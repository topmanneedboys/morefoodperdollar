#!/usr/bin/env python3
"""Verify an offline Argentina SEPA micro-partition delivery contract."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import zlib
from pathlib import Path
from typing import Any, Mapping

try:
    from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS, AVAILABILITY, CURRENCY, DELIVERY_PICKUP, _canonical_decimal, _canonical_json
    from tools.build_argentina_sepa_query_selective_mobile import offer_key
    from tools.build_argentina_sepa_regional_snapshot import _canonical_timestamp, _valid_gtin
    from tools.verify_argentina_sepa_query_selective_mobile import _validate_quantity, _validate_reference, _validate_store
    from tools.build_argentina_sepa_micro_partition_mobile import (
        BOOTSTRAP_FILE,
        MICRO_PARTITION_ALGORITHM,
        MICRO_PARTITION_COMPATIBILITY_VERSION,
        MICRO_PARTITION_POLICY_VERSION,
        MICRO_PARTITION_SCHEMA_VERSION,
        MEMBER_COMPRESSION,
        MAX_LOGICAL_PARTITION_COUNT,
        MIN_LOGICAL_PARTITION_COUNT,
        MIN_PHYSICAL_PACK_COUNT,
        MAX_PHYSICAL_PACK_COUNT,
        PACKS_DIR,
        PACK_FILE_TEMPLATE,
        PHYSICAL_PACK_ALGORITHM,
        REGION_MANIFEST_FILE,
        SEARCH_INDEX_FILE,
        STORE_INDEX_FILE,
        logical_partition_for_product,
        logical_partition_id,
    )
except ModuleNotFoundError:  # direct ``python tools/verify_...py`` invocation
    from build_argentina_sepa_national_shards import ARGENTINA_REGIONS, AVAILABILITY, CURRENCY, DELIVERY_PICKUP, _canonical_decimal, _canonical_json
    from build_argentina_sepa_query_selective_mobile import offer_key
    from build_argentina_sepa_regional_snapshot import _canonical_timestamp, _valid_gtin
    from verify_argentina_sepa_query_selective_mobile import _validate_quantity, _validate_reference, _validate_store
    from build_argentina_sepa_micro_partition_mobile import BOOTSTRAP_FILE, MICRO_PARTITION_ALGORITHM, MICRO_PARTITION_COMPATIBILITY_VERSION, MICRO_PARTITION_POLICY_VERSION, MICRO_PARTITION_SCHEMA_VERSION, MEMBER_COMPRESSION, MAX_LOGICAL_PARTITION_COUNT, MIN_LOGICAL_PARTITION_COUNT, MIN_PHYSICAL_PACK_COUNT, MAX_PHYSICAL_PACK_COUNT, PACKS_DIR, PACK_FILE_TEMPLATE, PHYSICAL_PACK_ALGORITHM, REGION_MANIFEST_FILE, SEARCH_INDEX_FILE, STORE_INDEX_FILE, logical_partition_for_product, logical_partition_id


class MicroPartitionVerificationError(ValueError):
    """A micro-partition artifact failed its verification contract."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise MicroPartitionVerificationError(message)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _read_canonical_json(path: Path, label: str) -> dict[str, Any]:
    _require(path.is_file(), f"Missing {label}: {path}")
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise MicroPartitionVerificationError(f"Invalid {label}: {path}") from exc
    _require(raw == _canonical_json(value), f"{label} is not canonical JSON")
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _safe_relative(value: Any, label: str) -> Path:
    _require(isinstance(value, str) and value and not os.path.isabs(value), f"{label} path is invalid")
    path = Path(value)
    _require(".." not in path.parts, f"{label} path escapes contract root")
    return path


def _digest_ok(value: Any) -> bool:
    return isinstance(value, str) and len(value) == 64 and value == value.lower() and all(char in "0123456789abcdef" for char in value)


def _verify_gzip_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    _require(descriptor.get("path") == expected_path and descriptor.get("compression") == "gzip", f"{label} descriptor is invalid")
    for field in ("bytes", "uncompressedBytes", "recordCount"):
        _require(isinstance(descriptor.get(field), int) and descriptor[field] >= 0, f"{label} {field} is invalid")
    for field in ("sha256", "uncompressedSha256"):
        _require(_digest_ok(descriptor.get(field)), f"{label} {field} is invalid")
    path = root / _safe_relative(expected_path, label)
    _require(path.is_file() and path.stat().st_size == descriptor["bytes"] and _sha256_file(path) == descriptor["sha256"], f"{label} bytes or hash mismatch")
    return path


def _verify_plain_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    _require(descriptor.get("path") == expected_path and isinstance(descriptor.get("bytes"), int) and descriptor["bytes"] >= 0 and _digest_ok(descriptor.get("sha256")), f"{label} descriptor is invalid")
    path = root / _safe_relative(expected_path, label)
    _require(path.is_file() and path.stat().st_size == descriptor["bytes"] and _sha256_file(path) == descriptor["sha256"], f"{label} bytes or hash mismatch")
    return path


def _iter_jsonl_gzip(path: Path, descriptor: Mapping[str, Any], label: str):
    import gzip

    digest = hashlib.sha256()
    total = 0
    count = 0
    try:
        with gzip.open(path, "rb") as handle:
            for line_number, line in enumerate(handle, start=1):
                digest.update(line)
                total += len(line)
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise MicroPartitionVerificationError(f"{label} line {line_number} is invalid JSON") from exc
                _require(isinstance(value, dict) and _canonical_json(value) == line, f"{label} line {line_number} is not canonical")
                count += 1
                yield value
    except (OSError, EOFError, zlib.error) as exc:
        raise MicroPartitionVerificationError(f"{label} gzip stream is invalid") from exc
    _require(total == descriptor["uncompressedBytes"] and count == descriptor["recordCount"] and digest.hexdigest() == descriptor["uncompressedSha256"], f"{label} uncompressed metadata mismatch")


def decode_member_bytes(data: bytes, descriptor: Mapping[str, Any], label: str = "logical member") -> list[dict[str, Any]]:
    """Verify exactly one gzip member and decode its canonical JSONL records."""

    _require(isinstance(data, bytes), f"{label} bytes are invalid")
    for field in ("byteLength", "bytes", "recordCount", "uncompressedBytes"):
        _require(isinstance(descriptor.get(field), int) and descriptor[field] >= 0, f"{label} {field} is invalid")
    _require(len(data) == descriptor["byteLength"] == descriptor["bytes"], f"{label} byte length mismatch")
    _require(_digest_ok(descriptor.get("sha256")) and _digest_ok(descriptor.get("uncompressedSha256")) and hashlib.sha256(data).hexdigest() == descriptor["sha256"], f"{label} compressed hash mismatch")
    stream = zlib.decompressobj(16 + zlib.MAX_WBITS)
    try:
        payload = stream.decompress(data) + stream.flush()
    except zlib.error as exc:
        raise MicroPartitionVerificationError(f"{label} gzip stream is invalid") from exc
    _require(stream.eof and not stream.unused_data and not stream.unconsumed_tail, f"{label} is not exactly one independent gzip member")
    _require(len(payload) == descriptor["uncompressedBytes"] and hashlib.sha256(payload).hexdigest() == descriptor["uncompressedSha256"], f"{label} uncompressed hash mismatch")
    records: list[dict[str, Any]] = []
    for line_number, line in enumerate(payload.splitlines(keepends=True), start=1):
        try:
            value = json.loads(line)
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise MicroPartitionVerificationError(f"{label} line {line_number} is invalid JSON") from exc
        _require(isinstance(value, dict) and _canonical_json(value) == line, f"{label} line {line_number} is not canonical")
        records.append(value)
    _require(len(records) == descriptor["recordCount"], f"{label} record count mismatch")
    return records


def _read_member(root: Path, pack_descriptors: Mapping[str, Mapping[str, Any]], descriptor: Mapping[str, Any], label: str) -> list[dict[str, Any]]:
    pack_id = descriptor.get("packId")
    pack = pack_descriptors.get(pack_id)
    _require(pack is not None, f"{label} references an unknown pack")
    path = root / _safe_relative(pack["path"], f"{label} pack")
    offset = descriptor.get("byteOffset")
    length = descriptor.get("byteLength")
    _require(isinstance(offset, int) and isinstance(length, int) and offset >= 0 and length >= 0 and offset + length <= pack["bytes"], f"{label} range is invalid")
    with path.open("rb") as handle:
        handle.seek(offset)
        data = handle.read(length)
    _require(len(data) == length, f"{label} range is truncated")
    return decode_member_bytes(data, descriptor, label)


def _validate_source(source: Any, *, expected_outer_sha256: str, expected_outer_bytes: int, expected_release_date: str, expected_accepted_sha256: str | None, expected_national_index_sha256: str | None) -> dict[str, Any]:
    _require(isinstance(source, dict), "source provenance is missing")
    _require(source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS" and source.get("releaseDate") == expected_release_date and source.get("outerSha256") == expected_outer_sha256 and source.get("outerBytes") == expected_outer_bytes, "source provenance is invalid")
    if expected_accepted_sha256 is not None:
        _require(source.get("acceptedObservationsSha256") == expected_accepted_sha256, "accepted source hash differs from qualified release")
    if expected_national_index_sha256 is not None:
        _require(source.get("nationalIndexSha256") == expected_national_index_sha256, "national index hash differs from qualified release")
    _require(source.get("license") == "Creative Commons Attribution 4.0" and isinstance(source.get("attribution"), str) and source["attribution"].strip() and source.get("rawProviderDataCommitted") is False, "source rights metadata is invalid")
    return source


def _validate_micro_product(record: Mapping[str, Any], *, logical_count: int, product_keys: set[str], label: str) -> None:
    allowed = {"productEvidenceKey", "commerceId", "providerProductId", "gtin", "gtinStatus", "name", "brand", "quantity", "quantityStatus", "metadataStatus", "partitionId", "metadataVariants"}
    required = {"productEvidenceKey", "commerceId", "providerProductId", "gtin", "gtinStatus", "name", "brand", "quantity", "quantityStatus", "metadataStatus", "partitionId"}
    _require(set(record) <= allowed and required <= set(record), f"{label} schema is invalid")
    key = record["productEvidenceKey"]
    _require(isinstance(key, str) and key.startswith("ar-sepa-product:") and key not in product_keys, f"{label} product identity is duplicated or invalid")
    _require(isinstance(record["commerceId"], str) and record["commerceId"] and isinstance(record["providerProductId"], str) and record["providerProductId"], f"{label} provider identity is invalid")
    _require(key == f"ar-sepa-product:{record['commerceId']}:{record['providerProductId']}", f"{label} product key is invalid")
    _require(record["partitionId"] == logical_partition_for_product(key, logical_count), f"{label} partition assignment is invalid")
    gtin = record["gtin"]
    if gtin is None:
        _require(record["gtinStatus"] == "INVALID_OR_NOT_GTIN" and not _valid_gtin(record["providerProductId"]), f"{label} invalid GTIN was promoted")
    else:
        _require(record["gtinStatus"] == "VALID" and gtin == record["providerProductId"] and _valid_gtin(gtin), f"{label} GTIN is invalid")
    _require(record["name"] is None or isinstance(record["name"], str), f"{label} name is invalid")
    _require(record["brand"] is None or isinstance(record["brand"], str), f"{label} brand is invalid")
    _validate_quantity(record["quantity"], record["quantityStatus"], label)
    _require(record["metadataStatus"] in {"CONSISTENT", "CONFLICTING"}, f"{label} metadata status is invalid")
    if "metadataVariants" in record:
        _require(isinstance(record["metadataVariants"], dict), f"{label} metadata variants are invalid")
    product_keys.add(key)


def _validate_micro_offer(record: Mapping[str, Any], *, region: Any, logical_count: int, expected_release_date: str, product_keys: set[str], store_keys: set[str], offer_ids: set[int], label: str) -> int:
    required = {"offerKey", "offerId", "productEvidenceKey", "storeKey", "listPrice", "referencePrice", "packageProvenance", "sourceRow", "providerUpdateTime", "releaseDate", "freshnessStatus", "availability", "promotions"}
    _require(set(record) == required, f"{label} schema is invalid")
    offer_id = record["offerId"]
    _require(isinstance(offer_id, int) and offer_id > 0 and offer_id not in offer_ids, f"{label} offer id is duplicated or invalid")
    _require(record["offerKey"] == offer_key(region.region_id, offer_id), f"{label} offer key is invalid")
    product_key = record["productEvidenceKey"]
    _require(product_key in product_keys and logical_partition_for_product(product_key, logical_count) == label.rsplit("/", 1)[-1].replace(".jsonl.gz", ""), f"{label} product reference is invalid")
    _require(record["storeKey"] in store_keys, f"{label} store reference is invalid")
    price = record["listPrice"]
    _require(isinstance(price, dict) and set(price) == {"amount", "currency"} and price["currency"] == CURRENCY and isinstance(price["amount"], str) and price["amount"] == _canonical_decimal(price["amount"], f"{label} list price", positive=True), f"{label} list price is invalid")
    _validate_reference(record["referencePrice"], label)
    package = record["packageProvenance"]
    _require(isinstance(package, dict) and set(package) == {"sha256", "name"} and _digest_ok(package["sha256"]) and isinstance(package["name"], str) and package["name"], f"{label} package provenance is invalid")
    _require(isinstance(record["sourceRow"], int) and record["sourceRow"] > 0, f"{label} source row is invalid")
    if record["providerUpdateTime"] is not None:
        _require(record["providerUpdateTime"] == _canonical_timestamp(record["providerUpdateTime"], f"{label} provider update time"), f"{label} provider update time is invalid")
    _require(record["releaseDate"] == expected_release_date and record["freshnessStatus"] == "FRESH" and record["availability"] == AVAILABILITY, f"{label} evidence boundary is invalid")
    promotions = record["promotions"]
    _require(isinstance(promotions, list), f"{label} promotions are invalid")
    slots: set[int] = set()
    for promotion in promotions:
        _require(isinstance(promotion, dict) and set(promotion) == {"slot", "price", "priceRaw", "condition", "eligibility"}, f"{label} promotion schema is invalid")
        slot = promotion["slot"]
        _require(isinstance(slot, int) and slot in {1, 2} and slot not in slots, f"{label} promotion slot is invalid")
        slots.add(slot)
        _require(promotion["eligibility"] == "UNKNOWN", f"{label} promotion eligibility escaped UNKNOWN")
        promotion_price = promotion["price"]
        if promotion_price is not None:
            _require(isinstance(promotion_price, dict) and set(promotion_price) == {"amount", "currency"} and promotion_price["currency"] == CURRENCY and isinstance(promotion_price["amount"], str) and promotion_price["amount"] == _canonical_decimal(promotion_price["amount"], f"{label} promotion price", positive=True), f"{label} promotion price is invalid")
        for field in ("priceRaw", "condition"):
            _require(promotion[field] is None or isinstance(promotion[field], str) and promotion[field] == promotion[field].strip(), f"{label} promotion {field} is invalid")
    offer_ids.add(offer_id)
    return len(promotions)


def verify_micro_partition_mobile(
    root: Path,
    *,
    expected_outer_sha256: str,
    expected_outer_bytes: int,
    expected_release_date: str,
    expected_accepted_sha256: str | None = None,
    expected_national_index_sha256: str | None = None,
    expected_logical_partition_count: int | None = None,
    expected_physical_pack_count: int | None = None,
) -> dict[str, Any]:
    root = Path(root).resolve()
    bootstrap = _read_canonical_json(root / BOOTSTRAP_FILE, BOOTSTRAP_FILE)
    _require(bootstrap.get("artifactSchemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and bootstrap.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and bootstrap.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION and bootstrap.get("atomicCompletion") is True and bootstrap.get("completionState") == "COMPLETE" and bootstrap.get("productionUiAuthorized") is False, "bootstrap version/completion/authorization is invalid")
    _require(bootstrap.get("generatedAt") == _canonical_timestamp(bootstrap.get("generatedAt"), "bootstrap.generatedAt"), "bootstrap timestamp is invalid")
    bootstrap_hash = _sha256_file(root / BOOTSTRAP_FILE)
    _require((root / "bootstrap.sha256").read_text(encoding="ascii") == f"{bootstrap_hash}  {BOOTSTRAP_FILE}\n", "bootstrap checksum is invalid")
    integrity = _read_canonical_json(root / "integrity.json", "integrity.json")
    _require(integrity == {"bootstrapSha256": bootstrap_hash, "atomicCompletion": True, "schemaVersion": MICRO_PARTITION_SCHEMA_VERSION, "regionIds": integrity.get("regionIds")}, "root integrity metadata is malformed")
    source = _validate_source(bootstrap.get("source"), expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date, expected_accepted_sha256=expected_accepted_sha256, expected_national_index_sha256=expected_national_index_sha256)
    boundaries = {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "rawProviderDataCommitted": False}
    _require(bootstrap.get("boundaries") == boundaries, "bootstrap boundaries are invalid")
    partitioning = bootstrap.get("partitioning")
    _require(isinstance(partitioning, dict) and partitioning.get("algorithm") == MICRO_PARTITION_ALGORITHM and partitioning.get("key") == "productEvidenceKey" and partitioning.get("physicalPackAlgorithm") == PHYSICAL_PACK_ALGORITHM and partitioning.get("memberCompression") == MEMBER_COMPRESSION and partitioning.get("rangeUnit") == "BYTES", "bootstrap partitioning is invalid")
    logical_count = partitioning.get("logicalPartitionCount")
    pack_count = partitioning.get("physicalPackCount")
    _require(isinstance(logical_count, int) and MIN_LOGICAL_PARTITION_COUNT <= logical_count <= MAX_LOGICAL_PARTITION_COUNT and logical_count & (logical_count - 1) == 0, "logical partition count is invalid")
    _require(isinstance(pack_count, int) and MIN_PHYSICAL_PACK_COUNT <= pack_count <= MAX_PHYSICAL_PACK_COUNT and pack_count <= logical_count, "physical pack count is invalid")
    if expected_logical_partition_count is not None:
        _require(logical_count == expected_logical_partition_count, "logical partition count differs from expected")
    if expected_physical_pack_count is not None:
        _require(pack_count == expected_physical_pack_count, "physical pack count differs from expected")
    regions = bootstrap.get("regions")
    _require(isinstance(regions, list) and len(regions) == len(ARGENTINA_REGIONS), "bootstrap region list is incomplete")
    expected_by_code = {region.province_code: region for region in ARGENTINA_REGIONS}
    totals = {"stores": 0, "productEvidenceRecords": 0, "offers": 0, "promotions": 0}
    seen_region_ids: list[str] = []
    for entry in regions:
        _require(isinstance(entry, dict), "bootstrap region entry is invalid")
        spec = expected_by_code.get(entry.get("provinceCode"))
        _require(spec is not None and entry.get("regionId") == spec.region_id and entry.get("displayName") == spec.display_name and entry.get("selector") == {"field": "store.province", "operator": "EXACT", "value": spec.province_code}, "bootstrap region selector is invalid")
        seen_region_ids.append(spec.region_id)
        manifest_descriptor = entry.get("manifest")
        _require(isinstance(manifest_descriptor, dict), f"{spec.region_id} manifest descriptor is missing")
        manifest_path = _verify_plain_descriptor(root, manifest_descriptor, f"regions/{spec.region_id}/{REGION_MANIFEST_FILE}", f"{spec.region_id} manifest")
        manifest = _read_canonical_json(manifest_path, f"{spec.region_id} manifest")
        _require(manifest.get("artifactSchemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and manifest.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and manifest.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION and manifest.get("atomicCompletion") is True and manifest.get("completionState") == "COMPLETE" and manifest.get("generatedAt") == bootstrap["generatedAt"] and manifest.get("source") == source, f"{spec.region_id} manifest metadata is invalid")
        _require(manifest.get("region") == {"id": spec.region_id, "displayName": spec.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": spec.province_code}}, f"{spec.region_id} manifest selector is invalid")
        _require(manifest.get("boundaries") == {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "rawProviderDataCommitted": False}, f"{spec.region_id} boundaries are invalid")
        if (root / f"regions/{spec.region_id}/manifest.sha256").read_text(encoding="ascii") != f"{_sha256_file(manifest_path)}  manifest.json\n":
            raise MicroPartitionVerificationError(f"{spec.region_id} manifest checksum is invalid")
        region_partitioning = manifest.get("partitioning")
        _require(isinstance(region_partitioning, dict) and region_partitioning.get("logicalPartitionCount") == logical_count and region_partitioning.get("physicalPackCount") == pack_count and region_partitioning.get("algorithm") == MICRO_PARTITION_ALGORITHM and region_partitioning.get("physicalPackAlgorithm") == PHYSICAL_PACK_ALGORITHM and region_partitioning.get("memberCompression") == MEMBER_COMPRESSION, f"{spec.region_id} partitioning metadata is invalid")
        files = manifest.get("files")
        _require(isinstance(files, dict) and set(files) == {"searchIndex", "storeIndex", "offerPacks", "logicalPartitions"}, f"{spec.region_id} file set is invalid")
        search_descriptor = files["searchIndex"]
        store_descriptor = files["storeIndex"]
        search_path = _verify_gzip_descriptor(root, search_descriptor, f"regions/{spec.region_id}/{SEARCH_INDEX_FILE}", f"{spec.region_id} search index")
        store_path = _verify_gzip_descriptor(root, store_descriptor, f"regions/{spec.region_id}/{STORE_INDEX_FILE}", f"{spec.region_id} store index")
        product_keys: set[str] = set()
        for record in _iter_jsonl_gzip(search_path, search_descriptor, f"{spec.region_id} search index"):
            _validate_micro_product(record, logical_count=logical_count, product_keys=product_keys, label=f"{spec.region_id} search index")
        store_keys: set[str] = set()
        for record in _iter_jsonl_gzip(store_path, store_descriptor, f"{spec.region_id} store index"):
            _validate_store(record, region=spec, store_keys=store_keys, label=f"{spec.region_id} store index")
        packs = files["offerPacks"]
        _require(isinstance(packs, list) and len(packs) == pack_count, f"{spec.region_id} physical pack list is incomplete")
        pack_map: dict[str, Mapping[str, Any]] = {}
        for number, pack in enumerate(packs):
            _require(isinstance(pack, dict) and pack.get("packId") == f"pack{number:03d}" and pack.get("memberCompression") == MEMBER_COMPRESSION and pack.get("schemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and pack.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and pack.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION, f"{spec.region_id} pack metadata is invalid")
            expected_path = f"regions/{spec.region_id}/{PACKS_DIR}/{PACK_FILE_TEMPLATE.format(pack=number)}"
            path = _verify_plain_descriptor(root, pack, expected_path, f"{spec.region_id}/{pack['packId']}")
            _require(pack["packId"] not in pack_map, f"{spec.region_id} duplicate pack id")
            _require(isinstance(pack.get("memberCount"), int) and isinstance(pack.get("logicalPartitionIds"), list) and pack["memberCount"] == len(pack["logicalPartitionIds"]), f"{spec.region_id} pack member metadata is invalid")
            _require(path.stat().st_size == pack["bytes"], f"{spec.region_id} pack byte count changed")
            pack_map[pack["packId"]] = pack
        logicals = files["logicalPartitions"]
        _require(isinstance(logicals, list) and len(logicals) == logical_count, f"{spec.region_id} logical partition list is incomplete")
        seen_partitions: set[str] = set()
        ranges_by_pack: dict[str, list[tuple[int, int]]] = {pack_id: [] for pack_id in pack_map}
        offer_ids: set[int] = set()
        offer_count = 0
        promotion_count = 0
        for bucket, descriptor in enumerate(logicals):
            partition_id = logical_partition_id(bucket, logical_count)
            _require(isinstance(descriptor, dict) and descriptor.get("partitionId") == partition_id and partition_id not in seen_partitions and descriptor.get("memberCompression") == MEMBER_COMPRESSION and descriptor.get("schemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and descriptor.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and descriptor.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION and descriptor.get("releaseDate") == expected_release_date and descriptor.get("contents") == {"productBucket": bucket, "partitionAlgorithm": MICRO_PARTITION_ALGORITHM} and descriptor.get("dependencies") == ["searchIndex", "storeIndex"], f"{spec.region_id}/{partition_id} metadata is invalid")
            seen_partitions.add(partition_id)
            pack_id = descriptor.get("packId")
            _require(pack_id == f"pack{bucket % pack_count:03d}" and pack_id in pack_map, f"{spec.region_id}/{partition_id} pack assignment is invalid")
            offset = descriptor.get("byteOffset")
            length = descriptor.get("byteLength")
            _require(isinstance(offset, int) and isinstance(length, int) and offset >= 0 and length >= 0 and offset + length <= pack_map[pack_id]["bytes"], f"{spec.region_id}/{partition_id} range is invalid")
            ranges_by_pack[pack_id].append((offset, length))
            records = _read_member(root, pack_map, descriptor, f"{spec.region_id}/{partition_id}")
            for record in records:
                promotion_count += _validate_micro_offer(record, region=spec, logical_count=logical_count, expected_release_date=expected_release_date, product_keys=product_keys, store_keys=store_keys, offer_ids=offer_ids, label=f"regions/{spec.region_id}/p{bucket:0{max(3, len(str(logical_count - 1)))}d}.jsonl.gz")
                offer_count += 1
        for pack_id, ranges in ranges_by_pack.items():
            ordered = sorted(ranges)
            cursor = 0
            for offset, length in ordered:
                _require(offset == cursor, f"{spec.region_id}/{pack_id} member ranges are not contiguous")
                cursor += length
            _require(cursor == pack_map[pack_id]["bytes"], f"{spec.region_id}/{pack_id} member coverage is incomplete")
            _require(pack_map[pack_id]["memberCount"] == len(ranges), f"{spec.region_id}/{pack_id} member count mismatch")
            expected_members = [logical_partition_id(bucket, logical_count) for bucket in range(logical_count) if bucket % pack_count == int(pack_id[4:])]
            _require(pack_map[pack_id]["logicalPartitionIds"] == expected_members, f"{spec.region_id}/{pack_id} logical member ordering is invalid")
        counts = manifest.get("counts")
        _require(isinstance(counts, dict) and counts == entry.get("counts") and counts.get("stores") == store_descriptor["recordCount"] and counts.get("productEvidenceRecords") == search_descriptor["recordCount"] and counts.get("offers") == offer_count and counts.get("promotions") == promotion_count and counts.get("selectedAcceptedObservations") == offer_count, f"{spec.region_id} counts do not match files")
        size = manifest.get("size")
        pack_bytes = sum(pack["bytes"] for pack in packs)
        logical_bytes = sum(descriptor["byteLength"] for descriptor in logicals)
        _require(size == {"compressedBytes": search_descriptor["bytes"] + store_descriptor["bytes"] + pack_bytes, "uncompressedBytes": search_descriptor["uncompressedBytes"] + store_descriptor["uncompressedBytes"] + sum(item["uncompressedBytes"] for item in logicals), "searchIndexBytes": search_descriptor["bytes"], "storeIndexBytes": store_descriptor["bytes"], "offerPackBytes": pack_bytes, "logicalOfferBytes": logical_bytes, "fileCount": 2 + pack_count}, f"{spec.region_id} size metadata is invalid")
        for key in totals:
            totals[key] += int(counts[key])
    _require(seen_region_ids == [region.region_id for region in ARGENTINA_REGIONS], "bootstrap regions are not in stable order")
    _require(integrity.get("regionIds") == seen_region_ids, "root integrity region list is invalid")
    _require(bootstrap.get("totals") == {"publishableRegions": len(ARGENTINA_REGIONS), **totals}, "bootstrap totals do not match regions")
    return {"status": "VERIFIED", "bootstrapSha256": bootstrap_hash, "regions": len(regions), **totals, "logicalPartitionCount": logical_count, "physicalPackCount": pack_count}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--expected-outer-sha256", required=True)
    parser.add_argument("--expected-outer-bytes", required=True, type=int)
    parser.add_argument("--expected-release-date", required=True)
    parser.add_argument("--expected-accepted-sha256")
    parser.add_argument("--expected-national-index-sha256")
    parser.add_argument("--expected-logical-partition-count", type=int)
    parser.add_argument("--expected-physical-pack-count", type=int)
    args = parser.parse_args(argv)
    try:
        result = verify_micro_partition_mobile(
            args.root,
            expected_outer_sha256=args.expected_outer_sha256,
            expected_outer_bytes=args.expected_outer_bytes,
            expected_release_date=args.expected_release_date,
            expected_accepted_sha256=args.expected_accepted_sha256,
            expected_national_index_sha256=args.expected_national_index_sha256,
            expected_logical_partition_count=args.expected_logical_partition_count,
            expected_physical_pack_count=args.expected_physical_pack_count,
        )
    except (MicroPartitionVerificationError, OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"micro-partition verification failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

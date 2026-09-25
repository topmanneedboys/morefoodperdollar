#!/usr/bin/env python3
"""Verify the static query-selective Argentina SEPA mobile contract."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterator, Mapping

try:
    from tools.build_argentina_sepa_national_shards import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        NationalShardError,
        _canonical_decimal,
        _canonical_json,
    )
    from tools.build_argentina_sepa_query_selective_mobile import (
        BOOTSTRAP_FILE,
        DEFAULT_BUCKET_COUNT,
        EXPECTED_ACCEPTED_SHA256,
        EXPECTED_NATIONAL_INDEX_SHA256,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        MAX_BUCKET_COUNT,
        PARTITION_ALGORITHM,
        PARTITIONS_DIR,
        PARTITION_FILE_TEMPLATE,
        QUERY_SELECTIVE_COMPATIBILITY_VERSION,
        QUERY_SELECTIVE_POLICY_VERSION,
        QUERY_SELECTIVE_SCHEMA_VERSION,
        QuerySelectiveMobileError,
        RELEASE_DATE,
        SEARCH_INDEX_FILE,
        STORE_INDEX_FILE,
        offer_key,
        product_partition_id,
    )
    from tools.build_argentina_sepa_regional_snapshot import _canonical_timestamp, _valid_gtin
except ModuleNotFoundError:  # direct ``python tools/verify_...py`` invocation
    from build_argentina_sepa_national_shards import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        NationalShardError,
        _canonical_decimal,
        _canonical_json,
    )
    from build_argentina_sepa_query_selective_mobile import (
        BOOTSTRAP_FILE,
        DEFAULT_BUCKET_COUNT,
        EXPECTED_ACCEPTED_SHA256,
        EXPECTED_NATIONAL_INDEX_SHA256,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        MAX_BUCKET_COUNT,
        PARTITION_ALGORITHM,
        PARTITIONS_DIR,
        PARTITION_FILE_TEMPLATE,
        QUERY_SELECTIVE_COMPATIBILITY_VERSION,
        QUERY_SELECTIVE_POLICY_VERSION,
        QUERY_SELECTIVE_SCHEMA_VERSION,
        QuerySelectiveMobileError,
        RELEASE_DATE,
        SEARCH_INDEX_FILE,
        STORE_INDEX_FILE,
        offer_key,
        product_partition_id,
    )
    from build_argentina_sepa_regional_snapshot import _canonical_timestamp, _valid_gtin


class QuerySelectiveMobileVerificationError(QuerySelectiveMobileError):
    """A query-selective artifact failed its static verification contract."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise QuerySelectiveMobileVerificationError(message)


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
        raise QuerySelectiveMobileVerificationError(f"Invalid {label}: {path}") from exc
    _require(raw == _canonical_json(value), f"{label} is not canonical JSON")
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _safe_relative(value: Any, label: str) -> Path:
    _require(isinstance(value, str) and value and not os.path.isabs(value), f"{label} path is invalid")
    path = Path(value)
    _require(".." not in path.parts, f"{label} path escapes the contract root")
    return path


def _hash_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    _require(descriptor.get("path") == expected_path, f"{label} path is invalid")
    _require(descriptor.get("compression") == "gzip", f"{label} compression is invalid")
    for field in ("bytes", "uncompressedBytes", "recordCount"):
        _require(isinstance(descriptor.get(field), int) and descriptor[field] >= 0, f"{label} {field} is invalid")
    for field in ("sha256", "uncompressedSha256"):
        digest = descriptor.get(field)
        _require(isinstance(digest, str) and len(digest) == 64 and digest == digest.lower() and all(char in "0123456789abcdef" for char in digest), f"{label} {field} is invalid")
    path = root / _safe_relative(expected_path, label)
    _require(path.is_file(), f"Missing {label}: {path}")
    _require(path.stat().st_size == descriptor["bytes"], f"{label} byte count mismatch")
    _require(_sha256_file(path) == descriptor["sha256"], f"{label} hash mismatch")
    return path


def _hash_plain_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    """Validate a non-compressed manifest/checksum descriptor."""

    _require(descriptor.get("path") == expected_path, f"{label} path is invalid")
    value = descriptor.get("bytes")
    digest = descriptor.get("sha256")
    _require(isinstance(value, int) and value >= 0, f"{label} bytes are invalid")
    _require(isinstance(digest, str) and len(digest) == 64 and digest == digest.lower() and all(char in "0123456789abcdef" for char in digest), f"{label} hash is invalid")
    path = root / _safe_relative(expected_path, label)
    _require(path.is_file() and path.stat().st_size == value and _sha256_file(path) == digest, f"{label} bytes or hash mismatch")
    return path


def _iter_gzip_records(path: Path, descriptor: Mapping[str, Any], label: str) -> Iterator[dict[str, Any]]:
    uncompressed_hash = hashlib.sha256()
    uncompressed_bytes = 0
    try:
        with gzip.open(path, "rb") as handle:
            for line_number, line in enumerate(handle, start=1):
                uncompressed_hash.update(line)
                uncompressed_bytes += len(line)
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise QuerySelectiveMobileVerificationError(f"{label} line {line_number} is invalid JSON") from exc
                _require(isinstance(value, dict), f"{label} line {line_number} is not an object")
                yield value
    except (OSError, EOFError) as exc:
        raise QuerySelectiveMobileVerificationError(f"{label} gzip stream is invalid") from exc
    _require(uncompressed_bytes == descriptor["uncompressedBytes"], f"{label} uncompressed byte count mismatch")
    _require(uncompressed_hash.hexdigest() == descriptor["uncompressedSha256"], f"{label} uncompressed hash mismatch")


def _validate_source(source: Any, *, expected_outer_sha256: str, expected_outer_bytes: int, expected_release_date: str, expected_accepted_sha256: str | None, expected_national_index_sha256: str | None) -> dict[str, Any]:
    _require(isinstance(source, dict), "source provenance is missing")
    _require(source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS", "source provider is invalid")
    _require(source.get("releaseDate") == expected_release_date, "source release date is invalid")
    _require(source.get("outerSha256") == expected_outer_sha256 and source.get("outerBytes") == expected_outer_bytes, "source outer ZIP proof is invalid")
    accepted_sha = source.get("acceptedObservationsSha256")
    _require(isinstance(accepted_sha, str) and len(accepted_sha) == 64 and accepted_sha == accepted_sha.lower() and all(char in "0123456789abcdef" for char in accepted_sha), "accepted source hash is invalid")
    if expected_accepted_sha256 is not None:
        _require(accepted_sha == expected_accepted_sha256, "accepted source hash is not the qualified release")
    index_sha = source.get("nationalIndexSha256")
    _require(isinstance(index_sha, str) and len(index_sha) == 64 and index_sha == index_sha.lower() and all(char in "0123456789abcdef" for char in index_sha), "national index hash is invalid")
    if expected_national_index_sha256 is not None:
        _require(index_sha == expected_national_index_sha256, "national index hash is not the qualified release")
    _require(source.get("license") == "Creative Commons Attribution 4.0", "source licence is invalid")
    _require(isinstance(source.get("attribution"), str) and source["attribution"].strip(), "source attribution is invalid")
    _require(source.get("rawProviderDataCommitted") is False, "source claims raw provider data was committed")
    return source


def _validate_quantity(quantity: Any, quantity_status: Any, label: str) -> None:
    _require(quantity_status in {"KNOWN", "UNKNOWN", "CONFLICTING"}, f"{label} quantity status is invalid")
    if quantity_status == "KNOWN":
        _require(isinstance(quantity, dict), f"{label} known quantity is missing")
        _require(quantity.get("unit") in {"GRAM", "MILLILITRE", "COUNT"}, f"{label} quantity unit is invalid")
        value = quantity.get("value")
        _require(isinstance(value, str) and value == _canonical_decimal(value, f"{label} quantity value", positive=True), f"{label} quantity value is invalid")
    else:
        _require(quantity is None, f"{label} non-known quantity is exposed")


def _validate_product(record: Mapping[str, Any], *, bucket_count: int, product_keys: set[str], label: str) -> None:
    allowed = {"productEvidenceKey", "commerceId", "providerProductId", "gtin", "gtinStatus", "name", "brand", "quantity", "quantityStatus", "metadataStatus", "partitionId", "metadataVariants"}
    _require(set(record) <= allowed and {"productEvidenceKey", "commerceId", "providerProductId", "gtin", "gtinStatus", "name", "brand", "quantity", "quantityStatus", "metadataStatus", "partitionId"} <= set(record), f"{label} schema is invalid")
    key = record["productEvidenceKey"]
    _require(isinstance(key, str) and key.startswith("ar-sepa-product:") and key not in product_keys, f"{label} product identity is duplicated or invalid")
    _require(isinstance(record["commerceId"], str) and record["commerceId"] and isinstance(record["providerProductId"], str) and record["providerProductId"], f"{label} provider identity is invalid")
    _require(key == f"ar-sepa-product:{record['commerceId']}:{record['providerProductId']}", f"{label} product key is invalid")
    _require(record["partitionId"] == product_partition_id(key, bucket_count), f"{label} partition assignment is invalid")
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


def _validate_store(record: Mapping[str, Any], *, region: RegionSpec, store_keys: set[str], label: str) -> None:
    allowed = {"storeKey", "commerceId", "bannerId", "providerStoreId", "name", "type", "address", "locality", "province", "latitude", "longitude", "geoStatus", "metadataStatus", "metadataVariants"}
    required = {"storeKey", "commerceId", "bannerId", "providerStoreId", "name", "type", "address", "locality", "province", "latitude", "longitude", "geoStatus", "metadataStatus"}
    _require(set(record) <= allowed and required <= set(record), f"{label} schema is invalid")
    key = record["storeKey"]
    _require(isinstance(key, str) and key.startswith("ar-sepa-store:") and key not in store_keys, f"{label} store identity is duplicated or invalid")
    _require(key == f"ar-sepa-store:{record['commerceId']}:{record['bannerId']}:{record['providerStoreId']}", f"{label} store key is invalid")
    _require(record["province"] == region.province_code, f"{label} store crosses region boundary")
    _require(record["geoStatus"] in {"VALID", "GEO_INCOMPLETE", "GEO_CONFLICTING"}, f"{label} geo status is invalid")
    _require(record["metadataStatus"] in {"CONSISTENT", "CONFLICTING"}, f"{label} metadata status is invalid")
    if record["geoStatus"] == "VALID":
        _require(isinstance(record["latitude"], str) and isinstance(record["longitude"], str), f"{label} valid coordinates are incomplete")
        _require(Decimal("-56") <= Decimal(record["latitude"]) <= Decimal("-21") and Decimal("-74") <= Decimal(record["longitude"]) <= Decimal("-52"), f"{label} coordinates are outside Argentina")
    elif record["geoStatus"] == "GEO_CONFLICTING":
        _require(record["latitude"] is None and record["longitude"] is None, f"{label} conflicting coordinates are exposed")
    for field in ("latitude", "longitude"):
        value = record[field]
        if value is not None:
            _require(isinstance(value, str) and value == _canonical_decimal(value, f"{label} {field}"), f"{label} coordinate is not canonical")
    if "metadataVariants" in record:
        _require(isinstance(record["metadataVariants"], dict), f"{label} metadata variants are invalid")
    store_keys.add(key)


def _validate_reference(reference: Any, label: str) -> None:
    if reference is None:
        return
    _require(isinstance(reference, dict) and set(reference) == {"amount", "currency", "quantityRaw", "unitRaw", "semanticRole"}, f"{label} reference price is invalid")
    _require(reference["currency"] == CURRENCY and reference["semanticRole"] == "reference_price_not_current_offer", f"{label} reference price semantics are invalid")
    if reference["amount"] is not None:
        _require(isinstance(reference["amount"], str) and reference["amount"] == _canonical_decimal(reference["amount"], f"{label} reference amount", positive=True), f"{label} reference amount is invalid")
    for field in ("quantityRaw", "unitRaw"):
        _require(reference[field] is None or isinstance(reference[field], str), f"{label} reference {field} is invalid")


def _validate_offer(record: Mapping[str, Any], *, region: RegionSpec, bucket_count: int, expected_release_date: str, product_keys: set[str], store_keys: set[str], offer_ids: set[int], label: str) -> int:
    required = {"offerKey", "offerId", "productEvidenceKey", "storeKey", "listPrice", "referencePrice", "packageProvenance", "sourceRow", "providerUpdateTime", "releaseDate", "freshnessStatus", "availability", "promotions"}
    _require(set(record) == required, f"{label} schema is invalid")
    offer_id = record["offerId"]
    _require(isinstance(offer_id, int) and offer_id > 0 and offer_id not in offer_ids, f"{label} offer id is duplicated or invalid")
    _require(record["offerKey"] == offer_key(region.region_id, offer_id), f"{label} offer key is invalid")
    product_key = record["productEvidenceKey"]
    _require(product_key in product_keys and product_partition_id(product_key, bucket_count) == label.rsplit("/", 1)[-1].replace(".jsonl.gz", ""), f"{label} product reference is invalid")
    _require(record["storeKey"] in store_keys, f"{label} store reference is invalid")
    price = record["listPrice"]
    _require(isinstance(price, dict) and set(price) == {"amount", "currency"} and price["currency"] == CURRENCY, f"{label} list price is invalid")
    _require(isinstance(price["amount"], str) and price["amount"] == _canonical_decimal(price["amount"], f"{label} list price", positive=True), f"{label} list price is invalid")
    _validate_reference(record["referencePrice"], label)
    package = record["packageProvenance"]
    _require(isinstance(package, dict) and set(package) == {"sha256", "name"} and isinstance(package["sha256"], str) and len(package["sha256"]) == 64 and package["sha256"] == package["sha256"].lower() and all(char in "0123456789abcdef" for char in package["sha256"]) and isinstance(package["name"], str) and package["name"], f"{label} package provenance is invalid")
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


def verify_query_selective_mobile(
    snapshot_root: Path,
    *,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
    expected_bucket_count: int | None = DEFAULT_BUCKET_COUNT,
) -> dict[str, Any]:
    snapshot_root = Path(snapshot_root).resolve()
    _require(snapshot_root.is_dir(), f"Missing query-selective root: {snapshot_root}")
    bootstrap = _read_canonical_json(snapshot_root / BOOTSTRAP_FILE, BOOTSTRAP_FILE)
    _require(bootstrap.get("artifactSchemaVersion") == QUERY_SELECTIVE_SCHEMA_VERSION and bootstrap.get("policyVersion") == QUERY_SELECTIVE_POLICY_VERSION and bootstrap.get("compatibilityVersion") == QUERY_SELECTIVE_COMPATIBILITY_VERSION, "bootstrap version is invalid")
    _require(bootstrap.get("atomicCompletion") is True and bootstrap.get("completionState") == "COMPLETE" and bootstrap.get("productionUiAuthorized") is False, "bootstrap completion or UI authorization is invalid")
    _require(bootstrap.get("generatedAt") == _canonical_timestamp(bootstrap.get("generatedAt"), "bootstrap.generatedAt"), "bootstrap timestamp is invalid")
    bootstrap_hash = _sha256_file(snapshot_root / BOOTSTRAP_FILE)
    _require((snapshot_root / "bootstrap.sha256").read_text(encoding="ascii") == f"{bootstrap_hash}  {BOOTSTRAP_FILE}\n", "bootstrap checksum is invalid")
    integrity = _read_canonical_json(snapshot_root / "integrity.json", "integrity.json")
    _require(integrity == {"bootstrapSha256": bootstrap_hash, "atomicCompletion": True, "schemaVersion": QUERY_SELECTIVE_SCHEMA_VERSION, "regionIds": integrity.get("regionIds")}, "root integrity metadata is malformed")
    source = _validate_source(bootstrap.get("source"), expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date, expected_accepted_sha256=expected_accepted_sha256, expected_national_index_sha256=expected_national_index_sha256)
    _require(bootstrap.get("boundaries") == {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "rawProviderDataCommitted": False}, "bootstrap boundaries are invalid")
    regions = bootstrap.get("regions")
    _require(isinstance(regions, list) and len(regions) == len(ARGENTINA_REGIONS), "bootstrap region list is incomplete")
    expected_by_code = {region.province_code: region for region in ARGENTINA_REGIONS}
    seen_regions: list[str] = []
    region_bucket_counts: list[int] = []
    totals = {"stores": 0, "productEvidenceRecords": 0, "offers": 0, "promotions": 0}
    for entry in regions:
        _require(isinstance(entry, dict), "bootstrap region entry is invalid")
        spec = expected_by_code.get(entry.get("provinceCode"))
        _require(spec is not None and entry.get("regionId") == spec.region_id and entry.get("displayName") == spec.display_name, "bootstrap region selector is invalid")
        _require(entry.get("selector") == {"field": "store.province", "operator": "EXACT", "value": spec.province_code}, "bootstrap selector is invalid")
        seen_regions.append(spec.region_id)
        descriptor = entry.get("manifest")
        _require(isinstance(descriptor, dict), f"{spec.region_id} manifest descriptor is missing")
        manifest_path = _hash_plain_descriptor(snapshot_root, descriptor, f"regions/{spec.region_id}/manifest.json", f"{spec.region_id} manifest")
        manifest = _read_canonical_json(manifest_path, f"{spec.region_id} manifest")
        checksum_path = manifest_path.with_name("manifest.sha256")
        _require(checksum_path.is_file() and checksum_path.read_text(encoding="ascii") == f"{_sha256_file(manifest_path)}  manifest.json\n", f"{spec.region_id} manifest checksum is invalid")
        _require(manifest.get("artifactSchemaVersion") == QUERY_SELECTIVE_SCHEMA_VERSION and manifest.get("policyVersion") == QUERY_SELECTIVE_POLICY_VERSION and manifest.get("compatibilityVersion") == QUERY_SELECTIVE_COMPATIBILITY_VERSION and manifest.get("atomicCompletion") is True and manifest.get("completionState") == "COMPLETE", f"{spec.region_id} manifest version/completion is invalid")
        _require(manifest.get("generatedAt") == bootstrap["generatedAt"], f"{spec.region_id} timestamp differs from bootstrap")
        _require(manifest.get("source") == source, f"{spec.region_id} source differs from bootstrap")
        _require(manifest.get("region") == {"id": spec.region_id, "displayName": spec.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": spec.province_code}}, f"{spec.region_id} region selector is invalid")
        partitioning = manifest.get("partitioning")
        _require(isinstance(partitioning, dict) and partitioning.get("algorithm") == PARTITION_ALGORITHM and isinstance(partitioning.get("bucketCount"), int) and 1 <= partitioning["bucketCount"] <= MAX_BUCKET_COUNT and partitioning.get("key") == "productEvidenceKey", f"{spec.region_id} partitioning metadata is invalid")
        bucket_count = partitioning["bucketCount"]
        region_bucket_counts.append(bucket_count)
        if expected_bucket_count is not None:
            _require(bucket_count == expected_bucket_count, f"{spec.region_id} bucket count differs from expected")
        files = manifest.get("files")
        _require(isinstance(files, dict) and set(files) == {"searchIndex", "storeIndex", "offerPartitions"}, f"{spec.region_id} file set is invalid")
        search_descriptor = files["searchIndex"]
        store_descriptor = files["storeIndex"]
        _require(isinstance(search_descriptor, dict) and search_descriptor.get("kind") == "PRODUCT_SEARCH" and search_descriptor.get("dependencies") == [], f"{spec.region_id} search descriptor is invalid")
        _require(isinstance(store_descriptor, dict) and store_descriptor.get("kind") == "STORE_DIRECTORY" and store_descriptor.get("dependencies") == [], f"{spec.region_id} store descriptor is invalid")
        search_path = _hash_descriptor(snapshot_root, search_descriptor, f"regions/{spec.region_id}/{SEARCH_INDEX_FILE}", f"{spec.region_id} search index")
        store_path = _hash_descriptor(snapshot_root, store_descriptor, f"regions/{spec.region_id}/{STORE_INDEX_FILE}", f"{spec.region_id} store index")
        product_keys: set[str] = set()
        previous_product_id = None
        product_count = 0
        valid_gtins: set[str] = set()
        for record in _iter_gzip_records(search_path, search_descriptor, f"{spec.region_id} search index"):
            _validate_product(record, bucket_count=bucket_count, product_keys=product_keys, label=f"{spec.region_id} search index")
            product_count += 1
            if record["gtin"] is not None:
                valid_gtins.add(record["gtin"])
        store_keys: set[str] = set()
        store_count = 0
        for record in _iter_gzip_records(store_path, store_descriptor, f"{spec.region_id} store index"):
            _validate_store(record, region=spec, store_keys=store_keys, label=f"{spec.region_id} store index")
            store_count += 1
        partitions = files["offerPartitions"]
        _require(isinstance(partitions, list) and len(partitions) == bucket_count, f"{spec.region_id} partition count is invalid")
        seen_partition_ids: set[str] = set()
        offer_ids: set[int] = set()
        offer_count = 0
        promotion_count = 0
        compressed_sum = search_descriptor["bytes"] + store_descriptor["bytes"]
        uncompressed_sum = search_descriptor["uncompressedBytes"] + store_descriptor["uncompressedBytes"]
        for bucket, partition in enumerate(partitions):
            _require(isinstance(partition, dict), f"{spec.region_id} partition descriptor is invalid")
            partition_id = partition.get("partitionId")
            expected_partition = f"p{bucket:0{max(3, len(str(bucket_count - 1)))}d}"
            _require(partition_id == expected_partition and partition_id not in seen_partition_ids, f"{spec.region_id} partition ordering is invalid")
            seen_partition_ids.add(partition_id)
            _require(partition.get("kind") == "OFFERS" and partition.get("contents") == {"productBucket": bucket, "partitionAlgorithm": PARTITION_ALGORITHM} and partition.get("dependencies") == ["searchIndex", "storeIndex"], f"{spec.region_id} partition metadata is invalid")
            expected_path = f"regions/{spec.region_id}/{PARTITIONS_DIR}/{PARTITION_FILE_TEMPLATE.format(bucket=bucket)}"
            partition_path = _hash_descriptor(snapshot_root, partition, expected_path, f"{spec.region_id}/{partition_id}")
            compressed_sum += partition["bytes"]
            uncompressed_sum += partition["uncompressedBytes"]
            for record in _iter_gzip_records(partition_path, partition, f"{spec.region_id}/{partition_id}"):
                promotion_count += _validate_offer(record, region=spec, bucket_count=bucket_count, expected_release_date=expected_release_date, product_keys=product_keys, store_keys=store_keys, offer_ids=offer_ids, label=expected_path)
                offer_count += 1
        counts = manifest.get("counts")
        _require(isinstance(counts, dict) and counts.get("stores") == store_count and counts.get("productEvidenceRecords") == product_count and counts.get("offers") == offer_count and counts.get("promotions") == promotion_count and counts.get("selectedAcceptedObservations") == offer_count, f"{spec.region_id} counts do not match files")
        _require(entry.get("counts") == counts, f"{spec.region_id} bootstrap counts differ from manifest")
        size = manifest.get("size")
        _require(size == {"compressedBytes": compressed_sum, "uncompressedBytes": uncompressed_sum, "searchIndexBytes": search_descriptor["bytes"], "storeIndexBytes": store_descriptor["bytes"], "offerPartitionBytes": compressed_sum - search_descriptor["bytes"] - store_descriptor["bytes"], "fileCount": 2 + bucket_count}, f"{spec.region_id} size metadata is invalid")
        totals["stores"] += store_count
        totals["productEvidenceRecords"] += product_count
        totals["offers"] += offer_count
        totals["promotions"] += promotion_count
    _require(seen_regions == [region.region_id for region in ARGENTINA_REGIONS], "bootstrap regions are not in stable order")
    _require(integrity.get("regionIds") == seen_regions, "root integrity region list is invalid")
    bootstrap_partitioning = bootstrap.get("partitioning")
    _require(isinstance(bootstrap_partitioning, dict) and set(bootstrap_partitioning) == {"algorithm", "bucketCount", "key"} and bootstrap_partitioning.get("algorithm") == PARTITION_ALGORITHM and bootstrap_partitioning.get("key") == "productEvidenceKey" and isinstance(bootstrap_partitioning.get("bucketCount"), int) and 1 <= bootstrap_partitioning["bucketCount"] <= MAX_BUCKET_COUNT, "bootstrap partitioning metadata is invalid")
    _require(region_bucket_counts and all(value == bootstrap_partitioning["bucketCount"] for value in region_bucket_counts), "region bucket counts differ from bootstrap")
    _require(bootstrap.get("totals") == {"publishableRegions": len(ARGENTINA_REGIONS), **totals}, "bootstrap totals do not match regions")
    return {"bootstrapSha256": bootstrap_hash, "regions": len(regions), **totals}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-root", required=True, type=Path)
    parser.add_argument("--expected-outer-sha256", default=EXPECTED_OUTER_SHA256)
    parser.add_argument("--expected-outer-bytes", type=int, default=EXPECTED_OUTER_BYTES)
    parser.add_argument("--expected-release-date", default=RELEASE_DATE)
    parser.add_argument("--expected-accepted-sha256", default=EXPECTED_ACCEPTED_SHA256)
    parser.add_argument("--expected-national-index-sha256", default=EXPECTED_NATIONAL_INDEX_SHA256)
    parser.add_argument("--bucket-count", type=int, default=DEFAULT_BUCKET_COUNT)
    args = parser.parse_args(argv)
    try:
        result = verify_query_selective_mobile(args.snapshot_root, expected_outer_sha256=args.expected_outer_sha256, expected_outer_bytes=args.expected_outer_bytes, expected_release_date=args.expected_release_date, expected_accepted_sha256=args.expected_accepted_sha256, expected_national_index_sha256=args.expected_national_index_sha256, expected_bucket_count=args.bucket_count)
    except (QuerySelectiveMobileVerificationError, OSError, InvalidOperation, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"query-selective mobile verification failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Build a deterministic query-selective Argentina SEPA mobile contract.

The input is the verified national SQLite-shard release.  Product identity
and store metadata are each written once per region, while offers are split by
a stable SHA-256 product bucket.  A future static-file client can therefore
download the bootstrap, one region manifest, the search/store indexes, and
only the offer buckets selected by a bounded product search.  This tool is
offline/provider-edge tooling; it does not perform HTTP, networking, or
Android asset generation.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import tempfile
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterator, Mapping

try:
    from tools.build_argentina_sepa_national_shards import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        MOBILE_COMPATIBILITY_VERSION,
        NATIONAL_INDEX_SCHEMA_VERSION,
        NationalShardError,
        _canonical_json,
        _sha256_file,
    )
    from tools.build_argentina_sepa_regional_snapshot import (
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        RegionSpec,
        _canonical_decimal,
        _canonical_timestamp,
    )
    from tools.verify_argentina_sepa_national_shards import verify_national_shards
except ModuleNotFoundError:  # direct ``python tools/build_...py`` invocation
    from build_argentina_sepa_national_shards import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        MOBILE_COMPATIBILITY_VERSION,
        NATIONAL_INDEX_SCHEMA_VERSION,
        NationalShardError,
        _canonical_json,
        _sha256_file,
    )
    from build_argentina_sepa_regional_snapshot import (
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        RegionSpec,
        _canonical_decimal,
        _canonical_timestamp,
    )
    from verify_argentina_sepa_national_shards import verify_national_shards


QUERY_SELECTIVE_SCHEMA_VERSION = "argentina-sepa-query-selective-mobile-v1"
QUERY_SELECTIVE_POLICY_VERSION = "argentina-sepa-query-selective-policy-v1"
QUERY_SELECTIVE_COMPATIBILITY_VERSION = "argentina-sepa-query-selective-contract-v1"
PARTITION_ALGORITHM = "SHA256_PRODUCT_EVIDENCE_KEY_MOD_N"
DEFAULT_BUCKET_COUNT = 64
MAX_BUCKET_COUNT = 256
EXPECTED_ACCEPTED_SHA256 = "a5554d60a383acb83cf9f573a0e5f5db834c830a7a92e7db5051b09320e2da8d"
EXPECTED_NATIONAL_INDEX_SHA256 = "8449a879442e5437e2f644af8a7aa447576f6435240424ed3c3ddabc815ba46a"
RELEASE_DATE = "2026-09-06"
BOOTSTRAP_FILE = "bootstrap.json"
REGION_MANIFEST_FILE = "manifest.json"
SEARCH_INDEX_FILE = "search-index.jsonl.gz"
STORE_INDEX_FILE = "store-index.jsonl.gz"
PARTITIONS_DIR = "partitions"
PARTITION_FILE_TEMPLATE = "p{bucket:03d}.jsonl.gz"


class QuerySelectiveMobileError(NationalShardError):
    """A query-selective input or generated artifact failed a gate."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise QuerySelectiveMobileError(message)


def _stable_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _read_canonical_json(path: Path, label: str) -> dict[str, Any]:
    _require(path.is_file(), f"Missing {label}: {path}")
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise QuerySelectiveMobileError(f"Invalid {label}: {path}") from exc
    _require(raw == _canonical_json(value), f"{label} is not canonical JSON")
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _canonical_generated_at(value: str) -> str:
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise QuerySelectiveMobileError("generated_at is not ISO-8601") from exc
    _require(parsed.tzinfo is not None, "generated_at needs an explicit timezone")
    return parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


class _JsonlGzipWriter:
    """Canonical JSONL gzip writer with deterministic headers and counters."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._raw = path.open("wb")
        self._gzip = gzip.GzipFile(fileobj=self._raw, mode="wb", filename="", mtime=0, compresslevel=9)
        self._uncompressed_hash = hashlib.sha256()
        self.records = 0
        self.uncompressed_bytes = 0
        self._closed = False

    def write(self, value: Mapping[str, Any]) -> None:
        _require(not self._closed, f"Writer is closed: {self.path}")
        data = _canonical_json(value)
        self._gzip.write(data)
        self._uncompressed_hash.update(data)
        self.records += 1
        self.uncompressed_bytes += len(data)

    def close(self) -> dict[str, Any]:
        if self._closed:
            return {
                "path": self.path.name,
                "compression": "gzip",
                "recordCount": self.records,
                "bytes": self.path.stat().st_size,
                "uncompressedBytes": self.uncompressed_bytes,
                "sha256": _sha256_file(self.path),
                "uncompressedSha256": self._uncompressed_hash.hexdigest(),
            }
        self._closed = True
        self._gzip.close()
        self._raw.close()
        return {
            "path": self.path.name,
            "compression": "gzip",
            "recordCount": self.records,
            "bytes": self.path.stat().st_size,
            "uncompressedBytes": self.uncompressed_bytes,
            "sha256": _sha256_file(self.path),
            "uncompressedSha256": self._uncompressed_hash.hexdigest(),
        }


@dataclass(frozen=True)
class _FileResult:
    descriptor: dict[str, Any]
    relative_path: str


def product_partition_id(product_key: str, bucket_count: int = DEFAULT_BUCKET_COUNT) -> str:
    """Return a stable product bucket; Python's process-randomized hash is never used."""

    _require(isinstance(product_key, str) and product_key, "product key is required")
    _require(isinstance(bucket_count, int) and 1 <= bucket_count <= MAX_BUCKET_COUNT, "bucket count is invalid")
    digest = hashlib.sha256(product_key.encode("utf-8")).digest()
    bucket = int.from_bytes(digest[:8], "big") % bucket_count
    return partition_id_for_bucket(bucket, bucket_count)


def partition_id_for_bucket(bucket: int, bucket_count: int = DEFAULT_BUCKET_COUNT) -> str:
    """Format one validated numeric bucket as its stable partition id."""

    _require(isinstance(bucket_count, int) and 1 <= bucket_count <= MAX_BUCKET_COUNT, "bucket count is invalid")
    _require(isinstance(bucket, int) and 0 <= bucket < bucket_count, "partition bucket is invalid")
    width = max(3, len(str(bucket_count - 1)))
    return f"p{bucket:0{width}d}"


def _product_key(commerce_id: str, provider_product_id: str) -> str:
    return f"ar-sepa-product:{commerce_id}:{provider_product_id}"


def _store_key(commerce_id: str, banner_id: str, provider_store_id: str) -> str:
    return f"ar-sepa-store:{commerce_id}:{banner_id}:{provider_store_id}"


def offer_key(region_id: str, offer_id: int) -> str:
    _require(isinstance(region_id, str) and region_id, "region id is required")
    _require(isinstance(offer_id, int) and offer_id > 0, "offer id is invalid")
    return f"ar-sepa-offer:{region_id}:{offer_id}"


def _load_variants(connection: sqlite3.Connection, table: str, id_column: str) -> dict[int, dict[str, list[str]]]:
    values: dict[int, dict[str, list[str]]] = defaultdict(lambda: defaultdict(list))
    for item_id, field, value in connection.execute(f"SELECT {id_column},field,value FROM {table} ORDER BY {id_column},field,value"):
        values[int(item_id)][field].append(value)
    return {item_id: dict(fields) for item_id, fields in values.items()}


def _load_money(connection: sqlite3.Connection) -> dict[int, str]:
    return {int(item_id): amount for item_id, amount in connection.execute("SELECT money_id,amount FROM money ORDER BY money_id")}


def _load_packages(connection: sqlite3.Connection) -> dict[int, tuple[str, str]]:
    return {int(item_id): (sha256, name) for item_id, sha256, name in connection.execute("SELECT package_id,sha256,name FROM packages ORDER BY package_id")}


def _load_times(connection: sqlite3.Connection) -> dict[int, str]:
    return {int(item_id): value for item_id, value in connection.execute("SELECT time_id,value FROM provider_times ORDER BY time_id")}


def _load_references(connection: sqlite3.Connection, money: Mapping[int, str]) -> dict[int, dict[str, Any]]:
    values: dict[int, dict[str, Any]] = {}
    for reference_id, amount_id, quantity_raw, unit_raw, role in connection.execute(
        "SELECT reference_id,amount_id,quantity_raw,unit_raw,semantic_role FROM reference_prices ORDER BY reference_id"
    ):
        _require(role == "reference_price_not_current_offer", "reference price role is invalid")
        values[int(reference_id)] = {
            "amount": money[int(amount_id)] if amount_id is not None else None,
            "currency": CURRENCY,
            "quantityRaw": quantity_raw,
            "unitRaw": unit_raw,
            "semanticRole": role,
        }
    return values


def _variants_or_none(values: Mapping[str, list[str]] | None) -> dict[str, list[str]] | None:
    if not values:
        return None
    return {field: list(items) for field, items in sorted(values.items()) if items}


def _product_record(row: tuple[Any, ...], variants: Mapping[int, dict[str, list[str]]], bucket_count: int) -> dict[str, Any]:
    product_id, commerce_id, provider_id, gtin, gtin_status, name, brand, quantity_json, quantity_status, metadata_status = row
    key = _product_key(commerce_id, provider_id)
    quantity = json.loads(quantity_json) if quantity_json is not None else None
    record: dict[str, Any] = {
        "productEvidenceKey": key,
        "commerceId": commerce_id,
        "providerProductId": provider_id,
        "gtin": gtin,
        "gtinStatus": gtin_status,
        "name": name,
        "brand": brand,
        "quantity": quantity,
        "quantityStatus": quantity_status,
        "metadataStatus": metadata_status,
        "partitionId": product_partition_id(key, bucket_count),
    }
    metadata_variants = _variants_or_none(variants.get(int(product_id)))
    if metadata_variants:
        record["metadataVariants"] = metadata_variants
    return record


def _store_record(row: tuple[Any, ...], variants: Mapping[int, dict[str, list[str]]]) -> dict[str, Any]:
    (
        store_id,
        commerce_id,
        banner_id,
        provider_store_id,
        name,
        store_type,
        street,
        number,
        postal_code,
        locality,
        province,
        latitude,
        longitude,
        geo_status,
        metadata_status,
    ) = row
    record: dict[str, Any] = {
        "storeKey": _store_key(commerce_id, banner_id, provider_store_id),
        "commerceId": commerce_id,
        "bannerId": banner_id,
        "providerStoreId": provider_store_id,
        "name": name,
        "type": store_type,
        "address": {"street": street, "number": number, "postalCode": postal_code},
        "locality": locality,
        "province": province,
        "latitude": latitude,
        "longitude": longitude,
        "geoStatus": geo_status,
        "metadataStatus": metadata_status,
    }
    metadata_variants = _variants_or_none(variants.get(int(store_id)))
    if metadata_variants:
        record["metadataVariants"] = metadata_variants
    return record


class _PromotionCursor:
    """One forward-only promotion cursor, keeping promotion memory per offer bounded."""

    def __init__(self, connection: sqlite3.Connection, money: Mapping[int, str]) -> None:
        self._cursor = connection.execute(
            "SELECT offer_id,slot,price_id,price_raw,condition,eligibility FROM promotions ORDER BY offer_id,slot"
        )
        self._money = money
        self._next = self._cursor.fetchone()

    def for_offer(self, offer_id: int) -> list[dict[str, Any]]:
        promotions: list[dict[str, Any]] = []
        while self._next is not None and int(self._next[0]) < offer_id:
            raise QuerySelectiveMobileError(f"orphan promotion references offer {self._next[0]}")
        while self._next is not None and int(self._next[0]) == offer_id:
            _, slot, price_id, price_raw, condition, eligibility = self._next
            _require(slot in {1, 2}, "promotion slot is invalid")
            _require(eligibility == "UNKNOWN", "promotion eligibility escaped UNKNOWN")
            promotions.append(
                {
                    "slot": int(slot),
                    "price": {"amount": self._money[int(price_id)], "currency": CURRENCY} if price_id is not None else None,
                    "priceRaw": price_raw,
                    "condition": condition,
                    "eligibility": eligibility,
                }
            )
            self._next = self._cursor.fetchone()
        return promotions


def _offer_record(
    row: tuple[Any, ...],
    *,
    region: RegionSpec,
    expected_release_date: str,
    money: Mapping[int, str],
    packages: Mapping[int, tuple[str, str]],
    times: Mapping[int, str],
    references: Mapping[int, dict[str, Any]],
    promotions: list[dict[str, Any]],
) -> dict[str, Any]:
    (
        raw_offer_id,
        store_id,
        product_id,
        list_price_id,
        reference_id,
        package_id,
        source_row,
        update_time_id,
        release_date,
        freshness_status,
        availability,
        store_commerce,
        store_banner,
        provider_store_id,
        product_commerce,
        provider_product_id,
    ) = row
    offer_id = int(raw_offer_id)
    _require(isinstance(store_commerce, str) and isinstance(product_commerce, str), "offer identity is malformed")
    product_key = _product_key(product_commerce, provider_product_id)
    store_key = _store_key(store_commerce, store_banner, provider_store_id)
    amount = money.get(int(list_price_id))
    _require(amount is not None, "offer list price reference is missing")
    package = packages.get(int(package_id))
    _require(package is not None, "offer package reference is missing")
    if update_time_id is not None:
        _require(int(update_time_id) in times, "offer update-time reference is missing")
    record: dict[str, Any] = {
        "offerKey": offer_key(region.region_id, offer_id),
        "offerId": offer_id,
        "productEvidenceKey": product_key,
        "storeKey": store_key,
        "listPrice": {"amount": amount, "currency": CURRENCY},
        "referencePrice": references.get(int(reference_id)) if reference_id is not None else None,
        "packageProvenance": {"sha256": package[0], "name": package[1]},
        "sourceRow": int(source_row),
        "providerUpdateTime": times.get(int(update_time_id)) if update_time_id is not None else None,
        "releaseDate": release_date,
        "freshnessStatus": freshness_status,
        "availability": availability,
        "promotions": promotions,
    }
    _require(record["releaseDate"] == expected_release_date and record["freshnessStatus"] == "FRESH", "offer freshness boundary changed")
    _require(record["availability"] == AVAILABILITY, "offer availability boundary changed")
    return record


def _decompress_national_shard(shard_path: Path, descriptor: Mapping[str, Any]) -> tuple[Path, tempfile.TemporaryDirectory[str]]:
    _require(shard_path.is_file(), f"Missing national shard: {shard_path}")
    _require(shard_path.stat().st_size == descriptor["bytes"], "national shard byte count changed")
    _require(_sha256_file(shard_path) == descriptor["sha256"], "national shard hash changed")
    directory: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory(prefix="argentina-query-input-")
    raw_path = Path(directory.name) / "shard.sqlite"
    try:
        with gzip.open(shard_path, "rb") as source, raw_path.open("wb") as target:
            shutil.copyfileobj(source, target, length=1024 * 1024)
        _require(raw_path.stat().st_size == descriptor["uncompressedBytes"], "national shard uncompressed size changed")
        _require(_sha256_file(raw_path) == descriptor["uncompressedSha256"], "national shard uncompressed hash changed")
    except Exception:
        directory.cleanup()
        raise
    return raw_path, directory


def _write_region(
    *,
    national_root: Path,
    national_index: Mapping[str, Any],
    region: RegionSpec,
    output_region: Path,
    generated_at: str,
    bucket_count: int,
    expected_release_date: str,
) -> dict[str, Any]:
    entry = next((item for item in national_index["regions"] if item.get("provinceCode") == region.province_code), None)
    _require(isinstance(entry, dict), f"National index lacks {region.province_code}")
    descriptor = entry.get("shard")
    _require(isinstance(descriptor, dict), f"National index shard descriptor lacks {region.region_id}")
    shard_path = national_root / descriptor["path"]
    raw_path, temporary = _decompress_national_shard(shard_path, descriptor)
    output_region.mkdir(parents=True, exist_ok=True)
    search_writer = _JsonlGzipWriter(output_region / SEARCH_INDEX_FILE)
    store_writer = _JsonlGzipWriter(output_region / STORE_INDEX_FILE)
    partition_writers = {
        partition_id_for_bucket(bucket, bucket_count): _JsonlGzipWriter(
            output_region / PARTITIONS_DIR / PARTITION_FILE_TEMPLATE.format(bucket=bucket)
        )
        for bucket in range(bucket_count)
    }
    try:
        connection = sqlite3.connect(str(raw_path))
        try:
            product_variants = _load_variants(connection, "product_variants", "product_id")
            store_variants = _load_variants(connection, "store_variants", "store_id")
            money = _load_money(connection)
            packages = _load_packages(connection)
            times = _load_times(connection)
            references = _load_references(connection, money)
            for row in connection.execute(
                "SELECT product_id,commerce_id,provider_product_id,gtin,gtin_status,name,brand,quantity_json,quantity_status,metadata_status FROM products ORDER BY product_id"
            ):
                search_writer.write(_product_record(row, product_variants, bucket_count))
            for row in connection.execute(
                "SELECT store_id,commerce_id,banner_id,provider_store_id,name,type,street,number,postal_code,locality,province,latitude,longitude,geo_status,metadata_status FROM stores ORDER BY store_id"
            ):
                store_writer.write(_store_record(row, store_variants))
            promotion_cursor = _PromotionCursor(connection, money)
            offer_query = """
                SELECT o.offer_id,o.store_id,o.product_id,o.list_price_id,o.reference_price_id,
                       o.package_id,o.source_row,o.provider_update_time_id,o.release_date,
                       o.freshness_status,o.availability,
                       s.commerce_id,s.banner_id,s.provider_store_id,
                       p.commerce_id,p.provider_product_id
                FROM offers o
                JOIN stores s ON s.store_id=o.store_id
                JOIN products p ON p.product_id=o.product_id
                ORDER BY o.offer_id
            """
            for row in connection.execute(offer_query):
                offer = _offer_record(
                    row,
                    region=region,
                    expected_release_date=expected_release_date,
                    money=money,
                    packages=packages,
                    times=times,
                    references=references,
                    promotions=promotion_cursor.for_offer(int(row[0])),
                )
                partition_writers[product_partition_id(offer["productEvidenceKey"], bucket_count)].write(offer)
            _require(promotion_cursor._next is None, "promotion cursor has unconsumed rows")
            connection.close()
        except Exception:
            connection.close()
            raise
    finally:
        search_descriptor = search_writer.close()
        store_descriptor = store_writer.close()
        partition_descriptors = {
            partition_id: writer.close() for partition_id, writer in partition_writers.items()
        }
        temporary.cleanup()

    counts = dict(entry["counts"])
    _require(counts["offers"] == sum(item["recordCount"] for item in partition_descriptors.values()), f"{region.region_id} offer partition count mismatch")
    _require(counts["productEvidenceRecords"] == search_descriptor["recordCount"], f"{region.region_id} search index count mismatch")
    _require(counts["stores"] == store_descriptor["recordCount"], f"{region.region_id} store index count mismatch")
    files: dict[str, Any] = {
        "searchIndex": {
            **search_descriptor,
            "path": f"regions/{region.region_id}/{SEARCH_INDEX_FILE}",
            "kind": "PRODUCT_SEARCH",
            "dependencies": [],
        },
        "storeIndex": {
            **store_descriptor,
            "path": f"regions/{region.region_id}/{STORE_INDEX_FILE}",
            "kind": "STORE_DIRECTORY",
            "dependencies": [],
        },
    }
    partition_list: list[dict[str, Any]] = []
    for bucket in range(bucket_count):
        partition_id = partition_id_for_bucket(bucket, bucket_count)
        descriptor = partition_descriptors[partition_id]
        partition_list.append(
            {
                **descriptor,
                "path": f"regions/{region.region_id}/{PARTITIONS_DIR}/{PARTITION_FILE_TEMPLATE.format(bucket=bucket)}",
                "partitionId": partition_id,
                "kind": "OFFERS",
                "contents": {"productBucket": bucket, "partitionAlgorithm": PARTITION_ALGORITHM},
                "dependencies": ["searchIndex", "storeIndex"],
            }
        )
    partition_list.sort(key=lambda item: item["partitionId"])
    files["offerPartitions"] = partition_list
    compressed_bytes = sum(int(item["bytes"]) for item in files["offerPartitions"])
    uncompressed_bytes = sum(int(item["uncompressedBytes"]) for item in files["offerPartitions"])
    search_bytes = int(files["searchIndex"]["bytes"])
    store_bytes = int(files["storeIndex"]["bytes"])
    return {
        "artifactSchemaVersion": QUERY_SELECTIVE_SCHEMA_VERSION,
        "policyVersion": QUERY_SELECTIVE_POLICY_VERSION,
        "compatibilityVersion": QUERY_SELECTIVE_COMPATIBILITY_VERSION,
        "atomicCompletion": True,
        "completionState": "COMPLETE",
        "region": {"id": region.region_id, "displayName": region.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": region.province_code}},
        "generatedAt": generated_at,
        "source": dict(national_index["source"]),
        "boundaries": {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "rawProviderDataCommitted": False},
        "partitioning": {"algorithm": PARTITION_ALGORITHM, "bucketCount": bucket_count, "partitionIdWidth": max(3, len(str(bucket_count - 1))), "key": "productEvidenceKey", "crossRegionIdentity": "NOT_JOINED_BY_PARTITION"},
        "counts": counts,
        "storeSummary": dict(entry.get("storeSummary", {})),
        "size": {
            "compressedBytes": search_bytes + store_bytes + compressed_bytes,
            "uncompressedBytes": int(files["searchIndex"]["uncompressedBytes"]) + int(files["storeIndex"]["uncompressedBytes"]) + uncompressed_bytes,
            "searchIndexBytes": search_bytes,
            "storeIndexBytes": store_bytes,
            "offerPartitionBytes": compressed_bytes,
            "fileCount": 2 + bucket_count,
        },
        "files": files,
        "dependencies": {"bootstrap": BOOTSTRAP_FILE, "offerPartitions": ["searchIndex", "storeIndex"]},
    }


def build_query_selective_mobile(
    national_root: Path,
    output_root: Path,
    *,
    generated_at: str,
    bucket_count: int = DEFAULT_BUCKET_COUNT,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
) -> dict[str, Any]:
    """Build the complete query-selective root through an atomic directory rename."""

    generated_at = _canonical_generated_at(generated_at)
    _require(isinstance(bucket_count, int) and 1 <= bucket_count <= MAX_BUCKET_COUNT, "bucket count is invalid")
    national_root = Path(national_root).resolve()
    output_root = Path(output_root).resolve()
    _require(not output_root.exists(), f"Refusing to overwrite query-selective root: {output_root}")
    verify_national_shards(
        national_root,
        expected_outer_sha256=expected_outer_sha256,
        expected_outer_bytes=expected_outer_bytes,
        expected_release_date=expected_release_date,
    )
    national_index_path = national_root / "index.json"
    national_index = _read_canonical_json(national_index_path, "national index")
    if expected_national_index_sha256 is not None:
        _require(_sha256_file(national_index_path) == expected_national_index_sha256, "national index SHA-256 is not the qualified release")
    source = national_index.get("source")
    _require(isinstance(source, dict), "national source provenance is missing")
    _require(source.get("releaseDate") == expected_release_date, "national release date is unexpected")
    _require(source.get("outerSha256") == expected_outer_sha256 and source.get("outerBytes") == expected_outer_bytes, "national outer source proof is unexpected")
    if expected_accepted_sha256 is not None:
        _require(source.get("acceptedObservationsSha256") == expected_accepted_sha256, "national accepted source proof is unexpected")
    _require(source.get("rawProviderDataCommitted") is False, "national source claims raw data was committed")
    _require(national_index.get("artifactSchemaVersion") == NATIONAL_INDEX_SCHEMA_VERSION, "national index schema is unexpected")
    _require(isinstance(national_index.get("regions"), list) and len(national_index["regions"]) == len(ARGENTINA_REGIONS), "national region list is incomplete")
    source_copy = dict(source)
    source_copy["nationalIndexSha256"] = _sha256_file(national_index_path)
    national_index_for_output = dict(national_index)
    national_index_for_output["source"] = source_copy

    output_root.parent.mkdir(parents=True, exist_ok=True)
    partial = Path(tempfile.mkdtemp(prefix=f".{output_root.name}.partial-", dir=str(output_root.parent)))
    completed = False
    region_manifests: list[dict[str, Any]] = []
    try:
        for region in ARGENTINA_REGIONS:
            manifest = _write_region(
                national_root=national_root,
                national_index=national_index_for_output,
                region=region,
                output_region=partial / "regions" / region.region_id,
                generated_at=generated_at,
                bucket_count=bucket_count,
                expected_release_date=expected_release_date,
            )
            manifest_path = partial / "regions" / region.region_id / REGION_MANIFEST_FILE
            manifest_path.write_bytes(_canonical_json(manifest))
            manifest_hash = _sha256_file(manifest_path)
            (manifest_path.parent / "manifest.sha256").write_text(f"{manifest_hash}  {REGION_MANIFEST_FILE}\n", encoding="ascii")
            (manifest_path.parent / "integrity.json").write_bytes(
                _canonical_json({"manifestSha256": manifest_hash, "atomicCompletion": True, "fileNames": [REGION_MANIFEST_FILE, SEARCH_INDEX_FILE, STORE_INDEX_FILE, PARTITIONS_DIR]})
            )
            region_manifests.append(
                {
                    "regionId": region.region_id,
                    "displayName": region.display_name,
                    "provinceCode": region.province_code,
                    "selector": manifest["region"]["selector"],
                    "manifest": {"path": f"regions/{region.region_id}/{REGION_MANIFEST_FILE}", "bytes": manifest_path.stat().st_size, "sha256": manifest_hash, "kind": "REGION_CONTRACT", "dependencies": [BOOTSTRAP_FILE]},
                    "counts": manifest["counts"],
                }
            )
        bootstrap = {
            "artifactSchemaVersion": QUERY_SELECTIVE_SCHEMA_VERSION,
            "policyVersion": QUERY_SELECTIVE_POLICY_VERSION,
            "compatibilityVersion": QUERY_SELECTIVE_COMPATIBILITY_VERSION,
            "atomicCompletion": True,
            "completionState": "COMPLETE",
            "generatedAt": generated_at,
            "release": {"date": source_copy["releaseDate"], "id": f"argentina-sepa-{source_copy['releaseDate']}"},
            "source": source_copy,
            "productionUiAuthorized": False,
            "boundaries": {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "rawProviderDataCommitted": False},
            "distribution": {"granularity": "REGION_MANIFEST_PLUS_PRODUCT_BUCKETS", "bootstrap": BOOTSTRAP_FILE, "activation": "VERIFY_THEN_ATOMIC_LAST_KNOWN_GOOD", "networking": "NOT_IMPLEMENTED_STATIC_FILES_ONLY"},
            "regions": region_manifests,
            "partitioning": {"algorithm": PARTITION_ALGORITHM, "bucketCount": bucket_count, "key": "productEvidenceKey"},
            "totals": {
                "publishableRegions": len(region_manifests),
                "stores": sum(int(item["counts"]["stores"]) for item in region_manifests),
                "productEvidenceRecords": sum(int(item["counts"]["productEvidenceRecords"]) for item in region_manifests),
                "offers": sum(int(item["counts"]["offers"]) for item in region_manifests),
                "promotions": sum(int(item["counts"]["promotions"]) for item in region_manifests),
            },
        }
        bootstrap_path = partial / BOOTSTRAP_FILE
        bootstrap_path.write_bytes(_canonical_json(bootstrap))
        bootstrap_hash = _sha256_file(bootstrap_path)
        (partial / "bootstrap.sha256").write_text(f"{bootstrap_hash}  {BOOTSTRAP_FILE}\n", encoding="ascii")
        integrity = {
            "bootstrapSha256": bootstrap_hash,
            "atomicCompletion": True,
            "schemaVersion": QUERY_SELECTIVE_SCHEMA_VERSION,
            "regionIds": [item["regionId"] for item in region_manifests],
        }
        (partial / "integrity.json").write_bytes(_canonical_json(integrity))
        (partial / "README.txt").write_text("Static query-selective Argentina SEPA contract; verify manifests and selected partition hashes before activation.\n", encoding="utf-8")
        os.replace(partial, output_root)
        completed = True
        return bootstrap
    finally:
        if not completed:
            shutil.rmtree(partial, ignore_errors=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--national-root", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--bucket-count", type=int, default=DEFAULT_BUCKET_COUNT)
    parser.add_argument("--expected-outer-sha256", default=EXPECTED_OUTER_SHA256)
    parser.add_argument("--expected-outer-bytes", type=int, default=EXPECTED_OUTER_BYTES)
    parser.add_argument("--expected-release-date", default=RELEASE_DATE)
    parser.add_argument("--expected-accepted-sha256", default=EXPECTED_ACCEPTED_SHA256)
    parser.add_argument("--expected-national-index-sha256", default=EXPECTED_NATIONAL_INDEX_SHA256)
    args = parser.parse_args(argv)
    try:
        bootstrap = build_query_selective_mobile(
            args.national_root,
            args.output_root,
            generated_at=args.generated_at,
            bucket_count=args.bucket_count,
            expected_outer_sha256=args.expected_outer_sha256,
            expected_outer_bytes=args.expected_outer_bytes,
            expected_release_date=args.expected_release_date,
            expected_accepted_sha256=args.expected_accepted_sha256,
            expected_national_index_sha256=args.expected_national_index_sha256,
        )
    except (QuerySelectiveMobileError, OSError, sqlite3.Error, TypeError, ValueError, json.JSONDecodeError) as exc:
        print(f"query-selective mobile build failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps({"regions": len(bootstrap["regions"]), "path": str(args.output_root), "bucketCount": args.bucket_count}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

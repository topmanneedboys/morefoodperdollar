#!/usr/bin/env python3
"""Offline query access for the Argentina SEPA selective-mobile contract.

This module is the reference consumer of the static contract.  It verifies the
bootstrap, region manifest, and only the search/store/offer files needed for a
bounded query.  No HTTP or other networking is performed.  Product matching
continues to use the existing deterministic Spanish search core; this layer
only joins exact product/store/offer evidence and applies straight-line
Haversine distance.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import math
import os
import shutil
import sqlite3
import tempfile
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence

try:
    from tools.argentina_sepa_search import MAX_CANDIDATES, MAX_RESULTS, SearchError, SearchResult, search_products
    from tools.build_argentina_sepa_national_shards import AVAILABILITY, CURRENCY, DELIVERY_PICKUP, ARGENTINA_REGIONS, _canonical_decimal, _canonical_json
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
        SEARCH_INDEX_FILE,
        STORE_INDEX_FILE,
        offer_key,
        product_partition_id,
    )
    from tools.build_argentina_sepa_regional_snapshot import RegionSpec, _canonical_timestamp
except ModuleNotFoundError:  # direct ``python tools/argentina_sepa_query.py`` invocation
    from argentina_sepa_search import MAX_CANDIDATES, MAX_RESULTS, SearchError, SearchResult, search_products
    from build_argentina_sepa_national_shards import AVAILABILITY, CURRENCY, DELIVERY_PICKUP, ARGENTINA_REGIONS, _canonical_decimal, _canonical_json
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
        SEARCH_INDEX_FILE,
        STORE_INDEX_FILE,
        offer_key,
        product_partition_id,
    )
    from build_argentina_sepa_regional_snapshot import RegionSpec, _canonical_timestamp


RELEASE_DATE = "2026-09-06"
EARTH_RADIUS_KM = 6371.0088
MAX_RADIUS_KM = 500.0
MAX_STRUCTURED_ITEMS = 10
MAX_OFFERS = 100_000
# The shared search core bounds each iterable pass at 100,000 records.  A
# province can legitimately contain more product identities, so this adapter
# scans fixed-size chunks and merges only the bounded top-k results.  The
# explicit total cap prevents an accidentally enormous/corrupt index from
# becoming an unbounded query.
MAX_SEARCH_SCAN_RECORDS = 2_000_000


class ArgentinaSepaQueryError(ValueError):
    """A query was invalid or a selective artifact failed closed."""


@dataclass(frozen=True)
class RegionContract:
    root: Path
    region: RegionSpec
    bootstrap: Mapping[str, Any]
    manifest: Mapping[str, Any]
    search_descriptor: Mapping[str, Any]
    store_descriptor: Mapping[str, Any]
    partition_descriptors: Mapping[str, Mapping[str, Any]]
    bootstrap_bytes: int
    manifest_bytes: int


@dataclass(frozen=True)
class QueryPlan:
    region_id: str
    product_queries: tuple[str, ...]
    product_candidates: tuple[Mapping[str, Any], ...]
    partition_ids: tuple[str, ...]
    bootstrap_bytes: int
    region_manifest_bytes: int
    search_index_bytes: int
    store_index_bytes: int
    offer_partition_bytes: int
    file_count: int

    @property
    def total_bytes(self) -> int:
        return self.bootstrap_bytes + self.region_manifest_bytes + self.search_index_bytes + self.store_index_bytes + self.offer_partition_bytes

    def as_dict(self) -> dict[str, Any]:
        return {
            "regionId": self.region_id,
            "productQueries": list(self.product_queries),
            "productCandidates": [dict(item) for item in self.product_candidates],
            "partitionIds": list(self.partition_ids),
            "bootstrapBytes": self.bootstrap_bytes,
            "regionManifestBytes": self.region_manifest_bytes,
            "searchIndexBytes": self.search_index_bytes,
            "storeIndexBytes": self.store_index_bytes,
            "offerPartitionBytes": self.offer_partition_bytes,
            "totalBytes": self.total_bytes,
            "fileCount": self.file_count,
        }


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ArgentinaSepaQueryError(message)


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
        raise ArgentinaSepaQueryError(f"Invalid {label}: {path}") from exc
    _require(raw == _canonical_json(value), f"{label} is not canonical JSON")
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _safe_relative(value: Any, label: str) -> Path:
    _require(isinstance(value, str) and value and not os.path.isabs(value), f"{label} path is invalid")
    path = Path(value)
    _require(".." not in path.parts, f"{label} path escapes contract root")
    return path


def _verify_plain_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    _require(descriptor.get("path") == expected_path, f"{label} path is invalid")
    value = descriptor.get("bytes")
    digest = descriptor.get("sha256")
    _require(isinstance(value, int) and value >= 0, f"{label} byte count is invalid")
    _require(isinstance(digest, str) and len(digest) == 64 and digest == digest.lower() and all(char in "0123456789abcdef" for char in digest), f"{label} hash is invalid")
    path = root / _safe_relative(expected_path, label)
    _require(path.is_file() and path.stat().st_size == value and _sha256_file(path) == digest, f"{label} bytes or hash mismatch")
    return path


def _verify_gzip_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    _require(descriptor.get("path") == expected_path and descriptor.get("compression") == "gzip", f"{label} descriptor is invalid")
    for field in ("bytes", "uncompressedBytes", "recordCount"):
        _require(isinstance(descriptor.get(field), int) and descriptor[field] >= 0, f"{label} {field} is invalid")
    for field in ("sha256", "uncompressedSha256"):
        digest = descriptor.get(field)
        _require(isinstance(digest, str) and len(digest) == 64 and digest == digest.lower() and all(char in "0123456789abcdef" for char in digest), f"{label} {field} is invalid")
    path = root / _safe_relative(expected_path, label)
    _require(path.is_file() and path.stat().st_size == descriptor["bytes"] and _sha256_file(path) == descriptor["sha256"], f"{label} bytes or hash mismatch")
    return path


def _iter_gzip_records(path: Path, descriptor: Mapping[str, Any], label: str) -> Iterator[dict[str, Any]]:
    digest = hashlib.sha256()
    byte_count = 0
    try:
        with gzip.open(path, "rb") as handle:
            for line_number, line in enumerate(handle, start=1):
                digest.update(line)
                byte_count += len(line)
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise ArgentinaSepaQueryError(f"{label} line {line_number} is invalid JSON") from exc
                _require(isinstance(value, dict), f"{label} line {line_number} is not an object")
                yield value
    except (OSError, EOFError) as exc:
        raise ArgentinaSepaQueryError(f"{label} gzip stream is invalid") from exc
    _require(byte_count == descriptor["uncompressedBytes"] and digest.hexdigest() == descriptor["uncompressedSha256"], f"{label} uncompressed bytes or hash mismatch")


def _region_spec(region_id: str) -> RegionSpec:
    for region in ARGENTINA_REGIONS:
        if region.region_id == region_id:
            return region
    raise ArgentinaSepaQueryError(f"Unknown Argentina region: {region_id}")


def _bounded_chunked_search(
    products: Iterable[Mapping[str, Any]],
    query: str,
    *,
    limit: int,
    max_candidates: int,
) -> tuple[SearchResult, ...]:
    """Search an index larger than the shared-core pass bound safely.

    Each chunk is at most ``max_candidates`` records, while the total number
    of records scanned is capped explicitly.  Merging per-chunk top-k results
    is exact because the core score is independent for every product and the
    final ordering is deterministic.
    """

    _require(isinstance(max_candidates, int) and 1 <= max_candidates <= MAX_CANDIDATES, "max_candidates is invalid")
    _require(isinstance(limit, int) and 1 <= limit <= MAX_RESULTS, "limit is invalid")
    best: dict[str, SearchResult] = {}
    scanned = 0
    chunk: list[Mapping[str, Any]] = []

    def consume(values: list[Mapping[str, Any]]) -> None:
        if not values:
            return
        for result in search_products(values, query, limit=limit, max_candidates=len(values)):
            previous = best.get(result.product_evidence_key)
            if previous is None or (-result.score, result.product_evidence_key, result.name) < (-previous.score, previous.product_evidence_key, previous.name):
                best[result.product_evidence_key] = result

    for product in products:
        scanned += 1
        _require(scanned <= MAX_SEARCH_SCAN_RECORDS, f"search scan bound exceeded ({MAX_SEARCH_SCAN_RECORDS})")
        chunk.append(product)
        if len(chunk) >= max_candidates:
            consume(chunk)
            chunk = []
    consume(chunk)
    return tuple(sorted(best.values(), key=lambda result: (-result.score, result.product_evidence_key, result.name))[:limit])


def load_region_contract(
    snapshot_root: Path,
    region_id: str,
    *,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
) -> RegionContract:
    """Verify bootstrap/metadata and return descriptors for one exact region."""

    root = Path(snapshot_root).resolve()
    bootstrap_path = root / BOOTSTRAP_FILE
    bootstrap = _read_canonical_json(bootstrap_path, BOOTSTRAP_FILE)
    _require(bootstrap.get("artifactSchemaVersion") == QUERY_SELECTIVE_SCHEMA_VERSION and bootstrap.get("policyVersion") == QUERY_SELECTIVE_POLICY_VERSION and bootstrap.get("compatibilityVersion") == QUERY_SELECTIVE_COMPATIBILITY_VERSION, "bootstrap version is invalid")
    _require(bootstrap.get("atomicCompletion") is True and bootstrap.get("completionState") == "COMPLETE" and bootstrap.get("productionUiAuthorized") is False, "bootstrap completion or UI authorization is invalid")
    _require(bootstrap.get("generatedAt") == _canonical_timestamp(bootstrap.get("generatedAt"), "bootstrap.generatedAt"), "bootstrap timestamp is invalid")
    bootstrap_hash = _sha256_file(bootstrap_path)
    _require((root / "bootstrap.sha256").read_text(encoding="ascii") == f"{bootstrap_hash}  {BOOTSTRAP_FILE}\n", "bootstrap checksum is invalid")
    integrity = _read_canonical_json(root / "integrity.json", "integrity.json")
    _require(
        integrity == {
            "bootstrapSha256": bootstrap_hash,
            "atomicCompletion": True,
            "schemaVersion": QUERY_SELECTIVE_SCHEMA_VERSION,
            "regionIds": [item.region_id for item in ARGENTINA_REGIONS],
        },
        "root integrity metadata is invalid",
    )
    source = bootstrap.get("source")
    _require(isinstance(source, dict) and source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS" and source.get("releaseDate") == expected_release_date and source.get("outerSha256") == expected_outer_sha256 and source.get("outerBytes") == expected_outer_bytes and source.get("rawProviderDataCommitted") is False, "bootstrap source provenance is invalid")
    _require(source.get("license") == "Creative Commons Attribution 4.0" and isinstance(source.get("attribution"), str) and source["attribution"].strip(), "bootstrap source rights metadata is invalid")
    _require(isinstance(source.get("acceptedObservationsSha256"), str) and (expected_accepted_sha256 is None or source["acceptedObservationsSha256"] == expected_accepted_sha256), "bootstrap accepted source proof is invalid")
    _require(isinstance(source.get("nationalIndexSha256"), str) and (expected_national_index_sha256 is None or source["nationalIndexSha256"] == expected_national_index_sha256), "bootstrap national index proof is invalid")
    _require(bootstrap.get("boundaries") == {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "rawProviderDataCommitted": False}, "bootstrap boundaries are invalid")
    region = _region_spec(region_id)
    entries = bootstrap.get("regions")
    _require(isinstance(entries, list), "bootstrap regions are missing")
    entry = next((item for item in entries if isinstance(item, dict) and item.get("regionId") == region_id), None)
    _require(isinstance(entry, dict), f"bootstrap lacks region {region_id}")
    _require(entry.get("provinceCode") == region.province_code and entry.get("displayName") == region.display_name and entry.get("selector") == {"field": "store.province", "operator": "EXACT", "value": region.province_code}, "bootstrap region selector is invalid")
    manifest_descriptor = entry.get("manifest")
    _require(isinstance(manifest_descriptor, dict), "region manifest descriptor is missing")
    manifest_path = _verify_plain_descriptor(root, manifest_descriptor, f"regions/{region_id}/manifest.json", f"{region_id} manifest")
    manifest = _read_canonical_json(manifest_path, f"{region_id} manifest")
    _require(manifest.get("artifactSchemaVersion") == QUERY_SELECTIVE_SCHEMA_VERSION and manifest.get("policyVersion") == QUERY_SELECTIVE_POLICY_VERSION and manifest.get("compatibilityVersion") == QUERY_SELECTIVE_COMPATIBILITY_VERSION and manifest.get("atomicCompletion") is True and manifest.get("completionState") == "COMPLETE", f"{region_id} manifest version/completion is invalid")
    _require(manifest.get("generatedAt") == bootstrap["generatedAt"] and manifest.get("source") == source, f"{region_id} manifest provenance differs from bootstrap")
    _require(manifest.get("region") == {"id": region_id, "displayName": region.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": region.province_code}}, f"{region_id} manifest selector is invalid")
    partitioning = manifest.get("partitioning")
    _require(isinstance(partitioning, dict) and partitioning.get("algorithm") == PARTITION_ALGORITHM and partitioning.get("key") == "productEvidenceKey" and isinstance(partitioning.get("bucketCount"), int) and 1 <= partitioning["bucketCount"] <= MAX_BUCKET_COUNT, f"{region_id} partitioning is invalid")
    _require(bootstrap.get("partitioning") == {"algorithm": PARTITION_ALGORITHM, "bucketCount": partitioning["bucketCount"], "key": "productEvidenceKey"}, "bootstrap partitioning differs from region")
    files = manifest.get("files")
    _require(isinstance(files, dict) and set(files) == {"searchIndex", "storeIndex", "offerPartitions"}, f"{region_id} file set is invalid")
    search_descriptor = files["searchIndex"]
    store_descriptor = files["storeIndex"]
    _require(isinstance(search_descriptor, dict) and search_descriptor.get("kind") == "PRODUCT_SEARCH" and search_descriptor.get("dependencies") == [], "search descriptor is invalid")
    _require(isinstance(store_descriptor, dict) and store_descriptor.get("kind") == "STORE_DIRECTORY" and store_descriptor.get("dependencies") == [], "store descriptor is invalid")
    _verify_gzip_descriptor(root, search_descriptor, f"regions/{region_id}/{SEARCH_INDEX_FILE}", f"{region_id} search index")
    _verify_gzip_descriptor(root, store_descriptor, f"regions/{region_id}/{STORE_INDEX_FILE}", f"{region_id} store index")
    partitions = files["offerPartitions"]
    _require(isinstance(partitions, list) and len(partitions) == partitioning["bucketCount"], f"{region_id} offer partitions are incomplete")
    partition_descriptors: dict[str, Mapping[str, Any]] = {}
    width = max(3, len(str(partitioning["bucketCount"] - 1)))
    for bucket, descriptor in enumerate(partitions):
        _require(isinstance(descriptor, dict), f"{region_id} partition descriptor is invalid")
        partition_id = f"p{bucket:0{width}d}"
        expected_path = f"regions/{region_id}/{PARTITIONS_DIR}/{PARTITION_FILE_TEMPLATE.format(bucket=bucket)}"
        _require(descriptor.get("partitionId") == partition_id and descriptor.get("kind") == "OFFERS" and descriptor.get("contents") == {"productBucket": bucket, "partitionAlgorithm": PARTITION_ALGORITHM} and descriptor.get("dependencies") == ["searchIndex", "storeIndex"], f"{region_id} partition metadata is invalid")
        partition_descriptors[partition_id] = descriptor
    checksum_path = manifest_path.with_name("manifest.sha256")
    _require(checksum_path.is_file() and checksum_path.read_text(encoding="ascii") == f"{_sha256_file(manifest_path)}  manifest.json\n", f"{region_id} manifest checksum is invalid")
    return RegionContract(root, region, bootstrap, manifest, search_descriptor, store_descriptor, partition_descriptors, bootstrap_path.stat().st_size, manifest_path.stat().st_size)


def _search_candidates(contract: RegionContract, query: str, *, product_limit: int, max_candidates: int) -> tuple[tuple[Mapping[str, Any], ...], tuple[SearchResult, ...]]:
    search_path = contract.root / _safe_relative(contract.search_descriptor["path"], "search index")
    try:
        results = _bounded_chunked_search(_iter_gzip_records(search_path, contract.search_descriptor, "search index"), query, limit=product_limit, max_candidates=max_candidates)
    except (SearchError, ArgentinaSepaQueryError) as exc:
        raise ArgentinaSepaQueryError(str(exc)) from exc
    keys = {result.product_evidence_key for result in results}
    records: dict[str, Mapping[str, Any]] = {}
    if keys:
        for record in _iter_gzip_records(search_path, contract.search_descriptor, "search index"):
            if record.get("productEvidenceKey") in keys:
                records[record["productEvidenceKey"]] = record
    ordered = tuple(records[result.product_evidence_key] for result in results if result.product_evidence_key in records)
    return ordered, results


def _validate_location(latitude: Any, longitude: Any, radius_km: Any) -> tuple[float, float, float]:
    try:
        lat = float(latitude)
        lon = float(longitude)
        radius = float(radius_km)
    except (TypeError, ValueError) as exc:
        raise ArgentinaSepaQueryError("latitude, longitude and radiusKm must be numeric") from exc
    _require(math.isfinite(lat) and -90.0 <= lat <= 90.0, "latitude is invalid")
    _require(math.isfinite(lon) and -180.0 <= lon <= 180.0, "longitude is invalid")
    _require(math.isfinite(radius) and 0.0 <= radius <= MAX_RADIUS_KM, "radiusKm is invalid")
    return lat, lon, radius


def straight_line_distance_km(latitude_a: str | float, longitude_a: str | float, latitude_b: str | float, longitude_b: str | float) -> float:
    """Return the documented spherical Haversine straight-line distance in km."""

    lat_a, lon_a, _ = _validate_location(latitude_a, longitude_a, 0.0)
    lat_b, lon_b, _ = _validate_location(latitude_b, longitude_b, 0.0)
    delta_lat = math.radians(lat_b - lat_a)
    delta_lon = math.radians(lon_b - lon_a)
    a = math.sin(delta_lat / 2.0) ** 2 + math.cos(math.radians(lat_a)) * math.cos(math.radians(lat_b)) * math.sin(delta_lon / 2.0) ** 2
    a = min(1.0, max(0.0, a))
    return 2.0 * EARTH_RADIUS_KM * math.asin(math.sqrt(a))


def _load_stores(contract: RegionContract) -> dict[str, Mapping[str, Any]]:
    path = contract.root / _safe_relative(contract.store_descriptor["path"], "store index")
    stores: dict[str, Mapping[str, Any]] = {}
    for record in _iter_gzip_records(path, contract.store_descriptor, "store index"):
        key = record.get("storeKey")
        _require(isinstance(key, str) and key not in stores, "store index contains a duplicate key")
        _require(record.get("province") == contract.region.province_code, "store index crosses region boundary")
        _require(record.get("geoStatus") in {"VALID", "GEO_INCOMPLETE", "GEO_CONFLICTING"}, "store index geo status is invalid")
        if record["geoStatus"] == "VALID":
            _require(isinstance(record.get("latitude"), str) and isinstance(record.get("longitude"), str), "valid store coordinates are incomplete")
            _validate_location(record["latitude"], record["longitude"], 0.0)
        elif record["geoStatus"] == "GEO_CONFLICTING":
            _require(record.get("latitude") is None and record.get("longitude") is None, "conflicting store exposes coordinates")
        stores[key] = record
    return stores


def _offer_result(offer: Mapping[str, Any], product: Mapping[str, Any], store: Mapping[str, Any], distance: float) -> dict[str, Any]:
    result = {
        "offerKey": offer["offerKey"],
        "offerId": offer["offerId"],
        "productEvidenceKey": offer["productEvidenceKey"],
        "name": product.get("name"),
        "brand": product.get("brand"),
        "gtin": product.get("gtin"),
        "gtinStatus": product.get("gtinStatus"),
        "quantity": product.get("quantity"),
        "quantityStatus": product.get("quantityStatus"),
        "storeKey": offer["storeKey"],
        "storeName": store.get("name"),
        "storeType": store.get("type"),
        "storeAddress": store.get("address"),
        "locality": store.get("locality"),
        "province": store.get("province"),
        "storeLatitude": store.get("latitude"),
        "storeLongitude": store.get("longitude"),
        "distanceKm": f"{distance:.6f}",
        "distanceSemantics": "STRAIGHT_LINE_HAVERSINE",
        "listPrice": offer["listPrice"],
        "referencePrice": offer.get("referencePrice"),
        "packageProvenance": offer["packageProvenance"],
        "sourceRow": offer["sourceRow"],
        "providerUpdateTime": offer.get("providerUpdateTime"),
        "releaseDate": offer["releaseDate"],
        "freshnessStatus": offer["freshnessStatus"],
        "availability": offer["availability"],
        "promotions": offer.get("promotions", []),
    }
    return result


def _load_selected_offers(contract: RegionContract, products: Mapping[str, Mapping[str, Any]], stores: Mapping[str, Mapping[str, Any]], partition_ids: Sequence[str], latitude: float, longitude: float, radius: float, *, max_offers: int) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    for partition_id in sorted(set(partition_ids)):
        descriptor = contract.partition_descriptors.get(partition_id)
        _require(descriptor is not None, f"unknown offer partition: {partition_id}")
        path = contract.root / _safe_relative(descriptor["path"], f"{contract.region.region_id}/{partition_id}")
        for offer in _iter_gzip_records(path, descriptor, f"{contract.region.region_id}/{partition_id}"):
            product_key = offer.get("productEvidenceKey")
            if product_key not in products:
                continue
            store = stores.get(offer.get("storeKey"))
            if store is None or store.get("geoStatus") != "VALID":
                continue
            distance = straight_line_distance_km(latitude, longitude, store["latitude"], store["longitude"])
            if distance > radius + 1e-9:
                continue
            selected.append(_offer_result(offer, products[product_key], store, distance))
            if len(selected) > max_offers:
                raise ArgentinaSepaQueryError(f"nearby offer bound exceeded ({max_offers})")
    selected.sort(key=lambda item: (float(item["distanceKm"]), item["productEvidenceKey"], item["storeKey"], item["offerId"]))
    return selected


def plan_query(
    snapshot_root: Path,
    region_id: str,
    product_queries: Sequence[str],
    *,
    product_limit: int = MAX_RESULTS,
    max_candidates: int = MAX_CANDIDATES,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
) -> QueryPlan:
    """Return the exact static files required for one or more product searches."""

    _require(isinstance(product_queries, Sequence) and not isinstance(product_queries, (str, bytes)) and 1 <= len(product_queries) <= MAX_STRUCTURED_ITEMS, "product_queries must contain 1-10 queries")
    queries = tuple(product_queries)
    for query in queries:
        _require(isinstance(query, str), "product query must be text")
    _require(isinstance(product_limit, int) and 1 <= product_limit <= MAX_RESULTS, "product_limit is invalid")
    contract = load_region_contract(
        snapshot_root,
        region_id,
        expected_outer_sha256=expected_outer_sha256,
        expected_outer_bytes=expected_outer_bytes,
        expected_release_date=expected_release_date,
        expected_accepted_sha256=expected_accepted_sha256,
        expected_national_index_sha256=expected_national_index_sha256,
    )
    all_candidates: list[Mapping[str, Any]] = []
    seen_keys: set[str] = set()
    partition_ids: set[str] = set()
    for query in queries:
        candidates, _ = _search_candidates(contract, query, product_limit=product_limit, max_candidates=max_candidates)
        for candidate in candidates:
            key = candidate["productEvidenceKey"]
            if key not in seen_keys:
                seen_keys.add(key)
                all_candidates.append(candidate)
                partition_ids.add(candidate["partitionId"])
    selected_partitions = tuple(sorted(partition_ids))
    offer_bytes = sum(contract.partition_descriptors[item]["bytes"] for item in selected_partitions)
    return QueryPlan(region_id, queries, tuple(all_candidates), selected_partitions, contract.bootstrap_bytes, contract.manifest_bytes, contract.search_descriptor["bytes"], contract.store_descriptor["bytes"], offer_bytes, 4 + len(selected_partitions))


def query_nearby(
    snapshot_root: Path,
    region_id: str,
    *,
    latitude: str | float,
    longitude: str | float,
    radius_km: str | float,
    product_query: str,
    product_limit: int = MAX_RESULTS,
    max_candidates: int = MAX_CANDIDATES,
    max_offers: int = MAX_OFFERS,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
) -> dict[str, Any]:
    """Resolve one bounded product search to exact nearby offer evidence."""

    lat, lon, radius = _validate_location(latitude, longitude, radius_km)
    _require(isinstance(max_offers, int) and 1 <= max_offers <= MAX_OFFERS, "max_offers is invalid")
    plan = plan_query(
        snapshot_root,
        region_id,
        (product_query,),
        product_limit=product_limit,
        max_candidates=max_candidates,
        expected_outer_sha256=expected_outer_sha256,
        expected_outer_bytes=expected_outer_bytes,
        expected_release_date=expected_release_date,
        expected_accepted_sha256=expected_accepted_sha256,
        expected_national_index_sha256=expected_national_index_sha256,
    )
    contract = load_region_contract(
        snapshot_root,
        region_id,
        expected_outer_sha256=expected_outer_sha256,
        expected_outer_bytes=expected_outer_bytes,
        expected_release_date=expected_release_date,
        expected_accepted_sha256=expected_accepted_sha256,
        expected_national_index_sha256=expected_national_index_sha256,
    )
    products = {item["productEvidenceKey"]: item for item in plan.product_candidates}
    stores = _load_stores(contract)
    offers = _load_selected_offers(contract, products, stores, plan.partition_ids, lat, lon, radius, max_offers=max_offers)
    return {"regionId": region_id, "latitude": f"{lat:.6f}", "longitude": f"{lon:.6f}", "radiusKm": f"{radius:.6f}", "distanceSemantics": "STRAIGHT_LINE_HAVERSINE", "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "productQuery": product_query, "productCandidates": [dict(item) for item in plan.product_candidates], "offers": offers, "queryPlan": plan.as_dict()}


def _validate_structured_items(items: Sequence[Mapping[str, Any]]) -> tuple[dict[str, Any], ...]:
    _require(isinstance(items, Sequence) and not isinstance(items, (str, bytes)) and 1 <= len(items) <= MAX_STRUCTURED_ITEMS, "items must contain 1-10 entries")
    normalized: list[dict[str, Any]] = []
    for index, item in enumerate(items, start=1):
        _require(isinstance(item, Mapping), f"item {index} is not an object")
        query = item.get("query")
        amount = item.get("amount")
        unit = item.get("unit")
        _require(isinstance(query, str) and query.strip(), f"item {index} query is invalid")
        _require(isinstance(unit, str) and unit.strip() and len(unit.strip()) <= 32, f"item {index} unit is invalid")
        try:
            exact_amount = Decimal(str(amount))
        except (InvalidOperation, ValueError, TypeError) as exc:
            raise ArgentinaSepaQueryError(f"item {index} amount is invalid") from exc
        _require(exact_amount.is_finite() and exact_amount > 0, f"item {index} amount must be positive")
        normalized.append({"query": query, "amount": format(exact_amount, "f"), "unit": unit.strip()})
    return tuple(normalized)


def query_structured_request(
    snapshot_root: Path,
    region_id: str,
    *,
    latitude: str | float,
    longitude: str | float,
    radius_km: str | float,
    items: Sequence[Mapping[str, Any]],
    product_limit: int = MAX_RESULTS,
    max_candidates: int = MAX_CANDIDATES,
    max_offers: int = MAX_OFFERS,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
) -> dict[str, Any]:
    """Resolve a bounded multi-item request without doing basket optimization."""

    lat, lon, radius = _validate_location(latitude, longitude, radius_km)
    normalized_items = _validate_structured_items(items)
    _require(isinstance(max_offers, int) and 1 <= max_offers <= MAX_OFFERS, "max_offers is invalid")
    plan = plan_query(
        snapshot_root,
        region_id,
        tuple(item["query"] for item in normalized_items),
        product_limit=product_limit,
        max_candidates=max_candidates,
        expected_outer_sha256=expected_outer_sha256,
        expected_outer_bytes=expected_outer_bytes,
        expected_release_date=expected_release_date,
        expected_accepted_sha256=expected_accepted_sha256,
        expected_national_index_sha256=expected_national_index_sha256,
    )
    contract = load_region_contract(
        snapshot_root,
        region_id,
        expected_outer_sha256=expected_outer_sha256,
        expected_outer_bytes=expected_outer_bytes,
        expected_release_date=expected_release_date,
        expected_accepted_sha256=expected_accepted_sha256,
        expected_national_index_sha256=expected_national_index_sha256,
    )
    products = {item["productEvidenceKey"]: item for item in plan.product_candidates}
    stores = _load_stores(contract)
    all_offers = _load_selected_offers(contract, products, stores, plan.partition_ids, lat, lon, radius, max_offers=max_offers)
    per_item: list[dict[str, Any]] = []
    for item in normalized_items:
        item_candidates, _ = _search_candidates(contract, item["query"], product_limit=product_limit, max_candidates=max_candidates)
        keys = {candidate["productEvidenceKey"] for candidate in item_candidates}
        per_item.append({**item, "productCandidates": [dict(candidate) for candidate in item_candidates], "offers": [offer for offer in all_offers if offer["productEvidenceKey"] in keys]})
    return {"regionId": region_id, "latitude": f"{lat:.6f}", "longitude": f"{lon:.6f}", "radiusKm": f"{radius:.6f}", "distanceSemantics": "STRAIGHT_LINE_HAVERSINE", "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "items": per_item, "queryPlan": plan.as_dict()}


def _national_region_entry(national_root: Path, region_id: str) -> tuple[dict[str, Any], RegionSpec]:
    index = _read_canonical_json(Path(national_root) / "index.json", "national index")
    spec = _region_spec(region_id)
    entry = next((item for item in index.get("regions", []) if isinstance(item, dict) and item.get("regionId") == region_id), None)
    _require(isinstance(entry, dict), f"national index lacks region {region_id}")
    _require(entry.get("provinceCode") == spec.province_code, "national region selector is invalid")
    return index, spec


def _open_national_shard(national_root: Path, region_id: str) -> tuple[sqlite3.Connection, tempfile.TemporaryDirectory[str]]:
    index, _ = _national_region_entry(national_root, region_id)
    entry = next(item for item in index["regions"] if item["regionId"] == region_id)
    descriptor = entry["shard"]
    path = Path(national_root) / descriptor["path"]
    _require(path.is_file() and path.stat().st_size == descriptor["bytes"] and _sha256_file(path) == descriptor["sha256"], "national shard descriptor is invalid")
    directory: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory(prefix="argentina-query-reference-")
    raw = Path(directory.name) / "shard.sqlite"
    try:
        with gzip.open(path, "rb") as source, raw.open("wb") as target:
            shutil.copyfileobj(source, target, length=1024 * 1024)
        _require(raw.stat().st_size == descriptor["uncompressedBytes"] and _sha256_file(raw) == descriptor["uncompressedSha256"], "national shard payload is invalid")
        return sqlite3.connect(str(raw)), directory
    except Exception:
        directory.cleanup()
        raise


def query_full_shard(
    national_root: Path,
    region_id: str,
    *,
    latitude: str | float,
    longitude: str | float,
    radius_km: str | float,
    product_query: str,
    product_limit: int = MAX_RESULTS,
    max_candidates: int = MAX_CANDIDATES,
    max_offers: int = MAX_OFFERS,
) -> dict[str, Any]:
    """Reference query directly over one verified full province SQLite shard."""

    lat, lon, radius = _validate_location(latitude, longitude, radius_km)
    _require(isinstance(max_offers, int) and 1 <= max_offers <= MAX_OFFERS, "max_offers is invalid")
    connection, temporary = _open_national_shard(national_root, region_id)
    try:
        from tools.argentina_sepa_mobile_search import iter_mobile_products
    except ModuleNotFoundError:
        from argentina_sepa_mobile_search import iter_mobile_products
    try:
        candidates = _bounded_chunked_search(iter_mobile_products(connection), product_query, limit=product_limit, max_candidates=max_candidates)
        keys = {result.product_evidence_key for result in candidates}
        if not keys:
            return {"regionId": region_id, "latitude": f"{lat:.6f}", "longitude": f"{lon:.6f}", "radiusKm": f"{radius:.6f}", "distanceSemantics": "STRAIGHT_LINE_HAVERSINE", "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "productQuery": product_query, "productCandidates": [], "offers": []}
        products: dict[str, Mapping[str, Any]] = {}
        product_ids: list[int] = []
        for product_id, commerce, provider_id, gtin, gtin_status, name, brand, quantity_json, quantity_status, metadata_status in connection.execute("SELECT product_id,commerce_id,provider_product_id,gtin,gtin_status,name,brand,quantity_json,quantity_status,metadata_status FROM products ORDER BY product_id"):
            key = f"ar-sepa-product:{commerce}:{provider_id}"
            if key not in keys:
                continue
            products[key] = {"productEvidenceKey": key, "commerceId": commerce, "providerProductId": provider_id, "gtin": gtin, "gtinStatus": gtin_status, "name": name, "brand": brand, "quantity": json.loads(quantity_json) if quantity_json is not None else None, "quantityStatus": quantity_status, "metadataStatus": metadata_status}
            product_ids.append(int(product_id))
        money = {int(item_id): amount for item_id, amount in connection.execute("SELECT money_id,amount FROM money")}
        packages = {int(item_id): (sha256, name) for item_id, sha256, name in connection.execute("SELECT package_id,sha256,name FROM packages")}
        times = {int(item_id): value for item_id, value in connection.execute("SELECT time_id,value FROM provider_times")}
        references = {int(item_id): {"amount": money[int(amount_id)] if amount_id is not None else None, "currency": CURRENCY, "quantityRaw": quantity_raw, "unitRaw": unit_raw, "semanticRole": role} for item_id, amount_id, quantity_raw, unit_raw, role in connection.execute("SELECT reference_id,amount_id,quantity_raw,unit_raw,semantic_role FROM reference_prices")}
        stores: dict[str, Mapping[str, Any]] = {}
        for store_id, commerce, banner, provider_store, name, store_type, street, number, postal_code, locality, province, store_lat, store_lon, geo_status, metadata_status in connection.execute("SELECT store_id,commerce_id,banner_id,provider_store_id,name,type,street,number,postal_code,locality,province,latitude,longitude,geo_status,metadata_status FROM stores"):
            key = f"ar-sepa-store:{commerce}:{banner}:{provider_store}"
            stores[key] = {"storeKey": key, "name": name, "type": store_type, "address": {"street": street, "number": number, "postalCode": postal_code}, "locality": locality, "province": province, "latitude": store_lat, "longitude": store_lon, "geoStatus": geo_status, "metadataStatus": metadata_status}
        promotion_rows: dict[int, list[dict[str, Any]]] = {}
        query_ids = ",".join("?" for _ in product_ids)
        for offer_id, slot, price_id, price_raw, condition, eligibility in connection.execute(f"SELECT pr.offer_id,pr.slot,pr.price_id,pr.price_raw,pr.condition,pr.eligibility FROM promotions pr JOIN offers o ON o.offer_id=pr.offer_id WHERE o.product_id IN ({query_ids}) ORDER BY pr.offer_id,pr.slot", product_ids):
            promotion_rows.setdefault(int(offer_id), []).append({"slot": int(slot), "price": {"amount": money[int(price_id)], "currency": CURRENCY} if price_id is not None else None, "priceRaw": price_raw, "condition": condition, "eligibility": eligibility})
        offers: list[dict[str, Any]] = []
        for row in connection.execute(f"""
            SELECT o.offer_id,o.product_id,o.list_price_id,o.reference_price_id,o.package_id,o.source_row,o.provider_update_time_id,o.release_date,o.freshness_status,o.availability,
                   s.commerce_id,s.banner_id,s.provider_store_id,p.commerce_id,p.provider_product_id
            FROM offers o JOIN stores s ON s.store_id=o.store_id JOIN products p ON p.product_id=o.product_id
            WHERE o.product_id IN ({query_ids}) ORDER BY o.offer_id
        """, product_ids):
            offer_id, product_id, list_price_id, reference_id, package_id, source_row, update_time_id, release_date, freshness, availability, store_commerce, store_banner, provider_store_id, product_commerce, provider_product_id = row
            product_key = f"ar-sepa-product:{product_commerce}:{provider_product_id}"
            store_key = f"ar-sepa-store:{store_commerce}:{store_banner}:{provider_store_id}"
            store = stores.get(store_key)
            product = products.get(product_key)
            if store is None or product is None or store.get("geoStatus") != "VALID":
                continue
            distance = straight_line_distance_km(lat, lon, store["latitude"], store["longitude"])
            if distance > radius + 1e-9:
                continue
            package = packages[int(package_id)]
            offer = {"offerKey": offer_key(region_id, int(offer_id)), "offerId": int(offer_id), "productEvidenceKey": product_key, "storeKey": store_key, "listPrice": {"amount": money[int(list_price_id)], "currency": CURRENCY}, "referencePrice": references.get(int(reference_id)) if reference_id is not None else None, "packageProvenance": {"sha256": package[0], "name": package[1]}, "sourceRow": int(source_row), "providerUpdateTime": times.get(int(update_time_id)) if update_time_id is not None else None, "releaseDate": release_date, "freshnessStatus": freshness, "availability": availability, "promotions": promotion_rows.get(int(offer_id), [])}
            offers.append(_offer_result(offer, product, store, distance))
            if len(offers) > max_offers:
                raise ArgentinaSepaQueryError(f"reference offer bound exceeded ({max_offers})")
        offers.sort(key=lambda item: (float(item["distanceKm"]), item["productEvidenceKey"], item["storeKey"], item["offerId"]))
        return {"regionId": region_id, "latitude": f"{lat:.6f}", "longitude": f"{lon:.6f}", "radiusKm": f"{radius:.6f}", "distanceSemantics": "STRAIGHT_LINE_HAVERSINE", "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "productQuery": product_query, "productCandidates": [result.as_dict() for result in candidates], "offers": offers}
    finally:
        connection.close()
        temporary.cleanup()


__all__ = [
    "ArgentinaSepaQueryError",
    "QueryPlan",
    "RegionContract",
    "load_region_contract",
    "plan_query",
    "query_full_shard",
    "query_nearby",
    "query_structured_request",
    "straight_line_distance_km",
]

#!/usr/bin/env python3
"""Offline range/slice access for the Argentina SEPA micro-partition contract.

The module deliberately reuses the existing deterministic Spanish search and
offer projection.  It adds only contract verification, exact byte-range
member reads, and verified local-cache activation at the provider edge.
"""

from __future__ import annotations

import hashlib
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

try:
    from tools.argentina_sepa_query import (
        MAX_CANDIDATES,
        MAX_OFFERS,
        MAX_RESULTS,
        ArgentinaSepaQueryError,
        _iter_gzip_records,
        _load_stores,
        _offer_result,
        _search_candidates,
        _validate_location,
        straight_line_distance_km,
    )
    from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS, AVAILABILITY, CURRENCY, DELIVERY_PICKUP, _canonical_json
    from tools.build_argentina_sepa_micro_partition_mobile import (
        BOOTSTRAP_FILE,
        MICRO_PARTITION_ALGORITHM,
        MICRO_PARTITION_COMPATIBILITY_VERSION,
        MICRO_PARTITION_POLICY_VERSION,
        MICRO_PARTITION_SCHEMA_VERSION,
        MEMBER_COMPRESSION,
        PACKS_DIR,
        PHYSICAL_PACK_ALGORITHM,
        REGION_MANIFEST_FILE,
        SEARCH_INDEX_FILE,
        STORE_INDEX_FILE,
        logical_partition_id,
    )
    from tools.build_argentina_sepa_query_selective_mobile import EXPECTED_ACCEPTED_SHA256, EXPECTED_NATIONAL_INDEX_SHA256, EXPECTED_OUTER_BYTES, EXPECTED_OUTER_SHA256
    from tools.build_argentina_sepa_regional_snapshot import RegionSpec, _canonical_timestamp
    from tools.verify_argentina_sepa_micro_partition_mobile import decode_member_bytes
except ModuleNotFoundError:  # direct ``python tools/argentina_sepa_micro_partition.py`` invocation
    from argentina_sepa_query import MAX_CANDIDATES, MAX_OFFERS, MAX_RESULTS, ArgentinaSepaQueryError, _iter_gzip_records, _load_stores, _offer_result, _search_candidates, _validate_location, straight_line_distance_km
    from build_argentina_sepa_national_shards import ARGENTINA_REGIONS, AVAILABILITY, CURRENCY, DELIVERY_PICKUP, _canonical_json
    from build_argentina_sepa_micro_partition_mobile import BOOTSTRAP_FILE, MICRO_PARTITION_ALGORITHM, MICRO_PARTITION_COMPATIBILITY_VERSION, MICRO_PARTITION_POLICY_VERSION, MICRO_PARTITION_SCHEMA_VERSION, MEMBER_COMPRESSION, PACKS_DIR, PHYSICAL_PACK_ALGORITHM, REGION_MANIFEST_FILE, SEARCH_INDEX_FILE, STORE_INDEX_FILE, logical_partition_id
    from build_argentina_sepa_query_selective_mobile import EXPECTED_ACCEPTED_SHA256, EXPECTED_NATIONAL_INDEX_SHA256, EXPECTED_OUTER_BYTES, EXPECTED_OUTER_SHA256
    from build_argentina_sepa_regional_snapshot import RegionSpec, _canonical_timestamp
    from verify_argentina_sepa_micro_partition_mobile import decode_member_bytes


RELEASE_DATE = "2026-09-06"
MAX_STRUCTURED_ITEMS = 10


class MicroPartitionQueryError(ArgentinaSepaQueryError):
    """A micro-partition query or cache operation failed closed."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise MicroPartitionQueryError(message)


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
        import json

        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, ValueError) as exc:
        raise MicroPartitionQueryError(f"Invalid {label}: {path}") from exc
    _require(raw == _canonical_json(value) and isinstance(value, dict), f"{label} is not canonical JSON object")
    return value


def _safe_relative(value: Any, label: str) -> Path:
    _require(isinstance(value, str) and value and not os.path.isabs(value), f"{label} path is invalid")
    path = Path(value)
    _require(".." not in path.parts, f"{label} path escapes contract root")
    return path


def _coerce_path(value: Path | str) -> Path:
    raw = os.fspath(value)
    path = Path(raw)
    if isinstance(raw, str) and os.sep != "\\" and "\\" in raw and not path.exists():
        normalized = Path(raw.replace("\\", "/"))
        if normalized.exists():
            return normalized
    return path


@dataclass(frozen=True)
class MicroRegionContract:
    root: Path
    region: RegionSpec
    bootstrap: Mapping[str, Any]
    manifest: Mapping[str, Any]
    search_descriptor: Mapping[str, Any]
    store_descriptor: Mapping[str, Any]
    pack_descriptors: Mapping[str, Mapping[str, Any]]
    partition_descriptors: Mapping[str, Mapping[str, Any]]
    bootstrap_bytes: int
    manifest_bytes: int


@dataclass(frozen=True)
class MicroRegionRouting:
    """Verified routing-only view over a region's published metadata."""

    root: Path
    region: RegionSpec
    bootstrap: Mapping[str, Any]
    manifest: Mapping[str, Any]
    store_descriptor: Mapping[str, Any]
    bootstrap_bytes: int
    manifest_bytes: int


@dataclass(frozen=True)
class MicroQueryPlan:
    region_id: str
    product_queries: tuple[str, ...]
    product_candidates: tuple[Mapping[str, Any], ...]
    partition_ids: tuple[str, ...]
    slices: tuple[Mapping[str, Any], ...]
    bootstrap_bytes: int
    region_manifest_bytes: int
    search_index_bytes: int
    store_index_bytes: int
    compressed_bytes: int
    decompressed_bytes: int
    physical_pack_ids: tuple[str, ...]
    file_count: int

    @property
    def total_bytes(self) -> int:
        return self.bootstrap_bytes + self.region_manifest_bytes + self.search_index_bytes + self.store_index_bytes + self.compressed_bytes

    @property
    def physical_pack_bytes(self) -> int:
        return sum(int(item["packBytes"]) for item in self.slices)

    def as_dict(self) -> dict[str, Any]:
        return {
            "regionId": self.region_id,
            "productQueries": list(self.product_queries),
            "productCandidates": [dict(item) for item in self.product_candidates],
            "partitionIds": list(self.partition_ids),
            "physicalPackIds": list(self.physical_pack_ids),
            "slices": [dict(item) for item in self.slices],
            "bootstrapBytes": self.bootstrap_bytes,
            "regionManifestBytes": self.region_manifest_bytes,
            "searchIndexBytes": self.search_index_bytes,
            "storeIndexBytes": self.store_index_bytes,
            "compressedBytes": self.compressed_bytes,
            "decompressedBytes": self.decompressed_bytes,
            "physicalPackBytes": self.physical_pack_bytes,
            "totalBytes": self.total_bytes,
            "fileCount": self.file_count,
        }


def _verify_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str, *, compressed: bool = False, verify_hash: bool = True) -> Path:
    _require(descriptor.get("path") == expected_path, f"{label} path is invalid")
    _require(isinstance(descriptor.get("bytes"), int) and descriptor["bytes"] >= 0, f"{label} byte count is invalid")
    digest = descriptor.get("sha256")
    _require(isinstance(digest, str) and len(digest) == 64 and digest == digest.lower() and all(char in "0123456789abcdef" for char in digest), f"{label} hash is invalid")
    if compressed:
        _require(descriptor.get("compression") == "gzip", f"{label} compression is invalid")
        for field in ("uncompressedBytes", "recordCount"):
            _require(isinstance(descriptor.get(field), int) and descriptor[field] >= 0, f"{label} {field} is invalid")
        uncompressed_hash = descriptor.get("uncompressedSha256")
        _require(isinstance(uncompressed_hash, str) and len(uncompressed_hash) == 64 and uncompressed_hash == uncompressed_hash.lower() and all(char in "0123456789abcdef" for char in uncompressed_hash), f"{label} uncompressed hash is invalid")
    path = root / _safe_relative(expected_path, label)
    _require(path.is_file() and path.stat().st_size == descriptor["bytes"], f"{label} is missing or has the wrong byte count")
    if verify_hash:
        _require(_sha256_file(path) == digest, f"{label} hash mismatch")
    return path


def load_micro_region_contract(
    root: Path,
    region_id: str,
    *,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
) -> MicroRegionContract:
    root = _coerce_path(root).resolve()
    bootstrap_path = root / BOOTSTRAP_FILE
    bootstrap = _read_canonical_json(bootstrap_path, BOOTSTRAP_FILE)
    _require(bootstrap.get("artifactSchemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and bootstrap.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and bootstrap.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION and bootstrap.get("atomicCompletion") is True and bootstrap.get("completionState") == "COMPLETE" and bootstrap.get("productionUiAuthorized") is False, "bootstrap version/completion/authorization is invalid")
    _require(bootstrap.get("generatedAt") == _canonical_timestamp(bootstrap.get("generatedAt"), "bootstrap.generatedAt"), "bootstrap timestamp is invalid")
    bootstrap_hash = _sha256_file(bootstrap_path)
    _require((root / "bootstrap.sha256").read_text(encoding="ascii") == f"{bootstrap_hash}  {BOOTSTRAP_FILE}\n", "bootstrap checksum is invalid")
    integrity = _read_canonical_json(root / "integrity.json", "integrity.json")
    _require(integrity.get("bootstrapSha256") == bootstrap_hash and integrity.get("atomicCompletion") is True and integrity.get("schemaVersion") == MICRO_PARTITION_SCHEMA_VERSION, "root integrity metadata is invalid")
    source = bootstrap.get("source")
    _require(isinstance(source, dict) and source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS" and source.get("releaseDate") == expected_release_date and source.get("outerSha256") == expected_outer_sha256 and source.get("outerBytes") == expected_outer_bytes and source.get("license") == "Creative Commons Attribution 4.0" and source.get("rawProviderDataCommitted") is False, "bootstrap source provenance is invalid")
    if expected_accepted_sha256 is not None:
        _require(source.get("acceptedObservationsSha256") == expected_accepted_sha256, "accepted source hash is not the qualified release")
    if expected_national_index_sha256 is not None:
        _require(source.get("nationalIndexSha256") == expected_national_index_sha256, "national index hash is not the qualified release")
    boundaries = {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "rawProviderDataCommitted": False}
    _require(bootstrap.get("boundaries") == boundaries, "bootstrap boundaries are invalid")
    partitioning = bootstrap.get("partitioning")
    _require(isinstance(partitioning, dict) and partitioning.get("algorithm") == MICRO_PARTITION_ALGORITHM and partitioning.get("key") == "productEvidenceKey" and partitioning.get("physicalPackAlgorithm") == PHYSICAL_PACK_ALGORITHM and partitioning.get("memberCompression") == MEMBER_COMPRESSION and partitioning.get("rangeUnit") == "BYTES", "bootstrap partitioning is invalid")
    logical_count = partitioning.get("logicalPartitionCount")
    pack_count = partitioning.get("physicalPackCount")
    _require(isinstance(logical_count, int) and isinstance(pack_count, int) and logical_count >= pack_count > 0 and logical_count & (logical_count - 1) == 0 and 128 <= logical_count <= 1024, "bootstrap partition counts are invalid")
    spec = next((item for item in ARGENTINA_REGIONS if item.region_id == region_id), None)
    _require(spec is not None, f"unknown Argentina region: {region_id}")
    entry = next((item for item in bootstrap.get("regions", []) if isinstance(item, dict) and item.get("regionId") == region_id), None)
    _require(isinstance(entry, dict) and entry.get("provinceCode") == spec.province_code and entry.get("displayName") == spec.display_name and entry.get("selector") == {"field": "store.province", "operator": "EXACT", "value": spec.province_code}, f"bootstrap region {region_id} is invalid")
    manifest_descriptor = entry.get("manifest")
    _require(isinstance(manifest_descriptor, dict), f"{region_id} manifest descriptor is missing")
    manifest_path = _verify_descriptor(root, manifest_descriptor, f"regions/{region_id}/{REGION_MANIFEST_FILE}", f"{region_id} manifest")
    manifest = _read_canonical_json(manifest_path, f"{region_id} manifest")
    _require(manifest.get("artifactSchemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and manifest.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and manifest.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION and manifest.get("atomicCompletion") is True and manifest.get("completionState") == "COMPLETE" and manifest.get("generatedAt") == bootstrap["generatedAt"] and manifest.get("source") == source and manifest.get("region") == {"id": region_id, "displayName": spec.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": spec.province_code}} and manifest.get("boundaries") == {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "rawProviderDataCommitted": False}, f"{region_id} manifest metadata is invalid")
    _require((manifest_path.with_name("manifest.sha256")).read_text(encoding="ascii") == f"{_sha256_file(manifest_path)}  manifest.json\n", f"{region_id} manifest checksum is invalid")
    _require(manifest.get("partitioning", {}).get("logicalPartitionCount") == logical_count and manifest.get("partitioning", {}).get("physicalPackCount") == pack_count, f"{region_id} partition counts differ from bootstrap")
    files = manifest.get("files")
    _require(isinstance(files, dict) and set(files) == {"searchIndex", "storeIndex", "offerPacks", "logicalPartitions"}, f"{region_id} file set is invalid")
    search_descriptor = files["searchIndex"]
    store_descriptor = files["storeIndex"]
    _verify_descriptor(root, search_descriptor, f"regions/{region_id}/{SEARCH_INDEX_FILE}", f"{region_id} search index", compressed=True)
    _verify_descriptor(root, store_descriptor, f"regions/{region_id}/{STORE_INDEX_FILE}", f"{region_id} store index", compressed=True)
    packs = files["offerPacks"]
    _require(isinstance(packs, list) and len(packs) == pack_count, f"{region_id} pack list is incomplete")
    pack_map: dict[str, Mapping[str, Any]] = {}
    for number, pack in enumerate(packs):
        _require(isinstance(pack, dict) and pack.get("packId") == f"pack{number:03d}" and pack.get("memberCompression") == MEMBER_COMPRESSION and pack.get("schemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and pack.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and pack.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION, f"{region_id} pack metadata is invalid")
        # A query verifies only the requested member range.  Whole-pack hashes
        # are checked by the full verifier/promotion gate, so selecting one
        # slice never requires reading surrounding bytes.
        _verify_descriptor(root, pack, f"regions/{region_id}/{PACKS_DIR}/pack{number:03d}.bin", f"{region_id}/{pack['packId']}", verify_hash=False)
        pack_map[pack["packId"]] = pack
    logicals = files["logicalPartitions"]
    _require(isinstance(logicals, list) and len(logicals) == logical_count, f"{region_id} logical partition list is incomplete")
    partition_map: dict[str, Mapping[str, Any]] = {}
    for bucket, descriptor in enumerate(logicals):
        partition_id = logical_partition_id(bucket, logical_count)
        _require(isinstance(descriptor, dict) and descriptor.get("partitionId") == partition_id and descriptor.get("packId") == f"pack{bucket % pack_count:03d}" and descriptor.get("memberCompression") == MEMBER_COMPRESSION and descriptor.get("byteOffset", -1) >= 0 and descriptor.get("byteLength", -1) == descriptor.get("bytes", -2), f"{region_id}/{partition_id} descriptor is invalid")
        _require(descriptor.get("path") == pack_map[descriptor["packId"]]["path"], f"{region_id}/{partition_id} pack path differs")
        _require(descriptor.get("byteOffset") + descriptor.get("byteLength") <= pack_map[descriptor["packId"]]["bytes"], f"{region_id}/{partition_id} range exceeds pack")
        partition_map[partition_id] = descriptor
    return MicroRegionContract(root, spec, bootstrap, manifest, search_descriptor, store_descriptor, pack_map, partition_map, bootstrap_path.stat().st_size, manifest_path.stat().st_size)


def load_micro_region_routing(
    root: Path,
    region_id: str,
    *,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
) -> MicroRegionRouting:
    """Verify only bootstrap/manifest/store metadata needed for geography.

    Search indexes, offer packs and logical partition descriptors are left
    untouched; a caller can load the full contract only after routing selects
    this region.
    """

    root = _coerce_path(root).resolve()
    bootstrap_path = root / BOOTSTRAP_FILE
    bootstrap = _read_canonical_json(bootstrap_path, BOOTSTRAP_FILE)
    _require(bootstrap.get("artifactSchemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and bootstrap.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and bootstrap.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION and bootstrap.get("atomicCompletion") is True and bootstrap.get("completionState") == "COMPLETE" and bootstrap.get("productionUiAuthorized") is False, "bootstrap version/completion/authorization is invalid")
    _require(bootstrap.get("generatedAt") == _canonical_timestamp(bootstrap.get("generatedAt"), "bootstrap.generatedAt"), "bootstrap timestamp is invalid")
    bootstrap_hash = _sha256_file(bootstrap_path)
    _require((root / "bootstrap.sha256").read_text(encoding="ascii") == f"{bootstrap_hash}  {BOOTSTRAP_FILE}\n", "bootstrap checksum is invalid")
    integrity = _read_canonical_json(root / "integrity.json", "integrity.json")
    _require(integrity.get("bootstrapSha256") == bootstrap_hash and integrity.get("atomicCompletion") is True and integrity.get("schemaVersion") == MICRO_PARTITION_SCHEMA_VERSION, "root integrity metadata is invalid")
    source = bootstrap.get("source")
    _require(isinstance(source, dict) and source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS" and source.get("releaseDate") == expected_release_date and source.get("outerSha256") == expected_outer_sha256 and source.get("outerBytes") == expected_outer_bytes and source.get("license") == "Creative Commons Attribution 4.0" and source.get("rawProviderDataCommitted") is False, "bootstrap source provenance is invalid")
    if expected_accepted_sha256 is not None:
        _require(source.get("acceptedObservationsSha256") == expected_accepted_sha256, "accepted source hash is not the qualified release")
    if expected_national_index_sha256 is not None:
        _require(source.get("nationalIndexSha256") == expected_national_index_sha256, "national index hash is not the qualified release")
    boundaries = {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "rawProviderDataCommitted": False}
    _require(bootstrap.get("boundaries") == boundaries, "bootstrap boundaries are invalid")
    partitioning = bootstrap.get("partitioning")
    _require(isinstance(partitioning, dict) and partitioning.get("algorithm") == MICRO_PARTITION_ALGORITHM and partitioning.get("key") == "productEvidenceKey" and partitioning.get("physicalPackAlgorithm") == PHYSICAL_PACK_ALGORITHM and partitioning.get("memberCompression") == MEMBER_COMPRESSION and partitioning.get("rangeUnit") == "BYTES", "bootstrap partitioning is invalid")
    logical_count = partitioning.get("logicalPartitionCount")
    pack_count = partitioning.get("physicalPackCount")
    _require(isinstance(logical_count, int) and isinstance(pack_count, int) and logical_count >= pack_count > 0 and logical_count & (logical_count - 1) == 0 and 128 <= logical_count <= 1024, "bootstrap partition counts are invalid")
    spec = next((item for item in ARGENTINA_REGIONS if item.region_id == region_id), None)
    _require(spec is not None, f"unknown Argentina region: {region_id}")
    entry = next((item for item in bootstrap.get("regions", []) if isinstance(item, dict) and item.get("regionId") == region_id), None)
    _require(isinstance(entry, dict) and entry.get("provinceCode") == spec.province_code and entry.get("displayName") == spec.display_name and entry.get("selector") == {"field": "store.province", "operator": "EXACT", "value": spec.province_code}, f"bootstrap region {region_id} is invalid")
    manifest_descriptor = entry.get("manifest")
    _require(isinstance(manifest_descriptor, dict), f"{region_id} manifest descriptor is missing")
    manifest_path = _verify_descriptor(root, manifest_descriptor, f"regions/{region_id}/{REGION_MANIFEST_FILE}", f"{region_id} manifest")
    manifest = _read_canonical_json(manifest_path, f"{region_id} manifest")
    _require(manifest.get("artifactSchemaVersion") == MICRO_PARTITION_SCHEMA_VERSION and manifest.get("policyVersion") == MICRO_PARTITION_POLICY_VERSION and manifest.get("compatibilityVersion") == MICRO_PARTITION_COMPATIBILITY_VERSION and manifest.get("atomicCompletion") is True and manifest.get("completionState") == "COMPLETE" and manifest.get("generatedAt") == bootstrap["generatedAt"] and manifest.get("source") == source and manifest.get("region") == {"id": region_id, "displayName": spec.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": spec.province_code}} and manifest.get("boundaries") == {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "rawProviderDataCommitted": False}, f"{region_id} manifest metadata is invalid")
    _require((manifest_path.with_name("manifest.sha256")).read_text(encoding="ascii") == f"{_sha256_file(manifest_path)}  manifest.json\n", f"{region_id} manifest checksum is invalid")
    manifest_partitioning = manifest.get("partitioning")
    _require(isinstance(manifest_partitioning, dict) and manifest_partitioning.get("logicalPartitionCount") == logical_count and manifest_partitioning.get("physicalPackCount") == pack_count, f"{region_id} partition counts differ from bootstrap")
    files = manifest.get("files")
    _require(isinstance(files, dict) and set(files) == {"searchIndex", "storeIndex", "offerPacks", "logicalPartitions"}, f"{region_id} file set is invalid")
    store_descriptor = files.get("storeIndex")
    _require(isinstance(store_descriptor, Mapping), f"{region_id} store descriptor is invalid")
    _verify_descriptor(root, store_descriptor, f"regions/{region_id}/{STORE_INDEX_FILE}", f"{region_id} store index", compressed=True)
    return MicroRegionRouting(root, spec, bootstrap, manifest, store_descriptor, bootstrap_path.stat().st_size, manifest_path.stat().st_size)


def _read_member(contract: MicroRegionContract, partition_id: str) -> list[dict[str, Any]]:
    descriptor = contract.partition_descriptors.get(partition_id)
    _require(descriptor is not None, f"unknown logical partition: {partition_id}")
    pack = contract.pack_descriptors.get(descriptor["packId"])
    _require(pack is not None, f"unknown physical pack: {descriptor.get('packId')}")
    path = contract.root / _safe_relative(pack["path"], f"{contract.region.region_id}/{descriptor['packId']}")
    with path.open("rb") as handle:
        handle.seek(descriptor["byteOffset"])
        data = handle.read(descriptor["byteLength"])
    _require(len(data) == descriptor["byteLength"], f"{contract.region.region_id}/{partition_id} range is truncated")
    try:
        return decode_member_bytes(data, descriptor, f"{contract.region.region_id}/{partition_id}")
    except ValueError as exc:
        raise MicroPartitionQueryError(str(exc)) from exc


def required_slices(plan: MicroQueryPlan, cached_members: Mapping[str, Mapping[str, Any]] | None = None) -> dict[str, Any]:
    cached_members = cached_members or {}
    missing: list[Mapping[str, Any]] = []
    hits: list[str] = []
    for item in plan.slices:
        partition_id = item["partitionId"]
        cached = cached_members.get(partition_id)
        if isinstance(cached, Mapping) and cached.get("sha256") == item["sha256"] and cached.get("bytes") == item["byteLength"] and cached.get("uncompressedSha256") == item["uncompressedSha256"]:
            hits.append(partition_id)
        else:
            missing.append(item)
    return {
        "requestedPartitionIds": list(plan.partition_ids),
        "cachedPartitionIds": hits,
        "missingPartitionIds": [item["partitionId"] for item in missing],
        "slices": [dict(item) for item in missing],
        "physicalPackIds": sorted({item["packId"] for item in missing}),
        "compressedBytes": sum(int(item["byteLength"]) for item in missing),
        "decompressedBytes": sum(int(item["uncompressedBytes"]) for item in missing),
        "cacheHitCount": len(hits),
        "cacheMissCount": len(missing),
    }


def activate_cached_member(cache_root: Path, descriptor: Mapping[str, Any], data: bytes) -> Path:
    """Verify a member, then atomically activate its immutable cache file."""

    try:
        decode_member_bytes(data, descriptor, "cache member")
    except ValueError as exc:
        raise MicroPartitionQueryError(str(exc)) from exc
    cache_root = _coerce_path(cache_root).resolve()
    members = cache_root / "members"
    members.mkdir(parents=True, exist_ok=True)
    partition_id = descriptor.get("partitionId")
    digest = descriptor.get("sha256")
    _require(isinstance(partition_id, str) and isinstance(digest, str), "cache member identity is invalid")
    target = members / f"{partition_id}-{digest}.gz"
    _require(target.parent == members, "cache member path is invalid")
    if target.is_file():
        _require(target.read_bytes() == data, "existing cache member differs")
        return target
    fd, temp_name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".partial", dir=str(members))
    os.close(fd)
    temporary = Path(temp_name)
    try:
        temporary.write_bytes(data)
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    return target


def _load_selected_micro_offers(contract: MicroRegionContract, products: Mapping[str, Mapping[str, Any]], stores: Mapping[str, Mapping[str, Any]], partition_ids: Sequence[str], latitude: float, longitude: float, radius: float, *, max_offers: int) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    for partition_id in sorted(set(partition_ids)):
        for offer in _read_member(contract, partition_id):
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
                raise MicroPartitionQueryError(f"nearby offer bound exceeded ({max_offers})")
    selected.sort(key=lambda item: (float(item["distanceKm"]), item["productEvidenceKey"], item["storeKey"], item["offerId"]))
    return selected


def plan_micro_query(root: Path, region_id: str, product_queries: Sequence[str], *, product_limit: int = MAX_RESULTS, max_candidates: int = MAX_CANDIDATES, expected_outer_sha256: str = EXPECTED_OUTER_SHA256, expected_outer_bytes: int = EXPECTED_OUTER_BYTES, expected_release_date: str = RELEASE_DATE, expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256, expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256) -> MicroQueryPlan:
    _require(isinstance(product_queries, Sequence) and not isinstance(product_queries, (str, bytes)) and 1 <= len(product_queries) <= MAX_STRUCTURED_ITEMS, "product_queries must contain 1-10 queries")
    _require(all(isinstance(query, str) for query in product_queries), "product query must be text")
    _require(isinstance(product_limit, int) and 1 <= product_limit <= MAX_RESULTS, "product_limit is invalid")
    contract = load_micro_region_contract(root, region_id, expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date, expected_accepted_sha256=expected_accepted_sha256, expected_national_index_sha256=expected_national_index_sha256)
    all_candidates: list[Mapping[str, Any]] = []
    seen: set[str] = set()
    partition_ids: set[str] = set()
    for query in product_queries:
        candidates, _ = _search_candidates(contract, query, product_limit=product_limit, max_candidates=max_candidates)
        for candidate in candidates:
            key = candidate["productEvidenceKey"]
            if key not in seen:
                seen.add(key)
                all_candidates.append(candidate)
                partition_ids.add(candidate["partitionId"])
    selected = tuple(sorted(partition_ids))
    slices: list[dict[str, Any]] = []
    for partition_id in selected:
        descriptor = contract.partition_descriptors[partition_id]
        pack = contract.pack_descriptors[descriptor["packId"]]
        slices.append({"partitionId": partition_id, "packId": descriptor["packId"], "path": descriptor["path"], "byteOffset": descriptor["byteOffset"], "byteLength": descriptor["byteLength"], "bytes": descriptor["bytes"], "sha256": descriptor["sha256"], "uncompressedBytes": descriptor["uncompressedBytes"], "uncompressedSha256": descriptor["uncompressedSha256"], "recordCount": descriptor["recordCount"], "packBytes": pack["bytes"]})
    packs = tuple(sorted({item["packId"] for item in slices}))
    return MicroQueryPlan(region_id, tuple(product_queries), tuple(all_candidates), selected, tuple(slices), contract.bootstrap_bytes, contract.manifest_bytes, contract.search_descriptor["bytes"], contract.store_descriptor["bytes"], sum(item["byteLength"] for item in slices), sum(item["uncompressedBytes"] for item in slices), packs, 4 + len(packs))


def query_micro_nearby(root: Path, region_id: str, *, latitude: str | float, longitude: str | float, radius_km: str | float, product_query: str, product_limit: int = MAX_RESULTS, max_candidates: int = MAX_CANDIDATES, max_offers: int = MAX_OFFERS, expected_outer_sha256: str = EXPECTED_OUTER_SHA256, expected_outer_bytes: int = EXPECTED_OUTER_BYTES, expected_release_date: str = RELEASE_DATE, expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256, expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256) -> dict[str, Any]:
    lat, lon, radius = _validate_location(latitude, longitude, radius_km)
    _require(isinstance(max_offers, int) and 1 <= max_offers <= MAX_OFFERS, "max_offers is invalid")
    plan = plan_micro_query(root, region_id, (product_query,), product_limit=product_limit, max_candidates=max_candidates, expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date, expected_accepted_sha256=expected_accepted_sha256, expected_national_index_sha256=expected_national_index_sha256)
    contract = load_micro_region_contract(root, region_id, expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date, expected_accepted_sha256=expected_accepted_sha256, expected_national_index_sha256=expected_national_index_sha256)
    products = {item["productEvidenceKey"]: item for item in plan.product_candidates}
    stores = _load_stores(contract)
    offers = _load_selected_micro_offers(contract, products, stores, plan.partition_ids, lat, lon, radius, max_offers=max_offers)
    return {"regionId": region_id, "latitude": f"{lat:.6f}", "longitude": f"{lon:.6f}", "radiusKm": f"{radius:.6f}", "distanceSemantics": "STRAIGHT_LINE_HAVERSINE", "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "productQuery": product_query, "productCandidates": [dict(item) for item in plan.product_candidates], "offers": offers, "queryPlan": plan.as_dict()}


def query_micro_structured_request(root: Path, region_id: str, *, latitude: str | float, longitude: str | float, radius_km: str | float, items: Sequence[Mapping[str, Any]], product_limit: int = MAX_RESULTS, max_candidates: int = MAX_CANDIDATES, max_offers: int = MAX_OFFERS, expected_outer_sha256: str = EXPECTED_OUTER_SHA256, expected_outer_bytes: int = EXPECTED_OUTER_BYTES, expected_release_date: str = RELEASE_DATE, expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256, expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256) -> dict[str, Any]:
    from decimal import Decimal, InvalidOperation

    lat, lon, radius = _validate_location(latitude, longitude, radius_km)
    _require(isinstance(items, Sequence) and not isinstance(items, (str, bytes)) and 1 <= len(items) <= MAX_STRUCTURED_ITEMS, "items must contain 1-10 entries")
    normalized: list[dict[str, Any]] = []
    for index, item in enumerate(items, start=1):
        _require(isinstance(item, Mapping), f"item {index} is not an object")
        query = item.get("query")
        amount = item.get("amount")
        unit = item.get("unit")
        _require(isinstance(query, str) and query.strip() and isinstance(unit, str) and unit.strip() and len(unit.strip()) <= 32, f"item {index} is invalid")
        try:
            value = Decimal(str(amount))
        except (InvalidOperation, TypeError, ValueError) as exc:
            raise MicroPartitionQueryError(f"item {index} amount is invalid") from exc
        _require(value.is_finite() and value > 0, f"item {index} amount must be positive")
        normalized.append({"query": query, "amount": format(value, "f"), "unit": unit.strip()})
    plan = plan_micro_query(root, region_id, tuple(item["query"] for item in normalized), product_limit=product_limit, max_candidates=max_candidates, expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date, expected_accepted_sha256=expected_accepted_sha256, expected_national_index_sha256=expected_national_index_sha256)
    contract = load_micro_region_contract(root, region_id, expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date, expected_accepted_sha256=expected_accepted_sha256, expected_national_index_sha256=expected_national_index_sha256)
    products = {item["productEvidenceKey"]: item for item in plan.product_candidates}
    stores = _load_stores(contract)
    all_offers = _load_selected_micro_offers(contract, products, stores, plan.partition_ids, lat, lon, radius, max_offers=max_offers)
    per_item: list[dict[str, Any]] = []
    for item in normalized:
        candidates, _ = _search_candidates(contract, item["query"], product_limit=product_limit, max_candidates=max_candidates)
        keys = {candidate["productEvidenceKey"] for candidate in candidates}
        per_item.append({**item, "productCandidates": [dict(candidate) for candidate in candidates], "offers": [offer for offer in all_offers if offer["productEvidenceKey"] in keys]})
    return {"regionId": region_id, "latitude": f"{lat:.6f}", "longitude": f"{lon:.6f}", "radiusKm": f"{radius:.6f}", "distanceSemantics": "STRAIGHT_LINE_HAVERSINE", "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "items": per_item, "queryPlan": plan.as_dict()}


__all__ = ["MicroPartitionQueryError", "MicroQueryPlan", "MicroRegionContract", "MicroRegionRouting", "activate_cached_member", "load_micro_region_contract", "load_micro_region_routing", "plan_micro_query", "query_micro_nearby", "query_micro_structured_request", "required_slices"]

#!/usr/bin/env python3
"""Build a deterministic, range-addressable Argentina SEPA delivery contract.

This is an offline provider-edge transformation of the already verified
query-selective release.  Search and store evidence remain provider-neutral;
only the offer stream is repartitioned.  Each logical partition is an
independently compressed gzip member and several members are concatenated into
bounded physical pack files.  A consumer can therefore fetch and verify one
exact byte range without reading neighbouring members.
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
from pathlib import Path
from typing import Any, Mapping

try:
    from tools.argentina_sepa_query import _iter_gzip_records
    from tools.build_argentina_sepa_national_shards import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        NationalShardError,
        _canonical_json,
        _sha256_file,
    )
    from tools.build_argentina_sepa_query_selective_mobile import (
        DEFAULT_BUCKET_COUNT as DEFAULT_SOURCE_BUCKET_COUNT,
        EXPECTED_ACCEPTED_SHA256,
        EXPECTED_NATIONAL_INDEX_SHA256,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        MAX_BUCKET_COUNT as MAX_SOURCE_BUCKET_COUNT,
        PARTITION_ALGORITHM,
        QUERY_SELECTIVE_SCHEMA_VERSION,
        RELEASE_DATE,
        SEARCH_INDEX_FILE,
        STORE_INDEX_FILE,
    )
    from tools.verify_argentina_sepa_query_selective_mobile import verify_query_selective_mobile
except ModuleNotFoundError:  # direct ``python tools/build_...py`` invocation
    from argentina_sepa_query import _iter_gzip_records
    from build_argentina_sepa_national_shards import ARGENTINA_REGIONS, AVAILABILITY, CURRENCY, DELIVERY_PICKUP, NationalShardError, _canonical_json, _sha256_file
    from build_argentina_sepa_query_selective_mobile import DEFAULT_BUCKET_COUNT as DEFAULT_SOURCE_BUCKET_COUNT, EXPECTED_ACCEPTED_SHA256, EXPECTED_NATIONAL_INDEX_SHA256, EXPECTED_OUTER_BYTES, EXPECTED_OUTER_SHA256, MAX_BUCKET_COUNT as MAX_SOURCE_BUCKET_COUNT, PARTITION_ALGORITHM, QUERY_SELECTIVE_SCHEMA_VERSION, RELEASE_DATE, SEARCH_INDEX_FILE, STORE_INDEX_FILE
    from verify_argentina_sepa_query_selective_mobile import verify_query_selective_mobile


MICRO_PARTITION_SCHEMA_VERSION = "argentina-sepa-micro-partition-mobile-v1"
MICRO_PARTITION_POLICY_VERSION = "argentina-sepa-micro-partition-policy-v1"
MICRO_PARTITION_COMPATIBILITY_VERSION = "argentina-sepa-micro-partition-contract-v1"
MICRO_PARTITION_ALGORITHM = PARTITION_ALGORITHM
PHYSICAL_PACK_ALGORITHM = "LOGICAL_PARTITION_ID_MOD_PACK_COUNT"
MEMBER_COMPRESSION = "INDEPENDENT_GZIP_MEMBER"
DEFAULT_LOGICAL_PARTITION_COUNT = 512
MIN_LOGICAL_PARTITION_COUNT = 128
MAX_LOGICAL_PARTITION_COUNT = 1024
DEFAULT_PHYSICAL_PACK_COUNT = 32
MIN_PHYSICAL_PACK_COUNT = 1
MAX_PHYSICAL_PACK_COUNT = 64
BOOTSTRAP_FILE = "bootstrap.json"
REGION_MANIFEST_FILE = "manifest.json"
PACKS_DIR = "packs"
PACK_FILE_TEMPLATE = "pack{pack:03d}.bin"
SOURCE_ARTIFACT_SCHEMA_VERSION = QUERY_SELECTIVE_SCHEMA_VERSION


class MicroPartitionBuildError(NationalShardError):
    """A micro-partition input or output failed closed."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise MicroPartitionBuildError(message)


def _stable_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _read_canonical_json(path: Path, label: str) -> dict[str, Any]:
    _require(path.is_file(), f"Missing {label}: {path}")
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise MicroPartitionBuildError(f"Invalid {label}: {path}") from exc
    _require(raw == _canonical_json(value), f"{label} is not canonical JSON")
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _canonical_generated_at(value: str) -> str:
    from datetime import datetime, timezone

    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise MicroPartitionBuildError("generated_at is not ISO-8601") from exc
    _require(parsed.tzinfo is not None, "generated_at needs an explicit timezone")
    return parsed.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def logical_partition_id(bucket: int, partition_count: int) -> str:
    _require(isinstance(partition_count, int) and MIN_LOGICAL_PARTITION_COUNT <= partition_count <= MAX_LOGICAL_PARTITION_COUNT, "logical partition count is invalid")
    _require(isinstance(bucket, int) and 0 <= bucket < partition_count, "logical partition bucket is invalid")
    width = max(3, len(str(partition_count - 1)))
    return f"p{bucket:0{width}d}"


def logical_partition_for_product(product_key: str, partition_count: int = DEFAULT_LOGICAL_PARTITION_COUNT) -> str:
    _require(isinstance(product_key, str) and product_key, "product key is required")
    digest = hashlib.sha256(product_key.encode("utf-8")).digest()
    bucket = int.from_bytes(digest[:8], "big") % partition_count
    return logical_partition_id(bucket, partition_count)


def physical_pack_id(partition_id: str, partition_count: int, pack_count: int) -> str:
    _require(isinstance(partition_id, str) and partition_id.startswith("p"), "partition id is invalid")
    _require(isinstance(pack_count, int) and MIN_PHYSICAL_PACK_COUNT <= pack_count <= MAX_PHYSICAL_PACK_COUNT, "physical pack count is invalid")
    bucket = int(partition_id[1:])
    _require(bucket < partition_count, "partition id is outside logical partition count")
    return f"pack{bucket % pack_count:03d}"


class _JsonlGzipWriter:
    """Canonical JSONL gzip writer with deterministic headers and counters."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._raw = path.open("wb")
        self._gzip = gzip.GzipFile(fileobj=self._raw, mode="wb", filename="", mtime=0, compresslevel=9)
        self._hash = hashlib.sha256()
        self.records = 0
        self.uncompressed_bytes = 0
        self._closed = False

    def write(self, value: Mapping[str, Any]) -> None:
        _require(not self._closed, f"writer is closed: {self.path}")
        data = _canonical_json(value)
        self._gzip.write(data)
        self._hash.update(data)
        self.records += 1
        self.uncompressed_bytes += len(data)

    def close(self) -> dict[str, Any]:
        if not self._closed:
            self._closed = True
            self._gzip.close()
            self._raw.close()
        return {
            "bytes": self.path.stat().st_size,
            "uncompressedBytes": self.uncompressed_bytes,
            "recordCount": self.records,
            "sha256": _sha256_file(self.path),
            "uncompressedSha256": self._hash.hexdigest(),
        }


def _copy_index_records(source_root: Path, source_descriptor: Mapping[str, Any], source_path: str, output_path: Path, *, partition_count: int, label: str) -> dict[str, Any]:
    writer = _JsonlGzipWriter(output_path)
    try:
        for record in _iter_gzip_records(source_root / source_path, source_descriptor, label):
            value = dict(record)
            if label.endswith("search index"):
                value["partitionId"] = logical_partition_for_product(value["productEvidenceKey"], partition_count)
            writer.write(value)
    finally:
        descriptor = writer.close()
    return {"compression": "gzip", **descriptor}


def _append_file(source: Path, target: Any, digest: Any) -> int:
    total = 0
    with source.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            target.write(block)
            digest.update(block)
            total += len(block)
    return total


def _write_region(source_root: Path, source_entry: Mapping[str, Any], output_region: Path, *, generated_at: str, logical_count: int, pack_count: int, source_bootstrap: Mapping[str, Any]) -> dict[str, Any]:
    region_id = source_entry["regionId"]
    source_manifest_path = source_root / "regions" / region_id / REGION_MANIFEST_FILE
    source_manifest = _read_canonical_json(source_manifest_path, f"{region_id} source manifest")
    source_files = source_manifest.get("files")
    _require(isinstance(source_files, dict), f"{region_id} source files are missing")
    output_region.mkdir(parents=True, exist_ok=True)
    search_path = output_region / SEARCH_INDEX_FILE
    store_path = output_region / STORE_INDEX_FILE
    search_descriptor = _copy_index_records(
        source_root,
        source_files["searchIndex"],
        f"regions/{region_id}/{SEARCH_INDEX_FILE}",
        search_path,
        partition_count=logical_count,
        label=f"{region_id} source search index",
    )
    search_descriptor.update({"path": f"regions/{region_id}/{SEARCH_INDEX_FILE}", "kind": "PRODUCT_SEARCH", "dependencies": []})
    # Store identity and geography are copied byte-for-byte in canonical form;
    # only its location in the candidate artifact changes.
    shutil.copyfile(source_root / f"regions/{region_id}/{STORE_INDEX_FILE}", store_path)
    source_store = dict(source_files["storeIndex"])
    _require(store_path.stat().st_size == source_store["bytes"] and _sha256_file(store_path) == source_store["sha256"], f"{region_id} store index changed while copying")
    store_descriptor = {"path": f"regions/{region_id}/{STORE_INDEX_FILE}", **source_store, "kind": "STORE_DIRECTORY", "dependencies": []}

    member_root = output_region / ".logical-members"
    writers = {
        logical_partition_id(bucket, logical_count): _JsonlGzipWriter(member_root / f"p{bucket:04d}.jsonl.gz")
        for bucket in range(logical_count)
    }
    try:
        source_partitions = source_files.get("offerPartitions")
        _require(isinstance(source_partitions, list), f"{region_id} source offer partitions are missing")
        for source_partition in source_partitions:
            _require(isinstance(source_partition, dict), f"{region_id} source partition descriptor is invalid")
            source_path = source_root / source_partition["path"]
            for offer in _iter_gzip_records(source_path, source_partition, f"{region_id} source offers"):
                partition_id = logical_partition_for_product(offer["productEvidenceKey"], logical_count)
                writers[partition_id].write(offer)
    finally:
        member_descriptors = {partition_id: writer.close() for partition_id, writer in writers.items()}

    packs_dir = output_region / PACKS_DIR
    packs_dir.mkdir(parents=True, exist_ok=True)
    pack_descriptors: list[dict[str, Any]] = []
    logical_descriptors: list[dict[str, Any]] = []
    for pack_number in range(pack_count):
        pack_id = f"pack{pack_number:03d}"
        pack_path = packs_dir / PACK_FILE_TEMPLATE.format(pack=pack_number)
        pack_hash = hashlib.sha256()
        offset = 0
        member_ids: list[str] = []
        with pack_path.open("wb") as target:
            for bucket in range(logical_count):
                partition_id = logical_partition_id(bucket, logical_count)
                if bucket % pack_count != pack_number:
                    continue
                member_path = member_root / f"p{bucket:04d}.jsonl.gz"
                descriptor = member_descriptors[partition_id]
                length = _append_file(member_path, target, pack_hash)
                _require(length == descriptor["bytes"], f"{region_id}/{partition_id} member byte count changed")
                logical_descriptors.append(
                    {
                        "partitionId": partition_id,
                        "packId": pack_id,
                        "path": f"regions/{region_id}/{PACKS_DIR}/{PACK_FILE_TEMPLATE.format(pack=pack_number)}",
                        "byteOffset": offset,
                        "byteLength": length,
                        "bytes": length,
                        "compression": "gzip",
                        "memberCompression": MEMBER_COMPRESSION,
                        "recordCount": descriptor["recordCount"],
                        "uncompressedBytes": descriptor["uncompressedBytes"],
                        "sha256": descriptor["sha256"],
                        "uncompressedSha256": descriptor["uncompressedSha256"],
                        "schemaVersion": MICRO_PARTITION_SCHEMA_VERSION,
                        "policyVersion": MICRO_PARTITION_POLICY_VERSION,
                        "compatibilityVersion": MICRO_PARTITION_COMPATIBILITY_VERSION,
                        "releaseDate": source_bootstrap["release"]["date"],
                        "contents": {"productBucket": bucket, "partitionAlgorithm": MICRO_PARTITION_ALGORITHM},
                        "dependencies": ["searchIndex", "storeIndex"],
                    }
                )
                offset += length
                member_ids.append(partition_id)
        pack_descriptors.append(
            {
                "packId": pack_id,
                "path": f"regions/{region_id}/{PACKS_DIR}/{PACK_FILE_TEMPLATE.format(pack=pack_number)}",
                "bytes": pack_path.stat().st_size,
                "sha256": _sha256_file(pack_path),
                "memberCount": len(member_ids),
                "logicalPartitionIds": member_ids,
                "memberCompression": MEMBER_COMPRESSION,
                "schemaVersion": MICRO_PARTITION_SCHEMA_VERSION,
                "policyVersion": MICRO_PARTITION_POLICY_VERSION,
                "compatibilityVersion": MICRO_PARTITION_COMPATIBILITY_VERSION,
            }
        )
    shutil.rmtree(member_root, ignore_errors=True)
    logical_descriptors.sort(key=lambda item: item["partitionId"])
    pack_descriptors.sort(key=lambda item: item["packId"])
    counts = dict(source_manifest["counts"])
    _require(counts["offers"] == sum(item["recordCount"] for item in logical_descriptors), f"{region_id} offer count changed")
    compressed_offer_bytes = sum(item["bytes"] for item in pack_descriptors)
    uncompressed_offer_bytes = sum(item["uncompressedBytes"] for item in logical_descriptors)
    manifest = {
        "artifactSchemaVersion": MICRO_PARTITION_SCHEMA_VERSION,
        "policyVersion": MICRO_PARTITION_POLICY_VERSION,
        "compatibilityVersion": MICRO_PARTITION_COMPATIBILITY_VERSION,
        "atomicCompletion": True,
        "completionState": "COMPLETE",
        "generatedAt": generated_at,
        "source": dict(source_bootstrap["source"]),
        "region": {"id": region_id, "displayName": source_entry["displayName"], "selector": source_entry["selector"]},
        "boundaries": {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "rawProviderDataCommitted": False},
        "partitioning": {
            "algorithm": MICRO_PARTITION_ALGORITHM,
            "key": "productEvidenceKey",
            "logicalPartitionCount": logical_count,
            "logicalPartitionIdWidth": max(3, len(str(logical_count - 1))),
            "physicalPackAlgorithm": PHYSICAL_PACK_ALGORITHM,
            "physicalPackCount": pack_count,
            "memberCompression": MEMBER_COMPRESSION,
            "rangeUnit": "BYTES",
            "crossRegionIdentity": "NOT_JOINED_BY_PARTITION",
        },
        "counts": counts,
        "storeSummary": dict(source_manifest.get("storeSummary", {})),
        "size": {
            "compressedBytes": int(search_descriptor["bytes"]) + int(store_descriptor["bytes"]) + compressed_offer_bytes,
            "uncompressedBytes": int(search_descriptor["uncompressedBytes"]) + int(store_descriptor["uncompressedBytes"]) + uncompressed_offer_bytes,
            "searchIndexBytes": int(search_descriptor["bytes"]),
            "storeIndexBytes": int(store_descriptor["bytes"]),
            "offerPackBytes": compressed_offer_bytes,
            "logicalOfferBytes": sum(item["byteLength"] for item in logical_descriptors),
            "fileCount": 2 + pack_count,
        },
        "files": {"searchIndex": search_descriptor, "storeIndex": store_descriptor, "offerPacks": pack_descriptors, "logicalPartitions": logical_descriptors},
        "dependencies": {"bootstrap": BOOTSTRAP_FILE, "offerPacks": ["searchIndex", "storeIndex"]},
    }
    return manifest


def build_micro_partition_mobile(
    source_root: Path,
    output_root: Path,
    *,
    generated_at: str,
    logical_partition_count: int = DEFAULT_LOGICAL_PARTITION_COUNT,
    physical_pack_count: int = DEFAULT_PHYSICAL_PACK_COUNT,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    expected_accepted_sha256: str | None = EXPECTED_ACCEPTED_SHA256,
    expected_national_index_sha256: str | None = EXPECTED_NATIONAL_INDEX_SHA256,
    verify_source: bool = True,
) -> dict[str, Any]:
    """Build a complete micro-partition root through an atomic rename."""

    generated_at = _canonical_generated_at(generated_at)
    _require(MIN_LOGICAL_PARTITION_COUNT <= logical_partition_count <= MAX_LOGICAL_PARTITION_COUNT and logical_partition_count & (logical_partition_count - 1) == 0, "logical partition count must be a power of two from 128 through 1024")
    _require(MIN_PHYSICAL_PACK_COUNT <= physical_pack_count <= MAX_PHYSICAL_PACK_COUNT and physical_pack_count <= logical_partition_count, "physical pack count is invalid")
    source_root = Path(source_root).resolve()
    output_root = Path(output_root).resolve()
    _require(source_root != output_root and not output_root.exists(), f"Refusing to overwrite micro-partition root: {output_root}")
    source_bootstrap = _read_canonical_json(source_root / BOOTSTRAP_FILE, "source bootstrap")
    source_partitioning = source_bootstrap.get("partitioning")
    _require(isinstance(source_partitioning, dict), "source partitioning is missing")
    source_bucket_count = source_partitioning.get("bucketCount")
    _require(isinstance(source_bucket_count, int) and 1 <= source_bucket_count <= MAX_SOURCE_BUCKET_COUNT, "source bucket count is invalid")
    if verify_source:
        verify_query_selective_mobile(
            source_root,
            expected_outer_sha256=expected_outer_sha256,
            expected_outer_bytes=expected_outer_bytes,
            expected_release_date=expected_release_date,
            expected_accepted_sha256=expected_accepted_sha256,
            expected_national_index_sha256=expected_national_index_sha256,
            expected_bucket_count=source_bucket_count,
        )
    else:
        _require(source_bootstrap.get("atomicCompletion") is True and source_bootstrap.get("completionState") == "COMPLETE", "source was not marked complete")
        source = source_bootstrap.get("source")
        _require(
            isinstance(source, dict)
            and source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS"
            and source.get("outerSha256") == expected_outer_sha256
            and source.get("outerBytes") == expected_outer_bytes
            and source.get("releaseDate") == expected_release_date
            and source.get("license") == "Creative Commons Attribution 4.0"
            and isinstance(source.get("attribution"), str)
            and bool(source["attribution"].strip())
            and source.get("rawProviderDataCommitted") is False,
            "source provenance does not match the verified input",
        )
        if expected_accepted_sha256 is not None:
            _require(source.get("acceptedObservationsSha256") == expected_accepted_sha256, "accepted source provenance does not match the verified input")
        if expected_national_index_sha256 is not None:
            _require(source.get("nationalIndexSha256") == expected_national_index_sha256, "national index provenance does not match the verified input")
    _require(source_bootstrap["artifactSchemaVersion"] == SOURCE_ARTIFACT_SCHEMA_VERSION, "source artifact schema is not the qualified query-selective contract")
    _require(source_bootstrap["release"]["date"] == expected_release_date, "source release date is unexpected")
    output_root.parent.mkdir(parents=True, exist_ok=True)
    partial = Path(tempfile.mkdtemp(prefix=f".{output_root.name}.partial-", dir=str(output_root.parent)))
    completed = False
    region_entries: list[dict[str, Any]] = []
    try:
        for source_entry in source_bootstrap["regions"]:
            region_id = source_entry["regionId"]
            manifest = _write_region(
                source_root,
                source_entry,
                partial / "regions" / region_id,
                generated_at=generated_at,
                logical_count=logical_partition_count,
                pack_count=physical_pack_count,
                source_bootstrap=source_bootstrap,
            )
            manifest_path = partial / "regions" / region_id / REGION_MANIFEST_FILE
            manifest_path.write_bytes(_canonical_json(manifest))
            manifest_hash = _sha256_file(manifest_path)
            (manifest_path.parent / "manifest.sha256").write_text(f"{manifest_hash}  {REGION_MANIFEST_FILE}\n", encoding="ascii")
            (manifest_path.parent / "integrity.json").write_bytes(_canonical_json({"manifestSha256": manifest_hash, "atomicCompletion": True, "fileNames": [REGION_MANIFEST_FILE, SEARCH_INDEX_FILE, STORE_INDEX_FILE, PACKS_DIR]}))
            region_entries.append(
                {
                    "regionId": region_id,
                    "displayName": source_entry["displayName"],
                    "provinceCode": source_entry["provinceCode"],
                    "selector": source_entry["selector"],
                    "manifest": {"path": f"regions/{region_id}/{REGION_MANIFEST_FILE}", "bytes": manifest_path.stat().st_size, "sha256": manifest_hash, "kind": "REGION_CONTRACT", "dependencies": [BOOTSTRAP_FILE]},
                    "counts": manifest["counts"],
                }
            )
        source_copy = dict(source_bootstrap["source"])
        bootstrap = {
            "artifactSchemaVersion": MICRO_PARTITION_SCHEMA_VERSION,
            "policyVersion": MICRO_PARTITION_POLICY_VERSION,
            "compatibilityVersion": MICRO_PARTITION_COMPATIBILITY_VERSION,
            "atomicCompletion": True,
            "completionState": "COMPLETE",
            "generatedAt": generated_at,
            "release": dict(source_bootstrap["release"]),
            "source": source_copy,
            "productionUiAuthorized": False,
            "boundaries": {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "rawProviderDataCommitted": False},
            "distribution": {"granularity": "REGION_MANIFEST_PLUS_RANGED_MICRO_MEMBERS", "bootstrap": BOOTSTRAP_FILE, "activation": "VERIFY_MEMBER_THEN_ATOMIC_LAST_KNOWN_GOOD", "networking": "NOT_IMPLEMENTED_STATIC_FILES_ONLY", "rangeDelivery": "OFFLINE_CONTRACT_ONLY"},
            "partitioning": {"algorithm": MICRO_PARTITION_ALGORITHM, "key": "productEvidenceKey", "logicalPartitionCount": logical_partition_count, "physicalPackAlgorithm": PHYSICAL_PACK_ALGORITHM, "physicalPackCount": physical_pack_count, "memberCompression": MEMBER_COMPRESSION, "rangeUnit": "BYTES"},
            "regions": region_entries,
            "totals": {"publishableRegions": len(region_entries), "stores": sum(int(item["counts"]["stores"]) for item in region_entries), "productEvidenceRecords": sum(int(item["counts"]["productEvidenceRecords"]) for item in region_entries), "offers": sum(int(item["counts"]["offers"]) for item in region_entries), "promotions": sum(int(item["counts"]["promotions"]) for item in region_entries)},
            "sourceArtifact": {"schemaVersion": SOURCE_ARTIFACT_SCHEMA_VERSION, "bootstrapSha256": _sha256_file(source_root / BOOTSTRAP_FILE), "bucketCount": source_bucket_count},
        }
        bootstrap_path = partial / BOOTSTRAP_FILE
        bootstrap_path.write_bytes(_canonical_json(bootstrap))
        bootstrap_hash = _sha256_file(bootstrap_path)
        (partial / "bootstrap.sha256").write_text(f"{bootstrap_hash}  {BOOTSTRAP_FILE}\n", encoding="ascii")
        (partial / "integrity.json").write_bytes(_canonical_json({"bootstrapSha256": bootstrap_hash, "atomicCompletion": True, "schemaVersion": MICRO_PARTITION_SCHEMA_VERSION, "regionIds": [item["regionId"] for item in region_entries]}))
        (partial / "README.txt").write_text("Offline Argentina SEPA micro-partition contract. Verify bootstrap, manifests, pack ranges, and member hashes before activation.\n", encoding="utf-8")
        os.replace(partial, output_root)
        completed = True
        return bootstrap
    finally:
        if not completed:
            shutil.rmtree(partial, ignore_errors=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--logical-partition-count", type=int, default=DEFAULT_LOGICAL_PARTITION_COUNT)
    parser.add_argument("--physical-pack-count", type=int, default=DEFAULT_PHYSICAL_PACK_COUNT)
    parser.add_argument("--expected-outer-sha256", required=True)
    parser.add_argument("--expected-outer-bytes", required=True, type=int)
    parser.add_argument("--expected-release-date", default=RELEASE_DATE)
    parser.add_argument("--expected-accepted-sha256")
    parser.add_argument("--expected-national-index-sha256")
    args = parser.parse_args(argv)
    try:
        bootstrap = build_micro_partition_mobile(
            args.source_root,
            args.output_root,
            generated_at=args.generated_at,
            logical_partition_count=args.logical_partition_count,
            physical_pack_count=args.physical_pack_count,
            expected_outer_sha256=args.expected_outer_sha256,
            expected_outer_bytes=args.expected_outer_bytes,
            expected_release_date=args.expected_release_date,
            expected_accepted_sha256=args.expected_accepted_sha256,
            expected_national_index_sha256=args.expected_national_index_sha256,
        )
    except (MicroPartitionBuildError, OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"micro-partition build failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps({"regions": len(bootstrap["regions"]), "logicalPartitionCount": args.logical_partition_count, "physicalPackCount": args.physical_pack_count, "path": str(args.output_root)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

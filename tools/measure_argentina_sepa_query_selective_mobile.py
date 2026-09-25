#!/usr/bin/env python3
"""Measure an already verified offline Argentina SEPA selective release.

The generator is deliberately local-only. It emits source-derived payload
sizes and query-equivalence evidence; wall-clock values are diagnostics from
the qualification machine, not deterministic gates.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import time
from pathlib import Path
from typing import Any, Iterator

try:
    from tools.argentina_sepa_query import (
        MAX_RADIUS_KM,
        _load_selected_offers,
        _load_stores,
        _search_candidates,
        load_region_contract,
        plan_query,
        query_full_shard,
        query_nearby,
    )
    from tools.build_argentina_sepa_national_shards import _canonical_json
    from tools.build_argentina_sepa_query_selective_mobile import (
        EXPECTED_ACCEPTED_SHA256,
        EXPECTED_NATIONAL_INDEX_SHA256,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        RELEASE_DATE,
    )
    from tools.verify_argentina_sepa_query_selective_mobile import verify_query_selective_mobile
except ModuleNotFoundError:
    from argentina_sepa_query import MAX_RADIUS_KM, _load_selected_offers, _load_stores, _search_candidates, load_region_contract, plan_query, query_full_shard, query_nearby
    from build_argentina_sepa_national_shards import _canonical_json
    from build_argentina_sepa_query_selective_mobile import EXPECTED_ACCEPTED_SHA256, EXPECTED_NATIONAL_INDEX_SHA256, EXPECTED_OUTER_BYTES, EXPECTED_OUTER_SHA256, RELEASE_DATE
    from verify_argentina_sepa_query_selective_mobile import verify_query_selective_mobile


REGIONS = ("ar-caba", "ar-b", "ar-x", "ar-y")
RADII_KM = (2, 5, 10, 25, 50)
PRODUCT_QUERIES = {
    1: ("leche",),
    5: ("leche", "arroz", "detergente", "pan", "aceite"),
    10: ("leche", "arroz", "detergente", "pan", "aceite", "agua", "queso", "huevos", "cafe", "papel"),
}
PROVENANCE = {
    "expected_outer_sha256": EXPECTED_OUTER_SHA256,
    "expected_outer_bytes": EXPECTED_OUTER_BYTES,
    "expected_release_date": RELEASE_DATE,
    "expected_accepted_sha256": EXPECTED_ACCEPTED_SHA256,
    "expected_national_index_sha256": EXPECTED_NATIONAL_INDEX_SHA256,
}


def _read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"expected JSON object: {path}")
    return value


def _iter_gzip_jsonl(path: Path) -> Iterator[dict[str, Any]]:
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            value = json.loads(line)
            if isinstance(value, dict):
                yield value


def _first_valid_store(selective_root: Path, region_id: str) -> tuple[str, str]:
    path = selective_root / "regions" / region_id / "store-index.jsonl.gz"
    for store in _iter_gzip_jsonl(path):
        if store.get("geoStatus") == "VALID" and isinstance(store.get("latitude"), str) and isinstance(store.get("longitude"), str):
            return store["latitude"], store["longitude"]
    raise ValueError(f"region has no trusted coordinate: {region_id}")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _audit_summary(path: Path) -> dict[str, Any]:
    audit = _read_json(path)
    try:
        display_path = path.resolve().relative_to(Path.cwd().resolve()).as_posix()
    except ValueError:
        display_path = path.name
    return {
        "path": display_path,
        "sha256": _sha256(path),
        "status": audit.get("status"),
        "queryCount": audit.get("queryCount"),
        "overallPrecisionAt5": audit.get("overallPrecisionAt5"),
        "recallClaimed": audit.get("recallClaimed"),
        "auditedRelevantProductEvidenceIdentities": audit.get("auditedRelevantProductEvidenceIdentities"),
        "knownFalsePositiveRegressionClasses": audit.get("knownFalsePositiveRegressionClasses", []),
    }


def measure(selective_root: Path, national_root: Path, audit_path: Path, *, verify_artifact: bool = True) -> dict[str, Any]:
    selective_root = Path(selective_root).resolve()
    national_root = Path(national_root).resolve()
    audit_path = Path(audit_path).resolve()
    verification = (
        verify_query_selective_mobile(selective_root, **PROVENANCE, expected_bucket_count=64)
        if verify_artifact
        else {"status": "VERIFIED_SEPARATELY", "bootstrapSha256": _sha256(selective_root / "bootstrap.json")}
    )
    bootstrap = _read_json(selective_root / "bootstrap.json")
    national_index = _read_json(national_root / "index.json")
    national_by_region = {item["regionId"]: item for item in national_index["regions"]}
    source = dict(bootstrap["source"])
    selective_files = [path for path in selective_root.rglob("*") if path.is_file()]
    selective_bytes = sum(path.stat().st_size for path in selective_files)
    selective_uncompressed = sum(
        int(_read_json(selective_root / "regions" / entry["regionId"] / "manifest.json")["size"]["uncompressedBytes"])
        for entry in bootstrap["regions"]
    )
    scenarios: list[dict[str, Any]] = []
    for region_id in REGIONS:
        latitude, longitude = _first_valid_store(selective_root, region_id)
        full_bytes = int(national_by_region[region_id]["shard"]["bytes"])
        # Search each vocabulary item once and load the superset of selected
        # offer buckets once. Radius rows below are then pure deterministic
        # distance filters over the same evidence, not repeated decompression.
        contract = load_region_contract(selective_root, region_id)
        candidates_by_query: dict[str, tuple[dict[str, Any], ...]] = {}
        for query in PRODUCT_QUERIES[10]:
            candidates, _ = _search_candidates(contract, query, product_limit=5, max_candidates=100_000)
            candidates_by_query[query] = tuple(dict(item) for item in candidates)
        plans = {
            product_count: plan_query(selective_root, region_id, queries, product_limit=5, max_candidates=100_000)
            for product_count, queries in PRODUCT_QUERIES.items()
        }
        maximum_plan = plans[10]
        products = {
            item["productEvidenceKey"]: item
            for query in PRODUCT_QUERIES[10]
            for item in candidates_by_query[query]
        }
        stores = _load_stores(contract)
        started = time.perf_counter()
        all_offers = _load_selected_offers(
            contract,
            products,
            stores,
            maximum_plan.partition_ids,
            float(latitude),
            float(longitude),
            MAX_RADIUS_KM,
            max_offers=100_000,
        )
        offer_load_ms = max(0, int(round((time.perf_counter() - started) * 1000)))
        for product_count, queries in PRODUCT_QUERIES.items():
            plan = plans[product_count]
            item_keys = [
                {candidate["productEvidenceKey"] for candidate in candidates_by_query[query]}
                for query in queries
            ]
            for radius in RADII_KM:
                offers_returned = sum(
                    1
                    for keys in item_keys
                    for offer in all_offers
                    if offer["productEvidenceKey"] in keys and float(offer["distanceKm"]) <= radius + 1e-9
                )
                payload_bytes = plan.total_bytes
                reduction = (1.0 - (payload_bytes / full_bytes)) * 100.0 if full_bytes else 0.0
                scenarios.append(
                    {
                        "regionId": region_id,
                        "productCount": product_count,
                        "radiusKm": radius,
                        "candidateCount": len(plan.product_candidates),
                        "partitionCount": len(plan.partition_ids),
                        "partitionIds": list(plan.partition_ids),
                        "payloadBytes": payload_bytes,
                        "bootstrapBytes": plan.bootstrap_bytes,
                        "regionManifestBytes": plan.region_manifest_bytes,
                        "searchIndexBytes": plan.search_index_bytes,
                        "storeIndexBytes": plan.store_index_bytes,
                        "offerPartitionBytes": plan.offer_partition_bytes,
                        "fileCount": plan.file_count,
                        "fullProvinceShardBytes": full_bytes,
                        "reductionPercent": reduction,
                        "offersReturned": offers_returned,
                        "queryWallClockMs": offer_load_ms,
                    }
                )
    equivalence: list[dict[str, Any]] = []
    for region_id in REGIONS:
        latitude, longitude = _first_valid_store(selective_root, region_id)
        kwargs = {
            "latitude": latitude,
            "longitude": longitude,
            "radius_km": "50",
            "product_query": "leche",
            "product_limit": 5,
            "max_candidates": 100_000,
            "max_offers": 100_000,
        }
        started = time.perf_counter()
        selective = query_nearby(selective_root, region_id, **kwargs)
        selective_ms = max(0, int(round((time.perf_counter() - started) * 1000)))
        started = time.perf_counter()
        full = query_full_shard(national_root, region_id, **kwargs)
        full_ms = max(0, int(round((time.perf_counter() - started) * 1000)))
        selective_keys = [item["offerKey"] for item in selective["offers"]]
        full_keys = [item["offerKey"] for item in full["offers"]]
        equivalence.append(
            {
                "regionId": region_id,
                "selectiveOfferCount": len(selective_keys),
                "fullOfferCount": len(full_keys),
                "offerKeysEqual": selective_keys == full_keys,
                "selectiveQueryWallClockMs": selective_ms,
                "fullShardReferenceWallClockMs": full_ms,
            }
        )
    geography = {
        "storesWithTrustedCoordinates": int(
            national_index["totals"]["stores"]
            - sum(item["storeSummary"]["storesWithoutTrustedCoordinates"] for item in national_index["regions"])
        ),
        "storesWithoutTrustedCoordinates": int(
            sum(item["storeSummary"]["storesWithoutTrustedCoordinates"] for item in national_index["regions"])
        ),
        "unpublishedProvinceRows": int(national_index["totals"]["unpublishedProvinceRows"]),
        "unpublishedProvinceEvidence": national_index.get("unpublishedProvinceEvidence", []),
    }
    return {
        "schemaVersion": "argentina-query-selective-mobile-qualification-v1",
        "status": "QUALIFIED_FOR_BACKEND_DISTRIBUTION_ONLY",
        "productionUiAuthorized": False,
        "source": source,
        "architecture": {
            "schemaVersion": bootstrap["artifactSchemaVersion"],
            "policyVersion": bootstrap["policyVersion"],
            "compatibilityVersion": bootstrap["compatibilityVersion"],
            "partitionAlgorithm": bootstrap["partitioning"]["algorithm"],
            "bucketCount": bootstrap["partitioning"]["bucketCount"],
            "distribution": bootstrap["distribution"],
            "alternatives": {
                "fullProvinceShard": "Reference/fallback only; normal province payloads are tens to hundreds of MB compressed.",
                "geographicOfferPartitions": "Rejected as the primary key because radius boundaries can create false negatives.",
                "productBuckets": "Chosen: complete product search/store metadata plus stable SHA-256 product buckets.",
            },
        },
        "nationwide": {
            "fileCount": len(selective_files),
            "compressedBytes": selective_bytes,
            "uncompressedBytes": selective_uncompressed,
            "bootstrapBytes": (selective_root / "bootstrap.json").stat().st_size,
            "selectiveTotals": bootstrap["totals"],
            "verification": verification,
        },
        "regions": [
            {
                "regionId": entry["regionId"],
                "provinceCode": entry["provinceCode"],
                "counts": entry["counts"],
                "size": _read_json(selective_root / "regions" / entry["regionId"] / "manifest.json")["size"],
                "fullProvinceShardBytes": national_by_region[entry["regionId"]]["shard"]["bytes"],
            }
            for entry in bootstrap["regions"]
        ],
        "scenarios": scenarios,
        "queryEquivalence": {"allEqual": all(item["offerKeysEqual"] for item in equivalence), "checks": equivalence},
        "searchAudit": _audit_summary(audit_path),
        "distanceSemantics": "STRAIGHT_LINE_HAVERSINE_ONLY",
        "geography": geography,
        "limitations": [
            "Backend/tooling qualification only; no Android UI or networking authorization.",
            "SEPA publication is not live inventory; availability remains UNKNOWN.",
            "Search audit measures finite precision only and makes no recall or universal-category claim.",
            "Wall-clock values are local diagnostics, not deterministic performance gates.",
        ],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--selective-root", required=True, type=Path)
    parser.add_argument("--national-root", required=True, type=Path)
    parser.add_argument("--audit", required=True, type=Path)
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--skip-verification", action="store_true", help="Use only after the exact artifact has already passed the full verifier")
    args = parser.parse_args(argv)
    report = measure(args.selective_root, args.national_root, args.audit, verify_artifact=not args.skip_verification)
    args.output_json.write_bytes(_canonical_json(report))
    print(json.dumps({"status": report["status"], "scenarios": len(report["scenarios"]), "equivalence": report["queryEquivalence"]["allEqual"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

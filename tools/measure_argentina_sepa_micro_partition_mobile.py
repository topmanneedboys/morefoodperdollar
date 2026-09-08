#!/usr/bin/env python3
"""Benchmark and qualify the Argentina SEPA micro-partition alternatives.

All measurements are local diagnostics.  The script never performs HTTP and
never treats a timing or a price publication as proof of live inventory.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import tempfile
import time
from pathlib import Path
from typing import Any, Mapping

try:
    from tools.argentina_sepa_micro_partition import (
        _load_selected_micro_offers,
        load_micro_region_contract,
        plan_micro_query,
    )
    from tools.argentina_sepa_query import _load_selected_offers, _load_stores, _search_candidates, load_region_contract, plan_query, query_full_shard
    from tools.build_argentina_sepa_micro_partition_mobile import DEFAULT_PHYSICAL_PACK_COUNT, build_micro_partition_mobile
    from tools.build_argentina_sepa_national_shards import _canonical_json
    from tools.build_argentina_sepa_query_selective_mobile import EXPECTED_ACCEPTED_SHA256, EXPECTED_NATIONAL_INDEX_SHA256, EXPECTED_OUTER_BYTES, EXPECTED_OUTER_SHA256, RELEASE_DATE
    from tools.verify_argentina_sepa_micro_partition_mobile import verify_micro_partition_mobile
    from tools.verify_argentina_sepa_query_selective_mobile import verify_query_selective_mobile
except ModuleNotFoundError:
    from argentina_sepa_micro_partition import _load_selected_micro_offers, load_micro_region_contract, plan_micro_query
    from argentina_sepa_query import _load_selected_offers, _load_stores, _search_candidates, load_region_contract, plan_query, query_full_shard
    from build_argentina_sepa_micro_partition_mobile import DEFAULT_PHYSICAL_PACK_COUNT, build_micro_partition_mobile
    from build_argentina_sepa_national_shards import _canonical_json
    from build_argentina_sepa_query_selective_mobile import EXPECTED_ACCEPTED_SHA256, EXPECTED_NATIONAL_INDEX_SHA256, EXPECTED_OUTER_BYTES, EXPECTED_OUTER_SHA256, RELEASE_DATE
    from verify_argentina_sepa_micro_partition_mobile import verify_micro_partition_mobile
    from verify_argentina_sepa_query_selective_mobile import verify_query_selective_mobile


REGIONS = ("ar-caba", "ar-b", "ar-x", "ar-y")
RADII_KM = (2, 5, 10, 25, 50)
PRODUCT_QUERIES = {
    1: ("leche",),
    5: ("leche", "arroz", "detergente", "pan", "aceite"),
    10: ("leche", "arroz", "detergente", "pan", "aceite", "agua", "queso", "huevos", "cafe", "papel"),
}
LOGICAL_COUNTS = (128, 256, 512, 1024)
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


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _first_valid_store(contract: Any) -> tuple[str, str]:
    stores = _load_stores(contract)
    for store in stores.values():
        if store.get("geoStatus") == "VALID" and isinstance(store.get("latitude"), str) and isinstance(store.get("longitude"), str):
            return store["latitude"], store["longitude"]
    raise ValueError(f"region has no trusted coordinate: {contract.region.region_id}")


def _baseline_plans(selective_root: Path, region_id: str) -> dict[int, Any]:
    return {
        count: plan_query(
            selective_root,
            region_id,
            queries,
            product_limit=5,
            max_candidates=100_000,
            **PROVENANCE,
        )
        for count, queries in PRODUCT_QUERIES.items()
    }


def _micro_scenarios(root: Path, selective_root: Path, national_index: Mapping[str, Any], region_id: str) -> list[dict[str, Any]]:
    contract = load_micro_region_contract(root, region_id, **PROVENANCE)
    baseline_contract = load_region_contract(selective_root, region_id, **PROVENANCE)
    latitude, longitude = _first_valid_store(contract)
    baseline_plans = _baseline_plans(selective_root, region_id)
    plans = {
        count: plan_micro_query(root, region_id, queries, product_limit=5, max_candidates=100_000, **PROVENANCE)
        for count, queries in PRODUCT_QUERIES.items()
    }
    stores = _load_stores(contract)
    scenarios: list[dict[str, Any]] = []
    full_entry = next(item for item in national_index["regions"] if item["regionId"] == region_id)
    full_bytes = int(full_entry["shard"]["bytes"])
    for product_count, queries in PRODUCT_QUERIES.items():
        plan = plans[product_count]
        baseline = baseline_plans[product_count]
        item_keys = []
        for query in queries:
            candidates, _ = _search_candidates(baseline_contract, query, product_limit=5, max_candidates=100_000)
            item_keys.append({candidate["productEvidenceKey"] for candidate in candidates})
        products = {item["productEvidenceKey"]: item for item in plan.product_candidates}
        started = time.perf_counter()
        selected_offers = _load_selected_micro_offers(
            contract,
            products,
            stores,
            plan.partition_ids,
            float(latitude),
            float(longitude),
            500.0,
            max_offers=100_000,
        )
        query_ms = max(0, int(round((time.perf_counter() - started) * 1000)))
        for radius in RADII_KM:
            offers_returned = sum(
                1
                for keys in item_keys
                for offer in selected_offers
                if offer["productEvidenceKey"] in keys and float(offer["distanceKm"]) <= radius + 1e-9
            )
            pack_bytes = sum(int(item["packBytes"]) for item in plan.slices)
            payload = plan.total_bytes
            baseline_payload = baseline.total_bytes
            scenarios.append(
                {
                    "regionId": region_id,
                    "productCount": product_count,
                    "radiusKm": radius,
                    "candidateCount": len(plan.product_candidates),
                    "logicalPartitionCount": int(contract.bootstrap["partitioning"]["logicalPartitionCount"]),
                    "logicalPartitionIds": list(plan.partition_ids),
                    "physicalPacksTouched": list(plan.physical_pack_ids),
                    "rangeSlices": [dict(item) for item in plan.slices],
                    "compressedBytes": plan.compressed_bytes,
                    "decompressedBytes": plan.decompressed_bytes,
                    "physicalPackBytes": pack_bytes,
                    "packBytesNotFetched": pack_bytes - plan.compressed_bytes,
                    "bytesAvoidedByExactRanges": pack_bytes - plan.compressed_bytes,
                    "duplicateBytesAvoided": 0,
                    "payloadBytes": payload,
                    "baselinePayloadBytes": baseline_payload,
                    "reductionVsCurrentPercent": (1.0 - payload / baseline_payload) * 100.0 if baseline_payload else 0.0,
                    "reductionVsFullProvincePercent": (1.0 - payload / full_bytes) * 100.0 if full_bytes else 0.0,
                    "fileCount": plan.file_count,
                    "physicalPackCount": len(contract.pack_descriptors),
                    "offersReturned": offers_returned,
                    "queryWallClockMs": query_ms,
                }
            )
    return scenarios


def _summarize_alternative(root: Path, selective_root: Path, national_index: Mapping[str, Any], logical_count: int) -> dict[str, Any]:
    bootstrap = _read_json(root / "bootstrap.json")
    regions: list[dict[str, Any]] = []
    scenarios: list[dict[str, Any]] = []
    for entry in bootstrap["regions"]:
        region_id = entry["regionId"]
        manifest = _read_json(root / "regions" / region_id / "manifest.json")
        size = manifest["size"]
        regions.append({"regionId": region_id, "size": size, "logicalPartitionCount": logical_count, "physicalPackCount": manifest["partitioning"]["physicalPackCount"], "standaloneFileCount": 2 + logical_count, "packedFileCount": size["fileCount"], "standaloneCompressedBytes": size["compressedBytes"], "packedCompressedBytes": size["compressedBytes"], "packingOverheadBytes": size["compressedBytes"] - size["logicalOfferBytes"] - size["searchIndexBytes"] - size["storeIndexBytes"]})
    scenarios.extend(_micro_scenarios(root, selective_root, national_index, "ar-caba"))
    scenarios.extend(_micro_scenarios(root, selective_root, national_index, "ar-b"))
    scenarios.extend(_micro_scenarios(root, selective_root, national_index, "ar-x"))
    scenarios.extend(_micro_scenarios(root, selective_root, national_index, "ar-y"))
    return {"logicalPartitionCount": logical_count, "physicalPackCount": bootstrap["partitioning"]["physicalPackCount"], "regions": regions, "scenarios": scenarios, "nationwide": {"compressedBytes": sum(int(item["size"]["compressedBytes"]) for item in regions), "uncompressedBytes": sum(int(item["size"]["uncompressedBytes"]) for item in regions), "fileCount": sum(int(item["size"]["fileCount"]) for item in regions) + 3, "bootstrapBytes": (root / "bootstrap.json").stat().st_size}}


def _select_winner(alternatives: Mapping[str, Mapping[str, Any]]) -> tuple[int | None, dict[str, Any]]:
    checks: list[dict[str, Any]] = []
    winner: int | None = None
    for logical_count in LOGICAL_COUNTS:
        alternative = alternatives[str(logical_count)]
        scenarios = alternative["scenarios"]
        by_key = {(item["regionId"], item["productCount"], item["radiusKm"]): item for item in scenarios}
        caba10 = by_key[("ar-caba", 10, 50)]
        ba10 = by_key[("ar-b", 10, 50)]
        caba5 = by_key[("ar-caba", 5, 50)]
        ba5 = by_key[("ar-b", 5, 50)]
        caba1 = by_key[("ar-caba", 1, 50)]
        ba1 = by_key[("ar-b", 1, 50)]
        gate = {
            "logicalPartitionCount": logical_count,
            "caba10ReductionVsCurrentPercent": caba10["reductionVsCurrentPercent"],
            "buenosAires10ReductionVsCurrentPercent": ba10["reductionVsCurrentPercent"],
            "caba5ReductionVsCurrentPercent": caba5["reductionVsCurrentPercent"],
            "buenosAires5ReductionVsCurrentPercent": ba5["reductionVsCurrentPercent"],
            "singleProductNoMaterialRegression": caba1["reductionVsCurrentPercent"] >= -2.0 and ba1["reductionVsCurrentPercent"] >= -2.0,
        }
        gate["passes"] = all(value >= 40.0 for value in (caba10["reductionVsCurrentPercent"], ba10["reductionVsCurrentPercent"])) and all(value >= 25.0 for value in (caba5["reductionVsCurrentPercent"], ba5["reductionVsCurrentPercent"])) and gate["singleProductNoMaterialRegression"]
        checks.append(gate)
        if winner is None and gate["passes"]:
            winner = logical_count
    status = "GO" if winner is not None else "NO_GO"
    return winner, {"status": status, "checks": checks, "winnerLogicalPartitionCount": winner, "selectionRule": "smallest power-of-two logical count passing >=40% dense 10-product, >=25% dense 5-product, and <=2% single-product regression gates"}


def measure(selective_root: Path, national_root: Path, *, work_root: Path, generated_at: str, physical_pack_count: int = DEFAULT_PHYSICAL_PACK_COUNT, selected_root: Path | None = None, verify_source: bool = True) -> dict[str, Any]:
    selective_root = Path(selective_root).resolve()
    national_root = Path(national_root).resolve()
    work_root = Path(work_root).resolve()
    work_root.mkdir(parents=True, exist_ok=True)
    source_verification = verify_query_selective_mobile(selective_root, **PROVENANCE, expected_bucket_count=None) if verify_source else {"status": "VERIFIED_SEPARATELY", "bootstrapSha256": _sha256(selective_root / "bootstrap.json")}
    national_index = _read_json(national_root / "index.json")
    alternatives: dict[str, dict[str, Any]] = {}
    temporary_roots: list[Path] = []
    try:
        for logical_count in LOGICAL_COUNTS:
            root = work_root / f"micro-{logical_count}"
            use_existing = False
            if selected_root is not None and Path(selected_root).resolve() != root and Path(selected_root).is_dir():
                candidate_bootstrap = _read_json(Path(selected_root).resolve() / "bootstrap.json")
                use_existing = candidate_bootstrap.get("partitioning", {}).get("logicalPartitionCount") == logical_count
                if use_existing:
                    root = Path(selected_root).resolve()
            if not use_existing:
                if root.exists():
                    # A previous interrupted benchmark may have finished this
                    # root atomically before failing in a later reporting
                    # step.  Reuse it only after the normal verifier below;
                    # never treat a partial directory as complete.
                    if not (root / "bootstrap.json").is_file() or not (root / "integrity.json").is_file():
                        raise ValueError(f"refusing to overwrite incomplete alternative root: {root}")
                    use_existing = True
            if not use_existing:
                build_micro_partition_mobile(
                    selective_root,
                    root,
                    generated_at=generated_at,
                    logical_partition_count=logical_count,
                    physical_pack_count=min(physical_pack_count, logical_count),
                    # The complete qualified source was verified once above.
                    # The alternatives consume that immutable verified root;
                    # re-scanning the 10+ GB source for every count adds no
                    # assurance and only makes the benchmark needlessly slow.
                    verify_source=False,
                    **PROVENANCE,
                )
            temporary_roots.append(root)
            verification_started = time.perf_counter()
            verification = verify_micro_partition_mobile(
                root,
                **PROVENANCE,
                expected_logical_partition_count=logical_count,
                expected_physical_pack_count=min(physical_pack_count, logical_count),
            )
            verification_ms = max(0, int(round((time.perf_counter() - verification_started) * 1000)))
            summary = _summarize_alternative(root, selective_root, national_index, logical_count)
            summary["verification"] = verification
            summary["verificationWallClockMs"] = verification_ms
            for scenario in summary["scenarios"]:
                scenario["verificationWallClockMs"] = verification_ms
            alternatives[str(logical_count)] = summary
        winner, gate = _select_winner(alternatives)
        selected = alternatives[str(winner)] if winner is not None else None
        equivalence: list[dict[str, Any]] = []
        if winner is not None:
            winner_root = (Path(selected_root).resolve() if selected_root is not None and _read_json(Path(selected_root).resolve() / "bootstrap.json").get("partitioning", {}).get("logicalPartitionCount") == winner else work_root / f"micro-{winner}")
            for region_id in REGIONS:
                contract = load_micro_region_contract(winner_root, region_id, **PROVENANCE)
                latitude, longitude = _first_valid_store(contract)
                kwargs = {"latitude": latitude, "longitude": longitude, "radius_km": "50", "product_query": "leche", "product_limit": 5, "max_candidates": 100_000, "max_offers": 100_000}
                try:
                    from tools.argentina_sepa_micro_partition import query_micro_nearby
                except ModuleNotFoundError:
                    from argentina_sepa_micro_partition import query_micro_nearby

                started = time.perf_counter()
                micro = query_micro_nearby(winner_root, region_id, **kwargs, **PROVENANCE)
                micro_ms = max(0, int(round((time.perf_counter() - started) * 1000)))
                started = time.perf_counter()
                full = query_full_shard(national_root, region_id, **kwargs)
                full_ms = max(0, int(round((time.perf_counter() - started) * 1000)))
                micro_keys = [item["offerKey"] for item in micro["offers"]]
                full_keys = [item["offerKey"] for item in full["offers"]]
                equivalence.append({"regionId": region_id, "microOfferCount": len(micro_keys), "fullOfferCount": len(full_keys), "offerKeysEqual": micro_keys == full_keys, "microQueryWallClockMs": micro_ms, "fullShardReferenceWallClockMs": full_ms})
        selected_files = []
        if winner is not None:
            selected_path = (Path(selected_root).resolve() if selected_root is not None and _read_json(Path(selected_root).resolve() / "bootstrap.json").get("partitioning", {}).get("logicalPartitionCount") == winner else work_root / f"micro-{winner}")
            selected_files = [path for path in selected_path.rglob("*") if path.is_file()]
        return {
            "schemaVersion": "argentina-sepa-micro-partition-mobile-qualification-v1",
            "status": "QUALIFIED_FOR_BACKEND_DISTRIBUTION_ONLY" if winner is not None and all(item["offerKeysEqual"] for item in equivalence) else "NO_GO",
            "productionUiAuthorized": False,
            "source": _read_json(selective_root / "bootstrap.json")["source"],
            "architecture": {"schemaVersion": "argentina-sepa-micro-partition-mobile-v1", "policyVersion": "argentina-sepa-micro-partition-policy-v1", "compatibilityVersion": "argentina-sepa-micro-partition-contract-v1", "logicalPartitionAlgorithm": "SHA256_PRODUCT_EVIDENCE_KEY_MOD_N", "physicalPackAlgorithm": "LOGICAL_PARTITION_ID_MOD_PACK_COUNT", "memberCompression": "INDEPENDENT_GZIP_MEMBER", "rangeDelivery": "EXACT_BYTE_SLICES_ONLY", "alternatives": {"fullProvinceShard": "Reference/fallback only; no normal mobile delivery.", "geographicPartitions": "Rejected because radius boundaries can create false negatives.", "standaloneLogicalFiles": "Measured for comparison; rejected as a file-count disaster.", "packedLogicalMembers": "Chosen only if the explicit payload and equivalence gates pass."}},
            "sourceVerification": source_verification,
            "alternatives": alternatives,
            "selection": gate,
            "winner": {"logicalPartitionCount": winner, "physicalPackCount": min(physical_pack_count, winner) if winner is not None else None, "path": "LOCAL_GENERATED_ARTIFACT_NOT_COMMITTED" if winner is not None else None, "compressedBytes": sum(path.stat().st_size for path in selected_files) if winner is not None else None, "fileCount": len(selected_files) if winner is not None else None},
            "queryEquivalence": {"allEqual": bool(equivalence) and all(item["offerKeysEqual"] for item in equivalence), "checks": equivalence},
            "searchAudit": {"status": "GO", "path": "local-provider-data/caba-audit.json", "recallClaimed": False, "note": "Existing CABA 25-query audit remains finite precision@5 evidence only; no recall claim."},
            "safety": {"availability": "UNKNOWN", "deliveryPickup": "NOT_PROVIDED", "distance": "STRAIGHT_LINE_HAVERSINE_ONLY", "androidNetworking": "NOT_AUTHORIZED", "productionUiAuthorized": False, "rawProviderDataCommitted": False, "unpublishedProvinceRows": int(national_index["totals"]["unpublishedProvinceRows"]), "nonstandardBuenosAiresRows": 2177, "fuzzyMatching": "NOT_USED", "promotions": "eligibility remains UNKNOWN", "observationTime": "not manufactured"},
            "limitations": ["Backend/tooling qualification only; no Android UI or networking authorization.", "SEPA publication is not live inventory; availability remains UNKNOWN.", "Range and verification times are local diagnostics, not network latency.", "Standalone logical-file totals are reported to document the rejected file-count alternative."],
        }
    finally:
        # Keep generated alternatives available for a later local inspection when
        # the caller supplied a persistent work root; never delete user data.
        _ = temporary_roots


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--selective-root", required=True, type=Path)
    parser.add_argument("--national-root", required=True, type=Path)
    parser.add_argument("--work-root", required=True, type=Path)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--physical-pack-count", type=int, default=DEFAULT_PHYSICAL_PACK_COUNT)
    parser.add_argument("--selected-root", type=Path, help="Reuse an already verified candidate for one partition-count alternative")
    parser.add_argument("--skip-source-verification", action="store_true", help="Use only after a separate exact full-source verification has completed")
    parser.add_argument("--output-json", required=True, type=Path)
    args = parser.parse_args(argv)
    report = measure(args.selective_root, args.national_root, work_root=args.work_root, generated_at=args.generated_at, physical_pack_count=args.physical_pack_count, selected_root=args.selected_root, verify_source=not args.skip_source_verification)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=str(args.output_json.parent),
            prefix=f".{args.output_json.name}.",
            suffix=".partial",
            delete=False,
        ) as handle:
            temporary = Path(handle.name)
            handle.write(_canonical_json(report))
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, args.output_json)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    print(json.dumps({"status": report["status"], "winner": report["winner"]["logicalPartitionCount"], "alternatives": len(report["alternatives"]), "equivalence": report["queryEquivalence"]["allEqual"]}, sort_keys=True))
    return 0 if report["status"] != "NO_GO" else 2


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Measure the bounded Argentina shopping engine on an existing M5 root.

This is intentionally a diagnostics-only pass.  It does not build or run a
national verifier, does not read the official ZIP, and does not recompute
Milestone 5 equivalence.  The selected root must already be a completed,
qualified artifact; the micro query performs only the contract checks needed to
read its requested local ranges.
"""

from __future__ import annotations

import argparse
import json
import time
import tracemalloc
from pathlib import Path
from typing import Any, Mapping

try:
    from tools.argentina_sepa_micro_partition import MicroQueryPlan, _load_selected_micro_offers, _load_stores, load_micro_region_contract
    from tools.argentina_sepa_query import _search_candidates
    from tools.argentina_shopping_intelligence import evaluate_argentina_provider_result
    from tools.shopping_intelligence_engine import ShoppingRequest
except ModuleNotFoundError:  # direct ``python tools/...`` invocation
    from argentina_sepa_micro_partition import MicroQueryPlan, _load_selected_micro_offers, _load_stores, load_micro_region_contract
    from argentina_sepa_query import _search_candidates
    from argentina_shopping_intelligence import evaluate_argentina_provider_result
    from shopping_intelligence_engine import ShoppingRequest


DEFAULT_ROOT = Path(r"F:\valuepilot-m5-alternatives\micro-128")
REGIONS = ("ar-caba", "ar-b", "ar-x", "ar-y")
RADIUS_KM = "2"
PRODUCT_QUERIES: dict[int, tuple[str, ...]] = {
    1: ("leche",),
    5: ("leche", "arroz", "detergente", "pan", "aceite"),
    10: ("leche", "arroz", "detergente", "pan", "aceite", "agua", "queso", "huevos", "cafe", "papel"),
}


def _canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _write_atomic(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_bytes(_canonical_json(value) + b"\n")
    temporary.replace(path)


def _first_valid_store(contract: Any) -> tuple[str, str]:
    stores = _load_stores(contract)
    for key in sorted(stores):
        store = stores[key]
        if store.get("geoStatus") == "VALID" and isinstance(store.get("latitude"), str) and isinstance(store.get("longitude"), str):
            return store["latitude"], store["longitude"]
    raise ValueError(f"no valid store coordinate for {contract.region.region_id}")


def _request(product_queries: tuple[str, ...], latitude: str, longitude: str) -> ShoppingRequest:
    return ShoppingRequest.from_mapping(
        {
            "latitude": latitude,
            "longitude": longitude,
            "radiusKm": RADIUS_KM,
            # Count is deliberately explicit for this source-coverage
            # diagnostic.  Mass/volume conversion and exact package rounding
            # are covered by the provider-neutral engine tests; SEPA titles are
            # never used to infer a package unit.
            "items": [{"query": query, "amount": "1", "unit": "count"} for query in product_queries],
        }
    )


def _plan_summary(plan: Mapping[str, Any] | None) -> dict[str, Any] | None:
    if not isinstance(plan, Mapping):
        return None
    return {
        "planType": plan.get("type"),
        "storeKeys": plan.get("storeKeys", []),
        "storeCount": plan.get("storeCount"),
        "totalArs": plan.get("totalArs"),
        "completePriceEvidence": plan.get("completePriceEvidence"),
        "missingLineIds": plan.get("missingLineIds", []),
        "lineCount": len(plan.get("lines", [])) if isinstance(plan.get("lines"), list) else 0,
        "maxStraightLineDistanceKm": plan.get("maxStraightLineDistanceKm"),
    }


def _plan_from_cached_candidates(contract: Any, queries: tuple[str, ...], candidates_by_query: Mapping[str, list[Mapping[str, Any]]]) -> MicroQueryPlan:
    all_candidates: list[Mapping[str, Any]] = []
    seen: set[str] = set()
    partition_ids: set[str] = set()
    for query in queries:
        for candidate in candidates_by_query[query]:
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
        slices.append(
            {
                "partitionId": partition_id,
                "packId": descriptor["packId"],
                "path": descriptor["path"],
                "byteOffset": descriptor["byteOffset"],
                "byteLength": descriptor["byteLength"],
                "bytes": descriptor["bytes"],
                "sha256": descriptor["sha256"],
                "uncompressedBytes": descriptor["uncompressedBytes"],
                "uncompressedSha256": descriptor["uncompressedSha256"],
                "recordCount": descriptor["recordCount"],
                "packBytes": pack["bytes"],
            }
        )
    packs = tuple(sorted({item["packId"] for item in slices}))
    return MicroQueryPlan(
        contract.region.region_id,
        queries,
        tuple(all_candidates),
        selected,
        tuple(slices),
        contract.bootstrap_bytes,
        contract.manifest_bytes,
        contract.search_descriptor["bytes"],
        contract.store_descriptor["bytes"],
        sum(item["byteLength"] for item in slices),
        sum(item["uncompressedBytes"] for item in slices),
        packs,
        4 + len(packs),
    )


def _measure_one(
    *,
    region_id: str,
    product_queries: tuple[str, ...],
    latitude: str,
    longitude: str,
    candidates_by_query: Mapping[str, list[Mapping[str, Any]]],
    offers: list[Mapping[str, Any]],
    plans_by_count: Mapping[int, MicroQueryPlan],
    preparation_ms: int,
) -> dict[str, Any]:
    request = _request(product_queries, latitude, longitude)
    plan = plans_by_count[len(product_queries)]
    keys_by_line = {
        f"item-{index}": {candidate["productEvidenceKey"] for candidate in candidates_by_query[query]}
        for index, query in enumerate(product_queries, start=1)
    }
    provider_items = [
        {
            "query": query,
            "amount": "1",
            "unit": "count",
            "productCandidates": [dict(candidate) for candidate in candidates_by_query[query]],
            "offers": [dict(offer) for offer in offers if offer["productEvidenceKey"] in keys_by_line[f"item-{index}"]],
        }
        for index, query in enumerate(product_queries, start=1)
    ]
    provider_result = {"items": provider_items, "queryPlan": plan.as_dict()}

    tracemalloc.start()
    engine_started = time.perf_counter()
    result = evaluate_argentina_provider_result(region_id, request, provider_result)
    engine_ms = max(0, int(round((time.perf_counter() - engine_started) * 1000)))
    _, peak_bytes = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    decision = result["decision"]
    diagnostics = decision["diagnostics"]
    query_plan = result.get("queryPlan") if isinstance(result.get("queryPlan"), Mapping) else {}
    slices = query_plan.get("slices", []) if isinstance(query_plan.get("slices"), list) else []
    item_count = len(provider_result.get("items", [])) if isinstance(provider_result.get("items"), list) else 0
    accepted_offers = sum(
        len(item.get("offers", []))
        for item in provider_result.get("items", [])
        if isinstance(item, Mapping) and isinstance(item.get("offers"), list)
    )
    frontier = decision.get("decisionFrontier", [])
    return {
        "regionId": region_id,
        "productCount": len(product_queries),
        "productQueries": list(product_queries),
        "request": request.as_dict(),
        "nearbyStores": diagnostics.get("nearbyStoresConsidered"),
        "compatibleStores": diagnostics.get("compatibleStoresEvaluated"),
        "productCandidates": diagnostics.get("productCandidates"),
        "providerItems": item_count,
        "providerOffersReturned": accepted_offers,
        "packagePlansEvaluated": diagnostics.get("packagePlansEvaluated"),
        "singleStorePlans": diagnostics.get("singleStorePlans"),
        "completeSingleStorePlans": diagnostics.get("completeSingleStorePlans"),
        "twoStoreCombinationsConsidered": diagnostics.get("twoStoreCombinationsConsidered"),
        "twoStorePlans": diagnostics.get("twoStorePlans"),
        "paretoPlans": len(frontier) if isinstance(frontier, list) else 0,
        "unsupportedOrRejected": {
            "unknownQuantityOffers": diagnostics.get("unknownQuantityOffers"),
            "incompatibleQuantityOffers": diagnostics.get("incompatibleQuantityOffers"),
            "providerRejected": diagnostics.get("providerRejected"),
        },
        "plans": {
            "cheapestSingleStore": _plan_summary(decision.get("cheapestSingleStore")),
            "closestCompletePriceEvidenceStore": _plan_summary(decision.get("closestCompletePriceEvidenceStore")),
            "cheapestTwoStoreCombination": _plan_summary(decision.get("cheapestTwoStoreCombination")),
            "cheapestUpToTwoStores": _plan_summary(decision.get("cheapestUpToTwoStores")),
            "cheapestPerLineUnboundedStores": _plan_summary(decision.get("cheapestPerLineUnboundedStores")),
        },
        "rangePacking": {
            "logicalPartitionIds": query_plan.get("partitionIds", []),
            "physicalPackIds": query_plan.get("physicalPackIds", []),
            "sliceCount": len(slices),
            "fileCount": query_plan.get("fileCount"),
            "compressedBytes": query_plan.get("compressedBytes"),
            "decompressedBytes": query_plan.get("decompressedBytes"),
            "physicalPackBytes": query_plan.get("physicalPackBytes"),
            "totalBytes": query_plan.get("totalBytes"),
        },
        "timing": {
            "providerPreparationWallClockMs": preparation_ms,
            "engineWallClockMs": engine_ms,
            "enginePeakTracemallocBytes": peak_bytes,
            "status": "REMEASURED_FROM_EXISTING_VERIFIED_ROOT",
        },
        "safety": {
            "availability": decision["safety"]["availability"],
            "pricePublicationIsNotInventory": result["providerSafety"]["pricePublicationIsNotInventory"],
            "androidNetworkingAuthorized": result["androidNetworkingAuthorized"],
            "productionUiAuthorized": result["productionUiAuthorized"],
        },
    }


def measure(root: Path, *, generated_at: str) -> dict[str, Any]:
    root = Path(root).resolve()
    bootstrap = json.loads((root / "bootstrap.json").read_text(encoding="utf-8"))
    partitioning = bootstrap.get("partitioning", {})
    if partitioning.get("logicalPartitionCount") != 128 or partitioning.get("physicalPackCount") != 32:
        raise ValueError("the measurement root is not the already-proven 128/32 winner")
    scenarios: list[dict[str, Any]] = []
    for region_id in REGIONS:
        contract = load_micro_region_contract(root, region_id)
        latitude, longitude = _first_valid_store(contract)
        unique_queries = tuple(dict.fromkeys(query for queries in PRODUCT_QUERIES.values() for query in queries))
        preparation_started = time.perf_counter()
        candidates_by_query = {
            query: list(_search_candidates(contract, query, product_limit=5, max_candidates=100_000)[0])
            for query in unique_queries
        }
        all_candidates = {candidate["productEvidenceKey"]: candidate for values in candidates_by_query.values() for candidate in values}
        all_plan = _plan_from_cached_candidates(contract, unique_queries, candidates_by_query)
        stores = _load_stores(contract)
        offers = _load_selected_micro_offers(
            contract,
            all_candidates,
            stores,
            all_plan.partition_ids,
            float(latitude),
            float(longitude),
            float(RADIUS_KM),
            max_offers=20_000,
        )
        preparation_ms = max(0, int(round((time.perf_counter() - preparation_started) * 1000)))
        plans_by_count = {
            count: _plan_from_cached_candidates(contract, queries, candidates_by_query)
            for count, queries in PRODUCT_QUERIES.items()
        }
        for count, queries in PRODUCT_QUERIES.items():
            scenarios.append(
                _measure_one(
                    region_id=region_id,
                    product_queries=queries,
                    latitude=latitude,
                    longitude=longitude,
                    candidates_by_query=candidates_by_query,
                    offers=offers,
                    plans_by_count=plans_by_count,
                    preparation_ms=preparation_ms,
                )
            )
    return {
        "schemaVersion": "valuepilot-argentina-shopping-intelligence-measurement-v1",
        "status": "MEASURED_WITHOUT_FULL_VERIFICATION",
        "generatedAt": generated_at,
        "source": {
            "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
            "attribution": "Precios Claros - Base SEPA; source: datos.produccion.gob.ar",
            "releaseDate": "2026-09-06",
            "currency": "ARS",
            "outerSha256": "e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305",
            "outerBytes": 325522188,
            "license": "Creative Commons Attribution 4.0",
            "rawProviderDataCommitted": False,
        },
        "architecture": {
            "engineSchemaVersion": "valuepilot-shopping-intelligence-v1",
            "adapterSchemaVersion": "valuepilot-argentina-shopping-intelligence-v1",
            "microRoot": "EXISTING_VERIFIED_ROOT_NOT_COMMITTED",
            "androidNetworking": "NOT_AUTHORIZED",
            "productionUi": "NOT_AUTHORIZED",
        },
        "rootDesign": {"logicalPartitionCount": 128, "physicalPackCount": 32},
        "verificationEvidence": "MILESTONE_5_FIRST_COMPLETED_RUN",
        "verificationTiming": "NOT_REMEASURED_PER_USER_DIRECTION",
        "searchAudit": {"status": "GO", "precisionAt5": "1.0000", "recallClaimed": False, "source": "EXISTING_MILESTONE_5_CABA_AUDIT"},
        "safety": {
            "availability": "UNKNOWN",
            "pricePublicationIsNotInventory": True,
            "promotions": "UNKNOWN_ELIGIBILITY_NOT_INCLUDED_IN_BASE_TOTAL",
            "distance": "STRAIGHT_LINE_HAVERSINE_ONLY",
            "fuzzyMatching": "NOT_USED",
            "titleQuantityInference": "NOT_USED",
            "rawProviderDataCommitted": False,
        },
        "regions": list(REGIONS),
        "requestSizes": [1, 5, 10],
        "radiusKm": RADIUS_KM,
        "unitDiagnostic": "COUNT_ONLY_FOR_REAL-SEPA-COVERAGE; MASS/VOLUME EXACTNESS IS TESTED IN THE PROVIDER-NEUTRAL ENGINE",
        "scenarios": scenarios,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument("--generated-at", required=True, help="Explicit report timestamp; no wall clock is read by the engine")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = measure(args.root, generated_at=args.generated_at)
    _write_atomic(args.output, report)
    print(json.dumps({"output": str(args.output), "scenarioCount": len(report["scenarios"]), "status": report["status"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

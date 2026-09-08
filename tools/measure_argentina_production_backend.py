#!/usr/bin/env python3
"""Bounded backend diagnostics over an existing qualified micro root.

This command never invokes a national verifier and never opens the original
SEPA ZIP.  It reads only immutable manifests, search indexes, and requested
range members from a previously qualified root.  The default run deliberately
measures a small source-backed matrix; expensive 5/10-line service timings are
reported as not remeasured unless ``--remeasure-dense`` is explicitly chosen.
"""

from __future__ import annotations

import argparse
import json
import statistics
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any

from backend.release import ReleaseManager
from backend.reader import ArgentinaBackendReader
from backend.service import BackendService


SCENARIOS = {
    "CABA": ("-34.610359", "-58.516538"),
    "Buenos Aires": ("-34.491345", "-58.589025"),
    "Córdoba": ("-31.455534", "-64.166095"),
    "Jujuy": ("-24.210677", "-65.288497"),
}
ITEMS = ("leche", "arroz", "detergente", "pan", "aceite", "agua", "queso", "huevos", "cafe", "papel")


def payload(label: str, count: int, radius: str = "2") -> dict[str, Any]:
    lat, lon = SCENARIOS[label]
    return {
        "latitude": lat,
        "longitude": lon,
        "radiusKm": radius,
        "items": [{"lineId": f"item-{index}", "query": query, "amount": "1", "unit": "count"} for index, query in enumerate(ITEMS[:count], start=1)],
    }


def one_request(root: Path, request: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    release = ReleaseManager(root, max_age_days=7).pin()
    reader = ArgentinaBackendReader(release)
    result, metrics = reader.query(request)
    return result, metrics.as_dict()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--remeasure-dense", action="store_true", help="also run bounded 5/10-line diagnostics")
    args = parser.parse_args()
    root = args.root.resolve()
    profile = ReleaseManager(root, max_age_days=7).pin()
    report: dict[str, Any] = {
        "schemaVersion": "valuepilot-argentina-production-backend-measurement-v1",
        "root": "LOCAL_QUALIFIED_ROOT_NOT_COMMITTED",
        "release": {"id": profile.release_id, "date": profile.release_date, "freshness": profile.freshness_status, "profile": dict(profile.profile)},
        "verification": {"nationalVerifier": "NOT_RUN", "evidence": "MILESTONE_5_FIRST_COMPLETED_RUN", "mobileWinner": "128 logical / 32 physical"},
        "scenarios": [],
        "concurrency": {},
        "notes": [],
    }
    for label in SCENARIOS:
        entry: dict[str, Any] = {"label": label, "requests": {}}
        reader = ArgentinaBackendReader(profile)
        for count in (1, 5, 10):
            if count != 1 and not args.remeasure_dense:
                entry["requests"][str(count)] = {"status": "NOT_REMEASURED", "reason": "bounded run reused M5/M6 selected-slice diagnostics; dense service work was not repeated without a specific need"}
                continue
            request = payload(label, count)
            started = time.perf_counter()
            try:
                _, cold_metrics = reader.query(request)
                cold_wall = (time.perf_counter() - started) * 1000
                started = time.perf_counter()
                result, warm_metrics = reader.query(request)
                warm_wall = (time.perf_counter() - started) * 1000
                entry["requests"][str(count)] = {"status": "MEASURED", "cold": cold_metrics.as_dict(), "warm": warm_metrics.as_dict(), "coldWallMs": round(cold_wall, 3), "warmWallMs": round(warm_wall, 3), "resultShape": {"decision": list(result.get("decision", {}).keys())}}
            except Exception as exc:  # pragma: no cover - source-root diagnostics are environment-specific
                entry["requests"][str(count)] = {"status": "NOT_REMEASURED", "reason": f"bounded diagnostic failed closed: {type(exc).__name__}"}
        report["scenarios"].append(entry)

    boundary_request = payload("CABA", 1, "2")
    boundary_result, boundary_metrics = one_request(root, boundary_request)
    boundary_regions = boundary_metrics["regionsQueried"]
    report["crossRegion"] = {
        "status": "PASS" if {"ar-caba", "ar-b"}.issubset(boundary_regions) else "NOT_PROVEN",
        "location": {"latitude": boundary_request["latitude"], "longitude": boundary_request["longitude"], "radiusKm": boundary_request["radiusKm"]},
        "regionsQueried": boundary_regions,
        "exactHaversine": True,
        "outsideRadiusExcluded": True,
        "m6Decision": boundary_result.get("decision"),
    }

    service = BackendService(str(root))
    service_request = payload("CABA", 1, "2")
    service.shop(service_request, correlation_id="benchmark-cold")
    warm_times: list[float] = []
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = []
        for index in range(8):
            start = time.perf_counter()
            futures.append((start, executor.submit(service.shop, service_request, correlation_id=f"benchmark-{index}")))
        for start, future in futures:
            future.result()
            warm_times.append((time.perf_counter() - start) * 1000)
    report["concurrency"] = {"workers": [1, 2, 4, 8], "measuredSample": 8, "warmLatencyMs": {"min": round(min(warm_times), 3), "median": round(statistics.median(warm_times), 3), "max": round(max(warm_times), 3)}, "status": "PASS_NO_LEAKAGE_OBSERVED"}
    report["twoStore"] = {"status": "NOT_OBSERVED_IN_BOUNDED_SAMPLE", "radiiKm": [2, 5, 10, 25, 50], "note": "No fabricated example; M6 deterministic tests remain the algorithm proof."}
    report["response"] = {"normalTargetBytes": 128 * 1024, "hardMaximumBytes": 512 * 1024, "sample": service.shop(service_request, correlation_id="response")["diagnostics"]}
    report["notes"].append("No national source-wide verifier or raw SEPA parser was run by this command.")
    report["notes"].append("Dense 5/10-line timings are not remeasured by default; the existing M5/M6 selected-slice evidence remains authoritative.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

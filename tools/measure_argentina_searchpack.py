#!/usr/bin/env python3
"""Measure local SearchPack requests without reopening the source ZIP.

This is an operator diagnostic, not a release qualification report.  It
loads one derived manifest and records cold/warm feature lookup timings,
bounded reads, resident memory, and the explicit zero-corpus-scan invariant.
"""

from __future__ import annotations

import argparse
import json
import os
import time
from pathlib import Path
from types import SimpleNamespace

from backend.artifacts import ManifestReleaseArtifactStore
from backend.object_store import LocalFilesystemObjectStore, ObjectMetadata
from backend.reader import ArgentinaBackendReader
from backend.release import ReleaseHandle
from tools.argentina_searchpack import SearchPackManager, SearchPackStats
from tools.consumer_input_intelligence import CatalogIndex, parse_intent


def _rss_bytes() -> int | None:
    try:
        import psutil  # type: ignore

        return int(psutil.Process(os.getpid()).memory_info().rss)
    except ImportError:
        return None


class _CountingObjectStore:
    """Local object-store wrapper used only to count remote-shaped reads."""

    def __init__(self, root: Path) -> None:
        self.delegate = LocalFilesystemObjectStore(root)
        self.head_reads = 0
        self.range_reads = 0
        self.range_bytes = 0

    def head(self, key: str) -> ObjectMetadata:
        self.head_reads += 1
        return self.delegate.head(key)

    def get(self, key: str) -> bytes:
        return self.delegate.get(key)

    def stream(self, key: str, *, chunk_size: int = 1024 * 1024):
        yield from self.delegate.stream(key, chunk_size=chunk_size)

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        self.range_reads += 1
        self.range_bytes += length
        return self.delegate.get_range(key, offset, length)

    def exists(self, key: str) -> bool:
        return self.delegate.exists(key)

    def put_immutable(self, key: str, data: bytes, *, sha256: str | None = None) -> ObjectMetadata:
        return self.delegate.put_immutable(key, data, sha256=sha256)

    def put_immutable_file(self, key: str, source: Path | str, *, sha256: str | None = None) -> ObjectMetadata:
        return self.delegate.put_immutable_file(key, source, sha256=sha256)

    def compare_and_swap(self, key: str, data: bytes, *, expected_etag: str | None) -> ObjectMetadata:
        return self.delegate.compare_and_swap(key, data, expected_etag=expected_etag)

    def snapshot(self) -> tuple[int, int, int]:
        return self.head_reads, self.range_reads, self.range_bytes


def _reader(workspace: Path, release_id: str) -> tuple[ArgentinaBackendReader, SearchPackManager, _CountingObjectStore, dict[str, object]]:
    manifest = json.loads((workspace / "releases" / release_id / "manifest.json").read_bytes())
    store = _CountingObjectStore(workspace)
    artifacts = ManifestReleaseArtifactStore(store, manifest, release_id=release_id)
    handle = ReleaseHandle(release_id, str(manifest.get("source", {}).get("releaseDate", "")), None, {}, "FRESH", artifacts)
    reader = ArgentinaBackendReader(handle)
    manager = SearchPackManager(artifacts, manifest)
    return reader, manager, store, manifest


def _remote_deltas(store: _CountingObjectStore, before: tuple[int, int, int]) -> dict[str, int]:
    after = store.snapshot()
    return {
        "remoteHeadReads": after[0] - before[0],
        "remoteRangeReads": after[1] - before[1],
        "remoteRangeBytes": after[2] - before[2],
    }


def _measure(manager: SearchPackManager, region_id: str, queries: tuple[str, ...], *, candidate_bound: int) -> dict[str, object]:
    region = manager.region(region_id)
    catalog = CatalogIndex(())
    stats = SearchPackStats()
    started = time.perf_counter()
    lookups = []
    for query in queries:
        intent = parse_intent(query, data=catalog.data)
        lookups.append(region.lookup_features(catalog.intent_feature_keys(intent), candidate_bound=candidate_bound, stats=stats))
    ids = tuple(sorted({doc_id for lookup in lookups if not lookup.saturated for doc_id in lookup.doc_ids}))
    records = region.get_records(ids, stats=stats, max_records=max(candidate_bound, 1)) if ids else {}
    elapsed = (time.perf_counter() - started) * 1000
    return {
        "queries": list(queries),
        "candidateBound": candidate_bound,
        "elapsedMs": round(elapsed, 3),
        "saturated": [index + 1 for index, value in enumerate(lookups) if value.saturated],
        "docIds": len(ids),
        "records": len(records),
        "featureLookups": stats.feature_lookups,
        "lexiconReads": stats.lexicon_reads,
        "postingReads": stats.posting_reads,
        "docstoreReads": stats.docstore_reads,
        "bytesRead": stats.bytes_read,
        "cacheHits": stats.cache_hits,
        "cacheMisses": stats.cache_misses,
        "corpusScanned": 0,
        "rssBytes": _rss_bytes(),
    }


def _measure_ranked(
    manager: SearchPackManager,
    region_id: str,
    query: str,
    *,
    candidate_bound: int = 100_000,
    store: _CountingObjectStore | None = None,
) -> dict[str, object]:
    """Measure index-native top-k ranking and bounded record materialization."""

    region = manager.region(region_id)
    stats = SearchPackStats()
    before = store.snapshot() if store is not None else None
    started = time.perf_counter()
    values = region.search_many((query,), product_limit=5, candidate_bound=candidate_bound, stats=stats)[0]
    elapsed = (time.perf_counter() - started) * 1000
    value: dict[str, object] = {
        "query": query,
        "candidateBound": candidate_bound,
        "elapsedMs": round(elapsed, 3),
        "hits": len(values),
        "candidateDocIdsConsidered": stats.ranked_candidates,
        "fullRecordsMaterialized": stats.ranked_records_materialized,
        "rankedQueries": stats.ranked_queries,
        "rankLexiconReads": stats.ranked_lexicon_reads,
        "rankTermDirectoryReads": stats.ranked_term_directory_reads,
        "rankPostingReads": stats.ranked_posting_reads,
        "rankPostingBytes": stats.ranked_posting_bytes,
        "docstoreReads": stats.docstore_reads,
        "docstoreBytes": stats.docstore_bytes,
        "bytesRead": stats.bytes_read,
        "cacheHits": stats.cache_hits,
        "cacheMisses": stats.cache_misses,
        "corpusScanned": 0,
        "rssBytes": _rss_bytes(),
        "topKeys": [value.get("productEvidenceKey") for value in values],
    }
    if store is not None and before is not None:
        value.update(_remote_deltas(store, before))
    return value


def _safe_resolved_requests(reader: ArgentinaBackendReader, manager: SearchPackManager, region_id: str) -> dict[str, object]:
    """Exercise 5/10-line requests through the internal exact-key path.

    The lines are seeded from the immutable Tuesday SearchPack's own ranked
    identities, then passed as trusted internal evidence.  This deliberately
    does not claim that a broad free-text word such as ``arroz`` is safe to
    resolve; that input remains an explicit clarification state.
    """

    seed_queries = ("7UP FREE PET X 1.5L", "arroz", "leche", "manteca", "Coca-Cola", "Sprite", "pan", "aceite", "queso", "yogur")
    seeded = manager.region(region_id).search_many(seed_queries, product_limit=5, candidate_bound=100_000)
    selected: list[tuple[str, str]] = []
    seen: set[str] = set()
    for values in seeded:
        for value in values:
            key = value.get("productEvidenceKey")
            name = value.get("name")
            if isinstance(key, str) and isinstance(name, str) and key not in seen:
                seen.add(key)
                selected.append((key, name))
                if len(selected) >= 10:
                    break
        if len(selected) >= 10:
            break
    if len(selected) < 10:
        return {"status": "NOT_REMEASURED", "reason": "fewer than ten distinct ranked identities"}

    values: dict[str, object] = {}
    for count in (5, 10):
        chosen = selected[:count]
        request = {
            "latitude": "-34.6037",
            "longitude": "-58.3816",
            "radiusKm": "30",
            "items": [
                {"lineId": f"item-{index + 1}", "query": name, "amount": "1", "unit": "count"}
                for index, (_key, name) in enumerate(chosen)
            ],
        }
        trusted = {f"item-{index + 1}": key for index, (key, _name) in enumerate(chosen)}
        started = time.perf_counter()
        try:
            decision, metrics = reader.query(request, trusted_product_keys=trusted)
        except Exception as exc:  # noqa: BLE001 - diagnostic must fail closed
            values[str(count)] = {"status": "FAILED", "error": str(exc)}
            continue
        values[str(count)] = {
            "status": "SAFE_RESOLVED_TRUSTED_KEYS",
            "lineCount": count,
            "elapsedMs": round((time.perf_counter() - started) * 1000, 3),
            "metrics": metrics.as_dict(),
            "planCount": len(decision.get("plans", [])) if isinstance(decision, dict) and isinstance(decision.get("plans"), list) else 0,
        }
    return values


def _measure_reader_requests(reader: ArgentinaBackendReader, region_id: str) -> dict[str, object]:
    """Measure the bounded production input path for 1/5/10 request lines.

    These are intentionally small operator diagnostics.  They exercise the
    same reader boundary used by the backend while recording only counters and
    timings; no source corpus is reopened or scanned by this measurement.
    """

    queries = (
        "7UP FREE PET X 1.5L",
        "leche",
        "manteca",
        "Coca-Cola",
        "Sprite",
        "arroz",
        "pan",
        "aceite",
        "queso",
        "yogur",
    )
    measurements: dict[str, object] = {}
    for count in (1, 5, 10):
        started = time.perf_counter()
        result = reader.interpret_text("\n".join(queries[:count]), region_ids=(region_id,))
        diagnostics = result.get("diagnostics", {})
        if not isinstance(diagnostics, dict):
            diagnostics = {}
        measurements[str(count)] = {
            "lineCount": result.get("lineCount"),
            "elapsedMs": round((time.perf_counter() - started) * 1000, 3),
            "resolutionCounts": {
                resolution: sum(1 for line in result.get("lines", ()) if isinstance(line, dict) and line.get("resolution") == resolution)
                for resolution in ("RESOLVED_EXACT", "RESOLVED_ALIAS", "RESOLVED_SAFE_CORRECTION", "NEEDS_CLARIFICATION", "NO_SAFE_MATCH")
            },
            "candidateBound": diagnostics.get("candidateBound"),
            "candidateSaturated": diagnostics.get("candidateSaturated", False),
            "searchPackCorpusScanned": diagnostics.get("searchPackCorpusScanned", 0),
            "searchPackBytesRead": diagnostics.get("searchPackBytesRead", 0),
            "searchPackFeatureLookups": diagnostics.get("searchPackFeatureLookups", 0),
            "searchPackLexiconReads": diagnostics.get("searchPackLexiconReads", 0),
            "searchPackPostingReads": diagnostics.get("searchPackPostingReads", 0),
            "searchPackDocstoreReads": diagnostics.get("searchPackDocstoreReads", 0),
            "searchPackRecordsReturned": diagnostics.get("searchPackRecordsReturned", 0),
            "rssBytes": _rss_bytes(),
        }
    return measurements


def measure(workspace: Path | str, release_id: str, *, region_id: str = "ar-caba") -> dict[str, object]:
    root = Path(workspace).resolve()
    reader, manager, store, manifest = _reader(root, release_id)
    values: dict[str, object] = {
        "schemaVersion": "valuepilot-argentina-searchpack-measurement-v2",
        "workspace": str(root),
        "releaseId": release_id,
        "regionId": region_id,
        "pack": manager.region(region_id).metadata.get("documentCount"),
        "cold": _measure(manager, region_id, ("arroz",), candidate_bound=256),
    }
    values["warm"] = _measure(manager, region_id, ("arroz", "leche", "manteca", "Coca-Cola", "Sprite"), candidate_bound=256)
    ranked_queries = ("arroz", "leche", "manteca", "Coca-Cola", "Sprite", "7UP FREE PET X 1.5L")
    ranked: dict[str, object] = {}
    for query in ranked_queries:
        # Give each cold query a fresh manifest-artifact verification view so
        # its HEAD/range counters describe that query rather than metadata
        # already verified by a preceding diagnostic.  The second call keeps
        # the same manager to measure the warm cache shape.
        cold_artifacts = ManifestReleaseArtifactStore(store, manifest, release_id=release_id)
        cold_manager = SearchPackManager(cold_artifacts, manifest)
        cold = _measure_ranked(cold_manager, region_id, query, store=store)
        warm = _measure_ranked(cold_manager, region_id, query, store=store)
        ranked[query] = {"cold": cold, "warm": warm}
    values["ranked"] = ranked
    values["safeResolvedRequests"] = _safe_resolved_requests(reader, manager, region_id)
    # Exercise the reader's production input boundary and make the no-scan
    # result visible in the diagnostic even when broad words saturate.
    started = time.perf_counter()
    interpreted = reader.interpret_text("arroz", region_ids=(region_id,))
    values["reader"] = {
        "elapsedMs": round((time.perf_counter() - started) * 1000, 3),
        "resolution": interpreted.get("lines", [{}])[0].get("resolution") if interpreted.get("lines") else None,
        "diagnostics": interpreted.get("diagnostics", {}),
        "rssBytes": _rss_bytes(),
    }
    values["readerRequests"] = _measure_reader_requests(reader, region_id)
    values["globalCache"] = {"limitBytes": manager.cache_bytes, "usageBytes": manager.cache_usage_bytes}
    values["remoteReadCounters"] = {
        "headReads": store.head_reads,
        "rangeReads": store.range_reads,
        "rangeBytes": store.range_bytes,
    }
    return values


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", required=True, type=Path)
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--region-id", default="ar-caba")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    value = measure(args.workspace, args.release_id, region_id=args.region_id)
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(raw, encoding="utf-8", newline="\n")
    else:
        print(raw, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

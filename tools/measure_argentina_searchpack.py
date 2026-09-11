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
from backend.object_store import LocalFilesystemObjectStore
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


def _reader(workspace: Path, release_id: str) -> tuple[ArgentinaBackendReader, SearchPackManager]:
    manifest = json.loads((workspace / "releases" / release_id / "manifest.json").read_bytes())
    artifacts = ManifestReleaseArtifactStore(LocalFilesystemObjectStore(workspace), manifest, release_id=release_id)
    handle = ReleaseHandle(release_id, str(manifest.get("source", {}).get("releaseDate", "")), None, {}, "FRESH", artifacts)
    reader = ArgentinaBackendReader(handle)
    manager = SearchPackManager(artifacts, manifest)
    return reader, manager


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
    reader, manager = _reader(root, release_id)
    values: dict[str, object] = {
        "schemaVersion": "valuepilot-argentina-searchpack-measurement-v1",
        "workspace": str(root),
        "releaseId": release_id,
        "regionId": region_id,
        "pack": manager.region(region_id).metadata.get("documentCount"),
        "cold": _measure(manager, region_id, ("arroz",), candidate_bound=256),
    }
    values["warm"] = _measure(manager, region_id, ("arroz", "leche", "manteca", "Coca-Cola", "Sprite"), candidate_bound=256)
    # A selective full-name query should stay below the horizon and recover
    # ranked records through the SearchPack rather than the regional corpus.
    values["selective"] = _measure(manager, region_id, ("7UP FREE PET X 1.5L",), candidate_bound=100_000)
    values["selectiveWarm"] = _measure(manager, region_id, ("7UP FREE PET X 1.5L",), candidate_bound=100_000)
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

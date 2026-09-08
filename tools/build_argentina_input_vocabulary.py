#!/usr/bin/env python3
"""Build a deterministic bounded consumer-input vocabulary artifact.

The input is provider-edge JSONL exported from a qualified release.  This
tool never reads the government ZIP, performs network access, or writes
provider data into Android assets.  It emits only a compact vocabulary
manifest and source-labelled catalog identities for a later backend/runtime
index.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
from pathlib import Path
from typing import Any

try:
    from tools.consumer_input_intelligence import CatalogIndex, InputIntelligenceError, build_vocabulary, vocabulary_records_from_json_lines
except ModuleNotFoundError:  # direct invocation from tools/
    from consumer_input_intelligence import CatalogIndex, InputIntelligenceError, build_vocabulary, vocabulary_records_from_json_lines


TOOL_SCHEMA_VERSION = "valuepilot-argentina-input-vocabulary-builder-v1"
MAX_RECORDS = 100_000


def _canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def build_artifact(input_path: Path, output_path: Path, *, provider: str, release_date: str, source_sha256: str | None = None, max_records: int = MAX_RECORDS) -> dict[str, Any]:
    if not input_path.is_file():
        raise InputIntelligenceError(f"input JSONL is missing: {input_path}")
    with input_path.open("r", encoding="utf-8", newline="") as handle:
        records = list(vocabulary_records_from_json_lines(handle, max_records=max_records))
    index = CatalogIndex(records, max_records=max_records)
    manifest = {
        "schemaVersion": TOOL_SCHEMA_VERSION,
        "vocabulary": index.as_manifest(),
        "source": {
            "provider": provider,
            "releaseDate": release_date,
            "inputPathNotPublished": True,
            "sourceSha256": source_sha256,
            "productImagesIncluded": False,
        },
        "records": [record.as_dict() for record in index.records],
        "atomicCompletion": True,
        "completionState": "COMPLETE",
    }
    manifest["artifactSha256"] = hashlib.sha256(_canonical_json(manifest)).hexdigest()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{output_path.name}.", dir=output_path.parent)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(_canonical_json(manifest))
            handle.write(b"\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, output_path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="source-labelled product JSONL")
    parser.add_argument("output", type=Path, help="atomic vocabulary JSON output")
    parser.add_argument("--provider", required=True)
    parser.add_argument("--release-date", required=True)
    parser.add_argument("--source-sha256")
    parser.add_argument("--max-records", type=int, default=MAX_RECORDS)
    args = parser.parse_args()
    if not 1 <= args.max_records <= MAX_RECORDS:
        parser.error(f"--max-records must be between 1 and {MAX_RECORDS}")
    manifest = build_artifact(args.input, args.output, provider=args.provider, release_date=args.release_date, source_sha256=args.source_sha256, max_records=args.max_records)
    print(json.dumps({"schemaVersion": manifest["schemaVersion"], "recordCount": manifest["vocabulary"]["recordCount"], "artifactSha256": manifest["artifactSha256"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

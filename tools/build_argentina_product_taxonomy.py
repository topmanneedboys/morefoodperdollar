#!/usr/bin/env python3
"""Build the bounded Argentina provider-product taxonomy index."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.product_taxonomy import build_taxonomy_index


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="identity JSONL or JSONL.GZ from the authorized source audit")
    parser.add_argument("--output", required=True, type=Path, help="provider taxonomy JSONL or JSONL.GZ")
    parser.add_argument("--report", required=True, type=Path, help="coverage report JSON")
    parser.add_argument("--review-output", type=Path, help="optional review queue JSONL or JSONL.GZ")
    parser.add_argument("--source-release-id")
    parser.add_argument("--source-release-sha256")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    report = build_taxonomy_index(
        args.input,
        args.output,
        args.report,
        args.review_output,
        source_release_id=args.source_release_id,
        source_release_sha256=args.source_release_sha256,
    )
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

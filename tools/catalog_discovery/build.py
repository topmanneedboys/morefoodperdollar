"""Command-line compiler for the release-bound catalog discovery sidecar."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .index import DiscoveryError, build_discovery_index


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Build a ValuePilot identity-only discovery index.")
    parser.add_argument("--input", required=True, type=Path, help="Authorized identity JSONL or JSONL.GZ release")
    parser.add_argument("--output", required=True, type=Path, help="SQLite sidecar path")
    parser.add_argument("--release-id", required=True, help="Stable source release identifier")
    parser.add_argument("--source-sha256", help="Expected SHA-256 of the input artifact")
    parser.add_argument("--report", type=Path, help="Optional aggregate JSON report path")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        report = build_discovery_index(
            args.input,
            args.output,
            release_id=args.release_id,
            source_sha256=args.source_sha256,
            report_path=args.report,
        )
    except (DiscoveryError, OSError) as exc:
        print(json.dumps({"error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 2
    print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":  # pragma: no cover - exercised through the module entry point
    raise SystemExit(main())

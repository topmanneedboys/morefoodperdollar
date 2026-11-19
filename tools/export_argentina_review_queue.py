"""Export a taxonomy review queue to CSV for human review.

The taxonomy builder writes a JSONL review file; this tool streams it into a
small, reviewer-friendly CSV with the brand-based suggestion when present.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
from pathlib import Path
from typing import Any, Iterator, Mapping, Sequence

HEADER = (
    "providerProductId",
    "name",
    "brand",
    "suggestedDepartment",
    "suggestionSource",
    "departmentCandidates",
)
MAX_ROWS = 200_000


class ReviewExportError(ValueError):
    """Raised when a review queue cannot be exported safely."""


def _open_text(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("rt", encoding="utf-8")


def iter_review_rows(path: Path) -> Iterator[Mapping[str, Any]]:
    with _open_text(path) as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ReviewExportError("review row is invalid JSON") from exc
            if not isinstance(value, Mapping):
                raise ReviewExportError("review row is not an object")
            yield value


def export_review_queue(
    input_path: Path,
    output_path: Path,
    *,
    max_rows: int = MAX_ROWS,
) -> int:
    if max_rows <= 0:
        raise ReviewExportError("max rows must be positive")
    written = 0
    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(HEADER)
        for row in iter_review_rows(input_path):
            if written >= max_rows:
                raise ReviewExportError("review queue exceeds row limit")
            candidates = ";".join(
                str(item.get("key"))
                for item in row.get("departmentCandidates") or ()
                if isinstance(item, Mapping) and item.get("key")
            )
            writer.writerow((
                row.get("providerProductId") or "",
                row.get("name") or "",
                row.get("brand") or "",
                row.get("suggestedDepartment") or "",
                row.get("suggestionSource") or "",
                candidates,
            ))
            written += 1
    return written


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--max-rows", type=int, default=MAX_ROWS)
    args = parser.parse_args(argv)
    count = export_review_queue(Path(args.input), Path(args.output), max_rows=args.max_rows)
    print(f"exported {count} review rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

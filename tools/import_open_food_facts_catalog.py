#!/usr/bin/env python3
"""Convert a locally supplied Open Food Facts export into catalog-only JSONL.

The importer is deliberately offline.  It accepts a caller-provided JSONL
export, keeps only product identity and display metadata, and never emits
price, promotion, quantity, availability, store, or freshness claims.  The
caller must still provide the source rights manifest and regional snapshot
configuration to ``build_offline_catalog_snapshot.py``; this tool does not
authorize a dataset or infer Canadian/store availability.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from tools.build_offline_catalog_snapshot import NAMESPACE_RE, _canonical_json, _text
from tools.open_facts_barcode import canonical_open_facts_gtin


MAX_RECORDS = 50_000
OUTPUT_FIELDS = {
    "record_id",
    "provider_id",
    "dataset_namespace_id",
    "provider_item_id",
    "gtin",
    "display_name",
    "brand",
    "aliases",
}


class OpenFoodFactsCatalogImportError(ValueError):
    """A local export cannot be converted without guessing or hiding facts."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise OpenFoodFactsCatalogImportError(message)


def _optional_text(value: Any, *, field: str, max_length: int) -> str | None:
    if value is None:
        return None
    _require(isinstance(value, str), f"{field} must be a string when present")
    value = " ".join(value.split())
    if not value:
        return None
    _require(len(value) <= max_length, f"{field} exceeds {max_length} characters")
    _require("\x00" not in value, f"{field} contains a NUL character")
    return value


def _display_name(product: dict[str, Any], line_number: int) -> str:
    for field in ("product_name_en", "product_name"):
        candidate = _optional_text(product.get(field), field=f"line {line_number}.{field}", max_length=240)
        if candidate is not None:
            return candidate
    raise OpenFoodFactsCatalogImportError(f"line {line_number} has no usable product name")


def _record(product: dict[str, Any], *, line_number: int, dataset_namespace_id: str) -> dict[str, Any]:
    _require(isinstance(product, dict), f"line {line_number} must be a JSON object")
    raw_code = product.get("code")
    _require(isinstance(raw_code, str), f"line {line_number}.code must be a string")
    code = raw_code.strip()
    canonical_code = canonical_open_facts_gtin(code)
    _require(canonical_code is not None, f"line {line_number} has an invalid GTIN")
    display_name = _display_name(product, line_number)
    brand = _optional_text(product.get("brands"), field=f"line {line_number}.brands", max_length=160)
    return {
        "record_id": f"off:{code}",
        "provider_id": "open-food-facts",
        "dataset_namespace_id": dataset_namespace_id,
        "provider_item_id": code,
        "gtin": code,
        "display_name": display_name,
        "brand": brand,
        "aliases": [],
    }


def import_catalog(
    input_path: Path,
    output_path: Path,
    *,
    dataset_namespace_id: str = "off-ca",
    max_records: int = MAX_RECORDS,
) -> dict[str, int | str]:
    """Import one local JSONL export deterministically and source-isolated."""

    _require(input_path.is_file(), f"Missing Open Food Facts export: {input_path}")
    _require(1 <= max_records <= MAX_RECORDS, f"max_records must be between 1 and {MAX_RECORDS}")
    dataset_namespace_id = _text(dataset_namespace_id, "dataset_namespace_id", max_length=96)
    _require(
        NAMESPACE_RE.fullmatch(dataset_namespace_id) is not None,
        "dataset_namespace_id has invalid form",
    )
    _require(output_path != input_path, "Input and output paths must be different")
    _require(not output_path.exists(), f"Refusing to overwrite output: {output_path}")

    records: list[dict[str, Any]] = []
    seen_codes: set[str] = set()
    canonical_to_raw: dict[str, str] = {}
    try:
        with input_path.open("r", encoding="utf-8", newline="") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    product = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise OpenFoodFactsCatalogImportError(
                        f"line {line_number} is not valid JSON"
                    ) from exc
                record = _record(
                    product,
                    line_number=line_number,
                    dataset_namespace_id=dataset_namespace_id,
                )
                code = record["gtin"]
                canonical_code = canonical_open_facts_gtin(code)
                assert canonical_code is not None
                _require(code not in seen_codes, f"duplicate Open Food Facts GTIN: {code}")
                previous = canonical_to_raw.get(canonical_code)
                _require(
                    previous is None,
                    f"ambiguous GTIN representations: {previous} and {code}",
                )
                seen_codes.add(code)
                canonical_to_raw[canonical_code] = code
                records.append(record)
                _require(
                    len(records) <= max_records,
                    f"Open Food Facts export exceeds {max_records} records",
                )
    except OSError as exc:
        raise OpenFoodFactsCatalogImportError(f"Unable to read export: {input_path}") from exc

    _require(bool(records), "Open Food Facts export contains no usable catalog records")
    records.sort(key=lambda item: item["record_id"])
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(b"".join(_canonical_json(record) for record in records))
    return {
        "records": len(records),
        "records_with_valid_gtin": len(records),
        "dataset_namespace_id": dataset_namespace_id,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--dataset-namespace-id", default="off-ca")
    parser.add_argument("--max-records", type=int, default=MAX_RECORDS)
    args = parser.parse_args(argv)
    try:
        result = import_catalog(
            args.input,
            args.output,
            dataset_namespace_id=args.dataset_namespace_id,
            max_records=args.max_records,
        )
    except (OpenFoodFactsCatalogImportError, OSError) as exc:
        print(f"Open Food Facts catalog import failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

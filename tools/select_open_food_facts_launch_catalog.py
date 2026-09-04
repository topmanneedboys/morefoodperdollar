#!/usr/bin/env python3
"""Select a bounded Canadian identity catalog from a local Open Food Facts export.

This tool is deliberately offline.  It reads a caller-supplied Open Food Facts
CSV/TSV (optionally gzip-compressed) or JSONL export, keeps only rows whose
source explicitly labels Canada, and emits the small identity-only JSONL input
accepted by :mod:`import_open_food_facts_catalog`.

The selection is a reproducible prioritization, not a claim about retailer
availability or demand.  Canadian popularity tags, scan count, grocery
category hints, and completeness are used only to choose which identities fit
the bounded launch window.  Prices, quantities, stock, stores, images and
freshness are never emitted.  Rows rejected for malformed identity/name are
counted in a deterministic report so a caller can inspect data quality rather
than silently treating omissions as coverage.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import re
import sys
import unicodedata
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable, TextIO

from tools.build_offline_catalog_snapshot import _canonical_json
from tools.open_facts_barcode import canonical_open_facts_gtin


DEFAULT_MAX_RECORDS = 3_000
MAX_RECORDS = 5_000
MAX_FIELD_SIZE = 16 * 1024 * 1024

# These are deliberately broad category hints.  They rank likely grocery
# products but never establish category authority or store availability.
GROCERY_HINTS = frozenset(
    {
        "bakery",
        "beverage",
        "bread",
        "breakfast",
        "cereal",
        "cheese",
        "chocolate",
        "coffee",
        "condiment",
        "confection",
        "dairy",
        "dessert",
        "drink",
        "egg",
        "fish",
        "frozen",
        "fruit",
        "grain",
        "juice",
        "legume",
        "meat",
        "milk",
        "nut",
        "oil",
        "pasta",
        "plant-based",
        "rice",
        "seafood",
        "snack",
        "sauce",
        "spice",
        "tea",
        "vegetable",
        "water",
        "yogurt",
    }
)
# Open Food Facts category labels vary by language and nesting.  These tokens
# cover common household/non-food branches without treating a category label
# as proof of retailer assortment or demand.
HOUSEHOLD_HINTS = frozenset(
    {
        "baby-care",
        "beauty",
        "cleaning",
        "cleaner",
        "cleaning-products",
        "cosmetics",
        "dental-care",
        "deodorant",
        "diaper",
        "diapers",
        "dishwashing",
        "dishwasher",
        "feminine-hygiene",
        "hair-care",
        "hand-wash",
        "health",
        "home-and-garden",
        "home-care",
        "household-products",
        "hygiene",
        "kitchen-supplies",
        "laundry",
        "laundry-care",
        "non-food-products",
        "oral-care",
        "paper-goods",
        "paper-products",
        "personal-care",
        "pet-products",
        "pet-care",
        "pet-food",
        "razor",
        "shampoo",
        "shaving",
        "soap",
        "sponge",
        "tissue",
        "toilet-paper",
        "toiletries",
        "toothpaste",
        "household",
    }
)
COUNTRY_CANADA = "en:canada"
_WHITESPACE = re.compile(r"\s+")
MIN_IDENTITY_NAME_VARIETY = 1_500
MAX_IDENTITY_NAME_VARIETY = 5_000
HOUSEHOLD_RESERVE_PERCENT = 10


class OpenFoodFactsLaunchSelectionError(ValueError):
    """A local export cannot be selected without making an unsafe assumption."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise OpenFoodFactsLaunchSelectionError(message)


def _clean(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    return _WHITESPACE.sub(" ", value).strip()


def _is_canada(row: dict[str, Any]) -> bool:
    tags = {part.strip().casefold() for part in _clean(row.get("countries_tags")).split(",")}
    if COUNTRY_CANADA in tags:
        return True
    countries = {part.strip().casefold() for part in _clean(row.get("countries_en")).split(",")}
    return "canada" in countries


def _name(row: dict[str, Any]) -> str:
    return _clean(row.get("product_name_en")) or _clean(row.get("product_name"))


def _has_search_tokens(value: str) -> bool:
    ascii_value = (
        unicodedata.normalize("NFKD", value)
        .encode("ascii", "ignore")
        .decode("ascii")
        .lower()
    )
    return bool(re.search(r"[a-z0-9]", ascii_value))


def _category_tokens(row: dict[str, Any]) -> set[str]:
    value = _clean(row.get("categories_en")) or _clean(row.get("main_category_en"))
    return {token for token in re.findall(r"[a-z0-9]+(?:-[a-z0-9]+)?", value.casefold())}


def _scan_count(row: dict[str, Any]) -> int:
    value = _clean(row.get("unique_scans_n"))
    if not value.isdigit():
        return 0
    return min(int(value), 2_147_483_647)


def _completeness(row: dict[str, Any]) -> Decimal:
    value = _clean(row.get("completeness"))
    try:
        parsed = Decimal(value)
    except (InvalidOperation, ValueError):
        return Decimal("0")
    return parsed if parsed.is_finite() and parsed >= 0 else Decimal("0")


def _canadian_popularity(row: dict[str, Any]) -> int:
    tags = {_clean(tag).casefold() for tag in _clean(row.get("popularity_tags")).split(",")}
    if any("country-ca-scans-" in tag or "country-canada-scans-" in tag for tag in tags):
        return 2
    if any(tag.startswith("top-") and "scans-" in tag for tag in tags):
        return 1
    return 0


def _grocery_hint(row: dict[str, Any]) -> int:
    return int(bool(_category_tokens(row) & GROCERY_HINTS))


def _household_hint(row: dict[str, Any]) -> int:
    return int(bool(_category_tokens(row) & HOUSEHOLD_HINTS))


def _canonical_identity_name(value: str) -> str:
    """Return a stable identity-name variety key for coverage measurement."""

    ascii_value = (
        unicodedata.normalize("NFKD", value)
        .encode("ascii", "ignore")
        .decode("ascii")
        .lower()
    )
    return " ".join(re.findall(r"[a-z0-9]+", ascii_value))


def _raw_record(row: dict[str, Any], *, code: str, name: str) -> dict[str, Any]:
    # Keep only fields accepted by the identity-only importer.  In particular,
    # no quantity, price, country, store, image, promotion, stock or timestamp
    # field crosses this boundary.
    brand = _clean(row.get("brands"))
    return {
        "code": code,
        "product_name_en": name,
        "brands": brand if _has_search_tokens(brand) else None,
    }


def _open_rows(path: Path) -> tuple[TextIO, bool]:
    _require(path.is_file(), f"Missing Open Food Facts export: {path}")
    if path.name.casefold().endswith((".gz", ".gzip")):
        return gzip.open(path, "rt", encoding="utf-8", errors="replace", newline=""), True
    return path.open("r", encoding="utf-8", errors="replace", newline=""), True


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _iter_rows(path: Path) -> Iterable[dict[str, Any]]:
    handle, _ = _open_rows(path)
    try:
        if path.name.casefold().endswith((".jsonl", ".jsonl.gz")):
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    value = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise OpenFoodFactsLaunchSelectionError(
                        f"export line {line_number} is not valid JSON"
                    ) from exc
                _require(isinstance(value, dict), f"export line {line_number} must be an object")
                yield value
            return

        csv.field_size_limit(MAX_FIELD_SIZE)
        reader = csv.DictReader(handle, delimiter="\t")
        _require(reader.fieldnames is not None, "Open Food Facts tabular export has no header")
        _require("code" in reader.fieldnames, "Open Food Facts export is missing code column")
        for row in reader:
            yield dict(row)
    except csv.Error as exc:
        raise OpenFoodFactsLaunchSelectionError("Open Food Facts export has malformed tabular data") from exc
    finally:
        handle.close()


def select_catalog(
    input_path: Path,
    output_path: Path,
    report_path: Path,
    *,
    max_records: int = DEFAULT_MAX_RECORDS,
) -> dict[str, Any]:
    """Select and write a bounded identity-only catalog plus an audit report."""

    _require(1 <= max_records <= MAX_RECORDS, f"max_records must be between 1 and {MAX_RECORDS}")
    _require(output_path != input_path and report_path != input_path, "Outputs must differ from input")
    _require(output_path != report_path, "Catalog and report outputs must differ")
    _require(not output_path.exists(), f"Refusing to overwrite output: {output_path}")
    _require(not report_path.exists(), f"Refusing to overwrite report: {report_path}")

    stats: Counter[str] = Counter()
    candidates: list[tuple[tuple[Any, ...], str, dict[str, Any], int, int, str]] = []
    canonical_codes: dict[str, str] = {}

    for row in _iter_rows(input_path):
        stats["rows_seen"] += 1
        if not _is_canada(row):
            stats["rows_outside_canada"] += 1
            continue
        stats["rows_canada_labelled"] += 1
        code = _clean(row.get("code"))
        canonical = canonical_open_facts_gtin(code)
        if canonical is None:
            stats["rows_invalid_gtin"] += 1
            continue
        name = _name(row)
        if not name:
            stats["rows_missing_name"] += 1
            continue
        if not _has_search_tokens(name):
            stats["rows_unsearchable_name"] += 1
            continue
        previous = canonical_codes.get(canonical)
        if previous is not None:
            stats["rows_duplicate_canonical_gtin"] += 1
            continue
        canonical_codes[canonical] = code
        stats["rows_usable_identity"] += 1
        popularity = _canadian_popularity(row)
        grocery = _grocery_hint(row)
        household = _household_hint(row)
        scans = _scan_count(row)
        completeness = _completeness(row)
        relevance = max(grocery, household)
        # Sort descending on quality/relevance, then ascending on canonical
        # identity.  Decimal is used instead of float for reproducibility.
        score = (popularity, relevance, scans, completeness, canonical)
        candidates.append(
            (
                score,
                canonical,
                _raw_record(row, code=code, name=name),
                grocery,
                household,
                _canonical_identity_name(name),
            )
        )

    candidates.sort(key=lambda item: item[0], reverse=True)
    selected_candidates = list(candidates[:max_records])
    household_candidates = [item for item in candidates if item[4] == 1]
    household_reserve_target = min(
        len(household_candidates),
        (max_records * HOUSEHOLD_RESERVE_PERCENT) // 100,
    )
    # Keep a small deterministic household reserve when the bounded launch
    # window is large enough.  This prevents high-scan food rows from crowding
    # out all household identities while preserving the normal quality order
    # for the remaining slots.
    selected_keys = {item[1] for item in selected_candidates}
    selected_household = sum(item[4] for item in selected_candidates)
    if selected_household < household_reserve_target:
        replacement_indexes = [
            index for index, item in enumerate(selected_candidates) if item[4] == 0
        ]
        for item in household_candidates:
            if selected_household >= household_reserve_target:
                break
            if item[1] in selected_keys or not replacement_indexes:
                continue
            replacement_index = replacement_indexes.pop()
            selected_keys.remove(selected_candidates[replacement_index][1])
            selected_candidates[replacement_index] = item
            selected_keys.add(item[1])
            selected_household += 1
        selected_candidates.sort(key=lambda item: item[0], reverse=True)

    selected = [item[2] for item in selected_candidates]
    stats["records_selected"] = len(selected)
    stats["records_omitted_by_bound"] = max(0, len(candidates) - len(selected))
    _require(selected, "No usable Canada-labelled Open Food Facts identities found")

    selected_grocery_hints = sum(item[3] for item in selected_candidates)
    selected_household_hints = sum(item[4] for item in selected_candidates)
    unique_identity_names = len({item[5] for item in selected_candidates if item[5]})

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(b"".join(_canonical_json(record) for record in selected))
    source_hash = _sha256_file(input_path)
    output_hash = hashlib.sha256(output_path.read_bytes()).hexdigest()
    report = {
        "schemaVersion": 1,
        "source": {
            "providerId": "open-food-facts",
            "datasetNamespaceId": "off-ca",
            "inputFileSha256": source_hash,
        },
        "selection": {
            "scope": "CANADA_LABELLED_IDENTITY_ONLY",
            "maxRecords": max_records,
            "ordering": "canadian_popularity,grocery_or_household_hint,unique_scans,completeness,canonical_gtin_desc",
            "householdReservePercent": HOUSEHOLD_RESERVE_PERCENT,
            "doesNotEstablish": ["retailer_availability", "store_stock", "current_price", "package_quantity", "freshness"],
        },
        "coverage": {
            "records": len(selected),
            "selectedGroceryHintRecords": selected_grocery_hints,
            "selectedHouseholdHintRecords": selected_household_hints,
            "householdReserveTarget": household_reserve_target,
            "householdReserveSatisfied": selected_household_hints >= household_reserve_target,
            "uniqueCanonicalIdentityNames": unique_identity_names,
            "identityNameVarietyStatus": (
                "WITHIN_TARGET"
                if MIN_IDENTITY_NAME_VARIETY <= unique_identity_names <= MAX_IDENTITY_NAME_VARIETY
                else "OUTSIDE_TARGET"
            ),
            "identityNameVarietyTarget": {
                "minimum": MIN_IDENTITY_NAME_VARIETY,
                "maximum": MAX_IDENTITY_NAME_VARIETY,
            },
            "note": (
                "Canonical identity-name variety and category hints are bounded selection measurements, "
                "not demand, retailer availability, stock, price, package quantity, freshness, or ranking authority."
            ),
        },
        "metrics": dict(sorted(stats.items())),
        "output": {
            "records": len(selected),
            "sha256": output_hash,
        },
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_bytes(_canonical_json(report))
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--max-records", type=int, default=DEFAULT_MAX_RECORDS)
    args = parser.parse_args(argv)
    try:
        report = select_catalog(args.input, args.output, args.report, max_records=args.max_records)
    except (OpenFoodFactsLaunchSelectionError, OSError) as exc:
        print(f"Open Food Facts launch selection failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

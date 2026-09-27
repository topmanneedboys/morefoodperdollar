#!/usr/bin/env python3
"""Audit exact product-picture coverage without importing images.

This is an offline qualification tool.  It joins a provider-neutral identity
catalog to a locally supplied Open Food Facts tabular export by a validated
GTIN, then requires strict display-name, brand, and explicit package-quantity
agreement before counting a picture candidate.  It records rights evidence
separately: the current bulk export has no per-image licence column, so those
URLs are audit candidates only and are not Android display inputs.

The Open Food Facts export is streamed.  Only the bounded catalog and sets of
matched catalog keys are retained in memory; product images and URLs are never
downloaded, copied, or emitted.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import html
import json
import re
import sys
import tempfile
import unicodedata
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable, TextIO
from urllib.parse import urlparse

from tools.open_facts_barcode import canonical_open_facts_gtin


SCHEMA_VERSION = "valuepilot-product-image-coverage-v1"
POLICY_VERSION = "exact-gtin-name-brand-quantity-off-rights-v1"
MAX_CATALOG_RECORDS = 100_000
MAX_FIELD_SIZE = 16 * 1024 * 1024
OFF_IMAGE_HOST = "images.openfoodfacts.org"
_TOKEN_RE = re.compile(r"[a-z0-9]+")
_QUANTITY_RE = re.compile(
    r"^\s*(?P<value>[0-9]+(?:[.,][0-9]+)?)\s*"
    r"(?P<unit>kg|kilograms?|g|gr|grams?|gramos?|mg|milligrams?|"
    r"l|liters?|litres?|ml|milliliters?|millilitres?|cc|cm3|"
    r"units?|unit|u|un|pcs?|pieces?|count|ct|bottles?|bags?)\s*$",
    re.IGNORECASE,
)


class ProductImageAuditError(ValueError):
    """The supplied identity or image export cannot be audited safely."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ProductImageAuditError(message)


def _clean(value: Any) -> str:
    return " ".join(value.split()).strip() if isinstance(value, str) else ""


def _tokens(value: Any) -> tuple[str, ...]:
    text = html.unescape(_clean(value)).casefold()
    text = unicodedata.normalize("NFKD", text)
    text = "".join(char for char in text if not unicodedata.combining(char))
    return tuple(_TOKEN_RE.findall(text))


def _canonical_decimal(value: str) -> Decimal | None:
    normalized = value.strip().replace(",", ".")
    try:
        parsed = Decimal(normalized)
    except (InvalidOperation, ValueError):
        return None
    return parsed if parsed.is_finite() and parsed > 0 else None


def _quantity_key(value: Any, unit: Any) -> tuple[str, Decimal] | None:
    parsed = _canonical_decimal(str(value)) if value is not None else None
    if parsed is None or not isinstance(unit, str):
        return None
    normalized = unit.strip().upper()
    if normalized in {"GRAM", "GRAMS", "GRM", "G"}:
        return ("GRAM", parsed)
    if normalized in {"KILOGRAM", "KILOGRAMS", "KG"}:
        return ("GRAM", parsed * Decimal("1000"))
    if normalized in {"MILLIGRAM", "MILLIGRAMS", "MG"}:
        return ("GRAM", parsed / Decimal("1000"))
    if normalized in {"MILLILITRE", "MILLILITRES", "MILLILITER", "MILLILITERS", "ML", "CC", "CM3"}:
        return ("MILLILITRE", parsed)
    if normalized in {"LITRE", "LITRES", "LITER", "LITERS", "L"}:
        return ("MILLILITRE", parsed * Decimal("1000"))
    if normalized in {"COUNT", "COUNTS", "UNIT", "UNITS", "U", "UN", "PCS", "PC", "PIECE", "PIECES", "CT"}:
        return ("COUNT", parsed)
    return None


def _catalog_quantity(record: dict[str, Any]) -> tuple[str, Decimal] | None:
    quantity = record.get("quantity")
    if not isinstance(quantity, dict):
        return None
    return _quantity_key(quantity.get("value"), quantity.get("unit"))


def _off_quantity(value: Any) -> tuple[str, Decimal] | None:
    text = _clean(value)
    if not text or re.search(r"\bx\b|\*", text, re.IGNORECASE):
        return None
    match = _QUANTITY_RE.fullmatch(text)
    if match is None:
        return None
    return _quantity_key(match.group("value"), match.group("unit"))


def _brand_matches(catalog_brand: Any, off_brands: Any) -> bool:
    catalog = _tokens(catalog_brand)
    if not catalog or catalog == ("s", "d"):
        return True
    raw_brands = _clean(off_brands)
    if not raw_brands:
        return False
    return any(_tokens(part) == catalog for part in re.split(r"[,;|]", raw_brands))


def _valid_image_url(value: Any) -> bool:
    raw = _clean(value)
    if not raw:
        return False
    parsed = urlparse(raw)
    return (
        parsed.scheme == "https"
        and parsed.hostname == OFF_IMAGE_HOST
        and bool(parsed.path)
        and "/invalid/" not in parsed.path
    )


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_catalog(path: Path) -> tuple[dict[str, list[tuple[str, dict[str, Any]]]], dict[str, Any]]:
    _require(path.is_file(), f"Missing catalog: {path}")
    try:
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        raise ProductImageAuditError(f"Unable to parse catalog: {path}") from exc
    _require(isinstance(payload, dict), "catalog root must be an object")
    records = payload.get("records")
    _require(isinstance(records, list), "catalog must contain a records array")
    _require(len(records) <= MAX_CATALOG_RECORDS, f"catalog exceeds {MAX_CATALOG_RECORDS} records")
    by_gtin: dict[str, list[tuple[str, dict[str, Any]]]] = {}
    counters: Counter[str] = Counter()
    yerba_keys: set[str] = set()
    for index, record in enumerate(records, start=1):
        _require(isinstance(record, dict), f"catalog record {index} must be an object")
        canonical = canonical_open_facts_gtin(_clean(record.get("gtin")))
        if canonical is None:
            counters["invalid_gtin"] += 1
            continue
        name = _clean(record.get("name"))
        _require(bool(name), f"catalog record {index} has no name")
        identity_key = _clean(record.get("productEvidenceKey")) or f"catalog-record:{index}"
        existing_keys = {key for key, _ in by_gtin.get(canonical, [])}
        if identity_key in existing_keys:
            identity_key = f"{identity_key}#{index}"
        by_gtin.setdefault(canonical, []).append((identity_key, record))
        if "yerba" in _tokens(" ".join((name, _clean(record.get("brand"))))):
            yerba_keys.add(identity_key)
    counters["records_seen"] = len(records)
    counters["valid_gtin_records"] = sum(len(value) for value in by_gtin.values())
    counters["unique_gtin_values"] = len(by_gtin)
    counters["duplicate_gtin_records"] = counters["valid_gtin_records"] - counters["unique_gtin_values"]
    counters["yerba_records"] = len(yerba_keys)
    return by_gtin, {"catalog_counters": dict(sorted(counters.items())), "yerba_keys": yerba_keys}


def _open_off(path: Path) -> TextIO:
    _require(path.is_file(), f"Missing Open Food Facts export: {path}")
    if path.name.casefold().endswith((".gz", ".gzip")):
        return gzip.open(path, "rt", encoding="utf-8", errors="replace", newline="")
    return path.open("r", encoding="utf-8", errors="replace", newline="")


def _iter_off_rows(path: Path) -> Iterable[dict[str, Any]]:
    handle = _open_off(path)
    try:
        csv.field_size_limit(MAX_FIELD_SIZE)
        reader = csv.DictReader(handle, delimiter="\t")
        _require(reader.fieldnames is not None, "Open Food Facts export has no header")
        _require("code" in reader.fieldnames, "Open Food Facts export is missing code column")
        for row in reader:
            yield row
    except csv.Error as exc:
        raise ProductImageAuditError("Open Food Facts export has malformed tabular data") from exc
    finally:
        handle.close()


def _strict_match(record: dict[str, Any], row: dict[str, Any]) -> str | None:
    if _tokens(record.get("name")) != _tokens(row.get("product_name_en") or row.get("product_name")):
        return "name_mismatch"
    if not _brand_matches(record.get("brand"), row.get("brands")):
        return "brand_mismatch"
    catalog_quantity = _catalog_quantity(record)
    if catalog_quantity is None:
        return "catalog_quantity_unknown"
    off_quantity = _off_quantity(row.get("quantity"))
    if off_quantity is None:
        return "off_quantity_unknown_or_ambiguous"
    if catalog_quantity != off_quantity:
        return "quantity_mismatch"
    return None


def audit_images(catalog_path: Path, off_path: Path, output_path: Path) -> dict[str, Any]:
    _require(output_path != catalog_path and output_path != off_path, "output must differ from inputs")
    _require(not output_path.exists(), f"Refusing to overwrite output: {output_path}")
    catalog, catalog_meta = _load_catalog(catalog_path)
    yerba_keys: set[str] = catalog_meta.pop("yerba_keys")
    counters: Counter[str] = Counter()
    matched_keys: set[str] = set()
    strict_keys: set[str] = set()
    image_keys: set[str] = set()
    rights_keys: set[str] = set()
    yerba_strict: set[str] = set()
    yerba_image: set[str] = set()
    rejection_reasons: Counter[str] = Counter()
    for row in _iter_off_rows(off_path):
        counters["off_rows_seen"] += 1
        canonical = canonical_open_facts_gtin(_clean(row.get("code")))
        if canonical is None:
            counters["off_rows_invalid_gtin"] += 1
            continue
        identities = catalog.get(canonical)
        if identities is None:
            continue
        counters["off_rows_matching_catalog_gtin"] += 1
        for identity_key, record in identities:
            counters["catalog_identity_matches_evaluated"] += 1
            matched_keys.add(identity_key)
            mismatch = _strict_match(record, row)
            if mismatch is not None:
                rejection_reasons[mismatch] += 1
                continue
            strict_keys.add(identity_key)
            if identity_key in yerba_keys:
                yerba_strict.add(identity_key)
            image_value = row.get("image_front_url") or row.get("image_url")
            if not _valid_image_url(image_value):
                counters["strict_rows_without_valid_off_image"] += 1
                continue
            image_keys.add(identity_key)
            counters["strict_rows_with_off_image_candidate"] += 1
            license_value = row.get("image_front_licenses") or row.get("image_licenses") or row.get("image_license")
            if "cc by-sa" in _clean(license_value).casefold():
                rights_keys.add(identity_key)
            else:
                counters["image_candidates_without_per_image_license"] += 1
            if identity_key in yerba_keys:
                yerba_image.add(identity_key)

    catalog_count = sum(len(identities) for identities in catalog.values())
    strict_count = len(strict_keys)
    image_count = len(image_keys)
    rights_count = len(rights_keys)
    yerba_count = len(yerba_keys)
    report: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "catalog": {
            **catalog_meta["catalog_counters"],
            "sha256": _sha256(catalog_path),
        },
        "openFoodFactsExport": {
            "sha256": _sha256(off_path),
            **dict(sorted(counters.items())),
        },
        "coverage": {
            "catalogValidGtinRecords": catalog_count,
            "catalogUniqueGtins": len(catalog),
            "catalogIdentitiesWithExactGtinMatch": len(matched_keys),
            "strictExactIdentities": strict_count,
            "strictExactImageCandidates": image_count,
            "strictExactImageCandidatePercentOfValidGtinCatalog": round((image_count * 100 / catalog_count), 6) if catalog_count else 0,
            "strictExactImageCandidatesWithPerImageLicense": rights_count,
            "displayEligibleImages": 0,
            "displayEligibility": "BLOCKED_PER_IMAGE_RIGHTS_EVIDENCE_MISSING",
            "yerbaCatalogIdentities": yerba_count,
            "yerbaStrictExactIdentities": len(yerba_strict),
            "yerbaStrictExactImageCandidates": len(yerba_image),
            "yerbaImageCandidatePercentOfYerbaCatalog": round((len(yerba_image) * 100 / yerba_count), 6) if yerba_count else 0,
        },
        "rejections": dict(sorted(rejection_reasons.items())),
        "sourcePolicy": {
            "provider": "open-food-facts",
            "imageHost": OFF_IMAGE_HOST,
            "datasetLicense": "CC BY-SA (source documentation); per-image/third-party rights still require review",
            "imagesDownloaded": False,
            "imagesCommitted": False,
            "priceOrAvailabilityClaim": False,
        },
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=output_path.parent, prefix=f".{output_path.name}.", suffix=".tmp", delete=False) as handle:
        temp_path = Path(handle.name)
        json.dump(report, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")
    temp_path.replace(output_path)
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--open-food-facts", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        report = audit_images(args.catalog, args.open_food_facts, args.output)
    except (ProductImageAuditError, OSError) as exc:
        print(f"Product image audit failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

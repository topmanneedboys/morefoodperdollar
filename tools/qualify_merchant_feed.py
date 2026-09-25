#!/usr/bin/env python3
"""Offline qualification harness for authorized merchant product feeds.

This tool is deliberately conservative:
- it performs no network access;
- it does not decide licensing or production authorization;
- it never guesses ambiguous field mappings;
- XML requires an explicit record tag;
- it streams rows and bounds work with --max-rows;
- current-offer and unit-value candidate counts are structural/data-quality
  measurements only, not permission to expose or rank a provider in ValuePilot.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import re
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable, Iterator, Mapping, TextIO
from urllib.parse import urlparse

CANONICAL_FIELDS = {
    "provider_item_id",
    "sku",
    "gtin",
    "title",
    "brand",
    "currency",
    "current_price",
    "previous_price",
    "quantity_value",
    "quantity_unit",
    "package_text",
    "availability",
    "observed_at",
    "product_url",
    "image_url",
    "country",
    "store_id",
    "commerce_channel",
}

FIELD_ALIASES = {
    "provider_item_id": {"provider_item_id", "provideritemid", "item_id", "itemid", "product_id", "productid"},
    "sku": {"sku", "merchant_sku", "merchantsku"},
    "gtin": {"gtin", "gtin13", "gtin14", "ean", "ean13", "upc", "upca", "barcode"},
    "title": {"title", "product_title", "producttitle", "product_name", "productname", "name"},
    "brand": {"brand", "manufacturer", "maker"},
    "currency": {"currency", "currency_code", "currencycode", "price_currency", "pricecurrency"},
    "current_price": {"current_price", "currentprice", "price", "retail_price", "retailprice", "offer_price", "offerprice"},
    "previous_price": {"previous_price", "previousprice", "regular_price", "regularprice", "list_price", "listprice", "msrp"},
    "quantity_value": {"quantity_value", "quantityvalue", "package_quantity", "packagequantity", "net_quantity", "netquantity"},
    "quantity_unit": {"quantity_unit", "quantityunit", "package_unit", "packageunit", "unit_of_measure", "unitofmeasure", "uom"},
    "package_text": {"package_text", "packagetext", "package_size", "packagesize", "size", "size_text", "sizetext"},
    "availability": {"availability", "stock_status", "stockstatus", "inventory_status", "inventorystatus"},
    "observed_at": {"observed_at", "observedat", "updated_at", "updatedat", "last_updated", "lastupdated", "timestamp"},
    "product_url": {"product_url", "producturl", "url", "link", "deep_link", "deeplink"},
    "image_url": {"image_url", "imageurl", "image", "image_link", "imagelink"},
    "country": {"country", "country_code", "countrycode", "market", "market_code", "marketcode"},
    "store_id": {"store_id", "storeid", "location_id", "locationid", "merchant_store_id", "merchantstoreid"},
    "commerce_channel": {"commerce_channel", "commercechannel", "channel", "sales_channel", "saleschannel"},
}

COUNTRY_CA_VALUES = {"CA", "CAN", "CANADA"}
IN_STOCK_VALUES = {"IN_STOCK", "INSTOCK", "IN STOCK", "AVAILABLE", "YES", "TRUE", "1"}
OUT_OF_STOCK_VALUES = {"OUT_OF_STOCK", "OUTOFSTOCK", "OUT OF STOCK", "UNAVAILABLE", "NO", "FALSE", "0"}
LOW_STOCK_VALUES = {"LOW_STOCK", "LOWSTOCK", "LOW STOCK", "LIMITED"}
QUANTITY_UNITS = {
    "G": ("GRAM", Decimal("1")),
    "GRAM": ("GRAM", Decimal("1")),
    "GRAMS": ("GRAM", Decimal("1")),
    "KG": ("GRAM", Decimal("1000")),
    "KILOGRAM": ("GRAM", Decimal("1000")),
    "KILOGRAMS": ("GRAM", Decimal("1000")),
    "ML": ("MILLILITRE", Decimal("1")),
    "MILLILITER": ("MILLILITRE", Decimal("1")),
    "MILLILITERS": ("MILLILITRE", Decimal("1")),
    "MILLILITRE": ("MILLILITRE", Decimal("1")),
    "MILLILITRES": ("MILLILITRE", Decimal("1")),
    "L": ("MILLILITRE", Decimal("1000")),
    "LITER": ("MILLILITRE", Decimal("1000")),
    "LITERS": ("MILLILITRE", Decimal("1000")),
    "LITRE": ("MILLILITRE", Decimal("1000")),
    "LITRES": ("MILLILITRE", Decimal("1000")),
    "COUNT": ("COUNT", Decimal("1")),
    "CT": ("COUNT", Decimal("1")),
    "EA": ("COUNT", Decimal("1")),
    "EACH": ("COUNT", Decimal("1")),
    "ITEM": ("COUNT", Decimal("1")),
    "ITEMS": ("COUNT", Decimal("1")),
    "UNIT": ("COUNT", Decimal("1")),
    "UNITS": ("COUNT", Decimal("1")),
}


def _normalize_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.strip().lower())


def _clean(value: object | None) -> str:
    if value is None:
        return ""
    return str(value).replace("\u00a0", " ").strip()


def is_valid_gtin(value: str | None) -> bool:
    value = _clean(value)
    if len(value) not in {8, 12, 13, 14} or not value.isdigit():
        return False
    supplied = int(value[-1])
    total = 0
    weight = 3
    for char in reversed(value[:-1]):
        total += int(char) * weight
        weight = 1 if weight == 3 else 3
    return supplied == (10 - total % 10) % 10


def parse_positive_decimal(
    value: str | None,
    decimal_separator: str = ".",
) -> Decimal | None:
    """Parse a positive decimal without guessing decimal/grouping semantics.

    The caller must choose `.` or `,` as the decimal separator. When `.` is
    selected, comma is accepted only as a valid thousands-group separator.
    When `,` is selected, dot is accepted only as a valid thousands-group
    separator. This prevents a value such as `9,99` from silently becoming
    `999` under dot-decimal semantics.
    """
    text = _clean(value)
    if not text or decimal_separator not in {".", ","}:
        return None

    if decimal_separator == ".":
        pattern = r"(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?"
        if not re.fullmatch(pattern, text):
            return None
        normalized = text.replace(",", "")
    else:
        pattern = r"(?:\d{1,3}(?:\.\d{3})+|\d+)(?:,\d+)?"
        if not re.fullmatch(pattern, text):
            return None
        normalized = text.replace(".", "").replace(",", ".")

    try:
        parsed = Decimal(normalized)
    except InvalidOperation:
        return None
    if not parsed.is_finite() or parsed <= 0:
        return None
    return parsed


def parse_quantity(value: str | None, unit: str | None) -> tuple[str, Decimal] | None:
    amount = parse_positive_decimal(value)
    unit_key = _clean(unit).upper().replace(".", "")
    conversion = QUANTITY_UNITS.get(unit_key)
    if amount is None or conversion is None:
        return None
    canonical_unit, multiplier = conversion
    return canonical_unit, amount * multiplier


def parse_timestamp(value: str | None) -> datetime | None:
    text = _clean(value)
    if not text:
        return None
    normalized = text[:-1] + "+00:00" if text.endswith("Z") else text
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def valid_http_url(value: str | None) -> bool:
    text = _clean(value)
    if not text:
        return False
    parsed = urlparse(text)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


def conservative_mapping(headers: Iterable[str]) -> tuple[dict[str, str], dict[str, list[str]]]:
    normalized_to_headers: dict[str, list[str]] = defaultdict(list)
    for header in headers:
        normalized_to_headers[_normalize_name(header)].append(header)

    mapping: dict[str, str] = {}
    ambiguous: dict[str, list[str]] = {}
    for canonical, aliases in FIELD_ALIASES.items():
        alias_norms = {_normalize_name(alias) for alias in aliases}
        matches = sorted(
            {
                header
                for normalized, originals in normalized_to_headers.items()
                if normalized in alias_norms
                for header in originals
            }
        )
        if len(matches) == 1:
            mapping[canonical] = matches[0]
        elif len(matches) > 1:
            ambiguous[canonical] = matches
    return mapping, ambiguous


def load_mapping(path: Path | None) -> dict[str, str]:
    if path is None:
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    raw = payload.get("fields", payload)
    if not isinstance(raw, dict):
        raise ValueError("Mapping JSON must be an object or contain a 'fields' object")
    mapping: dict[str, str] = {}
    used_sources: set[str] = set()
    for canonical, source in raw.items():
        if canonical not in CANONICAL_FIELDS:
            raise ValueError(f"Unknown canonical field in mapping: {canonical}")
        if not isinstance(source, str) or not source.strip():
            raise ValueError(f"Mapping for {canonical} must be a non-empty string")
        if source in used_sources:
            raise ValueError(f"Source field mapped more than once: {source}")
        used_sources.add(source)
        mapping[canonical] = source
    return mapping


@dataclass(frozen=True)
class QualificationConfig:
    input_path: Path
    mapping_path: Path | None = None
    xml_record_tag: str | None = None
    encoding: str = "utf-8-sig"
    expected_currency: str = "CAD"
    expected_country: str | None = "CA"
    evaluated_at: datetime | None = None
    max_rows: int = 500_000

    def __post_init__(self) -> None:
        if self.max_rows <= 0:
            raise ValueError("max_rows must be positive")
        if not re.fullmatch(r"[A-Z]{3}", self.expected_currency):
            raise ValueError("expected_currency must be a 3-letter uppercase code")


def _is_gzip(path: Path) -> bool:
    if path.suffix.lower() == ".gz":
        return True
    with path.open("rb") as handle:
        return handle.read(2) == b"\x1f\x8b"


def _open_text(config: QualificationConfig) -> TextIO:
    if _is_gzip(config.input_path):
        return gzip.open(config.input_path, "rt", encoding=config.encoding, newline="")
    return config.input_path.open("r", encoding=config.encoding, newline="")


def _effective_suffix(path: Path) -> str:
    suffixes = [item.lower() for item in path.suffixes]
    if suffixes and suffixes[-1] == ".gz":
        suffixes = suffixes[:-1]
    return suffixes[-1] if suffixes else ""


def _detect_delimiter(config: QualificationConfig) -> str:
    with _open_text(config) as handle:
        sample = handle.read(64 * 1024)
    if not sample:
        raise ValueError("Feed is empty")
    try:
        return csv.Sniffer().sniff(sample, delimiters=",\t|;").delimiter
    except csv.Error:
        suffix = _effective_suffix(config.input_path)
        if suffix in {".tsv", ".tab"}:
            return "\t"
        return ","


def _iter_delimited(config: QualificationConfig) -> tuple[list[str], Iterator[dict[str, str]]]:
    delimiter = _detect_delimiter(config)
    handle = _open_text(config)
    reader = csv.DictReader(handle, delimiter=delimiter)
    if not reader.fieldnames:
        handle.close()
        raise ValueError("Delimited feed has no header row")
    headers = [str(item) for item in reader.fieldnames]

    def iterator() -> Iterator[dict[str, str]]:
        try:
            for row in reader:
                yield {str(k): _clean(v) for k, v in row.items() if k is not None}
        finally:
            handle.close()

    return headers, iterator()


def _strip_namespace(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _iter_xml(config: QualificationConfig) -> tuple[list[str], Iterator[dict[str, str]]]:
    if not config.xml_record_tag:
        raise ValueError("XML feeds require --xml-record-tag; ValuePilot does not guess record elements")

    target = config.xml_record_tag
    handle = _open_text(config)
    events = ET.iterparse(handle, events=("end",))
    buffered: list[dict[str, str]] = []
    headers: set[str] = set()

    for _, element in events:
        if _strip_namespace(element.tag) != target:
            continue
        row = {
            _strip_namespace(child.tag): _clean(child.text)
            for child in list(element)
            if len(child) == 0
        }
        buffered.append(row)
        headers.update(row.keys())
        element.clear()
        break

    if not buffered:
        handle.close()
        raise ValueError(f"XML record tag not found: {target}")

    def iterator() -> Iterator[dict[str, str]]:
        try:
            yield from buffered
            for _, element in events:
                if _strip_namespace(element.tag) != target:
                    continue
                yield {
                    _strip_namespace(child.tag): _clean(child.text)
                    for child in list(element)
                    if len(child) == 0
                }
                element.clear()
        finally:
            handle.close()

    return sorted(headers), iterator()


def _iter_rows(config: QualificationConfig) -> tuple[str, list[str], Iterator[dict[str, str]]]:
    suffix = _effective_suffix(config.input_path)
    if suffix == ".xml":
        headers, iterator = _iter_xml(config)
        return "xml", headers, iterator
    if suffix in {".csv", ".tsv", ".tab", ".txt", ""}:
        headers, iterator = _iter_delimited(config)
        return "delimited", headers, iterator
    raise ValueError(f"Unsupported feed format: {suffix or '<none>'}")


def _mapped(row: Mapping[str, str], mapping: Mapping[str, str], canonical: str) -> str:
    source = mapping.get(canonical)
    return _clean(row.get(source)) if source else ""


def _availability_class(value: str) -> str:
    normalized = _clean(value).upper().replace("-", "_")
    if not normalized:
        return "missing"
    if normalized in IN_STOCK_VALUES:
        return "in_stock"
    if normalized in OUT_OF_STOCK_VALUES:
        return "out_of_stock"
    if normalized in LOW_STOCK_VALUES:
        return "low_stock"
    return "unrecognized"


def _identity_key(row: Mapping[str, str], mapping: Mapping[str, str]) -> tuple[str, str] | None:
    gtin = _mapped(row, mapping, "gtin")
    if is_valid_gtin(gtin):
        return "gtin", gtin
    for canonical in ("provider_item_id", "sku"):
        value = _mapped(row, mapping, canonical)
        if value:
            return canonical, value
    return None


def _signature(row: Mapping[str, str], mapping: Mapping[str, str]) -> tuple[str, ...]:
    return (
        _mapped(row, mapping, "current_price"),
        _mapped(row, mapping, "currency").upper(),
        _mapped(row, mapping, "quantity_value"),
        _mapped(row, mapping, "quantity_unit").upper(),
        _mapped(row, mapping, "availability").upper(),
    )


def qualify_feed(config: QualificationConfig) -> dict[str, object]:
    if not config.input_path.is_file() or config.input_path.stat().st_size == 0:
        raise ValueError(f"Missing or empty feed: {config.input_path}")

    feed_format, headers, rows = _iter_rows(config)
    explicit = load_mapping(config.mapping_path)
    if explicit:
        mapping = explicit
        ambiguous: dict[str, list[str]] = {}
        mapping_mode = "explicit"
    else:
        mapping, ambiguous = conservative_mapping(headers)
        mapping_mode = "auto_conservative"

    missing_sources = sorted({source for source in mapping.values() if source not in headers})
    if missing_sources:
        raise ValueError(f"Mapped source fields not found in feed headers: {missing_sources}")

    required_capabilities = {
        "identity": any(name in mapping for name in ("gtin", "provider_item_id", "sku")),
        "title": "title" in mapping,
        "currency": "currency" in mapping,
        "current_price": "current_price" in mapping,
    }

    counts: Counter[str] = Counter()
    currencies: Counter[str] = Counter()
    countries: Counter[str] = Counter()
    availability: Counter[str] = Counter()
    identity_signatures: dict[tuple[str, str, str, str], set[tuple[str, ...]]] = defaultdict(set)
    identity_rows: Counter[tuple[str, str, str, str]] = Counter()
    timestamp_min: datetime | None = None
    timestamp_max: datetime | None = None
    age_buckets: Counter[str] = Counter()
    truncated = False

    for index, row in enumerate(rows, start=1):
        if index > config.max_rows:
            truncated = True
            break
        counts["rows_scanned"] += 1

        title = _mapped(row, mapping, "title")
        if title:
            counts["title_present"] += 1
        else:
            counts["title_missing"] += 1

        gtin = _mapped(row, mapping, "gtin")
        if gtin:
            counts["gtin_present"] += 1
            if is_valid_gtin(gtin):
                counts["gtin_valid"] += 1
            else:
                counts["gtin_invalid"] += 1

        identity = _identity_key(row, mapping)
        if identity is None:
            counts["identity_missing"] += 1
        else:
            counts["identity_present"] += 1

        currency = _mapped(row, mapping, "currency").upper()
        if currency:
            currencies[currency] += 1
            if re.fullmatch(r"[A-Z]{3}", currency):
                counts["currency_valid_format"] += 1
            else:
                counts["currency_invalid_format"] += 1
        else:
            counts["currency_missing"] += 1

        current_price_text = _mapped(row, mapping, "current_price")
        current_price = parse_positive_decimal(current_price_text)
        if current_price is None:
            counts["current_price_invalid_or_missing"] += 1
        else:
            counts["current_price_positive"] += 1
            exponent = -current_price.as_tuple().exponent
            if exponent > 2:
                counts["current_price_more_than_2_decimals"] += 1

        previous_text = _mapped(row, mapping, "previous_price")
        if previous_text:
            previous = parse_positive_decimal(previous_text)
            if previous is None:
                counts["previous_price_invalid"] += 1
            else:
                counts["previous_price_positive"] += 1
                if current_price is not None and previous <= current_price:
                    counts["previous_price_not_above_current"] += 1

        quantity = parse_quantity(
            _mapped(row, mapping, "quantity_value"),
            _mapped(row, mapping, "quantity_unit"),
        )
        if quantity is not None:
            counts["quantity_parseable"] += 1
        elif _mapped(row, mapping, "quantity_value") or _mapped(row, mapping, "quantity_unit"):
            counts["quantity_unparseable"] += 1
        if _mapped(row, mapping, "package_text"):
            counts["package_text_present"] += 1

        availability_class = _availability_class(_mapped(row, mapping, "availability"))
        availability[availability_class] += 1

        country = _mapped(row, mapping, "country").upper()
        if country:
            countries[country] += 1

        product_url = _mapped(row, mapping, "product_url")
        if product_url:
            if valid_http_url(product_url):
                counts["product_url_valid"] += 1
            else:
                counts["product_url_invalid"] += 1
        image_url = _mapped(row, mapping, "image_url")
        if image_url:
            if valid_http_url(image_url):
                counts["image_url_valid"] += 1
            else:
                counts["image_url_invalid"] += 1

        observed_text = _mapped(row, mapping, "observed_at")
        observed_at = parse_timestamp(observed_text)
        if observed_text:
            if observed_at is None:
                counts["observed_at_invalid"] += 1
            else:
                counts["observed_at_parseable"] += 1
                timestamp_min = observed_at if timestamp_min is None else min(timestamp_min, observed_at)
                timestamp_max = observed_at if timestamp_max is None else max(timestamp_max, observed_at)
                if config.evaluated_at is not None:
                    age_seconds = (config.evaluated_at - observed_at).total_seconds()
                    if age_seconds < -300:
                        age_buckets["future_dated"] += 1
                    elif age_seconds <= 15 * 60:
                        age_buckets["within_15_minutes"] += 1
                    elif age_seconds <= 2 * 60 * 60:
                        age_buckets["within_2_hours"] += 1
                    elif age_seconds <= 24 * 60 * 60:
                        age_buckets["within_24_hours"] += 1
                    elif age_seconds <= 7 * 24 * 60 * 60:
                        age_buckets["within_7_days"] += 1
                    else:
                        age_buckets["older_than_7_days"] += 1

        expected_currency_ok = currency == config.expected_currency
        if expected_currency_ok:
            counts["expected_currency_rows"] += 1
        elif currency:
            counts["unexpected_currency_rows"] += 1

        country_ok = True
        if config.expected_country and "country" in mapping:
            country_ok = country in COUNTRY_CA_VALUES if config.expected_country == "CA" else country == config.expected_country
            if country_ok:
                counts["expected_country_rows"] += 1
            else:
                counts["unexpected_country_rows"] += 1

        structural_offer = (
            identity is not None
            and bool(title)
            and current_price is not None
            and expected_currency_ok
            and country_ok
        )
        if structural_offer:
            counts["structural_current_offer_candidates"] += 1
            if quantity is not None:
                counts["structural_unit_value_candidates"] += 1
            if availability_class == "out_of_stock":
                counts["structural_candidates_out_of_stock"] += 1

        if identity is not None:
            store = _mapped(row, mapping, "store_id")
            channel = _mapped(row, mapping, "commerce_channel")
            scope_key = (identity[0], identity[1], store, channel)
            identity_rows[scope_key] += 1
            identity_signatures[scope_key].add(_signature(row, mapping))

    duplicate_scopes = sum(1 for count in identity_rows.values() if count > 1)
    duplicate_rows = sum(count - 1 for count in identity_rows.values() if count > 1)
    conflicting_scopes = sum(1 for signatures in identity_signatures.values() if len(signatures) > 1)

    mapping_missing = [name for name, present in required_capabilities.items() if not present]
    if mapping_missing:
        status = "FAIL_SCHEMA"
    elif counts["structural_current_offer_candidates"] == 0:
        status = "FAIL_NO_STRUCTURAL_CURRENT_OFFERS"
    else:
        status = "REVIEW_DATA_AND_RIGHTS"

    rows_scanned = counts["rows_scanned"]
    report: dict[str, object] = {
        "tool": "ValuePilot merchant feed qualification harness",
        "input": {
            "file_name": config.input_path.name,
            "compressed_gzip": _is_gzip(config.input_path),
            "format": feed_format,
            "encoding": config.encoding,
            "rows_scanned": rows_scanned,
            "max_rows": config.max_rows,
            "truncated_at_max_rows": truncated,
        },
        "mapping": {
            "mode": mapping_mode,
            "fields": mapping,
            "ambiguous_auto_matches": ambiguous,
            "missing_required_capabilities": mapping_missing,
            "note": (
                "Auto mapping uses exact conservative aliases only. Ambiguous fields are not guessed. "
                "An explicit provider mapping is required before treating field semantics as authoritative."
            ),
        },
        "target_market": {
            "expected_currency": config.expected_currency,
            "expected_country": config.expected_country,
            "country_field_present": "country" in mapping,
            "country_note": (
                "If the feed has no mapped country field, this tool cannot prove geography from row data. "
                "Feed-level/provider authorization must establish Canadian scope separately."
            ),
        },
        "quality": {
            **dict(sorted(counts.items())),
            "duplicate_identity_scopes": duplicate_scopes,
            "duplicate_identity_extra_rows": duplicate_rows,
            "conflicting_identity_scopes": conflicting_scopes,
            "unique_identity_scopes": len(identity_rows),
        },
        "coverage": {
            "identity": {
                "coverage_status": "MEASURED",
                "rows_with_identity": counts["identity_present"],
                "unique_identity_scopes": len(identity_rows),
            },
            "current_offers": {
                "coverage_status": "STRUCTURAL_ONLY",
                "candidate_count": counts["structural_current_offer_candidates"],
                "authority": "NONE",
            },
            "unit_values": {
                "coverage_status": "STRUCTURAL_ONLY",
                "candidate_count": counts["structural_unit_value_candidates"],
                "authority": "NONE",
            },
            "note": (
                "Identity coverage and structural current-offer candidates are separate measurements. "
                "Structural candidates do not prove current price, package quantity, stock, freshness, "
                "rights, or production rankability."
            ),
        },
        "currencies": dict(sorted(currencies.items())),
        "countries": dict(sorted(countries.items())),
        "availability": dict(sorted(availability.items())),
        "freshness": {
            "evaluated_at": config.evaluated_at.isoformat() if config.evaluated_at else None,
            "observed_at_min": timestamp_min.isoformat() if timestamp_min else None,
            "observed_at_max": timestamp_max.isoformat() if timestamp_max else None,
            "age_buckets": dict(sorted(age_buckets.items())),
            "note": "Freshness is classified only when --evaluated-at is supplied; the tool never reads the system clock.",
        },
        "decision": {
            "status": status,
            "production_authorized": False,
            "rights_gate": "NOT_EVALUATED_BY_TOOL",
            "structural_current_offer_candidates": counts["structural_current_offer_candidates"],
            "structural_unit_value_candidates": counts["structural_unit_value_candidates"],
            "note": (
                "Structural candidates are data-quality measurements only. They are not permission to cache, index, "
                "display, redistribute, or rank a merchant feed in production. Provider-specific rights, exact field "
                "semantics, freshness policy, provenance mapping, and ValuePilot acceptance/conflict gates remain required."
            ),
        },
    }
    return report


def report_markdown(report: Mapping[str, object]) -> str:
    inp = report["input"]
    mapping = report["mapping"]
    quality = report["quality"]
    decision = report["decision"]
    freshness = report["freshness"]
    assert isinstance(inp, dict) and isinstance(mapping, dict) and isinstance(quality, dict)
    assert isinstance(decision, dict) and isinstance(freshness, dict)

    lines = [
        "# ValuePilot Merchant Feed Qualification",
        "",
        f"- Status: **{decision['status']}**",
        f"- Rows scanned: **{inp['rows_scanned']:,}**",
        f"- Format: **{inp['format']}**{' + gzip' if inp['compressed_gzip'] else ''}",
        f"- Mapping mode: **{mapping['mode']}**",
        f"- Identity rows / unique scopes: **{report['coverage']['identity']['rows_with_identity']:,} / {report['coverage']['identity']['unique_identity_scopes']:,}**",
        f"- Structural current-offer candidates: **{decision['structural_current_offer_candidates']:,}**",
        f"- Structural unit-value candidates: **{decision['structural_unit_value_candidates']:,}**",
        f"- Valid GTIN rows: **{quality.get('gtin_valid', 0):,}**",
        f"- Invalid GTIN rows: **{quality.get('gtin_invalid', 0):,}**",
        f"- Unexpected-currency rows: **{quality.get('unexpected_currency_rows', 0):,}**",
        f"- Conflicting identity scopes: **{quality.get('conflicting_identity_scopes', 0):,}**",
        "",
        "## Safety / authorization boundary",
        "",
        "**This report never authorizes production use.** Rights/caching/indexing/display/redistribution permission is a separate provider-specific gate.",
        "",
        "Identity coverage is measured separately from structural current-offer candidates; neither count grants current-price or ranking authority.",
        "",
        "Ambiguous mappings are not guessed. XML requires an explicit record tag. Freshness is evaluated only when an explicit evaluation timestamp is supplied.",
    ]
    if freshness.get("evaluated_at"):
        lines += [
            "",
            "## Freshness",
            "",
            f"- Evaluation time: **{freshness['evaluated_at']}**",
            f"- Observed range: **{freshness.get('observed_at_min')} → {freshness.get('observed_at_max')}**",
        ]
    return "\n".join(lines) + "\n"


def _parse_eval_time(value: str | None) -> datetime | None:
    if value is None:
        return None
    parsed = parse_timestamp(value)
    if parsed is None:
        raise argparse.ArgumentTypeError("--evaluated-at must be ISO-8601")
    return parsed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--mapping", type=Path)
    parser.add_argument("--xml-record-tag")
    parser.add_argument("--encoding", default="utf-8-sig")
    parser.add_argument("--expected-currency", default="CAD")
    parser.add_argument("--expected-country", default="CA")
    parser.add_argument("--evaluated-at", type=_parse_eval_time)
    parser.add_argument("--max-rows", type=int, default=500_000)
    parser.add_argument("--json", type=Path)
    parser.add_argument("--markdown", type=Path)
    args = parser.parse_args()

    config = QualificationConfig(
        input_path=args.input,
        mapping_path=args.mapping,
        xml_record_tag=args.xml_record_tag,
        encoding=args.encoding,
        expected_currency=args.expected_currency.upper(),
        expected_country=args.expected_country.upper() if args.expected_country else None,
        evaluated_at=args.evaluated_at,
        max_rows=args.max_rows,
    )
    report = qualify_feed(config)
    markdown = report_markdown(report)

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(markdown, encoding="utf-8")

    print(markdown, end="")


if __name__ == "__main__":
    main()

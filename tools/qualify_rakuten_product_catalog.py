#!/usr/bin/env python3
"""Offline qualifier for Rakuten Advertising Product Catalog text feeds.

This is research/validation tooling, not a production feed adapter. It follows the
published Rakuten Product Catalog pipe-delimited layout, validates HDR/TRL
integrity, and measures field quality without performing network access or making
any licensing/production-authorization decision.

Important trust boundaries:
- the feed header timestamp is file-generation/deposit evidence, not a per-product
  advertiser update timestamp and is never promoted to product freshness;
- Sale Price and Retail Price relationships are measured explicitly; neither field
  is promoted to a production current price until real feed semantics and rights
  are validated;
- Currency=CAD does not by itself prove Canadian geography;
- Product Catalog access does not by itself grant caching/indexing/display rights.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Iterator, TextIO

from tools.qualify_merchant_feed import is_valid_gtin, parse_positive_decimal, valid_http_url

PROFILE_ID = "rakuten-product-catalog-text-v1"
MIN_PRIMARY_FIELDS = 28
DOCUMENTED_FULL_FIELDS = 38

# Rakuten Product Catalog primary fields 1..28, in documented order.
PRIMARY_FIELDS = [
    "product_id",
    "product_name",
    "sku_number",
    "primary_category",
    "secondary_category",
    "product_url",
    "product_image_url",
    "buy_url",
    "short_description",
    "long_description",
    "discount",
    "discount_type",
    "sale_price",
    "retail_price",
    "begin_date",
    "end_date",
    "brand",
    "shipping",
    "keywords",
    "manufacturer_part_number",
    "manufacturer_name",
    "shipping_information",
    "availability",
    "upc",
    "class_id",
    "currency",
    "m1",
    "pixel",
]

REQUIRED_PRIMARY_FIELDS = {
    "product_name",
    "sku_number",
    "primary_category",
    "product_url",
    "product_image_url",
    "retail_price",
    "currency",
}

DOCUMENTED_AVAILABILITY = {"in-stock", "out-of-stock", "preorder", "backorder"}


def clean(value: object | None) -> str:
    return "" if value is None else str(value).replace("\u00a0", " ").strip()


def parse_rakuten_timestamp(value: str | None) -> datetime | None:
    text = clean(value)
    for fmt in ("%m/%d/%Y %H:%M:%S", "%m/%d/%Y/%H:%M:%S"):
        try:
            return datetime.strptime(text, fmt).replace(tzinfo=timezone.utc)
        except ValueError:
            pass
    return None


def is_gzip(path: Path) -> bool:
    if path.suffix.lower() == ".gz":
        return True
    with path.open("rb") as handle:
        return handle.read(2) == b"\x1f\x8b"


def open_text(path: Path, encoding: str) -> TextIO:
    if is_gzip(path):
        return gzip.open(path, "rt", encoding=encoding, newline="")
    return path.open("r", encoding=encoding, newline="")


def effective_suffix(path: Path) -> str:
    suffixes = [suffix.lower() for suffix in path.suffixes]
    if suffixes and suffixes[-1] == ".gz":
        suffixes.pop()
    return suffixes[-1] if suffixes else ""


def canonical_product_row(fields: list[str]) -> dict[str, str] | None:
    if len(fields) < MIN_PRIMARY_FIELDS:
        return None
    return {name: clean(fields[index]) for index, name in enumerate(PRIMARY_FIELDS)}


def qualification_price_candidate(row: dict[str, str]) -> tuple[str, Decimal | None]:
    """Return a positive price only to measure structural coverage.

    Retail Price is preferred for qualification because it is a required primary
    field. Sale Price is only a fallback when Retail Price is not a positive number.
    This function deliberately does not decide the production current price.
    """

    retail = parse_positive_decimal(row["retail_price"])
    if retail is not None:
        return "retail_price", retail
    return "sale_price_fallback", parse_positive_decimal(row["sale_price"])


def iter_records(
    path: Path,
    encoding: str,
) -> tuple[dict[str, object], Iterator[list[str]]]:
    if effective_suffix(path) not in {".txt", ""}:
        raise ValueError("Rakuten text qualifier accepts .txt or .txt.gz files")

    handle = open_text(path, encoding)
    reader = csv.reader(handle, delimiter="|", quotechar='"')
    try:
        header = next(reader)
    except StopIteration:
        handle.close()
        raise ValueError("Rakuten feed is empty")

    if len(header) < 4 or header[0] != "HDR":
        handle.close()
        raise ValueError("Rakuten Product Catalog text feed must begin with HDR|MID|Name|Timestamp")

    header_time = parse_rakuten_timestamp(header[3])
    metadata: dict[str, object] = {
        "profile": PROFILE_ID,
        "header_valid": True,
        "merchant_id": clean(header[1]),
        "merchant_name": clean(header[2]),
        "header_created_at_raw": clean(header[3]),
        "header_created_at_parseable": header_time is not None,
        "header_created_at_utc": header_time.isoformat() if header_time else None,
        "trailer_checked": False,
        "trailer_valid": False,
        "trailer_declared_products": None,
    }

    def iterator() -> Iterator[list[str]]:
        pending: list[str] | None = None
        try:
            for fields in reader:
                if pending is not None:
                    if pending and pending[0] == "TRL":
                        raise ValueError("Rakuten trailer appeared before the final record")
                    yield pending
                pending = fields

            metadata["trailer_checked"] = True
            if pending and len(pending) >= 2 and pending[0] == "TRL":
                try:
                    declared = int(clean(pending[1]))
                except ValueError:
                    declared = None
                metadata["trailer_declared_products"] = declared
                metadata["trailer_valid"] = declared is not None and declared >= 0
            elif pending is not None:
                # Yield the final non-trailer product so the report can fail on the
                # missing trailer without silently dropping a product row.
                yield pending
        finally:
            handle.close()

    return metadata, iterator()


def qualify(
    path: Path,
    *,
    encoding: str = "utf-8-sig",
    expected_currency: str = "CAD",
    evaluated_at: datetime | None = None,
    max_rows: int = 500_000,
) -> dict[str, object]:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError(f"Missing or empty feed: {path}")
    if max_rows <= 0:
        raise ValueError("max_rows must be positive")
    expected_currency = expected_currency.upper()
    if len(expected_currency) != 3 or not expected_currency.isalpha():
        raise ValueError("expected_currency must be a 3-letter code")

    metadata, records = iter_records(path, encoding)
    counts: Counter[str] = Counter()
    currencies: Counter[str] = Counter()
    availabilities: Counter[str] = Counter()
    class_ids: Counter[str] = Counter()
    sku_signatures: dict[str, set[tuple[str, str, str]]] = defaultdict(set)
    product_id_signatures: dict[str, set[tuple[str, str, str]]] = defaultdict(set)
    truncated = False

    for index, fields in enumerate(records, start=1):
        if index > max_rows:
            truncated = True
            break

        counts["product_records_scanned"] += 1
        field_count = len(fields)
        if field_count < MIN_PRIMARY_FIELDS:
            counts["malformed_too_few_primary_fields"] += 1
            continue
        if field_count >= DOCUMENTED_FULL_FIELDS:
            counts["records_with_documented_full_shape_or_more"] += 1
        else:
            counts["records_with_primary_but_short_of_documented_full_shape"] += 1

        row = canonical_product_row(fields)
        assert row is not None

        for required in REQUIRED_PRIMARY_FIELDS:
            if not row[required]:
                counts[f"required_missing_{required}"] += 1

        if row["short_description"]:
            counts["short_description_present"] += 1
        else:
            counts["short_description_missing"] += 1
        if row["long_description"]:
            counts["long_description_present"] += 1
        else:
            counts["long_description_missing"] += 1
        if row["manufacturer_name"]:
            counts["manufacturer_name_present"] += 1
        else:
            counts["manufacturer_name_missing"] += 1

        retail = parse_positive_decimal(row["retail_price"])
        sale = parse_positive_decimal(row["sale_price"])
        price_source, price_candidate = qualification_price_candidate(row)
        if retail is not None:
            counts["retail_price_positive"] += 1
        else:
            counts["retail_price_invalid_or_missing"] += 1
        if row["sale_price"]:
            if sale is not None:
                counts["sale_price_positive"] += 1
                if retail is not None:
                    if sale < retail:
                        counts["sale_price_below_retail"] += 1
                    elif sale == retail:
                        counts["sale_price_equal_retail"] += 1
                    else:
                        counts["sale_price_above_retail"] += 1
            else:
                counts["sale_price_invalid"] += 1
        else:
            counts["sale_price_missing"] += 1
        if price_candidate is not None:
            counts["qualification_price_candidate_positive"] += 1
            counts[f"qualification_price_source_{price_source}"] += 1

        currency = row["currency"].upper()
        if currency:
            currencies[currency] += 1
            if currency == expected_currency:
                counts["expected_currency_rows"] += 1
            else:
                counts["unexpected_currency_rows"] += 1
        else:
            counts["currency_missing"] += 1

        availability = row["availability"].lower()
        if availability:
            availabilities[availability] += 1
            if availability in DOCUMENTED_AVAILABILITY:
                counts["availability_documented_value"] += 1
            else:
                counts["availability_unrecognized"] += 1
        else:
            counts["availability_missing"] += 1

        upc = row["upc"]
        if upc:
            counts["upc_present"] += 1
            if is_valid_gtin(upc):
                counts["upc_checksum_valid_gtin"] += 1
            else:
                counts["upc_not_checksum_valid_gtin"] += 1
        else:
            counts["upc_missing"] += 1

        if row["product_url"]:
            if valid_http_url(row["product_url"]):
                counts["product_url_valid"] += 1
            else:
                counts["product_url_invalid"] += 1
        if row["product_image_url"]:
            if valid_http_url(row["product_image_url"]):
                counts["image_url_valid"] += 1
            else:
                counts["image_url_invalid"] += 1

        class_id = row["class_id"] or "<blank>"
        class_ids[class_id] += 1
        if row["class_id"]:
            counts["class_id_present"] += 1
        else:
            counts["class_id_blank"] += 1
        # Rakuten's documented Food & Drink Class ID 110 uses product field 32
        # (attribute 4, zero-based index 31) as Size. We measure presence only;
        # we do not parse or promote it to authoritative quantity automatically.
        if row["class_id"] == "110":
            counts["class_110_food_drink_rows"] += 1
            if field_count > 31 and clean(fields[31]):
                counts["class_110_size_attribute_present"] += 1

        sku = row["sku_number"]
        product_id = row["product_id"]
        signature = (row["product_name"], row["retail_price"], row["sale_price"])
        if sku:
            sku_signatures[sku].add(signature)
        if product_id:
            product_id_signatures[product_id].add(signature)

        required_present = all(row[name] for name in REQUIRED_PRIMARY_FIELDS)
        structural_candidate = (
            required_present
            and price_candidate is not None
            and currency == expected_currency
        )
        if structural_candidate:
            counts["structural_offer_candidates"] += 1
            if availability == "out-of-stock":
                counts["structural_candidates_out_of_stock"] += 1
            elif availability in {"preorder", "backorder"}:
                counts["structural_candidates_non_immediate_availability"] += 1

    metadata["truncated_before_trailer_validation"] = truncated
    declared = metadata.get("trailer_declared_products")
    if not truncated and metadata.get("trailer_checked") and metadata.get("trailer_valid"):
        metadata["trailer_count_matches_records_scanned"] = declared == counts["product_records_scanned"]
    else:
        metadata["trailer_count_matches_records_scanned"] = None

    sku_duplicate_keys = sum(1 for signatures in sku_signatures.values() if len(signatures) > 1)
    product_id_duplicate_keys = sum(1 for signatures in product_id_signatures.values() if len(signatures) > 1)

    header_time = parse_rakuten_timestamp(str(metadata.get("header_created_at_raw") or ""))
    header_age: dict[str, object] = {
        "evaluated_at": evaluated_at.isoformat() if evaluated_at else None,
        "file_header_age_seconds": None,
        "note": (
            "The HDR timestamp is file-generation/deposit evidence only. It is not a per-product advertiser update "
            "timestamp and must not be used to claim that every product price is fresh."
        ),
    }
    if evaluated_at is not None and header_time is not None:
        header_age["file_header_age_seconds"] = int((evaluated_at - header_time).total_seconds())

    integrity_failure = (
        counts["malformed_too_few_primary_fields"] > 0
        or (
            not truncated
            and (
                not metadata.get("trailer_valid")
                or metadata.get("trailer_count_matches_records_scanned") is False
            )
        )
    )
    if integrity_failure:
        status = "FAIL_FEED_INTEGRITY"
    elif truncated:
        status = "REVIEW_TRUNCATED_DATA_AND_RIGHTS"
    elif counts["structural_offer_candidates"] == 0:
        status = "FAIL_NO_STRUCTURAL_OFFERS"
    else:
        status = "REVIEW_DATA_AND_RIGHTS"

    report: dict[str, object] = {
        "tool": "ValuePilot Rakuten Product Catalog offline qualifier",
        "profile": PROFILE_ID,
        "input": {
            "file_name": path.name,
            "gzip": is_gzip(path),
            "encoding": encoding,
            "max_rows": max_rows,
            "truncated": truncated,
        },
        "feed_metadata": metadata,
        "target_market": {
            "expected_currency": expected_currency,
            "row_level_country_field": False,
            "note": (
                "Rakuten's primary Product Catalog record has Currency but no row-level country field. CAD rows are "
                "not treated as proof of Canadian geography; feed variant/location and advertiser authorization remain separate gates."
            ),
        },
        "quality": {
            **dict(sorted(counts.items())),
            "unique_skus": len(sku_signatures),
            "sku_keys_with_conflicting_name_or_price": sku_duplicate_keys,
            "unique_product_ids": len(product_id_signatures),
            "product_id_keys_with_conflicting_name_or_price": product_id_duplicate_keys,
        },
        "currencies": dict(sorted(currencies.items())),
        "availability": dict(sorted(availabilities.items())),
        "class_ids": dict(sorted(class_ids.items())),
        "file_age": header_age,
        "price_semantics_gate": {
            "sale_below_retail_rows": counts["sale_price_below_retail"],
            "sale_equal_retail_rows": counts["sale_price_equal_retail"],
            "sale_above_retail_rows": counts["sale_price_above_retail"],
            "safe_to_infer_discount_from_field_names_alone": False,
            "note": (
                "Sale Price and Retail Price are preserved and their relationship is measured. Equal or inverted values "
                "must never create a savings claim, and this qualifier does not decide which field is the production current price."
            ),
        },
        "quantity_gate": {
            "generic_structured_quantity_available": False,
            "note": (
                "The Rakuten primary 28-field schema does not provide a universal structured package quantity. "
                "Class-specific attributes may contain Size for some classes, but ValuePilot will not guess package quantity "
                "from titles/descriptions or promote class-specific text without a validated parser."
            ),
        },
        "decision": {
            "status": status,
            "production_authorized": False,
            "rights_gate": "NOT_EVALUATED_BY_TOOL",
            "structural_offer_candidates": counts["structural_offer_candidates"],
            "unit_value_candidates": 0,
            "note": (
                "A structural offer candidate only means required fields, a positive qualification price candidate, and "
                f"{expected_currency} are present. Retail Price is preferred only to prove numeric qualification coverage, "
                "with Sale Price used only as a fallback if Retail Price is not positive. This does not establish current-offer "
                "semantics, product freshness, Canadian geography, package quantity, caching/indexing/display rights, or production rankability."
            ),
        },
    }
    return report


def markdown(report: dict[str, object]) -> str:
    inp = report["input"]
    metadata = report["feed_metadata"]
    quality = report["quality"]
    decision = report["decision"]
    price_gate = report["price_semantics_gate"]
    assert isinstance(inp, dict) and isinstance(metadata, dict)
    assert isinstance(quality, dict) and isinstance(decision, dict) and isinstance(price_gate, dict)

    lines = [
        "# ValuePilot Rakuten Product Catalog Qualification",
        "",
        f"- Status: **{decision['status']}**",
        f"- Product records scanned: **{quality.get('product_records_scanned', 0):,}**",
        f"- Structural offer candidates: **{decision['structural_offer_candidates']:,}**",
        f"- Expected-currency rows: **{quality.get('expected_currency_rows', 0):,}**",
        f"- Valid GTIN/UPC rows: **{quality.get('upc_checksum_valid_gtin', 0):,}**",
        f"- Full-shape (38+ field) rows: **{quality.get('records_with_documented_full_shape_or_more', 0):,}**",
        f"- Sale below retail rows: **{price_gate.get('sale_below_retail_rows', 0):,}**",
        f"- Sale equal retail rows: **{price_gate.get('sale_equal_retail_rows', 0):,}**",
        f"- Sale above retail rows: **{price_gate.get('sale_above_retail_rows', 0):,}**",
        f"- Malformed short rows: **{quality.get('malformed_too_few_primary_fields', 0):,}**",
        f"- Trailer valid: **{metadata.get('trailer_valid')}**",
        f"- Trailer count matches scanned rows: **{metadata.get('trailer_count_matches_records_scanned')}**",
        "",
        "## Non-negotiable interpretation",
        "",
        "This is an offline data-quality report only. It does **not** authorize production use, and the HDR timestamp is not treated as per-product freshness.",
        "",
        "Sale Price and Retail Price are preserved as separate source fields. Equal or inverted values do not create a discount, and this qualifier does not decide the production current price.",
        "",
        "Rakuten's primary schema does not give ValuePilot a universal structured package quantity, so this qualifier reports **0 unit-value candidates** until quantity is established by validated advertiser/class-specific evidence or another source joined by strong product identity.",
    ]
    return "\n".join(lines) + "\n"


def parse_eval(value: str | None) -> datetime | None:
    if value is None:
        return None
    text = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("--evaluated-at must be ISO-8601") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--encoding", default="utf-8-sig")
    parser.add_argument("--expected-currency", default="CAD")
    parser.add_argument("--evaluated-at", type=parse_eval)
    parser.add_argument("--max-rows", type=int, default=500_000)
    parser.add_argument("--json", type=Path)
    parser.add_argument("--markdown", type=Path)
    args = parser.parse_args()

    report = qualify(
        args.input,
        encoding=args.encoding,
        expected_currency=args.expected_currency,
        evaluated_at=args.evaluated_at,
        max_rows=args.max_rows,
    )
    rendered = markdown(report)

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(rendered, encoding="utf-8")

    print(rendered, end="")


if __name__ == "__main__":
    main()

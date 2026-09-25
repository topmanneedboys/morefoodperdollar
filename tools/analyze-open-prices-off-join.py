#!/usr/bin/env python3
"""Measure a provenance-safe Open Prices -> Open Food Facts quantity join.

Research tooling only. It downloads no retailer pages, persists no contributor
identifiers or proof images, and writes aggregate metrics only. Open Prices
supplies price/location/date/proof facts; Open Food Facts supplies product
metadata. Package quantity is never injected into the price observation.
"""

from __future__ import annotations

import argparse
import json
import re
import time
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

import duckdb
import requests

OFF_SEARCH_URL = "https://world.openfoodfacts.org/api/v2/search"
USER_AGENT = (
    "ValuePilot-OpenData-Validation/1.0 "
    "(https://topmanneedboys.github.io/morefoodperdollar/)"
)
FIELDS = (
    "code,product_name,brands,quantity,product_quantity,"
    "product_quantity_unit,last_modified_t"
)
BATCH_SIZE = 80
MAX_BASE_QUANTITY = Decimal("1000000")  # g or ml
DECIMAL_RE = re.compile(r"^\d{1,12}(?:[.,]\d{1,6})?$")
SIMPLE_RE = re.compile(r"^(\d{1,12}(?:[.,]\d{1,6})?)\s*(g|kg|ml|cl|l)$", re.I)
MULTIPACK_RE = re.compile(
    r"^(\d{1,4})\s*[x×]\s*(\d{1,12}(?:[.,]\d{1,6})?)\s*(g|kg|ml|cl|l)$",
    re.I,
)


def is_valid_gtin(value: str | None) -> bool:
    if value is None:
        return False
    value = value.strip()
    if len(value) not in {8, 12, 13, 14} or not value.isdigit():
        return False
    supplied = int(value[-1])
    total = 0
    weight = 3
    for char in reversed(value[:-1]):
        total += int(char) * weight
        weight = 1 if weight == 3 else 3
    expected = (10 - (total % 10)) % 10
    return supplied == expected


def parse_positive_decimal(value: Any) -> Decimal | None:
    if value is None:
        return None
    text = str(value).strip().replace(",", ".")
    if not DECIMAL_RE.fullmatch(text):
        return None
    try:
        amount = Decimal(text)
    except InvalidOperation:
        return None
    if amount <= 0 or amount > MAX_BASE_QUANTITY:
        return None
    if max(0, -amount.as_tuple().exponent) > 6:
        return None
    return amount


def normalized_quantity(value: Any, unit: Any) -> tuple[str, int] | None:
    amount = parse_positive_decimal(value)
    unit_text = str(unit or "").strip().lower()
    if amount is None or unit_text not in {"g", "ml"}:
        return None
    micros_decimal = amount * Decimal(1_000_000)
    if micros_decimal != micros_decimal.to_integral_value():
        return None
    micros = int(micros_decimal)
    if micros <= 0:
        return None
    return ("GRAM" if unit_text == "g" else "MILLILITRE", micros)


def parse_raw_quantity(value: Any) -> tuple[str, int] | None:
    if value is None:
        return None
    text = (
        str(value)
        .replace("\u00a0", " ")
        .replace("℮", "")
        .strip()
        .lower()
    )
    text = re.sub(r"\s+", " ", text)

    match = MULTIPACK_RE.fullmatch(text)
    if match:
        count = int(match.group(1))
        if count <= 0 or count > 1000:
            return None
        each = parse_positive_decimal(match.group(2))
        if each is None:
            return None
        converted = convert_displayed(each, match.group(3))
        if converted is None:
            return None
        unit, micros = converted
        total = micros * count
        return (unit, total) if total > 0 else None

    match = SIMPLE_RE.fullmatch(text)
    if not match:
        return None
    amount = parse_positive_decimal(match.group(1))
    if amount is None:
        return None
    return convert_displayed(amount, match.group(2))


def convert_displayed(amount: Decimal, unit: str) -> tuple[str, int] | None:
    normalized_unit = unit.lower()
    if normalized_unit == "g":
        base = amount
        output_unit = "GRAM"
    elif normalized_unit == "kg":
        base = amount * Decimal(1000)
        output_unit = "GRAM"
    elif normalized_unit == "ml":
        base = amount
        output_unit = "MILLILITRE"
    elif normalized_unit == "cl":
        base = amount * Decimal(10)
        output_unit = "MILLILITRE"
    elif normalized_unit == "l":
        base = amount * Decimal(1000)
        output_unit = "MILLILITRE"
    else:
        return None

    if base <= 0 or base > MAX_BASE_QUANTITY:
        return None
    micros_decimal = base * Decimal(1_000_000)
    if micros_decimal != micros_decimal.to_integral_value():
        return None
    return output_unit, int(micros_decimal)


def request_json(session: requests.Session, params: dict[str, Any]) -> dict[str, Any]:
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            response = session.get(OFF_SEARCH_URL, params=params, timeout=45)
            if response.status_code == 429:
                retry_after = response.headers.get("Retry-After")
                delay = float(retry_after) if retry_after and retry_after.isdigit() else 10.0 * (attempt + 1)
                time.sleep(delay)
                continue
            response.raise_for_status()
            payload = response.json()
            if not isinstance(payload, dict):
                raise RuntimeError("Open Food Facts response was not a JSON object")
            return payload
        except (requests.RequestException, ValueError, RuntimeError) as exc:
            last_error = exc
            if attempt == 4:
                break
            time.sleep(min(30.0, 2.0 ** attempt))
    raise RuntimeError(f"Open Food Facts request failed after retries: {last_error}")


def fetch_off_products(codes: list[str]) -> tuple[dict[str, list[dict[str, Any]]], int]:
    by_code: dict[str, list[dict[str, Any]]] = defaultdict(list)
    session = requests.Session()
    session.headers.update({"User-Agent": USER_AGENT, "Accept": "application/json"})
    api_calls = 0

    for start in range(0, len(codes), BATCH_SIZE):
        batch = codes[start : start + BATCH_SIZE]
        if api_calls:
            # Search is limited to 10 requests/min/IP. Deliberately stay below it.
            time.sleep(6.5)
        payload = request_json(
            session,
            {
                "code": ",".join(batch),
                "fields": FIELDS,
                "page": 1,
                "page_size": len(batch),
                "sort_by": "nothing",
            },
        )
        api_calls += 1
        products = payload.get("products", [])
        if not isinstance(products, list):
            raise RuntimeError("Open Food Facts search response has no products array")
        for product in products:
            if not isinstance(product, dict):
                continue
            code = str(product.get("code") or "").strip()
            if code in batch:
                by_code[code].append(product)

    return dict(by_code), api_calls


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--markdown", required=True, type=Path)
    args = parser.parse_args()

    if not args.input.is_file() or args.input.stat().st_size == 0:
        raise SystemExit(f"Missing or empty Parquet input: {args.input}")

    con = duckdb.connect(database=":memory:")
    con.read_parquet(str(args.input)).create_view("prices")
    columns = {row[0] for row in con.execute("DESCRIBE prices").fetchall()}
    required = {
        "type",
        "product_code",
        "price",
        "currency",
        "date",
        "proof_id",
        "proof_type",
        "location_type",
        "location_osm_tag_key",
        "location_osm_address_country_code",
    }
    missing = sorted(required - columns)
    if missing:
        raise SystemExit(f"Open Prices schema changed; missing columns: {missing}")

    strict = " AND ".join(
        [
            "upper(coalesce(currency, '')) = 'CAD'",
            "upper(coalesce(location_osm_address_country_code, '')) = 'CA'",
            "type = 'PRODUCT'",
            "product_code IS NOT NULL",
            "try_cast(price AS DOUBLE) IS NOT NULL",
            "try_cast(price AS DOUBLE) > 0",
            "proof_id IS NOT NULL",
            "proof_type IN ('RECEIPT', 'PRICE_TAG')",
            "location_type = 'OSM'",
            "location_osm_tag_key = 'shop'",
            "date IS NOT NULL",
        ]
    )

    rows = con.execute(
        f"""
        SELECT
            product_code,
            count(*) AS price_rows,
            min(date) AS first_date,
            max(date) AS latest_date,
            sum(CASE WHEN date >= current_date - INTERVAL 7 DAY THEN 1 ELSE 0 END) AS rows_7d,
            sum(CASE WHEN date >= current_date - INTERVAL 30 DAY THEN 1 ELSE 0 END) AS rows_30d,
            sum(CASE WHEN date >= current_date - INTERVAL 90 DAY THEN 1 ELSE 0 END) AS rows_90d
        FROM prices
        WHERE {strict}
        GROUP BY product_code
        """
    ).fetchall()

    price_stats: dict[str, dict[str, Any]] = {}
    invalid_gtin_groups = 0
    for code, price_rows, first_date, latest_date, rows_7d, rows_30d, rows_90d in rows:
        text = str(code or "").strip()
        if not is_valid_gtin(text):
            invalid_gtin_groups += 1
            continue
        price_stats[text] = {
            "price_rows": int(price_rows),
            "first_date": str(first_date),
            "latest_date": str(latest_date),
            "rows_7d": int(rows_7d or 0),
            "rows_30d": int(rows_30d or 0),
            "rows_90d": int(rows_90d or 0),
        }

    codes = sorted(price_stats)
    off_by_code, api_calls = fetch_off_products(codes)

    found = 0
    structured_present = 0
    structured_valid = 0
    raw_parseable = 0
    raw_mismatch_codes = 0
    conflicting_quantity_codes = 0
    usable_codes: set[str] = set()
    names_present = 0
    brands_present = 0
    modified_values: list[int] = []

    for code in codes:
        products = off_by_code.get(code, [])
        if not products:
            continue
        found += 1
        if any(str(p.get("product_name") or "").strip() for p in products):
            names_present += 1
        if any(str(p.get("brands") or "").strip() for p in products):
            brands_present += 1

        fingerprints: set[tuple[str, int]] = set()
        code_structured_present = False
        code_structured_valid = False
        code_raw_parseable = False
        code_raw_mismatch = False

        for product in products:
            quantity_value = product.get("product_quantity")
            quantity_unit = product.get("product_quantity_unit")
            if str(quantity_value or "").strip() or str(quantity_unit or "").strip():
                code_structured_present = True

            structured = normalized_quantity(quantity_value, quantity_unit)
            if structured is not None:
                code_structured_valid = True
                raw = parse_raw_quantity(product.get("quantity"))
                if raw is not None:
                    code_raw_parseable = True
                    if raw != structured:
                        code_raw_mismatch = True
                fingerprints.add(structured)

            modified = product.get("last_modified_t")
            try:
                modified_int = int(modified)
            except (TypeError, ValueError):
                modified_int = 0
            if modified_int > 0:
                modified_values.append(modified_int)

        structured_present += int(code_structured_present)
        structured_valid += int(code_structured_valid)
        raw_parseable += int(code_raw_parseable)
        raw_mismatch_codes += int(code_raw_mismatch)

        if len(fingerprints) > 1:
            conflicting_quantity_codes += 1
            continue
        if code_raw_mismatch:
            continue
        if len(fingerprints) == 1:
            usable_codes.add(code)

    total_strict_rows = sum(item["price_rows"] for item in price_stats.values())
    joined_rows = sum(price_stats[code]["price_rows"] for code in usable_codes)
    total_7d = sum(item["rows_7d"] for item in price_stats.values())
    total_30d = sum(item["rows_30d"] for item in price_stats.values())
    total_90d = sum(item["rows_90d"] for item in price_stats.values())
    joined_7d = sum(price_stats[code]["rows_7d"] for code in usable_codes)
    joined_30d = sum(price_stats[code]["rows_30d"] for code in usable_codes)
    joined_90d = sum(price_stats[code]["rows_90d"] for code in usable_codes)

    latest_price_date = max((item["latest_date"] for item in price_stats.values()), default=None)
    earliest_price_date = min((item["first_date"] for item in price_stats.values()), default=None)
    modified_min = min(modified_values) if modified_values else None
    modified_max = max(modified_values) if modified_values else None

    report: dict[str, Any] = {
        "measurement": "Open Prices strict Canadian proof-backed GTINs joined to Open Food Facts metadata",
        "open_prices": {
            "valid_gtin_products": len(codes),
            "strict_price_rows": total_strict_rows,
            "invalid_gtin_product_groups_excluded": invalid_gtin_groups,
            "date_min": earliest_price_date,
            "date_max": latest_price_date,
            "rows_last_7_days": total_7d,
            "rows_last_30_days": total_30d,
            "rows_last_90_days": total_90d,
        },
        "open_food_facts": {
            "api": "v2 bulk search by code with fields projection",
            "api_calls": api_calls,
            "requested_gtins": len(codes),
            "found_gtins": found,
            "missing_gtins": len(codes) - found,
            "product_name_present_gtins": names_present,
            "brands_present_gtins": brands_present,
            "structured_quantity_present_gtins": structured_present,
            "structured_quantity_valid_gtins": structured_valid,
            "raw_quantity_parseable_gtins": raw_parseable,
            "raw_structured_mismatch_gtins": raw_mismatch_codes,
            "conflicting_duplicate_quantity_gtins": conflicting_quantity_codes,
            "usable_quantity_gtins": len(usable_codes),
            "last_modified_t_min": modified_min,
            "last_modified_t_max": modified_max,
            "last_modified_utc_min": (
                datetime.fromtimestamp(modified_min, tz=timezone.utc).isoformat()
                if modified_min else None
            ),
            "last_modified_utc_max": (
                datetime.fromtimestamp(modified_max, tz=timezone.utc).isoformat()
                if modified_max else None
            ),
        },
        "join": {
            "unique_product_join_rate": len(usable_codes) / len(codes) if codes else 0.0,
            "strict_price_rows_with_usable_quantity": joined_rows,
            "strict_price_row_join_rate": joined_rows / total_strict_rows if total_strict_rows else 0.0,
            "joined_rows_last_7_days": joined_7d,
            "joined_rows_last_30_days": joined_30d,
            "joined_rows_last_90_days": joined_90d,
        },
        "safety": {
            "output_contains_product_codes": False,
            "output_contains_contributor_ids": False,
            "output_contains_proof_paths": False,
            "package_quantity_source": "Open Food Facts only",
            "price_source": "Open Prices only",
            "join_key": "checksum-valid GTIN",
            "raw_structured_disagreement_policy": "reject quantity join",
        },
    }

    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    off = report["open_food_facts"]
    join = report["join"]
    markdown = [
        "# Open Prices × Open Food Facts real-data join",
        "",
        "This is an aggregate research measurement. It does not merge source records or make old prices current.",
        "",
        f"- Strict Open Prices rows: **{total_strict_rows:,}** across **{len(codes):,}** checksum-valid GTINs",
        f"- Open Prices date range: **{earliest_price_date} → {latest_price_date}**",
        f"- Open Food Facts GTINs found: **{found:,} / {len(codes):,}**",
        f"- OFF GTINs with valid structured g/ml package quantity: **{structured_valid:,}**",
        f"- Raw-vs-structured quantity mismatches: **{raw_mismatch_codes:,}**",
        f"- Conflicting duplicate OFF quantities: **{conflicting_quantity_codes:,}**",
        f"- Usable quantity joins after fail-closed checks: **{len(usable_codes):,} / {len(codes):,} ({join['unique_product_join_rate']:.1%})**",
        f"- Strict Open Prices rows covered by usable quantity: **{joined_rows:,} / {total_strict_rows:,} ({join['strict_price_row_join_rate']:.1%})**",
        f"- All strict rows in last 7 / 30 / 90 days: **{total_7d:,} / {total_30d:,} / {total_90d:,}**",
        f"- Joined rows in last 7 / 30 / 90 days: **{joined_7d:,} / {joined_30d:,} / {joined_90d:,}**",
        f"- OFF bulk-search calls: **{off['api_calls']}**",
        "",
        "The join uses only validated GTIN identity. Open Prices remains the price/proof/date source; Open Food Facts remains the package-metadata source. A quantity join does not upgrade stale or display-only price evidence to rankable evidence.",
    ]
    args.markdown.write_text("\n".join(markdown) + "\n", encoding="utf-8")
    print(args.markdown.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()

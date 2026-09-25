#!/usr/bin/env python3
"""Measure OFF package-quantity coverage for an authorized Rakuten feed.

Research tooling only.

The input is a local authorized Rakuten Product Catalog text feed. Product
identifiers are held in memory only. The tool queries Open Food Facts' public
structured search API for a deliberately narrow metadata projection and writes
aggregate reports only.

It never emits source product rows, GTINs, product URLs, credentials, or
provider account identifiers. It also never parses product titles/descriptions
to invent package quantity.

This tool does not grant or infer production rights for the Rakuten feed or
Open Food Facts data. It does not add networking to Android.
"""

from __future__ import annotations

import argparse
import json
import re
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable
from urllib import error, parse, request

from tools.qualify_merchant_feed import is_valid_gtin
from tools.qualify_rakuten_product_catalog import canonical_product_row, iter_records

OFF_SEARCH_URL = "https://world.openfoodfacts.org/api/v2/search"
USER_AGENT = (
    "ValuePilot-Quantity-Coverage-Research/1.0 "
    "(https://topmanneedboys.github.io/morefoodperdollar/)"
)
OFF_FIELDS = (
    "code,brands,quantity,product_quantity,product_quantity_unit,last_modified_t"
)
DEFAULT_BATCH_SIZE = 80
MIN_SEARCH_DELAY_SECONDS = 6.5
MAX_GTINS = 5_000
MAX_BASE_QUANTITY = Decimal("1000000")  # g or ml
MAX_COUNT = 10_000

DECIMAL_RE = re.compile(r"^\d{1,12}(?:[.,]\d{1,6})?$")
SUPPLEMENT_COUNT_RE = re.compile(
    r"^(\d{1,5})\s+"
    r"(tablet|tablets|capsule|capsules|caplet|caplets|softgel|softgels|"
    r"soft gel|soft gels|gummy|gummies|lozenge|lozenges|sachet|sachets|"
    r"packet|packets|comprimé|comprimés|gélule|gélules|pastille|pastilles|"
    r"gomme|gommes)$",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class FeedIdentitySet:
    product_records: int
    rows_with_valid_gtin: int
    rows_missing_or_invalid_gtin: int
    gtins: tuple[str, ...]
    trailer_declared_products: int


@dataclass(frozen=True)
class QuantityCandidate:
    basis: str
    unit: str
    amount_micros: int

    @property
    def fingerprint(self) -> str:
        return f"{self.basis}:{self.unit}:{self.amount_micros}"


@dataclass(frozen=True)
class CodeResolution:
    matched: bool
    candidate: QuantityCandidate | None
    conflict: bool
    expected_brand_seen: bool
    last_modified_present: bool


def _clean_text(value: Any) -> str:
    return "" if value is None else str(value).replace("\u00a0", " ").strip()


def _parse_positive_decimal(value: Any) -> Decimal | None:
    text = _clean_text(value).replace(",", ".")
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


def parse_displayed_supplement_count(value: Any) -> QuantityCandidate | None:
    """Accept only exact whole-package count syntax from the source quantity field."""

    normalized = _clean_text(value).replace("℮", "").lower()
    normalized = re.sub(r"\s+", " ", normalized).strip()
    match = SUPPLEMENT_COUNT_RE.fullmatch(normalized)
    if not match:
        return None
    count = int(match.group(1))
    if count < 1 or count > MAX_COUNT:
        return None
    return QuantityCandidate(
        basis="displayed_supplement_count",
        unit="COUNT",
        amount_micros=count * 1_000_000,
    )


def parse_structured_mass_or_volume(
    value: Any,
    unit: Any,
) -> QuantityCandidate | None:
    amount = _parse_positive_decimal(value)
    unit_text = _clean_text(unit).lower()
    if amount is None or unit_text not in {"g", "ml"}:
        return None
    micros_decimal = amount * Decimal(1_000_000)
    if micros_decimal != micros_decimal.to_integral_value():
        return None
    return QuantityCandidate(
        basis="structured_mass_or_volume",
        unit="GRAM" if unit_text == "g" else "MILLILITRE",
        amount_micros=int(micros_decimal),
    )


def classify_off_product(product: dict[str, Any]) -> QuantityCandidate | None:
    """Mirror the strict Kotlin mapper's preference: exact count, else structured g/ml."""

    displayed = parse_displayed_supplement_count(product.get("quantity"))
    if displayed is not None:
        return displayed
    return parse_structured_mass_or_volume(
        product.get("product_quantity"),
        product.get("product_quantity_unit"),
    )


def resolve_code(
    products: Iterable[dict[str, Any]],
    *,
    expected_brand: str | None,
) -> CodeResolution:
    records = list(products)
    if not records:
        return CodeResolution(
            matched=False,
            candidate=None,
            conflict=False,
            expected_brand_seen=False,
            last_modified_present=False,
        )

    candidates = [classify_off_product(product) for product in records]
    non_null = [candidate for candidate in candidates if candidate is not None]
    distinct = {candidate.fingerprint: candidate for candidate in non_null}

    expected = _clean_text(expected_brand).casefold()
    expected_brand_seen = False
    if expected:
        expected_brand_seen = any(
            expected in _clean_text(product.get("brands")).casefold()
            for product in records
        )

    last_modified_present = any(
        _clean_text(product.get("last_modified_t")).isdigit()
        and int(_clean_text(product.get("last_modified_t"))) > 0
        for product in records
    )

    if len(distinct) > 1:
        return CodeResolution(
            matched=True,
            candidate=None,
            conflict=True,
            expected_brand_seen=expected_brand_seen,
            last_modified_present=last_modified_present,
        )

    candidate = next(iter(distinct.values()), None)
    return CodeResolution(
        matched=True,
        candidate=candidate,
        conflict=False,
        expected_brand_seen=expected_brand_seen,
        last_modified_present=last_modified_present,
    )


def extract_feed_gtins(
    path: Path,
    *,
    encoding: str = "utf-8-sig",
    max_gtins: int = MAX_GTINS,
) -> FeedIdentitySet:
    if not path.is_file() or path.stat().st_size == 0:
        raise ValueError("Missing or empty local Rakuten feed")
    if max_gtins <= 0 or max_gtins > MAX_GTINS:
        raise ValueError(f"max_gtins must be between 1 and {MAX_GTINS}")

    metadata, records = iter_records(path, encoding)
    product_records = 0
    rows_with_valid_gtin = 0
    rows_missing_or_invalid_gtin = 0
    gtins: set[str] = set()

    for fields in records:
        product_records += 1
        row = canonical_product_row(fields)
        if row is None:
            rows_missing_or_invalid_gtin += 1
            continue
        gtin = _clean_text(row.get("upc"))
        if not is_valid_gtin(gtin):
            rows_missing_or_invalid_gtin += 1
            continue
        rows_with_valid_gtin += 1
        gtins.add(gtin)
        if len(gtins) > max_gtins:
            raise ValueError("Feed exceeds the bounded GTIN research limit")

    if not metadata.get("trailer_checked") or not metadata.get("trailer_valid"):
        raise ValueError("Complete-feed measurement requires a valid Rakuten trailer")

    declared = metadata.get("trailer_declared_products")
    if not isinstance(declared, int) or declared != product_records:
        raise ValueError("Rakuten trailer product count does not match parsed product rows")

    return FeedIdentitySet(
        product_records=product_records,
        rows_with_valid_gtin=rows_with_valid_gtin,
        rows_missing_or_invalid_gtin=rows_missing_or_invalid_gtin,
        gtins=tuple(sorted(gtins)),
        trailer_declared_products=declared,
    )


def _request_json(params: dict[str, Any]) -> dict[str, Any]:
    query = parse.urlencode(params)
    req = request.Request(
        f"{OFF_SEARCH_URL}?{query}",
        headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
        method="GET",
    )

    last_kind = "unknown"
    for attempt in range(5):
        try:
            with request.urlopen(req, timeout=45) as response:
                payload = json.load(response)
                if not isinstance(payload, dict):
                    raise RuntimeError("Open Food Facts returned a non-object JSON payload")
                return payload
        except error.HTTPError as exc:
            last_kind = f"HTTP {exc.code}"
            if exc.code == 429:
                retry_after = exc.headers.get("Retry-After")
                try:
                    delay = max(10.0, float(retry_after)) if retry_after else 10.0
                except ValueError:
                    delay = 10.0
                time.sleep(delay * (attempt + 1))
                continue
        except (error.URLError, TimeoutError, json.JSONDecodeError, RuntimeError) as exc:
            # Deliberately do not stringify request exceptions: their URL may
            # contain the in-memory GTIN batch.
            last_kind = type(exc).__name__

        if attempt < 4:
            time.sleep(min(30.0, 2.0 ** attempt))

    raise RuntimeError(f"Open Food Facts request failed after retries ({last_kind})")


def fetch_off_products(
    codes: Iterable[str],
    *,
    batch_size: int = DEFAULT_BATCH_SIZE,
    delay_seconds: float = MIN_SEARCH_DELAY_SECONDS,
) -> tuple[dict[str, list[dict[str, Any]]], int]:
    code_list = list(codes)
    if not code_list:
        return {}, 0
    if batch_size < 1 or batch_size > DEFAULT_BATCH_SIZE:
        raise ValueError(f"batch_size must be between 1 and {DEFAULT_BATCH_SIZE}")
    if delay_seconds < MIN_SEARCH_DELAY_SECONDS:
        raise ValueError(
            f"delay_seconds must be at least {MIN_SEARCH_DELAY_SECONDS} "
            "to stay below the documented search rate limit"
        )

    by_code: dict[str, list[dict[str, Any]]] = defaultdict(list)
    api_calls = 0

    for start in range(0, len(code_list), batch_size):
        batch = code_list[start : start + batch_size]
        if api_calls:
            time.sleep(delay_seconds)

        payload = _request_json(
            {
                "code": ",".join(batch),
                "fields": OFF_FIELDS,
                "page": 1,
                "page_size": len(batch),
                "sort_by": "nothing",
            }
        )
        api_calls += 1

        products = payload.get("products", [])
        if not isinstance(products, list):
            raise RuntimeError("Open Food Facts response has no products array")

        batch_set = set(batch)
        for product in products:
            if not isinstance(product, dict):
                continue
            code = _clean_text(product.get("code"))
            if code in batch_set:
                by_code[code].append(product)

    return dict(by_code), api_calls


def build_aggregate_report(
    feed: FeedIdentitySet,
    by_code: dict[str, list[dict[str, Any]]],
    *,
    api_calls: int,
    report_label: str,
    expected_brand: str | None,
) -> dict[str, Any]:
    counts: Counter[str] = Counter()

    for code in feed.gtins:
        resolution = resolve_code(
            by_code.get(code, []),
            expected_brand=expected_brand,
        )
        if not resolution.matched:
            counts["unmatched"] += 1
            continue

        counts["matched"] += 1
        if resolution.expected_brand_seen:
            counts["expected_brand_seen"] += 1
        if resolution.last_modified_present:
            counts["last_modified_present"] += 1

        if resolution.conflict:
            counts["quantity_conflict"] += 1
            continue

        candidate = resolution.candidate
        if candidate is None:
            counts["matched_without_usable_quantity"] += 1
        elif candidate.basis == "displayed_supplement_count":
            counts["usable_exact_supplement_count"] += 1
        elif candidate.basis == "structured_mass_or_volume":
            counts["usable_structured_mass_or_volume"] += 1
        else:
            raise AssertionError("Unexpected quantity basis")

    count_ready = counts["usable_exact_supplement_count"]
    unique = len(feed.gtins)

    return {
        "schema_version": 1,
        "report_label": report_label,
        "research_only": True,
        "production_authorized": False,
        "feed": {
            "product_records": feed.product_records,
            "rows_with_valid_gtin": feed.rows_with_valid_gtin,
            "rows_missing_or_invalid_gtin": feed.rows_missing_or_invalid_gtin,
            "unique_valid_gtins": unique,
            "trailer_declared_products": feed.trailer_declared_products,
        },
        "open_food_facts": {
            "api_calls": api_calls,
            "matched_gtins": counts["matched"],
            "unmatched_gtins": counts["unmatched"],
            "matched_with_expected_brand_text": counts["expected_brand_seen"],
            "matched_with_last_modified_timestamp": counts["last_modified_present"],
            "usable_exact_supplement_count": count_ready,
            "usable_structured_mass_or_volume": counts[
                "usable_structured_mass_or_volume"
            ],
            "quantity_conflicts": counts["quantity_conflict"],
            "matched_without_usable_quantity": counts[
                "matched_without_usable_quantity"
            ],
        },
        "unit_value_readiness": {
            "exact_count_ready_gtins": count_ready,
            "not_exact_count_ready_gtins": unique - count_ready,
            "decision": (
                "Only exact source-displayed supplement counts are candidates "
                "for per-tablet/capsule/gummy value math. Mass/volume metadata "
                "does not substitute for a missing supplement count."
            ),
        },
        "privacy": {
            "gtins_emitted": False,
            "product_rows_emitted": False,
            "provider_credentials_used": False,
            "provider_account_identifiers_emitted": False,
        },
        "trust_boundary": (
            "Open Food Facts metadata remains separately attributed "
            "SOURCE_ASSERTED_METADATA. It does not become merchant-authoritative "
            "Jamieson quantity and cannot upgrade stale or otherwise non-rankable "
            "price evidence."
        ),
    }


def render_markdown(report: dict[str, Any]) -> str:
    feed = report["feed"]
    off = report["open_food_facts"]
    ready = report["unit_value_readiness"]

    lines = [
        f"# {report['report_label']} — Open Food Facts quantity coverage",
        "",
        "Research-only aggregate report. No GTINs, catalog rows, URLs, provider "
        "credentials, or private account identifiers are included.",
        "",
        "## Local authorized feed identity",
        "",
        f"- Product records: {feed['product_records']}",
        f"- Rows with checksum-valid GTIN: {feed['rows_with_valid_gtin']}",
        f"- Missing/invalid GTIN rows: {feed['rows_missing_or_invalid_gtin']}",
        f"- Unique checksum-valid GTINs: {feed['unique_valid_gtins']}",
        f"- Trailer-declared products: {feed['trailer_declared_products']}",
        "",
        "## Open Food Facts metadata coverage",
        "",
        f"- API calls: {off['api_calls']}",
        f"- Matched GTINs: {off['matched_gtins']}",
        f"- Unmatched GTINs: {off['unmatched_gtins']}",
        f"- Exact supplement-count candidates: {off['usable_exact_supplement_count']}",
        f"- Structured mass/volume only: {off['usable_structured_mass_or_volume']}",
        f"- Quantity conflicts: {off['quantity_conflicts']}",
        f"- Matched but no usable quantity: {off['matched_without_usable_quantity']}",
        f"- Matches containing expected brand text: {off['matched_with_expected_brand_text']}",
        f"- Matches with source modification timestamp: {off['matched_with_last_modified_timestamp']}",
        "",
        "## Unit-value readiness",
        "",
        f"- Exact-count-ready GTINs: {ready['exact_count_ready_gtins']}",
        f"- Not exact-count-ready GTINs: {ready['not_exact_count_ready_gtins']}",
        "",
        ready["decision"],
        "",
        "## Trust boundary",
        "",
        report["trust_boundary"],
        "",
        "**Production authorization remains false.** This measurement evaluates "
        "metadata coverage only; it does not grant caching, indexing, display, "
        "mobile, redistribution, or merchant-feed production rights.",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--markdown", required=True, type=Path)
    parser.add_argument("--report-label", default="Authorized Rakuten feed")
    parser.add_argument("--expected-brand", default=None)
    parser.add_argument("--encoding", default="utf-8-sig")
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument(
        "--delay-seconds",
        type=float,
        default=MIN_SEARCH_DELAY_SECONDS,
    )
    args = parser.parse_args()

    feed = extract_feed_gtins(args.input, encoding=args.encoding)
    by_code, api_calls = fetch_off_products(
        feed.gtins,
        batch_size=args.batch_size,
        delay_seconds=args.delay_seconds,
    )
    report = build_aggregate_report(
        feed,
        by_code,
        api_calls=api_calls,
        report_label=args.report_label,
        expected_brand=args.expected_brand,
    )

    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    args.markdown.write_text(render_markdown(report), encoding="utf-8")

    # Console output is aggregate-only as well.
    print(
        "OFF_QUANTITY_COVERAGE_OK "
        f"products={report['feed']['product_records']} "
        f"valid_gtins={report['feed']['unique_valid_gtins']} "
        f"off_matches={report['open_food_facts']['matched_gtins']} "
        f"exact_counts={report['open_food_facts']['usable_exact_supplement_count']} "
        f"conflicts={report['open_food_facts']['quantity_conflicts']}"
    )


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Measure Open Food Facts quantity coverage for an authorized Rakuten feed.

Version 2 fixes a critical lookup-boundary issue discovered by the first real
Jamieson run: Open Food Facts normalizes equivalent barcode representations,
while Rakuten may supply UPC-A / GTIN-12 or leading-zero GTIN-14 values.
Matching a normalized API response code against the raw provider code can
therefore discard a real response.

The tool remains research-only and aggregate-only. Raw provider rows, GTINs,
URLs, credentials and private account identifiers are never written to reports.
"""

from __future__ import annotations

import argparse
import json
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable
from urllib import error, parse, request

from tools.measure_rakuten_off_quantity_coverage import (
    DEFAULT_BATCH_SIZE,
    MIN_SEARCH_DELAY_SECONDS,
    OFF_FIELDS,
    USER_AGENT,
    FeedIdentitySet,
    build_aggregate_report,
    extract_feed_gtins,
    render_markdown,
)
from tools.open_facts_barcode import canonical_open_facts_gtin

OFF_SEARCH_URL = "https://world.openfoodfacts.org/api/v2/search"
OFF_PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product/{code}.json"
MIN_PRODUCT_READ_DELAY_SECONDS = 4.1


class OpenFactsRequestError(RuntimeError):
    """Safe request failure that never exposes a URL or barcode."""

    def __init__(self, kind: str):
        self.kind = kind
        super().__init__(f"Open Food Facts request failed after retries ({kind})")


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
            # Do not stringify request exceptions because their URLs can contain
            # in-memory GTIN batches.
            last_kind = type(exc).__name__

        if attempt < 4:
            time.sleep(min(30.0, 2.0 ** attempt))

    raise OpenFactsRequestError(last_kind)


def _request_product_json(code: str) -> dict[str, Any]:
    """Read one canonical barcode without exposing it in errors or reports."""

    safe_code = parse.quote(code, safe="")
    query = parse.urlencode({"fields": OFF_FIELDS})
    req = request.Request(
        f"{OFF_PRODUCT_URL.format(code=safe_code)}?{query}",
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
            if exc.code == 404:
                return {"status": 0}
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
            # The request URL contains the in-memory canonical GTIN. Never
            # stringify the exception or URL into logs/reports.
            last_kind = type(exc).__name__

        if attempt < 4:
            time.sleep(min(30.0, 2.0 ** attempt))

    raise OpenFactsRequestError(last_kind)


def build_lookup_normalization(codes: Iterable[str]) -> tuple[
    dict[str, tuple[str, ...]],
    dict[str, Any],
]:
    raw_codes = list(codes)
    canonical_to_raw: dict[str, list[str]] = defaultdict(list)
    lengths: Counter[int] = Counter()
    changed = 0

    for raw in raw_codes:
        lengths[len(raw)] += 1
        canonical = canonical_open_facts_gtin(raw)
        if canonical is None:
            raise ValueError("Validated feed GTIN unexpectedly failed canonicalization")
        if canonical != raw:
            changed += 1
        canonical_to_raw[canonical].append(raw)

    collisions = sum(
        1
        for raw_values in canonical_to_raw.values()
        if len(raw_values) > 1
    )

    stats: dict[str, Any] = {
        "input_valid_gtins": len(raw_codes),
        "input_length_distribution": {
            str(length): count for length, count in sorted(lengths.items())
        },
        "canonicalization_changed_gtins": changed,
        "canonical_unique_lookup_gtins": len(canonical_to_raw),
        "canonical_identity_collisions": collisions,
        "note": (
            "Lookup normalization only collapses documented leading-zero barcode "
            "representations. It does not repair invalid GTINs or infer product "
            "identity from names/descriptions."
        ),
    }

    return (
        {key: tuple(values) for key, values in canonical_to_raw.items()},
        stats,
    )


def _store_direct_product(
    canonical_code: str,
    payload: dict[str, Any],
    *,
    canonical_to_raw: dict[str, tuple[str, ...]],
    by_raw_code: dict[str, list[dict[str, Any]]],
) -> bool:
    """Store one direct product response; return True if its code was rejected."""

    if payload.get("status") == 0:
        return False

    product = payload.get("product")
    if not isinstance(product, dict):
        return False

    returned_raw = str(
        product.get("code") or payload.get("code") or canonical_code
    ).strip()
    returned = canonical_open_facts_gtin(returned_raw)
    if returned is None or returned != canonical_code:
        return True

    for source_code in canonical_to_raw[canonical_code]:
        by_raw_code[source_code].append(product)
    return False


def fetch_off_products_normalized(
    codes: Iterable[str],
    *,
    batch_size: int = DEFAULT_BATCH_SIZE,
    delay_seconds: float = MIN_SEARCH_DELAY_SECONDS,
    product_delay_seconds: float = MIN_PRODUCT_READ_DELAY_SECONDS,
) -> tuple[dict[str, list[dict[str, Any]]], int, dict[str, Any]]:
    if batch_size < 1 or batch_size > DEFAULT_BATCH_SIZE:
        raise ValueError(f"batch_size must be between 1 and {DEFAULT_BATCH_SIZE}")
    if delay_seconds < MIN_SEARCH_DELAY_SECONDS:
        raise ValueError(
            f"delay_seconds must be at least {MIN_SEARCH_DELAY_SECONDS} "
            "to stay below the documented search rate limit"
        )
    if product_delay_seconds < MIN_PRODUCT_READ_DELAY_SECONDS:
        raise ValueError(
            f"product_delay_seconds must be at least "
            f"{MIN_PRODUCT_READ_DELAY_SECONDS} to stay below the documented "
            "product-read rate limit"
        )

    canonical_to_raw, stats = build_lookup_normalization(codes)
    canonical_codes = sorted(canonical_to_raw)
    by_raw_code: dict[str, list[dict[str, Any]]] = defaultdict(list)
    api_calls = 0
    search_requests_succeeded = 0
    direct_product_requests = 0
    search_fallback_batches = 0
    response_codes_ignored = 0
    previous_search_attempted = False

    for start in range(0, len(canonical_codes), batch_size):
        batch = canonical_codes[start : start + batch_size]
        if previous_search_attempted:
            time.sleep(delay_seconds)
        previous_search_attempted = True

        try:
            payload = _request_json(
                {
                    "code": ",".join(batch),
                    "fields": OFF_FIELDS,
                    "page": 1,
                    "page_size": len(batch),
                    "sort_by": "nothing",
                }
            )
        except OpenFactsRequestError as exc:
            # Search is an optimization only. Repeated server-side 5xx failures
            # fall back to the documented product-by-barcode read endpoint.
            if not exc.kind.startswith("HTTP 5"):
                raise

            search_fallback_batches += 1
            for index, canonical_code in enumerate(batch):
                if index:
                    time.sleep(product_delay_seconds)
                product_payload = _request_product_json(canonical_code)
                direct_product_requests += 1
                api_calls += 1
                if _store_direct_product(
                    canonical_code,
                    product_payload,
                    canonical_to_raw=canonical_to_raw,
                    by_raw_code=by_raw_code,
                ):
                    response_codes_ignored += 1
            continue

        search_requests_succeeded += 1
        api_calls += 1

        products = payload.get("products", [])
        if not isinstance(products, list):
            raise RuntimeError("Open Food Facts response has no products array")

        batch_set = set(batch)
        for product in products:
            if not isinstance(product, dict):
                continue
            returned_raw = str(product.get("code") or "").strip()
            returned = canonical_open_facts_gtin(returned_raw)
            if returned is None or returned not in batch_set:
                response_codes_ignored += 1
                continue

            for source_code in canonical_to_raw[returned]:
                by_raw_code[source_code].append(product)

    stats["api_calls"] = api_calls
    stats["search_requests_succeeded"] = search_requests_succeeded
    stats["search_fallback_batches"] = search_fallback_batches
    stats["direct_product_requests"] = direct_product_requests
    stats["response_codes_ignored"] = response_codes_ignored
    stats["transport_note"] = (
        "Batch search remains an optimization. If the search endpoint repeatedly "
        "returns HTTP 5xx, the measurement falls back to rate-limited direct "
        "product-by-barcode reads without changing GTIN identity or quantity rules."
    )
    return dict(by_raw_code), api_calls, stats


def build_report_v2(
    feed: FeedIdentitySet,
    by_code: dict[str, list[dict[str, Any]]],
    *,
    api_calls: int,
    report_label: str,
    expected_brand: str | None,
    normalization: dict[str, Any],
) -> dict[str, Any]:
    report = build_aggregate_report(
        feed,
        by_code,
        api_calls=api_calls,
        report_label=report_label,
        expected_brand=expected_brand,
    )
    report["schema_version"] = 2
    report["lookup_normalization"] = normalization
    report["first_run_interpretation"] = (
        "A prior raw-code comparison can undercount Open Food Facts matches when "
        "the provider supplies UPC-A/GTIN representations that Open Food Facts "
        "returns in its canonical leading-zero form. Only this normalized run "
        "should be used for the coverage conclusion."
    )
    return report


def render_markdown_v2(report: dict[str, Any]) -> str:
    normalization = report["lookup_normalization"]
    base = render_markdown(report).rstrip()
    lengths = normalization["input_length_distribution"]
    length_text = ", ".join(
        f"{length}-digit={count}" for length, count in lengths.items()
    ) or "none"

    extra = [
        "",
        "## Barcode-normalization sanity",
        "",
        f"- Valid source GTINs: {normalization['input_valid_gtins']}",
        f"- Source GTIN lengths: {length_text}",
        (
            "- GTINs whose lookup representation changed: "
            f"{normalization['canonicalization_changed_gtins']}"
        ),
        (
            "- Canonical unique lookup GTINs: "
            f"{normalization['canonical_unique_lookup_gtins']}"
        ),
        (
            "- Canonical identity collisions: "
            f"{normalization['canonical_identity_collisions']}"
        ),
        (
            "- Successful batch-search requests: "
            f"{normalization['search_requests_succeeded']}"
        ),
        (
            "- Search batches using direct-read fallback: "
            f"{normalization['search_fallback_batches']}"
        ),
        (
            "- Direct product-read requests: "
            f"{normalization['direct_product_requests']}"
        ),
        (
            "- Response codes ignored after canonical validation: "
            f"{normalization['response_codes_ignored']}"
        ),
        "",
        normalization["note"],
        "",
        normalization["transport_note"],
        "",
        "The earlier raw-code result must not be interpreted as proof of zero "
        "Open Food Facts coverage. Open Food Facts documents leading-zero barcode "
        "normalization, so coverage must be measured after canonical matching.",
        "",
    ]
    return base + "\n" + "\n".join(extra)


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
    parser.add_argument(
        "--product-delay-seconds",
        type=float,
        default=MIN_PRODUCT_READ_DELAY_SECONDS,
    )
    args = parser.parse_args()

    feed = extract_feed_gtins(args.input, encoding=args.encoding)
    by_code, api_calls, normalization = fetch_off_products_normalized(
        feed.gtins,
        batch_size=args.batch_size,
        delay_seconds=args.delay_seconds,
        product_delay_seconds=args.product_delay_seconds,
    )
    report = build_report_v2(
        feed,
        by_code,
        api_calls=api_calls,
        report_label=args.report_label,
        expected_brand=args.expected_brand,
        normalization=normalization,
    )

    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    args.markdown.write_text(render_markdown_v2(report), encoding="utf-8")

    print(
        "OFF_QUANTITY_COVERAGE_V2_OK "
        f"products={report['feed']['product_records']} "
        f"valid_gtins={report['feed']['unique_valid_gtins']} "
        f"normalized_changed={normalization['canonicalization_changed_gtins']} "
        f"off_matches={report['open_food_facts']['matched_gtins']} "
        f"exact_counts={report['open_food_facts']['usable_exact_supplement_count']} "
        f"conflicts={report['open_food_facts']['quantity_conflicts']} "
        f"fallback_batches={normalization['search_fallback_batches']} "
        f"direct_reads={normalization['direct_product_requests']}"
    )


if __name__ == "__main__":
    main()

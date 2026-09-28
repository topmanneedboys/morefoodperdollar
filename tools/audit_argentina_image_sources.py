#!/usr/bin/env python3
"""Audit a bounded Argentina product-picture sample against a public source.

This provider-edge research tool reuses the existing exact product-image
manifest.  It streams the qualified Argentina SEPA observations, keeps only a
bounded deterministic sample, requests metadata for that sample through the
caller-selected Open Food Facts endpoint, and sends the result through the
existing GTIN/name/brand/explicit-quantity and source-rights gates.

The tool never downloads image bytes, never changes Android assets, and never
turns an image URL into a display permission.  Raw sample/API files belong in
the ignored ``local-provider-data/`` tree.  The aggregate report contains no
product rows or URLs and is safe to review as a small qualification artifact.
All dates and source identifiers are caller supplied; the tool has no hidden
wall-clock dependency.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import heapq
import json
import tempfile
import time
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Sequence
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen

from tools.exact_product_image_manifest import (
    ImageManifestError,
    _load_source_rights_policy,
    build_manifest,
)
from tools.open_facts_barcode import canonical_open_facts_gtin


SCHEMA_VERSION = "valuepilot-argentina-image-source-audit-v1"
POLICY_VERSION = "bounded-sample-exact-gtin-name-brand-quantity-v1"
DEFAULT_ENDPOINT = "https://world.openfoodfacts.org/api/v2/search"
DEFAULT_USER_AGENT = "ValuePilot-image-audit/1.0"
MAX_SAMPLE_SIZE = 5_000
MAX_YERBA_MINIMUM = 1_000
MAX_BATCH_SIZE = 100
MAX_RESPONSE_BYTES = 32 * 1024 * 1024
MAX_PRODUCTS_PER_RESPONSE_FACTOR = 3
MAX_DISTINCT_GTINS_TRACKED = 200_000
ALLOWED_API_HOSTS = frozenset(
    (
        "world.openfoodfacts.org",
        "world.openfoodfacts.net",
        "api.openfoodfacts.org",
        "api.openfoodfacts.net",
    )
)
IMAGE_FIELDS = (
    "code",
    "product_name",
    "product_name_en",
    "brands",
    "quantity",
    "image_front_url",
    "image_url",
    "image_ingredients_url",
    "image_nutrition_url",
    "image_packaging_url",
    "image_front_small_url",
    "image_front_thumb_url",
    "image_ingredients_small_url",
    "image_ingredients_thumb_url",
    "image_nutrition_small_url",
    "image_nutrition_thumb_url",
    "image_packaging_small_url",
    "image_packaging_thumb_url",
    "selected_images",
    "image_licenses",
    "image_front_licenses",
    "image_ingredients_licenses",
    "image_nutrition_licenses",
    "image_packaging_licenses",
)


class ImageSourceAuditError(ValueError):
    """The source audit input or response is unsafe or malformed."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ImageSourceAuditError(message)


def _clean(value: Any) -> str:
    return value.strip() if isinstance(value, str) else ""


def _canonical_json(value: Mapping[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _open_observations(path: Path):
    _require(path.is_file(), f"Missing accepted observations: {path}")
    if path.name.casefold().endswith((".gz", ".gzip")):
        return gzip.open(path, "rt", encoding="utf-8", errors="replace", newline="")
    return path.open("r", encoding="utf-8", errors="replace", newline="")


def _is_yerba(record: Mapping[str, Any]) -> bool:
    product = record.get("product")
    if not isinstance(product, Mapping):
        return False
    text = " ".join((_clean(product.get("name")), _clean(product.get("brand")))).casefold()
    return "yerba" in text


def _sample_record(record: Mapping[str, Any], gtin: str) -> dict[str, Any]:
    product = record.get("product")
    source = record.get("source")
    quantity = record.get("quantity")
    _require(isinstance(product, Mapping), "accepted observation product must be an object")
    _require(isinstance(source, Mapping), "accepted observation source must be an object")
    name = _clean(product.get("name"))
    _require(name, "accepted observation product name is missing")
    commerce_id = _clean(source.get("commerce_id")) or "UNKNOWN"
    provider_id = _clean(product.get("provider_product_id")) or gtin
    identity_key = f"ar-sepa-product:{commerce_id}:{provider_id}"
    result: dict[str, Any] = {
        "productEvidenceKey": identity_key,
        "gtin": gtin,
        "name": name,
        "brand": _clean(product.get("brand")),
        "providerProductId": provider_id,
        "commerceId": commerce_id,
    }
    if isinstance(quantity, Mapping):
        unit = _clean(quantity.get("unit"))
        value = _clean(quantity.get("value"))
        if unit and value:
            result["quantity"] = {"unit": unit, "value": value}
    return result


def _score(gtin: str) -> int:
    return int(hashlib.sha256(gtin.encode("ascii")).hexdigest(), 16)


def _offer(
    heap: list[tuple[int, str, str, dict[str, Any]]],
    active: set[str],
    record: dict[str, Any],
    limit: int,
) -> None:
    """Keep the lowest deterministic GTIN ranks with bounded memory."""

    if limit <= 0:
        return
    gtin = str(record["gtin"])
    if gtin in active:
        return
    score = _score(gtin)
    encoded = _canonical_json(record)
    entry = (-score, gtin, encoded, record)
    if len(heap) < limit:
        heapq.heappush(heap, entry)
        active.add(gtin)
        return
    worst = heap[0]
    if (score, gtin) >= (-worst[0], worst[1]):
        return
    removed = heapq.heapreplace(heap, entry)
    active.discard(removed[1])
    active.add(gtin)


def select_sample(
    observations_path: Path,
    *,
    sample_size: int,
    yerba_minimum: int,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """Select a stable, bounded sample of distinct valid GTINs.

    A hash rank avoids retaining the national identity set.  One bounded heap
    reserves a caller-selected yerba-mate minimum; a second heap supplies the
    remaining identities.  The first source row for each currently selected
    GTIN is retained as the exact provider identity evidence.
    """

    _require(1 <= sample_size <= MAX_SAMPLE_SIZE, f"sample size must be 1..{MAX_SAMPLE_SIZE}")
    _require(0 <= yerba_minimum <= min(sample_size, MAX_YERBA_MINIMUM), "yerba minimum is outside bounds")
    general_heap: list[tuple[int, str, str, dict[str, Any]]] = []
    yerba_heap: list[tuple[int, str, str, dict[str, Any]]] = []
    general_active: set[str] = set()
    yerba_active: set[str] = set()
    counters: Counter[str] = Counter()
    distinct_gtins: set[str] = set()
    tracking_complete = True
    handle = _open_observations(observations_path)
    try:
        for line_number, line in enumerate(handle, start=1):
            counters["rows_seen"] += 1
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ImageSourceAuditError(f"accepted observation line {line_number} is malformed") from exc
            if not isinstance(row, Mapping):
                raise ImageSourceAuditError(f"accepted observation line {line_number} must be an object")
            product = row.get("product")
            if not isinstance(product, Mapping) or _clean(product.get("gtin_status")) != "VALID":
                counters["invalid_or_missing_gtin_status"] += 1
                continue
            gtin = canonical_open_facts_gtin(_clean(product.get("gtin")))
            if gtin is None:
                counters["invalid_gtin"] += 1
                continue
            counters["valid_gtin_rows"] += 1
            if len(distinct_gtins) < MAX_DISTINCT_GTINS_TRACKED:
                distinct_gtins.add(gtin)
            else:
                tracking_complete = False
            try:
                candidate = _sample_record(row, gtin)
            except ImageSourceAuditError:
                counters["rows_without_sample_identity"] += 1
                continue
            if _is_yerba(row):
                counters["yerba_valid_rows"] += 1
                _offer(yerba_heap, yerba_active, candidate, min(sample_size, yerba_minimum))
            else:
                _offer(general_heap, general_active, candidate, sample_size)
    finally:
        handle.close()

    yerba_records = [entry[3] for entry in sorted(yerba_heap, key=lambda item: (item[1], item[2]))]
    general_records = [entry[3] for entry in sorted(general_heap, key=lambda item: (item[1], item[2]))]
    selected: list[dict[str, Any]] = []
    selected_gtins: set[str] = set()
    for record in yerba_records:
        if len(selected) >= yerba_minimum:
            break
        if record["gtin"] not in selected_gtins:
            selected.append(record)
            selected_gtins.add(record["gtin"])
    for record in general_records:
        if len(selected) >= sample_size:
            break
        if record["gtin"] not in selected_gtins:
            selected.append(record)
            selected_gtins.add(record["gtin"])
    # If the source has fewer non-yerba rows than requested, use additional
    # yerba rows rather than silently returning a short sample.
    for record in yerba_records:
        if len(selected) >= sample_size:
            break
        if record["gtin"] not in selected_gtins:
            selected.append(record)
            selected_gtins.add(record["gtin"])
    selected.sort(key=lambda item: (item["gtin"], item["productEvidenceKey"]))
    metadata = {
        "sampleSizeRequested": sample_size,
        "sampleSizeSelected": len(selected),
        "sampleDistinctGtins": len(selected_gtins),
        "sampleYerbaIdentities": sum(
            "yerba" in " ".join(
                (_clean(item.get("name")), _clean(item.get("brand")))
            ).casefold()
            for item in selected
        ),
        "sourceRowsSeen": counters["rows_seen"],
        "sourceValidGtinRows": counters["valid_gtin_rows"],
        "sourceYerbaValidRows": counters["yerba_valid_rows"],
        "sourceDistinctGtinsTracked": len(distinct_gtins),
        "sourceDistinctGtinTrackingComplete": tracking_complete,
        "sourceDistinctGtinCountBasis": (
            "bounded distinct canonical GTINs observed in the accepted-observation "
            "stream; not the consumer catalog denominator"
        ),
        "selection": "lowest-sha256-gtin-rank-with-bounded-yerba-reserve",
    }
    _require(len(selected) == sample_size, "source did not contain enough distinct valid GTINs for the requested sample")
    _require(metadata["sampleYerbaIdentities"] >= yerba_minimum, "source did not contain the requested yerba sample")
    return selected, metadata


def _request_batch(
    endpoint: str,
    codes: Sequence[str],
    *,
    user_agent: str,
    timeout_seconds: float,
    opener: Callable[..., Any] = urlopen,
) -> dict[str, Any]:
    _require(codes, "API batch cannot be empty")
    params = {
        "code": ",".join(codes),
        "fields": ",".join(IMAGE_FIELDS),
        "page_size": str(len(codes)),
    }
    separator = "&" if "?" in endpoint else "?"
    request = Request(
        f"{endpoint}{separator}{urlencode(params)}",
        headers={"Accept": "application/json", "User-Agent": user_agent},
        method="GET",
    )
    try:
        with opener(request, timeout=timeout_seconds) as response:
            status = int(getattr(response, "status", 200))
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except Exception as exc:  # pragma: no cover - exercised through injected opener
        raise ImageSourceAuditError(f"Open Food Facts request failed: {exc}") from exc
    _require(status == 200, f"Open Food Facts request returned HTTP {status}")
    _require(len(body) <= MAX_RESPONSE_BYTES, "Open Food Facts response exceeded the size bound")
    try:
        payload = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ImageSourceAuditError("Open Food Facts response is not valid UTF-8 JSON") from exc
    _require(isinstance(payload, Mapping), "Open Food Facts response must be an object")
    products = payload.get("products")
    _require(isinstance(products, list), "Open Food Facts response is missing products")
    _require(
        len(products) <= len(codes) * MAX_PRODUCTS_PER_RESPONSE_FACTOR,
        "Open Food Facts response returned too many products for the requested batch",
    )
    return dict(payload)


def _write_jsonl_atomic(path: Path, rows: Iterable[Mapping[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    _require(not path.exists(), f"Refusing to overwrite {path}")
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", newline="\n", dir=path.parent,
        prefix=f".{path.name}.", suffix=".tmp", delete=False,
    ) as handle:
        temporary = Path(handle.name)
        for row in rows:
            handle.write(_canonical_json(row) + "\n")
        handle.flush()
    temporary.replace(path)


def _write_json_atomic(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    _require(not path.exists(), f"Refusing to overwrite {path}")
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", newline="\n", dir=path.parent,
        prefix=f".{path.name}.", suffix=".tmp", delete=False,
    ) as handle:
        temporary = Path(handle.name)
        handle.write(_canonical_json(payload) + "\n")
        handle.flush()
    temporary.replace(path)


def _load_coverage_reference(path: Path | None) -> dict[str, Any] | None:
    if path is None:
        return None
    _require(path.is_file(), f"Missing coverage reference: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ImageSourceAuditError(f"Coverage reference is not valid JSON: {path}") from exc
    _require(isinstance(value, Mapping), "coverage reference must be an object")
    catalog = value.get("catalog")
    coverage = value.get("coverage")
    _require(isinstance(catalog, Mapping) and isinstance(coverage, Mapping), "coverage reference is missing catalog/coverage")
    return {
        "report": str(path),
        "catalogRecordsSeen": catalog.get("records_seen"),
        "catalogValidGtinRecords": catalog.get("valid_gtin_records"),
        "catalogUniqueGtins": catalog.get("unique_gtin_values"),
        "catalogYerbaRecords": catalog.get("yerba_records"),
    }


def audit_sample(
    observations_path: Path,
    output_dir: Path,
    *,
    sample_size: int,
    yerba_minimum: int,
    endpoint: str,
    evaluated_at: str,
    source_release: str,
    source_sha256: str | None,
    coverage_reference: Path | None,
    source_rights_policy: Path | None,
    batch_size: int,
    min_request_interval_seconds: float,
    user_agent: str,
    timeout_seconds: float,
    opener: Callable[..., Any] = urlopen,
) -> dict[str, Any]:
    try:
        parsed_evaluated_at = datetime.fromisoformat(evaluated_at.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ImageSourceAuditError("evaluated-at must be an ISO-8601 timestamp") from exc
    _require(parsed_evaluated_at.tzinfo is not None, "evaluated-at must include an explicit timezone")
    _require(1 <= batch_size <= MAX_BATCH_SIZE, f"batch size must be 1..{MAX_BATCH_SIZE}")
    _require(min_request_interval_seconds >= 0, "request interval cannot be negative")
    parsed_endpoint = urlparse(endpoint)
    _require(parsed_endpoint.scheme == "https", "source endpoint must use HTTPS")
    _require(parsed_endpoint.hostname in ALLOWED_API_HOSTS, "source endpoint is not an approved Open Food Facts host")
    if source_sha256 is not None:
        _require(len(source_sha256) == 64 and all(ch in "0123456789abcdefABCDEF" for ch in source_sha256), "source SHA-256 is invalid")

    sample, sample_metadata = select_sample(
        observations_path, sample_size=sample_size, yerba_minimum=yerba_minimum
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    sample_catalog_path = output_dir / "sample-catalog.json"
    sample_api_path = output_dir / "open-food-facts-sample.jsonl"
    manifest_path = output_dir / "image-evidence-manifest.jsonl"
    report_path = output_dir / "report.json"
    _write_json_atomic(sample_catalog_path, {"records": sample})

    codes = [str(item["gtin"]) for item in sample]
    products_by_code: dict[str, dict[str, Any]] = {}
    request_count = 0
    response_product_count = 0
    missing_codes: set[str] = set(codes)
    for start in range(0, len(codes), batch_size):
        batch = codes[start : start + batch_size]
        if request_count and min_request_interval_seconds:
            time.sleep(min_request_interval_seconds)
        payload = _request_batch(
            endpoint,
            batch,
            user_agent=user_agent,
            timeout_seconds=timeout_seconds,
            opener=opener,
        )
        request_count += 1
        products = payload["products"]
        response_product_count += len(products)
        for product in products:
            if not isinstance(product, Mapping):
                continue
            code = canonical_open_facts_gtin(_clean(product.get("code")))
            if code is None or code not in missing_codes:
                continue
            if code not in products_by_code:
                products_by_code[code] = dict(product)
                missing_codes.discard(code)
    ordered_products = [products_by_code[code] for code in codes if code in products_by_code]
    _write_jsonl_atomic(sample_api_path, ordered_products)
    source_policy = _load_source_rights_policy(source_rights_policy) if source_rights_policy else None
    manifest_report = build_manifest(
        sample_catalog_path,
        sample_api_path,
        manifest_path,
        source_rights_policy=source_policy,
    )
    report: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "evaluatedAt": evaluated_at,
        "sourceRelease": source_release,
        "source": {
            "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
            "acceptedObservations": str(observations_path),
            "acceptedObservationsSha256": source_sha256,
            "rawProviderDataCommitted": False,
        },
        "coverageReference": _load_coverage_reference(coverage_reference),
        "sample": sample_metadata,
        "api": {
            "endpoint": endpoint,
            "userAgent": user_agent,
            "batchSize": batch_size,
            "requestCount": request_count,
            "responseProductCount": response_product_count,
            "sampleProductsReturned": len(ordered_products),
            "sampleProductsMissing": len(missing_codes),
            "missingCodeCount": len(missing_codes),
            "responseDataSha256": _sha256(sample_api_path),
            "imageBytesDownloaded": False,
        },
        "manifest": {
            "counts": manifest_report["counts"],
            "rejections": manifest_report["rejections"],
            "coverage": manifest_report["coverage"],
            "rightsEvidence": manifest_report["rightsEvidence"],
            "displayEligibility": manifest_report["displayEligibility"],
            "manifestSha256": _sha256(manifest_path),
        },
        "rights": {
            "sourcePolicy": source_policy,
            "commercialDisplayClaim": False,
            "thirdPartyRightsReviewRequired": True,
            "imageBytesDownloaded": False,
            "priceOrAvailabilityClaim": False,
        },
        "artifacts": {
            "sampleCatalog": str(sample_catalog_path),
            "sampleApi": str(sample_api_path),
            "evidenceManifest": str(manifest_path),
            "rawArtifactsIgnored": True,
        },
    }
    _write_json_atomic(report_path, report)
    return report


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accepted-observations", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--sample-size", type=int, default=1_000)
    parser.add_argument("--yerba-minimum", type=int, default=100)
    parser.add_argument("--api-endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--evaluated-at", required=True)
    parser.add_argument("--source-release", required=True)
    parser.add_argument("--source-sha256")
    parser.add_argument("--coverage-reference", type=Path)
    parser.add_argument("--source-rights-policy", type=Path)
    parser.add_argument("--batch-size", type=int, default=100)
    parser.add_argument("--min-request-interval-seconds", type=float, default=6.0)
    parser.add_argument("--user-agent", default=DEFAULT_USER_AGENT)
    parser.add_argument("--timeout-seconds", type=float, default=60.0)
    args = parser.parse_args(argv)
    try:
        report = audit_sample(
            args.accepted_observations,
            args.output_dir,
            sample_size=args.sample_size,
            yerba_minimum=args.yerba_minimum,
            endpoint=args.api_endpoint,
            evaluated_at=args.evaluated_at,
            source_release=args.source_release,
            source_sha256=args.source_sha256,
            coverage_reference=args.coverage_reference,
            source_rights_policy=args.source_rights_policy,
            batch_size=args.batch_size,
            min_request_interval_seconds=args.min_request_interval_seconds,
            user_agent=args.user_agent,
            timeout_seconds=args.timeout_seconds,
        )
    except (ImageSourceAuditError, ImageManifestError, OSError) as exc:
        print(f"Argentina image-source audit failed: {exc}")
        return 2
    print(_canonical_json(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

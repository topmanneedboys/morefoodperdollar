#!/usr/bin/env python3
"""Run the bounded, manually labelled Spanish CABA search audit offline."""

from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
from typing import Any, Iterable, Mapping

try:
    from tools.argentina_sepa_search import MAX_CANDIDATES, SearchError, evaluate_audit, search_products
    from tools.build_argentina_sepa_regional_snapshot import EXPECTED_OUTER_SHA256, RELEASE_DATE, REGION_ID, _valid_gtin
except ModuleNotFoundError:  # direct ``python tools/audit_...py`` invocation
    from argentina_sepa_search import MAX_CANDIDATES, SearchError, evaluate_audit, search_products
    from build_argentina_sepa_regional_snapshot import EXPECTED_OUTER_SHA256, RELEASE_DATE, REGION_ID, _valid_gtin


def _canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _load_fixture(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SearchError(f"invalid audit fixture: {path}") from exc
    if not isinstance(value, dict):
        raise SearchError("audit fixture must be an object")
    return value


def _iter_products(path: Path) -> Iterable[Mapping[str, Any]]:
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rb") as handle:
        for line_number, line in enumerate(handle, start=1):
            try:
                value = json.loads(line.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise SearchError(f"product line {line_number} is invalid JSON") from exc
            if not isinstance(value, dict):
                raise SearchError(f"product line {line_number} is not an object")
            yield value


def _fixture_rows(fixture: Mapping[str, Any]) -> list[Mapping[str, Any]]:
    queries = fixture.get("queries")
    if not isinstance(queries, list) or not queries:
        raise SearchError("audit fixture queries are missing")
    rows: list[Mapping[str, Any]] = []
    seen_keys: set[tuple[str, str]] = set()
    for entry in queries:
        if not isinstance(entry, dict) or not isinstance(entry.get("query"), str) or not isinstance(entry.get("candidates"), list):
            raise SearchError("audit fixture query entry is invalid")
        query = entry["query"]
        for candidate in entry["candidates"]:
            if not isinstance(candidate, dict):
                raise SearchError("audit fixture candidate is invalid")
            required = {"productEvidenceKey", "name", "relevant", "rationale"}
            if not required <= set(candidate):
                raise SearchError("audit fixture candidate is missing a review field")
            if not isinstance(candidate["productEvidenceKey"], str) or not candidate["productEvidenceKey"]:
                raise SearchError("audit fixture candidate key is invalid")
            if not isinstance(candidate["name"], str) or not candidate["name"]:
                raise SearchError("audit fixture candidate name is invalid")
            if not isinstance(candidate["relevant"], bool) or not isinstance(candidate["rationale"], str) or not candidate["rationale"].strip():
                raise SearchError("audit fixture candidate review fields are invalid")
            if "gtin" in candidate and candidate["gtin"] is not None:
                if not isinstance(candidate["gtin"], str) or not _valid_gtin(candidate["gtin"]):
                    raise SearchError("audit fixture candidate GTIN is invalid")
            if any(field in candidate for field in ("price", "listPrice", "offer", "availability")):
                raise SearchError("search audit fixture must not contain prices or offers")
            key = (query, candidate["productEvidenceKey"])
            if key in seen_keys:
                raise SearchError("duplicate audit candidate")
            seen_keys.add(key)
            rows.append({"query": query, **candidate})
    return rows


def audit_search(products_path: Path, fixture_path: Path) -> dict[str, Any]:
    fixture = _load_fixture(fixture_path)
    _fixture_source = fixture.get("source")
    if not isinstance(_fixture_source, Mapping) or _fixture_source.get("provider") != "ARGENTINA_SEPA_PRECIOS_CLAROS" or _fixture_source.get("releaseDate") != RELEASE_DATE or _fixture_source.get("outerSha256") != EXPECTED_OUTER_SHA256 or fixture.get("regionId") != REGION_ID:
        raise SearchError("audit fixture source provenance is invalid")
    rows = _fixture_rows(fixture)
    products: list[Mapping[str, Any]] = []
    for product in _iter_products(products_path):
        if len(products) >= MAX_CANDIDATES:
            raise SearchError(f"product table exceeds bounded search limit ({MAX_CANDIDATES})")
        products.append(product)
    products_by_key = {product.get("productEvidenceKey"): product for product in products if isinstance(product.get("productEvidenceKey"), str)}
    keys = set(products_by_key)
    for row in rows:
        if row["productEvidenceKey"] not in keys:
            raise SearchError(f"audit candidate is not present in the product table: {row['productEvidenceKey']}")
        source_product = products_by_key[row["productEvidenceKey"]]
        if row["name"] != source_product.get("name"):
            raise SearchError(f"audit candidate name does not match product table: {row['productEvidenceKey']}")
        for field in ("brand", "gtin"):
            if field in row and row[field] != source_product.get(field):
                raise SearchError(f"audit candidate {field} does not match product table: {row['productEvidenceKey']}")
    result = evaluate_audit(products, rows, limit=5)
    labels = {(row["query"], row["productEvidenceKey"]): bool(row["relevant"]) for row in rows}
    missing_top_k: list[dict[str, Any]] = []
    for query in sorted({row["query"] for row in rows}):
        top = search_products(products, query, limit=5)
        for item in top:
            if (query, item.product_evidence_key) not in labels:
                missing_top_k.append({"query": query, "productEvidenceKey": item.product_evidence_key, "name": item.name})
    result["unreviewedTopK"] = missing_top_k
    positive_counts: dict[str, int] = {}
    for row in rows:
        if row["relevant"]:
            positive_counts[row["query"]] = positive_counts.get(row["query"], 0) + 1
    result["insufficientAuditedPositives"] = sorted(query for query in positive_counts if positive_counts[query] < 3)
    overall_precision = float(result["overallPrecisionAt5"])
    per_query_precision = [float(row["precisionAt5"]) for row in result["queries"]]
    result["status"] = "GO" if not missing_top_k and overall_precision >= 0.95 and all(value >= 0.80 for value in per_query_precision) else "SEARCH_NOT_YET_QUALIFIED"
    result["knownFalsePositiveRegressionClasses"] = [
        "pan-in-unrelated-name",
        "condimento-para-arroz",
        "fideos-con-huevo",
        "gaseosa-sin-azucar",
        "pasta-rellena-de-queso",
        "yogur-con-cereal",
    ]
    audited_relevant_keys = {row["productEvidenceKey"] for row in rows if row["relevant"]}
    # Product-evidence identity and GTIN are deliberately separate metrics.
    # The source product table is authoritative for the GTIN; the fixture's
    # optional GTIN field was already checked against it above.
    audited_relevant_gtins = {
        products_by_key[key].get("gtin")
        for key in audited_relevant_keys
        if isinstance(products_by_key[key].get("gtin"), str)
        and _valid_gtin(products_by_key[key]["gtin"])
    }
    gtin_commerce: dict[str, set[str]] = {}
    for product in products:
        gtin = product.get("gtin")
        commerce = product.get("commerceId")
        if isinstance(gtin, str) and gtin and isinstance(commerce, str):
            gtin_commerce.setdefault(gtin, set()).add(commerce)
    exact = sorted(gtin for gtin in audited_relevant_gtins if len(gtin_commerce.get(gtin, set())) >= 2)
    result["auditedRelevantProductEvidenceIdentities"] = len(audited_relevant_keys)
    result["auditedRelevantIdentitiesCarryingValidGtin"] = sum(
        1
        for key in audited_relevant_keys
        if isinstance(products_by_key[key].get("gtin"), str)
        and _valid_gtin(products_by_key[key]["gtin"])
    )
    result["distinctValidGtinsRepresentedByAuditedRelevantIdentities"] = len(audited_relevant_gtins)
    result["distinctGtinsWithExactCrossRetailerAvailability"] = len(exact)
    result["distinctGtinsWithoutExactCrossRetailerAvailability"] = len(audited_relevant_gtins) - len(exact)
    result["exactCrossRetailerGtins"] = exact
    result["fixtureSha256"] = __import__("hashlib").sha256(fixture_path.read_bytes()).hexdigest()
    return result


def _markdown(result: Mapping[str, Any]) -> str:
    lines = [
        "# Argentina CABA Spanish product-search audit",
        "",
        f"Status: **{result['status']}**",
        "",
        f"Queries audited: **{result['queryCount']}**",
        f"Overall precision@5: **{result['overallPrecisionAt5']}**",
        f"Audited relevant product-evidence identities: **{result['auditedRelevantProductEvidenceIdentities']}**",
        f"Relevant identities carrying valid GTIN: **{result['auditedRelevantIdentitiesCarryingValidGtin']}**",
        f"Distinct valid GTINs represented: **{result['distinctValidGtinsRepresentedByAuditedRelevantIdentities']}**",
        f"Distinct GTINs with exact cross-retailer availability: **{result['distinctGtinsWithExactCrossRetailerAvailability']}**",
        f"Distinct GTINs without exact cross-retailer availability: **{result['distinctGtinsWithoutExactCrossRetailerAvailability']}**",
        "",
        "This finite fixture measures precision only; it makes no recall or universal-category claim.",
        "",
        "Known false-positive regression classes: " + ", ".join(result["knownFalsePositiveRegressionClasses"]) + ".",
        "",
        "## Per-query results",
        "",
        "| Query | Top-5 precision | False positives |",
        "| --- | ---: | --- |",
    ]
    for row in result["queries"]:
        false_positives = ", ".join(row["falsePositives"]) if row["falsePositives"] else "None"
        lines.append(f"| {row['query']} | {row['precisionAt5']} | {false_positives} |")
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--products", required=True, type=Path)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-markdown", type=Path)
    args = parser.parse_args(argv)
    try:
        result = audit_search(args.products, args.fixture)
    except (SearchError, OSError, ValueError) as exc:
        print(f"search audit failed: {exc}", file=__import__("sys").stderr)
        return 2
    args.output_json.write_bytes(_canonical_json(result))
    if args.output_markdown is not None:
        args.output_markdown.write_text(_markdown(result), encoding="utf-8")
    # Keep the CLI diagnostic portable on Windows consoles that still use a
    # legacy code page; the artifact itself remains UTF-8/Unicode.
    print(json.dumps(result, ensure_ascii=True, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

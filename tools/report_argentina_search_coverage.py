"""Build a deterministic search-coverage report for one Argentina release.

The release pipeline is intentionally frozen, so this tool is run after a
release is qualified.  It combines the taxonomy builder report, the fixed
relevance query set, and optional quantity/unit-price audit results into one
machine-readable report with an optional markdown summary.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

from tools.product_taxonomy import classify_name

SCHEMA_VERSION = "valuepilot-argentina-search-coverage-v1"


class CoverageReportError(ValueError):
    """Raised when an input report cannot produce a valid coverage report."""


def _percent(part: int, whole: int) -> float:
    return round(100 * part / whole, 2) if whole else 0.0


def evaluate_relevance(queries: Sequence[Mapping[str, Any]]) -> dict[str, Any]:
    failures: list[dict[str, Any]] = []
    total = 0
    for item in queries:
        if not isinstance(item, Mapping):
            raise CoverageReportError("relevance query is invalid")
        query = item.get("query")
        expected = item.get("department")
        if not isinstance(query, str) or not query.strip() or not isinstance(expected, str) or not expected.strip():
            raise CoverageReportError("relevance query needs query and department")
        total += 1
        actual = classify_name(query).get("primaryDepartment")
        if actual != expected:
            failures.append({"query": query, "expected": expected, "actual": actual})
    matched = total - len(failures)
    return {
        "queries": total,
        "matched": matched,
        "percent": _percent(matched, total),
        "failures": failures,
    }


def build_report(
    *,
    taxonomy_report: Mapping[str, Any],
    queries: Sequence[Mapping[str, Any]],
    quantity: Mapping[str, Any] | None = None,
    unit_price: Mapping[str, Any] | None = None,
    release_id: str | None = None,
) -> dict[str, Any]:
    coverage = taxonomy_report.get("coverage") if isinstance(taxonomy_report, Mapping) else None
    if not isinstance(coverage, Mapping):
        raise CoverageReportError("taxonomy report has no coverage section")
    products = int(coverage.get("providerProductIds") or 0)
    if products <= 0:
        raise CoverageReportError("taxonomy report has no products")
    status_counts = coverage.get("statusCounts") or {}
    classified = int(status_counts.get("CLASSIFIED") or 0)
    review = int(status_counts.get("REVIEW_REQUIRED") or 0)
    with_subcategory = int(coverage.get("productsWithPrimarySubcategory") or 0)

    departments: list[dict[str, Any]] = []
    for key, count in (coverage.get("departmentCandidateCounts") or {}).items():
        if str(key).startswith("candidate:"):
            continue
        departments.append({"key": str(key), "count": int(count)})
    departments.sort(key=lambda item: (-item["count"], item["key"]))

    report: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "releaseId": release_id or str(taxonomy_report.get("source", {}).get("releaseId") or ""),
        "catalogue": {
            "providerProducts": products,
            "classified": classified,
            "classifiedPercent": _percent(classified, products),
            "reviewRequired": review,
            "reviewPercent": _percent(review, products),
            "withSubcategory": with_subcategory,
            "subcategoryPercent": _percent(with_subcategory, products),
        },
        "departments": departments,
        "relevance": evaluate_relevance(queries),
    }
    if quantity is not None:
        rows = int(quantity.get("rows") or 0)
        unknown = int(quantity.get("unknown") or 0)
        if rows <= 0 or unknown < 0 or unknown > rows:
            raise CoverageReportError("quantity audit is invalid")
        report["quantity"] = {
            "rows": rows,
            "unknown": unknown,
            "parsedPercent": _percent(rows - unknown, rows),
        }
    if unit_price is not None:
        compared = int(unit_price.get("compared") or 0)
        mismatched = int(unit_price.get("mismatched") or 0)
        if compared < 0 or mismatched < 0 or mismatched > compared:
            raise CoverageReportError("unit price audit is invalid")
        report["unitPrice"] = {
            "compared": compared,
            "mismatched": mismatched,
            "agreementPercent": _percent(compared - mismatched, compared) if compared else 100.0,
        }
    return report


def render_markdown(report: Mapping[str, Any]) -> str:
    catalogue = report["catalogue"]
    relevance = report["relevance"]
    lines = [
        "# Argentina search coverage report",
        "",
        f"- Release: `{report.get('releaseId') or 'unknown'}`",
        f"- Products: **{catalogue['providerProducts']}**",
        f"- Auto-classified: **{catalogue['classified']} ({catalogue['classifiedPercent']}%)**",
        f"- Review required: **{catalogue['reviewRequired']} ({catalogue['reviewPercent']}%)**",
        f"- With subcategory: **{catalogue['withSubcategory']} ({catalogue['subcategoryPercent']}%)**",
        f"- Relevance set: **{relevance['matched']}/{relevance['queries']} ({relevance['percent']}%)**",
    ]
    if "quantity" in report:
        quantity = report["quantity"]
        lines.append(f"- Quantity parsed: **{quantity['rows'] - quantity['unknown']}/{quantity['rows']} ({quantity['parsedPercent']}%)**")
    if "unitPrice" in report:
        unit = report["unitPrice"]
        lines.append(f"- Unit price agreement: **{unit['compared'] - unit['mismatched']}/{unit['compared']} ({unit['agreementPercent']}%)**")
    if relevance["failures"]:
        lines.extend(["", "## Relevance failures", ""])
        for failure in relevance["failures"]:
            lines.append(f"- `{failure['query']}` expected {failure['expected']}, got {failure['actual']}")
    lines.extend(["", "## Departments", ""])
    for department in report["departments"][:30]:
        lines.append(f"- {department['key']}: {department['count']}")
    return "\n".join(lines) + "\n"


def _read_json(path: str | Path) -> Any:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CoverageReportError(f"cannot read {path}") from exc


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--taxonomy-report", required=True)
    parser.add_argument("--queries", required=True)
    parser.add_argument("--quantity-json")
    parser.add_argument("--unit-price-json")
    parser.add_argument("--release-id")
    parser.add_argument("--output", required=True)
    parser.add_argument("--markdown")
    args = parser.parse_args(argv)

    queries_document = _read_json(args.queries)
    queries = queries_document.get("queries") if isinstance(queries_document, Mapping) else None
    if not isinstance(queries, Sequence) or isinstance(queries, (str, bytes)):
        raise CoverageReportError("queries file has no queries list")
    report = build_report(
        taxonomy_report=_read_json(args.taxonomy_report),
        queries=queries,
        quantity=_read_json(args.quantity_json) if args.quantity_json else None,
        unit_price=_read_json(args.unit_price_json) if args.unit_price_json else None,
        release_id=args.release_id,
    )
    Path(args.output).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.markdown:
        Path(args.markdown).write_text(render_markdown(report), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

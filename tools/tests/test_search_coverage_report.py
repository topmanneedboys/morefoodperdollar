from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.report_argentina_search_coverage import (
    CoverageReportError,
    build_report,
    evaluate_relevance,
    main,
    render_markdown,
)


class SearchCoverageReportTests(unittest.TestCase):
    def _taxonomy_report(self) -> dict:
        return {
            "source": {"releaseId": "fixture-release"},
            "coverage": {
                "providerProductIds": 10,
                "statusCounts": {"CLASSIFIED": 7, "REVIEW_REQUIRED": 3},
                "productsWithPrimarySubcategory": 5,
                "departmentCandidateCounts": {
                    "dairy": 4,
                    "meat_seafood": 3,
                    "candidate:dairy": 4,
                },
            },
        }

    def test_build_report_combines_catalogue_relevance_and_audits(self) -> None:
        report = build_report(
            taxonomy_report=self._taxonomy_report(),
            queries=[
                {"query": "leche", "department": "dairy"},
                {"query": "vino", "department": "dairy"},
            ],
            quantity={"rows": 100, "unknown": 1},
            unit_price={"compared": 10, "mismatched": 0},
        )

        self.assertEqual(report["catalogue"]["classifiedPercent"], 70.0)
        self.assertEqual(report["catalogue"]["subcategoryPercent"], 50.0)
        self.assertEqual(report["relevance"]["matched"], 1)
        self.assertEqual(report["relevance"]["failures"][0]["query"], "vino")
        self.assertEqual(report["quantity"]["parsedPercent"], 99.0)
        self.assertEqual(report["unitPrice"]["agreementPercent"], 100.0)
        self.assertEqual([item["key"] for item in report["departments"]], ["dairy", "meat_seafood"])

    def test_relevance_requires_a_department(self) -> None:
        with self.assertRaisesRegex(CoverageReportError, "department"):
            evaluate_relevance([{"query": "leche"}])

    def test_markdown_summarizes_the_report(self) -> None:
        report = build_report(
            taxonomy_report=self._taxonomy_report(),
            queries=[{"query": "leche", "department": "dairy"}],
        )
        markdown = render_markdown(report)
        self.assertIn("Auto-classified", markdown)
        self.assertIn("fixture-release", markdown)

    def test_cli_writes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "taxonomy.json").write_text(json.dumps(self._taxonomy_report()), encoding="utf-8")
            (root / "queries.json").write_text(
                json.dumps({"queries": [{"query": "leche", "department": "dairy"}]}),
                encoding="utf-8",
            )
            exit_code = main([
                "--taxonomy-report", str(root / "taxonomy.json"),
                "--queries", str(root / "queries.json"),
                "--output", str(root / "coverage.json"),
                "--markdown", str(root / "coverage.md"),
            ])
            self.assertEqual(exit_code, 0)
            report = json.loads((root / "coverage.json").read_text(encoding="utf-8"))
            self.assertEqual(report["relevance"]["matched"], 1)
            self.assertTrue((root / "coverage.md").is_file())


if __name__ == "__main__":
    unittest.main()

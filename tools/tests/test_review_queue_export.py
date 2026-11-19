from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

from tools.export_argentina_review_queue import (
    HEADER,
    ReviewExportError,
    export_review_queue,
    main,
)


class ReviewQueueExportTests(unittest.TestCase):
    def test_exports_rows_with_suggestion_and_candidates(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "review.jsonl"
            source.write_text(
                json.dumps({
                    "providerProductId": "1",
                    "name": "GALLETITA ACME",
                    "brand": "ACME",
                    "suggestedDepartment": "snacks_sweets",
                    "suggestionSource": "BRAND_ASSOCIATION",
                    "departmentCandidates": [{"key": "snacks_sweets"}],
                }, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )
            output = root / "review.csv"
            count = export_review_queue(source, output)

            self.assertEqual(count, 1)
            with output.open(encoding="utf-8", newline="") as handle:
                rows = list(csv.reader(handle))
            self.assertEqual(tuple(rows[0]), HEADER)
            self.assertEqual(rows[1][0], "1")
            self.assertEqual(rows[1][3], "snacks_sweets")
            self.assertEqual(rows[1][4], "BRAND_ASSOCIATION")
            self.assertEqual(rows[1][5], "snacks_sweets")

    def test_rejects_an_invalid_row(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "review.jsonl"
            source.write_text("not-json\n", encoding="utf-8")
            with self.assertRaisesRegex(ReviewExportError, "invalid JSON"):
                export_review_queue(source, root / "review.csv")

    def test_enforces_the_row_limit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "review.jsonl"
            source.write_text(
                "\n".join(json.dumps({"providerProductId": str(i)}) for i in range(3)) + "\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ReviewExportError, "row limit"):
                export_review_queue(source, root / "review.csv", max_rows=2)

    def test_cli_writes_the_csv(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "review.jsonl"
            source.write_text(json.dumps({"providerProductId": "1", "name": "X"}) + "\n", encoding="utf-8")
            exit_code = main(["--input", str(source), "--output", str(root / "review.csv")])
            self.assertEqual(exit_code, 0)
            self.assertTrue((root / "review.csv").is_file())


if __name__ == "__main__":
    unittest.main()

"""Fixed shopper-query relevance set for department intent.

The set is deliberately small and reviewable. It measures whether a bare
shopper query lands in the expected department before any result ranking is
applied, and it is the baseline the category work must not regress.
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path

from tools.product_taxonomy import classify_name


ROOT = Path(__file__).parents[2]
QUERIES = ROOT / "tools" / "data" / "argentina_search_relevance_queries.json"


class SearchRelevanceSetTests(unittest.TestCase):
    def test_every_query_resolves_to_expected_department(self) -> None:
        data = json.loads(QUERIES.read_text(encoding="utf-8"))
        cases = data["queries"]
        self.assertGreaterEqual(len(cases), 70)
        failures = []
        for case in cases:
            query = case["query"]
            expected = case["department"]
            classified = classify_name(query)
            departments = [item["key"] for item in classified["departmentCandidates"]]
            if not departments or departments[0] != expected:
                failures.append((query, expected, departments[:3]))
        self.assertEqual(failures, [], f"queries resolving to the wrong department: {failures}")


if __name__ == "__main__":
    unittest.main()

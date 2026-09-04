from __future__ import annotations

import csv
import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.select_open_food_facts_launch_catalog import (
    OpenFoodFactsLaunchSelectionError,
    _household_hint,
    select_catalog,
)


class OpenFoodFactsLaunchSelectionTest(unittest.TestCase):
    def write_tsv_gz(self, path: Path, rows: list[dict[str, str]]) -> None:
        fields = [
            "code",
            "product_name",
            "product_name_en",
            "brands",
            "countries_tags",
            "countries_en",
            "categories_en",
            "popularity_tags",
            "unique_scans_n",
            "completeness",
            "price",
            "quantity",
        ]
        with gzip.open(path, "wt", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t")
            writer.writeheader()
            writer.writerows({field: row.get(field, "") for field in fields} for row in rows)

    def test_canada_selection_is_bounded_deterministic_and_identity_only(self):
        rows = [
            {
                "code": "036000291452",
                "product_name": "Whole milk",
                "product_name_en": "Whole Milk",
                "brands": "Dairy Co",
                "countries_tags": "en:canada",
                "categories_en": "Dairy products,Milks",
                "popularity_tags": "top-country-ca-scans-2024",
                "unique_scans_n": "20",
                "completeness": "0.8",
                "price": "9.99",
                "quantity": "2 L",
            },
            {
                "code": "4006381333931",
                "product_name": "Tea Bags",
                "brands": "Tea Co",
                "countries_en": "Canada",
                "categories_en": "Tea,Beverages",
                "unique_scans_n": "4",
                "completeness": "0.7",
            },
            {
                "code": "0036002914529",
                "product_name": "Not selected by bound",
                "countries_tags": "en:canada",
                "unique_scans_n": "1",
            },
            {
                "code": "036000291453",
                "product_name": "Invalid code",
                "countries_tags": "en:canada",
            },
            {
                "code": "036000291452",
                "product_name": "Duplicate representation",
                "countries_tags": "en:canada",
            },
            {
                "code": "4006381333931",
                "product_name": "French tea",
                "countries_en": "France",
            },
        ]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "off.csv.gz"
            self.write_tsv_gz(source, rows)
            first = root / "first.jsonl"
            first_report = root / "first-report.json"
            second = root / "second.jsonl"
            second_report = root / "second-report.json"
            report_one = select_catalog(source, first, first_report, max_records=2)
            report_two = select_catalog(source, second, second_report, max_records=2)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(first_report.read_bytes(), second_report.read_bytes())
            self.assertEqual(report_one, report_two)
            self.assertEqual(2, report_one["output"]["records"])
            output_rows = [json.loads(line) for line in first.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(["036000291452"], [row["code"] for row in output_rows[:1]])
            self.assertEqual(
                {"code", "product_name_en", "brands"},
                set(output_rows[0]),
            )
            self.assertNotIn("price", output_rows[0])
            self.assertNotIn("quantity", output_rows[0])
            self.assertEqual("CANADA_LABELLED_IDENTITY_ONLY", report_one["selection"]["scope"])

    def test_no_canada_identity_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "off.csv.gz"
            self.write_tsv_gz(
                source,
                [{"code": "036000291452", "product_name": "Milk", "countries_en": "France"}],
            )
            with self.assertRaisesRegex(OpenFoodFactsLaunchSelectionError, "No usable Canada"):
                select_catalog(source, root / "out.jsonl", root / "report.json")

    def test_household_reserve_and_identity_variety_are_measured_deterministically(self):
        rows = [
            {
                "code": self.gtin(index),
                "product_name_en": f"Food staple {index}",
                "countries_tags": "en:canada",
                "categories_en": "Food products,Beverages",
                "unique_scans_n": "100",
                "completeness": "1",
            }
            for index in range(11)
        ]
        rows.append(
            {
                "code": self.gtin(11),
                "product_name_en": "Laundry detergent",
                "countries_tags": "en:canada",
                "categories_en": "",
                "unique_scans_n": "0",
                "completeness": "0",
            }
        )
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "off.csv.gz"
            self.write_tsv_gz(source, rows)
            output = root / "out.jsonl"
            report_path = root / "report.json"
            report = select_catalog(source, output, report_path, max_records=10)
            report_again = select_catalog(
                source,
                root / "out-again.jsonl",
                root / "report-again.json",
                max_records=10,
            )

            self.assertEqual(report, report_again)
            self.assertEqual(1, report["coverage"]["householdReserveTarget"])
            self.assertEqual(1, report["coverage"]["selectedHouseholdHintRecords"])
            self.assertTrue(report["coverage"]["householdReserveSatisfied"])
            self.assertEqual(10, report["coverage"]["records"])
            selected_names = [
                json.loads(line)["product_name_en"]
                for line in output.read_text(encoding="utf-8").splitlines()
            ]
            self.assertIn("Laundry detergent", selected_names)

    def test_name_hints_reject_common_food_false_positives(self):
        self.assertEqual(0, _household_hint({"product_name_en": "Garbage Candy"}))
        self.assertEqual(0, _household_hint({"product_name_en": "Sponge Toffee"}))
        self.assertEqual(0, _household_hint({"product_name_en": "Dough conditioner"}))
        self.assertEqual(1, _household_hint({"product_name_en": "Garbage bags"}))
        self.assertEqual(1, _household_hint({"product_name_en": "Kitchen sponge"}))
        self.assertEqual(1, _household_hint({"product_name_en": "Fabric conditioner"}))

    @staticmethod
    def gtin(index: int) -> str:
        body = f"036000{index:05d}"
        total = sum(
            int(digit) * (3 if (len(body) - offset) % 2 else 1)
            for offset, digit in enumerate(body)
        )
        return body + str((10 - total % 10) % 10)

    def test_bounds_and_overwrite_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "off.csv.gz"
            self.write_tsv_gz(
                source,
                [{"code": "036000291452", "product_name": "Milk", "countries_en": "Canada"}],
            )
            with self.assertRaisesRegex(OpenFoodFactsLaunchSelectionError, "between 1 and 5000"):
                select_catalog(source, root / "out.jsonl", root / "report.json", max_records=0)
            output = root / "out.jsonl"
            output.write_text("existing\n", encoding="utf-8")
            with self.assertRaisesRegex(OpenFoodFactsLaunchSelectionError, "overwrite"):
                select_catalog(source, output, root / "report.json")


if __name__ == "__main__":
    unittest.main()

import json
import tempfile
import unittest
from pathlib import Path

from tools.measure_rakuten_off_quantity_coverage import (
    FeedIdentitySet,
    build_aggregate_report,
    classify_off_product,
    extract_feed_gtins,
    parse_displayed_supplement_count,
    resolve_code,
)


def rakuten_row(upc: str) -> str:
    fields = [""] * 38
    fields[0] = f"product-{upc or 'missing'}"
    fields[1] = "Synthetic Product"
    fields[2] = f"sku-{upc or 'missing'}"
    fields[3] = "Synthetic"
    fields[5] = "https://example.test/product"
    fields[6] = "https://example.test/image.jpg"
    fields[13] = "10.00"
    fields[22] = "in-stock"
    fields[23] = upc
    fields[25] = "CAD"
    return "|".join(fields)


class RakutenOffQuantityCoverageTest(unittest.TestCase):
    def test_exact_supplement_count_parser_is_deliberately_narrow(self):
        accepted = {
            "100 tablets": 100,
            "60 capsules": 60,
            "90 gummies": 90,
            "120 soft gels": 120,
            "75 comprimés": 75,
            "30 gélules": 30,
        }
        for text, count in accepted.items():
            with self.subTest(text=text):
                candidate = parse_displayed_supplement_count(text)
                self.assertIsNotNone(candidate)
                self.assertEqual("COUNT", candidate.unit)
                self.assertEqual(count * 1_000_000, candidate.amount_micros)

        for text in [
            "60 capsules x 500 mg",
            "60 servings",
            "60",
            "about 60 capsules",
            "30-60 tablets",
            "2 x 60 capsules",
            "100 vegetarian capsules",
        ]:
            with self.subTest(text=text):
                self.assertIsNone(parse_displayed_supplement_count(text))

    def test_count_preferred_over_structured_mass_but_title_is_never_parsed(self):
        product = {
            "product_name": "Vitamin C 100 tablets",
            "quantity": "100 tablets",
            "product_quantity": "80",
            "product_quantity_unit": "g",
        }
        candidate = classify_off_product(product)
        self.assertIsNotNone(candidate)
        self.assertEqual("displayed_supplement_count", candidate.basis)
        self.assertEqual("COUNT", candidate.unit)

        title_only = {
            "product_name": "Vitamin C 100 tablets",
            "quantity": "",
            "product_quantity": "",
            "product_quantity_unit": "",
        }
        self.assertIsNone(classify_off_product(title_only))

    def test_structured_mass_volume_remains_separate_non_count_basis(self):
        mass = classify_off_product(
            {
                "quantity": "complex package text",
                "product_quantity": "500",
                "product_quantity_unit": "g",
            }
        )
        self.assertIsNotNone(mass)
        self.assertEqual("structured_mass_or_volume", mass.basis)
        self.assertEqual("GRAM", mass.unit)
        self.assertEqual(500_000_000, mass.amount_micros)

    def test_conflicting_duplicate_off_quantities_fail_closed(self):
        resolution = resolve_code(
            [
                {"quantity": "60 capsules", "brands": "Jamieson"},
                {"quantity": "90 capsules", "brands": "Jamieson"},
            ],
            expected_brand="Jamieson",
        )
        self.assertTrue(resolution.matched)
        self.assertTrue(resolution.conflict)
        self.assertIsNone(resolution.candidate)
        self.assertTrue(resolution.expected_brand_seen)

    def test_complete_rakuten_feed_extraction_counts_only_valid_gtins(self):
        valid_codes = ["036000291452", "3017620422003", "4006381333931"]
        invalid_code = "036000291453"
        rows = [rakuten_row(code) for code in valid_codes + [invalid_code]]
        text = "\n".join(
            [
                "HDR|synthetic-mid|Synthetic Merchant|08/28/2026 12:00:00",
                *rows,
                f"TRL|{len(rows)}",
                "",
            ]
        )

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic.txt"
            path.write_text(text, encoding="utf-8")
            result = extract_feed_gtins(path)

        self.assertEqual(4, result.product_records)
        self.assertEqual(3, result.rows_with_valid_gtin)
        self.assertEqual(1, result.rows_missing_or_invalid_gtin)
        self.assertEqual(tuple(sorted(valid_codes)), result.gtins)
        self.assertEqual(4, result.trailer_declared_products)

    def test_aggregate_report_never_emits_gtins_or_source_rows(self):
        codes = ("036000291452", "3017620422003", "4006381333931")
        feed = FeedIdentitySet(
            product_records=4,
            rows_with_valid_gtin=3,
            rows_missing_or_invalid_gtin=1,
            gtins=codes,
            trailer_declared_products=4,
        )
        by_code = {
            codes[0]: [
                {
                    "code": codes[0],
                    "brands": "Jamieson",
                    "quantity": "100 tablets",
                    "last_modified_t": "1790000000",
                }
            ],
            codes[1]: [
                {
                    "code": codes[1],
                    "brands": "Other",
                    "quantity": "500 g",
                    "product_quantity": "500",
                    "product_quantity_unit": "g",
                }
            ],
        }

        report = build_aggregate_report(
            feed,
            by_code,
            api_calls=1,
            report_label="Synthetic authorized feed",
            expected_brand="Jamieson",
        )

        self.assertEqual(2, report["open_food_facts"]["matched_gtins"])
        self.assertEqual(1, report["open_food_facts"]["unmatched_gtins"])
        self.assertEqual(
            1,
            report["open_food_facts"]["usable_exact_supplement_count"],
        )
        self.assertEqual(
            1,
            report["open_food_facts"]["usable_structured_mass_or_volume"],
        )
        self.assertEqual(1, report["unit_value_readiness"]["exact_count_ready_gtins"])
        self.assertFalse(report["production_authorized"])
        self.assertFalse(report["privacy"]["gtins_emitted"])

        serialized = json.dumps(report)
        for code in codes:
            self.assertNotIn(code, serialized)
        self.assertNotIn("Synthetic Product", serialized)


if __name__ == "__main__":
    unittest.main()

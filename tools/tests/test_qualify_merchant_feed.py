from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from tools.qualify_merchant_feed import QualificationConfig, qualify_feed


class MerchantFeedQualificationTest(unittest.TestCase):
    def write(self, directory: Path, name: str, content: str) -> Path:
        path = directory / name
        path.write_text(content, encoding="utf-8")
        return path

    def test_csv_auto_mapping_counts_structural_and_unit_candidates(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.csv",
                "product_id,gtin,product_name,currency,price,quantity_value,quantity_unit,availability,country,updated_at\n"
                "a,0036000291452,Vitamin C,CAD,10.99,100,count,in_stock,CA,2026-08-27T20:00:00Z\n"
                "b,036000291453,Bad code,CAD,9.99,90,count,in_stock,CA,2026-08-27T20:00:00Z\n",
            )
            report = qualify_feed(
                QualificationConfig(
                    input_path=feed,
                    evaluated_at=datetime(2026, 8, 27, 20, 5, tzinfo=timezone.utc),
                )
            )
            self.assertEqual("REVIEW_DATA_AND_RIGHTS", report["decision"]["status"])
            self.assertEqual(2, report["decision"]["structural_current_offer_candidates"])
            self.assertEqual(2, report["decision"]["structural_unit_value_candidates"])
            self.assertEqual(
                {
                    "coverage_status": "MEASURED",
                    "rows_with_identity": 2,
                    "unique_identity_scopes": 2,
                },
                report["coverage"]["identity"],
            )
            self.assertEqual(
                {
                    "coverage_status": "STRUCTURAL_ONLY",
                    "candidate_count": 2,
                    "authority": "NONE",
                },
                report["coverage"]["current_offers"],
            )
            self.assertEqual(
                {
                    "coverage_status": "STRUCTURAL_ONLY",
                    "candidate_count": 2,
                    "authority": "NONE",
                },
                report["coverage"]["unit_values"],
            )
            self.assertIn("separate measurements", report["coverage"]["note"])
            self.assertEqual(1, report["quality"]["gtin_valid"])
            self.assertEqual(1, report["quality"]["gtin_invalid"])
            self.assertFalse(report["decision"]["production_authorized"])

    def test_non_cad_row_is_not_structural_candidate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.csv",
                "product_id,product_name,currency,price,country\n"
                "a,Tea,USD,4.99,CA\n",
            )
            report = qualify_feed(QualificationConfig(input_path=feed))
            self.assertEqual("FAIL_NO_STRUCTURAL_CURRENT_OFFERS", report["decision"]["status"])
            self.assertEqual(1, report["quality"]["unexpected_currency_rows"])
            self.assertEqual(1, report["coverage"]["identity"]["unique_identity_scopes"])
            self.assertEqual(0, report["coverage"]["current_offers"]["candidate_count"])
            self.assertEqual("STRUCTURAL_ONLY", report["coverage"]["current_offers"]["coverage_status"])

    def test_coverage_block_is_deterministic_and_non_authoritative(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.csv",
                "product_id,product_name,currency,price,quantity_value,quantity_unit,country\n"
                "a,Tea,CAD,4.99,100,g,CA\n"
                "b,Coffee,CAD,5.99,,,CA\n",
            )
            first = qualify_feed(QualificationConfig(input_path=feed))
            second = qualify_feed(QualificationConfig(input_path=feed))

            self.assertEqual(
                json.dumps(first["coverage"], sort_keys=True),
                json.dumps(second["coverage"], sort_keys=True),
            )
            self.assertEqual(2, first["coverage"]["identity"]["rows_with_identity"])
            self.assertEqual(2, first["coverage"]["current_offers"]["candidate_count"])
            self.assertEqual(1, first["coverage"]["unit_values"]["candidate_count"])
            self.assertEqual("NONE", first["coverage"]["current_offers"]["authority"])
            self.assertEqual("NONE", first["coverage"]["unit_values"]["authority"])

    def test_bad_price_is_not_structural_candidate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.csv",
                "product_id,product_name,currency,price,country\n"
                "a,Tea,CAD,0,CA\n"
                "b,Coffee,CAD,not-a-price,CA\n",
            )
            report = qualify_feed(QualificationConfig(input_path=feed))
            self.assertEqual(0, report["decision"]["structural_current_offer_candidates"])
            self.assertEqual(2, report["quality"]["current_price_invalid_or_missing"])

    def test_duplicate_scope_with_different_prices_is_reported_as_conflict(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.csv",
                "product_id,product_name,currency,price,country,store_id\n"
                "a,Tea,CAD,4.99,CA,1\n"
                "a,Tea,CAD,5.49,CA,1\n"
                "a,Tea,CAD,5.49,CA,2\n",
            )
            report = qualify_feed(QualificationConfig(input_path=feed))
            self.assertEqual(1, report["quality"]["duplicate_identity_scopes"])
            self.assertEqual(1, report["quality"]["conflicting_identity_scopes"])
            self.assertEqual(2, report["quality"]["unique_identity_scopes"])

    def test_gzip_tsv_is_streamed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = root / "feed.tsv.gz"
            with gzip.open(feed, "wt", encoding="utf-8", newline="") as handle:
                handle.write("product_id\tproduct_name\tcurrency\tprice\tcountry\n")
                handle.write("a\tTea\tCAD\t4.99\tCA\n")
            report = qualify_feed(QualificationConfig(input_path=feed))
            self.assertTrue(report["input"]["compressed_gzip"])
            self.assertEqual("delimited", report["input"]["format"])
            self.assertEqual(1, report["decision"]["structural_current_offer_candidates"])

    def test_xml_requires_explicit_record_tag_and_mapping(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.xml",
                "<catalog><item><id>a</id><name>Tea</name><currency>CAD</currency><amount>4.99</amount><country>CA</country></item></catalog>",
            )
            mapping = self.write(
                root,
                "mapping.json",
                json.dumps({"fields": {
                    "provider_item_id": "id",
                    "title": "name",
                    "currency": "currency",
                    "current_price": "amount",
                    "country": "country",
                }}),
            )
            with self.assertRaisesRegex(ValueError, "XML feeds require"):
                qualify_feed(QualificationConfig(input_path=feed, mapping_path=mapping))
            report = qualify_feed(
                QualificationConfig(input_path=feed, mapping_path=mapping, xml_record_tag="item")
            )
            self.assertEqual("explicit", report["mapping"]["mode"])
            self.assertEqual(1, report["decision"]["structural_current_offer_candidates"])

    def test_ambiguous_price_mapping_fails_schema_instead_of_guessing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.csv",
                "product_id,product_name,currency,price,current_price,country\n"
                "a,Tea,CAD,4.99,4.49,CA\n",
            )
            report = qualify_feed(QualificationConfig(input_path=feed))
            self.assertEqual("FAIL_SCHEMA", report["decision"]["status"])
            self.assertIn("current_price", report["mapping"]["ambiguous_auto_matches"])
            self.assertIn("current_price", report["mapping"]["missing_required_capabilities"])

    def test_max_rows_bounds_work_and_marks_truncation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(
                root,
                "feed.csv",
                "product_id,product_name,currency,price,country\n"
                "a,Tea,CAD,4.99,CA\n"
                "b,Coffee,CAD,5.99,CA\n"
                "c,Cocoa,CAD,6.99,CA\n",
            )
            report = qualify_feed(QualificationConfig(input_path=feed, max_rows=2))
            self.assertEqual(2, report["input"]["rows_scanned"])
            self.assertTrue(report["input"]["truncated_at_max_rows"])


if __name__ == "__main__":
    unittest.main()

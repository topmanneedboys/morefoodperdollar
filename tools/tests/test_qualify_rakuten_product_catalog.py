from __future__ import annotations

import csv
import gzip
import io
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from tools.qualify_rakuten_product_catalog import qualify


class RakutenProductCatalogQualifierTest(unittest.TestCase):
    def product_fields(self) -> list[str]:
        fields = [""] * 38
        fields[0] = "101"
        fields[1] = "Vitamin C 100 Tablets"
        fields[2] = "SKU-101"
        fields[3] = "Supplements"
        fields[5] = "https://example.test/product"
        fields[6] = "https://example.test/image.jpg"
        fields[8] = "Description with | a pipe"
        fields[12] = "9.99"
        fields[13] = "12.99"
        fields[16] = "Example Brand"
        fields[22] = "in-stock"
        fields[23] = "0036000291452"
        fields[25] = "CAD"
        return fields

    def render_feed(self, products: list[list[str]], trailer_count: int | None = None) -> str:
        buffer = io.StringIO()
        writer = csv.writer(buffer, delimiter="|", quotechar='"', lineterminator="\n")
        writer.writerow(["HDR", "1234", "Example Merchant", "08/27/2026 20:00:00"])
        writer.writerows(products)
        if trailer_count is not None:
            writer.writerow(["TRL", str(trailer_count)])
        return buffer.getvalue()

    def write(self, directory: Path, name: str, content: str) -> Path:
        path = directory / name
        path.write_text(content, encoding="utf-8")
        return path

    def test_valid_feed_preserves_quoted_pipe_and_validates_trailer(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(root, "1234_567_mp.txt", self.render_feed([self.product_fields()], 1))
            report = qualify(
                feed,
                evaluated_at=datetime(2026, 8, 27, 20, 5, tzinfo=timezone.utc),
            )

            self.assertEqual("REVIEW_DATA_AND_RIGHTS", report["decision"]["status"])
            self.assertEqual(1, report["decision"]["structural_offer_candidates"])
            self.assertEqual(0, report["decision"]["unit_value_candidates"])
            self.assertEqual(1, report["quality"]["upc_checksum_valid_gtin"])
            self.assertEqual(1, report["quality"]["qualification_price_source_sale_price"])
            self.assertTrue(report["feed_metadata"]["trailer_valid"])
            self.assertTrue(report["feed_metadata"]["trailer_count_matches_records_scanned"])
            self.assertIsNotNone(report["file_age"]["file_header_age_seconds"])

    def test_header_timestamp_is_not_promoted_to_product_freshness(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(root, "feed.txt", self.render_feed([self.product_fields()], 1))
            report = qualify(feed)
            self.assertFalse(report["quantity_gate"]["generic_structured_quantity_available"])
            self.assertIn("not a per-product", report["file_age"]["note"])

    def test_trailer_count_mismatch_fails_integrity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(root, "feed.txt", self.render_feed([self.product_fields()], 2))
            report = qualify(feed)
            self.assertEqual("FAIL_FEED_INTEGRITY", report["decision"]["status"])
            self.assertFalse(report["feed_metadata"]["trailer_count_matches_records_scanned"])

    def test_missing_trailer_fails_integrity_without_dropping_last_product(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(root, "feed.txt", self.render_feed([self.product_fields()], None))
            report = qualify(feed)
            self.assertEqual(1, report["quality"]["product_records_scanned"])
            self.assertEqual("FAIL_FEED_INTEGRITY", report["decision"]["status"])
            self.assertFalse(report["feed_metadata"]["trailer_valid"])

    def test_short_product_record_fails_integrity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = self.write(root, "feed.txt", self.render_feed([["101", "Too short"]], 1))
            report = qualify(feed)
            self.assertEqual(1, report["quality"]["malformed_too_few_primary_fields"])
            self.assertEqual("FAIL_FEED_INTEGRITY", report["decision"]["status"])

    def test_truncated_scan_does_not_pretend_trailer_was_validated(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            products = [self.product_fields(), self.product_fields()]
            products[1][0] = "102"
            products[1][2] = "SKU-102"
            feed = self.write(root, "feed.txt", self.render_feed(products, 2))
            report = qualify(feed, max_rows=1)
            self.assertEqual("REVIEW_TRUNCATED_DATA_AND_RIGHTS", report["decision"]["status"])
            self.assertTrue(report["input"]["truncated"])
            self.assertIsNone(report["feed_metadata"]["trailer_count_matches_records_scanned"])

    def test_gzip_feed_supported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            feed = root / "feed.txt.gz"
            with gzip.open(feed, "wt", encoding="utf-8", newline="") as handle:
                handle.write(self.render_feed([self.product_fields()], 1))
            report = qualify(feed)
            self.assertTrue(report["input"]["gzip"])
            self.assertEqual("REVIEW_DATA_AND_RIGHTS", report["decision"]["status"])


if __name__ == "__main__":
    unittest.main()

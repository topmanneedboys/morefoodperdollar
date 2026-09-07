from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.argentina_sepa_search import SearchError
from tools.audit_argentina_sepa_search import audit_search


class ArgentinaSepaSearchAuditTest(unittest.TestCase):
    def _write_products(self, path: Path) -> None:
        products = [
            {"productEvidenceKey": "ar-sepa-product:1:7790070318398", "commerceId": "1", "name": "LECHE ENTERA", "brand": "FIXTURE", "gtin": "7790070318398"},
            {"productEvidenceKey": "ar-sepa-product:2:7790070318398", "commerceId": "2", "name": "LECHE ENTERA", "brand": "FIXTURE", "gtin": "7790070318398"},
            {"productEvidenceKey": "ar-sepa-product:1:12345", "commerceId": "1", "name": "CHOCOLATE CON LECHE", "brand": "FIXTURE", "gtin": None},
        ]
        with path.open("wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as stream:
                for product in products:
                    stream.write((json.dumps(product, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8"))

    def _write_fixture(self, path: Path, *, tamper_name: bool = False) -> None:
        name = "NOT THE SOURCE" if tamper_name else "LECHE ENTERA"
        fixture = {
            "schemaVersion": "argentina-sepa-search-audit-fixture-v1",
            "regionId": "ar-caba",
            "source": {"provider": "ARGENTINA_SEPA_PRECIOS_CLAROS", "releaseDate": "2026-09-06", "outerSha256": "e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305"},
            "queries": [{"query": "leche", "candidates": [{"productEvidenceKey": "ar-sepa-product:1:7790070318398", "name": name, "brand": "FIXTURE", "gtin": "7790070318398", "relevant": True, "rationale": "Exact fixture milk."}, {"productEvidenceKey": "ar-sepa-product:1:12345", "name": "CHOCOLATE CON LECHE", "brand": "FIXTURE", "gtin": None, "relevant": False, "rationale": "Confection wording."}]}],
        }
        path.write_text(json.dumps(fixture, ensure_ascii=False, sort_keys=True), encoding="utf-8")

    def test_audit_is_bounded_and_measures_exact_gtin_overlap(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            products = root / "products.jsonl.gz"
            fixture = root / "fixture.json"
            self._write_products(products)
            self._write_fixture(fixture)
            result = audit_search(products, fixture)
            self.assertEqual(result["status"], "SEARCH_NOT_YET_QUALIFIED")
            self.assertEqual(result["queryCount"], 1)
            self.assertEqual(result["auditedRelevantWithExactCrossRetailerGtin"], 1)
            self.assertEqual(result["unreviewedTopK"], [{"name": "LECHE ENTERA", "productEvidenceKey": "ar-sepa-product:2:7790070318398", "query": "leche"}])

    def test_fixture_must_match_source_product_identity(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            products = root / "products.jsonl.gz"
            fixture = root / "fixture.json"
            self._write_products(products)
            self._write_fixture(fixture, tamper_name=True)
            with self.assertRaises(SearchError):
                audit_search(products, fixture)


if __name__ == "__main__":
    unittest.main()

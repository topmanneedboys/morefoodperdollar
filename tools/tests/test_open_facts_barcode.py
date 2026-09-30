from __future__ import annotations

import unittest

from tools.open_facts_barcode import canonical_open_facts_gtin


class OpenFactsBarcodeTest(unittest.TestCase):
    """Keep the generic barcode normalizer covered for the active image audit."""

    def test_documented_leading_zero_forms_canonicalize(self):
        self.assertEqual("0036000291452", canonical_open_facts_gtin("036000291452"))
        self.assertEqual("0036000291452", canonical_open_facts_gtin("00036000291452"))

    def test_valid_ean8_and_nonzero_indicator_gtin14_remain_distinct(self):
        self.assertEqual("96385074", canonical_open_facts_gtin("96385074"))
        self.assertEqual("10012345678902", canonical_open_facts_gtin("10012345678902"))

    def test_invalid_gtin_is_never_repaired(self):
        self.assertIsNone(canonical_open_facts_gtin("036000291453"))
        self.assertIsNone(canonical_open_facts_gtin("not-a-gtin"))


if __name__ == "__main__":
    unittest.main()

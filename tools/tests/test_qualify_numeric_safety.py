from __future__ import annotations

import unittest
from decimal import Decimal

from tools.qualify_merchant_feed import parse_positive_decimal


class FeedNumericSafetyTest(unittest.TestCase):
    def test_dot_decimal_accepts_plain_and_grouped_values(self):
        self.assertEqual(Decimal("9.99"), parse_positive_decimal("9.99"))
        self.assertEqual(Decimal("1299.99"), parse_positive_decimal("1,299.99"))

    def test_dot_decimal_rejects_decimal_comma_instead_of_turning_it_into_999(self):
        self.assertIsNone(parse_positive_decimal("9,99"))
        self.assertIsNone(parse_positive_decimal("1,23"))

    def test_comma_decimal_requires_explicit_choice(self):
        self.assertEqual(Decimal("9.99"), parse_positive_decimal("9,99", decimal_separator=","))
        self.assertEqual(Decimal("1299.99"), parse_positive_decimal("1.299,99", decimal_separator=","))

    def test_ambiguous_or_malformed_grouping_fails_closed(self):
        self.assertIsNone(parse_positive_decimal("1,2,3.45"))
        self.assertIsNone(parse_positive_decimal("1.23.4,56", decimal_separator=","))
        self.assertIsNone(parse_positive_decimal("$9.99"))
        self.assertIsNone(parse_positive_decimal("-9.99"))


if __name__ == "__main__":
    unittest.main()

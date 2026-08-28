from __future__ import annotations

import unittest
from unittest.mock import patch

from tools.measure_rakuten_off_quantity_coverage_v2 import (
    OpenFactsRequestError,
    build_lookup_normalization,
    fetch_off_products_normalized,
)
from tools.open_facts_barcode import canonical_open_facts_gtin


class OpenFactsBarcodeNormalizationTest(unittest.TestCase):
    def test_documented_leading_zero_representations_canonicalize(self):
        upc12 = "036000291452"
        gtin13 = "0036000291452"
        gtin14_with_zero_indicator = "00036000291452"

        self.assertEqual(gtin13, canonical_open_facts_gtin(upc12))
        self.assertEqual(gtin13, canonical_open_facts_gtin(gtin13))
        self.assertEqual(gtin13, canonical_open_facts_gtin(gtin14_with_zero_indicator))

    def test_ean8_and_nonzero_indicator_gtin14_remain_distinct(self):
        self.assertEqual("96385074", canonical_open_facts_gtin("96385074"))
        self.assertEqual(
            "10012345678902",
            canonical_open_facts_gtin("10012345678902"),
        )

    def test_invalid_gtin_is_never_repaired(self):
        self.assertIsNone(canonical_open_facts_gtin("036000291453"))
        self.assertIsNone(canonical_open_facts_gtin("not-a-gtin"))

    def test_lookup_statistics_are_aggregate_only(self):
        raw_codes = (
            "036000291452",
            "3017620422003",
            "10012345678902",
        )
        mapping, stats = build_lookup_normalization(raw_codes)

        self.assertEqual(3, stats["input_valid_gtins"])
        self.assertEqual({"12": 1, "13": 1, "14": 1}, stats["input_length_distribution"])
        self.assertEqual(1, stats["canonicalization_changed_gtins"])
        self.assertEqual(3, stats["canonical_unique_lookup_gtins"])
        self.assertEqual(0, stats["canonical_identity_collisions"])

        serialized = repr(stats)
        for code in raw_codes:
            self.assertNotIn(code, serialized)

        self.assertIn("0036000291452", mapping)
        self.assertEqual(("036000291452",), mapping["0036000291452"])

    def test_normalized_api_response_maps_back_to_raw_provider_gtin(self):
        raw_upc12 = "036000291452"
        normalized_gtin13 = "0036000291452"

        fake_payload = {
            "products": [
                {
                    "code": normalized_gtin13,
                    "brands": "Example",
                    "quantity": "100 tablets",
                }
            ]
        }

        with patch(
            "tools.measure_rakuten_off_quantity_coverage_v2._request_json",
            return_value=fake_payload,
        ):
            by_code, calls, stats = fetch_off_products_normalized(
                [raw_upc12],
                batch_size=1,
                delay_seconds=6.5,
            )

        self.assertEqual(1, calls)
        self.assertEqual(1, len(by_code[raw_upc12]))
        self.assertEqual(1, stats["canonicalization_changed_gtins"])
        self.assertEqual(0, stats["response_codes_ignored"])
        self.assertEqual(1, stats["search_requests_succeeded"])
        self.assertEqual(0, stats["search_fallback_batches"])
        self.assertEqual(0, stats["direct_product_requests"])

    def test_search_503_falls_back_to_direct_product_read(self):
        raw_upc12 = "036000291452"
        normalized_gtin13 = "0036000291452"
        direct_payload = {
            "status": 1,
            "code": normalized_gtin13,
            "product": {
                "code": normalized_gtin13,
                "brands": "Example",
                "quantity": "100 tablets",
            },
        }

        with (
            patch(
                "tools.measure_rakuten_off_quantity_coverage_v2._request_json",
                side_effect=OpenFactsRequestError("HTTP 503"),
            ),
            patch(
                "tools.measure_rakuten_off_quantity_coverage_v2._request_product_json",
                return_value=direct_payload,
            ) as direct_read,
        ):
            by_code, calls, stats = fetch_off_products_normalized(
                [raw_upc12],
                batch_size=1,
                delay_seconds=6.5,
            )

        self.assertEqual(1, calls)
        self.assertEqual(1, len(by_code[raw_upc12]))
        self.assertEqual(0, stats["search_requests_succeeded"])
        self.assertEqual(1, stats["search_fallback_batches"])
        self.assertEqual(1, stats["direct_product_requests"])
        self.assertEqual(0, stats["response_codes_ignored"])
        direct_read.assert_called_once_with(normalized_gtin13)

        serialized = repr(stats)
        self.assertNotIn(raw_upc12, serialized)
        self.assertNotIn(normalized_gtin13, serialized)

    def test_non_server_search_failure_does_not_fallback(self):
        with (
            patch(
                "tools.measure_rakuten_off_quantity_coverage_v2._request_json",
                side_effect=OpenFactsRequestError("HTTP 429"),
            ),
            patch(
                "tools.measure_rakuten_off_quantity_coverage_v2._request_product_json"
            ) as direct_read,
        ):
            with self.assertRaises(OpenFactsRequestError):
                fetch_off_products_normalized(
                    ["036000291452"],
                    batch_size=1,
                    delay_seconds=6.5,
                )

        direct_read.assert_not_called()

    def test_canonical_collision_is_reported_not_hidden(self):
        mapping, stats = build_lookup_normalization(
            ["036000291452", "0036000291452"]
        )
        self.assertEqual(1, stats["canonical_identity_collisions"])
        self.assertEqual(1, stats["canonical_unique_lookup_gtins"])
        self.assertEqual(
            ("036000291452", "0036000291452"),
            mapping["0036000291452"],
        )


if __name__ == "__main__":
    unittest.main()

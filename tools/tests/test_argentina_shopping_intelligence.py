from __future__ import annotations

from unittest.mock import patch
import unittest

from tools.argentina_shopping_intelligence import evaluate_argentina_request


def _raw_offer(*, offer_id: int, product: str, store: str, name: str, amount: str, quantity: dict[str, str] | None, quantity_status: str = "KNOWN", currency: str = "ARS", freshness: str = "FRESH") -> dict[str, object]:
    return {
        "offerKey": f"ar-sepa-offer:ar-caba:{offer_id}",
        "offerId": offer_id,
        "productEvidenceKey": product,
        "name": name,
        "brand": "Fixture",
        "gtin": "7790000000001",
        "storeKey": store,
        "storeName": store,
        "storeAddress": {"street": "Main", "number": "1"},
        "storeLatitude": "-34.600000",
        "storeLongitude": "-58.400000",
        "distanceKm": "1.250000",
        "listPrice": {"amount": amount, "currency": currency},
        "quantity": quantity,
        "quantityStatus": quantity_status,
        "releaseDate": "2026-09-06",
        "freshnessStatus": freshness,
        "availability": "UNKNOWN",
        "promotions": [{"slot": 1, "price": {"amount": "1.00", "currency": "ARS"}, "condition": "loyalty", "eligibility": "UNKNOWN"}],
        "packageProvenance": {"sha256": "a" * 64, "name": "fixture.zip"},
        "sourceRow": offer_id,
        "providerUpdateTime": "2026-09-06T11:30:01Z",
    }


class ArgentinaShoppingIntelligenceAdapterTest(unittest.TestCase):
    def test_adapter_translates_qualified_evidence_without_inference(self) -> None:
        provider_result = {
            "items": [
                {
                    "productCandidates": [{"productEvidenceKey": "product:rice"}],
                    "offers": [
                        _raw_offer(offer_id=1, product="product:rice", store="store:a", name="Rice title", amount="1200.00", quantity={"value": "1", "unit": "KG"}),
                        _raw_offer(offer_id=2, product="product:rice-unknown", store="store:a", name="Rice title 2KG", amount="100.00", quantity=None, quantity_status="UNKNOWN"),
                        _raw_offer(offer_id=3, product="product:rice-stale", store="store:a", name="Rice stale", amount="1.00", quantity={"value": "1", "unit": "KG"}, freshness="STALE"),
                    ],
                },
                {
                    "productCandidates": [{"productEvidenceKey": "product:milk"}],
                    "offers": [
                        _raw_offer(offer_id=4, product="product:milk", store="store:a", name="Milk", amount="2000.00", quantity={"value": "1", "unit": "L"}),
                    ],
                },
            ],
            "queryPlan": {"partitionIds": ["p001"]},
        }
        request = {
            "latitude": "-34.60",
            "longitude": "-58.40",
            "radiusKm": "10",
            "items": [
                {"query": "arroz", "amount": "2", "unit": "kg"},
                {"query": "leche", "amount": "1", "unit": "l"},
            ],
        }
        with patch("tools.argentina_shopping_intelligence.query_micro_structured_request", return_value=provider_result) as query:
            result = evaluate_argentina_request(r"C:\\fixture\\micro-128", "ar-caba", request)
        query.assert_called_once()
        self.assertEqual(result["productionUiAuthorized"], False)
        self.assertEqual(result["androidNetworkingAuthorized"], False)
        decision = result["decision"]
        self.assertEqual(decision["cheapestSingleStore"]["totalArs"]["amount"], "4400.00")
        self.assertEqual(decision["cheapestSingleStore"]["lines"][0]["packageCount"], 2)
        self.assertEqual(decision["cheapestSingleStore"]["lines"][0]["excessQuantity"]["amount"], "0")
        self.assertEqual(decision["safety"]["availability"], "UNKNOWN")
        self.assertEqual(decision["cheapestSingleStore"]["lines"][0]["promotions"][0]["eligibility"], "UNKNOWN")
        self.assertEqual(decision["diagnostics"]["providerRejected"]["unknownQuantity"], 1)
        self.assertEqual(decision["diagnostics"]["providerRejected"]["stale"], 1)
        self.assertEqual(result["providerSafety"]["pricePublicationIsNotInventory"], True)
        self.assertNotIn("Rice title 2KG", result["decision"]["cheapestSingleStore"]["lines"][0]["productName"])

    def test_adapter_rejects_non_ars_and_preserves_no_coverage(self) -> None:
        provider_result = {
            "items": [
                {
                    "productCandidates": [{"productEvidenceKey": "product:x"}],
                    "offers": [_raw_offer(offer_id=1, product="product:x", store="store:a", name="X", amount="1.00", quantity={"value": "1", "unit": "COUNT"}, currency="USD")],
                }
            ],
            "queryPlan": {},
        }
        request = {"latitude": "-34.60", "longitude": "-58.40", "radiusKm": "1", "items": [{"query": "x", "amount": "1", "unit": "count"}]}
        with patch("tools.argentina_shopping_intelligence.query_micro_structured_request", return_value=provider_result):
            result = evaluate_argentina_request("C:/fixture/micro-128", "ar-caba", request)
        self.assertIsNone(result["decision"]["cheapestSingleStore"])
        self.assertEqual(result["decision"]["diagnostics"]["providerRejected"]["invalidPrice"], 1)
        self.assertEqual(result["decision"]["bestSensibleChoice"]["status"], "UNRESOLVED")


if __name__ == "__main__":
    unittest.main()

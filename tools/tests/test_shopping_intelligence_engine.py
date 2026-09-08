from __future__ import annotations

from decimal import Decimal
import json
import unittest

from tools.shopping_intelligence_engine import (
    BestValuePolicy,
    ExactMoney,
    ExactQuantity,
    OfferEvidence,
    PromotionEvidence,
    ShoppingIntelligenceError,
    ShoppingRequest,
    evaluate_shopping_request,
)


def _request(*items: dict[str, object]) -> ShoppingRequest:
    return ShoppingRequest.from_mapping(
        {
            "latitude": "-34.60",
            "longitude": "-58.40",
            "radiusKm": "10",
            "items": list(items),
        }
    )


def _offer(
    *,
    line: str,
    product: str,
    store: str,
    name: str,
    amount: str,
    package_amount: str | None,
    package_unit: str | None,
    distance: str,
    promotions: tuple[PromotionEvidence, ...] = (),
    availability: str = "UNKNOWN",
) -> OfferEvidence:
    return OfferEvidence(
        offer_key=f"offer:{line}:{product}:{store}",
        product_evidence_key=product,
        product_name=name,
        brand="Brand",
        store_key=store,
        store_name=store.title(),
        store_address={"street": "Main", "number": "1"},
        store_latitude=Decimal("-34.60"),
        store_longitude=Decimal("-58.40"),
        distance_km=Decimal(distance),
        price=ExactMoney.parse(amount, positive=True),
        package_quantity=ExactQuantity.parse(package_amount, package_unit) if package_amount is not None else None,
        freshness_status="FRESH",
        availability=availability,
        promotions=promotions,
        provenance={"provider": "fixture", "sourceRow": 1},
    )


class ShoppingIntelligenceEngineTest(unittest.TestCase):
    def test_exact_quantity_conversion_and_incompatible_dimensions(self) -> None:
        self.assertEqual(ExactQuantity.parse("4", "kg").base_amount, Decimal("4000"))
        self.assertEqual(ExactQuantity.parse("1.5", "l").base_amount, Decimal("1500.0"))
        self.assertEqual(ExactQuantity.parse("3", "count").base_amount, Decimal("3"))
        with self.assertRaises(ShoppingIntelligenceError):
            ExactQuantity.parse("1", "kg").in_unit("l")

    def test_package_rounding_exact_multiple_and_overfill(self) -> None:
        request = _request({"query": "arroz", "amount": "4", "unit": "kg"})
        exact = _offer(line="item-1", product="rice-1", store="a", name="Rice 1 kg", amount="1200.00", package_amount="1", package_unit="kg", distance="1")
        overfill = _offer(line="item-1", product="rice-2", store="a", name="Rice 1.5 kg", amount="1700.00", package_amount="1.5", package_unit="kg", distance="1")
        result = evaluate_shopping_request(request, {"item-1": [exact, overfill]})
        line = result["cheapestSingleStore"]["lines"][0]
        self.assertEqual(line["packageCount"], 4)
        self.assertEqual(line["suppliedQuantity"]["amount"], "4")
        self.assertEqual(line["excessQuantity"]["amount"], "0")
        self.assertEqual(line["lineTotal"]["amount"], "4800.00")

        overfill_result = evaluate_shopping_request(
            request,
            {"item-1": [overfill]},
        )
        overfill_line = overfill_result["cheapestSingleStore"]["lines"][0]
        self.assertEqual(overfill_line["packageCount"], 3)
        self.assertEqual(overfill_line["suppliedQuantity"]["amount"], "4.5")
        self.assertEqual(overfill_line["excessQuantity"]["amount"], "0.5")
        self.assertEqual(overfill_line["lineTotal"]["amount"], "5100.00")

    def test_identity_isolation_and_unknown_quantity_never_becomes_a_plan(self) -> None:
        request = _request({"query": "milk", "amount": "3", "unit": "l"})
        unknown = _offer(line="item-1", product="unknown", store="a", name="Milk title only", amount="1.00", package_amount=None, package_unit=None, distance="1")
        count = _offer(line="item-1", product="count-product", store="a", name="Milk unit", amount="2.00", package_amount="1", package_unit="count", distance="1")
        result = evaluate_shopping_request(request, {"item-1": [unknown, count]})
        self.assertIsNone(result["cheapestSingleStore"])
        self.assertEqual(result["diagnostics"]["unknownQuantityOffers"], 1)
        self.assertEqual(result["diagnostics"]["incompatibleQuantityOffers"], 1)

    def test_single_store_closest_two_store_and_lower_bound(self) -> None:
        request = _request(
            {"query": "arroz", "amount": "4", "unit": "kg"},
            {"query": "leche", "amount": "1", "unit": "l"},
        )
        offers = {
            "item-1": [
                _offer(line="item-1", product="rice-a", store="a", name="Rice A", amount="1200", package_amount="1", package_unit="kg", distance="1"),
                _offer(line="item-1", product="rice-b", store="b", name="Rice B", amount="1700", package_amount="1.5", package_unit="kg", distance="5"),
            ],
            "item-2": [
                _offer(line="item-2", product="milk-a", store="a", name="Milk A", amount="2000", package_amount="1", package_unit="l", distance="1"),
                _offer(line="item-2", product="milk-b", store="b", name="Milk B", amount="1500", package_amount="1", package_unit="l", distance="5"),
            ],
        }
        result = evaluate_shopping_request(request, offers)
        self.assertEqual(result["cheapestSingleStore"]["stores"][0]["storeKey"], "b")
        self.assertEqual(result["cheapestSingleStore"]["totalArs"]["amount"], "6600.00")
        self.assertEqual(result["closestCompletePriceEvidenceStore"]["stores"][0]["storeKey"], "a")
        self.assertEqual(result["closestCompletePriceEvidenceStore"]["maxStraightLineDistanceKm"], "1")
        self.assertEqual(result["cheapestTwoStoreCombination"]["totalArs"]["amount"], "6300.00")
        self.assertEqual(set(result["cheapestTwoStoreCombination"]["storeKeys"] if "storeKeys" in result["cheapestTwoStoreCombination"] else result["cheapestTwoStoreCombination"]["planId"].split(":", 1)[1].split("+")), {"a", "b"})
        self.assertEqual(result["cheapestPerLineUnboundedStores"]["totalArs"]["amount"], "6300.00")
        self.assertEqual(result["cheapestUpToTwoStores"]["totalArs"]["amount"], "6300.00")

    def test_incomplete_store_is_not_ranked_by_partial_subtotal(self) -> None:
        request = _request(
            {"query": "one", "amount": "1", "unit": "count"},
            {"query": "two", "amount": "1", "unit": "count"},
        )
        result = evaluate_shopping_request(
            request,
            {
                "item-1": [_offer(line="item-1", product="one-a", store="cheap", name="One", amount="1", package_amount="1", package_unit="count", distance="1"), _offer(line="item-1", product="one-complete", store="complete", name="One", amount="100", package_amount="1", package_unit="count", distance="2")],
                "item-2": [_offer(line="item-2", product="two-b", store="complete", name="Two", amount="2", package_amount="1", package_unit="count", distance="2")],
            },
        )
        self.assertEqual(result["cheapestSingleStore"]["stores"][0]["storeKey"], "complete")
        self.assertEqual(result["cheapestSingleStore"]["totalArs"]["amount"], "102.00")
        self.assertTrue(result["closestCompletePriceEvidenceStore"]["completePriceEvidence"])

    def test_promotions_are_preserved_but_not_in_base_total_and_availability_stays_unknown(self) -> None:
        request = _request({"query": "oil", "amount": "2", "unit": "count"})
        promotion = PromotionEvidence(raw={"price": {"amount": "1.00"}, "condition": "loyalty"}, eligibility="UNKNOWN")
        result = evaluate_shopping_request(
            request,
            {"item-1": [_offer(line="item-1", product="oil", store="a", name="Oil", amount="10", package_amount="1", package_unit="count", distance="1", promotions=(promotion,), availability="UNKNOWN")]},
        )
        plan = result["cheapestSingleStore"]
        self.assertEqual(plan["totalArs"]["amount"], "20.00")
        self.assertEqual(plan["evidence"]["availability"], "UNKNOWN")
        self.assertEqual(plan["lines"][0]["promotions"][0]["eligibility"], "UNKNOWN")

    def test_policy_threshold_and_no_policy_behavior(self) -> None:
        request = _request(
            {"query": "one", "amount": "1", "unit": "count"},
            {"query": "two", "amount": "1", "unit": "count"},
        )
        offers = {
            "item-1": [_offer(line="item-1", product="one-a", store="a", name="One", amount="10", package_amount="1", package_unit="count", distance="1"), _offer(line="item-1", product="one-b", store="b", name="One", amount="1", package_amount="1", package_unit="count", distance="5")],
            "item-2": [_offer(line="item-2", product="two-a", store="a", name="Two", amount="1", package_amount="1", package_unit="count", distance="1"), _offer(line="item-2", product="two-b", store="b", name="Two", amount="10", package_amount="1", package_unit="count", distance="5")],
        }
        unresolved = evaluate_shopping_request(request, offers)
        self.assertEqual(unresolved["bestSensibleChoice"]["status"], "UNRESOLVED")
        threshold = evaluate_shopping_request(request, offers, policy=BestValuePolicy.from_mapping({"maxStores": 2, "maxStraightLineDistanceKm": "8", "minimumSavingsArsForExtraStore": "15"}))
        self.assertEqual(threshold["bestSensibleChoice"]["status"], "BEST_SENSIBLE_CHOICE_UNDER_POLICY")
        self.assertEqual(threshold["bestSensibleChoice"]["explanation"]["policyRule"], "EXTRA_STORE_SAVINGS_THRESHOLD")
        self.assertEqual(threshold["bestSensibleChoice"]["explanation"]["selectedAlternative"]["storeCount"], 1)
        low_threshold = evaluate_shopping_request(request, offers, policy=BestValuePolicy.from_mapping({"maxStores": 2, "maxStraightLineDistanceKm": "8", "minimumSavingsArsForExtraStore": "1"}))
        self.assertEqual(low_threshold["bestSensibleChoice"]["explanation"]["selectedAlternative"]["storeCount"], 2)

    def test_frontier_dominance_and_deterministic_repeat(self) -> None:
        request = _request({"query": "one", "amount": "1", "unit": "count"})
        offers = {"item-1": [
            _offer(line="item-1", product="near", store="near", name="One", amount="12", package_amount="1", package_unit="count", distance="1"),
            _offer(line="item-1", product="far", store="far", name="One", amount="5", package_amount="1", package_unit="count", distance="10"),
        ]}
        first = evaluate_shopping_request(request, offers)
        second = evaluate_shopping_request(request, offers)
        self.assertEqual(first, second)
        self.assertEqual(len(first["decisionFrontier"]), 2)
        self.assertEqual(json.dumps(first, sort_keys=True), json.dumps(second, sort_keys=True))

    def test_request_bounds_and_malformed_amounts_fail_closed(self) -> None:
        with self.assertRaises(ShoppingIntelligenceError):
            ShoppingRequest.from_mapping({"latitude": 0, "longitude": 0, "radiusKm": 1, "items": [{"query": "x", "amount": "0", "unit": "kg"}]})
        with self.assertRaises(ShoppingIntelligenceError):
            ShoppingRequest.from_mapping({"latitude": 0, "longitude": 0, "radiusKm": 1, "items": [{"query": "x", "amount": "-1", "unit": "kg"}]})
        with self.assertRaises(ShoppingIntelligenceError):
            ShoppingRequest.from_mapping({"latitude": 0, "longitude": 0, "radiusKm": 1, "items": [{"query": "x", "amount": "1,5", "unit": "kg"}]})
        with self.assertRaises(ShoppingIntelligenceError):
            ShoppingRequest.from_mapping({"latitude": 0, "longitude": 0, "radiusKm": 1, "items": [{"query": "x", "amount": "1", "unit": "kg"}] * 11})
        with self.assertRaises(ShoppingIntelligenceError):
            BestValuePolicy.from_mapping({"maxStores": "2"})

    def test_offer_coordinates_are_explicit_and_bounded(self) -> None:
        with self.assertRaises(ShoppingIntelligenceError):
            OfferEvidence(
                offer_key="offer:bad",
                product_evidence_key="product:bad",
                product_name="Bad coordinate",
                brand=None,
                store_key="store:bad",
                store_name="Bad",
                store_address=None,
                store_latitude=Decimal("91"),
                store_longitude=Decimal("0"),
                distance_km=Decimal("1"),
                price=ExactMoney.parse("1", positive=True),
                package_quantity=ExactQuantity.parse("1", "count"),
                freshness_status="FRESH",
                availability="UNKNOWN",
                promotions=(),
                provenance={"provider": "fixture"},
            )


if __name__ == "__main__":
    unittest.main()

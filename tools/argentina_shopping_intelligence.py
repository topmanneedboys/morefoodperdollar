#!/usr/bin/env python3
"""Argentina SEPA adapter for the provider-neutral shopping engine.

The adapter owns only translation of the already-qualified Milestone 5
micro-partition evidence.  It performs no title parsing, product substitution,
network access, clock reads, routing, or inventory inference.
"""

from __future__ import annotations

from decimal import Decimal
from pathlib import Path
from typing import Any, Mapping

try:
    from tools.argentina_sepa_micro_partition import query_micro_structured_request
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
except ModuleNotFoundError:  # direct ``python tools/argentina_shopping_intelligence.py`` invocation
    from argentina_sepa_micro_partition import query_micro_structured_request
    from shopping_intelligence_engine import BestValuePolicy, ExactMoney, ExactQuantity, OfferEvidence, PromotionEvidence, ShoppingIntelligenceError, ShoppingRequest, evaluate_shopping_request


ARGENTINA_ENGINE_SCHEMA_VERSION = "valuepilot-argentina-shopping-intelligence-v1"
ARGENTINA_PROVIDER = "ARGENTINA_SEPA_PRECIOS_CLAROS"
RELEASE_DATE = "2026-09-06"
ARS = "ARS"


def _provider_quantity(raw_offer: Mapping[str, Any]) -> ExactQuantity | None:
    if raw_offer.get("quantityStatus") != "KNOWN":
        return None
    quantity = raw_offer.get("quantity")
    if not isinstance(quantity, Mapping):
        return None
    value = quantity.get("value")
    unit = quantity.get("unit")
    if value is None or unit is None:
        return None
    try:
        return ExactQuantity.from_provider(value, unit)
    except ShoppingIntelligenceError:
        return None


def _offer_from_provider(raw_offer: Mapping[str, Any]) -> OfferEvidence:
    list_price = raw_offer.get("listPrice")
    if not isinstance(list_price, Mapping) or list_price.get("currency") != ARS:
        raise ShoppingIntelligenceError("Argentina offer currency is not ARS")
    price = ExactMoney.parse(list_price.get("amount"), currency=ARS, positive=True)
    latitude = Decimal(str(raw_offer["storeLatitude"]))
    longitude = Decimal(str(raw_offer["storeLongitude"]))
    distance = Decimal(str(raw_offer["distanceKm"]))
    promotions: list[PromotionEvidence] = []
    raw_promotions = raw_offer.get("promotions", [])
    if not isinstance(raw_promotions, list):
        raise ShoppingIntelligenceError("promotion evidence is malformed")
    for promotion in raw_promotions:
        if not isinstance(promotion, Mapping):
            raise ShoppingIntelligenceError("promotion evidence is malformed")
        eligibility = promotion.get("eligibility", "UNKNOWN")
        if not isinstance(eligibility, str):
            raise ShoppingIntelligenceError("promotion eligibility is malformed")
        promotions.append(PromotionEvidence(raw=dict(promotion), eligibility=eligibility))
    provenance = {
        "provider": ARGENTINA_PROVIDER,
        "releaseDate": raw_offer.get("releaseDate"),
        "packageProvenance": raw_offer.get("packageProvenance"),
        "sourceRow": raw_offer.get("sourceRow"),
        "providerUpdateTime": raw_offer.get("providerUpdateTime"),
        "freshnessStatus": raw_offer.get("freshnessStatus"),
    }
    if provenance["releaseDate"] != RELEASE_DATE:
        raise ShoppingIntelligenceError("Argentina offer release date is not the qualified release")
    store_name = raw_offer.get("storeName")
    product_name = raw_offer.get("name")
    store_key = raw_offer.get("storeKey")
    product_key = raw_offer.get("productEvidenceKey")
    offer_key = raw_offer.get("offerKey")
    if not all(isinstance(value, str) and value for value in (store_name, product_name, store_key, product_key, offer_key)):
        raise ShoppingIntelligenceError("Argentina offer identity is incomplete")
    validated_gtin = raw_offer.get("gtin") if raw_offer.get("gtinStatus") == "VALID" and isinstance(raw_offer.get("gtin"), str) else None
    return OfferEvidence(
        offer_key=offer_key,
        product_evidence_key=product_key,
        product_name=product_name,
        brand=raw_offer.get("brand") if isinstance(raw_offer.get("brand"), str) else None,
        store_key=store_key,
        store_name=store_name,
        store_address=raw_offer.get("storeAddress") if isinstance(raw_offer.get("storeAddress"), Mapping) else None,
        store_latitude=latitude,
        store_longitude=longitude,
        distance_km=distance,
        price=price,
        package_quantity=_provider_quantity(raw_offer),
        freshness_status=raw_offer.get("freshnessStatus", "UNKNOWN"),
        availability=raw_offer.get("availability", "UNKNOWN"),
        promotions=tuple(promotions),
        provenance=provenance,
        gtin=validated_gtin,
    )


def evaluate_argentina_request(
    snapshot_root: Path | str,
    region_id: str,
    request: ShoppingRequest | Mapping[str, Any],
    *,
    product_limit: int = 5,
    max_candidates: int = 100_000,
    max_offers: int = 100_000,
    policy: BestValuePolicy | Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    """Evaluate a structured request against one verified Argentina region."""

    normalized_request = request if isinstance(request, ShoppingRequest) else ShoppingRequest.from_mapping(request)
    normalized_policy = policy if isinstance(policy, BestValuePolicy) or policy is None else BestValuePolicy.from_mapping(policy)
    provider_result = query_micro_structured_request(
        Path(snapshot_root),
        region_id,
        latitude=decimal_text(normalized_request.latitude),
        longitude=decimal_text(normalized_request.longitude),
        radius_km=decimal_text(normalized_request.radius_km),
        items=normalized_request.as_query_items(),
        product_limit=product_limit,
        max_candidates=max_candidates,
        max_offers=max_offers,
    )
    return evaluate_argentina_provider_result(
        region_id,
        normalized_request,
        provider_result,
        policy=normalized_policy,
    )


def evaluate_argentina_provider_result(
    region_id: str,
    request: ShoppingRequest | Mapping[str, Any],
    provider_result: Mapping[str, Any],
    *,
    policy: BestValuePolicy | Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    """Evaluate one already-fetched provider result without querying again.

    This split keeps production evaluation simple while allowing bounded
    performance diagnostics to measure the decision engine separately from the
    already-verified micro-partition lookup.
    """

    normalized_request = request if isinstance(request, ShoppingRequest) else ShoppingRequest.from_mapping(request)
    normalized_policy = policy if isinstance(policy, BestValuePolicy) or policy is None else BestValuePolicy.from_mapping(policy)
    raw_items = provider_result.get("items")
    if not isinstance(raw_items, list) or len(raw_items) != len(normalized_request.lines):
        raise ShoppingIntelligenceError("Argentina provider response does not preserve request line order")
    offers_by_line: dict[str, list[OfferEvidence]] = {}
    rejected = {"malformed": 0, "stale": 0, "unknownQuantity": 0, "invalidPrice": 0}
    nearby_stores: set[str] = set()
    product_candidates = 0
    for line, raw_item in zip(normalized_request.lines, raw_items):
        if not isinstance(raw_item, Mapping):
            raise ShoppingIntelligenceError("Argentina provider item is malformed")
        candidates = raw_item.get("productCandidates", [])
        if isinstance(candidates, list):
            product_candidates += len({candidate.get("productEvidenceKey") for candidate in candidates if isinstance(candidate, Mapping) and isinstance(candidate.get("productEvidenceKey"), str)})
        raw_offers = raw_item.get("offers", [])
        if not isinstance(raw_offers, list):
            raise ShoppingIntelligenceError("Argentina provider offers are malformed")
        line_offers: list[OfferEvidence] = []
        for raw_offer in raw_offers:
            if not isinstance(raw_offer, Mapping):
                rejected["malformed"] += 1
                continue
            store_key = raw_offer.get("storeKey")
            if isinstance(store_key, str):
                nearby_stores.add(store_key)
            if raw_offer.get("freshnessStatus") != "FRESH":
                rejected["stale"] += 1
                continue
            if raw_offer.get("quantityStatus") != "KNOWN" or _provider_quantity(raw_offer) is None:
                rejected["unknownQuantity"] += 1
                continue
            try:
                line_offers.append(_offer_from_provider(raw_offer))
            except (KeyError, TypeError, ValueError, ShoppingIntelligenceError):
                rejected["invalidPrice"] += 1
        offers_by_line[line.line_id] = line_offers
    decision = evaluate_shopping_request(
        normalized_request,
        offers_by_line,
        nearby_store_count=len(nearby_stores),
        product_candidate_count=product_candidates,
        policy=normalized_policy,
    )
    decision["diagnostics"]["providerRejected"] = rejected
    decision["diagnostics"]["providerOffersReturned"] = sum(len(values) for values in offers_by_line.values())
    return {
        "schemaVersion": ARGENTINA_ENGINE_SCHEMA_VERSION,
        "provider": ARGENTINA_PROVIDER,
        "releaseDate": RELEASE_DATE,
        "regionId": region_id,
        "productionUiAuthorized": False,
        "androidNetworkingAuthorized": False,
        "request": normalized_request.as_dict(),
        "decision": decision,
        "queryPlan": provider_result.get("queryPlan"),
        "providerSafety": {
            "availability": "UNKNOWN",
            "pricePublicationIsNotInventory": True,
            "promotions": "UNKNOWN_ELIGIBILITY_NOT_INCLUDED_IN_BASE_TOTAL",
            "distance": "STRAIGHT_LINE_HAVERSINE_ONLY",
            "rawProviderRowsPublished": False,
        },
    }


def decimal_text(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


__all__ = [
    "ARGENTINA_ENGINE_SCHEMA_VERSION",
    "evaluate_argentina_provider_result",
    "evaluate_argentina_request",
]

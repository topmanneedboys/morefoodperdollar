"""Release-bound full-set price ordering for additive catalog discovery.

The immutable catalog still owns meaning. The existing reader and exact-money
engine own prices. This adapter joins their exact keys before product paging.
"""
from __future__ import annotations

import base64
import hashlib
import json
from decimal import Decimal, InvalidOperation
from fractions import Fraction
from typing import Any, Mapping

from .reader import BackendQueryError
from tools.shopping_intelligence_engine import ExactMoney, ExactQuantity, ShoppingIntelligenceError

SORTS = frozenset({"name", "pack", "unit", "distance"})
BASES = frozenset({"100 g", "100 ml", "1 count"})
# Bounded exact-offer join used only when the full join exceeds the safety cap.
MAX_SCOPED_OFFER_IDENTITIES = 24


def _amount(offer: Mapping[str, Any], sort: str) -> Fraction | None:
    try:
        if sort == "distance":
            value = Decimal(str(offer["distanceKm"]))
            return Fraction(value) if value.is_finite() and value >= 0 else None
        money = offer.get("listPrice")
        if not isinstance(money, Mapping) or money.get("currency") != "ARS":
            return None
        price = ExactMoney.parse(money.get("amount"), currency="ARS", positive=True)
        if sort != "unit":
            return Fraction(price.amount)
        if offer.get("quantityStatus") != "KNOWN":
            return None
        quantity = offer.get("quantity")
        if not isinstance(quantity, Mapping):
            return None
        package = ExactQuantity.from_provider(quantity.get("value"), quantity.get("unit"))
        if package.base_amount <= 0:
            return None
        multiplier = 100 if package.dimension in {"MASS", "VOLUME"} else 1
        return Fraction(price.amount) * multiplier / Fraction(package.base_amount)
    except (KeyError, InvalidOperation, TypeError, ValueError, ShoppingIntelligenceError):
        return None


def _offer_order(offer: Mapping[str, Any], sort: str):
    amount = _amount(offer, sort)
    return (amount is None, amount or Fraction(0), str(offer.get("storeKey", "")), str(offer.get("offerKey", "")))


def scoped_search(index, reader, handle, payload: Mapping[str, Any], selections, arrange=None):
    # The active immutable release must match the classifier's source release.
    if index.metadata["release_id"] != handle.release_id:
        raise BackendQueryError("DISCOVERY_PRICE_RELEASE_MISMATCH")
    sort = payload.get("sort", "name")
    if sort not in SORTS:
        raise BackendQueryError("discovery sort is invalid")
    basis = payload.get("unitBasis")
    if basis is not None and basis not in BASES:
        raise BackendQueryError("unit price basis is invalid")
    store_key = payload.get("storeKey")
    if store_key is not None and (not isinstance(store_key, str) or not store_key.strip() or len(store_key) > 256):
        raise BackendQueryError("store filter is invalid")
    request, query_fingerprint, predicates, keys, clarification = index.resolve_candidates({**payload, "cursor": None})
    context = {
        "contract": "valuepilot-scoped-discovery-v1", "queryFingerprint": query_fingerprint,
        "releaseId": handle.release_id, "indexVersion": index.metadata["source_sha256"],
        "latitude": str(Decimal(payload["latitude"]).normalize()),
        "longitude": str(Decimal(payload["longitude"]).normalize()),
        "radiusKm": str(Decimal(payload["radiusKm"]).normalize()),
        "sort": sort, "unitBasis": basis, "storeKey": store_key, "pageSize": request.page_size,
    }
    fingerprint = hashlib.sha256(json.dumps(context, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    offset = 0
    if payload.get("cursor"):
        try:
            raw = payload["cursor"]
            if not isinstance(raw, str) or len(raw) > 2048:
                raise ValueError("invalid cursor")
            cursor = json.loads(base64.urlsafe_b64decode(raw + "=" * (-len(raw) % 4)))
            offset = cursor["offset"]
            if (cursor.get("kind") != "scoped-product" or cursor.get("fingerprint") != fingerprint
                    or isinstance(offset, bool) or not isinstance(offset, int) or not 0 <= offset <= len(keys)):
                raise ValueError("cursor mismatch")
        except (KeyError, TypeError, ValueError) as exc:
            raise BackendQueryError("cursor does not match this scoped query") from exc
    offers = {}
    metrics = None
    windows: list[int] = []
    for window in (len(keys), MAX_SCOPED_OFFER_IDENTITIES, 16, 8, 4, 0):
        if window not in windows and window <= len(keys):
            windows.append(window)
    for window in windows:
        try:
            offers, metrics = reader.offers_for_identities(
                tuple(keys)[:window], selections=selections, latitude=Decimal(payload["latitude"]),
                longitude=Decimal(payload["longitude"]), radius_km=Decimal(payload["radiusKm"]),
            )
            break
        except BackendQueryError as exc:
            if "DISCOVERY_SCOPE_TOO_LARGE" not in str(exc) or window == 0:
                raise
    # Store choices cover the complete eligible offer set, not the visible page.
    stores = {}
    for bucket in offers.values():
        for offer in bucket:
            key = offer["storeKey"]
            distance = Decimal(str(offer["distanceKm"]))
            previous = stores.get(key)
            if previous is None or distance < Decimal(previous["distanceKm"]):
                stores[key] = {"storeKey": key, "storeName": offer.get("storeName"), "distanceKm": str(distance)}
    bases = sorted({offer["unitPrice"]["basis"] for bucket in offers.values() for offer in bucket
                    if isinstance(offer.get("unitPrice"), Mapping) and offer["unitPrice"].get("basis") in BASES
                    and (store_key is None or offer.get("storeKey") == store_key)})
    if sort == "unit" and basis is None and len(bases) > 1:
        raise BackendQueryError("DISCOVERY_UNIT_BASIS_REQUIRED: " + ", ".join(bases))
    effective_basis = basis or (bases[0] if sort == "unit" and bases else None)
    ranked = []
    qualified_total = 0
    for identity in index.matching_identities(keys):
        bucket = [offer for offer in offers.get(identity["identityKey"], ())
                  if (store_key is None or offer.get("storeKey") == store_key)
                  and (basis is None or (offer.get("unitPrice") or {}).get("basis") == basis)]
        if not bucket and (store_key is not None or basis is not None):
            continue
        qualified_total += len(bucket)
        bucket.sort(key=lambda offer: _offer_order(offer, sort if sort != "name" else "distance"))
        best = _amount(bucket[0], sort) if bucket and sort != "name" else None
        order = ((False, Fraction(0)) if sort == "name" else (best is None, best or Fraction(0)))
        ranked.append((order + (identity["displayName"].casefold(), identity["identityKey"]),
                       {**identity, "qualifiedOffers": bucket[:8], "qualifiedOfferCount": len(bucket)}))
    ranked.sort(key=lambda row: row[0])
    if arrange is not None:
        ranked = arrange(ranked)
    matches = [row[1] for row in ranked[offset:offset + request.page_size]]
    next_offset = offset + len(matches)
    has_more = next_offset < len(ranked)
    cursor = None
    if has_more:
        cursor = base64.urlsafe_b64encode(json.dumps({"kind": "scoped-product", "fingerprint": fingerprint,
            "offset": next_offset}, separators=(",", ":")).encode()).decode().rstrip("=")
    return {
        "contract": "valuepilot-discovery-v2", "status": "NEEDS_CLARIFICATION" if clarification else ("READY" if ranked else "NO_MATCHES"),
        "query": request.query, "language": request.language, "releaseId": handle.release_id,
        "releaseDate": handle.release_date, "freshness": handle.freshness_status,
        "indexVersion": index.metadata["source_sha256"], "queryFingerprint": fingerprint,
        "resolvedPredicates": list(predicates), "selections": list(request.selections),
        "total": {"productCount": len({row[1]["providerProductId"] for row in ranked}),
                  "sourceIdentityCount": len(ranked), "qualifiedOfferCount": qualified_total,
                  "countUnit": "DISTINCT_PROVIDER_PRODUCT"},
        "matches": matches, "pagination": {"pageSize": request.page_size, "hasMore": has_more, "nextCursor": cursor},
        "clarification": clarification, "clarificationCountScope": "CATALOG_BEFORE_GEOGRAPHIC_OFFER_FILTER",
        "scope": {**context, "effectiveUnitBasis": effective_basis, "sortScope": "ALL_MATCHING_IDENTITIES_BEFORE_PAGINATION",
                  "offerPreviewLimitPerIdentity": 8, "regionsQueried": metrics.regions_queried},
        "stores": sorted(stores.values(), key=lambda row: (Decimal(row["distanceKm"]), row["storeKey"])),
    }

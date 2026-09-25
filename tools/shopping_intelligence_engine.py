#!/usr/bin/env python3
"""Provider-neutral exact shopping intelligence for bounded basket requests.

This module deliberately contains no SEPA names, filesystem access, Android,
network, clock, routing, or natural-language parsing.  A provider adapter turns
qualified evidence into :class:`OfferEvidence` values and this module performs
only exact quantity/package arithmetic and deterministic plan selection.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, InvalidOperation, ROUND_CEILING
import itertools
import re
from typing import Any, Mapping, Sequence


ENGINE_SCHEMA_VERSION = "valuepilot-shopping-intelligence-v1"
MAX_REQUEST_ITEMS = 10
MAX_QUERY_LENGTH = 96
MAX_PACKAGE_COUNT = 1_000_000
MAX_STORES = 2_048
MAX_STORE_PAIRS = 2_000_000

_DECIMAL_RE = re.compile(r"-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?\Z")
_ID_RE = re.compile(r"[A-Za-z0-9_.:-]{1,240}\Z")


class ShoppingIntelligenceError(ValueError):
    """Invalid, ambiguous, or unbounded shopping-intelligence input."""


def _decimal(value: Any, label: str, *, positive: bool = False, nonnegative: bool = False) -> Decimal:
    if isinstance(value, bool) or value is None:
        raise ShoppingIntelligenceError(f"{label} must be an explicit decimal")
    if isinstance(value, Decimal):
        text = format(value, "f")
    elif isinstance(value, int):
        text = str(value)
    elif isinstance(value, str):
        text = value.strip()
    else:
        raise ShoppingIntelligenceError(f"{label} must be an explicit decimal")
    if not _DECIMAL_RE.fullmatch(text):
        raise ShoppingIntelligenceError(f"{label} has invalid decimal syntax")
    try:
        parsed = Decimal(text)
    except InvalidOperation as exc:
        raise ShoppingIntelligenceError(f"{label} is not finite") from exc
    if not parsed.is_finite():
        raise ShoppingIntelligenceError(f"{label} is not finite")
    if positive and parsed <= 0:
        raise ShoppingIntelligenceError(f"{label} must be positive")
    if nonnegative and parsed < 0:
        raise ShoppingIntelligenceError(f"{label} must be non-negative")
    return parsed


def decimal_text(value: Decimal) -> str:
    """Canonical, non-exponential decimal text without losing precision."""

    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def money_text(value: Decimal) -> str:
    """Canonical money text with at least two fractional digits."""

    text = format(value, "f")
    if "." not in text:
        return text + ".00"
    whole, fraction = text.split(".", 1)
    if len(fraction) < 2:
        fraction = fraction.ljust(2, "0")
    return whole + "." + fraction


@dataclass(frozen=True)
class ExactMoney:
    amount: Decimal
    currency: str = "ARS"

    def __post_init__(self) -> None:
        if not isinstance(self.amount, Decimal) or not self.amount.is_finite() or self.amount < 0:
            raise ShoppingIntelligenceError("money must be a finite non-negative Decimal")
        if not isinstance(self.currency, str) or not re.fullmatch(r"[A-Z]{3}", self.currency):
            raise ShoppingIntelligenceError("money currency must be an uppercase ISO-style code")

    @classmethod
    def parse(cls, value: Any, *, currency: str = "ARS", positive: bool = False) -> "ExactMoney":
        amount = _decimal(value, "price", positive=positive, nonnegative=not positive)
        return cls(amount=amount, currency=currency)

    def _same_currency(self, other: "ExactMoney") -> None:
        if self.currency != other.currency:
            raise ShoppingIntelligenceError("money currencies cannot be combined")

    def __add__(self, other: "ExactMoney") -> "ExactMoney":
        self._same_currency(other)
        return ExactMoney(self.amount + other.amount, self.currency)

    def __sub__(self, other: "ExactMoney") -> "ExactMoney":
        self._same_currency(other)
        result = self.amount - other.amount
        if result < 0:
            raise ShoppingIntelligenceError("money subtraction would become negative")
        return ExactMoney(result, self.currency)

    def multiply(self, count: int) -> "ExactMoney":
        if not isinstance(count, int) or count < 0 or count > MAX_PACKAGE_COUNT:
            raise ShoppingIntelligenceError("package count is outside the bounded range")
        return ExactMoney(self.amount * count, self.currency)

    def as_dict(self) -> dict[str, str]:
        return {"amount": money_text(self.amount), "currency": self.currency}


_UNIT_ALIASES: dict[str, tuple[str, Decimal, str]] = {
    "g": ("MASS", Decimal("1"), "g"),
    "gram": ("MASS", Decimal("1"), "g"),
    "grams": ("MASS", Decimal("1"), "g"),
    "gramo": ("MASS", Decimal("1"), "g"),
    "gramos": ("MASS", Decimal("1"), "g"),
    "kg": ("MASS", Decimal("1000"), "g"),
    "kilogram": ("MASS", Decimal("1000"), "g"),
    "kilograms": ("MASS", Decimal("1000"), "g"),
    "kilogramo": ("MASS", Decimal("1000"), "g"),
    "kilogramos": ("MASS", Decimal("1000"), "g"),
    "ml": ("VOLUME", Decimal("1"), "ml"),
    "millilitre": ("VOLUME", Decimal("1"), "ml"),
    "millilitres": ("VOLUME", Decimal("1"), "ml"),
    "milliliter": ("VOLUME", Decimal("1"), "ml"),
    "milliliters": ("VOLUME", Decimal("1"), "ml"),
    "mililitro": ("VOLUME", Decimal("1"), "ml"),
    "mililitros": ("VOLUME", Decimal("1"), "ml"),
    "cc": ("VOLUME", Decimal("1"), "ml"),
    "cm3": ("VOLUME", Decimal("1"), "ml"),
    "l": ("VOLUME", Decimal("1000"), "ml"),
    "lt": ("VOLUME", Decimal("1000"), "ml"),
    "litre": ("VOLUME", Decimal("1000"), "ml"),
    "litres": ("VOLUME", Decimal("1000"), "ml"),
    "liter": ("VOLUME", Decimal("1000"), "ml"),
    "liters": ("VOLUME", Decimal("1000"), "ml"),
    "litro": ("VOLUME", Decimal("1000"), "ml"),
    "litros": ("VOLUME", Decimal("1000"), "ml"),
    "count": ("COUNT", Decimal("1"), "count"),
    "counts": ("COUNT", Decimal("1"), "count"),
    "item": ("COUNT", Decimal("1"), "count"),
    "items": ("COUNT", Decimal("1"), "count"),
    "unit": ("COUNT", Decimal("1"), "count"),
    "units": ("COUNT", Decimal("1"), "count"),
    "each": ("COUNT", Decimal("1"), "count"),
    "ea": ("COUNT", Decimal("1"), "count"),
}

# Provider-shaped uppercase spellings are accepted only as explicit structured
# quantity evidence, never from a title or name.
_UNIT_ALIASES.update(
    {
        "GRAM": ("MASS", Decimal("1"), "g"),
        "GRAMS": ("MASS", Decimal("1"), "g"),
        "KILOGRAM": ("MASS", Decimal("1000"), "g"),
        "KILOGRAMS": ("MASS", Decimal("1000"), "g"),
        "KG": ("MASS", Decimal("1000"), "g"),
        "MILLILITRE": ("VOLUME", Decimal("1"), "ml"),
        "MILLILITRES": ("VOLUME", Decimal("1"), "ml"),
        "MILLILITER": ("VOLUME", Decimal("1"), "ml"),
        "MILLILITERS": ("VOLUME", Decimal("1"), "ml"),
        "ML": ("VOLUME", Decimal("1"), "ml"),
        "LITRE": ("VOLUME", Decimal("1000"), "ml"),
        "LITRES": ("VOLUME", Decimal("1000"), "ml"),
        "LITER": ("VOLUME", Decimal("1000"), "ml"),
        "LITERS": ("VOLUME", Decimal("1000"), "ml"),
        "L": ("VOLUME", Decimal("1000"), "ml"),
        "LT": ("VOLUME", Decimal("1000"), "ml"),
        "COUNT": ("COUNT", Decimal("1"), "count"),
        "UNI": ("COUNT", Decimal("1"), "count"),
        "UN": ("COUNT", Decimal("1"), "count"),
        "UNIT": ("COUNT", Decimal("1"), "count"),
        "EACH": ("COUNT", Decimal("1"), "count"),
    }
)


@dataclass(frozen=True)
class ExactQuantity:
    """A quantity normalized to grams, millilitres, or count."""

    dimension: str
    base_amount: Decimal
    base_unit: str
    input_unit: str
    allow_zero: bool = False

    def __post_init__(self) -> None:
        if self.dimension not in {"MASS", "VOLUME", "COUNT"}:
            raise ShoppingIntelligenceError("quantity dimension is unsupported")
        if self.base_unit not in {"g", "ml", "count"}:
            raise ShoppingIntelligenceError("quantity base unit is unsupported")
        if not self.base_amount.is_finite() or self.base_amount < 0 or (self.base_amount == 0 and not self.allow_zero):
            raise ShoppingIntelligenceError("quantity must be positive")

    @classmethod
    def parse(cls, amount: Any, unit: Any, *, label: str = "quantity") -> "ExactQuantity":
        if not isinstance(unit, str) or not unit.strip():
            raise ShoppingIntelligenceError(f"{label} unit must be explicit")
        unit_text = unit.strip()
        alias = _UNIT_ALIASES.get(unit_text)
        if alias is None:
            alias = _UNIT_ALIASES.get(unit_text.lower())
        if alias is None:
            raise ShoppingIntelligenceError(f"{label} unit is unsupported")
        dimension, factor, base_unit = alias
        value = _decimal(amount, f"{label} amount", positive=True)
        return cls(dimension, value * factor, base_unit, unit_text)

    @classmethod
    def from_provider(cls, value: Any, unit: Any, *, label: str = "package quantity") -> "ExactQuantity":
        """Parse only explicit provider quantity fields; never inspect a name."""

        return cls.parse(value, unit, label=label)

    def compatible(self, other: "ExactQuantity") -> bool:
        return self.dimension == other.dimension

    def in_unit(self, unit: str) -> Decimal:
        alias = _UNIT_ALIASES.get(unit) or _UNIT_ALIASES.get(unit.lower())
        if alias is None:
            raise ShoppingIntelligenceError("requested display unit is unsupported")
        dimension, factor, base_unit = alias
        if dimension != self.dimension or base_unit != self.base_unit:
            raise ShoppingIntelligenceError("quantity display unit has an incompatible dimension")
        return self.base_amount / factor

    def as_dict(self, *, display_unit: str | None = None) -> dict[str, str]:
        unit = display_unit or self.base_unit
        return {
            "dimension": self.dimension,
            "amount": decimal_text(self.in_unit(unit)),
            "unit": unit,
            "baseAmount": decimal_text(self.base_amount),
            "baseUnit": self.base_unit,
        }


@dataclass(frozen=True)
class ShoppingLine:
    line_id: str
    query: str
    requested_quantity: ExactQuantity

    def __post_init__(self) -> None:
        if not _ID_RE.fullmatch(self.line_id):
            raise ShoppingIntelligenceError("line id is invalid")
        if not isinstance(self.query, str) or not self.query.strip() or len(self.query.strip()) > MAX_QUERY_LENGTH:
            raise ShoppingIntelligenceError("shopping query is empty or too long")

    def as_dict(self) -> dict[str, Any]:
        return {
            "lineId": self.line_id,
            "query": self.query,
            "requestedQuantity": self.requested_quantity.as_dict(display_unit=self.requested_quantity.input_unit),
        }


@dataclass(frozen=True)
class ShoppingRequest:
    latitude: Decimal
    longitude: Decimal
    radius_km: Decimal
    lines: tuple[ShoppingLine, ...]

    def __post_init__(self) -> None:
        if not self.latitude.is_finite() or not Decimal("-90") <= self.latitude <= Decimal("90"):
            raise ShoppingIntelligenceError("latitude is outside bounds")
        if not self.longitude.is_finite() or not Decimal("-180") <= self.longitude <= Decimal("180"):
            raise ShoppingIntelligenceError("longitude is outside bounds")
        if not self.radius_km.is_finite() or self.radius_km < 0 or self.radius_km > Decimal("500"):
            raise ShoppingIntelligenceError("radiusKm is outside the bounded range")
        if not 1 <= len(self.lines) <= MAX_REQUEST_ITEMS:
            raise ShoppingIntelligenceError("shopping request must contain 1-10 lines")
        if len({line.line_id for line in self.lines}) != len(self.lines):
            raise ShoppingIntelligenceError("shopping line ids must be unique")

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "ShoppingRequest":
        if not isinstance(value, Mapping):
            raise ShoppingIntelligenceError("shopping request must be an object")
        raw_items = value.get("items")
        if not isinstance(raw_items, Sequence) or isinstance(raw_items, (str, bytes)):
            raise ShoppingIntelligenceError("shopping request items must be an array")
        if not 1 <= len(raw_items) <= MAX_REQUEST_ITEMS:
            raise ShoppingIntelligenceError("shopping request must contain 1-10 lines")
        lines: list[ShoppingLine] = []
        seen_ids: set[str] = set()
        for index, raw in enumerate(raw_items, start=1):
            if not isinstance(raw, Mapping):
                raise ShoppingIntelligenceError(f"item {index} must be an object")
            line_id = raw.get("lineId", f"item-{index}")
            if not isinstance(line_id, str) or line_id in seen_ids:
                raise ShoppingIntelligenceError(f"item {index} lineId is invalid or duplicated")
            seen_ids.add(line_id)
            query = raw.get("query")
            quantity = ExactQuantity.parse(raw.get("amount"), raw.get("unit"), label=f"item {index}")
            lines.append(ShoppingLine(line_id=line_id, query=query, requested_quantity=quantity))
        return cls(
            latitude=_decimal(value.get("latitude"), "latitude"),
            longitude=_decimal(value.get("longitude"), "longitude"),
            radius_km=_decimal(value.get("radiusKm"), "radiusKm", nonnegative=True),
            lines=tuple(lines),
        )

    def as_query_items(self) -> list[dict[str, str]]:
        return [
            {"lineId": line.line_id, "query": line.query, "amount": decimal_text(line.requested_quantity.in_unit(line.requested_quantity.input_unit)), "unit": line.requested_quantity.input_unit}
            for line in self.lines
        ]

    def as_dict(self) -> dict[str, Any]:
        return {
            "latitude": decimal_text(self.latitude),
            "longitude": decimal_text(self.longitude),
            "radiusKm": decimal_text(self.radius_km),
            "items": [line.as_dict() for line in self.lines],
        }


@dataclass(frozen=True)
class PromotionEvidence:
    raw: Mapping[str, Any]
    eligibility: str

    def as_dict(self) -> dict[str, Any]:
        return {"eligibility": self.eligibility, "raw": dict(self.raw)}


@dataclass(frozen=True)
class OfferEvidence:
    offer_key: str
    product_evidence_key: str
    product_name: str
    brand: str | None
    store_key: str
    store_name: str
    store_address: Mapping[str, Any] | None
    store_latitude: Decimal
    store_longitude: Decimal
    distance_km: Decimal
    price: ExactMoney
    package_quantity: ExactQuantity | None
    freshness_status: str
    availability: str
    promotions: tuple[PromotionEvidence, ...]
    provenance: Mapping[str, Any]
    gtin: str | None = None

    def __post_init__(self) -> None:
        for label, value in (("offer key", self.offer_key), ("product evidence key", self.product_evidence_key), ("store key", self.store_key), ("store name", self.store_name), ("product name", self.product_name)):
            if not isinstance(value, str) or not value:
                raise ShoppingIntelligenceError(f"{label} is missing")
        if not self.distance_km.is_finite() or self.distance_km < 0:
            raise ShoppingIntelligenceError("distance must be non-negative")
        if not self.store_latitude.is_finite() or not Decimal("-90") <= self.store_latitude <= Decimal("90"):
            raise ShoppingIntelligenceError("store latitude is outside bounds")
        if not self.store_longitude.is_finite() or not Decimal("-180") <= self.store_longitude <= Decimal("180"):
            raise ShoppingIntelligenceError("store longitude is outside bounds")
        if self.price.amount <= 0:
            raise ShoppingIntelligenceError("current price must be positive")
        if self.freshness_status != "FRESH":
            raise ShoppingIntelligenceError("only explicitly fresh current evidence is rankable")
        if not self.availability:
            raise ShoppingIntelligenceError("availability state is missing")

    def as_dict(self) -> dict[str, Any]:
        return {
            "offerKey": self.offer_key,
            "productEvidenceKey": self.product_evidence_key,
            "productName": self.product_name,
            "brand": self.brand,
            "gtin": self.gtin,
            "storeKey": self.store_key,
            "storeName": self.store_name,
            "distanceKm": decimal_text(self.distance_km),
            "price": self.price.as_dict(),
            "packageQuantity": self.package_quantity.as_dict() if self.package_quantity else None,
            "freshnessStatus": self.freshness_status,
            "availability": self.availability,
            "promotions": [promotion.as_dict() for promotion in self.promotions],
            "provenance": dict(self.provenance),
        }


@dataclass(frozen=True)
class BestValuePolicy:
    max_stores: int = 2
    max_straight_line_distance_km: Decimal = Decimal("500")
    minimum_savings_ars_for_extra_store: ExactMoney = ExactMoney(Decimal("0"), "ARS")

    def __post_init__(self) -> None:
        if self.max_stores not in {1, 2}:
            raise ShoppingIntelligenceError("maxStores must be 1 or 2")
        if self.max_straight_line_distance_km < 0:
            raise ShoppingIntelligenceError("maxStraightLineDistanceKm must be non-negative")
        if self.minimum_savings_ars_for_extra_store.currency != "ARS" or self.minimum_savings_ars_for_extra_store.amount < 0:
            raise ShoppingIntelligenceError("minimum extra-store savings must be non-negative ARS")

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "BestValuePolicy":
        if not isinstance(value, Mapping):
            raise ShoppingIntelligenceError("policy must be an object")
        raw_max_stores = value.get("maxStores", 2)
        if isinstance(raw_max_stores, bool) or not isinstance(raw_max_stores, int):
            raise ShoppingIntelligenceError("maxStores must be an integer")
        return cls(
            max_stores=raw_max_stores,
            max_straight_line_distance_km=_decimal(value.get("maxStraightLineDistanceKm", "500"), "maxStraightLineDistanceKm", nonnegative=True),
            minimum_savings_ars_for_extra_store=ExactMoney.parse(value.get("minimumSavingsArsForExtraStore", "0"), currency="ARS"),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "maxStores": self.max_stores,
            "maxStraightLineDistanceKm": decimal_text(self.max_straight_line_distance_km),
            "minimumSavingsArsForExtraStore": self.minimum_savings_ars_for_extra_store.as_dict(),
        }


@dataclass(frozen=True)
class PackagePlan:
    line_id: str
    product_evidence_key: str
    product_name: str
    brand: str | None
    offer_key: str
    store_key: str
    store_name: str
    distance_km: Decimal
    requested_quantity: ExactQuantity
    package_quantity: ExactQuantity
    package_count: int
    supplied_quantity: ExactQuantity
    excess_quantity: ExactQuantity
    line_total: ExactMoney
    price: ExactMoney
    availability: str
    freshness_status: str
    promotions: tuple[PromotionEvidence, ...]
    provenance: Mapping[str, Any]

    def as_dict(self) -> dict[str, Any]:
        return {
            "lineId": self.line_id,
            "productEvidenceKey": self.product_evidence_key,
            "productName": self.product_name,
            "brand": self.brand,
            "offerKey": self.offer_key,
            "storeKey": self.store_key,
            "storeName": self.store_name,
            "distanceKm": decimal_text(self.distance_km),
            "requestedQuantity": self.requested_quantity.as_dict(display_unit=self.requested_quantity.input_unit),
            "packageQuantity": self.package_quantity.as_dict(display_unit=self.requested_quantity.input_unit),
            "packageCount": self.package_count,
            "suppliedQuantity": self.supplied_quantity.as_dict(display_unit=self.requested_quantity.input_unit),
            "excessQuantity": self.excess_quantity.as_dict(display_unit=self.requested_quantity.input_unit),
            "lineTotal": self.line_total.as_dict(),
            "packagePrice": self.price.as_dict(),
            "availability": self.availability,
            "freshnessStatus": self.freshness_status,
            "promotions": [promotion.as_dict() for promotion in self.promotions],
            "provenance": dict(self.provenance),
        }


@dataclass(frozen=True)
class StorePlan:
    plan_type: str
    store_keys: tuple[str, ...]
    stores: tuple[Mapping[str, Any], ...]
    lines: tuple[PackagePlan, ...]
    total: ExactMoney
    max_distance_km: Decimal
    complete_price_evidence: bool
    missing_line_ids: tuple[str, ...]

    @property
    def store_count(self) -> int:
        return len(self.store_keys)

    @property
    def plan_id(self) -> str:
        return self.plan_type + ":" + "+".join(self.store_keys)

    def as_dict(self) -> dict[str, Any]:
        return {
            "type": self.plan_type,
            "planId": self.plan_id,
            "storeCount": self.store_count,
            "storeKeys": list(self.store_keys),
            "stores": [dict(store) for store in self.stores],
            "lines": [line.as_dict() for line in self.lines],
            "totalArs": self.total.as_dict(),
            "maxStraightLineDistanceKm": decimal_text(self.max_distance_km),
            "completePriceEvidence": self.complete_price_evidence,
            "missingLineIds": list(self.missing_line_ids),
            "evidence": {
                "availability": "UNKNOWN",
                "priceEvidence": "CURRENT_QUALIFIED",
                "promotions": "NOT_INCLUDED_UNLESS_ELIGIBILITY_KNOWN",
                "distance": "STRAIGHT_LINE_HAVERSINE_ONLY",
            },
        }


def _package_count(requested: ExactQuantity, package: ExactQuantity) -> int:
    if not requested.compatible(package):
        raise ShoppingIntelligenceError("incompatible package dimension")
    quotient = requested.base_amount / package.base_amount
    count_decimal = quotient.to_integral_value(rounding=ROUND_CEILING)
    if count_decimal > MAX_PACKAGE_COUNT:
        raise ShoppingIntelligenceError("required package count exceeds the bounded range")
    count = int(count_decimal)
    if count < 1:
        raise ShoppingIntelligenceError("package count must be positive")
    return count


def _make_package_plan(line: ShoppingLine, offer: OfferEvidence) -> PackagePlan | None:
    if offer.package_quantity is None or not line.requested_quantity.compatible(offer.package_quantity):
        return None
    count = _package_count(line.requested_quantity, offer.package_quantity)
    supplied = ExactQuantity(
        line.requested_quantity.dimension,
        offer.package_quantity.base_amount * count,
        offer.package_quantity.base_unit,
        line.requested_quantity.input_unit,
    )
    excess = ExactQuantity(
        line.requested_quantity.dimension,
        supplied.base_amount - line.requested_quantity.base_amount,
        supplied.base_unit,
        line.requested_quantity.input_unit,
        allow_zero=True,
    )
    return PackagePlan(
        line_id=line.line_id,
        product_evidence_key=offer.product_evidence_key,
        product_name=offer.product_name,
        brand=offer.brand,
        offer_key=offer.offer_key,
        store_key=offer.store_key,
        store_name=offer.store_name,
        distance_km=offer.distance_km,
        requested_quantity=line.requested_quantity,
        package_quantity=offer.package_quantity,
        package_count=count,
        supplied_quantity=supplied,
        excess_quantity=excess,
        line_total=offer.price.multiply(count),
        price=offer.price,
        availability=offer.availability,
        freshness_status=offer.freshness_status,
        promotions=offer.promotions,
        provenance=offer.provenance,
    )


def _package_sort_key(plan: PackagePlan) -> tuple[Any, ...]:
    return (
        plan.line_total.amount,
        plan.package_count,
        plan.excess_quantity.base_amount,
        plan.product_evidence_key,
        plan.offer_key,
        plan.store_key,
    )


def _money_sum(lines: Sequence[PackagePlan], currency: str) -> ExactMoney:
    total = ExactMoney(Decimal("0"), currency)
    for line in lines:
        total = total + line.line_total
    return total


def _store_info(offers: Sequence[OfferEvidence], store_key: str) -> dict[str, Any]:
    matching = [offer for offer in offers if offer.store_key == store_key]
    if not matching:
        raise ShoppingIntelligenceError("store metadata is missing")
    first = min(matching, key=lambda offer: (offer.distance_km, offer.offer_key))
    return {
        "storeKey": first.store_key,
        "storeName": first.store_name,
        "distanceKm": decimal_text(first.distance_km),
        "latitude": decimal_text(first.store_latitude),
        "longitude": decimal_text(first.store_longitude),
        "address": dict(first.store_address) if first.store_address else None,
    }


def _plan_sort_key(plan: StorePlan) -> tuple[Any, ...]:
    return (plan.total.amount, plan.store_count, plan.max_distance_km, plan.store_keys, plan.plan_id)


def _choose_line(left: PackagePlan, right: PackagePlan) -> PackagePlan:
    return min((left, right), key=_package_sort_key)


def _dominates(left: StorePlan, right: StorePlan) -> bool:
    no_worse = (
        left.total.amount <= right.total.amount
        and left.store_count <= right.store_count
        and left.max_distance_km <= right.max_distance_km
    )
    strictly_better = (
        left.total.amount < right.total.amount
        or left.store_count < right.store_count
        or left.max_distance_km < right.max_distance_km
    )
    return no_worse and strictly_better


def _frontier(plans: Sequence[StorePlan]) -> list[StorePlan]:
    unique: dict[str, StorePlan] = {plan.plan_id: plan for plan in plans}
    ordered = sorted(unique.values(), key=_plan_sort_key)
    return [candidate for candidate in ordered if not any(_dominates(other, candidate) for other in ordered if other.plan_id != candidate.plan_id)]


def _policy_result(policy: BestValuePolicy | None, *, all_plans: Sequence[StorePlan], cheapest: StorePlan | None, cheapest_single: StorePlan | None, frontier: Sequence[StorePlan]) -> dict[str, Any]:
    if policy is None:
        return {
            "status": "UNRESOLVED",
            "reason": "NO_EXPLICIT_POLICY",
            "frontierPlanIds": [plan.plan_id for plan in frontier],
        }
    allowed = [
        plan
        for plan in all_plans
        if plan.store_count <= policy.max_stores and plan.max_distance_km <= policy.max_straight_line_distance_km
    ]
    if not allowed:
        return {
            "status": "UNRESOLVED",
            "reason": "NO_COMPLETE_PLAN_WITHIN_EXPLICIT_POLICY",
            "policy": policy.as_dict(),
            "frontierPlanIds": [plan.plan_id for plan in frontier],
        }
    selected = min(allowed, key=_plan_sort_key)
    rule = "LOWEST_ALLOWED_EXACT_TOTAL"
    if selected.store_count > 1 and cheapest_single is not None:
        savings = cheapest_single.total.amount - selected.total.amount
        if savings < policy.minimum_savings_ars_for_extra_store.amount:
            eligible_singles = [plan for plan in allowed if plan.store_count == 1]
            if eligible_singles:
                selected = min(eligible_singles, key=_plan_sort_key)
                rule = "EXTRA_STORE_SAVINGS_THRESHOLD"
    comparison = cheapest
    difference = Decimal("0")
    store_difference = 0
    distance_difference = Decimal("0")
    if comparison is not None:
        difference = selected.total.amount - comparison.total.amount
        store_difference = selected.store_count - comparison.store_count
        distance_difference = selected.max_distance_km - comparison.max_distance_km
    return {
        "status": "BEST_SENSIBLE_CHOICE_UNDER_POLICY",
        "policy": policy.as_dict(),
        "selectedPlan": selected.as_dict(),
        "explanation": {
            "cheapestAlternative": comparison.as_dict() if comparison else None,
            "selectedAlternative": selected.as_dict(),
            "differenceArs": money_text(difference),
            "storeCountDifference": store_difference,
            "straightLineDistanceDifferenceKm": decimal_text(distance_difference),
            "policyRule": rule,
        },
    }


def evaluate_shopping_request(
    request: ShoppingRequest,
    offers_by_line: Mapping[str, Sequence[OfferEvidence]],
    *,
    nearby_store_count: int | None = None,
    product_candidate_count: int | None = None,
    policy: BestValuePolicy | None = None,
) -> dict[str, Any]:
    """Evaluate exact package plans for one bounded request.

    Every line is evaluated independently against one product-evidence identity;
    plans never combine unrelated identities or treat a promotion as a base
    price.  A store with missing lines remains visible only as incomplete price
    evidence and is never ranked by its partial subtotal.
    """

    requested_ids = {line.line_id for line in request.lines}
    if set(offers_by_line) - requested_ids:
        raise ShoppingIntelligenceError("offers contain a line outside the request")

    line_plans_by_store: dict[str, dict[str, PackagePlan]] = {}
    all_eligible_offers = [offer for values in offers_by_line.values() for offer in values]
    store_metadata_offers: dict[str, list[OfferEvidence]] = {}
    package_plans_evaluated = 0
    unknown_quantity_offers = 0
    incompatible_offers = 0
    for line in request.lines:
        by_store = line_plans_by_store.setdefault(line.line_id, {})
        product_best: dict[tuple[str, str], PackagePlan] = {}
        for offer in offers_by_line.get(line.line_id, ()):  # missing evidence remains unknown
            store_metadata_offers.setdefault(offer.store_key, []).append(offer)
            package_plan = _make_package_plan(line, offer)
            if offer.package_quantity is None:
                unknown_quantity_offers += 1
                continue
            if not line.requested_quantity.compatible(offer.package_quantity):
                incompatible_offers += 1
                continue
            package_plans_evaluated += 1
            product_key = (offer.store_key, offer.product_evidence_key)
            previous = product_best.get(product_key)
            if previous is None or _package_sort_key(package_plan) < _package_sort_key(previous):
                product_best[product_key] = package_plan
        for (store_key, _), package_plan in product_best.items():
            previous = by_store.get(store_key)
            if previous is None or _package_sort_key(package_plan) < _package_sort_key(previous):
                by_store[store_key] = package_plan

    store_keys = sorted({store for by_store in line_plans_by_store.values() for store in by_store})
    if len(store_keys) > MAX_STORES:
        raise ShoppingIntelligenceError("nearby compatible store bound exceeded")

    single_plans: list[StorePlan] = []
    for store_key in store_keys:
        lines = tuple(line_plans_by_store[line.line_id][store_key] for line in request.lines if store_key in line_plans_by_store[line.line_id])
        missing = tuple(line.line_id for line in request.lines if store_key not in line_plans_by_store[line.line_id])
        if not lines:
            continue
        total = _money_sum(lines, lines[0].line_total.currency)
        stores = (_store_info(store_metadata_offers[store_key], store_key),)
        single_plans.append(
            StorePlan(
                plan_type="SINGLE_STORE",
                store_keys=(store_key,),
                stores=stores,
                lines=lines,
                total=total,
                max_distance_km=max(line.distance_km for line in lines),
                complete_price_evidence=not missing,
                missing_line_ids=missing,
            )
        )

    complete_singles = [plan for plan in single_plans if plan.complete_price_evidence]
    cheapest_single = min(complete_singles, key=_plan_sort_key) if complete_singles else None
    closest_complete = min(complete_singles, key=lambda plan: (plan.max_distance_km, plan.total.amount, plan.store_keys)) if complete_singles else None

    pair_count = len(store_keys) * (len(store_keys) - 1) // 2
    if pair_count > MAX_STORE_PAIRS:
        raise ShoppingIntelligenceError("two-store combination bound exceeded")
    pair_plans: list[StorePlan] = []
    for left_key, right_key in itertools.combinations(store_keys, 2):
        selected_lines: list[PackagePlan] = []
        for line in request.lines:
            left = line_plans_by_store[line.line_id].get(left_key)
            right = line_plans_by_store[line.line_id].get(right_key)
            if left is None and right is None:
                break
            selected_lines.append(right if left is None else left if right is None else _choose_line(left, right))
        else:
            used_stores = tuple(sorted({line.store_key for line in selected_lines}))
            if len(used_stores) == 2:
                stores = tuple(_store_info(store_metadata_offers[key], key) for key in used_stores)
                pair_plans.append(
                    StorePlan(
                        plan_type="TWO_STORE",
                        store_keys=used_stores,
                        stores=stores,
                        lines=tuple(selected_lines),
                        total=_money_sum(selected_lines, selected_lines[0].line_total.currency),
                        max_distance_km=max(line.distance_km for line in selected_lines),
                        complete_price_evidence=True,
                        missing_line_ids=(),
                    )
                )

    all_complete_plans = complete_singles + pair_plans
    cheapest_up_to_two = min(all_complete_plans, key=_plan_sort_key) if all_complete_plans else None
    cheapest_two_store = min(pair_plans, key=_plan_sort_key) if pair_plans else None

    lower_bound = None
    line_minima: list[PackagePlan] = []
    for line in request.lines:
        candidates = list(line_plans_by_store[line.line_id].values())
        if not candidates:
            line_minima = []
            break
        line_minima.append(min(candidates, key=_package_sort_key))
    if len(line_minima) == len(request.lines):
        lower_bound = StorePlan(
            plan_type="REFERENCE_LOWER_BOUND",
            store_keys=tuple(sorted({line.store_key for line in line_minima})),
            stores=tuple(_store_info(store_metadata_offers[key], key) for key in sorted({line.store_key for line in line_minima})),
            lines=tuple(line_minima),
            total=_money_sum(line_minima, line_minima[0].line_total.currency),
            max_distance_km=max(line.distance_km for line in line_minima),
            complete_price_evidence=True,
            missing_line_ids=(),
        )

    frontier = _frontier(all_complete_plans)
    result = {
        "schemaVersion": ENGINE_SCHEMA_VERSION,
        "request": request.as_dict(),
        "cheapestSingleStore": cheapest_single.as_dict() if cheapest_single else None,
        "closestCompletePriceEvidenceStore": closest_complete.as_dict() if closest_complete else None,
        "cheapestTwoStoreCombination": cheapest_two_store.as_dict() if cheapest_two_store else None,
        "cheapestUpToTwoStores": cheapest_up_to_two.as_dict() if cheapest_up_to_two else None,
        "cheapestPerLineUnboundedStores": lower_bound.as_dict() if lower_bound else None,
        "decisionFrontier": [plan.as_dict() for plan in frontier],
        "bestSensibleChoice": _policy_result(policy, all_plans=all_complete_plans, cheapest=cheapest_up_to_two, cheapest_single=cheapest_single, frontier=frontier),
        "diagnostics": {
            "nearbyStoresConsidered": nearby_store_count if nearby_store_count is not None else len(store_metadata_offers),
            "compatibleStoresEvaluated": len(store_keys),
            "productCandidates": product_candidate_count if product_candidate_count is not None else None,
            "packagePlansEvaluated": package_plans_evaluated,
            "singleStorePlans": len(single_plans),
            "completeSingleStorePlans": len(complete_singles),
            "twoStoreCombinationsConsidered": pair_count,
            "twoStorePlans": len(pair_plans),
            "unknownQuantityOffers": unknown_quantity_offers,
            "incompatibleQuantityOffers": incompatible_offers,
            "bounded": True,
        },
        "safety": {
            "availability": "UNKNOWN",
            "completePriceEvidenceIsNotInventory": True,
            "promotions": "NOT_INCLUDED_UNLESS_ELIGIBILITY_KNOWN",
            "distance": "STRAIGHT_LINE_HAVERSINE_ONLY",
            "routing": "NOT_PROVIDED",
            "productIdentity": "ONE_PRODUCT_EVIDENCE_IDENTITY_PER_LINE",
        },
    }
    return result


__all__ = [
    "BestValuePolicy",
    "ENGINE_SCHEMA_VERSION",
    "ExactMoney",
    "ExactQuantity",
    "OfferEvidence",
    "PromotionEvidence",
    "ShoppingIntelligenceError",
    "ShoppingLine",
    "ShoppingRequest",
    "StorePlan",
    "decimal_text",
    "evaluate_shopping_request",
    "money_text",
]

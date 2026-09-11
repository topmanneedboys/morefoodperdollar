#!/usr/bin/env python3
"""Bounded, deterministic consumer shopping-input interpretation.

This module deliberately sits at the provider/application edge.  It turns a
short human shopping request into labelled, provider-neutral intent and (only
when the input is complete) an exact structured request for the existing
shopping engine.  It never creates a product, price, offer, store or
availability fact.  Catalog records are supplied by a qualified release and
remain the only source of product identities.

The implementation uses a small data-driven Argentine/English vocabulary and a
catalog-aware trigram index.  Fuzzy scoring is only candidate generation and
never bypasses the explicit product-form, variant, quantity or ambiguity gates.
"""

from __future__ import annotations

import json
import math
import re
import sqlite3
import time
import unicodedata
from collections import defaultdict
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence


SCHEMA_VERSION = "valuepilot-argentina-consumer-input-intelligence-v1"
VOCABULARY_SCHEMA_VERSION = "valuepilot-argentina-input-vocabulary-v1"
POLICY_VERSION = "valuepilot-argentina-input-policy-v1"
MAX_RAW_TEXT = 4096
MAX_LINE_LENGTH = 256
MAX_LINES = 10
MAX_TOKENS_PER_LINE = 32
MAX_CANDIDATES = 256
MAX_FUZZY_COMPARISONS = 512
MAX_SUGGESTIONS = 3
CANDIDATE_HORIZON_REACHED = "CANDIDATE_HORIZON_REACHED"
AMBIGUITY_GAP_SCORE = 70
FUZZY_MIN_SIMILARITY = 780
SHORT_MIN_SIMILARITY = 900

RESOLVED_EXACT = "RESOLVED_EXACT"
RESOLVED_ALIAS = "RESOLVED_ALIAS"
RESOLVED_SAFE_CORRECTION = "RESOLVED_SAFE_CORRECTION"
NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION"
NO_SAFE_MATCH = "NO_SAFE_MATCH"
RESULT_STATES = frozenset({RESOLVED_EXACT, RESOLVED_ALIAS, RESOLVED_SAFE_CORRECTION, NEEDS_CLARIFICATION, NO_SAFE_MATCH})

PACKAGE_SIZE_REQUIRED = "PACKAGE_SIZE_REQUIRED"
QUANTITY_REQUIRED = "QUANTITY_REQUIRED"
AMBIGUOUS_PRODUCT = "AMBIGUOUS_PRODUCT"
DIMENSION_MISMATCH = "DIMENSION_MISMATCH"
MULTIPLE_PLAUSIBLE_PRODUCTS = "MULTIPLE_PLAUSIBLE_PRODUCTS"

_DEFAULT_DATA_PATH = Path(__file__).with_name("data") / "argentina_input_aliases.json"

# The input layer accepts the same canonical display units as the M6 engine,
# plus explicit pounds which are converted exactly to grams here.
_UNIT_ALIASES: dict[str, tuple[str, Decimal, str]] = {
    "g": ("MASS", Decimal("1"), "g"),
    "gram": ("MASS", Decimal("1"), "g"),
    "grams": ("MASS", Decimal("1"), "g"),
    "gramo": ("MASS", Decimal("1"), "g"),
    "gramos": ("MASS", Decimal("1"), "g"),
    "kg": ("MASS", Decimal("1000"), "g"),
    "kilo": ("MASS", Decimal("1000"), "g"),
    "kilos": ("MASS", Decimal("1000"), "g"),
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
    "lb": ("MASS", Decimal("453.59237"), "g"),
    "lbs": ("MASS", Decimal("453.59237"), "g"),
    "pound": ("MASS", Decimal("453.59237"), "g"),
    "pounds": ("MASS", Decimal("453.59237"), "g"),
    "libra": ("MASS", Decimal("453.59237"), "g"),
    "libras": ("MASS", Decimal("453.59237"), "g"),
    "count": ("COUNT", Decimal("1"), "count"),
    "counts": ("COUNT", Decimal("1"), "count"),
    "item": ("COUNT", Decimal("1"), "count"),
    "items": ("COUNT", Decimal("1"), "count"),
    "unit": ("COUNT", Decimal("1"), "count"),
    "units": ("COUNT", Decimal("1"), "count"),
    "unidad": ("COUNT", Decimal("1"), "count"),
    "unidades": ("COUNT", Decimal("1"), "count"),
    "each": ("COUNT", Decimal("1"), "count"),
    "ea": ("COUNT", Decimal("1"), "count"),
}

_FRACTION_CHARS = {"½": Decimal("0.5"), "¼": Decimal("0.25"), "¾": Decimal("0.75")}
_NUMBER_RE = re.compile(r"(?<![A-Za-zÀ-ÿ])(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?|[½¼¾])(?![A-Za-zÀ-ÿ/])", re.UNICODE)
_UNIT_RE = re.compile(r"(?<![A-Za-zÀ-ÿ])(?:kg|kilos?|kilogramos?|g|gramos?|lb|lbs|pounds?|libras?|ml|mililitros?|cc|cm3|lt|litros?|l|count|units?|unidades?|each|ea)(?![A-Za-zÀ-ÿ])", re.IGNORECASE | re.UNICODE)
_TOKEN_RE = re.compile(r"[a-z0-9]+", re.IGNORECASE)
_RAW_TOKEN_RE = re.compile(r"[A-Za-z0-9]+")
_SEPARATOR_RE = re.compile(r"[;,\n\r]+")
_CONJUNCTION_RE = re.compile(r"\s+(?:and|y)\s+", re.IGNORECASE)
_FILLER_JOIN_RE = re.compile(r"\s+", re.UNICODE)


class InputIntelligenceError(ValueError):
    """A bounded input-intelligence request or catalog is invalid."""


def _decimal_text(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def _parse_decimal(value: str, *, label: str = "number") -> Decimal:
    text = value.strip()
    if not text or len(text) > 32:
        raise InputIntelligenceError(f"{label} is invalid")
    if text in _FRACTION_CHARS:
        return _FRACTION_CHARS[text]
    if re.fullmatch(r"\d+\s+\d+/\d+", text):
        whole, numerator, denominator = re.fullmatch(r"(\d+)\s+(\d+)/(\d+)", text).groups()  # type: ignore[union-attr]
        if int(denominator) == 0:
            raise InputIntelligenceError(f"{label} has a zero denominator")
        result = Decimal(whole) + (Decimal(numerator) / Decimal(denominator))
    elif re.fullmatch(r"\d+/\d+", text):
        numerator, denominator = text.split("/", 1)
        if int(denominator) == 0:
            raise InputIntelligenceError(f"{label} has a zero denominator")
        result = Decimal(numerator) / Decimal(denominator)
    else:
        if text.count(",") and text.count("."):
            raise InputIntelligenceError(f"{label} has mixed decimal punctuation")
        result = Decimal(text.replace(",", "."))
    if not result.is_finite() or result <= 0:
        raise InputIntelligenceError(f"{label} must be positive")
    return result


def normalize_text(value: str, *, max_length: int = MAX_RAW_TEXT) -> str:
    """Return a stable accent-insensitive comparison form.

    Decimal commas are converted only when they are between digits.  Other
    punctuation is a separator, never a numeric deletion.
    """

    if not isinstance(value, str):
        raise InputIntelligenceError("shopping text must be a string")
    if len(value) > max_length:
        raise InputIntelligenceError(f"shopping text exceeds {max_length} characters")
    # Replace vulgar fractions before NFKC: Unicode compatibility
    # normalization expands ``½`` into ``1⁄2`` and would otherwise destroy
    # the exact fraction token.
    text = value
    for source, replacement in _FRACTION_CHARS.items():
        text = text.replace(source, f" {replacement} ")
    text = unicodedata.normalize("NFKC", text).casefold()
    text = re.sub(r"(?<=\d)\s*,\s*(?=\d)", ".", text)
    text = re.sub(r"(?<=\d)\s*\.\s*(?=\d)", ".", text)
    # Retail notation commonly writes ``2.25L`` or ``1kg``.  Insert a
    # comparison boundary without changing the numeric value.
    text = re.sub(r"(?<=\d)(?=[A-Za-zÀ-ÿ])", " ", text)
    text = re.sub(r"(?<=[A-Za-zÀ-ÿ])(?=\d)", " ", text)
    text = text.replace("’", "'")
    # Keep a slash only when it is the explicit numeric fraction separator;
    # slash punctuation elsewhere remains a token boundary.
    text = re.sub(r"(?<=\d)\s*/\s*(?=\d)", "/", text)
    text = re.sub(r"[-_']+", " ", text)
    text = re.sub(r"(?<!\d)/+|/+(?!\d)", " ", text)
    decomposed = unicodedata.normalize("NFKD", text)
    text = "".join(char for char in decomposed if not unicodedata.combining(char))
    text = re.sub(r"[^a-z0-9./\s]+", " ", text)
    return " ".join(text.split())


def _joined_key(value: str) -> str:
    return "".join(normalize_text(value).split())


def _tokens(value: str) -> tuple[str, ...]:
    normalized = normalize_text(value, max_length=MAX_LINE_LENGTH)
    values = tuple(_TOKEN_RE.findall(normalized))
    if len(values) > MAX_TOKENS_PER_LINE:
        raise InputIntelligenceError("shopping line contains too many tokens")
    return values


def _trigrams(value: str) -> tuple[str, ...]:
    compact = f"  {_joined_key(value)}  "
    if len(compact) <= 3:
        return (compact,)
    return tuple(sorted({compact[index : index + 3] for index in range(len(compact) - 2)}))


def _interior_trigrams(value: str) -> tuple[str, ...]:
    """Return boundary-free trigrams used by the bounded candidate horizon."""

    return tuple(sorted({value[index : index + 3] for index in range(max(0, len(value) - 2))}))


def _intent_phrases(intent: "ParsedIntent", data: Mapping[str, Any]) -> tuple[str, ...]:
    """Return the exact phrase universe used by catalog candidate lookup."""

    phrases = [intent.normalized, " ".join(intent.search_tokens)]
    if intent.brand:
        phrases.extend((intent.brand, _joined_key(intent.brand)))
    if intent.concept:
        phrases.append(intent.concept)
        aliases = data.get("aliases", {})
        if isinstance(aliases, Mapping):
            phrases.extend(str(alias) for alias, target in aliases.items() if target == intent.concept)
    return tuple(dict.fromkeys(value for value in phrases if value))


def _phrase_features(phrases: Iterable[str]) -> set[tuple[str, str]]:
    """Build a small lexical feature set without retaining catalog records."""

    features: set[tuple[str, str]] = set()
    for phrase in phrases:
        normalized = normalize_text(phrase, max_length=512)
        if not normalized:
            continue
        features.add(("exact", normalized))
        # ``normalized`` has already passed the length/character policy; use
        # its whitespace tokens directly so the streamed path does not
        # normalize the same catalog phrase three times per record.
        features.add(("exact", "".join(normalized.split())))
        for token in normalized.split():
            features.add(("token", token))
            # A one-character prefix is intentionally not part of the
            # streaming horizon: on a national catalog it would make nearly
            # every record sharing a common vowel look plausible and would
            # saturate every ordinary query.  The shared index and streaming
            # horizon therefore use the same narrower gate, with
            # trigram/prefix-2 evidence preserving audited typo and
            # joined-token cases.
            for width in (2, 3, 4):
                if len(token) >= width:
                    features.add(("prefix", token[:width]))
            # Boundary-padded trigrams make a short token such as ``de``
            # overlap every word ending in ``e``.  The bounded horizon uses
            # interior trigrams only; prefix-2 evidence still covers the
            # audited one-edit/transpose cases without a national false-
            # positive fan-out.
            for trigram in _interior_trigrams(token):
                features.add(("trigram", trigram))
    return features


def _mapping_phrases(value: Mapping[str, Any]) -> tuple[str, ...]:
    """Validate the identity fields and return the same phrases as a record."""

    key = value.get("productEvidenceKey")
    name = value.get("name")
    if not isinstance(key, str) or not key or len(key) > 256:
        raise InputIntelligenceError("catalog productEvidenceKey is invalid")
    if not isinstance(name, str) or not name.strip() or len(name) > 512:
        raise InputIntelligenceError("catalog product name is invalid")
    aliases = value.get("canonicalSearchAliases", value.get("aliases", ()))
    if isinstance(aliases, str):
        aliases = (aliases,)
    if not isinstance(aliases, Sequence) or isinstance(aliases, (bytes, str)):
        aliases = ()
    normalized_aliases = tuple(str(item) for item in aliases if isinstance(item, str) and item.strip())[:8]
    return (name.strip(), *normalized_aliases, value.get("brand") if isinstance(value.get("brand"), str) else "")


def _phrases_match_features(phrases: Iterable[str], features: frozenset[tuple[str, str]]) -> bool:
    """Probe the shared lexical feature policy without allocating a record."""

    normalized_phrases: list[str] = []
    tokens: set[str] = set()
    for phrase in phrases:
        normalized = normalize_text(phrase, max_length=512)
        if not normalized:
            continue
        normalized_phrases.append(normalized)
        tokens.update(normalized.split())
    # Whole-token evidence is the common path for provider records.  Check it
    # before the more expensive prefix/trigram probes so a national stream can
    # discard unrelated rows without constructing every feature variant.
    if any(("token", token) in features for token in tokens):
        return True
    for normalized in normalized_phrases:
        if ("exact", normalized) in features or ("exact", "".join(normalized.split())) in features:
            return True
        for token in normalized.split():
            for width in (2, 3, 4):
                if len(token) >= width and ("prefix", token[:width]) in features:
                    return True
            for trigram in _interior_trigrams(token):
                if ("trigram", trigram) in features:
                    return True
    return False


def _raw_phrase_features(phrases: Iterable[str]) -> tuple[frozenset[tuple[str, str]], bool]:
    """Build an ASCII fast-path feature set and report normalization risk."""

    features: set[tuple[str, str]] = set()
    needs_normalized_fallback = False
    for phrase in phrases:
        if not phrase.isascii():
            needs_normalized_fallback = True
        tokens = tuple(token.casefold() for token in _RAW_TOKEN_RE.findall(phrase))
        if not tokens:
            continue
        normalized = " ".join(tokens)
        features.add(("exact", normalized))
        features.add(("exact", "".join(tokens)))
        for token in tokens:
            features.add(("token", token))
            for width in (2, 3, 4):
                if len(token) >= width:
                    features.add(("prefix", token[:width]))
            for trigram in _interior_trigrams(token):
                features.add(("trigram", trigram))
    return frozenset(features), needs_normalized_fallback


def _levenshtein_ratio(left: str, right: str) -> int:
    """Return a deterministic 0..1000 edit similarity without dependencies."""

    if left == right:
        return 1000
    if not left or not right:
        return 0
    previous = list(range(len(right) + 1))
    for row, left_char in enumerate(left, 1):
        current = [row]
        for column, right_char in enumerate(right, 1):
            current.append(min(current[-1] + 1, previous[column] + 1, previous[column - 1] + (left_char != right_char)))
        previous = current
    distance = previous[-1]
    return max(0, 1000 - (distance * 1000 // max(len(left), len(right))))


def _load_data(path: Path | str | None = None) -> dict[str, Any]:
    source = Path(path) if path is not None else _DEFAULT_DATA_PATH
    try:
        value = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InputIntelligenceError(f"input vocabulary data is unreadable: {source}") from exc
    if not isinstance(value, dict) or value.get("schemaVersion") != "valuepilot-argentina-input-aliases-v1":
        raise InputIntelligenceError("input vocabulary schema is invalid")
    return value


def sqlite_fts5_trigram_supported() -> bool:
    """Probe capability only; the runtime index remains provider-neutral."""

    try:
        connection = sqlite3.connect(":memory:")
        try:
            connection.execute("CREATE VIRTUAL TABLE probe USING fts5(value, tokenize='trigram remove_diacritics 2')")
            return True
        finally:
            connection.close()
    except sqlite3.Error:
        return False


@dataclass(frozen=True)
class QuantityValue:
    dimension: str
    base_amount: Decimal
    base_unit: str
    input_amount: Decimal
    input_unit: str

    def as_dict(self) -> dict[str, str]:
        return {
            "dimension": self.dimension,
            "amount": _decimal_text(self.input_amount),
            "unit": self.input_unit,
            "baseAmount": _decimal_text(self.base_amount),
            "baseUnit": self.base_unit,
        }


@dataclass(frozen=True)
class QuantitySpec:
    total: QuantityValue | None = None
    package_count: Decimal | None = None
    package_size: QuantityValue | None = None
    explicit: bool = False
    package_size_explicit: bool = False
    raw: str | None = None
    error: str | None = None

    def as_dict(self) -> dict[str, Any]:
        return {
            "status": "KNOWN" if self.total is not None else "UNSPECIFIED" if self.error is None else "INVALID",
            "total": self.total.as_dict() if self.total else None,
            "packageCount": _decimal_text(self.package_count) if self.package_count is not None else None,
            "packageSize": self.package_size.as_dict() if self.package_size else None,
            "explicit": self.explicit,
            "packageSizeExplicit": self.package_size_explicit,
            "raw": self.raw,
            "error": self.error,
        }


@dataclass(frozen=True)
class CatalogRecord:
    product_evidence_key: str
    name: str
    brand: str | None = None
    gtin: str | None = None
    quantity: Mapping[str, Any] | None = None
    form: str | None = None
    aliases: tuple[str, ...] = ()
    provenance: Mapping[str, Any] | None = None

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "CatalogRecord":
        key = value.get("productEvidenceKey")
        name = value.get("name")
        if not isinstance(key, str) or not key or len(key) > 256:
            raise InputIntelligenceError("catalog productEvidenceKey is invalid")
        if not isinstance(name, str) or not name.strip() or len(name) > 512:
            raise InputIntelligenceError("catalog product name is invalid")
        aliases = value.get("canonicalSearchAliases", value.get("aliases", ()))
        if isinstance(aliases, str):
            aliases = (aliases,)
        if not isinstance(aliases, Sequence) or isinstance(aliases, (bytes, str)):
            aliases = ()
        return cls(
            product_evidence_key=key,
            name=name.strip(),
            brand=value.get("brand") if isinstance(value.get("brand"), str) else None,
            gtin=value.get("gtin") if isinstance(value.get("gtin"), str) else None,
            quantity=value.get("quantity") if isinstance(value.get("quantity"), Mapping) else None,
            form=value.get("form") if isinstance(value.get("form"), str) else None,
            aliases=tuple(str(item) for item in aliases if isinstance(item, str) and item.strip())[:8],
            provenance=value.get("provenance") if isinstance(value.get("provenance"), Mapping) else None,
        )

    def as_dict(self) -> dict[str, Any]:
        value = {
            "productEvidenceKey": self.product_evidence_key,
            "name": self.name,
            "brand": self.brand,
            "gtin": self.gtin,
            "quantity": dict(self.quantity) if self.quantity else None,
            "form": self.form,
            "canonicalSearchAliases": list(self.aliases),
        }
        if self.provenance:
            value["provenance"] = dict(self.provenance)
        return value


@dataclass(frozen=True)
class _ScoredCandidate:
    record: CatalogRecord
    score: int
    similarity: int
    exact: bool
    alias: bool
    safe: bool
    safety_reason: str


@dataclass(frozen=True)
class ParsedIntent:
    normalized: str
    tokens: tuple[str, ...]
    concept: str | None
    brand: str | None
    requested_form: str | None
    variants: tuple[str, ...]
    negated_variants: tuple[str, ...]
    alias_used: bool
    typo_used: bool
    search_tokens: tuple[str, ...]

    def as_dict(self) -> dict[str, Any]:
        return {
            "normalized": self.normalized,
            "tokens": list(self.tokens),
            "concept": self.concept,
            "brand": self.brand,
            "requestedForm": self.requested_form,
            "variants": list(self.variants),
            "negatedVariants": list(self.negated_variants),
            "aliasUsed": self.alias_used,
            "typoUsed": self.typo_used,
            "searchTokens": list(self.search_tokens),
        }


@dataclass(frozen=True)
class ResolvedLine:
    line_id: str
    original_text: str
    normalized_text: str
    state: str
    intent: ParsedIntent
    candidate: CatalogRecord | None
    quantity: QuantitySpec
    correction: Mapping[str, Any] | None = None
    clarification: Mapping[str, Any] | None = None
    suggestions: tuple[CatalogRecord, ...] = ()
    structured_line: Mapping[str, Any] | None = None
    candidates_examined: int = 0

    def as_dict(self) -> dict[str, Any]:
        return {
            "lineId": self.line_id,
            "originalText": self.original_text,
            "normalizedText": self.normalized_text,
            "resolution": self.state,
            "normalizedIntent": self.intent.as_dict(),
            "recognizedProduct": self.candidate.as_dict() if self.candidate else None,
            "correction": dict(self.correction) if self.correction else None,
            "quantity": self.quantity.as_dict(),
            "clarification": dict(self.clarification) if self.clarification else None,
            "suggestions": [record.as_dict() for record in self.suggestions],
            "structuredLine": dict(self.structured_line) if self.structured_line else None,
            "candidatesExamined": self.candidates_examined,
        }


def _quantity_value(amount: Decimal, unit: str) -> QuantityValue:
    alias = _UNIT_ALIASES.get(unit.casefold())
    if alias is None:
        raise InputIntelligenceError(f"unsupported quantity unit: {unit}")
    dimension, factor, base_unit = alias
    return QuantityValue(dimension, amount * factor, base_unit, amount, unit.casefold())


def _find_number_unit(text: str) -> tuple[Decimal, str, int, int] | None:
    candidates: list[tuple[Decimal, str, int, int]] = []
    for match in _NUMBER_RE.finditer(text):
        end = match.end()
        suffix = text[end:]
        unit_match = _UNIT_RE.match(suffix.lstrip())
        if unit_match:
            unit = unit_match.group(0)
            start = match.start()
            unit_end = end + len(suffix) - len(suffix.lstrip()) + unit_match.end()
            try:
                candidates.append((_parse_decimal(match.group(0)), unit, start, unit_end))
            except InputIntelligenceError:
                raise
    return candidates[0] if candidates else None


def parse_quantity(text: str) -> QuantitySpec:
    """Parse explicit package/quantity evidence with exact Decimal arithmetic."""

    normalized = normalize_text(text, max_length=MAX_LINE_LENGTH)
    if not normalized:
        return QuantitySpec(raw=text)

    # Word forms which carry exact count semantics.
    if re.search(r"\bmedia\s+docena\b", normalized):
        value = _quantity_value(Decimal("6"), "count")
        return QuantitySpec(total=value, package_count=Decimal("6"), explicit=True, package_size_explicit=False, raw=text)
    if re.search(r"\b(?:una?\s+)?docena\b", normalized):
        value = _quantity_value(Decimal("12"), "count")
        return QuantitySpec(total=value, package_count=Decimal("12"), explicit=True, package_size_explicit=False, raw=text)
    if re.search(r"\bmedio\s+kilo\b|\bmedia\s+libra\b", normalized):
        value = _quantity_value(Decimal("0.5"), "kg" if "kilo" in normalized else "lb")
        return QuantitySpec(total=value, explicit=True, package_size_explicit=True, raw=text)

    # Count × size, including "2 x 2.25 L Sprite" and "Sprite 2.25L x2".
    first_count: Decimal | None = None
    count_match = re.search(r"(?<!\w)(\d+(?:[.,]\d+)?)\s*(?:x|×)\s*", normalized)
    if count_match:
        first_count = _parse_decimal(count_match.group(1), label="package count")
    trailing_count_match = re.search(r"\s*(?:x|×)\s*(\d+(?:[.,]\d+)?)\s*$", normalized)
    if trailing_count_match:
        first_count = _parse_decimal(trailing_count_match.group(1), label="package count")
    if first_count is None:
        pack_match = re.search(r"\b(?:pack|paquete|caja|botellas?|latas?|bottles?|cans?)\s*(?:x|×)?\s*(\d+(?:[.,]\d+)?)", normalized)
        if pack_match:
            first_count = _parse_decimal(pack_match.group(1), label="package count")

    value = _find_number_unit(normalized)
    if value is None:
        # A bare leading number next to a product/brand is a package count, not
        # an invented quantity.  It is handled as PACKAGE_SIZE_REQUIRED by the
        # resolver when the product is not naturally count-based.
        bare = re.match(r"^(\d+(?:[.,]\d+)?)\s+", normalized)
        if bare:
            count = _parse_decimal(bare.group(1), label="package count")
            return QuantitySpec(package_count=count, explicit=True, raw=text)
        return QuantitySpec(raw=text)

    amount, unit, start, end = value
    package_size = _quantity_value(amount, unit)
    if first_count is not None:
        total_base = package_size.base_amount * first_count
        total = QuantityValue(package_size.dimension, total_base, package_size.base_unit, package_size.input_amount * first_count, package_size.input_unit)
        return QuantitySpec(total=total, package_count=first_count, package_size=package_size, explicit=True, package_size_explicit=True, raw=text)

    # A number followed by an item/count word is a direct count request.  A
    # number followed by a known dimension is the direct quantity.
    return QuantitySpec(total=package_size, explicit=True, package_size_explicit=True, raw=text)


def _remove_quantity_text(normalized: str) -> str:
    text = normalized
    text = re.sub(r"\bmedia\s+docena\b|\bdocena\b|\bmedio\s+kilo\b|\bmedia\s+libra\b", " ", text)
    text = re.sub(r"\b(?:pack|paquete|caja|botellas?|latas?|bottles?|cans?)\s*(?:x|×)?\s*\d+(?:[.,]\d+)?", " ", text)
    text = re.sub(r"(?<!\w)\d+(?:[.,]\d+)?\s*(?:x|×)\s*", " ", text)
    text = re.sub(r"\s*(?:x|×)\s*\d+(?:[.,]\d+)?\s*$", " ", text)
    text = _NUMBER_RE.sub(" ", text)
    text = _UNIT_RE.sub(" ", text)
    return " ".join(text.split())


def _phrase_tokens(value: str) -> tuple[str, ...]:
    return tuple(_tokens(value))


def _contains_phrase(tokens: tuple[str, ...], phrase: str) -> bool:
    wanted = _phrase_tokens(phrase)
    return bool(wanted) and any(tokens[i : i + len(wanted)] == wanted for i in range(len(tokens) - len(wanted) + 1))


def _strip_fillers(text: str, fillers: Sequence[str]) -> str:
    result = normalize_text(text, max_length=MAX_LINE_LENGTH)
    ordered = sorted((normalize_text(item, max_length=MAX_LINE_LENGTH) for item in fillers), key=len, reverse=True)
    changed = True
    while changed and result:
        changed = False
        for filler in ordered:
            if result == filler:
                return ""
            if result.startswith(filler + " "):
                result = result[len(filler) :].strip()
                changed = True
                break
            if result.endswith(" " + filler):
                result = result[: -len(filler)].strip()
                changed = True
                break
    return result


def _split_lines(text: str, fillers: Sequence[str], known_phrases: Sequence[str]) -> list[str]:
    if not isinstance(text, str) or not text.strip():
        raise InputIntelligenceError("text must contain at least one shopping line")
    if len(text) > MAX_RAW_TEXT:
        raise InputIntelligenceError(f"text exceeds {MAX_RAW_TEXT} characters")
    pieces: list[str] = []
    for chunk in _SEPARATOR_RE.split(text):
        chunk = chunk.strip()
        if not chunk:
            continue
        # Protect a recognized multi-token phrase while splitting natural
        # conjunctions.  A conjunction inside a product phrase is not a list
        # boundary; otherwise the bounded split is useful for ordinary lists.
        normalized_chunk = normalize_text(chunk, max_length=MAX_LINE_LENGTH)
        # Known product phrases are protected only when the conjunction is
        # literally part of that phrase.  A phrase appearing elsewhere in the
        # chunk (for example ``tomato`` after ``and``) must not suppress list
        # segmentation.
        conjunction_in_phrase = any(
            re.search(r"\s+(?:and|y)\s+", normalize_text(phrase, max_length=MAX_LINE_LENGTH), re.IGNORECASE)
            and normalize_text(phrase, max_length=MAX_LINE_LENGTH) in normalized_chunk
            for phrase in known_phrases
        )
        if conjunction_in_phrase or not re.search(r"\s+(?:and|y)\s+", normalized_chunk, re.IGNORECASE):
            pieces.append(_strip_fillers(chunk, fillers))
            continue
        # Split only conjunctions surrounded by an apparent item boundary.
        for part in _CONJUNCTION_RE.split(chunk):
            value = _strip_fillers(part, fillers)
            if value:
                pieces.append(value)
    pieces = [piece for piece in pieces if piece]
    if not pieces:
        raise InputIntelligenceError("text contains no shopping lines")
    if len(pieces) > MAX_LINES:
        raise InputIntelligenceError(f"shopping input exceeds {MAX_LINES} lines")
    if any(len(piece) > MAX_LINE_LENGTH for piece in pieces):
        raise InputIntelligenceError(f"shopping line exceeds {MAX_LINE_LENGTH} characters")
    return pieces


def split_shopping_lines(
    text: str,
    *,
    data_path: Path | str | None = None,
    data: Mapping[str, Any] | None = None,
) -> tuple[str, ...]:
    """Split shopper text using the interpreter's single shared policy."""

    source = data if data is not None else _load_data(data_path)
    fillers = source.get("fillers", [])
    if not isinstance(fillers, Sequence) or isinstance(fillers, (str, bytes)):
        fillers = ()
    phrases: list[str] = []
    brands = source.get("brands", {})
    if isinstance(brands, Mapping):
        phrases.extend(str(value) for value in brands.keys())
    aliases = source.get("aliases", {})
    if isinstance(aliases, Mapping):
        phrases.extend(str(value) for value in aliases.keys())
    return tuple(_split_lines(text, fillers, phrases))


def _dimension_matches(expected: str | None, actual: str | None) -> bool:
    if expected is None or actual is None:
        return True
    if expected.endswith("_OR_COUNT"):
        return actual in {expected.removesuffix("_OR_COUNT"), "COUNT"}
    if expected == "MASS_OR_COUNT":
        return actual in {"MASS", "COUNT"}
    return expected == actual


class CatalogIndex:
    """A bounded immutable vocabulary/index for qualified catalog records."""

    def __init__(
        self,
        records: Sequence[CatalogRecord],
        *,
        data: Mapping[str, Any] | None = None,
        max_records: int = 100_000,
        query_candidate_keys: Mapping[ParsedIntent, Sequence[str]] | None = None,
    ):
        if len(records) > max_records:
            raise InputIntelligenceError(f"catalog vocabulary exceeds {max_records} records")
        unique: dict[str, CatalogRecord] = {}
        for record in records:
            if record.product_evidence_key in unique:
                raise InputIntelligenceError("duplicate productEvidenceKey is not allowed")
            unique[record.product_evidence_key] = record
        self.records: tuple[CatalogRecord, ...] = tuple(sorted(unique.values(), key=lambda item: (item.product_evidence_key, normalize_text(item.name))))
        self._by_key = {item.product_evidence_key: item for item in self.records}
        self.data = dict(data or _load_data())
        self._query_candidate_keys = {
            intent: tuple(key for key in values if isinstance(key, str))
            for intent, values in (query_candidate_keys or {}).items()
            if isinstance(intent, ParsedIntent)
        }
        self._exact: dict[str, set[str]] = defaultdict(set)
        self._token: dict[str, set[str]] = defaultdict(set)
        self._prefix: dict[str, set[str]] = defaultdict(set)
        self._trigram: dict[str, set[str]] = defaultdict(set)
        for record in self.records:
            phrases = [record.name, *record.aliases]
            if record.brand:
                phrases.append(record.brand)
            for phrase in phrases:
                normalized = normalize_text(phrase, max_length=512)
                if normalized:
                    self._exact[normalized].add(record.product_evidence_key)
                    compact = _joined_key(normalized)
                    self._exact[compact].add(record.product_evidence_key)
                    for token in _tokens(normalized):
                        self._token[token].add(record.product_evidence_key)
                        for width in (2, 3, 4):
                            if len(token) >= width:
                                self._prefix[token[:width]].add(record.product_evidence_key)
                        for trigram in _interior_trigrams(token):
                            self._trigram[trigram].add(record.product_evidence_key)

    @classmethod
    def from_records(
        cls,
        records: Iterable[Mapping[str, Any] | CatalogRecord],
        *,
        data_path: Path | str | None = None,
        max_records: int = 100_000,
        data: Mapping[str, Any] | None = None,
        query_candidate_keys: Mapping[ParsedIntent, Sequence[str]] | None = None,
    ) -> "CatalogIndex":
        materialized: list[CatalogRecord] = []
        for item in records:
            materialized.append(item if isinstance(item, CatalogRecord) else CatalogRecord.from_mapping(item))
            if len(materialized) > max_records:
                raise InputIntelligenceError(f"catalog vocabulary exceeds {max_records} records")
        return cls(
            materialized,
            data=data if data is not None else _load_data(data_path),
            max_records=max_records,
            query_candidate_keys=query_candidate_keys,
        )

    def as_manifest(self) -> dict[str, Any]:
        return {
            "schemaVersion": VOCABULARY_SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "recordCount": len(self.records),
            "recordKeys": [record.product_evidence_key for record in self.records],
            "tokenCount": len(self._token),
            "trigramCount": len(self._trigram),
            "sqliteFts5TrigramSupported": sqlite_fts5_trigram_supported(),
        }

    def get(self, key: str) -> CatalogRecord | None:
        return self._by_key.get(key)

    def all_records(self) -> tuple[CatalogRecord, ...]:
        return self.records

    def _candidate_keys(self, intent: ParsedIntent) -> tuple[str, ...]:
        if intent in self._query_candidate_keys:
            return self._query_candidate_keys[intent]
        keys: set[str] = set()
        ordered: list[str] = []

        def add(values: Iterable[str]) -> None:
            for value in sorted(values):
                if value not in keys:
                    keys.add(value)
                    ordered.append(value)

        for phrase in _intent_phrases(intent, self.data):
            normalized = normalize_text(phrase, max_length=512)
            add(self._exact.get(normalized, ()))
            add(self._exact.get(_joined_key(normalized), ()))
            for token in _tokens(normalized):
                add(self._token.get(token, ()))
                for width in (2, 3, 4):
                    if len(token) >= width:
                        add(self._prefix.get(token[:width], ()))
                for trigram in _interior_trigrams(token):
                    add(self._trigram.get(trigram, ()))
        if not keys and len(self.records) <= 2048:
            add(record.product_evidence_key for record in self.records)
        return tuple(ordered[:MAX_CANDIDATES])

    def intent_features(self, intent: ParsedIntent) -> frozenset[tuple[str, str]]:
        """Return the lexical features used to enter this intent's horizon."""

        return frozenset(_phrase_features(_intent_phrases(intent, self.data)))

    @staticmethod
    def record_features(record: CatalogRecord) -> frozenset[tuple[str, str]]:
        return frozenset(_phrase_features((record.name, *record.aliases, record.brand or "")))

    def record_matches_intent(
        self,
        record: CatalogRecord,
        intent: ParsedIntent,
        *,
        intent_features: frozenset[tuple[str, str]] | None = None,
        record_features: frozenset[tuple[str, str]] | None = None,
    ) -> bool:
        """Return whether a record can enter the normal candidate-key horizon."""

        left = intent_features if intent_features is not None else self.intent_features(intent)
        if record_features is not None:
            return bool(left & record_features)
        # Streamed callers should not allocate a feature set for every record
        # in a national index.  Probe the same exact/joined/token/prefix/
        # trigram feature universe lazily and stop at the first hit.
        return _phrases_match_features((record.name, *record.aliases, record.brand or ""), left)

    def raw_record_matches_intents(
        self,
        value: Mapping[str, Any],
        intent_features: Sequence[frozenset[tuple[str, str]]],
    ) -> tuple[int, ...]:
        """Return matching intent positions before constructing a record.

        Required identity fields are validated exactly as ``CatalogRecord``
        would validate them.  All other record fields remain untouched until a
        lexical match is found, keeping national streaming CPU and allocations
        proportional to the query's plausible candidates.
        """

        phrases = _mapping_phrases(value)
        raw_features, needs_normalized_fallback = _raw_phrase_features(phrases)
        matches: list[int] = []
        for index, features in enumerate(intent_features):
            if raw_features & features:
                matches.append(index)
            elif needs_normalized_fallback and _phrases_match_features(phrases, features):
                # Accented/compatibility text can normalize differently from
                # the ASCII probe; retain the exact shared policy for those
                # records instead of risking a false negative.
                matches.append(index)
        return tuple(matches)

    def score_record(self, record: CatalogRecord, intent: ParsedIntent) -> _ScoredCandidate:
        """Score one streamed record with the same rules as ``candidates``."""

        score, similarity, safe, reason, exact, alias = self._score(record, intent)
        return _ScoredCandidate(record, score, similarity, exact, alias, safe, reason)

    def _expected_dimension(self, intent: ParsedIntent) -> str | None:
        dimensions = self.data.get("dimensions", {})
        if intent.brand and intent.brand in dimensions:
            return dimensions[intent.brand]
        if intent.concept and intent.concept in dimensions:
            return dimensions[intent.concept]
        return None

    def _form_safe(self, record: CatalogRecord, intent: ParsedIntent) -> tuple[bool, str]:
        haystack = normalize_text(" ".join(filter(None, (record.name, record.brand or "", record.form or ""))), max_length=768)
        # Product-form exclusions apply to the requested wording as well as
        # the catalog record.  This prevents an otherwise valid fresh product
        # from being silently substituted for ``apple juice``, ``rice
        # seasoning`` or ``coconut shampoo`` when the catalog happens to have
        # no safe processed-form record.
        requested_text = intent.normalized
        concept = intent.concept
        if intent.brand:
            candidate_brand = normalize_text(record.brand or "")
            if intent.brand not in candidate_brand and intent.brand.replace("-", " ") not in candidate_brand:
                return False, "BRAND_MISMATCH"
        for variant in intent.variants:
            if normalize_text(variant) not in haystack:
                return False, f"VARIANT_NOT_PRESENT:{variant}"
        for variant in intent.negated_variants:
            if normalize_text(variant) in haystack:
                return False, f"NEGATED_VARIANT_PRESENT:{variant}"
        if concept is None:
            return True, "NO_CLASS_RULE"
        forms = self.data.get("productForms", {})
        rule = forms.get(concept) if isinstance(forms, Mapping) else None
        aliases = self.data.get("aliases", {})
        concept_terms = [concept]
        if isinstance(aliases, Mapping):
            concept_terms.extend(str(key) for key, value in aliases.items() if value == concept)
        if not any(normalize_text(term) in haystack for term in concept_terms):
            return False, "CONCEPT_NOT_PRESENT"
        if isinstance(rule, Mapping):
            excludes = list(rule.get("exclude", [])) if isinstance(rule.get("exclude", []), Sequence) and not isinstance(rule.get("exclude", []), (str, bytes)) else []
            form_rules = rule.get("forms", {})
            selected_form_name = intent.requested_form
            if selected_form_name is None and concept == "tomato":
                selected_form_name = rule.get("default") if isinstance(rule.get("default"), str) else None
            selected_form = form_rules.get(selected_form_name) if selected_form_name and isinstance(form_rules, Mapping) else None
            if isinstance(selected_form, Mapping) and isinstance(selected_form.get("exclude", []), Sequence) and not isinstance(selected_form.get("exclude", []), (str, bytes)):
                excludes.extend(selected_form.get("exclude", []))
            for value in excludes if isinstance(excludes, Sequence) and not isinstance(excludes, (str, bytes)) else ():
                normalized_value = normalize_text(str(value))
                if normalized_value in haystack or normalized_value in requested_text:
                    return False, f"EXCLUDED_FORM:{value}"
            if intent.requested_form:
                selected = form_rules.get(intent.requested_form) if isinstance(form_rules, Mapping) else None
                if isinstance(selected, Mapping):
                    include = selected.get("include", [])
                    if include and not any(normalize_text(str(value)) in haystack for value in include):
                        return False, "REQUESTED_FORM_NOT_PRESENT"
            elif concept == "tomato":
                default = rule.get("default")
                selected = form_rules.get(default) if isinstance(form_rules, Mapping) else None
                include = selected.get("include", []) if isinstance(selected, Mapping) else []
                if not any(normalize_text(str(value)) in haystack for value in include):
                    return False, "DEFAULT_FRESH_FORM_NOT_AUDITED"
        return True, "SAFE_FORM"

    def _score(self, record: CatalogRecord, intent: ParsedIntent) -> tuple[int, int, bool, str, bool, bool]:
        name = normalize_text(record.name, max_length=512)
        brand = normalize_text(record.brand or "", max_length=256)
        aliases = tuple(normalize_text(value, max_length=256) for value in record.aliases)
        combined = f"{name} {brand} {' '.join(aliases)}".strip()
        query = intent.normalized
        query_tokens = set(intent.search_tokens)
        name_tokens = set(_tokens(name))
        variant_tokens_present = all(normalize_text(value) in name for value in intent.variants)
        exact_brand_variant = bool(intent.brand and normalize_text(intent.brand) in brand and variant_tokens_present and intent.variants)
        exact = query == name or query in aliases or (intent.brand and query == normalize_text(intent.brand) and intent.brand in brand) or exact_brand_variant
        alias = intent.alias_used
        token_matches = sum(1 for token in query_tokens if token in name_tokens or token in set(_tokens(brand)))
        concept_match = bool(intent.concept and any(intent.concept == normalize_text(alias) for alias in (name, brand, *aliases)))
        if intent.concept:
            alias_targets = self.data.get("aliases", {})
            if isinstance(alias_targets, Mapping):
                concept_words = [normalize_text(str(key)) for key, value in alias_targets.items() if value == intent.concept]
                concept_match = concept_match or any(word in name or word in brand for word in concept_words)
        ratios: list[int] = []
        for query_token in sorted(query_tokens):
            if len(query_token) < 2:
                continue
            words = _tokens(combined)
            ratios.append(max((_levenshtein_ratio(query_token, word) for word in words), default=0))
        similarity = sum(ratios) // len(ratios) if ratios else 0
        score = similarity
        score += token_matches * 140
        if concept_match:
            score += 320
            # A vocabulary alias is an explicit semantic bridge (for
            # example English ``rice`` to a Spanish catalog name).  It is
            # safe to report as RESOLVED_ALIAS once form and dimension gates
            # pass, but it is never an implicit fuzzy match.
            if not exact:
                alias = True
            similarity = max(similarity, 850)
            if intent.typo_used:
                # The typo table is a small audited correction vocabulary, so
                # it may establish the concept while still being reported as
                # a correction.  Product-form and variant checks remain in
                # force below.
                score += 500
                similarity = max(similarity, 850)
        if intent.brand and intent.brand in brand:
            score += 360
        if exact:
            score += 900
        if query and query in name:
            score += 180
        safe, reason = self._form_safe(record, intent)
        if safe:
            score += 20
        else:
            score -= 800
        return score, similarity, safe, reason, exact, alias

    def candidates(self, intent: ParsedIntent) -> tuple[_ScoredCandidate, ...]:
        keys = self._candidate_keys(intent)
        values: list[_ScoredCandidate] = []
        comparisons = 0
        for key in keys:
            record = self._by_key.get(key)
            if record is None:
                continue
            comparisons += 1
            if comparisons > MAX_FUZZY_COMPARISONS:
                break
            score, similarity, safe, reason, exact, alias = self._score(record, intent)
            values.append(_ScoredCandidate(record, score, similarity, exact, alias, safe, reason))
        return tuple(sorted(values, key=lambda value: (-value.score, -value.similarity, value.record.product_evidence_key, normalize_text(value.record.name))))

    def package_suggestions(self, intent: ParsedIntent, *, limit: int = MAX_SUGGESTIONS) -> tuple[CatalogRecord, ...]:
        values = [candidate for candidate in self.candidates(intent) if candidate.safe]
        return tuple(candidate.record for candidate in values[:limit])


def parse_intent(
    text: str,
    *,
    data_path: Path | str | None = None,
    data: Mapping[str, Any] | None = None,
) -> ParsedIntent:
    data = data if data is not None else _load_data(data_path)
    fillers = data.get("fillers", [])
    if not isinstance(fillers, Sequence) or isinstance(fillers, (str, bytes)):
        fillers = []
    normalized = _strip_fillers(_remove_quantity_text(normalize_text(text, max_length=MAX_LINE_LENGTH)), fillers)
    tokens = _tokens(normalized)
    if not tokens:
        return ParsedIntent("", (), None, None, None, (), (), False, False, ())
    aliases = data.get("aliases", {})
    brands = data.get("brands", {})
    concept: str | None = None
    alias_used = False
    typo_used = False
    matched_alias: str | None = None
    if isinstance(aliases, Mapping):
        for phrase in sorted((str(key) for key in aliases), key=lambda value: (-len(_tokens(value)), -len(value), value)):
            if _contains_phrase(tokens, phrase):
                concept = str(aliases[phrase])
                matched_alias = phrase
                alias_used = normalize_text(phrase) != normalize_text(concept)
                break
    # A small audited typo table is deliberately separate from semantic
    # aliases so the result can be labelled RESOLVED_SAFE_CORRECTION rather
    # than hiding a spelling correction as a language translation.
    typos = data.get("typos", {})
    if concept is None and isinstance(typos, Mapping):
        for phrase in sorted((str(key) for key in typos), key=lambda value: (-len(_tokens(value)), -len(value), value)):
            if _contains_phrase(tokens, phrase):
                concept = str(typos[phrase])
                matched_alias = phrase
                alias_used = False
                typo_used = True
                break
    brand: str | None = None
    if isinstance(brands, Mapping):
        for phrase in sorted((str(key) for key in brands), key=lambda value: (-len(_tokens(value)), -len(value), value)):
            if _contains_phrase(tokens, phrase) or _joined_key(phrase) == _joined_key(normalized):
                brand = str(brands[phrase])
                if normalize_text(phrase) != normalize_text(brand):
                    alias_used = True
                break
    requested_form: str | None = None
    if concept == "tomato":
        product_rules = data.get("productForms", {}).get("tomato", {}) if isinstance(data.get("productForms"), Mapping) else {}
        form_rules = product_rules.get("forms", {}) if isinstance(product_rules, Mapping) else {}
        for form_name, rule in sorted(form_rules.items(), key=lambda item: (item[0] == product_rules.get("default"), -len(item[0]), item[0])) if isinstance(form_rules, Mapping) else ():
            include = rule.get("include", []) if isinstance(rule, Mapping) else []
            if any(_contains_phrase(tokens, str(value)) for value in include):
                requested_form = str(form_name)
                break
    variant_names = ("zero", "original", "regular", "clasica", "clásica", "descremada", "entera", "light", "integral", "sin tacc", "sin sal", "sin azucar", "sin azúcar")
    variants = tuple(value for value in variant_names if _contains_phrase(tokens, value) and not _contains_phrase(tokens, f"no {value}"))
    negated = tuple(value for value in variant_names if _contains_phrase(tokens, f"no {value}"))
    ignored = set(_tokens(matched_alias or "")) if matched_alias else set()
    if brand:
        ignored.update(_tokens(brand))
    ignored.update({"de", "del", "con", "sin", "no", "y", "and", "para"})
    ignored.update(_tokens(" ".join(variants)))
    ignored.update(_tokens(" ".join(negated)))
    search_tokens = tuple(token for token in tokens if token not in ignored)
    if concept and concept not in search_tokens:
        search_tokens = (concept, *search_tokens)
    if brand and not search_tokens:
        search_tokens = (brand,)
    return ParsedIntent(normalized, tokens, concept, brand, requested_form, variants, negated, alias_used, typo_used, tuple(dict.fromkeys(search_tokens)))


def _record_quantity(record: CatalogRecord) -> QuantityValue | None:
    if not record.quantity:
        return None
    unit = record.quantity.get("unit")
    amount = record.quantity.get("value", record.quantity.get("amount"))
    if not isinstance(unit, str) or amount is None:
        return None
    try:
        value = Decimal(str(amount))
        if value <= 0 or not value.is_finite():
            return None
        # Provider units are explicit uppercase names; aliases cover both.
        return _quantity_value(value, unit)
    except (InvalidOperation, InputIntelligenceError):
        return None


def _unit_for_structured(quantity: QuantityValue) -> tuple[str, Decimal]:
    # Use the exact base unit so pound conversion cannot round and all M6
    # arithmetic receives a compatible dimension.
    return quantity.base_unit, quantity.base_amount


def _clarification(code: str, message: str, *, recognized: CatalogRecord | None = None, choices: Sequence[Any] = (), details: Mapping[str, Any] | None = None) -> dict[str, Any]:
    value: dict[str, Any] = {"code": code, "message": message, "choices": list(choices)}
    if recognized is not None:
        value["recognizedProduct"] = recognized.as_dict()
    if details:
        value["details"] = dict(details)
    return value


class ConsumerInputInterpreter:
    """Interpret bounded text against one immutable catalog index."""

    def __init__(self, catalog: CatalogIndex, *, data_path: Path | str | None = None):
        self.catalog = catalog
        self.data_path = data_path

    def resolve_line(self, text: str, *, line_id: str = "item-1", require_quantity: bool = False) -> ResolvedLine:
        if not isinstance(text, str) or not text.strip():
            raise InputIntelligenceError("shopping line is empty")
        if len(text) > MAX_LINE_LENGTH:
            raise InputIntelligenceError(f"shopping line exceeds {MAX_LINE_LENGTH} characters")
        normalized = normalize_text(text, max_length=MAX_LINE_LENGTH)
        quantity = parse_quantity(text)
        intent = parse_intent(text, data_path=self.data_path, data=self.catalog.data)
        candidates = self.catalog.candidates(intent)
        safe = tuple(candidate for candidate in candidates if candidate.safe)
        if not safe:
            clarification = _clarification(AMBIGUOUS_PRODUCT, "No safe catalog match was found.", choices=()) if candidates else None
            return ResolvedLine(line_id, text, normalized, NO_SAFE_MATCH, intent, None, quantity, clarification=clarification, candidates_examined=len(candidates))
        top = safe[0]
        second = safe[1] if len(safe) > 1 else None
        expected = self.catalog._expected_dimension(intent)
        actual = quantity.total.dimension if quantity.total else None
        if actual and expected and not _dimension_matches(expected, actual):
            return ResolvedLine(
                line_id,
                text,
                normalized,
                NEEDS_CLARIFICATION,
                intent,
                top.record,
                quantity,
                clarification=_clarification(DIMENSION_MISMATCH, "The quantity unit does not match the product's retail dimension.", recognized=top.record, details={"expected": expected, "received": actual, "problem": f"EXPECTED_{expected}_BUT_RECEIVED_{actual}"}),
                suggestions=tuple(candidate.record for candidate in safe[:MAX_SUGGESTIONS]),
                candidates_examined=len(candidates),
            )
        short_query = len("".join(intent.search_tokens)) < 4
        gap = top.score - second.score if second else 10_000
        # A short token is never accepted merely because it is close to one
        # catalog word.  Exact brand evidence can still resolve a full brand.
        if second and ((gap < AMBIGUITY_GAP_SCORE) or (short_query and not top.exact and not top.alias)):
            suggestions = tuple(candidate.record for candidate in safe[:MAX_SUGGESTIONS])
            return ResolvedLine(
                line_id,
                text,
                normalized,
                NEEDS_CLARIFICATION,
                intent,
                top.record,
                quantity,
                clarification=_clarification(MULTIPLE_PLAUSIBLE_PRODUCTS, "More than one safe product is plausible.", recognized=top.record, choices=[record.as_dict() for record in suggestions]),
                suggestions=suggestions,
                candidates_examined=len(candidates),
            )
        if quantity.package_count is not None and quantity.package_size is None and actual is None and expected not in {None, "COUNT", "COUNT_OR_MASS"}:
            suggestions = self.catalog.package_suggestions(intent)
            choices = []
            for record in suggestions:
                size = _record_quantity(record)
                if size:
                    choices.append(size.as_dict())
            return ResolvedLine(
                line_id,
                text,
                normalized,
                NEEDS_CLARIFICATION,
                intent,
                top.record,
                quantity,
                clarification=_clarification(PACKAGE_SIZE_REQUIRED, "What package size should be used?", recognized=top.record, choices=choices),
                suggestions=suggestions,
                candidates_examined=len(candidates),
            )
        if require_quantity and quantity.total is None:
            return ResolvedLine(
                line_id,
                text,
                normalized,
                NEEDS_CLARIFICATION,
                intent,
                top.record,
                quantity,
                clarification=_clarification(QUANTITY_REQUIRED, "How much do you need?", recognized=top.record, choices=[]),
                suggestions=tuple(candidate.record for candidate in safe[:MAX_SUGGESTIONS]),
                candidates_examined=len(candidates),
            )
        if top.similarity < (SHORT_MIN_SIMILARITY if short_query else FUZZY_MIN_SIMILARITY) and not top.exact and not top.alias:
            return ResolvedLine(
                line_id,
                text,
                normalized,
                NO_SAFE_MATCH,
                intent,
                None,
                quantity,
                clarification=_clarification(AMBIGUOUS_PRODUCT, "The spelling is not close enough to a safe catalog identity."),
                suggestions=tuple(candidate.record for candidate in safe[:MAX_SUGGESTIONS]),
                candidates_examined=len(candidates),
            )
        if intent.typo_used:
            state = RESOLVED_SAFE_CORRECTION
        elif top.exact:
            state = RESOLVED_EXACT
        elif top.alias or intent.alias_used:
            state = RESOLVED_ALIAS
        else:
            state = RESOLVED_SAFE_CORRECTION
        correction = None
        if state == RESOLVED_SAFE_CORRECTION or state == RESOLVED_ALIAS:
            correction = {"from": text, "to": top.record.name, "reason": "CATALOG_ALIAS" if state == RESOLVED_ALIAS else "BOUNDED_EDIT_CORRECTION", "score": top.score, "similarity": top.similarity}
        structured = None
        if quantity.total is not None:
            unit, amount = _unit_for_structured(quantity.total)
            structured = {"lineId": line_id, "query": top.record.name, "amount": _decimal_text(amount), "unit": unit, "productEvidenceKey": top.record.product_evidence_key}
        return ResolvedLine(
            line_id,
            text,
            normalized,
            state,
            intent,
            top.record,
            quantity,
            correction=correction,
            suggestions=tuple(candidate.record for candidate in safe[:MAX_SUGGESTIONS]),
            structured_line=structured,
            candidates_examined=len(candidates),
        )

    def interpret(self, text: str, *, require_quantities: bool = False) -> dict[str, Any]:
        started = time.perf_counter()
        lines = split_shopping_lines(text, data=self.catalog.data)
        resolved = tuple(self.resolve_line(value, line_id=f"item-{index}", require_quantity=require_quantities) for index, value in enumerate(lines, 1))
        clarifications = [line.as_dict()["clarification"] | {"lineId": line.line_id} for line in resolved if line.clarification]
        safe_lines = [line.structured_line for line in resolved if line.structured_line is not None]
        ready = len(safe_lines) == len(resolved) and all(line.state in {RESOLVED_EXACT, RESOLVED_ALIAS, RESOLVED_SAFE_CORRECTION} for line in resolved)
        elapsed = (time.perf_counter() - started) * 1000
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "originalText": text,
            "lineCount": len(resolved),
            "lines": [line.as_dict() for line in resolved],
            "clarifications": clarifications,
            "safeRequestReady": ready,
            "structuredItems": safe_lines if ready else [],
            "diagnostics": {"totalMs": round(elapsed, 3), "candidateCount": sum(line.candidates_examined for line in resolved), "bounded": True},
            "safety": {"unknownRemainsUnknown": True, "unsafeAutomaticSubstitutionAllowed": False, "universalLanguageUnderstandingClaimed": False},
        }


def apply_candidate_horizon_guard(result: Mapping[str, Any], saturated_line_ids: Sequence[str], *, candidate_bound: int) -> dict[str, Any]:
    """Downgrade only confidence that could depend on discarded candidates."""

    saturated = frozenset(value for value in saturated_line_ids if isinstance(value, str))
    if not saturated:
        return dict(result)
    lines: list[dict[str, Any]] = []
    for raw_line in result.get("lines", ()):
        if not isinstance(raw_line, Mapping):
            continue
        line = dict(raw_line)
        if line.get("lineId") in saturated:
            suggestions = line.get("suggestions", [])
            if not isinstance(suggestions, list):
                suggestions = []
            if line.get("resolution") in {RESOLVED_EXACT, RESOLVED_ALIAS, RESOLVED_SAFE_CORRECTION}:
                line["resolution"] = NEEDS_CLARIFICATION
                line["correction"] = None
                line["structuredLine"] = None
            line["clarification"] = {
                "code": CANDIDATE_HORIZON_REACHED,
                "message": "Too many plausible catalog matches were found to choose safely.",
                "choices": suggestions[:MAX_SUGGESTIONS],
                "recognizedProduct": line.get("recognizedProduct"),
                "details": {"candidateBound": candidate_bound},
            }
        lines.append(line)
    guarded = dict(result)
    guarded["lines"] = lines
    guarded["clarifications"] = [
        dict(line["clarification"]) | {"lineId": line["lineId"]}
        for line in lines
        if isinstance(line.get("clarification"), Mapping) and isinstance(line.get("lineId"), str)
    ]
    guarded["safeRequestReady"] = False
    guarded["structuredItems"] = []
    diagnostics = dict(result.get("diagnostics", {})) if isinstance(result.get("diagnostics"), Mapping) else {}
    diagnostics.update({"candidateSaturated": True, "saturatedLineIds": sorted(saturated), "candidateBound": candidate_bound})
    guarded["diagnostics"] = diagnostics
    return guarded


def build_vocabulary(records: Iterable[Mapping[str, Any] | CatalogRecord], *, data_path: Path | str | None = None, max_records: int = 100_000) -> CatalogIndex:
    return CatalogIndex.from_records(records, data_path=data_path, max_records=max_records)


def vocabulary_records_from_json_lines(lines: Iterable[str], *, max_records: int = 100_000) -> Iterator[CatalogRecord]:
    count = 0
    for line_number, line in enumerate(lines, 1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as exc:
            raise InputIntelligenceError(f"catalog line {line_number} is invalid JSON") from exc
        if not isinstance(value, Mapping):
            raise InputIntelligenceError(f"catalog line {line_number} is not an object")
        count += 1
        if count > max_records:
            raise InputIntelligenceError(f"catalog vocabulary exceeds {max_records} records")
        yield CatalogRecord.from_mapping(value)


__all__ = [
    "AMBIGUOUS_PRODUCT",
    "CANDIDATE_HORIZON_REACHED",
    "CatalogIndex",
    "CatalogRecord",
    "ConsumerInputInterpreter",
    "DIMENSION_MISMATCH",
    "InputIntelligenceError",
    "MULTIPLE_PLAUSIBLE_PRODUCTS",
    "NEEDS_CLARIFICATION",
    "NO_SAFE_MATCH",
    "PACKAGE_SIZE_REQUIRED",
    "POLICY_VERSION",
    "QUANTITY_REQUIRED",
    "QuantitySpec",
    "QuantityValue",
    "RESOLVED_ALIAS",
    "RESOLVED_EXACT",
    "RESOLVED_SAFE_CORRECTION",
    "RESULT_STATES",
    "SCHEMA_VERSION",
    "apply_candidate_horizon_guard",
    "build_vocabulary",
    "normalize_text",
    "parse_intent",
    "parse_quantity",
    "split_shopping_lines",
    "sqlite_fts5_trigram_supported",
    "vocabulary_records_from_json_lines",
]

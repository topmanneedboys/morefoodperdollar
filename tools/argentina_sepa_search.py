#!/usr/bin/env python3
"""Small deterministic Spanish product search for the CABA evidence table.

This module is intentionally lexical and provider-neutral at its boundary. It
does not classify every product, infer a category, or claim availability. It
only ranks already-admitted product identity evidence using token boundaries,
documented synonyms, and conservative context exclusions for known semantic
traps (``condimento para arroz``, ``gaseosa sin azúcar``, and similar).
"""

from __future__ import annotations

import heapq
import re
import unicodedata
from dataclasses import dataclass
from typing import Any, Iterable, Mapping


SEARCH_SCHEMA_VERSION = "argentina-sepa-spanish-search-v2"
MAX_QUERY_LENGTH = 96
MAX_RESULTS = 5
# Discovery continuation may ask for a small bounded window.  The ordinary
# callers keep the historical top-five default; this is only a validation
# ceiling for the explicitly paged public discovery path.
MAX_DISCOVERY_RESULTS = 31
MAX_CANDIDATES = 100_000

# These are broad, documented Argentine-Spanish spelling equivalents rather
# than product-specific memorization.  A query expands only within its group.
SYNONYMS: dict[str, tuple[str, ...]] = {
    "leche": ("leche",),
    "pan": ("pan",),
    # English labels are accepted at the service boundary, while the
    # Spanish catalog term remains the canonical phrase used for ranking.
    "arroz": ("arroz", "rice"),
    "rice": ("arroz", "rice"),
    "lychee": ("lichi", "lychee", "litchi", "lichis", "litchis"),
    "lichi": ("lichi", "lychee", "litchi", "lichis", "litchis"),
    "litchi": ("lichi", "lychee", "litchi", "lichis", "litchis"),
    "lichis": ("lichi", "lychee", "litchi", "lichis", "litchis"),
    "litchis": ("lichi", "lychee", "litchi", "lichis", "litchis"),
    "yerba": ("yerba",),
    "aceite": ("aceite",),
    "fideo": ("fideo", "fideos"),
    "fideos": ("fideo", "fideos"),
    "harina": ("harina",),
    "azucar": ("azucar",),
    "huevo": ("huevo", "huevos", "egg", "eggs"),
    "huevos": ("huevo", "huevos", "egg", "eggs"),
    "egg": ("huevo", "huevos", "egg", "eggs"),
    "eggs": ("huevo", "huevos", "egg", "eggs"),
    "cafe": ("cafe",),
    "te": ("te",),
    "agua": ("agua",),
    "gaseosa": ("gaseosa", "gaseosas", "refresco", "refrescos"),
    "yogur": ("yogur", "yogurt"),
    "queso": ("queso", "quesos"),
    "manteca": ("manteca",),
    "atun": ("atun",),
    "pollo": ("pollo", "pollos"),
    "cereal": ("cereal", "cereales"),
    "galletita": ("galleta", "galletitas", "galletita"),
    "galletitas": ("galleta", "galletitas", "galletita"),
    "detergente": ("detergente",),
    "papel": ("papel",),
    "shampoo": ("shampoo", "champu"),
    "champu": ("shampoo", "champu"),
    "jabon": ("jabon",),
    "pasta": ("pasta",),
    "dental": ("dental",),
    "panal": ("panal", "panales"),
    "panales": ("panal", "panales"),
}

# A query token that occurs only as an ingredient/flavour/condition should
# not outrank the product that is actually that thing.  The patterns are
# expressed in normalized tokens and are intentionally short and auditable.
CONTEXT_EXCLUSIONS: dict[str, tuple[tuple[str, ...], ...]] = {
    "pan": (("manteca", "pan"), ("manteca", "con", "marca", "pan"), ("sabor", "pan")),
    # A bare commodity query should return rice identities, not products that
    # merely mention rice as an ingredient, flavour, recipe, or derived form.
    # Explicit queries such as ``arroz con leche`` remain eligible because the
    # matcher skips a context pattern when the shopper asks for that context.
    "arroz": (
        ("condimento", "para", "arroz"),
        ("sabor", "arroz"),
        ("para", "arroz"),
        ("arroz", "con", "leche"),
        ("postre", "de", "arroz"),
        ("postre", "arroz"),
        ("pudin", "de", "arroz"),
        ("pudding", "de", "arroz"),
        ("budin", "de", "arroz"),
        ("harina", "de", "arroz"),
        ("leche", "de", "arroz"),
        ("bebida", "de", "arroz"),
        ("jugo", "de", "arroz"),
        ("galleta", "de", "arroz"),
        ("galletita", "de", "arroz"),
        ("torta", "de", "arroz"),
        ("rice", "dessert"),
        ("rice", "pudding"),
        ("rice", "flour"),
        ("rice", "milk"),
        ("rice", "drink"),
    ),
    # Lichi/lychee fruit and yerba mate use the same conservative rule: the
    # family token must identify the product itself, not a drink, flavour,
    # extract, cosmetic, or prepared dessert that uses it.
    "lychee": (
        ("jugo", "de", "lichi"),
        ("jugo", "de", "litchi"),
        ("jugo", "de", "lychee"),
        ("jugo", "lichi"),
        ("nectar", "de", "lichi"),
        ("nectar", "de", "litchi"),
        ("bebida", "de", "lichi"),
        ("bebida", "de", "litchi"),
        ("jarabe", "de", "lichi"),
        ("almibar", "de", "lichi"),
        ("sabor", "lichi"),
        ("sabor", "litchi"),
        ("yogur", "de", "lichi"),
        ("postre", "de", "lichi"),
        ("dulce", "de", "lichi"),
        ("candy", "lychee"),
        ("juice", "lychee"),
        ("drink", "lychee"),
    ),
    "yerba": (
        ("bebida", "de", "yerba"),
        ("gaseosa", "de", "yerba"),
        ("energizante", "de", "yerba"),
        ("energy", "yerba"),
        ("extracto", "de", "yerba"),
        ("sabor", "yerba"),
        ("te", "de", "yerba"),
        ("jugo", "de", "yerba"),
        ("jarabe", "de", "yerba"),
        ("yerba", "sabor"),
        ("yerba", "extracto"),
        ("yerba", "gaseosa"),
        ("yerba", "drink"),
    ),
    "huevo": (
        ("fideo", "con", "huevo"), ("fideos", "con", "huevo"), ("pasta", "con", "huevo"),
        ("fideo",), ("fideos",), ("pasta",), ("noodle",), ("noodles",),
        ("chocolate",), ("cacao",), ("pascua",), ("easter",), ("sorpresa",), ("surprise",),
        ("dino",), ("dinosaurio",), ("dinosaur",), ("unicornio",), ("unicorn",),
        ("juguete",), ("juego",), ("toy",), ("playset",), ("coleccionable",), ("collectible",),
        ("figura",), ("figure",), ("smash",), ("smashers",), ("rainbocorn",),
        ("licor",), ("liqueur",), ("bebida",), ("drink",),
    ),
    "huevos": (
        ("fideo", "con", "huevo"), ("fideos", "con", "huevo"), ("pasta", "con", "huevo"),
        ("fideo",), ("fideos",), ("pasta",), ("noodle",), ("noodles",),
        ("chocolate",), ("cacao",), ("pascua",), ("easter",), ("sorpresa",), ("surprise",),
        ("dino",), ("dinosaurio",), ("dinosaur",), ("unicornio",), ("unicorn",),
        ("juguete",), ("juego",), ("toy",), ("playset",), ("coleccionable",), ("collectible",),
        ("figura",), ("figure",), ("smash",), ("smashers",), ("rainbocorn",),
        ("licor",), ("liqueur",), ("bebida",), ("drink",),
    ),
    "leche": (("leche", "y", "tonico"), ("leche", "tonico"), ("tonico", "micelar"), ("leche", "corporal")),
    "aceite": (("aceite", "para", "bebe"), ("aceite", "bebe")),
    "azucar": (("sin", "azucar"), ("cero", "azucar"), ("zero", "azucar")),
    "gaseosa": (("gaseosa", "empleados", "externos"), ("empleados", "externos")),
    "queso": (("rellena", "de", "queso"), ("relleno", "de", "queso"), ("pasta", "con", "queso")),
    "cereal": (("yogur", "con", "cereal"), ("yogurt", "con", "cereal")),
    "papel": (("palito", "porto", "rollo", "papel"), ("palito", "papel")),
}

# A toy/confectionery egg often carries more than one marker (for example
# ``SMASH DINO NANO EGG``).  Asking for one marker should keep that observed
# form searchable without making every other marker an accidental exclusion.
_EGG_CONTEXT_MARKERS = frozenset({
    "chocolate", "cacao", "pascua", "easter", "sorpresa", "surprise",
    "dino", "dinosaurio", "dinosaur", "unicornio", "unicorn", "juguete",
    "juego", "toy", "playset", "coleccionable", "collectible", "figura",
    "figure", "smash", "smashers", "rainbocorn", "licor", "liqueur",
    "bebida", "drink",
})


class SearchError(ValueError):
    """An invalid or unbounded search request."""


@dataclass(frozen=True)
class SearchResult:
    product_evidence_key: str
    name: str
    brand: str | None
    gtin: str | None
    score: int
    matched_tokens: tuple[str, ...]

    def as_dict(self) -> dict[str, Any]:
        return {
            "productEvidenceKey": self.product_evidence_key,
            "name": self.name,
            "brand": self.brand,
            "gtin": self.gtin,
            "score": self.score,
            "matchedTokens": list(self.matched_tokens),
        }


def normalize_spanish(value: str, *, max_length: int = MAX_QUERY_LENGTH) -> str:
    if not isinstance(value, str):
        raise SearchError("search text must be a string")
    if len(value) > max_length:
        raise SearchError(f"search text exceeds {max_length} characters")
    decomposed = unicodedata.normalize("NFKD", value).casefold()
    ascii_text = decomposed.encode("ascii", "ignore").decode("ascii")
    return " ".join(re.findall(r"[a-z0-9]+", ascii_text))


def _variants(token: str) -> tuple[str, ...]:
    values = list(SYNONYMS.get(token, (token,)))
    # Safe plural handling for the small grocery query vocabulary.  Do not
    # stem arbitrary product text, which would create fuzzy identity claims.
    if token.endswith("es") and len(token) > 4:
        values.append(token[:-2])
    if token.endswith("s") and len(token) > 3:
        values.append(token[:-1])
    return tuple(dict.fromkeys(values))


def _contains_sequence(tokens: tuple[str, ...], sequence: tuple[str, ...]) -> bool:
    if len(sequence) > len(tokens):
        return False
    return any(tokens[index : index + len(sequence)] == sequence for index in range(len(tokens) - len(sequence) + 1))


def _contains_ordered_within(tokens: tuple[str, ...], first: str, second: str, max_gap: int = 4) -> bool:
    """Return true for a short, explicit ingredient/brand context."""

    first_positions = [index for index, token in enumerate(tokens) if token == first]
    second_positions = [index for index, token in enumerate(tokens) if token == second]
    return any(0 < right - left <= max_gap for left in first_positions for right in second_positions)


def _context_keys(token: str) -> tuple[str, ...]:
    """Return canonical context-policy keys for one query token."""

    values = [token]
    values.extend(value for value in _variants(token) if value in CONTEXT_EXCLUSIONS)
    return tuple(dict.fromkeys(values))


def _context_requested(query_tokens: tuple[str, ...], sequence: tuple[str, ...]) -> bool:
    """Keep a derived product when the shopper explicitly asks for it."""

    if not sequence:
        return False
    expanded = set(query_tokens)
    for token in query_tokens:
        expanded.update(_variants(token))
    if len(sequence) == 1 and sequence[0] in _EGG_CONTEXT_MARKERS:
        if any(marker in expanded for marker in _EGG_CONTEXT_MARKERS):
            return True
    return all(any(value in expanded for value in _variants(token)) for token in sequence)


def _has_context_exclusion(query_tokens: tuple[str, ...], name_tokens: tuple[str, ...]) -> bool:
    for token in query_tokens:
        for context_key in _context_keys(token):
            for sequence in CONTEXT_EXCLUSIONS.get(context_key, ()):
                if _context_requested(query_tokens, sequence):
                    continue
                if _contains_sequence(name_tokens, sequence):
                    return True
        # In catalog names, a butter/flavour descriptor can place a product
        # token after a short gap (for example ``MANTECA CON MARCA PAN``).
        # Keep this deliberately one-way: ``PAN CON MANTECA`` remains a bread
        # product, while ``MANTECA ... PAN`` is treated as a descriptor hit.
        if token == "pan" and _contains_ordered_within(name_tokens, "manteca", "pan"):
            return True
        if token == "papel" and _contains_ordered_within(name_tokens, "palito", "papel", max_gap=6):
            return True
    return False


def _score(query: str, name: str, brand: str | None, aliases: Iterable[str] = ()) -> tuple[int, tuple[str, ...]] | None:
    query_tokens = tuple(normalize_spanish(query).split())
    name_tokens = tuple(normalize_spanish(name, max_length=240).split())
    if not query_tokens or not name_tokens:
        return None
    brand_tokens = tuple(normalize_spanish(brand or "", max_length=160).split())
    alias_tokens = tuple(normalize_spanish(alias, max_length=160) for alias in aliases)
    alias_flat = tuple(token for alias in alias_tokens for token in alias.split())
    matched: list[str] = []
    all_query_present = True
    for query_token in query_tokens:
        variants = _variants(query_token)
        if any(variant in name_tokens for variant in variants):
            matched.append(query_token)
        elif any(variant in alias_flat for variant in variants):
            matched.append(query_token)
        else:
            all_query_present = False
    if not matched:
        return None
    if _has_context_exclusion(query_tokens, name_tokens):
        return None
    # Product name evidence dominates brand/alias hints.  A one-token query
    # must appear as a whole token in the name, never merely in the brand.
    name_matches = sum(any(variant in name_tokens for variant in _variants(token)) for token in query_tokens)
    if name_matches == 0:
        return None
    score = name_matches * 100
    if all_query_present:
        score += 80
    phrase = tuple(_variants(token)[0] for token in query_tokens)
    if _contains_sequence(name_tokens, phrase):
        score += 60
    if len(query_tokens) == 1 and name_tokens[0] in _variants(query_tokens[0]):
        score += 20
    if brand_tokens and any(token in brand_tokens for token in query_tokens):
        score += 5
    return score, tuple(sorted(set(matched)))


def search_products(
    products: Iterable[Mapping[str, Any]],
    query: str,
    *,
    limit: int = MAX_RESULTS,
    max_candidates: int = MAX_CANDIDATES,
    _allow_discovery: bool = False,
) -> list[SearchResult]:
    """Return a bounded, deterministic top-k search over product records."""

    maximum_limit = MAX_DISCOVERY_RESULTS if _allow_discovery else MAX_RESULTS
    if not isinstance(limit, int) or not 1 <= limit <= maximum_limit:
        raise SearchError(f"limit must be between 1 and {maximum_limit}")
    normalized_query = normalize_spanish(query)
    if not normalized_query:
        return []
    if not isinstance(max_candidates, int) or max_candidates <= 0:
        raise SearchError("max_candidates must be positive")
    best: list[tuple[int, str, SearchResult]] = []
    seen = 0
    for product in products:
        seen += 1
        if seen > max_candidates:
            raise SearchError(f"search candidate bound exceeded ({max_candidates})")
        if not isinstance(product, Mapping):
            continue
        key = product.get("productEvidenceKey")
        name = product.get("name")
        if not isinstance(key, str) or not key or not isinstance(name, str) or not name:
            continue
        scored = _score(normalized_query, name, product.get("brand"), product.get("canonicalSearchAliases", ()))
        if scored is None:
            continue
        score, matched = scored
        result = SearchResult(key, name, product.get("brand") if isinstance(product.get("brand"), str) else None, product.get("gtin") if isinstance(product.get("gtin"), str) else None, score, matched)
        best.append((score, key, result))
        best.sort(key=lambda item: (-item[0], item[1], item[2].name))
        if len(best) > limit:
            best.pop()
    return [item[2] for item in best]


def evaluate_audit(products: Iterable[Mapping[str, Any]], audit_rows: Iterable[Mapping[str, Any]], *, limit: int = 5) -> dict[str, Any]:
    """Evaluate a manually labelled fixture without claiming recall."""

    grouped: dict[str, list[Mapping[str, Any]]] = {}
    for row in audit_rows:
        query = row.get("query") if isinstance(row, Mapping) else None
        if isinstance(query, str):
            grouped.setdefault(query, []).append(row)
    materialized: list[Mapping[str, Any]] = []
    for product in products:
        if len(materialized) >= MAX_CANDIDATES:
            raise SearchError(f"search candidate bound exceeded ({MAX_CANDIDATES})")
        materialized.append(product)
    per_query: list[dict[str, Any]] = []
    total_relevant = 0
    total_hits = 0
    for query in sorted(grouped, key=lambda value: normalize_spanish(value)):
        labels = {row.get("productEvidenceKey"): bool(row.get("relevant")) for row in grouped[query]}
        results = search_products(materialized, query, limit=limit)
        hits = sum(1 for result in results if labels.get(result.product_evidence_key) is True)
        false_positives = [result.product_evidence_key for result in results if labels.get(result.product_evidence_key) is False]
        audited_positives = sum(1 for value in labels.values() if value)
        precision = (hits / len(results)) if results else 0.0
        per_query.append({"query": query, "topK": len(results), "hits": hits, "falsePositives": false_positives, "auditedPositives": audited_positives, "precisionAt5": f"{precision:.4f}"})
        total_hits += hits
        total_relevant += len(results)
    overall = total_hits / total_relevant if total_relevant else 0.0
    return {"schemaVersion": SEARCH_SCHEMA_VERSION, "queries": per_query, "overallPrecisionAt5": f"{overall:.4f}", "queryCount": len(per_query), "recallClaimed": False}


__all__ = ["MAX_CANDIDATES", "MAX_DISCOVERY_RESULTS", "MAX_RESULTS", "SEARCH_SCHEMA_VERSION", "SearchError", "SearchResult", "evaluate_audit", "normalize_spanish", "search_products"]

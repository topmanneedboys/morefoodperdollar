#!/usr/bin/env python3
"""Build a safe, source-backed product identity lexicon.

The Argentina release contains product identities, but it does not contain a
curated synonym for every identity.  This module derives only bounded aliases
from the canonical name and a small, reviewable bilingual vocabulary.  It
never invents a product, price, offer, stock state, or category.  Ambiguous
generic words (for example ``rice``) are recorded as family choices rather
than being allowed to select one product.

The generated lexicon is a derived release artifact.  It deliberately keeps
the provider source outside Git; the reviewed aliases are consumed by the
SearchPack build and the bounded Search clarification UI.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence

from tools.consumer_input_intelligence import normalize_text


SCHEMA_VERSION = "valuepilot-product-identity-lexicon-v1"
POLICY_VERSION = "valuepilot-product-identity-policy-v1"
MAX_NAME_LENGTH = 512
MAX_ALIAS_LENGTH = 96


@dataclass(frozen=True)
class FamilyRule:
    """Conservative family vocabulary used for clarification, not pricing."""

    key: str
    spanish_aliases: tuple[str, ...]
    english_aliases: tuple[str, ...]
    include_terms: tuple[str, ...]
    excluded_phrases: tuple[tuple[str, ...], ...]
    forms: Mapping[str, tuple[tuple[str, ...], tuple[str, ...]]]


# These are deliberately small and explicit.  A product is classified only
# when its source name contains one of these terms.  New families should be
# added with tests and a reviewed translation, never generated from a model.
FAMILY_RULES: tuple[FamilyRule, ...] = (
    FamilyRule(
        key="rice",
        spanish_aliases=("arroz",),
        english_aliases=("rice",),
        include_terms=("arroz", "rice"),
        excluded_phrases=(
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
            ("galletas", "de", "arroz"),
            ("galletitas", "de", "arroz"),
            ("torta", "de", "arroz"),
            ("barra", "arroz"),
            ("arroz", "barra"),
            ("alfajor", "arroz"),
            ("arroz", "dulce"),
            ("dulce", "de", "arroz"),
            ("arroz", "inflado"),
            ("cereal", "de", "arroz"),
            ("snack", "de", "arroz"),
            ("tortita", "de", "arroz"),
            ("rice", "dessert"),
            ("rice", "pudding"),
            ("rice", "flour"),
            ("rice", "milk"),
            ("rice", "drink"),
        ),
        forms={
            "grain": (("grano", "granos", "largo", "blanco", "integral", "parboil", "parboiled"), ("grain", "grains", "long", "white", "brown", "parboil", "parboiled")),
            "flour": (("harina",), ("flour",)),
            "drink": (("bebida", "jugo"), ("drink", "juice")),
            "dessert": (("postre", "pudin", "pudding", "budin", "galleta", "galletitas", "galletita", "galletas", "torta", "barra", "alfajor", "dulce", "inflado", "cereal", "snack", "tortita"), ("dessert", "pudding", "cake", "cracker", "snack")),
        },
    ),
    FamilyRule(
        key="lychee",
        spanish_aliases=("lichi", "litchi"),
        english_aliases=("lychee", "litchi"),
        include_terms=("lichi", "litchi", "lychee"),
        excluded_phrases=(
            ("jugo", "de", "lichi"),
            ("jugo", "de", "litchi"),
            ("jugo", "de", "lychee"),
            ("nectar", "de", "lichi"),
            ("bebida", "de", "lichi"),
            ("jarabe", "de", "lichi"),
            ("almibar", "de", "lichi"),
            ("sabor", "lichi"),
            ("sabor", "litchi"),
            ("yogur", "de", "lichi"),
            ("postre", "de", "lichi"),
            ("dulce", "de", "lichi"),
            ("juice", "lychee"),
            ("drink", "lychee"),
            ("candy", "lychee"),
            ("jabon",),
            ("shampoo",),
            ("crema",),
            ("perfume",),
            ("cosmetico",),
            ("te",),
        ),
        forms={
            "fresh": (("fresco", "fresca", "frescos", "frescas", "fruta"), ("fresh", "fruit")),
            "drink": (("jugo", "nectar", "bebida", "juice", "drink"), ("juice", "nectar", "drink")),
            "preserved": (("almibar", "jarabe", "conserva", "enlatado"), ("syrup", "canned", "preserved")),
            "dessert": (("postre", "dulce", "caramelo"), ("dessert", "candy")),
        },
    ),
    FamilyRule(
        key="yerba_mate",
        spanish_aliases=("yerba", "yerba mate", "mate"),
        english_aliases=("yerba mate", "mate"),
        # ``mate`` is also a common stationery brand (Paper Mate).  A bare
        # token is therefore not enough evidence for the grocery family.
        include_terms=("yerba", "yerba mate", "mate cocido", "mate cebado", "mate amargo", "mate suave"),
        excluded_phrases=(
            ("bebida", "de", "yerba"),
            ("gaseosa", "de", "yerba"),
            ("energizante", "de", "yerba"),
            ("extracto", "de", "yerba"),
            ("sabor", "yerba"),
            ("te", "de", "yerba"),
            ("jugo", "de", "yerba"),
            ("jarabe", "de", "yerba"),
            ("yerba", "gaseosa"),
            ("yerba", "drink"),
        ),
        forms={
            "loose_leaves": (("yerba", "mate", "hoja", "hojas"), ("yerba", "mate", "leaves")),
            "tea_bags": (("te", "saquito", "saquitos", "filtro", "infusion"), ("tea", "teabag", "tea bags", "infusion")),
            "drink": (("bebida", "gaseosa", "energizante", "jugo"), ("drink", "soda", "energy", "juice")),
            "extract": (("extracto", "jarabe", "sabor"), ("extract", "syrup", "flavour", "flavor")),
        },
    ),
    FamilyRule(
        key="egg",
        spanish_aliases=("huevo", "huevos"),
        english_aliases=("egg", "eggs"),
        include_terms=("huevo", "huevos", "egg", "eggs"),
        # A bare egg query must not silently present toys, confectionery,
        # pasta, or other products that only happen to contain the token.
        # These markers are observed catalog-name evidence; they do not make
        # a claim about ingredients, price, or availability.
        excluded_phrases=(
            ("fideo",), ("fideos",), ("pasta",), ("noodle",), ("noodles",),
            ("chocolate",), ("cacao",), ("pascua",), ("easter",), ("sorpresa",), ("surprise",),
            ("dino",), ("dinosaurio",), ("dinosaur",), ("unicornio",), ("unicorn",),
            ("juguete",), ("juego",), ("toy",), ("playset",), ("coleccionable",), ("collectible",),
            ("figura",), ("figure",), ("smash",), ("smashers",), ("rainbocorn",),
            ("licor",), ("liqueur",), ("bebida",), ("drink",),
        ),
        forms={
            "whole_egg": (
                ("fresco", "fresca", "frescos", "frescas", "blanco", "blancos", "blanca", "blancas", "color", "colores", "entero", "enteros", "cascara", "cascaron", "gallina", "gallinas"),
                ("fresh", "white", "whole", "shell", "chicken"),
            ),
            "liquid": (
                ("liquido", "liquida", "liquidos", "liquidas", "pasteurizado", "pasteurizada", "clara", "claras", "yema", "yemas", "albumina"),
                ("liquid", "pasteurized", "white", "yolk", "albumin"),
            ),
            "powder": (("polvo", "deshidratado", "deshidratada"), ("powder", "dehydrated")),
            "chocolate": (("chocolate", "cacao", "pascua", "easter"), ("chocolate", "cocoa", "easter")),
            "toy": (("dino", "dinosaurio", "dinosaur", "unicornio", "unicorn", "juguete", "juego", "sorpresa", "smash", "smashers", "playset", "coleccionable", "figura", "rainbocorn"), ("dino", "dinosaur", "unicorn", "toy", "surprise", "smash", "playset", "collectible", "figure")),
            "pasta": (("fideo", "fideos", "pasta", "tallarines"), ("noodle", "noodles", "pasta")),
            "drink": (("bebida", "licor", "licores"), ("drink", "liqueur", "eggnog")),
        },
    ),
    FamilyRule(
        key="carrot",
        spanish_aliases=("zanahoria",),
        english_aliases=("carrot",),
        include_terms=("zanahoria", "zanahorias", "carrot", "carrots"),
        # These are identity-form signals, not claims about what the product
        # is made of.  They let a customer separate the vegetable from
        # carrot-flavoured drinks, desserts, cosmetics, and supplements after
        # the bounded discovery window has shown those forms.
        excluded_phrases=(
            ("jugo",),
            ("zumo",),
            ("bebida",),
            ("nectar",),
            ("juice",),
            ("drink",),
            ("beverage",),
            ("postre",),
            ("torta",),
            ("pastel",),
            ("budin",),
            ("pudin",),
            ("galleta",),
            ("galletita",),
            ("cake",),
            ("dessert",),
            ("locion",),
            ("lotion",),
            ("crema",),
            ("cream",),
            ("protector",),
            ("sunscreen",),
            ("cosmetico",),
            ("cosmetics",),
            ("maquillaje",),
            ("makeup",),
            ("perfume",),
            ("jabon",),
            ("soap",),
            ("shampoo",),
            ("suplemento",),
            ("supplement",),
            ("extracto",),
            ("extract",),
            ("capsula",),
            ("capsule",),
        ),
        forms={
            "vegetable": (
                ("fresca", "fresco", "frescos", "frescas", "crudo", "cruda", "raiz", "raices", "baby", "mini", "verdura", "vegetal"),
                ("fresh", "raw", "root", "baby", "mini", "vegetable"),
            ),
            "drink": (
                ("jugo", "zumo", "bebida", "nectar"),
                ("juice", "drink", "beverage"),
            ),
            "dessert": (
                ("postre", "torta", "pastel", "budin", "pudin", "galleta", "galletita"),
                ("cake", "dessert", "cookie"),
            ),
            "cosmetics": (
                ("locion", "crema", "protector", "cosmetico", "maquillaje", "perfume", "jabon", "shampoo"),
                ("lotion", "cream", "sunscreen", "cosmetics", "makeup", "perfume", "soap", "shampoo"),
            ),
            "supplement": (
                ("suplemento", "extracto", "capsula", "polvo"),
                ("supplement", "extract", "capsule", "powder"),
            ),
        },
    ),
)


def _generic_family_rules() -> tuple[FamilyRule, ...]:
    """Extend the reviewed input vocabulary to ordinary product families.

    The existing input vocabulary is deliberately the source of truth for
    these common translations.  We do not ask a model to translate 87,000
    rows.  The specialized families with important derived-form traps above keep
    their richer hand-reviewed rules; the remaining vocabulary gets a simple
    observed-name family with no invented form choices.
    """

    path = Path(__file__).with_name("data") / "argentina_input_aliases.json"
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return ()
    aliases = data.get("aliases", {})
    if not isinstance(aliases, Mapping):
        return ()
    product_forms = data.get("productForms", {})
    if not isinstance(product_forms, Mapping):
        product_forms = {}
    grouped: dict[str, list[str]] = defaultdict(list)
    for phrase, target in aliases.items():
        if isinstance(phrase, str) and isinstance(target, str) and phrase.strip() and target.strip():
            grouped[normalize_text(target)].append(phrase)
    reviewed = {"carrot", "egg", "rice", "lychee", "yerba mate"}
    english_terms = {
        "apple", "avocado", "beans", "beef", "bread", "butter", "cheese", "chicken",
        "chocolate", "coconut", "coffee", "egg", "mango", "milk", "orange", "peach", "pineapple",
        "rice", "shampoo", "soap", "soft drink", "strawberry", "sugar", "tea", "toilet paper",
        "tomato", "cookie", "cookies", "biscuit",
    }
    rules: list[FamilyRule] = []
    for normalized_target, phrases in sorted(grouped.items()):
        if normalized_target in reviewed:
            continue
        target = normalized_target
        spanish = tuple(sorted({phrase for phrase in phrases if normalize_text(phrase) not in english_terms}))
        english = tuple(sorted({phrase for phrase in phrases if normalize_text(phrase) in english_terms} | {target}))
        if not spanish and not english:
            continue
        raw_forms = product_forms.get(target)
        exclusions: tuple[tuple[str, ...], ...] = ()
        if isinstance(raw_forms, Mapping) and isinstance(raw_forms.get("exclude"), Sequence) and not isinstance(raw_forms.get("exclude"), (str, bytes)):
            exclusions = tuple(tuple(normalize_text(str(value), max_length=MAX_ALIAS_LENGTH).split()) for value in raw_forms["exclude"] if str(value).strip())
        rules.append(
            FamilyRule(
                key=target.replace(" ", "_"),
                spanish_aliases=spanish,
                english_aliases=english,
                include_terms=tuple(sorted(set(phrases) | {target})),
                excluded_phrases=exclusions,
                forms={},
            )
        )
    return tuple(rules)


# Add the broader reviewed vocabulary after the high-risk families so the
# specialized rules remain the only source of their derived-form policy.
FAMILY_RULES = FAMILY_RULES + _generic_family_rules()


# Search clarification is intentionally a small presentation projection.  It
# is generated only when the bounded result window contains two or more
# observed forms for one reviewed family.  The query terms below are Spanish
# catalog terms so that a choice made from the bilingual UI remains searchable
# by the existing Argentina SearchPack.
_FORM_PRESENTATION: Mapping[str, Mapping[str, Mapping[str, str]]] = {
    "rice": {
        "grain": {
            "labelEs": "Arroz en grano",
            "labelEn": "Rice grains",
            "query": "arroz largo",
        },
        "flour": {
            "labelEs": "Harina de arroz",
            "labelEn": "Rice flour",
            "query": "harina de arroz",
        },
        "drink": {
            "labelEs": "Bebida de arroz",
            "labelEn": "Rice drink",
            "query": "bebida de arroz",
        },
        "dessert": {
            "labelEs": "Dulces o galletas de arroz",
            "labelEn": "Rice sweets or cakes",
            "query": "arroz dulce",
        },
        "unspecified": {
            "labelEs": "Arroz (presentación no especificada)",
            "labelEn": "Rice (presentation unspecified)",
            "query": "arroz",
        },
    },
    "lychee": {
        "fresh": {
            "labelEs": "Lichi fresco",
            "labelEn": "Fresh lychee",
            "query": "lichi fresco",
        },
        "drink": {
            "labelEs": "Jugo o bebida de lichi",
            "labelEn": "Lychee juice or drink",
            "query": "jugo de lichi",
        },
        "preserved": {
            "labelEs": "Lichi en almíbar o conserva",
            "labelEn": "Lychee in syrup or preserved",
            "query": "lichi en almibar",
        },
        "dessert": {
            "labelEs": "Dulce de lichi",
            "labelEn": "Lychee dessert",
            "query": "dulce de lichi",
        },
        "unspecified": {
            "labelEs": "Lichi (presentación no especificada)",
            "labelEn": "Lychee (presentation unspecified)",
            "query": "lichi",
        },
    },
    "yerba_mate": {
        "loose_leaves": {
            "labelEs": "Yerba mate suelta",
            "labelEn": "Loose yerba mate",
            "query": "yerba mate",
        },
        "tea_bags": {
            "labelEs": "Yerba mate en saquitos",
            "labelEn": "Yerba mate tea bags",
            "query": "te de yerba mate",
        },
        "drink": {
            "labelEs": "Bebida de yerba mate",
            "labelEn": "Yerba mate drink",
            "query": "bebida de yerba",
        },
        "extract": {
            "labelEs": "Extracto de yerba mate",
            "labelEn": "Yerba mate extract",
            "query": "extracto de yerba",
        },
        "unspecified": {
            "labelEs": "Yerba mate (presentación no especificada)",
            "labelEn": "Yerba mate (presentation unspecified)",
            "query": "yerba mate",
        },
    },
    "egg": {
        "whole_egg": {
            "labelEs": "Huevo de gallina",
            "labelEn": "Chicken eggs",
            # This choice represents the shopper's shell-egg category, not
            # one freshness descriptor.  A freshness-only query collapsed
            # the live result to the single identity whose name contains
            # ``fresco`` even though the catalog contains white, brown,
            # colored, free-range, and other shell-egg presentations.
            # ``huevo de gallina`` is explicit enough to select this form and
            # suppress the clarification card, while the backend's reviewed
            # egg-family rules still include ordinary shell-egg presentations
            # such as white, brown, colored, and pack-size variants.
            "query": "huevo de gallina",
        },
        "liquid": {
            "labelEs": "Huevo líquido",
            "labelEn": "Liquid egg",
            "query": "huevo liquido",
        },
        "powder": {
            "labelEs": "Huevo en polvo",
            "labelEn": "Egg powder",
            "query": "huevo en polvo",
        },
        "chocolate": {
            "labelEs": "Huevo de chocolate o Pascua",
            "labelEn": "Chocolate or Easter egg",
            "query": "huevo chocolate",
        },
        "toy": {
            "labelEs": "Huevo juguete o coleccionable",
            "labelEn": "Toy or collectible egg",
            "query": "huevo dino",
        },
        "pasta": {
            "labelEs": "Pasta al huevo",
            "labelEn": "Egg pasta",
            "query": "fideos al huevo",
        },
        "drink": {
            "labelEs": "Bebida o licor de huevo",
            "labelEn": "Egg drink or liqueur",
            "query": "bebida de huevo",
        },
    },
    "carrot": {
        "vegetable": {
            "labelEs": "Zanahoria como verdura",
            "labelEn": "Carrot vegetable",
            "query": "carrot fresh",
        },
        "drink": {
            "labelEs": "Jugo o bebida de zanahoria",
            "labelEn": "Carrot juice or drink",
            "query": "carrot juice",
        },
        "dessert": {
            "labelEs": "Torta o postre de zanahoria",
            "labelEn": "Carrot cake or dessert",
            "query": "carrot cake",
        },
        "cosmetics": {
            "labelEs": "Cosméticos con zanahoria",
            "labelEn": "Carrot cosmetics",
            "query": "carrot lotion",
        },
        "supplement": {
            "labelEs": "Suplemento o extracto de zanahoria",
            "labelEn": "Carrot supplement or extract",
            "query": "carrot supplement",
        },
        "unspecified": {
            "labelEs": "Zanahoria (presentación no especificada)",
            "labelEn": "Carrot (presentation unspecified)",
            "query": "carrot",
        },
    },
}


def _query_matches_family(query: str, rule: FamilyRule) -> bool:
    tokens = _tokens(query)
    return bool(tokens) and _contains_any(tokens, rule.include_terms)


def family_query_key(query: str) -> str | None:
    """Return the reviewed family selected by a query, when one is present."""

    normalized_query = " ".join(str(query or "").split())
    if not normalized_query:
        return None
    selected_rule = next((rule for rule in FAMILY_RULES if _query_matches_family(normalized_query, rule)), None)
    return selected_rule.key if selected_rule is not None else None


def build_family_clarification(
    query: str,
    candidates: Iterable[Mapping[str, Any]],
) -> dict[str, Any] | None:
    """Return an observed-form clarification for a bounded Search result set.

    This helper never resolves a product and never adds catalog rows.  It only
    groups the names already returned by Search, then offers Spanish query
    refinements with English labels for the UI.  Invalid or incomplete rows are
    ignored so a malformed optional hint cannot make a valid search fail.
    """

    normalized_query = " ".join(str(query or "").split())
    if not normalized_query:
        return None
    selected_rule = next((rule for rule in FAMILY_RULES if _query_matches_family(normalized_query, rule)), None)
    if selected_rule is None or selected_rule.key not in _FORM_PRESENTATION:
        return None

    evidence: Counter[str] = Counter()
    for candidate in candidates:
        if not isinstance(candidate, Mapping):
            continue
        try:
            identity = derive_identity_record(candidate)
        except (TypeError, ValueError):
            continue
        for family in identity.get("familyCandidates", ()):
            if not isinstance(family, Mapping) or family.get("family") != selected_rule.key:
                continue
            for form in family.get("observedForms", ()):
                if isinstance(form, str) and form in _FORM_PRESENTATION[selected_rule.key]:
                    evidence[form] += 1

    # A clarification is useful only when it can separate at least two
    # observed forms.  Keep the order stable and cap it for a compact UI.
    # Keep the curated presentation order rather than sorting by the internal
    # form key.  This puts the ordinary food form first while remaining fully
    # deterministic for every family.
    forms = [form for form in _FORM_PRESENTATION[selected_rule.key] if evidence[form] > 0]
    if len(forms) < 2:
        return None
    choices = []
    for form in forms[:6]:
        copy = _FORM_PRESENTATION[selected_rule.key][form]
        choices.append(
            {
                "id": f"{selected_rule.key}:{form}",
                "label": copy["labelEs"],
                "labelEn": copy["labelEn"],
                "query": copy["query"],
            }
        )
    return {
        "code": "PRODUCT_FAMILY_CLARIFICATION",
        "family": selected_rule.key,
        "message": "Elegí el tipo de producto para afinar la búsqueda.",
        "messageEn": "Choose a product type to refine the search.",
        "choices": choices,
        "evidence": "OBSERVED_CATALOG_NAMES",
    }


# Only high-confidence, common inflection pairs are generated.  Generating a
# suffix-stripped alias for every Spanish word would turn brand and package
# text into false product identities.
SAFE_INFLECTIONS: Mapping[str, tuple[str, ...]] = {
    "huevos": ("huevo",),
    "fideos": ("fideo",),
    "galletitas": ("galletita", "galleta"),
    "galletas": ("galleta",),
    "yogures": ("yogur",),
    "gaseosas": ("gaseosa",),
    "tomates": ("tomate",),
    "manzanas": ("manzana",),
    "naranjas": ("naranja",),
    "higienicos": ("higienico",),
    "panales": ("panal",),
}


def _tokens(value: str) -> tuple[str, ...]:
    return tuple(normalize_text(value, max_length=MAX_NAME_LENGTH).split())


def _contains_sequence(tokens: Sequence[str], sequence: Sequence[str]) -> bool:
    if not sequence or len(sequence) > len(tokens):
        return False
    size = len(sequence)
    return any(tuple(tokens[index : index + size]) == tuple(sequence) for index in range(len(tokens) - size + 1))


def _contains_any(tokens: Sequence[str], values: Iterable[str]) -> bool:
    return any(_contains_sequence(tokens, _tokens(value)) for value in values)


def family_query_form(query: str) -> tuple[str, str] | None:
    """Return one explicit family/form refinement present in a query.

    A bare family query (``carrot`` or ``zanahoria``) remains broad so the
    clarification card can show the observed choices.  A choice query such as
    ``carrot juice`` has one observed form and can therefore be used as a
    conservative identity-only result filter.
    """

    normalized_query = " ".join(str(query or "").split())
    if not normalized_query:
        return None
    selected_rule = next((rule for rule in FAMILY_RULES if _query_matches_family(normalized_query, rule)), None)
    if selected_rule is None or selected_rule.key not in _FORM_PRESENTATION:
        return None
    tokens = _tokens(normalized_query)
    # Family words such as ``yerba`` and ``mate`` are also present in the
    # loose-leaf form vocabulary.  They identify the family, but they do not
    # identify that presentation.  Ignore those generic aliases while
    # detecting an explicit form so a query such as ``te de yerba mate``
    # selects tea bags instead of being treated as an unfiltered family
    # search.
    generic_family_terms = {
        token
        for term in (*selected_rule.spanish_aliases, *selected_rule.english_aliases, *selected_rule.include_terms)
        for token in _tokens(term)
        if token
    }
    observed = [
        form
        for form, (spanish_terms, english_terms) in selected_rule.forms.items()
        if _contains_any(
            tokens,
            tuple(
                term
                for term in (*spanish_terms, *english_terms)
                if not set(_tokens(term)).intersection(generic_family_terms)
            ),
        )
    ]
    if len(observed) != 1:
        return None
    return selected_rule.key, observed[0]


def filter_family_candidates(
    query: str,
    candidates: Iterable[Mapping[str, Any]],
) -> tuple[Mapping[str, Any], ...]:
    """Keep candidates matching an explicit observed family form.

    The filter is fail-open when no candidate can be classified.  That keeps
    an incomplete or newly added source row searchable while preventing a
    known ``carrot juice`` refinement from retaining a classified carrot
    lotion or cake in the same bounded result window.
    """

    values = tuple(candidate for candidate in candidates if isinstance(candidate, Mapping))
    normalized_query = " ".join(str(query or "").split())
    selected_rule = next((rule for rule in FAMILY_RULES if _query_matches_family(normalized_query, rule)), None)
    context = family_query_form(normalized_query)
    if context is None:
        # Bare reviewed-family queries should show the direct family products
        # first.  Derived rows (rice bars, lychee soap, carrot lotion, toy
        # eggs, and similar products) remain available to the clarification
        # card, but must not silently take over the main result table.
        if selected_rule is not None and selected_rule.key in _FORM_PRESENTATION:
            direct: list[Mapping[str, Any]] = []
            for candidate in values:
                try:
                    identity = derive_identity_record(candidate)
                except (TypeError, ValueError):
                    continue
                if any(
                    isinstance(family, Mapping)
                    and family.get("family") == selected_rule.key
                    and family.get("role") == "DIRECT"
                    for family in identity.get("familyCandidates", ())
                ):
                    direct.append(candidate)
            return tuple(direct) if direct else values
        return values
    family_key, form = context
    filtered: list[Mapping[str, Any]] = []
    for candidate in values:
        try:
            identity = derive_identity_record(candidate)
        except (TypeError, ValueError):
            continue
        if any(
            isinstance(family, Mapping)
            and family.get("family") == family_key
            and form in family.get("observedForms", ())
            for family in identity.get("familyCandidates", ())
        ):
            filtered.append(candidate)
    if filtered:
        return tuple(filtered)
    # Explicit reviewed-family choices are safety refinements.  Returning
    # unrelated lexical partial matches (for example ordinary juice for a
    # lychee-drink choice) is worse than an empty result when the bounded
    # source window contains no observed match for that form.
    if family_key in _FORM_PRESENTATION:
        return ()
    return values


def _alias(value: str, language: str, provenance: str, *, confidence: str = "HIGH") -> dict[str, str]:
    return {
        "value": value,
        "language": language,
        "provenance": provenance,
        "confidence": confidence,
    }


def _add_alias(aliases: dict[str, dict[str, str]], value: str, language: str, provenance: str, *, confidence: str = "HIGH") -> None:
    normalized = normalize_text(value, max_length=MAX_ALIAS_LENGTH)
    if not normalized:
        return
    key = f"{language}:{normalized}"
    if key not in aliases:
        aliases[key] = _alias(value.strip(), language, provenance, confidence=confidence)


def _family_observation(tokens: tuple[str, ...], rule: FamilyRule) -> tuple[bool, bool, tuple[str, ...], tuple[str, ...]]:
    """Return (matched, derived, forms, matched include terms)."""

    include = tuple(term for term in rule.include_terms if _contains_sequence(tokens, _tokens(term)))
    if not include:
        return False, False, (), ()
    derived = any(_contains_sequence(tokens, phrase) for phrase in rule.excluded_phrases)
    forms: list[str] = []
    for form_key, (spanish_terms, english_terms) in rule.forms.items():
        if _contains_any(tokens, spanish_terms) or _contains_any(tokens, english_terms):
            forms.append(form_key)
    if not forms:
        # A matched egg without a derived marker is the ordinary whole-egg
        # presentation.  This keeps a plain ``eggs`` search useful while
        # retaining separate observed choices for toys and other forms.
        forms.append("whole_egg" if rule.key == "egg" and not derived else "unspecified")
    return True, derived, tuple(sorted(set(forms))), tuple(sorted(set(include)))


def derive_identity_record(source: Mapping[str, Any]) -> dict[str, Any]:
    """Build one identity-only record from a qualified product row."""

    key = source.get("productEvidenceKey")
    name = source.get("name")
    if not isinstance(key, str) or not key.strip() or len(key) > 256:
        raise ValueError("productEvidenceKey is invalid")
    if not isinstance(name, str) or not name.strip() or len(name) > MAX_NAME_LENGTH:
        raise ValueError("product name is invalid")
    clean_name = " ".join(name.split())
    tokens = _tokens(clean_name)
    aliases: dict[str, dict[str, str]] = {}
    # The normalized canonical phrase is useful for accent and punctuation
    # tolerance, but it is tagged as source-derived rather than translated.
    normalized_name = normalize_text(clean_name, max_length=MAX_NAME_LENGTH)
    if normalized_name != clean_name.casefold():
        _add_alias(aliases, normalized_name, "es", "SOURCE_NORMALIZED")
    for token in tokens:
        for singular in SAFE_INFLECTIONS.get(token, ()):
            _add_alias(aliases, singular, "es", "SAFE_INFLECTION")

    family_candidates: list[dict[str, Any]] = []
    for rule in FAMILY_RULES:
        matched, derived, forms, matched_terms = _family_observation(tokens, rule)
        if not matched:
            continue
        if not derived:
            for value in rule.spanish_aliases:
                _add_alias(aliases, value, "es", "CURATED_BILINGUAL")
            for value in rule.english_aliases:
                _add_alias(aliases, value, "en", "CURATED_BILINGUAL")
        # Preserve a bilingual alias for the observed presentation.  A
        # derived row intentionally does not receive the bare family alias,
        # but it must remain reachable when the customer chooses that form
        # from the clarification card (for example ``carrot cake``).
        for form in forms:
            form_terms = rule.forms.get(form)
            if not form_terms:
                continue
            spanish_terms, english_terms = form_terms
            if spanish_terms and rule.spanish_aliases:
                if form in {"vegetable", "fresh", "grain", "loose_leaves"}:
                    spanish_value = f"{rule.spanish_aliases[0]} {spanish_terms[0]}"
                else:
                    spanish_value = f"{spanish_terms[0]} de {rule.spanish_aliases[0]}"
                _add_alias(aliases, spanish_value, "es", "CURATED_FORM")
            if english_terms and rule.english_aliases:
                _add_alias(aliases, f"{rule.english_aliases[0]} {english_terms[0]}", "en", "CURATED_FORM")
        family_candidates.append(
            {
                "family": rule.key,
                "role": "DERIVED" if derived else "DIRECT",
                "matchedTerms": list(matched_terms),
                "observedForms": list(forms),
            }
        )

    # A product may appear in more than one family (for example a mate drink
    # also contains the word yerba).  Keep all evidence and let the search/UI
    # layer clarify instead of silently choosing one.
    family_candidates.sort(key=lambda item: (item["family"], item["role"], item["matchedTerms"]))
    alias_values = sorted(aliases.values(), key=lambda item: (normalize_text(item["value"]), item["language"], item["provenance"]))
    result: dict[str, Any] = {
        "productEvidenceKey": key,
        "name": clean_name,
        "brand": source.get("brand") if isinstance(source.get("brand"), str) else None,
        "gtin": source.get("gtin") if isinstance(source.get("gtin"), str) else None,
        "quantity": dict(source["quantity"]) if isinstance(source.get("quantity"), Mapping) else None,
        "familyCandidates": family_candidates,
        "aliases": alias_values,
    }
    if not family_candidates:
        result["classification"] = "UNCLASSIFIED"
    elif len(family_candidates) == 1:
        result["classification"] = family_candidates[0]["role"]
    else:
        result["classification"] = "MULTIPLE_FAMILIES"
    return result


def _open_text(path: Path, mode: str):
    if path.suffix == ".gz":
        return gzip.open(path, mode + "t", encoding="utf-8", newline="")
    return path.open(mode + "t", encoding="utf-8", newline="")


def _iter_jsonl(path: Path) -> Iterator[tuple[int, Mapping[str, Any]]]:
    try:
        with _open_text(path, "r") as handle:
            for line_number, line in enumerate(handle, 1):
                if not line.strip():
                    continue
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise ValueError(f"source line {line_number} is not valid JSON") from exc
                if not isinstance(value, Mapping):
                    raise ValueError(f"source line {line_number} is not an object")
                yield line_number, value
    except OSError as exc:
        raise ValueError(f"could not read source: {path}") from exc


def _canonical_json(value: Mapping[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _open_output(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix == ".gz":
        # mtime=0 keeps the derived artifact reproducible across builds.
        return gzip.GzipFile(filename=str(path), mode="wb", mtime=0)
    return path.open("wb")


def build_identity_lexicon(source_path: Path | str, output_path: Path | str, manifest_path: Path | str | None = None) -> dict[str, Any]:
    """Stream a qualified products file into a deterministic identity lexicon."""

    source = Path(source_path).resolve()
    output = Path(output_path).resolve()
    manifest_output = Path(manifest_path).resolve() if manifest_path else output.with_suffix(output.suffix + ".manifest.json")
    if not source.is_file():
        raise ValueError(f"source does not exist: {source}")
    if source == output or source == manifest_output:
        raise ValueError("output must be separate from source")

    count = 0
    classifications = Counter()
    family_counts: dict[str, Counter[str]] = defaultdict(Counter)
    alias_usage: Counter[tuple[str, str]] = Counter()
    alias_products: dict[tuple[str, str], set[str]] = defaultdict(set)
    seen_keys: set[str] = set()
    output.parent.mkdir(parents=True, exist_ok=True)
    with _open_output(output) as raw_output:
        for line_number, source_record in _iter_jsonl(source):
            try:
                identity = derive_identity_record(source_record)
            except ValueError as exc:
                raise ValueError(f"source line {line_number}: {exc}") from exc
            key = identity["productEvidenceKey"]
            if key in seen_keys:
                raise ValueError(f"source line {line_number}: duplicate productEvidenceKey {key}")
            seen_keys.add(key)
            raw_output.write((_canonical_json(identity) + "\n").encode("utf-8"))
            count += 1
            classifications[str(identity["classification"])] += 1
            for family in identity["familyCandidates"]:
                family_counts[family["family"]][family["role"]] += 1
                for form in family["observedForms"]:
                    family_counts[family["family"]][f"form:{form}"] += 1
            for alias in identity["aliases"]:
                alias_key = (normalize_text(alias["value"], max_length=MAX_ALIAS_LENGTH), alias["language"])
                alias_usage[alias_key] += 1
                alias_products[alias_key].add(key)

    ambiguous_aliases = sum(1 for key, products in alias_products.items() if len(products) > 1 and alias_usage[key] > 1)
    family_summary = {
        family: {
            "productCount": sum(value for label, value in counts.items() if label in {"DIRECT", "DERIVED"}),
            "directCount": counts.get("DIRECT", 0),
            "derivedCount": counts.get("DERIVED", 0),
            "observedForms": {label.removeprefix("form:"): value for label, value in sorted(counts.items()) if label.startswith("form:")},
        }
        for family, counts in sorted(family_counts.items())
    }
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "source": {
            "path": str(source),
            "sha256": _sha256_file(source),
            "recordCount": count,
        },
        "output": {
            "path": str(output),
            "sha256": _sha256_file(output),
            "bytes": output.stat().st_size,
        },
        "identityCounts": {
            "records": count,
            "classifications": dict(sorted(classifications.items())),
            "distinctAliases": len(alias_usage),
            "ambiguousAliases": ambiguous_aliases,
        },
        "families": family_summary,
        "safety": {
            "prices": False,
            "offers": False,
            "stock": False,
            "availability": False,
            "autoResolutionForAmbiguousAliases": False,
            "providerAttribution": "Precios Claros - Base SEPA; datos.gob.ar",
        },
    }
    manifest_output.parent.mkdir(parents=True, exist_ok=True)
    manifest_output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="qualified products.jsonl or products.jsonl.gz")
    parser.add_argument("--output", required=True, type=Path, help="derived identity JSONL or JSONL.GZ")
    parser.add_argument("--manifest", type=Path, help="manifest path (defaults to <output>.manifest.json)")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    manifest = build_identity_lexicon(args.input, args.output, args.manifest)
    print(json.dumps(manifest, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


__all__ = [
    "FAMILY_RULES",
    "POLICY_VERSION",
    "SCHEMA_VERSION",
    "build_family_clarification",
    "build_identity_lexicon",
    "derive_identity_record",
    "family_query_key",
    "family_query_form",
    "filter_family_candidates",
]

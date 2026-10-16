"""Deterministic, full-catalog identity discovery.

This module is intentionally identity-only.  It stores source product names and
evidence-backed semantic postings, but it never reads or writes prices, offers,
stock, availability, credentials, or shopping decisions.  The generated SQLite
file is a release-bound sidecar that can be opened read-only by a backend.
"""

from __future__ import annotations

import base64
import gzip
import hashlib
import json
import re
import shutil
import sqlite3
import tempfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence

from tools.consumer_input_intelligence import normalize_text
from tools.product_identity_lexicon import (
    FAMILY_RULES,
    _FORM_PRESENTATION,
    _tokens,
    derive_identity_record,
)
from tools.product_taxonomy import DEPARTMENT_RULES, classify_name


BUILD_SCHEMA_VERSION = "valuepilot-catalog-discovery-v2"
RULES_VERSION = "valuepilot-catalog-discovery-rules-v18"
MAX_QUERY_CHARS = 256
MAX_PAGE_SIZE = 50
MAX_FACET_PAGE_SIZE = 30
MAX_SELECTIONS = 8
MAX_SOURCE_ROW_BYTES = 2 * 1024 * 1024
MAX_SOURCE_ROWS = 500_000

_ID_RE = re.compile(r"[a-z][a-z0-9:_-]{0,159}\Z")
_RELATIONS = frozenset(
    {
        "IS_A",
        "BASE",
        "DECLARED_INGREDIENT",
        "FLAVOR",
        "SCENT",
        "MATERIAL",
        "THEME",
        "INTENDED_USE",
        "FORM",
        "STATE",
        "VARIANT",
        "BRAND",
        "MENTIONS",
    }
)
_RELATION_RULES_VERSION = "typed-context-v1"

# These are deliberately small parent edges.  They do not claim that every
# product containing a family word is a member of the parent; only accepted
# direct family postings receive the edge.
_FAMILY_PARENTS: Mapping[str, tuple[str, ...]] = {
    "apple": ("fruit",),
    "avocado": ("fruit",),
    "coconut": ("fruit",),
    "mango": ("fruit",),
    "orange": ("fruit",),
    "peach": ("fruit",),
    "pineapple": ("fruit",),
    "strawberry": ("fruit",),
    "tomato": ("fruit",),
}


class DiscoveryError(ValueError):
    """A malformed query, index, predicate, or source record."""


class IndexIntegrityError(DiscoveryError):
    """The sidecar could not prove its release-bound identity invariants."""


@dataclass(frozen=True)
class DiscoveryQuery:
    query: str
    language: str = "es"
    selections: tuple[str, ...] = ()
    page_size: int = 20
    cursor: str | None = None
    facet_page_size: int = MAX_FACET_PAGE_SIZE
    facet_cursor: str | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.query, str) or not self.query.strip():
            raise DiscoveryError("query must contain text")
        if len(self.query) > MAX_QUERY_CHARS:
            raise DiscoveryError("query is too long")
        if self.language not in {"es", "en"}:
            raise DiscoveryError("language must be es or en")
        if len(self.selections) > MAX_SELECTIONS:
            raise DiscoveryError("too many selections")
        if len(set(self.selections)) != len(self.selections):
            raise DiscoveryError("duplicate selection")
        for value in self.selections:
            if not isinstance(value, str) or not _ID_RE.fullmatch(value):
                raise DiscoveryError("selection predicate is invalid")
        if not 1 <= self.page_size <= MAX_PAGE_SIZE:
            raise DiscoveryError("page size is invalid")
        if not 1 <= self.facet_page_size <= MAX_FACET_PAGE_SIZE:
            raise DiscoveryError("facet page size is invalid")


@dataclass(frozen=True)
class IndexBuildReport:
    source_path: str
    source_sha256: str
    release_id: str
    provider_products: int
    identity_records: int
    indexed_tokens: int
    concepts: int
    assertions: int
    coverage: Mapping[str, int]
    bilingual: Mapping[str, int]
    output_path: str
    output_sha256: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": BUILD_SCHEMA_VERSION,
            "rulesVersion": RULES_VERSION,
            "source": {
                "path": self.source_path,
                "sha256": self.source_sha256,
                "releaseId": self.release_id,
            },
            "output": {"path": self.output_path, "sha256": self.output_sha256},
            "counts": {
                "providerProducts": self.provider_products,
                "identityRecords": self.identity_records,
                "indexedTokens": self.indexed_tokens,
                "concepts": self.concepts,
                "assertions": self.assertions,
            },
            "coverage": dict(sorted(self.coverage.items())),
            "bilingual": dict(sorted(self.bilingual.items())),
            "safety": {
                "prices": False,
                "offers": False,
                "stock": False,
                "availability": False,
                "credentials": False,
                "everyIdentityHasLiteralPosting": self.identity_records > 0,
            },
        }


@dataclass(frozen=True)
class DiscoveryResponse:
    value: Mapping[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return dict(self.value)


def _open_jsonl(path: Path):
    if path.suffix.lower() == ".gz":
        return gzip.open(path, "rb")
    return path.open("rb")


def _iter_rows(path: Path) -> Iterator[Mapping[str, Any]]:
    count = 0
    with _open_jsonl(path) as handle:
        for line_number, line in enumerate(handle, 1):
            if len(line) > MAX_SOURCE_ROW_BYTES:
                raise IndexIntegrityError(f"source row {line_number} is too large")
            if not line.strip():
                continue
            count += 1
            if count > MAX_SOURCE_ROWS:
                raise IndexIntegrityError("source row limit exceeded")
            try:
                value = json.loads(line.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise IndexIntegrityError(f"source row {line_number} is invalid JSON") from exc
            if not isinstance(value, Mapping):
                raise IndexIntegrityError(f"source row {line_number} is not an object")
            yield value


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _canonical(value: Mapping[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _fingerprint(value: Mapping[str, Any]) -> str:
    return hashlib.sha256(_canonical(value).encode("utf-8")).hexdigest()


def _norm(value: str) -> str:
    try:
        return normalize_text(value, max_length=MAX_QUERY_CHARS)
    except Exception as exc:  # normalize_text has a domain-specific exception
        raise DiscoveryError("text cannot be normalized") from exc


def _tokens(value: str) -> tuple[str, ...]:
    return tuple(token for token in _norm(value).split() if len(token) > 1)


def _index_tokens(value: str) -> tuple[str, ...]:
    """Keep every normalized name token, including one-letter source tokens."""

    return tuple(token for token in _norm(value).split() if token)


def _safe_id(value: str, *, label: str) -> str:
    if not isinstance(value, str) or not _ID_RE.fullmatch(value):
        raise DiscoveryError(f"{label} is invalid")
    return value


def _labelize(value: str) -> str:
    return " ".join(part.capitalize() for part in value.replace("_", " ").split())


def _family_labels() -> dict[str, tuple[str, str]]:
    labels: dict[str, tuple[str, str]] = {}
    for rule in FAMILY_RULES:
        spanish = rule.spanish_aliases[0] if rule.spanish_aliases else rule.key
        english = rule.english_aliases[0] if rule.english_aliases else rule.key
        labels[rule.key] = (
            spanish.capitalize(),
            english.capitalize(),
        )
    labels["fruit"] = ("Fruta", "Fruit")
    return labels


def _form_label(family: str, form: str, language: str) -> str:
    presentation = _FORM_PRESENTATION.get(family, {}).get(form)
    if presentation:
        return presentation["labelEs" if language == "es" else "labelEn"]
    family_rule = next((item for item in FAMILY_RULES if item.key == family), None)
    if family_rule is not None:
        family_aliases = family_rule.spanish_aliases if language == "es" else family_rule.english_aliases
        form_terms = family_rule.forms.get(form, ((), ()))[0 if language == "es" else 1]
        if family_aliases and form_terms:
            return f"{family_aliases[0].capitalize()} {form_terms[0]}"
    return f"{_labelize(family)} {_labelize(form)}"


def _department_labels() -> dict[str, tuple[str, str]]:
    return {rule.key: (rule.label_es, rule.label_en) for rule in DEPARTMENT_RULES}


def _schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        PRAGMA foreign_keys = ON;
        PRAGMA journal_mode = DELETE;
        CREATE TABLE build (
            schema_version TEXT NOT NULL,
            rules_version TEXT NOT NULL,
            release_id TEXT NOT NULL,
            source_sha256 TEXT NOT NULL,
            created_at TEXT NOT NULL,
            provider_products INTEGER NOT NULL,
            identity_records INTEGER NOT NULL,
            indexed_tokens INTEGER NOT NULL,
            concepts INTEGER NOT NULL,
            assertions INTEGER NOT NULL,
            PRIMARY KEY (schema_version, release_id)
        );
        CREATE TABLE identity (
            identity_key TEXT PRIMARY KEY,
            provider_key TEXT NOT NULL,
            display_name TEXT NOT NULL,
            brand TEXT,
            normalized_name TEXT NOT NULL,
            source_fingerprint TEXT NOT NULL
        );
        CREATE INDEX identity_provider_idx ON identity(provider_key);
        CREATE INDEX identity_name_idx ON identity(normalized_name, identity_key);
        CREATE TABLE product_group (
            group_id TEXT PRIMARY KEY,
            provider_key TEXT NOT NULL UNIQUE,
            equivalence_basis TEXT NOT NULL
        );
        CREATE TABLE group_member (
            group_id TEXT NOT NULL REFERENCES product_group(group_id),
            identity_key TEXT NOT NULL REFERENCES identity(identity_key),
            PRIMARY KEY (group_id, identity_key)
        );
        CREATE TABLE concept (
            concept_id TEXT PRIMARY KEY,
            kind TEXT NOT NULL,
            label_es TEXT NOT NULL,
            label_en TEXT NOT NULL,
            definition TEXT NOT NULL
        );
        CREATE TABLE parent (
            child_concept TEXT NOT NULL REFERENCES concept(concept_id),
            parent_concept TEXT NOT NULL REFERENCES concept(concept_id),
            PRIMARY KEY (child_concept, parent_concept),
            CHECK (child_concept <> parent_concept)
        );
        CREATE TABLE evidence (
            evidence_id TEXT PRIMARY KEY,
            identity_key TEXT NOT NULL REFERENCES identity(identity_key),
            source_field TEXT NOT NULL,
            original_span TEXT NOT NULL,
            source_fingerprint TEXT NOT NULL
        );
        CREATE TABLE assertion (
            assertion_id TEXT PRIMARY KEY,
            identity_key TEXT NOT NULL REFERENCES identity(identity_key),
            component_id TEXT NOT NULL,
            relation TEXT NOT NULL,
            concept_id TEXT NOT NULL REFERENCES concept(concept_id),
            evidence_id TEXT NOT NULL REFERENCES evidence(evidence_id),
            rule_id TEXT NOT NULL,
            state TEXT NOT NULL,
            matched_span TEXT NOT NULL
        );
        CREATE INDEX assertion_identity_idx ON assertion(identity_key);
        CREATE INDEX assertion_concept_idx ON assertion(concept_id, relation);
        CREATE TABLE posting (
            predicate_id TEXT NOT NULL,
            relation TEXT NOT NULL,
            concept_id TEXT NOT NULL REFERENCES concept(concept_id),
            identity_key TEXT NOT NULL REFERENCES identity(identity_key),
            PRIMARY KEY (predicate_id, identity_key)
        );
        CREATE INDEX posting_predicate_idx ON posting(predicate_id, identity_key);
        CREATE INDEX posting_identity_idx ON posting(identity_key, predicate_id);
        CREATE TABLE alias (
            phrase_norm TEXT NOT NULL,
            language TEXT NOT NULL,
            predicate_id TEXT NOT NULL,
            concept_id TEXT NOT NULL REFERENCES concept(concept_id),
            relation TEXT NOT NULL,
            alias_kind TEXT NOT NULL,
            evidence_id TEXT,
            PRIMARY KEY (phrase_norm, language, predicate_id)
        );
        CREATE INDEX alias_phrase_idx ON alias(phrase_norm, language);
        CREATE TABLE identity_alias (
            phrase_norm TEXT NOT NULL,
            language TEXT NOT NULL,
            identity_key TEXT NOT NULL REFERENCES identity(identity_key),
            provenance TEXT NOT NULL,
            PRIMARY KEY (phrase_norm, language, identity_key)
        );
        CREATE INDEX identity_alias_phrase_idx ON identity_alias(phrase_norm, language);
        CREATE TABLE identity_token (
            token TEXT NOT NULL,
            identity_key TEXT NOT NULL REFERENCES identity(identity_key),
            PRIMARY KEY (token, identity_key)
        );
        CREATE INDEX identity_token_token_idx ON identity_token(token, identity_key);
        CREATE TABLE coverage (
            identity_key TEXT PRIMARY KEY REFERENCES identity(identity_key),
            type_status TEXT NOT NULL,
            role_status TEXT NOT NULL,
            department_status TEXT NOT NULL,
            bilingual_status TEXT NOT NULL,
            reason_codes TEXT NOT NULL
        );
        CREATE TABLE build_impact (
            identity_key TEXT PRIMARY KEY REFERENCES identity(identity_key),
            record_fingerprint TEXT NOT NULL,
            rule_dependencies TEXT NOT NULL,
            assertion_digest TEXT NOT NULL
        );
        """
    )


def _ensure_concept(
    connection: sqlite3.Connection,
    concept_id: str,
    kind: str,
    label_es: str,
    label_en: str,
    definition: str,
) -> None:
    _safe_id(concept_id, label="concept id")
    connection.execute(
        "INSERT OR IGNORE INTO concept(concept_id, kind, label_es, label_en, definition) VALUES (?, ?, ?, ?, ?)",
        (concept_id, kind, label_es.strip(), label_en.strip(), definition.strip()),
    )


def _ensure_alias(
    connection: sqlite3.Connection,
    phrase: str,
    language: str,
    predicate_id: str,
    concept_id: str,
    relation: str,
    alias_kind: str,
    evidence_id: str | None = None,
) -> None:
    phrase_norm = _norm(phrase)
    if not phrase_norm or language not in {"es", "en"}:
        return
    connection.execute(
        "INSERT OR IGNORE INTO alias(phrase_norm, language, predicate_id, concept_id, relation, alias_kind, evidence_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (phrase_norm, language, predicate_id, concept_id, relation, alias_kind, evidence_id),
    )


def _seed_dictionary(connection: sqlite3.Connection) -> None:
    family_labels = _family_labels()
    for family, (label_es, label_en) in family_labels.items():
        concept_id = f"type:{family}"
        _ensure_concept(connection, concept_id, "TYPE", label_es, label_en, f"Accepted product family: {family}.")
        aliases: tuple[str, ...] = ()
        rule = next((item for item in FAMILY_RULES if item.key == family), None)
        if rule is not None:
            aliases = tuple(dict.fromkeys(rule.spanish_aliases + rule.english_aliases))
        else:
            aliases = (family, "fruta", "fruit") if family == "fruit" else (family,)
        if rule is not None:
            for alias in rule.spanish_aliases:
                _ensure_alias(connection, alias, "es", concept_id, concept_id, "IS_A", "CURATED_DICTIONARY")
            for alias in rule.english_aliases:
                _ensure_alias(connection, alias, "en", concept_id, concept_id, "IS_A", "CURATED_DICTIONARY")
        else:
            for alias in aliases:
                _ensure_alias(connection, alias, "es", concept_id, concept_id, "IS_A", "CURATED_DICTIONARY")

    for family, parents in _FAMILY_PARENTS.items():
        for parent in parents:
            parent_id = f"type:{parent}"
            if parent_id not in {row[0] for row in connection.execute("SELECT concept_id FROM concept")}:  # tiny seed set
                labels = family_labels.get(parent, (_labelize(parent), _labelize(parent)))
                _ensure_concept(connection, parent_id, "TYPE", labels[0], labels[1], f"Parent type: {parent}.")
            connection.execute("INSERT OR IGNORE INTO parent(child_concept, parent_concept) VALUES (?, ?)", (f"type:{family}", parent_id))
            for language, value in (("es", _family_labels()[parent][0]), ("en", _family_labels()[parent][1])):
                _ensure_alias(connection, value, language, parent_id, parent_id, "IS_A", "CURATED_DICTIONARY")

    for family, forms in _FORM_PRESENTATION.items():
        family_rule = next((item for item in FAMILY_RULES if item.key == family), None)
        family_aliases = tuple(dict.fromkeys((family_rule.spanish_aliases + family_rule.english_aliases) if family_rule else (family,)))
        form_rules = {rule.key: rule for rule in __import__("tools.product_taxonomy", fromlist=["FORM_RULES"]).FORM_RULES}
        for form, presentation in forms.items():
            concept_id = f"form:{family}:{form}"
            _ensure_concept(connection, concept_id, "FORM", presentation["labelEs"], presentation["labelEn"], f"Observed presentation {form} of {family}.")
            _ensure_alias(connection, presentation["labelEs"], "es", concept_id, concept_id, "FORM", "CURATED_DICTIONARY")
            _ensure_alias(connection, presentation["labelEn"], "en", concept_id, concept_id, "FORM", "CURATED_DICTIONARY")
            _ensure_alias(connection, presentation["query"], "es", concept_id, concept_id, "FORM", "CURATED_DICTIONARY")
            form_rule = form_rules.get(form)
            form_terms = tuple(form_rule.terms) if form_rule else ()
            for family_alias in family_aliases:
                language = "en" if family_rule and family_alias in family_rule.english_aliases else "es"
                for term in tuple(dict.fromkeys(form_terms + (form,))):
                    _ensure_alias(connection, f"{family_alias} {term}", language, concept_id, concept_id, "FORM", "CURATED_COMPOSITION")

    # Generic JSON rules can carry observed forms even when they do not have a
    # hand-authored presentation block above. Seed their composed ES/EN aliases
    # so an explicit query such as ``sal fina`` resolves to the form predicate
    # instead of falling back to a broad family plus an unrelated literal token.
    for family_rule in FAMILY_RULES:
        if not family_rule.forms:
            continue
        for form, (spanish_terms, english_terms) in family_rule.forms.items():
            concept_id = f"form:{family_rule.key}:{form}"
            _ensure_concept(
                connection,
                concept_id,
                "FORM",
                _form_label(family_rule.key, form, "es"),
                _form_label(family_rule.key, form, "en"),
                f"Observed form {form} of {family_rule.key}.",
            )
            for family_alias in family_rule.spanish_aliases:
                for term in tuple(dict.fromkeys(spanish_terms + (form,))):
                    _ensure_alias(connection, f"{family_alias} {term}", "es", concept_id, concept_id, "FORM", "CURATED_COMPOSITION")
            for family_alias in family_rule.english_aliases:
                for term in tuple(dict.fromkeys(english_terms + (form,))):
                    _ensure_alias(connection, f"{family_alias} {term}", "en", concept_id, concept_id, "FORM", "CURATED_COMPOSITION")

    for rule in DEPARTMENT_RULES:
        concept_id = f"department:{rule.key}"
        _ensure_concept(connection, concept_id, "DEPARTMENT", rule.label_es, rule.label_en, f"Observed department {rule.key}.")
        _ensure_alias(connection, rule.label_es, "es", concept_id, concept_id, "IS_A", "CURATED_DICTIONARY")
        _ensure_alias(connection, rule.label_en, "en", concept_id, concept_id, "IS_A", "CURATED_DICTIONARY")
        for term in rule.terms:
            language = "en" if re.search(r"[a-z]", term) and not re.search(r"[áéíóúñ]", term) and term.casefold() in {"cleaning", "food", "beverages", "dairy", "electronics", "toys", "tools", "sports", "clothing", "health", "personal care", "home goods"} else "es"
            _ensure_alias(connection, term, language, concept_id, concept_id, "IS_A", "CURATED_RULE")


def _descendant_concepts(connection: sqlite3.Connection, concept_id: str) -> tuple[str, ...]:
    values = {concept_id}
    frontier = [concept_id]
    while frontier:
        child = frontier.pop()
        for (candidate,) in connection.execute("SELECT child_concept FROM parent WHERE parent_concept = ?", (child,)):
            if candidate not in values:
                values.add(candidate)
                frontier.append(candidate)
    return tuple(sorted(values))


def _predicate_identity_keys(connection: sqlite3.Connection, predicate_id: str) -> set[str]:
    rows = connection.execute("SELECT concept_id FROM concept WHERE concept_id = ?", (predicate_id,)).fetchall()
    if not rows:
        raise DiscoveryError(f"unknown predicate: {predicate_id}")
    concepts = _descendant_concepts(connection, predicate_id) if predicate_id.startswith("type:") else (predicate_id,)
    placeholders = ",".join("?" for _ in concepts)
    return {
        value[0]
        for value in connection.execute(
            f"SELECT DISTINCT identity_key FROM posting WHERE predicate_id IN ({placeholders})",
            tuple(concepts),
        )
    }


def _broad_predicate_identity_keys(connection: sqlite3.Connection, predicate_id: str) -> set[str]:
    """Return accepted postings plus literal semantic mentions for clarification.

    ``MENTIONS`` is intentionally broad-query context only.  Explicit choices
    use ``_predicate_identity_keys`` and therefore never turn an unresolved
    mention into an accepted product type.
    """

    rows = connection.execute("SELECT concept_id FROM concept WHERE concept_id = ?", (predicate_id,)).fetchall()
    if not rows:
        raise DiscoveryError(f"unknown predicate: {predicate_id}")
    concepts = _descendant_concepts(connection, predicate_id) if predicate_id.startswith("type:") else (predicate_id,)
    placeholders = ",".join("?" for _ in concepts)
    return {
        value[0]
        for value in connection.execute(
            f"SELECT DISTINCT identity_key FROM posting WHERE concept_id IN ({placeholders})",
            tuple(concepts),
        )
    }


def _literal_identity_keys(connection: sqlite3.Connection, tokens: Sequence[str]) -> set[str]:
    if not tokens:
        return set()
    values: set[str] | None = None
    for token in dict.fromkeys(tokens):
        current = {row[0] for row in connection.execute("SELECT identity_key FROM identity_token WHERE token = ?", (token,))}
        values = current if values is None else values & current
        if not values:
            return set()
    return values or set()


def _insert_posting(connection: sqlite3.Connection, predicate_id: str, relation: str, concept_id: str, identity_key: str) -> None:
    if relation not in _RELATIONS:
        raise IndexIntegrityError(f"unsupported relation: {relation}")
    connection.execute(
        "INSERT OR IGNORE INTO posting(predicate_id, relation, concept_id, identity_key) VALUES (?, ?, ?, ?)",
        (predicate_id, relation, concept_id, identity_key),
    )


def _insert_assertion(
    connection: sqlite3.Connection,
    identity_key: str,
    relation: str,
    concept_id: str,
    evidence_id: str,
    rule_id: str,
    state: str,
    matched_span: str,
) -> str:
    assertion_id = hashlib.sha256(f"{identity_key}|{relation}|{concept_id}|{evidence_id}|{rule_id}".encode()).hexdigest()
    connection.execute(
        "INSERT OR IGNORE INTO assertion(assertion_id, identity_key, component_id, relation, concept_id, evidence_id, rule_id, state, matched_span) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (assertion_id, identity_key, "product", relation, concept_id, evidence_id, rule_id, state, matched_span[:MAX_QUERY_CHARS]),
    )
    return assertion_id


def _source_aliases(row: Mapping[str, Any]) -> Iterator[tuple[str, str, str]]:
    aliases = row.get("aliases")
    if not isinstance(aliases, Sequence) or isinstance(aliases, (str, bytes)):
        return
    for alias in aliases:
        if not isinstance(alias, Mapping):
            continue
        value = alias.get("value")
        language = alias.get("language")
        provenance = alias.get("provenance")
        if isinstance(value, str) and isinstance(language, str) and isinstance(provenance, str) and language in {"es", "en"}:
            yield value, language, provenance


def _typed_context_relations(name: str, family: str, role: str) -> tuple[tuple[str, str], ...]:
    """Extract only explicit, role-scoped context relations from a name.

    These patterns are deliberately conservative.  A literal family mention
    remains ``MENTIONS``; a typed relation is created only when the source
    phrase supplies an unambiguous cue such as ``sabor`` or ``con``.
    """

    normalized = _norm(name)
    rule = next((item for item in FAMILY_RULES if item.key == family), None)
    aliases = tuple(rule.spanish_aliases + rule.english_aliases) if rule is not None else (family,)
    aliases = tuple(alias for alias in aliases if alias and len(alias) > 1)
    if not aliases:
        return ()
    family_pattern = "(?:" + "|".join(re.escape(_norm(alias)) for alias in aliases) + ")"
    relations: list[tuple[str, str]] = []
    if role != "DIRECT" and re.search(rf"\b(?:de|del|con|with|of)\s+{family_pattern}\b", normalized):
        relations.append(("BASE", _RELATION_RULES_VERSION))
    if re.search(rf"\b(?:sabor|sabor\s+a|flavor|flavored|gusto)\s+{family_pattern}\b", normalized):
        relations.append(("FLAVOR", _RELATION_RULES_VERSION))
    if re.search(rf"\b(?:aroma|fragancia|perfume|scent|fragrance)\s+{family_pattern}\b", normalized):
        relations.append(("SCENT", _RELATION_RULES_VERSION))
    if re.search(rf"\b(?:con|contiene|ingrediente|with|contains)\s+{family_pattern}\b", normalized):
        relations.append(("DECLARED_INGREDIENT", _RELATION_RULES_VERSION))
    if re.search(r"\b(?:juguete|juguetes|toy|figure|figura|theme)\b", normalized):
        relations.append(("THEME", _RELATION_RULES_VERSION))
    return tuple(dict.fromkeys(relations))


def _family_candidates(row: Mapping[str, Any], identity_key: str) -> list[Mapping[str, Any]]:
    families = row.get("familyCandidates")
    provided: list[Mapping[str, Any]] = []
    if isinstance(families, Sequence) and not isinstance(families, (str, bytes)):
        provided = [value for value in families if isinstance(value, Mapping)]
    try:
        derived = derive_identity_record({
            "productEvidenceKey": identity_key,
            "name": row.get("name"),
            "brand": row.get("brand"),
            "gtin": row.get("gtin"),
            "quantity": row.get("quantity"),
        })
    except (TypeError, ValueError):
        return provided
    # Re-run the current dictionary even when the source already contains an
    # older family list.  Otherwise a new reviewed rule can never reach rows
    # whose previous lexicon happened to contain a different family.
    merged: dict[tuple[str, str, tuple[str, ...], tuple[str, ...]], Mapping[str, Any]] = {}
    provided_roles = {
        str(value.get("family")).strip(): str(value.get("role")).strip()
        for value in provided
        if isinstance(value.get("family"), str) and isinstance(value.get("role"), str)
    }
    derived_values = tuple(value for value in derived.get("familyCandidates", ()) if isinstance(value, Mapping))
    derived_roles = {
        str(value.get("family")).strip(): str(value.get("role")).strip()
        for value in derived_values
        if isinstance(value.get("family"), str) and isinstance(value.get("role"), str)
    }
    normalized_tokens = _tokens(str(row.get("name") or ""))
    negative_families = {
        rule.key
        for rule in FAMILY_RULES
        if any(
            phrase
            and any(
                normalized_tokens[index : index + len(phrase)] == phrase
                for index in range(len(normalized_tokens) - len(phrase) + 1)
            )
            for phrase in rule.excluded_phrases
        )
    }
    for source_name, values in (("provided", provided), ("derived", derived_values)):
        for value in values:
            if not isinstance(value, Mapping):
                continue
            family = str(value.get("family") or "").strip()
            role = str(value.get("role") or "").strip()
            # A source release may carry a reviewed derived/context assertion
            # that is safer than a bare token match from the newer dictionary.
            # Preserve that negative context instead of turning a shampoo or
            # drink mention into the food family during a refresh.
            if source_name == "derived" and role == "DIRECT" and provided_roles.get(family) == "DERIVED":
                continue
            # The source identity release can also contain an older DIRECT
            # assertion for a family whose current reviewed dictionary now
            # recognizes an explicit negative context (for example an envase
            # de gaseosa or a grocery malla). Keep current evidence in charge
            # so stale direct postings cannot leak into a type query.
            if source_name == "provided" and role == "DIRECT" and derived_roles.get(family) == "DERIVED":
                continue
            # A source release may contain a stale direct assertion without a
            # current include term (for example a pet-food row with only the
            # generic word ``carne``).  Current reviewed negative context must
            # still suppress that assertion so a bare family query cannot leak
            # the row into the direct result set.
            if source_name == "provided" and role == "DIRECT" and family in negative_families:
                continue
            terms = tuple(sorted(str(item) for item in value.get("matchedTerms", ()) if str(item).strip()))
            forms = tuple(sorted(str(item) for item in value.get("observedForms", ()) if str(item).strip()))
            if family and role:
                merged[(family, role, terms, forms)] = value
    return list(merged.values())


def _insert_identity(
    connection: sqlite3.Connection,
    row: Mapping[str, Any],
    coverage_counts: Counter[str],
    bilingual_counts: Counter[str],
) -> tuple[str, str, int, set[str]]:
    identity_key = row.get("productEvidenceKey")
    provider_key = row.get("providerProductId")
    name = row.get("name")
    if not isinstance(identity_key, str) or not identity_key.strip():
        raise IndexIntegrityError("identity is missing productEvidenceKey")
    if not isinstance(provider_key, str) or not provider_key.strip():
        raise IndexIntegrityError(f"identity {identity_key} is missing providerProductId")
    if not isinstance(name, str) or not name.strip():
        raise IndexIntegrityError(f"identity {identity_key} is missing name")
    identity_key = identity_key.strip()
    provider_key = provider_key.strip()
    name = " ".join(name.split())
    normalized_name = _norm(name)
    fingerprint = _fingerprint(row)
    prior = connection.execute("SELECT source_fingerprint FROM identity WHERE identity_key = ?", (identity_key,)).fetchone()
    if prior is not None and prior[0] != fingerprint:
        raise IndexIntegrityError(f"duplicate identity key has conflicting source data: {identity_key}")
    connection.execute(
        "INSERT OR IGNORE INTO identity(identity_key, provider_key, display_name, brand, normalized_name, source_fingerprint) VALUES (?, ?, ?, ?, ?, ?)",
        (identity_key, provider_key, name, row.get("brand") if isinstance(row.get("brand"), str) else None, normalized_name, fingerprint),
    )
    group_id = f"provider:{provider_key}"
    connection.execute("INSERT OR IGNORE INTO product_group(group_id, provider_key, equivalence_basis) VALUES (?, ?, ?)", (group_id, provider_key, "QUALIFIED_PROVIDER_PRODUCT_ID"))
    connection.execute("INSERT OR IGNORE INTO group_member(group_id, identity_key) VALUES (?, ?)", (group_id, identity_key))
    name_evidence_id = f"evidence:{identity_key}:name"
    connection.execute(
        "INSERT OR IGNORE INTO evidence(evidence_id, identity_key, source_field, original_span, source_fingerprint) VALUES (?, ?, ?, ?, ?)",
        (name_evidence_id, identity_key, "name", name, fingerprint),
    )
    token_values = set(_index_tokens(name))
    for token in token_values:
        connection.execute("INSERT OR IGNORE INTO identity_token(token, identity_key) VALUES (?, ?)", (token, identity_key))
    for value, language, provenance in _source_aliases(row):
        connection.execute(
            "INSERT OR IGNORE INTO identity_alias(phrase_norm, language, identity_key, provenance) VALUES (?, ?, ?, ?)",
            (_norm(value), language, identity_key, provenance),
        )

    families = _family_candidates(row, identity_key)
    semantic_assertions: list[str] = []
    direct_families = 0
    derived_families = 0
    family_keys: set[str] = set()
    family_roles: dict[str, str] = {}
    for family in families:
        key = family.get("family")
        if not isinstance(key, str) or not key.strip():
            continue
        key = key.strip()
        family_keys.add(key)
        role = "DIRECT" if family.get("role") == "DIRECT" else "DERIVED"
        family_roles[key] = role
        relation = "IS_A" if role == "DIRECT" else "MENTIONS"
        concept_id = f"type:{key}"
        labels = _family_labels().get(key, (_labelize(key), _labelize(key)))
        _ensure_concept(connection, concept_id, "TYPE", labels[0], labels[1], f"Observed family {key}.")
        posting_id = concept_id if relation == "IS_A" else f"mentions:{concept_id}"
        _insert_posting(connection, posting_id, relation, concept_id, identity_key)
        matched = " ".join(str(value) for value in family.get("matchedTerms", ()) if str(value).strip())
        assertion_id = _insert_assertion(connection, identity_key, relation, concept_id, name_evidence_id, "family-lexicon-v1", "ACCEPTED", matched or name)
        semantic_assertions.append(assertion_id)
        direct_families += role == "DIRECT"
        derived_families += role == "DERIVED"
        for form in family.get("observedForms", ()) if isinstance(family.get("observedForms"), Sequence) else ():
            if not isinstance(form, str) or not form.strip() or form == "unspecified":
                continue
            form_id = f"form:{key}:{form}"
            _ensure_concept(connection, form_id, "FORM", _form_label(key, form, "es"), _form_label(key, form, "en"), f"Observed form {form} for {key}.")
            _insert_posting(connection, form_id, "FORM", form_id, identity_key)
            semantic_assertions.append(_insert_assertion(connection, identity_key, "FORM", form_id, name_evidence_id, "family-form-v1", "ACCEPTED", matched or name))
        for relation, rule_id in _typed_context_relations(name, key, role):
            relation_predicate = f"relation:{relation}:type:{key}"
            _insert_posting(connection, relation_predicate, relation, concept_id, identity_key)
            semantic_assertions.append(_insert_assertion(connection, identity_key, relation, concept_id, name_evidence_id, rule_id, "ACCEPTED", matched or name))

    family_payload = [dict(value) for value in families]
    classified = classify_name(name, family_candidates=family_payload)
    for department in classified.get("departmentCandidates", ()):
        if not isinstance(department, Mapping):
            continue
        key = department.get("key")
        if not isinstance(key, str) or not key.strip():
            continue
        concept_id = f"department:{key}"
        labels = _department_labels().get(key, (_labelize(key), _labelize(key)))
        _ensure_concept(connection, concept_id, "DEPARTMENT", labels[0], labels[1], f"Observed department {key}.")
        _insert_posting(connection, concept_id, "IS_A", concept_id, identity_key)
        semantic_assertions.append(_insert_assertion(connection, identity_key, "IS_A", concept_id, name_evidence_id, "department-rules-v1", "ACCEPTED", " ".join(department.get("matchedTerms", ())) or name))

    department_keys = {
        str(value.get("key")).strip()
        for value in classified.get("departmentCandidates", ())
        if isinstance(value, Mapping) and isinstance(value.get("key"), str) and str(value.get("key")).strip()
    }
    if direct_families:
        coverage_counts["TYPE_RESOLVED"] += 1
    else:
        coverage_counts["TYPE_UNRESOLVED"] += 1
    coverage_counts["DEPARTMENT_RESOLVED"] += int(bool(department_keys))
    coverage_counts["TYPE_OR_DEPARTMENT_RESOLVED"] += int(bool(direct_families or department_keys))
    coverage_counts["ROLE_RESOLVED"] += int(bool(direct_families or derived_families))
    coverage_counts["IDENTITY_VISITED"] += 1
    coverage_counts["DERIVED_ONLY"] += int(not direct_families and bool(derived_families))
    coverage_counts["NO_FORM"] += int(not any(value.startswith("form:") for value in [row[0] for row in connection.execute("SELECT predicate_id FROM posting WHERE identity_key = ?", (identity_key,))]))
    has_es = bool(connection.execute("SELECT 1 FROM identity_alias WHERE identity_key = ? AND language = 'es' LIMIT 1", (identity_key,)).fetchone()) or bool(family_keys) or bool(department_keys)
    has_en = bool(connection.execute("SELECT 1 FROM identity_alias WHERE identity_key = ? AND language = 'en' LIMIT 1", (identity_key,)).fetchone()) or bool(family_keys) or bool(department_keys)
    bilingual_counts["ES_AVAILABLE"] += int(has_es)
    bilingual_counts["EN_AVAILABLE"] += int(has_en)
    bilingual_counts["MISSING_ES"] += int(not has_es)
    bilingual_counts["MISSING_EN"] += int(not has_en)
    role_status = "RESOLVED" if direct_families or derived_families else "UNRESOLVED"
    department_status = "RESOLVED" if department_keys else "UNRESOLVED"
    reasons = []
    if not direct_families and not derived_families:
        reasons.append("NO_ACCEPTED_TYPE")
    if not department_keys:
        reasons.append("NO_ACCEPTED_DEPARTMENT")
    if derived_families and not direct_families:
        reasons.append("DERIVED_ONLY")
    if not has_es:
        reasons.append("NO_ES_QUERY_PATH")
    if not has_en:
        reasons.append("NO_EN_QUERY_PATH")
    if not semantic_assertions:
        reasons.append("LITERAL_ONLY")
    connection.execute(
        "INSERT OR REPLACE INTO coverage(identity_key, type_status, role_status, department_status, bilingual_status, reason_codes) VALUES (?, ?, ?, ?, ?, ?)",
        (identity_key, "RESOLVED" if direct_families else "UNRESOLVED", role_status, department_status, "COMPLETE" if has_es and has_en else "PARTIAL", json.dumps(sorted(reasons), separators=(",", ":"))),
    )
    assertion_digest = hashlib.sha256("|".join(sorted(semantic_assertions)).encode()).hexdigest()
    connection.execute(
        "INSERT OR REPLACE INTO build_impact(identity_key, record_fingerprint, rule_dependencies, assertion_digest) VALUES (?, ?, ?, ?)",
        (identity_key, fingerprint, json.dumps(["family-lexicon-v1", "family-form-v1", "department-rules-v1", _RELATION_RULES_VERSION], separators=(",", ":")), assertion_digest),
    )
    return identity_key, provider_key, len(semantic_assertions), token_values


def build_discovery_index(
    source_path: Path | str,
    output_path: Path | str,
    *,
    release_id: str = "local",
    source_sha256: str | None = None,
    report_path: Path | str | None = None,
) -> IndexBuildReport:
    """Build a fresh release-bound index from every source identity row."""

    source = Path(source_path).resolve()
    output = Path(output_path).resolve()
    if not source.is_file():
        raise DiscoveryError(f"identity source does not exist: {source}")
    actual_source_sha = _sha256(source)
    if source_sha256 is not None and source_sha256.lower() != actual_source_sha:
        raise IndexIntegrityError("identity source checksum mismatch")
    output.parent.mkdir(parents=True, exist_ok=True)
    temp_dir = Path(tempfile.mkdtemp(prefix="valuepilot-discovery-", dir=str(output.parent)))
    temp_output = temp_dir / output.name
    coverage_counts: Counter[str] = Counter()
    bilingual_counts: Counter[str] = Counter()
    token_values: set[str] = set()
    identity_count = 0
    provider_values: set[str] = set()
    assertion_count = 0
    connection: sqlite3.Connection | None = None
    try:
        connection = sqlite3.connect(temp_output)
        connection.row_factory = sqlite3.Row
        _schema(connection)
        _seed_dictionary(connection)
        for row in _iter_rows(source):
            identity_key, provider_key, assertions, tokens = _insert_identity(connection, row, coverage_counts, bilingual_counts)
            identity_count += 1
            provider_values.add(provider_key)
            assertion_count += assertions
            token_values.update(tokens)
        concept_count = int(connection.execute("SELECT COUNT(*) FROM concept").fetchone()[0])
        connection.execute(
            "INSERT INTO build(schema_version, rules_version, release_id, source_sha256, created_at, provider_products, identity_records, indexed_tokens, concepts, assertions) VALUES (?, ?, ?, ?, datetime('now'), ?, ?, ?, ?, ?)",
            (BUILD_SCHEMA_VERSION, RULES_VERSION, release_id, actual_source_sha, len(provider_values), identity_count, len(token_values), concept_count, assertion_count),
        )
        connection.commit()
        # Integrity checks run before the file becomes visible as a release artifact.
        checks = {
            "identity": connection.execute("SELECT COUNT(*) FROM identity").fetchone()[0],
            "coverage": connection.execute("SELECT COUNT(*) FROM coverage").fetchone()[0],
            "groupMembers": connection.execute("SELECT COUNT(*) FROM group_member").fetchone()[0],
            "tokens": connection.execute("SELECT COUNT(DISTINCT token) FROM identity_token").fetchone()[0],
            "literalIdentities": connection.execute("SELECT COUNT(DISTINCT identity_key) FROM identity_token").fetchone()[0],
        }
        if (
            checks["identity"] != identity_count
            or checks["coverage"] != identity_count
            or checks["groupMembers"] != identity_count
            or checks["literalIdentities"] != identity_count
        ):
            raise IndexIntegrityError("not every source identity received a sidecar record")
        connection.close()
        connection = None
        if output.exists():
            output.unlink()
        shutil.move(str(temp_output), str(output))
    finally:
        if connection is not None:
            connection.close()
        shutil.rmtree(temp_dir, ignore_errors=True)
    output_sha = _sha256(output)
    report = IndexBuildReport(
        source_path=str(source),
        source_sha256=actual_source_sha,
        release_id=release_id,
        provider_products=len(provider_values),
        identity_records=identity_count,
        indexed_tokens=len(token_values),
        concepts=concept_count,
        assertions=assertion_count,
        coverage=dict(coverage_counts),
        bilingual=dict(bilingual_counts),
        output_path=str(output),
        output_sha256=output_sha,
    )
    if report_path is not None:
        report_file = Path(report_path).resolve()
        report_file.parent.mkdir(parents=True, exist_ok=True)
        report_file.write_text(json.dumps(report.to_dict(), ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


def refresh_discovery_index(*args: Any, **kwargs: Any) -> IndexBuildReport:
    """Rebuild atomically; incremental parity is verified by comparing reports."""

    return build_discovery_index(*args, **kwargs)


class DiscoveryIndex:
    """Read-only query facade over a qualified discovery sidecar."""

    def __init__(self, path: Path | str, *, expected_source_sha256: str | None = None, expected_release_id: str | None = None):
        self.path = Path(path).resolve()
        if not self.path.is_file():
            raise DiscoveryError(f"discovery index does not exist: {self.path}")
        # FastAPI/TestClient may evaluate a request on a worker thread while
        # the service owns the read-only sidecar.  The facade serializes its
        # own use through SQLite's read-only connection and never mutates it.
        self.connection = sqlite3.connect(f"file:{self.path}?mode=ro", uri=True, check_same_thread=False)
        self.connection.row_factory = sqlite3.Row
        row = self.connection.execute("SELECT * FROM build LIMIT 1").fetchone()
        if row is None or row["schema_version"] != BUILD_SCHEMA_VERSION:
            self.close()
            raise IndexIntegrityError("discovery index schema is unsupported")
        if expected_source_sha256 is not None and row["source_sha256"] != expected_source_sha256.lower():
            self.close()
            raise IndexIntegrityError("discovery index source checksum mismatch")
        if expected_release_id is not None and row["release_id"] != expected_release_id:
            self.close()
            raise IndexIntegrityError("discovery index release mismatch")
        self.metadata = dict(row)

    def close(self) -> None:
        connection = getattr(self, "connection", None)
        if connection is not None:
            connection.close()

    def __enter__(self) -> "DiscoveryIndex":
        return self

    def __exit__(self, _type: Any, _value: Any, _traceback: Any) -> None:
        self.close()

    def _aliases(self, phrase: str, language: str) -> tuple[sqlite3.Row, ...]:
        value = self.connection.execute("SELECT * FROM alias WHERE phrase_norm = ? AND language = ? ORDER BY predicate_id", (_norm(phrase), language)).fetchall()
        if value:
            return tuple(value)
        # Cross-language fallback is explicit and deterministic: a query in one
        # language may use the other reviewed alias but never an arbitrary guess.
        return tuple(self.connection.execute("SELECT * FROM alias WHERE phrase_norm = ? ORDER BY predicate_id", (_norm(phrase),)).fetchall())

    def _resolve(self, query: str, language: str) -> tuple[tuple[str, ...], tuple[str, ...]]:
        normalized = _norm(query)
        aliases = self._aliases(normalized, language)
        if aliases:
            resolved = tuple(dict.fromkeys(row["predicate_id"] for row in aliases))
            # A precise accepted type is the stable meaning for a bare family
            # word.  Generic department aliases and an unspecified form are
            # navigation metadata, not additional constraints; retaining them
            # would make equivalent ES/EN aliases resolve to different sets.
            type_ids = tuple(value for value in resolved if value.startswith("type:"))
            if type_ids:
                resolved = type_ids + tuple(
                    value
                    for value in resolved
                    if not value.startswith("department:") and not value.endswith(":unspecified") and value not in type_ids
                )
            return resolved, ()
        tokens = _tokens(normalized)
        # Longest reviewed aliases can resolve a phrase with a harmless package
        # suffix; the remaining tokens still constrain the literal set.
        predicates: list[str] = []
        consumed: list[str] = []
        for size in range(min(5, len(tokens)), 0, -1):
            for start in range(0, len(tokens) - size + 1):
                phrase = " ".join(tokens[start:start + size])
                candidates = self._aliases(phrase, language)
                if not candidates:
                    continue
                predicates.extend(row["predicate_id"] for row in candidates)
                consumed.extend(tokens[start:start + size])
                break
            if predicates:
                break
        remaining = list(tokens)
        for token in consumed:
            if token in remaining:
                remaining.remove(token)
        return tuple(dict.fromkeys(predicates)), tuple(remaining)

    def _candidate_keys(self, predicates: Sequence[str], tokens: Sequence[str], selections: Sequence[str]) -> set[str]:
        sets: list[set[str]] = []
        if predicates:
            sets.append(set().union(*(_broad_predicate_identity_keys(self.connection, value) for value in predicates)))
        if tokens:
            sets.append(_literal_identity_keys(self.connection, tokens))
        for selection in selections:
            sets.append(_predicate_identity_keys(self.connection, selection))
        if not sets:
            return set()
        values = sets[0]
        for current in sets[1:]:
            values &= current
        return values

    def _predicate_row(self, predicate_id: str) -> sqlite3.Row:
        row = self.connection.execute("SELECT * FROM concept WHERE concept_id = ?", (predicate_id,)).fetchone()
        if row is None:
            raise DiscoveryError(f"unknown predicate: {predicate_id}")
        return row

    def _facet_choices(
        self,
        base_keys: set[str],
        predicates: Sequence[str],
        language: str,
        facet_page_size: int,
        facet_cursor: str | None,
        *,
        query: str,
        selections: Sequence[str],
    ) -> tuple[dict[str, Any] | None, str | None]:
        if not predicates or not base_keys:
            return None, None
        # A form-qualified query is already an explicit clarification choice.
        # Do not reopen unrelated sibling forms merely because the selected
        # result window contains products that carry more than one observed
        # family mention (for example an alfajor with both rice and peanut).
        if any(predicate.startswith("form:") for predicate in predicates):
            return None, None
        base = predicates[0]
        dimension_id = "product_type"
        candidate_predicates: set[str] = set()
        if base.startswith("type:"):
            # If the broad query contains unresolved mentions, expose the
            # accepted type as an explicit choice alongside contextual forms.
            # This gives the shopper a safe way to exclude shampoo, drinks or
            # toys that merely mention the same word.
            if _predicate_identity_keys(self.connection, base) != base_keys:
                candidate_predicates.add(base)
            children = {row[0] for row in self.connection.execute("SELECT child_concept FROM parent WHERE parent_concept = ?", (base,))}
            candidate_predicates.update(children)
            family = base.split(":", 1)[1]
            candidate_predicates.update(row[0] for row in self.connection.execute("SELECT DISTINCT predicate_id FROM posting WHERE predicate_id LIKE ?", (f"form:{family}:%",)))
            dimension_id = "product_type"
        elif base.startswith("department:"):
            candidate_predicates.update(row[0] for row in self.connection.execute("SELECT DISTINCT predicate_id FROM posting WHERE predicate_id LIKE 'type:%'"))
            dimension_id = "product_type"
        else:
            candidate_predicates.update(row[0] for row in self.connection.execute("SELECT DISTINCT predicate_id FROM posting WHERE relation = 'FORM'"))
            dimension_id = "form"
        choices: list[dict[str, Any]] = []
        for predicate in sorted(candidate_predicates):
            if predicate in predicates or predicate in selections:
                continue
            row = self._predicate_row(predicate)
            keys = _predicate_identity_keys(self.connection, predicate) & base_keys
            if not keys:
                continue
            placeholders = ",".join("?" for _ in keys)
            count = self.connection.execute(f"SELECT COUNT(DISTINCT provider_key) FROM identity WHERE identity_key IN ({placeholders})", tuple(keys)).fetchone()[0]
            choices.append({
                "id": predicate,
                "predicateId": predicate,
                "labelEs": row["label_es"],
                "labelEn": row["label_en"],
                "productCount": int(count),
                "sourceIdentityCount": len(keys),
                "countUnit": "DISTINCT_PROVIDER_PRODUCT",
                "hasChildren": bool(self.connection.execute("SELECT 1 FROM parent WHERE parent_concept = ? LIMIT 1", (predicate,)).fetchone()),
            })
        choices.sort(key=lambda item: (-item["productCount"], item["id"]))
        offset = 0
        if facet_cursor:
            cursor = self._decode_cursor(facet_cursor)
            if cursor.get("kind") != "facet" or cursor.get("fingerprint") != self._fingerprint_for(query, language, selections):
                raise DiscoveryError("facet cursor does not match this query")
            offset = int(cursor.get("offset", 0))
        page = choices[offset:offset + facet_page_size]
        next_cursor = None
        if offset + facet_page_size < len(choices):
            next_cursor = self._encode_cursor({"kind": "facet", "offset": offset + facet_page_size, "fingerprint": self._fingerprint_for(query, language, selections)})
        if len(choices) < 2:
            return None, next_cursor
        return {"dimensionId": dimension_id, "choices": page, "hasMore": next_cursor is not None, "nextCursor": next_cursor}, next_cursor

    @staticmethod
    def _encode_cursor(value: Mapping[str, Any]) -> str:
        raw = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")

    @staticmethod
    def _decode_cursor(value: str) -> Mapping[str, Any]:
        if not isinstance(value, str) or len(value) > 2048:
            raise DiscoveryError("cursor is invalid")
        try:
            raw = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
            parsed = json.loads(raw.decode("utf-8"))
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise DiscoveryError("cursor is invalid") from exc
        if not isinstance(parsed, Mapping):
            raise DiscoveryError("cursor is invalid")
        return parsed

    def _fingerprint(self, query: str, language: str, selections: Sequence[str]) -> str:
        return hashlib.sha256(
            _canonical(
                {
                    "query": _norm(query),
                    "language": language,
                    "selections": list(selections),
                    "releaseId": self.metadata["release_id"],
                    "sourceSha256": self.metadata["source_sha256"],
                    "rulesVersion": self.metadata["rules_version"],
                }
            ).encode()
        ).hexdigest()

    def _fingerprint_for(self, query: str, language: str, selections: Sequence[str]) -> str:
        return self._fingerprint(query, language, selections)

    def query(self, request: DiscoveryQuery | Mapping[str, Any]) -> DiscoveryResponse:
        if isinstance(request, Mapping):
            request = DiscoveryQuery(
                query=request.get("query", ""),
                language=request.get("language", "es"),
                selections=tuple(request.get("selections", ())),
                page_size=int(request.get("pageSize", request.get("page_size", 20))),
                cursor=request.get("cursor"),
                facet_page_size=int(request.get("facetPageSize", MAX_FACET_PAGE_SIZE)),
                facet_cursor=request.get("facetCursor"),
            )
        if not isinstance(request, DiscoveryQuery):
            raise DiscoveryError("request is invalid")
        predicates, remaining_tokens = self._resolve(request.query, request.language)
        candidates = self._candidate_keys(predicates, remaining_tokens, request.selections)
        clarification, _ = self._facet_choices(
            candidates,
            predicates,
            request.language,
            request.facet_page_size,
            request.facet_cursor,
            query=request.query,
            selections=request.selections,
        )
        # Broad type predicates include literal ``MENTIONS`` postings so a
        # clarification can explain why a word appeared. Once no facet is
        # available, there is no safe choice to separate those mentions; show
        # only accepted type postings instead of leaking contextual products
        # into the ready result table.
        if clarification is None and not request.selections:
            strict_types = tuple(value for value in predicates if value.startswith("type:"))
            if strict_types:
                candidates = set().union(*(_predicate_identity_keys(self.connection, value) for value in strict_types))
                if remaining_tokens:
                    candidates &= _literal_identity_keys(self.connection, remaining_tokens)
        fingerprint = self._fingerprint(request.query, request.language, request.selections)
        last_name = ""
        last_key = ""
        if request.cursor:
            cursor = self._decode_cursor(request.cursor)
            if cursor.get("kind") != "product" or cursor.get("fingerprint") != fingerprint:
                raise DiscoveryError("cursor does not match this query")
            last_name = str(cursor.get("lastName", ""))
            last_key = str(cursor.get("lastKey", ""))
        rows: list[sqlite3.Row] = []
        if candidates:
            placeholders = ",".join("?" for _ in candidates)
            rows = list(self.connection.execute(
                f"SELECT identity_key, provider_key, display_name, brand, normalized_name FROM identity WHERE identity_key IN ({placeholders}) AND (normalized_name > ? OR (normalized_name = ? AND identity_key > ?)) ORDER BY normalized_name, identity_key LIMIT ?",
                tuple(candidates) + (last_name, last_name, last_key, request.page_size + 1),
            ))
        has_more = len(rows) > request.page_size
        visible = rows[:request.page_size]
        next_cursor = None
        if has_more and visible:
            tail = visible[-1]
            next_cursor = self._encode_cursor({"kind": "product", "fingerprint": fingerprint, "lastName": tail["normalized_name"], "lastKey": tail["identity_key"]})
        product_count = 0
        source_identity_count = len(candidates)
        if candidates:
            placeholders = ",".join("?" for _ in candidates)
            product_count = int(self.connection.execute(f"SELECT COUNT(DISTINCT provider_key) FROM identity WHERE identity_key IN ({placeholders})", tuple(candidates)).fetchone()[0])
        status = "NEEDS_CLARIFICATION" if clarification is not None else ("READY" if candidates else "NO_MATCHES")
        matches = [
            {"identityKey": row["identity_key"], "providerProductId": row["provider_key"], "displayName": row["display_name"], "brand": row["brand"]}
            for row in visible
        ]
        value = {
            "contract": "valuepilot-discovery-v2",
            "status": status,
            "query": request.query,
            "language": request.language,
            "releaseId": self.metadata["release_id"],
            "indexVersion": self.metadata["source_sha256"],
            "queryFingerprint": fingerprint,
            "resolvedPredicates": list(predicates),
            "selections": list(request.selections),
            "total": {"productCount": product_count, "sourceIdentityCount": source_identity_count, "countUnit": "DISTINCT_PROVIDER_PRODUCT"},
            "matches": matches,
            "pagination": {"pageSize": request.page_size, "hasMore": has_more, "nextCursor": next_cursor},
            "clarification": clarification,
        }
        return DiscoveryResponse(value)


__all__ = [
    "BUILD_SCHEMA_VERSION",
    "DiscoveryError",
    "DiscoveryIndex",
    "DiscoveryQuery",
    "DiscoveryResponse",
    "IndexBuildReport",
    "IndexIntegrityError",
    "build_discovery_index",
    "refresh_discovery_index",
]

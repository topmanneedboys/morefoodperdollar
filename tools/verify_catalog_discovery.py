#!/usr/bin/env python3
"""Verify the complete catalog discovery contract against one SQLite sidecar.

The compiler's own counters are not sufficient evidence for Search.  This
module independently walks the pinned source and the immutable sidecar, then
exercises the same DiscoveryIndex query path used by the backend.  It keeps
identity accounting, literal reachability, strict predicate retrieval,
language coverage, pagination and semantic accuracy as separate gates.

Semantic accuracy is intentionally *not* inferred from the compiler's labels.
An independent review ledger can be supplied with ``--semantic-eval``.  When
it is absent, the report records the gate as NOT_ESTABLISHED and can generate a
deterministic review queue for a human reviewer.
"""

from __future__ import annotations

import argparse
import collections
import gzip
import hashlib
import json
import sqlite3
import time
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from tools.catalog_discovery.index import (
    DiscoveryError,
    DiscoveryIndex,
    DiscoveryQuery,
    _fingerprint,
    _index_tokens,
    _norm,
    _predicate_identity_keys,
)


MAX_FAILURE_DETAILS = 5000
MAX_FAILURE_SAMPLES = 100
MAX_PREDICATE_FAILURE_SAMPLES = 50
DEFAULT_PAGE_SIZE = 50
DEFAULT_FACET_PAGE_SIZE = 30
REVIEW_QUEUE_SIZE = 1000
INDEPENDENT_REVIEW_METHODS = frozenset({
    "HUMAN_SOURCE_REVIEW",
    "DUAL_HUMAN_REVIEW",
    "DOMAIN_REVIEW",
})


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _source_rows(path: Path):
    opener = gzip.open if path.suffix.lower() == ".gz" else open
    with opener(path, "rt", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            value = json.loads(line)
            if not isinstance(value, Mapping):
                raise ValueError(f"source row {line_number} is not an object")
            yield line_number, value


def _limited_append(values: list[Any], value: Any, *, limit: int = MAX_FAILURE_DETAILS) -> None:
    if len(values) < limit:
        values.append(value)


def _status(failures: int) -> str:
    return "PASS" if failures == 0 else "FAIL"


def _identity_sort_key(meta: Mapping[str, Mapping[str, Any]], identity_key: str) -> tuple[str, str]:
    row = meta.get(identity_key)
    if row is None:
        return ("\uffff", identity_key)
    return (str(row["normalized_name"]), identity_key)


def _collect_product_pages(
    index: DiscoveryIndex,
    *,
    query: str,
    language: str,
    selections: Sequence[str] = (),
    page_size: int = DEFAULT_PAGE_SIZE,
) -> dict[str, Any]:
    """Fetch every product page and reject cursor loops or duplicate keys."""

    cursor: str | None = None
    rows: list[Mapping[str, Any]] = []
    cursors: set[str] = set()
    first: Mapping[str, Any] | None = None
    failures: list[str] = []
    page_count = 0
    while True:
        try:
            value = index.query(
                DiscoveryQuery(
                    query=query,
                    language=language,
                    selections=tuple(selections),
                    page_size=page_size,
                    cursor=cursor,
                )
            ).to_dict()
        except DiscoveryError as exc:
            failures.append(str(exc))
            break
        if first is None:
            first = value
        page_count += 1
        rows.extend(value.get("matches", ()))
        pagination = value.get("pagination") or {}
        if not pagination.get("hasMore"):
            break
        next_cursor = pagination.get("nextCursor")
        if not isinstance(next_cursor, str) or not next_cursor or next_cursor in cursors:
            failures.append("missing or repeated product cursor")
            break
        cursors.add(next_cursor)
        cursor = next_cursor
        if page_count > 100000:
            failures.append("product pagination exceeded safety bound")
            break
    keys = [str(row.get("identityKey")) for row in rows if isinstance(row, Mapping)]
    duplicates = len(keys) - len(set(keys))
    if duplicates:
        failures.append(f"duplicate identity keys across product pages: {duplicates}")
    return {
        "first": first or {},
        "rows": rows,
        "identityKeys": keys,
        "pageCount": page_count,
        "failures": failures,
    }


def _collect_facets(
    index: DiscoveryIndex,
    *,
    query: str,
    language: str,
    page_size: int = DEFAULT_PAGE_SIZE,
    facet_page_size: int = DEFAULT_FACET_PAGE_SIZE,
) -> dict[str, Any]:
    """Fetch all clarification pages and strict-check every advertised choice."""

    facet_cursor: str | None = None
    seen_cursors: set[str] = set()
    choice_ids: list[str] = []
    choices: list[Mapping[str, Any]] = []
    product_pages = 0
    failures: list[str] = []
    while True:
        try:
            value = index.query(
                DiscoveryQuery(
                    query=query,
                    language=language,
                    page_size=page_size,
                    facet_page_size=facet_page_size,
                    facet_cursor=facet_cursor,
                )
            ).to_dict()
        except DiscoveryError as exc:
            failures.append(str(exc))
            break
        product_pages += 1
        clarification = value.get("clarification")
        if not isinstance(clarification, Mapping):
            break
        page_choices = clarification.get("choices") or ()
        for choice in page_choices:
            if not isinstance(choice, Mapping):
                failures.append("clarification choice is not an object")
                continue
            predicate_id = choice.get("predicateId") or choice.get("id")
            if not isinstance(predicate_id, str) or not predicate_id:
                failures.append("clarification choice has no predicate id")
                continue
            if predicate_id in choice_ids:
                failures.append(f"duplicate clarification predicate: {predicate_id}")
            choice_ids.append(predicate_id)
            choices.append(choice)
        if not clarification.get("hasMore"):
            break
        next_cursor = clarification.get("nextCursor")
        if not isinstance(next_cursor, str) or not next_cursor or next_cursor in seen_cursors:
            failures.append("missing or repeated facet cursor")
            break
        seen_cursors.add(next_cursor)
        facet_cursor = next_cursor
        if product_pages > 100000:
            failures.append("facet pagination exceeded safety bound")
            break
    return {
        "pageCount": product_pages,
        "choiceCount": len(choice_ids),
        "choiceIds": choice_ids,
        "choices": choices,
        "failures": failures,
    }


def _choose_strict_alias(
    index: DiscoveryIndex,
    predicate_id: str,
    language: str,
    phrases: Sequence[str],
) -> str | None:
    """Choose an alias whose meaning is not narrowed by another predicate.

    A department often has very specific aliases too (for example ``agua``
    maps to the water type and the beverages department).  That phrase is a
    good query for the water type, but it cannot be used to test the complete
    beverages department posting.  The verifier therefore asks the same
    resolver for a canonical, non-competing phrase before comparing a strict
    result set.
    """

    for phrase in phrases:
        try:
            resolved, _remaining = index._resolve(phrase, language)  # type: ignore[attr-defined]
        except DiscoveryError:
            continue
        if predicate_id not in resolved:
            continue
        competing = tuple(
            value
            for value in resolved
            if value != predicate_id and value.startswith(("type:", "form:"))
        )
        if competing:
            continue
        return phrase
    return phrases[0] if phrases else None


def _load_review_queue(
    connection: sqlite3.Connection,
    output: Path,
    *,
    limit: int = REVIEW_QUEUE_SIZE,
) -> dict[str, Any]:
    """Write a deterministic stratified human-review queue, without labels."""

    rows = connection.execute(
        """
        SELECT i.identity_key, i.provider_key, i.display_name, i.source_fingerprint,
               c.type_status, c.department_status,
               COALESCE((SELECT MIN(p.predicate_id) FROM posting p
                         WHERE p.identity_key = i.identity_key
                           AND p.predicate_id LIKE 'department:%'), 'UNRESOLVED') AS stratum
        FROM identity i
        JOIN coverage c ON c.identity_key = i.identity_key
        ORDER BY stratum, i.identity_key
        """
    ).fetchall()
    by_stratum: dict[str, list[sqlite3.Row]] = collections.defaultdict(list)
    for row in rows:
        by_stratum[str(row["stratum"])].append(row)
    strata = sorted(by_stratum)
    selected: list[sqlite3.Row] = []
    cursor = {stratum: 0 for stratum in strata}
    while len(selected) < min(limit, len(rows)) and strata:
        progressed = False
        for stratum in strata:
            offset = cursor[stratum]
            values = by_stratum[stratum]
            if offset >= len(values):
                continue
            selected.append(values[offset])
            cursor[stratum] += 1
            progressed = True
            if len(selected) >= limit:
                break
        if not progressed:
            break
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        for row in selected:
            handle.write(
                json.dumps(
                    {
                        "identityKey": row["identity_key"],
                        "providerProductId": row["provider_key"],
                        "displayName": row["display_name"],
                        "sourceFingerprint": row["source_fingerprint"],
                        "stratum": row["stratum"],
                        "typeStatus": row["type_status"],
                        "departmentStatus": row["department_status"],
                        "reviewStatus": "PENDING_HUMAN_REVIEW",
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                )
                + "\n"
            )
    return {
        "path": str(output),
        "count": len(selected),
        "strata": {key: len(value) for key, value in sorted(by_stratum.items())},
        "status": "PENDING_HUMAN_REVIEW",
        "sha256": _sha256(output),
    }


def _evaluate_semantic_set(
    index: DiscoveryIndex,
    path: Path,
    *,
    meta: Mapping[str, Mapping[str, Any]],
) -> dict[str, Any]:
    """Evaluate an independently authored query/identity review ledger.

    Supported case shape::

        {"id":"...", "query":"rice", "language":"es",
         "selection":"form:rice:grain",
         "expectedIdentityKeys":[...],
         "expectedProviderProductIds":[...],
         "reviewStatus":"REVIEWED",
         "reviewerId":"reviewer-a",
         "reviewMethod":"HUMAN_SOURCE_REVIEW",
         "reviewedAt":"2026-10-17T19:00:00Z"}

    ``expectedIdentityKeys`` is a complete membership oracle for the query,
    not just a positive sample.  Provider IDs make retailer-copy grouping
    explicit.  The expected sets and review provenance are external evidence;
    missing, incomplete or unreviewed cases are never counted as accuracy
    passes.  ``mustIncludeIdentityKeys`` and ``mustExcludeIdentityKeys`` are
    retained as optional readable assertions, but cannot establish accuracy on
    their own.
    """

    value = json.loads(path.read_text(encoding="utf-8"))
    cases = value.get("cases") if isinstance(value, Mapping) else value
    if not isinstance(cases, list):
        return {"status": "FAIL", "failureCount": 1, "failures": ["semantic eval must contain a cases array"]}
    results: list[dict[str, Any]] = []
    reviewed = 0
    unreviewed = 0
    passed = 0
    failures = 0
    expected_identity_total = 0
    actual_identity_total = 0
    true_positive_identity_total = 0
    expected_provider_total = 0
    actual_provider_total = 0
    true_positive_provider_total = 0
    case_ids: set[str] = set()
    for position, case in enumerate(cases):
        if not isinstance(case, Mapping):
            failures += 1
            _limited_append(results, {"index": position, "status": "FAIL", "reason": "case is not an object"})
            continue
        case_id = str(case.get("id", position))
        if case_id in case_ids:
            failures += 1
            _limited_append(results, {"id": case_id, "status": "FAIL", "reason": "duplicate case id"})
            continue
        case_ids.add(case_id)
        if case.get("reviewStatus") != "REVIEWED":
            unreviewed += 1
            _limited_append(results, {"id": case_id, "status": "NOT_REVIEWED"})
            continue
        reviewed += 1
        query = case.get("query")
        language = case.get("language", "es")
        if not isinstance(query, str) or language not in {"es", "en"}:
            failures += 1
            _limited_append(results, {"id": case_id, "status": "FAIL", "reason": "invalid query or language"})
            continue
        reviewer_id = case.get("reviewerId")
        review_method = case.get("reviewMethod")
        reviewed_at = case.get("reviewedAt")
        provenance_failures: list[str] = []
        if not isinstance(reviewer_id, str) or not reviewer_id.strip():
            provenance_failures.append("reviewerId is required")
        if review_method not in INDEPENDENT_REVIEW_METHODS:
            provenance_failures.append("reviewMethod is not an independent review method")
        if not isinstance(reviewed_at, str) or not reviewed_at.strip():
            provenance_failures.append("reviewedAt is required")
        expected = case.get("expectedIdentityKeys")
        expected_providers = case.get("expectedProviderProductIds")
        if not isinstance(expected, list) or any(not isinstance(item, str) or not item.strip() for item in expected):
            provenance_failures.append("expectedIdentityKeys must be a complete string array")
            expected = []
        if not isinstance(expected_providers, list) or any(not isinstance(item, str) or not item.strip() for item in expected_providers):
            provenance_failures.append("expectedProviderProductIds must be a complete string array")
            expected_providers = []
        expected_set = {str(item) for item in expected}
        expected_provider_set = {str(item) for item in expected_providers}
        if len(expected_set) != len(expected) or len(expected_provider_set) != len(expected_providers):
            provenance_failures.append("expected membership arrays must not contain duplicates")
        unknown_expected = sorted(expected_set - set(meta))
        if unknown_expected:
            provenance_failures.append("expectedIdentityKeys contain identities outside the pinned sidecar")
        metadata_providers = {
            str(meta[key]["provider_key"])
            for key in expected_set
            if key in meta
        }
        if metadata_providers != expected_provider_set:
            provenance_failures.append("expected provider IDs do not match expected identity memberships")
        if provenance_failures:
            failures += 1
            _limited_append(
                results,
                {
                    "id": case_id,
                    "status": "FAIL",
                    "reason": "review provenance or complete oracle is invalid",
                    "details": provenance_failures,
                },
            )
            continue
        selection = case.get("selection")
        selections = (str(selection),) if isinstance(selection, str) and selection else ()
        pages = _collect_product_pages(index, query=query, language=language, selections=selections)
        actual = set(pages["identityKeys"])
        actual_providers = {
            str(meta[key]["provider_key"])
            for key in actual
            if key in meta
        }
        required = {str(item) for item in case.get("mustIncludeIdentityKeys", ()) if isinstance(item, str)}
        forbidden = {str(item) for item in case.get("mustExcludeIdentityKeys", ()) if isinstance(item, str)}
        missing = sorted(expected_set - actual)
        unexpected = sorted(actual - expected_set)
        leaked = sorted(forbidden & actual)
        missing_providers = sorted(expected_provider_set - actual_providers)
        unexpected_providers = sorted(actual_providers - expected_provider_set)
        expected_identity_total += len(expected_set)
        actual_identity_total += len(actual)
        true_positive_identity_total += len(expected_set & actual)
        expected_provider_total += len(expected_provider_set)
        actual_provider_total += len(actual_providers)
        true_positive_provider_total += len(expected_provider_set & actual_providers)
        ok = (
            not pages["failures"]
            and not missing
            and not unexpected
            and not leaked
            and not missing_providers
            and not unexpected_providers
            and required <= actual
        )
        if ok:
            passed += 1
        else:
            failures += 1
        _limited_append(
            results,
            {
                "id": case_id,
                "status": "PASS" if ok else "FAIL",
                "missingRequired": missing[:MAX_FAILURE_SAMPLES],
                "unexpectedIdentityKeys": unexpected[:MAX_FAILURE_SAMPLES],
                "leakedForbidden": leaked[:MAX_FAILURE_SAMPLES],
                "missingProviderProductIds": missing_providers[:MAX_FAILURE_SAMPLES],
                "unexpectedProviderProductIds": unexpected_providers[:MAX_FAILURE_SAMPLES],
                "queryFailures": pages["failures"],
            },
        )
    if not reviewed:
        status = "NOT_ESTABLISHED"
    elif failures:
        status = "FAIL"
    elif unreviewed:
        status = "INCOMPLETE"
    else:
        status = "PASS"
    identity_precision = true_positive_identity_total / actual_identity_total if actual_identity_total else None
    identity_recall = true_positive_identity_total / expected_identity_total if expected_identity_total else None
    provider_precision = true_positive_provider_total / actual_provider_total if actual_provider_total else None
    provider_recall = true_positive_provider_total / expected_provider_total if expected_provider_total else None
    return {
        "status": status,
        "caseCount": len(cases),
        "reviewedCases": reviewed,
        "unreviewedCases": unreviewed,
        "passedCases": passed,
        "failureCount": failures,
        "membershipMetrics": {
            "identity": {
                "expectedTotal": expected_identity_total,
                "actualTotal": actual_identity_total,
                "truePositiveTotal": true_positive_identity_total,
                "precision": identity_precision,
                "recall": identity_recall,
            },
            "providerProduct": {
                "expectedTotal": expected_provider_total,
                "actualTotal": actual_provider_total,
                "truePositiveTotal": true_positive_provider_total,
                "precision": provider_precision,
                "recall": provider_recall,
            },
        },
        "results": results,
        "note": "Expected memberships come from complete externally reviewed identity/provider sets; compiler labels are not used as the oracle.",
    }


def verify_catalog_discovery(
    index_path: Path | str,
    source_path: Path | str,
    *,
    expected_source_sha256: str | None = None,
    report_path: Path | str | None = None,
    review_queue_path: Path | str | None = None,
    semantic_eval_path: Path | str | None = None,
) -> dict[str, Any]:
    """Run the corpus-wide verification and return a JSON-ready report."""

    started = time.perf_counter()
    index_file = Path(index_path).resolve()
    source_file = Path(source_path).resolve()
    failures: list[str] = []
    if not index_file.is_file():
        return {"status": "FAIL", "failures": [f"index does not exist: {index_file}"]}
    if not source_file.is_file():
        return {"status": "FAIL", "failures": [f"source does not exist: {source_file}"]}
    source_sha256 = _sha256(source_file)
    if expected_source_sha256 and source_sha256 != expected_source_sha256.lower():
        failures.append("source checksum does not match the expected release")

    try:
        index = DiscoveryIndex(index_file, expected_source_sha256=expected_source_sha256)
    except DiscoveryError as exc:
        return {"status": "FAIL", "failures": [str(exc)], "index": str(index_file)}

    try:
        connection = index.connection
        build = dict(connection.execute("SELECT * FROM build LIMIT 1").fetchone())
        identity_meta = {
            str(row["identity_key"]): dict(row)
            for row in connection.execute("SELECT * FROM identity")
        }
        # These three release-scoped tables are one row per identity.  Load
        # them once so the 134k-row source walk does not issue hundreds of
        # thousands of indexed point queries.
        evidence_meta = {
            str(row["identity_key"]): dict(row)
            for row in connection.execute(
                "SELECT identity_key, source_field, original_span, source_fingerprint FROM evidence WHERE source_field = 'name'"
            )
        }
        impact_meta = {
            str(row["identity_key"]): str(row["record_fingerprint"])
            for row in connection.execute("SELECT identity_key, record_fingerprint FROM build_impact")
        }
        group_meta = {
            str(row["identity_key"]): str(row["group_id"])
            for row in connection.execute("SELECT identity_key, group_id FROM group_member")
        }
        identity_count = len(identity_meta)
        provider_keys = {str(row["provider_key"]) for row in identity_meta.values()}

        # 1. Source accounting, stable identity keys and source provenance.
        source_count = 0
        source_identity_keys: set[str] = set()
        source_provider_keys: set[str] = set()
        source_name_counts: collections.Counter[str] = collections.Counter()
        duplicate_source_keys: list[str] = []
        source_mismatches: collections.Counter[str] = collections.Counter()
        provenance_failures: list[dict[str, Any]] = []
        for line_number, row in _source_rows(source_file):
            source_count += 1
            identity_key = row.get("productEvidenceKey")
            provider_key = row.get("providerProductId")
            name = row.get("name")
            if not isinstance(identity_key, str) or not identity_key.strip():
                source_mismatches["missingIdentityKey"] += 1
                continue
            identity_key = identity_key.strip()
            if identity_key in source_identity_keys:
                duplicate_source_keys.append(identity_key)
            source_identity_keys.add(identity_key)
            if isinstance(provider_key, str):
                source_provider_keys.add(provider_key.strip())
            if isinstance(name, str):
                source_name_counts[_norm(name)] += 1
            source_fingerprint = _fingerprint(row)
            db_row = identity_meta.get(identity_key)
            if db_row is None:
                source_mismatches["missingSidecarIdentity"] += 1
                _limited_append(provenance_failures, {"line": line_number, "identityKey": identity_key, "reason": "missing sidecar identity"})
                continue
            expected_name = " ".join(str(name).split()) if isinstance(name, str) else ""
            expected_brand = row.get("brand") if isinstance(row.get("brand"), str) else None
            checks = {
                "providerKey": db_row["provider_key"] == provider_key,
                "displayName": db_row["display_name"] == expected_name,
                "brand": db_row["brand"] == expected_brand,
                "normalizedName": db_row["normalized_name"] == _norm(expected_name),
                "sourceFingerprint": db_row["source_fingerprint"] == source_fingerprint,
            }
            for label, ok in checks.items():
                if not ok:
                    source_mismatches[label] += 1
            evidence = evidence_meta.get(identity_key)
            if evidence is None:
                source_mismatches["missingNameEvidence"] += 1
                _limited_append(provenance_failures, {"line": line_number, "identityKey": identity_key, "reason": "missing name evidence"})
            else:
                evidence_ok = (
                    evidence["original_span"] == expected_name
                    and evidence["source_fingerprint"] == source_fingerprint
                )
                if not evidence_ok:
                    source_mismatches["evidenceMismatch"] += 1
                    _limited_append(provenance_failures, {"line": line_number, "identityKey": identity_key, "reason": "name evidence mismatch"})
            impact = impact_meta.get(identity_key)
            if impact is None:
                source_mismatches["missingBuildImpact"] += 1
                _limited_append(provenance_failures, {"line": line_number, "identityKey": identity_key, "reason": "missing build impact"})
            elif impact != source_fingerprint:
                source_mismatches["buildImpactFingerprintMismatch"] += 1
                _limited_append(provenance_failures, {"line": line_number, "identityKey": identity_key, "reason": "build impact fingerprint mismatch"})
            expected_group = f"provider:{provider_key}" if isinstance(provider_key, str) else ""
            if group_meta.get(identity_key) != expected_group:
                source_mismatches["groupMembershipMismatch"] += 1
                _limited_append(provenance_failures, {"line": line_number, "identityKey": identity_key, "reason": "group membership mismatch"})
        extra_sidecar_keys = sorted(set(identity_meta) - source_identity_keys)
        source_provider_groups = int(connection.execute("SELECT COUNT(*) FROM product_group").fetchone()[0])
        source_identity_status_failures = sum(source_mismatches.values()) + len(duplicate_source_keys) + len(extra_sidecar_keys)
        source_accounting = {
            "status": _status(source_identity_status_failures),
            "sourceRows": source_count,
            "sourceUniqueIdentityKeys": len(source_identity_keys),
            "sourceProviderProducts": len(source_provider_keys),
            "sidecarIdentityRows": identity_count,
            "sidecarProviderProducts": len(provider_keys),
            "sidecarProductGroups": source_provider_groups,
            "duplicateSourceIdentityKeys": len(duplicate_source_keys),
            "extraSidecarIdentityKeys": len(extra_sidecar_keys),
            "mismatches": dict(sorted(source_mismatches.items())),
            "sourceNameDuplicateIdentities": sum(value for value in source_name_counts.values() if value > 1),
            "sourceNameDuplicateGroups": sum(1 for value in source_name_counts.values() if value > 1),
            "provenanceFailureCount": len(provenance_failures),
            "provenanceFailures": provenance_failures,
        }
        if source_identity_status_failures:
            failures.append("source and sidecar identity/provenance accounting failed")

        # 2. Every identity's exact normalized name is represented by the
        # identity_token postings used by DiscoveryIndex._literal_keys.
        literal_failures: list[dict[str, Any]] = []
        token_rows = connection.execute("SELECT identity_key, token FROM identity_token ORDER BY identity_key, token")
        identity_rows = connection.execute("SELECT identity_key, display_name FROM identity ORDER BY identity_key")
        token_iter = iter(token_rows)
        current_token_row = next(token_iter, None)
        literal_reachable = 0
        literal_expected = 0
        for identity_row in identity_rows:
            identity_key = str(identity_row["identity_key"])
            expected_tokens = set(_index_tokens(str(identity_row["display_name"])))
            literal_expected += 1
            actual_tokens: set[str] = set()
            # Keep the merge robust even when a corrupt sidecar contains an
            # orphan token row; structural accounting will report that orphan
            # separately instead of making every later identity look missing.
            while current_token_row is not None and str(current_token_row[0]) < identity_key:
                current_token_row = next(token_iter, None)
            while current_token_row is not None and str(current_token_row[0]) == identity_key:
                actual_tokens.add(str(current_token_row[1]))
                current_token_row = next(token_iter, None)
            missing_tokens = sorted(expected_tokens - actual_tokens)
            unexpected_tokens = sorted(actual_tokens - expected_tokens)
            if not missing_tokens and not unexpected_tokens and expected_tokens:
                literal_reachable += 1
            else:
                _limited_append(
                    literal_failures,
                    {
                        "identityKey": identity_key,
                        "missingTokens": missing_tokens,
                        "unexpectedTokens": unexpected_tokens,
                    },
                )
        literal_status = _status(literal_expected - literal_reachable)
        identity_reachability = {
            "status": literal_status,
            "identitiesExpected": literal_expected,
            "identitiesLiteralReachable": literal_reachable,
            "identitiesLiteralUnreachable": literal_expected - literal_reachable,
            "failureDetails": literal_failures,
            "method": "Exact normalized-name token intersection over the same identity_token postings used by DiscoveryIndex._literal_keys; names are all within the query length limit.",
        }
        if literal_expected != literal_reachable:
            failures.append("one or more identities are not literally reachable")

        # 3. Language mappings and semantic retrieval paths.
        coverage_counts = {
            str(row[0]): int(row[1])
            for row in connection.execute("SELECT bilingual_status, COUNT(*) FROM coverage GROUP BY bilingual_status")
        }
        missing_es_identity_alias = int(
            connection.execute(
                "SELECT COUNT(*) FROM identity i WHERE NOT EXISTS (SELECT 1 FROM identity_alias a WHERE a.identity_key = i.identity_key AND a.language = 'es')"
            ).fetchone()[0]
        )
        missing_en_identity_alias = int(
            connection.execute(
                "SELECT COUNT(*) FROM identity i WHERE NOT EXISTS (SELECT 1 FROM identity_alias a WHERE a.identity_key = i.identity_key AND a.language = 'en')"
            ).fetchone()[0]
        )
        concept_language_counts = {
            language: int(value)
            for language, value in connection.execute("SELECT language, COUNT(DISTINCT predicate_id) FROM alias GROUP BY language")
        }
        all_predicates = [
            str(row[0])
            for row in connection.execute(
                "SELECT DISTINCT predicate_id FROM posting WHERE predicate_id LIKE 'type:%' OR predicate_id LIKE 'department:%' OR predicate_id LIKE 'form:%' ORDER BY predicate_id"
            )
        ]
        predicate_results: list[dict[str, Any]] = []
        predicate_failures: list[dict[str, Any]] = []
        typed_identity_keys: set[str] = set()
        type_or_department_identity_keys: set[str] = set()
        department_identity_keys: set[str] = set()
        form_identity_keys: set[str] = set()
        for predicate_id in all_predicates:
            expected_keys = _predicate_identity_keys(connection, predicate_id)
            if predicate_id.startswith("type:"):
                typed_identity_keys.update(expected_keys)
            if predicate_id.startswith("department:"):
                department_identity_keys.update(expected_keys)
            if predicate_id.startswith("form:"):
                form_identity_keys.update(expected_keys)
            if predicate_id.startswith(("type:", "department:")):
                type_or_department_identity_keys.update(expected_keys)
            alias_rows = connection.execute(
                "SELECT phrase_norm, language FROM alias WHERE predicate_id = ? ORDER BY language, phrase_norm",
                (predicate_id,),
            ).fetchall()
            aliases_by_language: dict[str, list[str]] = {"es": [], "en": []}
            for row in alias_rows:
                if str(row["language"]) in aliases_by_language:
                    aliases_by_language[str(row["language"])].append(str(row["phrase_norm"]))
            language_results: dict[str, Any] = {}
            for language in ("es", "en"):
                phrases = aliases_by_language[language]
                if not phrases:
                    language_results[language] = {"status": "NO_ALIAS", "aliasCount": 0}
                    continue
                phrase = _choose_strict_alias(index, predicate_id, language, phrases)
                if phrase is None:
                    language_results[language] = {"status": "NO_USABLE_ALIAS", "aliasCount": len(phrases)}
                    continue
                pages = _collect_product_pages(
                    index,
                    query=phrase,
                    language=language,
                    selections=(predicate_id,),
                )
                returned_keys = set(pages["identityKeys"])
                first = pages["first"]
                expected_product_count = len({identity_meta[key]["provider_key"] for key in expected_keys})
                mismatches: list[str] = list(pages["failures"])
                if returned_keys != expected_keys:
                    missing = sorted(expected_keys - returned_keys)
                    extra = sorted(returned_keys - expected_keys)
                    mismatches.append(f"identity set mismatch missing={len(missing)} extra={len(extra)}")
                if first:
                    total = first.get("total") or {}
                    if int(total.get("sourceIdentityCount", -1)) != len(expected_keys):
                        mismatches.append("advertised source identity count mismatch")
                    if int(total.get("productCount", -1)) != expected_product_count:
                        mismatches.append("advertised provider product count mismatch")
                    ordered = [str(key) for key in pages["identityKeys"]]
                    if ordered != sorted(ordered, key=lambda key: _identity_sort_key(identity_meta, key)):
                        mismatches.append("deterministic identity sorting mismatch")
                language_results[language] = {
                    "status": _status(len(mismatches)),
                    "aliasCount": len(phrases),
                    "query": phrase,
                    "expectedIdentityCount": len(expected_keys),
                    "returnedIdentityCount": len(returned_keys),
                    "pageCount": pages["pageCount"],
                    "failures": mismatches,
                }
                if mismatches:
                    detail = {
                        "predicateId": predicate_id,
                        "language": language,
                        "query": phrase,
                        "expectedIdentityCount": len(expected_keys),
                        "returnedIdentityCount": len(returned_keys),
                        "failureCount": len(mismatches),
                        "failures": mismatches,
                        "failedIdentitySample": sorted(expected_keys - returned_keys)[:MAX_PREDICATE_FAILURE_SAMPLES],
                    }
                    _limited_append(predicate_failures, detail)
            predicate_results.append(
                {
                    "predicateId": predicate_id,
                    "kind": "type" if predicate_id.startswith("type:") else "department" if predicate_id.startswith("department:") else "form" if predicate_id.startswith("form:") else "other",
                    "expectedIdentityCount": len(expected_keys),
                    "expectedProviderProductCount": len({identity_meta[key]["provider_key"] for key in expected_keys}),
                    "aliasCounts": {language: len(aliases_by_language[language]) for language in ("es", "en")},
                    "languages": language_results,
                }
            )
        predicate_status = _status(len(predicate_failures))
        strict_selection = {
            "status": predicate_status,
            "predicateCount": len(all_predicates),
            "predicateResults": predicate_results,
            "failedPredicateCount": len(predicate_failures),
            "failedPredicates": predicate_failures,
            "directTypeIdentityCount": len(typed_identity_keys),
            "typeOrDepartmentIdentityCount": len(type_or_department_identity_keys),
            "departmentIdentityCount": len(department_identity_keys),
            "formIdentityCount": len(form_identity_keys),
        }
        if predicate_failures:
            failures.append("one or more materialized strict predicates failed full retrieval")

        language_coverage = {
            "status": "PASS",
            "sidecarCoverageStatuses": coverage_counts,
            "identityAliasIdentitiesMissingES": missing_es_identity_alias,
            "identityAliasIdentitiesMissingEN": missing_en_identity_alias,
            "conceptPredicatesWithAliases": concept_language_counts,
            "conceptPredicatesMissingES": sum(
                1
                for item in predicate_results
                if item["expectedIdentityCount"] and item["aliasCounts"]["es"] == 0
            ),
            "conceptPredicatesMissingEN": sum(
                1
                for item in predicate_results
                if item["expectedIdentityCount"] and item["aliasCounts"]["en"] == 0
            ),
            "note": "Literal retrieval is language-neutral; these counts distinguish source identity aliases and reviewed semantic predicate aliases from the literal path.",
        }

        # 4. Full broad-query pagination and facet paging for every concept in
        # both languages.  This is separate from strict predicate retrieval.
        concept_rows = connection.execute(
            "SELECT concept_id FROM concept WHERE kind IN ('TYPE', 'DEPARTMENT') ORDER BY concept_id"
        ).fetchall()
        broad_queries = 0
        broad_failures: list[dict[str, Any]] = []
        facet_pages = 0
        facet_choices = 0
        facet_strict_failures: list[dict[str, Any]] = []
        for concept_row in concept_rows:
            concept_id = str(concept_row[0])
            aliases = connection.execute(
                "SELECT phrase_norm, language FROM alias WHERE predicate_id = ? AND language IN ('es','en') ORDER BY language, phrase_norm",
                (concept_id,),
            ).fetchall()
            for language in ("es", "en"):
                phrase_rows = [row for row in aliases if str(row["language"]) == language]
                if not phrase_rows:
                    continue
                phrase = str(phrase_rows[0]["phrase_norm"])
                broad_queries += 1
                product_pages = _collect_product_pages(index, query=phrase, language=language)
                ordered = product_pages["identityKeys"]
                broad_query_failures = list(product_pages["failures"])
                if len(ordered) != len(set(ordered)):
                    broad_query_failures.append("duplicate identity keys")
                if ordered != sorted(ordered, key=lambda key: _identity_sort_key(identity_meta, key)):
                    broad_query_failures.append("unstable result sorting")
                if broad_query_failures:
                    _limited_append(
                        broad_failures,
                        {"conceptId": concept_id, "language": language, "query": phrase, "failures": broad_query_failures},
                    )
                facets = _collect_facets(index, query=phrase, language=language)
                facet_pages += int(facets["pageCount"])
                facet_choices += int(facets["choiceCount"])
                for choice in facets["choices"]:
                    predicate_id = str(choice.get("predicateId") or choice.get("id"))
                    selected = _collect_product_pages(
                        index,
                        query=phrase,
                        language=language,
                        selections=(predicate_id,),
                        page_size=DEFAULT_PAGE_SIZE,
                    )
                    expected_identities = int(choice.get("sourceIdentityCount", -1))
                    expected_products = int(choice.get("productCount", -1))
                    actual_identities = len(set(selected["identityKeys"]))
                    actual_products = len({identity_meta[key]["provider_key"] for key in set(selected["identityKeys"]) if key in identity_meta})
                    choice_failures = list(selected["failures"])
                    if actual_identities != expected_identities:
                        choice_failures.append("choice source identity count mismatch")
                    if actual_products != expected_products:
                        choice_failures.append("choice provider product count mismatch")
                    if choice_failures:
                        _limited_append(
                            facet_strict_failures,
                            {
                                "conceptId": concept_id,
                                "language": language,
                                "query": phrase,
                                "predicateId": predicate_id,
                                "failures": choice_failures,
                            },
                        )
        pagination_status = _status(len(broad_failures) + len(facet_strict_failures))
        pagination = {
            "status": pagination_status,
            "broadConceptLanguageQueries": broad_queries,
            "broadQueryFailures": broad_failures,
            "facetPages": facet_pages,
            "facetChoices": facet_choices,
            "strictChoiceChecks": facet_choices,
            "strictChoiceFailures": facet_strict_failures,
            "pageSize": DEFAULT_PAGE_SIZE,
            "facetPageSize": DEFAULT_FACET_PAGE_SIZE,
        }
        if pagination_status != "PASS":
            failures.append("broad query or facet pagination verification failed")

        review_queue: dict[str, Any] | None = None
        if review_queue_path:
            review_queue = _load_review_queue(connection, Path(review_queue_path).resolve())
        if semantic_eval_path:
            semantic_accuracy = _evaluate_semantic_set(index, Path(semantic_eval_path).resolve(), meta=identity_meta)
        else:
            semantic_accuracy = {
                "status": "NOT_ESTABLISHED",
                "caseCount": 0,
                "reviewedCases": 0,
                "passedCases": 0,
                "failureCount": 0,
                "note": "No independently reviewed semantic ledger was supplied. The compiler's own postings are deliberately not used as an accuracy oracle.",
            }
        if semantic_accuracy["status"] != "PASS":
            failures.append("independent semantic accuracy is not established")

        # Build metadata is included even when the sidecar's historical
        # assertion counter does not match its deduplicated table.
        actual_assertions = int(connection.execute("SELECT COUNT(*) FROM assertion").fetchone()[0])
        structural = {
            "status": _status(
                int(build["identity_records"]) != identity_count
                or int(build["assertions"]) != actual_assertions
            ),
            "build": build,
            "actualAssertionRows": actual_assertions,
            "assertionCountMismatch": int(build["assertions"]) != actual_assertions,
        }
        if structural["status"] != "PASS":
            failures.append("build metadata does not match stored assertion rows")

        report: dict[str, Any] = {
            "schemaVersion": "valuepilot-catalog-verification-v1",
            "status": "PASS" if not failures else "INCOMPLETE",
            "index": {
                "path": str(index_file),
                "sha256": _sha256(index_file),
                "rulesVersion": build.get("rules_version"),
                "schemaVersion": build.get("schema_version"),
                "releaseId": build.get("release_id"),
                "sourceSha256": build.get("source_sha256"),
            },
            "source": {"path": str(source_file), "sha256": source_sha256},
            "structural": structural,
            "sourceAccounting": source_accounting,
            "identityReachability": identity_reachability,
            "classification": {
                "directProductType": {
                    "identityCount": len(typed_identity_keys),
                    "providerProductCount": len({identity_meta[key]["provider_key"] for key in typed_identity_keys}),
                },
                "typeOrDepartment": {
                    "identityCount": len(type_or_department_identity_keys),
                    "providerProductCount": len({identity_meta[key]["provider_key"] for key in type_or_department_identity_keys}),
                },
                "department": {
                    "identityCount": len(department_identity_keys),
                    "providerProductCount": len({identity_meta[key]["provider_key"] for key in department_identity_keys}),
                },
                "form": {
                    "identityCount": len(form_identity_keys),
                    "providerProductCount": len({identity_meta[key]["provider_key"] for key in form_identity_keys}),
                },
                "coverageRows": {
                    str(row["type_status"]): int(row["count"])
                    for row in connection.execute("SELECT type_status, COUNT(*) AS count FROM coverage GROUP BY type_status")
                },
            },
            "languageCoverage": language_coverage,
            "strictSelection": strict_selection,
            "pagination": pagination,
            "semanticAccuracy": semantic_accuracy,
            "reviewQueue": review_queue,
            "failures": failures,
            "elapsedSeconds": round(time.perf_counter() - started, 3),
        }
        if report_path:
            report_file = Path(report_path).resolve()
            report_file.parent.mkdir(parents=True, exist_ok=True)
            report_file.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        return report
    finally:
        index.close()


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Verify every identity and materialized discovery result in a ValuePilot sidecar.")
    parser.add_argument("--index", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--source-sha256")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--review-queue", type=Path)
    parser.add_argument("--semantic-eval", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    report = verify_catalog_discovery(
        args.index,
        args.source,
        expected_source_sha256=args.source_sha256,
        report_path=args.report,
        review_queue_path=args.review_queue,
        semantic_eval_path=args.semantic_eval,
    )
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    return 0 if report.get("status") == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())

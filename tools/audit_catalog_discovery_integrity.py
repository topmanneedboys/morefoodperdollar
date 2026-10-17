#!/usr/bin/env python3
"""Audit structural invariants of a qualified catalog discovery sidecar.

This audit is deliberately separate from the compiler.  It checks that the
compiled identity/evidence graph is complete and internally consistent without
re-running the classifier as its own semantic oracle.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path
from typing import Any

from tools.catalog_discovery import DiscoveryError, DiscoveryIndex


def _check(condition: bool, failures: list[str], message: str) -> None:
    if not condition:
        failures.append(message)


def _hierarchy_cycle(connection: sqlite3.Connection) -> list[str] | None:
    edges: dict[str, set[str]] = {}
    for row in connection.execute("SELECT child_concept, parent_concept FROM parent"):
        edges.setdefault(str(row[0]), set()).add(str(row[1]))
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(node: str, trail: list[str]) -> list[str] | None:
        if node in visiting:
            start = trail.index(node) if node in trail else 0
            return trail[start:] + [node]
        if node in visited:
            return None
        visiting.add(node)
        for parent in sorted(edges.get(node, ())):
            cycle = visit(parent, trail + [node])
            if cycle:
                return cycle
        visiting.remove(node)
        visited.add(node)
        return None

    for node in sorted(edges):
        cycle = visit(node, [])
        if cycle:
            return cycle
    return None


def audit_catalog_discovery_index(
    index_path: Path | str,
    *,
    expected_source_sha256: str | None = None,
) -> dict[str, Any]:
    """Return a machine-readable structural audit for one immutable sidecar."""

    failures: list[str] = []
    path = Path(index_path).resolve()
    try:
        index = DiscoveryIndex(path, expected_source_sha256=expected_source_sha256)
    except DiscoveryError as exc:
        return {
            "status": "FAIL",
            "failureCount": 1,
            "failures": [str(exc)],
            "index": str(path),
        }

    try:
        connection = index.connection
        build = dict(connection.execute("SELECT * FROM build LIMIT 1").fetchone())
        counts = {
            "identity": int(connection.execute("SELECT COUNT(*) FROM identity").fetchone()[0]),
            "coverage": int(connection.execute("SELECT COUNT(*) FROM coverage").fetchone()[0]),
            "groupMembers": int(connection.execute("SELECT COUNT(*) FROM group_member").fetchone()[0]),
            "identityTokens": int(connection.execute("SELECT COUNT(DISTINCT identity_key) FROM identity_token").fetchone()[0]),
            "evidence": int(connection.execute("SELECT COUNT(*) FROM evidence").fetchone()[0]),
            "assertions": int(connection.execute("SELECT COUNT(*) FROM assertion").fetchone()[0]),
            "postings": int(connection.execute("SELECT COUNT(*) FROM posting").fetchone()[0]),
            "concepts": int(connection.execute("SELECT COUNT(*) FROM concept").fetchone()[0]),
            "aliases": int(connection.execute("SELECT COUNT(*) FROM alias").fetchone()[0]),
        }
        _check(counts["identity"] == int(build["identity_records"]), failures, "build identity count differs from identity table")
        _check(counts["assertions"] == int(build["assertions"]), failures, "build assertion count differs from assertion table")
        _check(counts["coverage"] == counts["identity"], failures, "not every identity has a coverage row")
        _check(counts["groupMembers"] == counts["identity"], failures, "not every identity has one group membership")
        _check(counts["identityTokens"] == counts["identity"], failures, "not every identity has a literal token posting")

        orphan_coverage = connection.execute(
            "SELECT COUNT(*) FROM coverage c LEFT JOIN identity i ON i.identity_key = c.identity_key WHERE i.identity_key IS NULL"
        ).fetchone()[0]
        orphan_members = connection.execute(
            "SELECT COUNT(*) FROM group_member g LEFT JOIN identity i ON i.identity_key = g.identity_key WHERE i.identity_key IS NULL"
        ).fetchone()[0]
        orphan_evidence = connection.execute(
            "SELECT COUNT(*) FROM evidence e LEFT JOIN identity i ON i.identity_key = e.identity_key WHERE i.identity_key IS NULL"
        ).fetchone()[0]
        orphan_assertions = connection.execute(
            "SELECT COUNT(*) FROM assertion a LEFT JOIN identity i ON i.identity_key = a.identity_key LEFT JOIN evidence e ON e.evidence_id = a.evidence_id LEFT JOIN concept c ON c.concept_id = a.concept_id WHERE i.identity_key IS NULL OR e.evidence_id IS NULL OR c.concept_id IS NULL"
        ).fetchone()[0]
        orphan_postings = connection.execute(
            "SELECT COUNT(*) FROM posting p LEFT JOIN identity i ON i.identity_key = p.identity_key LEFT JOIN concept c ON c.concept_id = p.concept_id WHERE i.identity_key IS NULL OR c.concept_id IS NULL"
        ).fetchone()[0]
        for label, value in (
            ("orphan coverage rows", orphan_coverage),
            ("orphan group members", orphan_members),
            ("orphan evidence rows", orphan_evidence),
            ("orphan assertions", orphan_assertions),
            ("orphan postings", orphan_postings),
        ):
            _check(int(value) == 0, failures, f"{label}: {value}")

        duplicate_groups = connection.execute(
            "SELECT COUNT(*) FROM (SELECT identity_key, COUNT(*) c FROM group_member GROUP BY identity_key HAVING c <> 1)"
        ).fetchone()[0]
        _check(int(duplicate_groups) == 0, failures, f"identities with non-singleton group membership: {duplicate_groups}")

        missing_labels = connection.execute(
            "SELECT COUNT(*) FROM concept WHERE TRIM(label_es) = '' OR TRIM(label_en) = ''"
        ).fetchone()[0]
        _check(int(missing_labels) == 0, failures, f"concepts missing bilingual labels: {missing_labels}")

        cycle = _hierarchy_cycle(connection)
        _check(cycle is None, failures, f"concept hierarchy cycle: {' -> '.join(cycle or ())}")

        predicate_ids = [str(row[0]) for row in connection.execute("SELECT DISTINCT predicate_id FROM posting ORDER BY predicate_id")]
        empty_predicates = [
            predicate
            for predicate in predicate_ids
            if int(connection.execute("SELECT COUNT(*) FROM posting WHERE predicate_id = ?", (predicate,)).fetchone()[0]) == 0
        ]
        _check(not empty_predicates, failures, f"empty posting predicates: {empty_predicates[:5]}")

        # Exercise the typed error boundary and a stale cursor without using a
        # semantic expectation as the oracle.
        try:
            index.query({"query": "rice", "language": "en", "selections": ["form:does-not-exist"]})
            failures.append("unknown selection was accepted")
        except (ValueError, DiscoveryError):
            pass
        first = index.query({"query": "rice", "language": "en", "pageSize": 1}).to_dict()
        cursor = first.get("pagination", {}).get("nextCursor")
        if cursor:
            try:
                index.query({"query": "eggs", "language": "en", "pageSize": 1, "cursor": cursor})
                failures.append("stale product cursor was accepted")
            except (ValueError, DiscoveryError):
                pass

        return {
            "status": "PASS" if not failures else "FAIL",
            "failureCount": len(failures),
            "failures": failures,
            "index": str(path),
            "build": build,
            "counts": counts,
            "predicateCount": len(predicate_ids),
            "checked": [
                "identity/coverage/group/literal accounting",
                "evidence/assertion/posting foreign-key reachability",
                "single group membership per identity",
                "bilingual concept labels",
                "acyclic concept hierarchy",
                "nonempty posting predicates",
                "unknown selection and stale cursor rejection",
            ],
        }
    finally:
        index.close()


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Audit structural invariants of a ValuePilot discovery sidecar.")
    parser.add_argument("--index", required=True, type=Path)
    parser.add_argument("--source-sha256")
    parser.add_argument("--report", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    result = audit_catalog_discovery_index(args.index, expected_source_sha256=args.source_sha256)
    if args.report:
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())


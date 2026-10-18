#!/usr/bin/env python3
"""Build a provider-group-safe semantic review queue from a discovery sidecar.

The queue contains no expected labels. It selects complete provider-product
groups so retailer identity copies cannot be split between review sets, and it
keeps unresolved and department strata visible to a human reviewer.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from tools.catalog_discovery import DiscoveryError, DiscoveryIndex


DEFAULT_GROUP_LIMIT = 1000


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def build_semantic_review_queue(
    index_path: Path | str,
    output_path: Path | str,
    *,
    expected_source_sha256: str | None = None,
    group_limit: int = DEFAULT_GROUP_LIMIT,
) -> dict[str, Any]:
    """Select complete provider groups in deterministic review strata."""

    if group_limit < 1:
        raise ValueError("group_limit must be positive")
    output = Path(output_path).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    index = DiscoveryIndex(Path(index_path).resolve(), expected_source_sha256=expected_source_sha256)
    try:
        rows = index.connection.execute(
            """
            SELECT i.identity_key, i.provider_key, i.display_name, i.source_fingerprint,
                   c.type_status, c.department_status,
                   COALESCE((SELECT MIN(p.predicate_id) FROM posting p
                             WHERE p.identity_key = i.identity_key
                               AND p.predicate_id LIKE 'department:%'), 'UNRESOLVED') AS stratum
            FROM identity i
            JOIN coverage c ON c.identity_key = i.identity_key
            ORDER BY i.provider_key, i.identity_key
            """
        ).fetchall()
        groups: dict[str, list[sqlite3.Row]] = defaultdict(list)
        for row in rows:
            groups[str(row["provider_key"])].append(row)

        grouped_by_stratum: dict[str, list[tuple[str, list[sqlite3.Row]]]] = defaultdict(list)
        group_stratum: dict[str, str] = {}
        for provider_key, identities in groups.items():
            strata = {str(row["stratum"]) for row in identities}
            # An unresolved copy keeps the whole provider group in the
            # unresolved review stratum instead of hiding it under a resolved
            # retailer identity.
            stratum = "UNRESOLVED" if "UNRESOLVED" in strata else sorted(strata)[0]
            grouped_by_stratum[stratum].append((provider_key, identities))
            group_stratum[provider_key] = stratum
        for values in grouped_by_stratum.values():
            values.sort(key=lambda item: item[0])

        strata = sorted(grouped_by_stratum)
        selected: list[tuple[str, list[sqlite3.Row]]] = []
        offsets = {stratum: 0 for stratum in strata}
        while len(selected) < min(group_limit, len(groups)):
            progressed = False
            for stratum in strata:
                offset = offsets[stratum]
                values = grouped_by_stratum[stratum]
                if offset >= len(values):
                    continue
                selected.append(values[offset])
                offsets[stratum] += 1
                progressed = True
                if len(selected) >= group_limit:
                    break
            if not progressed:
                break
        selected.sort(key=lambda item: (group_stratum[item[0]], item[0]))

        identity_count = 0
        selected_strata: Counter[str] = Counter()
        with output.open("w", encoding="utf-8", newline="\n") as handle:
            for provider_key, identities in selected:
                strata_for_group = {str(row["stratum"]) for row in identities}
                stratum = "UNRESOLVED" if "UNRESOLVED" in strata_for_group else sorted(strata_for_group)[0]
                selected_strata[stratum] += 1
                for row in identities:
                    identity_count += 1
                    handle.write(
                        json.dumps(
                            {
                                "reviewGroupId": f"provider:{provider_key}",
                                "providerProductId": provider_key,
                                "identityKey": row["identity_key"],
                                "displayName": row["display_name"],
                                "sourceFingerprint": row["source_fingerprint"],
                                "stratum": stratum,
                                "typeStatus": row["type_status"],
                                "departmentStatus": row["department_status"],
                                "reviewStatus": "PENDING_HUMAN_REVIEW",
                                "split": "HELD_OUT",
                            },
                            ensure_ascii=False,
                            sort_keys=True,
                        )
                        + "\n"
                    )
        return {
            "schemaVersion": "valuepilot-semantic-review-queue-v1",
            "indexPath": str(Path(index_path).resolve()),
            "indexSha256": _sha256(Path(index_path).resolve()),
            "sourceSha256": index.metadata["source_sha256"],
            "releaseId": index.metadata["release_id"],
            "rulesVersion": index.metadata["rules_version"],
            "providerProductGroupsAvailable": len(groups),
            "providerProductGroupsSelected": len(selected),
            "identityRowsSelected": identity_count,
            "strataProviderGroups": dict(sorted(selected_strata.items())),
            "outputPath": str(output),
            "outputSha256": _sha256(output),
            "status": "PENDING_HUMAN_REVIEW",
            "groupIntegrity": "EVERY_SELECTED_PROVIDER_GROUP_INCLUDES_ALL_LINKED_IDENTITIES",
        }
    finally:
        index.close()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build a provider-group-safe semantic review queue.")
    parser.add_argument("--index", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--source-sha256")
    parser.add_argument("--group-limit", type=int, default=DEFAULT_GROUP_LIMIT)
    args = parser.parse_args(argv)
    try:
        result = build_semantic_review_queue(
            args.index,
            args.output,
            expected_source_sha256=args.source_sha256,
            group_limit=args.group_limit,
        )
    except (DiscoveryError, OSError, ValueError) as exc:
        print(json.dumps({"status": "FAIL", "error": str(exc)}, ensure_ascii=False))
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

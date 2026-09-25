#!/usr/bin/env python3
"""Promote a verified offline catalog snapshot without losing last-known-good state.

This is a local, deterministic release boundary.  It never downloads data or
rewrites a candidate snapshot.  The caller supplies an already-built snapshot
directory and an RSA public key; promotion verifies the exact signed artifact,
freshness, metro scope, and launch coverage band before atomically replacing
the active and last-known-good pointers.  Any rejected candidate leaves both
pointers byte-for-byte unchanged.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path
from typing import Any, Mapping

from tools.build_offline_catalog_snapshot import (
    IDENTITY_ONLY_CATALOG_ROLE,
    NO_CURRENT_OFFERS_STATUS,
    _canonical_json,
    _parse_timestamp,
)
from tools.verify_offline_catalog_snapshot import SnapshotBuildError, verify_snapshot


MIN_CATALOG_RECORDS = 1_500
MAX_CATALOG_RECORDS = 30_000
SUPPORTED_METRO_REGIONS = frozenset({"ca-gta", "ca-metro-vancouver"})
CURRENT_POINTER_NAME = "current.json"
LAST_KNOWN_GOOD_POINTER_NAME = "last-known-good.json"


class SnapshotPromotionError(ValueError):
    """A candidate cannot become the active offline catalog snapshot."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise SnapshotPromotionError(message)


def _load_manifest(snapshot_dir: Path) -> Mapping[str, Any]:
    manifest_path = snapshot_dir / "manifest.json"
    _require(manifest_path.is_file(), f"Missing candidate manifest: {manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_bytes())
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SnapshotPromotionError("Candidate manifest is not valid JSON") from exc
    _require(isinstance(manifest, dict), "Candidate manifest must be an object")
    return manifest


def _pointer_payload(snapshot_dir: Path, state_root: Path, manifest: Mapping[str, Any], verification: Mapping[str, Any]) -> dict[str, Any]:
    resolved_snapshot = snapshot_dir.resolve()
    resolved_root = state_root.resolve()
    _require(resolved_snapshot != resolved_root, "Snapshot directory must not be the pointer state root")
    try:
        relative_snapshot = resolved_snapshot.relative_to(resolved_root)
    except ValueError as exc:
        raise SnapshotPromotionError("Snapshot directory must be inside the promotion state root") from exc
    records = manifest.get("coverage", {}).get("catalogRecordCount") if isinstance(manifest.get("coverage"), dict) else None
    coverage = manifest.get("coverage") if isinstance(manifest.get("coverage"), dict) else {}
    return {
        "schemaVersion": 1,
        "catalogRole": manifest["catalogRole"],
        "snapshotId": manifest["snapshotId"],
        "regionId": manifest["regionId"],
        "generatedAtEpochMillis": manifest["generatedAtEpochMillis"],
        "manifestSha256": verification["manifestSha256"],
        "catalogRecordCount": records,
        "currentOfferRecordCount": coverage.get("currentOfferRecordCount"),
        "currentOfferCoverage": coverage.get("currentOfferCoverage"),
        "snapshotPath": relative_snapshot.as_posix(),
    }


def _read_pointer(pointer_path: Path, state_root: Path) -> tuple[dict[str, Any], Path]:
    _require(pointer_path.is_file(), f"Missing snapshot pointer: {pointer_path}")
    try:
        value = json.loads(pointer_path.read_bytes())
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SnapshotPromotionError(f"Invalid snapshot pointer: {pointer_path.name}") from exc
    _require(isinstance(value, dict), f"Snapshot pointer {pointer_path.name} must be an object")
    _require(value.get("schemaVersion") == 1, f"Unsupported snapshot pointer schema: {pointer_path.name}")
    relative_path = value.get("snapshotPath")
    _require(isinstance(relative_path, str) and relative_path.strip() == relative_path and relative_path, "Snapshot pointer path is invalid")
    root = state_root.resolve()
    resolved_snapshot = (root / Path(relative_path)).resolve()
    try:
        resolved_snapshot.relative_to(root)
    except ValueError as exc:
        raise SnapshotPromotionError("Snapshot pointer escapes the promotion state root") from exc
    _require(resolved_snapshot.is_dir(), f"Snapshot pointer target is missing: {relative_path}")
    return value, resolved_snapshot


def _write_pointer_atomically(pointer_path: Path, payload: Mapping[str, Any]) -> None:
    pointer_path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=pointer_path.parent,
            prefix=f".{pointer_path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary = Path(handle.name)
            handle.write(_canonical_json(payload))
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, pointer_path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def _verify_existing_pointer(pointer_path: Path, state_root: Path, public_key: Path) -> tuple[dict[str, Any], Path]:
    pointer, snapshot_dir = _read_pointer(pointer_path, state_root)
    try:
        verification = verify_snapshot(snapshot_dir, public_key=public_key, require_signature=True)
    except (SnapshotBuildError, OSError) as exc:
        raise SnapshotPromotionError(f"Existing pointer verification failed: {pointer_path.name}: {exc}") from exc
    manifest = _load_manifest(snapshot_dir)
    _require(pointer.get("snapshotId") == manifest.get("snapshotId"), f"Pointer {pointer_path.name} snapshot id mismatch")
    _require(pointer.get("catalogRole") == manifest.get("catalogRole") == IDENTITY_ONLY_CATALOG_ROLE, f"Pointer {pointer_path.name} catalog role mismatch")
    coverage = manifest.get("coverage") if isinstance(manifest.get("coverage"), dict) else {}
    _require(pointer.get("currentOfferRecordCount") == coverage.get("currentOfferRecordCount") == 0, f"Pointer {pointer_path.name} current-offer count mismatch")
    _require(pointer.get("currentOfferCoverage") == coverage.get("currentOfferCoverage") == NO_CURRENT_OFFERS_STATUS, f"Pointer {pointer_path.name} current-offer status mismatch")
    _require(pointer.get("manifestSha256") == verification.get("manifestSha256"), f"Pointer {pointer_path.name} manifest hash mismatch")
    _require(pointer.get("generatedAtEpochMillis") == manifest.get("generatedAtEpochMillis"), f"Pointer {pointer_path.name} generated time mismatch")
    return pointer, snapshot_dir


def promote_snapshot(
    candidate_dir: Path,
    state_root: Path,
    public_key: Path,
    *,
    evaluated_at_epoch_millis: int,
    maximum_snapshot_age_millis: int,
    expected_region_id: str | None = None,
    minimum_catalog_records: int = MIN_CATALOG_RECORDS,
    maximum_catalog_records: int = MAX_CATALOG_RECORDS,
) -> dict[str, Any]:
    """Verify and atomically promote one candidate, or leave state unchanged."""

    candidate_dir = candidate_dir.resolve()
    state_root = state_root.resolve()
    _require(candidate_dir.is_dir(), f"Missing candidate snapshot directory: {candidate_dir}")
    _require(public_key.is_file(), f"Missing snapshot verification key: {public_key}")
    _require(evaluated_at_epoch_millis > 0, "evaluated_at_epoch_millis must be positive")
    _require(maximum_snapshot_age_millis > 0, "maximum_snapshot_age_millis must be positive")
    _require(1 <= minimum_catalog_records <= maximum_catalog_records, "Catalog coverage bounds are invalid")

    try:
        verification = verify_snapshot(candidate_dir, public_key=public_key, require_signature=True)
    except (SnapshotBuildError, OSError) as exc:
        raise SnapshotPromotionError(f"Candidate verification failed: {exc}") from exc
    manifest = _load_manifest(candidate_dir)
    _require(manifest.get("catalogRole") == IDENTITY_ONLY_CATALOG_ROLE, "Candidate catalog role must be IDENTITY_ONLY")
    region_id = manifest.get("regionId")
    _require(region_id in SUPPORTED_METRO_REGIONS, f"Candidate region is outside the Canada-first metro scope: {region_id}")
    if expected_region_id is not None:
        _require(region_id == expected_region_id, f"Candidate region {region_id} does not match expected region {expected_region_id}")
    generated_at = manifest.get("generatedAtEpochMillis")
    _require(isinstance(generated_at, int) and generated_at > 0, "Candidate generated time is invalid")
    _require(generated_at <= evaluated_at_epoch_millis, "Candidate snapshot is future-dated")
    _require(evaluated_at_epoch_millis - generated_at <= maximum_snapshot_age_millis, "Candidate snapshot is expired")
    coverage = manifest.get("coverage")
    _require(isinstance(coverage, dict), "Candidate coverage metadata is missing")
    records = coverage.get("catalogRecordCount")
    _require(isinstance(records, int) and minimum_catalog_records <= records <= maximum_catalog_records, f"Candidate catalog coverage must be between {minimum_catalog_records} and {maximum_catalog_records} records")
    _require(coverage.get("currentOfferRecordCount") == 0, "Identity-only candidate cannot contain current offers")
    _require(coverage.get("currentOfferCoverage") == NO_CURRENT_OFFERS_STATUS, "Candidate current-offer coverage status is invalid")

    state_root.mkdir(parents=True, exist_ok=True)
    current_path = state_root / CURRENT_POINTER_NAME
    last_known_good_path = state_root / LAST_KNOWN_GOOD_POINTER_NAME
    current: dict[str, Any] | None = None
    if current_path.exists():
        current, _ = _verify_existing_pointer(current_path, state_root, public_key)
        current_generated = current.get("generatedAtEpochMillis")
        _require(isinstance(current_generated, int), "Current pointer generated time is invalid")
        if generated_at < current_generated:
            raise SnapshotPromotionError("Candidate snapshot is older than the active snapshot")
        if generated_at == current_generated and current.get("manifestSha256") != verification["manifestSha256"]:
            raise SnapshotPromotionError("Candidate snapshot conflicts with the active generation")
    if last_known_good_path.exists():
        _verify_existing_pointer(last_known_good_path, state_root, public_key)

    payload = _pointer_payload(candidate_dir, state_root, manifest, verification)
    if current is not None and current.get("manifestSha256") == payload["manifestSha256"]:
        return {"promoted": False, "reason": "already_current", **payload}

    _write_pointer_atomically(current_path, payload)
    _write_pointer_atomically(last_known_good_path, payload)
    return {"promoted": True, **payload}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--state-root", required=True, type=Path)
    parser.add_argument("--public-key", required=True, type=Path)
    parser.add_argument("--evaluated-at", required=True, help="ISO-8601 timestamp with timezone")
    parser.add_argument("--maximum-age-millis", required=True, type=int)
    parser.add_argument("--expected-region")
    parser.add_argument("--minimum-records", type=int, default=MIN_CATALOG_RECORDS)
    parser.add_argument("--maximum-records", type=int, default=MAX_CATALOG_RECORDS)
    args = parser.parse_args(argv)
    try:
        evaluated_at = _parse_timestamp(args.evaluated_at, "--evaluated-at")
        result = promote_snapshot(
            args.candidate,
            args.state_root,
            args.public_key,
            evaluated_at_epoch_millis=evaluated_at,
            maximum_snapshot_age_millis=args.maximum_age_millis,
            expected_region_id=args.expected_region,
            minimum_catalog_records=args.minimum_records,
            maximum_catalog_records=args.maximum_records,
        )
    except (SnapshotPromotionError, SnapshotBuildError, OSError) as exc:
        print(f"snapshot promotion failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

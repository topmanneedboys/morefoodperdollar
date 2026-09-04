#!/usr/bin/env python3
"""Atomically promote one complete offline catalog release.

The regional snapshot directories are immutable, signed artifacts.  This
module assembles their references into one immutable release record and then
atomically replaces a single root-level active-generation pointer.  A process
crash before the pointer replace therefore leaves the previous release active;
an orphaned release record is never authoritative.  The pointer also embeds
the previous complete release as a last-known-good fallback so a damaged
active artifact can be recovered without mutating several regional pointers.

No network, clock, price, availability, or ranking authority is introduced.
All timestamps and candidate paths are supplied by the caller.
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

from tools.build_offline_catalog_snapshot import (
    IDENTITY_ONLY_CATALOG_ROLE,
    NO_CURRENT_OFFERS_STATUS,
    _canonical_json,
)
from tools.verify_offline_catalog_snapshot import SnapshotBuildError, verify_snapshot


RELEASE_SCHEMA_VERSION = 1
ACTIVE_GENERATION_POINTER_NAME = "active-generation.json"
GENERATION_DIRECTORY_NAME = "generations"
SUPPORTED_METRO_REGIONS = frozenset({"ca-gta", "ca-metro-vancouver"})


class CatalogReleasePromotionError(ValueError):
    """A complete offline catalog release cannot become active."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise CatalogReleasePromotionError(message)


def _canonical_mapping(value: Mapping[str, Any]) -> bytes:
    return _canonical_json(dict(value))


def _read_canonical_json(path: Path, label: str) -> Mapping[str, Any]:
    _require(path.is_file(), f"Missing {label}: {path}")
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise CatalogReleasePromotionError(f"{label} is not valid JSON: {path}") from exc
    _require(isinstance(value, dict), f"{label} must be an object: {path}")
    _require(raw == _canonical_mapping(value), f"{label} is not canonical JSON: {path}")
    return value


def _write_bytes_atomically(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary = Path(handle.name)
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def _write_json_atomically(path: Path, value: Mapping[str, Any]) -> None:
    _write_bytes_atomically(path, _canonical_mapping(value))


def _relative_path(path: Path, state_root: Path, label: str) -> str:
    resolved_path = path.resolve()
    resolved_root = state_root.resolve()
    _require(resolved_path != resolved_root, f"{label} must not be the promotion state root")
    try:
        relative = resolved_path.relative_to(resolved_root)
    except ValueError as exc:
        raise CatalogReleasePromotionError(f"{label} must be inside the promotion state root") from exc
    return relative.as_posix()


def _region_reference(
    candidate: Mapping[str, Any],
    *,
    state_root: Path,
    public_key: Path,
    evaluated_at_epoch_millis: int,
    maximum_snapshot_age_millis: int,
    minimum_catalog_records: int,
    maximum_catalog_records: int,
) -> dict[str, Any]:
    region_id = candidate.get("regionId")
    _require(isinstance(region_id, str) and region_id in SUPPORTED_METRO_REGIONS, f"Unsupported candidate region: {region_id}")
    candidate_path_value = candidate.get("candidatePath")
    _require(isinstance(candidate_path_value, str) and candidate_path_value.strip() == candidate_path_value and candidate_path_value, f"Candidate path is invalid for {region_id}")
    candidate_path = Path(candidate_path_value).resolve()
    _require(candidate_path.is_dir(), f"Missing candidate snapshot for {region_id}: {candidate_path}")
    try:
        verification = verify_snapshot(candidate_path, public_key=public_key, require_signature=True)
    except (SnapshotBuildError, OSError) as exc:
        raise CatalogReleasePromotionError(f"Candidate verification failed for {region_id}: {exc}") from exc
    manifest = _read_canonical_json(candidate_path / "manifest.json", f"Candidate manifest for {region_id}")
    _require(manifest.get("catalogRole") == IDENTITY_ONLY_CATALOG_ROLE, f"Candidate catalog role is invalid for {region_id}")
    _require(manifest.get("regionId") == region_id, f"Candidate region mismatch for {region_id}")
    generated_at = manifest.get("generatedAtEpochMillis")
    _require(isinstance(generated_at, int) and generated_at > 0, f"Candidate generated time is invalid for {region_id}")
    _require(generated_at <= evaluated_at_epoch_millis, f"Candidate is future-dated for {region_id}")
    _require(
        evaluated_at_epoch_millis - generated_at <= maximum_snapshot_age_millis,
        f"Candidate is expired for {region_id}",
    )
    coverage = manifest.get("coverage")
    _require(isinstance(coverage, Mapping), f"Candidate coverage is missing for {region_id}")
    record_count = coverage.get("catalogRecordCount")
    _require(
        isinstance(record_count, int) and minimum_catalog_records <= record_count <= maximum_catalog_records,
        f"Candidate catalog coverage is outside bounds for {region_id}",
    )
    _require(coverage.get("currentOfferRecordCount") == 0, f"Identity-only candidate contains offers for {region_id}")
    _require(coverage.get("currentOfferCoverage") == NO_CURRENT_OFFERS_STATUS, f"Candidate offer status is invalid for {region_id}")
    sources = manifest.get("sources")
    _require(isinstance(sources, list) and sources, f"Candidate sources are missing for {region_id}")
    identity_catalog_key = _identity_catalog_key(sources)
    return {
        "regionId": region_id,
        "snapshotId": manifest.get("snapshotId"),
        "manifestSha256": verification["manifestSha256"],
        "catalogRecordCount": record_count,
        "identityCatalogKey": identity_catalog_key,
        "currentOfferRecordCount": 0,
        "currentOfferCoverage": NO_CURRENT_OFFERS_STATUS,
        "snapshotPath": _relative_path(candidate_path, state_root, f"Candidate snapshot for {region_id}"),
        "generatedAtEpochMillis": generated_at,
    }


def _validate_region_reference(
    reference: Mapping[str, Any],
    *,
    state_root: Path,
    public_key: Path,
) -> dict[str, Any]:
    region_id = reference.get("regionId")
    _require(isinstance(region_id, str) and region_id in SUPPORTED_METRO_REGIONS, f"Invalid release region: {region_id}")
    relative_path = reference.get("snapshotPath")
    _require(isinstance(relative_path, str) and relative_path.strip() == relative_path and relative_path, f"Invalid release snapshot path for {region_id}")
    snapshot_path = (state_root.resolve() / Path(relative_path)).resolve()
    _relative_path(snapshot_path, state_root, f"Release snapshot for {region_id}")
    _require(snapshot_path.is_dir(), f"Release snapshot target is missing for {region_id}: {relative_path}")
    try:
        verification = verify_snapshot(snapshot_path, public_key=public_key, require_signature=True)
    except (SnapshotBuildError, OSError) as exc:
        raise CatalogReleasePromotionError(f"Release snapshot verification failed for {region_id}: {exc}") from exc
    manifest = _read_canonical_json(snapshot_path / "manifest.json", f"Release manifest for {region_id}")
    coverage = manifest.get("coverage")
    _require(isinstance(coverage, Mapping), f"Release coverage is missing for {region_id}")
    sources = manifest.get("sources")
    _require(isinstance(sources, list) and sources, f"Release sources are missing for {region_id}")
    expected = {
        "regionId": region_id,
        "snapshotId": manifest.get("snapshotId"),
        "manifestSha256": verification["manifestSha256"],
        "catalogRecordCount": coverage.get("catalogRecordCount"),
        "identityCatalogKey": _identity_catalog_key(sources),
        "currentOfferRecordCount": 0,
        "currentOfferCoverage": NO_CURRENT_OFFERS_STATUS,
        "snapshotPath": relative_path,
        "generatedAtEpochMillis": manifest.get("generatedAtEpochMillis"),
    }
    _require(dict(reference) == expected, f"Release reference metadata mismatch for {region_id}")
    return expected


def _identity_catalog_key(sources: Sequence[Any]) -> str:
    """Return a stable key for one shared identity source set.

    Regional manifests may deliberately reference the same national identity
    catalog.  The source content hashes, namespaces and record counts are the
    signed facts that identify that shared catalog; region, store and offer
    metadata are not part of this key.
    """

    normalized: list[dict[str, Any]] = []
    for source in sources:
        _require(isinstance(source, Mapping), "Catalog source metadata is invalid")
        namespace = source.get("namespace")
        snapshot = source.get("snapshot")
        content_hash = source.get("contentSha256")
        record_count = source.get("recordCount")
        _require(isinstance(namespace, Mapping), "Catalog source namespace is missing")
        _require(isinstance(snapshot, Mapping), "Catalog source snapshot is missing")
        _require(isinstance(namespace.get("id"), str) and namespace["id"], "Catalog source namespace id is invalid")
        _require(isinstance(snapshot.get("providerId"), str) and snapshot["providerId"], "Catalog source provider id is invalid")
        _require(isinstance(content_hash, str) and len(content_hash) == 64, "Catalog source content hash is invalid")
        _require(isinstance(record_count, int) and record_count >= 0, "Catalog source record count is invalid")
        normalized.append(
            {
                "namespaceId": namespace["id"],
                "providerId": snapshot["providerId"],
                "contentSha256": content_hash,
                "recordCount": record_count,
            }
        )
    normalized.sort(key=lambda item: (item["namespaceId"], item["providerId"], item["contentSha256"]))
    return hashlib.sha256(_canonical_mapping({"sources": normalized})).hexdigest()


def _deduplicated_catalog_record_count(regions: Sequence[Mapping[str, Any]]) -> int:
    """Count shared identity sources once while retaining regional references."""

    return sum(
        max(
            reference["catalogRecordCount"]
            for reference in regions
            if reference["identityCatalogKey"] == key
        )
        for key in {reference["identityCatalogKey"] for reference in regions}
    )


def _validate_generation(
    generation: Mapping[str, Any],
    *,
    state_root: Path,
    public_key: Path,
) -> dict[str, Any]:
    _require(generation.get("schemaVersion") == RELEASE_SCHEMA_VERSION, "Unsupported catalog release schema")
    active = generation.get("release")
    _require(isinstance(active, Mapping), "Catalog release record is missing release metadata")
    _require(active.get("schemaVersion") == RELEASE_SCHEMA_VERSION, "Catalog release metadata schema is invalid")
    generation_id = active.get("generationId")
    _require(isinstance(generation_id, str) and generation_id.startswith("generation-"), "Catalog release generation id is invalid")
    regions = active.get("regions")
    _require(isinstance(regions, list) and regions, "Catalog release has no regions")
    normalized_regions: list[dict[str, Any]] = []
    seen_regions: set[str] = set()
    for reference in regions:
        _require(isinstance(reference, Mapping), "Catalog release region reference is invalid")
        normalized = _validate_region_reference(reference, state_root=state_root, public_key=public_key)
        _require(normalized["regionId"] not in seen_regions, f"Catalog release contains duplicate region: {normalized['regionId']}")
        seen_regions.add(normalized["regionId"])
        normalized_regions.append(normalized)
    normalized_regions.sort(key=lambda item: item["regionId"])
    _require(regions == normalized_regions, "Catalog release regions are not canonical")
    total_records = _deduplicated_catalog_record_count(normalized_regions)
    _require(active.get("catalogRecordCount") == total_records, "Catalog release coverage count mismatch")
    _require(active.get("currentOfferRecordCount") == 0, "Catalog release contains current offers")
    _require(active.get("currentOfferCoverage") == NO_CURRENT_OFFERS_STATUS, "Catalog release offer status is invalid")
    generated_at = active.get("generatedAtEpochMillis")
    _require(isinstance(generated_at, int) and generated_at > 0, "Catalog release generated time is invalid")
    _require(active.get("generationPath") == generation.get("generationPath"), "Catalog release path mismatch")
    return dict(active)


def _load_generation_file(
    state_root: Path,
    relative_path: str,
    *,
    public_key: Path,
) -> dict[str, Any]:
    path = (state_root.resolve() / Path(relative_path)).resolve()
    _relative_path(path, state_root, "Catalog release generation")
    generation = _read_canonical_json(path, "Catalog release generation")
    _require(generation.get("generationPath") == relative_path, "Catalog release generation path is invalid")
    _validate_generation(generation, state_root=state_root, public_key=public_key)
    return dict(generation)


def _load_pointer(state_root: Path, public_key: Path) -> tuple[dict[str, Any], dict[str, Any] | None]:
    pointer_path = state_root / ACTIVE_GENERATION_POINTER_NAME
    if not pointer_path.exists():
        return {}, None
    pointer = dict(_read_canonical_json(pointer_path, "Active catalog generation pointer"))
    _require(pointer.get("schemaVersion") == RELEASE_SCHEMA_VERSION, "Unsupported active generation pointer schema")
    active = pointer.get("activeGeneration")
    last_good = pointer.get("lastKnownGoodGeneration")
    _require(isinstance(active, Mapping), "Active generation pointer is missing activeGeneration")
    if last_good is not None:
        _require(isinstance(last_good, Mapping), "Active generation pointer lastKnownGoodGeneration is invalid")

    def resolve(value: Mapping[str, Any]) -> dict[str, Any]:
        relative_path = value.get("generationPath")
        _require(isinstance(relative_path, str) and relative_path, "Active generation path is invalid")
        generation = _load_generation_file(state_root, relative_path, public_key=public_key)
        _require(generation.get("release") == dict(value), "Active generation pointer metadata mismatch")
        return generation

    try:
        return pointer, resolve(active)
    except CatalogReleasePromotionError:
        if last_good is None:
            raise
        return pointer, resolve(last_good)


def _generation_id(active: Mapping[str, Any]) -> str:
    material = dict(active)
    material.pop("generationId", None)
    material.pop("generationPath", None)
    return "generation-" + hashlib.sha256(_canonical_mapping(material)).hexdigest()


def promote_release(
    candidates: Sequence[Mapping[str, Any]],
    state_root: Path,
    public_key: Path,
    *,
    generated_at_epoch_millis: int,
    evaluated_at_epoch_millis: int,
    maximum_snapshot_age_millis: int,
    minimum_catalog_records: int,
    maximum_catalog_records: int,
    coverage_report: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    """Promote a complete multi-region release with one atomic pointer write."""

    state_root = state_root.resolve()
    public_key = public_key.resolve()
    _require(candidates, "At least one catalog release candidate is required")
    _require(generated_at_epoch_millis > 0, "generated_at_epoch_millis must be positive")
    _require(evaluated_at_epoch_millis >= generated_at_epoch_millis, "evaluated_at_epoch_millis must not precede generation")
    _require(maximum_snapshot_age_millis > 0, "maximum_snapshot_age_millis must be positive")
    _require(1 <= minimum_catalog_records <= maximum_catalog_records, "Catalog coverage bounds are invalid")
    _require(public_key.is_file(), f"Missing signature public key: {public_key}")
    state_root.mkdir(parents=True, exist_ok=True)

    _, previous_generation_file = _load_pointer(state_root, public_key)
    previous_release: dict[str, Any] | None = None
    if previous_generation_file is not None:
        previous_release = dict(previous_generation_file["release"])
        previous_generated_at = previous_release.get("generatedAtEpochMillis")
        _require(isinstance(previous_generated_at, int), "Previous catalog release generated time is invalid")
        _require(generated_at_epoch_millis >= previous_generated_at, "Catalog release is older than the active generation")

    candidate_refs = [
        _region_reference(
            candidate,
            state_root=state_root,
            public_key=public_key,
            evaluated_at_epoch_millis=evaluated_at_epoch_millis,
            maximum_snapshot_age_millis=maximum_snapshot_age_millis,
            minimum_catalog_records=minimum_catalog_records,
            maximum_catalog_records=maximum_catalog_records,
        )
        for candidate in candidates
    ]
    candidate_refs.sort(key=lambda item: item["regionId"])
    _require(len({item["regionId"] for item in candidate_refs}) == len(candidate_refs), "Duplicate candidate region")

    merged: dict[str, dict[str, Any]] = {}
    if previous_release is not None:
        for reference in previous_release["regions"]:
            merged[reference["regionId"]] = dict(reference)
    merged.update({reference["regionId"]: reference for reference in candidate_refs})
    regions = [merged[key] for key in sorted(merged)]
    active: dict[str, Any] = {
        "schemaVersion": RELEASE_SCHEMA_VERSION,
        "generationId": "pending",
        "generatedAtEpochMillis": generated_at_epoch_millis,
        "catalogRecordCount": _deduplicated_catalog_record_count(regions),
        "currentOfferRecordCount": 0,
        "currentOfferCoverage": NO_CURRENT_OFFERS_STATUS,
        "regions": regions,
    }
    active["generationId"] = _generation_id(active)
    generation_path = f"{GENERATION_DIRECTORY_NAME}/{active['generationId']}.json"
    active["generationPath"] = generation_path
    generation = {
        "schemaVersion": RELEASE_SCHEMA_VERSION,
        "generationPath": generation_path,
        "release": active,
    }
    if coverage_report is not None:
        _require(isinstance(coverage_report, Mapping), "Coverage report must be an object")
        generation["coverageReport"] = dict(coverage_report)

    generation_file = state_root / generation_path
    if generation_file.exists():
        existing = _read_canonical_json(generation_file, "Existing catalog release generation")
        _require(existing == generation, "Catalog release generation id already exists with different content")
    else:
        _write_json_atomically(generation_file, generation)

    pointer = {
        "schemaVersion": RELEASE_SCHEMA_VERSION,
        "activeGeneration": active,
        "lastKnownGoodGeneration": previous_release,
    }
    pointer_path = state_root / ACTIVE_GENERATION_POINTER_NAME
    if previous_release is not None and previous_release == active:
        return {
            "promoted": False,
            "reason": "already_current",
            "activeGeneration": active,
            "activeGenerationPath": pointer_path.as_posix(),
        }
    _write_json_atomically(pointer_path, pointer)
    return {
        "promoted": True,
        "activeGeneration": active,
        "activeGenerationPath": pointer_path.as_posix(),
    }


def read_active_release(state_root: Path, public_key: Path) -> dict[str, Any]:
    """Validate and return the active release, falling back to last-known-good."""

    _, generation = _load_pointer(state_root.resolve(), public_key.resolve())
    _require(generation is not None, "No active catalog release is available")
    return dict(generation["release"])

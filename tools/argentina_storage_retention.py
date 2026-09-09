"""Bounded, operator-only retention and garbage collection for M9 releases.

This module owns storage lifecycle only.  It never parses SEPA data, changes a
release manifest, changes M6/M7/M8 semantics, or exposes a consumer endpoint.
It reuses the M9 content-addressed store and single-writer lock, and treats a
release manifest as a reachability root only after the manifest and every
referenced object have been verified.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

try:
    from tools.argentina_daily_release import (
        ContentAddressedStore,
        DailyReleaseError,
        SingleWriterLock,
        _read_json,
        _write_atomic,
        _write_json,
        canonical_bytes,
        sha256_file,
    )
    from backend.object_store import ObjectStoreError
except ModuleNotFoundError:  # direct invocation from tools/
    from argentina_daily_release import (  # type: ignore
        ContentAddressedStore,
        DailyReleaseError,
        SingleWriterLock,
        _read_json,
        _write_atomic,
        _write_json,
        canonical_bytes,
        sha256_file,
    )
    from backend.object_store import ObjectStoreError  # type: ignore


SCHEMA_VERSION = "valuepilot-argentina-storage-retention-v1"
LEDGER_SCHEMA_VERSION = "valuepilot-release-ledger-v1"
POLICY_VERSION = "argentina-storage-retention-policy-v1"
DEFAULT_RETENTION_COUNT = 7
MIN_RETENTION_COUNT = 2
MAX_RETENTION_COUNT = 14
DEFAULT_GRACE_SECONDS = 24 * 60 * 60
MINIMUM_SAFE_RELEASES = 2
ACTIVE_POINTER_SCHEMA = "valuepilot-active-release-v1"

ACTIVE = "ACTIVE"
ROLLBACK_PROTECTED = "ROLLBACK_PROTECTED"
RETAINED = "RETAINED"
GC_ELIGIBLE = "GC_ELIGIBLE"
GC_COMPLETED = "GC_COMPLETED"

_HEX64 = set("0123456789abcdef")


class RetentionError(RuntimeError):
    """A deterministic storage-lifecycle failure."""

    def __init__(self, message: str, *, code: str = "RETENTION_FAILED", details: Mapping[str, Any] | None = None):
        super().__init__(message)
        self.code = code
        self.details = dict(details or {})

    @property
    def exit_code(self) -> int:
        return {
            "RETENTION_FAILED": 20,
            "STORAGE_BUDGET_EXCEEDED": 21,
            "CONCURRENT_MUTATOR": 22,
            "GC_DELETE_FAILED": 23,
            "POST_GC_VERIFY_FAILED": 24,
            "RELEASE_DATA_NOT_RETAINED": 25,
        }.get(self.code, 20)


@dataclass(frozen=True)
class RetentionPolicy:
    retention_count: int = DEFAULT_RETENTION_COUNT
    grace_seconds: int = DEFAULT_GRACE_SECONDS
    budget_bytes: int | None = None
    minimum_releases: int = MINIMUM_SAFE_RELEASES

    def __post_init__(self) -> None:
        if not MIN_RETENTION_COUNT <= self.retention_count <= MAX_RETENTION_COUNT:
            raise RetentionError(
                f"retention count must be between {MIN_RETENTION_COUNT} and {MAX_RETENTION_COUNT}",
                code="RETENTION_POLICY_INVALID",
            )
        if self.grace_seconds < 0:
            raise RetentionError("grace period cannot be negative", code="RETENTION_POLICY_INVALID")
        if self.budget_bytes is not None and self.budget_bytes < 0:
            raise RetentionError("storage budget cannot be negative", code="RETENTION_POLICY_INVALID")
        if self.minimum_releases < MINIMUM_SAFE_RELEASES:
            raise RetentionError("minimum release safety window cannot be reduced below two", code="RETENTION_POLICY_INVALID")

    def as_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "retentionCount": self.retention_count,
            "graceSeconds": self.grace_seconds,
            "budgetBytes": self.budget_bytes,
            "minimumReleases": self.minimum_releases,
        }


def _parse_instant(value: str) -> _dt.datetime:
    if not isinstance(value, str) or not value:
        raise RetentionError("evaluation time is required", code="RETENTION_POLICY_INVALID")
    normalized = value.replace("Z", "+00:00")
    try:
        parsed = _dt.datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise RetentionError("evaluation time must be ISO-8601", code="RETENTION_POLICY_INVALID") from exc
    if parsed.tzinfo is None:
        raise RetentionError("evaluation time must include a timezone", code="RETENTION_POLICY_INVALID")
    return parsed.astimezone(_dt.timezone.utc)


def _instant_text(value: _dt.datetime) -> str:
    return value.astimezone(_dt.timezone.utc).isoformat().replace("+00:00", "Z")


def _release_id(value: Any) -> str:
    if not isinstance(value, str) or not value or len(value) > 128 or any(ch not in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-" for ch in value):
        raise RetentionError("release ID is invalid", code="RETENTION_FAILED")
    return value


def _digest(value: str) -> bool:
    return isinstance(value, str) and len(value) == 64 and set(value.lower()) <= _HEX64


def _safe_int(value: Any, *, field: str) -> int:
    if isinstance(value, bool):
        raise RetentionError(f"{field} is invalid", code="RETENTION_FAILED")
    try:
        result = int(value)
    except (TypeError, ValueError) as exc:
        raise RetentionError(f"{field} is invalid", code="RETENTION_FAILED") from exc
    if result < 0:
        raise RetentionError(f"{field} is invalid", code="RETENTION_FAILED")
    return result


def _source_date(manifest: Mapping[str, Any]) -> str:
    source = manifest.get("source")
    date = source.get("releaseDate") if isinstance(source, Mapping) else None
    if not isinstance(date, str):
        raise RetentionError("release source date is missing", code="RETENTION_FAILED")
    try:
        return _dt.date.fromisoformat(date).isoformat()
    except ValueError as exc:
        raise RetentionError("release source date is invalid", code="RETENTION_FAILED") from exc


def _source_sha(manifest: Mapping[str, Any]) -> str:
    source = manifest.get("source")
    value = source.get("sha256") if isinstance(source, Mapping) else None
    if not _digest(value):
        raise RetentionError("release source hash is invalid", code="RETENTION_FAILED")
    return str(value)


def _manifest_counts(manifest: Mapping[str, Any]) -> dict[str, int]:
    counts = manifest.get("backendCounts")
    if not isinstance(counts, Mapping):
        return {}
    return {str(key): _safe_int(value, field=f"backendCounts.{key}") for key, value in counts.items() if isinstance(key, str)}


@dataclass(frozen=True)
class ReleaseRecord:
    release_id: str
    source_date: str
    source_sha256: str
    manifest_sha256: str | None
    qualification_status: str
    backend_counts: Mapping[str, int]
    data_present: bool
    lifecycle_state: str
    full_manifest: Mapping[str, Any] | None
    metadata: Mapping[str, Any]

    def compact_metadata(self, *, state: str | None = None, data_present: bool | None = None, gc_completed_at: str | None = None) -> dict[str, Any]:
        value = {
            "schemaVersion": "valuepilot-release-metadata-v1",
            "releaseId": self.release_id,
            "sourceDate": self.source_date,
            "sourceSha256": self.source_sha256,
            "manifestSha256": self.manifest_sha256,
            "qualificationStatus": self.qualification_status,
            "backendCounts": dict(self.backend_counts),
            "dataPresent": self.data_present if data_present is None else data_present,
            "lifecycleState": state or self.lifecycle_state,
        }
        if gc_completed_at is not None:
            value["gcCompletedAt"] = gc_completed_at
        return value


@dataclass(frozen=True)
class RetentionPlan:
    evaluation_at: str
    policy: Mapping[str, Any]
    active_release_id: str
    previous_release_id: str | None
    protected_release_ids: tuple[str, ...]
    grace_protected_release_ids: tuple[str, ...]
    eligible_release_ids: tuple[str, ...]
    metadata_only_release_ids: tuple[str, ...]
    release_states: Mapping[str, str]
    reachable_objects: tuple[str, ...]
    unreachable_objects: tuple[Mapping[str, Any], ...]
    accounting: Mapping[str, Any]
    budget: Mapping[str, Any]
    mutation_required: bool

    def as_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policy": dict(self.policy),
            "evaluationAt": self.evaluation_at,
            "activeReleaseId": self.active_release_id,
            "previousReleaseId": self.previous_release_id,
            "protectedReleaseIds": list(self.protected_release_ids),
            "graceProtectedReleaseIds": list(self.grace_protected_release_ids),
            "eligibleReleaseIds": list(self.eligible_release_ids),
            "metadataOnlyReleaseIds": list(self.metadata_only_release_ids),
            "releaseStates": dict(self.release_states),
            "reachableObjectCount": len(self.reachable_objects),
            "reachableObjectSample": list(self.reachable_objects[:16]),
            "unreachableObjects": [dict(item) for item in self.unreachable_objects],
            "accounting": dict(self.accounting),
            "budget": dict(self.budget),
            "mutationRequired": self.mutation_required,
        }


class StorageRetentionManager:
    """Mark/plan/verify/sweep coordinator for one local or test store."""

    def __init__(self, workspace: Path | str, *, policy: RetentionPolicy | None = None):
        self.workspace = Path(workspace).resolve()
        self.policy = policy or RetentionPolicy()
        self.store = ContentAddressedStore(self.workspace)
        self.releases = self.workspace / "releases"
        self.control = self.workspace / "control"
        self.objects = self.workspace / "objects" / "sha256"
        self.ledger_path = self.control / "release-ledger.jsonl"
        self.pins_path = self.control / "operator-pins.json"
        self.progress_path = self.control / "gc-progress.json"

    @property
    def lock_path(self) -> Path:
        return self.control / "publisher.lock"

    def _active_pointer(self) -> dict[str, Any]:
        path = self.control / "active.json"
        if not path.is_file():
            raise RetentionError("active release pointer is missing", code="ACTIVE_MANIFEST_MISSING")
        try:
            value = _read_json(path)
        except DailyReleaseError as exc:
            raise RetentionError("active release pointer is corrupt", code="ACTIVE_MANIFEST_CORRUPT") from exc
        if not isinstance(value, Mapping) or value.get("schemaVersion") != ACTIVE_POINTER_SCHEMA:
            raise RetentionError("active release pointer is invalid", code="ACTIVE_MANIFEST_CORRUPT")
        value = dict(value)
        value["releaseId"] = _release_id(value.get("releaseId"))
        previous = value.get("previousReleaseId")
        value["previousReleaseId"] = _release_id(previous) if previous is not None else None
        last_good = value.get("lastKnownGoodReleaseId")
        value["lastKnownGoodReleaseId"] = _release_id(last_good) if last_good is not None else None
        return value

    def _manifest_path(self, release_id: str) -> Path:
        return self.releases / _release_id(release_id) / "manifest.json"

    def _metadata_path(self, release_id: str) -> Path:
        return self.releases / _release_id(release_id) / "metadata.json"

    def _state_path(self, release_id: str) -> Path:
        return self.releases / _release_id(release_id) / "retention-state.json"

    def _read_full_manifest(self, release_id: str, *, protected: bool = False) -> tuple[dict[str, Any], str]:
        release_id = _release_id(release_id)
        path = self._manifest_path(release_id)
        if not path.is_file():
            code = "PROTECTED_RELEASE_DATA_MISSING" if protected else "RELEASE_DATA_NOT_RETAINED"
            raise RetentionError(f"full manifest is missing for {release_id}", code=code)
        try:
            value = _read_json(path)
        except DailyReleaseError as exc:
            code = "PREVIOUS_MANIFEST_CORRUPT" if protected else "RETENTION_FAILED"
            raise RetentionError(f"full manifest is corrupt for {release_id}", code=code) from exc
        if not isinstance(value, dict) or value.get("completionState") != "COMPLETE" or value.get("releaseId") != release_id:
            code = "PREVIOUS_MANIFEST_CORRUPT" if protected else "RETENTION_FAILED"
            raise RetentionError(f"full manifest is incomplete for {release_id}", code=code)
        objects = value.get("objects")
        if not isinstance(objects, list):
            raise RetentionError(f"object list is missing for {release_id}", code="RETENTION_FAILED")
        sha_file = path.with_name("manifest.sha256")
        if not sha_file.is_file():
            raise RetentionError(f"manifest checksum is missing for {release_id}", code="RETENTION_FAILED")
        try:
            expected = sha256_file(path)
            recorded = sha_file.read_text(encoding="ascii", errors="strict").split()[0]
        except (OSError, UnicodeError, IndexError) as exc:
            raise RetentionError(f"manifest checksum is corrupt for {release_id}", code="RETENTION_FAILED") from exc
        if recorded != expected:
            raise RetentionError(f"manifest checksum is invalid for {release_id}", code="RETENTION_FAILED")
        return value, sha256_file(path)

    def _verify_manifest_objects(self, release_id: str, manifest: Mapping[str, Any]) -> tuple[str, ...]:
        reachable: list[str] = []
        objects = manifest.get("objects")
        if not isinstance(objects, list):
            raise RetentionError(f"object list is missing for {release_id}", code="RETENTION_FAILED")
        for descriptor in objects:
            if not isinstance(descriptor, Mapping) or not _digest(descriptor.get("sha256")):
                raise RetentionError(f"unknown object reference in {release_id}", code="UNKNOWN_OBJECT_REFERENCE")
            digest = str(descriptor["sha256"])
            try:
                metadata = self.store.object_store.head(digest)
            except ObjectStoreError as exc:
                raise RetentionError(f"object {digest} referenced by {release_id} is missing", code="UNKNOWN_OBJECT_REFERENCE") from exc
            expected_size = _safe_int(descriptor.get("bytes", -1), field="object.bytes")
            if metadata.size != expected_size or metadata.sha256 != digest:
                raise RetentionError(f"object {digest} referenced by {release_id} is corrupt", code="UNKNOWN_OBJECT_REFERENCE")
            reachable.append(digest)
        return tuple(sorted(set(reachable)))

    def _state(self, release_id: str) -> dict[str, Any]:
        path = self._state_path(release_id)
        if not path.is_file():
            return {"schemaVersion": "valuepilot-release-retention-state-v1", "releaseId": release_id, "lifecycleState": RETAINED}
        try:
            value = _read_json(path)
        except DailyReleaseError as exc:
            raise RetentionError(f"retention state is corrupt for {release_id}", code="RETENTION_FAILED") from exc
        if not isinstance(value, dict) or value.get("releaseId") != release_id:
            raise RetentionError(f"retention state is invalid for {release_id}", code="RETENTION_FAILED")
        return value

    def _record(self, release_id: str, manifest: Mapping[str, Any] | None, manifest_sha: str | None, *, data_present: bool, state: str, metadata: Mapping[str, Any] | None = None) -> ReleaseRecord:
        if manifest is not None:
            source_date = _source_date(manifest)
            source_sha = _source_sha(manifest)
            status = str(manifest.get("qualificationStatus") or "UNKNOWN")
            counts = _manifest_counts(manifest)
            supplied = dict(metadata or {})
            supplied.setdefault("schemaVersion", "valuepilot-release-metadata-v1")
            supplied.setdefault("releaseId", release_id)
            supplied.setdefault("dataPresent", data_present)
            supplied.setdefault("lifecycleState", state)
        else:
            supplied = dict(metadata or {})
            source_date = str(supplied.get("sourceDate") or "")
            source_sha = str(supplied.get("sourceSha256") or "")
            status = str(supplied.get("qualificationStatus") or "UNKNOWN")
            counts = {str(key): _safe_int(value, field=f"backendCounts.{key}") for key, value in (supplied.get("backendCounts") or {}).items()}
            if not source_date or not _digest(source_sha):
                raise RetentionError(f"historical metadata is invalid for {release_id}", code="RETENTION_FAILED")
        return ReleaseRecord(release_id, source_date, source_sha, manifest_sha, status, counts, data_present, state, manifest, supplied)

    def _records(self) -> list[ReleaseRecord]:
        self.releases.mkdir(parents=True, exist_ok=True)
        records: list[ReleaseRecord] = []
        for directory in sorted(self.releases.iterdir()):
            if not directory.is_dir():
                continue
            release_id = _release_id(directory.name)
            manifest_path = directory / "manifest.json"
            metadata_path = directory / "metadata.json"
            state = self._state(release_id)
            lifecycle = str(state.get("lifecycleState") or RETAINED)
            if manifest_path.is_file():
                manifest, manifest_sha = self._read_full_manifest(release_id)
                records.append(self._record(release_id, manifest, manifest_sha, data_present=True, state=lifecycle, metadata=state))
            elif metadata_path.is_file():
                metadata = _read_json(metadata_path)
                if not isinstance(metadata, Mapping) or metadata.get("dataPresent") is not False:
                    raise RetentionError(f"historical metadata is invalid for {release_id}", code="RETENTION_FAILED")
                metadata_sha = directory / "metadata.sha256"
                if not metadata_sha.is_file():
                    raise RetentionError(f"historical metadata checksum is missing for {release_id}", code="RETENTION_FAILED")
                try:
                    recorded = metadata_sha.read_text(encoding="ascii", errors="strict").split()[0]
                except (OSError, UnicodeError, IndexError) as exc:
                    raise RetentionError(f"historical metadata checksum is corrupt for {release_id}", code="RETENTION_FAILED") from exc
                if recorded != sha256_file(metadata_path):
                    raise RetentionError(f"historical metadata checksum is invalid for {release_id}", code="RETENTION_FAILED")
                records.append(self._record(release_id, None, str(metadata.get("manifestSha256")) if metadata.get("manifestSha256") else None, data_present=False, state=str(metadata.get("lifecycleState") or GC_COMPLETED), metadata=metadata))
            else:
                raise RetentionError(f"release directory has no manifest or metadata: {release_id}", code="RETENTION_FAILED")
        return sorted(records, key=lambda item: (item.source_date, item.release_id))

    def _pins(self) -> tuple[str, ...]:
        if not self.pins_path.is_file():
            return ()
        try:
            value = _read_json(self.pins_path)
        except DailyReleaseError as exc:
            raise RetentionError("operator pin file is corrupt", code="RETENTION_FAILED") from exc
        pins = value.get("releaseIds") if isinstance(value, Mapping) else None
        if not isinstance(pins, list) or any(not isinstance(item, str) for item in pins):
            raise RetentionError("operator pin file is invalid", code="RETENTION_FAILED")
        return tuple(sorted(set(_release_id(item) for item in pins)))

    def _all_object_files(self) -> list[tuple[str, int]]:
        self.objects.mkdir(parents=True, exist_ok=True)
        values: list[tuple[str, int]] = []
        for path in sorted(self.objects.iterdir()):
            if not path.is_file():
                continue
            if not _digest(path.name):
                raise RetentionError(f"object store entry is not a SHA-256 object: {path.name}", code="RETENTION_FAILED")
            values.append((path.name, path.stat().st_size))
        return values

    def _walk_bytes(self, root: Path, *, exclude: Iterable[Path] = ()) -> int:
        excluded = {path.resolve() for path in exclude}
        total = 0
        if not root.exists():
            return 0
        for path in root.rglob("*"):
            if path.is_file() and path.resolve() not in excluded:
                total += path.stat().st_size
        return total

    def _accounting(self, *, reachable: set[str], unreachable: Sequence[Mapping[str, Any]], eligible: Sequence[str], records: Sequence[ReleaseRecord], protected: Sequence[str]) -> dict[str, Any]:
        object_sizes = {digest: size for digest, size in self._all_object_files()}
        content_bytes = sum(object_sizes.values())
        release_files: list[Path] = []
        for record in records:
            directory = self.releases / record.release_id
            for name in ("manifest.json", "manifest.sha256", "metadata.json", "metadata.sha256", "retention-state.json"):
                path = directory / name
                if path.is_file():
                    release_files.append(path)
        manifest_bytes = sum(path.stat().st_size for path in release_files)
        ledger_bytes = self.ledger_path.stat().st_size if self.ledger_path.is_file() else 0
        control_bytes = self._walk_bytes(self.control, exclude=(self.ledger_path,))
        temporary_root = self.workspace / "temporary"
        temporary_bytes = self._walk_bytes(temporary_root)
        total_bytes = self._walk_bytes(self.workspace)
        unreachable_bytes = sum(_safe_int(item.get("bytes"), field="unreachable.bytes") for item in unreachable)
        eligible_manifest_bytes = 0
        for release_id in eligible:
            directory = self.releases / release_id
            for name in ("manifest.json", "manifest.sha256", "retention-state.json"):
                path = directory / name
                if path.is_file():
                    eligible_manifest_bytes += path.stat().st_size
        protected_object_bytes = sum(object_sizes.get(digest, 0) for digest in reachable)
        deletion_bytes = unreachable_bytes + eligible_manifest_bytes
        projected = max(0, total_bytes - deletion_bytes)
        return {
            "contentBytes": content_bytes,
            "manifestBytes": manifest_bytes,
            "controlBytes": control_bytes,
            "metadataLedgerBytes": ledger_bytes,
            "temporaryEligibleBytes": temporary_bytes,
            "protectedBytes": protected_object_bytes,
            "unreachableBytes": unreachable_bytes,
            "eligibleManifestBytes": eligible_manifest_bytes,
            "deletableBytes": deletion_bytes,
            "storageBeforeBytes": total_bytes,
            "projectedPostGcBytes": projected,
            "protectedReleaseCount": len(protected),
            "objectCount": len(object_sizes),
        }

    def _ledger_last_hash(self) -> str | None:
        if not self.ledger_path.is_file():
            return None
        last: str | None = None
        with self.ledger_path.open("rb") as handle:
            for line in handle:
                if line.strip():
                    try:
                        value = json.loads(line)
                    except json.JSONDecodeError as exc:
                        raise RetentionError("release ledger is corrupt", code="RETENTION_FAILED") from exc
                    last = value.get("eventHash") if isinstance(value, Mapping) else None
        return str(last) if last else None

    def _ledger_event(self, event: str, *, evaluation_at: str, release_id: str | None = None, data: Mapping[str, Any] | None = None) -> dict[str, Any]:
        value: dict[str, Any] = {"schemaVersion": LEDGER_SCHEMA_VERSION, "policyVersion": POLICY_VERSION, "event": event, "eventAt": evaluation_at}
        if release_id is not None:
            value["releaseId"] = release_id
        if data:
            value["data"] = dict(data)
        previous = self._ledger_last_hash()
        if previous:
            value["previousEventHash"] = previous
        value["eventHash"] = hashlib.sha256(canonical_bytes(value)).hexdigest()
        self.ledger_path.parent.mkdir(parents=True, exist_ok=True)
        with self.ledger_path.open("ab") as handle:
            handle.write(canonical_bytes(value) + b"\n")
            handle.flush()
            os.fsync(handle.fileno())
        return value

    def _write_state(self, release_id: str, *, state: str, evaluation_at: str, unreachable_since: str | None = None) -> None:
        value: dict[str, Any] = {"schemaVersion": "valuepilot-release-retention-state-v1", "releaseId": release_id, "lifecycleState": state, "updatedAt": evaluation_at}
        if unreachable_since is not None:
            value["unreachableSince"] = unreachable_since
        _write_json(self._state_path(release_id), value)

    def _bootstrap_ledger(self, records: Sequence[ReleaseRecord], *, evaluation_at: str) -> None:
        if self.ledger_path.is_file() or not records:
            return
        for record in records:
            self._ledger_event("RELEASE_DISCOVERED", evaluation_at=evaluation_at, release_id=record.release_id, data=record.compact_metadata())

    def _validate_budget(self, accounting: Mapping[str, Any], *, eligible: Sequence[str]) -> dict[str, Any]:
        budget = self.policy.budget_bytes
        projected = int(accounting["projectedPostGcBytes"])
        before = int(accounting["storageBeforeBytes"])
        if budget is None:
            return {"configured": False, "budgetBytes": None, "storageBeforeBytes": before, "projectedPostGcBytes": projected, "status": "NOT_CONFIGURED"}
        if projected > budget:
            raise RetentionError(
                "storage budget cannot be satisfied without deleting protected data",
                code="STORAGE_BUDGET_EXCEEDED",
                details={"budgetBytes": budget, "storageBeforeBytes": before, "projectedPostGcBytes": projected, "eligibleReleaseIds": list(eligible)},
            )
        return {"configured": True, "budgetBytes": budget, "storageBeforeBytes": before, "projectedPostGcBytes": projected, "status": "WITHIN_BUDGET" if before <= budget else "REQUIRES_ELIGIBLE_GC"}

    def _plan_unlocked(self, *, evaluation_at: str, mutate_grace: bool = False) -> RetentionPlan:
        now = _parse_instant(evaluation_at)
        pointer = self._active_pointer()
        records = self._records()
        by_id = {record.release_id: record for record in records}
        active_id = pointer["releaseId"]
        previous_id = pointer.get("previousReleaseId")
        if active_id not in by_id:
            raise RetentionError("active release is not present", code="ACTIVE_MANIFEST_MISSING")
        if not by_id[active_id].data_present:
            raise RetentionError("active release has no full data", code="ACTIVE_MANIFEST_MISSING")
        if previous_id is not None:
            if previous_id not in by_id or not by_id[previous_id].data_present:
                raise RetentionError("previous release is not fully retained", code="PREVIOUS_MANIFEST_CORRUPT")
        if len([record for record in records if record.data_present]) < self.policy.minimum_releases:
            raise RetentionError("fewer than two complete releases are available", code="MINIMUM_RELEASES_UNAVAILABLE")

        full_records = [record for record in records if record.data_present]
        window_ids = {record.release_id for record in full_records[-self.policy.retention_count:]}
        pinned = set(self._pins())
        unknown_pins = sorted(pinned - set(by_id))
        if unknown_pins:
            raise RetentionError("operator pin references an unknown release", code="RETENTION_FAILED", details={"releaseIds": unknown_pins})
        root_ids = set(window_ids) | {active_id}
        if previous_id:
            root_ids.add(previous_id)
        root_ids |= pinned
        last_good = pointer.get("lastKnownGoodReleaseId")
        if last_good:
            root_ids.add(last_good)
        grace_protected: set[str] = set()
        eligible: set[str] = set()
        states: dict[str, str] = {}
        for record in full_records:
            state = self._state(record.release_id)
            current = str(state.get("lifecycleState") or RETAINED)
            if record.release_id in root_ids:
                states[record.release_id] = ACTIVE if record.release_id == active_id else (ROLLBACK_PROTECTED if record.release_id == previous_id or record.release_id in pinned else RETAINED)
                continue
            since_text = state.get("unreachableSince")
            since = _parse_instant(since_text) if isinstance(since_text, str) else None
            if since is None:
                grace_protected.add(record.release_id)
                states[record.release_id] = RETAINED
                if mutate_grace:
                    self._write_state(record.release_id, state=RETAINED, evaluation_at=evaluation_at, unreachable_since=evaluation_at)
                    self._ledger_event("RELEASE_GRACE_STARTED", evaluation_at=evaluation_at, release_id=record.release_id, data={"graceSeconds": self.policy.grace_seconds})
            elif (now - since).total_seconds() < self.policy.grace_seconds:
                grace_protected.add(record.release_id)
                states[record.release_id] = RETAINED
            else:
                eligible.add(record.release_id)
                states[record.release_id] = GC_ELIGIBLE

        protected_ids = root_ids | grace_protected
        reachable: set[str] = set()
        for release_id in sorted(protected_ids):
            record = by_id.get(release_id)
            if record is None or not record.data_present or record.full_manifest is None:
                raise RetentionError(f"protected release {release_id} is not fully verifiable", code="PROTECTED_RELEASE_DATA_MISSING")
            reachable.update(self._verify_manifest_objects(release_id, record.full_manifest))

        all_objects = dict(self._all_object_files())
        unreachable = tuple({"sha256": digest, "bytes": size} for digest, size in sorted(all_objects.items()) if digest not in reachable)
        accounting = self._accounting(reachable=reachable, unreachable=unreachable, eligible=sorted(eligible), records=records, protected=sorted(protected_ids))
        budget = self._validate_budget(accounting, eligible=sorted(eligible))
        mutation_required = bool(eligible or grace_protected)
        return RetentionPlan(
            evaluation_at=evaluation_at,
            policy=self.policy.as_dict(),
            active_release_id=active_id,
            previous_release_id=previous_id,
            protected_release_ids=tuple(sorted(protected_ids)),
            grace_protected_release_ids=tuple(sorted(grace_protected)),
            eligible_release_ids=tuple(sorted(eligible)),
            metadata_only_release_ids=tuple(sorted(record.release_id for record in records if not record.data_present)),
            release_states=states,
            reachable_objects=tuple(sorted(reachable)),
            unreachable_objects=unreachable,
            accounting=accounting,
            budget=budget,
            mutation_required=mutation_required,
        )

    def plan(self, *, evaluation_at: str) -> RetentionPlan:
        """Read and verify state without changing any file."""

        with SingleWriterLock(self.lock_path):
            return self._plan_unlocked(evaluation_at=evaluation_at, mutate_grace=False)

    def collect(self, *, evaluation_at: str, apply: bool = False, fail_delete_after: int | None = None) -> dict[str, Any]:
        """Run a dry-run plan or an authorized mark/plan/verify/sweep."""

        with SingleWriterLock(self.lock_path):
            if not apply:
                plan = self._plan_unlocked(evaluation_at=evaluation_at, mutate_grace=False)
                return {"mode": "DRY_RUN", "applied": False, "plan": plan.as_dict()}

            records = self._records()
            self._bootstrap_ledger(records, evaluation_at=evaluation_at)
            plan = self._plan_unlocked(evaluation_at=evaluation_at, mutate_grace=True)
            if plan.budget.get("configured") and int(plan.accounting["storageBeforeBytes"]) > int(plan.budget["budgetBytes"] or 0) and not plan.eligible_release_ids:
                raise RetentionError("storage budget is exceeded and grace has not elapsed", code="STORAGE_BUDGET_EXCEEDED", details=plan.as_dict())

            pointer_before = canonical_bytes(self._active_pointer())
            progress = {"schemaVersion": "valuepilot-gc-progress-v1", "startedAt": evaluation_at, "status": "RUNNING", "eligibleReleaseIds": list(plan.eligible_release_ids), "deletedObjects": []}
            _write_json(self.progress_path, progress)
            deleted: list[str] = []
            try:
                for release_id in plan.eligible_release_ids:
                    self._write_state(release_id, state=GC_ELIGIBLE, evaluation_at=evaluation_at, unreachable_since=self._state(release_id).get("unreachableSince"))
                    self._ledger_event("RELEASE_GC_ELIGIBLE", evaluation_at=evaluation_at, release_id=release_id, data={"graceSeconds": self.policy.grace_seconds})
                for index, item in enumerate(plan.unreachable_objects):
                    digest = str(item["sha256"])
                    if fail_delete_after is not None and index >= fail_delete_after:
                        raise RetentionError("injected object deletion failure", code="GC_DELETE_FAILED", details={"deletedObjects": deleted})
                    path = self.store.object_path(digest)
                    if path.exists():
                        try:
                            path.unlink()
                        except OSError as exc:
                            raise RetentionError(f"cannot delete object {digest}", code="GC_DELETE_FAILED", details={"deletedObjects": deleted}) from exc
                    deleted.append(digest)
                    progress["deletedObjects"] = list(deleted)
                    _write_json(self.progress_path, progress)

                now_text = _instant_text(_parse_instant(evaluation_at))
                for release_id in plan.eligible_release_ids:
                    record = next(item for item in records if item.release_id == release_id)
                    metadata = record.compact_metadata(state=GC_COMPLETED, data_present=False, gc_completed_at=now_text)
                    directory = self.releases / release_id
                    _write_json(directory / "metadata.json", metadata)
                    _write_atomic(directory / "metadata.sha256", (sha256_file(directory / "metadata.json") + "  metadata.json\n").encode("ascii"))
                    for name in ("manifest.json", "manifest.sha256", "retention-state.json"):
                        (directory / name).unlink(missing_ok=True)
                    self._ledger_event("GC_COMPLETED", evaluation_at=evaluation_at, release_id=release_id, data={"dataPresent": False, "deletedObjectCount": len(deleted)})

                progress.update({"status": "COMPLETE", "completedAt": evaluation_at, "deletedObjects": list(deleted)})
                _write_json(self.progress_path, progress)
            except RetentionError:
                progress.update({"status": "PARTIAL", "deletedObjects": list(deleted)})
                _write_json(self.progress_path, progress)
                raise

            if canonical_bytes(self._active_pointer()) != pointer_before:
                raise RetentionError("active pointer changed during GC", code="POST_GC_VERIFY_FAILED")
            post = self._plan_unlocked(evaluation_at=evaluation_at, mutate_grace=False)
            if post.active_release_id != plan.active_release_id or post.previous_release_id != plan.previous_release_id:
                raise RetentionError("protected release set changed during GC", code="POST_GC_VERIFY_FAILED")
            result = {"mode": "APPLY", "applied": True, "deletedObjectCount": len(deleted), "deletedObjects": deleted, "planBefore": plan.as_dict(), "planAfter": post.as_dict()}
            return result

    def pin(self, release_id: str, *, evaluation_at: str) -> dict[str, Any]:
        with SingleWriterLock(self.lock_path):
            release_id = _release_id(release_id)
            records = {record.release_id: record for record in self._records()}
            if release_id not in records or not records[release_id].data_present:
                raise RetentionError("only fully retained releases can be pinned", code="RELEASE_DATA_NOT_RETAINED")
            pins = set(self._pins())
            pins.add(release_id)
            _write_json(self.pins_path, {"schemaVersion": "valuepilot-operator-pins-v1", "releaseIds": sorted(pins)})
            event = self._ledger_event("RELEASE_PINNED", evaluation_at=evaluation_at, release_id=release_id)
            return {"releaseId": release_id, "pinned": True, "eventHash": event["eventHash"]}

    def unpin(self, release_id: str, *, evaluation_at: str) -> dict[str, Any]:
        with SingleWriterLock(self.lock_path):
            release_id = _release_id(release_id)
            pins = set(self._pins())
            pins.discard(release_id)
            _write_json(self.pins_path, {"schemaVersion": "valuepilot-operator-pins-v1", "releaseIds": sorted(pins)})
            event = self._ledger_event("RELEASE_UNPINNED", evaluation_at=evaluation_at, release_id=release_id)
            return {"releaseId": release_id, "pinned": False, "eventHash": event["eventHash"]}

    def rollback(self, release_id: str, *, evaluation_at: str) -> dict[str, Any]:
        with SingleWriterLock(self.lock_path):
            release_id = _release_id(release_id)
            # A compact metadata-only historical record is intentionally not
            # rollback-capable.  Return the public lifecycle error rather than
            # treating its missing full manifest as a protected-root failure.
            if self._metadata_path(release_id).is_file() or not self._manifest_path(release_id).is_file():
                raise RetentionError(f"full release data is not retained for {release_id}", code="RELEASE_DATA_NOT_RETAINED")
            manifest, _ = self._read_full_manifest(release_id, protected=True)
            self._verify_manifest_objects(release_id, manifest)
            old = self._active_pointer()
            pointer = {"schemaVersion": ACTIVE_POINTER_SCHEMA, "releaseId": release_id, "previousReleaseId": old.get("releaseId"), "lastKnownGoodReleaseId": release_id}
            _write_json(self.control / "active.json", pointer)
            self._ledger_event("ROLLBACK", evaluation_at=evaluation_at, release_id=release_id, data={"fromReleaseId": old.get("releaseId"), "dataPresent": True})
            return {"rolledBack": True, "activeBefore": old.get("releaseId"), "activeAfter": release_id}


def _fixture_manifest(release_id: str, source_date: str, objects: Sequence[tuple[str, bytes]]) -> dict[str, Any]:
    return {
        "schemaVersion": "fixture-release-v1",
        "policyVersion": "fixture-policy-v1",
        "toolVersion": "fixture-tool-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "source": {"releaseDate": source_date, "sha256": hashlib.sha256(release_id.encode("utf-8")).hexdigest()},
        "backendProfile": {"logicalPartitionCount": 1, "physicalPackCount": 1},
        "backendCounts": {"offers": len(objects), "stores": 1, "productEvidenceRecords": len(objects), "promotions": 0},
        "objects": [{"path": f"fixture/{index}.bin", "sha256": hashlib.sha256(payload).hexdigest(), "bytes": len(payload), "compressedBytes": len(payload), "uncompressedBytes": len(payload)} for index, (_, payload) in enumerate(objects)],
        "qualificationStatus": "FIXTURE_ONLY",
        "rawProviderDataCommitted": False,
        "automatedOfficialAcquisitionProven": False,
    }


def _fixture_publish(workspace: Path, release_id: str, source_date: str, objects: Sequence[tuple[str, bytes]], *, active_before: str | None = None) -> None:
    manager = StorageRetentionManager(workspace)
    for _, payload in objects:
        digest = hashlib.sha256(payload).hexdigest()
        manager.store.object_store.put_immutable(digest, payload, sha256=digest)
    manifest = _fixture_manifest(release_id, source_date, objects)
    directory = workspace / "releases" / release_id
    directory.mkdir(parents=True, exist_ok=True)
    _write_json(directory / "manifest.json", manifest)
    _write_atomic(directory / "manifest.sha256", (sha256_file(directory / "manifest.json") + "  manifest.json\n").encode("ascii"))
    _write_json(directory / "retention-state.json", {"schemaVersion": "valuepilot-release-retention-state-v1", "releaseId": release_id, "lifecycleState": RETAINED})
    if active_before is not None:
        _write_json(workspace / "control" / "active.json", {"schemaVersion": ACTIVE_POINTER_SCHEMA, "releaseId": release_id, "previousReleaseId": active_before, "lastKnownGoodReleaseId": release_id})
    else:
        _write_json(workspace / "control" / "active.json", {"schemaVersion": ACTIVE_POINTER_SCHEMA, "releaseId": release_id, "previousReleaseId": None, "lastKnownGoodReleaseId": release_id})


def run_fixture_simulation(*, days: int = 30, retention_count: int = DEFAULT_RETENTION_COUNT, grace_seconds: int = DEFAULT_GRACE_SECONDS) -> dict[str, Any]:
    """Run a deterministic small-object lifecycle simulation, never real SEPA."""

    if days < 30:
        raise RetentionError("fixture simulation must cover at least 30 days", code="RETENTION_POLICY_INVALID")
    with tempfile.TemporaryDirectory(prefix="valuepilot-retention-simulation-") as temporary:
        workspace = Path(temporary) / "workspace"
        manager = StorageRetentionManager(workspace, policy=RetentionPolicy(retention_count=retention_count, grace_seconds=grace_seconds))
        full_counts: list[int] = []
        storage_sizes: list[int] = []
        events: list[str] = []
        pinned_release = "fixture-day-05"
        for day in range(1, days + 1):
            release_id = f"fixture-day-{day:02d}"
            date = _dt.date(2026, 1, 1) + _dt.timedelta(days=day - 1)
            shared = b"shared-fixture-object-v1"
            if day <= 15:
                size = 80 + day * 7
            else:
                size = max(24, 190 - (day - 15) * 8)
            unique = (f"day-{day:02d}-".encode("ascii") * ((size // 8) + 1))[:size]
            previous = f"fixture-day-{day - 1:02d}" if day > 1 else None
            _fixture_publish(workspace, release_id, date.isoformat(), [("shared", shared), ("unique", unique)], active_before=previous)
            if day == 5:
                manager.pin(pinned_release, evaluation_at=f"{date.isoformat()}T12:00:00Z")
                events.append("operator_pin")
            if day == 9:
                before = _read_json(workspace / "control" / "active.json")["releaseId"]
                try:
                    manager.rollback("missing-fixture-release", evaluation_at=f"{date.isoformat()}T12:00:00Z")
                except RetentionError:
                    events.append("failed_activation_preserved_pointer")
                after = _read_json(workspace / "control" / "active.json")["releaseId"]
                if before != after:
                    raise RetentionError("fixture failed activation changed pointer", code="RETENTION_FAILED")
            if day == 16:
                manager.unpin(pinned_release, evaluation_at=f"{date.isoformat()}T12:00:00Z")
                events.append("operator_unpin")
            if day == 20:
                manager.rollback("fixture-day-19", evaluation_at=f"{date.isoformat()}T12:00:00Z")
                _write_json(workspace / "control" / "active.json", {"schemaVersion": ACTIVE_POINTER_SCHEMA, "releaseId": release_id, "previousReleaseId": "fixture-day-19", "lastKnownGoodReleaseId": release_id})
                events.append("rollback")
            evaluation = f"{date.isoformat()}T12:00:00Z"
            if day >= 2:
                try:
                    manager.collect(evaluation_at=evaluation, apply=True)
                except RetentionError as exc:
                    if exc.code != "STORAGE_BUDGET_EXCEEDED":
                        raise
            full = sum(1 for item in manager._records() if item.data_present)
            full_counts.append(full)
            storage_sizes.append(manager._accounting(reachable=set(), unreachable=(), eligible=(), records=manager._records(), protected=()).get("storageBeforeBytes", 0))
        budget_manager = StorageRetentionManager(workspace, policy=RetentionPolicy(retention_count=retention_count, grace_seconds=grace_seconds, budget_bytes=1))
        budget_pressure = False
        try:
            budget_manager.collect(evaluation_at="2026-02-15T12:00:00Z", apply=False)
        except RetentionError as exc:
            budget_pressure = exc.code == "STORAGE_BUDGET_EXCEEDED"
        final_records = manager._records()
        return {
            "fixtureOnly": True,
            "days": days,
            "retentionCount": retention_count,
            "graceSeconds": grace_seconds,
            "maxFullReleaseCount": max(full_counts),
            "finalFullReleaseCount": sum(1 for item in final_records if item.data_present),
            "maxStorageBytes": max(storage_sizes),
            "finalStorageBytes": storage_sizes[-1],
            "allObjectsUniqueAndSharedCases": True,
            "growingAndShrinkingReleaseSizes": True,
            "events": events,
            "budgetPressureFailClosed": budget_pressure,
            "maxRetainedReleaseBound": retention_count + 2,
            "boundedByRetentionGraceAndExplicitPin": max(full_counts) <= retention_count + 2,
        }


def markdown_report(report: Mapping[str, Any]) -> str:
    lines = [
        "# Argentina production storage retention V1",
        "",
        f"Status: **{report.get('status')}**",
        "",
        f"- Starting SHA: `{report.get('startingSha')}`",
        f"- M9 source evidence reused: `{report.get('m9SourceEvidence', {}).get('status')}`",
        f"- Retention policy: keep `{report.get('retentionPolicy', {}).get('defaultCount')}` complete verified releases (allowed `{report.get('retentionPolicy', {}).get('allowedRange')}`).",
        f"- Grace period: `{report.get('retentionPolicy', {}).get('gracePeriod')}`.",
        f"- Minimum safe releases: `{report.get('retentionPolicy', {}).get('minimumSafeReleases')}`.",
        "",
        "## Real M9 workspace",
        "",
        f"- Dry-run status: `{report.get('realWorkspaceDryRun', {}).get('status')}`.",
        f"- Protected releases: `{report.get('realWorkspaceDryRun', {}).get('protectedReleaseIds')}`.",
        f"- Objects that would be deleted: `{report.get('realWorkspaceDryRun', {}).get('wouldDeleteObjectCount')}`; bytes: `{report.get('realWorkspaceDryRun', {}).get('wouldDeleteBytes')}`.",
        f"- Storage before/projected after: `{report.get('realWorkspaceDryRun', {}).get('storageBeforeBytes')}` / `{report.get('realWorkspaceDryRun', {}).get('projectedPostGcBytes')}`.",
        "",
        "## Fixture simulation",
        "",
        f"- 30-day simulation: `{report.get('fixtureSimulation', {}).get('days')}` days; maximum full releases `{report.get('fixtureSimulation', {}).get('maxFullReleaseCount')}`; maximum storage `{report.get('fixtureSimulation', {}).get('maxStorageBytes')}` bytes.",
        f"- Budget pressure failed closed: `{report.get('fixtureSimulation', {}).get('budgetPressureFailClosed')}`.",
        "",
        "## Safety boundary",
        "",
        "- GC is operator-only, dry-run by default, mark/plan/verify/sweep based, and never changes active.json.",
        "- M6/M7/M8/M9 semantics are reused and not rebuilt; old full data is never treated as price history.",
        "- LIVE_CLOUD_DEPLOYMENT_VERIFIED = false; PRICE_HISTORY_IMPLEMENTED = false; ANDROID_NETWORKING_AUTHORIZED = false.",
        "",
    ]
    return "\n".join(lines)


def _budget_from_environment(value: int | None) -> int | None:
    if value is not None:
        return value
    raw = os.environ.get("VALUEPILOT_STORAGE_BUDGET_BYTES")
    if raw is None or raw == "":
        return None
    try:
        parsed = int(raw)
    except ValueError as exc:
        raise RetentionError("VALUEPILOT_STORAGE_BUDGET_BYTES is invalid", code="RETENTION_POLICY_INVALID") from exc
    return parsed


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path)
    parser.add_argument("--retention-count", type=int, default=DEFAULT_RETENTION_COUNT)
    parser.add_argument("--grace-hours", type=float, default=24.0)
    parser.add_argument("--budget-bytes", type=int)
    parser.add_argument("--evaluation-at", default="2026-09-08T18:00:00Z")
    parser.add_argument("--apply", action="store_true", help="authorize deletion; default is a no-delete dry-run")
    parser.add_argument("--fail-delete-after", type=int, help="test-only partial deletion injection")
    parser.add_argument("--pin")
    parser.add_argument("--unpin")
    parser.add_argument("--rollback")
    parser.add_argument("--simulate", action="store_true", help="run the deterministic 30-day fixture simulation")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.simulate:
            result = {"status": "SIMULATION_COMPLETE", "fixtureSimulation": run_fixture_simulation(days=30, retention_count=args.retention_count, grace_seconds=int(args.grace_hours * 3600))}
        else:
            if args.workspace is None:
                parser.error("--workspace is required unless --simulate is used")
            policy = RetentionPolicy(retention_count=args.retention_count, grace_seconds=int(args.grace_hours * 3600), budget_bytes=_budget_from_environment(args.budget_bytes))
            manager = StorageRetentionManager(args.workspace, policy=policy)
            if args.pin:
                result = manager.pin(args.pin, evaluation_at=args.evaluation_at)
            elif args.unpin:
                result = manager.unpin(args.unpin, evaluation_at=args.evaluation_at)
            elif args.rollback:
                result = manager.rollback(args.rollback, evaluation_at=args.evaluation_at)
            else:
                result = manager.collect(evaluation_at=args.evaluation_at, apply=args.apply, fail_delete_after=args.fail_delete_after)
            result = {"status": "GC_APPLIED" if args.apply else "DRY_RUN_COMPLETE", "result": result, "policy": policy.as_dict()}
        if args.report:
            _write_json(args.report, result)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except (RetentionError, DailyReleaseError) as exc:
        print(json.dumps({"error": getattr(exc, "code", "RETENTION_FAILED"), "message": str(exc), "details": getattr(exc, "details", {})}, ensure_ascii=False, sort_keys=True))
        return getattr(exc, "exit_code", 20)


if __name__ == "__main__":
    raise SystemExit(main())

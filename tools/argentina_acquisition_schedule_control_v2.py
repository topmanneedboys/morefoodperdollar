"""Three-phase Argentina SEPA acquisition control.

This module layers a bounded late-day byte recheck over the immutable daily
observation semantics in ``argentina_acquisition_schedule_control``. Each
scheduled phase still performs fresh official metadata discovery:
``DISCOVERY_PLUS_RECOVERY`` is the explicit late-check policy.

PRIMARY performs the first byte-level check for a selected metadata fingerprint.
METADATA_ONLY reuses same-day evidence and downloads only when the selected
metadata fingerprint is new.  LATE_RECHECK permits exactly one additional
byte-level check when the primary observation for that same fingerprint ended in
NO_CHANGE or OLDER_REJECTED.  The late result is written under a distinct
immutable key, so a newly published ZIP can be accepted without overwriting or
weakening the earlier evidence.

All release identity still comes from the inspected ZIP date and SHA-256.  This
module never writes ACTIVE or CANARY pointers.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.clock import DEFAULT_MAX_RELEASE_AGE_DAYS, FrozenClock, freshness
from backend.object_store import (
    GCSObjectStore,
    ObjectStore,
    ObjectStoreError,
    ObjectStoreLimitExceeded,
)
from tools import argentina_acquisition_schedule_control as _base
from tools.argentina_processed_source_guard import (
    SUPPRESSION_REASON,
    apply_processed_source_guard,
)
from tools.argentina_release_acquisition import (
    AcquisitionError,
    CATALOG_ENDPOINT,
    DiscoveryResult,
    discover_metadata,
    select_latest_candidate,
)


SCHEMA_VERSION = _base.SCHEMA_VERSION
POLICY_VERSION = _base.POLICY_VERSION
DAILY_OBSERVATION_PREFIX = _base.DAILY_OBSERVATION_PREFIX
BUILD_ATTEMPT_PREFIX = _base.BUILD_ATTEMPT_PREFIX
LATE_RECHECK_PREFIX = "acquisition/late-rechecks"
DAILY_STATE_PREFIX = "acquisition/daily-state"
DAILY_STATE_EVENT_PREFIX = "acquisition/daily-state-events"
DAILY_STATE_SCHEMA_VERSION = "valuepilot-argentina-daily-state-v2"
DAILY_STATE_POLICY_VERSION = "valuepilot-argentina-daily-state-policy-v2"
LEGACY_DAILY_STATE_SCHEMA_VERSION = "valuepilot-argentina-daily-state-v1"
LEGACY_DAILY_STATE_POLICY_VERSION = "valuepilot-argentina-daily-state-policy-v1"
DAILY_STATE_EVENT_MAX = 128
LATE_CHECK_POLICY = "DISCOVERY_PLUS_RECOVERY"
ACTIVE_RELEASE_MAX_AGE_DAYS = DEFAULT_MAX_RELEASE_AGE_DAYS
FRESHNESS_WARNING_WINDOW_DAYS = 2
DAILY_STATE_READ_ATTEMPTS = 3
_DAY_STATE_RANK = {
    "METADATA_DISCOVERED": 0,
    "DOWNLOAD_REQUIRED": 0,
    "NO_BUILD_REQUIRED": 1,
    "SOURCE_READY": 2,
    "CANDIDATE_PERSISTED": 3,
    "BUILD_RESERVED": 4,
    "SHADOW_STARTED": 5,
    "SHADOW_FAILED": 6,
    "SHADOW_SUCCEEDED": 7,
}

CHECK_PHASE_PRIMARY = "PRIMARY"
CHECK_PHASE_METADATA_ONLY = "METADATA_ONLY"
CHECK_PHASE_LATE_RECHECK = "LATE_RECHECK"
CHECK_PHASES = frozenset(
    {
        CHECK_PHASE_PRIMARY,
        CHECK_PHASE_METADATA_ONLY,
        CHECK_PHASE_LATE_RECHECK,
    }
)


AcquisitionScheduleError = _base.AcquisitionScheduleError


def daily_state_key(argentina_day: str) -> str:
    try:
        day = _dt.date.fromisoformat(argentina_day).isoformat()
    except (TypeError, ValueError) as exc:
        raise AcquisitionScheduleError("Argentina date is invalid", code="INVALID_CONTROL_STATE") from exc
    return f"{DAILY_STATE_PREFIX}/{day}.json"


def daily_state_event_prefix(argentina_day: str) -> str:
    try:
        day = _dt.date.fromisoformat(argentina_day).isoformat()
    except (TypeError, ValueError) as exc:
        raise AcquisitionScheduleError("Argentina date is invalid", code="INVALID_CONTROL_STATE") from exc
    return f"{DAILY_STATE_EVENT_PREFIX}/{day}/"


def _validate_daily_state(value: Mapping[str, Any], argentina_day: str) -> None:
    schema_policy = (value.get("schemaVersion"), value.get("policyVersion"))
    if (
        schema_policy
        not in {
            (DAILY_STATE_SCHEMA_VERSION, DAILY_STATE_POLICY_VERSION),
            (LEGACY_DAILY_STATE_SCHEMA_VERSION, LEGACY_DAILY_STATE_POLICY_VERSION),
        }
        or value.get("argentinaDate") != argentina_day
        or value.get("status") not in _DAY_STATE_RANK
    ):
        raise AcquisitionScheduleError("same-day acquisition state is invalid", code="INVALID_CONTROL_STATE")
    updated_at = value.get("updatedAt")
    if not isinstance(updated_at, str):
        raise AcquisitionScheduleError("same-day acquisition timestamp is invalid", code="INVALID_CONTROL_STATE")
    _base._timestamp(updated_at, field="dailyState.updatedAt")
    _base._validate_probe(
        {
            "schemaVersion": _base.SCHEMA_VERSION,
            "policyVersion": _base.POLICY_VERSION,
            "observedAt": value.get("observedAt"),
            "argentinaDate": value.get("argentinaDate"),
            "metadataFingerprint": value.get("metadataFingerprint"),
            "metadataIdentity": value.get("metadataIdentity"),
            "catalogResponseSha256": value.get("catalogResponseSha256"),
        }
    )
    fingerprint = value.get("metadataFingerprint")
    if fingerprint is not None:
        _base._sha(fingerprint, field="daily-state metadata fingerprint")


def _read_daily_state_snapshot(
    *, store: ObjectStore, argentina_day: str
) -> tuple[dict[str, Any] | None, str | None]:
    """Read the state payload and its matching CAS token as one snapshot.

    The object-store API exposes metadata and payload reads separately. Bracket
    the payload read with metadata reads; if a writer changed the object, retry
    instead of pairing older JSON with a newer generation token.
    """

    key = daily_state_key(argentina_day)
    for _ in range(DAILY_STATE_READ_ATTEMPTS):
        try:
            first = store.head(key)
        except ObjectStoreError:
            try:
                if not store.exists(key):
                    return None, None
            except ObjectStoreError as exc:
                raise AcquisitionScheduleError(
                    "same-day acquisition state is unavailable",
                    code="CONTROL_STORE_UNAVAILABLE",
                ) from exc
            continue
        if first.size <= 0 or first.size > _base.MAX_CONTROL_BYTES:
            raise AcquisitionScheduleError("same-day acquisition state size is invalid", code="INVALID_CONTROL_STATE")
        try:
            raw = store.get(key)
            last = store.head(key)
        except ObjectStoreError:
            continue
        if first.etag != last.etag or len(raw) != first.size:
            continue
        digest = hashlib.sha256(raw).hexdigest()
        if first.sha256 is not None and first.sha256 != digest:
            raise AcquisitionScheduleError("same-day acquisition state hash is invalid", code="INVALID_CONTROL_STATE")
        try:
            value = json.loads(raw)
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise AcquisitionScheduleError("same-day acquisition state is invalid JSON", code="INVALID_CONTROL_STATE") from exc
        if not isinstance(value, dict):
            raise AcquisitionScheduleError("same-day acquisition state is invalid", code="INVALID_CONTROL_STATE")
        _validate_daily_state(value, argentina_day)
        return value, first.etag
    raise AcquisitionScheduleError(
        "same-day acquisition state changed while being read",
        code="CONTROL_STORE_CONFLICT",
    )


def _read_daily_state_events(*, store: ObjectStore, argentina_day: str) -> list[tuple[str, dict[str, Any]]]:
    """Read the bounded immutable event journal for one acquisition day."""

    try:
        entries = store.list_immediate(
            daily_state_event_prefix(argentina_day),
            max_results=DAILY_STATE_EVENT_MAX,
        )
    except ObjectStoreLimitExceeded as exc:
        raise AcquisitionScheduleError(
            "same-day acquisition event journal exceeds bound",
            code="INVALID_CONTROL_STATE",
        ) from exc
    except ObjectStoreError as exc:
        raise AcquisitionScheduleError(
            "same-day acquisition event journal is unavailable",
            code="CONTROL_STORE_UNAVAILABLE",
        ) from exc

    events: list[tuple[str, dict[str, Any]]] = []
    prefix = daily_state_event_prefix(argentina_day)
    for metadata in entries:
        relative = metadata.key.removeprefix(prefix)
        if not relative.endswith(".json") or len(relative) != 69:
            raise AcquisitionScheduleError("same-day acquisition event key is invalid", code="INVALID_CONTROL_STATE")
        digest = relative[:-5]
        if len(digest) != 64 or any(character not in "0123456789abcdef" for character in digest):
            raise AcquisitionScheduleError("same-day acquisition event key is invalid", code="INVALID_CONTROL_STATE")
        try:
            raw = store.get(metadata.key)
        except ObjectStoreError as exc:
            raise AcquisitionScheduleError(
                "same-day acquisition event is unavailable",
                code="CONTROL_STORE_UNAVAILABLE",
            ) from exc
        expected_size = metadata.size
        if expected_size < 0:
            try:
                expected_size = store.head(metadata.key).size
            except ObjectStoreError as exc:
                raise AcquisitionScheduleError(
                    "same-day acquisition event metadata is unavailable",
                    code="CONTROL_STORE_UNAVAILABLE",
                ) from exc
        if len(raw) != expected_size or hashlib.sha256(raw).hexdigest() != digest:
            raise AcquisitionScheduleError("same-day acquisition event hash is invalid", code="INVALID_CONTROL_STATE")
        try:
            value = json.loads(raw)
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise AcquisitionScheduleError("same-day acquisition event is invalid JSON", code="INVALID_CONTROL_STATE") from exc
        if not isinstance(value, dict):
            raise AcquisitionScheduleError("same-day acquisition event is invalid", code="INVALID_CONTROL_STATE")
        _validate_daily_state(value, argentina_day)
        events.append((digest, value))
    return events


def _canonical_value(value: Any) -> bytes:
    return _base.canonical_bytes(value)


def _merge_daily_state_records(
    records: Sequence[tuple[str, Mapping[str, Any]]],
    *,
    argentina_day: str,
) -> dict[str, Any] | None:
    """Merge immutable records without hiding identity or evidence conflicts."""

    if not records:
        return None
    validated: list[tuple[str, dict[str, Any]]] = []
    for digest, record in records:
        value = dict(record)
        _validate_daily_state(value, argentina_day)
        validated.append((digest, value))

    def parsed_time(record: Mapping[str, Any]) -> _dt.datetime:
        return _dt.datetime.fromisoformat(str(record["updatedAt"]).replace("Z", "+00:00"))

    latest_time = max(parsed_time(record) for _, record in validated)
    latest_records = [(digest, record) for digest, record in validated if parsed_time(record) == latest_time]
    latest_fingerprints = {record.get("metadataFingerprint") for _, record in latest_records}
    if len(latest_fingerprints) > 1:
        raise AcquisitionScheduleError("same-time daily acquisition states conflict", code="CONTROL_CONFLICT")
    selected_fingerprint = next(iter(latest_fingerprints))
    selected = [
        (digest, record)
        for digest, record in validated
        if record.get("metadataFingerprint") == selected_fingerprint
    ]
    selected.sort(key=lambda item: (parsed_time(item[1]), item[0]))
    merged = dict(selected[-1][1])

    # Probe identity is immutable for one metadata fingerprint.  A mismatch
    # means the journal cannot establish which source claim is authoritative.
    for field in ("metadataIdentity", "catalogResponseSha256", "observedAt"):
        values = {
            _canonical_value(record.get(field))
            for _, record in selected
            if record.get(field) is not None
        }
        if len(values) > 1:
            raise AcquisitionScheduleError(
                f"same-fingerprint daily acquisition {field} conflicts",
                code="CONTROL_CONFLICT",
            )

    identity_fields = ("candidateReleaseId", "buildReservationId", "workflowExecution")
    for field in identity_fields:
        values = {record.get(field) for _, record in selected if record.get(field) is not None}
        if len(values) > 1:
            raise AcquisitionScheduleError(
                f"same-fingerprint daily acquisition {field} conflicts",
                code="CONTROL_CONFLICT",
            )

    # A source archive is itself identity evidence.  Its representation can
    # gain fields as the handoff advances, so compare the immutable identity
    # fields and retain the most complete/latest representation.
    source_identities: set[tuple[Any, ...]] = set()
    source_entries: list[tuple[str, Mapping[str, Any], Mapping[str, Any]]] = []
    for digest, record in selected:
        source = record.get("source")
        if not isinstance(source, Mapping):
            continue
        source_entries.append((digest, record, source))
        source_identities.add(
            (
                source.get("releaseDate"),
                source.get("sha256"),
                source.get("bytes"),
                source.get("candidateReleaseId"),
            )
        )
    source_observation_keys = {record.get("dailyObservationKey") for _, record, _ in source_entries}
    source_statuses = {_DAY_STATE_RANK[record["status"]] for _, record, _ in source_entries}
    if (
        len(source_identities - {(None, None, None, None)}) > 1
        and len(source_observation_keys) <= 1
        and len(source_statuses) <= 1
    ):
        raise AcquisitionScheduleError("same-fingerprint source identities conflict", code="CONTROL_CONFLICT")
    if source_entries:
        source_entries.sort(key=lambda item: (_DAY_STATE_RANK[item[1]["status"]], parsed_time(item[1]), item[0]))
        merged["source"] = dict(source_entries[-1][2])

    status_record = max(
        selected,
        key=lambda item: (_DAY_STATE_RANK[item[1]["status"]], parsed_time(item[1]), item[0]),
    )[1]
    merged["status"] = status_record["status"]
    top_rank = _DAY_STATE_RANK[status_record["status"]]
    top_shadow_values = [
        record.get("shadowResult")
        for _, record in selected
        if _DAY_STATE_RANK[record["status"]] == top_rank and record.get("shadowResult") is not None
    ]
    if top_shadow_values:
        shadow_digests = {_canonical_value(value) for value in top_shadow_values}
        if len(shadow_digests) > 1:
            raise AcquisitionScheduleError("same-fingerprint SHADOW results conflict", code="CONTROL_CONFLICT")
        merged["shadowResult"] = top_shadow_values[-1]
    # updatedAt remains the latest event time even when a lower-ranked retry is
    # merged, so a new metadata fingerprint can supersede an older day cleanly.
    merged["updatedAt"] = selected[-1][1]["updatedAt"]
    for field in identity_fields:
        values = [record.get(field) for _, record in selected if record.get(field) is not None]
        if values:
            merged[field] = values[-1]
    return merged


def read_daily_state(*, store: ObjectStore, argentina_day: str) -> dict[str, Any] | None:
    """Read legacy state plus the bounded immutable same-day event journal."""

    legacy, _ = _read_daily_state_snapshot(store=store, argentina_day=argentina_day)
    records: list[tuple[str, Mapping[str, Any]]] = []
    if legacy is not None:
        legacy_raw = _base.canonical_bytes(legacy) + b"\n"
        records.append((hashlib.sha256(legacy_raw).hexdigest(), legacy))
    records.extend(_read_daily_state_events(store=store, argentina_day=argentina_day))
    merged = _merge_daily_state_records(records, argentina_day=argentina_day)
    return merged


def _write_daily_state(*, store: ObjectStore, value: Mapping[str, Any]) -> dict[str, Any]:
    """Append one immutable state event; concurrent writers cannot overwrite evidence."""

    day = value.get("argentinaDate")
    if not isinstance(day, str):
        raise AcquisitionScheduleError("same-day acquisition state is invalid", code="INVALID_CONTROL_STATE")
    incoming = dict(value)
    incoming["schemaVersion"] = DAILY_STATE_SCHEMA_VERSION
    incoming["policyVersion"] = DAILY_STATE_POLICY_VERSION
    if incoming.get("status") not in _DAY_STATE_RANK:
        raise AcquisitionScheduleError("same-day acquisition status is invalid", code="INVALID_CONTROL_STATE")
    incoming["updatedAt"] = _base._timestamp(str(incoming.get("updatedAt")), field="dailyState.updatedAt")

    current = read_daily_state(store=store, argentina_day=day)
    if current is not None:
        old_updated = _dt.datetime.fromisoformat(current["updatedAt"].replace("Z", "+00:00"))
        new_updated = _dt.datetime.fromisoformat(incoming["updatedAt"].replace("Z", "+00:00"))
        if new_updated < old_updated:
            return current
        merged = _merge_daily_state_records(
            [("current", current), ("incoming", incoming)],
            argentina_day=day,
        )
        if merged is None:
            raise AcquisitionScheduleError("same-day acquisition state is unavailable", code="CONTROL_STORE_UNAVAILABLE")
        incoming = merged
    raw = _base.canonical_bytes(incoming) + b"\n"
    if len(raw) > _base.MAX_CONTROL_BYTES:
        raise AcquisitionScheduleError("same-day acquisition state exceeds bound", code="INVALID_CONTROL_STATE")
    digest = hashlib.sha256(raw).hexdigest()
    key = f"{daily_state_event_prefix(day)}{digest}.json"
    try:
        store.put_immutable(key, raw, sha256=digest)
    except ObjectStoreError as exc:
        raise AcquisitionScheduleError("same-day acquisition event could not be recorded", code="CONTROL_STORE_UNAVAILABLE") from exc
    return incoming


def _persist_resolution_state(
    *,
    resolution: Mapping[str, Any],
    store: ObjectStore,
    state_status: str | None = None,
    updated_at: str | None = None,
    extra: Mapping[str, Any] | None = None,
) -> dict[str, Any] | None:
    status = state_status or str(resolution.get("status", ""))
    if status not in _DAY_STATE_RANK:
        return None
    probe = _base._validate_probe(resolution)
    record: dict[str, Any] = {
        "argentinaDate": probe["argentinaDate"],
        "observedAt": probe["observedAt"],
        "updatedAt": _base._timestamp(updated_at or probe["observedAt"], field="dailyState.updatedAt"),
        "metadataFingerprint": probe["metadataFingerprint"],
        "metadataIdentity": probe["metadataIdentity"],
        "catalogResponseSha256": probe["catalogResponseSha256"],
        "dailyObservationKey": resolution.get("dailyObservationKey"),
        "status": status,
    }
    source = resolution.get("source")
    if isinstance(source, Mapping):
        record["source"] = dict(source)
        release_id = source.get("candidateReleaseId")
        if isinstance(release_id, str):
            record["candidateReleaseId"] = release_id
    candidate = resolution.get("candidateReleaseId")
    if isinstance(candidate, str):
        record["candidateReleaseId"] = candidate
    if extra:
        record.update(dict(extra))
    return _write_daily_state(store=store, value=record)


def _phase(value: str) -> str:
    if not isinstance(value, str) or value not in CHECK_PHASES:
        raise AcquisitionScheduleError("acquisition check phase is invalid", code="INVALID_CONTROL_STATE")
    return value


def _late_recheck_key(argentina_day: str, fingerprint: str) -> str:
    primary = _base._observation_key(argentina_day, fingerprint)
    suffix = primary.removeprefix(f"{DAILY_OBSERVATION_PREFIX}/")
    return f"{LATE_RECHECK_PREFIX}/{suffix}"


def _decorate_resolution(
    value: Mapping[str, Any],
    *,
    key: str,
    check_phase: str,
    observation_kind: str,
) -> dict[str, Any]:
    return {
        **dict(value),
        "dailyObservationKey": key,
        "checkPhase": check_phase,
        "observationKind": observation_kind,
    }


def _resolve_at_key(
    value: Mapping[str, Any],
    probe: Mapping[str, Any],
    store: ObjectStore,
    *,
    key: str,
    check_phase: str,
    observation_kind: str,
) -> dict[str, Any]:
    resolved = _base._resolve_observation(value, probe, store)
    resolved = apply_processed_source_guard(resolved, store=store)
    return _decorate_resolution(
        resolved,
        key=key,
        check_phase=check_phase,
        observation_kind=observation_kind,
    )


def preflight_observation(
    *,
    discovery: DiscoveryResult,
    candidate: Any,
    store: ObjectStore,
    check_phase: str = CHECK_PHASE_PRIMARY,
    force_download: bool = False,
) -> dict[str, Any]:
    """Resolve one scheduled check without allowing duplicate expensive work."""

    phase = _phase(check_phase)
    if not isinstance(force_download, bool):
        raise AcquisitionScheduleError("force-download flag is invalid", code="INVALID_CONTROL_STATE")

    probe = _base._probe(discovery, candidate)
    primary_key = _base._observation_key(probe["argentinaDate"], probe["metadataFingerprint"])
    late_key = _late_recheck_key(probe["argentinaDate"], probe["metadataFingerprint"])
    prior_day_state = read_daily_state(store=store, argentina_day=probe["argentinaDate"])

    def finish(result: Mapping[str, Any]) -> dict[str, Any]:
        output = {
            **dict(result),
            "catalogResponseSha256": probe["catalogResponseSha256"],
            "lateCheckPolicy": LATE_CHECK_POLICY,
            "priorSameDayState": prior_day_state,
        }
        state_status = output.get("status")
        if state_status == "DOWNLOAD_REQUIRED":
            state_status = "METADATA_DISCOVERED"
        if state_status in _DAY_STATE_RANK:
            record: dict[str, Any] = {
                "argentinaDate": probe["argentinaDate"],
                "observedAt": probe["observedAt"],
                "updatedAt": probe["observedAt"],
                "metadataFingerprint": probe["metadataFingerprint"],
                "metadataIdentity": probe["metadataIdentity"],
                "catalogResponseSha256": probe["catalogResponseSha256"],
                "dailyObservationKey": output.get("dailyObservationKey"),
                "status": state_status,
            }
            if isinstance(output.get("source"), Mapping):
                record["source"] = dict(output["source"])
                record["candidateReleaseId"] = output["source"].get("candidateReleaseId")
            elif isinstance(output.get("candidateReleaseId"), str):
                record["candidateReleaseId"] = output["candidateReleaseId"]
            _write_daily_state(store=store, value=record)
        return output

    if force_download:
        target_key = late_key if phase == CHECK_PHASE_LATE_RECHECK else primary_key
        return finish({
            **probe,
            "status": "DOWNLOAD_REQUIRED",
            "reason": "OPERATOR_FORCED_BYTE_CHECK",
            "dailyObservationKey": target_key,
            "existingObservation": _base._read_store_json(store, target_key) is not None,
            "checkPhase": phase,
            "observationKind": "LATE_RECHECK" if target_key == late_key else "PRIMARY",
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        })

    primary = _base._read_store_json(store, primary_key)
    if primary is None:
        # If the 13:30 run was missed, a later phase is allowed to catch up.
        # A metadata change also lands here because it has a new fingerprint.
        return finish({
            **probe,
            "status": "DOWNLOAD_REQUIRED",
            "reason": "FIRST_BYTE_CHECK_FOR_METADATA_FINGERPRINT_TODAY",
            "dailyObservationKey": primary_key,
            "existingObservation": False,
            "checkPhase": phase,
            "observationKind": "PRIMARY",
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        })

    primary_resolution = _resolve_at_key(
        primary,
        probe,
        store,
        key=primary_key,
        check_phase=phase,
        observation_kind="PRIMARY",
    )

    if phase != CHECK_PHASE_LATE_RECHECK:
        return finish(primary_resolution)

    if primary_resolution.get("status") in {"SOURCE_READY", "CANDIDATE_PERSISTED"}:
        return finish(primary_resolution)

    if (
        primary_resolution.get("status") == "NO_BUILD_REQUIRED"
        and primary_resolution.get("reason") == SUPPRESSION_REASON
    ):
        return finish(primary_resolution)

    # Only a stale/no-change primary observation earns one late byte recheck.
    # The recheck is immutable and distinct from the primary observation.
    if primary_resolution.get("status") != "NO_BUILD_REQUIRED" or primary_resolution.get("disposition") not in {
        "NO_CHANGE",
        "OLDER_REJECTED",
    }:
        raise AcquisitionScheduleError("primary observation cannot be late-rechecked", code="INVALID_CONTROL_STATE")

    late = _base._read_store_json(store, late_key)
    if late is None:
        return finish({
            **probe,
            "status": "DOWNLOAD_REQUIRED",
            "reason": "LATE_BYTE_RECHECK_AFTER_STALE_PRIMARY",
            "dailyObservationKey": late_key,
            "existingObservation": False,
            "priorDisposition": primary_resolution.get("disposition"),
            "checkPhase": phase,
            "observationKind": "LATE_RECHECK",
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        })

    return finish(
        _resolve_at_key(
            late,
            probe,
            store,
            key=late_key,
            check_phase=phase,
            observation_kind="LATE_RECHECK",
        )
    )


def finalize_download_observation(
    *,
    preflight: Mapping[str, Any],
    handoff: Mapping[str, Any],
    store: ObjectStore,
    publish: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    """Persist one primary or late observation without overwriting either."""

    probe = _base._validate_probe(preflight)
    if preflight.get("status") != "DOWNLOAD_REQUIRED":
        raise AcquisitionScheduleError("finalize requires a download-required preflight", code="INVALID_CONTROL_STATE")

    phase = _phase(str(preflight.get("checkPhase", CHECK_PHASE_PRIMARY)))
    primary_key = _base._observation_key(probe["argentinaDate"], probe["metadataFingerprint"])
    late_key = _late_recheck_key(probe["argentinaDate"], probe["metadataFingerprint"])
    target_key = preflight.get("dailyObservationKey")

    if target_key == primary_key:
        observation_kind = "PRIMARY"
    elif target_key == late_key and phase == CHECK_PHASE_LATE_RECHECK:
        observation_kind = "LATE_RECHECK"
    else:
        raise AcquisitionScheduleError("preflight observation key is invalid", code="INVALID_CONTROL_STATE")

    _base._validate_handoff_binding(probe, handoff)
    status = handoff.get("status")
    if status not in {"HANDOFF_READY", "NO_CHANGE", "OLDER_REJECTED"}:
        raise AcquisitionScheduleError("handoff did not produce a safe acquisition disposition", code="INVALID_HANDOFF")

    source = _base._source_from_handoff(handoff)

    def finish(resolution: Mapping[str, Any]) -> dict[str, Any]:
        result = {
            **dict(resolution),
            "catalogResponseSha256": probe["catalogResponseSha256"],
        }
        _persist_resolution_state(resolution=result, store=store)
        return result

    existing = _base._read_store_json(store, target_key)
    if existing is not None:
        current = _base._validate_daily_observation(existing, probe)
        prior_source = current["source"]
        if any(prior_source.get(field) != source.get(field) for field in ("releaseDate", "sha256", "bytes")):
            raise AcquisitionScheduleError(
                "same observation slot produced different source bytes",
                code="SILENT_SOURCE_CHANGE_REVIEW_REQUIRED",
            )
        return finish(
            _resolve_at_key(
                current,
                probe,
                store,
                key=target_key,
                check_phase=phase,
                observation_kind=observation_kind,
            )
        )

    disposition = status
    persisted_source = dict(source)
    if status == "HANDOFF_READY":
        if not isinstance(publish, Mapping) or publish.get("status") != "PUBLISHED":
            raise AcquisitionScheduleError("verified source was not durably published", code="INVALID_HANDOFF")
        expected_key = f"source-archive/sha256/{source['sha256']}.zip"
        expected_manifest = f"source-archive/manifests/{source['sha256']}.json"
        if (
            publish.get("sourceArchiveKey") != expected_key
            or publish.get("sourceArchiveSha256") != source["sha256"]
            or publish.get("sourceBytes") != source["bytes"]
            or publish.get("manifestKey") != expected_manifest
        ):
            raise AcquisitionScheduleError("published archive identity differs from verified handoff", code="INVALID_HANDOFF")
        disposition = "SOURCE_READY"
        persisted_source.update(
            {
                "archiveKey": expected_key,
                "manifestKey": expected_manifest,
                "candidateReleaseId": _base.candidate_release_id(source["releaseDate"], source["sha256"]),
            }
        )

    value = _base._observation_value(probe, disposition=disposition, source=persisted_source)
    value["checkPhase"] = phase
    value["observationKind"] = observation_kind
    _base._put_store_json(store, target_key, value)

    stored = _base._read_store_json(store, target_key)
    if stored is None:
        raise AcquisitionScheduleError("daily observation disappeared after publication", code="CONTROL_STORE_UNAVAILABLE")
    return finish(
        _resolve_at_key(
            stored,
            probe,
            store,
            key=target_key,
            check_phase=phase,
            observation_kind=observation_kind,
        )
    )


def reserve_build_attempt(
    *,
    resolution: Mapping[str, Any],
    store: ObjectStore,
    reservation_id: str,
) -> dict[str, Any]:
    result = _base.reserve_build_attempt(resolution=resolution, store=store, reservation_id=reservation_id)
    if result.get("status") in {"RESERVED", "ALREADY_RESERVED"}:
        _persist_resolution_state(
            resolution=resolution,
            store=store,
            state_status="BUILD_RESERVED",
            extra={
                "buildReservationId": result.get("reservationId")
                or result.get("existingReservationId")
                or reservation_id,
                "candidateReleaseId": result.get("candidateReleaseId"),
            },
        )
    elif result.get("status") == "CANDIDATE_PERSISTED":
        _persist_resolution_state(
            resolution=resolution,
            store=store,
            state_status="CANDIDATE_PERSISTED",
            extra={"candidateReleaseId": result.get("candidateReleaseId")},
        )
    return result


def record_shadow_state(
    *,
    resolution: Mapping[str, Any],
    store: ObjectStore,
    status: str,
    workflow_execution: str | None = None,
    reservation_id: str | None = None,
    shadow_result: Mapping[str, Any] | None = None,
    recorded_at: str | None = None,
) -> dict[str, Any]:
    if status not in {"SHADOW_STARTED", "SHADOW_SUCCEEDED", "SHADOW_FAILED"}:
        raise AcquisitionScheduleError("SHADOW state is invalid", code="INVALID_CONTROL_STATE")
    if workflow_execution is not None and (not isinstance(workflow_execution, str) or len(workflow_execution) > 512):
        raise AcquisitionScheduleError("workflow execution identity is invalid", code="INVALID_CONTROL_STATE")
    if reservation_id is not None and (not isinstance(reservation_id, str) or len(reservation_id) > 128):
        raise AcquisitionScheduleError("build reservation identity is invalid", code="INVALID_CONTROL_STATE")
    extra: dict[str, Any] = {
        "workflowExecution": workflow_execution,
        "buildReservationId": reservation_id,
        "shadowResult": dict(shadow_result) if shadow_result is not None else None,
    }
    if extra["shadowResult"] is not None and len(_base.canonical_bytes(extra["shadowResult"])) > 16 * 1024:
        raise AcquisitionScheduleError("SHADOW result exceeds bound", code="INVALID_CONTROL_STATE")
    result = _persist_resolution_state(
        resolution=resolution,
        store=store,
        state_status=status,
        updated_at=recorded_at,
        extra=extra,
    )
    if result is None:
        raise AcquisitionScheduleError("SHADOW state could not be recorded", code="INVALID_CONTROL_STATE")
    return result


def upstream_outage_result(
    *,
    observed_at: str,
    current_release_date: str | None,
    current_source_sha256: str | None,
    error: AcquisitionError,
    same_day_state: Mapping[str, Any] | None,
    max_age_days: int = ACTIVE_RELEASE_MAX_AGE_DAYS,
) -> dict[str, Any]:
    """Classify a bounded metadata outage using only explicit active inputs."""

    if error.code != "UPSTREAM_TEMPORARILY_UNAVAILABLE":
        raise AcquisitionScheduleError(
            "outage classification requires an exhausted transient metadata failure",
            code="INVALID_CONTROL_STATE",
        )
    observed = _base._timestamp(observed_at, field="observedAt")
    argentina_day = _base.argentina_date(observed)
    base: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "lateCheckPolicy": LATE_CHECK_POLICY,
        "status": "ACTIVE_RELEASE_UNVERIFIED",
        "observedAt": observed,
        "argentinaDate": argentina_day,
        "upstreamFailure": {
            "code": error.cause_code or error.code,
            "httpStatus": error.http_status,
            "attempts": error.attempts,
            "retryPolicyVersion": "valuepilot-argentina-metadata-retry-v1",
        },
        "activeReleaseUnchanged": True,
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
        "sourceSubstitution": 0,
        "candidatePromotion": False,
        "freshness": {"state": "UNVERIFIED"},
        "sameDayState": None,
    }
    if same_day_state is not None:
        source = same_day_state.get("source")
        base["sameDayState"] = {
            "status": same_day_state.get("status"),
            "candidateReleaseId": same_day_state.get("candidateReleaseId"),
            "sourceReleaseDate": source.get("releaseDate") if isinstance(source, Mapping) else None,
            "sourceSha256": source.get("sha256") if isinstance(source, Mapping) else None,
            "buildReservationId": same_day_state.get("buildReservationId"),
            "workflowExecution": same_day_state.get("workflowExecution"),
            "shadowResult": same_day_state.get("shadowResult"),
        }
    if (
        isinstance(max_age_days, bool)
        or not isinstance(max_age_days, int)
        or max_age_days < 0
        or max_age_days > 365
        or not isinstance(current_release_date, str)
        or not isinstance(current_source_sha256, str)
        or _base._SHA256.fullmatch(current_source_sha256) is None
    ):
        return base
    try:
        release_date = _base._release_date(current_release_date)
        evaluated = _dt.datetime.fromisoformat(observed.replace("Z", "+00:00"))
        status = freshness(release_date, clock=FrozenClock(evaluated), max_age_days=max_age_days)
        age_days = (_dt.date.fromisoformat(argentina_day) - _dt.date.fromisoformat(release_date)).days
    except (ValueError, TypeError):
        return base
    days_remaining = max_age_days - age_days
    near_limit = status == "FRESH" and days_remaining <= FRESHNESS_WARNING_WINDOW_DAYS
    base.update(
        {
            "status": "UPSTREAM_TEMPORARILY_UNAVAILABLE" if status == "FRESH" else "FRESHNESS_LIMIT_EXCEEDED",
            "activeRelease": {
                "releaseDate": release_date,
                "sourceSha256": current_source_sha256,
            },
            "freshness": {
                "policy": "backend-release-calendar-age-v1",
                "state": "NEARING_LIMIT" if near_limit else status,
                "maxAgeDays": max_age_days,
                "ageDays": age_days,
                "daysRemaining": days_remaining,
                "evaluationDate": argentina_day,
                "warningWindowDays": FRESHNESS_WARNING_WINDOW_DAYS,
            },
            "operatorAttentionRequired": near_limit or status != "FRESH",
        }
    )
    return base


def metadata_change_evidence(
    *,
    metadata_fingerprint: str,
    catalog_response_sha256: str,
    resource_id: str | None,
    same_day_state: Mapping[str, Any] | None,
) -> dict[str, Any]:
    current = _base._sha(metadata_fingerprint, field="metadata fingerprint")
    response = _base._sha(catalog_response_sha256, field="catalog response SHA-256")
    previous = same_day_state.get("metadataFingerprint") if isinstance(same_day_state, Mapping) else None
    if previous is None:
        return {
            "upstreamChanged": False,
            "metadataChangeEvidenceStatus": "NO_PRIOR_DISCOVERY",
            "upstreamChangeEvidence": None,
        }
    previous = _base._sha(previous, field="previous metadata fingerprint")
    if previous == current:
        return {
            "upstreamChanged": False,
            "metadataChangeEvidenceStatus": "UNCHANGED",
            "upstreamChangeEvidence": None,
        }

    previous_response_value = same_day_state.get("catalogResponseSha256") if isinstance(same_day_state, Mapping) else None
    if previous_response_value is None:
        # Older daily-state records may not contain a response digest.  A
        # changed fingerprint without that raw-response anchor is not enough
        # to authorize a same-day source revision; wait for a complete pair of
        # discovery observations rather than substituting another identifier.
        return {
            "upstreamChanged": False,
            "metadataChangeEvidenceStatus": "CHANGE_UNPROVEN_PREVIOUS_RESPONSE_MISSING",
            "upstreamChangeEvidence": None,
        }
    previous_response = _base._sha(previous_response_value, field="previous catalog response SHA-256")
    if previous_response == response:
        raise AcquisitionScheduleError(
            "metadata fingerprint changed while the catalog response hash stayed identical",
            code="INVALID_CONTROL_STATE",
        )
    if not isinstance(resource_id, str) or not resource_id.strip() or len(resource_id) > 256:
        return {
            "upstreamChanged": False,
            "metadataChangeEvidenceStatus": "CHANGE_UNPROVEN_RESOURCE_ID_INVALID",
            "upstreamChangeEvidence": None,
        }
    return {
        "upstreamChanged": True,
        "metadataChangeEvidenceStatus": "CHANGE_PROVEN",
        "upstreamChangeEvidence": {
            # These four fields match the pinned release worker's
            # --upstream-change-evidence contract.  The metadata fingerprints
            # remain as additional auditable producer-side evidence.
            "changed": True,
            "resourceId": resource_id,
            "previousDiscoveryResponseSha256": previous_response,
            "currentDiscoveryResponseSha256": response,
            "previousMetadataFingerprint": previous,
            "currentMetadataFingerprint": current,
            "catalogResponseSha256": response,
        },
    }


def _gcs_store(bucket: str, project: str | None) -> GCSObjectStore:
    return GCSObjectStore(bucket=bucket, project=project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None)


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    preflight_parser = sub.add_parser("preflight")
    preflight_parser.add_argument("--observed-at", required=True)
    preflight_parser.add_argument("--metadata-endpoint", default=CATALOG_ENDPOINT)
    preflight_parser.add_argument("--archive-bucket", required=True)
    preflight_parser.add_argument("--archive-project")
    preflight_parser.add_argument("--current-release-id")
    preflight_parser.add_argument("--current-release-date")
    preflight_parser.add_argument("--current-source-sha256")
    preflight_parser.add_argument(
        "--active-release-max-age-days",
        type=int,
        default=ACTIVE_RELEASE_MAX_AGE_DAYS,
    )
    preflight_parser.add_argument("--check-phase", choices=sorted(CHECK_PHASES), default=CHECK_PHASE_PRIMARY)
    preflight_parser.add_argument("--force-download", action="store_true")
    preflight_parser.add_argument("--output", type=Path, required=True)

    finalize_parser = sub.add_parser("finalize")
    finalize_parser.add_argument("--preflight", type=Path, required=True)
    finalize_parser.add_argument("--handoff", type=Path, required=True)
    finalize_parser.add_argument("--publish", type=Path)
    finalize_parser.add_argument("--archive-bucket", required=True)
    finalize_parser.add_argument("--archive-project")
    finalize_parser.add_argument("--output", type=Path, required=True)

    reserve_parser = sub.add_parser("reserve")
    reserve_parser.add_argument("--resolution", type=Path, required=True)
    reserve_parser.add_argument("--reservation-id", required=True)
    reserve_parser.add_argument("--archive-bucket", required=True)
    reserve_parser.add_argument("--archive-project")
    reserve_parser.add_argument("--output", type=Path, required=True)

    shadow_parser = sub.add_parser("record-shadow-state")
    shadow_parser.add_argument("--resolution", type=Path, required=True)
    shadow_parser.add_argument("--status", choices=("SHADOW_STARTED", "SHADOW_SUCCEEDED", "SHADOW_FAILED"), required=True)
    shadow_parser.add_argument("--workflow-execution")
    shadow_parser.add_argument("--reservation-id")
    shadow_parser.add_argument("--recorded-at", required=True)
    shadow_parser.add_argument("--shadow-result", type=Path)
    shadow_parser.add_argument("--archive-bucket", required=True)
    shadow_parser.add_argument("--archive-project")
    shadow_parser.add_argument("--output", type=Path, required=True)

    args = parser.parse_args(argv)
    try:
        if args.command == "preflight":
            store = _gcs_store(args.archive_bucket, args.archive_project)
            observed_at = _base._timestamp(args.observed_at, field="observedAt")
            day = _base.argentina_date(observed_at)
            same_day_state = read_daily_state(store=store, argentina_day=day)
            try:
                discovery = discover_metadata(observed_at=observed_at, endpoint=args.metadata_endpoint)
            except AcquisitionError as exc:
                if exc.code != "UPSTREAM_TEMPORARILY_UNAVAILABLE":
                    raise
                result = upstream_outage_result(
                    observed_at=observed_at,
                    current_release_date=args.current_release_date,
                    current_source_sha256=args.current_source_sha256,
                    error=exc,
                    same_day_state=same_day_state,
                    max_age_days=args.active_release_max_age_days,
                )
                if args.current_release_id:
                    result["activeReleaseId"] = args.current_release_id
                _base._emit(result, args.output)
                return 0
            candidate = select_latest_candidate(discovery.candidates)
            result = preflight_observation(
                discovery=discovery,
                candidate=candidate,
                store=store,
                check_phase=args.check_phase,
                force_download=bool(args.force_download),
            )
            result["metadataAttempts"] = discovery.attempts
            result["metadataRetryPolicyVersion"] = "valuepilot-argentina-metadata-retry-v1"
            result["sameDayStateBeforeDiscovery"] = same_day_state
            result.update(
                metadata_change_evidence(
                    metadata_fingerprint=str(result["metadataFingerprint"]),
                    catalog_response_sha256=discovery.response_sha256,
                    resource_id=(
                        result.get("metadataIdentity", {}).get("resource", {}).get("resourceId")
                        if isinstance(result.get("metadataIdentity"), Mapping)
                        and isinstance(result.get("metadataIdentity", {}).get("resource"), Mapping)
                        else None
                    ),
                    same_day_state=same_day_state,
                )
            )
            _base._emit(result, args.output)
            return 0
        if args.command == "finalize":
            result = finalize_download_observation(
                preflight=_base._read_json_file(args.preflight, label="preflight report"),
                handoff=_base._read_json_file(args.handoff, label="handoff report"),
                publish=_base._read_json_file(args.publish, label="publish report") if args.publish else None,
                store=_gcs_store(args.archive_bucket, args.archive_project),
            )
            _base._emit(result, args.output)
            return 0
        if args.command == "reserve":
            result = reserve_build_attempt(
                resolution=_base._read_json_file(args.resolution, label="resolution report"),
                store=_gcs_store(args.archive_bucket, args.archive_project),
                reservation_id=args.reservation_id,
            )
            _base._emit(result, args.output)
            return 0
        if args.command == "record-shadow-state":
            shadow_result = (
                _base._read_json_file(args.shadow_result, label="SHADOW result")
                if args.shadow_result
                else None
            )
            result = record_shadow_state(
                resolution=_base._read_json_file(args.resolution, label="acquisition resolution"),
                store=_gcs_store(args.archive_bucket, args.archive_project),
                status=args.status,
                workflow_execution=args.workflow_execution,
                reservation_id=args.reservation_id,
                shadow_result=shadow_result,
                recorded_at=args.recorded_at,
            )
            _base._emit(result, args.output)
            return 0
        raise AcquisitionScheduleError("unknown command", code="INVALID_CONTROL_STATE")
    except (AcquisitionError, AcquisitionScheduleError, ObjectStoreError) as exc:
        error = {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "status": "FAILED",
            "error": getattr(exc, "code", "ACQUISITION_SCHEDULE_FAILED"),
            "message": str(exc),
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        }
        output = getattr(args, "output", None)
        if output is not None:
            try:
                _base._write_json_file(output, error)
            except Exception:
                pass
        print(json.dumps(error, ensure_ascii=False, sort_keys=True, separators=(",", ":")), file=os.sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = [
    "AcquisitionScheduleError",
    "ACTIVE_RELEASE_MAX_AGE_DAYS",
    "BUILD_ATTEMPT_PREFIX",
    "CHECK_PHASE_LATE_RECHECK",
    "CHECK_PHASE_METADATA_ONLY",
    "CHECK_PHASE_PRIMARY",
    "DAILY_OBSERVATION_PREFIX",
    "DAILY_STATE_EVENT_MAX",
    "DAILY_STATE_EVENT_PREFIX",
    "DAILY_STATE_POLICY_VERSION",
    "DAILY_STATE_SCHEMA_VERSION",
    "DAILY_STATE_PREFIX",
    "LATE_CHECK_POLICY",
    "LATE_RECHECK_PREFIX",
    "POLICY_VERSION",
    "SCHEMA_VERSION",
    "finalize_download_observation",
    "daily_state_event_prefix",
    "daily_state_key",
    "metadata_change_evidence",
    "preflight_observation",
    "read_daily_state",
    "record_shadow_state",
    "reserve_build_attempt",
    "upstream_outage_result",
]

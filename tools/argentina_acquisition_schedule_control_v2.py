"""Three-phase Argentina SEPA acquisition control.

This module layers a bounded late-day byte recheck over the immutable daily
observation semantics in ``argentina_acquisition_schedule_control``.

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
import json
import os
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.object_store import GCSObjectStore, ObjectStore, ObjectStoreError
from tools import argentina_acquisition_schedule_control as _base
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

    if force_download:
        target_key = late_key if phase == CHECK_PHASE_LATE_RECHECK else primary_key
        return {
            **probe,
            "status": "DOWNLOAD_REQUIRED",
            "reason": "OPERATOR_FORCED_BYTE_CHECK",
            "dailyObservationKey": target_key,
            "existingObservation": _base._read_store_json(store, target_key) is not None,
            "checkPhase": phase,
            "observationKind": "LATE_RECHECK" if target_key == late_key else "PRIMARY",
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        }

    primary = _base._read_store_json(store, primary_key)
    if primary is None:
        # If the 13:30 run was missed, a later phase is allowed to catch up.
        # A metadata change also lands here because it has a new fingerprint.
        return {
            **probe,
            "status": "DOWNLOAD_REQUIRED",
            "reason": "FIRST_BYTE_CHECK_FOR_METADATA_FINGERPRINT_TODAY",
            "dailyObservationKey": primary_key,
            "existingObservation": False,
            "checkPhase": phase,
            "observationKind": "PRIMARY",
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        }

    primary_resolution = _resolve_at_key(
        primary,
        probe,
        store,
        key=primary_key,
        check_phase=phase,
        observation_kind="PRIMARY",
    )

    if phase != CHECK_PHASE_LATE_RECHECK:
        return primary_resolution

    if primary_resolution.get("status") in {"SOURCE_READY", "CANDIDATE_PERSISTED"}:
        return primary_resolution

    # Only a stale/no-change primary observation earns one late byte recheck.
    # The recheck is immutable and distinct from the primary observation.
    if primary_resolution.get("status") != "NO_BUILD_REQUIRED" or primary_resolution.get("disposition") not in {
        "NO_CHANGE",
        "OLDER_REJECTED",
    }:
        raise AcquisitionScheduleError("primary observation cannot be late-rechecked", code="INVALID_CONTROL_STATE")

    late = _base._read_store_json(store, late_key)
    if late is None:
        return {
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
        }

    return _resolve_at_key(
        late,
        probe,
        store,
        key=late_key,
        check_phase=phase,
        observation_kind="LATE_RECHECK",
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
    existing = _base._read_store_json(store, target_key)
    if existing is not None:
        current = _base._validate_daily_observation(existing, probe)
        prior_source = current["source"]
        if any(prior_source.get(field) != source.get(field) for field in ("releaseDate", "sha256", "bytes")):
            raise AcquisitionScheduleError(
                "same observation slot produced different source bytes",
                code="SILENT_SOURCE_CHANGE_REVIEW_REQUIRED",
            )
        return _resolve_at_key(
            current,
            probe,
            store,
            key=target_key,
            check_phase=phase,
            observation_kind=observation_kind,
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
    return _resolve_at_key(
        stored,
        probe,
        store,
        key=target_key,
        check_phase=phase,
        observation_kind=observation_kind,
    )


def reserve_build_attempt(
    *,
    resolution: Mapping[str, Any],
    store: ObjectStore,
    reservation_id: str,
) -> dict[str, Any]:
    return _base.reserve_build_attempt(resolution=resolution, store=store, reservation_id=reservation_id)


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

    args = parser.parse_args(argv)
    try:
        if args.command == "preflight":
            discovery = discover_metadata(observed_at=args.observed_at, endpoint=args.metadata_endpoint)
            candidate = select_latest_candidate(discovery.candidates)
            result = preflight_observation(
                discovery=discovery,
                candidate=candidate,
                store=_gcs_store(args.archive_bucket, args.archive_project),
                check_phase=args.check_phase,
                force_download=bool(args.force_download),
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
    "BUILD_ATTEMPT_PREFIX",
    "CHECK_PHASE_LATE_RECHECK",
    "CHECK_PHASE_METADATA_ONLY",
    "CHECK_PHASE_PRIMARY",
    "DAILY_OBSERVATION_PREFIX",
    "LATE_RECHECK_PREFIX",
    "POLICY_VERSION",
    "SCHEMA_VERSION",
    "finalize_download_observation",
    "preflight_observation",
    "reserve_build_attempt",
]

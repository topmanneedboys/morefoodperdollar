"""Bounded daily dedupe control for scheduled Argentina SEPA acquisition.

The expensive provider ZIP is intentionally not used as a polling primitive.
For each Argentina civil date this module records an immutable observation that
binds one stable CKAN candidate fingerprint to the exact verified source bytes
(or to a safe no-build disposition). Repeated checks on the same day can then
reuse that evidence without reopening the Argentina egress tunnel. A metadata
change creates a different fingerprint and therefore forces a new acquisition.
The next Argentina day also forces one fresh byte-level check even when CKAN
metadata is unchanged, bounding silent upstream changes to one business day.

The control objects live only in the private archive bucket. They never write
ACTIVE or CANARY and they never make a freshness decision from CKAN metadata
alone. Release identity still comes only from the inspected ZIP date and
SHA-256 produced by the existing acquisition handoff.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.object_store import GCSObjectStore, ObjectStore, ObjectStoreError
from tools.argentina_object_store_publisher import ObjectStorePublicationError, ObjectStoreReleasePublisher
from tools.argentina_release_acquisition import (
    AcquisitionCandidate,
    AcquisitionError,
    CATALOG_ENDPOINT,
    DiscoveryResult,
    canonical_bytes,
    discover_metadata,
    select_latest_candidate,
)


SCHEMA_VERSION = "valuepilot-argentina-acquisition-schedule-v1"
POLICY_VERSION = "valuepilot-argentina-acquisition-schedule-policy-v1"
DAILY_OBSERVATION_PREFIX = "acquisition/daily-observations"
BUILD_ATTEMPT_PREFIX = "acquisition/build-attempts"
MAX_CONTROL_BYTES = 256 * 1024
MAX_ARCHIVE_MANIFEST_BYTES = 256 * 1024
ARGENTINA_UTC_OFFSET = _dt.timedelta(hours=-3)
_FINAL_RELEASE_SUFFIX = "-routing-v1-search-v2"
_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_RELEASE_ID = re.compile(r"^[A-Za-z0-9._-]{1,128}$")
_RESERVATION_ID = re.compile(r"^[A-Za-z0-9._-]{1,128}$")
_ALLOWED_DISPOSITIONS = frozenset({"SOURCE_READY", "NO_CHANGE", "OLDER_REJECTED"})


class AcquisitionScheduleError(RuntimeError):
    def __init__(self, message: str, *, code: str = "ACQUISITION_SCHEDULE_FAILED") -> None:
        super().__init__(message)
        self.code = code


def _timestamp(value: str, *, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise AcquisitionScheduleError(f"{field} is required", code="INVALID_CONTROL_STATE")
    try:
        parsed = _dt.datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError as exc:
        raise AcquisitionScheduleError(f"{field} is invalid", code="INVALID_CONTROL_STATE") from exc
    if parsed.tzinfo is None:
        raise AcquisitionScheduleError(f"{field} must include a timezone", code="INVALID_CONTROL_STATE")
    return parsed.astimezone(_dt.timezone.utc).isoformat().replace("+00:00", "Z")


def argentina_date(observed_at: str) -> str:
    observed = _dt.datetime.fromisoformat(_timestamp(observed_at, field="observedAt").replace("Z", "+00:00"))
    return (observed + ARGENTINA_UTC_OFFSET).date().isoformat()


def _release_date(value: Any) -> str:
    if not isinstance(value, str):
        raise AcquisitionScheduleError("source release date is invalid", code="INVALID_CONTROL_STATE")
    try:
        return _dt.date.fromisoformat(value).isoformat()
    except ValueError as exc:
        raise AcquisitionScheduleError("source release date is invalid", code="INVALID_CONTROL_STATE") from exc


def _sha(value: Any, *, field: str = "source SHA-256") -> str:
    if not isinstance(value, str) or _SHA256.fullmatch(value) is None:
        raise AcquisitionScheduleError(f"{field} is invalid", code="INVALID_CONTROL_STATE")
    return value


def candidate_release_id(release_date: str, source_sha256: str) -> str:
    date = _release_date(release_date)
    digest = _sha(source_sha256)
    value = f"argentina-sepa-{date}-{digest[:16]}{_FINAL_RELEASE_SUFFIX}"
    if _RELEASE_ID.fullmatch(value) is None:
        raise AcquisitionScheduleError("candidate release ID is invalid", code="INVALID_CONTROL_STATE")
    return value


def metadata_identity(discovery: DiscoveryResult, candidate: AcquisitionCandidate) -> dict[str, Any]:
    observed = _timestamp(discovery.observed_at, field="discovery observedAt")
    if _timestamp(candidate.observed_at, field="candidate observedAt") != observed:
        raise AcquisitionScheduleError("candidate observation is not bound to discovery", code="INVALID_CONTROL_STATE")
    if candidate.dataset_id != discovery.dataset_id or candidate.discovery_response_sha256 != discovery.response_sha256:
        raise AcquisitionScheduleError("candidate is not bound to discovery", code="INVALID_CONTROL_STATE")
    return {
        "dataset": {
            "id": discovery.dataset_id,
            "name": discovery.dataset_name,
            "title": discovery.dataset_title,
            "licenseTitle": discovery.license_title,
        },
        "resource": {
            "provider": candidate.provider,
            "resourceId": candidate.resource_id,
            "resourceName": candidate.resource_name,
            "officialUrl": candidate.official_url,
            "observedSize": candidate.observed_size,
            "lastModified": candidate.last_modified,
            "releaseDateHint": candidate.release_date_hint,
            "description": candidate.description,
            "format": candidate.format,
            "expectedSourceHost": candidate.expected_source_host,
        },
    }


def metadata_fingerprint(identity: Mapping[str, Any]) -> str:
    if not isinstance(identity, Mapping) or not identity:
        raise AcquisitionScheduleError("metadata identity is invalid", code="INVALID_CONTROL_STATE")
    try:
        raw = canonical_bytes(dict(identity))
    except (TypeError, ValueError, OverflowError) as exc:
        raise AcquisitionScheduleError("metadata identity is not serializable", code="INVALID_CONTROL_STATE") from exc
    return hashlib.sha256(raw).hexdigest()


def _probe(discovery: DiscoveryResult, candidate: AcquisitionCandidate) -> dict[str, Any]:
    identity = metadata_identity(discovery, candidate)
    observed = _timestamp(discovery.observed_at, field="observedAt")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "observedAt": observed,
        "argentinaDate": argentina_date(observed),
        "metadataFingerprint": metadata_fingerprint(identity),
        "metadataIdentity": identity,
        "catalogResponseSha256": _sha(discovery.response_sha256, field="catalog response SHA-256"),
    }


def _observation_key(argentina_day: str, fingerprint: str) -> str:
    try:
        day = _dt.date.fromisoformat(argentina_day).isoformat()
    except (TypeError, ValueError) as exc:
        raise AcquisitionScheduleError("Argentina date is invalid", code="INVALID_CONTROL_STATE") from exc
    digest = _sha(fingerprint, field="metadata fingerprint")
    return f"{DAILY_OBSERVATION_PREFIX}/{day}/{digest}.json"


def _attempt_key(argentina_day: str, release_id: str) -> str:
    try:
        day = _dt.date.fromisoformat(argentina_day).isoformat()
    except (TypeError, ValueError) as exc:
        raise AcquisitionScheduleError("Argentina date is invalid", code="INVALID_CONTROL_STATE") from exc
    if not isinstance(release_id, str) or _RELEASE_ID.fullmatch(release_id) is None:
        raise AcquisitionScheduleError("candidate release ID is invalid", code="INVALID_CONTROL_STATE")
    return f"{BUILD_ATTEMPT_PREFIX}/{day}/{release_id}.json"


def _read_store_json(store: ObjectStore, key: str, *, maximum: int = MAX_CONTROL_BYTES) -> dict[str, Any] | None:
    try:
        if not store.exists(key):
            return None
        metadata = store.head(key)
        if metadata.size <= 0 or metadata.size > maximum:
            raise AcquisitionScheduleError("control object size is invalid", code="INVALID_CONTROL_STATE")
        raw = store.get(key)
    except AcquisitionScheduleError:
        raise
    except ObjectStoreError as exc:
        raise AcquisitionScheduleError("control object availability is unknown", code="CONTROL_STORE_UNAVAILABLE") from exc
    if len(raw) != metadata.size or len(raw) > maximum:
        raise AcquisitionScheduleError("control object size is inconsistent", code="INVALID_CONTROL_STATE")
    digest = hashlib.sha256(raw).hexdigest()
    if metadata.sha256 is not None and metadata.sha256 != digest:
        raise AcquisitionScheduleError("control object hash is inconsistent", code="INVALID_CONTROL_STATE")
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AcquisitionScheduleError("control object is invalid JSON", code="INVALID_CONTROL_STATE") from exc
    if not isinstance(value, dict):
        raise AcquisitionScheduleError("control object is invalid", code="INVALID_CONTROL_STATE")
    return value


def _put_store_json(store: ObjectStore, key: str, value: Mapping[str, Any]) -> None:
    raw = canonical_bytes(dict(value)) + b"\n"
    if len(raw) > MAX_CONTROL_BYTES:
        raise AcquisitionScheduleError("control object exceeds bound", code="INVALID_CONTROL_STATE")
    try:
        store.put_immutable(key, raw, sha256=hashlib.sha256(raw).hexdigest())
    except ObjectStoreError as exc:
        raise AcquisitionScheduleError("immutable control object conflicts with existing evidence", code="CONTROL_CONFLICT") from exc


def _validate_probe(value: Mapping[str, Any]) -> dict[str, Any]:
    if value.get("schemaVersion") != SCHEMA_VERSION or value.get("policyVersion") != POLICY_VERSION:
        raise AcquisitionScheduleError("preflight schema or policy is unsupported", code="INVALID_CONTROL_STATE")
    observed = _timestamp(value.get("observedAt"), field="observedAt")
    day = argentina_date(observed)
    if value.get("argentinaDate") != day:
        raise AcquisitionScheduleError("preflight Argentina date is inconsistent", code="INVALID_CONTROL_STATE")
    identity = value.get("metadataIdentity")
    if not isinstance(identity, Mapping):
        raise AcquisitionScheduleError("preflight metadata identity is invalid", code="INVALID_CONTROL_STATE")
    fingerprint = metadata_fingerprint(identity)
    if value.get("metadataFingerprint") != fingerprint:
        raise AcquisitionScheduleError("preflight metadata fingerprint is inconsistent", code="INVALID_CONTROL_STATE")
    return {
        "observedAt": observed,
        "argentinaDate": day,
        "metadataFingerprint": fingerprint,
        "metadataIdentity": dict(identity),
        "catalogResponseSha256": _sha(value.get("catalogResponseSha256"), field="catalog response SHA-256"),
    }


def _source_from_handoff(report: Mapping[str, Any]) -> dict[str, Any]:
    source = report.get("source")
    if not isinstance(source, Mapping):
        raise AcquisitionScheduleError("handoff source identity is missing", code="INVALID_HANDOFF")
    size = source.get("bytes")
    if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
        raise AcquisitionScheduleError("handoff source size is invalid", code="INVALID_HANDOFF")
    return {
        "releaseDate": _release_date(source.get("releaseDate")),
        "sha256": _sha(source.get("sha256")),
        "bytes": size,
    }


def _validate_handoff_binding(probe: Mapping[str, Any], report: Mapping[str, Any]) -> None:
    candidate = report.get("candidate")
    identity = probe.get("metadataIdentity")
    resource = identity.get("resource") if isinstance(identity, Mapping) else None
    if not isinstance(candidate, Mapping) or not isinstance(resource, Mapping):
        raise AcquisitionScheduleError("handoff candidate identity is missing", code="INVALID_HANDOFF")
    for handoff_field, identity_field in (
        ("resourceId", "resourceId"),
        ("resourceName", "resourceName"),
        ("officialUrl", "officialUrl"),
    ):
        if candidate.get(handoff_field) != resource.get(identity_field):
            raise AcquisitionScheduleError("handoff candidate changed after metadata preflight", code="SOURCE_METADATA_RACE")
    dataset = identity.get("dataset") if isinstance(identity, Mapping) else None
    if not isinstance(dataset, Mapping) or report.get("datasetId") != dataset.get("id"):
        raise AcquisitionScheduleError("handoff dataset changed after metadata preflight", code="SOURCE_METADATA_RACE")


def _validate_source_archive(store: ObjectStore, source: Mapping[str, Any]) -> None:
    digest = _sha(source.get("sha256"))
    size = source.get("bytes")
    if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
        raise AcquisitionScheduleError("source archive size is invalid", code="INVALID_CONTROL_STATE")
    key = source.get("archiveKey")
    manifest_key = source.get("manifestKey")
    expected_key = f"source-archive/sha256/{digest}.zip"
    expected_manifest = f"source-archive/manifests/{digest}.json"
    if key != expected_key or manifest_key != expected_manifest:
        raise AcquisitionScheduleError("source archive key is not content-addressed", code="INVALID_CONTROL_STATE")
    try:
        metadata = store.head(expected_key)
        if metadata.size != size or metadata.sha256 != digest:
            raise AcquisitionScheduleError("source archive identity is inconsistent", code="ARCHIVE_INVALID")
        manifest_metadata = store.head(expected_manifest)
        if manifest_metadata.size <= 0 or manifest_metadata.size > MAX_ARCHIVE_MANIFEST_BYTES:
            raise AcquisitionScheduleError("source archive manifest size is invalid", code="ARCHIVE_INVALID")
        raw = store.get(expected_manifest)
    except AcquisitionScheduleError:
        raise
    except ObjectStoreError as exc:
        raise AcquisitionScheduleError("source archive is unavailable", code="ARCHIVE_UNAVAILABLE") from exc
    try:
        manifest = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AcquisitionScheduleError("source archive manifest is invalid", code="ARCHIVE_INVALID") from exc
    if not isinstance(manifest, Mapping):
        raise AcquisitionScheduleError("source archive manifest is invalid", code="ARCHIVE_INVALID")
    if (
        manifest.get("sourceSha256") != digest
        or manifest.get("bytes") != size
        or manifest.get("releaseDate") != _release_date(source.get("releaseDate"))
        or manifest.get("archiveKey") != expected_key
        or manifest.get("rawProviderDataCommitted") is not False
        or manifest.get("runtimePublicationAllowed") is not False
    ):
        raise AcquisitionScheduleError("source archive manifest identity is inconsistent", code="ARCHIVE_INVALID")


def _candidate_archive_status(store: ObjectStore, release_id: str) -> dict[str, Any]:
    if not isinstance(release_id, str) or _RELEASE_ID.fullmatch(release_id) is None:
        raise AcquisitionScheduleError("candidate release ID is invalid", code="INVALID_CONTROL_STATE")
    manifest_key = f"releases/{release_id}/manifest.json"
    try:
        if not store.exists(manifest_key):
            return {"status": "MISSING", "releaseId": release_id}
        verification = ObjectStoreReleasePublisher().verify_on_store(store, release_id)
    except ObjectStorePublicationError as exc:
        raise AcquisitionScheduleError("persisted candidate failed immutable verification", code="CANDIDATE_ARCHIVE_INVALID") from exc
    except ObjectStoreError as exc:
        raise AcquisitionScheduleError("candidate archive availability is unknown", code="CONTROL_STORE_UNAVAILABLE") from exc
    return {"status": "PERSISTED", "releaseId": release_id, "verification": verification}


def _validate_daily_observation(value: Mapping[str, Any], probe: Mapping[str, Any]) -> dict[str, Any]:
    if value.get("schemaVersion") != SCHEMA_VERSION or value.get("policyVersion") != POLICY_VERSION:
        raise AcquisitionScheduleError("daily observation schema or policy is unsupported", code="INVALID_CONTROL_STATE")
    if value.get("argentinaDate") != probe["argentinaDate"] or value.get("metadataFingerprint") != probe["metadataFingerprint"]:
        raise AcquisitionScheduleError("daily observation identity is inconsistent", code="INVALID_CONTROL_STATE")
    if value.get("metadataIdentity") != probe["metadataIdentity"]:
        raise AcquisitionScheduleError("daily observation metadata identity is inconsistent", code="INVALID_CONTROL_STATE")
    disposition = value.get("disposition")
    if disposition not in _ALLOWED_DISPOSITIONS:
        raise AcquisitionScheduleError("daily observation disposition is invalid", code="INVALID_CONTROL_STATE")
    source = value.get("source")
    if not isinstance(source, Mapping):
        raise AcquisitionScheduleError("daily observation source is invalid", code="INVALID_CONTROL_STATE")
    release = _release_date(source.get("releaseDate"))
    digest = _sha(source.get("sha256"))
    size = source.get("bytes")
    if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
        raise AcquisitionScheduleError("daily observation source size is invalid", code="INVALID_CONTROL_STATE")
    normalized_source: dict[str, Any] = {"releaseDate": release, "sha256": digest, "bytes": size}
    if disposition == "SOURCE_READY":
        release_id = candidate_release_id(release, digest)
        if source.get("candidateReleaseId") != release_id:
            raise AcquisitionScheduleError("daily observation candidate release is inconsistent", code="INVALID_CONTROL_STATE")
        normalized_source.update(
            {
                "archiveKey": source.get("archiveKey"),
                "manifestKey": source.get("manifestKey"),
                "candidateReleaseId": release_id,
            }
        )
    return {**dict(value), "source": normalized_source}


def _resolve_observation(value: Mapping[str, Any], probe: Mapping[str, Any], store: ObjectStore) -> dict[str, Any]:
    observation = _validate_daily_observation(value, probe)
    disposition = observation["disposition"]
    common = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "observedAt": probe["observedAt"],
        "argentinaDate": probe["argentinaDate"],
        "metadataFingerprint": probe["metadataFingerprint"],
        "metadataIdentity": probe["metadataIdentity"],
        "dailyObservationKey": _observation_key(probe["argentinaDate"], probe["metadataFingerprint"]),
        "disposition": disposition,
        "source": observation["source"],
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
    }
    if disposition in {"NO_CHANGE", "OLDER_REJECTED"}:
        return {**common, "status": "NO_BUILD_REQUIRED", "reason": disposition}
    _validate_source_archive(store, observation["source"])
    candidate = _candidate_archive_status(store, observation["source"]["candidateReleaseId"])
    if candidate["status"] == "PERSISTED":
        return {**common, "status": "CANDIDATE_PERSISTED", "reason": "VERIFIED_CANDIDATE_ALREADY_ARCHIVED", "candidate": candidate}
    return {**common, "status": "SOURCE_READY", "reason": "VERIFIED_SOURCE_ARCHIVED_CANDIDATE_MISSING", "candidate": candidate}


def preflight_observation(
    *,
    discovery: DiscoveryResult,
    candidate: AcquisitionCandidate,
    store: ObjectStore,
    force_download: bool = False,
) -> dict[str, Any]:
    if not isinstance(force_download, bool):
        raise AcquisitionScheduleError("force-download flag is invalid", code="INVALID_CONTROL_STATE")
    probe = _probe(discovery, candidate)
    key = _observation_key(probe["argentinaDate"], probe["metadataFingerprint"])
    existing = _read_store_json(store, key)
    if force_download:
        return {
            **probe,
            "status": "DOWNLOAD_REQUIRED",
            "reason": "OPERATOR_FORCED_BYTE_CHECK",
            "dailyObservationKey": key,
            "existingObservation": existing is not None,
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        }
    if existing is None:
        return {
            **probe,
            "status": "DOWNLOAD_REQUIRED",
            "reason": "FIRST_BYTE_CHECK_FOR_METADATA_FINGERPRINT_TODAY",
            "dailyObservationKey": key,
            "existingObservation": False,
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        }
    return _resolve_observation(existing, probe, store)


def _observation_value(probe: Mapping[str, Any], *, disposition: str, source: Mapping[str, Any]) -> dict[str, Any]:
    if disposition not in _ALLOWED_DISPOSITIONS:
        raise AcquisitionScheduleError("daily observation disposition is invalid", code="INVALID_CONTROL_STATE")
    value = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "argentinaDate": probe["argentinaDate"],
        "metadataFingerprint": probe["metadataFingerprint"],
        "metadataIdentity": probe["metadataIdentity"],
        "disposition": disposition,
        "source": dict(source),
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
    }
    return value


def finalize_download_observation(
    *,
    preflight: Mapping[str, Any],
    handoff: Mapping[str, Any],
    store: ObjectStore,
    publish: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    probe = _validate_probe(preflight)
    if preflight.get("status") != "DOWNLOAD_REQUIRED":
        raise AcquisitionScheduleError("finalize requires a download-required preflight", code="INVALID_CONTROL_STATE")
    _validate_handoff_binding(probe, handoff)
    status = handoff.get("status")
    if status not in {"HANDOFF_READY", "NO_CHANGE", "OLDER_REJECTED"}:
        raise AcquisitionScheduleError("handoff did not produce a safe acquisition disposition", code="INVALID_HANDOFF")
    source = _source_from_handoff(handoff)
    key = _observation_key(probe["argentinaDate"], probe["metadataFingerprint"])
    existing = _read_store_json(store, key)
    if existing is not None:
        current = _validate_daily_observation(existing, probe)
        prior_source = current["source"]
        if any(prior_source.get(field) != source.get(field) for field in ("releaseDate", "sha256", "bytes")):
            raise AcquisitionScheduleError(
                "same-day source bytes changed without a selected-metadata fingerprint change",
                code="SILENT_SOURCE_CHANGE_REVIEW_REQUIRED",
            )
        return _resolve_observation(current, probe, store)

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
                "candidateReleaseId": candidate_release_id(source["releaseDate"], source["sha256"]),
            }
        )

    value = _observation_value(probe, disposition=disposition, source=persisted_source)
    _put_store_json(store, key, value)
    stored = _read_store_json(store, key)
    if stored is None:
        raise AcquisitionScheduleError("daily observation disappeared after publication", code="CONTROL_STORE_UNAVAILABLE")
    return _resolve_observation(stored, probe, store)


def reserve_build_attempt(
    *,
    resolution: Mapping[str, Any],
    store: ObjectStore,
    reservation_id: str,
) -> dict[str, Any]:
    if not isinstance(reservation_id, str) or _RESERVATION_ID.fullmatch(reservation_id) is None:
        raise AcquisitionScheduleError("build reservation ID is invalid", code="INVALID_CONTROL_STATE")
    if resolution.get("schemaVersion") != SCHEMA_VERSION or resolution.get("policyVersion") != POLICY_VERSION:
        raise AcquisitionScheduleError("resolution schema or policy is unsupported", code="INVALID_CONTROL_STATE")
    if resolution.get("status") == "CANDIDATE_PERSISTED":
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "status": "CANDIDATE_PERSISTED",
            "candidateReleaseId": resolution.get("source", {}).get("candidateReleaseId") if isinstance(resolution.get("source"), Mapping) else None,
            "activePointerWrites": 0,
        }
    if resolution.get("status") != "SOURCE_READY":
        raise AcquisitionScheduleError("only a source-ready resolution can reserve a build", code="INVALID_CONTROL_STATE")
    day = resolution.get("argentinaDate")
    try:
        day = _dt.date.fromisoformat(str(day)).isoformat()
    except ValueError as exc:
        raise AcquisitionScheduleError("resolution Argentina date is invalid", code="INVALID_CONTROL_STATE") from exc
    source = resolution.get("source")
    if not isinstance(source, Mapping):
        raise AcquisitionScheduleError("resolution source is invalid", code="INVALID_CONTROL_STATE")
    release_id = source.get("candidateReleaseId")
    if release_id != candidate_release_id(source.get("releaseDate"), source.get("sha256")):
        raise AcquisitionScheduleError("resolution candidate release is inconsistent", code="INVALID_CONTROL_STATE")
    _validate_source_archive(store, source)
    candidate = _candidate_archive_status(store, release_id)
    if candidate["status"] == "PERSISTED":
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "status": "CANDIDATE_PERSISTED",
            "candidateReleaseId": release_id,
            "candidate": candidate,
            "activePointerWrites": 0,
        }

    key = _attempt_key(day, release_id)
    existing = _read_store_json(store, key)
    if existing is not None:
        if existing.get("candidateReleaseId") != release_id or existing.get("sourceSha256") != source.get("sha256"):
            raise AcquisitionScheduleError("build-attempt marker conflicts with source identity", code="CONTROL_CONFLICT")
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "status": "ALREADY_RESERVED",
            "reason": "ONE_BUILD_ATTEMPT_PER_CANDIDATE_PER_ARGENTINA_DAY",
            "attemptKey": key,
            "candidateReleaseId": release_id,
            "existingReservationId": existing.get("reservationId"),
            "activePointerWrites": 0,
        }

    marker = {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "kind": "BUILD_ATTEMPT_RESERVATION",
        "argentinaDate": day,
        "candidateReleaseId": release_id,
        "sourceSha256": source.get("sha256"),
        "sourceArchiveKey": source.get("archiveKey"),
        "reservationId": reservation_id,
        "promotionMode": "SHADOW",
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
    }
    try:
        _put_store_json(store, key, marker)
    except AcquisitionScheduleError as exc:
        if exc.code != "CONTROL_CONFLICT":
            raise
        winner = _read_store_json(store, key)
        if winner is None or winner.get("candidateReleaseId") != release_id or winner.get("sourceSha256") != source.get("sha256"):
            raise
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "status": "ALREADY_RESERVED",
            "reason": "BUILD_ATTEMPT_RACE_LOST",
            "attemptKey": key,
            "candidateReleaseId": release_id,
            "existingReservationId": winner.get("reservationId"),
            "activePointerWrites": 0,
        }
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "status": "RESERVED",
        "reason": "FIRST_BUILD_ATTEMPT_FOR_CANDIDATE_TODAY",
        "attemptKey": key,
        "candidateReleaseId": release_id,
        "reservationId": reservation_id,
        "sourceArchiveKey": source.get("archiveKey"),
        "sourceArchiveSha256": source.get("sha256"),
        "releaseDate": source.get("releaseDate"),
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
    }


def _read_json_file(path: Path | str, *, label: str) -> dict[str, Any]:
    try:
        raw = Path(path).read_bytes()
    except OSError as exc:
        raise AcquisitionScheduleError(f"{label} is unavailable", code="INVALID_CONTROL_STATE") from exc
    if not raw or len(raw) > MAX_CONTROL_BYTES:
        raise AcquisitionScheduleError(f"{label} size is invalid", code="INVALID_CONTROL_STATE")
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AcquisitionScheduleError(f"{label} is invalid JSON", code="INVALID_CONTROL_STATE") from exc
    if not isinstance(value, dict):
        raise AcquisitionScheduleError(f"{label} is invalid", code="INVALID_CONTROL_STATE")
    return value


def _write_json_file(path: Path | str, value: Mapping[str, Any]) -> None:
    raw = canonical_bytes(dict(value)) + b"\n"
    if len(raw) > MAX_CONTROL_BYTES:
        raise AcquisitionScheduleError("output report exceeds bound", code="INVALID_CONTROL_STATE")
    target = Path(path).resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".tmp", dir=str(target.parent))
    temporary = Path(name)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def _gcs_store(bucket: str, project: str | None) -> GCSObjectStore:
    return GCSObjectStore(bucket=bucket, project=project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None)


def _emit(value: Mapping[str, Any], output: Path | None) -> None:
    if output is not None:
        _write_json_file(output, value)
    print(json.dumps(dict(value), ensure_ascii=False, sort_keys=True, separators=(",", ":")))


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    preflight_parser = sub.add_parser("preflight")
    preflight_parser.add_argument("--observed-at", required=True)
    preflight_parser.add_argument("--metadata-endpoint", default=CATALOG_ENDPOINT)
    preflight_parser.add_argument("--archive-bucket", required=True)
    preflight_parser.add_argument("--archive-project")
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
                force_download=bool(args.force_download),
            )
            _emit(result, args.output)
            return 0
        if args.command == "finalize":
            result = finalize_download_observation(
                preflight=_read_json_file(args.preflight, label="preflight report"),
                handoff=_read_json_file(args.handoff, label="handoff report"),
                publish=_read_json_file(args.publish, label="publish report") if args.publish else None,
                store=_gcs_store(args.archive_bucket, args.archive_project),
            )
            _emit(result, args.output)
            return 0
        if args.command == "reserve":
            result = reserve_build_attempt(
                resolution=_read_json_file(args.resolution, label="resolution report"),
                store=_gcs_store(args.archive_bucket, args.archive_project),
                reservation_id=args.reservation_id,
            )
            _emit(result, args.output)
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
                _write_json_file(output, error)
            except Exception:
                pass
        print(json.dumps(error, ensure_ascii=False, sort_keys=True, separators=(",", ":")), file=os.sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = [
    "AcquisitionScheduleError",
    "BUILD_ATTEMPT_PREFIX",
    "DAILY_OBSERVATION_PREFIX",
    "POLICY_VERSION",
    "SCHEMA_VERSION",
    "argentina_date",
    "candidate_release_id",
    "finalize_download_observation",
    "metadata_fingerprint",
    "metadata_identity",
    "preflight_observation",
    "reserve_build_attempt",
]

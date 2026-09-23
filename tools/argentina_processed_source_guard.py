"""Fail-closed suppression for exact Argentina sources already processed in SHADOW.

This module handles one narrow historical edge case: an official source may have
completed a verified SHADOW build before durable candidate persistence was added.

A content-addressed immutable marker can prove that exact source was already
processed. A valid marker suppresses only the duplicate heavy build. It never
writes ACTIVE or CANARY and cannot claim that a candidate was persisted.

Missing markers are ignored. Present but malformed or mismatched markers fail
closed.
"""

from __future__ import annotations

from typing import Any, Mapping

from backend.object_store import ObjectStore
from tools import argentina_acquisition_schedule_control as _base


SCHEMA_VERSION = "valuepilot-argentina-processed-source-v1"
POLICY_VERSION = "valuepilot-argentina-processed-source-policy-v1"
PROCESSED_SOURCE_PREFIX = "acquisition/processed-sources"
MARKER_KIND = "VERIFIED_SHADOW_PROCESSED_SOURCE"
MARKER_DISPOSITION = "VERIFIED_SHADOW_PROCESSED_NO_CANDIDATE"
SUPPRESSION_REASON = "PROCESSED_SOURCE_NO_REBUILD"


def processed_source_key(source_sha256: str) -> str:
    digest = _base._sha(source_sha256)
    return f"{PROCESSED_SOURCE_PREFIX}/{digest}.json"


def _invalid(message: str) -> _base.AcquisitionScheduleError:
    return _base.AcquisitionScheduleError(
        message,
        code="PROCESSED_SOURCE_MARKER_INVALID",
    )


def _nonempty_string(value: Any, *, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise _invalid(f"processed-source marker {field} is invalid")
    return value.strip()


def _expected_source(source: Mapping[str, Any]) -> dict[str, str]:
    if not isinstance(source, Mapping):
        raise _invalid("processed-source resolution source is invalid")

    try:
        release_date = _base._release_date(source.get("releaseDate"))
        digest = _base._sha(source.get("sha256"))
        release_id = _base.candidate_release_id(release_date, digest)
    except _base.AcquisitionScheduleError as exc:
        raise _invalid("processed-source resolution identity is invalid") from exc

    archive_key = f"source-archive/sha256/{digest}.zip"
    manifest_key = f"source-archive/manifests/{digest}.json"

    if source.get("archiveKey") != archive_key:
        raise _invalid("processed-source resolution archive key is inconsistent")
    if source.get("manifestKey") != manifest_key:
        raise _invalid("processed-source resolution manifest key is inconsistent")
    if source.get("candidateReleaseId") != release_id:
        raise _invalid("processed-source resolution candidate release is inconsistent")

    return {
        "releaseDate": release_date,
        "sha256": digest,
        "archiveKey": archive_key,
        "manifestKey": manifest_key,
        "candidateReleaseId": release_id,
    }


def validate_processed_source_marker(
    marker: Mapping[str, Any],
    *,
    source: Mapping[str, Any],
) -> dict[str, Any]:
    if not isinstance(marker, Mapping):
        raise _invalid("processed-source marker is invalid")

    if (
        marker.get("schemaVersion") != SCHEMA_VERSION
        or marker.get("policyVersion") != POLICY_VERSION
    ):
        raise _invalid("processed-source marker schema or policy is unsupported")

    if (
        marker.get("kind") != MARKER_KIND
        or marker.get("disposition") != MARKER_DISPOSITION
    ):
        raise _invalid("processed-source marker disposition is invalid")

    # This historical marker deliberately does NOT pretend the old ephemeral
    # candidate was durably persisted.
    if marker.get("candidatePersisted") is not False:
        raise _invalid("processed-source marker candidate state is invalid")

    if (
        marker.get("activePointerWrites") != 0
        or marker.get("canaryPointerWrites") != 0
    ):
        raise _invalid("processed-source marker claims pointer writes")

    expected = _expected_source(source)

    marker_source = marker.get("source")
    if not isinstance(marker_source, Mapping):
        raise _invalid("processed-source marker source is invalid")

    for field, expected_value in expected.items():
        if marker_source.get(field) != expected_value:
            raise _invalid(
                f"processed-source marker {field} does not match verified source"
            )

    proof = marker.get("proof")
    if not isinstance(proof, Mapping):
        raise _invalid("processed-source marker proof is invalid")

    normalized_proof = {
        "cloudBuildId": _nonempty_string(
            proof.get("cloudBuildId"),
            field="cloudBuildId",
        ),
        "workflowExecution": _nonempty_string(
            proof.get("workflowExecution"),
            field="workflowExecution",
        ),
        "workerExecution": _nonempty_string(
            proof.get("workerExecution"),
            field="workerExecution",
        ),
        "workerStatus": proof.get("workerStatus"),
        "promotionStatus": proof.get("promotionStatus"),
        "activationStatus": proof.get("activationStatus"),
        "activePointerWrites": proof.get("activePointerWrites"),
        "canaryPointerWrites": proof.get("canaryPointerWrites"),
    }

    if normalized_proof["workerStatus"] != "CANDIDATE_VERIFIED":
        raise _invalid("processed-source marker worker status is invalid")

    if normalized_proof["promotionStatus"] != "SHADOW_ONLY":
        raise _invalid("processed-source marker promotion status is invalid")

    if normalized_proof["activationStatus"] != "NOT_EXECUTED":
        raise _invalid("processed-source marker activation status is invalid")

    if (
        normalized_proof["activePointerWrites"] != 0
        or normalized_proof["canaryPointerWrites"] != 0
    ):
        raise _invalid("processed-source marker proof claims pointer writes")

    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "kind": MARKER_KIND,
        "disposition": MARKER_DISPOSITION,
        "candidatePersisted": False,
        "source": expected,
        "proof": normalized_proof,
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
    }


def apply_processed_source_guard(
    resolution: Mapping[str, Any],
    *,
    store: ObjectStore,
) -> dict[str, Any]:
    value = dict(resolution)

    # Only an otherwise-buildable exact verified source can be suppressed.
    if value.get("status") != "SOURCE_READY":
        return value

    source = value.get("source")
    if not isinstance(source, Mapping):
        raise _invalid("processed-source resolution source is invalid")

    expected = _expected_source(source)
    key = processed_source_key(expected["sha256"])

    marker = _base._read_store_json(store, key)
    if marker is None:
        return value

    validated = validate_processed_source_marker(marker, source=source)

    return {
        **value,
        "status": "NO_BUILD_REQUIRED",
        "reason": SUPPRESSION_REASON,
        "processedSourceMarkerKey": key,
        "processedSource": validated,
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
    }


__all__ = [
    "MARKER_DISPOSITION",
    "MARKER_KIND",
    "POLICY_VERSION",
    "PROCESSED_SOURCE_PREFIX",
    "SCHEMA_VERSION",
    "SUPPRESSION_REASON",
    "apply_processed_source_guard",
    "processed_source_key",
    "validate_processed_source_marker",
]

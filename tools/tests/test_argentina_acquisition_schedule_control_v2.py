from __future__ import annotations

import hashlib
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

from backend.object_store import LocalFilesystemObjectStore, ObjectMetadata, ObjectStoreError, ObjectStoreLimitExceeded
from tools.argentina_acquisition_schedule_control import candidate_release_id, metadata_fingerprint
from tools import argentina_acquisition_schedule_control_v2 as schedule_control
from tools.argentina_acquisition_schedule_control_v2 import (
    CHECK_PHASE_LATE_RECHECK,
    CHECK_PHASE_METADATA_ONLY,
    CHECK_PHASE_PRIMARY,
    DAILY_STATE_EVENT_MAX,
    DAILY_STATE_EVENT_PREFIX,
    daily_state_key,
    LATE_RECHECK_PREFIX,
    AcquisitionScheduleError,
    finalize_download_observation,
    metadata_change_evidence,
    preflight_observation,
    reconcile_shadow_state,
    read_daily_state,
    read_pending_workflow,
    read_reconciliation_state,
    read_shadow_state,
    record_shadow_state,
    reserve_build_attempt,
    write_pending_workflow,
    upstream_outage_result,
    _write_daily_state,
)
from tools.argentina_release_acquisition import AcquisitionCandidate, AcquisitionError, DATASET_ID, DiscoveryResult


OBSERVED_AT = "2026-09-15T16:30:00Z"
PRIMARY_BYTES = b"primary-source"
PRIMARY_SHA = hashlib.sha256(PRIMARY_BYTES).hexdigest()
LATE_BYTES = b"late-new-source"
LATE_SHA = hashlib.sha256(LATE_BYTES).hexdigest()


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _candidate(*, observed_at: str = OBSERVED_AT, last_modified: str = "2026-06-30T16:19:00Z") -> AcquisitionCandidate:
    return AcquisitionCandidate(
        provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
        dataset_id=DATASET_ID,
        resource_id="resource-martes",
        resource_name="Martes",
        official_url="https://datos.produccion.gob.ar/download/sepa_martes.zip",
        observed_size=None,
        last_modified=last_modified,
        observed_at=observed_at,
        expected_source_host="datos.produccion.gob.ar",
        discovery_response_sha256="a" * 64,
        release_date_hint="2026-06-30",
        description="fixture",
        format="ZIP",
    )


def _discovery(candidate: AcquisitionCandidate) -> DiscoveryResult:
    return DiscoveryResult(
        endpoint="https://datos.gob.ar/api/3/action/package_show?id=precios-claros-base-sepa",
        response_sha256="a" * 64,
        observed_at=candidate.observed_at,
        dataset_id=DATASET_ID,
        dataset_name="precios-claros-base-sepa",
        dataset_title="Precios Claros - Base SEPA",
        license_title="Creative Commons Attribution 4.0",
        candidates=(candidate,),
    )


def _handoff(
    candidate: AcquisitionCandidate,
    *,
    status: str,
    source_sha: str,
    source_bytes: int,
    release_date: str,
) -> dict[str, object]:
    return {
        "status": status,
        "datasetId": DATASET_ID,
        "candidate": {
            "resourceId": candidate.resource_id,
            "resourceName": candidate.resource_name,
            "officialUrl": candidate.official_url,
        },
        "source": {
            "releaseDate": release_date,
            "sha256": source_sha,
            "bytes": source_bytes,
        },
    }


def _publish(source_sha: str, source_bytes: int) -> dict[str, object]:
    return {
        "status": "PUBLISHED",
        "sourceArchiveKey": f"source-archive/sha256/{source_sha}.zip",
        "sourceArchiveSha256": source_sha,
        "sourceBytes": source_bytes,
        "manifestKey": f"source-archive/manifests/{source_sha}.json",
    }


def _put_source_archive(store: LocalFilesystemObjectStore, data: bytes, release_date: str) -> str:
    digest = hashlib.sha256(data).hexdigest()
    key = f"source-archive/sha256/{digest}.zip"
    manifest_key = f"source-archive/manifests/{digest}.json"
    store.put_immutable(key, data, sha256=digest)
    manifest = {
        "sourceSha256": digest,
        "bytes": len(data),
        "releaseDate": release_date,
        "archiveKey": key,
        "rawProviderDataCommitted": False,
        "runtimePublicationAllowed": False,
    }
    raw = _canonical(manifest) + b"\n"
    store.put_immutable(manifest_key, raw, sha256=hashlib.sha256(raw).hexdigest())
    return digest


def _put_candidate(store: LocalFilesystemObjectStore, release_id: str) -> None:
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "objects": [],
    }
    raw = _canonical(manifest)
    checksum = f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii")
    store.put_immutable(f"releases/{release_id}/manifest.json", raw, sha256=hashlib.sha256(raw).hexdigest())
    store.put_immutable(f"releases/{release_id}/manifest.sha256", checksum, sha256=hashlib.sha256(checksum).hexdigest())


class ArgentinaAcquisitionScheduleControlV2Tests(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix="valuepilot-acquisition-v2-")
        self.addCleanup(self.directory.cleanup)
        self.store = LocalFilesystemObjectStore(Path(self.directory.name) / "archive")
        self.candidate = _candidate()
        self.discovery = _discovery(self.candidate)

    def _record_primary_no_change(self) -> dict[str, object]:
        primary = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_PRIMARY,
        )
        self.assertEqual(primary["status"], "DOWNLOAD_REQUIRED")
        return finalize_download_observation(
            preflight=primary,
            handoff=_handoff(
                self.candidate,
                status="NO_CHANGE",
                source_sha=PRIMARY_SHA,
                source_bytes=len(PRIMARY_BYTES),
                release_date="2026-09-08",
            ),
            store=self.store,
        )

    def test_primary_then_midday_reuses_but_late_rechecks_once(self) -> None:
        primary_result = self._record_primary_no_change()
        self.assertEqual(primary_result["status"], "NO_BUILD_REQUIRED")

        midday = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_METADATA_ONLY,
        )
        self.assertEqual(midday["status"], "NO_BUILD_REQUIRED")
        self.assertEqual(midday["observationKind"], "PRIMARY")

        late = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )
        self.assertEqual(late["status"], "DOWNLOAD_REQUIRED")
        self.assertEqual(late["reason"], "LATE_BYTE_RECHECK_AFTER_STALE_PRIMARY")
        self.assertEqual(late["observationKind"], "LATE_RECHECK")
        self.assertTrue(late["dailyObservationKey"].startswith(f"{LATE_RECHECK_PREFIX}/"))
        self.assertNotEqual(late["dailyObservationKey"], primary_result["dailyObservationKey"])

    def test_late_recheck_accepts_new_bytes_with_unchanged_metadata(self) -> None:
        self._record_primary_no_change()
        late = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )
        _put_source_archive(self.store, LATE_BYTES, "2026-09-15")
        result = finalize_download_observation(
            preflight=late,
            handoff=_handoff(
                self.candidate,
                status="HANDOFF_READY",
                source_sha=LATE_SHA,
                source_bytes=len(LATE_BYTES),
                release_date="2026-09-15",
            ),
            publish=_publish(LATE_SHA, len(LATE_BYTES)),
            store=self.store,
        )
        self.assertEqual(result["status"], "SOURCE_READY")
        self.assertEqual(result["source"]["sha256"], LATE_SHA)
        self.assertEqual(result["observationKind"], "LATE_RECHECK")

        reservation = reserve_build_attempt(resolution=result, store=self.store, reservation_id="github-300-1")
        self.assertEqual(reservation["status"], "RESERVED")
        duplicate = reserve_build_attempt(resolution=result, store=self.store, reservation_id="github-301-1")
        self.assertEqual(duplicate["status"], "ALREADY_RESERVED")

    def test_late_recheck_is_not_used_when_primary_source_is_ready_or_persisted(self) -> None:
        _put_source_archive(self.store, LATE_BYTES, "2026-09-15")
        primary = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_PRIMARY,
        )
        ready = finalize_download_observation(
            preflight=primary,
            handoff=_handoff(
                self.candidate,
                status="HANDOFF_READY",
                source_sha=LATE_SHA,
                source_bytes=len(LATE_BYTES),
                release_date="2026-09-15",
            ),
            publish=_publish(LATE_SHA, len(LATE_BYTES)),
            store=self.store,
        )
        late = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )
        self.assertEqual(late["status"], "SOURCE_READY")
        self.assertEqual(late["observationKind"], "PRIMARY")

        release_id = candidate_release_id("2026-09-15", LATE_SHA)
        _put_candidate(self.store, release_id)
        persisted = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )
        self.assertEqual(persisted["status"], "CANDIDATE_PERSISTED")

    def test_metadata_change_at_midday_gets_a_new_primary_byte_check(self) -> None:
        self._record_primary_no_change()
        changed_candidate = replace(
            self.candidate,
            last_modified="2026-09-15T15:00:00Z",
            observed_at="2026-09-15T16:31:00Z",
        )
        changed = preflight_observation(
            discovery=_discovery(changed_candidate),
            candidate=changed_candidate,
            store=self.store,
            check_phase=CHECK_PHASE_METADATA_ONLY,
        )
        self.assertEqual(changed["status"], "DOWNLOAD_REQUIRED")
        self.assertEqual(changed["reason"], "FIRST_BYTE_CHECK_FOR_METADATA_FINGERPRINT_TODAY")
        self.assertEqual(changed["observationKind"], "PRIMARY")
        self.assertFalse(changed["dailyObservationKey"].startswith(f"{LATE_RECHECK_PREFIX}/"))

    def test_second_late_check_reuses_late_evidence(self) -> None:
        self._record_primary_no_change()
        late = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )
        first_late = finalize_download_observation(
            preflight=late,
            handoff=_handoff(
                self.candidate,
                status="NO_CHANGE",
                source_sha=PRIMARY_SHA,
                source_bytes=len(PRIMARY_BYTES),
                release_date="2026-09-08",
            ),
            store=self.store,
        )
        self.assertEqual(first_late["status"], "NO_BUILD_REQUIRED")

        repeated = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )
        self.assertEqual(repeated["status"], "NO_BUILD_REQUIRED")
        self.assertEqual(repeated["observationKind"], "LATE_RECHECK")
        self.assertEqual(repeated["dailyObservationKey"], first_late["dailyObservationKey"])

    def test_metadata_change_evidence_requires_a_distinct_fingerprint(self) -> None:
        prior = {
            "metadataFingerprint": "a" * 64,
            "catalogResponseSha256": "c" * 64,
        }
        unchanged = metadata_change_evidence(
            metadata_fingerprint="a" * 64,
            catalog_response_sha256="c" * 64,
            resource_id="resource-martes",
            same_day_state=prior,
        )
        changed = metadata_change_evidence(
            metadata_fingerprint="b" * 64,
            catalog_response_sha256="d" * 64,
            resource_id="resource-miercoles",
            same_day_state=prior,
        )
        self.assertFalse(unchanged["upstreamChanged"])
        self.assertEqual(unchanged["metadataChangeEvidenceStatus"], "UNCHANGED")
        self.assertIsNone(unchanged["upstreamChangeEvidence"])
        self.assertTrue(changed["upstreamChanged"])
        self.assertEqual(changed["metadataChangeEvidenceStatus"], "CHANGE_PROVEN")
        self.assertTrue(changed["upstreamChangeEvidence"]["changed"])
        self.assertEqual(changed["upstreamChangeEvidence"]["resourceId"], "resource-miercoles")
        self.assertEqual(changed["upstreamChangeEvidence"]["previousDiscoveryResponseSha256"], "c" * 64)
        self.assertEqual(changed["upstreamChangeEvidence"]["currentDiscoveryResponseSha256"], "d" * 64)
        self.assertEqual(changed["upstreamChangeEvidence"]["previousMetadataFingerprint"], "a" * 64)
        self.assertEqual(changed["upstreamChangeEvidence"]["currentMetadataFingerprint"], "b" * 64)
        self.assertEqual(changed["upstreamChangeEvidence"]["catalogResponseSha256"], "d" * 64)

    def test_metadata_change_without_prior_response_digest_fails_closed(self) -> None:
        result = metadata_change_evidence(
            metadata_fingerprint="b" * 64,
            catalog_response_sha256="d" * 64,
            resource_id="resource-miercoles",
            same_day_state={"metadataFingerprint": "a" * 64},
        )
        self.assertFalse(result["upstreamChanged"])
        self.assertEqual(result["metadataChangeEvidenceStatus"], "CHANGE_UNPROVEN_PREVIOUS_RESPONSE_MISSING")
        self.assertIsNone(result["upstreamChangeEvidence"])

    def test_same_fingerprint_repeat_with_new_observed_at_is_not_a_conflict(self) -> None:
        self._record_primary_no_change()
        repeated_candidate = replace(self.candidate, observed_at="2026-09-15T16:31:00Z")

        repeated = preflight_observation(
            discovery=_discovery(repeated_candidate),
            candidate=repeated_candidate,
            store=self.store,
            check_phase=CHECK_PHASE_METADATA_ONLY,
        )

        self.assertEqual(repeated["status"], "NO_BUILD_REQUIRED")
        self.assertEqual(repeated["reason"], "NO_CHANGE")
        state = read_daily_state(store=self.store, argentina_day="2026-09-15")
        self.assertIsNotNone(state)
        self.assertEqual(state["metadataFingerprint"], repeated["metadataFingerprint"])
        self.assertEqual(state["observedAt"], "2026-09-15T16:31:00Z")
        events = self.store.list_immediate(
            f"{DAILY_STATE_EVENT_PREFIX}/2026-09-15/",
            max_results=DAILY_STATE_EVENT_MAX,
        )
        observed_times = {json.loads(self.store.get(item.key))["observedAt"] for item in events}
        self.assertIn(OBSERVED_AT, observed_times)
        self.assertIn("2026-09-15T16:31:00Z", observed_times)

    def test_metadata_change_with_same_discovery_digest_is_a_state_invariant_failure(self) -> None:
        with self.assertRaises(AcquisitionScheduleError) as caught:
            metadata_change_evidence(
                metadata_fingerprint="b" * 64,
                catalog_response_sha256="c" * 64,
                resource_id="resource-miercoles",
                same_day_state={"metadataFingerprint": "a" * 64, "catalogResponseSha256": "c" * 64},
            )
        self.assertEqual(caught.exception.code, "INVALID_CONTROL_STATE")

    def test_metadata_outage_reports_explicit_freshness_without_mutation(self) -> None:
        error = AcquisitionError(
            "official metadata unavailable",
            code="UPSTREAM_TEMPORARILY_UNAVAILABLE",
            http_status=502,
            attempts=3,
            cause_code="UPSTREAM_TRANSIENT_HTTP",
        )
        healthy = upstream_outage_result(
            observed_at="2026-09-22T16:30:00Z",
            current_release_date="2026-09-18",
            current_source_sha256="a" * 64,
            error=error,
            same_day_state={"status": "BUILD_RESERVED", "candidateReleaseId": "candidate"},
        )
        near = upstream_outage_result(
            observed_at="2026-09-22T16:30:00Z",
            current_release_date="2026-09-16",
            current_source_sha256="a" * 64,
            error=error,
            same_day_state=None,
        )
        stale = upstream_outage_result(
            observed_at="2026-09-22T16:30:00Z",
            current_release_date="2026-09-14",
            current_source_sha256="a" * 64,
            error=error,
            same_day_state=None,
        )
        unknown_active = upstream_outage_result(
            observed_at="2026-09-22T16:30:00Z",
            current_release_date=None,
            current_source_sha256=None,
            error=error,
            same_day_state=None,
        )
        self.assertEqual(healthy["status"], "UPSTREAM_TEMPORARILY_UNAVAILABLE")
        self.assertEqual(healthy["freshness"]["state"], "FRESH")
        self.assertEqual(healthy["sameDayState"]["status"], "BUILD_RESERVED")
        self.assertEqual(near["freshness"]["state"], "NEARING_LIMIT")
        self.assertTrue(near["operatorAttentionRequired"])
        self.assertEqual(stale["status"], "FRESHNESS_LIMIT_EXCEEDED")
        self.assertEqual(unknown_active["status"], "ACTIVE_RELEASE_UNVERIFIED")
        self.assertEqual(unknown_active["freshness"]["state"], "UNVERIFIED")
        for result in (healthy, near, stale, unknown_active):
            self.assertTrue(result["activeReleaseUnchanged"])
            self.assertEqual(result["activePointerWrites"], 0)
            self.assertEqual(result["canaryPointerWrites"], 0)
            self.assertEqual(result["sourceSubstitution"], 0)
            self.assertFalse(result["candidatePromotion"])

    def test_preflight_cli_records_exhausted_metadata_502_as_handled_state(self) -> None:
        error = AcquisitionError(
            "official metadata unavailable",
            code="UPSTREAM_TEMPORARILY_UNAVAILABLE",
            http_status=502,
            attempts=3,
            cause_code="UPSTREAM_TRANSIENT_HTTP",
        )
        same_day_state = {
            "status": "BUILD_RESERVED",
            "candidateReleaseId": "argentina-sepa-2026-09-22-candidate",
        }

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "preflight.json"
            with (
                patch.object(schedule_control, "_gcs_store", return_value=object()),
                patch.object(schedule_control, "read_daily_state", return_value=same_day_state),
                patch.object(schedule_control, "discover_metadata", side_effect=error),
                redirect_stdout(io.StringIO()),
            ):
                exit_code = schedule_control._main(
                    [
                        "preflight",
                        "--observed-at",
                        "2026-09-23T16:30:00Z",
                        "--current-release-id",
                        "argentina-sepa-2026-09-18-active",
                        "--current-release-date",
                        "2026-09-18",
                        "--current-source-sha256",
                        "a" * 64,
                        "--archive-bucket",
                        "fixture-archive",
                        "--output",
                        str(output),
                    ]
                )

            report = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(0, exit_code)
        self.assertEqual("UPSTREAM_TEMPORARILY_UNAVAILABLE", report["status"])
        self.assertEqual("UPSTREAM_TRANSIENT_HTTP", report["upstreamFailure"]["code"])
        self.assertEqual(502, report["upstreamFailure"]["httpStatus"])
        self.assertEqual(3, report["upstreamFailure"]["attempts"])
        self.assertEqual("BUILD_RESERVED", report["sameDayState"]["status"])
        self.assertTrue(report["activeReleaseUnchanged"])
        self.assertEqual(0, report["activePointerWrites"])
        self.assertEqual(0, report["canaryPointerWrites"])
        self.assertEqual(0, report["sourceSubstitution"])
        self.assertFalse(report["candidatePromotion"])

    def test_shadow_state_is_idempotent_and_success_cannot_be_downgraded(self) -> None:
        resolution = self._record_primary_no_change()
        started = record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-1",
            reservation_id="reservation-1",
            recorded_at="2026-09-15T17:00:00Z",
        )
        self.assertEqual(started["status"], "SHADOW_STARTED")
        failed = record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_FAILED",
            workflow_execution="execution-1",
            reservation_id="reservation-1",
            shadow_result={"status": "FAILED", "activePointerWrites": 0},
            recorded_at="2026-09-15T17:01:00Z",
        )
        self.assertEqual(failed["status"], "SHADOW_FAILED")
        succeeded = record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_SUCCEEDED",
            workflow_execution="execution-1",
            reservation_id="reservation-1",
            shadow_result={"status": "SUCCEEDED", "activePointerWrites": 0},
            recorded_at="2026-09-15T17:02:00Z",
        )
        self.assertEqual(succeeded["status"], "SHADOW_SUCCEEDED")
        rerun_failure = record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_FAILED",
            workflow_execution="execution-1",
            reservation_id="reservation-1",
            shadow_result={"status": "FAILED", "activePointerWrites": 0},
            recorded_at="2026-09-15T17:03:00Z",
        )
        self.assertEqual(rerun_failure["status"], "SHADOW_SUCCEEDED")
        self.assertEqual(rerun_failure["shadowResult"]["status"], "SUCCEEDED")
        self.assertEqual(read_daily_state(store=self.store, argentina_day="2026-09-15")["status"], "SHADOW_SUCCEEDED")

        rerun = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_METADATA_ONLY,
        )
        self.assertEqual(rerun["priorSameDayState"]["status"], "SHADOW_SUCCEEDED")
        self.assertEqual(rerun["activePointerWrites"], 0)

    def test_shadow_reconciliation_leaves_active_execution_pending(self) -> None:
        resolution = self._record_primary_no_change()
        record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-pending",
            reservation_id="reservation-pending",
            recorded_at="2026-09-15T17:00:00Z",
        )

        pending = reconcile_shadow_state(
            store=self.store,
            argentina_day="2026-09-15",
            workflow_execution="execution-pending",
            execution_state="ACTIVE",
            recorded_at="2026-09-15T17:05:00Z",
        )

        self.assertEqual(pending["status"], "SHADOW_PENDING")
        self.assertEqual(pending["reconciliationStatus"], "PENDING")
        self.assertEqual(read_daily_state(store=self.store, argentina_day="2026-09-15")["status"], "SHADOW_STARTED")

    def test_pending_workflow_is_recoverable_after_midnight_without_duplicate_identity(self) -> None:
        resolution = self._record_primary_no_change()
        resolution["candidateReleaseId"] = "argentina-sepa-2026-09-15-1234567890abcdef-routing-v1-search-v2"
        started = record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-cross-midnight",
            reservation_id="reservation-cross-midnight",
            recorded_at="2026-09-15T23:59:00Z",
        )
        self.assertEqual(started["status"], "SHADOW_STARTED")
        recovered = read_shadow_state(store=self.store, argentina_day="2026-09-16")
        self.assertEqual(recovered["status"], "SHADOW_STARTED")
        self.assertTrue(recovered["crossDayPending"])
        self.assertEqual(recovered["argentinaDate"], "2026-09-15")
        self.assertEqual(recovered["workflowExecution"], "execution-cross-midnight")
        pending = read_pending_workflow(store=self.store)
        self.assertEqual(pending["candidateReleaseId"], resolution["candidateReleaseId"])
        with self.assertRaises(AcquisitionScheduleError) as conflict:
            write_pending_workflow(
                store=self.store,
                argentina_day="2026-09-15",
                candidate_release_id="different-candidate",
                source_release_date="2026-09-15",
                source_sha256="a" * 64,
                reservation_id="reservation-cross-midnight",
                workflow_execution="execution-cross-midnight",
                state="SHADOW_STARTED",
                created_at="2026-09-15T23:59:00Z",
                updated_at="2026-09-16T00:01:00Z",
            )
        self.assertEqual(conflict.exception.code, "CONTROL_CONFLICT")

    def test_yesterdays_pending_build_survives_todays_metadata_only_state(self) -> None:
        resolution = self._record_primary_no_change()
        resolution['candidateReleaseId'] = candidate_release_id('2026-09-08', PRIMARY_SHA)
        record_shadow_state(resolution=resolution, store=self.store, status="SHADOW_STARTED",
                            workflow_execution="execution-overnight", reservation_id="overnight",
                            recorded_at="2026-09-15T23:59:00Z")
        tomorrow = _candidate(observed_at="2026-09-16T16:30:00Z")
        preflight_observation(discovery=_discovery(tomorrow), candidate=tomorrow, store=self.store)
        self.assertEqual(read_daily_state(store=self.store, argentina_day="2026-09-16")["status"], "METADATA_DISCOVERED")
        recovered = read_reconciliation_state(store=self.store, argentina_day="2026-09-16", current_release_date="2026-09-01")
        self.assertEqual(recovered["workflowExecution"], "execution-overnight")
        self.assertEqual(recovered["argentinaDate"], "2026-09-15")
        self.assertTrue(recovered["crossDayPending"])

    def test_terminal_global_reservation_can_rotate_but_late_callbacks_cannot_overwrite_next_build(self) -> None:
        resolution = self._record_primary_no_change()
        resolution['candidateReleaseId'] = candidate_release_id('2026-09-08', PRIMARY_SHA)
        record_shadow_state(resolution=resolution, store=self.store, status="SHADOW_STARTED",
                            workflow_execution="execution-first", reservation_id="first",
                            recorded_at="2026-09-15T17:00:00Z")
        original = read_pending_workflow(store=self.store)
        def write(value, **overrides):
            fields = dict(store=self.store, argentina_day=value['argentinaDate'],
                          candidate_release_id=value['candidateReleaseId'], source_release_date=value['sourceReleaseDate'],
                          source_sha256=value['sourceSha256'], reservation_id=value['reservationId'],
                          workflow_execution=value['workflowExecution'], state=value['state'],
                          created_at=value['createdAt'], updated_at=value['updatedAt'])
            return write_pending_workflow(**{**fields, **overrides})
        write(original, state="SHADOW_SUCCEEDED", updated_at="2026-09-16T00:10:00Z")
        second = write(original, argentina_day="2026-09-16", candidate_release_id="new-candidate",
                       source_release_date="2026-09-16", source_sha256="b"*64, reservation_id="second",
                       workflow_execution="execution-second", state="SHADOW_STARTED",
                       created_at="2026-09-16T16:30:00Z", updated_at="2026-09-16T16:30:00Z")
        self.assertEqual(second['workflowExecution'], 'execution-second')
        with self.assertRaises(AcquisitionScheduleError):
            write(original, state="SHADOW_SUCCEEDED", updated_at="2026-09-17T00:00:00Z")
        self.assertEqual(read_pending_workflow(store=self.store), second)

    def test_successful_unpromoted_legacy_build_remains_retryable_after_midnight(self) -> None:
        resolution = self._record_primary_no_change()
        resolution['candidateReleaseId'] = candidate_release_id('2026-09-08', PRIMARY_SHA)
        record_shadow_state(resolution=resolution, store=self.store, status="SHADOW_STARTED",
                            workflow_execution="execution-completed", reservation_id="completed",
                            recorded_at="2026-09-15T17:00:00Z")
        record_shadow_state(resolution=resolution, store=self.store, status="SHADOW_SUCCEEDED",
                            workflow_execution="execution-completed", reservation_id="completed",
                            recorded_at="2026-09-15T18:00:00Z")
        recovered = read_reconciliation_state(store=self.store, argentina_day="2026-09-16", current_release_date="2026-09-01")
        self.assertEqual(recovered['status'], 'SHADOW_STARTED')
        self.assertEqual(recovered['durableStatus'], 'SHADOW_SUCCEEDED')
        self.assertEqual(recovered['workflowExecution'], 'execution-completed')
        already_newer = read_reconciliation_state(store=self.store, argentina_day="2026-09-16", current_release_date="2026-09-10")
        self.assertEqual(already_newer['status'], 'NO_STATE')

    def test_nullable_workflow_payload_is_accepted_while_execution_is_active(self) -> None:
        resolution = self._record_primary_no_change()
        resolution["candidateReleaseId"] = "argentina-sepa-2026-09-15-1234567890abcdef-routing-v1-search-v2"
        record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-null-result",
            reservation_id="reservation-null-result",
            recorded_at="2026-09-15T17:00:00Z",
        )
        with tempfile.TemporaryDirectory() as directory:
            result_path = Path(directory) / "result.json"
            error_path = Path(directory) / "error.json"
            result_path.write_text("null\n", encoding="utf-8")
            error_path.write_text("null\n", encoding="utf-8")
            with patch.object(schedule_control, "_gcs_store", return_value=self.store):
                exit_code = schedule_control._main(
                    [
                        "reconcile-shadow-state",
                        "--argentina-day", "2026-09-15",
                        "--workflow-execution", "execution-null-result",
                        "--execution-state", "RUNNING",
                        "--workflow-result", str(result_path),
                        "--workflow-error", str(error_path),
                        "--recorded-at", "2026-09-15T17:05:00Z",
                        "--archive-bucket", "fixture",
                        "--output", str(Path(directory) / "output.json"),
                    ]
                )
                output = json.loads((Path(directory) / "output.json").read_text(encoding="utf-8"))
        self.assertEqual(exit_code, 0)
        self.assertEqual(output["status"], "SHADOW_PENDING")

    def test_shadow_reconciliation_records_only_verified_write_free_success(self) -> None:
        resolution = self._record_primary_no_change()
        record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-success",
            reservation_id="reservation-success",
            recorded_at="2026-09-15T17:00:00Z",
        )

        reconciled = reconcile_shadow_state(
            store=self.store,
            argentina_day="2026-09-15",
            workflow_execution="execution-success",
            execution_state="SUCCEEDED",
            workflow_result={
                "status": "CANDIDATE_VERIFIED",
                "activePointerWrites": 0,
                "canaryPointerWrites": 0,
                "promotionMode": "SHADOW",
                "activation": "NOT_EXECUTED",
                "promotion": {"status": "SHADOW_ONLY", "activePointerWrites": 0},
            },
            recorded_at="2026-09-15T17:05:00Z",
        )

        self.assertEqual(reconciled["status"], "SHADOW_SUCCEEDED")
        self.assertEqual(reconciled["reconciliationStatus"], "RECORDED")
        state = read_daily_state(store=self.store, argentina_day="2026-09-15")
        self.assertEqual(state["status"], "SHADOW_SUCCEEDED")
        self.assertEqual(state["shadowResult"]["workflowResult"]["status"], "CANDIDATE_VERIFIED")
        self.assertEqual(state["shadowResult"]["workflowResult"]["activePointerWrites"], 0)

    def test_shadow_reconciliation_records_terminal_failure_and_alertable_reason(self) -> None:
        resolution = self._record_primary_no_change()
        record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-failed",
            reservation_id="reservation-failed",
            recorded_at="2026-09-15T17:00:00Z",
        )

        reconciled = reconcile_shadow_state(
            store=self.store,
            argentina_day="2026-09-15",
            workflow_execution="execution-failed",
            execution_state="FAILED",
            workflow_error={"code": "BUILD_FAILED", "message": "fixture failure"},
            recorded_at="2026-09-15T17:05:00Z",
        )

        self.assertEqual(reconciled["status"], "SHADOW_FAILED")
        self.assertEqual(reconciled["reason"], "WORKFLOW_EXECUTION_FAILED")
        state = read_daily_state(store=self.store, argentina_day="2026-09-15")
        self.assertEqual(state["status"], "SHADOW_FAILED")
        self.assertEqual(state["shadowResult"]["workflowError"]["code"], "BUILD_FAILED")

    def test_shadow_reconciliation_rejects_wrong_execution_and_unsafe_success(self) -> None:
        resolution = self._record_primary_no_change()
        record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-identity",
            reservation_id="reservation-identity",
            recorded_at="2026-09-15T17:00:00Z",
        )

        with self.assertRaises(AcquisitionScheduleError) as conflict:
            reconcile_shadow_state(
                store=self.store,
                argentina_day="2026-09-15",
                workflow_execution="different-execution",
                execution_state="SUCCEEDED",
                workflow_result={"status": "CANDIDATE_VERIFIED", "activePointerWrites": 0},
                recorded_at="2026-09-15T17:05:00Z",
            )
        self.assertEqual(conflict.exception.code, "CONTROL_CONFLICT")

        unsafe = reconcile_shadow_state(
            store=self.store,
            argentina_day="2026-09-15",
            workflow_execution="execution-identity",
            execution_state="SUCCEEDED",
            workflow_result={"status": "CANDIDATE_VERIFIED", "activePointerWrites": 1},
            recorded_at="2026-09-15T17:06:00Z",
        )
        self.assertEqual(unsafe["status"], "SHADOW_FAILED")
        self.assertEqual(unsafe["reason"], "ACTIVE_POINTER_WRITE_CLAIMED")

    def test_shadow_reconciliation_is_idempotent_after_terminal_record(self) -> None:
        resolution = self._record_primary_no_change()
        record_shadow_state(
            resolution=resolution,
            store=self.store,
            status="SHADOW_STARTED",
            workflow_execution="execution-terminal",
            reservation_id="reservation-terminal",
            recorded_at="2026-09-15T17:00:00Z",
        )
        first = reconcile_shadow_state(
            store=self.store,
            argentina_day="2026-09-15",
            workflow_execution="execution-terminal",
            execution_state="SUCCEEDED",
            workflow_result={"status": "CANDIDATE_VERIFIED", "activePointerWrites": 0},
            recorded_at="2026-09-15T17:05:00Z",
        )
        second = reconcile_shadow_state(
            store=self.store,
            argentina_day="2026-09-15",
            workflow_execution="execution-terminal",
            execution_state="SUCCEEDED",
            workflow_result={"status": "CANDIDATE_VERIFIED", "activePointerWrites": 0},
            recorded_at="2026-09-15T17:10:00Z",
        )
        self.assertEqual(first["status"], "SHADOW_SUCCEEDED")
        self.assertEqual(second["status"], "SHADOW_SUCCEEDED")
        self.assertEqual(second["reconciliationStatus"], "ALREADY_RECORDED")

    def test_read_shadow_state_is_bounded_and_reports_missing_day(self) -> None:
        missing = read_shadow_state(store=self.store, argentina_day="2026-09-16")
        self.assertEqual(missing["status"], "NO_STATE")
        self.assertIsNone(missing["workflowExecution"])

    def test_daily_state_journal_survives_a_create_only_store(self) -> None:
        class CreateOnlyStore(LocalFilesystemObjectStore):
            cas_calls = 0

            def compare_and_swap(self, key, data, *, expected_etag):
                self.cas_calls += 1
                raise ObjectStoreError("conditional replacement is forbidden")

        store = CreateOnlyStore(Path(self.directory.name) / "create-only-archive")
        metadata_identity = {"resource": "fixture"}
        base = {
            "argentinaDate": "2026-09-15",
            "observedAt": OBSERVED_AT,
            "updatedAt": OBSERVED_AT,
            "metadataFingerprint": metadata_fingerprint(metadata_identity),
            "metadataIdentity": metadata_identity,
            "catalogResponseSha256": "b" * 64,
        }
        self.assertEqual(
            _write_daily_state(store=store, value={**base, "status": "METADATA_DISCOVERED"})["status"],
            "METADATA_DISCOVERED",
        )
        advanced = _write_daily_state(
            store=store,
            value={
                **base,
                "status": "BUILD_RESERVED",
                "candidateReleaseId": "candidate-1",
                "buildReservationId": "reservation-1",
                "updatedAt": "2026-09-15T16:31:00Z",
            },
        )
        self.assertEqual(advanced["status"], "BUILD_RESERVED")
        self.assertEqual(advanced["candidateReleaseId"], "candidate-1")
        self.assertEqual(read_daily_state(store=store, argentina_day="2026-09-15")["status"], "BUILD_RESERVED")
        self.assertEqual(store.cas_calls, 0)
        events = store.list_immediate(
            f"{DAILY_STATE_EVENT_PREFIX}/2026-09-15/",
            max_results=DAILY_STATE_EVENT_MAX,
        )
        self.assertEqual(len(events), 2)

    def test_full_source_ready_reservation_retry_works_without_cas_replacement(self) -> None:
        class CreateOnlyStore(LocalFilesystemObjectStore):
            cas_calls = 0

            def compare_and_swap(self, key, data, *, expected_etag):
                self.cas_calls += 1
                raise ObjectStoreError("conditional replacement is forbidden")

        self.store = CreateOnlyStore(Path(self.directory.name) / "full-create-only-archive")
        self._record_primary_no_change()
        late = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )
        _put_source_archive(self.store, LATE_BYTES, "2026-09-15")
        result = finalize_download_observation(
            preflight=late,
            handoff=_handoff(
                self.candidate,
                status="HANDOFF_READY",
                source_sha=LATE_SHA,
                source_bytes=len(LATE_BYTES),
                release_date="2026-09-15",
            ),
            publish=_publish(LATE_SHA, len(LATE_BYTES)),
            store=self.store,
        )
        self.assertEqual(result["status"], "SOURCE_READY")
        self.assertEqual(reserve_build_attempt(resolution=result, store=self.store, reservation_id="reservation-1")["status"], "RESERVED")
        self.assertEqual(reserve_build_attempt(resolution=result, store=self.store, reservation_id="reservation-2")["status"], "ALREADY_RESERVED")
        self.assertEqual(self.store.cas_calls, 0)
        self.assertEqual(read_daily_state(store=self.store, argentina_day="2026-09-15")["status"], "BUILD_RESERVED")

    def test_legacy_v1_daily_state_is_read_and_migrated_by_next_write(self) -> None:
        store = LocalFilesystemObjectStore(Path(self.directory.name) / "legacy-archive")
        metadata_identity = {"resource": "legacy-fixture"}
        legacy = {
            "schemaVersion": "valuepilot-argentina-daily-state-v1",
            "policyVersion": "valuepilot-argentina-daily-state-policy-v1",
            "argentinaDate": "2026-09-15",
            "observedAt": OBSERVED_AT,
            "updatedAt": OBSERVED_AT,
            "metadataFingerprint": metadata_fingerprint(metadata_identity),
            "metadataIdentity": metadata_identity,
            "catalogResponseSha256": "c" * 64,
            "status": "METADATA_DISCOVERED",
        }
        store.put_immutable(daily_state_key("2026-09-15"), _canonical(legacy) + b"\n")
        self.assertEqual(read_daily_state(store=store, argentina_day="2026-09-15")["status"], "METADATA_DISCOVERED")
        advanced = _write_daily_state(
            store=store,
            value={**legacy, "status": "BUILD_RESERVED", "updatedAt": "2026-09-15T16:31:00Z"},
        )
        self.assertEqual(advanced["status"], "BUILD_RESERVED")
        events = store.list_immediate(f"{DAILY_STATE_EVENT_PREFIX}/2026-09-15/", max_results=DAILY_STATE_EVENT_MAX)
        self.assertEqual(len(events), 1)

    def test_daily_state_journal_rejects_same_slot_same_rank_source_conflict(self) -> None:
        store = LocalFilesystemObjectStore(Path(self.directory.name) / "conflict-archive")
        metadata_identity = {"resource": "fixture"}
        common = {
            "argentinaDate": "2026-09-15",
            "observedAt": OBSERVED_AT,
            "updatedAt": OBSERVED_AT,
            "metadataFingerprint": metadata_fingerprint(metadata_identity),
            "metadataIdentity": metadata_identity,
            "catalogResponseSha256": "b" * 64,
            "dailyObservationKey": "acquisition/daily-observations/2026-09-15/slot.json",
            "status": "SOURCE_READY",
        }
        first = {
            **common,
            "source": {"releaseDate": "2026-09-15", "sha256": "a" * 64, "bytes": 10},
        }
        second = {
            **common,
            "source": {"releaseDate": "2026-09-15", "sha256": "b" * 64, "bytes": 11},
        }
        _write_daily_state(store=store, value=first)
        with self.assertRaisesRegex(AcquisitionScheduleError, "source identities conflict"):
            _write_daily_state(store=store, value=second)

    def test_daily_state_event_listing_is_bounded(self) -> None:
        store = LocalFilesystemObjectStore(Path(self.directory.name) / "bounded-archive")
        prefix = f"{DAILY_STATE_EVENT_PREFIX}/2026-09-15/"
        for index in range(DAILY_STATE_EVENT_MAX + 1):
            store.put_immutable(f"{prefix}{index:064x}.json", str(index).encode("ascii"))
        with self.assertRaises(ObjectStoreLimitExceeded):
            store.list_immediate(prefix, max_results=DAILY_STATE_EVENT_MAX)

    def test_event_reader_rechecks_size_when_listing_metadata_is_partial(self) -> None:
        class PartialListingStore(LocalFilesystemObjectStore):
            def list_immediate(self, prefix, *, max_results):
                entries = super().list_immediate(prefix, max_results=max_results)
                return tuple(ObjectMetadata(item.key, -1, item.etag, None, ()) for item in entries)

        store = PartialListingStore(Path(self.directory.name) / "partial-listing-archive")
        metadata_identity = {"resource": "partial-listing-fixture"}
        _write_daily_state(
            store=store,
            value={
                "argentinaDate": "2026-09-15",
                "observedAt": OBSERVED_AT,
                "updatedAt": OBSERVED_AT,
                "metadataFingerprint": metadata_fingerprint(metadata_identity),
                "metadataIdentity": metadata_identity,
                "catalogResponseSha256": "d" * 64,
                "status": "METADATA_DISCOVERED",
            },
        )
        self.assertEqual(read_daily_state(store=store, argentina_day="2026-09-15")["status"], "METADATA_DISCOVERED")


if __name__ == "__main__":
    unittest.main()

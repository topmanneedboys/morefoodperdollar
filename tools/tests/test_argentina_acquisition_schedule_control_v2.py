from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_acquisition_schedule_control import candidate_release_id
from tools.argentina_acquisition_schedule_control_v2 import (
    CHECK_PHASE_LATE_RECHECK,
    CHECK_PHASE_METADATA_ONLY,
    CHECK_PHASE_PRIMARY,
    LATE_RECHECK_PREFIX,
    finalize_download_observation,
    preflight_observation,
    reserve_build_attempt,
)
from tools.argentina_release_acquisition import AcquisitionCandidate, DATASET_ID, DiscoveryResult


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
        changed_candidate = replace(self.candidate, last_modified="2026-09-15T15:00:00Z")
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


if __name__ == "__main__":
    unittest.main()

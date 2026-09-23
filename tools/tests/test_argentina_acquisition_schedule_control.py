from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_acquisition_schedule_control import (
    AcquisitionScheduleError,
    argentina_date,
    candidate_release_id,
    finalize_download_observation,
    metadata_fingerprint,
    metadata_identity,
    preflight_observation,
    reserve_build_attempt,
)
from tools.argentina_release_acquisition import AcquisitionCandidate, DATASET_ID, DiscoveryResult


OBSERVED_AT = "2026-09-14T16:30:00Z"
SOURCE_BYTES = b"verified-source-fixture"
SOURCE_SHA = hashlib.sha256(SOURCE_BYTES).hexdigest()


def _canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _candidate(*, observed_at: str = OBSERVED_AT, last_modified: str = "2026-09-14T12:00:00") -> AcquisitionCandidate:
    return AcquisitionCandidate(
        provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
        dataset_id=DATASET_ID,
        resource_id="resource-lunes",
        resource_name="Lunes",
        official_url="https://datos.produccion.gob.ar/download/sepa_lunes.zip",
        observed_size=len(SOURCE_BYTES),
        last_modified=last_modified,
        observed_at=observed_at,
        expected_source_host="datos.produccion.gob.ar",
        discovery_response_sha256="a" * 64,
        release_date_hint="2026-09-14",
        description="fixture",
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


def _handoff(candidate: AcquisitionCandidate, *, status: str = "HANDOFF_READY", source_sha: str = SOURCE_SHA, release_date: str = "2026-09-14", source_bytes: int = len(SOURCE_BYTES)) -> dict[str, object]:
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


def _publish(*, source_sha: str = SOURCE_SHA, source_bytes: int = len(SOURCE_BYTES)) -> dict[str, object]:
    return {
        "status": "PUBLISHED",
        "sourceArchiveKey": f"source-archive/sha256/{source_sha}.zip",
        "sourceArchiveSha256": source_sha,
        "sourceBytes": source_bytes,
        "manifestKey": f"source-archive/manifests/{source_sha}.json",
    }


def _put_source_archive(store: LocalFilesystemObjectStore, *, source_bytes: bytes = SOURCE_BYTES, release_date: str = "2026-09-14") -> str:
    digest = hashlib.sha256(source_bytes).hexdigest()
    key = f"source-archive/sha256/{digest}.zip"
    manifest_key = f"source-archive/manifests/{digest}.json"
    store.put_immutable(key, source_bytes, sha256=digest)
    manifest = {
        "sourceSha256": digest,
        "bytes": len(source_bytes),
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


class ArgentinaAcquisitionScheduleControlTests(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix="valuepilot-acquisition-schedule-")
        self.addCleanup(self.directory.cleanup)
        self.store = LocalFilesystemObjectStore(Path(self.directory.name) / "archive")
        self.candidate = _candidate()
        self.discovery = _discovery(self.candidate)

    def test_argentina_day_and_metadata_fingerprint_exclude_observation_time(self) -> None:
        self.assertEqual(argentina_date("2026-09-15T01:00:00Z"), "2026-09-14")
        first = metadata_fingerprint(metadata_identity(self.discovery, self.candidate))
        later_candidate = replace(self.candidate, observed_at="2026-09-14T20:30:00Z")
        later = metadata_fingerprint(metadata_identity(_discovery(later_candidate), later_candidate))
        self.assertEqual(first, later)

    def test_first_check_downloads_once_then_reuses_verified_source_same_day(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        self.assertEqual(first["status"], "DOWNLOAD_REQUIRED")
        self.assertEqual(first["reason"], "FIRST_BYTE_CHECK_FOR_METADATA_FINGERPRINT_TODAY")
        _put_source_archive(self.store)
        resolved = finalize_download_observation(
            preflight=first,
            handoff=_handoff(self.candidate),
            publish=_publish(),
            store=self.store,
        )
        self.assertEqual(resolved["status"], "SOURCE_READY")
        self.assertEqual(resolved["source"]["sha256"], SOURCE_SHA)
        second = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        self.assertEqual(second["status"], "SOURCE_READY")
        self.assertEqual(second["source"]["candidateReleaseId"], candidate_release_id("2026-09-14", SOURCE_SHA))

    def test_persisted_candidate_turns_later_checks_into_no_build(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        _put_source_archive(self.store)
        resolved = finalize_download_observation(preflight=first, handoff=_handoff(self.candidate), publish=_publish(), store=self.store)
        release_id = resolved["source"]["candidateReleaseId"]
        _put_candidate(self.store, release_id)
        later = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        self.assertEqual(later["status"], "CANDIDATE_PERSISTED")
        self.assertEqual(later["candidate"]["verification"]["status"], "PASS")
        self.assertEqual(later["activePointerWrites"], 0)

    def test_new_argentina_day_requires_one_fresh_byte_check_even_with_same_metadata(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        _put_source_archive(self.store)
        finalize_download_observation(preflight=first, handoff=_handoff(self.candidate), publish=_publish(), store=self.store)
        next_candidate = replace(self.candidate, observed_at="2026-09-15T16:30:00Z")
        next_day = preflight_observation(discovery=_discovery(next_candidate), candidate=next_candidate, store=self.store)
        self.assertEqual(next_day["argentinaDate"], "2026-09-15")
        self.assertEqual(next_day["status"], "DOWNLOAD_REQUIRED")

    def test_same_day_selected_metadata_change_requires_another_byte_check(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        _put_source_archive(self.store)
        finalize_download_observation(preflight=first, handoff=_handoff(self.candidate), publish=_publish(), store=self.store)
        changed_candidate = replace(self.candidate, last_modified="2026-09-14T14:00:00")
        changed = preflight_observation(discovery=_discovery(changed_candidate), candidate=changed_candidate, store=self.store)
        self.assertNotEqual(changed["metadataFingerprint"], first["metadataFingerprint"])
        self.assertEqual(changed["status"], "DOWNLOAD_REQUIRED")

    def test_force_download_rechecks_same_observation_but_silent_source_change_fails_closed(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        no_change = finalize_download_observation(
            preflight=first,
            handoff=_handoff(self.candidate, status="NO_CHANGE"),
            publish=None,
            store=self.store,
        )
        self.assertEqual(no_change["status"], "NO_BUILD_REQUIRED")
        forced = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store, force_download=True)
        self.assertEqual(forced["status"], "DOWNLOAD_REQUIRED")
        self.assertTrue(forced["existingObservation"])
        different_bytes = b"silently-changed-source"
        different_sha = hashlib.sha256(different_bytes).hexdigest()
        with self.assertRaises(AcquisitionScheduleError) as caught:
            finalize_download_observation(
                preflight=forced,
                handoff=_handoff(self.candidate, status="HANDOFF_READY", source_sha=different_sha, source_bytes=len(different_bytes)),
                publish={
                    "status": "PUBLISHED",
                    "sourceArchiveKey": f"source-archive/sha256/{different_sha}.zip",
                    "sourceArchiveSha256": different_sha,
                    "sourceBytes": len(different_bytes),
                    "manifestKey": f"source-archive/manifests/{different_sha}.json",
                },
                store=self.store,
            )
        self.assertEqual(caught.exception.code, "SILENT_SOURCE_CHANGE_REVIEW_REQUIRED")

    def test_no_change_and_older_dispositions_stop_later_same_day_downloads(self) -> None:
        for status in ("NO_CHANGE", "OLDER_REJECTED"):
            with self.subTest(status=status), tempfile.TemporaryDirectory(prefix="valuepilot-safe-stop-") as directory:
                store = LocalFilesystemObjectStore(directory)
                first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=store)
                result = finalize_download_observation(preflight=first, handoff=_handoff(self.candidate, status=status), store=store)
                self.assertEqual(result["status"], "NO_BUILD_REQUIRED")
                self.assertEqual(result["reason"], status)
                later = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=store)
                self.assertEqual(later["status"], "NO_BUILD_REQUIRED")

    def test_one_build_attempt_per_candidate_per_argentina_day(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        _put_source_archive(self.store)
        resolution = finalize_download_observation(preflight=first, handoff=_handoff(self.candidate), publish=_publish(), store=self.store)
        reservation = reserve_build_attempt(resolution=resolution, store=self.store, reservation_id="github-100-1")
        self.assertEqual(reservation["status"], "RESERVED")
        again = reserve_build_attempt(resolution=resolution, store=self.store, reservation_id="github-101-1")
        self.assertEqual(again["status"], "ALREADY_RESERVED")
        self.assertEqual(again["existingReservationId"], "github-100-1")
        self.assertEqual(again["activePointerWrites"], 0)

    def test_candidate_persisting_between_resolution_and_reservation_prevents_build(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        _put_source_archive(self.store)
        resolution = finalize_download_observation(preflight=first, handoff=_handoff(self.candidate), publish=_publish(), store=self.store)
        _put_candidate(self.store, resolution["source"]["candidateReleaseId"])
        result = reserve_build_attempt(resolution=resolution, store=self.store, reservation_id="github-200-1")
        self.assertEqual(result["status"], "CANDIDATE_PERSISTED")

    def test_handoff_candidate_change_after_preflight_fails_closed(self) -> None:
        first = preflight_observation(discovery=self.discovery, candidate=self.candidate, store=self.store)
        bad = _handoff(self.candidate)
        bad["candidate"]["resourceId"] = "different-resource"
        with self.assertRaises(AcquisitionScheduleError) as caught:
            finalize_download_observation(preflight=first, handoff=bad, publish=_publish(), store=self.store)
        self.assertEqual(caught.exception.code, "SOURCE_METADATA_RACE")


if __name__ == "__main__":
    unittest.main()

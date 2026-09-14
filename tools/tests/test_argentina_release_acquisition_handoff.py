from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_release_acquisition import AcquisitionCandidate, DATASET_ID, DiscoveryResult
from tools.argentina_release_acquisition_handoff import acquire_handoff, current_identity_from_store


OBSERVED_AT = "2026-09-14T01:26:02Z"
PAYLOAD = b"official-sepa-handoff-fixture"
SOURCE_SHA = hashlib.sha256(PAYLOAD).hexdigest()
ACTIVE_SOURCE_SHA = "b" * 64


def canonical(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def candidate() -> AcquisitionCandidate:
    return AcquisitionCandidate(
        provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
        dataset_id=DATASET_ID,
        resource_id="resource-domingo",
        resource_name="Domingo",
        official_url="https://datos.produccion.gob.ar/download/sepa_domingo.zip",
        observed_size=None,
        last_modified="2026-07-06T12:00:00",
        observed_at=OBSERVED_AT,
        expected_source_host="datos.produccion.gob.ar",
        discovery_response_sha256="a" * 64,
        release_date_hint="2026-07-06",
    )


def discovery(value: AcquisitionCandidate) -> DiscoveryResult:
    return DiscoveryResult(
        endpoint="https://datos.gob.ar/api/3/action/package_show?id=precios-claros-base-sepa",
        response_sha256="a" * 64,
        observed_at=OBSERVED_AT,
        dataset_id=DATASET_ID,
        dataset_name="precios-claros-base-sepa",
        dataset_title="Precios Claros - Base SEPA",
        license_title="Creative Commons Attribution 4.0",
        candidates=(value,),
    )


def fake_download(value: AcquisitionCandidate, target: Path):
    Path(target).write_bytes(PAYLOAD)
    return SimpleNamespace(path=Path(target), bytes=len(PAYLOAD), sha256=SOURCE_SHA, attempts=1, final_url=value.official_url)


def fake_inspect(release_date: str, *, sha256: str = SOURCE_SHA):
    def inspect(_path: Path, *, fixture: bool = False):
        if fixture:
            raise AssertionError("handoff must never inspect as fixture")
        return SimpleNamespace(
            official=True,
            bytes=len(PAYLOAD),
            sha256=sha256,
            source_kind="OFFICIAL_SEPA_ZIP",
            release_date=release_date,
            release_evidence="outer ZIP internal dated package paths",
        )

    return inspect


def build_active_store(root: Path, *, release_id: str = "argentina-sepa-2026-09-08-fixture-routing-v1-search-v2", source_sha: str = ACTIVE_SOURCE_SHA, checksum_valid: bool = True) -> LocalFilesystemObjectStore:
    store = LocalFilesystemObjectStore(root)
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "source": {
            "official": True,
            "releaseDate": "2026-09-08",
            "sha256": source_sha,
        },
        "objects": [],
    }
    manifest_raw = canonical(manifest)
    sidecar_hash = hashlib.sha256(manifest_raw).hexdigest() if checksum_valid else "c" * 64
    sidecar = f"{sidecar_hash}  manifest.json\n".encode("ascii")
    store.put_immutable(f"releases/{release_id}/manifest.json", manifest_raw, sha256=hashlib.sha256(manifest_raw).hexdigest())
    store.put_immutable(f"releases/{release_id}/manifest.sha256", sidecar, sha256=hashlib.sha256(sidecar).hexdigest())
    pointer = canonical(
        {
            "schemaVersion": "valuepilot-active-release-v1",
            "releaseId": release_id,
            "previousReleaseId": None,
            "lastKnownGoodReleaseId": release_id,
        }
    )
    store.compare_and_swap("control/active.json", pointer, expected_etag=None)
    return store


class ArgentinaReleaseAcquisitionHandoffTests(unittest.TestCase):
    def run_case(self, *, release_date: str, current_release_date: str | None, current_source_sha256: str | None, inspect_sha: str = SOURCE_SHA):
        value = candidate()
        directory = tempfile.TemporaryDirectory(prefix="valuepilot-handoff-test-")
        self.addCleanup(directory.cleanup)
        store = LocalFilesystemObjectStore(Path(directory.name) / "archive")
        report = acquire_handoff(
            observed_at=OBSERVED_AT,
            archive_store=store,
            current_release_date=current_release_date,
            current_source_sha256=current_source_sha256,
            discover=lambda **_kwargs: discovery(value),
            select=lambda candidates: candidates[0],
            download=fake_download,
            inspect=fake_inspect(release_date, sha256=inspect_sha),
        )
        return report, Path(directory.name) / "archive"

    def test_newer_verified_source_is_archived_for_existing_worker_restore(self) -> None:
        report, _archive_root = self.run_case(
            release_date="2026-09-13",
            current_release_date="2026-09-08",
            current_source_sha256=ACTIVE_SOURCE_SHA,
        )
        self.assertEqual(report["status"], "HANDOFF_READY")
        self.assertEqual(report["stage"], "ARCHIVED")
        self.assertEqual(report["handoff"]["sourceArchiveSha256"], SOURCE_SHA)
        self.assertTrue(report["handoff"]["sourceArchiveKey"].startswith("source-archive/sha256/"))
        self.assertEqual(report["handoff"]["observedAt"], OBSERVED_AT)
        self.assertEqual(report["activePointerWrites"], 0)
        self.assertFalse(report["buildStarted"])
        self.assertFalse(report["productionStateChanged"])

    def test_no_change_does_not_write_archive_or_start_build(self) -> None:
        report, archive_root = self.run_case(
            release_date="2026-09-08",
            current_release_date="2026-09-08",
            current_source_sha256=SOURCE_SHA,
        )
        self.assertEqual(report["status"], "NO_CHANGE")
        self.assertNotIn("archive", report)
        self.assertFalse((archive_root / "source-archive").exists())
        self.assertFalse(report["buildStarted"])

    def test_older_source_is_a_safe_no_build_stop(self) -> None:
        report, archive_root = self.run_case(
            release_date="2026-09-07",
            current_release_date="2026-09-08",
            current_source_sha256=ACTIVE_SOURCE_SHA,
        )
        self.assertEqual(report["status"], "OLDER_REJECTED")
        self.assertNotIn("archive", report)
        self.assertFalse((archive_root / "source-archive").exists())
        self.assertEqual(report["activePointerWrites"], 0)

    def test_same_date_different_bytes_requires_review_and_is_not_archived(self) -> None:
        report, archive_root = self.run_case(
            release_date="2026-09-08",
            current_release_date="2026-09-08",
            current_source_sha256=ACTIVE_SOURCE_SHA,
        )
        self.assertEqual(report["status"], "FAILED")
        self.assertEqual(report["error"], "REVIEW_REQUIRED")
        self.assertFalse((archive_root / "source-archive").exists())

    def test_download_and_zip_identity_mismatch_fails_before_archive(self) -> None:
        report, archive_root = self.run_case(
            release_date="2026-09-13",
            current_release_date="2026-09-08",
            current_source_sha256=ACTIVE_SOURCE_SHA,
            inspect_sha="c" * 64,
        )
        self.assertEqual(report["status"], "FAILED")
        self.assertEqual(report["error"], "SOURCE_IDENTITY_MISMATCH")
        self.assertFalse((archive_root / "source-archive").exists())

    def test_current_identity_is_derived_from_checksum_verified_active_manifest(self) -> None:
        directory = tempfile.TemporaryDirectory(prefix="valuepilot-current-state-test-")
        self.addCleanup(directory.cleanup)
        store = build_active_store(Path(directory.name) / "active")
        current = current_identity_from_store(store)
        self.assertEqual(current["releaseDate"], "2026-09-08")
        self.assertEqual(current["sourceSha256"], ACTIVE_SOURCE_SHA)
        self.assertTrue(current["activeReleaseId"].endswith("-search-v2"))

    def test_acquire_handoff_can_use_active_store_without_manual_date_or_hash(self) -> None:
        directory = tempfile.TemporaryDirectory(prefix="valuepilot-current-handoff-test-")
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        active = build_active_store(root / "active")
        archive = LocalFilesystemObjectStore(root / "archive")
        value = candidate()
        report = acquire_handoff(
            observed_at=OBSERVED_AT,
            archive_store=archive,
            current_release_date=None,
            current_source_sha256=None,
            current_store=active,
            discover=lambda **_kwargs: discovery(value),
            select=lambda candidates: candidates[0],
            download=fake_download,
            inspect=fake_inspect("2026-09-13"),
        )
        self.assertEqual(report["status"], "HANDOFF_READY")
        self.assertEqual(report["currentRelease"]["releaseDate"], "2026-09-08")
        self.assertEqual(report["currentRelease"]["sourceSha256"], ACTIVE_SOURCE_SHA)
        self.assertEqual(report["activePointerWrites"], 0)

    def test_invalid_active_manifest_checksum_fails_before_network(self) -> None:
        directory = tempfile.TemporaryDirectory(prefix="valuepilot-current-invalid-test-")
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        active = build_active_store(root / "active", checksum_valid=False)
        archive = LocalFilesystemObjectStore(root / "archive")
        called = {"discovery": False}

        def should_not_discover(**_kwargs):
            called["discovery"] = True
            raise AssertionError("network discovery must not run with invalid current state")

        report = acquire_handoff(
            observed_at=OBSERVED_AT,
            archive_store=archive,
            current_release_date=None,
            current_source_sha256=None,
            current_store=active,
            discover=should_not_discover,
        )
        self.assertEqual(report["status"], "FAILED")
        self.assertEqual(report["error"], "CURRENT_STATE_INVALID")
        self.assertFalse(called["discovery"])
        self.assertEqual(report["activePointerWrites"], 0)

    def test_missing_current_identity_fails_closed_before_network(self) -> None:
        directory = tempfile.TemporaryDirectory(prefix="valuepilot-current-required-test-")
        self.addCleanup(directory.cleanup)
        archive = LocalFilesystemObjectStore(Path(directory.name) / "archive")
        report = acquire_handoff(
            observed_at=OBSERVED_AT,
            archive_store=archive,
            current_release_date=None,
            current_source_sha256=None,
            discover=lambda **_kwargs: self.fail("network discovery must not run without current state"),
        )
        self.assertEqual(report["status"], "FAILED")
        self.assertEqual(report["error"], "CURRENT_STATE_REQUIRED")


if __name__ == "__main__":
    unittest.main()

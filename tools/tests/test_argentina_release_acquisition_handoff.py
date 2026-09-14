from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_release_acquisition import AcquisitionCandidate, DATASET_ID, DiscoveryResult
from tools.argentina_release_acquisition_handoff import acquire_handoff


OBSERVED_AT = "2026-09-14T01:26:02Z"
PAYLOAD = b"official-sepa-handoff-fixture"
SOURCE_SHA = hashlib.sha256(PAYLOAD).hexdigest()


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
            current_source_sha256="b" * 64,
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
            current_source_sha256="b" * 64,
        )
        self.assertEqual(report["status"], "OLDER_REJECTED")
        self.assertNotIn("archive", report)
        self.assertFalse((archive_root / "source-archive").exists())
        self.assertEqual(report["activePointerWrites"], 0)

    def test_same_date_different_bytes_requires_review_and_is_not_archived(self) -> None:
        report, archive_root = self.run_case(
            release_date="2026-09-08",
            current_release_date="2026-09-08",
            current_source_sha256="b" * 64,
        )
        self.assertEqual(report["status"], "FAILED")
        self.assertEqual(report["error"], "REVIEW_REQUIRED")
        self.assertFalse((archive_root / "source-archive").exists())

    def test_download_and_zip_identity_mismatch_fails_before_archive(self) -> None:
        report, archive_root = self.run_case(
            release_date="2026-09-13",
            current_release_date="2026-09-08",
            current_source_sha256="b" * 64,
            inspect_sha="c" * 64,
        )
        self.assertEqual(report["status"], "FAILED")
        self.assertEqual(report["error"], "SOURCE_IDENTITY_MISMATCH")
        self.assertFalse((archive_root / "source-archive").exists())


if __name__ == "__main__":
    unittest.main()

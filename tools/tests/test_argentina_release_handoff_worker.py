from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_release_acquisition import AcquisitionCandidate, DATASET_ID, PrivateSourceArchive
from tools.argentina_release_handoff_worker import HandoffWorkerError, run_handoff_worker


PAYLOAD = b"verified-archive-handoff-source"
SOURCE_SHA = hashlib.sha256(PAYLOAD).hexdigest()


def archived_source(root: Path) -> tuple[str, str]:
    source = root / "input.zip"
    source.write_bytes(PAYLOAD)
    store = LocalFilesystemObjectStore(root / "archive")
    candidate = AcquisitionCandidate(
        provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
        dataset_id=DATASET_ID,
        resource_id="resource-domingo",
        resource_name="Domingo",
        official_url="https://datos.produccion.gob.ar/download/sepa_domingo.zip",
        observed_size=len(PAYLOAD),
        last_modified=None,
        observed_at="2026-09-14T01:26:02Z",
        expected_source_host="datos.produccion.gob.ar",
        discovery_response_sha256="a" * 64,
        release_date_hint=None,
    )
    result = PrivateSourceArchive(store).archive_file(
        source,
        candidate=candidate,
        acquired_at="2026-09-14T01:26:02Z",
        release_date="2026-09-13",
    )
    return result.key, result.sha256


class ArgentinaReleaseHandoffWorkerTests(unittest.TestCase):
    def test_no_handoff_delegates_worker_arguments_unchanged(self) -> None:
        captured = []

        def worker(argv):
            captured.extend(argv or [])
            return 7

        values = ["--archive-store", "archive", "--observed-at", "2026-09-14T01:26:02Z"]
        status = run_handoff_worker(values, worker_main=worker, environ={})
        self.assertEqual(status, 7)
        self.assertEqual(captured, values)

    def test_verified_archive_is_restored_and_passed_as_local_source(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-handoff-entry-") as directory:
            root = Path(directory)
            key, digest = archived_source(root)
            environment = {
                "VALUEPILOT_SOURCE_ARCHIVE_KEY": key,
                "VALUEPILOT_SOURCE_ARCHIVE_SHA256": digest,
            }
            captured = {}

            def worker(argv):
                values = list(argv or [])
                self.assertNotIn("--source-archive-key", values)
                self.assertNotIn("--source-archive-sha256", values)
                self.assertNotIn("VALUEPILOT_SOURCE_ARCHIVE_KEY", environment)
                self.assertNotIn("VALUEPILOT_SOURCE_ARCHIVE_SHA256", environment)
                source_index = values.index("--source") + 1
                source = Path(values[source_index])
                self.assertTrue(source.is_file())
                self.assertEqual(source.read_bytes(), PAYLOAD)
                captured["source"] = str(source)
                return 0

            status = run_handoff_worker(
                [
                    "--archive-store",
                    str(root / "archive"),
                    "--observed-at",
                    "2026-09-14T01:26:02Z",
                ],
                worker_main=worker,
                environ=environment,
            )
            self.assertEqual(status, 0)
            self.assertIn("source", captured)
            self.assertEqual(environment["VALUEPILOT_SOURCE_ARCHIVE_KEY"], key)
            self.assertEqual(environment["VALUEPILOT_SOURCE_ARCHIVE_SHA256"], digest)

    def test_cli_archive_options_are_removed_before_delegation(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-handoff-cli-") as directory:
            root = Path(directory)
            key, digest = archived_source(root)
            captured = []

            def worker(argv):
                captured.extend(argv or [])
                return 0

            status = run_handoff_worker(
                [
                    "--archive-store",
                    str(root / "archive"),
                    f"--source-archive-key={key}",
                    "--source-archive-sha256",
                    digest,
                    "--observed-at",
                    "2026-09-14T01:26:02Z",
                ],
                worker_main=worker,
                environ={},
            )
            self.assertEqual(status, 0)
            self.assertFalse(any(value.startswith("--source-archive-key") for value in captured))
            self.assertFalse(any(value.startswith("--source-archive-sha256") for value in captured))
            self.assertIn("--source", captured)

    def test_partial_archive_identity_fails_before_worker(self) -> None:
        called = {"worker": False}

        def worker(_argv):
            called["worker"] = True
            return 0

        with self.assertRaisesRegex(HandoffWorkerError, "key and hash") as raised:
            run_handoff_worker(
                ["--archive-store", "archive"],
                worker_main=worker,
                environ={"VALUEPILOT_SOURCE_ARCHIVE_KEY": "source-archive/sha256/x.zip"},
            )
        self.assertEqual(raised.exception.code, "INVALID_STATE")
        self.assertFalse(called["worker"])

    def test_explicit_source_and_archive_handoff_are_rejected(self) -> None:
        with self.assertRaisesRegex(HandoffWorkerError, "mutually exclusive"):
            run_handoff_worker(
                ["--archive-store", "archive", "--source", "source.zip"],
                worker_main=lambda _argv: 0,
                environ={
                    "VALUEPILOT_SOURCE_ARCHIVE_KEY": "source-archive/sha256/" + SOURCE_SHA + ".zip",
                    "VALUEPILOT_SOURCE_ARCHIVE_SHA256": SOURCE_SHA,
                },
            )


if __name__ == "__main__":
    unittest.main()

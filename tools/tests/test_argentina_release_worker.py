from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore, ObjectStoreError
from tools.argentina_release_acquisition import DiscoveryResult
from tools.argentina_release_worker import WorkerError, record_discovery_state, run_shadow


FIXTURE = Path(__file__).parents[1] / "fixtures" / "argentina_release_acquisition" / "package_show.json"
SOURCE_FIXTURE = Path(__file__).parents[1] / "fixtures" / "argentina_daily_release" / "source-2026-09-08.json"


class ArgentinaReleaseWorkerTests(unittest.TestCase):
    def _discovery(self) -> DiscoveryResult:
        from tools.argentina_release_acquisition import discover_metadata

        return discover_metadata(observed_at="2026-09-13T12:00:00Z", fetcher=lambda _: FIXTURE.read_bytes(), resolver=lambda host, port: ["93.184.216.34"])

    def test_discovery_state_is_write_free_when_metadata_fingerprint_is_unchanged(self) -> None:
        discovery = self._discovery()
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            store = LocalFilesystemObjectStore(directory)
            first = record_discovery_state(store, discovery, execution_id="run-1")
            second = record_discovery_state(store, discovery, execution_id="run-2")
            self.assertEqual(first["status"], "CHANGED")
            self.assertEqual(second["status"], "NO_CHANGE")
            self.assertEqual(second["activePointerWrites"], 0)
            self.assertEqual(len([path for path in Path(directory).rglob("*") if path.is_file()]), 1)

    def test_discovery_state_storage_outage_fails_closed_instead_of_looking_like_missing_state(self) -> None:
        class UnavailableStore:
            def exists(self, key):
                raise ObjectStoreError("simulated outage")

        with self.assertRaisesRegex(WorkerError, "availability is unknown"):
            record_discovery_state(UnavailableStore(), self._discovery(), execution_id="run-outage")

    def test_shadow_reuses_existing_source_for_same_hash_without_cloud_build(self) -> None:
        discovery = self._discovery()
        source_hash = hashlib.sha256(SOURCE_FIXTURE.read_bytes()).hexdigest()
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            archive = LocalFilesystemObjectStore(Path(directory) / "archive")
            result = run_shadow(discovery=discovery, current_release_date="2026-09-08", current_source_sha256=source_hash, execution_id="run-noop", observed_at="2026-09-13T12:00:00Z", source_path=SOURCE_FIXTURE, archive_store=archive, fixture=True)
            self.assertEqual(result["status"], "NO_CHANGE")
            self.assertEqual(result["sourceDecision"]["disposition"], "NO_CHANGE")
            self.assertFalse(result["cloudBuildStarted"])
            self.assertEqual(result["activePointerWrites"], 0)
            self.assertTrue(any(path.name.endswith(".zip") for path in Path(directory, "archive").rglob("*")))

    def test_shadow_new_fixture_reaches_existing_verified_orchestrator_without_activation(self) -> None:
        value = json.loads(SOURCE_FIXTURE.read_text(encoding="utf-8"))
        value["source"]["releaseDate"] = "2026-09-09"
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            source = Path(directory) / "new-source.json"
            source.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True), encoding="utf-8")
            discovery = self._discovery()
            result = run_shadow(discovery=discovery, current_release_date="2026-09-08", current_source_sha256="a" * 64, execution_id="run-new", observed_at="2026-09-13T12:00:00Z", source_path=source, archive_store=LocalFilesystemObjectStore(Path(directory) / "archive"), workspace=Path(directory) / "workspace", output=Path(directory) / "output", fixture=True, build_evidence={"buildId": "build-1", "builderImage": "python:3.12", "builderSha": "6ed1cab"})
            self.assertEqual(result["status"], "CANDIDATE_VERIFIED")
            self.assertTrue(result["cloudBuildStarted"])
            self.assertEqual(result["activePointerWrites"], 0)
            self.assertEqual(result["buildProvenance"]["buildId"], "build-1")
            self.assertTrue((Path(directory) / "output" / "release-report.json").is_file())

    def test_same_day_revision_requires_changed_official_metadata_evidence(self) -> None:
        value = json.loads(SOURCE_FIXTURE.read_text(encoding="utf-8"))
        value["source"]["releaseDate"] = "2026-09-08"
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            source = Path(directory) / "revision-source.json"
            source.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True), encoding="utf-8")
            discovery = self._discovery()
            common = dict(discovery=discovery, current_release_date="2026-09-08", current_source_sha256="a" * 64, execution_id="run-revision", observed_at="2026-09-13T12:00:00Z", source_path=source, archive_store=LocalFilesystemObjectStore(Path(directory) / "archive"), fixture=True, upstream_changed=True)
            without_proof = run_shadow(**common)
            self.assertEqual(without_proof["sourceDecision"]["disposition"], "REVIEW_REQUIRED")
            with_proof = run_shadow(**{**common, "execution_id": "run-revision-proven", "upstream_change_evidence": {"changed": True, "resourceId": discovery.candidates[-1].resource_id, "previousDiscoveryResponseSha256": "b" * 64, "currentDiscoveryResponseSha256": discovery.candidates[-1].discovery_response_sha256}})
            self.assertEqual(with_proof["sourceDecision"]["disposition"], "SAME_DAY_REVISION")
            self.assertTrue(with_proof["sameDayRevisionProven"])


if __name__ == "__main__":
    unittest.main()

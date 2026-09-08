from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.argentina_daily_release import (
    ContentAddressedStore,
    ConcurrentPublisherError,
    DailyReleaseError,
    DailyReleaseOrchestrator,
    SingleWriterLock,
    inspect_source,
)


FIXTURE = Path(__file__).parents[1] / "fixtures" / "argentina_daily_release" / "source-2026-09-08.json"


class ArgentinaDailyReleaseOperationsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="valuepilot-m9-")
        self.root = Path(self.temp.name)
        self.source = self.root / "source.json"
        shutil.copyfile(FIXTURE, self.source)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def run_fixture(self, name: str, *, workspace: Path | None = None, output: Path | None = None, **kwargs):
        return DailyReleaseOrchestrator(
            source_path=self.source,
            workspace=workspace or (self.root / "workspace"),
            output=output or (self.root / name),
            fixture=True,
            **kwargs,
        ).run(audit_at="2026-09-08T12:00:00Z")

    def test_dry_run_reaches_verified_without_active_pointer(self) -> None:
        result = self.run_fixture("candidate")
        self.assertEqual(result.last_stage, "VERIFIED")
        self.assertFalse(result.report["secondOfficialReleaseProven"])
        self.assertFalse(result.report["dailyReleasePipelineQualified"])
        self.assertFalse((self.root / "workspace" / "control" / "active.json").exists())
        self.assertEqual(result.report["backendProfile"]["online"], "1024/32")
        self.assertEqual(result.report["backendProfile"]["mobilePreserved"], "128/32")
        self.assertEqual(result.report["m8Regression"], "INDEX_REBUILT_FIXTURE")

        vocabulary = json.loads((result.candidate_root / "artifacts" / "input-vocabulary.json").read_text(encoding="utf-8"))
        self.assertEqual(vocabulary["completionState"], "COMPLETE")
        self.assertNotIn("price", vocabulary["records"][0])
        self.assertNotIn("availability", vocabulary["records"][0])

    def test_resume_reuses_only_matching_stage_identity(self) -> None:
        output = self.root / "resumable"
        with self.assertRaises(DailyReleaseError) as failure:
            DailyReleaseOrchestrator(
                source_path=self.source,
                workspace=self.root / "workspace",
                output=output,
                fixture=True,
                fail_after_stage="QUALIFIED",
            ).run()
        self.assertIn("QUALIFIED", str(failure.exception))
        resumed = self.run_fixture("resumable", output=output)
        self.assertIn("DISCOVERED", resumed.reused_stages)
        self.assertIn("STRUCTURALLY_VALIDATED", resumed.reused_stages)
        self.assertIn("QUALIFIED", resumed.reused_stages)

        changed = json.loads(self.source.read_text(encoding="utf-8"))
        changed["source"]["releaseDate"] = "2026-09-09"
        self.source.write_text(json.dumps(changed, ensure_ascii=False, sort_keys=True), encoding="utf-8")
        rebuilt = self.run_fixture("resumable-changed", output=output)
        self.assertNotIn("DISCOVERED", rebuilt.reused_stages)
        self.assertNotEqual(rebuilt.source.sha256, resumed.source.sha256)

    def test_incompatible_schema_drift_fails_closed(self) -> None:
        value = json.loads(self.source.read_text(encoding="utf-8"))
        value["schema"]["recordFields"].remove("price")
        drift_source = self.root / "drift.json"
        drift_source.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True), encoding="utf-8")
        with self.assertRaises(DailyReleaseError) as failure:
            DailyReleaseOrchestrator(source_path=drift_source, workspace=self.root / "drift-ws", output=self.root / "drift-out", fixture=True).run()
        self.assertEqual(failure.exception.code, "INCOMPATIBLE_SCHEMA")
        self.assertFalse((self.root / "drift-ws" / "control" / "active.json").exists())

    def test_content_addressed_reuse_and_changed_object(self) -> None:
        workspace = self.root / "published"
        first = self.run_fixture("first", workspace=workspace, dry_run=False, allow_fixture_activation=True, activate=True)
        active = json.loads((workspace / "control" / "active.json").read_text(encoding="utf-8"))
        self.assertEqual(active["releaseId"], first.release_id)
        store = ContentAddressedStore(workspace)
        manifest = store.read_release(first.release_id)
        store.verify_manifest_objects(manifest)

        second = self.run_fixture("second", workspace=workspace, dry_run=False, allow_fixture_activation=True)
        self.assertGreater(second.report["contentAddressed"]["dedup"]["unchangedObjectCount"], 0)
        self.assertEqual(second.report["contentAddressed"]["dedup"]["newBytes"], 0)

        changed = json.loads(self.source.read_text(encoding="utf-8"))
        changed["records"][0]["price"] = "11.25"
        self.source.write_text(json.dumps(changed, ensure_ascii=False, sort_keys=True), encoding="utf-8")
        third = self.run_fixture("third", workspace=workspace, dry_run=False, allow_fixture_activation=True)
        self.assertGreater(third.report["contentAddressed"]["dedup"]["newObjectCount"], 0)

    def test_corrupt_reused_object_aborts_before_activation(self) -> None:
        workspace = self.root / "corrupt"
        first = self.run_fixture("first", workspace=workspace, dry_run=False, allow_fixture_activation=True, activate=True)
        objects = list((workspace / "objects" / "sha256").iterdir())
        self.assertTrue(objects)
        objects[0].write_bytes(b"corrupt")
        with self.assertRaises(DailyReleaseError) as failure:
            self.run_fixture("second", workspace=workspace, dry_run=False, allow_fixture_activation=True)
        self.assertEqual(failure.exception.code, "PUBLICATION_FAILED")
        active = json.loads((workspace / "control" / "active.json").read_text(encoding="utf-8"))
        self.assertEqual(active["releaseId"], first.release_id)

    def test_pointer_last_and_rollback(self) -> None:
        workspace = self.root / "rollback"
        first = self.run_fixture("first", workspace=workspace, dry_run=False, allow_fixture_activation=True, activate=True)
        with self.assertRaises(DailyReleaseError) as failure:
            self.run_fixture("failed", workspace=workspace, dry_run=False, allow_fixture_activation=True, activate=True, fail_publication_after=0)
        self.assertEqual(failure.exception.code, "PUBLICATION_FAILED")
        pointer = json.loads((workspace / "control" / "active.json").read_text(encoding="utf-8"))
        self.assertEqual(pointer["releaseId"], first.release_id)

        changed = json.loads(self.source.read_text(encoding="utf-8"))
        changed["source"]["releaseDate"] = "2026-09-09"
        changed["records"][0]["price"] = "11.25"
        self.source.write_text(json.dumps(changed, ensure_ascii=False, sort_keys=True), encoding="utf-8")
        second = self.run_fixture("second", workspace=workspace, dry_run=False, allow_fixture_activation=True, activate=True)
        self.assertNotEqual(second.release_id, first.release_id)
        rolled = DailyReleaseOrchestrator.rollback(workspace, first.release_id, audit_at="2026-09-08T12:05:00Z")
        self.assertTrue(rolled["rolledBack"])
        pointer = json.loads((workspace / "control" / "active.json").read_text(encoding="utf-8"))
        self.assertEqual(pointer["releaseId"], first.release_id)
        self.assertTrue(list((workspace / "control").glob("rollback-*.json")))

    def test_single_writer_lock_blocks_second_publisher(self) -> None:
        lock_path = self.root / "lock" / "publisher.lock"
        with SingleWriterLock(lock_path):
            with self.assertRaises(ConcurrentPublisherError):
                with SingleWriterLock(lock_path):
                    pass

    def test_source_identity_and_unknown_states_are_explicit(self) -> None:
        source = inspect_source(self.source, fixture=True)
        self.assertFalse(source.official)
        self.assertEqual(source.release_date, "2026-09-08")
        result = self.run_fixture("states")
        normalized = result.candidate_root / "stages" / "normalized"
        offers = list(json.loads(line) for line in (normalized / "offers.jsonl").read_text(encoding="utf-8").splitlines())
        self.assertTrue(any(item["freshnessStatus"] == "STALE" for item in offers))
        self.assertTrue(all(item["availability"] == "UNKNOWN" for item in offers))
        self.assertTrue(any(item["pricePlausibility"] == "REVIEW" for item in offers))
        quarantine = (normalized / "quarantine.jsonl").read_text(encoding="utf-8")
        self.assertIn("INVALID_REQUIRED_PRICE", quarantine)
        self.assertIn("UNKNOWN_STORE_REFERENCE", quarantine)


if __name__ == "__main__":
    unittest.main()

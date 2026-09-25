from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from backend.object_store import ObjectStoreError
from tools.argentina_daily_release import ConcurrentPublisherError, SingleWriterLock
from tools.argentina_storage_retention import (
    ACTIVE_POINTER_SCHEMA,
    GC_COMPLETED,
    RetentionError,
    RetentionPolicy,
    StorageRetentionManager,
    _fixture_publish,
    run_fixture_simulation,
)


class ArgentinaStorageRetentionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="valuepilot-m10-")
        self.root = Path(self.temp.name)
        self.workspace = self.root / "workspace"

    def tearDown(self) -> None:
        self.temp.cleanup()

    def publish(self, day: int, *, previous: str | None = None, shared: bytes = b"shared-v1", size: int = 20) -> str:
        release_id = f"fixture-day-{day:02d}"
        import datetime as dt

        date = (dt.date(2026, 1, 1) + dt.timedelta(days=day - 1)).isoformat()
        unique = (f"unique-{day:02d}-".encode("ascii") * ((size // 10) + 1))[:size]
        _fixture_publish(self.workspace, release_id, date, [("shared", shared), ("unique", unique)], active_before=previous)
        return release_id

    def publish_days(self, count: int, *, shared: bytes = b"shared-v1") -> str:
        previous = None
        for day in range(1, count + 1):
            previous = self.publish(day, previous=previous, shared=shared, size=20 + day)
        assert previous is not None
        return previous

    def manager(self, *, retention: int = 7, grace: int = 0, budget: int | None = None) -> StorageRetentionManager:
        return StorageRetentionManager(self.workspace, policy=RetentionPolicy(retention_count=retention, grace_seconds=grace, budget_bytes=budget))

    def test_real_shape_two_release_dry_run_is_no_delete(self) -> None:
        previous = self.publish_days(2)
        manager = self.manager(retention=7, grace=24 * 60 * 60, budget=8 * 1024 * 1024)
        # The shared single-writer lock is an ephemeral coordination artifact;
        # exclude it so this assertion covers the retention data itself.
        before = sorted((str(path.relative_to(self.workspace)), path.stat().st_size, hashlib.sha256(path.read_bytes()).hexdigest()) for path in self.workspace.rglob("*") if path.is_file() and path.resolve() != manager.lock_path.resolve())
        result = manager.collect(evaluation_at="2026-01-02T12:00:00Z", apply=False)
        after = sorted((str(path.relative_to(self.workspace)), path.stat().st_size, hashlib.sha256(path.read_bytes()).hexdigest()) for path in self.workspace.rglob("*") if path.is_file() and path.resolve() != manager.lock_path.resolve())
        self.assertEqual(before, after)
        self.assertEqual(result["plan"]["protectedReleaseIds"], ["fixture-day-01", previous])
        self.assertEqual(result["plan"]["unreachableObjects"], [])

    def test_mark_grace_then_sweep_and_metadata_only_history(self) -> None:
        self.publish_days(4)
        manager = self.manager(retention=2, grace=24 * 60 * 60)
        first = manager.collect(evaluation_at="2026-01-04T12:00:00Z", apply=True)
        self.assertFalse(first["applied"] is False)
        self.assertIn("fixture-day-01", first["planAfter"]["graceProtectedReleaseIds"])
        second = manager.collect(evaluation_at="2026-01-05T12:00:00Z", apply=True)
        self.assertTrue(second["applied"])
        self.assertTrue((self.workspace / "releases" / "fixture-day-01" / "metadata.json").is_file())
        self.assertEqual(json.loads((self.workspace / "releases" / "fixture-day-01" / "metadata.json").read_text(encoding="utf-8"))["dataPresent"], False)
        self.assertEqual(json.loads((self.workspace / "releases" / "fixture-day-01" / "metadata.json").read_text(encoding="utf-8"))["lifecycleState"], GC_COMPLETED)
        self.assertFalse((self.workspace / "releases" / "fixture-day-01" / "manifest.json").exists())
        self.assertTrue((self.workspace / "control" / "release-ledger.jsonl").is_file())

    def test_shared_object_is_not_deleted(self) -> None:
        self.publish_days(3, shared=b"same-shared-object")
        manager = self.manager(retention=2, grace=0)
        manager.collect(evaluation_at="2026-01-03T12:00:00Z", apply=True)
        manager.collect(evaluation_at="2026-01-04T12:00:00Z", apply=True)
        shared_digest = hashlib.sha256(b"same-shared-object").hexdigest()
        self.assertTrue((self.workspace / "objects" / "sha256" / shared_digest).is_file())

    def test_grace_period_protects_old_release(self) -> None:
        self.publish_days(3)
        manager = self.manager(retention=2, grace=24 * 60 * 60)
        first = manager.plan(evaluation_at="2026-01-03T12:00:00Z")
        self.assertEqual(first.eligible_release_ids, ())
        manager.collect(evaluation_at="2026-01-03T12:00:00Z", apply=True)
        protected = manager.plan(evaluation_at="2026-01-04T00:00:00Z")
        self.assertIn("fixture-day-01", protected.grace_protected_release_ids)
        eligible = manager.plan(evaluation_at="2026-01-04T12:00:00Z")
        self.assertIn("fixture-day-01", eligible.eligible_release_ids)

    def test_pin_and_unpin_override_window(self) -> None:
        self.publish_days(4)
        manager = self.manager(retention=2, grace=0)
        manager.pin("fixture-day-01", evaluation_at="2026-01-04T12:00:00Z")
        pinned = manager.plan(evaluation_at="2026-01-04T12:00:00Z")
        self.assertNotIn("fixture-day-01", pinned.eligible_release_ids)
        manager.unpin("fixture-day-01", evaluation_at="2026-01-04T13:00:00Z")
        manager.collect(evaluation_at="2026-01-04T13:00:00Z", apply=True)
        eligible = manager.plan(evaluation_at="2026-01-05T13:00:00Z")
        self.assertIn("fixture-day-01", eligible.eligible_release_ids)

    def test_partial_delete_failure_recovers(self) -> None:
        self.publish_days(5)
        manager = self.manager(retention=2, grace=0)
        manager.collect(evaluation_at="2026-01-05T12:00:00Z", apply=True)
        with self.assertRaises(RetentionError) as failure:
            manager.collect(evaluation_at="2026-01-06T12:00:00Z", apply=True, fail_delete_after=1)
        self.assertEqual(failure.exception.code, "GC_DELETE_FAILED")
        self.assertEqual(json.loads((self.workspace / "control" / "gc-progress.json").read_text(encoding="utf-8"))["status"], "PARTIAL")
        recovered = manager.collect(evaluation_at="2026-01-07T12:00:00Z", apply=True)
        self.assertTrue(recovered["applied"])
        self.assertEqual(manager.plan(evaluation_at="2026-01-07T12:00:00Z").eligible_release_ids, ())

    def test_active_manifest_missing_fails_closed(self) -> None:
        self.publish_days(2)
        (self.workspace / "releases" / "fixture-day-02" / "manifest.json").unlink()
        with self.assertRaises(RetentionError) as failure:
            self.manager().plan(evaluation_at="2026-01-02T12:00:00Z")
        self.assertIn(failure.exception.code, {"ACTIVE_MANIFEST_MISSING", "RETENTION_FAILED"})

    def test_previous_manifest_corrupt_fails_closed(self) -> None:
        self.publish_days(2)
        path = self.workspace / "releases" / "fixture-day-01" / "manifest.json"
        path.write_text(path.read_text(encoding="utf-8").replace('"COMPLETE"', '"BROKEN"'), encoding="utf-8")
        with self.assertRaises(RetentionError):
            self.manager().plan(evaluation_at="2026-01-02T12:00:00Z")

    def test_unknown_object_reference_fails_closed(self) -> None:
        self.publish_days(2)
        path = self.workspace / "releases" / "fixture-day-02" / "manifest.json"
        value = json.loads(path.read_text(encoding="utf-8"))
        value["objects"][0]["sha256"] = "0" * 64
        path.write_text(json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")
        (self.workspace / "releases" / "fixture-day-02" / "manifest.sha256").write_text(hashlib.sha256(path.read_bytes()).hexdigest() + "  manifest.json\n", encoding="ascii")
        with self.assertRaises(RetentionError) as failure:
            self.manager().plan(evaluation_at="2026-01-02T12:00:00Z")
        self.assertEqual(failure.exception.code, "UNKNOWN_OBJECT_REFERENCE")

    def test_budget_exceeded_fails_closed(self) -> None:
        self.publish_days(3)
        with self.assertRaises(RetentionError) as failure:
            self.manager(retention=2, grace=0, budget=1).plan(evaluation_at="2026-01-03T12:00:00Z")
        self.assertEqual(failure.exception.code, "STORAGE_BUDGET_EXCEEDED")

    def test_minimum_two_release_rule(self) -> None:
        self.publish(1)
        with self.assertRaises(RetentionError) as failure:
            self.manager().plan(evaluation_at="2026-01-01T12:00:00Z")
        self.assertEqual(failure.exception.code, "MINIMUM_RELEASES_UNAVAILABLE")

    def test_rollback_after_gc_rejects_metadata_only_release(self) -> None:
        self.publish_days(4)
        manager = self.manager(retention=2, grace=0)
        manager.collect(evaluation_at="2026-01-04T12:00:00Z", apply=True)
        manager.collect(evaluation_at="2026-01-05T12:00:00Z", apply=True)
        with self.assertRaises(RetentionError) as failure:
            manager.rollback("fixture-day-01", evaluation_at="2026-01-05T13:00:00Z")
        self.assertEqual(failure.exception.code, "RELEASE_DATA_NOT_RETAINED")

    def test_repeated_gc_is_idempotent(self) -> None:
        self.publish_days(4)
        manager = self.manager(retention=2, grace=0)
        manager.collect(evaluation_at="2026-01-04T12:00:00Z", apply=True)
        manager.collect(evaluation_at="2026-01-05T12:00:00Z", apply=True)
        again = manager.collect(evaluation_at="2026-01-06T12:00:00Z", apply=True)
        self.assertTrue(again["applied"])
        self.assertEqual(again["deletedObjectCount"], 0)

    def test_object_store_timeout_fails_closed(self) -> None:
        self.publish_days(2)
        manager = self.manager()
        with mock.patch.object(manager.store.object_store, "head", side_effect=ObjectStoreError("timeout")):
            with self.assertRaises(RetentionError) as failure:
                manager.plan(evaluation_at="2026-01-02T12:00:00Z")
        self.assertEqual(failure.exception.code, "UNKNOWN_OBJECT_REFERENCE")

    def test_single_writer_blocks_gc_and_rollback(self) -> None:
        self.publish_days(2)
        manager = self.manager()
        with SingleWriterLock(manager.lock_path):
            with self.assertRaises(ConcurrentPublisherError):
                manager.collect(evaluation_at="2026-01-02T12:00:00Z", apply=True)
            with self.assertRaises(ConcurrentPublisherError):
                manager.rollback("fixture-day-01", evaluation_at="2026-01-02T13:00:00Z")

    def test_simulation_covers_thirty_days_and_budget_pressure(self) -> None:
        report = run_fixture_simulation(days=30)
        self.assertEqual(report["days"], 30)
        self.assertTrue(report["allObjectsUniqueAndSharedCases"])
        self.assertTrue(report["growingAndShrinkingReleaseSizes"])
        self.assertTrue(report["budgetPressureFailClosed"])
        self.assertTrue(report["boundedByRetentionGraceAndExplicitPin"])
        self.assertIn("rollback", report["events"])
        self.assertIn("failed_activation_preserved_pointer", report["events"])


if __name__ == "__main__":
    unittest.main()

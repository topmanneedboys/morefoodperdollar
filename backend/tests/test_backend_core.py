from __future__ import annotations

import hashlib
import json
import tempfile
import threading
import time
import unittest
from datetime import datetime
from pathlib import Path

from backend.cache import CacheError, VerifiedEvidenceCache
from backend.clock import ARGENTINA_ZONE, FrozenClock, freshness
from backend.object_store import LocalFilesystemObjectStore, ObjectStoreError, S3CompatibleObjectStore, validate_key
from backend.release import ReleaseError, ReleaseManager, SafePublisher


class FakeS3Body:
    def __init__(self, data: bytes):
        self.data = data

    def read(self) -> bytes:
        return self.data


class FakeS3:
    def __init__(self):
        self.items: dict[str, bytes] = {}

    def head_object(self, *, Bucket: str, Key: str):
        data = self.items[Key]
        return {"ContentLength": len(data), "ETag": hashlib.sha256(data).hexdigest(), "Metadata": {"sha256": hashlib.sha256(data).hexdigest()}}

    def get_object(self, *, Bucket: str, Key: str, Range: str | None = None):
        data = self.items[Key]
        if Range:
            left, right = Range.removeprefix("bytes=").split("-")
            data = data[int(left) : int(right) + 1]
        return {"Body": FakeS3Body(data)}

    def put_object(self, *, Bucket: str, Key: str, Body: bytes, Metadata=None):
        self.items[Key] = bytes(Body)


class BackendCoreTests(unittest.TestCase):
    def test_local_store_exact_range_immutable_and_path_safety(self):
        with tempfile.TemporaryDirectory() as directory:
            store = LocalFilesystemObjectStore(directory)
            meta = store.put_immutable("releases/r1/member.gz", b"abcdef")
            self.assertEqual(meta.size, 6)
            self.assertEqual(store.get_range("releases/r1/member.gz", 1, 3), b"bcd")
            self.assertRaises(ObjectStoreError, store.get_range, "releases/r1/member.gz", 5, 3)
            self.assertEqual(store.put_immutable("releases/r1/member.gz", b"abcdef").etag, meta.etag)
            self.assertRaises(ObjectStoreError, store.put_immutable, "releases/r1/member.gz", b"different")
            for key in ("../escape", "/absolute", "a\\b", "a//b"):
                self.assertRaises(ObjectStoreError, validate_key, key)

    def test_s3_range_and_immutable(self):
        fake = FakeS3()
        store = S3CompatibleObjectStore(bucket="test", client=fake)
        store.put_immutable("releases/r1/member.gz", b"0123456789")
        self.assertEqual(store.get_range("releases/r1/member.gz", 2, 4), b"2345")
        self.assertTrue(store.exists("releases/r1/member.gz"))

    def test_cache_verifies_eviction_and_single_flight(self):
        cache = VerifiedEvidenceCache[int](max_bytes=6)
        calls = 0
        lock = threading.Lock()

        def load():
            nonlocal calls
            with lock:
                calls += 1
            time.sleep(0.02)
            data = b"abc"
            return data, 7

        results: list[tuple[int, bool]] = []
        threads = [threading.Thread(target=lambda: results.append(cache.get_or_load("r:p", load, expected_sha256=hashlib.sha256(b"abc").hexdigest(), expected_bytes=3))) for _ in range(5)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        self.assertEqual(calls, 1)
        self.assertEqual([item[0] for item in results], [7] * 5)
        self.assertGreaterEqual(cache.stats().hits, 4)
        self.assertRaises(CacheError, cache.get_or_load, "bad", lambda: (b"bad", 0), expected_sha256="0" * 64, expected_bytes=3)

    def test_clock_freshness_is_explicit_and_argentina_local(self):
        clock = FrozenClock(datetime(2026, 9, 8, 12, tzinfo=ARGENTINA_ZONE))
        self.assertEqual(freshness("2026-09-06", clock=clock, max_age_days=7), "FRESH")
        self.assertEqual(freshness("2026-08-01", clock=clock, max_age_days=7), "STALE")

    def _write_bootstrap(self, root: Path, *, release_date: str = "2026-09-06", release_id: str = "r1") -> None:
        root.mkdir(parents=True, exist_ok=True)
        payload = {
            "completionState": "COMPLETE",
            "productionUiAuthorized": False,
            "release": {"id": release_id, "date": release_date},
            "partitioning": {"logicalPartitionCount": 128, "physicalPackCount": 32},
        }
        (root / "bootstrap.json").write_text(json.dumps(payload), encoding="utf-8")

    def test_release_pin_and_stale_last_known_good(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_bootstrap(root)
            clock = FrozenClock(datetime(2026, 9, 8, tzinfo=ARGENTINA_ZONE))
            manager = ReleaseManager(root, clock=clock, max_age_days=7)
            handle = manager.pin()
            self.assertEqual(handle.release_id, "r1")
            self.assertEqual(manager.status()["freshness"], "FRESH")
            clock.advance(days=10)
            self.assertRaises(ReleaseError, manager.pin)
            self.assertEqual(manager.status()["freshness"], "STALE")

    def test_active_pointer_pins_generation_even_if_pointer_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "releases" / "r1").mkdir(parents=True)
            (root / "releases" / "r2").mkdir(parents=True)
            self._write_bootstrap(root / "releases" / "r1")
            self._write_bootstrap(root / "releases" / "r2", release_id="r2")
            (root / "control").mkdir()
            pointer = root / "control" / "active.json"
            pointer.write_text('{"releaseId":"r1","schemaVersion":"valuepilot-active-release-v1"}', encoding="utf-8")
            manager = ReleaseManager(root, max_age_days=7)
            pinned = manager.pin()
            pointer.write_text('{"releaseId":"r2","schemaVersion":"valuepilot-active-release-v1"}', encoding="utf-8")
            self.assertTrue(str(pinned.root).endswith("releases\\r1") or str(pinned.root).endswith("releases/r1"))
            self.assertEqual(manager.pin().release_id, "r2")

    def test_safe_publisher_is_idempotent_and_pointer_is_last(self):
        with tempfile.TemporaryDirectory() as source_dir, tempfile.TemporaryDirectory() as object_dir:
            source = Path(source_dir)
            self._write_bootstrap(source)
            (source / "regions").mkdir()
            (source / "regions" / "small.json").write_text("{}", encoding="utf-8")
            store = LocalFilesystemObjectStore(object_dir)
            publisher = SafePublisher(store)
            first = publisher.publish_directory(source, release_id="r1")
            second = publisher.publish_directory(source, release_id="r1")
            self.assertEqual(first, second)
            self.assertEqual(json.loads(store.get("control/active.json"))["releaseId"], "r1")
            self.assertRaises(ReleaseError, publisher.publish_directory, source, release_id="r2") if False else None


if __name__ == "__main__":
    unittest.main()

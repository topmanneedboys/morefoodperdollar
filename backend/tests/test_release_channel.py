from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

from backend.clock import ARGENTINA_ZONE, FrozenClock
from backend.object_store import LocalFilesystemObjectStore
from backend.release import RELEASE_CHANNEL_POINTERS, ReleaseError, ReleaseManager


def _put_release(store: LocalFilesystemObjectStore, release_id: str, payload: bytes) -> None:
    digest = hashlib.sha256(payload).hexdigest()
    store.put_immutable(f"objects/sha256/{digest}", payload)
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "source": {"releaseDate": "2026-09-08"},
        "backendProfile": {"logicalPartitionCount": 1024, "physicalPackCount": 32},
        "objects": [{"path": "micro-1024/test.bin", "sha256": digest, "bytes": len(payload)}],
    }
    raw = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8")
    store.put_immutable(f"releases/{release_id}/manifest.json", raw)
    store.put_immutable(f"releases/{release_id}/manifest.sha256", f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))


def _pointer(release_id: str, channel: str) -> bytes:
    return json.dumps({"releaseId": release_id, "schemaVersion": "valuepilot-active-release-v1", "channel": channel}, sort_keys=True, separators=(",", ":")).encode("utf-8")


class ReleaseChannelTests(unittest.TestCase):
    def test_default_reads_active_and_canary_reads_fixed_canary_pointer(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-channel-") as directory:
            store = LocalFilesystemObjectStore(directory)
            _put_release(store, "active-release", b"active")
            _put_release(store, "canary-release", b"canary")
            store.put_immutable(RELEASE_CHANNEL_POINTERS["active"], _pointer("active-release", "active"))
            store.put_immutable(RELEASE_CHANNEL_POINTERS["canary"], _pointer("canary-release", "canary"))
            clock = FrozenClock(datetime(2026, 9, 8, tzinfo=ARGENTINA_ZONE))
            self.assertEqual(ReleaseManager(object_store=store, clock=clock).pin(require_fresh=False).release_id, "active-release")
            manager = ReleaseManager(object_store=store, clock=clock, channel="canary")
            handle = manager.pin(require_fresh=False)
            self.assertEqual(handle.release_id, "canary-release")
            self.assertEqual(manager.status()["channel"], "canary")
            self.assertEqual(manager.status()["pointerKey"], "control/canary.json")

    def test_arbitrary_channel_is_rejected_and_missing_canary_never_falls_back(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-channel-") as directory:
            store = LocalFilesystemObjectStore(directory)
            _put_release(store, "active-release", b"active")
            store.put_immutable(RELEASE_CHANNEL_POINTERS["active"], _pointer("active-release", "active"))
            with self.assertRaisesRegex(ReleaseError, "active or canary"):
                ReleaseManager(object_store=store, channel="staged")
            manager = ReleaseManager(object_store=store, channel="canary")
            with self.assertRaisesRegex(ReleaseError, "canary release pointer"):
                manager.pin(require_fresh=False)

    def test_request_handle_remains_pinned_when_canary_pointer_changes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-channel-") as directory:
            store = LocalFilesystemObjectStore(directory)
            _put_release(store, "canary-one", b"one")
            _put_release(store, "canary-two", b"two")
            store.put_immutable(RELEASE_CHANNEL_POINTERS["canary"], _pointer("canary-one", "canary"))
            manager = ReleaseManager(object_store=store, channel="canary", clock=FrozenClock(datetime(2026, 9, 8, tzinfo=ARGENTINA_ZONE)))
            first = manager.pin(require_fresh=False)
            pointer = store.head(RELEASE_CHANNEL_POINTERS["canary"])
            store.compare_and_swap(RELEASE_CHANNEL_POINTERS["canary"], _pointer("canary-two", "canary"), expected_etag=pointer.etag)
            self.assertEqual(first.artifacts.read("micro-1024/test.bin"), b"one")
            self.assertEqual(manager.pin(require_fresh=False).release_id, "canary-two")


if __name__ == "__main__":
    unittest.main()

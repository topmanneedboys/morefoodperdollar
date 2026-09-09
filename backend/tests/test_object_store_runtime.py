from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

from backend.artifacts import ReleaseArtifactError
from backend.clock import ARGENTINA_ZONE, FrozenClock
from backend.object_store import LocalFilesystemObjectStore, ObjectStoreError, ReadOnlyObjectStore, S3CompatibleObjectStore
from backend.release import ReleaseManager


class _Body:
    def __init__(self, data: bytes):
        self.data = data

    def read(self) -> bytes:
        return self.data


class ReadOnlyS3:
    """A read-only fake intentionally without put/delete methods."""

    def __init__(self):
        self.items: dict[str, bytes] = {}

    def head_object(self, *, Bucket: str, Key: str):
        data = self.items[Key]
        digest = hashlib.sha256(data).hexdigest()
        return {"ContentLength": len(data), "ETag": digest, "Metadata": {"sha256": digest}}

    def get_object(self, *, Bucket: str, Key: str, Range: str | None = None):
        data = self.items[Key]
        if Range:
            left, right = Range.removeprefix("bytes=").split("-")
            data = data[int(left) : int(right) + 1]
        return {"Body": _Body(data)}


def _put_release(items: dict[str, bytes], release_id: str, marker: bytes) -> None:
    digest = hashlib.sha256(marker).hexdigest()
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "source": {"releaseDate": "2026-09-08"},
        "backendProfile": {"logicalPartitionCount": 1024, "physicalPackCount": 32},
        "objects": [{"path": "micro-1024/test.bin", "sha256": digest, "bytes": len(marker)}],
    }
    manifest_raw = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode()
    items[f"objects/sha256/{digest}"] = marker
    items[f"releases/{release_id}/manifest.json"] = manifest_raw
    items[f"releases/{release_id}/manifest.sha256"] = f"{hashlib.sha256(manifest_raw).hexdigest()}  manifest.json\n".encode("ascii")


def _pointer(release_id: str) -> bytes:
    return json.dumps({"releaseId": release_id, "schemaVersion": "valuepilot-active-release-v1"}, sort_keys=True, separators=(",", ":")).encode()


class ObjectStoreRuntimeTests(unittest.TestCase):
    def test_s3_runtime_reads_pin_one_generation_and_need_no_write_capability(self):
        client = ReadOnlyS3()
        _put_release(client.items, "r1", b"generation-one")
        client.items["control/active.json"] = _pointer("r1")
        store = S3CompatibleObjectStore(bucket="test", client=client)
        manager = ReleaseManager(object_store=store, clock=FrozenClock(datetime(2026, 9, 8, tzinfo=ARGENTINA_ZONE)))
        first = manager.pin()
        self.assertEqual(first.release_id, "r1")
        self.assertEqual(first.artifacts.read("micro-1024/test.bin"), b"generation-one")
        _put_release(client.items, "r2", b"generation-two")
        client.items["control/active.json"] = _pointer("r2")
        self.assertEqual(first.artifacts.read("micro-1024/test.bin"), b"generation-one")
        self.assertEqual(manager.pin().release_id, "r2")
        self.assertFalse(hasattr(client, "put_object"))
        with self.assertRaises(ObjectStoreError):
            ReadOnlyObjectStore(store).put_immutable("control/nope", b"x")

    def test_remote_missing_wrong_hash_and_truncated_objects_fail_closed(self):
        client = ReadOnlyS3()
        _put_release(client.items, "r1", b"verified")
        client.items["control/active.json"] = _pointer("r1")
        store = S3CompatibleObjectStore(bucket="test", client=client)
        manager = ReleaseManager(object_store=store, clock=FrozenClock(datetime(2026, 9, 8, tzinfo=ARGENTINA_ZONE)))
        handle = manager.pin()
        digest = hashlib.sha256(b"verified").hexdigest()
        del client.items[f"objects/sha256/{digest}"]
        with self.assertRaises(ReleaseArtifactError):
            handle.artifacts.read("micro-1024/test.bin")

        _put_release(client.items, "r1", b"verified")
        client.items[f"objects/sha256/{digest}"] = b"tampered"
        with self.assertRaises(ReleaseArtifactError):
            handle.artifacts.read("micro-1024/test.bin")

        client.items[f"objects/sha256/{digest}"] = b"ver"
        with self.assertRaises(ReleaseArtifactError):
            handle.artifacts.read_range("micro-1024/test.bin", 0, 3)

    def test_local_content_addressed_workspace_uses_same_manifest_view(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            store = LocalFilesystemObjectStore(root)
            marker = b"local-generation"
            digest = hashlib.sha256(marker).hexdigest()
            manifest = {
                "completionState": "COMPLETE",
                "releaseId": "r1",
                "source": {"releaseDate": "2026-09-08"},
                "backendProfile": {"logicalPartitionCount": 128, "physicalPackCount": 32},
                "objects": [{"path": "micro-1024/test.bin", "sha256": digest, "bytes": len(marker)}],
            }
            manifest_raw = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode()
            store.put_immutable(f"objects/sha256/{digest}", marker)
            store.put_immutable("releases/r1/manifest.json", manifest_raw)
            store.put_immutable("releases/r1/manifest.sha256", f"{hashlib.sha256(manifest_raw).hexdigest()}  manifest.json\n".encode())
            store.put_immutable("control/active.json", _pointer("r1"))
            manager = ReleaseManager(root, clock=FrozenClock(datetime(2026, 9, 8, tzinfo=ARGENTINA_ZONE)))
            handle = manager.pin()
            self.assertEqual(handle.release_id, "r1")
            self.assertEqual(handle.artifacts.read("micro-1024/test.bin"), marker)


if __name__ == "__main__":
    unittest.main()

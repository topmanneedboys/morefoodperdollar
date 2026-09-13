from __future__ import annotations

import hashlib
import tempfile
import threading
import time
import unittest
from pathlib import Path

from backend.artifacts import DEFAULT_FULL_OBJECT_CACHE_BYTES, ManifestReleaseArtifactStore, ReleaseArtifactError
from backend.object_store import GCSObjectStore, ObjectStoreError, ReadOnlyObjectStore
from backend.release import ReleaseManager


class _FakeGCSBody:
    def __init__(self, data: bytes):
        self.data = data

    def read(self, amount: int | None = None) -> bytes:
        if amount is None:
            value, self.data = self.data, b""
            return value
        value, self.data = self.data[:amount], self.data[amount:]
        return value

    def close(self) -> None:
        return None


class _FakeGCSBlob:
    def __init__(self, bucket: "_FakeGCSBucket", key: str):
        self.bucket = bucket
        self.key = key
        self.name = key
        self.size: int | None = None
        self.generation: int | None = None
        self.etag: str | None = None
        self.metadata: dict[str, str] | None = None

    def _entry(self) -> tuple[bytes, dict[str, str], int]:
        try:
            return self.bucket.items[self.key]
        except KeyError as exc:
            raise KeyError(self.key) from exc

    def reload(self) -> None:
        data, metadata, generation = self._entry()
        self.size = len(data)
        self.metadata = dict(metadata)
        self.generation = generation
        self.etag = f"etag-{generation}"

    def exists(self) -> bool:
        return self.key in self.bucket.items

    def download_as_bytes(self, *, start: int | None = None, end: int | None = None) -> bytes:
        data, _, _ = self._entry()
        if start is None:
            return data
        return data[start : (end + 1 if end is not None else None)]

    def open(self, mode: str, *, chunk_size: int):
        del mode, chunk_size
        return _FakeGCSBody(self.download_as_bytes())

    def upload_from_string(self, data: bytes, *, if_generation_match: int, **_: object) -> None:
        self._upload(data, if_generation_match)

    def upload_from_filename(self, filename: str, *, if_generation_match: int, **_: object) -> None:
        self._upload(Path(filename).read_bytes(), if_generation_match)

    def _upload(self, data: bytes, expected: int) -> None:
        current = self.bucket.items.get(self.key)
        current_generation = current[2] if current is not None else 0
        if expected == 0 and current is not None:
            raise RuntimeError("precondition failed")
        if expected != 0 and (current is None or current_generation != expected):
            raise RuntimeError("precondition failed")
        generation = current_generation + 1
        self.bucket.items[self.key] = (bytes(data), dict(self.metadata or {}), generation)


class _FakeGCSBucket:
    def __init__(self):
        self.items: dict[str, tuple[bytes, dict[str, str], int]] = {}

    def blob(self, key: str) -> _FakeGCSBlob:
        return _FakeGCSBlob(self, key)

    def list_blobs(self, *, prefix: str = ""):
        for key in sorted(self.items):
            if not key.startswith(prefix):
                continue
            blob = _FakeGCSBlob(self, key)
            blob.reload()
            yield blob


class _FakeGCSClient:
    def __init__(self):
        self.buckets: dict[str, _FakeGCSBucket] = {}

    def bucket(self, name: str) -> _FakeGCSBucket:
        return self.buckets.setdefault(name, _FakeGCSBucket())


def _pointer(release_id: str) -> bytes:
    return ('{"releaseId":"' + release_id + '","schemaVersion":"valuepilot-active-release-v1"}').encode()


def _release(client: _FakeGCSClient, release_id: str, payload: bytes = b"payload") -> dict[str, object]:
    digest = hashlib.sha256(payload).hexdigest()
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "source": {"releaseDate": "2026-09-08"},
        "backendProfile": {"logicalPartitionCount": 1024, "physicalPackCount": 32},
        "objects": [{"path": "micro-1024/test.bin", "sha256": digest, "bytes": len(payload)}],
    }
    store = GCSObjectStore(bucket="test", client=client)
    store.put_immutable(f"objects/sha256/{digest}", payload)
    raw = __import__("json").dumps(manifest, sort_keys=True, separators=(",", ":")).encode()
    store.put_immutable(f"releases/{release_id}/manifest.json", raw)
    store.put_immutable(
        f"releases/{release_id}/manifest.sha256",
        f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode(),
    )
    return manifest


class GCSRuntimeTests(unittest.TestCase):
    def test_gcs_adapter_reads_ranges_and_uses_generation_cas(self):
        client = _FakeGCSClient()
        store = GCSObjectStore(bucket="test", client=client)
        payload = b"0123456789"
        metadata = store.put_immutable("objects/payload", payload)
        self.assertEqual(store.get("objects/payload"), payload)
        self.assertEqual(store.get_range("objects/payload", 2, 4), b"2345")
        self.assertEqual(metadata.size, len(payload))
        self.assertEqual(metadata.sha256, hashlib.sha256(payload).hexdigest())
        updated = store.compare_and_swap("control/active.json", b"one", expected_etag=None)
        self.assertEqual(store.get("control/active.json"), b"one")
        store.compare_and_swap("control/active.json", b"two", expected_etag=updated.etag)
        with self.assertRaises(ObjectStoreError):
            store.compare_and_swap("control/active.json", b"three", expected_etag=updated.etag)

    def test_gcs_immutable_create_only_and_read_only_capability(self):
        client = _FakeGCSClient()
        store = GCSObjectStore(bucket="test", client=client)
        digest = hashlib.sha256(b"same").hexdigest()
        first = store.put_immutable("objects/same", b"same", sha256=digest)
        second = store.put_immutable("objects/same", b"same", sha256=digest)
        self.assertEqual(first.etag, second.etag)
        with self.assertRaises(ObjectStoreError):
            store.put_immutable("objects/same", b"different")
        read_only = ReadOnlyObjectStore(store)
        with self.assertRaises(ObjectStoreError):
            read_only.put_immutable("objects/new", b"no")

    def test_gcs_file_upload_uses_create_precondition_and_inventory_listing(self):
        client = _FakeGCSClient()
        store = GCSObjectStore(bucket="test", client=client)
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "payload.bin"
            source.write_bytes(b"file-payload")
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            result = store.put_immutable_file("objects/sha256/file", source, sha256=digest)
        self.assertEqual(result.sha256, digest)
        self.assertEqual(store.stats_snapshot()["existsRequests"], 0)
        inventory = store.inventory(("objects/sha256/",))
        self.assertEqual(inventory["objects/sha256/file"].sha256, digest)
        self.assertEqual(store.stats_snapshot()["listRequests"], 1)

    def test_gcs_manifest_verification_rejects_corrupt_and_truncated_objects(self):
        client = _FakeGCSClient()
        manifest = _release(client, "r1", b"verified")
        store = GCSObjectStore(bucket="test", client=client)
        artifacts = ManifestReleaseArtifactStore(store, manifest, release_id="r1")
        self.assertEqual(artifacts.read("micro-1024/test.bin"), b"verified")
        digest = hashlib.sha256(b"verified").hexdigest()
        client.bucket("test").items[f"objects/sha256/{digest}"] = (b"tampered", {}, 99)
        with self.assertRaises(ReleaseArtifactError):
            ManifestReleaseArtifactStore(store, manifest, release_id="r1").read("micro-1024/test.bin")
        client.bucket("test").items[f"objects/sha256/{digest}"] = (b"ver", {}, 100)
        with self.assertRaises(ReleaseArtifactError):
            ManifestReleaseArtifactStore(store, manifest, release_id="r1").read_range("micro-1024/test.bin", 0, 2)

    def test_release_manager_pointer_probe_reuses_handle_and_observes_rollback(self):
        client = _FakeGCSClient()
        store = GCSObjectStore(bucket="test", client=client)
        _release(client, "r1", b"one")
        store.put_immutable("control/active.json", _pointer("r1"))
        manager = ReleaseManager(object_store=ReadOnlyObjectStore(store))
        first = manager.pin(require_fresh=False)
        second = manager.pin(require_fresh=False)
        self.assertIs(first, second)
        _release(client, "r2", b"two")
        current = store.head("control/active.json")
        store.compare_and_swap("control/active.json", _pointer("r2"), expected_etag=current.etag)
        third = manager.pin(require_fresh=False)
        self.assertEqual(third.release_id, "r2")
        self.assertIsNot(third, first)
        self.assertEqual(first.artifacts.read("micro-1024/test.bin"), b"one")

    def test_full_object_cache_single_flight_and_bound(self):
        payload = b"x" * 128
        digest = hashlib.sha256(payload).hexdigest()

        class CountingStore:
            def __init__(self):
                self.calls = 0

            def stream(self, key: str, *, chunk_size: int):
                del key, chunk_size
                self.calls += 1
                time.sleep(0.02)
                yield payload

            def head(self, key: str):
                del key
                from backend.object_store import ObjectMetadata

                return ObjectMetadata("objects/sha256/" + digest, len(payload), "etag", digest, {"sha256": digest})

            def get(self, key: str):
                del key
                return payload

            def get_range(self, key: str, offset: int, length: int):
                del key
                return payload[offset : offset + length]

        manifest = {"objects": [{"path": "micro-1024/test.bin", "sha256": digest, "bytes": len(payload)}]}
        store = CountingStore()
        artifacts = ManifestReleaseArtifactStore(store, manifest, release_id="r1", full_object_cache_bytes=256)
        values: list[bytes] = []
        threads = [threading.Thread(target=lambda: values.append(artifacts.read_range("micro-1024/test.bin", 5, 10))) for _ in range(4)]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        self.assertEqual(values, [payload[5:15]] * 4)
        self.assertEqual(store.calls, 1)
        self.assertEqual(artifacts.cache_stats()["maxBytes"], 256)
        self.assertLessEqual(artifacts.cache_stats()["bytes"], 256)
        self.assertEqual(DEFAULT_FULL_OBJECT_CACHE_BYTES, 512 * 1024 * 1024)

    def test_read_only_gcs_wrapper_enables_bounded_runtime_cache(self):
        client = _FakeGCSClient()
        manifest = _release(client, "r-cache", b"cached")
        store = ReadOnlyObjectStore(GCSObjectStore(bucket="test", client=client))
        artifacts = ManifestReleaseArtifactStore(store, manifest, release_id="r-cache")
        self.assertEqual(artifacts.cache_stats()["maxBytes"], DEFAULT_FULL_OBJECT_CACHE_BYTES)

    def test_full_object_cache_skips_objects_over_bound_without_unverified_data(self):
        payload = b"large" * 10
        digest = hashlib.sha256(payload).hexdigest()
        manifest = {"objects": [{"path": "micro-1024/test.bin", "sha256": digest, "bytes": len(payload)}]}

        class Store:
            def stream(self, key: str, *, chunk_size: int):
                del key, chunk_size
                yield payload

            def head(self, key: str):
                from backend.object_store import ObjectMetadata

                return ObjectMetadata(key, len(payload), "etag", digest, {"sha256": digest})

            def get_range(self, key: str, offset: int, length: int):
                del key
                return payload[offset : offset + length]

        artifacts = ManifestReleaseArtifactStore(Store(), manifest, release_id="r1", full_object_cache_bytes=4)
        self.assertEqual(artifacts.read_range("micro-1024/test.bin", 1, 3), payload[1:4])
        self.assertEqual(artifacts.cache_stats()["bytes"], 0)


if __name__ == "__main__":
    unittest.main()

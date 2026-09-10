from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from unittest.mock import patch

from backend.artifacts import ManifestReleaseArtifactStore, ReleaseArtifactError
from backend.clock import ARGENTINA_ZONE, FrozenClock
from backend.object_store import (
    LocalFilesystemObjectStore,
    ObjectStoreError,
    ReadOnlyObjectStore,
    S3CompatibleObjectStore,
    S3_CLIENT_TOTAL_MAX_ATTEMPTS,
    S3_UPLOAD_MAX_ATTEMPTS,
    S3_UPLOAD_MAX_CONCURRENCY,
    S3_UPLOAD_MULTIPART_CHUNKSIZE_BYTES,
    S3_UPLOAD_MULTIPART_THRESHOLD_BYTES,
)
from backend.release import ReleaseManager


class _Body:
    def __init__(self, data: bytes):
        self.data = data

    def read(self, amount: int | None = None) -> bytes:
        if amount is None:
            return self.data
        value, self.data = self.data[:amount], self.data[amount:]
        return value


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


class UploadS3:
    def __init__(self, *, fail_uploads: int = 0, commit_before_failure: bool = False):
        self.items: dict[str, bytes] = {}
        self.metadata: dict[str, dict[str, str]] = {}
        self.upload_calls: list[dict[str, object]] = []
        self.fail_uploads = fail_uploads
        self.commit_before_failure = commit_before_failure

    def head_object(self, *, Bucket: str, Key: str):
        if Key not in self.items:
            raise KeyError(Key)
        data = self.items[Key]
        digest = hashlib.sha256(data).hexdigest()
        return {"ContentLength": len(data), "ETag": digest, "Metadata": self.metadata.get(Key, {})}

    def get_object(self, *, Bucket: str, Key: str, Range: str | None = None):
        data = self.items[Key]
        if Range:
            left, right = Range.removeprefix("bytes=").split("-")
            data = data[int(left) : int(right) + 1]
        return {"Body": _Body(data)}

    def upload_file(self, Filename: str, Bucket: str, Key: str, *, ExtraArgs, Config):
        self.upload_calls.append({"Filename": Filename, "Key": Key, "ExtraArgs": ExtraArgs, "Config": Config})
        data = Path(Filename).read_bytes()
        if self.fail_uploads:
            self.fail_uploads -= 1
            if self.commit_before_failure:
                self.items[Key] = data
                self.metadata[Key] = dict(ExtraArgs["Metadata"])
            raise ConnectionResetError(10054, "connection reset")
        self.items[Key] = data
        self.metadata[Key] = dict(ExtraArgs["Metadata"])


class InterruptingObjectStore(LocalFilesystemObjectStore):
    def stream(self, key: str, *, chunk_size: int = 1024 * 1024):
        yield b"partial"
        raise ObjectStoreError("injected stream interruption")


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
    def test_s3_client_uses_bounded_standard_retries(self):
        with patch("boto3.client") as client_factory:
            S3CompatibleObjectStore(bucket="test", endpoint_url="https://example.invalid", region_name="auto")
        config = client_factory.call_args.kwargs["config"]
        self.assertEqual(config.retries, {"mode": "standard", "total_max_attempts": S3_CLIENT_TOTAL_MAX_ATTEMPTS})
        self.assertEqual(config.max_pool_connections, S3_UPLOAD_MAX_CONCURRENCY)

    def test_s3_file_upload_uses_managed_transfer_and_preserves_sha_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "payload.bin"
            source.write_bytes(b"managed-transfer")
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            client = UploadS3()
            store = S3CompatibleObjectStore(bucket="test", client=client)
            result = store.put_immutable_file("objects/payload", source, sha256=digest)
            self.assertEqual(result.sha256, digest)
            self.assertEqual(client.metadata["objects/payload"], {"sha256": digest})
            call = client.upload_calls[0]
            config = call["Config"]
            self.assertEqual(config.multipart_threshold, S3_UPLOAD_MULTIPART_THRESHOLD_BYTES)
            self.assertEqual(config.multipart_chunksize, S3_UPLOAD_MULTIPART_CHUNKSIZE_BYTES)
            self.assertEqual(config.max_concurrency, S3_UPLOAD_MAX_CONCURRENCY)

    def test_s3_file_upload_retries_transient_reset_and_is_resumable(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "payload.bin"
            source.write_bytes(b"retryable-transfer")
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            client = UploadS3(fail_uploads=1)
            store = S3CompatibleObjectStore(bucket="test", client=client)
            with patch("backend.object_store.time.sleep") as sleep:
                result = store.put_immutable_file("objects/payload", source, sha256=digest)
            self.assertEqual(result.sha256, digest)
            self.assertEqual(len(client.upload_calls), 2)
            sleep.assert_called_once()

    def test_s3_file_upload_verifies_committed_object_after_ambiguous_reset(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "payload.bin"
            source.write_bytes(b"committed-before-reset")
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            client = UploadS3(fail_uploads=1, commit_before_failure=True)
            store = S3CompatibleObjectStore(bucket="test", client=client)
            with patch("backend.object_store.time.sleep") as sleep:
                result = store.put_immutable_file("objects/payload", source, sha256=digest)
            self.assertEqual(result.sha256, digest)
            self.assertEqual(len(client.upload_calls), 1)
            sleep.assert_not_called()

    def test_s3_existing_verified_object_is_skipped_and_mismatch_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "payload.bin"
            source.write_bytes(b"expected")
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            client = UploadS3()
            client.items["objects/payload"] = source.read_bytes()
            client.metadata["objects/payload"] = {"sha256": digest}
            store = S3CompatibleObjectStore(bucket="test", client=client)
            store.put_immutable_file("objects/payload", source, sha256=digest)
            self.assertEqual(client.upload_calls, [])

            client.items["objects/payload"] = b"different"
            with self.assertRaises(ObjectStoreError):
                store.put_immutable_file("objects/payload", source, sha256=digest)
            self.assertEqual(client.upload_calls, [])

    def test_partial_publication_never_advances_pointer_and_resumes(self):
        # The release publisher test exercises pointer ordering.  This runtime
        # fixture specifically proves the object-store half: an interrupted
        # upload leaves no committed object, then a later call safely resumes.
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "payload.bin"
            source.write_bytes(b"resume-me")
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            client = UploadS3(fail_uploads=S3_UPLOAD_MAX_ATTEMPTS)
            store = S3CompatibleObjectStore(bucket="test", client=client)
            with patch("backend.object_store.time.sleep"):
                with self.assertRaises(ObjectStoreError):
                    store.put_immutable_file("objects/payload", source, sha256=digest)
            self.assertNotIn("objects/payload", client.items)
            client.fail_uploads = 0
            result = store.put_immutable_file("objects/payload", source, sha256=digest)
            self.assertEqual(result.sha256, digest)
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

    def test_stream_materialization_is_bounded_atomic_and_cleans_failed_temporary_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            object_root = root / "objects"
            store = LocalFilesystemObjectStore(object_root)
            expected = b"verified-stream-payload"
            digest = hashlib.sha256(expected).hexdigest()
            manifest = {
                "objects": [{"path": "micro-1024/payload.bin", "sha256": digest, "bytes": len(expected)}]
            }
            store.put_immutable(f"objects/sha256/{digest}", expected)
            cache = root / "cache"
            artifacts = ManifestReleaseArtifactStore(store, manifest, release_id="r1", cache_root=cache)
            target = artifacts.materialize("micro-1024/payload.bin")
            self.assertEqual(target.read_bytes(), expected)
            self.assertFalse(list(cache.rglob(".*.partial-*")))

            # Extra bytes, a wrong digest, and a transport interruption never
            # replace the verified target or leave an authoritative partial.
            bad_digest = hashlib.sha256(b"bad").hexdigest()
            bad_manifest = {"objects": [{"path": "micro-1024/bad.bin", "sha256": bad_digest, "bytes": 2}]}
            store.put_immutable(f"objects/sha256/{bad_digest}", b"too-long")
            bad_artifacts = ManifestReleaseArtifactStore(store, bad_manifest, release_id="r2", cache_root=cache)
            with self.assertRaises(ReleaseArtifactError):
                bad_artifacts.materialize("micro-1024/bad.bin")
            self.assertFalse((cache / "micro-1024" / "bad.bin").exists())
            self.assertFalse(list((cache / "micro-1024").glob(".bad.bin.*")))

            interrupted_store = InterruptingObjectStore(object_root)
            interrupted_cache = root / "interrupt-cache"
            interrupted_artifacts = ManifestReleaseArtifactStore(interrupted_store, manifest, release_id="r3", cache_root=interrupted_cache)
            with self.assertRaises(ReleaseArtifactError):
                interrupted_artifacts.materialize("micro-1024/payload.bin")
            self.assertEqual(target.read_bytes(), expected)
            self.assertFalse((interrupted_cache / "micro-1024" / "payload.bin").exists())
            self.assertFalse(list((interrupted_cache / "micro-1024").glob(".payload.bin.*")))

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

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore, ObjectStoreError
from tools.argentina_object_store_publisher import ObjectStorePublicationError, ObjectStoreReleasePublisher


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


class RecordingStore:
    def __init__(self, root: Path):
        self.delegate = LocalFilesystemObjectStore(root)
        self.calls: list[str] = []

    def head(self, key):
        self.calls.append(f"head:{key}")
        return self.delegate.head(key)

    def get(self, key):
        self.calls.append(f"get:{key}")
        return self.delegate.get(key)

    def get_range(self, key, offset, length):
        self.calls.append(f"range:{key}")
        return self.delegate.get_range(key, offset, length)

    def exists(self, key):
        self.calls.append(f"exists:{key}")
        return self.delegate.exists(key)

    def put_immutable(self, key, data, *, sha256=None):
        self.calls.append(f"put:{key}")
        return self.delegate.put_immutable(key, data, sha256=sha256)

    def put_immutable_file(self, key, source, *, sha256=None):
        self.calls.append(f"put_file:{key}")
        return self.delegate.put_immutable_file(key, source, sha256=sha256)

    def compare_and_swap(self, key, data, *, expected_etag=None):
        self.calls.append(f"cas:{key}")
        return self.delegate.compare_and_swap(key, data, expected_etag=expected_etag)


class FailOnceStore(RecordingStore):
    def __init__(self, root: Path, fail_key: str):
        super().__init__(root)
        self.fail_key = fail_key
        self.failed = False

    def put_immutable_file(self, key, source, *, sha256=None):
        if key == self.fail_key and not self.failed:
            self.failed = True
            self.calls.append(f"put_file_failed:{key}")
            raise ObjectStoreError("simulated interrupted publication")
        return super().put_immutable_file(key, source, sha256=sha256)


class CasFailStore(RecordingStore):
    def compare_and_swap(self, key, data, *, expected_etag=None):
        self.calls.append(f"cas_failed:{key}")
        raise ObjectStoreError("simulated CAS failure")


def _write_manifest(root: Path, release_id: str, descriptors: list[dict[str, object]], *, completion_state: str = "COMPLETE") -> None:
    release_dir = root / "releases" / release_id
    release_dir.mkdir(parents=True, exist_ok=True)
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": completion_state,
        "releaseId": release_id,
        "source": {"releaseDate": "2026-09-08"},
        "objects": descriptors,
    }
    raw = _canonical(manifest)
    (release_dir / "manifest.json").write_bytes(raw)
    (release_dir / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))


def _add_object(root: Path, payload: bytes) -> tuple[str, dict[str, object]]:
    digest = hashlib.sha256(payload).hexdigest()
    target = root / "objects" / "sha256" / digest
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(payload)
    return digest, {"path": f"fixture/{digest[:8]}.bin", "sha256": digest, "bytes": len(payload)}


def _workspace(root: Path) -> tuple[str, str]:
    release_id = "argentina-sepa-2026-09-08-test"
    digest, descriptor = _add_object(root, b"qualified-object")
    _write_manifest(root, release_id, [descriptor])
    (root / "control").mkdir(parents=True, exist_ok=True)
    (root / "control" / "rollback-test.json").write_text('{"schemaVersion":"test"}', encoding="utf-8")
    return release_id, digest


def _two_release_workspace(root: Path) -> tuple[str, str, str]:
    shared_digest, shared = _add_object(root, b"shared-qualified-object")
    _tuesday_digest, tuesday = _add_object(root, b"tuesday-qualified-object")
    _sunday_digest, sunday = _add_object(root, b"sunday-qualified-object")
    tuesday_id = "argentina-sepa-tuesday"
    sunday_id = "argentina-sepa-sunday"
    _write_manifest(root, tuesday_id, [shared, tuesday])
    _write_manifest(root, sunday_id, [shared, sunday])
    (root / "control").mkdir(parents=True, exist_ok=True)
    (root / "control" / "release-note.json").write_bytes(b'{"schemaVersion":"test"}')
    return tuesday_id, sunday_id, shared_digest


def _active_bytes(release_id: str, previous: str | None = None) -> bytes:
    return _canonical(
        {
            "lastKnownGoodReleaseId": release_id,
            "previousReleaseId": previous,
            "releaseId": release_id,
            "schemaVersion": "valuepilot-active-release-v1",
        }
    )


def _store_path(store: RecordingStore, key: str) -> Path:
    return store.delegate.root.joinpath(*key.split("/"))


class ObjectStorePublisherTests(unittest.TestCase):
    def test_cli_plan_needs_no_publisher_configuration(self):
        with tempfile.TemporaryDirectory() as workspace_dir:
            workspace = Path(workspace_dir)
            release_id, _ = _workspace(workspace)
            environment = os.environ.copy()
            for name in ("VALUEPILOT_PUBLISH_BUCKET", "VALUEPILOT_RELEASE_BUCKET", "VALUEPILOT_PUBLISH_ENDPOINT_URL", "VALUEPILOT_PUBLISH_REGION", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN"):
                environment.pop(name, None)
            result = subprocess.run(
                [sys.executable, "-m", "tools.argentina_object_store_publisher", "plan", str(workspace), "--release-id", release_id, "--active-release-id", release_id],
                check=True,
                capture_output=True,
                text=True,
                env=environment,
            )
            output = json.loads(result.stdout)
            self.assertEqual(output["operation"], "PLAN")
            self.assertEqual(output["activeReleaseId"], release_id)
            self.assertEqual(output["remoteWriteCount"], 0)
            self.assertEqual(output["deleteCount"], 0)

    def test_plan_is_write_free_and_reports_remote_reuse(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            tuesday_id, sunday_id, shared_digest = _two_release_workspace(workspace)
            store = RecordingStore(Path(object_dir))
            store.delegate.put_immutable(f"objects/sha256/{shared_digest}", b"shared-qualified-object")
            publisher = ObjectStoreReleasePublisher(store)
            result = publisher.plan(workspace, release_ids=[tuesday_id, sunday_id], active_release_id=sunday_id)
            self.assertEqual(result["operation"], "PLAN")
            self.assertEqual(result["objectCount"], 3)
            self.assertEqual(result["manifestCount"], 2)
            self.assertEqual(result["reusedImmutableObjectCount"], 1)
            self.assertEqual(result["newImmutableObjectCount"], 2)
            self.assertTrue(result["remoteInventoryKnown"])
            self.assertEqual(result["deleteCount"], 0)
            self.assertFalse(any(call.startswith(("put:", "put_file:", "cas:")) for call in store.calls))

    def test_dry_run_is_write_free_and_apply_is_pointer_last(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            release_id, digest = _workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            with self.assertWarns(DeprecationWarning):
                dry = publisher.publish(workspace, release_ids=[release_id], active_release_id=release_id)
            self.assertFalse(dry["applied"])
            self.assertEqual(store.calls, [])

            with self.assertWarns(DeprecationWarning):
                result = publisher.publish(workspace, release_ids=[release_id], active_release_id=release_id, apply=True)
            self.assertTrue(result["applied"])
            self.assertIn(f"put_file:objects/sha256/{digest}", store.calls)
            self.assertEqual(store.delegate.get("control/active.json"), _active_bytes(release_id))
            self.assertTrue(store.calls.index("cas:control/active.json") > store.calls.index("put:releases/" + release_id + "/manifest.json"))
            self.assertTrue(store.calls.index("cas:control/active.json") > store.calls.index("put:control/rollback-test.json"))
            self.assertEqual(result["deleteCount"], 0)

    def test_stage_never_writes_or_changes_preexisting_active(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            tuesday_id, sunday_id, _ = _two_release_workspace(workspace)
            store = RecordingStore(Path(object_dir))
            store.delegate.put_immutable("control/active.json", _active_bytes(tuesday_id))
            before = store.delegate.get("control/active.json")
            publisher = ObjectStoreReleasePublisher(store)
            result = publisher.stage(workspace, release_ids=[tuesday_id, sunday_id], active_release_id=sunday_id)
            self.assertEqual(result["status"], "STAGED")
            self.assertFalse(result["activePointerChanged"])
            self.assertEqual(result["activeWriteCount"], 0)
            self.assertEqual(store.delegate.get("control/active.json"), before)
            self.assertFalse(any(call.endswith("control/active.json") and call.startswith("cas:") for call in store.calls))
            self.assertFalse(any(call.endswith("control/active.json") and call.startswith("put") for call in store.calls))
            self.assertEqual(result["deleteCount"], 0)

    def test_stage_is_resumable_and_idempotent(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            tuesday_id, sunday_id, _ = _two_release_workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            first = publisher.stage(workspace, release_ids=[tuesday_id, sunday_id])
            store.calls.clear()
            second = publisher.stage(workspace, release_ids=[tuesday_id, sunday_id])
            self.assertEqual(first["status"], second["status"])
            self.assertEqual(second["activeWriteCount"], 0)
            self.assertFalse(any(call.startswith("cas:") for call in store.calls))
            self.assertTrue(store.delegate.exists(f"releases/{sunday_id}/manifest.json"))
            self.assertEqual(second["deleteCount"], 0)

    def test_immutable_conflict_fails_closed_before_stage_writes(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            release_id, digest = _workspace(workspace)
            store = RecordingStore(Path(object_dir))
            store.delegate.put_immutable(f"objects/sha256/{digest}", b"wrong-bytes")
            with self.assertRaises(ObjectStorePublicationError):
                ObjectStoreReleasePublisher(store).stage(workspace, release_ids=[release_id])
            self.assertFalse(store.delegate.exists(f"releases/{release_id}/manifest.json"))
            self.assertFalse(any(call.startswith(("put:", "put_file:", "cas:")) for call in store.calls))

    def test_verify_is_read_only_and_checks_a_staged_release(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            release_id, _ = _workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            publisher.stage(workspace, release_ids=[release_id])
            store.calls.clear()
            result = publisher.verify(release_id)
            self.assertEqual(result["status"], "PASS")
            self.assertFalse(any(call.startswith(("put:", "put_file:", "cas:")) for call in store.calls))
            self.assertEqual(result["deleteCount"], 0)

    def test_verify_detects_missing_object_wrong_bytes_and_wrong_manifest_checksum(self):
        for mutation in ("missing", "wrong-bytes", "wrong-checksum"):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
                workspace = Path(workspace_dir)
                release_id, digest = _workspace(workspace)
                store = RecordingStore(Path(object_dir))
                publisher = ObjectStoreReleasePublisher(store)
                publisher.stage(workspace, release_ids=[release_id])
                if mutation == "missing":
                    _store_path(store, f"objects/sha256/{digest}").unlink()
                elif mutation == "wrong-bytes":
                    _store_path(store, f"objects/sha256/{digest}").write_bytes(b"wrong")
                else:
                    _store_path(store, f"releases/{release_id}/manifest.sha256").write_bytes(b"0" * 64 + b"  manifest.json\n")
                store.calls.clear()
                with self.assertRaises(ObjectStorePublicationError):
                    publisher.verify(release_id)
                self.assertFalse(any(call.startswith(("put:", "put_file:", "cas:")) for call in store.calls))

    def test_verify_rejects_incomplete_release(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            release_id, _ = _workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            publisher.stage(workspace, release_ids=[release_id])
            manifest_path = _store_path(store, f"releases/{release_id}/manifest.json")
            manifest = json.loads(manifest_path.read_bytes())
            manifest["completionState"] = "IN_PROGRESS"
            raw = _canonical(manifest)
            manifest_path.write_bytes(raw)
            _store_path(store, f"releases/{release_id}/manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
            with self.assertRaises(ObjectStorePublicationError):
                publisher.verify(release_id)

    def test_activate_verifies_target_first_and_writes_only_active_via_cas(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            tuesday_id, sunday_id, _ = _two_release_workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            publisher.stage(workspace, release_ids=[tuesday_id, sunday_id])
            store.calls.clear()
            result = publisher.activate(sunday_id)
            self.assertEqual(result["status"], "ACTIVE")
            self.assertEqual(result["previousReleaseId"], None)
            self.assertEqual(result["activeWriteCount"], 1)
            self.assertEqual([call for call in store.calls if call.startswith("cas:")], ["cas:control/active.json"])
            self.assertFalse(any(call.startswith(("put:", "put_file:")) for call in store.calls))
            self.assertEqual(json.loads(store.delegate.get("control/active.json"))["releaseId"], sunday_id)

    def test_activate_rejects_stale_expected_active_without_writing(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            tuesday_id, sunday_id, _ = _two_release_workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            publisher.stage(workspace, release_ids=[tuesday_id, sunday_id])
            publisher.activate(tuesday_id)
            before = store.delegate.get("control/active.json")
            store.calls.clear()
            with self.assertRaises(ObjectStorePublicationError):
                publisher.activate(sunday_id, expected_current_release_id="some-other-release")
            self.assertEqual(store.delegate.get("control/active.json"), before)
            self.assertFalse(any(call.startswith("cas:") for call in store.calls))

    def test_failed_activation_leaves_old_active_intact(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            tuesday_id, sunday_id, _ = _two_release_workspace(workspace)
            store = CasFailStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            publisher.stage(workspace, release_ids=[tuesday_id, sunday_id])
            store.delegate.put_immutable("control/active.json", _active_bytes(tuesday_id))
            before = store.delegate.get("control/active.json")
            with self.assertRaises(ObjectStorePublicationError):
                publisher.activate(sunday_id, expected_current_release_id=tuesday_id)
            self.assertEqual(store.delegate.get("control/active.json"), before)

    def test_rollback_tuesday_sunday_tuesday_uses_pointer_writes_only(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            tuesday_id, sunday_id, _ = _two_release_workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            publisher.stage(workspace, release_ids=[tuesday_id, sunday_id])
            store.calls.clear()
            publisher.activate(tuesday_id)
            store.calls.clear()
            sunday_result = publisher.activate(sunday_id, expected_current_release_id=tuesday_id)
            sunday_calls = list(store.calls)
            store.calls.clear()
            tuesday_result = publisher.activate(tuesday_id, expected_current_release_id=sunday_id)
            tuesday_calls = list(store.calls)
            self.assertEqual(sunday_result["previousReleaseId"], tuesday_id)
            self.assertEqual(tuesday_result["previousReleaseId"], sunday_id)
            for calls in (sunday_calls, tuesday_calls):
                self.assertEqual([call for call in calls if call.startswith("cas:")], ["cas:control/active.json"])
                self.assertFalse(any(call.startswith(("put:", "put_file:")) for call in calls))
            self.assertEqual(json.loads(store.delegate.get("control/active.json"))["releaseId"], tuesday_id)
            self.assertEqual(sunday_result["deleteCount"], 0)
            self.assertEqual(tuesday_result["deleteCount"], 0)

    def test_interrupted_publication_does_not_advance_pointer_and_resumes(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            release_id, first_digest = _workspace(workspace)
            second_digest, second_descriptor = _add_object(workspace, b"second-qualified-object")
            manifest_path = workspace / "releases" / release_id / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["objects"].append(second_descriptor)
            raw = _canonical(manifest)
            manifest_path.write_bytes(raw)
            (manifest_path.parent / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))

            fail_key = f"objects/sha256/{second_digest}"
            store = FailOnceStore(Path(object_dir), fail_key)
            publisher = ObjectStoreReleasePublisher(store)
            with self.assertWarns(DeprecationWarning):
                with self.assertRaises(ObjectStorePublicationError):
                    publisher.publish(workspace, release_ids=[release_id], active_release_id=release_id, apply=True)
            self.assertTrue(store.delegate.exists(f"objects/sha256/{first_digest}"))
            self.assertFalse(store.delegate.exists(fail_key))
            self.assertFalse(store.delegate.exists("control/active.json"))
            self.assertFalse(store.delegate.exists(f"releases/{release_id}/manifest.json"))

            with self.assertWarns(DeprecationWarning):
                result = publisher.publish(workspace, release_ids=[release_id], active_release_id=release_id, apply=True)
            self.assertTrue(result["applied"])
            self.assertTrue(store.delegate.exists(fail_key))
            self.assertEqual(json.loads(store.delegate.get("control/active.json"))["releaseId"], release_id)


if __name__ == "__main__":
    unittest.main()

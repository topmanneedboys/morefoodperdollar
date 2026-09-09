from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_object_store_publisher import ObjectStoreReleasePublisher


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


def _workspace(root: Path) -> tuple[str, str]:
    release_id = "argentina-sepa-2026-09-08-test"
    (root / "objects" / "sha256").mkdir(parents=True)
    (root / "releases" / release_id).mkdir(parents=True)
    (root / "control").mkdir(parents=True)
    payload = b"qualified-object"
    digest = hashlib.sha256(payload).hexdigest()
    (root / "objects" / "sha256" / digest).write_bytes(payload)
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "source": {"releaseDate": "2026-09-08"},
        "objects": [{"path": "backend-profile.json", "sha256": digest, "bytes": len(payload)}],
    }
    raw = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode()
    manifest_path = root / "releases" / release_id / "manifest.json"
    manifest_path.write_bytes(raw)
    (manifest_path.parent / "manifest.sha256").write_text(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n", encoding="ascii")
    (root / "control" / "rollback-test.json").write_text('{"schemaVersion":"test"}', encoding="utf-8")
    return release_id, digest


class ObjectStorePublisherTests(unittest.TestCase):
    def test_cli_dry_run_needs_no_publisher_configuration(self):
        with tempfile.TemporaryDirectory() as workspace_dir:
            workspace = Path(workspace_dir)
            release_id, _ = _workspace(workspace)
            environment = os.environ.copy()
            for name in ("VALUEPILOT_PUBLISH_BUCKET", "VALUEPILOT_PUBLISH_ENDPOINT_URL", "VALUEPILOT_PUBLISH_REGION", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN"):
                environment.pop(name, None)
            result = subprocess.run(
                [sys.executable, "-m", "tools.argentina_object_store_publisher", str(workspace), "--release-id", release_id, "--active-release-id", release_id],
                check=True,
                capture_output=True,
                text=True,
                env=environment,
            )
            output = json.loads(result.stdout)
            self.assertFalse(output["applied"])
            self.assertEqual(output["remoteWriteCount"], 0)

    def test_dry_run_is_write_free_and_apply_is_pointer_last(self):
        with tempfile.TemporaryDirectory() as workspace_dir, tempfile.TemporaryDirectory() as object_dir:
            workspace = Path(workspace_dir)
            release_id, digest = _workspace(workspace)
            store = RecordingStore(Path(object_dir))
            publisher = ObjectStoreReleasePublisher(store)
            dry = publisher.publish(workspace, release_ids=[release_id], active_release_id=release_id)
            self.assertFalse(dry["applied"])
            self.assertEqual(store.calls, [])

            result = publisher.publish(workspace, release_ids=[release_id], active_release_id=release_id, apply=True)
            self.assertTrue(result["applied"])
            self.assertIn(f"put_file:objects/sha256/{digest}", store.calls)
            self.assertEqual(store.delegate.get("control/active.json"), json.dumps({"lastKnownGoodReleaseId": release_id, "previousReleaseId": None, "releaseId": release_id, "schemaVersion": "valuepilot-active-release-v1"}, sort_keys=True, separators=(",", ":")).encode())
            self.assertTrue(store.calls.index("cas:control/active.json") > store.calls.index("put:releases/" + release_id + "/manifest.json"))
            self.assertTrue(store.calls.index("cas:control/active.json") > store.calls.index("put:control/rollback-test.json"))


if __name__ == "__main__":
    unittest.main()

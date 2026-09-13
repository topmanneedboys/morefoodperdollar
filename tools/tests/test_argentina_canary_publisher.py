from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_object_store_publisher import ObjectStoreReleasePublisher


class ArgentinaCanaryPublisherTests(unittest.TestCase):
    def test_canary_activation_uses_fixed_pointer_and_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-canary-publisher-") as directory:
            root = Path(directory) / "workspace"
            release_id = "argentina-sepa-2026-09-09-canary"
            payload = b"candidate"
            digest = hashlib.sha256(payload).hexdigest()
            object_path = root / "objects" / "sha256" / digest
            object_path.parent.mkdir(parents=True)
            object_path.write_bytes(payload)
            release = root / "releases" / release_id
            release.mkdir(parents=True)
            manifest = {
                "schemaVersion": "valuepilot-argentina-daily-release-v1",
                "completionState": "COMPLETE",
                "releaseId": release_id,
                "source": {"releaseDate": "2026-09-09"},
                "objects": [{"path": "candidate.bin", "sha256": digest, "bytes": len(payload)}],
            }
            raw = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8")
            (release / "manifest.json").write_bytes(raw)
            (release / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
            store = LocalFilesystemObjectStore(Path(directory) / "store")
            publisher = ObjectStoreReleasePublisher(store)
            publisher.stage(root, release_ids=[release_id])
            first = publisher.activate_canary(release_id)
            self.assertEqual(first["status"], "CANARY")
            self.assertEqual(first["pointerKey"], "control/canary.json")
            self.assertFalse(store.exists("control/active.json"))
            self.assertEqual(json.loads(store.get("control/canary.json"))["releaseId"], release_id)
            second = publisher.activate_canary(release_id)
            self.assertEqual(second["status"], "ALREADY_CANARY")
            self.assertFalse(second["activePointerChanged"])


if __name__ == "__main__":
    unittest.main()

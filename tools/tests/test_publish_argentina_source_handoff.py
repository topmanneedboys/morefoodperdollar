from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_release_acquisition_handoff import SCHEMA_VERSION as HANDOFF_SCHEMA_VERSION
from tools.publish_argentina_source_handoff import SourceHandoffPublishError, publish_verified_handoff


class ArgentinaSourceHandoffPublisherTests(unittest.TestCase):
    def _fixture(self, root: Path, *, source: bytes = b"official-sepa-zip"):
        digest = hashlib.sha256(source).hexdigest()
        key = f"source-archive/sha256/{digest}.zip"
        manifest_key = f"source-archive/manifests/{digest}.json"
        local = LocalFilesystemObjectStore(root / "local")
        destination = LocalFilesystemObjectStore(root / "destination")
        local.put_immutable(key, source, sha256=digest)
        manifest = {
            "schemaVersion": "valuepilot-argentina-acquisition-v1",
            "policyVersion": "valuepilot-argentina-acquisition-policy-v1",
            "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
            "datasetId": "0f495d54-4a5d-5fa3-a323-f6e97ce434cf",
            "resourceId": "fixture-resource",
            "resourceName": "Domingo",
            "officialUrl": "https://datos.produccion.gob.ar/fixture.zip",
            "sourceSha256": digest,
            "bytes": len(source),
            "releaseDate": "2026-09-13",
            "archiveKey": key,
            "rawProviderDataCommitted": False,
            "runtimePublicationAllowed": False,
        }
        local.put_immutable(manifest_key, json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode() + b"\n")
        report = {
            "schemaVersion": HANDOFF_SCHEMA_VERSION,
            "status": "HANDOFF_READY",
            "productionStateChanged": False,
            "activePointerWrites": 0,
            "buildStarted": False,
            "archive": {
                "key": key,
                "manifestKey": manifest_key,
                "sha256": digest,
                "bytes": len(source),
                "reused": False,
            },
            "handoff": {
                "sourceArchiveKey": key,
                "sourceArchiveSha256": digest,
                "releaseDate": "2026-09-13",
                "observedAt": "2026-09-14T02:30:00Z",
                "resourceId": "fixture-resource",
                "resourceName": "Domingo",
            },
        }
        return local, destination, report, source, key, manifest_key, digest

    def test_publishes_exact_content_addressed_zip_and_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            local, destination, report, source, key, manifest_key, digest = self._fixture(Path(temporary))
            value = publish_verified_handoff(report, local_store=local, destination_store=destination)
            self.assertEqual(value["status"], "PUBLISHED")
            self.assertEqual(value["sourceArchiveKey"], key)
            self.assertEqual(value["sourceArchiveSha256"], digest)
            self.assertEqual(destination.get(key), source)
            self.assertEqual(destination.get(manifest_key), local.get(manifest_key))
            self.assertEqual(value["activePointerWrites"], 0)
            self.assertFalse(value["productionStateChanged"])

    def test_repeated_publish_reuses_identical_immutable_objects(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            local, destination, report, _, key, _, digest = self._fixture(Path(temporary))
            first = publish_verified_handoff(report, local_store=local, destination_store=destination)
            second = publish_verified_handoff(report, local_store=local, destination_store=destination)
            self.assertEqual(first["sourceArchiveKey"], key)
            self.assertEqual(second["sourceArchiveSha256"], digest)

    def test_rejects_manifest_identity_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            local, destination, report, _, _, manifest_key, _ = self._fixture(Path(temporary))
            bad_root = Path(temporary) / "bad"
            bad = LocalFilesystemObjectStore(bad_root)
            key = report["archive"]["key"]
            source = local.get(key)
            bad.put_immutable(key, source, sha256=report["archive"]["sha256"])
            manifest = json.loads(local.get(manifest_key))
            manifest["runtimePublicationAllowed"] = True
            bad.put_immutable(manifest_key, json.dumps(manifest, sort_keys=True).encode())
            with self.assertRaisesRegex(SourceHandoffPublishError, "manifest identity"):
                publish_verified_handoff(report, local_store=bad, destination_store=destination)

    def test_rejects_non_ready_or_writeful_handoff(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            local, destination, report, *_ = self._fixture(Path(temporary))
            report["status"] = "NO_CHANGE"
            with self.assertRaisesRegex(SourceHandoffPublishError, "not a verified ready"):
                publish_verified_handoff(report, local_store=local, destination_store=destination)
            report["status"] = "HANDOFF_READY"
            report["activePointerWrites"] = 1
            with self.assertRaisesRegex(SourceHandoffPublishError, "write-free"):
                publish_verified_handoff(report, local_store=local, destination_store=destination)


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import gzip
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.argentina_national_routing import NationalRoutingError, load_national_routing
from tools.argentina_object_store_publisher import ObjectStoreReleasePublisher
from tools.build_argentina_national_routing_artifact import NationalRoutingArtifactError, build_national_routing_artifact, derive_release_workspace
from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS, _canonical_json


def _write_release_workspace(root: Path, release_ids: tuple[str, ...]) -> None:
    objects = root / "objects" / "sha256"
    objects.mkdir(parents=True)
    for index, spec in enumerate(ARGENTINA_REGIONS, start=1):
        value = {
            "geoStatus": "VALID",
            "latitude": f"{-34.0 - index / 100:.2f}",
            "longitude": f"{-58.0 - index / 100:.2f}",
            "province": spec.province_code,
            "storeKey": f"fixture:{spec.region_id}:store",
        }
        raw = gzip.compress(_canonical_json(value), compresslevel=9, mtime=0)
        digest = hashlib.sha256(raw).hexdigest()
        (objects / digest).write_bytes(raw)
        descriptor = {"path": f"micro-1024/regions/{spec.region_id}/store-index.jsonl.gz", "bytes": len(raw), "sha256": digest}
        for release_id in release_ids:
            release_dir = root / "releases" / release_id
            release_dir.mkdir(parents=True, exist_ok=True)
            manifest_path = release_dir / "manifest.json"
            if manifest_path.exists():
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                manifest["objects"].append(descriptor)
            else:
                manifest = {
                    "schemaVersion": "valuepilot-argentina-daily-release-v1",
                    "completionState": "COMPLETE",
                    "releaseId": release_id,
                    "source": {"releaseDate": "2026-09-08"},
                    "objects": [descriptor],
                }
            raw_manifest = _canonical_json(manifest)
            manifest_path.write_bytes(raw_manifest)
            (release_dir / "manifest.sha256").write_text(f"{hashlib.sha256(raw_manifest).hexdigest()}  manifest.json\n", encoding="ascii")


class NationalRoutingArtifactTests(unittest.TestCase):
    def test_build_is_deterministic_and_parser_keeps_only_compact_valid_points(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            release_id = "argentina-sepa-test"
            _write_release_workspace(root, (release_id,))
            first = root / "first.gz"
            second = root / "second.gz"
            descriptor_one = build_national_routing_artifact(root, release_id, first)
            descriptor_two = build_national_routing_artifact(root, release_id, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(descriptor_one, descriptor_two)
            logical = root / "micro-1024" / "national-routing.jsonl.gz"
            logical.parent.mkdir(parents=True)
            logical.write_bytes(first.read_bytes())
            points = load_national_routing(root, descriptor_one)
            self.assertEqual(set(points), {spec.region_id for spec in ARGENTINA_REGIONS})
            self.assertEqual(sum(len(value) for value in points.values()), len(ARGENTINA_REGIONS))

    def test_corrupt_or_incomplete_routing_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            release_id = "argentina-sepa-test"
            _write_release_workspace(root, (release_id,))
            artifact = root / "routing.gz"
            descriptor = build_national_routing_artifact(root, release_id, artifact)
            logical = root / "micro-1024" / "national-routing.jsonl.gz"
            logical.parent.mkdir(parents=True)
            logical.write_bytes(artifact.read_bytes())
            logical.write_bytes(b"truncated")
            with self.assertRaises(NationalRoutingError):
                load_national_routing(root, descriptor)
            incomplete = dict(descriptor)
            incomplete["regions"] = incomplete["regions"][:-1]
            logical.write_bytes(artifact.read_bytes())
            with self.assertRaises(NationalRoutingError):
                load_national_routing(root, incomplete)

    def test_duplicate_store_identity_is_rejected_before_derivation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            release_id = "argentina-sepa-test"
            _write_release_workspace(root, (release_id,))
            manifest_path = root / "releases" / release_id / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            target = next(item for item in manifest["objects"] if item["path"].endswith("regions/ar-a/store-index.jsonl.gz"))
            source = root / "objects" / "sha256" / target["sha256"]
            with gzip.open(source, "rb") as handle:
                payload = handle.read()
            duplicate = gzip.compress(payload + payload, compresslevel=9, mtime=0)
            digest = hashlib.sha256(duplicate).hexdigest()
            (root / "objects" / "sha256" / digest).write_bytes(duplicate)
            target["sha256"] = digest
            target["bytes"] = len(duplicate)
            raw = _canonical_json(manifest)
            manifest_path.write_bytes(raw)
            (manifest_path.parent / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
            with self.assertRaises(NationalRoutingArtifactError):
                build_national_routing_artifact(root, release_id, root / "routing.gz")

    def test_derived_sunday_and_tuesday_manifests_reuse_old_objects(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source"
            output = Path(directory) / "derived"
            ids = ("argentina-sepa-sunday", "argentina-sepa-tuesday")
            _write_release_workspace(source, ids)
            plan = derive_release_workspace(source, output, ids)
            self.assertTrue(plan["oldReleasesUnchanged"])
            self.assertEqual([item["derivedReleaseId"] for item in plan["releases"]], [f"{release_id}-routing-v1" for release_id in ids])
            for item in plan["releases"]:
                manifest = json.loads((output / "releases" / item["derivedReleaseId"] / "manifest.json").read_text(encoding="utf-8"))
                self.assertEqual(manifest["routingArtifact"]["schemaVersion"], "valuepilot-national-routing-v1")
                self.assertEqual(manifest["releaseId"], item["derivedReleaseId"])
            self.assertTrue((source / "releases" / ids[0] / "manifest.json").is_file())
            publication = ObjectStoreReleasePublisher().publish(
                output,
                release_ids=tuple(item["derivedReleaseId"] for item in plan["releases"]),
                active_release_id=plan["activeReleaseId"],
                apply=False,
            )
            self.assertFalse(publication["applied"])
            self.assertEqual(publication["remoteWriteCount"], 0)
            self.assertEqual(publication["remoteDeleteCount"], 0)
            self.assertGreaterEqual(publication["objectCount"], 25)


if __name__ == "__main__":
    unittest.main()

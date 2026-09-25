from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.build_store_directory_snapshot import StoreDirectoryBuildError, build_snapshot
from tools.verify_store_directory_snapshot import verify_snapshot


GATES = [
    "DATA_ACCESS_AUTHORIZED",
    "CACHE_AUTHORIZED",
    "INDEX_AUTHORIZED",
    "CONSUMER_DISPLAY_AUTHORIZED",
    "MOBILE_APP_AUTHORIZED",
    "RETENTION_DELETION_POLICY_DEFINED",
    "GEOGRAPHY_SCOPED",
    "COMMERCIAL_USE_REVIEWED",
]


class StoreDirectorySnapshotTest(unittest.TestCase):
    def write_json(self, path: Path, value: object) -> Path:
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def make_inputs(self, root: Path, *, pending_gate: str | None = None) -> tuple[Path, Path]:
        rights = {
            "provider_id": "openstreetmap",
            "dataset_namespace_id": "openstreetmap-places",
            "display_name": "OpenStreetMap places",
            "license_id": "ODbL-1.0",
            "storage_boundary": "OPEN_SHARE_ALIKE",
            "attribution": "© OpenStreetMap contributors",
            "source_url": "https://www.openstreetmap.org/copyright",
            "license_url": "https://opendatacommons.org/licenses/odbl/1-0/",
            "allowed_uses": ["access", "cache", "comparison", "display", "index", "mobile_app", "retention_deletion"],
            "authorization": {
                "gates": [
                    {"gate": gate, "state": "PENDING" if gate == pending_gate else "SATISFIED", "basis_id": f"test-{gate.lower()}"}
                    for gate in GATES
                ]
            },
        }
        rights_path = self.write_json(root / "rights.json", rights)
        config_path = self.write_json(
            root / "config.json",
            {
                "snapshot_id": "directory-test-20260904",
                "generated_at": "2026-09-04T14:00:00Z",
                "regions": ["ca-gta", "ca-metro-vancouver"],
                "source": {
                    "rights_manifest": rights_path.name,
                    "source_snapshot_id": "overpass-test-20260904",
                    "acquired_at": "2026-09-04T13:00:00Z",
                    "observed_at": "2026-09-04T13:00:00Z",
                },
            },
        )
        input_path = self.write_json(
            root / "source.json",
            {
                "elements": [
                    {
                        "type": "node",
                        "id": 2,
                        "lat": 43.65,
                        "lon": -79.38,
                        "tags": {
                            "name": "Toronto Grocery",
                            "brand": "Example Foods",
                            "shop": "supermarket",
                            "addr:housenumber": "10",
                            "addr:street": "Main Street",
                            "addr:city": "Toronto",
                            "addr:postcode": "M5V 2H1",
                            "phone": "not emitted",
                        },
                    },
                    {
                        "type": "way",
                        "id": 1,
                        "center": {"lat": 49.28, "lon": -123.12},
                        "tags": {"name": "Vancouver Bakery", "shop": "bakery"},
                    },
                    {"type": "node", "id": 3, "lat": 43.65, "lon": -79.38, "tags": {"shop": "convenience"}},
                    {"type": "node", "id": 4, "lat": 45.0, "lon": -79.38, "tags": {"name": "Outside", "shop": "supermarket"}},
                ]
            },
        )
        return config_path, input_path

    def test_same_inputs_are_byte_identical_and_location_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config, source = self.make_inputs(root)
            first = root / "first"
            second = root / "second"
            first_result = build_snapshot(config, source, first)
            second_result = build_snapshot(config, source, second)
            for relative in (
                "manifest.json",
                "manifest.sha256",
                "integrity.json",
                "ATTRIBUTION.txt",
                "sources/openstreetmap-places.jsonl",
            ):
                self.assertEqual((first / relative).read_bytes(), (second / relative).read_bytes())
            self.assertEqual({"ca-gta": 1, "ca-metro-vancouver": 1}, first_result["regions"])
            self.assertEqual(first_result, second_result)
            rows = [json.loads(line) for line in (first / "sources/openstreetmap-places.jsonl").read_text(encoding="utf-8").splitlines()]
            self.assertEqual(["osm:node:2", "osm:way:1"], [row["recordId"] for row in rows])
            self.assertEqual("LOCATION_ONLY", rows[0]["status"])
            self.assertNotIn("price", rows[0])
            self.assertNotIn("phone", rows[0])
            self.assertEqual({"housenumber", "street", "city", "postcode"}, set(rows[0]["address"]))

    def test_pending_rights_gate_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config, source = self.make_inputs(root, pending_gate="COMMERCIAL_USE_REVIEWED")
            with self.assertRaisesRegex(StoreDirectoryBuildError, "COMMERCIAL_USE_REVIEWED"):
                build_snapshot(config, source, root / "out")

    def test_invalid_coordinates_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config, source = self.make_inputs(root)
            raw = json.loads(source.read_text(encoding="utf-8"))
            raw["elements"][0]["lat"] = 91
            source.write_text(json.dumps(raw), encoding="utf-8")
            with self.assertRaisesRegex(StoreDirectoryBuildError, "outside geographic bounds"):
                build_snapshot(config, source, root / "out")

    def test_signature_is_detached_and_verifiable(self):
        if shutil.which("openssl") is None:
            self.skipTest("openssl is required")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config, source = self.make_inputs(root)
            private_key = root / "private.pem"
            public_key = root / "public.pem"
            subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(private_key)], check=True, capture_output=True)
            subprocess.run(["openssl", "rsa", "-in", str(private_key), "-pubout", "-out", str(public_key)], check=True, capture_output=True)
            out = root / "out"
            result = build_snapshot(config, source, out, private_key=private_key, require_signature=True)
            self.assertEqual("VERIFIED", result["signatureState"])
            verified = verify_snapshot(out, public_key=public_key, require_signature=True)
            self.assertEqual("VERIFIED", verified["signatureState"])

    def test_verifier_rejects_tampering_and_unsigned_when_signature_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config, source = self.make_inputs(root)
            out = root / "out"
            build_snapshot(config, source, out)
            self.assertEqual("UNSIGNED", verify_snapshot(out)["signatureState"])
            with self.assertRaisesRegex(StoreDirectoryBuildError, "signed store-directory"):
                verify_snapshot(out, require_signature=True)
            content = out / "sources/openstreetmap-places.jsonl"
            content.write_bytes(content.read_bytes().replace(b"LOCATION_ONLY", b"price", 1))
            with self.assertRaisesRegex(StoreDirectoryBuildError, "content hash"):
                verify_snapshot(out)


if __name__ == "__main__":
    unittest.main()

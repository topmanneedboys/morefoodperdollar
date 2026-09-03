from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.build_offline_catalog_snapshot import SnapshotBuildError, build_snapshot
from tools.verify_offline_catalog_snapshot import verify_snapshot


DISCOVERY_GATES = [
    "DATA_ACCESS_AUTHORIZED",
    "CONSUMER_DISPLAY_AUTHORIZED",
    "CACHE_AUTHORIZED",
    "INDEX_AUTHORIZED",
    "MOBILE_APP_AUTHORIZED",
    "RETENTION_DELETION_POLICY_DEFINED",
]


class OfflineCatalogSnapshotBuilderTest(unittest.TestCase):
    def write_json(self, path: Path, value: object) -> Path:
        path.write_text(json.dumps(value), encoding="utf-8")
        return path

    def make_source(self, root: Path, *, provider: str = "open-food-facts", dataset: str = "off-ca", rows: list[dict[str, object]] | None = None, pending_gate: str | None = None) -> dict[str, str]:
        rights = {
            "provider_id": provider,
            "dataset_namespace_id": dataset,
            "display_name": "Open Food Facts Canada",
            "license_id": "odbl-1.0",
            "storage_boundary": "OPEN_SHARE_ALIKE",
            "allowed_uses": ["discovery", "display", "cache", "index", "mobile_app", "retention_deletion"],
            "authorization": {
                "gates": [
                    {"gate": gate, "state": "PENDING" if gate == pending_gate else "SATISFIED", "basis_id": f"test-{gate.lower()}"}
                    for gate in DISCOVERY_GATES
                ]
            },
        }
        self.write_json(root / f"{dataset}-rights.json", rights)
        records = rows or [
            {
                "record_id": "off:milk-1",
                "provider_item_id": "milk-1",
                "gtin": "0036000291452",
                "display_name": "Café Whole Milk 2 L",
                "brand": "Dairy Best",
                "aliases": ["whole milk", "2 litre milk"],
            },
            {
                "record_id": "off:tea-1",
                "sku": "tea-1",
                "display_name": "Black Tea Bags",
                "aliases": ["tea bags"],
            },
        ]
        catalog = root / f"{dataset}.jsonl"
        catalog.write_text("".join(json.dumps(row) + "\n" for row in records), encoding="utf-8")
        return {
            "rights_manifest": f"{dataset}-rights.json",
            "catalog": catalog.name,
            "snapshot_id": f"{dataset}-2026-09-03",
            "acquired_at": "2026-09-03T11:00:00Z",
            "source_published_at": "2026-09-02T00:00:00Z",
        }

    def make_config(self, root: Path, sources: list[dict[str, str]]) -> Path:
        return self.write_json(
            root / "config.json",
            {
                "region_id": "ca-gta",
                "snapshot_id": "ca-gta-2026-09-03",
                "generated_at": "2026-09-03T12:00:00Z",
                "sources": sources,
            },
        )

    def test_same_inputs_are_byte_identical_and_source_isolated(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root)])
            first = root / "first"
            second = root / "second"
            manifest_one = build_snapshot(config, first)
            manifest_two = build_snapshot(config, second)
            for relative in ("manifest.json", "manifest.sha256", "integrity.json", "sources/off-ca.jsonl"):
                self.assertEqual((first / relative).read_bytes(), (second / relative).read_bytes())
            self.assertEqual(2, manifest_one["coverage"]["catalogRecordCount"])
            self.assertEqual(1, manifest_one["coverage"]["recordsWithValidGtin"])
            self.assertEqual(manifest_one, manifest_two)
            records = [json.loads(line) for line in (first / "sources/off-ca.jsonl").read_text(encoding="utf-8").splitlines()]
            self.assertEqual(["off:milk-1", "off:tea-1"], [record["recordId"] for record in records])
            self.assertNotIn("price", records[0])
            self.assertEqual("cafe whole milk 2 l", records[0]["canonicalSearchName"])

    def test_rights_pending_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root, pending_gate="CACHE_AUTHORIZED")])
            with self.assertRaisesRegex(SnapshotBuildError, "CACHE_AUTHORIZED"):
                build_snapshot(config, root / "out")

    def test_offer_fields_are_rejected_in_catalog(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root, rows=[{"record_id": "a", "sku": "a", "display_name": "Tea", "price": "4.99"}])])
            with self.assertRaisesRegex(SnapshotBuildError, "offer-only fields"):
                build_snapshot(config, root / "out")

    def test_invalid_gtin_only_identity_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root, rows=[{"record_id": "a", "gtin": "036000291453", "display_name": "Tea"}])])
            with self.assertRaisesRegex(SnapshotBuildError, "only an invalid GTIN"):
                build_snapshot(config, root / "out")

    def test_duplicate_ids_and_namespace_collisions_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            duplicate_rows = [
                {"record_id": "a", "sku": "a", "display_name": "Tea"},
                {"record_id": "a", "sku": "b", "display_name": "Coffee"},
            ]
            config = self.make_config(root, [self.make_source(root, rows=duplicate_rows)])
            with self.assertRaisesRegex(SnapshotBuildError, "Duplicate catalog record id"):
                build_snapshot(config, root / "out")

            # A second source with the same namespace must not be flattened.
            config = self.make_config(root, [self.make_source(root), self.make_source(root)])
            with self.assertRaisesRegex(SnapshotBuildError, "Duplicate dataset namespace"):
                build_snapshot(config, root / "out2")

    def test_time_order_and_output_overwrite_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = self.make_source(root)
            source["acquired_at"] = "2026-09-03T13:00:00Z"
            config = self.make_config(root, [source])
            with self.assertRaisesRegex(SnapshotBuildError, "acquired after"):
                build_snapshot(config, root / "out")
            out = root / "existing"
            out.mkdir()
            (out / "keep.txt").write_text("do not overwrite", encoding="utf-8")
            source["acquired_at"] = "2026-09-03T11:00:00Z"
            config = self.make_config(root, [source])
            with self.assertRaisesRegex(SnapshotBuildError, "missing or empty"):
                build_snapshot(config, out)

    def test_timestamps_require_timezone_and_positive_epoch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = self.make_source(root)
            config = self.make_config(root, [source])
            config_data = json.loads(config.read_text(encoding="utf-8"))
            config_data["generated_at"] = "2026-09-03T12:00:00"
            config.write_text(json.dumps(config_data), encoding="utf-8")
            with self.assertRaisesRegex(SnapshotBuildError, "explicit timezone"):
                build_snapshot(config, root / "out")
            config_data["generated_at"] = "1969-12-31T23:59:59Z"
            config.write_text(json.dumps(config_data), encoding="utf-8")
            with self.assertRaisesRegex(SnapshotBuildError, "Unix epoch"):
                build_snapshot(config, root / "out2")

    def test_signature_is_required_when_requested(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root)])
            out = root / "out"
            with self.assertRaisesRegex(SnapshotBuildError, "--require-signature"):
                build_snapshot(config, out, require_signature=True)
            self.assertFalse(out.exists())

    def test_unknown_record_fields_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root, rows=[{"record_id": "a", "sku": "a", "display_name": "Tea", "source_note": "ambiguous"}])])
            with self.assertRaisesRegex(SnapshotBuildError, "unsupported fields"):
                build_snapshot(config, root / "out")

    @unittest.skipUnless(shutil.which("openssl"), "openssl is required for signature integration test")
    def test_optional_rsa_signature_is_detached_and_verifiable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root)])
            private_key = root / "private-key.pem"
            public_key = root / "public-key.pem"
            subprocess.run(
                ["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(private_key)],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["openssl", "rsa", "-in", str(private_key), "-pubout", "-out", str(public_key)],
                check=True,
                capture_output=True,
            )
            out = root / "out"
            build_snapshot(config, out, private_key=private_key, require_signature=True)
            verification = subprocess.run(
                ["openssl", "dgst", "-sha256", "-verify", str(public_key), "-signature", str(out / "manifest.sig"), str(out / "manifest.json")],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, verification.returncode)
            self.assertIn("Verified OK", verification.stdout)

    def test_verifier_accepts_canonical_unsigned_output_but_not_tampering(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root)])
            out = root / "out"
            build_snapshot(config, out)
            result = verify_snapshot(out)
            self.assertEqual("UNSIGNED", result["signatureState"])
            self.assertEqual(2, result["records"])
            source = out / "sources" / "off-ca.jsonl"
            source.write_bytes(source.read_bytes() + b"\n")
            with self.assertRaisesRegex(SnapshotBuildError, "Source content hash mismatch"):
                verify_snapshot(out)

    def test_verifier_requires_public_key_for_signed_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = self.make_config(root, [self.make_source(root)])
            private_key = root / "private-key.pem"
            public_key = root / "public-key.pem"
            subprocess.run(
                ["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(private_key)],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["openssl", "rsa", "-in", str(private_key), "-pubout", "-out", str(public_key)],
                check=True,
                capture_output=True,
            )
            out = root / "out"
            build_snapshot(config, out, private_key=private_key, require_signature=True)
            with self.assertRaisesRegex(SnapshotBuildError, "public key"):
                verify_snapshot(out)
            result = verify_snapshot(out, public_key=public_key, require_signature=True)
            self.assertEqual("VERIFIED", result["signatureState"])


if __name__ == "__main__":
    unittest.main()

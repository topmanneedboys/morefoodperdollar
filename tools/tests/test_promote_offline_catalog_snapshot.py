from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.build_offline_catalog_snapshot import _parse_timestamp, build_snapshot
from tools.promote_offline_catalog_snapshot import SnapshotPromotionError, promote_snapshot


DISCOVERY_GATES = [
    "DATA_ACCESS_AUTHORIZED",
    "CONSUMER_DISPLAY_AUTHORIZED",
    "CACHE_AUTHORIZED",
    "INDEX_AUTHORIZED",
    "MOBILE_APP_AUTHORIZED",
    "RETENTION_DELETION_POLICY_DEFINED",
]
NOW = _parse_timestamp("2026-09-03T12:00:00Z", "test.now")
MAX_AGE = 10_000


class OfflineCatalogSnapshotPromotionTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("openssl"), "openssl is required for promotion tests")
    def test_promotes_signed_snapshot_and_writes_both_pointers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            private_key, public_key = self.make_keypair(root)
            state = root / "state"
            state.mkdir()
            candidate = self.make_snapshot(state, record_count=1_500, private_key=private_key)

            result = promote_snapshot(
                candidate,
                state,
                public_key,
                evaluated_at_epoch_millis=NOW,
                maximum_snapshot_age_millis=MAX_AGE,
                expected_region_id="ca-gta",
            )

            self.assertTrue(result["promoted"])
            current = json.loads((state / "current.json").read_text())
            last_good = json.loads((state / "last-known-good.json").read_text())
            self.assertEqual(current, last_good)
            self.assertEqual("ca-gta", current["regionId"])
            self.assertEqual(1_500, current["catalogRecordCount"])
            self.assertEqual("out", current["snapshotPath"])

    @unittest.skipUnless(shutil.which("openssl"), "openssl is required for promotion tests")
    def test_rejected_regression_leaves_last_known_good_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            private_key, public_key = self.make_keypair(root)
            state = root / "state"
            state.mkdir()
            candidate = self.make_snapshot(state, record_count=1_500, private_key=private_key)
            promote_snapshot(candidate, state, public_key, evaluated_at_epoch_millis=NOW, maximum_snapshot_age_millis=MAX_AGE)
            before = {
                name: (state / name).read_bytes()
                for name in ("current.json", "last-known-good.json")
            }

            undersized = self.make_snapshot(state, record_count=1_499, snapshot_name="under", private_key=private_key)
            with self.assertRaisesRegex(SnapshotPromotionError, "between 1500 and 5000"):
                promote_snapshot(undersized, state, public_key, evaluated_at_epoch_millis=NOW, maximum_snapshot_age_millis=MAX_AGE)

            self.assertEqual(before["current.json"], (state / "current.json").read_bytes())
            self.assertEqual(before["last-known-good.json"], (state / "last-known-good.json").read_bytes())

    @unittest.skipUnless(shutil.which("openssl"), "openssl is required for promotion tests")
    def test_rejects_unsigned_and_older_candidates_without_pointer_creation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            private_key, public_key = self.make_keypair(root)
            state = root / "state"
            state.mkdir()
            unsigned = self.make_snapshot(state, record_count=1_500, snapshot_name="unsigned")
            with self.assertRaisesRegex(SnapshotPromotionError, "Candidate verification failed"):
                promote_snapshot(unsigned, state, public_key, evaluated_at_epoch_millis=NOW, maximum_snapshot_age_millis=MAX_AGE)
            self.assertFalse((state / "current.json").exists())

            current = self.make_snapshot(state, record_count=1_500, snapshot_name="current", generated_at="2026-09-03T12:00:00Z", private_key=private_key)
            promote_snapshot(current, state, public_key, evaluated_at_epoch_millis=NOW, maximum_snapshot_age_millis=MAX_AGE)
            older = self.make_snapshot(state, record_count=1_500, snapshot_name="older", generated_at="2026-09-03T11:59:59Z", private_key=private_key)
            with self.assertRaisesRegex(SnapshotPromotionError, "older than the active"):
                promote_snapshot(older, state, public_key, evaluated_at_epoch_millis=NOW, maximum_snapshot_age_millis=MAX_AGE)

    @staticmethod
    def make_keypair(root: Path) -> tuple[Path, Path]:
        private_key = root / "private.pem"
        public_key = root / "public.pem"
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(private_key)], check=True, capture_output=True)
        subprocess.run(["openssl", "rsa", "-in", str(private_key), "-pubout", "-out", str(public_key)], check=True, capture_output=True)
        return private_key, public_key

    def make_snapshot(
        self,
        root: Path,
        *,
        record_count: int,
        snapshot_name: str = "out",
        generated_at: str = "2026-09-03T12:00:00Z",
        private_key: Path | None = None,
    ) -> Path:
        source = root / f"{snapshot_name}-source.jsonl"
        rows = [
            {
                "record_id": f"off:item-{index}",
                "provider_item_id": f"item-{index}",
                "display_name": f"Grocery item {index}",
            }
            for index in range(record_count)
        ]
        source.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        rights = {
            "provider_id": "open-food-facts",
            "dataset_namespace_id": "off-ca",
            "display_name": "Open Food Facts Canada",
            "license_id": "odbl-1.0",
            "storage_boundary": "OPEN_SHARE_ALIKE",
            "allowed_uses": ["discovery", "display", "cache", "index", "mobile_app", "retention_deletion"],
            "authorization": {"gates": [{"gate": gate, "state": "SATISFIED", "basis_id": f"test-{gate.lower()}"} for gate in DISCOVERY_GATES]},
        }
        rights_path = root / f"{snapshot_name}-rights.json"
        rights_path.write_text(json.dumps(rights), encoding="utf-8")
        config = {
            "region_id": "ca-gta",
            "snapshot_id": f"ca-gta-{snapshot_name}",
            "generated_at": generated_at,
            "sources": [{
                "rights_manifest": rights_path.name,
                "catalog": source.name,
                "snapshot_id": f"off-{snapshot_name}",
                "acquired_at": "2026-09-03T11:00:00Z",
                "source_published_at": "2026-09-02T00:00:00Z",
            }],
        }
        config_path = root / f"{snapshot_name}-config.json"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        output = root / snapshot_name
        build_snapshot(config_path, output, private_key=private_key, require_signature=private_key is not None)
        return output


if __name__ == "__main__":
    unittest.main()

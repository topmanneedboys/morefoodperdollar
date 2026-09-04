from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import tools.promote_offline_catalog_release as release_module
from tools.build_offline_catalog_snapshot import build_snapshot
from tools.promote_offline_catalog_release import (
    ACTIVE_GENERATION_POINTER_NAME,
    CatalogReleasePromotionError,
    promote_release,
    read_active_release,
)


DISCOVERY_GATES = (
    "DATA_ACCESS_AUTHORIZED",
    "CONSUMER_DISPLAY_AUTHORIZED",
    "CACHE_AUTHORIZED",
    "INDEX_AUTHORIZED",
    "MOBILE_APP_AUTHORIZED",
    "RETENTION_DELETION_POLICY_DEFINED",
)


@unittest.skipUnless(shutil.which("openssl"), "openssl is required for release tests")
class OfflineCatalogReleasePromotionTest(unittest.TestCase):
    def test_promotes_complete_regions_through_one_active_pointer(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            private_key, public_key = self.make_keypair(root)
            state = root
            candidates = [
                {
                    "regionId": region,
                    "candidatePath": str(
                        self.make_snapshot(
                            root,
                            region=region,
                            snapshot_name=region,
                            generated_at="2026-09-03T12:00:00Z",
                            private_key=private_key,
                        )
                    ),
                }
                for region in ("ca-gta", "ca-metro-vancouver")
            ]

            result = promote_release(
                candidates,
                state,
                public_key,
                generated_at_epoch_millis=correct_epoch("2026-09-03T12:00:00Z"),
                evaluated_at_epoch_millis=correct_epoch("2026-09-03T12:00:00Z"),
                maximum_snapshot_age_millis=7 * 24 * 60 * 60 * 1_000,
                minimum_catalog_records=1,
                maximum_catalog_records=5_000,
            )

            self.assertTrue(result["promoted"])
            pointer_path = state / ACTIVE_GENERATION_POINTER_NAME
            self.assertTrue(pointer_path.is_file())
            pointer = json.loads(pointer_path.read_text(encoding="utf-8"))
            active = pointer["activeGeneration"]
            self.assertEqual(
                ["ca-gta", "ca-metro-vancouver"],
                [item["regionId"] for item in active["regions"]],
            )
            self.assertIsNone(pointer["lastKnownGoodGeneration"])
            self.assertTrue((state / active["generationPath"]).is_file())
            self.assertEqual(active["generationId"], read_active_release(state, public_key)["generationId"])

    def test_shared_identity_source_counts_once_across_regional_references(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            private_key, public_key = self.make_keypair(root)
            candidates = [
                {
                    "regionId": region,
                    "candidatePath": str(
                        self.make_snapshot(
                            root,
                            region=region,
                            snapshot_name=f"{region}-shared",
                            source_name="shared-national",
                            generated_at="2026-09-03T12:00:00Z",
                            private_key=private_key,
                        )
                    ),
                }
                for region in ("ca-gta", "ca-metro-vancouver")
            ]

            result = promote_release(
                candidates,
                root,
                public_key,
                generated_at_epoch_millis=correct_epoch("2026-09-03T12:00:00Z"),
                evaluated_at_epoch_millis=correct_epoch("2026-09-03T12:00:00Z"),
                maximum_snapshot_age_millis=7 * 24 * 60 * 60 * 1_000,
                minimum_catalog_records=1,
                maximum_catalog_records=5_000,
            )

            self.assertEqual(1, result["activeGeneration"]["catalogRecordCount"])
            self.assertEqual(
                1,
                read_active_release(root, public_key)["catalogRecordCount"],
            )

    def test_pointer_write_failure_leaves_previous_generation_active_and_orphans_are_ignored(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            private_key, public_key = self.make_keypair(root)
            state = root
            first_candidate = self.make_snapshot(
                root,
                region="ca-gta",
                snapshot_name="first",
                generated_at="2026-09-03T12:00:00Z",
                private_key=private_key,
            )
            promote_release(
                [{"regionId": "ca-gta", "candidatePath": str(first_candidate)}],
                state,
                public_key,
                generated_at_epoch_millis=correct_epoch("2026-09-03T12:00:00Z"),
                evaluated_at_epoch_millis=correct_epoch("2026-09-03T12:00:00Z"),
                maximum_snapshot_age_millis=7 * 24 * 60 * 60 * 1_000,
                minimum_catalog_records=1,
                maximum_catalog_records=5_000,
            )
            pointer_path = state / ACTIVE_GENERATION_POINTER_NAME
            before_pointer = pointer_path.read_bytes()
            before_active = read_active_release(state, public_key)
            second_candidate = self.make_snapshot(
                root,
                region="ca-gta",
                snapshot_name="second",
                generated_at="2026-09-03T13:00:00Z",
                private_key=private_key,
            )
            real_write = release_module._write_json_atomically

            def fail_pointer_write(path: Path, value):
                if path.name == ACTIVE_GENERATION_POINTER_NAME:
                    raise OSError("simulated interruption during active pointer replace")
                return real_write(path, value)

            with patch.object(release_module, "_write_json_atomically", side_effect=fail_pointer_write):
                with self.assertRaisesRegex(OSError, "simulated interruption"):
                    promote_release(
                        [{"regionId": "ca-gta", "candidatePath": str(second_candidate)}],
                        state,
                        public_key,
                        generated_at_epoch_millis=correct_epoch("2026-09-03T13:00:00Z"),
                        evaluated_at_epoch_millis=correct_epoch("2026-09-03T13:00:00Z"),
                        maximum_snapshot_age_millis=7 * 24 * 60 * 60 * 1_000,
                        minimum_catalog_records=1,
                        maximum_catalog_records=5_000,
                    )

            self.assertEqual(before_pointer, pointer_path.read_bytes())
            self.assertEqual(before_active["generationId"], read_active_release(state, public_key)["generationId"])
            self.assertGreater(len(list((state / "generations").glob("*.json"))), 1)

    def test_corrupt_active_generation_falls_back_to_last_known_good(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            private_key, public_key = self.make_keypair(root)
            state = root
            for name, timestamp in (("first", "2026-09-03T12:00:00Z"), ("second", "2026-09-03T13:00:00Z")):
                candidate = self.make_snapshot(
                    root,
                    region="ca-gta",
                    snapshot_name=name,
                    generated_at=timestamp,
                    private_key=private_key,
                )
                promote_release(
                    [{"regionId": "ca-gta", "candidatePath": str(candidate)}],
                    state,
                    public_key,
                    generated_at_epoch_millis=correct_epoch(timestamp),
                    evaluated_at_epoch_millis=correct_epoch(timestamp),
                    maximum_snapshot_age_millis=7 * 24 * 60 * 60 * 1_000,
                    minimum_catalog_records=1,
                    maximum_catalog_records=5_000,
                )

            pointer = json.loads((state / ACTIVE_GENERATION_POINTER_NAME).read_text(encoding="utf-8"))
            active_path = state / pointer["activeGeneration"]["generationPath"]
            last_good_id = pointer["lastKnownGoodGeneration"]["generationId"]
            active_path.unlink()

            recovered = read_active_release(state, public_key)
            self.assertEqual(last_good_id, recovered["generationId"])

    @staticmethod
    def make_keypair(root: Path) -> tuple[Path, Path]:
        private_key = root / "private.pem"
        public_key = root / "public.pem"
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
        return private_key, public_key

    @staticmethod
    def make_snapshot(
        root: Path,
        *,
        region: str,
        snapshot_name: str,
        source_name: str | None = None,
        generated_at: str,
        private_key: Path,
    ) -> Path:
        source_name = source_name or snapshot_name
        source = root / f"{snapshot_name}-source.jsonl"
        source.write_text(
            json.dumps(
                {
                    "record_id": f"off:{source_name}",
                    "provider_item_id": source_name,
                    "display_name": f"Grocery item {source_name}",
                },
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        rights = root / f"{snapshot_name}-rights.json"
        rights.write_text(
            json.dumps(
                {
                    "provider_id": "open-food-facts",
                    "dataset_namespace_id": "off-ca",
                    "display_name": "Open Food Facts Canada",
                    "license_id": "odbl-1.0",
                    "storage_boundary": "OPEN_SHARE_ALIKE",
                    "allowed_uses": ["discovery", "display", "cache", "index", "mobile_app", "retention_deletion"],
                    "authorization": {
                        "gates": [{"gate": gate, "state": "SATISFIED", "basis_id": f"test-{gate.lower()}"} for gate in DISCOVERY_GATES]
                    },
                },
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        config = root / f"{snapshot_name}-config.json"
        config.write_text(
            json.dumps(
                {
                    "region_id": region,
                    "snapshot_id": f"{region}-{snapshot_name}",
                    "generated_at": generated_at,
                    "sources": [
                        {
                            "rights_manifest": rights.name,
                            "catalog": source.name,
                            "snapshot_id": f"off-{source_name}",
                            "acquired_at": "2026-09-03T11:00:00Z",
                        }
                    ],
                },
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        output = root / f"snapshot-{snapshot_name}"
        build_snapshot(config, output, private_key=private_key, require_signature=True)
        return output


def correct_epoch(value: str) -> int:
    from datetime import datetime, timezone

    return int(datetime.fromisoformat(value.replace("Z", "+00:00")).replace(tzinfo=timezone.utc).timestamp() * 1_000)


if __name__ == "__main__":
    unittest.main()

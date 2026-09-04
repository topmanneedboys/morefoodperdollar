from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import tools.refresh_offline_catalog_snapshots as refresh_module
from tools.promote_offline_catalog_snapshot import SnapshotPromotionError
from tools.refresh_offline_catalog_snapshots import (
    OfflineCatalogRefreshError,
    refresh_snapshots,
)
from tools.verify_offline_catalog_snapshot import verify_snapshot


DISCOVERY_GATES = [
    "DATA_ACCESS_AUTHORIZED",
    "CONSUMER_DISPLAY_AUTHORIZED",
    "CACHE_AUTHORIZED",
    "INDEX_AUTHORIZED",
    "MOBILE_APP_AUTHORIZED",
    "RETENTION_DELETION_POLICY_DEFINED",
]


@unittest.skipUnless(shutil.which("openssl"), "openssl is required for refresh tests")
class OfflineCatalogRefreshTest(unittest.TestCase):
    def test_builds_and_verifies_both_metros_deterministically(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "products.jsonl"
            self.write_export(source, count=1_500)
            rights = self.write_rights(root / "off-rights.json")
            private_key, public_key = self.make_keypair(root)

            first = refresh_snapshots(
                source,
                root / "first",
                rights,
                private_key,
                public_key,
                **self.run_args(),
                max_records=1_500,
            )
            second = refresh_snapshots(
                source,
                root / "second",
                rights,
                private_key,
                public_key,
                **self.run_args(),
                max_records=1_500,
            )

            self.assertFalse(first["promoted"])
            self.assertEqual(1_500, first["catalogRecordCount"])
            self.assertEqual(
                ["ca-gta", "ca-metro-vancouver"],
                [item["regionId"] for item in first["regions"]],
            )
            self.assertEqual(
                {
                    "catalogRecordCount": 1_500,
                    "currentOfferRecordCount": 0,
                    "currentOfferCoverage": "NOT_INCLUDED",
                },
                first["coverage"],
            )
            first_report = json.loads((root / "first" / "coverage-report.json").read_text())
            second_report = json.loads((root / "second" / "coverage-report.json").read_text())
            self.assertEqual(first_report, second_report)
            self.assertEqual("OFFLINE_CATALOG_COVERAGE", first_report["reportType"])
            self.assertEqual(1_500, first_report["catalog"]["recordCount"])
            self.assertEqual("MEASURED", first_report["catalog"]["coverageStatus"])
            self.assertEqual(1_500, first_report["catalog"]["selection"]["uniqueCanonicalIdentityNames"])
            self.assertEqual(0, first_report["catalog"]["selection"]["selectedHouseholdHintRecords"])
            self.assertEqual("NONE", first_report["catalog"]["selection"]["authority"])
            self.assertEqual(0, first_report["currentOffers"]["recordCount"])
            self.assertEqual("NOT_INCLUDED", first_report["currentOffers"]["coverageStatus"])
            self.assertEqual(
                ["ca-gta", "ca-metro-vancouver"],
                [item["regionId"] for item in first_report["regions"]],
            )
            for left, right in zip(first["regions"], second["regions"]):
                left_dir = Path(left["candidatePath"])
                right_dir = Path(right["candidatePath"])
                self.assertEqual(left["manifestSha256"], right["manifestSha256"])
                self.assertEqual(0, left["currentOfferRecordCount"])
                self.assertEqual("NOT_INCLUDED", left["currentOfferCoverage"])
                for name in (
                    "manifest.json",
                    "manifest.sha256",
                    "manifest.sig",
                    "integrity.json",
                    "sources/off-ca.jsonl",
                ):
                    self.assertEqual(
                        (left_dir / name).read_bytes(),
                        (right_dir / name).read_bytes(),
                        name,
                    )
                verified = verify_snapshot(
                    left_dir,
                    public_key=public_key,
                    require_signature=True,
                )
                self.assertEqual(left["manifestSha256"], verified["manifestSha256"])

    def test_promotes_only_after_preflight_and_preserves_pointers_on_regression(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "products.jsonl"
            self.write_export(source, count=1_500)
            rights = self.write_rights(root / "off-rights.json")
            private_key, public_key = self.make_keypair(root)
            state = root / "state"

            result = refresh_snapshots(
                source,
                state,
                rights,
                private_key,
                public_key,
                **self.run_args(),
                max_records=1_500,
                promote=True,
            )
            self.assertTrue(result["promoted"])
            before = {
                region: {
                    name: (state / region / name).read_bytes()
                    for name in ("current.json", "last-known-good.json")
                }
                for region in ("ca-gta", "ca-metro-vancouver")
            }
            coverage_report_before = (state / "coverage-report.json").read_bytes()
            for pointers in before.values():
                self.assertEqual(pointers["current.json"], pointers["last-known-good.json"])

            self.write_export(source, count=1_499)
            with self.assertRaisesRegex(OfflineCatalogRefreshError, "between 1500 and 5000"):
                refresh_snapshots(
                    source,
                    state,
                    rights,
                    private_key,
                    public_key,
                    **self.run_args(),
                    max_records=1_500,
                    promote=True,
                )

            for region, pointers in before.items():
                for name, content in pointers.items():
                    self.assertEqual(content, (state / region / name).read_bytes())
            self.assertEqual(coverage_report_before, (state / "coverage-report.json").read_bytes())

    def test_rejects_catalog_outside_identity_variety_target_before_building(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "products.jsonl"
            self.write_export(source, count=1_500, same_name=True)
            rights = self.write_rights(root / "off-rights.json")
            private_key, public_key = self.make_keypair(root)

            with self.assertRaisesRegex(
                OfflineCatalogRefreshError,
                "identity-name variety is outside the launch target",
            ):
                refresh_snapshots(
                    source,
                    root / "state",
                    rights,
                    private_key,
                    public_key,
                    **self.run_args(),
                    max_records=1_500,
                )

            self.assertFalse((root / "state" / "coverage-report.json").exists())

    def test_rolls_back_all_region_pointers_if_later_promotion_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "products.jsonl"
            self.write_export(source, count=1_500)
            rights = self.write_rights(root / "off-rights.json")
            private_key, public_key = self.make_keypair(root)
            state = root / "state"

            refresh_snapshots(
                source,
                state,
                rights,
                private_key,
                public_key,
                **self.run_args(),
                max_records=1_500,
                promote=True,
            )
            before_pointers = {
                region: {
                    name: (state / region / name).read_bytes()
                    for name in ("current.json", "last-known-good.json")
                }
                for region in ("ca-gta", "ca-metro-vancouver")
            }
            before_report = (state / "coverage-report.json").read_bytes()

            updated_args = self.run_args()
            updated_args.update(
                {
                    "generated_at": "2026-09-03T13:00:00Z",
                    "acquired_at": "2026-09-03T12:00:00Z",
                    "evaluated_at": "2026-09-03T13:00:00Z",
                }
            )
            real_promote = refresh_module.promote_snapshot
            calls = 0

            def promote_with_failure(*args, **kwargs):
                nonlocal calls
                calls += 1
                if calls == 2:
                    raise SnapshotPromotionError("simulated later-region failure")
                return real_promote(*args, **kwargs)

            with patch.object(
                refresh_module,
                "promote_snapshot",
                side_effect=promote_with_failure,
            ):
                with self.assertRaisesRegex(
                    OfflineCatalogRefreshError,
                    "Snapshot promotion failed for ca-metro-vancouver",
                ):
                    refresh_snapshots(
                        source,
                        state,
                        rights,
                        private_key,
                        public_key,
                        **updated_args,
                        max_records=1_500,
                        promote=True,
                    )

            self.assertEqual(2, calls)
            for region, pointers in before_pointers.items():
                for name, content in pointers.items():
                    self.assertEqual(content, (state / region / name).read_bytes())
            self.assertEqual(before_report, (state / "coverage-report.json").read_bytes())

    @staticmethod
    def run_args() -> dict[str, object]:
        return {
            "generated_at": "2026-09-03T12:00:00Z",
            "acquired_at": "2026-09-03T11:00:00Z",
            "evaluated_at": "2026-09-03T12:00:00Z",
            "maximum_snapshot_age_millis": 7 * 24 * 60 * 60 * 1_000,
            "source_snapshot_id": "off-products-2026-09-03",
            "source_published_at": "2026-09-02T00:00:00Z",
            "minimum_catalog_records": 1_500,
            "maximum_catalog_records": 5_000,
        }

    @staticmethod
    def write_export(path: Path, *, count: int, same_name: bool = False) -> None:
        path.write_text(
            "".join(
                json.dumps(
                    {
                        "code": OfflineCatalogRefreshTest.gtin(index),
                        "product_name_en": "Grocery Item" if same_name else f"Grocery Item {index}",
                        "brands": "ValuePilot Test",
                        "countries_tags": "en:canada",
                        "categories_tags": "en:beverages",
                    },
                    sort_keys=True,
                )
                + "\n"
                for index in range(count)
            ),
            encoding="utf-8",
        )

    @staticmethod
    def gtin(index: int) -> str:
        body = f"036000{index:05d}"
        total = sum(
            int(digit) * (3 if (len(body) - offset) % 2 else 1)
            for offset, digit in enumerate(body)
        )
        return body + str((10 - total % 10) % 10)

    @staticmethod
    def write_rights(path: Path) -> Path:
        path.write_text(
            json.dumps(
                {
                    "provider_id": "open-food-facts",
                    "dataset_namespace_id": "off-ca",
                    "display_name": "Open Food Facts Canada",
                    "license_id": "odbl-1.0",
                    "storage_boundary": "OPEN_SHARE_ALIKE",
                    "allowed_uses": [
                        "discovery",
                        "display",
                        "cache",
                        "index",
                        "mobile_app",
                        "retention_deletion",
                    ],
                    "authorization": {
                        "gates": [
                            {
                                "gate": gate,
                                "state": "SATISFIED",
                                "basis_id": f"test-{gate.lower()}",
                            }
                            for gate in DISCOVERY_GATES
                        ]
                    },
                },
                sort_keys=True,
            ),
            encoding="utf-8",
        )
        return path

    @staticmethod
    def make_keypair(root: Path) -> tuple[Path, Path]:
        private_key = root / "private.pem"
        public_key = root / "public.pem"
        subprocess.run(
            [
                "openssl",
                "genpkey",
                "-algorithm",
                "RSA",
                "-pkeyopt",
                "rsa_keygen_bits:2048",
                "-out",
                str(private_key),
            ],
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["openssl", "rsa", "-in", str(private_key), "-pubout", "-out", str(public_key)],
            check=True,
            capture_output=True,
        )
        return private_key, public_key


if __name__ == "__main__":
    unittest.main()

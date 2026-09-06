from __future__ import annotations

import json
import hashlib
import io
import unittest
from contextlib import redirect_stdout
from pathlib import Path

from tools.verify_offline_catalog_snapshot import main, verify_snapshot


class BundledOfflineCatalogAssetTest(unittest.TestCase):
    root = Path(__file__).resolve().parents[2]
    assets = root / "android" / "app" / "src" / "main" / "assets" / "offline_catalog"

    def test_both_metro_assets_are_signed_bounded_identity_only_snapshots(self):
        public_key = self.assets / "public-key.pem"
        source = self.assets / "sources" / "off-ca.jsonl"
        self.assertTrue(public_key.is_file())
        self.assertTrue(source.is_file())
        for region in ("ca-gta", "ca-metro-vancouver"):
            with self.subTest(region=region):
                snapshot = self.assets / region
                result = verify_snapshot(
                    snapshot,
                    public_key=public_key,
                    require_signature=True,
                    source_root=self.assets / "sources",
                )
                self.assertEqual(30_000, result["records"])
                self.assertEqual("VERIFIED", result["signatureState"])
                manifest = json.loads((snapshot / "manifest.json").read_text(encoding="utf-8"))
                self.assertEqual("IDENTITY_ONLY", manifest["catalogRole"])
                self.assertEqual("NOT_INCLUDED", manifest["coverage"]["currentOfferCoverage"])
                self.assertEqual(0, manifest["coverage"]["currentOfferRecordCount"])
                self.assertEqual(
                    manifest["sources"][0]["contentSha256"],
                    hashlib.sha256(source.read_bytes()).hexdigest(),
                )

    def test_bundled_source_has_no_offer_fields(self):
        source = self.assets / "sources" / "off-ca.jsonl"
        forbidden = {
            "price",
            "current_price",
            "quantity",
            "availability",
            "stock",
            "promotion",
            "store_id",
            "currency",
        }
        rows = [json.loads(line) for line in source.read_text(encoding="utf-8").splitlines()]
        self.assertEqual(30_000, len(rows))
        for row in rows:
            self.assertTrue(forbidden.isdisjoint(row))
            self.assertEqual("open-food-facts", row["providerId"])
            self.assertEqual("off-ca", row["datasetNamespaceId"])

    def test_cli_accepts_shared_source_root_for_bundled_snapshot(self):
        output = io.StringIO()
        with redirect_stdout(output):
            exit_code = main(
                [
                    "--snapshot",
                    str(self.assets / "ca-gta"),
                    "--public-key",
                    str(self.assets / "public-key.pem"),
                    "--require-signature",
                    "--source-root",
                    str(self.assets / "sources"),
                ]
            )
        self.assertEqual(0, exit_code)
        result = json.loads(output.getvalue())
        self.assertEqual(30_000, result["records"])
        self.assertEqual("VERIFIED", result["signatureState"])


if __name__ == "__main__":
    unittest.main()

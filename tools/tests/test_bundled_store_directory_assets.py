from __future__ import annotations

import json
import unittest
from pathlib import Path

from tools.verify_store_directory_snapshot import verify_snapshot


class BundledStoreDirectoryAssetTest(unittest.TestCase):
    root = Path(__file__).resolve().parents[2]
    assets = root / "android" / "app" / "src" / "main" / "assets" / "store_directory"

    def test_bundled_directory_is_signed_and_bounded_to_launch_regions(self):
        public_key = self.assets / "public-key.pem"
        self.assertTrue(public_key.is_file())
        result = verify_snapshot(self.assets, public_key=public_key, require_signature=True)
        self.assertEqual("VERIFIED", result["signatureState"])
        self.assertEqual(6_093, result["records"])
        self.assertEqual({"ca-gta": 4_311, "ca-metro-vancouver": 1_782}, result["regions"])

        manifest = json.loads((self.assets / "manifest.json").read_text(encoding="utf-8"))
        self.assertEqual("STORE_DIRECTORY", manifest["snapshotRole"])
        self.assertEqual("ODbL-1.0", manifest["source"]["licenseId"])
        self.assertEqual("OPEN_SHARE_ALIKE", manifest["source"]["storageBoundary"])
        self.assertEqual(0, manifest["coverage"]["currentOfferRecordCount"])
        self.assertEqual("NOT_INCLUDED", manifest["coverage"]["priceCoverage"])

    def test_bundled_rows_never_expose_offer_fields(self):
        forbidden = {
            "price",
            "currentPrice",
            "currency",
            "offer",
            "offerId",
            "promotion",
            "stock",
            "availability",
            "quantity",
            "packageQuantity",
            "unitPrice",
            "validFrom",
            "validTo",
        }
        rows = [
            json.loads(line)
            for line in (self.assets / "sources" / "openstreetmap-places.jsonl").read_text(encoding="utf-8").splitlines()
        ]
        self.assertEqual(6_093, len(rows))
        self.assertTrue(all(forbidden.isdisjoint(row) for row in rows))
        self.assertTrue(all(row["status"] == "LOCATION_ONLY" for row in rows))
        self.assertTrue(all(row["confidence"] == "SOURCE_LISTED" for row in rows))
        self.assertTrue(all(row["providerId"] == "openstreetmap" for row in rows))


if __name__ == "__main__":
    unittest.main()

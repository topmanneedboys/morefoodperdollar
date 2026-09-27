from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

from tools.audit_argentina_product_images import audit_images


class ArgentinaProductImageAuditTest(unittest.TestCase):
    def _fixture(self, root: Path) -> tuple[Path, Path]:
        catalog = root / "catalog.json"
        catalog.write_text(
            json.dumps(
                {
                    "schemaVersion": "fixture",
                    "records": [
                    {
                        "gtin": "0036000291452",
                        "name": "Whole Milk",
                        "brand": "Example Brand",
                        "quantity": {"unit": "GRAM", "value": "500"},
                        "productEvidenceKey": "identity-milk-a",
                    },
                    {
                        "gtin": "0036000291452",
                        "name": "Whole Milk",
                        "brand": "Example Brand",
                        "quantity": {"unit": "GRAM", "value": "500"},
                        "productEvidenceKey": "identity-milk-b",
                        },
                        {
                            "gtin": "4006381333931",
                            "name": "Yerba Mate",
                            "brand": "Mate Co",
                            "quantity": {"unit": "GRAM", "value": "1000"},
                        },
                        {
                            "gtin": "036000291469",
                            "name": "Unknown Size",
                            "brand": "Example Brand",
                            "quantity": {},
                        },
                    ],
                }
            ),
            encoding="utf-8",
        )
        export = root / "off.tsv.gz"
        import gzip

        with gzip.open(export, "wt", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=[
                    "code",
                    "product_name",
                    "product_name_en",
                    "brands",
                    "quantity",
                    "image_url",
                    "image_front_licenses",
                ],
                delimiter="\t",
                lineterminator="\n",
            )
            writer.writeheader()
            writer.writerows(
                [
                    {
                        "code": "036000291452",
                        "product_name": "Whole Milk",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_url": "https://images.openfoodfacts.org/images/products/036/000/291/452/front_en.1.400.jpg",
                        "image_front_licenses": "CC BY-SA 4.0",
                    },
                    {
                        "code": "4006381333931",
                        "product_name": "Yerba Mate",
                        "brands": "Mate Co",
                        "quantity": "1 kg",
                        "image_url": "https://images.openfoodfacts.org/images/products/400/638/133/3931/front_en.1.400.jpg",
                    },
                    {
                        "code": "036000291469",
                        "product_name": "Unknown Size",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_url": "https://images.openfoodfacts.org/images/products/036/000/291/469/front_en.1.400.jpg",
                    },
                    {
                        "code": "036000291452",
                        "product_name": "Different Milk",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_url": "https://images.openfoodfacts.org/images/products/036/000/291/452/front_en.2.400.jpg",
                    },
                    {
                        "code": "036000291452",
                        "product_name": "Whole Milk",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_url": "https://example.invalid/not-off.jpg",
                    },
                ]
            )
        return catalog, export

    def test_strict_gtin_variant_quantity_and_rights_are_separate(self):
        with tempfile.TemporaryDirectory() as tmp:
            catalog, export = self._fixture(Path(tmp))
            output = Path(tmp) / "report.json"
            report = audit_images(catalog, export, output)

            self.assertEqual(4, report["coverage"]["catalogValidGtinRecords"])
            self.assertEqual(3, report["coverage"]["catalogUniqueGtins"])
            self.assertEqual(4, report["coverage"]["catalogIdentitiesWithExactGtinMatch"])
            self.assertEqual(3, report["coverage"]["strictExactIdentities"])
            self.assertEqual(3, report["coverage"]["strictExactImageCandidates"])
            self.assertEqual(2, report["coverage"]["strictExactImageCandidatesWithPerImageLicense"])
            self.assertEqual(0, report["coverage"]["displayEligibleImages"])
            self.assertEqual(1, report["coverage"]["yerbaCatalogIdentities"])
            self.assertEqual(1, report["coverage"]["yerbaStrictExactImageCandidates"])
            self.assertEqual(1, report["rejections"]["catalog_quantity_unknown"])
            self.assertEqual(2, report["rejections"]["name_mismatch"])
            self.assertFalse(report["sourcePolicy"]["imagesDownloaded"])

    def test_repeat_run_is_byte_deterministic_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog, export = self._fixture(root)
            first = root / "first.json"
            second = root / "second.json"
            audit_images(catalog, export, first)
            audit_images(catalog, export, second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with self.assertRaisesRegex(ValueError, "Refusing to overwrite"):
                audit_images(catalog, export, first)


if __name__ == "__main__":
    unittest.main()

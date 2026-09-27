from __future__ import annotations

import csv
import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.exact_product_image_manifest import _compatible_license, build_manifest


class ExactProductImageManifestTest(unittest.TestCase):
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
                            "productEvidenceKey": "identity-milk",
                        },
                        {
                            "gtin": "4006381333931",
                            "name": "Yerba Mate",
                            "brand": "Mate Co",
                            "quantity": {"unit": "GRAM", "value": "1000"},
                            "productEvidenceKey": "identity-yerba",
                        },
                    ],
                }
            ),
            encoding="utf-8",
        )
        export = root / "off.tsv.gz"
        with gzip.open(export, "wt", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=[
                    "code",
                    "product_name",
                    "product_name_en",
                    "brands",
                    "quantity",
                    "image_front_url",
                    "image_ingredients_url",
                    "image_url",
                    "image_front_licenses",
                    "image_licenses",
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
                        "image_front_url": "https://images.openfoodfacts.org/images/products/036/000/291/452/front_es.1.400.jpg",
                        "image_ingredients_url": "https://images.openfoodfacts.org/images/products/036/000/291/452/ingredients_es.2.200.jpg",
                        "image_front_licenses": "CC BY-SA 4.0",
                        "image_licenses": "CC BY-SA 4.0",
                    },
                    {
                        "code": "4006381333931",
                        "product_name": "Yerba Mate",
                        "brands": "Mate Co",
                        "quantity": "1 kg",
                        "image_url": "https://static.openfoodfacts.org/images/products/400/638/133/3931/front_es.1.400.jpg",
                        "image_licenses": "CC BY 4.0",
                    },
                    {
                        "code": "036000291452",
                        "product_name": "Whole Milk",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_front_url": "https://example.invalid/not-authorized.jpg",
                        "image_front_licenses": "CC BY-SA 4.0",
                    },
                    {
                        "code": "036000291452",
                        "product_name": "Whole Milk",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_front_url": "https://images.openfoodfacts.org/images/products/036/000/291/452/front_en.3.400.jpg",
                        "image_front_licenses": "CC BY-NC-SA 4.0",
                    },
                    {
                        "code": "036000291452",
                        "product_name": "Whole Milk",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_front_url": "https://images.openfoodfacts.org/images/products/036/000/291/452/front_en.4.400.jpg",
                        "image_front_licenses": "CC BY-ND 4.0",
                    },
                    {
                        "code": "036000291452",
                        "product_name": "Whole Milk",
                        "brands": "Example Brand",
                        "quantity": "500 g",
                        "image_front_url": "https://images.openfoodfacts.org/images/products/036/000/291/452/front_en.5.400.jpg",
                    },
                ]
            )
        return catalog, export

    def test_roles_languages_gtin_normalization_and_explicit_rights(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            catalog, export = self._fixture(Path(tmp))
            output = Path(tmp) / "manifest.jsonl"
            report = build_manifest(catalog, export, output)

            self.assertEqual(6, report["counts"]["strict_identity_rows"])
            self.assertEqual(6, report["counts"]["valid_image_candidates"])
            self.assertEqual(3, report["counts"]["accepted_evidence_rows"])
            self.assertEqual({"CC-BY": 1, "CC-BY-SA": 2}, report["acceptedLicences"])
            self.assertEqual({"front": 2, "ingredients": 1}, report["acceptedRoles"])
            self.assertEqual({"es": 3}, report["acceptedLanguages"])
            self.assertEqual(1, report["rejections"]["IMAGE_URL_INVALID_OR_UNSUPPORTED_HOST"])
            self.assertEqual(1, report["rejections"]["RIGHTS_EVIDENCE_MISSING"])
            self.assertEqual(2, report["rejections"]["RIGHTS_LICENSE_INCOMPATIBLE"])
            self.assertIn("preserve attribution", report["obligations"])

            lines = output.read_text(encoding="utf-8").splitlines()
            self.assertEqual("manifestHeader", json.loads(lines[0])["recordType"])
            records = [json.loads(line) for line in lines[1:]]
            self.assertEqual(["identity-milk", "identity-milk", "identity-yerba"], [record["productEvidenceKey"] for record in records])
            self.assertEqual("0036000291452", records[0]["gtin"])
            self.assertTrue(all(record["priceOrAvailabilityClaim"] is False for record in records))

    def test_repeat_run_is_byte_deterministic_and_refuses_overwrite(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            catalog, export = self._fixture(root)
            first = root / "first.jsonl"
            second = root / "second.jsonl"
            first_report = build_manifest(catalog, export, first)
            second_report = build_manifest(catalog, export, second)
            self.assertEqual(first_report, second_report)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with self.assertRaisesRegex(ValueError, "Refusing to overwrite"):
                build_manifest(catalog, export, first)

    def test_restricted_creative_commons_variants_are_not_accepted(self) -> None:
        for value in (
            "CC BY-NC 4.0",
            "CC BY-NC-SA 4.0",
            "CC BY-ND 4.0",
            "https://creativecommons.org/licenses/by-nc/4.0/",
            "Creative Commons Attribution-NonCommercial 4.0",
            "Creative Commons Attribution-NoDerivatives 4.0",
        ):
            self.assertIsNone(_compatible_license(value), value)
        self.assertEqual(("CC-BY", True, False), _compatible_license("CC BY 4.0"))
        self.assertEqual(("CC-BY-SA", True, True), _compatible_license("CC BY-SA 4.0"))


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.import_open_food_facts_catalog import (
    OpenFoodFactsCatalogImportError,
    import_catalog,
)


class OpenFoodFactsCatalogImportTest(unittest.TestCase):
    def test_import_is_deterministic_catalog_only_and_source_scoped(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "products.jsonl"
            source.write_text(
                "".join(
                    json.dumps(row) + "\n"
                    for row in [
                        {
                            "code": "036000291452",
                            "product_name": "Whole milk",
                            "product_name_en": "Whole Milk",
                            "brands": "Example Brand",
                            "price": "9.99",
                            "availability": "in stock",
                        },
                        {
                            "code": "4006381333931",
                            "product_name": "Tea",
                            "brands": "Tea Co; Other Brand",
                            "quantity": "20 bags",
                        },
                    ]
                ),
                encoding="utf-8",
            )
            output = root / "canonical.jsonl"

            result = import_catalog(source, output, dataset_namespace_id="off-ca")

            self.assertEqual(2, result["records"])
            rows = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(["off:036000291452", "off:4006381333931"], [row["record_id"] for row in rows])
            self.assertEqual("Whole Milk", rows[0]["display_name"])
            self.assertEqual("off-ca", rows[0]["dataset_namespace_id"])
            self.assertEqual(
                {
                    "record_id",
                    "provider_id",
                    "dataset_namespace_id",
                    "provider_item_id",
                    "gtin",
                    "display_name",
                    "brand",
                    "aliases",
                },
                set(rows[0]),
            )
            self.assertNotIn("price", rows[0])
            self.assertNotIn("availability", rows[0])
            self.assertNotIn("quantity", rows[0])

            second = root / "canonical-second.jsonl"
            import_catalog(source, second, dataset_namespace_id="off-ca")
            self.assertEqual(output.read_bytes(), second.read_bytes())

    def test_invalid_gtin_missing_name_and_ambiguous_representation_fail_closed(self):
        cases = [
            ({"code": "036000291453", "product_name": "Bad"}, "invalid GTIN"),
            ({"code": "036000291452"}, "no usable product name"),
        ]
        for row, expected in cases:
            with self.subTest(expected=expected), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                source = root / "products.jsonl"
                source.write_text(json.dumps(row) + "\n", encoding="utf-8")
                with self.assertRaisesRegex(OpenFoodFactsCatalogImportError, expected):
                    import_catalog(source, root / "out.jsonl")

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "products.jsonl"
            source.write_text(
                "\n".join(
                    json.dumps({"code": code, "product_name": "Same product"})
                    for code in ("036000291452", "0036000291452")
                )
                + "\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(OpenFoodFactsCatalogImportError, "ambiguous GTIN representations"):
                import_catalog(source, root / "out.jsonl")

    def test_bounds_and_overwrite_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "products.jsonl"
            source.write_text(json.dumps({"code": "036000291452", "product_name": "Milk"}) + "\n", encoding="utf-8")
            output = root / "out.jsonl"
            output.write_text("existing\n", encoding="utf-8")
            with self.assertRaisesRegex(OpenFoodFactsCatalogImportError, "Refusing to overwrite"):
                import_catalog(source, output)
            with self.assertRaisesRegex(OpenFoodFactsCatalogImportError, "between 1 and 50000"):
                import_catalog(source, root / "other.jsonl", max_records=0)


if __name__ == "__main__":
    unittest.main()

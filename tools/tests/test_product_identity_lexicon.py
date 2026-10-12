from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.product_identity_lexicon import (
    POLICY_VERSION,
    SCHEMA_VERSION,
    build_family_clarification,
    build_identity_lexicon,
    derive_identity_record,
)


def _row(key: str, name: str, *, brand: str = "Marca") -> dict[str, object]:
    return {
        "brand": brand,
        "gtin": key.rsplit(":", 1)[-1],
        "name": name,
        "productEvidenceKey": key,
        "quantity": {"unit": "GRAM", "value": "500"},
    }


class ProductIdentityLexiconTests(unittest.TestCase):
    def test_direct_family_adds_curated_bilingual_aliases(self) -> None:
        value = derive_identity_record(_row("p:rice", "ARROZ LARGO FINCA 500 G"))
        self.assertEqual(value["classification"], "DIRECT")
        self.assertEqual(value["familyCandidates"][0]["family"], "rice")
        self.assertEqual(value["familyCandidates"][0]["role"], "DIRECT")
        aliases = {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]}
        self.assertIn(("arroz", "es", "CURATED_BILINGUAL"), aliases)
        self.assertIn(("rice", "en", "CURATED_BILINGUAL"), aliases)

    def test_reviewed_common_vocabulary_adds_family_aliases(self) -> None:
        value = derive_identity_record(_row("p:apple", "MANZANA ROJA 1 KG"))
        self.assertEqual(value["classification"], "DIRECT")
        self.assertEqual(value["familyCandidates"][0]["family"], "apple")
        aliases = {(item["value"], item["language"]) for item in value["aliases"]}
        self.assertIn(("manzana", "es"), aliases)
        self.assertIn(("apple", "en"), aliases)

    def test_mango_family_is_available_for_observed_forms(self) -> None:
        value = derive_identity_record(_row("p:mango-cookie", "GALLETAS DE MANGO 200 G"))
        mango = next(item for item in value["familyCandidates"] if item["family"] == "mango")
        self.assertEqual(mango["role"], "DERIVED")
        self.assertNotIn(
            ("mango", "en", "CURATED_BILINGUAL"),
            {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]},
        )

    def test_derived_rice_form_does_not_get_direct_rice_alias(self) -> None:
        value = derive_identity_record(_row("p:cake", "GALLETITAS DE ARROZ DULCES 100 G"))
        candidate = next(item for item in value["familyCandidates"] if item["family"] == "rice")
        self.assertEqual(candidate["family"], "rice")
        self.assertEqual(candidate["role"], "DERIVED")
        self.assertIn("dessert", candidate["observedForms"])
        self.assertNotIn(("rice", "en", "CURATED_BILINGUAL"), {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]})

    def test_carrot_forms_separate_food_drink_dessert_and_cosmetics(self) -> None:
        cases = (
            ("p:vegetable", "BABY CARROT", "DIRECT", "vegetable"),
            ("p:drink", "DISC:GRE JUICE MARKET HAPPY CARROT 500M", "DERIVED", "drink"),
            ("p:dessert", "POSTRE CARROT CAKE RIDERS X 140 GR", "DERIVED", "dessert"),
            ("p:cosmetics", "LOCION PROT. SOLAR FPS 30 CARROT HAWAIIAN BOT 240 ML", "DERIVED", "cosmetics"),
        )
        for key, name, role, form in cases:
            with self.subTest(name=name):
                value = derive_identity_record(_row(key, name))
                candidate = value["familyCandidates"]
                self.assertEqual(len(candidate), 1)
                self.assertEqual(candidate[0]["family"], "carrot")
                self.assertEqual(candidate[0]["role"], role)
                self.assertEqual(candidate[0]["observedForms"], [form])

        dessert = derive_identity_record(_row("p:dessert", "TORTA DE ZANAHORIA 400 G"))
        aliases = {(item["value"], item["language"], item["provenance"]) for item in dessert["aliases"]}
        self.assertIn(("carrot cake", "en", "CURATED_FORM"), aliases)
        self.assertIn(("postre de zanahoria", "es", "CURATED_FORM"), aliases)

    def test_carrot_clarification_exposes_observed_forms(self) -> None:
        candidates = [
            _row("p:vegetable", "BABY CARROT"),
            _row("p:drink", "DISC:GRE JUICE MARKET HAPPY CARROT 500M"),
            _row("p:dessert", "POSTRE CARROT CAKE RIDERS X 140 GR"),
            _row("p:cosmetics", "LOCION PROT. SOLAR FPS 30 CARROT HAWAIIAN BOT 240 ML"),
        ]
        value = build_family_clarification("carrot", candidates)
        self.assertIsNotNone(value)
        assert value is not None
        self.assertEqual(value["family"], "carrot")
        choices = {choice["id"]: choice for choice in value["choices"]}
        self.assertEqual(
            set(choices),
            {"carrot:vegetable", "carrot:drink", "carrot:dessert", "carrot:cosmetics"},
        )
        self.assertEqual(choices["carrot:vegetable"]["labelEn"], "Carrot vegetable")
        self.assertEqual(choices["carrot:drink"]["query"], "carrot juice")

    def test_rice_bar_is_a_derived_form(self) -> None:
        value = derive_identity_record(_row("p:bar", "BARRA DE ARROZ DULCE 12 G"))
        candidate = value["familyCandidates"][0]
        self.assertEqual(candidate["role"], "DERIVED")
        self.assertIn("dessert", candidate["observedForms"])

    def test_lychee_in_soap_is_not_fresh_fruit(self) -> None:
        value = derive_identity_record(_row("p:soap", "JABON LIQUIDO SANDIA Y LICHI 250 ML"))
        candidate = value["familyCandidates"][0]
        self.assertEqual(candidate["role"], "DERIVED")
        self.assertNotIn(("lychee", "en", "CURATED_BILINGUAL"), {(item["value"], item["language"], item["provenance"]) for item in value["aliases"]})

    def test_multiple_families_are_retained_for_clarification(self) -> None:
        value = derive_identity_record(_row("p:drink", "BEBIDA DE YERBA CON ARROZ"))
        self.assertEqual(value["classification"], "MULTIPLE_FAMILIES")
        families = {item["family"] for item in value["familyCandidates"]}
        self.assertEqual(families, {"rice", "yerba_mate"})
        self.assertIn("DERIVED", {item["role"] for item in value["familyCandidates"]})

    def test_stationery_brand_is_not_yerba_mate(self) -> None:
        value = derive_identity_record(_row("p:pen", "BOLIGRAFO PAPER MATE AZUL X1"))
        self.assertEqual(value["classification"], "UNCLASSIFIED")
        self.assertFalse(value["familyCandidates"])

    def test_family_clarification_uses_only_observed_rice_forms(self) -> None:
        candidates = [
            _row("p:grain", "ARROZ LARGO FINCA 500 G"),
            _row("p:cake", "GALLETITAS DE ARROZ DULCES 100 G"),
            _row("p:flour", "HARINA DE ARROZ 500 G"),
        ]
        value = build_family_clarification("rice", candidates)
        self.assertIsNotNone(value)
        assert value is not None
        self.assertEqual(value["code"], "PRODUCT_FAMILY_CLARIFICATION")
        self.assertEqual(value["family"], "rice")
        choices = {choice["id"]: choice for choice in value["choices"]}
        self.assertEqual(set(choices), {"rice:grain", "rice:dessert", "rice:flour"})
        self.assertEqual(choices["rice:grain"]["query"], "arroz largo")
        self.assertEqual(choices["rice:flour"]["labelEn"], "Rice flour")

    def test_family_clarification_is_absent_without_two_observed_forms(self) -> None:
        value = build_family_clarification("arroz", [_row("p:grain", "ARROZ LARGO FINCA 500 G")])
        self.assertIsNone(value)

    def test_family_clarification_does_not_invent_generic_family_forms(self) -> None:
        value = build_family_clarification("manzana", [_row("p:apple", "MANZANA ROJA 1 KG")])
        self.assertIsNone(value)

    def test_build_is_deterministic_and_writes_manifest(self) -> None:
        rows = [
            _row("p:rice", "ARROZ LARGO FINCA 500 G"),
            _row("p:cake", "GALLETITAS DE ARROZ DULCES 100 G"),
            _row("p:mate", "YERBA MATE SUAVE 1 KG"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "products.jsonl.gz"
            with gzip.open(source, "wt", encoding="utf-8", newline="") as handle:
                for row in rows:
                    handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
            output = root / "identity.jsonl.gz"
            manifest_path = root / "manifest.json"
            manifest = build_identity_lexicon(source, output, manifest_path)
            self.assertEqual(manifest["schemaVersion"], SCHEMA_VERSION)
            self.assertEqual(manifest["policyVersion"], POLICY_VERSION)
            self.assertEqual(manifest["identityCounts"]["records"], 3)
            self.assertEqual(manifest["source"]["recordCount"], 3)
            self.assertTrue(manifest["output"]["sha256"])
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                written = [json.loads(line) for line in handle if line.strip()]
            self.assertEqual(len(written), 3)
            self.assertEqual({item["productEvidenceKey"] for item in written}, {"p:rice", "p:cake", "p:mate"})
            self.assertEqual(json.loads(manifest_path.read_text(encoding="utf-8"))["families"]["rice"]["derivedCount"], 1)

    def test_new_source_rows_are_classified_on_each_build(self) -> None:
        row = _row("p:new-carrot", "ZANAHORIA FRESCA 1 KG")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "products.jsonl"
            source.write_text(json.dumps(row, ensure_ascii=False) + "\n", encoding="utf-8")
            output = root / "identity.jsonl.gz"
            manifest = build_identity_lexicon(source, output)
            self.assertEqual(manifest["source"]["recordCount"], 1)
            self.assertEqual(manifest["identityCounts"]["classifications"], {"DIRECT": 1})
            self.assertEqual(manifest["families"]["carrot"]["directCount"], 1)
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                identity = json.loads(handle.readline())
            self.assertEqual(identity["familyCandidates"][0]["observedForms"], ["vegetable"])

    def test_duplicate_keys_fail_closed(self) -> None:
        rows = [_row("p:same", "ARROZ"), _row("p:same", "ARROZ INTEGRAL")]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "products.jsonl"
            source.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate productEvidenceKey"):
                build_identity_lexicon(source, root / "identity.jsonl.gz")


if __name__ == "__main__":
    unittest.main()

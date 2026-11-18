from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.product_taxonomy import (
    ProductTaxonomyIndex,
    build_taxonomy_clarification,
    build_taxonomy_index,
    classify_name,
    filter_taxonomy_candidates,
    taxonomy_selection_form,
)


def _identity(provider: str, key: str, name: str, *, family: str | None = None) -> dict[str, object]:
    families = []
    if family:
        families.append({"family": family, "role": "DIRECT", "matchedTerms": [family], "observedForms": ["unspecified"]})
    return {
        "productEvidenceKey": key,
        "providerProductId": provider,
        "name": name,
        "brand": "Marca",
        "familyCandidates": families,
        "sourceEvidence": {"nameSamples": [name]},
    }


class ProductTaxonomyTests(unittest.TestCase):
    def test_index_loads_provider_and_identity_keys_and_keeps_sidecar_internal(self) -> None:
        record = {
            "providerProductId": "egg-1",
            "identityKeys": ["ar-sepa-product:1:egg-1"],
            "taxonomy": {
                "coverageStatus": "CLASSIFIED",
                "familyCandidates": [{"family": "egg", "observedForms": ["liquid"]}],
                "formCandidates": [{"key": "liquid", "labelEs": "Líquido", "labelEn": "Liquid"}],
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "taxonomy.jsonl.gz"
            with gzip.open(path, "wt", encoding="utf-8", newline="") as handle:
                handle.write(json.dumps(record, ensure_ascii=False) + "\n")
            index = ProductTaxonomyIndex.load(path)
            self.assertEqual(index.record_count, 1)
            values = index.annotate((
                {"productEvidenceKey": "ar-sepa-product:1:egg-1", "name": "HUEVOS LIQUIDOS"},
                {"productEvidenceKey": "missing", "name": "OTRO"},
            ))
            self.assertEqual(values[0]["_taxonomy"]["familyCandidates"][0]["family"], "egg")
            self.assertNotIn("_taxonomy", values[1])

    def test_classifies_departments_and_forms_from_source_terms(self) -> None:
        rice = classify_name("ARROZ LARGO FINCA 500 G", family_candidates=[{"family": "rice", "role": "DIRECT", "observedForms": ["grain"], "matchedTerms": ["arroz"]}])
        self.assertEqual(rice["coverageStatus"], "CLASSIFIED")
        self.assertEqual(rice["primaryDepartment"], "staples")
        self.assertIn("grain", {value["key"] for value in rice["formCandidates"]})
        eggs = classify_name("HUEVOS LIQUIDOS 500 ML")
        self.assertEqual(eggs["primaryDepartment"], "dairy")
        self.assertEqual(eggs["primarySubcategory"], "eggs")
        self.assertIn("liquid", {value["key"] for value in eggs["formCandidates"]})
        self.assertNotIn("whole_egg", {value["key"] for value in eggs["formCandidates"]})
        fresh_eggs = classify_name("HUEVOS FRESCOS X12")
        self.assertIn("whole_egg", {value["key"] for value in fresh_eggs["formCandidates"]})

    def test_exclusions_prevent_carrot_cosmetics_from_produce(self) -> None:
        value = classify_name("LOCION FACIAL CON CARROT 200 ML")
        departments = {item["key"] for item in value["departmentCandidates"]}
        self.assertIn("personal_care", departments)
        self.assertNotIn("fresh_produce", departments)
        self.assertIn("cosmetics", {item["key"] for item in value["formCandidates"]})

    def test_review_batch_classifies_observed_source_terms_without_cross_category_leakage(self) -> None:
        cases = [
            ("BATATA SUELTA", "fresh_produce"),
            ("DCE/BATATA ESNAOLA PERS", "snacks_sweets"),
            ("DCE.BATATA ESNAOLA LATA", "snacks_sweets"),
            ("DCE. BATATA EMMETH XKG", "snacks_sweets"),
            ("D BATATA ARCOR X KG.", "snacks_sweets"),
            ("TARTA DE CHOCLO CRESFOOD 250 GR", "prepared_food"),
            ("MOLDE NON STICK P/TARTA 28 CM.", None),
            ("DOY PACK LIMONADA C JENGIBRE Y MENTA X 450 GRS", "beverages"),
            ("BOOCH JENGIBRE-BURRITO 354cc", "beverages"),
            ("SODASTREAM FIZZ GO SAND", None),
            ("TE NEGRO C/JENGIBRE Y CANELA TWININGS CJA 10 SAQ 20 GRM", "beverages"),
            ("Pochoclos HANKS Cheddar x 44g", "snacks_sweets"),
            ("MUSLITOS C/CHEDDAR XKG SOLIMENO", "meat_seafood"),
            ("MENTHO-PLUS ACIDOS CEREZA X 27.2G", "health_supplements"),
            ("JENGIBRE MOLIDO BADIA x42.5g", "condiments"),
            ("ALBAHACA DESECADA MOLIDA", "condiments"),
            ("RAIZ DE JENGIBRE FRESCA X KG", "fresh_produce"),
            ("PEREJIL DESHIDRATADO", None),
            ("PAN BAGUETIN DE BATATA THE FERMENTO X4 UNI", "staples"),
            ("QUESO DE CABRA C/ALBAHACA LA HUERTA TAMBO X KGM", "dairy"),
            ("PIZZA MOZZARELLA 4P CRESFOOD 380 GR", "prepared_food"),
            ("SHAMPOO ROMERO ANTICAIDA HEAD SHOULDERS X 180 CC", "personal_care"),
            ("ACONDICIONAD. C/ROMERO Y MENTA OGX BOT 385 ML", "personal_care"),
            ("CHECK Q TYBO 200G", "dairy"),
            ("TAPA DE NALGA PROMOCION CJN-1-Kg", "meat_seafood"),
            ("ARVEJAS PARTIDAS X KG", "staples"),
            ("AJO EN POLVO BADIA 85,05g", "condiments"),
            ("JARRO MUGG EQUIPOS 1un", "home_goods"),
            ("ESCOBILLA P/BAÑO W.C. GLOW X 1 UNI", "household_cleaning"),
            ("TINTURA KOLESTON C/REACT.KIT 777MARRON", "personal_care"),
            ("TAPAS P/EMPANADAS OTTONELLO 420gr", "prepared_food"),
            ("GOMITAS SIMPLE vitalidad 240gr FCO-240-gr.", None),
            ("BULBO ALTA POTENCIA 20W E27 FRIA OSRAM", None),
            ("BULBOS CROCUS COL. VARIADOS", "garden_outdoor"),
            ("MANTA ALGODON WAFFLE", None),
            ("WAFFLE BELGA CRISTAL X 1 UNI", "prepared_food"),
            ("ALICATE P/UÑAS CUCHILLA CURVA BASICARE BLI 1 UNI", None),
            ("ALICATE MEDIA CANA CRF 6", "tools_hardware"),
            ("LIBRO COLORING DRAGONES 1un", "stationery"),
            ("LENTEJAS", "staples"),
            ("TAPA P PASCUALINA HOJALDRE HORNO TAPANDIL X 350 G", "prepared_food"),
            ("CERA LIQUIDA CARREFOUR X 490 CM3", "household_cleaning"),
            ("CONTROL REMOTO UNIVERSAL GENERAL ELECTRIC 4 DISPOS", "electronics"),
            ("CAMARA SEGURIDAD", "electronics"),
            ("CAFETERA FILTRO", "electronics"),
            ("CONSERVADORA SOPRANO", "home_goods"),
            ("CONTENEDOR OCUISIENE RED.T/ROJA 950cc", "home_goods"),
            ("CAPPUCCINO VIRGINIA", "beverages"),
            ("GROWLER VIDRIO AMBAR C TAPA 1950 ML DC", "home_goods"),
            ("CRAYONES CERA FABER CASTELL X12", None),
            ("MOUSE RETRACTIL MAXELL CONECTOR TIPO C", "electronics"),
            ("PALA CON CEPILLO", "household_cleaning"),
            ("AJO DESHIDRATADO POLVO", "condiments"),
            ("POLVO TAJIN CLASICO", "condiments"),
            ("FRUTOS SECOS", "snacks_sweets"),
            ("YOGURTERA SUONO 4 VASOS 180ML HOG0342", "electronics"),
            ("TOALLAS FEMENINAS DONCELLA PAQ-8-un.", "personal_care"),
            ("BALDE BLOQUES SAN REMO C/RUEDAS 25Pzas", "toys_games"),
            ("BALDE DE ARENA INTELIGENTE", "toys_games"),
            ("GUISO DE LENTEJAS X KG.", "prepared_food"),
            ("AUTO BURAGO A CONTROL REMOTO MERCEDES 1:24", "toys_games"),
            ("TERMOTANQUE SOLAR PEABODY 200 L + ANODO", "electronics"),
            ("MOUSE PAD LOGITECH 200X230MM NEGRO", "electronics"),
        ]
        for name, expected in cases:
            with self.subTest(name=name):
                self.assertEqual(classify_name(name)["primaryDepartment"], expected)

    def test_generic_egg_clarification_uses_only_observed_forms(self) -> None:
        candidates = [
            {"productEvidenceKey": "egg-fresh", "name": "HUEVOS FRESCOS X12", "brand": "Marca"},
            {"productEvidenceKey": "egg-liquid", "name": "HUEVOS LIQUIDOS 500 ML", "brand": "Marca"},
            {"productEvidenceKey": "egg-powder", "name": "HUEVO EN POLVO 100 G", "brand": "Marca"},
        ]
        value = build_taxonomy_clarification("huevos", candidates)
        self.assertIsNotNone(value)
        assert value is not None
        self.assertEqual(value["family"], "egg")
        self.assertEqual(
            {choice["id"] for choice in value["choices"]},
            {"egg:liquid", "egg:powder", "egg:whole_egg"},
        )
        self.assertEqual(value["choices"][0]["id"], "egg:whole_egg")
        filtered = filter_taxonomy_candidates("huevos liquid", candidates)
        self.assertEqual([item["productEvidenceKey"] for item in filtered], ["egg-liquid"])
        self.assertEqual(taxonomy_selection_form("huevos", "egg:liquid"), ("egg", "liquid"))
        selected = filter_taxonomy_candidates("huevos", candidates, selection_id="egg:liquid")
        self.assertEqual([item["productEvidenceKey"] for item in selected], ["egg-liquid"])

    def test_generic_clarification_is_absent_for_one_observed_form(self) -> None:
        value = build_taxonomy_clarification(
            "huevos",
            [{"productEvidenceKey": "egg-fresh", "name": "HUEVOS FRESCOS X12", "brand": "Marca"}],
        )
        self.assertIsNone(value)

    def test_explicit_selection_does_not_fall_back_when_membership_is_empty(self) -> None:
        candidates = [
            {"productEvidenceKey": "egg-fresh", "name": "HUEVOS FRESCOS X12", "brand": "Marca"},
        ]
        selected = filter_taxonomy_candidates("huevos", candidates, selection_id="egg:powder")
        self.assertEqual(selected, ())

    def test_builder_groups_store_identities_by_provider_product(self) -> None:
        rows = [
            _identity("egg-1", "ar-sepa-product:1:egg-1", "HUEVOS FRESCOS X12"),
            _identity("egg-1", "ar-sepa-product:2:egg-1", "HUEVOS LIQUIDOS 500 ML"),
            _identity("rice-1", "ar-sepa-product:1:rice-1", "ARROZ LARGO 1 KG", family="rice"),
            _identity("unknown-1", "ar-sepa-product:1:unknown-1", "PRODUCTO SIN DESCRIPCION"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "identity.jsonl.gz"
            with gzip.open(source, "wt", encoding="utf-8", newline="") as handle:
                for row in rows:
                    handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
            output = root / "taxonomy.jsonl.gz"
            report_path = root / "report.json"
            review = root / "review.jsonl.gz"
            report = build_taxonomy_index(source, output, report_path, review)
            self.assertEqual(report["coverage"]["providerProductIds"], 3)
            self.assertTrue(report["safety"]["everyProviderProductHasCoverageStatus"])
            self.assertEqual(report["coverage"]["reviewQueueRecords"], 1)
            with gzip.open(output, "rt", encoding="utf-8") as handle:
                records = [json.loads(line) for line in handle if line.strip()]
            self.assertEqual(len(records), 3)
            egg = next(value for value in records if value["providerProductId"] == "egg-1")
            self.assertEqual(egg["identityCount"], 2)
            self.assertIn("liquid", {value["key"] for value in egg["taxonomy"]["formCandidates"]})
            self.assertIn("whole_egg", {value["key"] for value in egg["taxonomy"]["formCandidates"]})

    def test_builder_rejects_missing_provider_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "identity.jsonl"
            source.write_text(json.dumps({"name": "ARROZ"}) + "\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "providerProductId"):
                build_taxonomy_index(source, root / "taxonomy.jsonl", root / "report.json")

    def test_subcategory_tree_emits_children_only_under_observed_parents(self) -> None:
        cases = {
            "Naranja Fresca 1 kg": ("citrus", "fresh_produce"),
            "Vodka 700 ml": ("spirits", "beverages"),
            "Arroz Largo Fino 1 kg": ("rice", "staples"),
            "Queso Cremoso 500 g": ("cheese", "dairy"),
            "Detergente Limon 500 ml": ("detergent", "household_cleaning"),
            "Remera Algodon M": ("tops", "clothing"),
        }
        for name, (subcategory, department) in cases.items():
            with self.subTest(name=name):
                result = classify_name(name)
                self.assertEqual(result["primarySubcategory"], subcategory)
                parents = {item["key"] for item in result["departmentCandidates"]}
                self.assertIn(department, parents)
                self.assertTrue(
                    all(item["parent"] in parents for item in result["subcategoryCandidates"]),
                    "a subcategory was emitted under an unobserved department",
                )

    def test_category_counts_summarize_the_observed_tree(self) -> None:
        index = ProductTaxonomyIndex({
            "a": {
                "taxonomy": {
                    "primaryDepartment": "dairy",
                    "departmentCandidates": [{"key": "dairy", "labelEs": "Lácteos", "labelEn": "Dairy"}],
                    "primarySubcategory": "milk",
                    "subcategoryCandidates": [{"key": "milk", "labelEs": "Leches", "labelEn": "Milk"}],
                }
            },
            "b": {
                "taxonomy": {
                    "primaryDepartment": "dairy",
                    "departmentCandidates": [{"key": "dairy", "labelEs": "Lácteos", "labelEn": "Dairy"}],
                    "primarySubcategory": "eggs",
                    "subcategoryCandidates": [{"key": "eggs", "labelEs": "Huevos", "labelEn": "Eggs"}],
                }
            },
            "c": {
                "taxonomy": {
                    "coverageStatus": "REVIEW_REQUIRED",
                    "departmentCandidates": [],
                    "subcategoryCandidates": [],
                }
            },
        })

        counts = index.category_counts()

        self.assertEqual(counts["productsWithDepartment"], 2)
        self.assertEqual(counts["productsWithSubcategory"], 2)
        self.assertEqual(counts["departments"][0]["key"], "dairy")
        self.assertEqual(counts["departments"][0]["count"], 2)
        subcategory_counts = {item["key"]: item["count"] for item in counts["subcategories"]}
        self.assertEqual(subcategory_counts, {"milk": 1, "eggs": 1})


if __name__ == "__main__":
    unittest.main()

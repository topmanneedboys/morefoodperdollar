from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from decimal import Decimal

from tools.consumer_input_intelligence import (
    AMBIGUOUS_PRODUCT,
    DIMENSION_MISMATCH,
    MULTIPLE_PLAUSIBLE_PRODUCTS,
    NEEDS_CLARIFICATION,
    NO_SAFE_MATCH,
    PACKAGE_SIZE_REQUIRED,
    QUANTITY_REQUIRED,
    RESOLVED_ALIAS,
    RESOLVED_EXACT,
    RESOLVED_SAFE_CORRECTION,
    CatalogIndex,
    CatalogRecord,
    ConsumerInputInterpreter,
    normalize_text,
    parse_quantity,
    sqlite_fts5_trigram_supported,
)
from tools.build_argentina_input_vocabulary import build_artifact


def fixture_records() -> list[dict[str, object]]:
    """Small, source-shaped identity fixture; no prices or availability."""

    def item(key: str, name: str, quantity: str, unit: str, *, brand: str | None = None, form: str | None = None) -> dict[str, object]:
        return {
            "productEvidenceKey": key,
            "name": name,
            "brand": brand,
            "form": form,
            "quantity": {"value": quantity, "unit": unit},
            "canonicalSearchAliases": [],
            "provenance": {"source": "fixture", "sourceRow": key},
        }

    return [
        item("rice", "Arroz Largo Fino 1 kg", "1", "kg"),
        item("butter", "Manteca Sin Sal 500 g", "500", "g"),
        item("milk", "Leche Entera 1 L", "1", "l"),
        item("tomato-fresh", "Tomate Fresco 1 kg", "1", "kg", form="fresh"),
        item("tomato-paste", "Pasta de Tomate 500 g", "500", "g", form="paste"),
        item("apple", "Manzana Roja 1 kg", "1", "kg", form="fresh"),
        item("apple-juice", "Jugo de Manzana 1 L", "1", "l", form="juice"),
        item("orange", "Naranja Fresca 1 kg", "1", "kg", form="fresh"),
        item("orange-soda", "Gaseosa Naranja 2 L", "2", "l", form="soda"),
        item("chicken", "Pollo Fresco 1 kg", "1", "kg", form="fresh"),
        item("chicken-broth", "Caldo Sabor Pollo 1 L", "1", "l", form="broth"),
        item("milk-cosmetic", "Crema Corporal 250 ml", "250", "ml", form="cosmetic"),
        item("coconut-oil", "Aceite de Coco 500 ml", "500", "ml", form="oil"),
        item("coconut-shampoo", "Shampoo Coco 500 ml", "500", "ml", form="cosmetic"),
        item("rice-seasoning", "Condimento para Arroz 100 g", "100", "g", form="seasoning"),
        item("eggs", "Huevos Grandes 12", "12", "count", form="fresh"),
        item("egg-pasta", "Pasta de Huevo 500 g", "500", "g", form="pasta"),
        item("cheese", "Queso Cremoso 500 g", "500", "g", form="fresh"),
        item("ravioli", "Ravioli Relleno 500 g", "500", "g", form="filled"),
        item("cereal", "Cereal de Maíz 500 g", "500", "g", form="dry"),
        item("yogurt-cereal", "Yogur Cereal 500 g", "500", "g", form="yogurt"),
        item("sugar", "Azúcar Blanca 1 kg", "1", "kg", form="dry"),
        item("sugar-soda", "Gaseosa Sin Azúcar 2 L", "2", "l", form="soda"),
        item("coca", "Coca-Cola Original 2.25 L", "2.25", "l", brand="Coca-Cola", form="soda"),
        item("coca-zero", "Coca-Cola Zero 2.25 L", "2.25", "l", brand="Coca-Cola", form="soda"),
        item("sprite", "Sprite 2.25 L", "2.25", "l", brand="Sprite", form="soda"),
    ]


class ConsumerInputIntelligenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = CatalogIndex.from_records(fixture_records())
        cls.interpreter = ConsumerInputInterpreter(cls.catalog)

    def line(self, text: str, *, require: bool = True) -> dict[str, object]:
        return self.interpreter.interpret(text, require_quantities=require)["lines"][0]

    def test_normalization_preserves_numbers_and_accents(self):
        self.assertEqual(normalize_text("  Mi\u00e9rcoles\u20142,25L!!  "), "miercoles 2.25 l")
        self.assertEqual(normalize_text("\u00bd kg caf\u00e9"), "0.5 kg cafe")
        self.assertEqual(normalize_text("1kg"), "1 kg")
        self.assertEqual(normalize_text("2.25 L"), "2.25 l")

    def test_aliases_brands_and_safe_corrections(self):
        self.assertEqual(self.line("arroz 1kg")["resolution"], RESOLVED_ALIAS)
        self.assertEqual(self.line("rice 1kg")["resolution"], RESOLVED_ALIAS)
        self.assertEqual(self.line("aroz 1kg")["resolution"], RESOLVED_SAFE_CORRECTION)
        self.assertEqual(self.line("rise 1kg")["resolution"], RESOLVED_SAFE_CORRECTION)
        self.assertEqual(self.line("milkk 1l")["resolution"], RESOLVED_SAFE_CORRECTION)
        self.assertEqual(self.line("buter 500g")["resolution"], RESOLVED_SAFE_CORRECTION)
        self.assertEqual(self.line("tomte 1kg")["resolution"], RESOLVED_SAFE_CORRECTION)
        self.assertEqual(self.line("coka cola 2.25l")["recognizedProduct"]["brand"], "Coca-Cola")
        self.assertEqual(self.line("coke 2.25l")["recognizedProduct"]["brand"], "Coca-Cola")
        self.assertEqual(self.line("sprit 2.25l")["recognizedProduct"]["brand"], "Sprite")
        self.assertEqual(self.line("sprtie 2.25l")["recognizedProduct"]["brand"], "Sprite")

    def test_tomato_form_gate_and_explicit_forms(self):
        bare = self.line("tomate 1kg")
        self.assertEqual(bare["recognizedProduct"]["productEvidenceKey"], "tomato-fresh")
        self.assertNotEqual(bare["recognizedProduct"]["productEvidenceKey"], "tomato-paste")
        explicit = self.line("pasta de tomate 500g")
        self.assertEqual(explicit["recognizedProduct"]["productEvidenceKey"], "tomato-paste")
        self.assertIn(self.line("tomate pasta 500g")["recognizedProduct"]["productEvidenceKey"], {"tomato-paste"})

    def test_high_risk_traps_never_auto_substitute(self):
        trap_queries = (
            "apple juice 1l",
            "orange soda 2l",
            "chicken flavour 1kg",
            "chicken broth 1l",
            "milk cosmetics 250ml",
            "coconut shampoo 500ml",
            "rice seasoning 100g",
            "egg pasta 500g",
            "cheese ravioli 500g",
            "cereal yogurt 500g",
            "sugar free soda 2l",
        )
        for query in trap_queries:
            with self.subTest(query=query):
                result = self.line(query)
                self.assertIn(result["resolution"], {NO_SAFE_MATCH, NEEDS_CLARIFICATION})
                if result.get("recognizedProduct"):
                    # A result may be shown only as a clarification suggestion;
                    # it must never be silently turned into a structured line.
                    self.assertIsNone(result.get("structuredLine"))

    def test_variants_negation_and_wrong_dimension(self):
        zero = self.line("coca zero 2.25l")
        self.assertEqual(zero["recognizedProduct"]["productEvidenceKey"], "coca-zero")
        not_zero = self.line("no zero coca 2.25l")
        self.assertEqual(not_zero["recognizedProduct"]["productEvidenceKey"], "coca")
        wrong = self.line("1 kg Coca-Cola")
        self.assertEqual(wrong["clarification"]["code"], DIMENSION_MISMATCH)
        self.assertIsNone(wrong["structuredLine"])

    def test_quantity_forms_are_exact(self):
        self.assertEqual(parse_quantity("2 kg").total.base_amount, Decimal("2000"))
        self.assertEqual(parse_quantity("500 g").total.base_amount, Decimal("500"))
        self.assertEqual(parse_quantity("1,5 L").total.base_amount, Decimal("1500"))
        self.assertEqual(parse_quantity("250 cc").total.base_amount, Decimal("250"))
        self.assertEqual(parse_quantity("1 lb").total.base_amount, Decimal("453.59237"))
        self.assertEqual(parse_quantity("media docena").total.base_amount, Decimal("6"))
        self.assertEqual(parse_quantity("docena").total.base_amount, Decimal("12"))
        self.assertEqual(parse_quantity("2 x 2.25 L").total.base_amount, Decimal("4500"))
        self.assertEqual(parse_quantity("1/2 kg").total.base_amount, Decimal("500"))
        self.assertEqual(parse_quantity("\u00bd kg").total.base_amount, Decimal("500"))
        self.assertEqual(self.line("2 Sprite")["clarification"]["code"], PACKAGE_SIZE_REQUIRED)
        self.assertEqual(self.line("sprite")["clarification"]["code"], QUANTITY_REQUIRED)

    def test_multi_item_segmentation_and_bounds(self):
        result = self.interpreter.interpret("necesito 2kg rice, 1 lt milkk, buter 500g and tomato 1kg", require_quantities=True)
        self.assertEqual(result["lineCount"], 4)
        self.assertTrue(result["safeRequestReady"])
        self.assertEqual([line["resolution"] for line in result["lines"]], [RESOLVED_ALIAS, RESOLVED_SAFE_CORRECTION, RESOLVED_SAFE_CORRECTION, RESOLVED_ALIAS])
        result = self.interpreter.interpret("coca-cola zero 2.25l", require_quantities=True)
        self.assertEqual(result["lineCount"], 1)
        self.assertEqual(result["lines"][0]["recognizedProduct"]["productEvidenceKey"], "coca-zero")
        with self.assertRaises(ValueError):
            self.interpreter.interpret(";".join("rice 1kg" for _ in range(11)), require_quantities=True)

    def test_deterministic_repeat_and_catalog_bound(self):
        first = self.interpreter.interpret("aroz 1kg, sprit 2.25l", require_quantities=True)
        second = self.interpreter.interpret("aroz 1kg, sprit 2.25l", require_quantities=True)
        first["diagnostics"]["totalMs"] = None
        second["diagnostics"]["totalMs"] = None
        self.assertEqual(first, second)
        self.assertEqual(self.catalog.as_manifest()["recordCount"], len(fixture_records()))
        self.assertIsInstance(sqlite_fts5_trigram_supported(), bool)

    def test_mutation_corpus_has_frozen_hash_and_no_unsafe_auto_resolution(self):
        # A deterministic development/holdout corpus exercises bounded edits,
        # accent loss, joins and transpositions without claiming independent
        # human labels.  The hash protects accidental fixture drift.
        seeds = ("arroz", "manteca", "leche", "tomate", "sprite", "coca cola")
        mutations: list[tuple[str, str]] = []
        for seed in seeds:
            for index in range(500):
                mode = index % 5
                if mode == 0:
                    value = seed.replace("a", "") or seed
                elif mode == 1:
                    value = seed + seed[-1]
                elif mode == 2 and len(seed) > 2:
                    value = seed[:1] + seed[2] + seed[1] + seed[3:]
                elif mode == 3:
                    value = seed.replace(" ", "")
                else:
                    value = seed.replace("e", "é")
                mutations.append((seed, value))
        corpus_bytes = "\n".join(f"{seed}\t{value}" for seed, value in mutations).encode("utf-8")
        self.assertEqual(len(mutations), 3000)
        self.assertEqual(hashlib.sha256(corpus_bytes).hexdigest(), "ee98484fc9714023854f9e68148dff5189601926139ee6dfc9159effe33e1d1d")
        dev = mutations[:2100]
        holdout = mutations[2100:]
        self.assertEqual(len(dev), 2100)
        self.assertEqual(len(holdout), 900)
        for seed, value in holdout[:120]:
            result = self.interpreter.interpret(f"{value} 1kg", require_quantities=True)
            self.assertTrue(result["lines"])
            line = result["lines"][0]
            self.assertNotEqual(line["resolution"], RESOLVED_EXACT)
            if line["resolution"] in {RESOLVED_ALIAS, RESOLVED_SAFE_CORRECTION}:
                self.assertIsNotNone(line.get("correction"))

    def test_vocabulary_jsonl_builder_input_is_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.jsonl"
            path.write_text("\n".join(json.dumps(value, ensure_ascii=False) for value in fixture_records()) + "\n", encoding="utf-8")
            output = Path(directory) / "nested" / "vocabulary.json"
            manifest = build_artifact(path, output, provider="fixture", release_date="2026-09-06", source_sha256="fixture-sha")
            self.assertEqual(manifest["vocabulary"]["recordCount"], len(fixture_records()))
            self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["completionState"], "COMPLETE")
            duplicate = Path(directory) / "duplicate.jsonl"
            duplicate.write_text(json.dumps(fixture_records()[0]) + "\n" + json.dumps(fixture_records()[0]) + "\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                build_artifact(duplicate, Path(directory) / "duplicate.json", provider="fixture", release_date="2026-09-06")


if __name__ == "__main__":
    unittest.main()

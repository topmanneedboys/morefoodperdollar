from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.product_taxonomy import (
    build_taxonomy_clarification,
    build_taxonomy_index,
    classify_name,
    filter_taxonomy_candidates,
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
    def test_classifies_departments_and_forms_from_source_terms(self) -> None:
        rice = classify_name("ARROZ LARGO FINCA 500 G", family_candidates=[{"family": "rice", "role": "DIRECT", "observedForms": ["grain"], "matchedTerms": ["arroz"]}])
        self.assertEqual(rice["coverageStatus"], "CLASSIFIED")
        self.assertEqual(rice["primaryDepartment"], "staples")
        self.assertIn("grain", {value["key"] for value in rice["formCandidates"]})
        eggs = classify_name("HUEVOS LIQUIDOS 500 ML")
        self.assertEqual(eggs["primaryDepartment"], "staples")
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
        filtered = filter_taxonomy_candidates("huevos liquid", candidates)
        self.assertEqual([item["productEvidenceKey"] for item in filtered], ["egg-liquid"])

    def test_generic_clarification_is_absent_for_one_observed_form(self) -> None:
        value = build_taxonomy_clarification(
            "huevos",
            [{"productEvidenceKey": "egg-fresh", "name": "HUEVOS FRESCOS X12", "brand": "Marca"}],
        )
        self.assertIsNone(value)

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


if __name__ == "__main__":
    unittest.main()

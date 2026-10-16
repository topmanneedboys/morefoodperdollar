from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.catalog_discovery import DiscoveryIndex, DiscoveryQuery, build_discovery_index


def _row(number: int, name: str, *, family: str = "rice", form: str = "grain", provider: str | None = None) -> dict[str, object]:
    provider_id = provider or f"provider-{number:03d}"
    return {
        "productEvidenceKey": f"ar-sepa-product:synthetic:{number:03d}",
        "providerProductId": provider_id,
        "name": name,
        "brand": "Marca",
        "familyCandidates": [
            {
                "family": family,
                "matchedTerms": ["arroz" if family == "rice" else family],
                "observedForms": [form],
                "role": "DIRECT",
            }
        ],
        "aliases": [],
        "sourceEvidence": {"nameSamples": [name]},
    }


class CatalogDiscoveryIndexTests(unittest.TestCase):
    def _build(self, directory: str, rows: list[dict[str, object]]) -> Path:
        source = Path(directory) / "identities.jsonl"
        with source.open("w", encoding="utf-8", newline="\n") as handle:
            for row in rows:
                handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
        output = Path(directory) / "discovery.sqlite"
        report = build_discovery_index(source, output, release_id="synthetic-v1")
        self.assertEqual(report.identity_records, len(rows))
        self.assertEqual(report.provider_products, len({row["providerProductId"] for row in rows}))
        return output

    def test_full_set_facets_and_strict_selection_are_independent_of_page_size(self) -> None:
        rows = [_row(i, f"ARROZ LARGO MARCA {i} X 1 KG") for i in range(40)]
        rows.extend(
            [
                _row(100, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
                _row(101, "BEBIDA DE ARROZ MARCA X 1 L", form="drink"),
                _row(102, "ARROZ PARA HARINA MARCA X 500 G", form="flour"),
            ]
        )
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                first = index.query(DiscoveryQuery("rice", language="en", page_size=7, facet_page_size=10)).to_dict()
                self.assertEqual(first["total"]["productCount"], 43)
                self.assertEqual(first["total"]["sourceIdentityCount"], 43)
                self.assertEqual(first["status"], "NEEDS_CLARIFICATION")
                choice_ids = {choice["id"] for choice in first["clarification"]["choices"]}
                self.assertIn("form:rice:grain", choice_ids)
                self.assertIn("form:rice:dessert", choice_ids)

                seen: list[str] = []
                cursor = None
                while True:
                    page = index.query(DiscoveryQuery("rice", language="en", page_size=7, cursor=cursor)).to_dict()
                    seen.extend(item["identityKey"] for item in page["matches"])
                    cursor = page["pagination"]["nextCursor"]
                    if not cursor:
                        break
                self.assertEqual(len(seen), 43)
                self.assertEqual(len(set(seen)), 43)

                dessert = index.query(
                    DiscoveryQuery("rice", language="en", selections=("form:rice:dessert",), page_size=20)
                ).to_dict()
                self.assertEqual(dessert["status"], "READY")
                self.assertEqual(dessert["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in dessert["matches"]], ["GALLETAS DE ARROZ MARCA X 100 G"])

                empty = index.query(
                    DiscoveryQuery("rice", language="en", selections=("form:rice:unspecified",), page_size=20)
                ).to_dict()
                self.assertEqual(empty["status"], "NO_MATCHES")
                self.assertEqual(empty["total"]["productCount"], 0)
                self.assertEqual(empty["matches"], [])

    def test_final_facet_page_with_one_choice_remains_reachable(self) -> None:
        rows = [
            _row(1, "ARROZ LARGO MARCA X 1 KG", form="grain"),
            _row(2, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
            _row(3, "BEBIDA DE ARROZ MARCA X 1 L", form="drink"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                first = index.query(
                    DiscoveryQuery("rice", language="en", facet_page_size=2)
                ).to_dict()
                self.assertIsNotNone(first["clarification"]["nextCursor"])
                second = index.query(
                    DiscoveryQuery(
                        "rice",
                        language="en",
                        facet_page_size=2,
                        facet_cursor=first["clarification"]["nextCursor"],
                    )
                ).to_dict()
                self.assertEqual(len(second["clarification"]["choices"]), 1)
                self.assertIsNone(second["clarification"]["nextCursor"])

    def test_bilingual_aliases_resolve_to_the_same_predicate_set(self) -> None:
        rows = [_row(1, "ARROZ LARGO MARCA X 1 KG"), _row(2, "RICE LONG GRAIN BRAND X 1 KG")]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                spanish = index.query(DiscoveryQuery("arroz", language="es", page_size=10)).to_dict()
                english = index.query(DiscoveryQuery("rice", language="en", page_size=10)).to_dict()
                self.assertEqual(spanish["resolvedPredicates"], english["resolvedPredicates"])
                self.assertEqual(
                    {item["identityKey"] for item in spanish["matches"]},
                    {item["identityKey"] for item in english["matches"]},
                )

    def test_bowl_query_keeps_derived_appliance_mentions_out_of_strict_results(self) -> None:
        direct = _row(1, "BOWL CERAMICA CAROL 20 CM", family="bowl", form="unspecified")
        derived = _row(2, "BATIDORA CON BOWL BLACK DECKER 500W", family="bowl", form="unspecified")
        derived["familyCandidates"][0]["role"] = "DERIVED"
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, [direct, derived])
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("bowl", language="es", page_size=10)).to_dict()
                self.assertEqual(result["status"], "READY")
                self.assertEqual(result["resolvedPredicates"], ["type:bowl"])
                self.assertEqual(result["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in result["matches"]], ["BOWL CERAMICA CAROL 20 CM"])

    def test_identical_es_en_aliases_are_seeded_in_both_languages(self) -> None:
        rows = [
            _row(1, "WHISKY IMPORTADO X 750 CC", family="whisky", form="unspecified"),
            _row(2, "VODKA IMPORTADO X 700 CC", family="vodka", form="unspecified"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                spanish = index.query(DiscoveryQuery("whisky", language="es", page_size=10)).to_dict()
                english = index.query(DiscoveryQuery("whisky", language="en", page_size=10)).to_dict()
                self.assertEqual(spanish["resolvedPredicates"], ["type:whisky"])
                self.assertEqual(english["resolvedPredicates"], ["type:whisky"])
                self.assertEqual(spanish["total"]["productCount"], 1)
                self.assertEqual(english["total"]["productCount"], 1)

    def test_generic_family_form_aliases_resolve_bilingually(self) -> None:
        rows = [
            _row(1, "SAL FINA MARCA X 500 G", family="salt", form="fine"),
            _row(2, "SAL GRUESA MARCA X 1 KG", family="salt", form="coarse"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                spanish = index.query(DiscoveryQuery("sal fina", language="es", page_size=10)).to_dict()
                english = index.query(DiscoveryQuery("salt fine", language="en", page_size=10)).to_dict()
                for result in (spanish, english):
                    self.assertEqual(result["status"], "READY")
                    self.assertEqual(result["resolvedPredicates"], ["form:salt:fine"])
                    self.assertEqual(result["total"]["productCount"], 1)
                    self.assertEqual([item["displayName"] for item in result["matches"]], ["SAL FINA MARCA X 500 G"])

    def test_v12_generic_food_form_aliases_resolve_to_observed_predicates(self) -> None:
        rows = [
            _row(1, "ALFAJOR CHOCOLATE BLANCO MARCA X 60 G", family="alfajor", form="chocolate"),
            _row(2, "HARINA DE TRIGO MARCA X 1 KG", family="flour", form="wheat"),
            _row(3, "MANI TOSTADO MARCA X 500 G", family="peanut", form="whole"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                checks = (
                    ("alfajor chocolate", "es", "form:alfajor:chocolate"),
                    ("flour wheat", "en", "form:flour:wheat"),
                    ("mani tostado", "es", "form:peanut:whole"),
                )
                for query, language, predicate in checks:
                    with self.subTest(query=query):
                        result = index.query(DiscoveryQuery(query, language=language, page_size=10)).to_dict()
                        self.assertEqual(result["status"], "READY")
                        self.assertEqual(result["resolvedPredicates"], [predicate])
                        self.assertEqual(result["total"]["productCount"], 1)
                        self.assertIsNone(result["clarification"])

    def test_explicit_form_does_not_reopen_sibling_family_choices(self) -> None:
        rows = [
            _row(1, "ALFAJOR CHOCOLATE CON ARROZ MARCA X 60 G", family="alfajor", form="chocolate"),
            _row(2, "ALFAJOR DE ARROZ MARCA X 60 G", family="alfajor", form="rice"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("alfajor chocolate", language="es", page_size=10)).to_dict()
                self.assertEqual(result["status"], "READY")
                self.assertEqual(result["resolvedPredicates"], ["form:alfajor:chocolate"])
                self.assertIsNone(result["clarification"])

    def test_literal_only_rows_remain_searchable_and_unknown_selection_is_rejected(self) -> None:
        rows = [
            _row(1, "ARROZ LARGO MARCA X 1 KG"),
            {
                "productEvidenceKey": "ar-sepa-product:synthetic:999",
                "providerProductId": "provider-999",
                "name": "PRODUCTO RARO SIN CLASIFICAR 1 UN",
                "brand": "S/D",
                "familyCandidates": [],
                "aliases": [],
            },
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                literal = index.query(DiscoveryQuery("raro", language="es", page_size=10)).to_dict()
                self.assertEqual(literal["status"], "READY")
                self.assertEqual(literal["total"]["productCount"], 1)
                with self.assertRaises(ValueError):
                    index.query(DiscoveryQuery("arroz", selections=("form:does-not-exist",)))

    def test_input_order_changes_do_not_change_logical_results(self) -> None:
        rows = [
            _row(1, "ARROZ LARGO MARCA X 1 KG"),
            _row(2, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
            _row(3, "BEBIDA DE ARROZ MARCA X 1 L", form="drink"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            first = self._build(directory, rows)
            second_source = Path(directory) / "identities-shuffled.jsonl"
            with second_source.open("w", encoding="utf-8", newline="\n") as handle:
                for row in reversed(rows):
                    handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
            second = Path(directory) / "discovery-shuffled.sqlite"
            build_discovery_index(second_source, second, release_id="synthetic-v1")
            with DiscoveryIndex(first) as left, DiscoveryIndex(second) as right:
                left_value = left.query(DiscoveryQuery("rice", language="en", page_size=20)).to_dict()
                right_value = right.query(DiscoveryQuery("rice", language="en", page_size=20)).to_dict()
                for key in ("status", "resolvedPredicates", "selections", "total", "matches", "clarification"):
                    self.assertEqual(left_value[key], right_value[key], key)

    def test_unresolved_mentions_are_available_for_clarification_but_not_as_a_type_selection(self) -> None:
        rows = [
            _row(1, "FRUTILLA FRESCA 250 G", family="strawberry", form="unspecified"),
            {
                "productEvidenceKey": "ar-sepa-product:synthetic:2",
                "providerProductId": "provider-2",
                "name": "SHAMPOO FRUTILLA 400 ML",
                "brand": "Marca",
                "familyCandidates": [{
                    "family": "strawberry",
                    "matchedTerms": ["frutilla"],
                    "observedForms": ["cosmetics"],
                    "role": "DERIVED",
                }],
                "aliases": [],
            },
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                broad = index.query(DiscoveryQuery("strawberry", language="en", page_size=10)).to_dict()
                self.assertEqual(broad["status"], "READY")
                self.assertEqual(broad["total"]["productCount"], 1)
                cosmetics = index.query(
                    DiscoveryQuery("strawberry", language="en", selections=("form:strawberry:cosmetics",), page_size=10)
                ).to_dict()
                self.assertEqual(cosmetics["total"]["productCount"], 1)
                self.assertEqual(cosmetics["matches"][0]["displayName"], "SHAMPOO FRUTILLA 400 ML")
                fruit = index.query(
                    DiscoveryQuery("strawberry", language="en", selections=("type:strawberry",), page_size=10)
                ).to_dict()
                self.assertEqual(fruit["total"]["productCount"], 1)
                self.assertEqual(fruit["matches"][0]["displayName"], "FRUTILLA FRESCA 250 G")

    def test_bare_type_without_facets_hides_derived_mentions_from_ready_results(self) -> None:
        rows = [
            _row(1, "TOALLA DE BAÑO MARCA X", family="towel", form="unspecified"),
            {
                "productEvidenceKey": "ar-sepa-product:synthetic:2",
                "providerProductId": "provider-2",
                "name": "TOALLA FEMENINA MARCA X",
                "brand": "Marca",
                "familyCandidates": [{
                    "family": "towel",
                    "matchedTerms": ["toalla"],
                    "observedForms": ["unspecified"],
                    "role": "DERIVED",
                }],
                "aliases": [],
            },
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("towel", language="en", page_size=10)).to_dict()
                self.assertEqual(result["status"], "READY")
                self.assertEqual(result["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in result["matches"]], ["TOALLA DE BAÑO MARCA X"])

    def test_current_dictionary_overrides_stale_direct_family_assertions(self) -> None:
        rows = [{
            "productEvidenceKey": "ar-sepa-product:synthetic:stale",
            "providerProductId": "provider-stale",
            "name": "AJO EN MALLA BOL 2 UNI",
            "brand": "Marca",
            "familyCandidates": [{
                "family": "swimwear",
                "matchedTerms": ["malla"],
                "observedForms": ["unspecified"],
                "role": "DIRECT",
            }],
            "aliases": [],
        }]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("swimwear", language="en", page_size=10)).to_dict()
                self.assertEqual(result["status"], "NO_MATCHES")
                self.assertEqual(result["total"]["productCount"], 0)

    def test_current_negative_context_overrides_stale_meat_assertions(self) -> None:
        rows = [
            {
                "productEvidenceKey": "ar-sepa-product:synthetic:pet",
                "providerProductId": "provider-pet",
                "name": "ALIMENTO PERROS CARNE VACUNA 2 KG",
                "brand": "Marca",
                "familyCandidates": [{
                    "family": "beef",
                    "matchedTerms": ["carne"],
                    "observedForms": ["unspecified"],
                    "role": "DIRECT",
                }],
                "aliases": [],
            },
            {
                "productEvidenceKey": "ar-sepa-product:synthetic:meat",
                "providerProductId": "provider-meat",
                "name": "CENTRO DE ENTRANA VACUNO X KG",
                "brand": "Marca",
                "familyCandidates": [],
                "aliases": [],
            },
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("vacuno", language="es", page_size=10)).to_dict()
                self.assertEqual(result["status"], "READY")
                self.assertEqual(result["total"]["productCount"], 1)
                self.assertEqual(result["matches"][0]["displayName"], "CENTRO DE ENTRANA VACUNO X KG")

    def test_explicit_context_is_stored_as_a_typed_relation(self) -> None:
        row = {
            "productEvidenceKey": "ar-sepa-product:synthetic:77",
            "providerProductId": "provider-77",
            "name": "BEBIDA SABOR MANGO CON MANGO 500 ML",
            "brand": "Marca",
            "familyCandidates": [{
                "family": "mango",
                "matchedTerms": ["mango"],
                "observedForms": ["drink"],
                "role": "DERIVED",
            }],
            "aliases": [],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, [row])
            with DiscoveryIndex(path) as index:
                relations = {
                    value[0]
                    for value in index.connection.execute(
                        "SELECT relation FROM assertion WHERE identity_key = ?", (row["productEvidenceKey"],)
                    )
                }
                self.assertIn("MENTIONS", relations)
                self.assertIn("FLAVOR", relations)
                self.assertIn("DECLARED_INGREDIENT", relations)


if __name__ == "__main__":
    unittest.main()

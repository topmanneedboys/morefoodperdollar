from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.audit_catalog_discovery_integrity import audit_catalog_discovery_index
from tools.build_semantic_review_queue import build_semantic_review_queue
from tools.catalog_discovery import DiscoveryIndex, DiscoveryQuery, build_discovery_index
from tools.verify_catalog_discovery import verify_catalog_discovery


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
                self.assertEqual(first["total"]["productCount"], 40)
                self.assertEqual(first["total"]["sourceIdentityCount"], 40)
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
                self.assertEqual(len(seen), 40)
                self.assertEqual(len(set(seen)), 40)

                dessert = index.query(
                    DiscoveryQuery("rice", language="en", selections=("form:rice:dessert",), page_size=20)
                ).to_dict()
                self.assertEqual(dessert["status"], "READY")
                self.assertEqual(dessert["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in dessert["matches"]], ["GALLETAS DE ARROZ MARCA X 100 G"])

                grain = index.query(
                    DiscoveryQuery("rice", language="en", selections=("form:rice:grain",), page_size=20)
                ).to_dict()
                self.assertEqual(grain["status"], "READY")
                self.assertIsNone(grain["clarification"])
                self.assertEqual(grain["total"]["productCount"], 40)

                empty = index.query(
                    DiscoveryQuery("rice", language="en", selections=("form:rice:unspecified",), page_size=20)
                ).to_dict()
                self.assertEqual(empty["status"], "NO_MATCHES")
                self.assertEqual(empty["total"]["productCount"], 0)
                self.assertEqual(empty["matches"], [])

    def test_large_candidate_sets_do_not_exceed_sqlite_bind_variable_limit(self) -> None:
        rows = [_row(i, f"ARROZ LARGO MARCA {i:04d} X 1 KG") for i in range(1100)]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("rice", language="en", page_size=20)).to_dict()
                self.assertEqual(result["status"], "READY")
                self.assertEqual(result["total"]["productCount"], 1100)
                self.assertEqual(result["total"]["sourceIdentityCount"], 1100)
                self.assertTrue(result["pagination"]["hasMore"])
                self.assertEqual(len(result["matches"]), 20)

    def test_structural_integrity_audit_checks_a_built_sidecar(self) -> None:
        rows = [
            _row(1, "ARROZ LARGO MARCA X 1 KG"),
            _row(2, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            result = audit_catalog_discovery_index(path)
            self.assertEqual(result["status"], "PASS")
            self.assertEqual(result["failureCount"], 0)
            self.assertGreater(result["predicateCount"], 0)

    def test_full_catalog_verifier_accounts_for_literal_and_strict_paths(self) -> None:
        rows = [
            _row(1, "ARROZ LARGO MARCA X 1 KG"),
            _row(2, "ARROZ LARGO MARCA X 1 KG", provider="provider-002"),
            _row(3, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "identities.jsonl"
            source.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")
            output = Path(directory) / "discovery.sqlite"
            build_discovery_index(source, output, release_id="synthetic-verification-v1")
            report = verify_catalog_discovery(output, source, review_queue_path=Path(directory) / "review.jsonl")
            self.assertEqual(report["sourceAccounting"]["status"], "PASS")
            self.assertEqual(report["identityReachability"]["identitiesLiteralUnreachable"], 0)
            self.assertEqual(report["strictSelection"]["status"], "PASS")
            self.assertEqual(report["pagination"]["status"], "PASS")
            self.assertEqual(report["semanticAccuracy"]["status"], "NOT_ESTABLISHED")

    def test_semantic_ledger_requires_complete_independent_membership_oracle(self) -> None:
        rows = [
            _row(1, "ARROZ LARGO MARCA X 1 KG", form="grain"),
            _row(2, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "identities.jsonl"
            source.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in rows), encoding="utf-8")
            output = Path(directory) / "discovery.sqlite"
            build_discovery_index(source, output, release_id="semantic-ledger-v1")
            ledger = Path(directory) / "semantic.json"
            ledger.write_text(
                json.dumps(
                    {
                        "schemaVersion": "valuepilot-semantic-review-ledger-v1",
                        "sourceReleaseId": "semantic-ledger-v1",
                        "sourceSha256": "0" * 64,
                        "cases": [
                            {
                                "id": "rice-grain-001",
                                "query": "rice",
                                "language": "en",
                                "selection": "form:rice:grain",
                                "expectedIdentityKeys": ["ar-sepa-product:synthetic:001"],
                                "expectedProviderProductIds": ["provider-001"],
                                "mustExcludeIdentityKeys": ["ar-sepa-product:synthetic:002"],
                                "reviewStatus": "REVIEWED",
                                "reviewerId": "reviewer-a",
                                "reviewMethod": "HUMAN_SOURCE_REVIEW",
                                "reviewedAt": "2026-10-17T19:00:00Z",
                                "reviewNotes": "The source name explicitly identifies whole grain rice.",
                            }
                        ],
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            report = verify_catalog_discovery(output, source, semantic_eval_path=ledger)
            self.assertEqual(report["semanticAccuracy"]["status"], "PASS")
            self.assertEqual(report["semanticAccuracy"]["reviewedCases"], 1)
            self.assertEqual(report["semanticAccuracy"]["membershipMetrics"]["identity"]["precision"], 1.0)

            invalid = json.loads(ledger.read_text(encoding="utf-8"))
            invalid["cases"][0]["expectedIdentityKeys"].append("ar-sepa-product:synthetic:002")
            invalid["cases"][0]["expectedProviderProductIds"].append("provider-002")
            ledger.write_text(json.dumps(invalid, ensure_ascii=False), encoding="utf-8")
            failed = verify_catalog_discovery(output, source, semantic_eval_path=ledger)
            self.assertEqual(failed["semanticAccuracy"]["status"], "FAIL")
            self.assertEqual(failed["semanticAccuracy"]["results"][0]["missingRequired"], ["ar-sepa-product:synthetic:002"])

    def test_future_import_add_change_delete_is_literal_safe_and_reviewable(self) -> None:
        initial_rows = [
            _row(1, "ARROZ LARGO MARCA X 1 KG"),
            _row(2, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
        ]
        new_unknown = {
            "productEvidenceKey": "ar-sepa-product:synthetic:003",
            "providerProductId": "provider-003",
            "name": "PRODUCTO NUEVO SIN CLASIFICAR 1 UN",
            "brand": "Marca",
            "familyCandidates": [],
            "aliases": [],
            "sourceEvidence": {"nameSamples": ["PRODUCTO NUEVO SIN CLASIFICAR 1 UN"]},
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            initial_source = root / "initial.jsonl"
            initial_source.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in initial_rows), encoding="utf-8")
            initial_index = root / "initial.sqlite"
            build_discovery_index(initial_source, initial_index, release_id="future-v1")

            next_source = root / "next.jsonl"
            next_rows = [initial_rows[0], new_unknown]
            next_source.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in next_rows), encoding="utf-8")
            next_index = root / "next.sqlite"
            build_discovery_index(next_source, next_index, release_id="future-v2")
            queue = root / "review.jsonl"
            report = verify_catalog_discovery(next_index, next_source, review_queue_path=queue)

            self.assertEqual(report["sourceAccounting"]["sourceRows"], 2)
            self.assertEqual(report["sourceAccounting"]["sidecarIdentityRows"], 2)
            self.assertEqual(report["identityReachability"]["identitiesLiteralUnreachable"], 0)
            review_rows = [json.loads(line) for line in queue.read_text(encoding="utf-8").splitlines() if line.strip()]
            self.assertTrue(any(row["identityKey"] == new_unknown["productEvidenceKey"] and row["stratum"] == "UNRESOLVED" for row in review_rows))
            with DiscoveryIndex(next_index) as index:
                literal = index.query(DiscoveryQuery("producto nuevo sin clasificar", language="es", page_size=10)).to_dict()
                self.assertEqual(literal["status"], "READY")
                self.assertEqual([item["identityKey"] for item in literal["matches"]], [new_unknown["productEvidenceKey"]])
                self.assertNotIn("ar-sepa-product:synthetic:002", {row["identityKey"] for row in index.query(DiscoveryQuery("galletas de arroz", language="es", page_size=10)).to_dict()["matches"]})

    def test_semantic_review_queue_keeps_provider_identity_copies_together(self) -> None:
        first = _row(1, "ARROZ LARGO MARCA X 1 KG", provider="shared-provider")
        second = _row(2, "ARROZ LARGO MARCA X 1 KG", provider="shared-provider")
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "identities.jsonl"
            source.write_text("".join(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n" for row in (first, second)), encoding="utf-8")
            index_path = Path(directory) / "discovery.sqlite"
            build_discovery_index(source, index_path, release_id="queue-v1")
            output = Path(directory) / "queue.jsonl"
            summary = build_semantic_review_queue(index_path, output, group_limit=1)
            rows = [json.loads(line) for line in output.read_text(encoding="utf-8").splitlines() if line.strip()]
            self.assertEqual(summary["providerProductGroupsSelected"], 1)
            self.assertEqual(summary["identityRowsSelected"], 2)
            self.assertEqual({row["providerProductId"] for row in rows}, {"shared-provider"})
            self.assertEqual({row["reviewGroupId"] for row in rows}, {"provider:shared-provider"})

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

    def test_department_facet_selection_keeps_the_strict_child_results(self) -> None:
        rows = [
            _row(1, "PANAL BEBE MARCA X 10 UN", family="diaper", form="unspecified"),
            _row(2, "ALIMENTO PERRO MARCA X 2 KG", family="dog_food", form="unspecified"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                broad = index.query(DiscoveryQuery("baby", language="en", page_size=10)).to_dict()
                self.assertEqual(broad["status"], "NEEDS_CLARIFICATION")
                diaper = index.query(
                    DiscoveryQuery("baby", language="en", selections=("type:diaper",), page_size=10)
                ).to_dict()
                self.assertEqual(diaper["status"], "READY")
                self.assertEqual(diaper["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in diaper["matches"]], ["PANAL BEBE MARCA X 10 UN"])

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

    def test_plural_compound_form_alias_keeps_rice_cakes_scoped(self) -> None:
        rows = [
            _row(1, "GALLETAS DE ARROZ MARCA X 100 G", form="dessert"),
            _row(2, "ARROZ LARGO MARCA X 1 KG", form="grain"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                english = index.query(DiscoveryQuery("rice cakes", language="en", page_size=10)).to_dict()
                spanish = index.query(DiscoveryQuery("galletas de arroz", language="es", page_size=10)).to_dict()
                self.assertEqual(english["status"], "READY")
                self.assertEqual(spanish["status"], "READY")
                self.assertEqual(english["resolvedPredicates"], ["form:rice:dessert"])
                self.assertEqual(spanish["resolvedPredicates"], ["form:rice:dessert"])
                self.assertEqual(english["total"]["productCount"], 1)
                self.assertEqual(spanish["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in english["matches"]], [rows[0]["name"]])
                self.assertEqual([item["displayName"] for item in spanish["matches"]], [rows[0]["name"]])

    def test_rice_context_aliases_keep_prepared_pet_and_grain_queries_scoped(self) -> None:
        rows = [
            _row(1, "ARROZ C/LECH C/DDL TREG 180 G"),
            _row(2, "ARROZ C/MARISCOS MENU BOX CJA 250 GRM"),
            _row(3, "ARROZ DEL AMIGO P/PERROS 5KG"),
            _row(4, "ARROZ PARA RISOTTO X KG"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                checks = (
                    ("arroz c lech", "es", "form:rice:dessert", rows[0]["name"]),
                    ("arroz preparado", "es", "form:rice:prepared", rows[1]["name"]),
                    ("prepared rice", "en", "form:rice:prepared", rows[1]["name"]),
                    ("arroz para perros", "es", "form:rice:pet_food", rows[2]["name"]),
                    ("rice pudding", "en", "form:rice:dessert", rows[0]["name"]),
                    ("arroz para risotto", "es", "form:rice:grain", rows[3]["name"]),
                )
                for query, language, predicate, expected_name in checks:
                    with self.subTest(query=query):
                        result = index.query(DiscoveryQuery(query, language=language, page_size=10)).to_dict()
                        self.assertEqual(result["status"], "READY")
                        self.assertEqual(result["resolvedPredicates"], [predicate])
                        self.assertEqual(result["total"]["productCount"], 1)
                        self.assertEqual([item["displayName"] for item in result["matches"]], [expected_name])

    def test_current_context_replaces_stale_derived_form_assertion(self) -> None:
        stale = _row(1, "SOPA CREMA DE ZANAHORIA SABORES DEL VAL 500 G", family="carrot", form="cosmetics")
        stale["familyCandidates"][0]["role"] = "DERIVED"
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, [stale])
            with DiscoveryIndex(path) as index:
                cosmetic = index.query(
                    DiscoveryQuery("carrot", language="en", selections=("form:carrot:cosmetics",), page_size=10)
                ).to_dict()
                self.assertEqual(cosmetic["status"], "NO_MATCHES")
                self.assertEqual(cosmetic["total"]["productCount"], 0)

    def test_specific_cross_language_family_alias_beats_broad_department_alias(self) -> None:
        rows = [_row(1, "ARROZ LARGO MARCA X 1 KG")]
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, rows)
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("rice", language="es", page_size=10)).to_dict()
                self.assertEqual(result["status"], "READY")
                self.assertEqual(result["resolvedPredicates"], ["type:rice"])
                self.assertEqual(result["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in result["matches"]], ["ARROZ LARGO MARCA X 1 KG"])

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

    def test_direct_cleaning_cloth_forms_exclude_derived_mentions(self) -> None:
        direct = _row(1, "PANO DE COCINA MARCA X 1 UN", family="cleaning_cloth", form="kitchen")
        derived = _row(2, "CORTINAS DE COCINA ESTAMPADAS 2 PAÑO", family="cleaning_cloth", form="kitchen")
        derived["familyCandidates"][0]["role"] = "DERIVED"
        with tempfile.TemporaryDirectory() as directory:
            path = self._build(directory, [direct, derived])
            with DiscoveryIndex(path) as index:
                result = index.query(DiscoveryQuery("pano de cocina", language="es", page_size=10)).to_dict()
                self.assertEqual(result["status"], "READY")
                self.assertEqual(result["resolvedPredicates"], ["form:cleaning_cloth:kitchen"])
                self.assertEqual(result["total"]["productCount"], 1)
                self.assertEqual([item["displayName"] for item in result["matches"]], ["PANO DE COCINA MARCA X 1 UN"])

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
                self.assertEqual(broad["status"], "NEEDS_CLARIFICATION")
                self.assertEqual(broad["total"]["productCount"], 1)
                self.assertIsNotNone(broad["clarification"])
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

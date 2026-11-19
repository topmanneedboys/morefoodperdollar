from __future__ import annotations

from decimal import Decimal
import gzip
import json
import os
import tempfile
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from backend.reader import ArgentinaBackendReader, RegionSelection
from backend.service import (
    ANDROID_NETWORKING_AUTHORIZED_ENV,
    ANDROID_NETWORKING_RELEASE_DATE_ENV,
    ANDROID_NETWORKING_RELEASE_ID_ENV,
    ANDROID_NETWORKING_SCOPE_ENV,
    PRODUCTION_ANDROID_UI_AUTHORIZED_ENV,
    PRODUCTION_ANDROID_UI_SCOPE,
    PRODUCTION_ANDROID_UI_SCOPE_ENV,
    BackendService,
)


class SearchEvidenceTests(unittest.TestCase):
    class _ReleaseManager:
        def __init__(self) -> None:
            self.handle = SimpleNamespace(
                release_id="fixture-release",
                release_date="2026-10-01",
                freshness_status="FRESH",
                artifacts=SimpleNamespace(manifest={
                    "backendProfile": {"logicalPartitionCount": 1024, "physicalPackCount": 32},
                    "searchPackArtifact": {"schemaVersion": "valuepilot-argentina-searchpack-v2", "regions": ["ar-caba"]},
                }),
            )

        def pin(self, *, require_fresh: bool = True):
            del require_fresh
            return self.handle

    class _Reader:
        def __init__(self) -> None:
            self.router = SimpleNamespace(
                route=lambda **_kwargs: (RegionSelection("ar-caba", ("store-1",)),)
            )

        def discover_with_offers(self, query, *, selections, latitude, longitude, radius_km, product_limit):
            del latitude, longitude, radius_km
            self.args = (query, selections, product_limit)
            return (
                ({
                    "productEvidenceKey": "product-1",
                    "name": query,
                    "brand": "Fixture",
                    "gtin": None,
                    "score": 100,
                    "matchedTokens": [query],
                    "qualifiedOffers": [{
                        "offerKey": "offer-1",
                        "productEvidenceKey": "product-1",
                        "storeKey": "store-1",
                        "storeName": "Fixture Market",
                        "storeAddress": {"street": "Calle 1"},
                        "storeLatitude": "-34.60",
                        "storeLongitude": "-58.38",
                        "distanceKm": "1.000000",
                        "distanceSemantics": "STRAIGHT_LINE_HAVERSINE",
                        "listPrice": {"amount": "2500.00", "currency": "ARS"},
                        "referencePrice": None,
                        "quantity": {"value": "500", "unit": "g"},
                        "quantityStatus": "KNOWN",
                        "unitPrice": {"amount": "500", "currency": "ARS", "basis": "100 g"},
                        "releaseDate": "2026-10-01",
                        "freshnessStatus": "FRESH",
                        "availability": "UNKNOWN",
                        "promotions": [],
                        "provenance": {
                            "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
                            "releaseDate": "2026-10-01",
                            "freshnessStatus": "FRESH",
                        },
                    }],
                },),
                SimpleNamespace(
                    regions_queried=["ar-caba"],
                    as_dict=lambda: {"regionsQueried": ["ar-caba"]},
                ),
            )

    def test_discovery_offer_preview_is_exact_bounded_and_unknown_inventory(self) -> None:
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        candidate = {
            "productEvidenceKey": "ar-sepa-product:1:7790000000001",
            "name": "Yerba Fixture 500 g",
            "brand": "Fixture",
            "gtin": None,
            "score": 100,
            "matchedTokens": ["yerba"],
            "partitionId": "partition-yerba",
            "quantity": {"value": "500", "unit": "g"},
            "quantityStatus": "KNOWN",
        }
        raw_offer = {
            "offerKey": "ar-caba-offer:1",
            "offerId": 1,
            "productEvidenceKey": candidate["productEvidenceKey"],
            "storeKey": "ar-sepa-store:1:1:1",
            "listPrice": {"amount": "2500.00", "currency": "ARS"},
            "referencePrice": None,
            "packageProvenance": {"sha256": "a" * 64, "name": "fixture.zip"},
            "sourceRow": 7,
            "providerUpdateTime": None,
            "releaseDate": "2026-10-01",
            "freshnessStatus": "FRESH",
            "availability": "UNKNOWN",
            "promotions": [],
        }
        store = {
            "name": "Fixture Market",
            "type": "GROCERY",
            "address": {"street": "Calle 1"},
            "locality": "CABA",
            "province": "C",
            "latitude": "-34.6037",
            "longitude": "-58.3816",
            "geoStatus": "VALID",
        }
        reader.router = SimpleNamespace(
            contract=lambda _region: SimpleNamespace(region=SimpleNamespace(region_id="ar-caba")),
            stores=lambda _region: {raw_offer["storeKey"]: store},
        )
        reader._rank_discovery_region = lambda *_args, **_kwargs: (candidate,)
        reader._plan = lambda *_args, **_kwargs: SimpleNamespace(slices=({"partitionId": "partition-yerba"},))
        reader._load_members = lambda _region, _slices, _metrics: [[raw_offer]]

        values, metrics = reader.discover_with_offers(
            "yerba",
            selections=(RegionSelection("ar-caba", (raw_offer["storeKey"],)),),
            latitude=Decimal("-34.6037"),
            longitude=Decimal("-58.3816"),
            radius_km=Decimal("5"),
            product_limit=5,
        )

        self.assertEqual(metrics.regions_queried, ["ar-caba"])
        self.assertEqual(len(values), 1)
        self.assertEqual(values[0]["productEvidenceKey"], candidate["productEvidenceKey"])
        offer = values[0]["qualifiedOffers"][0]
        self.assertEqual(offer["listPrice"], {"amount": "2500.00", "currency": "ARS"})
        self.assertEqual(offer["unitPrice"], {"amount": "500", "currency": "ARS", "basis": "100 g"})
        self.assertEqual(offer["availability"], "UNKNOWN")
        self.assertEqual(offer["provenance"]["provider"], "ARGENTINA_SEPA_PRECIOS_CLAROS")

    def test_missing_partition_keeps_identity_without_guessing_price(self) -> None:
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        candidate = {
            "productEvidenceKey": "legacy-product",
            "name": "Legacy Fixture",
            "brand": None,
            "gtin": None,
            "score": 1,
            "matchedTokens": ["legacy"],
        }
        reader.router = SimpleNamespace()
        reader._rank_discovery_region = lambda *_args, **_kwargs: (candidate,)
        values, _metrics = reader.discover_with_offers(
            "legacy",
            selections=(RegionSelection("ar-caba", ("store-1",)),),
            latitude=Decimal("-34"),
            longitude=Decimal("-58"),
            radius_km=Decimal("5"),
        )
        self.assertEqual(values[0]["qualifiedOffers"], [])

    def test_offer_metadata_conflict_cannot_inherit_selected_identity_price(self) -> None:
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        candidate = {
            "productEvidenceKey": "product-1",
            "providerProductId": "provider-1",
            "gtin": "7791234567895",
            "name": "Yerba Fixture 500 g",
            "brand": "Fixture",
            "score": 100,
            "matchedTokens": ["yerba"],
            "partitionId": "partition-yerba",
            "quantity": {"value": "500", "unit": "g"},
            "quantityStatus": "KNOWN",
        }
        store = {
            "name": "Fixture Market",
            "type": "GROCERY",
            "address": {"street": "Calle 1"},
            "locality": "CABA",
            "province": "C",
            "latitude": "-34.6037",
            "longitude": "-58.3816",
            "geoStatus": "VALID",
        }

        def offer(offer_id: int, **overrides):
            value = {
                "offerKey": f"offer-{offer_id}",
                "offerId": offer_id,
                "productEvidenceKey": "product-1",
                "storeKey": "store-1",
                "listPrice": {"amount": "2500.00", "currency": "ARS"},
                "referencePrice": None,
                "packageProvenance": {"sha256": "a" * 64, "name": "fixture.zip"},
                "sourceRow": offer_id,
                "providerUpdateTime": None,
                "releaseDate": "2026-10-01",
                "freshnessStatus": "FRESH",
                "availability": "UNKNOWN",
                "promotions": [],
            }
            value.update(overrides)
            return value

        valid = offer(
            1,
            providerProductId="provider-1",
            gtin="7791234567895",
            quantity={"value": "500", "unit": "g"},
        )
        wrong_gtin = offer(2, gtin="7791234567894")
        wrong_package = offer(3, quantity={"value": "1", "unit": "kg"})
        wrong_product = offer(4, productEvidenceKey="product-2")
        reader.router = SimpleNamespace(
            contract=lambda _region: SimpleNamespace(region=SimpleNamespace(region_id="ar-caba")),
            stores=lambda _region: {"store-1": store},
        )
        reader._rank_discovery_region = lambda *_args, **_kwargs: (candidate,)
        reader._plan = lambda *_args, **_kwargs: SimpleNamespace(slices=({"partitionId": "partition-yerba"},))
        reader._load_members = lambda _region, _slices, _metrics: [[valid, wrong_gtin, wrong_package, wrong_product]]

        values, _metrics = reader.discover_with_offers(
            "yerba",
            selections=(RegionSelection("ar-caba", ("store-1",)),),
            latitude=Decimal("-34.6037"),
            longitude=Decimal("-58.3816"),
            radius_km=Decimal("5"),
            product_limit=5,
        )

        self.assertEqual([offer["offerKey"] for offer in values[0]["qualifiedOffers"]], ["offer-1"])

    def test_service_exposes_bounded_offer_preview_without_internal_sort_fields(self) -> None:
        reader = self._Reader()
        service = BackendService("", release_manager=self._ReleaseManager())
        service._reader = lambda _handle: reader

        environment = {
            ANDROID_NETWORKING_AUTHORIZED_ENV: "true",
            ANDROID_NETWORKING_SCOPE_ENV: "identity-search",
            ANDROID_NETWORKING_RELEASE_ID_ENV: "fixture-release",
            ANDROID_NETWORKING_RELEASE_DATE_ENV: "2026-10-01",
            PRODUCTION_ANDROID_UI_AUTHORIZED_ENV: "true",
            PRODUCTION_ANDROID_UI_SCOPE_ENV: PRODUCTION_ANDROID_UI_SCOPE,
        }
        with patch.dict(os.environ, environment, clear=True):
            response = service.search({
                "latitude": "-34.60",
                "longitude": "-58.38",
                "radiusKm": "5",
                "query": "yerba",
            })

        offer = response["matches"][0]["qualifiedOffers"][0]
        self.assertEqual(offer["listPrice"], {"amount": "2500.00", "currency": "ARS"})
        self.assertEqual(offer["availability"], "UNKNOWN")
        self.assertNotIn("__offerSortId", offer)
        self.assertTrue(response["evidenceSemantics"]["qualifiedCurrentPriceEvidence"])

    def test_public_search_offer_omits_unknown_store_address_fields(self) -> None:
        offer = {
            "offerKey": "offer-1",
            "productEvidenceKey": "product-1",
            "storeKey": "store-1",
            "storeName": "Fixture Market",
            "storeAddress": {"street": "Av. Corrientes", "number": None, "postalCode": None},
            "storeLatitude": "-34.60",
            "storeLongitude": "-58.38",
            "distanceKm": "1.000000",
            "listPrice": {"amount": "2500.00", "currency": "ARS"},
            "referencePrice": None,
            "releaseDate": "2026-10-01",
            "freshnessStatus": "FRESH",
            "promotions": [],
        }
        product = {
            "productEvidenceKey": "product-1",
            "quantity": None,
            "quantityStatus": "UNKNOWN",
        }

        projected = ArgentinaBackendReader._public_search_offer(offer, product)

        self.assertEqual(projected["storeAddress"], {"street": "Av. Corrientes"})

    def test_public_search_offer_projects_provider_reference_unit_price(self) -> None:
        offer = {
            "offerKey": "offer-1",
            "productEvidenceKey": "product-1",
            "storeKey": "store-1",
            "listPrice": {"amount": "9055", "currency": "ARS"},
            "referencePrice": {
                "amount": "36220",
                "currency": "ARS",
                "quantityRaw": "1",
                "unitRaw": "ltr",
                "semanticRole": "reference_price_not_current_offer",
            },
            "releaseDate": "2026-11-15",
            "freshnessStatus": "FRESH",
            "promotions": [],
        }
        product = {
            "productEvidenceKey": "product-1",
            "quantity": None,
            "quantityStatus": "UNKNOWN",
        }

        projected = ArgentinaBackendReader._public_search_offer(offer, product)

        self.assertIsNone(projected["unitPrice"])
        self.assertEqual(
            projected["referenceUnitPrice"],
            {
                "amount": "36220",
                "currency": "ARS",
                "basis": "1 l",
                "source": "PROVIDER_REFERENCE",
            },
        )

    def test_public_search_offer_rejects_unmapped_reference_units(self) -> None:
        offer = {
            "offerKey": "offer-1",
            "productEvidenceKey": "product-1",
            "storeKey": "store-1",
            "listPrice": {"amount": "100", "currency": "ARS"},
            "referencePrice": {
                "amount": "500",
                "currency": "ARS",
                "quantityRaw": "1",
                "unitRaw": "furlong",
                "semanticRole": "reference_price_not_current_offer",
            },
            "releaseDate": "2026-11-15",
            "freshnessStatus": "FRESH",
            "promotions": [],
        }
        product = {
            "productEvidenceKey": "product-1",
            "quantity": None,
            "quantityStatus": "UNKNOWN",
        }

        projected = ArgentinaBackendReader._public_search_offer(offer, product)

        self.assertIsNone(projected["referenceUnitPrice"])

    def test_bare_orange_query_searches_the_spanish_catalog_term(self) -> None:
        reader = self._Reader()
        service = BackendService("", release_manager=self._ReleaseManager())
        service._reader = lambda _handle: reader

        with patch.dict(os.environ, {}, clear=True):
            service.search({
                "latitude": "-34.60",
                "longitude": "-58.38",
                "radiusKm": "5",
                "query": "orange",
            })

        self.assertEqual(reader.args[0], "naranja")

    def test_categories_reads_the_taxonomy_sidecar_tree(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "taxonomy.jsonl.gz"
            record = {
                "providerProductId": "1",
                "identityKeys": ["ar-sepa-product:1:1"],
                "taxonomy": {
                    "coverageStatus": "CLASSIFIED",
                    "primaryDepartment": "dairy",
                    "primarySubcategory": "milk",
                    "departmentCandidates": [
                        {"key": "dairy", "labelEs": "Lácteos", "labelEn": "Dairy"}
                    ],
                    "subcategoryCandidates": [
                        {"key": "milk", "labelEs": "Leches", "labelEn": "Milk"}
                    ],
                },
            }
            with gzip.open(path, "wt", encoding="utf-8") as handle:
                handle.write(json.dumps(record, ensure_ascii=False) + "\n")
            service = BackendService(
                str(Path(directory)),
                release_manager=self._ReleaseManager(),
                taxonomy_index_path=path,
            )

            result = service.categories()

            self.assertEqual(result["departments"][0]["key"], "dairy")
            self.assertEqual(result["departments"][0]["labelEs"], "Lácteos")
            self.assertEqual(result["subcategories"][0]["key"], "milk")
            self.assertEqual(result["productsWithSubcategory"], 1)

    def test_category_match_ranks_before_literal_name_match(self) -> None:
        from backend.service import _rank_by_expected_department

        candidates = (
            {
                "productEvidenceKey": "juice",
                "name": "JUGO NARANJA",
                "_taxonomy": {
                    "primaryDepartment": "beverages",
                    "departmentCandidates": [{"key": "beverages"}],
                    "primarySubcategory": "juice",
                    "subcategoryCandidates": [{"key": "juice"}],
                },
            },
            {
                "productEvidenceKey": "fruit",
                "name": "NARANJA",
                "_taxonomy": {
                    "primaryDepartment": "fresh_produce",
                    "departmentCandidates": [{"key": "fresh_produce"}],
                    "primarySubcategory": "citrus",
                    "subcategoryCandidates": [{"key": "citrus"}],
                },
            },
        )

        ranked = _rank_by_expected_department(candidates, "naranja")

        self.assertEqual([item["productEvidenceKey"] for item in ranked], ["fruit", "juice"])

    def test_category_facet_filter_matches_department_and_subcategory(self) -> None:
        from backend.service import _matches_taxonomy_facet

        candidate = {
            "_taxonomy": {
                "departmentCandidates": [{"key": "dairy"}],
                "subcategoryCandidates": [{"key": "milk"}],
            }
        }

        self.assertTrue(_matches_taxonomy_facet(candidate, "dairy", None))
        self.assertTrue(_matches_taxonomy_facet(candidate, None, "milk"))
        self.assertFalse(_matches_taxonomy_facet(candidate, "meat_seafood", None))
        self.assertFalse(_matches_taxonomy_facet(candidate, None, "eggs"))
        self.assertFalse(_matches_taxonomy_facet({}, "dairy", None))

    def test_search_rejects_an_invalid_category_filter(self) -> None:
        from backend.service import BackendQueryError

        service = BackendService("", release_manager=self._ReleaseManager())

        with self.assertRaises(BackendQueryError) as caught:
            service.search({"query": "leche", "department": "Dairy!"})

        self.assertIn("filter is invalid", str(caught.exception))

    def test_search_facets_summarize_departments_brands_stores_and_prices(self) -> None:
        from backend.service import _search_facets

        candidates = [
            {
                "brand": "Marca A",
                "quantity": {"value": "500", "unit": "g"},
                "_taxonomy": {
                    "primaryDepartment": "dairy",
                    "departmentCandidates": [{"key": "dairy", "labelEs": "Lácteos", "labelEn": "Dairy"}],
                    "primarySubcategory": "milk",
                    "subcategoryCandidates": [{"key": "milk", "labelEs": "Leches", "labelEn": "Milk"}],
                },
                "qualifiedOffers": [
                    {"storeName": "Store 1", "listPrice": {"amount": "1000", "currency": "ARS"}}
                ],
            },
            {
                "brand": "Marca B",
                "quantity": {"value": "1", "unit": "l"},
                "qualifiedOffers": [
                    {"storeName": "Store 2", "listPrice": {"amount": "2500", "currency": "ARS"}}
                ],
            },
        ]

        facets = _search_facets(candidates)

        self.assertEqual(
            facets["departments"],
            [{"key": "dairy", "count": 1, "labelEs": "Lácteos", "labelEn": "Dairy"}],
        )
        self.assertEqual(facets["subcategories"][0]["key"], "milk")
        self.assertEqual([item["key"] for item in facets["brands"]], ["Marca A", "Marca B"])
        self.assertEqual([item["key"] for item in facets["stores"]], ["Store 1", "Store 2"])
        self.assertEqual(facets["priceRange"], {"min": "1000", "max": "2500", "currency": "ARS"})

    def test_search_keeps_identity_but_strips_offers_without_production_ui_gate(self) -> None:
        reader = self._Reader()
        service = BackendService("", release_manager=self._ReleaseManager())
        service._reader = lambda _handle: reader

        with patch.dict(os.environ, {}, clear=True):
            response = service.search({
                "latitude": "-34.60",
                "longitude": "-58.38",
                "radiusKm": "5",
                "query": "yerba",
            })

        self.assertEqual(response["matches"][0]["productEvidenceKey"], "product-1")
        self.assertEqual(response["matches"][0]["qualifiedOffers"], [])
        self.assertFalse(response["evidenceSemantics"]["qualifiedCurrentPriceEvidence"])


if __name__ == "__main__":
    unittest.main()

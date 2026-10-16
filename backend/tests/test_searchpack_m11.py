from __future__ import annotations

from collections import OrderedDict
import gzip
import hashlib
import json
import tempfile
import threading
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from backend.artifacts import ManifestReleaseArtifactStore
from backend.object_store import LocalFilesystemObjectStore, ObjectMetadata
from backend.cache import VerifiedEvidenceCache
from backend.reader import ArgentinaBackendReader, BackendQueryError, MAX_OFFER_PARTITION_CONCURRENCY, RegionSelection, RequestMetrics
from backend.service import BackendService
from tools.argentina_searchpack import MAX_RANK_WORKERS, SearchPackLookup, SearchPackManager, SearchPackStats, _CHUNK_STRUCT, _TERM_STRUCT
from tools.argentina_searchpack import SearchPackError
from tools.build_argentina_searchpack import SEARCHPACK_RELEASE_SUFFIX, derive_searchpack_workspace
from tools.tests.test_argentina_searchpack import _fixture_workspace, _new_output
from tools.tests.test_consumer_input_intelligence import fixture_records
from tools.consumer_input_intelligence import NEEDS_CLARIFICATION


class _FixtureRouter:
    def __init__(self) -> None:
        self.contract_value = SimpleNamespace(region=SimpleNamespace(region_id="ar-caba"))

    def contract(self, region_id: str) -> object:
        if region_id != "ar-caba":
            raise AssertionError(region_id)
        return self.contract_value


def _reader_with_pack(rows: list[dict[str, object]]) -> tuple[ArgentinaBackendReader, SearchPackManager, Path, dict[str, object]]:
    source, release_id = _fixture_workspace(rows)
    output = _new_output("valuepilot-m11-reader-")
    derive_searchpack_workspace(source, output, [release_id])
    derived_id = f"{release_id}{SEARCHPACK_RELEASE_SUFFIX}"
    manifest = json.loads((output / "releases" / derived_id / "manifest.json").read_bytes())
    artifacts = ManifestReleaseArtifactStore(LocalFilesystemObjectStore(output), manifest, release_id=derived_id)
    manager = SearchPackManager(artifacts, manifest)
    reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
    reader.release = SimpleNamespace(release_id=derived_id)
    reader.artifacts = artifacts
    reader.router = _FixtureRouter()
    reader._search_cache = {}
    reader._input_candidate_cache = {}
    reader._searchpack = manager
    reader._searchpack_declared = True
    reader._searchpack_error = None
    return reader, manager, output, manifest


class _MultiRegionDiscoveryRouter:
    def __init__(self, region_ids: tuple[str, ...]) -> None:
        self.contract_calls: list[str] = []
        self.region_ids = region_ids

    def contract(self, region_id: str) -> object:
        self.contract_calls.append(region_id)
        if region_id not in self.region_ids:
            raise AssertionError(region_id)
        return SimpleNamespace(region=SimpleNamespace(region_id=region_id))


class _FakeDiscoveryRegion:
    def __init__(self, region_id: str, values: tuple[dict[str, object], ...], calls: list[str]) -> None:
        self.region_id = region_id
        self.values = values
        self.calls = calls

    def search_many(self, queries, *, product_limit: int, candidate_bound: int, stats: SearchPackStats):
        self.calls.append(self.region_id)
        self.asserted_query = tuple(queries)
        self.asserted_limit = product_limit
        self.asserted_bound = candidate_bound
        stats.ranked_candidates += len(self.values)
        stats.ranked_records_materialized += min(product_limit, len(self.values))
        return tuple(tuple(self.values[:product_limit]) for _query in queries)


class _FakeDiscoveryManager:
    def __init__(self, values_by_region: dict[str, tuple[dict[str, object], ...]]) -> None:
        self.calls: list[str] = []
        self.regions = {
            region_id: _FakeDiscoveryRegion(region_id, values, self.calls)
            for region_id, values in values_by_region.items()
        }

    def region(self, region_id: str) -> _FakeDiscoveryRegion:
        return self.regions[region_id]


class _FakeInputRegion:
    """Small SearchPack input double that records per-line work."""

    def __init__(
        self,
        region_id: str,
        records: dict[int, dict[str, object]],
        *,
        saturated_arroz: bool = False,
        forbid_arroz: bool = False,
    ) -> None:
        self.region_id = region_id
        self.records = records
        self.saturated_arroz = saturated_arroz
        self.forbid_arroz = forbid_arroz
        self.lookup_calls: list[tuple[str, ...]] = []
        self.get_records_calls: list[tuple[int, ...]] = []

    def lookup_features(self, feature_keys, *, candidate_bound: int, stats: SearchPackStats):
        del candidate_bound, stats
        keys = tuple(feature_keys)
        self.lookup_calls.append(keys)
        has_arroz = "exact:arroz" in keys
        has_leche = "exact:leche" in keys
        if has_arroz:
            if self.forbid_arroz:
                raise AssertionError(f"saturated arroz line was queried in {self.region_id}")
            if self.saturated_arroz:
                return SearchPackLookup((), True, ("exact:arroz",), keys)
            return SearchPackLookup((0,), False, (), keys)
        if has_leche:
            return SearchPackLookup((0,), False, (), keys)
        return SearchPackLookup((), False, (), keys)

    def get_records(self, doc_ids, *, stats: SearchPackStats, max_records: int):
        del stats, max_records
        requested = tuple(sorted(doc_ids))
        self.get_records_calls.append(requested)
        return {doc_id: self.records[doc_id] for doc_id in requested}


class _FakeInputManager:
    def __init__(self, regions: dict[str, _FakeInputRegion]) -> None:
        self.regions = regions
        self.region_calls: list[str] = []

    def region(self, region_id: str) -> _FakeInputRegion:
        self.region_calls.append(region_id)
        return self.regions[region_id]


class _StubReleaseManager:
    def __init__(self) -> None:
        self.handle = SimpleNamespace(release_id="fixture-release", release_date="2026-09-08", freshness_status="FRESH")

    def pin(self, *, require_fresh: bool = True):
        del require_fresh
        return self.handle


class _StubReader:
    def __init__(self) -> None:
        self.router = SimpleNamespace(route=lambda **kwargs: (RegionSelection("ar-caba", ("store-1",)),))
        self.trusted_calls: list[object] = []
        self.discovery_calls: list[tuple[str, tuple[str, ...], int]] = []
        self.interpret_calls: list[str] = []
        self.discovery_values: list[dict[str, object]] | None = None

    def interpret_text(self, text: str, *, require_quantities: bool = False, region_ids=None):
        self.interpret_calls.append(text)
        del require_quantities, region_ids
        if "\n" in text:
            return {
                "safeRequestReady": True,
                "lines": [{"lineId": "item-1", "recognizedProduct": {"productEvidenceKey": "key-1", "name": text.splitlines()[0]}}],
                "structuredItems": [{"lineId": "item-1", "query": text.splitlines()[0], "amount": "1", "unit": "count"}],
            }
        return {
            "safeRequestReady": True,
            "lines": [{"lineId": "item-1", "recognizedProduct": {"productEvidenceKey": "key-1", "name": text}, "resolution": "RESOLVED_EXACT"}],
            "structuredItems": [{"lineId": "item-1", "query": text, "amount": "1", "unit": "count"}],
        }

    def query(self, payload, *, trusted_product_keys=None):
        self.trusted_calls.append(trusted_product_keys)
        return {"providerItems": [], "plans": []}, SimpleNamespace(regions_queried=["ar-caba"])

    def discover(self, query: str, *, region_ids, product_limit: int = 5):
        selected = tuple(region_ids)
        self.discovery_calls.append((query, selected, product_limit))
        values = self.discovery_values
        if values is None:
            values = [{
                "productEvidenceKey": "key-1",
                "name": query,
                "brand": None,
                "gtin": None,
                "score": 100,
                "matchedTokens": [query],
            }]
        metrics = SimpleNamespace(
            regions_queried=list(selected),
            as_dict=lambda: {"regionsQueried": list(selected), "searchPackCorpusScanned": 0},
        )
        return tuple(values[:product_limit]), metrics


class SearchPackM11Tests(unittest.TestCase):
    @staticmethod
    def _input_reader(manager: _FakeInputManager) -> ArgentinaBackendReader:
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        reader.release = SimpleNamespace(release_id="fixture-release")
        reader._searchpack_declared = True
        reader._searchpack_error = None
        reader._searchpack = manager
        reader._input_candidate_cache = OrderedDict()
        return reader

    def test_broad_exact_interpretation_stops_after_first_region_saturation(self) -> None:
        records = {0: fixture_records()[0]}
        first = _FakeInputRegion("ar-a", records, saturated_arroz=True)
        second = _FakeInputRegion("ar-b", records, forbid_arroz=True)
        manager = _FakeInputManager({"ar-a": first, "ar-b": second})
        reader = self._input_reader(manager)

        result = reader.interpret_text("arroz", region_ids=("ar-a", "ar-b"))

        self.assertEqual(result["lines"][0]["resolution"], NEEDS_CLARIFICATION)
        self.assertIsNone(result["lines"][0]["recognizedProduct"])
        self.assertEqual(manager.region_calls, ["ar-a"])
        self.assertEqual(len(first.lookup_calls), 1)
        self.assertEqual(first.get_records_calls, [])
        self.assertEqual(second.lookup_calls, [])
        self.assertEqual(second.get_records_calls, [])
        self.assertEqual(result["diagnostics"]["searchPackCorpusScanned"], 0)
        self.assertEqual(result["diagnostics"]["searchPackDocstoreReads"], 0)

    def test_mixed_lines_skip_saturated_line_but_continue_unsaturated_line(self) -> None:
        records = {0: fixture_records()[2]}
        first = _FakeInputRegion("ar-a", records, saturated_arroz=True)
        second = _FakeInputRegion("ar-b", records, forbid_arroz=True)
        manager = _FakeInputManager({"ar-a": first, "ar-b": second})
        reader = self._input_reader(manager)

        result = reader.interpret_text("arroz\nleche", region_ids=("ar-a", "ar-b"))

        self.assertEqual(result["lines"][0]["resolution"], NEEDS_CLARIFICATION)
        self.assertIsNone(result["lines"][0]["recognizedProduct"])
        self.assertEqual(result["lines"][1]["recognizedProduct"]["productEvidenceKey"], "milk")
        self.assertEqual(manager.region_calls, ["ar-a", "ar-b"])
        self.assertEqual(len(first.lookup_calls), 2)
        self.assertEqual(len(second.lookup_calls), 1)
        self.assertNotIn("exact:arroz", second.lookup_calls[0])
        self.assertIn("exact:leche", second.lookup_calls[0])
        self.assertEqual(first.get_records_calls, [(0,)])
        self.assertEqual(second.get_records_calls, [(0,)])
        self.assertEqual(result["diagnostics"]["searchPackCorpusScanned"], 0)

    def test_discovery_handles_broad_and_specific_terms_without_identity_horizon(self) -> None:
        rows = fixture_records() + [
            {
                "productEvidenceKey": "seven-up",
                "name": "7UP Free PET X 1.5 L",
                "brand": "7UP",
                "canonicalSearchAliases": [],
            }
        ]
        reader, _manager, _output, _manifest = _reader_with_pack(rows)
        with patch("backend.reader._iter_gzip_records", side_effect=AssertionError("discovery must not scan the corpus")):
            arroz, arroz_metrics = reader.discover("arroz", region_ids=("ar-caba",), product_limit=5)
            coca, coca_metrics = reader.discover("Coca-Cola", region_ids=("ar-caba",), product_limit=5)
            sprite, sprite_metrics = reader.discover("Sprite", region_ids=("ar-caba",), product_limit=5)
            seven_up, seven_up_metrics = reader.discover("7UP FREE PET X 1.5L", region_ids=("ar-caba",), product_limit=5)

        self.assertTrue(arroz)
        self.assertEqual(arroz[0]["productEvidenceKey"], "rice")
        self.assertNotIn("rice-seasoning", {item["productEvidenceKey"] for item in arroz})
        self.assertEqual({item["productEvidenceKey"] for item in coca}, {"coca", "coca-zero"})
        self.assertEqual(sprite[0]["productEvidenceKey"], "sprite")
        self.assertEqual(seven_up[0]["productEvidenceKey"], "seven-up")
        for metrics in (arroz_metrics, coca_metrics, sprite_metrics, seven_up_metrics):
            self.assertEqual(metrics.searchpack_corpus_scanned, 0)
            self.assertLessEqual(metrics.searchpack_ranked_records_materialized, 5)
            self.assertEqual(metrics.regions_queried, ["ar-caba"])

    def test_discovery_merges_regions_deduplicates_and_is_order_independent(self) -> None:
        values_by_region = {
            "ar-b": (
                {"productEvidenceKey": "shared", "name": "Arroz Marca B", "brand": None, "gtin": None, "score": 100, "matchedTokens": ["arroz"]},
                {"productEvidenceKey": "b-only", "name": "Arroz B", "brand": None, "gtin": None, "score": 80, "matchedTokens": ["arroz"]},
            ),
            "ar-caba": (
                {"productEvidenceKey": "shared", "name": "Arroz Marca C", "brand": None, "gtin": None, "score": 120, "matchedTokens": ["arroz"]},
                {"productEvidenceKey": "c-only", "name": "Arroz C", "brand": None, "gtin": None, "score": 90, "matchedTokens": ["arroz"]},
            ),
        }
        manager = _FakeDiscoveryManager(values_by_region)
        router = _MultiRegionDiscoveryRouter(tuple(sorted(values_by_region)))
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        reader.router = router
        reader._searchpack_declared = True
        reader._searchpack_error = None
        reader._searchpack = manager
        reader._discovery_cache = {}

        first, first_metrics = reader.discover("arroz", region_ids=("ar-caba", "ar-b"), product_limit=3)
        second, second_metrics = reader.discover("arroz", region_ids=("ar-b", "ar-caba"), product_limit=3)

        self.assertEqual(first, second)
        self.assertEqual([item["productEvidenceKey"] for item in first], ["shared", "c-only", "b-only"])
        self.assertEqual(first[0]["name"], "Arroz Marca C")
        self.assertEqual(first_metrics.regions_queried, ["ar-b", "ar-caba"])
        self.assertEqual(second_metrics.regions_queried, ["ar-b", "ar-caba"])
        self.assertEqual(first_metrics.searchpack_corpus_scanned, 0)
        self.assertLessEqual(first_metrics.searchpack_ranked_records_materialized, 5 * len(first_metrics.regions_queried))
        self.assertEqual(router.contract_calls, [])
        self.assertEqual(manager.calls, ["ar-b", "ar-caba", "ar-b", "ar-caba"])

    def test_broad_discovery_is_not_limited_by_exact_identity_horizon(self) -> None:
        rows = [
            {
                "productEvidenceKey": f"arroz-{index:03d}",
                "name": f"Arroz Marca {index:03d} 1 kg",
                "brand": None,
                "canonicalSearchAliases": [],
            }
            for index in range(257)
        ]
        reader, _manager, _output, _manifest = _reader_with_pack(rows)
        with patch("backend.reader._iter_gzip_records", side_effect=AssertionError("discovery must not scan the corpus")):
            values, metrics = reader.discover("arroz", region_ids=("ar-caba",), product_limit=5)
        self.assertEqual(len(values), 5)
        self.assertEqual(values[0]["productEvidenceKey"], "arroz-000")
        self.assertEqual(metrics.searchpack_saturated_features, 0)
        self.assertEqual(metrics.searchpack_corpus_scanned, 0)
        self.assertEqual(metrics.searchpack_ranked_candidates, 257)
        self.assertEqual(metrics.searchpack_ranked_records_materialized, 5)

    def test_discovery_can_be_restricted_to_routed_regions(self) -> None:
        values_by_region = {
            "ar-b": ({"productEvidenceKey": "b", "name": "Arroz B", "brand": None, "gtin": None, "score": 100, "matchedTokens": ["arroz"]},),
            "ar-caba": ({"productEvidenceKey": "c", "name": "Arroz C", "brand": None, "gtin": None, "score": 100, "matchedTokens": ["arroz"]},),
        }
        manager = _FakeDiscoveryManager(values_by_region)
        router = _MultiRegionDiscoveryRouter(tuple(sorted(values_by_region)))
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        reader.router = router
        reader._searchpack_declared = True
        reader._searchpack_error = None
        reader._searchpack = manager
        reader._discovery_cache = {}

        values, metrics = reader.discover("arroz", region_ids=("ar-caba",), product_limit=5)

        self.assertEqual([item["productEvidenceKey"] for item in values], ["c"])
        self.assertEqual(metrics.regions_queried, ["ar-caba"])
        self.assertEqual(router.contract_calls, [])
        self.assertEqual(manager.calls, ["ar-caba"])

    def test_exact_key_path_skips_lexical_ranked_search(self) -> None:
        reader, manager, _output, _manifest = _reader_with_pack(fixture_records())
        contract = reader.router.contract("ar-caba")
        region = manager.region("ar-caba")
        with patch.object(region, "search_many", side_effect=AssertionError("lexical ranked search must be skipped")):
            values = reader._search_many(contract, ("Arroz Largo Fino 1 kg",), trusted_product_keys=("rice",))
        self.assertEqual(len(values), 1)
        self.assertEqual(values[0][0]["productEvidenceKey"], "rice")

    def test_plan_consumes_preserved_partition_identity(self) -> None:
        reader, manager, _output, _manifest = _reader_with_pack([{
            "productEvidenceKey": "rice",
            "name": "Arroz Largo Fino 1 kg",
            "brand": None,
            "canonicalSearchAliases": [],
            "partitionId": "p-rice",
        }])
        contract = reader.router.contract("ar-caba")
        contract.partition_descriptors = {
            "p-rice": {
                "packId": "pack-rice",
                "path": "pack-rice.bin",
                "byteOffset": 0,
                "byteLength": 1,
                "bytes": 1,
                "sha256": "0" * 64,
                "uncompressedBytes": 1,
                "uncompressedSha256": "1" * 64,
                "recordCount": 1,
            }
        }
        contract.pack_descriptors = {"pack-rice": {"bytes": 1}}
        contract.bootstrap_bytes = 1
        contract.manifest_bytes = 1
        contract.search_descriptor = {"bytes": 1}
        contract.store_descriptor = {"bytes": 1}

        values = manager.region("ar-caba").search_many(("arroz",), product_limit=5, candidate_bound=100_000)
        plan = reader._plan(contract, ("arroz",), values_by_query=values)

        self.assertEqual(plan.partition_ids, ("p-rice",))
        self.assertEqual(plan.slices[0]["partitionId"], "p-rice")
        self.assertEqual(plan.product_candidates[0]["partitionId"], "p-rice")

    def test_ten_line_shop_request_uses_searchpack_plan_without_key_error(self) -> None:
        reader, manager, _output, _manifest = _reader_with_pack([{
            "productEvidenceKey": "rice",
            "name": "Arroz Largo Fino 1 kg",
            "brand": None,
            "canonicalSearchAliases": [],
            "partitionId": "p-rice",
        }])
        reader.release = SimpleNamespace(release_id="fixture-release", freshness_status="FRESH", profile={"name": "fixture"})
        contract = reader.router.contract("ar-caba")
        contract.partition_descriptors = {
            "p-rice": {
                "packId": "pack-rice",
                "path": "pack-rice.bin",
                "byteOffset": 0,
                "byteLength": 1,
                "bytes": 1,
                "sha256": "0" * 64,
                "uncompressedBytes": 1,
                "uncompressedSha256": "1" * 64,
                "recordCount": 1,
            }
        }
        contract.pack_descriptors = {"pack-rice": {"bytes": 1}}
        contract.bootstrap_bytes = 1
        contract.manifest_bytes = 1
        contract.search_descriptor = {"bytes": 1}
        contract.store_descriptor = {"bytes": 1}
        reader.router = SimpleNamespace(
            route=lambda **kwargs: (RegionSelection("ar-caba", ("store-1",)),),
            contract=lambda _region_id: contract,
            stores=lambda _region_id: {
                "store-1": {
                    "name": "Fixture Store",
                    "type": "Supermercado",
                    "address": {"street": "Calle Uno", "number": "1"},
                    "locality": "CABA",
                    "province": "AR-C",
                    "latitude": "-34",
                    "longitude": "-58",
                    "geoStatus": "VALID",
                }
            },
        )
        service = BackendService("", release_manager=_StubReleaseManager())
        service._reader = lambda _handle: reader
        payload = {
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "items": [
                {"lineId": f"item-{index}", "query": "arroz", "amount": "1", "unit": "count"}
                for index in range(1, 11)
            ],
        }

        with patch.object(reader, "_member", return_value=[]):
            response = service.shop(payload, correlation_id="ten-line")

        self.assertEqual(response["requestId"], "ten-line")
        self.assertIsInstance(response["result"], dict)
        self.assertEqual(response["regionsQueried"], ["ar-caba"])
        self.assertEqual(response["evidenceSemantics"]["availability"], "UNKNOWN")

    def test_missing_exact_key_is_safe_for_one_region(self) -> None:
        reader, _manager, _output, _manifest = _reader_with_pack(fixture_records())
        values = reader._search_many(reader.router.contract("ar-caba"), ("anything",), trusted_product_keys=("missing",))
        self.assertEqual(values, ((),))

    def test_corrupt_key_lexicon_fails_closed(self) -> None:
        _reader, _manager, output, manifest = _reader_with_pack(fixture_records())
        descriptor = next(item for item in manifest["objects"] if item["path"].endswith("/key-lexicon.bin"))
        object_path = output / "objects" / "sha256" / descriptor["sha256"]
        object_path.write_bytes(b"corrupt")
        artifacts = ManifestReleaseArtifactStore(LocalFilesystemObjectStore(output), manifest, release_id=f"release{SEARCHPACK_RELEASE_SUFFIX}")
        manager = SearchPackManager(artifacts, manifest)
        with self.assertRaises(SearchPackError):
            manager.region("ar-caba").lookup_product_key("rice")

    def test_service_shop_text_preserves_internal_identity(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader
        shop_payload = {"latitude": "-34", "longitude": "-58", "radiusKm": "5", "text": "arroz 1kg"}
        service.shop_text(shop_payload)
        self.assertEqual(reader.trusted_calls[-1], {"item-1": "key-1"})

    def test_service_search_uses_discovery_without_interpreter(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader
        response = service.search({"latitude": "-34", "longitude": "-58", "radiusKm": "5", "query": "arroz"})
        self.assertEqual(response["searchMode"], "DISCOVERY")
        self.assertEqual(response["resolution"], "DISCOVERY")
        self.assertEqual(response["regionsQueried"], ["ar-caba"])
        self.assertEqual(response["matches"][0]["productEvidenceKey"], "key-1")
        self.assertEqual(reader.discovery_calls, [("arroz", ("ar-caba",), 6)])
        self.assertEqual(reader.interpret_calls, [])
        self.assertEqual(response["pagination"], {"page": 0, "pageSize": 5, "hasMore": False, "nextPage": None, "limitReached": False})

    def test_service_search_filters_explicit_carrot_form_without_inventing_rows(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": "carrot-lotion",
                "name": "LOCION PROT. SOLAR FPS 30 CARROT 240 ML",
                "brand": "HAWAIIAN TROPIC",
                "gtin": None,
                "score": 120,
                "matchedTokens": ["carrot", "juice"],
            },
            {
                "productEvidenceKey": "carrot-juice",
                "name": "JUICE MARKET HAPPY CARROT 500 ML",
                "brand": "JUICE MARKET",
                "gtin": None,
                "score": 110,
                "matchedTokens": ["carrot", "juice"],
            },
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "carrot juice",
        })

        self.assertEqual([item["productEvidenceKey"] for item in response["matches"]], ["carrot-juice"])
        self.assertIsNone(response["clarification"])

    def test_service_search_exposes_observed_carrot_clarification(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "name": name,
                "brand": None,
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["carrot"],
            }
            for index, (key, name) in enumerate((
                ("carrot-vegetable", "BABY CARROT"),
                ("carrot-drink", "JUICE MARKET HAPPY CARROT 500 ML"),
                ("carrot-dessert", "POSTRE CARROT CAKE 140 G"),
                ("carrot-cosmetics", "LOCION SOLAR CARROT 240 ML"),
            ))
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "carrot",
        })

        clarification = response["clarification"]
        self.assertIsNotNone(clarification)
        assert clarification is not None
        self.assertEqual(clarification["family"], "carrot")
        self.assertEqual(
            {choice["id"] for choice in clarification["choices"]},
            {"carrot:vegetable", "carrot:drink", "carrot:dessert", "carrot:cosmetics"},
        )

    def test_service_search_exposes_generic_egg_clarification_and_filters_form(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "name": name,
                "brand": "Marca",
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["huevos"],
            }
            for index, (key, name) in enumerate((
                ("egg-whole", "HUEVOS FRESCOS X12"),
                ("egg-liquid", "HUEVOS LIQUIDOS 500 ML"),
                ("egg-powder", "HUEVO EN POLVO 100 G"),
            ))
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "huevos",
        })
        clarification = response["clarification"]
        self.assertIsNotNone(clarification)
        assert clarification is not None
        self.assertEqual(clarification["family"], "egg")
        self.assertEqual(
            {choice["id"] for choice in clarification["choices"]},
            {"egg:liquid", "egg:powder", "egg:whole_egg"},
        )

        liquid = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "huevos liquid",
        })
        self.assertEqual([item["productEvidenceKey"] for item in liquid["matches"]], ["egg-liquid"])

    def test_service_search_keeps_reviewed_meat_family_free_of_pet_mentions(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": "pork-cut",
                "name": "ASADO DE CERDO X KG",
                "brand": "Marca",
                "gtin": None,
                "score": 100,
                "matchedTokens": ["cerdo"],
            },
            {
                "productEvidenceKey": "pork-pet",
                "name": "ALIM.P/PERROS ADULTOS CARNE, POLLO Y CERDO PEDIGREE BSA 8KG",
                "brand": "Pedigree",
                "gtin": None,
                "score": 99,
                "matchedTokens": ["cerdo"],
            },
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "cerdo",
        })

        self.assertEqual([item["productEvidenceKey"] for item in response["matches"]], ["pork-cut"])
        self.assertEqual(reader.discovery_calls[0][0], "cerdo")

    def test_service_search_prioritizes_evidence_backed_shell_eggs(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": "egg-no-offer",
                "name": "HUEVO BLANCO IDENTIDAD SIN OFERTA",
                "brand": "Marca",
                "gtin": None,
                "score": 120,
                "matchedTokens": ["huevo"],
                "qualifiedOffers": [],
            },
            {
                "productEvidenceKey": "egg-priced",
                "name": "HUEVO BLANCO CON OFERTA VIGENTE",
                "brand": "Marca",
                "gtin": None,
                "score": 100,
                "matchedTokens": ["huevo"],
                "qualifiedOffers": [{"offerKey": "offer-1"}],
            },
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        with patch("backend.service._production_android_ui_authorized", return_value=True):
            response = service.search({
                "latitude": "-34",
                "longitude": "-58",
                "radiusKm": "5",
                "query": "huevo de gallina",
            })

        self.assertEqual(
            [item["productEvidenceKey"] for item in response["matches"]],
            ["egg-priced", "egg-no-offer"],
        )
        self.assertIsNone(response["clarification"])

    def test_service_search_prioritizes_evidence_backed_eggs_for_generic_query(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": "egg-no-offer",
                "name": "HUEVO BLANCO IDENTIDAD SIN OFERTA",
                "brand": "Marca",
                "gtin": None,
                "score": 120,
                "matchedTokens": ["huevo"],
                "qualifiedOffers": [],
            },
            {
                "productEvidenceKey": "egg-priced",
                "name": "HUEVO BLANCO CON OFERTA VIGENTE",
                "brand": "Marca",
                "gtin": None,
                "score": 100,
                "matchedTokens": ["huevo"],
                "qualifiedOffers": [{"offerKey": "offer-1"}],
            },
            {
                "productEvidenceKey": "egg-toy",
                "name": "SMASH DINO NANO EGG",
                "brand": "Marca",
                "gtin": None,
                "score": 90,
                "matchedTokens": ["egg"],
                "qualifiedOffers": [{"offerKey": "toy-offer"}],
            },
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        with patch("backend.service._production_android_ui_authorized", return_value=True):
            response = service.search({
                "latitude": "-34",
                "longitude": "-58",
                "radiusKm": "5",
                "query": "eggs",
            })

        self.assertEqual(
            [item["productEvidenceKey"] for item in response["matches"]],
            ["egg-priced", "egg-no-offer"],
        )

    def test_service_search_prioritizes_verified_unit_prices_for_rice(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": "rice-unknown-quantity",
                "name": "ARROZ PARA RISOTTO X KG",
                "brand": "Marca",
                "gtin": None,
                "score": 120,
                "matchedTokens": ["arroz"],
                "qualifiedOffers": [{"offerKey": "offer-unknown", "unitPrice": None}],
            },
            {
                "productEvidenceKey": "rice-known-quantity",
                "name": "ARROZ CORTO PULIDO 400 G",
                "brand": "Marca",
                "gtin": None,
                "score": 100,
                "matchedTokens": ["arroz"],
                "qualifiedOffers": [{
                    "offerKey": "offer-known",
                    "unitPrice": {"amount": "250", "currency": "ARS", "basis": "100 g"},
                }],
            },
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        with patch("backend.service._production_android_ui_authorized", return_value=True):
            response = service.search({
                "latitude": "-34",
                "longitude": "-58",
                "radiusKm": "5",
                "query": "rice",
            })

        self.assertEqual(
            [item["productEvidenceKey"] for item in response["matches"]],
            ["rice-known-quantity", "rice-unknown-quantity"],
        )

    def test_service_search_uses_family_base_for_explicit_form(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": "carrot-drink",
                "name": "DISC:GRE JUICE MARKET HAPPY CARROT 500M",
                "brand": "Marca",
                "gtin": None,
                "score": 100,
                "matchedTokens": ["carrot"],
                "qualifiedOffers": [],
            },
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "carrot juice",
        })

        self.assertEqual(reader.discovery_calls[0][0], "carrot")
        self.assertEqual([item["productEvidenceKey"] for item in response["matches"]], ["carrot-drink"])

    def test_service_search_uses_canonical_family_for_bilingual_gum_and_filters_form(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "name": name,
                "brand": "Marca",
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["chicle"],
                "qualifiedOffers": [],
            }
            for index, (key, name) in enumerate((
                ("gum-sugar-free", "CHICLE CERO AZUCARES MENTA X 14 G"),
                ("gum-filled", "CHICLE CONFIT RELL FRUTILLA X 17 G"),
                ("gum-scent", "AROMATIZADOR CHICLE"),
            ))
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        broad = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "chewing gum",
        })
        self.assertEqual(reader.discovery_calls[0][0], "chicle")
        self.assertEqual(
            [item["productEvidenceKey"] for item in broad["matches"]],
            ["gum-sugar-free", "gum-filled"],
        )
        self.assertIsNotNone(broad["clarification"])

        sugar_free = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "sugar free chewing gum",
        })
        self.assertEqual(reader.discovery_calls[-1][0], "chicle")
        self.assertEqual([item["productEvidenceKey"] for item in sugar_free["matches"]], ["gum-sugar-free"])
        self.assertIsNone(sugar_free["clarification"])

    def test_service_search_uses_canonical_bowl_family_and_filters_appliances(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "name": name,
                "brand": "Marca",
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["bowl"],
                "qualifiedOffers": [],
            }
            for index, (key, name) in enumerate((
                ("bowl-direct", "BOWL CERAMICA CAROL 20 CM"),
                ("bowl-mixer", "BATIDORA CON BOWL BLACK DECKER 500W"),
                ("bowl-chopper", "PICADOR SUONO BOWL VIDRIO 500W"),
            ))
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "bowl",
        })

        self.assertEqual(reader.discovery_calls[0][0], "bowl")
        self.assertEqual([item["productEvidenceKey"] for item in response["matches"]], ["bowl-direct"])
        self.assertIsNone(response["clarification"])

    def test_service_search_uses_canonical_appliance_family_and_filters_accessory(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "name": name,
                "brand": "Marca",
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["cafetera"],
                "qualifiedOffers": [],
            }
            for index, (key, name) in enumerate((
                ("coffee-direct", "CAFETERA DE FILTRO ATMA 1.8L"),
                ("coffee-context", "JARRA MEDIDORA 1LT CON EXPRIMIDOR"),
            ))
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "coffee maker",
        })

        self.assertEqual(reader.discovery_calls[0][0], "cafetera")
        self.assertEqual([item["productEvidenceKey"] for item in response["matches"]], ["coffee-direct"])
        self.assertIsNone(response["clarification"])

    def test_service_search_exposes_new_food_family_clarification_and_filters_form(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "name": name,
                "brand": "Marca",
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["alfajor"],
                "qualifiedOffers": [],
            }
            for index, (key, name) in enumerate((
                ("alfajor-chocolate", "ALFAJOR CHOCOLATE BLANCO X 60 G"),
                ("alfajor-dulce", "ALFAJOR CON DULCE DE LECHE X 60 G"),
            ))
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        broad = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "alfajor",
        })
        clarification = broad["clarification"]
        self.assertIsNotNone(clarification)
        assert clarification is not None
        self.assertEqual(clarification["family"], "alfajor")
        self.assertEqual(
            {choice["id"] for choice in clarification["choices"]},
            {"alfajor:chocolate", "alfajor:dulce_de_leche"},
        )

        chocolate = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "alfajor chocolate",
        })
        self.assertEqual(reader.discovery_calls[-1][0], "alfajor")
        self.assertEqual(
            [item["productEvidenceKey"] for item in chocolate["matches"]],
            ["alfajor-chocolate"],
        )
        self.assertIsNone(chocolate["clarification"])

    def test_service_search_keeps_food_eggs_and_clarifies_toy_eggs(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "name": name,
                "brand": "Marca",
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["huevos"],
            }
            for index, (key, name) in enumerate((
                ("egg-food", "HUEVOS FRESCOS X12"),
                ("egg-dino", "SMASH DINO NANO EGG"),
                ("egg-chocolate", "HUEVO CHOCOLATE SORPRESA"),
            ))
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        response = service.search({
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "query": "eggs",
        })

        self.assertEqual([item["productEvidenceKey"] for item in response["matches"]], ["egg-food"])
        clarification = response["clarification"]
        self.assertIsNotNone(clarification)
        assert clarification is not None
        self.assertEqual(
            {choice["id"] for choice in clarification["choices"]},
            {"egg:whole_egg", "egg:toy", "egg:chocolate"},
        )

    def test_service_search_uses_configured_taxonomy_sidecar_without_exposing_it(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": key,
                "providerProductId": key,
                "name": name,
                "brand": "Marca",
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["huevos"],
            }
            for index, (key, name) in enumerate((
                ("egg-whole", "HUEVOS FRESCOS X12"),
                ("egg-liquid", "HUEVOS LIQUIDOS 500 ML"),
            ))
        ]
        with tempfile.TemporaryDirectory() as directory:
            taxonomy_path = Path(directory) / "product-taxonomy.jsonl.gz"
            records = [
                {
                    "providerProductId": "egg-whole",
                    "identityKeys": ["egg-whole"],
                    "taxonomy": {
                        "familyCandidates": [{"family": "egg", "observedForms": ["whole_egg"]}],
                        "formCandidates": [{"key": "whole_egg", "labelEs": "Huevo entero", "labelEn": "Whole egg"}],
                    },
                },
                {
                    "providerProductId": "egg-liquid",
                    "identityKeys": ["egg-liquid"],
                    "taxonomy": {
                        "familyCandidates": [{"family": "egg", "observedForms": ["liquid"]}],
                        "formCandidates": [{"key": "liquid", "labelEs": "Líquido", "labelEn": "Liquid"}],
                    },
                },
            ]
            with gzip.open(taxonomy_path, "wt", encoding="utf-8", newline="") as handle:
                for record in records:
                    handle.write(json.dumps(record, ensure_ascii=False) + "\n")
            service = BackendService(
                "",
                release_manager=manager,
                taxonomy_index_path=taxonomy_path,
            )
            service._reader = lambda _handle: reader

            response = service.search({
                "latitude": "-34",
                "longitude": "-58",
                "radiusKm": "5",
                "query": "huevos",
            })
            clarification = response["clarification"]
            self.assertIsNotNone(clarification)
            assert clarification is not None
            self.assertEqual({choice["id"] for choice in clarification["choices"]}, {"egg:liquid", "egg:whole_egg"})
            self.assertTrue(all("_taxonomy" not in item for item in response["matches"]))

            liquid = service.search({
                "latitude": "-34",
                "longitude": "-58",
                "radiusKm": "5",
                "query": "huevos liquid",
            })
            self.assertEqual([item["productEvidenceKey"] for item in liquid["matches"]], ["egg-liquid"])

    def test_searchpack_uses_reviewed_bilingual_identity_aliases(self) -> None:
        reader, manager, _output, _manifest = _reader_with_pack(fixture_records())
        del reader

        values = manager.region("ar-caba").search_many(
            ("rice",),
            product_limit=10,
            candidate_bound=100_000,
        )[0]

        self.assertEqual([item["productEvidenceKey"] for item in values], ["rice"])
        self.assertEqual(values[0]["name"], "Arroz Largo Fino 1 kg")

    def test_searchpack_indexes_bilingual_observed_carrot_forms(self) -> None:
        rows = [
            {
                "productEvidenceKey": "carrot-cake",
                "name": "TORTA DE ZANAHORIA 400 G",
                "brand": "DULCE",
                "gtin": None,
                "quantity": {"value": "400", "unit": "g"},
            },
            {
                "productEvidenceKey": "carrot-juice",
                "name": "JUGO DE ZANAHORIA 1 L",
                "brand": "BEBIDA",
                "gtin": None,
                "quantity": {"value": "1", "unit": "l"},
            },
        ]
        _reader, manager, _output, _manifest = _reader_with_pack(rows)

        cake = manager.region("ar-caba").search_many(
            ("carrot cake",), product_limit=5, candidate_bound=100_000
        )[0]
        juice = manager.region("ar-caba").search_many(
            ("carrot juice",), product_limit=5, candidate_bound=100_000
        )[0]

        self.assertEqual(cake[0]["productEvidenceKey"], "carrot-cake")
        self.assertEqual(juice[0]["productEvidenceKey"], "carrot-juice")

    def test_service_search_pages_deterministically_with_bounded_continuation(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        reader.discovery_values = [
            {
                "productEvidenceKey": f"key-{index}",
                "name": f"Arroz {index}",
                "brand": None,
                "gtin": None,
                "score": 100 - index,
                "matchedTokens": ["arroz"],
            }
            for index in range(11)
        ]
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader

        first = service.search({"latitude": "-34", "longitude": "-58", "radiusKm": "5", "query": "arroz", "page": 0})
        second = service.search({"latitude": "-34", "longitude": "-58", "radiusKm": "5", "query": "arroz", "page": 1})

        self.assertEqual([item["productEvidenceKey"] for item in first["matches"]], [f"key-{index}" for index in range(5)])
        self.assertEqual([item["productEvidenceKey"] for item in second["matches"]], [f"key-{index}" for index in range(5, 10)])
        self.assertEqual(first["pagination"]["nextPage"], 1)
        self.assertTrue(first["pagination"]["hasMore"])
        self.assertFalse(second["pagination"]["limitReached"])
        self.assertEqual(reader.discovery_calls[-2:], [("arroz", ("ar-caba",), 6), ("arroz", ("ar-caba",), 11)])

    def test_public_shop_mapping_cannot_inject_trusted_identity(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader
        payload = {
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "items": [{"lineId": "item-1", "query": "arroz", "amount": "1", "unit": "count"}],
            "trusted_product_keys": {"item-1": "attacker-controlled"},
        }
        service.shop(payload)
        self.assertIsNone(reader.trusted_calls[-1])

    def test_public_shop_accepts_only_explicit_search_selected_product_key(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader
        payload = {
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "items": [{
                "lineId": "item-1",
                "query": "arroz",
                "amount": "1",
                "unit": "count",
                "productEvidenceKey": "search-selected-key",
            }],
        }
        service.shop(payload)
        self.assertEqual(reader.trusted_calls[-1], {"item-1": "search-selected-key"})

    def test_public_shop_rejects_empty_explicit_line_id_for_trusted_identity(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader
        payload = {
            "latitude": "-34",
            "longitude": "-58",
            "radiusKm": "5",
            "items": [{
                "lineId": "",
                "query": "arroz",
                "amount": "1",
                "unit": "count",
                "productEvidenceKey": "search-selected-key",
            }],
        }
        with self.assertRaises(BackendQueryError):
            service.shop(payload)


class _LatencyStore:
    def __init__(self, root: Path, latency_seconds: float) -> None:
        self.delegate = LocalFilesystemObjectStore(root)
        self.latency_seconds = latency_seconds
        self.operations: list[tuple[str, str]] = []
        self.range_details: list[tuple[str, int, int]] = []

    def _delay(self, operation: str, key: str) -> None:
        self.operations.append((operation, key))
        time.sleep(self.latency_seconds)

    def head(self, key: str) -> ObjectMetadata:
        self._delay("head", key)
        return self.delegate.head(key)

    def get(self, key: str) -> bytes:
        self._delay("get", key)
        return self.delegate.get(key)

    def stream(self, key: str, *, chunk_size: int = 1024 * 1024):
        self._delay("stream", key)
        yield from self.delegate.stream(key, chunk_size=chunk_size)

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        self._delay("range", key)
        self.range_details.append((key, offset, length))
        return self.delegate.get_range(key, offset, length)

    def exists(self, key: str) -> bool:
        return self.delegate.exists(key)

    def count(self, operation: str) -> int:
        return sum(name == operation for name, _key in self.operations)


def _offer_partition_fixture(partition_count: int = 8, *, latency_seconds: float = 0.03):
    """Build tiny real gzip/hash-verified offer members with delayed ranges."""

    root = Path(tempfile.mkdtemp(prefix="valuepilot-m11-offer-partitions-"))
    (root / "objects" / "sha256").mkdir(parents=True)
    objects: list[dict[str, object]] = []
    partitions: list[dict[str, object]] = []
    for index in range(partition_count):
        payload = (json.dumps({"offerId": index + 1}, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
        compressed = gzip.compress(payload, mtime=0)
        digest = hashlib.sha256(compressed).hexdigest()
        (root / "objects" / "sha256" / digest).write_bytes(compressed)
        path = f"micro-1024/regions/ar-caba/offers/p{index:03d}.jsonl.gz"
        objects.append({"path": path, "bytes": len(compressed), "sha256": digest})
        partitions.append({
            "partitionId": f"p{index:03d}",
            "path": path,
            "byteOffset": 0,
            "byteLength": len(compressed),
            "bytes": len(compressed),
            "sha256": digest,
            "uncompressedBytes": len(payload),
            "uncompressedSha256": hashlib.sha256(payload).hexdigest(),
            "recordCount": 1,
        })
    manifest = {"objects": objects}
    store = _LatencyStore(root, latency_seconds)
    artifacts = ManifestReleaseArtifactStore(store, manifest, release_id="offer-fixture")
    reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
    reader.release = SimpleNamespace(release_id="offer-fixture")
    reader.artifacts = artifacts
    reader.cache = VerifiedEvidenceCache(max_bytes=1024 * 1024)
    return root, reader, store, tuple(partitions)


class SearchPackRemoteLatencyTests(unittest.TestCase):
    def test_rank_query_parallel_load_preserves_results_reads_and_reduces_delayed_range_time(self) -> None:
        rows = [
            {"productEvidenceKey": "arroz", "name": "Arroz Marca 1 kg", "brand": "Marca", "canonicalSearchAliases": []},
            {"productEvidenceKey": "leche", "name": "Leche Marca 1 l", "brand": "Marca", "canonicalSearchAliases": []},
            {"productEvidenceKey": "manteca", "name": "Manteca Marca 500 g", "brand": "Marca", "canonicalSearchAliases": []},
            {"productEvidenceKey": "coca", "name": "Coca Cola 1.5 l", "brand": "Coca", "canonicalSearchAliases": []},
            {"productEvidenceKey": "sprite", "name": "Sprite 1.5 l", "brand": "Sprite", "canonicalSearchAliases": []},
            {"productEvidenceKey": "seven", "name": "7UP Free Pet 1.5 l", "brand": "7UP", "canonicalSearchAliases": []},
            {"productEvidenceKey": "detergente", "name": "Detergente Marca 500 ml", "brand": "Marca", "canonicalSearchAliases": []},
            {"productEvidenceKey": "aceite", "name": "Aceite Girasol 1 l", "brand": "Marca", "canonicalSearchAliases": []},
        ]
        queries = ("arroz", "leche", "manteca", "Coca-Cola", "Sprite", "7UP FREE PET X 1.5L", "detergente", "aceite")

        def build_case():
            source, release_id = _fixture_workspace(rows)
            output = _new_output("valuepilot-m11-rank-latency-")
            derive_searchpack_workspace(source, output, [release_id])
            derived_id = f"{release_id}{SEARCHPACK_RELEASE_SUFFIX}"
            manifest = json.loads((output / "releases" / derived_id / "manifest.json").read_bytes())
            store = _LatencyStore(output, 0.03)
            manager = SearchPackManager(ManifestReleaseArtifactStore(store, manifest, release_id=derived_id), manifest)
            return manager.region("ar-caba"), store

        sequential_region, sequential_store = build_case()
        sequential_stats = SearchPackStats()
        started = time.perf_counter()
        with patch("tools.argentina_searchpack.MAX_RANK_WORKERS", 1):
            sequential = sequential_region.search_many(queries, product_limit=5, candidate_bound=100_000, stats=sequential_stats)
        sequential_elapsed = time.perf_counter() - started

        parallel_region, parallel_store = build_case()
        parallel_stats = SearchPackStats()
        started = time.perf_counter()
        with patch("tools.argentina_searchpack.MAX_RANK_WORKERS", MAX_RANK_WORKERS):
            parallel = parallel_region.search_many(queries, product_limit=5, candidate_bound=100_000, stats=parallel_stats)
        parallel_elapsed = time.perf_counter() - started

        self.assertEqual(parallel, sequential)
        self.assertEqual(parallel_stats, sequential_stats)
        self.assertEqual(parallel_store.count("range"), sequential_store.count("range"))
        self.assertEqual(sorted(parallel_store.range_details), sorted(sequential_store.range_details))
        self.assertEqual(parallel_stats.bytes_read, sequential_stats.bytes_read)
        self.assertEqual(parallel_stats.ranked_records_materialized, 8)
        self.assertLessEqual(parallel_stats.ranked_records_materialized, 5 * len(set(queries)))
        self.assertLess(parallel_elapsed, sequential_elapsed * 0.85)

    def test_offer_partition_parallel_load_preserves_order_and_reduces_delayed_range_time(self) -> None:
        _root, sequential_reader, sequential_store, partitions = _offer_partition_fixture(latency_seconds=0.03)
        sequential_metrics = RequestMetrics()
        started = time.perf_counter()
        sequential = [sequential_reader._member("ar-caba", partition, sequential_metrics) for partition in partitions]
        sequential_elapsed = time.perf_counter() - started

        _root, optimized_reader, optimized_store, optimized_partitions = _offer_partition_fixture(latency_seconds=0.03)
        optimized_metrics = RequestMetrics()
        started = time.perf_counter()
        optimized = optimized_reader._load_members("ar-caba", optimized_partitions, optimized_metrics)
        optimized_elapsed = time.perf_counter() - started

        self.assertEqual(optimized, sequential)
        self.assertEqual(sequential_store.count("range"), len(partitions))
        self.assertEqual(optimized_store.count("range"), len(partitions))
        self.assertEqual(optimized_metrics.range_reads, sequential_metrics.range_reads)
        self.assertEqual(optimized_metrics.object_reads, sequential_metrics.object_reads)
        self.assertEqual(optimized_metrics.bytes_read, sequential_metrics.bytes_read)
        self.assertLess(optimized_elapsed, sequential_elapsed * 0.75)

    def test_offer_partition_parallelism_is_bounded_and_metrics_merge_in_partition_order(self) -> None:
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        partitions = tuple({"partitionId": f"p{index:03d}"} for index in range(9))
        lock = threading.Lock()
        active = 0
        maximum_active = 0

        def load(_region_id: str, partition: dict[str, object], metrics: RequestMetrics):
            nonlocal active, maximum_active
            with lock:
                active += 1
                maximum_active = max(maximum_active, active)
            time.sleep(0.01)
            with lock:
                active -= 1
            index = int(str(partition["partitionId"])[1:])
            metrics.object_reads = 1
            metrics.range_reads = 1
            metrics.bytes_read = index + 1
            metrics.range_reads_ms = 2.0
            metrics.decode_ms = 3.0
            metrics.cache_misses = 1
            return [{"partitionId": partition["partitionId"], "order": index}]

        with patch.object(reader, "_member", side_effect=load):
            metrics = RequestMetrics()
            values = reader._load_members("ar-caba", partitions, metrics)

        self.assertLessEqual(maximum_active, MAX_OFFER_PARTITION_CONCURRENCY)
        self.assertEqual([item[0]["partitionId"] for item in values], [item["partitionId"] for item in partitions])
        self.assertEqual(metrics.object_reads, len(partitions))
        self.assertEqual(metrics.range_reads, len(partitions))
        self.assertEqual(metrics.bytes_read, sum(range(1, len(partitions) + 1)))
        self.assertEqual(metrics.cache_misses, len(partitions))

    def test_offer_partition_worker_failure_is_fail_closed(self) -> None:
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        partitions = tuple({"partitionId": f"p{index:03d}"} for index in range(6))

        def load(_region_id: str, partition: dict[str, object], _metrics: RequestMetrics):
            if partition["partitionId"] == "p002":
                raise RuntimeError("immutable offer member failed")
            return [{"partitionId": partition["partitionId"]}]

        with patch.object(reader, "_member", side_effect=load):
            with self.assertRaisesRegex(RuntimeError, "immutable offer member failed"):
                reader._load_members("ar-caba", partitions, RequestMetrics())

    def test_offer_partition_integrity_failure_is_not_hidden_by_parallel_loader(self) -> None:
        root, reader, _store, partitions = _offer_partition_fixture(partition_count=2, latency_seconds=0)
        descriptor = partitions[1]
        digest = str(descriptor["sha256"])
        object_path = root / "objects" / "sha256" / digest
        data = object_path.read_bytes()
        object_path.write_bytes(bytes([data[0] ^ 1]) + data[1:])

        with self.assertRaisesRegex(Exception, r"hash.*mismatch"):
            reader._load_members("ar-caba", partitions, RequestMetrics())

    def test_rank_directory_reads_use_declared_blocks(self) -> None:
        rows = [
            {"productEvidenceKey": f"key-{index:04d}", "name": "7UP FREE PET X 1.5L", "brand": "7UP", "canonicalSearchAliases": []}
            for index in range(1024)
        ]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-m11-rank-directory-blocks-")
        derive_searchpack_workspace(source, output, [release_id])
        derived_id = f"{release_id}{SEARCHPACK_RELEASE_SUFFIX}"
        manifest = json.loads((output / "releases" / derived_id / "manifest.json").read_bytes())
        store = _LatencyStore(output, 0)
        artifacts = ManifestReleaseArtifactStore(store, manifest, release_id=derived_id)
        manager = SearchPackManager(artifacts, manifest)
        region = manager.region("ar-caba")
        stats = SearchPackStats()

        values = region.search_many(("7UP FREE PET X 1.5L",), product_limit=5, candidate_bound=100_000, stats=stats)[0]

        def object_key(suffix: str) -> str:
            descriptor = next(item for item in manifest["objects"] if item["path"].endswith(suffix))
            return f"objects/sha256/{descriptor['sha256']}"

        term_key = object_key("/rank-term-directory.bin")
        chunk_key = object_key("/rank-chunk-directory.bin")
        term_ranges = [(offset, length) for key, offset, length in store.range_details if key == term_key]
        chunk_ranges = [(offset, length) for key, offset, length in store.range_details if key == chunk_key]
        term_block = region.metadata["rankTermDirectoryBlocks"][0]
        chunk_block = region.metadata["rankChunkDirectoryBlocks"][0]

        self.assertEqual(len(values), 5)
        self.assertEqual(values[0]["productEvidenceKey"], "key-0000")
        self.assertEqual(stats.ranked_records_materialized, 5)
        self.assertGreater(stats.ranked_term_directory_reads, 1)
        self.assertGreater(stats.ranked_posting_reads, 1)
        self.assertEqual(term_ranges, [(term_block["offset"], term_block["length"])])
        self.assertEqual(chunk_ranges, [(chunk_block["offset"], chunk_block["length"])])
        self.assertTrue(all(length > _TERM_STRUCT.size for _offset, length in term_ranges))
        self.assertTrue(all(length > _CHUNK_STRUCT.size for _offset, length in chunk_ranges))

    def test_remote_latency_is_bounded_by_query_complexity_not_candidate_blocks(self) -> None:
        rows = [
            {"productEvidenceKey": f"key-{index:04d}", "name": "7UP FREE PET X 1.5L", "brand": "7UP", "canonicalSearchAliases": []}
            for index in range(1024)
        ]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-m11-latency-")
        derive_searchpack_workspace(source, output, [release_id])
        derived_id = f"{release_id}{SEARCHPACK_RELEASE_SUFFIX}"
        manifest = json.loads((output / "releases" / derived_id / "manifest.json").read_bytes())
        old_range_counts: list[int] = []
        new_range_counts: list[int] = []
        for latency_ms in (20, 50, 100):
            old_store = _LatencyStore(output, latency_ms / 1000)
            old_artifacts = ManifestReleaseArtifactStore(old_store, manifest, release_id=derived_id)
            old_region = SearchPackManager(old_artifacts, manifest).region("ar-caba")
            old_stats = SearchPackStats()
            # Model the rejected access shape: generic candidates followed by
            # every candidate's docstore block.
            from tools.consumer_input_intelligence import CatalogIndex, parse_intent

            catalog = CatalogIndex(())
            lookup = old_region.lookup_features(catalog.intent_feature_keys(parse_intent("7UP FREE PET X 1.5L", data=catalog.data)), candidate_bound=100_000, stats=old_stats)
            old_region.get_records(lookup.doc_ids, stats=old_stats, max_records=100_000)
            old_range_counts.append(old_store.count("range"))

            new_store = _LatencyStore(output, latency_ms / 1000)
            new_artifacts = ManifestReleaseArtifactStore(new_store, manifest, release_id=derived_id)
            new_region = SearchPackManager(new_artifacts, manifest).region("ar-caba")
            new_stats = SearchPackStats()
            started = time.perf_counter()
            values = new_region.search_many(("7UP FREE PET X 1.5L",), product_limit=5, candidate_bound=100_000, stats=new_stats)
            elapsed_ms = (time.perf_counter() - started) * 1000
            new_range_counts.append(new_store.count("range"))
            self.assertEqual(len(values[0]), 5)
            self.assertEqual(new_stats.ranked_records_materialized, 5)
            self.assertLess(new_store.count("range"), old_store.count("range"))
            self.assertLess(elapsed_ms, old_store.count("range") * latency_ms + 500)
        self.assertEqual(len(set(new_range_counts)), 1)
        self.assertTrue(all(old > new for old, new in zip(old_range_counts, new_range_counts)))


if __name__ == "__main__":
    unittest.main()

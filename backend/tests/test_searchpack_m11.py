from __future__ import annotations

import json
import tempfile
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from backend.artifacts import ManifestReleaseArtifactStore
from backend.object_store import LocalFilesystemObjectStore, ObjectMetadata
from backend.reader import ArgentinaBackendReader, RegionSelection
from backend.service import BackendService
from tools.argentina_searchpack import SearchPackManager, SearchPackStats
from tools.argentina_searchpack import SearchPackError
from tools.build_argentina_searchpack import SEARCHPACK_RELEASE_SUFFIX, derive_searchpack_workspace
from tools.tests.test_argentina_searchpack import _fixture_workspace, _new_output
from tools.tests.test_consumer_input_intelligence import fixture_records


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

    def interpret_text(self, text: str, *, require_quantities: bool = False, region_ids=None):
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


class SearchPackM11Tests(unittest.TestCase):
    def test_exact_key_path_skips_lexical_ranked_search(self) -> None:
        reader, manager, _output, _manifest = _reader_with_pack(fixture_records())
        contract = reader.router.contract("ar-caba")
        region = manager.region("ar-caba")
        with patch.object(region, "search_many", side_effect=AssertionError("lexical ranked search must be skipped")):
            values = reader._search_many(contract, ("Arroz Largo Fino 1 kg",), trusted_product_keys=("rice",))
        self.assertEqual(len(values), 1)
        self.assertEqual(values[0][0]["productEvidenceKey"], "rice")

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

    def test_service_search_and_shop_text_preserve_internal_identity(self) -> None:
        manager = _StubReleaseManager()
        reader = _StubReader()
        service = BackendService("", release_manager=manager)
        service._reader = lambda _handle: reader
        search_payload = {"latitude": "-34", "longitude": "-58", "radiusKm": "5", "query": "arroz"}
        service.search(search_payload)
        self.assertEqual(reader.trusted_calls[-1], {"search-1": "key-1"})
        shop_payload = {"latitude": "-34", "longitude": "-58", "radiusKm": "5", "text": "arroz 1kg"}
        service.shop_text(shop_payload)
        self.assertEqual(reader.trusted_calls[-1], {"item-1": "key-1"})

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


class _LatencyStore:
    def __init__(self, root: Path, latency_seconds: float) -> None:
        self.delegate = LocalFilesystemObjectStore(root)
        self.latency_seconds = latency_seconds
        self.operations: list[tuple[str, str]] = []

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
        return self.delegate.get_range(key, offset, length)

    def exists(self, key: str) -> bool:
        return self.delegate.exists(key)

    def count(self, operation: str) -> int:
        return sum(name == operation for name, _key in self.operations)


class SearchPackRemoteLatencyTests(unittest.TestCase):
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

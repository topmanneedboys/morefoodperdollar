from __future__ import annotations

import json
import tempfile
import threading
import time
import unittest
from collections import Counter
from decimal import Decimal
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from backend.reader import BackendQueryError, MAX_REMOTE_ROUTING_CONCURRENCY, NationalStoreRouter, RegionSelection
from backend.artifacts import ManifestReleaseArtifactStore

NATIONAL_REGION_IDS = (
    "ar-a", "ar-b", "ar-caba", "ar-d", "ar-e", "ar-f", "ar-g", "ar-h", "ar-j", "ar-k", "ar-l", "ar-m",
    "ar-n", "ar-p", "ar-q", "ar-r", "ar-s", "ar-t", "ar-u", "ar-v", "ar-w", "ar-x", "ar-y", "ar-z",
)


class RecordingRemoteArtifacts(ManifestReleaseArtifactStore):
    """Small manifest-shaped remote fixture that records materialized paths."""

    remote = True

    def __init__(self, root: Path, region_ids: tuple[str, ...]):
        self.cache_root = root
        self.manifest = {"objects": []}
        self.calls: list[tuple[str, bool]] = []
        self._calls_lock = threading.Lock()
        self.delay_seconds = 0.0
        self.active = 0
        self.max_active = 0
        self._activity_lock = threading.Lock()
        self.block_path_suffix: str | None = None
        self.block_entered = threading.Event()
        self.block_release = threading.Event()
        self.fail_region: str | None = None
        self.bootstrap = {
            "source": {
                "outerSha256": "a" * 64,
                "outerBytes": 1,
                "releaseDate": "2026-09-08",
                "acceptedObservationsSha256": "b" * 64,
                "nationalIndexSha256": "c" * 64,
            },
            "regions": [
                {"regionId": region_id, "manifest": {"path": f"regions/{region_id}/manifest.json"}}
                for region_id in region_ids
            ],
        }
        self.manifests = {
            region_id: {"files": {"storeIndex": {"path": f"regions/{region_id}/store-index.jsonl.gz"}}}
            for region_id in region_ids
        }

    def materialize(self, path: str, *, sparse: bool = False) -> Path:
        with self._calls_lock:
            self.calls.append((path, sparse))
        if self.fail_region is not None and f"/regions/{self.fail_region}/" in f"/{path}":
            raise OSError("fixture region failure")
        regional = "/regions/" in f"/{path}"
        if regional:
            with self._activity_lock:
                self.active += 1
                self.max_active = max(self.max_active, self.active)
            try:
                if self.block_path_suffix and path.endswith(self.block_path_suffix) and not self.block_entered.is_set():
                    self.block_entered.set()
                    if not self.block_release.wait(timeout=2):
                        raise OSError("fixture block timed out")
                if self.delay_seconds:
                    time.sleep(self.delay_seconds)
            finally:
                with self._activity_lock:
                    self.active -= 1
        target = self.cache_root / Path(*path.split("/"))
        target.parent.mkdir(parents=True, exist_ok=True)
        if path.endswith("bootstrap.json"):
            target.write_text(json.dumps(self.bootstrap), encoding="utf-8")
        elif path.endswith("manifest.json"):
            region_id = Path(path).parent.name
            target.write_text(json.dumps(self.manifests[region_id]), encoding="utf-8")
        elif sparse:
            with target.open("wb") as handle:
                handle.truncate(1)
        else:
            target.write_bytes(b"fixture")
        return target


def _stores() -> dict[str, dict[str, dict[str, object]]]:
    return {
        "ar-caba": {
            "caba-store": {"geoStatus": "VALID", "latitude": "-34.610359", "longitude": "-58.516538"},
        },
        "ar-b": {
            "ba-store": {"geoStatus": "VALID", "latitude": "-34.610500", "longitude": "-58.517000"},
        },
        "ar-x": {
            "cordoba-store": {"geoStatus": "VALID", "latitude": "-31.420000", "longitude": "-64.190000"},
        },
    }


class NationalStoreRouterTests(unittest.TestCase):
    def test_remote_route_materializes_only_store_geography_and_preserves_exact_selection(self):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), NATIONAL_REGION_IDS)
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            def fake_load_stores(contract):
                return stores.get(contract.region.region_id, {})

            with patch("backend.reader.load_micro_region_routing", side_effect=fake_routing), patch("backend.reader._load_stores", side_effect=fake_load_stores), patch("backend.reader.load_micro_region_contract", side_effect=AssertionError("full contract loaded during routing")):
                result = router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))
                first_call_count = len(artifacts.calls)
                warm = router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))

            expected = (
                RegionSelection("ar-b", ("ba-store",)),
                RegionSelection("ar-caba", ("caba-store",)),
            )
            self.assertEqual(result, expected)
            self.assertEqual(warm, expected)
            self.assertEqual(len(artifacts.calls), first_call_count)
            materialized = [path for path, _ in artifacts.calls]
            self.assertTrue(any(path.endswith("bootstrap.json") for path in materialized))
            self.assertTrue(any(path.endswith("store-index.jsonl.gz") for path in materialized))
            self.assertFalse(any("search-index" in path or "/packs/" in path for path in materialized))

    def test_selected_region_contract_is_deferred_until_requested(self):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), NATIONAL_REGION_IDS)
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            with patch("backend.reader.load_micro_region_routing", side_effect=fake_routing), patch("backend.reader._load_stores", side_effect=lambda contract: stores.get(contract.region.region_id, {})), patch("backend.reader.load_micro_region_contract", side_effect=AssertionError("full contract loaded during routing")):
                result = router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))
                self.assertEqual([item.region_id for item in result], ["ar-b", "ar-caba"])

            full_contract = object()
            with patch.object(router, "_materialize_remote_contract", return_value=full_contract) as materialize:
                self.assertIs(router.contract("ar-caba"), full_contract)
                materialize.assert_called_once_with("ar-caba")
            materialized = [path for path, _ in artifacts.calls]
            self.assertFalse(any("search-index" in path or "/packs/" in path for path in materialized))

    def test_missing_routing_descriptor_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), NATIONAL_REGION_IDS)
            artifacts.manifests["ar-caba"] = {"files": {}}
            router = NationalStoreRouter(None, artifacts=artifacts)

            with self.assertRaises(BackendQueryError):
                router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))

    def test_incomplete_national_region_metadata_fails_before_store_fetches(self):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), NATIONAL_REGION_IDS)
            artifacts.bootstrap["regions"] = artifacts.bootstrap["regions"][:-1]
            router = NationalStoreRouter(None, artifacts=artifacts)

            with self.assertRaises(BackendQueryError):
                router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))

            self.assertFalse(any("/regions/" in path for path, _ in artifacts.calls))

    def test_first_remote_routing_fetch_is_bounded_parallel_and_materially_faster_than_sequential(self):
        region_ids = NATIONAL_REGION_IDS
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), region_ids)
            artifacts.delay_seconds = 0.03
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            def fake_load_stores(contract):
                return stores.get(contract.region.region_id, {})

            with patch("backend.reader.load_micro_region_routing", side_effect=fake_routing), patch("backend.reader._load_stores", side_effect=fake_load_stores), patch("backend.reader.load_micro_region_contract", side_effect=AssertionError("full contract loaded during routing")):
                started = time.perf_counter()
                result = router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))
                elapsed = time.perf_counter() - started

            self.assertEqual(result, (RegionSelection("ar-b", ("ba-store",)), RegionSelection("ar-caba", ("caba-store",))))
            self.assertGreater(artifacts.max_active, 1)
            self.assertLessEqual(artifacts.max_active, MAX_REMOTE_ROUTING_CONCURRENCY)
            sequential_budget = artifacts.delay_seconds * len(region_ids) * 3
            self.assertLess(elapsed, sequential_budget * 0.85)

    def test_router_wide_lock_is_available_during_remote_routing_io(self):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), NATIONAL_REGION_IDS)
            artifacts.block_path_suffix = "store-index.jsonl.gz"
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()
            result: list[object] = []
            errors: list[BaseException] = []

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            def run_route():
                try:
                    result.append(router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2")))
                except BaseException as exc:  # pragma: no cover - assertion below reports failures
                    errors.append(exc)

            with patch("backend.reader.load_micro_region_routing", side_effect=fake_routing), patch("backend.reader._load_stores", side_effect=lambda contract: stores.get(contract.region.region_id, {})):
                thread = threading.Thread(target=run_route)
                thread.start()
                self.assertTrue(artifacts.block_entered.wait(timeout=1))
                lock_probe = threading.Event()

                def probe_lock():
                    with router._lock:
                        lock_probe.set()

                probe = threading.Thread(target=probe_lock)
                probe.start()
                self.assertTrue(lock_probe.wait(timeout=0.5))
                artifacts.block_release.set()
                thread.join(timeout=2)
                probe.join(timeout=2)

            self.assertFalse(thread.is_alive())
            self.assertFalse(errors)
            self.assertEqual(result, [(RegionSelection("ar-b", ("ba-store",)), RegionSelection("ar-caba", ("caba-store",)))])

    def test_simultaneous_cold_routes_share_one_routing_initialization(self):
        region_ids = NATIONAL_REGION_IDS
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), region_ids)
            artifacts.delay_seconds = 0.01
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()
            barrier = threading.Barrier(3)
            results: list[object] = []
            errors: list[BaseException] = []

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            def run_route():
                try:
                    barrier.wait(timeout=2)
                    results.append(router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2")))
                except BaseException as exc:  # pragma: no cover - assertion below reports failures
                    errors.append(exc)

            with patch("backend.reader.load_micro_region_routing", side_effect=fake_routing), patch("backend.reader._load_stores", side_effect=lambda contract: stores.get(contract.region.region_id, {})):
                threads = [threading.Thread(target=run_route) for _ in range(2)]
                for thread in threads:
                    thread.start()
                barrier.wait(timeout=2)
                for thread in threads:
                    thread.join(timeout=3)

            self.assertFalse(errors)
            self.assertEqual(len(results), 2)
            store_calls = Counter(path for path, _ in artifacts.calls if path.endswith("store-index.jsonl.gz"))
            self.assertEqual(store_calls, Counter({f"micro-1024/regions/{region_id}/store-index.jsonl.gz": 1 for region_id in region_ids}))

    def test_failed_region_does_not_commit_partial_routing_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = RecordingRemoteArtifacts(Path(directory), NATIONAL_REGION_IDS)
            artifacts.fail_region = "ar-b"
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            with patch("backend.reader.load_micro_region_routing", side_effect=fake_routing), patch("backend.reader._load_stores", side_effect=lambda contract: stores.get(contract.region.region_id, {})):
                with self.assertRaises(BackendQueryError):
                    router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))
                calls_after_failure = len(artifacts.calls)
                with self.assertRaises(BackendQueryError):
                    router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))

            self.assertFalse(router._routing_initialized)
            self.assertEqual(router._routing_stores, {})
            self.assertEqual(len(artifacts.calls), calls_after_failure)


if __name__ == "__main__":
    unittest.main()

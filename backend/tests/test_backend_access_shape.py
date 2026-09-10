from __future__ import annotations

import hashlib
import json
import tempfile
import threading
import time
import unittest
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterator, Mapping

from backend.clock import ARGENTINA_ZONE, FrozenClock
from backend.object_store import OBJECT_STREAM_CHUNK_BYTES, LocalFilesystemObjectStore, ObjectMetadata
from backend.artifacts import ManifestReleaseArtifactStore
from backend.reader import BackendQueryError, MAX_SEARCH_CACHE_ENTRIES, NationalStoreRouter
from backend.release import ReleaseManager
from backend.service import BackendService
from tools.build_argentina_national_routing_artifact import derive_release_workspace
from tools.build_argentina_sepa_micro_partition_mobile import build_micro_partition_mobile
from tools.build_argentina_sepa_national_shards import _canonical_json
from tools.build_argentina_sepa_query_selective_mobile import build_query_selective_mobile
from tools.tests.test_argentina_sepa_national_shards import OUTER_BYTES, OUTER_SHA, _build, _row


FIXTURE_RELEASE = "fixture-argentina-sepa"
ROUTING_RELEASE = f"{FIXTURE_RELEASE}-routing-v1"


class CountingRemoteObjectStore:
    """A deterministic latency/counter wrapper around the real object store."""

    def __init__(self, root: Path, logical_by_digest: Mapping[str, tuple[str, ...]], *, latency_seconds: float = 0.001):
        self.delegate = LocalFilesystemObjectStore(root)
        self.logical_by_digest = dict(logical_by_digest)
        self.latency_seconds = latency_seconds
        self.operations: list[tuple[str, str]] = []
        self.streamed_bytes = 0
        self.current_buffered_bytes = 0
        self.peak_buffered_bytes = 0

    @property
    def request_count(self) -> int:
        return len(self.operations)

    def _request(self, operation: str, key: str) -> None:
        self.operations.append((operation, key))
        if self.latency_seconds:
            time.sleep(self.latency_seconds)

    def head(self, key: str) -> ObjectMetadata:
        self._request("head", key)
        return self.delegate.head(key)

    def get(self, key: str) -> bytes:
        self._request("get", key)
        return self.delegate.get(key)

    def stream(self, key: str, *, chunk_size: int = 1024 * 1024) -> Iterator[bytes]:
        self._request("stream", key)
        for data in self.delegate.stream(key, chunk_size=chunk_size):
            self.streamed_bytes += len(data)
            self.current_buffered_bytes += len(data)
            self.peak_buffered_bytes = max(self.peak_buffered_bytes, self.current_buffered_bytes)
            try:
                yield data
            finally:
                self.current_buffered_bytes -= len(data)

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        self._request("range", key)
        return self.delegate.get_range(key, offset, length)

    def exists(self, key: str) -> bool:
        return self.delegate.exists(key)

    def logical_paths(self, key: str) -> tuple[str, ...]:
        digest = key.rsplit("/", 1)[-1]
        return self.logical_by_digest.get(digest, ())

    def counts(self) -> dict[str, int]:
        return {name: sum(operation == name for operation, _ in self.operations) for name in ("head", "get", "stream", "range")}


def _fixture_rows() -> list[dict[str, Any]]:
    rows = [
        _row(commerce="caba-commerce", banner="caba-banner", store_id="1", province="AR-C", product_id="fixture-arroz", latitude="-34.6037", longitude="-58.3816", quantity={"unit": "GRAM", "value": "1000"}),
        _row(commerce="caba-commerce", banner="caba-banner", store_id="2", province="AR-C", product_id="fixture-leche", latitude=None, longitude=None, quantity=None, quantity_status="UNKNOWN"),
        _row(commerce="ba-commerce", banner="ba-banner", store_id="3", province="AR-B", product_id="fixture-manteca", latitude="-34.6037", longitude="-58.3816", quantity={"unit": "GRAM", "value": "500"}),
        _row(commerce="cordoba-commerce", banner="cordoba-banner", store_id="4", province="AR-X", product_id="fixture-coca", latitude="-31.4200", longitude="-64.1900", quantity={"unit": "MILLILITRE", "value": "2250"}),
        _row(commerce="jujuy-commerce", banner="jujuy-banner", store_id="5", province="AR-J", product_id="fixture-sprite", latitude="-24.1800", longitude="-65.3000", quantity={"unit": "MILLILITRE", "value": "2250"}),
    ]
    names = ("ARROZ BLANCO", "LECHE ENTERA", "MANTECA", "COCA-COLA", "SPRITE")
    for row, name in zip(rows, names):
        row["product"].update({"name": name, "gtin": None, "gtin_status": "INVALID_OR_NOT_GTIN"})
    rows[0]["product"]["form"] = "dry"
    rows[1]["product"]["form"] = "dairy"
    rows[2]["product"]["form"] = "dairy"
    rows[3]["product"]["form"] = "soda"
    rows[4]["product"]["form"] = "soda"
    rows[3]["product"]["brand"] = "Coca-Cola"
    rows[4]["product"]["brand"] = "Sprite"
    return rows


def _write_content_addressed_workspace(root: Path) -> tuple[Path, Path, dict[str, tuple[str, ...]]]:
    micro_source, _ = _build(root / "input", _fixture_rows())
    selective = root / "selective"
    build_query_selective_mobile(
        micro_source,
        selective,
        generated_at="2026-09-07T12:00:00Z",
        bucket_count=4,
        expected_outer_sha256=OUTER_SHA,
        expected_outer_bytes=OUTER_BYTES,
        expected_accepted_sha256=None,
        expected_national_index_sha256=None,
    )
    micro = root / "micro"
    build_micro_partition_mobile(
        selective,
        micro,
        generated_at="2026-09-07T12:00:00Z",
        logical_partition_count=128,
        physical_pack_count=32,
        expected_outer_sha256=OUTER_SHA,
        expected_outer_bytes=OUTER_BYTES,
        expected_release_date="2026-09-06",
        expected_accepted_sha256=None,
        expected_national_index_sha256=None,
    )

    source_workspace = root / "source-workspace"
    objects = source_workspace / "objects" / "sha256"
    release_root = source_workspace / "releases" / FIXTURE_RELEASE
    objects.mkdir(parents=True)
    release_root.mkdir(parents=True)
    descriptors: list[dict[str, Any]] = []
    logical_by_digest: dict[str, list[str]] = {}
    for path in sorted(micro.rglob("*")):
        if not path.is_file() or path.name in {"bootstrap.sha256", "manifest.sha256", "README.txt"}:
            continue
        logical = f"micro-1024/{path.relative_to(micro).as_posix()}"
        data = path.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        object_path = objects / digest
        if not object_path.exists():
            object_path.write_bytes(data)
        descriptors.append({"path": logical, "bytes": len(data), "sha256": digest})
        logical_by_digest.setdefault(digest, []).append(logical)
    bootstrap = json.loads((micro / "bootstrap.json").read_text(encoding="utf-8"))
    source = dict(bootstrap["source"])
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": FIXTURE_RELEASE,
        "source": source,
        "backendProfile": {"logicalPartitionCount": 128, "physicalPackCount": 32},
        "objects": sorted(descriptors, key=lambda item: item["path"]),
    }
    raw_manifest = _canonical_json(manifest)
    (release_root / "manifest.json").write_bytes(raw_manifest)
    (release_root / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw_manifest).hexdigest()}  manifest.json\n".encode("ascii"))
    (source_workspace / "control").mkdir()
    (source_workspace / "control" / "active.json").write_bytes(_canonical_json({"releaseId": FIXTURE_RELEASE, "schemaVersion": "valuepilot-active-release-v1"}))

    derived_workspace = root / "derived-workspace"
    derive_release_workspace(source_workspace, derived_workspace, (FIXTURE_RELEASE,))
    derived_id = ROUTING_RELEASE
    (derived_workspace / "control").mkdir()
    (derived_workspace / "control" / "active.json").write_bytes(_canonical_json({"releaseId": derived_id, "schemaVersion": "valuepilot-active-release-v1"}))
    derived_manifest = json.loads((derived_workspace / "releases" / derived_id / "manifest.json").read_text(encoding="utf-8"))
    for descriptor in derived_manifest["objects"]:
        logical_by_digest.setdefault(descriptor["sha256"], []).append(descriptor["path"])
    return source_workspace, derived_workspace, {digest: tuple(sorted(paths)) for digest, paths in logical_by_digest.items()}


def _service(workspace: Path, logical_by_digest: Mapping[str, tuple[str, ...]]) -> tuple[BackendService, CountingRemoteObjectStore]:
    store = CountingRemoteObjectStore(workspace, logical_by_digest)
    manager = ReleaseManager(object_store=store, clock=FrozenClock(datetime(2026, 9, 8, tzinfo=ARGENTINA_ZONE)))
    return BackendService(str(workspace), release_manager=manager), store


def _caba_request(query: str) -> dict[str, str]:
    return {"latitude": "-34.6037", "longitude": "-58.3816", "radiusKm": "20", "query": query}


class BackendAccessShapeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tempdir = tempfile.TemporaryDirectory(prefix="valuepilot-backend-shape-")
        cls.old_workspace, cls.new_workspace, cls.logical_by_digest = _write_content_addressed_workspace(Path(cls.tempdir.name))

    @classmethod
    def tearDownClass(cls) -> None:
        cls.tempdir.cleanup()

    def test_new_cold_shape_uses_one_routing_object_and_only_selected_ranges(self):
        service, store = _service(self.new_workspace, self.logical_by_digest)
        response = service.search(_caba_request("arroz"))
        self.assertEqual(response["releaseId"], ROUTING_RELEASE)
        self.assertEqual(response["regionsQueried"], ["ar-b", "ar-caba"])
        self.assertIn(response["resolution"], {"RESOLVED_EXACT", "RESOLVED_ALIAS", "RESOLVED_SAFE_CORRECTION"})
        self.assertTrue(response["matches"])
        counts = store.counts()
        self.assertEqual(counts["stream"], 9)
        self.assertEqual(counts["range"], 1)
        self.assertEqual(counts["head"], 1)
        self.assertEqual(counts["get"], 3)
        self.assertEqual(store.request_count, 14)
        self.assertEqual(store.streamed_bytes, 240159)
        self.assertTrue(all(not key.startswith("objects/sha256/") for operation, key in store.operations if operation == "get"))
        routing_paths = [path for paths in store.logical_by_digest.values() for path in paths if path.endswith("national-routing.jsonl.gz")]
        streamed_routing = [key for operation, key in store.operations if operation == "stream" and any(path in routing_paths for path in store.logical_paths(key))]
        self.assertEqual(len(streamed_routing), 1)
        all_store_paths = {path for paths in store.logical_by_digest.values() for path in paths if path.endswith("store-index.jsonl.gz")}
        selected_store_paths = {path for paths in store.logical_by_digest.values() for path in paths if path.endswith("/ar-b/store-index.jsonl.gz") or path.endswith("/ar-caba/store-index.jsonl.gz")}
        streamed_store_paths = {path for operation, key in store.operations if operation == "stream" for path in store.logical_paths(key) if path.endswith("store-index.jsonl.gz")}
        self.assertTrue(streamed_store_paths)
        self.assertTrue(streamed_store_paths <= selected_store_paths)
        self.assertTrue(all_store_paths - selected_store_paths)
        self.assertFalse(streamed_store_paths & (all_store_paths - selected_store_paths))
        range_paths = {path for operation, key in store.operations if operation == "range" for path in store.logical_paths(key)}
        self.assertTrue(range_paths)
        self.assertTrue(all("/ar-caba/" in path for path in range_paths))
        self.assertLessEqual(store.peak_buffered_bytes, OBJECT_STREAM_CHUNK_BYTES)
        self.assertEqual(store.peak_buffered_bytes, 112486)

    def test_warm_request_reuses_routing_contract_and_verified_range(self):
        service, store = _service(self.new_workspace, self.logical_by_digest)
        service.search(_caba_request("arroz"))
        before = len(store.operations)
        service.search(_caba_request("arroz"))
        delta = store.operations[before:]
        self.assertEqual(sum(operation == "stream" for operation, _ in delta), 0)
        self.assertEqual(sum(operation == "range" for operation, _ in delta), 0)
        self.assertEqual(sum(operation == "head" for operation, _ in delta), 0)
        self.assertEqual(sum(operation == "get" for operation, _ in delta), 3)

    def test_declared_routing_without_a_pinned_object_fails_closed(self):
        manifest_path = self.new_workspace / "releases" / ROUTING_RELEASE / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["objects"] = [item for item in manifest["objects"] if item["path"] != "micro-1024/national-routing.jsonl.gz"]
        store = CountingRemoteObjectStore(self.new_workspace, self.logical_by_digest)
        artifacts = ManifestReleaseArtifactStore(store, manifest, release_id=ROUTING_RELEASE, cache_root=Path(self.tempdir.name) / "missing-routing-cache")
        router = NationalStoreRouter(None, artifacts=artifacts)
        with self.assertRaises(BackendQueryError):
            router.route(latitude=Decimal("-34.6037"), longitude=Decimal("-58.3816"), radius_km=Decimal("20"))

    def test_national_routing_initialization_is_single_flight(self):
        service, store = _service(self.new_workspace, self.logical_by_digest)
        reader = service._reader(service.release_manager.pin())
        barrier = threading.Barrier(4)
        results: list[object] = []
        errors: list[BaseException] = []

        def route() -> None:
            try:
                barrier.wait(timeout=2)
                results.append(reader.router.route(latitude=Decimal("-34.6037"), longitude=Decimal("-58.3816"), radius_km=Decimal("20")))
            except BaseException as exc:  # pragma: no cover - assertion below reports failures
                errors.append(exc)

        threads = [threading.Thread(target=route) for _ in range(3)]
        for thread in threads:
            thread.start()
        barrier.wait(timeout=2)
        for thread in threads:
            thread.join(timeout=3)
        self.assertFalse(errors)
        self.assertEqual(len(results), 3)
        routing_paths = [path for paths in store.logical_by_digest.values() for path in paths if path.endswith("national-routing.jsonl.gz")]
        self.assertEqual(sum(operation == "stream" and any(path in routing_paths for path in store.logical_paths(key)) for operation, key in store.operations), 1)

    def test_old_and_new_cold_models_have_deterministic_structural_gap(self):
        old_service, old_store = _service(self.old_workspace, self.logical_by_digest)
        new_service, new_store = _service(self.new_workspace, self.logical_by_digest)
        old_service.search(_caba_request("arroz"))
        new_service.search(_caba_request("arroz"))
        old_counts = old_store.counts()
        new_counts = new_store.counts()
        self.assertGreaterEqual(old_counts["stream"], 50)
        self.assertEqual(new_counts["stream"], 9)
        self.assertEqual(old_counts["range"], 1)
        self.assertEqual(new_counts["range"], 1)
        self.assertEqual(old_store.request_count, 57)
        self.assertEqual(new_store.request_count, 14)
        self.assertEqual(old_store.streamed_bytes, 2703835)
        self.assertEqual(new_store.streamed_bytes, 240159)
        # The compatibility path intentionally uses four bounded workers, so
        # scheduler interleaving can change the observed overlap.  The safety
        # property is the fixed four-chunk upper bound, not a flaky timing
        # dependent exact sample.
        self.assertLessEqual(old_store.peak_buffered_bytes, 4 * OBJECT_STREAM_CHUNK_BYTES)
        self.assertGreaterEqual(old_store.peak_buffered_bytes, new_store.peak_buffered_bytes)
        self.assertEqual(new_store.peak_buffered_bytes, 112486)
        old_modeled_ms = old_store.request_count * old_store.latency_seconds * 1000
        new_modeled_ms = new_store.request_count * new_store.latency_seconds * 1000
        self.assertLess(new_modeled_ms, old_modeled_ms)

    def test_regional_queries_keep_identity_and_route_to_cordoba_and_jujuy(self):
        service, _ = _service(self.new_workspace, self.logical_by_digest)
        cases = (
            (_caba_request("leche"), "ar-caba"),
            (_caba_request("manteca"), "ar-b"),
            ({"latitude": "-31.4200", "longitude": "-64.1900", "radiusKm": "20", "query": "Coca-Cola"}, "ar-x"),
            ({"latitude": "-24.1800", "longitude": "-65.3000", "radiusKm": "20", "query": "Sprite"}, "ar-j"),
        )
        for request, expected_region in cases:
            response = service.search(request)
            self.assertEqual(response["releaseId"], ROUTING_RELEASE)
            self.assertIn(expected_region, response["regionsQueried"])
            self.assertIn(response["resolution"], {"RESOLVED_EXACT", "RESOLVED_ALIAS", "RESOLVED_SAFE_CORRECTION"})

    def test_search_cache_has_a_fixed_entry_bound(self):
        service, _ = _service(self.new_workspace, self.logical_by_digest)
        reader = service._reader(service.release_manager.pin())
        contract = reader.router.contract("ar-caba")
        for index in range(MAX_SEARCH_CACHE_ENTRIES + 10):
            reader._search(contract, f"query-{index}")
        self.assertLessEqual(len(reader._search_cache), MAX_SEARCH_CACHE_ENTRIES)


if __name__ == "__main__":
    unittest.main()

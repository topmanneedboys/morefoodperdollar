from __future__ import annotations

import json
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from backend.reader import BackendQueryError, NationalStoreRouter, RegionSelection
from backend.artifacts import ManifestReleaseArtifactStore


class RecordingRemoteArtifacts(ManifestReleaseArtifactStore):
    """Small manifest-shaped remote fixture that records materialized paths."""

    remote = True

    def __init__(self, root: Path, region_ids: tuple[str, ...]):
        self.cache_root = root
        self.calls: list[tuple[str, bool]] = []
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
        self.calls.append((path, sparse))
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
            artifacts = RecordingRemoteArtifacts(Path(directory), ("ar-caba", "ar-b", "ar-x"))
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            def fake_load_stores(contract):
                return stores[contract.region.region_id]

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
            artifacts = RecordingRemoteArtifacts(Path(directory), ("ar-caba", "ar-b"))
            router = NationalStoreRouter(None, artifacts=artifacts)
            stores = _stores()

            def fake_routing(root, region_id, **kwargs):
                return SimpleNamespace(region=SimpleNamespace(region_id=region_id))

            with patch("backend.reader.load_micro_region_routing", side_effect=fake_routing), patch("backend.reader._load_stores", side_effect=lambda contract: stores[contract.region.region_id]), patch("backend.reader.load_micro_region_contract", side_effect=AssertionError("full contract loaded during routing")):
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
            artifacts = RecordingRemoteArtifacts(Path(directory), ("ar-caba",))
            artifacts.manifests["ar-caba"] = {"files": {}}
            router = NationalStoreRouter(None, artifacts=artifacts)

            with self.assertRaises(BackendQueryError):
                router.route(latitude=Decimal("-34.610359"), longitude=Decimal("-58.516538"), radius_km=Decimal("2"))


if __name__ == "__main__":
    unittest.main()

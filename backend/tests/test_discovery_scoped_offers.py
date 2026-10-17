from __future__ import annotations

import copy
import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient
from backend.app import create_app
from backend.discovery_offers import scoped_search, _amount
from backend.reader import BackendQueryError, RegionSelection
from backend.service import BackendService
from tools.catalog_discovery import DiscoveryIndex, build_discovery_index


def offer(key, price="100", *, quantity="1000", unit="g", store="store-a", distance="2"):
    basis = {"g": "100 g", "ml": "100 ml", "count": "1 count"}[unit]
    return {"offerKey": "offer-" + key + store, "productEvidenceKey": key,
            "storeKey": store, "storeName": store, "distanceKm": distance,
            "listPrice": {"amount": price, "currency": "ARS"},
            "quantity": {"value": quantity, "unit": unit}, "quantityStatus": "KNOWN",
            "unitPrice": {"amount": "10", "currency": "ARS", "basis": basis},
            "releaseDate": "2026-10-14", "freshnessStatus": "FRESH", "availability": "UNKNOWN"}


class ScopedDiscoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        root = Path(cls.temp.name)
        source = root / "identities.jsonl"
        source.write_text("".join(json.dumps({"productEvidenceKey": f"key-{i:04}",
            "providerProductId": f"provider-{i:04}", "name": f"PRODUCTO CONTROL {i:04}", "brand": "Control"}) + "\n"
            for i in range(300)), encoding="utf-8")
        target = root / "index.sqlite"
        build_discovery_index(source, target, release_id="test-release")
        cls.index = DiscoveryIndex(target)

    @classmethod
    def tearDownClass(cls):
        cls.index.close()
        cls.temp.cleanup()

    def setUp(self):
        self.offers = {f"key-{i:04}": [offer(f"key-{i:04}", str(1000 - i))] for i in range(300)}
        self.offers["key-0299"] = [offer("key-0299", "1", store="last-store", distance="0.1")]
        self.calls = []
        def join(keys, **scope):
            self.calls.append((keys, scope))
            return copy.deepcopy(self.offers), SimpleNamespace(regions_queried=["ar-x"])
        self.reader = SimpleNamespace(offers_for_identities=join,
            router=SimpleNamespace(route=lambda **kw: (RegionSelection("ar-x", ("store-a", "last-store")),)))
        self.handle = SimpleNamespace(release_id="test-release", release_date="2026-10-14", freshness_status="FRESH",
            artifacts=SimpleNamespace(manifest={"backendProfile": {"logicalPartitionCount": 1024, "physicalPackCount": 32},
                "searchPackArtifact": {"schemaVersion": "valuepilot-argentina-searchpack-v2", "regions": ["ar-x"]}}))
        self.payload = {"query": "control", "language": "en", "pageSize": 20,
            "latitude": "-31.4", "longitude": "-64.2", "radiusKm": "25", "sort": "unit"}

    def run_query(self, **changes):
        return scoped_search(self.index, self.reader, self.handle, {**self.payload, **changes}, ())

    def test_cheapest_identity_at_position_300_is_on_first_page(self):
        result = self.run_query()
        self.assertEqual(result["total"]["sourceIdentityCount"], 300)
        self.assertEqual(len(self.calls[0][0]), 300)
        self.assertEqual(len(result["matches"]), 20)
        self.assertEqual(result["matches"][0]["identityKey"], "key-0299")
        self.assertEqual(result["scope"]["sortScope"], "ALL_MATCHING_IDENTITIES_BEFORE_PAGINATION")
        self.assertEqual(result["stores"][0]["storeKey"], "last-store")

    def test_all_pages_are_complete_unique_and_globally_sorted(self):
        cursor = None
        keys = []
        while True:
            result = self.run_query(cursor=cursor)
            keys.extend(row["identityKey"] for row in result["matches"])
            cursor = result["pagination"]["nextCursor"]
            if not cursor:
                break
        self.assertEqual(len(keys), 300)
        self.assertEqual(len(set(keys)), 300)
        self.assertEqual(keys, [f"key-{i:04}" for i in range(299, -1, -1)])

    def test_store_filter_covers_all_300_before_pagination(self):
        result = self.run_query(storeKey="last-store")
        self.assertEqual(result["total"]["sourceIdentityCount"], 1)
        self.assertEqual(result["matches"][0]["identityKey"], "key-0299")
        self.assertFalse(result["pagination"]["hasMore"])

    def test_scope_changes_reject_previous_cursor(self):
        cursor = self.run_query()["pagination"]["nextCursor"]
        for change in ({"latitude": "-32"}, {"longitude": "-65"}, {"radiusKm": "10"},
                       {"sort": "pack"}, {"storeKey": "store-a"}, {"unitBasis": "100 g"},
                       {"pageSize": 10}, {"query": "producto"}, {"language": "es"}):
            before = len(self.calls)
            with self.subTest(change=change), self.assertRaisesRegex(BackendQueryError, "cursor"):
                self.run_query(cursor=cursor, **change)
            self.assertEqual(len(self.calls), before, "invalid cursors must fail before expensive offer reads")

    def test_old_catalog_cannot_inherit_new_release_prices(self):
        handle = copy.copy(self.handle)
        handle.release_id = "different-newer-release"
        with self.assertRaisesRegex(BackendQueryError, "RELEASE_MISMATCH"):
            scoped_search(self.index, self.reader, handle, self.payload, ())
        self.assertEqual(self.calls, [])

    def test_null_prices_stay_unknown_and_incomparable_bases_require_choice(self):
        self.offers["key-0000"][0]["quantityStatus"] = "UNKNOWN"
        self.offers["key-0000"][0]["unitPrice"] = None
        self.assertEqual(self.run_query()["matches"][0]["identityKey"], "key-0299")
        self.offers["key-0298"] = [offer("key-0298", unit="ml")]
        with self.assertRaisesRegex(BackendQueryError, "UNIT_BASIS_REQUIRED"):
            self.run_query()
        result = self.run_query(unitBasis="100 ml")
        self.assertEqual([row["identityKey"] for row in result["matches"]], ["key-0298"])

    def test_exact_rational_unit_order_does_not_use_rounded_display_rate(self):
        first = offer("a", "1", quantity="3")
        second = offer("b", "1", quantity="7")
        self.assertLess(_amount(second, "unit"), _amount(first, "unit"))
        self.assertIsNone(_amount({**first, "listPrice": {"amount": "1", "currency": "CAD"}}, "unit"))

    def test_http_scoped_route_requires_live_price_authorization(self):
        service = BackendService("", discovery_index_path=self.index.path, discovery_release_id="test-release",
                                 release_manager=SimpleNamespace(pin=lambda **kw: self.handle))
        env = {"VALUEPILOT_DISCOVERY_V2_ENABLED": "true", "VALUEPILOT_ANDROID_NETWORKING_AUTHORIZED": "true",
            "VALUEPILOT_ANDROID_NETWORKING_SCOPE": "identity-search", "VALUEPILOT_ANDROID_NETWORKING_RELEASE_ID": "test-release",
            "VALUEPILOT_ANDROID_NETWORKING_RELEASE_DATE": "2026-10-14", "VALUEPILOT_PRODUCTION_ANDROID_UI_AUTHORIZED": "true",
            "VALUEPILOT_PRODUCTION_ANDROID_UI_SCOPE": "qualified-offers"}
        try:
            with patch.dict(os.environ, env), patch.object(service, "_reader", return_value=self.reader):
                client = TestClient(create_app(service))
                response = client.post("/v2/search", json=self.payload)
                self.assertEqual(response.status_code, 200, response.text)
                body = response.json()
                self.assertEqual(body["result"]["matches"][0]["identityKey"], "key-0299")
                self.assertEqual(body["evidenceSemantics"]["prices"], "QUALIFIED_CURRENT_RELEASE")
                with patch.dict(os.environ, {"VALUEPILOT_PRODUCTION_ANDROID_UI_AUTHORIZED": "false"}):
                    denied = client.post("/v2/search", json=self.payload)
                    self.assertNotEqual(denied.status_code, 200)
                    self.assertIn("AUTHORIZATION_REQUIRED", denied.text)
        finally:
            if service._discovery_index:
                service._discovery_index.close()


class ExactReaderJoinTests(unittest.TestCase):
    def test_reader_joins_300_exact_keys_reads_shared_partition_once_and_rejects_wrong_evidence(self):
        from decimal import Decimal
        from backend.reader import ArgentinaBackendReader
        from tools.tests.test_argentina_searchpack import _manager_for_rows
        products = [{"productEvidenceKey": f"key-{i:04}", "name": f"Arroz {i:04}", "brand": "Control",
            "partitionId": "partition-shared", "quantity": {"value": "1000", "unit": "g"},
            "quantityStatus": "KNOWN", "canonicalSearchAliases": []} for i in range(300)]
        manager = _manager_for_rows(products)
        reader = ArgentinaBackendReader.__new__(ArgentinaBackendReader)
        reader.release = SimpleNamespace(freshness_status="FRESH", release_date="2026-10-14")
        descriptor = {"packId": "pack", "path": "offers", "byteOffset": 0, "byteLength": 10,
            "bytes": 10, "sha256": "0" * 64, "uncompressedBytes": 100, "uncompressedSha256": "0" * 64,
            "recordCount": 300}
        contract = SimpleNamespace(region=SimpleNamespace(region_id="ar-x"),
            partition_descriptors={"partition-shared": descriptor}, pack_descriptors={"pack": {"bytes": 10}},
            bootstrap_bytes=1, manifest_bytes=1, search_descriptor={"bytes": 1}, store_descriptor={"bytes": 1})
        stores = {"store-a": {"name": "Store A", "type": "GROCERY", "geoStatus": "VALID",
            "latitude": "-31.4", "longitude": "-64.2"},
            "outside": {"name": "Outside", "type": "GROCERY", "geoStatus": "VALID",
            "latitude": "-34.6", "longitude": "-58.4"}}
        reader.router = SimpleNamespace(contract=lambda region: contract, stores=lambda region: stores)
        reader._searchpack_for_region = lambda region: manager
        raw = [{"offerKey": f"offer-{i:04}", "offerId": i, "productEvidenceKey": f"key-{i:04}",
            "storeKey": "store-a", "listPrice": {"amount": str(1000 - i), "currency": "ARS"},
            "packageProvenance": {}, "sourceRow": i, "releaseDate": "2026-10-14", "freshnessStatus": "FRESH",
            "availability": "UNKNOWN"} for i in range(300)]
        raw.extend([{**raw[0], "offerKey": "stale", "releaseDate": "2026-10-08"},
                    {**raw[0], "offerKey": "wrong-quantity", "quantity": {"value": "500", "unit": "g"}},
                    {**raw[0], "offerKey": "outside", "storeKey": "outside"},
                    {**raw[0], "offerKey": "wrong-currency", "listPrice": {"amount": "1", "currency": "CAD"}},
                    {**raw[0], "offerKey": "invalid-price", "listPrice": {"amount": "-1", "currency": "ARS"}},
                    dict(raw[0])])
        with patch.object(reader, "_member", return_value=raw) as member:
            joined, metrics = reader.offers_for_identities([product["productEvidenceKey"] for product in products],
                selections=(RegionSelection("ar-caba", ("store-a", "outside")),), latitude=Decimal("-31.4"),
                longitude=Decimal("-64.2"), radius_km=Decimal("25"))
        self.assertEqual(member.call_count, 1)
        self.assertEqual(len(joined), 300)
        self.assertEqual(sum(map(len, joined.values())), 300)
        self.assertEqual(joined["key-0299"][0]["unitPrice"]["amount"], "70.1")
        self.assertGreater(metrics.searchpack_records_returned, 0)

    def test_batched_keys_decode_shared_docstore_once_and_reject_wrong_identity(self):
        from tools.argentina_searchpack import SearchPackStats, SearchPackError
        from tools.tests.test_argentina_searchpack import _manager_for_rows
        manager = _manager_for_rows([{"productEvidenceKey": f"key-{i}", "name": f"Item {i}",
            "brand": "Control", "canonicalSearchAliases": []} for i in range(4)])
        region = manager.region("ar-caba")
        stats = SearchPackStats()
        rows = region.lookup_product_keys(["key-0", "key-1", "key-0", "missing"], stats=stats)
        self.assertEqual(set(rows), {"key-0", "key-1"})
        self.assertEqual(stats.docstore_reads, 1)
        self.assertEqual(stats.key_lookups, 3)
        with patch.object(region, "get_records", return_value={0: {"productEvidenceKey": "wrong"}}):
            with self.assertRaisesRegex(SearchPackError, "identity mismatch"):
                region.lookup_product_keys(["key-0"])


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.argentina_sepa_query import (
    ArgentinaSepaQueryError,
    _bounded_chunked_search,
    load_region_contract,
    plan_query,
    query_full_shard,
    query_nearby,
    query_structured_request,
    straight_line_distance_km,
)
from tools.build_argentina_sepa_query_selective_mobile import (
    PARTITION_FILE_TEMPLATE,
    PARTITIONS_DIR,
    product_partition_id,
    build_query_selective_mobile,
)
from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS, _canonical_json
from tools.tests.test_argentina_sepa_national_shards import (
    OUTER_BYTES,
    OUTER_SHA,
    ArgentinaSepaNationalShardsTest,
    _build,
)
from tools.verify_argentina_sepa_query_selective_mobile import (
    QuerySelectiveMobileVerificationError,
    verify_query_selective_mobile,
)


PROVENANCE = {
    "expected_outer_sha256": OUTER_SHA,
    "expected_outer_bytes": OUTER_BYTES,
    "expected_accepted_sha256": None,
    "expected_national_index_sha256": None,
}


class ArgentinaSepaQuerySelectiveMobileTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tempdir = tempfile.TemporaryDirectory(prefix="argentina-query-selective-test-")
        root = Path(cls.tempdir.name)
        cls.rows = ArgentinaSepaNationalShardsTest()._rows()
        cls.national, cls.national_index = _build(root / "input", cls.rows)
        cls.selective_root = root / "selective"
        cls.selective_bootstrap = build_query_selective_mobile(
            cls.national,
            cls.selective_root,
            generated_at="2026-09-07T12:00:00Z",
            bucket_count=4,
            expected_outer_sha256=OUTER_SHA,
            expected_outer_bytes=OUTER_BYTES,
            expected_accepted_sha256=None,
            expected_national_index_sha256=None,
        )

    @classmethod
    def tearDownClass(cls) -> None:
        cls.tempdir.cleanup()

    def _query_kwargs(self) -> dict[str, object]:
        return dict(PROVENANCE)

    def _copy_selective(self, name: str) -> Path:
        destination = Path(self.tempdir.name) / name
        shutil.copytree(self.selective_root, destination)
        return destination

    def test_contract_has_all_regions_and_unpublished_provinces_stay_unpublished(self):
        result = verify_query_selective_mobile(
            self.selective_root,
            **PROVENANCE,
            expected_bucket_count=4,
        )
        self.assertEqual(result["regions"], len(ARGENTINA_REGIONS))
        self.assertEqual(result["stores"], 4)
        self.assertEqual(result["productEvidenceRecords"], 4)
        self.assertEqual(result["offers"], 4)
        self.assertEqual(result["promotions"], 1)
        bootstrap = json.loads((self.selective_root / "bootstrap.json").read_text(encoding="utf-8"))
        self.assertFalse(bootstrap["productionUiAuthorized"])
        self.assertEqual(bootstrap["totals"]["publishableRegions"], 24)
        national_unpublished = self.national_index["unpublishedProvinceEvidence"]
        self.assertEqual([(item["classification"], item["value"]) for item in national_unpublished], [("NONSTANDARD", "Buenos Aires"), ("UNKNOWN", None)])

    def test_repeat_build_is_byte_identical(self):
        root = Path(self.tempdir.name) / "repeat"
        national, _ = _build(root / "input", self.rows)
        repeat = build_query_selective_mobile(
            national,
            root / "selective",
            generated_at="2026-09-07T12:00:00Z",
            bucket_count=4,
            expected_outer_sha256=OUTER_SHA,
            expected_outer_bytes=OUTER_BYTES,
            expected_accepted_sha256=None,
            expected_national_index_sha256=None,
        )
        self.assertEqual(repeat["totals"], self.selective_bootstrap["totals"])
        first_files = sorted(path.relative_to(self.selective_root) for path in self.selective_root.rglob("*") if path.is_file())
        second_files = sorted(path.relative_to(root / "selective") for path in (root / "selective").rglob("*") if path.is_file())
        self.assertEqual(first_files, second_files)
        for relative in first_files:
            self.assertEqual((self.selective_root / relative).read_bytes(), (root / "selective" / relative).read_bytes(), str(relative))

    def test_product_bucket_is_stable_and_offer_files_are_complete(self):
        key = "ar-sepa-product:caba-commerce:7790070318398"
        self.assertEqual(product_partition_id(key, 4), product_partition_id(key, 4))
        contract = load_region_contract(self.selective_root, "ar-caba", **PROVENANCE)
        self.assertEqual(len(contract.partition_descriptors), 4)
        self.assertEqual(sorted(contract.partition_descriptors), ["p000", "p001", "p002", "p003"])
        plan = plan_query(self.selective_root, "ar-caba", ["leche", "arroz"], product_limit=5, **PROVENANCE)
        self.assertTrue(plan.partition_ids)
        self.assertEqual(plan.file_count, 4 + len(plan.partition_ids))
        for partition_id in plan.partition_ids:
            self.assertTrue((self.selective_root / "regions" / "ar-caba" / PARTITIONS_DIR / PARTITION_FILE_TEMPLATE.format(bucket=int(partition_id[1:]))).is_file())

    def test_search_scans_larger_provinces_in_bounded_chunks(self):
        products = [
            {"productEvidenceKey": f"p-{index:03d}", "name": "LECHE ESPECIAL" if index in {1, 4} else "ARROZ", "brand": None, "gtin": None}
            for index in range(1, 7)
        ]
        results = _bounded_chunked_search(products, "leche", limit=2, max_candidates=2)
        self.assertEqual([item.product_evidence_key for item in results], ["p-001", "p-004"])

    def test_nearby_query_returns_exact_evidence_and_unknown_boundaries(self):
        result = query_nearby(
            self.selective_root,
            "ar-caba",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="0",
            product_query="leche",
            max_offers=10,
            **self._query_kwargs(),
        )
        self.assertEqual(len(result["offers"]), 1)
        offer = result["offers"][0]
        self.assertEqual(offer["listPrice"], {"amount": "1250.00", "currency": "ARS"})
        self.assertEqual(offer["quantity"], {"unit": "GRAM", "value": "1000", "raw_value": "1", "raw_unit": "KG"})
        self.assertEqual(offer["availability"], "UNKNOWN")
        self.assertEqual(offer["promotions"][0]["eligibility"], "UNKNOWN")
        self.assertEqual(offer["distanceKm"], "0.000000")
        self.assertEqual(offer["distanceSemantics"], "STRAIGHT_LINE_HAVERSINE")

        unknown = query_nearby(
            self.selective_root,
            "ar-caba",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="50",
            product_query="producto sin gtin",
            max_offers=10,
            **self._query_kwargs(),
        )
        self.assertTrue(unknown["productCandidates"])
        self.assertEqual(unknown["productCandidates"][0]["quantityStatus"], "UNKNOWN")
        self.assertIsNone(unknown["productCandidates"][0]["gtin"])
        self.assertEqual(unknown["productCandidates"][0]["gtinStatus"], "INVALID_OR_NOT_GTIN")
        self.assertEqual(unknown["offers"], [])

    def test_query_matches_full_verified_shard_and_respects_radius(self):
        kwargs = dict(
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="50",
            product_query="leche",
            max_offers=10,
        )
        selective = query_nearby(self.selective_root, "ar-caba", **kwargs, **self._query_kwargs())
        full = query_full_shard(self.national, "ar-caba", **kwargs)
        self.assertEqual(selective["offers"], full["offers"])

        far = query_nearby(
            self.selective_root,
            "ar-caba",
            latitude="0",
            longitude="0",
            radius_km="50",
            product_query="leche",
            max_offers=10,
            **self._query_kwargs(),
        )
        self.assertEqual(far["offers"], [])

    def test_same_gtin_keeps_retailer_and_region_evidence_separate(self):
        caba = query_nearby(
            self.selective_root,
            "ar-caba",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="500",
            product_query="leche",
            max_offers=10,
            **self._query_kwargs(),
        )
        buenos_aires = query_nearby(
            self.selective_root,
            "ar-b",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="500",
            product_query="leche",
            max_offers=10,
            **self._query_kwargs(),
        )
        self.assertEqual(caba["offers"][0]["gtin"], buenos_aires["offers"][0]["gtin"])
        self.assertNotEqual(caba["offers"][0]["productEvidenceKey"], buenos_aires["offers"][0]["productEvidenceKey"])
        self.assertNotEqual(caba["offers"][0]["storeKey"], buenos_aires["offers"][0]["storeKey"])

    def test_adjacent_product_buckets_are_selected_without_boundary_loss(self):
        plan = plan_query(self.selective_root, "ar-caba", ["leche", "producto sin gtin"], product_limit=5, **PROVENANCE)
        self.assertEqual(set(plan.partition_ids), {"p000", "p001"})
        self.assertEqual(
            {candidate["partitionId"] for candidate in plan.product_candidates},
            set(plan.partition_ids),
        )

    def test_multi_item_request_is_structured_and_not_optimized(self):
        result = query_structured_request(
            self.selective_root,
            "ar-caba",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="50",
            items=[{"query": "leche", "amount": 3, "unit": "l"}, {"query": "arroz", "amount": "4.00", "unit": "kg"}],
            max_offers=10,
            **self._query_kwargs(),
        )
        self.assertEqual([item["amount"] for item in result["items"]], ["3", "4.00"])
        self.assertEqual([item["unit"] for item in result["items"]], ["l", "kg"])
        self.assertIn("offers", result["items"][0])
        self.assertNotIn("basketTotal", result)
        with self.assertRaises(ArgentinaSepaQueryError):
            query_structured_request(
                self.selective_root,
                "ar-caba",
                latitude="-34.6037",
                longitude="-58.3816",
                radius_km="50",
                items=[{"query": "leche", "amount": 0, "unit": "l"}],
                **self._query_kwargs(),
            )
        with self.assertRaises(ArgentinaSepaQueryError):
            query_structured_request(
                self.selective_root,
                "ar-caba",
                latitude="-34.6037",
                longitude="-58.3816",
                radius_km="50",
                items=[{"query": "leche", "amount": 1, "unit": "l"}] * 11,
                **self._query_kwargs(),
            )

    def test_distance_is_haversine_and_coordinates_are_not_routed(self):
        self.assertAlmostEqual(straight_line_distance_km("-34.6037", "-58.3816", "-34.6037", "-58.3816"), 0.0, places=12)
        self.assertAlmostEqual(straight_line_distance_km("-34.6037", "-58.3816", "-34.6137", "-58.3816"), 1.11195, places=3)

    def test_tampered_missing_wrong_region_and_wrong_release_fail_closed(self):
        tampered = self._copy_selective("tampered")
        partition = next((path for path in (tampered / "regions" / "ar-caba" / PARTITIONS_DIR).glob("*.jsonl.gz") if path.stat().st_size > 30), None)
        self.assertIsNotNone(partition)
        data = bytearray(partition.read_bytes())
        data[-1] ^= 1
        partition.write_bytes(data)
        with self.assertRaises(QuerySelectiveMobileVerificationError):
            verify_query_selective_mobile(tampered, **PROVENANCE, expected_bucket_count=4)

        missing = self._copy_selective("missing")
        (missing / "regions" / "ar-caba" / PARTITIONS_DIR / "p000.jsonl.gz").unlink()
        with self.assertRaises(QuerySelectiveMobileVerificationError):
            verify_query_selective_mobile(missing, **PROVENANCE, expected_bucket_count=4)

        wrong_region = self._copy_selective("wrong-region")
        manifest_path = wrong_region / "regions" / "ar-caba" / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["region"]["id"] = "ar-b"
        manifest_path.write_bytes(_canonical_json(manifest))
        with self.assertRaises(QuerySelectiveMobileVerificationError):
            verify_query_selective_mobile(wrong_region, **PROVENANCE, expected_bucket_count=4)

        wrong_release = self._copy_selective("wrong-release")
        bootstrap_path = wrong_release / "bootstrap.json"
        bootstrap = json.loads(bootstrap_path.read_text(encoding="utf-8"))
        bootstrap["source"]["releaseDate"] = "2026-09-07"
        bootstrap_path.write_bytes(_canonical_json(bootstrap))
        with self.assertRaises(QuerySelectiveMobileVerificationError):
            verify_query_selective_mobile(wrong_release, **PROVENANCE, expected_bucket_count=4)

        bad_integrity = self._copy_selective("bad-integrity")
        integrity_path = bad_integrity / "integrity.json"
        integrity = json.loads(integrity_path.read_text(encoding="utf-8"))
        integrity["regionIds"] = list(reversed(integrity["regionIds"]))
        integrity_path.write_bytes(_canonical_json(integrity))
        with self.assertRaises(ArgentinaSepaQueryError):
            load_region_contract(bad_integrity, "ar-caba", **PROVENANCE)

    def test_atomic_candidate_is_removed_when_generation_fails(self):
        root = Path(self.tempdir.name) / "atomic"
        with patch("tools.build_argentina_sepa_query_selective_mobile._write_region", side_effect=RuntimeError("injected failure")):
            with self.assertRaises(RuntimeError):
                build_query_selective_mobile(
                    self.national,
                    root,
                    generated_at="2026-09-07T12:00:00Z",
                    bucket_count=4,
                    expected_outer_sha256=OUTER_SHA,
                    expected_outer_bytes=OUTER_BYTES,
                    expected_accepted_sha256=None,
                    expected_national_index_sha256=None,
                )
        self.assertFalse(root.exists())
        self.assertEqual(list(root.parent.glob(f".{root.name}.partial-*")), [])

    def test_windows_path_and_repeat_query_are_supported(self):
        path_with_windows_separators = Path(str(self.selective_root).replace("/", "\\"))
        first = query_nearby(
            path_with_windows_separators,
            "ar-caba",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="50",
            product_query="leche",
            max_offers=10,
            **self._query_kwargs(),
        )
        second = query_nearby(
            self.selective_root,
            "ar-caba",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="50",
            product_query="leche",
            max_offers=10,
            **self._query_kwargs(),
        )
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()

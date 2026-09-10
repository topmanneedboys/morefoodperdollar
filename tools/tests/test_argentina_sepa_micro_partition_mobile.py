from __future__ import annotations

import dataclasses
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.argentina_sepa_micro_partition import (
    MicroPartitionQueryError,
    _read_member,
    activate_cached_member,
    load_micro_region_contract,
    load_micro_region_routing,
    plan_micro_query,
    query_micro_nearby,
    query_micro_structured_request,
    required_slices,
)
from tools.argentina_sepa_query import query_full_shard, query_nearby
from tools.build_argentina_sepa_micro_partition_mobile import (
    build_micro_partition_mobile,
    logical_partition_for_product,
)
from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS, _canonical_json
from tools.tests.test_argentina_sepa_national_shards import (
    OUTER_BYTES,
    OUTER_SHA,
    VALID_GTIN,
    ArgentinaSepaNationalShardsTest,
    _build,
)
from tools.build_argentina_sepa_query_selective_mobile import build_query_selective_mobile
from tools.verify_argentina_sepa_micro_partition_mobile import (
    MicroPartitionVerificationError,
    decode_member_bytes,
    verify_micro_partition_mobile,
)


PROVENANCE = {
    "expected_outer_sha256": OUTER_SHA,
    "expected_outer_bytes": OUTER_BYTES,
    "expected_release_date": "2026-09-06",
    "expected_accepted_sha256": None,
    "expected_national_index_sha256": None,
}


class ArgentinaSepaMicroPartitionMobileTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tempdir = tempfile.TemporaryDirectory(prefix="argentina-micro-partition-test-")
        root = Path(cls.tempdir.name)
        rows = ArgentinaSepaNationalShardsTest()._rows()
        cls.national, _ = _build(root / "input", rows)
        cls.selective = root / "selective"
        build_query_selective_mobile(
            cls.national,
            cls.selective,
            generated_at="2026-09-07T12:00:00Z",
            bucket_count=4,
            expected_outer_sha256=OUTER_SHA,
            expected_outer_bytes=OUTER_BYTES,
            expected_accepted_sha256=None,
            expected_national_index_sha256=None,
        )
        cls.micro = root / "micro"
        build_micro_partition_mobile(
            cls.selective,
            cls.micro,
            generated_at="2026-09-07T12:00:00Z",
            logical_partition_count=128,
            physical_pack_count=8,
            **PROVENANCE,
        )

    @classmethod
    def tearDownClass(cls) -> None:
        cls.tempdir.cleanup()

    def test_contract_is_verified_and_preserves_all_regions_and_unknown_boundaries(self):
        result = verify_micro_partition_mobile(
            self.micro,
            **PROVENANCE,
            expected_logical_partition_count=128,
            expected_physical_pack_count=8,
        )
        self.assertEqual(result["regions"], len(ARGENTINA_REGIONS))
        self.assertEqual(result["stores"], 4)
        self.assertEqual(result["productEvidenceRecords"], 4)
        self.assertEqual(result["offers"], 4)
        bootstrap = json.loads((self.micro / "bootstrap.json").read_text(encoding="utf-8"))
        self.assertFalse(bootstrap["productionUiAuthorized"])
        self.assertEqual(bootstrap["boundaries"]["availability"], "UNKNOWN")
        self.assertEqual(bootstrap["boundaries"]["androidNetworking"], "NOT_AUTHORIZED")
        self.assertEqual(bootstrap["source"]["license"], "Creative Commons Attribution 4.0")

    def test_routing_loader_only_requires_store_index_and_fails_closed_when_corrupt(self):
        routing = load_micro_region_routing(self.micro, "ar-caba", **PROVENANCE)
        self.assertEqual(routing.region.region_id, "ar-caba")
        self.assertTrue(routing.store_descriptor["path"].endswith("store-index.jsonl.gz"))

        broken = Path(self.tempdir.name) / "routing-broken"
        shutil.copytree(self.micro, broken)
        store_path = broken / "regions" / "ar-caba" / "store-index.jsonl.gz"
        store_path.write_bytes(b"corrupt")
        with self.assertRaises(MicroPartitionQueryError):
            load_micro_region_routing(broken, "ar-caba", **PROVENANCE)

    def test_full_contract_validates_pack_descriptors_without_touching_pack_files(self):
        deferred = Path(self.tempdir.name) / "deferred-packs"
        shutil.copytree(self.micro, deferred)
        for pack in (deferred / "regions" / "ar-caba" / "packs").glob("pack*.bin"):
            pack.unlink()
        contract = load_micro_region_contract(
            deferred,
            "ar-caba",
            verify_companion_files=False,
            verify_pack_files=False,
            **PROVENANCE,
        )
        self.assertEqual(len(contract.pack_descriptors), 8)
        self.assertEqual(len(contract.partition_descriptors), 128)

    def test_repeat_build_is_byte_identical(self):
        repeat = Path(self.tempdir.name) / "repeat"
        build_micro_partition_mobile(
            self.selective,
            repeat,
            generated_at="2026-09-07T12:00:00Z",
            logical_partition_count=128,
            physical_pack_count=8,
            **PROVENANCE,
        )
        first = sorted(path.relative_to(self.micro) for path in self.micro.rglob("*") if path.is_file())
        second = sorted(path.relative_to(repeat) for path in repeat.rglob("*") if path.is_file())
        self.assertEqual(first, second)
        for relative in first:
            self.assertEqual((self.micro / relative).read_bytes(), (repeat / relative).read_bytes(), str(relative))

    def test_logical_members_have_contiguous_exact_ranges_and_no_surrounding_bytes_needed(self):
        contract = load_micro_region_contract(self.micro, "ar-caba", **PROVENANCE)
        by_pack: dict[str, list[tuple[int, int]]] = {}
        for descriptor in contract.partition_descriptors.values():
            data = (contract.root / descriptor["path"]).read_bytes()[descriptor["byteOffset"] : descriptor["byteOffset"] + descriptor["byteLength"]]
            records = decode_member_bytes(data, descriptor)
            self.assertEqual(len(records), descriptor["recordCount"])
            by_pack.setdefault(descriptor["packId"], []).append((descriptor["byteOffset"], descriptor["byteLength"]))
        for pack_id, ranges in by_pack.items():
            cursor = 0
            for offset, length in sorted(ranges):
                self.assertEqual(offset, cursor, pack_id)
                cursor += length
            self.assertEqual(cursor, contract.pack_descriptors[pack_id]["bytes"])

    def test_query_is_exactly_equivalent_to_milestone_four_and_keeps_evidence(self):
        kwargs = {"latitude": "-34.6037", "longitude": "-58.3816", "radius_km": "50", "product_query": "leche", "product_limit": 5, "max_candidates": 100_000, "max_offers": 10}
        result = query_micro_nearby(self.micro, "ar-caba", **kwargs, **PROVENANCE)
        reference = query_nearby(self.selective, "ar-caba", **kwargs, expected_accepted_sha256=None, expected_national_index_sha256=None, expected_outer_sha256=OUTER_SHA, expected_outer_bytes=OUTER_BYTES)
        full = query_full_shard(self.national, "ar-caba", **kwargs)
        self.assertEqual(result["offers"], reference["offers"])
        self.assertEqual(result["offers"], full["offers"])
        self.assertEqual(result["offers"][0]["availability"], "UNKNOWN")
        self.assertEqual(result["offers"][0]["listPrice"], {"amount": "1250.00", "currency": "ARS"})
        self.assertEqual(result["offers"][0]["quantity"]["unit"], "GRAM")

    def test_radius_boundary_and_same_gtin_across_retailers_match_reference(self):
        boundary = {
            "latitude": "-34.6037",
            "longitude": "-58.3816",
            "radius_km": "0",
            "product_query": "leche",
            "product_limit": 5,
            "max_candidates": 100_000,
            "max_offers": 10,
        }
        micro = query_micro_nearby(self.micro, "ar-caba", **boundary, **PROVENANCE)
        reference = query_nearby(
            self.selective,
            "ar-caba",
            **boundary,
            expected_accepted_sha256=None,
            expected_national_index_sha256=None,
            expected_outer_sha256=OUTER_SHA,
            expected_outer_bytes=OUTER_BYTES,
        )
        self.assertEqual(micro["offers"], reference["offers"])
        self.assertEqual(micro["productCandidates"][0]["gtin"], VALID_GTIN)

        ba_boundary = {**boundary, "latitude": "-34.6037", "longitude": "-58.3816"}
        ba = query_micro_nearby(self.micro, "ar-b", **ba_boundary, **PROVENANCE)
        self.assertTrue(ba["offers"])
        self.assertEqual(ba["productCandidates"][0]["gtin"], VALID_GTIN)

    def test_wrong_source_or_release_is_rejected(self):
        with self.assertRaises(MicroPartitionQueryError):
            load_micro_region_contract(self.micro, "ar-caba", **{**PROVENANCE, "expected_release_date": "2026-09-07"})
        with self.assertRaises(MicroPartitionQueryError):
            load_micro_region_contract(self.micro, "ar-caba", **{**PROVENANCE, "expected_outer_sha256": "b" * 64})

    def test_plan_reports_logical_partitions_physical_packs_ranges_and_decompressed_bytes(self):
        plan = plan_micro_query(self.micro, "ar-caba", ["leche", "producto sin gtin"], product_limit=5, max_candidates=100_000, **PROVENANCE)
        self.assertTrue(plan.partition_ids)
        self.assertEqual(set(plan.physical_pack_ids), {item["packId"] for item in plan.slices})
        self.assertEqual(plan.compressed_bytes, sum(item["byteLength"] for item in plan.slices))
        self.assertEqual(plan.decompressed_bytes, sum(item["uncompressedBytes"] for item in plan.slices))
        self.assertEqual(plan.file_count, 4 + len(plan.physical_pack_ids))
        self.assertTrue(all(item["byteLength"] <= item["packBytes"] for item in plan.slices))

    def test_cache_reuse_verifies_then_activates_atomically_and_reports_hits(self):
        plan = plan_micro_query(self.micro, "ar-caba", ["leche"], product_limit=5, max_candidates=100_000, **PROVENANCE)
        descriptor = plan.slices[0]
        pack = self.micro / descriptor["path"]
        data = pack.read_bytes()[descriptor["byteOffset"] : descriptor["byteOffset"] + descriptor["byteLength"]]
        cache = Path(self.tempdir.name) / "cache"
        activated = activate_cached_member(cache, descriptor, data)
        self.assertTrue(activated.is_file())
        self.assertFalse(list(cache.rglob("*.partial")))
        cached = {descriptor["partitionId"]: descriptor}
        reuse = required_slices(plan, cached)
        self.assertEqual(reuse["cacheHitCount"], 1)
        self.assertEqual(reuse["cacheMissCount"], 0)
        self.assertEqual(reuse["slices"], [])

    def test_tampered_truncated_and_wrong_pack_or_offset_fail_closed(self):
        contract = load_micro_region_contract(self.micro, "ar-caba", **PROVENANCE)
        partition_id, descriptor = next(iter(contract.partition_descriptors.items()))
        pack = contract.root / descriptor["path"]
        raw = pack.read_bytes()
        data = raw[descriptor["byteOffset"] : descriptor["byteOffset"] + descriptor["byteLength"]]
        tampered = bytearray(data)
        tampered[-1] ^= 1
        with self.assertRaises(MicroPartitionVerificationError):
            decode_member_bytes(bytes(tampered), descriptor)
        with self.assertRaises(MicroPartitionVerificationError):
            decode_member_bytes(data[:-1], {**descriptor, "byteLength": len(data) - 1, "bytes": len(data) - 1})
        wrong_pack = dataclasses.replace(contract, partition_descriptors={partition_id: {**descriptor, "packId": "pack999"}})
        with self.assertRaises(MicroPartitionQueryError):
            _read_member(wrong_pack, partition_id)
        wrong_offset = dataclasses.replace(contract, partition_descriptors={partition_id: {**descriptor, "byteOffset": descriptor["byteOffset"] + 1}})
        with self.assertRaises(MicroPartitionQueryError):
            _read_member(wrong_offset, partition_id)

    def test_incomplete_candidate_is_removed_and_prior_root_is_not_overwritten(self):
        destination = Path(self.tempdir.name) / "interrupted"
        with patch("tools.build_argentina_sepa_micro_partition_mobile._write_region", side_effect=RuntimeError("injected interruption")):
            with self.assertRaises(RuntimeError):
                build_micro_partition_mobile(
                    self.selective,
                    destination,
                    generated_at="2026-09-07T12:00:00Z",
                    logical_partition_count=128,
                    physical_pack_count=8,
                    **PROVENANCE,
                )
        self.assertFalse(destination.exists())
        self.assertFalse(list(destination.parent.glob(f".{destination.name}.partial-*")))

    def test_windows_shaped_root_path_and_structured_request_remain_bounded(self):
        windows_path = str(self.micro).replace("/", "\\")
        result = query_micro_structured_request(
            windows_path,
            "ar-caba",
            latitude="-34.6037",
            longitude="-58.3816",
            radius_km="50",
            items=[{"query": "leche", "amount": 3, "unit": "l"}, {"query": "arroz", "amount": "4.00", "unit": "kg"}],
            product_limit=5,
            max_candidates=100_000,
            max_offers=10,
            **PROVENANCE,
        )
        self.assertEqual([item["amount"] for item in result["items"]], ["3", "4.00"])
        self.assertEqual(len(result["items"]), 2)
        self.assertNotIn("basketTotal", result)

    def test_same_gtin_and_unknown_quantity_are_not_repaired_or_joined(self):
        result = query_micro_nearby(self.micro, "ar-caba", latitude="-34.6037", longitude="-58.3816", radius_km="50", product_query="producto sin gtin", product_limit=5, max_candidates=100_000, max_offers=10, **PROVENANCE)
        self.assertTrue(result["productCandidates"])
        candidate = result["productCandidates"][0]
        self.assertIsNone(candidate["gtin"])
        self.assertEqual(candidate["gtinStatus"], "INVALID_OR_NOT_GTIN")
        self.assertEqual(candidate["quantityStatus"], "UNKNOWN")
        self.assertIsNone(candidate["quantity"])


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import gzip
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from backend.artifacts import ManifestReleaseArtifactStore
from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_searchpack import (
    SearchPackError,
    SearchPackManager,
    SearchPackStats,
    SEARCHPACK_CANDIDATE_HORIZON,
)
from tools.build_argentina_searchpack import SEARCHPACK_RELEASE_SUFFIX, derive_searchpack_workspace
from tools.argentina_sepa_search import search_products
from tools.consumer_input_intelligence import CatalogIndex, parse_intent


def _canonical(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _put_object(root: Path, data: bytes) -> dict[str, object]:
    digest = hashlib.sha256(data).hexdigest()
    (root / "objects" / "sha256" / digest).write_bytes(data)
    return {"bytes": len(data), "sha256": digest}


def _fixture_workspace(rows: list[dict[str, object]]) -> tuple[Path, str]:
    root = Path(tempfile.mkdtemp(prefix="valuepilot-searchpack-source-"))
    (root / "objects" / "sha256").mkdir(parents=True)
    (root / "releases" / "release").mkdir(parents=True)
    # SearchPack ranked results carry the physical partition identity that the
    # backend planner consumes.  Keep the tiny source fixtures convenient by
    # assigning a stable test-only identity when a case does not need to
    # specify one explicitly.
    normalized_rows = []
    for index, row in enumerate(rows):
        value = dict(row)
        value.setdefault("partitionId", f"fixture-partition-{index:04d}")
        normalized_rows.append(value)
    uncompressed = b"".join(_canonical(row) for row in normalized_rows)
    compressed = gzip.compress(uncompressed, mtime=0)
    search_object = _put_object(root, compressed)
    regional_manifest_value = {
        "schemaVersion": "valuepilot-regional-search-fixture-v1",
        "files": {
            "searchIndex": {
                "path": "micro-1024/regions/ar-caba/search-index.jsonl.gz",
                "bytes": len(compressed),
                "sha256": search_object["sha256"],
                "uncompressedBytes": len(uncompressed),
                "uncompressedSha256": hashlib.sha256(uncompressed).hexdigest(),
            }
        },
    }
    regional_object = _put_object(root, _canonical(regional_manifest_value))
    manifest = {
        "completionState": "COMPLETE",
        "releaseId": "release",
        "objects": [
            {"path": "micro-1024/regions/ar-caba/search-index.jsonl.gz", **search_object, "uncompressedBytes": len(uncompressed)},
            {"path": "micro-1024/regions/ar-caba/manifest.json", **regional_object},
        ],
    }
    (root / "releases" / "release" / "manifest.json").write_bytes(_canonical(manifest))
    return root, "release"


def _manager(workspace: Path) -> SearchPackManager:
    release_id = f"release{SEARCHPACK_RELEASE_SUFFIX}"
    manifest = json.loads((workspace / "releases" / release_id / "manifest.json").read_bytes())
    artifacts = ManifestReleaseArtifactStore(LocalFilesystemObjectStore(workspace), manifest, release_id=release_id)
    return SearchPackManager(artifacts, manifest)


def _new_output(prefix: str) -> Path:
    parent = Path(tempfile.mkdtemp(prefix=prefix))
    return parent / "workspace"


class ArgentinaSearchPackTests(unittest.TestCase):
    def test_search_many_preserves_materialized_partition_identity(self) -> None:
        rows = [{
            "productEvidenceKey": "arroz-key",
            "name": "Arroz 1 kg",
            "brand": None,
            "canonicalSearchAliases": [],
            "partitionId": "p-explicit",
        }]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-partition-preservation-")
        derive_searchpack_workspace(source, output, [release_id])

        values = _manager(output).region("ar-caba").search_many(("arroz",))

        self.assertEqual(values[0][0]["productEvidenceKey"], "arroz-key")
        self.assertEqual(values[0][0]["partitionId"], "p-explicit")

    def test_search_many_rejects_missing_or_invalid_partition_identity(self) -> None:
        source, release_id = _fixture_workspace([{
            "productEvidenceKey": "arroz-key",
            "name": "Arroz 1 kg",
            "brand": None,
            "canonicalSearchAliases": [],
        }])
        output = _new_output("valuepilot-searchpack-partition-validation-")
        derive_searchpack_workspace(source, output, [release_id])
        region = _manager(output).region("ar-caba")

        for label, record in (
            ("missing", {"productEvidenceKey": "arroz-key", "name": "Arroz 1 kg"}),
            ("empty", {"productEvidenceKey": "arroz-key", "name": "Arroz 1 kg", "partitionId": ""}),
            ("non-string", {"productEvidenceKey": "arroz-key", "name": "Arroz 1 kg", "partitionId": 7}),
        ):
            with self.subTest(label=label):
                with patch.object(region, "_rank_query", return_value=((100, 0, ("arroz",)),)), patch.object(region, "get_records", return_value={0: record}):
                    with self.assertRaisesRegex(SearchPackError, "partition identity is invalid"):
                        region.search_many(("arroz",))

    def test_reproducible_build_and_feature_key_lookup(self) -> None:
        rows = [
            {"productEvidenceKey": "z", "name": "Arroz 1 kg", "brand": "Marca", "canonicalSearchAliases": []},
            {"productEvidenceKey": "a", "name": "Leche 1 l", "brand": None, "canonicalSearchAliases": []},
        ]
        source, release_id = _fixture_workspace(rows)
        first = _new_output("valuepilot-searchpack-first-")
        second = _new_output("valuepilot-searchpack-second-")
        derive_searchpack_workspace(source, first, [release_id])
        derive_searchpack_workspace(source, second, [release_id])
        release_id = f"release{SEARCHPACK_RELEASE_SUFFIX}"
        first_manifest = (first / "releases" / release_id / "manifest.json").read_bytes()
        second_manifest = (second / "releases" / release_id / "manifest.json").read_bytes()
        self.assertEqual(first_manifest, second_manifest)
        manager = _manager(first)
        region = manager.region("ar-caba")
        catalog = CatalogIndex(())
        intent = parse_intent("arroz", data=catalog.data)
        stats = SearchPackStats()
        lookup = region.lookup_features(catalog.intent_feature_keys(intent), stats=stats)
        self.assertFalse(lookup.saturated)
        self.assertEqual(len(lookup.doc_ids), 1)
        records = region.get_records(lookup.doc_ids, stats=stats)
        self.assertEqual(records[lookup.doc_ids[0]]["name"], "Arroz 1 kg")
        self.assertEqual(region.lookup_product_key("a", stats=stats)["name"], "Leche 1 l")
        self.assertGreater(stats.feature_lookups, 0)

    def test_candidate_horizon_is_explicit_saturation(self) -> None:
        rows = [
            {"productEvidenceKey": f"key-{index:03d}", "name": f"Arroz Marca {index} 1 kg", "brand": None, "canonicalSearchAliases": []}
            for index in range(SEARCHPACK_CANDIDATE_HORIZON + 1)
        ]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-saturation-")
        derive_searchpack_workspace(source, output, [release_id])
        manager = _manager(output)
        catalog = CatalogIndex(())
        lookup = manager.region("ar-caba").lookup_features(catalog.intent_feature_keys(parse_intent("arroz", data=catalog.data)))
        self.assertTrue(lookup.saturated)
        self.assertEqual(lookup.doc_ids, ())

    def test_corrupt_postings_object_fails_closed(self) -> None:
        rows = [{"productEvidenceKey": "a", "name": "Arroz 1 kg", "brand": None, "canonicalSearchAliases": []}]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-corrupt-")
        derive_searchpack_workspace(source, output, [release_id])
        release_id = f"release{SEARCHPACK_RELEASE_SUFFIX}"
        manifest_path = output / "releases" / release_id / "manifest.json"
        manifest = json.loads(manifest_path.read_bytes())
        postings = next(item for item in manifest["objects"] if item["path"].endswith("/postings.bin"))
        object_path = output / "objects" / "sha256" / postings["sha256"]
        object_path.write_bytes(object_path.read_bytes() + b"x")
        manager = _manager(output)
        catalog = CatalogIndex(())
        with self.assertRaises(SearchPackError):
            manager.region("ar-caba").lookup_features(catalog.intent_feature_keys(parse_intent("arroz", data=catalog.data)))

    def test_declared_pack_with_missing_metadata_fails_closed(self) -> None:
        rows = [{"productEvidenceKey": "a", "name": "Arroz 1 kg", "brand": None, "canonicalSearchAliases": []}]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-missing-")
        derive_searchpack_workspace(source, output, [release_id])
        release_id = f"release{SEARCHPACK_RELEASE_SUFFIX}"
        manifest_path = output / "releases" / release_id / "manifest.json"
        manifest = json.loads(manifest_path.read_bytes())
        manifest["searchPackArtifact"]["metadataSha256"]["ar-caba"] = "0" * 64
        artifacts = ManifestReleaseArtifactStore(LocalFilesystemObjectStore(output), manifest, release_id=release_id)
        with self.assertRaises(SearchPackError):
            SearchPackManager(artifacts, manifest).region("ar-caba")

    def test_search_many_uses_index_native_ranker(self) -> None:
        rows = [
            {"productEvidenceKey": "a", "name": "Arroz Marca 1 kg", "brand": None, "canonicalSearchAliases": []},
            {"productEvidenceKey": "b", "name": "Leche Marca 1 l", "brand": None, "canonicalSearchAliases": []},
            {"productEvidenceKey": "c", "name": "Condimento para arroz", "brand": None, "canonicalSearchAliases": []},
        ]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-ranker-")
        derive_searchpack_workspace(source, output, [release_id])
        values = _manager(output).region("ar-caba").search_many(("arroz", "leche"))
        self.assertEqual(values[0][0]["productEvidenceKey"], "a")
        self.assertEqual(values[1][0]["productEvidenceKey"], "b")
        self.assertNotIn("c", {item["productEvidenceKey"] for item in values[0]})

    def test_index_native_ranker_matches_exhaustive_score_and_materializes_top_k_only(self) -> None:
        rows = [
            {"productEvidenceKey": "a", "name": "Arroz Marca 1 kg", "brand": None, "canonicalSearchAliases": []},
            {"productEvidenceKey": "b", "name": "Arroz Marca 2 kg", "brand": "Marca", "canonicalSearchAliases": ["rice"]},
            {"productEvidenceKey": "c", "name": "Condimento para arroz", "brand": None, "canonicalSearchAliases": []},
            {"productEvidenceKey": "d", "name": "Huevos Grandes 12", "brand": None, "canonicalSearchAliases": []},
            {"productEvidenceKey": "e", "name": "Pasta de Huevo 500 g", "brand": None, "canonicalSearchAliases": []},
        ]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-index-native-")
        derive_searchpack_workspace(source, output, [release_id])
        derived_id = f"{release_id}{SEARCHPACK_RELEASE_SUFFIX}"
        manifest = json.loads((output / "releases" / derived_id / "manifest.json").read_bytes())
        manager = SearchPackManager(ManifestReleaseArtifactStore(LocalFilesystemObjectStore(output), manifest, release_id=derived_id), manifest)
        region = manager.region("ar-caba")
        stats = SearchPackStats()
        queries = ("arroz", "huevos", "rice")
        actual = region.search_many(queries, product_limit=2, candidate_bound=100_000, stats=stats)
        expected = tuple(tuple(item.as_dict() for item in search_products(rows, query, limit=2, max_candidates=100_000)) for query in queries)
        actual_without_partition = tuple(
            tuple({key: value for key, value in item.items() if key != "partitionId"} for item in values)
            for values in actual
        )
        self.assertEqual(actual_without_partition, expected)
        self.assertTrue(all(isinstance(item["partitionId"], str) and item["partitionId"] for values in actual for item in values))
        self.assertEqual(stats.ranked_records_materialized, 4)
        self.assertEqual(stats.records_returned, 4)
        self.assertLessEqual(stats.ranked_records_materialized, len(queries) * 2)
        self.assertGreater(stats.ranked_candidates, stats.ranked_records_materialized)

    def test_high_df_saturation_is_metadata_only(self) -> None:
        rows = [
            {"productEvidenceKey": f"key-{index:03d}", "name": f"Arroz Marca {index:03d} 1 kg", "brand": None, "canonicalSearchAliases": []}
            for index in range(SEARCHPACK_CANDIDATE_HORIZON + 1)
        ]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-metadata-saturation-")
        derive_searchpack_workspace(source, output, [release_id])
        manager = _manager(output)
        stats = SearchPackStats()
        catalog = CatalogIndex(())
        lookup = manager.region("ar-caba").lookup_features(catalog.intent_feature_keys(parse_intent("arroz", data=catalog.data)), stats=stats)
        self.assertTrue(lookup.saturated)
        self.assertEqual(stats.posting_reads, 0)
        self.assertEqual(stats.posting_bytes, 0)
        self.assertEqual(stats.docstore_reads, 0)

    def test_manager_cache_is_global_and_hard_bounded(self) -> None:
        rows = [{"productEvidenceKey": "a", "name": "Arroz 1 kg", "brand": None, "canonicalSearchAliases": []}]
        source, release_id = _fixture_workspace(rows)
        output = _new_output("valuepilot-searchpack-global-cache-")
        derive_searchpack_workspace(source, output, [release_id])
        derived_id = f"{release_id}{SEARCHPACK_RELEASE_SUFFIX}"
        manifest = json.loads((output / "releases" / derived_id / "manifest.json").read_bytes())
        manager = SearchPackManager(ManifestReleaseArtifactStore(LocalFilesystemObjectStore(output), manifest, release_id=derived_id), manifest, cache_bytes=1024)
        # Simulate 24 instantiated regions touching distinct immutable ranges.
        for region_index in range(24):
            manager._cache.put(f"region-{region_index}:object:{region_index}", bytes([region_index]) * 96)
        self.assertLessEqual(manager.cache_usage_bytes, 1024)
        self.assertLessEqual(manager.cache_usage_bytes, manager.cache_bytes)


if __name__ == "__main__":
    unittest.main()

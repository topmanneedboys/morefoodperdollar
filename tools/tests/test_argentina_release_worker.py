from __future__ import annotations

import hashlib
import gzip
import json
import tempfile
import unittest
from pathlib import Path

from backend.artifacts import ManifestReleaseArtifactStore
from backend.object_store import LocalFilesystemObjectStore, ObjectStoreError
from tools.argentina_release_acquisition import DiscoveryResult
from tools.argentina_release_worker import WorkerError, prepare_searchpack_candidate, record_discovery_state, run_shadow
from tools.argentina_searchpack import SearchPackManager
from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS


FIXTURE = Path(__file__).parents[1] / "fixtures" / "argentina_release_acquisition" / "package_show.json"
SOURCE_FIXTURE = Path(__file__).parents[1] / "fixtures" / "argentina_daily_release" / "source-2026-09-08.json"


def _canonical(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _put_object(root: Path, data: bytes) -> dict[str, object]:
    digest = hashlib.sha256(data).hexdigest()
    (root / "objects" / "sha256" / digest).write_bytes(data)
    return {"bytes": len(data), "sha256": digest}


def _routing_searchpack_fixture(root: Path) -> tuple[Path, str]:
    """Create a tiny complete 24-region release for worker derivation tests."""

    objects = root / "objects" / "sha256"
    objects.mkdir(parents=True)
    release_id = "base"
    release_dir = root / "releases" / release_id
    release_dir.mkdir(parents=True)
    descriptors: list[dict[str, object]] = []
    for index, spec in enumerate(ARGENTINA_REGIONS):
        store_value = {
            "geoStatus": "VALID",
            "latitude": f"{-34 - index / 100:.2f}",
            "longitude": f"{-58 - index / 100:.2f}",
            "province": spec.province_code,
            "storeKey": f"fixture:{spec.region_id}:store",
        }
        store_raw = gzip.compress(_canonical(store_value), compresslevel=9, mtime=0)
        store_object = _put_object(root, store_raw)
        descriptors.append({"path": f"micro-1024/regions/{spec.region_id}/store-index.jsonl.gz", **store_object})

        search_value = {
            "productEvidenceKey": f"{spec.region_id}:arroz",
            "name": "Arroz 1 kg",
            "brand": "Marca",
            "canonicalSearchAliases": ["arroz"],
            "partitionId": f"fixture-partition-{index:02d}",
        }
        search_uncompressed = _canonical(search_value)
        search_raw = gzip.compress(search_uncompressed, compresslevel=9, mtime=0)
        search_object = _put_object(root, search_raw)
        search_path = f"micro-1024/regions/{spec.region_id}/search-index.jsonl.gz"
        descriptors.append({"path": search_path, **search_object, "uncompressedBytes": len(search_uncompressed)})
        regional_manifest = {
            "schemaVersion": "valuepilot-regional-search-fixture-v1",
            "files": {
                "searchIndex": {
                    "path": search_path,
                    "bytes": len(search_raw),
                    "sha256": search_object["sha256"],
                    "uncompressedBytes": len(search_uncompressed),
                    "uncompressedSha256": hashlib.sha256(search_uncompressed).hexdigest(),
                }
            },
        }
        regional_object = _put_object(root, _canonical(regional_manifest))
        descriptors.append({"path": f"micro-1024/regions/{spec.region_id}/manifest.json", **regional_object})
    manifest = {"schemaVersion": "valuepilot-argentina-daily-release-v1", "completionState": "COMPLETE", "releaseId": release_id, "objects": sorted(descriptors, key=lambda value: str(value["path"]))}
    raw = _canonical(manifest)
    (release_dir / "manifest.json").write_bytes(raw)
    (release_dir / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
    return root, release_id


class ArgentinaReleaseWorkerTests(unittest.TestCase):
    def _discovery(self) -> DiscoveryResult:
        from tools.argentina_release_acquisition import discover_metadata

        return discover_metadata(observed_at="2026-09-13T12:00:00Z", fetcher=lambda _: FIXTURE.read_bytes(), resolver=lambda host, port: ["93.184.216.34"])

    def test_discovery_state_is_write_free_when_metadata_fingerprint_is_unchanged(self) -> None:
        discovery = self._discovery()
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            store = LocalFilesystemObjectStore(directory)
            first = record_discovery_state(store, discovery, execution_id="run-1")
            second = record_discovery_state(store, discovery, execution_id="run-2")
            self.assertEqual(first["status"], "CHANGED")
            self.assertEqual(second["status"], "NO_CHANGE")
            self.assertEqual(second["activePointerWrites"], 0)
            self.assertEqual(len([path for path in Path(directory).rglob("*") if path.is_file()]), 1)

    def test_discovery_state_storage_outage_fails_closed_instead_of_looking_like_missing_state(self) -> None:
        class UnavailableStore:
            def exists(self, key):
                raise ObjectStoreError("simulated outage")

        with self.assertRaisesRegex(WorkerError, "availability is unknown"):
            record_discovery_state(UnavailableStore(), self._discovery(), execution_id="run-outage")

    def test_shadow_reuses_existing_source_for_same_hash_without_cloud_build(self) -> None:
        discovery = self._discovery()
        source_hash = hashlib.sha256(SOURCE_FIXTURE.read_bytes()).hexdigest()
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            archive = LocalFilesystemObjectStore(Path(directory) / "archive")
            result = run_shadow(discovery=discovery, current_release_date="2026-09-08", current_source_sha256=source_hash, execution_id="run-noop", observed_at="2026-09-13T12:00:00Z", source_path=SOURCE_FIXTURE, archive_store=archive, fixture=True)
            self.assertEqual(result["status"], "NO_CHANGE")
            self.assertEqual(result["sourceDecision"]["disposition"], "NO_CHANGE")
            self.assertFalse(result["cloudBuildStarted"])
            self.assertEqual(result["activePointerWrites"], 0)
            self.assertTrue(any(path.name.endswith(".zip") for path in Path(directory, "archive").rglob("*")))

    def test_shadow_new_fixture_reaches_existing_verified_orchestrator_without_activation(self) -> None:
        value = json.loads(SOURCE_FIXTURE.read_text(encoding="utf-8"))
        value["source"]["releaseDate"] = "2026-09-09"
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            source = Path(directory) / "new-source.json"
            source.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True), encoding="utf-8")
            discovery = self._discovery()
            result = run_shadow(discovery=discovery, current_release_date="2026-09-08", current_source_sha256="a" * 64, execution_id="run-new", observed_at="2026-09-13T12:00:00Z", source_path=source, archive_store=LocalFilesystemObjectStore(Path(directory) / "archive"), workspace=Path(directory) / "workspace", output=Path(directory) / "output", fixture=True, build_evidence={"buildId": "build-1", "builderImage": "python:3.12", "builderSha": "6ed1cab"})
            self.assertEqual(result["status"], "CANDIDATE_VERIFIED")
            self.assertTrue(result["cloudBuildStarted"])
            self.assertEqual(result["activePointerWrites"], 0)
            self.assertEqual(result["buildProvenance"]["buildId"], "build-1")
            self.assertTrue((Path(directory) / "output" / "release-report.json").is_file())

    def test_searchpack_candidate_builds_all_regions_and_reuses_complete_output(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-searchpack-") as directory:
            source, release_id = _routing_searchpack_fixture(Path(directory) / "base")
            output = Path(directory) / "candidate"
            first = prepare_searchpack_candidate(source, release_id, output_workspace=output)
            self.assertEqual(first["status"], "BUILT")
            self.assertEqual(first["baseReleaseId"], release_id)
            self.assertEqual(first["routingReleaseId"], "base-routing-v1")
            self.assertEqual(first["searchPackReleaseId"], "base-routing-v1-search-v2")
            self.assertEqual(first["searchPack"]["regionCount"], len(ARGENTINA_REGIONS))
            self.assertEqual(first["activePointerWrites"], 0)
            manifest_path = output / "releases" / first["searchPackReleaseId"] / "manifest.json"
            manifest_before = manifest_path.read_bytes()
            manifest = json.loads(manifest_before)
            self.assertEqual(manifest["routingArtifact"]["sourceReleaseId"], release_id)
            self.assertEqual(manifest["searchPackArtifact"]["sourceReleaseId"], "base-routing-v1")
            manager = SearchPackManager(ManifestReleaseArtifactStore(LocalFilesystemObjectStore(output), manifest, release_id=first["searchPackReleaseId"]), manifest)
            self.assertEqual(len(manager.regions()), len(ARGENTINA_REGIONS))
            second = prepare_searchpack_candidate(source, release_id, output_workspace=output)
            self.assertEqual(second["status"], "REUSED")
            self.assertEqual(second["publicationPlan"], first["publicationPlan"])
            self.assertEqual(manifest_path.read_bytes(), manifest_before)
            self.assertFalse((output / "control" / "active.json").exists())
            self.assertFalse((output / "control" / "canary.json").exists())

    def test_searchpack_candidate_fails_closed_for_partial_or_corrupt_existing_output(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-searchpack-safety-") as directory:
            source, release_id = _routing_searchpack_fixture(Path(directory) / "base")
            output = Path(directory) / "candidate"
            partial = output.parent / f".{output.name}.partial"
            partial.mkdir()
            with self.assertRaisesRegex(WorkerError, "interrupted SearchPack candidate"):
                prepare_searchpack_candidate(source, release_id, output_workspace=output)
            partial.rmdir()
            prepare_searchpack_candidate(source, release_id, output_workspace=output)
            manifest_path = output / "releases" / "base-routing-v1-search-v2" / "manifest.json"
            manifest = json.loads(manifest_path.read_bytes())
            manifest["releaseId"] = "corrupt"
            manifest_path.write_bytes(_canonical(manifest))
            with self.assertRaisesRegex(WorkerError, "existing SearchPack candidate"):
                prepare_searchpack_candidate(source, release_id, output_workspace=output)

    def test_same_day_revision_requires_changed_official_metadata_evidence(self) -> None:
        value = json.loads(SOURCE_FIXTURE.read_text(encoding="utf-8"))
        value["source"]["releaseDate"] = "2026-09-08"
        with tempfile.TemporaryDirectory(prefix="valuepilot-worker-") as directory:
            source = Path(directory) / "revision-source.json"
            source.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True), encoding="utf-8")
            discovery = self._discovery()
            common = dict(discovery=discovery, current_release_date="2026-09-08", current_source_sha256="a" * 64, execution_id="run-revision", observed_at="2026-09-13T12:00:00Z", source_path=source, archive_store=LocalFilesystemObjectStore(Path(directory) / "archive"), fixture=True, upstream_changed=True)
            without_proof = run_shadow(**common)
            self.assertEqual(without_proof["sourceDecision"]["disposition"], "REVIEW_REQUIRED")
            with_proof = run_shadow(**{**common, "execution_id": "run-revision-proven", "upstream_change_evidence": {"changed": True, "resourceId": discovery.candidates[-1].resource_id, "previousDiscoveryResponseSha256": "b" * 64, "currentDiscoveryResponseSha256": discovery.candidates[-1].discovery_response_sha256}})
            self.assertEqual(with_proof["sourceDecision"]["disposition"], "SAME_DAY_REVISION")
            self.assertTrue(with_proof["sameDayRevisionProven"])


if __name__ == "__main__":
    unittest.main()

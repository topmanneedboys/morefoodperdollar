from __future__ import annotations

import hashlib
import json
import tempfile
import threading
import unittest
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_release_acquisition import AcquisitionCandidate
from tools.argentina_release_automation import (
    ACTIVATION_PARTICIPANTS,
    DistributedReleaseLease,
    LeaseOutcome,
    TransactionalActivationCoordinator,
    anomaly_gate,
    discovery_decision,
    redact_secrets,
    stage_verify_and_canary,
    write_execution_summary,
)


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


class ArgentinaReleaseAutomationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.candidate = AcquisitionCandidate(
            provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
            dataset_id="0f495d54-4a5d-5fa3-a323-f6e97ce434cf",
            resource_id="resource-martes-fixture",
            resource_name="Martes",
            official_url="https://datos.produccion.gob.ar/download/sepa_martes.zip",
            observed_size=None,
            last_modified=None,
            observed_at="2026-09-13T12:00:00Z",
            expected_source_host="datos.produccion.gob.ar",
            discovery_response_sha256="a" * 64,
        )

    def test_distributed_lease_create_contention_expiry_and_release(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-automation-") as directory:
            store = LocalFilesystemObjectStore(directory)
            manager = DistributedReleaseLease(store, ttl_seconds=60)
            first = manager.acquire(execution_id="run-one", candidate_identity={"sourceSha256": "a" * 64}, now="2026-09-13T12:00:00Z")
            self.assertEqual(first.status, "ACQUIRED")
            self.assertIsNotNone(first.handle)
            second = manager.acquire(execution_id="run-two", candidate_identity={"sourceSha256": "b" * 64}, now="2026-09-13T12:00:30Z")
            self.assertEqual(second.status, "ALREADY_RUNNING")
            self.assertIsNone(second.handle)
            expired = manager.acquire(execution_id="run-two", candidate_identity={"sourceSha256": "b" * 64}, now="2026-09-13T12:02:00Z")
            self.assertEqual(expired.status, "ACQUIRED")
            self.assertEqual(manager.release(expired.handle, released_at="2026-09-13T12:02:01Z"), "RELEASED")
            released = manager.acquire(execution_id="run-three", candidate_identity={"sourceSha256": "c" * 64}, now="2026-09-13T12:02:02Z")
            self.assertEqual(released.status, "ACQUIRED")

    def test_two_simultaneous_executions_have_one_winner(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-automation-") as directory:
            manager = DistributedReleaseLease(LocalFilesystemObjectStore(directory), ttl_seconds=60)
            barrier = threading.Barrier(2)
            outcomes: list[LeaseOutcome] = []

            def acquire(execution_id: str) -> None:
                barrier.wait()
                outcomes.append(manager.acquire(execution_id=execution_id, candidate_identity={"sourceSha256": "a" * 64}, now="2026-09-13T12:00:00Z"))

            threads = [threading.Thread(target=acquire, args=(f"run-{index}",)) for index in range(2)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            self.assertEqual(sorted(outcome.status for outcome in outcomes), ["ACQUIRED", "ALREADY_RUNNING"])

    def test_summary_is_immutable_and_log_redaction_is_recursive(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-automation-") as directory:
            store = LocalFilesystemObjectStore(directory)
            key = write_execution_summary(store, "run-1", {"status": "NO_CHANGE", "nested": {"accessToken": "do-not-log"}})
            self.assertEqual(key, "control/executions/run-1.json")
            self.assertEqual(write_execution_summary(store, "run-1", {"status": "NO_CHANGE", "nested": {"accessToken": "do-not-log"}}), key)
            with self.assertRaisesRegex(RuntimeError, "conflicts"):
                write_execution_summary(store, "run-1", {"status": "PASS"})
        safe = redact_secrets({"token": "secret", "nested": [{"password": "pw", "value": 1}], "sourceSha256": "a" * 64})
        self.assertEqual(safe["token"], "[REDACTED]")
        self.assertEqual(safe["nested"][0]["password"], "[REDACTED]")
        self.assertEqual(safe["sourceSha256"], "a" * 64)

    def test_discovery_decision_does_not_claim_release_without_internal_bytes(self) -> None:
        self.assertIsNone(discovery_decision(candidate=self.candidate, source_release_date=None, source_sha256=None, current_release_date="2026-09-08", current_source_sha256="a" * 64))
        decision = discovery_decision(candidate=self.candidate, source_release_date="2026-09-08", source_sha256="a" * 64, current_release_date="2026-09-08", current_source_sha256="a" * 64)
        self.assertEqual(decision.disposition, "NO_CHANGE")

    def test_anomaly_gate_passes_normal_candidate_and_stops_catastrophic_changes(self) -> None:
        current = {"publishableRegions": 24, "stores": 2500, "productEvidenceRecords": 1_000_000, "offers": 10_000_000, "promotions": 1_000_000, "quarantineRows": 100_000}
        good = {**current, "requiredArtifactSetComplete": True, "manifestVerified": True, "searchPackVerified": True, "schemaDrift": "NO_DECLARED_DRIFT", "availability": "UNKNOWN", "stores": 2400}
        self.assertEqual(anomaly_gate(current=current, candidate=good)["status"], "PASS")
        bad = {**good, "offers": 100, "publishableRegions": 2, "availability": "IN_STOCK"}
        result = anomaly_gate(current=current, candidate=bad)
        self.assertEqual(result["status"], "REVIEW_REQUIRED")
        self.assertIn("REGIONAL_COVERAGE_COLLAPSE", result["reasons"])
        self.assertIn("OFFERS_CATASTROPHIC_DROP", result["reasons"])
        self.assertIn("AVAILABILITY_SEMANTICS_CHANGED", result["reasons"])

    def test_stage_verify_and_canary_never_writes_active_pointers(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-canary-stage-") as directory:
            workspace = Path(directory) / "workspace"
            release_id = "argentina-sepa-2026-09-09-fixture"
            payload = b"qualified-fixture-object"
            digest = hashlib.sha256(payload).hexdigest()
            object_path = workspace / "objects" / "sha256" / digest
            object_path.parent.mkdir(parents=True, exist_ok=True)
            object_path.write_bytes(payload)
            release_dir = workspace / "releases" / release_id
            release_dir.mkdir(parents=True, exist_ok=True)
            manifest = {
                "schemaVersion": "valuepilot-argentina-daily-release-v1",
                "completionState": "COMPLETE",
                "releaseId": release_id,
                "source": {"releaseDate": "2026-09-09"},
                "objects": [{"path": "fixture/object.bin", "sha256": digest, "bytes": len(payload)}],
            }
            raw = _canonical(manifest)
            (release_dir / "manifest.json").write_bytes(raw)
            (release_dir / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
            control = workspace / "control"
            control.mkdir(parents=True, exist_ok=True)
            (control / "release-note.json").write_bytes(b'{"schemaVersion":"test"}')
            (control / "canary.json").write_bytes(b'{"releaseId":"old-canary"}')
            stores = {participant: LocalFilesystemObjectStore(Path(directory) / participant) for participant in ACTIVATION_PARTICIPANTS}

            result = stage_verify_and_canary(workspace, stores, release_ids=[release_id], candidate_release_id=release_id)

            self.assertEqual(result["status"], "CANARY_READY")
            self.assertEqual(result["activePointerWrites"], 0)
            self.assertTrue(all(report["remoteWriteCount"] == 4 for report in result["staging"]["stores"].values()))
            self.assertTrue(all(report["status"] == "STAGED" for report in result["staging"]["stores"].values()))
            self.assertTrue(all(reports[0]["status"] == "PASS" for reports in result["verification"].values()))
            self.assertTrue(all(report["status"] == "CANARY" for report in result["canary"].values()))
            for store in stores.values():
                self.assertFalse(store.exists("control/active.json"))
                self.assertEqual(json.loads(store.get("control/canary.json"))["releaseId"], release_id)

    def test_stage_verify_and_canary_rejects_duplicate_release_ids_before_writes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-canary-input-") as directory:
            stores = self._activation_stores(directory)
            with self.assertRaisesRegex(RuntimeError, "release set is invalid"):
                stage_verify_and_canary(Path(directory), stores, release_ids=["a", "a"], candidate_release_id="a")

    @staticmethod
    def _activation_stores(directory: str, release_id: str = "old-release") -> dict[str, LocalFilesystemObjectStore]:
        stores: dict[str, LocalFilesystemObjectStore] = {}
        pointer = {"releaseId": release_id, "schemaVersion": "valuepilot-active-release-v1"}
        raw = json.dumps(pointer, sort_keys=True, separators=(",", ":")).encode("utf-8")
        for participant in ACTIVATION_PARTICIPANTS:
            store = LocalFilesystemObjectStore(Path(directory) / participant)
            store.put_immutable("control/active.json", raw)
            stores[participant] = store
        return stores

    def test_transactional_activation_requires_matching_regional_before_state(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-activation-") as directory:
            stores = self._activation_stores(directory)
            pointer = stores["sa"].head("control/active.json")
            stores["sa"].compare_and_swap(
                "control/active.json",
                json.dumps({"releaseId": "different", "schemaVersion": "valuepilot-active-release-v1"}, sort_keys=True, separators=(",", ":")).encode(),
                expected_etag=pointer.etag,
            )
            result = TransactionalActivationCoordinator(stores).activate(candidate_release_id="new-release", expected_current_release_id="old-release")
            self.assertEqual(result["status"], "STOPPED")
            self.assertEqual(result["reason"], "REGIONAL_POINTER_MISMATCH")
            self.assertEqual(result["pointerWrites"], 0)

    def test_us_smoke_failure_restores_us_and_leaves_other_regions_untouched(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-activation-" ) as directory:
            stores = self._activation_stores(directory)
            result = TransactionalActivationCoordinator(stores, smoke_checks={"us": lambda _: False}).activate(candidate_release_id="new-release", expected_current_release_id="old-release")
            self.assertEqual(result["status"], "STOPPED")
            self.assertEqual(result["reason"], "US_ACTIVATION_FAILED")
            self.assertEqual(result["rollback"]["status"], "ROLLED_BACK")
            self.assertEqual(json.loads(stores["us"].get("control/active.json"))["releaseId"], "old-release")
            self.assertEqual(json.loads(stores["sa"].get("control/active.json"))["releaseId"], "old-release")
            self.assertEqual(json.loads(stores["r2"].get("control/active.json"))["releaseId"], "old-release")

    def test_sa_failure_after_us_success_compensates_both_runtime_regions(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-activation-") as directory:
            stores = self._activation_stores(directory)
            result = TransactionalActivationCoordinator(stores, smoke_checks={"us": lambda _: True, "sa": lambda _: False}).activate(candidate_release_id="new-release", expected_current_release_id="old-release")
            self.assertEqual(result["status"], "STOPPED")
            self.assertEqual(result["reason"], "SA_ACTIVATION_FAILED")
            self.assertEqual(result["rollback"]["status"], "ROLLED_BACK")
            for participant in ACTIVATION_PARTICIPANTS:
                self.assertEqual(json.loads(stores[participant].get("control/active.json"))["releaseId"], "old-release")

    def test_r2_failure_after_both_regions_compensates_all_pointers(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-activation-") as directory:
            stores = self._activation_stores(directory)
            checks = {"us": lambda _: True, "sa": lambda _: True, "r2": lambda _: False}
            result = TransactionalActivationCoordinator(stores, smoke_checks=checks).activate(candidate_release_id="new-release", expected_current_release_id="old-release")
            self.assertEqual(result["status"], "STOPPED")
            self.assertEqual(result["reason"], "R2_ACTIVATION_FAILED")
            self.assertEqual(result["rollback"]["status"], "ROLLED_BACK")
            for participant in ACTIVATION_PARTICIPANTS:
                self.assertEqual(json.loads(stores[participant].get("control/active.json"))["releaseId"], "old-release")

    def test_transactional_activation_success_is_idempotent_when_retried_from_candidate(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-activation-") as directory:
            stores = self._activation_stores(directory)
            checks = {participant: lambda _: True for participant in ACTIVATION_PARTICIPANTS}
            first = TransactionalActivationCoordinator(stores, smoke_checks=checks).activate(candidate_release_id="new-release", expected_current_release_id="old-release")
            self.assertEqual(first["status"], "ACTIVE")
            self.assertEqual(first["pointerWrites"], 3)
            second = TransactionalActivationCoordinator(stores, smoke_checks=checks).activate(candidate_release_id="new-release", expected_current_release_id="new-release")
            self.assertEqual(second["status"], "ACTIVE")
            self.assertEqual(second["pointerWrites"], 0)


if __name__ == "__main__":
    unittest.main()

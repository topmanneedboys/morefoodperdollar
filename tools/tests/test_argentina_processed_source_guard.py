from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_acquisition_schedule_control import (
    AcquisitionScheduleError,
    candidate_release_id,
)
from tools.argentina_acquisition_schedule_control_v2 import (
    CHECK_PHASE_LATE_RECHECK,
    CHECK_PHASE_PRIMARY,
    finalize_download_observation,
    preflight_observation,
)
from tools.argentina_processed_source_guard import (
    MARKER_DISPOSITION,
    MARKER_KIND,
    POLICY_VERSION,
    SCHEMA_VERSION,
    SUPPRESSION_REASON,
    processed_source_key,
)
from tools.tests.test_argentina_acquisition_schedule_control import (
    SOURCE_SHA,
    _candidate,
    _discovery,
    _handoff,
    _publish,
    _put_source_archive,
)


def _canonical(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def _marker() -> dict[str, object]:
    release_id = candidate_release_id("2026-09-14", SOURCE_SHA)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "kind": MARKER_KIND,
        "disposition": MARKER_DISPOSITION,
        "candidatePersisted": False,
        "source": {
            "releaseDate": "2026-09-14",
            "sha256": SOURCE_SHA,
            "archiveKey": f"source-archive/sha256/{SOURCE_SHA}.zip",
            "manifestKey": f"source-archive/manifests/{SOURCE_SHA}.json",
            "candidateReleaseId": release_id,
        },
        "proof": {
            "cloudBuildId": "build-fixture",
            "workflowExecution": "workflow-fixture",
            "workerExecution": "worker-fixture",
            "workerStatus": "CANDIDATE_VERIFIED",
            "promotionStatus": "SHADOW_ONLY",
            "activationStatus": "NOT_EXECUTED",
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
        },
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
    }


def _put_marker(
    store: LocalFilesystemObjectStore,
    marker: dict[str, object],
) -> None:
    raw = _canonical(marker) + b"\n"
    store.put_immutable(
        processed_source_key(SOURCE_SHA),
        raw,
        sha256=hashlib.sha256(raw).hexdigest(),
    )


class ArgentinaProcessedSourceGuardTests(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(
            prefix="valuepilot-processed-source-"
        )
        self.addCleanup(self.directory.cleanup)

        self.store = LocalFilesystemObjectStore(
            Path(self.directory.name) / "archive"
        )
        self.candidate = _candidate()
        self.discovery = _discovery(self.candidate)

    def _preflight(self) -> dict[str, object]:
        return preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_PRIMARY,
        )

    def test_unmarked_verified_source_remains_buildable(self) -> None:
        preflight = self._preflight()
        _put_source_archive(self.store)

        result = finalize_download_observation(
            preflight=preflight,
            handoff=_handoff(self.candidate),
            publish=_publish(),
            store=self.store,
        )

        self.assertEqual(result["status"], "SOURCE_READY")
        self.assertEqual(
            result["source"]["candidateReleaseId"],
            candidate_release_id("2026-09-14", SOURCE_SHA),
        )

    def test_matching_historical_marker_suppresses_duplicate_build(self) -> None:
        preflight = self._preflight()
        _put_source_archive(self.store)
        _put_marker(self.store, _marker())

        result = finalize_download_observation(
            preflight=preflight,
            handoff=_handoff(self.candidate),
            publish=_publish(),
            store=self.store,
        )

        self.assertEqual(result["status"], "NO_BUILD_REQUIRED")
        self.assertEqual(result["reason"], SUPPRESSION_REASON)
        self.assertEqual(
            result["processedSourceMarkerKey"],
            processed_source_key(SOURCE_SHA),
        )
        self.assertFalse(result["processedSource"]["candidatePersisted"])
        self.assertEqual(result["activePointerWrites"], 0)
        self.assertEqual(result["canaryPointerWrites"], 0)

    def test_late_phase_does_not_redownload_processed_source(self) -> None:
        preflight = self._preflight()
        _put_source_archive(self.store)
        _put_marker(self.store, _marker())

        first = finalize_download_observation(
            preflight=preflight,
            handoff=_handoff(self.candidate),
            publish=_publish(),
            store=self.store,
        )

        self.assertEqual(first["reason"], SUPPRESSION_REASON)

        late = preflight_observation(
            discovery=self.discovery,
            candidate=self.candidate,
            store=self.store,
            check_phase=CHECK_PHASE_LATE_RECHECK,
        )

        self.assertEqual(late["status"], "NO_BUILD_REQUIRED")
        self.assertEqual(late["reason"], SUPPRESSION_REASON)

    def test_mismatched_source_identity_fails_closed(self) -> None:
        cases = {
            "releaseDate": "2026-09-13",
            "sha256": "1" * 64,
            "archiveKey": "source-archive/sha256/" + ("2" * 64) + ".zip",
            "manifestKey": "source-archive/manifests/" + ("3" * 64) + ".json",
            "candidateReleaseId": "argentina-sepa-invalid-routing-v1-search-v2",
        }

        for field, bad_value in cases.items():
            with self.subTest(field=field), tempfile.TemporaryDirectory(
                prefix="valuepilot-processed-source-mismatch-"
            ) as directory:
                store = LocalFilesystemObjectStore(Path(directory) / "archive")
                preflight = preflight_observation(
                    discovery=self.discovery,
                    candidate=self.candidate,
                    store=store,
                    check_phase=CHECK_PHASE_PRIMARY,
                )
                _put_source_archive(store)

                marker = deepcopy(_marker())
                marker["source"][field] = bad_value
                _put_marker(store, marker)

                with self.assertRaises(AcquisitionScheduleError) as caught:
                    finalize_download_observation(
                        preflight=preflight,
                        handoff=_handoff(self.candidate),
                        publish=_publish(),
                        store=store,
                    )

                self.assertEqual(
                    caught.exception.code,
                    "PROCESSED_SOURCE_MARKER_INVALID",
                )

    def test_marker_cannot_claim_candidate_or_pointer_activation(self) -> None:
        mutations = (
            ("candidatePersisted", True),
            ("activePointerWrites", 1),
            ("canaryPointerWrites", 1),
        )

        for field, bad_value in mutations:
            with self.subTest(field=field), tempfile.TemporaryDirectory(
                prefix="valuepilot-processed-source-pointer-"
            ) as directory:
                store = LocalFilesystemObjectStore(Path(directory) / "archive")
                preflight = preflight_observation(
                    discovery=self.discovery,
                    candidate=self.candidate,
                    store=store,
                    check_phase=CHECK_PHASE_PRIMARY,
                )
                _put_source_archive(store)

                marker = deepcopy(_marker())
                marker[field] = bad_value
                _put_marker(store, marker)

                with self.assertRaises(AcquisitionScheduleError) as caught:
                    finalize_download_observation(
                        preflight=preflight,
                        handoff=_handoff(self.candidate),
                        publish=_publish(),
                        store=store,
                    )

                self.assertEqual(
                    caught.exception.code,
                    "PROCESSED_SOURCE_MARKER_INVALID",
                )


if __name__ == "__main__":
    unittest.main()

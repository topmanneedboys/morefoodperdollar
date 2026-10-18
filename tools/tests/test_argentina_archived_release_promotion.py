from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from backend.object_store import LocalFilesystemObjectStore
from tools.argentina_archived_release_promotion import PromotionError, promote_archived_release, run_production_smoke
import tools.argentina_archived_release_promotion as promotion


def _canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


class ArchivedReleasePromotionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="valuepilot-promotion-")
        root = Path(self.temp.name)
        self.archive = LocalFilesystemObjectStore(root / "archive")
        self.us = LocalFilesystemObjectStore(root / "us")
        self.sa = LocalFilesystemObjectStore(root / "sa")
        self.candidate = "argentina-sepa-2026-10-01-aaaaaaaaaaaaaaaa-routing-v1-search-v2"
        self.previous = "argentina-sepa-2026-09-25-bbbbbbbbbbbbbbbb-routing-v1-search-v2"
        data = b"deterministic-pack"
        digest = hashlib.sha256(data).hexdigest()
        self.archive.put_immutable(f"objects/sha256/{digest}", data, sha256=digest)
        manifest = {
            "schemaVersion": "valuepilot-argentina-daily-release-v1",
            "completionState": "COMPLETE",
            "releaseId": self.candidate,
            "source": {"releaseDate": "2026-10-01", "sha256": "c" * 64},
            "objects": [{"path": "fixture.bin", "bytes": len(data), "sha256": digest}],
        }
        manifest_raw = _canonical(manifest)
        checksum = f"{hashlib.sha256(manifest_raw).hexdigest()}  manifest.json\n".encode("ascii")
        self.archive.put_immutable(f"releases/{self.candidate}/manifest.json", manifest_raw, sha256=hashlib.sha256(manifest_raw).hexdigest())
        self.archive.put_immutable(f"releases/{self.candidate}/manifest.sha256", checksum, sha256=hashlib.sha256(checksum).hexdigest())
        old = _canonical({
            "lastKnownGoodReleaseId": self.previous,
            "previousReleaseId": "argentina-sepa-2026-09-18-cccccccccccccccc-routing-v1-search-v2",
            "releaseId": self.previous,
            "schemaVersion": "valuepilot-active-release-v1",
        })
        self.us.put_immutable("control/active.json", old)
        self.sa.put_immutable("control/active.json", old)
        previous_manifest = _canonical({'releaseId': self.previous, 'source': {'releaseDate': '2026-09-25'}})
        for store in (self.us, self.sa):
            store.put_immutable(f'releases/{self.previous}/manifest.json', previous_manifest)
            store.put_immutable(f'releases/{self.previous}/manifest.sha256',
                                hashlib.sha256(previous_manifest).hexdigest().encode('ascii') + b'  manifest.json\n')

    def tearDown(self):
        self.temp.cleanup()

    def test_promotes_both_regions_and_is_idempotent(self):
        smoke_calls = []

        def smoke(region):
            smoke_calls.append(region)
            return {"region": region, "status": "PASS"}

        result = promote_archived_release(
            self.archive,
            {"us": self.us, "sa": self.sa},
            release_id=self.candidate,
            expected_current_release_id=self.previous,
            expected_release_date="2026-10-01",
            expected_source_sha256="c" * 64,
            smoke=smoke,
            smoke_targets={"us": "us", "sa": "sa"},
        )
        self.assertEqual(result["status"], "ACTIVE")
        self.assertEqual(result["pointerWrites"], 2)
        self.assertEqual(json.loads(self.us.get("control/active.json"))["releaseId"], self.candidate)
        self.assertEqual(json.loads(self.sa.get("control/active.json"))["releaseId"], self.candidate)

        second = promote_archived_release(
            self.archive,
            {"us": self.us, "sa": self.sa},
            release_id=self.candidate,
            expected_current_release_id=self.previous,
            expected_release_date="2026-10-01",
            expected_source_sha256="c" * 64,
        )
        self.assertEqual(second["status"], "ALREADY_ACTIVE")
        self.assertEqual(smoke_calls, ["us", "sa"])

    def test_smoke_failure_restores_exact_previous_pointer(self):
        old_us = self.us.get("control/active.json")
        old_sa = self.sa.get("control/active.json")

        def smoke(_region):
            raise PromotionError("smoke failed")

        with self.assertRaises(PromotionError):
            promote_archived_release(
                self.archive,
                {"us": self.us, "sa": self.sa},
                release_id=self.candidate,
                expected_current_release_id=self.previous,
                expected_release_date="2026-10-01",
                expected_source_sha256="c" * 64,
                smoke=smoke,
                smoke_targets={"us": "us", "sa": "sa"},
            )
        self.assertEqual(self.us.get("control/active.json"), old_us)
        self.assertEqual(self.sa.get("control/active.json"), old_sa)

    def test_failed_activation_restores_release_scoped_service_authorization(self):
        old = self.us.get('control/active.json')
        with patch.object(promotion, '_update_service') as update:
            with self.assertRaises(PromotionError):
                promote_archived_release(
                    self.archive, {'us':self.us, 'sa':self.sa}, release_id=self.candidate,
                    expected_current_release_id=self.previous, expected_release_date='2026-10-01',
                    expected_source_sha256='c'*64,
                    service_updates=(('backend-us:us-east4','project','2026-10-01'),
                                     ('backend-sa:southamerica-east1','project','2026-10-01')),
                    smoke=lambda _: (_ for _ in ()).throw(PromotionError('failed smoke')),
                    smoke_targets={'us':'us'},
                )
        self.assertEqual(self.us.get('control/active.json'), old)
        self.assertEqual(self.sa.get('control/active.json'), old)
        self.assertEqual(len(update.call_args_list), 4)
        for call in update.call_args_list[-2:]:
            self.assertEqual(call.kwargs['release_id'], self.previous)
            self.assertEqual(call.kwargs['release_date'], '2026-09-25')

    def test_pointer_mismatch_fails_before_any_write(self):
        unexpected = _canonical({
            "lastKnownGoodReleaseId": "unexpected",
            "previousReleaseId": None,
            "releaseId": "unexpected",
            "schemaVersion": "valuepilot-active-release-v1",
        })
        self.sa.compare_and_swap("control/active.json", unexpected, expected_etag=self.sa.head("control/active.json").etag)
        before = self.us.get("control/active.json")
        with self.assertRaises(PromotionError):
            promote_archived_release(
                self.archive,
                {"us": self.us, "sa": self.sa},
                release_id=self.candidate,
                expected_current_release_id=self.previous,
                expected_release_date="2026-10-01",
                expected_source_sha256="c" * 64,
            )
        self.assertEqual(self.us.get("control/active.json"), before)

    def test_smoke_requires_explicit_networking_authorization(self):
        def fake_json(url, **kwargs):
            if url.endswith("/v1/status"):
                return {"releaseId": self.candidate, "freshness": "FRESH", "androidNetworkingAuthorized": False}
            if url.endswith("/v1/search"):
                return {"releaseId": self.candidate, "freshness": "FRESH", "matches": [], "pagination": {}}
            return {"releaseId": self.candidate, "freshness": "FRESH"}

        with patch.object(promotion, "_http_json", side_effect=fake_json):
            with self.assertRaisesRegex(PromotionError, "Android networking authorization"):
                run_production_smoke("https://fixture.example", release_id=self.candidate, token="token")


    def test_service_update_retries_a_transient_control_plane_failure(self):
        invocations = []

        def fake_run(args, **kwargs):
            invocations.append(list(args))
            if len(invocations) < 3:
                return SimpleNamespace(returncode=1, stdout="", stderr="ERROR: conflict")
            return SimpleNamespace(returncode=0, stdout="", stderr="")

        with patch.object(promotion.subprocess, "run", side_effect=fake_run), patch.object(promotion.time, "sleep"):
            promotion._update_service(
                "backend-sa:southamerica-east1",
                project="project",
                release_id=self.candidate,
                release_date="2026-10-01",
            )

        self.assertEqual(len(invocations), 4)  # three bounded retries, then one traffic update
        self.assertIn("services", invocations[0])
        self.assertIn("update-traffic", invocations[-1])

    def test_service_update_surfaces_the_bounded_gcloud_diagnostic(self):
        invocations = []

        def fake_run(args, **kwargs):
            invocations.append(list(args))
            return SimpleNamespace(
                returncode=1,
                stdout="",
                stderr="ERROR: Permission 'artifactregistry.repositories.downloadArtifacts' denied",
            )

        with patch.object(promotion.subprocess, "run", side_effect=fake_run), patch.object(promotion.time, "sleep"):
            with self.assertRaisesRegex(PromotionError, "downloadArtifacts"):
                promotion._update_service(
                    "backend-sa:southamerica-east1",
                    project="project",
                    release_id=self.candidate,
                    release_date="2026-10-01",
                )

        self.assertEqual(len(invocations), promotion._SERVICE_UPDATE_ATTEMPTS)


    def test_rollback_failure_preserves_the_original_cause(self):
        calls = {"count": 0}

        def update(service, *, project, release_id, release_date):
            calls["count"] += 1
            if calls["count"] > 2:  # the two rollback attempts fail
                raise PromotionError("rollback denied")

        with patch.object(promotion, "_update_service", side_effect=update):
            with self.assertRaisesRegex(PromotionError, "original failure: smoke failed"):
                promote_archived_release(
                    self.archive,
                    {"us": self.us, "sa": self.sa},
                    release_id=self.candidate,
                    expected_current_release_id=self.previous,
                    expected_release_date="2026-10-01",
                    expected_source_sha256="c" * 64,
                    service_updates=(
                        ("backend-us:us-east4", "project", "2026-10-01"),
                        ("backend-sa:southamerica-east1", "project", "2026-10-01"),
                    ),
                    smoke=lambda _: (_ for _ in ()).throw(PromotionError("smoke failed")),
                    smoke_targets={"us": "us"},
                )


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.argentina_release_automation import (
    DATA_CANARY_LOCATIONS,
    DATA_CANARY_POLICY_VERSION,
    DATA_CANARY_REGIONS,
    DATA_CANARY_REQUESTS,
    DATA_CANARY_SCHEMA_VERSION,
    DATA_CANARY_SHOPPING_LINE_COUNTS,
)
from tools.argentina_release_promoter import (
    MAX_SMOKE_BYTES,
    PromotionAdapterError,
    _main,
    build_promotion_stores,
    build_smoke_checks,
    https_smoke_check,
    read_bounded_json,
)


def _canonical(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def _promotion_workspace(root: Path, release_id: str = "new-release") -> Path:
    workspace = root / "workspace"
    payload = b"promoter-fixture"
    digest = hashlib.sha256(payload).hexdigest()
    object_path = workspace / "objects" / "sha256" / digest
    object_path.parent.mkdir(parents=True, exist_ok=True)
    object_path.write_bytes(payload)
    release = workspace / "releases" / release_id
    release.mkdir(parents=True, exist_ok=True)
    manifest = {
        "schemaVersion": "valuepilot-argentina-daily-release-v1",
        "completionState": "COMPLETE",
        "releaseId": release_id,
        "source": {"releaseDate": "2026-09-09"},
        "objects": [{"path": "fixture/object.bin", "sha256": digest, "bytes": len(payload)}],
    }
    raw = _canonical(manifest)
    (release / "manifest.json").write_bytes(raw)
    (release / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
    return workspace


def _canary_evidence(candidate: str) -> dict[str, object]:
    def probe() -> dict[str, object]:
        return {
            "httpStatus": 200,
            "releaseId": candidate,
            "freshness": "FRESH",
            "availability": "UNKNOWN",
            "corpusScanned": 0,
            "partitionIdentity": "promoter-fixture-partition",
            "partitionIdentityVerified": True,
            "oom": False,
            "restartCount": 0,
            "runtimeR2Reads": 0,
            "runtimeR2Writes": 0,
            "malformedPrices": 0,
        }

    return {
        "schemaVersion": DATA_CANARY_SCHEMA_VERSION,
        "policyVersion": DATA_CANARY_POLICY_VERSION,
        "candidateReleaseId": candidate,
        "regions": {
            region: {
                "readyz": probe(),
                "status": probe(),
                "requests": {name: probe() for name in DATA_CANARY_REQUESTS},
                "shopping": {str(count): probe() for count in DATA_CANARY_SHOPPING_LINE_COUNTS},
                "locations": {name: probe() for name in DATA_CANARY_LOCATIONS},
                "timings": {"cold10LineCount": 10, "cold10LineMs": 1, "warmSmokeP95Ms": 1, "warmSmokeSamples": 1},
            }
            for region in DATA_CANARY_REGIONS
        },
    }


class _Response:
    def __init__(self, status: int, body: bytes):
        self.status = status
        self.body = body
        self.closed = False

    def getcode(self) -> int:
        return self.status

    def read(self, limit: int = -1) -> bytes:
        self.read_limit = limit
        return self.body

    def close(self) -> None:
        self.closed = True


class ArgentinaReleasePromoterTests(unittest.TestCase):
    def test_https_smoke_requires_matching_bounded_json_and_sends_candidate_header(self) -> None:
        seen: dict[str, object] = {}
        response = _Response(200, b'{"releaseId":"candidate","status":"PASS"}')

        def opener(request, timeout):
            seen["url"] = request.full_url
            seen["release"] = request.get_header("X-valuepilot-expected-release")
            seen["timeout"] = timeout
            return response

        result = https_smoke_check("https://us.example.test/readyz", opener=opener)("candidate")
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(seen["release"], "candidate")
        self.assertEqual(seen["timeout"], 30)
        self.assertTrue(response.closed)

    def test_https_smoke_fails_closed_for_mismatch_or_oversized_body(self) -> None:
        mismatch = https_smoke_check("https://us.example.test/readyz", opener=lambda *_, **__: _Response(200, b'{"releaseId":"other","status":"PASS"}'))("candidate")
        self.assertEqual(mismatch["reason"], "RELEASE_MISMATCH")
        oversized = https_smoke_check("https://us.example.test/readyz", opener=lambda *_, **__: _Response(200, b"x" * (MAX_SMOKE_BYTES + 1)))("candidate")
        self.assertEqual(oversized["status"], "FAILED")
        self.assertEqual(oversized["reason"], "SMOKE_RESPONSE_INVALID")

    def test_https_smoke_rejects_non_https_credentials_and_private_literal(self) -> None:
        for url in ("http://us.example.test/readyz", "https://user:pass@us.example.test/readyz", "https://127.0.0.1/readyz"):
            with self.assertRaises(PromotionAdapterError):
                https_smoke_check(url)

    def test_store_builder_requires_explicit_three_destinations(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-promoter-store-") as directory:
            stores = build_promotion_stores(local_root=Path(directory) / "stores")
            self.assertEqual(set(stores), {"us", "sa", "r2"})
        with self.assertRaisesRegex(PromotionAdapterError, "all promotion destinations"):
            build_promotion_stores(us_bucket="us", sa_bucket="sa")

    def test_cli_shadow_is_write_free_and_stage_canary_uses_local_mirror(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-promoter-cli-") as directory:
            root = Path(directory)
            workspace = _promotion_workspace(root)
            self.assertEqual(_main([str(workspace), "--release-id", "new-release", "--candidate-release-id", "new-release"]), 0)
            evidence = root / "canary.json"
            evidence.write_bytes(_canonical(_canary_evidence("new-release")))
            self.assertEqual(
                _main(
                    [
                        str(workspace),
                        "--release-id",
                        "new-release",
                        "--candidate-release-id",
                        "new-release",
                        "--mode",
                        "STAGE_CANARY",
                        "--data-canary",
                        str(evidence),
                        "--store-root",
                        str(root / "stores"),
                    ]
                ),
                0,
            )
            self.assertTrue((root / "stores" / "us" / "control" / "canary.json").is_file())

    def test_bounded_json_rejects_non_object_and_oversized_input(self) -> None:
        with tempfile.TemporaryDirectory(prefix="valuepilot-promoter-json-") as directory:
            path = Path(directory) / "value.json"
            path.write_bytes(b"[]")
            with self.assertRaises(PromotionAdapterError):
                read_bounded_json(path, label="evidence")
            path.write_bytes(b"x" * (128 * 1024 + 1))
            with self.assertRaises(PromotionAdapterError):
                read_bounded_json(path, label="evidence")

    def test_build_smoke_checks_requires_all_participants(self) -> None:
        with self.assertRaises(PromotionAdapterError):
            build_smoke_checks(us_url="https://us.example.test", sa_url=None, r2_url="https://r2.example.test")


if __name__ == "__main__":
    unittest.main()

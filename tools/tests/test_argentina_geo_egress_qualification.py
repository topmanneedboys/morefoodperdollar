from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from tools.argentina_release_acquisition import AcquisitionCandidate, AcquisitionError, DiscoveryResult
from tools.qualify_argentina_geo_egress import (
    GeoEgressQualificationError,
    SCHEMA_VERSION,
    compare_reports,
    qualify_route,
)


OBSERVED_AT = "2026-09-13T22:00:00Z"
SOURCE_SHA = "a" * 64
DISCOVERY_SHA = "b" * 64


def candidate() -> AcquisitionCandidate:
    return AcquisitionCandidate(
        provider="ARGENTINA_SEPA_PRECIOS_CLAROS",
        dataset_id="0f495d54-4a5d-5fa3-a323-f6e97ce434cf",
        resource_id="fixture-resource",
        resource_name="martes",
        official_url="https://datos.produccion.gob.ar/fixture.zip",
        observed_size=12,
        last_modified="2026-09-13T20:00:00",
        observed_at=OBSERVED_AT,
        expected_source_host="datos.produccion.gob.ar",
        discovery_response_sha256=DISCOVERY_SHA,
        release_date_hint="2026-09-13",
        description=None,
        format="ZIP",
    )


def discovery(value: AcquisitionCandidate) -> DiscoveryResult:
    return DiscoveryResult(
        endpoint="https://datos.gob.ar/api/3/action/package_show?id=precios-claros-base-sepa",
        response_sha256=DISCOVERY_SHA,
        observed_at=OBSERVED_AT,
        dataset_id=value.dataset_id,
        dataset_name="precios-claros-base-sepa",
        dataset_title="Precios Claros - Base SEPA",
        license_title="Creative Commons Attribution 4.0",
        candidates=(value,),
    )


class ArgentinaGeoEgressQualificationTests(unittest.TestCase):
    def test_success_reuses_official_download_identity_and_writes_no_control_state(self):
        value = candidate()

        def fake_discover(**_kwargs):
            return discovery(value)

        def fake_download(_candidate, target):
            Path(target).write_bytes(b"fixture-data")
            return SimpleNamespace(
                path=Path(target),
                bytes=12,
                sha256=SOURCE_SHA,
                final_url="https://datos.produccion.gob.ar/fixture.zip",
                attempts=1,
            )

        def fake_inspect(_path, *, fixture=False):
            self.assertFalse(fixture)
            return SimpleNamespace(
                official=True,
                bytes=12,
                sha256=SOURCE_SHA,
                source_kind="OFFICIAL_SEPA_ZIP",
                release_date="2026-09-13",
                release_evidence="outer ZIP internal dated package paths",
                nested_packages=({"name": "provider.zip"},),
            )

        result = qualify_route(
            observed_at=OBSERVED_AT,
            egress_label="argentina-vpn",
            discover=fake_discover,
            select=lambda candidates: candidates[0],
            download=fake_download,
            inspect=fake_inspect,
        )

        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["stage"], "VERIFIED_OFFICIAL_SOURCE")
        self.assertEqual(result["download"]["finalHost"], "datos.produccion.gob.ar")
        self.assertEqual(result["source"]["sha256"], SOURCE_SHA)
        self.assertEqual(result["source"]["releaseDate"], "2026-09-13")
        self.assertEqual(result["activePointerWrites"], 0)
        self.assertEqual(result["archiveWrites"], 0)
        self.assertFalse(result["buildStarted"])
        self.assertFalse(result["productionStateChanged"])
        self.assertEqual(result["egressLabelAuthority"], "OPERATOR_SUPPLIED_NOT_GEOLOCATED")

    def test_http_403_is_recorded_without_build_or_pointer_writes(self):
        value = candidate()

        result = qualify_route(
            observed_at=OBSERVED_AT,
            egress_label="canada-direct",
            discover=lambda **_kwargs: discovery(value),
            select=lambda candidates: candidates[0],
            download=lambda *_args, **_kwargs: (_ for _ in ()).throw(
                AcquisitionError("official source returned HTTP 403", code="HTTP_ERROR")
            ),
            inspect=lambda *_args, **_kwargs: self.fail("inspect must not run after failed download"),
        )

        self.assertEqual(result["status"], "FAILED")
        self.assertEqual(result["stage"], "DOWNLOAD")
        self.assertEqual(result["error"], "HTTP_ERROR")
        self.assertIn("403", result["message"])
        self.assertEqual(result["activePointerWrites"], 0)
        self.assertEqual(result["archiveWrites"], 0)
        self.assertFalse(result["buildStarted"])

    def test_mismatched_inspection_identity_fails_closed(self):
        value = candidate()

        def fake_download(_candidate, target):
            Path(target).write_bytes(b"fixture-data")
            return SimpleNamespace(
                path=Path(target),
                bytes=12,
                sha256=SOURCE_SHA,
                final_url="https://datos.produccion.gob.ar/fixture.zip",
                attempts=1,
            )

        result = qualify_route(
            observed_at=OBSERVED_AT,
            egress_label="argentina-vpn",
            discover=lambda **_kwargs: discovery(value),
            select=lambda candidates: candidates[0],
            download=fake_download,
            inspect=lambda *_args, **_kwargs: SimpleNamespace(
                official=True,
                bytes=12,
                sha256="c" * 64,
                source_kind="OFFICIAL_SEPA_ZIP",
                release_date="2026-09-13",
                release_evidence="fixture",
                nested_packages=(),
            ),
        )

        self.assertEqual(result["status"], "FAILED")
        self.assertEqual(result["error"], "SOURCE_IDENTITY_MISMATCH")
        self.assertEqual(result["activePointerWrites"], 0)

    def test_compare_proves_only_route_dependent_success_not_country_geolocation(self):
        baseline = {
            "schemaVersion": SCHEMA_VERSION,
            "egressLabel": "canada-direct",
            "status": "FAILED",
            "error": "HTTP_ERROR",
            "message": "official source returned HTTP 403",
        }
        candidate_report = {
            "schemaVersion": SCHEMA_VERSION,
            "egressLabel": "argentina-vpn",
            "status": "PASS",
            "source": {"sha256": SOURCE_SHA, "releaseDate": "2026-09-13", "bytes": 12},
        }

        result = compare_reports(baseline, candidate_report)

        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["conclusion"], "CANDIDATE_ROUTE_SUCCEEDS_WHERE_BASELINE_403")
        self.assertTrue(result["egressLabelsAreOperatorSupplied"])
        self.assertFalse(result["countryGeolocationIndependentlyProven"])
        self.assertEqual(result["activePointerWrites"], 0)

    def test_compare_both_pass_requires_same_source_only_for_identity_claim(self):
        baseline = {
            "schemaVersion": SCHEMA_VERSION,
            "egressLabel": "route-a",
            "status": "PASS",
            "source": {"sha256": SOURCE_SHA, "releaseDate": "2026-09-13", "bytes": 12},
        }
        candidate_report = {
            "schemaVersion": SCHEMA_VERSION,
            "egressLabel": "route-b",
            "status": "PASS",
            "source": {"sha256": SOURCE_SHA, "releaseDate": "2026-09-13", "bytes": 12},
        }

        result = compare_reports(baseline, candidate_report)

        self.assertEqual(result["conclusion"], "BOTH_ROUTES_SUCCEED")
        self.assertTrue(result["sameVerifiedSourceWhenBothPass"])

    def test_invalid_operator_label_is_rejected(self):
        with self.assertRaises(GeoEgressQualificationError):
            qualify_route(
                observed_at=OBSERVED_AT,
                egress_label="contains a secret or spaces",
                discover=lambda **_kwargs: self.fail("network must not run for invalid label"),
            )


if __name__ == "__main__":
    unittest.main()

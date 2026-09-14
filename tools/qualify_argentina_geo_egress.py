"""Qualify route-dependent access to the official Argentina SEPA source.

This tool intentionally reuses ValuePilot's existing official metadata discovery,
HTTPS host validation, bounded download, SHA-256 calculation, and official ZIP
inspection.  It does not configure a VPN/proxy, does not use alternate mirrors,
and never writes release/archive/control-plane state.

Typical operator sequence:

1. Run once using the normal network route and save the report.
2. Run again after the machine is already routed through an approved Argentina
   egress path and save the report.
3. Compare the two reports.

The egress label is operator-supplied context only.  This tool never claims to
geolocate an IP address or independently prove a country from that label.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import urllib.parse
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Callable, Mapping, Sequence

try:
    from tools.argentina_daily_release import DailyReleaseError, inspect_source
    from tools.argentina_release_acquisition import (
        AcquisitionCandidate,
        AcquisitionError,
        CATALOG_ENDPOINT,
        DiscoveryResult,
        download_candidate,
        discover_metadata,
        select_latest_candidate,
    )
except ModuleNotFoundError:  # direct invocation from tools/
    from argentina_daily_release import DailyReleaseError, inspect_source
    from argentina_release_acquisition import (
        AcquisitionCandidate,
        AcquisitionError,
        CATALOG_ENDPOINT,
        DiscoveryResult,
        download_candidate,
        discover_metadata,
        select_latest_candidate,
    )


SCHEMA_VERSION = "valuepilot-argentina-geo-egress-qualification-v1"
POLICY_VERSION = "valuepilot-argentina-geo-egress-policy-v1"
MAX_REPORT_BYTES = 128 * 1024
_LABEL = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")


class GeoEgressQualificationError(RuntimeError):
    def __init__(self, message: str, *, code: str = "QUALIFICATION_FAILED") -> None:
        super().__init__(message)
        self.code = code


def _canonical_bytes(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _write_json_atomic(path: Path, value: Mapping[str, Any]) -> None:
    raw = _canonical_bytes(value) + b"\n"
    if len(raw) > MAX_REPORT_BYTES:
        raise GeoEgressQualificationError("qualification report exceeds bound", code="REPORT_TOO_LARGE")
    path = Path(path).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _read_report(path: Path | str) -> dict[str, Any]:
    source = Path(path)
    try:
        raw = source.read_bytes()
    except OSError as exc:
        raise GeoEgressQualificationError("qualification report is unavailable", code="REPORT_UNAVAILABLE") from exc
    if len(raw) > MAX_REPORT_BYTES:
        raise GeoEgressQualificationError("qualification report exceeds bound", code="REPORT_TOO_LARGE")
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GeoEgressQualificationError("qualification report is invalid", code="INVALID_REPORT") from exc
    if not isinstance(value, dict) or value.get("schemaVersion") != SCHEMA_VERSION:
        raise GeoEgressQualificationError("qualification report schema is unknown", code="INVALID_REPORT")
    return value


def _egress_label(value: str) -> str:
    text = str(value or "").strip()
    if _LABEL.fullmatch(text) is None:
        raise GeoEgressQualificationError("egress label is invalid", code="INVALID_CONFIGURATION")
    return text


def _base_report(*, observed_at: str, egress_label: str) -> dict[str, Any]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "observedAt": observed_at,
        "egressLabel": _egress_label(egress_label),
        "egressLabelAuthority": "OPERATOR_SUPPLIED_NOT_GEOLOCATED",
        "status": "FAILED",
        "stage": "DISCOVERY",
        "officialHost": "datos.produccion.gob.ar",
        "activePointerWrites": 0,
        "archiveWrites": 0,
        "buildStarted": False,
        "productionStateChanged": False,
    }


def qualify_route(
    *,
    observed_at: str,
    egress_label: str,
    metadata_endpoint: str = CATALOG_ENDPOINT,
    discover: Callable[..., DiscoveryResult] = discover_metadata,
    select: Callable[[Sequence[AcquisitionCandidate]], AcquisitionCandidate] = select_latest_candidate,
    download: Callable[..., Any] = download_candidate,
    inspect: Callable[..., Any] = inspect_source,
) -> dict[str, Any]:
    """Run one read-only acquisition qualification over the machine's current route."""

    report = _base_report(observed_at=observed_at, egress_label=egress_label)
    with tempfile.TemporaryDirectory(prefix="valuepilot-argentina-egress-") as temporary:
        target = Path(temporary) / "official-sepa.zip"
        try:
            discovery = discover(observed_at=observed_at, endpoint=metadata_endpoint)
            candidate = select(discovery.candidates)
            report.update(
                {
                    "stage": "DOWNLOAD",
                    "catalogEndpoint": discovery.endpoint,
                    "catalogResponseSha256": discovery.response_sha256,
                    "datasetId": discovery.dataset_id,
                    "candidate": {
                        "resourceId": candidate.resource_id,
                        "resourceName": candidate.resource_name,
                        "releaseDateHint": candidate.release_date_hint,
                        "observedSize": candidate.observed_size,
                        "expectedSourceHost": candidate.expected_source_host,
                    },
                }
            )
            downloaded = download(candidate, target)
            final_host = (urllib.parse.urlsplit(downloaded.final_url).hostname or "").casefold().rstrip(".")
            if final_host != candidate.expected_source_host.casefold().rstrip("."):
                raise GeoEgressQualificationError("download final host differs from official host", code="OFFICIAL_HOST_MISMATCH")
            report.update(
                {
                    "stage": "ZIP_INSPECTION",
                    "download": {
                        "bytes": downloaded.bytes,
                        "sha256": downloaded.sha256,
                        "attempts": downloaded.attempts,
                        "finalHost": final_host,
                    },
                }
            )
            source = inspect(downloaded.path, fixture=False)
            if source.official is not True:
                raise GeoEgressQualificationError("downloaded source is not classified as official", code="SOURCE_NOT_OFFICIAL")
            if source.bytes != downloaded.bytes or source.sha256 != downloaded.sha256:
                raise GeoEgressQualificationError("download and ZIP inspection identities differ", code="SOURCE_IDENTITY_MISMATCH")
            report.update(
                {
                    "status": "PASS",
                    "stage": "VERIFIED_OFFICIAL_SOURCE",
                    "source": {
                        "kind": source.source_kind,
                        "bytes": source.bytes,
                        "sha256": source.sha256,
                        "releaseDate": source.release_date,
                        "releaseEvidence": source.release_evidence,
                        "official": source.official,
                        "nestedPackageCount": len(source.nested_packages),
                    },
                }
            )
            return report
        except (AcquisitionError, DailyReleaseError, GeoEgressQualificationError) as exc:
            report.update(
                {
                    "status": "FAILED",
                    "error": getattr(exc, "code", "QUALIFICATION_FAILED"),
                    "message": str(exc),
                }
            )
            return report


def compare_reports(baseline: Mapping[str, Any], candidate: Mapping[str, Any]) -> dict[str, Any]:
    """Compare two bounded reports without trusting their operator route labels as geolocation."""

    for value in (baseline, candidate):
        if value.get("schemaVersion") != SCHEMA_VERSION:
            raise GeoEgressQualificationError("qualification report schema is unknown", code="INVALID_REPORT")

    baseline_pass = baseline.get("status") == "PASS"
    candidate_pass = candidate.get("status") == "PASS"
    baseline_403 = (
        not baseline_pass
        and baseline.get("error") == "HTTP_ERROR"
        and "403" in str(baseline.get("message", ""))
    )

    if baseline_403 and candidate_pass:
        conclusion = "CANDIDATE_ROUTE_SUCCEEDS_WHERE_BASELINE_403"
    elif baseline_pass and candidate_pass:
        conclusion = "BOTH_ROUTES_SUCCEED"
    elif baseline_pass and not candidate_pass:
        conclusion = "CANDIDATE_ROUTE_REGRESSION"
    elif not baseline_pass and not candidate_pass:
        conclusion = "ACCESS_NOT_PROVEN"
    else:
        conclusion = "INCONCLUSIVE"

    same_source = False
    if baseline_pass and candidate_pass:
        baseline_source = baseline.get("source") if isinstance(baseline.get("source"), Mapping) else {}
        candidate_source = candidate.get("source") if isinstance(candidate.get("source"), Mapping) else {}
        same_source = (
            baseline_source.get("sha256") == candidate_source.get("sha256")
            and baseline_source.get("releaseDate") == candidate_source.get("releaseDate")
            and baseline_source.get("bytes") == candidate_source.get("bytes")
        )

    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "status": "PASS" if conclusion in {"CANDIDATE_ROUTE_SUCCEEDS_WHERE_BASELINE_403", "BOTH_ROUTES_SUCCEED"} else "NOT_PROVEN",
        "conclusion": conclusion,
        "baseline": {
            "egressLabel": baseline.get("egressLabel"),
            "status": baseline.get("status"),
            "error": baseline.get("error"),
        },
        "candidate": {
            "egressLabel": candidate.get("egressLabel"),
            "status": candidate.get("status"),
            "error": candidate.get("error"),
        },
        "sameVerifiedSourceWhenBothPass": same_source,
        "egressLabelsAreOperatorSupplied": True,
        "countryGeolocationIndependentlyProven": False,
        "activePointerWrites": 0,
        "archiveWrites": 0,
        "buildStarted": False,
        "productionStateChanged": False,
    }


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    run = subparsers.add_parser("run", help="qualify the current machine network route")
    run.add_argument("--observed-at", required=True)
    run.add_argument("--egress-label", required=True)
    run.add_argument("--metadata-endpoint", default=CATALOG_ENDPOINT)
    run.add_argument("--output", type=Path, required=True)

    compare = subparsers.add_parser("compare", help="compare two qualification reports")
    compare.add_argument("--baseline", type=Path, required=True)
    compare.add_argument("--candidate", type=Path, required=True)
    compare.add_argument("--output", type=Path, required=True)

    args = parser.parse_args(argv)
    try:
        if args.command == "run":
            value = qualify_route(
                observed_at=args.observed_at,
                egress_label=args.egress_label,
                metadata_endpoint=args.metadata_endpoint,
            )
            _write_json_atomic(args.output, value)
            print(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
            return 0 if value.get("status") == "PASS" else 2

        baseline = _read_report(args.baseline)
        candidate = _read_report(args.candidate)
        value = compare_reports(baseline, candidate)
        _write_json_atomic(args.output, value)
        print(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0 if value.get("status") == "PASS" else 2
    except GeoEgressQualificationError as exc:
        print(json.dumps({"error": exc.code, "message": str(exc)}, ensure_ascii=False, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = [
    "GeoEgressQualificationError",
    "POLICY_VERSION",
    "SCHEMA_VERSION",
    "compare_reports",
    "qualify_route",
]

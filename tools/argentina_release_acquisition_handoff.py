"""Acquire one official SEPA source and hand it to the existing private archive.

This bounded runner is intended to execute only on a machine that already has
an approved Argentina egress route.  It does not configure a VPN, build a
release, stage a canary, or change any production pointer.  Its only durable
write is the existing content-addressed private source archive, and only after
the official download passes the existing host/hash/ZIP/date controls and the
source is eligible to move forward.
"""

from __future__ import annotations

import argparse
import json
import os
import tempfile
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

try:
    from backend.object_store import LocalFilesystemObjectStore, ObjectStore
    from tools.argentina_daily_release import DailyReleaseError, inspect_source
    from tools.argentina_release_acquisition import (
        AcquisitionCandidate,
        AcquisitionError,
        CATALOG_ENDPOINT,
        DiscoveryResult,
        PrivateSourceArchive,
        canonical_bytes,
        classify_source,
        discover_metadata,
        download_candidate,
        select_latest_candidate,
    )
except ModuleNotFoundError:  # direct invocation from tools/
    from backend.object_store import LocalFilesystemObjectStore, ObjectStore
    from argentina_daily_release import DailyReleaseError, inspect_source
    from argentina_release_acquisition import AcquisitionCandidate, AcquisitionError, CATALOG_ENDPOINT, DiscoveryResult, PrivateSourceArchive, canonical_bytes, classify_source, discover_metadata, download_candidate, select_latest_candidate


SCHEMA_VERSION = "valuepilot-argentina-acquisition-handoff-v1"
POLICY_VERSION = "valuepilot-argentina-acquisition-handoff-policy-v1"
MAX_REPORT_BYTES = 128 * 1024


class AcquisitionHandoffError(RuntimeError):
    def __init__(self, message: str, *, code: str = "HANDOFF_FAILED") -> None:
        super().__init__(message)
        self.code = code


def _write_json_atomic(path: Path, value: Mapping[str, Any]) -> None:
    raw = canonical_bytes(dict(value)) + b"\n"
    if len(raw) > MAX_REPORT_BYTES:
        raise AcquisitionHandoffError("handoff report exceeds bound", code="REPORT_TOO_LARGE")
    target = Path(path).resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".tmp", dir=str(target.parent))
    temporary = Path(name)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def _base_report(observed_at: str) -> dict[str, Any]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "observedAt": observed_at,
        "status": "FAILED",
        "stage": "DISCOVERY",
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
        "buildStarted": False,
        "productionStateChanged": False,
    }


def acquire_handoff(
    *,
    observed_at: str,
    archive_store: ObjectStore,
    current_release_date: str | None,
    current_source_sha256: str | None,
    metadata_endpoint: str = CATALOG_ENDPOINT,
    discover: Callable[..., DiscoveryResult] = discover_metadata,
    select: Callable[[Sequence[AcquisitionCandidate]], AcquisitionCandidate] = select_latest_candidate,
    download: Callable[..., Any] = download_candidate,
    inspect: Callable[..., Any] = inspect_source,
) -> dict[str, Any]:
    """Return a verified archive handoff or a safe no-build disposition."""

    report = _base_report(observed_at)
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
                    "officialUrl": candidate.official_url,
                    "expectedSourceHost": candidate.expected_source_host,
                    "observedAt": candidate.observed_at,
                },
            }
        )
        with tempfile.TemporaryDirectory(prefix="valuepilot-argentina-handoff-") as temporary:
            downloaded = download(candidate, Path(temporary) / "official-sepa.zip")
            source = inspect(downloaded.path, fixture=False)
            if source.official is not True:
                raise AcquisitionHandoffError("downloaded source is not official", code="SOURCE_NOT_OFFICIAL")
            if source.bytes != downloaded.bytes or source.sha256 != downloaded.sha256:
                raise AcquisitionHandoffError("download and ZIP inspection identities differ", code="SOURCE_IDENTITY_MISMATCH")
            report.update(
                {
                    "stage": "VERIFIED_SOURCE",
                    "download": {
                        "bytes": downloaded.bytes,
                        "sha256": downloaded.sha256,
                        "attempts": downloaded.attempts,
                    },
                    "source": {
                        "kind": source.source_kind,
                        "bytes": source.bytes,
                        "sha256": source.sha256,
                        "releaseDate": source.release_date,
                        "releaseEvidence": source.release_evidence,
                        "official": source.official,
                    },
                }
            )
            decision = classify_source(
                release_date=source.release_date,
                source_sha256=source.sha256,
                current_release_date=current_release_date,
                current_source_sha256=current_source_sha256,
                upstream_changed=False,
            )
            report["sourceDecision"] = decision.as_dict()
            if decision.disposition in {"NO_CHANGE", "OLDER_REJECTED"}:
                report.update({"stage": "STOPPED", "status": decision.disposition})
                return report
            if decision.disposition == "REVIEW_REQUIRED":
                raise AcquisitionHandoffError(decision.reason, code="REVIEW_REQUIRED")
            if decision.disposition != "NEWER_RELEASE":
                raise AcquisitionHandoffError("source disposition is not eligible for automatic handoff", code="UNEXPECTED_SOURCE_DECISION")
            archive = PrivateSourceArchive(archive_store).archive_file(
                downloaded.path,
                candidate=candidate,
                acquired_at=observed_at,
                release_date=source.release_date,
            )
            report.update(
                {
                    "stage": "ARCHIVED",
                    "status": "HANDOFF_READY",
                    "archive": archive.as_dict(),
                    "handoff": {
                        "sourceArchiveKey": archive.key,
                        "sourceArchiveSha256": archive.sha256,
                        "releaseDate": source.release_date,
                        "observedAt": observed_at,
                        "resourceId": candidate.resource_id,
                        "resourceName": candidate.resource_name,
                    },
                }
            )
            return report
    except (AcquisitionError, DailyReleaseError, AcquisitionHandoffError) as exc:
        report.update({"status": "FAILED", "error": getattr(exc, "code", "HANDOFF_FAILED"), "message": str(exc)})
        return report


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--observed-at", required=True)
    parser.add_argument("--metadata-endpoint", default=CATALOG_ENDPOINT)
    parser.add_argument("--current-release-date")
    parser.add_argument("--current-source-sha256")
    archive_group = parser.add_mutually_exclusive_group(required=True)
    archive_group.add_argument("--archive-store", type=Path)
    archive_group.add_argument("--archive-bucket")
    parser.add_argument("--archive-project")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)

    if args.archive_bucket:
        from backend.object_store import GCSObjectStore

        archive_store: ObjectStore = GCSObjectStore(bucket=args.archive_bucket, project=args.archive_project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None)
    else:
        archive_store = LocalFilesystemObjectStore(args.archive_store)

    report = acquire_handoff(
        observed_at=args.observed_at,
        archive_store=archive_store,
        current_release_date=args.current_release_date,
        current_source_sha256=args.current_source_sha256,
        metadata_endpoint=args.metadata_endpoint,
    )
    if args.output is not None:
        _write_json_atomic(args.output, report)
    print(json.dumps(report, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    return 0 if report.get("status") in {"HANDOFF_READY", "NO_CHANGE", "OLDER_REJECTED"} else 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = ["AcquisitionHandoffError", "POLICY_VERSION", "SCHEMA_VERSION", "acquire_handoff"]

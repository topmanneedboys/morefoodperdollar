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
import datetime as _dt
import hashlib
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

try:
    from backend.object_store import LocalFilesystemObjectStore, ObjectStore, ObjectStoreError
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
    from backend.object_store import LocalFilesystemObjectStore, ObjectStore, ObjectStoreError
    from argentina_daily_release import DailyReleaseError, inspect_source
    from argentina_release_acquisition import AcquisitionCandidate, AcquisitionError, CATALOG_ENDPOINT, DiscoveryResult, PrivateSourceArchive, canonical_bytes, classify_source, discover_metadata, download_candidate, select_latest_candidate


SCHEMA_VERSION = "valuepilot-argentina-acquisition-handoff-v1"
POLICY_VERSION = "valuepilot-argentina-acquisition-handoff-policy-v1"
ACTIVE_SCHEMA_VERSION = "valuepilot-active-release-v1"
ACTIVE_KEY = "control/active.json"
MAX_REPORT_BYTES = 128 * 1024
MAX_ACTIVE_POINTER_BYTES = 16 * 1024
MAX_RELEASE_MANIFEST_BYTES = 4 * 1024 * 1024
_RELEASE_ID = re.compile(r"^[A-Za-z0-9._-]{1,128}$")
_SHA256 = re.compile(r"^[0-9a-f]{64}$")


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


def _store_bytes(store: ObjectStore, key: str, *, maximum: int, label: str) -> bytes:
    try:
        metadata = store.head(key)
        if metadata.size <= 0 or metadata.size > maximum:
            raise AcquisitionHandoffError(f"{label} size is invalid", code="CURRENT_STATE_INVALID")
        raw = store.get(key)
    except ObjectStoreError as exc:
        raise AcquisitionHandoffError(f"{label} is unavailable", code="CURRENT_STATE_UNAVAILABLE") from exc
    if len(raw) != metadata.size or len(raw) > maximum:
        raise AcquisitionHandoffError(f"{label} size is inconsistent", code="CURRENT_STATE_INVALID")
    if metadata.sha256 is not None and hashlib.sha256(raw).hexdigest() != metadata.sha256:
        raise AcquisitionHandoffError(f"{label} hash is inconsistent", code="CURRENT_STATE_INVALID")
    return raw


def _json_object(raw: bytes, *, label: str) -> dict[str, Any]:
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AcquisitionHandoffError(f"{label} is invalid JSON", code="CURRENT_STATE_INVALID") from exc
    if not isinstance(value, dict):
        raise AcquisitionHandoffError(f"{label} is not an object", code="CURRENT_STATE_INVALID")
    return value


def current_identity_from_store(store: ObjectStore) -> dict[str, str]:
    """Read the checksum-verified active release's original SEPA identity.

    Derived routing/SearchPack manifests intentionally preserve the base
    manifest's ``source`` object.  This lookup is read-only and therefore can
    run on an acquisition worker with only runtime-store read capability.
    """

    pointer = _json_object(
        _store_bytes(store, ACTIVE_KEY, maximum=MAX_ACTIVE_POINTER_BYTES, label="active release pointer"),
        label="active release pointer",
    )
    release_id = pointer.get("releaseId")
    if pointer.get("schemaVersion") != ACTIVE_SCHEMA_VERSION or not isinstance(release_id, str) or _RELEASE_ID.fullmatch(release_id) is None:
        raise AcquisitionHandoffError("active release pointer identity is invalid", code="CURRENT_STATE_INVALID")

    manifest_key = f"releases/{release_id}/manifest.json"
    sidecar_key = f"releases/{release_id}/manifest.sha256"
    manifest_raw = _store_bytes(store, manifest_key, maximum=MAX_RELEASE_MANIFEST_BYTES, label="active release manifest")
    sidecar_raw = _store_bytes(store, sidecar_key, maximum=256, label="active release manifest checksum")
    expected_sidecar = f"{hashlib.sha256(manifest_raw).hexdigest()}  manifest.json\n".encode("ascii")
    if sidecar_raw not in {expected_sidecar, expected_sidecar.replace(b"\n", b"\r\n")}:
        raise AcquisitionHandoffError("active release manifest checksum is invalid", code="CURRENT_STATE_INVALID")
    manifest = _json_object(manifest_raw, label="active release manifest")
    if manifest.get("releaseId") != release_id or manifest.get("completionState") != "COMPLETE":
        raise AcquisitionHandoffError("active release manifest identity is invalid", code="CURRENT_STATE_INVALID")
    source = manifest.get("source")
    if not isinstance(source, Mapping) or source.get("official") is not True:
        raise AcquisitionHandoffError("active release source identity is missing", code="CURRENT_STATE_INVALID")
    release_date = source.get("releaseDate")
    source_sha256 = source.get("sha256")
    if not isinstance(release_date, str):
        raise AcquisitionHandoffError("active release source date is invalid", code="CURRENT_STATE_INVALID")
    try:
        parsed_date = _dt.date.fromisoformat(release_date).isoformat()
    except ValueError as exc:
        raise AcquisitionHandoffError("active release source date is invalid", code="CURRENT_STATE_INVALID") from exc
    if not isinstance(source_sha256, str) or _SHA256.fullmatch(source_sha256) is None:
        raise AcquisitionHandoffError("active release source hash is invalid", code="CURRENT_STATE_INVALID")
    return {"activeReleaseId": release_id, "releaseDate": parsed_date, "sourceSha256": source_sha256}


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
    current_store: ObjectStore | None = None,
    metadata_endpoint: str = CATALOG_ENDPOINT,
    upstream_changed: bool = False,
    upstream_change_evidence: Mapping[str, Any] | None = None,
    expected_discovery_response_sha256: str | None = None,
    discover: Callable[..., DiscoveryResult] = discover_metadata,
    select: Callable[[Sequence[AcquisitionCandidate]], AcquisitionCandidate] = select_latest_candidate,
    download: Callable[..., Any] = download_candidate,
    inspect: Callable[..., Any] = inspect_source,
) -> dict[str, Any]:
    """Return a verified archive handoff or a safe no-build disposition."""

    report = _base_report(observed_at)
    try:
        if (current_release_date is None) != (current_source_sha256 is None):
            raise AcquisitionHandoffError("current release date and source hash must be supplied together", code="INVALID_CONFIGURATION")
        if not isinstance(upstream_changed, bool):
            raise AcquisitionHandoffError("upstream change flag is invalid", code="INVALID_CONFIGURATION")
        if upstream_changed:
            if not isinstance(upstream_change_evidence, Mapping):
                raise AcquisitionHandoffError("same-day revision lacks metadata-change evidence", code="UPSTREAM_CHANGE_EVIDENCE_REQUIRED")
            previous_fingerprint = upstream_change_evidence.get("previousMetadataFingerprint")
            current_fingerprint = upstream_change_evidence.get("currentMetadataFingerprint")
            response_sha = upstream_change_evidence.get("catalogResponseSha256")
            if (
                not isinstance(previous_fingerprint, str)
                or _SHA256.fullmatch(previous_fingerprint) is None
                or not isinstance(current_fingerprint, str)
                or _SHA256.fullmatch(current_fingerprint) is None
                or previous_fingerprint == current_fingerprint
                or not isinstance(response_sha, str)
                or _SHA256.fullmatch(response_sha) is None
            ):
                raise AcquisitionHandoffError("same-day revision metadata evidence is invalid", code="UPSTREAM_CHANGE_EVIDENCE_INVALID")
            if expected_discovery_response_sha256 != response_sha:
                raise AcquisitionHandoffError("same-day revision metadata evidence does not match preflight", code="UPSTREAM_CHANGE_EVIDENCE_INVALID")
        elif upstream_change_evidence is not None:
            raise AcquisitionHandoffError("metadata-change evidence was supplied without a detected change", code="INVALID_CONFIGURATION")
        if current_store is not None:
            if current_release_date is not None or current_source_sha256 is not None:
                raise AcquisitionHandoffError("manual current identity and current store are mutually exclusive", code="INVALID_CONFIGURATION")
            report["stage"] = "CURRENT_STATE"
            current = current_identity_from_store(current_store)
            current_release_date = current["releaseDate"]
            current_source_sha256 = current["sourceSha256"]
            report["currentRelease"] = current
        elif current_release_date is None or current_source_sha256 is None:
            raise AcquisitionHandoffError("current trusted release identity is required", code="CURRENT_STATE_REQUIRED")

        discovery = discover(observed_at=observed_at, endpoint=metadata_endpoint)
        if expected_discovery_response_sha256 is not None and discovery.response_sha256 != expected_discovery_response_sha256:
            raise AcquisitionHandoffError("official metadata changed after preflight", code="SOURCE_METADATA_RACE")
        candidate = select(discovery.candidates)
        report.update(
            {
                "stage": "DOWNLOAD",
                "catalogEndpoint": discovery.endpoint,
                "catalogResponseSha256": discovery.response_sha256,
                "upstreamChanged": upstream_changed,
                "upstreamChangeEvidence": dict(upstream_change_evidence) if upstream_change_evidence is not None else None,
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
                upstream_changed=upstream_changed,
            )
            report["sourceDecision"] = decision.as_dict()
            if decision.disposition in {"NO_CHANGE", "OLDER_REJECTED"}:
                report.update({"stage": "STOPPED", "status": decision.disposition})
                return report
            if decision.disposition == "REVIEW_REQUIRED":
                raise AcquisitionHandoffError(decision.reason, code="REVIEW_REQUIRED")
            if decision.disposition not in {"NEWER_RELEASE", "SAME_DAY_REVISION"}:
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
    parser.add_argument("--upstream-changed", action="store_true")
    parser.add_argument("--upstream-change-evidence", type=Path)
    parser.add_argument("--expected-discovery-response-sha256")
    archive_group = parser.add_mutually_exclusive_group(required=True)
    archive_group.add_argument("--archive-store", type=Path)
    archive_group.add_argument("--archive-bucket")
    current_group = parser.add_mutually_exclusive_group()
    current_group.add_argument("--active-store", type=Path)
    current_group.add_argument("--active-bucket")
    parser.add_argument("--archive-project")
    parser.add_argument("--active-project")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)

    if args.archive_bucket or args.active_bucket:
        from backend.object_store import GCSObjectStore
    if args.archive_bucket:
        archive_store: ObjectStore = GCSObjectStore(bucket=args.archive_bucket, project=args.archive_project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None)
    else:
        archive_store = LocalFilesystemObjectStore(args.archive_store)
    if args.active_bucket:
        current_store: ObjectStore | None = GCSObjectStore(bucket=args.active_bucket, project=args.active_project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None)
    elif args.active_store:
        current_store = LocalFilesystemObjectStore(args.active_store)
    else:
        current_store = None

    evidence = None
    expected_response_sha256 = args.expected_discovery_response_sha256
    if args.upstream_change_evidence is not None:
        try:
            source_preflight = json.loads(args.upstream_change_evidence.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise SystemExit("upstream change evidence is unavailable or invalid") from exc
        if not isinstance(source_preflight, Mapping):
            raise SystemExit("upstream change evidence must be an object")
        evidence = source_preflight.get("upstreamChangeEvidence")
        if expected_response_sha256 is None:
            expected_response_sha256 = source_preflight.get("catalogResponseSha256")
    report = acquire_handoff(
        observed_at=args.observed_at,
        archive_store=archive_store,
        current_release_date=args.current_release_date,
        current_source_sha256=args.current_source_sha256,
        current_store=current_store,
        metadata_endpoint=args.metadata_endpoint,
        upstream_changed=bool(args.upstream_changed),
        upstream_change_evidence=evidence,
        expected_discovery_response_sha256=expected_response_sha256,
    )
    if args.output is not None:
        _write_json_atomic(args.output, report)
    print(json.dumps(report, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    return 0 if report.get("status") in {"HANDOFF_READY", "NO_CHANGE", "OLDER_REJECTED"} else 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = ["AcquisitionHandoffError", "POLICY_VERSION", "SCHEMA_VERSION", "acquire_handoff", "current_identity_from_store"]

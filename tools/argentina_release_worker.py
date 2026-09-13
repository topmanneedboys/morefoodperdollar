"""Automated official-release worker entry point.

The worker is designed for Cloud Build (or a local operator shadow run).  It
does only provider acquisition/control-plane work and then delegates source
qualification and deterministic artifact generation to
``DailyReleaseOrchestrator``.  It never writes a production pointer itself;
staging, canary selection, and activation remain explicit publisher phases.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import sys
import tempfile
import uuid
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

try:
    from backend.object_store import LocalFilesystemObjectStore, ObjectStore, ObjectStoreError
    from tools.argentina_daily_release import DailyReleaseError, DailyReleaseOrchestrator, inspect_source
    from tools.argentina_release_acquisition import (
        AcquisitionCandidate,
        AcquisitionError,
        CATALOG_ENDPOINT,
        DiscoveryResult,
        PrivateSourceArchive,
        SourceDecision,
        canonical_bytes,
        classify_source,
        discover_metadata,
        download_candidate,
        select_latest_candidate,
    )
    from tools.argentina_release_automation import DistributedReleaseLease, LeaseHandle, write_execution_summary
except ModuleNotFoundError:  # direct invocation from tools/
    from backend.object_store import LocalFilesystemObjectStore, ObjectStore, ObjectStoreError
    from argentina_daily_release import DailyReleaseError, DailyReleaseOrchestrator, inspect_source
    from argentina_release_acquisition import AcquisitionCandidate, AcquisitionError, CATALOG_ENDPOINT, DiscoveryResult, PrivateSourceArchive, SourceDecision, canonical_bytes, classify_source, discover_metadata, download_candidate, select_latest_candidate
    from argentina_release_automation import DistributedReleaseLease, LeaseHandle, write_execution_summary


WORKER_SCHEMA_VERSION = "valuepilot-argentina-release-worker-v1"
WORKER_POLICY_VERSION = "valuepilot-argentina-release-worker-policy-v1"
DISCOVERY_STATE_KEY = "control/discovery-state.json"
MAX_DISCOVERY_STATE_BYTES = 64 * 1024
MAX_UPSTREAM_EVIDENCE_BYTES = 32 * 1024


class WorkerError(RuntimeError):
    """A worker state transition failed closed."""

    def __init__(self, message: str, *, code: str = "WORKER_FAILED") -> None:
        super().__init__(message)
        self.code = code


def discovery_fingerprint(result: DiscoveryResult) -> str:
    """Hash only the bounded metadata contract, never downloaded bytes."""

    payload = {
        "endpoint": result.endpoint,
        "responseSha256": result.response_sha256,
        "datasetId": result.dataset_id,
        "candidates": [
            {
                "resourceId": candidate.resource_id,
                "resourceName": candidate.resource_name,
                "officialUrl": candidate.official_url,
                "observedSize": candidate.observed_size,
                "lastModified": candidate.last_modified,
                "releaseDateHint": candidate.release_date_hint,
            }
            for candidate in result.candidates
        ],
    }
    import hashlib

    return hashlib.sha256(canonical_bytes(payload)).hexdigest()


def _read_discovery_state(store: ObjectStore) -> tuple[Any | None, dict[str, Any] | None]:
    try:
        if not store.exists(DISCOVERY_STATE_KEY):
            return None, None
        metadata = store.head(DISCOVERY_STATE_KEY)
        if metadata.size > MAX_DISCOVERY_STATE_BYTES:
            raise WorkerError("discovery state exceeds bound", code="INVALID_STATE")
        value = json.loads(store.get(DISCOVERY_STATE_KEY))
    except WorkerError:
        raise
    except ObjectStoreError as exc:
        raise WorkerError("discovery state availability is unknown", code="CONTROL_STORE_UNAVAILABLE") from exc
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
        raise WorkerError("discovery state is invalid", code="INVALID_STATE") from exc
    if not isinstance(value, dict):
        raise WorkerError("discovery state is invalid", code="INVALID_STATE")
    return metadata, value


def record_discovery_state(store: ObjectStore, result: DiscoveryResult, *, execution_id: str) -> dict[str, Any]:
    """CAS the small discovery fingerprint; unchanged metadata is write-free."""

    if not isinstance(execution_id, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", execution_id):
        raise WorkerError("execution ID is invalid", code="INVALID_STATE")
    fingerprint = discovery_fingerprint(result)
    metadata, previous = _read_discovery_state(store)
    if previous is not None and previous.get("fingerprint") == fingerprint:
        return {"status": "NO_CHANGE", "fingerprint": fingerprint, "activePointerWrites": 0, "state": previous}
    value = {
        "schemaVersion": WORKER_SCHEMA_VERSION,
        "policyVersion": WORKER_POLICY_VERSION,
        "fingerprint": fingerprint,
        "executionId": execution_id,
        "endpoint": result.endpoint,
        "responseSha256": result.response_sha256,
        "datasetId": result.dataset_id,
        "resourceIds": [candidate.resource_id for candidate in result.candidates],
    }
    raw = canonical_bytes(value) + b"\n"
    if len(raw) > MAX_DISCOVERY_STATE_BYTES:
        raise WorkerError("discovery state exceeds bound", code="INVALID_STATE")
    try:
        if metadata is None:
            store.put_immutable(DISCOVERY_STATE_KEY, raw)
        else:
            store.compare_and_swap(DISCOVERY_STATE_KEY, raw, expected_etag=metadata.etag)
    except ObjectStoreError as exc:
        # A concurrent worker may have committed the same metadata fingerprint
        # between our read and create/CAS.  Re-read once and report a
        # write-free NO_CHANGE for that idempotent winner; all other races and
        # provider failures remain an explicit control-plane stop.
        try:
            winner_metadata, winner = _read_discovery_state(store)
        except WorkerError as reread_exc:
            raise reread_exc from exc
        if winner is not None and winner.get("fingerprint") == fingerprint:
            return {"status": "NO_CHANGE", "fingerprint": fingerprint, "activePointerWrites": 0, "state": winner}
        raise WorkerError("discovery state CAS failed", code="STATE_CONFLICT") from exc
    return {"status": "CHANGED", "fingerprint": fingerprint, "activePointerWrites": 0, "state": value}


def _candidate_identity(candidate: AcquisitionCandidate) -> dict[str, Any]:
    return {"provider": candidate.provider, "datasetId": candidate.dataset_id, "resourceId": candidate.resource_id, "resourceName": candidate.resource_name, "discoveryResponseSha256": candidate.discovery_response_sha256}


def _same_day_change_proven(candidate: AcquisitionCandidate, *, previous_source_sha256: str | None, evidence: Mapping[str, Any] | None) -> bool:
    """Require explicit changed-metadata evidence for same-day revisions."""

    if not isinstance(evidence, Mapping) or evidence.get("changed") is not True:
        return False
    if evidence.get("resourceId") != candidate.resource_id or evidence.get("currentDiscoveryResponseSha256") != candidate.discovery_response_sha256:
        return False
    previous_discovery = evidence.get("previousDiscoveryResponseSha256")
    if not isinstance(previous_discovery, str) or not re.fullmatch(r"[0-9a-f]{64}", previous_discovery) or previous_discovery == candidate.discovery_response_sha256:
        return False
    if not isinstance(previous_source_sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", previous_source_sha256):
        return False
    # The response hash changed and the exact candidate resource is named; the
    # downloaded bytes are separately compared to the active source below.
    return True


def _write_local_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    temporary = Path(name)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(canonical_bytes(value) + b"\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def run_shadow(
    *,
    discovery: DiscoveryResult,
    current_release_date: str | None,
    current_source_sha256: str | None,
    execution_id: str,
    observed_at: str,
    source_path: Path | None = None,
    archive_store: ObjectStore | None = None,
    workspace: Path | None = None,
    output: Path | None = None,
    fixture: bool = False,
    upstream_changed: bool = False,
    build_evidence: Mapping[str, Any] | None = None,
    upstream_change_evidence: Mapping[str, Any] | None = None,
    opener: Callable[..., Any] | None = None,
    sleep_fn: Callable[[float], None] | None = None,
) -> dict[str, Any]:
    """Run the safe shadow path; production activation is never implicit."""

    if not isinstance(execution_id, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", execution_id):
        raise WorkerError("execution ID is invalid", code="INVALID_STATE")
    if upstream_change_evidence is not None:
        if not isinstance(upstream_change_evidence, Mapping) or len(canonical_bytes(dict(upstream_change_evidence))) > MAX_UPSTREAM_EVIDENCE_BYTES:
            raise WorkerError("upstream change evidence is invalid or exceeds bound", code="INVALID_STATE")
    if build_evidence is not None:
        if not isinstance(build_evidence, Mapping) or len(canonical_bytes(dict(build_evidence))) > MAX_UPSTREAM_EVIDENCE_BYTES:
            raise WorkerError("build evidence is invalid or exceeds bound", code="INVALID_STATE")
    candidate = select_latest_candidate(discovery.candidates)
    state: dict[str, Any] = {
        "schemaVersion": WORKER_SCHEMA_VERSION,
        "policyVersion": WORKER_POLICY_VERSION,
        "executionId": execution_id,
        "mode": "SHADOW",
        "stage": "DISCOVERED",
        "candidate": candidate.as_dict(),
        "currentReleaseDate": current_release_date,
        "currentSourceSha256": current_source_sha256,
        "activePointerWrites": 0,
        "releasePointerWrites": 0,
    }
    lease: LeaseHandle | None = None
    lease_manager = DistributedReleaseLease(archive_store) if archive_store is not None else None
    try:
        if lease_manager is not None:
            outcome = lease_manager.acquire(execution_id=execution_id, candidate_identity=_candidate_identity(candidate), now=observed_at)
            state["lease"] = outcome.as_dict()
            if outcome.status != "ACQUIRED" or outcome.handle is None:
                state.update({"status": "ALREADY_RUNNING", "stage": "LEASED"})
                return state
            lease = outcome.handle
        if source_path is None:
            if sleep_fn is None:
                from time import sleep as sleep_fn
            with tempfile.TemporaryDirectory(prefix="valuepilot-sepa-worker-") as temporary:
                downloaded = download_candidate(candidate, Path(temporary) / "source.zip", opener=opener, sleep_fn=sleep_fn)
                return _continue_with_source(state, downloaded.path, candidate, current_release_date=current_release_date, current_source_sha256=current_source_sha256, archive_store=archive_store, workspace=workspace, output=output, fixture=fixture, upstream_changed=upstream_changed, observed_at=observed_at, build_evidence=build_evidence, upstream_change_evidence=upstream_change_evidence)
        result = _continue_with_source(state, Path(source_path).resolve(), candidate, current_release_date=current_release_date, current_source_sha256=current_source_sha256, archive_store=archive_store, workspace=workspace, output=output, fixture=fixture, upstream_changed=upstream_changed, observed_at=observed_at, build_evidence=build_evidence, upstream_change_evidence=upstream_change_evidence)
        return result
    finally:
        if lease is not None and lease_manager is not None:
            try:
                lease_manager.release(lease, released_at=observed_at)
            except WorkerError:
                # Do not hide the original qualification failure; the durable
                # execution state will make an unreleased lease observable.
                pass


def _continue_with_source(
    state: dict[str, Any],
    source_path: Path,
    candidate: AcquisitionCandidate,
    *,
    current_release_date: str | None,
    current_source_sha256: str | None,
    archive_store: ObjectStore | None,
    workspace: Path | None,
    output: Path | None,
    fixture: bool,
    upstream_changed: bool,
    observed_at: str,
    build_evidence: Mapping[str, Any] | None,
    upstream_change_evidence: Mapping[str, Any] | None,
) -> dict[str, Any]:
    try:
        source = inspect_source(source_path, fixture=fixture)
    except DailyReleaseError as exc:
        raise WorkerError(str(exc), code=exc.code) from exc
    state.update({"stage": "ACQUIRED", "source": source.as_dict(), "sourcePath": str(source_path)})
    if archive_store is not None:
        archive = PrivateSourceArchive(archive_store).archive_file(source_path, candidate=candidate, acquired_at=observed_at, release_date=source.release_date)
        state.update({"stage": "ARCHIVED", "archive": archive.as_dict()})
    same_day_proven = upstream_changed and _same_day_change_proven(candidate, previous_source_sha256=current_source_sha256, evidence=upstream_change_evidence)
    decision = classify_source(release_date=source.release_date, source_sha256=source.sha256, current_release_date=current_release_date, current_source_sha256=current_source_sha256, upstream_changed=same_day_proven)
    state["sourceDecision"] = decision.as_dict()
    state["sameDayRevisionProven"] = same_day_proven
    if decision.disposition in {"NO_CHANGE", "OLDER_REJECTED", "REVIEW_REQUIRED"}:
        state.update({"stage": "NO_CHANGE" if decision.disposition == "NO_CHANGE" else "STOPPED", "status": decision.disposition, "cloudBuildStarted": False, "activePointerWrites": 0})
        return state
    if workspace is None or output is None:
        state.update({"stage": "VERIFIED_INPUT", "status": "CANDIDATE_READY", "cloudBuildStarted": False})
        return state
    builder = dict(build_evidence or {})
    builder = {str(key): value for key, value in builder.items() if value is not None}
    acquisition_evidence = {"automated": True, "provider": candidate.provider, "datasetId": candidate.dataset_id, "resourceId": candidate.resource_id, "resourceName": candidate.resource_name, "officialUrl": candidate.official_url, "discoveryResponseSha256": candidate.discovery_response_sha256, "observedAt": candidate.observed_at, "acquiredAt": observed_at, "archiveKey": state.get("archive", {}).get("key") if isinstance(state.get("archive"), Mapping) else None, "builder": builder, "upstreamChangeEvidence": dict(upstream_change_evidence or {}) if same_day_proven else None}
    state["buildProvenance"] = builder
    orchestrator = DailyReleaseOrchestrator(source_path=source_path, workspace=workspace, output=output, fixture=fixture, dry_run=True, upstream_changed=upstream_changed, acquisition_evidence=acquisition_evidence)
    release = orchestrator.run(audit_at=observed_at)
    state.update({"stage": "VERIFIED", "status": "CANDIDATE_VERIFIED", "releaseId": release.release_id, "releaseReport": str(output / "release-report.json"), "cloudBuildStarted": True, "activePointerWrites": 0})
    return state


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata-endpoint", default=None)
    parser.add_argument("--observed-at", required=True)
    parser.add_argument("--execution-id", default=None)
    parser.add_argument("--current-release-date")
    parser.add_argument("--current-source-sha256")
    parser.add_argument("--source", type=Path)
    parser.add_argument("--source-archive-key")
    parser.add_argument("--source-archive-sha256")
    archive_group = parser.add_mutually_exclusive_group(required=True)
    archive_group.add_argument("--archive-store", type=Path, help="operator-only local mirror in shadow/local mode")
    archive_group.add_argument("--archive-bucket", help="operator-only GCS bucket for source archives and control state")
    parser.add_argument("--archive-project")
    parser.add_argument("--workspace", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fixture", action="store_true")
    parser.add_argument("--upstream-changed", action="store_true")
    parser.add_argument("--upstream-change-evidence", type=Path)
    parser.add_argument("--build-id")
    parser.add_argument("--builder-image")
    parser.add_argument("--builder-sha")
    args = parser.parse_args(argv)
    execution_id = args.execution_id or f"worker-{uuid.uuid4().hex}"
    archive: ObjectStore | None = None
    stage = "DISCOVERY"
    try:
        discovery = discover_metadata(observed_at=args.observed_at, endpoint=args.metadata_endpoint or CATALOG_ENDPOINT)
        if args.archive_bucket:
            from backend.object_store import GCSObjectStore

            archive = GCSObjectStore(bucket=args.archive_bucket, project=args.archive_project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None)
        else:
            archive = LocalFilesystemObjectStore(args.archive_store)
        source_archive_key = args.source_archive_key or os.environ.get("VALUEPILOT_SOURCE_ARCHIVE_KEY") or None
        source_archive_sha256 = args.source_archive_sha256 or os.environ.get("VALUEPILOT_SOURCE_ARCHIVE_SHA256") or None
        upstream_changed = args.upstream_changed or os.environ.get("VALUEPILOT_UPSTREAM_CHANGED", "").casefold() == "true"
        upstream_change_evidence: Mapping[str, Any] | None = None
        if args.upstream_change_evidence is not None:
            try:
                raw_evidence = args.upstream_change_evidence.read_bytes()
            except OSError as exc:
                raise WorkerError("upstream change evidence is unavailable", code="INVALID_STATE") from exc
            if len(raw_evidence) > MAX_UPSTREAM_EVIDENCE_BYTES:
                raise WorkerError("upstream change evidence exceeds bound", code="INVALID_STATE")
            try:
                value = json.loads(raw_evidence)
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise WorkerError("upstream change evidence is invalid", code="INVALID_STATE") from exc
            if not isinstance(value, Mapping):
                raise WorkerError("upstream change evidence is invalid", code="INVALID_STATE")
            upstream_change_evidence = value
        if args.source is not None and (source_archive_key is not None or source_archive_sha256 is not None):
            raise WorkerError("source and source archive restore are mutually exclusive", code="INVALID_STATE")
        stage = "DISCOVERY_STATE"
        discovery_state = record_discovery_state(archive, discovery, execution_id=execution_id)
        if discovery_state["status"] == "NO_CHANGE" and args.source is None:
            state = {"schemaVersion": WORKER_SCHEMA_VERSION, "policyVersion": WORKER_POLICY_VERSION, "executionId": execution_id, "mode": "DISCOVERY", "stage": "NO_CHANGE", "status": "NO_CHANGE", "discovery": discovery_state, "cloudBuildStarted": False, "activePointerWrites": 0}
        else:
            build_evidence = {"buildId": args.build_id or os.environ.get("BUILD_ID"), "builderImage": args.builder_image or os.environ.get("BUILDER_IMAGE"), "builderSha": args.builder_sha or os.environ.get("BUILDER_SHA")}
            if source_archive_key is not None or source_archive_sha256 is not None:
                if not source_archive_key or not source_archive_sha256:
                    raise WorkerError("source archive key and hash are both required", code="INVALID_STATE")
                stage = "RESTORE"
                with tempfile.TemporaryDirectory(prefix="valuepilot-sepa-restore-") as temporary:
                    restored = PrivateSourceArchive(archive).restore_file(source_archive_key, Path(temporary) / "source.zip", expected_sha256=source_archive_sha256)
                    stage = "SHADOW"
                    state = run_shadow(discovery=discovery, current_release_date=args.current_release_date, current_source_sha256=args.current_source_sha256, execution_id=execution_id, observed_at=args.observed_at, source_path=restored, archive_store=archive, workspace=args.workspace, output=args.output, fixture=args.fixture, upstream_changed=upstream_changed, upstream_change_evidence=upstream_change_evidence, build_evidence=build_evidence)
            else:
                stage = "SHADOW"
                state = run_shadow(discovery=discovery, current_release_date=args.current_release_date, current_source_sha256=args.current_source_sha256, execution_id=execution_id, observed_at=args.observed_at, source_path=args.source, archive_store=archive, workspace=args.workspace, output=args.output, fixture=args.fixture, upstream_changed=upstream_changed, upstream_change_evidence=upstream_change_evidence, build_evidence=build_evidence)
            state["discovery"] = discovery_state
        stage = "SUMMARY"
        write_execution_summary(archive, execution_id, state)
        print(json.dumps(state, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except (AcquisitionError, WorkerError, DailyReleaseError) as exc:
        if archive is not None:
            try:
                write_execution_summary(
                    archive,
                    execution_id,
                    {
                        "mode": "SHADOW",
                        "stage": stage,
                        "status": "FAILED",
                        "error": getattr(exc, "code", "WORKER_FAILED"),
                        "activePointerWrites": 0,
                    },
                )
            except Exception:  # noqa: BLE001 - preserve the original failure on the operator stream
                pass
        print(json.dumps({"error": getattr(exc, "code", "WORKER_FAILED"), "message": str(exc)}, ensure_ascii=False, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = ["DISCOVERY_STATE_KEY", "WORKER_POLICY_VERSION", "WORKER_SCHEMA_VERSION", "WorkerError", "discovery_fingerprint", "record_discovery_state", "run_shadow"]

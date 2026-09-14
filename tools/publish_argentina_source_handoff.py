"""Publish one locally verified Argentina source handoff to a private object store.

The acquisition step is intentionally separate: it runs while the approved
Argentina egress tunnel is up and writes only to a local content-addressed
archive. This publisher runs after that tunnel is down. It revalidates the
handoff report and local archive identity, then performs immutable object-store
writes for the ZIP and its sidecar manifest. It never builds a release, writes
canary state, or changes an active pointer.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.object_store import GCSObjectStore, LocalFilesystemObjectStore, ObjectStore, ObjectStoreError, validate_key
from tools.argentina_release_acquisition import MAX_SOURCE_BYTES, canonical_bytes
from tools.argentina_release_acquisition_handoff import SCHEMA_VERSION as HANDOFF_SCHEMA_VERSION


SCHEMA_VERSION = "valuepilot-argentina-source-handoff-publish-v1"
POLICY_VERSION = "valuepilot-argentina-source-handoff-publish-policy-v1"
MAX_REPORT_BYTES = 128 * 1024
MAX_MANIFEST_BYTES = 256 * 1024
_SHA256 = re.compile(r"^[0-9a-f]{64}$")


class SourceHandoffPublishError(RuntimeError):
    def __init__(self, message: str, *, code: str = "PUBLISH_FAILED") -> None:
        super().__init__(message)
        self.code = code


def _read_json_object(path: Path | str, *, maximum: int, label: str) -> dict[str, Any]:
    source = Path(path)
    try:
        raw = source.read_bytes()
    except OSError as exc:
        raise SourceHandoffPublishError(f"{label} is unavailable", code="REPORT_UNAVAILABLE") from exc
    if not raw or len(raw) > maximum:
        raise SourceHandoffPublishError(f"{label} size is invalid", code="INVALID_REPORT")
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SourceHandoffPublishError(f"{label} is invalid JSON", code="INVALID_REPORT") from exc
    if not isinstance(value, dict):
        raise SourceHandoffPublishError(f"{label} must be an object", code="INVALID_REPORT")
    return value


def _write_json_atomic(path: Path, value: Mapping[str, Any]) -> None:
    raw = canonical_bytes(dict(value)) + b"\n"
    if len(raw) > MAX_REPORT_BYTES:
        raise SourceHandoffPublishError("publisher report exceeds bound", code="REPORT_TOO_LARGE")
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


def _hash_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _validated_archive(report: Mapping[str, Any]) -> tuple[str, str, str, int]:
    if report.get("schemaVersion") != HANDOFF_SCHEMA_VERSION or report.get("status") != "HANDOFF_READY":
        raise SourceHandoffPublishError("handoff is not a verified ready report", code="INVALID_HANDOFF")
    if report.get("productionStateChanged") is not False or report.get("activePointerWrites") != 0 or report.get("buildStarted") is not False:
        raise SourceHandoffPublishError("handoff violates the write-free acquisition contract", code="INVALID_HANDOFF")

    archive = report.get("archive")
    handoff = report.get("handoff")
    if not isinstance(archive, Mapping) or not isinstance(handoff, Mapping):
        raise SourceHandoffPublishError("handoff archive identity is missing", code="INVALID_HANDOFF")

    key = archive.get("key")
    manifest_key = archive.get("manifestKey")
    digest = archive.get("sha256")
    size = archive.get("bytes")
    if not isinstance(key, str) or not isinstance(manifest_key, str):
        raise SourceHandoffPublishError("handoff archive keys are invalid", code="INVALID_HANDOFF")
    try:
        key = validate_key(key)
        manifest_key = validate_key(manifest_key)
    except ObjectStoreError as exc:
        raise SourceHandoffPublishError("handoff archive key is invalid", code="INVALID_HANDOFF") from exc
    expected_key = f"source-archive/sha256/{digest}.zip"
    expected_manifest_key = f"source-archive/manifests/{digest}.json"
    if key != expected_key or manifest_key != expected_manifest_key:
        raise SourceHandoffPublishError("handoff archive is not content-addressed", code="INVALID_HANDOFF")
    if not isinstance(digest, str) or _SHA256.fullmatch(digest) is None:
        raise SourceHandoffPublishError("handoff source hash is invalid", code="INVALID_HANDOFF")
    if not isinstance(size, int) or isinstance(size, bool) or not 1 <= size <= MAX_SOURCE_BYTES:
        raise SourceHandoffPublishError("handoff source size is invalid", code="INVALID_HANDOFF")
    if handoff.get("sourceArchiveKey") != key or handoff.get("sourceArchiveSha256") != digest:
        raise SourceHandoffPublishError("handoff and archive identities differ", code="INVALID_HANDOFF")
    return key, manifest_key, digest, size


def publish_verified_handoff(
    report: Mapping[str, Any],
    *,
    local_store: ObjectStore,
    destination_store: ObjectStore,
) -> dict[str, Any]:
    key, manifest_key, digest, size = _validated_archive(report)
    try:
        local_source = local_store.head(key)
        if local_source.size != size or local_source.sha256 != digest:
            raise SourceHandoffPublishError("local archived ZIP identity differs from handoff", code="LOCAL_ARCHIVE_MISMATCH")
        manifest_raw = local_store.get(manifest_key)
    except ObjectStoreError as exc:
        raise SourceHandoffPublishError("local archive is unavailable", code="LOCAL_ARCHIVE_UNAVAILABLE") from exc
    if not manifest_raw or len(manifest_raw) > MAX_MANIFEST_BYTES:
        raise SourceHandoffPublishError("local archive manifest size is invalid", code="LOCAL_ARCHIVE_MISMATCH")
    try:
        manifest = json.loads(manifest_raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SourceHandoffPublishError("local archive manifest is invalid", code="LOCAL_ARCHIVE_MISMATCH") from exc
    if not isinstance(manifest, dict):
        raise SourceHandoffPublishError("local archive manifest is invalid", code="LOCAL_ARCHIVE_MISMATCH")
    if (
        manifest.get("sourceSha256") != digest
        or manifest.get("bytes") != size
        or manifest.get("archiveKey") != key
        or manifest.get("rawProviderDataCommitted") is not False
        or manifest.get("runtimePublicationAllowed") is not False
    ):
        raise SourceHandoffPublishError("local archive manifest identity differs from handoff", code="LOCAL_ARCHIVE_MISMATCH")

    source_path: Path | None = None
    if isinstance(local_store, LocalFilesystemObjectStore):
        source_path = local_store._path(key)  # validated key; avoids loading ~330 MB into RAM
    try:
        if source_path is not None:
            published_source = destination_store.put_immutable_file(key, source_path, sha256=digest)
        else:
            source_bytes = local_store.get(key)
            if len(source_bytes) != size or _hash_bytes(source_bytes) != digest:
                raise SourceHandoffPublishError("local archived ZIP identity differs from handoff", code="LOCAL_ARCHIVE_MISMATCH")
            published_source = destination_store.put_immutable(key, source_bytes, sha256=digest)
        destination_store.put_immutable(manifest_key, manifest_raw, sha256=_hash_bytes(manifest_raw))
    except ObjectStoreError as exc:
        raise SourceHandoffPublishError("private archive publication failed", code="DESTINATION_ARCHIVE_FAILED") from exc

    if published_source.size != size:
        raise SourceHandoffPublishError("published source size differs from handoff", code="DESTINATION_ARCHIVE_FAILED")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policyVersion": POLICY_VERSION,
        "status": "PUBLISHED",
        "sourceArchiveKey": key,
        "sourceArchiveSha256": digest,
        "sourceBytes": size,
        "manifestKey": manifest_key,
        "manifestSha256": _hash_bytes(manifest_raw),
        "activePointerWrites": 0,
        "canaryPointerWrites": 0,
        "buildStarted": False,
        "productionStateChanged": False,
    }


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--handoff-report", type=Path, required=True)
    parser.add_argument("--local-archive-store", type=Path, required=True)
    parser.add_argument("--archive-bucket", required=True)
    parser.add_argument("--archive-project")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)

    try:
        handoff = _read_json_object(args.handoff_report, maximum=MAX_REPORT_BYTES, label="handoff report")
        value = publish_verified_handoff(
            handoff,
            local_store=LocalFilesystemObjectStore(args.local_archive_store),
            destination_store=GCSObjectStore(bucket=args.archive_bucket, project=args.archive_project or os.environ.get("GOOGLE_CLOUD_PROJECT") or None),
        )
        if args.output is not None:
            _write_json_atomic(args.output, value)
        print(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except SourceHandoffPublishError as exc:
        error = {
            "schemaVersion": SCHEMA_VERSION,
            "status": "FAILED",
            "error": exc.code,
            "message": str(exc),
            "activePointerWrites": 0,
            "productionStateChanged": False,
        }
        if args.output is not None:
            _write_json_atomic(args.output, error)
        print(json.dumps(error, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = ["POLICY_VERSION", "SCHEMA_VERSION", "SourceHandoffPublishError", "publish_verified_handoff"]

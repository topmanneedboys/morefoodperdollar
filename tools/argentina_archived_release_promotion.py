"""Promote an already verified Argentina release from the archive to production.

This operator-side controller never downloads national data.  It copies the
immutable, content-addressed objects already recorded by the release worker
between GCS buckets, CAS-updates both regional active pointers, runs bounded
authenticated smoke checks, and compensates with the exact prior pointers on
failure.  It is deliberately separate from the Android application.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from backend.object_store import GCSObjectStore, ObjectMetadata, ObjectStore, ObjectStoreError


class PromotionError(RuntimeError):
    """A promotion failed closed."""


_RELEASE_ID = re.compile(r"[A-Za-z0-9._-]{1,160}\Z")
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_MAX_OBJECTS = 5000
_MAX_MANIFEST_BYTES = 2 * 1024 * 1024
_MAX_OBJECT_BYTES = 2 * 1024 * 1024 * 1024
_ACTIVE_KEY = "control/active.json"
_ACTIVE_SCHEMA = "valuepilot-active-release-v1"


def _canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _validate_digest(value: Any, label: str) -> str:
    if not isinstance(value, str) or _SHA256.fullmatch(value) is None:
        raise PromotionError(f"{label} is invalid")
    return value


def _read(store: ObjectStore, key: str, *, maximum: int) -> bytes:
    try:
        metadata = store.head(key)
        if metadata.size < 0 or metadata.size > maximum:
            raise PromotionError(f"{key} exceeds bound")
        raw = store.get(key)
    except ObjectStoreError as exc:
        raise PromotionError(f"{key} is unavailable") from exc
    if len(raw) != metadata.size:
        raise PromotionError(f"{key} changed during read")
    return raw


def load_archived_manifest(
    archive: ObjectStore,
    *,
    release_id: str,
    expected_release_date: str,
    expected_source_sha256: str,
) -> tuple[dict[str, Any], bytes, bytes]:
    if not isinstance(release_id, str) or _RELEASE_ID.fullmatch(release_id) is None:
        raise PromotionError("release ID is invalid")
    manifest_key = f"releases/{release_id}/manifest.json"
    checksum_key = f"releases/{release_id}/manifest.sha256"
    manifest_raw = _read(archive, manifest_key, maximum=_MAX_MANIFEST_BYTES)
    checksum_raw = _read(archive, checksum_key, maximum=4096)
    digest = _sha256(manifest_raw)
    token = checksum_raw.decode("ascii", "strict").split()[0] if checksum_raw.strip() else ""
    if token != digest:
        raise PromotionError("archived manifest checksum does not match")
    try:
        manifest = json.loads(manifest_raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PromotionError("archived manifest is invalid") from exc
    if not isinstance(manifest, dict) or manifest.get("completionState") != "COMPLETE" or manifest.get("releaseId") != release_id:
        raise PromotionError("archived release is not complete")
    source = manifest.get("source")
    if not isinstance(source, Mapping) or source.get("releaseDate") != expected_release_date:
        raise PromotionError("archived source release date does not match")
    if source.get("sha256") != expected_source_sha256:
        raise PromotionError("archived source hash does not match")
    objects = manifest.get("objects")
    if not isinstance(objects, list) or not objects or len(objects) > _MAX_OBJECTS:
        raise PromotionError("archived object inventory is invalid")
    seen: set[str] = set()
    total = 0
    for descriptor in objects:
        if not isinstance(descriptor, Mapping):
            raise PromotionError("archived object descriptor is invalid")
        digest_value = _validate_digest(descriptor.get("sha256"), "archived object hash")
        size = descriptor.get("bytes")
        if not isinstance(size, int) or isinstance(size, bool) or size < 0:
            raise PromotionError("archived object size is invalid")
        if digest_value in seen:
            raise PromotionError("archived object inventory contains a duplicate hash")
        seen.add(digest_value)
        total += size
        if total > _MAX_OBJECT_BYTES:
            raise PromotionError("archived object inventory exceeds bound")
    return manifest, manifest_raw, checksum_raw


def _verify_metadata(metadata: ObjectMetadata, *, digest: str, size: int, label: str) -> None:
    if metadata.size != size:
        raise PromotionError(f"{label} size does not match manifest")
    if metadata.sha256 != digest:
        raise PromotionError(f"{label} hash metadata is missing or mismatched")


def _copy_immutable(archive: ObjectStore, destination: ObjectStore, *, key: str, digest: str, size: int) -> bool:
    """Copy one immutable object, using GCS server-side copy when available."""

    try:
        source_metadata = archive.head(key)
    except ObjectStoreError as exc:
        raise PromotionError(f"archived object {key} is unavailable") from exc
    _verify_metadata(source_metadata, digest=digest, size=size, label=f"archived object {key}")
    try:
        existing = destination.head(key)
    except ObjectStoreError:
        existing = None
    if existing is not None:
        _verify_metadata(existing, digest=digest, size=size, label=f"destination object {key}")
        return True
    if isinstance(archive, GCSObjectStore) and isinstance(destination, GCSObjectStore):
        try:
            source_blob = archive._blob(key)  # operator-only server-side copy seam
            destination._bucket.copy_blob(source_blob, destination._bucket, new_name=key, if_generation_match=0)
        except Exception as exc:  # noqa: BLE001 - provider details stay out of logs
            if "precondition" not in str(exc).lower() and exc.__class__.__name__ not in {"Conflict", "PreconditionFailed"}:
                raise PromotionError(f"server-side copy failed for {key}") from exc
    else:
        # Local qualification/test stores are allowed to use bounded bytes.
        try:
            data = archive.get(key)
            if len(data) != size or _sha256(data) != digest:
                raise PromotionError(f"archived object {key} changed during copy")
            destination.put_immutable(key, data, sha256=digest)
        except ObjectStoreError as exc:
            raise PromotionError(f"object copy failed for {key}") from exc
    try:
        final = destination.head(key)
    except ObjectStoreError as exc:
        raise PromotionError(f"destination object {key} disappeared") from exc
    _verify_metadata(final, digest=digest, size=size, label=f"destination object {key}")
    return False


def stage_archived_release(archive: ObjectStore, destination: ObjectStore, *, release_id: str, manifest: Mapping[str, Any], manifest_raw: bytes, checksum_raw: bytes) -> dict[str, int]:
    descriptors = tuple(manifest["objects"])
    reused = 0
    copied = 0
    def copy_one(descriptor: Mapping[str, Any]) -> bool:
        return _copy_immutable(
            archive,
            destination,
            key=f"objects/sha256/{descriptor['sha256']}",
            digest=str(descriptor["sha256"]),
            size=int(descriptor["bytes"]),
        )
    # Four workers are sufficient for GCS rewrite throughput while keeping
    # operator memory and request pressure bounded.
    with ThreadPoolExecutor(max_workers=4) as executor:
        for was_reused in executor.map(copy_one, descriptors):
            if was_reused:
                reused += 1
            else:
                copied += 1
    try:
        destination.put_immutable(f"releases/{release_id}/manifest.json", manifest_raw, sha256=_sha256(manifest_raw))
        destination.put_immutable(f"releases/{release_id}/manifest.sha256", checksum_raw, sha256=_sha256(checksum_raw))
    except ObjectStoreError as exc:
        raise PromotionError("release manifest publication failed") from exc
    return {"objectCount": len(descriptors), "objectsReused": reused, "objectsCopied": copied}


def _read_pointer(store: ObjectStore) -> tuple[ObjectMetadata, dict[str, Any], bytes]:
    before = store.head(_ACTIVE_KEY)
    raw = _read(store, _ACTIVE_KEY, maximum=64 * 1024)
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PromotionError("active pointer is invalid") from exc
    if not isinstance(value, dict) or value.get("schemaVersion") != _ACTIVE_SCHEMA or not isinstance(value.get("releaseId"), str):
        raise PromotionError("active pointer is invalid")
    try:
        metadata = store.head(_ACTIVE_KEY)
    except ObjectStoreError as exc:
        raise PromotionError("active pointer metadata is unavailable") from exc
    if metadata.etag != before.etag:
        raise PromotionError("active pointer changed during read")
    return metadata, value, raw


def _active_source_date(store: ObjectStore, release_id: str) -> str:
    raw = _read(store, f"releases/{release_id}/manifest.json", maximum=_MAX_MANIFEST_BYTES)
    checksum = _read(store, f"releases/{release_id}/manifest.sha256", maximum=4096)
    if not checksum.strip() or checksum.decode('ascii').split()[0] != _sha256(raw):
        raise PromotionError("previous release manifest checksum is invalid")
    try:
        value = json.loads(raw)
    except (ValueError, UnicodeError) as exc:
        raise PromotionError('previous release manifest is invalid') from exc
    if not isinstance(value, Mapping) or value.get('releaseId') != release_id:
        raise PromotionError("previous release identity does not match")
    source = value.get('source')
    date = source.get('releaseDate') if isinstance(source, Mapping) else None
    try:
        return dt.date.fromisoformat(date).isoformat()
    except (TypeError, ValueError) as exc:
        raise PromotionError("previous source date is invalid") from exc


def _pointer_bytes(release_id: str, previous: str | None) -> bytes:
    return _canonical({
        "lastKnownGoodReleaseId": release_id,
        "previousReleaseId": previous,
        "releaseId": release_id,
        "schemaVersion": _ACTIVE_SCHEMA,
    })


def _http_json(url: str, *, token: str, method: str = "GET", payload: Mapping[str, Any] | None = None) -> dict[str, Any]:
    if not isinstance(url, str) or not url.startswith("https://"):
        raise PromotionError("smoke URL must use HTTPS")
    body = None
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json", "User-Agent": "ValuePilot-release-smoke/1"}
    if payload is not None:
        body = _canonical(dict(payload))
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read(512 * 1024 + 1)
            if len(raw) > 512 * 1024:
                raise PromotionError("smoke response exceeds bound")
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
        raise PromotionError("production smoke request failed") from exc
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PromotionError("production smoke response is invalid") from exc
    if not isinstance(value, dict):
        raise PromotionError("production smoke response is invalid")
    return value


def run_production_smoke(url: str, *, release_id: str, token: str) -> dict[str, Any]:
    ready = _http_json(f"{url.rstrip('/')}/readyz", token=token)
    status = _http_json(f"{url.rstrip('/')}/v1/status", token=token)
    search = _http_json(
        f"{url.rstrip('/')}/v1/search",
        token=token,
        method="POST",
        payload={"latitude": "-34.6037", "longitude": "-58.3816", "radiusKm": "25", "query": "arroz", "page": 0},
    )
    for label, value in (("readyz", ready), ("status", status), ("search", search)):
        if value.get("releaseId") != release_id or value.get("freshness") != "FRESH":
            raise PromotionError(f"{label} did not prove the candidate release")
    if status.get("androidNetworkingAuthorized") is not True:
        raise PromotionError("status did not prove Android networking authorization for the candidate")
    return {"readyz": ready, "status": status, "search": {"matchCount": len(search.get("matches", [])), "pagination": search.get("pagination")}}


def _update_service(service: str, *, project: str, release_id: str, release_date: str) -> None:
    if ":" not in service:
        raise PromotionError("backend service must be NAME:REGION")
    name, region = service.split(":", 1)
    cloud_cli = shutil.which("gcloud") or "gcloud"
    completed = subprocess.run(
        [cloud_cli, "run", "services", "update", name, "--region", region, "--project", project, "--quiet", "--update-env-vars", f"VALUEPILOT_ANDROID_NETWORKING_RELEASE_ID={release_id},VALUEPILOT_ANDROID_NETWORKING_RELEASE_DATE={release_date}"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if completed.returncode != 0:
        raise PromotionError(f"backend service configuration failed for {name}")
    routed = subprocess.run(
        [cloud_cli, "run", "services", "update-traffic", name, "--region", region, "--project", project, "--quiet", "--to-latest"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if routed.returncode != 0:
        raise PromotionError(f"backend service traffic routing failed for {name}")


def promote_archived_release(
    archive: ObjectStore,
    destinations: Mapping[str, ObjectStore],
    *,
    release_id: str,
    expected_current_release_id: str,
    expected_release_date: str,
    expected_source_sha256: str,
    smoke: Callable[[str], Mapping[str, Any]] | None = None,
    smoke_targets: Mapping[str, str] | None = None,
    service_updates: Sequence[tuple[str, str, str]] = (),
) -> dict[str, Any]:
    if set(destinations) != {"us", "sa"}:
        raise PromotionError("exactly us and sa runtime stores are required")
    manifest, manifest_raw, checksum_raw = load_archived_manifest(
        archive,
        release_id=release_id,
        expected_release_date=expected_release_date,
        expected_source_sha256=expected_source_sha256,
    )
    pointers = {name: _read_pointer(store) for name, store in destinations.items()}
    if all(value[1].get("releaseId") == release_id for value in pointers.values()):
        for service, project, date in service_updates:
            _update_service(service, project=project, release_id=release_id, release_date=date)
        smoke_results = {name: dict(smoke(url)) for name, url in (smoke_targets or {}).items()} if smoke and smoke_targets else {}
        return {"status": "ALREADY_ACTIVE", "releaseId": release_id, "pointerWrites": 0, "staged": {}, "smoke": smoke_results}
    if any(value[1].get("releaseId") != expected_current_release_id for value in pointers.values()):
        raise PromotionError("runtime pointers do not match the expected current release")
    previous_date = _active_source_date(destinations['us'], expected_current_release_id)
    if expected_release_date < previous_date:
        raise PromotionError("candidate source is older than the active release")
    staged = {name: stage_archived_release(archive, store, release_id=release_id, manifest=manifest, manifest_raw=manifest_raw, checksum_raw=checksum_raw) for name, store in destinations.items()}
    candidate_pointer = _pointer_bytes(release_id, expected_current_release_id)
    prior = {name: value for name, value in pointers.items()}
    updated: dict[str, ObjectMetadata] = {}
    attempted_services: list[tuple[str, str]] = []
    try:
        for name in ("us", "sa"):
            updated[name] = destinations[name].compare_and_swap(_ACTIVE_KEY, candidate_pointer, expected_etag=prior[name][0].etag)
        for service, project, date in service_updates:
            attempted_services.append((service, project))
            _update_service(service, project=project, release_id=release_id, release_date=date)
        smoke_results = {name: dict(smoke(url)) for name, url in (smoke_targets or {}).items()} if smoke and smoke_targets else {}
    except Exception as exc:  # noqa: BLE001 - always compensate before surfacing failure
        rollback_errors: list[str] = []
        for name in reversed(tuple(updated)):
            try:
                destinations[name].compare_and_swap(_ACTIVE_KEY, prior[name][2], expected_etag=updated[name].etag)
            except Exception:
                rollback_errors.append(name)
        for service, project in reversed(attempted_services):
            try:
                _update_service(service, project=project, release_id=expected_current_release_id, release_date=previous_date)
            except Exception:
                rollback_errors.append("service:" + service)
        if rollback_errors:
            raise PromotionError("promotion failed and rollback requires operator attention: " + ",".join(rollback_errors)) from exc
        if isinstance(exc, PromotionError):
            raise
        raise PromotionError("promotion smoke or activation failed; previous pointers restored") from exc
    return {"status": "ACTIVE", "releaseId": release_id, "pointerWrites": 2, "staged": staged, "smoke": smoke_results}


def _gcs(bucket: str, project: str) -> GCSObjectStore:
    try:
        token = os.environ.get("VALUEPILOT_GCS_ACCESS_TOKEN", "").strip()
        if token:
            from google.oauth2.credentials import Credentials  # type: ignore

            return GCSObjectStore(bucket=bucket, project=project, credentials=Credentials(token=token))
        return GCSObjectStore(bucket=bucket, project=project)
    except ImportError as exc:
        raise PromotionError("Google authentication support is unavailable") from exc
    except ObjectStoreError as exc:
        raise PromotionError("GCS store is unavailable") from exc


def smoke_tokens(urls: Sequence[str], names: Sequence[str]) -> dict[str, str]:
    """Bind each short-lived token to its intended target before any mutation."""
    if len(set(urls)) != len(urls) or (names and len(names) != len(urls)):
        raise PromotionError("smoke token targets are misaligned")
    if names and any(re.fullmatch(r"VALUEPILOT_[A-Z0-9_]{1,100}", name) is None for name in names):
        raise PromotionError("smoke token environment name is invalid")
    requested = names or ["VALUEPILOT_SMOKE_ID_TOKEN"] * len(urls)
    result = {}
    for url, name in zip(urls, requested):
        if re.fullmatch(r"https://[a-z0-9-]+\.[a-z0-9-]+\.run\.app", url) is None:
            raise PromotionError("smoke target must be an exact Cloud Run HTTPS service URL")
        token = os.environ.get(name, "").strip()
        if not token:
            raise PromotionError("required smoke token is unavailable: " + name)
        result[url] = token
    return result


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--expected-current-release-id", required=True)
    parser.add_argument("--release-date", required=True)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--archive-bucket", required=True)
    parser.add_argument("--us-bucket", required=True)
    parser.add_argument("--sa-bucket", required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--smoke-url", action="append", default=[])
    parser.add_argument("--smoke-token-env", action="append", default=[])
    parser.add_argument("--service-update", action="append", default=[], help="Cloud Run service:region")
    parser.add_argument("--control-bucket")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        tokens = smoke_tokens(args.smoke_url, args.smoke_token_env)
        archive = _gcs(args.archive_bucket, args.project)
        destinations = {"us": _gcs(args.us_bucket, args.project), "sa": _gcs(args.sa_bucket, args.project)}
        smoke_targets = {f"region-{index}": url for index, url in enumerate(args.smoke_url)}
        smoke = (lambda url: run_production_smoke(url, release_id=args.release_id, token=tokens[url])) if smoke_targets else None
        updates = tuple((service, args.project, args.release_date) for service in args.service_update)
        result = promote_archived_release(
            archive,
            destinations,
            release_id=args.release_id,
            expected_current_release_id=args.expected_current_release_id,
            expected_release_date=args.release_date,
            expected_source_sha256=args.source_sha256,
            smoke=smoke,
            smoke_targets=smoke_targets,
            service_updates=updates,
        )
        if args.control_bucket:
            receipt = {"schemaVersion": "valuepilot-argentina-automated-promotion-v1", "releaseId": args.release_id, **result}
            control = _gcs(args.control_bucket, args.project)
            raw = _canonical(receipt) + b"\n"
            control.put_immutable(f"control/automated-promotions/{args.release_id}.json", raw, sha256=_sha256(raw))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(_canonical(result) + b"\n")
        print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except PromotionError as exc:
        value = {"status": "FAILED", "error": str(exc), "pointerWrites": 0}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(_canonical(value) + b"\n")
        print(json.dumps(value, separators=(",", ":")))
        return 2


__all__ = ["PromotionError", "load_archived_manifest", "promote_archived_release", "run_production_smoke", "stage_archived_release"]


if __name__ == "__main__":
    raise SystemExit(_main())

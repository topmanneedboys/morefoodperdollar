"""Operator-only publication of qualified content-addressed releases."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.object_store import ObjectStore, ObjectStoreError, S3CompatibleObjectStore, validate_key


class ObjectStorePublicationError(RuntimeError):
    pass


_HEX64 = re.compile(r"[0-9a-f]{64}\Z")
_RELEASE_ID = re.compile(r"[A-Za-z0-9._-]{1,128}\Z")


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _canonical(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _read_json(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, ValueError) as exc:
        raise ObjectStorePublicationError(f"{label} is invalid") from exc
    if not isinstance(value, dict):
        raise ObjectStorePublicationError(f"{label} is invalid")
    return value


def _verify_remote(store: ObjectStore, key: str, *, expected_sha256: str, expected_bytes: int) -> None:
    try:
        metadata = store.head(key)
    except ObjectStoreError as exc:
        raise ObjectStorePublicationError("published object is unavailable") from exc
    if metadata.size != expected_bytes:
        raise ObjectStorePublicationError("published object size mismatch")
    if metadata.sha256 == expected_sha256:
        return
    try:
        data = store.get(key)
    except ObjectStoreError as exc:
        raise ObjectStorePublicationError("published object verification failed") from exc
    if len(data) != expected_bytes or hashlib.sha256(data).hexdigest() != expected_sha256:
        raise ObjectStorePublicationError("published object hash mismatch")


class ObjectStoreReleasePublisher:
    """Publish with objects -> manifests -> control -> pointer-last ordering."""

    def __init__(self, store: ObjectStore):
        self.store = store

    @staticmethod
    def _release_ids(workspace: Path, release_ids: Sequence[str] | None) -> tuple[str, ...]:
        if release_ids is None:
            releases = workspace / "releases"
            try:
                values = tuple(sorted(path.name for path in releases.iterdir() if path.is_dir()))
            except OSError as exc:
                raise ObjectStorePublicationError("release directory is unavailable") from exc
        else:
            values = tuple(release_ids)
        if not values or any(_RELEASE_ID.fullmatch(value) is None for value in values):
            raise ObjectStorePublicationError("release IDs are invalid")
        return values

    @staticmethod
    def _manifest(workspace: Path, release_id: str) -> tuple[Path, bytes, dict[str, Any]]:
        path = workspace / "releases" / release_id / "manifest.json"
        checksum_path = path.with_name("manifest.sha256")
        try:
            raw = path.read_bytes()
            checksum = checksum_path.read_text(encoding="ascii")
        except (OSError, UnicodeDecodeError) as exc:
            raise ObjectStorePublicationError("release manifest is unavailable") from exc
        match = re.fullmatch(r"([0-9a-f]{64})  manifest\.json\n", checksum)
        if match is None or hashlib.sha256(raw).hexdigest() != match.group(1):
            raise ObjectStorePublicationError("release manifest checksum mismatch")
        value = _read_json(path, "release manifest")
        if value.get("completionState") != "COMPLETE" or value.get("releaseId") != release_id:
            raise ObjectStorePublicationError("release manifest is incomplete")
        return path, raw, value

    def _plan(self, workspace: Path, release_ids: Sequence[str], active_release_id: str) -> dict[str, Any]:
        if active_release_id not in release_ids:
            raise ObjectStorePublicationError("active release is not in the publication set")
        objects: list[dict[str, Any]] = []
        manifests: list[dict[str, Any]] = []
        seen: set[str] = set()
        for release_id in release_ids:
            _, manifest_raw, manifest = self._manifest(workspace, release_id)
            manifests.append({"releaseId": release_id, "bytes": len(manifest_raw), "sha256": hashlib.sha256(manifest_raw).hexdigest()})
            descriptors = manifest.get("objects")
            if not isinstance(descriptors, list):
                raise ObjectStorePublicationError("release manifest objects are invalid")
            for descriptor in sorted(descriptors, key=lambda item: str(item.get("path", "")) if isinstance(item, Mapping) else ""):
                if not isinstance(descriptor, Mapping):
                    raise ObjectStorePublicationError("release object descriptor is invalid")
                digest, size, logical_path = descriptor.get("sha256"), descriptor.get("bytes"), descriptor.get("path")
                if not isinstance(digest, str) or not _HEX64.fullmatch(digest) or not isinstance(size, int) or size < 0 or not isinstance(logical_path, str):
                    raise ObjectStorePublicationError("release object descriptor is invalid")
                try:
                    validate_key(logical_path)
                except ObjectStoreError as exc:
                    raise ObjectStorePublicationError("release object path is invalid") from exc
                if digest in seen:
                    continue
                source = workspace / "objects" / "sha256" / digest
                try:
                    source_size = source.stat().st_size
                except OSError as exc:
                    raise ObjectStorePublicationError("local object is unavailable") from exc
                if source_size != size or _sha256_file(source) != digest:
                    raise ObjectStorePublicationError("local object failed verification")
                seen.add(digest)
                objects.append({"key": f"objects/sha256/{digest}", "source": source, "bytes": size, "sha256": digest})
        control: list[dict[str, Any]] = []
        control_dir = workspace / "control"
        if control_dir.is_dir():
            for path in sorted(control_dir.iterdir()):
                if not path.is_file() or path.name in {"active.json", "publisher.lock"}:
                    continue
                key = f"control/{path.name}"
                validate_key(key)
                data = path.read_bytes()
                control.append({"key": key, "source": path, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        return {"releaseIds": list(release_ids), "activeReleaseId": active_release_id, "objects": objects, "manifests": manifests, "control": control, "objectCount": len(objects)}

    def publish(self, workspace: Path | str, *, release_ids: Sequence[str] | None = None, active_release_id: str | None = None, apply: bool = False) -> dict[str, Any]:
        root = Path(workspace).resolve()
        ids = self._release_ids(root, release_ids)
        active_id = active_release_id or ids[-1]
        plan = self._plan(root, ids, active_id)
        order = ["immutable_objects", "verify_objects", "manifests", "verify_manifests", "control_metadata", "active_pointer_last"]
        if not apply:
            return {**plan, "applied": False, "order": order}

        for item in plan["objects"]:
            try:
                self.store.put_immutable_file(item["key"], item["source"], sha256=item["sha256"])
            except ObjectStoreError as exc:
                raise ObjectStorePublicationError("immutable object publication failed") from exc
        for item in plan["objects"]:
            _verify_remote(self.store, item["key"], expected_sha256=item["sha256"], expected_bytes=item["bytes"])

        for release_id in ids:
            manifest_path, manifest_raw, _ = self._manifest(root, release_id)
            checksum_raw = manifest_path.with_name("manifest.sha256").read_bytes()
            for relative, payload in (("manifest.json", manifest_raw), ("manifest.sha256", checksum_raw)):
                key = f"releases/{release_id}/{relative}"
                try:
                    self.store.put_immutable(key, payload, sha256=hashlib.sha256(payload).hexdigest())
                except ObjectStoreError as exc:
                    raise ObjectStorePublicationError("manifest publication failed") from exc
            _verify_remote(self.store, f"releases/{release_id}/manifest.json", expected_sha256=hashlib.sha256(manifest_raw).hexdigest(), expected_bytes=len(manifest_raw))
            _verify_remote(self.store, f"releases/{release_id}/manifest.sha256", expected_sha256=hashlib.sha256(checksum_raw).hexdigest(), expected_bytes=len(checksum_raw))

        for item in plan["control"]:
            payload = item["source"].read_bytes()
            try:
                self.store.put_immutable(item["key"], payload, sha256=item["sha256"])
            except ObjectStoreError as exc:
                raise ObjectStorePublicationError("control metadata publication failed") from exc
            _verify_remote(self.store, item["key"], expected_sha256=item["sha256"], expected_bytes=item["bytes"])

        try:
            current = self.store.head("control/active.json") if self.store.exists("control/active.json") else None
            previous_id = None
            if current is not None:
                pointer = json.loads(self.store.get("control/active.json"))
                previous_id = pointer.get("releaseId") if isinstance(pointer, Mapping) else None
            pointer_raw = _canonical({"lastKnownGoodReleaseId": active_id, "previousReleaseId": previous_id, "releaseId": active_id, "schemaVersion": "valuepilot-active-release-v1"})
            self.store.compare_and_swap("control/active.json", pointer_raw, expected_etag=current.etag if current else None)
            _verify_remote(self.store, "control/active.json", expected_sha256=hashlib.sha256(pointer_raw).hexdigest(), expected_bytes=len(pointer_raw))
        except (ObjectStoreError, ValueError, TypeError) as exc:
            raise ObjectStorePublicationError("active pointer publication failed") from exc
        return {**plan, "applied": True, "activePointer": active_id, "order": order}


def _main() -> int:
    parser = argparse.ArgumentParser(description="Publish qualified ValuePilot releases to an S3-compatible ObjectStore")
    parser.add_argument("workspace", type=Path)
    parser.add_argument("--release-id", action="append", dest="release_ids")
    parser.add_argument("--active-release-id")
    parser.add_argument("--apply", action="store_true", help="perform writes; default is a deterministic dry-run")
    args = parser.parse_args()
    bucket = os.environ.get("VALUEPILOT_PUBLISH_BUCKET", "").strip()
    if not bucket:
        raise SystemExit("VALUEPILOT_PUBLISH_BUCKET is required")
    store = S3CompatibleObjectStore(bucket=bucket, endpoint_url=os.environ.get("VALUEPILOT_PUBLISH_ENDPOINT_URL") or None, region_name=os.environ.get("VALUEPILOT_PUBLISH_REGION") or None)
    result = ObjectStoreReleasePublisher(store).publish(args.workspace, release_ids=args.release_ids, active_release_id=args.active_release_id, apply=args.apply)
    print(json.dumps({key: value for key, value in result.items() if key not in {"objects", "control"}}, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = ["ObjectStorePublicationError", "ObjectStoreReleasePublisher"]

"""Plan, stage, verify, and activate immutable ValuePilot releases.

The publisher is intentionally split into two permanent phases:

* :meth:`stage` publishes only immutable release data and never touches the
  active pointer.
* :meth:`activate` verifies an already staged release and performs the one
  compare-and-swap of ``control/active.json``.

The implementation is operator-side tooling.  The request-serving runtime
gets only the read-only object-store capability and never imports this module.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import warnings
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.object_store import ObjectMetadata, ObjectStore, ObjectStoreError, S3CompatibleObjectStore, validate_key


class ObjectStorePublicationError(RuntimeError):
    """A release publication operation failed closed."""


_HEX64 = re.compile(r"[0-9a-f]{64}\Z")
_RELEASE_ID = re.compile(r"[A-Za-z0-9._-]{1,128}\Z")
_ACTIVE_KEY = "control/active.json"
_ACTIVE_SCHEMA_VERSION = "valuepilot-active-release-v1"
_ROUTING_SCHEMA_VERSION = "valuepilot-national-routing-v1"
_SEARCHPACK_SCHEMA_VERSION = "valuepilot-argentina-searchpack-v2"
_SEARCHPACK_FORMAT_VERSION = 2
_SEARCHPACK_POLICY_VERSION = "valuepilot-argentina-lexical-features-v1"


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


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


def _valid_release_id(value: Any) -> bool:
    return isinstance(value, str) and _RELEASE_ID.fullmatch(value) is not None


def _validate_digest(value: Any, label: str) -> str:
    if not isinstance(value, str) or _HEX64.fullmatch(value) is None:
        raise ObjectStorePublicationError(f"{label} is invalid")
    return value


def _validate_nonnegative_int(value: Any, label: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise ObjectStorePublicationError(f"{label} is invalid")
    return value


def _canonical_manifest_checksum(manifest_raw: bytes, sidecar_raw: bytes) -> bytes:
    """Validate a sidecar and return its platform-independent LF bytes.

    A few local Windows fixtures are written through ``Path.write_text`` and
    therefore contain CRLF.  The immutable publication contract is LF; we
    accept that harmless local representation but always publish the canonical
    bytes.
    """

    expected = f"{_sha256_bytes(manifest_raw)}  manifest.json\n".encode("ascii")
    if sidecar_raw == expected:
        return expected
    if sidecar_raw == expected.replace(b"\n", b"\r\n"):
        return expected
    raise ObjectStorePublicationError("release manifest checksum mismatch")


def _validate_manifest_declarations(manifest: Mapping[str, Any]) -> dict[str, dict[str, Any]]:
    """Validate optional derived-artifact declarations and return objects by path."""

    descriptors = manifest.get("objects")
    if not isinstance(descriptors, list):
        raise ObjectStorePublicationError("release manifest objects are invalid")
    by_path: dict[str, dict[str, Any]] = {}
    by_digest: dict[str, int] = {}
    for descriptor in descriptors:
        if not isinstance(descriptor, Mapping):
            raise ObjectStorePublicationError("release object descriptor is invalid")
        logical_path = descriptor.get("path")
        if not isinstance(logical_path, str):
            raise ObjectStorePublicationError("release object descriptor is invalid")
        try:
            validate_key(logical_path)
        except ObjectStoreError as exc:
            raise ObjectStorePublicationError("release object path is invalid") from exc
        if logical_path in by_path:
            raise ObjectStorePublicationError("release object paths are duplicated")
        digest = _validate_digest(descriptor.get("sha256"), "release object hash")
        size = _validate_nonnegative_int(descriptor.get("bytes"), "release object byte count")
        prior_size = by_digest.get(digest)
        if prior_size is not None and prior_size != size:
            raise ObjectStorePublicationError("release object hash has inconsistent byte counts")
        by_digest[digest] = size
        by_path[logical_path] = {"path": logical_path, "sha256": digest, "bytes": size}

    routing = manifest.get("routingArtifact")
    if "routingArtifact" in manifest:
        if not isinstance(routing, Mapping):
            raise ObjectStorePublicationError("routing artifact declaration is invalid")
        if routing.get("schemaVersion") != _ROUTING_SCHEMA_VERSION or routing.get("compression") != "gzip":
            raise ObjectStorePublicationError("routing artifact declaration is incompatible")
        path = routing.get("path")
        if not isinstance(path, str) or path not in by_path:
            raise ObjectStorePublicationError("routing artifact object is not pinned")
        if routing.get("sha256") != by_path[path]["sha256"] or routing.get("bytes") != by_path[path]["bytes"]:
            raise ObjectStorePublicationError("routing artifact object pin is inconsistent")
        _validate_digest(routing.get("sha256"), "routing artifact hash")
        _validate_digest(routing.get("uncompressedSha256"), "routing artifact uncompressed hash")
        _validate_nonnegative_int(routing.get("bytes"), "routing artifact byte count")
        _validate_nonnegative_int(routing.get("uncompressedBytes"), "routing artifact uncompressed byte count")
        _validate_nonnegative_int(routing.get("recordCount"), "routing artifact record count")
        regions = routing.get("regions")
        if (
            not isinstance(regions, list)
            or not regions
            or any(not isinstance(region, str) or not region for region in regions)
            or len(set(regions)) != len(regions)
            or regions != sorted(regions)
        ):
            raise ObjectStorePublicationError("routing artifact regions are invalid")
        source_release = routing.get("sourceReleaseId")
        if not _valid_release_id(source_release):
            raise ObjectStorePublicationError("routing artifact source release is invalid")

    searchpack = manifest.get("searchPackArtifact")
    if "searchPackArtifact" in manifest:
        if not isinstance(searchpack, Mapping):
            raise ObjectStorePublicationError("SearchPack declaration is invalid")
        if (
            searchpack.get("schemaVersion") != _SEARCHPACK_SCHEMA_VERSION
            or searchpack.get("formatVersion") != _SEARCHPACK_FORMAT_VERSION
            or searchpack.get("policyVersion") != _SEARCHPACK_POLICY_VERSION
        ):
            raise ObjectStorePublicationError("SearchPack declaration is incompatible")
        regions = searchpack.get("regions")
        if (
            not isinstance(regions, list)
            or not regions
            or any(not isinstance(region, str) or not region for region in regions)
            or len(set(regions)) != len(regions)
            or regions != sorted(regions)
        ):
            raise ObjectStorePublicationError("SearchPack regions are invalid")
        paths = searchpack.get("metadataPaths")
        hashes = searchpack.get("metadataSha256")
        if not isinstance(paths, Mapping) or not isinstance(hashes, Mapping) or set(paths) != set(regions) or set(hashes) != set(regions):
            raise ObjectStorePublicationError("SearchPack metadata index is invalid")
        for region in regions:
            metadata_path = paths.get(region)
            metadata_hash = hashes.get(region)
            if not isinstance(metadata_path, str) or metadata_path not in by_path:
                raise ObjectStorePublicationError("SearchPack metadata object is not pinned")
            if by_path[metadata_path]["sha256"] != metadata_hash:
                raise ObjectStorePublicationError("SearchPack metadata hash is inconsistent")
            _validate_digest(metadata_hash, "SearchPack metadata hash")
        source_release = searchpack.get("sourceReleaseId")
        if not _valid_release_id(source_release):
            raise ObjectStorePublicationError("SearchPack source release is invalid")
        source_hashes = searchpack.get("sourceSearchIndexSha256")
        if source_hashes is not None:
            if not isinstance(source_hashes, Mapping) or set(source_hashes) != set(regions):
                raise ObjectStorePublicationError("SearchPack source index map is invalid")
            for region in regions:
                _validate_digest(source_hashes.get(region), "SearchPack source index hash")
    return by_path


def _verify_remote(store: ObjectStore, key: str, *, expected_sha256: str, expected_bytes: int) -> ObjectMetadata:
    """Verify one immutable object, using exact content when metadata lacks SHA."""

    try:
        metadata = store.head(key)
    except ObjectStoreError as exc:
        raise ObjectStorePublicationError(f"published object is unavailable: {key}") from exc
    if metadata.size != expected_bytes:
        raise ObjectStorePublicationError(f"published object size mismatch: {key}")
    if metadata.sha256 is not None:
        if metadata.sha256 != expected_sha256:
            raise ObjectStorePublicationError(f"published object metadata hash mismatch: {key}")
        return metadata
    try:
        data = store.get(key)
    except ObjectStoreError as exc:
        raise ObjectStorePublicationError(f"published object verification failed: {key}") from exc
    if len(data) != expected_bytes or _sha256_bytes(data) != expected_sha256:
        raise ObjectStorePublicationError(f"published object hash mismatch: {key}")
    return metadata


class ObjectStoreReleasePublisher:
    """Two-phase immutable release controller.

    ``store`` is optional only for local ``plan`` calls.  ``stage``, ``verify``,
    and ``activate`` require an object store capability supplied by the
    operator.  No method in this class ever deletes an object.
    """

    def __init__(self, store: ObjectStore | None = None):
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
        if not values or any(not _valid_release_id(value) for value in values) or len(set(values)) != len(values):
            raise ObjectStorePublicationError("release IDs are invalid")
        return values

    @staticmethod
    def _manifest(workspace: Path, release_id: str) -> tuple[Path, bytes, dict[str, Any]]:
        path = workspace / "releases" / release_id / "manifest.json"
        checksum_path = path.with_name("manifest.sha256")
        try:
            raw = path.read_bytes()
            checksum_raw = checksum_path.read_bytes()
        except OSError as exc:
            raise ObjectStorePublicationError("release manifest is unavailable") from exc
        _canonical_manifest_checksum(raw, checksum_raw)
        value = _read_json(path, "release manifest")
        if value.get("completionState") != "COMPLETE" or value.get("releaseId") != release_id:
            raise ObjectStorePublicationError("release manifest is incomplete")
        _validate_manifest_declarations(value)
        return path, raw, value

    @staticmethod
    def _remote_manifest(store: ObjectStore, release_id: str) -> tuple[bytes, bytes, dict[str, Any]]:
        if not _valid_release_id(release_id):
            raise ObjectStorePublicationError("release ID is invalid")
        manifest_key = f"releases/{release_id}/manifest.json"
        checksum_key = f"releases/{release_id}/manifest.sha256"
        try:
            raw = store.get(manifest_key)
            sidecar = store.get(checksum_key)
        except ObjectStoreError as exc:
            raise ObjectStorePublicationError("release manifest is unavailable") from exc
        try:
            checksum = sidecar.decode("ascii")
        except UnicodeDecodeError as exc:
            raise ObjectStorePublicationError("release manifest checksum is invalid") from exc
        match = re.fullmatch(r"([0-9a-f]{64})  manifest\.json\n", checksum)
        if match is None or _sha256_bytes(raw) != match.group(1):
            raise ObjectStorePublicationError("release manifest checksum mismatch")
        try:
            value = json.loads(raw)
        except (UnicodeDecodeError, ValueError) as exc:
            raise ObjectStorePublicationError("release manifest is invalid") from exc
        if not isinstance(value, dict):
            raise ObjectStorePublicationError("release manifest is invalid")
        if value.get("releaseId") != release_id or value.get("completionState") != "COMPLETE":
            raise ObjectStorePublicationError("release manifest is incomplete or mismatched")
        _validate_manifest_declarations(value)
        return raw, sidecar, value

    @staticmethod
    def _local_object_items(workspace: Path, release_ids: Sequence[str]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        objects: list[dict[str, Any]] = []
        manifests: list[dict[str, Any]] = []
        seen: set[str] = set()
        for release_id in release_ids:
            manifest_path, manifest_raw, manifest = ObjectStoreReleasePublisher._manifest(workspace, release_id)
            checksum_raw = _canonical_manifest_checksum(manifest_raw, manifest_path.with_name("manifest.sha256").read_bytes())
            manifests.append(
                {
                    "releaseId": release_id,
                    "bytes": len(manifest_raw),
                    "sha256": _sha256_bytes(manifest_raw),
                    "checksumBytes": len(checksum_raw),
                    "checksumSha256": _sha256_bytes(checksum_raw),
                }
            )
            for descriptor in sorted(manifest["objects"], key=lambda item: str(item["path"])):
                digest = str(descriptor["sha256"])
                size = int(descriptor["bytes"])
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
        return objects, manifests

    @staticmethod
    def _local_control_items(workspace: Path) -> list[dict[str, Any]]:
        control: list[dict[str, Any]] = []
        control_dir = workspace / "control"
        if not control_dir.is_dir():
            return control
        try:
            paths = sorted(control_dir.iterdir())
        except OSError as exc:
            raise ObjectStorePublicationError("control directory is unavailable") from exc
        for path in paths:
            if not path.is_file() or path.name in {"active.json", "publisher.lock"}:
                continue
            key = f"control/{path.name}"
            try:
                validate_key(key)
                data = path.read_bytes()
            except (ObjectStoreError, OSError) as exc:
                raise ObjectStorePublicationError("control metadata is unavailable") from exc
            control.append({"key": key, "source": path, "bytes": len(data), "sha256": _sha256_bytes(data)})
        return control

    def _build_plan(
        self,
        workspace: Path,
        release_ids: Sequence[str],
        intended_active_release_id: str | None,
        *,
        remote_store: ObjectStore | None,
    ) -> dict[str, Any]:
        if intended_active_release_id is not None:
            if not _valid_release_id(intended_active_release_id) or intended_active_release_id not in release_ids:
                raise ObjectStorePublicationError("intended active release is not in the publication set")
        objects, manifests = self._local_object_items(workspace, release_ids)
        control = self._local_control_items(workspace)
        object_bytes = sum(item["bytes"] for item in objects)
        manifest_bytes = sum(item["bytes"] for item in manifests)
        manifest_checksum_bytes = sum(item["checksumBytes"] for item in manifests)
        control_bytes = sum(item["bytes"] for item in control)

        remote_known = remote_store is not None
        new_count: int | None = 0 if remote_known else None
        reused_count: int | None = 0 if remote_known else None
        if remote_store is not None:
            for item in objects:
                try:
                    present = remote_store.exists(item["key"])
                except ObjectStoreError as exc:
                    raise ObjectStorePublicationError("remote object inventory is unavailable") from exc
                if present:
                    _verify_remote(remote_store, item["key"], expected_sha256=item["sha256"], expected_bytes=item["bytes"])
                    reused_count += 1
                else:
                    new_count += 1
            # Preflight every other immutable publication key too.  A
            # conflicting manifest or control artifact must fail before any
            # object upload, so a stage can never partially publish a release
            # merely because a late immutable key differed.
            for manifest_info in manifests:
                release_id = manifest_info["releaseId"]
                manifest_path, manifest_raw, _ = self._manifest(workspace, release_id)
                checksum_raw = _canonical_manifest_checksum(manifest_raw, manifest_path.with_name("manifest.sha256").read_bytes())
                for key, payload in (
                    (f"releases/{release_id}/manifest.json", manifest_raw),
                    (f"releases/{release_id}/manifest.sha256", checksum_raw),
                ):
                    if remote_store.exists(key):
                        _verify_remote(remote_store, key, expected_sha256=_sha256_bytes(payload), expected_bytes=len(payload))
            for item in control:
                if remote_store.exists(item["key"]):
                    _verify_remote(remote_store, item["key"], expected_sha256=item["sha256"], expected_bytes=item["bytes"])

        total_bytes = object_bytes + manifest_bytes + manifest_checksum_bytes + control_bytes
        return {
            "operation": "PLAN",
            "status": "READY",
            "releaseIds": list(release_ids),
            "activeReleaseId": intended_active_release_id,
            "intendedActiveReleaseId": intended_active_release_id,
            "objects": objects,
            "manifests": manifests,
            "control": control,
            "objectCount": len(objects),
            "manifestCount": len(manifests),
            "objectBytes": object_bytes,
            "manifestBytes": manifest_bytes,
            "manifestChecksumBytes": manifest_checksum_bytes,
            "controlBytes": control_bytes,
            "bytes": total_bytes,
            "newObjectCount": new_count,
            "newImmutableObjectCount": new_count,
            "reusedObjectCount": reused_count,
            "reusedImmutableObjectCount": reused_count,
            "remoteInventoryKnown": remote_known,
            "deleteCount": 0,
            "remoteDeleteCount": 0,
            "remoteWriteCount": 0,
            "activePointerChanged": False,
            "activeWriteCount": 0,
        }

    def plan(
        self,
        workspace: Path | str,
        *,
        release_ids: Sequence[str] | None = None,
        active_release_id: str | None = None,
        remote_store: ObjectStore | None = None,
    ) -> dict[str, Any]:
        """Build a deterministic, write-free publication plan."""

        root = Path(workspace).resolve()
        ids = self._release_ids(root, release_ids)
        store = self.store if remote_store is None else remote_store
        return self._build_plan(root, ids, active_release_id, remote_store=store)

    @staticmethod
    def _put_file(store: ObjectStore, item: Mapping[str, Any], *, label: str) -> None:
        try:
            store.put_immutable_file(item["key"], item["source"], sha256=item["sha256"])
        except ObjectStoreError as exc:
            raise ObjectStorePublicationError(f"{label} publication failed") from exc

    @staticmethod
    def _put_bytes(store: ObjectStore, key: str, payload: bytes, *, label: str) -> None:
        try:
            store.put_immutable(key, payload, sha256=_sha256_bytes(payload))
        except ObjectStoreError as exc:
            raise ObjectStorePublicationError(f"{label} publication failed") from exc

    def stage(
        self,
        workspace: Path | str,
        *,
        release_ids: Sequence[str] | None = None,
        active_release_id: str | None = None,
    ) -> dict[str, Any]:
        """Publish immutable objects/manifests without reading or writing ACTIVE."""

        if self.store is None:
            raise ObjectStorePublicationError("object store is required for stage")
        root = Path(workspace).resolve()
        ids = self._release_ids(root, release_ids)
        plan = self._build_plan(root, ids, active_release_id, remote_store=self.store)

        for item in plan["objects"]:
            self._put_file(self.store, item, label="immutable object")
        for item in plan["objects"]:
            _verify_remote(self.store, item["key"], expected_sha256=item["sha256"], expected_bytes=item["bytes"])

        for release_id in ids:
            manifest_path, manifest_raw, _ = self._manifest(root, release_id)
            checksum_raw = _canonical_manifest_checksum(manifest_raw, manifest_path.with_name("manifest.sha256").read_bytes())
            manifest_key = f"releases/{release_id}/manifest.json"
            checksum_key = f"releases/{release_id}/manifest.sha256"
            self._put_bytes(self.store, manifest_key, manifest_raw, label="manifest")
            self._put_bytes(self.store, checksum_key, checksum_raw, label="manifest checksum")
            _verify_remote(self.store, manifest_key, expected_sha256=_sha256_bytes(manifest_raw), expected_bytes=len(manifest_raw))
            _verify_remote(self.store, checksum_key, expected_sha256=_sha256_bytes(checksum_raw), expected_bytes=len(checksum_raw))

        for item in plan["control"]:
            payload = item["source"].read_bytes()
            self._put_bytes(self.store, item["key"], payload, label="control metadata")
            _verify_remote(self.store, item["key"], expected_sha256=item["sha256"], expected_bytes=item["bytes"])

        # There is deliberately no reference to ACTIVE in this code path.  A
        # future refactor that wants to update it must go through activate().
        return {
            **plan,
            "operation": "STAGE",
            "status": "STAGED",
            "activePointerChanged": False,
            "activeWriteCount": 0,
            "remoteWriteCount": len(plan["objects"]) + (2 * len(plan["manifests"])) + len(plan["control"]),
            "remoteDeleteCount": 0,
            "deleteCount": 0,
        }

    def verify(self, release_id: str) -> dict[str, Any]:
        """Read-only verification of one remote, complete release."""

        if self.store is None:
            raise ObjectStorePublicationError("object store is required for verify")
        manifest_raw, checksum_raw, manifest = self._remote_manifest(self.store, release_id)
        objects = manifest["objects"]
        seen: set[str] = set()
        object_bytes = 0
        for descriptor in sorted(objects, key=lambda item: str(item["path"])):
            digest = str(descriptor["sha256"])
            if digest in seen:
                continue
            seen.add(digest)
            size = int(descriptor["bytes"])
            _verify_remote(self.store, f"objects/sha256/{digest}", expected_sha256=digest, expected_bytes=size)
            object_bytes += size
        return {
            "operation": "VERIFY",
            "status": "PASS",
            "releaseId": release_id,
            "objectCount": len(seen),
            "manifestCount": 1,
            "objectBytes": object_bytes,
            "manifestBytes": len(manifest_raw),
            "manifestChecksumBytes": len(checksum_raw),
            "bytes": object_bytes + len(manifest_raw) + len(checksum_raw),
            "deleteCount": 0,
            "remoteDeleteCount": 0,
            "remoteWriteCount": 0,
            "activePointerChanged": False,
            "activeWriteCount": 0,
        }

    @staticmethod
    def _read_active(store: ObjectStore) -> tuple[ObjectMetadata | None, dict[str, Any] | None]:
        try:
            present = store.exists(_ACTIVE_KEY)
        except ObjectStoreError as exc:
            raise ObjectStorePublicationError("active pointer is unavailable") from exc
        if not present:
            return None, None
        try:
            metadata = store.head(_ACTIVE_KEY)
            raw = store.get(_ACTIVE_KEY)
            value = json.loads(raw)
        except (ObjectStoreError, UnicodeDecodeError, ValueError) as exc:
            raise ObjectStorePublicationError("active pointer is invalid") from exc
        if not isinstance(value, Mapping) or not _valid_release_id(value.get("releaseId")):
            raise ObjectStorePublicationError("active pointer is invalid")
        return metadata, dict(value)

    def activate(self, release_id: str, *, expected_current_release_id: str | None = None) -> dict[str, Any]:
        """Verify and atomically select an already staged release."""

        if self.store is None:
            raise ObjectStorePublicationError("object store is required for activate")
        if not _valid_release_id(release_id):
            raise ObjectStorePublicationError("release ID is invalid")
        if expected_current_release_id is not None and not _valid_release_id(expected_current_release_id):
            raise ObjectStorePublicationError("expected active release ID is invalid")

        # This is intentionally the first operation: activation can never
        # move ACTIVE toward an incomplete or partially materialized release.
        verification = self.verify(release_id)
        current_metadata, current_pointer = self._read_active(self.store)
        current_release_id = current_pointer.get("releaseId") if current_pointer else None
        if expected_current_release_id is not None and current_release_id != expected_current_release_id:
            raise ObjectStorePublicationError("expected active release does not match current active release")
        if current_release_id == release_id:
            return {
                **verification,
                "operation": "ACTIVATE",
                "status": "ALREADY_ACTIVE",
                "previousReleaseId": current_release_id,
                "activeReleaseId": release_id,
                "activePointerChanged": False,
                "activeWriteCount": 0,
                "remoteWriteCount": 0,
            }

        pointer = {
            "lastKnownGoodReleaseId": release_id,
            "previousReleaseId": current_release_id,
            "releaseId": release_id,
            "schemaVersion": _ACTIVE_SCHEMA_VERSION,
        }
        pointer_raw = _canonical(pointer)
        try:
            self.store.compare_and_swap(_ACTIVE_KEY, pointer_raw, expected_etag=current_metadata.etag if current_metadata else None)
            _verify_remote(self.store, _ACTIVE_KEY, expected_sha256=_sha256_bytes(pointer_raw), expected_bytes=len(pointer_raw))
        except (ObjectStoreError, ObjectStorePublicationError) as exc:
            # No fallback PUT exists.  A failed CAS leaves the prior pointer
            # untouched in stores implementing the ObjectStore contract.
            raise ObjectStorePublicationError("active pointer publication failed") from exc
        return {
            **verification,
            "operation": "ACTIVATE",
            "status": "ACTIVE",
            "previousReleaseId": current_release_id,
            "activeReleaseId": release_id,
            "activePointerChanged": True,
            "activeWriteCount": 1,
            "remoteWriteCount": 1,
            "remoteDeleteCount": 0,
            "deleteCount": 0,
        }

    def publish(
        self,
        workspace: Path | str,
        *,
        release_ids: Sequence[str] | None = None,
        active_release_id: str | None = None,
        apply: bool = False,
    ) -> dict[str, Any]:
        """Deprecated compatibility wrapper for older tooling/tests.

        New callers must use ``plan``, ``stage``, and ``activate`` explicitly.
        The wrapper remains only so older qualification tooling can migrate
        without changing release bytes.  Its CLI spelling has been removed.
        """

        warnings.warn("publish() is deprecated; use plan(), stage(), and activate()", DeprecationWarning, stacklevel=2)
        root = Path(workspace).resolve()
        ids = self._release_ids(root, release_ids)
        intended = active_release_id or ids[-1]
        if not apply:
            # Keep the historical dry-run write-free even when a publisher was
            # constructed with a store.  The explicit plan() can opt into
            # remote inventory through its publisher store.
            result = self._build_plan(root, ids, intended, remote_store=None)
            return {**result, "applied": False, "order": ["immutable_objects", "verify_objects", "manifests", "verify_manifests", "control_metadata", "active_pointer_last"]}
        stage_result = self.stage(root, release_ids=ids, active_release_id=intended)
        activation = self.activate(intended)
        return {
            **stage_result,
            "applied": True,
            "activePointer": intended,
            "activePointerChanged": activation["activePointerChanged"],
            "activeWriteCount": activation["activeWriteCount"],
            "remoteWriteCount": stage_result["remoteWriteCount"] + activation["remoteWriteCount"],
            "remoteDeleteCount": 0,
            "deleteCount": 0,
            "order": ["immutable_objects", "verify_objects", "manifests", "verify_manifests", "control_metadata", "active_pointer_last"],
        }


def _store_from_environment(*, write: bool) -> S3CompatibleObjectStore:
    prefix = "VALUEPILOT_PUBLISH_" if write else "VALUEPILOT_RELEASE_"
    bucket = os.environ.get(f"{prefix}BUCKET", "").strip()
    if not bucket and not write:
        # A local operator may intentionally use the publication bucket with a
        # read-only credential for verification; this fallback does not grant
        # the runtime any write capability.
        bucket = os.environ.get("VALUEPILOT_PUBLISH_BUCKET", "").strip()
        prefix = "VALUEPILOT_PUBLISH_"
    if not bucket:
        variable = "VALUEPILOT_PUBLISH_BUCKET" if write else "VALUEPILOT_RELEASE_BUCKET"
        raise ObjectStorePublicationError(f"{variable} is required")
    return S3CompatibleObjectStore(
        bucket=bucket,
        endpoint_url=os.environ.get(f"{prefix}ENDPOINT_URL") or None,
        region_name=os.environ.get(f"{prefix}REGION") or None,
    )


def _display_result(result: Mapping[str, Any]) -> None:
    filtered = {key: value for key, value in result.items() if key not in {"objects", "control"}}
    print(json.dumps(filtered, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str))


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Operate the two-phase ValuePilot immutable release controller")
    subparsers = parser.add_subparsers(dest="operation", required=True)

    plan_parser = subparsers.add_parser("plan", help="build a local, write-free publication plan")
    plan_parser.add_argument("workspace", type=Path)
    plan_parser.add_argument("--release-id", action="append", dest="release_ids")
    plan_parser.add_argument("--active-release-id")

    stage_parser = subparsers.add_parser("stage", help="publish immutable objects and manifests; never ACTIVE")
    stage_parser.add_argument("workspace", type=Path)
    stage_parser.add_argument("--release-id", action="append", dest="release_ids")
    stage_parser.add_argument("--active-release-id", help="intended target recorded in the report only")

    verify_parser = subparsers.add_parser("verify", help="read-only verify one remote release")
    verify_parser.add_argument("release_id")

    activate_parser = subparsers.add_parser("activate", help="verify then CAS one staged release into ACTIVE")
    activate_parser.add_argument("release_id")
    activate_parser.add_argument("--expected-current-release-id")

    args = parser.parse_args(argv)
    try:
        if args.operation == "plan":
            result = ObjectStoreReleasePublisher().plan(args.workspace, release_ids=args.release_ids, active_release_id=args.active_release_id)
        elif args.operation == "stage":
            result = ObjectStoreReleasePublisher(_store_from_environment(write=True)).stage(
                args.workspace, release_ids=args.release_ids, active_release_id=args.active_release_id
            )
        elif args.operation == "verify":
            result = ObjectStoreReleasePublisher(_store_from_environment(write=False)).verify(args.release_id)
        else:
            result = ObjectStoreReleasePublisher(_store_from_environment(write=True)).activate(
                args.release_id, expected_current_release_id=args.expected_current_release_id
            )
    except ObjectStorePublicationError as exc:
        print(f"release operation failed: {exc}", file=__import__("sys").stderr)
        return 2
    _display_result(result)
    return 0


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = ["ObjectStorePublicationError", "ObjectStoreReleasePublisher"]

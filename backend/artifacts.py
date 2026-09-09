"""Verified release-artifact views over local or object storage.

The release manifest is the only authority that maps a logical artifact path
to a content-addressed object.  Runtime callers never choose arbitrary object
keys and every read is checked against the manifest's exact byte count and
SHA-256 digest.
"""

from __future__ import annotations

import hashlib
import os
import re
import tempfile
import threading
from pathlib import Path
from typing import Any, Mapping, Protocol

from .object_store import LocalFilesystemObjectStore, ObjectMetadata, ObjectStore, ObjectStoreError, validate_key


class ReleaseArtifactError(RuntimeError):
    """An immutable release artifact could not be verified or read."""


class ReleaseArtifactStore(Protocol):
    """Read-only artifact contract used by the backend reader."""

    remote: bool

    def read(self, path: str, *, expected_sha256: str | None = None, expected_bytes: int | None = None) -> bytes: ...

    def read_range(self, path: str, offset: int, length: int) -> bytes: ...


_HEX64 = re.compile(r"[0-9a-f]{64}\Z")


def _digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _verify_bytes(data: bytes, *, expected_sha256: str | None, expected_bytes: int | None, label: str) -> bytes:
    if expected_bytes is not None and len(data) != expected_bytes:
        raise ReleaseArtifactError(f"{label} byte count mismatch")
    if expected_sha256 is not None and _digest(data) != expected_sha256:
        raise ReleaseArtifactError(f"{label} hash mismatch")
    return data


def _atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


class FilesystemReleaseArtifactStore:
    """Direct-file release view retained for local qualification and tests."""

    remote = False

    def __init__(self, root: Path | str):
        self.root = Path(root).resolve()
        self._store = LocalFilesystemObjectStore(self.root)

    def _key(self, path: str) -> str:
        try:
            return validate_key(path)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact path is invalid") from exc

    def read(self, path: str, *, expected_sha256: str | None = None, expected_bytes: int | None = None) -> bytes:
        key = self._key(path)
        try:
            data = self._store.get(key)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact is unavailable") from exc
        return _verify_bytes(data, expected_sha256=expected_sha256, expected_bytes=expected_bytes, label=key)

    def read_range(self, path: str, offset: int, length: int) -> bytes:
        key = self._key(path)
        try:
            return self._store.get_range(key, offset, length)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact range is unavailable") from exc

    def materialize(self, path: str, *, sparse: bool = False) -> Path:
        del sparse
        key = self._key(path)
        value = (self.root / Path(*key.split("/"))).resolve()
        if self.root not in value.parents or not value.is_file():
            raise ReleaseArtifactError("release artifact is unavailable")
        return value


class ManifestReleaseArtifactStore:
    """Manifest-derived view over a provider-neutral ObjectStore.

    ``remote`` is true even when the supplied ObjectStore is local: this view
    deliberately exercises the same content-addressed publication contract in
    local tests without changing the production data layout.
    """

    remote = True

    def __init__(self, store: ObjectStore, manifest: Mapping[str, Any], *, release_id: str, cache_root: Path | str | None = None):
        self.store = store
        self.release_id = release_id
        self.cache_root = Path(cache_root).resolve() if cache_root is not None else Path(tempfile.mkdtemp(prefix=f"valuepilot-release-{release_id}-")).resolve()
        self._descriptors: dict[str, tuple[str, int]] = {}
        self._relative_prefixes: tuple[str, ...] = ()
        self._verified_objects: set[str] = set()
        self._lock = threading.RLock()
        objects = manifest.get("objects")
        if not isinstance(objects, list):
            raise ReleaseArtifactError("release manifest objects are invalid")
        for raw in objects:
            if not isinstance(raw, Mapping):
                raise ReleaseArtifactError("release object descriptor is invalid")
            path = raw.get("path")
            digest = raw.get("sha256")
            size = raw.get("bytes")
            if not isinstance(path, str) or not isinstance(digest, str) or not _HEX64.fullmatch(digest) or not isinstance(size, int) or size < 0:
                raise ReleaseArtifactError("release object descriptor is invalid")
            try:
                path = validate_key(path)
            except ObjectStoreError as exc:
                raise ReleaseArtifactError("release object path is invalid") from exc
            if path in self._descriptors:
                raise ReleaseArtifactError("release manifest contains duplicate paths")
            self._descriptors[path] = (digest, size)
        prefixes = {
            path.split("/", 1)[0]
            for path in self._descriptors
            if path.startswith("micro-") and "/regions/" in path
        }
        self._relative_prefixes = tuple(sorted(prefixes))

    def _descriptor(self, path: str) -> tuple[str, int]:
        try:
            path = validate_key(path)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact path is invalid") from exc
        try:
            return self._descriptors[path]
        except KeyError:
            for prefix in self._relative_prefixes:
                candidate = f"{prefix}/{path}"
                if candidate in self._descriptors:
                    return self._descriptors[candidate]
            raise ReleaseArtifactError("release artifact is not in the pinned manifest")

    @staticmethod
    def _object_key(digest: str) -> str:
        return f"objects/sha256/{digest}"

    def _verify_remote_metadata(self, path: str, digest: str, expected_bytes: int) -> ObjectMetadata:
        key = self._object_key(digest)
        try:
            metadata = self.store.head(key)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError(f"{path} object metadata is unavailable") from exc
        if metadata.size != expected_bytes:
            raise ReleaseArtifactError(f"{path} object byte count mismatch")
        # The publisher stores the content digest as object metadata.  If a
        # provider does not preserve that metadata, force one full verified
        # read before allowing range reads; never trust an ETag as SHA-256.
        if metadata.sha256 == digest:
            with self._lock:
                self._verified_objects.add(digest)
            return metadata
        with self._lock:
            already_verified = digest in self._verified_objects
        if not already_verified:
            try:
                data = self.store.get(key)
            except ObjectStoreError as exc:
                raise ReleaseArtifactError(f"{path} object verification read failed") from exc
            _verify_bytes(data, expected_sha256=digest, expected_bytes=expected_bytes, label=path)
            with self._lock:
                self._verified_objects.add(digest)
        return metadata

    def read(self, path: str, *, expected_sha256: str | None = None, expected_bytes: int | None = None) -> bytes:
        digest, size = self._descriptor(path)
        if expected_sha256 is not None and expected_sha256 != digest:
            raise ReleaseArtifactError("release artifact hash disagrees with manifest")
        if expected_bytes is not None and expected_bytes != size:
            raise ReleaseArtifactError("release artifact size disagrees with manifest")
        self._verify_remote_metadata(path, digest, size)
        try:
            data = self.store.get(self._object_key(digest))
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact read failed") from exc
        return _verify_bytes(data, expected_sha256=digest, expected_bytes=size, label=path)

    def read_range(self, path: str, offset: int, length: int) -> bytes:
        digest, size = self._descriptor(path)
        if not isinstance(offset, int) or not isinstance(length, int) or offset < 0 or length < 0 or offset + length > size:
            raise ReleaseArtifactError("release artifact range is invalid")
        self._verify_remote_metadata(path, digest, size)
        try:
            data = self.store.get_range(self._object_key(digest), offset, length)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact range read failed") from exc
        if len(data) != length:
            raise ReleaseArtifactError("release artifact range is truncated")
        return data

    def materialize(self, path: str, *, sparse: bool = False) -> Path:
        digest, size = self._descriptor(path)
        target = (self.cache_root / Path(*validate_key(path).split("/"))).resolve()
        if self.cache_root not in target.parents:
            raise ReleaseArtifactError("release artifact cache path escapes root")
        if sparse:
            self._verify_remote_metadata(path, digest, size)
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.is_file() or target.stat().st_size != size:
                with target.open("wb") as handle:
                    handle.truncate(size)
            return target
        data = self.read(path, expected_sha256=digest, expected_bytes=size)
        if not target.is_file() or target.stat().st_size != len(data) or _digest(target.read_bytes()) != digest:
            _atomic_write(target, data)
        return target


__all__ = ["FilesystemReleaseArtifactStore", "ManifestReleaseArtifactStore", "ReleaseArtifactError", "ReleaseArtifactStore"]

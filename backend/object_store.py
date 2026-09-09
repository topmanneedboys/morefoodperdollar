"""Provider-neutral immutable object storage primitives.

The runtime only receives manifest-derived keys.  This module nevertheless
validates every key so an accidental caller cannot turn a backend request into
arbitrary filesystem access.  S3-compatible support is intentionally a thin
adapter over the standard boto3 client; no Cloudflare-specific behavior lives
here.
"""

from __future__ import annotations

import hashlib
import os
import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Protocol


class ObjectStoreError(RuntimeError):
    """A storage operation failed closed."""


@dataclass(frozen=True)
class ObjectMetadata:
    key: str
    size: int
    etag: str
    sha256: str | None = None
    metadata: Mapping[str, str] = ()


class ObjectStore(Protocol):
    def head(self, key: str) -> ObjectMetadata: ...
    def get(self, key: str) -> bytes: ...
    def get_range(self, key: str, offset: int, length: int) -> bytes: ...
    def exists(self, key: str) -> bool: ...
    def put_immutable(self, key: str, data: bytes, *, sha256: str | None = None) -> ObjectMetadata: ...
    def put_immutable_file(self, key: str, source: Path | str, *, sha256: str | None = None) -> ObjectMetadata: ...
    def compare_and_swap(self, key: str, data: bytes, *, expected_etag: str | None) -> ObjectMetadata: ...


def validate_key(key: str) -> str:
    if not isinstance(key, str) or not key or len(key) > 1024:
        raise ObjectStoreError("object key is invalid")
    if "\\" in key or key.startswith("/") or "\x00" in key:
        raise ObjectStoreError("object key is invalid")
    parts = key.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        raise ObjectStoreError("object key is invalid")
    return key


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class LocalFilesystemObjectStore:
    """Small deterministic object store for local qualification and tests."""

    def __init__(self, root: Path | str):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()

    def _path(self, key: str) -> Path:
        key = validate_key(key)
        path = (self.root / Path(*key.split("/"))).resolve()
        if self.root not in path.parents:
            raise ObjectStoreError("object key escapes store root")
        return path

    def _metadata(self, key: str, path: Path) -> ObjectMetadata:
        digest = _sha256_file(path)
        return ObjectMetadata(key, path.stat().st_size, digest, digest, ())

    def head(self, key: str) -> ObjectMetadata:
        path = self._path(key)
        try:
            if not path.is_file():
                raise ObjectStoreError("object does not exist")
            return self._metadata(key, path)
        except OSError as exc:
            raise ObjectStoreError("object metadata unavailable") from exc

    def get(self, key: str) -> bytes:
        path = self._path(key)
        try:
            data = path.read_bytes()
        except OSError as exc:
            raise ObjectStoreError("object does not exist") from exc
        if not path.is_file():
            raise ObjectStoreError("object does not exist")
        return data

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        if not isinstance(offset, int) or not isinstance(length, int) or offset < 0 or length < 0:
            raise ObjectStoreError("range is invalid")
        path = self._path(key)
        try:
            with path.open("rb") as handle:
                handle.seek(offset)
                data = handle.read(length)
        except OSError as exc:
            raise ObjectStoreError("range read failed") from exc
        if len(data) != length:
            raise ObjectStoreError("range is truncated")
        return data

    def exists(self, key: str) -> bool:
        return self._path(key).is_file()

    def put_immutable(self, key: str, data: bytes, *, sha256: str | None = None) -> ObjectMetadata:
        if not isinstance(data, bytes):
            raise ObjectStoreError("object data must be bytes")
        digest = _sha256(data)
        if sha256 is not None and digest != sha256:
            raise ObjectStoreError("object hash mismatch")
        path = self._path(key)
        with self._lock:
            if path.exists():
                existing = path.read_bytes()
                if existing != data:
                    raise ObjectStoreError("immutable object differs")
                return ObjectMetadata(key, len(data), digest, digest, ())
            path.parent.mkdir(parents=True, exist_ok=True)
            fd, temporary_name = tempfile.mkstemp(prefix=".partial-", dir=str(path.parent))
            temporary = Path(temporary_name)
            try:
                with os.fdopen(fd, "wb") as handle:
                    handle.write(data)
                    handle.flush()
                    os.fsync(handle.fileno())
                os.replace(temporary, path)
            finally:
                temporary.unlink(missing_ok=True)
        return ObjectMetadata(key, len(data), digest, digest, ())

    def put_immutable_file(self, key: str, source: Path | str, *, sha256: str | None = None) -> ObjectMetadata:
        """Publish one immutable file without loading it into memory."""

        key = validate_key(key)
        source_path = Path(source)
        try:
            size = source_path.stat().st_size
            digest = _sha256_file(source_path)
        except OSError as exc:
            raise ObjectStoreError("object source is unavailable") from exc
        if sha256 is not None and digest != sha256:
            raise ObjectStoreError("object hash mismatch")
        path = self._path(key)
        with self._lock:
            if path.exists():
                existing = self._metadata(key, path)
                if existing.size != size or existing.sha256 != digest:
                    raise ObjectStoreError("immutable object differs")
                return existing
            path.parent.mkdir(parents=True, exist_ok=True)
            fd, temporary_name = tempfile.mkstemp(prefix=".partial-", dir=str(path.parent))
            temporary = Path(temporary_name)
            try:
                with os.fdopen(fd, "wb") as target, source_path.open("rb") as source_handle:
                    for chunk in iter(lambda: source_handle.read(1024 * 1024), b""):
                        target.write(chunk)
                    target.flush()
                    os.fsync(target.fileno())
                os.replace(temporary, path)
            finally:
                temporary.unlink(missing_ok=True)
        return ObjectMetadata(key, size, digest, digest, ())

    def compare_and_swap(self, key: str, data: bytes, *, expected_etag: str | None) -> ObjectMetadata:
        path = self._path(key)
        with self._lock:
            actual = _sha256(path.read_bytes()) if path.is_file() else None
            if actual != expected_etag:
                raise ObjectStoreError("conditional pointer update failed")
            return self.put_immutable(key, data) if expected_etag is None else self._replace(key, data)

    def _replace(self, key: str, data: bytes) -> ObjectMetadata:
        path = self._path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_name(f".{path.name}.partial")
        temporary.write_bytes(data)
        os.replace(temporary, path)
        digest = _sha256(data)
        return ObjectMetadata(key, len(data), digest, digest, ())


class S3CompatibleObjectStore:
    """S3-compatible object store adapter (works with R2 without R2 logic)."""

    def __init__(self, *, bucket: str, client: Any | None = None, endpoint_url: str | None = None, region_name: str | None = None):
        self.bucket = bucket
        if client is None:
            try:
                import boto3  # type: ignore
            except ImportError as exc:  # pragma: no cover - exercised in deployments
                raise ObjectStoreError("boto3 is required for S3-compatible storage") from exc
            client = boto3.client("s3", endpoint_url=endpoint_url, region_name=region_name)
        self.client = client

    def head(self, key: str) -> ObjectMetadata:
        key = validate_key(key)
        try:
            value = self.client.head_object(Bucket=self.bucket, Key=key)
        except Exception as exc:  # noqa: BLE001 - normalize provider errors
            raise ObjectStoreError("object metadata unavailable") from exc
        etag = str(value.get("ETag", "")).strip('"')
        metadata = {str(k): str(v) for k, v in (value.get("Metadata") or {}).items()}
        return ObjectMetadata(key, int(value.get("ContentLength", -1)), etag, metadata.get("sha256"), metadata)

    def get(self, key: str) -> bytes:
        key = validate_key(key)
        try:
            body = self.client.get_object(Bucket=self.bucket, Key=key)["Body"]
            return bytes(body.read())
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("object read failed") from exc

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        key = validate_key(key)
        if not isinstance(offset, int) or not isinstance(length, int) or offset < 0 or length < 0:
            raise ObjectStoreError("range is invalid")
        end = offset + length - 1
        value = self.client.get_object(Bucket=self.bucket, Key=key, Range=f"bytes={offset}-{end}")
        data = bytes(value["Body"].read())
        if len(data) != length:
            raise ObjectStoreError("range is truncated")
        return data

    def exists(self, key: str) -> bool:
        try:
            self.head(key)
            return True
        except ObjectStoreError:
            return False

    def put_immutable(self, key: str, data: bytes, *, sha256: str | None = None) -> ObjectMetadata:
        key = validate_key(key)
        digest = _sha256(data)
        if sha256 is not None and digest != sha256:
            raise ObjectStoreError("object hash mismatch")
        if self.exists(key):
            existing = self.get(key)
            if existing != data:
                raise ObjectStoreError("immutable object differs")
            return self.head(key)
        self.client.put_object(Bucket=self.bucket, Key=key, Body=data, Metadata={"sha256": digest})
        return self.head(key)

    def put_immutable_file(self, key: str, source: Path | str, *, sha256: str | None = None) -> ObjectMetadata:
        """Publish one immutable file through the provider-neutral S3 contract."""

        key = validate_key(key)
        source_path = Path(source)
        try:
            size = source_path.stat().st_size
            digest = _sha256_file(source_path)
        except OSError as exc:
            raise ObjectStoreError("object source is unavailable") from exc
        if sha256 is not None and digest != sha256:
            raise ObjectStoreError("object hash mismatch")
        if self.exists(key):
            existing = self.head(key)
            if existing.size != size or (existing.sha256 and existing.sha256 != digest):
                raise ObjectStoreError("immutable object differs")
            if existing.sha256 is None and _sha256(self.get(key)) != digest:
                raise ObjectStoreError("immutable object differs")
            return existing
        try:
            with source_path.open("rb") as handle:
                self.client.put_object(Bucket=self.bucket, Key=key, Body=handle, Metadata={"sha256": digest})
        except Exception as exc:  # noqa: BLE001 - normalize provider errors
            raise ObjectStoreError("object upload failed") from exc
        return self.head(key)

    def compare_and_swap(self, key: str, data: bytes, *, expected_etag: str | None) -> ObjectMetadata:
        key = validate_key(key)
        actual = None
        if self.exists(key):
            actual = self.head(key).etag
        if actual != expected_etag:
            raise ObjectStoreError("conditional pointer update failed")
        # Generic S3 has no portable atomic conditional PUT.  A publisher must
        # serialize this operation; the backend never exposes it to clients.
        self.client.put_object(Bucket=self.bucket, Key=key, Body=data)
        return self.head(key)


class ReadOnlyObjectStore:
    """Capability-reduced view for the request-serving runtime."""

    def __init__(self, store: ObjectStore):
        self._store = store

    def head(self, key: str) -> ObjectMetadata:
        return self._store.head(key)

    def get(self, key: str) -> bytes:
        return self._store.get(key)

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        return self._store.get_range(key, offset, length)

    def exists(self, key: str) -> bool:
        return self._store.exists(key)

    def put_immutable(self, key: str, data: bytes, *, sha256: str | None = None) -> ObjectMetadata:
        raise ObjectStoreError("runtime object store is read-only")

    def put_immutable_file(self, key: str, source: Path | str, *, sha256: str | None = None) -> ObjectMetadata:
        raise ObjectStoreError("runtime object store is read-only")

    def compare_and_swap(self, key: str, data: bytes, *, expected_etag: str | None) -> ObjectMetadata:
        raise ObjectStoreError("runtime object store is read-only")


__all__ = ["LocalFilesystemObjectStore", "ObjectMetadata", "ObjectStore", "ObjectStoreError", "ReadOnlyObjectStore", "S3CompatibleObjectStore", "validate_key"]

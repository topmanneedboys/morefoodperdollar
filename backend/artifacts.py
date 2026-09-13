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
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterator, Mapping, Protocol

from .object_store import OBJECT_STREAM_CHUNK_BYTES, LocalFilesystemObjectStore, ObjectMetadata, ObjectStore, ObjectStoreError, validate_key


class ReleaseArtifactError(RuntimeError):
    """An immutable release artifact could not be verified or read."""


DEFAULT_FULL_OBJECT_CACHE_BYTES = 512 * 1024 * 1024
# Whole-object verification is useful for the small control/index members,
# but turning a single range read into a multi-megabyte download is counter-
# productive for the regional offer packs.  Keep the total cache bound above
# while using the safe verified range path for larger immutable objects.
DEFAULT_FULL_OBJECT_CACHE_OBJECT_BYTES = 1 * 1024 * 1024


@dataclass
class _FullObjectFlight:
    size: int
    event: threading.Event
    error: BaseException | None = None


class _FullObjectCache:
    """Bounded, verified, single-flight cache for immutable release objects."""

    def __init__(self, max_bytes: int):
        if not isinstance(max_bytes, int) or max_bytes <= 0 or max_bytes > 1024 * 1024 * 1024:
            raise ValueError("full-object cache bound must be between 1 byte and 1 GiB")
        self.max_bytes = max_bytes
        self._items: OrderedDict[str, bytes] = OrderedDict()
        self._inflight: dict[str, _FullObjectFlight] = {}
        self._bytes = 0
        self._reserved = 0
        self._hits = 0
        self._misses = 0
        self._downloads = 0
        self._evictions = 0
        self._condition = threading.Condition()

    def _evict_for(self, required: int) -> None:
        while self._bytes + self._reserved + required > self.max_bytes and self._items:
            _, value = self._items.popitem(last=False)
            self._bytes -= len(value)
            self._evictions += 1

    def get_or_load(self, digest: str, size: int, loader: Callable[[], bytes]) -> tuple[bytes | None, bool]:
        """Return ``(bytes, hit)`` or ``(None, False)`` when not cacheable.

        A digest is downloaded at most once concurrently.  Reservations count
        in-flight full objects, so the configured cache budget also bounds
        overlapping downloads.  If another object occupies the whole budget,
        callers use the existing verified range/stream path instead of
        blocking an unbounded queue.
        """

        if size > self.max_bytes:
            return None, False
        while True:
            with self._condition:
                current = self._items.get(digest)
                if current is not None:
                    self._items.move_to_end(digest)
                    self._hits += 1
                    return current, True
                flight = self._inflight.get(digest)
                if flight is None:
                    self._evict_for(size)
                    if self._bytes + self._reserved + size > self.max_bytes:
                        return None, False
                    flight = _FullObjectFlight(size=size, event=threading.Event())
                    self._inflight[digest] = flight
                    self._reserved += size
                    self._misses += 1
                    owner = True
                else:
                    event = flight.event
                    owner = False
            if owner:
                assert flight is not None
                try:
                    data = loader()
                    if not isinstance(data, bytes) or len(data) != size or hashlib.sha256(data).hexdigest() != digest:
                        raise ReleaseArtifactError("full-object cache verification failed")
                    with self._condition:
                        self._reserved -= size
                        self._items[digest] = data
                        self._bytes += len(data)
                        self._downloads += 1
                        self._inflight.pop(digest, None)
                        flight.event.set()
                        self._condition.notify_all()
                    return data, False
                except BaseException as exc:
                    with self._condition:
                        self._reserved -= size
                        flight.error = exc
                        self._inflight.pop(digest, None)
                        flight.event.set()
                        self._condition.notify_all()
                    raise
            event.wait()
            # A failed owner raises directly for all waiters; a successful
            # owner loops once to observe the verified immutable bytes.
            with self._condition:
                if flight.error is not None:
                    raise ReleaseArtifactError("full-object cache single-flight failed") from flight.error
                if digest in self._items:
                    continue
                raise ReleaseArtifactError("full-object cache single-flight failed")

    def stats(self) -> dict[str, int]:
        with self._condition:
            return {
                "maxBytes": self.max_bytes,
                "bytes": self._bytes,
                "entries": len(self._items),
                "inflight": len(self._inflight),
                "hits": self._hits,
                "misses": self._misses,
                "downloads": self._downloads,
                "evictions": self._evictions,
            }

    def clear(self) -> None:
        with self._condition:
            self._items.clear()
            self._bytes = 0


class ReleaseArtifactStore(Protocol):
    """Read-only artifact contract used by the backend reader."""

    remote: bool

    def read(self, path: str, *, expected_sha256: str | None = None, expected_bytes: int | None = None) -> bytes: ...

    def stream(self, path: str, *, chunk_size: int = OBJECT_STREAM_CHUNK_BYTES) -> Iterator[bytes]: ...

    def read_range(self, path: str, offset: int, length: int) -> bytes: ...


_HEX64 = re.compile(r"[0-9a-f]{64}\Z")


def _digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


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

    def stream(self, path: str, *, chunk_size: int = OBJECT_STREAM_CHUNK_BYTES) -> Iterator[bytes]:
        key = self._key(path)
        try:
            yield from self._store.stream(key, chunk_size=chunk_size)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact stream is unavailable") from exc

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

    def __init__(
        self,
        store: ObjectStore,
        manifest: Mapping[str, Any],
        *,
        release_id: str,
        cache_root: Path | str | None = None,
        full_object_cache: _FullObjectCache | None = None,
        full_object_cache_bytes: int | None = None,
        full_object_cache_object_bytes: int | None = None,
    ):
        self.store = store
        self.release_id = release_id
        self.manifest = manifest
        self.cache_root = Path(cache_root).resolve() if cache_root is not None else Path(tempfile.mkdtemp(prefix=f"valuepilot-release-{release_id}-")).resolve()
        self._descriptors: dict[str, tuple[str, int]] = {}
        self._relative_prefixes: tuple[str, ...] = ()
        self._verified_objects: set[str] = set()
        self._metadata_verified: dict[str, ObjectMetadata] = {}
        self._lock = threading.RLock()
        if full_object_cache_object_bytes is not None and (
            not isinstance(full_object_cache_object_bytes, int) or full_object_cache_object_bytes <= 0
        ):
            raise ValueError("full-object cache per-object bound must be positive")
        self._full_object_cache_object_bytes = full_object_cache_object_bytes
        if full_object_cache is not None:
            self._full_object_cache: _FullObjectCache | None = full_object_cache
        else:
            # The whole-object cache is a regional GCS runtime optimization.
            # Keep local qualification and the legacy S3-shaped compatibility
            # path's range/read access shape unchanged unless a caller opts in.
            if full_object_cache_bytes is None:
                # Regional GCS is wrapped in ReadOnlyObjectStore by the
                # runtime.  Capability-based detection keeps the bounded
                # cache enabled through that safety wrapper while leaving
                # local and legacy S3-shaped stores unchanged.
                full_object_cache_bytes = (
                    DEFAULT_FULL_OBJECT_CACHE_BYTES
                    if getattr(store, "supports_pointer_generation_cache", False)
                    else 0
                )
            if self._full_object_cache_object_bytes is None and getattr(store, "supports_pointer_generation_cache", False):
                self._full_object_cache_object_bytes = DEFAULT_FULL_OBJECT_CACHE_OBJECT_BYTES
            self._full_object_cache = _FullObjectCache(full_object_cache_bytes) if full_object_cache_bytes > 0 else None
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

    def has(self, path: str) -> bool:
        """Return whether a logical path is pinned by this immutable manifest."""

        try:
            self._descriptor(path)
        except ReleaseArtifactError as exc:
            if str(exc) == "release artifact is not in the pinned manifest":
                return False
            raise
        return True

    def cache_stats(self) -> dict[str, int]:
        """Return bounded verified full-object cache counters for diagnostics."""

        stats = self._full_object_cache.stats() if self._full_object_cache is not None else {"maxBytes": 0, "bytes": 0, "entries": 0, "inflight": 0, "hits": 0, "misses": 0, "downloads": 0, "evictions": 0}
        stats["maxObjectBytes"] = self._full_object_cache_object_bytes or 0
        return stats

    @staticmethod
    def _object_key(digest: str) -> str:
        return f"objects/sha256/{digest}"

    def _verify_remote_metadata(self, path: str, digest: str, expected_bytes: int) -> ObjectMetadata:
        key = self._object_key(digest)
        with self._lock:
            cached = self._metadata_verified.get(digest)
        if cached is not None:
            if cached.size != expected_bytes:
                raise ReleaseArtifactError(f"{path} object byte count mismatch")
            return cached
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
                self._metadata_verified[digest] = metadata
            return metadata
        with self._lock:
            already_verified = digest in self._verified_objects
        if not already_verified:
            try:
                count = 0
                digest_state = hashlib.sha256()
                for data in self.store.stream(key, chunk_size=OBJECT_STREAM_CHUNK_BYTES):
                    if not isinstance(data, bytes):
                        raise ObjectStoreError("object stream returned non-bytes")
                    count += len(data)
                    if count > expected_bytes:
                        raise ReleaseArtifactError(f"{path} object is oversized")
                    digest_state.update(data)
            except ObjectStoreError as exc:
                raise ReleaseArtifactError(f"{path} object verification read failed") from exc
            if count != expected_bytes or digest_state.hexdigest() != digest:
                raise ReleaseArtifactError(f"{path} object hash or byte count mismatch")
            with self._lock:
                self._verified_objects.add(digest)
                self._metadata_verified[digest] = metadata
        return metadata

    def _load_verified_full(self, path: str, digest: str, size: int) -> tuple[bytes | None, bool]:
        """Load and verify one whole immutable object when it fits the cache."""

        if self._full_object_cache is None or (
            self._full_object_cache_object_bytes is not None and size > self._full_object_cache_object_bytes
        ):
            return None, False

        def load() -> bytes:
            chunks: list[bytes] = []
            count = 0
            digest_state = hashlib.sha256()
            try:
                for data in self.store.stream(self._object_key(digest), chunk_size=OBJECT_STREAM_CHUNK_BYTES):
                    if not isinstance(data, bytes):
                        raise ReleaseArtifactError(f"{path} stream returned non-bytes")
                    count += len(data)
                    if count > size:
                        raise ReleaseArtifactError(f"{path} object is oversized")
                    digest_state.update(data)
                    chunks.append(data)
            except ObjectStoreError as exc:
                raise ReleaseArtifactError(f"{path} object stream failed") from exc
            if count != size or digest_state.hexdigest() != digest:
                raise ReleaseArtifactError(f"{path} hash or byte count mismatch")
            return b"".join(chunks)

        try:
            return self._full_object_cache.get_or_load(digest, size, load)
        except ReleaseArtifactError:
            raise
        except BaseException as exc:  # noqa: BLE001 - normalize cache failures
            raise ReleaseArtifactError(f"{path} full-object verification failed") from exc

    def read(self, path: str, *, expected_sha256: str | None = None, expected_bytes: int | None = None) -> bytes:
        digest, size = self._descriptor(path)
        if expected_sha256 is not None and expected_sha256 != digest:
            raise ReleaseArtifactError("release artifact hash disagrees with manifest")
        if expected_bytes is not None and expected_bytes != size:
            raise ReleaseArtifactError("release artifact size disagrees with manifest")
        cached, _ = self._load_verified_full(path, digest, size)
        if cached is not None:
            with self._lock:
                self._verified_objects.add(digest)
            return cached
        chunks: list[bytes] = []
        count = 0
        digest_state = hashlib.sha256()
        try:
            for data in self.store.stream(self._object_key(digest), chunk_size=OBJECT_STREAM_CHUNK_BYTES):
                if not isinstance(data, bytes):
                    raise ReleaseArtifactError(f"{path} stream returned non-bytes")
                count += len(data)
                if count > size:
                    raise ReleaseArtifactError(f"{path} object is oversized")
                digest_state.update(data)
                chunks.append(data)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact read failed") from exc
        if count != size or digest_state.hexdigest() != digest:
            raise ReleaseArtifactError(f"{path} hash or byte count mismatch")
        with self._lock:
            self._verified_objects.add(digest)
        return b"".join(chunks)

    def stream(self, path: str, *, chunk_size: int = OBJECT_STREAM_CHUNK_BYTES) -> Iterator[bytes]:
        digest, size = self._descriptor(path)
        if not isinstance(chunk_size, int) or not 1 <= chunk_size <= 16 * 1024 * 1024:
            raise ReleaseArtifactError("release artifact stream chunk size is invalid")
        cached, _ = self._load_verified_full(path, digest, size)
        if cached is not None:
            for offset in range(0, len(cached), chunk_size):
                yield cached[offset : offset + chunk_size]
            return
        count = 0
        digest_state = hashlib.sha256()
        try:
            for data in self.store.stream(self._object_key(digest), chunk_size=chunk_size):
                if not isinstance(data, bytes):
                    raise ReleaseArtifactError(f"{path} stream returned non-bytes")
                count += len(data)
                if count > size:
                    raise ReleaseArtifactError(f"{path} object is oversized")
                digest_state.update(data)
                yield data
        except ObjectStoreError as exc:
            raise ReleaseArtifactError("release artifact stream failed") from exc
        if count != size or digest_state.hexdigest() != digest:
            raise ReleaseArtifactError(f"{path} hash or byte count mismatch")
        with self._lock:
            self._verified_objects.add(digest)

    def read_range(self, path: str, offset: int, length: int) -> bytes:
        digest, size = self._descriptor(path)
        if not isinstance(offset, int) or not isinstance(length, int) or offset < 0 or length < 0 or offset + length > size:
            raise ReleaseArtifactError("release artifact range is invalid")
        cached, _ = self._load_verified_full(path, digest, size)
        if cached is not None:
            return cached[offset : offset + length]
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
        cached, _ = self._load_verified_full(path, digest, size)
        if cached is not None:
            if target.is_file() and target.stat().st_size == size and _sha256_file(target) == digest:
                with self._lock:
                    self._verified_objects.add(digest)
                return target
            _atomic_write(target, cached)
            with self._lock:
                self._verified_objects.add(digest)
            return target
        if target.is_file() and target.stat().st_size == size and _sha256_file(target) == digest:
            with self._lock:
                self._verified_objects.add(digest)
            return target
        target.parent.mkdir(parents=True, exist_ok=True)
        fd, temporary_name = tempfile.mkstemp(prefix=f".{target.name}.", dir=str(target.parent))
        temporary = Path(temporary_name)
        count = 0
        digest_state = hashlib.sha256()
        try:
            with os.fdopen(fd, "wb") as handle:
                for data in self.store.stream(self._object_key(digest), chunk_size=OBJECT_STREAM_CHUNK_BYTES):
                    if not isinstance(data, bytes):
                        raise ReleaseArtifactError(f"{path} stream returned non-bytes")
                    count += len(data)
                    if count > size:
                        raise ReleaseArtifactError(f"{path} object is oversized")
                    digest_state.update(data)
                    handle.write(data)
                if count != size or digest_state.hexdigest() != digest:
                    raise ReleaseArtifactError(f"{path} hash or byte count mismatch")
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, target)
        except ObjectStoreError as exc:
            raise ReleaseArtifactError(f"{path} stream failed") from exc
        finally:
            temporary.unlink(missing_ok=True)
        with self._lock:
            self._verified_objects.add(digest)
        return target


__all__ = ["DEFAULT_FULL_OBJECT_CACHE_BYTES", "DEFAULT_FULL_OBJECT_CACHE_OBJECT_BYTES", "FilesystemReleaseArtifactStore", "ManifestReleaseArtifactStore", "ReleaseArtifactError", "ReleaseArtifactStore"]

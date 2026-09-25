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
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator, Mapping, Protocol


class ObjectStoreError(RuntimeError):
    """A storage operation failed closed."""


class ObjectStoreLimitExceeded(ObjectStoreError):
    """A bounded object-store listing exceeded its declared safety limit."""


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
    def stream(self, key: str, *, chunk_size: int = 1024 * 1024) -> Iterator[bytes]: ...
    def get_range(self, key: str, offset: int, length: int) -> bytes: ...
    def exists(self, key: str) -> bool: ...
    def list_immediate(self, prefix: str, *, max_results: int) -> tuple[ObjectMetadata, ...]: ...
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


def validate_prefix(prefix: str) -> str:
    """Validate a bounded directory-like object prefix."""

    if not isinstance(prefix, str) or not prefix or len(prefix) > 1024 or not prefix.endswith("/"):
        raise ObjectStoreError("object prefix is invalid")
    if "\\" in prefix or prefix.startswith("/") or "\x00" in prefix:
        raise ObjectStoreError("object prefix is invalid")
    parts = prefix[:-1].split("/")
    if any(part in {"", ".", ".."} for part in parts):
        raise ObjectStoreError("object prefix is invalid")
    return prefix


def _validate_list_bound(max_results: int) -> int:
    if not isinstance(max_results, int) or not 1 <= max_results <= 10_000:
        raise ObjectStoreError("object listing bound is invalid")
    return max_results


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


# Managed S3 transfers use a multipart upload above this threshold.  The
# values are deliberately bounded: a single publisher uploads one object at a
# time, and four 16 MiB parts keep the transfer memory-bounded while avoiding
# the fragile single-request path that failed for the first R2 publication.
S3_UPLOAD_MULTIPART_THRESHOLD_BYTES = 16 * 1024 * 1024
S3_UPLOAD_MULTIPART_CHUNKSIZE_BYTES = 16 * 1024 * 1024
S3_UPLOAD_MAX_CONCURRENCY = 4
S3_UPLOAD_MAX_ATTEMPTS = 3
S3_UPLOAD_RETRY_BACKOFF_SECONDS = 0.25
S3_CLIENT_TOTAL_MAX_ATTEMPTS = 5
OBJECT_STREAM_CHUNK_BYTES = 1024 * 1024


class LocalFilesystemObjectStore:
    """Small deterministic object store for local qualification and tests."""

    def __init__(self, root: Path | str):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()

    def _path(self, key: str) -> Path:
        key = validate_key(key)
        path = (self.root / Path(*key.split("/"))).resolve()
        # Windows may return an extended-length ``\\?\`` prefix for a path
        # while another bounded writer is creating its parents.  Compare
        # normalized case-insensitive paths rather than raw ``Path`` objects,
        # while retaining resolve-based symlink containment.
        def normalized(value: Path) -> str:
            text = os.path.normpath(str(value))
            if text.startswith("\\\\?\\"):
                text = text[4:]
            return os.path.normcase(text)

        root_text = normalized(self.root)
        path_text = normalized(path)
        try:
            contained = os.path.commonpath((root_text, path_text)) == root_text
        except ValueError:
            contained = False
        if not contained:
            raise ObjectStoreError("object key escapes store root")
        return Path(path_text)

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

    def stream(self, key: str, *, chunk_size: int = OBJECT_STREAM_CHUNK_BYTES) -> Iterator[bytes]:
        key = validate_key(key)
        if not isinstance(chunk_size, int) or not 1 <= chunk_size <= 16 * 1024 * 1024:
            raise ObjectStoreError("stream chunk size is invalid")
        path = self._path(key)
        try:
            with path.open("rb") as handle:
                while True:
                    data = handle.read(chunk_size)
                    if not data:
                        return
                    yield data
        except OSError as exc:
            raise ObjectStoreError("object stream failed") from exc

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

    def list_immediate(self, prefix: str, *, max_results: int) -> tuple[ObjectMetadata, ...]:
        prefix = validate_prefix(prefix)
        max_results = _validate_list_bound(max_results)
        directory = self._path(prefix.rstrip("/"))
        try:
            if not directory.exists():
                return ()
            if not directory.is_dir():
                raise ObjectStoreError("object prefix is not a directory")
            entries = sorted(os.scandir(directory), key=lambda entry: entry.name)
            if any(entry.is_dir() for entry in entries):
                raise ObjectStoreLimitExceeded("object prefix contains nested keys")
            files = [entry for entry in entries if entry.is_file()]
            if len(files) > max_results:
                raise ObjectStoreLimitExceeded("object listing exceeds bound")
            return tuple(
                self._metadata(f"{prefix}{entry.name}", Path(entry.path))
                for entry in files
            )
        except ObjectStoreError:
            raise
        except OSError as exc:
            raise ObjectStoreError("object listing unavailable") from exc

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
                from botocore.config import Config  # type: ignore
            except ImportError as exc:  # pragma: no cover - exercised in deployments
                raise ObjectStoreError("boto3 is required for S3-compatible storage") from exc
            client = boto3.client(
                "s3",
                endpoint_url=endpoint_url,
                region_name=region_name,
                config=Config(
                    connect_timeout=20,
                    read_timeout=120,
                    max_pool_connections=S3_UPLOAD_MAX_CONCURRENCY,
                    retries={"mode": "standard", "total_max_attempts": S3_CLIENT_TOTAL_MAX_ATTEMPTS},
                ),
            )
        self.client = client

    @staticmethod
    def _transfer_config() -> Any:
        try:
            from boto3.s3.transfer import TransferConfig  # type: ignore
        except ImportError as exc:  # pragma: no cover - exercised in deployments
            raise ObjectStoreError("boto3 is required for managed S3 transfers") from exc
        return TransferConfig(
            multipart_threshold=S3_UPLOAD_MULTIPART_THRESHOLD_BYTES,
            multipart_chunksize=S3_UPLOAD_MULTIPART_CHUNKSIZE_BYTES,
            max_concurrency=S3_UPLOAD_MAX_CONCURRENCY,
            use_threads=True,
        )

    @staticmethod
    def _is_transient_upload_error(error: BaseException) -> bool:
        if isinstance(error, (ConnectionError, TimeoutError)):
            return True
        # Keep the adapter import-light for local-only use while recognizing
        # the botocore transport exceptions used by real S3-compatible clients.
        return error.__class__.__name__ in {
            "ConnectionClosedError",
            "ConnectTimeoutError",
            "EndpointConnectionError",
            "ReadTimeoutError",
            "RetriesExceededError",
            "S3UploadFailedError",
        }

    def _verified_existing_file(self, key: str, *, size: int, digest: str) -> ObjectMetadata | None:
        if not self.exists(key):
            return None
        existing = self.head(key)
        if existing.size != size or (existing.sha256 and existing.sha256 != digest):
            raise ObjectStoreError("immutable object differs")
        if existing.sha256 is None and _sha256(self.get(key)) != digest:
            raise ObjectStoreError("immutable object differs")
        return existing

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

    def stream(self, key: str, *, chunk_size: int = OBJECT_STREAM_CHUNK_BYTES) -> Iterator[bytes]:
        key = validate_key(key)
        if not isinstance(chunk_size, int) or not 1 <= chunk_size <= 16 * 1024 * 1024:
            raise ObjectStoreError("stream chunk size is invalid")
        body = None
        try:
            body = self.client.get_object(Bucket=self.bucket, Key=key)["Body"]
            while True:
                data = body.read(chunk_size)
                if not data:
                    return
                if not isinstance(data, bytes):
                    data = bytes(data)
                yield data
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("object stream failed") from exc
        finally:
            close = getattr(body, "close", None)
            if callable(close):
                close()

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

    def list_immediate(self, prefix: str, *, max_results: int) -> tuple[ObjectMetadata, ...]:
        prefix = validate_prefix(prefix)
        max_results = _validate_list_bound(max_results)
        try:
            response = self.client.list_objects_v2(
                Bucket=self.bucket,
                Prefix=prefix,
                MaxKeys=max_results + 1,
                Delimiter="/",
            )
        except Exception as exc:  # noqa: BLE001 - normalize provider errors
            raise ObjectStoreError("object listing unavailable") from exc
        if response.get("CommonPrefixes"):
            raise ObjectStoreLimitExceeded("object prefix contains nested keys")
        contents = list(response.get("Contents") or [])
        if response.get("IsTruncated") or len(contents) > max_results:
            raise ObjectStoreLimitExceeded("object listing exceeds bound")
        result: list[ObjectMetadata] = []
        for item in contents:
            key = str(item.get("Key", ""))
            relative = key.removeprefix(prefix)
            if not relative or "/" in relative:
                raise ObjectStoreLimitExceeded("object prefix contains nested keys")
            result.append(
                ObjectMetadata(
                    key,
                    int(item.get("Size", -1)),
                    str(item.get("ETag", "")).strip('"'),
                    None,
                    (),
                )
            )
        return tuple(sorted(result, key=lambda item: item.key))

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
        existing = self._verified_existing_file(key, size=size, digest=digest)
        if existing is not None:
            return existing

        # upload_file is boto3's managed file transfer.  It selects a regular
        # PUT below the threshold and multipart upload above it, retries failed
        # requests through the configured client, and does not expose an
        # incomplete multipart upload as a committed object.
        transfer_config = self._transfer_config()
        upload_error: BaseException | None = None
        for attempt in range(S3_UPLOAD_MAX_ATTEMPTS):
            try:
                self.client.upload_file(
                    str(source_path),
                    self.bucket,
                    key,
                    ExtraArgs={"Metadata": {"sha256": digest}},
                    Config=transfer_config,
                )
            except Exception as exc:  # noqa: BLE001 - normalize provider errors
                upload_error = exc
                # A connection can fail after the provider has committed the
                # complete object.  Verify before deciding whether a retry is
                # safe; a visible mismatch is immutable and fails closed.
                existing = self._verified_existing_file(key, size=size, digest=digest)
                if existing is not None:
                    return existing
                if not self._is_transient_upload_error(exc) or attempt + 1 >= S3_UPLOAD_MAX_ATTEMPTS:
                    raise ObjectStoreError("object upload failed") from exc
                time.sleep(S3_UPLOAD_RETRY_BACKOFF_SECONDS * (2**attempt))
                continue

            try:
                return self._verified_existing_file(key, size=size, digest=digest) or self.head(key)
            except ObjectStoreError as exc:
                raise ObjectStoreError("uploaded object verification failed") from exc

        # The loop either returns or raises.  Keep a defensive failure for
        # static analyzers and future changes that alter the loop bounds.
        raise ObjectStoreError("object upload failed") from upload_error

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


class GCSObjectStore:
    """Direct Google Cloud Storage adapter for a regional runtime mirror.

    The adapter deliberately uses the application client rather than FUSE or
    a provider-specific filesystem.  Content-addressed objects are created
    with ``if_generation_match=0`` and can never be silently replaced.  The
    generation number is the compare-and-swap token for the small ACTIVE
    pointer; it is kept separate from ValuePilot's manifest SHA-256 checks.
    """

    supports_pointer_generation_cache = True

    def __init__(self, *, bucket: str, client: Any | None = None, project: str | None = None, credentials: Any | None = None):
        if not isinstance(bucket, str) or not bucket.strip():
            raise ObjectStoreError("GCS bucket is invalid")
        self.bucket = bucket.strip()
        if client is None:
            try:
                from google.cloud import storage  # type: ignore
            except ImportError as exc:  # pragma: no cover - deployment dependency
                raise ObjectStoreError("google-cloud-storage is required for GCS storage") from exc
            try:
                client = storage.Client(project=project, credentials=credentials)
            except Exception as exc:  # noqa: BLE001 - normalize credential failures
                raise ObjectStoreError("GCS client is unavailable") from exc
        try:
            self.client = client
            self._bucket = client.bucket(self.bucket)
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("GCS bucket is unavailable") from exc
        self._stats_lock = threading.Lock()
        self._stats = {
            "headRequests": 0,
            "getRequests": 0,
            "streamRequests": 0,
            "rangeRequests": 0,
            "existsRequests": 0,
            "listRequests": 0,
            "putRequests": 0,
            "casRequests": 0,
            "bytesRead": 0,
            "bytesWritten": 0,
        }

    def _record(self, name: str, amount: int = 0) -> None:
        with self._stats_lock:
            if name in {"bytesRead", "bytesWritten"}:
                self._stats[name] = self._stats.get(name, 0) + amount
            else:
                self._stats[name] = self._stats.get(name, 0) + 1
                if amount:
                    self._stats["bytesRead"] += amount

    def stats_snapshot(self) -> dict[str, int]:
        """Return cumulative provider-operation counters for diagnostics."""

        with self._stats_lock:
            return dict(self._stats)

    def inventory(self, prefixes: tuple[str, ...] = ("objects/sha256/", "releases/", "control/")) -> dict[str, ObjectMetadata]:
        """List bounded metadata for destination preflight/verification.

        GCS listing is used only by operator-side publication diagnostics.  It
        avoids one network existence request per content-addressed object while
        retaining the application SHA-256 stored in object metadata.  Runtime
        reads never call this method.
        """

        if not isinstance(prefixes, tuple) or any(not isinstance(prefix, str) for prefix in prefixes):
            raise ObjectStoreError("GCS inventory prefixes are invalid")
        result: dict[str, ObjectMetadata] = {}
        for prefix in prefixes:
            self._record("listRequests")
            try:
                blobs = self._bucket.list_blobs(prefix=prefix)
                for blob in blobs:
                    key = validate_key(str(getattr(blob, "name", "")))
                    size = getattr(blob, "size", None)
                    token = self._generation(blob)
                    if not isinstance(size, int) or size < 0 or not token:
                        # A partial listing entry is not proof of an object;
                        # leave it out so the caller can fail closed via head.
                        continue
                    metadata = {str(k): str(v) for k, v in (getattr(blob, "metadata", None) or {}).items()}
                    result[key] = ObjectMetadata(key, size, token, metadata.get("sha256"), metadata)
            except Exception as exc:  # noqa: BLE001 - normalize provider errors
                raise ObjectStoreError("GCS object inventory unavailable") from exc
        return result

    def _blob(self, key: str) -> Any:
        return self._bucket.blob(validate_key(key))

    @staticmethod
    def _is_precondition_failure(error: BaseException) -> bool:
        return error.__class__.__name__ in {
            "BadRequest",
            "Conflict",
            "PreconditionFailed",
            "PreconditionFailure",
        } or "precondition" in str(error).lower()

    @staticmethod
    def _generation(blob: Any) -> str:
        generation = getattr(blob, "generation", None)
        if generation is not None:
            return str(generation)
        etag = getattr(blob, "etag", None)
        return str(etag or "")

    def head(self, key: str) -> ObjectMetadata:
        key = validate_key(key)
        self._record("headRequests")
        blob = self._blob(key)
        try:
            blob.reload()
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("object metadata unavailable") from exc
        size = getattr(blob, "size", None)
        if not isinstance(size, int) or size < 0:
            raise ObjectStoreError("object metadata unavailable")
        metadata = {str(k): str(v) for k, v in (getattr(blob, "metadata", None) or {}).items()}
        token = self._generation(blob)
        if not token:
            raise ObjectStoreError("object generation is unavailable")
        return ObjectMetadata(key, size, token, metadata.get("sha256"), metadata)

    def get(self, key: str) -> bytes:
        key = validate_key(key)
        self._record("getRequests")
        try:
            data = self._blob(key).download_as_bytes()
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("object read failed") from exc
        if not isinstance(data, bytes):
            data = bytes(data)
        self._record("bytesRead", len(data))
        return data

    def stream(self, key: str, *, chunk_size: int = OBJECT_STREAM_CHUNK_BYTES) -> Iterator[bytes]:
        key = validate_key(key)
        if not isinstance(chunk_size, int) or not 1 <= chunk_size <= 16 * 1024 * 1024:
            raise ObjectStoreError("stream chunk size is invalid")
        self._record("streamRequests")
        reader = None
        try:
            reader = self._blob(key).open("rb", chunk_size=chunk_size)
            while True:
                data = reader.read(chunk_size)
                if not data:
                    return
                if not isinstance(data, bytes):
                    data = bytes(data)
                self._record("bytesRead", len(data))
                yield data
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("object stream failed") from exc
        finally:
            close = getattr(reader, "close", None)
            if callable(close):
                close()

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        key = validate_key(key)
        if not isinstance(offset, int) or not isinstance(length, int) or offset < 0 or length < 0:
            raise ObjectStoreError("range is invalid")
        if length == 0:
            return b""
        self._record("rangeRequests")
        try:
            data = self._blob(key).download_as_bytes(start=offset, end=offset + length - 1)
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("range read failed") from exc
        if not isinstance(data, bytes):
            data = bytes(data)
        if len(data) != length:
            raise ObjectStoreError("range is truncated")
        self._record("bytesRead", len(data))
        return data

    def exists(self, key: str) -> bool:
        key = validate_key(key)
        self._record("existsRequests")
        try:
            return bool(self._blob(key).exists())
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("object existence is unavailable") from exc

    def list_immediate(self, prefix: str, *, max_results: int) -> tuple[ObjectMetadata, ...]:
        prefix = validate_prefix(prefix)
        max_results = _validate_list_bound(max_results)
        self._record("listRequests")
        try:
            blobs = self._bucket.list_blobs(prefix=prefix, max_results=max_results + 1)
            result: list[ObjectMetadata] = []
            for blob in blobs:
                key = str(getattr(blob, "name", ""))
                relative = key.removeprefix(prefix)
                if not relative or "/" in relative:
                    raise ObjectStoreLimitExceeded("object prefix contains nested keys")
                result.append(
                    ObjectMetadata(
                        key,
                        int(getattr(blob, "size", -1) if getattr(blob, "size", None) is not None else -1),
                        self._generation(blob),
                        None,
                        (),
                    )
                )
                if len(result) > max_results:
                    raise ObjectStoreLimitExceeded("object listing exceeds bound")
            return tuple(sorted(result, key=lambda item: item.key))
        except ObjectStoreLimitExceeded:
            raise
        except ObjectStoreError:
            raise
        except Exception as exc:  # noqa: BLE001 - normalize provider errors
            raise ObjectStoreError("object listing unavailable") from exc

    def _verified_existing(self, key: str, *, data: bytes, digest: str) -> ObjectMetadata | None:
        if not self.exists(key):
            return None
        metadata = self.head(key)
        if metadata.size != len(data) or (metadata.sha256 is not None and metadata.sha256 != digest):
            raise ObjectStoreError("immutable object differs")
        if metadata.sha256 is None and _sha256(self.get(key)) != digest:
            raise ObjectStoreError("immutable object differs")
        return metadata

    def _verified_existing_file(self, key: str, *, size: int, digest: str) -> ObjectMetadata | None:
        if not self.exists(key):
            return None
        metadata = self.head(key)
        if metadata.size != size or (metadata.sha256 is not None and metadata.sha256 != digest):
            raise ObjectStoreError("immutable object differs")
        if metadata.sha256 is None:
            state = hashlib.sha256()
            try:
                for chunk in self.stream(key):
                    state.update(chunk)
            except ObjectStoreError:
                raise
            if state.hexdigest() != digest:
                raise ObjectStoreError("immutable object differs")
        return metadata

    def put_immutable(self, key: str, data: bytes, *, sha256: str | None = None) -> ObjectMetadata:
        key = validate_key(key)
        if not isinstance(data, bytes):
            raise ObjectStoreError("object data must be bytes")
        digest = _sha256(data)
        if sha256 is not None and digest != sha256:
            raise ObjectStoreError("object hash mismatch")
        existing = self._verified_existing(key, data=data, digest=digest)
        if existing is not None:
            return existing
        blob = self._blob(key)
        self._record("putRequests")
        try:
            blob.metadata = {"sha256": digest}
            blob.upload_from_string(data, if_generation_match=0)
            self._record("bytesWritten", len(data))
        except Exception as exc:  # noqa: BLE001
            if self._is_precondition_failure(exc):
                existing = self._verified_existing(key, data=data, digest=digest)
                if existing is not None:
                    return existing
            raise ObjectStoreError("object upload failed") from exc
        existing = self._verified_existing(key, data=data, digest=digest)
        if existing is None:
            raise ObjectStoreError("uploaded object disappeared")
        return existing

    def put_immutable_file(self, key: str, source: Path | str, *, sha256: str | None = None) -> ObjectMetadata:
        key = validate_key(key)
        source_path = Path(source)
        try:
            size = source_path.stat().st_size
            digest = _sha256_file(source_path)
        except OSError as exc:
            raise ObjectStoreError("object source is unavailable") from exc
        if sha256 is not None and digest != sha256:
            raise ObjectStoreError("object hash mismatch")
        blob = self._blob(key)
        self._record("putRequests")
        try:
            blob.metadata = {"sha256": digest}
            blob.upload_from_filename(str(source_path), if_generation_match=0)
            self._record("bytesWritten", size)
        except Exception as exc:  # noqa: BLE001
            if self._is_precondition_failure(exc):
                existing = self._verified_existing_file(key, size=size, digest=digest)
                if existing is not None:
                    return existing
            raise ObjectStoreError("object upload failed") from exc
        token = self._generation(blob)
        if not token or getattr(blob, "size", None) != size:
            try:
                blob.reload()
            except Exception as exc:  # noqa: BLE001
                raise ObjectStoreError("uploaded object verification failed") from exc
            token = self._generation(blob)
        if not token or getattr(blob, "size", None) != size:
            raise ObjectStoreError("uploaded object verification failed")
        return ObjectMetadata(key, size, token, digest, {"sha256": digest})

    def compare_and_swap(self, key: str, data: bytes, *, expected_etag: str | None) -> ObjectMetadata:
        key = validate_key(key)
        if not isinstance(data, bytes):
            raise ObjectStoreError("object data must be bytes")
        current = self.head(key) if self.exists(key) else None
        actual = current.etag if current is not None else None
        if actual != expected_etag:
            raise ObjectStoreError("conditional pointer update failed")
        if expected_etag is None:
            generation_match = 0
        else:
            try:
                generation_match = int(expected_etag)
            except (TypeError, ValueError) as exc:
                raise ObjectStoreError("GCS generation token is invalid") from exc
        blob = self._blob(key)
        self._record("casRequests")
        try:
            blob.metadata = {"sha256": _sha256(data)}
            blob.upload_from_string(data, if_generation_match=generation_match)
            self._record("bytesWritten", len(data))
        except Exception as exc:  # noqa: BLE001
            raise ObjectStoreError("conditional pointer update failed") from exc
        return self.head(key)


class ReadOnlyObjectStore:
    """Capability-reduced view for the request-serving runtime."""

    def __init__(self, store: ObjectStore):
        self._store = store
        self.supports_pointer_generation_cache = bool(getattr(store, "supports_pointer_generation_cache", False))

    def head(self, key: str) -> ObjectMetadata:
        return self._store.head(key)

    def get(self, key: str) -> bytes:
        return self._store.get(key)

    def stream(self, key: str, *, chunk_size: int = OBJECT_STREAM_CHUNK_BYTES) -> Iterator[bytes]:
        return self._store.stream(key, chunk_size=chunk_size)

    def get_range(self, key: str, offset: int, length: int) -> bytes:
        return self._store.get_range(key, offset, length)

    def exists(self, key: str) -> bool:
        return self._store.exists(key)

    def list_immediate(self, prefix: str, *, max_results: int) -> tuple[ObjectMetadata, ...]:
        return self._store.list_immediate(prefix, max_results=max_results)

    def stats_snapshot(self) -> dict[str, int]:
        snapshot = getattr(self._store, "stats_snapshot", None)
        return dict(snapshot()) if callable(snapshot) else {}

    def put_immutable(self, key: str, data: bytes, *, sha256: str | None = None) -> ObjectMetadata:
        raise ObjectStoreError("runtime object store is read-only")

    def put_immutable_file(self, key: str, source: Path | str, *, sha256: str | None = None) -> ObjectMetadata:
        raise ObjectStoreError("runtime object store is read-only")

    def compare_and_swap(self, key: str, data: bytes, *, expected_etag: str | None) -> ObjectMetadata:
        raise ObjectStoreError("runtime object store is read-only")


__all__ = ["GCSObjectStore", "LocalFilesystemObjectStore", "OBJECT_STREAM_CHUNK_BYTES", "ObjectMetadata", "ObjectStore", "ObjectStoreError", "ObjectStoreLimitExceeded", "ReadOnlyObjectStore", "S3CompatibleObjectStore", "validate_key", "validate_prefix"]

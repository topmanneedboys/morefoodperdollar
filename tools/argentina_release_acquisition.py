"""Safe discovery, acquisition, and private archiving for official SEPA releases.

This module deliberately stops at the provider edge.  It does not parse prices,
join products, or decide whether a release is suitable for the Android app.  A
worker may pass the verified bytes to :mod:`tools.argentina_daily_release`,
which remains the only authority for SEPA qualification and artifact building.

The official national catalog is used for discovery because it is the stable
public catalog of the Ministry dataset.  Its resource URLs are still required
to resolve to the official production host.  Every network and storage action
is bounded, injectable for deterministic tests, and fails closed on ambiguity.
"""

from __future__ import annotations

import contextlib
import datetime as _dt
import hashlib
import ipaddress
import json
import os
import random
import re
import socket
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Sequence

try:
    from backend.object_store import ObjectMetadata, ObjectStore, ObjectStoreError
except ModuleNotFoundError:  # direct invocation from tools/
    from object_store import ObjectMetadata, ObjectStore, ObjectStoreError


SCHEMA_VERSION = "valuepilot-argentina-acquisition-v1"
POLICY_VERSION = "valuepilot-argentina-acquisition-policy-v1"
PROVIDER = "ARGENTINA_SEPA_PRECIOS_CLAROS"
DATASET_ID = "produccion_6f47ec76-d1ce-4e34-a7e1-621fe9b1d0b5"
DATASET_NAME = "produccion-precios-claros---base-sepa"
DATASET_TITLE = "Precios Claros - Base SEPA"
LICENSE_TITLE = "Creative Commons Attribution 4.0"
CATALOG_ENDPOINT = "https://datos.gob.ar/api/3/action/package_show?id=produccion_6f47ec76-d1ce-4e34-a7e1-621fe9b1d0b5"
CATALOG_HOST = "datos.gob.ar"
SOURCE_HOST = "datos.produccion.gob.ar"
SOURCE_HOST_URL = f"https://{SOURCE_HOST}"
MAX_METADATA_BYTES = 2 * 1024 * 1024
# The qualified source is about 325 MB compressed.  512 MiB gives meaningful
# operational headroom while retaining a hard, reviewable upper bound.
MAX_SOURCE_BYTES = 512 * 1024 * 1024
DOWNLOAD_CHUNK_BYTES = 1024 * 1024
MAX_RETRIES = 3
RETRY_BACKOFF_SECONDS = 0.5
RETRY_JITTER_SECONDS = 0.25
METADATA_RETRY_BACKOFF_SECONDS = 0.5
TRANSIENT_HTTP_STATUSES = frozenset({408, 425, 429, 500, 502, 503, 504})
METADATA_RETRY_POLICY_VERSION = "valuepilot-argentina-metadata-retry-v1"
MAX_RESOURCES = 64
MAX_RESOURCE_NAME_BYTES = 256
MAX_DESCRIPTION_BYTES = 4096
WEEKDAY_NAMES = frozenset({"lunes", "martes", "miercoles", "miércoles", "jueves", "viernes", "sabado", "sábado", "domingo"})
# Argentina's current official civil time is UTC-3.  Candidate selection uses
# this only to choose which weekday-labelled endpoint to try first.  It never
# establishes release identity or freshness; the downloaded ZIP's internally
# proven date and SHA-256 remain authoritative.
ARGENTINA_UTC_OFFSET = _dt.timedelta(hours=-3)
ARGENTINA_WEEKDAY_ALIASES = (
    frozenset({"lunes"}),
    frozenset({"martes"}),
    frozenset({"miercoles", "miércoles"}),
    frozenset({"jueves"}),
    frozenset({"viernes"}),
    frozenset({"sabado", "sábado"}),
    frozenset({"domingo"}),
)
_SHA256 = re.compile(r"^[0-9a-f]{64}$")
_DATE = re.compile(r"(?<!\d)(20\d{2}-\d{2}-\d{2})(?!\d)")


class AcquisitionError(RuntimeError):
    """An acquisition operation failed closed."""

    def __init__(
        self,
        message: str,
        *,
        code: str = "ACQUISITION_FAILED",
        http_status: int | None = None,
        attempts: int | None = None,
        cause_code: str | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.http_status = http_status
        self.attempts = attempts
        self.cause_code = cause_code


def canonical_bytes(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path | str) -> str:
    digest = hashlib.sha256()
    try:
        with Path(path).open("rb") as handle:
            for chunk in iter(lambda: handle.read(DOWNLOAD_CHUNK_BYTES), b""):
                digest.update(chunk)
    except OSError as exc:
        raise AcquisitionError("source file is unavailable", code="SOURCE_UNAVAILABLE") from exc
    return digest.hexdigest()


def _iso_timestamp(value: str, *, field: str = "observedAt") -> str:
    if not isinstance(value, str) or not value.strip():
        raise AcquisitionError(f"{field} is required", code="INVALID_METADATA")
    text = value.strip()
    try:
        parsed = _dt.datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as exc:
        raise AcquisitionError(f"{field} is not an ISO timestamp", code="INVALID_METADATA") from exc
    if parsed.tzinfo is None:
        raise AcquisitionError(f"{field} must include a timezone", code="INVALID_METADATA")
    return parsed.astimezone(_dt.timezone.utc).isoformat().replace("+00:00", "Z")


def _iso_date(value: Any, *, field: str) -> str | None:
    if value is None or value == "":
        return None
    if not isinstance(value, str):
        raise AcquisitionError(f"{field} is invalid", code="INVALID_METADATA")
    try:
        return _dt.date.fromisoformat(value.strip()).isoformat()
    except ValueError as exc:
        raise AcquisitionError(f"{field} is not an ISO date", code="INVALID_METADATA") from exc


def _optional_timestamp(value: Any, *, field: str) -> str | None:
    if value is None or value == "":
        return None
    text = str(value).strip()
    try:
        parsed = _dt.datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as exc:
        raise AcquisitionError(f"{field} is not an ISO timestamp", code="INVALID_METADATA") from exc
    # CKAN commonly emits ``last_modified`` without a timezone.  Preserve
    # that source fact exactly; it is discovery evidence, never an assumed UTC
    # observation or release identity.
    return parsed.isoformat().replace("+00:00", "Z") if parsed.tzinfo is not None else parsed.isoformat()


def _normalise_name(value: str) -> str:
    return unicodedata.normalize("NFKC", value).strip().casefold()


def _reject_private_address(host: str) -> None:
    lowered = host.casefold().rstrip(".")
    if lowered in {"localhost", "localhost.localdomain"} or lowered.endswith(".localhost") or lowered.endswith(".local"):
        raise AcquisitionError("source host is local", code="UNSAFE_SOURCE_URL")
    try:
        address = ipaddress.ip_address(lowered)
    except ValueError:
        return
    if address.is_private or address.is_loopback or address.is_link_local or address.is_reserved or address.is_multicast or address.is_unspecified:
            raise AcquisitionError("source host resolves to a private or local address", code="UNSAFE_SOURCE_URL")


def _system_resolver(host: str, port: int) -> Iterable[Any]:
    """Resolve one official host using the system resolver for network calls."""

    return socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)


def validate_official_url(
    url: str,
    *,
    expected_host: str,
    resolver: Callable[[str, int], Iterable[Any]] | None = None,
) -> str:
    """Validate an HTTPS URL and its official host before opening it.

    ``resolver`` is injectable so tests can exercise DNS/SSRF policy without
    depending on the public network.  Production callers should leave it at
    the default, which checks all addresses returned by ``getaddrinfo``.
    """

    if not isinstance(url, str) or len(url) > 4096:
        raise AcquisitionError("source URL is invalid", code="UNSAFE_SOURCE_URL")
    try:
        parsed = urllib.parse.urlsplit(url)
    except ValueError as exc:
        raise AcquisitionError("source URL is invalid", code="UNSAFE_SOURCE_URL") from exc
    host = (parsed.hostname or "").casefold().rstrip(".")
    expected = expected_host.casefold().rstrip(".")
    if parsed.scheme.casefold() != "https" or parsed.username is not None or parsed.password is not None:
        raise AcquisitionError("source URL must be HTTPS without credentials", code="UNSAFE_SOURCE_URL")
    if not host or host != expected:
        raise AcquisitionError("source URL is outside the official host policy", code="UNSAFE_SOURCE_URL")
    _reject_private_address(host)
    if resolver is not None:
        try:
            addresses = list(resolver(host, 443))
        except OSError as exc:
            raise AcquisitionError("official host DNS lookup failed", code="UNSAFE_SOURCE_URL") from exc
        if not addresses:
            raise AcquisitionError("official host has no public address", code="UNSAFE_SOURCE_URL")
        for entry in addresses:
            raw_address = entry[4][0] if isinstance(entry, tuple) and len(entry) > 4 and isinstance(entry[4], tuple) else entry
            try:
                address = ipaddress.ip_address(str(raw_address))
            except ValueError as exc:
                raise AcquisitionError("official host DNS answer is invalid", code="UNSAFE_SOURCE_URL") from exc
            if address.is_private or address.is_loopback or address.is_link_local or address.is_reserved or address.is_multicast or address.is_unspecified:
                raise AcquisitionError("official host resolves to a private or local address", code="UNSAFE_SOURCE_URL")
    return urllib.parse.urlunsplit(("https", host, parsed.path or "/", parsed.query, ""))


class _OfficialRedirectHandler(urllib.request.HTTPRedirectHandler):
    def __init__(self, expected_host: str, resolver: Callable[[str, int], Iterable[Any]] | None) -> None:
        super().__init__()
        self.expected_host = expected_host
        self.resolver = resolver

    def redirect_request(self, req: urllib.request.Request, fp: Any, code: int, msg: str, headers: Any, newurl: str) -> urllib.request.Request | None:
        target = urllib.parse.urljoin(req.full_url, newurl)
        validate_official_url(target, expected_host=self.expected_host, resolver=self.resolver)
        return super().redirect_request(req, fp, code, msg, headers, target)


def _response_context(response: Any):
    if hasattr(response, "__enter__") and hasattr(response, "__exit__"):
        return response
    return contextlib.closing(response)


def _read_bounded(response: Any, *, maximum: int) -> bytes:
    chunks: list[bytes] = []
    total = 0
    while True:
        chunk = response.read(min(DOWNLOAD_CHUNK_BYTES, maximum - total + 1))
        if not chunk:
            break
        if not isinstance(chunk, bytes):
            chunk = bytes(chunk)
        total += len(chunk)
        if total > maximum:
            raise AcquisitionError("metadata response exceeds bound", code="METADATA_TOO_LARGE")
        chunks.append(chunk)
    return b"".join(chunks)


def _http_open(url: str, *, expected_host: str, timeout: float, resolver: Callable[[str, int], Iterable[Any]] | None = None) -> Any:
    effective_resolver = resolver or _system_resolver
    validated = validate_official_url(url, expected_host=expected_host, resolver=effective_resolver)
    request = urllib.request.Request(validated, headers={"Accept": "application/json, application/zip", "User-Agent": "ValuePilot-release-worker/1"})
    opener = urllib.request.build_opener(_OfficialRedirectHandler(expected_host, effective_resolver))
    try:
        return opener.open(request, timeout=timeout)
    except urllib.error.HTTPError as exc:
        code = "UPSTREAM_TRANSIENT_HTTP" if exc.code in TRANSIENT_HTTP_STATUSES else "HTTP_ERROR"
        raise AcquisitionError(
            f"official source returned HTTP {exc.code}",
            code=code,
            http_status=exc.code,
        ) from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise AcquisitionError("official source request failed", code="NETWORK_ERROR") from exc


def _default_fetch_metadata(url: str, *, timeout: float, resolver: Callable[[str, int], Iterable[Any]] | None) -> bytes:
    with _response_context(_http_open(url, expected_host=CATALOG_HOST, timeout=timeout, resolver=resolver)) as response:
        status = getattr(response, "status", getattr(response, "code", 200))
        if status != 200:
            code = "UPSTREAM_TRANSIENT_HTTP" if status in TRANSIENT_HTTP_STATUSES else "HTTP_ERROR"
            raise AcquisitionError(f"metadata returned HTTP {status}", code=code, http_status=status)
        length = response.headers.get("Content-Length") if getattr(response, "headers", None) is not None else None
        if length is not None:
            try:
                if int(length) > MAX_METADATA_BYTES:
                    raise AcquisitionError("metadata response exceeds bound", code="METADATA_TOO_LARGE")
            except ValueError as exc:
                raise AcquisitionError("metadata content length is invalid", code="INVALID_METADATA") from exc
        return _read_bounded(response, maximum=MAX_METADATA_BYTES)


@dataclass(frozen=True)
class AcquisitionCandidate:
    provider: str
    dataset_id: str
    resource_id: str
    resource_name: str
    official_url: str
    observed_size: int | None
    last_modified: str | None
    observed_at: str
    expected_source_host: str
    discovery_response_sha256: str
    release_date_hint: str | None = None
    description: str | None = None
    format: str = "ZIP"

    def as_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "provider": self.provider,
            "datasetId": self.dataset_id,
            "resourceId": self.resource_id,
            "resourceName": self.resource_name,
            "officialUrl": self.official_url,
            "observedSize": self.observed_size,
            "lastModified": self.last_modified,
            "observedAt": self.observed_at,
            "expectedSourceHost": self.expected_source_host,
            "discoveryResponseSha256": self.discovery_response_sha256,
            "releaseDateHint": self.release_date_hint,
            "description": self.description,
            "format": self.format,
        }


@dataclass(frozen=True)
class DiscoveryResult:
    endpoint: str
    response_sha256: str
    observed_at: str
    dataset_id: str
    dataset_name: str
    dataset_title: str
    license_title: str
    candidates: tuple[AcquisitionCandidate, ...]
    attempts: int = 1

    def as_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "endpoint": self.endpoint,
            "responseSha256": self.response_sha256,
            "observedAt": self.observed_at,
            "datasetId": self.dataset_id,
            "datasetName": self.dataset_name,
            "datasetTitle": self.dataset_title,
            "licenseTitle": self.license_title,
            "candidates": [candidate.as_dict() for candidate in self.candidates],
            "attempts": self.attempts,
        }


def _resource_date_hint(resource: Mapping[str, Any]) -> str | None:
    for value in (resource.get("description"), resource.get("name")):
        if isinstance(value, str):
            match = _DATE.search(value)
            if match:
                try:
                    return _dt.date.fromisoformat(match.group(1)).isoformat()
                except ValueError:
                    pass
    return None


def _parse_resource(resource: Mapping[str, Any], *, response_sha256: str, observed_at: str, resolver: Callable[[str, int], Iterable[Any]] | None) -> AcquisitionCandidate | None:
    name = resource.get("name")
    resource_id = resource.get("id")
    url = resource.get("url")
    fmt = resource.get("format")
    if not isinstance(name, str) or not name.strip() or len(name.encode("utf-8")) > MAX_RESOURCE_NAME_BYTES:
        raise AcquisitionError("resource name is invalid", code="INVALID_METADATA")
    if not isinstance(resource_id, str) or not resource_id.strip() or len(resource_id) > 256:
        raise AcquisitionError("resource ID is invalid", code="INVALID_METADATA")
    if not isinstance(url, str):
        raise AcquisitionError("resource URL is missing", code="INVALID_METADATA")
    format_name = str(fmt or "").strip().upper()
    if format_name != "ZIP" and not url.casefold().split("?", 1)[0].endswith(".zip"):
        return None
    if _normalise_name(name) not in WEEKDAY_NAMES:
        return None
    official_url = validate_official_url(url, expected_host=SOURCE_HOST, resolver=resolver)
    observed_size = resource.get("size")
    if observed_size is not None:
        if isinstance(observed_size, bool) or not isinstance(observed_size, int) or observed_size < 0 or observed_size > MAX_SOURCE_BYTES:
            raise AcquisitionError("resource size is invalid or oversized", code="INVALID_METADATA")
    description = resource.get("description")
    if description is not None and (not isinstance(description, str) or len(description.encode("utf-8")) > MAX_DESCRIPTION_BYTES):
        raise AcquisitionError("resource description is invalid", code="INVALID_METADATA")
    return AcquisitionCandidate(
        provider=PROVIDER,
        dataset_id=DATASET_ID,
        resource_id=resource_id.strip(),
        resource_name=name.strip(),
        official_url=official_url,
        observed_size=observed_size,
        last_modified=_optional_timestamp(resource.get("last_modified"), field="last_modified"),
        observed_at=observed_at,
        expected_source_host=SOURCE_HOST,
        discovery_response_sha256=response_sha256,
        release_date_hint=_resource_date_hint(resource),
        description=description,
        format="ZIP",
    )


def discover_metadata(
    *,
    observed_at: str,
    endpoint: str = CATALOG_ENDPOINT,
    fetcher: Callable[[str], bytes] | None = None,
    resolver: Callable[[str, int], Iterable[Any]] | None = None,
    max_retries: int = MAX_RETRIES,
    sleep_fn: Callable[[float], None] = time.sleep,
) -> DiscoveryResult:
    """Read and validate the official CKAN package metadata.

    The caller supplies ``observed_at`` explicitly so reports and tests never
    depend on an implicit wall clock.  A production scheduler should pass its
    execution timestamp.
    """

    observed = _iso_timestamp(observed_at)
    validate_official_url(endpoint, expected_host=CATALOG_HOST, resolver=resolver)
    if isinstance(max_retries, bool) or not isinstance(max_retries, int) or max_retries < 1:
        raise AcquisitionError("metadata retry bound is invalid", code="INVALID_CONFIGURATION")
    attempts_limit = min(max_retries, MAX_RETRIES)
    raw: bytes | None = None
    last_error: BaseException | None = None
    attempts = 0
    for attempts in range(1, attempts_limit + 1):
        try:
            raw = (
                fetcher(endpoint)
                if fetcher is not None
                else _default_fetch_metadata(endpoint, timeout=20.0, resolver=resolver)
            )
            break
        except Exception as exc:  # noqa: BLE001 - classify, then apply a bounded transport-only retry policy
            last_error = exc
            status = getattr(exc, "http_status", None)
            if isinstance(exc, urllib.error.HTTPError):
                status = exc.code
            transient = (
                isinstance(exc, (TimeoutError, ConnectionError, urllib.error.URLError))
                or (
                    isinstance(exc, AcquisitionError)
                    and exc.code in {"NETWORK_ERROR", "UPSTREAM_TRANSIENT_HTTP"}
                )
                or (isinstance(status, int) and status in TRANSIENT_HTTP_STATUSES)
            )
            if not transient:
                if isinstance(exc, AcquisitionError):
                    raise
                raise AcquisitionError("official metadata response is invalid", code="INVALID_METADATA") from exc
            if attempts >= attempts_limit:
                raise AcquisitionError(
                    "official metadata remained temporarily unavailable after bounded retries",
                    code="UPSTREAM_TEMPORARILY_UNAVAILABLE",
                    http_status=status if isinstance(status, int) else None,
                    attempts=attempts,
                    cause_code=getattr(exc, "code", "NETWORK_ERROR"),
                ) from exc
            sleep_fn(METADATA_RETRY_BACKOFF_SECONDS * (2 ** (attempts - 1)))
    if raw is None:
        raise AcquisitionError(
            "official metadata remained temporarily unavailable after bounded retries",
            code="UPSTREAM_TEMPORARILY_UNAVAILABLE",
            attempts=attempts,
            cause_code=getattr(last_error, "code", "NETWORK_ERROR"),
        ) from last_error
    if not isinstance(raw, bytes) or len(raw) > MAX_METADATA_BYTES:
        raise AcquisitionError("metadata response exceeds bound", code="METADATA_TOO_LARGE")
    response_sha256 = sha256_bytes(raw)
    try:
        payload = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AcquisitionError("metadata response is not valid UTF-8 JSON", code="INVALID_METADATA") from exc
    if not isinstance(payload, Mapping) or payload.get("success") is not True:
        raise AcquisitionError("metadata API did not return success", code="INVALID_METADATA")
    result = payload.get("result")
    if not isinstance(result, Mapping):
        raise AcquisitionError("metadata result is missing", code="INVALID_METADATA")
    dataset_id = result.get("id")
    dataset_name = result.get("name")
    dataset_title = result.get("title")
    if dataset_id != DATASET_ID or dataset_name != DATASET_NAME or dataset_title != DATASET_TITLE:
        raise AcquisitionError("metadata dataset identity is not the official Base SEPA dataset", code="SOURCE_CONTRACT_CHANGED")
    license_title = result.get("license_title") or result.get("license_id")
    if license_title != LICENSE_TITLE:
        raise AcquisitionError("metadata licence changed or is absent", code="SOURCE_CONTRACT_CHANGED")
    resources = result.get("resources")
    if not isinstance(resources, list) or len(resources) > MAX_RESOURCES:
        raise AcquisitionError("metadata resources are invalid or oversized", code="INVALID_METADATA")
    candidates = tuple(sorted((candidate for resource in resources if isinstance(resource, Mapping) for candidate in (_parse_resource(resource, response_sha256=response_sha256, observed_at=observed, resolver=resolver),) if candidate is not None), key=lambda candidate: (candidate.release_date_hint or "", candidate.last_modified or "", candidate.resource_name.casefold(), candidate.resource_id)))
    if not candidates:
        raise AcquisitionError("metadata contains no eligible weekday ZIP resources", code="SOURCE_CONTRACT_CHANGED")
    return DiscoveryResult(
        endpoint,
        response_sha256,
        observed,
        dataset_id,
        dataset_name,
        dataset_title,
        license_title,
        candidates,
        attempts,
    )


def select_latest_candidate(candidates: Sequence[AcquisitionCandidate]) -> AcquisitionCandidate:
    """Select the Argentina-local weekday resource before stale metadata hints.

    CKAN ``releaseDateHint`` and ``lastModified`` values are discovery evidence
    only and can lag the rotating weekday ZIPs.  All candidates in one
    discovery result must share the same explicit observation timestamp.  When
    exactly one candidate matches the Argentina-local weekday, use it.  If the
    expected weekday is absent, preserve the prior deterministic metadata-hint
    fallback; the downloaded ZIP's internally proven date/hash and the later
    source-classification gate still prevent an older release from promotion.
    """

    if not candidates:
        raise AcquisitionError("no acquisition candidates are available", code="NO_CANDIDATE")

    observed_values = {_iso_timestamp(candidate.observed_at) for candidate in candidates}
    if len(observed_values) != 1:
        raise AcquisitionError("candidate observation timestamps differ", code="INVALID_METADATA")
    observed_utc = _dt.datetime.fromisoformat(next(iter(observed_values)).replace("Z", "+00:00"))
    argentina_local = observed_utc + ARGENTINA_UTC_OFFSET
    expected_aliases = ARGENTINA_WEEKDAY_ALIASES[argentina_local.weekday()]
    weekday_matches = [candidate for candidate in candidates if _normalise_name(candidate.resource_name) in expected_aliases]
    if len(weekday_matches) == 1:
        return weekday_matches[0]
    if len(weekday_matches) > 1:
        raise AcquisitionError("multiple candidates match the Argentina-local weekday", code="AMBIGUOUS_CANDIDATE")

    return max(candidates, key=lambda candidate: (candidate.release_date_hint or "", candidate.last_modified or "", candidate.resource_name.casefold(), candidate.resource_id))


@dataclass(frozen=True)
class DownloadedSource:
    candidate: AcquisitionCandidate
    path: Path
    bytes: int
    sha256: str
    final_url: str
    attempts: int

    def as_dict(self) -> dict[str, Any]:
        return {"candidate": self.candidate.as_dict(), "path": str(self.path), "bytes": self.bytes, "sha256": self.sha256, "finalUrl": self.final_url, "attempts": self.attempts}


def _retryable(error: BaseException) -> bool:
    # A size mismatch against the authoritative metadata is deterministic and
    # must not be retried as if it were a transient transport failure.
    return isinstance(error, (TimeoutError, ConnectionError, urllib.error.URLError)) or (
        isinstance(error, AcquisitionError)
        and error.code in {"NETWORK_ERROR", "UPSTREAM_TRANSIENT_HTTP", "DOWNLOAD_TRANSIENT"}
    )


def _open_download(url: str, *, timeout: float, resolver: Callable[[str, int], Iterable[Any]] | None) -> Any:
    return _http_open(url, expected_host=SOURCE_HOST, timeout=timeout, resolver=resolver)


def download_candidate(
    candidate: AcquisitionCandidate,
    destination: Path | str,
    *,
    opener: Callable[..., Any] | None = None,
    resolver: Callable[[str, int], Iterable[Any]] | None = None,
    timeout: float = 120.0,
    max_bytes: int = MAX_SOURCE_BYTES,
    max_retries: int = MAX_RETRIES,
    sleep_fn: Callable[[float], None] = time.sleep,
    random_fn: Callable[[], float] = random.random,
) -> DownloadedSource:
    """Stream one official ZIP to an atomic destination with bounded retries."""

    if not isinstance(max_bytes, int) or max_bytes <= 0 or max_bytes > MAX_SOURCE_BYTES:
        raise AcquisitionError("download bound is invalid", code="INVALID_CONFIGURATION")
    validate_official_url(candidate.official_url, expected_host=SOURCE_HOST, resolver=resolver)
    if candidate.observed_size is not None and candidate.observed_size > max_bytes:
        raise AcquisitionError("official source exceeds configured size bound", code="SOURCE_TOO_LARGE")
    if candidate.observed_size == 0:
        raise AcquisitionError("official source is empty", code="EMPTY_SOURCE")
    target = Path(destination).resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    last_error: BaseException | None = None
    attempts_limit = max(1, min(int(max_retries), MAX_RETRIES))
    for attempt in range(1, attempts_limit + 1):
        temporary: Path | None = None
        try:
            fd, name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".part", dir=str(target.parent))
            os.close(fd)
            temporary = Path(name)
            open_response = opener(candidate.official_url, timeout=timeout) if opener is not None else _open_download(candidate.official_url, timeout=timeout, resolver=resolver)
            with _response_context(open_response) as response, temporary.open("wb") as output:
                final_url = str(getattr(response, "geturl", lambda: candidate.official_url)())
                validate_official_url(final_url, expected_host=SOURCE_HOST, resolver=resolver)
                status = getattr(response, "status", getattr(response, "code", 200))
                if status in TRANSIENT_HTTP_STATUSES:
                    raise AcquisitionError(f"official source returned HTTP {status}", code="DOWNLOAD_TRANSIENT")
                if status != 200:
                    raise AcquisitionError(f"official source returned HTTP {status}", code="HTTP_ERROR")
                headers = getattr(response, "headers", None)
                content_length = headers.get("Content-Length") if headers is not None else None
                if content_length is not None:
                    try:
                        if int(content_length) > max_bytes:
                            raise AcquisitionError("official source exceeds configured size bound", code="SOURCE_TOO_LARGE")
                    except ValueError as exc:
                        raise AcquisitionError("source content length is invalid", code="DOWNLOAD_INVALID") from exc
                total = 0
                digest = hashlib.sha256()
                while True:
                    chunk = response.read(DOWNLOAD_CHUNK_BYTES)
                    if not chunk:
                        break
                    if not isinstance(chunk, bytes):
                        chunk = bytes(chunk)
                    total += len(chunk)
                    if total > max_bytes:
                        raise AcquisitionError("official source exceeds configured size bound", code="SOURCE_TOO_LARGE")
                    output.write(chunk)
                    digest.update(chunk)
                if total <= 0:
                    raise AcquisitionError("official source is empty", code="EMPTY_SOURCE")
                if candidate.observed_size is not None and total != candidate.observed_size:
                    raise AcquisitionError("downloaded source size differs from metadata", code="SOURCE_TRUNCATED")
                output.flush()
                os.fsync(output.fileno())
                digest_hex = digest.hexdigest()
            os.replace(temporary, target)
            temporary = None
            return DownloadedSource(candidate, target, total, digest_hex, final_url, attempt)
        except BaseException as exc:  # noqa: BLE001 - preserve bounded retry policy
            last_error = exc
            if temporary is not None:
                temporary.unlink(missing_ok=True)
            if not _retryable(exc) or attempt >= attempts_limit:
                if isinstance(exc, AcquisitionError):
                    raise
                raise AcquisitionError("official source download failed", code="DOWNLOAD_FAILED") from exc
            delay = RETRY_BACKOFF_SECONDS * (2 ** (attempt - 1)) + RETRY_JITTER_SECONDS * max(0.0, min(1.0, float(random_fn())))
            sleep_fn(delay)
    raise AcquisitionError("official source download failed", code="DOWNLOAD_FAILED") from last_error


@dataclass(frozen=True)
class SourceDecision:
    disposition: str
    reason: str
    release_date: str
    source_sha256: str
    current_release_date: str | None
    current_source_sha256: str | None

    def as_dict(self) -> dict[str, Any]:
        return {"disposition": self.disposition, "reason": self.reason, "releaseDate": self.release_date, "sourceSha256": self.source_sha256, "currentReleaseDate": self.current_release_date, "currentSourceSha256": self.current_source_sha256}


def classify_source(
    *,
    release_date: str,
    source_sha256: str,
    current_release_date: str | None,
    current_source_sha256: str | None,
    upstream_changed: bool = False,
) -> SourceDecision:
    """Apply the four explicit freshness/idempotency cases."""

    parsed_date = _iso_date(release_date, field="release_date")
    if parsed_date is None or not isinstance(source_sha256, str) or not _SHA256.fullmatch(source_sha256):
        raise AcquisitionError("source identity is invalid", code="INVALID_SOURCE_IDENTITY")
    current_date = _iso_date(current_release_date, field="current_release_date") if current_release_date else None
    if current_source_sha256 is not None and not _SHA256.fullmatch(current_source_sha256):
        raise AcquisitionError("current source hash is invalid", code="INVALID_SOURCE_IDENTITY")
    if current_date is None:
        return SourceDecision("NEWER_RELEASE", "no current release is recorded", parsed_date, source_sha256, None, current_source_sha256)
    if parsed_date < current_date:
        return SourceDecision("OLDER_REJECTED", "source release date is older than active release", parsed_date, source_sha256, current_date, current_source_sha256)
    if parsed_date > current_date:
        return SourceDecision("NEWER_RELEASE", "source release date is newer than active release", parsed_date, source_sha256, current_date, current_source_sha256)
    if source_sha256 == current_source_sha256:
        return SourceDecision("NO_CHANGE", "same release date and source hash", parsed_date, source_sha256, current_date, current_source_sha256)
    if not upstream_changed:
        return SourceDecision("REVIEW_REQUIRED", "same-date bytes changed without upstream change evidence", parsed_date, source_sha256, current_date, current_source_sha256)
    return SourceDecision("SAME_DAY_REVISION", "same-date bytes changed and upstream change evidence is present", parsed_date, source_sha256, current_date, current_source_sha256)


@dataclass(frozen=True)
class ArchiveResult:
    key: str
    manifest_key: str
    sha256: str
    bytes: int
    reused: bool
    metadata: ObjectMetadata

    def as_dict(self) -> dict[str, Any]:
        return {"key": self.key, "manifestKey": self.manifest_key, "sha256": self.sha256, "bytes": self.bytes, "reused": self.reused}


class PrivateSourceArchive:
    """Content-addressed, operator-only source archive with no deletes."""

    def __init__(self, store: ObjectStore, *, prefix: str = "source-archive") -> None:
        self.store = store
        self.prefix = prefix.strip("/")
        if not self.prefix or any(part in {"", ".", ".."} for part in self.prefix.split("/")):
            raise AcquisitionError("archive prefix is invalid", code="INVALID_CONFIGURATION")

    def archive_file(
        self,
        source: Path | str,
        *,
        candidate: AcquisitionCandidate,
        acquired_at: str,
        release_date: str,
    ) -> ArchiveResult:
        path = Path(source).resolve()
        acquired = _iso_timestamp(acquired_at, field="acquiredAt")
        release = _iso_date(release_date, field="release_date")
        if release is None:
            raise AcquisitionError("release date is required", code="INVALID_SOURCE_IDENTITY")
        digest = sha256_file(path)
        try:
            size = path.stat().st_size
        except OSError as exc:
            raise AcquisitionError("source file is unavailable", code="SOURCE_UNAVAILABLE") from exc
        key = f"{self.prefix}/sha256/{digest}.zip"
        manifest_key = f"{self.prefix}/manifests/{digest}.json"
        try:
            existing = self.store.head(key) if self.store.exists(key) else None
            if existing is not None:
                if existing.size != size or (existing.sha256 is not None and existing.sha256 != digest):
                    raise AcquisitionError("existing source archive differs", code="ARCHIVE_CONFLICT")
                if existing.sha256 is None and sha256_bytes(self.store.get(key)) != digest:
                    raise AcquisitionError("existing source archive differs", code="ARCHIVE_CONFLICT")
                reused = True
            else:
                existing = self.store.put_immutable_file(key, path, sha256=digest)
                reused = False
            manifest = {
                "schemaVersion": SCHEMA_VERSION,
                "policyVersion": POLICY_VERSION,
                "provider": PROVIDER,
                "datasetId": candidate.dataset_id,
                "resourceId": candidate.resource_id,
                "resourceName": candidate.resource_name,
                "officialUrl": candidate.official_url,
                # Keep the complete bounded discovery record beside the
                # content-addressed bytes.  The resource metadata is evidence
                # about acquisition, never a release identity or freshness
                # decision.
                "candidate": candidate.as_dict(),
                "discoveryResponseSha256": candidate.discovery_response_sha256,
                "observedAt": candidate.observed_at,
                "observedSize": candidate.observed_size,
                "lastModified": candidate.last_modified,
                "releaseDateHint": candidate.release_date_hint,
                "description": candidate.description,
                "sourceSha256": digest,
                "bytes": size,
                "acquiredAt": acquired,
                "releaseDate": release,
                "archiveKey": key,
                "rawProviderDataCommitted": False,
                "runtimePublicationAllowed": False,
            }
            manifest_raw = canonical_bytes(manifest) + b"\n"
            if self.store.exists(manifest_key):
                try:
                    existing_manifest = json.loads(self.store.get(manifest_key))
                except (ObjectStoreError, UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise AcquisitionError("existing source archive manifest is invalid", code="ARCHIVE_CONFLICT") from exc
                if not isinstance(existing_manifest, Mapping) or existing_manifest.get("sourceSha256") != digest or existing_manifest.get("archiveKey") != key:
                    raise AcquisitionError("existing source archive manifest differs", code="ARCHIVE_CONFLICT")
                # ``acquiredAt`` may legitimately differ on a retry that
                # reuses the same archive.  Every other recorded identity and
                # source-metadata field must agree when an older manifest has
                # the field; legacy manifests without the newer evidence are
                # retained rather than rewritten in place.
                immutable_fields = (
                    "schemaVersion",
                    "policyVersion",
                    "provider",
                    "datasetId",
                    "resourceId",
                    "resourceName",
                    "officialUrl",
                    "sourceSha256",
                    "bytes",
                    "releaseDate",
                    "archiveKey",
                    "rawProviderDataCommitted",
                    "runtimePublicationAllowed",
                )
                if any(field in existing_manifest and existing_manifest.get(field) != manifest.get(field) for field in immutable_fields):
                    raise AcquisitionError("existing source archive manifest differs", code="ARCHIVE_CONFLICT")
                existing_candidate = existing_manifest.get("candidate")
                if isinstance(existing_candidate, Mapping):
                    current_candidate = candidate.as_dict()
                    candidate_identity_fields = ("provider", "datasetId", "resourceId", "resourceName", "officialUrl", "expectedSourceHost", "format")
                    if any(field in existing_candidate and existing_candidate.get(field) != current_candidate.get(field) for field in candidate_identity_fields):
                        raise AcquisitionError("existing source archive candidate differs", code="ARCHIVE_CONFLICT")
            else:
                self.store.put_immutable(manifest_key, manifest_raw, sha256=sha256_bytes(manifest_raw))
            return ArchiveResult(key, manifest_key, digest, size, reused, existing)
        except ObjectStoreError as exc:
            raise AcquisitionError("source archive operation failed", code="ARCHIVE_FAILED") from exc

    def restore_file(
        self,
        key: str,
        destination: Path | str,
        *,
        expected_sha256: str,
        max_bytes: int = MAX_SOURCE_BYTES,
    ) -> Path:
        """Restore one archived ZIP with bounded streaming and exact hash checks."""

        if not isinstance(expected_sha256, str) or not _SHA256.fullmatch(expected_sha256):
            raise AcquisitionError("archive source hash is invalid", code="ARCHIVE_CONFLICT")
        expected_key = f"{self.prefix}/sha256/{expected_sha256}.zip"
        if key != expected_key:
            raise AcquisitionError("archive key is not content-addressed", code="ARCHIVE_CONFLICT")
        if not isinstance(max_bytes, int) or max_bytes <= 0 or max_bytes > MAX_SOURCE_BYTES:
            raise AcquisitionError("archive restore bound is invalid", code="INVALID_CONFIGURATION")
        manifest_key = f"{self.prefix}/manifests/{expected_sha256}.json"
        try:
            manifest_raw = self.store.get(manifest_key)
            manifest = json.loads(manifest_raw)
            metadata = self.store.head(key)
        except (ObjectStoreError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise AcquisitionError("source archive is unavailable", code="ARCHIVE_UNAVAILABLE") from exc
        if not isinstance(manifest, Mapping) or manifest.get("archiveKey") != key or manifest.get("sourceSha256") != expected_sha256:
            raise AcquisitionError("source archive manifest is invalid", code="ARCHIVE_CONFLICT")
        if manifest.get("rawProviderDataCommitted") is not False or manifest.get("runtimePublicationAllowed") is not False:
            raise AcquisitionError("source archive manifest has unsafe publication flags", code="ARCHIVE_CONFLICT")
        if metadata.size <= 0 or metadata.size > max_bytes:
            raise AcquisitionError("source archive exceeds configured bound", code="SOURCE_TOO_LARGE")
        if "bytes" in manifest and manifest.get("bytes") != metadata.size:
            raise AcquisitionError("source archive manifest size is inconsistent", code="ARCHIVE_CONFLICT")
        target = Path(destination).resolve()
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary: Path | None = None
        try:
            fd, name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".part", dir=str(target.parent))
            temporary = Path(name)
            digest = hashlib.sha256()
            total = 0
            with os.fdopen(fd, "wb") as output:
                for chunk in self.store.stream(key, chunk_size=DOWNLOAD_CHUNK_BYTES):
                    if not isinstance(chunk, bytes):
                        chunk = bytes(chunk)
                    total += len(chunk)
                    if total > max_bytes:
                        raise AcquisitionError("source archive exceeds configured bound", code="SOURCE_TOO_LARGE")
                    output.write(chunk)
                    digest.update(chunk)
                output.flush()
                os.fsync(output.fileno())
            if total != metadata.size or total <= 0:
                raise AcquisitionError("source archive size is inconsistent", code="ARCHIVE_CONFLICT")
            if digest.hexdigest() != expected_sha256:
                raise AcquisitionError("source archive hash is inconsistent", code="ARCHIVE_CONFLICT")
            os.replace(temporary, target)
            temporary = None
            return target
        except ObjectStoreError as exc:
            raise AcquisitionError("source archive restore failed", code="ARCHIVE_FAILED") from exc
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)


__all__ = [
    "AcquisitionCandidate",
    "AcquisitionError",
    "ArchiveResult",
    "CATALOG_ENDPOINT",
    "CATALOG_HOST",
    "DATASET_ID",
    "DATASET_NAME",
    "DATASET_TITLE",
    "DiscoveryResult",
    "DownloadedSource",
    "LICENSE_TITLE",
    "MAX_SOURCE_BYTES",
    "POLICY_VERSION",
    "PROVIDER",
    "PrivateSourceArchive",
    "SOURCE_HOST",
    "SourceDecision",
    "canonical_bytes",
    "classify_source",
    "discover_metadata",
    "download_candidate",
    "select_latest_candidate",
    "sha256_file",
    "validate_official_url",
]

"""Bounded Google Cloud Run service-to-service adapter for M12.2.

This module is deliberately an edge transport adapter.  It obtains a short-
lived Google-signed ID token through Application Default Credentials, sends it
only to one explicitly allow-listed private M11 Cloud Run destination, and
streams a bounded JSON response back to the existing consumer-edge seam.
Shopping, ranking, product, offer, promotion, availability, and release
semantics remain owned by M11.
"""

from __future__ import annotations

import asyncio
import inspect
import os
import re
import threading
from collections.abc import Awaitable, Callable, Mapping
from dataclasses import dataclass
from typing import Any, Protocol
from urllib.parse import urlsplit

from backend.consumer_edge import EDGE_ALLOWED_ROUTES, EdgeUpstreamResponse


_ROUTE_METHODS = {
    "/v1/status": "GET",
    "/v1/search": "POST",
    "/v1/interpret": "POST",
    "/v1/shop-text": "POST",
    "/v1/shop": "POST",
}
_CLOUD_RUN_HOST_RE = re.compile(r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.run\.app\Z")
_REQUEST_ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}\Z")
_DEFAULT_MAX_TOKEN_BYTES = 8192
_DEFAULT_TIMEOUT_SECONDS = 12.0
_DEFAULT_TOKEN_FETCH_TIMEOUT_SECONDS = 4.0


class CloudRunConfigurationError(ValueError):
    """The configured private M11 destination is not trusted or bounded."""


class CloudRunAuthenticationError(RuntimeError):
    """A Google OIDC token could not be obtained or was malformed."""


class CloudRunResponseTooLarge(RuntimeError):
    """The private backend response exceeded the edge response bound."""


class OidcTokenProvider(Protocol):
    """Obtains one short-lived token for an explicit audience."""

    def get_token(self, audience: str) -> str | Awaitable[str]:
        ...


def _https_run_host(value: str, *, field_name: str) -> str:
    if not isinstance(value, str) or not value:
        raise CloudRunConfigurationError(f"{field_name} is required")
    try:
        parts = urlsplit(value)
        port = parts.port
    except ValueError as exc:
        raise CloudRunConfigurationError(f"{field_name} is invalid") from exc
    if parts.scheme.lower() != "https" or parts.username or parts.password or port is not None:
        raise CloudRunConfigurationError(f"{field_name} must be an HTTPS URL without credentials or a port")
    if parts.path not in ("", "/") or parts.query or parts.fragment:
        raise CloudRunConfigurationError(f"{field_name} must not contain a path, query, or fragment")
    host = parts.hostname
    if host is None or not _CLOUD_RUN_HOST_RE.fullmatch(host.lower()):
        raise CloudRunConfigurationError(f"{field_name} must use an exact Cloud Run run.app host")
    return host.lower()


def _normalized_allowed_hosts(values: frozenset[str]) -> frozenset[str]:
    if not isinstance(values, frozenset) or not values:
        raise CloudRunConfigurationError("allowed_hosts must be a non-empty frozenset")
    normalized: set[str] = set()
    for value in values:
        if not isinstance(value, str) or not _CLOUD_RUN_HOST_RE.fullmatch(value.strip().lower()):
            raise CloudRunConfigurationError("allowed_hosts contains an invalid host")
        normalized.add(value.strip().lower())
    return frozenset(normalized)


@dataclass(frozen=True)
class CloudRunTarget:
    """One immutable, exact Cloud Run destination and OIDC audience."""

    base_url: str
    audience: str
    allowed_hosts: frozenset[str]
    max_response_bytes: int = 512 * 1024
    timeout_seconds: float = _DEFAULT_TIMEOUT_SECONDS
    max_token_bytes: int = _DEFAULT_MAX_TOKEN_BYTES

    def __post_init__(self) -> None:
        base_host = _https_run_host(self.base_url, field_name="base_url")
        audience_host = _https_run_host(self.audience, field_name="audience")
        allowed_hosts = _normalized_allowed_hosts(self.allowed_hosts)
        if base_host not in allowed_hosts or audience_host not in allowed_hosts:
            raise CloudRunConfigurationError("base_url and audience must be in allowed_hosts")
        if audience_host != base_host:
            raise CloudRunConfigurationError("audience must match the private M11 host")
        if not isinstance(self.max_response_bytes, int) or isinstance(self.max_response_bytes, bool) or self.max_response_bytes <= 0:
            raise CloudRunConfigurationError("max_response_bytes must be positive")
        if self.max_response_bytes > 4 * 1024 * 1024:
            raise CloudRunConfigurationError("max_response_bytes exceeds the hard edge bound")
        if not isinstance(self.timeout_seconds, (int, float)) or isinstance(self.timeout_seconds, bool) or self.timeout_seconds <= 0:
            raise CloudRunConfigurationError("timeout_seconds must be positive")
        if self.timeout_seconds > 30:
            raise CloudRunConfigurationError("timeout_seconds exceeds the hard upstream bound")
        if not isinstance(self.max_token_bytes, int) or isinstance(self.max_token_bytes, bool) or self.max_token_bytes <= 0:
            raise CloudRunConfigurationError("max_token_bytes must be positive")
        if self.max_token_bytes > 32 * 1024:
            raise CloudRunConfigurationError("max_token_bytes exceeds the hard token bound")
        object.__setattr__(self, "base_url", f"https://{base_host}")
        object.__setattr__(self, "audience", f"https://{audience_host}")
        object.__setattr__(self, "allowed_hosts", allowed_hosts)

    def url_for(self, path: str) -> str:
        """Build one of the five fixed M11 URLs; arbitrary paths are refused."""

        if path not in EDGE_ALLOWED_ROUTES:
            raise CloudRunConfigurationError("path is not an allowed consumer route")
        return f"{self.base_url}{path}"


class GoogleApplicationDefaultTokenProvider:
    """Fetch Google-signed ID tokens without static keys or reusable secrets."""

    def __init__(
        self,
        *,
        fetcher: Callable[[str], str] | None = None,
        request_timeout_seconds: float = _DEFAULT_TOKEN_FETCH_TIMEOUT_SECONDS,
    ) -> None:
        if (
            not isinstance(request_timeout_seconds, (int, float))
            or isinstance(request_timeout_seconds, bool)
            or request_timeout_seconds <= 0
            or request_timeout_seconds > 10
        ):
            raise CloudRunConfigurationError("request_timeout_seconds is invalid")
        self._fetcher = fetcher
        self._request_timeout_seconds = float(request_timeout_seconds)

    def _fetch_with_adc(self, audience: str) -> str:
        try:
            from google.auth.transport.requests import Request
            from google.oauth2 import id_token

            request = Request()

            def bounded_request(
                url: str,
                method: str = "GET",
                body: bytes | None = None,
                headers: Mapping[str, str] | None = None,
                timeout: float | None = None,
                **kwargs: Any,
            ):
                requested_timeout = self._request_timeout_seconds if timeout is None else min(
                    self._request_timeout_seconds, float(timeout)
                )
                return request(url, method=method, body=body, headers=headers, timeout=requested_timeout, **kwargs)

            return id_token.fetch_id_token(bounded_request, audience)
        except Exception as exc:  # noqa: BLE001 - auth failure must fail closed
            raise CloudRunAuthenticationError("Google OIDC token acquisition failed") from exc

    async def get_token(self, audience: str) -> str:
        fetcher = self._fetcher or self._fetch_with_adc
        try:
            token = await asyncio.to_thread(fetcher, audience)
        except CloudRunAuthenticationError:
            raise
        except Exception as exc:  # noqa: BLE001 - auth failure must fail closed
            raise CloudRunAuthenticationError("Google OIDC token acquisition failed") from exc
        if not isinstance(token, str) or not token:
            raise CloudRunAuthenticationError("Google OIDC token is invalid")
        return token


class CloudRunOidcUpstream:
    """Forward the already-validated consumer request to private M11."""

    def __init__(
        self,
        target: CloudRunTarget,
        *,
        token_provider: OidcTokenProvider | None = None,
        client: Any | None = None,
    ) -> None:
        if not isinstance(target, CloudRunTarget):
            raise TypeError("target must be a CloudRunTarget")
        self._target = target
        self._token_provider = token_provider or GoogleApplicationDefaultTokenProvider()
        self._owns_client = client is None
        self._client_lock = threading.Lock()
        self._client = client
        if client is not None and not hasattr(client, "stream"):
            raise TypeError("client must provide an async stream method")

    @property
    def target(self) -> CloudRunTarget:
        return self._target

    async def aclose(self) -> None:
        if self._owns_client and self._client is not None and hasattr(self._client, "aclose"):
            result = self._client.aclose()
            if inspect.isawaitable(result):
                await result

    def _client_instance(self) -> Any:
        if self._client is None:
            with self._client_lock:
                if self._client is None:
                    try:
                        import httpx

                        self._client = httpx.AsyncClient(
                            timeout=httpx.Timeout(self._target.timeout_seconds),
                            trust_env=False,
                        )
                    except ImportError as exc:  # pragma: no cover - deployment packaging failure
                        raise CloudRunConfigurationError("httpx is unavailable") from exc
        return self._client

    async def forward(
        self,
        *,
        method: str,
        path: str,
        body: bytes,
        headers: Mapping[str, str],
        request_id: str,
    ) -> EdgeUpstreamResponse:
        expected_method = _ROUTE_METHODS.get(path)
        if expected_method != method:
            raise CloudRunConfigurationError("method/path is not an allowed consumer route")
        if not isinstance(body, bytes) or not isinstance(request_id, str) or not _REQUEST_ID_RE.fullmatch(request_id):
            raise CloudRunConfigurationError("upstream request fields are invalid")
        del headers  # Caller headers are never trusted or copied to the private service.
        token_result = self._token_provider.get_token(self._target.audience)
        raw_token = await token_result if inspect.isawaitable(token_result) else token_result
        if not isinstance(raw_token, str) or not raw_token or any(char.isspace() for char in raw_token):
            raise CloudRunAuthenticationError("Google OIDC token is malformed")
        if len(raw_token.encode("utf-8")) > self._target.max_token_bytes:
            raise CloudRunAuthenticationError("Google OIDC token is too large")
        forward_headers = {
            "Authorization": f"Bearer {raw_token}",
            "Accept": "application/json",
            "X-Request-ID": request_id,
        }
        if method == "POST":
            forward_headers["Content-Type"] = "application/json"
        async with self._client_instance().stream(
            method,
            self._target.url_for(path),
            headers=forward_headers,
            content=body,
        ) as response:
            chunks: list[bytes] = []
            total = 0
            async for chunk in response.aiter_bytes():
                if not isinstance(chunk, (bytes, bytearray)):
                    raise CloudRunConfigurationError("private M11 response chunk is invalid")
                total += len(chunk)
                if total > self._target.max_response_bytes:
                    raise CloudRunResponseTooLarge("private M11 response exceeds the edge limit")
                if chunk:
                    chunks.append(bytes(chunk))
            content_type = response.headers.get("content-type", "application/json")
            return EdgeUpstreamResponse(
                status_code=response.status_code,
                body=b"".join(chunks),
                content_type=content_type,
            )


def create_cloud_run_upstream_from_env(
    *,
    max_response_bytes: int = 512 * 1024,
    timeout_seconds: float = _DEFAULT_TIMEOUT_SECONDS,
) -> CloudRunOidcUpstream:
    """Create a fail-closed M11 adapter from explicit deployment settings."""

    base_url = os.environ.get("VALUEPILOT_M11_BASE_URL", "")
    audience = os.environ.get("VALUEPILOT_M11_AUDIENCE", "")
    raw_hosts = os.environ.get("VALUEPILOT_M11_ALLOWED_HOSTS", "")
    allowed_hosts = frozenset(value.strip().lower() for value in raw_hosts.split(",") if value.strip())
    if not base_url or not audience or not allowed_hosts:
        raise CloudRunConfigurationError(
            "VALUEPILOT_M11_BASE_URL, VALUEPILOT_M11_AUDIENCE, and VALUEPILOT_M11_ALLOWED_HOSTS are required"
        )
    return CloudRunOidcUpstream(
        CloudRunTarget(
            base_url=base_url,
            audience=audience,
            allowed_hosts=allowed_hosts,
            max_response_bytes=max_response_bytes,
            timeout_seconds=timeout_seconds,
        )
    )


__all__ = [
    "CloudRunAuthenticationError",
    "CloudRunConfigurationError",
    "CloudRunOidcUpstream",
    "CloudRunResponseTooLarge",
    "CloudRunTarget",
    "GoogleApplicationDefaultTokenProvider",
    "OidcTokenProvider",
    "create_cloud_run_upstream_from_env",
]

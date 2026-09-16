"""Bounded public consumer-edge boundary for the Argentina backend.

This module deliberately contains no shopping or provider logic.  It is a
small, fail-closed boundary that authenticates App Check requests, enforces
transport/payload limits, and forwards only the five consumer routes through
an injected upstream interface.  The private M11 invocation is a later,
separately qualified stage.
"""

from __future__ import annotations

import asyncio
import inspect
import json
import logging
import re
import threading
import time
import uuid
from collections.abc import Awaitable, Callable, Mapping
from dataclasses import dataclass, field
from typing import Any, Protocol

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

LOGGER = logging.getLogger("valuepilot.consumer_edge")

EDGE_CONTRACT_VERSION = "valuepilot-argentina-consumer-edge-v1"
EDGE_ALLOWED_ROUTES = frozenset(
    {
        "/v1/status",
        "/v1/search",
        "/v1/interpret",
        "/v1/shop-text",
        "/v1/shop",
    }
)
_ROUTE_METHODS = {
    "/v1/status": "GET",
    "/v1/search": "POST",
    "/v1/interpret": "POST",
    "/v1/shop-text": "POST",
    "/v1/shop": "POST",
}
_REQUEST_ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,95}\Z")
_PROJECT_ID_RE = re.compile(r"[a-z][a-z0-9-]{4,61}[a-z0-9]\Z")


class EdgeRequestError(ValueError):
    """A request failed a deterministic edge-boundary check."""


class EdgeRequestTooLarge(EdgeRequestError):
    """A request exceeded the configured byte bound."""


class AppCheckRejected(ValueError):
    """The App Check token was absent, malformed, or not trusted."""


@dataclass(frozen=True)
class EdgeConfig:
    """Explicit, bounded edge policy.

    The defaults are intentionally small for the first consumer edge.  They
    are configuration rather than hidden business policy and can be changed
    only with a separately tested deployment decision.
    """

    max_request_bytes: int = 16 * 1024
    max_response_bytes: int = 512 * 1024
    max_token_bytes: int = 4096
    max_json_depth: int = 24
    # The bound is larger than a normal request (M11 caps a basket at ten
    # lines) because a valid bounded response may contain a decision frontier
    # or many nearby offers.  The byte cap remains the primary response bound.
    max_json_array_items: int = 4096
    max_json_object_keys: int = 64
    max_json_string_bytes: int = 4096
    require_https: bool = True
    max_concurrent_requests: int = 16
    queue_timeout_seconds: float = 0.05
    upstream_timeout_seconds: float = 5.0
    rate_limit_requests: int = 60
    rate_limit_window_seconds: float = 60.0
    rate_limit_keys: int = 1024

    def __post_init__(self) -> None:
        integer_fields = (
            "max_request_bytes",
            "max_response_bytes",
            "max_token_bytes",
            "max_json_depth",
            "max_json_array_items",
            "max_json_object_keys",
            "max_json_string_bytes",
            "max_concurrent_requests",
            "rate_limit_requests",
            "rate_limit_keys",
        )
        for name in integer_fields:
            value = getattr(self, name)
            if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
                raise ValueError(f"{name} must be a positive integer")
        for name in ("queue_timeout_seconds", "upstream_timeout_seconds", "rate_limit_window_seconds"):
            value = getattr(self, name)
            if not isinstance(value, (int, float)) or isinstance(value, bool) or value <= 0:
                raise ValueError(f"{name} must be positive")
        if not isinstance(self.require_https, bool):
            raise ValueError("require_https must be a boolean")
        if self.max_response_bytes > 4 * 1024 * 1024:
            raise ValueError("max_response_bytes exceeds the hard edge bound")
        if self.max_request_bytes > 256 * 1024:
            raise ValueError("max_request_bytes exceeds the hard edge bound")
        if self.queue_timeout_seconds > self.upstream_timeout_seconds:
            raise ValueError("queue timeout cannot exceed upstream timeout")


class AppCheckVerifier(Protocol):
    """Verifies a Firebase App Check token without exposing token material."""

    def verify(self, token: str) -> Mapping[str, Any] | Awaitable[Mapping[str, Any]]:
        ...


@dataclass(frozen=True)
class EdgeUpstreamResponse:
    """The minimal response shape an upstream adapter may return."""

    status_code: int
    body: bytes
    content_type: str = "application/json"
    headers: Mapping[str, str] = field(default_factory=dict)


class EdgeUpstream(Protocol):
    """The provider-neutral upstream seam used by the public edge."""

    def forward(
        self,
        *,
        method: str,
        path: str,
        body: bytes,
        headers: Mapping[str, str],
        request_id: str,
    ) -> EdgeUpstreamResponse | Awaitable[EdgeUpstreamResponse]:
        ...


class FirebaseAdminAppCheckVerifier:
    """Production App Check adapter backed by the Firebase Admin SDK.

    Imports are lazy so the M11 private service does not need the Firebase
    dependency.  The SDK verifies the token signature, issuer, expiry and
    project audience.  The edge additionally requires the App Check subject
    (the registered App ID) and can enforce an explicit allow-list.
    """

    def __init__(self, project_id: str, *, allowed_app_ids: frozenset[str] = frozenset()) -> None:
        if not isinstance(project_id, str) or not _PROJECT_ID_RE.fullmatch(project_id):
            raise ValueError("project_id is invalid")
        if any(not isinstance(value, str) or not value or len(value) > 256 for value in allowed_app_ids):
            raise ValueError("allowed_app_ids contains an invalid value")
        self._project_id = project_id
        self._allowed_app_ids = frozenset(allowed_app_ids)
        self._app: Any | None = None
        self._lock = threading.Lock()

    def _firebase_app(self) -> Any:
        try:
            import firebase_admin
        except ImportError as exc:  # pragma: no cover - exercised by deployment packaging
            raise AppCheckRejected("firebase-admin is unavailable") from exc

        with self._lock:
            if self._app is not None:
                app = self._app
            else:
                try:
                    app = firebase_admin.get_app()
                except ValueError:
                    try:
                        app = firebase_admin.initialize_app(options={"projectId": self._project_id})
                    except Exception as exc:  # noqa: BLE001 - fail closed at the security boundary
                        raise AppCheckRejected("firebase app initialization failed") from exc
                actual_project_id = getattr(app, "project_id", None)
                if actual_project_id not in (None, self._project_id):
                    raise AppCheckRejected("firebase project mismatch")
                self._app = app
        return app

    def verify(self, token: str) -> Mapping[str, Any]:
        if not isinstance(token, str) or not token or len(token.encode("utf-8")) > 4096 or any(char.isspace() for char in token):
            raise AppCheckRejected("token is malformed")
        try:
            from firebase_admin import app_check

            claims = app_check.verify_token(token, app=self._firebase_app())
        except AppCheckRejected:
            raise
        except Exception as exc:  # noqa: BLE001 - never disclose verifier details
            raise AppCheckRejected("token verification failed") from exc
        if not isinstance(claims, Mapping):
            raise AppCheckRejected("token claims are invalid")
        app_id = claims.get("sub")
        if not isinstance(app_id, str) or not app_id or len(app_id) > 256:
            raise AppCheckRejected("token subject is invalid")
        if self._allowed_app_ids and app_id not in self._allowed_app_ids:
            raise AppCheckRejected("token app is not allowed")
        return dict(claims)


@dataclass
class _RateEntry:
    window_start: float
    count: int
    last_seen: float


class BoundedRateLimiter:
    """A small per-instance limiter with deterministic bounded eviction."""

    def __init__(self, *, limit: int, window_seconds: float, max_keys: int, clock: Callable[[], float] = time.monotonic) -> None:
        if limit <= 0 or window_seconds <= 0 or max_keys <= 0:
            raise ValueError("rate limiter bounds must be positive")
        self._limit = limit
        self._window_seconds = window_seconds
        self._max_keys = max_keys
        self._clock = clock
        self._entries: dict[str, _RateEntry] = {}
        self._lock = threading.Lock()

    @property
    def size(self) -> int:
        with self._lock:
            return len(self._entries)

    def allow(self, key: str, *, now: float | None = None) -> bool:
        if not isinstance(key, str) or not key:
            raise ValueError("rate-limit key is required")
        normalized = key[:384]
        current = self._clock() if now is None else now
        with self._lock:
            entry = self._entries.get(normalized)
            if entry is None:
                if len(self._entries) >= self._max_keys:
                    oldest_key = min(self._entries, key=lambda item: (self._entries[item].last_seen, item))
                    del self._entries[oldest_key]
                self._entries[normalized] = _RateEntry(current, 1, current)
                return True
            if current - entry.window_start >= self._window_seconds:
                entry.window_start = current
                entry.count = 1
                entry.last_seen = current
                return True
            entry.last_seen = current
            if entry.count >= self._limit:
                return False
            entry.count += 1
            return True


def _reject_json_constant(value: str) -> None:
    raise ValueError(f"non-finite JSON value {value}")


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON object key")
        result[key] = value
    return result


def _validate_json_value(value: Any, *, depth: int, config: EdgeConfig) -> None:
    if depth > config.max_json_depth:
        raise EdgeRequestError("JSON nesting is too deep")
    if isinstance(value, dict):
        if len(value) > config.max_json_object_keys:
            raise EdgeRequestError("JSON object is too large")
        for key, child in value.items():
            if not isinstance(key, str) or len(key.encode("utf-8")) > config.max_json_string_bytes:
                raise EdgeRequestError("JSON key is too long")
            _validate_json_value(child, depth=depth + 1, config=config)
    elif isinstance(value, list):
        if len(value) > config.max_json_array_items:
            raise EdgeRequestError("JSON array is too large")
        for child in value:
            _validate_json_value(child, depth=depth + 1, config=config)
    elif isinstance(value, str):
        if len(value.encode("utf-8")) > config.max_json_string_bytes:
            raise EdgeRequestError("JSON string is too long")
    elif isinstance(value, float) and not (value == value and abs(value) != float("inf")):
        raise EdgeRequestError("JSON number is not finite")


def parse_bounded_json(
    body: bytes,
    *,
    config: EdgeConfig = EdgeConfig(),
    max_bytes: int | None = None,
) -> Mapping[str, Any]:
    """Parse one bounded UTF-8 JSON object with deterministic duplicate checks."""

    byte_limit = config.max_request_bytes if max_bytes is None else max_bytes
    if not isinstance(byte_limit, int) or isinstance(byte_limit, bool) or byte_limit <= 0:
        raise ValueError("max_bytes must be a positive integer")
    if not isinstance(body, bytes) or len(body) > byte_limit:
        raise EdgeRequestTooLarge("request body exceeds the edge limit")
    if not body:
        raise EdgeRequestError("request body is empty")
    try:
        value = json.loads(
            body.decode("utf-8"),
            object_pairs_hook=_reject_duplicate_keys,
            parse_constant=_reject_json_constant,
        )
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError, ValueError) as exc:
        raise EdgeRequestError("request JSON is invalid") from exc
    _validate_json_value(value, depth=0, config=config)
    if not isinstance(value, Mapping):
        raise EdgeRequestError("request JSON must be an object")
    return value


async def _read_bounded_body(request: Any, *, config: EdgeConfig) -> bytes:
    """Read at most max_request_bytes + 1 bytes from a Starlette request."""

    content_length = request.headers.get("content-length")
    if content_length is not None:
        if not content_length.isdigit():
            raise EdgeRequestError("content length is invalid")
        if int(content_length) > config.max_request_bytes:
            raise EdgeRequestTooLarge("request body exceeds the edge limit")
    chunks: list[bytes] = []
    total = 0
    async for chunk in request.stream():
        if not isinstance(chunk, (bytes, bytearray)):
            raise EdgeRequestError("request body chunk is invalid")
        total += len(chunk)
        if total > config.max_request_bytes:
            raise EdgeRequestTooLarge("request body exceeds the edge limit")
        if chunk:
            chunks.append(bytes(chunk))
    return b"".join(chunks)


def _is_json_content_type(value: str | None) -> bool:
    if not isinstance(value, str):
        return False
    parts = [part.strip().lower() for part in value.split(";")]
    if parts[0] != "application/json":
        return False
    return all(part == "charset=utf-8" for part in parts[1:])


def _request_id(request: Any) -> str:
    value = request.headers.get("x-request-id")
    if isinstance(value, str) and _REQUEST_ID_RE.fullmatch(value):
        return value
    return f"edge-{uuid.uuid4().hex}"


def _is_https_request(request: Any) -> bool:
    """Return whether the request arrived through the trusted HTTPS proxy.

    Cloud Run terminates TLS before forwarding HTTP to the container, so the
    platform communicates the original scheme through ``X-Forwarded-Proto``.
    A direct ASGI HTTPS request is accepted as well.  The production
    deployment must keep this process behind Cloud Run (or another trusted
    proxy that overwrites this header); the edge is not intended to be
    exposed directly on a plaintext socket.
    """

    url = getattr(request, "url", None)
    scheme = str(getattr(url, "scheme", "")).lower()
    if scheme == "https":
        return True
    forwarded = request.headers.get("x-forwarded-proto")
    if not isinstance(forwarded, str):
        return False
    # Multiple/ambiguous proxy values are rejected rather than guessing.
    return forwarded.strip().lower() == "https"


def _security_headers(request_id: str, *, allow: str | None = None) -> dict[str, str]:
    headers = {
        "X-Request-ID": request_id,
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
        "X-Frame-Options": "DENY",
        "Referrer-Policy": "no-referrer",
        "Content-Security-Policy": "default-src 'none'",
        "Strict-Transport-Security": "max-age=31536000",
    }
    if allow is not None:
        headers["Allow"] = allow
    return headers


async def _maybe_await(value: Any) -> Any:
    if inspect.isawaitable(value):
        return await value
    return value


def _verified_claims(claims: Any) -> Mapping[str, Any]:
    if not isinstance(claims, Mapping):
        raise AppCheckRejected("claims are invalid")
    app_id = claims.get("sub")
    if not isinstance(app_id, str) or not app_id or len(app_id) > 256:
        raise AppCheckRejected("claims do not identify an app")
    return claims


def _client_key(request: Any, claims: Mapping[str, Any]) -> str:
    app_id = str(claims["sub"])
    client = getattr(request, "client", None)
    host = getattr(client, "host", "unknown") if client is not None else "unknown"
    if not isinstance(host, str) or not host:
        host = "unknown"
    return f"{app_id[:256]}|{host[:96]}"


def create_consumer_edge_app(
    *,
    verifier: AppCheckVerifier,
    upstream: EdgeUpstream,
    config: EdgeConfig = EdgeConfig(),
    clock: Callable[[], float] = time.monotonic,
):
    """Create the public-edge FastAPI application.

    ``verifier`` and ``upstream`` are explicit dependencies so tests can use
    deterministic fakes.  No default can accidentally bypass App Check or
    point the public edge directly at the private M11 service.
    """

    if verifier is None or upstream is None:
        raise ValueError("verifier and upstream are required")
    app = FastAPI(
        title="ValuePilot Argentina Consumer Edge",
        version=EDGE_CONTRACT_VERSION,
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    limiter = BoundedRateLimiter(
        limit=config.rate_limit_requests,
        window_seconds=config.rate_limit_window_seconds,
        max_keys=config.rate_limit_keys,
        clock=clock,
    )
    concurrency = asyncio.BoundedSemaphore(config.max_concurrent_requests)

    def json_error(request_id: str, status_code: int, code: str, *, allow: str | None = None) -> JSONResponse:
        return JSONResponse(
            {"error": code},
            status_code=status_code,
            headers=_security_headers(request_id, allow=allow),
        )

    async def verify_request(request: Request) -> Mapping[str, Any]:
        token = request.headers.get("x-firebase-appcheck", "")
        if not token or len(token.encode("utf-8")) > config.max_token_bytes or any(char.isspace() for char in token):
            raise AppCheckRejected("token is missing or malformed")
        claims = await _maybe_await(verifier.verify(token))
        return _verified_claims(claims)

    async def forward_response(request_id: str, response: Any) -> Response:
        if not isinstance(response, EdgeUpstreamResponse):
            return json_error(request_id, 502, "UPSTREAM_INVALID_RESPONSE")
        if not isinstance(response.status_code, int) or isinstance(response.status_code, bool) or not 100 <= response.status_code <= 599:
            return json_error(request_id, 502, "UPSTREAM_INVALID_RESPONSE")
        if not isinstance(response.body, (bytes, bytearray)):
            return json_error(request_id, 502, "UPSTREAM_INVALID_RESPONSE")
        if len(response.body) > config.max_response_bytes:
            return json_error(request_id, 502, "UPSTREAM_RESPONSE_TOO_LARGE")
        body = bytes(response.body)
        if 200 <= response.status_code < 300:
            if not _is_json_content_type(response.content_type):
                return json_error(request_id, 502, "UPSTREAM_INVALID_RESPONSE")
            try:
                parse_bounded_json(body, config=config, max_bytes=config.max_response_bytes)
            except EdgeRequestError:
                return json_error(request_id, 502, "UPSTREAM_INVALID_RESPONSE")
            return Response(
                content=body,
                status_code=response.status_code,
                headers=_security_headers(request_id),
                media_type="application/json",
            )
        if response.status_code in {401, 403}:
            code = "UPSTREAM_UNAUTHENTICATED"
        elif response.status_code == 429:
            code = "UPSTREAM_RATE_LIMITED"
        elif response.status_code >= 500:
            code = "UPSTREAM_UNAVAILABLE"
        else:
            code = "UPSTREAM_REJECTED"
        return json_error(request_id, response.status_code, code)

    @app.api_route(
        "/{path:path}",
        methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"],
    )
    async def dispatch(request: Request, path: str):  # noqa: ARG001 - route is intentionally catch-all for the allow-list
        request_id = _request_id(request)
        started = clock()
        status_code = 500
        known_route = request.url.path in EDGE_ALLOWED_ROUTES
        acquired = False
        try:
            route = request.url.path
            if config.require_https and not _is_https_request(request):
                status_code = 400
                return json_error(request_id, status_code, "HTTPS_REQUIRED")
            expected_method = _ROUTE_METHODS.get(route)
            if expected_method is None:
                status_code = 404
                return json_error(request_id, status_code, "NOT_FOUND")
            if request.url.query:
                status_code = 400
                return json_error(request_id, status_code, "QUERY_PARAMETERS_NOT_ALLOWED")
            if request.method != expected_method:
                status_code = 405
                return json_error(request_id, status_code, "METHOD_NOT_ALLOWED", allow=expected_method)
            try:
                await asyncio.wait_for(concurrency.acquire(), timeout=config.queue_timeout_seconds)
                acquired = True
            except asyncio.TimeoutError:
                status_code = 503
                return json_error(request_id, status_code, "EDGE_BUSY")
            try:
                claims = await verify_request(request)
            except Exception as exc:  # noqa: BLE001 - authentication failures are intentionally indistinguishable
                LOGGER.debug("app_check_rejected type=%s", type(exc).__name__)
                status_code = 401
                return json_error(request_id, status_code, "UNAUTHENTICATED")
            if not limiter.allow(_client_key(request, claims)):
                status_code = 429
                return json_error(request_id, status_code, "RATE_LIMITED")

            body = b""
            if request.method == "GET":
                body = await _read_bounded_body(request, config=config)
                if body:
                    status_code = 400
                    return json_error(request_id, status_code, "GET_BODY_NOT_ALLOWED")
            else:
                if not _is_json_content_type(request.headers.get("content-type")):
                    status_code = 415
                    return json_error(request_id, status_code, "UNSUPPORTED_MEDIA_TYPE")
                body = await _read_bounded_body(request, config=config)
                try:
                    parse_bounded_json(body, config=config)
                except EdgeRequestTooLarge:
                    status_code = 413
                    return json_error(request_id, status_code, "REQUEST_TOO_LARGE")
                except EdgeRequestError:
                    status_code = 400
                    return json_error(request_id, status_code, "INVALID_JSON")

            forward_headers = {"accept": "application/json", "x-request-id": request_id}
            if request.method == "POST":
                forward_headers["content-type"] = "application/json"
            try:
                upstream_result = await asyncio.wait_for(
                    _maybe_await(
                        upstream.forward(
                            method=request.method,
                            path=route,
                            body=body,
                            headers=forward_headers,
                            request_id=request_id,
                        )
                    ),
                    timeout=config.upstream_timeout_seconds,
                )
            except asyncio.TimeoutError:
                status_code = 504
                return json_error(request_id, status_code, "UPSTREAM_TIMEOUT")
            except Exception as exc:  # noqa: BLE001 - upstream details never cross the public edge
                LOGGER.warning("upstream_failure type=%s", type(exc).__name__)
                status_code = 503
                return json_error(request_id, status_code, "UPSTREAM_UNAVAILABLE")
            response = await forward_response(request_id, upstream_result)
            status_code = response.status_code
            return response
        except EdgeRequestTooLarge:
            status_code = 413
            return json_error(request_id, status_code, "REQUEST_TOO_LARGE")
        except EdgeRequestError:
            status_code = 400
            return json_error(request_id, status_code, "INVALID_REQUEST")
        except Exception as exc:  # noqa: BLE001 - the edge must fail closed
            LOGGER.warning("edge_failure type=%s", type(exc).__name__)
            status_code = 500
            return json_error(request_id, status_code, "EDGE_FAILURE")
        finally:
            if acquired:
                concurrency.release()
            elapsed_ms = max(0.0, (clock() - started) * 1000.0)
            LOGGER.info(
                "consumer_request request_id=%s route=%s status=%d duration_ms=%.3f",
                request_id,
                request.url.path if known_route else "<blocked>",
                status_code,
                elapsed_ms,
            )

    return app


__all__ = [
    "AppCheckRejected",
    "AppCheckVerifier",
    "BoundedRateLimiter",
    "EDGE_ALLOWED_ROUTES",
    "EDGE_CONTRACT_VERSION",
    "EdgeConfig",
    "EdgeRequestError",
    "EdgeRequestTooLarge",
    "EdgeUpstream",
    "EdgeUpstreamResponse",
    "FirebaseAdminAppCheckVerifier",
    "create_consumer_edge_app",
    "parse_bounded_json",
]

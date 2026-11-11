"""Operator-side destination and smoke adapters for Argentina releases.

This module contains only bounded wiring around the provider-neutral release
promotion controller.  It is intentionally separate from the source parser
and is never imported by the Android application.  A shadow run constructs no
remote clients and performs no writes.
"""

from __future__ import annotations

import argparse
import ipaddress
import json
import os
import sys
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

try:
    from backend.object_store import GCSObjectStore, LocalFilesystemObjectStore, ObjectStore, ObjectStoreError, S3CompatibleObjectStore
    from tools.argentina_release_automation import ACTIVATION_PARTICIPANTS, RELEASE_MODES, run_release_promotion
except ModuleNotFoundError:  # direct invocation from tools/
    from backend.object_store import GCSObjectStore, LocalFilesystemObjectStore, ObjectStore, ObjectStoreError, S3CompatibleObjectStore
    from argentina_release_automation import ACTIVATION_PARTICIPANTS, RELEASE_MODES, run_release_promotion


PROMOTER_SCHEMA_VERSION = "valuepilot-argentina-release-promoter-v1"
MAX_JSON_BYTES = 128 * 1024
MAX_SMOKE_BYTES = 64 * 1024
MAX_SMOKE_TIMEOUT_SECONDS = 30


class PromotionAdapterError(RuntimeError):
    """A destination or smoke adapter failed closed."""

    def __init__(self, message: str, *, code: str = "PROMOTION_ADAPTER_FAILED") -> None:
        super().__init__(message)
        self.code = code


def read_bounded_json(path: Path | str, *, label: str, maximum: int = MAX_JSON_BYTES) -> dict[str, Any]:
    """Read one small operator-supplied JSON evidence document."""

    source = Path(path)
    try:
        raw = source.read_bytes()
    except OSError as exc:
        raise PromotionAdapterError(f"{label} is unavailable", code="INVALID_PROMOTION_INPUT") from exc
    if len(raw) > maximum:
        raise PromotionAdapterError(f"{label} exceeds its bound", code="INVALID_PROMOTION_INPUT")
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PromotionAdapterError(f"{label} is invalid JSON", code="INVALID_PROMOTION_INPUT") from exc
    if not isinstance(value, dict):
        raise PromotionAdapterError(f"{label} must be an object", code="INVALID_PROMOTION_INPUT")
    return value


def _local_store_root(root: Path | str) -> dict[str, ObjectStore]:
    """Build the deterministic three-store local mirror used by tests/operators."""

    base = Path(root).resolve()
    working = Path.cwd().resolve()
    if base == working or base in working.parents or working in base.parents:
        raise PromotionAdapterError("promotion store root must be outside the working tree", code="INVALID_PROMOTION_INPUT")
    base.mkdir(parents=True, exist_ok=True)
    return {name: LocalFilesystemObjectStore(base / name) for name in ACTIVATION_PARTICIPANTS}


def build_promotion_stores(
    *,
    local_root: Path | str | None = None,
    us_bucket: str | None = None,
    sa_bucket: str | None = None,
    r2_bucket: str | None = None,
    gcs_project: str | None = None,
    r2_endpoint: str | None = None,
    r2_region: str | None = None,
) -> dict[str, ObjectStore]:
    """Construct exactly US, South America, and R2 stores.

    No fallback from a missing production destination to a local path is
    allowed.  Local mirrors are explicit and useful for deterministic tests;
    production runs must name every destination.
    """

    if local_root is not None:
        if any(value for value in (us_bucket, sa_bucket, r2_bucket)):
            raise PromotionAdapterError("local and remote promotion stores are mutually exclusive", code="INVALID_PROMOTION_INPUT")
        return _local_store_root(local_root)
    buckets = {"us": us_bucket, "sa": sa_bucket, "r2": r2_bucket}
    if any(not isinstance(value, str) or not value.strip() for value in buckets.values()):
        raise PromotionAdapterError("all promotion destinations are required", code="INVALID_PROMOTION_INPUT")
    try:
        token = os.environ.get("VALUEPILOT_GCS_ACCESS_TOKEN", "").strip()
        credentials = None
        if token:
            from google.oauth2.credentials import Credentials  # type: ignore

            credentials = Credentials(token=token)

        def gcs(bucket: str) -> GCSObjectStore:
            return GCSObjectStore(bucket=bucket, project=gcs_project or None, credentials=credentials)

        return {
            "us": gcs(str(us_bucket).strip()),
            "sa": gcs(str(sa_bucket).strip()),
            "r2": S3CompatibleObjectStore(
                bucket=str(r2_bucket).strip(),
                endpoint_url=r2_endpoint or None,
                region_name=r2_region or None,
            ),
        }
    except (ImportError, ObjectStoreError, OSError, ValueError, TypeError) as exc:
        raise PromotionAdapterError("promotion destination is unavailable", code="PROMOTION_DESTINATION_UNAVAILABLE") from exc


def _safe_https_url(value: str, *, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise PromotionAdapterError(f"{label} is required", code="INVALID_SMOKE_CONFIGURATION")
    parsed = urlsplit(value.strip())
    if parsed.scheme.casefold() != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.fragment or parsed.query:
        raise PromotionAdapterError(f"{label} must be an HTTPS URL without credentials, queries, or fragments", code="INVALID_SMOKE_CONFIGURATION")
    # Reject literal local/reserved targets.  DNS names remain operator-owned;
    # a production deployment should additionally restrict egress at IAM/VPC.
    try:
        address = ipaddress.ip_address(parsed.hostname)
    except ValueError:
        address = None
    if address is not None and (address.is_private or address.is_loopback or address.is_link_local or address.is_reserved or address.is_multicast):
        raise PromotionAdapterError(f"{label} targets a non-public address", code="INVALID_SMOKE_CONFIGURATION")
    return value.strip()


def _bounded_response(response: Any) -> tuple[int, bytes]:
    try:
        status = int(response.getcode())
        body = response.read(MAX_SMOKE_BYTES + 1)
    except Exception as exc:  # noqa: BLE001 - adapter returns bounded failure
        raise PromotionAdapterError("smoke response is unavailable", code="SMOKE_REQUEST_FAILED") from exc
    if not isinstance(body, bytes):
        body = bytes(body)
    if len(body) > MAX_SMOKE_BYTES:
        raise PromotionAdapterError("smoke response exceeds its bound", code="SMOKE_RESPONSE_INVALID")
    return status, body


def https_smoke_check(url: str, *, opener: Callable[..., Any] | None = None) -> Callable[[str], dict[str, Any]]:
    """Return a bounded HTTPS GET callback for activation smoke checks.

    The callback requires a JSON object whose ``releaseId`` matches the
    candidate and whose status is PASS/OK.  It never treats a 200 response or
    an unlabelled body as proof by itself.
    """

    endpoint = _safe_https_url(url, label="smoke URL")
    open_fn = opener or urlopen

    def check(candidate_release_id: str) -> dict[str, Any]:
        request = Request(
            endpoint,
            headers={
                "Accept": "application/json",
                "User-Agent": "ValuePilot-release-smoke/1",
                "X-ValuePilot-Expected-Release": candidate_release_id,
            },
            method="GET",
        )
        try:
            response = open_fn(request, timeout=MAX_SMOKE_TIMEOUT_SECONDS)
            try:
                status, raw = _bounded_response(response)
            finally:
                close = getattr(response, "close", None)
                if callable(close):
                    close()
        except HTTPError as exc:
            return {"status": "FAILED", "reason": "HTTP_ERROR", "httpStatus": int(exc.code)}
        except (URLError, TimeoutError, OSError, PromotionAdapterError) as exc:
            return {"status": "FAILED", "reason": getattr(exc, "code", "SMOKE_REQUEST_FAILED")}
        if status != 200:
            return {"status": "FAILED", "reason": "HTTP_STATUS", "httpStatus": status}
        try:
            value = json.loads(raw)
        except (UnicodeDecodeError, json.JSONDecodeError):
            return {"status": "FAILED", "reason": "RESPONSE_NOT_JSON", "httpStatus": status}
        if not isinstance(value, Mapping):
            return {"status": "FAILED", "reason": "RESPONSE_NOT_OBJECT", "httpStatus": status}
        if value.get("releaseId") != candidate_release_id:
            return {"status": "FAILED", "reason": "RELEASE_MISMATCH", "httpStatus": status}
        response_status = str(value.get("status", "")).upper()
        if response_status not in {"PASS", "OK", "HEALTHY"}:
            return {"status": "FAILED", "reason": "STATUS_NOT_PASS", "httpStatus": status}
        return {
            "status": "PASS",
            "httpStatus": status,
            "releaseId": candidate_release_id,
            "endpoint": endpoint,
        }

    return check


def build_smoke_checks(
    *,
    us_url: str | None,
    sa_url: str | None,
    r2_url: str | None,
    opener: Callable[..., Any] | None = None,
) -> dict[str, Callable[[str], dict[str, Any]]]:
    values = {"us": us_url, "sa": sa_url, "r2": r2_url}
    if any(not isinstance(value, str) or not value.strip() for value in values.values()):
        raise PromotionAdapterError("all production smoke URLs are required", code="INVALID_SMOKE_CONFIGURATION")
    return {name: https_smoke_check(str(value), opener=opener) for name, value in values.items()}


def _main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workspace", type=Path)
    parser.add_argument("--release-id", action="append", dest="release_ids", required=True)
    parser.add_argument("--candidate-release-id", required=True)
    parser.add_argument("--mode", choices=RELEASE_MODES, default="SHADOW")
    parser.add_argument("--data-canary", type=Path)
    parser.add_argument("--expected-current-release-id")
    parser.add_argument("--store-root", type=Path)
    parser.add_argument("--us-bucket")
    parser.add_argument("--sa-bucket")
    parser.add_argument("--r2-bucket")
    parser.add_argument("--gcs-project")
    parser.add_argument("--r2-endpoint")
    parser.add_argument("--r2-region")
    parser.add_argument("--us-smoke-url")
    parser.add_argument("--sa-smoke-url")
    parser.add_argument("--r2-smoke-url")
    args = parser.parse_args(argv)
    try:
        evidence = read_bounded_json(args.data_canary, label="data-canary evidence") if args.data_canary else None
        stores = None
        smoke = None
        if args.mode != "SHADOW":
            stores = build_promotion_stores(
                local_root=args.store_root,
                us_bucket=args.us_bucket,
                sa_bucket=args.sa_bucket,
                r2_bucket=args.r2_bucket,
                gcs_project=args.gcs_project,
                r2_endpoint=args.r2_endpoint,
                r2_region=args.r2_region,
            )
        if args.mode == "AUTO_ACTIVATE":
            smoke = build_smoke_checks(us_url=args.us_smoke_url, sa_url=args.sa_smoke_url, r2_url=args.r2_smoke_url)
        result = run_release_promotion(
            args.workspace,
            release_ids=args.release_ids,
            candidate_release_id=args.candidate_release_id,
            mode=args.mode,
            stores=stores,
            data_canary=evidence,
            expected_current_release_id=args.expected_current_release_id,
            smoke_checks=smoke,
        )
        print(json.dumps({"schemaVersion": PROMOTER_SCHEMA_VERSION, "result": result}, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0 if result.get("status") in {"SHADOW_ONLY", "CANARY_READY", "ACTIVE"} else 2
    except (PromotionAdapterError, ObjectStoreError, RuntimeError) as exc:
        print(json.dumps({"schemaVersion": PROMOTER_SCHEMA_VERSION, "error": getattr(exc, "code", "PROMOTION_FAILED"), "message": str(exc)}, ensure_ascii=False, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(_main())


__all__ = [
    "MAX_JSON_BYTES",
    "MAX_SMOKE_BYTES",
    "PROMOTER_SCHEMA_VERSION",
    "PromotionAdapterError",
    "build_promotion_stores",
    "build_smoke_checks",
    "https_smoke_check",
    "read_bounded_json",
]

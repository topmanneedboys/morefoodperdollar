"""Bounded public service contract around the backend reader."""

from __future__ import annotations

import json
import logging
import time
import uuid
import threading
from decimal import Decimal, InvalidOperation
from typing import Any, Mapping

from .reader import ArgentinaBackendReader, BackendQueryError, CurrentPriceEvidenceUnavailable
from .release import ReleaseError, ReleaseManager

LOGGER = logging.getLogger("valuepilot.backend")
MAX_REQUEST_BYTES = 16 * 1024
NORMAL_RESPONSE_BYTES = 128 * 1024
HARD_RESPONSE_BYTES = 512 * 1024


def _strip_internal(value: Any) -> Any:
    if isinstance(value, list):
        return [_strip_internal(item) for item in value]
    if not isinstance(value, dict):
        return value
    result = {}
    for key, item in value.items():
        if key in {"queryPlan", "providerItems", "sourceRow", "packageProvenance"}:
            continue
        if key == "provenance" and isinstance(item, dict):
            result[key] = {name: item.get(name) for name in ("provider", "releaseDate", "freshnessStatus") if item.get(name) is not None}
        else:
            result[key] = _strip_internal(item)
    return result


class ResponseLimitError(BackendQueryError):
    code = "RESPONSE_TOO_LARGE"


class BackendService:
    def __init__(self, root: str, *, release_manager: ReleaseManager | None = None):
        self.release_manager = release_manager or ReleaseManager(root)
        self._reader_lock = threading.Lock()
        self._readers: dict[tuple[str, str], ArgentinaBackendReader] = {}

    def _reader(self, handle):
        key = (handle.release_id, str(handle.root))
        with self._reader_lock:
            reader = self._readers.get(key)
            if reader is None:
                reader = ArgentinaBackendReader(handle)
                self._readers[key] = reader
            return reader

    @staticmethod
    def _trusted_keys_from_input(input_result: Mapping[str, Any]) -> dict[str, str] | None:
        """Project only interpreter-qualified identities into the private edge."""

        if input_result.get("safeRequestReady") is not True:
            return None
        lines = input_result.get("lines")
        if not isinstance(lines, list):
            return None
        result: dict[str, str] = {}
        for line in lines:
            if not isinstance(line, Mapping):
                return None
            line_id = line.get("lineId")
            recognized = line.get("recognizedProduct")
            key = recognized.get("productEvidenceKey") if isinstance(recognized, Mapping) else None
            if not isinstance(line_id, str) or not isinstance(key, str) or not key:
                return None
            result[line_id] = key
        return result or None

    def _execute(self, payload: Mapping[str, Any], *, correlation_id: str | None = None):
        request_id = correlation_id or str(uuid.uuid4())
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        decision, metrics = reader.query(payload)
        return request_id, handle, decision, metrics

    @staticmethod
    def _route_ids(reader: ArgentinaBackendReader, payload: Mapping[str, Any]) -> tuple[str, ...]:
        try:
            latitude = Decimal(str(payload.get("latitude")))
            longitude = Decimal(str(payload.get("longitude")))
            radius = Decimal(str(payload.get("radiusKm")))
        except (InvalidOperation, TypeError, ValueError) as exc:
            raise BackendQueryError("latitude, longitude and radiusKm must be numeric") from exc
        try:
            return tuple(selection.region_id for selection in reader.router.route(latitude=latitude, longitude=longitude, radius_km=radius))
        except (BackendQueryError, ValueError) as exc:
            raise BackendQueryError(str(exc)) from exc

    @staticmethod
    def _response_size(response: dict[str, Any]) -> int:
        raw = json.dumps(response, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        if len(raw) > HARD_RESPONSE_BYTES:
            raise ResponseLimitError(ResponseLimitError.code)
        return len(raw)

    def _shop_response(self, request_id: str, handle: Any, decision: Mapping[str, Any], metrics: Any, *, started: float, input_result: Mapping[str, Any] | None = None) -> dict[str, Any]:
        response: dict[str, Any] = {
            "apiContractVersion": "valuepilot-argentina-backend-http-v1",
            "requestId": request_id,
            "releaseId": handle.release_id,
            "releaseDate": handle.release_date,
            "freshness": handle.freshness_status,
            "regionsQueried": metrics.regions_queried,
            "evidenceSemantics": {
                "availability": "UNKNOWN",
                "distance": "STRAIGHT_LINE_HAVERSINE_ONLY",
                "promotions": "UNKNOWN_ELIGIBILITY_NOT_INCLUDED_IN_BASE_TOTAL",
                "pricePublicationIsNotInventory": True,
            },
            "result": _strip_internal(decision),
            "diagnostics": {"serviceMs": round((time.perf_counter() - started) * 1000, 3), "customerResponseBytes": 0},
        }
        if input_result is not None:
            response["input"] = dict(input_result)
        response["diagnostics"]["customerResponseBytes"] = self._response_size(response)
        return response

    def health(self) -> dict[str, str]:
        return {"status": "ok", "service": "valuepilot-argentina-backend"}

    def ready(self) -> tuple[bool, dict[str, Any]]:
        status = self.release_manager.status()
        return status.get("service") == "ready", status

    def status(self) -> dict[str, Any]:
        value = self.release_manager.status()
        value.update({
            "apiContractVersion": "valuepilot-argentina-backend-http-v1",
            "backendCodeQualified": True,
            "liveCloudDeploymentVerified": False,
            "androidNetworkingAuthorized": False,
            "productionAndroidUiAuthorized": False,
            "automatedOfficialAcquisitionProven": False,
        })
        return value

    def shop(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        request_id = correlation_id or str(uuid.uuid4())
        started = time.perf_counter()
        try:
            request_id, handle, decision, metrics = self._execute(payload, correlation_id=request_id)
        except CurrentPriceEvidenceUnavailable:
            LOGGER.info("request=%s release=%s result=current_evidence_unavailable", request_id, handle.release_id)
            raise
        except BackendQueryError:
            LOGGER.info("request=%s release=%s result=invalid_or_failed", request_id, handle.release_id)
            raise
        return self._shop_response(request_id, handle, decision, metrics, started=started)

    def interpret(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        text = payload.get("text")
        if not isinstance(text, str) or not text.strip():
            raise BackendQueryError("text must contain 1-4096 characters")
        if len(text) > 4096:
            raise BackendQueryError("text must contain 1-4096 characters")
        request_id = correlation_id or str(uuid.uuid4())
        started = time.perf_counter()
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        result = reader.interpret_text(text, require_quantities=False)
        response = {
            "apiContractVersion": "valuepilot-argentina-backend-http-v1",
            "requestId": request_id,
            "releaseId": handle.release_id,
            "releaseDate": handle.release_date,
            "freshness": handle.freshness_status,
            "input": result,
            "diagnostics": {"serviceMs": round((time.perf_counter() - started) * 1000, 3), "customerResponseBytes": 0},
        }
        response["diagnostics"]["customerResponseBytes"] = self._response_size(response)
        return response

    def shop_text(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        text = payload.get("text")
        if not isinstance(text, str) or not text.strip() or len(text) > 4096:
            raise BackendQueryError("text must contain 1-4096 characters")
        request_id = correlation_id or str(uuid.uuid4())
        started = time.perf_counter()
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        regions = self._route_ids(reader, payload)
        input_result = reader.interpret_text(text, require_quantities=True, region_ids=regions)
        if not input_result.get("safeRequestReady"):
            response = {
                "apiContractVersion": "valuepilot-argentina-backend-http-v1",
                "requestId": request_id,
                "releaseId": handle.release_id,
                "releaseDate": handle.release_date,
                "freshness": handle.freshness_status,
                "regionsQueried": list(regions),
                "input": input_result,
                "result": None,
                "decisionSkipped": "CLARIFICATION_REQUIRED",
                "evidenceSemantics": {"availability": "UNKNOWN", "distance": "STRAIGHT_LINE_HAVERSINE_ONLY"},
                "diagnostics": {"serviceMs": round((time.perf_counter() - started) * 1000, 3), "customerResponseBytes": 0},
            }
            response["diagnostics"]["customerResponseBytes"] = self._response_size(response)
            return response
        request = {"latitude": payload.get("latitude"), "longitude": payload.get("longitude"), "radiusKm": payload.get("radiusKm"), "items": input_result["structuredItems"]}
        trusted_keys = self._trusted_keys_from_input(input_result)
        if trusted_keys:
            decision, metrics = reader.query(request, trusted_product_keys=trusted_keys)
        else:
            decision, metrics = reader.query(request)
        return self._shop_response(request_id, handle, decision, metrics, started=started, input_result=input_result)

    def search(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        query = payload.get("query")
        if not isinstance(query, str) or not query.strip() or len(query.strip()) > 96:
            raise BackendQueryError("query must contain 1-96 characters")
        request_id = correlation_id or str(uuid.uuid4())
        started = time.perf_counter()
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        regions = self._route_ids(reader, payload)
        candidates, metrics = reader.discover(query, region_ids=regions, product_limit=5)
        response = {
            "apiContractVersion": "valuepilot-argentina-backend-http-v1",
            "requestId": request_id,
            "releaseId": handle.release_id,
            "releaseDate": handle.release_date,
            "freshness": handle.freshness_status,
            "regionsQueried": metrics.regions_queried,
            "query": query,
            "originalQuery": query,
            # This is a normalized display echo, not a resolved product name.
            "interpretedQuery": query.strip(),
            "resolution": "DISCOVERY",
            "searchMode": "DISCOVERY",
            # Kept for callers that consumed the earlier search envelope;
            # discovery deliberately has no correction or interpreter object.
            "correction": None,
            "input": None,
            "matches": [dict(value) for value in candidates],
            "evidenceSemantics": {
                "availability": "UNKNOWN",
                "distance": "STRAIGHT_LINE_HAVERSINE_ONLY",
                "pricePublicationIsNotInventory": True,
            },
            "diagnostics": {
                **metrics.as_dict(),
                "serviceMs": round((time.perf_counter() - started) * 1000, 3),
                "customerResponseBytes": 0,
            },
        }
        response["diagnostics"]["customerResponseBytes"] = self._response_size(response)
        return response


__all__ = ["BackendService", "HARD_RESPONSE_BYTES", "MAX_REQUEST_BYTES", "NORMAL_RESPONSE_BYTES", "ResponseLimitError"]

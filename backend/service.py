"""Bounded public service contract around the backend reader."""

from __future__ import annotations

import json
import logging
import time
import uuid
import threading
from copy import deepcopy
from decimal import Decimal
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

    def _execute(self, payload: Mapping[str, Any], *, correlation_id: str | None = None):
        request_id = correlation_id or str(uuid.uuid4())
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        decision, metrics = reader.query(payload)
        return request_id, handle, decision, metrics

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
        safe_decision = _strip_internal(decision)
        response = {
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
            "result": safe_decision,
            "diagnostics": {
                "serviceMs": round((time.perf_counter() - started) * 1000, 3),
                "customerResponseBytes": 0,
            },
        }
        raw = json.dumps(response, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        if len(raw) > HARD_RESPONSE_BYTES:
            raise ResponseLimitError(self.code)
        response["diagnostics"]["customerResponseBytes"] = len(raw)
        return response

    def search(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        query = payload.get("query")
        if not isinstance(query, str) or not query.strip() or len(query.strip()) > 96:
            raise BackendQueryError("query must contain 1-96 characters")
        request = {
            "latitude": payload.get("latitude"),
            "longitude": payload.get("longitude"),
            "radiusKm": payload.get("radiusKm"),
            "items": [{"lineId": "search-1", "query": query, "amount": "1", "unit": "count"}],
        }
        request_id, handle, raw_decision, metrics = self._execute(request, correlation_id=correlation_id)
        candidates = []
        for item in raw_decision.get("providerItems", []):
            if not isinstance(item, dict):
                continue
            candidates.append({
                "lineId": item.get("lineId"),
                "query": item.get("query"),
                "productCandidates": _strip_internal(item.get("productCandidates", [])),
            })
        return {
            "apiContractVersion": "valuepilot-argentina-backend-http-v1",
            "requestId": request_id,
            "releaseId": handle.release_id,
            "releaseDate": handle.release_date,
            "freshness": handle.freshness_status,
            "regionsQueried": metrics.regions_queried,
            "query": query,
            "matches": candidates,
            "evidenceSemantics": {"availability": "UNKNOWN", "distance": "STRAIGHT_LINE_HAVERSINE_ONLY"},
            "diagnostics": {"serviceMs": round(metrics.total_ms, 3)},
        }


__all__ = ["BackendService", "HARD_RESPONSE_BYTES", "MAX_REQUEST_BYTES", "NORMAL_RESPONSE_BYTES", "ResponseLimitError"]

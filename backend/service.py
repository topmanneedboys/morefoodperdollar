"""Bounded public service contract around the backend reader."""

from __future__ import annotations

import json
import logging
import os
import time
import uuid
import threading
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Mapping

from .reader import (
    MAX_DISCOVERY_PRODUCT_LIMIT,
    ArgentinaBackendReader,
    BackendQueryError,
    CurrentPriceEvidenceUnavailable,
)
from .release import ReleaseError, ReleaseManager, qualified_offer_artifacts_available
from tools.product_identity_lexicon import build_family_clarification, family_query_form, family_query_key, filter_family_candidates
from tools.product_taxonomy import ProductTaxonomyIndex, build_taxonomy_clarification, filter_taxonomy_candidates

LOGGER = logging.getLogger("valuepilot.backend")
MAX_REQUEST_BYTES = 16 * 1024
NORMAL_RESPONSE_BYTES = 128 * 1024
HARD_RESPONSE_BYTES = 512 * 1024

ANDROID_NETWORKING_AUTHORIZED_ENV = "VALUEPILOT_ANDROID_NETWORKING_AUTHORIZED"
ANDROID_NETWORKING_SCOPE_ENV = "VALUEPILOT_ANDROID_NETWORKING_SCOPE"
ANDROID_NETWORKING_RELEASE_ID_ENV = "VALUEPILOT_ANDROID_NETWORKING_RELEASE_ID"
ANDROID_NETWORKING_RELEASE_DATE_ENV = "VALUEPILOT_ANDROID_NETWORKING_RELEASE_DATE"
ANDROID_NETWORKING_SCOPE = "identity-search"
PRODUCTION_ANDROID_UI_AUTHORIZED_ENV = "VALUEPILOT_PRODUCTION_ANDROID_UI_AUTHORIZED"
PRODUCTION_ANDROID_UI_SCOPE_ENV = "VALUEPILOT_PRODUCTION_ANDROID_UI_SCOPE"
PRODUCTION_ANDROID_UI_SCOPE = "qualified-offers"
DISCOVERY_PAGE_SIZE = 5
DISCOVERY_MAX_PAGE = 5
PRODUCT_TAXONOMY_PATH_ENV = "VALUEPILOT_PRODUCT_TAXONOMY_PATH"
PRODUCT_TAXONOMY_SHA256_ENV = "VALUEPILOT_PRODUCT_TAXONOMY_SHA256"


def _android_networking_authorized(status: Mapping[str, Any]) -> bool:
    """Authorize only the explicitly deployed identity-search environment."""
    if os.environ.get(ANDROID_NETWORKING_AUTHORIZED_ENV, "").strip().lower() != "true":
        return False
    if os.environ.get(ANDROID_NETWORKING_SCOPE_ENV, "") != ANDROID_NETWORKING_SCOPE:
        return False
    if status.get("service") != "ready" or status.get("freshness") != "FRESH":
        return False
    if status.get("backendCodeQualified") is not True:
        return False
    configured_release_id = os.environ.get(ANDROID_NETWORKING_RELEASE_ID_ENV, "").strip()
    configured_release_date = os.environ.get(ANDROID_NETWORKING_RELEASE_DATE_ENV, "").strip()
    return bool(
        configured_release_id
        and configured_release_date
        and configured_release_id == status.get("releaseId")
        and configured_release_date == status.get("releaseDate")
    )


def _production_android_ui_authorized(status: Mapping[str, Any]) -> bool:
    """Authorize qualified offer exposure only for an explicit release gate.

    Identity discovery has a narrower ``identity-search`` authorization.  A
    separate operator-controlled gate is required before Search may expose
    current price/store evidence to the Android UI.  The gate is deliberately
    release-scoped through the same release id/date variables used by the
    identity authorization, and defaults to false.
    """

    if os.environ.get(PRODUCTION_ANDROID_UI_AUTHORIZED_ENV, "").strip().lower() != "true":
        return False
    if os.environ.get(PRODUCTION_ANDROID_UI_SCOPE_ENV, "") != PRODUCTION_ANDROID_UI_SCOPE:
        return False
    if status.get("qualifiedOfferEvidenceAvailable") is not True:
        return False
    return _android_networking_authorized(status)


def _strip_internal(value: Any) -> Any:
    if isinstance(value, list):
        return [_strip_internal(item) for item in value]
    if not isinstance(value, dict):
        return value
    result = {}
    for key, item in value.items():
        if key in {"queryPlan", "providerItems", "sourceRow", "packageProvenance", "_taxonomy"}:
            continue
        if key == "provenance" and isinstance(item, dict):
            result[key] = {name: item.get(name) for name in ("provider", "releaseDate", "freshnessStatus") if item.get(name) is not None}
        else:
            result[key] = _strip_internal(item)
    return result


class ResponseLimitError(BackendQueryError):
    code = "RESPONSE_TOO_LARGE"


class BackendService:
    def __init__(
        self,
        root: str,
        *,
        release_manager: ReleaseManager | None = None,
        taxonomy_index_path: str | Path | None = None,
        taxonomy_index_sha256: str | None = None,
    ):
        self.release_manager = release_manager or ReleaseManager(root)
        self._reader_lock = threading.Lock()
        self._readers: dict[tuple[str, str], ArgentinaBackendReader] = {}
        self._taxonomy_lock = threading.Lock()
        self._taxonomy_index: ProductTaxonomyIndex | None = None
        self._taxonomy_index_attempted = False
        configured_path = taxonomy_index_path
        if configured_path is None:
            configured_path = os.environ.get(PRODUCT_TAXONOMY_PATH_ENV, "").strip() or None
        self._taxonomy_index_path = Path(configured_path).resolve() if configured_path else None
        self._taxonomy_index_sha256 = (
            taxonomy_index_sha256
            if taxonomy_index_sha256 is not None
            else os.environ.get(PRODUCT_TAXONOMY_SHA256_ENV, "").strip() or None
        )

    def _reader(self, handle):
        key = (handle.release_id, str(handle.root))
        with self._reader_lock:
            reader = self._readers.get(key)
            if reader is None:
                reader = ArgentinaBackendReader(handle)
                self._readers[key] = reader
            return reader

    def _taxonomy(self, handle) -> ProductTaxonomyIndex | None:
        """Load the optional identity taxonomy once for the pinned release.

        The sidecar is deliberately opt-in.  A deployment must point at the
        exact generated artifact (and may provide its SHA-256); an absent or
        invalid sidecar fails open to the existing reviewed lexical path and
        never blocks or changes price/offer authority.
        """

        with self._taxonomy_lock:
            if self._taxonomy_index_attempted:
                return self._taxonomy_index
            self._taxonomy_index_attempted = True
            path = self._taxonomy_index_path
            if path is None:
                root = getattr(handle, "root", None)
                if root is not None:
                    candidate = Path(root) / "product-taxonomy.jsonl.gz"
                    if candidate.is_file():
                        path = candidate.resolve()
            if path is None or not path.is_file():
                return None
            try:
                self._taxonomy_index = ProductTaxonomyIndex.load(
                    path,
                    expected_sha256=self._taxonomy_index_sha256,
                )
            except (OSError, ValueError) as exc:
                LOGGER.warning("taxonomy_sidecar_unavailable path=%s reason=%s", path, exc)
                self._taxonomy_index = None
            return self._taxonomy_index

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

    @staticmethod
    def _trusted_keys_from_shop_payload(payload: Mapping[str, Any]) -> dict[str, str] | None:
        """Carry only explicit Search-selected identities into direct /shop."""

        items = payload.get("items")
        if not isinstance(items, list):
            return None
        trusted: dict[str, str] = {}
        for index, item in enumerate(items):
            if not isinstance(item, Mapping):
                return None
            supplied_line_id = item.get("lineId")
            line_id = supplied_line_id if supplied_line_id is not None else f"item-{index + 1}"
            product_key = item.get("productEvidenceKey")
            if product_key is None:
                continue
            if not isinstance(line_id, str) or not line_id or not isinstance(product_key, str) or not product_key:
                raise BackendQueryError("trusted product identity is invalid")
            trusted[line_id] = product_key
        return trusted or None

    def _execute(self, payload: Mapping[str, Any], *, correlation_id: str | None = None, trusted_product_keys: Mapping[str, str] | None = None):
        request_id = correlation_id or str(uuid.uuid4())
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        decision, metrics = reader.query(payload, trusted_product_keys=trusted_product_keys)
        return request_id, handle, decision, metrics

    @staticmethod
    def _route_selections(reader: ArgentinaBackendReader, payload: Mapping[str, Any]):
        try:
            latitude = Decimal(str(payload.get("latitude")))
            longitude = Decimal(str(payload.get("longitude")))
            radius = Decimal(str(payload.get("radiusKm")))
        except (InvalidOperation, TypeError, ValueError) as exc:
            raise BackendQueryError("latitude, longitude and radiusKm must be numeric") from exc
        try:
            return tuple(reader.router.route(latitude=latitude, longitude=longitude, radius_km=radius))
        except (BackendQueryError, ValueError) as exc:
            raise BackendQueryError(str(exc)) from exc

    @classmethod
    def _route_ids(cls, reader: ArgentinaBackendReader, payload: Mapping[str, Any]) -> tuple[str, ...]:
        return tuple(selection.region_id for selection in cls._route_selections(reader, payload))

    @staticmethod
    def _response_size(response: dict[str, Any]) -> int:
        raw = json.dumps(response, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        if len(raw) > HARD_RESPONSE_BYTES:
            raise ResponseLimitError(ResponseLimitError.code)
        return len(raw)

    @staticmethod
    def _emit_request_metrics(request_id: str, handle: Any, metrics: Any, *, started: float) -> None:
        """Emit bounded server-side diagnostics without changing the API shape."""

        reader_metrics = metrics.as_dict() if callable(getattr(metrics, "as_dict", None)) else {}
        artifacts = getattr(handle, "artifacts", None)
        cache_stats = artifacts.cache_stats() if callable(getattr(artifacts, "cache_stats", None)) else {}
        store = getattr(artifacts, "store", None)
        store_stats = store.stats_snapshot() if callable(getattr(store, "stats_snapshot", None)) else {}
        payload = {
            "requestId": request_id,
            "releaseId": getattr(handle, "release_id", None),
            "serviceMs": round((time.perf_counter() - started) * 1000, 3),
            "reader": reader_metrics,
            "artifactCache": cache_stats,
            "objectStore": store_stats,
        }
        LOGGER.info("request_metrics=%s", json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")))

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
        self._emit_request_metrics(request_id, handle, metrics, started=started)
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
            "automatedOfficialAcquisitionProven": False,
        })
        value["androidNetworkingAuthorized"] = _android_networking_authorized(value)
        value["productionAndroidUiAuthorized"] = _production_android_ui_authorized(value)
        return value

    def shop(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        request_id = correlation_id or str(uuid.uuid4())
        started = time.perf_counter()
        handle = None
        try:
            request_id, handle, decision, metrics = self._execute(
                payload,
                correlation_id=request_id,
                trusted_product_keys=self._trusted_keys_from_shop_payload(payload),
            )
        except CurrentPriceEvidenceUnavailable:
            LOGGER.info("request=%s release=%s result=current_evidence_unavailable", request_id, getattr(handle, "release_id", None))
            raise
        except BackendQueryError:
            LOGGER.info("request=%s release=%s result=invalid_or_failed", request_id, getattr(handle, "release_id", None))
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
        page = payload.get("page", 0)
        if isinstance(page, bool) or not isinstance(page, int) or not 0 <= page <= DISCOVERY_MAX_PAGE:
            raise BackendQueryError("search page is invalid")
        request_id = correlation_id or str(uuid.uuid4())
        started = time.perf_counter()
        selected_family = family_query_key(query)
        selected_form = family_query_form(query)
        discovery_query = query
        if selected_family == "egg" and selected_form is not None:
            # Form labels are identity refinements, not reliable lexical
            # catalog phrases.  Search the canonical egg family first, then
            # apply the observed form to names.  Derived choices keep their
            # marker because the SearchPack exclusion policy deliberately
            # hides those rows from a bare family query.
            if selected_form[1] not in {"toy", "chocolate", "pasta", "drink"}:
                discovery_query = "huevo"
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        authorization_status = {
            "service": "ready",
            "releaseId": handle.release_id,
            "releaseDate": handle.release_date,
            "freshness": handle.freshness_status,
            "backendCodeQualified": True,
            "qualifiedOfferEvidenceAvailable": qualified_offer_artifacts_available(
                getattr(getattr(handle, "artifacts", None), "manifest", {}) or {}
            ),
        }
        production_android_ui_authorized = _production_android_ui_authorized(authorization_status)
        selections = self._route_selections(reader, payload)
        regions = tuple(selection.region_id for selection in selections)
        offset = page * DISCOVERY_PAGE_SIZE
        window_limit = min(offset + DISCOVERY_PAGE_SIZE + 1, MAX_DISCOVERY_PRODUCT_LIMIT)
        # Egg names are also used by toy and confectionery products.  Search a
        # larger bounded window before applying the direct-egg identity filter
        # so genuine food rows are not displaced by those higher-ranked noise
        # rows.  The cap remains the existing 31-product discovery limit.
        if selected_family == "egg":
            window_limit = MAX_DISCOVERY_PRODUCT_LIMIT
        discover_with_offers = getattr(reader, "discover_with_offers", None)
        if callable(discover_with_offers):
            candidates, metrics = discover_with_offers(
                discovery_query,
                selections=selections,
                latitude=Decimal(str(payload.get("latitude"))),
                longitude=Decimal(str(payload.get("longitude"))),
                radius_km=Decimal(str(payload.get("radiusKm"))),
                product_limit=window_limit,
            )
        else:
            # Compatibility for older test doubles and pre-wiring readers.
            candidates, metrics = reader.discover(discovery_query, region_ids=regions, product_limit=window_limit)
        if not production_android_ui_authorized:
            # Identity discovery remains available under the narrower
            # identity-search authorization, but current price/store evidence
            # must not cross into the Android UI before the explicit production
            # gate is enabled for this exact release.
            candidates = tuple(
                {
                    **dict(candidate),
                    "qualifiedOffers": [],
                }
                if isinstance(candidate, Mapping) and "qualifiedOffers" in candidate
                else candidate
                for candidate in candidates
            )
        taxonomy = self._taxonomy(handle)
        if taxonomy is not None:
            # Keep the index inside the identity boundary.  The internal
            # annotation is consumed only by the family/form refinements and
            # removed by _strip_internal before the response leaves Search.
            candidates = taxonomy.annotate(candidates)
        # Keep the complete bounded evidence window for clarification.  The
        # visible result set may be refined below (for example a plain egg
        # query removes toy eggs), but the shopper still needs the observed
        # alternatives that made the clarification useful.
        clarification_candidates = candidates
        # A clarification choice is an identity-only refinement.  Apply its
        # observed family form to the bounded result window so a query such as
        # ``carrot juice`` cannot retain a classified carrot lotion or cake.
        # The helper fails open when new source rows are not yet classified.
        candidates = filter_family_candidates(query, candidates)
        # Generic reviewed families use the same bounded identity evidence to
        # apply explicit form choices such as ``eggs liquid``.  This remains a
        # fail-open presentation refinement; it never changes price, offer, or
        # availability authority.
        candidates = filter_taxonomy_candidates(query, candidates)
        if selected_family == "egg" and selected_form == ("egg", "whole_egg"):
            # The canonical family discovery window can contain valid shell-egg
            # identities whose current offer partition has no matching price
            # evidence.  Keep those identities available, but put products
            # with real current offers first so the first page is immediately
            # useful for a shopper.  Python's stable sort preserves the
            # existing SearchPack order within each evidence group.
            candidates = tuple(
                sorted(
                    candidates,
                    key=lambda candidate: 0 if candidate.get("qualifiedOffers") else 1,
                )
            )
        has_more = len(candidates) > offset + DISCOVERY_PAGE_SIZE
        limit_reached = has_more and page == DISCOVERY_MAX_PAGE
        visible_candidates = candidates[offset:offset + DISCOVERY_PAGE_SIZE]
        # This is an optional identity-only hint.  It is derived from the
        # bounded discovery window and never changes ranking, prices, offers,
        # availability, or the selected product identity.
        clarification = None
        if page == 0 and family_query_form(query) is None:
            clarification = build_family_clarification(query, clarification_candidates)
            if clarification is None:
                clarification = build_taxonomy_clarification(query, clarification_candidates)
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
            "clarification": clarification,
            "matches": [_strip_internal(value) for value in visible_candidates],
            "pagination": {
                "page": page,
                "pageSize": DISCOVERY_PAGE_SIZE,
                "hasMore": has_more,
                "nextPage": (page + 1) if has_more and not limit_reached else None,
                "limitReached": limit_reached,
            },
            "evidenceSemantics": {
                "availability": "UNKNOWN",
                "distance": "STRAIGHT_LINE_HAVERSINE_ONLY",
                "qualifiedCurrentPriceEvidence": production_android_ui_authorized,
                "pricePublicationIsNotInventory": True,
            },
            "diagnostics": {
                **metrics.as_dict(),
                "serviceMs": round((time.perf_counter() - started) * 1000, 3),
                "customerResponseBytes": 0,
            },
        }
        response["diagnostics"]["customerResponseBytes"] = self._response_size(response)
        self._emit_request_metrics(request_id, handle, metrics, started=started)
        return response


__all__ = ["BackendService", "HARD_RESPONSE_BYTES", "MAX_REQUEST_BYTES", "NORMAL_RESPONSE_BYTES", "ResponseLimitError"]

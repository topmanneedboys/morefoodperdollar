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
from .english_queries import ENGLISH_QUERY_PHRASES, ENGLISH_QUERY_WORDS

# Common English grocery words that the family labels do not spell the way
# shoppers type them (plural, colloquial, or a different register).
_ENGLISH_SHOPPER_SYNONYMS = {
    "soda": "gaseosa", "candy": "caramelos", "candies": "caramelos",
    "cookies": "galletitas", "cookie": "galletitas", "crackers": "galletitas",
    "chips": "papas", "crisps": "papas", "diapers": "panales", "nappy": "panal",
    "toothpaste": "pasta dental", "toilet paper": "papel higienico",
    "paper towels": "papel de cocina", "napkins": "servilletas",
    "dish soap": "detergente", "laundry detergent": "detergente",
    "bleach": "lavandina", "softener": "suavizante", "shampoo": "shampoo",
    "conditioner": "acondicionador", "deodorant": "desodorante",
    "razor": "afeitar", "diaper": "panal", "wipes": "toallitas",
    "ice cream": "helado", "yogurt": "yogur", "yoghurt": "yogur",
    "butter": "manteca", "cheese": "queso", "ham": "jamon",
    "sausage": "salchicha", "bacon": "panceta", "chicken": "pollo",
    "beef": "carne", "pork": "cerdo", "fish": "pescado", "tuna": "atun",
    "shrimp": "camaron", "flour": "harina", "sugar": "azucar", "salt": "sal",
    "oil": "aceite", "vinegar": "vinagre", "rice": "arroz", "noodles": "fideos",
    "pasta": "fideos", "bread": "pan", "toast": "tostadas", "cereal": "cereal",
    "oats": "avena", "honey": "miel", "jam": "mermelada", "coffee": "cafe",
    "tea": "te", "juice": "jugo", "water": "agua", "beer": "cerveza",
    "wine": "vino", "milk": "leche", "eggs": "huevo", "egg": "huevo",
    "onion": "cebolla", "potato": "papa", "tomato": "tomate",
    "lettuce": "lechuga", "carrot": "zanahoria", "apple": "manzana",
    "banana": "banana", "orange": "naranja", "lemon": "limon",
    "grapes": "uva", "strawberry": "frutilla", "peach": "durazno",
    "pear": "pera", "watermelon": "sandia", "melon": "melon",
    "avocado": "palta", "broccoli": "brocoli", "spinach": "espinaca",
    "pepper": "pimienta", "oregano": "oregano", "cumin": "comino",
    "cinnamon": "canela", "nutmeg": "nuez moscada", "garlic": "ajo",
    "parsley": "perejil", "bay leaf": "laurel", "soup": "sopa",
    "stock": "caldo", "sauce": "salsa", "ketchup": "ketchup",
    "mustard": "mostaza", "mayonnaise": "mayonesa", "olives": "aceitunas",
    "pickles": "encurtidos", "lentils": "lentejas", "beans": "porotos",
    "chickpeas": "garbanzos", "peas": "arvejas", "corn": "choclo",
    "pizza": "pizza", "empanada": "empanada", "sausages": "salchichas",
    "chocolate": "chocolate", "biscuits": "galletitas", "crackers ": "galletitas",
    "peanut butter": "manteca de mani", "nuts": "nueces", "almonds": "almendras",
    "raisins": "pasas", "granola": "granola", "quinoa": "quinoa",
    "tofu": "tofu", "hummus": "hummus", "soap": "jabon",
    "toothbrush": "cepillo", "mouthwash": "enjuague", "floss": "hilo dental",
    "bandages": "curitas", "alcohol": "alcohol", "cotton": "algodon",
    "batteries": "pilas", "light bulb": "lampara", "trash bags": "bolsas",
    "garbage bags": "bolsas", "foil": "aluminio", "plastic wrap": "film",
    "sponge": "esponja", "broom": "escoba", "mop": "mopa", "bucket": "balde",
    "gloves": "guantes", "mask": "barbijo", "candles": "velas",
    "incense": "sahumerio", "air freshener": "aromatizante",
    "insecticide": "insecticida", "matches": "fosforos", "lighter": "encendedor",
}
ENGLISH_QUERY_WORDS = {**_ENGLISH_SHOPPER_SYNONYMS, **ENGLISH_QUERY_WORDS}

# US / UK / AU words that name the same product must reach the same family.
_REGIONAL_ENGLISH = {
    "cookies": "galletitas", "cookie": "galletitas", "biscuits": "galletitas", "biscuit": "galletitas",
    "chips": "papas fritas", "crisps": "papas fritas", "french fries": "papas fritas", "fries": "papas fritas",
    "soda": "gaseosa", "pop": "gaseosa", "soft drink": "gaseosa", "fizzy drink": "gaseosa",
    "candy": "caramelos", "sweets": "caramelos", "lollies": "caramelos",
    "diapers": "panales", "nappies": "panales", "nappy": "panal",
    "eggplant": "berenjena", "aubergine": "berenjena",
    "zucchini": "zucchini", "courgette": "zucchini",
    "cilantro": "cilantro", "coriander": "cilantro",
    "shrimp": "camaron", "prawns": "camaron",
    "ground beef": "carne picada", "mince": "carne picada", "minced meat": "carne picada",
    "whole milk": "leche entera", "full cream milk": "leche entera",
    "skim milk": "leche descremada", "skimmed milk": "leche descremada",
    "heavy cream": "crema de leche", "double cream": "crema de leche",
    "yogurt": "yogur", "yoghurt": "yogur", "yoghourt": "yogur",
    "stove": "cocina", "cooker": "cocina", "gas cooker": "cocina",
    "trash bags": "bolsas", "rubbish bags": "bolsas", "garbage bags": "bolsas",
    "paper towels": "papel de cocina", "kitchen roll": "papel de cocina",
    "flashlight": "linterna", "torch": "linterna",
    "vacuum cleaner": "aspiradora", "hoover": "aspiradora",
    "trunk": "baul", "boot": "baul",
}
ENGLISH_QUERY_WORDS = {**ENGLISH_QUERY_WORDS, **_REGIONAL_ENGLISH}
# Regional terms outrank generated phrase mappings ("cookies" -> pepas).
ENGLISH_QUERY_PHRASES = {**ENGLISH_QUERY_PHRASES, **_REGIONAL_ENGLISH}


def _translate_query_words(query: str) -> str:
    """Map English catalogue words and phrases to the Spanish index terms."""
    words = query.split()
    if not words:
        return query
    phrase = " ".join(word.casefold() for word in words)
    if phrase in ENGLISH_QUERY_PHRASES:
        return ENGLISH_QUERY_PHRASES[phrase]

    def translate(word: str) -> str:
        lowered = word.casefold()
        direct = ENGLISH_QUERY_WORDS.get(lowered)
        if direct is not None:
            return direct
        # English plurals must reach the same family: "peanut" -> "peanuts".
        if lowered.endswith("es") and lowered[:-2] in ENGLISH_QUERY_WORDS:
            return ENGLISH_QUERY_WORDS[lowered[:-2]]
        if lowered.endswith("s") and lowered[:-1] in ENGLISH_QUERY_WORDS:
            return ENGLISH_QUERY_WORDS[lowered[:-1]]
        if lowered + "s" in ENGLISH_QUERY_WORDS:
            return ENGLISH_QUERY_WORDS[lowered + "s"]
        return word

    return " ".join(translate(word) for word in words)

from tools.catalog_discovery import DiscoveryError, DiscoveryIndex
from tools.product_identity_lexicon import (
    build_family_clarification,
    family_query_form,
    family_query_key,
    family_selection_form,
    filter_family_candidates,
)
from tools.product_taxonomy import (
    ProductTaxonomyIndex,
    build_taxonomy_clarification,
    filter_taxonomy_candidates,
    taxonomy_selection_form,
)

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
DISCOVERY_PAGE_SIZE = 20
DISCOVERY_MAX_PAGE = 24
# Ranked windows are requested in fixed buckets so consecutive pages share one
# computation in the reader cache.
DISCOVERY_WINDOW_BUCKET = 100
PRODUCT_TAXONOMY_PATH_ENV = "VALUEPILOT_PRODUCT_TAXONOMY_PATH"
PRODUCT_TAXONOMY_SHA256_ENV = "VALUEPILOT_PRODUCT_TAXONOMY_SHA256"
DISCOVERY_INDEX_PATH_ENV = "VALUEPILOT_DISCOVERY_INDEX_PATH"
DISCOVERY_INDEX_SHA256_ENV = "VALUEPILOT_DISCOVERY_INDEX_SHA256"
DISCOVERY_RELEASE_ID_ENV = "VALUEPILOT_DISCOVERY_RELEASE_ID"
DISCOVERY_V2_ENABLED_ENV = "VALUEPILOT_DISCOVERY_V2_ENABLED"
_FAMILY_DISCOVERY_QUERIES = {
    "rice": "arroz",
    "lychee": "lichi",
    "yerba_mate": "yerba mate",
    "egg": "huevo",
    "carrot": "carrot",
    "strawberry": "frutilla",
    "mango": "mango",
    "alfajor": "alfajor",
    "flour": "harina",
    "peanut": "mani",
    "pork": "cerdo",
    "lamb": "cordero",
    "turkey": "pavo",
    "beef": "vacuno",
    "facial_cream": "crema facial",
    "wafer": "oblea",
    "chewing_gum": "chicle",
    "raticide": "raticida",
    "bowl": "bowl",
    "coffee_maker": "cafetera",
    "air_fryer": "airfryer",
    "toaster": "tostadora",
    "sandwich_maker": "sandwichera",
    "juicer": "exprimidor",
    "blender": "licuadora",
    "microwave": "microondas",
    "heater": "calefactor",
    "water_heater": "termotanque",
    "fan": "ventilador",
    "vacuum_cleaner": "aspiradora",
    "broom": "escoba",
    "mop": "mopa",
    "duster": "plumero",
    "cleaning_cloth": "pano",
}


def _candidate_evidence_sort_key(candidate: Mapping[str, Any]) -> tuple[int, int]:
    """Put usable current unit-price evidence ahead of incomplete rows."""

    offers = candidate.get("qualifiedOffers")
    if not isinstance(offers, (list, tuple)):
        return (1, 1)
    has_unit_price = any(
        isinstance(offer, Mapping) and isinstance(offer.get("unitPrice"), Mapping)
        for offer in offers
    )
    return (0 if has_unit_price else 1, 0 if offers else 1)


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
        discovery_index_path: str | Path | None = None,
        discovery_index_sha256: str | None = None,
        discovery_release_id: str | None = None,
    ):
        # Repeated searches for the same query and area are served from memory
        # instead of re-ranking; the startup warm-up fills this cache.
        self._response_cache: dict[tuple, dict[str, Any]] = {}
        self._response_lock = threading.Lock()
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
        self._discovery_lock = threading.Lock()
        self._discovery_index: DiscoveryIndex | None = None
        self._discovery_index_attempted = False
        configured_discovery_path = discovery_index_path
        if configured_discovery_path is None:
            configured_discovery_path = os.environ.get(DISCOVERY_INDEX_PATH_ENV, "").strip() or None
        self._discovery_index_path = Path(configured_discovery_path).resolve() if configured_discovery_path else None
        self._discovery_index_sha256 = (
            discovery_index_sha256
            if discovery_index_sha256 is not None
            else os.environ.get(DISCOVERY_INDEX_SHA256_ENV, "").strip() or None
        )
        self._discovery_release_id = (
            discovery_release_id
            if discovery_release_id is not None
            else os.environ.get(DISCOVERY_RELEASE_ID_ENV, "").strip() or None
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

    def _discovery(self) -> DiscoveryIndex | None:
        """Load the additive full-catalog sidecar only when explicitly enabled."""

        if os.environ.get(DISCOVERY_V2_ENABLED_ENV, "").strip().lower() != "true":
            return None
        with self._discovery_lock:
            if self._discovery_index_attempted:
                return self._discovery_index
            self._discovery_index_attempted = True
            path = self._discovery_index_path
            if path is None or not path.is_file():
                return None
            try:
                self._discovery_index = DiscoveryIndex(
                    path,
                    expected_source_sha256=self._discovery_index_sha256,
                    expected_release_id=self._discovery_release_id,
                )
            except (OSError, DiscoveryError) as exc:
                LOGGER.warning("discovery_sidecar_unavailable path=%s reason=%s", path, exc)
                self._discovery_index = None
            return self._discovery_index

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
        index = self._discovery()
        value["discoveryIdentitySearchAvailable"] = bool(
            value["androidNetworkingAuthorized"] and index is not None
            and index.metadata["release_id"] == value.get("releaseId")
        )
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

    def discovery_search(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        """Evaluate the full identity sidecar through the additive v2 contract.

        Identity-only requests preserve the frozen sidecar contract. Explicit
        location requests join the full strict identity set through the existing
        qualified offer reader before pagination, with matching release and UI
        authorization gates. Stock remains unknown. Nothing changes v1 Search.
        """

        if os.environ.get(DISCOVERY_V2_ENABLED_ENV, "").strip().lower() != "true":
            raise BackendQueryError("DISCOVERY_V2_UNAVAILABLE")
        index = self._discovery()
        if index is None:
            raise BackendQueryError("DISCOVERY_V2_UNAVAILABLE")
        scoped = any(key in payload for key in ("latitude", "longitude", "radiusKm", "sort", "storeKey", "unitBasis"))
        try:
            if scoped:
                from .discovery_offers import scoped_search
                if not all(key in payload for key in ("latitude", "longitude", "radiusKm")):
                    raise BackendQueryError("scoped discovery requires a complete location")
                handle = self.release_manager.pin(require_fresh=True)
                status = {"service": "ready", "releaseId": handle.release_id, "releaseDate": handle.release_date,
                          "freshness": handle.freshness_status, "backendCodeQualified": True,
                          "qualifiedOfferEvidenceAvailable": qualified_offer_artifacts_available(handle.artifacts.manifest)}
                if not _production_android_ui_authorized(status):
                    raise BackendQueryError("DISCOVERY_PRICE_AUTHORIZATION_REQUIRED")
                reader = self._reader(handle)
                selections = self._route_selections(reader, payload)
                result = scoped_search(index, reader, handle, payload, selections)
            else:
                result = index.query(payload).to_dict()
        except DiscoveryError as exc:
            raise BackendQueryError(str(exc)) from exc
        request_id = correlation_id or str(uuid.uuid4())
        response = {
            "apiContractVersion": "valuepilot-discovery-http-v2",
            "requestId": request_id,
            "releaseId": result["releaseId"],
            "indexVersion": result["indexVersion"],
            "result": result,
            "evidenceSemantics": {
                "identity": "SOURCE_IDENTITY_AND_ACCEPTED_ASSERTIONS",
                "prices": "QUALIFIED_CURRENT_RELEASE" if scoped else "NOT_INCLUDED",
                "offers": "EXACT_IDENTITY_SCOPED_PREVIEW" if scoped else "NOT_INCLUDED",
                "stock": "NOT_INCLUDED",
            },
        }
        self._response_size(response)
        return response

    def search(self, payload: Mapping[str, Any], *, correlation_id: str | None = None) -> dict[str, Any]:
        query = payload.get("query")
        if not isinstance(query, str) or not query.strip() or len(query.strip()) > 96:
            raise BackendQueryError("query must contain 1-96 characters")
        selection_id = payload.get("selectionId")
        if selection_id is not None:
            if (
                not isinstance(selection_id, str)
                or len(selection_id) > 160
                or not selection_id.strip()
                or (
                    family_selection_form(query, selection_id) is None
                    and taxonomy_selection_form(query, selection_id) is None
                )
            ):
                raise BackendQueryError("search selection is invalid for this query")
        page = payload.get("page", 0)
        if isinstance(page, bool) or not isinstance(page, int) or not 0 <= page <= DISCOVERY_MAX_PAGE:
            raise BackendQueryError("search page is invalid")
        request_id = correlation_id or str(uuid.uuid4())
        started = time.perf_counter()
        # Family phrases ("coffee maker", "chewing gum") are recognised before
        # any word-level translation; translating first would break them.
        selected_family = family_query_key(query)
        selected_form = family_query_form(query)
        effective_query = query
        if selected_family is None:
            effective_query = _translate_query_words(query)
            selected_family = family_query_key(effective_query)
            selected_form = family_query_form(effective_query)
        discovery_query = effective_query
        if selected_family in _FAMILY_DISCOVERY_QUERIES:
            # Bilingual family labels and presentation choices are identity
            # refinements, not reliable lexical catalog phrases.  Search the
            # canonical Argentina family term first, then apply the observed
            # form to names.  This keeps an English query such as ``face
            # cream`` or ``chewing gum`` on the same source-backed result
            # set as its Spanish equivalent.
            discovery_query = _FAMILY_DISCOVERY_QUERIES[selected_family]
        handle = self.release_manager.pin(require_fresh=True)
        reader = self._reader(handle)
        # Every input is parsed and the release is pinned at this point, so the
        # cache key is complete and a hit can return before any routing or
        # ranking work happens.
        cache_key = (
            handle.release_id,
            query.casefold().strip(),
            page,
            selection_id,
            str(payload.get("latitude")),
            str(payload.get("longitude")),
            str(payload.get("radiusKm")),
        )
        with self._response_lock:
            cached_response = self._response_cache.get(cache_key)
        if cached_response is not None:
            reply = dict(cached_response)
            reply["requestId"] = request_id
            return reply
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
        if selected_family == "egg" or selected_form is not None:
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
        # A query such as "MOTOSIERRA 16 37 2 CC" has no catalogue match, so
        # the ranker falls back to the numbers and returns unrelated products.
        # If none of the query's meaningful words appears in any candidate,
        # report no matches instead of noise.
        meaningful = [
            token for token in discovery_query.split()
            if len(token) >= 5 and sum(character.isalpha() for character in token) >= 3
        ]
        has_model_number = any(
            any(character.isdigit() for character in token) for token in discovery_query.split()
        )
        if meaningful and has_model_number and candidates:
            matched = [
                candidate for candidate in candidates
                if any(token.casefold() in (candidate.get("name") or "").casefold() for token in meaningful)
            ]
            candidates = matched

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
        candidates = filter_family_candidates(query, candidates, selection_id=selection_id)
        # Generic reviewed families use the same bounded identity evidence to
        # apply explicit form choices such as ``eggs liquid``.  This remains a
        # fail-open presentation refinement; it never changes price, offer, or
        # availability authority.
        candidates = filter_taxonomy_candidates(query, candidates, selection_id=selection_id)
        if selected_family in {"rice", "lychee", "yerba_mate", "egg", "carrot"}:
            # The canonical family discovery window can contain valid
            # identities whose current offer partition has no matching price
            # evidence.  Keep those identities available, but put products
            # with real current offers first so the first page is immediately
            # useful for a shopper.  Python's stable sort preserves the
            # existing SearchPack order within each evidence group.
            candidates = tuple(
                sorted(
                    candidates,
                    key=_candidate_evidence_sort_key,
                )
            )
        has_more = len(candidates) > offset + DISCOVERY_PAGE_SIZE
        limit_reached = has_more and page == DISCOVERY_MAX_PAGE
        visible_candidates = candidates[offset:offset + DISCOVERY_PAGE_SIZE]
        # This is an optional identity-only hint.  It is derived from the
        # bounded discovery window and never changes ranking, prices, offers,
        # availability, or the selected product identity.
        clarification = None
        if page == 0 and selection_id is None and family_query_form(effective_query) is None:
            clarification = build_family_clarification(effective_query, clarification_candidates)
            if clarification is None:
                clarification = build_taxonomy_clarification(effective_query, clarification_candidates)
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
        with self._response_lock:
            self._response_cache[cache_key] = dict(response)
            if len(self._response_cache) > 4096:
                self._response_cache.pop(next(iter(self._response_cache)))
        return response


__all__ = ["BackendService", "HARD_RESPONSE_BYTES", "MAX_REQUEST_BYTES", "NORMAL_RESPONSE_BYTES", "ResponseLimitError"]



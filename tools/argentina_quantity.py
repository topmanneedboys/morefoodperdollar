"""Size parsing for SEPA text, shared by the release pipeline.

SEPA stores a quantity and a unit in two columns, but they are often generic ("1", "un")
while the real size sits in the product name ("SACHET X 1LT", "PET X 1.5 LT"). This
module reads both, prefers a real measure over a generic count, and refuses to guess.

Canonical units: ``L`` (litres), ``kg`` (kilograms), ``count`` (pack pieces).
``kind`` is ``volume`` / ``mass`` / ``count`` / ``dimension`` / ``unknown``; only
volume, mass and count may carry a per-unit price.
"""

from __future__ import annotations

import re
import unicodedata
from typing import Any

MIN_LITRES = 0.001
MAX_LITRES = 200.0
MIN_KILOS = 0.001
MAX_KILOS = 500.0

_NUMBER = re.compile(r"(\d+(?:[.,]\d+)?)")
_VOLUME_L = re.compile(r"(?<![a-z])(?:litros|litro|litres|litre|lts|lt|l)(?![a-z])")
_VOLUME_ML = re.compile(r"(?<![a-z])(?:mililitros|mililitro|mls|ml|cm3|cm\u00b3|cc)(?![a-z])")
_MASS_KG = re.compile(r"(?<![a-z])(?:kilogramos|kilogramo|kilos|kilo|kgs|kgm|kg)(?![a-z])")
_MASS_G = re.compile(r"(?<![a-z])(?:gramos|gramo|grs|grm|gr|g)(?![a-z])")
_OUNCE = re.compile(r"(?<![a-z])(?:oz|onzas?)(?![a-z])")
_POUND = re.compile(r"(?<![a-z])(?:lb|lbs|libras?)(?![a-z])")
_COUNT = re.compile(
    r"(?<![a-z])(?:unidades|unidad|unid|uni|count|pack|paquete|atado|cubeta|bandeja|"
    r"docena|docenas|sobres|sobre|saquitos|saquito|capsulas|pastillas|pastilla|fetas|feta|"
    r"varillas|varilla|cubitos|cubito|cubiertos|huevos|huevo|comprimidos|comprimido|pods|"
    r"cargas|carga|compartimentos|compartimento|cajones|cajon|cavidades|cavidad|dedos|alas|"
    r"divisores|luces|hojas|hoja|paños|paño|sachet|botella|bolsa|unit|un|u)(?![a-z])"
)
_DIMENSION_UNIT = re.compile(
    r"(?<![a-z])(?:cm|mm|m|in|pulg|pulgada|pulgadas|hrs|h|w|v|watt|voltios|gb|mb|tb|hz|"
    r"kcal|meses|años|rpm|hp|frigorias|paginas|led|leds|modulo|amper|ampere|a|t)(?![a-z])"
)
_TYRE = re.compile(r"^\s*\d{3}\s*/\s*\d{2}\s*[rR]\s*\d{2}")
_SHOE = re.compile(r"^\s*\d{2,3}\s*[-/]\s*\d{2,3}\s*$")
_TRIPLE = re.compile(r"^\s*\d+(?:[.,]\d+)?\s*x\s*\d+(?:[.,]\d+)?\s*x\s*\d+(?:[.,]\d+)?\s*$")
_SCALE = re.compile(r"^\s*\d+\s*:\s*\d+\s*$")
_DIMENSION = re.compile(r"^\s*\d+(?:[.,]\d+)?\s*(?:x|por)\s*\d+(?:[.,]\d+)?\s*(?:cm|mm|m)?\s*$")
_MOJIBAKE = re.compile(r"[\ufffd\u00c3\u00c2]")


def normalize(text: str | None) -> str:
    text = unicodedata.normalize("NFKD", text or "")
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"\s+", " ", text.strip().lower())


def _result(kind: str, quantity: float | None, unit: str | None, source: str) -> dict[str, Any]:
    return {"quantity": quantity, "unit": unit, "kind": kind, "source": source}


def parse_one(raw: str | None) -> dict[str, Any]:
    """Parse a single size string."""

    if not raw:
        return _result("unknown", None, None, "")
    text = normalize(raw)
    if _MOJIBAKE.search(raw):
        return _result("unknown", None, None, raw)
    if _TYRE.match(text) or _SHOE.match(text) or _TRIPLE.match(text) or _SCALE.match(text):
        return _result("dimension", None, None, raw)
    if _DIMENSION.match(text):
        return _result("dimension", None, None, raw)

    number = _NUMBER.search(text)
    if not number:
        return _result("unknown", None, None, raw)
    try:
        value = float(number.group(1).replace(",", "."))
    except ValueError:
        return _result("unknown", None, None, raw)
    if value <= 0:
        return _result("unknown", None, None, raw)

    if _OUNCE.search(text):
        litres = value * 0.0295735
        return (_result("volume", round(litres, 4), "L", raw) if MIN_LITRES <= litres <= MAX_LITRES
                else _result("dimension", None, None, raw))
    if _POUND.search(text):
        kilos = value * 0.453592
        return (_result("mass", round(kilos, 4), "kg", raw) if MIN_KILOS <= kilos <= MAX_KILOS
                else _result("dimension", None, None, raw))
    if _VOLUME_ML.search(text):
        litres = value / 1000.0
        return (_result("volume", round(litres, 4), "L", raw) if MIN_LITRES <= litres <= MAX_LITRES
                else _result("dimension", None, None, raw))
    if _MASS_G.search(text) and not _MASS_KG.search(text):
        kilos = value / 1000.0
        return (_result("mass", round(kilos, 4), "kg", raw) if MIN_KILOS <= kilos <= MAX_KILOS
                else _result("dimension", None, None, raw))
    if _VOLUME_L.search(text):
        return (_result("volume", round(value, 4), "L", raw) if MIN_LITRES <= value <= MAX_LITRES
                else _result("dimension", None, None, raw))
    if _MASS_KG.search(text):
        return (_result("mass", round(value, 4), "kg", raw) if MIN_KILOS <= value <= MAX_KILOS
                else _result("dimension", None, None, raw))
    if _DIMENSION_UNIT.search(text):
        return _result("dimension", None, None, raw)
    if _COUNT.search(text):
        return _result("count", value, "count", raw)
    if re.fullmatch(r"\d+(?:[.,]\d+)?", text):
        return _result("count", value, "count", raw)
    return _result("unknown", None, None, raw)


def parse_size(*candidates: str | None) -> dict[str, Any]:
    """Parse the candidates in order, but prefer a real measure over a generic count."""

    best = _result("unknown", None, None, "")
    for raw in candidates:
        parsed = parse_one(raw)
        if parsed["kind"] in {"volume", "mass"}:
            return parsed
        if parsed["kind"] == "dimension":
            if best["kind"] == "unknown":
                best = parsed
            continue
        if parsed["kind"] == "count" and best["kind"] == "unknown":
            best = parsed
    return best

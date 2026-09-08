"""Bounded daily Argentina SEPA release orchestration.

This module is the operator-side coordinator for the Argentina daily release
milestone.  It deliberately keeps provider parsing at the existing M1--M8
tool boundary and owns only release identity, resumability, publication and
activation.  The normal path accepts one operator-supplied official SEPA ZIP;
the small JSON fixture path is used by tests and is never eligible for
production activation.

No network is used here.  Raw provider archives and expanded national data
remain local inputs.  Published objects contain only verified normalized
artifacts and compact indexes.
"""

from __future__ import annotations

import argparse
import contextlib
import csv
import datetime as _dt
import errno
import gzip
import hashlib
import json
import os
import re
import shutil
import sys
import tempfile
import threading
import time
import zipfile
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence

try:
    from backend.object_store import LocalFilesystemObjectStore, ObjectStoreError
    from tools.consumer_input_intelligence import CatalogIndex
    from tools.qualify_argentina_sepa import is_valid_gtin, parse_quantity
except ModuleNotFoundError:  # direct invocation from tools/
    from backend.object_store import LocalFilesystemObjectStore, ObjectStoreError
    from consumer_input_intelligence import CatalogIndex
    from qualify_argentina_sepa import is_valid_gtin, parse_quantity


SCHEMA_VERSION = "valuepilot-argentina-daily-release-v1"
POLICY_VERSION = "argentina-daily-release-policy-v1"
TOOL_VERSION = "argentina-daily-release-operations-v1"
PROVIDER = "ARGENTINA_SEPA_PRECIOS_CLAROS"
LICENSE = "Creative Commons Attribution 4.0"
ATTRIBUTION = "Precios Claros - Base SEPA; source: datos.produccion.gob.ar"
SOURCE_HOST = "https://datos.produccion.gob.ar"
BASELINE_RELEASE_DATE = "2026-09-06"
BASELINE_SOURCE_SHA256 = "e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305"
MOBILE_LOGICAL_PARTITIONS = 128
BACKEND_LOGICAL_PARTITIONS = 1024
PHYSICAL_PACKS = 32
STALE_AFTER_DAYS = 2
MAX_FIXTURE_ROWS = 100_000
MAX_INPUT_RECORDS = 100_000

# The 2026-09-06 release is the immutable M1--M8 reference for this
# milestone.  These are backend counts from its already-verified 1024/32
# bootstrap; they are used only when an older release manifest predates the
# daily-operations count fields.  The source qualification metrics below are
# read from the canonical committed M1 report, never re-derived from the raw
# Sunday archive.
BASELINE_BACKEND_COUNTS: dict[str, int] = {
    "publishableRegions": 24,
    "stores": 2537,
    "productEvidenceRecords": 1447719,
    "offers": 14139997,
    "promotions": 1677262,
}

STAGES: tuple[str, ...] = (
    "DISCOVERED",
    "STRUCTURALLY_VALIDATED",
    "QUALIFIED",
    "NORMALIZED",
    "BACKEND_BUILT",
    "INDEXED",
    "VERIFIED",
    "PUBLISHED",
    "ACTIVATED",
)
STAGE_NUM = {name: index for index, name in enumerate(STAGES)}

EXIT_CODES = {
    "OK": 0,
    "INVALID_SOURCE": 10,
    "INCOMPATIBLE_SCHEMA": 11,
    "QUALIFICATION_FAILED": 12,
    "BUILD_FAILED": 13,
    "VERIFICATION_FAILED": 14,
    "PUBLICATION_FAILED": 15,
    "ACTIVATION_FAILED": 16,
    "SECOND_RELEASE_REQUIRED": 12,
    "CONCURRENT_PUBLISHER": 15,
}

_HEX64 = re.compile(r"^[0-9a-f]{64}$")
_RELEASE_ID = re.compile(r"^[A-Za-z0-9._-]{1,128}$")


class DailyReleaseError(RuntimeError):
    """A deterministic, operator-visible pipeline failure."""

    def __init__(self, message: str, *, code: str = "BUILD_FAILED") -> None:
        super().__init__(message)
        self.code = code

    @property
    def exit_code(self) -> int:
        return EXIT_CODES.get(self.code, EXIT_CODES["BUILD_FAILED"])


class ConcurrentPublisherError(DailyReleaseError):
    def __init__(self, message: str = "another publisher holds the release lock") -> None:
        super().__init__(message, code="CONCURRENT_PUBLISHER")


def canonical_bytes(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def canonical_text(value: Any) -> str:
    return canonical_bytes(value).decode("utf-8") + "\n"


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _write_atomic(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _write_json(path: Path, value: Any) -> None:
    _write_atomic(path, canonical_bytes(value) + b"\n")


def _parse_date(value: Any) -> str:
    if not isinstance(value, str):
        raise DailyReleaseError("release date evidence is missing", code="INVALID_SOURCE")
    try:
        parsed = _dt.date.fromisoformat(value)
    except ValueError as exc:
        raise DailyReleaseError("release date evidence is not ISO YYYY-MM-DD", code="INVALID_SOURCE") from exc
    return parsed.isoformat()


def _decimal(value: Any, *, field: str, positive: bool = False) -> Decimal | None:
    if value is None or isinstance(value, bool):
        return None
    text = str(value).strip()
    if not re.fullmatch(r"(?:\d+(?:\.\d+)?|\.\d+)", text):
        return None
    try:
        parsed = Decimal(text)
    except InvalidOperation:
        return None
    if not parsed.is_finite() or (positive and parsed <= 0):
        return None
    return parsed


def _decimal_text(value: Decimal) -> str:
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def _release_id(release_date: str, source_sha256: str) -> str:
    return f"argentina-sepa-{release_date}-{source_sha256[:16]}"


def _safe_rel(path: str) -> str:
    value = Path(path).as_posix()
    if not value or value.startswith("/") or "\\" in value or any(part in {"", ".", ".."} for part in value.split("/")):
        raise DailyReleaseError(f"unsafe artifact path: {path}", code="VERIFICATION_FAILED")
    return value


@dataclass(frozen=True)
class SourceIdentity:
    path: str
    filename: str
    source_kind: str
    bytes: int
    sha256: str
    release_date: str
    release_evidence: str
    official: bool
    nested_packages: tuple[Mapping[str, Any], ...]
    provider: str = PROVIDER
    license: str = LICENSE
    attribution: str = ATTRIBUTION
    records: tuple[Mapping[str, Any], ...] = ()
    schema: Mapping[str, Any] = field(default_factory=dict)

    def as_dict(self) -> dict[str, Any]:
        return {
            "filename": self.filename,
            "sourceKind": self.source_kind,
            "bytes": self.bytes,
            "sha256": self.sha256,
            "releaseDate": self.release_date,
            "releaseEvidence": self.release_evidence,
            "official": self.official,
            "provider": self.provider,
            "license": self.license,
            "attribution": self.attribution,
            "nestedPackages": [dict(value) for value in self.nested_packages],
        }


def _fixture_source(path: Path) -> SourceIdentity:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DailyReleaseError(f"fixture source is unreadable: {path}", code="INVALID_SOURCE") from exc
    if not isinstance(value, Mapping) or value.get("fixture") is not True:
        raise DailyReleaseError("JSON input is not an explicit deterministic fixture", code="INVALID_SOURCE")
    source = value.get("source")
    if not isinstance(source, Mapping):
        raise DailyReleaseError("fixture source metadata is missing", code="INVALID_SOURCE")
    records = value.get("records")
    if not isinstance(records, list) or len(records) > MAX_FIXTURE_ROWS:
        raise DailyReleaseError("fixture record bound exceeded", code="INVALID_SOURCE")
    release_date = _parse_date(source.get("releaseDate"))
    raw = path.read_bytes()
    packages: list[Mapping[str, Any]] = []
    for index, package in enumerate(value.get("packages", [])):
        if not isinstance(package, Mapping):
            raise DailyReleaseError("fixture package metadata is invalid", code="INVALID_SOURCE")
        package_value = dict(package)
        package_value.setdefault("name", f"fixture-package-{index + 1}.zip")
        package_value.setdefault("bytes", 0)
        package_value.setdefault("sha256", sha256_bytes(canonical_bytes(package_value)))
        packages.append(package_value)
    return SourceIdentity(
        path=str(path.resolve()),
        filename=path.name,
        source_kind="FIXTURE",
        bytes=len(raw),
        sha256=sha256_bytes(raw),
        release_date=release_date,
        release_evidence="fixture source.release.releaseDate",
        official=False,
        nested_packages=tuple(packages),
        provider=str(source.get("provider") or PROVIDER),
        license=str(source.get("license") or LICENSE),
        attribution=str(source.get("attribution") or ATTRIBUTION),
        records=tuple(item for item in records if isinstance(item, Mapping)),
        schema=value.get("schema") if isinstance(value.get("schema"), Mapping) else {},
    )


def _zip_internal_date(names: Sequence[str]) -> str | None:
    dates: set[str] = set()
    for name in names:
        for match in re.findall(r"(?<!\d)(20\d{2}-\d{2}-\d{2})(?!\d)", name):
            try:
                dates.add(_dt.date.fromisoformat(match).isoformat())
            except ValueError:
                pass
    return sorted(dates)[0] if len(dates) == 1 else None


def _official_zip_source(path: Path) -> SourceIdentity:
    if not path.is_file() or path.stat().st_size <= 0:
        raise DailyReleaseError("official input ZIP is missing or empty", code="INVALID_SOURCE")
    digest = sha256_file(path)
    try:
        with zipfile.ZipFile(path) as outer:
            if outer.testzip() is not None:
                raise DailyReleaseError("outer ZIP CRC validation failed", code="INVALID_SOURCE")
            infos = [info for info in outer.infolist() if not info.is_dir() and info.filename.lower().endswith(".zip")]
            if not infos:
                raise DailyReleaseError("outer ZIP contains no nested retailer ZIPs", code="INCOMPATIBLE_SCHEMA")
            nested: list[Mapping[str, Any]] = []
            for info in infos:
                nested.append({"name": info.filename, "bytes": info.file_size, "compressedBytes": info.compress_size, "crc32": f"{info.CRC:08x}", "status": "EMPTY_PROVIDER_PACKAGE" if info.file_size == 0 else "PRESENT"})
            names = [info.filename for info in outer.infolist()]
            release_date = _zip_internal_date(names)
            if release_date is None:
                raise DailyReleaseError("internal release-date evidence is absent or ambiguous", code="INVALID_SOURCE")
    except zipfile.BadZipFile as exc:
        raise DailyReleaseError("official input is not a valid ZIP", code="INVALID_SOURCE") from exc
    return SourceIdentity(
        path=str(path.resolve()),
        filename=path.name,
        source_kind="OFFICIAL_SEPA_ZIP",
        bytes=path.stat().st_size,
        sha256=digest,
        release_date=release_date,
        release_evidence="outer ZIP internal dated package paths",
        official=True,
        nested_packages=tuple(nested),
    )


def inspect_source(path: Path, *, fixture: bool = False) -> SourceIdentity:
    path = Path(path).resolve()
    if not path.is_file():
        raise DailyReleaseError(f"source does not exist: {path}", code="INVALID_SOURCE")
    if fixture or path.suffix.lower() == ".json":
        return _fixture_source(path)
    return _official_zip_source(path)


def detect_schema_drift(schema: Mapping[str, Any]) -> dict[str, Any]:
    """Classify explicit fixture/schema metadata without guessing fields."""

    expected = {
        "commerceId", "storeId", "productEvidenceKey", "providerProductId", "name", "brand",
        "gtin", "price", "currency", "quantity", "province", "latitude", "longitude",
        "promotion", "providerUpdateTime",
    }
    fields = schema.get("recordFields")
    if fields is None:
        return {"classification": "NO_DECLARED_DRIFT", "added": [], "removed": [], "details": []}
    if not isinstance(fields, list) or any(not isinstance(item, str) for item in fields):
        return {"classification": "INCOMPATIBLE_DRIFT", "added": [], "removed": [], "details": ["recordFields is not a string list"]}
    actual = set(fields)
    added = sorted(actual - expected)
    removed = sorted(expected - actual)
    details = [str(item) for item in schema.get("drift", [])] if isinstance(schema.get("drift"), list) else []
    if schema.get("delimiter") not in (None, "|") or schema.get("encoding") not in (None, "utf-8", "utf-8-sig"):
        details.append("delimiter_or_encoding_change")
    if schema.get("renamed"):
        details.append("column_renamed")
    if schema.get("unexpectedUnits") or schema.get("unexpectedProvinces") or schema.get("unexpectedPriceStructure") or schema.get("unexpectedPromotionStructure"):
        details.append("semantic_vocabulary_change")
    if removed or schema.get("renamed"):
        classification = "INCOMPATIBLE_DRIFT"
    elif details or added:
        classification = "REVIEW_REQUIRED_DRIFT"
    else:
        classification = "NO_DECLARED_DRIFT"
    return {"classification": classification, "added": added, "removed": removed, "details": sorted(set(details))}


def _stage_identity(source: SourceIdentity, *, upstream: Mapping[str, str] | None, config: Mapping[str, Any]) -> dict[str, Any]:
    config_hash = sha256_bytes(canonical_bytes(config))
    return {
        "sourceSha256": source.sha256,
        "sourceBytes": source.bytes,
        "policyVersion": POLICY_VERSION,
        "toolVersion": TOOL_VERSION,
        "upstreamObjectHashes": dict(sorted((upstream or {}).items())),
        "configurationSha256": config_hash,
    }


def _file_descriptor(path: Path, *, relative_to: Path) -> dict[str, Any]:
    if not path.is_file():
        raise DailyReleaseError(f"stage output is missing: {path}", code="BUILD_FAILED")
    return {"path": _safe_rel(path.relative_to(relative_to).as_posix()), "bytes": path.stat().st_size, "sha256": sha256_file(path)}


def _read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DailyReleaseError(f"invalid JSON artifact: {path}", code="VERIFICATION_FAILED") from exc


def _int_metric(value: Any) -> int | None:
    """Return an integer metric without coercing missing/invalid evidence."""

    if isinstance(value, bool) or value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _qualification_metrics(report: Mapping[str, Any], *, source_report_sha256: str | None = None) -> dict[str, Any]:
    """Project the existing M1 report into compact M9 release diagnostics.

    M1 remains the only authority for SEPA parsing and quarantine decisions.
    This function merely copies its deterministic counters; it never opens the
    provider archive or invents a second set of acceptance rules.
    """

    scale = report.get("scale") if isinstance(report.get("scale"), Mapping) else {}
    identity = report.get("identity") if isinstance(report.get("identity"), Mapping) else {}
    price = report.get("price_quality") if isinstance(report.get("price_quality"), Mapping) else {}
    quantity = report.get("quantity_unit_value") if isinstance(report.get("quantity_unit_value"), Mapping) else {}
    diagnostics = report.get("diagnostics") if isinstance(report.get("diagnostics"), Mapping) else {}
    diagnostic_counts = diagnostics.get("counts") if isinstance(diagnostics.get("counts"), Mapping) else {}
    freshness = report.get("freshness") if isinstance(report.get("freshness"), Mapping) else {}
    quarantine_by_reason = {
        str(key)[11:]: int(value)
        for key, value in diagnostic_counts.items()
        if str(key).startswith("quarantine_") and _int_metric(value) is not None
    }
    rows_scanned = _int_metric(scale.get("rows_scanned")) or _int_metric(diagnostic_counts.get("rows_scanned")) or 0
    accepted_current = _int_metric(diagnostic_counts.get("accepted_current_price_rows"))
    if accepted_current is None:
        accepted_current = _int_metric(diagnostic_counts.get("positive_list_price_rows"))
    unknown_quantity = _int_metric(diagnostic_counts.get("quantity_unknown_rows"))
    if unknown_quantity is None:
        unknown_quantity = _int_metric(quantity.get("quantity_unknown_rows"))
    return {
        "rowsScanned": rows_scanned,
        "acceptedCurrentPriceRows": accepted_current,
        "quarantineRows": rows_scanned - accepted_current if accepted_current is not None else None,
        "quarantineByReason": quarantine_by_reason,
        "stores": _int_metric(scale.get("store_rows")),
        "validCoordinateStores": _int_metric(scale.get("coordinate_valid_store_rows")),
        "providerProductIds": _int_metric(scale.get("distinct_provider_product_ids")),
        "checksumValidUniqueGtins": _int_metric(identity.get("checksum_valid_unique_gtins")),
        "gtinValidRows": _int_metric(identity.get("gtin_valid_rows")),
        "commerceIds": _int_metric(scale.get("distinct_commerce_ids")),
        "bannerKeys": _int_metric(scale.get("distinct_banner_keys")),
        "provinces": _int_metric(scale.get("distinct_provinces")),
        "promotionRows": _int_metric(price.get("promotion_bearing_rows")),
        "quantityUnknownRows": unknown_quantity,
        "quantityParseableRows": _int_metric(diagnostic_counts.get("quantity_parseable_rows")) or _int_metric(quantity.get("quantity_parseable_rows")),
        "unitValueReadyRows": _int_metric(diagnostic_counts.get("unit_value_ready_rows")) or _int_metric(quantity.get("unit_value_ready_rows")),
        "invalidRequiredPriceRows": _int_metric(price.get("invalid_required_price_rows")),
        "lowPriceRows": _int_metric(price.get("price_under_ars_10_rows")),
        "highPriceRows": _int_metric(price.get("price_over_ars_10000000_rows")),
        "stalePackageCount": _int_metric(freshness.get("stale_package_count")),
        "stalePackageProductRows": _int_metric(freshness.get("stale_package_product_rows")),
        "status": report.get("status"),
        "sourceReportSha256": source_report_sha256,
    }


def _baseline_qualification_metrics() -> dict[str, Any]:
    """Read the committed Sunday qualification evidence without raw input."""

    report_path = Path(__file__).resolve().parents[1] / "ARGENTINA_SEPA_QUALIFICATION.json"
    if not report_path.is_file():
        return {"status": "NOT_AVAILABLE", "sourceReportSha256": None}
    try:
        report = _read_json(report_path)
    except DailyReleaseError:
        return {"status": "INVALID_COMMITTED_REPORT", "sourceReportSha256": None}
    if not isinstance(report, Mapping):
        return {"status": "INVALID_COMMITTED_REPORT", "sourceReportSha256": None}
    return _qualification_metrics(report, source_report_sha256=sha256_file(report_path))


def _write_jsonl(path: Path, rows: Iterable[Mapping[str, Any]]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=str(path.parent))
    count = 0
    try:
        with os.fdopen(fd, "wb") as handle:
            for row in rows:
                handle.write(canonical_bytes(dict(row)) + b"\n")
                count += 1
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_name, path)
    finally:
        Path(temporary_name).unlink(missing_ok=True)
    return count


def _iter_jsonl(path: Path) -> Iterator[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise DailyReleaseError(f"invalid JSONL at {path}:{number}", code="VERIFICATION_FAILED") from exc
            if not isinstance(value, dict):
                raise DailyReleaseError(f"JSONL row is not an object at {path}:{number}", code="VERIFICATION_FAILED")
            yield value


def _iter_jsonl_maybe_gzip(path: Path) -> Iterator[dict[str, Any]]:
    """Read a deterministic JSONL or gzip JSONL index without buffering it."""

    if path.suffix.lower() == ".gz":
        with gzip.open(path, "rt", encoding="utf-8") as handle:
            for number, line in enumerate(handle, 1):
                if not line.strip():
                    continue
                try:
                    value = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise DailyReleaseError(f"invalid gzip JSONL at {path}:{number}", code="VERIFICATION_FAILED") from exc
                if not isinstance(value, dict):
                    raise DailyReleaseError(f"gzip JSONL row is not an object at {path}:{number}", code="VERIFICATION_FAILED")
                yield value
        return
    yield from _iter_jsonl(path)


def _valid_geo(record: Mapping[str, Any]) -> tuple[str, str | None, str | None]:
    latitude = _decimal(record.get("latitude"), field="latitude")
    longitude = _decimal(record.get("longitude"), field="longitude")
    if latitude is None or longitude is None:
        return "GEO_INCOMPLETE", _decimal_text(latitude) if latitude is not None else None, _decimal_text(longitude) if longitude is not None else None
    if not (Decimal("-56") <= latitude <= Decimal("-21") and Decimal("-74") <= longitude <= Decimal("-52")):
        return "GEO_OUT_OF_BOUNDS", _decimal_text(latitude), _decimal_text(longitude)
    return "VALID", _decimal_text(latitude), _decimal_text(longitude)


def _quantity_value(raw: Any) -> tuple[dict[str, Any] | None, str]:
    if not isinstance(raw, Mapping):
        return None, "UNKNOWN"
    value = raw.get("value")
    unit = raw.get("unit")
    parsed = parse_quantity(str(value) if value is not None else None, str(unit) if unit is not None else None)
    if parsed is None:
        return None, "UNKNOWN"
    canonical_unit, amount = parsed
    return {"unit": canonical_unit, "amount": _decimal_text(amount), "raw": {"value": str(value), "unit": str(unit)}}, "KNOWN"


def _package_freshness(record: Mapping[str, Any], release_date: str) -> str:
    raw = record.get("providerUpdateTime") or record.get("packageUpdatedAt")
    if not isinstance(raw, str) or not raw:
        return "UNKNOWN"
    normalized = raw.replace("Z", "+00:00")
    try:
        updated = _dt.datetime.fromisoformat(normalized)
        if updated.tzinfo is None:
            updated = updated.replace(tzinfo=_dt.timezone.utc)
        release = _dt.datetime.combine(_dt.date.fromisoformat(release_date), _dt.time.min, tzinfo=_dt.timezone.utc)
    except ValueError:
        return "UNKNOWN"
    return "STALE" if release - updated.astimezone(_dt.timezone.utc) > _dt.timedelta(days=STALE_AFTER_DAYS) else "FRESH"


def _normalise_fixture(source: SourceIdentity, destination: Path) -> dict[str, Any]:
    records = sorted(source.records, key=lambda row: (str(row.get("commerceId", "")), str(row.get("storeId", "")), str(row.get("productEvidenceKey", "")), str(row.get("providerProductId", ""))))
    stores: dict[str, dict[str, Any]] = {}
    products: dict[str, dict[str, Any]] = {}
    offers: list[dict[str, Any]] = []
    quarantine: list[dict[str, Any]] = []
    for line_number, raw in enumerate(records, 1):
        if not isinstance(raw, Mapping):
            quarantine.append({"reason": "MALFORMED_ROW", "sourceRow": line_number})
            continue
        commerce = str(raw.get("commerceId") or "")
        store_id = str(raw.get("storeId") or "")
        product_key = str(raw.get("productEvidenceKey") or "")
        if not commerce or not store_id or not product_key:
            quarantine.append({"reason": "MALFORMED_ROW", "sourceRow": line_number})
            continue
        store_key = f"{commerce}:{store_id}"
        if raw.get("storeKnown") is False:
            quarantine.append({"reason": "UNKNOWN_STORE_REFERENCE", "sourceRow": line_number, "storeKey": store_key, "productEvidenceKey": product_key})
            continue
        geo_status, latitude, longitude = _valid_geo(raw)
        stores.setdefault(store_key, {"storeKey": store_key, "commerceId": commerce, "storeId": store_id, "name": raw.get("storeName"), "province": raw.get("province"), "locality": raw.get("locality"), "latitude": latitude, "longitude": longitude, "geoStatus": geo_status})
        provider_product_id = str(raw.get("providerProductId") or product_key)
        gtin = str(raw.get("gtin")) if raw.get("gtin") is not None else None
        gtin_valid = bool(gtin and is_valid_gtin(gtin))
        quantity, quantity_status = _quantity_value(raw.get("quantity"))
        products.setdefault(product_key, {"productEvidenceKey": product_key, "providerProductId": provider_product_id, "name": str(raw.get("name") or product_key), "brand": raw.get("brand"), "gtin": gtin if gtin_valid else None, "gtinStatus": "VALID" if gtin_valid else "INVALID_OR_NOT_GTIN", "quantity": quantity, "quantityStatus": quantity_status, "provenance": {"sourceRow": line_number, "provider": source.provider}})
        price = _decimal(raw.get("price"), field="price", positive=True)
        currency = str(raw.get("currency") or "ARS")
        if price is None or currency != "ARS":
            quarantine.append({"reason": "INVALID_REQUIRED_PRICE" if price is None else "INVALID_CURRENCY", "sourceRow": line_number, "productEvidenceKey": product_key, "currency": currency})
            continue
        freshness = _package_freshness(raw, source.release_date)
        low_high = price < Decimal("10") or price > Decimal("10000000")
        offer_key = sha256_bytes(canonical_bytes({"source": source.sha256, "row": line_number, "store": store_key, "product": product_key}))
        offers.append({"offerKey": offer_key, "productEvidenceKey": product_key, "storeKey": store_key, "listPrice": {"amount": _decimal_text(price), "currency": "ARS"}, "quantity": quantity, "promotions": list(raw.get("promotions", [])) if isinstance(raw.get("promotions"), list) else [], "availability": "UNKNOWN", "observationTime": None, "providerUpdateTime": raw.get("providerUpdateTime"), "freshnessStatus": freshness, "pricePlausibility": "REVIEW" if low_high else "NORMAL", "eligibleForCurrentPlan": freshness == "FRESH" and not low_high, "provenance": {"provider": source.provider, "sourceSha256": source.sha256, "releaseDate": source.release_date, "sourceRow": line_number, "commerceId": commerce, "storeId": store_id, "providerProductId": provider_product_id}})
    artifact_rows = {"stores": stores, "products": products, "offers": offers, "quarantine": quarantine}
    _write_jsonl(destination / "stores.jsonl", (stores[key] for key in sorted(stores)))
    _write_jsonl(destination / "products.jsonl", (products[key] for key in sorted(products)))
    _write_jsonl(destination / "offers.jsonl", offers)
    _write_jsonl(destination / "quarantine.jsonl", quarantine)
    _write_json(destination / "normalization-summary.json", {"schemaVersion": SCHEMA_VERSION, "completionState": "COMPLETE", "counts": {"stores": len(stores), "products": len(products), "offers": len(offers), "quarantine": len(quarantine)}})
    return {"counts": {"stores": len(stores), "products": len(products), "offers": len(offers), "quarantine": len(quarantine)}, "artifactRows": artifact_rows}


class SingleWriterLock:
    """Cross-process lock with a portable Windows/POSIX implementation."""

    def __init__(self, path: Path):
        self.path = Path(path)
        self.handle: Any = None

    def __enter__(self) -> "SingleWriterLock":
        self.path.parent.mkdir(parents=True, exist_ok=True)
        # ``a+b`` forces every write to the end on Windows, which can attempt
        # to write through an already-held byte range when a second publisher
        # opens the lock.  Open an existing file read/write and create only
        # when absent, then lock byte zero without mutating it while locked.
        self.handle = self.path.open("r+b") if self.path.exists() else self.path.open("w+b")
        try:
            if os.name == "nt":
                import msvcrt
                self.handle.seek(0)
                if self.handle.read(1) == b"":
                    self.handle.seek(0)
                    self.handle.write(b"0")
                    self.handle.flush()
                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except (OSError, IOError) as exc:
            self.handle.close()
            self.handle = None
            if getattr(exc, "errno", None) in {errno.EACCES, errno.EAGAIN, errno.EDEADLK} or os.name == "nt":
                raise ConcurrentPublisherError() from exc
            raise
        return self

    def __exit__(self, exc_type: Any, exc: Any, tb: Any) -> None:
        if self.handle is None:
            return
        try:
            if os.name == "nt":
                import msvcrt
                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(self.handle.fileno(), fcntl.LOCK_UN)
        finally:
            self.handle.close()
            self.handle = None


class ContentAddressedStore:
    """Content-addressed publication coordinator over the shared ObjectStore."""

    def __init__(self, root: Path):
        self.root = Path(root).resolve()
        self.objects = self.root / "objects" / "sha256"
        self.releases = self.root / "releases"
        self.control = self.root / "control"
        self.objects.mkdir(parents=True, exist_ok=True)
        self.releases.mkdir(parents=True, exist_ok=True)
        self.control.mkdir(parents=True, exist_ok=True)
        self.object_store = LocalFilesystemObjectStore(self.objects)

    def object_path(self, digest: str) -> Path:
        if not _HEX64.fullmatch(digest):
            raise DailyReleaseError("invalid object digest", code="VERIFICATION_FAILED")
        return self.objects / digest

    def put_file(self, path: Path) -> tuple[str, int, bool]:
        digest = sha256_file(path)
        was_reused = self.object_store.exists(digest)
        try:
            metadata = self.object_store.put_immutable_file(digest, path, sha256=digest)
        except ObjectStoreError as exc:
            raise DailyReleaseError(f"existing content-addressed object is corrupt: {digest}", code="PUBLICATION_FAILED") from exc
        return digest, metadata.size, was_reused

    def read_active(self) -> dict[str, Any] | None:
        path = self.control / "active.json"
        if not path.is_file():
            return None
        value = _read_json(path)
        if not isinstance(value, dict) or not isinstance(value.get("releaseId"), str):
            raise DailyReleaseError("active pointer is invalid", code="VERIFICATION_FAILED")
        return value

    def read_release(self, release_id: str) -> dict[str, Any]:
        if not _RELEASE_ID.fullmatch(release_id):
            raise DailyReleaseError("release ID is invalid", code="VERIFICATION_FAILED")
        value = _read_json(self.releases / release_id / "manifest.json")
        if not isinstance(value, dict) or value.get("completionState") != "COMPLETE":
            raise DailyReleaseError("release manifest is incomplete", code="VERIFICATION_FAILED")
        return value

    def verify_manifest_objects(self, manifest: Mapping[str, Any]) -> None:
        for descriptor in manifest.get("objects", []):
            if not isinstance(descriptor, Mapping):
                raise DailyReleaseError("release object descriptor is invalid", code="VERIFICATION_FAILED")
            digest = descriptor.get("sha256")
            if not isinstance(digest, str):
                raise DailyReleaseError("release object hash is missing", code="VERIFICATION_FAILED")
            try:
                metadata = self.object_store.head(digest)
            except ObjectStoreError as exc:
                raise DailyReleaseError(f"published object failed verification: {digest}", code="VERIFICATION_FAILED") from exc
            if metadata.size != int(descriptor.get("bytes", -1)) or metadata.sha256 != digest:
                raise DailyReleaseError(f"published object failed verification: {digest}", code="VERIFICATION_FAILED")


def _stage_reusable(stage_dir: Path, expected_identity: Mapping[str, Any], *, root: Path) -> bool:
    manifest_path = stage_dir / "stage.json"
    if not manifest_path.is_file():
        return False
    try:
        manifest = _read_json(manifest_path)
    except DailyReleaseError:
        return False
    if not isinstance(manifest, Mapping) or manifest.get("completionState") != "COMPLETE" or manifest.get("identity") != dict(expected_identity):
        return False
    for descriptor in manifest.get("outputs", []):
        if not isinstance(descriptor, Mapping):
            return False
        path = stage_dir / str(descriptor.get("path", ""))
        if not path.is_file() or path.stat().st_size != int(descriptor.get("bytes", -1)) or sha256_file(path) != descriptor.get("sha256"):
            return False
    return True


def _stage_manifest(stage_dir: Path, stage: str, identity: Mapping[str, Any], *, root: Path, metadata: Mapping[str, Any]) -> None:
    outputs = []
    for path in sorted(stage_dir.rglob("*")):
        if path.is_file() and path.name != "stage.json":
            outputs.append(_file_descriptor(path, relative_to=stage_dir))
    _write_json(stage_dir / "stage.json", {"schemaVersion": SCHEMA_VERSION, "stage": stage, "completionState": "COMPLETE", "identity": dict(identity), "outputs": outputs, "metadata": dict(metadata)})


def _atomic_stage(stage_root: Path, stage: str, identity: Mapping[str, Any], builder: Any, *, metadata: Mapping[str, Any]) -> tuple[Path, bool, dict[str, Any]]:
    final = stage_root / stage.lower()
    if _stage_reusable(final, identity, root=stage_root):
        manifest = _read_json(final / "stage.json")
        return final, True, dict(manifest.get("metadata", {}))
    if final.exists():
        shutil.rmtree(final)
    stage_root.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=f".{stage.lower()}.", dir=str(stage_root)))
    completed = False
    try:
        metadata_value = builder(temporary)
        if not isinstance(metadata_value, Mapping):
            metadata_value = {}
        _stage_manifest(temporary, stage, identity, root=stage_root, metadata=metadata_value)
        os.replace(temporary, final)
        completed = True
        return final, False, dict(metadata_value)
    finally:
        if not completed:
            shutil.rmtree(temporary, ignore_errors=True)


def _copy_stage_files(stage: Path, destination: Path) -> list[Path]:
    destination.mkdir(parents=True, exist_ok=True)
    outputs: list[Path] = []
    for path in sorted(stage.rglob("*")):
        if not path.is_file() or path.name == "stage.json":
            continue
        target = destination / path.relative_to(stage)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        outputs.append(target)
    return outputs


@dataclass
class DailyReleaseResult:
    release_id: str
    source: SourceIdentity
    candidate_root: Path
    last_stage: str
    report: dict[str, Any]
    reused_stages: tuple[str, ...] = ()


class DailyReleaseOrchestrator:
    """Run one deterministic release candidate and optionally publish it."""

    def __init__(self, *, source_path: Path, workspace: Path, output: Path, fixture: bool = False, previous_release: Path | None = None, activate: bool = False, dry_run: bool = True, allow_fixture_activation: bool = False, fail_after_stage: str | None = None, fail_publication_after: int | None = None, operator_acquired_at: str | None = None):
        self.source_path = Path(source_path).resolve()
        self.workspace = Path(workspace).resolve()
        self.output = Path(output).resolve()
        self.fixture = fixture
        self.previous_release = Path(previous_release).resolve() if previous_release else None
        self.activate = activate
        self.dry_run = dry_run and not activate
        self.allow_fixture_activation = allow_fixture_activation
        self.fail_after_stage = fail_after_stage
        self.fail_publication_after = fail_publication_after
        self.operator_acquired_at = operator_acquired_at
        self.stage_root = self.output / "stages"
        self.artifact_root = self.output / "artifacts"
        self.timings: dict[str, float] = {}
        self.reused_stages: list[str] = []

    def _previous_manifest(self, store: ContentAddressedStore) -> dict[str, Any] | None:
        if self.previous_release is not None:
            path = self.previous_release / "manifest.json" if self.previous_release.is_dir() else self.previous_release
            if path.is_file():
                value = _read_json(path)
                return value if isinstance(value, dict) else None
        active = store.read_active()
        if active is None:
            return None
        return store.read_release(active["releaseId"])

    def _source_gate(self, source: SourceIdentity, previous: Mapping[str, Any] | None) -> bool:
        old_date = str((previous or {}).get("source", {}).get("releaseDate") or BASELINE_RELEASE_DATE)
        if source.official and (source.release_date <= old_date or source.sha256 == BASELINE_SOURCE_SHA256):
            raise DailyReleaseError(f"source {source.release_date} is not newer than qualified reference {old_date}", code="SECOND_RELEASE_REQUIRED")
        return source.official and source.release_date > old_date

    def _identity(self, source: SourceIdentity, upstream: Mapping[str, str] | None = None) -> dict[str, Any]:
        return _stage_identity(source, upstream=upstream, config={"fixture": self.fixture, "releaseDate": source.release_date, "mobileLogicalPartitions": MOBILE_LOGICAL_PARTITIONS, "backendLogicalPartitions": BACKEND_LOGICAL_PARTITIONS, "physicalPacks": PHYSICAL_PACKS, "staleAfterDays": STALE_AFTER_DAYS})

    def _write_source_stage(self, root: Path, source: SourceIdentity) -> dict[str, Any]:
        _write_json(root / "source.json", source.as_dict())
        _write_json(root / "schema-drift.json", detect_schema_drift(source.schema))
        return {"source": source.as_dict(), "schemaDrift": detect_schema_drift(source.schema)}

    def _validate_structure(self, root: Path, source: SourceIdentity) -> dict[str, Any]:
        drift = detect_schema_drift(source.schema)
        if drift["classification"] == "INCOMPATIBLE_DRIFT":
            raise DailyReleaseError("incompatible source schema drift", code="INCOMPATIBLE_SCHEMA")
        if source.official:
            packages = [dict(value) for value in source.nested_packages]
            if any(value.get("status") == "PRESENT" and int(value.get("bytes", 0)) <= 0 for value in packages):
                raise DailyReleaseError("non-empty provider package has zero bytes", code="INCOMPATIBLE_SCHEMA")
            _write_json(root / "structure.json", {"requiredFiles": ["comercio.csv", "sucursales.csv", "productos.csv"], "nestedPackages": packages, "outerZipValidated": True})
        else:
            _write_json(root / "structure.json", {"fixtureRecords": len(source.records), "nestedPackages": [dict(value) for value in source.nested_packages], "outerZipValidated": False})
        return {"schemaDrift": drift, "nestedPackages": len(source.nested_packages), "zeroBytePackages": sum(1 for value in source.nested_packages if int(value.get("bytes", 0)) == 0)}

    def _qualify(self, root: Path, source: SourceIdentity) -> dict[str, Any]:
        if self.fixture:
            _write_json(root / "qualification.json", {"status": "FIXTURE_ONLY", "official": False, "sourceSha256": source.sha256, "releaseDate": source.release_date, "strictProviderRules": True})
            return {"status": "FIXTURE_ONLY", "accepted": len(source.records), "quarantine": 0}
        # The official path intentionally delegates parsing and all M1 rules
        # to the existing qualification authority.  No alternate parser lives
        # in the release coordinator.
        try:
            try:
                from tools.qualify_argentina_sepa import QualificationRun, SepaConfig
            except ModuleNotFoundError:  # direct invocation from tools/
                from qualify_argentina_sepa import QualificationRun, SepaConfig
            report = QualificationRun(SepaConfig(input_path=Path(source.path), release_date=_dt.date.fromisoformat(source.release_date), expected_sha256=source.sha256, expected_bytes=source.bytes, output_dir=root, report_json=root / "qualification-report.json", report_markdown=root / "qualification-report.md", write_normalized=True)).run()
        except Exception as exc:  # noqa: BLE001 - normalize provider-tool failures
            raise DailyReleaseError(f"official SEPA qualification failed: {exc}", code="QUALIFICATION_FAILED") from exc
        return {"status": report.get("status", "QUALIFIED"), "accepted": int(report.get("scale", {}).get("rows_scanned", 0)), "quarantine": int(report.get("diagnostics", {}).get("counts", {}).get("quarantine_rows", 0))}

    def _normalise(self, root: Path, source: SourceIdentity, qualification: Mapping[str, Any]) -> dict[str, Any]:
        if self.fixture:
            return _normalise_fixture(source, root)
        qualified = root.parent / "qualified"
        accepted = qualified / "accepted-observations.ndjson.gz"
        if not accepted.is_file():
            raise DailyReleaseError("qualified accepted stream is missing", code="BUILD_FAILED")
        # The M1 accepted stream is already provider-neutral and deterministic.
        # Keep the stream in the private qualification stage for downstream
        # builders, but never copy the national stream into the publishable
        # candidate artifact root.
        manifest = qualified / "manifest.json"
        if not manifest.is_file():
            raise DailyReleaseError("qualification manifest is missing", code="BUILD_FAILED")
        _write_json(root / "qualification-manifest.json", _read_json(manifest))
        _write_json(root / "normalization-summary.json", {"schemaVersion": SCHEMA_VERSION, "completionState": "COMPLETE", "sourceAcceptedStream": str(accepted.name), "delegatedTo": "tools.qualify_argentina_sepa", "rawStreamPublished": False})
        return {"counts": {"acceptedStream": accepted.stat().st_size, "quarantineStream": (qualified / "quarantine.ndjson.gz").stat().st_size if (qualified / "quarantine.ndjson.gz").is_file() else 0}, "rawStreamPublished": False}

    def _build_backend(self, root: Path, source: SourceIdentity) -> dict[str, Any]:
        if self.fixture:
            _write_json(root / "backend-profile.json", {"schemaVersion": SCHEMA_VERSION, "profileVersion": "argentina-backend-online-1024-32-v1", "logicalPartitionCount": BACKEND_LOGICAL_PARTITIONS, "physicalPackCount": PHYSICAL_PACKS, "mobileContract": {"logicalPartitionCount": MOBILE_LOGICAL_PARTITIONS, "physicalPackCount": PHYSICAL_PACKS, "status": "PRESERVED_NOT_REBUILT"}, "equivalence": "REUSED_EXISTING_M5_M7_PROOF", "sourceReleaseDate": source.release_date})
            return {"profile": "1024/32", "mobileContract": "128/32 preserved"}
        qualified = root.parent / "qualified"
        accepted = qualified / "accepted-observations.ndjson.gz"
        manifest_path = qualified / "manifest.json"
        national = root / "national"
        try:
            try:
                from tools.build_argentina_sepa_national_shards import build_national_shards
                from tools.build_argentina_sepa_query_selective_mobile import build_query_selective_mobile
                from tools.build_argentina_sepa_micro_partition_mobile import build_micro_partition_mobile
            except ModuleNotFoundError:  # direct invocation from tools/
                from build_argentina_sepa_national_shards import build_national_shards
                from build_argentina_sepa_query_selective_mobile import build_query_selective_mobile
                from build_argentina_sepa_micro_partition_mobile import build_micro_partition_mobile
            index = build_national_shards(accepted, manifest_path, national, generated_at=f"{source.release_date}T00:00:00Z", expected_outer_sha256=source.sha256, expected_outer_bytes=source.bytes, expected_release_date=source.release_date)
            qualified_manifest = _read_json(manifest_path)
            accepted_sha = qualified_manifest["files"]["accepted-observations.ndjson.gz"]["sha256"]
            national_sha = sha256_file(national / "index.json")
            selective = root / "query-selective"
            build_query_selective_mobile(national, selective, generated_at=f"{source.release_date}T00:00:00Z", expected_outer_sha256=source.sha256, expected_outer_bytes=source.bytes, expected_release_date=source.release_date, expected_accepted_sha256=accepted_sha, expected_national_index_sha256=national_sha)
            micro = root / "micro-1024"
            micro_bootstrap = build_micro_partition_mobile(selective, micro, generated_at=f"{source.release_date}T00:00:00Z", logical_partition_count=BACKEND_LOGICAL_PARTITIONS, physical_pack_count=PHYSICAL_PACKS, expected_outer_sha256=source.sha256, expected_outer_bytes=source.bytes, expected_release_date=source.release_date, expected_accepted_sha256=accepted_sha, expected_national_index_sha256=national_sha)
        except Exception as exc:  # noqa: BLE001
            raise DailyReleaseError(f"national backend build failed: {exc}", code="BUILD_FAILED") from exc
        _write_json(root / "backend-profile.json", {"schemaVersion": SCHEMA_VERSION, "profileVersion": "argentina-backend-online-1024-32-v1", "logicalPartitionCount": BACKEND_LOGICAL_PARTITIONS, "physicalPackCount": PHYSICAL_PACKS, "mobileContract": {"logicalPartitionCount": MOBILE_LOGICAL_PARTITIONS, "physicalPackCount": PHYSICAL_PACKS, "status": "PRESERVED_NOT_REBUILT"}, "nationalIndex": {"path": "national/index.json", "sha256": national_sha}, "querySelective": {"path": "query-selective/bootstrap.json", "sha256": sha256_file(selective / "bootstrap.json")}, "backendRoot": {"path": "micro-1024/bootstrap.json", "sha256": sha256_file(micro / "bootstrap.json")}, "counts": micro_bootstrap.get("totals", index.get("totals", {}))})
        return {"profile": "1024/32", "mobileContract": "128/32 preserved", "counts": micro_bootstrap.get("totals", index.get("totals", {})), "nationalIndexSha256": national_sha}

    def _build_indexes(self, root: Path, source: SourceIdentity) -> dict[str, Any]:
        if self.fixture:
            products_path = root.parent / "normalized" / "products.jsonl"
            stores_path = root.parent / "normalized" / "stores.jsonl"
            if not products_path.is_file():
                products_path = root.parent / "normalised" / "products.jsonl"
            products = list(_iter_jsonl(products_path)) if products_path.is_file() else []
            stores = list(_iter_jsonl(stores_path)) if stores_path.is_file() else []
        else:
            # The M4/M5 search index is the source of truth for product
            # identities.  The small vocabulary is rebuilt from it without
            # copying prices or offers.
            products = []
            stores = []
            backend_root = root.parent / "backend_built" / "micro-1024"
            product_paths = sorted(backend_root.rglob("search-index.jsonl*"))
            store_paths = sorted(backend_root.rglob("store-index.jsonl*"))
            # The existing M8 vocabulary contract is bounded.  Keep the
            # deterministic first 100,000 provider identities in stable
            # region/file order rather than retaining a national list.
            for path in product_paths:
                for value in _iter_jsonl_maybe_gzip(path):
                    products.append(value)
                    if len(products) >= MAX_INPUT_RECORDS:
                        break
                if len(products) >= MAX_INPUT_RECORDS:
                    break
            for path in store_paths:
                stores.extend(_iter_jsonl_maybe_gzip(path))
        unique_products: dict[str, Mapping[str, Any]] = {}
        for value in products:
            key = value.get("productEvidenceKey") if isinstance(value, Mapping) else None
            if isinstance(key, str) and key not in unique_products:
                unique_products[key] = {"productEvidenceKey": key, "name": value.get("name") or key, "brand": value.get("brand"), "gtin": value.get("gtin"), "quantity": value.get("quantity"), "provenance": {"provider": source.provider, "releaseDate": source.release_date, "sourceSha256": source.sha256}}
        ordered = [unique_products[key] for key in sorted(unique_products)][:MAX_INPUT_RECORDS]
        index = CatalogIndex.from_records(ordered, max_records=MAX_INPUT_RECORDS)
        _write_jsonl(root / "search-index.jsonl", ordered)
        _write_jsonl(root / "store-index.jsonl", (value for value in sorted(stores, key=lambda row: str(row.get("storeKey", "")))))
        vocabulary = {"schemaVersion": "valuepilot-argentina-input-vocabulary-builder-v1", "vocabulary": index.as_manifest(), "source": {"provider": source.provider, "releaseDate": source.release_date, "sourceSha256": source.sha256, "productImagesIncluded": False}, "records": [record.as_dict() for record in index.records], "atomicCompletion": True, "completionState": "COMPLETE"}
        vocabulary["artifactSha256"] = sha256_bytes(canonical_bytes(vocabulary))
        _write_json(root / "input-vocabulary.json", vocabulary)
        return {"productIdentities": len(ordered), "storeRows": len(stores), "inputVocabularySha256": sha256_file(root / "input-vocabulary.json")}

    def _verify_candidate(self, root: Path, source: SourceIdentity) -> dict[str, Any]:
        backend_root = root.parent / "backend_built"
        index_root = root.parent / "indexed"
        normalized_root = root.parent / "normalized"
        required = [backend_root / "backend-profile.json", index_root / "input-vocabulary.json", index_root / "search-index.jsonl", index_root / "store-index.jsonl"]
        if self.fixture:
            required.extend(normalized_root / name for name in ("stores.jsonl", "products.jsonl", "offers.jsonl", "quarantine.jsonl"))
        for path in required:
            if not path.is_file():
                raise DailyReleaseError(f"required candidate artifact is missing: {path.name}", code="VERIFICATION_FAILED")
        profile = _read_json(backend_root / "backend-profile.json")
        if profile.get("logicalPartitionCount") != BACKEND_LOGICAL_PARTITIONS or profile.get("physicalPackCount") != PHYSICAL_PACKS:
            raise DailyReleaseError("backend profile is not 1024/32", code="VERIFICATION_FAILED")
        vocabulary = _read_json(index_root / "input-vocabulary.json")
        if vocabulary.get("completionState") != "COMPLETE" or vocabulary.get("source", {}).get("sourceSha256") != source.sha256:
            raise DailyReleaseError("input vocabulary provenance is invalid", code="VERIFICATION_FAILED")
        if self.fixture:
            products = {row["productEvidenceKey"] for row in _iter_jsonl(normalized_root / "products.jsonl")}
            stores = {row["storeKey"] for row in _iter_jsonl(normalized_root / "stores.jsonl")}
            for offer in _iter_jsonl(normalized_root / "offers.jsonl"):
                if offer["productEvidenceKey"] not in products or offer["storeKey"] not in stores or offer["availability"] != "UNKNOWN":
                    raise DailyReleaseError("offer reference or availability boundary failed", code="VERIFICATION_FAILED")
                if offer["listPrice"]["currency"] != "ARS" or Decimal(offer["listPrice"]["amount"]) <= 0:
                    raise DailyReleaseError("offer money boundary failed", code="VERIFICATION_FAILED")
        return {"verified": True, "requiredArtifacts": len(required), "m6": "PRESERVED", "m7": "PRESERVED", "m8": "REBUILT"}

    def _read_qualification_report(self) -> tuple[dict[str, Any], str | None]:
        """Read the completed M1 report; never re-run or reopen its source."""

        report_path = self.stage_root / "qualified" / "qualification-report.json"
        if report_path.is_file():
            value = _read_json(report_path)
            if isinstance(value, Mapping):
                return dict(value), sha256_file(report_path)
        if self.fixture:
            return {"status": "FIXTURE_ONLY"}, None
        return {"status": "NOT_AVAILABLE"}, None

    def _previous_backend_counts(self, previous: Mapping[str, Any] | None) -> dict[str, int]:
        value = (previous or {}).get("backendCounts")
        if isinstance(value, Mapping):
            parsed = {str(key): int(item) for key, item in value.items() if _int_metric(item) is not None}
            if parsed:
                return parsed
        return dict(BASELINE_BACKEND_COUNTS)

    @staticmethod
    def _count_delta(old: Any, new: Any) -> dict[str, Any]:
        old_value = _int_metric(old)
        new_value = _int_metric(new)
        return {"old": old_value, "new": new_value, "delta": new_value - old_value if old_value is not None and new_value is not None else None}

    def _nested_package_provenance(self, qualification_report: Mapping[str, Any], source: SourceIdentity) -> list[dict[str, Any]]:
        by_name: dict[str, Mapping[str, Any]] = {}
        freshness = qualification_report.get("freshness")
        if isinstance(freshness, Mapping) and isinstance(freshness.get("packages"), list):
            for package in freshness["packages"]:
                if isinstance(package, Mapping) and isinstance(package.get("name"), str):
                    by_name[str(package["name"])] = package
        enriched: list[dict[str, Any]] = []
        for package in source.nested_packages:
            value = dict(package)
            observed = by_name.get(str(value.get("name")))
            if observed is not None:
                for key in ("sha256", "status", "package_update_time", "freshness_status", "inner_uncompressed_bytes", "product_rows", "accepted_rows", "quarantined_rows"):
                    if key in observed:
                        value[key] = observed[key]
            enriched.append(value)
        return enriched

    def _uncompressed_size_map(self) -> dict[str, int]:
        """Recover backend-declared uncompressed sizes for published objects."""

        values: dict[str, int] = {}
        micro = self.artifact_root / "micro-1024"
        if not micro.is_dir():
            return values
        for manifest_path in sorted(micro.glob("regions/*/manifest.json")):
            try:
                manifest = _read_json(manifest_path)
            except DailyReleaseError:
                continue
            files = manifest.get("files") if isinstance(manifest, Mapping) else None
            if not isinstance(files, Mapping):
                continue
            for key in ("searchIndex", "storeIndex"):
                descriptor = files.get(key)
                if isinstance(descriptor, Mapping) and isinstance(descriptor.get("path"), str) and _int_metric(descriptor.get("uncompressedBytes")) is not None:
                    values[f"micro-1024/{descriptor['path']}"] = int(descriptor["uncompressedBytes"])
            partitions = files.get("logicalPartitions")
            if isinstance(partitions, list):
                for descriptor in partitions:
                    if not isinstance(descriptor, Mapping) or not isinstance(descriptor.get("path"), str):
                        continue
                    path = f"micro-1024/{descriptor['path']}"
                    amount = _int_metric(descriptor.get("uncompressedBytes"))
                    if amount is not None:
                        values[path] = values.get(path, 0) + amount
        return values

    def _candidate_files(self) -> list[Path]:
        return [path for path in sorted(self.artifact_root.rglob("*")) if path.is_file() and path.suffix.lower() != ".zip"]

    def _dedup(self, descriptors: Sequence[Mapping[str, Any]], previous: Mapping[str, Any] | None) -> dict[str, Any]:
        old = {str(item.get("sha256")): int(item.get("bytes", 0)) for item in (previous or {}).get("objects", []) if isinstance(item, Mapping) and isinstance(item.get("sha256"), str)}
        new = {str(item.get("sha256")): int(item.get("bytes", 0)) for item in descriptors}
        unchanged = sorted(set(old) & set(new))
        added = sorted(set(new) - set(old))
        removed = sorted(set(old) - set(new))
        logical = sum(new.values())
        reused = sum(new[key] for key in unchanged)
        fresh = sum(new[key] for key in added)
        previous_bytes = sum(old.values())
        return {
            "logicalBytes": logical,
            "previousLogicalBytes": previous_bytes,
            "unchangedObjectCount": len(unchanged),
            "changedObjectCount": 0,
            "newObjectCount": len(added),
            "removedReferences": len(removed),
            "reusedBytes": reused,
            "newBytes": fresh,
            "deduplicationPercent": round((reused / logical * 100) if logical else 0.0, 6),
            # Previous release objects stay retained for last-known-good and
            # rollback.  Therefore unique storage is old objects plus only
            # genuinely new content, not merely the new release's bytes.
            "totalUniqueBytesAfter": previous_bytes + fresh,
            "retainedPreviousObjectCount": len(old),
        }

    def _publish(self, source: SourceIdentity, previous: Mapping[str, Any] | None, *, release_id: str) -> dict[str, Any]:
        store = ContentAddressedStore(self.workspace)
        descriptors: list[dict[str, Any]] = []
        reused = new = 0
        uncompressed_sizes = self._uncompressed_size_map()
        with SingleWriterLock(self.workspace / "control" / "publisher.lock"):
            for index, path in enumerate(self._candidate_files()):
                if self.fail_publication_after is not None and index >= self.fail_publication_after:
                    raise DailyReleaseError("injected publication failure", code="PUBLICATION_FAILED")
                digest, size, was_reused = store.put_file(path)
                reused += int(was_reused)
                new += int(not was_reused)
                relative = path.relative_to(self.artifact_root).as_posix()
                descriptors.append({"path": relative, "sha256": digest, "bytes": size, "compressedBytes": size, "uncompressedBytes": uncompressed_sizes.get(relative, size)})
            backend_profile = _read_json(self.artifact_root / "backend-profile.json") if (self.artifact_root / "backend-profile.json").is_file() else {}
            backend_counts = backend_profile.get("counts", {}) if isinstance(backend_profile, Mapping) else {}
            manifest = {"schemaVersion": SCHEMA_VERSION, "policyVersion": POLICY_VERSION, "toolVersion": TOOL_VERSION, "completionState": "COMPLETE", "releaseId": release_id, "source": source.as_dict(), "backendProfile": {"logicalPartitionCount": BACKEND_LOGICAL_PARTITIONS, "physicalPackCount": PHYSICAL_PACKS, "mobileLogicalPartitionCount": MOBILE_LOGICAL_PARTITIONS, "mobilePhysicalPackCount": PHYSICAL_PACKS}, "backendCounts": dict(backend_counts) if isinstance(backend_counts, Mapping) else {}, "objects": descriptors, "qualificationStatus": "FIXTURE_ONLY" if self.fixture else "QUALIFIED", "rawProviderDataCommitted": False, "automatedOfficialAcquisitionProven": False}
            release_dir = store.releases / release_id
            release_dir.mkdir(parents=True, exist_ok=True)
            _write_json(release_dir / "manifest.json", manifest)
            _write_atomic(release_dir / "manifest.sha256", (sha256_file(release_dir / "manifest.json") + "  manifest.json\n").encode("ascii"))
            store.verify_manifest_objects(manifest)
            return {"published": True, "objectCount": len(descriptors), "reusedObjects": reused, "newObjects": new, "manifest": manifest, "publicationOperations": {"objectPutCalls": len(descriptors), "newObjectWrites": new, "existingObjectChecks": reused, "manifestWrites": 2, "manifestObjectVerificationReads": len(descriptors), "activePointerWrites": 0}}

    def _activate(self, release_id: str, *, audit_at: str | None = None) -> dict[str, Any]:
        store = ContentAddressedStore(self.workspace)
        with SingleWriterLock(self.workspace / "control" / "publisher.lock"):
            manifest = store.read_release(release_id)
            store.verify_manifest_objects(manifest)
            old = store.read_active()
            pointer = {"schemaVersion": "valuepilot-active-release-v1", "releaseId": release_id, "previousReleaseId": old.get("releaseId") if old else None, "lastKnownGoodReleaseId": release_id}
            _write_json(store.control / "active.json", pointer)
            return {"activated": True, "activeBefore": old.get("releaseId") if old else None, "activeAfter": release_id, "auditAt": audit_at}

    def run(self, *, audit_at: str | None = None) -> DailyReleaseResult:
        started = time.perf_counter()
        self.output.mkdir(parents=True, exist_ok=True)
        source = inspect_source(self.source_path, fixture=self.fixture)
        store = ContentAddressedStore(self.workspace)
        previous = self._previous_manifest(store)
        second_official = self._source_gate(source, previous)
        release_id = _release_id(source.release_date, source.sha256)
        _write_json(self.output / "candidate.json", {"schemaVersion": SCHEMA_VERSION, "releaseId": release_id, "source": source.as_dict(), "secondOfficialReleaseProven": second_official})
        stage_meta: dict[str, Any] = {}
        upstream: dict[str, str] = {}
        last_stage = "DISCOVERED"

        def execute(stage: str, builder: Any) -> dict[str, Any]:
            nonlocal last_stage, upstream
            stage_started = time.perf_counter()
            identity = self._identity(source, upstream)
            path, reused, metadata = _atomic_stage(self.stage_root, stage, identity, builder, metadata={})
            if reused:
                self.reused_stages.append(stage)
            manifest = _read_json(path / "stage.json")
            upstream = {descriptor["path"]: descriptor["sha256"] for descriptor in manifest.get("outputs", []) if isinstance(descriptor, Mapping)}
            stage_meta[stage] = metadata
            self.timings[stage] = round((time.perf_counter() - stage_started) * 1000, 3)
            last_stage = stage
            if self.fail_after_stage == stage:
                raise DailyReleaseError(f"injected crash after {stage}", code="BUILD_FAILED")
            return metadata

        execute("DISCOVERED", lambda root: self._write_source_stage(root, source))
        execute("STRUCTURALLY_VALIDATED", lambda root: self._validate_structure(root, source))
        qualification = execute("QUALIFIED", lambda root: self._qualify(root, source))
        execute("NORMALIZED", lambda root: self._normalise(root, source, qualification))
        backend = execute("BACKEND_BUILT", lambda root: self._build_backend(root, source))
        execute("INDEXED", lambda root: self._build_indexes(root, source))
        execute("VERIFIED", lambda root: self._verify_candidate(root, source))
        # Materialize verified stage outputs into a stable candidate artifact root.
        if not self.artifact_root.exists():
            self.artifact_root.mkdir(parents=True, exist_ok=True)
        for stage in ("NORMALIZED", "BACKEND_BUILT", "INDEXED"):
            stage_dir = self.stage_root / stage.lower()
            if stage == "BACKEND_BUILT" and not self.fixture:
                # National and query-selective roots are build intermediates;
                # the online release publishes only the verified 1024/32
                # backend root and its profile.
                profile = stage_dir / "backend-profile.json"
                if profile.is_file():
                    target = self.artifact_root / profile.name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(profile, target)
                micro_root = stage_dir / "micro-1024"
                if micro_root.is_dir():
                    _copy_stage_files(micro_root, self.artifact_root / "micro-1024")
            else:
                _copy_stage_files(stage_dir, self.artifact_root)
        publish_info: dict[str, Any] = {"published": False, "activation": "NOT_REQUESTED"}
        if self.activate and self.fixture and not self.allow_fixture_activation:
            raise DailyReleaseError("fixture releases cannot activate production state", code="ACTIVATION_FAILED")
        if self.activate or not self.dry_run:
            if not second_official and not self.allow_fixture_activation:
                raise DailyReleaseError("a newer official release is required before publication/activation", code="SECOND_RELEASE_REQUIRED")
            publish_info = self._publish(source, previous, release_id=release_id)
            if self.activate:
                activation = self._activate(release_id, audit_at=audit_at)
                publish_info.update(activation)
        candidate_descriptors = publish_info.get("manifest", {}).get("objects", []) if publish_info.get("manifest") else [{"sha256": sha256_file(path), "bytes": path.stat().st_size} for path in self._candidate_files()]
        dedup = self._dedup(candidate_descriptors, previous)
        qualification_report, qualification_report_sha = self._read_qualification_report()
        qualification_metrics = _qualification_metrics(qualification_report, source_report_sha256=qualification_report_sha)
        old_qualification_metrics = _baseline_qualification_metrics()
        new_backend_counts = {
            str(key): int(value)
            for key, value in (backend.get("counts", {}) if isinstance(backend, Mapping) else {}).items()
            if _int_metric(value) is not None
        }
        old_backend_counts = self._previous_backend_counts(previous)
        new_release = source.as_dict()
        new_release["nestedPackages"] = self._nested_package_provenance(qualification_report, source)
        def backend_delta(name: str) -> dict[str, Any]:
            return self._count_delta(old_backend_counts.get(name), new_backend_counts.get(name))
        def qualification_delta(name: str) -> dict[str, Any]:
            return self._count_delta(old_qualification_metrics.get(name), qualification_metrics.get(name))
        object_count = len(publish_info.get("manifest", {}).get("objects", [])) if publish_info.get("manifest") else len(candidate_descriptors)
        current_bytes = sum(int(item.get("bytes", 0)) for item in candidate_descriptors if isinstance(item, Mapping))
        operations = publish_info.get("publicationOperations")
        if isinstance(operations, Mapping):
            operations = dict(operations)
            operations["activePointerWrites"] = 1 if publish_info.get("activated") else 0
        else:
            operations = {"status": "NOT_EXECUTED", "writes": None, "reads": None}
        report = {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "startSha": None,
            "candidateSha": None,
            "promotedSha": None,
            "oldRelease": {"date": (previous or {}).get("source", {}).get("releaseDate", BASELINE_RELEASE_DATE), "sha256": (previous or {}).get("source", {}).get("sha256", BASELINE_SOURCE_SHA256), "backendCounts": old_backend_counts, "qualification": old_qualification_metrics},
            "newRelease": new_release,
            "secondOfficialReleaseProven": second_official,
            "sourceStructure": {"nestedPackages": len(source.nested_packages), "zeroBytePackages": sum(1 for item in source.nested_packages if int(item.get("bytes", 0)) == 0), "sourceKind": source.source_kind, "outerZipCrcVerified": source.official, "nestedPackageHashesSource": "M1 qualification report" if qualification_report_sha else "NOT_AVAILABLE"},
            "schemaDrift": stage_meta.get("STRUCTURALLY_VALIDATED", {}).get("schemaDrift", {"classification": "NOT_MEASURED"}),
            "qualification": {"status": stage_meta.get("QUALIFIED", {}).get("status", qualification_report.get("status", "UNKNOWN")), "counts": qualification_metrics, "sourceReportSha256": qualification_report_sha, "rawProviderDataPublished": False},
            "deltas": {
                "stores": backend_delta("stores"),
                "products": backend_delta("productEvidenceRecords"),
                "gtins": qualification_delta("checksumValidUniqueGtins"),
                "offers": backend_delta("offers"),
                "promotions": backend_delta("promotions"),
                "provinces": qualification_delta("provinces"),
                "providers": qualification_delta("commerceIds"),
                "banners": qualification_delta("bannerKeys"),
                "unknownQuantity": qualification_delta("quantityUnknownRows"),
                "quarantine": qualification_delta("quarantineRows"),
                "status": "MEASURED_AGAINST_COMMITTED_2026-09-06_REPORT" if second_official else "NOT_COMPUTED_WITHOUT_REAL_SECOND_RELEASE",
            },
            "backendProfile": {"online": "1024/32", "mobilePreserved": "128/32", "details": backend, "equivalence": "M5/M7 verified contract reused; M6 exact money authority unchanged"},
            "contentAddressed": {"objectPrefix": "objects/sha256/<sha256>", "manifestReferencesObjects": True, "dedup": dedup, "physicalPackDecision": {"chosen": "A", "reason": "Measured M5/M7 physical-pack contract remains the verified backend boundary; no partition study reopened in M9."}},
            "storageEconomics": {"activeReleaseBytes": current_bytes if publish_info.get("published") else None, "previousReleaseBytes": dedup.get("previousLogicalBytes"), "uniqueContentAddressedBytes": dedup.get("totalUniqueBytesAfter"), "newDailyBytesRequired": dedup.get("newBytes"), "objectCount": object_count, "publicationOperations": operations},
            "timings": {"stagesMs": self.timings, "totalMs": round((time.perf_counter() - started) * 1000, 3), "measurement": "full new official release run; resumed reruns do not remeasure source-wide work"},
            "resumability": {"stageManifests": True, "identityIncludesSourceHashPolicyToolUpstreamAndConfig": True, "codeIdentity": TOOL_VERSION, "reusedStages": list(self.reused_stages)},
            "activation": publish_info,
            "rollback": {"implemented": True, "tested": False, "testEvidence": "tools.tests.test_argentina_daily_release_operations"},
            "failureInjection": {"implemented": True, "tested": False, "testEvidence": "tools.tests.test_argentina_daily_release_operations"},
            "freshness": {"staleAfterDays": STALE_AFTER_DAYS, "oldReleaseIsNeverMadeCurrent": True, "availability": "UNKNOWN"},
            "m6Regression": "PENDING_BOUNDED_CROSS_RELEASE_RUN" if second_official else "PRESERVED_NOT_REMEASURED",
            "m7Regression": "PENDING_BOUNDED_CROSS_RELEASE_RUN" if second_official else "PRESERVED_NOT_REMEASURED",
            "m8Regression": "INDEX_REBUILT_FIXTURE" if self.fixture else "REBUILT_FROM_QUALIFIED_SEARCH_INDEX",
            "automatedOfficialAcquisitionProven": False,
            "liveCloudDeploymentVerified": False,
            "androidNetworkingAuthorized": False,
            "productionAndroidUiAuthorized": False,
            "dailyReleasePipelineQualified": bool(second_official and publish_info.get("activated")),
            "status": "FIXTURE_ORCHESTRATION_ONLY" if self.fixture else ("QUALIFIED_AND_ACTIVATED" if publish_info.get("activated") else "CANDIDATE_VERIFIED"),
        }
        _write_json(self.output / "release-report.json", report)
        _write_atomic(self.output / "release-report.sha256", (sha256_file(self.output / "release-report.json") + "  release-report.json\n").encode("ascii"))
        return DailyReleaseResult(release_id, source, self.output, last_stage, report, tuple(self.reused_stages))

    @staticmethod
    def rollback(workspace: Path, release_id: str, *, audit_at: str) -> dict[str, Any]:
        store = ContentAddressedStore(Path(workspace))
        with SingleWriterLock(store.control / "publisher.lock"):
            manifest = store.read_release(release_id)
            store.verify_manifest_objects(manifest)
            old = store.read_active()
            pointer = {"schemaVersion": "valuepilot-active-release-v1", "releaseId": release_id, "previousReleaseId": old.get("releaseId") if old else None, "lastKnownGoodReleaseId": release_id}
            _write_json(store.control / "active.json", pointer)
            audit = {"schemaVersion": "valuepilot-release-rollback-v1", "auditAt": audit_at, "fromReleaseId": old.get("releaseId") if old else None, "toReleaseId": release_id, "verifiedBeforePointer": True}
            _write_json(store.control / f"rollback-{audit_at.replace(':', '').replace('-', '')}.json", audit)
            return {"rolledBack": True, "activeBefore": old.get("releaseId") if old else None, "activeAfter": release_id, "audit": audit}


def markdown_report(report: Mapping[str, Any]) -> str:
    source = report.get("newRelease", {})
    dedup = report.get("contentAddressed", {}).get("dedup", {})
    lines = [
        "# Argentina daily release operations V1",
        "",
        f"Status: `{report.get('status')}`",
        "",
        f"- Starting SHA: `{report.get('startSha') or 'not supplied'}`",
        f"- Old release: `{report.get('oldRelease', {}).get('date')}` / `{report.get('oldRelease', {}).get('sha256')}`",
        f"- New source: `{source.get('releaseDate')}` / `{source.get('sha256')}`",
        f"- SECOND_OFFICIAL_RELEASE_PROVEN: **{str(report.get('secondOfficialReleaseProven')).lower()}**",
        f"- DAILY_RELEASE_PIPELINE_QUALIFIED: **{str(report.get('dailyReleasePipelineQualified')).lower()}**",
        f"- AUTOMATED_OFFICIAL_ACQUISITION_PROVEN: **{str(report.get('automatedOfficialAcquisitionProven')).lower()}**",
        "",
        "## Source and safety",
        "",
        f"- Structure: {report.get('sourceStructure')}",
        f"- Schema drift: {report.get('schemaDrift')}",
        "- Availability remains UNKNOWN; promotions retain explicit conditions and unknown eligibility.",
        "- Android networking, live cloud deployment and production Android UI remain unauthorized.",
        "",
        "## Backend and content-addressed publication",
        "",
        "- Online profile: 1024 logical partitions / 32 physical packs.",
        "- Existing offline/mobile 128 / 32 contract is preserved and not rebuilt daily.",
        f"- Reused bytes: {dedup.get('reusedBytes')}; new bytes: {dedup.get('newBytes')}; deduplication: {dedup.get('deduplicationPercent')}%.",
        f"- Resumability: {report.get('resumability')}",
        f"- Activation: {report.get('activation')}",
        "",
        "## Verification boundary",
        "",
        f"- M6 regression: `{report.get('m6Regression')}`",
        f"- M7 regression: `{report.get('m7Regression')}`",
        f"- M8 regression: `{report.get('m8Regression')}`",
        "- This report does not claim a real daily refresh unless a newer official source passed the strict second-release gate.",
        "",
    ]
    return "\n".join(lines)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--workspace", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--fixture", action="store_true", help="allow only an explicit deterministic JSON fixture; never activates production")
    parser.add_argument("--previous-release", type=Path)
    parser.add_argument("--activate", action="store_true")
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--audit-at", default="operator-unspecified")
    parser.add_argument("--operator-acquired-at")
    parser.add_argument("--fail-after-stage", choices=STAGES)
    parser.add_argument("--fail-publication-after", type=int, help="test-only publication failure injection; never use for normal activation")
    args = parser.parse_args(argv)
    try:
        result = DailyReleaseOrchestrator(source_path=args.input, workspace=args.workspace, output=args.output, fixture=args.fixture, previous_release=args.previous_release, activate=args.activate, dry_run=not args.publish and not args.activate, operator_acquired_at=args.operator_acquired_at, fail_after_stage=args.fail_after_stage, fail_publication_after=args.fail_publication_after).run(audit_at=args.audit_at)
        _write_atomic(args.output / "release-report.md", markdown_report(result.report).encode("utf-8"))
        summary = {"source": result.source.as_dict(), "releaseId": result.release_id, "lastStage": result.last_stage, "secondOfficialReleaseProven": result.report["secondOfficialReleaseProven"], "qualification": result.report.get("qualification", {}).get("status"), "offers": result.report.get("backendProfile", {}).get("details", {}).get("counts", {}).get("offers"), "stores": result.report.get("backendProfile", {}).get("details", {}).get("counts", {}).get("stores"), "products": result.report.get("backendProfile", {}).get("details", {}).get("counts", {}).get("productEvidenceRecords"), "quarantine": result.report.get("qualification", {}).get("counts", {}).get("quarantineRows"), "backendBytes": result.report.get("contentAddressed", {}).get("dedup", {}).get("logicalBytes"), "reusedBytes": result.report.get("contentAddressed", {}).get("dedup", {}).get("reusedBytes"), "newBytes": result.report.get("contentAddressed", {}).get("dedup", {}).get("newBytes"), "dedupPercent": result.report.get("contentAddressed", {}).get("dedup", {}).get("deduplicationPercent"), "activeBefore": result.report.get("activation", {}).get("activeBefore"), "activeAfter": result.report.get("activation", {}).get("activeAfter"), "status": result.report["status"], "reusedStages": list(result.reused_stages), "report": str(args.output / "release-report.json")}
        print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
        return 0
    except DailyReleaseError as exc:
        print(json.dumps({"error": exc.code, "message": str(exc)}, ensure_ascii=False, sort_keys=True), file=sys.stderr)
        return exc.exit_code


if __name__ == "__main__":
    raise SystemExit(main())

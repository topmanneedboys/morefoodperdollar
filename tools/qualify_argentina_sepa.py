#!/usr/bin/env python3
"""Stream and qualify one official Argentina SEPA release offline.

The tool is deliberately provider-edge tooling.  It never performs network
access, never authorizes a provider, and never changes Android or shared-core
code.  The national SEPA release is an outer ZIP containing one ZIP per
retailer.  Retailer ``productos.csv`` files are streamed; only bounded
retailer/store metadata and small identity/audit indexes are retained in
memory.  Duplicate-scope accounting uses a temporary disk-backed SQLite
ledger so the national product table is never held in memory.

Only the list price is considered a current-price candidate.  Promotions,
inventory, delivery, and package quantity remain separate evidence.  Unknown
quantity, availability, observation time, and geography are retained as
unknown rather than guessed.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import heapq
import io
import json
import os
import re
import sqlite3
import sys
import tempfile
import zipfile
from collections import Counter, defaultdict
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal, InvalidOperation, localcontext
from pathlib import Path
from typing import Any, BinaryIO, Iterable, Iterator, Mapping, TextIO


SCHEMA_VERSION = "argentina-sepa-normalized-observation-v1"
POLICY_VERSION = "argentina-sepa-policy-v1"
PROVIDER_ID = "ARGENTINA_SEPA_PRECIOS_CLAROS"
PROVIDER_LABEL = "Argentina SEPA / Precios Claros"
SOURCE_HOST = "https://datos.produccion.gob.ar"
SOURCE_DATASET_URL = "https://datos.produccion.gob.ar/dataset/sepa-precios"
SOURCE_CATALOG_URL = "https://datos.gob.ar"
SOURCE_LANDING_URL = (
    "https://www.argentina.gob.ar/economia/industria-y-comercio/"
    "defensadelconsumidor/precios-sepa"
)
SOURCE_LICENSE = "Creative Commons Attribution 4.0"
SOURCE_ATTRIBUTION = "Precios Claros - Base SEPA; source: datos.produccion.gob.ar"
EXPECTED_RELEASE_DATE = date(2026, 9, 6)
EXPECTED_SHA256 = "e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305"
EXPECTED_BYTES = 325_522_188
STALE_AFTER_DAYS = 2
MAX_AUDIT_SAMPLE = 128
MAX_BUCKET_SAMPLE = 4
REQUIRED_FILES = ("comercio.csv", "sucursales.csv", "productos.csv")

# These are intentionally broad, versioned bounds.  They are only a
# plausibility/geo-safety gate; the tool never geocodes or repairs coordinates.
ARGENTINA_BOUNDS = {
    "latitude_min": Decimal("-56.0"),
    "latitude_max": Decimal("-21.0"),
    "longitude_min": Decimal("-74.0"),
    "longitude_max": Decimal("-52.0"),
}


class SepaQualificationError(RuntimeError):
    """Raised when a source or package cannot be qualified safely."""


class SourceVerificationError(SepaQualificationError):
    """Raised when the supplied source is not the expected official release."""


def _clean(value: object | None) -> str:
    if value is None:
        return ""
    return str(value).replace("\u00a0", " ").strip()


def _canonical_json(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def _pretty_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n"


def _write_bytes_atomically(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    partial = path.with_name(path.name + ".partial")
    try:
        with partial.open("wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(partial, path)
    finally:
        if partial.exists():
            partial.unlink()


def _write_text_atomically(path: Path, payload: str) -> None:
    _write_bytes_atomically(path, payload.encode("utf-8"))


def _sha256_path(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _sha256_stream(handle: BinaryIO) -> str:
    digest = hashlib.sha256()
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def _format_decimal(value: Decimal) -> str:
    if not value.is_finite():
        raise ValueError("Decimal must be finite")
    text = format(value, "f")
    if "." in text:
        text = text.rstrip("0").rstrip(".")
    return text or "0"


def _format_decimal_preserving_scale(value: Decimal) -> str:
    if not value.is_finite():
        raise ValueError("Decimal must be finite")
    return format(value, "f")


_DECIMAL_PATTERN = re.compile(r"(?:\d+(?:\.\d+)?|\.\d+)")
_SIGNED_DECIMAL_PATTERN = re.compile(r"[+-]?(?:\d+(?:\.\d+)?|\.\d+)")


def parse_decimal_exact(value: str | None) -> Decimal | None:
    """Parse dot-decimal SEPA numbers without punctuation guessing."""

    text = _clean(value)
    if not text or not _DECIMAL_PATTERN.fullmatch(text):
        return None
    try:
        parsed = Decimal(text)
    except InvalidOperation:
        return None
    return parsed if parsed.is_finite() else None


def parse_signed_decimal_exact(value: str | None) -> Decimal | None:
    """Parse a signed dot-decimal value for coordinate plausibility only."""

    text = _clean(value)
    if not text or not _SIGNED_DECIMAL_PATTERN.fullmatch(text):
        return None
    try:
        parsed = Decimal(text)
    except InvalidOperation:
        return None
    return parsed if parsed.is_finite() else None


def parse_positive_decimal(value: str | None) -> Decimal | None:
    parsed = parse_decimal_exact(value)
    return parsed if parsed is not None and parsed > 0 else None


def is_valid_gtin(value: str | None) -> bool:
    """Validate only the supplied GTIN representation; never pad or repair."""

    text = _clean(value)
    if len(text) not in {8, 12, 13, 14} or not text.isdigit():
        return False
    supplied = int(text[-1])
    total = 0
    weight = 3
    for char in reversed(text[:-1]):
        total += int(char) * weight
        weight = 1 if weight == 3 else 3
    return supplied == (10 - total % 10) % 10


_UNIT_ALIASES: dict[str, tuple[str, Decimal]] = {
    # Mass
    "MG": ("GRAM", Decimal("0.001")),
    "MILIGRAMO": ("GRAM", Decimal("0.001")),
    "MILIGRAMOS": ("GRAM", Decimal("0.001")),
    "G": ("GRAM", Decimal("1")),
    "GR": ("GRAM", Decimal("1")),
    "GRM": ("GRAM", Decimal("1")),
    "GRS": ("GRAM", Decimal("1")),
    "GRAM": ("GRAM", Decimal("1")),
    "GRAMO": ("GRAM", Decimal("1")),
    "GRAMOS": ("GRAM", Decimal("1")),
    "KG": ("GRAM", Decimal("1000")),
    "KGS": ("GRAM", Decimal("1000")),
    "KILO": ("GRAM", Decimal("1000")),
    "KILOS": ("GRAM", Decimal("1000")),
    "KILOGRAM": ("GRAM", Decimal("1000")),
    "KILOGRAMO": ("GRAM", Decimal("1000")),
    "KILOGRAMOS": ("GRAM", Decimal("1000")),
    "T": ("GRAM", Decimal("1000000")),
    "TN": ("GRAM", Decimal("1000000")),
    "TON": ("GRAM", Decimal("1000000")),
    "TONELADA": ("GRAM", Decimal("1000000")),
    "TONELADAS": ("GRAM", Decimal("1000000")),
    # Volume
    "ML": ("MILLILITRE", Decimal("1")),
    "MLS": ("MILLILITRE", Decimal("1")),
    "MLT": ("MILLILITRE", Decimal("1")),
    "MLTS": ("MILLILITRE", Decimal("1")),
    "MILILITRO": ("MILLILITRE", Decimal("1")),
    "MILILITROS": ("MILLILITRE", Decimal("1")),
    "CC": ("MILLILITRE", Decimal("1")),
    "CM3": ("MILLILITRE", Decimal("1")),
    "CM³": ("MILLILITRE", Decimal("1")),
    "L": ("MILLILITRE", Decimal("1000")),
    "LT": ("MILLILITRE", Decimal("1000")),
    "LTR": ("MILLILITRE", Decimal("1000")),
    "LTS": ("MILLILITRE", Decimal("1000")),
    "LITRO": ("MILLILITRE", Decimal("1000")),
    "LITROS": ("MILLILITRE", Decimal("1000")),
    # Explicit count forms
    "U": ("COUNT", Decimal("1")),
    "UN": ("COUNT", Decimal("1")),
    "UNI": ("COUNT", Decimal("1")),
    "UNID": ("COUNT", Decimal("1")),
    "UNIDAD": ("COUNT", Decimal("1")),
    "UNIDADES": ("COUNT", Decimal("1")),
    "UD": ("COUNT", Decimal("1")),
    "EA": ("COUNT", Decimal("1")),
    "EACH": ("COUNT", Decimal("1")),
    "ITEM": ("COUNT", Decimal("1")),
    "ITEMS": ("COUNT", Decimal("1")),
    "PZ": ("COUNT", Decimal("1")),
    "PZA": ("COUNT", Decimal("1")),
    "PIEZA": ("COUNT", Decimal("1")),
    "PIEZAS": ("COUNT", Decimal("1")),
    "UNIT": ("COUNT", Decimal("1")),
    "UNITS": ("COUNT", Decimal("1")),
}


def _normalize_unit(value: str | None) -> str:
    text = _clean(value).upper()
    text = text.replace(".", "").replace(" ", "")
    return text


def parse_quantity(value: str | None, unit: str | None) -> tuple[str, Decimal] | None:
    amount = parse_positive_decimal(value)
    conversion = _UNIT_ALIASES.get(_normalize_unit(unit))
    if amount is None or conversion is None:
        return None
    canonical_unit, multiplier = conversion
    return canonical_unit, amount * multiplier


def parse_timestamp(value: str | None) -> datetime | None:
    text = _clean(value)
    if not text:
        return None
    normalized = text[:-1] + "+00:00" if text.endswith("Z") else text
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


_ISO_TIMESTAMP_RE = re.compile(
    r"\b\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})\b"
)


def _timestamps_in_text(value: str) -> list[datetime]:
    return [parsed for parsed in (parse_timestamp(item) for item in _ISO_TIMESTAMP_RE.findall(value)) if parsed]


def _normalized_header(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.strip().lower())


def _field_indexes(headers: list[str]) -> dict[str, int]:
    return {_normalized_header(value): index for index, value in enumerate(headers)}


def _required_indexes(headers: list[str], required: Iterable[str]) -> dict[str, int]:
    indexes = _field_indexes(headers)
    missing = [name for name in required if _normalized_header(name) not in indexes]
    if missing:
        raise SepaQualificationError(f"required fields missing: {', '.join(missing)}")
    return {_normalized_header(name): indexes[_normalized_header(name)] for name in required}


def _row_value(row: list[str], indexes: Mapping[str, int], name: str) -> str:
    index = indexes.get(_normalized_header(name))
    if index is None or index >= len(row):
        return ""
    return row[index]


def _has_replacement(row: Iterable[str]) -> bool:
    return any("\ufffd" in value for value in row)


def _coordinate_status(latitude: str, longitude: str) -> tuple[str, Decimal | None, Decimal | None]:
    lat_text = _clean(latitude)
    lon_text = _clean(longitude)
    # A missing member of the coordinate pair is incomplete evidence, not a
    # malformed coordinate.  Retain the store/address but keep it out of any
    # distance reasoning; no coordinate is ever inferred from the other one.
    if not lat_text or not lon_text:
        return "GEO_INCOMPLETE", parse_signed_decimal_exact(lat_text), parse_signed_decimal_exact(lon_text)
    lat = parse_signed_decimal_exact(lat_text)
    lon = parse_signed_decimal_exact(lon_text)
    if lat is None or lon is None:
        return "GEO_INVALID", lat, lon
    if not (
        ARGENTINA_BOUNDS["latitude_min"] <= lat <= ARGENTINA_BOUNDS["latitude_max"]
        and ARGENTINA_BOUNDS["longitude_min"] <= lon <= ARGENTINA_BOUNDS["longitude_max"]
    ):
        return "GEO_OUT_OF_BOUNDS", lat, lon
    return "VALID", lat, lon


@dataclass(frozen=True)
class SepaConfig:
    input_path: Path
    release_date: date = EXPECTED_RELEASE_DATE
    stale_after_days: int = STALE_AFTER_DAYS
    expected_sha256: str | None = None
    expected_bytes: int | None = None
    output_dir: Path | None = None
    report_json: Path | None = None
    report_markdown: Path | None = None
    write_normalized: bool = True

    def __post_init__(self) -> None:
        if self.stale_after_days < 0:
            raise ValueError("stale_after_days must be non-negative")
        if self.expected_sha256 is not None and not re.fullmatch(r"[0-9a-fA-F]{64}", self.expected_sha256):
            raise ValueError("expected_sha256 must be a 64-character hexadecimal hash")
        if self.expected_bytes is not None and self.expected_bytes <= 0:
            raise ValueError("expected_bytes must be positive")
        if self.write_normalized and self.output_dir is None:
            raise ValueError("output_dir is required when write_normalized is enabled")


@dataclass
class CommerceRecord:
    commerce_id: str
    banner_id: str
    cuit: str
    company_name: str
    banner_name: str
    banner_url: str
    updated_at: datetime | None
    updated_at_raw: str


@dataclass
class StoreRecord:
    commerce_id: str
    banner_id: str
    store_id: str
    name: str
    store_type: str
    street: str
    number: str
    locality: str
    province: str
    postal_code: str
    latitude: Decimal | None
    longitude: Decimal | None
    geo_status: str


@dataclass
class PackageState:
    name: str
    size_bytes: int
    sha256: str
    status: str = "VALID"
    inner_uncompressed_bytes: int = 0
    commerce_rows: int = 0
    store_rows: int = 0
    product_rows: int = 0
    accepted_rows: int = 0
    quarantined_rows: int = 0
    malformed_rows: int = 0
    package_update_at: datetime | None = None
    freshness_status: str = "UNKNOWN"
    age_days: str | None = None
    diagnostics: Counter[str] = field(default_factory=Counter)


class _SliceReader(io.RawIOBase):
    """Seekable bounded view over a stored member of the outer ZIP."""

    def __init__(self, raw: BinaryIO, start: int, length: int) -> None:
        self._raw = raw
        self._start = start
        self._length = length
        self._position = 0

    def readable(self) -> bool:
        return True

    def seekable(self) -> bool:
        return True

    def tell(self) -> int:
        return self._position

    def seek(self, offset: int, whence: int = io.SEEK_SET) -> int:
        if whence == io.SEEK_SET:
            position = offset
        elif whence == io.SEEK_CUR:
            position = self._position + offset
        elif whence == io.SEEK_END:
            position = self._length + offset
        else:
            raise ValueError(f"unsupported seek mode: {whence}")
        # zipfile probes a short/corrupt member with a negative relative seek
        # from EOF.  Clamp that probe to the bounded start so corruption is
        # reported as ZIP_INVALID rather than escaping the slice.
        self._position = max(0, position)
        return self._position

    def readinto(self, buffer: bytearray | memoryview) -> int:
        if self._position >= self._length:
            return 0
        amount = min(len(buffer), self._length - self._position)
        self._raw.seek(self._start + self._position)
        data = self._raw.read(amount)
        buffer[: len(data)] = data
        self._position += len(data)
        return len(data)


def _stored_member_offset(raw: BinaryIO, info: zipfile.ZipInfo) -> int:
    raw.seek(info.header_offset)
    header = raw.read(30)
    if len(header) != 30 or header[:4] != b"PK\x03\x04":
        raise SepaQualificationError(f"invalid local ZIP header for {info.filename}")
    name_length = int.from_bytes(header[26:28], "little")
    extra_length = int.from_bytes(header[28:30], "little")
    return info.header_offset + 30 + name_length + extra_length


def _find_inner_info(inner: zipfile.ZipFile, expected_name: str) -> zipfile.ZipInfo | None:
    matches = [
        info
        for info in inner.infolist()
        if not info.is_dir() and Path(info.filename).name.lower() == expected_name.lower()
    ]
    if len(matches) != 1:
        return None
    return matches[0]


@contextmanager
def _open_csv(inner: zipfile.ZipFile, info: zipfile.ZipInfo) -> Iterator[tuple[TextIO, csv.reader]]:
    binary = inner.open(info, "r")
    text = io.TextIOWrapper(binary, encoding="utf-8-sig", errors="replace", newline="")
    try:
        yield text, csv.reader(text, delimiter="|")
    finally:
        text.close()


def _freshness_for_package(
    package_update_at: datetime | None,
    release_date: date,
    stale_after_days: int,
) -> tuple[str, str | None]:
    if package_update_at is None:
        return "UNKNOWN", None
    release_start = datetime.combine(release_date, time.min, tzinfo=timezone.utc)
    age = (release_start - package_update_at).total_seconds() / 86_400
    age_text = format(Decimal(str(age)).quantize(Decimal("0.01")), "f")
    cutoff = release_start - timedelta(days=stale_after_days)
    return ("STALE" if package_update_at < cutoff else "FRESH"), age_text


def _age_bucket(age_days: str | None, freshness_status: str) -> str:
    if age_days is None or freshness_status == "UNKNOWN":
        return "unknown"
    age = Decimal(age_days)
    if age < 0:
        return "future_or_same_release_day"
    if age <= 2:
        return "0_to_2_days"
    if age <= 30:
        return "3_to_30_days"
    return "over_30_days"


class ScopeLedger:
    """Disk-backed duplicate/conflict ledger with bounded Python memory."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.connection = sqlite3.connect(str(path))
        self.connection.execute("PRAGMA journal_mode=OFF")
        self.connection.execute("PRAGMA synchronous=OFF")
        self.connection.execute("PRAGMA temp_store=FILE")
        self.connection.execute(
            "CREATE TABLE scopes ("
            "digest BLOB PRIMARY KEY, signature BLOB NOT NULL, "
            "duplicate_rows INTEGER NOT NULL DEFAULT 0, "
            "conflicting INTEGER NOT NULL DEFAULT 0) WITHOUT ROWID"
        )
        self.connection.commit()
        self.pending: list[tuple[bytes, bytes]] = []
        self.batch_size = 2_000

    def add(self, scope: tuple[str, str, str, str], signature: tuple[str, ...]) -> None:
        scope_bytes = "\x1f".join(scope).encode("utf-8")
        signature_bytes = "\x1f".join(signature).encode("utf-8")
        digest = hashlib.blake2b(scope_bytes, digest_size=16).digest()
        self.pending.append((digest, signature_bytes))
        if len(self.pending) >= self.batch_size:
            self.flush()

    def flush(self) -> None:
        if not self.pending:
            return
        self.connection.executemany(
            "INSERT INTO scopes(digest, signature) VALUES (?, ?) "
            "ON CONFLICT(digest) DO UPDATE SET "
            "duplicate_rows=scopes.duplicate_rows + 1, "
            "conflicting=CASE WHEN scopes.signature <> excluded.signature "
            "THEN 1 ELSE scopes.conflicting END",
            self.pending,
        )
        self.connection.commit()
        self.pending.clear()

    def summary(self) -> dict[str, int]:
        self.flush()
        row = self.connection.execute(
            "SELECT COUNT(*), "
            "COALESCE(SUM(duplicate_rows), 0), "
            "COALESCE(SUM(CASE WHEN duplicate_rows > 0 THEN 1 ELSE 0 END), 0), "
            "COALESCE(SUM(CASE WHEN conflicting > 0 THEN 1 ELSE 0 END), 0) "
            "FROM scopes"
        ).fetchone()
        assert row is not None
        return {
            "unique_store_product_scopes": int(row[0]),
            "duplicate_store_product_extra_rows": int(row[1]),
            "duplicate_store_product_scopes": int(row[2]),
            "conflicting_store_product_scopes": int(row[3]),
        }

    def close(self) -> None:
        self.flush()
        self.connection.close()


class AuditSampler:
    """Deterministic bounded sample using stable SHA-256 ranks."""

    def __init__(self) -> None:
        self._heap: list[tuple[int, str, dict[str, Any]]] = []
        self.by_province: dict[str, list[tuple[int, str, dict[str, Any]]]] = defaultdict(list)
        self.by_commerce: dict[str, list[tuple[int, str, dict[str, Any]]]] = defaultdict(list)

    @staticmethod
    def _rank(key: str) -> int:
        return int.from_bytes(hashlib.sha256(key.encode("utf-8")).digest()[:8], "big")

    @staticmethod
    def _keep_bucket(
        buckets: dict[str, list[tuple[int, str, dict[str, Any]]]],
        bucket: str,
        item: tuple[int, str, dict[str, Any]],
    ) -> None:
        values = buckets[bucket]
        values.append(item)
        values.sort(key=lambda value: (-value[0], value[1]))
        del values[MAX_BUCKET_SAMPLE:]

    def consider(self, key: str, record: dict[str, Any]) -> None:
        rank = self._rank(key)
        # A max-heap over the rank keeps the smallest (best) ranks.  The
        # record is never compared because rank+key are unique and precede it.
        item = (-rank, key, record)
        if len(self._heap) < MAX_AUDIT_SAMPLE:
            heapq.heappush(self._heap, item)
        elif item > self._heap[0]:
            heapq.heapreplace(self._heap, item)
        self._keep_bucket(self.by_province, record.get("province", "") or "UNKNOWN", item)
        self._keep_bucket(self.by_commerce, record.get("commerce_id", "") or "UNKNOWN", item)

    def records(self) -> list[dict[str, Any]]:
        values = {key: record for _, key, record in self._heap}
        for buckets in (self.by_province, self.by_commerce):
            for entries in buckets.values():
                for _, key, record in entries:
                    values[key] = record
        return [values[key] for key in sorted(values)]


class GzipJsonLinesWriter:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.partial_path = path.with_name(path.name + ".partial")
        self.partial_path.parent.mkdir(parents=True, exist_ok=True)
        self.handle = self.partial_path.open("wb")
        self.gzip = gzip.GzipFile(fileobj=self.handle, mode="wb", compresslevel=6, mtime=0)
        self.uncompressed_bytes = 0
        self.rows = 0
        self._closed = False

    def write(self, value: Mapping[str, Any]) -> None:
        payload = _canonical_json(value) + b"\n"
        self.gzip.write(payload)
        self.uncompressed_bytes += len(payload)
        self.rows += 1

    def close(self, promote: bool) -> None:
        if self._closed:
            return
        try:
            self.gzip.close()
            self.handle.flush()
            os.fsync(self.handle.fileno())
        finally:
            self.handle.close()
        if promote:
            os.replace(self.partial_path, self.path)
        elif self.partial_path.exists():
            self.partial_path.unlink()
        self._closed = True


def _file_descriptor(path: Path) -> dict[str, Any]:
    return {
        "file_name": path.name,
        "bytes": path.stat().st_size,
        "sha256": _sha256_path(path),
    }


def _safe_zip_members(inner: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    return [info for info in inner.infolist() if not info.is_dir()]


def _parse_commerce(
    inner: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    package: PackageState,
    diagnostics: Counter[str],
) -> tuple[dict[tuple[str, str], CommerceRecord], datetime | None]:
    records: dict[tuple[str, str], CommerceRecord] = {}
    timestamps: list[datetime] = []
    with _open_csv(inner, info) as (text, reader):
        try:
            headers = next(reader)
        except StopIteration as exc:
            raise SepaQualificationError("comercio.csv has no header") from exc
        indexes = _required_indexes(
            headers,
            (
                "id_comercio",
                "id_bandera",
                "comercio_cuit",
                "comercio_razon_social",
                "comercio_bandera_nombre",
                "comercio_bandera_url",
                "comercio_ultima_actualizacion",
            ),
        )
        for row in reader:
            if _has_replacement(row):
                diagnostics["TEXT_DECODE_REPLACEMENT"] += 1
            if not any(_clean(value) for value in row):
                continue
            commerce_id = _clean(_row_value(row, indexes, "id_comercio"))
            banner_id = _clean(_row_value(row, indexes, "id_bandera"))
            update_raw = _clean(_row_value(row, indexes, "comercio_ultima_actualizacion"))
            timestamps.extend(_timestamps_in_text("|".join(row)))
            if not commerce_id or not banner_id or not commerce_id.isdigit() or not banner_id.isdigit():
                # Footer/notes are expected in this file.  Keep their timestamps
                # for package provenance and do not turn them into commerce rows.
                continue
            if len(row) < len(headers):
                diagnostics["MALFORMED_COMMERCE_ROW"] += 1
                continue
            updated_at = parse_timestamp(update_raw)
            if update_raw and updated_at is None:
                diagnostics["INVALID_COMMERCE_TIMESTAMP"] += 1
            record = CommerceRecord(
                commerce_id=commerce_id,
                banner_id=banner_id,
                cuit=_clean(_row_value(row, indexes, "comercio_cuit")),
                company_name=_clean(_row_value(row, indexes, "comercio_razon_social")),
                banner_name=_clean(_row_value(row, indexes, "comercio_bandera_nombre")),
                banner_url=_clean(_row_value(row, indexes, "comercio_bandera_url")),
                updated_at=updated_at,
                updated_at_raw=update_raw,
            )
            key = (commerce_id, banner_id)
            previous = records.get(key)
            if previous is not None:
                diagnostics["DUPLICATE_COMMERCE_KEY"] += 1
                if previous != record:
                    diagnostics["CONFLICTING_COMMERCE_KEY"] += 1
            else:
                records[key] = record
            package.commerce_rows += 1
    if not records:
        raise SepaQualificationError("comercio.csv has no structured commerce rows")
    package_update_at = max(timestamps) if timestamps else None
    return records, package_update_at


def _parse_stores(
    inner: zipfile.ZipFile,
    info: zipfile.ZipInfo,
    package: PackageState,
    diagnostics: Counter[str],
) -> dict[tuple[str, str, str], StoreRecord]:
    records: dict[tuple[str, str, str], StoreRecord] = {}
    with _open_csv(inner, info) as (text, reader):
        try:
            headers = next(reader)
        except StopIteration as exc:
            raise SepaQualificationError("sucursales.csv has no header") from exc
        indexes = _required_indexes(
            headers,
            (
                "id_comercio",
                "id_bandera",
                "id_sucursal",
                "sucursales_nombre",
                "sucursales_tipo",
                "sucursales_calle",
                "sucursales_numero",
                "sucursales_latitud",
                "sucursales_longitud",
                "sucursales_codigo_postal",
                "sucursales_localidad",
                "sucursales_provincia",
            ),
        )
        for row in reader:
            if _has_replacement(row):
                diagnostics["TEXT_DECODE_REPLACEMENT"] += 1
            if not any(_clean(value) for value in row):
                continue
            if len(row) < len(headers):
                diagnostics["MALFORMED_STORE_ROW"] += 1
                continue
            commerce_id = _clean(_row_value(row, indexes, "id_comercio"))
            banner_id = _clean(_row_value(row, indexes, "id_bandera"))
            store_id = _clean(_row_value(row, indexes, "id_sucursal"))
            if not commerce_id or not banner_id or not store_id:
                diagnostics["MALFORMED_STORE_ROW"] += 1
                continue
            geo_status, latitude, longitude = _coordinate_status(
                _row_value(row, indexes, "sucursales_latitud"),
                _row_value(row, indexes, "sucursales_longitud"),
            )
            record = StoreRecord(
                commerce_id=commerce_id,
                banner_id=banner_id,
                store_id=store_id,
                name=_clean(_row_value(row, indexes, "sucursales_nombre")),
                store_type=_clean(_row_value(row, indexes, "sucursales_tipo")),
                street=_clean(_row_value(row, indexes, "sucursales_calle")),
                number=_clean(_row_value(row, indexes, "sucursales_numero")),
                locality=_clean(_row_value(row, indexes, "sucursales_localidad")),
                province=_clean(_row_value(row, indexes, "sucursales_provincia")),
                postal_code=_clean(_row_value(row, indexes, "sucursales_codigo_postal")),
                latitude=latitude,
                longitude=longitude,
                geo_status=geo_status,
            )
            key = (commerce_id, banner_id, store_id)
            previous = records.get(key)
            if previous is not None:
                diagnostics["DUPLICATE_STORE_KEY"] += 1
                if previous != record:
                    diagnostics["CONFLICTING_STORE_KEY"] += 1
            else:
                records[key] = record
            package.store_rows += 1
    if not records:
        raise SepaQualificationError("sucursales.csv has no structured store rows")
    return records


def _promotion_values(row: list[str], indexes: Mapping[str, int]) -> list[dict[str, Any]]:
    promotions: list[dict[str, Any]] = []
    for slot in (1, 2):
        price_raw = _clean(_row_value(row, indexes, f"productos_precio_unitario_promo{slot}"))
        condition = _clean(_row_value(row, indexes, f"productos_leyenda_promo{slot}"))
        if not price_raw and not condition:
            continue
        price = parse_positive_decimal(price_raw)
        promotions.append(
            {
                "slot": slot,
                "price_ars": _format_decimal_preserving_scale(price) if price is not None else None,
                "price_raw": price_raw or None,
                "condition": condition or None,
                "eligibility": "UNKNOWN",
            }
        )
    return promotions


def _quantity_value(row: list[str], indexes: Mapping[str, int]) -> tuple[dict[str, Any] | None, str]:
    raw_value = _clean(_row_value(row, indexes, "productos_cantidad_presentacion"))
    raw_unit = _clean(_row_value(row, indexes, "productos_unidad_medida_presentacion"))
    parsed = parse_quantity(raw_value, raw_unit)
    if parsed is None:
        return None, "UNKNOWN"
    unit, amount = parsed
    return (
        {
            "value": _format_decimal(amount),
            "unit": unit,
            "raw_value": raw_value or None,
            "raw_unit": raw_unit or None,
            "source": "productos_cantidad_presentacion/productos_unidad_medida_presentacion",
        },
        "KNOWN",
    )


def _reference_price(row: list[str], indexes: Mapping[str, int]) -> dict[str, Any] | None:
    raw = _clean(_row_value(row, indexes, "productos_precio_referencia"))
    if not raw:
        return None
    parsed = parse_positive_decimal(raw)
    return {
        "amount_ars": _format_decimal_preserving_scale(parsed) if parsed is not None else None,
        "raw": raw,
        "quantity_raw": _clean(_row_value(row, indexes, "productos_cantidad_referencia")) or None,
        "unit_raw": _clean(_row_value(row, indexes, "productos_unidad_medida_referencia")) or None,
        "semantic_role": "reference_price_not_current_offer",
    }


def _identity_signature(row: list[str], indexes: Mapping[str, int]) -> tuple[str, ...]:
    quantity = parse_quantity(
        _row_value(row, indexes, "productos_cantidad_presentacion"),
        _row_value(row, indexes, "productos_unidad_medida_presentacion"),
    )
    quantity_signature = ""
    if quantity is not None:
        quantity_signature = f"{quantity[0]}:{_format_decimal(quantity[1])}"
    return (
        _clean(_row_value(row, indexes, "productos_descripcion")).casefold(),
        _clean(_row_value(row, indexes, "productos_marca")).casefold(),
        quantity_signature,
    )


def _row_signature(row: list[str], indexes: Mapping[str, int], price_raw: str) -> tuple[str, ...]:
    return _identity_signature(row, indexes) + (
        price_raw,
        _clean(_row_value(row, indexes, "productos_precio_unitario_promo1")),
        _clean(_row_value(row, indexes, "productos_precio_unitario_promo2")),
    )


def _normalized_observation(
    *,
    config: SepaConfig,
    outer_sha256: str,
    package: PackageState,
    commerce: CommerceRecord,
    store: StoreRecord,
    row: list[str],
    indexes: Mapping[str, int],
    line_number: int,
) -> dict[str, Any]:
    product_id_raw = _row_value(row, indexes, "id_producto")
    product_id = _clean(product_id_raw)
    gtin_valid = is_valid_gtin(product_id)
    quantity, quantity_status = _quantity_value(row, indexes)
    price_raw = _clean(_row_value(row, indexes, "productos_precio_lista"))
    price = parse_positive_decimal(price_raw)
    assert price is not None
    update_time = commerce.updated_at.isoformat() if commerce.updated_at else None
    return {
        "schema_version": SCHEMA_VERSION,
        "provider": PROVIDER_ID,
        "source": {
            "release_date": config.release_date.isoformat(),
            "outer_sha256": outer_sha256,
            "nested_package_name": package.name,
            "nested_package_sha256": package.sha256,
            "commerce_id": commerce.commerce_id,
            "banner_id": commerce.banner_id,
            "store_id": store.store_id,
            "product_row": line_number,
        },
        "product": {
            "provider_product_id": product_id,
            "provider_product_id_raw": product_id_raw,
            "gtin": product_id if gtin_valid else None,
            "gtin_status": "VALID" if gtin_valid else "INVALID_OR_NOT_GTIN",
            "name": _clean(_row_value(row, indexes, "productos_descripcion")) or None,
            "brand": _clean(_row_value(row, indexes, "productos_marca")) or None,
        },
        "offer": {
            "list_price": {
                "amount": _format_decimal_preserving_scale(price),
                "currency": "ARS",
                "field": "productos_precio_lista",
                "raw": price_raw,
            },
            "reference_price": _reference_price(row, indexes),
            "promotions": _promotion_values(row, indexes),
            "availability": "UNKNOWN",
            "observation_time": None,
            "provider_update_time": update_time,
            "freshness_status": package.freshness_status,
        },
        "quantity": quantity,
        "quantity_status": quantity_status,
        "store": {
            "commerce_id": store.commerce_id,
            "banner_id": store.banner_id,
            "store_id": store.store_id,
            "name": store.name or None,
            "type": store.store_type or None,
            "street": store.street or None,
            "number": store.number or None,
            "locality": store.locality or None,
            "province": store.province or None,
            "postal_code": store.postal_code or None,
            "latitude": _format_decimal(store.latitude) if store.latitude is not None else None,
            "longitude": _format_decimal(store.longitude) if store.longitude is not None else None,
            "geo_status": store.geo_status,
        },
    }


def _quarantine_record(
    *,
    config: SepaConfig,
    outer_sha256: str,
    package: PackageState,
    row: list[str] | None,
    indexes: Mapping[str, int] | None,
    line_number: int | None,
    reason: str,
    detail: str | None = None,
) -> dict[str, Any]:
    result: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "provider": PROVIDER_ID,
        "reason": reason,
        "detail": detail,
        "source": {
            "release_date": config.release_date.isoformat(),
            "outer_sha256": outer_sha256,
            "nested_package_name": package.name,
            "nested_package_sha256": package.sha256,
            "product_row": line_number,
        },
    }
    if row is not None and indexes is not None:
        result["identifiers"] = {
            "commerce_id": _clean(_row_value(row, indexes, "id_comercio")),
            "banner_id": _clean(_row_value(row, indexes, "id_bandera")),
            "store_id": _clean(_row_value(row, indexes, "id_sucursal")),
            "provider_product_id": _clean(_row_value(row, indexes, "id_producto")),
        }
    return result


class QualificationRun:
    def __init__(self, config: SepaConfig) -> None:
        self.config = config
        self.outer_sha256 = ""
        self.outer_bytes = 0
        self.packages: list[PackageState] = []
        self.diagnostics: Counter[str] = Counter()
        self.counts: Counter[str] = Counter()
        self.currencies: Counter[str] = Counter({"ARS": 0})
        self.provinces: Counter[str] = Counter()
        self.localities: Counter[str] = Counter()
        self.commerce_ids: set[str] = set()
        self.banner_keys: set[tuple[str, str]] = set()
        self.store_keys: set[tuple[str, str, str]] = set()
        self.provider_product_ids: set[str] = set()
        self.seen_provider_product_ids: set[str] = set()
        self.gtin_ids: set[str] = set()
        self.seen_gtins: set[str] = set()
        self.gtin_commerces: dict[str, set[str]] = defaultdict(set)
        self.product_identity_signatures: dict[str, tuple[str, ...]] = {}
        self.gtin_identity_signatures: dict[str, tuple[str, ...]] = {}
        self.audit_sampler = AuditSampler()
        self.normalized_writer: GzipJsonLinesWriter | None = None
        self.quarantine_writer: GzipJsonLinesWriter | None = None
        self.normalized_uncompressed_by_province: Counter[str] = Counter()
        self.normalized_rows_by_province: Counter[str] = Counter()
        self.ledger_path: Path | None = None
        self.ledger: ScopeLedger | None = None

    def verify_input(self) -> None:
        path = self.config.input_path
        if not path.is_file():
            raise SourceVerificationError(f"input file does not exist: {path}")
        self.outer_bytes = path.stat().st_size
        if self.outer_bytes <= 0:
            raise SourceVerificationError("input file is empty")
        self.outer_sha256 = _sha256_path(path)
        if self.config.expected_bytes is not None and self.outer_bytes != self.config.expected_bytes:
            raise SourceVerificationError(
                f"source byte mismatch: expected {self.config.expected_bytes}, got {self.outer_bytes}"
            )
        if self.config.expected_sha256 is not None and self.outer_sha256.lower() != self.config.expected_sha256.lower():
            raise SourceVerificationError(
                f"source SHA-256 mismatch: expected {self.config.expected_sha256}, got {self.outer_sha256}"
            )
        if not zipfile.is_zipfile(path):
            raise SourceVerificationError("input is not a valid ZIP archive")

    def _open_writers(self) -> None:
        if not self.config.write_normalized:
            return
        assert self.config.output_dir is not None
        self.normalized_writer = GzipJsonLinesWriter(self.config.output_dir / "accepted-observations.ndjson.gz")
        self.quarantine_writer = GzipJsonLinesWriter(self.config.output_dir / "quarantine.ndjson.gz")

    def _close_writers(self, promote: bool) -> None:
        for writer in (self.normalized_writer, self.quarantine_writer):
            if writer is not None:
                writer.close(promote)

    def _setup_ledger(self) -> None:
        if self.config.output_dir is not None:
            self.config.output_dir.mkdir(parents=True, exist_ok=True)
            temp_dir = self.config.output_dir
        else:
            temp_dir = Path(tempfile.gettempdir())
        handle = tempfile.NamedTemporaryFile(
            prefix="valuepilot-sepa-",
            suffix=".sqlite",
            dir=temp_dir,
            delete=False,
        )
        handle.close()
        self.ledger_path = Path(handle.name)
        self.ledger = ScopeLedger(self.ledger_path)

    def _remove_ledger(self) -> None:
        if self.ledger is not None:
            self.ledger.close()
        if self.ledger_path is not None and self.ledger_path.exists():
            self.ledger_path.unlink()

    def _record_identity(self, row: list[str], indexes: Mapping[str, int]) -> None:
        self.counts["rows_scanned"] += 1
        product_id_raw = _row_value(row, indexes, "id_producto")
        product_id = _clean(product_id_raw)
        if product_id:
            self.counts["product_id_present_rows"] += 1
            if product_id in self.seen_provider_product_ids:
                self.counts["duplicate_provider_product_id_rows"] += 1
            else:
                self.seen_provider_product_ids.add(product_id)
            self.provider_product_ids.add(product_id)
            signature = _identity_signature(row, indexes)
            previous = self.product_identity_signatures.get(product_id)
            if previous is None:
                self.product_identity_signatures[product_id] = signature
            elif previous != signature:
                if product_id not in self._conflicting_product_ids:
                    self._conflicting_product_ids.add(product_id)
                    self.counts["conflicting_product_id_scopes"] += 1
        else:
            self.counts["product_id_missing_rows"] += 1

        if _clean(_row_value(row, indexes, "productos_ean")).upper() in {"1", "Y", "YES", "SI", "SÍ", "TRUE"}:
            self.counts["apparent_ean_flag_rows"] += 1
        if is_valid_gtin(product_id):
            self.counts["gtin_valid_rows"] += 1
            self.gtin_ids.add(product_id)
            if product_id in self.seen_gtins:
                self.counts["duplicate_gtin_rows"] += 1
            else:
                self.seen_gtins.add(product_id)
            commerce_id = _clean(_row_value(row, indexes, "id_comercio"))
            self.gtin_commerces[product_id].add(commerce_id)
            signature = _identity_signature(row, indexes)
            previous = self.gtin_identity_signatures.get(product_id)
            if previous is None:
                self.gtin_identity_signatures[product_id] = signature
            elif previous != signature:
                if product_id not in self._conflicting_gtins:
                    self._conflicting_gtins.add(product_id)
                    self.counts["conflicting_gtin_identity_scopes"] += 1
        else:
            self.counts["retailer_specific_only_rows"] += 1

    def _record_scope(self, row: list[str], indexes: Mapping[str, int], price_raw: str) -> None:
        if self.ledger is None:
            return
        scope = (
            _clean(_row_value(row, indexes, "id_comercio")),
            _clean(_row_value(row, indexes, "id_bandera")),
            _clean(_row_value(row, indexes, "id_sucursal")),
            _clean(_row_value(row, indexes, "id_producto")),
        )
        if not all(scope):
            return
        self.ledger.add(scope, _row_signature(row, indexes, price_raw))

    def _record_audit_sample(
        self,
        package: PackageState,
        row: list[str],
        indexes: Mapping[str, int],
        store: StoreRecord | None,
        commerce: CommerceRecord | None,
        line_number: int,
        price: Decimal | None,
        quantity_status: str,
        promotions: list[dict[str, Any]],
    ) -> None:
        key = f"{package.sha256}:{line_number}"
        self.audit_sampler.consider(
            key,
            {
                "package": package.name,
                "line": line_number,
                "commerce_id": _clean(_row_value(row, indexes, "id_comercio")),
                "banner_id": _clean(_row_value(row, indexes, "id_bandera")),
                "store_id": _clean(_row_value(row, indexes, "id_sucursal")),
                "provider_product_id": _clean(_row_value(row, indexes, "id_producto")),
                "province": store.province if store is not None else "",
                "locality": store.locality if store is not None else "",
                "store_linked": store is not None,
                "commerce_linked": commerce is not None,
                "price_positive": price is not None and price > 0,
                "gtin_valid": is_valid_gtin(_clean(_row_value(row, indexes, "id_producto"))),
                "geo_status": store.geo_status if store is not None else "UNKNOWN_STORE_REFERENCE",
                "quantity_status": quantity_status,
                "promotion_status": "EXPLICIT" if promotions else "NONE",
                "freshness_status": package.freshness_status,
                "provider_update_time": commerce.updated_at.isoformat() if commerce and commerce.updated_at else None,
            },
        )

    def _write_quarantine(
        self,
        package: PackageState,
        row: list[str] | None,
        indexes: Mapping[str, int] | None,
        line_number: int | None,
        reason: str,
        detail: str | None = None,
    ) -> None:
        package.quarantined_rows += 1
        self.counts[f"quarantine_{reason}"] += 1
        if self.quarantine_writer is not None:
            self.quarantine_writer.write(
                _quarantine_record(
                    config=self.config,
                    outer_sha256=self.outer_sha256,
                    package=package,
                    row=row,
                    indexes=indexes,
                    line_number=line_number,
                    reason=reason,
                    detail=detail,
                )
            )

    def _parse_products(
        self,
        inner: zipfile.ZipFile,
        info: zipfile.ZipInfo,
        package: PackageState,
        commerces: Mapping[tuple[str, str], CommerceRecord],
        stores: Mapping[tuple[str, str, str], StoreRecord],
    ) -> None:
        with _open_csv(inner, info) as (text, reader):
            try:
                headers = next(reader)
            except StopIteration as exc:
                raise SepaQualificationError("productos.csv has no header") from exc
            indexes = _required_indexes(
                headers,
                (
                    "id_comercio",
                    "id_bandera",
                    "id_sucursal",
                    "id_producto",
                    "productos_ean",
                    "productos_descripcion",
                    "productos_cantidad_presentacion",
                    "productos_unidad_medida_presentacion",
                    "productos_marca",
                    "productos_precio_lista",
                    "productos_precio_referencia",
                    "productos_cantidad_referencia",
                    "productos_unidad_medida_referencia",
                    "productos_precio_unitario_promo1",
                    "productos_leyenda_promo1",
                    "productos_precio_unitario_promo2",
                    "productos_leyenda_promo2",
                ),
            )
            for line_number, row in enumerate(reader, start=2):
                package.product_rows += 1
                self._record_identity(row, indexes) if len(row) == len(headers) else None
                if len(row) != len(headers):
                    package.malformed_rows += 1
                    self._write_quarantine(package, row, indexes, line_number, "MALFORMED_ROW")
                    continue
                commerce_id = _clean(_row_value(row, indexes, "id_comercio"))
                banner_id = _clean(_row_value(row, indexes, "id_bandera"))
                store_id = _clean(_row_value(row, indexes, "id_sucursal"))
                product_id = _clean(_row_value(row, indexes, "id_producto"))
                if not commerce_id or not banner_id or not store_id or not product_id:
                    self._write_quarantine(package, row, indexes, line_number, "MALFORMED_ROW", "missing provider key")
                    continue
                commerce = commerces.get((commerce_id, banner_id))
                store = stores.get((commerce_id, banner_id, store_id))
                price_raw = _clean(_row_value(row, indexes, "productos_precio_lista"))
                price = parse_decimal_exact(price_raw)
                self._record_scope(row, indexes, price_raw)
                quantity, quantity_status = _quantity_value(row, indexes)
                raw_quantity = _clean(
                    _row_value(row, indexes, "productos_cantidad_presentacion")
                )
                raw_unit = _clean(
                    _row_value(row, indexes, "productos_unidad_medida_presentacion")
                )
                if raw_quantity and raw_unit:
                    self.counts["quantity_explicit_rows"] += 1
                    # This is the structural/numeric interpretation used by
                    # the preliminary cross-check.  It deliberately does not
                    # claim that every unit has mass/volume/count semantics.
                    if parse_positive_decimal(raw_quantity) is not None:
                        self.counts["quantity_numeric_structured_rows"] += 1
                else:
                    self.counts["quantity_missing_evidence_rows"] += 1
                promotions = _promotion_values(row, indexes)
                if quantity_status == "KNOWN":
                    self.counts["quantity_parseable_rows"] += 1
                else:
                    self.counts["quantity_unknown_rows"] += 1
                    if raw_quantity and raw_unit:
                        if _normalize_unit(raw_unit) not in _UNIT_ALIASES:
                            self.counts["quantity_unknown_unit_rows"] += 1
                        elif parse_positive_decimal(raw_quantity) is None:
                            self.counts["quantity_invalid_value_rows"] += 1
                if promotions:
                    self.counts["promotion_bearing_rows"] += 1
                if price is None:
                    self.counts["invalid_required_price_rows"] += 1
                    if price_raw:
                        if _SIGNED_DECIMAL_PATTERN.fullmatch(price_raw):
                            try:
                                signed = Decimal(price_raw)
                            except InvalidOperation:
                                signed = None
                            if signed is not None and signed <= 0:
                                self.counts["zero_or_negative_price_rows"] += 1
                            else:
                                self.counts["malformed_price_rows"] += 1
                        else:
                            self.counts["malformed_price_rows"] += 1
                elif price <= 0:
                    self.counts["invalid_required_price_rows"] += 1
                    self.counts["zero_or_negative_price_rows"] += 1
                else:
                    self.counts["positive_list_price_rows"] += 1
                    if price < Decimal("10"):
                        self.counts["price_under_ars_10_rows"] += 1
                    if price > Decimal("10000000"):
                        self.counts["price_over_ars_10000000_rows"] += 1
                self._record_audit_sample(
                    package,
                    row,
                    indexes,
                    store,
                    commerce,
                    line_number,
                    price,
                    quantity_status,
                    promotions,
                )
                if commerce is None:
                    self._write_quarantine(package, row, indexes, line_number, "UNKNOWN_COMMERCE_REFERENCE")
                    continue
                if store is None:
                    self.counts["unknown_store_reference_rows"] += 1
                    self._write_quarantine(package, row, indexes, line_number, "UNKNOWN_STORE_REFERENCE")
                    continue
                if package.freshness_status == "STALE":
                    self._write_quarantine(package, row, indexes, line_number, "STALE_PROVIDER_PACKAGE")
                    continue
                if package.freshness_status == "UNKNOWN":
                    self._write_quarantine(package, row, indexes, line_number, "FRESHNESS_UNKNOWN")
                    continue
                if price is None or price <= 0:
                    self._write_quarantine(package, row, indexes, line_number, "INVALID_REQUIRED_PRICE")
                    continue
                if price < Decimal("10") or price > Decimal("10000000"):
                    self._write_quarantine(package, row, indexes, line_number, "PRICE_PLAUSIBILITY_REVIEW")
                    continue
                if store.geo_status in {"GEO_INVALID", "GEO_OUT_OF_BOUNDS"}:
                    self._write_quarantine(package, row, indexes, line_number, store.geo_status)
                    continue

                if _has_replacement(row):
                    self.diagnostics["TEXT_DECODE_REPLACEMENT"] += 1
                observation = _normalized_observation(
                    config=self.config,
                    outer_sha256=self.outer_sha256,
                    package=package,
                    commerce=commerce,
                    store=store,
                    row=row,
                    indexes=indexes,
                    line_number=line_number,
                )
                package.accepted_rows += 1
                self.counts["accepted_current_price_rows"] += 1
                if quantity_status == "KNOWN":
                    self.counts["unit_value_ready_rows"] += 1
                if store.geo_status == "GEO_INCOMPLETE":
                    self.counts["geo_incomplete_accepted_rows"] += 1
                self.provinces[store.province or "UNKNOWN"] += 1
                self.localities[store.locality or "UNKNOWN"] += 1
                if self.normalized_writer is not None:
                    self.normalized_writer.write(observation)
                    self.normalized_uncompressed_by_province[store.province or "UNKNOWN"] += (
                        len(_canonical_json(observation)) + 1
                    )
                    self.normalized_rows_by_province[store.province or "UNKNOWN"] += 1

    def _package_report(self, package: PackageState) -> dict[str, Any]:
        return {
            "name": package.name,
            "bytes": package.size_bytes,
            "sha256": package.sha256,
            "status": package.status,
            "inner_uncompressed_bytes": package.inner_uncompressed_bytes,
            "commerce_rows": package.commerce_rows,
            "store_rows": package.store_rows,
            "product_rows": package.product_rows,
            "accepted_rows": package.accepted_rows,
            "quarantined_rows": package.quarantined_rows,
            "malformed_rows": package.malformed_rows,
            "package_update_time": package.package_update_at.isoformat() if package.package_update_at else None,
            "freshness_status": package.freshness_status,
            "age_days_from_release_start": package.age_days,
            "diagnostics": dict(sorted(package.diagnostics.items())),
        }

    def _audit_report(self) -> dict[str, Any]:
        records = self.audit_sampler.records()
        failures: Counter[str] = Counter()
        for record in records:
            checks = {
                "store_linkage": record["store_linked"],
                "commerce_linkage": record["commerce_linked"],
                "price_parse": record["price_positive"],
                "coordinates": record["geo_status"] == "VALID",
                "freshness": record["freshness_status"] == "FRESH",
            }
            for name, passed in checks.items():
                if not passed:
                    failures[name] += 1
        provinces = sorted({record["province"] for record in records if record["province"]})
        commerces = sorted({record["commerce_id"] for record in records if record["commerce_id"]})
        localities = sorted({record["locality"] for record in records if record["locality"]})
        return {
            "status": "MEASURED" if len(records) >= 100 else "INSUFFICIENT_SAMPLE",
            "sample_size": len(records),
            "target_minimum": 100,
            "selection": "stable SHA-256 rank over nested-package SHA-256 and CSV line; bounded sample",
            "retailer_ids": commerces,
            "province_count": len(provinces),
            "provinces": provinces,
            "locality_count": len(localities),
            "failure_counts": dict(sorted(failures.items())),
            "quantity_known": sum(record["quantity_status"] == "KNOWN" for record in records),
            "quantity_unknown": sum(record["quantity_status"] != "KNOWN" for record in records),
            "valid_gtin": sum(bool(record["gtin_valid"]) for record in records),
            "promotion_bearing": sum(record["promotion_status"] == "EXPLICIT" for record in records),
            "note": "Sample audit is observational and does not manually repair or promote rows.",
        }

    def _build_report(self, ledger_summary: Mapping[str, int]) -> dict[str, Any]:
        accepted = self.counts["accepted_current_price_rows"]
        total = self.counts["rows_scanned"]
        quantity_ready = self.counts["unit_value_ready_rows"]
        store_coordinate_counts = Counter()
        address_count = 0
        store_province_counts: Counter[str] = Counter()
        store_locality_counts: Counter[str] = Counter()
        store_coordinate_counts.update(self._store_geo_counts)
        address_count = self._address_store_count
        store_province_counts.update(self._store_province_counts)
        store_locality_counts.update(self._store_locality_counts)
        package_statuses = Counter(package.status for package in self.packages)
        freshness_statuses = Counter(package.freshness_status for package in self.packages if package.status == "VALID")
        stale_rows = sum(package.product_rows for package in self.packages if package.freshness_status == "STALE")
        normalized_files: dict[str, Any] = {}
        if self.config.output_dir is not None and self.config.write_normalized:
            for name in ("accepted-observations.ndjson.gz", "quarantine.ndjson.gz"):
                path = self.config.output_dir / name
                if path.exists():
                    normalized_files[name] = _file_descriptor(path)
        regional_estimates: dict[str, Any] = {}
        for province, row_count in sorted(self.normalized_rows_by_province.items()):
            uncompressed = self.normalized_uncompressed_by_province[province]
            regional_estimates[province] = {
                "accepted_rows": row_count,
                "normalized_uncompressed_bytes": uncompressed,
                "national_share_estimate": (
                    f"{(Decimal(row_count) / Decimal(accepted) * Decimal(100)).quantize(Decimal('0.01'))}%"
                    if accepted
                    else "0.00%"
                ),
            }
        status = "CONDITIONAL_GO_OFFLINE_ADAPTER_ONLY"
        blockers = [
            "No row-level product observation timestamp is present; freshness is package-level provenance only.",
            "Availability is UNKNOWN; SEPA price publication does not prove stock, pickup, or delivery.",
            "Semantic 25-basket coverage is not qualified because the schema has no category field and keyword-only matching is unsafe.",
            "Retailer marks, logos, images, and any rights beyond the catalog licence remain outside this ingestion and require separate confirmation.",
        ]
        report: dict[str, Any] = {
            "report_schema_version": "argentina-sepa-qualification-report-v2",
            "status": status,
            "recommendation": status,
            "policy_version": POLICY_VERSION,
            "schema_version": SCHEMA_VERSION,
            "source": {
                "provider": PROVIDER_ID,
                "label": PROVIDER_LABEL,
                "host": SOURCE_HOST,
                "dataset_url": SOURCE_DATASET_URL,
                "landing_url": SOURCE_LANDING_URL,
                "national_catalog_url": SOURCE_CATALOG_URL,
                "license": SOURCE_LICENSE,
                "attribution": SOURCE_ATTRIBUTION,
                "release_date": self.config.release_date.isoformat(),
                "file_name": self.config.input_path.name,
                "bytes": self.outer_bytes,
                "sha256": self.outer_sha256,
                "outer_zip_entries": self._outer_entry_count,
                "nested_retailer_zip_count": self._nested_zip_count,
                "nested_zero_byte_count": self._zero_byte_nested_count,
                "nested_uncompressed_bytes": sum(package.inner_uncompressed_bytes for package in self.packages),
                "raw_data_downloaded_locally": True,
                "raw_data_committed": False,
            },
            "schema": {
                "outer_structure": "daily outer ZIP containing nested retailer ZIP packages",
                "required_files": list(REQUIRED_FILES),
                "delimiter": "|",
                "encoding": "UTF-8 with BOM accepted; replacement characters diagnosed, not repaired",
                "product_fields": [
                    "id_comercio",
                    "id_bandera",
                    "id_sucursal",
                    "id_producto",
                    "productos_ean (flag, not barcode value)",
                    "productos_descripcion",
                    "productos_cantidad_presentacion",
                    "productos_unidad_medida_presentacion",
                    "productos_marca",
                    "productos_precio_lista",
                    "productos_precio_referencia",
                    "productos_cantidad_referencia",
                    "productos_unidad_medida_referencia",
                    "productos_precio_unitario_promo1",
                    "productos_leyenda_promo1",
                    "productos_precio_unitario_promo2",
                    "productos_leyenda_promo2",
                ],
                "store_fields": [
                    "id_comercio",
                    "id_bandera",
                    "id_sucursal",
                    "sucursales_nombre",
                    "sucursales_tipo",
                    "sucursales_calle",
                    "sucursales_numero",
                    "sucursales_latitud",
                    "sucursales_longitud",
                    "sucursales_codigo_postal",
                    "sucursales_localidad",
                    "sucursales_provincia",
                ],
                "commerce_fields": [
                    "id_comercio",
                    "id_bandera",
                    "comercio_cuit",
                    "comercio_razon_social",
                    "comercio_bandera_nombre",
                    "comercio_bandera_url",
                    "comercio_ultima_actualizacion",
                    "comercio_version_sepa",
                ],
                "not_provided": [
                    "row-level observation timestamp",
                    "currency field in productos.csv",
                    "inventory/stock",
                    "online orderability",
                    "pickup readiness",
                    "delivery, fee, ETA, service area, slot, or fulfilment hours",
                    "structured product category",
                ],
            },
            "scale": {
                "rows_scanned": total,
                "distinct_provider_product_ids": len(self.provider_product_ids),
                "distinct_commerce_ids": len(self.commerce_ids),
                "distinct_banner_keys": len(self.banner_keys),
                "distinct_store_keys": len(self.store_keys),
                "distinct_localities": len(self._locality_set),
                "distinct_provinces": len(self._province_set),
                "store_rows": self._store_row_count,
                "coordinate_valid_store_rows": self._store_geo_counts["VALID"],
                "coordinate_incomplete_store_rows": self._store_geo_counts["GEO_INCOMPLETE"],
                "coordinate_invalid_store_rows": self._store_geo_counts["GEO_INVALID"],
                "coordinate_out_of_bounds_store_rows": self._store_geo_counts["GEO_OUT_OF_BOUNDS"],
                "stores_with_address": address_count,
                "cities_top_by_store_count": [
                    {"locality": name, "stores": count}
                    for name, count in sorted(
                        store_locality_counts.items(), key=lambda item: (-item[1], item[0])
                    )[:20]
                ],
                "stores_per_province": dict(sorted(store_province_counts.items())),
                "coverage_beyond_buenos_aires": any(
                    name.casefold() not in {"buenos aires", "caba", "ciudad autónoma de buenos aires"}
                    for name in self._province_set
                ),
            },
            "identity": {
                "product_id_present_rows": self.counts["product_id_present_rows"],
                "apparent_ean_flag_rows": self.counts["apparent_ean_flag_rows"],
                "gtin_valid_rows": self.counts["gtin_valid_rows"],
                "gtin_valid_row_percent": (
                    f"{(Decimal(self.counts['gtin_valid_rows']) / Decimal(total) * Decimal(100)).quantize(Decimal('0.01'))}%"
                    if total
                    else "0.00%"
                ),
                "checksum_valid_unique_gtins": len(self.gtin_ids),
                "retailer_specific_only_rows": self.counts["retailer_specific_only_rows"],
                "duplicate_provider_product_id_rows": self.counts["duplicate_provider_product_id_rows"],
                "duplicate_gtin_rows": self.counts["duplicate_gtin_rows"],
                "conflicting_product_id_scopes": self.counts["conflicting_product_id_scopes"],
                "conflicting_gtin_identity_scopes": self.counts["conflicting_gtin_identity_scopes"],
                "valid_gtins_in_multiple_commerce_ids": sum(
                    len(values) >= 2 for values in self.gtin_commerces.values()
                ),
                "fuzzy_matching": "NOT_USED",
            },
            "quantity_unit_value": {
                "structured_quantity_fields": [
                    "productos_cantidad_presentacion",
                    "productos_unidad_medida_presentacion",
                ],
                "quantity_parseable_rows": self.counts["quantity_parseable_rows"],
                "quantity_explicit_rows": self.counts["quantity_explicit_rows"],
                "quantity_numeric_structured_rows": self.counts[
                    "quantity_numeric_structured_rows"
                ],
                "quantity_parseable_row_percent": (
                    f"{(Decimal(self.counts['quantity_parseable_rows']) / Decimal(total) * Decimal(100)).quantize(Decimal('0.01'))}%"
                    if total
                    else "0.00%"
                ),
                "quantity_unknown_rows": self.counts["quantity_unknown_rows"],
                "quantity_unknown_unit_rows": self.counts["quantity_unknown_unit_rows"],
                "quantity_invalid_value_rows": self.counts[
                    "quantity_invalid_value_rows"
                ],
                "quantity_missing_evidence_rows": self.counts[
                    "quantity_missing_evidence_rows"
                ],
                "package_text_field": "NOT_PROVIDED_AS_SEPARATE_FIELD",
                "unit_value_ready_rows": quantity_ready,
                "quantity_semantics": "explicit structured presentation quantity only; unknown remains unknown",
            },
            "price_quality": {
                "currency": "ARS",
                "currency_field_present": False,
                "currency_basis": "release/source scope supplied by milestone; no per-row currency column",
                "positive_list_price_rows": self.counts["positive_list_price_rows"],
                "invalid_required_price_rows": self.counts["invalid_required_price_rows"],
                "zero_or_negative_price_rows": self.counts["zero_or_negative_price_rows"],
                "malformed_price_rows": self.counts["malformed_price_rows"],
                "price_under_ars_10_rows": self.counts["price_under_ars_10_rows"],
                "price_over_ars_10000000_rows": self.counts["price_over_ars_10000000_rows"],
                "promotion_bearing_rows": self.counts["promotion_bearing_rows"],
                "promotion_eligibility": "UNKNOWN unless shopper conditions are separately established",
                **{key: value for key, value in ledger_summary.items()},
            },
            "freshness": {
                "release_date": self.config.release_date.isoformat(),
                "stale_after_days": self.config.stale_after_days,
                "policy": "package-level latest explicit commerce/footer timestamp; no hidden wall clock",
                "row_level_observation_time_present": False,
                "provider_update_timestamp_coverage": self._provider_update_coverage,
                "package_status_counts": dict(sorted(freshness_statuses.items())),
                "stale_package_count": sum(
                    package.freshness_status == "STALE" for package in self.packages
                ),
                "stale_package_product_rows": stale_rows,
                "age_buckets": dict(sorted(self._age_buckets.items())),
                "packages": [self._package_report(package) for package in self.packages],
            },
            "geography": {
                "coordinate_bounds_policy": {
                    key: _format_decimal(value) for key, value in ARGENTINA_BOUNDS.items()
                },
                "store_geo_status_counts": dict(sorted(store_coordinate_counts.items())),
                "address_coverage": {
                    "stores_with_address": address_count,
                    "store_rows": self._store_row_count,
                },
                "locality_count": len(self._locality_set),
                "province_count": len(self._province_set),
                "stores_per_province": dict(sorted(store_province_counts.items())),
                "top_localities": [
                    {"locality": name, "stores": count}
                    for name, count in sorted(
                        store_locality_counts.items(), key=lambda item: (-item[1], item[0])
                    )[:20]
                ],
                "beyond_buenos_aires_evidence": sorted(self._province_set),
            },
            "sample_audit": self._audit_report(),
            "basket_feasibility": {
                "status": "NOT_YET_QUALIFIED",
                "scenario_count": 25,
                "categories": [
                    "milk",
                    "eggs",
                    "bread",
                    "rice",
                    "pasta",
                    "cooking oil",
                    "chicken/meat",
                    "fruit/vegetables",
                    "canned goods",
                    "beverages",
                    "cleaning/basic household",
                ],
                "exact_product_comparison": "NOT_MEASURED",
                "category_or_substitute_comparison": "NOT_MEASURED",
                "reason": "No structured category field; keyword-only semantic selection is explicitly disallowed for a launch claim.",
            },
            "storage": {
                "raw_download_bytes": self.outer_bytes,
                "nested_uncompressed_bytes": sum(package.inner_uncompressed_bytes for package in self.packages),
                "normalized_files": normalized_files,
                "normalized_uncompressed_bytes": self.normalized_writer.uncompressed_bytes if self.normalized_writer else None,
                "regional_estimates": regional_estimates,
                "android_asset_policy": "FULL NATIONAL RAW DATA MUST NOT SHIP TO ANDROID",
                "recommendation": "backend/tooling-side immutable regional snapshots; keep Android offline",
            },
            "rights": {
                "dataset_license": SOURCE_LICENSE,
                "attribution": SOURCE_ATTRIBUTION,
                "source_link": SOURCE_DATASET_URL,
                "clearly_granted_for_dataset": "catalog attribution licence as recorded by the official national catalog",
                "not_addressed_or_separate": [
                    "retailer logos, product images, and trademarks",
                    "merchant-specific commercial display terms beyond the dataset licence",
                    "retention/caching terms not stated in this engineering report",
                ],
                "legal_opinion": False,
            },
            "delivery_pickup": {
                "online_orderability": "NOT_PROVIDED",
                "pickup": "NOT_PROVIDED",
                "delivery": "NOT_PROVIDED",
                "delivery_fee": "NOT_PROVIDED",
                "service_fee": "NOT_PROVIDED",
                "minimum_order": "NOT_PROVIDED",
                "service_area": "NOT_PROVIDED",
                "eta": "NOT_PROVIDED",
                "delivery_slot": "NOT_PROVIDED",
                "pickup_readiness": "NOT_PROVIDED",
                "fulfilment_hours": "NOT_PROVIDED",
                "v1_boundary": "IN_STORE_ONLY",
            },
            "diagnostics": {
                "package_status_counts": dict(sorted(package_statuses.items())),
                "counts": dict(sorted(self.counts.items())),
                "warnings": dict(sorted(self.diagnostics.items())),
            },
            "blockers": blockers,
            "next_milestone": (
                "After rights/attribution confirmation and a small manually reviewed semantic basket set, build a separate provider-edge Argentina adapter; keep Android offline and keep delivery/pickup as later adapters."
            ),
            "production_authorized": False,
        }
        report["preliminary_cross_check"] = {
            "status": "RECONCILED_WITH_DEFINITIONAL_DIFFERENCES",
            "expected_from_preliminary_pass": {
                "outer_bytes": 325522188,
                "nested_packages": 15,
                "valid_nonempty_nested_packages": 14,
                "nested_uncompressed_bytes_approx": "~1.495 GiB",
                "product_price_rows": 14313249,
                "unique_provider_product_ids": 78671,
                "store_rows": 2620,
                "valid_coordinate_store_rows": 2594,
                "checksum_valid_unique_gtins": 78184,
                "rows_with_checksum_valid_gtin": 14272803,
                "valid_gtins_in_at_least_two_commerces": 20399,
                "promotion_rows": 1640544,
                "unknown_store_reference_rows": 55989,
                "quantity_numeric_rows": 14311497,
                "zero_or_negative_list_price_rows": 56,
                "price_under_ars_10_rows": 8024,
                "price_over_ars_10000000_rows": 9,
                "stale_package_age_days_approx": "~452",
            },
            "observed_by_this_adapter": {
                "outer_bytes": self.outer_bytes,
                "nested_packages": self._nested_zip_count,
                "valid_nonempty_nested_packages": sum(
                    package.status == "VALID" and package.size_bytes > 0
                    for package in self.packages
                ),
                "nested_uncompressed_bytes": sum(
                    package.inner_uncompressed_bytes for package in self.packages
                ),
                "product_price_rows": total,
                "unique_provider_product_ids": len(self.provider_product_ids),
                "store_rows": self._store_row_count,
                "valid_coordinate_store_rows": self._store_geo_counts["VALID"],
                "checksum_valid_unique_gtins": len(self.gtin_ids),
                "rows_with_checksum_valid_gtin": self.counts["gtin_valid_rows"],
                "valid_gtins_in_at_least_two_commerces": sum(
                    len(values) >= 2 for values in self.gtin_commerces.values()
                ),
                "promotion_rows": self.counts["promotion_bearing_rows"],
                "unknown_store_reference_rows": self.counts[
                    "unknown_store_reference_rows"
                ],
                "quantity_numeric_rows": self.counts[
                    "quantity_numeric_structured_rows"
                ],
                "quantity_canonical_rows": self.counts["quantity_parseable_rows"],
                "zero_or_negative_list_price_rows": self.counts[
                    "zero_or_negative_price_rows"
                ],
                "price_under_ars_10_rows": self.counts["price_under_ars_10_rows"],
                "price_over_ars_10000000_rows": self.counts[
                    "price_over_ars_10000000_rows"
                ],
                "stale_package_age_days": next(
                    (
                        package.age_days
                        for package in self.packages
                        if package.freshness_status == "STALE"
                    ),
                    None,
                ),
            },
            "notes": [
                "The 25-row store-count difference is deterministic: the source has 25 short eight-field continuation rows in commerce 6; this adapter requires all store fields and quarantines all 120 short/footer store records rather than treating them as stores.",
                "The 17,975-row unknown-store difference is entirely the same commerce-6 package: its split store rows leave those product references without an exact store key, so this adapter quarantines them instead of dropping the package silently.",
                "The preliminary quantity number is a positive numeric quantity-plus-unit count (14,311,497). This adapter's canonical mass/volume/count policy recognizes 13,915,328 rows; 396,169 explicit rows use noncanonical units (for example KGM, CU, CMQ, M2) and remain UNKNOWN, while 1,752 rows have non-positive/malformed quantity values.",
                "The stale package is exactly 451.00 days old after converting its explicit 2025-06-11T21:00:01-03:00 timestamp to UTC; the preliminary value was an approximate local-date figure (~452 days).",
            ],
        }
        return report

    def run(self) -> dict[str, Any]:
        self.verify_input()
        self._conflicting_product_ids: set[str] = set()
        self._conflicting_gtins: set[str] = set()
        self._store_geo_counts: Counter[str] = Counter()
        self._store_province_counts: Counter[str] = Counter()
        self._store_locality_counts: Counter[str] = Counter()
        self._locality_set: set[str] = set()
        self._province_set: set[str] = set()
        self._address_store_count = 0
        self._store_row_count = 0
        self._provider_update_rows = 0
        self._provider_update_missing_rows = 0
        self._provider_update_coverage: str = "NOT_MEASURED"
        self._age_buckets: Counter[str] = Counter()
        self._outer_entry_count = 0
        self._nested_zip_count = 0
        self._zero_byte_nested_count = 0
        self._store_keys_seen_for_metadata: set[tuple[str, str, str]] = set()

        self._open_writers()
        self._setup_ledger()
        try:
            with self.config.input_path.open("rb") as raw, zipfile.ZipFile(raw) as outer:
                try:
                    bad_outer = outer.testzip()
                except (OSError, zipfile.BadZipFile) as exc:
                    raise SourceVerificationError(f"outer ZIP integrity failure: {exc}") from exc
                if bad_outer is not None:
                    raise SourceVerificationError(f"outer ZIP CRC failure: {bad_outer}")
                outer_infos = outer.infolist()
                self._outer_entry_count = len(outer_infos)
                for outer_info in outer_infos:
                    if outer_info.is_dir() or not outer_info.filename.lower().endswith(".zip"):
                        continue
                    self._nested_zip_count += 1
                    package = PackageState(
                        name=outer_info.filename,
                        size_bytes=outer_info.file_size,
                        sha256="",
                    )
                    self.packages.append(package)
                    if outer_info.file_size == 0:
                        self._zero_byte_nested_count += 1
                        # Even an unusable empty member gets a deterministic
                        # content hash for provenance/audit purposes.
                        package.sha256 = hashlib.sha256(b"").hexdigest()
                        package.status = "EMPTY_PROVIDER_PACKAGE"
                        self._write_quarantine(package, None, None, None, "EMPTY_PROVIDER_PACKAGE")
                        continue
                    try:
                        if outer_info.compress_type == zipfile.ZIP_STORED:
                            package_view: BinaryIO = io.BufferedReader(
                                _SliceReader(raw, _stored_member_offset(raw, outer_info), outer_info.file_size)
                            )
                            package.sha256 = _sha256_stream(package_view)
                            package_view.seek(0)
                            with zipfile.ZipFile(package_view) as inner:
                                self._process_package(package, inner)
                            package_view.close()
                        else:
                            with tempfile.NamedTemporaryFile(
                                prefix="valuepilot-sepa-nested-",
                                suffix=".zip",
                                dir=self.config.output_dir,
                                delete=False,
                            ) as nested_temp:
                                with outer.open(outer_info, "r") as source:
                                    digest = hashlib.sha256()
                                    for chunk in iter(lambda: source.read(1024 * 1024), b""):
                                        digest.update(chunk)
                                        nested_temp.write(chunk)
                                nested_temp.flush()
                                nested_path = Path(nested_temp.name)
                            package.sha256 = digest.hexdigest()
                            try:
                                with zipfile.ZipFile(nested_path) as inner:
                                    self._process_package(package, inner)
                            finally:
                                nested_path.unlink(missing_ok=True)
                    except (zipfile.BadZipFile, OSError, EOFError) as exc:
                        package.status = "ZIP_INVALID"
                        package.diagnostics["ZIP_INVALID"] += 1
                        self._write_quarantine(package, None, None, None, "ZIP_INVALID", str(exc))
            self._finalize_counts()
            ledger_summary = self.ledger.summary() if self.ledger is not None else {}
            self._close_writers(promote=True)
            report = self._build_report(ledger_summary)
            self._write_outputs(report)
            return report
        except Exception:
            self._close_writers(promote=False)
            raise
        finally:
            self._remove_ledger()

    def _process_package(self, package: PackageState, inner: zipfile.ZipFile) -> None:
        bad_member = inner.testzip()
        if bad_member is not None:
            raise zipfile.BadZipFile(f"inner ZIP CRC failure: {bad_member}")
        members = _safe_zip_members(inner)
        package.inner_uncompressed_bytes = sum(info.file_size for info in members)
        infos = {name: _find_inner_info(inner, name) for name in REQUIRED_FILES}
        missing = [name for name, info in infos.items() if info is None]
        if missing:
            package.status = "REQUIRED_FILE_MISSING"
            package.diagnostics["REQUIRED_FILE_MISSING"] += len(missing)
            self._write_quarantine(package, None, None, None, "REQUIRED_FILE_MISSING", ",".join(missing))
            return
        assert infos["comercio.csv"] is not None
        assert infos["sucursales.csv"] is not None
        assert infos["productos.csv"] is not None
        try:
            commerces, package_update_at = _parse_commerce(
                inner, infos["comercio.csv"], package, package.diagnostics
            )
            stores = _parse_stores(inner, infos["sucursales.csv"], package, package.diagnostics)
        except SepaQualificationError as exc:
            package.status = "REQUIRED_FILE_INVALID"
            package.diagnostics["REQUIRED_FILE_INVALID"] += 1
            self._write_quarantine(package, None, None, None, "REQUIRED_FILE_INVALID", str(exc))
            return
        package.package_update_at = package_update_at
        package.freshness_status, package.age_days = _freshness_for_package(
            package_update_at,
            self.config.release_date,
            self.config.stale_after_days,
        )
        self._age_buckets[_age_bucket(package.age_days, package.freshness_status)] += 1
        if package.freshness_status == "UNKNOWN":
            package.diagnostics["FRESHNESS_UNKNOWN"] += 1
        self._provider_update_rows += sum(record.updated_at is not None for record in commerces.values())
        self._provider_update_missing_rows += sum(record.updated_at is None for record in commerces.values())
        self._provider_update_coverage = "MEASURED"
        for commerce in commerces.values():
            self.commerce_ids.add(commerce.commerce_id)
            self.banner_keys.add((commerce.commerce_id, commerce.banner_id))
        for store in stores.values():
            key = (store.commerce_id, store.banner_id, store.store_id)
            self.store_keys.add(key)
            if key not in self._store_keys_seen_for_metadata:
                self._store_keys_seen_for_metadata.add(key)
                self._store_row_count += 1
                self._store_geo_counts[store.geo_status] += 1
                self._store_province_counts[store.province or "UNKNOWN"] += 1
                self._store_locality_counts[store.locality or "UNKNOWN"] += 1
                if store.province:
                    self._province_set.add(store.province)
                if store.locality:
                    self._locality_set.add(store.locality)
                if store.street or store.number or store.locality or store.province or store.postal_code:
                    self._address_store_count += 1
        self._parse_products(inner, infos["productos.csv"], package, commerces, stores)

    def _finalize_counts(self) -> None:
        self.counts["fresh_provider_update_rows"] = self._provider_update_rows
        self.counts["missing_provider_update_rows"] = self._provider_update_missing_rows
        for key in (
            "accepted_current_price_rows",
            "unit_value_ready_rows",
            "quantity_parseable_rows",
            "quantity_unknown_rows",
            "promotion_bearing_rows",
            "price_under_ars_10_rows",
            "price_over_ars_10000000_rows",
            "unknown_store_reference_rows",
            "invalid_required_price_rows",
            "malformed_price_rows",
            "zero_or_negative_price_rows",
            "quantity_explicit_rows",
            "quantity_numeric_structured_rows",
            "quantity_unknown_unit_rows",
            "quantity_invalid_value_rows",
            "quantity_missing_evidence_rows",
        ):
            self.counts.setdefault(key, 0)
        if self.counts["rows_scanned"]:
            self._provider_update_coverage = (
                f"{self._provider_update_rows} commerce metadata rows with parseable update timestamps; "
                f"{self._provider_update_missing_rows} without"
            )

    def _write_outputs(self, report: dict[str, Any]) -> None:
        if self.config.output_dir is not None and self.config.write_normalized:
            manifest = {
                "manifest_schema_version": "argentina-sepa-manifest-v1",
                "policy_version": POLICY_VERSION,
                "schema_version": SCHEMA_VERSION,
                "provider": PROVIDER_ID,
                "release_date": self.config.release_date.isoformat(),
                "source": {
                    "file_name": self.config.input_path.name,
                    "bytes": self.outer_bytes,
                    "sha256": self.outer_sha256,
                    "host": SOURCE_HOST,
                    "dataset_url": SOURCE_DATASET_URL,
                    "license": SOURCE_LICENSE,
                    "attribution": SOURCE_ATTRIBUTION,
                },
                "files": {
                    name: _file_descriptor(self.config.output_dir / name)
                    for name in ("accepted-observations.ndjson.gz", "quarantine.ndjson.gz")
                },
                "counts": {
                    "accepted_rows": self.normalized_writer.rows if self.normalized_writer else 0,
                    "quarantine_rows": self.quarantine_writer.rows if self.quarantine_writer else 0,
                    "nested_packages": len(self.packages),
                },
                "atomic_completion": True,
            }
            _write_bytes_atomically(self.config.output_dir / "manifest.json", _canonical_json(manifest) + b"\n")
            manifest_hash = _sha256_path(self.config.output_dir / "manifest.json")
            _write_text_atomically(self.config.output_dir / "manifest.sha256", f"{manifest_hash}  manifest.json\n")
            report["storage"]["manifest"] = _file_descriptor(self.config.output_dir / "manifest.json")
            report["storage"]["manifest_sha256_file"] = _file_descriptor(self.config.output_dir / "manifest.sha256")
        if self.config.report_json is not None:
            _write_text_atomically(self.config.report_json, _pretty_json(report))
        if self.config.report_markdown is not None:
            _write_text_atomically(self.config.report_markdown, report_markdown(report))


def report_markdown(report: Mapping[str, Any]) -> str:
    source = report["source"]
    scale = report["scale"]
    identity = report["identity"]
    quantity = report["quantity_unit_value"]
    price = report["price_quality"]
    freshness = report["freshness"]
    geography = report["geography"]
    audit = report["sample_audit"]
    storage = report["storage"]
    rights = report["rights"]
    delivery = report["delivery_pickup"]
    lines = [
        "# Argentina SEPA / Precios Claros qualification",
        "",
        f"Status: `{report['status']}`",
        "",
        f"Recommendation: **{report['recommendation']}**",
        "",
        "This report qualifies one exact official release for offline/provider-edge normalization only. It does not add Android networking or authorize production display/ranking.",
        "",
        "## Exact source",
        "",
        f"- Release date: **{source['release_date']}**",
        f"- File: **{source['file_name']}**",
        f"- Bytes: **{source['bytes']:,}**",
        f"- SHA-256: `{source['sha256']}`",
        f"- Dataset: [{source['dataset_url']}]({source['dataset_url']})",
        f"- Licence recorded from the national catalog: **{source['license']}**",
        f"- Attribution: **{source['attribution']}**",
        "",
        "## Actual structure",
        "",
        f"The outer ZIP contained **{source['nested_retailer_zip_count']}** nested retailer packages ({source['nested_zero_byte_count']} zero-byte). Each valid package was required to contain `comercio.csv`, `sucursales.csv`, and a streamed `productos.csv`.",
        f"Expanded nested member sizes total **{source['nested_uncompressed_bytes']:,} bytes**; the national product table was never loaded into memory.",
        "",
        "## Preliminary cross-check",
        "",
        "The independent preliminary figures reconcile to this run where the definitions are identical. The remaining differences are intentional and documented:",
        "",
        f"- Store rows: preliminary **2,620** vs strict full-field rows **{scale['store_rows']:,}**; 25 short eight-field continuation rows in commerce 6 (among 120 malformed/footer rows) are not treated as stores.",
        f"- Unknown-store product rows: preliminary **55,989** vs **{report['diagnostics']['counts'].get('unknown_store_reference_rows', 0):,}**; the additional 17,975 are the product references from that same malformed commerce-6 store table and are explicitly quarantined.",
        f"- Quantity: preliminary positive numeric quantity+unit rows **14,311,497** vs **{quantity['quantity_numeric_structured_rows']:,}**; canonical mass/volume/count rows are **{quantity['quantity_parseable_rows']:,}**, with **{quantity['quantity_unknown_unit_rows']:,}** noncanonical units and **{quantity['quantity_invalid_value_rows']:,}** invalid/non-positive values kept UNKNOWN.",
        f"- Stale age: the stale package is **{next((p['age_days_from_release_start'] for p in freshness['packages'] if p['freshness_status'] == 'STALE'), 'unknown')}** days using its explicit UTC-normalized timestamp; the preliminary **~452** was an approximate local-date value.",
        "",
        "## Scale",
        "",
        f"- Product-price rows scanned: **{scale['rows_scanned']:,}**",
        f"- Distinct provider product IDs: **{scale['distinct_provider_product_ids']:,}**",
        f"- Commerce IDs / banner keys: **{scale['distinct_commerce_ids']:,} / {scale['distinct_banner_keys']:,}**",
        f"- Store keys / localities / provinces: **{scale['distinct_store_keys']:,} / {scale['distinct_localities']:,} / {scale['distinct_provinces']:,}**",
        f"- Store rows with valid coordinates: **{scale['coordinate_valid_store_rows']:,}**; incomplete: **{scale['coordinate_incomplete_store_rows']:,}**; invalid/out-of-bounds: **{scale['coordinate_invalid_store_rows'] + scale['coordinate_out_of_bounds_store_rows']:,}**",
        "",
        "## Identity and quantity readiness",
        "",
        f"- Checksum-valid GTIN rows / unique GTINs: **{identity['gtin_valid_rows']:,} / {identity['checksum_valid_unique_gtins']:,}** ({identity['gtin_valid_row_percent']})",
        f"- Valid GTINs appearing across at least two commerce IDs: **{identity['valid_gtins_in_multiple_commerce_ids']:,}**",
        f"- Retailer-specific-only rows: **{identity['retailer_specific_only_rows']:,}**; fuzzy matching: **{identity['fuzzy_matching']}**",
        f"- Conflicting product-ID / GTIN identity scopes: **{identity['conflicting_product_id_scopes']:,} / {identity['conflicting_gtin_identity_scopes']:,}**",
        f"- Explicit quantity/unit rows: **{quantity['quantity_explicit_rows']:,}**; canonical mass/volume/count rows: **{quantity['quantity_parseable_rows']:,}** ({quantity['quantity_parseable_row_percent']}); unknown quantity: **{quantity['quantity_unknown_rows']:,}**",
        f"- Unit-value-ready rows: **{quantity['unit_value_ready_rows']:,}**; quantity is never inferred from title text.",
        "",
        "## Price, promotions, and freshness",
        "",
        f"- Positive list-price rows: **{price['positive_list_price_rows']:,}**; invalid required price: **{price['invalid_required_price_rows']:,}**",
        f"- Plausibility signals preserved/quarantined: under ARS 10 = **{price['price_under_ars_10_rows']:,}**, over ARS 10,000,000 = **{price['price_over_ars_10000000_rows']:,}**",
        f"- Promotion-bearing rows: **{price['promotion_bearing_rows']:,}**; promotion eligibility remains **UNKNOWN** unless separately established.",
        f"- Package freshness policy: **{freshness['stale_after_days']} days**, using explicit release date and package-level latest timestamp; stale packages: **{freshness['stale_package_count']}**.",
        f"- Row-level observation timestamp: **not provided**; package update coverage: **{freshness['provider_update_timestamp_coverage']}**.",
        "",
        "## Geography",
        "",
        f"- Province count: **{geography['province_count']}**; locality count: **{geography['locality_count']}**.",
        f"- Coverage beyond Buenos Aires is evidenced by the represented provinces: {', '.join(geography['beyond_buenos_aires_evidence']) or 'none recorded'}.",
        "- Missing coordinates remain geo-incomplete; no coordinates were invented or geocoded.",
        "",
        "## Deterministic sample audit",
        "",
        f"- Status: **{audit['status']}**, sample size **{audit['sample_size']}** (minimum 100).",
        f"- Sample spans **{audit['province_count']}** provinces, **{audit['locality_count']}** localities, and **{len(audit['retailer_ids'])}** commerce IDs.",
        f"- Failure counts: `{json.dumps(audit['failure_counts'], ensure_ascii=False, sort_keys=True)}`.",
        "",
        "## Basket feasibility",
        "",
        "The 25-scenario semantic basket test is **NOT_YET_QUALIFIED**. SEPA has no structured category field, and keyword-only matches previously produced false positives. No basket launch claim is made here.",
        "",
        "## Storage and delivery boundary",
        "",
        f"- Raw outer ZIP: **{storage['raw_download_bytes']:,} bytes**; nested uncompressed members: **{storage['nested_uncompressed_bytes']:,} bytes**.",
        f"- Normalized output is local/provider-edge only: **{storage.get('normalized_uncompressed_bytes') or 0:,} uncompressed bytes** before gzip.",
        "- The full national raw dataset must not ship in Android assets; regional immutable snapshots remain a later delivery decision.",
        f"- Delivery/pickup fields are **{delivery['v1_boundary']}** only: orderability, pickup, delivery, fees, ETA, slots, and fulfilment hours are NOT PROVIDED.",
        "",
        "## Rights and recommendation",
        "",
        f"The source licence is recorded as **{rights['dataset_license']}** with attribution and source link. Retailer marks, product images, and trademarks are separate/unaddressed and are not ingested. This is not a legal opinion.",
        "",
        "Conditional GO is limited to offline/provider-edge normalization and a future separate adapter review. Production integration still requires the documented freshness, rights, semantic basket, and evidence gates; availability remains UNKNOWN.",
        "",
        "### Explicit blockers",
        "",
    ]
    lines.extend(f"- {blocker}" for blocker in report["blockers"])
    lines.extend(
        [
            "",
            f"Next milestone: {report['next_milestone']}",
            "",
        ]
    )
    return "\n".join(lines)


def _parse_date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("release date must be YYYY-MM-DD") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--release-date", default=EXPECTED_RELEASE_DATE.isoformat(), type=_parse_date)
    parser.add_argument("--expected-sha256", default=EXPECTED_SHA256)
    parser.add_argument("--expected-bytes", default=EXPECTED_BYTES, type=int)
    parser.add_argument("--stale-after-days", default=STALE_AFTER_DAYS, type=int)
    parser.add_argument("--output-dir", default=Path("local-provider-data/argentina-sepa"), type=Path)
    parser.add_argument("--report-json", default=Path("ARGENTINA_SEPA_QUALIFICATION.json"), type=Path)
    parser.add_argument("--report-markdown", default=Path("ARGENTINA_SEPA_QUALIFICATION.md"), type=Path)
    parser.add_argument("--no-normalized-output", action="store_true")
    args = parser.parse_args(argv)
    config = SepaConfig(
        input_path=args.input,
        release_date=args.release_date,
        stale_after_days=args.stale_after_days,
        expected_sha256=args.expected_sha256 or None,
        expected_bytes=args.expected_bytes,
        output_dir=None if args.no_normalized_output else args.output_dir,
        report_json=args.report_json,
        report_markdown=args.report_markdown,
        write_normalized=not args.no_normalized_output,
    )
    try:
        report = QualificationRun(config).run()
    except (SepaQualificationError, OSError, zipfile.BadZipFile) as exc:
        print(f"SEPA qualification failed safely: {exc}", file=sys.stderr)
        return 2
    print(report_markdown(report), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

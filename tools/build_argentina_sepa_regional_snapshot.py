#!/usr/bin/env python3
"""Build a compact, deterministic Argentina SEPA regional snapshot offline.

The input is the already-qualified ``accepted-observations.ndjson.gz`` output
from :mod:`tools.qualify_argentina_sepa`.  It is deliberately a provider-edge
tool: it performs no network access, does not infer stock or delivery, and
does not write Android assets.  Repeated store/product metadata is aggregated
in a temporary SQLite database so a national observation stream is never held
in Python memory.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import tempfile
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, BinaryIO, Iterable, Iterator, Mapping


QUALIFIED_SCHEMA_VERSION = "argentina-sepa-normalized-observation-v1"
QUALIFIED_POLICY_VERSION = "argentina-sepa-policy-v1"
REGIONAL_SCHEMA_VERSION = "argentina-sepa-regional-snapshot-v1"
REGIONAL_POLICY_VERSION = "argentina-sepa-regional-policy-v1"
PROVIDER_ID = "ARGENTINA_SEPA_PRECIOS_CLAROS"
REGION_ID = "ar-caba"
REGION_DISPLAY_NAME = "Ciudad Autónoma de Buenos Aires"
REGION_PROVINCE_CODE = "AR-C"
RELEASE_DATE = "2026-09-06"
EXPECTED_OUTER_SHA256 = "e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305"
EXPECTED_OUTER_BYTES = 325522188
CURRENCY = "ARS"
AVAILABILITY = "UNKNOWN"
DELIVERY_PICKUP = "NOT_PROVIDED"

_DECIMAL_CHARS = frozenset("0123456789+-.eE")


class RegionalSnapshotError(ValueError):
    """An input or generated regional artifact failed a deterministic gate."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise RegionalSnapshotError(message)


def _canonical_json(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode("utf-8")


def _read_json(path: Path, label: str) -> dict[str, Any]:
    _require(path.is_file(), f"Missing {label}: {path}")
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RegionalSnapshotError(f"Invalid {label}: {path}") from exc
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _text(value: Any, field: str, *, required: bool = True, max_length: int = 512) -> str | None:
    if value is None:
        _require(not required, f"{field} is required")
        return None
    _require(isinstance(value, str), f"{field} must be text")
    value = value.strip()
    _require((not required) or bool(value), f"{field} must not be blank")
    _require(len(value) <= max_length, f"{field} exceeds {max_length} characters")
    return value or None


def _canonical_timestamp(value: str, field: str = "timestamp") -> str:
    _require(isinstance(value, str) and value.strip() == value and value, f"{field} is invalid")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise RegionalSnapshotError(f"{field} is not ISO-8601") from exc
    _require(parsed.tzinfo is not None, f"{field} needs an explicit timezone")
    utc = parsed.astimezone(timezone.utc)
    return utc.isoformat().replace("+00:00", "Z")


def _canonical_decimal(value: Any, field: str, *, positive: bool = False) -> str:
    _require(isinstance(value, str), f"{field} must be a decimal string")
    raw = value.strip()
    _require(raw == value and raw and set(raw) <= _DECIMAL_CHARS, f"{field} is not an exact decimal")
    try:
        number = Decimal(raw)
    except InvalidOperation as exc:
        raise RegionalSnapshotError(f"{field} is not an exact decimal") from exc
    _require(number.is_finite(), f"{field} must be finite")
    if positive:
        _require(number > 0, f"{field} must be positive")
    # Decimal's fixed-point form preserves source precision while avoiding an
    # exponent or binary floating point representation in an artifact.
    fixed = format(number, "f")
    _require(fixed == raw or Decimal(fixed) == number, f"{field} is not canonical")
    return fixed


def _parse_timestamp_epoch(value: str) -> int:
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    parsed = datetime.fromisoformat(normalized).astimezone(timezone.utc)
    epoch = datetime(1970, 1, 1, tzinfo=timezone.utc)
    delta = parsed - epoch
    return delta.days * 86_400_000 + delta.seconds * 1_000 + delta.microseconds // 1_000


def _valid_gtin(value: str) -> bool:
    if len(value) not in {8, 12, 13, 14} or not value.isdigit():
        return False
    total = 0
    weight = 3
    for char in reversed(value[:-1]):
        total += int(char) * weight
        weight = 1 if weight == 3 else 3
    return int(value[-1]) == (10 - total % 10) % 10


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _source_manifest_hash(path: Path) -> str:
    raw = path.read_bytes()
    _require(raw == _canonical_json(json.loads(raw)), "Qualification manifest is not canonical JSON")
    return hashlib.sha256(raw).hexdigest()


def _report_normalized_size(path: Path | None, proof: "SourceProof") -> int | None:
    """Read the prior qualification report only for its measured size fact."""

    if path is None or proof.normalized_uncompressed_bytes is not None:
        return proof.normalized_uncompressed_bytes
    report = _read_json(path, "qualification report")
    storage = report.get("storage")
    value = storage.get("normalized_uncompressed_bytes") if isinstance(storage, dict) else None
    _require(value is None or (isinstance(value, int) and value > 0), "Qualification report normalized size is invalid")
    return value


@dataclass(frozen=True)
class SourceProof:
    accepted_path: Path
    accepted_sha256: str
    accepted_bytes: int
    accepted_rows: int
    manifest_sha256: str
    outer_sha256: str
    outer_bytes: int
    release_date: str
    qualification_schema_version: str
    qualification_policy_version: str
    license: str
    attribution: str
    normalized_uncompressed_bytes: int | None


def verify_qualified_source(
    accepted_path: Path,
    manifest_path: Path,
    *,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
) -> SourceProof:
    """Verify the prior qualification manifest and accepted output identity."""

    manifest = _read_json(manifest_path, "qualification manifest")
    _require(manifest.get("atomic_completion") is True, "Qualification output is not complete")
    _require(manifest.get("manifest_schema_version") == "argentina-sepa-manifest-v1", "Unsupported qualification manifest")
    _require(manifest.get("schema_version") == QUALIFIED_SCHEMA_VERSION, "Qualification schema mismatch")
    _require(manifest.get("policy_version") == QUALIFIED_POLICY_VERSION, "Qualification policy mismatch")
    _require(manifest.get("provider") == PROVIDER_ID, "Qualification provider mismatch")
    _require(manifest.get("release_date") == expected_release_date, "Qualification release date mismatch")
    source = manifest.get("source")
    _require(isinstance(source, dict), "Qualification source proof is missing")
    _require(source.get("sha256") == expected_outer_sha256, "Qualification outer source SHA mismatch")
    _require(source.get("bytes") == expected_outer_bytes, "Qualification outer source byte count mismatch")
    # The prior accepted-output manifest predates this explicit field; absence
    # is treated as false because the manifest itself contains only generated
    # normalized files and the report separately records the raw-data boundary.
    _require(source.get("raw_data_committed", False) is False, "Qualification source claims raw data was committed")
    _require(source.get("license") == "Creative Commons Attribution 4.0", "Qualification source licence mismatch")
    attribution = _text(source.get("attribution"), "source.attribution", max_length=400)
    normalized_uncompressed: int | None = None
    storage = manifest.get("storage")
    if isinstance(storage, dict):
        value = storage.get("normalized_uncompressed_bytes")
        if isinstance(value, int) and value > 0:
            normalized_uncompressed = value
    files = manifest.get("files")
    _require(isinstance(files, dict), "Qualification file descriptors are missing")
    accepted_descriptor = files.get("accepted-observations.ndjson.gz")
    _require(isinstance(accepted_descriptor, dict), "Qualification accepted output descriptor is missing")
    accepted_path = accepted_path.resolve()
    _require(accepted_path.is_file(), f"Missing accepted observations: {accepted_path}")
    expected_bytes = accepted_descriptor.get("bytes")
    expected_hash = accepted_descriptor.get("sha256")
    _require(isinstance(expected_bytes, int) and expected_bytes > 0, "Qualification accepted byte count is invalid")
    _require(isinstance(expected_hash, str) and len(expected_hash) == 64, "Qualification accepted hash is invalid")
    actual_bytes = accepted_path.stat().st_size
    _require(actual_bytes == expected_bytes, "Accepted observation file size does not match its manifest")
    actual_hash = _sha256_file(accepted_path)
    _require(actual_hash == expected_hash, "Accepted observation file hash does not match its manifest")
    count = manifest.get("counts", {}).get("accepted_rows") if isinstance(manifest.get("counts"), dict) else None
    _require(isinstance(count, int) and count >= 0, "Qualification accepted row count is invalid")
    return SourceProof(
        accepted_path=accepted_path,
        accepted_sha256=actual_hash,
        accepted_bytes=actual_bytes,
        accepted_rows=count,
        manifest_sha256=_source_manifest_hash(manifest_path),
        outer_sha256=expected_outer_sha256,
        outer_bytes=expected_outer_bytes,
        release_date=expected_release_date,
        qualification_schema_version=manifest["schema_version"],
        qualification_policy_version=manifest["policy_version"],
        license=source["license"],
        attribution=attribution or "",
        normalized_uncompressed_bytes=normalized_uncompressed,
    )


class _JsonlGzipWriter:
    def __init__(self, path: Path) -> None:
        self.path = path
        self._raw = path.open("wb")
        self._gzip = gzip.GzipFile(fileobj=self._raw, mode="wb", filename="", mtime=0)
        self.sha256 = hashlib.sha256()
        self.records = 0
        self.uncompressed_bytes = 0

    def write(self, value: Mapping[str, Any]) -> None:
        data = _canonical_json(value)
        self._gzip.write(data)
        self.sha256.update(data)
        self.uncompressed_bytes += len(data)
        self.records += 1

    def close(self) -> dict[str, Any]:
        self._gzip.close()
        self._raw.close()
        return {
            "path": self.path.name,
            "compression": "gzip",
            "recordCount": self.records,
            "bytes": self.path.stat().st_size,
            "uncompressedBytes": self.uncompressed_bytes,
            "sha256": _sha256_file(self.path),
            "uncompressedSha256": self.sha256.hexdigest(),
        }


def _stable_key(prefix: str, *parts: str) -> str:
    return prefix + ":" + ":".join(parts)


def _value_json(value: Any) -> str:
    return _canonical_json(value).decode("utf-8").rstrip("\n")


def _ensure_db(db_path: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(str(db_path))
    connection.execute("PRAGMA journal_mode=OFF")
    connection.execute("PRAGMA synchronous=OFF")
    connection.execute("PRAGMA temp_store=FILE")
    connection.execute("PRAGMA cache_size=-32768")
    connection.executescript(
        """
        CREATE TABLE stores (
            store_key TEXT PRIMARY KEY,
            commerce_id TEXT NOT NULL,
            banner_id TEXT NOT NULL,
            store_id TEXT NOT NULL
        );
        CREATE TABLE store_values (
            store_key TEXT NOT NULL,
            field TEXT NOT NULL,
            value TEXT NOT NULL,
            PRIMARY KEY (store_key, field, value)
        );
        CREATE TABLE products (
            product_key TEXT PRIMARY KEY,
            commerce_id TEXT NOT NULL,
            provider_product_id TEXT NOT NULL,
            gtin TEXT,
            gtin_status TEXT NOT NULL
        );
        CREATE TABLE product_values (
            product_key TEXT NOT NULL,
            field TEXT NOT NULL,
            value TEXT NOT NULL,
            PRIMARY KEY (product_key, field, value)
        );
        CREATE TABLE offers (
            offer_key TEXT PRIMARY KEY,
            store_key TEXT NOT NULL,
            product_key TEXT NOT NULL,
            amount TEXT NOT NULL,
            reference_json TEXT,
            package_sha256 TEXT NOT NULL,
            source_row INTEGER NOT NULL,
            provider_update_time TEXT,
            release_date TEXT NOT NULL,
            freshness_status TEXT NOT NULL,
            availability TEXT NOT NULL
        );
        CREATE TABLE promotions (
            promotion_key TEXT PRIMARY KEY,
            offer_key TEXT NOT NULL,
            slot INTEGER NOT NULL,
            price_amount TEXT,
            price_raw TEXT,
            condition TEXT,
            eligibility TEXT NOT NULL
        );
        CREATE INDEX product_gtin_index ON products(gtin);
        CREATE INDEX offer_store_index ON offers(store_key, product_key);
        """
    )
    return connection


def _store_fields(store: Mapping[str, Any]) -> dict[str, str]:
    fields: dict[str, str] = {}
    mapping = {
        "name": "name",
        "type": "type",
        "street": "street",
        "number": "number",
        "postal_code": "postalCode",
        "locality": "locality",
        "province": "province",
        "latitude": "latitude",
        "longitude": "longitude",
        "geo_status": "geoStatus",
    }
    for source, target in mapping.items():
        value = store.get(source)
        if value is None:
            continue
        if source in {"latitude", "longitude"}:
            _require(isinstance(value, str), f"store.{source} must be text")
            fields[target] = _canonical_decimal(value, f"store.{source}")
        elif str(value).strip():
            fields[target] = str(value).strip()
    return fields


def _validate_store_evidence(store: Mapping[str, Any]) -> None:
    """Reject coordinates that could not safely participate in proximity."""

    geo_status = store.get("geo_status")
    _require(geo_status in {"VALID", "GEO_INCOMPLETE", "GEO_CONFLICTING"}, "Store geo status is invalid")
    lat = store.get("latitude")
    lon = store.get("longitude")
    if lat is not None:
        _require(isinstance(lat, str), "Store latitude is not exact text")
        latitude = Decimal(_canonical_decimal(lat, "store.latitude"))
        _require(Decimal("-56") <= latitude <= Decimal("-21"), "Store latitude is outside Argentina")
    if lon is not None:
        _require(isinstance(lon, str), "Store longitude is not exact text")
        longitude = Decimal(_canonical_decimal(lon, "store.longitude"))
        _require(Decimal("-74") <= longitude <= Decimal("-52"), "Store longitude is outside Argentina")
    if geo_status == "VALID":
        _require(lat is not None and lon is not None, "Valid store coordinates are incomplete")


def _product_fields(product: Mapping[str, Any], quantity: Any, quantity_status: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    name = product.get("name")
    brand = product.get("brand")
    if isinstance(name, str) and name.strip():
        fields["name"] = name.strip()
    if isinstance(brand, str) and brand.strip():
        fields["brand"] = brand.strip()
    fields["quantityStatus"] = quantity_status
    if isinstance(quantity, dict):
        unit = _text(quantity.get("unit"), "quantity.unit", max_length=32)
        value = _canonical_decimal(quantity.get("value"), "quantity.value", positive=True)
        fields["quantity"] = _value_json({"unit": unit, "value": value})
    return fields


def _validate_row(row: Mapping[str, Any], source: SourceProof) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], list[dict[str, Any]]]:
    _require(row.get("schema_version") == QUALIFIED_SCHEMA_VERSION, "Accepted row schema mismatch")
    _require(row.get("provider") == PROVIDER_ID, "Accepted row provider mismatch")
    source_data = row.get("source")
    product = row.get("product")
    offer = row.get("offer")
    store = row.get("store")
    _require(isinstance(source_data, dict) and isinstance(product, dict) and isinstance(offer, dict) and isinstance(store, dict), "Accepted row structure is invalid")
    _require(source_data.get("outer_sha256") == source.outer_sha256, "Accepted row outer source mismatch")
    _require(source_data.get("release_date") == source.release_date, "Accepted row release mismatch")
    _require(row.get("quantity_status") in {"KNOWN", "UNKNOWN"}, "Accepted row quantity status is invalid")
    _require(store.get("province") == REGION_PROVINCE_CODE, "Non-CABA row reached regional selector")
    _validate_store_evidence(store)
    commerce_id = _text(source_data.get("commerce_id"), "source.commerce_id", max_length=64)
    banner_id = _text(source_data.get("banner_id"), "source.banner_id", max_length=64)
    store_id = _text(source_data.get("store_id"), "source.store_id", max_length=128)
    provider_product_id = _text(product.get("provider_product_id_raw"), "product.provider_product_id_raw", max_length=160)
    _require(source_data.get("product_row") is not None and int(source_data["product_row"]) > 0, "Accepted row source line is invalid")
    _require(store.get("commerce_id") == commerce_id and store.get("banner_id") == banner_id and store.get("store_id") == store_id, "Accepted store key disagrees with source key")
    _require(offer.get("freshness_status") == "FRESH", "Stale/unknown observation reached regional artifact")
    _require(offer.get("availability") == AVAILABILITY, "Accepted availability boundary changed")
    list_price = offer.get("list_price")
    _require(isinstance(list_price, dict) and list_price.get("currency") == CURRENCY, "Accepted price currency is not ARS")
    amount = _canonical_decimal(list_price.get("amount"), "offer.list_price.amount", positive=True)
    package_sha = _text(source_data.get("nested_package_sha256"), "source.nested_package_sha256", max_length=64)
    _require(package_sha is not None and len(package_sha) == 64 and all(char in "0123456789abcdef" for char in package_sha), "Nested package provenance is invalid")
    store_key = _stable_key("ar-sepa-store", commerce_id or "", banner_id or "", store_id or "")
    product_key = _stable_key("ar-sepa-product", commerce_id or "", provider_product_id or "")
    reference = offer.get("reference_price")
    reference_json: str | None = None
    if reference is not None:
        _require(isinstance(reference, dict), "Reference price is malformed")
        reference_value = reference.get("amount_ars")
        if reference_value is not None:
            reference_value = _canonical_decimal(reference_value, "reference_price.amount_ars", positive=True)
        reference_json = _value_json(
            {
                "amount": reference_value,
                "currency": CURRENCY,
                "quantityRaw": reference.get("quantity_raw"),
                "unitRaw": reference.get("unit_raw"),
                "semanticRole": "reference_price_not_current_offer",
            }
        )
    promotions = offer.get("promotions") or []
    _require(isinstance(promotions, list), "Offer promotions are malformed")
    provider_update_time = offer.get("provider_update_time")
    if provider_update_time is not None:
        provider_update_time = _canonical_timestamp(provider_update_time, "offer.provider_update_time")
    # Include every evidence-bearing field in the key.  A repeated source line
    # is allowed to remain a separate auditable offer; a collision must never
    # silently replace a different promotion/reference claim.
    offer_basis = {
        "package": package_sha,
        "product": product_key,
        "store": store_key,
        "amount": amount,
        "row": int(source_data["product_row"]),
        "reference": reference_json,
        "providerUpdateTime": provider_update_time,
        "promotions": promotions,
    }
    offer_key = "ar-sepa-offer:" + hashlib.sha256(_canonical_json(offer_basis)).hexdigest()
    return (
        {
            "store_key": store_key,
            "commerce_id": commerce_id,
            "banner_id": banner_id,
            "store_id": store_id,
            "fields": _store_fields(store),
        },
        {
            "product_key": product_key,
            "commerce_id": commerce_id,
            "provider_product_id": provider_product_id,
            "gtin": provider_product_id if _valid_gtin(provider_product_id or "") else None,
            "gtin_status": "VALID" if _valid_gtin(provider_product_id or "") else "INVALID_OR_NOT_GTIN",
            "fields": _product_fields(product, row.get("quantity"), row["quantity_status"]),
        },
        {
            "offer_key": offer_key,
            "store_key": store_key,
            "product_key": product_key,
            "amount": amount,
            "reference_json": reference_json,
            "package_sha256": package_sha,
            "source_row": int(source_data["product_row"]),
            "provider_update_time": provider_update_time,
            "release_date": source.release_date,
            "freshness_status": "FRESH",
            "availability": AVAILABILITY,
        },
        promotions,
    )


def _insert_row(connection: sqlite3.Connection, store: Mapping[str, Any], product: Mapping[str, Any], offer: Mapping[str, Any], promotions: Iterable[Mapping[str, Any]]) -> None:
    connection.execute(
        "INSERT OR IGNORE INTO stores(store_key,commerce_id,banner_id,store_id) VALUES(?,?,?,?)",
        (store["store_key"], store["commerce_id"], store["banner_id"], store["store_id"]),
    )
    connection.executemany(
        "INSERT OR IGNORE INTO store_values(store_key,field,value) VALUES(?,?,?)",
        [(store["store_key"], field, value) for field, value in store["fields"].items()],
    )
    connection.execute(
        "INSERT OR IGNORE INTO products(product_key,commerce_id,provider_product_id,gtin,gtin_status) VALUES(?,?,?,?,?)",
        (product["product_key"], product["commerce_id"], product["provider_product_id"], product["gtin"], product["gtin_status"]),
    )
    connection.executemany(
        "INSERT OR IGNORE INTO product_values(product_key,field,value) VALUES(?,?,?)",
        [(product["product_key"], field, value) for field, value in product["fields"].items()],
    )
    connection.execute(
        "INSERT OR REPLACE INTO offers(offer_key,store_key,product_key,amount,reference_json,package_sha256,source_row,provider_update_time,release_date,freshness_status,availability) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        tuple(offer[field] for field in ("offer_key", "store_key", "product_key", "amount", "reference_json", "package_sha256", "source_row", "provider_update_time", "release_date", "freshness_status", "availability")),
    )
    for promo in promotions:
        _require(isinstance(promo, dict), "Promotion must be an object")
        slot = promo.get("slot")
        _require(isinstance(slot, int) and slot in {1, 2}, "Promotion slot is invalid")
        price_amount = promo.get("price_ars")
        if price_amount is not None:
            price_amount = _canonical_decimal(price_amount, "promotion.price_ars", positive=True)
        condition = promo.get("condition")
        condition = condition.strip() if isinstance(condition, str) and condition.strip() else None
        price_raw = promo.get("price_raw")
        price_raw = price_raw.strip() if isinstance(price_raw, str) and price_raw.strip() else None
        _require(promo.get("eligibility") == "UNKNOWN", "Promotion eligibility must remain UNKNOWN")
        promotion_key = f"{offer['offer_key']}:promotion:{slot}"
        connection.execute(
            "INSERT OR REPLACE INTO promotions(promotion_key,offer_key,slot,price_amount,price_raw,condition,eligibility) VALUES(?,?,?,?,?,?,?)",
            (promotion_key, offer["offer_key"], slot, price_amount, price_raw, condition, "UNKNOWN"),
        )


def _field_values(connection: sqlite3.Connection, table: str, key_field: str, key: str) -> dict[str, list[str]]:
    rows = connection.execute(
        f"SELECT field,value FROM {table} WHERE {key_field}=? ORDER BY field,value", (key,)
    )
    values: dict[str, list[str]] = {}
    for field, value in rows:
        values.setdefault(field, []).append(value)
    return values


def _canonical_field(values: Mapping[str, list[str]], field: str) -> str | None:
    entries = values.get(field, [])
    return entries[0] if entries else None


def _metadata_status(values: Mapping[str, list[str]]) -> str:
    return "CONFLICTING" if any(len(items) > 1 for items in values.values()) else "CONSISTENT"


def _write_store_records(connection: sqlite3.Connection, path: Path) -> dict[str, Any]:
    writer = _JsonlGzipWriter(path)
    try:
        for row in connection.execute("SELECT store_key,commerce_id,banner_id,store_id FROM stores ORDER BY store_key"):
            key, commerce, banner, store_id = row
            values = _field_values(connection, "store_values", "store_key", key)
            coordinate_conflict = len(values.get("latitude", [])) > 1 or len(values.get("longitude", [])) > 1 or len(values.get("geoStatus", [])) > 1
            geo_status = "GEO_CONFLICTING" if coordinate_conflict else _canonical_field(values, "geoStatus")
            record: dict[str, Any] = {
                "storeKey": key,
                "commerceId": commerce,
                "bannerId": banner,
                "storeId": store_id,
                "name": _canonical_field(values, "name"),
                "type": _canonical_field(values, "type"),
                "address": {
                    "street": _canonical_field(values, "street"),
                    "number": _canonical_field(values, "number"),
                    "postalCode": _canonical_field(values, "postalCode"),
                },
                "locality": _canonical_field(values, "locality"),
                "province": _canonical_field(values, "province"),
                "latitude": None if coordinate_conflict else _canonical_field(values, "latitude"),
                "longitude": None if coordinate_conflict else _canonical_field(values, "longitude"),
                "geoStatus": geo_status,
                "metadataStatus": _metadata_status(values),
            }
            variants = {field: entries for field, entries in values.items() if len(entries) > 1}
            if variants:
                record["metadataVariants"] = variants
            writer.write(record)
    finally:
        descriptor = writer.close()
    return descriptor


def _write_product_records(connection: sqlite3.Connection, path: Path) -> tuple[dict[str, Any], int, int]:
    writer = _JsonlGzipWriter(path)
    product_count = 0
    try:
        for key, commerce, provider_id, gtin, gtin_status in connection.execute(
            "SELECT product_key,commerce_id,provider_product_id,gtin,gtin_status FROM products ORDER BY product_key"
        ):
            values = _field_values(connection, "product_values", "product_key", key)
            quantity_values = values.get("quantity", [])
            quantity_status_values = set(values.get("quantityStatus", []))
            quantity_conflict = len(quantity_values) > 1 or len(quantity_status_values) > 1
            quantity_value = quantity_values[0] if len(quantity_values) == 1 else None
            quantity = json.loads(quantity_value) if quantity_value is not None and not quantity_conflict else None
            quantity_status = "CONFLICTING" if quantity_conflict else ("KNOWN" if quantity is not None else "UNKNOWN")
            record: dict[str, Any] = {
                "productEvidenceKey": key,
                "commerceId": commerce,
                "providerProductId": provider_id,
                "gtin": gtin,
                "gtinStatus": gtin_status,
                "name": _canonical_field(values, "name"),
                "brand": _canonical_field(values, "brand"),
                "quantity": quantity,
                "quantityStatus": quantity_status,
                "metadataStatus": _metadata_status(values),
            }
            variants = {field: entries for field, entries in values.items() if len(entries) > 1}
            if variants:
                record["metadataVariants"] = variants
            writer.write(record)
            product_count += 1
    finally:
        descriptor = writer.close()
    valid_gtin_count = connection.execute("SELECT COUNT(DISTINCT gtin) FROM products WHERE gtin IS NOT NULL").fetchone()[0]
    return descriptor, product_count, valid_gtin_count


def _write_offer_records(connection: sqlite3.Connection, path: Path) -> dict[str, Any]:
    writer = _JsonlGzipWriter(path)
    try:
        query = """
            SELECT offer_key,store_key,product_key,amount,reference_json,package_sha256,
                   source_row,provider_update_time,release_date,freshness_status,availability
            FROM offers ORDER BY store_key,product_key,package_sha256,source_row,offer_key
        """
        for row in connection.execute(query):
            (
                offer_key, store_key, product_key, amount, reference_json,
                package_sha, source_row, provider_update, release_date,
                freshness, availability,
            ) = row
            record: dict[str, Any] = {
                "offerKey": offer_key,
                "storeKey": store_key,
                "productEvidenceKey": product_key,
                "listPrice": {"amount": amount, "currency": CURRENCY},
                "referencePrice": json.loads(reference_json) if reference_json is not None else None,
                "releaseDate": release_date,
                "providerUpdateTime": provider_update,
                "freshnessStatus": freshness,
                "packageProvenanceSha256": package_sha,
                "sourceRow": source_row,
                "availability": availability,
            }
            writer.write(record)
    finally:
        descriptor = writer.close()
    return descriptor


def _write_promotion_records(connection: sqlite3.Connection, path: Path) -> dict[str, Any]:
    writer = _JsonlGzipWriter(path)
    try:
        for row in connection.execute(
            "SELECT promotion_key,offer_key,slot,price_amount,price_raw,condition,eligibility FROM promotions ORDER BY offer_key,slot,promotion_key"
        ):
            key, offer_key, slot, amount, raw, condition, eligibility = row
            writer.write(
                {
                    "promotionKey": key,
                    "offerKey": offer_key,
                    "slot": slot,
                    "price": {"amount": amount, "currency": CURRENCY} if amount is not None else None,
                    "priceRaw": raw,
                    "condition": condition,
                    "eligibility": eligibility,
                }
            )
    finally:
        descriptor = writer.close()
    return descriptor


def _write_release(path: Path, proof: SourceProof, generated_at: str, package_hashes: list[str]) -> dict[str, Any]:
    release = {
        "artifactSchemaVersion": REGIONAL_SCHEMA_VERSION,
        "policyVersion": REGIONAL_POLICY_VERSION,
        "region": {"id": REGION_ID, "displayName": REGION_DISPLAY_NAME, "selector": {"field": "store.province", "operator": "EXACT", "value": REGION_PROVINCE_CODE}},
        "source": {
            "provider": PROVIDER_ID,
            "releaseDate": proof.release_date,
            "outerSha256": proof.outer_sha256,
            "qualificationManifestSha256": proof.manifest_sha256,
            "qualificationSchemaVersion": proof.qualification_schema_version,
            "qualificationPolicyVersion": proof.qualification_policy_version,
            "license": proof.license,
            "attribution": proof.attribution,
            "nestedPackageHashes": sorted(set(package_hashes)),
        },
        "boundaries": {
            "availability": AVAILABILITY,
            "deliveryPickup": DELIVERY_PICKUP,
            "distance": "TRUSTED_COORDINATES_ONLY",
            "rawProviderDataCommitted": False,
        },
        "generatedAt": generated_at,
    }
    path.write_bytes(_canonical_json(release))
    return {
        "path": path.name,
        "compression": "none",
        "recordCount": 1,
        "bytes": path.stat().st_size,
        "uncompressedBytes": path.stat().st_size,
        "sha256": _sha256_file(path),
        "uncompressedSha256": _sha256_file(path),
    }


def build_regional_snapshot(
    accepted_path: Path,
    source_manifest_path: Path,
    output_dir: Path,
    *,
    generated_at: str,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
    qualification_report_path: Path | None = None,
) -> dict[str, Any]:
    """Build one complete CABA artifact and return its manifest."""

    generated_at = _canonical_timestamp(generated_at, "generated_at")
    proof = verify_qualified_source(
        accepted_path,
        source_manifest_path,
        expected_outer_sha256=expected_outer_sha256,
        expected_outer_bytes=expected_outer_bytes,
        expected_release_date=expected_release_date,
    )
    if qualification_report_path is None:
        candidate = source_manifest_path.parent.parent.parent / "ARGENTINA_SEPA_QUALIFICATION.json"
        qualification_report_path = candidate if candidate.is_file() else None
    # The canonical accepted-output manifest is hashed strictly above.  The
    # human-readable qualification report is provenance only and may retain
    # its repository formatting, so hash its exact bytes without rewriting it.
    qualification_report_hash = _sha256_file(qualification_report_path) if qualification_report_path is not None else None
    normalized_size = _report_normalized_size(qualification_report_path, proof)
    proof = replace(proof, normalized_uncompressed_bytes=normalized_size)
    output_dir = output_dir.resolve()
    _require(not output_dir.exists(), f"Refusing to overwrite existing regional artifact: {output_dir}")
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    partial = Path(tempfile.mkdtemp(prefix=f".{output_dir.name}.partial-", dir=str(output_dir.parent)))
    scratch_fd, scratch_name = tempfile.mkstemp(prefix="argentina-sepa-caba-", suffix=".sqlite", dir=str(output_dir.parent))
    os.close(scratch_fd)
    scratch = Path(scratch_name)
    connection = _ensure_db(scratch)
    selected_rows = 0
    input_rows = 0
    malformed_rows = 0
    package_hashes: set[str] = set()
    try:
        with gzip.open(proof.accepted_path, "rt", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                input_rows += 1
                try:
                    raw = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise RegionalSnapshotError(f"Accepted source line {line_number} is not JSON") from exc
                if not isinstance(raw, dict):
                    raise RegionalSnapshotError(f"Accepted source line {line_number} is not an object")
                store = raw.get("store")
                if not isinstance(store, dict) or store.get("province") != REGION_PROVINCE_CODE:
                    continue
                try:
                    store_record, product_record, offer_record, promotions = _validate_row(raw, proof)
                    _insert_row(connection, store_record, product_record, offer_record, promotions)
                except (RegionalSnapshotError, TypeError, ValueError) as exc:
                    raise RegionalSnapshotError(f"CABA row {line_number} failed validation: {exc}") from exc
                selected_rows += 1
                package_hashes.add(offer_record["package_sha256"])
                if selected_rows % 10_000 == 0:
                    connection.commit()
        connection.commit()
        _require(input_rows == proof.accepted_rows, f"Accepted row count mismatch: read {input_rows}, manifest {proof.accepted_rows}")
        file_descriptors: dict[str, Any] = {}
        file_descriptors["release.json"] = _write_release(partial / "release.json", proof, generated_at, sorted(package_hashes))
        file_descriptors["stores.jsonl.gz"] = _write_store_records(connection, partial / "stores.jsonl.gz")
        product_descriptor, product_count, valid_gtin_count = _write_product_records(connection, partial / "products.jsonl.gz")
        file_descriptors["products.jsonl.gz"] = product_descriptor
        file_descriptors["offers.jsonl.gz"] = _write_offer_records(connection, partial / "offers.jsonl.gz")
        file_descriptors["promotions.jsonl.gz"] = _write_promotion_records(connection, partial / "promotions.jsonl.gz")
        store_count = connection.execute("SELECT COUNT(*) FROM stores").fetchone()[0]
        offer_count = connection.execute("SELECT COUNT(*) FROM offers").fetchone()[0]
        promotion_count = connection.execute("SELECT COUNT(*) FROM promotions").fetchone()[0]
        exact_cross_retailer = connection.execute(
            "SELECT COUNT(*) FROM (SELECT gtin FROM products WHERE gtin IS NOT NULL GROUP BY gtin HAVING COUNT(DISTINCT commerce_id) >= 2)"
        ).fetchone()[0]
        compact_uncompressed = sum(int(item["uncompressedBytes"]) for item in file_descriptors.values())
        compact_compressed = sum(int(item["bytes"]) for item in file_descriptors.values())
        reduction = None
        if proof.normalized_uncompressed_bytes:
            reduction = format(Decimal(proof.normalized_uncompressed_bytes) / Decimal(compact_uncompressed), ".6f")
        manifest = {
            "artifactSchemaVersion": REGIONAL_SCHEMA_VERSION,
            "policyVersion": REGIONAL_POLICY_VERSION,
            "atomicCompletion": True,
            "completionState": "COMPLETE",
            "region": {"id": REGION_ID, "displayName": REGION_DISPLAY_NAME, "selector": {"field": "store.province", "operator": "EXACT", "value": REGION_PROVINCE_CODE}},
            "generatedAt": generated_at,
            "source": {
                "provider": PROVIDER_ID,
                "releaseDate": proof.release_date,
                "outerSha256": proof.outer_sha256,
                "outerBytes": proof.outer_bytes,
                "acceptedObservationsSha256": proof.accepted_sha256,
                "acceptedObservationsBytes": proof.accepted_bytes,
                "acceptedObservationsRows": proof.accepted_rows,
                "qualificationManifestSha256": proof.manifest_sha256,
                "qualificationReportSha256": qualification_report_hash,
                "qualificationSchemaVersion": proof.qualification_schema_version,
                "qualificationPolicyVersion": proof.qualification_policy_version,
                "license": proof.license,
                "attribution": proof.attribution,
                "rawProviderDataCommitted": False,
            },
            "regionSelector": {"provinceCode": REGION_PROVINCE_CODE, "matching": "EXACT_NORMALIZED_SOURCE_FIELD"},
            "counts": {
                "inputAcceptedObservations": input_rows,
                "selectedAcceptedObservations": selected_rows,
                "stores": store_count,
                "productEvidenceRecords": product_count,
                "validGtins": valid_gtin_count,
                "exactCrossRetailerGtins": exact_cross_retailer,
                "offers": offer_count,
                "promotions": promotion_count,
            },
            "boundaries": {
                "currency": CURRENCY,
                "availability": AVAILABILITY,
                "deliveryPickup": DELIVERY_PICKUP,
                "distance": "TRUSTED_COORDINATES_ONLY_NO_ROUTING",
                "rawProviderDataCommitted": False,
            },
            "size": {
                "inputAcceptedGzipBytes": proof.accepted_bytes,
                "inputNormalizedUncompressedBytes": proof.normalized_uncompressed_bytes,
                "compactUncompressedBytes": compact_uncompressed,
                "compactCompressedBytes": compact_compressed,
                "bytesPerOfferUncompressed": format(Decimal(compact_uncompressed) / Decimal(max(offer_count, 1)), ".6f"),
                "compactionRatio": reduction,
            },
            "performance": {
                # Wall-clock runtime is deliberately not embedded in the
                # deterministic artifact.  The qualification report records
                # an operator measurement separately when a full run is made.
                "elapsedSeconds": None,
                "memoryBounded": True,
                "peakRssBytes": None,
                "peakRssNote": "Not measured by the standard Windows Python runtime; aggregation uses disk-backed SQLite and one streamed row.",
            },
            "packageProvenance": sorted(package_hashes),
            "files": file_descriptors,
        }
        manifest_path = partial / "manifest.json"
        manifest_path.write_bytes(_canonical_json(manifest))
        manifest_hash = _sha256_file(manifest_path)
        (partial / "manifest.sha256").write_text(f"{manifest_hash}  manifest.json\n", encoding="ascii")
        integrity = {
            "manifestSha256": manifest_hash,
            "schemaVersion": REGIONAL_SCHEMA_VERSION,
            "atomicCompletion": True,
            "fileNames": sorted(file_descriptors),
        }
        (partial / "integrity.json").write_bytes(_canonical_json(integrity))
        # The manifest is written only after every table is complete.  Renaming
        # the directory is the sole publication step; an interrupted build can
        # leave a .partial-* directory but never a complete-looking target.
        os.replace(partial, output_dir)
        return manifest
    except Exception:
        shutil.rmtree(partial, ignore_errors=True)
        raise
    finally:
        connection.close()
        try:
            scratch.unlink()
        except FileNotFoundError:
            pass


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accepted", required=True, type=Path)
    parser.add_argument("--source-manifest", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--generated-at", required=True, help="Explicit ISO-8601 timestamp; no wall clock is read")
    parser.add_argument("--qualification-report", type=Path)
    parser.add_argument("--expected-outer-sha256", default=EXPECTED_OUTER_SHA256)
    parser.add_argument("--expected-outer-bytes", default=EXPECTED_OUTER_BYTES, type=int)
    parser.add_argument("--expected-release-date", default=RELEASE_DATE)
    args = parser.parse_args(argv)
    try:
        manifest = build_regional_snapshot(
            args.accepted,
            args.source_manifest,
            args.output_dir,
            generated_at=args.generated_at,
            expected_outer_sha256=args.expected_outer_sha256,
            expected_outer_bytes=args.expected_outer_bytes,
            expected_release_date=args.expected_release_date,
            qualification_report_path=args.qualification_report,
        )
    except (RegionalSnapshotError, OSError, sqlite3.Error) as exc:
        print(f"regional snapshot build failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps({"regionId": REGION_ID, "offers": manifest["counts"]["offers"], "path": str(args.output_dir)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

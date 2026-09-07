#!/usr/bin/env python3
"""Build deterministic, lossless Argentina SEPA mobile shards offline.

The input is the previously qualified normalized observation stream.  Each
exact registered province is written to an indexed SQLite shard whose offer
rows use local integer references to deduplicated stores, products, money,
package provenance, update times and reference prices.  The compressed SQLite
file is a distribution artifact; the national index is the only cross-shard
authority.  No network, Android asset, or raw provider data is used here.
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
from collections import OrderedDict
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable, Mapping

try:
    from tools.build_argentina_sepa_regional_snapshot import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CABA_REGION,
        CURRENCY,
        DELIVERY_PICKUP,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        QUALIFIED_POLICY_VERSION,
        QUALIFIED_SCHEMA_VERSION,
        REGIONS_BY_PROVINCE,
        RELEASE_DATE,
        RegionSpec,
        RegionalSnapshotError,
        _canonical_decimal,
        _canonical_json,
        _canonical_timestamp,
        _validate_row,
        verify_qualified_source,
    )
except ModuleNotFoundError:  # direct ``python tools/build_...py`` invocation
    from build_argentina_sepa_regional_snapshot import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CABA_REGION,
        CURRENCY,
        DELIVERY_PICKUP,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        QUALIFIED_POLICY_VERSION,
        QUALIFIED_SCHEMA_VERSION,
        REGIONS_BY_PROVINCE,
        RELEASE_DATE,
        RegionSpec,
        RegionalSnapshotError,
        _canonical_decimal,
        _canonical_json,
        _canonical_timestamp,
        _validate_row,
        verify_qualified_source,
    )


MOBILE_SHARD_SCHEMA_VERSION = "argentina-sepa-mobile-shard-v1"
MOBILE_SHARD_POLICY_VERSION = "argentina-sepa-mobile-policy-v1"
NATIONAL_INDEX_SCHEMA_VERSION = "argentina-sepa-national-index-v1"
NATIONAL_INDEX_POLICY_VERSION = "argentina-sepa-national-policy-v1"
MOBILE_COMPATIBILITY_VERSION = "argentina-sepa-mobile-contract-v1"
MOBILE_SHARD_FILE = "shard.sqlite.gz"

_STORE_FIELDS = (
    "name",
    "type",
    "street",
    "number",
    "postalCode",
    "locality",
    "province",
    "latitude",
    "longitude",
    "geoStatus",
)
_PRODUCT_FIELDS = ("name", "brand", "quantity", "quantityStatus")


class NationalShardError(RegionalSnapshotError):
    """The national index or a mobile shard failed a deterministic gate."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise NationalShardError(message)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _canonical_optional_text(value: Any, field: str, max_length: int = 512) -> str | None:
    if value is None:
        return None
    _require(isinstance(value, str), f"{field} must be text")
    value = value.strip()
    _require(len(value) <= max_length, f"{field} exceeds {max_length} characters")
    return value or None


def _stable_value(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _gzip_deterministic(source: Path, destination: Path) -> None:
    with source.open("rb") as source_handle, destination.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0, compresslevel=9) as stream:
            for block in iter(lambda: source_handle.read(1024 * 1024), b""):
                stream.write(block)


def _province_unpublished_bucket(value: Any) -> tuple[str, Any] | None:
    """Return an explicit bucket for a province value not in the registry."""

    if value is None or not isinstance(value, str) or not value:
        return "UNKNOWN", None
    if value in REGIONS_BY_PROVINCE:
        return None
    return "NONSTANDARD", value


class _LruIds:
    """Small bounded cache over a deterministic SQLite dictionary."""

    def __init__(self, limit: int = 8192) -> None:
        self.limit = limit
        self.values: OrderedDict[Any, Any] = OrderedDict()

    def get(self, key: Any) -> Any:
        value = self.values.get(key)
        if value is not None:
            self.values.move_to_end(key)
        return value

    def put(self, key: Any, value: Any) -> None:
        self.values[key] = value
        self.values.move_to_end(key)
        while len(self.values) > self.limit:
            self.values.popitem(last=False)


@dataclass
class ShardStats:
    selected_rows: int = 0
    offers: int = 0
    products: int = 0
    stores: int = 0
    promotions: int = 0


class MobileShardWriter:
    """One province shard backed by a temporary SQLite database."""

    def __init__(
        self,
        region: RegionSpec,
        raw_path: Path,
        *,
        generated_at: str,
        source: Mapping[str, Any],
        product_callback: Callable[[str, str, str], None],
    ) -> None:
        self.region = region
        self.raw_path = raw_path
        self.generated_at = generated_at
        self.source = source
        self.product_callback = product_callback
        self.connection = sqlite3.connect(str(raw_path))
        self.connection.execute("PRAGMA journal_mode=OFF")
        self.connection.execute("PRAGMA synchronous=OFF")
        self.connection.execute("PRAGMA locking_mode=EXCLUSIVE")
        self.connection.execute("PRAGMA temp_store=FILE")
        self.connection.execute("PRAGMA cache_size=-32768")
        self.connection.execute("PRAGMA foreign_keys=ON")
        self.connection.execute("PRAGMA page_size=4096")
        self._create_schema()
        self.stats = ShardStats()
        self._next_offer_id = 1
        self._store_cache = _LruIds(32768)
        self._product_cache = _LruIds(32768)
        self._money_cache = _LruIds(8192)
        self._package_cache = _LruIds(64)
        self._time_cache = _LruIds(256)
        self._reference_cache = _LruIds(4096)

    def _create_schema(self) -> None:
        self.connection.executescript(
            """
            CREATE TABLE metadata (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            ) WITHOUT ROWID;
            CREATE TABLE money (
                money_id INTEGER PRIMARY KEY,
                amount TEXT NOT NULL UNIQUE
            );
            CREATE TABLE packages (
                package_id INTEGER PRIMARY KEY,
                sha256 TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL
            );
            CREATE TABLE package_variants (
                package_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                PRIMARY KEY (package_id, name)
            ) WITHOUT ROWID;
            CREATE TABLE provider_times (
                time_id INTEGER PRIMARY KEY,
                value TEXT NOT NULL UNIQUE
            );
            CREATE TABLE reference_prices (
                reference_id INTEGER PRIMARY KEY,
                reference_key TEXT NOT NULL UNIQUE,
                amount_id INTEGER,
                quantity_raw TEXT,
                unit_raw TEXT,
                semantic_role TEXT NOT NULL,
                CHECK (semantic_role='reference_price_not_current_offer')
            );
            CREATE TABLE stores (
                store_id INTEGER PRIMARY KEY,
                commerce_id TEXT NOT NULL,
                banner_id TEXT NOT NULL,
                provider_store_id TEXT NOT NULL,
                name TEXT,
                type TEXT,
                street TEXT,
                number TEXT,
                postal_code TEXT,
                locality TEXT,
                province TEXT NOT NULL,
                latitude TEXT,
                longitude TEXT,
                geo_status TEXT NOT NULL,
                metadata_status TEXT NOT NULL,
                UNIQUE (commerce_id, banner_id, provider_store_id)
            );
            CREATE TABLE store_variants (
                store_id INTEGER NOT NULL,
                field TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY (store_id, field, value)
            ) WITHOUT ROWID;
            CREATE TABLE products (
                product_id INTEGER PRIMARY KEY,
                commerce_id TEXT NOT NULL,
                provider_product_id TEXT NOT NULL,
                gtin TEXT,
                gtin_status TEXT NOT NULL,
                name TEXT,
                brand TEXT,
                quantity_json TEXT,
                quantity_status TEXT NOT NULL,
                metadata_status TEXT NOT NULL,
                UNIQUE (commerce_id, provider_product_id)
            );
            CREATE TABLE product_variants (
                product_id INTEGER NOT NULL,
                field TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY (product_id, field, value)
            ) WITHOUT ROWID;
            CREATE TABLE offers (
                offer_id INTEGER PRIMARY KEY,
                store_id INTEGER NOT NULL REFERENCES stores(store_id),
                product_id INTEGER NOT NULL REFERENCES products(product_id),
                list_price_id INTEGER NOT NULL REFERENCES money(money_id),
                reference_price_id INTEGER REFERENCES reference_prices(reference_id),
                package_id INTEGER NOT NULL REFERENCES packages(package_id),
                source_row INTEGER NOT NULL,
                provider_update_time_id INTEGER REFERENCES provider_times(time_id),
                release_date TEXT NOT NULL,
                freshness_status TEXT NOT NULL,
                availability TEXT NOT NULL
            );
            CREATE TABLE promotions (
                offer_id INTEGER NOT NULL REFERENCES offers(offer_id),
                slot INTEGER NOT NULL,
                price_id INTEGER REFERENCES money(money_id),
                price_raw TEXT,
                condition TEXT,
                eligibility TEXT NOT NULL,
                PRIMARY KEY (offer_id, slot)
            ) WITHOUT ROWID;
            """
        )
        metadata = {
            "schemaVersion": MOBILE_SHARD_SCHEMA_VERSION,
            "policyVersion": MOBILE_SHARD_POLICY_VERSION,
            "compatibilityVersion": MOBILE_COMPATIBILITY_VERSION,
            "regionId": self.region.region_id,
            "regionDisplayName": self.region.display_name,
            "provinceCode": self.region.province_code,
            "releaseDate": self.source["releaseDate"],
            "outerSha256": self.source["outerSha256"],
            "currency": CURRENCY,
            "availability": AVAILABILITY,
            "deliveryPickup": DELIVERY_PICKUP,
            "rawProviderDataCommitted": "false",
            "generatedAt": self.generated_at,
        }
        self.connection.executemany("INSERT INTO metadata(key,value) VALUES(?,?)", metadata.items())
        self.connection.commit()

    @staticmethod
    def _store_fields(record: Mapping[str, Any]) -> dict[str, str | None]:
        fields = record["fields"]
        return {field: fields.get(field) for field in _STORE_FIELDS}

    @staticmethod
    def _product_fields(record: Mapping[str, Any], raw_quantity: Any) -> dict[str, str | None]:
        fields = record["fields"]
        quantity = fields.get("quantity")
        if raw_quantity is not None and isinstance(raw_quantity, dict):
            quantity = _stable_value(raw_quantity)
        return {
            "name": fields.get("name"),
            "brand": fields.get("brand"),
            "quantity": quantity,
            "quantityStatus": fields.get("quantityStatus"),
        }

    def _record_variants(
        self,
        table: str,
        item_id: int,
        fields: Mapping[str, str | None],
        previous: Mapping[str, str | None],
    ) -> bool:
        conflict = False
        for field in fields:
            current = fields.get(field)
            old = previous.get(field)
            if current == old:
                continue
            conflict = True
            variant_table = "store_variants" if table == "stores" else "product_variants"
            for value in (old, current):
                if value is not None:
                    self.connection.execute(
                        f"INSERT OR IGNORE INTO {variant_table}(" +
                        ("store_id" if table == "stores" else "product_id") +
                        ",field,value) VALUES(?,?,?)",
                        (item_id, field, value),
                    )
        return conflict

    def _ensure_store(self, record: Mapping[str, Any]) -> int:
        key = (record["commerce_id"], record["banner_id"], record["store_id"])
        fields = self._store_fields(record)
        cached = self._store_cache.get(key)
        if cached is not None:
            store_id, previous = cached
        else:
            row = self.connection.execute(
                "SELECT store_id,name,type,street,number,postal_code,locality,province,latitude,longitude,geo_status FROM stores WHERE commerce_id=? AND banner_id=? AND provider_store_id=?",
                key,
            ).fetchone()
            if row is None:
                cursor = self.connection.execute(
                    "INSERT INTO stores(commerce_id,banner_id,provider_store_id,name,type,street,number,postal_code,locality,province,latitude,longitude,geo_status,metadata_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (key[0], key[1], key[2], fields["name"], fields["type"], fields["street"], fields["number"], fields["postalCode"], fields["locality"], fields["province"], fields["latitude"], fields["longitude"], fields["geoStatus"], "CONSISTENT"),
                )
                store_id = int(cursor.lastrowid)
                previous = fields
                self.stats.stores += 1
            else:
                store_id = int(row[0])
                previous = dict(zip(("name", "type", "street", "number", "postalCode", "locality", "province", "latitude", "longitude", "geoStatus"), row[1:]))
            self._store_cache.put(key, (store_id, previous))
        conflict = self._record_variants("stores", store_id, fields, previous)
        if conflict:
            coordinate_conflict = any(fields.get(name) != previous.get(name) for name in ("latitude", "longitude", "geoStatus"))
            self.connection.execute("UPDATE stores SET metadata_status='CONFLICTING' WHERE store_id=?", (store_id,))
            if coordinate_conflict:
                self.connection.execute("UPDATE stores SET geo_status='GEO_CONFLICTING',latitude=NULL,longitude=NULL WHERE store_id=?", (store_id,))
        return store_id

    def _ensure_product(self, record: Mapping[str, Any], raw_quantity: Any) -> int:
        key = (record["commerce_id"], record["provider_product_id"])
        fields = self._product_fields(record, raw_quantity)
        cached = self._product_cache.get(key)
        if cached is not None:
            product_id, previous = cached
        else:
            row = self.connection.execute(
                "SELECT product_id,gtin,gtin_status,name,brand,quantity_json,quantity_status FROM products WHERE commerce_id=? AND provider_product_id=?",
                key,
            ).fetchone()
            if row is None:
                cursor = self.connection.execute(
                    "INSERT INTO products(commerce_id,provider_product_id,gtin,gtin_status,name,brand,quantity_json,quantity_status,metadata_status) VALUES(?,?,?,?,?,?,?,?,?)",
                    (key[0], key[1], record["gtin"], record["gtin_status"], fields["name"], fields["brand"], fields["quantity"], fields["quantityStatus"], "CONSISTENT"),
                )
                product_id = int(cursor.lastrowid)
                previous = fields
                self.stats.products += 1
                if record["gtin"] is not None:
                    self.product_callback(record["gtin"], record["commerce_id"], self.region.region_id)
            else:
                product_id = int(row[0])
                _require(row[1] == record["gtin"] and row[2] == record["gtin_status"], "Product GTIN identity changed within a shard")
                previous = {"name": row[3], "brand": row[4], "quantity": row[5], "quantityStatus": row[6]}
            self._product_cache.put(key, (product_id, previous))
        conflict = self._record_variants("products", product_id, fields, previous)
        if conflict:
            quantity_conflict = fields.get("quantity") != previous.get("quantity") or fields.get("quantityStatus") != previous.get("quantityStatus")
            self.connection.execute("UPDATE products SET metadata_status='CONFLICTING' WHERE product_id=?", (product_id,))
            if quantity_conflict:
                self.connection.execute("UPDATE products SET quantity_status='CONFLICTING',quantity_json=NULL WHERE product_id=?", (product_id,))
        return product_id

    def _intern_money(self, amount: str | None) -> int | None:
        if amount is None:
            return None
        cached = self._money_cache.get(amount)
        if cached is not None:
            return int(cached)
        amount = _canonical_decimal(amount, "money.amount", positive=True)
        self.connection.execute("INSERT OR IGNORE INTO money(amount) VALUES(?)", (amount,))
        money_id = int(self.connection.execute("SELECT money_id FROM money WHERE amount=?", (amount,)).fetchone()[0])
        self._money_cache.put(amount, money_id)
        return money_id

    def _intern_package(self, package_sha: str, package_name: str) -> int:
        cached = self._package_cache.get(package_sha)
        if cached is not None:
            package_id = int(cached)
        else:
            self.connection.execute("INSERT OR IGNORE INTO packages(sha256,name) VALUES(?,?)", (package_sha, package_name))
            package_id = int(self.connection.execute("SELECT package_id FROM packages WHERE sha256=?", (package_sha,)).fetchone()[0])
            self._package_cache.put(package_sha, package_id)
        self.connection.execute("INSERT OR IGNORE INTO package_variants(package_id,name) VALUES(?,?)", (package_id, package_name))
        return package_id

    def _intern_time(self, value: str | None) -> int | None:
        if value is None:
            return None
        cached = self._time_cache.get(value)
        if cached is not None:
            return int(cached)
        value = _canonical_timestamp(value, "provider_update_time")
        self.connection.execute("INSERT OR IGNORE INTO provider_times(value) VALUES(?)", (value,))
        time_id = int(self.connection.execute("SELECT time_id FROM provider_times WHERE value=?", (value,)).fetchone()[0])
        self._time_cache.put(value, time_id)
        return time_id

    def _intern_reference(self, reference_json: str | None) -> int | None:
        if reference_json is None:
            return None
        reference = json.loads(reference_json)
        amount_id = self._intern_money(reference.get("amount"))
        quantity_raw = _canonical_optional_text(reference.get("quantityRaw"), "reference.quantityRaw", 160)
        unit_raw = _canonical_optional_text(reference.get("unitRaw"), "reference.unitRaw", 64)
        role = reference.get("semanticRole")
        _require(role == "reference_price_not_current_offer", "Reference price semantic role is invalid")
        key = (amount_id, quantity_raw, unit_raw, role)
        cached = self._reference_cache.get(key)
        if cached is not None:
            return int(cached)
        reference_key = _stable_value(key)
        self.connection.execute("INSERT OR IGNORE INTO reference_prices(reference_key,amount_id,quantity_raw,unit_raw,semantic_role) VALUES(?,?,?,?,?)", (reference_key, *key))
        reference_id = int(self.connection.execute("SELECT reference_id FROM reference_prices WHERE reference_key=?", (reference_key,)).fetchone()[0])
        self._reference_cache.put(key, reference_id)
        return reference_id

    def add(self, raw: Mapping[str, Any], *, source: Any) -> None:
        store_record, product_record, offer_record, promotions = _validate_row(raw, source, self.region)
        store_id = self._ensure_store(store_record)
        product_id = self._ensure_product(product_record, raw.get("quantity"))
        package_sha = offer_record["package_sha256"]
        package_name = str(raw["source"].get("nested_package_name") or package_sha)
        package_id = self._intern_package(package_sha, package_name)
        list_price_id = self._intern_money(offer_record["amount"])
        reference_id = self._intern_reference(offer_record["reference_json"])
        update_time_id = self._intern_time(offer_record["provider_update_time"])
        offer_id = self._next_offer_id
        self._next_offer_id += 1
        self.connection.execute(
            "INSERT INTO offers(offer_id,store_id,product_id,list_price_id,reference_price_id,package_id,source_row,provider_update_time_id,release_date,freshness_status,availability) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            (offer_id, store_id, product_id, list_price_id, reference_id, package_id, offer_record["source_row"], update_time_id, offer_record["release_date"], offer_record["freshness_status"], offer_record["availability"]),
        )
        for promotion in promotions:
            slot = promotion.get("slot")
            _require(isinstance(slot, int) and slot in {1, 2}, "Promotion slot is invalid")
            price_raw = _canonical_optional_text(promotion.get("price_raw"), "promotion.price_raw", 160)
            condition = _canonical_optional_text(promotion.get("condition"), "promotion.condition", 512)
            price_id = self._intern_money(promotion.get("price_ars"))
            _require(promotion.get("eligibility") == "UNKNOWN", "Promotion eligibility must remain UNKNOWN")
            self.connection.execute("INSERT INTO promotions(offer_id,slot,price_id,price_raw,condition,eligibility) VALUES(?,?,?,?,?,?)", (offer_id, slot, price_id, price_raw, condition, "UNKNOWN"))
            self.stats.promotions += 1
        self.stats.selected_rows += 1
        self.stats.offers += 1
        if self.stats.offers % 10_000 == 0:
            self.connection.commit()

    def finalize(self, destination_dir: Path) -> dict[str, Any]:
        self.connection.commit()
        valid_gtins = int(self.connection.execute("SELECT COUNT(DISTINCT gtin) FROM products WHERE gtin IS NOT NULL").fetchone()[0])
        exact_cross_retailer = int(self.connection.execute("SELECT COUNT(*) FROM (SELECT gtin FROM products WHERE gtin IS NOT NULL GROUP BY gtin HAVING COUNT(DISTINCT commerce_id)>=2)").fetchone()[0])
        geo_valid = int(self.connection.execute("SELECT COUNT(*) FROM stores WHERE geo_status='VALID'").fetchone()[0])
        package_hashes = [row[0] for row in self.connection.execute("SELECT sha256 FROM packages ORDER BY sha256")]
        self.connection.execute("CREATE INDEX offers_product_index ON offers(product_id,offer_id)")
        self.connection.execute("CREATE INDEX offers_store_index ON offers(store_id,offer_id)")
        self.connection.execute("PRAGMA user_version=1")
        self.connection.commit()
        self.connection.execute("VACUUM")
        self.connection.commit()
        self.connection.close()
        raw_bytes = self.raw_path.stat().st_size
        raw_sha = _sha256_file(self.raw_path)
        compressed_path = destination_dir / MOBILE_SHARD_FILE
        compressed_partial = compressed_path.with_name(compressed_path.name + ".partial")
        _gzip_deterministic(self.raw_path, compressed_partial)
        os.replace(compressed_partial, compressed_path)
        compressed_bytes = compressed_path.stat().st_size
        compressed_sha = _sha256_file(compressed_path)
        self.raw_path.unlink()
        counts = {
            "selectedAcceptedObservations": self.stats.selected_rows,
            "stores": self.stats.stores,
            "productEvidenceRecords": self.stats.products,
            "validGtins": valid_gtins,
            "exactCrossRetailerGtins": exact_cross_retailer,
            "offers": self.stats.offers,
            "promotions": self.stats.promotions,
        }
        manifest = {
            "artifactSchemaVersion": MOBILE_SHARD_SCHEMA_VERSION,
            "policyVersion": MOBILE_SHARD_POLICY_VERSION,
            "compatibilityVersion": MOBILE_COMPATIBILITY_VERSION,
            "atomicCompletion": True,
            "completionState": "COMPLETE",
            "region": {"id": self.region.region_id, "displayName": self.region.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": self.region.province_code}},
            "generatedAt": self.generated_at,
            "source": dict(self.source),
            "boundaries": {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "TRUSTED_COORDINATES_ONLY_NO_ROUTING", "rawProviderDataCommitted": False},
            "counts": counts,
            "storeSummary": {"storesWithTrustedCoordinates": geo_valid, "storesWithoutTrustedCoordinates": counts["stores"] - geo_valid},
            "size": {"sqliteBytes": raw_bytes, "sqliteSha256": raw_sha, "compressedBytes": compressed_bytes, "compressedSha256": compressed_sha, "bytesPerOfferCompressed": format(Decimal(compressed_bytes) / Decimal(max(self.stats.offers, 1)), ".6f"), "bytesPerOfferUncompressed": format(Decimal(raw_bytes) / Decimal(max(self.stats.offers, 1)), ".6f")},
            "packages": package_hashes,
            "files": {MOBILE_SHARD_FILE: {"path": MOBILE_SHARD_FILE, "compression": "gzip", "bytes": compressed_bytes, "uncompressedBytes": raw_bytes, "sha256": compressed_sha, "uncompressedSha256": raw_sha}},
        }
        manifest_path = destination_dir / "manifest.json"
        manifest_path.write_bytes(_canonical_json(manifest))
        manifest_sha = _sha256_file(manifest_path)
        (destination_dir / "manifest.sha256").write_text(f"{manifest_sha}  manifest.json\n", encoding="ascii")
        return manifest

def _source_for_index(proof: Any) -> dict[str, Any]:
    return {
        "provider": "ARGENTINA_SEPA_PRECIOS_CLAROS",
        "releaseDate": proof.release_date,
        "outerSha256": proof.outer_sha256,
        "outerBytes": proof.outer_bytes,
        "acceptedObservationsSha256": proof.accepted_sha256,
        "acceptedObservationsBytes": proof.accepted_bytes,
        "acceptedObservationsRows": proof.accepted_rows,
        "qualificationManifestSha256": proof.manifest_sha256,
        "qualificationSchemaVersion": proof.qualification_schema_version,
        "qualificationPolicyVersion": proof.qualification_policy_version,
        "license": proof.license,
        "attribution": proof.attribution,
        "rawProviderDataCommitted": False,
    }


def _write_index(path: Path, *, generated_at: str, source: Mapping[str, Any], regions: list[Mapping[str, Any]], unpublished: list[Mapping[str, Any]], totals: Mapping[str, Any]) -> dict[str, Any]:
    index = {
        "artifactSchemaVersion": NATIONAL_INDEX_SCHEMA_VERSION,
        "policyVersion": NATIONAL_INDEX_POLICY_VERSION,
        "compatibilityVersion": MOBILE_COMPATIBILITY_VERSION,
        "atomicCompletion": True,
        "completionState": "COMPLETE",
        "generatedAt": generated_at,
        "release": {"date": source["releaseDate"], "id": f"argentina-sepa-{source['releaseDate']}"},
        "source": dict(source),
        "boundaries": {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "TRUSTED_COORDINATES_ONLY_NO_ROUTING", "rawProviderDataCommitted": False},
        "distribution": {"granularity": "REGION_SHARD", "activation": "VERIFY_THEN_ATOMIC_LAST_KNOWN_GOOD", "deltaSupport": "NOT_MEASURED_SINGLE_RELEASE", "androidNetworking": "NOT_AUTHORIZED"},
        "regions": list(regions),
        "unpublishedProvinceEvidence": list(unpublished),
        "totals": dict(totals),
    }
    path.write_bytes(_canonical_json(index))
    index_sha = _sha256_file(path)
    (path.parent / "index.sha256").write_text(f"{index_sha}  index.json\n", encoding="ascii")
    (path.parent / "integrity.json").write_bytes(_canonical_json({"indexSha256": index_sha, "atomicCompletion": True, "schemaVersion": NATIONAL_INDEX_SCHEMA_VERSION, "regionIds": [entry["regionId"] for entry in regions]}))
    return index


def build_national_shards(
    accepted_path: Path,
    source_manifest_path: Path,
    output_root: Path,
    *,
    generated_at: str,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = RELEASE_DATE,
) -> dict[str, Any]:
    """Stream one qualified national source into every registered region."""

    generated_at = _canonical_timestamp(generated_at, "generated_at")
    proof = verify_qualified_source(accepted_path, source_manifest_path, expected_outer_sha256=expected_outer_sha256, expected_outer_bytes=expected_outer_bytes, expected_release_date=expected_release_date)
    output_root = output_root.resolve()
    _require(not output_root.exists(), f"Refusing to overwrite existing national shard root: {output_root}")
    output_root.parent.mkdir(parents=True, exist_ok=True)
    partial = Path(tempfile.mkdtemp(prefix=f".{output_root.name}.partial-", dir=str(output_root.parent)))
    scratch_fd, scratch_name = tempfile.mkstemp(prefix="argentina-sepa-national-", suffix=".sqlite", dir=str(output_root.parent))
    os.close(scratch_fd)
    scratch_path = Path(scratch_name)
    diagnostics: sqlite3.Connection | None = None
    writers: dict[str, MobileShardWriter] = {}
    input_rows = 0
    selected_rows = 0
    unpublished_rows = 0
    completed = False
    try:
        diagnostics = sqlite3.connect(str(scratch_path))
        diagnostics.execute("PRAGMA journal_mode=OFF")
        diagnostics.execute("PRAGMA synchronous=OFF")
        diagnostics.execute("CREATE TABLE gtin_commerce(gtin TEXT NOT NULL, commerce_id TEXT NOT NULL, PRIMARY KEY(gtin,commerce_id)) WITHOUT ROWID")
        diagnostics.execute("CREATE TABLE gtin_regions(gtin TEXT NOT NULL, region_id TEXT NOT NULL, PRIMARY KEY(gtin,region_id)) WITHOUT ROWID")
        diagnostics.execute("CREATE TABLE unpublished_provinces(classification TEXT NOT NULL, value_json TEXT NOT NULL, rows INTEGER NOT NULL, PRIMARY KEY(classification,value_json)) WITHOUT ROWID")
        source = _source_for_index(proof)

        def record_gtin(gtin: str, commerce_id: str, region_id: str) -> None:
            diagnostics.execute(
                "INSERT OR IGNORE INTO gtin_commerce(gtin,commerce_id) VALUES(?,?)",
                (gtin, commerce_id),
            )
            diagnostics.execute(
                "INSERT OR IGNORE INTO gtin_regions(gtin,region_id) VALUES(?,?)",
                (gtin, region_id),
            )

        for region in ARGENTINA_REGIONS:
            region_dir = partial / "regions" / region.region_id
            region_dir.mkdir(parents=True, exist_ok=True)
            writers[region.region_id] = MobileShardWriter(
                region,
                region_dir / "shard.sqlite",
                generated_at=generated_at,
                source=source,
                product_callback=record_gtin,
            )
        with gzip.open(proof.accepted_path, "rt", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                input_rows += 1
                raw = json.loads(line)
                _require(isinstance(raw, dict), f"Accepted source line {line_number} is not an object")
                store = raw.get("store")
                province = store.get("province") if isinstance(store, dict) else None
                bucket = _province_unpublished_bucket(province)
                if bucket is not None:
                    classification, value = bucket
                    value_json = _stable_value(value)
                    diagnostics.execute("INSERT INTO unpublished_provinces(classification,value_json,rows) VALUES(?,?,1) ON CONFLICT(classification,value_json) DO UPDATE SET rows=rows+1", (classification, value_json))
                    unpublished_rows += 1
                    continue
                region = REGIONS_BY_PROVINCE[province]
                writers[region.region_id].add(raw, source=proof)
                selected_rows += 1
                if input_rows % 10_000 == 0:
                    diagnostics.commit()
        _require(input_rows == proof.accepted_rows, f"Accepted row count mismatch: read {input_rows}, manifest {proof.accepted_rows}")
        diagnostics.commit()
        region_entries: list[dict[str, Any]] = []
        totals_stores = totals_products = totals_offers = totals_promotions = 0
        for region in ARGENTINA_REGIONS:
            writer = writers[region.region_id]
            manifest = writer.finalize(partial / "regions" / region.region_id)
            counts = manifest["counts"]
            totals_stores += counts["stores"]
            totals_products += counts["productEvidenceRecords"]
            totals_offers += counts["offers"]
            totals_promotions += counts["promotions"]
            shard_descriptor = dict(manifest["files"][MOBILE_SHARD_FILE])
            # A per-shard manifest resolves files relative to its own
            # directory; the national index resolves the same descriptor from
            # the national root.  Keep the bytes/hashes identical while making
            # the two path bases explicit.
            shard_descriptor["path"] = f"regions/{region.region_id}/{MOBILE_SHARD_FILE}"
            region_entries.append({
                "regionId": region.region_id,
                "displayName": region.display_name,
                "provinceCode": region.province_code,
                "selector": {"field": "store.province", "operator": "EXACT", "value": region.province_code},
                "shard": shard_descriptor,
                "manifest": {"path": f"regions/{region.region_id}/manifest.json", "sha256": _sha256_file(partial / "regions" / region.region_id / "manifest.json"), "bytes": (partial / "regions" / region.region_id / "manifest.json").stat().st_size},
                "counts": counts,
                "storeSummary": manifest["storeSummary"],
                "freshness": {"releaseDate": expected_release_date, "status": "FRESH_ONLY"},
            })
        unpublished = [
            {"classification": row[0], "value": json.loads(row[1]), "rows": row[2]}
            for row in diagnostics.execute("SELECT classification,value_json,rows FROM unpublished_provinces ORDER BY classification,value_json")
        ]
        national_valid_gtins = int(diagnostics.execute("SELECT COUNT(DISTINCT gtin) FROM gtin_commerce").fetchone()[0])
        national_exact_gtins = int(diagnostics.execute("SELECT COUNT(*) FROM (SELECT gtin FROM gtin_commerce GROUP BY gtin HAVING COUNT(DISTINCT commerce_id)>=2)").fetchone()[0])
        totals = {
            "inputAcceptedObservations": input_rows,
            "selectedAcceptedObservations": selected_rows,
            "unpublishedProvinceRows": unpublished_rows,
            "stores": totals_stores,
            "productEvidenceRecords": totals_products,
            "validGtins": national_valid_gtins,
            "exactCrossRetailerGtins": national_exact_gtins,
            "offers": totals_offers,
            "promotions": totals_promotions,
        }
        index = _write_index(partial / "index.json", generated_at=generated_at, source=source, regions=region_entries, unpublished=unpublished, totals=totals)
        (partial / "README.txt").write_text("This directory contains a verified national index and region-sharded compressed SQLite artifacts.\n", encoding="utf-8")
        os.replace(partial, output_root)
        completed = True
        return index
    finally:
        for writer in writers.values():
            try:
                writer.connection.close()
            except Exception:
                pass
        if diagnostics is not None:
            diagnostics.close()
        try:
            scratch_path.unlink()
        except FileNotFoundError:
            pass
        # Close all SQLite handles before removing a failed candidate.  This
        # ordering is required on Windows, where an open handle can otherwise
        # leave an incomplete partial directory behind.
        if not completed:
            shutil.rmtree(partial, ignore_errors=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accepted", required=True, type=Path)
    parser.add_argument("--source-manifest", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--expected-outer-sha256", default=EXPECTED_OUTER_SHA256)
    parser.add_argument("--expected-outer-bytes", default=EXPECTED_OUTER_BYTES, type=int)
    parser.add_argument("--expected-release-date", default=RELEASE_DATE)
    args = parser.parse_args(argv)
    try:
        index = build_national_shards(args.accepted, args.source_manifest, args.output_root, generated_at=args.generated_at, expected_outer_sha256=args.expected_outer_sha256, expected_outer_bytes=args.expected_outer_bytes, expected_release_date=args.expected_release_date)
    except (NationalShardError, OSError, sqlite3.Error, TypeError, ValueError, json.JSONDecodeError) as exc:
        print(f"national shard build failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps({"regions": len(index["regions"]), "offers": index["totals"]["offers"], "path": str(args.output_root)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

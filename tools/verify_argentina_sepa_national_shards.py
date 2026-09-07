#!/usr/bin/env python3
"""Verify the national Argentina SEPA index and compressed SQLite shards."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import shutil
import sqlite3
import tempfile
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Mapping

try:
    from tools.build_argentina_sepa_national_shards import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        MOBILE_COMPATIBILITY_VERSION,
        MOBILE_SHARD_FILE,
        MOBILE_SHARD_POLICY_VERSION,
        MOBILE_SHARD_SCHEMA_VERSION,
        NATIONAL_INDEX_POLICY_VERSION,
        NATIONAL_INDEX_SCHEMA_VERSION,
        NationalShardError,
        _canonical_decimal,
        _canonical_json,
    )
    from tools.build_argentina_sepa_regional_snapshot import _canonical_timestamp, _valid_gtin
except ModuleNotFoundError:  # direct ``python tools/verify_...py`` invocation
    from build_argentina_sepa_national_shards import (
        ARGENTINA_REGIONS,
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        EXPECTED_OUTER_BYTES,
        EXPECTED_OUTER_SHA256,
        MOBILE_COMPATIBILITY_VERSION,
        MOBILE_SHARD_FILE,
        MOBILE_SHARD_POLICY_VERSION,
        MOBILE_SHARD_SCHEMA_VERSION,
        NATIONAL_INDEX_POLICY_VERSION,
        NATIONAL_INDEX_SCHEMA_VERSION,
        NationalShardError,
        _canonical_decimal,
        _canonical_json,
    )
    from build_argentina_sepa_regional_snapshot import _canonical_timestamp, _valid_gtin


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise NationalShardError(message)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _read_canonical_json(path: Path, label: str) -> dict[str, Any]:
    raw = path.read_bytes() if path.is_file() else b""
    try:
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise NationalShardError(f"{label} is missing or invalid JSON") from exc
    _require(raw == _canonical_json(value), f"{label} is not canonical JSON")
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _safe_relative_path(value: Any, label: str) -> Path:
    _require(isinstance(value, str) and value and not os.path.isabs(value), f"{label} path is invalid")
    path = Path(value)
    _require(".." not in path.parts, f"{label} path escapes snapshot root")
    return path


def _validate_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    _require(descriptor.get("path") == expected_path, f"{label} descriptor path is invalid")
    _require(descriptor.get("compression") == "gzip", f"{label} compression is invalid")
    for field in ("bytes", "uncompressedBytes"):
        _require(isinstance(descriptor.get(field), int) and descriptor[field] >= 0, f"{label} {field} is invalid")
    for field in ("sha256", "uncompressedSha256"):
        value = descriptor.get(field)
        _require(isinstance(value, str) and len(value) == 64 and value == value.lower() and all(char in "0123456789abcdef" for char in value), f"{label} {field} is invalid")
    relative = _safe_relative_path(expected_path, label)
    path = root / relative
    _require(path.is_file(), f"Missing {label}: {path}")
    _require(path.stat().st_size == descriptor["bytes"], f"{label} byte count mismatch")
    _require(_sha256_file(path) == descriptor["sha256"], f"{label} hash mismatch")
    return path


def _validate_manifest_descriptor(root: Path, descriptor: Mapping[str, Any], expected_path: str, label: str) -> Path:
    """Validate the small per-shard manifest descriptor from the national index."""

    _require(descriptor.get("path") == expected_path, f"{label} descriptor path is invalid")
    value = descriptor.get("bytes")
    _require(isinstance(value, int) and value >= 0, f"{label} bytes are invalid")
    digest = descriptor.get("sha256")
    _require(isinstance(digest, str) and len(digest) == 64 and digest == digest.lower() and all(char in "0123456789abcdef" for char in digest), f"{label} hash is invalid")
    relative = _safe_relative_path(expected_path, label)
    path = root / relative
    _require(path.is_file(), f"Missing {label}: {path}")
    _require(path.stat().st_size == value and _sha256_file(path) == digest, f"{label} bytes or hash mismatch")
    return path


def _decompress(path: Path) -> tuple[Path, Any]:
    directory = tempfile.TemporaryDirectory(prefix="argentina-mobile-verify-")
    raw_path = Path(directory.name) / "shard.sqlite"
    with gzip.open(path, "rb") as source, raw_path.open("wb") as target:
        shutil.copyfileobj(source, target, length=1024 * 1024)
    return raw_path, directory


def _metadata(connection: sqlite3.Connection) -> dict[str, str]:
    return {key: value for key, value in connection.execute("SELECT key,value FROM metadata ORDER BY key")}


def _contiguous_ids(connection: sqlite3.Connection, table: str, column: str) -> None:
    count, minimum, maximum = connection.execute(f"SELECT COUNT(*),MIN({column}),MAX({column}) FROM {table}").fetchone()
    if count == 0:
        _require(minimum is None and maximum is None, f"{table} id bounds are invalid")
    else:
        _require(minimum == 1 and maximum == count, f"{table} ids are not contiguous")


def _validate_shard_sqlite(raw_path: Path, region: Mapping[str, Any], manifest: Mapping[str, Any], expected_release_date: str, gtin_commerce: dict[str, set[str]], gtin_regions: dict[str, set[str]]) -> dict[str, Any]:
    connection = sqlite3.connect(str(raw_path))
    try:
        _require(connection.execute("PRAGMA integrity_check").fetchone()[0] == "ok", f"{region['regionId']} SQLite integrity check failed")
        _require(connection.execute("PRAGMA foreign_key_check").fetchone() is None, f"{region['regionId']} has a foreign-key violation")
        metadata = _metadata(connection)
        expected_metadata = {
            "schemaVersion": MOBILE_SHARD_SCHEMA_VERSION,
            "policyVersion": MOBILE_SHARD_POLICY_VERSION,
            "compatibilityVersion": MOBILE_COMPATIBILITY_VERSION,
            "regionId": region["regionId"],
            "regionDisplayName": region["displayName"],
            "provinceCode": region["provinceCode"],
            "releaseDate": expected_release_date,
            "outerSha256": manifest["source"]["outerSha256"],
            "currency": CURRENCY,
            "availability": AVAILABILITY,
            "deliveryPickup": DELIVERY_PICKUP,
            "rawProviderDataCommitted": "false",
            "generatedAt": manifest["generatedAt"],
        }
        _require(metadata == expected_metadata, f"{region['regionId']} SQLite metadata is invalid")
        expected_tables = {"metadata", "money", "packages", "package_variants", "provider_times", "reference_prices", "stores", "store_variants", "products", "product_variants", "offers", "promotions"}
        tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        _require(tables == expected_tables, f"{region['regionId']} SQLite table set is invalid")
        for table, column in (("money", "money_id"), ("packages", "package_id"), ("provider_times", "time_id"), ("reference_prices", "reference_id"), ("stores", "store_id"), ("products", "product_id"), ("offers", "offer_id")):
            _contiguous_ids(connection, table, column)
        for amount, in connection.execute("SELECT amount FROM money ORDER BY money_id"):
            _require(amount == _canonical_decimal(amount, "money.amount", positive=True), f"{region['regionId']} money is not canonical")
        package_hashes: list[str] = []
        for package_id, digest, name in connection.execute("SELECT package_id,sha256,name FROM packages ORDER BY package_id"):
            _require(isinstance(digest, str) and len(digest) == 64 and digest == digest.lower() and all(char in "0123456789abcdef" for char in digest), f"{region['regionId']} package hash is invalid")
            _require(isinstance(name, str) and bool(name), f"{region['regionId']} package name is invalid")
            _require(connection.execute("SELECT 1 FROM package_variants WHERE package_id=? AND name=?", (package_id, name)).fetchone() is not None, f"{region['regionId']} package provenance variant is missing")
            package_hashes.append(digest)
        for value, in connection.execute("SELECT value FROM provider_times ORDER BY time_id"):
            _require(value == _canonical_timestamp(value, "provider time"), f"{region['regionId']} provider time is not canonical")
        province_code = region["provinceCode"]
        for row in connection.execute("SELECT store_id,province,latitude,longitude,geo_status,metadata_status FROM stores ORDER BY store_id"):
            store_id, province, latitude, longitude, geo_status, metadata_status = row
            _require(province == province_code, f"{region['regionId']} store crosses province boundary")
            _require(geo_status in {"VALID", "GEO_INCOMPLETE", "GEO_CONFLICTING"}, f"{region['regionId']} store geo status is invalid")
            _require(metadata_status in {"CONSISTENT", "CONFLICTING"}, f"{region['regionId']} store metadata status is invalid")
            if geo_status == "VALID":
                _require(isinstance(latitude, str) and isinstance(longitude, str), f"{region['regionId']} valid store lacks coordinates")
                _require(Decimal("-56") <= Decimal(latitude) <= Decimal("-21") and Decimal("-74") <= Decimal(longitude) <= Decimal("-52"), f"{region['regionId']} store coordinate is outside Argentina")
            elif geo_status == "GEO_CONFLICTING":
                _require(latitude is None and longitude is None, f"{region['regionId']} conflicting store exposes coordinates")
            for field, value in (("latitude", latitude), ("longitude", longitude)):
                if value is not None:
                    _require(value == _canonical_decimal(value, f"{field}"), f"{region['regionId']} {field} is not canonical")
        for row in connection.execute("SELECT product_id,commerce_id,provider_product_id,gtin,gtin_status,quantity_json,quantity_status,metadata_status FROM products ORDER BY product_id"):
            product_id, commerce, provider_id, gtin, gtin_status, quantity_json, quantity_status, metadata_status = row
            _require(isinstance(commerce, str) and commerce and isinstance(provider_id, str) and provider_id, f"{region['regionId']} product identity is invalid")
            if gtin is None:
                _require(gtin_status == "INVALID_OR_NOT_GTIN" and not _valid_gtin(provider_id), f"{region['regionId']} invalid GTIN was promoted")
            else:
                _require(gtin_status == "VALID" and gtin == provider_id and _valid_gtin(gtin), f"{region['regionId']} GTIN is invalid")
                gtin_commerce.setdefault(gtin, set()).add(commerce)
                gtin_regions.setdefault(gtin, set()).add(region["regionId"])
            _require(quantity_status in {"KNOWN", "UNKNOWN", "CONFLICTING"}, f"{region['regionId']} quantity status is invalid")
            _require(metadata_status in {"CONSISTENT", "CONFLICTING"}, f"{region['regionId']} product metadata status is invalid")
            if quantity_status == "KNOWN":
                _require(isinstance(quantity_json, str), f"{region['regionId']} known quantity is missing")
                quantity = json.loads(quantity_json)
                _require(isinstance(quantity, dict) and quantity.get("unit") in {"GRAM", "MILLILITRE", "COUNT"}, f"{region['regionId']} quantity unit is invalid")
                _require(quantity.get("value") == _canonical_decimal(quantity.get("value"), "quantity.value", positive=True), f"{region['regionId']} quantity value is invalid")
            else:
                _require(quantity_json is None, f"{region['regionId']} unknown/conflicting quantity is exposed")
            if quantity_status == "CONFLICTING":
                _require(connection.execute("SELECT 1 FROM product_variants WHERE product_id=? AND field IN ('quantity','quantityStatus')", (product_id,)).fetchone() is not None, f"{region['regionId']} quantity conflict lacks variants")
        for row in connection.execute("SELECT reference_key,amount_id,quantity_raw,unit_raw,semantic_role FROM reference_prices ORDER BY reference_id"):
            reference_key, amount_id, quantity_raw, unit_raw, role = row
            _require(reference_key == json.dumps([amount_id, quantity_raw, unit_raw, role], ensure_ascii=False, sort_keys=True, separators=(",", ":")), f"{region['regionId']} reference key is not canonical")
            _require(role == "reference_price_not_current_offer", f"{region['regionId']} reference semantics are invalid")
            if amount_id is not None:
                _require(connection.execute("SELECT 1 FROM money WHERE money_id=?", (amount_id,)).fetchone() is not None, f"{region['regionId']} reference price points to missing money")
        counts = manifest["counts"]
        _require(set(counts) == {"selectedAcceptedObservations", "stores", "productEvidenceRecords", "validGtins", "exactCrossRetailerGtins", "offers", "promotions"}, f"{region['regionId']} count schema is invalid")
        actual = {
            "stores": int(connection.execute("SELECT COUNT(*) FROM stores").fetchone()[0]),
            "productEvidenceRecords": int(connection.execute("SELECT COUNT(*) FROM products").fetchone()[0]),
            "validGtins": int(connection.execute("SELECT COUNT(DISTINCT gtin) FROM products WHERE gtin IS NOT NULL").fetchone()[0]),
            "exactCrossRetailerGtins": int(connection.execute("SELECT COUNT(*) FROM (SELECT gtin FROM products WHERE gtin IS NOT NULL GROUP BY gtin HAVING COUNT(DISTINCT commerce_id)>=2)").fetchone()[0]),
            "offers": int(connection.execute("SELECT COUNT(*) FROM offers").fetchone()[0]),
            "promotions": int(connection.execute("SELECT COUNT(*) FROM promotions").fetchone()[0]),
        }
        _require(counts == {"selectedAcceptedObservations": counts["selectedAcceptedObservations"], **actual}, f"{region['regionId']} counts do not match SQLite")
        _require(counts["selectedAcceptedObservations"] == actual["offers"], f"{region['regionId']} selected rows do not match offers")
        for row in connection.execute("SELECT offer_id,list_price_id,reference_price_id,package_id,source_row,provider_update_time_id,release_date,freshness_status,availability FROM offers ORDER BY offer_id"):
            offer_id, list_price_id, reference_id, package_id, source_row, time_id, release_date, freshness, availability = row
            _require(release_date == expected_release_date and freshness == "FRESH" and availability == AVAILABILITY, f"{region['regionId']} offer boundary is invalid")
            _require(isinstance(source_row, int) and source_row > 0, f"{region['regionId']} source row is invalid")
            # All four integer references are declared foreign keys.  The
            # shard-wide ``foreign_key_check`` above validates them in one
            # pass; avoid a per-offer SELECT over the 14M-row national stream.
            _require(isinstance(list_price_id, int) and list_price_id > 0, f"{region['regionId']} offer price reference is invalid")
            _require(isinstance(package_id, int) and package_id > 0, f"{region['regionId']} offer package reference is invalid")
            _require(reference_id is None or (isinstance(reference_id, int) and reference_id > 0), f"{region['regionId']} offer reference price is invalid")
            _require(time_id is None or (isinstance(time_id, int) and time_id > 0), f"{region['regionId']} offer provider time is invalid")
        for offer_id, slot, price_id, price_raw, condition, eligibility in connection.execute("SELECT offer_id,slot,price_id,price_raw,condition,eligibility FROM promotions ORDER BY offer_id,slot"):
            _require(slot in {1, 2} and eligibility == "UNKNOWN", f"{region['regionId']} promotion boundary is invalid")
            # Promotion offer/price references are also foreign keys checked
            # shard-wide above, so validation here stays linear in rows.
            _require(isinstance(offer_id, int) and offer_id > 0, f"{region['regionId']} promotion offer reference is invalid")
            _require(price_id is None or (isinstance(price_id, int) and price_id > 0), f"{region['regionId']} promotion price reference is invalid")
            for field, value in (("price_raw", price_raw), ("condition", condition)):
                if value is not None:
                    _require(isinstance(value, str) and value == value.strip(), f"{region['regionId']} promotion {field} is invalid")
        _require(actual["stores"] == manifest["storeSummary"]["storesWithTrustedCoordinates"] + manifest["storeSummary"]["storesWithoutTrustedCoordinates"], f"{region['regionId']} store summary is invalid")
        _require(manifest.get("packages") == sorted(package_hashes), f"{region['regionId']} package provenance list is invalid")
        size = manifest.get("size")
        _require(isinstance(size, dict), f"{region['regionId']} size metadata is invalid")
        _require(size.get("sqliteBytes") == raw_path.stat().st_size and size.get("compressedBytes") == manifest["files"][MOBILE_SHARD_FILE]["bytes"], f"{region['regionId']} size metadata does not match descriptor")
        _require(size.get("bytesPerOfferCompressed") == format(Decimal(size["compressedBytes"]) / Decimal(max(actual["offers"], 1)), ".6f"), f"{region['regionId']} compressed bytes-per-offer is invalid")
        _require(size.get("bytesPerOfferUncompressed") == format(Decimal(size["sqliteBytes"]) / Decimal(max(actual["offers"], 1)), ".6f"), f"{region['regionId']} uncompressed bytes-per-offer is invalid")
        return actual
    finally:
        connection.close()


def verify_national_shards(snapshot_root: Path, *, expected_outer_sha256: str = EXPECTED_OUTER_SHA256, expected_outer_bytes: int = EXPECTED_OUTER_BYTES, expected_release_date: str = "2026-09-06") -> dict[str, Any]:
    snapshot_root = snapshot_root.resolve()
    _require(snapshot_root.is_dir(), f"Missing national shard root: {snapshot_root}")
    index = _read_canonical_json(snapshot_root / "index.json", "index.json")
    _require(index.get("artifactSchemaVersion") == NATIONAL_INDEX_SCHEMA_VERSION and index.get("policyVersion") == NATIONAL_INDEX_POLICY_VERSION and index.get("compatibilityVersion") == MOBILE_COMPATIBILITY_VERSION, "National index version is invalid")
    _require(index.get("atomicCompletion") is True and index.get("completionState") == "COMPLETE", "National index is incomplete")
    _require(isinstance(index.get("generatedAt"), str) and index["generatedAt"] == _canonical_timestamp(index["generatedAt"], "index.generatedAt"), "National index timestamp is invalid")
    source = index.get("source")
    _require(isinstance(source, dict) and source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS" and source.get("releaseDate") == expected_release_date and source.get("outerSha256") == expected_outer_sha256 and source.get("outerBytes") == expected_outer_bytes and source.get("license") == "Creative Commons Attribution 4.0" and isinstance(source.get("attribution"), str) and source.get("rawProviderDataCommitted") is False, "National source provenance is invalid")
    _require(isinstance(source.get("acceptedObservationsRows"), int) and source["acceptedObservationsRows"] >= 0 and isinstance(source.get("acceptedObservationsSha256"), str) and len(source["acceptedObservationsSha256"]) == 64 and source["acceptedObservationsSha256"] == source["acceptedObservationsSha256"].lower() and all(char in "0123456789abcdef" for char in source["acceptedObservationsSha256"]), "National accepted source proof is invalid")
    _require(index.get("boundaries") == {"currency": CURRENCY, "availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "TRUSTED_COORDINATES_ONLY_NO_ROUTING", "rawProviderDataCommitted": False}, "National boundaries are invalid")
    _require(index.get("distribution") == {"granularity": "REGION_SHARD", "activation": "VERIFY_THEN_ATOMIC_LAST_KNOWN_GOOD", "deltaSupport": "NOT_MEASURED_SINGLE_RELEASE", "androidNetworking": "NOT_AUTHORIZED"}, "Distribution contract is invalid")
    index_hash = _sha256_file(snapshot_root / "index.json")
    _require((snapshot_root / "index.sha256").read_text(encoding="ascii") == f"{index_hash}  index.json\n", "National index checksum is invalid")
    integrity = _read_canonical_json(snapshot_root / "integrity.json", "integrity.json")
    _require(integrity == {"atomicCompletion": True, "indexSha256": index_hash, "regionIds": sorted(integrity.get("regionIds", [])), "schemaVersion": NATIONAL_INDEX_SCHEMA_VERSION}, "National integrity metadata is invalid")
    regions = index.get("regions")
    _require(isinstance(regions, list) and len(regions) == len(ARGENTINA_REGIONS), "National region index is incomplete")
    expected_regions = {region.province_code: region for region in ARGENTINA_REGIONS}
    gtin_commerce: dict[str, set[str]] = {}
    gtin_regions: dict[str, set[str]] = {}
    totals = {"stores": 0, "productEvidenceRecords": 0, "offers": 0, "promotions": 0, "validGtins": 0, "exactCrossRetailerGtins": 0, "selectedAcceptedObservations": 0}
    seen_region_ids: list[str] = []
    for entry in regions:
        _require(isinstance(entry, dict), "National region entry is invalid")
        province = entry.get("provinceCode")
        spec = expected_regions.get(province)
        _require(spec is not None and entry.get("regionId") == spec.region_id and entry.get("displayName") == spec.display_name, "National region selector is invalid")
        _require(entry.get("selector") == {"field": "store.province", "operator": "EXACT", "value": province}, "National region selector metadata is invalid")
        seen_region_ids.append(spec.region_id)
        manifest_descriptor = entry.get("manifest")
        _require(isinstance(manifest_descriptor, dict), f"{spec.region_id} manifest descriptor is missing")
        manifest_path = _validate_manifest_descriptor(snapshot_root, manifest_descriptor, f"regions/{spec.region_id}/manifest.json", f"{spec.region_id} manifest")
        shard_manifest = _read_canonical_json(manifest_path, f"{spec.region_id} manifest")
        manifest_sha_path = manifest_path.with_name("manifest.sha256")
        _require(manifest_sha_path.is_file() and manifest_sha_path.read_text(encoding="ascii") == f"{_sha256_file(manifest_path)}  manifest.json\n", f"{spec.region_id} manifest checksum is invalid")
        _require(shard_manifest.get("artifactSchemaVersion") == MOBILE_SHARD_SCHEMA_VERSION and shard_manifest.get("policyVersion") == MOBILE_SHARD_POLICY_VERSION and shard_manifest.get("compatibilityVersion") == MOBILE_COMPATIBILITY_VERSION and shard_manifest.get("atomicCompletion") is True and shard_manifest.get("completionState") == "COMPLETE", f"{spec.region_id} shard manifest is incomplete")
        _require(shard_manifest.get("region") == {"id": spec.region_id, "displayName": spec.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": spec.province_code}}, f"{spec.region_id} shard region is invalid")
        _require(shard_manifest.get("generatedAt") == index["generatedAt"], f"{spec.region_id} generated timestamp differs from index")
        _require(shard_manifest.get("source") == source, f"{spec.region_id} source provenance differs from index")
        descriptor = entry.get("shard")
        _require(isinstance(descriptor, dict), f"{spec.region_id} shard descriptor is missing")
        shard_path = _validate_descriptor(snapshot_root, descriptor, f"regions/{spec.region_id}/{MOBILE_SHARD_FILE}", f"{spec.region_id} shard")
        manifest_descriptor = shard_manifest.get("files", {}).get(MOBILE_SHARD_FILE)
        _require(isinstance(manifest_descriptor, dict) and manifest_descriptor.get("path") == MOBILE_SHARD_FILE, f"{spec.region_id} shard manifest path is invalid")
        _require(set(shard_manifest.get("files", {})) == {MOBILE_SHARD_FILE}, f"{spec.region_id} shard file set is invalid")
        _require(
            {key: value for key, value in manifest_descriptor.items() if key != "path"}
            == {key: value for key, value in descriptor.items() if key != "path"},
            f"{spec.region_id} shard descriptor differs from manifest",
        )
        raw_path, directory = _decompress(shard_path)
        try:
            _require(raw_path.stat().st_size == descriptor["uncompressedBytes"] and _sha256_file(raw_path) == descriptor["uncompressedSha256"], f"{spec.region_id} SQLite payload hash mismatch")
            actual = _validate_shard_sqlite(raw_path, entry, shard_manifest, expected_release_date, gtin_commerce, gtin_regions)
        finally:
            directory.cleanup()
        _require(entry.get("counts") == shard_manifest.get("counts") and entry.get("storeSummary") == shard_manifest.get("storeSummary"), f"{spec.region_id} index summary differs from shard manifest")
        for key in totals:
            totals[key] += actual.get(key, shard_manifest["counts"].get(key, 0))
    _require(seen_region_ids == sorted(seen_region_ids), "National regions are not stably ordered")
    _require(len(set(seen_region_ids)) == len(ARGENTINA_REGIONS), "National regions are duplicated")
    unpublished = index.get("unpublishedProvinceEvidence")
    _require(isinstance(unpublished, list), "Unpublished province evidence is missing")
    unpublished_rows = 0
    previous_unpublished = None
    for row in unpublished:
        _require(isinstance(row, dict) and row.get("classification") in {"UNKNOWN", "NONSTANDARD"} and isinstance(row.get("rows"), int) and row["rows"] > 0, "Unpublished province evidence is invalid")
        _require(row.get("value") is None if row["classification"] == "UNKNOWN" else isinstance(row.get("value"), str) and row["value"] not in expected_regions, "Unpublished province value is silently publishable")
        ordering = (row["classification"], _stable_json_value(row.get("value")))
        _require(previous_unpublished is None or ordering > previous_unpublished, "Unpublished province evidence is not stably ordered")
        previous_unpublished = ordering
        unpublished_rows += row["rows"]
    national_valid = len(gtin_commerce)
    national_exact = sum(len(commerce) >= 2 for commerce in gtin_commerce.values())
    expected_totals = index.get("totals")
    _require(expected_totals == {"inputAcceptedObservations": source["acceptedObservationsRows"], "selectedAcceptedObservations": totals["selectedAcceptedObservations"], "unpublishedProvinceRows": unpublished_rows, "stores": totals["stores"], "productEvidenceRecords": totals["productEvidenceRecords"], "validGtins": national_valid, "exactCrossRetailerGtins": national_exact, "offers": totals["offers"], "promotions": totals["promotions"]}, "National totals do not match shards")
    _require(expected_totals["inputAcceptedObservations"] == expected_totals["selectedAcceptedObservations"] + expected_totals["unpublishedProvinceRows"], "National input accounting is incomplete")
    _require(integrity["regionIds"] == sorted(seen_region_ids), "National integrity region list is invalid")
    return {"indexSha256": index_hash, "regions": len(regions), "unpublishedProvinceRows": unpublished_rows, **expected_totals}


def _stable_json_value(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-root", required=True, type=Path)
    parser.add_argument("--expected-outer-sha256", default=EXPECTED_OUTER_SHA256)
    parser.add_argument("--expected-outer-bytes", default=EXPECTED_OUTER_BYTES, type=int)
    parser.add_argument("--expected-release-date", default="2026-09-06")
    args = parser.parse_args(argv)
    try:
        result = verify_national_shards(args.snapshot_root, expected_outer_sha256=args.expected_outer_sha256, expected_outer_bytes=args.expected_outer_bytes, expected_release_date=args.expected_release_date)
    except (NationalShardError, OSError, sqlite3.Error, InvalidOperation, TypeError, ValueError, json.JSONDecodeError) as exc:
        print(f"national shard verification failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

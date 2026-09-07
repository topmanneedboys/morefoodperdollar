#!/usr/bin/env python3
"""Verify a compact Argentina SEPA regional snapshot without networking."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import sqlite3
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable, Mapping

try:
    from tools.build_argentina_sepa_regional_snapshot import (
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        EXPECTED_OUTER_SHA256,
        EXPECTED_OUTER_BYTES,
        QUALIFIED_POLICY_VERSION,
        QUALIFIED_SCHEMA_VERSION,
        CABA_REGION,
        RegionSpec,
        REGION_ID,
        REGION_PROVINCE_CODE,
        region_for_province,
        REGIONAL_POLICY_VERSION,
        REGIONAL_SCHEMA_VERSION,
        RegionalSnapshotError,
        _canonical_json,
        _canonical_decimal,
        _canonical_timestamp,
        _valid_gtin,
    )
except ModuleNotFoundError:  # direct ``python tools/verify_...py`` invocation
    from build_argentina_sepa_regional_snapshot import (
        AVAILABILITY,
        CURRENCY,
        DELIVERY_PICKUP,
        EXPECTED_OUTER_SHA256,
        EXPECTED_OUTER_BYTES,
        QUALIFIED_POLICY_VERSION,
        QUALIFIED_SCHEMA_VERSION,
        CABA_REGION,
        RegionSpec,
        REGION_ID,
        REGION_PROVINCE_CODE,
        region_for_province,
        REGIONAL_POLICY_VERSION,
        REGIONAL_SCHEMA_VERSION,
        RegionalSnapshotError,
        _canonical_json,
        _canonical_decimal,
        _canonical_timestamp,
        _valid_gtin,
    )


EXPECTED_FILES = frozenset(
    {"release.json", "stores.jsonl.gz", "products.jsonl.gz", "offers.jsonl.gz", "promotions.jsonl.gz"}
)


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise RegionalSnapshotError(message)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _read_canonical_json(path: Path, label: str) -> dict[str, Any]:
    raw = path.read_bytes() if path.is_file() else b""
    try:
        value = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RegionalSnapshotError(f"{label} is missing or invalid JSON") from exc
    _require(raw and raw == _canonical_json(value), f"{label} is missing or non-canonical")
    _require(isinstance(value, dict), f"{label} must be an object")
    return value


def _validate_money(value: Any, field: str, *, nullable: bool = False) -> str | None:
    if value is None and nullable:
        return None
    normalized = _canonical_decimal(value, field, positive=True)
    _require(value == normalized, f"{field} is not canonical")
    return normalized


def _validate_descriptor(snapshot_dir: Path, name: str, descriptor: Mapping[str, Any]) -> None:
    _require(descriptor.get("path") == name, f"File descriptor path mismatch: {name}")
    _require(descriptor.get("compression") in {"none", "gzip"}, f"Unsupported compression: {name}")
    _require(isinstance(descriptor.get("recordCount"), int) and descriptor["recordCount"] >= 0, f"Invalid record count: {name}")
    for key in ("bytes", "uncompressedBytes"):
        _require(isinstance(descriptor.get(key), int) and descriptor[key] >= 0, f"Invalid file size: {name}")
    for key in ("sha256", "uncompressedSha256"):
        value = descriptor.get(key)
        _require(isinstance(value, str) and len(value) == 64 and all(char in "0123456789abcdef" for char in value), f"Invalid file hash: {name}")
    path = snapshot_dir / name
    _require(path.is_file(), f"Missing artifact file: {name}")
    _require(path.stat().st_size == descriptor["bytes"], f"File size mismatch: {name}")
    _require(_sha256(path) == descriptor["sha256"], f"File hash mismatch: {name}")


def _iter_records(path: Path, descriptor: Mapping[str, Any], label: str) -> Iterable[tuple[int, bytes, dict[str, Any]]]:
    opener = gzip.open if descriptor.get("compression") == "gzip" else open
    uncompressed_hash = hashlib.sha256()
    uncompressed_bytes = 0
    count = 0
    with opener(path, "rb") as handle:
        for line_number, line in enumerate(handle, start=1):
            _require(line.endswith(b"\n"), f"{label} line {line_number} lacks a newline")
            uncompressed_hash.update(line)
            uncompressed_bytes += len(line)
            try:
                value = json.loads(line.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise RegionalSnapshotError(f"{label} line {line_number} is invalid JSON") from exc
            _require(isinstance(value, dict), f"{label} line {line_number} must be an object")
            _require(line == _canonical_json(value), f"{label} line {line_number} is not canonical JSON")
            count += 1
            yield line_number, line, value
    _require(count == descriptor["recordCount"], f"{label} record count mismatch")
    _require(uncompressed_bytes == descriptor["uncompressedBytes"], f"{label} uncompressed size mismatch")
    _require(uncompressed_hash.hexdigest() == descriptor["uncompressedSha256"], f"{label} uncompressed hash mismatch")


def _validate_store(record: Mapping[str, Any], line: int, region: RegionSpec) -> None:
    required = {
        "storeKey", "commerceId", "bannerId", "storeId", "name", "type", "address",
        "locality", "province", "latitude", "longitude", "geoStatus", "metadataStatus",
    }
    _require(set(record) <= required | {"metadataVariants"} and required <= set(record), f"stores line {line} schema is invalid")
    _require(record["storeKey"] == f"ar-sepa-store:{record['commerceId']}:{record['bannerId']}:{record['storeId']}", f"stores line {line} key is invalid")
    _require(record["province"] == region.province_code, f"stores line {line} is outside the exact regional selector")
    _require(record["geoStatus"] in {"VALID", "GEO_INCOMPLETE", "GEO_CONFLICTING"}, f"stores line {line} geo status is invalid")
    address = record["address"]
    _require(isinstance(address, dict) and set(address) == {"street", "number", "postalCode"}, f"stores line {line} address is invalid")
    if record["geoStatus"] == "VALID":
        _require(isinstance(record["latitude"], str) and isinstance(record["longitude"], str), f"stores line {line} lacks coordinates")
        _require(record["latitude"] == _canonical_decimal(record["latitude"], f"stores line {line} latitude"), f"stores line {line} latitude is not canonical")
        _require(record["longitude"] == _canonical_decimal(record["longitude"], f"stores line {line} longitude"), f"stores line {line} longitude is not canonical")
        lat = Decimal(record["latitude"])
        lon = Decimal(record["longitude"])
        _require(Decimal("-56") <= lat <= Decimal("-21") and Decimal("-74") <= lon <= Decimal("-52"), f"stores line {line} coordinate is outside Argentina")
    elif record["geoStatus"] == "GEO_CONFLICTING":
        _require(record["latitude"] is None and record["longitude"] is None, f"stores line {line} exposes conflicting coordinates")
    else:
        for field, value, lower, upper in (("latitude", record["latitude"], Decimal("-56"), Decimal("-21")), ("longitude", record["longitude"], Decimal("-74"), Decimal("-52"))):
            if value is not None:
                _require(isinstance(value, str) and value == _canonical_decimal(value, f"stores line {line} {field}"), f"stores line {line} {field} is not canonical")
                _require(lower <= Decimal(value) <= upper, f"stores line {line} {field} is outside Argentina")


def _validate_product(record: Mapping[str, Any], line: int) -> None:
    required = {
        "productEvidenceKey", "commerceId", "providerProductId", "gtin", "gtinStatus", "name", "brand",
        "quantity", "quantityStatus", "metadataStatus",
    }
    _require(set(record) <= required | {"metadataVariants"} and required <= set(record), f"products line {line} schema is invalid")
    _require(record["productEvidenceKey"] == f"ar-sepa-product:{record['commerceId']}:{record['providerProductId']}", f"products line {line} key is invalid")
    provider_id = record["providerProductId"]
    _require(isinstance(provider_id, str) and provider_id, f"products line {line} provider id is invalid")
    if record["gtin"] is None:
        _require(record["gtinStatus"] == "INVALID_OR_NOT_GTIN" and not _valid_gtin(provider_id), f"products line {line} GTIN status is invalid")
    else:
        _require(record["gtinStatus"] == "VALID" and record["gtin"] == provider_id and _valid_gtin(record["gtin"]), f"products line {line} GTIN is invalid")
    _require(record["quantityStatus"] in {"KNOWN", "UNKNOWN", "CONFLICTING"}, f"products line {line} quantity status is invalid")
    if record["quantity"] is None:
        _require(record["quantityStatus"] in {"UNKNOWN", "CONFLICTING"}, f"products line {line} unknown quantity status is invalid")
    else:
        _require(record["quantityStatus"] == "KNOWN", f"products line {line} quantity status is invalid")
        quantity = record["quantity"]
        _require(isinstance(quantity, dict) and set(quantity) == {"unit", "value"}, f"products line {line} quantity schema is invalid")
        _require(quantity["unit"] in {"GRAM", "MILLILITRE", "COUNT"}, f"products line {line} quantity unit is invalid")
        _validate_money(quantity["value"], f"products line {line} quantity", nullable=False)


def _validate_offer(record: Mapping[str, Any], line: int, expected_release_date: str) -> None:
    required = {
        "offerKey", "storeKey", "productEvidenceKey", "listPrice", "referencePrice", "releaseDate",
        "providerUpdateTime", "freshnessStatus", "packageProvenanceSha256", "sourceRow", "availability",
    }
    _require(set(record) == required, f"offers line {line} schema is invalid")
    price = record["listPrice"]
    _require(isinstance(price, dict) and set(price) == {"amount", "currency"} and price["currency"] == CURRENCY, f"offers line {line} money schema is invalid")
    _validate_money(price["amount"], f"offers line {line} list price")
    _require(record["releaseDate"] == expected_release_date and record["freshnessStatus"] == "FRESH", f"offers line {line} freshness is invalid")
    _require(record["availability"] == AVAILABILITY, f"offers line {line} availability is not UNKNOWN")
    _require(isinstance(record["sourceRow"], int) and record["sourceRow"] > 0, f"offers line {line} source row is invalid")
    _require(isinstance(record["packageProvenanceSha256"], str) and len(record["packageProvenanceSha256"]) == 64 and all(char in "0123456789abcdef" for char in record["packageProvenanceSha256"]), f"offers line {line} package provenance is invalid")
    _require(isinstance(record["offerKey"], str) and record["offerKey"].startswith("ar-sepa-offer:") and len(record["offerKey"]) == 78 and all(char in "0123456789abcdef" for char in record["offerKey"][14:]), f"offers line {line} offer key is invalid")
    if record["providerUpdateTime"] is not None:
        _require(record["providerUpdateTime"] == _canonical_timestamp(record["providerUpdateTime"], f"offers line {line} provider update time"), f"offers line {line} provider update time is not canonical")
    reference = record["referencePrice"]
    if reference is not None:
        _require(isinstance(reference, dict) and set(reference) == {"amount", "currency", "quantityRaw", "unitRaw", "semanticRole"}, f"offers line {line} reference schema is invalid")
        _require(reference["currency"] == CURRENCY and reference["semanticRole"] == "reference_price_not_current_offer", f"offers line {line} reference semantics are invalid")
        _validate_money(reference["amount"], f"offers line {line} reference price", nullable=True)


def _validate_promotion(record: Mapping[str, Any], line: int) -> None:
    required = {"promotionKey", "offerKey", "slot", "price", "priceRaw", "condition", "eligibility"}
    _require(set(record) == required, f"promotions line {line} schema is invalid")
    _require(isinstance(record["offerKey"], str) and record["offerKey"].startswith("ar-sepa-offer:") and len(record["offerKey"]) == 78 and all(char in "0123456789abcdef" for char in record["offerKey"][14:]), f"promotions line {line} offer key is invalid")
    _require(isinstance(record["slot"], int) and record["slot"] in {1, 2}, f"promotions line {line} slot is invalid")
    _require(record["eligibility"] == "UNKNOWN", f"promotions line {line} eligibility is not UNKNOWN")
    _require(isinstance(record["promotionKey"], str) and record["promotionKey"].startswith(record["offerKey"] + ":promotion:"), f"promotions line {line} key is invalid")
    price = record["price"]
    if price is not None:
        _require(isinstance(price, dict) and set(price) == {"amount", "currency"} and price["currency"] == CURRENCY, f"promotions line {line} money schema is invalid")
        _validate_money(price["amount"], f"promotions line {line} price")


def verify_snapshot(
    snapshot_dir: Path,
    *,
    expected_outer_sha256: str = EXPECTED_OUTER_SHA256,
    expected_outer_bytes: int = EXPECTED_OUTER_BYTES,
    expected_release_date: str = "2026-09-06",
    region: RegionSpec = CABA_REGION,
) -> dict[str, Any]:
    snapshot_dir = snapshot_dir.resolve()
    _require(snapshot_dir.is_dir(), f"Missing snapshot directory: {snapshot_dir}")
    manifest = _read_canonical_json(snapshot_dir / "manifest.json", "manifest.json")
    _require(manifest.get("artifactSchemaVersion") == REGIONAL_SCHEMA_VERSION, "Regional schema version is invalid")
    _require(manifest.get("policyVersion") == REGIONAL_POLICY_VERSION, "Regional policy version is invalid")
    _require(manifest.get("atomicCompletion") is True and manifest.get("completionState") == "COMPLETE", "Regional artifact is incomplete")
    expected_region = {"id": region.region_id, "displayName": region.display_name, "selector": {"field": "store.province", "operator": "EXACT", "value": region.province_code}}
    _require(manifest.get("region") == expected_region, "Region selector is not the requested exact province")
    _require(manifest.get("regionSelector") == {"matching": "EXACT_NORMALIZED_SOURCE_FIELD", "provinceCode": region.province_code}, "Region selector metadata is invalid")
    _require(manifest.get("generatedAt") and isinstance(manifest["generatedAt"], str), "Generated timestamp is missing")
    _require(manifest["generatedAt"] == _canonical_timestamp(manifest["generatedAt"], "manifest.generatedAt"), "Generated timestamp is not canonical")
    source = manifest.get("source")
    _require(isinstance(source, dict), "Source provenance is missing")
    _require(source.get("provider") == "ARGENTINA_SEPA_PRECIOS_CLAROS", "Source provider is invalid")
    _require(source.get("outerSha256") == expected_outer_sha256, "Source outer SHA is invalid")
    _require(source.get("outerBytes") == expected_outer_bytes, "Source outer byte count is invalid")
    _require(source.get("releaseDate") == expected_release_date, "Source release date is invalid")
    _require(source.get("qualificationSchemaVersion") == QUALIFIED_SCHEMA_VERSION, "Qualification schema provenance is invalid")
    _require(source.get("qualificationPolicyVersion") == QUALIFIED_POLICY_VERSION, "Qualification policy provenance is invalid")
    _require(source.get("license") == "Creative Commons Attribution 4.0", "Source licence is invalid")
    _require(isinstance(source.get("attribution"), str) and source["attribution"], "Source attribution is missing")
    _require(source.get("rawProviderDataCommitted") is False, "Raw provider data is claimed committed")
    boundaries = manifest.get("boundaries")
    _require(boundaries == {"availability": AVAILABILITY, "currency": CURRENCY, "deliveryPickup": DELIVERY_PICKUP, "distance": "TRUSTED_COORDINATES_ONLY_NO_ROUTING", "rawProviderDataCommitted": False}, "Regional boundaries are invalid")
    files = manifest.get("files")
    _require(isinstance(files, dict) and set(files) == EXPECTED_FILES, "Regional file set is invalid")
    for name in sorted(EXPECTED_FILES):
        _validate_descriptor(snapshot_dir, name, files[name])
    release = _read_canonical_json(snapshot_dir / "release.json", "release.json")
    _require(release.get("artifactSchemaVersion") == REGIONAL_SCHEMA_VERSION and release.get("policyVersion") == REGIONAL_POLICY_VERSION, "Release metadata version is invalid")
    _require(release.get("region") == manifest["region"], "Release region is invalid")
    _require(release.get("generatedAt") == manifest["generatedAt"], "Release generated timestamp is invalid")
    release_source = release.get("source")
    _require(isinstance(release_source, dict) and release_source.get("provider") == source["provider"] and release_source.get("releaseDate") == expected_release_date and release_source.get("outerSha256") == expected_outer_sha256 and release_source.get("qualificationSchemaVersion") == QUALIFIED_SCHEMA_VERSION and release_source.get("qualificationPolicyVersion") == QUALIFIED_POLICY_VERSION and release_source.get("license") == source["license"] and release_source.get("attribution") == source["attribution"], "Release source provenance is invalid")
    _require(release.get("boundaries") == {"availability": AVAILABILITY, "deliveryPickup": DELIVERY_PICKUP, "distance": "TRUSTED_COORDINATES_ONLY", "rawProviderDataCommitted": False}, "Release boundaries are invalid")
    manifest_bytes = (snapshot_dir / "manifest.json").read_bytes()
    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
    _require((snapshot_dir / "manifest.sha256").read_text(encoding="ascii") == f"{manifest_hash}  manifest.json\n", "Manifest checksum is invalid")
    integrity = _read_canonical_json(snapshot_dir / "integrity.json", "integrity.json")
    _require(integrity.get("manifestSha256") == manifest_hash and integrity.get("atomicCompletion") is True and integrity.get("fileNames") == sorted(EXPECTED_FILES), "Integrity metadata is invalid")

    db = sqlite3.connect(":memory:")
    db.executescript("CREATE TABLE stores(k TEXT PRIMARY KEY); CREATE TABLE products(k TEXT PRIMARY KEY, commerce TEXT NOT NULL, gtin TEXT); CREATE TABLE offers(k TEXT PRIMARY KEY);")
    store_count = product_count = offer_count = promotion_count = 0
    previous_store = previous_product = previous_offer = previous_promotion = None
    try:
        for _, _, record in _iter_records(snapshot_dir / "stores.jsonl.gz", files["stores.jsonl.gz"], "stores"):
            _validate_store(record, store_count + 1, region)
            key = record["storeKey"]
            _require(previous_store is None or key > previous_store, "Stores are not stably ordered or are duplicated")
            previous_store = key
            _require(db.execute("INSERT OR IGNORE INTO stores(k) VALUES(?)", (key,)).rowcount == 1, f"Duplicate store key: {key}")
            store_count += 1
        for _, _, record in _iter_records(snapshot_dir / "products.jsonl.gz", files["products.jsonl.gz"], "products"):
            _validate_product(record, product_count + 1)
            key = record["productEvidenceKey"]
            _require(previous_product is None or key > previous_product, "Products are not stably ordered or are duplicated")
            previous_product = key
            _require(db.execute("INSERT OR IGNORE INTO products(k,commerce,gtin) VALUES(?,?,?)", (key, record["commerceId"], record["gtin"])).rowcount == 1, f"Duplicate product key: {key}")
            product_count += 1
        for _, _, record in _iter_records(snapshot_dir / "offers.jsonl.gz", files["offers.jsonl.gz"], "offers"):
            _validate_offer(record, offer_count + 1, expected_release_date)
            _require(db.execute("SELECT 1 FROM stores WHERE k=?", (record["storeKey"],)).fetchone() is not None, f"Offer references missing store: {record['storeKey']}")
            _require(db.execute("SELECT 1 FROM products WHERE k=?", (record["productEvidenceKey"],)).fetchone() is not None, f"Offer references missing product: {record['productEvidenceKey']}")
            key = record["offerKey"]
            _require(previous_offer is None or (record["storeKey"], record["productEvidenceKey"], record["packageProvenanceSha256"], record["sourceRow"], key) > previous_offer, "Offers are not stably ordered or are duplicated")
            previous_offer = (record["storeKey"], record["productEvidenceKey"], record["packageProvenanceSha256"], record["sourceRow"], key)
            _require(db.execute("INSERT OR IGNORE INTO offers(k) VALUES(?)", (key,)).rowcount == 1, f"Duplicate offer key: {key}")
            offer_count += 1
        for _, _, record in _iter_records(snapshot_dir / "promotions.jsonl.gz", files["promotions.jsonl.gz"], "promotions"):
            _validate_promotion(record, promotion_count + 1)
            _require(db.execute("SELECT 1 FROM offers WHERE k=?", (record["offerKey"],)).fetchone() is not None, f"Promotion references missing offer: {record['offerKey']}")
            key = record["promotionKey"]
            _require(previous_promotion is None or (record["offerKey"], record["slot"], key) > previous_promotion, "Promotions are not stably ordered or are duplicated")
            previous_promotion = (record["offerKey"], record["slot"], key)
            promotion_count += 1
    except Exception:
        db.close()
        raise
    counts = manifest.get("counts")
    _require(isinstance(counts, dict), "Manifest record counts are missing")
    expected_count_keys = {"inputAcceptedObservations", "selectedAcceptedObservations", "stores", "productEvidenceRecords", "validGtins", "exactCrossRetailerGtins", "offers", "promotions"}
    _require(set(counts) == expected_count_keys, "Manifest record count schema is invalid")
    computed_valid_gtins = db.execute("SELECT COUNT(DISTINCT gtin) FROM products WHERE gtin IS NOT NULL").fetchone()[0]
    computed_cross_retailer = db.execute("SELECT COUNT(*) FROM (SELECT gtin FROM products WHERE gtin IS NOT NULL GROUP BY gtin HAVING COUNT(DISTINCT commerce) >= 2)").fetchone()[0]
    _require(counts == {"inputAcceptedObservations": counts["inputAcceptedObservations"], "selectedAcceptedObservations": counts["selectedAcceptedObservations"], "stores": store_count, "productEvidenceRecords": product_count, "validGtins": computed_valid_gtins, "exactCrossRetailerGtins": computed_cross_retailer, "offers": offer_count, "promotions": promotion_count}, "Manifest record counts do not match artifacts")
    _require(isinstance(counts.get("inputAcceptedObservations"), int) and isinstance(counts.get("selectedAcceptedObservations"), int), "Manifest observation counts are invalid")
    _require(counts["selectedAcceptedObservations"] == offer_count, "Selected observation count does not match offers")
    _require(isinstance(counts.get("validGtins"), int) and isinstance(counts.get("exactCrossRetailerGtins"), int), "Manifest identity counts are invalid")
    size = manifest.get("size")
    _require(isinstance(size, dict), "Manifest size metadata is missing")
    _require(size.get("compactUncompressedBytes") == sum(files[name]["uncompressedBytes"] for name in files), "Manifest compact uncompressed size is invalid")
    _require(size.get("compactCompressedBytes") == sum(files[name]["bytes"] for name in files), "Manifest compact compressed size is invalid")
    _require(size.get("inputAcceptedGzipBytes") == source.get("acceptedObservationsBytes"), "Manifest input size is invalid")
    _require(size.get("inputNormalizedUncompressedBytes") is None or (isinstance(size.get("inputNormalizedUncompressedBytes"), int) and size["inputNormalizedUncompressedBytes"] > 0), "Manifest normalized input size is invalid")
    _require(size.get("bytesPerOfferUncompressed") == format(Decimal(size["compactUncompressedBytes"]) / Decimal(max(offer_count, 1)), ".6f"), "Manifest bytes-per-offer metric is invalid")
    result = {"regionId": region.region_id, "stores": store_count, "products": product_count, "offers": offer_count, "promotions": promotion_count, "manifestSha256": manifest_hash}
    db.close()
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--expected-outer-sha256", default=EXPECTED_OUTER_SHA256)
    parser.add_argument("--expected-outer-bytes", default=EXPECTED_OUTER_BYTES, type=int)
    parser.add_argument("--expected-release-date", default="2026-09-06")
    parser.add_argument("--province-code", default=REGION_PROVINCE_CODE)
    args = parser.parse_args(argv)
    try:
        result = verify_snapshot(
            args.snapshot,
            expected_outer_sha256=args.expected_outer_sha256,
            expected_outer_bytes=args.expected_outer_bytes,
            expected_release_date=args.expected_release_date,
            region=region_for_province(args.province_code),
        )
    except (RegionalSnapshotError, OSError, sqlite3.Error, InvalidOperation, json.JSONDecodeError) as exc:
        print(f"regional snapshot verification failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

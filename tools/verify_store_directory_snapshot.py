#!/usr/bin/env python3
"""Verify a signed, canonical store-directory snapshot without networking."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from typing import Any, Mapping

try:
    from tools.build_store_directory_snapshot import (
        CURRENT_SCHEMA_VERSION,
        ID_RE,
        MAX_RECORDS,
        REQUIRED_ALLOWED_USES,
        REQUIRED_RIGHTS_GATES,
        STORE_DIRECTORY_ROLE,
        SUPPORTED_REGIONS,
        SUPPORTED_SHOP_TYPES,
        StoreDirectoryBuildError,
        _canonical_json,
        _canonical_search_key,
        _load_json,
    )
except ModuleNotFoundError:  # pragma: no cover - supports direct CLI execution
    from build_store_directory_snapshot import (
        CURRENT_SCHEMA_VERSION,
        ID_RE,
        MAX_RECORDS,
        REQUIRED_ALLOWED_USES,
        REQUIRED_RIGHTS_GATES,
        STORE_DIRECTORY_ROLE,
        SUPPORTED_REGIONS,
        SUPPORTED_SHOP_TYPES,
        StoreDirectoryBuildError,
        _canonical_json,
        _canonical_search_key,
        _load_json,
    )


OUTPUT_REQUIRED_FIELDS = {
    "recordId",
    "sourceElementId",
    "sourceElementType",
    "providerId",
    "datasetNamespaceId",
    "regionId",
    "name",
    "storeType",
    "latitudeE7",
    "longitudeE7",
    "sourceSnapshotId",
    "licenseId",
    "observedAtEpochMillis",
    "confidence",
    "status",
}
OUTPUT_OPTIONAL_FIELDS = {
    "brand",
    "operator",
    "address",
    "brandWikidataId",
    "operatorWikidataId",
}
FORBIDDEN_FIELDS = {
    "price",
    "currentPrice",
    "salePrice",
    "currency",
    "offer",
    "offerId",
    "promotion",
    "stock",
    "availability",
    "quantity",
    "packageQuantity",
    "unitPrice",
    "validFrom",
    "validTo",
}
ELEMENT_TYPES = {"node", "way", "relation"}
WIKIDATA_RE = re.compile(r"Q[1-9][0-9]{0,18}\Z")
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise StoreDirectoryBuildError(message)


def _text(value: Any, field: str, max_length: int) -> str:
    _require(isinstance(value, str), f"{field} must be a string")
    _require(value == " ".join(value.split()) and value, f"{field} is not canonical text")
    _require(len(value) <= max_length, f"{field} exceeds {max_length} characters")
    return value


def _verify_signature(snapshot_dir: Path, manifest_path: Path, integrity: Mapping[str, Any], public_key: Path | None, require_signature: bool) -> str:
    state = integrity.get("signatureState")
    _require(state in {"VERIFIED", "UNSIGNED"}, "integrity.signatureState is invalid")
    if state == "UNSIGNED":
        _require(public_key is None and not require_signature, "A signed store-directory snapshot is required")
        return state
    _require(public_key is not None and public_key.is_file(), "A signature public key is required")
    _require(integrity.get("signatureAlgorithm") == "SHA256withRSA", "Unsupported signature algorithm")
    _require(integrity.get("signatureFile") == "manifest.sig", "Invalid signature file")
    signature_hash = integrity.get("signatureSha256")
    _require(isinstance(signature_hash, str) and SHA256_RE.fullmatch(signature_hash) is not None, "Invalid signature hash")
    signature_path = snapshot_dir / "manifest.sig"
    _require(signature_path.is_file() and hashlib.sha256(signature_path.read_bytes()).hexdigest() == signature_hash, "Manifest signature hash mismatch")
    try:
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(public_key), "-signature", str(signature_path), str(manifest_path)],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as exc:
        raise StoreDirectoryBuildError("openssl is required to verify signed snapshots") from exc
    _require(result.returncode == 0 and "Verified OK" in result.stdout, "Manifest signature verification failed")
    return state


def _verify_rights(source: Mapping[str, Any]) -> None:
    _require(isinstance(source.get("providerId"), str) and source["providerId"].strip() == source["providerId"], "Source provider id is invalid")
    _require(isinstance(source.get("datasetNamespaceId"), str) and ID_RE.fullmatch(source["datasetNamespaceId"]) is not None, "Source dataset namespace is invalid")
    _text(source.get("displayName"), "source.displayName", 160)
    _text(source.get("licenseId"), "source.licenseId", 120)
    _require(source.get("storageBoundary") == "OPEN_SHARE_ALIKE", "Store directory must retain OPEN_SHARE_ALIKE boundary")
    _text(source.get("attribution"), "source.attribution", 400)
    _text(source.get("sourceUrl"), "source.sourceUrl", 400)
    _text(source.get("licenseUrl"), "source.licenseUrl", 400)
    allowed = source.get("allowedUses")
    _require(isinstance(allowed, list) and allowed == sorted(set(allowed)) and REQUIRED_ALLOWED_USES <= set(allowed), "Source allowed uses are incomplete or non-canonical")
    authorization = source.get("authorization")
    _require(isinstance(authorization, Mapping), "Source authorization is missing")
    _require(authorization.get("providerId") == source["providerId"], "Source authorization provider mismatch")
    _require(authorization.get("datasetNamespaceId") == source["datasetNamespaceId"], "Source authorization namespace mismatch")
    gates = authorization.get("gates")
    _require(isinstance(gates, list), "Source authorization gates are missing")
    by_name = {}
    for gate in gates:
        _require(isinstance(gate, Mapping), "Source authorization gate is invalid")
        name = gate.get("gate")
        _require(isinstance(name, str) and name not in by_name, "Source authorization has duplicate gate")
        _require(gate.get("state") == "SATISFIED", f"Source authorization gate is not satisfied: {name}")
        _text(gate.get("basisId"), f"source.authorization.{name}.basisId", 400)
        by_name[name] = gate
    for required in REQUIRED_RIGHTS_GATES:
        _require(required in by_name, f"Source authorization is missing {required}")


def _bbox_contains(region_id: str, latitude_e7: int, longitude_e7: int) -> bool:
    bounds = SUPPORTED_REGIONS[region_id]
    return (
        round(bounds["minLatitude"] * 10_000_000) <= latitude_e7 <= round(bounds["maxLatitude"] * 10_000_000)
        and round(bounds["minLongitude"] * 10_000_000) <= longitude_e7 <= round(bounds["maxLongitude"] * 10_000_000)
    )


def _verify_address(address: Any, line_number: int) -> None:
    if address is None:
        return
    _require(isinstance(address, Mapping) and set(address) <= {"housenumber", "unit", "street", "city", "postcode"}, f"source line {line_number} has invalid address")
    for key, value in address.items():
        _text(value, f"source line {line_number}.address.{key}", 160)


def _verify_record(raw: Any, *, source: Mapping[str, Any], regions: Mapping[str, Mapping[str, Any]], line_number: int) -> None:
    _require(isinstance(raw, Mapping), f"source line {line_number} must be an object")
    _require(not (set(raw) & FORBIDDEN_FIELDS), f"source line {line_number} contains price/offer/availability fields")
    _require(OUTPUT_REQUIRED_FIELDS <= set(raw) <= OUTPUT_REQUIRED_FIELDS | OUTPUT_OPTIONAL_FIELDS, f"source line {line_number} has a non-canonical schema")
    record_id = raw["recordId"]
    _require(isinstance(record_id, str) and ID_RE.fullmatch(record_id) is not None, f"source line {line_number} has invalid record id")
    element_type = raw["sourceElementType"]
    _require(element_type in ELEMENT_TYPES, f"source line {line_number} has invalid element type")
    _require(re.fullmatch(rf"{element_type}/[1-9][0-9]*", raw["sourceElementId"]) is not None, f"source line {line_number} has invalid source element id")
    _require(record_id == "osm:" + raw["sourceElementId"].replace("/", ":"), f"source line {line_number} source id mismatch")
    _require(raw["providerId"] == source["providerId"], f"source line {line_number} provider scope mismatch")
    _require(raw["datasetNamespaceId"] == source["datasetNamespaceId"], f"source line {line_number} dataset scope mismatch")
    region_id = raw["regionId"]
    _require(region_id in regions and region_id in SUPPORTED_REGIONS, f"source line {line_number} has unsupported region")
    _text(raw["name"], f"source line {line_number}.name", 240)
    if "brand" in raw:
        _text(raw["brand"], f"source line {line_number}.brand", 240)
    if "operator" in raw:
        _text(raw["operator"], f"source line {line_number}.operator", 240)
    _require(raw["storeType"] in SUPPORTED_SHOP_TYPES, f"source line {line_number} has unsupported store type")
    latitude_e7 = raw["latitudeE7"]
    longitude_e7 = raw["longitudeE7"]
    _require(isinstance(latitude_e7, int) and not isinstance(latitude_e7, bool), f"source line {line_number} latitude is not an integer")
    _require(isinstance(longitude_e7, int) and not isinstance(longitude_e7, bool), f"source line {line_number} longitude is not an integer")
    _require(_bbox_contains(region_id, latitude_e7, longitude_e7), f"source line {line_number} coordinate is outside its region")
    _text(raw["sourceSnapshotId"], f"source line {line_number}.sourceSnapshotId", 160)
    _text(raw["licenseId"], f"source line {line_number}.licenseId", 120)
    _require(isinstance(raw["observedAtEpochMillis"], int) and raw["observedAtEpochMillis"] > 0, f"source line {line_number} observed time is invalid")
    _require(raw["confidence"] == "SOURCE_LISTED", f"source line {line_number} confidence is invalid")
    _require(raw["status"] == "LOCATION_ONLY", f"source line {line_number} status is invalid")
    _verify_address(raw.get("address"), line_number)
    for key in ("brandWikidataId", "operatorWikidataId"):
        if key in raw:
            _require(isinstance(raw[key], str) and WIKIDATA_RE.fullmatch(raw[key]) is not None, f"source line {line_number} has invalid {key}")


def _read_manifest(snapshot_dir: Path) -> tuple[dict[str, Any], bytes, str]:
    manifest_path = snapshot_dir / "manifest.json"
    raw = manifest_path.read_bytes() if manifest_path.is_file() else b""
    _require(raw and raw == _canonical_json(json.loads(raw)), "manifest.json is not canonical JSON")
    manifest = json.loads(raw)
    _require(isinstance(manifest, Mapping), "manifest.json must be an object")
    manifest_hash = hashlib.sha256(raw).hexdigest()
    checksum = snapshot_dir / "manifest.sha256"
    _require(checksum.is_file() and checksum.read_text(encoding="ascii") == f"{manifest_hash}  manifest.json\n", "Manifest checksum mismatch")
    return dict(manifest), raw, manifest_hash


def verify_snapshot(snapshot_dir: Path, *, public_key: Path | None = None, require_signature: bool = False) -> dict[str, Any]:
    _require(snapshot_dir.is_dir(), f"Missing snapshot directory: {snapshot_dir}")
    manifest, manifest_bytes, manifest_hash = _read_manifest(snapshot_dir)
    _require(manifest.get("schemaVersion") == CURRENT_SCHEMA_VERSION, "Unsupported store-directory schema")
    _require(manifest.get("snapshotRole") == STORE_DIRECTORY_ROLE, "Snapshot role must be STORE_DIRECTORY")
    _require(isinstance(manifest.get("snapshotId"), str) and ID_RE.fullmatch(manifest["snapshotId"]) is not None, "Invalid snapshot id")
    _require(isinstance(manifest.get("generatedAtEpochMillis"), int) and manifest["generatedAtEpochMillis"] > 0, "Invalid generated time")
    source = manifest.get("source")
    _require(isinstance(source, Mapping), "Snapshot source metadata is missing")
    _verify_rights(source)
    _require(isinstance(source.get("sourceSnapshotId"), str) and ID_RE.fullmatch(source["sourceSnapshotId"]) is not None, "Invalid source snapshot id")
    for field in ("acquiredAtEpochMillis", "observedAtEpochMillis"):
        _require(isinstance(source.get(field), int) and source[field] > 0, f"Invalid source {field}")
        _require(source[field] <= manifest["generatedAtEpochMillis"], f"Source {field} is after generated time")

    regions = manifest.get("regions")
    _require(isinstance(regions, list) and {entry.get("regionId") for entry in regions if isinstance(entry, Mapping)} == set(SUPPORTED_REGIONS), "Snapshot regions must be exactly GTA and Metro Vancouver")
    region_by_id: dict[str, Mapping[str, Any]] = {}
    for entry in regions:
        _require(isinstance(entry, Mapping), "Snapshot region metadata is invalid")
        region_id = entry.get("regionId")
        _require(region_id in SUPPORTED_REGIONS and region_id not in region_by_id, "Snapshot region id is invalid or duplicated")
        region_by_id[region_id] = entry
        _require(entry.get("displayName") == SUPPORTED_REGIONS[region_id]["displayName"], f"Region display name mismatch: {region_id}")
        bounds = entry.get("boundingBoxE7")
        _require(isinstance(bounds, Mapping) and set(bounds) == {"minLatitude", "maxLatitude", "minLongitude", "maxLongitude"}, f"Region bounds are invalid: {region_id}")
        expected_bounds = {
            "ca-gta": {"minLatitude": 433_500_000, "maxLatitude": 443_500_000, "minLongitude": -802_000_000, "maxLongitude": -784_500_000},
            "ca-metro-vancouver": {"minLatitude": 489_500_000, "maxLatitude": 495_500_000, "minLongitude": -1_235_500_000, "maxLongitude": -1_222_000_000},
        }[region_id]
        _require(dict(bounds) == expected_bounds, f"Region bounds are not the launch geography: {region_id}")
        _require(entry.get("recordCount") is not None and isinstance(entry["recordCount"], int) and 0 <= entry["recordCount"] <= MAX_RECORDS, f"Region count is invalid: {region_id}")
    _require(regions == sorted(regions, key=lambda entry: entry["regionId"]), "Snapshot regions are not canonical")

    content = manifest.get("content")
    _require(isinstance(content, Mapping), "Snapshot content metadata is missing")
    _require(content.get("path") == f"sources/{source['datasetNamespaceId']}.jsonl", "Snapshot content path is invalid")
    content_hash = content.get("sha256")
    _require(isinstance(content_hash, str) and SHA256_RE.fullmatch(content_hash) is not None, "Snapshot content hash is invalid")
    record_count = content.get("recordCount")
    _require(isinstance(record_count, int) and 0 <= record_count <= MAX_RECORDS, "Snapshot content count is invalid")
    source_path = snapshot_dir / content["path"]
    source_bytes = source_path.read_bytes() if source_path.is_file() else b""
    _require(source_bytes and source_bytes.endswith(b"\n") and hashlib.sha256(source_bytes).hexdigest() == content_hash, "Store directory content hash mismatch")
    lines = source_bytes.splitlines(keepends=True)
    _require(len(lines) == record_count and all(line.endswith(b"\n") for line in lines), "Store directory record count/newline mismatch")
    seen: set[str] = set()
    region_counts = {region_id: 0 for region_id in sorted(SUPPORTED_REGIONS)}
    previous_key: tuple[str, str, str] | None = None
    for line_number, line in enumerate(lines, start=1):
        try:
            raw = json.loads(line.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise StoreDirectoryBuildError(f"Store directory line {line_number} is invalid UTF-8 JSON") from exc
        _require(line == _canonical_json(raw), f"Store directory line {line_number} is not canonical JSON")
        _verify_record(raw, source=source, regions=region_by_id, line_number=line_number)
        _require(raw["recordId"] not in seen, f"Duplicate source record id: {raw['recordId']}")
        seen.add(raw["recordId"])
        region_counts[raw["regionId"]] += 1
        key = (raw["regionId"], _canonical_search_key(raw["name"]), raw["recordId"])
        _require(previous_key is None or previous_key <= key, "Store directory rows are not deterministically sorted")
        previous_key = key
    _require(all(region_by_id[region]["recordCount"] == count for region, count in region_counts.items()), "Region record counts do not match source rows")

    coverage = manifest.get("coverage")
    _require(isinstance(coverage, Mapping), "Snapshot coverage is missing")
    _require(coverage.get("storeRecordCount") == record_count, "Snapshot store count does not match source")
    _require(coverage.get("currentOfferRecordCount") == 0 and coverage.get("currentOfferCoverage") == "NOT_INCLUDED", "Store directory cannot contain current offers")
    for field in ("priceCoverage", "stockCoverage", "availabilityCoverage"):
        _require(coverage.get(field) == "NOT_INCLUDED", f"Store directory {field} must be NOT_INCLUDED")
    _require(coverage.get("regionRecordCounts") == region_counts, "Snapshot region coverage does not match source")

    integrity = _load_json(snapshot_dir / "integrity.json", "integrity metadata")
    _require((snapshot_dir / "integrity.json").read_bytes() == _canonical_json(integrity), "integrity.json is not canonical JSON")
    _require(integrity.get("schemaVersion") == CURRENT_SCHEMA_VERSION and integrity.get("manifestSha256") == manifest_hash, "Integrity metadata does not match manifest")
    signature_state = _verify_signature(snapshot_dir, snapshot_dir / "manifest.json", integrity, public_key, require_signature)
    return {"snapshotId": manifest["snapshotId"], "records": record_count, "regions": region_counts, "manifestSha256": manifest_hash, "signatureState": signature_state}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--public-key", type=Path)
    parser.add_argument("--require-signature", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = verify_snapshot(args.snapshot, public_key=args.public_key, require_signature=args.require_signature)
    except (StoreDirectoryBuildError, OSError, json.JSONDecodeError) as exc:
        print(f"store directory verification failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

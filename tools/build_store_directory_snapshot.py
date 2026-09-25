#!/usr/bin/env python3
"""Build a deterministic, rights-gated store-directory snapshot.

The input is a locally saved source export (for example an Overpass JSON
response).  This builder deliberately performs no network access and never
creates an offer, price, stock, or availability fact.  Every emitted row is a
source-listed physical location with an explicit region, source element id,
observation time, and ODbL/source attribution metadata in the signed manifest.

The source export is normalized into a canonical JSONL file.  Same bytes,
configuration, and rights manifest produce byte-identical output, which makes
the artifact suitable for review, signing, rollback, and offline APK use.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import unicodedata
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path
from typing import Any, Mapping


CURRENT_SCHEMA_VERSION = 1
STORE_DIRECTORY_ROLE = "STORE_DIRECTORY"
MAX_RECORDS = 50_000
MAX_NAME_LENGTH = 240
MAX_ADDRESS_LENGTH = 160
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")
ID_RE = re.compile(r"[a-z0-9][a-z0-9._:-]{0,159}\Z")
WIKIDATA_RE = re.compile(r"Q[1-9][0-9]{0,18}\Z")

SUPPORTED_REGIONS: dict[str, dict[str, Any]] = {
    "ca-gta": {
        "displayName": "Greater Toronto Area",
        "minLatitude": 43.35,
        "maxLatitude": 44.35,
        "minLongitude": -80.20,
        "maxLongitude": -78.45,
    },
    "ca-metro-vancouver": {
        "displayName": "Metro Vancouver",
        "minLatitude": 48.95,
        "maxLatitude": 49.55,
        "minLongitude": -123.55,
        "maxLongitude": -122.20,
    },
}

SUPPORTED_SHOP_TYPES = frozenset(
    {
        "supermarket",
        "grocery",
        "convenience",
        "greengrocer",
        "butcher",
        "bakery",
        "deli",
        "seafood",
        "organic",
        "health_food",
        "farm",
    }
)

REQUIRED_RIGHTS_GATES = (
    "DATA_ACCESS_AUTHORIZED",
    "CACHE_AUTHORIZED",
    "INDEX_AUTHORIZED",
    "CONSUMER_DISPLAY_AUTHORIZED",
    "MOBILE_APP_AUTHORIZED",
    "RETENTION_DELETION_POLICY_DEFINED",
    "GEOGRAPHY_SCOPED",
    "COMMERCIAL_USE_REVIEWED",
)
REQUIRED_ALLOWED_USES = frozenset(
    {"access", "cache", "display", "index", "mobile_app", "retention_deletion", "comparison"}
)

ADDRESS_TAGS = (
    ("housenumber", "addr:housenumber"),
    ("unit", "addr:unit"),
    ("street", "addr:street"),
    ("city", "addr:city"),
    ("postcode", "addr:postcode"),
)


class StoreDirectoryBuildError(ValueError):
    """An input failed a deterministic store-directory admission check."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise StoreDirectoryBuildError(message)


def _canonical_json(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode("utf-8")


def _load_json(path: Path, label: str) -> Mapping[str, Any]:
    _require(path.is_file(), f"Missing {label}: {path}")
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise StoreDirectoryBuildError(f"Unable to read {label}: {path}") from exc
    _require(isinstance(value, Mapping), f"{label} must be a JSON object")
    return value


def _text(value: Any, field: str, *, max_length: int, collapse: bool = True) -> str:
    _require(isinstance(value, str), f"{field} must be a string")
    result = " ".join(value.split()) if collapse else value.strip()
    _require(bool(result), f"{field} must not be blank")
    _require(len(result) <= max_length, f"{field} exceeds {max_length} characters")
    return result


def _parse_timestamp(value: Any, field: str) -> int:
    text = _text(value, field, max_length=64)
    normalized = text[:-1] + "+00:00" if text.endswith("Z") else text
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise StoreDirectoryBuildError(f"{field} must be an ISO-8601 timestamp") from exc
    _require(parsed.tzinfo is not None, f"{field} must include an explicit timezone")
    utc_value = parsed.astimezone(timezone.utc)
    epoch = datetime(1970, 1, 1, tzinfo=timezone.utc)
    delta = utc_value - epoch
    result = delta.days * 86_400_000 + delta.seconds * 1_000 + delta.microseconds // 1_000
    _require(result > 0, f"{field} must be after the Unix epoch")
    return result


def _canonical_number(value: Any, field: str, *, minimum: float, maximum: float) -> int:
    _require(isinstance(value, (int, float, str)) and not isinstance(value, bool), f"{field} must be numeric")
    try:
        decimal = Decimal(str(value))
    except (InvalidOperation, ValueError) as exc:
        raise StoreDirectoryBuildError(f"{field} must be numeric") from exc
    _require(decimal.is_finite(), f"{field} must be finite")
    _require(Decimal(str(minimum)) <= decimal <= Decimal(str(maximum)), f"{field} is outside geographic bounds")
    scaled = (decimal * Decimal("10000000")).quantize(Decimal("1"), rounding=ROUND_HALF_UP)
    return int(scaled)


def _coordinate_from_element(value: Any, field: str, *, minimum: float, maximum: float) -> int:
    return _canonical_number(value, field, minimum=minimum, maximum=maximum)


def _region_for(latitude_e7: int, longitude_e7: int) -> str | None:
    latitude = latitude_e7 / 10_000_000
    longitude = longitude_e7 / 10_000_000
    matches = [
        region_id
        for region_id, bounds in SUPPORTED_REGIONS.items()
        if bounds["minLatitude"] <= latitude <= bounds["maxLatitude"]
        and bounds["minLongitude"] <= longitude <= bounds["maxLongitude"]
    ]
    _require(len(matches) <= 1, "A source location matches more than one supported region")
    return matches[0] if matches else None


def _canonical_search_key(value: str) -> str:
    return " ".join(
        unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii").lower().split()
    )


def _load_rights(path: Path) -> dict[str, Any]:
    rights = dict(_load_json(path, "store-directory rights manifest"))
    provider_id = _text(rights.get("provider_id"), "rights.provider_id", max_length=128)
    namespace_id = _text(
        rights.get("dataset_namespace_id"), "rights.dataset_namespace_id", max_length=96
    )
    _require(ID_RE.fullmatch(namespace_id) is not None, "rights.dataset_namespace_id has invalid form")
    display_name = _text(rights.get("display_name"), "rights.display_name", max_length=160)
    license_id = _text(rights.get("license_id"), "rights.license_id", max_length=120)
    storage_boundary = _text(rights.get("storage_boundary"), "rights.storage_boundary", max_length=64)
    _require(storage_boundary == "OPEN_SHARE_ALIKE", "store-directory ODbL data must remain source-isolated")
    attribution = _text(rights.get("attribution"), "rights.attribution", max_length=400)
    source_url = _text(rights.get("source_url"), "rights.source_url", max_length=400)
    license_url = _text(rights.get("license_url"), "rights.license_url", max_length=400)

    allowed = rights.get("allowed_uses")
    _require(isinstance(allowed, list), "rights.allowed_uses must be a list")
    allowed_values = {_text(entry, "rights.allowed_uses entry", max_length=80) for entry in allowed}
    _require(REQUIRED_ALLOWED_USES <= allowed_values, "rights.allowed_uses is missing a directory use")

    authorization = rights.get("authorization")
    _require(isinstance(authorization, Mapping), "rights.authorization must be an object")
    raw_gates = authorization.get("gates")
    _require(isinstance(raw_gates, list), "rights.authorization.gates must be a list")
    gates: list[dict[str, str]] = []
    seen: set[str] = set()
    for index, raw_gate in enumerate(raw_gates):
        _require(isinstance(raw_gate, Mapping), f"rights.authorization.gates[{index}] must be an object")
        gate = _text(raw_gate.get("gate"), f"rights.authorization.gates[{index}].gate", max_length=96)
        _require(gate not in seen, f"rights.authorization contains duplicate gate {gate}")
        seen.add(gate)
        state = _text(raw_gate.get("state"), f"rights.authorization.gates[{index}].state", max_length=32)
        _require(state in {"SATISFIED", "PENDING", "DENIED", "UNKNOWN", "NOT_REQUIRED"}, f"Unsupported gate state: {state}")
        basis = _text(raw_gate.get("basis_id"), f"rights.authorization.gates[{index}].basis_id", max_length=400)
        gates.append({"gate": gate, "state": state, "basisId": basis})
    gate_by_name = {gate["gate"]: gate for gate in gates}
    for required in REQUIRED_RIGHTS_GATES:
        _require(required in gate_by_name, f"rights.authorization is missing {required}")
        _require(gate_by_name[required]["state"] == "SATISFIED", f"rights gate {required} is not SATISFIED")

    return {
        "providerId": provider_id,
        "datasetNamespaceId": namespace_id,
        "displayName": display_name,
        "licenseId": license_id,
        "storageBoundary": storage_boundary,
        "attribution": attribution,
        "sourceUrl": source_url,
        "licenseUrl": license_url,
        "allowedUses": sorted(allowed_values),
        "authorization": {
            "providerId": provider_id,
            "datasetNamespaceId": namespace_id,
            "gates": sorted(gates, key=lambda gate: gate["gate"]),
        },
    }


def _config(config_path: Path) -> tuple[dict[str, Any], dict[str, Any], int, int, int]:
    config = dict(_load_json(config_path, "store-directory configuration"))
    snapshot_id = _text(config.get("snapshot_id"), "config.snapshot_id", max_length=160)
    _require(ID_RE.fullmatch(snapshot_id) is not None, "config.snapshot_id has invalid form")
    generated_at = _parse_timestamp(config.get("generated_at"), "config.generated_at")
    source = config.get("source")
    _require(isinstance(source, Mapping), "config.source must be an object")
    source_snapshot_id = _text(source.get("source_snapshot_id"), "config.source.source_snapshot_id", max_length=160)
    _require(ID_RE.fullmatch(source_snapshot_id) is not None, "config.source.source_snapshot_id has invalid form")
    acquired_at = _parse_timestamp(source.get("acquired_at"), "config.source.acquired_at")
    observed_at = _parse_timestamp(source.get("observed_at"), "config.source.observed_at")
    _require(acquired_at <= generated_at, "config.source.acquired_at is after generated_at")
    _require(observed_at <= generated_at, "config.source.observed_at is after generated_at")
    rights_name = _text(source.get("rights_manifest"), "config.source.rights_manifest", max_length=240)
    rights_path = (config_path.parent / rights_name).resolve()
    rights = _load_rights(rights_path)
    configured_regions = config.get("regions")
    _require(isinstance(configured_regions, list), "config.regions must be a list")
    _require(set(configured_regions) == set(SUPPORTED_REGIONS), "config.regions must be exactly GTA and Metro Vancouver")
    return (
        {
            "snapshotId": snapshot_id,
            "sourceSnapshotId": source_snapshot_id,
            "rightsManifestId": rights_name,
            "acquiredAtEpochMillis": acquired_at,
            "observedAtEpochMillis": observed_at,
        },
        rights,
        generated_at,
        acquired_at,
        observed_at,
    )


def _coordinates(element: Mapping[str, Any], element_type: str) -> tuple[int, int] | None:
    if element_type == "node":
        if "lat" not in element or "lon" not in element:
            return None
        latitude = element["lat"]
        longitude = element["lon"]
    else:
        center = element.get("center")
        if not isinstance(center, Mapping) or "lat" not in center or "lon" not in center:
            return None
        latitude = center["lat"]
        longitude = center["lon"]
    return (
        _coordinate_from_element(latitude, f"{element_type}.latitude", minimum=-90.0, maximum=90.0),
        _coordinate_from_element(longitude, f"{element_type}.longitude", minimum=-180.0, maximum=180.0),
    )


def _optional_tag(tags: Mapping[str, Any], key: str, *, max_length: int) -> str | None:
    raw = tags.get(key)
    if raw is None or not isinstance(raw, str) or not raw.strip():
        return None
    return _text(raw, f"tags.{key}", max_length=max_length)


def _record(element: Mapping[str, Any], *, source: Mapping[str, Any], rights: Mapping[str, Any], observed_at: int) -> dict[str, Any] | None:
    element_type = element.get("type")
    _require(element_type in {"node", "way", "relation"}, "Source element type is unsupported")
    element_id = element.get("id")
    _require(isinstance(element_id, int) and not isinstance(element_id, bool) and element_id > 0, "Source element id is invalid")
    tags = element.get("tags")
    if not isinstance(tags, Mapping):
        return None
    shop = _optional_tag(tags, "shop", max_length=64)
    if shop is None or shop not in SUPPORTED_SHOP_TYPES:
        return None
    coordinates = _coordinates(element, element_type)
    if coordinates is None:
        return None
    latitude_e7, longitude_e7 = coordinates
    region_id = _region_for(latitude_e7, longitude_e7)
    if region_id is None:
        return None

    name = _optional_tag(tags, "name", max_length=MAX_NAME_LENGTH)
    brand = _optional_tag(tags, "brand", max_length=MAX_NAME_LENGTH)
    operator = _optional_tag(tags, "operator", max_length=MAX_NAME_LENGTH)
    if name is None:
        name = brand or operator
    if name is None:
        return None

    result: dict[str, Any] = {
        "recordId": f"osm:{element_type}:{element_id}",
        "sourceElementId": f"{element_type}/{element_id}",
        "sourceElementType": element_type,
        "providerId": rights["providerId"],
        "datasetNamespaceId": rights["datasetNamespaceId"],
        "regionId": region_id,
        "name": name,
        "storeType": shop,
        "latitudeE7": latitude_e7,
        "longitudeE7": longitude_e7,
        "sourceSnapshotId": source["sourceSnapshotId"],
        "licenseId": rights["licenseId"],
        "observedAtEpochMillis": observed_at,
        "confidence": "SOURCE_LISTED",
        "status": "LOCATION_ONLY",
    }
    if brand is not None:
        result["brand"] = brand
    if operator is not None:
        result["operator"] = operator

    address: dict[str, str] = {}
    for output_name, source_name in ADDRESS_TAGS:
        value = _optional_tag(tags, source_name, max_length=MAX_ADDRESS_LENGTH)
        if value is not None:
            address[output_name] = value
    if address:
        result["address"] = address

    for output_name, source_name in (("brandWikidataId", "brand:wikidata"), ("operatorWikidataId", "operator:wikidata")):
        value = _optional_tag(tags, source_name, max_length=32)
        if value is not None and WIKIDATA_RE.fullmatch(value):
            result[output_name] = value
    return result


def _write_bytes_atomically(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(mode="wb", dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False) as handle:
            temporary = Path(handle.name)
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def _ensure_output_dir(path: Path) -> None:
    if path.exists():
        _require(path.is_dir(), f"Output path is not a directory: {path}")
        _require(not any(path.iterdir()), f"Output directory must be missing or empty: {path}")
    else:
        path.mkdir(parents=True)


def build_snapshot(
    config_path: Path,
    input_path: Path,
    output_dir: Path,
    *,
    private_key: Path | None = None,
    require_signature: bool = False,
) -> dict[str, Any]:
    source, rights, generated_at, _acquired_at, observed_at = _config(config_path)
    _require(input_path.is_file(), f"Missing source export: {input_path}")
    try:
        raw = json.loads(input_path.read_bytes())
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise StoreDirectoryBuildError(f"Source export is not valid JSON: {input_path}") from exc
    _require(isinstance(raw, Mapping), "Source export must be a JSON object")
    elements = raw.get("elements")
    _require(isinstance(elements, list), "Source export elements must be a list")

    records: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for index, element in enumerate(elements, start=1):
        _require(isinstance(element, Mapping), f"Source element {index} must be an object")
        normalized = _record(element, source=source, rights=rights, observed_at=observed_at)
        if normalized is None:
            continue
        record_id = normalized["recordId"]
        _require(record_id not in seen_ids, f"Duplicate source record id: {record_id}")
        seen_ids.add(record_id)
        records.append(normalized)
        _require(len(records) <= MAX_RECORDS, "Store directory exceeds the 50,000-record safety ceiling")

    records.sort(key=lambda row: (row["regionId"], _canonical_search_key(row["name"]), row["recordId"]))
    source_bytes = b"".join(_canonical_json(record) for record in records)
    _require(source_bytes and source_bytes.endswith(b"\n"), "Store directory contains no named source locations")
    source_hash = hashlib.sha256(source_bytes).hexdigest()

    region_counts = {
        region_id: sum(1 for row in records if row["regionId"] == region_id)
        for region_id in sorted(SUPPORTED_REGIONS)
    }
    manifest: dict[str, Any] = {
        "schemaVersion": CURRENT_SCHEMA_VERSION,
        "snapshotRole": STORE_DIRECTORY_ROLE,
        "snapshotId": source["snapshotId"],
        "generatedAtEpochMillis": generated_at,
        "source": {
            "providerId": rights["providerId"],
            "datasetNamespaceId": rights["datasetNamespaceId"],
            "displayName": rights["displayName"],
            "licenseId": rights["licenseId"],
            "storageBoundary": rights["storageBoundary"],
            "attribution": rights["attribution"],
            "sourceUrl": rights["sourceUrl"],
            "licenseUrl": rights["licenseUrl"],
            "allowedUses": rights["allowedUses"],
            "authorization": rights["authorization"],
            "sourceSnapshotId": source["sourceSnapshotId"],
            "rightsManifestId": source["rightsManifestId"],
            "acquiredAtEpochMillis": source["acquiredAtEpochMillis"],
            "observedAtEpochMillis": source["observedAtEpochMillis"],
        },
        "regions": [
            {
                "regionId": region_id,
                "displayName": SUPPORTED_REGIONS[region_id]["displayName"],
                "boundingBoxE7": {
                    "minLatitude": int(round(SUPPORTED_REGIONS[region_id]["minLatitude"] * 10_000_000)),
                    "maxLatitude": int(round(SUPPORTED_REGIONS[region_id]["maxLatitude"] * 10_000_000)),
                    "minLongitude": int(round(SUPPORTED_REGIONS[region_id]["minLongitude"] * 10_000_000)),
                    "maxLongitude": int(round(SUPPORTED_REGIONS[region_id]["maxLongitude"] * 10_000_000)),
                },
                "recordCount": region_counts[region_id],
            }
            for region_id in sorted(SUPPORTED_REGIONS)
        ],
        "coverage": {
            "storeRecordCount": len(records),
            "currentOfferRecordCount": 0,
            "currentOfferCoverage": "NOT_INCLUDED",
            "priceCoverage": "NOT_INCLUDED",
            "stockCoverage": "NOT_INCLUDED",
            "availabilityCoverage": "NOT_INCLUDED",
            "regionRecordCounts": region_counts,
        },
        "content": {
            "path": f"sources/{rights['datasetNamespaceId']}.jsonl",
            "sha256": source_hash,
            "recordCount": len(records),
        },
    }

    _ensure_output_dir(output_dir)
    source_path = output_dir / "sources" / f"{rights['datasetNamespaceId']}.jsonl"
    manifest_path = output_dir / "manifest.json"
    _write_bytes_atomically(source_path, source_bytes)
    manifest_bytes = _canonical_json(manifest)
    _write_bytes_atomically(manifest_path, manifest_bytes)
    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
    _write_bytes_atomically(output_dir / "manifest.sha256", f"{manifest_hash}  manifest.json\n".encode("ascii"))

    signature_state = "UNSIGNED"
    if private_key is not None:
        _require(private_key.is_file(), f"Missing signing private key: {private_key}")
        signature_path = output_dir / "manifest.sig"
        try:
            result = subprocess.run(
                ["openssl", "dgst", "-sha256", "-sign", str(private_key), "-out", str(signature_path), str(manifest_path)],
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError as exc:
            raise StoreDirectoryBuildError("openssl is required for signed snapshots") from exc
        _require(result.returncode == 0 and signature_path.is_file() and signature_path.stat().st_size > 0, f"Unable to sign manifest: {result.stderr.strip()}")
        signature_state = "VERIFIED"
        signature_hash = hashlib.sha256(signature_path.read_bytes()).hexdigest()
    else:
        _require(not require_signature, "--require-signature requires --private-key")
        signature_hash = None

    integrity: dict[str, Any] = {
        "schemaVersion": CURRENT_SCHEMA_VERSION,
        "manifestSha256": manifest_hash,
        "signatureState": signature_state,
    }
    if signature_state == "VERIFIED":
        integrity.update(
            {
                "signatureAlgorithm": "SHA256withRSA",
                "signatureFile": "manifest.sig",
                "signatureSha256": signature_hash,
            }
        )
    _write_bytes_atomically(output_dir / "integrity.json", _canonical_json(integrity))
    _write_bytes_atomically(
        output_dir / "ATTRIBUTION.txt",
        (
            "Store directory source: OpenStreetMap contributors.\n"
            "License: Open Database License (ODbL-1.0).\n"
            "This artifact contains source-listed physical locations only; it does not assert price, stock, availability, or current retailer status.\n"
            f"Source snapshot: {source['sourceSnapshotId']} observed at {source['observedAtEpochMillis']} epoch milliseconds.\n"
            f"{rights['sourceUrl']}\n{rights['licenseUrl']}\n"
        ).encode("utf-8"),
    )
    return {"snapshotId": source["snapshotId"], "records": len(records), "regions": region_counts, "manifestSha256": manifest_hash, "signatureState": signature_state}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--private-key", type=Path)
    parser.add_argument("--require-signature", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = build_snapshot(
            args.config,
            args.input,
            args.output,
            private_key=args.private_key,
            require_signature=args.require_signature,
        )
    except (StoreDirectoryBuildError, OSError) as exc:
        print(f"store directory build failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Build a deterministic, rights-gated offline catalog snapshot.

The builder accepts only locally supplied JSON/JSONL files.  It deliberately
does not fetch a provider, parse prices, or infer availability.  Each source
is written to its own file and the manifest records the source namespace,
authorization evidence, acquisition time, record count, and content hash.

The input configuration is intentionally explicit::

    {
      "region_id": "ca-gta",
      "snapshot_id": "2026-09-03-gta",
      "generated_at": "2026-09-03T12:00:00Z",
      "sources": [{
        "rights_manifest": "off-rights.json",
        "catalog": "off-records.jsonl",
        "snapshot_id": "off-2026-09-03",
        "acquired_at": "2026-09-03T11:00:00Z",
        "source_published_at": "2026-09-02T00:00:00Z"
      }]
    }

The rights manifest must contain explicit authorization gate assessments and
all six product-discovery uses.  No wall clock is read, so the same inputs and
timestamps produce byte-identical output.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping


CURRENT_SCHEMA_VERSION = 1
MAX_SOURCES = 32
MAX_RECORDS_PER_SOURCE = 50_000
MAX_TOTAL_RECORDS = 50_000
MAX_ALIASES = 16

DISCOVERY_GATES = (
    "DATA_ACCESS_AUTHORIZED",
    "CONSUMER_DISPLAY_AUTHORIZED",
    "CACHE_AUTHORIZED",
    "INDEX_AUTHORIZED",
    "MOBILE_APP_AUTHORIZED",
    "RETENTION_DELETION_POLICY_DEFINED",
)
DISCOVERY_USES = (
    "discovery",
    "display",
    "cache",
    "index",
    "mobile_app",
    "retention_deletion",
)
FORBIDDEN_RECORD_FIELDS = {
    "price",
    "current_price",
    "previous_price",
    "sale_price",
    "regular_price",
    "promotion",
    "availability",
    "stock",
    "stock_status",
    "quantity",
    "quantity_value",
    "quantity_unit",
    "package_quantity",
    "observed_at",
    "store_id",
    "currency",
}
ALLOWED_RECORD_FIELDS = {
    "record_id",
    "provider_id",
    "dataset_namespace_id",
    "dataset_id",
    "provider_item_id",
    "sku",
    "gtin",
    "display_name",
    "brand",
    "aliases",
    "canonical_search_name",
}
ID_RE = re.compile(r"[a-z0-9][a-z0-9._:-]{0,159}\Z")
NAMESPACE_RE = re.compile(r"[a-z0-9][a-z0-9._-]{0,95}\Z")
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z")


class SnapshotBuildError(ValueError):
    """An input failed a deterministic snapshot admission check."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise SnapshotBuildError(message)


def _text(value: Any, field: str, *, max_length: int) -> str:
    _require(isinstance(value, str), f"{field} must be a string")
    result = value.strip()
    _require(bool(result), f"{field} must not be blank")
    _require(len(result) <= max_length, f"{field} exceeds {max_length} characters")
    return result


def _parse_timestamp(value: Any, field: str) -> int:
    text = _text(value, field, max_length=64)
    normalized = text[:-1] + "+00:00" if text.endswith("Z") else text
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise SnapshotBuildError(f"{field} must be an ISO-8601 timestamp") from exc
    _require(parsed.tzinfo is not None, f"{field} must include an explicit timezone")
    utc_value = parsed.astimezone(timezone.utc)
    epoch = datetime(1970, 1, 1, tzinfo=timezone.utc)
    delta = utc_value - epoch
    result = delta.days * 86_400_000 + delta.seconds * 1_000 + delta.microseconds // 1_000
    _require(result > 0, f"{field} must be after the Unix epoch")
    return result


def _canonical_search_text(value: Any, field: str, *, max_length: int) -> str:
    text = _text(value, field, max_length=max_length)
    # NFKD removes presentation accents without inventing a product fact.  Any
    # non-ASCII punctuation is a separator, never a token or ranking signal.
    ascii_text = (
        unicodedata.normalize("NFKD", text)
        .encode("ascii", "ignore")
        .decode("ascii")
        .lower()
    )
    tokens = re.findall(r"[a-z0-9]+", ascii_text)
    _require(bool(tokens), f"{field} has no usable lowercase search tokens")
    result = " ".join(tokens)
    _require(len(result) <= max_length, f"{field} exceeds {max_length} characters after normalization")
    return result


def _is_valid_gtin(value: str) -> bool:
    if len(value) not in {8, 12, 13, 14} or not value.isdigit():
        return False
    supplied = int(value[-1])
    total = 0
    weight = 3
    for char in reversed(value[:-1]):
        total += int(char) * weight
        weight = 1 if weight == 3 else 3
    return supplied == (10 - total % 10) % 10


def _canonical_json(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def _load_json(path: Path, label: str) -> Mapping[str, Any]:
    _require(path.is_file(), f"Missing {label}: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SnapshotBuildError(f"Unable to read {label}: {path}") from exc
    _require(isinstance(value, dict), f"{label} must be a JSON object")
    return value


def _load_rights(path: Path) -> dict[str, Any]:
    value = _load_json(path, "rights manifest")
    provider_id = _text(value.get("provider_id"), "rights.provider_id", max_length=128)
    dataset_id = _text(
        value.get("dataset_namespace_id", value.get("dataset_id")),
        "rights.dataset_namespace_id",
        max_length=96,
    )
    _require(NAMESPACE_RE.fullmatch(dataset_id) is not None, "rights.dataset_namespace_id has invalid form")
    display_name = _text(value.get("display_name"), "rights.display_name", max_length=160)
    license_id = _text(value.get("license_id"), "rights.license_id", max_length=120)
    storage_boundary = _text(value.get("storage_boundary"), "rights.storage_boundary", max_length=64)
    _require(
        storage_boundary
        in {"OPEN_SHARE_ALIKE", "OPEN_GOVERNMENT", "PROPRIETARY_RESTRICTED", "USER_CONTROLLED", "UNKNOWN"},
        "rights.storage_boundary is not a supported evidence boundary",
    )

    uses = value.get("allowed_uses")
    _require(isinstance(uses, list), "rights.allowed_uses must be a list")
    normalized_uses = [_text(item, "rights.allowed_uses entry", max_length=64) for item in uses]
    _require(set(normalized_uses) >= set(DISCOVERY_USES), "rights.allowed_uses is missing product-discovery uses")

    authorization = value.get("authorization")
    _require(isinstance(authorization, dict), "rights.authorization must be an object")
    gates = authorization.get("gates")
    _require(isinstance(gates, list), "rights.authorization.gates must be a list")
    normalized_gates: list[dict[str, str]] = []
    seen: set[str] = set()
    for index, raw_gate in enumerate(gates):
        _require(isinstance(raw_gate, dict), f"rights.authorization.gates[{index}] must be an object")
        gate = _text(raw_gate.get("gate"), f"rights.authorization.gates[{index}].gate", max_length=80)
        _require(gate not in seen, f"rights.authorization contains duplicate gate {gate}")
        seen.add(gate)
        state = _text(raw_gate.get("state"), f"rights.authorization.gates[{index}].state", max_length=32)
        _require(state in {"SATISFIED", "PENDING", "DENIED", "UNKNOWN", "NOT_REQUIRED"}, f"Unsupported gate state: {state}")
        basis = _text(raw_gate.get("basis_id"), f"rights.authorization.gates[{index}].basis_id", max_length=240)
        normalized_gates.append({"gate": gate, "state": state, "basisId": basis})

    gate_by_name = {entry["gate"]: entry for entry in normalized_gates}
    for gate in DISCOVERY_GATES:
        _require(gate in gate_by_name, f"rights.authorization is missing {gate}")
        _require(gate_by_name[gate]["state"] == "SATISFIED", f"rights gate {gate} is not SATISFIED")

    return {
        "providerId": provider_id,
        "datasetNamespaceId": dataset_id,
        "namespace": {
            "id": dataset_id,
            "displayName": display_name,
            "licenseId": license_id,
            "storageBoundary": storage_boundary,
        },
        "authorization": {
            "providerId": provider_id,
            "datasetNamespaceId": dataset_id,
            "gates": sorted(normalized_gates, key=lambda item: item["gate"]),
        },
        "allowedUses": sorted(set(normalized_uses)),
    }


def _normalize_record(raw: Any, *, provider_id: str, dataset_id: str, line_number: int) -> dict[str, Any]:
    _require(isinstance(raw, dict), f"catalog line {line_number} must be a JSON object")
    forbidden = sorted(set(raw) & FORBIDDEN_RECORD_FIELDS)
    _require(not forbidden, f"catalog line {line_number} contains offer-only fields: {', '.join(forbidden)}")
    unsupported = sorted(set(raw) - ALLOWED_RECORD_FIELDS)
    _require(not unsupported, f"catalog line {line_number} contains unsupported fields: {', '.join(unsupported)}")

    record_id = _text(raw.get("record_id"), f"catalog line {line_number}.record_id", max_length=160)
    _require(ID_RE.fullmatch(record_id) is not None, f"catalog line {line_number}.record_id has invalid form")
    supplied_provider = raw.get("provider_id")
    if supplied_provider is not None:
        _require(_text(supplied_provider, "record.provider_id", max_length=128) == provider_id, f"catalog line {line_number} provider scope mismatch")
    supplied_dataset = raw.get("dataset_namespace_id", raw.get("dataset_id"))
    if supplied_dataset is not None:
        _require(_text(supplied_dataset, "record.dataset_namespace_id", max_length=96) == dataset_id, f"catalog line {line_number} dataset scope mismatch")

    identity: dict[str, str] = {}
    for input_name, output_name in (("provider_item_id", "providerItemId"), ("sku", "sku")):
        if raw.get(input_name) is not None:
            identity[output_name] = _text(raw[input_name], f"catalog line {line_number}.{input_name}", max_length=160)
    if raw.get("gtin") is not None:
        gtin = _text(raw["gtin"], f"catalog line {line_number}.gtin", max_length=14)
        _require(gtin.isdigit() and len(gtin) in {8, 12, 13, 14}, f"catalog line {line_number}.gtin must be 8, 12, 13, or 14 digits")
        identity["gtin"] = gtin
    _require(bool(identity), f"catalog line {line_number} has no source product identity")
    _require(any(key != "gtin" for key in identity) or _is_valid_gtin(identity["gtin"]), f"catalog line {line_number} has only an invalid GTIN")

    display_name = _text(raw.get("display_name"), f"catalog line {line_number}.display_name", max_length=240)
    brand = raw.get("brand")
    if brand is not None:
        brand = _text(brand, f"catalog line {line_number}.brand", max_length=160)
    canonical_name = _canonical_search_text(display_name, f"catalog line {line_number}.display_name", max_length=240)
    supplied_canonical = raw.get("canonical_search_name")
    if supplied_canonical is not None:
        _require(
            _text(supplied_canonical, f"catalog line {line_number}.canonical_search_name", max_length=240) == canonical_name,
            f"catalog line {line_number}.canonical_search_name does not match deterministic normalization",
        )
    canonical_brand = _canonical_search_text(brand, f"catalog line {line_number}.brand", max_length=160) if brand else None

    aliases = raw.get("aliases", [])
    _require(isinstance(aliases, list), f"catalog line {line_number}.aliases must be a list")
    _require(len(aliases) <= MAX_ALIASES, f"catalog line {line_number}.aliases exceeds {MAX_ALIASES}")
    canonical_aliases = sorted(
        {
            _canonical_search_text(alias, f"catalog line {line_number}.aliases entry", max_length=160)
            for alias in aliases
        }
    )
    _require(len(canonical_aliases) <= MAX_ALIASES, f"catalog line {line_number}.aliases are not unique after normalization")

    return {
        "recordId": record_id,
        "providerId": provider_id,
        "datasetNamespaceId": dataset_id,
        "sourceIdentity": identity,
        "displayName": display_name,
        "brand": brand,
        "canonicalSearchName": canonical_name,
        "canonicalSearchBrand": canonical_brand,
        "canonicalSearchAliases": canonical_aliases,
    }


def _read_catalog(path: Path, *, provider_id: str, dataset_id: str) -> tuple[list[dict[str, Any]], dict[str, int]]:
    _require(path.is_file(), f"Missing catalog input: {path}")
    records: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    valid_gtin_count = 0
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    raw = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise SnapshotBuildError(f"catalog line {line_number} is not valid JSON") from exc
                record = _normalize_record(raw, provider_id=provider_id, dataset_id=dataset_id, line_number=line_number)
                _require(record["recordId"] not in seen_ids, f"Duplicate catalog record id: {record['recordId']}")
                seen_ids.add(record["recordId"])
                if record["sourceIdentity"].get("gtin") and _is_valid_gtin(record["sourceIdentity"]["gtin"]):
                    valid_gtin_count += 1
                records.append(record)
                _require(len(records) <= MAX_RECORDS_PER_SOURCE, f"Catalog exceeds {MAX_RECORDS_PER_SOURCE} records")
    except OSError as exc:
        raise SnapshotBuildError(f"Unable to read catalog input: {path}") from exc
    _require(bool(records), f"Catalog input is empty: {path}")
    records.sort(key=lambda item: item["recordId"])
    return records, {"recordCount": len(records), "recordsWithValidGtin": valid_gtin_count}


def _sign_manifest(manifest_path: Path, signature_path: Path, private_key: Path) -> None:
    _require(private_key.is_file(), f"Missing signing private key: {private_key}")
    try:
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(private_key), "-out", str(signature_path), str(manifest_path)],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as exc:
        raise SnapshotBuildError("openssl is required when signing a snapshot") from exc
    if result.returncode != 0:
        raise SnapshotBuildError(f"Unable to sign manifest: {result.stderr.strip() or 'openssl failed'}")
    _require(signature_path.is_file() and signature_path.stat().st_size > 0, "openssl produced an empty signature")


def build_snapshot(config_path: Path, output_dir: Path, *, private_key: Path | None = None, require_signature: bool = False) -> dict[str, Any]:
    if require_signature and private_key is None:
        raise SnapshotBuildError("--require-signature needs --private-key")
    if private_key is not None:
        _require(private_key.is_file(), f"Missing signing private key: {private_key}")
    config = _load_json(config_path, "snapshot configuration")
    region_id = _text(config.get("region_id"), "config.region_id", max_length=128)
    _require(NAMESPACE_RE.fullmatch(region_id) is not None, "config.region_id has invalid form")
    snapshot_id = _text(config.get("snapshot_id"), "config.snapshot_id", max_length=128)
    _require(NAMESPACE_RE.fullmatch(snapshot_id) is not None, "config.snapshot_id has invalid form")
    generated_at = _parse_timestamp(config.get("generated_at"), "config.generated_at")
    sources = config.get("sources")
    _require(isinstance(sources, list) and 1 <= len(sources) <= MAX_SOURCES, f"config.sources must contain 1..{MAX_SOURCES} sources")
    if output_dir.exists():
        _require(output_dir.is_dir(), f"Output path must be a directory: {output_dir}")
        _require(not any(output_dir.iterdir()), f"Output directory must be missing or empty: {output_dir}")

    prepared: list[dict[str, Any]] = []
    namespace_ids: set[str] = set()
    snapshot_refs: set[tuple[str, str]] = set()
    total_records = 0
    for index, source in enumerate(sources):
        _require(isinstance(source, dict), f"config.sources[{index}] must be an object")
        rights_path = (config_path.parent / _text(source.get("rights_manifest"), f"sources[{index}].rights_manifest", max_length=400)).resolve()
        catalog_path = (config_path.parent / _text(source.get("catalog"), f"sources[{index}].catalog", max_length=400)).resolve()
        rights = _load_rights(rights_path)
        namespace_id = rights["datasetNamespaceId"]
        source_snapshot_id = _text(source.get("snapshot_id"), f"sources[{index}].snapshot_id", max_length=128)
        _require(NAMESPACE_RE.fullmatch(source_snapshot_id) is not None, f"sources[{index}].snapshot_id has invalid form")
        acquired_at = _parse_timestamp(source.get("acquired_at"), f"sources[{index}].acquired_at")
        published_value = source.get("source_published_at")
        source_published_at = _parse_timestamp(published_value, f"sources[{index}].source_published_at") if published_value is not None else None
        _require(acquired_at <= generated_at, f"sources[{index}] was acquired after config.generated_at")
        if source_published_at is not None:
            _require(source_published_at <= acquired_at, f"sources[{index}] was published after acquisition")
        _require(namespace_id not in namespace_ids, f"Duplicate dataset namespace: {namespace_id}")
        ref = (rights["providerId"], source_snapshot_id)
        _require(ref not in snapshot_refs, f"Duplicate source snapshot: {rights['providerId']}:{source_snapshot_id}")
        records, metrics = _read_catalog(catalog_path, provider_id=rights["providerId"], dataset_id=namespace_id)
        namespace_ids.add(namespace_id)
        snapshot_refs.add(ref)
        total_records += len(records)
        _require(total_records <= MAX_TOTAL_RECORDS, f"Snapshot exceeds {MAX_TOTAL_RECORDS} records")
        prepared.append({
            "rights": rights,
            "snapshotId": source_snapshot_id,
            "acquiredAt": acquired_at,
            "sourcePublishedAt": source_published_at,
            "records": records,
            "metrics": metrics,
        })

    output_dir.mkdir(parents=True, exist_ok=True)
    sources_dir = output_dir / "sources"
    sources_dir.mkdir()
    manifest_sources: list[dict[str, Any]] = []
    source_metrics: list[dict[str, Any]] = []
    for entry in sorted(prepared, key=lambda item: item["rights"]["datasetNamespaceId"]):
        rights = entry["rights"]
        source_path = sources_dir / f"{rights['datasetNamespaceId']}.jsonl"
        content = b"".join(_canonical_json(record) for record in entry["records"])
        source_path.write_bytes(content)
        content_sha256 = hashlib.sha256(content).hexdigest()
        manifest_sources.append({
            "namespace": rights["namespace"],
            "snapshot": {
                "providerId": rights["providerId"],
                "datasetNamespaceId": rights["datasetNamespaceId"],
                "snapshotId": entry["snapshotId"],
            },
            "authorization": rights["authorization"],
            "recordCount": len(entry["records"]),
            "contentSha256": content_sha256,
            "acquiredAtEpochMillis": entry["acquiredAt"],
            "sourcePublishedAtEpochMillis": entry["sourcePublishedAt"],
        })
        source_metrics.append({
            "datasetNamespaceId": rights["datasetNamespaceId"],
            "recordCount": entry["metrics"]["recordCount"],
            "recordsWithValidGtin": entry["metrics"]["recordsWithValidGtin"],
        })

    manifest = {
        "schemaVersion": CURRENT_SCHEMA_VERSION,
        "snapshotId": snapshot_id,
        "regionId": region_id,
        "generatedAtEpochMillis": generated_at,
        "sources": manifest_sources,
        "coverage": {
            "catalogRecordCount": total_records,
            "recordsWithUsableIdentity": total_records,
            "recordsWithValidGtin": sum(item["recordsWithValidGtin"] for item in source_metrics),
            "sources": source_metrics,
        },
    }
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_bytes(_canonical_json(manifest))
    manifest_sha256 = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
    _require(SHA256_RE.fullmatch(manifest_sha256) is not None, "Manifest hash calculation failed")
    (output_dir / "manifest.sha256").write_text(f"{manifest_sha256}  manifest.json\n", encoding="ascii", newline="\n")

    signature_path = output_dir / "manifest.sig"
    if private_key is not None:
        _sign_manifest(manifest_path, signature_path, private_key)
    integrity: dict[str, Any] = {
        "manifestSha256": manifest_sha256,
        "signatureAlgorithm": "SHA256withRSA",
        "signatureState": "VERIFIED" if private_key is not None else "UNSIGNED",
    }
    if private_key is not None:
        signature_hash = hashlib.sha256(signature_path.read_bytes()).hexdigest()
        integrity.update({"signatureFile": "manifest.sig", "signatureSha256": signature_hash})
    (output_dir / "integrity.json").write_bytes(_canonical_json(integrity))
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--private-key", type=Path)
    parser.add_argument("--require-signature", action="store_true")
    args = parser.parse_args(argv)
    try:
        manifest = build_snapshot(args.config, args.output, private_key=args.private_key, require_signature=args.require_signature)
    except SnapshotBuildError as exc:
        print(f"snapshot build failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({"snapshotId": manifest["snapshotId"], "records": manifest["coverage"]["catalogRecordCount"], "output": str(args.output)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Verify a built offline catalog snapshot without network access.

Verification is intentionally separate from production activation.  It proves
that a local artifact is canonical, bounded, source-isolated, and unchanged
since the manifest was generated.  Signature verification is required by
callers that want a production-admissible artifact; an unsigned snapshot can
still be inspected for debugging but is never marked signed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, Mapping

from tools.build_offline_catalog_snapshot import (
    CURRENT_SCHEMA_VERSION,
    IDENTITY_ONLY_CATALOG_ROLE,
    MAX_RECORDS_PER_SOURCE,
    MAX_SOURCES,
    MAX_TOTAL_RECORDS,
    NO_CURRENT_OFFERS_STATUS,
    NAMESPACE_RE,
    SHA256_RE,
    SnapshotBuildError,
    _canonical_json,
    _canonical_search_text,
    _is_valid_gtin,
    _load_json,
)


OUTPUT_RECORD_FIELDS = {
    "recordId",
    "providerId",
    "datasetNamespaceId",
    "sourceIdentity",
    "displayName",
    "brand",
    "canonicalSearchName",
    "canonicalSearchBrand",
    "canonicalSearchAliases",
}
OUTPUT_IDENTITY_FIELDS = {"providerItemId", "sku", "gtin"}


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise SnapshotBuildError(message)


def _verify_emitted_record(raw: Any, *, provider_id: str, dataset_id: str, line_number: int) -> None:
    _require(isinstance(raw, dict), f"source line {line_number} must be a JSON object")
    _require(set(raw) == OUTPUT_RECORD_FIELDS, f"source line {line_number} has a non-canonical record schema")
    _require(raw["providerId"] == provider_id, f"source line {line_number} provider scope mismatch")
    _require(raw["datasetNamespaceId"] == dataset_id, f"source line {line_number} dataset scope mismatch")
    record_id = raw["recordId"]
    _require(isinstance(record_id, str) and re.fullmatch(r"[a-z0-9][a-z0-9._:-]{0,159}", record_id) is not None, f"source line {line_number} has invalid record id")
    display_name = raw["displayName"]
    _require(isinstance(display_name, str) and display_name.strip() == display_name and 0 < len(display_name) <= 240, f"source line {line_number} has invalid display name")
    _require(raw["canonicalSearchName"] == _canonical_search_text(display_name, "emitted display name", max_length=240), f"source line {line_number} has non-deterministic search name")

    identity = raw["sourceIdentity"]
    _require(isinstance(identity, dict) and set(identity) <= OUTPUT_IDENTITY_FIELDS, f"source line {line_number} has invalid source identity")
    _require(bool(identity), f"source line {line_number} has no source identity")
    for key, value in identity.items():
        _require(isinstance(value, str) and value.strip() == value and 0 < len(value) <= 160, f"source line {line_number} has invalid identity value")
        if key == "gtin":
            _require(value.isdigit() and len(value) in {8, 12, 13, 14}, f"source line {line_number} has invalid GTIN shape")
    _require(any(key != "gtin" for key in identity) or _is_valid_gtin(identity["gtin"]), f"source line {line_number} has only an invalid GTIN")

    brand = raw["brand"]
    if brand is not None:
        _require(isinstance(brand, str) and brand.strip() == brand and 0 < len(brand) <= 160, f"source line {line_number} has invalid brand")
    canonical_brand = raw["canonicalSearchBrand"]
    _require(canonical_brand is None or canonical_brand == _canonical_search_text(brand, "emitted brand", max_length=160), f"source line {line_number} has non-deterministic brand search text")
    aliases = raw["canonicalSearchAliases"]
    _require(isinstance(aliases, list) and len(aliases) <= 16 and aliases == sorted(set(aliases)), f"source line {line_number} has non-canonical aliases")
    for alias in aliases:
        _require(alias == _canonical_search_text(alias, "emitted alias", max_length=160), f"source line {line_number} has non-canonical alias")


def _verify_signature(snapshot_dir: Path, manifest_path: Path, integrity: Mapping[str, Any], public_key: Path | None, require_signature: bool) -> str:
    state = integrity.get("signatureState")
    _require(state in {"VERIFIED", "UNSIGNED"}, "integrity.signatureState is invalid")
    if state == "UNSIGNED":
        _require(public_key is None and not require_signature, "A signed snapshot is required")
        return state
    _require(public_key is not None, "A public key is required to verify a signed snapshot")
    _require(public_key.is_file(), f"Missing signature public key: {public_key}")
    signature_name = integrity.get("signatureFile")
    signature_hash = integrity.get("signatureSha256")
    _require(signature_name == "manifest.sig" and isinstance(signature_hash, str) and SHA256_RE.fullmatch(signature_hash) is not None, "integrity signature metadata is invalid")
    signature_path = snapshot_dir / signature_name
    _require(signature_path.is_file() and hashlib.sha256(signature_path.read_bytes()).hexdigest() == signature_hash, "Manifest signature hash mismatch")
    try:
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(public_key), "-signature", str(signature_path), str(manifest_path)],
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError as exc:
        raise SnapshotBuildError("openssl is required to verify a signed snapshot") from exc
    _require(result.returncode == 0 and "Verified OK" in result.stdout, "Manifest signature verification failed")
    return state


def verify_snapshot(
    snapshot_dir: Path,
    *,
    public_key: Path | None = None,
    require_signature: bool = False,
    source_root: Path | None = None,
) -> dict[str, Any]:
    _require(snapshot_dir.is_dir(), f"Missing snapshot directory: {snapshot_dir}")
    manifest_path = snapshot_dir / "manifest.json"
    manifest_bytes = manifest_path.read_bytes() if manifest_path.is_file() else b""
    _require(bool(manifest_bytes), "Missing or empty manifest.json")
    try:
        manifest = json.loads(manifest_bytes)
    except json.JSONDecodeError as exc:
        raise SnapshotBuildError("manifest.json is not valid JSON") from exc
    _require(isinstance(manifest, dict) and manifest_bytes == _canonical_json(manifest), "manifest.json is not canonical JSON")
    _require(manifest.get("schemaVersion") == CURRENT_SCHEMA_VERSION, "Unsupported snapshot schema")
    _require(manifest.get("catalogRole") == IDENTITY_ONLY_CATALOG_ROLE, "Snapshot catalog role must be IDENTITY_ONLY")
    _require(isinstance(manifest.get("snapshotId"), str) and NAMESPACE_RE.fullmatch(manifest["snapshotId"]) is not None, "Invalid manifest snapshot id")
    _require(isinstance(manifest.get("regionId"), str) and NAMESPACE_RE.fullmatch(manifest["regionId"]) is not None, "Invalid manifest region id")
    _require(isinstance(manifest.get("generatedAtEpochMillis"), int) and manifest["generatedAtEpochMillis"] > 0, "Invalid manifest generated time")
    sources = manifest.get("sources")
    _require(isinstance(sources, list) and 1 <= len(sources) <= MAX_SOURCES, "Invalid manifest source count")
    _require(sum(source.get("recordCount", 0) for source in sources if isinstance(source, dict)) <= MAX_TOTAL_RECORDS, "Manifest exceeds total record bound")

    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
    _require(SHA256_RE.fullmatch(manifest_hash) is not None, "Manifest hash calculation failed")
    checksum_path = snapshot_dir / "manifest.sha256"
    _require(checksum_path.is_file() and checksum_path.read_text(encoding="ascii") == f"{manifest_hash}  manifest.json\n", "Manifest checksum mismatch")

    integrity_path = snapshot_dir / "integrity.json"
    integrity = _load_json(integrity_path, "integrity metadata")
    _require(integrity_path.read_bytes() == _canonical_json(integrity), "integrity.json is not canonical JSON")
    _require(integrity.get("manifestSha256") == manifest_hash, "Integrity metadata references a different manifest")
    signature_state = _verify_signature(snapshot_dir, manifest_path, integrity, public_key, require_signature)

    # Bundled APK assets may share a read-only source payload across several
    # regional manifests.  Standalone snapshots keep the historical
    # ``<snapshot>/sources`` layout; callers can provide the explicit shared
    # root without weakening the manifest's namespace/hash checks.
    resolved_source_root = source_root if source_root is not None else snapshot_dir / "sources"
    _require(resolved_source_root.is_dir(), f"Missing snapshot source directory: {resolved_source_root}")

    seen_namespaces: set[str] = set()
    seen_snapshots: set[tuple[str, str]] = set()
    total_records = 0
    for source_index, source in enumerate(sources):
        _require(isinstance(source, dict), f"manifest source {source_index} must be an object")
        namespace = source.get("namespace")
        snapshot = source.get("snapshot")
        _require(isinstance(namespace, dict) and isinstance(snapshot, dict), f"manifest source {source_index} is missing scope metadata")
        dataset_id = namespace.get("id")
        provider_id = snapshot.get("providerId")
        source_snapshot_id = snapshot.get("snapshotId")
        _require(isinstance(dataset_id, str) and NAMESPACE_RE.fullmatch(dataset_id) is not None, f"manifest source {source_index} has invalid dataset id")
        _require(isinstance(provider_id, str) and provider_id.strip() == provider_id and 0 < len(provider_id) <= 128, f"manifest source {source_index} has invalid provider id")
        _require(isinstance(source_snapshot_id, str) and NAMESPACE_RE.fullmatch(source_snapshot_id) is not None, f"manifest source {source_index} has invalid source snapshot id")
        _require(dataset_id not in seen_namespaces, f"Duplicate dataset namespace: {dataset_id}")
        source_ref = (provider_id, source_snapshot_id)
        _require(source_ref not in seen_snapshots, f"Duplicate source snapshot: {provider_id}:{source_snapshot_id}")
        seen_namespaces.add(dataset_id)
        seen_snapshots.add(source_ref)
        record_count = source.get("recordCount")
        content_hash = source.get("contentSha256")
        _require(isinstance(record_count, int) and 1 <= record_count <= MAX_RECORDS_PER_SOURCE, f"manifest source {source_index} has invalid record count")
        _require(isinstance(content_hash, str) and SHA256_RE.fullmatch(content_hash) is not None, f"manifest source {source_index} has invalid content hash")
        source_path = resolved_source_root / f"{dataset_id}.jsonl"
        content = source_path.read_bytes() if source_path.is_file() else b""
        _require(bool(content) and hashlib.sha256(content).hexdigest() == content_hash, f"Source content hash mismatch: {dataset_id}")
        lines = content.splitlines(keepends=True)
        _require(len(lines) == record_count and all(line.endswith(b"\n") for line in lines), f"Source record count or newline mismatch: {dataset_id}")
        record_ids: set[str] = set()
        for line_number, line in enumerate(lines, start=1):
            try:
                raw = json.loads(line.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise SnapshotBuildError(f"Source line {line_number} is not valid UTF-8 JSON") from exc
            _require(line == _canonical_json(raw), f"Source line {line_number} is not canonical JSON")
            _verify_emitted_record(raw, provider_id=provider_id, dataset_id=dataset_id, line_number=line_number)
            _require(raw["recordId"] not in record_ids, f"Duplicate source record id: {raw['recordId']}")
            record_ids.add(raw["recordId"])
        total_records += record_count

    coverage = manifest.get("coverage")
    _require(isinstance(coverage, dict) and coverage.get("catalogRecordCount") == total_records, "Manifest coverage count mismatch")
    _require(coverage.get("currentOfferRecordCount") == 0, "Identity-only snapshot cannot contain current offers")
    _require(coverage.get("currentOfferCoverage") == NO_CURRENT_OFFERS_STATUS, "Manifest current-offer coverage status is invalid")
    return {"snapshotId": manifest["snapshotId"], "records": total_records, "signatureState": signature_state, "manifestSha256": manifest_hash}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--public-key", type=Path)
    parser.add_argument("--require-signature", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = verify_snapshot(args.snapshot, public_key=args.public_key, require_signature=args.require_signature)
    except (SnapshotBuildError, OSError) as exc:
        print(f"snapshot verification failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

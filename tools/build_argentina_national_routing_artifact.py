#!/usr/bin/env python3
"""Derive one compact national routing object from qualified store evidence.

This tool consumes only an existing content-addressed release workspace.  It
does not open the SEPA ZIP or rebuild any product/offer artifact.  Derived
release workspaces hard-link reused immutable objects and add one deterministic
gzip JSONL routing object per release.
"""

from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import json
import os
import re
import shutil
import tempfile
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable, Mapping

from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS, _canonical_json


ROUTING_SCHEMA_VERSION = "valuepilot-national-routing-v1"
ROUTING_LOGICAL_PATH = "micro-1024/national-routing.jsonl.gz"
DERIVED_RELEASE_SUFFIX = "-routing-v1"
_HEX64 = re.compile(r"[0-9a-f]{64}\Z")
_ALLOWED_GEO_STATUS = frozenset({"VALID", "GEO_INCOMPLETE", "GEO_CONFLICTING"})


class NationalRoutingArtifactError(ValueError):
    """A qualified release could not produce a safe routing artifact."""


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _canonical(value: Any) -> bytes:
    return _canonical_json(value)


def _read_json(path: Path, label: str) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
        value = json.loads(raw)
    except (OSError, UnicodeDecodeError, ValueError) as exc:
        raise NationalRoutingArtifactError(f"{label} is invalid") from exc
    if not isinstance(value, dict) or raw != _canonical(value):
        raise NationalRoutingArtifactError(f"{label} is invalid or non-canonical")
    return value


def _verify_object(source: Path, descriptor: Mapping[str, Any], label: str) -> None:
    digest = descriptor.get("sha256")
    size = descriptor.get("bytes")
    if not isinstance(digest, str) or _HEX64.fullmatch(digest) is None or not isinstance(size, int) or size < 0:
        raise NationalRoutingArtifactError(f"{label} descriptor is invalid")
    if not source.is_file() or source.stat().st_size != size or _sha256_file(source) != digest:
        raise NationalRoutingArtifactError(f"{label} object failed immutable verification")


def _normalized_coordinate(value: Any, label: str, *, minimum: Decimal, maximum: Decimal) -> str:
    if not isinstance(value, str):
        raise NationalRoutingArtifactError(f"{label} is missing")
    try:
        number = Decimal(value)
    except (InvalidOperation, ValueError) as exc:
        raise NationalRoutingArtifactError(f"{label} is invalid") from exc
    if not number.is_finite() or number < minimum or number > maximum:
        raise NationalRoutingArtifactError(f"{label} is outside geographic bounds")
    # Preserve the provider's decimal spelling after deterministic validation;
    # the backend converts the exact published text to its existing Haversine
    # representation.
    return value


def _routing_record(region_id: str, province_code: str, raw: Mapping[str, Any]) -> dict[str, Any]:
    store_key = raw.get("storeKey")
    status = raw.get("geoStatus")
    if not isinstance(store_key, str) or not store_key or not isinstance(status, str) or status not in _ALLOWED_GEO_STATUS:
        raise NationalRoutingArtifactError("store identity or geo status is invalid")
    if raw.get("province") != province_code:
        raise NationalRoutingArtifactError(f"{region_id} store province does not match its published region")
    if status == "VALID":
        latitude = _normalized_coordinate(raw.get("latitude"), f"{region_id}/{store_key} latitude", minimum=Decimal("-90"), maximum=Decimal("90"))
        longitude = _normalized_coordinate(raw.get("longitude"), f"{region_id}/{store_key} longitude", minimum=Decimal("-180"), maximum=Decimal("180"))
    else:
        if raw.get("latitude") is not None or raw.get("longitude") is not None:
            raise NationalRoutingArtifactError(f"{region_id}/{store_key} non-valid geography exposes coordinates")
        latitude = None
        longitude = None
    return {"geoStatus": status, "latitude": latitude, "longitude": longitude, "regionId": region_id, "storeKey": store_key}


def _iter_store_records(path: Path, region_id: str, province_code: str) -> Iterable[dict[str, Any]]:
    try:
        with gzip.open(path, "rt", encoding="utf-8", newline="") as handle:
            for line_number, line in enumerate(handle, start=1):
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, ValueError) as exc:
                    raise NationalRoutingArtifactError(f"{region_id} store index line {line_number} is invalid") from exc
                if not isinstance(value, Mapping):
                    raise NationalRoutingArtifactError(f"{region_id} store index line {line_number} is not an object")
                yield _routing_record(region_id, province_code, value)
    except (OSError, EOFError) as exc:
        raise NationalRoutingArtifactError(f"{region_id} store index is not a valid gzip stream") from exc


def _release_manifest(workspace: Path, release_id: str) -> dict[str, Any]:
    release_root = workspace / "releases" / release_id
    manifest = _read_json(release_root / "manifest.json", f"{release_id} manifest")
    if manifest.get("completionState") != "COMPLETE" or manifest.get("releaseId") != release_id:
        raise NationalRoutingArtifactError(f"{release_id} is not a complete release")
    return manifest


def _store_descriptors(manifest: Mapping[str, Any]) -> dict[str, Mapping[str, Any]]:
    descriptors = manifest.get("objects")
    if not isinstance(descriptors, list):
        raise NationalRoutingArtifactError("release object descriptors are missing")
    result: dict[str, Mapping[str, Any]] = {}
    for descriptor in descriptors:
        if not isinstance(descriptor, Mapping) or not isinstance(descriptor.get("path"), str):
            raise NationalRoutingArtifactError("release object descriptor is invalid")
        path = descriptor["path"]
        if "/store-index.jsonl.gz" in path:
            result[path] = descriptor
    return result


def build_national_routing_artifact(workspace: Path | str, release_id: str, output_path: Path | str) -> dict[str, Any]:
    """Build and return the descriptor for one immutable routing object."""

    root = Path(workspace).resolve()
    manifest = _release_manifest(root, release_id)
    descriptors = _store_descriptors(manifest)
    specs = {spec.region_id: spec for spec in ARGENTINA_REGIONS}
    if set(path.split("/")[2] for path in descriptors if path.startswith("micro-1024/regions/")) != set(specs):
        raise NationalRoutingArtifactError("release store indexes do not cover the exact Argentina region set")

    stores: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    for region_id in sorted(specs):
        logical_path = f"micro-1024/regions/{region_id}/store-index.jsonl.gz"
        descriptor = descriptors.get(logical_path)
        if descriptor is None:
            raise NationalRoutingArtifactError(f"{region_id} store index is missing")
        object_path = root / "objects" / "sha256" / str(descriptor.get("sha256"))
        _verify_object(object_path, descriptor, logical_path)
        for record in _iter_store_records(object_path, region_id, specs[region_id].province_code):
            identity = (region_id, record["storeKey"])
            if identity in seen:
                raise NationalRoutingArtifactError(f"duplicate store identity: {region_id}/{record['storeKey']}")
            seen.add(identity)
            stores.append(record)
    stores.sort(key=lambda value: (value["regionId"], value["storeKey"]))

    target = Path(output_path).resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix=f".{target.name}.", dir=str(target.parent))
    temporary = Path(temporary_name)
    uncompressed_hash = hashlib.sha256()
    uncompressed_bytes = 0
    record_count = 0
    try:
        with os.fdopen(fd, "wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0, compresslevel=9) as compressed:
                for record in stores:
                    line = _canonical(record)
                    compressed.write(line)
                    uncompressed_hash.update(line)
                    uncompressed_bytes += len(line)
                    record_count += 1
            raw.flush()
            os.fsync(raw.fileno())
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    return {
        "path": ROUTING_LOGICAL_PATH,
        "bytes": target.stat().st_size,
        "compressedBytes": target.stat().st_size,
        "sha256": _sha256_file(target),
        "uncompressedBytes": uncompressed_bytes,
        "uncompressedSha256": uncompressed_hash.hexdigest(),
        "recordCount": record_count,
        "compression": "gzip",
        "schemaVersion": ROUTING_SCHEMA_VERSION,
        "regions": sorted(specs),
    }


def _hard_link_objects(source_workspace: Path, output_workspace: Path, manifest: Mapping[str, Any]) -> None:
    descriptors = manifest.get("objects")
    if not isinstance(descriptors, list):
        raise NationalRoutingArtifactError("release object descriptors are missing")
    for descriptor in descriptors:
        if not isinstance(descriptor, Mapping):
            raise NationalRoutingArtifactError("release object descriptor is invalid")
        digest = descriptor.get("sha256")
        if not isinstance(digest, str) or _HEX64.fullmatch(digest) is None:
            raise NationalRoutingArtifactError("release object hash is invalid")
        source = source_workspace / "objects" / "sha256" / digest
        target = output_workspace / "objects" / "sha256" / digest
        _verify_object(source, descriptor, str(descriptor.get("path")))
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            _verify_object(target, descriptor, str(descriptor.get("path")))
        else:
            try:
                os.link(source, target)
            except OSError as exc:
                raise NationalRoutingArtifactError("reused immutable object could not be hard-linked") from exc


def derive_release_workspace(source_workspace: Path | str, output_workspace: Path | str, release_ids: Iterable[str]) -> dict[str, Any]:
    """Create derived release manifests without changing the source workspace."""

    source = Path(source_workspace).resolve()
    output = Path(output_workspace).resolve()
    if source == output or output.exists():
        raise NationalRoutingArtifactError("derived workspace must be a new path")
    ids = tuple(release_ids)
    if not ids:
        raise NationalRoutingArtifactError("at least one release is required")
    output.mkdir(parents=True)
    (output / "objects" / "sha256").mkdir(parents=True)
    (output / "releases").mkdir(parents=True)
    derived: list[dict[str, Any]] = []
    try:
        for release_id in ids:
            base = _release_manifest(source, release_id)
            derived_id = f"{release_id}{DERIVED_RELEASE_SUFFIX}"
            release_dir = output / "releases" / derived_id
            release_dir.mkdir(parents=True)
            _hard_link_objects(source, output, base)
            routing_path = output / "objects" / "sha256" / "routing.pending"
            descriptor = build_national_routing_artifact(source, release_id, routing_path)
            routing_digest = descriptor["sha256"]
            final_routing = output / "objects" / "sha256" / routing_digest
            if final_routing != routing_path:
                if final_routing.exists():
                    routing_path.unlink()
                else:
                    os.replace(routing_path, final_routing)
            derived_manifest = copy.deepcopy(base)
            derived_manifest["releaseId"] = derived_id
            derived_manifest["routingArtifact"] = {**descriptor, "sourceReleaseId": release_id}
            objects = [dict(item) for item in derived_manifest["objects"]]
            objects.append({key: value for key, value in descriptor.items() if key not in {"regions"}})
            derived_manifest["objects"] = sorted(objects, key=lambda item: str(item.get("path", "")))
            raw = _canonical(derived_manifest)
            manifest_path = release_dir / "manifest.json"
            manifest_path.write_bytes(raw)
            # Keep the sidecar byte-exact across Windows and POSIX.  The
            # release verifier intentionally accepts only the canonical LF
            # representation because this digest is part of the immutable
            # publication contract.
            (release_dir / "manifest.sha256").write_bytes(f"{hashlib.sha256(raw).hexdigest()}  manifest.json\n".encode("ascii"))
            derived.append({"sourceReleaseId": release_id, "derivedReleaseId": derived_id, "routing": descriptor})
        plan = {
            "schemaVersion": "valuepilot-derived-routing-publication-plan-v1",
            "sourceWorkspace": str(source),
            "derivedWorkspace": str(output),
            "oldReleasesUnchanged": True,
            "releases": derived,
            "activeReleaseId": derived[-1]["derivedReleaseId"],
            "publicationOrder": ["immutable_objects", "verify_objects", "manifests", "verify_manifests", "active_pointer_last"],
        }
        (output / "ROUTING_RELEASE_PLAN.json").write_bytes(_canonical(plan))
        return plan
    except Exception:
        shutil.rmtree(output, ignore_errors=True)
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-workspace", required=True, type=Path)
    parser.add_argument("--output-workspace", required=True, type=Path)
    parser.add_argument("--release-id", action="append", dest="release_ids")
    args = parser.parse_args(argv)
    try:
        ids = tuple(args.release_ids or sorted(path.name for path in (args.source_workspace / "releases").iterdir() if path.is_dir()))
        plan = derive_release_workspace(args.source_workspace, args.output_workspace, ids)
    except (NationalRoutingArtifactError, OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"national routing derivation failed: {exc}", file=__import__("sys").stderr)
        return 2
    print(json.dumps({"derivedWorkspace": plan["derivedWorkspace"], "releases": plan["releases"]}, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


__all__ = [
    "DERIVED_RELEASE_SUFFIX",
    "NationalRoutingArtifactError",
    "ROUTING_LOGICAL_PATH",
    "ROUTING_SCHEMA_VERSION",
    "build_national_routing_artifact",
    "derive_release_workspace",
]

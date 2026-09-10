"""Verified compact national routing artifact reader."""

from __future__ import annotations

import gzip
import hashlib
import json
import math
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from tools.build_argentina_sepa_national_shards import ARGENTINA_REGIONS


ROUTING_SCHEMA_VERSION = "valuepilot-national-routing-v1"
_ALLOWED_GEO_STATUS = frozenset({"VALID", "GEO_INCOMPLETE", "GEO_CONFLICTING"})


class NationalRoutingError(ValueError):
    """A routing artifact failed closed verification."""


@dataclass(frozen=True, slots=True)
class RoutingPoint:
    region_id: str
    store_key: str
    latitude: float
    longitude: float


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _coordinate(value: Any, label: str, minimum: float, maximum: float) -> float:
    if not isinstance(value, str):
        raise NationalRoutingError(f"{label} is missing")
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise NationalRoutingError(f"{label} is invalid") from exc
    if not math.isfinite(result) or result < minimum or result > maximum:
        raise NationalRoutingError(f"{label} is outside geographic bounds")
    return result


def load_national_routing(root: Path | str, descriptor: Mapping[str, Any], *, expected_region_ids: frozenset[str] | None = None) -> dict[str, tuple[RoutingPoint, ...]]:
    """Verify and load only compact valid geography from one routing object."""

    root = Path(root).resolve()
    path_value = descriptor.get("path")
    if not isinstance(path_value, str) or not path_value or os.path.isabs(path_value) or ".." in Path(path_value).parts:
        raise NationalRoutingError("routing artifact path is invalid")
    if descriptor.get("schemaVersion") != ROUTING_SCHEMA_VERSION or descriptor.get("compression") != "gzip":
        raise NationalRoutingError("routing artifact schema is invalid")
    size = descriptor.get("bytes")
    digest = descriptor.get("sha256")
    uncompressed_size = descriptor.get("uncompressedBytes")
    uncompressed_digest = descriptor.get("uncompressedSha256")
    record_count = descriptor.get("recordCount")
    if not isinstance(size, int) or size < 0 or not isinstance(digest, str) or len(digest) != 64 or not isinstance(uncompressed_size, int) or uncompressed_size < 0 or not isinstance(uncompressed_digest, str) or len(uncompressed_digest) != 64 or not isinstance(record_count, int) or record_count < 0:
        raise NationalRoutingError("routing artifact descriptor is invalid")
    path = (root / Path(*path_value.split("/"))).resolve()
    if root not in path.parents or not path.is_file() or path.stat().st_size != size or _sha256_file(path) != digest:
        raise NationalRoutingError("routing artifact bytes or hash mismatch")

    expected = expected_region_ids or frozenset(spec.region_id for spec in ARGENTINA_REGIONS)
    if descriptor.get("regions") != sorted(expected):
        raise NationalRoutingError("routing artifact region coverage metadata is invalid")
    by_region: dict[str, list[RoutingPoint]] = {region_id: [] for region_id in expected}
    seen: set[tuple[str, str]] = set()
    compressed_hash = hashlib.sha256()
    uncompressed_hash = hashlib.sha256()
    compressed_bytes = 0
    uncompressed_bytes = 0
    records = 0
    try:
        with path.open("rb") as raw:
            for chunk in iter(lambda: raw.read(1024 * 1024), b""):
                compressed_hash.update(chunk)
                compressed_bytes += len(chunk)
        with gzip.open(path, "rb") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.endswith(b"\n"):
                    raise NationalRoutingError(f"routing artifact line {line_number} is not newline terminated")
                uncompressed_bytes += len(line)
                uncompressed_hash.update(line)
                try:
                    value = json.loads(line)
                except (UnicodeDecodeError, ValueError) as exc:
                    raise NationalRoutingError(f"routing artifact line {line_number} is invalid JSON") from exc
                if not isinstance(value, Mapping):
                    raise NationalRoutingError(f"routing artifact line {line_number} is not an object")
                canonical = (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
                if line != canonical:
                    raise NationalRoutingError(f"routing artifact line {line_number} is not canonical JSON")
                region_id = value.get("regionId")
                store_key = value.get("storeKey")
                status = value.get("geoStatus")
                if not isinstance(region_id, str) or region_id not in expected or not isinstance(store_key, str) or not store_key or not isinstance(status, str) or status not in _ALLOWED_GEO_STATUS:
                    raise NationalRoutingError(f"routing artifact line {line_number} identity is invalid")
                identity = (region_id, store_key)
                if identity in seen:
                    raise NationalRoutingError(f"duplicate routing store identity: {region_id}/{store_key}")
                seen.add(identity)
                if status == "VALID":
                    point = RoutingPoint(region_id, store_key, _coordinate(value.get("latitude"), f"{region_id}/{store_key} latitude", -90.0, 90.0), _coordinate(value.get("longitude"), f"{region_id}/{store_key} longitude", -180.0, 180.0))
                    by_region[region_id].append(point)
                elif value.get("latitude") is not None or value.get("longitude") is not None:
                    raise NationalRoutingError(f"routing artifact line {line_number} non-valid geography has coordinates")
                records += 1
    except (OSError, EOFError) as exc:
        raise NationalRoutingError("routing artifact gzip stream is invalid") from exc
    if compressed_bytes != size or compressed_hash.hexdigest() != digest or uncompressed_bytes != uncompressed_size or uncompressed_hash.hexdigest() != uncompressed_digest or records != record_count:
        raise NationalRoutingError("routing artifact integrity or region coverage mismatch")
    return {region_id: tuple(sorted(points, key=lambda point: point.store_key)) for region_id, points in sorted(by_region.items())}


__all__ = ["NationalRoutingError", "ROUTING_SCHEMA_VERSION", "RoutingPoint", "load_national_routing"]

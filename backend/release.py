"""Immutable release pointer and request pinning."""

from __future__ import annotations

import json
import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping

from .clock import Clock, SystemClock, freshness
from .object_store import ObjectStoreError, LocalFilesystemObjectStore, validate_key


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class ReleaseHandle:
    release_id: str
    release_date: str
    root: Path
    profile: Mapping[str, Any]
    freshness_status: str


class ReleaseManager:
    """Pins one active release for the full lifetime of a request."""

    def __init__(self, root: Path | str, *, clock: Clock | None = None, max_age_days: int = 7, profile: str | None = None):
        self.root = Path(root).resolve()
        self.clock = clock or SystemClock()
        self.max_age_days = max_age_days
        self.profile = profile
        self._lock = threading.RLock()
        self._cached: ReleaseHandle | None = None

    def _bootstrap(self, root: Path) -> dict[str, Any]:
        path = root / "bootstrap.json"
        try:
            raw = path.read_bytes()
            data = json.loads(raw)
        except (OSError, ValueError) as exc:
            raise ReleaseError("qualified release bootstrap is unavailable") from exc
        if not isinstance(data, dict) or data.get("completionState") != "COMPLETE" or data.get("productionUiAuthorized") is not False:
            raise ReleaseError("qualified release is incomplete or UI-authorized")
        release = data.get("release")
        if not isinstance(release, dict) or not isinstance(release.get("id"), str) or not isinstance(release.get("date"), str):
            raise ReleaseError("release metadata is invalid")
        return data

    def _resolved_root(self) -> Path:
        """Resolve the active immutable generation once per pin.

        A plain qualified root (the local M5 layout) remains supported.  A
        published object-store mirror instead contains ``control/active.json``
        and ``releases/<id>/``; malformed pointers fail closed rather than
        silently falling back to another generation.
        """
        pointer = self.root / "control" / "active.json"
        if not pointer.exists():
            return self.root
        try:
            value = json.loads(pointer.read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise ReleaseError("active release pointer is invalid") from exc
        release_id = value.get("releaseId") if isinstance(value, dict) else None
        if not isinstance(release_id, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", release_id):
            raise ReleaseError("active release pointer is invalid")
        candidate = (self.root / "releases" / release_id).resolve()
        if self.root not in candidate.parents or not (candidate / "bootstrap.json").is_file():
            raise ReleaseError("active release generation is unavailable")
        return candidate

    def _handle(self) -> ReleaseHandle:
        resolved_root = self._resolved_root()
        data = self._bootstrap(resolved_root)
        release = data["release"]
        status = freshness(release["date"], clock=self.clock, max_age_days=self.max_age_days)
        profile_data = data.get("partitioning", {})
        logical = profile_data.get("logicalPartitionCount")
        physical = profile_data.get("physicalPackCount")
        profile_name = self.profile or (f"{logical}/{physical}" if isinstance(logical, int) and isinstance(physical, int) else "qualified")
        return ReleaseHandle(release["id"], release["date"], resolved_root, {"logical": logical, "physical": physical, "name": profile_name}, status)

    def pin(self, *, require_fresh: bool = True) -> ReleaseHandle:
        with self._lock:
            handle = self._handle()
            self._cached = handle
        if require_fresh and handle.freshness_status != "FRESH":
            raise ReleaseError("CURRENT_PRICE_EVIDENCE_UNAVAILABLE")
        return handle

    def status(self) -> dict[str, Any]:
        try:
            handle = self._handle()
            return {"service": "ready", "releaseId": handle.release_id, "releaseDate": handle.release_date, "freshness": handle.freshness_status, "profile": dict(handle.profile)}
        except ReleaseError as exc:
            return {"service": "not_ready", "error": str(exc)}


class SafePublisher:
    """Single-writer local publisher for already-verified generated roots."""

    def __init__(self, store: LocalFilesystemObjectStore):
        self.store = store
        self._lock = threading.Lock()

    def publish_directory(self, source_root: Path | str, *, release_id: str, active_key: str = "control/active.json") -> dict[str, Any]:
        source = Path(source_root).resolve()
        if not (source / "bootstrap.json").is_file():
            raise ReleaseError("source release is incomplete")
        with self._lock:
            prefix = f"releases/{release_id}"
            uploaded = 0
            for path in sorted(source.rglob("*")):
                if not path.is_file():
                    continue
                relative = path.relative_to(source).as_posix()
                if relative.endswith(".zip"):
                    raise ReleaseError("raw provider archive cannot be published")
                self.store.put_immutable(f"{prefix}/{relative}", path.read_bytes())
                uploaded += 1
            pointer = json.dumps({"releaseId": release_id, "schemaVersion": "valuepilot-active-release-v1"}, sort_keys=True, separators=(",", ":")).encode()
            expected = self.store.head(active_key).etag if self.store.exists(active_key) else None
            if expected is None:
                self.store.put_immutable(active_key, pointer)
            else:
                self.store.compare_and_swap(active_key, pointer, expected_etag=expected)
            return {"releaseId": release_id, "uploadedObjects": uploaded, "activePointer": active_key}


__all__ = ["ReleaseError", "ReleaseHandle", "ReleaseManager", "SafePublisher"]

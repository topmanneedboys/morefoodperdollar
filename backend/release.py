"""Immutable release pointer and request pinning."""

from __future__ import annotations

import json
import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping

from .clock import Clock, SystemClock, freshness
from .artifacts import FilesystemReleaseArtifactStore, ManifestReleaseArtifactStore, ReleaseArtifactError, ReleaseArtifactStore
from .object_store import LocalFilesystemObjectStore, ObjectStore, ObjectStoreError, validate_key


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class ReleaseHandle:
    release_id: str
    release_date: str
    root: Path | None
    profile: Mapping[str, Any]
    freshness_status: str
    artifacts: ReleaseArtifactStore


class ReleaseManager:
    """Pins one active release for the full lifetime of a request."""

    def __init__(self, root: Path | str | None = None, *, object_store: ObjectStore | None = None, clock: Clock | None = None, max_age_days: int = 7, profile: str | None = None):
        if (root is None) == (object_store is None):
            raise ValueError("exactly one release root or object store is required")
        self.root = Path(root).resolve() if root is not None else None
        self.object_store = object_store
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
        if self.root is None:
            raise ReleaseError("local release root is unavailable")
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

    @staticmethod
    def _release_id(value: Any) -> str:
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", value):
            raise ReleaseError("active release pointer is invalid")
        return value

    @staticmethod
    def _json_bytes(raw: bytes, label: str, *, limit: int = 16 * 1024 * 1024) -> dict[str, Any]:
        if len(raw) > limit:
            raise ReleaseError(f"{label} is too large")
        try:
            value = json.loads(raw)
        except (UnicodeDecodeError, ValueError) as exc:
            raise ReleaseError(f"{label} is invalid") from exc
        if not isinstance(value, dict):
            raise ReleaseError(f"{label} is invalid")
        return value

    @staticmethod
    def _manifest_checksum(raw: bytes, sidecar: bytes) -> None:
        try:
            value = sidecar.decode("ascii")
        except UnicodeDecodeError as exc:
            raise ReleaseError("release manifest checksum is invalid") from exc
        expected = re.fullmatch(r"([0-9a-f]{64})  manifest\.json\n", value)
        if expected is None:
            raise ReleaseError("release manifest checksum is invalid")
        import hashlib
        if hashlib.sha256(raw).hexdigest() != expected.group(1):
            raise ReleaseError("release manifest checksum mismatch")

    def _store_handle(self, store: ObjectStore, *, root: Path | None) -> ReleaseHandle:
        try:
            pointer = self._json_bytes(store.get("control/active.json"), "active release pointer", limit=64 * 1024)
        except ObjectStoreError as exc:
            raise ReleaseError("active release pointer is unavailable") from exc
        release_id = self._release_id(pointer.get("releaseId"))
        manifest_key = f"releases/{release_id}/manifest.json"
        checksum_key = f"releases/{release_id}/manifest.sha256"
        try:
            manifest_raw = store.get(manifest_key)
            checksum_raw = store.get(checksum_key)
        except ObjectStoreError as exc:
            raise ReleaseError("active release manifest is unavailable") from exc
        self._manifest_checksum(manifest_raw, checksum_raw)
        manifest = self._json_bytes(manifest_raw, "release manifest")
        if manifest.get("completionState") != "COMPLETE" or manifest.get("releaseId") != release_id:
            raise ReleaseError("release manifest is incomplete or mismatched")
        source = manifest.get("source")
        release_date = source.get("releaseDate") if isinstance(source, Mapping) else None
        if not isinstance(release_date, str):
            raise ReleaseError("release manifest date is invalid")
        try:
            artifacts = ManifestReleaseArtifactStore(store, manifest, release_id=release_id)
        except ReleaseArtifactError as exc:
            raise ReleaseError(str(exc)) from exc
        backend_profile = manifest.get("backendProfile")
        logical = backend_profile.get("logicalPartitionCount") if isinstance(backend_profile, Mapping) else None
        physical = backend_profile.get("physicalPackCount") if isinstance(backend_profile, Mapping) else None
        profile_name = self.profile or (f"{logical}/{physical}" if isinstance(logical, int) and isinstance(physical, int) else "qualified")
        status = freshness(release_date, clock=self.clock, max_age_days=self.max_age_days)
        return ReleaseHandle(release_id, release_date, root, {"logical": logical, "physical": physical, "name": profile_name}, status, artifacts)

    def _handle(self) -> ReleaseHandle:
        if self.object_store is not None:
            return self._store_handle(self.object_store, root=None)
        if self.root is None:
            raise ReleaseError("release root is unavailable")
        # A local M9 content-addressed workspace follows the same pointer and
        # manifest contract as the remote store.  Keep the older direct
        # bootstrap root below for local qualification compatibility.
        active_manifest = self.root / "control" / "active.json"
        if active_manifest.is_file():
            try:
                local_store = LocalFilesystemObjectStore(self.root)
                active = self._json_bytes(local_store.get("control/active.json"), "active release pointer", limit=64 * 1024)
                release_id = self._release_id(active.get("releaseId"))
                if local_store.exists(f"releases/{release_id}/manifest.json"):
                    return self._store_handle(local_store, root=None)
            except (ObjectStoreError, ReleaseError):
                # Do not fall through when a pointer is present but malformed;
                # the only compatible legacy case is a complete bootstrap
                # generation, which _resolved_root validates explicitly.
                if not (self.root / "releases").is_dir():
                    raise
        resolved_root = self._resolved_root()
        data = self._bootstrap(resolved_root)
        release = data["release"]
        status = freshness(release["date"], clock=self.clock, max_age_days=self.max_age_days)
        profile_data = data.get("partitioning", {})
        logical = profile_data.get("logicalPartitionCount")
        physical = profile_data.get("physicalPackCount")
        profile_name = self.profile or (f"{logical}/{physical}" if isinstance(logical, int) and isinstance(physical, int) else "qualified")
        return ReleaseHandle(release["id"], release["date"], resolved_root, {"logical": logical, "physical": physical, "name": profile_name}, status, FilesystemReleaseArtifactStore(resolved_root))

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

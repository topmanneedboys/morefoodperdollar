"""National router and range-addressable Argentina evidence reader."""

from __future__ import annotations

import json
import threading
import time
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path
from typing import Any, Mapping, Sequence

from backend.cache import VerifiedEvidenceCache
from backend.release import ReleaseHandle

try:
    from tools.argentina_national_routing import RoutingPoint, load_national_routing
    from tools.argentina_sepa_micro_partition import ARGENTINA_REGIONS, MicroPartitionQueryError, MicroQueryPlan, _load_stores, load_micro_region_contract, load_micro_region_routing
    from tools.argentina_sepa_query import _offer_result, _search_candidates, straight_line_distance_km
    from tools.argentina_sepa_query import _iter_gzip_records
    from tools.argentina_shopping_intelligence import evaluate_argentina_provider_result
    from tools.consumer_input_intelligence import CatalogIndex, ConsumerInputInterpreter, InputIntelligenceError
    from tools.shopping_intelligence_engine import ShoppingIntelligenceError, ShoppingRequest
    from tools.verify_argentina_sepa_micro_partition_mobile import decode_member_bytes
except ModuleNotFoundError:  # pragma: no cover - direct module invocation
    from argentina_national_routing import RoutingPoint, load_national_routing
    from argentina_sepa_micro_partition import ARGENTINA_REGIONS, MicroPartitionQueryError, MicroQueryPlan, _load_stores, load_micro_region_contract, load_micro_region_routing
    from argentina_sepa_query import _offer_result, _search_candidates, straight_line_distance_km
    from argentina_sepa_query import _iter_gzip_records
    from argentina_shopping_intelligence import evaluate_argentina_provider_result
    from consumer_input_intelligence import CatalogIndex, ConsumerInputInterpreter, InputIntelligenceError
    from shopping_intelligence_engine import ShoppingIntelligenceError, ShoppingRequest
    from verify_argentina_sepa_micro_partition_mobile import decode_member_bytes

from .artifacts import FilesystemReleaseArtifactStore, ManifestReleaseArtifactStore, ReleaseArtifactError, ReleaseArtifactStore


MAX_BACKEND_RADIUS_KM = Decimal("50")
MAX_ITEMS = 10
MAX_CANDIDATES = 100_000
MAX_OFFERS = 100_000
MAX_INPUT_VOCABULARY_RECORDS = 100_000
MAX_REMOTE_ROUTING_CONCURRENCY = 4
MAX_SEARCH_CACHE_ENTRIES = 512
MAX_INPUT_CATALOG_CACHE_ENTRIES = 8


class BackendQueryError(ValueError):
    pass


class CurrentPriceEvidenceUnavailable(BackendQueryError):
    code = "CURRENT_PRICE_EVIDENCE_UNAVAILABLE"


@dataclass
class RequestMetrics:
    active_lookup_ms: float = 0.0
    routing_ms: float = 0.0
    regional_search_ms: float = 0.0
    range_reads_ms: float = 0.0
    decode_ms: float = 0.0
    engine_ms: float = 0.0
    serialization_ms: float = 0.0
    total_ms: float = 0.0
    object_reads: int = 0
    range_reads: int = 0
    bytes_read: int = 0
    compressed_bytes: int = 0
    decompressed_bytes: int = 0
    physical_pack_bytes: int = 0
    cache_hits: int = 0
    cache_misses: int = 0
    regions_queried: list[str] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        return {
            "activeLookupMs": round(self.active_lookup_ms, 3),
            "routingMs": round(self.routing_ms, 3),
            "regionalSearchMs": round(self.regional_search_ms, 3),
            "rangeReadsMs": round(self.range_reads_ms, 3),
            "decompressionMs": round(self.decode_ms, 3),
            "engineMs": round(self.engine_ms, 3),
            "serializationMs": round(self.serialization_ms, 3),
            "totalMs": round(self.total_ms, 3),
            "objectReads": self.object_reads,
            "rangeReads": self.range_reads,
            "bytesRead": self.bytes_read,
            "compressedBytes": self.compressed_bytes,
            "decompressedBytes": self.decompressed_bytes,
            "physicalPackBytes": self.physical_pack_bytes,
            "cacheHits": self.cache_hits,
            "cacheMisses": self.cache_misses,
            "regionsQueried": list(self.regions_queried),
        }


@dataclass(frozen=True)
class RegionSelection:
    region_id: str
    store_keys: tuple[str, ...]


class NationalStoreRouter:
    """Route by exact published store province and Haversine distance only."""

    def __init__(self, root: Path | str | None, *, artifacts: ReleaseArtifactStore):
        self.root = Path(root).resolve() if root is not None else None
        self.artifacts = artifacts
        self._contracts: dict[str, Any] = {}
        self._stores: dict[str, dict[str, Mapping[str, Any]]] = {}
        self._routing_stores: dict[str, dict[str, Mapping[str, Any]]] = {}
        self._routing_points: dict[str, tuple[RoutingPoint, ...]] = {}
        self._routing_bootstrap: Mapping[str, Any] | None = None
        self._routing_mode: str | None = None
        self._lock = threading.RLock()
        # Routing initialization is a separate state machine from the data
        # caches.  Its lock protects only state transitions; no network or
        # filesystem operation may run while it is held.
        self._routing_state_lock = threading.Lock()
        self._bootstrap_inflight: threading.Event | None = None
        self._bootstrap_error: str | None = None
        self._routing_inflight: threading.Event | None = None
        self._routing_initialized = False
        self._routing_error: str | None = None

    @staticmethod
    def _artifact_path(value: Any) -> str:
        if not isinstance(value, str) or not value:
            raise BackendQueryError("remote artifact path is invalid")
        return value if value.startswith("micro-1024/") else f"micro-1024/{value}"

    def _remote_bootstrap(self) -> tuple[Path, Mapping[str, Any]]:
        if not isinstance(self.artifacts, ManifestReleaseArtifactStore):
            raise BackendQueryError("remote release artifact adapter is invalid")
        with self._routing_state_lock:
            if self._routing_bootstrap is not None:
                return self.artifacts.cache_root / "micro-1024", self._routing_bootstrap
            if self._bootstrap_error is not None:
                raise BackendQueryError(self._bootstrap_error)
            flight = self._bootstrap_inflight
            owner = flight is None
            if owner:
                flight = threading.Event()
                self._bootstrap_inflight = flight
        if not owner:
            flight.wait()
            with self._routing_state_lock:
                if self._routing_bootstrap is not None:
                    return self.artifacts.cache_root / "micro-1024", self._routing_bootstrap
                message = self._bootstrap_error or "remote release bootstrap is unavailable"
            raise BackendQueryError(message)

        assert flight is not None
        try:
            try:
                bootstrap_path = self.artifacts.materialize("micro-1024/bootstrap.json")
                self.artifacts.materialize("micro-1024/integrity.json")
                bootstrap = json.loads(bootstrap_path.read_bytes())
            except (ReleaseArtifactError, OSError, ValueError) as exc:
                raise BackendQueryError("remote release bootstrap is unavailable") from exc
            if not isinstance(bootstrap, Mapping):
                raise BackendQueryError("remote release bootstrap is invalid")
            regions = bootstrap.get("regions")
            if not isinstance(regions, list) or not regions:
                raise BackendQueryError("remote release region metadata is invalid")
            seen: set[str] = set()
            for entry in regions:
                if not isinstance(entry, Mapping) or not isinstance(entry.get("regionId"), str) or entry["regionId"] in seen:
                    raise BackendQueryError("remote release region metadata is invalid")
                seen.add(entry["regionId"])
        except Exception as exc:  # noqa: BLE001 - all remote bootstrap failures fail closed
            message = str(exc) if isinstance(exc, BackendQueryError) else "remote release bootstrap is unavailable"
            with self._routing_state_lock:
                self._bootstrap_error = message
                self._bootstrap_inflight = None
                flight.set()
            if isinstance(exc, BackendQueryError):
                raise
            raise BackendQueryError(message) from exc
        with self._routing_state_lock:
            self._routing_bootstrap = bootstrap
            self._bootstrap_inflight = None
            flight.set()
        return self.artifacts.cache_root / "micro-1024", bootstrap

    @staticmethod
    def _remote_region_entry(bootstrap: Mapping[str, Any], region_id: str) -> Mapping[str, Any]:
        for entry in bootstrap.get("regions", []):
            if isinstance(entry, Mapping) and entry.get("regionId") == region_id:
                return entry
        raise BackendQueryError("remote release region is unavailable")

    def _materialize_remote_contract(self, region_id: str) -> Any:
        cache_root, bootstrap = self._remote_bootstrap()
        source = bootstrap.get("source")
        if not isinstance(source, Mapping):
            raise BackendQueryError("remote release source metadata is invalid")
        entry = self._remote_region_entry(bootstrap, region_id)
        region_manifest_descriptor = entry.get("manifest")
        if not isinstance(region_manifest_descriptor, Mapping):
            raise BackendQueryError("remote region manifest descriptor is invalid")
        try:
            region_manifest_path = self.artifacts.materialize(self._artifact_path(region_manifest_descriptor.get("path")))
            region_manifest = json.loads(region_manifest_path.read_bytes())
        except (ReleaseArtifactError, OSError, ValueError, BackendQueryError) as exc:
            raise BackendQueryError("remote region manifest is unavailable") from exc
        if not isinstance(region_manifest, Mapping):
            raise BackendQueryError("remote region manifest is invalid")
        files = region_manifest.get("files")
        if not isinstance(files, Mapping):
            raise BackendQueryError("remote region file descriptors are invalid")
        descriptors = [files.get("searchIndex"), files.get("storeIndex")]
        packs = files.get("offerPacks")
        if not isinstance(packs, list):
            raise BackendQueryError("remote region pack descriptors are invalid")
        try:
            for descriptor in descriptors:
                if not isinstance(descriptor, Mapping):
                    raise BackendQueryError("remote region artifact descriptor is invalid")
                logical_path = self._artifact_path(descriptor.get("path"))
                self.artifacts.materialize(logical_path, sparse=logical_path.endswith(".bin"))
            expected_outer = source.get("outerSha256")
            expected_bytes = source.get("outerBytes")
            expected_date = source.get("releaseDate")
            expected_accepted = source.get("acceptedObservationsSha256")
            expected_national = source.get("nationalIndexSha256")
            if (
                not isinstance(expected_outer, str)
                or not isinstance(expected_date, str)
                or not isinstance(expected_bytes, int)
                or (expected_accepted is not None and not isinstance(expected_accepted, str))
                or (expected_national is not None and not isinstance(expected_national, str))
            ):
                raise BackendQueryError("remote release source evidence is incomplete")
            return load_micro_region_contract(
                cache_root,
                region_id,
                expected_outer_sha256=expected_outer,
                expected_outer_bytes=expected_bytes,
                expected_release_date=expected_date,
                expected_accepted_sha256=expected_accepted,
                expected_national_index_sha256=expected_national,
                verify_companion_files=False,
                verify_pack_files=False,
            )
        except (ReleaseArtifactError, OSError, ValueError, MicroPartitionQueryError, BackendQueryError) as exc:
            raise BackendQueryError("remote region contract failed verification") from exc

    def _materialize_remote_routing(self, region_id: str) -> dict[str, Mapping[str, Any]]:
        cache_root, bootstrap = self._remote_bootstrap()
        source = bootstrap.get("source")
        if not isinstance(source, Mapping):
            raise BackendQueryError("remote release source metadata is invalid")
        entry = self._remote_region_entry(bootstrap, region_id)
        manifest_descriptor = entry.get("manifest")
        if not isinstance(manifest_descriptor, Mapping):
            raise BackendQueryError("remote region manifest descriptor is invalid")
        try:
            manifest_path = self.artifacts.materialize(self._artifact_path(manifest_descriptor.get("path")))
            manifest = json.loads(manifest_path.read_bytes())
            if not isinstance(manifest, Mapping) or not isinstance(manifest.get("files"), Mapping):
                raise BackendQueryError("remote region file descriptors are invalid")
            store_descriptor = manifest["files"].get("storeIndex")
            if not isinstance(store_descriptor, Mapping):
                raise BackendQueryError("remote region store descriptor is invalid")
            self.artifacts.materialize(self._artifact_path(store_descriptor.get("path")))
            expected_outer = source.get("outerSha256")
            expected_bytes = source.get("outerBytes")
            expected_date = source.get("releaseDate")
            expected_accepted = source.get("acceptedObservationsSha256")
            expected_national = source.get("nationalIndexSha256")
            if (
                not isinstance(expected_outer, str)
                or not isinstance(expected_date, str)
                or not isinstance(expected_bytes, int)
                or (expected_accepted is not None and not isinstance(expected_accepted, str))
                or (expected_national is not None and not isinstance(expected_national, str))
            ):
                raise BackendQueryError("remote release source evidence is incomplete")
            routing = load_micro_region_routing(
                cache_root,
                region_id,
                expected_outer_sha256=expected_outer,
                expected_outer_bytes=expected_bytes,
                expected_release_date=expected_date,
                expected_accepted_sha256=expected_accepted,
                expected_national_index_sha256=expected_national,
                verify_companion_files=False,
            )
            return _load_stores(routing)
        except (ReleaseArtifactError, OSError, ValueError, MicroPartitionQueryError, BackendQueryError) as exc:
            raise BackendQueryError("remote region routing failed verification") from exc

    def _remote_routing_descriptor(self) -> Mapping[str, Any] | None:
        if not isinstance(self.artifacts, ManifestReleaseArtifactStore):
            raise BackendQueryError("remote release artifact adapter is invalid")
        candidate = self.artifacts.manifest.get("routingArtifact")
        if candidate is None:
            return None
        if not isinstance(candidate, Mapping):
            raise BackendQueryError("remote national routing metadata is invalid")
        expected_regions = sorted(spec.region_id for spec in ARGENTINA_REGIONS)
        if candidate.get("schemaVersion") != "valuepilot-national-routing-v1" or candidate.get("path") != "micro-1024/national-routing.jsonl.gz" or candidate.get("regions") != expected_regions:
            raise BackendQueryError("remote national routing metadata is invalid")
        path = candidate.get("path")
        if not self.artifacts.has(path):
            raise BackendQueryError("remote national routing artifact is missing")
        objects = self.artifacts.manifest.get("objects")
        matching = [item for item in objects if isinstance(item, Mapping) and item.get("path") == path] if isinstance(objects, list) else []
        if len(matching) != 1 or any(matching[0].get(name) != candidate.get(name) for name in ("sha256", "bytes")):
            raise BackendQueryError("remote national routing descriptor is not pinned")
        return candidate

    def _ensure_remote_routing(self) -> Mapping[str, Any]:
        """Initialize every region's store directory once, in bounded parallel.

        The staged dictionary is never exposed until every region has passed
        verification.  Concurrent callers wait on the same event rather than
        starting another national fetch.
        """

        if not self.artifacts.remote:
            raise BackendQueryError("remote routing is unavailable for local artifacts")
        with self._routing_state_lock:
            if self._routing_initialized:
                bootstrap = self._routing_bootstrap
                if bootstrap is None:
                    raise BackendQueryError("remote routing state is invalid")
                return bootstrap
            if self._routing_error is not None:
                raise BackendQueryError(self._routing_error)
            flight = self._routing_inflight
            owner = flight is None
            if owner:
                flight = threading.Event()
                self._routing_inflight = flight
        if not owner:
            flight.wait()
            with self._routing_state_lock:
                if self._routing_initialized and self._routing_bootstrap is not None:
                    return self._routing_bootstrap
                message = self._routing_error or "remote routing initialization failed"
            raise BackendQueryError(message)

        assert flight is not None
        try:
            national_descriptor = self._remote_routing_descriptor()
            if national_descriptor is not None:
                # Keep the verified release bootstrap available for selected
                # contract validation and region metadata.  The routing
                # object replaces only the national geography load; it does
                # not replace the release's source/provenance contract with a
                # synthetic bootstrap.
                _, bootstrap = self._remote_bootstrap()
                routing_path = self._artifact_path(national_descriptor["path"])
                self.artifacts.materialize(routing_path)
                staged_points = load_national_routing(self.artifacts.cache_root, national_descriptor, expected_region_ids=frozenset(spec.region_id for spec in ARGENTINA_REGIONS))
                staged = None
            else:
                _, bootstrap = self._remote_bootstrap()
                entries = bootstrap.get("regions")
                if not isinstance(entries, list) or not entries:
                    raise BackendQueryError("remote release region metadata is invalid")
                region_ids: tuple[str, ...] = tuple(sorted(entry["regionId"] for entry in entries if isinstance(entry, Mapping) and isinstance(entry.get("regionId"), str)))
                expected_region_ids = frozenset(spec.region_id for spec in ARGENTINA_REGIONS)
                if len(region_ids) != len(entries) or len(set(region_ids)) != len(region_ids) or frozenset(region_ids) != expected_region_ids:
                    raise BackendQueryError("remote release region metadata is incomplete or invalid")

                # Futures are collected in sorted region order so completion timing
                # cannot change the cache's observable iteration order.
                with ThreadPoolExecutor(max_workers=MAX_REMOTE_ROUTING_CONCURRENCY, thread_name_prefix="valuepilot-routing") as executor:
                    futures = {region_id: executor.submit(self._materialize_remote_routing, region_id) for region_id in region_ids}
                    staged = {region_id: futures[region_id].result() for region_id in region_ids}
                staged_points = None
        except Exception as exc:  # noqa: BLE001 - routing must fail closed
            message = str(exc) if isinstance(exc, BackendQueryError) else "remote routing initialization failed"
            with self._routing_state_lock:
                self._routing_error = message
                self._routing_inflight = None
                flight.set()
            if isinstance(exc, BackendQueryError):
                raise
            raise BackendQueryError(message) from exc

        # Commit only after every worker completed successfully.  This lock is
        # held for a bounded in-memory assignment, never for remote I/O.
        with self._lock:
            if staged_points is not None:
                self._routing_points = staged_points
                self._routing_stores = {}
            else:
                self._routing_stores = staged or {}
                self._routing_points = {}
        with self._routing_state_lock:
            self._routing_bootstrap = bootstrap
            self._routing_mode = "national" if staged_points is not None else "regional"
            self._routing_initialized = True
            self._routing_inflight = None
            flight.set()
        return bootstrap

    def contract(self, region_id: str) -> Any:
        with self._lock:
            contract = self._contracts.get(region_id)
            if contract is None:
                if self.artifacts.remote:
                    contract = self._materialize_remote_contract(region_id)
                else:
                    if self.root is None:
                        raise BackendQueryError("local release root is unavailable")
                    contract = load_micro_region_contract(self.root, region_id)
                self._contracts[region_id] = contract
            return contract

    def stores(self, region_id: str) -> dict[str, Mapping[str, Any]]:
        with self._lock:
            stores = self._stores.get(region_id)
            if stores is None:
                stores = _load_stores(self.contract(region_id))
                self._stores[region_id] = stores
            return stores

    def routing_stores(self, region_id: str) -> dict[str, Mapping[str, Any]]:
        """Load only store geography until a region wins exact routing."""

        if not self.artifacts.remote:
            return self.stores(region_id)
        self._ensure_remote_routing()
        if self._routing_mode == "national":
            raise BackendQueryError("compact national routing does not expose full store metadata")
        with self._lock:
            stores = self._routing_stores.get(region_id)
        if stores is None:
            raise BackendQueryError("remote routing region is unavailable")
        return stores

    def route(self, *, latitude: Decimal, longitude: Decimal, radius_km: Decimal) -> tuple[RegionSelection, ...]:
        if radius_km < 0 or radius_km > MAX_BACKEND_RADIUS_KM:
            raise BackendQueryError("radiusKm must be between 0 and 50 km")
        regions = []
        if self.artifacts.remote:
            bootstrap = self._ensure_remote_routing()
        else:
            bootstrap = self.contract("ar-caba").bootstrap
        entries = bootstrap.get("regions") if isinstance(bootstrap, Mapping) else None
        if not isinstance(entries, list) or not entries:
            raise BackendQueryError("release region metadata is invalid")
        for entry in entries:
            if not isinstance(entry, Mapping) or not isinstance(entry.get("regionId"), str):
                raise BackendQueryError("release region metadata is invalid")
            region_id = entry["regionId"]
            selected: list[str] = []
            if self.artifacts.remote and self._routing_mode == "national":
                points = self._routing_points.get(region_id, ())
                for point in points:
                    distance = straight_line_distance_km(latitude, longitude, point.latitude, point.longitude)
                    if distance <= float(radius_km) + 1e-9:
                        selected.append(point.store_key)
            else:
                stores = self.routing_stores(region_id) if self.artifacts.remote else self.stores(region_id)
                for store_key, store in stores.items():
                    if store.get("geoStatus") != "VALID":
                        continue
                    distance = straight_line_distance_km(latitude, longitude, store["latitude"], store["longitude"])
                    if distance <= float(radius_km) + 1e-9:
                        selected.append(store_key)
            if selected:
                regions.append(RegionSelection(region_id, tuple(sorted(selected))))
        return tuple(sorted(regions, key=lambda item: item.region_id))


class ArgentinaBackendReader:
    """Read exact M5/M6 evidence and delegate decisions to the M6 engine."""

    def __init__(self, release: ReleaseHandle, *, cache: VerifiedEvidenceCache[list[dict[str, Any]]] | None = None):
        self.release = release
        self.root = release.root
        self.artifacts = release.artifacts
        self.router = NationalStoreRouter(self.root, artifacts=self.artifacts)
        self.cache = cache or VerifiedEvidenceCache()
        # Search indexes are immutable for a pinned release. Cache bounded
        # top-k results so warm service calls do not rescan the same gzip.
        self._search_cache: OrderedDict[tuple[str, str, int], tuple[Mapping[str, Any], ...]] = OrderedDict()
        self._input_catalog_cache: OrderedDict[tuple[str, ...], CatalogIndex] = OrderedDict()

    def _search(self, contract: Any, query: str, *, product_limit: int = 5) -> tuple[Mapping[str, Any], ...]:
        cache_key = (contract.region.region_id, query, product_limit)
        value = self._search_cache.get(cache_key)
        if value is None:
            value, _ = _search_candidates(contract, query, product_limit=product_limit, max_candidates=MAX_CANDIDATES)
            value = tuple(value)
            if len(self._search_cache) >= MAX_SEARCH_CACHE_ENTRIES:
                self._search_cache.popitem(last=False)
            self._search_cache[cache_key] = value
        else:
            self._search_cache.move_to_end(cache_key)
        return value

    def _plan(self, contract: Any, queries: Sequence[str], *, product_limit: int = 5) -> MicroQueryPlan:
        candidates: list[Mapping[str, Any]] = []
        seen: set[str] = set()
        partitions: set[str] = set()
        start = time.perf_counter()
        for query in queries:
            values = self._search(contract, query, product_limit=product_limit)
            for value in values:
                key = value["productEvidenceKey"]
                if key not in seen:
                    seen.add(key)
                    candidates.append(value)
                    partitions.add(value["partitionId"])
        selected = tuple(sorted(partitions))
        slices = []
        for partition_id in selected:
            descriptor = contract.partition_descriptors[partition_id]
            pack = contract.pack_descriptors[descriptor["packId"]]
            slices.append({
                "partitionId": partition_id,
                "packId": descriptor["packId"],
                "path": descriptor["path"],
                "byteOffset": descriptor["byteOffset"],
                "byteLength": descriptor["byteLength"],
                "bytes": descriptor["bytes"],
                "sha256": descriptor["sha256"],
                "uncompressedBytes": descriptor["uncompressedBytes"],
                "uncompressedSha256": descriptor["uncompressedSha256"],
                "recordCount": descriptor["recordCount"],
                "packBytes": pack["bytes"],
            })
        return MicroQueryPlan(contract.region.region_id, tuple(queries), tuple(candidates), selected, tuple(slices), contract.bootstrap_bytes, contract.manifest_bytes, contract.search_descriptor["bytes"], contract.store_descriptor["bytes"], sum(int(value["byteLength"]) for value in slices), sum(int(value["uncompressedBytes"]) for value in slices), tuple(sorted({value["packId"] for value in slices})), 4 + len({value["packId"] for value in slices}))

    def input_catalog(self, region_ids: Sequence[str] | None = None) -> CatalogIndex:
        """Build/cache a bounded vocabulary from qualified search indexes.

        Search indexes are already immutable release artifacts.  This reads
        only their product identity records; it never reads the national ZIP
        or offer partitions and never turns an identity into price/stock.
        """

        if region_ids is None:
            bootstrap = self.router.contract("ar-caba").bootstrap
            values = [entry.get("regionId") for entry in bootstrap.get("regions", []) if isinstance(entry, Mapping) and isinstance(entry.get("regionId"), str)]
            selected = tuple(sorted(values))
        else:
            selected = tuple(sorted({value for value in region_ids if isinstance(value, str)}))
        cache_key = ("__all__",) if region_ids is None else selected
        cached = self._input_catalog_cache.get(cache_key)
        if cached is not None:
            self._input_catalog_cache.move_to_end(cache_key)
            return cached
        records: list[Mapping[str, Any]] = []
        seen: set[str] = set()
        for region_id in selected:
            contract = self.router.contract(region_id)
            path = contract.root / contract.search_descriptor["path"]
            for raw in _iter_gzip_records(path, contract.search_descriptor, f"{region_id} input vocabulary"):
                key = raw.get("productEvidenceKey")
                if not isinstance(key, str) or key in seen:
                    continue
                seen.add(key)
                records.append(raw)
                if len(records) >= MAX_INPUT_VOCABULARY_RECORDS:
                    break
            if len(records) >= MAX_INPUT_VOCABULARY_RECORDS:
                break
        try:
            catalog = CatalogIndex.from_records(records, max_records=MAX_INPUT_VOCABULARY_RECORDS)
        except InputIntelligenceError as exc:
            raise BackendQueryError(str(exc)) from exc
        if len(self._input_catalog_cache) >= MAX_INPUT_CATALOG_CACHE_ENTRIES:
            self._input_catalog_cache.popitem(last=False)
        self._input_catalog_cache[cache_key] = catalog
        return catalog

    def interpret_text(self, text: str, *, require_quantities: bool = False, region_ids: Sequence[str] | None = None) -> dict[str, Any]:
        try:
            interpreter = ConsumerInputInterpreter(self.input_catalog(region_ids))
            return interpreter.interpret(text, require_quantities=require_quantities)
        except InputIntelligenceError as exc:
            raise BackendQueryError(str(exc)) from exc

    def _member(self, region_id: str, partition: Mapping[str, Any], metrics: RequestMetrics) -> list[dict[str, Any]]:
        key = f"{self.release.release_id}:{region_id}:{partition['partitionId']}:{partition['sha256']}"
        started = time.perf_counter()

        def load() -> tuple[bytes, list[dict[str, Any]]]:
            metrics.object_reads += 1
            metrics.range_reads += 1
            range_started = time.perf_counter()
            data = self.artifacts.read_range(partition["path"], int(partition["byteOffset"]), int(partition["byteLength"]))
            metrics.range_reads_ms += (time.perf_counter() - range_started) * 1000
            metrics.bytes_read += len(data)
            records = decode_member_bytes(data, partition, f"{region_id}/{partition['partitionId']}")
            return data, records

        value, hit = self.cache.get_or_load(key, load, expected_sha256=partition["sha256"], expected_bytes=int(partition["byteLength"]))
        elapsed = (time.perf_counter() - started) * 1000
        metrics.decode_ms += elapsed
        if hit:
            metrics.cache_hits += 1
        else:
            metrics.cache_misses += 1
        return value

    def query(self, request: ShoppingRequest | Mapping[str, Any]) -> tuple[dict[str, Any], RequestMetrics]:
        started = time.perf_counter()
        try:
            normalized = request if isinstance(request, ShoppingRequest) else ShoppingRequest.from_mapping(request)
        except ShoppingIntelligenceError as exc:
            raise BackendQueryError(str(exc)) from exc
        if normalized.radius_km > MAX_BACKEND_RADIUS_KM:
            raise BackendQueryError("radiusKm must be between 0 and 50 km")
        metrics = RequestMetrics()
        active_start = time.perf_counter()
        # The caller pins the ReleaseHandle before constructing this reader.
        if self.release.freshness_status != "FRESH":
            raise CurrentPriceEvidenceUnavailable(CurrentPriceEvidenceUnavailable.code)
        metrics.active_lookup_ms = (time.perf_counter() - active_start) * 1000

        route_start = time.perf_counter()
        selections = self.router.route(latitude=normalized.latitude, longitude=normalized.longitude, radius_km=normalized.radius_km)
        metrics.routing_ms = (time.perf_counter() - route_start) * 1000
        metrics.regions_queried = [item.region_id for item in selections]
        per_line: dict[str, dict[str, dict[str, Any]]] = {line.line_id: {"candidates": {}, "offers": {}} for line in normalized.lines}
        regional_plans: list[dict[str, Any]] = []
        for selection in selections:
            contract = self.router.contract(selection.region_id)
            search_start = time.perf_counter()
            plan = self._plan(contract, [line.query for line in normalized.lines])
            metrics.regional_search_ms += (time.perf_counter() - search_start) * 1000
            metrics.compressed_bytes += plan.compressed_bytes
            metrics.decompressed_bytes += plan.decompressed_bytes
            metrics.physical_pack_bytes += plan.physical_pack_bytes
            stores = self.router.stores(selection.region_id)
            products = {item["productEvidenceKey"]: item for item in plan.product_candidates}
            records: list[Mapping[str, Any]] = []
            for partition in plan.slices:
                records.extend(self._member(selection.region_id, partition, metrics))
            offers = []
            for raw_offer in records:
                product_key = raw_offer.get("productEvidenceKey")
                store_key = raw_offer.get("storeKey")
                product = products.get(product_key)
                store = stores.get(store_key)
                if product is None or store is None or store_key not in selection.store_keys or store.get("geoStatus") != "VALID":
                    continue
                distance = straight_line_distance_km(normalized.latitude, normalized.longitude, store["latitude"], store["longitude"])
                if distance > float(normalized.radius_km) + 1e-9:
                    continue
                offers.append(_offer_result(raw_offer, product, store, distance))
            by_query: dict[str, set[str]] = {}
            for line in normalized.lines:
                candidates = self._search(contract, line.query, product_limit=5)
                keys = {candidate["productEvidenceKey"] for candidate in candidates}
                by_query[line.line_id] = keys
                for candidate in candidates:
                    per_line[line.line_id]["candidates"].setdefault(candidate["productEvidenceKey"], candidate)
                for offer in offers:
                    if offer["productEvidenceKey"] in keys:
                        per_line[line.line_id]["offers"].setdefault((offer["offerKey"], selection.region_id), offer)
            regional_plans.append({"regionId": selection.region_id, "plan": plan.as_dict(), "storeCount": len(selection.store_keys), "returnedOfferCount": len(offers)})

        provider_items = []
        for line in normalized.lines:
            values = per_line[line.line_id]
            provider_items.append({
                "lineId": line.line_id,
                "query": line.query,
                "amount": format(line.requested_quantity.in_unit(line.requested_quantity.input_unit), "f"),
                "unit": line.requested_quantity.input_unit,
                "productCandidates": [dict(values["candidates"][key]) for key in sorted(values["candidates"])],
                "offers": [values["offers"][key] for key in sorted(values["offers"], key=lambda value: (value[0], value[1]))],
            })
        provider_result = {"items": provider_items, "queryPlan": {"regions": regional_plans, "profile": self.release.profile}}
        engine_start = time.perf_counter()
        try:
            decision = evaluate_argentina_provider_result("national", normalized, provider_result)
        except (ShoppingIntelligenceError, ValueError) as exc:
            raise BackendQueryError(str(exc)) from exc
        metrics.engine_ms = (time.perf_counter() - engine_start) * 1000
        metrics.total_ms = (time.perf_counter() - started) * 1000
        # Kept only inside the provider-edge result so /v1/search can expose
        # bounded exact candidates. The HTTP shop projector removes it.
        decision["providerItems"] = provider_items
        return decision, metrics


__all__ = ["ArgentinaBackendReader", "BackendQueryError", "CurrentPriceEvidenceUnavailable", "MAX_BACKEND_RADIUS_KM", "MAX_INPUT_CATALOG_CACHE_ENTRIES", "MAX_REMOTE_ROUTING_CONCURRENCY", "MAX_SEARCH_CACHE_ENTRIES", "NationalStoreRouter", "RequestMetrics", "RegionSelection"]

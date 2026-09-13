"""Provider-neutral control-plane primitives for automated SEPA releases.

The module is intentionally small.  It coordinates discovery decisions,
single-writer execution, anomaly review, and durable operator diagnostics; it
does not parse SEPA rows or create artifacts.  The existing daily-release
orchestrator and object-store publisher remain the authorities for those
operations.
"""

from __future__ import annotations

import datetime as _dt
import hashlib
import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

try:
    from backend.object_store import ObjectMetadata, ObjectStore, ObjectStoreError
except ModuleNotFoundError:  # direct invocation from tools/
    from object_store import ObjectMetadata, ObjectStore, ObjectStoreError

try:
    from .argentina_release_acquisition import AcquisitionCandidate, SourceDecision, canonical_bytes, classify_source
except ImportError:  # direct invocation from tools/
    from argentina_release_acquisition import AcquisitionCandidate, SourceDecision, canonical_bytes, classify_source


SCHEMA_VERSION = "valuepilot-argentina-automated-release-v1"
POLICY_VERSION = "valuepilot-argentina-automated-release-policy-v1"
LEASE_KEY = "control/automation-lease.json"
EXECUTION_PREFIX = "control/executions"
LEASE_TTL_SECONDS = 15 * 60
MAX_EXECUTION_ID = 128
MAX_LEASE_BYTES = 64 * 1024
MIN_CATASTROPHIC_COUNT_RATIO = 0.50
MAX_QUARANTINE_MULTIPLIER = 4.0
_TOKEN = re.compile(r"^[A-Za-z0-9._-]{1,128}$")
ACTIVE_POINTER_KEY = "control/active.json"
ACTIVE_POINTER_SCHEMA = "valuepilot-active-release-v1"
ACTIVATION_PARTICIPANTS = ("us", "sa", "r2")
RELEASE_MODES = ("SHADOW", "STAGE_CANARY", "AUTO_ACTIVATE")

# The data-canary contract is deliberately small and provider-neutral.  A
# deployment adapter supplies observations from the same backend image that
# would serve production; this module only validates the bounded evidence and
# decides whether it is safe to proceed to immutable staging.  The limits are
# explicit so a future operator can change the policy in a reviewed commit,
# rather than smuggling a wall-clock or network assumption into activation.
DATA_CANARY_SCHEMA_VERSION = "valuepilot-argentina-data-canary-v1"
DATA_CANARY_POLICY_VERSION = "valuepilot-argentina-data-canary-policy-v1"
DATA_CANARY_REGIONS = ("us", "sa")
DATA_CANARY_REQUESTS = ("arrozDiscovery", "arrozInterpretation", "sevenUpSpecificSearch")
DATA_CANARY_SHOPPING_LINE_COUNTS = (1, 5, 10)
DATA_CANARY_LOCATIONS = ("CABA", "Buenos Aires Province", "Córdoba", "Jujuy")
DATA_CANARY_COLD_10_LINE_MAX_MS = 10_000
DATA_CANARY_WARM_SMOKE_MAX_MS = 5_000
DATA_CANARY_MAX_EVIDENCE_BYTES = 128 * 1024


class AutomationError(RuntimeError):
    """A control-plane operation failed closed."""

    def __init__(self, message: str, *, code: str = "AUTOMATION_FAILED") -> None:
        super().__init__(message)
        self.code = code


def _timestamp(value: str, *, field: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise AutomationError(f"{field} is required", code="INVALID_CONTROL_STATE")
    try:
        parsed = _dt.datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError as exc:
        raise AutomationError(f"{field} is invalid", code="INVALID_CONTROL_STATE") from exc
    if parsed.tzinfo is None:
        raise AutomationError(f"{field} must include a timezone", code="INVALID_CONTROL_STATE")
    return parsed.astimezone(_dt.timezone.utc).isoformat().replace("+00:00", "Z")


def _instant(value: str, *, field: str) -> _dt.datetime:
    return _dt.datetime.fromisoformat(_timestamp(value, field=field).replace("Z", "+00:00"))


def _execution_id(value: str) -> str:
    if not isinstance(value, str) or not _TOKEN.fullmatch(value) or len(value) > MAX_EXECUTION_ID:
        raise AutomationError("execution ID is invalid", code="INVALID_CONTROL_STATE")
    return value


def _canonical_hash(value: Any) -> str:
    return hashlib.sha256(canonical_bytes(value)).hexdigest()


def _read_json(store: ObjectStore, key: str, *, maximum: int = MAX_LEASE_BYTES) -> tuple[ObjectMetadata, dict[str, Any]] | None:
    try:
        present = store.exists(key)
    except ObjectStoreError as exc:
        raise AutomationError("control object availability is unknown", code="CONTROL_STORE_UNAVAILABLE") from exc
    if not present:
        return None
    try:
        metadata = store.head(key)
        if metadata.size > maximum:
            raise AutomationError("control object exceeds bound", code="INVALID_CONTROL_STATE")
        raw = store.get(key)
        value = json.loads(raw)
    except ObjectStoreError as exc:
        raise AutomationError("control object availability is unknown", code="CONTROL_STORE_UNAVAILABLE") from exc
    except (UnicodeDecodeError, ValueError) as exc:
        raise AutomationError("control object is invalid", code="INVALID_CONTROL_STATE") from exc
    if not isinstance(value, dict):
        raise AutomationError("control object is invalid", code="INVALID_CONTROL_STATE")
    return metadata, value


@dataclass(frozen=True)
class LeaseHandle:
    execution_id: str
    candidate_identity: Mapping[str, Any]
    acquired_at: str
    expires_at: str
    generation: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "executionId": self.execution_id,
            "candidate": dict(self.candidate_identity),
            "acquiredAt": self.acquired_at,
            "expiresAt": self.expires_at,
            "generation": self.generation,
        }


@dataclass(frozen=True)
class LeaseOutcome:
    status: str
    handle: LeaseHandle | None
    current: Mapping[str, Any] | None = None

    def as_dict(self) -> dict[str, Any]:
        return {"status": self.status, "lease": self.handle.as_dict() if self.handle else None, "current": dict(self.current) if self.current else None}


class DistributedReleaseLease:
    """GCS-generation-backed lease; the same contract works in local tests."""

    def __init__(self, store: ObjectStore, *, key: str = LEASE_KEY, ttl_seconds: int = LEASE_TTL_SECONDS):
        if key != LEASE_KEY:
            raise AutomationError("lease key is not trusted", code="INVALID_CONTROL_STATE")
        if not isinstance(ttl_seconds, int) or not 60 <= ttl_seconds <= 24 * 60 * 60:
            raise AutomationError("lease TTL is outside its bound", code="INVALID_CONTROL_STATE")
        self.store = store
        self.key = key
        self.ttl_seconds = ttl_seconds

    @staticmethod
    def _live(value: Mapping[str, Any], now: _dt.datetime) -> bool:
        if value.get("status", "ACTIVE") != "ACTIVE":
            return False
        expires = value.get("expiresAt")
        if not isinstance(expires, str):
            raise AutomationError("lease expiry is invalid", code="INVALID_CONTROL_STATE")
        return _instant(expires, field="expiresAt") > now

    def acquire(self, *, execution_id: str, candidate_identity: Mapping[str, Any], now: str) -> LeaseOutcome:
        execution = _execution_id(execution_id)
        if not isinstance(candidate_identity, Mapping) or not candidate_identity:
            raise AutomationError("candidate identity is invalid", code="INVALID_CONTROL_STATE")
        acquired = _timestamp(now, field="now")
        instant = _instant(acquired, field="now")
        expires = (instant + _dt.timedelta(seconds=self.ttl_seconds)).isoformat().replace("+00:00", "Z")
        payload = {
            "schemaVersion": SCHEMA_VERSION,
            "policyVersion": POLICY_VERSION,
            "status": "ACTIVE",
            "executionId": execution,
            "candidate": dict(candidate_identity),
            "acquiredAt": acquired,
            "expiresAt": expires,
        }
        raw = canonical_bytes(payload)
        existing = _read_json(self.store, self.key)
        if existing is None:
            try:
                metadata = self.store.put_immutable(self.key, raw, sha256=hashlib.sha256(raw).hexdigest())
            except ObjectStoreError:
                # Another worker may have won the create-only race.  Read it
                # and return ALREADY_RUNNING rather than overwriting it.
                winner = _read_json(self.store, self.key)
                if winner is None:
                    raise AutomationError("lease create race cannot be resolved", code="LEASE_CONFLICT")
                winner_metadata, winner_value = winner
                if self._live(winner_value, instant):
                    return LeaseOutcome("ALREADY_RUNNING", None, winner_value)
                raise AutomationError("expired lease race cannot be resolved", code="LEASE_CONFLICT")
            return LeaseOutcome("ACQUIRED", LeaseHandle(execution, dict(candidate_identity), acquired, expires, metadata.etag))
        metadata, value = existing
        if self._live(value, instant):
            return LeaseOutcome("ALREADY_RUNNING", None, value)
        try:
            replaced = self.store.compare_and_swap(self.key, raw, expected_etag=metadata.etag)
        except ObjectStoreError as exc:
            winner = _read_json(self.store, self.key)
            if winner is not None and self._live(winner[1], instant):
                return LeaseOutcome("ALREADY_RUNNING", None, winner[1])
            raise AutomationError("expired lease replacement lost its generation race", code="LEASE_CONFLICT") from exc
        return LeaseOutcome("ACQUIRED", LeaseHandle(execution, dict(candidate_identity), acquired, expires, replaced.etag))

    def release(self, handle: LeaseHandle, *, released_at: str) -> str:
        released = _timestamp(released_at, field="releasedAt")
        current = _read_json(self.store, self.key)
        if current is None:
            return "ALREADY_RELEASED"
        metadata, value = current
        if value.get("executionId") != handle.execution_id or value.get("acquiredAt") != handle.acquired_at:
            raise AutomationError("lease is owned by another execution", code="LEASE_CONFLICT")
        payload = dict(value)
        payload.update({"status": "RELEASED", "releasedAt": released, "releaseGeneration": handle.generation})
        try:
            self.store.compare_and_swap(self.key, canonical_bytes(payload), expected_etag=metadata.etag)
        except ObjectStoreError as exc:
            raise AutomationError("lease release generation race", code="LEASE_CONFLICT") from exc
        return "RELEASED"


def write_execution_summary(store: ObjectStore, execution_id: str, summary: Mapping[str, Any]) -> str:
    """Persist one immutable, compact operator summary; retries are idempotent."""

    execution = _execution_id(execution_id)
    if not isinstance(summary, Mapping):
        raise AutomationError("execution summary is invalid", code="INVALID_CONTROL_STATE")
    payload = dict(summary)
    if any(key in payload for key in ("schemaVersion", "policyVersion", "executionId")):
        raise AutomationError("execution summary contains reserved fields", code="INVALID_CONTROL_STATE")
    value = {"schemaVersion": SCHEMA_VERSION, "policyVersion": POLICY_VERSION, "executionId": execution, **payload}
    raw = canonical_bytes(value) + b"\n"
    if len(raw) > MAX_LEASE_BYTES:
        raise AutomationError("execution summary exceeds bound", code="INVALID_CONTROL_STATE")
    key = f"{EXECUTION_PREFIX}/{execution}.json"
    try:
        store.put_immutable(key, raw, sha256=hashlib.sha256(raw).hexdigest())
    except ObjectStoreError as exc:
        raise AutomationError("execution summary conflicts with an existing run", code="EXECUTION_CONFLICT") from exc
    return key


def redact_secrets(value: Any) -> Any:
    """Remove credential-like fields before structured logs are emitted."""

    secret_words = ("token", "secret", "password", "credential", "privatekey", "accesskey", "authorization")
    if isinstance(value, Mapping):
        result: dict[str, Any] = {}
        for key, item in value.items():
            lowered = str(key).casefold().replace("_", "")
            result[str(key)] = "[REDACTED]" if any(word in lowered for word in secret_words) else redact_secrets(item)
        return result
    if isinstance(value, list):
        return [redact_secrets(item) for item in value]
    return value


def discovery_decision(
    *,
    candidate: AcquisitionCandidate | None,
    source_release_date: str | None,
    source_sha256: str | None,
    current_release_date: str | None,
    current_source_sha256: str | None,
    upstream_changed: bool = False,
) -> SourceDecision | None:
    """Return a cheap metadata/no-op decision before Cloud Build starts."""

    if candidate is None:
        return None
    if not isinstance(source_release_date, str) or not isinstance(source_sha256, str):
        # Metadata alone cannot prove the internal release date or hash.  The
        # worker must acquire bytes before making a release decision.
        return None
    return classify_source(
        release_date=source_release_date,
        source_sha256=source_sha256,
        current_release_date=current_release_date,
        current_source_sha256=current_source_sha256,
        upstream_changed=upstream_changed,
    )


def anomaly_gate(*, current: Mapping[str, Any], candidate: Mapping[str, Any]) -> dict[str, Any]:
    """Apply broad catastrophic-regression checks without learning silently."""

    reasons: list[str] = []
    required = ("requiredArtifactSetComplete", "manifestVerified", "searchPackVerified")
    for field in required:
        if candidate.get(field) is not True:
            reasons.append(field.upper() + "_FAILED")
    if candidate.get("schemaDrift") not in (None, "NO_DECLARED_DRIFT"):
        reasons.append("SOURCE_SCHEMA_REVIEW_REQUIRED")
    old_regions = current.get("publishableRegions")
    new_regions = candidate.get("publishableRegions")
    if isinstance(old_regions, int) and isinstance(new_regions, int) and new_regions < old_regions:
        reasons.append("REGIONAL_COVERAGE_COLLAPSE")
    count_fields = ("stores", "productEvidenceRecords", "offers", "promotions")
    for field in count_fields:
        old = current.get(field)
        new = candidate.get(field)
        if isinstance(old, (int, float)) and isinstance(new, (int, float)) and old > 0 and new < old * MIN_CATASTROPHIC_COUNT_RATIO:
            reasons.append(f"{field.upper()}_CATASTROPHIC_DROP")
    old_quarantine = current.get("quarantineRows")
    new_quarantine = candidate.get("quarantineRows")
    if isinstance(old_quarantine, (int, float)) and isinstance(new_quarantine, (int, float)) and old_quarantine > 0 and new_quarantine > old_quarantine * MAX_QUARANTINE_MULTIPLIER:
        reasons.append("QUARANTINE_SPIKE_REVIEW_REQUIRED")
    if candidate.get("availability") not in (None, "UNKNOWN"):
        reasons.append("AVAILABILITY_SEMANTICS_CHANGED")
    return {
        "status": "REVIEW_REQUIRED" if reasons else "PASS",
        "policyVersion": POLICY_VERSION,
        "reasons": sorted(set(reasons)),
        "bounds": {"minimumCountRatio": MIN_CATASTROPHIC_COUNT_RATIO, "maximumQuarantineMultiplier": MAX_QUARANTINE_MULTIPLIER},
    }


def _canary_nonnegative_int(value: Any, *, label: str, reasons: list[str]) -> int | None:
    """Validate a bounded integer counter without coercing unknown evidence."""

    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        reasons.append(f"{label.upper()}_INVALID")
        return None
    return value


def _canary_timing(value: Any, *, label: str, maximum: int, reasons: list[str]) -> float | None:
    """Validate a finite non-negative millisecond measurement."""

    if isinstance(value, bool) or not isinstance(value, (int, float)):
        reasons.append(f"{label.upper()}_INVALID")
        return None
    number = float(value)
    if not math.isfinite(number) or number < 0 or number > maximum:
        reasons.append(f"{label.upper()}_OUT_OF_BOUNDS")
        return None
    return number


def _validate_canary_probe(
    probe: Any,
    *,
    label: str,
    candidate_release_id: str,
    reasons: list[str],
) -> None:
    """Check one HTTP/data observation used by the release canary.

    The caller must provide these fields even when an endpoint itself does not
    expose every value.  The deployment-side probe is responsible for joining
    the endpoint response with its immutable release/telemetry evidence; a
    missing field therefore remains a fail-closed review condition.
    """

    if not isinstance(probe, Mapping):
        reasons.append(f"{label.upper()}_INVALID")
        return
    if probe.get("httpStatus") != 200:
        reasons.append(f"{label.upper()}_HTTP_FAILED")
    if probe.get("releaseId") != candidate_release_id:
        reasons.append(f"{label.upper()}_RELEASE_MISMATCH")
    if probe.get("freshness") != "FRESH":
        reasons.append(f"{label.upper()}_NOT_FRESH")
    if probe.get("availability") != "UNKNOWN":
        reasons.append(f"{label.upper()}_AVAILABILITY_CHANGED")
    corpus = _canary_nonnegative_int(probe.get("corpusScanned"), label=f"{label}_corpus_scanned", reasons=reasons)
    if corpus is not None and corpus != 0:
        reasons.append(f"{label.upper()}_CORPUS_SCAN")
    partition = probe.get("partitionIdentity")
    if not isinstance(partition, str) or not partition or len(partition) > 256:
        reasons.append(f"{label.upper()}_PARTITION_IDENTITY_INVALID")
    if probe.get("partitionIdentityVerified") is not True:
        reasons.append(f"{label.upper()}_PARTITION_IDENTITY_UNVERIFIED")
    oom = probe.get("oom")
    if oom is not False:
        reasons.append(f"{label.upper()}_OOM_DETECTED")
    restarts = _canary_nonnegative_int(probe.get("restartCount"), label=f"{label}_restart_count", reasons=reasons)
    if restarts is not None and restarts != 0:
        reasons.append(f"{label.upper()}_RESTART_DETECTED")
    r2_reads = _canary_nonnegative_int(probe.get("runtimeR2Reads"), label=f"{label}_runtime_r2_reads", reasons=reasons)
    if r2_reads is not None and r2_reads != 0:
        reasons.append(f"{label.upper()}_RUNTIME_R2_READ")
    r2_writes = _canary_nonnegative_int(probe.get("runtimeR2Writes"), label=f"{label}_runtime_r2_writes", reasons=reasons)
    if r2_writes is not None and r2_writes != 0:
        reasons.append(f"{label.upper()}_RUNTIME_R2_WRITE")
    malformed = _canary_nonnegative_int(probe.get("malformedPrices"), label=f"{label}_malformed_prices", reasons=reasons)
    if malformed is not None and malformed != 0:
        reasons.append(f"{label.upper()}_MALFORMED_PRICE")


def evaluate_data_canary(*, candidate_release_id: str, evidence: Mapping[str, Any]) -> dict[str, Any]:
    """Validate the bounded zero-traffic data-canary evidence contract.

    This function performs no network or object-store operation.  It is the
    single gate shared by shadow orchestration and later activation callers.
    The evidence is intentionally summary-only: request bodies, credentials,
    and product corpora are not accepted or emitted here.
    """

    candidate = TransactionalActivationCoordinator._release(candidate_release_id, field="candidateReleaseId")
    if not isinstance(evidence, Mapping):
        raise AutomationError("data-canary evidence is invalid", code="INVALID_CONTROL_STATE")
    try:
        evidence_bytes = len(canonical_bytes(dict(evidence)))
    except (TypeError, ValueError, OverflowError) as exc:
        raise AutomationError("data-canary evidence is not serializable", code="INVALID_CONTROL_STATE") from exc
    if evidence_bytes > DATA_CANARY_MAX_EVIDENCE_BYTES:
        raise AutomationError("data-canary evidence exceeds bound", code="INVALID_CONTROL_STATE")

    reasons: list[str] = []
    if evidence.get("schemaVersion") != DATA_CANARY_SCHEMA_VERSION:
        reasons.append("SCHEMA_VERSION_UNSUPPORTED")
    if evidence.get("policyVersion") != DATA_CANARY_POLICY_VERSION:
        reasons.append("POLICY_VERSION_UNSUPPORTED")
    if evidence.get("candidateReleaseId") != candidate:
        reasons.append("CANDIDATE_RELEASE_MISMATCH")
    regions = evidence.get("regions")
    if not isinstance(regions, Mapping) or set(regions) != set(DATA_CANARY_REGIONS):
        reasons.append("REGION_MATRIX_INCOMPLETE")
        regions = {}

    region_reports: dict[str, Any] = {}
    for region in DATA_CANARY_REGIONS:
        value = regions.get(region)
        region_reasons: list[str] = []
        if not isinstance(value, Mapping):
            region_reasons.append("REGION_EVIDENCE_INVALID")
            value = {}
        for endpoint in ("readyz", "status"):
            _validate_canary_probe(value.get(endpoint), label=f"{region}_{endpoint}", candidate_release_id=candidate, reasons=region_reasons)

        requests = value.get("requests")
        if not isinstance(requests, Mapping) or set(requests) != set(DATA_CANARY_REQUESTS):
            region_reasons.append("REQUEST_MATRIX_INCOMPLETE")
            requests = {}
        for request in DATA_CANARY_REQUESTS:
            _validate_canary_probe(requests.get(request), label=f"{region}_{request}", candidate_release_id=candidate, reasons=region_reasons)

        shopping = value.get("shopping")
        expected_shopping = {str(count) for count in DATA_CANARY_SHOPPING_LINE_COUNTS}
        if not isinstance(shopping, Mapping) or set(shopping) != expected_shopping:
            region_reasons.append("SHOPPING_MATRIX_INCOMPLETE")
            shopping = {}
        for count in DATA_CANARY_SHOPPING_LINE_COUNTS:
            _validate_canary_probe(shopping.get(str(count)), label=f"{region}_shopping_{count}", candidate_release_id=candidate, reasons=region_reasons)

        locations = value.get("locations")
        if not isinstance(locations, Mapping) or set(locations) != set(DATA_CANARY_LOCATIONS):
            region_reasons.append("LOCATION_MATRIX_INCOMPLETE")
            locations = {}
        location_tokens = {
            "CABA": "CABA",
            "Buenos Aires Province": "BUENOS_AIRES_PROVINCE",
            "Córdoba": "CORDOBA",
            "Jujuy": "JUJUY",
        }
        for location in DATA_CANARY_LOCATIONS:
            _validate_canary_probe(locations.get(location), label=f"{region}_location_{location_tokens[location]}", candidate_release_id=candidate, reasons=region_reasons)

        timings = value.get("timings")
        if not isinstance(timings, Mapping):
            region_reasons.append("TIMINGS_INVALID")
            timings = {}
        cold_line_count = timings.get("cold10LineCount")
        if cold_line_count != 10:
            region_reasons.append("COLD_10_LINE_COUNT_INVALID")
        cold_ms = _canary_timing(timings.get("cold10LineMs"), label=f"{region}_cold_10_line_ms", maximum=DATA_CANARY_COLD_10_LINE_MAX_MS, reasons=region_reasons)
        warm_ms = _canary_timing(timings.get("warmSmokeP95Ms"), label=f"{region}_warm_smoke_p95_ms", maximum=DATA_CANARY_WARM_SMOKE_MAX_MS, reasons=region_reasons)
        warm_samples = _canary_nonnegative_int(timings.get("warmSmokeSamples"), label=f"{region}_warm_smoke_samples", reasons=region_reasons)
        if warm_samples is not None and not 1 <= warm_samples <= 100:
            region_reasons.append("WARM_SMOKE_SAMPLES_OUT_OF_BOUNDS")

        unique_reasons = sorted(set(region_reasons))
        region_reports[region] = {
            "status": "PASS" if not unique_reasons else "REVIEW_REQUIRED",
            "reasons": unique_reasons,
            "endpointCount": 2,
            "requestCount": len(DATA_CANARY_REQUESTS),
            "shoppingLineCounts": list(DATA_CANARY_SHOPPING_LINE_COUNTS),
            "locationCount": len(DATA_CANARY_LOCATIONS),
            "cold10LineMs": cold_ms,
            "warmSmokeP95Ms": warm_ms,
            "warmSmokeSamples": warm_samples,
        }
        region_prefix = f"{region.upper()}_"
        reasons.extend(reason if reason.startswith(region_prefix) else f"{region_prefix}{reason}" for reason in unique_reasons)

    unique_reasons = sorted(set(reasons))
    return {
        "schemaVersion": DATA_CANARY_SCHEMA_VERSION,
        "policyVersion": DATA_CANARY_POLICY_VERSION,
        "status": "PASS" if not unique_reasons else "REVIEW_REQUIRED",
        "candidateReleaseId": candidate,
        "regions": region_reports,
        "reasons": unique_reasons,
        "bounds": {
            "cold10LineMaxMs": DATA_CANARY_COLD_10_LINE_MAX_MS,
            "warmSmokeP95MaxMs": DATA_CANARY_WARM_SMOKE_MAX_MS,
            "maxEvidenceBytes": DATA_CANARY_MAX_EVIDENCE_BYTES,
        },
    }


def stage_verify_and_canary(
    workspace: str | Path,
    stores: Mapping[str, ObjectStore],
    *,
    release_ids: Sequence[str],
    candidate_release_id: str,
    data_canary: Mapping[str, Any] | None = None,
    require_data_canary: bool = True,
) -> dict[str, Any]:
    """Stage, verify, and select a candidate on fixed canary pointers.

    The existing :class:`ObjectStoreReleasePublisher` remains the authority
    for manifest/object validation and immutable publication.  This helper
    only provides the bounded three-destination control-plane sequence; it
    never writes an ACTIVE pointer.  A validated data-canary report is required
    by default before any destination write; production activation is a
    separate call to :class:`TransactionalActivationCoordinator`.
    """

    if not isinstance(workspace, (str, Path)) or not str(workspace):
        raise AutomationError("release workspace is invalid", code="INVALID_CONTROL_STATE")
    if not isinstance(stores, Mapping) or set(stores) != set(ACTIVATION_PARTICIPANTS):
        raise AutomationError("staging requires exactly us, sa, and r2 stores", code="INVALID_CONTROL_STATE")
    if isinstance(release_ids, (str, bytes)) or not isinstance(release_ids, Sequence) or not release_ids:
        raise AutomationError("release set is invalid", code="INVALID_CONTROL_STATE")
    if any(not isinstance(value, str) for value in release_ids) or len(set(release_ids)) != len(release_ids):
        raise AutomationError("release set is invalid", code="INVALID_CONTROL_STATE")
    candidate = TransactionalActivationCoordinator._release(candidate_release_id, field="candidateReleaseId")
    if candidate not in release_ids:
        raise AutomationError("candidate release is not in publication set", code="INVALID_CONTROL_STATE")
    if not isinstance(require_data_canary, bool):
        raise AutomationError("data-canary requirement is invalid", code="INVALID_CONTROL_STATE")
    if data_canary is None:
        if require_data_canary:
            raise AutomationError("required data-canary evidence is missing", code="CANARY_FAILED")
        canary_evidence = {
            "schemaVersion": DATA_CANARY_SCHEMA_VERSION,
            "policyVersion": DATA_CANARY_POLICY_VERSION,
            "status": "NOT_EXECUTED",
            "candidateReleaseId": candidate,
            "reasons": ["DATA_CANARY_NOT_EXECUTED"],
        }
    else:
        canary_evidence = evaluate_data_canary(candidate_release_id=candidate, evidence=data_canary)
        if canary_evidence["status"] != "PASS":
            raise AutomationError("data-canary qualification failed", code="CANARY_FAILED")
    try:
        from tools.argentina_object_store_publisher import ObjectStoreReleasePublisher
    except ModuleNotFoundError:  # direct invocation from tools/
        from argentina_object_store_publisher import ObjectStoreReleasePublisher
    publisher = ObjectStoreReleasePublisher()
    try:
        staged = publisher.stage_to_stores(workspace, stores, release_ids=tuple(release_ids), active_release_id=candidate)
        verified = {
            name: [ObjectStoreReleasePublisher(store).verify_on_store(store, release_id) for release_id in sorted(set(release_ids))]
            for name, store in sorted(stores.items())
        }
        canary = {
            name: ObjectStoreReleasePublisher(store).activate_canary(candidate)
            for name, store in sorted(stores.items())
        }
    except Exception as exc:  # noqa: BLE001 - publication failure stops promotion
        raise AutomationError("staging or canary qualification failed", code="CANARY_FAILED") from exc
    return {
        "status": "CANARY_READY",
        "candidateReleaseId": candidate,
        "staging": staged,
        "verification": verified,
        "canary": canary,
        "dataCanary": canary_evidence,
        "activePointerWrites": 0,
    }


def run_release_promotion(
    workspace: str | Path,
    *,
    release_ids: Sequence[str],
    candidate_release_id: str,
    mode: str = "SHADOW",
    stores: Mapping[str, ObjectStore] | None = None,
    data_canary: Mapping[str, Any] | None = None,
    expected_current_release_id: str | None = None,
    smoke_checks: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    """Run the bounded stage/canary/activation control sequence.

    ``SHADOW`` is write-free and is the default worker mode.  ``STAGE_CANARY``
    stages immutable objects and selects only the fixed canary pointers.  The
    explicitly opt-in ``AUTO_ACTIVATE`` mode additionally requires a matching
    pre-activation release and one callable production smoke check for every
    US, South America, and R2 participant.  The coordinator then performs the
    existing CAS activation/compensation sequence; this function adds no data
    parsing or pointer authority.
    """

    if mode not in RELEASE_MODES:
        raise AutomationError("release mode is invalid", code="INVALID_CONTROL_STATE")
    candidate = TransactionalActivationCoordinator._release(candidate_release_id, field="candidateReleaseId")
    if isinstance(release_ids, (str, bytes, bytearray)) or not isinstance(release_ids, Sequence) or not release_ids or any(not isinstance(value, str) for value in release_ids) or len(set(release_ids)) != len(release_ids) or candidate not in release_ids:
        raise AutomationError("release set is invalid", code="INVALID_CONTROL_STATE")
    if mode == "SHADOW":
        return {
            "status": "SHADOW_ONLY",
            "promotionMode": mode,
            "candidateReleaseId": candidate,
            "releaseIds": list(release_ids),
            "activePointerWrites": 0,
            "canaryPointerWrites": 0,
            "activation": "NOT_EXECUTED",
        }

    if not isinstance(stores, Mapping) or set(stores) != set(ACTIVATION_PARTICIPANTS) or any(stores.get(name) is None for name in ACTIVATION_PARTICIPANTS):
        raise AutomationError("promotion requires exactly us, sa, and r2 stores", code="INVALID_CONTROL_STATE")

    # Validate activation-only inputs before staging so a misconfigured
    # automatic run cannot leave even a canary pointer behind.
    expected: str | None = None
    if mode == "AUTO_ACTIVATE":
        expected = TransactionalActivationCoordinator._release(expected_current_release_id, field="expectedCurrentReleaseId")
        if not isinstance(smoke_checks, Mapping) or set(smoke_checks) != set(ACTIVATION_PARTICIPANTS) or any(not callable(smoke_checks.get(name)) for name in ACTIVATION_PARTICIPANTS):
            raise AutomationError("automatic activation requires all production smoke checks", code="ACTIVATION_REVIEW_REQUIRED")

    staged = stage_verify_and_canary(
        workspace,
        stores,
        release_ids=tuple(release_ids),
        candidate_release_id=candidate,
        data_canary=data_canary,
        require_data_canary=True,
    )
    if mode == "STAGE_CANARY":
        return {
            "status": "CANARY_READY",
            "promotionMode": mode,
            "candidateReleaseId": candidate,
            "releaseIds": list(release_ids),
            "staging": staged,
            "activation": "NOT_EXECUTED",
            "activePointerWrites": 0,
            "canaryPointerWrites": sum(1 for value in staged.get("canary", {}).values() if isinstance(value, Mapping) and value.get("canaryPointerChanged")),
        }

    activation = TransactionalActivationCoordinator(stores, smoke_checks=smoke_checks).activate(candidate_release_id=candidate, expected_current_release_id=expected)
    return {
        "status": activation.get("status", "STOPPED"),
        "promotionMode": mode,
        "candidateReleaseId": candidate,
        "releaseIds": list(release_ids),
        "staging": staged,
        "activation": activation,
        "activePointerWrites": activation.get("pointerWrites", 0),
        "canaryPointerWrites": sum(1 for value in staged.get("canary", {}).values() if isinstance(value, Mapping) and value.get("canaryPointerChanged")),
    }


@dataclass(frozen=True)
class PointerSnapshot:
    """The exact pointer bytes and generation observed before activation."""

    participant: str
    release_id: str
    etag: str
    raw: bytes
    value: Mapping[str, Any]

    def as_dict(self) -> dict[str, Any]:
        return {
            "participant": self.participant,
            "releaseId": self.release_id,
            "etag": self.etag,
            "pointerSha256": hashlib.sha256(self.raw).hexdigest(),
        }


class TransactionalActivationCoordinator:
    """CAS-only US → SA → durable activation with compensation.

    This is deliberately a control-plane adapter.  It accepts already-staged
    object stores and optional smoke callbacks; it never parses SEPA data or
    invents a pointer.  A failed later step restores every pointer that may
    have changed, using the generation observed immediately before the
    compensating write.  Any disagreement is returned as a review stop rather
    than being overwritten.
    """

    def __init__(
        self,
        stores: Mapping[str, ObjectStore],
        *,
        smoke_checks: Mapping[str, Any] | None = None,
        pointer_key: str = ACTIVE_POINTER_KEY,
    ) -> None:
        if pointer_key != ACTIVE_POINTER_KEY:
            raise AutomationError("activation pointer key is not trusted", code="INVALID_CONTROL_STATE")
        if not isinstance(stores, Mapping) or set(stores) != set(ACTIVATION_PARTICIPANTS):
            raise AutomationError("activation requires exactly us, sa, and r2 stores", code="INVALID_CONTROL_STATE")
        if any(stores[name] is None for name in ACTIVATION_PARTICIPANTS):
            raise AutomationError("activation store is invalid", code="INVALID_CONTROL_STATE")
        if smoke_checks is not None and (not isinstance(smoke_checks, Mapping) or any(name not in ACTIVATION_PARTICIPANTS for name in smoke_checks)):
            raise AutomationError("activation smoke checks are invalid", code="INVALID_CONTROL_STATE")
        self.stores = {name: stores[name] for name in ACTIVATION_PARTICIPANTS}
        self.smoke_checks = dict(smoke_checks or {})
        self.pointer_key = pointer_key

    @staticmethod
    def _release(value: Any, *, field: str) -> str:
        if not isinstance(value, str) or _TOKEN.fullmatch(value) is None:
            raise AutomationError(f"{field} is invalid", code="INVALID_CONTROL_STATE")
        return value

    def _read_pointer(self, participant: str) -> PointerSnapshot:
        store = self.stores[participant]
        try:
            metadata = store.head(self.pointer_key)
            if metadata.size <= 0 or metadata.size > MAX_LEASE_BYTES:
                raise AutomationError("activation pointer exceeds bound", code="ACTIVATION_REVIEW_REQUIRED")
            raw = store.get(self.pointer_key)
        except ObjectStoreError as exc:
            raise AutomationError(f"{participant} active pointer is unavailable", code="ACTIVATION_REVIEW_REQUIRED") from exc
        try:
            value = json.loads(raw)
        except (UnicodeDecodeError, ValueError) as exc:
            raise AutomationError(f"{participant} active pointer is invalid", code="ACTIVATION_REVIEW_REQUIRED") from exc
        if not isinstance(value, dict):
            raise AutomationError(f"{participant} active pointer is invalid", code="ACTIVATION_REVIEW_REQUIRED")
        release_id = self._release(value.get("releaseId"), field=f"{participant} releaseId")
        token = metadata.etag or metadata.sha256
        if not isinstance(token, str) or not token:
            raise AutomationError(f"{participant} pointer generation is unavailable", code="ACTIVATION_REVIEW_REQUIRED")
        # A pointer written by an older compatible publisher may not have the
        # schema field; if it has one, it must be the trusted schema.
        schema = value.get("schemaVersion")
        if schema is not None and schema != ACTIVE_POINTER_SCHEMA:
            raise AutomationError(f"{participant} active pointer schema is unknown", code="ACTIVATION_REVIEW_REQUIRED")
        return PointerSnapshot(participant, release_id, token, raw, value)

    @staticmethod
    def _candidate_pointer(before: PointerSnapshot, release_id: str) -> bytes:
        value = dict(before.value)
        value.update(
            {
                "schemaVersion": ACTIVE_POINTER_SCHEMA,
                "lastKnownGoodReleaseId": release_id,
                "previousReleaseId": before.release_id,
                "releaseId": release_id,
            }
        )
        return canonical_bytes(value)

    def _activate_one(self, before: PointerSnapshot, release_id: str) -> PointerSnapshot:
        if before.release_id == release_id:
            return before
        raw = self._candidate_pointer(before, release_id)
        try:
            self.stores[before.participant].compare_and_swap(self.pointer_key, raw, expected_etag=before.etag)
        except ObjectStoreError as exc:
            # A CAS can time out after the remote write.  Read the pointer and
            # report the actual state; compensation can then decide safely.
            try:
                after = self._read_pointer(before.participant)
            except AutomationError:
                after = None
            if after is not None and after.release_id == release_id:
                return after
            raise AutomationError(f"{before.participant} activation CAS failed", code="ACTIVATION_FAILED") from exc
        after = self._read_pointer(before.participant)
        if after.release_id != release_id:
            raise AutomationError(f"{before.participant} activation verification failed", code="ACTIVATION_FAILED")
        return after

    def _smoke(self, participant: str, release_id: str) -> Any:
        check = self.smoke_checks.get(participant)
        if check is None:
            return {"status": "NOT_CONFIGURED", "participant": participant}
        try:
            result = check(release_id)
        except Exception as exc:  # noqa: BLE001 - smoke failures stop activation
            return {"status": "FAILED", "participant": participant, "error": type(exc).__name__}
        if result is False:
            return {"status": "FAILED", "participant": participant}
        if isinstance(result, Mapping):
            status = result.get("status")
            if status is not None and str(status).upper() in {"FAIL", "FAILED", "REVIEW_REQUIRED", "NOT_READY"}:
                return {**dict(result), "status": "FAILED", "participant": participant}
            return {**dict(result), "status": str(status or "PASS"), "participant": participant}
        return {"status": "PASS", "participant": participant, "result": result}

    def _restore_candidates(
        self,
        before: Mapping[str, PointerSnapshot],
        *,
        release_id: str,
        participants: Sequence[str],
    ) -> dict[str, Any]:
        results: dict[str, Any] = {}
        for participant in reversed(tuple(participants)):
            original = before[participant]
            try:
                current = self._read_pointer(participant)
            except AutomationError as exc:
                results[participant] = {"status": "ROLLBACK_REVIEW_REQUIRED", "error": exc.code}
                continue
            if current.raw == original.raw and current.release_id == original.release_id:
                results[participant] = {"status": "ALREADY_RESTORED", "releaseId": original.release_id}
                continue
            if current.release_id != release_id:
                results[participant] = {"status": "ROLLBACK_REVIEW_REQUIRED", "currentReleaseId": current.release_id, "expectedCandidate": release_id}
                continue
            try:
                self.stores[participant].compare_and_swap(self.pointer_key, original.raw, expected_etag=current.etag)
                verified = self._read_pointer(participant)
            except (ObjectStoreError, AutomationError) as exc:
                results[participant] = {"status": "ROLLBACK_REVIEW_REQUIRED", "error": getattr(exc, "code", type(exc).__name__)}
                continue
            results[participant] = {"status": "ROLLED_BACK", "releaseId": verified.release_id}
        return results

    def activate(self, *, candidate_release_id: str, expected_current_release_id: str) -> dict[str, Any]:
        candidate = self._release(candidate_release_id, field="candidateReleaseId")
        expected = self._release(expected_current_release_id, field="expectedCurrentReleaseId")
        before = {name: self._read_pointer(name) for name in ACTIVATION_PARTICIPANTS}
        mismatches = {name: snapshot.release_id for name, snapshot in before.items() if snapshot.release_id != expected}
        if mismatches:
            return {
                "status": "STOPPED",
                "reason": "REGIONAL_POINTER_MISMATCH",
                "candidateReleaseId": candidate,
                "expectedCurrentReleaseId": expected,
                "before": {name: snapshot.as_dict() for name, snapshot in before.items()},
                "mismatches": mismatches,
                "pointerWrites": 0,
                "rollback": {},
            }

        changed: dict[str, PointerSnapshot] = {}
        smoke: dict[str, Any] = {}
        stages: list[str] = []
        try:
            stages.append("US_CAS")
            us_after = self._activate_one(before["us"], candidate)
            if us_after.raw != before["us"].raw:
                changed["us"] = us_after
            smoke["us"] = self._smoke("us", candidate)
            if smoke["us"].get("status") == "FAILED":
                raise AutomationError("US activation smoke failed", code="US_ACTIVATION_FAILED")

            stages.append("SA_CAS")
            sa_after = self._activate_one(before["sa"], candidate)
            if sa_after.raw != before["sa"].raw:
                changed["sa"] = sa_after
            smoke["sa"] = self._smoke("sa", candidate)
            if smoke["sa"].get("status") == "FAILED":
                raise AutomationError("South America activation smoke failed", code="SA_ACTIVATION_FAILED")

            stages.append("R2_CAS")
            r2_after = self._activate_one(before["r2"], candidate)
            if r2_after.raw != before["r2"].raw:
                changed["r2"] = r2_after
            smoke["r2"] = self._smoke("r2", candidate)
            if smoke["r2"].get("status") == "FAILED":
                raise AutomationError("durable R2 activation smoke failed", code="R2_ACTIVATION_FAILED")
        except AutomationError as exc:
            if stages[-1:] == ["US_CAS"]:
                eligible = ("us",)
            elif stages[-1:] == ["SA_CAS"]:
                eligible = ("sa", "us")
            else:
                eligible = ("r2", "sa", "us")
            rollback = self._restore_candidates(before, release_id=candidate, participants=eligible)
            rollback_status = "ROLLED_BACK" if all(item.get("status") in {"ROLLED_BACK", "ALREADY_RESTORED"} for item in rollback.values()) else "ROLLBACK_REVIEW_REQUIRED"
            return {
                "status": "STOPPED" if rollback_status == "ROLLED_BACK" else "ROLLBACK_REVIEW_REQUIRED",
                "reason": exc.code,
                "message": str(exc),
                "candidateReleaseId": candidate,
                "expectedCurrentReleaseId": expected,
                "stages": stages,
                "before": {name: snapshot.as_dict() for name, snapshot in before.items()},
                "smoke": smoke,
                "pointerWrites": len(changed),
                "rollback": {"status": rollback_status, "participants": rollback},
            }

        after = {name: self._read_pointer(name) for name in ACTIVATION_PARTICIPANTS}
        return {
            "status": "ACTIVE",
            "candidateReleaseId": candidate,
            "expectedCurrentReleaseId": expected,
            "stages": stages,
            "before": {name: snapshot.as_dict() for name, snapshot in before.items()},
            "after": {name: snapshot.as_dict() for name, snapshot in after.items()},
            "smoke": smoke,
            "pointerWrites": len(changed),
            "rollback": {"status": "NOT_REQUIRED", "participants": {}},
        }


__all__ = [
    "AutomationError",
    "ACTIVATION_PARTICIPANTS",
    "ACTIVE_POINTER_KEY",
    "ACTIVE_POINTER_SCHEMA",
    "RELEASE_MODES",
    "DATA_CANARY_COLD_10_LINE_MAX_MS",
    "DATA_CANARY_LOCATIONS",
    "DATA_CANARY_POLICY_VERSION",
    "DATA_CANARY_REGIONS",
    "DATA_CANARY_REQUESTS",
    "DATA_CANARY_SCHEMA_VERSION",
    "DATA_CANARY_SHOPPING_LINE_COUNTS",
    "DATA_CANARY_WARM_SMOKE_MAX_MS",
    "DistributedReleaseLease",
    "EXECUTION_PREFIX",
    "LEASE_KEY",
    "LEASE_TTL_SECONDS",
    "LeaseHandle",
    "LeaseOutcome",
    "PointerSnapshot",
    "POLICY_VERSION",
    "SCHEMA_VERSION",
    "anomaly_gate",
    "discovery_decision",
    "evaluate_data_canary",
    "redact_secrets",
    "run_release_promotion",
    "stage_verify_and_canary",
    "TransactionalActivationCoordinator",
    "write_execution_summary",
]

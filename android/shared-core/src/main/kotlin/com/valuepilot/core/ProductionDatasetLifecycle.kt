package com.valuepilot.core

/**
 * Exact provider dataset snapshot identity used by production lifecycle logic.
 *
 * A dataset namespace is a storage/provenance boundary, not a snapshot. This
 * reference deliberately adds an opaque adapter-supplied snapshot id so one
 * imported file/extract can be activated, suspended, expired, or revoked
 * without silently applying that lifecycle state to another snapshot.
 */
data class ProductionDatasetSnapshotRef(
    val providerId: EvidenceProviderId,
    val datasetNamespaceId: String,
    val snapshotId: String
) {
    init {
        require(datasetNamespaceId.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))
        require(snapshotId.matches(Regex("[a-z0-9][a-z0-9._-]{0,127}")))
    }
}

enum class ProductionDatasetSnapshotBindingBlocker {
    PROVIDER_SCOPE_MISMATCH,
    DATASET_SCOPE_MISMATCH
}

/**
 * Snapshot-bound staging object.
 *
 * This is still NOT an Offer and carries no rankability permission. The
 * wrapper only proves that a previously staged candidate has been associated
 * with an explicit provider/dataset snapshot identity.
 */
data class SnapshotBoundProductionOfferCandidate private constructor(
    val candidate: StagedProductionOfferCandidate,
    val snapshot: ProductionDatasetSnapshotRef
) {
    companion object {
        internal fun create(
            candidate: StagedProductionOfferCandidate,
            snapshot: ProductionDatasetSnapshotRef
        ): SnapshotBoundProductionOfferCandidate =
            SnapshotBoundProductionOfferCandidate(candidate, snapshot)
    }
}

data class ProductionDatasetSnapshotBindingResult(
    val boundCandidate: SnapshotBoundProductionOfferCandidate?,
    val blockers: Set<ProductionDatasetSnapshotBindingBlocker>
) {
    init {
        require((boundCandidate != null) == blockers.isEmpty()) {
            "A snapshot-bound candidate exists if and only if there are no blockers"
        }
    }

    val bound: Boolean
        get() = boundCandidate != null
}

object ProductionDatasetSnapshotBinder {

    fun bind(
        candidate: StagedProductionOfferCandidate,
        snapshot: ProductionDatasetSnapshotRef
    ): ProductionDatasetSnapshotBindingResult {
        val blockers = linkedSetOf<ProductionDatasetSnapshotBindingBlocker>()

        if (candidate.provider.id != snapshot.providerId) {
            blockers += ProductionDatasetSnapshotBindingBlocker.PROVIDER_SCOPE_MISMATCH
        }
        if (candidate.dataset.id != snapshot.datasetNamespaceId) {
            blockers += ProductionDatasetSnapshotBindingBlocker.DATASET_SCOPE_MISMATCH
        }

        return if (blockers.isEmpty()) {
            ProductionDatasetSnapshotBindingResult(
                boundCandidate =
                    SnapshotBoundProductionOfferCandidate.create(
                        candidate = candidate,
                        snapshot = snapshot
                    ),
                blockers = emptySet()
            )
        } else {
            ProductionDatasetSnapshotBindingResult(
                boundCandidate = null,
                blockers = blockers
            )
        }
    }
}

enum class ProductionDatasetLifecycleState {
    ACTIVE,
    SUSPENDED,
    REVOKED,
    RETIRED
}

/**
 * Current lifecycle assertion for one exact snapshot + activation profile.
 *
 * revision is caller supplied and monotonic within the registry. Shared core
 * owns no clock and performs no persistence. REVOKED and RETIRED are terminal
 * for the same snapshot/profile key; re-enabling requires a new snapshot id.
 */
data class ProductionDatasetLifecycleRecord(
    val snapshot: ProductionDatasetSnapshotRef,
    val activationProfileId: String,
    val revision: Long,
    val state: ProductionDatasetLifecycleState,
    val effectiveAtEpochMillis: Long,
    val validUntilEpochMillis: Long? = null,
    val basisId: String
) {
    init {
        require(activationProfileId.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")))
        require(revision > 0L)
        require(effectiveAtEpochMillis > 0L)
        validUntilEpochMillis?.let {
            require(it >= effectiveAtEpochMillis)
        }
        require(basisId.isNotBlank())
        require(basisId.length <= 240)
    }

    val terminal: Boolean
        get() =
            state == ProductionDatasetLifecycleState.REVOKED ||
                state == ProductionDatasetLifecycleState.RETIRED
}

data class ProductionDatasetLifecycleKey(
    val snapshot: ProductionDatasetSnapshotRef,
    val activationProfileId: String
)

enum class ProductionDatasetLifecycleWriteResult {
    ADDED,
    UPDATED,
    DUPLICATE,
    REJECTED_STALE_REVISION,
    REJECTED_REVISION_COLLISION,
    REJECTED_TERMINAL_SNAPSHOT
}

/**
 * Bounded in-memory lifecycle prototype.
 *
 * It intentionally mirrors the source-isolated evidence design: state is keyed
 * by exact provider + dataset namespace + snapshot + activation profile. One
 * dataset can therefore be suspended/revoked without mutating another.
 */
class ProductionDatasetLifecycleRegistry {
    private val records =
        linkedMapOf<ProductionDatasetLifecycleKey, ProductionDatasetLifecycleRecord>()

    fun write(
        record: ProductionDatasetLifecycleRecord
    ): ProductionDatasetLifecycleWriteResult {
        val key =
            ProductionDatasetLifecycleKey(
                snapshot = record.snapshot,
                activationProfileId = record.activationProfileId
            )
        val existing = records[key]

        if (existing == null) {
            records[key] = record
            return ProductionDatasetLifecycleWriteResult.ADDED
        }

        if (record.revision < existing.revision) {
            return ProductionDatasetLifecycleWriteResult.REJECTED_STALE_REVISION
        }

        if (record.revision == existing.revision) {
            return if (record == existing) {
                ProductionDatasetLifecycleWriteResult.DUPLICATE
            } else {
                ProductionDatasetLifecycleWriteResult.REJECTED_REVISION_COLLISION
            }
        }

        if (existing.terminal) {
            return ProductionDatasetLifecycleWriteResult.REJECTED_TERMINAL_SNAPSHOT
        }

        records[key] = record
        return ProductionDatasetLifecycleWriteResult.UPDATED
    }

    fun currentRecord(
        snapshot: ProductionDatasetSnapshotRef,
        activationProfileId: String
    ): ProductionDatasetLifecycleRecord? =
        records[
            ProductionDatasetLifecycleKey(
                snapshot = snapshot,
                activationProfileId = activationProfileId
            )
        ]

    fun recordsForDataset(
        providerId: EvidenceProviderId,
        datasetNamespaceId: String
    ): List<ProductionDatasetLifecycleRecord> =
        records.values
            .filter {
                it.snapshot.providerId == providerId &&
                    it.snapshot.datasetNamespaceId == datasetNamespaceId
            }
            .sortedWith(
                compareBy<ProductionDatasetLifecycleRecord>(
                    { it.snapshot.snapshotId },
                    { it.activationProfileId }
                )
            )

    fun size(): Int = records.size
}

enum class ProductionDatasetActivationBlocker {
    INSUFFICIENT_ACTIVATION_PROFILE,
    LIFECYCLE_RECORD_MISSING,
    LIFECYCLE_SCOPE_MISMATCH,
    ACTIVATION_PROFILE_MISMATCH,
    AUTHORIZATION_SCOPE_MISMATCH,
    PRODUCTION_AUTHORIZATION_BLOCKED,
    DATASET_SUSPENDED,
    DATASET_REVOKED,
    DATASET_RETIRED,
    ACTIVATION_NOT_YET_EFFECTIVE,
    ACTIVATION_EXPIRED,
    OFFER_FRESHNESS_UNKNOWN,
    OFFER_FUTURE_DATED,
    OFFER_STALE
}

/**
 * Point-in-time activation evaluation.
 *
 * activeCandidate is intentionally the snapshot-bound staged object, not an
 * Offer. Downstream code must re-evaluate this lifecycle boundary at its own
 * decision time; this result grants no durable rankability or display right.
 */
data class ProductionDatasetActivationResult(
    val activeCandidate: SnapshotBoundProductionOfferCandidate?,
    val blockers: Set<ProductionDatasetActivationBlocker>,
    val authorizationDecision: ProductionActivationDecision?,
    val currentFreshness: EvidenceFreshness
) {
    init {
        require((activeCandidate != null) == blockers.isEmpty()) {
            "An active candidate exists if and only if there are no blockers"
        }
    }

    val active: Boolean
        get() = activeCandidate != null
}

/**
 * Fail-closed activation/revocation evaluator for one exact dataset snapshot.
 *
 * Every evaluation re-checks:
 * - at least the full base mobile-catalog activation profile;
 * - exact lifecycle snapshot/profile scope;
 * - current provider production authorization;
 * - lifecycle state/effective window; and
 * - current per-offer price freshness using the original observation time.
 *
 * It performs no I/O, reads no hidden clock, creates no Offer, and never treats
 * a previously successful staging decision as permanent authorization.
 */
object ProductionDatasetActivationEvaluator {

    fun evaluate(
        boundCandidate: SnapshotBoundProductionOfferCandidate,
        lifecycleRecord: ProductionDatasetLifecycleRecord?,
        currentAuthorizationAssessment: ProviderProductionAuthorizationAssessment,
        activationProfile: ProductionActivationProfile,
        evaluatedAtEpochMillis: Long,
        offerFreshnessPolicy: EvidenceFreshnessPolicy
    ): ProductionDatasetActivationResult {
        require(evaluatedAtEpochMillis > 0L)

        val blockers = linkedSetOf<ProductionDatasetActivationBlocker>()
        val candidate = boundCandidate.candidate
        val snapshot = boundCandidate.snapshot

        val baseProfile = ProductionActivationProfiles.CONSUMER_MOBILE_CATALOG
        if (!activationProfile.requiredGates.containsAll(baseProfile.requiredGates)) {
            blockers += ProductionDatasetActivationBlocker.INSUFFICIENT_ACTIVATION_PROFILE
        }

        if (lifecycleRecord == null) {
            blockers += ProductionDatasetActivationBlocker.LIFECYCLE_RECORD_MISSING
        } else {
            if (lifecycleRecord.snapshot != snapshot) {
                blockers += ProductionDatasetActivationBlocker.LIFECYCLE_SCOPE_MISMATCH
            }
            if (
                lifecycleRecord.activationProfileId != candidate.activationProfileId ||
                lifecycleRecord.activationProfileId != activationProfile.id
            ) {
                blockers += ProductionDatasetActivationBlocker.ACTIVATION_PROFILE_MISMATCH
            }

            when (lifecycleRecord.state) {
                ProductionDatasetLifecycleState.ACTIVE -> Unit
                ProductionDatasetLifecycleState.SUSPENDED ->
                    blockers += ProductionDatasetActivationBlocker.DATASET_SUSPENDED
                ProductionDatasetLifecycleState.REVOKED ->
                    blockers += ProductionDatasetActivationBlocker.DATASET_REVOKED
                ProductionDatasetLifecycleState.RETIRED ->
                    blockers += ProductionDatasetActivationBlocker.DATASET_RETIRED
            }

            if (evaluatedAtEpochMillis < lifecycleRecord.effectiveAtEpochMillis) {
                blockers += ProductionDatasetActivationBlocker.ACTIVATION_NOT_YET_EFFECTIVE
            }
            if (
                lifecycleRecord.validUntilEpochMillis != null &&
                evaluatedAtEpochMillis > lifecycleRecord.validUntilEpochMillis
            ) {
                blockers += ProductionDatasetActivationBlocker.ACTIVATION_EXPIRED
            }
        }

        if (candidate.activationProfileId != activationProfile.id) {
            blockers += ProductionDatasetActivationBlocker.ACTIVATION_PROFILE_MISMATCH
        }

        val authorizationScopeMatches =
            currentAuthorizationAssessment.providerId == snapshot.providerId &&
                currentAuthorizationAssessment.datasetNamespaceId ==
                    snapshot.datasetNamespaceId
        if (!authorizationScopeMatches) {
            blockers += ProductionDatasetActivationBlocker.AUTHORIZATION_SCOPE_MISMATCH
        }

        val authorizationDecision =
            if (authorizationScopeMatches) {
                ProductionAuthorizationEvaluator.evaluate(
                    assessment = currentAuthorizationAssessment,
                    profile = activationProfile
                )
            } else {
                null
            }

        if (authorizationDecision?.authorized != true) {
            blockers += ProductionDatasetActivationBlocker.PRODUCTION_AUTHORIZATION_BLOCKED
        }

        val currentFreshness =
            EvidenceFreshnessEvaluator.classify(
                observedAtEpochMillis = candidate.priceObservedAtEpochMillis,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                policy = offerFreshnessPolicy
            )

        when (currentFreshness) {
            EvidenceFreshness.UNKNOWN ->
                blockers += ProductionDatasetActivationBlocker.OFFER_FRESHNESS_UNKNOWN
            EvidenceFreshness.FUTURE_DATED ->
                blockers += ProductionDatasetActivationBlocker.OFFER_FUTURE_DATED
            EvidenceFreshness.STALE ->
                blockers += ProductionDatasetActivationBlocker.OFFER_STALE
            EvidenceFreshness.FRESH,
            EvidenceFreshness.AGING -> Unit
        }

        return if (blockers.isEmpty()) {
            ProductionDatasetActivationResult(
                activeCandidate = boundCandidate,
                blockers = emptySet(),
                authorizationDecision = authorizationDecision,
                currentFreshness = currentFreshness
            )
        } else {
            ProductionDatasetActivationResult(
                activeCandidate = null,
                blockers = blockers,
                authorizationDecision = authorizationDecision,
                currentFreshness = currentFreshness
            )
        }
    }
}

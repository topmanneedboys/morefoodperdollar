package com.valuepilot.core

/**
 * Point-in-time production price view created only by [ProductionOfferViewEvaluator].
 *
 * The constructor is private and the evaluator starts from the raw provider import
 * record, then re-runs staging, snapshot binding, current lifecycle-registry state,
 * current production authorization, geography/freshness checks and namespace-wide
 * disposition. Intermediate staged/bound objects are never accepted as authority.
 *
 * This is still not a durable authorization token and exposes no rankability flag.
 * Callers must re-evaluate before a later production display/ranking decision because
 * rights, lifecycle state, namespace disposition and offer freshness can change.
 */
class LifecycleBoundProductionOfferView private constructor(
    private val boundCandidate: SnapshotBoundProductionOfferCandidate,
    val evaluatedAtEpochMillis: Long,
    val currentFreshness: EvidenceFreshness,
    val lifecycleRevision: Long,
    val dispositionRevision: Long
) {
    val snapshot: ProductionDatasetSnapshotRef
        get() = boundCandidate.snapshot

    val provider: EvidenceProvider
        get() = boundCandidate.candidate.provider

    val source: ShoppingSource
        get() = boundCandidate.candidate.source

    val dataset: EvidenceDatasetNamespace
        get() = boundCandidate.candidate.dataset

    val sourceProductIdentity: SourceProductIdentity
        get() = boundCandidate.candidate.sourceProductIdentity

    val productName: String
        get() = boundCandidate.candidate.productName

    val currentPrice: Money
        get() = boundCandidate.candidate.currentPrice

    /** Provider-documented reference/non-discounted price, not historical price. */
    val referencePrice: Money?
        get() = boundCandidate.candidate.referencePrice

    val priceObservedAtEpochMillis: Long
        get() = boundCandidate.candidate.priceObservedAtEpochMillis

    val offerCountryCode: String
        get() = boundCandidate.candidate.offerCountryCode

    val availability: AvailabilityEvidence
        get() = boundCandidate.candidate.availability

    val productUrl: String?
        get() = boundCandidate.candidate.productUrl

    val imageUrl: String?
        get() = boundCandidate.candidate.imageUrl

    /**
     * Shared-core-only arithmetic representation.
     *
     * The provider reference price intentionally does not populate Offer.previous:
     * reference/list/non-discounted price is not necessarily a prior historical price.
     * No member price or promotion is invented here.
     */
    internal fun arithmeticOffer(): Offer =
        Offer(current = currentPrice)

    companion object {
        internal fun create(
            boundCandidate: SnapshotBoundProductionOfferCandidate,
            evaluatedAtEpochMillis: Long,
            currentFreshness: EvidenceFreshness,
            lifecycleRevision: Long,
            dispositionRevision: Long
        ): LifecycleBoundProductionOfferView =
            LifecycleBoundProductionOfferView(
                boundCandidate = boundCandidate,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                currentFreshness = currentFreshness,
                lifecycleRevision = lifecycleRevision,
                dispositionRevision = dispositionRevision
            )
    }
}

enum class ProductionOfferViewBlocker {
    STAGING_BLOCKED,
    SNAPSHOT_BINDING_BLOCKED,
    SNAPSHOT_LIFECYCLE_BLOCKED,
    NAMESPACE_DISPOSITION_BLOCKED
}

data class ProductionOfferViewResult(
    val view: LifecycleBoundProductionOfferView?,
    val blockers: Set<ProductionOfferViewBlocker>,
    val stagingDecision: ProductionOfferCandidateResult,
    val bindingDecision: ProductionDatasetSnapshotBindingResult?,
    val lifecycleDecision: ProductionDatasetActivationResult?,
    val namespaceDecision: ProductionDatasetUseDecision
) {
    init {
        require((view != null) == blockers.isEmpty()) {
            "A production offer view exists if and only if all current gates pass"
        }
    }

    val available: Boolean
        get() = view != null
}

/**
 * Composite production read boundary from raw provider evidence.
 *
 * Important properties:
 * - starts from [ProviderOfferImportRecord], never a caller-supplied staged candidate;
 * - re-runs price semantics, source identity, geography and per-offer freshness;
 * - binds the newly accepted candidate to the requested exact snapshot;
 * - reads lifecycle state only from [ProductionDatasetLifecycleRegistry];
 * - re-checks current production authorization at lifecycle evaluation;
 * - reads namespace disposition only from [ProductionDatasetDispositionRegistry];
 * - performs no I/O and reads no hidden clock;
 * - creates no rankability decision, quantity, unit value or promotion.
 */
object ProductionOfferViewEvaluator {

    fun evaluate(
        record: ProviderOfferImportRecord,
        priceRoles: ProductionPriceFieldRoles,
        currentAuthorizationAssessment: ProviderProductionAuthorizationAssessment,
        activationProfile: ProductionActivationProfile,
        geography: ProviderDatasetOfferGeography,
        targetCountryCode: String,
        snapshot: ProductionDatasetSnapshotRef,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        offerFreshnessPolicy: EvidenceFreshnessPolicy
    ): ProductionOfferViewResult {
        val stagingDecision =
            ProductionOfferCandidateEvaluator.evaluate(
                record = record,
                priceRoles = priceRoles,
                authorizationAssessment = currentAuthorizationAssessment,
                activationProfile = activationProfile,
                geography = geography,
                targetCountryCode = targetCountryCode,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                offerFreshnessPolicy = offerFreshnessPolicy
            )

        val namespaceDecision =
            ProductionDatasetUseDispositionEvaluator.evaluate(
                expectedNamespace = record.dataset,
                registry = dispositionRegistry
            )

        val bindingDecision =
            stagingDecision.candidate?.let { candidate ->
                ProductionDatasetSnapshotBinder.bind(
                    candidate = candidate,
                    snapshot = snapshot
                )
            }

        val boundCandidate = bindingDecision?.boundCandidate
        val lifecycleRecord =
            boundCandidate?.let {
                lifecycleRegistry.currentRecord(
                    snapshot = it.snapshot,
                    activationProfileId = activationProfile.id
                )
            }

        val lifecycleDecision =
            boundCandidate?.let {
                ProductionDatasetActivationEvaluator.evaluate(
                    boundCandidate = it,
                    lifecycleRecord = lifecycleRecord,
                    currentAuthorizationAssessment = currentAuthorizationAssessment,
                    activationProfile = activationProfile,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    offerFreshnessPolicy = offerFreshnessPolicy
                )
            }

        val blockers = linkedSetOf<ProductionOfferViewBlocker>()
        if (!stagingDecision.accepted) {
            blockers += ProductionOfferViewBlocker.STAGING_BLOCKED
        }
        if (stagingDecision.accepted && bindingDecision?.bound != true) {
            blockers += ProductionOfferViewBlocker.SNAPSHOT_BINDING_BLOCKED
        }
        if (boundCandidate != null && lifecycleDecision?.active != true) {
            blockers += ProductionOfferViewBlocker.SNAPSHOT_LIFECYCLE_BLOCKED
        }
        if (!namespaceDecision.usableFromNamespacePolicy) {
            blockers += ProductionOfferViewBlocker.NAMESPACE_DISPOSITION_BLOCKED
        }

        val dispositionRevision = namespaceDecision.disposition?.revision
        if (
            blockers.isEmpty() &&
            boundCandidate != null &&
            lifecycleDecision != null &&
            lifecycleRecord != null &&
            dispositionRevision != null
        ) {
            return ProductionOfferViewResult(
                view =
                    LifecycleBoundProductionOfferView.create(
                        boundCandidate = boundCandidate,
                        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                        currentFreshness = lifecycleDecision.currentFreshness,
                        lifecycleRevision = lifecycleRecord.revision,
                        dispositionRevision = dispositionRevision
                    ),
                blockers = emptySet(),
                stagingDecision = stagingDecision,
                bindingDecision = bindingDecision,
                lifecycleDecision = lifecycleDecision,
                namespaceDecision = namespaceDecision
            )
        }

        return ProductionOfferViewResult(
            view = null,
            blockers = blockers,
            stagingDecision = stagingDecision,
            bindingDecision = bindingDecision,
            lifecycleDecision = lifecycleDecision,
            namespaceDecision = namespaceDecision
        )
    }
}

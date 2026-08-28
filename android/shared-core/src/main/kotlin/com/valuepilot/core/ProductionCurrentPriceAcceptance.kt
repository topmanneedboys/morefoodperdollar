package com.valuepilot.core

/**
 * Result of applying the shared acceptance policy to a lifecycle-bound
 * production current-price claim.
 *
 * [acceptanceRankable] means only that this one price evidence item passes the
 * common evidence-acceptance policy at the supplied decision instant. It is not
 * final Best Value eligibility: factual conflict resolution, package-quantity
 * authority, unit-value compatibility and all downstream ranking gates still
 * apply independently.
 */
data class ProductionCurrentPriceAcceptanceResult(
    val evidence: ProductionCurrentPriceEvidence?,
    val acceptanceDecision: EvidenceAcceptanceDecision?,
    val claimDecision: ProductionCurrentPriceClaimResult
) {
    init {
        require((evidence != null) == (acceptanceDecision != null)) {
            "Production price evidence and acceptance decision must exist together"
        }
        require((evidence != null) == claimDecision.accepted) {
            "Acceptance can only be evaluated for a currently accepted production price claim"
        }
    }

    val acceptanceEvaluated: Boolean
        get() = acceptanceDecision != null

    val acceptanceRankable: Boolean
        get() = acceptanceDecision?.rankable == true

    val displayableByAcceptancePolicy: Boolean
        get() = acceptanceDecision?.displayable == true
}

/**
 * Composite production price acceptance boundary.
 *
 * This evaluator deliberately starts from the raw provider import inputs and
 * re-runs [ProductionCurrentPriceClaimEvaluator]. It therefore re-establishes
 * current authorization, geography, snapshot lifecycle, namespace disposition,
 * price semantics and production freshness before applying the shared evidence
 * acceptance policy.
 *
 * It never accepts a detached [ProductionCurrentPriceEvidence] as authority and
 * never turns an acceptance decision into final ranking eligibility.
 */
object ProductionCurrentPriceAcceptanceEvaluator {

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
        descriptor: ProductionCurrentPriceClaimDescriptor,
        evaluatedAtEpochMillis: Long,
        offerFreshnessPolicy: EvidenceFreshnessPolicy,
        acceptancePolicy: EvidenceAcceptancePolicy
    ): ProductionCurrentPriceAcceptanceResult {
        val claimDecision =
            ProductionCurrentPriceClaimEvaluator.evaluate(
                record = record,
                priceRoles = priceRoles,
                currentAuthorizationAssessment = currentAuthorizationAssessment,
                activationProfile = activationProfile,
                geography = geography,
                targetCountryCode = targetCountryCode,
                snapshot = snapshot,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                descriptor = descriptor,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                offerFreshnessPolicy = offerFreshnessPolicy
            )

        val evidence = claimDecision.evidence
            ?: return ProductionCurrentPriceAcceptanceResult(
                evidence = null,
                acceptanceDecision = null,
                claimDecision = claimDecision
            )

        val acceptanceFacts =
            EvidenceAcceptanceFacts(
                environment = record.environment,
                channel = evidence.acquisitionChannel,
                observationClaimKind = evidence.sourceClaimKind,
                observedAtEpochMillis = evidence.claim.observedAtEpochMillis,
                availability = record.availability,
                promotion = null
            )

        val acceptanceDecision =
            EvidenceAcceptanceEvaluator.evaluate(
                facts = acceptanceFacts,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                policy = acceptancePolicy
            )

        return ProductionCurrentPriceAcceptanceResult(
            evidence = evidence,
            acceptanceDecision = acceptanceDecision,
            claimDecision = claimDecision
        )
    }
}

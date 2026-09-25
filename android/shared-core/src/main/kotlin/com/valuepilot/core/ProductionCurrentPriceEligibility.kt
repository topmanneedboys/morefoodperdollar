package com.valuepilot.core

/**
 * Raw inputs needed to re-evaluate one provider current-price path at a shared
 * final-eligibility decision instant.
 *
 * This is input only, never proof. Production eligibility always begins from
 * these raw inputs plus current lifecycle/disposition registries.
 */
data class ProductionCurrentPriceEligibilityRequest(
    val requestId: String,
    val record: ProviderOfferImportRecord,
    val priceRoles: ProductionPriceFieldRoles,
    val currentAuthorizationAssessment: ProviderProductionAuthorizationAssessment,
    val activationProfile: ProductionActivationProfile,
    val geography: ProviderDatasetOfferGeography,
    val targetCountryCode: String,
    val snapshot: ProductionDatasetSnapshotRef,
    val descriptor: ProductionCurrentPriceClaimDescriptor,
    val offerFreshnessPolicy: EvidenceFreshnessPolicy
) {
    init {
        require(requestId.isNotBlank() && requestId.length <= 200)
    }
}

data class ProductionCurrentPriceEligibilityEvaluation(
    val requestId: String,
    val acceptanceResult: ProductionCurrentPriceAcceptanceResult
)

enum class ProductionCurrentPriceEligibilityBlocker {
    CANDIDATE_REQUEST_MISSING,
    CANDIDATE_CLAIM_BLOCKED,
    CANDIDATE_NOT_ACCEPTANCE_RANKABLE,
    RELEVANT_CLAIM_ID_COLLISION,
    CANDIDATE_FACT_RESOLUTION_MISSING,
    UNRESOLVED_CURRENT_PRICE_CONFLICT,
    RESOLVED_CURRENT_PRICE_DIFFERS
}

/**
 * Point-in-time eligibility result for the current-price stage only.
 *
 * [eligibleForCurrentPriceStage] is deliberately narrower than final Best Value
 * eligibility. Package quantity, unit-value evidence, promotion arithmetic and
 * any later ranking constraints remain downstream.
 */
data class ProductionCurrentPriceEligibilityResult(
    val candidateRequestId: String,
    val candidateEvaluation: ProductionCurrentPriceEligibilityEvaluation?,
    val evaluations: List<ProductionCurrentPriceEligibilityEvaluation>,
    val factResolution: EvidenceFactResolution?,
    val blockers: Set<ProductionCurrentPriceEligibilityBlocker>
) {
    val eligibleForCurrentPriceStage: Boolean
        get() = blockers.isEmpty()

    val eligibleEvidence: ProductionCurrentPriceEvidence?
        get() =
            if (eligibleForCurrentPriceStage) {
                candidateEvaluation?.acceptanceResult?.evidence
            } else {
                null
            }
}

/**
 * Composite current-price conflict + acceptance eligibility boundary.
 *
 * Ordering is intentional:
 * 1. re-run every raw request through lifecycle-bound claim + acceptance;
 * 2. keep every CURRENT_PRICE claim whose production claim path is currently
 *    valid, even when its acceptance decision is DISPLAY_ONLY;
 * 3. resolve exact same-product CURRENT_PRICE facts using the existing conflict
 *    engine without inventing a second conflict policy;
 * 4. require the candidate's exact fact to resolve to the candidate fingerprint;
 * 5. separately require the candidate's acceptance decision to be rankable.
 *
 * This avoids the dangerous shortcut of pre-filtering display-only evidence and
 * thereby hiding a stronger contradictory factual claim.
 *
 * [evaluate] preserves the original one-candidate API. [evaluateAll] is an
 * internal same-instant batching path: it re-runs every raw request exactly once
 * for that call, then derives candidate-specific conflict results from the same
 * immutable evaluation set. The batch result is not persisted and is not an
 * authorization token.
 *
 * The evaluator performs no I/O, owns no clock and mutates no evidence index.
 */
object ProductionCurrentPriceEligibilityEvaluator {

    private const val MAX_REQUESTS = 128

    fun evaluate(
        requests: List<ProductionCurrentPriceEligibilityRequest>,
        candidateRequestId: String,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy
    ): ProductionCurrentPriceEligibilityResult {
        require(candidateRequestId.isNotBlank())

        val evaluations =
            evaluateRequests(
                requests = requests,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                acceptancePolicy = acceptancePolicy
            )

        return evaluateFromEvaluations(
            evaluations = evaluations,
            candidateRequestId = candidateRequestId
        )
    }

    /**
     * Shared-core-only batching for one decision instant.
     *
     * Raw provider requests are re-evaluated once for this invocation. Every
     * request id then receives the exact same candidate-specific eligibility
     * semantics as [evaluate], including display-only conflict participants.
     */
    internal fun evaluateAll(
        requests: List<ProductionCurrentPriceEligibilityRequest>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy
    ): Map<String, ProductionCurrentPriceEligibilityResult> {
        val evaluations =
            evaluateRequests(
                requests = requests,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                acceptancePolicy = acceptancePolicy
            )

        return evaluations.associate { evaluation ->
            evaluation.requestId to
                evaluateFromEvaluations(
                    evaluations = evaluations,
                    candidateRequestId = evaluation.requestId
                )
        }
    }

    private fun evaluateRequests(
        requests: List<ProductionCurrentPriceEligibilityRequest>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy
    ): List<ProductionCurrentPriceEligibilityEvaluation> {
        require(requests.isNotEmpty())
        require(requests.size <= MAX_REQUESTS)
        require(evaluatedAtEpochMillis > 0L)

        val requestIds = requests.map { it.requestId }
        require(requestIds.size == requestIds.toSet().size) {
            "Current-price eligibility request ids must be unique"
        }

        return requests.map { request ->
            ProductionCurrentPriceEligibilityEvaluation(
                requestId = request.requestId,
                acceptanceResult =
                    ProductionCurrentPriceAcceptanceEvaluator.evaluate(
                        record = request.record,
                        priceRoles = request.priceRoles,
                        currentAuthorizationAssessment =
                            request.currentAuthorizationAssessment,
                        activationProfile = request.activationProfile,
                        geography = request.geography,
                        targetCountryCode = request.targetCountryCode,
                        snapshot = request.snapshot,
                        lifecycleRegistry = lifecycleRegistry,
                        dispositionRegistry = dispositionRegistry,
                        descriptor = request.descriptor,
                        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                        offerFreshnessPolicy = request.offerFreshnessPolicy,
                        acceptancePolicy = acceptancePolicy
                    )
            )
        }
    }

    private fun evaluateFromEvaluations(
        evaluations: List<ProductionCurrentPriceEligibilityEvaluation>,
        candidateRequestId: String
    ): ProductionCurrentPriceEligibilityResult {
        val blockers = linkedSetOf<ProductionCurrentPriceEligibilityBlocker>()
        val candidateEvaluation =
            evaluations.firstOrNull { it.requestId == candidateRequestId }

        if (candidateEvaluation == null) {
            blockers += ProductionCurrentPriceEligibilityBlocker.CANDIDATE_REQUEST_MISSING
            return ProductionCurrentPriceEligibilityResult(
                candidateRequestId = candidateRequestId,
                candidateEvaluation = null,
                evaluations = evaluations,
                factResolution = null,
                blockers = blockers
            )
        }

        val candidateAcceptance = candidateEvaluation.acceptanceResult
        val candidateEvidence = candidateAcceptance.evidence
        if (candidateEvidence == null) {
            blockers += ProductionCurrentPriceEligibilityBlocker.CANDIDATE_CLAIM_BLOCKED
            return ProductionCurrentPriceEligibilityResult(
                candidateRequestId = candidateRequestId,
                candidateEvaluation = candidateEvaluation,
                evaluations = evaluations,
                factResolution = null,
                blockers = blockers
            )
        }

        if (!candidateAcceptance.acceptanceRankable) {
            blockers +=
                ProductionCurrentPriceEligibilityBlocker
                    .CANDIDATE_NOT_ACCEPTANCE_RANKABLE
        }

        val relevantAcceptedClaims =
            evaluations
                .mapNotNull { it.acceptanceResult.evidence }
                .filter {
                    it.claim.domain == EvidenceClaimDomain.CURRENT_PRICE &&
                        it.claim.scope.productKey == candidateEvidence.claim.scope.productKey
                }
                .map {
                    IndexedEvidenceClaim(
                        namespace = it.dataset,
                        claim = it.claim
                    )
                }

        val groupedByNamespaceClaimId =
            relevantAcceptedClaims.groupBy {
                it.namespace.id to it.claim.claimId
            }

        val hasClaimIdCollision =
            groupedByNamespaceClaimId.values.any { grouped ->
                grouped.distinct().size > 1
            }

        if (hasClaimIdCollision) {
            blockers +=
                ProductionCurrentPriceEligibilityBlocker.RELEVANT_CLAIM_ID_COLLISION
            return ProductionCurrentPriceEligibilityResult(
                candidateRequestId = candidateRequestId,
                candidateEvaluation = candidateEvaluation,
                evaluations = evaluations,
                factResolution = null,
                blockers = blockers
            )
        }

        val deduplicatedClaims =
            groupedByNamespaceClaimId
                .values
                .map { it.first() }

        val candidateKey =
            EvidenceFactKey(
                domain = EvidenceClaimDomain.CURRENT_PRICE,
                scope = candidateEvidence.claim.scope
            )

        val factResolution =
            EvidenceFactResolver.resolve(deduplicatedClaims)
                .firstOrNull { it.key == candidateKey }

        if (factResolution == null) {
            blockers +=
                ProductionCurrentPriceEligibilityBlocker
                    .CANDIDATE_FACT_RESOLUTION_MISSING
        } else {
            when (factResolution.status) {
                EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT ->
                    blockers +=
                        ProductionCurrentPriceEligibilityBlocker
                            .UNRESOLVED_CURRENT_PRICE_CONFLICT

                EvidenceFactResolutionStatus.RESOLVED ->
                    if (
                        factResolution.selectedValueFingerprint !=
                            candidateEvidence.claim.valueFingerprint
                    ) {
                        blockers +=
                            ProductionCurrentPriceEligibilityBlocker
                                .RESOLVED_CURRENT_PRICE_DIFFERS
                    }
            }
        }

        return ProductionCurrentPriceEligibilityResult(
            candidateRequestId = candidateRequestId,
            candidateEvaluation = candidateEvaluation,
            evaluations = evaluations,
            factResolution = factResolution,
            blockers = blockers
        )
    }
}

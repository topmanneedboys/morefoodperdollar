package com.valuepilot.core

/**
 * One separately attributed materialized PACKAGE_QUANTITY fact candidate.
 *
 * This type does not authorize a provider or grant production rights. Source
 * adapters must establish their own legal/lifecycle permission before supplying
 * evidence here. The candidate preserves the dataset namespace, exact factual
 * claim and exact normalized quantity so conflict handling and unit-value math
 * never collapse provenance.
 *
 * Package quantity at this boundary is deliberately product-scoped rather than
 * merchant/location/channel/currency-scoped. Offer scope belongs to price and
 * availability facts; the package quantity describes the identified product.
 */
data class ProductPackageQuantityEvidenceCandidate(
    val evidenceId: String,
    val namespace: EvidenceDatasetNamespace,
    val claim: EvidenceClaim,
    val quantity: NormalizedQuantity
) {
    init {
        require(evidenceId.isNotBlank() && evidenceId.length <= 240)
        require(claim.domain == EvidenceClaimDomain.PACKAGE_QUANTITY) {
            "Package quantity candidates must use the PACKAGE_QUANTITY domain"
        }
        require(
            claim.scope.merchantKey == null &&
                claim.scope.locationKey == null &&
                claim.scope.commerceChannelKey == null &&
                claim.scope.currencyCode == null
        ) {
            "Package quantity candidates must be scoped to product identity only"
        }
    }
}

data class ProductionUnitValuePolicyAttempt(
    val quantityEvidenceId: String,
    val result: EvidenceBackedUnitValueResult
)

enum class ProductionUnitValueEligibilityBlocker {
    PRICE_STAGE_BLOCKED,
    NO_RELEVANT_PACKAGE_QUANTITY,
    PACKAGE_QUANTITY_CLAIM_ID_COLLISION,
    PACKAGE_QUANTITY_FACT_RESOLUTION_MISSING,
    UNRESOLVED_PACKAGE_QUANTITY_CONFLICT,
    RESOLVED_PACKAGE_QUANTITY_NOT_MATERIALIZED,
    UNIT_VALUE_POLICY_BLOCKED
}

/**
 * Point-in-time result of combining a verified current-price selection with a
 * separately attributed, conflict-resolved package quantity.
 *
 * [rankable] means only that the existing EvidenceBackedUnitValuePolicy produced
 * a deterministic rate after all current-price and quantity gates in this
 * evaluator passed. This still does not choose between products or implement a
 * final Best Value ranking algorithm.
 */
data class ProductionUnitValueEligibilityResult(
    val priceEligibility: ProductionCurrentPriceEligibilityResult,
    val quantityResolution: EvidenceFactResolution?,
    val policyAttempts: List<ProductionUnitValuePolicyAttempt>,
    val selectedQuantityEvidence: ProductPackageQuantityEvidenceCandidate?,
    val unitValueResult: EvidenceBackedUnitValueResult?,
    val blockers: Set<ProductionUnitValueEligibilityBlocker>
) {
    val rankable: Boolean
        get() =
            blockers.isEmpty() &&
                selectedQuantityEvidence != null &&
                unitValueResult?.rankable == true
}

/**
 * Provider-neutral orchestration boundary from current production price evidence
 * to the existing exact unit-value policy.
 *
 * Ordering is deliberate:
 * 1. validate package-quantity candidate bounds/identity before price evaluation;
 * 2. re-run ProductionCurrentPriceEligibilityEvaluator from raw price requests;
 * 3. resolve exact-product package quantity through ProductPackageQuantityFactResolver;
 * 4. evaluate each resolved materialized supporter with EvidenceBackedUnitValuePolicy;
 * 5. accept the first deterministic supporter that the policy itself approves.
 *
 * Step 4 intentionally avoids copying quantity-authority precedence into this
 * class. If multiple claims support one resolved quantity, the authoritative
 * unit-value policy decides whether each supporter is strong and internally
 * consistent enough to drive a rate.
 *
 * This evaluator performs no I/O, owns no clock, mutates no evidence registry,
 * invents no package quantity and creates no provider network dependency.
 */
object ProductionUnitValueEligibilityEvaluator {

    fun evaluate(
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        candidatePriceRequestId: String,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy,
        quantityCandidates: List<ProductPackageQuantityEvidenceCandidate>
    ): ProductionUnitValueEligibilityResult {
        ProductPackageQuantityFactResolver.validateCandidates(quantityCandidates)

        val priceEligibility =
            ProductionCurrentPriceEligibilityEvaluator.evaluate(
                requests = priceRequests,
                candidateRequestId = candidatePriceRequestId,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                acceptancePolicy = acceptancePolicy
            )

        val blockers = linkedSetOf<ProductionUnitValueEligibilityBlocker>()

        if (!priceEligibility.eligibleForCurrentPriceStage) {
            blockers += ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED
            return blocked(
                priceEligibility = priceEligibility,
                blockers = blockers
            )
        }

        val priceEvidence = requireNotNull(priceEligibility.eligibleEvidence)
        val candidateAcceptance =
            requireNotNull(priceEligibility.candidateEvaluation?.acceptanceResult)
        val priceDisposition =
            requireNotNull(candidateAcceptance.acceptanceDecision).disposition
        val currentPrice =
            requireNotNull(
                candidateAcceptance
                    .claimDecision
                    .productionViewDecision
                    .view
            ).currentPrice
        val offer = Offer(current = currentPrice)
        val productKey = priceEvidence.claim.scope.productKey

        val quantitySelection =
            ProductPackageQuantityFactResolver.resolve(
                productKey = productKey,
                candidates = quantityCandidates
            )

        if (!quantitySelection.resolved) {
            blockers +=
                quantitySelection.blockers.map { blocker ->
                    blocker.toProductionBlocker()
                }
            return ProductionUnitValueEligibilityResult(
                priceEligibility = priceEligibility,
                quantityResolution = quantitySelection.factResolution,
                policyAttempts = emptyList(),
                selectedQuantityEvidence = null,
                unitValueResult = null,
                blockers = blockers
            )
        }

        val quantityResolution = requireNotNull(quantitySelection.factResolution)
        val supportingCandidates = quantitySelection.supportingCandidates

        val attempts =
            supportingCandidates.map { quantityEvidence ->
                ProductionUnitValuePolicyAttempt(
                    quantityEvidenceId = quantityEvidence.evidenceId,
                    result =
                        EvidenceBackedUnitValuePolicy.evaluate(
                            EvidenceBackedUnitValueInput(
                                priceClaim = priceEvidence.claim,
                                quantityClaim = quantityEvidence.claim,
                                offer = offer,
                                quantity = quantityEvidence.quantity,
                                priceDisposition = priceDisposition
                            )
                        )
                )
            }

        val selectedAttempt = attempts.firstOrNull { it.result.rankable }

        if (selectedAttempt == null) {
            blockers +=
                ProductionUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED
            return ProductionUnitValueEligibilityResult(
                priceEligibility = priceEligibility,
                quantityResolution = quantityResolution,
                policyAttempts = attempts,
                selectedQuantityEvidence = null,
                unitValueResult = null,
                blockers = blockers
            )
        }

        val selectedQuantityEvidence =
            supportingCandidates.single { candidate ->
                candidate.evidenceId == selectedAttempt.quantityEvidenceId
            }

        return ProductionUnitValueEligibilityResult(
            priceEligibility = priceEligibility,
            quantityResolution = quantityResolution,
            policyAttempts = attempts,
            selectedQuantityEvidence = selectedQuantityEvidence,
            unitValueResult = selectedAttempt.result,
            blockers = emptySet()
        )
    }

    private fun ProductPackageQuantityResolutionBlocker.toProductionBlocker():
        ProductionUnitValueEligibilityBlocker =
        when (this) {
            ProductPackageQuantityResolutionBlocker.NO_RELEVANT_PACKAGE_QUANTITY ->
                ProductionUnitValueEligibilityBlocker.NO_RELEVANT_PACKAGE_QUANTITY

            ProductPackageQuantityResolutionBlocker.CLAIM_ID_COLLISION ->
                ProductionUnitValueEligibilityBlocker.PACKAGE_QUANTITY_CLAIM_ID_COLLISION

            ProductPackageQuantityResolutionBlocker.FACT_RESOLUTION_MISSING ->
                ProductionUnitValueEligibilityBlocker.PACKAGE_QUANTITY_FACT_RESOLUTION_MISSING

            ProductPackageQuantityResolutionBlocker.UNRESOLVED_CONFLICT ->
                ProductionUnitValueEligibilityBlocker.UNRESOLVED_PACKAGE_QUANTITY_CONFLICT

            ProductPackageQuantityResolutionBlocker.RESOLVED_VALUE_NOT_MATERIALIZED ->
                ProductionUnitValueEligibilityBlocker.RESOLVED_PACKAGE_QUANTITY_NOT_MATERIALIZED
        }

    private fun blocked(
        priceEligibility: ProductionCurrentPriceEligibilityResult,
        blockers: Set<ProductionUnitValueEligibilityBlocker>
    ) =
        ProductionUnitValueEligibilityResult(
            priceEligibility = priceEligibility,
            quantityResolution = null,
            policyAttempts = emptyList(),
            selectedQuantityEvidence = null,
            unitValueResult = null,
            blockers = blockers
        )
}

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
 * 1. re-run ProductionCurrentPriceEligibilityEvaluator from raw price requests;
 * 2. keep only PACKAGE_QUANTITY claims for the exact selected product key;
 * 3. detect same-namespace claim-id mutations before resolution;
 * 4. resolve package quantity through the existing EvidenceFactResolver;
 * 5. evaluate each materialized supporter of the resolved value with the
 *    existing EvidenceBackedUnitValuePolicy;
 * 6. accept the first deterministic supporter that the policy itself approves.
 *
 * Step 5 intentionally avoids copying quantity-authority precedence into this
 * class. If multiple claims support one resolved quantity, the authoritative
 * unit-value policy decides whether each supporter is strong and internally
 * consistent enough to drive a rate.
 *
 * This evaluator performs no I/O, owns no clock, mutates no evidence registry,
 * invents no package quantity and creates no provider network dependency.
 */
object ProductionUnitValueEligibilityEvaluator {

    private const val MAX_QUANTITY_CANDIDATES = 128

    fun evaluate(
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        candidatePriceRequestId: String,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy,
        quantityCandidates: List<ProductPackageQuantityEvidenceCandidate>
    ): ProductionUnitValueEligibilityResult {
        require(quantityCandidates.size <= MAX_QUANTITY_CANDIDATES)

        val quantityEvidenceIds = quantityCandidates.map { it.evidenceId }
        require(quantityEvidenceIds.size == quantityEvidenceIds.toSet().size) {
            "Package quantity evidence ids must be unique"
        }

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

        val relevantQuantities =
            quantityCandidates.filter {
                it.claim.scope.productKey == productKey
            }

        if (relevantQuantities.isEmpty()) {
            blockers +=
                ProductionUnitValueEligibilityBlocker.NO_RELEVANT_PACKAGE_QUANTITY
            return blocked(
                priceEligibility = priceEligibility,
                blockers = blockers
            )
        }

        val groupedByNamespaceClaimId =
            relevantQuantities.groupBy {
                it.namespace.id to it.claim.claimId
            }

        val hasClaimIdCollision =
            groupedByNamespaceClaimId.values.any { grouped ->
                grouped.map { it.claim }.distinct().size > 1
            }

        if (hasClaimIdCollision) {
            blockers +=
                ProductionUnitValueEligibilityBlocker
                    .PACKAGE_QUANTITY_CLAIM_ID_COLLISION
            return blocked(
                priceEligibility = priceEligibility,
                blockers = blockers
            )
        }

        val deduplicatedClaims =
            groupedByNamespaceClaimId
                .values
                .map { grouped ->
                    val candidate = grouped.first()
                    IndexedEvidenceClaim(
                        namespace = candidate.namespace,
                        claim = candidate.claim
                    )
                }

        val quantityFactKey =
            EvidenceFactKey(
                domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                scope = EvidenceClaimScope(productKey = productKey)
            )

        val quantityResolution =
            EvidenceFactResolver.resolve(deduplicatedClaims)
                .firstOrNull { it.key == quantityFactKey }

        if (quantityResolution == null) {
            blockers +=
                ProductionUnitValueEligibilityBlocker
                    .PACKAGE_QUANTITY_FACT_RESOLUTION_MISSING
            return blocked(
                priceEligibility = priceEligibility,
                blockers = blockers
            )
        }

        if (
            quantityResolution.status ==
            EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT
        ) {
            blockers +=
                ProductionUnitValueEligibilityBlocker
                    .UNRESOLVED_PACKAGE_QUANTITY_CONFLICT
            return ProductionUnitValueEligibilityResult(
                priceEligibility = priceEligibility,
                quantityResolution = quantityResolution,
                policyAttempts = emptyList(),
                selectedQuantityEvidence = null,
                unitValueResult = null,
                blockers = blockers
            )
        }

        val selectedFingerprint =
            requireNotNull(quantityResolution.selectedValueFingerprint)

        val supportingCandidates =
            relevantQuantities
                .filter { candidate ->
                    candidate.claim.valueFingerprint == selectedFingerprint &&
                        quantityResolution.supportingClaims.any { supporting ->
                            supporting.namespace == candidate.namespace &&
                                supporting.claim == candidate.claim
                        }
                }
                .sortedBy { it.evidenceId }

        if (supportingCandidates.isEmpty()) {
            blockers +=
                ProductionUnitValueEligibilityBlocker
                    .RESOLVED_PACKAGE_QUANTITY_NOT_MATERIALIZED
            return ProductionUnitValueEligibilityResult(
                priceEligibility = priceEligibility,
                quantityResolution = quantityResolution,
                policyAttempts = emptyList(),
                selectedQuantityEvidence = null,
                unitValueResult = null,
                blockers = blockers
            )
        }

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

        val selectedAttempt =
            attempts.firstOrNull { it.result.rankable }

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
            supportingCandidates.single {
                it.evidenceId == selectedAttempt.quantityEvidenceId
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

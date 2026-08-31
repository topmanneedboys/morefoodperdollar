package com.valuepilot.app

import com.valuepilot.core.EvidenceBackedUnitValueInput
import com.valuepilot.core.EvidenceBackedUnitValuePolicy
import com.valuepilot.core.EvidenceBackedUnitValueResult
import com.valuepilot.core.EvidenceFactResolution
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.Offer
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate
import com.valuepilot.core.ProductPackageQuantityFactResolver
import com.valuepilot.core.ProductPackageQuantityResolutionBlocker

enum class UserObservedPriceUnitValueEligibilityBlocker {
    PRICE_CLAIM_UNAVAILABLE,
    NO_RELEVANT_PACKAGE_QUANTITY,
    PACKAGE_QUANTITY_CLAIM_ID_COLLISION,
    PACKAGE_QUANTITY_FACT_RESOLUTION_MISSING,
    UNRESOLVED_PACKAGE_QUANTITY_CONFLICT,
    RESOLVED_PACKAGE_QUANTITY_NOT_MATERIALIZED,
    UNIT_VALUE_POLICY_BLOCKED
}

data class UserObservedPriceUnitValuePolicyAttempt(
    val quantityEvidenceId: String,
    val result: EvidenceBackedUnitValueResult
)

/**
 * Point-in-time result for one retained user-observed price evaluated against a resolved set of
 * independently attributed package-quantity candidates.
 *
 * A rankable result means only that the proof/freshness policy, package-quantity fact resolver and
 * existing evidence-backed unit-value policy all accepted one deterministic price/quantity pair.
 * It does not make the observed price merchant-authoritative, authorize a dataset, prove stock or
 * promotion, compare products, or choose a final Best Value winner.
 */
data class UserProofBackedObservedPriceUnitValueEligibilityResult(
    val priceUse: UserProofBackedObservedPriceUseResult,
    val quantityResolution: EvidenceFactResolution?,
    val policyAttempts: List<UserObservedPriceUnitValuePolicyAttempt>,
    val selectedQuantityEvidence: ProductPackageQuantityEvidenceCandidate?,
    val unitValueResult: EvidenceBackedUnitValueResult?,
    val blockers: Set<UserObservedPriceUnitValueEligibilityBlocker>
) {
    init {
        require((selectedQuantityEvidence != null) == (unitValueResult != null))
        if (selectedQuantityEvidence != null) {
            require(unitValueResult?.rankable == true)
        }
        if (blockers.isEmpty()) {
            require(priceUse.rankable)
            require(selectedQuantityEvidence != null)
        }
    }

    val rankable: Boolean
        get() =
            blockers.isEmpty() &&
                priceUse.rankable &&
                selectedQuantityEvidence != null &&
                unitValueResult?.rankable == true
}

/**
 * Multi-candidate orchestration for proof-backed OBSERVED_PRICE unit-value math.
 *
 * Ordering is deliberate:
 * 1. bound quantity-candidate input independently of price proof state;
 * 2. re-verify retained proof and classify freshness exactly once for this evaluation;
 * 3. stop if no observed-price claim survives proof verification;
 * 4. resolve package quantity for the exact observed-price product key through shared core;
 * 5. evaluate every resolved materialized supporter with EvidenceBackedUnitValuePolicy;
 * 6. select the first policy-approved supporter in the resolver's stable evidence-id order.
 *
 * Quantity candidates supplied here are already-admitted evidence from the caller's source/legal/
 * lifecycle boundary. This evaluator does not authorize datasets. It also deliberately does not use
 * ProductionCurrentPriceEligibilityEvaluator or otherwise upgrade OBSERVED_PRICE to CURRENT_PRICE.
 * Receipt/aging/future-dated proof with a surviving claim continues through the unit-value policy so
 * PRICE_NOT_RANKABLE remains explicit; missing/corrupt proof stops before factual resolution.
 *
 * This class performs no I/O itself, owns no clock, invents no quantity, and creates no availability,
 * promotion, provider authorization, or final-ranking authority.
 */
class UserProofBackedObservedPriceUnitValueEligibilityEvaluator(
    private val priceUsePolicy: UserProofBackedObservedPriceUsePolicy
) {

    fun evaluate(
        confirmation: UserConfirmedObservedPrice,
        evaluatedAtEpochMillis: Long,
        freshnessPolicy: EvidenceFreshnessPolicy,
        quantityCandidates: List<ProductPackageQuantityEvidenceCandidate>
    ): UserProofBackedObservedPriceUnitValueEligibilityResult {
        ProductPackageQuantityFactResolver.validateCandidates(quantityCandidates)

        val priceUse =
            priceUsePolicy.evaluate(
                confirmation = confirmation,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                freshnessPolicy = freshnessPolicy
            )

        val priceClaim =
            priceUse.claim
                ?: return blocked(
                    priceUse = priceUse,
                    blockers = setOf(UserObservedPriceUnitValueEligibilityBlocker.PRICE_CLAIM_UNAVAILABLE)
                )

        val quantitySelection =
            ProductPackageQuantityFactResolver.resolve(
                productKey = priceClaim.scope.productKey,
                candidates = quantityCandidates
            )

        if (!quantitySelection.resolved) {
            return UserProofBackedObservedPriceUnitValueEligibilityResult(
                priceUse = priceUse,
                quantityResolution = quantitySelection.factResolution,
                policyAttempts = emptyList(),
                selectedQuantityEvidence = null,
                unitValueResult = null,
                blockers = quantitySelection.blockers.mapTo(linkedSetOf()) { it.toEligibilityBlocker() }
            )
        }

        val arithmeticOffer = Offer(current = confirmation.price)
        val supportingCandidates = quantitySelection.supportingCandidates
        val attempts =
            supportingCandidates.map { quantityEvidence ->
                UserObservedPriceUnitValuePolicyAttempt(
                    quantityEvidenceId = quantityEvidence.evidenceId,
                    result =
                        EvidenceBackedUnitValuePolicy.evaluate(
                            EvidenceBackedUnitValueInput(
                                priceClaim = priceClaim,
                                quantityClaim = quantityEvidence.claim,
                                offer = arithmeticOffer,
                                quantity = quantityEvidence.quantity,
                                priceDisposition = priceUse.disposition,
                                useMemberPrice = false
                            )
                        )
                )
            }

        val selectedAttempt = attempts.firstOrNull { it.result.rankable }
        if (selectedAttempt == null) {
            return UserProofBackedObservedPriceUnitValueEligibilityResult(
                priceUse = priceUse,
                quantityResolution = quantitySelection.factResolution,
                policyAttempts = attempts,
                selectedQuantityEvidence = null,
                unitValueResult = null,
                blockers = setOf(UserObservedPriceUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED)
            )
        }

        val selectedQuantityEvidence =
            supportingCandidates.single { candidate ->
                candidate.evidenceId == selectedAttempt.quantityEvidenceId
            }

        return UserProofBackedObservedPriceUnitValueEligibilityResult(
            priceUse = priceUse,
            quantityResolution = quantitySelection.factResolution,
            policyAttempts = attempts,
            selectedQuantityEvidence = selectedQuantityEvidence,
            unitValueResult = selectedAttempt.result,
            blockers = emptySet()
        )
    }

    private fun ProductPackageQuantityResolutionBlocker.toEligibilityBlocker():
        UserObservedPriceUnitValueEligibilityBlocker =
        when (this) {
            ProductPackageQuantityResolutionBlocker.NO_RELEVANT_PACKAGE_QUANTITY ->
                UserObservedPriceUnitValueEligibilityBlocker.NO_RELEVANT_PACKAGE_QUANTITY

            ProductPackageQuantityResolutionBlocker.CLAIM_ID_COLLISION ->
                UserObservedPriceUnitValueEligibilityBlocker.PACKAGE_QUANTITY_CLAIM_ID_COLLISION

            ProductPackageQuantityResolutionBlocker.FACT_RESOLUTION_MISSING ->
                UserObservedPriceUnitValueEligibilityBlocker.PACKAGE_QUANTITY_FACT_RESOLUTION_MISSING

            ProductPackageQuantityResolutionBlocker.UNRESOLVED_CONFLICT ->
                UserObservedPriceUnitValueEligibilityBlocker.UNRESOLVED_PACKAGE_QUANTITY_CONFLICT

            ProductPackageQuantityResolutionBlocker.RESOLVED_VALUE_NOT_MATERIALIZED ->
                UserObservedPriceUnitValueEligibilityBlocker.RESOLVED_PACKAGE_QUANTITY_NOT_MATERIALIZED
        }

    private fun blocked(
        priceUse: UserProofBackedObservedPriceUseResult,
        blockers: Set<UserObservedPriceUnitValueEligibilityBlocker>
    ) =
        UserProofBackedObservedPriceUnitValueEligibilityResult(
            priceUse = priceUse,
            quantityResolution = null,
            policyAttempts = emptyList(),
            selectedQuantityEvidence = null,
            unitValueResult = null,
            blockers = blockers
        )
}

package com.valuepilot.app

import com.valuepilot.core.EvidenceBackedUnitValueInput
import com.valuepilot.core.EvidenceBackedUnitValuePolicy
import com.valuepilot.core.EvidenceBackedUnitValueResult
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.Offer

/**
 * Point-in-time exact unit-value result for one retained user-observed price and one already chosen
 * package-quantity fact.
 *
 * [priceUse] is always the freshly re-evaluated proof/freshness decision. [unitValue] is absent only
 * when retained proof cannot expose an observed-price claim at all. A present unit-value result may
 * still be blocked by the shared-core policy, for example because a receipt/aging tag is display-only,
 * the quantity belongs to a different product, or quantity authority is too weak.
 *
 * This result does not select among competing quantity claims and does not mean the product is a final
 * Best Value winner.
 */
data class UserProofBackedObservedPriceUnitValueResult(
    val priceUse: UserProofBackedObservedPriceUseResult,
    val unitValue: EvidenceBackedUnitValueResult?
) {
    init {
        require((priceUse.claim != null) == (unitValue != null))
    }

    val rankable: Boolean
        get() =
            priceUse.rankable &&
                unitValue?.rankable == true
}

/**
 * Narrow bridge from proof-aware observed-price policy to existing deterministic unit-value math.
 *
 * Ordering is deliberate:
 * 1. re-run [UserProofBackedObservedPriceUsePolicy] so retained proof and caller-supplied freshness
 *    are checked again at the requested evaluation instant;
 * 2. stop before arithmetic if proof is missing/corrupt/unverifiable and no claim can be exposed;
 * 3. otherwise delegate the exact observed-price claim, exact confirmed Money, caller-selected
 *    PACKAGE_QUANTITY claim and exact normalized quantity to [EvidenceBackedUnitValuePolicy].
 *
 * [Offer] is used only as the canonical exact-money arithmetic carrier required by shared core. This
 * evaluator supplies only [confirmation.price] as its current arithmetic slot and never supplies a
 * member price, previous price, promotion, availability, or merchant-current-price authorization.
 * The factual claim remains OBSERVED_PRICE throughout.
 *
 * Quantity conflict resolution deliberately remains outside this boundary. Callers must resolve any
 * competing PACKAGE_QUANTITY facts before choosing the one claim/quantity pair supplied here.
 * This evaluator owns no clock, performs no I/O itself, and creates no CURRENT_PRICE evidence.
 */
class UserProofBackedObservedPriceUnitValueEvaluator(
    private val priceUsePolicy: UserProofBackedObservedPriceUsePolicy
) {

    fun evaluate(
        confirmation: UserConfirmedObservedPrice,
        evaluatedAtEpochMillis: Long,
        freshnessPolicy: EvidenceFreshnessPolicy,
        quantityClaim: EvidenceClaim,
        quantity: NormalizedQuantity
    ): UserProofBackedObservedPriceUnitValueResult {
        val priceUse =
            priceUsePolicy.evaluate(
                confirmation = confirmation,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                freshnessPolicy = freshnessPolicy
            )

        val priceClaim =
            priceUse.claim
                ?: return UserProofBackedObservedPriceUnitValueResult(
                    priceUse = priceUse,
                    unitValue = null
                )

        val arithmeticOffer = Offer(current = confirmation.price)
        val unitValue =
            EvidenceBackedUnitValuePolicy.evaluate(
                EvidenceBackedUnitValueInput(
                    priceClaim = priceClaim,
                    quantityClaim = quantityClaim,
                    offer = arithmeticOffer,
                    quantity = quantity,
                    priceDisposition = priceUse.disposition,
                    useMemberPrice = false
                )
            )

        return UserProofBackedObservedPriceUnitValueResult(
            priceUse = priceUse,
            unitValue = unitValue
        )
    }
}

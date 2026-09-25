package com.valuepilot.app

import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate

/**
 * Raw application inputs required to re-evaluate one confirmed observed price for display.
 *
 * The quantity candidates are already-admitted evidence from the caller's source/legal/lifecycle
 * boundary, matching the evaluator contract. This request is not a durable eligibility snapshot.
 */
data class UserObservedPriceUnitValueSurfaceEvaluationRequest(
    val confirmation: UserConfirmedObservedPrice,
    val evaluatedAtEpochMillis: Long,
    val freshnessPolicy: EvidenceFreshnessPolicy,
    val quantityCandidates: List<ProductPackageQuantityEvidenceCandidate>
)

/** Replaceable renderer that receives only immutable, consumer-facing presentation state. */
fun interface UserObservedPriceUnitValueSurfaceRenderer {
    fun render(state: UserObservedPriceUnitValueUiState)
}

/**
 * Application host for the observed-price unit-value surface.
 *
 * Every display request is evaluated again from the original confirmation and explicit caller
 * policy/time inputs. The host therefore cannot reuse a detached proof/freshness/eligibility result
 * after proof deletion, time advancement, or policy changes. The renderer sees only the immutable
 * projection and cannot acquire factual, quantity-resolution, arithmetic, or ranking authority.
 *
 * This host owns no clock, storage, networking, provider/dataset activation, CURRENT_PRICE bridge,
 * availability, promotion, or final Best Value decision. It also deliberately performs no Activity
 * or View wiring; those remain replaceable presentation adapters.
 */
class UserObservedPriceUnitValueSurfaceHost(
    private val evaluator: UserProofBackedObservedPriceUnitValueEligibilityEvaluator,
    private val renderer: UserObservedPriceUnitValueSurfaceRenderer
) {

    fun evaluateAndRender(
        request: UserObservedPriceUnitValueSurfaceEvaluationRequest
    ): UserObservedPriceUnitValueUiState {
        val eligibility =
            evaluator.evaluate(
                confirmation = request.confirmation,
                evaluatedAtEpochMillis = request.evaluatedAtEpochMillis,
                freshnessPolicy = request.freshnessPolicy,
                quantityCandidates = request.quantityCandidates
            )
        val state = UserObservedPriceUnitValueUiProjector.project(eligibility)
        renderer.render(state)
        return state
    }
}

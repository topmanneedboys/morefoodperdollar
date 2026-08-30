package com.valuepilot.core

private const val MAX_STAPLE_WATCH_ALTERNATIVES = 64
private const val MIN_STAPLE_WATCH_ITEMS = 2

/**
 * One alternative store for an already-resolved recurring staple basket.
 *
 * [storePlan] contains the same exact one-store facts used by Practical Shopping.
 * [additionalTravel] is supplied explicitly by an upstream route boundary and represents
 * the extra trip cost versus the user's normal-store baseline. Shared core never derives a
 * hidden hassle/value-of-time score from it.
 */
data class StapleWatchAlternativeCandidate(
    val storePlan: SingleStorePlanCandidate,
    val additionalTravel: ShoppingTravel
)

/**
 * Explicit product policy for deciding whether a store switch is economically meaningful.
 *
 * This is not notification authorization. Evidence freshness, cadence, quiet hours and delivery
 * remain separate future boundaries. The evaluator owns no clock, network or background work.
 */
data class StapleWatchPolicy(
    val minimumSwitchSavings: Money,
    val maxAdditionalTravelSeconds: Long,
    val maxAdditionalDistanceMetres: Long? = null,
    val minimumStapleItemCount: Int = MIN_STAPLE_WATCH_ITEMS
) {
    init {
        require(minimumSwitchSavings.minorUnits >= 0L)
        require(maxAdditionalTravelSeconds >= 0L)
        require(maxAdditionalDistanceMetres == null || maxAdditionalDistanceMetres >= 0L)
        require(minimumStapleItemCount in MIN_STAPLE_WATCH_ITEMS..128)
    }
}

enum class StapleWatchEconomicStatus {
    NOT_EVALUATED_NOT_ENOUGH_STAPLES,
    NOT_EVALUATED_BASELINE_INCOMPLETE,
    NOT_WORTH_SWITCHING,
    SWITCH_WORTHWHILE
}

/**
 * Exact economic decision only. A worthwhile result is intentionally not permission to notify.
 */
data class StapleWatchEconomicDecision(
    val baseline: SingleStorePlanCandidate,
    val status: StapleWatchEconomicStatus,
    val recommendedAlternative: StapleWatchAlternativeCandidate? = null,
    val switchSavings: Money? = null
) {
    init {
        val hasRecommendation = status == StapleWatchEconomicStatus.SWITCH_WORTHWHILE
        require((recommendedAlternative != null) == hasRecommendation)
        require((switchSavings != null) == hasRecommendation)
        require(switchSavings == null || switchSavings.minorUnits > 0L)
    }
}

/**
 * Deterministic basket-level gate for the future Watch My Staples experience.
 *
 * Trust rules:
 * - A one-item request is not a normal staple-watch alert; the minimum item count is explicit.
 * - The normal-store baseline must cover the complete watched basket before any savings claim.
 * - An alternative must cover that same complete basket.
 * - Only exact same-currency/same-precision basket costs are compared.
 * - Additional travel is explicit input and must satisfy explicit route caps.
 * - Savings must be positive and meet the explicit threshold.
 * - Evidence freshness is preserved on the selected SingleStorePlanCandidate but is not interpreted
 *   here; a future alert-authority boundary must decide whether evidence is current enough.
 * - Provider/affiliate economics, UI, clocks, networking and scheduling are absent.
 */
object StapleWatchEconomicEvaluator {

    fun evaluate(
        request: ShoppingRequest,
        baseline: SingleStorePlanCandidate,
        alternatives: List<StapleWatchAlternativeCandidate>,
        policy: StapleWatchPolicy
    ): StapleWatchEconomicDecision {
        require(alternatives.size <= MAX_STAPLE_WATCH_ALTERNATIVES)

        val requestedItems = request.itemKeySet
        validateCandidateItems(requestedItems, baseline.coveredItemKeys)
        requireSameMoneySpec(baseline.knownBasketCost, policy.minimumSwitchSavings)

        require(alternatives.map { it.storePlan.storeKey }.distinct().size == alternatives.size) {
            "Staple-watch alternatives require unique store keys"
        }

        alternatives.forEach { alternative ->
            require(alternative.storePlan.storeKey != baseline.storeKey) {
                "Staple-watch alternative must differ from the normal store"
            }
            validateCandidateItems(requestedItems, alternative.storePlan.coveredItemKeys)
            requireSameMoneySpec(alternative.storePlan.knownBasketCost, policy.minimumSwitchSavings)
        }

        if (request.itemKeys.size < policy.minimumStapleItemCount) {
            return StapleWatchEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.NOT_EVALUATED_NOT_ENOUGH_STAPLES
            )
        }

        if (baseline.coveredItemKeys != requestedItems) {
            return StapleWatchEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.NOT_EVALUATED_BASELINE_INCOMPLETE
            )
        }

        val eligible =
            alternatives.mapNotNull { alternative ->
                val plan = alternative.storePlan
                if (plan.coveredItemKeys != requestedItems) return@mapNotNull null
                if (alternative.additionalTravel.travelTimeSeconds > policy.maxAdditionalTravelSeconds) {
                    return@mapNotNull null
                }
                val distanceCap = policy.maxAdditionalDistanceMetres
                if (
                    distanceCap != null &&
                    alternative.additionalTravel.distanceMetres > distanceCap
                ) {
                    return@mapNotNull null
                }

                val savingsMinor = Math.subtractExact(
                    baseline.knownBasketCost.minorUnits,
                    plan.knownBasketCost.minorUnits
                )
                if (savingsMinor <= 0L) return@mapNotNull null
                if (savingsMinor < policy.minimumSwitchSavings.minorUnits) return@mapNotNull null

                StapleWatchEvaluation(
                    alternative = alternative,
                    savings = policy.minimumSwitchSavings.copy(minorUnits = savingsMinor)
                )
            }

        val best =
            eligible.minWithOrNull(
                compareByDescending<StapleWatchEvaluation> { it.savings.minorUnits }
                    .thenBy { it.alternative.additionalTravel.travelTimeSeconds }
                    .thenBy { it.alternative.additionalTravel.distanceMetres }
                    .thenBy { it.alternative.storePlan.storeKey.value }
            )

        return if (best == null) {
            StapleWatchEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.NOT_WORTH_SWITCHING
            )
        } else {
            StapleWatchEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.SWITCH_WORTHWHILE,
                recommendedAlternative = best.alternative,
                switchSavings = best.savings
            )
        }
    }

    private fun validateCandidateItems(
        requestedItems: Set<ShoppingItemKey>,
        coveredItems: Set<ShoppingItemKey>
    ) {
        require(requestedItems.containsAll(coveredItems)) {
            "Staple-watch candidate contains an item outside the watched basket"
        }
    }

    private fun requireSameMoneySpec(left: Money, right: Money) {
        require(
            left.currencyCode == right.currencyCode &&
                left.fractionDigits == right.fractionDigits
        ) {
            "Staple-watch money must use one explicit currency/precision"
        }
    }

    private data class StapleWatchEvaluation(
        val alternative: StapleWatchAlternativeCandidate,
        val savings: Money
    )
}

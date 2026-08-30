package com.valuepilot.core

private const val MAX_STAPLE_WATCH_ALTERNATIVES = 64
private const val MIN_STAPLE_WATCH_ITEMS = 2
private const val MAX_STAPLE_WATCH_ITEMS = 128

/**
 * One alternative store for an already-resolved recurring staple basket.
 *
 * [storePlan] contains the same exact one-store facts used by Practical Shopping.
 * [additionalTravel] is supplied explicitly by an upstream route boundary and represents
 * the extra trip cost versus the user's normal-store baseline. Shared core never derives a
 * hidden hassle/value-of-time score from it.
 *
 * This legacy-compatible shape remains supported. New Watch-native assembly should prefer
 * [StapleWatchBasketAlternativeCandidate], which does not require unrelated absolute travel.
 */
data class StapleWatchAlternativeCandidate(
    val storePlan: SingleStorePlanCandidate,
    val additionalTravel: ShoppingTravel
)

/**
 * Exact basket facts needed by Watch economics for one store.
 *
 * Unlike [SingleStorePlanCandidate], this Watch-native shape deliberately has no absolute-travel
 * field. Watch switching economics uses only explicitly supplied *additional* travel versus the
 * user's normal-store baseline, so requiring an unused absolute route would encourage callers to
 * invent a zero/placeholder fact. Evidence currentness is preserved here but interpreted upstream.
 */
data class StapleWatchBasketCandidate(
    val storeKey: ShoppingStoreKey,
    val coveredItemKeys: Set<ShoppingItemKey>,
    val knownBasketCost: Money,
    val evidence: ShoppingPlanEvidenceSummary
) {
    init {
        require(coveredItemKeys.size <= MAX_STAPLE_WATCH_ITEMS)
        require(knownBasketCost.minorUnits >= 0L)
        require(evidence.totalItemCount == coveredItemKeys.size)
    }
}

/** One Watch-native alternative with only the route delta actually used by switching policy. */
data class StapleWatchBasketAlternativeCandidate(
    val basket: StapleWatchBasketCandidate,
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
        require(minimumStapleItemCount in MIN_STAPLE_WATCH_ITEMS..MAX_STAPLE_WATCH_ITEMS)
    }
}

enum class StapleWatchEconomicStatus {
    NOT_EVALUATED_NOT_ENOUGH_STAPLES,
    NOT_EVALUATED_BASELINE_INCOMPLETE,
    NOT_WORTH_SWITCHING,
    SWITCH_WORTHWHILE
}

/**
 * Exact Watch-native economic decision only. A worthwhile result is intentionally not permission
 * to notify.
 */
data class StapleWatchBasketEconomicDecision(
    val baseline: StapleWatchBasketCandidate,
    val status: StapleWatchEconomicStatus,
    val recommendedAlternative: StapleWatchBasketAlternativeCandidate? = null,
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
 * Exact economic decision for the legacy Practical-Shopping-shaped Watch entry point.
 * A worthwhile result is intentionally not permission to notify.
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
 * - Evidence freshness is preserved on the selected basket candidate but is not interpreted here;
 *   an upstream currentness boundary decides whether evidence is eligible before assembly.
 * - Provider/affiliate economics, UI, clocks, networking and scheduling are absent.
 *
 * The Watch-native overload owns the arithmetic. The legacy [SingleStorePlanCandidate] overload
 * removes only its unused absolute-travel field and delegates, then maps a recommendation back to
 * the exact original legacy object so existing callers retain object identity and behavior.
 */
object StapleWatchEconomicEvaluator {

    fun evaluate(
        request: ShoppingRequest,
        baseline: SingleStorePlanCandidate,
        alternatives: List<StapleWatchAlternativeCandidate>,
        policy: StapleWatchPolicy
    ): StapleWatchEconomicDecision {
        val nativeAlternatives =
            alternatives.map { alternative ->
                StapleWatchBasketAlternativeCandidate(
                    basket = alternative.storePlan.toStapleWatchBasketCandidate(),
                    additionalTravel = alternative.additionalTravel
                )
            }
        val nativeDecision =
            evaluate(
                request = request,
                baseline = baseline.toStapleWatchBasketCandidate(),
                alternatives = nativeAlternatives,
                policy = policy
            )
        val legacyRecommendation =
            nativeDecision.recommendedAlternative?.let { recommendation ->
                alternatives.single { alternative ->
                    alternative.storePlan.storeKey == recommendation.basket.storeKey
                }
            }

        return StapleWatchEconomicDecision(
            baseline = baseline,
            status = nativeDecision.status,
            recommendedAlternative = legacyRecommendation,
            switchSavings = nativeDecision.switchSavings
        )
    }

    fun evaluate(
        request: ShoppingRequest,
        baseline: StapleWatchBasketCandidate,
        alternatives: List<StapleWatchBasketAlternativeCandidate>,
        policy: StapleWatchPolicy
    ): StapleWatchBasketEconomicDecision {
        require(alternatives.size <= MAX_STAPLE_WATCH_ALTERNATIVES)

        val requestedItems = request.itemKeySet
        validateCandidateItems(requestedItems, baseline.coveredItemKeys)
        requireSameMoneySpec(baseline.knownBasketCost, policy.minimumSwitchSavings)

        require(alternatives.map { it.basket.storeKey }.distinct().size == alternatives.size) {
            "Staple-watch alternatives require unique store keys"
        }

        alternatives.forEach { alternative ->
            require(alternative.basket.storeKey != baseline.storeKey) {
                "Staple-watch alternative must differ from the normal store"
            }
            validateCandidateItems(requestedItems, alternative.basket.coveredItemKeys)
            requireSameMoneySpec(alternative.basket.knownBasketCost, policy.minimumSwitchSavings)
        }

        if (request.itemKeys.size < policy.minimumStapleItemCount) {
            return StapleWatchBasketEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.NOT_EVALUATED_NOT_ENOUGH_STAPLES
            )
        }

        if (baseline.coveredItemKeys != requestedItems) {
            return StapleWatchBasketEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.NOT_EVALUATED_BASELINE_INCOMPLETE
            )
        }

        val eligible =
            alternatives.mapNotNull { alternative ->
                val basket = alternative.basket
                if (basket.coveredItemKeys != requestedItems) return@mapNotNull null
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
                    basket.knownBasketCost.minorUnits
                )
                if (savingsMinor <= 0L) return@mapNotNull null
                if (savingsMinor < policy.minimumSwitchSavings.minorUnits) return@mapNotNull null

                StapleWatchNativeEvaluation(
                    alternative = alternative,
                    savings = policy.minimumSwitchSavings.copy(minorUnits = savingsMinor)
                )
            }

        val best =
            eligible.minWithOrNull(
                compareByDescending<StapleWatchNativeEvaluation> { it.savings.minorUnits }
                    .thenBy { it.alternative.additionalTravel.travelTimeSeconds }
                    .thenBy { it.alternative.additionalTravel.distanceMetres }
                    .thenBy { it.alternative.basket.storeKey.value }
            )

        return if (best == null) {
            StapleWatchBasketEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.NOT_WORTH_SWITCHING
            )
        } else {
            StapleWatchBasketEconomicDecision(
                baseline = baseline,
                status = StapleWatchEconomicStatus.SWITCH_WORTHWHILE,
                recommendedAlternative = best.alternative,
                switchSavings = best.savings
            )
        }
    }

    private fun SingleStorePlanCandidate.toStapleWatchBasketCandidate(): StapleWatchBasketCandidate =
        StapleWatchBasketCandidate(
            storeKey = storeKey,
            coveredItemKeys = coveredItemKeys,
            knownBasketCost = knownBasketCost,
            evidence = evidence
        )

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

    private data class StapleWatchNativeEvaluation(
        val alternative: StapleWatchBasketAlternativeCandidate,
        val savings: Money
    )
}

package com.valuepilot.core

private const val MAX_SHOPPING_ITEMS = 128
private const val MAX_SINGLE_STORE_CANDIDATES = 64
private const val MAX_TWO_STORE_CANDIDATES = 128

@JvmInline
value class ShoppingItemKey(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

@JvmInline
value class ShoppingStoreKey(val value: String) {
    init {
        require(value.isNotBlank())
    }
}

/**
 * A bounded shopping request expressed only as stable item identities.
 *
 * Product resolution, substitutions, presentation labels, network retrieval and
 * retailer-specific logic live outside shared-core.
 */
data class ShoppingRequest(
    val itemKeys: List<ShoppingItemKey>
) {
    init {
        require(itemKeys.isNotEmpty())
        require(itemKeys.size <= MAX_SHOPPING_ITEMS)
        require(itemKeys.distinct().size == itemKeys.size)
    }

    internal val itemKeySet: Set<ShoppingItemKey>
        get() = itemKeys.toSet()
}

/** Explicit route metadata. No hidden value-of-time or human-friction score. */
data class ShoppingTravel(
    val distanceMetres: Long,
    val travelTimeSeconds: Long
) {
    init {
        require(distanceMetres >= 0L)
        require(travelTimeSeconds >= 0L)
    }
}

/**
 * Point-in-time evidence summary supplied by upstream evidence policy.
 *
 * This type deliberately does not own a clock or decide what counts as fresh.
 * AGING remains distinct because accepted real-world evidence may be rankable
 * while aging; presentation must not silently upgrade or downgrade that fact.
 */
data class ShoppingPlanEvidenceSummary(
    val freshItemCount: Int,
    val staleItemCount: Int,
    val unknownFreshnessItemCount: Int,
    val agingItemCount: Int = 0
) {
    init {
        require(freshItemCount >= 0)
        require(staleItemCount >= 0)
        require(unknownFreshnessItemCount >= 0)
        require(agingItemCount >= 0)
    }

    val totalItemCount: Int
        get() = Math.addExact(
            Math.addExact(
                Math.addExact(freshItemCount, agingItemCount),
                staleItemCount
            ),
            unknownFreshnessItemCount
        )
}

/**
 * One-store candidate after matching and price-evidence validation.
 *
 * [knownBasketCost] includes only [coveredItemKeys]. Missing items remain unknown.
 */
data class SingleStorePlanCandidate(
    val storeKey: ShoppingStoreKey,
    val coveredItemKeys: Set<ShoppingItemKey>,
    val knownBasketCost: Money,
    val travel: ShoppingTravel,
    val evidence: ShoppingPlanEvidenceSummary
) {
    init {
        require(coveredItemKeys.size <= MAX_SHOPPING_ITEMS)
        require(knownBasketCost.minorUnits >= 0L)
        require(evidence.totalItemCount == coveredItemKeys.size)
    }
}

/**
 * A precomputed two-store candidate that starts with [baseStoreKey] and adds one
 * exceptional extra stop. It does not authorize arbitrary multi-store routing.
 */
data class TwoStorePlanCandidate(
    val baseStoreKey: ShoppingStoreKey,
    val addedStoreKey: ShoppingStoreKey,
    val coveredItemKeys: Set<ShoppingItemKey>,
    val addedStoreItemKeys: Set<ShoppingItemKey>,
    val knownCombinedBasketCost: Money,
    val additionalTravel: ShoppingTravel,
    val evidence: ShoppingPlanEvidenceSummary
) {
    init {
        require(baseStoreKey != addedStoreKey)
        require(coveredItemKeys.size <= MAX_SHOPPING_ITEMS)
        require(addedStoreItemKeys.isNotEmpty())
        require(addedStoreItemKeys.size <= coveredItemKeys.size)
        require(coveredItemKeys.containsAll(addedStoreItemKeys))
        require(knownCombinedBasketCost.minorUnits >= 0L)
        require(evidence.totalItemCount == coveredItemKeys.size)
    }
}

/**
 * Product policy is explicit. Presentation may expose these values as user
 * preferences. Shared-core never invents a hidden hassle score.
 */
data class PracticalShoppingPolicy(
    val minimumSecondStopSavings: Money,
    val maxAdditionalTravelSeconds: Long,
    val maxAdditionalDistanceMetres: Long? = null
) {
    init {
        require(minimumSecondStopSavings.minorUnits >= 0L)
        require(maxAdditionalTravelSeconds >= 0L)
        require(maxAdditionalDistanceMetres == null || maxAdditionalDistanceMetres >= 0L)
    }
}

enum class PrimaryShoppingPlanKind {
    NO_COVERAGE,
    COMPLETE_PRICE_COMPARISON,
    INCOMPLETE_BEST_COVERAGE
}

enum class SecondStopDecision {
    NOT_EVALUATED_NO_PRIMARY,
    NOT_EVALUATED_PRIMARY_INCOMPLETE,
    NOT_WORTH_IT,
    RECOMMENDED
}

data class PracticalShoppingDecision(
    val primary: SingleStorePlanCandidate?,
    val primaryKind: PrimaryShoppingPlanKind,
    val secondStop: TwoStorePlanCandidate?,
    val secondStopDecision: SecondStopDecision,
    val incrementalSecondStopSavings: Money?
)

/**
 * Deterministic one-store-first shopping decision policy.
 *
 * Important trust rules:
 * - A complete basket always outranks an incomplete basket.
 * - Incomplete baskets are never ranked by known subtotal because their missing
 *   item sets make those totals non-comparable.
 * - A second stop is considered only after a complete one-store baseline exists.
 * - Second-stop savings compare the same complete requested basket.
 * - Currency/fraction precision must match the explicit policy money.
 * - No clock, network, retailer logic, UI or hidden friction/value-of-time score.
 */
object PracticalShoppingPlanner {

    fun evaluate(
        request: ShoppingRequest,
        singleStoreCandidates: List<SingleStorePlanCandidate>,
        twoStoreCandidates: List<TwoStorePlanCandidate>,
        policy: PracticalShoppingPolicy
    ): PracticalShoppingDecision {
        require(singleStoreCandidates.size <= MAX_SINGLE_STORE_CANDIDATES)
        require(twoStoreCandidates.size <= MAX_TWO_STORE_CANDIDATES)

        val requestedItems = request.itemKeySet

        singleStoreCandidates.forEach { candidate ->
            validateCandidateItems(requestedItems, candidate.coveredItemKeys)
            requireSameMoneySpec(candidate.knownBasketCost, policy.minimumSecondStopSavings)
        }

        twoStoreCandidates.forEach { candidate ->
            validateCandidateItems(requestedItems, candidate.coveredItemKeys)
            validateCandidateItems(requestedItems, candidate.addedStoreItemKeys)
            requireSameMoneySpec(candidate.knownCombinedBasketCost, policy.minimumSecondStopSavings)
        }

        val usableSingles = singleStoreCandidates.filter { it.coveredItemKeys.isNotEmpty() }
        if (usableSingles.isEmpty()) {
            return PracticalShoppingDecision(
                primary = null,
                primaryKind = PrimaryShoppingPlanKind.NO_COVERAGE,
                secondStop = null,
                secondStopDecision = SecondStopDecision.NOT_EVALUATED_NO_PRIMARY,
                incrementalSecondStopSavings = null
            )
        }

        val completeSingles = usableSingles.filter { it.coveredItemKeys == requestedItems }
        val primary =
            if (completeSingles.isNotEmpty()) {
                completeSingles.minWithOrNull(
                    compareBy<SingleStorePlanCandidate> { it.knownBasketCost.minorUnits }
                        .thenBy { it.travel.travelTimeSeconds }
                        .thenBy { it.travel.distanceMetres }
                        .thenBy { it.storeKey.value }
                )!!
            } else {
                usableSingles.minWithOrNull(
                    compareByDescending<SingleStorePlanCandidate> { it.coveredItemKeys.size }
                        .thenBy { it.travel.travelTimeSeconds }
                        .thenBy { it.travel.distanceMetres }
                        .thenBy { it.storeKey.value }
                )!!
            }

        val primaryIsComplete = primary.coveredItemKeys == requestedItems
        if (!primaryIsComplete) {
            return PracticalShoppingDecision(
                primary = primary,
                primaryKind = PrimaryShoppingPlanKind.INCOMPLETE_BEST_COVERAGE,
                secondStop = null,
                secondStopDecision = SecondStopDecision.NOT_EVALUATED_PRIMARY_INCOMPLETE,
                incrementalSecondStopSavings = null
            )
        }

        val eligibleSecondStops =
            twoStoreCandidates.mapNotNull { candidate ->
                if (candidate.baseStoreKey != primary.storeKey) return@mapNotNull null
                if (candidate.coveredItemKeys != requestedItems) return@mapNotNull null
                if (candidate.additionalTravel.travelTimeSeconds > policy.maxAdditionalTravelSeconds) {
                    return@mapNotNull null
                }
                val distanceCap = policy.maxAdditionalDistanceMetres
                if (
                    distanceCap != null &&
                    candidate.additionalTravel.distanceMetres > distanceCap
                ) {
                    return@mapNotNull null
                }

                val savingsMinor = Math.subtractExact(
                    primary.knownBasketCost.minorUnits,
                    candidate.knownCombinedBasketCost.minorUnits
                )
                if (savingsMinor < policy.minimumSecondStopSavings.minorUnits) {
                    return@mapNotNull null
                }

                SecondStopEvaluation(
                    candidate = candidate,
                    savings = policy.minimumSecondStopSavings.copy(minorUnits = savingsMinor)
                )
            }

        val bestSecondStop =
            eligibleSecondStops.minWithOrNull(
                compareByDescending<SecondStopEvaluation> { it.savings.minorUnits }
                    .thenBy { it.candidate.additionalTravel.travelTimeSeconds }
                    .thenBy { it.candidate.additionalTravel.distanceMetres }
                    .thenBy { it.candidate.addedStoreKey.value }
            )

        return PracticalShoppingDecision(
            primary = primary,
            primaryKind = PrimaryShoppingPlanKind.COMPLETE_PRICE_COMPARISON,
            secondStop = bestSecondStop?.candidate,
            secondStopDecision =
                if (bestSecondStop == null) {
                    SecondStopDecision.NOT_WORTH_IT
                } else {
                    SecondStopDecision.RECOMMENDED
                },
            incrementalSecondStopSavings = bestSecondStop?.savings
        )
    }

    private fun validateCandidateItems(
        requestedItems: Set<ShoppingItemKey>,
        coveredItems: Set<ShoppingItemKey>
    ) {
        require(requestedItems.containsAll(coveredItems)) {
            "Plan candidate contains an item outside the shopping request"
        }
    }

    private fun requireSameMoneySpec(left: Money, right: Money) {
        require(
            left.currencyCode == right.currencyCode &&
                left.fractionDigits == right.fractionDigits
        ) {
            "Shopping-plan money must use one explicit currency/precision"
        }
    }

    private data class SecondStopEvaluation(
        val candidate: TwoStorePlanCandidate,
        val savings: Money
    )
}

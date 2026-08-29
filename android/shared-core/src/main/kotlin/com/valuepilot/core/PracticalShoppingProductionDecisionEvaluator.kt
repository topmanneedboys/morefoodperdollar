package com.valuepilot.core

/**
 * Exact candidate partition for one Practical Shopping planning money spec.
 *
 * Excluded candidates remain visible to orchestration/audit but are never passed
 * to the planner. No currency conversion or fraction-precision coercion occurs.
 */
data class PracticalShoppingProductionComparableCandidates(
    val singleStoreCandidates: List<SingleStorePlanCandidate>,
    val twoStoreCandidates: List<TwoStorePlanCandidate>,
    val excludedSingleStoreCandidates: List<SingleStorePlanCandidate>,
    val excludedTwoStoreCandidates: List<TwoStorePlanCandidate>
) {
    init {
        val includedSingleKeys = singleStoreCandidates.map { it.storeKey }.toSet()
        require(excludedSingleStoreCandidates.none { it.storeKey in includedSingleKeys })

        val includedPairKeys =
            twoStoreCandidates.map { it.baseStoreKey to it.addedStoreKey }.toSet()
        require(
            excludedTwoStoreCandidates.none {
                (it.baseStoreKey to it.addedStoreKey) in includedPairKeys
            }
        )
    }
}

data class PracticalShoppingProductionDecisionResult(
    val candidateBridgeResult: PracticalShoppingProductionPlanCandidateBridgeResult,
    val comparableCandidates: PracticalShoppingProductionComparableCandidates,
    val decision: PracticalShoppingDecision
)

/**
 * Point-in-time production Practical Shopping decision boundary.
 *
 * This evaluator starts from raw current-price eligibility inputs, reruns the
 * complete production candidate bridge at [evaluatedAtEpochMillis], filters only
 * by the exact money specification already declared by [planningPolicy], and then
 * delegates the shopping decision to [PracticalShoppingPlanner].
 *
 * It does not accept a detached production decision as authority, perform I/O,
 * convert currencies, infer travel, change evidence freshness, fill missing
 * prices, rank stores, or add a hidden convenience/value-of-time score.
 */
object PracticalShoppingProductionDecisionEvaluator {

    fun evaluate(
        request: ShoppingRequest,
        stores: List<PracticalShoppingProductionStoreScope>,
        storePairs: List<PracticalShoppingProductionStorePairScope>,
        priceBindings: List<PracticalShoppingProductionPriceBinding>,
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy,
        planningPolicy: PracticalShoppingPolicy
    ): PracticalShoppingProductionDecisionResult {
        val bridgeResult =
            PracticalShoppingProductionPlanCandidateBridge.evaluate(
                request = request,
                stores = stores,
                storePairs = storePairs,
                priceBindings = priceBindings,
                priceRequests = priceRequests,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                acceptancePolicy = acceptancePolicy
            )

        return decideFromBridge(
            request = request,
            bridgeResult = bridgeResult,
            planningPolicy = planningPolicy
        )
    }

    internal fun decideFromBridge(
        request: ShoppingRequest,
        bridgeResult: PracticalShoppingProductionPlanCandidateBridgeResult,
        planningPolicy: PracticalShoppingPolicy
    ): PracticalShoppingProductionDecisionResult {
        val planningMoney = planningPolicy.minimumSecondStopSavings
        val allSingles = bridgeResult.singleStoreCandidates
        val allPairs = bridgeResult.twoStoreCandidates

        val compatibleSingles =
            allSingles.filter { sameMoneySpec(it.knownBasketCost, planningMoney) }
        val excludedSingles =
            allSingles.filterNot { sameMoneySpec(it.knownBasketCost, planningMoney) }

        val compatiblePairs =
            allPairs.filter { sameMoneySpec(it.knownCombinedBasketCost, planningMoney) }
        val excludedPairs =
            allPairs.filterNot { sameMoneySpec(it.knownCombinedBasketCost, planningMoney) }

        val completeCompatibleBaseKeys =
            compatibleSingles
                .filter { it.coveredItemKeys == request.itemKeySet }
                .mapTo(linkedSetOf()) { it.storeKey }

        require(
            compatiblePairs.all { it.baseStoreKey in completeCompatibleBaseKeys }
        ) {
            "A comparable two-store candidate requires its comparable complete base candidate"
        }

        val comparable =
            PracticalShoppingProductionComparableCandidates(
                singleStoreCandidates = compatibleSingles,
                twoStoreCandidates = compatiblePairs,
                excludedSingleStoreCandidates = excludedSingles,
                excludedTwoStoreCandidates = excludedPairs
            )

        val decision =
            PracticalShoppingPlanner.evaluate(
                request = request,
                singleStoreCandidates = comparable.singleStoreCandidates,
                twoStoreCandidates = comparable.twoStoreCandidates,
                policy = planningPolicy
            )

        return PracticalShoppingProductionDecisionResult(
            candidateBridgeResult = bridgeResult,
            comparableCandidates = comparable,
            decision = decision
        )
    }

    private fun sameMoneySpec(left: Money, right: Money): Boolean =
        left.currencyCode == right.currencyCode &&
            left.fractionDigits == right.fractionDigits
}

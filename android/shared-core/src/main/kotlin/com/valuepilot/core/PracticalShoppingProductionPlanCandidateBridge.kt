package com.valuepilot.core

private const val MAX_PRACTICAL_SHOPPING_PRODUCTION_STORE_PAIRS = 128

/**
 * Explicit ordered base -> added-store scope for a possible Practical Shopping
 * second stop.
 *
 * The bridge performs no routing. [additionalTravel] is caller-supplied route
 * metadata for adding [addedStoreKey] after [baseStoreKey]. The planner remains
 * the only layer that decides whether this additional stop is worth taking.
 */
data class PracticalShoppingProductionStorePairScope(
    val baseStoreKey: ShoppingStoreKey,
    val addedStoreKey: ShoppingStoreKey,
    val additionalTravel: ShoppingTravel
) {
    init {
        require(baseStoreKey != addedStoreKey)
    }
}

enum class PracticalShoppingProductionPairBlocker {
    BASE_STORE_NOT_DECLARED,
    ADDED_STORE_NOT_DECLARED,
    SAME_OFFER_SCOPE,
    BASE_STORE_NOT_COMPLETE,
    ADDED_STORE_DOES_NOT_IMPROVE_BASKET,
    BASKET_TOTAL_OVERFLOW
}

/** Exact auditable price choice for one requested item inside one fixed pair. */
data class PracticalShoppingProductionPairItemSelection(
    val itemKey: ShoppingItemKey,
    val selectedStoreKey: ShoppingStoreKey,
    val currentPriceRequestId: String,
    val selectedPrice: Money,
    val freshness: EvidenceFreshness
) {
    init {
        require(currentPriceRequestId.isNotBlank())
        require(selectedPrice.minorUnits > 0L)
        require(
            freshness == EvidenceFreshness.FRESH ||
                freshness == EvidenceFreshness.AGING
        )
    }
}

data class PracticalShoppingProductionPairEvaluation(
    val pair: PracticalShoppingProductionStorePairScope,
    val itemSelections: List<PracticalShoppingProductionPairItemSelection>,
    val candidate: TwoStorePlanCandidate?,
    val blockers: Set<PracticalShoppingProductionPairBlocker>
) {
    init {
        require((candidate != null) == blockers.isEmpty())
        if (candidate == null) {
            require(itemSelections.isEmpty()) {
                "Blocked two-store evaluations must not expose a partial basket"
            }
        } else {
            require(candidate.baseStoreKey == pair.baseStoreKey)
            require(candidate.addedStoreKey == pair.addedStoreKey)
            require(itemSelections.isNotEmpty())
            require(itemSelections.map { it.itemKey }.distinct().size == itemSelections.size)
            require(itemSelections.any { it.selectedStoreKey == pair.addedStoreKey }) {
                "A two-store candidate must actually use its added store"
            }
        }
    }
}

data class PracticalShoppingProductionPlanCandidateBridgeResult(
    val oneStoreResult: PracticalShoppingProductionCandidateBridgeResult,
    val pairEvaluations: List<PracticalShoppingProductionPairEvaluation>
) {
    val singleStoreCandidates: List<SingleStorePlanCandidate>
        get() = oneStoreResult.singleStoreCandidates

    val twoStoreCandidates: List<TwoStorePlanCandidate>
        get() = pairEvaluations.mapNotNull { it.candidate }
}

/**
 * Point-in-time raw-evidence -> Practical Shopping plan-candidate boundary.
 *
 * The evaluator deliberately re-runs [PracticalShoppingProductionCandidateBridge]
 * from raw current-price inputs at the supplied decision instant. It never accepts
 * a caller-supplied detached SingleStorePlanCandidate as production authority.
 *
 * For each explicitly declared ordered base -> added pair, construction is narrow:
 * - the base must already have a complete one-store basket;
 * - each requested item starts from that verified base price;
 * - an added-store price replaces the base price only when it is usable, has the
 *   exact same money specification, and is strictly cheaper;
 * - equal or incomparable added-store prices stay at the base store;
 * - the added store must contribute at least one selected item;
 * - the resulting basket is complete by construction.
 *
 * This lower-price choice is arithmetic inside one fixed pair, not store ranking.
 * This class does not choose the primary store, value time, apply a savings
 * threshold, or choose among pair candidates. Those decisions remain exclusively
 * in [PracticalShoppingPlanner]. It owns no network, clock, geocoder or router.
 */
object PracticalShoppingProductionPlanCandidateBridge {

    fun evaluate(
        request: ShoppingRequest,
        stores: List<PracticalShoppingProductionStoreScope>,
        storePairs: List<PracticalShoppingProductionStorePairScope>,
        priceBindings: List<PracticalShoppingProductionPriceBinding>,
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy
    ): PracticalShoppingProductionPlanCandidateBridgeResult {
        val oneStoreResult =
            PracticalShoppingProductionCandidateBridge.evaluate(
                request = request,
                stores = stores,
                priceBindings = priceBindings,
                priceRequests = priceRequests,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                acceptancePolicy = acceptancePolicy
            )

        return PracticalShoppingProductionPlanCandidateBridgeResult(
            oneStoreResult = oneStoreResult,
            pairEvaluations =
                buildPairEvaluations(
                    request = request,
                    stores = stores,
                    storePairs = storePairs,
                    oneStoreResult = oneStoreResult
                )
        )
    }

    internal fun buildPairEvaluations(
        request: ShoppingRequest,
        stores: List<PracticalShoppingProductionStoreScope>,
        storePairs: List<PracticalShoppingProductionStorePairScope>,
        oneStoreResult: PracticalShoppingProductionCandidateBridgeResult
    ): List<PracticalShoppingProductionPairEvaluation> {
        require(storePairs.size <= MAX_PRACTICAL_SHOPPING_PRODUCTION_STORE_PAIRS)

        val pairKeys = storePairs.map { it.baseStoreKey to it.addedStoreKey }
        require(pairKeys.size == pairKeys.toSet().size) {
            "Practical Shopping production ordered store pairs must be unique"
        }

        val declaredStoreKeys = stores.map { it.storeKey }
        require(declaredStoreKeys.size == declaredStoreKeys.toSet().size) {
            "Practical Shopping production store keys must be unique"
        }

        val evaluationStoreKeys = oneStoreResult.storeEvaluations.map { it.store.storeKey }
        require(evaluationStoreKeys.size == evaluationStoreKeys.toSet().size) {
            "One-store evaluation keys must be unique"
        }

        val usablePriceKeys =
            oneStoreResult.priceEvaluations
                .filter { it.usable }
                .map { it.binding.storeKey to it.binding.itemKey }
        require(usablePriceKeys.size == usablePriceKeys.toSet().size) {
            "Usable one-store price evaluations must be unique per store/item"
        }

        val storesByKey = stores.associateBy { it.storeKey }
        val storeEvaluationsByKey = oneStoreResult.storeEvaluations.associateBy { it.store.storeKey }
        val usablePricesByStoreItem =
            oneStoreResult.priceEvaluations
                .filter { it.usable }
                .associateBy { it.binding.storeKey to it.binding.itemKey }

        return storePairs
            .sortedWith(
                compareBy<PracticalShoppingProductionStorePairScope>(
                    { it.baseStoreKey.value },
                    { it.addedStoreKey.value }
                )
            )
            .map { pair ->
                buildPairCandidate(
                    request = request,
                    pair = pair,
                    storesByKey = storesByKey,
                    storeEvaluationsByKey = storeEvaluationsByKey,
                    usablePricesByStoreItem = usablePricesByStoreItem
                )
            }
    }

    private fun buildPairCandidate(
        request: ShoppingRequest,
        pair: PracticalShoppingProductionStorePairScope,
        storesByKey: Map<ShoppingStoreKey, PracticalShoppingProductionStoreScope>,
        storeEvaluationsByKey: Map<ShoppingStoreKey, PracticalShoppingProductionStoreEvaluation>,
        usablePricesByStoreItem: Map<Pair<ShoppingStoreKey, ShoppingItemKey>, PracticalShoppingProductionPriceEvaluation>
    ): PracticalShoppingProductionPairEvaluation {
        val blockers = linkedSetOf<PracticalShoppingProductionPairBlocker>()
        val baseStore = storesByKey[pair.baseStoreKey]
        val addedStore = storesByKey[pair.addedStoreKey]

        if (baseStore == null) {
            blockers += PracticalShoppingProductionPairBlocker.BASE_STORE_NOT_DECLARED
        }
        if (addedStore == null) {
            blockers += PracticalShoppingProductionPairBlocker.ADDED_STORE_NOT_DECLARED
        }
        if (baseStore != null && addedStore != null && sameOfferScope(baseStore, addedStore)) {
            blockers += PracticalShoppingProductionPairBlocker.SAME_OFFER_SCOPE
        }

        val baseCandidate = storeEvaluationsByKey[pair.baseStoreKey]?.candidate
        if (baseCandidate == null || baseCandidate.coveredItemKeys != request.itemKeySet) {
            blockers += PracticalShoppingProductionPairBlocker.BASE_STORE_NOT_COMPLETE
        }

        if (blockers.isNotEmpty()) {
            return blockedPair(pair, blockers)
        }

        val completeBase = requireNotNull(baseCandidate)
        val baseMoneySpec =
            completeBase.knownBasketCost.currencyCode to
                completeBase.knownBasketCost.fractionDigits

        val selections =
            request.itemKeys.map { itemKey ->
                val basePriceEvaluation =
                    requireNotNull(usablePricesByStoreItem[pair.baseStoreKey to itemKey]) {
                        "Complete base-store candidate is missing its usable item price"
                    }
                val basePrice = requireNotNull(basePriceEvaluation.selectedPrice)
                val addedPriceEvaluation = usablePricesByStoreItem[pair.addedStoreKey to itemKey]
                val addedPrice = addedPriceEvaluation?.selectedPrice

                val useAdded =
                    addedPriceEvaluation != null &&
                        addedPrice != null &&
                        (addedPrice.currencyCode to addedPrice.fractionDigits) == baseMoneySpec &&
                        addedPrice.minorUnits < basePrice.minorUnits

                val selectedEvaluation =
                    if (useAdded) {
                        requireNotNull(addedPriceEvaluation)
                    } else {
                        basePriceEvaluation
                    }
                val selectedPrice = requireNotNull(selectedEvaluation.selectedPrice)

                PracticalShoppingProductionPairItemSelection(
                    itemKey = itemKey,
                    selectedStoreKey = selectedEvaluation.binding.storeKey,
                    currentPriceRequestId = selectedEvaluation.binding.currentPriceRequestId,
                    selectedPrice = selectedPrice,
                    freshness = requireNotNull(selectedEvaluation.freshness)
                )
            }

        if (selections.none { it.selectedStoreKey == pair.addedStoreKey }) {
            return blockedPair(
                pair,
                setOf(
                    PracticalShoppingProductionPairBlocker
                        .ADDED_STORE_DOES_NOT_IMPROVE_BASKET
                )
            )
        }

        val combinedBasketCost =
            try {
                val zero =
                    Money(
                        minorUnits = 0L,
                        currencyCode = completeBase.knownBasketCost.currencyCode,
                        fractionDigits = completeBase.knownBasketCost.fractionDigits
                    )
                selections.fold(zero) { total, selection ->
                    total + selection.selectedPrice
                }
            } catch (overflow: ArithmeticException) {
                return blockedPair(
                    pair,
                    setOf(PracticalShoppingProductionPairBlocker.BASKET_TOTAL_OVERFLOW)
                )
            }

        val evidence =
            ShoppingPlanEvidenceSummary(
                freshItemCount = selections.count { it.freshness == EvidenceFreshness.FRESH },
                agingItemCount = selections.count { it.freshness == EvidenceFreshness.AGING },
                staleItemCount = 0,
                unknownFreshnessItemCount = 0
            )

        return PracticalShoppingProductionPairEvaluation(
            pair = pair,
            itemSelections = selections,
            candidate =
                TwoStorePlanCandidate(
                    baseStoreKey = pair.baseStoreKey,
                    addedStoreKey = pair.addedStoreKey,
                    coveredItemKeys = request.itemKeySet,
                    addedStoreItemKeys =
                        selections
                            .filter { it.selectedStoreKey == pair.addedStoreKey }
                            .mapTo(linkedSetOf()) { it.itemKey },
                    knownCombinedBasketCost = combinedBasketCost,
                    additionalTravel = pair.additionalTravel,
                    evidence = evidence,
                    itemPrices = selections.associate { selection ->
                        selection.itemKey to selection.selectedPrice
                    }
                ),
            blockers = emptySet()
        )
    }

    private fun sameOfferScope(
        left: PracticalShoppingProductionStoreScope,
        right: PracticalShoppingProductionStoreScope
    ): Boolean =
        left.merchantKey == right.merchantKey &&
            left.locationKey == right.locationKey &&
            left.commerceChannelKey == right.commerceChannelKey

    private fun blockedPair(
        pair: PracticalShoppingProductionStorePairScope,
        blockers: Set<PracticalShoppingProductionPairBlocker>
    ): PracticalShoppingProductionPairEvaluation =
        PracticalShoppingProductionPairEvaluation(
            pair = pair,
            itemSelections = emptyList(),
            candidate = null,
            blockers = blockers
        )
}

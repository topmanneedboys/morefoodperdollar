package com.valuepilot.core

private const val MAX_PRACTICAL_SHOPPING_PRODUCTION_STORES = 64
private const val MAX_PRACTICAL_SHOPPING_PRODUCTION_PRICE_BINDINGS = 128
private const val MAX_PRACTICAL_SHOPPING_PRODUCTION_PRICE_REQUESTS = 128

/**
 * Explicit store/offer scope supplied by application orchestration.
 *
 * The bridge performs no geocoding or routing. [travelFromUser] is already-known
 * route metadata, while merchant/location/channel are exact offer-scope facts
 * that a current-price claim must match before it can enter this store basket.
 */
data class PracticalShoppingProductionStoreScope(
    val storeKey: ShoppingStoreKey,
    val merchantKey: String,
    val locationKey: String?,
    val commerceChannelKey: String,
    val travelFromUser: ShoppingTravel
) {
    init {
        require(merchantKey.isNotBlank() && merchantKey.length <= 240)
        require(locationKey == null || (locationKey.isNotBlank() && locationKey.length <= 240))
        require(commerceChannelKey.isNotBlank() && commerceChannelKey.length <= 160)
    }
}

/** Exact store/offer scope for price validation without route data. */
data class PracticalShoppingProductionPriceStoreScope(
    val storeKey: ShoppingStoreKey,
    val merchantKey: String,
    val locationKey: String?,
    val commerceChannelKey: String
) {
    init {
        require(merchantKey.isNotBlank() && merchantKey.length <= 240)
        require(locationKey == null || (locationKey.isNotBlank() && locationKey.length <= 240))
        require(commerceChannelKey.isNotBlank() && commerceChannelKey.length <= 160)
    }
}

/**
 * Explicit binding from one requested shopping intent to one exact production
 * product identity and one exact current-price request at one declared store.
 *
 * Product/category resolution happens upstream. This bridge never matches by
 * title, description, image, price similarity or retailer preference.
 */
data class PracticalShoppingProductionPriceBinding(
    val itemKey: ShoppingItemKey,
    val productKey: ProductionProductEvidenceKey,
    val storeKey: ShoppingStoreKey,
    val currentPriceRequestId: String
) {
    init {
        require(currentPriceRequestId.isNotBlank() && currentPriceRequestId.length <= 200)
    }
}

enum class PracticalShoppingProductionPriceBlocker {
    ITEM_NOT_REQUESTED,
    STORE_NOT_DECLARED,
    CURRENT_PRICE_REQUEST_MISSING,
    CURRENT_PRICE_NOT_ELIGIBLE,
    PRODUCT_SCOPE_MISMATCH,
    MERCHANT_SCOPE_MISMATCH,
    LOCATION_SCOPE_MISMATCH,
    COMMERCE_CHANNEL_SCOPE_MISMATCH,
    CURRENCY_SCOPE_MISMATCH,
    UNSUPPORTED_ELIGIBLE_FRESHNESS
}

data class PracticalShoppingProductionPriceEvaluation(
    val binding: PracticalShoppingProductionPriceBinding,
    val selectedPrice: Money?,
    val freshness: EvidenceFreshness?,
    val upstreamBlockers: Set<ProductionCurrentPriceEligibilityBlocker>,
    val blockers: Set<PracticalShoppingProductionPriceBlocker>
) {
    val usable: Boolean
        get() = blockers.isEmpty() && upstreamBlockers.isEmpty()

    init {
        require((selectedPrice != null) == usable)
        require((freshness != null) == usable)
        selectedPrice?.let { require(it.minorUnits > 0L) }
    }
}

enum class PracticalShoppingProductionStoreBlocker {
    NO_USABLE_PRICES,
    MIXED_MONEY_SPEC,
    BASKET_TOTAL_OVERFLOW
}

data class PracticalShoppingProductionStoreEvaluation(
    val store: PracticalShoppingProductionStoreScope,
    val candidate: SingleStorePlanCandidate?,
    val blockers: Set<PracticalShoppingProductionStoreBlocker>
) {
    init {
        require((candidate != null) == blockers.isEmpty())
        candidate?.let { require(it.storeKey == store.storeKey) }
    }
}

data class PracticalShoppingProductionCandidateBridgeResult(
    val priceEvaluations: List<PracticalShoppingProductionPriceEvaluation>,
    val storeEvaluations: List<PracticalShoppingProductionStoreEvaluation>
) {
    val singleStoreCandidates: List<SingleStorePlanCandidate>
        get() = storeEvaluations.mapNotNull { it.candidate }
}

/**
 * Deterministic production-evidence -> one-store-candidate bridge.
 *
 * This is deliberately NOT another ranking engine. Every raw current-price
 * request is re-established once for this bridge invocation at the supplied
 * decision instant. Each explicitly bound item/store price then consumes the
 * candidate-specific result derived from that same immutable in-call evaluation
 * set and verifies exact product + merchant + location + channel scope.
 *
 * The batch is only an execution optimization. It is never persisted and never
 * weakens lifecycle, disposition, authorization, freshness or conflict checks.
 * Blocked/missing prices remain missing.
 *
 * The bridge only constructs exact [SingleStorePlanCandidate] inputs. It does not
 * choose a store, build split-store plans, infer substitutions, own a
 * clock/network/location service, or invent an unknown price. One-store ranking
 * and second-stop decisions remain exclusively in [PracticalShoppingPlanner].
 */
object PracticalShoppingProductionCandidateBridge {

    fun evaluate(
        request: ShoppingRequest,
        stores: List<PracticalShoppingProductionStoreScope>,
        priceBindings: List<PracticalShoppingProductionPriceBinding>,
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy
    ): PracticalShoppingProductionCandidateBridgeResult {
        require(stores.size <= MAX_PRACTICAL_SHOPPING_PRODUCTION_STORES)
        require(priceBindings.size <= MAX_PRACTICAL_SHOPPING_PRODUCTION_PRICE_BINDINGS)
        require(priceRequests.size <= MAX_PRACTICAL_SHOPPING_PRODUCTION_PRICE_REQUESTS)
        require(evaluatedAtEpochMillis > 0L)

        val storeKeys = stores.map { it.storeKey }
        require(storeKeys.size == storeKeys.toSet().size) {
            "Practical Shopping production store keys must be unique"
        }

        val requestIds = priceRequests.map { it.requestId }
        require(requestIds.size == requestIds.toSet().size) {
            "Practical Shopping production current-price request ids must be unique"
        }

        val itemStoreKeys = priceBindings.map { it.itemKey to it.storeKey }
        require(itemStoreKeys.size == itemStoreKeys.toSet().size) {
            "Each shopping item may have at most one bound price per store"
        }

        val boundRequestIds = priceBindings.map { it.currentPriceRequestId }
        require(boundRequestIds.size == boundRequestIds.toSet().size) {
            "One current-price request cannot be reused for multiple shopping bindings"
        }

        val storeProductKeys = priceBindings.map { it.storeKey to it.productKey }
        require(storeProductKeys.size == storeProductKeys.toSet().size) {
            "One exact product cannot be counted twice in the same store basket"
        }

        val priceEvaluations =
            evaluatePrices(
                request = request,
                stores =
                    stores.map { store ->
                        PracticalShoppingProductionPriceStoreScope(
                            storeKey = store.storeKey,
                            merchantKey = store.merchantKey,
                            locationKey = store.locationKey,
                            commerceChannelKey = store.commerceChannelKey
                        )
                    },
                priceBindings = priceBindings,
                priceRequests = priceRequests,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                acceptancePolicy = acceptancePolicy
            )

        val storeEvaluations =
            stores
                .sortedBy { it.storeKey.value }
                .map { store ->
                    buildStoreCandidate(
                        store = store,
                        priceEvaluations = priceEvaluations
                    )
                }

        return PracticalShoppingProductionCandidateBridgeResult(
            priceEvaluations = priceEvaluations,
            storeEvaluations = storeEvaluations
        )
    }

    fun evaluatePrices(
        request: ShoppingRequest,
        stores: List<PracticalShoppingProductionPriceStoreScope>,
        priceBindings: List<PracticalShoppingProductionPriceBinding>,
        priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        evaluatedAtEpochMillis: Long,
        acceptancePolicy: EvidenceAcceptancePolicy
    ): List<PracticalShoppingProductionPriceEvaluation> {
        require(stores.size <= MAX_PRACTICAL_SHOPPING_PRODUCTION_STORES)
        require(priceBindings.size <= MAX_PRACTICAL_SHOPPING_PRODUCTION_PRICE_BINDINGS)
        require(priceRequests.size <= MAX_PRACTICAL_SHOPPING_PRODUCTION_PRICE_REQUESTS)
        require(evaluatedAtEpochMillis > 0L)

        val storeKeys = stores.map { it.storeKey }
        require(storeKeys.size == storeKeys.toSet().size) {
            "Practical Shopping production store keys must be unique"
        }

        val requestIds = priceRequests.map { it.requestId }
        require(requestIds.size == requestIds.toSet().size) {
            "Practical Shopping production current-price request ids must be unique"
        }

        val itemStoreKeys = priceBindings.map { it.itemKey to it.storeKey }
        require(itemStoreKeys.size == itemStoreKeys.toSet().size) {
            "Each shopping item may have at most one bound price per store"
        }

        val boundRequestIds = priceBindings.map { it.currentPriceRequestId }
        require(boundRequestIds.size == boundRequestIds.toSet().size) {
            "One current-price request cannot be reused for multiple shopping bindings"
        }

        val storeProductKeys = priceBindings.map { it.storeKey to it.productKey }
        require(storeProductKeys.size == storeProductKeys.toSet().size) {
            "One exact product cannot be counted twice in the same store basket"
        }

        val storesByKey = stores.associateBy { it.storeKey }
        val requestsById = priceRequests.associateBy { it.requestId }
        val requestedItems = request.itemKeySet
        val eligibilityByRequestId =
            if (priceRequests.isEmpty()) {
                emptyMap()
            } else {
                ProductionCurrentPriceEligibilityEvaluator.evaluateAll(
                    requests = priceRequests,
                    lifecycleRegistry = lifecycleRegistry,
                    dispositionRegistry = dispositionRegistry,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    acceptancePolicy = acceptancePolicy
                )
            }

        return priceBindings
            .sortedWith(
                compareBy<PracticalShoppingProductionPriceBinding>(
                    { it.storeKey.value },
                    { it.itemKey.value },
                    { it.currentPriceRequestId }
                )
            )
            .map { binding ->
                evaluatePriceBinding(
                    binding = binding,
                    requestedItems = requestedItems,
                    storesByKey = storesByKey,
                    requestsById = requestsById,
                    eligibilityByRequestId = eligibilityByRequestId
                )
            }
    }

    private fun evaluatePriceBinding(
        binding: PracticalShoppingProductionPriceBinding,
        requestedItems: Set<ShoppingItemKey>,
        storesByKey: Map<ShoppingStoreKey, PracticalShoppingProductionPriceStoreScope>,
        requestsById: Map<String, ProductionCurrentPriceEligibilityRequest>,
        eligibilityByRequestId: Map<String, ProductionCurrentPriceEligibilityResult>
    ): PracticalShoppingProductionPriceEvaluation {
        val blockers = linkedSetOf<PracticalShoppingProductionPriceBlocker>()

        if (binding.itemKey !in requestedItems) {
            blockers += PracticalShoppingProductionPriceBlocker.ITEM_NOT_REQUESTED
        }

        val store = storesByKey[binding.storeKey]
        if (store == null) {
            blockers += PracticalShoppingProductionPriceBlocker.STORE_NOT_DECLARED
        }

        val priceRequest = requestsById[binding.currentPriceRequestId]
        if (priceRequest == null) {
            blockers += PracticalShoppingProductionPriceBlocker.CURRENT_PRICE_REQUEST_MISSING
        }

        if (blockers.isNotEmpty()) {
            return blockedPrice(binding, blockers = blockers)
        }

        val eligibility =
            requireNotNull(eligibilityByRequestId[requireNotNull(priceRequest).requestId]) {
                "Current-price batch is missing an evaluated request"
            }

        if (!eligibility.eligibleForCurrentPriceStage) {
            blockers += PracticalShoppingProductionPriceBlocker.CURRENT_PRICE_NOT_ELIGIBLE
            return blockedPrice(
                binding = binding,
                upstreamBlockers = eligibility.blockers,
                blockers = blockers
            )
        }

        val evidence = requireNotNull(eligibility.eligibleEvidence)
        val scope = evidence.claim.scope
        val declaredStore = requireNotNull(store)

        if (evidence.productKey != binding.productKey) {
            blockers += PracticalShoppingProductionPriceBlocker.PRODUCT_SCOPE_MISMATCH
        }
        if (scope.merchantKey != declaredStore.merchantKey) {
            blockers += PracticalShoppingProductionPriceBlocker.MERCHANT_SCOPE_MISMATCH
        }
        if (scope.locationKey != declaredStore.locationKey) {
            blockers += PracticalShoppingProductionPriceBlocker.LOCATION_SCOPE_MISMATCH
        }
        if (scope.commerceChannelKey != declaredStore.commerceChannelKey) {
            blockers += PracticalShoppingProductionPriceBlocker.COMMERCE_CHANNEL_SCOPE_MISMATCH
        }

        val candidateAcceptance =
            requireNotNull(eligibility.candidateEvaluation?.acceptanceResult)
        val acceptanceDecision = requireNotNull(candidateAcceptance.acceptanceDecision)
        val view =
            requireNotNull(
                candidateAcceptance
                    .claimDecision
                    .productionViewDecision
                    .view
            )
        val currentPrice = view.currentPrice

        if (scope.currencyCode != currentPrice.currencyCode) {
            blockers += PracticalShoppingProductionPriceBlocker.CURRENCY_SCOPE_MISMATCH
        }

        if (
            acceptanceDecision.freshness != EvidenceFreshness.FRESH &&
            acceptanceDecision.freshness != EvidenceFreshness.AGING
        ) {
            blockers += PracticalShoppingProductionPriceBlocker.UNSUPPORTED_ELIGIBLE_FRESHNESS
        }

        if (blockers.isNotEmpty()) {
            return blockedPrice(binding = binding, blockers = blockers)
        }

        return PracticalShoppingProductionPriceEvaluation(
            binding = binding,
            selectedPrice = currentPrice,
            freshness = acceptanceDecision.freshness,
            upstreamBlockers = emptySet(),
            blockers = emptySet()
        )
    }

    private fun blockedPrice(
        binding: PracticalShoppingProductionPriceBinding,
        upstreamBlockers: Set<ProductionCurrentPriceEligibilityBlocker> = emptySet(),
        blockers: Set<PracticalShoppingProductionPriceBlocker>
    ): PracticalShoppingProductionPriceEvaluation =
        PracticalShoppingProductionPriceEvaluation(
            binding = binding,
            selectedPrice = null,
            freshness = null,
            upstreamBlockers = upstreamBlockers,
            blockers = blockers
        )

    private fun buildStoreCandidate(
        store: PracticalShoppingProductionStoreScope,
        priceEvaluations: List<PracticalShoppingProductionPriceEvaluation>
    ): PracticalShoppingProductionStoreEvaluation {
        val usable =
            priceEvaluations.filter {
                it.usable && it.binding.storeKey == store.storeKey
            }

        if (usable.isEmpty()) {
            return PracticalShoppingProductionStoreEvaluation(
                store = store,
                candidate = null,
                blockers = setOf(PracticalShoppingProductionStoreBlocker.NO_USABLE_PRICES)
            )
        }

        val selectedPrices = usable.map { requireNotNull(it.selectedPrice) }
        val moneySpecs =
            selectedPrices
                .map { it.currencyCode to it.fractionDigits }
                .toSet()

        if (moneySpecs.size != 1) {
            return PracticalShoppingProductionStoreEvaluation(
                store = store,
                candidate = null,
                blockers = setOf(PracticalShoppingProductionStoreBlocker.MIXED_MONEY_SPEC)
            )
        }

        val basketTotal =
            try {
                selectedPrices.drop(1).fold(selectedPrices.first()) { total, price ->
                    total + price
                }
            } catch (overflow: ArithmeticException) {
                return PracticalShoppingProductionStoreEvaluation(
                    store = store,
                    candidate = null,
                    blockers = setOf(PracticalShoppingProductionStoreBlocker.BASKET_TOTAL_OVERFLOW)
                )
            }

        val coveredItemKeys =
            usable.mapTo(linkedSetOf()) { it.binding.itemKey }
        val evidence =
            ShoppingPlanEvidenceSummary(
                freshItemCount = usable.count { it.freshness == EvidenceFreshness.FRESH },
                agingItemCount = usable.count { it.freshness == EvidenceFreshness.AGING },
                staleItemCount = 0,
                unknownFreshnessItemCount = 0
            )

        return PracticalShoppingProductionStoreEvaluation(
            store = store,
            candidate =
                SingleStorePlanCandidate(
                    storeKey = store.storeKey,
                    coveredItemKeys = coveredItemKeys,
                    knownBasketCost = basketTotal,
                    travel = store.travelFromUser,
                    evidence = evidence
                ),
            blockers = emptySet()
        )
    }
}

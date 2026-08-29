package com.valuepilot.core

private const val MAX_PRACTICAL_SHOPPING_ASSEMBLY_STORES = 64
private const val MAX_PRACTICAL_SHOPPING_ASSEMBLY_PAIRS = 128
private const val MAX_PRACTICAL_SHOPPING_ASSEMBLY_PRICE_LINKS = 128
private const val MAX_PRACTICAL_SHOPPING_ASSEMBLY_PRICE_REQUESTS = 128

/** Ordered logical store pair before travel has been established. */
data class PracticalShoppingRequestedStorePair(
    val baseStoreKey: ShoppingStoreKey,
    val addedStoreKey: ShoppingStoreKey
) {
    init {
        require(baseStoreKey != addedStoreKey)
    }
}

/**
 * Explicit adapter link from one shopping item/store to one raw current-price request.
 *
 * Product identity is intentionally absent here. It is supplied only after the
 * independent [PracticalShoppingProductIdentityResolver] establishes an automatic
 * product binding for the shopping intent.
 */
data class PracticalShoppingProductionPriceLink(
    val itemKey: ShoppingItemKey,
    val storeKey: ShoppingStoreKey,
    val currentPriceRequestId: String
) {
    init {
        require(currentPriceRequestId.isNotBlank() && currentPriceRequestId.length <= 200)
    }
}

enum class PracticalShoppingProductionPriceLinkGap {
    PRODUCT_IDENTITY_UNRESOLVED,
    STORE_UNAVAILABLE
}

data class PracticalShoppingProductionPriceLinkReadiness(
    val link: PracticalShoppingProductionPriceLink,
    val gaps: Set<PracticalShoppingProductionPriceLinkGap>
) {
    init {
        require(gaps.isNotEmpty())
    }
}

data class PracticalShoppingProductionAssemblyReadiness(
    val unresolvedProductItems: Set<ShoppingItemKey>,
    val unresolvedStoreIdentities: Set<ShoppingStoreKey>,
    val unresolvedStoreTravel: Set<ShoppingStoreKey>,
    val unavailableStorePairs: Set<PracticalShoppingRequestedStorePair>,
    val unresolvedPairTravel: Set<PracticalShoppingRequestedStorePair>,
    val skippedPriceLinks: List<PracticalShoppingProductionPriceLinkReadiness>
) {
    val complete: Boolean
        get() =
            unresolvedProductItems.isEmpty() &&
                unresolvedStoreIdentities.isEmpty() &&
                unresolvedStoreTravel.isEmpty() &&
                unavailableStorePairs.isEmpty() &&
                unresolvedPairTravel.isEmpty() &&
                skippedPriceLinks.isEmpty()
}

data class PracticalShoppingProductionAssemblyRequest(
    val shoppingRequest: ShoppingRequest,
    val storeKeys: List<ShoppingStoreKey>,
    val requestedStorePairs: List<PracticalShoppingRequestedStorePair>,
    val priceLinks: List<PracticalShoppingProductionPriceLink>,
    val productIdentityCandidates: List<PracticalShoppingProductIdentityCandidate>,
    val storeIdentityCandidates: List<PracticalShoppingStoreIdentityCandidate>,
    val travelContext: PracticalShoppingTravelContext,
    val travelCandidates: List<PracticalShoppingTravelCandidate>,
    val travelFreshnessPolicy: EvidenceFreshnessPolicy,
    val priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
    val evaluatedAtEpochMillis: Long,
    val acceptancePolicy: EvidenceAcceptancePolicy,
    val planningPolicy: PracticalShoppingPolicy
) {
    init {
        require(evaluatedAtEpochMillis > 0L)
        require(storeKeys.size <= MAX_PRACTICAL_SHOPPING_ASSEMBLY_STORES)
        require(storeKeys.distinct().size == storeKeys.size) {
            "Practical Shopping assembly store keys must be unique"
        }
        require(requestedStorePairs.size <= MAX_PRACTICAL_SHOPPING_ASSEMBLY_PAIRS)
        require(requestedStorePairs.distinct().size == requestedStorePairs.size) {
            "Practical Shopping assembly store pairs must be unique"
        }
        val targetStores = storeKeys.toSet()
        requestedStorePairs.forEach { pair ->
            require(pair.baseStoreKey in targetStores && pair.addedStoreKey in targetStores) {
                "Practical Shopping assembly pairs must reference target stores"
            }
        }

        require(priceLinks.size <= MAX_PRACTICAL_SHOPPING_ASSEMBLY_PRICE_LINKS)
        val itemStoreLinks = priceLinks.map { it.itemKey to it.storeKey }
        require(itemStoreLinks.distinct().size == itemStoreLinks.size) {
            "Practical Shopping assembly permits one price link per item/store"
        }
        val boundRequestIds = priceLinks.map { it.currentPriceRequestId }
        require(boundRequestIds.distinct().size == boundRequestIds.size) {
            "One raw current-price request cannot be linked to multiple shopping bindings"
        }
        val requestedItems = shoppingRequest.itemKeys.toSet()
        priceLinks.forEach { link ->
            require(link.itemKey in requestedItems) {
                "Practical Shopping assembly price link item is not requested"
            }
            require(link.storeKey in targetStores) {
                "Practical Shopping assembly price link store is not targeted"
            }
        }

        require(priceRequests.size <= MAX_PRACTICAL_SHOPPING_ASSEMBLY_PRICE_REQUESTS)
        val rawRequestIds = priceRequests.map { it.requestId }
        require(rawRequestIds.distinct().size == rawRequestIds.size) {
            "Practical Shopping assembly raw price-request ids must be unique"
        }
        val suppliedRawRequestIds = rawRequestIds.toSet()
        priceLinks.forEach { link ->
            require(link.currentPriceRequestId in suppliedRawRequestIds) {
                "Practical Shopping assembly price link must reference a supplied raw request"
            }
        }
    }
}

data class PracticalShoppingProductionAssemblyResult(
    val productIdentityResolution: PracticalShoppingProductIdentityResolutionResult,
    val storeIdentityResolution: PracticalShoppingStoreIdentityResolutionResult,
    val travelResolution: PracticalShoppingTravelResolutionResult,
    val readiness: PracticalShoppingProductionAssemblyReadiness,
    val orchestrationRequest: PracticalShoppingProductionOrchestrationRequest
)

/**
 * Deterministic composition boundary from independently established adapter facts into
 * the verified production Practical Shopping orchestration request.
 *
 * This assembler deliberately allows truthful partial coverage. An unresolved product
 * identity, store identity, or route does not become a guess: the affected binding/store/
 * pair is omitted and reported in [PracticalShoppingProductionAssemblyReadiness]. Raw
 * current-price requests are preserved even when unbound so downstream same-product
 * conflict resolution still sees relevant contradictory evidence.
 *
 * The assembler never derives product identity from a price row, never derives merchant
 * identity from a geocoder/route result, never invents zero travel, never selects among
 * conflicting identity/route candidates, and never ranks stores. The returned request is
 * validated with [PracticalShoppingProductionOrchestrator] before it is exposed.
 */
object PracticalShoppingProductionAssembler {

    fun assemble(
        request: PracticalShoppingProductionAssemblyRequest
    ): PracticalShoppingProductionAssemblyResult {
        val productResolution =
            PracticalShoppingProductIdentityResolver.resolve(
                request = request.shoppingRequest,
                candidates = request.productIdentityCandidates
            )

        val storeResolution =
            PracticalShoppingStoreIdentityResolver.resolve(
                storeKeys = request.storeKeys,
                candidates = request.storeIdentityCandidates
            )

        val travelLegs =
            buildList {
                request.storeKeys.forEach { storeKey ->
                    add(PracticalShoppingTravelLeg(baseStoreKey = null, destinationStoreKey = storeKey))
                }
                request.requestedStorePairs.forEach { pair ->
                    add(
                        PracticalShoppingTravelLeg(
                            baseStoreKey = pair.baseStoreKey,
                            destinationStoreKey = pair.addedStoreKey
                        )
                    )
                }
            }

        val travelResolution =
            PracticalShoppingTravelResolver.resolve(
                context = request.travelContext,
                legs = travelLegs,
                candidates = request.travelCandidates,
                evaluatedAtEpochMillis = request.evaluatedAtEpochMillis,
                freshnessPolicy = request.travelFreshnessPolicy
            )

        val automaticStoreScopes = storeResolution.automaticScopes
        val automaticTravel = travelResolution.automaticTravel

        val unresolvedStoreIdentities =
            request.storeKeys
                .filterTo(linkedSetOf()) { it !in automaticStoreScopes }

        val unresolvedStoreTravel = linkedSetOf<ShoppingStoreKey>()
        val stores =
            request.storeKeys.mapNotNull { storeKey ->
                val identity = automaticStoreScopes[storeKey] ?: return@mapNotNull null
                val travel =
                    automaticTravel[
                        PracticalShoppingTravelLeg(
                            baseStoreKey = null,
                            destinationStoreKey = storeKey
                        )
                    ]
                if (travel == null) {
                    unresolvedStoreTravel += storeKey
                    return@mapNotNull null
                }

                PracticalShoppingProductionStoreScope(
                    storeKey = storeKey,
                    merchantKey = identity.merchantKey,
                    locationKey = identity.locationKey,
                    commerceChannelKey = identity.commerceChannelKey,
                    travelFromUser = travel
                )
            }

        val assembledStoreKeys = stores.mapTo(linkedSetOf()) { it.storeKey }
        val unavailablePairs = linkedSetOf<PracticalShoppingRequestedStorePair>()
        val unresolvedPairTravel = linkedSetOf<PracticalShoppingRequestedStorePair>()
        val storePairs =
            request.requestedStorePairs.mapNotNull { pair ->
                if (
                    pair.baseStoreKey !in assembledStoreKeys ||
                    pair.addedStoreKey !in assembledStoreKeys
                ) {
                    unavailablePairs += pair
                    return@mapNotNull null
                }

                val travel =
                    automaticTravel[
                        PracticalShoppingTravelLeg(
                            baseStoreKey = pair.baseStoreKey,
                            destinationStoreKey = pair.addedStoreKey
                        )
                    ]
                if (travel == null) {
                    unresolvedPairTravel += pair
                    unavailablePairs += pair
                    return@mapNotNull null
                }

                PracticalShoppingProductionStorePairScope(
                    baseStoreKey = pair.baseStoreKey,
                    addedStoreKey = pair.addedStoreKey,
                    additionalTravel = travel
                )
            }

        val automaticProducts = productResolution.automaticBindings
        val unresolvedProductItems =
            request.shoppingRequest.itemKeys
                .filterTo(linkedSetOf()) { it !in automaticProducts }

        val skippedPriceLinks = mutableListOf<PracticalShoppingProductionPriceLinkReadiness>()
        val priceBindings =
            request.priceLinks.mapNotNull { link ->
                val gaps = linkedSetOf<PracticalShoppingProductionPriceLinkGap>()
                val productKey = automaticProducts[link.itemKey]
                if (productKey == null) {
                    gaps += PracticalShoppingProductionPriceLinkGap.PRODUCT_IDENTITY_UNRESOLVED
                }
                if (link.storeKey !in assembledStoreKeys) {
                    gaps += PracticalShoppingProductionPriceLinkGap.STORE_UNAVAILABLE
                }

                if (gaps.isNotEmpty()) {
                    skippedPriceLinks +=
                        PracticalShoppingProductionPriceLinkReadiness(
                            link = link,
                            gaps = gaps
                        )
                    return@mapNotNull null
                }

                PracticalShoppingProductionPriceBinding(
                    itemKey = link.itemKey,
                    productKey = requireNotNull(productKey),
                    storeKey = link.storeKey,
                    currentPriceRequestId = link.currentPriceRequestId
                )
            }

        val orchestrationRequest =
            PracticalShoppingProductionOrchestrationRequest(
                shoppingRequest = request.shoppingRequest,
                stores = stores,
                storePairs = storePairs,
                priceBindings = priceBindings,
                priceRequests = request.priceRequests,
                evaluatedAtEpochMillis = request.evaluatedAtEpochMillis,
                acceptancePolicy = request.acceptancePolicy,
                planningPolicy = request.planningPolicy
            )

        val finalValidation =
            PracticalShoppingProductionOrchestrator.validate(orchestrationRequest)
        require(finalValidation.valid) {
            "Resolved Practical Shopping adapter facts produced structurally invalid orchestration: ${finalValidation.issues}"
        }

        return PracticalShoppingProductionAssemblyResult(
            productIdentityResolution = productResolution,
            storeIdentityResolution = storeResolution,
            travelResolution = travelResolution,
            readiness =
                PracticalShoppingProductionAssemblyReadiness(
                    unresolvedProductItems = unresolvedProductItems,
                    unresolvedStoreIdentities = unresolvedStoreIdentities,
                    unresolvedStoreTravel = unresolvedStoreTravel,
                    unavailableStorePairs = unavailablePairs,
                    unresolvedPairTravel = unresolvedPairTravel,
                    skippedPriceLinks = skippedPriceLinks
                ),
            orchestrationRequest = orchestrationRequest
        )
    }
}

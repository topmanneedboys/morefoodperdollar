package com.valuepilot.app

import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingProductionCandidateBridge
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ProductionCurrentPriceEligibilityRequest
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

/** Price coverage for one requested staple at one resolved alternative logical store. */
data class StapleWatchAlternativeBasketItemPriceFact(
    val itemKey: ShoppingItemKey,
    val state: StapleWatchBasketItemPriceState,
    val exactPrice: Money?
) {
    init {
        when (state) {
            StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE -> {
                require(exactPrice != null && exactPrice.minorUnits > 0L)
            }
            StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE,
            StapleWatchBasketItemPriceState.PRODUCTION_PRICE_BLOCKED -> {
                require(exactPrice == null)
            }
        }
    }
}

/** Accounted per-item price coverage for one exact resolved alternative store. */
data class StapleWatchAlternativeStoreBasketPriceFact(
    val storeKey: ShoppingStoreKey,
    val itemPrices: List<StapleWatchAlternativeBasketItemPriceFact>
)

/**
 * Accounted basket-price facts for the exact resolved alternative logical stores of one watch intent.
 *
 * Instances can only be minted by [resolve]. The factory requires an exact production price scope
 * for every resolved alternative identity and re-runs the permanent production current-price rail.
 * There is exactly one item outcome per requested staple for every alternative store, preserving
 * stable store order and original request order. Missing and blocked prices remain explicit gaps.
 *
 * Exact production scopes, bindings, and raw requests are retained only as internal provenance so a
 * later currentness resolver can re-evaluate the same evidence rather than attach metadata from a
 * different price. This contract still exposes no route, evidence-currentness result, economics,
 * persistence, scheduling, or notification authority. Empty [alternatives] is a valid explicit
 * result when identity resolution found no alternative stores.
 */
class StapleWatchAlternativeStoreBasketPriceFacts private constructor(
    val identityFacts: StapleWatchAlternativeStoreIdentityFacts,
    val alternatives: List<StapleWatchAlternativeStoreBasketPriceFact>,
    internal val productionStoreScopes: List<PracticalShoppingProductionPriceStoreScope>,
    internal val productionPriceBindings: List<PracticalShoppingProductionPriceBinding>,
    internal val productionPriceRequests: List<ProductionCurrentPriceEligibilityRequest>
) {
    init {
        val expectedStoreKeys = identityFacts.alternativeStoreKeys
        require(alternatives.map { fact -> fact.storeKey } == expectedStoreKeys) {
            "Staple-watch alternative price facts must exactly cover resolved stores in stable order"
        }
        alternatives.forEach { storeFact ->
            require(storeFact.itemPrices.map { fact -> fact.itemKey } == intent.request.itemKeys) {
                "Staple-watch alternative store prices must exactly cover requested items in request order"
            }
        }
        require(productionStoreScopes.map { store -> store.storeKey } == expectedStoreKeys) {
            "Staple-watch alternative provenance scopes must match resolved stores in stable order"
        }

        val boundStoreItems =
            alternatives.flatMap { storeFact ->
                storeFact.itemPrices
                    .filter { fact ->
                        fact.state != StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE
                    }
                    .map { fact -> storeFact.storeKey to fact.itemKey }
            }
        require(
            productionPriceBindings.map { binding -> binding.storeKey to binding.itemKey } ==
                boundStoreItems
        ) {
            "Staple-watch alternative provenance bindings must match bound facts in stable order"
        }
        require(
            productionPriceRequests.map { request -> request.requestId } ==
                productionPriceBindings.map { binding -> binding.currentPriceRequestId }
        ) {
            "Staple-watch alternative provenance requests must align with canonical bindings"
        }
    }

    val intent: StapleWatchFactCheckIntent
        get() = identityFacts.intent

    val resolvedRequirement: StapleWatchFactResolutionRequirement
        get() = StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE

    companion object {
        fun resolve(
            identityFacts: StapleWatchAlternativeStoreIdentityFacts,
            stores: List<PracticalShoppingProductionPriceStoreScope>,
            priceBindings: List<PracticalShoppingProductionPriceBinding>,
            priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
            lifecycleRegistry: ProductionDatasetLifecycleRegistry,
            dispositionRegistry: ProductionDatasetDispositionRegistry,
            evaluatedAtEpochMillis: Long,
            acceptancePolicy: EvidenceAcceptancePolicy
        ): StapleWatchAlternativeStoreBasketPriceFacts {
            val expectedStoreKeys = identityFacts.alternativeStoreKeys
            val storesByKey = stores.associateBy { store -> store.storeKey }
            require(storesByKey.size == stores.size) {
                "Staple-watch alternative price store scopes must use unique logical store keys"
            }
            require(storesByKey.keys == expectedStoreKeys.toSet()) {
                "Staple-watch alternative price scopes must exactly cover resolved alternative stores"
            }

            val requestedItemKeys = identityFacts.intent.request.itemKeys.toSet()
            val expectedStoreKeySet = expectedStoreKeys.toSet()
            require(
                priceBindings.all { binding ->
                    binding.storeKey in expectedStoreKeySet && binding.itemKey in requestedItemKeys
                }
            ) {
                "Staple-watch alternative price bindings must target only resolved stores and requested items"
            }

            val boundRequestIds = priceBindings.map { binding -> binding.currentPriceRequestId }.toSet()
            require(priceRequests.map { request -> request.requestId }.toSet() == boundRequestIds) {
                "Staple-watch alternative price requests must exactly match supplied bindings"
            }

            val orderedStores =
                expectedStoreKeys.map { storeKey ->
                    requireNotNull(storesByKey[storeKey]) {
                        "Resolved alternative store is missing its production price scope"
                    }
                }
            val evaluations =
                PracticalShoppingProductionCandidateBridge.evaluatePrices(
                    request = identityFacts.intent.request,
                    stores = orderedStores,
                    priceBindings = priceBindings,
                    priceRequests = priceRequests,
                    lifecycleRegistry = lifecycleRegistry,
                    dispositionRegistry = dispositionRegistry,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    acceptancePolicy = acceptancePolicy
                )
            val evaluationByStoreItem =
                evaluations.associateBy { evaluation ->
                    evaluation.binding.storeKey to evaluation.binding.itemKey
                }

            val alternatives =
                expectedStoreKeys.map { storeKey ->
                    StapleWatchAlternativeStoreBasketPriceFact(
                        storeKey = storeKey,
                        itemPrices =
                            identityFacts.intent.request.itemKeys.map { itemKey ->
                                val evaluation = evaluationByStoreItem[storeKey to itemKey]
                                when {
                                    evaluation == null ->
                                        StapleWatchAlternativeBasketItemPriceFact(
                                            itemKey = itemKey,
                                            state = StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE,
                                            exactPrice = null
                                        )
                                    evaluation.usable ->
                                        StapleWatchAlternativeBasketItemPriceFact(
                                            itemKey = itemKey,
                                            state = StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE,
                                            exactPrice = requireNotNull(evaluation.selectedPrice)
                                        )
                                    else ->
                                        StapleWatchAlternativeBasketItemPriceFact(
                                            itemKey = itemKey,
                                            state = StapleWatchBasketItemPriceState.PRODUCTION_PRICE_BLOCKED,
                                            exactPrice = null
                                        )
                                }
                            }
                    )
                }

            val bindingByStoreItem =
                priceBindings.associateBy { binding -> binding.storeKey to binding.itemKey }
            val requestById = priceRequests.associateBy { request -> request.requestId }
            val canonicalBindings =
                expectedStoreKeys.flatMap { storeKey ->
                    identityFacts.intent.request.itemKeys.mapNotNull { itemKey ->
                        bindingByStoreItem[storeKey to itemKey]
                    }
                }
            val canonicalRequests =
                canonicalBindings.map { binding ->
                    requireNotNull(requestById[binding.currentPriceRequestId]) {
                        "Canonical alternative-store binding is missing its raw production request"
                    }
                }

            return StapleWatchAlternativeStoreBasketPriceFacts(
                identityFacts = identityFacts,
                alternatives = alternatives,
                productionStoreScopes = orderedStores,
                productionPriceBindings = canonicalBindings,
                productionPriceRequests = canonicalRequests
            )
        }
    }
}

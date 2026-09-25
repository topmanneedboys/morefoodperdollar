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

enum class StapleWatchBasketItemPriceState {
    USABLE_EXACT_PRICE,
    NO_BOUND_PRODUCTION_PRICE,
    PRODUCTION_PRICE_BLOCKED
}

/**
 * Price coverage for one requested staple at the user's usual logical store.
 *
 * A missing or blocked production price stays explicit and never receives an invented amount.
 * Currentness is intentionally not exposed here; that remains a separate watch fact category.
 */
data class StapleWatchBasketItemPriceFact(
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

/**
 * Accounted usual-store basket-price facts for one exact staple-watch intent.
 *
 * Instances can only be minted by [resolve] from raw production current-price inputs. The factory
 * re-runs the permanent lifecycle/disposition/authorization/acceptance/conflict/scope validation
 * rail through [PracticalShoppingProductionCandidateBridge.evaluatePrices]. Callers cannot turn a
 * detached price-evaluation DTO into watch facts.
 *
 * There is exactly one item fact for every requested staple, in original request order. Missing
 * bindings and production-blocked prices remain explicit coverage gaps. Exact production scope,
 * bindings, and raw requests are retained only as internal provenance so a later currentness
 * resolver can re-evaluate the same evidence rather than attach metadata from a different price.
 * This contract still exposes no currentness, route, alternative-store, economic, persistence,
 * scheduling or notification authority.
 */
class StapleWatchUsualStoreBasketPriceFacts private constructor(
    val intent: StapleWatchFactCheckIntent,
    val itemPrices: List<StapleWatchBasketItemPriceFact>,
    internal val productionStoreScope: PracticalShoppingProductionPriceStoreScope,
    internal val productionPriceBindings: List<PracticalShoppingProductionPriceBinding>,
    internal val productionPriceRequests: List<ProductionCurrentPriceEligibilityRequest>
) {
    init {
        require(itemPrices.map { it.itemKey } == intent.request.itemKeys) {
            "Staple-watch usual-store prices must exactly cover requested items in request order"
        }
        require(productionStoreScope.storeKey == intent.usualStoreKey) {
            "Staple-watch usual-store provenance scope must match the intent usual store"
        }

        val boundItemKeys =
            itemPrices
                .filter { fact -> fact.state != StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE }
                .map { fact -> fact.itemKey }
        require(productionPriceBindings.map { binding -> binding.itemKey } == boundItemKeys) {
            "Staple-watch usual-store provenance bindings must match bound item facts in request order"
        }
        require(productionPriceBindings.all { binding -> binding.storeKey == intent.usualStoreKey }) {
            "Staple-watch usual-store provenance bindings must remain at the usual store"
        }
        require(
            productionPriceRequests.map { request -> request.requestId } ==
                productionPriceBindings.map { binding -> binding.currentPriceRequestId }
        ) {
            "Staple-watch usual-store provenance requests must align with canonical bindings"
        }
    }

    val resolvedRequirement: StapleWatchFactResolutionRequirement
        get() = StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE

    companion object {
        fun resolve(
            intent: StapleWatchFactCheckIntent,
            store: PracticalShoppingProductionPriceStoreScope,
            priceBindings: List<PracticalShoppingProductionPriceBinding>,
            priceRequests: List<ProductionCurrentPriceEligibilityRequest>,
            lifecycleRegistry: ProductionDatasetLifecycleRegistry,
            dispositionRegistry: ProductionDatasetDispositionRegistry,
            evaluatedAtEpochMillis: Long,
            acceptancePolicy: EvidenceAcceptancePolicy
        ): StapleWatchUsualStoreBasketPriceFacts {
            require(store.storeKey == intent.usualStoreKey) {
                "Staple-watch usual-store price scope must match the intent usual store"
            }

            val requestedItemKeys = intent.request.itemKeys.toSet()
            require(
                priceBindings.all { binding ->
                    binding.storeKey == intent.usualStoreKey && binding.itemKey in requestedItemKeys
                }
            ) {
                "Staple-watch usual-store price bindings must target only requested items at the usual store"
            }

            val boundRequestIds = priceBindings.map { it.currentPriceRequestId }.toSet()
            require(priceRequests.map { it.requestId }.toSet() == boundRequestIds) {
                "Staple-watch usual-store price requests must exactly match the supplied bindings"
            }

            val evaluations =
                PracticalShoppingProductionCandidateBridge.evaluatePrices(
                    request = intent.request,
                    stores = listOf(store),
                    priceBindings = priceBindings,
                    priceRequests = priceRequests,
                    lifecycleRegistry = lifecycleRegistry,
                    dispositionRegistry = dispositionRegistry,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    acceptancePolicy = acceptancePolicy
                )
            val evaluationByItem = evaluations.associateBy { it.binding.itemKey }

            val itemPrices =
                intent.request.itemKeys.map { itemKey ->
                    val evaluation = evaluationByItem[itemKey]
                    when {
                        evaluation == null ->
                            StapleWatchBasketItemPriceFact(
                                itemKey = itemKey,
                                state = StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE,
                                exactPrice = null
                            )
                        evaluation.usable ->
                            StapleWatchBasketItemPriceFact(
                                itemKey = itemKey,
                                state = StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE,
                                exactPrice = requireNotNull(evaluation.selectedPrice)
                            )
                        else ->
                            StapleWatchBasketItemPriceFact(
                                itemKey = itemKey,
                                state = StapleWatchBasketItemPriceState.PRODUCTION_PRICE_BLOCKED,
                                exactPrice = null
                            )
                    }
                }

            val bindingByItem = priceBindings.associateBy { binding -> binding.itemKey }
            val requestById = priceRequests.associateBy { request -> request.requestId }
            val canonicalBindings =
                intent.request.itemKeys.mapNotNull { itemKey -> bindingByItem[itemKey] }
            val canonicalRequests =
                canonicalBindings.map { binding ->
                    requireNotNull(requestById[binding.currentPriceRequestId]) {
                        "Canonical usual-store binding is missing its raw production request"
                    }
                }

            return StapleWatchUsualStoreBasketPriceFacts(
                intent = intent,
                itemPrices = itemPrices,
                productionStoreScope = store,
                productionPriceBindings = canonicalBindings,
                productionPriceRequests = canonicalRequests
            )
        }
    }
}

package com.valuepilot.app

import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.PracticalShoppingProductionCandidateBridge
import com.valuepilot.core.PracticalShoppingProductionPriceEvaluation
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

enum class StapleWatchEvidenceCurrentnessStatus {
    NO_BOUND_PRODUCTION_EVIDENCE,
    PRODUCTION_EVIDENCE_BLOCKED,
    CURRENTNESS_ESTABLISHED
}

/**
 * Point-in-time currentness for one requested staple at one exact logical store.
 *
 * [freshness] exists only when the permanent production price rail currently accepts the exact
 * retained price provenance as usable. That rail permits only FRESH or AGING evidence to reach this
 * state. Missing, stale, unknown, future-dated, unavailable, conflicting, or otherwise blocked
 * production evidence is never upgraded into current Watch evidence here.
 */
data class StapleWatchBasketItemCurrentnessFact(
    val itemKey: ShoppingItemKey,
    val status: StapleWatchEvidenceCurrentnessStatus,
    val freshness: EvidenceFreshness?
) {
    init {
        when (status) {
            StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE,
            StapleWatchEvidenceCurrentnessStatus.PRODUCTION_EVIDENCE_BLOCKED -> {
                require(freshness == null)
            }
            StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED -> {
                require(
                    freshness == EvidenceFreshness.FRESH ||
                        freshness == EvidenceFreshness.AGING
                ) {
                    "Established staple-watch currentness must preserve FRESH or AGING production evidence"
                }
            }
        }
    }
}

/** Exact request-order currentness metadata for one logical store. */
data class StapleWatchStoreEvidenceCurrentnessFact(
    val storeKey: ShoppingStoreKey,
    val itemCurrentness: List<StapleWatchBasketItemCurrentnessFact>
)

/**
 * Authoritative point-in-time currentness metadata for the exact Watch price facts supplied to [resolve].
 *
 * Construction is private. The factory re-runs the permanent production current-price rail from the
 * raw provenance retained by the already-minted usual-store and alternative-store price facts at the
 * caller-supplied evaluation instant and acceptance policy. Detached prior acceptance/currentness
 * decisions are never accepted as authority.
 *
 * The exact source price-fact objects are retained internally so a later deterministic assembler can
 * fail closed if currentness from one price-fact set is paired with different same-intent price facts.
 * This contract does not refresh price values, choose providers, evaluate switch economics, persist
 * state, schedule work, or authorize a notification.
 */
class StapleWatchEvidenceCurrentnessFacts private constructor(
    internal val usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts,
    internal val alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts,
    val usualStore: StapleWatchStoreEvidenceCurrentnessFact,
    val alternatives: List<StapleWatchStoreEvidenceCurrentnessFact>
) {
    init {
        require(usualStorePriceFacts.intent == alternativeStorePriceFacts.intent) {
            "Staple-watch currentness price facts must share one exact fact-check intent"
        }
        require(usualStore.storeKey == intent.usualStoreKey) {
            "Staple-watch currentness usual-store key must match the fact-check intent"
        }
        require(usualStore.itemCurrentness.map { fact -> fact.itemKey } == intent.request.itemKeys) {
            "Staple-watch usual-store currentness must exactly cover requested items in request order"
        }
        require(
            alternatives.map { fact -> fact.storeKey } ==
                alternativeStorePriceFacts.alternatives.map { fact -> fact.storeKey }
        ) {
            "Staple-watch alternative currentness must preserve resolved alternative store order"
        }
        alternatives.forEach { storeFact ->
            require(storeFact.itemCurrentness.map { fact -> fact.itemKey } == intent.request.itemKeys) {
                "Staple-watch alternative currentness must exactly cover requested items in request order"
            }
        }

        require(
            usualStore.itemCurrentness.map { fact ->
                fact.status != StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE
            } ==
                usualStorePriceFacts.itemPrices.map { fact ->
                    fact.state != StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE
                }
        ) {
            "Staple-watch usual-store currentness must preserve exact price-binding coverage"
        }

        alternatives.zip(alternativeStorePriceFacts.alternatives).forEach { (currentness, prices) ->
            require(
                currentness.itemCurrentness.map { fact ->
                    fact.status != StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE
                } ==
                    prices.itemPrices.map { fact ->
                        fact.state != StapleWatchBasketItemPriceState.NO_BOUND_PRODUCTION_PRICE
                    }
            ) {
                "Staple-watch alternative currentness must preserve exact price-binding coverage"
            }
        }
    }

    val intent: StapleWatchFactCheckIntent
        get() = usualStorePriceFacts.intent

    val resolvedRequirement: StapleWatchFactResolutionRequirement
        get() = StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA

    companion object {
        fun resolve(
            usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts,
            alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts,
            lifecycleRegistry: ProductionDatasetLifecycleRegistry,
            dispositionRegistry: ProductionDatasetDispositionRegistry,
            evaluatedAtEpochMillis: Long,
            acceptancePolicy: EvidenceAcceptancePolicy
        ): StapleWatchEvidenceCurrentnessFacts {
            require(usualStorePriceFacts.intent == alternativeStorePriceFacts.intent) {
                "Staple-watch currentness price facts must share one exact fact-check intent"
            }
            require(evaluatedAtEpochMillis > 0L)

            val intent = usualStorePriceFacts.intent
            val usualEvaluations =
                PracticalShoppingProductionCandidateBridge.evaluatePrices(
                    request = intent.request,
                    stores = listOf(usualStorePriceFacts.productionStoreScope),
                    priceBindings = usualStorePriceFacts.productionPriceBindings,
                    priceRequests = usualStorePriceFacts.productionPriceRequests,
                    lifecycleRegistry = lifecycleRegistry,
                    dispositionRegistry = dispositionRegistry,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    acceptancePolicy = acceptancePolicy
                )
            val usualEvaluationByItem =
                usualEvaluations.associateBy { evaluation -> evaluation.binding.itemKey }
            val usualStore =
                StapleWatchStoreEvidenceCurrentnessFact(
                    storeKey = intent.usualStoreKey,
                    itemCurrentness =
                        intent.request.itemKeys.map { itemKey ->
                            currentnessFact(itemKey, usualEvaluationByItem[itemKey])
                        }
                )

            val alternativeEvaluations =
                PracticalShoppingProductionCandidateBridge.evaluatePrices(
                    request = intent.request,
                    stores = alternativeStorePriceFacts.productionStoreScopes,
                    priceBindings = alternativeStorePriceFacts.productionPriceBindings,
                    priceRequests = alternativeStorePriceFacts.productionPriceRequests,
                    lifecycleRegistry = lifecycleRegistry,
                    dispositionRegistry = dispositionRegistry,
                    evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                    acceptancePolicy = acceptancePolicy
                )
            val alternativeEvaluationByStoreItem =
                alternativeEvaluations.associateBy { evaluation ->
                    evaluation.binding.storeKey to evaluation.binding.itemKey
                }
            val alternatives =
                alternativeStorePriceFacts.alternatives.map { storePriceFact ->
                    StapleWatchStoreEvidenceCurrentnessFact(
                        storeKey = storePriceFact.storeKey,
                        itemCurrentness =
                            intent.request.itemKeys.map { itemKey ->
                                currentnessFact(
                                    itemKey = itemKey,
                                    evaluation =
                                        alternativeEvaluationByStoreItem[
                                            storePriceFact.storeKey to itemKey
                                        ]
                                )
                            }
                    )
                }

            return StapleWatchEvidenceCurrentnessFacts(
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                usualStore = usualStore,
                alternatives = alternatives
            )
        }

        private fun currentnessFact(
            itemKey: ShoppingItemKey,
            evaluation: PracticalShoppingProductionPriceEvaluation?
        ): StapleWatchBasketItemCurrentnessFact =
            when {
                evaluation == null ->
                    StapleWatchBasketItemCurrentnessFact(
                        itemKey = itemKey,
                        status =
                            StapleWatchEvidenceCurrentnessStatus.NO_BOUND_PRODUCTION_EVIDENCE,
                        freshness = null
                    )
                !evaluation.usable ->
                    StapleWatchBasketItemCurrentnessFact(
                        itemKey = itemKey,
                        status = StapleWatchEvidenceCurrentnessStatus.PRODUCTION_EVIDENCE_BLOCKED,
                        freshness = null
                    )
                else -> {
                    val freshness = requireNotNull(evaluation.freshness)
                    require(
                        freshness == EvidenceFreshness.FRESH ||
                            freshness == EvidenceFreshness.AGING
                    ) {
                        "Usable production price currentness must be FRESH or AGING"
                    }
                    StapleWatchBasketItemCurrentnessFact(
                        itemKey = itemKey,
                        status = StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED,
                        freshness = freshness
                    )
                }
            }
    }
}

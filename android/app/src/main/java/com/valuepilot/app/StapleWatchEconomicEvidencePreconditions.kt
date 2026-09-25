package com.valuepilot.app

import com.valuepilot.core.ShoppingStoreKey

enum class StapleWatchEconomicEvidencePreconditionIssue {
    USUAL_STORE_PRICE_COVERAGE_INCOMPLETE,
    USUAL_STORE_CURRENTNESS_INCOMPLETE
}

/**
 * Fail-closed evidence preconditions before any Watch economic-input assembly can be attempted.
 *
 * This boundary binds the exact five authoritative fact objects for one fact-check intent. It does
 * not trust the separately forgeable readiness bookkeeping as authority. Exact object identity is
 * required where a downstream fact was minted from an upstream fact so equivalent-looking detached
 * fact sets cannot be mixed.
 *
 * [satisfied] means only that the usual-store basket has a usable exact price fact and established
 * currentness for every requested staple. [priceAndCurrentnessReadyAlternativeStoreKeys] identifies
 * alternatives meeting that same evidence condition in stable identity order. It does not establish
 * basket-money compatibility, sum prices, inspect travel payloads, rank stores, evaluate savings,
 * persist state, schedule work, or authorize a notification.
 */
class StapleWatchEconomicEvidencePreconditions private constructor(
    internal val identityFacts: StapleWatchAlternativeStoreIdentityFacts,
    internal val usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts,
    internal val alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts,
    internal val additionalTravelFacts: StapleWatchAlternativeAdditionalTravelFacts,
    internal val currentnessFacts: StapleWatchEvidenceCurrentnessFacts,
    val issue: StapleWatchEconomicEvidencePreconditionIssue?,
    val priceAndCurrentnessReadyAlternativeStoreKeys: List<ShoppingStoreKey>
) {
    init {
        require(
            priceAndCurrentnessReadyAlternativeStoreKeys.distinct().size ==
                priceAndCurrentnessReadyAlternativeStoreKeys.size
        ) {
            "Staple-watch evidence-ready alternatives must be unique"
        }
        require(
            priceAndCurrentnessReadyAlternativeStoreKeys ==
                identityFacts.alternativeStoreKeys.filter { storeKey ->
                    storeKey in priceAndCurrentnessReadyAlternativeStoreKeys
                }
        ) {
            "Staple-watch evidence-ready alternatives must preserve stable identity order"
        }
        require(issue == null || priceAndCurrentnessReadyAlternativeStoreKeys.isEmpty()) {
            "Blocked staple-watch baseline cannot expose evidence-ready alternatives"
        }
    }

    val intent: StapleWatchFactCheckIntent
        get() = usualStorePriceFacts.intent

    val satisfied: Boolean
        get() = issue == null

    companion object {
        fun evaluate(
            identityFacts: StapleWatchAlternativeStoreIdentityFacts,
            usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts,
            alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts,
            additionalTravelFacts: StapleWatchAlternativeAdditionalTravelFacts,
            currentnessFacts: StapleWatchEvidenceCurrentnessFacts
        ): StapleWatchEconomicEvidencePreconditions {
            val intent = usualStorePriceFacts.intent
            require(identityFacts.intent == intent) {
                "Staple-watch economic evidence must share one exact fact-check intent"
            }
            require(alternativeStorePriceFacts.intent == intent) {
                "Staple-watch alternative prices must match the economic-evidence intent"
            }
            require(additionalTravelFacts.intent == intent) {
                "Staple-watch additional travel must match the economic-evidence intent"
            }
            require(currentnessFacts.intent == intent) {
                "Staple-watch currentness must match the economic-evidence intent"
            }

            require(alternativeStorePriceFacts.identityFacts === identityFacts) {
                "Staple-watch alternative prices must retain the exact identity fact object"
            }
            require(additionalTravelFacts.identityFacts === identityFacts) {
                "Staple-watch additional travel must retain the exact identity fact object"
            }
            require(currentnessFacts.usualStorePriceFacts === usualStorePriceFacts) {
                "Staple-watch currentness must retain the exact usual-store price fact object"
            }
            require(currentnessFacts.alternativeStorePriceFacts === alternativeStorePriceFacts) {
                "Staple-watch currentness must retain the exact alternative price fact object"
            }

            if (
                usualStorePriceFacts.itemPrices.any { fact ->
                    fact.state != StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE
                }
            ) {
                return blocked(
                    identityFacts = identityFacts,
                    usualStorePriceFacts = usualStorePriceFacts,
                    alternativeStorePriceFacts = alternativeStorePriceFacts,
                    additionalTravelFacts = additionalTravelFacts,
                    currentnessFacts = currentnessFacts,
                    issue =
                        StapleWatchEconomicEvidencePreconditionIssue
                            .USUAL_STORE_PRICE_COVERAGE_INCOMPLETE
                )
            }

            if (
                currentnessFacts.usualStore.itemCurrentness.any { fact ->
                    fact.status != StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED
                }
            ) {
                return blocked(
                    identityFacts = identityFacts,
                    usualStorePriceFacts = usualStorePriceFacts,
                    alternativeStorePriceFacts = alternativeStorePriceFacts,
                    additionalTravelFacts = additionalTravelFacts,
                    currentnessFacts = currentnessFacts,
                    issue =
                        StapleWatchEconomicEvidencePreconditionIssue
                            .USUAL_STORE_CURRENTNESS_INCOMPLETE
                )
            }

            val alternativePricesByStore =
                alternativeStorePriceFacts.alternatives.associateBy { fact -> fact.storeKey }
            val alternativeCurrentnessByStore =
                currentnessFacts.alternatives.associateBy { fact -> fact.storeKey }
            val readyAlternatives =
                identityFacts.alternativeStoreKeys.filter { storeKey ->
                    val prices = requireNotNull(alternativePricesByStore[storeKey])
                    val currentness = requireNotNull(alternativeCurrentnessByStore[storeKey])
                    prices.itemPrices.all { fact ->
                        fact.state == StapleWatchBasketItemPriceState.USABLE_EXACT_PRICE
                    } &&
                        currentness.itemCurrentness.all { fact ->
                            fact.status == StapleWatchEvidenceCurrentnessStatus.CURRENTNESS_ESTABLISHED
                        }
                }

            return StapleWatchEconomicEvidencePreconditions(
                identityFacts = identityFacts,
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                additionalTravelFacts = additionalTravelFacts,
                currentnessFacts = currentnessFacts,
                issue = null,
                priceAndCurrentnessReadyAlternativeStoreKeys = readyAlternatives
            )
        }

        private fun blocked(
            identityFacts: StapleWatchAlternativeStoreIdentityFacts,
            usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts,
            alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts,
            additionalTravelFacts: StapleWatchAlternativeAdditionalTravelFacts,
            currentnessFacts: StapleWatchEvidenceCurrentnessFacts,
            issue: StapleWatchEconomicEvidencePreconditionIssue
        ): StapleWatchEconomicEvidencePreconditions =
            StapleWatchEconomicEvidencePreconditions(
                identityFacts = identityFacts,
                usualStorePriceFacts = usualStorePriceFacts,
                alternativeStorePriceFacts = alternativeStorePriceFacts,
                additionalTravelFacts = additionalTravelFacts,
                currentnessFacts = currentnessFacts,
                issue = issue,
                priceAndCurrentnessReadyAlternativeStoreKeys = emptyList()
            )
    }
}

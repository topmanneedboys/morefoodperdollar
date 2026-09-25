package com.valuepilot.app

import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel

/**
 * Explicit additional-travel delta for one resolved alternative logical store.
 *
 * [additionalTravel] is already-resolved extra distance/time versus the user's usual-store trip
 * baseline. This type does not derive a route, value time, score inconvenience, or decide whether
 * the extra travel is worth taking.
 */
data class StapleWatchAlternativeAdditionalTravelFact(
    val storeKey: ShoppingStoreKey,
    val additionalTravel: ShoppingTravel
)

/**
 * Complete additional-travel facts for the exact alternative identities of one staple-watch intent.
 *
 * The contract requires exactly one travel delta per resolved alternative store, in the same stable
 * order as [identityFacts]. It carries no price, evidence-currentness decision, provider choice,
 * economics, persistence instruction, background schedule, or notification authority.
 *
 * Empty facts are valid when identity resolution explicitly found no alternative stores.
 */
data class StapleWatchAlternativeAdditionalTravelFacts(
    val identityFacts: StapleWatchAlternativeStoreIdentityFacts,
    val alternatives: List<StapleWatchAlternativeAdditionalTravelFact>
) {
    init {
        val expectedStoreKeys = identityFacts.alternativeStoreKeys
        require(alternatives.map { fact -> fact.storeKey }.distinct().size == alternatives.size) {
            "Staple-watch alternative travel facts must use unique store keys"
        }
        require(alternatives.map { fact -> fact.storeKey } == expectedStoreKeys) {
            "Staple-watch alternative travel facts must exactly cover alternative stores in stable order"
        }
    }

    val intent: StapleWatchFactCheckIntent
        get() = identityFacts.intent

    val resolvedRequirement: StapleWatchFactResolutionRequirement
        get() = StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS

    companion object {
        fun fromUnordered(
            identityFacts: StapleWatchAlternativeStoreIdentityFacts,
            additionalTravelByStore: Map<ShoppingStoreKey, ShoppingTravel>
        ): StapleWatchAlternativeAdditionalTravelFacts {
            val expectedStoreKeys = identityFacts.alternativeStoreKeys
            require(additionalTravelByStore.keys == expectedStoreKeys.toSet()) {
                "Staple-watch alternative travel must exactly cover the resolved alternative stores"
            }

            return StapleWatchAlternativeAdditionalTravelFacts(
                identityFacts = identityFacts,
                alternatives =
                    expectedStoreKeys.map { storeKey ->
                        StapleWatchAlternativeAdditionalTravelFact(
                            storeKey = storeKey,
                            additionalTravel = requireNotNull(additionalTravelByStore[storeKey])
                        )
                    }
            )
        }
    }
}

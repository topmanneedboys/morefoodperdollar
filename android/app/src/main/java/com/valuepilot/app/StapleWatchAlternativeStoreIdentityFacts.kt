package com.valuepilot.app

import com.valuepilot.core.ShoppingStoreKey

private const val MAX_STAPLE_WATCH_ALTERNATIVE_STORE_IDENTITIES = 64

/**
 * Resolved logical alternative-store identities for one exact staple fact-check intent.
 *
 * These are only stable [ShoppingStoreKey] candidates. They do not establish an exact
 * merchant/location/channel offer scope, current price, route, evidence freshness, economic
 * result, persistence instruction, background schedule, or notification authority.
 *
 * An empty list is a valid explicit result meaning that the resolver found no alternative logical
 * store candidates for this intent. That is different from an unresolved requirement, which is
 * represented separately by [StapleWatchFactResolutionReadiness].
 */
data class StapleWatchAlternativeStoreIdentityFacts(
    val intent: StapleWatchFactCheckIntent,
    val alternativeStoreKeys: List<ShoppingStoreKey>
) {
    init {
        require(alternativeStoreKeys.size <= MAX_STAPLE_WATCH_ALTERNATIVE_STORE_IDENTITIES) {
            "Staple-watch alternative store identities exceed the bound"
        }
        require(alternativeStoreKeys.distinct().size == alternativeStoreKeys.size) {
            "Staple-watch alternative store identities must be unique"
        }
        require(alternativeStoreKeys.none { storeKey -> storeKey == intent.usualStoreKey }) {
            "The usual store cannot also be an alternative store"
        }
        require(alternativeStoreKeys == alternativeStoreKeys.sortedBy { storeKey -> storeKey.value }) {
            "Staple-watch alternative store identities must use stable key order"
        }
    }

    val resolvedRequirement: StapleWatchFactResolutionRequirement
        get() = StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES

    companion object {
        fun fromUnordered(
            intent: StapleWatchFactCheckIntent,
            alternativeStoreKeys: Collection<ShoppingStoreKey>
        ): StapleWatchAlternativeStoreIdentityFacts {
            require(alternativeStoreKeys.size <= MAX_STAPLE_WATCH_ALTERNATIVE_STORE_IDENTITIES) {
                "Staple-watch alternative store identities exceed the bound"
            }
            require(alternativeStoreKeys.toSet().size == alternativeStoreKeys.size) {
                "Staple-watch alternative store identities must be unique"
            }

            return StapleWatchAlternativeStoreIdentityFacts(
                intent = intent,
                alternativeStoreKeys = alternativeStoreKeys.sortedBy { storeKey -> storeKey.value }
            )
        }
    }
}

package com.valuepilot.app

import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey

private const val MIN_STAPLE_WATCH_FACT_CHECK_ITEMS = 2

/**
 * Fact categories that must still be resolved after an accepted Saved-backed identity handoff.
 *
 * These values describe missing inputs only. They carry no provider choice, current fact value,
 * freshness decision, route value, economic result, persistence instruction, or delivery authority.
 */
enum class StapleWatchFactResolutionRequirement {
    USUAL_STORE_BASKET_PRICE_EVIDENCE,
    ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
    ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
    ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
    EVIDENCE_CURRENTNESS_METADATA
}

/**
 * Explicit next-stage intent after the Saved setup handoff gate has accepted the user's selection.
 *
 * The intent preserves only the watched-basket request and usual-store identity. It does not say
 * that any current price, route, evidence, alternative store, switch decision, or notification is
 * already known. [requirements] is a fixed declaration of the fact categories a later replaceable
 * resolver must satisfy before deterministic Watch My Staples economics can be evaluated.
 */
data class StapleWatchFactCheckIntent(
    val request: ShoppingRequest,
    val usualStoreKey: ShoppingStoreKey
) {
    init {
        require(request.itemKeys.size >= MIN_STAPLE_WATCH_FACT_CHECK_ITEMS)
    }

    val requirements: List<StapleWatchFactResolutionRequirement>
        get() = REQUIRED_FACTS

    companion object {
        private val REQUIRED_FACTS =
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            )
    }
}

/**
 * Fail-closed adapter from the verified explicit handoff attempt to the next fact-resolution intent.
 *
 * Rejected handoffs produce no intent. Accepted handoffs are preserved without deriving labels,
 * providers, locations, prices, travel, evidence freshness, economics, or notification policy.
 */
object StapleWatchSavedFactCheckIntentAdapter {

    fun from(
        attempt: StapleWatchSavedIdentityHandoffAttempt
    ): StapleWatchFactCheckIntent? {
        val handoff = attempt.handoff ?: return null
        return StapleWatchFactCheckIntent(
            request = handoff.request,
            usualStoreKey = handoff.usualStoreKey
        )
    }
}

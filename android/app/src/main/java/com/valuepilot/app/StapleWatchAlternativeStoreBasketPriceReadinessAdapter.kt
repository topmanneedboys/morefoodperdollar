package com.valuepilot.app

/**
 * Pure readiness transition for accounted alternative-store basket-price facts.
 *
 * Matching facts clear only their own declared requirement. The adapter deliberately does not
 * inspect alternative-store price payloads, infer basket completeness, or grant authority for
 * currentness, travel, economics, persistence, scheduling, or notification. Facts for a different
 * fact-check intent fail closed, and repeated application is idempotent.
 */
object StapleWatchAlternativeStoreBasketPriceReadinessAdapter {

    fun apply(
        readiness: StapleWatchFactResolutionReadiness,
        facts: StapleWatchAlternativeStoreBasketPriceFacts
    ): StapleWatchFactResolutionReadiness {
        require(readiness.intent == facts.intent) {
            "Staple-watch alternative-store price facts must match the readiness intent"
        }

        val resolvedRequirement = facts.resolvedRequirement
        if (resolvedRequirement !in readiness.unresolvedRequirements) {
            return readiness
        }

        return StapleWatchFactResolutionReadiness.fromUnresolved(
            intent = readiness.intent,
            unresolvedRequirements =
                readiness.unresolvedRequirements
                    .filterNot { requirement -> requirement == resolvedRequirement }
                    .toSet()
        )
    }
}

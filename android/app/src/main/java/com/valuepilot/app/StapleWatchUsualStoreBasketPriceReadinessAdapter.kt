package com.valuepilot.app

/**
 * Pure readiness transition for accounted usual-store basket-price facts.
 *
 * Matching facts clear only their own declared requirement. The adapter deliberately does not
 * inspect the fact payload, infer basket completeness, or grant authority for any later decision.
 * Facts for a different fact-check intent fail closed, and repeated application is idempotent.
 */
object StapleWatchUsualStoreBasketPriceReadinessAdapter {

    fun apply(
        readiness: StapleWatchFactResolutionReadiness,
        facts: StapleWatchUsualStoreBasketPriceFacts
    ): StapleWatchFactResolutionReadiness {
        require(readiness.intent == facts.intent) {
            "Staple-watch usual-store price facts must match the readiness intent"
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

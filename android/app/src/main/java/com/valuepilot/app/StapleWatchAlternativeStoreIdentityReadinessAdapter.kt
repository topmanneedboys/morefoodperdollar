package com.valuepilot.app

/**
 * Pure readiness transition for resolved alternative logical-store identities.
 *
 * This adapter accepts no provider, store-scope, price, route, freshness, economic, persistence,
 * scheduling, or notification input. Matching facts clear only their own declared requirement.
 * Facts for a different fact-check intent fail closed, and reapplying facts whose requirement is
 * already accounted for is idempotent.
 */
object StapleWatchAlternativeStoreIdentityReadinessAdapter {

    fun apply(
        readiness: StapleWatchFactResolutionReadiness,
        facts: StapleWatchAlternativeStoreIdentityFacts
    ): StapleWatchFactResolutionReadiness {
        require(readiness.intent == facts.intent) {
            "Staple-watch alternative store identity facts must match the readiness intent"
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

package com.valuepilot.app

/**
 * Pure readiness transition for resolved alternative additional-travel facts.
 *
 * Matching facts clear only their own declared requirement. The adapter deliberately does not read
 * or interpret the travel values carried by the fact object, does not implicitly resolve the
 * alternative-identity requirement, and owns no price, freshness, economics, persistence,
 * scheduling, or notification authority. Facts for a different fact-check intent fail closed.
 */
object StapleWatchAlternativeAdditionalTravelReadinessAdapter {

    fun apply(
        readiness: StapleWatchFactResolutionReadiness,
        facts: StapleWatchAlternativeAdditionalTravelFacts
    ): StapleWatchFactResolutionReadiness {
        require(readiness.intent == facts.intent) {
            "Staple-watch alternative travel facts must match the readiness intent"
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

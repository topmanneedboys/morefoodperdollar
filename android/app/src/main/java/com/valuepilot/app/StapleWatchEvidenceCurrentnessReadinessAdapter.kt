package com.valuepilot.app

/**
 * Pure readiness transition for authoritative staple-watch evidence-currentness metadata.
 *
 * Matching facts clear only their own declared requirement. The adapter deliberately does not
 * inspect currentness payloads, freshness classifications, source price facts, or infer that price,
 * identity, travel, economics, persistence, scheduling, or notification requirements are satisfied.
 * Facts for a different fact-check intent fail closed, and repeated application is idempotent.
 */
object StapleWatchEvidenceCurrentnessReadinessAdapter {

    fun apply(
        readiness: StapleWatchFactResolutionReadiness,
        facts: StapleWatchEvidenceCurrentnessFacts
    ): StapleWatchFactResolutionReadiness {
        require(readiness.intent == facts.intent) {
            "Staple-watch evidence-currentness facts must match the readiness intent"
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

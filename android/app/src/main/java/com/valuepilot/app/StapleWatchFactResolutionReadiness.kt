package com.valuepilot.app

/**
 * Immutable progress snapshot for one exact [StapleWatchFactCheckIntent].
 *
 * This boundary reports only which declared fact categories remain unresolved. It carries no
 * resolved fact values and grants no authority to evaluate economics, persist state, schedule
 * work, or deliver a notification. A later fact-value handoff must remain a separate boundary.
 */
data class StapleWatchFactResolutionReadiness private constructor(
    val intent: StapleWatchFactCheckIntent,
    val unresolvedRequirements: List<StapleWatchFactResolutionRequirement>
) {
    init {
        require(unresolvedRequirements.distinct().size == unresolvedRequirements.size) {
            "Staple fact-resolution readiness cannot contain duplicate requirements"
        }
        require(intent.requirements.containsAll(unresolvedRequirements)) {
            "Staple fact-resolution readiness must use only requirements declared by its intent"
        }
        require(
            unresolvedRequirements ==
                intent.requirements.filter { requirement -> requirement in unresolvedRequirements }
        ) {
            "Staple fact-resolution readiness must preserve the intent requirement order"
        }
    }

    val resolvedRequirements: List<StapleWatchFactResolutionRequirement>
        get() =
            intent.requirements.filterNot { requirement ->
                requirement in unresolvedRequirements
            }

    val allRequirementsReportedResolved: Boolean
        get() = unresolvedRequirements.isEmpty()

    companion object {
        fun initial(
            intent: StapleWatchFactCheckIntent
        ): StapleWatchFactResolutionReadiness =
            StapleWatchFactResolutionReadiness(
                intent = intent,
                unresolvedRequirements = intent.requirements
            )

        fun fromUnresolved(
            intent: StapleWatchFactCheckIntent,
            unresolvedRequirements: Set<StapleWatchFactResolutionRequirement>
        ): StapleWatchFactResolutionReadiness {
            require(intent.requirements.containsAll(unresolvedRequirements)) {
                "Staple fact-resolution readiness must use only requirements declared by its intent"
            }

            return StapleWatchFactResolutionReadiness(
                intent = intent,
                unresolvedRequirements =
                    intent.requirements.filter { requirement ->
                        requirement in unresolvedRequirements
                    }
            )
        }
    }
}

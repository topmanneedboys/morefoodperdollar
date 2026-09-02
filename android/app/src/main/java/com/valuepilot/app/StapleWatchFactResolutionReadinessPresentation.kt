package com.valuepilot.app

/**
 * Consumer-safe progress for one foreground Watch fact-resolution attempt.
 *
 * Only bounded counts and fixed requirement labels cross into the renderer. Exact intent
 * identities, fact payloads, prices, routes and evidence provenance remain outside this state.
 */
data class StapleWatchFactResolutionUiState(
    val totalRequirementCount: Int,
    val resolvedRequirementCount: Int,
    val unresolvedRequirementLabels: List<String>,
    val headline: String,
    val guidance: String
) {
    init {
        require(totalRequirementCount in 1..5)
        require(resolvedRequirementCount in 0..totalRequirementCount)
        require(unresolvedRequirementLabels.size == totalRequirementCount - resolvedRequirementCount)
        require(unresolvedRequirementLabels.distinct().size == unresolvedRequirementLabels.size)
        require(unresolvedRequirementLabels.none(String::isBlank))
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
    }
}

/** Pure mapping of exact readiness into a concise, identity-free consumer progress state. */
internal object StapleWatchFactResolutionUiProjector {

    fun project(readiness: StapleWatchFactResolutionReadiness): StapleWatchFactResolutionUiState {
        val unresolved = readiness.unresolvedRequirements.map(::label)
        val total = readiness.intent.requirements.size
        val resolved = total - unresolved.size
        val complete = unresolved.isEmpty()

        return StapleWatchFactResolutionUiState(
            totalRequirementCount = total,
            resolvedRequirementCount = resolved,
            unresolvedRequirementLabels = unresolved,
            headline = if (complete) "All fact checks supplied" else "Fact checks in progress",
            guidance =
                if (complete) {
                    "All required fact categories are supplied. Policy and display metadata still remain separate before a decision can be shown."
                } else {
                    "$resolved of $total required fact checks supplied. No switch decision is available yet."
                }
        )
    }

    private fun label(requirement: StapleWatchFactResolutionRequirement): String =
        when (requirement) {
            StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE ->
                "Usual-store basket prices"
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES ->
                "Alternative store identities"
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE ->
                "Alternative-store basket prices"
            StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS ->
                "Additional travel details"
            StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA ->
                "Evidence freshness metadata"
        }
}

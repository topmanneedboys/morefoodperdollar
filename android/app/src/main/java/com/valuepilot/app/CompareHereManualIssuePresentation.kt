package com.valuepilot.app

import com.valuepilot.core.ProductObservation

/**
 * Consumer-safe repair copy for observations that cannot cross the exact comparison boundary.
 *
 * The adapter's issue entries contain invocation-local observation ids. This projector resolves
 * those ids to the submitted position only; it never exposes the id or echoes untrusted OCR/raw
 * text. The result is guidance, not a new parser or validation authority.
 */
internal object CompareHereManualIssuePresentation {

    fun forIssues(
        issues: List<CompareHereManualObservationIssueEntry>,
        observations: List<ProductObservation>
    ): List<String> {
        val positionByObservationId =
            observations
                .mapIndexed { index, observation -> observation.id.value to index + 1 }
                .toMap()

        return issues
            .map { issue ->
                val prefix =
                    positionByObservationId[issue.observationId]
                        ?.let { position -> "Product $position" }
                        ?: "One product"
                "$prefix: ${repairCopy(issue.issue)}"
            }
            .distinct()
            .take(MAX_GUIDANCE_LINES)
    }

    private fun repairCopy(issue: CompareHereManualObservationIssue): String =
        when (issue) {
            CompareHereManualObservationIssue.PARSE_FAILED ->
                "add a product name, one concrete price and currency, and an exact package size or count."

            CompareHereManualObservationIssue.INVALID_CANDIDATE_ID ->
                "check the product details and try again."

            CompareHereManualObservationIssue.AMBIGUOUS_OR_MIXED_CURRENCY ->
                "use one concrete currency such as CA$ or US$."

            CompareHereManualObservationIssue.UNSUPPORTED_PROMOTION ->
                "describe a supported exact promotion or remove the promotion details."

            CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH ->
                "enter an exact package size or count instead of an estimate."

            CompareHereManualObservationIssue.INVALID_EXACT_FACTS ->
                "check the price, currency, and package details."

            CompareHereManualObservationIssue.DISPLAY_NAME_OMITTED ->
                "add a short product name so the result can be identified safely."
        }

    private const val MAX_GUIDANCE_LINES = CompareHereManualInputAdapter.MAX_OBSERVATIONS
}

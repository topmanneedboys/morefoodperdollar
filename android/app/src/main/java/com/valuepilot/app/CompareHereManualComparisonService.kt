package com.valuepilot.app

import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHereEvaluator
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.ProductObservation

sealed interface CompareHereManualComparisonResult {
    data class Success(
        val projection: CompareHereUiProjection,
        val adaptationIssues: List<CompareHereManualObservationIssueEntry>
    ) : CompareHereManualComparisonResult

    data class InputFailure(
        val reason: CompareHereManualInputFailure
    ) : CompareHereManualComparisonResult

    /**
     * One or more submitted observations could not safely cross the exact-core boundary.
     *
     * No comparison projection is exposed in this state. This prevents a surviving subset from
     * producing a consumer-facing winner while another submitted product silently disappeared
     * before the shared-core evaluator could see it.
     */
    data class RejectedObservations(
        val issues: List<CompareHereManualObservationIssueEntry>
    ) : CompareHereManualComparisonResult {
        init {
            require(issues.isNotEmpty())
            require(issues.all { it.issue.isPreCoreRejection() })
        }
    }
}

/**
 * Application-layer composition for the current manual Compare Here path.
 *
 * The semantic comparison intent remains an explicit caller assertion. This service only composes
 * the verified fail-closed manual adapter, the exact shared-core evaluator and the pure UI
 * projector. It does not infer comparability, perform ranking itself, or invoke the legacy SMART
 * ranking path.
 */
object CompareHereManualComparisonService {

    fun compare(
        comparisonIntentKey: CompareHereComparisonIntentKey,
        priceSelection: CompareHerePriceSelection,
        observations: List<ProductObservation>,
        parser: ProductParser = DeterministicProductParser
    ): CompareHereManualComparisonResult {
        val adapted =
            CompareHereManualInputAdapter.adapt(
                comparisonIntentKey = comparisonIntentKey,
                observations = observations,
                parser = parser
            )

        return when (adapted) {
            is CompareHereManualInputResult.Failure ->
                CompareHereManualComparisonResult.InputFailure(adapted.reason)

            is CompareHereManualInputResult.Success -> {
                val adaptation = adapted.adaptation
                val rejected = adaptation.issues.filter { it.issue.isPreCoreRejection() }
                if (rejected.isNotEmpty()) {
                    CompareHereManualComparisonResult.RejectedObservations(rejected)
                } else {
                    val exactResult =
                        CompareHereEvaluator.evaluate(
                            comparisonIntentKey = comparisonIntentKey,
                            priceSelection = priceSelection,
                            candidates = adaptation.candidates
                        )
                    CompareHereManualComparisonResult.Success(
                        projection =
                            CompareHereUiProjector.project(
                                result = exactResult,
                                displayMetadata = adaptation.displayMetadata
                            ),
                        adaptationIssues = adaptation.issues
                    )
                }
            }
        }
    }
}

private fun CompareHereManualObservationIssue.isPreCoreRejection(): Boolean =
    when (this) {
        CompareHereManualObservationIssue.PARSE_FAILED,
        CompareHereManualObservationIssue.INVALID_CANDIDATE_ID,
        CompareHereManualObservationIssue.AMBIGUOUS_OR_MIXED_CURRENCY,
        CompareHereManualObservationIssue.UNSUPPORTED_PROMOTION,
        CompareHereManualObservationIssue.INVALID_EXACT_FACTS -> true

        CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH,
        CompareHereManualObservationIssue.DISPLAY_NAME_OMITTED -> false
    }

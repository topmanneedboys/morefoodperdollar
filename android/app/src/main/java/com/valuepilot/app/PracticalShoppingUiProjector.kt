package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingDecision
import com.valuepilot.core.PrimaryShoppingPlanKind
import com.valuepilot.core.SecondStopDecision
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import java.math.BigDecimal

/**
 * Immutable UI-ready state for the Practical Shopping MVP.
 *
 * Shared-core owns every one-store/second-stop decision. This layer formats the
 * already-decided result only; it does not compare candidates, recalculate
 * savings, infer missing prices, or invent a hidden convenience score.
 */
data class PracticalShoppingPrimaryUiState(
    val badge: String,
    val storeName: String,
    val basketCostText: String,
    val coverageText: String,
    val travelText: String,
    val evidenceText: String,
    val whyText: String,
    val notice: String?
) {
    init {
        require(badge.isNotBlank())
        require(storeName.isNotBlank())
        require(basketCostText.isNotBlank())
        require(coverageText.isNotBlank())
        require(travelText.isNotBlank())
        require(evidenceText.isNotBlank())
        require(whyText.isNotBlank())
        require(notice == null || notice.isNotBlank())
    }
}

data class PracticalShoppingSecondStopUiState(
    val badge: String,
    val storeName: String,
    val combinedBasketCostText: String,
    val savingsText: String,
    val additionalTravelText: String,
    val evidenceText: String
) {
    init {
        require(badge.isNotBlank())
        require(storeName.isNotBlank())
        require(combinedBasketCostText.isNotBlank())
        require(savingsText.isNotBlank())
        require(additionalTravelText.isNotBlank())
        require(evidenceText.isNotBlank())
    }
}

data class PracticalShoppingUiState(
    val headline: String,
    val primary: PracticalShoppingPrimaryUiState?,
    val secondStop: PracticalShoppingSecondStopUiState?,
    val secondaryMessage: String?
) {
    init {
        require(headline.isNotBlank())
        require(secondaryMessage == null || secondaryMessage.isNotBlank())
    }
}

/**
 * UI state plus opaque exact keys for future typed actions.
 *
 * The renderer receives [state] only. Internal store keys remain outside normal
 * consumer strings so the UI never reconstructs business facts from display text.
 */
data class PracticalShoppingUiProjection(
    val state: PracticalShoppingUiState,
    val primaryStoreKey: ShoppingStoreKey?,
    val addedStoreKey: ShoppingStoreKey?,
    val exactDecision: PracticalShoppingDecision
) {
    init {
        require((state.primary == null) == (primaryStoreKey == null))
        require((state.secondStop == null) == (addedStoreKey == null))
    }
}

object PracticalShoppingUiProjector {

    fun project(
        request: ShoppingRequest,
        decision: PracticalShoppingDecision,
        storeDisplayNames: Map<ShoppingStoreKey, String>
    ): PracticalShoppingUiProjection {
        storeDisplayNames.values.forEach { require(it.isNotBlank()) }

        val primary = decision.primary?.let { candidate ->
            val coveredCount = candidate.coveredItemKeys.size
            val totalCount = request.itemKeys.size
            val complete = decision.primaryKind == PrimaryShoppingPlanKind.COMPLETE_PRICE_COMPARISON

            PracticalShoppingPrimaryUiState(
                badge =
                    if (complete) {
                        "BEST ONE-STORE OPTION"
                    } else {
                        "BEST COVERAGE FOUND"
                    },
                storeName = displayName(candidate.storeKey, storeDisplayNames),
                basketCostText =
                    if (complete) {
                        "Basket ${formatMoney(candidate.knownBasketCost)}"
                    } else {
                        "Known subtotal ${formatMoney(candidate.knownBasketCost)}"
                    },
                coverageText = coverageText(coveredCount, totalCount),
                travelText = formatTravel(candidate.travel),
                evidenceText = formatEvidence(candidate.evidence),
                whyText = primaryWhyText(decision.primaryKind),
                notice =
                    if (complete) {
                        null
                    } else {
                        incompleteNotice(totalCount - coveredCount)
                    }
            )
        }

        val secondStop =
            if (decision.secondStopDecision == SecondStopDecision.RECOMMENDED) {
                val candidate = requireNotNull(decision.secondStop)
                val savings = requireNotNull(decision.incrementalSecondStopSavings)

                PracticalShoppingSecondStopUiState(
                    badge = "OPTIONAL EXTRA STOP",
                    storeName = displayName(candidate.addedStoreKey, storeDisplayNames),
                    combinedBasketCostText =
                        "Combined basket ${formatMoney(candidate.knownCombinedBasketCost)}",
                    savingsText = "Save ${formatMoney(savings)}",
                    additionalTravelText =
                        "Adds ${formatTravel(candidate.additionalTravel)}",
                    evidenceText = formatEvidence(candidate.evidence)
                )
            } else {
                null
            }

        val state =
            PracticalShoppingUiState(
                headline = headline(decision.primaryKind),
                primary = primary,
                secondStop = secondStop,
                secondaryMessage = secondaryMessage(decision)
            )

        return PracticalShoppingUiProjection(
            state = state,
            primaryStoreKey = decision.primary?.storeKey,
            addedStoreKey = decision.secondStop?.addedStoreKey,
            exactDecision = decision
        )
    }

    private fun headline(kind: PrimaryShoppingPlanKind): String =
        when (kind) {
            PrimaryShoppingPlanKind.NO_COVERAGE ->
                "Not enough price coverage yet"

            PrimaryShoppingPlanKind.COMPLETE_PRICE_COMPARISON ->
                "Your best practical shop"

            PrimaryShoppingPlanKind.INCOMPLETE_BEST_COVERAGE ->
                "Best option with the prices we know"
        }

    /**
     * Explains only the shared-core decision kind. It does not inspect or compare
     * candidates, recompute money, or create a second presentation-layer score.
     */
    internal fun primaryWhyText(kind: PrimaryShoppingPlanKind): String =
        when (kind) {
            PrimaryShoppingPlanKind.NO_COVERAGE ->
                "No one-store option has usable price coverage yet."

            PrimaryShoppingPlanKind.COMPLETE_PRICE_COMPARISON ->
                "Lowest known complete basket among the one-store options compared."

            PrimaryShoppingPlanKind.INCOMPLETE_BEST_COVERAGE ->
                "No complete basket is priced yet; this option covers the most requested items."
        }

    private fun secondaryMessage(decision: PracticalShoppingDecision): String? =
        when (decision.secondStopDecision) {
            SecondStopDecision.NOT_EVALUATED_NO_PRIMARY ->
                "No requested item has a usable price yet."

            SecondStopDecision.NOT_EVALUATED_PRIMARY_INCOMPLETE ->
                "Not enough complete price coverage to judge another stop fairly."

            SecondStopDecision.NOT_WORTH_IT ->
                "Another stop is not worth it under your current savings and travel limits."

            SecondStopDecision.RECOMMENDED ->
                null
        }

    private fun displayName(
        key: ShoppingStoreKey,
        storeDisplayNames: Map<ShoppingStoreKey, String>
    ): String =
        requireNotNull(storeDisplayNames[key]) {
            "Missing consumer display name for a practical-shopping store"
        }.also { require(it.isNotBlank()) }

    private fun coverageText(coveredCount: Int, totalCount: Int): String =
        "$coveredCount of $totalCount ${if (totalCount == 1) "item" else "items"} priced"

    private fun incompleteNotice(missingCount: Int): String =
        if (missingCount == 1) {
            "1 item still has an unknown price. This is not a complete basket total."
        } else {
            "$missingCount items still have unknown prices. This is not a complete basket total."
        }

    /** Exact decimal formatting only; never convert Money to Double. */
    internal fun formatMoney(money: Money): String =
        "${BigDecimal.valueOf(money.minorUnits).movePointLeft(money.fractionDigits).toPlainString()} ${money.currencyCode}"

    internal fun formatTravel(travel: ShoppingTravel): String {
        val wholeMinutes = travel.travelTimeSeconds / 60L
        val minutes =
            if (travel.travelTimeSeconds % 60L == 0L) {
                wholeMinutes
            } else {
                wholeMinutes + 1L
            }

        val distance =
            if (travel.distanceMetres < 1_000L) {
                "${travel.distanceMetres} m"
            } else {
                val kilometres =
                    BigDecimal.valueOf(travel.distanceMetres)
                        .movePointLeft(3)
                        .stripTrailingZeros()
                        .toPlainString()
                "$kilometres km"
            }

        return "$minutes min · $distance"
    }

    internal fun formatEvidence(evidence: ShoppingPlanEvidenceSummary): String =
        "${evidence.freshItemCount} fresh · " +
            "${evidence.staleItemCount} stale · " +
            "${evidence.unknownFreshnessItemCount} unknown"
}

package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingDecision
import com.valuepilot.core.PracticalShoppingPolicy
import com.valuepilot.core.PrimaryShoppingPlanKind
import com.valuepilot.core.SecondStopDecision
import com.valuepilot.core.ShoppingItemKey
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
    val missingItemsText: String?,
    val travelText: String,
    val evidenceText: String,
    val whyText: String,
    val notice: String?,
    /** Presentation-only caution derived from explicit evidence freshness counts. */
    val hasPriceFreshnessCaution: Boolean = false,
    val freshnessNotice: String? = null
) {
    init {
        require(badge.isNotBlank())
        require(storeName.isNotBlank())
        require(basketCostText.isNotBlank())
        require(coverageText.isNotBlank())
        require(missingItemsText == null || missingItemsText.isNotBlank())
        require(travelText.isNotBlank())
        require(evidenceText.isNotBlank())
        require(whyText.isNotBlank())
        require(notice == null || notice.isNotBlank())
        require(freshnessNotice == null || freshnessNotice.isNotBlank())
        require(!hasPriceFreshnessCaution || freshnessNotice != null)
        require((missingItemsText == null) == (notice == null))
    }
}

data class PracticalShoppingSecondStopUiState(
    val badge: String,
    val storeName: String,
    val baseItemsText: String,
    val addedItemsText: String,
    val combinedBasketCostText: String,
    val savingsText: String,
    val additionalTravelText: String,
    val evidenceText: String
) {
    init {
        require(badge.isNotBlank())
        require(storeName.isNotBlank())
        require(baseItemsText.isNotBlank())
        require(addedItemsText.isNotBlank())
        require(combinedBasketCostText.isNotBlank())
        require(savingsText.isNotBlank())
        require(additionalTravelText.isNotBlank())
        require(evidenceText.isNotBlank())
    }
}

/** Consumer-facing store allocation for one already-covered requested item. */
data class PracticalShoppingItemStoreAssignmentUiState(
    val itemKey: ShoppingItemKey,
    val storeName: String,
    /** Exact price used for this covered item, when the upstream candidate supplied a breakdown. */
    val priceText: String? = null
) {
    init {
        require(itemKey.value.isNotBlank())
        require(storeName.isNotBlank())
        require(priceText == null || priceText.isNotBlank())
    }

    /** Keep opaque item identity out of diagnostic/UI text while retaining typed lookup. */
    override fun toString(): String =
        "PracticalShoppingItemStoreAssignmentUiState(storeName=$storeName)"
}

data class PracticalShoppingUiState(
    val headline: String,
    val primary: PracticalShoppingPrimaryUiState?,
    val secondStop: PracticalShoppingSecondStopUiState?,
    val secondaryMessage: String?,
    val itemStoreAssignments: List<PracticalShoppingItemStoreAssignmentUiState> = emptyList()
) {
    init {
        require(headline.isNotBlank())
        require(secondaryMessage == null || secondaryMessage.isNotBlank())
        require(
            itemStoreAssignments.map { it.itemKey }.distinct().size ==
                itemStoreAssignments.size
        ) {
            "Each practical-shopping item may have only one store allocation"
        }
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
        storeDisplayNames: Map<ShoppingStoreKey, String>,
        itemDisplayNames: Map<ShoppingItemKey, String>,
        policy: PracticalShoppingPolicy
    ): PracticalShoppingUiProjection {
        storeDisplayNames.values.forEach { require(it.isNotBlank()) }
        itemDisplayNames.values.forEach { require(it.isNotBlank()) }

        val primary = decision.primary?.let { candidate ->
            val coveredCount = candidate.coveredItemKeys.size
            val totalCount = request.itemKeys.size
            val complete = decision.primaryKind == PrimaryShoppingPlanKind.COMPLETE_PRICE_COMPARISON
            val missingItemKeys = request.itemKeys.filterNot(candidate.coveredItemKeys::contains)

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
                missingItemsText =
                    if (complete) {
                        null
                    } else {
                        missingItemsText(missingItemKeys, itemDisplayNames)
                    },
                travelText = formatTravel(candidate.travel),
                evidenceText = formatHomeEvidence(candidate.evidence),
                whyText = primaryWhyText(decision.primaryKind),
                notice =
                    if (complete) {
                        null
                    } else {
                        incompleteNotice(totalCount - coveredCount)
                    },
                hasPriceFreshnessCaution = hasPriceFreshnessCaution(candidate.evidence),
                freshnessNotice = freshnessNotice(candidate.evidence)
            )
        }

        val secondStop =
            if (decision.secondStopDecision == SecondStopDecision.RECOMMENDED) {
                val candidate = requireNotNull(decision.secondStop)
                val savings = requireNotNull(decision.incrementalSecondStopSavings)
                require(request.itemKeys.containsAll(candidate.addedStoreItemKeys)) {
                    "Second-stop item allocation must stay inside the shopping request"
                }
                val addedItemKeys =
                    request.itemKeys.filter(candidate.addedStoreItemKeys::contains)
                val baseItemKeys = request.itemKeys.filterNot(candidate.addedStoreItemKeys::contains)
                val addedStoreName = displayName(candidate.addedStoreKey, storeDisplayNames)
                val baseStoreName = displayName(candidate.baseStoreKey, storeDisplayNames)

                PracticalShoppingSecondStopUiState(
                    badge = "OPTIONAL EXTRA STOP",
                    storeName = addedStoreName,
                    baseItemsText =
                        itemsText(
                            prefix = "Buy at $baseStoreName",
                            itemKeys = baseItemKeys,
                            itemDisplayNames = itemDisplayNames
                        ),
                    addedItemsText =
                        itemsText(
                            prefix = "Then buy at $addedStoreName",
                            itemKeys = addedItemKeys,
                            itemDisplayNames = itemDisplayNames
                        ),
                    combinedBasketCostText =
                        "Combined basket ${formatMoney(candidate.knownCombinedBasketCost)}",
                    // This is a projected alternative, not a confirmed purchase outcome.
                    // Keep the wording explicit so the shopper cannot mistake a plan
                    // estimate for money already saved.
                    savingsText = "Could save ${formatMoney(savings)}",
                    additionalTravelText =
                        "Adds ${formatTravel(candidate.additionalTravel)}",
                    evidenceText = formatHomeEvidence(candidate.evidence)
                )
            } else {
                null
            }

        val state =
            PracticalShoppingUiState(
                headline = headline(decision.primaryKind),
                primary = primary,
                secondStop = secondStop,
                secondaryMessage = secondaryMessage(decision, policy),
                itemStoreAssignments =
                    projectItemStoreAssignments(
                        request = request,
                        decision = decision,
                        storeDisplayNames = storeDisplayNames
                    )
            )

        return PracticalShoppingUiProjection(
            state = state,
            primaryStoreKey = decision.primary?.storeKey,
            addedStoreKey = decision.secondStop?.addedStoreKey,
            exactDecision = decision
        )
    }

    /**
     * Projects the exact plan's existing item allocation for consumer collection guidance.
     * Missing items are intentionally omitted; no display string is used to infer coverage.
     */
    private fun projectItemStoreAssignments(
        request: ShoppingRequest,
        decision: PracticalShoppingDecision,
        storeDisplayNames: Map<ShoppingStoreKey, String>
    ): List<PracticalShoppingItemStoreAssignmentUiState> {
        val primary = decision.primary ?: return emptyList()
        val assignments = linkedMapOf<ShoppingItemKey, Pair<ShoppingStoreKey, Money?>>()
        primary.coveredItemKeys.forEach { itemKey ->
            assignments[itemKey] = primary.storeKey to primary.itemPrices[itemKey]
        }

        if (decision.secondStopDecision == SecondStopDecision.RECOMMENDED) {
            val secondStop = requireNotNull(decision.secondStop)
            secondStop.addedStoreItemKeys.forEach { itemKey ->
                assignments[itemKey] = secondStop.addedStoreKey to secondStop.itemPrices[itemKey]
            }
        }

        return request.itemKeys.mapNotNull { itemKey ->
            assignments[itemKey]?.let { (storeKey, price) ->
                PracticalShoppingItemStoreAssignmentUiState(
                    itemKey = itemKey,
                    storeName = displayName(storeKey, storeDisplayNames),
                    priceText = price?.let(::formatMoney)
                )
            }
        }
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

    private fun secondaryMessage(
        decision: PracticalShoppingDecision,
        policy: PracticalShoppingPolicy
    ): String? =
        when (decision.secondStopDecision) {
            SecondStopDecision.NOT_EVALUATED_NO_PRIMARY ->
                "No requested item has a usable price yet."

            SecondStopDecision.NOT_EVALUATED_PRIMARY_INCOMPLETE ->
                "Not enough complete price coverage to judge another stop fairly."

            SecondStopDecision.NOT_WORTH_IT ->
                notWorthItMessage(policy)

            SecondStopDecision.RECOMMENDED ->
                null
        }

    private fun notWorthItMessage(policy: PracticalShoppingPolicy): String {
        val distanceLimit =
            policy.maxAdditionalDistanceMetres?.let { distanceMetres ->
                " and ${formatDistance(distanceMetres)}"
            }.orEmpty()
        return "Another stop is not worth it: your current rule requires at least " +
            "${formatMoney(policy.minimumSecondStopSavings)} savings and caps extra travel at " +
            "${formatDurationLimit(policy.maxAdditionalTravelSeconds)}$distanceLimit."
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

    private fun missingItemsText(
        missingItemKeys: List<ShoppingItemKey>,
        itemDisplayNames: Map<ShoppingItemKey, String>
    ): String {
        require(missingItemKeys.isNotEmpty())
        val names =
            missingItemKeys.map { key ->
                requireNotNull(itemDisplayNames[key]) {
                    "Missing consumer display name for a practical-shopping item"
                }.also { require(it.isNotBlank()) }
            }
        val label = if (names.size == 1) "Missing price" else "Missing prices"
        return "$label: ${names.joinToString(", ")}"
    }

    private fun itemsText(
        prefix: String,
        itemKeys: List<ShoppingItemKey>,
        itemDisplayNames: Map<ShoppingItemKey, String>
    ): String {
        require(prefix.isNotBlank())
        if (itemKeys.isEmpty()) return "$prefix: none"
        val names =
            itemKeys.map { key ->
                requireNotNull(itemDisplayNames[key]) {
                    "Missing consumer display name for a practical-shopping item"
                }.also { require(it.isNotBlank()) }
            }
        return "$prefix: ${names.joinToString(", ")}"
    }

    private fun incompleteNotice(missingCount: Int): String =
        if (missingCount == 1) {
            "1 item still has an unknown price. This is not a complete basket total."
        } else {
            "$missingCount items still have unknown prices. This is not a complete basket total."
        }

    private fun hasPriceFreshnessCaution(evidence: ShoppingPlanEvidenceSummary): Boolean =
        evidence.agingItemCount > 0 ||
            evidence.staleItemCount > 0 ||
            evidence.unknownFreshnessItemCount > 0

    private fun freshnessNotice(evidence: ShoppingPlanEvidenceSummary): String? =
        if (hasPriceFreshnessCaution(evidence)) {
            "Some price evidence is not fully fresh. Verify before buying."
        } else {
            null
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

        return "$minutes min · ${formatDistance(travel.distanceMetres)}"
    }

    internal fun formatDurationLimit(seconds: Long): String {
        require(seconds >= 0L)
        return if (seconds % 60L == 0L) {
            "${seconds / 60L} min"
        } else {
            "$seconds sec"
        }
    }

    private fun formatDistance(distanceMetres: Long): String {
        require(distanceMetres >= 0L)
        return if (distanceMetres < 1_000L) {
            "$distanceMetres m"
        } else {
            val kilometres =
                BigDecimal.valueOf(distanceMetres)
                    .movePointLeft(3)
                    .stripTrailingZeros()
                    .toPlainString()
            "$kilometres km"
        }
    }

    internal fun formatEvidence(evidence: ShoppingPlanEvidenceSummary): String =
        if (evidence.agingItemCount == 0) {
            "${evidence.freshItemCount} fresh · " +
                "${evidence.staleItemCount} stale · " +
                "${evidence.unknownFreshnessItemCount} unknown"
        } else {
            "${evidence.freshItemCount} fresh · " +
                "${evidence.agingItemCount} aging · " +
                "${evidence.staleItemCount} stale · " +
                "${evidence.unknownFreshnessItemCount} unknown"
        }

    /** Home-facing label clarifies that unknown refers to freshness, not price. */
    private fun formatHomeEvidence(evidence: ShoppingPlanEvidenceSummary): String =
        "Price freshness: ${formatEvidence(evidence)}"
}

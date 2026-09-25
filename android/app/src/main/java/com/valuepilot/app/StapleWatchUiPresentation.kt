package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingPlanEvidenceSummary
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import com.valuepilot.core.StapleWatchBasketEconomicDecision
import com.valuepilot.core.StapleWatchEconomicDecision
import com.valuepilot.core.StapleWatchEconomicStatus

private const val MAX_STAPLE_WATCH_DISPLAY_METADATA = 64
private const val MAX_STAPLE_WATCH_RAW_STORE_LABEL_LENGTH = 500
private const val MAX_STAPLE_WATCH_CONSUMER_STORE_LABEL_LENGTH = 160

/** Detached, non-authoritative consumer label for one opaque staple-watch store key. */
data class StapleWatchStoreDisplayMetadataEntry(
    val storeKey: ShoppingStoreKey,
    val displayName: String
) {
    init {
        require(displayName.length <= MAX_STAPLE_WATCH_RAW_STORE_LABEL_LENGTH)
    }
}

data class StapleWatchStoreDisplayMetadata(
    val entries: List<StapleWatchStoreDisplayMetadataEntry> = emptyList()
) {
    init {
        require(entries.size <= MAX_STAPLE_WATCH_DISPLAY_METADATA)
        require(entries.map { it.storeKey }.distinct().size == entries.size) {
            "Staple-watch display metadata store keys must be unique"
        }
    }
}

enum class StapleWatchUiStatus {
    NOT_ENOUGH_STAPLES,
    BASELINE_INCOMPLETE,
    NOT_WORTH_SWITCHING,
    WORTH_CHECKING,
    DISPLAY_METADATA_INCOMPLETE
}

data class StapleWatchSwitchUiState(
    val badge: String,
    val storeName: String,
    val savingsText: String,
    val additionalTravelText: String,
    val alternativeEvidenceText: String,
    val actionText: String
) {
    init {
        require(badge.isNotBlank())
        requireConsumerStapleWatchStoreLabel(storeName)
        require(savingsText.isNotBlank())
        require(additionalTravelText.isNotBlank())
        require(alternativeEvidenceText.isNotBlank())
        require(actionText.isNotBlank())
    }
}

data class StapleWatchUiState(
    val headline: String,
    val status: StapleWatchUiStatus,
    val statusTitle: String,
    val guidance: String,
    val baselineEvidenceText: String,
    val switchCandidate: StapleWatchSwitchUiState?,
    val notice: String?
) {
    init {
        require(headline.isNotBlank())
        require(statusTitle.isNotBlank())
        require(guidance.isNotBlank())
        require(baselineEvidenceText.isNotBlank())
        require(notice == null || notice.isNotBlank())
        require((switchCandidate != null) == (status == StapleWatchUiStatus.WORTH_CHECKING))
    }
}

/**
 * Immutable legacy consumer state plus the opaque exact store key retained outside renderer strings.
 *
 * A physical renderer should receive [state] only. The projected state never reconstructs a
 * store identity from text and never converts economic eligibility into notification authority.
 */
class StapleWatchUiProjection internal constructor(
    val state: StapleWatchUiState,
    internal val recommendedStoreKey: ShoppingStoreKey?,
    internal val exactDecision: StapleWatchEconomicDecision
) {
    init {
        require((state.switchCandidate == null) == (recommendedStoreKey == null))
    }
}

/**
 * Immutable Watch-native consumer state that retains the exact basket decision outside renderer text.
 *
 * This projection exists so the Watch-native path never needs to reconstruct the legacy
 * SingleStorePlanCandidate shape or invent its unrelated absolute-travel fact. A physical renderer
 * should receive [state] only; a worthwhile economic result is still not notification permission.
 */
class StapleWatchBasketUiProjection internal constructor(
    val state: StapleWatchUiState,
    internal val recommendedStoreKey: ShoppingStoreKey?,
    internal val exactDecision: StapleWatchBasketEconomicDecision
) {
    init {
        require((state.switchCandidate == null) == (recommendedStoreKey == null))
    }
}

/**
 * Pure presentation projector for deterministic staple-watch economic decisions.
 *
 * Both supported entry points format already-decided exact savings, explicit additional travel,
 * and evidence only. They never re-evaluate candidates, recalculate savings, interpret freshness,
 * choose alert timing, schedule background work, or authorize a notification. The Watch-native
 * overload consumes its basket decision directly and never converts it back to the legacy shape.
 */
object StapleWatchUiProjector {

    fun project(
        decision: StapleWatchEconomicDecision,
        metadata: StapleWatchStoreDisplayMetadata
    ): StapleWatchUiProjection {
        val recommendation = decision.recommendedAlternative
        val projected =
            projectConsumerState(
                status = decision.status,
                baselineEvidence = decision.baseline.evidence,
                recommendedStoreKey = recommendation?.storePlan?.storeKey,
                savings = decision.switchSavings,
                additionalTravel = recommendation?.additionalTravel,
                alternativeEvidence = recommendation?.storePlan?.evidence,
                metadata = metadata,
                worthwhileGuidance =
                    "This is an exact economic comparison only; price freshness and timing still need separate verification."
            )

        return StapleWatchUiProjection(
            state = projected.state,
            recommendedStoreKey = projected.recommendedStoreKey,
            exactDecision = decision
        )
    }

    fun project(
        decision: StapleWatchBasketEconomicDecision,
        metadata: StapleWatchStoreDisplayMetadata
    ): StapleWatchBasketUiProjection {
        val recommendation = decision.recommendedAlternative
        val projected =
            projectConsumerState(
                status = decision.status,
                baselineEvidence = decision.baseline.evidence,
                recommendedStoreKey = recommendation?.basket?.storeKey,
                savings = decision.switchSavings,
                additionalTravel = recommendation?.additionalTravel,
                alternativeEvidence = recommendation?.basket?.evidence,
                metadata = metadata,
                worthwhileGuidance =
                    "This is an exact economic comparison only; notification timing and delivery permission remain separate."
            )

        return StapleWatchBasketUiProjection(
            state = projected.state,
            recommendedStoreKey = projected.recommendedStoreKey,
            exactDecision = decision
        )
    }

    private fun projectConsumerState(
        status: StapleWatchEconomicStatus,
        baselineEvidence: ShoppingPlanEvidenceSummary,
        recommendedStoreKey: ShoppingStoreKey?,
        savings: Money?,
        additionalTravel: ShoppingTravel?,
        alternativeEvidence: ShoppingPlanEvidenceSummary?,
        metadata: StapleWatchStoreDisplayMetadata,
        worthwhileGuidance: String
    ): StapleWatchProjectedConsumerState {
        val rawLabels = metadata.entries.associate { it.storeKey to it.displayName }
        val safeRecommendedStoreName =
            recommendedStoreKey?.let { key ->
                safeStapleWatchStoreLabel(
                    raw = rawLabels[key],
                    storeKey = key
                )
            }

        val uiStatus =
            when {
                status == StapleWatchEconomicStatus.SWITCH_WORTHWHILE &&
                    safeRecommendedStoreName == null ->
                    StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE

                status == StapleWatchEconomicStatus.NOT_EVALUATED_NOT_ENOUGH_STAPLES ->
                    StapleWatchUiStatus.NOT_ENOUGH_STAPLES

                status == StapleWatchEconomicStatus.NOT_EVALUATED_BASELINE_INCOMPLETE ->
                    StapleWatchUiStatus.BASELINE_INCOMPLETE

                status == StapleWatchEconomicStatus.NOT_WORTH_SWITCHING ->
                    StapleWatchUiStatus.NOT_WORTH_SWITCHING

                else -> StapleWatchUiStatus.WORTH_CHECKING
            }

        val switchCandidate =
            if (uiStatus == StapleWatchUiStatus.WORTH_CHECKING) {
                StapleWatchSwitchUiState(
                    badge = "ECONOMIC SWITCH CANDIDATE",
                    storeName = requireNotNull(safeRecommendedStoreName),
                    savingsText =
                        "Could save ${PracticalShoppingUiProjector.formatMoney(requireNotNull(savings))}",
                    additionalTravelText =
                        "Adds ${PracticalShoppingUiProjector.formatTravel(requireNotNull(additionalTravel))}",
                    alternativeEvidenceText =
                        "Alternative evidence: " +
                            PracticalShoppingUiProjector.formatEvidence(
                                requireNotNull(alternativeEvidence)
                            ),
                    actionText = "Worth checking before your next shop"
                )
            } else {
                null
            }

        val state =
            StapleWatchUiState(
                headline = "Watch my staples",
                status = uiStatus,
                statusTitle = statusTitle(uiStatus),
                guidance = guidance(uiStatus, worthwhileGuidance),
                baselineEvidenceText =
                    "Usual store evidence: " +
                        PracticalShoppingUiProjector.formatEvidence(baselineEvidence),
                switchCandidate = switchCandidate,
                notice = notice(uiStatus)
            )

        return StapleWatchProjectedConsumerState(
            state = state,
            recommendedStoreKey =
                if (uiStatus == StapleWatchUiStatus.WORTH_CHECKING) recommendedStoreKey else null
        )
    }

    private fun statusTitle(status: StapleWatchUiStatus): String =
        when (status) {
            StapleWatchUiStatus.NOT_ENOUGH_STAPLES -> "Add more recurring items"
            StapleWatchUiStatus.BASELINE_INCOMPLETE -> "Need complete usual-store prices"
            StapleWatchUiStatus.NOT_WORTH_SWITCHING -> "No worthwhile store switch"
            StapleWatchUiStatus.WORTH_CHECKING -> "Store switch worth checking"
            StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE -> "Store name needed"
        }

    private fun guidance(
        status: StapleWatchUiStatus,
        worthwhileGuidance: String
    ): String =
        when (status) {
            StapleWatchUiStatus.NOT_ENOUGH_STAPLES ->
                "Watch at least two recurring items before judging a store switch."

            StapleWatchUiStatus.BASELINE_INCOMPLETE ->
                "Your usual-store basket needs complete price coverage before savings can be judged."

            StapleWatchUiStatus.NOT_WORTH_SWITCHING ->
                "No complete alternative basket clears your current savings and extra-travel limits."

            StapleWatchUiStatus.WORTH_CHECKING -> worthwhileGuidance

            StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE ->
                "The alternative store cannot be shown safely, so no switch suggestion is displayed."
        }

    private fun notice(status: StapleWatchUiStatus): String? =
        when (status) {
            StapleWatchUiStatus.WORTH_CHECKING ->
                "Economic eligibility alone does not authorize a notification."

            StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE ->
                "Opaque store identifiers are never used as consumer labels."

            else -> null
        }
}

private class StapleWatchProjectedConsumerState(
    val state: StapleWatchUiState,
    val recommendedStoreKey: ShoppingStoreKey?
)

private fun safeStapleWatchStoreLabel(
    raw: String?,
    storeKey: ShoppingStoreKey
): String? {
    val value = raw?.trim() ?: return null
    if (value.isBlank() || value.length > MAX_STAPLE_WATCH_CONSUMER_STORE_LABEL_LENGTH) return null
    if (value.any { Character.isISOControl(it.code) }) return null

    val opaqueKey = storeKey.value
    if (value.equals(opaqueKey, ignoreCase = true)) return null
    if (opaqueKey.length >= 6 && value.contains(opaqueKey, ignoreCase = true)) return null

    val lower = value.lowercase()
    val technicalMarkers =
        listOf("provider:", "merchant:", "location:", "osm:", "wikidata:", "internal-store-")
    if (technicalMarkers.any(lower::contains)) return null

    if (value.all { it.isDigit() || it.isWhitespace() }) return null

    return value
}

private fun requireConsumerStapleWatchStoreLabel(value: String) {
    require(value.isNotBlank())
    require(value.length <= MAX_STAPLE_WATCH_CONSUMER_STORE_LABEL_LENGTH)
    require(value.none { Character.isISOControl(it.code) })
}

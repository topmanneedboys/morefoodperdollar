package com.valuepilot.app

import com.valuepilot.core.ShoppingStoreKey
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
 * Immutable consumer state plus the opaque exact store key retained outside renderer strings.
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
 * Pure presentation projector for the deterministic staple-watch economic decision.
 *
 * It formats already-decided exact savings, explicit additional travel, and evidence only. It
 * never re-evaluates candidates, recalculates savings, interprets freshness, chooses alert timing,
 * schedules background work, or authorizes a notification.
 */
object StapleWatchUiProjector {

    fun project(
        decision: StapleWatchEconomicDecision,
        metadata: StapleWatchStoreDisplayMetadata
    ): StapleWatchUiProjection {
        val rawLabels = metadata.entries.associate { it.storeKey to it.displayName }
        val recommendation = decision.recommendedAlternative
        val recommendedStoreKey = recommendation?.storePlan?.storeKey
        val safeRecommendedStoreName =
            recommendedStoreKey?.let { key ->
                safeStapleWatchStoreLabel(
                    raw = rawLabels[key],
                    storeKey = key
                )
            }

        val status =
            when {
                decision.status == StapleWatchEconomicStatus.SWITCH_WORTHWHILE &&
                    safeRecommendedStoreName == null ->
                    StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE

                decision.status == StapleWatchEconomicStatus.NOT_EVALUATED_NOT_ENOUGH_STAPLES ->
                    StapleWatchUiStatus.NOT_ENOUGH_STAPLES

                decision.status == StapleWatchEconomicStatus.NOT_EVALUATED_BASELINE_INCOMPLETE ->
                    StapleWatchUiStatus.BASELINE_INCOMPLETE

                decision.status == StapleWatchEconomicStatus.NOT_WORTH_SWITCHING ->
                    StapleWatchUiStatus.NOT_WORTH_SWITCHING

                else -> StapleWatchUiStatus.WORTH_CHECKING
            }

        val switchCandidate =
            if (status == StapleWatchUiStatus.WORTH_CHECKING) {
                val exactRecommendation = requireNotNull(recommendation)
                val exactSavings = requireNotNull(decision.switchSavings)

                StapleWatchSwitchUiState(
                    badge = "ECONOMIC SWITCH CANDIDATE",
                    storeName = requireNotNull(safeRecommendedStoreName),
                    savingsText =
                        "Could save ${PracticalShoppingUiProjector.formatMoney(exactSavings)}",
                    additionalTravelText =
                        "Adds ${PracticalShoppingUiProjector.formatTravel(exactRecommendation.additionalTravel)}",
                    alternativeEvidenceText =
                        "Alternative evidence: " +
                            PracticalShoppingUiProjector.formatEvidence(
                                exactRecommendation.storePlan.evidence
                            ),
                    actionText = "Worth checking before your next shop"
                )
            } else {
                null
            }

        val state =
            StapleWatchUiState(
                headline = "Watch my staples",
                status = status,
                statusTitle = statusTitle(status),
                guidance = guidance(status),
                baselineEvidenceText =
                    "Usual store evidence: " +
                        PracticalShoppingUiProjector.formatEvidence(decision.baseline.evidence),
                switchCandidate = switchCandidate,
                notice = notice(status)
            )

        return StapleWatchUiProjection(
            state = state,
            recommendedStoreKey =
                if (status == StapleWatchUiStatus.WORTH_CHECKING) recommendedStoreKey else null,
            exactDecision = decision
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

    private fun guidance(status: StapleWatchUiStatus): String =
        when (status) {
            StapleWatchUiStatus.NOT_ENOUGH_STAPLES ->
                "Watch at least two recurring items before judging a store switch."

            StapleWatchUiStatus.BASELINE_INCOMPLETE ->
                "Your usual-store basket needs complete price coverage before savings can be judged."

            StapleWatchUiStatus.NOT_WORTH_SWITCHING ->
                "No complete alternative basket clears your current savings and extra-travel limits."

            StapleWatchUiStatus.WORTH_CHECKING ->
                "This is an exact economic comparison only; price freshness and timing still need separate verification."

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

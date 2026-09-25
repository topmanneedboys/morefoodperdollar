package com.valuepilot.app

/**
 * A deliberately small, privacy-safe text card for an explicitly shared comparison result.
 *
 * The card contains no product names, receipts, private history, location, account identifiers
 * or source/provider identifiers. It is only available after the existing exact comparison has
 * produced a fully labelled READY state. The comparison itself remains the authority; this
 * object only formats already-projected facts for a user-triggered share.
 */
data class CompareHereShareCard(
    val title: String,
    val text: String,
    val preview: String
) {
    init {
        require(title.isNotBlank())
        require(text.isNotBlank())
        require(preview.isNotBlank())
        require(title.length <= MAX_SHARE_CARD_TEXT_CHARS)
        require(text.length <= MAX_SHARE_CARD_TEXT_CHARS)
        require(preview.length <= MAX_SHARE_CARD_TEXT_CHARS)
        require(title.none { Character.isISOControl(it.code) })
        require(text.none { Character.isISOControl(it.code) })
        require(preview.none { Character.isISOControl(it.code) })
    }

    companion object {
        private const val MAX_SHARE_CARD_TEXT_CHARS = 600
    }
}

/**
 * Projects only a complete, exact Compare Here result into a generic share card.
 *
 * A blocked, incomplete, incompatible or partially-labelled result cannot become a share claim.
 * The card intentionally omits product labels and all private/contextual data by default.
 */
internal object CompareHereShareCardProjector {
    fun project(state: CompareHereUiState): CompareHereShareCard? {
        if (state.status != CompareHereUiStatus.READY || state.rows.size < 2) return null

        val bestRows = state.rows.filter { it.bestValue }
        if (bestRows.isEmpty()) return null

        val bestRate = bestRows.first().unitRateText
        val comparisonCount = state.rows.size
        val isTie = bestRows.size > 1
        val title =
            if (isTie) {
                "ValuePilot found a tied best unit price"
            } else {
                "ValuePilot found a better unit price"
            }
        val resultLine =
            if (isTie) {
                "Lowest exact unit price: $bestRate (tie)"
            } else {
                "Best exact unit price: $bestRate"
            }
        val disclosure =
            "Based on the $comparisonCount values I entered, not live store pricing."
        val text =
            "I compared $comparisonCount grocery options with ValuePilot. " +
                "$resultLine. $disclosure"
        val preview =
            "$title. $resultLine. $disclosure " +
                "This share contains no product names, history, receipt, location or account data."

        return CompareHereShareCard(
            title = title,
            text = text,
            preview = preview
        )
    }
}

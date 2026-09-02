package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection

/**
 * Pure lifecycle state for the manual Compare Here activity.
 *
 * This state intentionally contains no Android View references, raw product text, parser output,
 * candidate ids or ranking objects. It only records whether the current unchanged draft was
 * compared, when that attempt happened, whether the user confirmed like-for-like semantics, and
 * which explicit price basis the next comparison should use.
 */
data class CompareHereManualActivitySessionState(
    val comparisonWasRun: Boolean,
    val observedAtEpochMillis: Long,
    val likeForLikeConfirmed: Boolean,
    val priceSelection: CompareHerePriceSelection
) {
    companion object {
        fun initial(): CompareHereManualActivitySessionState =
            CompareHereManualActivitySessionState(
                comparisonWasRun = false,
                observedAtEpochMillis = 0L,
                likeForLikeConfirmed = false,
                priceSelection = CompareHerePriceSelection.CURRENT
            )

        fun restore(
            comparisonWasRun: Boolean,
            observedAtEpochMillis: Long,
            likeForLikeConfirmed: Boolean = false,
            priceSelection: CompareHerePriceSelection = CompareHerePriceSelection.CURRENT
        ): CompareHereManualActivitySessionState =
            CompareHereManualActivitySessionState(
                comparisonWasRun = comparisonWasRun,
                observedAtEpochMillis = observedAtEpochMillis,
                likeForLikeConfirmed = likeForLikeConfirmed,
                priceSelection = priceSelection
            )
    }
}

/** Deterministic transitions for the activity lifecycle around an unchanged manual draft. */
object CompareHereManualActivitySessionReducer {

    fun productsChanged(
        state: CompareHereManualActivitySessionState
    ): CompareHereManualActivitySessionState =
        state.copy(
            comparisonWasRun = false,
            observedAtEpochMillis = 0L,
            likeForLikeConfirmed = false
        )

    fun confirmationChanged(
        state: CompareHereManualActivitySessionState,
        confirmed: Boolean
    ): CompareHereManualActivitySessionState =
        state.copy(
            comparisonWasRun = false,
            observedAtEpochMillis = 0L,
            likeForLikeConfirmed = confirmed
        )

    fun priceSelectionChanged(
        state: CompareHereManualActivitySessionState,
        selection: CompareHerePriceSelection
    ): CompareHereManualActivitySessionState =
        state.copy(
            comparisonWasRun = false,
            observedAtEpochMillis = 0L,
            priceSelection = selection
        )

    fun comparisonAttempted(
        state: CompareHereManualActivitySessionState,
        observedAtEpochMillis: Long
    ): CompareHereManualActivitySessionState {
        require(observedAtEpochMillis >= 0L)
        return state.copy(
            comparisonWasRun = true,
            observedAtEpochMillis = observedAtEpochMillis
        )
    }

    fun clear(): CompareHereManualActivitySessionState =
        CompareHereManualActivitySessionState.initial()
}

package com.valuepilot.app

/**
 * Pure lifecycle state for the manual Compare Here activity.
 *
 * This state intentionally contains no Android View references, raw product text, parser output,
 * candidate ids or ranking objects. It only records whether the current unchanged draft was
 * compared, when that attempt happened, and whether the user confirmed like-for-like semantics.
 */
data class CompareHereManualActivitySessionState(
    val comparisonWasRun: Boolean,
    val observedAtEpochMillis: Long,
    val likeForLikeConfirmed: Boolean
) {
    companion object {
        fun initial(): CompareHereManualActivitySessionState =
            CompareHereManualActivitySessionState(
                comparisonWasRun = false,
                observedAtEpochMillis = 0L,
                likeForLikeConfirmed = false
            )

        fun restore(
            comparisonWasRun: Boolean,
            observedAtEpochMillis: Long,
            likeForLikeConfirmed: Boolean = false
        ): CompareHereManualActivitySessionState =
            CompareHereManualActivitySessionState(
                comparisonWasRun = comparisonWasRun,
                observedAtEpochMillis = observedAtEpochMillis,
                likeForLikeConfirmed = likeForLikeConfirmed
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

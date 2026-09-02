package com.valuepilot.app

/**
 * Presentation-only feedback for the latest explicit Saved-backed Watch handoff attempt.
 *
 * The messages describe setup acknowledgement or a fail-closed setup issue. They never claim
 * that prices, routes, evidence, economics, alerts, or notifications are available or complete.
 */
internal object StapleWatchSavedSelectionHandoffUiAdapter {

    fun apply(
        state: StapleWatchSavedSelectionUiState,
        attempt: StapleWatchSavedIdentityHandoffAttempt?
    ): StapleWatchSavedSelectionUiState {
        val attemptNotice =
            when {
                attempt == null -> null
                attempt.accepted ->
                    "Selection accepted. No switch decision has been made; current prices, route details, and evidence checks are still required."
                attempt.issue == StapleWatchSavedIdentityHandoffIssue.NOT_READY ->
                    "This selection is no longer ready. Choose at least two saved staples and a usual store before continuing."
                attempt.issue == StapleWatchSavedIdentityHandoffIssue.SELECTED_DISPLAY_METADATA_INCOMPLETE ->
                    "A selected saved choice needs a safe display name before continuing. Refresh Saved and try again."
                else -> null
            }

        val notice =
            when {
                attemptNotice == null -> state.notice
                state.notice == null -> attemptNotice
                else -> "$attemptNotice ${state.notice}"
            }

        return if (notice == state.notice) state else state.copy(notice = notice)
    }
}

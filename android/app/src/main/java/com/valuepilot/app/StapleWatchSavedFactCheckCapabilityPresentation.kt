package com.valuepilot.app

/**
 * Whether this composition has a real foreground source capable of producing the authoritative
 * Watch facts required after an explicit Saved-backed identity handoff.
 *
 * This is application capability only. It does not say whether a merchant has data, whether a
 * product has a current price, whether a route exists, or whether a switch is worthwhile.
 */
enum class StapleWatchForegroundFactCheckCapability {
    NOT_CONFIGURED,
    CONFIGURED
}

/**
 * Consumer-safe presentation gate between identity readiness and configured fact acquisition.
 *
 * The Saved identity projector remains authoritative for whether the user's explicit selection is
 * ready to hand off. This adapter can only remove continuation when the current composition has no
 * configured foreground fact source. It never upgrades a non-ready identity state, creates a fact,
 * interprets market coverage, selects a provider, or starts work.
 */
object StapleWatchSavedFactCheckCapabilityUiAdapter {

    fun apply(
        state: StapleWatchSavedSelectionUiState,
        capability: StapleWatchForegroundFactCheckCapability
    ): StapleWatchSavedSelectionUiState {
        if (
            capability == StapleWatchForegroundFactCheckCapability.CONFIGURED ||
            state.status != StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK
        ) {
            return state
        }

        val capabilityNotice =
            "Current price, route, and evidence checks aren't available in this build yet."
        val notice =
            if (state.notice == null) {
                capabilityNotice
            } else {
                "$capabilityNotice ${state.notice}"
            }

        return state.copy(
            status = StapleWatchSavedSelectionUiStatus.FACT_CHECK_UNAVAILABLE,
            guidance = "Your saved staple choices are ready.",
            notice = notice,
            continueAction = null,
            continueActionLabel = null
        )
    }
}

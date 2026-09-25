package com.valuepilot.app

/** Narrow target for any replaceable physical Saved-backed staple setup renderer. */
fun interface StapleWatchSavedSelectionSurfaceRenderer {
    fun render(state: StapleWatchSavedSelectionUiState)
}

/**
 * Projects explicit Saved-backed staple setup into the only state a physical renderer may see.
 *
 * The presenter owns no Android classes, persistence, lifecycle, networking, price/travel facts,
 * evidence-freshness policy, economic evaluation, scheduling, or notification authority. Stable
 * item/store identity remains encapsulated only in the typed actions already carried by the
 * consumer state; the renderer never receives Saved documents, exact-preference records, or raw
 * display metadata.
 *
 * Fact-check capability fails safe. Existing renderer-only composition keeps the established API
 * and defaults to NOT_CONFIGURED. A composition that really owns a foreground fact source must use
 * the explicit capability constructor. Keeping the renderer last there preserves Kotlin trailing-
 * lambda call sites without giving the presenter any fact authority.
 */
class StapleWatchSavedSelectionSurfacePresenter private constructor(
    private val renderer: StapleWatchSavedSelectionSurfaceRenderer,
    private val factCheckCapability: StapleWatchForegroundFactCheckCapability
) {
    private var lastIdentityState: StapleWatchSavedSelectionUiState? = null
    private var handoffAttempt: StapleWatchSavedIdentityHandoffAttempt? = null
    private var factResolutionReadiness: StapleWatchFactResolutionReadiness? = null

    constructor(renderer: StapleWatchSavedSelectionSurfaceRenderer) :
        this(
            renderer,
            StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED
        )

    constructor(
        factCheckCapability: StapleWatchForegroundFactCheckCapability,
        renderer: StapleWatchSavedSelectionSurfaceRenderer
    ) : this(
        renderer,
        factCheckCapability
    )

    fun render(
        savedState: PracticalShoppingSavedExactPreferenceState,
        selection: StapleWatchSavedIdentitySelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ) {
        val identityState =
            StapleWatchSavedIdentitySelectionUiProjector.project(
                savedState = savedState,
                selection = selection,
                metadata = metadata
            )
        val nextState =
            StapleWatchSavedFactCheckCapabilityUiAdapter.apply(
                state = identityState,
                capability = factCheckCapability
            )
        if (lastIdentityState != nextState) {
            handoffAttempt = null
            factResolutionReadiness = null
        }
        lastIdentityState = nextState
        renderCurrent()
    }

    /**
     * Records the latest explicit handoff result for the current immutable setup projection.
     * This is presentation-only feedback; it does not create or retry a fact check.
     */
    fun onHandoffAttempt(attempt: StapleWatchSavedIdentityHandoffAttempt) {
        handoffAttempt = attempt
        factResolutionReadiness = null
        renderCurrent()
    }

    /**
     * Records progress from the exact active foreground fact-resolution session.
     * The intent and fact values remain outside the renderer-facing state.
     */
    fun onFactResolutionReadiness(readiness: StapleWatchFactResolutionReadiness) {
        if (handoffAttempt?.accepted != true) return
        factResolutionReadiness = readiness
        renderCurrent()
    }

    private fun renderCurrent() {
        lastIdentityState?.let { state ->
            val withHandoff =
                StapleWatchSavedSelectionHandoffUiAdapter.apply(
                    state = state,
                    attempt = handoffAttempt
                )
            renderer.render(
                withHandoff.copy(
                    factResolutionProgress =
                        if (handoffAttempt?.accepted == true) {
                            factResolutionReadiness?.let(
                                StapleWatchFactResolutionUiProjector::project
                            )
                        } else {
                            null
                        }
                )
            )
        }
    }
}

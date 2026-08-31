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
 * Fact-check capability fails safe. Unless composition explicitly declares a real foreground fact
 * source configured, an otherwise-ready identity selection is presented as unavailable and carries
 * no continuation marker. This presenter does not inspect or produce facts itself.
 */
class StapleWatchSavedSelectionSurfacePresenter(
    private val renderer: StapleWatchSavedSelectionSurfaceRenderer,
    private val factCheckCapability: StapleWatchForegroundFactCheckCapability =
        StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED
) {
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
        renderer.render(
            StapleWatchSavedFactCheckCapabilityUiAdapter.apply(
                state = identityState,
                capability = factCheckCapability
            )
        )
    }
}

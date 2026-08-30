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
 */
class StapleWatchSavedSelectionSurfacePresenter(
    private val renderer: StapleWatchSavedSelectionSurfaceRenderer
) {
    fun render(
        savedState: PracticalShoppingSavedExactPreferenceState,
        selection: StapleWatchSavedIdentitySelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ) {
        renderer.render(
            StapleWatchSavedIdentitySelectionUiProjector.project(
                savedState = savedState,
                selection = selection,
                metadata = metadata
            )
        )
    }
}

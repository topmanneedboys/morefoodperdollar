package com.valuepilot.app

/**
 * Pure route-local owner for configuring Watch My Staples from already-validated Saved data.
 *
 * This session owns only temporary explicit selection, route visibility, reconciliation against
 * newer Saved snapshots, and re-presentation through the verified setup presenter. It never loads
 * Saved data, persists staple selection, resolves shopping facts, creates an identity handoff,
 * evaluates economics, schedules work, or interprets presentation readiness as authorization.
 *
 * Selection survives hide/show transitions within this session, but remains memory-only. Closing
 * the session discards it. Actions are accepted only while the setup route is visible; stale
 * actions are still reduced so the existing fail-closed reducer can reconcile removed Saved
 * identities before the current safe state is rendered again.
 */
internal class StapleWatchSavedSelectionRouteSession(
    initialSavedState: PracticalShoppingSavedExactPreferenceState,
    initialMetadata: PracticalShoppingSavedExactPreferenceDisplayMetadata,
    private val presenter: StapleWatchSavedSelectionSurfacePresenter
) : AutoCloseable {

    private var savedState = initialSavedState
    private var metadata = initialMetadata
    private var selection = StapleWatchSavedIdentitySelectionReducer.initial()
    private var routeVisible = false
    private var closed = false

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (visible) {
            selection =
                StapleWatchSavedIdentitySelectionReducer.reconcile(
                    previous = selection,
                    savedState = savedState
                )
            renderCurrent()
        }
    }

    fun onSavedSnapshotChanged(
        savedState: PracticalShoppingSavedExactPreferenceState,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ) {
        if (closed) return

        this.savedState = savedState
        this.metadata = metadata
        selection =
            StapleWatchSavedIdentitySelectionReducer.reconcile(
                previous = selection,
                savedState = savedState
            )

        if (routeVisible) {
            renderCurrent()
        }
    }

    fun onSurfaceAction(action: StapleWatchSavedIdentitySelectionAction) {
        if (closed || !routeVisible) return

        val transition =
            StapleWatchSavedIdentitySelectionReducer.reduce(
                previous = selection,
                savedState = savedState,
                action = action
            )
        selection = transition.state
        renderCurrent()
    }

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        selection = StapleWatchSavedIdentitySelectionReducer.initial()
    }

    private fun renderCurrent() {
        presenter.render(
            savedState = savedState,
            selection = selection,
            metadata = metadata
        )
    }
}

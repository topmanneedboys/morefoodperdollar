package com.valuepilot.app

/**
 * Route-local owner for explicit Saved product/store selection before observed-price prefill.
 *
 * The validated Saved snapshot is the only Saved trust boundary accepted here. This session owns
 * only memory-local selection, route visibility, reconciliation against newer validated snapshots,
 * re-presentation through the verified Saved-selection presenter, and an explicit request to the
 * already-verified Saved-prefill handoff gate.
 *
 * Saved identities are never auto-selected. Hidden or closed routes ignore selection actions and
 * cannot request prefill. Hiding preserves temporary selection for the same open route session;
 * closing discards it. Snapshot changes may only reconcile removed identities and never select
 * newly Saved identities.
 *
 * This session does not implement physical UI, interpret presentation readiness, mutate an
 * observed-price draft, navigate, read proof bytes, capture a price, generate ids or timestamps,
 * persist anything, create evidence, resolve quantity, classify freshness, rank offers, or
 * authorize current-price semantics.
 */
internal class UserObservedPriceSavedSelectionRouteSession(
    initialSnapshot: PracticalShoppingSavedValidatedSnapshot,
    private val presenter: UserObservedPriceSavedSelectionSurfacePresenter
) : AutoCloseable {

    private var snapshot = initialSnapshot
    private var selection = UserObservedPriceSavedSelectionReducer.initial()
    private var routeVisible = false
    private var closed = false

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (visible) {
            selection =
                UserObservedPriceSavedSelectionReducer.reconcile(
                    previous = selection,
                    savedState = snapshot.exactState
                )
            renderCurrent()
        }
    }

    fun onSavedSnapshotChanged(updatedSnapshot: PracticalShoppingSavedValidatedSnapshot) {
        if (closed) return

        snapshot = updatedSnapshot
        selection =
            UserObservedPriceSavedSelectionReducer.reconcile(
                previous = selection,
                savedState = snapshot.exactState
            )

        if (routeVisible) {
            renderCurrent()
        }
    }

    fun onSelectionAction(
        action: UserObservedPriceSavedSelectionAction
    ): UserObservedPriceSavedSelectionTransition? {
        if (closed || !routeVisible) return null

        val transition =
            UserObservedPriceSavedSelectionReducer.reduce(
                previous = selection,
                savedState = snapshot.exactState,
                action = action
            )
        selection = transition.state
        renderCurrent()
        return transition
    }

    fun currentSelectionOrNull(): UserObservedPriceSavedSelection? {
        if (closed || !routeVisible) return null
        return selection.copy()
    }

    fun requestPrefillOrNull(): UserObservedPriceSavedPrefillHandoffAttempt? {
        if (closed || !routeVisible) return null

        return UserObservedPriceSavedPrefillHandoffGate.request(
            selection = selection,
            snapshot = snapshot
        )
    }

    fun isVisible(): Boolean = !closed && routeVisible

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        selection = UserObservedPriceSavedSelectionReducer.initial()
    }

    private fun renderCurrent() {
        presenter.render(
            savedState = snapshot.exactState,
            selection = selection,
            metadata = snapshot.displayMetadata
        )
    }
}

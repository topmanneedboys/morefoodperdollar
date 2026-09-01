package com.valuepilot.app

/** Receives only the typed result of an explicit Saved-selection prefill request. */
internal fun interface UserObservedPriceSavedPrefillHandoffAttemptObserver {
    fun onAttempt(attempt: UserObservedPriceSavedPrefillHandoffAttempt)
}

/**
 * Pure composition boundary between validated Saved snapshots and explicit observed-price
 * product/store selection.
 *
 * This coordinator keeps only the latest already-validated Saved snapshot in memory and creates or
 * updates the verified [UserObservedPriceSavedSelectionRouteSession] while the route is visible.
 * It forwards only the typed selection actions already emitted by the immutable consumer surface.
 *
 * An identity/display-only prefill handoff may be requested only through [requestPrefillHandoff].
 * The typed [onCheckPrefillAction] entry point maps only the already-projected explicit request
 * marker to that same path and grants no additional authority. The route session remains the owner
 * of the current selection and delegates the actual eligibility check to the already-verified
 * Saved-prefill handoff gate. This coordinator emits that typed attempt unchanged and performs no
 * downstream adaptation itself.
 *
 * It does not read Saved persistence, infer identity, mutate an observed-price draft, capture
 * proof/price/time, persist evidence, resolve quantity, rank offers, navigate, or authorize
 * current-price semantics.
 *
 * Route visibility may arrive before the first validated Saved load. In that case composition stays
 * fail-closed until a snapshot arrives. Once created, the same memory-only route session is reused
 * across hide/show and reconciled whenever a newer validated snapshot arrives. Hidden or closed
 * routes ignore prefill requests, and a missing route session emits no attempt.
 */
internal class UserObservedPriceSavedSelectionCompositionCoordinator(
    private val prefillHandoffAttemptObserver: UserObservedPriceSavedPrefillHandoffAttemptObserver =
        UserObservedPriceSavedPrefillHandoffAttemptObserver { },
    private val sessionFactory:
        (PracticalShoppingSavedValidatedSnapshot) -> UserObservedPriceSavedSelectionRouteSession
) : PracticalShoppingSavedValidatedSnapshotObserver, AutoCloseable {

    private var latestSnapshot: PracticalShoppingSavedValidatedSnapshot? = null
    private var session: UserObservedPriceSavedSelectionRouteSession? = null
    private var routeVisible = false
    private var closed = false

    override fun onSnapshot(snapshot: PracticalShoppingSavedValidatedSnapshot) {
        if (closed) return

        latestSnapshot = snapshot
        val currentSession = session
        if (currentSession != null) {
            currentSession.onSavedSnapshotChanged(snapshot)
        } else if (routeVisible) {
            ensureSession(snapshot).onRouteVisibilityChanged(true)
        }
    }

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (visible) {
            latestSnapshot?.let { snapshot ->
                ensureSession(snapshot).onRouteVisibilityChanged(true)
            }
        } else {
            session?.onRouteVisibilityChanged(false)
        }
    }

    fun onSurfaceAction(action: UserObservedPriceSavedSelectionAction) {
        if (closed || !routeVisible) return

        session?.onSelectionAction(action)
    }

    fun onCheckPrefillAction(action: UserObservedPriceSavedPrefillCheckUiAction) {
        when (action) {
            UserObservedPriceSavedPrefillCheckUiAction.Request -> requestPrefillHandoff()
        }
    }

    fun requestPrefillHandoff() {
        if (closed || !routeVisible) return

        session
            ?.requestPrefillOrNull()
            ?.let(prefillHandoffAttemptObserver::onAttempt)
    }

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        latestSnapshot = null
        session?.close()
        session = null
    }

    fun isClosed(): Boolean = closed

    private fun ensureSession(
        snapshot: PracticalShoppingSavedValidatedSnapshot
    ): UserObservedPriceSavedSelectionRouteSession =
        session ?: sessionFactory(snapshot).also { created -> session = created }
}

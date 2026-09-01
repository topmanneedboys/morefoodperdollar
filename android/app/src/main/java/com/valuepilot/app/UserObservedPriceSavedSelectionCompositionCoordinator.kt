package com.valuepilot.app

/**
 * Pure composition boundary between validated Saved snapshots and explicit observed-price
 * product/store selection.
 *
 * This coordinator keeps only the latest already-validated Saved snapshot in memory and creates or
 * updates the verified [UserObservedPriceSavedSelectionRouteSession] while the route is visible.
 * It forwards only the typed selection actions already emitted by the immutable consumer surface.
 *
 * It deliberately exposes no prefill-check path. Selection composition therefore cannot turn UI
 * readiness into downstream authority. It also does not read Saved persistence, infer identity,
 * mutate an observed-price draft, capture proof/price/time, persist evidence, resolve quantity,
 * rank offers, navigate, or authorize current-price semantics.
 *
 * Route visibility may arrive before the first validated Saved load. In that case composition stays
 * fail-closed until a snapshot arrives. Once created, the same memory-only route session is reused
 * across hide/show and reconciled whenever a newer validated snapshot arrives.
 */
internal class UserObservedPriceSavedSelectionCompositionCoordinator(
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

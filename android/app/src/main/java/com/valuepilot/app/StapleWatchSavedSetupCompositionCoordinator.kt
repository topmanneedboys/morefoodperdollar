package com.valuepilot.app

/**
 * Pure composition boundary between validated Saved snapshots and Watch My Staples setup.
 *
 * This coordinator keeps only the latest already-validated Saved snapshot in memory and uses it
 * to create or update the verified [StapleWatchSavedSelectionRouteSession]. It does not read Saved
 * persistence, infer identity, resolve shopping facts, create a fact/economic handoff, evaluate
 * savings, schedule background work, or authorize notifications.
 *
 * Route visibility may arrive before the first accepted Saved load. In that case setup remains
 * fail-closed: no route session exists and surface actions are ignored until a validated snapshot
 * is observed. Once created, the same memory-only setup session is reused across hide/show within
 * this coordinator and reconciled whenever a newer validated snapshot arrives.
 */
internal class StapleWatchSavedSetupCompositionCoordinator(
    private val sessionFactory:
        (PracticalShoppingSavedValidatedSnapshot) -> StapleWatchSavedSelectionRouteSession
) : PracticalShoppingSavedValidatedSnapshotObserver, AutoCloseable {

    private var latestSnapshot: PracticalShoppingSavedValidatedSnapshot? = null
    private var session: StapleWatchSavedSelectionRouteSession? = null
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

    fun onSurfaceAction(action: StapleWatchSavedIdentitySelectionAction) {
        if (closed || !routeVisible) return

        session?.onSurfaceAction(action)
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
    ): StapleWatchSavedSelectionRouteSession =
        session ?: sessionFactory(snapshot).also { created -> session = created }
}

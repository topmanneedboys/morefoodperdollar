package com.valuepilot.app

/** Narrow Activity-owned session contract for Saved route integration. */
interface PracticalShoppingSavedRouteSession : AutoCloseable {
    fun refresh()

    fun selectAction(action: PracticalShoppingSavedExactPreferenceUiAction)

    override fun close()
}

/**
 * Pure route/action coordinator for the physical Saved surface.
 *
 * The coordinator owns only route-entry sequencing. It has no Android View, persistence,
 * provider, identity, price, travel, ranking, clock, or network logic. A session is created
 * lazily on the first real Saved-route entry and reused for later entries in the same Activity.
 *
 * Repeated shell renders while Saved remains visible do not create duplicate refreshes. Leaving
 * and re-entering Saved requests a fresh authoritative load. Surface actions are ignored while
 * Saved is hidden or after close; destructive preference actions never manufacture a session
 * before a route entry has established the authoritative lifecycle.
 */
internal class PracticalShoppingSavedRouteCoordinator(
    private val sessionFactory: () -> PracticalShoppingSavedRouteSession
) : AutoCloseable {
    private var session: PracticalShoppingSavedRouteSession? = null
    private var routeVisible = false
    private var closed = false

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        if (visible) {
            val currentSession = ensureSession()
            currentSession.refresh()
            routeVisible = true
        } else {
            routeVisible = false
        }
    }

    fun onSurfaceAction(action: PracticalShoppingSavedSurfaceAction) {
        if (closed || !routeVisible) return

        when (action) {
            PracticalShoppingSavedSurfaceAction.Refresh -> ensureSession().refresh()
            is PracticalShoppingSavedSurfaceAction.Preference ->
                session?.selectAction(action.action)
        }
    }

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        session?.close()
        session = null
    }

    private fun ensureSession(): PracticalShoppingSavedRouteSession =
        session ?: sessionFactory().also { created -> session = created }
}

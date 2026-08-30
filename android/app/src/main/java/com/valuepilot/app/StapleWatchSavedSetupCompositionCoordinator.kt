package com.valuepilot.app

/** Receives only an explicit identity-handoff gate result; it owns no downstream work itself. */
internal fun interface StapleWatchSavedIdentityHandoffAttemptObserver {
    fun onAttempt(attempt: StapleWatchSavedIdentityHandoffAttempt)
}

/** Receives only an accepted unresolved fact-check intent; it performs no fact resolution itself. */
internal fun interface StapleWatchFactCheckIntentObserver {
    fun onIntent(intent: StapleWatchFactCheckIntent)
}

/**
 * Pure composition boundary between validated Saved snapshots and Watch My Staples setup.
 *
 * This coordinator keeps only the latest already-validated Saved snapshot in memory and uses it
 * to create or update the verified [StapleWatchSavedSelectionRouteSession]. It does not read Saved
 * persistence, infer identity, resolve shopping facts, evaluate savings, schedule background work,
 * or authorize notifications.
 *
 * An identity-only handoff may be requested only through [requestIdentityHandoff]. That explicit
 * call reads the current visible route selection and delegates all eligibility/display-safety
 * decisions to the verified [StapleWatchSavedIdentityHandoffGate]. A successful gate result is then
 * adapted into the verified unresolved [StapleWatchFactCheckIntent]; rejected attempts emit no fact
 * intent. Merely selecting enough items or receiving a newer Saved snapshot never emits either.
 *
 * Route visibility may arrive before the first accepted Saved load. In that case setup remains
 * fail-closed: no route session exists and surface actions or handoff requests are ignored until a
 * validated snapshot is observed. Once created, the same memory-only setup session is reused
 * across hide/show within this coordinator and reconciled whenever a newer validated snapshot
 * arrives.
 */
internal class StapleWatchSavedSetupCompositionCoordinator(
    private val handoffAttemptObserver: StapleWatchSavedIdentityHandoffAttemptObserver =
        StapleWatchSavedIdentityHandoffAttemptObserver { },
    private val factCheckIntentObserver: StapleWatchFactCheckIntentObserver =
        StapleWatchFactCheckIntentObserver { },
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

    fun requestIdentityHandoff() {
        if (closed || !routeVisible) return

        val snapshot = latestSnapshot ?: return
        val currentSelection = session?.currentSelectionOrNull() ?: return
        val attempt =
            StapleWatchSavedIdentityHandoffGate.request(
                selection = currentSelection,
                snapshot = snapshot
            )
        handoffAttemptObserver.onAttempt(attempt)
        StapleWatchSavedFactCheckIntentAdapter.from(attempt)?.let(factCheckIntentObserver::onIntent)
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

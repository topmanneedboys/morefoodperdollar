package com.valuepilot.app

import com.valuepilot.core.Money

/** Requests the Saved-owned observed-price confirmation draft subroute. */
internal fun interface UserObservedPriceConfirmationDraftRouteOpenObserver {
    fun onOpenRequested()
}

/**
 * Shell adapter that may open the confirmation draft only from the observed-price Saved selection
 * route. It owns no draft, prefill, proof, price, persistence, or Android View behavior.
 */
internal class UserObservedPriceConfirmationDraftRouteShellAdapter(
    private val currentRoute: () -> AppRoute,
    private val emitIntent: (AppShellIntent) -> Unit
) : UserObservedPriceConfirmationDraftRouteOpenObserver {

    override fun onOpenRequested() {
        if (currentRoute() != AppRoute.OBSERVED_PRICE_SAVED_SELECTION) return
        emitIntent(AppShellIntent.OpenObservedPriceConfirmationDraft)
    }
}

/**
 * Narrow composition owner for the transition from one accepted Saved identity handoff into one
 * route-local observed-price confirmation draft.
 *
 * An accepted handoff is adapted only to GTIN, product name, and exact store scope by the existing
 * identity adapter. Rejected attempts fail closed and cannot request navigation. The confirmation
 * draft session is not created until the shell confirms that the draft route is visible. At that
 * point a fresh route-local session is created and receives only the identity prefill.
 *
 * While that exact route remains visible, an explicit typed [Money] emitted by the separate manual
 * price-input adapter, an explicit artifact-reference/proof-type pair emitted by the separate
 * non-byte proof-reference surface, and an explicit observed-at epoch millisecond value emitted by
 * a separate time-input adapter may be forwarded into the active draft. The first such explicit
 * observation fact lazily starts the route-local observation-record lifecycle by asking the
 * injected [UserObservedPriceObservationIdSource] for one opaque ID. That ID is applied once to the
 * active draft and reused for the remainder of that route session. Merely opening the route does
 * not allocate an observation identity, and leaving the route discards the route-local allocation.
 *
 * The coordinator never chooses, parses, defaults, or infers price currency, civil time, UTC
 * offset, or proof facts. Hidden/closed routes and route states with no active draft session fail
 * closed. It does not implement the opaque-ID mechanism itself and never creates confirmation IDs
 * or confirmation timestamps, reads a clock, supplies proof bytes, fingerprints or stores proof,
 * submits, persists, creates evidence, ranks offers, or authorizes current-price semantics.
 */
internal class UserObservedPriceSavedConfirmationDraftRouteCoordinator(
    private val routeOpenObserver: UserObservedPriceConfirmationDraftRouteOpenObserver,
    private val sessionFactory: () -> UserObservedPriceConfirmationDraftRouteSession,
    private val observationIdSource: UserObservedPriceObservationIdSource =
        LocalUserObservedPriceObservationIdSource
) : UserObservedPriceSavedPrefillHandoffAttemptObserver, AutoCloseable {

    private var pendingPrefill: UserObservedPriceConfirmationDraftIdentityPrefill? = null
    private var session: UserObservedPriceConfirmationDraftRouteSession? = null
    private var observationReferenceAssigned = false
    private var routeVisible = false
    private var closed = false

    override fun onAttempt(attempt: UserObservedPriceSavedPrefillHandoffAttempt) {
        if (closed) return

        val prefill =
            UserObservedPriceSavedConfirmationDraftIdentityPrefillAdapter.adapt(attempt)
                ?: return

        pendingPrefill = prefill
        routeOpenObserver.onOpenRequested()
    }

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (!visible) {
            pendingPrefill = null
            session?.close()
            session = null
            observationReferenceAssigned = false
            return
        }

        val prefill = pendingPrefill ?: return
        session?.close()

        val created = sessionFactory()
        session = created
        observationReferenceAssigned = false
        created.onRouteVisibilityChanged(true)
        created.onIdentityPrefill(prefill)
        pendingPrefill = null
    }

    fun onPriceInput(price: Money) {
        if (closed || !routeVisible) return
        val activeSession = session ?: return
        ensureObservationReference(activeSession)
        activeSession.onPriceChanged(price)
    }

    fun onProofReferenceInput(
        artifactId: String,
        proofType: UserProvidedPriceProofType
    ) {
        if (closed || !routeVisible) return
        val activeSession = session ?: return
        ensureObservationReference(activeSession)
        activeSession.onArtifactReferenceChanged(
            artifactId = artifactId,
            proofType = proofType
        )
    }

    fun onObservedAtInput(observedAtEpochMillis: Long) {
        if (closed || !routeVisible) return
        val activeSession = session ?: return
        ensureObservationReference(activeSession)
        activeSession.onObservedAtChanged(observedAtEpochMillis)
    }

    fun isVisible(): Boolean = !closed && routeVisible

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return

        closed = true
        routeVisible = false
        pendingPrefill = null
        session?.close()
        session = null
        observationReferenceAssigned = false
    }

    private fun ensureObservationReference(
        activeSession: UserObservedPriceConfirmationDraftRouteSession
    ) {
        if (observationReferenceAssigned) return

        activeSession.onObservationReferenceChanged(observationIdSource.nextObservationId())
        observationReferenceAssigned = true
    }
}

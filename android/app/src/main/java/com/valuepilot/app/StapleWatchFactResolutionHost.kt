package com.valuepilot.app

/** Receives completed exact Watch evidence preconditions only; owns no evaluation or delivery. */
internal fun interface StapleWatchEconomicEvidencePreconditionsObserver {
    fun onPreconditions(preconditions: StapleWatchEconomicEvidencePreconditions)
}

/** Receives bounded readiness progress for the current foreground fact-resolution session. */
internal fun interface StapleWatchFactResolutionReadinessObserver {
    fun onReadiness(readiness: StapleWatchFactResolutionReadiness)
}

/**
 * Foreground in-memory owner for one active Watch fact-resolution session.
 *
 * The host implements the existing unresolved fact-check intent observer so it can be wired behind
 * the explicit Saved setup continuation without changing that setup boundary. A newly accepted
 * intent replaces any prior in-memory session with a fresh verified readiness snapshot.
 * Already-authoritative fact objects may then be handed in explicitly and are delegated unchanged
 * to [StapleWatchFactResolutionSession].
 *
 * When one successful fact transition completes all five authoritative categories, the host emits
 * only the exact evidence-precondition object minted by that session. Reapplying an already-retained
 * exact fact object is idempotent and cannot emit a duplicate. Policy choice, economics, display
 * metadata, projection, rendering and delivery remain separate downstream boundaries.
 *
 * This host owns sequencing only. It performs no fact acquisition, provider access, clock reads,
 * persistence, economic evaluation, UI projection, rendering, background scheduling, delivery, or
 * notification work.
 */
internal class StapleWatchFactResolutionHost(
    private val preconditionsObserver: StapleWatchEconomicEvidencePreconditionsObserver =
        StapleWatchEconomicEvidencePreconditionsObserver { },
    private val readinessObserver: StapleWatchFactResolutionReadinessObserver =
        StapleWatchFactResolutionReadinessObserver { }
) : StapleWatchFactCheckIntentObserver, AutoCloseable {

    private var session: StapleWatchFactResolutionSession? = null
    private var closed = false

    @Synchronized
    override fun onIntent(intent: StapleWatchFactCheckIntent) {
        if (closed) return
        session = StapleWatchFactResolutionSession.start(intent).also { started ->
            readinessObserver.onReadiness(started.readiness)
        }
    }

    @Synchronized
    fun currentSessionOrNull(): StapleWatchFactResolutionSession? = session

    @Synchronized
    fun accept(facts: StapleWatchAlternativeStoreIdentityFacts) {
        update { current -> current.accept(facts) }
    }

    @Synchronized
    fun accept(facts: StapleWatchUsualStoreBasketPriceFacts) {
        update { current -> current.accept(facts) }
    }

    @Synchronized
    fun accept(facts: StapleWatchAlternativeStoreBasketPriceFacts) {
        update { current -> current.accept(facts) }
    }

    @Synchronized
    fun accept(facts: StapleWatchAlternativeAdditionalTravelFacts) {
        update { current -> current.accept(facts) }
    }

    @Synchronized
    fun accept(facts: StapleWatchEvidenceCurrentnessFacts) {
        update { current -> current.accept(facts) }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        session = null
    }

    @Synchronized
    fun isClosed(): Boolean = closed

    private inline fun update(
        transform: (StapleWatchFactResolutionSession) -> StapleWatchFactResolutionSession
    ) {
        if (closed) return
        val current = session ?: return
        val next = transform(current)
        if (next === current) return

        session = next
        readinessObserver.onReadiness(next.readiness)
        next.economicPreconditionsOrNull()?.let(preconditionsObserver::onPreconditions)
    }
}

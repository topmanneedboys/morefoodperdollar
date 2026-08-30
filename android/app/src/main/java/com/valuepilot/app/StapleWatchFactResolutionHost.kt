package com.valuepilot.app

/**
 * Foreground in-memory owner for one active Watch fact-resolution session.
 *
 * The host implements the existing unresolved fact-check intent observer so it can later be wired
 * behind the explicit Saved setup continuation without changing that setup boundary. A newly
 * accepted intent replaces any prior in-memory session with a fresh verified readiness snapshot.
 * Already-authoritative fact objects may then be handed in explicitly and are delegated unchanged
 * to [StapleWatchFactResolutionSession].
 *
 * This host owns sequencing only. It performs no fact acquisition, provider access, clock reads,
 * persistence, economic evaluation, UI projection, rendering, background scheduling, delivery, or
 * notification work. Consumers that need completed economic preconditions must read the current
 * immutable session and use its verified handoff separately.
 */
internal class StapleWatchFactResolutionHost : StapleWatchFactCheckIntentObserver, AutoCloseable {

    private var session: StapleWatchFactResolutionSession? = null
    private var closed = false

    @Synchronized
    override fun onIntent(intent: StapleWatchFactCheckIntent) {
        if (closed) return
        session = StapleWatchFactResolutionSession.start(intent)
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
        session = transform(current)
    }
}

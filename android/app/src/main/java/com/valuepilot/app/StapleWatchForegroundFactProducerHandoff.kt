package com.valuepilot.app

/**
 * Sink for already-authoritative fact objects produced for one foreground Staple Watch check.
 *
 * Implementations must preserve the exact fact objects. This contract grants no authority to mint
 * facts, choose providers, evaluate economics, persist state, schedule work, or notify the user.
 */
internal interface StapleWatchForegroundFactSink {
    fun accept(facts: StapleWatchAlternativeStoreIdentityFacts)

    fun accept(facts: StapleWatchUsualStoreBasketPriceFacts)

    fun accept(facts: StapleWatchAlternativeStoreBasketPriceFacts)

    fun accept(facts: StapleWatchAlternativeAdditionalTravelFacts)

    fun accept(facts: StapleWatchEvidenceCurrentnessFacts)
}

/**
 * Replaceable foreground producer for the authoritative facts required by one exact Watch intent.
 *
 * The producer receives the exact intent only after the resolution host has started its matching
 * session. It may hand already-authoritative facts to [sink] and returns one close handle for any
 * producer-local work. This contract itself provides no network, clock, persistence, background,
 * economic, rendering, delivery, or notification capability.
 */
internal fun interface StapleWatchForegroundFactProducer {
    fun begin(
        intent: StapleWatchFactCheckIntent,
        sink: StapleWatchForegroundFactSink
    ): AutoCloseable
}

/**
 * Foreground lifecycle handoff between an explicit fact-check intent, one replaceable fact producer,
 * and the existing exact [StapleWatchFactResolutionHost].
 *
 * A new intent closes the previous producer handle, starts a fresh host session first, then invokes
 * the producer with that exact intent. This ordering allows a synchronous producer to return facts
 * immediately without racing an uninitialized host session.
 *
 * Producer callbacks are forwarded only when each fact retains the exact active intent object.
 * Late callbacks from a replaced or closed producer are ignored rather than being allowed to reach
 * the host's fail-closed foreign-fact assertion. The handoff never inspects fact payloads and never
 * constructs or transforms a fact object.
 *
 * Closing this handoff closes only its current producer handle and forgets the active intent. The
 * shell remains the owner of closing the independently composed resolution host.
 */
internal class StapleWatchForegroundFactProducerHandoff(
    private val resolutionHost: StapleWatchFactResolutionHost,
    private val producer: StapleWatchForegroundFactProducer
) : StapleWatchFactCheckIntentObserver, StapleWatchForegroundFactSink, AutoCloseable {

    private var activeIntent: StapleWatchFactCheckIntent? = null
    private var activeProducerHandle: AutoCloseable? = null
    private var closed = false

    override fun onIntent(intent: StapleWatchFactCheckIntent) {
        if (closed) return

        activeProducerHandle?.close()
        activeProducerHandle = null
        activeIntent = intent
        resolutionHost.onIntent(intent)

        val startedHandle = producer.begin(intent, this)
        if (closed || activeIntent !== intent) {
            startedHandle.close()
        } else {
            activeProducerHandle = startedHandle
        }
    }

    override fun accept(facts: StapleWatchAlternativeStoreIdentityFacts) {
        forward(facts.intent) { resolutionHost.accept(facts) }
    }

    override fun accept(facts: StapleWatchUsualStoreBasketPriceFacts) {
        forward(facts.intent) { resolutionHost.accept(facts) }
    }

    override fun accept(facts: StapleWatchAlternativeStoreBasketPriceFacts) {
        forward(facts.intent) { resolutionHost.accept(facts) }
    }

    override fun accept(facts: StapleWatchAlternativeAdditionalTravelFacts) {
        forward(facts.intent) { resolutionHost.accept(facts) }
    }

    override fun accept(facts: StapleWatchEvidenceCurrentnessFacts) {
        forward(facts.intent) { resolutionHost.accept(facts) }
    }

    fun activeIntentOrNull(): StapleWatchFactCheckIntent? = activeIntent

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return

        closed = true
        activeIntent = null
        activeProducerHandle?.close()
        activeProducerHandle = null
    }

    private inline fun forward(
        factIntent: StapleWatchFactCheckIntent,
        accept: () -> Unit
    ) {
        if (closed || factIntent !== activeIntent) return
        accept()
    }
}

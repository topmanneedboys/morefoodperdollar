package com.valuepilot.app

/** Produces resolved alternative-store identities for one exact foreground Watch intent. */
internal fun interface StapleWatchAlternativeStoreIdentityFactProducer {
    fun begin(
        intent: StapleWatchFactCheckIntent,
        observer: (StapleWatchAlternativeStoreIdentityFacts) -> Unit
    ): AutoCloseable
}

/** Produces usual-store basket-price facts for one exact foreground Watch intent. */
internal fun interface StapleWatchUsualStorePriceFactProducer {
    fun begin(
        intent: StapleWatchFactCheckIntent,
        observer: (StapleWatchUsualStoreBasketPriceFacts) -> Unit
    ): AutoCloseable
}

/** Produces alternative-store basket-price facts from the exact resolved identity facts. */
internal fun interface StapleWatchAlternativeStorePriceFactProducer {
    fun begin(
        identityFacts: StapleWatchAlternativeStoreIdentityFacts,
        observer: (StapleWatchAlternativeStoreBasketPriceFacts) -> Unit
    ): AutoCloseable
}

/** Produces additional-travel facts from the exact resolved alternative-store identities. */
internal fun interface StapleWatchAlternativeTravelFactProducer {
    fun begin(
        identityFacts: StapleWatchAlternativeStoreIdentityFacts,
        observer: (StapleWatchAlternativeAdditionalTravelFacts) -> Unit
    ): AutoCloseable
}

/** Produces currentness only from the exact already-produced price-fact objects. */
internal fun interface StapleWatchEvidenceCurrentnessFactProducer {
    fun begin(
        usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts,
        alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts,
        observer: (StapleWatchEvidenceCurrentnessFacts) -> Unit
    ): AutoCloseable
}

/**
 * Dependency-aware composition of replaceable foreground Watch fact producers.
 *
 * This coordinator owns sequencing only:
 *
 * 1. alternative-store identity and usual-store price production may begin from the exact intent;
 * 2. exact identity facts unlock alternative-store price and additional-travel production;
 * 3. the exact usual + alternative price fact objects unlock evidence-currentness production.
 *
 * Every fact is forwarded unchanged to [StapleWatchForegroundFactSink]. Dependent facts must retain
 * the exact upstream fact objects that unlocked their producer. A producer may re-emit the exact same
 * object idempotently, but replacing a category with a detached object fails closed.
 *
 * This class does not discover stores, acquire routes/prices, choose providers, read a clock, create
 * evidence, evaluate economics, persist state, schedule background work, render UI, or notify users.
 * Those authorities remain with the replaceable fact producers and the already-existing downstream
 * boundaries.
 */
internal class StapleWatchForegroundFactProductionCoordinator(
    private val identityProducer: StapleWatchAlternativeStoreIdentityFactProducer,
    private val usualStorePriceProducer: StapleWatchUsualStorePriceFactProducer,
    private val alternativeStorePriceProducer: StapleWatchAlternativeStorePriceFactProducer,
    private val alternativeTravelProducer: StapleWatchAlternativeTravelFactProducer,
    private val currentnessProducer: StapleWatchEvidenceCurrentnessFactProducer
) : StapleWatchForegroundFactProducer {

    override fun begin(
        intent: StapleWatchFactCheckIntent,
        sink: StapleWatchForegroundFactSink
    ): AutoCloseable {
        val session =
            Session(
                intent = intent,
                sink = sink,
                identityProducer = identityProducer,
                usualStorePriceProducer = usualStorePriceProducer,
                alternativeStorePriceProducer = alternativeStorePriceProducer,
                alternativeTravelProducer = alternativeTravelProducer,
                currentnessProducer = currentnessProducer
            )
        try {
            session.start()
            return session
        } catch (failure: Throwable) {
            session.close()
            throw failure
        }
    }

    private class Session(
        private val intent: StapleWatchFactCheckIntent,
        private val sink: StapleWatchForegroundFactSink,
        private val identityProducer: StapleWatchAlternativeStoreIdentityFactProducer,
        private val usualStorePriceProducer: StapleWatchUsualStorePriceFactProducer,
        private val alternativeStorePriceProducer: StapleWatchAlternativeStorePriceFactProducer,
        private val alternativeTravelProducer: StapleWatchAlternativeTravelFactProducer,
        private val currentnessProducer: StapleWatchEvidenceCurrentnessFactProducer
    ) : AutoCloseable {

        private var identityFacts: StapleWatchAlternativeStoreIdentityFacts? = null
        private var usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts? = null
        private var alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts? = null
        private var travelFacts: StapleWatchAlternativeAdditionalTravelFacts? = null
        private var currentnessFacts: StapleWatchEvidenceCurrentnessFacts? = null

        private var identityHandle: AutoCloseable? = null
        private var usualStorePriceHandle: AutoCloseable? = null
        private var alternativeStorePriceHandle: AutoCloseable? = null
        private var alternativeTravelHandle: AutoCloseable? = null
        private var currentnessHandle: AutoCloseable? = null

        private var dependentIdentityStagesStarted = false
        private var currentnessStageStarted = false
        private var closed = false

        fun start() {
            if (closed) return

            installIdentityHandle(
                identityProducer.begin(intent, ::onIdentityFacts)
            )
            installUsualStorePriceHandle(
                usualStorePriceProducer.begin(intent, ::onUsualStorePriceFacts)
            )
        }

        private fun onIdentityFacts(facts: StapleWatchAlternativeStoreIdentityFacts) {
            if (!accepts(facts.intent)) return

            val existing = identityFacts
            if (existing != null) {
                require(existing === facts) {
                    "Foreground Watch identity facts are single-assignment for one producer session"
                }
                sink.accept(facts)
                return
            }

            sink.accept(facts)
            identityFacts = facts
            startIdentityDependentStages(facts)
        }

        private fun onUsualStorePriceFacts(facts: StapleWatchUsualStoreBasketPriceFacts) {
            if (!accepts(facts.intent)) return

            val existing = usualStorePriceFacts
            if (existing != null) {
                require(existing === facts) {
                    "Foreground Watch usual-store price facts are single-assignment for one producer session"
                }
                sink.accept(facts)
                return
            }

            sink.accept(facts)
            usualStorePriceFacts = facts
            maybeStartCurrentnessStage()
        }

        private fun onAlternativeStorePriceFacts(facts: StapleWatchAlternativeStoreBasketPriceFacts) {
            if (!accepts(facts.intent)) return

            val identities = requireNotNull(identityFacts) {
                "Alternative Watch prices cannot arrive before exact identity facts"
            }
            require(facts.identityFacts === identities) {
                "Alternative Watch prices must retain the exact identity facts that unlocked production"
            }

            val existing = alternativeStorePriceFacts
            if (existing != null) {
                require(existing === facts) {
                    "Foreground Watch alternative price facts are single-assignment for one producer session"
                }
                sink.accept(facts)
                return
            }

            sink.accept(facts)
            alternativeStorePriceFacts = facts
            maybeStartCurrentnessStage()
        }

        private fun onTravelFacts(facts: StapleWatchAlternativeAdditionalTravelFacts) {
            if (!accepts(facts.intent)) return

            val identities = requireNotNull(identityFacts) {
                "Alternative Watch travel cannot arrive before exact identity facts"
            }
            require(facts.identityFacts === identities) {
                "Alternative Watch travel must retain the exact identity facts that unlocked production"
            }

            val existing = travelFacts
            if (existing != null) {
                require(existing === facts) {
                    "Foreground Watch travel facts are single-assignment for one producer session"
                }
                sink.accept(facts)
                return
            }

            sink.accept(facts)
            travelFacts = facts
        }

        private fun onCurrentnessFacts(facts: StapleWatchEvidenceCurrentnessFacts) {
            if (!accepts(facts.intent)) return

            val usualPrices = requireNotNull(usualStorePriceFacts)
            val alternativePrices = requireNotNull(alternativeStorePriceFacts)
            require(facts.usualStorePriceFacts === usualPrices) {
                "Watch currentness must retain the exact usual-store price facts that unlocked production"
            }
            require(facts.alternativeStorePriceFacts === alternativePrices) {
                "Watch currentness must retain the exact alternative price facts that unlocked production"
            }

            val existing = currentnessFacts
            if (existing != null) {
                require(existing === facts) {
                    "Foreground Watch currentness facts are single-assignment for one producer session"
                }
                sink.accept(facts)
                return
            }

            sink.accept(facts)
            currentnessFacts = facts
        }

        private fun startIdentityDependentStages(
            facts: StapleWatchAlternativeStoreIdentityFacts
        ) {
            if (closed || dependentIdentityStagesStarted) return
            dependentIdentityStagesStarted = true

            installAlternativeStorePriceHandle(
                alternativeStorePriceProducer.begin(facts, ::onAlternativeStorePriceFacts)
            )
            installAlternativeTravelHandle(
                alternativeTravelProducer.begin(facts, ::onTravelFacts)
            )
        }

        private fun maybeStartCurrentnessStage() {
            if (closed || currentnessStageStarted) return
            val usualPrices = usualStorePriceFacts ?: return
            val alternativePrices = alternativeStorePriceFacts ?: return

            currentnessStageStarted = true
            installCurrentnessHandle(
                currentnessProducer.begin(
                    usualStorePriceFacts = usualPrices,
                    alternativeStorePriceFacts = alternativePrices,
                    observer = ::onCurrentnessFacts
                )
            )
        }

        private fun installIdentityHandle(handle: AutoCloseable) {
            if (closed) handle.close() else identityHandle = handle
        }

        private fun installUsualStorePriceHandle(handle: AutoCloseable) {
            if (closed) handle.close() else usualStorePriceHandle = handle
        }

        private fun installAlternativeStorePriceHandle(handle: AutoCloseable) {
            if (closed) handle.close() else alternativeStorePriceHandle = handle
        }

        private fun installAlternativeTravelHandle(handle: AutoCloseable) {
            if (closed) handle.close() else alternativeTravelHandle = handle
        }

        private fun installCurrentnessHandle(handle: AutoCloseable) {
            if (closed) handle.close() else currentnessHandle = handle
        }

        private fun accepts(factIntent: StapleWatchFactCheckIntent): Boolean =
            !closed && factIntent === intent

        override fun close() {
            if (closed) return
            closed = true

            currentnessHandle?.close()
            alternativeTravelHandle?.close()
            alternativeStorePriceHandle?.close()
            usualStorePriceHandle?.close()
            identityHandle?.close()

            currentnessHandle = null
            alternativeTravelHandle = null
            alternativeStorePriceHandle = null
            usualStorePriceHandle = null
            identityHandle = null
        }
    }
}

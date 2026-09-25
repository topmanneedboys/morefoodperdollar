package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionPriceStoreScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchForegroundFactProducerHandoffTest {

    private val productionFixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-producer-usual-111111")
    private val alternative = ShoppingStoreKey("opaque-producer-alt-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun `host session starts before producer and all exact fact categories forward unchanged`() {
        val emitted = mutableListOf<StapleWatchEconomicEvidencePreconditions>()
        val host =
            StapleWatchFactResolutionHost(
                preconditionsObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { preconditions ->
                        emitted += preconditions
                    }
            )
        val facts = blockedFacts(intent)
        val handle = CloseTracker()
        var hostWasReadyInsideProducer = false
        val handoff =
            StapleWatchForegroundFactProducerHandoff(
                resolutionHost = host,
                producer =
                    StapleWatchForegroundFactProducer { requestedIntent, sink ->
                        assertSame(intent, requestedIntent)
                        hostWasReadyInsideProducer =
                            host.currentSessionOrNull()?.intent === requestedIntent

                        sink.accept(facts.currentness)
                        sink.accept(facts.travel)
                        sink.accept(facts.alternativePrices)
                        sink.accept(facts.usualPrices)
                        sink.accept(facts.identities)
                        handle
                    }
            )

        handoff.onIntent(intent)

        assertTrue(hostWasReadyInsideProducer)
        assertSame(intent, handoff.activeIntentOrNull())
        assertEquals(1, emitted.size)
        val preconditions = emitted.single()
        assertSame(facts.identities, preconditions.identityFacts)
        assertSame(facts.usualPrices, preconditions.usualStorePriceFacts)
        assertSame(facts.alternativePrices, preconditions.alternativeStorePriceFacts)
        assertSame(facts.travel, preconditions.additionalTravelFacts)
        assertSame(facts.currentness, preconditions.currentnessFacts)
        assertFalse(handle.closed)
    }

    @Test
    fun `new intent closes prior producer and late prior facts are ignored`() {
        val host = StapleWatchFactResolutionHost()
        val invocations = mutableListOf<ProducerInvocation>()
        val handoff =
            StapleWatchForegroundFactProducerHandoff(
                resolutionHost = host,
                producer =
                    StapleWatchForegroundFactProducer { requestedIntent, sink ->
                        CloseTracker().also { handle ->
                            invocations += ProducerInvocation(requestedIntent, sink, handle)
                        }
                    }
            )
        val priorFacts = identityFacts(intent)
        handoff.onIntent(intent)

        val nextIntent =
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk, eggs)),
                usualStoreKey = ShoppingStoreKey("opaque-producer-next-usual-333333")
            )
        handoff.onIntent(nextIntent)

        assertEquals(2, invocations.size)
        assertTrue(invocations[0].handle.closed)
        assertFalse(invocations[1].handle.closed)
        assertSame(nextIntent, handoff.activeIntentOrNull())
        val freshSession = requireNotNull(host.currentSessionOrNull())
        assertSame(nextIntent, freshSession.intent)
        assertEquals(nextIntent.requirements, freshSession.readiness.unresolvedRequirements)

        invocations[0].sink.accept(priorFacts)

        assertSame(freshSession, host.currentSessionOrNull())
        assertEquals(nextIntent.requirements, freshSession.readiness.unresolvedRequirements)

        val currentFacts = identityFacts(nextIntent)
        invocations[1].sink.accept(currentFacts)
        val progressed = requireNotNull(host.currentSessionOrNull())
        assertSame(currentFacts, progressed.identityFacts)
        assertFalse(
            progressed.readiness.unresolvedRequirements.contains(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES
            )
        )
    }

    @Test
    fun `structurally equal detached prior intent cannot cross a fresh producer lifecycle`() {
        val host = StapleWatchFactResolutionHost()
        val handoff =
            StapleWatchForegroundFactProducerHandoff(
                resolutionHost = host,
                producer = StapleWatchForegroundFactProducer { _, _ -> CloseTracker() }
            )
        val detachedEqualIntent =
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk, eggs)),
                usualStoreKey = usual
            )
        assertEquals(intent, detachedEqualIntent)
        assertFalse(intent === detachedEqualIntent)

        handoff.onIntent(detachedEqualIntent)
        val session = requireNotNull(host.currentSessionOrNull())
        handoff.accept(identityFacts(intent))

        assertSame(session, host.currentSessionOrNull())
        assertEquals(detachedEqualIntent.requirements, session.readiness.unresolvedRequirements)
        assertNull(session.identityFacts)
    }

    @Test
    fun `close cancels active producer and suppresses later facts and intents without closing host`() {
        val host = StapleWatchFactResolutionHost()
        val invocations = mutableListOf<ProducerInvocation>()
        val handoff =
            StapleWatchForegroundFactProducerHandoff(
                resolutionHost = host,
                producer =
                    StapleWatchForegroundFactProducer { requestedIntent, sink ->
                        CloseTracker().also { handle ->
                            invocations += ProducerInvocation(requestedIntent, sink, handle)
                        }
                    }
            )
        handoff.onIntent(intent)
        val retainedSession = requireNotNull(host.currentSessionOrNull())
        val retainedFacts = identityFacts(intent)

        handoff.close()

        assertTrue(handoff.isClosed())
        assertNull(handoff.activeIntentOrNull())
        assertTrue(invocations.single().handle.closed)
        assertFalse(host.isClosed())
        assertSame(retainedSession, host.currentSessionOrNull())

        invocations.single().sink.accept(retainedFacts)
        assertSame(retainedSession, host.currentSessionOrNull())
        assertNull(retainedSession.identityFacts)

        val nextIntent =
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk, eggs)),
                usualStoreKey = ShoppingStoreKey("opaque-after-close-usual-444444")
            )
        handoff.onIntent(nextIntent)
        assertEquals(1, invocations.size)
        assertSame(retainedSession, host.currentSessionOrNull())

        handoff.close()
        assertTrue(invocations.single().handle.closed)
    }

    @Test
    fun `handoff owns only exact foreground producer lifecycle and fact forwarding`() {
        val source = source("StapleWatchForegroundFactProducerHandoff.kt").readText()

        assertTrue(source.contains("resolutionHost.onIntent(intent)"))
        assertTrue(source.contains("producer.begin(intent, this)"))
        assertTrue(source.contains("factIntent !== activeIntent"))
        assertTrue(source.contains("activeProducerHandle?.close()"))
        assertTrue(source.contains("resolutionHost.accept(facts)"))

        listOf(
            "PracticalShoppingProduction",
            "ProductionCurrentPrice",
            "EvidenceProvider",
            "OpenPrices",
            "OpenStreetMap",
            "OpenFoodFacts",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "StapleWatchEconomicEvidencePreconditions.evaluate(",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecisionCoordinator",
            "StapleWatchForegroundEvaluationCoordinator",
            "StapleWatchPolicy(",
            "StapleWatchUiProjector",
            "StapleWatchSurfacePresenter",
            "renderer.render",
            "android."
        ).forEach { forbidden ->
            assertFalse("Fact-producer handoff must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun identityFacts(
        factIntent: StapleWatchFactCheckIntent
    ): StapleWatchAlternativeStoreIdentityFacts =
        StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
            intent = factIntent,
            alternativeStoreKeys = listOf(alternative)
        )

    private fun blockedFacts(factIntent: StapleWatchFactCheckIntent): FactBundle {
        val identities =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = factIntent,
                alternativeStoreKeys = emptyList()
            )
        val registries = productionFixture.registries(emptyList())
        val usualPrices =
            StapleWatchUsualStoreBasketPriceFacts.resolve(
                intent = factIntent,
                store = storeScope(factIntent.usualStoreKey),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = productionFixture.evaluatedAtEpochMillis,
                acceptancePolicy = productionFixture.acceptancePolicy
            )
        val alternativePrices =
            StapleWatchAlternativeStoreBasketPriceFacts.resolve(
                identityFacts = identities,
                stores = emptyList(),
                priceBindings = emptyList(),
                priceRequests = emptyList(),
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = productionFixture.evaluatedAtEpochMillis,
                acceptancePolicy = productionFixture.acceptancePolicy
            )
        val travel =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identities,
                additionalTravelByStore = emptyMap()
            )
        val currentness =
            StapleWatchEvidenceCurrentnessFacts.resolve(
                usualStorePriceFacts = usualPrices,
                alternativeStorePriceFacts = alternativePrices,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                evaluatedAtEpochMillis = productionFixture.evaluatedAtEpochMillis,
                acceptancePolicy = productionFixture.acceptancePolicy
            )

        return FactBundle(
            identities = identities,
            usualPrices = usualPrices,
            alternativePrices = alternativePrices,
            travel = travel,
            currentness = currentness
        )
    }

    private fun storeScope(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = "merchant-${storeKey.value}",
            locationKey = "location-${storeKey.value}",
            commerceChannelKey = "IN_STORE"
        )

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }

    private class CloseTracker : AutoCloseable {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }

    private data class ProducerInvocation(
        val intent: StapleWatchFactCheckIntent,
        val sink: StapleWatchForegroundFactSink,
        val handle: CloseTracker
    )

    private data class FactBundle(
        val identities: StapleWatchAlternativeStoreIdentityFacts,
        val usualPrices: StapleWatchUsualStoreBasketPriceFacts,
        val alternativePrices: StapleWatchAlternativeStoreBasketPriceFacts,
        val travel: StapleWatchAlternativeAdditionalTravelFacts,
        val currentness: StapleWatchEvidenceCurrentnessFacts
    )
}

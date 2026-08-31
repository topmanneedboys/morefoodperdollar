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

class StapleWatchForegroundFactProductionCoordinatorTest {

    private val productionFixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-composition-usual-111111")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun `identity unlocks only its dependents and exact two price facts unlock currentness`() {
        val facts = blockedFacts(intent)
        val harness = ProducerHarness(intent)
        val sink = RecordingSink()
        val coordinator = harness.coordinator()

        val session = coordinator.begin(intent, sink)

        assertEquals(1, harness.identityStarts)
        assertEquals(1, harness.usualPriceStarts)
        assertEquals(0, harness.alternativePriceStarts)
        assertEquals(0, harness.travelStarts)
        assertEquals(0, harness.currentnessStarts)

        harness.emitIdentity(facts.identities)

        assertEquals(1, harness.alternativePriceStarts)
        assertEquals(1, harness.travelStarts)
        assertSame(facts.identities, harness.alternativePriceInput)
        assertSame(facts.identities, harness.travelInput)
        assertEquals(0, harness.currentnessStarts)

        harness.emitAlternativePrices(facts.alternativePrices)
        assertEquals(0, harness.currentnessStarts)

        harness.emitUsualPrices(facts.usualPrices)

        assertEquals(1, harness.currentnessStarts)
        assertSame(facts.usualPrices, harness.currentnessUsualInput)
        assertSame(facts.alternativePrices, harness.currentnessAlternativeInput)

        harness.emitTravel(facts.travel)
        harness.emitCurrentness(facts.currentness)

        assertEquals(
            listOf(
                facts.identities,
                facts.alternativePrices,
                facts.usualPrices,
                facts.travel,
                facts.currentness
            ),
            sink.accepted
        )

        session.close()
    }

    @Test
    fun `all synchronous category producers can complete one exact foreground fact set`() {
        val facts = blockedFacts(intent)
        val accepted = mutableListOf<Any>()
        val handles = mutableListOf<CloseTracker>()
        val coordinator =
            StapleWatchForegroundFactProductionCoordinator(
                identityProducer =
                    StapleWatchAlternativeStoreIdentityFactProducer { requestedIntent, observer ->
                        assertSame(intent, requestedIntent)
                        observer(facts.identities)
                        CloseTracker().also { handles += it }
                    },
                usualStorePriceProducer =
                    StapleWatchUsualStorePriceFactProducer { requestedIntent, observer ->
                        assertSame(intent, requestedIntent)
                        observer(facts.usualPrices)
                        CloseTracker().also { handles += it }
                    },
                alternativeStorePriceProducer =
                    StapleWatchAlternativeStorePriceFactProducer { identities, observer ->
                        assertSame(facts.identities, identities)
                        observer(facts.alternativePrices)
                        CloseTracker().also { handles += it }
                    },
                alternativeTravelProducer =
                    StapleWatchAlternativeTravelFactProducer { identities, observer ->
                        assertSame(facts.identities, identities)
                        observer(facts.travel)
                        CloseTracker().also { handles += it }
                    },
                currentnessProducer =
                    StapleWatchEvidenceCurrentnessFactProducer { usualPrices, alternativePrices, observer ->
                        assertSame(facts.usualPrices, usualPrices)
                        assertSame(facts.alternativePrices, alternativePrices)
                        observer(facts.currentness)
                        CloseTracker().also { handles += it }
                    }
            )
        val sink = RecordingSink(accepted)

        val session = coordinator.begin(intent, sink)

        assertEquals(5, accepted.size)
        assertTrue(accepted.containsAll(listOf(facts.identities, facts.usualPrices, facts.alternativePrices, facts.travel, facts.currentness)))
        assertEquals(5, handles.size)
        assertTrue(handles.none { it.closed })

        session.close()
        assertTrue(handles.all { it.closed })
    }

    @Test
    fun `dependent fact with detached upstream provenance fails closed`() {
        val facts = blockedFacts(intent)
        val harness = ProducerHarness(intent)
        val coordinator = harness.coordinator()
        coordinator.begin(intent, RecordingSink())
        harness.emitIdentity(facts.identities)

        val detachedIdentities =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = emptyList()
            )
        assertFalse(detachedIdentities === facts.identities)
        val detachedAlternativePrices = alternativePrices(detachedIdentities)

        expectIllegalArgument {
            harness.emitAlternativePrices(detachedAlternativePrices)
        }

        assertEquals(0, harness.currentnessStarts)
    }

    @Test
    fun `currentness must retain exact price fact objects that unlocked its stage`() {
        val facts = blockedFacts(intent)
        val harness = ProducerHarness(intent)
        val coordinator = harness.coordinator()
        coordinator.begin(intent, RecordingSink())
        harness.emitIdentity(facts.identities)
        harness.emitAlternativePrices(facts.alternativePrices)
        harness.emitUsualPrices(facts.usualPrices)
        assertEquals(1, harness.currentnessStarts)

        val detachedUsualPrices = usualPrices(intent)
        assertFalse(detachedUsualPrices === facts.usualPrices)
        val detachedCurrentness =
            currentness(
                usualPrices = detachedUsualPrices,
                alternativePrices = facts.alternativePrices
            )

        expectIllegalArgument {
            harness.emitCurrentness(detachedCurrentness)
        }
    }

    @Test
    fun `close cancels every started category and stale callbacks are ignored`() {
        val facts = blockedFacts(intent)
        val harness = ProducerHarness(intent)
        val sink = RecordingSink()
        val session = harness.coordinator().begin(intent, sink)
        harness.emitIdentity(facts.identities)
        harness.emitAlternativePrices(facts.alternativePrices)
        harness.emitUsualPrices(facts.usualPrices)

        assertEquals(5, harness.startedHandles().size)
        assertTrue(harness.startedHandles().none { it.closed })

        session.close()

        assertTrue(harness.startedHandles().all { it.closed })
        val acceptedBeforeLateCallbacks = sink.accepted.toList()

        harness.emitTravel(facts.travel)
        harness.emitCurrentness(facts.currentness)
        harness.emitIdentity(facts.identities)
        harness.emitUsualPrices(facts.usualPrices)

        assertEquals(acceptedBeforeLateCallbacks, sink.accepted)
        session.close()
        assertTrue(harness.startedHandles().all { it.closed })
    }

    @Test
    fun `composition owns dependency sequencing only`() {
        val source = source("StapleWatchForegroundFactProductionCoordinator.kt").readText()

        assertTrue(source.contains(": StapleWatchForegroundFactProducer"))
        assertTrue(source.contains("identityProducer.begin(intent"))
        assertTrue(source.contains("usualStorePriceProducer.begin(intent"))
        assertTrue(source.contains("alternativeStorePriceProducer.begin(facts"))
        assertTrue(source.contains("alternativeTravelProducer.begin(facts"))
        assertTrue(source.contains("facts.identityFacts === identities"))
        assertTrue(source.contains("facts.usualStorePriceFacts === usualPrices"))
        assertTrue(source.contains("facts.alternativeStorePriceFacts === alternativePrices"))
        assertTrue(source.contains("factIntent === intent"))

        listOf(
            "PracticalShoppingProductionCandidateBridge",
            "PracticalShoppingProductionOrchestrator",
            "ProductionCurrentPrice",
            "EvidenceProvider",
            "OpenPrices",
            "OpenStreetMap",
            "OpenFoodFacts",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "StapleWatchAlternativeStoreIdentityFacts.fromUnordered",
            "StapleWatchUsualStoreBasketPriceFacts.resolve",
            "StapleWatchAlternativeStoreBasketPriceFacts.resolve",
            "StapleWatchAlternativeAdditionalTravelFacts.fromUnordered",
            "StapleWatchEvidenceCurrentnessFacts.resolve",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecisionCoordinator",
            "StapleWatchPolicy(",
            "StapleWatchUiProjector",
            "renderer.render",
            "android."
        ).forEach { forbidden ->
            assertFalse("Fact-production composition must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun blockedFacts(factIntent: StapleWatchFactCheckIntent): FactBundle {
        val identities =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = factIntent,
                alternativeStoreKeys = emptyList()
            )
        val usualPrices = usualPrices(factIntent)
        val alternativePrices = alternativePrices(identities)
        val travel =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identities,
                additionalTravelByStore = emptyMap()
            )
        val currentness = currentness(usualPrices, alternativePrices)

        return FactBundle(
            identities = identities,
            usualPrices = usualPrices,
            alternativePrices = alternativePrices,
            travel = travel,
            currentness = currentness
        )
    }

    private fun usualPrices(
        factIntent: StapleWatchFactCheckIntent
    ): StapleWatchUsualStoreBasketPriceFacts {
        val registries = productionFixture.registries(emptyList())
        return StapleWatchUsualStoreBasketPriceFacts.resolve(
            intent = factIntent,
            store = storeScope(factIntent.usualStoreKey),
            priceBindings = emptyList(),
            priceRequests = emptyList(),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = productionFixture.evaluatedAtEpochMillis,
            acceptancePolicy = productionFixture.acceptancePolicy
        )
    }

    private fun alternativePrices(
        identities: StapleWatchAlternativeStoreIdentityFacts
    ): StapleWatchAlternativeStoreBasketPriceFacts {
        val registries = productionFixture.registries(emptyList())
        return StapleWatchAlternativeStoreBasketPriceFacts.resolve(
            identityFacts = identities,
            stores = emptyList(),
            priceBindings = emptyList(),
            priceRequests = emptyList(),
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = productionFixture.evaluatedAtEpochMillis,
            acceptancePolicy = productionFixture.acceptancePolicy
        )
    }

    private fun currentness(
        usualPrices: StapleWatchUsualStoreBasketPriceFacts,
        alternativePrices: StapleWatchAlternativeStoreBasketPriceFacts
    ): StapleWatchEvidenceCurrentnessFacts {
        val registries = productionFixture.registries(emptyList())
        return StapleWatchEvidenceCurrentnessFacts.resolve(
            usualStorePriceFacts = usualPrices,
            alternativeStorePriceFacts = alternativePrices,
            lifecycleRegistry = registries.lifecycle,
            dispositionRegistry = registries.disposition,
            evaluatedAtEpochMillis = productionFixture.evaluatedAtEpochMillis,
            acceptancePolicy = productionFixture.acceptancePolicy
        )
    }

    private fun storeScope(storeKey: ShoppingStoreKey): PracticalShoppingProductionPriceStoreScope =
        PracticalShoppingProductionPriceStoreScope(
            storeKey = storeKey,
            merchantKey = "merchant-${storeKey.value}",
            locationKey = "location-${storeKey.value}",
            commerceChannelKey = "IN_STORE"
        )

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected fail-closed contract.
        }
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }

    private class RecordingSink(
        val accepted: MutableList<Any> = mutableListOf()
    ) : StapleWatchForegroundFactSink {
        override fun accept(facts: StapleWatchAlternativeStoreIdentityFacts) {
            accepted += facts
        }

        override fun accept(facts: StapleWatchUsualStoreBasketPriceFacts) {
            accepted += facts
        }

        override fun accept(facts: StapleWatchAlternativeStoreBasketPriceFacts) {
            accepted += facts
        }

        override fun accept(facts: StapleWatchAlternativeAdditionalTravelFacts) {
            accepted += facts
        }

        override fun accept(facts: StapleWatchEvidenceCurrentnessFacts) {
            accepted += facts
        }
    }

    private class CloseTracker : AutoCloseable {
        var closed = false
            private set

        override fun close() {
            closed = true
        }
    }

    private class ProducerHarness(
        private val expectedIntent: StapleWatchFactCheckIntent
    ) {
        var identityStarts = 0
            private set
        var usualPriceStarts = 0
            private set
        var alternativePriceStarts = 0
            private set
        var travelStarts = 0
            private set
        var currentnessStarts = 0
            private set

        var alternativePriceInput: StapleWatchAlternativeStoreIdentityFacts? = null
            private set
        var travelInput: StapleWatchAlternativeStoreIdentityFacts? = null
            private set
        var currentnessUsualInput: StapleWatchUsualStoreBasketPriceFacts? = null
            private set
        var currentnessAlternativeInput: StapleWatchAlternativeStoreBasketPriceFacts? = null
            private set

        private var identityObserver: ((StapleWatchAlternativeStoreIdentityFacts) -> Unit)? = null
        private var usualPriceObserver: ((StapleWatchUsualStoreBasketPriceFacts) -> Unit)? = null
        private var alternativePriceObserver: ((StapleWatchAlternativeStoreBasketPriceFacts) -> Unit)? = null
        private var travelObserver: ((StapleWatchAlternativeAdditionalTravelFacts) -> Unit)? = null
        private var currentnessObserver: ((StapleWatchEvidenceCurrentnessFacts) -> Unit)? = null

        private val identityHandle = CloseTracker()
        private val usualPriceHandle = CloseTracker()
        private val alternativePriceHandle = CloseTracker()
        private val travelHandle = CloseTracker()
        private val currentnessHandle = CloseTracker()

        fun coordinator(): StapleWatchForegroundFactProductionCoordinator =
            StapleWatchForegroundFactProductionCoordinator(
                identityProducer =
                    StapleWatchAlternativeStoreIdentityFactProducer { intent, observer ->
                        assertSame(expectedIntent, intent)
                        identityStarts += 1
                        identityObserver = observer
                        identityHandle
                    },
                usualStorePriceProducer =
                    StapleWatchUsualStorePriceFactProducer { intent, observer ->
                        assertSame(expectedIntent, intent)
                        usualPriceStarts += 1
                        usualPriceObserver = observer
                        usualPriceHandle
                    },
                alternativeStorePriceProducer =
                    StapleWatchAlternativeStorePriceFactProducer { identities, observer ->
                        alternativePriceStarts += 1
                        alternativePriceInput = identities
                        alternativePriceObserver = observer
                        alternativePriceHandle
                    },
                alternativeTravelProducer =
                    StapleWatchAlternativeTravelFactProducer { identities, observer ->
                        travelStarts += 1
                        travelInput = identities
                        travelObserver = observer
                        travelHandle
                    },
                currentnessProducer =
                    StapleWatchEvidenceCurrentnessFactProducer { usualPrices, alternativePrices, observer ->
                        currentnessStarts += 1
                        currentnessUsualInput = usualPrices
                        currentnessAlternativeInput = alternativePrices
                        currentnessObserver = observer
                        currentnessHandle
                    }
            )

        fun emitIdentity(facts: StapleWatchAlternativeStoreIdentityFacts) {
            requireNotNull(identityObserver)(facts)
        }

        fun emitUsualPrices(facts: StapleWatchUsualStoreBasketPriceFacts) {
            requireNotNull(usualPriceObserver)(facts)
        }

        fun emitAlternativePrices(facts: StapleWatchAlternativeStoreBasketPriceFacts) {
            requireNotNull(alternativePriceObserver)(facts)
        }

        fun emitTravel(facts: StapleWatchAlternativeAdditionalTravelFacts) {
            requireNotNull(travelObserver)(facts)
        }

        fun emitCurrentness(facts: StapleWatchEvidenceCurrentnessFacts) {
            requireNotNull(currentnessObserver)(facts)
        }

        fun startedHandles(): List<CloseTracker> =
            listOf(
                identityHandle to identityStarts,
                usualPriceHandle to usualPriceStarts,
                alternativePriceHandle to alternativePriceStarts,
                travelHandle to travelStarts,
                currentnessHandle to currentnessStarts
            ).filter { (_, starts) -> starts > 0 }
                .map { (handle, _) -> handle }
    }

    private data class FactBundle(
        val identities: StapleWatchAlternativeStoreIdentityFacts,
        val usualPrices: StapleWatchUsualStoreBasketPriceFacts,
        val alternativePrices: StapleWatchAlternativeStoreBasketPriceFacts,
        val travel: StapleWatchAlternativeAdditionalTravelFacts,
        val currentness: StapleWatchEvidenceCurrentnessFacts
    )
}

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

class StapleWatchFactResolutionHostTest {

    private val productionFixture = StapleWatchProductionPriceTestFixture()
    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val usual = ShoppingStoreKey("opaque-host-usual-111111")
    private val alternative = ShoppingStoreKey("opaque-host-alt-222222")
    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(milk, eggs)),
            usualStoreKey = usual
        )

    @Test
    fun acceptedFactCheckIntentStartsFreshExactResolutionSession() {
        val host = StapleWatchFactResolutionHost()

        assertNull(host.currentSessionOrNull())
        host.onIntent(intent)
        val session = requireNotNull(host.currentSessionOrNull())

        assertSame(intent, session.intent)
        assertSame(intent, session.readiness.intent)
        assertEquals(intent.requirements, session.readiness.unresolvedRequirements)
        assertFalse(session.allFactCategoriesRetained)
    }

    @Test
    fun authoritativeFactIsDelegatedToCurrentSessionWithoutPayloadInterpretation() {
        val host = StapleWatchFactResolutionHost()
        val facts = identityFacts(intent)
        host.onIntent(intent)
        val before = requireNotNull(host.currentSessionOrNull())

        host.accept(facts)
        val after = requireNotNull(host.currentSessionOrNull())

        assertFalse(after === before)
        assertSame(intent, after.intent)
        assertSame(facts, after.identityFacts)
        assertEquals(
            intent.requirements.filterNot {
                it == StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES
            },
            after.readiness.unresolvedRequirements
        )
        assertNull(after.economicPreconditionsOrNull())
    }

    @Test
    fun completedPreconditionsEmitOnlyAfterFifthExactFactAndExactReapplyDoesNotDuplicate() {
        val emitted = mutableListOf<StapleWatchEconomicEvidencePreconditions>()
        val host =
            StapleWatchFactResolutionHost(
                preconditionsObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { preconditions ->
                        emitted += preconditions
                    }
            )
        val facts = blockedFacts(intent)
        host.onIntent(intent)

        host.accept(facts.currentness)
        host.accept(facts.travel)
        host.accept(facts.alternativePrices)
        host.accept(facts.usualPrices)
        assertTrue(emitted.isEmpty())

        host.accept(facts.identities)
        assertEquals(1, emitted.size)
        val preconditions = emitted.single()
        assertSame(facts.identities, preconditions.identityFacts)
        assertSame(facts.usualPrices, preconditions.usualStorePriceFacts)
        assertSame(facts.alternativePrices, preconditions.alternativeStorePriceFacts)
        assertSame(facts.travel, preconditions.additionalTravelFacts)
        assertSame(facts.currentness, preconditions.currentnessFacts)
        assertEquals(
            StapleWatchEconomicEvidencePreconditionIssue.USUAL_STORE_PRICE_COVERAGE_INCOMPLETE,
            preconditions.issue
        )
        assertFalse(preconditions.satisfied)

        host.accept(facts.identities)
        host.accept(facts.currentness)
        assertEquals(1, emitted.size)
    }

    @Test
    fun newIntentStartsFreshCompletionOpportunity() {
        val emitted = mutableListOf<StapleWatchEconomicEvidencePreconditions>()
        val host =
            StapleWatchFactResolutionHost(
                preconditionsObserver =
                    StapleWatchEconomicEvidencePreconditionsObserver { preconditions ->
                        emitted += preconditions
                    }
            )
        val firstFacts = blockedFacts(intent)
        host.onIntent(intent)
        acceptAll(host, firstFacts)
        assertEquals(1, emitted.size)

        val nextIntent =
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk, eggs)),
                usualStoreKey = ShoppingStoreKey("opaque-next-completion-usual-333333")
            )
        val nextFacts = blockedFacts(nextIntent)
        host.onIntent(nextIntent)
        assertEquals(1, emitted.size)

        acceptAll(host, nextFacts)
        assertEquals(2, emitted.size)
        assertSame(intent, emitted[0].intent)
        assertSame(nextIntent, emitted[1].intent)
    }

    @Test
    fun aNewAcceptedIntentReplacesPriorInMemoryResolutionProgress() {
        val host = StapleWatchFactResolutionHost()
        host.onIntent(intent)
        host.accept(identityFacts(intent))
        val progressed = requireNotNull(host.currentSessionOrNull())
        assertFalse(
            progressed.readiness.unresolvedRequirements.contains(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES
            )
        )

        val nextIntent =
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk, eggs)),
                usualStoreKey = ShoppingStoreKey("opaque-next-usual-333333")
            )
        host.onIntent(nextIntent)
        val replacement = requireNotNull(host.currentSessionOrNull())

        assertSame(nextIntent, replacement.intent)
        assertEquals(nextIntent.requirements, replacement.readiness.unresolvedRequirements)
        assertFalse(replacement === progressed)
    }

    @Test
    fun foreignFactFailsClosedAndLeavesCurrentSessionUnchanged() {
        val host = StapleWatchFactResolutionHost()
        host.onIntent(intent)
        val before = requireNotNull(host.currentSessionOrNull())
        val foreignIntent =
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk, eggs)),
                usualStoreKey = ShoppingStoreKey("opaque-foreign-usual-444444")
            )
        val foreignFacts = identityFacts(foreignIntent)

        expectIllegalArgument { host.accept(foreignFacts) }

        assertSame(before, host.currentSessionOrNull())
        assertEquals(intent.requirements, before.readiness.unresolvedRequirements)
    }

    @Test
    fun factsBeforeIntentAndAllEventsAfterCloseAreIgnored() {
        val host = StapleWatchFactResolutionHost()
        val facts = identityFacts(intent)

        host.accept(facts)
        assertNull(host.currentSessionOrNull())

        host.onIntent(intent)
        assertTrue(host.currentSessionOrNull() != null)
        host.close()

        assertTrue(host.isClosed())
        assertNull(host.currentSessionOrNull())
        host.onIntent(intent)
        host.accept(facts)
        assertNull(host.currentSessionOrNull())
    }

    @Test
    fun hostOwnsOnlyForegroundSessionSequencingAndCompletedPreconditionRouting() {
        val source = source("StapleWatchFactResolutionHost.kt").readText()

        assertTrue(source.contains(": StapleWatchFactCheckIntentObserver"))
        assertTrue(source.contains("StapleWatchFactResolutionSession.start(intent)"))
        assertTrue(source.contains("current.accept(facts)"))
        assertTrue(source.contains("currentSessionOrNull"))
        assertTrue(source.contains("StapleWatchEconomicEvidencePreconditionsObserver"))
        assertTrue(source.contains("economicPreconditionsOrNull()"))

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
            "StapleWatchPolicy",
            "StapleWatchStoreDisplayMetadata",
            "StapleWatchUiProjector",
            "StapleWatchSurfacePresenter",
            "renderer.render",
            "android."
        ).forEach { forbidden ->
            assertFalse("Fact-resolution host must not own $forbidden", source.contains(forbidden))
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

    private fun acceptAll(
        host: StapleWatchFactResolutionHost,
        facts: FactBundle
    ) {
        host.accept(facts.identities)
        host.accept(facts.usualPrices)
        host.accept(facts.alternativePrices)
        host.accept(facts.travel)
        host.accept(facts.currentness)
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

    private data class FactBundle(
        val identities: StapleWatchAlternativeStoreIdentityFacts,
        val usualPrices: StapleWatchUsualStoreBasketPriceFacts,
        val alternativePrices: StapleWatchAlternativeStoreBasketPriceFacts,
        val travel: StapleWatchAlternativeAdditionalTravelFacts,
        val currentness: StapleWatchEvidenceCurrentnessFacts
    )
}

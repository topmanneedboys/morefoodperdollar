package com.valuepilot.app

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
    fun hostOwnsOnlyForegroundSessionSequencing() {
        val source = source("StapleWatchFactResolutionHost.kt").readText()

        assertTrue(source.contains(": StapleWatchFactCheckIntentObserver"))
        assertTrue(source.contains("StapleWatchFactResolutionSession.start(intent)"))
        assertTrue(source.contains("current.accept(facts)"))
        assertTrue(source.contains("currentSessionOrNull"))

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
}

package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class StapleWatchAlternativeStoreIdentityFactsTest {

    private val usual = ShoppingStoreKey("usual")
    private val intent =
        StapleWatchFactCheckIntent(
            request =
                ShoppingRequest(
                    listOf(
                        ShoppingItemKey("milk"),
                        ShoppingItemKey("eggs")
                    )
                ),
            usualStoreKey = usual
        )

    @Test
    fun unorderedCandidatesBecomeStableLogicalStoreIdentityFacts() {
        val facts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys =
                    listOf(
                        ShoppingStoreKey("west"),
                        ShoppingStoreKey("east")
                    )
            )

        assertSame(intent, facts.intent)
        assertEquals(
            listOf(ShoppingStoreKey("east"), ShoppingStoreKey("west")),
            facts.alternativeStoreKeys
        )
        assertEquals(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
            facts.resolvedRequirement
        )
    }

    @Test
    fun explicitNoAlternativeCandidatesIsAValidResolvedIdentityFact() {
        val facts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = emptyList()
            )

        assertSame(intent, facts.intent)
        assertTrue(facts.alternativeStoreKeys.isEmpty())
        assertEquals(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
            facts.resolvedRequirement
        )
    }

    @Test
    fun usualStoreDuplicateAlternativeAndOversizedCandidateSetFailClosed() {
        try {
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(usual)
            )
            fail("The usual store must not be accepted as its own alternative")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        val duplicate = ShoppingStoreKey("duplicate")
        try {
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = listOf(duplicate, duplicate)
            )
            fail("Duplicate alternative store identities must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys =
                    (1..65).map { index -> ShoppingStoreKey("alternative-$index") }
            )
            fail("Alternative store identity facts must remain bounded")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun directConstructionAndGeneratedCopyCannotBypassStableOrdering() {
        val east = ShoppingStoreKey("east")
        val west = ShoppingStoreKey("west")

        try {
            StapleWatchAlternativeStoreIdentityFacts(
                intent = intent,
                alternativeStoreKeys = listOf(west, east)
            )
            fail("Direct construction must preserve stable store-key order")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        val canonical =
            StapleWatchAlternativeStoreIdentityFacts(
                intent = intent,
                alternativeStoreKeys = listOf(east, west)
            )
        try {
            canonical.copy(alternativeStoreKeys = listOf(west, east))
            fail("Generated copy must not bypass stable store-key ordering")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun alternativeIdentityFactsOwnNoOfferScopePricesTravelProvidersEconomicsOrDelivery() {
        val source = source("StapleWatchAlternativeStoreIdentityFacts.kt").readText()

        assertTrue(source.contains("ShoppingStoreKey"))
        assertTrue(source.contains("StapleWatchFactCheckIntent"))
        assertTrue(source.contains("ALTERNATIVE_STORE_CANDIDATE_IDENTITIES"))

        listOf(
            "PracticalShoppingStoreIdentityScope",
            "merchantKey",
            "locationKey",
            "commerceChannelKey",
            "SingleStorePlanCandidate",
            "StapleWatchAlternativeCandidate",
            "ShoppingTravel",
            "Money",
            "EvidenceProviderId",
            "EvidenceFreshness",
            "StapleWatchPolicy",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "OpenPrices",
            "OpenStreetMap",
            "OpenFoodFacts",
            "Http",
            "URL(",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Alternative identity facts must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}

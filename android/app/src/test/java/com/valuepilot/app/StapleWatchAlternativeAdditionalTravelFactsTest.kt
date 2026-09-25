package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class StapleWatchAlternativeAdditionalTravelFactsTest {

    private val intent =
        StapleWatchFactCheckIntent(
            request =
                ShoppingRequest(
                    listOf(
                        ShoppingItemKey("milk"),
                        ShoppingItemKey("eggs")
                    )
                ),
            usualStoreKey = ShoppingStoreKey("usual")
        )

    @Test
    fun unorderedTravelIsProjectedInResolvedAlternativeIdentityOrder() {
        val identityFacts = identities(listOf("west", "east", "central"))
        val east = ShoppingTravel(distanceMetres = 1_200L, travelTimeSeconds = 360L)
        val central = ShoppingTravel(distanceMetres = 700L, travelTimeSeconds = 240L)
        val west = ShoppingTravel(distanceMetres = 1_800L, travelTimeSeconds = 480L)

        val facts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore =
                    mapOf(
                        ShoppingStoreKey("west") to west,
                        ShoppingStoreKey("central") to central,
                        ShoppingStoreKey("east") to east
                    )
            )

        assertSame(identityFacts, facts.identityFacts)
        assertSame(intent, facts.intent)
        assertEquals(
            listOf("central", "east", "west"),
            facts.alternatives.map { it.storeKey.value }
        )
        assertEquals(listOf(central, east, west), facts.alternatives.map { it.additionalTravel })
        assertEquals(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
            facts.resolvedRequirement
        )
    }

    @Test
    fun zeroAdditionalTravelIsAnExplicitValidFact() {
        val identityFacts = identities(listOf("east"))
        val zero = ShoppingTravel(distanceMetres = 0L, travelTimeSeconds = 0L)

        val facts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore = mapOf(ShoppingStoreKey("east") to zero)
            )

        assertEquals(zero, facts.alternatives.single().additionalTravel)
    }

    @Test
    fun noAlternativeStoresRequiresAndAcceptsNoTravelFacts() {
        val identityFacts = identities()

        val facts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore = emptyMap()
            )

        assertTrue(facts.alternatives.isEmpty())
    }

    @Test
    fun missingAlternativeTravelFailsClosed() {
        val identityFacts = identities(listOf("east", "west"))

        try {
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identityFacts,
                additionalTravelByStore =
                    mapOf(
                        ShoppingStoreKey("east") to
                            ShoppingTravel(distanceMetres = 500L, travelTimeSeconds = 180L)
                    )
            )
            fail("Missing alternative travel must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: readiness cannot later treat partial alternative travel as complete facts.
        }
    }

    @Test
    fun foreignOrUsualStoreTravelFailsClosed() {
        val identityFacts = identities(listOf("east"))
        val travel = ShoppingTravel(distanceMetres = 500L, travelTimeSeconds = 180L)

        listOf(ShoppingStoreKey("foreign"), intent.usualStoreKey).forEach { invalidStore ->
            try {
                StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                    identityFacts = identityFacts,
                    additionalTravelByStore = mapOf(invalidStore to travel)
                )
                fail("Travel for $invalidStore must be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected: only the resolved alternative identity set is accepted.
            }
        }
    }

    @Test
    fun directConstructionCannotReorderOrPartiallyCoverAlternatives() {
        val identityFacts = identities(listOf("east", "west"))
        val east =
            StapleWatchAlternativeAdditionalTravelFact(
                storeKey = ShoppingStoreKey("east"),
                additionalTravel = ShoppingTravel(distanceMetres = 500L, travelTimeSeconds = 180L)
            )
        val west =
            StapleWatchAlternativeAdditionalTravelFact(
                storeKey = ShoppingStoreKey("west"),
                additionalTravel = ShoppingTravel(distanceMetres = 900L, travelTimeSeconds = 300L)
            )

        try {
            StapleWatchAlternativeAdditionalTravelFacts(
                identityFacts = identityFacts,
                alternatives = listOf(west, east)
            )
            fail("Noncanonical direct construction must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            StapleWatchAlternativeAdditionalTravelFacts(
                identityFacts = identityFacts,
                alternatives = listOf(east)
            )
            fail("Partial direct construction must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun generatedCopyCannotBypassExactCoverageInvariant() {
        val valid =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = identities(listOf("east", "west")),
                additionalTravelByStore =
                    mapOf(
                        ShoppingStoreKey("east") to
                            ShoppingTravel(distanceMetres = 500L, travelTimeSeconds = 180L),
                        ShoppingStoreKey("west") to
                            ShoppingTravel(distanceMetres = 900L, travelTimeSeconds = 300L)
                    )
            )

        try {
            valid.copy(alternatives = valid.alternatives.reversed())
            fail("Generated copy must not bypass stable exact coverage")
        } catch (_: IllegalArgumentException) {
            // Expected: data-class copy still executes init validation.
        }
    }

    @Test
    fun travelFactBoundaryOwnsNoPriceFreshnessEconomicsProviderPersistenceOrDeliveryAuthority() {
        val source = source("StapleWatchAlternativeAdditionalTravelFacts.kt").readText()

        assertTrue(source.contains("ShoppingTravel"))
        assertTrue(source.contains("ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS"))
        assertFalse(source.contains("knownBasketCost"))
        assertFalse(source.contains("selectedPrice"))

        listOf(
            "Money",
            "EvidenceFreshness",
            "EvidenceProvider",
            "ProductionCurrentPrice",
            "SingleStorePlanCandidate",
            "StapleWatchPolicy",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "PracticalShoppingTravelResolver",
            "OpenStreetMap",
            "Http",
            "URL(",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Travel fact boundary must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun identities(
        storeNames: List<String> = emptyList()
    ): StapleWatchAlternativeStoreIdentityFacts =
        StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
            intent = intent,
            alternativeStoreKeys = storeNames.map(::ShoppingStoreKey)
        )

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}

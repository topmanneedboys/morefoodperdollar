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

class StapleWatchAlternativeAdditionalTravelReadinessAdapterTest {

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
    fun matchingFactsResolveOnlyAlternativeAdditionalTravelRequirement() {
        val readiness = StapleWatchFactResolutionReadiness.initial(intent)
        val facts = facts(listOf("east", "west"))

        val updated =
            StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply(
                readiness = readiness,
                facts = facts
            )

        assertSame(intent, updated.intent)
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS),
            updated.resolvedRequirements
        )
        assertFalse(updated.allRequirementsReportedResolved)
    }

    @Test
    fun embeddedIdentityFactsDoNotImplicitlyResolveIdentityRequirement() {
        val updated =
            StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = facts(listOf("east"))
            )

        assertTrue(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES in
                updated.unresolvedRequirements
        )
        assertFalse(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS in
                updated.unresolvedRequirements
        )
    }

    @Test
    fun explicitNoAlternativeTravelStillResolvesTravelRequirementOnly() {
        val updated =
            StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = facts()
            )

        assertFalse(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS in
                updated.unresolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES in
                updated.unresolvedRequirements
        )
    }

    @Test
    fun existingProgressIsPreservedAndRepeatedApplicationIsIdempotent() {
        val readiness =
            StapleWatchFactResolutionReadiness.fromUnresolved(
                intent = intent,
                unresolvedRequirements =
                    setOf(
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                        StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
                    )
            )
        val facts = facts(listOf("east"))

        val updated =
            StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply(readiness, facts)
        val repeated =
            StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply(updated, facts)

        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS
            ),
            updated.resolvedRequirements
        )
        assertSame(updated, repeated)
    }

    @Test
    fun factsForDifferentIntentFailClosed() {
        val otherIntent =
            StapleWatchFactCheckIntent(
                request = intent.request,
                usualStoreKey = ShoppingStoreKey("different-usual")
            )
        val otherIdentityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = otherIntent,
                alternativeStoreKeys = emptyList()
            )
        val otherFacts =
            StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
                identityFacts = otherIdentityFacts,
                additionalTravelByStore = emptyMap()
            )

        try {
            StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = otherFacts
            )
            fail("Travel facts for a different fact-check intent must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: readiness cannot be advanced by facts from another intent.
        }
    }

    @Test
    fun readinessAdapterOwnsNoTravelValuesPricesFreshnessEconomicsPersistenceOrDelivery() {
        val source = source("StapleWatchAlternativeAdditionalTravelReadinessAdapter.kt").readText()

        assertTrue(source.contains("StapleWatchFactResolutionReadiness"))
        assertTrue(source.contains("StapleWatchAlternativeAdditionalTravelFacts"))
        assertTrue(source.contains("facts.resolvedRequirement"))
        assertFalse(source.contains("facts.alternatives"))
        assertFalse(source.contains("identityFacts"))

        listOf(
            "ShoppingTravel",
            "ShoppingStoreKey",
            "distanceMetres",
            "travelTimeSeconds",
            "knownBasketCost",
            "selectedPrice",
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
            assertFalse("Readiness adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun facts(
        storeNames: List<String> = emptyList()
    ): StapleWatchAlternativeAdditionalTravelFacts {
        val identityFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = intent,
                alternativeStoreKeys = storeNames.map(::ShoppingStoreKey)
            )
        return StapleWatchAlternativeAdditionalTravelFacts.fromUnordered(
            identityFacts = identityFacts,
            additionalTravelByStore =
                identityFacts.alternativeStoreKeys.associateWith {
                    ShoppingTravel(distanceMetres = 500L, travelTimeSeconds = 180L)
                }
        )
    }

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}

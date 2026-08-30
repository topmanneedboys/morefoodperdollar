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

class StapleWatchAlternativeStoreIdentityReadinessAdapterTest {

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
    fun matchingFactsResolveOnlyAlternativeStoreIdentityRequirement() {
        val readiness = StapleWatchFactResolutionReadiness.initial(intent)
        val facts = facts(listOf(ShoppingStoreKey("east"), ShoppingStoreKey("west")))

        val updated =
            StapleWatchAlternativeStoreIdentityReadinessAdapter.apply(
                readiness = readiness,
                facts = facts
            )

        assertSame(intent, updated.intent)
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES),
            updated.resolvedRequirements
        )
        assertFalse(updated.allRequirementsReportedResolved)
    }

    @Test
    fun explicitNoAlternativeCandidatesStillResolvesIdentityRequirement() {
        val updated =
            StapleWatchAlternativeStoreIdentityReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = facts()
            )

        assertFalse(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES in
                updated.unresolvedRequirements
        )
        assertTrue(
            StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES in
                updated.resolvedRequirements
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
        val facts = facts(listOf(ShoppingStoreKey("east")))

        val updated =
            StapleWatchAlternativeStoreIdentityReadinessAdapter.apply(readiness, facts)
        val repeated =
            StapleWatchAlternativeStoreIdentityReadinessAdapter.apply(updated, facts)

        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            updated.unresolvedRequirements
        )
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE
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
        val otherFacts =
            StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
                intent = otherIntent,
                alternativeStoreKeys = emptyList()
            )

        try {
            StapleWatchAlternativeStoreIdentityReadinessAdapter.apply(
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                facts = otherFacts
            )
            fail("Facts for a different fact-check intent must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: readiness cannot be advanced by facts from another intent.
        }
    }

    @Test
    fun readinessAdapterOwnsNoFactValuesProvidersEconomicsPersistenceOrDelivery() {
        val source = source("StapleWatchAlternativeStoreIdentityReadinessAdapter.kt").readText()

        assertTrue(source.contains("StapleWatchFactResolutionReadiness"))
        assertTrue(source.contains("StapleWatchAlternativeStoreIdentityFacts"))
        assertTrue(source.contains("facts.resolvedRequirement"))
        assertFalse(source.contains("alternativeStoreKeys"))

        listOf(
            "ShoppingStoreKey",
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
            assertFalse("Readiness adapter must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun facts(
        stores: List<ShoppingStoreKey> = emptyList()
    ): StapleWatchAlternativeStoreIdentityFacts =
        StapleWatchAlternativeStoreIdentityFacts.fromUnordered(
            intent = intent,
            alternativeStoreKeys = stores
        )

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}

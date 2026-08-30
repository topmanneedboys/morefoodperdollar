package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchFactResolutionReadinessTest {

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
    fun initialReadinessPreservesExactIntentAndEveryRequirementAsUnresolved() {
        val readiness = StapleWatchFactResolutionReadiness.initial(intent)

        assertSame(intent, readiness.intent)
        assertEquals(intent.requirements, readiness.unresolvedRequirements)
        assertTrue(readiness.resolvedRequirements.isEmpty())
        assertFalse(readiness.allRequirementsReportedResolved)
    }

    @Test
    fun partialReadinessUsesCanonicalIntentOrderAndExactComplement() {
        val readiness =
            StapleWatchFactResolutionReadiness.fromUnresolved(
                intent = intent,
                unresolvedRequirements =
                    setOf(
                        StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA,
                        StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE
                    )
            )

        assertSame(intent, readiness.intent)
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            readiness.unresolvedRequirements
        )
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS
            ),
            readiness.resolvedRequirements
        )
        assertFalse(readiness.allRequirementsReportedResolved)
    }

    @Test
    fun emptyUnresolvedSetReportsOnlyRequirementCompletionNotFactValues() {
        val readiness =
            StapleWatchFactResolutionReadiness.fromUnresolved(
                intent = intent,
                unresolvedRequirements = emptySet()
            )

        assertSame(intent, readiness.intent)
        assertTrue(readiness.unresolvedRequirements.isEmpty())
        assertEquals(intent.requirements, readiness.resolvedRequirements)
        assertTrue(readiness.allRequirementsReportedResolved)
    }

    @Test
    fun readinessBoundaryOwnsNoResolvedFactsProvidersEconomicsOrDelivery() {
        val source = source("StapleWatchFactResolutionReadiness.kt").readText()

        assertTrue(source.contains("StapleWatchFactCheckIntent"))
        assertTrue(source.contains("StapleWatchFactResolutionRequirement"))
        assertTrue(source.contains("allRequirementsReportedResolved"))

        listOf(
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
            assertFalse("Fact-resolution readiness must not own $forbidden", source.contains(forbidden))
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

package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class StapleWatchSavedFactCheckIntentAdapterTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")

    @Test
    fun acceptedIdentityHandoffBecomesFactCheckIntentWithoutChangingIdentity() {
        val request = ShoppingRequest(listOf(milk, eggs, bread))
        val attempt = acceptedAttempt(request = request, usualStoreKey = north)

        val intent = requireNotNull(StapleWatchSavedFactCheckIntentAdapter.from(attempt))

        assertEquals(request, intent.request)
        assertEquals(listOf(milk, eggs, bread), intent.request.itemKeys)
        assertEquals(north, intent.usualStoreKey)
    }

    @Test
    fun intentDeclaresAllStillUnresolvedFactCategoriesInStableOrder() {
        val intent =
            requireNotNull(
                StapleWatchSavedFactCheckIntentAdapter.from(
                    acceptedAttempt(
                        request = ShoppingRequest(listOf(milk, eggs)),
                        usualStoreKey = north
                    )
                )
            )

        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            intent.requirements
        )
    }

    @Test
    fun rejectedHandoffAttemptsNeverCreateFactCheckIntent() {
        StapleWatchSavedIdentityHandoffIssue.entries.forEach { issue ->
            val rejected =
                StapleWatchSavedIdentityHandoffAttempt(
                    handoff = null,
                    issue = issue
                )

            assertFalse(rejected.accepted)
            assertNull(StapleWatchSavedFactCheckIntentAdapter.from(rejected))
        }
    }

    @Test
    fun directFactCheckIntentFailsClosedForOneItemBasket() {
        try {
            StapleWatchFactCheckIntent(
                request = ShoppingRequest(listOf(milk)),
                usualStoreKey = north
            )
            fail("One-item staple fact-check intent must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: Watch My Staples remains basket-level.
        }
    }

    @Test
    fun factCheckIntentBoundaryOwnsNoResolvedFactsProvidersEconomicsOrDelivery() {
        val source = source("StapleWatchFactCheckIntent.kt").readText()

        assertTrue(source.contains("StapleWatchSavedIdentityHandoffAttempt"))
        assertTrue(source.contains("val handoff = attempt.handoff ?: return null"))
        assertTrue(source.contains("ShoppingRequest"))
        assertTrue(source.contains("ShoppingStoreKey"))

        listOf(
            "SingleStorePlanCandidate",
            "TwoStorePlanCandidate",
            "ShoppingTravel",
            "ShoppingPlanEvidenceSummary",
            "Money",
            "EvidenceProviderId",
            "SourceProductIdentity",
            "PracticalShoppingPlanner",
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
            assertFalse("Fact-check intent boundary must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun acceptedAttempt(
        request: ShoppingRequest,
        usualStoreKey: ShoppingStoreKey
    ): StapleWatchSavedIdentityHandoffAttempt =
        StapleWatchSavedIdentityHandoffAttempt(
            handoff =
                StapleWatchSavedIdentityHandoff(
                    request = request,
                    usualStoreKey = usualStoreKey
                ),
            issue = null
        )

    private fun source(fileName: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$fileName").also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }
    }
}

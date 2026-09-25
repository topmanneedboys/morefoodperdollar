package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchFactResolutionReadinessPresentationTest {

    private val intent =
        StapleWatchFactCheckIntent(
            request = ShoppingRequest(listOf(ShoppingItemKey("milk"), ShoppingItemKey("eggs"))),
            usualStoreKey = ShoppingStoreKey("usual-store")
        )

    @Test
    fun initialReadinessProjectsAllFiveRequirementsWithoutIdentity() {
        val state =
            StapleWatchFactResolutionUiProjector.project(
                StapleWatchFactResolutionReadiness.initial(intent)
            )

        assertEquals(5, state.totalRequirementCount)
        assertEquals(0, state.resolvedRequirementCount)
        assertEquals(
            listOf(
                "Usual-store basket prices",
                "Alternative store identities",
                "Alternative-store basket prices",
                "Additional travel details",
                "Evidence freshness metadata"
            ),
            state.unresolvedRequirementLabels
        )
        assertEquals("Fact checks in progress", state.headline)
        assertTrue(state.guidance.contains("No switch decision is available yet"))
    }

    @Test
    fun partialReadinessPreservesDeclaredOrderAndReportsResolvedCount() {
        val readiness =
            StapleWatchFactResolutionReadiness.fromUnresolved(
                intent = intent,
                unresolvedRequirements =
                    setOf(
                        StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                        StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
                    )
            )

        val state = StapleWatchFactResolutionUiProjector.project(readiness)

        assertEquals(5, state.totalRequirementCount)
        assertEquals(3, state.resolvedRequirementCount)
        assertEquals(
            listOf("Alternative-store basket prices", "Evidence freshness metadata"),
            state.unresolvedRequirementLabels
        )
    }

    @Test
    fun completeReadinessStillExplainsThatPolicyAndDisplayStaySeparate() {
        val state =
            StapleWatchFactResolutionUiProjector.project(
                StapleWatchFactResolutionReadiness.fromUnresolved(intent, emptySet())
            )

        assertEquals("All fact checks supplied", state.headline)
        assertEquals(5, state.resolvedRequirementCount)
        assertTrue(state.unresolvedRequirementLabels.isEmpty())
        assertTrue(state.guidance.contains("Policy and display metadata"))
    }

    @Test
    fun readinessPresentationOwnsNoFactValueEconomicAndroidOrIdentityAuthority() {
        val source = source("StapleWatchFactResolutionReadinessPresentation.kt").readText()

        listOf(
            "ShoppingRequest",
            "ShoppingStoreKey",
            "Money",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis",
            "android."
        ).forEach { forbidden ->
            assertFalse("Readiness presentation must not own $forbidden", source.contains(forbidden))
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

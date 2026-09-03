package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSurfaceViewBoundaryTest {

    @Test
    fun physicalViewConsumesConsumerStateOnly() {
        val source = source("StapleWatchSurfaceView.kt").readText()

        assertTrue(source.contains("StapleWatchSurfaceRenderer"))
        assertTrue(source.contains("render(state: StapleWatchUiState)"))
        assertTrue(source.contains("StapleWatchSwitchUiState"))

        listOf(
            "StapleWatchUiProjection",
            "StapleWatchEconomicDecision",
            "StapleWatchEconomicEvaluator",
            "ShoppingStoreKey",
            "recommendedStoreKey",
            "Money"
        ).forEach { forbidden ->
            assertFalse("Physical staple-watch View must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun physicalViewStartsHiddenRendersVisibleAndClearsAllConsumerState() {
        val source = source("StapleWatchSurfaceView.kt").readText()

        assertTrue(source.contains("isSaveEnabled = false\n        visibility = GONE"))
        assertTrue(source.contains("override fun render(state: StapleWatchUiState)"))
        assertTrue(source.contains("notice.visibility = VISIBLE\n        }\n        visibility = VISIBLE"))
        assertTrue(source.contains("fun clear()"))
        assertTrue(source.contains("headline.text = \"\""))
        assertTrue(source.contains("statusTitle.text = \"\""))
        assertTrue(source.contains("guidance.text = \"\""))
        assertTrue(source.contains("baselineEvidence.text = \"\""))
        assertTrue(source.contains("renderSwitchCandidate(null)"))
        assertTrue(source.contains("notice.text = \"\""))
        assertTrue(source.contains("notice.visibility = GONE"))
        assertTrue(source.contains("visibility = GONE"))
    }

    @Test
    fun switchCandidateSummaryIncludesEveryProjectedConsumerField() {
        val candidate =
            StapleWatchSwitchUiState(
                badge = "ECONOMIC SWITCH CANDIDATE",
                storeName = "Example Grocer",
                savingsText = "Could save 19.00 CAD",
                additionalTravelText = "Adds 5 min · 2 km",
                alternativeEvidenceText = "Alternative evidence: 2 fresh · 1 stale · 0 unknown",
                actionText = "Worth checking before your next shop"
            )

        assertEquals(
            "ECONOMIC SWITCH CANDIDATE. Store: Example Grocer. Could save 19.00 CAD. " +
                "Adds 5 min · 2 km. Alternative evidence: 2 fresh · 1 stale · 0 unknown. " +
                "Worth checking before your next shop.",
            stapleWatchSwitchCardContentDescription(candidate)
        )
    }

    @Test
    fun physicalViewAnnouncesStatusAndKeepsWatchAuthorityOutsideTheView() {
        val source = source("StapleWatchSurfaceView.kt").readText()

        assertTrue(source.contains("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"))
        assertTrue(source.contains("contentDescription = stapleWatchSwitchCardContentDescription(candidate)"))
        assertTrue(
            source.contains(
                "importantForAccessibility =\n                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"
            )
        )

        listOf(
            "StapleWatchEconomicEvaluator",
            "StapleWatchPolicy",
            "Money.parse",
            "System.currentTimeMillis",
            "ShoppingStoreKey"
        ).forEach { forbidden ->
            assertFalse("Watch View must not own authority through $forbidden", source.contains(forbidden))
        }
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

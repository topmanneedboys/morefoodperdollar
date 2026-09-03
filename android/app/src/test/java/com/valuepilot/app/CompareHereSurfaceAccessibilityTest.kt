package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompareHereSurfaceAccessibilityTest {

    @Test
    fun exactResultSummaryIncludesProjectedRankAndValueFields() {
        val row =
            CompareHereUiRow(
                title = "Large Milk",
                priceText = "7.00 CAD",
                quantityText = "1000 g",
                unitRateText = "7.00 CAD/kg",
                valueRank = 1,
                bestValue = true
            )

        assertEquals(
            "Best value. Large Milk. 7.00 CAD. 1000 g. 7.00 CAD/kg.",
            compareHereExactCardContentDescription(row)
        )
    }

    @Test
    fun blockedResultSummaryExplainsWhyTheProjectedCandidateIsNotComparable() {
        val row =
            CompareHereBlockedUiRow(
                title = "Unknown Milk",
                reasonText = "Package quantity needed"
            )

        assertEquals(
            "Needs information. Unknown Milk. Package quantity needed.",
            compareHereBlockedCardContentDescription(row)
        )
    }

    @Test
    fun surfaceAnnouncesStatusAndKeepsComparisonAuthorityOutsideTheView() {
        val source = source().readText()

        assertTrue(source.contains("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"))
        assertTrue(source.contains("contentDescription = compareHereExactCardContentDescription(row)"))
        assertTrue(source.contains("contentDescription = compareHereBlockedCardContentDescription(row)"))
        assertTrue(
            source.contains(
                "importantForAccessibility =\n                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"
            )
        )

        listOf(
            "CompareHereEvaluator",
            "CompareHereCandidate",
            "Money.parse",
            "RankingEngine",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse("Compare Here View must not own authority through $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/CompareHereSurfaceView.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}

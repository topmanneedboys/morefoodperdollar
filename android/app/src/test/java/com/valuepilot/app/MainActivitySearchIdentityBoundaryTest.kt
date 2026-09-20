package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySearchIdentityBoundaryTest {

    @Test
    fun `visible Search is Argentina identity-only and supports Compare handoff`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val layout = source("src/main/res/layout/activity_shell.xml").readText()

        listOf(
            "ArgentinaLiveSearchSurfaceHost",
            "ArgentinaQualifiedLaunchAreas.BuenosAiresCaba",
            "searchAreaLabel",
            "PracticalShoppingSearchIdentityPresentation.Match",
            "openComparisonWithSharedText(match.displayName)",
            "Argentina SEPA identity result"
        ).forEach { required ->
            assertTrue("Expected visible identity boundary $required", activity.contains(required))
        }
        assertTrue(layout.contains("android:id=\"@+id/searchAreaLabel\""))
        assertFalse(layout.contains("@+id/searchIdentityButton"))
        assertFalse(activity.contains("LocalSampleProductSearchProvider"))
        assertFalse(activity.contains("UniversalSearchController"))
        assertFalse(activity.contains("row.priceSummary"))
        assertFalse(activity.contains("row.metricLabel"))
        assertFalse(activity.contains("row.best"))
    }

    @Test
    fun `identity cards do not expose price ranking or availability authority`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val cardStart = activity.indexOf("private fun createSearchResultView(")
        val cardEnd = activity.indexOf("private fun copyFor", cardStart)
        assertTrue(cardStart >= 0)
        assertTrue(cardEnd > cardStart)
        val card = activity.substring(cardStart, cardEnd)
        listOf(
            "priceSummary",
            "metricLabel",
            "best_value",
            "rank",
            "inventory",
            "availability",
            "merchant availability"
        ).forEach { forbidden ->
            assertFalse("Identity card must not own $forbidden", card.contains(forbidden, ignoreCase = true))
        }
        assertTrue(card.contains("caveat"))
        assertTrue(card.contains("openComparisonWithSharedText"))
    }

    private fun source(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

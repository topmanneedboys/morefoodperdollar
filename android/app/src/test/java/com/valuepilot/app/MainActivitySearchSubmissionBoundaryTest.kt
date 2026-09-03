package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySearchSubmissionBoundaryTest {

    @Test
    fun keyboardSubmissionSharesTheVisibleReadinessGate() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val presentation =
            source(
                "src/main/java/com/valuepilot/app/PracticalShoppingSearchSurfacePresentation.kt"
            ).readText()

        assertTrue(
            activity.contains(
                "searchButton.isEnabled = practicalShoppingSearchSubmitEnabled(state)"
            )
        )
        assertTrue(
            activity.contains(
                "if (!practicalShoppingSearchSubmitEnabled(searchState, rawQuery))"
            )
        )
        assertTrue(
            activity.contains(
                "if (practicalShoppingSearchQuickEntryBlocked(searchState, query))"
            )
        )
        assertTrue(presentation.contains("UniversalSearchStatus.LOADING"))
        assertTrue(presentation.contains("UniversalSearchStatus.QUERY_TOO_LONG"))
        assertTrue(presentation.contains("state.query == rawQuery"))

        listOf(
            "PracticalShoppingPlanner(",
            "Money.parse",
            "HttpURLConnection",
            "ProductSearchProvider",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse(
                "Search readiness presentation must not own $forbidden",
                presentation.contains(forbidden)
            )
        }
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

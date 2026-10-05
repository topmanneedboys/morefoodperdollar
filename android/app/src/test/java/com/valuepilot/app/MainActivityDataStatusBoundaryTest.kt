package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityDataStatusBoundaryTest {

    @Test
    fun `technical details are reachable only from the Settings data-source path`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        listOf(
            "AppRoute.SETTINGS_DATA_SOURCES",
            "AppRoute.SETTINGS_TECHNICAL_DETAILS",
            "settingsScreenCopy(state.route)",
            "renderSettings(state.route)",
            "PracticalShoppingDataStatusPresentation.from("
        ).forEach { required ->
            assertTrue("Expected data-status containment: $required", activity.contains(required))
        }
        assertFalse(activity.contains("dataStatusButton"))
        assertFalse(activity.contains("showDataStatus()"))
    }

    @Test
    fun `data status remains identity and uncertainty only`() {
        val source = source("src/main/java/com/valuepilot/app/PracticalShoppingDataStatusPresentation.kt").readText()

        listOf(
            "None included. There is no authorized live-offer feed",
            "Unknown prices stay unknown",
            "No bundled store directory is shipped",
            "Flyer content is not copied",
            "fictional sample data",
            "No bundled product catalogue is shipped",
            "source-attributed"
        ).forEach { required ->
            assertTrue("Expected explicit uncertainty boundary: $required", source.contains(required))
        }

        listOf(
            "HttpURLConnection",
            "URL(",
            "PracticalShoppingPlanner",
            "Money.parse",
            "rank"
        ).forEach { forbidden ->
            assertFalse("Status presentation must not add authority: $forbidden", source.contains(forbidden))
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

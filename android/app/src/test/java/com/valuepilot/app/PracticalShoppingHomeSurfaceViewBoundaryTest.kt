package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingHomeSurfaceViewBoundaryTest {

    @Test
    fun advancedExtraStopControlMechanicallyObeysImmutableVisibility() {
        val source = source().readText()

        assertTrue(
            source.contains(
                "extraStopSettingsButton.visibility = if (state.visible) VISIBLE else GONE"
            )
        )
        assertTrue(source.contains("if (!state.visible) extraStopSettingsExpanded = false"))
        assertTrue(
            source.contains(
                "extraStopSettingsButton.visibility == VISIBLE && extraStopSettingsExpanded"
            )
        )
    }

    @Test
    fun physicalViewDoesNotDecideWhenAdvancedControlIsAvailable() {
        val source = source().readText()

        listOf(
            "LocalSamplePracticalShoppingDemo.Status",
            "source.result",
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy(",
            "minimumSecondStopSavings",
            "Money.parse"
        ).forEach { forbidden ->
            assertFalse(
                "Home View must not own advanced-control policy through $forbidden",
                source.contains(forbidden)
            )
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/PracticalShoppingHomeSurfaceView.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}

package com.valuepilot.app

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

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

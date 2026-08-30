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

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecondaryConsumerButtonVisualBoundaryTest {

    @Test
    fun secondary_consumer_surfaces_use_the_shared_programmatic_button_treatment() {
        listOf(
            "PracticalShoppingSavedObservedPriceLaunchView.kt",
            "PracticalShoppingSavedStapleLaunchView.kt",
            "StapleWatchPolicyDraftSurfaceView.kt",
            "StapleWatchSavedSelectionSurfaceView.kt",
            "UserObservedPriceConfirmationDraftPriceInputSurfaceView.kt",
            "UserObservedPriceConfirmationDraftObservedAtInputSurfaceView.kt",
            "UserObservedPriceConfirmationDraftProofContentSelectionSurfaceView.kt",
            "UserObservedPriceConfirmationDraftProofReferenceInputSurfaceView.kt",
            "UserObservedPriceSavedSelectionSurfaceView.kt",
            "ComparisonActivity.kt"
        ).forEach { name ->
            val source = source(name).readText()
            assertTrue("$name must use the shared button factory", source.contains("ValuePilotProgrammaticButton"))
            assertFalse(
                "$name must not create a platform context Button",
                Regex("(?m)^\\s*Button\\(context\\)").containsMatchIn(source)
            )
            assertFalse(
                "$name must not create a platform activity Button",
                Regex("(?m)^\\s*Button\\(this\\)").containsMatchIn(source)
            )
        }
    }

    @Test
    fun shared_factory_keeps_touch_targets_and_warm_filled_and_outlined_roles() {
        val source = source("ValuePilotProgrammaticButton.kt").readText()

        assertTrue(source.contains("fun filled("))
        assertTrue(source.contains("fun outlined("))
        assertTrue(source.contains("ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP"))
        assertTrue(source.contains("ValuePilotDesignTokens.TouchTarget.MINIMUM_DP"))
        assertTrue(source.contains("ValuePilotDesignTokens.Colors.ACTION_FILLED"))
        assertTrue(source.contains("ValuePilotDesignTokens.Colors.SURFACE"))
        assertTrue(source.contains("strokeColor"))
        assertTrue(source.contains("destructive"))
    }

    private fun source(name: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$name")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $name")
    }
}

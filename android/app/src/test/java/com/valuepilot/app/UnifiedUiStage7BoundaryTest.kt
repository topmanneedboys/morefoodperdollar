package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Presentation-only regression checks for the final legacy consumer surfaces. */
class UnifiedUiStage7BoundaryTest {

    @Test
    fun `compare uses shared cards progressive details and no view authority`() {
        val source = source("CompareHereSurfaceView.kt").readText()

        listOf(
            "ValuePilotProductCard",
            "ValuePilotProgrammaticButton.outlined",
            "ValuePilotMotion.reveal",
            "Show comparison details",
            "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE",
            "contentDescription = compareHereExactCardContentDescription(row)",
            "contentDescription = compareHereBlockedCardContentDescription(row)"
        ).forEach { required ->
            assertTrue("Compare presentation should contain $required", source.contains(required))
        }

        listOf(
            "CompareHereEvaluator",
            "ShoppingDecisionEngine",
            "Money.parse",
            "System.currentTimeMillis",
            "HttpURLConnection",
            "URL("
        ).forEach { forbidden ->
            assertFalse("Compare View must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `good price makes evidence basis visible and keeps detail disclosure`() {
        val source = source("GoodPriceCheckSurfaceView.kt").readText()

        listOf(
            "ValuePilotProductCard",
            "Evidence: this exact price only",
            "Evidence: your private history",
            "What this means",
            "ValuePilotMotion.reveal",
            "goodPriceAnswerCardContentDescription(state)",
            "View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"
        ).forEach { required ->
            assertTrue("Good Price presentation should contain $required", source.contains(required))
        }

        listOf(
            "GoodPriceCheckRouteCoordinator",
            "CompareHereManualComparisonService",
            "CompareHerePriceMemoryEvaluator",
            "Money.parse",
            "System.currentTimeMillis",
            "RankingEngine",
            "HttpURLConnection"
        ).forEach { forbidden ->
            assertFalse("Good Price View must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `comparison capture tools are visually secondary to the manual flow`() {
        val layout = source("src/main/res/layout/activity_main.xml").readText()
        val manualInputs = layout.indexOf("@+id/productInputsContainer")
        val result = layout.indexOf("@+id/compareHereScreen")
        val capture = layout.indexOf("@+id/captureToolsCard")
        val photo = layout.indexOf("@+id/capturePhotoButton")
        val barcode = layout.indexOf("@+id/compareBarcodeButton")

        assertTrue(manualInputs >= 0)
        assertTrue(result > manualInputs)
        assertTrue(capture > result)
        assertTrue(photo > capture)
        assertTrue(barcode > capture)
        assertTrue(layout.contains("@string/compare_capture_tools_title"))
        assertTrue(layout.contains("@string/compare_capture_tools_body"))
        assertTrue(layout.contains("@style/Widget.ValuePilot.Button.Outlined"))
    }

    @Test
    fun `barcode and share adapters use the same calm card language`() {
        val barcode = source("src/main/res/layout/activity_barcode_capture.xml").readText()
        val share = source("src/main/res/layout/activity_share_to_valuepilot.xml").readText()

        listOf(barcode, share).forEach { layout ->
            assertTrue(layout.contains("<com.google.android.material.card.MaterialCardView"))
            assertTrue(layout.contains("@color/vp_surface"))
            assertTrue(layout.contains("@color/vp_outline"))
            assertTrue(layout.contains("@style/Widget.ValuePilot.Button"))
            assertTrue(layout.contains("@style/Widget.ValuePilot.Button.Outlined"))
        }
    }

    @Test
    fun `capture and comparison sources do not parse colors outside the shared palette`() {
        listOf(
            "CompareHereSurfaceView.kt",
            "GoodPriceCheckSurfaceView.kt",
            "ComparisonActivity.kt",
            "BarcodeCaptureActivity.kt",
            "ShareToValuePilotActivity.kt"
        ).forEach { name ->
            assertFalse(source(name).readText().contains("Color.parseColor("))
        }
    }

    private fun source(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            listOf(
                File(directory, relativePath),
                File(directory, "src/main/java/com/valuepilot/app/$relativePath"),
                File(directory, "app/$relativePath"),
                File(directory, "app/src/main/java/com/valuepilot/app/$relativePath"),
                File(directory, "android/app/$relativePath"),
                File(directory, "android/app/src/main/java/com/valuepilot/app/$relativePath")
            ).firstOrNull { it.isFile }?.let { return it }
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

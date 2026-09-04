package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoodPriceCheckBoundaryTest {

    @Test
    fun `coordinator reuses exact comparison and private history authorities`() {
        val source = source("GoodPriceCheck.kt").readText()

        listOf(
            "ManualProductObservationAdapter.captureBlocks",
            "CompareHereManualComparisonService.compare",
            "CompareHerePriceMemoryEvaluator.assess",
            "CompareHerePrivatePriceMemoryCapture",
            "CONFIRMED_GOOD_PRICE_CHECK"
        ).forEach { required -> assertTrue(source.contains(required)) }

        listOf(
            "PracticalShoppingPlanner",
            "CompareHereEvaluator.evaluate",
            "Money.parse",
            "HttpURLConnection",
            "URL(",
            "RankingEngine",
            "current store",
            "live inventory"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
    }

    @Test
    fun `physical good price view consumes projected content only`() {
        val source = source("GoodPriceCheckSurfaceView.kt").readText()

        assertTrue(source.contains("GoodPriceCheckScreenRenderer"))
        assertTrue(source.contains("GoodPriceCheckScreenContent.Message"))
        assertTrue(source.contains("GoodPriceCheckScreenContent.Result"))
        assertTrue(source.contains("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"))
        listOf(
            "GoodPriceCheckRouteCoordinator",
            "CompareHereManualComparisonService",
            "CompareHerePriceMemoryEvaluator",
            "Money.parse",
            "System.currentTimeMillis",
            "PracticalShoppingPlanner",
            "RankingEngine"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
    }

    @Test
    fun `activity owns only local orchestration and has no network or ranking authority`() {
        val source = source("GoodPriceActivity.kt").readText()

        listOf(
            "GoodPriceCheckRouteCoordinator.checkBlock",
            "CompareHerePrivatePriceMemoryAndroidStore",
            "privateMemoryLoadIssue",
            "good_price_memory_unavailable",
            "memoryStore.append",
            "memoryStore.clear",
            "EXTRA_PRODUCT_NAME",
            "GoodPriceActivityPrefill",
            ".sanitize(intent.getStringExtra(EXTRA_PRODUCT_NAME))",
            "savedInstanceState == null",
            "productInput.setText(value)"
        ).forEach { required -> assertTrue(source.contains(required)) }
        listOf(
            "HttpURLConnection",
            "URL(",
            "PracticalShoppingPlanner",
            "CompareHereEvaluator",
            "RankingEngine",
            "INTERNET"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
    }

    @Test
    fun `good price activity is not exported`() {
        val manifest = manifest().readText()
        val activityStart = manifest.indexOf("android:name=\".GoodPriceActivity\"")
        assertTrue(activityStart >= 0)
        val activityEnd = manifest.indexOf("/>", activityStart)
        assertTrue(activityEnd > activityStart)
        assertTrue(manifest.substring(activityStart, activityEnd).contains("android:exported=\"false\""))
    }

    @Test
    fun `good price layout keeps its own basis and unavailable-history copy`() {
        val layout = layout().readText()
        val strings = strings().readText()

        assertTrue(layout.contains("@string/good_price_selection_body"))
        assertTrue(strings.contains("name=\"good_price_selection_body\""))
        assertTrue(strings.contains("name=\"good_price_memory_unavailable\""))
    }

    private fun source(name: String): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/java/com/valuepilot/app/$name"
        ).also { assertTrue("Missing source at ${it.absolutePath}", it.isFile) }

    private fun manifest(): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/AndroidManifest.xml"
        ).also { assertTrue("Missing manifest at ${it.absolutePath}", it.isFile) }

    private fun layout(): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/res/layout/activity_good_price.xml"
        ).also { assertTrue("Missing layout at ${it.absolutePath}", it.isFile) }

    private fun strings(): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/res/values/strings.xml"
        ).also { assertTrue("Missing strings at ${it.absolutePath}", it.isFile) }
}

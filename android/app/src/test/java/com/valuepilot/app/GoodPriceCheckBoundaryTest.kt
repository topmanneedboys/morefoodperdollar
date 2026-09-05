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
            "goodPriceAnswerCardContentDescription(state)",
            "importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES",
            "View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS",
            "contentDescription = history"
        ).forEach { required -> assertTrue(source.contains(required)) }
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
    fun `good price preserves the bounded typed draft across recreation`() {
        val source = source("GoodPriceActivity.kt").readText()
        val saveState =
            source
                .substringAfter("override fun onSaveInstanceState")
                .substringBefore("private fun runCheck")

        assertTrue(source.contains("STATE_PRODUCT_INPUT"))
        assertTrue(source.contains("savedInstanceState?.containsKey(STATE_PRODUCT_INPUT) == true"))
        assertTrue(source.contains("productInput.setText(restoredInput)"))
        assertTrue(saveState.contains("productInput.text?.toString().orEmpty()"))
        assertTrue(source.contains("} else {"))
        assertTrue(source.contains("GoodPriceActivityPrefill"))
    }

    @Test
    fun `good price private memory outcomes use a polite live region`() {
        val source = source("GoodPriceActivity.kt").readText()

        assertTrue(
            source.contains(
                "memoryStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
        assertTrue(source.contains("memoryStatus = findViewById(R.id.goodPriceMemoryStatus)"))
    }

    @Test
    fun `good price invalidates projected personal context when memory changes on resume`() {
        val source = source("GoodPriceActivity.kt").readText()
        val onResume =
            source.substringAfter("override fun onResume").substringBefore("override fun onDestroy")

        listOf(
            "val previousMemory = privateMemory",
            "val previousIssue = privateMemoryLoadIssue",
            "privateMemory = loadPrivateMemory()",
            "previousMemory != privateMemory || previousIssue != privateMemoryLoadIssue",
            "renderIdle()",
            "showMemoryUnavailable()"
        ).forEach { required -> assertTrue(onResume.contains(required)) }
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

    @Test
    fun `good price sharing stays explicit generic and offline`() {
        val activity = source("GoodPriceActivity.kt").readText()
        val share = source("GoodPriceShareCard.kt").readText()
        val layout = layout().readText()
        val strings = strings().readText()

        listOf(
            "GoodPriceShareCard",
            "renderShareCard(evaluation.state.shareCard)",
            "shareGoodPriceResult()",
            "Intent(Intent.ACTION_SEND)",
            "good_price_share_preview_body"
        ).forEach { required -> assertTrue(activity.contains(required)) }
        listOf(
            "GoodPriceShareCardProjector",
            "not live store pricing",
            "product name",
            "private history",
            "Character.isISOControl"
        ).forEach { required -> assertTrue(share.contains(required)) }
        listOf(
            "goodPriceShareButton",
            "goodPriceShareStatus",
            "@string/good_price_share_result"
        ).forEach { required -> assertTrue(layout.contains(required)) }
        listOf(
            "name=\"good_price_share_result\"",
            "name=\"good_price_share_result_description\"",
            "name=\"good_price_share_unavailable\""
        ).forEach { required -> assertTrue(strings.contains(required)) }
        listOf(
            "HttpURLConnection",
            "URL(",
            "INTERNET",
            "PracticalShoppingPlanner",
            "RankingEngine"
        ).forEach { forbidden -> assertFalse(activity.contains(forbidden)) }
    }

    @Test
    fun `clearing private memory invalidates any displayed good price result`() {
        val activity = source("GoodPriceActivity.kt").readText()
        val clearHandler =
            activity
                .substringAfter("clearMemoryButton.setOnClickListener")
                .substringBefore("checkButton.setOnClickListener")

        assertTrue(clearHandler.contains("privateMemory = CompareHerePrivatePriceMemoryState.empty()"))
        assertTrue(clearHandler.contains("renderIdle()"))
        assertTrue(
            "Clearing Good Price history must not leave the deleted result/share card visible",
            clearHandler.contains("renderIdle()")
        )
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

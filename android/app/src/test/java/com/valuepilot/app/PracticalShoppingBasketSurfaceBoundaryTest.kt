package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingBasketSurfaceBoundaryTest {

    @Test
    fun basketMechanicallyRendersImmutableStateAndEmitsOnlyTypedNavigation() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readText()
        val renderer = source("PracticalShoppingBasketRenderer.kt").readText()

        assertTrue(source.contains("fun render(state: PracticalShoppingBasketRenderState)"))
        assertTrue(source.contains("planResult.render(state.result, state.sampleNotice)"))
        assertTrue(source.contains("sampleNotice.text = state.sampleNotice"))
        assertTrue(source.contains("PracticalShoppingBasketUiAction.OpenHome"))
        assertTrue(source.contains("isEnabled = false"))
        assertTrue(source.contains("actionButton.isEnabled = onAction != null"))
        assertTrue(source.contains("item.requestDetailsSummary"))
        assertTrue(source.contains("item.requestDetailsNotice"))
        assertTrue(source.contains("item.priceCoverageNotice"))
        assertTrue(source.contains("item.storeAssignment"))
        assertTrue(source.contains("line(\"Buy at ${'$'}store\""))
        assertTrue(source.contains("state.collectibleItemKeys"))
        assertTrue(source.contains("state.collectionScopeId"))
        assertTrue(source.contains("SAVED_COLLECTION_SCOPE_ID"))
        assertTrue(source.contains("state.putString(SAVED_COLLECTION_SCOPE_ID"))
        assertTrue(source.contains("state.containsKey(SAVED_COLLECTION_SCOPE_ID)"))
        assertTrue(source.contains("collectionNotice.text = state.collectionNotice.orEmpty()"))
        assertTrue(source.contains("extraStopRuleNotice.text = state.extraStopRuleNotice.orEmpty()"))
        assertTrue(source.contains("state.extraStopRuleNotice == null"))
        assertTrue(source.contains("state.collectionNotice == null"))
        assertTrue(source.contains("No usable price yet — not ready to collect"))
        assertTrue(source.contains("addItemDetails(item, this, state.collectionEnabled)"))
        assertTrue(source.contains("practicalShoppingBasketCollectionActionDescription(item, collected)"))
        assertFalse(source.contains("Check-off starts for items with usable planned price coverage."))
        assertTrue(renderer.contains("source.result.itemStoreAssignments"))
        assertTrue(renderer.contains("source.extraStopSettings.notice"))
        assertFalse(renderer.contains("storeAssignment != null"))

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy(",
            "Money.parse",
            "knownBasketCost",
            "SecondStopDecision",
            "ProductionCurrentPrice",
            "affiliate",
            "providerEconomics"
        ).forEach { forbidden ->
            assertFalse(
                "Basket View must not gain shopping authority through $forbidden",
                source.contains(forbidden, ignoreCase = true)
            )
        }
    }

    @Test
    fun ownerCallbackChangesFailClosedForAlreadyRenderedNavigation() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readText()

        listOf(
            "private var hasRenderedState = false",
            "actionButton.isEnabled = value != null && hasRenderedState",
            "hasRenderedState = true",
            "actionButton.isEnabled = onAction != null && hasRenderedState",
            "setOnClickListener { onAction?.invoke(PracticalShoppingBasketUiAction.OpenHome) }"
        ).forEach { required ->
            assertTrue("Expected live Basket owner invalidation binding $required", source.contains(required))
        }
    }

    @Test
    fun checkOffResetIsLocalForegroundStateAndAppearsOnlyWhenMarksExist() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readText()

        assertTrue(source.contains("PracticalShoppingBasketProgressSession.clearCollected"))
        assertTrue(source.contains("progressState.collectedItemKeys.isNotEmpty()"))
        assertTrue(source.contains("text = \"Clear check-off\""))
        assertTrue(source.contains("contentDescription = \"Clear collected item marks\""))

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy(",
            "Money.parse",
            "knownBasketCost",
            "SecondStopDecision",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse("Check-off reset must stay outside shopping authority through $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun checkOffProgressUsesAPoliteAccessibilityLiveRegion() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readText()

        assertTrue(
            source.contains(
                "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
    }

    @Test
    fun collectionSafetyDisclosureUsesAPoliteAccessibilityLiveRegion() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readText()

        assertEquals(
            2,
            source
                .split("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE")
                .size - 1
        )
        assertTrue(source.contains("private val collectionNotice"))
        assertTrue(source.contains("projected safety disclosure"))
    }

    @Test
    fun homeAndBasketShareOneUiReadyPlanResultRenderer() {
        val home = source("PracticalShoppingHomeSurfaceView.kt").readText()
        val basket = source("PracticalShoppingBasketSurfaceView.kt").readText()
        val result = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        assertTrue(home.contains("PracticalShoppingPlanResultSurfaceView(context)"))
        assertTrue(home.contains("resultContainer.render(state.result, state.sampleNotice)"))
        assertTrue(basket.contains("PracticalShoppingPlanResultSurfaceView(context)"))
        assertTrue(result.contains("fun render(state: PracticalShoppingUiState?)"))

        assertFalse(home.contains("private fun primaryCard"))
        assertFalse(home.contains("private fun secondStopCard"))
        assertFalse(basket.contains("private fun primaryCard"))
        assertFalse(basket.contains("private fun secondStopCard"))
    }

    @Test
    fun sharedPlanViewFormatsNoMoneyAndMakesNoDecision() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy",
            "Money",
            "BigDecimal",
            "PrimaryShoppingPlanKind",
            "SecondStopDecision",
            "formatMoney",
            "compareTo("
        ).forEach { forbidden ->
            assertFalse(
                "Shared plan View must render projected strings only; found $forbidden",
                source.contains(forbidden)
            )
        }
    }

    @Test
    fun sharedPlanViewUsesProjectedCompletenessForCautionStyling() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        assertTrue(source.contains("practicalShoppingPrimaryCardStyle(state)"))
        assertTrue(source.contains("style.backgroundColor"))
        assertTrue(source.contains("style.strokeColor"))
        assertTrue(source.contains("style.accentColor"))
        assertTrue(source.contains("state.missingItemsText == null"))
        assertTrue(source.contains("importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES"))
        assertTrue(source.contains("practicalShoppingPrimaryCardContentDescription(state, sampleNotice)"))
        assertTrue(source.contains("practicalShoppingSecondStopCardContentDescription(state, sampleNotice)"))
    }

    @Test
    fun sharedPlanResultAnnouncesProjectedChangesPolitely() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        assertTrue(
            source.contains(
                "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
        assertTrue(source.contains("fun render(state: PracticalShoppingUiState?)"))
        assertTrue(source.contains("if (state == null) return"))
    }

    @Test
    fun sharedPlanCardsExposeOneSummaryInsteadOfRepeatingDecorativeChildren() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        assertEquals(
            2,
            source.split(
                "importantForAccessibility =\n                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"
            ).size - 1
        )
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/$name"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}

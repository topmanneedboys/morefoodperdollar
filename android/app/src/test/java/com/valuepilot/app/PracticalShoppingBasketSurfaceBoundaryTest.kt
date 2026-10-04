package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingBasketSurfaceBoundaryTest {

    @Test
    fun basketMechanicallyRendersImmutableStateAndEmitsOnlyTypedNavigation() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()
        val renderer = source("PracticalShoppingBasketRenderer.kt").readSourceText()

        assertTrue(source.contains("fun render(state: PracticalShoppingBasketRenderState)"))
        assertTrue(source.contains("planResult.render(state.result, state.sampleNotice)"))
        assertTrue(source.contains("sampleNotice.text = state.sampleNotice"))
        assertTrue(source.contains("noCoverageSummary.text = state.noCoverageSummary.orEmpty()"))
        assertTrue(source.contains("state.noCoverageSummary == null"))
        assertTrue(source.contains("PracticalShoppingBasketUiAction.OpenHome"))
        assertTrue(source.contains("isEnabled = false"))
        assertTrue(source.contains("actionButton.isEnabled = onAction != null"))
        assertTrue(source.contains("item.requestDetailsSummary"))
        assertTrue(source.contains("item.requestDetailsNotice"))
        assertTrue(source.contains("item.priceCoverageNotice"))
        assertTrue(source.contains("item.plannedPriceText?.let"))
        assertTrue(source.contains("item.plannedPriceNotice?.let"))
        assertTrue(source.contains("item.storeAssignment"))
        assertTrue(source.contains("practicalShoppingPlannedStoreLabel(store)"))
        assertTrue(source.contains("PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE"))
        assertTrue(source.contains("state.collectibleItemKeys"))
        assertTrue(source.contains("state.collectionScopeId"))
        assertTrue(source.contains("SAVED_COLLECTION_SCOPE_ID"))
        assertTrue(source.contains("state.putString(SAVED_COLLECTION_SCOPE_ID"))
        assertTrue(source.contains("state.containsKey(SAVED_COLLECTION_SCOPE_ID)"))
        assertTrue(source.contains("collectionNotice.text = state.collectionNotice.orEmpty()"))
        assertTrue(source.contains("extraStopRuleNotice.text = state.extraStopRuleNotice.orEmpty()"))
        assertTrue(source.contains("private val noCoverageSummary"))
        assertTrue(source.contains("state.extraStopRuleNotice == null"))
        assertTrue(source.contains("state.collectionNotice == null"))
        assertTrue(source.contains("No usable price yet — not ready to collect"))
        assertTrue(source.contains("addItemDetails(item, details, collectionEnabled)"))
        assertTrue(source.contains("practicalShoppingBasketCollectionActionDescription(item, collected)"))
        assertFalse(source.contains("Check-off starts for items with usable planned price coverage."))
        assertTrue(renderer.contains("source.result.itemStoreAssignments"))
        assertTrue(renderer.contains("source.extraStopSettings.notice"))
        assertTrue(renderer.contains("assignment.priceText"))
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
    fun basketUsesUnifiedSummaryCardsAndKeyedProductRows() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()

        listOf(
            "practicalShoppingBasketSummaryProjection",
            "private val basketSummaryCard = ValuePilotProductCard(context)",
            "private val basketItemRowsByKey = LinkedHashMap<ShoppingItemKey, BasketItemRow>()",
            "private inner class BasketItemRow",
            "ValuePilotMotion.reveal(root)",
            "More for this item",
            "state.items.map { it.key }.toSet()",
            "item.personalHistoryNotice",
            "item.savedExactProductNotice"
        ).forEach { required ->
            assertTrue("Missing unified Basket presentation boundary $required", source.contains(required))
        }
        assertFalse(source.contains("itemsContainer.removeAllViews()"))
    }

    @Test
    fun basketSecondaryActionsAreProgressivelyDisclosedAndPurchaseIsIntentional() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()

        listOf(
            "private val secondaryActions = column(padded = false).apply",
            "secondaryActions.visibility = if (expanded) VISIBLE else GONE",
            "setMoreExpanded(!moreExpanded)",
            "ValuePilotMotion.reveal(secondaryActions, animate = expanded)",
            "ConfirmPurchase(",
            "PriceWatchUiAction.ToggleWatch("
        ).forEach { required ->
            assertTrue("Missing progressive Basket action boundary $required", source.contains(required))
        }
    }

    @Test
    fun basketScrollPositionIsSavedPerBasketRouteWithoutChangingBusinessState() {
        val source = File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/java/com/valuepilot/app/MainActivity.kt"
        ).readSourceText()

        listOf(
            "private var basketScrollY = 0",
            "private var basketRouteActive = false",
            "STATE_BASKET_SCROLL_Y",
            "restoreBasketScrollPosition()",
            "if (basketRouteActive) basketScrollY = shellScrollView.scrollY.coerceAtLeast(0)"
        ).forEach { required ->
            assertTrue("Missing Basket state-preservation boundary $required", source.contains(required))
        }
    }

    @Test
    fun ownerCallbackChangesFailClosedForAlreadyRenderedNavigation() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()

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
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()

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
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()

        assertTrue(
            source.contains(
                "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
    }

    @Test
    fun collectionSafetyDisclosureUsesAPoliteAccessibilityLiveRegion() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()

        assertEquals(
            5,
            source
                .split("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE")
                .size - 1
        )
        assertTrue(source.contains("private val collectionNotice"))
        assertTrue(source.contains("projected safety disclosure"))
    }

    @Test
    fun privateIdentityChecklistReconcilesRowsByStableIdentityKey() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()
        val renderStart = source.indexOf("private fun renderPrivateIdentities")
        val reconcileStart = source.indexOf("private fun reconcilePrivateIdentityRows", renderStart)

        assertTrue("Expected private checklist renderer", renderStart >= 0)
        assertTrue("Expected keyed private checklist reconciliation", reconcileStart > renderStart)
        val renderBody = source.substring(renderStart, reconcileStart)

        assertFalse(
            "A private checkbox tap must not rebuild the checklist body",
            renderBody.contains("privateIdentityBody.removeAllViews()")
        )
        listOf(
            "privateIdentityRowsByKey",
            "reconcilePrivateIdentityRows(identities)",
            "privateIdentityRows.removeViewAt",
            "privateIdentityRows.addView",
            "fun updatePrivateIdentityCollected(identityKey: String, collected: Boolean)"
        ).forEach { required ->
            assertTrue("Missing stable private-row boundary $required", source.contains(required))
        }
    }

    @Test
    fun privatePhotoFailureIsAVisibleAccessibleBasketState() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()

        listOf(
            "privateIdentityPhotoError",
            "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE",
            "importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES",
            "fun showPhotoError(message: String)"
        ).forEach { required ->
            assertTrue("Missing Basket photo error boundary $required", source.contains(required))
        }
    }

    @Test
    fun homeAndBasketShareOneUiReadyPlanResultRenderer() {
        val home = source("PracticalShoppingHomeSurfaceView.kt").readSourceText()
        val basket = source("PracticalShoppingBasketSurfaceView.kt").readSourceText()
        val result = source("PracticalShoppingPlanResultSurfaceView.kt").readSourceText()

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
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readSourceText()

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
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readSourceText()

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
    fun resultCardsRepeatTheProvidedSampleDisclosureVisibly() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readSourceText()

        assertTrue(source.contains("require(sampleNotice == null || sampleNotice.isNotBlank())"))
        assertEquals(
            2,
            source.split("sampleNotice?.let { notice ->").size - 1
        )
        assertTrue(source.contains("line(\n                                notice,"))
    }

    @Test
    fun sharedPlanResultAnnouncesProjectedChangesPolitely() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readSourceText()

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
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readSourceText()

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

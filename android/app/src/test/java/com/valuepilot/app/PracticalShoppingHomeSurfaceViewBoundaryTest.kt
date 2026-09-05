package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingHomeSurfaceViewBoundaryTest {

    @Test
    fun queryEditorMechanicallyAppliesImmutableBoundAndVisibleCounter() {
        val source = source().readText()

        assertTrue(
            source.contains("InputFilter.LengthFilter(limit + 1)")
        )
        assertTrue(source.contains("inputLayout.counterMaxLength = limit"))
        assertTrue(source.contains("inputLayout.isCounterEnabled = true"))
        assertTrue(source.contains("syncQueryCharacterLimit(state.queryCharacterLimit)"))
        assertFalse(source.contains("MAX_QUERY_CHARACTERS"))
    }

    @Test
    fun keyboardSubmitUsesTheSameRenderedReadinessAsTheVisibleButton() {
        val source = source().readText()

        assertTrue(source.contains("isEnabled = false"))
        assertTrue(source.contains("submitButton.isEnabled = state.submitEnabled && onSubmit != null"))
        assertTrue(source.contains("if (!submitButton.isEnabled || onSubmit == null)"))
        assertTrue(source.contains("onSubmit?.invoke(input.text?.toString().orEmpty())"))
    }

    @Test
    fun ownerDrivenHomeControlsFailClosedWhenCallbacksAreMissing() {
        val source = source().readText()

        listOf(
            "inputLayout.isEnabled = onQueryChanged != null",
            "input.isEnabled = onQueryChanged != null",
            "compareActionButton.isEnabled = onCompare != null",
            "goodPriceOwnerControls.forEach { control ->",
            "shopAgainOwnerControls.forEach { control ->",
            "removeEnabled = onRemoveItem != null",
            "detailsEnabled = onEditItemDetails != null",
            "enabled = onAddObservedPrice != null",
            "removeEnabled = onRemoveUnknownItem != null",
            "isEnabled = onChickenChoice != null",
            "extraStopSettingsButton.isEnabled =",
            "state.visible && onExtraStopMinimumSavingsChoice != null",
            "isEnabled = onExtraStopMinimumSavingsChoice != null",
            "isEnabled = enabled"
        ).forEach { required ->
            assertTrue("Expected callback readiness binding $required", source.contains(required))
        }
    }

    @Test
    fun ownerCallbackChangesFailClosedForAlreadyRenderedHomeControls() {
        val source = source().readText()

        listOf(
            "private val queryOwnerControls = mutableListOf<View>()",
            "private val submitOwnerControls = mutableListOf<View>()",
            "private val itemRemovalOwnerControls = mutableListOf<View>()",
            "private val unknownRemovalOwnerControls = mutableListOf<View>()",
            "private val offlineCatalogOwnerControls = mutableListOf<View>()",
            "private val itemDetailsOwnerControls = mutableListOf<View>()",
            "private val exactProductOwnerControls = mutableListOf<View>()",
            "private val observedPriceOwnerControls = mutableListOf<View>()",
            "private val chickenChoiceOwnerControls = mutableListOf<View>()",
            "private val extraStopOwnerControls = mutableListOf<View>()",
            "private val extraStopChoiceOwnerControls = mutableListOf<View>()",
            "private val goodPriceOwnerControls = mutableListOf<View>()",
            "private val shopAgainOwnerControls = mutableListOf<View>()",
            "private val privateMemoryExportOwnerControls = mutableListOf<View>()",
            "private val privateMemoryForgetOwnerControls = mutableListOf<View>()",
            "private var hasRenderedState = false",
            "submitOwnerControls.forEach { control ->",
            "control.isEnabled = value != null && lastRenderedSubmitEnabled",
            "control.isEnabled = value != null && hasRenderedState",
            "queryOwnerControls += inputLayout",
            "queryOwnerControls += input",
            "submitOwnerControls += submitButton",
            "goodPriceOwnerControls += this",
            "shopAgainOwnerControls += this",
            "privateMemoryExportOwnerControls += this",
            "privateMemoryForgetOwnerControls += this",
            "extraStopOwnerControls += extraStopSettingsButton",
            "itemRemovalOwnerControls.clear()",
            "unknownRemovalOwnerControls.clear()",
            "itemDetailsOwnerControls.clear()",
            "observedPriceOwnerControls.clear()",
            "chickenChoiceOwnerControls.clear()",
            "extraStopChoiceOwnerControls.clear()",
            "hasRenderedState = true",
            "removeOwnerControls = itemRemovalOwnerControls",
            "removeOwnerControls = unknownRemovalOwnerControls",
            "detailsOwnerControls = offlineCatalogOwnerControls",
            "detailsOwnerControls = itemDetailsOwnerControls",
            "exactProductOwnerControls.clear()",
            "exactProductOwnerControls.forEach { control ->",
            "control.isEnabled = value != null",
            "ownerControls = observedPriceOwnerControls",
            "ownerControls?.add(this)",
            "chickenChoiceOwnerControls += this",
            "extraStopChoiceOwnerControls += this",
            "extraStopOwnerControls.forEach { control ->",
            "extraStopChoiceOwnerControls.forEach { control ->",
            "compareActionButton.isEnabled = value != null && hasRenderedState",
            "goodPriceOwnerControls.forEach { control ->",
            "control.isEnabled = value != null && hasRenderedState",
            "shopAgainActionButton.visibility = if (visible) VISIBLE else GONE",
            "visible && onShopAgain != null && hasRenderedState"
        ).forEach { required ->
            assertTrue("Expected live owner invalidation binding $required", source.contains(required))
        }
    }

    @Test
    fun unknownItemsForwardOfflineCatalogLookupThroughAnOwnerCallback() {
        val source = source().readText()

        listOf(
            "var onFindOfflineCatalogMatch: ((String) -> Unit)? = null",
            "offlineCatalogOwnerControls.forEach { control ->",
            "detailsEnabled = onFindOfflineCatalogMatch != null",
            "detailsLabel = context.getString(R.string.home_unknown_find_matches)",
            "onFindOfflineCatalogMatch?.invoke(token)"
        ).forEach { required ->
            assertTrue("Expected bounded offline catalog Home action $required", source.contains(required))
        }

        listOf(
            "OfflineCatalogDiscoveryEngine",
            "OfflineCatalogProduct(",
            "Money.parse",
            "PracticalShoppingPlanner"
        ).forEach { forbidden ->
            assertFalse("Home View must not own catalog or planner authority through $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun clearingExtraStopOwnerCollapsesExpandedSettingsImmediately() {
        val source = source().readText()
        val setterStart = source.indexOf("var onExtraStopMinimumSavingsChoice:")
        val setterEnd = source.indexOf("var onEditItemDetails", setterStart)
        assertTrue("Expected extra-stop owner setter", setterStart >= 0 && setterEnd > setterStart)

        val setter = source.substring(setterStart, setterEnd)
        assertTrue(setter.contains("if (value == null)"))
        assertTrue(setter.contains("extraStopSettingsExpanded = false"))
        assertTrue(setter.contains("syncExtraStopSettingsVisibility()"))
    }

    @Test
    fun advancedExtraStopControlMechanicallyObeysImmutableVisibility() {
        val source = source().readText()

        assertTrue(
            source.contains(
                "extraStopSettingsButton.visibility = if (state.visible) VISIBLE else GONE"
            )
        )
        assertTrue(
            source.contains(
                "if (!state.visible || onExtraStopMinimumSavingsChoice == null)"
            )
        )
        assertTrue(source.contains("extraStopSettingsExpanded = false"))
        assertTrue(source.contains("syncExtraStopSettingsAccessibility()"))
        assertTrue(source.contains("extraStopSettingsButton.contentDescription"))
        assertTrue(source.contains("practicalShoppingExtraStopSettingsContentDescription"))
        assertTrue(source.contains("currentExtraStopSettingsNotice = state.notice"))
        assertTrue(
            source.contains(
                "state.visible && onExtraStopMinimumSavingsChoice != null"
            )
        )
        assertTrue(source.contains("state.notice?.let"))
        assertTrue(
            source.contains(
                "extraStopSettingsButton.visibility == VISIBLE && extraStopSettingsExpanded"
            )
        )
    }

    @Test
    fun changingHomeStatusFeedbackUsesAPoliteAccessibilityLiveRegion() {
        val source = source().readText()

        assertTrue(
            source.contains(
                "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
    }

    @Test
    fun explicitResultRevealUsesOnlyTheLatestBoundedProjection() {
        val source = source().readText()

        listOf(
            "private var projectedResultVisible = false",
            "projectedResultVisible = state.result != null",
            "fun revealProjectedResult()",
            "if (!projectedResultVisible) return",
            "resultContainer.doOnLayout",
            "if (!projectedResultVisible) return@doOnLayout",
            "val revealHeight = minOf(resultContainer.height, dp(320))",
            "resultContainer.requestRectangleOnScreen(",
            "Rect(0, 0, resultContainer.width, revealHeight)",
            "true"
        ).forEach { required ->
            assertTrue("Expected bounded projected-result reveal binding $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingUiProjector",
            "Money.parse",
            "LocalSamplePracticalShoppingDemo.Status"
        ).forEach { forbidden ->
            assertFalse(
                "Result reveal must not own shopping authority through $forbidden",
                source.contains(forbidden)
            )
        }
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

    @Test
    fun itemDetailsAreRenderedFromImmutableStateAndForwardedAsTypedKeys() {
        val source = source().readText()

        listOf(
            "var onEditItemDetails: ((ShoppingItemKey) -> Unit)? = null",
            "var onAddObservedPrice: ((ShoppingItemKey) -> Unit)? = null",
            "item.observedPriceActionVisible",
            "R.string.home_item_observed_price_action",
            "R.string.home_item_observed_price_action_description",
            "onAddObservedPrice?.invoke(item.key)",
            "item.requestDetailsSummary",
            "item.requestDetailsNotice",
            "item.requestDetailsActionLabel",
            "item.storeAssignment",
            "item.plannedPriceText?.let",
            "item.plannedPriceNotice?.let",
            "item.priceCoverageNotice",
            "line(\"Buy at ${'$'}store\"",
            "line(\"Included in plan: ${'$'}price\"",
            "onEditItemDetails?.invoke(item.key)"
        ).forEach { required ->
            assertTrue("Expected Home item-details binding $required", source.contains(required))
        }

        listOf(
            "ShoppingRequestedQuantity(",
            "ShoppingItemRequestDetail(",
            "ShoppingBrandKey(",
            "PracticalShoppingPlanner",
            "Money.parse"
        ).forEach { forbidden ->
            assertFalse("Home View must not construct or interpret item intent through $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun exactProductChoiceIsAnOwnerDrivenIdentityOnlyHomeAction() {
        val source = source().readText()

        listOf(
            "var onChooseExactProduct: ((ShoppingItemKey) -> Unit)? = null",
            "item.exactProductActionVisible",
            "R.string.home_choose_exact_product",
            "R.string.home_choose_exact_product_description",
            "onChooseExactProduct?.invoke(item.key)",
            "ownerControls = exactProductOwnerControls"
        ).forEach { required ->
            assertTrue("Expected exact-product Home action binding $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "OfflineCatalogDiscoveryEngine",
            "current_price",
            "availability"
        ).forEach { forbidden ->
            assertFalse("Home View must not own identity, price or planner authority through $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun personalHistoryNoticeIsBoundAsReadOnlyItemContext() {
        val source = source().readText()

        assertTrue(source.contains("item.personalHistoryNotice?.let"))
        listOf(
            "privateMemorySummary",
            "renderPrivateMemory(",
            "status = state.privateMemoryStatus",
            "summary = state.privateMemorySummary",
            "privateMemoryReviewActionButton",
            "privateMemoryExportActionButton",
            "privateMemoryForgetActionButton",
            "state.privateMemoryReviewActionVisible",
            "state.privateMemoryExportActionVisible",
            "state.privateMemoryForgetActionVisible",
            "var onReviewPrivateMemory: (() -> Unit)? = null",
            "var onExportPrivateMemory: (() -> Unit)? = null",
            "var onForgetPrivateMemory: (() -> Unit)? = null",
            "onReviewPrivateMemory?.invoke()",
            "onExportPrivateMemory?.invoke()",
            "onForgetPrivateMemory?.invoke()",
            "home_private_memory_review",
            "home_private_memory_export",
            "home_private_memory_forget",
            "privateMemorySummary.visibility",
            "privateMemoryNotice",
            "PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE",
            "R.string.home_private_memory_unavailable",
            "if (reviewActionVisible) VISIBLE else GONE",
            "reviewActionVisible && onReviewPrivateMemory != null && hasRenderedState",
            "exportActionVisible && onExportPrivateMemory != null && hasRenderedState",
            "forgetActionVisible && onForgetPrivateMemory != null && hasRenderedState",
            "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
        ).forEach { required ->
            assertTrue("Expected explicit private-history availability disclosure $required", source.contains(required))
        }
        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "CompareHerePrivatePriceMemoryStore"
        ).forEach { forbidden ->
            assertFalse(
                "Home View must not own personal-history or planner authority through $forbidden",
                source.contains(forbidden)
            )
        }
    }

    @Test
    fun noCoverageSummaryIsRenderedAsImmutableHomePresentation() {
        val source = source().readText()

        listOf(
            "private val noCoverageSummary = line(\"\", 13f, \"#92400E\", true)",
            "addView(noCoverageSummary)",
            "renderNoCoverageSummary(state.noCoverageSummary)",
            "noCoverageSummary.text = summary.orEmpty()",
            "noCoverageSummary.visibility = if (summary == null) GONE else VISIBLE",
            "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
        ).forEach { required ->
            assertTrue("Expected no-coverage summary binding $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "PracticalShoppingUiProjector"
        ).forEach { forbidden ->
            assertFalse("Home View must not decide coverage through $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun goodPriceActionIsAnOwnerDrivenHomeNavigationControl() {
        val source = source().readText()

        listOf(
            "var onGoodPrice: (() -> Unit)? = null",
            "goodPriceActionButton",
            "onGoodPrice?.invoke()",
            "home_good_price_secondary"
        ).forEach { required ->
            assertTrue("Expected Home good-price action binding $required", source.contains(required))
        }

        listOf(
            "GoodPriceCheckRouteCoordinator",
            "CompareHerePriceMemoryEvaluator",
            "Money.parse",
            "PracticalShoppingPlanner"
        ).forEach { forbidden ->
            assertFalse("Home View must not own good-price authority through $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun shopAgainIsAnOwnerDrivenCompletedListAction() {
        val source = source().readText()

        listOf(
            "var onShopAgain: (() -> Unit)? = null",
            "private val shopAgainActionButton",
            "renderShopAgain(state.shopAgainVisible)",
            "R.string.home_shop_again",
            "R.string.home_shop_again_description",
            "onShopAgain?.invoke()"
        ).forEach { required ->
            assertTrue("Expected repeat-list Home action binding $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "GoodPriceCheckRouteCoordinator",
            "CompareHerePrivatePriceMemoryStore"
        ).forEach { forbidden ->
            assertFalse("Shop-again View must not own shopping authority through $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun sharePlanIsAnOwnerDrivenProjectedResultAction() {
        val source = source().readText()

        listOf(
            "private val sharePlanOwnerControls = mutableListOf<View>()",
            "var onSharePlan: (() -> Unit)? = null",
            "private val sharePlanActionButton",
            "renderSharePlan(state.result?.primary != null)",
            "sharePlanActionButton.visibility = if (visible) VISIBLE else GONE",
            "visible && onSharePlan != null && hasRenderedState",
            "R.string.home_share_plan",
            "R.string.home_share_plan_description",
            "sharePlanOwnerControls += this",
            "onSharePlan?.invoke()"
        ).forEach { required ->
            assertTrue("Expected bounded Home share-plan action binding $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingUiProjector",
            "Money.parse"
        ).forEach { forbidden ->
            assertFalse("Home share action must not own shopping or item-detail authority through $forbidden", source.contains(forbidden))
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

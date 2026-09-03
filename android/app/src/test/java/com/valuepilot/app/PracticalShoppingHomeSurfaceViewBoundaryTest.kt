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
            "removeEnabled = onRemoveItem != null",
            "detailsEnabled = onEditItemDetails != null",
            "removeEnabled = onRemoveUnknownItem != null",
            "isEnabled = onChickenChoice != null",
            "isEnabled = onExtraStopMinimumSavingsChoice != null",
            "isEnabled = enabled"
        ).forEach { required ->
            assertTrue("Expected callback readiness binding $required", source.contains(required))
        }
    }

    @Test
    fun advancedExtraStopControlMechanicallyObeysImmutableVisibility() {
        val source = source().readText()

        assertTrue(
            source.contains(
                "extraStopSettingsButton.visibility = if (state.visible) VISIBLE else GONE"
            )
        )
        assertTrue(source.contains("if (!state.visible) extraStopSettingsExpanded = false"))
        assertTrue(source.contains("syncExtraStopSettingsAccessibility()"))
        assertTrue(source.contains("extraStopSettingsButton.contentDescription"))
        assertTrue(source.contains("practicalShoppingExtraStopSettingsContentDescription"))
        assertTrue(source.contains("currentExtraStopSettingsNotice = state.notice"))
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
            "item.requestDetailsSummary",
            "item.requestDetailsNotice",
            "item.requestDetailsActionLabel",
            "item.storeAssignment",
            "item.priceCoverageNotice",
            "line(\"Buy at ${'$'}store\"",
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

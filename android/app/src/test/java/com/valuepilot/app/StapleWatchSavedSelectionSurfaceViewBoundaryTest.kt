package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSavedSelectionSurfaceViewBoundaryTest {

    @Test
    fun physicalSetupViewConsumesConsumerStateAndEmitsTypedActionsOnly() {
        val source = source("StapleWatchSavedSelectionSurfaceView.kt").readText()

        assertTrue(source.contains("StapleWatchSavedSelectionSurfaceRenderer"))
        assertTrue(source.contains("render(state: StapleWatchSavedSelectionUiState)"))
        assertTrue(source.contains("state.selectionSummary"))
        assertTrue(source.contains("selectionSummary(value: String)"))
        assertTrue(source.contains("state.factResolutionProgress"))
        assertTrue(source.contains("factResolutionCard(progress)"))
        assertTrue(
            source.contains(
                "var onAction: ((StapleWatchSavedIdentitySelectionAction) -> Unit)? = null"
            )
        )
        assertTrue(
            source.contains(
                "var onContinueAction: ((StapleWatchSavedIdentityHandoffUiAction) -> Unit)? = null"
            )
        )
        assertTrue(source.contains("setOnClickListener { onAction?.invoke(action) }"))
        assertTrue(source.contains("setOnClickListener { onContinueAction?.invoke(action) }"))
        assertTrue(source.contains("private val ownerBoundActionButtons = mutableListOf<Button>()"))
        assertTrue(
            source.contains(
                "private val ownerBoundContinuationButtons = mutableListOf<Button>()"
            )
        )
        assertTrue(source.contains("ownerBoundActionButtons.forEach { button ->"))
        assertTrue(source.contains("ownerBoundContinuationButtons.forEach { button ->"))
        assertTrue(source.contains("ownerBoundActionButtons.clear()"))
        assertTrue(source.contains("ownerBoundContinuationButtons.clear()"))
        assertTrue(source.contains("ownerBoundActionButtons += this"))
        assertTrue(source.contains("ownerBoundContinuationButtons += this"))
        assertTrue(source.contains("visibility = View.GONE"))
        assertFalse(source.contains("visibility = View.VISIBLE"))

        listOf(
            "PracticalShoppingSavedExactPreferenceState",
            "PracticalShoppingSavedExactPreferenceDisplayMetadata",
            "StapleWatchSavedIdentitySelectionUiProjector",
            "StapleWatchSavedIdentitySelectionReducer",
            "StapleWatchSavedIdentityHandoffGate",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "ShoppingItemKey",
            "ShoppingStoreKey",
            "ShoppingRequest",
            "Money",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "System.currentTimeMillis",
            "StapleWatchFactResolutionHost",
            "StapleWatchFactResolutionSession",
            "StapleWatchEconomicEvidencePreconditions"
        ).forEach { forbidden ->
            assertFalse("Physical staple setup View must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun physicalContinuationIsOwnerControlledAndDoesNotInterpretReadiness() {
        val source = source("StapleWatchSavedSelectionSurfaceView.kt").readText()

        assertTrue(source.contains("state.continueAction"))
        assertTrue(source.contains("?.takeIf { onContinueAction != null }"))
        assertTrue(source.contains("label = requireNotNull(state.continueActionLabel)"))
        assertTrue(source.contains("action: StapleWatchSavedIdentityHandoffUiAction"))
        assertFalse(source.contains("state.status"))
        assertFalse(source.contains("READY_FOR_FACT_CHECK"))
        assertFalse(source.contains("DISPLAY_METADATA_INCOMPLETE"))
        assertFalse(source.contains("StapleWatchSavedIdentityHandoffUiAction.Request"))
    }

    @Test
    fun physicalSetupViewDoesNotTurnContinuationIntoFactOrNavigationAuthority() {
        val source = source("StapleWatchSavedSelectionSurfaceView.kt").readText()

        assertTrue(source.contains("action = row.action"))
        assertTrue(source.contains("actionLabel = row.actionLabel"))
        assertTrue(source.contains("actionDescription = row.actionDescription"))
        assertTrue(source.contains("contentDescription = actionDescription"))
        assertTrue(source.contains("this.contentDescription = contentDescription"))
        assertFalse(source.contains("startActivity"))
        assertFalse(source.contains("Intent("))
        assertFalse(source.contains("requestIdentityHandoff"))
        assertFalse(source.contains("Check prices"))
        assertFalse(source.contains("Start watching"))
    }

    @Test
    fun projectedSelectionFeedbackUsesPoliteAccessibilityLiveRegions() {
        val source = source("StapleWatchSavedSelectionSurfaceView.kt").readText()

        assertEquals(
            3,
            source
                .split("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE")
                .size - 1
        )
        assertTrue(source.contains("selectionSummary(value: String)"))
        assertTrue(source.contains("state.notice?.let { message -> addView(notice(message)) }"))
        assertTrue(source.contains("state.factResolutionProgress"))
        assertTrue(source.contains("factResolutionCard(progress)"))
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

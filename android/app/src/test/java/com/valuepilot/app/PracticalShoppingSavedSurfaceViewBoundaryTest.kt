package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingSavedSurfaceViewBoundaryTest {

    @Test
    fun rowActionsConsumeProjectedItemSpecificAccessibilityDescriptions() {
        val source = source().readText()

        assertTrue(source.contains("actionDescription = row.actionDescription"))
        assertTrue(source.contains("contentDescription = requireNotNull(actionDescription)"))
        assertTrue(source.contains("this.contentDescription = contentDescription"))
        assertFalse(source.contains("Remove saved product"))
        assertFalse(source.contains("Remove saved store"))
    }

    @Test
    fun clearAllRequiresExplicitConfirmationBeforeEmittingTheTypedAction() {
        val source = source().readText()

        assertTrue(source.contains("showClearAllConfirmation(action)"))
        assertTrue(source.contains("AlertDialog.Builder(context)"))
        assertTrue(source.contains("R.string.saved_clear_all_confirmation_title"))
        assertTrue(source.contains("R.string.saved_clear_all_confirmation_body"))
        assertTrue(source.contains("R.string.saved_clear_all_confirmation_confirm"))
        assertTrue(source.contains("onAction?.invoke(action)"))
    }

    @Test
    fun projectedSavedStatusFeedbackUsesAPoliteAccessibilityLiveRegion() {
        val source = source().readText()

        assertTrue(
            source.contains(
                "accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
    }

    @Test
    fun projectedSavedWarningFeedbackUsesAPoliteAccessibilityLiveRegion() {
        val source = source().readText()

        assertEquals(
            2,
            source
                .split("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE")
                .size - 1
        )
        assertTrue(source.contains("private fun notice(value: String): TextView"))
        assertTrue(source.contains("unresolved display-metadata warning"))
    }

    @Test
    fun ownerCallbackChangesFailClosedForAlreadyRenderedButtons() {
        val source = source().readText()

        assertTrue(source.contains("private val ownerBoundButtons = mutableListOf<Button>()"))
        assertTrue(source.contains("set(value)"))
        assertTrue(source.contains("ownerBoundButtons.forEach { button ->"))
        assertTrue(source.contains("button.isEnabled = value != null"))
        assertTrue(source.contains("ownerBoundButtons.clear()"))
        assertTrue(source.contains("ownerBoundButtons += this"))
    }

    @Test
    fun physicalSavedViewHasNoPersistenceProviderOrDecisionAuthority() {
        val source = source().readText()

        listOf(
            "SharedPreferences",
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedLifecycleController",
            "PracticalShoppingSavedExactPreferenceUiProjector",
            "Money(",
            "StapleWatchEconomicEvaluator",
            "System.currentTimeMillis",
            "WorkManager",
            "NotificationManager",
            "Http",
            "URL(",
            "startActivity",
            "Intent("
        ).forEach { forbidden ->
            assertFalse("Physical Saved View must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/PracticalShoppingSavedSurfaceView.kt").also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}

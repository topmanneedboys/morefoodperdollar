package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSavedSelectionSurfaceViewBoundaryTest {

    @Test
    fun physicalSetupViewConsumesConsumerStateAndEmitsTypedSelectionActionsOnly() {
        val source = source("StapleWatchSavedSelectionSurfaceView.kt").readText()

        assertTrue(source.contains("StapleWatchSavedSelectionSurfaceRenderer"))
        assertTrue(source.contains("render(state: StapleWatchSavedSelectionUiState)"))
        assertTrue(
            source.contains(
                "var onAction: ((StapleWatchSavedIdentitySelectionAction) -> Unit)? = null"
            )
        )
        assertTrue(source.contains("setOnClickListener { onAction?.invoke(action) }"))
        assertTrue(source.contains("visibility = View.GONE"))
        assertFalse(source.contains("visibility = View.VISIBLE"))

        listOf(
            "PracticalShoppingSavedExactPreferenceState",
            "PracticalShoppingSavedExactPreferenceDisplayMetadata",
            "StapleWatchSavedIdentitySelectionUiProjector",
            "StapleWatchSavedIdentitySelectionReducer",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "ShoppingItemKey",
            "ShoppingStoreKey",
            "Money",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse("Physical staple setup View must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun physicalSetupViewDoesNotTurnReadyPresentationIntoFactOrNavigationAuthority() {
        val source = source("StapleWatchSavedSelectionSurfaceView.kt").readText()

        assertTrue(source.contains("action = row.action"))
        assertTrue(source.contains("actionLabel = row.actionLabel"))
        assertFalse(source.contains("state.status"))
        assertFalse(source.contains("READY_FOR_FACT_CHECK"))
        assertFalse(source.contains("DISPLAY_METADATA_INCOMPLETE"))
        assertFalse(source.contains("startActivity"))
        assertFalse(source.contains("Intent("))
        assertFalse(source.contains("Check prices"))
        assertFalse(source.contains("Start watching"))
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

package com.valuepilot.app

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

package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySourcesLicencesBoundaryTest {

    @Test
    fun `settings owns sources and licence navigation instead of the Saved landing surface`() {
        val layout = source("src/main/res/layout/activity_shell.xml").readText()
        assertTrue(layout.contains("android:id=\"@+id/settingsExperience\""))
        assertFalse(layout.contains("sourcesLicencesButton"))
        assertFalse(layout.contains("dataStatusButton"))
    }

    @Test
    fun `activity routes Settings actions through the shell`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        listOf(
            "settingsExperience.onAction = ::handleSettingsAction",
            "private fun handleSettingsAction(action: PracticalShoppingSettingsAction)",
            "AppShellIntent.OpenSettingsDataSources",
            "AppShellIntent.OpenSettingsLicences",
            "AppShellIntent.OpenSettingsTechnicalDetails",
            "PracticalShoppingSettingsPresentation.page("
        ).forEach { required ->
            assertTrue("Expected Settings boundary: $required", activity.contains(required))
        }
        listOf(
            "startActivity(Intent.ACTION_VIEW",
            "PracticalShoppingPlanner",
            "Money.parse"
        ).forEach { forbidden ->
            assertFalse("Settings must not own business/network authority: $forbidden", activity.contains(forbidden))
        }
    }

    @Test
    fun `licence page preserves dataset attribution and external photo boundary`() {
        val presentation = source(
            "src/main/java/com/valuepilot/app/PracticalShoppingSettingsPresentation.kt"
        ).readText()
        listOf(
            "Precios Claros",
            "datos.gob.ar",
            "Creative Commons Attribution 4.0",
            "retailer logos",
            "Google Images",
            "not copied"
        ).forEach { required ->
            assertTrue("Expected attribution boundary: $required", presentation.contains(required))
        }
    }

    private fun source(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

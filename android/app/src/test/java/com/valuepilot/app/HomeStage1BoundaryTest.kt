package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStage1BoundaryTest {

    @Test
    fun shellRoutesHomeActionsThroughTheExistingReducerAndRelocatesDiagnostics() {
        val source = source("MainActivity.kt")
        val layout = source("res/layout/activity_shell.xml")

        assertTrue(source.contains("homeExperience.onOpenSearch"))
        assertTrue(source.contains("AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH)"))
        assertTrue(source.contains("homeExperience.onOpenBasket"))
        assertTrue(source.contains("AppShellIntent.SelectPrimary(AppPrimaryTab.BASKET)"))
        assertTrue(source.contains("settingsExperience.visibility = if (settingsVisible) View.VISIBLE else View.GONE"))
        assertTrue(source.contains("val savedVisible = state.route == AppRoute.SAVED"))
        assertTrue(layout.contains("android:id=\"@+id/shellTagline\""))
    }

    @Test
    fun ArgentinaDemoAndEmptyTripCurrencyAreExplicitlyScopedWithoutChangingGenericMoneySupport() {
        val demo = source("LocalSamplePracticalShoppingDemo.kt")
        val activity = source("MainActivity.kt")

        assertTrue(demo.contains("internal const val SAMPLE_CURRENCY = \"ARS\""))
        assertTrue(demo.contains("Money.parse(\"1.00\", SAMPLE_CURRENCY)"))
        assertTrue(activity.contains("?: \"ARS\""))
        assertTrue(source("src/test/java/com/valuepilot/app/LegacyCanadianSourceFootprintTest.kt").contains("accepts CAD"))
    }

    private fun source(name: String): String {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                if (name.startsWith("res/") || name.startsWith("src/test/")) {
                    File(directory, if (name.startsWith("src/test/")) name else "app/src/main/$name")
                } else {
                    File(directory, "app/src/main/java/com/valuepilot/app/$name")
                }
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $name")
    }
}

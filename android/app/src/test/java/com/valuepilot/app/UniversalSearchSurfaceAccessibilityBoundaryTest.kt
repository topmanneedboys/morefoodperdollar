package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UniversalSearchSurfaceAccessibilityBoundaryTest {

    @Test
    fun `search status is a polite live region for async state changes`() {
        val activity = source("MainActivity.kt").readText()
        val layout = layoutSource().readText()
        val statusStart = layout.indexOf("android:id=\"@+id/searchStatus\"")
        val statusEnd = layout.indexOf("/>", statusStart)

        assertTrue("Search status must exist in the shell", statusStart >= 0)
        assertTrue("Search status must be a complete TextView block", statusEnd > statusStart)
        val statusBlock = layout.substring(statusStart, statusEnd)
        assertTrue(statusBlock.contains("android:accessibilityLiveRegion=\"polite\""))
        assertTrue(activity.contains("searchStatus.text = state.statusText"))
    }

    @Test
    fun `search presentation keeps decision and network authority outside the status view`() {
        val activity = source("MainActivity.kt").readText()

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy(",
            "Money.parse",
            "RankingEngine",
            "HttpURLConnection",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        ).forEach { forbidden ->
            assertFalse("Search status host must not gain authority through $forbidden", activity.contains(forbidden))
        }
    }

    private fun source(name: String): File =
        moduleFile("src/main/java/com/valuepilot/app/$name")

    private fun layoutSource(): File =
        moduleFile("src/main/res/layout/activity_shell.xml")

    private fun moduleFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

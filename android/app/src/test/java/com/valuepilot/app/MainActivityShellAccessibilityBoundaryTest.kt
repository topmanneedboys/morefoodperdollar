package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityShellAccessibilityBoundaryTest {

    @Test
    fun `selected destination title is a polite live region`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val layout = source("src/main/res/layout/activity_shell.xml").readText()
        val titleStart = layout.indexOf("android:id=\"@+id/screenTitle\"")
        val titleEnd = layout.indexOf("/>", titleStart)

        assertTrue("Shell title must exist", titleStart >= 0)
        assertTrue("Shell title must be a complete TextView block", titleEnd > titleStart)
        val titleBlock = layout.substring(titleStart, titleEnd)
        assertTrue(titleBlock.contains("android:accessibilityLiveRegion=\"polite\""))
        assertTrue(activity.contains("screenTitle.text = copy.title"))
    }

    @Test
    fun `shell title announcement remains presentation-only`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()

        listOf(
            "PracticalShoppingPlanner(",
            "Money.parse",
            "HttpURLConnection",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "NotificationManager",
            "WorkManager"
        ).forEach { forbidden ->
            assertFalse("Shell title host must not own $forbidden", activity.contains(forbidden))
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

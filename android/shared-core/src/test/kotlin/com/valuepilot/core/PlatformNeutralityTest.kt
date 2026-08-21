package com.valuepilot.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformNeutralityTest {
    @Test fun productionSourcesContainNoPlatformAdapters() {
        val root = File(System.getProperty("user.dir"), "src/main/kotlin")
        assertTrue(root.isDirectory)
        val forbidden = listOf(
            "import android.", "import androidx.", "AccessibilityNodeInfo", "WindowManager",
            "AccessibilityService", "LocalFoodModel", "org.json", "java.io.", "java.net.",
            "System.currentTimeMillis", "Uber", "DoorDash"
        )
        root.walkTopDown().filter(File::isFile).forEach { source ->
            val text = source.readText()
            forbidden.forEach { token -> assertFalse("${source.name} contains $token", text.contains(token)) }
        }
    }
}

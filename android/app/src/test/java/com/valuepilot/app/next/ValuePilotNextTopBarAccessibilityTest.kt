package com.valuepilot.app.next

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ValuePilotNextTopBarAccessibilityTest {

    @Test
    fun `back and exit controls have clear spoken labels`() {
        val source = appSource()

        assertTrue(source.contains("contentDescription = \"Back\""))
        assertTrue(
            source.contains(
                "contentDescription = \"Exit preview and return to the classic app\""
            )
        )
        assertTrue(source.contains("Text(\"Exit\", style = MaterialTheme.typography.labelLarge"))
    }

    private fun appSource(): String {
        var directory = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "android/app/src/debug/java/com/valuepilot/app/next/ValuePilotNextApp.kt"
                )
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate ValuePilotNextApp.kt")
    }
}

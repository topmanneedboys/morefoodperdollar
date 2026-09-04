package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityDataStatusBoundaryTest {

    @Test
    fun `shell exposes data status below source disclosure`() {
        val layout = source("src/main/res/layout/activity_shell.xml").readText()
        val sources = layout.indexOf("android:id=\"@+id/sourcesLicencesButton\"")
        val data = layout.indexOf("android:id=\"@+id/dataStatusButton\"")
        val end = layout.indexOf("/>", data)

        assertTrue("Sources action must exist", sources >= 0)
        assertTrue("Data status action must exist", data > sources)
        assertTrue("Data status action must be a complete view block", end > data)
        assertTrue(layout.substring(data, end).contains("android:text=\"@string/data_status\""))
    }

    @Test
    fun `activity wires only a descriptive data status dialog`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val strings = source("src/main/res/values/strings.xml").readText()

        listOf(
            "private lateinit var dataStatusButton: MaterialButton",
            "dataStatusButton = findViewById(R.id.dataStatusButton)",
            "dataStatusButton.setOnClickListener { showDataStatus() }",
            "private fun showDataStatus()",
            "PracticalShoppingDataStatusPresentation.from(",
            ".setTitle(R.string.data_status_title)",
            ".setMessage(presentation.message)",
            "dataStatusDialog?.dismiss()"
        ).forEach { required ->
            assertTrue("Expected data status presentation boundary: $required", activity.contains(required))
        }

        listOf(
            "data_status",
            "ValuePilot data status",
            "Open Food Facts",
            "No flyer content is copied here"
        ).forEach { required ->
            assertTrue("Expected truthful status/disclosure copy: $required", strings.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection",
            "startActivity(Intent.ACTION_VIEW",
            "provider economics"
        ).forEach { forbidden ->
            assertFalse("Data status must not own business/network authority: $forbidden", activity.contains(forbidden))
        }
    }

    @Test
    fun `data status presentation remains identity and uncertainty only`() {
        val source = source("src/main/java/com/valuepilot/app/PracticalShoppingDataStatusPresentation.kt").readText()

        listOf(
            "current-offer records",
            "Unknown prices stay unknown",
            "Not included yet",
            "No flyer content is copied",
            "fictional sample data"
        ).forEach { required ->
            assertTrue("Expected explicit uncertainty boundary: $required", source.contains(required))
        }

        listOf(
            "HttpURLConnection",
            "URL(",
            "PracticalShoppingPlanner",
            "Money.parse",
            "rank"
        ).forEach { forbidden ->
            assertFalse("Status presentation must not add authority: $forbidden", source.contains(forbidden))
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

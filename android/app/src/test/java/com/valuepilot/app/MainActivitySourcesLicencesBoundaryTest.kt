package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySourcesLicencesBoundaryTest {

    @Test
    fun `shell exposes a clearly labelled source disclosure action`() {
        val layout = source("src/main/res/layout/activity_shell.xml").readText()
        val start = layout.indexOf("android:id=\"@+id/sourcesLicencesButton\"")
        val end = layout.indexOf("/>", start)

        assertTrue("Sources and licences action must exist", start >= 0)
        assertTrue("Sources and licences action must be a complete view block", end > start)
        val block = layout.substring(start, end)
        assertTrue(block.contains("android:text=\"@string/sources_licences\""))
        assertTrue(block.contains("android:textAllCaps=\"false\""))
        assertTrue(
            "Disclosure must remain below the screen-specific footnote",
            layout.indexOf("android:id=\"@+id/screenFootnote\"") < start
        )
    }

    @Test
    fun `foreground activity owns only the presentation dialog`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val strings = source("src/main/res/values/strings.xml").readText()

        listOf(
            "private lateinit var sourcesLicencesButton: MaterialButton",
            "sourcesLicencesButton = findViewById(R.id.sourcesLicencesButton)",
            "sourcesLicencesButton.setOnClickListener { showSourcesLicences() }",
            "private fun showSourcesLicences()",
            "AlertDialog.Builder(this)",
            ".setTitle(R.string.sources_licences_title)",
            ".setMessage(R.string.sources_licences_body)",
            ".setPositiveButton(android.R.string.ok, null)"
        ).forEach { required ->
            assertTrue("Expected source disclosure presentation boundary: $required", activity.contains(required))
        }

        listOf(
            "Open Food Facts",
            "ODbL-1.0",
            "offline search only",
            "not live retailer offers",
            "fictional demo data",
            "authorized current-offer feed",
            "No flyer content is copied here"
        ).forEach { required ->
            assertTrue("Expected truthful disclosure copy: $required", strings.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection",
            "startActivity(Intent.ACTION_VIEW",
            "INTERNET"
        ).forEach { forbidden ->
            assertFalse("Source disclosure must not own business/network authority: $forbidden", activity.contains(forbidden))
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

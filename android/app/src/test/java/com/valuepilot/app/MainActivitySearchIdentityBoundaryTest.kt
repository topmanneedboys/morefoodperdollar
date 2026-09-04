package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySearchIdentityBoundaryTest {

    @Test
    fun `search exposes a bounded signed identity lookup and review handoff`() {
        val source = source().readText()
        val layout = layoutSource().readText()

        listOf(
            "searchIdentityButton = findViewById(R.id.searchIdentityButton)",
            "searchIdentityButton.setOnClickListener { showSearchIdentityMatches() }",
            "searchIdentityButton.isEnabled = practicalShoppingSearchIdentityEnabled(state)",
            "dismissSearchIdentityDialog()\n        suppressSearchInputCallback = true",
            "private fun submitSearch() {\n        dismissSearchIdentityDialog()",
            "private fun showSearchIdentityMatches()",
            "BundledOfflineCatalog.discoverSupportedRegions(",
            "PracticalShoppingSearchIdentityPresentation.from(",
            "showSearchIdentityResult(",
            "openComparisonWithSharedText(match.displayName)",
            "ComparisonActivity.EXTRA_SHARED_TEXT",
            "searchIdentityDialog?.dismiss()",
            "cancelSearchIdentityLookup()"
        ).forEach { required ->
            assertTrue("Expected Search identity boundary $required", source.contains(required))
        }
        assertTrue(layout.contains("android:id=\"@+id/searchIdentityButton\""))
        assertTrue(layout.contains("@string/search_identity_action_description"))
    }

    @Test
    fun `search identity path does not become a price or network provider`() {
        val source = source().readText()
        val identityStart = source.indexOf("private fun showSearchIdentityMatches()")
        val identityEnd = source.indexOf("private fun executeSearch", identityStart)
        assertTrue(identityStart >= 0)
        assertTrue(identityEnd > identityStart)
        val identityPath = source.substring(identityStart, identityEnd)

        listOf(
            "ProductSearchProvider",
            "UniversalSearchController",
            "PracticalShoppingPlanner",
            "Money.parse",
            "HttpURLConnection",
            "android.permission.INTERNET",
            "ACCESS_NETWORK_STATE",
            "currentPrice",
            "availability"
        ).forEach { forbidden ->
            assertFalse("Search identity path must not own $forbidden", identityPath.contains(forbidden))
        }
        assertTrue(identityPath.contains("maximumSnapshotAgeMillis = OFFLINE_CATALOG_MAX_AGE_MILLIS"))
        assertTrue(identityPath.contains("isDestroyed"))
        assertTrue(identityPath.contains("requestId != searchIdentityRequestId"))
    }

    private fun source(): File = moduleFile("src/main/java/com/valuepilot/app/MainActivity.kt")

    private fun layoutSource(): File = moduleFile("src/main/res/layout/activity_shell.xml")

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

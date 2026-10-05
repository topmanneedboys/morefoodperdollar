package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStage2BoundaryTest {

    @Test
    fun searchSurfaceKeepsOnlyTheFieldResultsAndPrimaryNavigationVisible() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt")
        val layout = source("src/main/res/layout/activity_shell.xml")
        val strings = source("src/main/res/values/strings.xml")

        assertTrue(activity.contains("state.route == AppRoute.HOME || state.route == AppRoute.SEARCH || state.route == AppRoute.SAVED"))
        assertTrue(layout.contains("android:id=\"@+id/searchQuickActions\""))
        assertTrue(layout.contains("android:id=\"@+id/searchIdentityBoundaryCard\""))
        assertTrue(layout.contains("android:id=\"@+id/searchAreaLabel\""))
        listOf("searchAreaLabel", "searchQuickActions", "searchIdentityBoundaryCard").forEach { id ->
            val block = layout.substringAfter("android:id=\"@+id/$id\"").substringBefore("/>")
            assertTrue("$id should stay out of the default Search surface", block.contains("android:visibility=\"gone\""))
        }
        assertTrue(strings.contains("<string name=\"search_hint\">Search products</string>"))
        assertTrue(strings.contains("<string name=\"search_load_more\">More results</string>"))
        assertFalse(strings.contains("Search Argentina products"))
        assertTrue(activity.contains("state.mode == ArgentinaLiveSearchSurfaceMode.STALE_RELEASE"))
        assertTrue(activity.contains("R.string.search_again"))
    }

    @Test
    fun resultCardHasOnePrimaryActionAndNoPermanentExplorerControls() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt")
        val start = activity.indexOf("private fun createSearchResultView(")
        val end = activity.indexOf("private fun openSearchProductHistory(", start)
        assertTrue(start >= 0)
        assertTrue(end > start)
        val card = activity.substring(start, end)

        assertTrue(card.contains("SearchResultCardPresentationPolicy.project"))
        assertTrue(card.contains("ValuePilotProductContextMenu(this)"))
        assertTrue(card.contains("content.addView(addHomeButton)"))
        assertFalse(card.contains("setOnClickListener { openComparisonWithSharedText"))
        assertFalse(card.contains("picturePlaceholder"))
        assertFalse(card.contains("search_identity_source"))
        assertFalse(card.contains("search_identity_photo_disclosure"))
        assertFalse(card.contains("search_identity_open_compare"))
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

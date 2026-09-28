package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySearchSubmissionBoundaryTest {

    @Test
    fun `keyboard button and quick entries share the live submission boundary`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")

        listOf(
            "searchInput.setOnEditorActionListener",
            "submitArgentinaSearch()",
            "searchButton.setOnClickListener { submitArgentinaSearch() }",
            "argentinaSearchHost.submit(rawQuery)",
            "setSearchQuery(query)",
            "argentinaSearchHost.queryChanged(query)"
        ).forEach { required ->
            assertTrue("Expected live Search boundary $required", activity.contains(required))
        }
        assertFalse(activity.contains("submitSearch()"))
        assertFalse(activity.contains("LocalSampleProductSearchProvider"))
        assertFalse(activity.contains("UniversalSearchController"))
    }

    @Test
    fun `surface host is user-triggered and has no startup submission`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val host = source("src/main/java/com/valuepilot/app/ArgentinaLiveSearchSurface.kt").readText()

        assertTrue(activity.contains("ArgentinaLiveSearchSurfaceHost.createProduction"))
        assertTrue(activity.contains("argentinaSearchHost.restoreForRevalidation(searchQuery)"))
        assertFalse(activity.contains("argentinaSearchHost.invalidateForRouteChange()"))
        assertFalse(activity.contains("searchInput.post { submit"))
        assertTrue(host.contains("fun submit(rawQuery: String)"))
        assertTrue(host.contains("coordinator.submit(rawQuery, area.context())"))
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

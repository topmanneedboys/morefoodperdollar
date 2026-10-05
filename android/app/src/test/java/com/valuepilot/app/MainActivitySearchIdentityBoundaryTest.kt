package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySearchIdentityBoundaryTest {

    @Test
    fun `visible Search is Argentina identity-only and supports Compare handoff`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val layout = source("src/main/res/layout/activity_shell.xml").readText()
        val strings = source("src/main/res/values/strings.xml").readText()

        listOf(
            "ArgentinaLiveSearchSurfaceHost",
            "ArgentinaQualifiedLaunchAreas.BuenosAiresCaba",
            "searchAreaLabel",
            "PracticalShoppingSearchIdentityPresentation.Match",
            "packageQuantityLabel",
            "SearchResultCardPresentationPolicy.project",
            "addSearchIdentityToHome",
            "search_identity_add_home_draft",
            "search_identity_no_current_price",
            "searchResultsCount",
            "R.plurals.search_results_count"
        ).forEach { required ->
            assertTrue("Expected visible identity boundary $required", activity.contains(required))
        }
        assertTrue(layout.contains("android:id=\"@+id/searchAreaLabel\""))
        assertTrue(layout.contains("android:id=\"@+id/searchResultsCount\""))
        assertTrue(strings.contains("package size appears only when the source supplies it explicitly"))
        assertTrue(strings.contains("%1\$d results"))
        assertFalse(strings.contains("not live prices, stock, availability, package quantities"))
        assertFalse(layout.contains("@+id/searchIdentityButton"))
        assertFalse(activity.contains("LocalSampleProductSearchProvider"))
        assertFalse(activity.contains("UniversalSearchController"))
        assertFalse(activity.contains("row.priceSummary"))
        assertFalse(activity.contains("row.metricLabel"))
        assertFalse(activity.contains("row.best"))
    }

    @Test
    fun `identity cards may show bounded prices but do not expose ranking or availability authority`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val cardStart = activity.indexOf("private fun createSearchResultView(")
        val cardEnd = activity.indexOf("private fun copyFor", cardStart)
        assertTrue(cardStart >= 0)
        assertTrue(cardEnd > cardStart)
        val card = activity.substring(cardStart, cardEnd)
        listOf(
            "metricLabel",
            "best_value",
            "rank",
            "inventory",
            "availability",
            "merchant availability"
        ).forEach { forbidden ->
            assertFalse("Identity card must not own $forbidden", card.contains(forbidden, ignoreCase = true))
        }
        assertTrue(card.contains("SearchResultCardPresentationPolicy.project"))
        assertTrue(card.contains("qualifiedOffers"))
        assertTrue(card.contains("offer.listPrice"))
        assertTrue(card.contains("stock unknown"))
        assertTrue(card.contains("search_identity_no_current_price"))
        assertTrue(card.contains("search_identity_add_home_draft"))
        assertTrue(card.contains("search_identity_package_size"))
        assertTrue(card.contains("ValuePilotProductContextMenu(this)"))
        assertFalse(card.contains("search_identity_source"))
        assertFalse(card.contains("search_identity_open_compare"))
        assertTrue(card.contains("minHeight = dp(48)"))
        assertTrue(card.contains("ValuePilotProductCard(this)"))
    }

    @Test
    fun `activity recreation restores only the query and asks for fresh validation`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val layout = source("src/main/res/layout/activity_shell.xml").readText()
        val saveStart = activity.indexOf("override fun onSaveInstanceState(outState: Bundle)")
        val destroyStart = activity.indexOf("override fun onDestroy()", saveStart)
        assertTrue(saveStart >= 0)
        assertTrue(destroyStart > saveStart)
        val savedState = activity.substring(saveStart, destroyStart)

        assertTrue(savedState.contains("STATE_SEARCH_QUERY"))
        assertTrue(savedState.contains("STATE_SEARCH_WAS_SUBMITTED"))
        assertTrue(savedState.contains("STATE_SEARCH_SCROLL_Y"))
        assertFalse(savedState.contains("argentinaSearchSurfaceState"))
        assertTrue(activity.contains("ArgentinaLiveSearchSurfaceHost.createProduction("))
        assertTrue(activity.contains("argentinaSearchHost.restoreForRevalidation(searchQuery)"))
        assertTrue(activity.contains("R.string.search_again"))
        assertTrue(layout.contains("android:saveEnabled=\"false\""))
        assertTrue(activity.contains("argentinaSearchHost.close()"))
    }

    @Test
    fun `search session stays mounted across primary tab changes and reuses unchanged cards`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        assertTrue(activity.contains("val enteringSearch = searchVisible && !searchRouteActive"))
        assertTrue(activity.contains("searchScrollY = shellScrollView.scrollY"))
        assertFalse(activity.contains("argentinaSearchHost.invalidateForRouteChange()"))
        assertTrue(activity.contains("if (matches != renderedSearchMatches)"))
        assertTrue(activity.contains("renderedSearchMatches = matches"))
        assertTrue(activity.contains("shellScrollView.scrollTo(0, preservedScrollY)"))
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

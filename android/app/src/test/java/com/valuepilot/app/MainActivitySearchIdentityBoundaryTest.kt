package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivitySearchIdentityBoundaryTest {

    @Test
    fun `visible Search keeps exact identities and qualified evidence bounded`() {
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
            "openComparisonWithSharedText(match.displayName)",
            "addSearchIdentityToHome",
            "search_identity_add_home_draft",
            "search_identity_add_short",
            "Argentina SEPA identity result",
            "searchResultsCount",
            "R.plurals.search_results_count",
            "search_identity_unit_rate_unavailable",
            "search_identity_availability_unknown",
            "search_identity_source_short",
            "search_identity_price_unavailable",
            "search_identity_store_context_unavailable"
        ).forEach { required ->
            assertTrue("Expected visible identity boundary $required", activity.contains(required))
        }
        assertTrue(layout.contains("android:id=\"@+id/searchAreaLabel\""))
        assertTrue(layout.contains("android:id=\"@+id/searchResultsCount\""))
        assertTrue(layout.contains("android:id=\"@+id/searchDesignTitle\""))
        assertTrue(layout.contains("android:id=\"@+id/searchInputLayout\""))
        assertTrue(strings.contains("package size appears only when the source supplies it explicitly"))
        assertTrue(strings.contains("Showing %1\$d matching identity suggestions."))
        assertFalse(strings.contains("not live prices, stock, availability, package quantities"))
        assertFalse(layout.contains("@+id/searchIdentityButton"))
        assertFalse(activity.contains("LocalSampleProductSearchProvider"))
        assertFalse(activity.contains("UniversalSearchController"))
        assertFalse(activity.contains("row.priceSummary"))
        assertFalse(activity.contains("row.metricLabel"))
        assertFalse(activity.contains("row.best"))
    }

    @Test
    fun `qualified cards expose evidence without ranking or availability authority`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val cardStart = activity.indexOf("private fun createSearchResultView(")
        val cardEnd = activity.indexOf("private fun copyFor", cardStart)
        assertTrue(cardStart >= 0)
        assertTrue(cardEnd > cardStart)
        val card = activity.substring(cardStart, cardEnd)
        listOf(
            "offerSummary",
            "qualifiedOffers",
            "showQualifiedOfferDetails",
            "best_value",
            "rank",
            "inventory",
            "merchant availability"
        ).forEach { forbidden ->
            if (forbidden == "best_value" || forbidden == "rank" || forbidden == "inventory" || forbidden == "merchant availability") {
                assertFalse("Search card must not own $forbidden", card.contains(forbidden, ignoreCase = true))
            } else {
                assertTrue("Search card must expose $forbidden", card.contains(forbidden))
            }
        }
        assertTrue(card.contains("caveat"))
        assertTrue(card.contains("openComparisonWithSharedText"))
        assertTrue(card.contains("search_identity_open_compare"))
        assertTrue(card.contains("search_identity_add_home_draft"))
        assertTrue(card.contains("search_identity_no_verified_picture"))
        assertTrue(card.contains("search_identity_package_size"))
        assertTrue(card.contains("search_identity_unit_rate_unavailable"))
        assertTrue(card.contains("search_identity_availability_unknown"))
        assertTrue(card.contains("search_identity_source_short"))
        assertTrue(card.contains("search_identity_add_short"))
        assertTrue(card.contains("minHeight = dp(48)"))
        assertTrue(card.contains("#FFFFFF"))
        assertTrue(card.contains("#E8672D"))
    }

    @Test
    fun `Search cards remain wrap safe and explicit for missing price evidence`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val cardStart = activity.indexOf("private fun createSearchResultView(")
        val cardEnd = activity.indexOf("private fun showQualifiedOfferDetails", cardStart)
        assertTrue(cardStart >= 0)
        assertTrue(cardEnd > cardStart)
        val card = activity.substring(cardStart, cardEnd)

        assertTrue(card.contains("isSingleLine = false"))
        assertTrue(card.contains("setLineSpacing"))
        assertTrue(card.contains("search_identity_price_unavailable"))
        assertTrue(card.contains("search_identity_store_context_unavailable"))
        assertTrue(card.contains("search_identity_unit_rate_unavailable"))
        assertFalse(card.contains("ellipsize"))
    }

    @Test
    fun `Search route hides generic shell chrome without changing route state`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        listOf(
            "shellIntro.visibility = if (searchVisible) View.GONE else View.VISIBLE",
            "screenFootnote.visibility = if (searchVisible) View.GONE else View.VISIBLE",
            "sourcesLicencesButton.visibility = if (searchVisible) View.GONE else View.VISIBLE",
            "dataStatusButton.visibility = if (searchVisible) View.GONE else View.VISIBLE"
        ).forEach { required -> assertTrue(activity.contains(required)) }
    }

    @Test
    fun `transient live results are not restored across activity recreation`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val saveStart = activity.indexOf("override fun onSaveInstanceState(outState: Bundle)")
        val destroyStart = activity.indexOf("override fun onDestroy()", saveStart)
        assertTrue(saveStart >= 0)
        assertTrue(destroyStart > saveStart)
        val savedState = activity.substring(saveStart, destroyStart)

        assertTrue(savedState.contains("STATE_SEARCH_QUERY"))
        assertFalse(savedState.contains("argentinaSearchSurfaceState"))
        assertTrue(activity.contains("ArgentinaLiveSearchSurfaceHost.createProduction("))
        assertTrue(activity.contains("argentinaSearchHost.close()"))
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

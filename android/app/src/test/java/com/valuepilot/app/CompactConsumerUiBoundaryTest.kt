package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression boundaries for the Wave 2 compact interaction architecture. */
class CompactConsumerUiBoundaryTest {

    @Test
    fun sharedFoundationDefinesSemanticCompactTokensAndAReusableSheet() {
        val tokens = source("ValuePilotDesignTokens.kt")
        val components = source("ValuePilotCompactComponents.kt")

        listOf(
            "SCREEN_HORIZONTAL_DP",
            "NORMAL_COMPONENT_RADIUS_DP",
            "ICON_DP",
            "MICRO_DURATION_MS",
            "ROUTE_DURATION_MS",
            "DIVIDER"
        ).forEach { assertTrue("Missing semantic token $it", tokens.contains(it)) }
        listOf(
            "class ValuePilotCompactRow",
            "fun bind(",
            "fun overflowButton(",
            "fun statusChip(",
            "fun showSheet(",
            "data class SheetAction",
            "MINIMUM_DP",
            "ValuePilotMotion.reveal"
        ).forEach { assertTrue("Missing compact component boundary $it", components.contains(it)) }
        listOf(
            "PracticalShoppingPlanner",
            "Money.parse",
            "Argentina",
            "ranking",
            "storeAssignment"
        ).forEach { assertFalse("Compact components must stay presentation-only: $it", components.contains(it)) }
    }

    @Test
    fun searchIsSearchFirstAndMovesIdentityTruthIntoOneDisclosure() {
        val activity = source("MainActivity.kt")
        val layout = resource("layout/activity_shell.xml")
        listOf(
            "searchAboutResultsButton",
            "openSearchAboutResults()",
            "ValuePilotCompactUi.showSheet(",
            "searchIdentityBoundaryCard.visibility = View.GONE",
            "searchButton.visibility =",
            "shellBrand.visibility ="
        ).forEach { assertTrue("Missing Search compact disclosure $it", activity.contains(it)) }
        assertTrue(layout.contains("android:id=\"@+id/searchAboutResultsButton\""))
        assertTrue(layout.contains("android:id=\"@+id/searchButton\""))
        assertTrue(layout.contains("android:visibility=\"gone\""))
    }

    @Test
    fun productObjectsUseBodyActionAndCompactOverflowInsteadOfPermanentActionStacks() {
        val activity = source("MainActivity.kt")
        val contextMenu = source("ValuePilotProductContextMenu.kt")
        val start = activity.indexOf("private fun createSearchResultView(")
        val end = activity.indexOf("private fun openSearchProductHistory(", start)
        assertTrue(start >= 0 && end > start)
        val result = activity.substring(start, end)
        listOf(
            "ValuePilotCompactUi.overflowButton",
            "ValuePilotCompactUi.showSheet(",
            "content.setOnClickListener",
            "content.addView(addHomeButton)"
        ).forEach { assertTrue("Missing compact product interaction $it", result.contains(it)) }
        assertTrue(contextMenu.contains("text = \"⋯\""))
        assertTrue(contextMenu.contains("MINIMUM_DP"))
    }

    @Test
    fun homeAndYouFirstViewportsSuppressDashboardAndSummaryDuplication() {
        val home = source("PracticalShoppingHomeSurfaceView.kt")
        val you = source("PracticalShoppingYouOverviewView.kt")
        listOf(
            "homeDemoBadge",
            "homeFlow.visibility = GONE",
            "commandCenterCard.visibility = GONE",
            "buyAgainCard.visibility =",
            "ValuePilotCompactUi.statusChip"
        ).forEach { assertTrue("Missing compact Home boundary $it", home.contains(it)) }
        listOf(
            "Private on this device",
            "segment(\"Bought\"",
            "segment(\"Saved\"",
            "segment(\"Watching\"",
            "private enum class Collection"
        ).forEach { assertTrue("Missing segmented You boundary $it", you.contains(it)) }
        assertFalse("You must not reintroduce three giant overview cards", you.contains("overviewCard("))
        assertFalse("You must not duplicate the old card shell", you.contains("ValuePilotProductCard(context)"))
    }

    @Test
    fun basketRowsExposeCompactCollectionAndContextualProductDetails() {
        val basket = source("PracticalShoppingBasketSurfaceView.kt")
        listOf(
            "var onOpenProduct",
            "identityBody.setOnClickListener",
            "text = \"⋯\"",
            "collected -> \"✓\"",
            "else -> \"○\"",
            "sampleCardView.contentDescription",
            "ValuePilotDesignTokens.Shape.NORMAL_COMPONENT_RADIUS_DP"
        ).forEach { assertTrue("Missing compact Basket boundary $it", basket.contains(it)) }
        assertTrue(basket.contains("PracticalShoppingBasketProgressSession.toggle"))
        assertTrue(basket.contains("Record purchase"))
        assertFalse("Basket rows should not use the old full-width collection wording", basket.contains("text = \"✓  Collected\""))
    }

    @Test
    fun privacyAndStateRulesRemainOutsidePresentationComponents() {
        val components = source("ValuePilotCompactComponents.kt")
        val activity = source("MainActivity.kt")
        assertFalse(components.contains("System.currentTimeMillis"))
        assertFalse(components.contains("SharedPreferences"))
        assertTrue(activity.contains("basketExperience.onOpenProduct"))
        assertTrue(activity.contains("searchAboutResultsButton.setOnClickListener"))
    }

    @Test
    fun contextualSurfacesUseCompactHierarchyAndProgressiveDisclosure() {
        val settings = source("PracticalShoppingSettingsView.kt")
        val trip = source("TripPlannerSurfaceView.kt")
        val map = source("ProductStoreMapSurfaceView.kt")
        val compare = source("CompareHereSurfaceView.kt")
        val goodPrice = source("GoodPriceCheckSurfaceView.kt")

        assertTrue(settings.contains("ValuePilotCompactRow(context)"))
        assertTrue(settings.contains("ValuePilotCompactUi.divider(context)"))
        assertFalse(settings.contains("ValuePilotProductCard(context)"))

        assertTrue(trip.contains("private val metricsRow"))
        assertTrue(trip.contains("text = \"More trip actions\""))
        assertTrue(trip.contains("ValuePilotDesignTokens.Shape.NORMAL_COMPONENT_RADIUS_DP"))

        assertTrue(map.contains("dp(320)"))
        assertTrue(map.contains("orientation = LinearLayout.HORIZONTAL"))
        assertTrue(map.contains("Availability unknown"))

        assertTrue(compare.contains("compact = true"))
        assertTrue(compare.contains("NORMAL_COMPONENT_RADIUS_DP"))
        assertTrue(goodPrice.contains("compact = true"))
        assertTrue(goodPrice.contains("NORMAL_COMPONENT_RADIUS_DP"))
    }

    @Test
    fun compact_controls_keep_large_text_and_recreation_boundaries() {
        val components = source("ValuePilotCompactComponents.kt")
        val activity = source("MainActivity.kt")

        assertTrue(components.contains("MINIMUM_DP"))
        assertTrue(components.contains("maxLines = 2"))
        assertTrue(components.contains("ValuePilotDesignTokens.Typography.LABEL_SP"))
        assertTrue(activity.contains("AppShellStateRestoration.restore"))
        assertTrue(activity.contains("STATE_PERSONAL_SCROLL_Y"))
    }

    private fun source(name: String): String =
        File("src/main/java/com/valuepilot/app/$name").readText()

    private fun resource(path: String): String = File("src/main/res/$path").readText()
}

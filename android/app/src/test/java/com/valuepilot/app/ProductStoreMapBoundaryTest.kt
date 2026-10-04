package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductStoreMapBoundaryTest {
    @Test
    fun searchMapActionUsesTheExactSelectedIdentityAndKeepsSearchMounted() {
        val main = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val cardStart = main.indexOf("private fun createSearchResultView(")
        val cardEnd = main.indexOf("private fun openGoogleProductPhotos(", cardStart)
        assertTrue(cardStart >= 0)
        assertTrue(cardEnd > cardStart)
        val card = main.substring(cardStart, cardEnd)
        assertTrue(card.contains("onOpenMap"))
        assertTrue(card.contains("ValuePilotProductContextActionKind.MAP"))
        assertTrue(card.contains("ValuePilotProductContextActionKind.MAP -> onOpenMap(match)"))
        assertTrue(main.contains("productEvidenceKey = match.identityKey"))
        assertTrue(main.contains("ProductStoreMapSurfaceView"))
        assertTrue(main.contains("storeKey = pin.storeKey"))
        assertTrue(main.contains("dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.BASKET))"))
        assertTrue(main.contains("dispatch(AppShellIntent.OpenTripPlanner)"))
        assertTrue(main.contains("STATE_PRODUCT_MAP_SELECTED_KEY"))
        assertTrue(main.contains("searchScrollY = shellScrollView.scrollY"))
        assertTrue(main.contains("PRODUCT_MAP_FOREGROUND_LOCATION_REQUEST"))
        assertFalse(main.contains("TileProvider"))
        assertFalse(main.contains("tile.openstreetmap.org"))
    }

    @Test
    fun mapDisclosureDoesNotTurnPriceIntoInventoryOrRouting() {
        val surface = source("src/main/java/com/valuepilot/app/ProductStoreMapSurfaceView.kt").readText()
        assertTrue(surface.contains("availability unknown"))
        assertTrue(surface.contains("straight-line"))
        assertTrue(surface.contains("no road route"))
        assertTrue(surface.contains("Published release price"))
        assertTrue(surface.contains("Use one-shot location"))
        assertTrue(surface.contains("no background tracking"))
        assertFalse(surface.contains("cheapest"))
        assertFalse(surface.contains("in stock"))
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

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
        assertTrue(card.contains("ValuePilotProductContextActionKind.HISTORY"))
        assertTrue(card.contains("historyAvailable"))
        assertTrue(card.contains("homeSaved"))
        assertTrue(card.contains("onToggleHome(match)"))
        assertTrue(main.contains("purchase.productIdentityKey == match.identityKey"))
        assertTrue(main.contains("openSearchProductHistory"))
        assertTrue(main.contains("toggleSearchIdentityInHome"))
        assertTrue(main.contains("productEvidenceKey = match.identityKey"))
        assertTrue(main.contains("ProductStoreMapSurfaceView"))
        assertTrue(main.contains("storeKey = pin.storeKey"))
        assertTrue(main.contains("dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.BASKET))"))
        assertTrue(main.contains("dispatch(AppShellIntent.OpenTripPlanner)"))
        assertTrue(main.contains("dispatch(AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH))"))
        assertTrue(main.contains("reopenProductMap"))
        assertTrue(main.contains("openProductMapDirections"))
        assertTrue(main.contains("geo:\$latitude,\$longitude"))
        assertTrue(main.contains("STATE_PRODUCT_MAP_SELECTED_KEY"))
        assertTrue(main.contains("searchScrollY = shellScrollView.scrollY"))
        assertTrue(main.contains("PRODUCT_MAP_FOREGROUND_LOCATION_REQUEST"))
        assertFalse(main.contains("TileProvider"))
        assertFalse(main.contains("tile.openstreetmap.org"))
    }

    @Test
    fun mapDisclosureDoesNotTurnPriceIntoInventoryOrRouting() {
        val surface = source("src/main/java/com/valuepilot/app/ProductStoreMapSurfaceView.kt").readText()
        val presentation = source("src/main/java/com/valuepilot/app/ProductStoreMapPresentation.kt").readText()
        assertTrue(surface.contains("availability unknown"))
        assertTrue(surface.contains("straight-line"))
        assertTrue(surface.contains("Trip cost"))
        assertTrue(surface.contains("Directions"))
        assertTrue(surface.contains("Use my location"))
        assertTrue(surface.contains("no road route"))
        assertTrue(surface.contains("no background tracking"))
        assertTrue(surface.contains("ValuePilotProductCard(context)"))
        assertTrue(surface.contains("selectedStoreKey"))
        assertTrue(surface.contains("restoreSelectedStore"))
        assertTrue(surface.contains("ValuePilotMotion.reveal(mapCard"))
        assertTrue(presentation.contains("No nearby store data"))
        assertTrue(presentation.contains("Reported price"))
        assertTrue(presentation.contains("Older release · not current"))
        assertTrue(surface.contains("straight-line"))
        assertFalse(surface.contains("cheapest"))
        assertFalse(surface.contains("in stock"))
        assertFalse(presentation.contains("in stock"))
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

package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ProductStoreMapCoordinate
import com.valuepilot.core.ProductStoreMapFreshness
import com.valuepilot.core.ProductStoreMapQuery
import com.valuepilot.core.ProductStoreMapResult
import com.valuepilot.core.ProductStoreMapResultStatus
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductStoreMapPresentationTest {

    @Test
    fun `ready map is concise and keeps exact store context`() {
        val pin = pin(
            storeKey = "store-a",
            storeName = "Supermercado Yerba",
            distanceMetres = 1_420L,
            freshness = ProductStoreMapFreshness.FRESH,
            price = Money.parse("12.50", "ARS")
        )
        val state = ProductStoreMapProjector.project(
            productName = "Playadito Yerba Mate",
            packageQuantityLabel = "1 kg",
            result = result(ProductStoreMapResultStatus.READY, listOf(pin)),
            origin = ProductStoreMapOrigin.CABA_FALLBACK
        )

        assertEquals(ProductStoreMapSurfaceMode.READY, state.mode)
        assertEquals("1 store reported", state.headline)
        assertEquals("1 kg", state.packageQuantityLabel)
        assertEquals("Location not used · CABA search area", state.locationLabel)
        assertEquals(
            "≈ 1.4 km from CABA search area",
            ProductStoreMapProjector.distanceLabel(pin, ProductStoreMapOrigin.CABA_FALLBACK)
        )
        assertEquals("Reported price · ARS 12.50", ProductStoreMapProjector.priceLabel(pin))
        assertTrue(state.support.contains("Availability is unknown"))
    }

    @Test
    fun `one shot location is clearly distinguished without claiming live availability`() {
        val pin = pin(
            storeKey = "store-b",
            storeName = "Long retailer name that remains readable",
            distanceMetres = 350L,
            freshness = ProductStoreMapFreshness.STALE,
            price = null
        )
        val state = ProductStoreMapProjector.project(
            productName = "Yerba Mate",
            packageQuantityLabel = null,
            result = result(ProductStoreMapResultStatus.READY, listOf(pin)),
            origin = ProductStoreMapOrigin.ONE_SHOT_LOCATION
        )

        assertEquals("Using one-shot location · approximate distance", state.locationLabel)
        assertEquals(
            "≈ 350 m away",
            ProductStoreMapProjector.distanceLabel(pin, ProductStoreMapOrigin.ONE_SHOT_LOCATION)
        )
        assertEquals("Older release · not current", ProductStoreMapProjector.freshnessLabel(pin.freshness))
        assertEquals(null, ProductStoreMapProjector.priceLabel(pin))
        assertFalse(state.support.contains("in stock"))
    }

    @Test
    fun `missing relationships become a simple empty state`() {
        val state = ProductStoreMapProjector.project(
            productName = "Yerba Mate",
            packageQuantityLabel = "1 kg",
            result = result(ProductStoreMapResultStatus.NO_EXACT_RELATIONSHIP, emptyList()),
            origin = ProductStoreMapOrigin.CABA_FALLBACK
        )

        assertEquals(ProductStoreMapSurfaceMode.EMPTY, state.mode)
        assertEquals("No nearby store data", state.headline)
        assertEquals("Try another product.", state.support)
        assertTrue(state.pins.isEmpty())
    }

    @Test
    fun `conflicting price remains a non-live diagnostic`() {
        val pin = pin(
            storeKey = "store-c",
            storeName = "Store",
            distanceMetres = null,
            freshness = ProductStoreMapFreshness.UNKNOWN,
            price = null,
            priceConflict = true
        )

        assertEquals("Conflicting reported prices", ProductStoreMapProjector.priceLabel(pin))
        assertEquals(null, ProductStoreMapProjector.distanceLabel(pin, ProductStoreMapOrigin.ONE_SHOT_LOCATION))
    }

    private fun result(
        status: ProductStoreMapResultStatus,
        pins: List<com.valuepilot.core.ProductStoreMapPin>
    ) = ProductStoreMapResult(
        query = ProductStoreMapQuery(productEvidenceKey = "product-key"),
        status = status,
        pins = pins,
        relationshipCount = pins.size,
        excludedCoordinateCount = 0,
        duplicateStoreCount = 0
    )

    private fun pin(
        storeKey: String,
        storeName: String,
        distanceMetres: Long?,
        freshness: ProductStoreMapFreshness,
        price: Money?,
        priceConflict: Boolean = false
    ) = com.valuepilot.core.ProductStoreMapPin(
        relationshipKey = "relationship-$storeKey",
        productEvidenceKey = "product-key",
        storeKey = ShoppingStoreKey(storeKey),
        storeName = storeName,
        addressLabel = "Buenos Aires · Calle 1",
        coordinate = ProductStoreMapCoordinate(-346055060, -584425620),
        releaseDate = "2026-09-06",
        freshness = freshness,
        price = price,
        priceConflict = priceConflict,
        approximateDistanceMetres = distanceMetres,
        sourceLabel = "Precios Claros"
    )
}

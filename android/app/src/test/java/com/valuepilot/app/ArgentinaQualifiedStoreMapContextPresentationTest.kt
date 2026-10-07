package com.valuepilot.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaQualifiedStoreMapContextPresentationTest {
    @Test
    fun `context is deterministic and keeps exact evidence markers`() {
        val store = ArgentinaPrivateBasketStoreProjection(
            storeKey = "store-1",
            storeName = "Caballito",
            latitude = "-34.611087",
            longitude = "-58.460584",
            addressLabel = "street: Av. Donato Alvarez 1351"
        )
        val markers = listOf(
            marker("product-b", "Baldo", "ARS 7850.00"),
            marker("product-a", "Secadero", "ARS 2669.00")
        )

        val context = ArgentinaQualifiedStoreMapContextPresentation.fromStore(store, markers)
        val message = ArgentinaQualifiedStoreMapContextPresentation.message(context)

        assertEquals(listOf("product-a", "product-b"), context.markers.map { it.productEvidenceKey })
        assertTrue(message.contains("storeKey: store-1"))
        assertTrue(message.contains("productEvidenceKey product-a"))
        assertTrue(message.contains("ARS 2669.00"))
        assertTrue(message.contains("does not infer a driving route"))
        assertTrue(message.contains("availability here"))
        assertEquals(message, ArgentinaQualifiedStoreMapContextPresentation.message(context))
    }

    @Test
    fun `invalid coordinates stay unknown and are never rendered as a marker`() {
        val offer = ArgentinaBackendContract.SearchOffer(
            offerKey = "offer-1",
            productEvidenceKey = "product-a",
            storeKey = "store-1",
            storeName = "Unknown coordinates",
            storeAddress = emptyMap(),
            storeLatitude = "not-a-coordinate",
            storeLongitude = "-58.4",
            distanceKm = "1.0",
            listPrice = money("12.00"),
            referencePrice = null,
            quantity = null,
            quantityStatus = "UNKNOWN",
            unitPrice = null,
            unitPriceBasis = null,
            releaseDate = "2026-10-01",
            freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
            availability = ArgentinaBackendContract.Availability.UNKNOWN,
            provenance = ArgentinaBackendContract.Provenance(
                provider = "ARGENTINA_SEPA_PRECIOS_CLAROS",
                releaseDate = "2026-10-01",
                freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                source = "Precios Claros"
            )
        )

        val context = ArgentinaQualifiedStoreMapContextPresentation.fromOffer(offer)

        assertEquals(null, context.latitude)
        assertEquals(null, context.longitude)
        assertTrue(context.markers.isEmpty())
        assertTrue(ArgentinaQualifiedStoreMapContextPresentation.message(context)
            .contains("Coordinates: unknown"))
    }

    private fun marker(key: String, name: String, price: String) =
        ArgentinaPrivateBasketMapMarkerProjection(
            storeKey = "store-1",
            storeName = "Caballito",
            latitude = "-34.611087",
            longitude = "-58.460584",
            productEvidenceKey = key,
            productName = name,
            priceText = price
        )

    private fun money(amount: String) =
        ArgentinaBackendContract.Money(amount, "ARS", BigDecimal(amount))
}

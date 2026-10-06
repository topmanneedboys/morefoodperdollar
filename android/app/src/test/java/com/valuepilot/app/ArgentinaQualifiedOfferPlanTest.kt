package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArgentinaQualifiedOfferPlanTest {
    @Test
    fun requestPreservesExactIdentityAndStructuredQuantity() {
        val match = match(quantity = quantity("500", "g"))
        val request = ArgentinaQualifiedOfferPlan.request(
            match,
            ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
        )

        val item = requireNotNull(request).items.single()
        assertEquals("Yerba Mate", item.query)
        assertEquals("500", item.amount)
        assertEquals("g", item.unit)
        assertEquals("product-a", item.productEvidenceKey)
    }

    @Test
    fun unknownQuantityDoesNotCreateAPlannerRequest() {
        assertNull(
            ArgentinaQualifiedOfferPlan.request(
                match(quantity = null),
                ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
            )
        )
    }

    private fun match(quantity: ArgentinaBackendContract.Quantity?) =
        PracticalShoppingSearchIdentityPresentation.Match(
            displayName = "Yerba Mate",
            brand = "Mate",
            matchLabel = "exact barcode",
            packageQuantityLabel = quantity?.let { "${it.amount} ${it.unit}" },
            sourceLabel = "Argentina SEPA / Precios Claros",
            identityKey = "product-a",
            qualifiedOffers = listOf(
                ArgentinaBackendContract.SearchOffer(
                    offerKey = "offer-a",
                    productEvidenceKey = "product-a",
                    storeKey = "store-a",
                    storeName = "Store A",
                    storeAddress = emptyMap(),
                    storeLatitude = "-34.6",
                    storeLongitude = "-58.4",
                    distanceKm = "1.2",
                    listPrice = ArgentinaBackendContract.Money("12.50", "ARS", java.math.BigDecimal("12.50")),
                    referencePrice = null,
                    quantity = quantity,
                    quantityStatus = if (quantity == null) "UNKNOWN" else "KNOWN",
                    unitPrice = null,
                    unitPriceBasis = null,
                    releaseDate = "2026-09-06",
                    freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                    availability = ArgentinaBackendContract.Availability.UNKNOWN,
                    provenance = ArgentinaBackendContract.Provenance(
                        provider = "ARGENTINA_SEPA_PRECIOS_CLAROS",
                        releaseDate = "2026-09-06",
                        freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                        source = "Precios Claros"
                    )
                )
            )
        )

    private fun quantity(amount: String, unit: String) = ArgentinaBackendContract.Quantity(
        dimension = "MASS",
        amount = amount,
        unit = unit,
        baseAmount = amount,
        baseUnit = unit
    )
}

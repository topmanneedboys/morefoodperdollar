package com.valuepilot.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaQualifiedOfferPresentationTest {
    @Test
    fun summaryShowsExactListedPriceStoreAndUnitRateWithoutStockClaim() {
        val offer = offer(unitPrice = money("1.25"), quantity = quantity("500", "g"))

        assertEquals("ARS 12.50 at Store A · ARS 1.25/100 g · stock unknown", ArgentinaQualifiedOfferPresentation.summary(offer))
        assertTrue(ArgentinaQualifiedOfferPresentation.details(offer).contains("Availability: UNKNOWN"))
        assertTrue(ArgentinaQualifiedOfferPresentation.details(offer).contains("Source: ARGENTINA_SEPA_PRECIOS_CLAROS"))
    }

    @Test
    fun unknownQuantityDoesNotInventPackageOrUnitPrice() {
        val offer = offer(unitPrice = null, quantity = null)

        assertEquals("ARS 12.50 at Store A · stock unknown", ArgentinaQualifiedOfferPresentation.summary(offer))
        assertTrue(ArgentinaQualifiedOfferPresentation.details(offer).contains("Package quantity: unknown"))
    }

    private fun offer(
        unitPrice: ArgentinaBackendContract.Money?,
        quantity: ArgentinaBackendContract.Quantity?
    ) = ArgentinaBackendContract.SearchOffer(
        offerKey = "offer-a",
        productEvidenceKey = "product-a",
        storeKey = "store-a",
        storeName = "Store A",
        storeAddress = mapOf("city" to "CABA"),
        storeLatitude = "-34.6",
        storeLongitude = "-58.4",
        distanceKm = "1.2",
        listPrice = money("12.50"),
        referencePrice = null,
        quantity = quantity,
        quantityStatus = if (quantity == null) "UNKNOWN" else "KNOWN",
        unitPrice = unitPrice,
        unitPriceBasis = unitPrice?.let { "100 g" },
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

    private fun money(amount: String) = ArgentinaBackendContract.Money(amount, "ARS", BigDecimal(amount))

    private fun quantity(amount: String, unit: String) = ArgentinaBackendContract.Quantity(
        dimension = "MASS",
        amount = amount,
        unit = unit,
        baseAmount = amount,
        baseUnit = unit,
        allowZero = false
    )
}

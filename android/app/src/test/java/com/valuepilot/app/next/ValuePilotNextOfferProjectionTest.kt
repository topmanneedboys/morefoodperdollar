package com.valuepilot.app.next

import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextOfferProjectionTest {

    @Test
    fun `unit price sorting and store filters use only fresh comparable offers`() {
        val first = match(
            "product:first",
            listOf(
                offer("product:first", "coto-1", "1400", storeName = "Coto"),
                offer("product:first", "dia-1", "1200", storeName = "Día")
            )
        )
        val second = match(
            "product:second",
            listOf(
                offer("product:second", "coto-2", "1000", storeName = "Coto"),
                offer("product:second", "dia-2", "1100", storeName = "Día")
            )
        )
        val options = searchResultFilterOptions(listOf(first, second))
        val basis = requireNotNull(options.comparableUnitPriceBasis)

        assertEquals("ARS", basis.currency)
        assertEquals("l", basis.unitBasis)
        assertEquals(listOf("Coto", "Día"), options.stores.map { it.storeName })
        assertEquals(
            listOf("product:second", "product:first"),
            filterAndSortSearchMatches(listOf(first, second), null, true, basis).map { it.identityKey }
        )
        assertEquals(
            "dia-1",
            requireNotNull(preferredSearchOffer(first, null, true, basis)).storeKey
        )
        assertEquals(
            listOf("product:first", "product:second"),
            filterAndSortSearchMatches(listOf(first, second), "Coto", false, basis).map { it.identityKey }
        )
    }

    @Test
    fun `sort control is omitted when current offers do not share one comparable basis`() {
        val first = match("product:first", listOf(offer("product:first", "a", "1000", storeName = "Tienda A", basis = "L")))
        val second = match("product:second", listOf(offer("product:second", "b", "1000", storeName = "Tienda B", basis = "kg")))

        assertNull(searchResultFilterOptions(listOf(first, second)).comparableUnitPriceBasis)
    }

    @Test
    fun `product range uses recent same-basis prices and excludes stale evidence`() {
        val range = requireNotNull(
            currentOfferPriceRange(
                listOf(
                    offer("product:first", "a", "1200", storeName = "Tienda A"),
                    offer("product:first", "b", "1500"),
                    offer("product:first", "c", "900", freshness = ArgentinaBackendContract.Freshness.STALE)
                )
            )
        )

        assertEquals(BigDecimal("1200"), range.minimum)
        assertEquals(BigDecimal("1350"), range.median)
        assertEquals(BigDecimal("1500"), range.maximum)
        assertEquals(2, range.offerCount)
        assertTrue(range.medianPosition in 0.49f..0.51f)
    }

    @Test
    fun `product range stays hidden when currencies or unit bases differ`() {
        val mixedBasis = listOf(
            offer("product:first", "a", "1200", storeName = "Tienda A", basis = "L"),
            offer("product:first", "b", "1500", basis = "kg")
        )
        val mixedCurrency = listOf(
            offer("product:first", "a", "1200", storeName = "Tienda A", currency = "ARS"),
            offer("product:first", "b", "1500", currency = "USD")
        )

        assertNull(currentOfferPriceRange(mixedBasis))
        assertNull(currentOfferPriceRange(mixedCurrency))
    }

    private fun match(
        identity: String,
        offers: List<ArgentinaBackendContract.SearchOffer>
    ) = PracticalShoppingSearchIdentityPresentation.Match(
        displayName = identity,
        brand = null,
        matchLabel = "exact name",
        packageQuantityLabel = "1 L",
        sourceLabel = "test source",
        identityKey = identity,
        qualifiedOffers = offers
    )

    private fun offer(
        productKey: String,
        storeKey: String,
        amount: String,
        storeName: String = storeKey,
        basis: String = "L",
        currency: String = "ARS",
        freshness: ArgentinaBackendContract.Freshness = ArgentinaBackendContract.Freshness.FRESH
    ) = ArgentinaBackendContract.SearchOffer(
        offerKey = "$productKey:$storeKey:$amount",
        productEvidenceKey = productKey,
        storeKey = storeKey,
        storeName = storeName,
        storeAddress = emptyMap(),
        storeLatitude = "",
        storeLongitude = "",
        distanceKm = "",
        listPrice = money("2000", currency),
        referencePrice = null,
        quantity = null,
        quantityStatus = null,
        unitPrice = money(amount, currency),
        unitPriceBasis = basis,
        releaseDate = "2026-10-09",
        freshnessStatus = freshness,
        availability = ArgentinaBackendContract.Availability.UNKNOWN,
        provenance = ArgentinaBackendContract.Provenance(
            provider = "test provider",
            releaseDate = "2026-10-09",
            freshnessStatus = freshness,
            source = null
        )
    )

    private fun money(amount: String, currency: String) = ArgentinaBackendContract.Money(
        amount = amount,
        currency = currency,
        decimal = amount.toBigDecimal()
    )
}

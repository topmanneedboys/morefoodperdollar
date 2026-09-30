package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductStoreMapTest {
    private val origin = ProductStoreMapCoordinate(-346055060, -584425620)

    @Test
    fun exactProductMatchesOnlyItsExactIdentityAndGtin() {
        val index = ProductStoreMapIndex.build(
            listOf(
                evidence("offer-a", "yerba:1kg", "4006381333931", "store-a", -346000000, -584000000),
                evidence("offer-wrong-package", "yerba:500g", "4006381333931", "store-b", -346100000, -584100000),
                evidence("offer-wrong-gtin", "yerba:1kg", "4006381333932", "store-c", -346200000, -584200000)
            )
        )

        val exact = index.query(ProductStoreMapQuery("yerba:1kg", "4006381333931", origin))
        assertEquals(listOf("store-a"), exact.pins.map { it.storeKey.value })
        assertEquals(ProductStoreMapResultStatus.READY, exact.status)
        assertTrue(exact.pins.single().approximateDistanceMetres!! >= 0L)
        assertEquals(
            ProductStoreMapResultStatus.NO_EXACT_RELATIONSHIP,
            index.query(ProductStoreMapQuery("yerba:unknown", null, origin)).status
        )
    }

    @Test
    fun invalidCoordinatesAreExcludedAndNoLocationStillSortsDeterministically() {
        val index = ProductStoreMapIndex.build(
            listOf(
                evidence("offer-b", "milk", null, "store-b", 100000000, 100000000),
                evidence("offer-a", "milk", null, "store-a", null, null)
            )
        )
        val result = index.query(ProductStoreMapQuery("milk"))
        assertEquals(ProductStoreMapResultStatus.READY, result.status)
        assertEquals(listOf("store-b"), result.pins.map { it.storeKey.value })
        assertEquals(1, result.excludedCoordinateCount)
        assertNull(result.pins.single().approximateDistanceMetres)
    }

    @Test
    fun duplicateStoresAreCollapsedAndConflictingPricesRemainUnknown() {
        val index = ProductStoreMapIndex.build(
            listOf(
                evidence("offer-a", "coffee", null, "store-a", -346000000, -584000000, "100.00"),
                evidence("offer-b", "coffee", null, "store-a", -346000000, -584000000, "101.00"),
                evidence("offer-c", "coffee", null, "store-b", -346100000, -584100000, "99.00")
            )
        )
        val result = index.query(ProductStoreMapQuery("coffee", origin = origin))
        assertEquals(listOf("store-b", "store-a"), result.pins.map { it.storeKey.value })
        assertEquals(1, result.duplicateStoreCount)
        val conflicting = result.pins.single { it.storeKey.value == "store-a" }
        assertNull(conflicting.price)
        assertTrue(conflicting.priceConflict)
    }

    @Test
    fun staleEvidenceIsRetainedAndMarkedNotCurrent() {
        val stale = evidence("offer-stale", "tea", null, "store-a", -346000000, -584000000)
            .copy(freshness = ProductStoreMapFreshness.STALE, releaseDate = "2026-09-06")
        val result = ProductStoreMapIndex.build(listOf(stale)).query(ProductStoreMapQuery("tea"))
        assertEquals(ProductStoreMapFreshness.STALE, result.pins.single().freshness)
        assertEquals("2026-09-06", result.pins.single().releaseDate)
        assertTrue(result.pins.single().availabilityUnknown)
    }

    @Test
    fun malformedInputIsRejectedBeforeIndexing() {
        assertFailsWith<IllegalArgumentException> {
            ProductStoreMapIndex.build(
                (0..ProductStoreMapIndex.MAX_RECORDS).map { index ->
                    evidence("offer-$index", "item", null, "store-$index", 0, 0)
                }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProductStoreMapIndex.build(
                listOf(
                    evidence("same", "item", null, "store-a", 0, 0),
                    evidence("same", "item", null, "store-b", 0, 0)
                )
            )
        }
    }

    private fun evidence(
        relationship: String,
        product: String,
        gtin: String?,
        store: String,
        latitude: Int?,
        longitude: Int?,
        price: String? = null
    ): ProductStoreMapEvidence =
        ProductStoreMapEvidence(
            relationshipKey = relationship,
            productEvidenceKey = product,
            gtin = gtin,
            storeKey = ShoppingStoreKey(store),
            storeName = store,
            addressLabel = "$store address",
            coordinate = if (latitude == null || longitude == null) null else ProductStoreMapCoordinate(latitude, longitude),
            releaseDate = null,
            freshness = ProductStoreMapFreshness.FRESH,
            price = price?.let { Money.parse(it, "ARS") },
            sourceLabel = "fixture"
        )
}

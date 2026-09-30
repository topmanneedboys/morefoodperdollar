package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.BaseUnit
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.PriceObservation
import com.valuepilot.core.PriceWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceWatchStoreTest {

    @Test
    fun `same watch key upserts instead of duplicating across views`() {
        val storage = FakeStorage()
        val store = AndroidPriceWatchStore(storage)
        assertTrue(store.upsertWatch(PriceWatch("gtin:1", "Coffee")).accepted)
        assertTrue(store.upsertWatch(PriceWatch("gtin:1", "Coffee", targetPrice = Money.parse("5.00", "CAD"))).accepted)

        val state = requireNotNull(store.load().state)
        assertEquals(1, state.watches.size)
        assertEquals(500L, state.watches.single().targetPrice?.minorUnits)
    }

    @Test
    fun `observations round trip with exact package store and provenance`() {
        val storage = FakeStorage()
        val store = AndroidPriceWatchStore(storage)
        assertTrue(store.upsertWatch(PriceWatch("provider:item-1", "Coffee", packageQuantityLabel = "340 g")).accepted)
        val observation =
            PriceObservation.personal(
                observationId = "observation-1",
                productIdentityKey = "provider:item-1",
                packageQuantity = NormalizedQuantity(340_000_000L, BaseUnit.GRAM),
                price = Money.parse("6.49", "CAD"),
                observedDateIso = "2026-09-29",
                store = ConfirmedPurchaseStore(
                    displayName = "Local market",
                    storeKey = ShoppingStoreKey("directory:osm:node:123")
                )
            )
        assertTrue(store.appendObservation(observation).accepted)

        val restarted = AndroidPriceWatchStore(storage)
        val loaded = requireNotNull(restarted.load().state)
        assertEquals(1, loaded.watches.size)
        assertEquals(1, loaded.observations.size)
        assertEquals("Local market", loaded.observations.single().store?.displayName)
        assertEquals(
            ShoppingStoreKey("directory:osm:node:123"),
            loaded.observations.single().store?.storeKey
        )
        assertEquals(340_000_000L, loaded.observations.single().packageQuantity?.amountMicros)
        assertEquals("CAD", loaded.observations.single().price.currencyCode)
    }

    @Test
    fun `unwatch preserves private history and remove supports correction`() {
        val storage = FakeStorage()
        val store = AndroidPriceWatchStore(storage)
        val watch = PriceWatch("gtin:1", "Coffee")
        val observation = PriceObservation.personal("observation-1", "gtin:1", price = Money.parse("5.00", "CAD"))
        assertTrue(store.upsertWatch(watch).accepted)
        assertTrue(store.appendObservation(observation).accepted)
        assertTrue(store.removeWatch("gtin:1").accepted)
        assertEquals(0, requireNotNull(store.load().state).watches.size)
        assertEquals(1, requireNotNull(store.load().state).observations.size)

        val corrected = observation.copy(price = Money.parse("4.50", "CAD"))
        assertTrue(store.replaceObservation(corrected).accepted)
        assertEquals(450L, requireNotNull(store.load().state).observations.single().price.minorUnits)
        assertTrue(store.removeObservation("observation-1").accepted)
        assertFalse(store.removeObservation("observation-1").accepted)
    }

    @Test
    fun `malformed and failed writes preserve last known good state`() {
        val storage = FakeStorage()
        val store = AndroidPriceWatchStore(storage)
        assertTrue(store.upsertWatch(PriceWatch("gtin:1", "Coffee")).accepted)
        storage.writeAccepted = false
        assertFalse(store.upsertWatch(PriceWatch("gtin:2", "Tea")).accepted)
        assertEquals(listOf("gtin:1"), requireNotNull(store.load().state).watches.map { it.productIdentityKey })

        storage.bytes = "bad".toByteArray()
        assertFalse(store.upsertWatch(PriceWatch("gtin:3", "Milk")).accepted)
        assertEquals(PriceWatchStoreIssue.STORED_DATA_INVALID, store.upsertWatch(PriceWatch("gtin:4", "Milk")).issue)
    }

    private class FakeStorage(
        var writeAccepted: Boolean = true,
        var bytes: ByteArray? = null
    ) : PriceWatchByteStorage {
        override fun read(maxBytes: Int): PriceWatchRawReadResult =
            bytes?.let { PriceWatchRawReadResult(it.copyOf(), true) }
                ?: PriceWatchRawReadResult(null, false)

        override fun replace(bytes: ByteArray): Boolean {
            if (!writeAccepted) return false
            this.bytes = bytes.copyOf()
            return true
        }
    }
}

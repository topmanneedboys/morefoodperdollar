package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.TripDistanceBasis
import com.valuepilot.core.TripOriginKind
import com.valuepilot.core.TripOriginReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerStoreTest {

    @Test
    fun `scenario round trips with exact store identity and restart`() {
        val destination =
            TripStoreSelection(
                storeKey = ShoppingStoreKey("directory:osm:node:123"),
                displayName = "Example Market",
                addressLabel = "1 Main Street, Toronto",
                sourceLabel = "Signed location directory · location only",
                latitudeE7 = 435000000,
                longitudeE7 = -793800000
            )
        val state =
            TripPlannerState(
                destination = destination,
                preferredStoreKey = destination.storeKey,
                origin = TripOriginReference(TripOriginKind.MANUAL, "Home"),
                drivingDistanceMetres = 12_500L,
                distanceBasis = TripDistanceBasis.ROUND_TRIP,
                fuelConsumptionMilliLitresPer100Km = 7_500L,
                fuelPrice = Money.parse("1.80", "CAD"),
                tolls = Money.parse("2.25", "CAD"),
                parking = Money.parse("1.00", "CAD")
            )
        val storage = FakeStorage()
        val store = AndroidTripPlannerStore(storage)

        assertTrue(store.replace(state).accepted)
        val restarted = AndroidTripPlannerStore(storage)
        assertEquals(state, requireNotNull(restarted.load().state))
        assertEquals(25_000L, requireNotNull(restarted.load().state).costInputOrNull()?.effectiveDrivingDistanceMetres)

        assertTrue(restarted.clear().accepted)
        assertEquals(TripPlannerState(), requireNotNull(restarted.load().state))
    }

    @Test
    fun `malformed or failed writes never replace last known good scenario`() {
        val storage = FakeStorage()
        val store = AndroidTripPlannerStore(storage)
        val first = TripPlannerState(origin = TripOriginReference(TripOriginKind.MANUAL, "Home"))
        assertTrue(store.replace(first).accepted)

        storage.writeAccepted = false
        assertFalse(store.replace(TripPlannerState(origin = TripOriginReference(TripOriginKind.MANUAL, "Other"))).accepted)
        assertEquals(first, requireNotNull(store.load().state))

        storage.bytes = "not-a-trip".toByteArray()
        val loaded = store.load()
        assertFalse(loaded.accepted)
        assertEquals(TripPlannerStoreIssue.STORED_DATA_INVALID, loaded.issue)
        assertFalse(store.replace(TripPlannerState()).accepted)
        storage.writeAccepted = true
        assertFalse(store.replace(TripPlannerState()).accepted)
        assertEquals("not-a-trip", String(requireNotNull(storage.bytes), Charsets.UTF_8))
    }

    @Test
    fun `codec is deterministic and rejects dangling optional fields`() {
        val state = TripPlannerState(origin = TripOriginReference(TripOriginKind.MANUAL, "Home"))
        assertEquals(TripPlannerCodec.encode(state)?.toList(), TripPlannerCodec.encode(state)?.toList())

        val parts = requireNotNull(TripPlannerCodec.encode(state)).toString(Charsets.US_ASCII).split('|').toMutableList()
        parts[3] = "486f6d65" // destination display without a destination key
        val decoded = TripPlannerCodec.decode(parts.joinToString("|").toByteArray(Charsets.US_ASCII))
        assertFalse(decoded.accepted)
        assertNotEquals(null, decoded.issue)
    }

    @Test
    fun `private store identity is deterministic and picker remains bounded`() {
        val first = requireNotNull(TripStoreDirectoryAdapter.privateEntry("  Local   Market "))
        val second = requireNotNull(TripStoreDirectoryAdapter.privateEntry("local market"))
        assertEquals(first.storeKey, second.storeKey)
        assertEquals("Local Market", first.displayName)

        val stores = (0 until 40).map { index ->
            TripStoreSelection(ShoppingStoreKey("private:$index"), "Store $index", sourceLabel = "private")
        }
        assertEquals(TripStoreDirectoryAdapter.MAX_PICKER_RESULTS, TripStoreDirectoryAdapter.filter(stores, "").size)
    }

    @Test
    fun `signed directory record maps to exact location identity without offer authority`() {
        val selection =
            TripStoreDirectoryAdapter.fromDirectoryRecord(
                StoreDirectoryRecord(
                    recordId = "osm:node:123",
                    sourceElementId = "123",
                    sourceElementType = "node",
                    providerId = "osm",
                    datasetNamespaceId = "stores",
                    regionId = "GTA",
                    name = "Example Market",
                    brand = null,
                    operator = null,
                    address = StoreDirectoryAddress(street = "Main Street", city = "Toronto", postcode = "M5V 2H1"),
                    storeType = "supermarket",
                    latitudeE7 = 435000000,
                    longitudeE7 = -793800000,
                    sourceSnapshotId = "snapshot-1",
                    licenseId = "odbl",
                    observedAtEpochMillis = 1L,
                    confidence = "high",
                    status = "active",
                    brandWikidataId = null,
                    operatorWikidataId = null
                )
            )
        assertEquals(ShoppingStoreKey("directory:osm:node:123"), selection.storeKey)
        assertEquals("Main Street, Toronto, M5V 2H1", selection.addressLabel)
        assertEquals(435000000, selection.latitudeE7)
        assertEquals(-793800000, selection.longitudeE7)
        assertTrue(selection.sourceLabel.contains("location only"))
    }

    private class FakeStorage(
        var writeAccepted: Boolean = true,
        var bytes: ByteArray? = null
    ) : TripPlannerByteStorage {
        override fun read(maxBytes: Int): TripPlannerRawReadResult =
            bytes?.let { TripPlannerRawReadResult(it.copyOf(), true) }
                ?: TripPlannerRawReadResult(null, false)

        override fun replace(bytes: ByteArray): Boolean {
            if (!writeAccepted) return false
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }
}

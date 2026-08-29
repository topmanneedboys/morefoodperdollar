package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import java.util.Collections
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceTransactionsTest {

    @Test
    fun `confirmed product is admitted and persisted transactionally`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store = store,
                confirmedCandidate = confirmedProduct("eggs", "036000291452")
            )

        assertTrue(result.accepted)
        assertEquals("036000291452", result.state?.productFor(ShoppingItemKey("eggs"))?.sourceIdentity?.gtin)
        assertEquals(result.state, store.load().state)
        assertEquals(1, storage.replaceCount)
    }

    @Test
    fun `one time exact barcode request cannot bypass explicit confirmation into persistence`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val exactRequest =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.exactBarcodeRequest(
                    itemKey = ShoppingItemKey("eggs"),
                    rawGtin = "036000291452",
                    candidateId = "barcode-only"
                ).candidate
            )

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(store, exactRequest)

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceTransactionIssue.PRODUCT_NOT_USER_CONFIRMED,
            result.issue
        )
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.replaceCount)
        assertNull(storage.bytes)
    }

    @Test
    fun `store discovery suggestion cannot bypass explicit confirmation into persistence`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val suggestion =
            PracticalShoppingStoreIdentityCandidate(
                candidateId = "suggestion",
                storeKey = ShoppingStoreKey("north"),
                scope = storeScope(11L),
                relationship = PracticalShoppingStoreIdentityRelationship.SOURCE_LOCATION_SUGGESTION,
                providerId = EvidenceProviderId("openstreetmap")
            )

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedStore(store, suggestion)

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceTransactionIssue.STORE_NOT_USER_CONFIRMED,
            result.issue
        )
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.replaceCount)
    }

    @Test
    fun `same product key replaces deterministically without growing persisted state`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        assertTrue(
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store,
                confirmedProduct("eggs", "036000291452")
            ).accepted
        )
        val second =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store,
                confirmedProduct("eggs", "042100005264")
            )

        assertTrue(second.accepted)
        assertEquals(1, requireNotNull(second.state).productPreferences.size)
        assertEquals("042100005264", second.state.productFor(ShoppingItemKey("eggs"))?.sourceIdentity?.gtin)
        assertEquals(second.state, store.load().state)
    }

    @Test
    fun `same store key replaces exact scope without growing persisted state`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        assertTrue(
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedStore(
                store,
                confirmedStore("north", 11L)
            ).accepted
        )
        val second =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedStore(
                store,
                confirmedStore("north", 22L)
            )

        assertTrue(second.accepted)
        assertEquals(1, requireNotNull(second.state).storePreferences.size)
        assertEquals("osm:node:22", second.state.storeFor(ShoppingStoreKey("north"))?.scope?.locationKey)
        assertEquals(second.state, store.load().state)
    }

    @Test
    fun `product capacity rejection leaves persisted bytes untouched`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val full =
            state(
                products =
                    (0 until 128).map { index ->
                        PracticalShoppingSavedExactProductPreference(
                            itemKey = ShoppingItemKey("item-${index.toString().padStart(3, '0')}"),
                            providerId = EvidenceProviderId("catalog"),
                            sourceIdentity = SourceProductIdentity(providerItemId = "product-$index")
                        )
                    },
                stores = emptyList()
            )
        assertTrue(store.replace(full).accepted)
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val writesBefore = storage.replaceCount

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store,
                confirmedProduct("overflow", "036000291452")
            )

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceTransactionIssue.PRODUCT_CAPACITY_REACHED,
            result.issue
        )
        assertEquals(writesBefore, storage.replaceCount)
        assertArrayEquals(oldBytes, storage.bytes)
    }

    @Test
    fun `store capacity rejection leaves persisted bytes untouched`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val full =
            state(
                products = emptyList(),
                stores =
                    (0 until 64).map { index ->
                        PracticalShoppingSavedExactStorePreference(
                            storeKey = ShoppingStoreKey("store-${index.toString().padStart(2, '0')}"),
                            scope = storeScope(index.toLong() + 1L)
                        )
                    }
            )
        assertTrue(store.replace(full).accepted)
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val writesBefore = storage.replaceCount

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedStore(
                store,
                confirmedStore("overflow-store", 999L)
            )

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceTransactionIssue.STORE_CAPACITY_REACHED,
            result.issue
        )
        assertEquals(writesBefore, storage.replaceCount)
        assertArrayEquals(oldBytes, storage.bytes)
    }

    @Test
    fun `corrupt existing document blocks save and remains untouched`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store,
                confirmedProduct("eggs", "036000291452")
            )

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceTransactionIssue.STORAGE_FAILURE, result.issue)
        assertEquals(PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_INVALID, result.storageIssue)
        assertEquals(PracticalShoppingSavedExactPreferenceCodecIssue.INVALID_HEADER, result.codecIssue)
        assertEquals(0, storage.replaceCount)
        assertArrayEquals(oldBytes, storage.bytes)
    }

    @Test
    fun `write failure leaves previous persisted generation intact`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        assertTrue(
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store,
                confirmedProduct("eggs", "036000291452")
            ).accepted
        )
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        storage.failReplace = true

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store,
                confirmedProduct("milk", "042100005264")
            )

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceTransactionIssue.STORAGE_FAILURE, result.issue)
        assertEquals(PracticalShoppingSavedExactPreferenceStorageIssue.WRITE_FAILED, result.storageIssue)
        assertArrayEquals(oldBytes, storage.bytes)
        storage.failReplace = false
        assertNull(store.load().state?.productFor(ShoppingItemKey("milk")))
        assertEquals("036000291452", store.load().state?.productFor(ShoppingItemKey("eggs"))?.sourceIdentity?.gtin)
    }

    @Test
    fun `user confirmed relationship with checksum invalid identity still fails closed`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val corruptConfirmed = confirmedProduct("eggs", "036000291453")

        val result =
            PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                store,
                corruptConfirmed
            )

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceTransactionIssue.PRODUCT_IDENTITY_INVALID,
            result.issue
        )
        assertEquals(0, storage.replaceCount)
        assertNull(storage.bytes)
    }

    @Test
    fun `concurrent saves through one store instance preserve both stable keys`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<PracticalShoppingSavedExactPreferenceTransactionResult>())

        val first =
            Thread {
                start.await()
                results +=
                    PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                        store,
                        confirmedProduct("eggs", "036000291452")
                    )
            }
        val second =
            Thread {
                start.await()
                results +=
                    PracticalShoppingSavedExactPreferenceTransactions.saveConfirmedProduct(
                        store,
                        confirmedProduct("milk", "042100005264")
                    )
            }

        first.start()
        second.start()
        start.countDown()
        first.join()
        second.join()

        assertEquals(2, results.size)
        assertTrue(results.all { it.accepted })
        val finalState = requireNotNull(store.load().state)
        assertEquals(2, finalState.productPreferences.size)
        assertTrue(finalState.productFor(ShoppingItemKey("eggs")) != null)
        assertTrue(finalState.productFor(ShoppingItemKey("milk")) != null)
    }

    private fun confirmedProduct(
        key: String,
        gtin: String
    ): PracticalShoppingProductIdentityCandidate =
        PracticalShoppingProductIdentityCandidate(
            candidateId = "confirmed-$key-$gtin",
            itemKey = ShoppingItemKey(key),
            providerId = EvidenceProviderId("confirmed-catalog"),
            sourceIdentity = SourceProductIdentity(gtin = gtin),
            relationship = PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
        )

    private fun confirmedStore(
        key: String,
        nodeId: Long
    ): PracticalShoppingStoreIdentityCandidate =
        PracticalShoppingStoreIdentityCandidate(
            candidateId = "confirmed-$key-$nodeId",
            storeKey = ShoppingStoreKey(key),
            scope = storeScope(nodeId),
            relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
        )

    private fun storeScope(nodeId: Long): PracticalShoppingStoreIdentityScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "wikidata:Q483551",
            locationKey = "osm:node:$nodeId",
            commerceChannelKey = "physical-store"
        )

    private fun state(
        products: List<PracticalShoppingSavedExactProductPreference>,
        stores: List<PracticalShoppingSavedExactStorePreference>
    ): PracticalShoppingSavedExactPreferenceState =
        requireNotNull(
            PracticalShoppingSavedExactPreferenceStateManager.load(
                PracticalShoppingSavedExactPreferenceDocument(
                    schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                    productPreferences = products,
                    storePreferences = stores
                )
            ).state
        )

    private class FakeByteStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedExactPreferenceByteStorage {
        var bytes: ByteArray? = bytes?.copyOf()
        var failReplace: Boolean = false
        var replaceCount: Int = 0
        var readCount: Int = 0

        override fun read(
            maxBytes: Int
        ): PracticalShoppingSavedExactPreferenceRawReadResult {
            readCount += 1
            val current = bytes
                ?: return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = false
                )
            if (current.size > maxBytes) {
                return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = true,
                    issue = PracticalShoppingSavedExactPreferenceRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return PracticalShoppingSavedExactPreferenceRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            replaceCount += 1
            if (failReplace) {
                return false
            }
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }
}

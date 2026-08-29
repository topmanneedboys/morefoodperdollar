package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedDisplayMetadataTransactionsTest {

    @Test
    fun `current exact product label is persisted transactionally`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val exact = exactState(products = listOf(exactProduct("eggs", "036000291452")))
        val entry = productEntry("eggs", "gtin:0036000291452", "Example Eggs")

        val result =
            PracticalShoppingSavedDisplayMetadataTransactions.saveProductEntry(
                store = store,
                exactState = exact,
                entry = entry
            )

        assertTrue(result.accepted)
        assertEquals(entry, requireNotNull(result.snapshot).productEntries.single())
        assertEquals(result.snapshot, store.load().snapshot)
        assertEquals(1, storage.replaceCount)
    }

    @Test
    fun `label for a different exact product is rejected before display storage is read`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val exact = exactState(products = listOf(exactProduct("eggs", "036000291452")))
        val wrong = productEntry("eggs", "gtin:0012345678905", "Wrong Eggs")

        val result =
            PracticalShoppingSavedDisplayMetadataTransactions.saveProductEntry(store, exact, wrong)

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataTransactionIssue.PRODUCT_NOT_CURRENT_EXACT_CHOICE,
            result.issue
        )
        assertEquals(0, storage.readCount)
        assertEquals(0, storage.replaceCount)
        assertNull(storage.bytes)
    }

    @Test
    fun `current exact store label is persisted and wrong scope is rejected`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val exact = exactState(stores = listOf(exactStore("north", 11L)))
        val current = storeEntry("north", 11L, "North Market")
        val wrong = storeEntry("north", 22L, "Wrong Branch")

        val accepted =
            PracticalShoppingSavedDisplayMetadataTransactions.saveStoreEntry(store, exact, current)
        val rejected =
            PracticalShoppingSavedDisplayMetadataTransactions.saveStoreEntry(store, exact, wrong)

        assertTrue(accepted.accepted)
        assertEquals(current, requireNotNull(accepted.snapshot).storeEntries.single())
        assertFalse(rejected.accepted)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataTransactionIssue.STORE_NOT_CURRENT_EXACT_CHOICE,
            rejected.issue
        )
        assertEquals(1, storage.replaceCount)
    }

    @Test
    fun `product upsert prunes stale products but leaves store metadata untouched`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val preservedStore = storeEntry("old-store", 99L, "Old Store")
        val initial =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries =
                    listOf(
                        productEntry("eggs", "gtin:0036000291452", "Old Eggs"),
                        productEntry("orphan", "gtin:0042100005264", "Orphan Product")
                    ),
                storeEntries = listOf(preservedStore)
            )
        assertTrue(store.replace(initial).accepted)

        val exact = exactState(products = listOf(exactProduct("eggs", "012345678905")))
        val replacement = productEntry("eggs", "gtin:0012345678905", "New Eggs")

        val result =
            PracticalShoppingSavedDisplayMetadataTransactions.saveProductEntry(
                store,
                exact,
                replacement
            )

        assertTrue(result.accepted)
        val snapshot = requireNotNull(result.snapshot)
        assertEquals(listOf(replacement), snapshot.productEntries)
        assertEquals(listOf(preservedStore), snapshot.storeEntries)
        assertEquals(
            listOf(ShoppingItemKey("eggs"), ShoppingItemKey("orphan")),
            result.prunedStaleProductKeys
        )
        assertTrue(result.prunedStaleStoreKeys.isEmpty())
    }

    @Test
    fun `store upsert prunes stale stores but leaves product metadata untouched`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val preservedProduct = productEntry("orphan-product", "gtin:0036000291452", "Product")
        val initial =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries = listOf(preservedProduct),
                storeEntries =
                    listOf(
                        storeEntry("north", 11L, "Old North"),
                        storeEntry("orphan-store", 99L, "Orphan Store")
                    )
            )
        assertTrue(store.replace(initial).accepted)

        val exact = exactState(stores = listOf(exactStore("north", 22L)))
        val replacement = storeEntry("north", 22L, "New North")

        val result =
            PracticalShoppingSavedDisplayMetadataTransactions.saveStoreEntry(
                store,
                exact,
                replacement
            )

        assertTrue(result.accepted)
        val snapshot = requireNotNull(result.snapshot)
        assertEquals(listOf(preservedProduct), snapshot.productEntries)
        assertEquals(listOf(replacement), snapshot.storeEntries)
        assertTrue(result.prunedStaleProductKeys.isEmpty())
        assertEquals(
            listOf(ShoppingStoreKey("north"), ShoppingStoreKey("orphan-store")),
            result.prunedStaleStoreKeys
        )
    }

    @Test
    fun `corrupt display document blocks transactional upsert and remains untouched`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val exact = exactState(products = listOf(exactProduct("eggs", "036000291452")))

        val result =
            PracticalShoppingSavedDisplayMetadataTransactions.saveProductEntry(
                store,
                exact,
                productEntry("eggs", "gtin:0036000291452", "Example Eggs")
            )

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedDisplayMetadataTransactionIssue.STORAGE_FAILURE, result.issue)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_INVALID,
            result.storageIssue
        )
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INVALID_HEADER,
            result.codecIssue
        )
        assertEquals(0, storage.replaceCount)
        assertArrayEquals(oldBytes, storage.bytes)
    }

    @Test
    fun `display write failure leaves prior generation intact`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val exact =
            exactState(
                products =
                    listOf(
                        exactProduct("eggs", "036000291452"),
                        exactProduct("milk", "042100005264")
                    )
            )
        val first = productEntry("eggs", "gtin:0036000291452", "Example Eggs")
        assertTrue(
            PracticalShoppingSavedDisplayMetadataTransactions
                .saveProductEntry(store, exact, first)
                .accepted
        )
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        storage.failReplace = true

        val result =
            PracticalShoppingSavedDisplayMetadataTransactions.saveProductEntry(
                store,
                exact,
                productEntry("milk", "gtin:0042100005264", "Milk")
            )

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedDisplayMetadataTransactionIssue.STORAGE_FAILURE, result.issue)
        assertEquals(PracticalShoppingSavedDisplayMetadataStorageIssue.WRITE_FAILED, result.storageIssue)
        assertArrayEquals(oldBytes, storage.bytes)
    }

    @Test
    fun `concurrent product label saves on one store instance preserve both keys`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val exact =
            exactState(
                products =
                    listOf(
                        exactProduct("eggs", "036000291452"),
                        exactProduct("milk", "042100005264")
                    )
            )
        val start = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val results =
            Collections.synchronizedList(
                mutableListOf<PracticalShoppingSavedDisplayMetadataTransactionResult>()
            )

        listOf(
            productEntry("eggs", "gtin:0036000291452", "Example Eggs"),
            productEntry("milk", "gtin:0042100005264", "Milk")
        ).forEach { entry ->
            Thread {
                try {
                    start.await()
                    results +=
                        PracticalShoppingSavedDisplayMetadataTransactions.saveProductEntry(
                            store,
                            exact,
                            entry
                        )
                } finally {
                    finished.countDown()
                }
            }.start()
        }

        start.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))

        assertEquals(2, results.size)
        assertTrue(results.all { it.accepted })
        val finalSnapshot = requireNotNull(store.load().snapshot)
        assertEquals(
            setOf(ShoppingItemKey("eggs"), ShoppingItemKey("milk")),
            finalSnapshot.productEntries.map { it.itemKey }.toSet()
        )
    }

    private fun exactState(
        products: List<PracticalShoppingSavedExactProductPreference> = emptyList(),
        stores: List<PracticalShoppingSavedExactStorePreference> = emptyList()
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

    private fun exactProduct(
        key: String,
        gtin: String
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = ShoppingItemKey(key),
            providerId = EvidenceProviderId("open-food-facts"),
            sourceIdentity = SourceProductIdentity(gtin = gtin),
            dataset = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
        )

    private fun exactStore(
        key: String,
        nodeId: Long
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = ShoppingStoreKey(key),
            scope = storeScope(nodeId),
            providerId = EvidenceProviderId("openstreetmap"),
            dataset = OpenStreetMapPracticalShoppingStoreSuggestionAdapter.DATASET_NAMESPACE
        )

    private fun productEntry(
        key: String,
        productKey: String,
        name: String
    ): PracticalShoppingSavedProductDisplayMetadataEntry =
        PracticalShoppingSavedProductDisplayMetadataEntry(
            itemKey = ShoppingItemKey(key),
            productKey =
                ProductionProductEvidenceKey(
                    value = productKey,
                    scope = ProductionProductKeyScope.CROSS_SOURCE_GTIN
                ),
            displayName = name,
            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
        )

    private fun storeEntry(
        key: String,
        nodeId: Long,
        name: String
    ): PracticalShoppingSavedStoreDisplayMetadataEntry =
        PracticalShoppingSavedStoreDisplayMetadataEntry(
            storeKey = ShoppingStoreKey(key),
            scope = storeScope(nodeId),
            displayName = name,
            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
        )

    private fun storeScope(nodeId: Long): PracticalShoppingStoreIdentityScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "wikidata:Q483551",
            locationKey = "osm:node:$nodeId",
            commerceChannelKey = "PHYSICAL_STORE"
        )

    private class FakeByteStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedDisplayMetadataByteStorage {
        var bytes: ByteArray? = bytes?.copyOf()
        var failReplace: Boolean = false
        var replaceCount: Int = 0
        var readCount: Int = 0

        override fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult {
            readCount += 1
            val current = bytes
                ?: return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = false
                )
            if (current.size > maxBytes) {
                return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = true,
                    issue = PracticalShoppingSavedDisplayMetadataRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return PracticalShoppingSavedDisplayMetadataRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            replaceCount += 1
            if (failReplace) return false
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }
}

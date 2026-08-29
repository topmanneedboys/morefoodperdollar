package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedDisplayMetadataLocalStoreTest {

    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")

    @Test
    fun `missing file loads as empty display metadata`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)

        val loaded = store.load()

        assertTrue(loaded.accepted)
        assertFalse(loaded.foundStoredDocument)
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(),
            loaded.snapshot
        )
        assertNull(loaded.issue)
    }

    @Test
    fun `replace persists one display codec document and load returns exact snapshot`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val snapshot = snapshot()

        val replaced = store.replace(snapshot)
        val loaded = store.load()

        assertTrue(replaced.accepted)
        assertEquals(snapshot, replaced.snapshot)
        assertTrue(loaded.accepted)
        assertTrue(loaded.foundStoredDocument)
        assertEquals(snapshot, loaded.snapshot)
        assertEquals(1, storage.replaceCount)
        assertArrayEquals(
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataCodec.encode(snapshot).bytes
            ),
            requireNotNull(storage.bytes)
        )
    }

    @Test
    fun `failed replacement leaves prior display bytes untouched`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        val original = snapshot()
        val replacement =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries = listOf(productEntry(ShoppingItemKey("milk"), "Milk")),
                storeEntries = emptyList()
            )
        assertTrue(store.replace(original).accepted)
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        storage.failReplace = true

        val failed = store.replace(replacement)

        assertFalse(failed.accepted)
        assertEquals(PracticalShoppingSavedDisplayMetadataStorageIssue.WRITE_FAILED, failed.issue)
        assertArrayEquals(oldBytes, storage.bytes)
        assertEquals(original, store.load().snapshot)
    }

    @Test
    fun `delete product persists remaining store and is idempotent`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        assertTrue(store.replace(snapshot()).accepted)
        val writesBefore = storage.replaceCount

        val first = store.deleteProduct(eggs)
        val writesAfterFirst = storage.replaceCount
        val second = store.deleteProduct(eggs)

        assertTrue(first.accepted)
        assertTrue(requireNotNull(first.snapshot).productEntries.isEmpty())
        assertEquals(1, requireNotNull(first.snapshot).storeEntries.size)
        assertEquals(writesBefore + 1, writesAfterFirst)
        assertTrue(second.accepted)
        assertEquals(first.snapshot, second.snapshot)
        assertEquals(writesAfterFirst, storage.replaceCount)
    }

    @Test
    fun `delete store persists remaining product`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        assertTrue(store.replace(snapshot()).accepted)

        val result = store.deleteStore(north)

        assertTrue(result.accepted)
        assertTrue(requireNotNull(result.snapshot).storeEntries.isEmpty())
        assertEquals(1, requireNotNull(result.snapshot).productEntries.size)
        assertEquals(result.snapshot, store.load().snapshot)
    }

    @Test
    fun `corrupt display document blocks selective deletion instead of partial repair`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)

        val result = store.deleteProduct(eggs)

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_INVALID,
            result.issue
        )
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INVALID_HEADER,
            result.codecIssue
        )
        assertArrayEquals(oldBytes, storage.bytes)
        assertEquals(0, storage.replaceCount)
    }

    @Test
    fun `clear all recovers corrupt display metadata without touching exact preference state`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)

        val cleared = store.clearAll()
        val loaded = store.load()

        assertTrue(cleared.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(),
            cleared.snapshot
        )
        assertNull(storage.bytes)
        assertEquals(1, storage.deleteCount)
        assertTrue(loaded.accepted)
        assertFalse(loaded.foundStoredDocument)
    }

    @Test
    fun `clear all reports display metadata deletion failure and preserves bytes`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        storage.failDelete = true
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)

        val result = store.clearAll()

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedDisplayMetadataStorageIssue.DELETE_FAILED, result.issue)
        assertArrayEquals(oldBytes, storage.bytes)
    }

    @Test
    fun `bounded raw read failure is surfaced before display codec parsing`() {
        val storage = FakeByteStorage()
        storage.forcedReadIssue = PracticalShoppingSavedDisplayMetadataRawReadIssue.INPUT_TOO_LARGE
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)

        val loaded = store.load()

        assertFalse(loaded.accepted)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_TOO_LARGE,
            loaded.issue
        )
        assertNull(loaded.codecIssue)
    }

    @Test
    fun `raw io failure is not mistaken for empty display metadata`() {
        val storage = FakeByteStorage()
        storage.forcedReadIssue = PracticalShoppingSavedDisplayMetadataRawReadIssue.IO_FAILURE
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)

        val loaded = store.load()

        assertFalse(loaded.accepted)
        assertEquals(PracticalShoppingSavedDisplayMetadataStorageIssue.READ_FAILED, loaded.issue)
        assertNull(loaded.snapshot)
    }

    @Test
    fun `detached stored label is harmless when exact saved identity changes`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedDisplayMetadataLocalStore(storage)
        assertTrue(store.replace(snapshot()).accepted)
        val stored = requireNotNull(store.load().snapshot)

        val differentSavedProduct =
            PracticalShoppingSavedExactProductPreference(
                itemKey = eggs,
                providerId = com.valuepilot.core.EvidenceProviderId("open-food-facts"),
                sourceIdentity = com.valuepilot.core.SourceProductIdentity(gtin = "012345678905"),
                dataset = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
            )
        val exactState =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager.load(
                    PracticalShoppingSavedExactPreferenceDocument(
                        schemaVersion =
                            PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                        productPreferences = listOf(differentSavedProduct),
                        storePreferences = emptyList()
                    )
                ).state
            )

        val bound =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(exactState, stored)

        assertTrue(bound.metadata.productDisplayNames.isEmpty())
        assertEquals(listOf(eggs), bound.staleProductKeys)
    }

    private fun snapshot(): PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot =
        PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
            productEntries = listOf(productEntry(eggs, "Example Eggs")),
            storeEntries = listOf(storeEntry(north, "North Market"))
        )

    private fun productEntry(
        itemKey: ShoppingItemKey,
        name: String
    ): PracticalShoppingSavedProductDisplayMetadataEntry =
        PracticalShoppingSavedProductDisplayMetadataEntry(
            itemKey = itemKey,
            productKey =
                ProductionProductEvidenceKey(
                    value = "gtin:0036000291452",
                    scope = ProductionProductKeyScope.CROSS_SOURCE_GTIN
                ),
            displayName = name,
            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
        )

    private fun storeEntry(
        storeKey: ShoppingStoreKey,
        name: String
    ): PracticalShoppingSavedStoreDisplayMetadataEntry =
        PracticalShoppingSavedStoreDisplayMetadataEntry(
            storeKey = storeKey,
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:Q483551",
                    locationKey = "osm:node:12345",
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            displayName = name,
            basis = PracticalShoppingSavedDisplayMetadataBasis.OPENSTREETMAP_PLACE_NAME
        )

    private class FakeByteStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedDisplayMetadataByteStorage {
        var bytes: ByteArray? = bytes?.copyOf()
        var failReplace: Boolean = false
        var failDelete: Boolean = false
        var forcedReadIssue: PracticalShoppingSavedDisplayMetadataRawReadIssue? = null
        var replaceCount: Int = 0
        var deleteCount: Int = 0

        override fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult {
            forcedReadIssue?.let { issue ->
                return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = false,
                    issue = issue
                )
            }
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
            deleteCount += 1
            if (failDelete) return false
            bytes = null
            return true
        }
    }
}

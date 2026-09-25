package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceLocalStoreTest {

    @Test
    fun `missing file loads as empty local state`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val loaded = store.load()

        assertTrue(loaded.accepted)
        assertFalse(loaded.foundStoredDocument)
        assertEquals(PracticalShoppingSavedExactPreferenceState.empty(), loaded.state)
        assertNull(loaded.issue)
    }

    @Test
    fun `replace persists one codec document and load returns exact state`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val state = state()

        val replaced = store.replace(state)
        val loaded = store.load()

        assertTrue(replaced.accepted)
        assertEquals(state, replaced.state)
        assertTrue(loaded.accepted)
        assertTrue(loaded.foundStoredDocument)
        assertEquals(state, loaded.state)
        assertEquals(1, storage.replaceCount)
        assertArrayEquals(
            requireNotNull(PracticalShoppingSavedExactPreferenceCodec.encode(state).bytes),
            requireNotNull(storage.bytes)
        )
    }

    @Test
    fun `failed replacement leaves prior bytes untouched`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val original = state()
        val replacement =
            state(
                products = listOf(product("milk", "042100005264")),
                stores = listOf(store("south", 22L))
            )
        assertTrue(store.replace(original).accepted)
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        storage.failReplace = true

        val failed = store.replace(replacement)

        assertFalse(failed.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceStorageIssue.WRITE_FAILED, failed.issue)
        assertArrayEquals(oldBytes, storage.bytes)
        assertEquals(original, store.load().state)
    }

    @Test
    fun `delete product persists remaining store and is idempotent when repeated`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        assertTrue(store.replace(state()).accepted)
        val writesBeforeDelete = storage.replaceCount

        val first = store.deleteProduct(ShoppingItemKey("eggs"))
        val writesAfterFirst = storage.replaceCount
        val second = store.deleteProduct(ShoppingItemKey("eggs"))

        assertTrue(first.accepted)
        assertTrue(requireNotNull(first.state).productPreferences.isEmpty())
        assertEquals(1, requireNotNull(first.state).storePreferences.size)
        assertEquals(writesBeforeDelete + 1, writesAfterFirst)
        assertTrue(second.accepted)
        assertEquals(first.state, second.state)
        assertEquals(writesAfterFirst, storage.replaceCount)
        assertEquals(first.state, store.load().state)
    }

    @Test
    fun `delete store persists remaining product`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        assertTrue(store.replace(state()).accepted)

        val result = store.deleteStore(ShoppingStoreKey("north"))

        assertTrue(result.accepted)
        assertTrue(requireNotNull(result.state).storePreferences.isEmpty())
        assertEquals(1, requireNotNull(result.state).productPreferences.size)
        assertEquals(result.state, store.load().state)
    }

    @Test
    fun `corrupt document blocks selective deletion instead of partial recovery`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val result = store.deleteProduct(ShoppingItemKey("eggs"))

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_INVALID,
            result.issue
        )
        assertEquals(
            PracticalShoppingSavedExactPreferenceCodecIssue.INVALID_HEADER,
            result.codecIssue
        )
        assertArrayEquals(oldBytes, storage.bytes)
        assertEquals(0, storage.replaceCount)
    }

    @Test
    fun `clear all can recover from corrupt local state`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val cleared = store.clearAll()
        val loaded = store.load()

        assertTrue(cleared.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceState.empty(), cleared.state)
        assertNull(storage.bytes)
        assertEquals(1, storage.deleteCount)
        assertTrue(loaded.accepted)
        assertFalse(loaded.foundStoredDocument)
        assertEquals(PracticalShoppingSavedExactPreferenceState.empty(), loaded.state)
    }

    @Test
    fun `clear all reports storage deletion failure`() {
        val storage = FakeByteStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        storage.failDelete = true
        val oldBytes = requireNotNull(storage.bytes).copyOf()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val result = store.clearAll()

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceStorageIssue.DELETE_FAILED, result.issue)
        assertArrayEquals(oldBytes, storage.bytes)
    }

    @Test
    fun `bounded raw read failure is surfaced before codec parsing`() {
        val storage = FakeByteStorage()
        storage.forcedReadIssue = PracticalShoppingSavedExactPreferenceRawReadIssue.INPUT_TOO_LARGE
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val loaded = store.load()

        assertFalse(loaded.accepted)
        assertEquals(
            PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_TOO_LARGE,
            loaded.issue
        )
        assertNull(loaded.codecIssue)
        assertTrue(loaded.documentIssues.isEmpty())
    }

    @Test
    fun `raw io failure is not mistaken for empty preferences`() {
        val storage = FakeByteStorage()
        storage.forcedReadIssue = PracticalShoppingSavedExactPreferenceRawReadIssue.IO_FAILURE
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)

        val loaded = store.load()

        assertFalse(loaded.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceStorageIssue.READ_FAILED, loaded.issue)
        assertNull(loaded.state)
    }

    @Test
    fun `codec rejection prevents oversized key from reaching storage`() {
        val storage = FakeByteStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val oversized =
            PracticalShoppingSavedExactPreferenceState(
                productPreferences =
                    listOf(
                        product("eggs", "036000291452").copy(
                            itemKey = ShoppingItemKey("x".repeat(513))
                        )
                    ),
                storePreferences = emptyList()
            )

        val result = store.replace(oversized)

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceStorageIssue.ENCODE_REJECTED, result.issue)
        assertEquals(PracticalShoppingSavedExactPreferenceCodecIssue.FIELD_TOO_LARGE, result.codecIssue)
        assertEquals(0, storage.replaceCount)
        assertNull(storage.bytes)
    }

    private fun state(
        products: List<PracticalShoppingSavedExactProductPreference> =
            listOf(product("eggs", "036000291452")),
        stores: List<PracticalShoppingSavedExactStorePreference> =
            listOf(store("north", 11L))
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

    private fun product(
        key: String,
        gtin: String
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = ShoppingItemKey(key),
            providerId = EvidenceProviderId("open-food-facts"),
            sourceIdentity = SourceProductIdentity(gtin = gtin),
            dataset = productDataset
        )

    private fun store(
        key: String,
        osmNodeId: Long
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = ShoppingStoreKey(key),
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:Q483551",
                    locationKey = "osm:node:$osmNodeId",
                    commerceChannelKey = "physical-store"
                ),
            providerId = EvidenceProviderId("openstreetmap"),
            dataset = storeDataset
        )

    private class FakeByteStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedExactPreferenceByteStorage {
        var bytes: ByteArray? = bytes?.copyOf()
        var failReplace: Boolean = false
        var failDelete: Boolean = false
        var forcedReadIssue: PracticalShoppingSavedExactPreferenceRawReadIssue? = null
        var replaceCount: Int = 0
        var deleteCount: Int = 0

        override fun read(
            maxBytes: Int
        ): PracticalShoppingSavedExactPreferenceRawReadResult {
            forcedReadIssue?.let { issue ->
                return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = false,
                    issue = issue
                )
            }
            val current = bytes
                ?: return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = false
                )
            if (current.size > maxBytes) {
                return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = false,
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
            deleteCount += 1
            if (failDelete) {
                return false
            }
            bytes = null
            return true
        }
    }

    private val productDataset =
        EvidenceDatasetNamespace(
            id = "openfoodfacts-products",
            displayName = "Open Food Facts products",
            licenseId = "ODbL-1.0",
            storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
        )

    private val storeDataset =
        EvidenceDatasetNamespace(
            id = "openstreetmap-places",
            displayName = "OpenStreetMap places",
            licenseId = "ODbL-1.0",
            storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
        )
}

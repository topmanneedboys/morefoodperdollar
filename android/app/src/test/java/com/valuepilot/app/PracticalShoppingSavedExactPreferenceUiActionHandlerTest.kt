package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceUiActionHandlerTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val north = ShoppingStoreKey("north")

    @Test
    fun `delete product action routes through local persistence and preserves store`() {
        val storage = MemoryStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val initial =
            state(
                products = listOf(product(eggs)),
                stores = listOf(storePreference(north))
            )
        assertTrue(store.replace(initial).accepted)

        val result =
            PracticalShoppingSavedExactPreferenceUiActionHandler.handle(
                store = store,
                action = PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(eggs)
            )

        assertTrue(result.accepted)
        val persisted = requireNotNull(store.load().state)
        assertTrue(persisted.productPreferences.isEmpty())
        assertEquals(listOf(north), persisted.storePreferences.map { it.storeKey })
        assertTrue(storage.replaceCallCount >= 2)
    }

    @Test
    fun `delete store action routes through local persistence and preserves product`() {
        val storage = MemoryStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        val initial =
            state(
                products = listOf(product(eggs)),
                stores = listOf(storePreference(north))
            )
        assertTrue(store.replace(initial).accepted)

        val result =
            PracticalShoppingSavedExactPreferenceUiActionHandler.handle(
                store = store,
                action = PracticalShoppingSavedExactPreferenceUiAction.DeleteStore(north)
            )

        assertTrue(result.accepted)
        val persisted = requireNotNull(store.load().state)
        assertEquals(listOf(eggs), persisted.productPreferences.map { it.itemKey })
        assertTrue(persisted.storePreferences.isEmpty())
    }

    @Test
    fun `clear all action deletes persisted document and returns empty state`() {
        val storage = MemoryStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        assertTrue(
            store.replace(
                state(
                    products = listOf(product(eggs), product(milk, "012345678905")),
                    stores = listOf(storePreference(north))
                )
            ).accepted
        )

        val result =
            PracticalShoppingSavedExactPreferenceUiActionHandler.handle(
                store = store,
                action = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
            )

        assertTrue(result.accepted)
        assertTrue(requireNotNull(result.state).productPreferences.isEmpty())
        assertTrue(requireNotNull(result.state).storePreferences.isEmpty())
        assertNull(storage.bytes)
        assertEquals(1, storage.deleteCallCount)

        val reloaded = store.load()
        assertTrue(reloaded.accepted)
        assertFalse(reloaded.foundStoredDocument)
    }

    @Test
    fun `persistence failure is surfaced instead of pretending clear succeeded`() {
        val storage = MemoryStorage(failDelete = true)
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        assertTrue(store.replace(state(products = listOf(product(eggs)))).accepted)
        val before = requireNotNull(storage.bytes).copyOf()

        val result =
            PracticalShoppingSavedExactPreferenceUiActionHandler.handle(
                store = store,
                action = PracticalShoppingSavedExactPreferenceUiAction.ClearAll
            )

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceStorageIssue.DELETE_FAILED, result.issue)
        assertTrue(before.contentEquals(requireNotNull(storage.bytes)))
    }

    @Test
    fun `deleting absent typed key remains idempotent through the store`() {
        val storage = MemoryStorage()
        val store = PracticalShoppingSavedExactPreferenceLocalStore(storage)
        assertTrue(store.replace(state(products = listOf(product(eggs)))).accepted)
        val writesBefore = storage.replaceCallCount

        val result =
            PracticalShoppingSavedExactPreferenceUiActionHandler.handle(
                store = store,
                action = PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(milk)
            )

        assertTrue(result.accepted)
        assertEquals(writesBefore, storage.replaceCallCount)
        assertEquals(listOf(eggs), requireNotNull(result.state).productPreferences.map { it.itemKey })
    }

    private fun product(
        itemKey: ShoppingItemKey,
        gtin: String = "036000291452"
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = itemKey,
            providerId = EvidenceProviderId("openfoodfacts"),
            sourceIdentity = SourceProductIdentity(gtin = gtin)
        )

    private fun storePreference(
        storeKey: ShoppingStoreKey
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = storeKey,
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:Q483551",
                    locationKey = "osm:node:12345",
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            providerId = EvidenceProviderId("openstreetmap")
        )

    private fun state(
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

    private class MemoryStorage(
        initialBytes: ByteArray? = null,
        var failReplace: Boolean = false,
        var failDelete: Boolean = false
    ) : PracticalShoppingSavedExactPreferenceByteStorage {
        var bytes: ByteArray? = initialBytes?.copyOf()
        var replaceCallCount: Int = 0
        var deleteCallCount: Int = 0

        override fun read(maxBytes: Int): PracticalShoppingSavedExactPreferenceRawReadResult {
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
            replaceCallCount += 1
            if (failReplace) {
                return false
            }
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            deleteCallCount += 1
            if (failDelete) {
                return false
            }
            bytes = null
            return true
        }
    }
}

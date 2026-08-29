package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceStateManagerTest {

    @Test
    fun `valid document loads and exports in deterministic stable key order`() {
        val document =
            PracticalShoppingSavedExactPreferenceDocument(
                schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                productPreferences =
                    listOf(
                        productPreference("milk", "036000291452"),
                        productPreference("eggs", "012345678905")
                    ),
                storePreferences =
                    listOf(
                        storePreference("west", "Q200", "osm:way:2"),
                        storePreference("north", "Q100", "osm:node:1")
                    )
            )

        val loaded = PracticalShoppingSavedExactPreferenceStateManager.load(document)

        assertTrue(loaded.accepted)
        val state = requireNotNull(loaded.state)
        assertEquals(listOf("eggs", "milk"), state.productPreferences.map { it.itemKey.value })
        assertEquals(listOf("north", "west"), state.storePreferences.map { it.storeKey.value })

        val exported = PracticalShoppingSavedExactPreferenceStateManager.document(state)
        assertEquals(PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion, exported.schemaVersion)
        assertEquals(listOf("eggs", "milk"), exported.productPreferences.map { it.itemKey.value })
        assertEquals(listOf("north", "west"), exported.storePreferences.map { it.storeKey.value })
    }

    @Test
    fun `unsupported schema and duplicate keys fail closed without partial state`() {
        val document =
            PracticalShoppingSavedExactPreferenceDocument(
                schemaVersion = 99,
                productPreferences =
                    listOf(
                        productPreference("eggs", "036000291452"),
                        productPreference("eggs", "012345678905")
                    ),
                storePreferences =
                    listOf(
                        storePreference("north", "Q100", "osm:node:1"),
                        storePreference("north", "Q100", "osm:node:2")
                    )
            )

        val loaded = PracticalShoppingSavedExactPreferenceStateManager.load(document)

        assertFalse(loaded.accepted)
        assertNull(loaded.state)
        assertEquals(
            setOf(
                PracticalShoppingSavedExactPreferenceLoadIssue.UNSUPPORTED_SCHEMA_VERSION,
                PracticalShoppingSavedExactPreferenceLoadIssue.DUPLICATE_PRODUCT_ITEM_KEY,
                PracticalShoppingSavedExactPreferenceLoadIssue.DUPLICATE_STORE_KEY
            ),
            loaded.issues
        )
    }

    @Test
    fun `over capacity persisted document fails closed`() {
        val document =
            PracticalShoppingSavedExactPreferenceDocument(
                schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                productPreferences =
                    (0..128).map { index ->
                        productPreference("item-$index", providerItemId = "product-$index")
                    },
                storePreferences =
                    (0..64).map { index ->
                        storePreference("store-$index", "Q${index + 1}", "osm:node:${index + 1}")
                    }
            )

        val loaded = PracticalShoppingSavedExactPreferenceStateManager.load(document)

        assertFalse(loaded.accepted)
        assertNull(loaded.state)
        assertEquals(
            setOf(
                PracticalShoppingSavedExactPreferenceLoadIssue.TOO_MANY_PRODUCT_PREFERENCES,
                PracticalShoppingSavedExactPreferenceLoadIssue.TOO_MANY_STORE_PREFERENCES
            ),
            loaded.issues
        )
    }

    @Test
    fun `corrupted unresolvable product identity fails closed on load`() {
        val document =
            PracticalShoppingSavedExactPreferenceDocument(
                schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                productPreferences =
                    listOf(
                        productPreference("eggs", "036000291453")
                    ),
                storePreferences = emptyList()
            )

        val loaded = PracticalShoppingSavedExactPreferenceStateManager.load(document)

        assertFalse(loaded.accepted)
        assertNull(loaded.state)
        assertEquals(
            setOf(PracticalShoppingSavedExactPreferenceLoadIssue.PRODUCT_IDENTITY_UNAVAILABLE),
            loaded.issues
        )
    }

    @Test
    fun `upserting existing product replaces exact identity without growing state`() {
        val initial =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager.load(
                    PracticalShoppingSavedExactPreferenceDocument(
                        schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                        productPreferences = listOf(productPreference("eggs", "036000291452")),
                        storePreferences = emptyList()
                    )
                ).state
            )
        val replacement = productPreference("eggs", "012345678905")

        val result = PracticalShoppingSavedExactPreferenceStateManager.upsertProduct(initial, replacement)

        assertTrue(result.accepted)
        assertEquals(1, result.state.productPreferences.size)
        assertEquals(replacement, result.state.productFor(ShoppingItemKey("eggs")))
    }

    @Test
    fun `new product beyond capacity is rejected without changing state`() {
        val full =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager.load(
                    PracticalShoppingSavedExactPreferenceDocument(
                        schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                        productPreferences =
                            (0 until 128).map { index ->
                                productPreference("item-$index", providerItemId = "product-$index")
                            },
                        storePreferences = emptyList()
                    )
                ).state
            )

        val result =
            PracticalShoppingSavedExactPreferenceStateManager.upsertProduct(
                state = full,
                preference = productPreference("overflow", providerItemId = "overflow-product")
            )

        assertFalse(result.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceMutationIssue.PRODUCT_CAPACITY_REACHED, result.issue)
        assertSame(full, result.state)
    }

    @Test
    fun `new store beyond capacity is rejected while replacing existing store remains allowed`() {
        val full =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager.load(
                    PracticalShoppingSavedExactPreferenceDocument(
                        schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                        productPreferences = emptyList(),
                        storePreferences =
                            (0 until 64).map { index ->
                                storePreference("store-$index", "Q${index + 1}", "osm:node:${index + 1}")
                            }
                    )
                ).state
            )

        val overflow =
            PracticalShoppingSavedExactPreferenceStateManager.upsertStore(
                state = full,
                preference = storePreference("overflow", "Q999", "osm:node:999")
            )
        assertFalse(overflow.accepted)
        assertEquals(PracticalShoppingSavedExactPreferenceMutationIssue.STORE_CAPACITY_REACHED, overflow.issue)
        assertSame(full, overflow.state)

        val replacement = storePreference("store-0", "Q500", "osm:way:500")
        val replaced = PracticalShoppingSavedExactPreferenceStateManager.upsertStore(full, replacement)
        assertTrue(replaced.accepted)
        assertEquals(64, replaced.state.storePreferences.size)
        assertEquals(replacement, replaced.state.storeFor(ShoppingStoreKey("store-0")))
    }

    @Test
    fun `remove operations are idempotent and clear removes all preferences`() {
        val loaded =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager.load(
                    PracticalShoppingSavedExactPreferenceDocument(
                        schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                        productPreferences =
                            listOf(
                                productPreference("eggs", "036000291452"),
                                productPreference("milk", "012345678905")
                            ),
                        storePreferences = listOf(storePreference("north", "Q100", "osm:node:1"))
                    )
                ).state
            )

        val withoutEggs =
            PracticalShoppingSavedExactPreferenceStateManager.removeProduct(
                loaded,
                ShoppingItemKey("eggs")
            )
        assertNull(withoutEggs.productFor(ShoppingItemKey("eggs")))
        assertEquals(1, withoutEggs.productPreferences.size)

        val stillWithoutEggs =
            PracticalShoppingSavedExactPreferenceStateManager.removeProduct(
                withoutEggs,
                ShoppingItemKey("eggs")
            )
        assertEquals(withoutEggs, stillWithoutEggs)

        val noStore =
            PracticalShoppingSavedExactPreferenceStateManager.removeStore(
                stillWithoutEggs,
                ShoppingStoreKey("north")
            )
        assertTrue(noStore.storePreferences.isEmpty())

        val cleared = PracticalShoppingSavedExactPreferenceStateManager.clear(noStore)
        assertTrue(cleared.productPreferences.isEmpty())
        assertTrue(cleared.storePreferences.isEmpty())
        assertSame(cleared, PracticalShoppingSavedExactPreferenceStateManager.clear(cleared))
    }

    @Test
    fun `state lifecycle preserves exact source provenance and never introduces price or travel data`() {
        val product = productPreference("eggs", "036000291452")
        val store = storePreference("north", "Q100", "osm:node:1")

        val productResult =
            PracticalShoppingSavedExactPreferenceStateManager.upsertProduct(
                PracticalShoppingSavedExactPreferenceState.empty(),
                product
            )
        val storeResult =
            PracticalShoppingSavedExactPreferenceStateManager.upsertStore(
                productResult.state,
                store
            )

        assertEquals(product, storeResult.state.productFor(ShoppingItemKey("eggs")))
        assertEquals(store, storeResult.state.storeFor(ShoppingStoreKey("north")))
        assertEquals("open-food-facts", product.providerId.value)
        assertEquals("wikidata:Q100", store.scope.merchantKey)
        assertEquals("osm:node:1", store.scope.locationKey)
        assertEquals("PHYSICAL_STORE", store.scope.commerceChannelKey)
    }

    private fun productPreference(
        itemKey: String,
        gtin: String? = null,
        providerItemId: String? = null
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = ShoppingItemKey(itemKey),
            providerId = EvidenceProviderId("open-food-facts"),
            sourceIdentity =
                if (gtin != null) {
                    SourceProductIdentity(gtin = gtin)
                } else {
                    SourceProductIdentity(providerItemId = requireNotNull(providerItemId))
                },
            dataset = null
        )

    private fun storePreference(
        storeKey: String,
        merchantQid: String,
        locationKey: String
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = ShoppingStoreKey(storeKey),
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:$merchantQid",
                    locationKey = locationKey,
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            providerId = EvidenceProviderId("openstreetmap"),
            dataset = null
        )
}

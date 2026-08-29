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

class PracticalShoppingSavedExactPreferenceUiProjectorTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val north = ShoppingStoreKey("north")

    @Test
    fun `safe supplied names render with typed actions and no persisted identifiers in consumer text`() {
        val saved =
            state(
                products = listOf(product(eggs)),
                stores = listOf(store(north))
            )

        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(eggs to "Large Eggs 12 Pack"),
                        storeDisplayNames = mapOf(north to "North Market")
                    )
            )

        assertEquals("Saved choices", projection.state.headline)
        assertEquals("Large Eggs 12 Pack", projection.state.productRows.single().title)
        assertEquals("Exact product choice", projection.state.productRows.single().supportingText)
        assertEquals(
            PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(eggs),
            projection.state.productRows.single().action
        )
        assertEquals("North Market", projection.state.storeRows.single().title)
        assertEquals("Exact store choice", projection.state.storeRows.single().supportingText)
        assertEquals(
            PracticalShoppingSavedExactPreferenceUiAction.DeleteStore(north),
            projection.state.storeRows.single().action
        )
        assertEquals(PracticalShoppingSavedExactPreferenceUiAction.ClearAll, projection.state.clearAllAction)
        assertEquals(0, projection.state.unresolvedDisplayNameCount)
        assertNull(projection.state.notice)
        assertNull(projection.state.emptyMessage)
        assertTrue(projection.unresolvedProductKeys.isEmpty())
        assertTrue(projection.unresolvedStoreKeys.isEmpty())

        val visible = visibleText(projection.state)
        listOf(
            "036000291452",
            "openfoodfacts",
            "wikidata:Q483551",
            "Q483551",
            "osm:node:12345",
            "PHYSICAL_STORE"
        ).forEach { identifier ->
            assertFalse(visible.contains(identifier, ignoreCase = true))
        }
    }

    @Test
    fun `missing display name fails closed per row and is explicit without leaking the key`() {
        val saved =
            state(
                products = listOf(product(eggs)),
                stores = listOf(store(north))
            )

        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(eggs to "Large Eggs 12 Pack")
                    )
            )

        assertEquals(1, projection.state.productRows.size)
        assertTrue(projection.state.storeRows.isEmpty())
        assertEquals(1, projection.state.unresolvedDisplayNameCount)
        assertEquals(
            "1 saved choice needs a display name before it can be shown.",
            projection.state.notice
        )
        assertEquals(listOf(north), projection.unresolvedStoreKeys)
        assertFalse(visibleText(projection.state).contains(north.value, ignoreCase = true))
        assertFalse(visibleText(projection.state).contains("wikidata", ignoreCase = true))
    }

    @Test
    fun `raw GTIN provider merchant and prefixed identity suffixes are rejected as labels`() {
        val saved =
            state(
                products = listOf(product(eggs)),
                stores = listOf(store(north))
            )

        val rawGtin =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(eggs to "036000291452"),
                        storeDisplayNames = mapOf(north to "Q483551")
                    )
            )

        assertTrue(rawGtin.state.productRows.isEmpty())
        assertTrue(rawGtin.state.storeRows.isEmpty())
        assertEquals(2, rawGtin.state.unresolvedDisplayNameCount)
        assertEquals(listOf(eggs), rawGtin.unresolvedProductKeys)
        assertEquals(listOf(north), rawGtin.unresolvedStoreKeys)

        val providerAndMerchant =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(eggs to "openfoodfacts"),
                        storeDisplayNames = mapOf(north to "wikidata:Q483551")
                    )
            )

        assertTrue(providerAndMerchant.state.productRows.isEmpty())
        assertTrue(providerAndMerchant.state.storeRows.isEmpty())
        assertEquals(2, providerAndMerchant.state.unresolvedDisplayNameCount)
    }

    @Test
    fun `blank control character and oversized consumer names are not rendered`() {
        val saved =
            state(
                products = listOf(product(eggs), product(milk, "012345678905")),
                stores = listOf(store(north))
            )

        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames =
                            mapOf(
                                eggs to "   ",
                                milk to "M".repeat(161)
                            ),
                        storeDisplayNames = mapOf(north to "North\nMarket")
                    )
            )

        assertTrue(projection.state.productRows.isEmpty())
        assertTrue(projection.state.storeRows.isEmpty())
        assertEquals(3, projection.state.unresolvedDisplayNameCount)
        assertEquals(
            "3 saved choices need display names before they can be shown.",
            projection.state.notice
        )
    }

    @Test
    fun `extra display metadata cannot manufacture a saved row`() {
        val saved = state(products = listOf(product(eggs)))

        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames =
                            mapOf(
                                eggs to "Large Eggs 12 Pack",
                                milk to "Milk 4 L"
                            ),
                        storeDisplayNames = mapOf(north to "North Market")
                    )
            )

        assertEquals(listOf("Large Eggs 12 Pack"), projection.state.productRows.map { row -> row.title })
        assertTrue(projection.state.storeRows.isEmpty())
    }

    @Test
    fun `empty persisted state has calm empty state and no destructive action`() {
        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = PracticalShoppingSavedExactPreferenceState.empty(),
                metadata = PracticalShoppingSavedExactPreferenceDisplayMetadata()
            )

        assertTrue(projection.state.productRows.isEmpty())
        assertTrue(projection.state.storeRows.isEmpty())
        assertEquals(0, projection.state.unresolvedDisplayNameCount)
        assertNull(projection.state.notice)
        assertEquals("No saved choices yet.", projection.state.emptyMessage)
        assertNull(projection.state.clearAllAction)
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

    private fun store(
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

    private fun visibleText(state: PracticalShoppingSavedExactPreferenceUiState): String =
        buildList {
            add(state.headline)
            state.productRows.forEach { row ->
                add(row.title)
                add(row.supportingText)
            }
            state.storeRows.forEach { row ->
                add(row.title)
                add(row.supportingText)
            }
            state.notice?.let { notice -> add(notice) }
            state.emptyMessage?.let { empty -> add(empty) }
        }.joinToString(" | ")
}

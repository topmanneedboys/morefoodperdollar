package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceDisplayMetadataBinderTest {

    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")
    private val provider = EvidenceProviderId("openfoodfacts")

    @Test
    fun `same exact product and store bindings become projector metadata`() {
        val productPreference = productPreference("036000291452")
        val storePreference = storePreference("wikidata:Q483551", "osm:node:12345")
        val saved = state(listOf(productPreference), listOf(storePreference))
        val productKey = requireNotNull(
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = productPreference.providerId,
                identity = productPreference.sourceIdentity
            )
        )
        val snapshot =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries =
                    listOf(
                        PracticalShoppingSavedProductDisplayMetadataEntry(
                            itemKey = eggs,
                            productKey = productKey,
                            displayName = "Example Eggs",
                            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                        )
                    ),
                storeEntries =
                    listOf(
                        PracticalShoppingSavedStoreDisplayMetadataEntry(
                            storeKey = north,
                            scope = storePreference.scope,
                            displayName = "North Market",
                            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                        )
                    )
            )

        val result =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(saved, snapshot)

        assertFalse(result.hasStaleEntries)
        assertEquals("Example Eggs", result.metadata.productDisplayNames[eggs])
        assertEquals("North Market", result.metadata.storeDisplayNames[north])
        assertTrue(result.staleProductKeys.isEmpty())
        assertTrue(result.staleStoreKeys.isEmpty())
    }

    @Test
    fun `old product label becomes stale when same logical item is reconfirmed to different product`() {
        val oldPreference = productPreference("036000291452")
        val oldKey = requireNotNull(
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = oldPreference.providerId,
                identity = oldPreference.sourceIdentity
            )
        )
        val newPreference = productPreference("012345678905")
        val saved = state(listOf(newPreference), emptyList())
        val snapshot =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries =
                    listOf(
                        PracticalShoppingSavedProductDisplayMetadataEntry(
                            itemKey = eggs,
                            productKey = oldKey,
                            displayName = "Old Eggs Product",
                            basis = PracticalShoppingSavedDisplayMetadataBasis.OPEN_FOOD_FACTS_PRODUCT_NAME
                        )
                    )
            )

        val result =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(saved, snapshot)

        assertTrue(result.hasStaleEntries)
        assertTrue(result.metadata.productDisplayNames.isEmpty())
        assertEquals(listOf(eggs), result.staleProductKeys)
    }

    @Test
    fun `old store label becomes stale when same logical store is reconfirmed to different exact scope`() {
        val oldScope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = "wikidata:Q483551",
                locationKey = "osm:node:12345",
                commerceChannelKey = "PHYSICAL_STORE"
            )
        val newPreference = storePreference("wikidata:Q100000", "osm:node:12345")
        val saved = state(emptyList(), listOf(newPreference))
        val snapshot =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                storeEntries =
                    listOf(
                        PracticalShoppingSavedStoreDisplayMetadataEntry(
                            storeKey = north,
                            scope = oldScope,
                            displayName = "Old Store Name",
                            basis = PracticalShoppingSavedDisplayMetadataBasis.OPENSTREETMAP_PLACE_NAME
                        )
                    )
            )

        val result =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(saved, snapshot)

        assertTrue(result.hasStaleEntries)
        assertTrue(result.metadata.storeDisplayNames.isEmpty())
        assertEquals(listOf(north), result.staleStoreKeys)
    }

    @Test
    fun `metadata for removed saved keys is stale and cannot manufacture rows`() {
        val productPreference = productPreference("036000291452")
        val productKey = requireNotNull(
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = productPreference.providerId,
                identity = productPreference.sourceIdentity
            )
        )
        val snapshot =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries =
                    listOf(
                        PracticalShoppingSavedProductDisplayMetadataEntry(
                            itemKey = eggs,
                            productKey = productKey,
                            displayName = "Example Eggs",
                            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                        )
                    )
            )

        val result =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = PracticalShoppingSavedExactPreferenceState.empty(),
                snapshot = snapshot
            )

        assertTrue(result.metadata.productDisplayNames.isEmpty())
        assertEquals(listOf(eggs), result.staleProductKeys)

        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = PracticalShoppingSavedExactPreferenceState.empty(),
                metadata = result.metadata
            )
        assertTrue(projection.state.productRows.isEmpty())
        assertEquals("No saved choices yet.", projection.state.emptyMessage)
    }

    @Test
    fun `same canonical GTIN can retain label across provider change because exact product key is unchanged`() {
        val oldPreference = productPreference("036000291452", providerId = EvidenceProviderId("provider-a"))
        val oldKey = requireNotNull(
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = oldPreference.providerId,
                identity = oldPreference.sourceIdentity
            )
        )
        val newPreference = productPreference("0036000291452", providerId = EvidenceProviderId("provider-b"))
        val saved = state(listOf(newPreference), emptyList())
        val snapshot =
            PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                productEntries =
                    listOf(
                        PracticalShoppingSavedProductDisplayMetadataEntry(
                            itemKey = eggs,
                            productKey = oldKey,
                            displayName = "Same Exact Product",
                            basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                        )
                    )
            )

        val result = PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(saved, snapshot)

        assertFalse(result.hasStaleEntries)
        assertEquals("Same Exact Product", result.metadata.productDisplayNames[eggs])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate product metadata keys fail closed`() {
        val preference = productPreference("036000291452")
        val key = requireNotNull(
            ProductionProductEvidenceKeyResolver.resolve(preference.providerId, preference.sourceIdentity)
        )

        PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
            productEntries =
                listOf(
                    PracticalShoppingSavedProductDisplayMetadataEntry(
                        eggs,
                        key,
                        "First",
                        PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                    ),
                    PracticalShoppingSavedProductDisplayMetadataEntry(
                        eggs,
                        key,
                        "Second",
                        PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                    )
                )
        )
    }

    private fun productPreference(
        gtin: String,
        providerId: EvidenceProviderId = provider
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = eggs,
            providerId = providerId,
            sourceIdentity = SourceProductIdentity(gtin = gtin)
        )

    private fun storePreference(
        merchantKey: String,
        locationKey: String
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = north,
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = merchantKey,
                    locationKey = locationKey,
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            providerId = EvidenceProviderId("openstreetmap")
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
}

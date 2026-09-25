package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceDisplayMetadataBindingIntegrationTest {

    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")

    @Test
    fun `adapter product binding follows matching saved identity and goes stale after reconfirmation`() {
        val firstRow = offRow("036000291452", "Example Eggs")
        val firstConfirmed = confirmProduct(offSuggestion(firstRow, "first-off"), "first-confirmed")
        val entry =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter
                    .openFoodFactsProductName(firstConfirmed, firstRow)
                    .entry
            )
        val firstSaved =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveProduct(firstConfirmed).preference
            )

        val firstBinding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = state(products = listOf(firstSaved)),
                snapshot =
                    PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                        productEntries = listOf(entry)
                    )
            )

        assertFalse(firstBinding.hasStaleEntries)
        assertEquals("Example Eggs", firstBinding.metadata.productDisplayNames[eggs])

        val replacementRow = offRow("012345678905", "Replacement Eggs")
        val replacementConfirmed =
            confirmProduct(offSuggestion(replacementRow, "replacement-off"), "replacement-confirmed")
        val replacementSaved =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveProduct(replacementConfirmed).preference
            )

        val staleBinding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = state(products = listOf(replacementSaved)),
                snapshot =
                    PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                        productEntries = listOf(entry)
                    )
            )

        assertTrue(staleBinding.hasStaleEntries)
        assertTrue(staleBinding.metadata.productDisplayNames.isEmpty())
        assertEquals(listOf(eggs), staleBinding.staleProductKeys)
    }

    @Test
    fun `adapter store binding follows matching exact scope and goes stale after scope changes`() {
        val firstIdentity = osmIdentity(elementId = 12345L, merchantId = "Q483551")
        val firstConfirmed = confirmStore(osmSuggestion(firstIdentity, "first-osm"), "first-confirmed")
        val entry =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter
                    .openStreetMapStoreName(
                        confirmedCandidate = firstConfirmed,
                        row =
                            OpenStreetMapPracticalShoppingStoreDisplayRecord(
                                identity = firstIdentity,
                                name = "North Market"
                            )
                    ).entry
            )
        val firstSaved =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveStore(firstConfirmed).preference
            )

        val firstBinding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = state(stores = listOf(firstSaved)),
                snapshot =
                    PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                        storeEntries = listOf(entry)
                    )
            )

        assertFalse(firstBinding.hasStaleEntries)
        assertEquals("North Market", firstBinding.metadata.storeDisplayNames[north])

        val replacementIdentity = osmIdentity(elementId = 12345L, merchantId = "Q100000")
        val replacementConfirmed =
            confirmStore(osmSuggestion(replacementIdentity, "replacement-osm"), "replacement-confirmed")
        val replacementSaved =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveStore(replacementConfirmed).preference
            )

        val staleBinding =
            PracticalShoppingSavedExactPreferenceDisplayMetadataBinder.bind(
                savedState = state(stores = listOf(replacementSaved)),
                snapshot =
                    PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
                        storeEntries = listOf(entry)
                    )
            )

        assertTrue(staleBinding.hasStaleEntries)
        assertTrue(staleBinding.metadata.storeDisplayNames.isEmpty())
        assertEquals(listOf(north), staleBinding.staleStoreKeys)
    }

    private fun offRow(
        code: String,
        name: String
    ): OpenFoodFactsImportedProduct =
        OpenFoodFactsImportedProduct(
            code = code,
            productName = name,
            brands = "Example Brand",
            rawQuantity = null,
            productQuantity = null,
            productQuantityUnit = null,
            lastModifiedEpochSeconds = null
        )

    private fun offSuggestion(
        row: OpenFoodFactsImportedProduct,
        candidateId: String
    ) =
        requireNotNull(
            OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                itemKey = eggs,
                row = row,
                candidateId = candidateId
            ).candidate
        )

    private fun confirmProduct(
        candidate: com.valuepilot.core.PracticalShoppingProductIdentityCandidate,
        candidateId: String
    ) =
        requireNotNull(
            PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                itemKey = eggs,
                selectedCandidate = candidate,
                candidateId = candidateId
            ).candidate
        )

    private fun osmIdentity(
        elementId: Long,
        merchantId: String
    ): OpenStreetMapPracticalShoppingStoreRecord =
        OpenStreetMapPracticalShoppingStoreRecord(
            elementType = OpenStreetMapElementType.NODE,
            elementId = elementId,
            brandWikidataId = merchantId
        )

    private fun osmSuggestion(
        identity: OpenStreetMapPracticalShoppingStoreRecord,
        candidateId: String
    ) =
        requireNotNull(
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = north,
                row = identity,
                candidateId = candidateId
            ).candidate
        )

    private fun confirmStore(
        candidate: com.valuepilot.core.PracticalShoppingStoreIdentityCandidate,
        candidateId: String
    ) =
        requireNotNull(
            PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                storeKey = north,
                selectedCandidate = candidate,
                candidateId = candidateId
            ).candidate
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
}

package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceDisplayMetadataAdapterTest {

    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")

    @Test
    fun `user product and store labels require the already confirmed exact candidates`() {
        val productSuggestion = offSuggestion(offRow("036000291452", "Example Eggs"))
        val confirmedProduct = confirmProduct(productSuggestion)
        val storeSuggestion = osmSuggestion(elementId = 12345L)
        val confirmedStore = confirmStore(storeSuggestion)

        val productAccepted =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.userProductLabel(
                confirmedCandidate = confirmedProduct,
                displayName = "My usual eggs"
            )
        val productRejected =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.userProductLabel(
                confirmedCandidate = productSuggestion,
                displayName = "My usual eggs"
            )
        val storeAccepted =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.userStoreLabel(
                confirmedCandidate = confirmedStore,
                displayName = "My grocery store"
            )
        val storeRejected =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.userStoreLabel(
                confirmedCandidate = storeSuggestion,
                displayName = "My grocery store"
            )

        assertTrue(productAccepted.accepted)
        assertEquals(eggs, requireNotNull(productAccepted.entry).itemKey)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED,
            productAccepted.entry?.basis
        )
        assertFalse(productRejected.accepted)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.PRODUCT_NOT_USER_CONFIRMED),
            productRejected.failures
        )

        assertTrue(storeAccepted.accepted)
        assertEquals(north, requireNotNull(storeAccepted.entry).storeKey)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED,
            storeAccepted.entry?.basis
        )
        assertFalse(storeRejected.accepted)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.STORE_NOT_USER_CONFIRMED),
            storeRejected.failures
        )
    }

    @Test
    fun `Open Food Facts name is accepted only for the same confirmed product and provenance`() {
        val row = offRow("036000291452", "Example Eggs")
        val confirmed = confirmProduct(offSuggestion(row))

        val accepted =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openFoodFactsProductName(
                confirmedCandidate = confirmed,
                row = row
            )

        assertTrue(accepted.accepted)
        assertEquals("Example Eggs", requireNotNull(accepted.entry).displayName)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataBasis.OPEN_FOOD_FACTS_PRODUCT_NAME,
            accepted.entry?.basis
        )

        val wrongProduct =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openFoodFactsProductName(
                confirmedCandidate = confirmed,
                row = offRow("012345678905", "Different Product")
            )
        assertFalse(wrongProduct.accepted)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.PRODUCT_IDENTITY_MISMATCH),
            wrongProduct.failures
        )

        val wrongProvider = confirmed.copy(providerId = EvidenceProviderId("different-provider"))
        val wrongProvenance =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openFoodFactsProductName(
                confirmedCandidate = wrongProvider,
                row = row
            )
        assertFalse(wrongProvenance.accepted)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.SOURCE_PROVENANCE_MISMATCH),
            wrongProvenance.failures
        )
    }

    @Test
    fun `Open Food Facts display name does not depend on package quantity evidence`() {
        val row = offRow("036000291452", "Quantity Unknown Eggs")
        val confirmed = confirmProduct(offSuggestion(row))

        val result =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openFoodFactsProductName(
                confirmedCandidate = confirmed,
                row = row
            )

        assertTrue(result.accepted)
        assertEquals("Quantity Unknown Eggs", requireNotNull(result.entry).displayName)
    }

    @Test
    fun `OpenStreetMap name is accepted only for the same confirmed full source scope`() {
        val sourceIdentity = osmIdentity(elementId = 12345L)
        val confirmed = confirmStore(osmSuggestion(sourceIdentity))

        val accepted =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openStreetMapStoreName(
                confirmedCandidate = confirmed,
                row =
                    OpenStreetMapPracticalShoppingStoreDisplayRecord(
                        identity = sourceIdentity,
                        name = "North Market"
                    )
            )

        assertTrue(accepted.accepted)
        assertEquals("North Market", requireNotNull(accepted.entry).displayName)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataBasis.OPENSTREETMAP_PLACE_NAME,
            accepted.entry?.basis
        )

        val wrongElement =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openStreetMapStoreName(
                confirmedCandidate = confirmed,
                row =
                    OpenStreetMapPracticalShoppingStoreDisplayRecord(
                        identity = osmIdentity(elementId = 99999L),
                        name = "Other Market"
                    )
            )
        assertFalse(wrongElement.accepted)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.STORE_IDENTITY_MISMATCH),
            wrongElement.failures
        )

        val wrongMerchant =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openStreetMapStoreName(
                confirmedCandidate = confirmed,
                row =
                    OpenStreetMapPracticalShoppingStoreDisplayRecord(
                        identity =
                            OpenStreetMapPracticalShoppingStoreRecord(
                                elementType = OpenStreetMapElementType.NODE,
                                elementId = 12345L,
                                brandWikidataId = "Q100000"
                            ),
                        name = "Different Banner"
                    )
            )
        assertFalse(wrongMerchant.accepted)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.STORE_IDENTITY_MISMATCH),
            wrongMerchant.failures
        )

        val wrongProvider = confirmed.copy(providerId = EvidenceProviderId("different-provider"))
        val wrongProvenance =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openStreetMapStoreName(
                confirmedCandidate = wrongProvider,
                row =
                    OpenStreetMapPracticalShoppingStoreDisplayRecord(
                        identity = sourceIdentity,
                        name = "North Market"
                    )
            )
        assertFalse(wrongProvenance.accepted)
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.SOURCE_PROVENANCE_MISMATCH),
            wrongProvenance.failures
        )
    }

    @Test
    fun `missing malformed or oversized names fail closed without changing identity`() {
        val productConfirmed = confirmProduct(offSuggestion(offRow("036000291452", "Example Eggs")))
        val storeIdentity = osmIdentity(elementId = 12345L)
        val storeConfirmed = confirmStore(osmSuggestion(storeIdentity))

        val blankProduct =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openFoodFactsProductName(
                confirmedCandidate = productConfirmed,
                row = offRow("036000291452", "   ")
            )
        val controlStore =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.openStreetMapStoreName(
                confirmedCandidate = storeConfirmed,
                row =
                    OpenStreetMapPracticalShoppingStoreDisplayRecord(
                        identity = storeIdentity,
                        name = "North\nMarket"
                    )
            )
        val oversizedUser =
            PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter.userProductLabel(
                confirmedCandidate = productConfirmed,
                displayName = "X".repeat(161)
            )

        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE),
            blankProduct.failures
        )
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE),
            controlStore.failures
        )
        assertEquals(
            setOf(PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE),
            oversizedUser.failures
        )
    }

    @Test
    fun `source bound labels feed the verified projector without becoming identity authority`() {
        val productRow = offRow("036000291452", "Example Eggs")
        val productConfirmed = confirmProduct(offSuggestion(productRow))
        val storeIdentity = osmIdentity(elementId = 12345L)
        val storeConfirmed = confirmStore(osmSuggestion(storeIdentity))

        val productEntry =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter
                    .openFoodFactsProductName(productConfirmed, productRow)
                    .entry
            )
        val storeEntry =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter
                    .openStreetMapStoreName(
                        confirmedCandidate = storeConfirmed,
                        row =
                            OpenStreetMapPracticalShoppingStoreDisplayRecord(
                                identity = storeIdentity,
                                name = "North Market"
                            )
                    ).entry
            )

        val savedProduct =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveProduct(productConfirmed).preference
            )
        val savedStore =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveStore(storeConfirmed).preference
            )
        val savedState =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager.load(
                    PracticalShoppingSavedExactPreferenceDocument(
                        schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                        productPreferences = listOf(savedProduct),
                        storePreferences = listOf(savedStore)
                    )
                ).state
            )

        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = savedState,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(productEntry.itemKey to productEntry.displayName),
                        storeDisplayNames = mapOf(storeEntry.storeKey to storeEntry.displayName)
                    )
            )

        assertEquals("Example Eggs", projection.state.productRows.single().title)
        assertEquals("North Market", projection.state.storeRows.single().title)
        assertEquals(0, projection.state.unresolvedDisplayNameCount)
    }

    @Test
    fun `technically shaped source name is still blocked by downstream projector`() {
        val row = offRow("036000291452", "036000291452")
        val confirmed = confirmProduct(offSuggestion(row))
        val entry =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter
                    .openFoodFactsProductName(confirmed, row)
                    .entry
            )
        val savedPreference =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveProduct(confirmed).preference
            )
        val state =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceStateManager.load(
                    PracticalShoppingSavedExactPreferenceDocument(
                        schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                        productPreferences = listOf(savedPreference),
                        storePreferences = emptyList()
                    )
                ).state
            )

        val projection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = state,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(entry.itemKey to entry.displayName)
                    )
            )

        assertTrue(projection.state.productRows.isEmpty())
        assertEquals(1, projection.state.unresolvedDisplayNameCount)
        assertEquals(listOf(eggs), projection.unresolvedProductKeys)
    }

    private fun offRow(
        code: String,
        productName: String?
    ): OpenFoodFactsImportedProduct =
        OpenFoodFactsImportedProduct(
            code = code,
            productName = productName,
            brands = "Example Brand",
            rawQuantity = null,
            productQuantity = null,
            productQuantityUnit = null,
            lastModifiedEpochSeconds = null
        )

    private fun offSuggestion(
        row: OpenFoodFactsImportedProduct
    ): PracticalShoppingProductIdentityCandidate =
        requireNotNull(
            OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                itemKey = eggs,
                row = row,
                candidateId = "off-eggs"
            ).candidate
        )

    private fun confirmProduct(
        selected: PracticalShoppingProductIdentityCandidate
    ): PracticalShoppingProductIdentityCandidate =
        requireNotNull(
            PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                itemKey = eggs,
                selectedCandidate = selected,
                candidateId = "confirmed-eggs"
            ).candidate
        )

    private fun osmIdentity(elementId: Long): OpenStreetMapPracticalShoppingStoreRecord =
        OpenStreetMapPracticalShoppingStoreRecord(
            elementType = OpenStreetMapElementType.NODE,
            elementId = elementId,
            brandWikidataId = "Q483551"
        )

    private fun osmSuggestion(elementId: Long): PracticalShoppingStoreIdentityCandidate =
        osmSuggestion(osmIdentity(elementId))

    private fun osmSuggestion(
        identity: OpenStreetMapPracticalShoppingStoreRecord
    ): PracticalShoppingStoreIdentityCandidate =
        requireNotNull(
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = north,
                row = identity,
                candidateId = "osm-north-${identity.elementId}"
            ).candidate
        )

    private fun confirmStore(
        selected: PracticalShoppingStoreIdentityCandidate
    ): PracticalShoppingStoreIdentityCandidate =
        requireNotNull(
            PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                storeKey = north,
                selectedCandidate = selected,
                candidateId = "confirmed-north"
            ).candidate
        )
}

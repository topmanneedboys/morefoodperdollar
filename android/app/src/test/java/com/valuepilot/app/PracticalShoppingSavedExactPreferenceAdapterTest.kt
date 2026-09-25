package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIdentityResolutionStatus
import com.valuepilot.core.PracticalShoppingProductIdentityResolver
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityResolutionStatus
import com.valuepilot.core.PracticalShoppingStoreIdentityResolver
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExactPreferenceAdapterTest {

    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")

    @Test
    fun `explicitly confirmed product can be saved and restored into the existing resolver`() {
        val suggestion =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = eggs,
                    row = offRow("036000291452"),
                    candidateId = "off-eggs"
                ).candidate
            )
        val confirmed =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                    itemKey = eggs,
                    selectedCandidate = suggestion,
                    candidateId = "confirmed-eggs"
                ).candidate
            )

        val saved = PracticalShoppingSavedExactPreferenceAdapter.saveProduct(confirmed)

        assertTrue(saved.accepted)
        val preference = requireNotNull(saved.preference)
        assertEquals(eggs, preference.itemKey)
        assertEquals(suggestion.providerId, preference.providerId)
        assertEquals(suggestion.sourceIdentity, preference.sourceIdentity)
        assertEquals(suggestion.dataset, preference.dataset)

        val restored =
            PracticalShoppingSavedExactPreferenceAdapter.restoreProduct(
                preference = preference,
                candidateId = "restored-eggs"
            )

        assertEquals(
            PracticalShoppingProductIntentRelationship.SAVED_EXACT_PREFERENCE,
            restored.relationship
        )
        assertEquals(eggs, restored.itemKey)
        assertEquals(suggestion.providerId, restored.providerId)
        assertEquals(suggestion.sourceIdentity, restored.sourceIdentity)
        assertEquals(suggestion.dataset, restored.dataset)

        val resolution =
            PracticalShoppingProductIdentityResolver.resolve(
                request = ShoppingRequest(listOf(eggs)),
                candidates = listOf(restored)
            )

        assertEquals(
            PracticalShoppingProductIdentityResolutionStatus.AUTO_BINDABLE,
            resolution.itemResolutions.single().status
        )
        assertEquals("gtin:0036000291452", resolution.automaticBindings[eggs]?.value)
    }

    @Test
    fun `catalog suggestion cannot be saved without explicit product confirmation`() {
        val suggestion =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = eggs,
                    row = offRow("036000291452"),
                    candidateId = "catalog-only"
                ).candidate
            )

        val result = PracticalShoppingSavedExactPreferenceAdapter.saveProduct(suggestion)

        assertFalse(result.accepted)
        assertNull(result.preference)
        assertEquals(
            setOf(PracticalShoppingSavedExactPreferenceFailure.PRODUCT_NOT_USER_CONFIRMED),
            result.failures
        )
    }

    @Test
    fun `one time exact barcode request is not silently remembered`() {
        val exactRequest =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.exactBarcodeRequest(
                    itemKey = eggs,
                    rawGtin = "036000291452",
                    candidateId = "barcode-request"
                ).candidate
            )

        val result = PracticalShoppingSavedExactPreferenceAdapter.saveProduct(exactRequest)

        assertFalse(result.accepted)
        assertNull(result.preference)
        assertEquals(
            setOf(PracticalShoppingSavedExactPreferenceFailure.PRODUCT_NOT_USER_CONFIRMED),
            result.failures
        )
    }

    @Test
    fun `explicitly confirmed OSM store can be saved and restored into the existing resolver`() {
        val suggestion =
            requireNotNull(
                OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                    storeKey = north,
                    row =
                        OpenStreetMapPracticalShoppingStoreRecord(
                            elementType = OpenStreetMapElementType.NODE,
                            elementId = 12345L,
                            brandWikidataId = "Q483551"
                        ),
                    candidateId = "osm-north"
                ).candidate
            )
        val confirmed =
            requireNotNull(
                PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                    storeKey = north,
                    selectedCandidate = suggestion,
                    candidateId = "confirmed-north"
                ).candidate
            )

        val saved = PracticalShoppingSavedExactPreferenceAdapter.saveStore(confirmed)

        assertTrue(saved.accepted)
        val preference = requireNotNull(saved.preference)
        assertEquals(north, preference.storeKey)
        assertEquals(suggestion.scope, preference.scope)
        assertEquals(suggestion.providerId, preference.providerId)
        assertEquals(suggestion.dataset, preference.dataset)

        val restored =
            PracticalShoppingSavedExactPreferenceAdapter.restoreStore(
                preference = preference,
                candidateId = "restored-north"
            )

        assertEquals(PracticalShoppingStoreIdentityRelationship.SAVED_EXACT_STORE, restored.relationship)
        assertEquals(north, restored.storeKey)
        assertEquals(suggestion.scope, restored.scope)
        assertEquals(suggestion.providerId, restored.providerId)
        assertEquals(suggestion.dataset, restored.dataset)

        val resolution =
            PracticalShoppingStoreIdentityResolver.resolve(
                storeKeys = listOf(north),
                candidates = listOf(restored)
            )

        assertEquals(
            PracticalShoppingStoreIdentityResolutionStatus.AUTO_BINDABLE,
            resolution.storeResolutions.single().status
        )
        assertEquals(suggestion.scope, resolution.automaticScopes[north])
    }

    @Test
    fun `OSM store suggestion cannot be saved without explicit store confirmation`() {
        val suggestion =
            requireNotNull(
                OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                    storeKey = north,
                    row =
                        OpenStreetMapPracticalShoppingStoreRecord(
                            elementType = OpenStreetMapElementType.WAY,
                            elementId = 9876L,
                            operatorWikidataId = "Q100000"
                        ),
                    candidateId = "osm-only"
                ).candidate
            )

        val result = PracticalShoppingSavedExactPreferenceAdapter.saveStore(suggestion)

        assertFalse(result.accepted)
        assertNull(result.preference)
        assertEquals(
            setOf(PracticalShoppingSavedExactPreferenceFailure.STORE_NOT_USER_CONFIRMED),
            result.failures
        )
    }

    @Test
    fun `restoration retains the same stable keys instead of rematching text`() {
        val productSuggestion =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = eggs,
                    row = offRow("036000291452"),
                    candidateId = "off"
                ).candidate
            )
        val confirmedProduct =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                    itemKey = eggs,
                    selectedCandidate = productSuggestion,
                    candidateId = "confirmed"
                ).candidate
            )
        val productPreference =
            requireNotNull(
                PracticalShoppingSavedExactPreferenceAdapter.saveProduct(confirmedProduct).preference
            )

        val restoredProduct =
            PracticalShoppingSavedExactPreferenceAdapter.restoreProduct(
                preference = productPreference,
                candidateId = "new-candidate-id"
            )

        assertEquals(eggs, restoredProduct.itemKey)
        assertEquals("new-candidate-id", restoredProduct.candidateId)
        assertEquals(productSuggestion.sourceIdentity, restoredProduct.sourceIdentity)
    }

    private fun offRow(code: String): OpenFoodFactsImportedProduct =
        OpenFoodFactsImportedProduct(
            code = code,
            productName = "Example product",
            brands = "Example brand",
            rawQuantity = null,
            productQuantity = null,
            productQuantityUnit = null,
            lastModifiedEpochSeconds = null
        )
}

package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIdentityResolutionStatus
import com.valuepilot.core.PracticalShoppingProductIdentityResolver
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingExactProductConfirmationAdapterTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")

    @Test
    fun `checksum valid barcode exact request becomes automatic exact product binding`() {
        val result =
            PracticalShoppingExactProductConfirmationAdapter.exactBarcodeRequest(
                itemKey = eggs,
                rawGtin = "036000291452",
                candidateId = "scan-eggs"
            )

        assertTrue(result.accepted)
        val candidate = requireNotNull(result.candidate)
        assertEquals(PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST, candidate.relationship)
        assertEquals("036000291452", candidate.sourceIdentity.gtin)
        assertNull(candidate.dataset)

        val resolution =
            PracticalShoppingProductIdentityResolver.resolve(
                request = ShoppingRequest(listOf(eggs)),
                candidates = listOf(candidate)
            )

        assertEquals("gtin:0036000291452", resolution.automaticBindings[eggs]?.value)
        assertEquals(
            PracticalShoppingProductIdentityResolutionStatus.AUTO_BINDABLE,
            resolution.itemResolutions.single().status
        )
    }

    @Test
    fun `invalid scanned barcode is rejected instead of repaired`() {
        val result =
            PracticalShoppingExactProductConfirmationAdapter.exactBarcodeRequest(
                itemKey = eggs,
                rawGtin = "036000291453",
                candidateId = "bad-scan"
            )

        assertFalse(result.accepted)
        assertNull(result.candidate)
        assertEquals(
            setOf(PracticalShoppingExactProductConfirmationFailure.INVALID_GTIN),
            result.failures
        )
    }

    @Test
    fun `explicit selection upgrades Open Food Facts suggestion intent while preserving provenance`() {
        val suggestion =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = eggs,
                    row =
                        OpenFoodFactsImportedProduct(
                            code = "036000291452",
                            productName = "Example catalog product",
                            brands = "Example brand",
                            rawQuantity = null,
                            productQuantity = null,
                            productQuantityUnit = null,
                            lastModifiedEpochSeconds = null
                        ),
                    candidateId = "off-suggestion"
                ).candidate
            )

        val result =
            PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                itemKey = eggs,
                selectedCandidate = suggestion,
                candidateId = "confirmed-eggs"
            )

        assertTrue(result.accepted)
        val confirmed = requireNotNull(result.candidate)
        assertEquals(
            PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT,
            confirmed.relationship
        )
        assertEquals(suggestion.providerId, confirmed.providerId)
        assertEquals(suggestion.sourceIdentity, confirmed.sourceIdentity)
        assertEquals(suggestion.dataset, confirmed.dataset)

        val resolution =
            PracticalShoppingProductIdentityResolver.resolve(
                request = ShoppingRequest(listOf(eggs)),
                candidates = listOf(confirmed)
            )
        assertEquals("gtin:0036000291452", resolution.automaticBindings[eggs]?.value)
    }

    @Test
    fun `selection for a different shopping item fails closed`() {
        val suggestion =
            PracticalShoppingProductIdentityCandidate(
                candidateId = "milk-candidate",
                itemKey = milk,
                providerId = EvidenceProviderId("catalog"),
                sourceIdentity = SourceProductIdentity(gtin = "036000291452"),
                relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION
            )

        val result =
            PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                itemKey = eggs,
                selectedCandidate = suggestion,
                candidateId = "wrong-item"
            )

        assertFalse(result.accepted)
        assertEquals(
            setOf(PracticalShoppingExactProductConfirmationFailure.ITEM_MISMATCH),
            result.failures
        )
    }

    @Test
    fun `selection whose source identity cannot produce a production key fails closed`() {
        val invalidIdentityCandidate =
            PracticalShoppingProductIdentityCandidate(
                candidateId = "invalid-identity",
                itemKey = eggs,
                providerId = EvidenceProviderId("catalog"),
                sourceIdentity = SourceProductIdentity(gtin = "036000291453"),
                relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION
            )

        val result =
            PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                itemKey = eggs,
                selectedCandidate = invalidIdentityCandidate,
                candidateId = "confirmed-invalid"
            )

        assertFalse(result.accepted)
        assertEquals(
            setOf(PracticalShoppingExactProductConfirmationFailure.PRODUCT_IDENTITY_UNAVAILABLE),
            result.failures
        )
    }

    @Test
    fun `explicit selection may confirm provider scoped exact identity without inventing GTIN`() {
        val suggestion =
            PracticalShoppingProductIdentityCandidate(
                candidateId = "provider-item",
                itemKey = eggs,
                providerId = EvidenceProviderId("merchant-catalog"),
                sourceIdentity = SourceProductIdentity(providerItemId = "item-123"),
                relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION
            )

        val confirmed =
            requireNotNull(
                PracticalShoppingExactProductConfirmationAdapter.confirmSelection(
                    itemKey = eggs,
                    selectedCandidate = suggestion,
                    candidateId = "confirmed-provider-item"
                ).candidate
            )

        assertNull(confirmed.sourceIdentity.gtin)
        val resolution =
            PracticalShoppingProductIdentityResolver.resolve(
                request = ShoppingRequest(listOf(eggs)),
                candidates = listOf(confirmed)
            )

        val key = requireNotNull(resolution.automaticBindings[eggs])
        assertTrue(key.value.startsWith("provider:"))
    }
}

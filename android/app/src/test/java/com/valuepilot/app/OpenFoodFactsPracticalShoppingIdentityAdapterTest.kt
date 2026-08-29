package com.valuepilot.app

import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.PracticalShoppingProductIdentityResolutionStatus
import com.valuepilot.core.PracticalShoppingProductIdentityResolver
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsPracticalShoppingIdentityAdapterTest {

    private val eggs = ShoppingItemKey("eggs")

    @Test
    fun `valid catalog GTIN maps to source isolated suggestion even when quantity is unknown`() {
        val result =
            OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                itemKey = eggs,
                row = row(code = "036000291452", productQuantity = null, productQuantityUnit = null),
                candidateId = "off-eggs"
            )

        assertTrue(result.accepted)
        val candidate = requireNotNull(result.candidate)
        assertEquals(PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION, candidate.relationship)
        assertEquals("036000291452", candidate.sourceIdentity.gtin)
        assertEquals("open-food-facts", candidate.providerId.value)
        assertEquals("open-food-facts-products", candidate.dataset?.id)
        assertEquals("ODbL-1.0", candidate.dataset?.licenseId)
        assertEquals(EvidenceStorageBoundary.OPEN_SHARE_ALIKE, candidate.dataset?.storageBoundary)
    }

    @Test
    fun `catalog suggestion never becomes automatic shopping intent binding`() {
        val candidate =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = eggs,
                    row = row(code = "036000291452", productQuantity = "12", productQuantityUnit = "g"),
                    candidateId = "catalog-only"
                ).candidate
            )

        val resolution =
            PracticalShoppingProductIdentityResolver.resolve(
                request = ShoppingRequest(listOf(eggs)),
                candidates = listOf(candidate)
            )

        assertTrue(resolution.automaticBindings.isEmpty())
        val item = resolution.itemResolutions.single()
        assertEquals(PracticalShoppingProductIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION, item.status)
        assertNull(item.selectedProductKey)
        assertEquals(listOf("catalog-only"), item.suggestionCandidateIds)
    }

    @Test
    fun `equivalent valid barcode representations resolve to same canonical suggestion identity`() {
        val upc =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = eggs,
                    row = row(code = "036000291452"),
                    candidateId = "upc"
                ).candidate
            )
        val gtin13 =
            requireNotNull(
                OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                    itemKey = eggs,
                    row = row(code = "0036000291452"),
                    candidateId = "gtin13"
                ).candidate
            )

        val resolution =
            PracticalShoppingProductIdentityResolver.resolve(
                request = ShoppingRequest(listOf(eggs)),
                candidates = listOf(upc, gtin13)
            )

        val keys = resolution.candidateEvaluations.map { it.productKey?.value }.toSet()
        assertEquals(setOf("gtin:0036000291452"), keys)
        assertTrue(resolution.automaticBindings.isEmpty())
        assertEquals(
            PracticalShoppingProductIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
            resolution.itemResolutions.single().status
        )
    }

    @Test
    fun `invalid GTIN is rejected instead of repaired`() {
        val result =
            OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                itemKey = eggs,
                row = row(code = "036000291453"),
                candidateId = "bad"
            )

        assertFalse(result.accepted)
        assertNull(result.candidate)
        assertEquals(
            setOf(OpenFoodFactsPracticalShoppingIdentityFailure.INVALID_GTIN),
            result.failures
        )
    }

    private fun row(
        code: String,
        productQuantity: String? = null,
        productQuantityUnit: String? = null
    ): OpenFoodFactsImportedProduct =
        OpenFoodFactsImportedProduct(
            code = code,
            productName = "Example catalog product",
            brands = "Example brand",
            rawQuantity = null,
            productQuantity = productQuantity,
            productQuantityUnit = productQuantityUnit,
            lastModifiedEpochSeconds = null
        )
}

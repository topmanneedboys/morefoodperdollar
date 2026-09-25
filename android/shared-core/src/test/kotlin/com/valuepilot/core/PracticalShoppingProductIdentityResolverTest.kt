package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductIdentityResolverTest {

    private val eggs = ShoppingItemKey("eggs")
    private val milk = ShoppingItemKey("milk")
    private val request = ShoppingRequest(listOf(eggs, milk))

    @Test
    fun `exact product request with valid gtin becomes canonical automatic binding`() {
        val candidate =
            candidate(
                id = "barcode",
                item = eggs,
                provider = "device-barcode",
                gtin = "036000291452",
                relationship = PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(candidate))

        val resolution = result.itemResolutions.first { it.itemKey == eggs }
        assertEquals(PracticalShoppingProductIdentityResolutionStatus.AUTO_BINDABLE, resolution.status)
        assertEquals("gtin:0036000291452", resolution.selectedProductKey?.value)
        assertEquals(ProductionProductKeyScope.CROSS_SOURCE_GTIN, resolution.selectedProductKey?.scope)
        assertEquals(listOf("barcode"), resolution.supportingCandidateIds)
        assertEquals(resolution.selectedProductKey, result.automaticBindings[eggs])
    }

    @Test
    fun `catalog suggestion with valid gtin never silently becomes exact shopping binding`() {
        val openFoodFactsNamespace =
            EvidenceDatasetNamespace(
                id = "open-food-facts",
                displayName = "Open Food Facts",
                licenseId = "odbl-1.0",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        val candidate =
            candidate(
                id = "off-suggestion",
                item = milk,
                provider = "open-food-facts",
                gtin = "036000291452",
                relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION,
                dataset = openFoodFactsNamespace
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(candidate))

        val resolution = result.itemResolutions.first { it.itemKey == milk }
        assertEquals(
            PracticalShoppingProductIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
            resolution.status
        )
        assertNull(resolution.selectedProductKey)
        assertTrue(result.automaticBindings.isEmpty())
        assertEquals(listOf("off-suggestion"), resolution.suggestionCandidateIds)
        assertSame(openFoodFactsNamespace, result.candidateEvaluations.single().candidate.dataset)
    }

    @Test
    fun `equivalent exact gtin representations agree on one cross source binding`() {
        val upc =
            candidate(
                id = "upc",
                item = eggs,
                provider = "provider-a",
                gtin = "036000291452",
                relationship = PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST
            )
        val gtin13 =
            candidate(
                id = "gtin13",
                item = eggs,
                provider = "provider-b",
                gtin = "0036000291452",
                relationship = PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(upc, gtin13))

        val resolution = result.itemResolutions.first { it.itemKey == eggs }
        assertEquals(PracticalShoppingProductIdentityResolutionStatus.AUTO_BINDABLE, resolution.status)
        assertEquals("gtin:0036000291452", resolution.selectedProductKey?.value)
        assertEquals(listOf("gtin13", "upc"), resolution.supportingCandidateIds)
    }

    @Test
    fun `conflicting explicit exact identities require selection instead of arbitrary winner`() {
        val first =
            candidate(
                id = "first",
                item = eggs,
                provider = "provider-a",
                providerItemId = "item-a",
                relationship = PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST
            )
        val second =
            candidate(
                id = "second",
                item = eggs,
                provider = "provider-b",
                providerItemId = "item-b",
                relationship = PracticalShoppingProductIntentRelationship.SAVED_EXACT_PREFERENCE
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(first, second))

        val resolution = result.itemResolutions.first { it.itemKey == eggs }
        assertEquals(
            PracticalShoppingProductIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
            resolution.status
        )
        assertNull(resolution.selectedProductKey)
        assertEquals(listOf("first", "second"), resolution.supportingCandidateIds)
        assertFalse(eggs in result.automaticBindings)
    }

    @Test
    fun `explicit exact identity wins while unrelated suggestions remain non authoritative`() {
        val exact =
            candidate(
                id = "confirmed",
                item = milk,
                provider = "user-choice",
                providerItemId = "exact-milk",
                relationship = PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
            )
        val suggestion =
            candidate(
                id = "semantic",
                item = milk,
                provider = "local-model",
                providerItemId = "different-milk",
                relationship = PracticalShoppingProductIntentRelationship.SEMANTIC_SUGGESTION
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(suggestion, exact))

        val resolution = result.itemResolutions.first { it.itemKey == milk }
        assertEquals(PracticalShoppingProductIdentityResolutionStatus.AUTO_BINDABLE, resolution.status)
        assertEquals(listOf("confirmed"), resolution.supportingCandidateIds)
        assertEquals(listOf("semantic"), resolution.suggestionCandidateIds)
        assertTrue(requireNotNull(resolution.selectedProductKey).value.contains("exact-milk"))
    }

    @Test
    fun `invalid gtin only candidate remains unresolved and is never repaired`() {
        val invalid =
            candidate(
                id = "invalid-gtin",
                item = eggs,
                provider = "catalog",
                gtin = "036000291453",
                relationship = PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(invalid))

        val evaluation = result.candidateEvaluations.single()
        assertFalse(evaluation.usable)
        assertNull(evaluation.productKey)
        assertEquals(
            setOf(PracticalShoppingProductIdentityCandidateBlocker.PRODUCT_KEY_UNAVAILABLE),
            evaluation.blockers
        )
        assertEquals(
            PracticalShoppingProductIdentityResolutionStatus.UNRESOLVED,
            result.itemResolutions.first { it.itemKey == eggs }.status
        )
    }

    @Test
    fun `invalid supplied gtin may fall back only to real provider scoped identifier`() {
        val candidate =
            candidate(
                id = "fallback",
                item = eggs,
                provider = "provider-a",
                providerItemId = "source-item-42",
                gtin = "036000291453",
                relationship = PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(candidate))

        val selected = requireNotNull(result.automaticBindings[eggs])
        assertEquals(ProductionProductKeyScope.PROVIDER_ITEM, selected.scope)
        assertTrue(selected.value.contains("source-item-42"))
        assertFalse(selected.value.startsWith("gtin:"))
    }

    @Test
    fun `candidate outside shopping request is blocked without contaminating requested item state`() {
        val bread = ShoppingItemKey("bread")
        val candidate =
            candidate(
                id = "outside",
                item = bread,
                provider = "provider",
                providerItemId = "bread-1",
                relationship = PracticalShoppingProductIntentRelationship.EXACT_PRODUCT_REQUEST
            )

        val result = PracticalShoppingProductIdentityResolver.resolve(request, listOf(candidate))

        assertEquals(
            setOf(PracticalShoppingProductIdentityCandidateBlocker.ITEM_NOT_REQUESTED),
            result.candidateEvaluations.single().blockers
        )
        assertTrue(result.itemResolutions.all {
            it.status == PracticalShoppingProductIdentityResolutionStatus.UNRESOLVED
        })
    }

    @Test
    fun `duplicate candidate ids fail closed`() {
        val first =
            candidate(
                id = "duplicate",
                item = eggs,
                provider = "provider-a",
                providerItemId = "a",
                relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION
            )
        val second =
            candidate(
                id = "duplicate",
                item = milk,
                provider = "provider-b",
                providerItemId = "b",
                relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION
            )

        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingProductIdentityResolver.resolve(request, listOf(first, second))
        }
    }

    @Test
    fun `per item candidate set is bounded`() {
        val candidates =
            (0..32).map { index ->
                candidate(
                    id = "candidate-$index",
                    item = eggs,
                    provider = "provider-$index",
                    providerItemId = "item-$index",
                    relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION
                )
            }

        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingProductIdentityResolver.resolve(request, candidates)
        }
    }

    private fun candidate(
        id: String,
        item: ShoppingItemKey,
        provider: String,
        providerItemId: String? = null,
        gtin: String? = null,
        relationship: PracticalShoppingProductIntentRelationship,
        dataset: EvidenceDatasetNamespace? = null
    ): PracticalShoppingProductIdentityCandidate =
        PracticalShoppingProductIdentityCandidate(
            candidateId = id,
            itemKey = item,
            providerId = EvidenceProviderId(provider),
            sourceIdentity =
                SourceProductIdentity(
                    providerItemId = providerItemId,
                    gtin = gtin
                ),
            relationship = relationship,
            dataset = dataset
        )
}

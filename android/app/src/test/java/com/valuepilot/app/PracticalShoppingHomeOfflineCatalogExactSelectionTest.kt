package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.OfflineCatalogDiscoveryMatch
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.OfflineCatalogMatchKind
import com.valuepilot.core.OfflineCatalogProduct
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.SourceProductIdentity
import com.valuepilot.core.ShoppingItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeOfflineCatalogExactSelectionTest {

    @Test
    fun `selected catalog identity is source revalidated before becoming a remember request`() {
        val result = discovery(product("eggs", "Example Eggs", gtin = "036000291452"))

        val selected =
            PracticalShoppingHomeOfflineCatalogExactSelection.confirm(
                itemKey = ShoppingItemKey("sample-eggs-large-12"),
                result = result,
                matchIndex = 0,
                presentationGeneration = 4L,
                confirmedCandidateId = "home-confirmed-4-1"
            )

        val choice = requireNotNull(selected.selection)
        assertNull(selected.issue)
        assertEquals(
            PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT,
            choice.confirmedCandidate.relationship
        )
        assertEquals(ShoppingItemKey("sample-eggs-large-12"), choice.confirmedCandidate.itemKey)
        assertEquals(choice.confirmedCandidate, choice.rememberRequest.confirmedCandidate)
        assertEquals("Example Eggs", choice.rememberRequest.row.productName)
        assertEquals("0036000291452", choice.rememberRequest.row.code)
    }

    @Test
    fun `missing source gtin stays blocked instead of manufacturing an exact identity`() {
        val selected =
            PracticalShoppingHomeOfflineCatalogExactSelection.confirm(
                itemKey = ShoppingItemKey("sample-eggs-large-12"),
                result = discovery(product("eggs", "Example Eggs")),
                matchIndex = 0,
                presentationGeneration = 5L,
                confirmedCandidateId = "home-confirmed-5-1"
            )

        assertNull(selected.selection)
        assertEquals(
            PracticalShoppingHomeOfflineCatalogExactSelectionIssue.SOURCE_IDENTITY_UNAVAILABLE,
            selected.issue
        )
    }

    @Test
    fun `stale or unknown match index cannot select a catalog product`() {
        val selected =
            PracticalShoppingHomeOfflineCatalogExactSelection.confirm(
                itemKey = ShoppingItemKey("sample-eggs-large-12"),
                result = discovery(product("eggs", "Example Eggs", gtin = "036000291452")),
                matchIndex = 1,
                presentationGeneration = 6L,
                confirmedCandidateId = "home-confirmed-6-1"
            )

        assertNull(selected.selection)
        assertEquals(
            PracticalShoppingHomeOfflineCatalogExactSelectionIssue.STALE_OR_UNKNOWN_MATCH,
            selected.issue
        )
    }

    @Test
    fun `unsafe display label remains blocked by the existing exact choice projector`() {
        val selected =
            PracticalShoppingHomeOfflineCatalogExactSelection.confirm(
                itemKey = ShoppingItemKey("sample-eggs-large-12"),
                result = discovery(
                    product("eggs", "Example Eggs 0036000291452", gtin = "036000291452")
                ),
                matchIndex = 0,
                presentationGeneration = 7L,
                confirmedCandidateId = "home-confirmed-7-1"
            )

        assertNull(selected.selection)
        assertEquals(
            PracticalShoppingHomeOfflineCatalogExactSelectionIssue.CONFIRMATION_REJECTED,
            selected.issue
        )
        assertTrue(selected.confirmationFailures.isNotEmpty())
    }

    private fun discovery(product: OfflineCatalogProduct): OfflineCatalogDiscoveryResult =
        OfflineCatalogDiscoveryResult(
            normalizedQuery = "eggs",
            evaluatedCandidateCount = 1,
            matches = listOf(OfflineCatalogDiscoveryMatch(product, OfflineCatalogMatchKind.EXACT_NAME))
        )

    private fun product(
        id: String,
        name: String,
        gtin: String? = null
    ): OfflineCatalogProduct =
        OfflineCatalogProduct(
            recordId = id,
            providerId = EvidenceProviderId("open-food-facts"),
            dataset =
                EvidenceDatasetNamespace(
                    id = "off-ca",
                    displayName = "Open Food Facts Canada",
                    licenseId = "ODbL-1.0",
                    storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
                ),
            sourceIdentity = SourceProductIdentity(providerItemId = id, gtin = gtin),
            displayName = name,
            canonicalSearchName = JvmTextCanonicalizer.search(name)
        )
}

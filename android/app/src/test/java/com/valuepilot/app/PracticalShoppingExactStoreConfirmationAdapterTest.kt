package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityResolutionStatus
import com.valuepilot.core.PracticalShoppingStoreIdentityResolver
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingExactStoreConfirmationAdapterTest {

    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun `explicit user choice upgrades OSM suggestion while preserving exact scope and provenance`() {
        val osmResult =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = north,
                row =
                    OpenStreetMapPracticalShoppingStoreRecord(
                        elementType = OpenStreetMapElementType.NODE,
                        elementId = 12345L,
                        brandWikidataId = "Q483551"
                    ),
                candidateId = "osm-north"
            )
        val suggestion = requireNotNull(osmResult.candidate)

        val confirmation =
            PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                storeKey = north,
                selectedCandidate = suggestion,
                candidateId = "confirmed-north"
            )

        assertTrue(confirmation.accepted)
        val confirmed = requireNotNull(confirmation.candidate)
        assertEquals(suggestion.scope, confirmed.scope)
        assertEquals(suggestion.providerId, confirmed.providerId)
        assertEquals(suggestion.dataset, confirmed.dataset)

        val resolution =
            PracticalShoppingStoreIdentityResolver.resolve(
                storeKeys = listOf(north),
                candidates = listOf(confirmed)
            )

        assertEquals(suggestion.scope, resolution.automaticScopes[north])
        assertEquals(
            PracticalShoppingStoreIdentityResolutionStatus.AUTO_BINDABLE,
            resolution.storeResolutions.single().status
        )
    }

    @Test
    fun `confirmation cannot retarget one store suggestion to a different logical store`() {
        val suggestion =
            requireNotNull(
                OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                    storeKey = west,
                    row =
                        OpenStreetMapPracticalShoppingStoreRecord(
                            elementType = OpenStreetMapElementType.WAY,
                            elementId = 999L,
                            operatorWikidataId = "Q100000"
                        ),
                    candidateId = "osm-west"
                ).candidate
            )

        val result =
            PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                storeKey = north,
                selectedCandidate = suggestion,
                candidateId = "wrong-store"
            )

        assertFalse(result.accepted)
        assertNull(result.candidate)
        assertEquals(
            setOf(PracticalShoppingExactStoreConfirmationFailure.STORE_MISMATCH),
            result.failures
        )
    }

    @Test
    fun `confirming store identity does not create travel or price facts`() {
        val suggestion =
            requireNotNull(
                OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                    storeKey = north,
                    row =
                        OpenStreetMapPracticalShoppingStoreRecord(
                            elementType = OpenStreetMapElementType.RELATION,
                            elementId = 777L,
                            brandWikidataId = "Q483551"
                        ),
                    candidateId = "osm-store"
                ).candidate
            )

        val confirmed =
            requireNotNull(
                PracticalShoppingExactStoreConfirmationAdapter.confirmSelection(
                    storeKey = north,
                    selectedCandidate = suggestion,
                    candidateId = "confirmed-store"
                ).candidate
            )

        assertEquals("wikidata:Q483551", confirmed.scope.merchantKey)
        assertEquals("osm:relation:777", confirmed.scope.locationKey)
        assertEquals("PHYSICAL_STORE", confirmed.scope.commerceChannelKey)
    }
}

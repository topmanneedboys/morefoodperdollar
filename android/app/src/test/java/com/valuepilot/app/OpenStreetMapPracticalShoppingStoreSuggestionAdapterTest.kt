package com.valuepilot.app

import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityResolutionStatus
import com.valuepilot.core.PracticalShoppingStoreIdentityResolver
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenStreetMapPracticalShoppingStoreSuggestionAdapterTest {

    private val store = ShoppingStoreKey("candidate-store")

    @Test
    fun `explicit OSM brand identity maps to source isolated location suggestion only`() {
        val result =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = store,
                row =
                    OpenStreetMapPracticalShoppingStoreRecord(
                        elementType = OpenStreetMapElementType.NODE,
                        elementId = 12345L,
                        brandWikidataId = "Q483551"
                    ),
                candidateId = "osm-store"
            )

        assertTrue(result.accepted)
        assertEquals(OpenStreetMapMerchantIdentityBasis.BRAND_WIKIDATA, result.merchantIdentityBasis)
        val candidate = requireNotNull(result.candidate)
        assertEquals(
            PracticalShoppingStoreIdentityRelationship.SOURCE_LOCATION_SUGGESTION,
            candidate.relationship
        )
        assertEquals("wikidata:Q483551", candidate.scope.merchantKey)
        assertEquals("osm:node:12345", candidate.scope.locationKey)
        assertEquals("PHYSICAL_STORE", candidate.scope.commerceChannelKey)
        assertEquals("openstreetmap", candidate.providerId?.value)
        assertEquals("openstreetmap-places", candidate.dataset?.id)
        assertEquals("ODbL-1.0", candidate.dataset?.licenseId)
        assertEquals(EvidenceStorageBoundary.OPEN_SHARE_ALIKE, candidate.dataset?.storageBoundary)

        val resolution =
            PracticalShoppingStoreIdentityResolver.resolve(
                storeKeys = listOf(store),
                candidates = listOf(candidate)
            )

        assertTrue(resolution.automaticScopes.isEmpty())
        val storeResolution = resolution.storeResolutions.single()
        assertEquals(
            PracticalShoppingStoreIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
            storeResolution.status
        )
        assertNull(storeResolution.selectedScope)
        assertEquals(listOf("osm-store"), storeResolution.suggestionCandidateIds)
    }

    @Test
    fun `matching brand and operator identity preserve corroboration basis without upgrading authority`() {
        val result =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = store,
                row =
                    OpenStreetMapPracticalShoppingStoreRecord(
                        elementType = OpenStreetMapElementType.WAY,
                        elementId = 9876L,
                        brandWikidataId = "Q483551",
                        operatorWikidataId = "Q483551"
                    ),
                candidateId = "same-brand-operator"
            )

        assertEquals(
            OpenStreetMapMerchantIdentityBasis.BRAND_AND_OPERATOR_AGREE,
            result.merchantIdentityBasis
        )
        val candidate = requireNotNull(result.candidate)
        assertEquals("wikidata:Q483551", candidate.scope.merchantKey)
        assertEquals("osm:way:9876", candidate.scope.locationKey)
        assertEquals(
            PracticalShoppingStoreIdentityRelationship.SOURCE_LOCATION_SUGGESTION,
            candidate.relationship
        )
    }

    @Test
    fun `operator only identity remains explicitly operator based`() {
        val result =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = store,
                row =
                    OpenStreetMapPracticalShoppingStoreRecord(
                        elementType = OpenStreetMapElementType.NODE,
                        elementId = 321L,
                        operatorWikidataId = "Q100000"
                    ),
                candidateId = "operator-only"
            )

        assertTrue(result.accepted)
        assertEquals(
            OpenStreetMapMerchantIdentityBasis.OPERATOR_WIKIDATA,
            result.merchantIdentityBasis
        )
        assertEquals("wikidata:Q100000", requireNotNull(result.candidate).scope.merchantKey)
    }

    @Test
    fun `conflicting explicit brand and operator identities fail instead of choosing one`() {
        val result =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = store,
                row =
                    OpenStreetMapPracticalShoppingStoreRecord(
                        elementType = OpenStreetMapElementType.RELATION,
                        elementId = 555L,
                        brandWikidataId = "Q483551",
                        operatorWikidataId = "Q100000"
                    ),
                candidateId = "conflict"
            )

        assertFalse(result.accepted)
        assertNull(result.candidate)
        assertNull(result.merchantIdentityBasis)
        assertEquals(
            setOf(OpenStreetMapPracticalShoppingStoreFailure.AMBIGUOUS_MERCHANT_IDENTITY),
            result.failures
        )
    }

    @Test
    fun `missing explicit merchant identity is not inferred from place identity`() {
        val result =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = store,
                row =
                    OpenStreetMapPracticalShoppingStoreRecord(
                        elementType = OpenStreetMapElementType.NODE,
                        elementId = 123L
                    ),
                candidateId = "no-merchant"
            )

        assertFalse(result.accepted)
        assertEquals(
            setOf(OpenStreetMapPracticalShoppingStoreFailure.MERCHANT_IDENTITY_UNAVAILABLE),
            result.failures
        )
    }

    @Test
    fun `invalid Wikidata identifier is rejected instead of normalized`() {
        val result =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = store,
                row =
                    OpenStreetMapPracticalShoppingStoreRecord(
                        elementType = OpenStreetMapElementType.NODE,
                        elementId = 123L,
                        brandWikidataId = "q483551"
                    ),
                candidateId = "bad-qid"
            )

        assertFalse(result.accepted)
        assertEquals(
            setOf(OpenStreetMapPracticalShoppingStoreFailure.INVALID_WIKIDATA_ID),
            result.failures
        )
    }
}

package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingStoreIdentityResolverTest {

    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")
    private val stores = listOf(north, west)

    @Test
    fun `source asserted exact scope with isolated provenance becomes automatic binding`() {
        val dataset = dataset("merchant-feed")
        val scope = scope("merchant-a", "location-101", "PICKUP")
        val candidate =
            candidate(
                id = "feed-store",
                store = north,
                scope = scope,
                relationship = PracticalShoppingStoreIdentityRelationship.SOURCE_ASSERTED_EXACT_OFFER_SCOPE,
                provider = "merchant-feed-provider",
                dataset = dataset
            )

        val result = PracticalShoppingStoreIdentityResolver.resolve(stores, listOf(candidate))

        val resolution = result.storeResolutions.first { it.storeKey == north }
        assertEquals(PracticalShoppingStoreIdentityResolutionStatus.AUTO_BINDABLE, resolution.status)
        assertEquals(scope, resolution.selectedScope)
        assertEquals(listOf("feed-store"), resolution.supportingCandidateIds)
        assertEquals(scope, result.automaticScopes[north])
        assertSame(dataset, result.candidateEvaluations.single().candidate.dataset)
    }

    @Test
    fun `geocoder or name suggestion never becomes exact retailer offer scope automatically`() {
        val proposedScope = scope("merchant-a", "osm-node-42", "PHYSICAL_STORE")
        val candidate =
            candidate(
                id = "geo-suggestion",
                store = north,
                scope = proposedScope,
                relationship = PracticalShoppingStoreIdentityRelationship.NAME_OR_GEO_SUGGESTION,
                provider = "openstreetmap",
                dataset = dataset("openstreetmap")
            )

        val result = PracticalShoppingStoreIdentityResolver.resolve(stores, listOf(candidate))

        val resolution = result.storeResolutions.first { it.storeKey == north }
        assertEquals(
            PracticalShoppingStoreIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
            resolution.status
        )
        assertNull(resolution.selectedScope)
        assertEquals(listOf("geo-suggestion"), resolution.suggestionCandidateIds)
        assertFalse(north in result.automaticScopes)
    }

    @Test
    fun `multiple exact sources may corroborate the same complete store scope`() {
        val sharedScope = scope("merchant-a", "store-22", "PHYSICAL_STORE")
        val first =
            candidate(
                id = "first",
                store = north,
                scope = sharedScope,
                relationship = PracticalShoppingStoreIdentityRelationship.SOURCE_ASSERTED_EXACT_OFFER_SCOPE,
                provider = "provider-a",
                dataset = dataset("dataset-a")
            )
        val second =
            candidate(
                id = "second",
                store = north,
                scope = sharedScope,
                relationship = PracticalShoppingStoreIdentityRelationship.SOURCE_ASSERTED_EXACT_OFFER_SCOPE,
                provider = "provider-b",
                dataset = dataset("dataset-b")
            )

        val result = PracticalShoppingStoreIdentityResolver.resolve(stores, listOf(second, first))

        val resolution = result.storeResolutions.first { it.storeKey == north }
        assertEquals(PracticalShoppingStoreIdentityResolutionStatus.AUTO_BINDABLE, resolution.status)
        assertEquals(sharedScope, resolution.selectedScope)
        assertEquals(listOf("first", "second"), resolution.supportingCandidateIds)
    }

    @Test
    fun `conflicting exact store scopes require selection instead of source preference`() {
        val first =
            candidate(
                id = "first",
                store = north,
                scope = scope("merchant-a", "location-a", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
            )
        val second =
            candidate(
                id = "second",
                store = north,
                scope = scope("merchant-a", "location-b", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.SAVED_EXACT_STORE
            )

        val result = PracticalShoppingStoreIdentityResolver.resolve(stores, listOf(first, second))

        val resolution = result.storeResolutions.first { it.storeKey == north }
        assertEquals(
            PracticalShoppingStoreIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
            resolution.status
        )
        assertNull(resolution.selectedScope)
        assertEquals(listOf("first", "second"), resolution.supportingCandidateIds)
    }

    @Test
    fun `one explicit exact scope is not displaced by unrelated location suggestions`() {
        val exactScope = scope("merchant-a", "location-a", "PHYSICAL_STORE")
        val exact =
            candidate(
                id = "confirmed",
                store = north,
                scope = exactScope,
                relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
            )
        val suggestion =
            candidate(
                id = "nearby",
                store = north,
                scope = scope("merchant-a", "location-nearby", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.SOURCE_LOCATION_SUGGESTION,
                provider = "location-source",
                dataset = dataset("location-source")
            )

        val result = PracticalShoppingStoreIdentityResolver.resolve(stores, listOf(suggestion, exact))

        val resolution = result.storeResolutions.first { it.storeKey == north }
        assertEquals(PracticalShoppingStoreIdentityResolutionStatus.AUTO_BINDABLE, resolution.status)
        assertEquals(exactScope, resolution.selectedScope)
        assertEquals(listOf("confirmed"), resolution.supportingCandidateIds)
        assertEquals(listOf("nearby"), resolution.suggestionCandidateIds)
    }

    @Test
    fun `source asserted exact scope without provider and dataset provenance fails construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            candidate(
                id = "unproven",
                store = north,
                scope = scope("merchant-a", "location-a", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.SOURCE_ASSERTED_EXACT_OFFER_SCOPE
            )
        }
    }

    @Test
    fun `dataset backed suggestion without provider provenance fails construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingStoreIdentityCandidate(
                candidateId = "missing-provider",
                storeKey = north,
                scope = scope("merchant-a", "location-a", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.NAME_OR_GEO_SUGGESTION,
                providerId = null,
                dataset = dataset("dataset-only")
            )
        }
    }

    @Test
    fun `candidate outside requested store set is blocked without contaminating requested stores`() {
        val outside = ShoppingStoreKey("outside")
        val candidate =
            candidate(
                id = "outside-candidate",
                store = outside,
                scope = scope("merchant-x", "location-x", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
            )

        val result = PracticalShoppingStoreIdentityResolver.resolve(stores, listOf(candidate))

        assertEquals(
            setOf(PracticalShoppingStoreIdentityCandidateBlocker.STORE_NOT_REQUESTED),
            result.candidateEvaluations.single().blockers
        )
        assertTrue(result.storeResolutions.all {
            it.status == PracticalShoppingStoreIdentityResolutionStatus.UNRESOLVED
        })
    }

    @Test
    fun `duplicate candidate ids fail closed`() {
        val first =
            candidate(
                id = "duplicate",
                store = north,
                scope = scope("merchant-a", "location-a", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.NAME_OR_GEO_SUGGESTION
            )
        val second =
            candidate(
                id = "duplicate",
                store = west,
                scope = scope("merchant-b", "location-b", "PHYSICAL_STORE"),
                relationship = PracticalShoppingStoreIdentityRelationship.NAME_OR_GEO_SUGGESTION
            )

        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingStoreIdentityResolver.resolve(stores, listOf(first, second))
        }
    }

    @Test
    fun `per store candidate set is bounded`() {
        val candidates =
            (0..32).map { index ->
                candidate(
                    id = "candidate-$index",
                    store = north,
                    scope = scope("merchant-$index", "location-$index", "PHYSICAL_STORE"),
                    relationship = PracticalShoppingStoreIdentityRelationship.NAME_OR_GEO_SUGGESTION
                )
            }

        assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingStoreIdentityResolver.resolve(stores, candidates)
        }
    }

    private fun scope(
        merchant: String,
        location: String?,
        channel: String
    ): PracticalShoppingStoreIdentityScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = merchant,
            locationKey = location,
            commerceChannelKey = channel
        )

    private fun candidate(
        id: String,
        store: ShoppingStoreKey,
        scope: PracticalShoppingStoreIdentityScope,
        relationship: PracticalShoppingStoreIdentityRelationship,
        provider: String? = null,
        dataset: EvidenceDatasetNamespace? = null
    ): PracticalShoppingStoreIdentityCandidate =
        PracticalShoppingStoreIdentityCandidate(
            candidateId = id,
            storeKey = store,
            scope = scope,
            relationship = relationship,
            providerId = provider?.let(::EvidenceProviderId),
            dataset = dataset
        )

    private fun dataset(id: String): EvidenceDatasetNamespace =
        EvidenceDatasetNamespace(
            id = id,
            displayName = id,
            licenseId = "reviewed-license",
            storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
        )
}

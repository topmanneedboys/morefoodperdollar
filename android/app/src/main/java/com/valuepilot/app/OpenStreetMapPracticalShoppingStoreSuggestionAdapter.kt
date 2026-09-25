package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingStoreKey

enum class OpenStreetMapElementType {
    NODE,
    WAY,
    RELATION
}

data class OpenStreetMapPracticalShoppingStoreRecord(
    val elementType: OpenStreetMapElementType,
    val elementId: Long,
    val brandWikidataId: String? = null,
    val operatorWikidataId: String? = null
) {
    init {
        require(elementId > 0L)
        listOfNotNull(brandWikidataId, operatorWikidataId).forEach { value ->
            require(value.isNotBlank() && value.length <= 32)
        }
    }
}

enum class OpenStreetMapMerchantIdentityBasis {
    BRAND_WIKIDATA,
    OPERATOR_WIKIDATA,
    BRAND_AND_OPERATOR_AGREE
}

enum class OpenStreetMapPracticalShoppingStoreFailure {
    MERCHANT_IDENTITY_UNAVAILABLE,
    INVALID_WIKIDATA_ID,
    AMBIGUOUS_MERCHANT_IDENTITY
}

data class OpenStreetMapPracticalShoppingStoreResult(
    val candidate: PracticalShoppingStoreIdentityCandidate?,
    val merchantIdentityBasis: OpenStreetMapMerchantIdentityBasis?,
    val failures: Set<OpenStreetMapPracticalShoppingStoreFailure>
) {
    init {
        require((candidate != null) == failures.isEmpty())
        require((candidate != null) == (merchantIdentityBasis != null))
    }

    val accepted: Boolean
        get() = candidate != null
}

/**
 * Network-free OpenStreetMap -> Practical Shopping store-discovery boundary.
 *
 * This adapter intentionally does not use a place name, address text, coordinates,
 * distance, route result, or fuzzy retailer-name match to create merchant identity.
 * It accepts only explicit OSM brand/operator Wikidata identifiers and preserves the
 * exact OSM element as a source-specific physical-location key.
 *
 * Even then, the result is only SOURCE_LOCATION_SUGGESTION. OSM location data does
 * not prove the exact merchant/location/channel scope of a production price claim,
 * so this adapter can never create an automatically bindable store scope.
 */
object OpenStreetMapPracticalShoppingStoreSuggestionAdapter {

    private val WIKIDATA_ID = Regex("Q[1-9][0-9]{0,18}")
    private val PROVIDER_ID = EvidenceProviderId("openstreetmap")

    val DATASET_NAMESPACE =
        EvidenceDatasetNamespace(
            id = "openstreetmap-places",
            displayName = "OpenStreetMap places",
            licenseId = "ODbL-1.0",
            storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
        )

    fun locationSuggestion(
        storeKey: ShoppingStoreKey,
        row: OpenStreetMapPracticalShoppingStoreRecord,
        candidateId: String
    ): OpenStreetMapPracticalShoppingStoreResult {
        val brandId = row.brandWikidataId?.trim()
        val operatorId = row.operatorWikidataId?.trim()
        val suppliedMerchantIds = listOfNotNull(brandId, operatorId)

        if (suppliedMerchantIds.isEmpty()) {
            return failure(OpenStreetMapPracticalShoppingStoreFailure.MERCHANT_IDENTITY_UNAVAILABLE)
        }

        if (suppliedMerchantIds.any { !WIKIDATA_ID.matches(it) }) {
            return failure(OpenStreetMapPracticalShoppingStoreFailure.INVALID_WIKIDATA_ID)
        }

        val distinctMerchantIds = suppliedMerchantIds.toSet()
        if (distinctMerchantIds.size != 1) {
            return failure(OpenStreetMapPracticalShoppingStoreFailure.AMBIGUOUS_MERCHANT_IDENTITY)
        }

        val merchantWikidataId = distinctMerchantIds.single()
        val basis =
            when {
                brandId != null && operatorId != null ->
                    OpenStreetMapMerchantIdentityBasis.BRAND_AND_OPERATOR_AGREE
                brandId != null ->
                    OpenStreetMapMerchantIdentityBasis.BRAND_WIKIDATA
                else ->
                    OpenStreetMapMerchantIdentityBasis.OPERATOR_WIKIDATA
            }
        val locationKey = "osm:${row.elementType.name.lowercase()}:${row.elementId}"

        return OpenStreetMapPracticalShoppingStoreResult(
            candidate =
                PracticalShoppingStoreIdentityCandidate(
                    candidateId = candidateId,
                    storeKey = storeKey,
                    scope =
                        PracticalShoppingStoreIdentityScope(
                            merchantKey = "wikidata:$merchantWikidataId",
                            locationKey = locationKey,
                            commerceChannelKey = "PHYSICAL_STORE"
                        ),
                    relationship =
                        PracticalShoppingStoreIdentityRelationship.SOURCE_LOCATION_SUGGESTION,
                    providerId = PROVIDER_ID,
                    dataset = DATASET_NAMESPACE
                ),
            merchantIdentityBasis = basis,
            failures = emptySet()
        )
    }

    private fun failure(
        failure: OpenStreetMapPracticalShoppingStoreFailure
    ): OpenStreetMapPracticalShoppingStoreResult =
        OpenStreetMapPracticalShoppingStoreResult(
            candidate = null,
            merchantIdentityBasis = null,
            failures = setOf(failure)
        )
}

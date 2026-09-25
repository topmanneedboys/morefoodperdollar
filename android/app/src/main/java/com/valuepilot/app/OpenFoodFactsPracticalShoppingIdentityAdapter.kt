package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.GtinValidation
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity

enum class OpenFoodFactsPracticalShoppingIdentityFailure {
    INVALID_GTIN
}

data class OpenFoodFactsPracticalShoppingIdentityResult(
    val candidate: PracticalShoppingProductIdentityCandidate?,
    val failures: Set<OpenFoodFactsPracticalShoppingIdentityFailure>
) {
    init {
        require((candidate != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = candidate != null
}

/**
 * Network-free bridge from one already-decoded Open Food Facts catalog row into
 * the production Practical Shopping product-identity candidate boundary.
 *
 * This adapter intentionally uses only the source GTIN. Product name, brand,
 * quantity, image, description, nutrition and any search similarity score are
 * not identity inputs here. A checksum-valid catalog row becomes only a
 * [PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION]; Open Food
 * Facts does not prove that a broad shopping intent means this exact product.
 *
 * Package quantity is deliberately independent. A row with no usable package
 * quantity may still be a valid catalog identity suggestion, while quantity
 * evidence continues through [OpenFoodFactsImportedMetadataMapper] and its
 * stricter quantity rules.
 */
object OpenFoodFactsPracticalShoppingIdentityAdapter {

    val DATASET_NAMESPACE =
        EvidenceDatasetNamespace(
            id = "open-food-facts-products",
            displayName = "Open Food Facts products",
            licenseId = "ODbL-1.0",
            storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
        )

    fun catalogSuggestion(
        itemKey: ShoppingItemKey,
        row: OpenFoodFactsImportedProduct,
        candidateId: String
    ): OpenFoodFactsPracticalShoppingIdentityResult {
        val gtin = row.code.trim()
        if (!GtinValidation.isValid(gtin)) {
            return OpenFoodFactsPracticalShoppingIdentityResult(
                candidate = null,
                failures = setOf(OpenFoodFactsPracticalShoppingIdentityFailure.INVALID_GTIN)
            )
        }

        return OpenFoodFactsPracticalShoppingIdentityResult(
            candidate =
                PracticalShoppingProductIdentityCandidate(
                    candidateId = candidateId,
                    itemKey = itemKey,
                    providerId = EvidenceProviderId(OpenFoodFactsImportedMetadataMapper.PROVIDER_ID),
                    sourceIdentity = SourceProductIdentity(gtin = gtin),
                    relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION,
                    dataset = DATASET_NAMESPACE
                ),
            failures = emptySet()
        )
    }
}

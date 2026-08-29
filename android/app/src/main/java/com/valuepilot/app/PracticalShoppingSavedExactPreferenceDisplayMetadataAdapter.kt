package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

private const val MAX_SAVED_PRESENTATION_DISPLAY_NAME_LENGTH = 160
private const val MAX_OSM_DISPLAY_RECORD_NAME_INPUT_LENGTH = 500

/** Presentation-only provenance for a human-facing saved-choice label. */
enum class PracticalShoppingSavedDisplayMetadataBasis {
    USER_PROVIDED,
    OPEN_FOOD_FACTS_PRODUCT_NAME,
    OPENSTREETMAP_PLACE_NAME
}

data class PracticalShoppingSavedProductDisplayMetadataEntry(
    val itemKey: ShoppingItemKey,
    val displayName: String,
    val basis: PracticalShoppingSavedDisplayMetadataBasis
) {
    init {
        require(displayName.isNotBlank())
        require(displayName.length <= MAX_SAVED_PRESENTATION_DISPLAY_NAME_LENGTH)
    }
}

data class PracticalShoppingSavedStoreDisplayMetadataEntry(
    val storeKey: ShoppingStoreKey,
    val displayName: String,
    val basis: PracticalShoppingSavedDisplayMetadataBasis
) {
    init {
        require(displayName.isNotBlank())
        require(displayName.length <= MAX_SAVED_PRESENTATION_DISPLAY_NAME_LENGTH)
    }
}

enum class PracticalShoppingSavedDisplayMetadataFailure {
    PRODUCT_NOT_USER_CONFIRMED,
    STORE_NOT_USER_CONFIRMED,
    SOURCE_PROVENANCE_MISMATCH,
    PRODUCT_IDENTITY_MISMATCH,
    STORE_IDENTITY_MISMATCH,
    DISPLAY_NAME_UNAVAILABLE
}

data class PracticalShoppingSavedProductDisplayMetadataResult(
    val entry: PracticalShoppingSavedProductDisplayMetadataEntry?,
    val failures: Set<PracticalShoppingSavedDisplayMetadataFailure>
) {
    init {
        require((entry != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = entry != null
}

data class PracticalShoppingSavedStoreDisplayMetadataResult(
    val entry: PracticalShoppingSavedStoreDisplayMetadataEntry?,
    val failures: Set<PracticalShoppingSavedDisplayMetadataFailure>
) {
    init {
        require((entry != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = entry != null
}

/**
 * Separately decoded OpenStreetMap display metadata for one exact source place row.
 *
 * The identity record is re-run through the verified OSM store-suggestion adapter before
 * its name can be attached to a confirmed saved store. Name text itself remains purely
 * presentation metadata and cannot establish merchant/location/channel authority.
 */
data class OpenStreetMapPracticalShoppingStoreDisplayRecord(
    val identity: OpenStreetMapPracticalShoppingStoreRecord,
    val name: String?
) {
    init {
        require(name == null || name.length <= MAX_OSM_DISPLAY_RECORD_NAME_INPUT_LENGTH)
    }
}

/**
 * Confirmation-bound human-label adapter for Saved presentation.
 *
 * This boundary never creates or changes exact product/store identity. It only emits a
 * display-name entry after the exact candidate is already USER_CONFIRMED. Source-derived
 * names additionally re-run the verified source identity adapter and must resolve back to
 * the same confirmed product key or complete store scope/provenance.
 *
 * The downstream Saved UI projector still applies its independent leakage policy before
 * consumer rendering. A technically shaped label therefore remains unable to bypass the
 * presentation safety boundary even if supplied by the user/source.
 */
object PracticalShoppingSavedExactPreferenceDisplayMetadataAdapter {

    private const val VALIDATION_CANDIDATE_ID = "saved-display-metadata-validation"

    fun userProductLabel(
        confirmedCandidate: PracticalShoppingProductIdentityCandidate,
        displayName: String
    ): PracticalShoppingSavedProductDisplayMetadataResult {
        if (
            confirmedCandidate.relationship !=
            PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
        ) {
            return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.PRODUCT_NOT_USER_CONFIRMED
            )
        }

        val safeName = sanitizeDisplayName(displayName)
            ?: return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE
            )

        return PracticalShoppingSavedProductDisplayMetadataResult(
            entry =
                PracticalShoppingSavedProductDisplayMetadataEntry(
                    itemKey = confirmedCandidate.itemKey,
                    displayName = safeName,
                    basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                ),
            failures = emptySet()
        )
    }

    fun userStoreLabel(
        confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
        displayName: String
    ): PracticalShoppingSavedStoreDisplayMetadataResult {
        if (
            confirmedCandidate.relationship !=
            PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
        ) {
            return storeFailure(
                PracticalShoppingSavedDisplayMetadataFailure.STORE_NOT_USER_CONFIRMED
            )
        }

        val safeName = sanitizeDisplayName(displayName)
            ?: return storeFailure(
                PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE
            )

        return PracticalShoppingSavedStoreDisplayMetadataResult(
            entry =
                PracticalShoppingSavedStoreDisplayMetadataEntry(
                    storeKey = confirmedCandidate.storeKey,
                    displayName = safeName,
                    basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                ),
            failures = emptySet()
        )
    }

    fun openFoodFactsProductName(
        confirmedCandidate: PracticalShoppingProductIdentityCandidate,
        row: OpenFoodFactsImportedProduct
    ): PracticalShoppingSavedProductDisplayMetadataResult {
        if (
            confirmedCandidate.relationship !=
            PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
        ) {
            return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.PRODUCT_NOT_USER_CONFIRMED
            )
        }

        val sourceCandidate =
            OpenFoodFactsPracticalShoppingIdentityAdapter.catalogSuggestion(
                itemKey = confirmedCandidate.itemKey,
                row = row,
                candidateId = VALIDATION_CANDIDATE_ID
            ).candidate
            ?: return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.PRODUCT_IDENTITY_MISMATCH
            )

        if (
            confirmedCandidate.providerId != sourceCandidate.providerId ||
            confirmedCandidate.dataset != sourceCandidate.dataset
        ) {
            return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.SOURCE_PROVENANCE_MISMATCH
            )
        }

        val confirmedProductKey =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = confirmedCandidate.providerId,
                identity = confirmedCandidate.sourceIdentity
            )
        val sourceProductKey =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = sourceCandidate.providerId,
                identity = sourceCandidate.sourceIdentity
            )
        if (
            confirmedProductKey == null ||
            sourceProductKey == null ||
            confirmedProductKey != sourceProductKey
        ) {
            return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.PRODUCT_IDENTITY_MISMATCH
            )
        }

        val safeName = sanitizeDisplayName(row.productName)
            ?: return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE
            )

        return PracticalShoppingSavedProductDisplayMetadataResult(
            entry =
                PracticalShoppingSavedProductDisplayMetadataEntry(
                    itemKey = confirmedCandidate.itemKey,
                    displayName = safeName,
                    basis = PracticalShoppingSavedDisplayMetadataBasis.OPEN_FOOD_FACTS_PRODUCT_NAME
                ),
            failures = emptySet()
        )
    }

    fun openStreetMapStoreName(
        confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
        row: OpenStreetMapPracticalShoppingStoreDisplayRecord
    ): PracticalShoppingSavedStoreDisplayMetadataResult {
        if (
            confirmedCandidate.relationship !=
            PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
        ) {
            return storeFailure(
                PracticalShoppingSavedDisplayMetadataFailure.STORE_NOT_USER_CONFIRMED
            )
        }

        val sourceCandidate =
            OpenStreetMapPracticalShoppingStoreSuggestionAdapter.locationSuggestion(
                storeKey = confirmedCandidate.storeKey,
                row = row.identity,
                candidateId = VALIDATION_CANDIDATE_ID
            ).candidate
            ?: return storeFailure(
                PracticalShoppingSavedDisplayMetadataFailure.STORE_IDENTITY_MISMATCH
            )

        if (
            confirmedCandidate.providerId != sourceCandidate.providerId ||
            confirmedCandidate.dataset != sourceCandidate.dataset
        ) {
            return storeFailure(
                PracticalShoppingSavedDisplayMetadataFailure.SOURCE_PROVENANCE_MISMATCH
            )
        }

        if (confirmedCandidate.scope != sourceCandidate.scope) {
            return storeFailure(
                PracticalShoppingSavedDisplayMetadataFailure.STORE_IDENTITY_MISMATCH
            )
        }

        val safeName = sanitizeDisplayName(row.name)
            ?: return storeFailure(
                PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE
            )

        return PracticalShoppingSavedStoreDisplayMetadataResult(
            entry =
                PracticalShoppingSavedStoreDisplayMetadataEntry(
                    storeKey = confirmedCandidate.storeKey,
                    displayName = safeName,
                    basis = PracticalShoppingSavedDisplayMetadataBasis.OPENSTREETMAP_PLACE_NAME
                ),
            failures = emptySet()
        )
    }

    private fun sanitizeDisplayName(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (value.isBlank() || value.length > MAX_SAVED_PRESENTATION_DISPLAY_NAME_LENGTH) {
            return null
        }
        if (value.any { character -> Character.isISOControl(character.code) }) {
            return null
        }
        return value
    }

    private fun productFailure(
        failure: PracticalShoppingSavedDisplayMetadataFailure
    ): PracticalShoppingSavedProductDisplayMetadataResult =
        PracticalShoppingSavedProductDisplayMetadataResult(
            entry = null,
            failures = setOf(failure)
        )

    private fun storeFailure(
        failure: PracticalShoppingSavedDisplayMetadataFailure
    ): PracticalShoppingSavedStoreDisplayMetadataResult =
        PracticalShoppingSavedStoreDisplayMetadataResult(
            entry = null,
            failures = setOf(failure)
        )
}

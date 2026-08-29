package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

private const val MAX_SAVED_PRESENTATION_DISPLAY_NAME_LENGTH = 160
private const val MAX_OSM_DISPLAY_RECORD_NAME_INPUT_LENGTH = 500
private const val MAX_SAVED_PRODUCT_DISPLAY_ENTRIES = 128
private const val MAX_SAVED_STORE_DISPLAY_ENTRIES = 64

/** Presentation-only provenance for a human-facing saved-choice label. */
enum class PracticalShoppingSavedDisplayMetadataBasis {
    USER_PROVIDED,
    OPEN_FOOD_FACTS_PRODUCT_NAME,
    OPENSTREETMAP_PLACE_NAME
}

/**
 * Human-facing product label bound to the exact production product identity it described.
 * The binding is not product authority; it prevents a stale label from following a stable
 * ShoppingItemKey after that key is re-confirmed to a different exact product.
 */
data class PracticalShoppingSavedProductDisplayMetadataEntry(
    val itemKey: ShoppingItemKey,
    val productKey: ProductionProductEvidenceKey,
    val displayName: String,
    val basis: PracticalShoppingSavedDisplayMetadataBasis
) {
    init {
        require(displayName.isNotBlank())
        require(displayName.length <= MAX_SAVED_PRESENTATION_DISPLAY_NAME_LENGTH)
    }
}

/**
 * Human-facing store label bound to the exact merchant/location/channel scope it described.
 * The binding is presentation safety only and grants no price, route or offer authority.
 */
data class PracticalShoppingSavedStoreDisplayMetadataEntry(
    val storeKey: ShoppingStoreKey,
    val scope: PracticalShoppingStoreIdentityScope,
    val displayName: String,
    val basis: PracticalShoppingSavedDisplayMetadataBasis
) {
    init {
        require(displayName.isNotBlank())
        require(displayName.length <= MAX_SAVED_PRESENTATION_DISPLAY_NAME_LENGTH)
    }
}

/**
 * Bounded storage-neutral display metadata set. One entry per stable saved key.
 * A future local codec may persist this object without changing the exact-preference schema.
 */
data class PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
    val productEntries: List<PracticalShoppingSavedProductDisplayMetadataEntry> = emptyList(),
    val storeEntries: List<PracticalShoppingSavedStoreDisplayMetadataEntry> = emptyList()
) {
    init {
        require(productEntries.size <= MAX_SAVED_PRODUCT_DISPLAY_ENTRIES)
        require(storeEntries.size <= MAX_SAVED_STORE_DISPLAY_ENTRIES)
        require(productEntries.map { it.itemKey }.distinct().size == productEntries.size) {
            "Saved product display metadata keys must be unique"
        }
        require(storeEntries.map { it.storeKey }.distinct().size == storeEntries.size) {
            "Saved store display metadata keys must be unique"
        }
    }
}

data class PracticalShoppingSavedExactPreferenceDisplayMetadataBindingResult(
    val metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata,
    val staleProductKeys: List<ShoppingItemKey>,
    val staleStoreKeys: List<ShoppingStoreKey>
) {
    init {
        require(staleProductKeys.distinct().size == staleProductKeys.size)
        require(staleStoreKeys.distinct().size == staleStoreKeys.size)
    }

    val hasStaleEntries: Boolean
        get() = staleProductKeys.isNotEmpty() || staleStoreKeys.isNotEmpty()
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
 * Binds detached display metadata back to the current validated saved preference state.
 *
 * Entries whose logical key no longer exists or whose exact product/store binding changed
 * are omitted from the projector metadata and reported as stale. Thus a stale display name
 * can never relabel a newly confirmed exact choice merely because the stable logical key is
 * reused. Extra metadata still cannot manufacture a saved row.
 */
object PracticalShoppingSavedExactPreferenceDisplayMetadataBinder {

    fun bind(
        savedState: PracticalShoppingSavedExactPreferenceState,
        snapshot: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): PracticalShoppingSavedExactPreferenceDisplayMetadataBindingResult {
        val staleProducts = mutableListOf<ShoppingItemKey>()
        val staleStores = mutableListOf<ShoppingStoreKey>()
        val productNames = linkedMapOf<ShoppingItemKey, String>()
        val storeNames = linkedMapOf<ShoppingStoreKey, String>()

        snapshot.productEntries
            .sortedBy { it.itemKey.value }
            .forEach { entry ->
                val current = savedState.productFor(entry.itemKey)
                val currentKey =
                    current?.let { preference ->
                        ProductionProductEvidenceKeyResolver.resolve(
                            providerId = preference.providerId,
                            identity = preference.sourceIdentity
                        )
                    }
                if (currentKey == entry.productKey) {
                    productNames[entry.itemKey] = entry.displayName
                } else {
                    staleProducts += entry.itemKey
                }
            }

        snapshot.storeEntries
            .sortedBy { it.storeKey.value }
            .forEach { entry ->
                val current = savedState.storeFor(entry.storeKey)
                if (current?.scope == entry.scope) {
                    storeNames[entry.storeKey] = entry.displayName
                } else {
                    staleStores += entry.storeKey
                }
            }

        return PracticalShoppingSavedExactPreferenceDisplayMetadataBindingResult(
            metadata =
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    productDisplayNames = productNames,
                    storeDisplayNames = storeNames
                ),
            staleProductKeys = staleProducts,
            staleStoreKeys = staleStores
        )
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
 * Every emitted entry retains its exact product/store binding so detached or persisted
 * metadata can later be revalidated through [PracticalShoppingSavedExactPreferenceDisplayMetadataBinder].
 * The downstream Saved UI projector still independently applies leakage policy.
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

        val productKey =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = confirmedCandidate.providerId,
                identity = confirmedCandidate.sourceIdentity
            )
            ?: return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.PRODUCT_IDENTITY_MISMATCH
            )
        val safeName = sanitizeDisplayName(displayName)
            ?: return productFailure(
                PracticalShoppingSavedDisplayMetadataFailure.DISPLAY_NAME_UNAVAILABLE
            )

        return PracticalShoppingSavedProductDisplayMetadataResult(
            entry =
                PracticalShoppingSavedProductDisplayMetadataEntry(
                    itemKey = confirmedCandidate.itemKey,
                    productKey = productKey,
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
                    scope = confirmedCandidate.scope,
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
                    productKey = confirmedProductKey,
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
                    scope = confirmedCandidate.scope,
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

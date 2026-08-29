package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingProductIntentRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityRelationship
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity

/**
 * Stable, storage-neutral record for one explicitly confirmed exact product preference.
 *
 * Candidate ids are intentionally not persisted. A restored candidate gets a new
 * invocation-local id while retaining the same stable shopping item, source product
 * identity, provider and dataset provenance.
 */
data class PracticalShoppingSavedExactProductPreference(
    val itemKey: ShoppingItemKey,
    val providerId: EvidenceProviderId,
    val sourceIdentity: SourceProductIdentity,
    val dataset: EvidenceDatasetNamespace? = null
)

/**
 * Stable, storage-neutral record for one explicitly confirmed exact store preference.
 *
 * This stores only the already-confirmed merchant/location/channel scope and its source
 * provenance. It contains no store display name, coordinates, travel, price or ranking.
 */
data class PracticalShoppingSavedExactStorePreference(
    val storeKey: ShoppingStoreKey,
    val scope: PracticalShoppingStoreIdentityScope,
    val providerId: EvidenceProviderId? = null,
    val dataset: EvidenceDatasetNamespace? = null
) {
    init {
        require(dataset == null || providerId != null) {
            "Dataset-backed saved store preferences require provider provenance"
        }
    }
}

enum class PracticalShoppingSavedExactPreferenceFailure {
    PRODUCT_NOT_USER_CONFIRMED,
    STORE_NOT_USER_CONFIRMED
}

data class PracticalShoppingSavedExactProductPreferenceResult(
    val preference: PracticalShoppingSavedExactProductPreference?,
    val failures: Set<PracticalShoppingSavedExactPreferenceFailure>
) {
    init {
        require((preference != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = preference != null
}

data class PracticalShoppingSavedExactStorePreferenceResult(
    val preference: PracticalShoppingSavedExactStorePreference?,
    val failures: Set<PracticalShoppingSavedExactPreferenceFailure>
) {
    init {
        require((preference != null) == failures.isEmpty())
    }

    val accepted: Boolean
        get() = preference != null
}

/**
 * Network/filesystem-free boundary for remembering explicit exact identity choices.
 *
 * Saving is intentionally stricter than automatic binding. Only an already explicit
 * USER_CONFIRMED exact product/store candidate may become a saved preference. A catalog
 * suggestion, OSM/location suggestion, source-asserted scope, or one-time exact barcode
 * request is not remembered automatically.
 *
 * Restoration never searches or rematches text. It recreates a candidate for the same
 * stable ShoppingItemKey/ShoppingStoreKey and changes only the relationship to the
 * existing SAVED_EXACT_* relationship understood by shared-core resolvers.
 */
object PracticalShoppingSavedExactPreferenceAdapter {

    fun saveProduct(
        confirmedCandidate: PracticalShoppingProductIdentityCandidate
    ): PracticalShoppingSavedExactProductPreferenceResult {
        if (
            confirmedCandidate.relationship !=
            PracticalShoppingProductIntentRelationship.USER_CONFIRMED_EXACT_PRODUCT
        ) {
            return PracticalShoppingSavedExactProductPreferenceResult(
                preference = null,
                failures =
                    setOf(
                        PracticalShoppingSavedExactPreferenceFailure.PRODUCT_NOT_USER_CONFIRMED
                    )
            )
        }

        return PracticalShoppingSavedExactProductPreferenceResult(
            preference =
                PracticalShoppingSavedExactProductPreference(
                    itemKey = confirmedCandidate.itemKey,
                    providerId = confirmedCandidate.providerId,
                    sourceIdentity = confirmedCandidate.sourceIdentity,
                    dataset = confirmedCandidate.dataset
                ),
            failures = emptySet()
        )
    }

    fun restoreProduct(
        preference: PracticalShoppingSavedExactProductPreference,
        candidateId: String
    ): PracticalShoppingProductIdentityCandidate =
        PracticalShoppingProductIdentityCandidate(
            candidateId = candidateId,
            itemKey = preference.itemKey,
            providerId = preference.providerId,
            sourceIdentity = preference.sourceIdentity,
            relationship = PracticalShoppingProductIntentRelationship.SAVED_EXACT_PREFERENCE,
            dataset = preference.dataset
        )

    fun saveStore(
        confirmedCandidate: PracticalShoppingStoreIdentityCandidate
    ): PracticalShoppingSavedExactStorePreferenceResult {
        if (
            confirmedCandidate.relationship !=
            PracticalShoppingStoreIdentityRelationship.USER_CONFIRMED_EXACT_STORE
        ) {
            return PracticalShoppingSavedExactStorePreferenceResult(
                preference = null,
                failures =
                    setOf(
                        PracticalShoppingSavedExactPreferenceFailure.STORE_NOT_USER_CONFIRMED
                    )
            )
        }

        return PracticalShoppingSavedExactStorePreferenceResult(
            preference =
                PracticalShoppingSavedExactStorePreference(
                    storeKey = confirmedCandidate.storeKey,
                    scope = confirmedCandidate.scope,
                    providerId = confirmedCandidate.providerId,
                    dataset = confirmedCandidate.dataset
                ),
            failures = emptySet()
        )
    }

    fun restoreStore(
        preference: PracticalShoppingSavedExactStorePreference,
        candidateId: String
    ): PracticalShoppingStoreIdentityCandidate =
        PracticalShoppingStoreIdentityCandidate(
            candidateId = candidateId,
            storeKey = preference.storeKey,
            scope = preference.scope,
            relationship = PracticalShoppingStoreIdentityRelationship.SAVED_EXACT_STORE,
            providerId = preference.providerId,
            dataset = preference.dataset
        )
}

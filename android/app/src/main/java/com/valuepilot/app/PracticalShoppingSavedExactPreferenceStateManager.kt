package com.valuepilot.app

import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

private const val PRACTICAL_SHOPPING_SAVED_PREFERENCES_SCHEMA_VERSION = 1
private const val MAX_SAVED_EXACT_PRODUCT_PREFERENCES = 128
private const val MAX_SAVED_EXACT_STORE_PREFERENCES = 64

/**
 * Storage-format-neutral versioned document for saved exact preferences.
 *
 * This object is the data a future local storage adapter may encode. It does not
 * perform serialization, file/database access, Android storage, cloud sync, or I/O.
 */
data class PracticalShoppingSavedExactPreferenceDocument(
    val schemaVersion: Int,
    val productPreferences: List<PracticalShoppingSavedExactProductPreference>,
    val storePreferences: List<PracticalShoppingSavedExactStorePreference>
)

enum class PracticalShoppingSavedExactPreferenceLoadIssue {
    UNSUPPORTED_SCHEMA_VERSION,
    TOO_MANY_PRODUCT_PREFERENCES,
    TOO_MANY_STORE_PREFERENCES,
    DUPLICATE_PRODUCT_ITEM_KEY,
    DUPLICATE_STORE_KEY,
    PRODUCT_IDENTITY_UNAVAILABLE
}

/**
 * Validated immutable application state. Construction is restricted to this file so
 * callers cannot bypass schema, duplicate-key, or capacity validation accidentally.
 */
data class PracticalShoppingSavedExactPreferenceState internal constructor(
    val productPreferences: List<PracticalShoppingSavedExactProductPreference>,
    val storePreferences: List<PracticalShoppingSavedExactStorePreference>
) {
    init {
        require(productPreferences.size <= MAX_SAVED_EXACT_PRODUCT_PREFERENCES)
        require(storePreferences.size <= MAX_SAVED_EXACT_STORE_PREFERENCES)
        require(productPreferences.map { it.itemKey }.distinct().size == productPreferences.size)
        require(storePreferences.map { it.storeKey }.distinct().size == storePreferences.size)
    }

    fun productFor(itemKey: ShoppingItemKey): PracticalShoppingSavedExactProductPreference? =
        productPreferences.firstOrNull { it.itemKey == itemKey }

    fun storeFor(storeKey: ShoppingStoreKey): PracticalShoppingSavedExactStorePreference? =
        storePreferences.firstOrNull { it.storeKey == storeKey }

    companion object {
        fun empty(): PracticalShoppingSavedExactPreferenceState =
            PracticalShoppingSavedExactPreferenceState(
                productPreferences = emptyList(),
                storePreferences = emptyList()
            )
    }
}

data class PracticalShoppingSavedExactPreferenceLoadResult(
    val state: PracticalShoppingSavedExactPreferenceState?,
    val issues: Set<PracticalShoppingSavedExactPreferenceLoadIssue>
) {
    init {
        require((state != null) == issues.isEmpty())
    }

    val accepted: Boolean
        get() = state != null
}

enum class PracticalShoppingSavedExactPreferenceMutationIssue {
    PRODUCT_CAPACITY_REACHED,
    STORE_CAPACITY_REACHED
}

data class PracticalShoppingSavedExactPreferenceMutationResult(
    val state: PracticalShoppingSavedExactPreferenceState,
    val issue: PracticalShoppingSavedExactPreferenceMutationIssue? = null
) {
    val accepted: Boolean
        get() = issue == null
}

/**
 * Deterministic lifecycle contract for saved exact-preference state.
 *
 * The boundary deliberately owns no clock and no persistence technology. A future
 * storage adapter can decode bytes/rows into [PracticalShoppingSavedExactPreferenceDocument],
 * call [load], and persist [document] output. Unsupported versions and malformed key
 * multiplicity fail closed instead of being partially repaired.
 *
 * Product identities are re-resolved during load so storage corruption cannot turn an
 * invalid/unresolvable product identity into a saved automatic binding.
 *
 * Upsert semantics are explicit: the stable item/store key is the preference key.
 * Re-saving the same key replaces the old exact preference without growing the state.
 * Adding a new key beyond the bounded capacity is rejected without modifying state.
 */
object PracticalShoppingSavedExactPreferenceStateManager {

    val currentSchemaVersion: Int
        get() = PRACTICAL_SHOPPING_SAVED_PREFERENCES_SCHEMA_VERSION

    fun load(
        document: PracticalShoppingSavedExactPreferenceDocument
    ): PracticalShoppingSavedExactPreferenceLoadResult {
        val issues = linkedSetOf<PracticalShoppingSavedExactPreferenceLoadIssue>()

        if (document.schemaVersion != PRACTICAL_SHOPPING_SAVED_PREFERENCES_SCHEMA_VERSION) {
            issues += PracticalShoppingSavedExactPreferenceLoadIssue.UNSUPPORTED_SCHEMA_VERSION
        }
        if (document.productPreferences.size > MAX_SAVED_EXACT_PRODUCT_PREFERENCES) {
            issues += PracticalShoppingSavedExactPreferenceLoadIssue.TOO_MANY_PRODUCT_PREFERENCES
        }
        if (document.storePreferences.size > MAX_SAVED_EXACT_STORE_PREFERENCES) {
            issues += PracticalShoppingSavedExactPreferenceLoadIssue.TOO_MANY_STORE_PREFERENCES
        }
        if (
            document.productPreferences.map { it.itemKey }.distinct().size !=
            document.productPreferences.size
        ) {
            issues += PracticalShoppingSavedExactPreferenceLoadIssue.DUPLICATE_PRODUCT_ITEM_KEY
        }
        if (
            document.storePreferences.map { it.storeKey }.distinct().size !=
            document.storePreferences.size
        ) {
            issues += PracticalShoppingSavedExactPreferenceLoadIssue.DUPLICATE_STORE_KEY
        }
        if (
            document.productPreferences.any { preference ->
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = preference.providerId,
                    identity = preference.sourceIdentity
                ) == null
            }
        ) {
            issues += PracticalShoppingSavedExactPreferenceLoadIssue.PRODUCT_IDENTITY_UNAVAILABLE
        }

        if (issues.isNotEmpty()) {
            return PracticalShoppingSavedExactPreferenceLoadResult(
                state = null,
                issues = issues
            )
        }

        return PracticalShoppingSavedExactPreferenceLoadResult(
            state =
                PracticalShoppingSavedExactPreferenceState(
                    productPreferences = sortProducts(document.productPreferences),
                    storePreferences = sortStores(document.storePreferences)
                ),
            issues = emptySet()
        )
    }

    fun document(
        state: PracticalShoppingSavedExactPreferenceState
    ): PracticalShoppingSavedExactPreferenceDocument =
        PracticalShoppingSavedExactPreferenceDocument(
            schemaVersion = PRACTICAL_SHOPPING_SAVED_PREFERENCES_SCHEMA_VERSION,
            productPreferences = sortProducts(state.productPreferences),
            storePreferences = sortStores(state.storePreferences)
        )

    fun upsertProduct(
        state: PracticalShoppingSavedExactPreferenceState,
        preference: PracticalShoppingSavedExactProductPreference
    ): PracticalShoppingSavedExactPreferenceMutationResult {
        require(
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = preference.providerId,
                identity = preference.sourceIdentity
            ) != null
        ) {
            "Saved exact product preference must resolve to a production product identity"
        }

        val exists = state.productPreferences.any { it.itemKey == preference.itemKey }
        if (!exists && state.productPreferences.size >= MAX_SAVED_EXACT_PRODUCT_PREFERENCES) {
            return PracticalShoppingSavedExactPreferenceMutationResult(
                state = state,
                issue = PracticalShoppingSavedExactPreferenceMutationIssue.PRODUCT_CAPACITY_REACHED
            )
        }

        val updated =
            state.productPreferences
                .filterNot { it.itemKey == preference.itemKey } +
                preference

        return PracticalShoppingSavedExactPreferenceMutationResult(
            state =
                PracticalShoppingSavedExactPreferenceState(
                    productPreferences = sortProducts(updated),
                    storePreferences = state.storePreferences
                )
        )
    }

    fun removeProduct(
        state: PracticalShoppingSavedExactPreferenceState,
        itemKey: ShoppingItemKey
    ): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences =
                state.productPreferences.filterNot { it.itemKey == itemKey },
            storePreferences = state.storePreferences
        )

    fun upsertStore(
        state: PracticalShoppingSavedExactPreferenceState,
        preference: PracticalShoppingSavedExactStorePreference
    ): PracticalShoppingSavedExactPreferenceMutationResult {
        val exists = state.storePreferences.any { it.storeKey == preference.storeKey }
        if (!exists && state.storePreferences.size >= MAX_SAVED_EXACT_STORE_PREFERENCES) {
            return PracticalShoppingSavedExactPreferenceMutationResult(
                state = state,
                issue = PracticalShoppingSavedExactPreferenceMutationIssue.STORE_CAPACITY_REACHED
            )
        }

        val updated =
            state.storePreferences
                .filterNot { it.storeKey == preference.storeKey } +
                preference

        return PracticalShoppingSavedExactPreferenceMutationResult(
            state =
                PracticalShoppingSavedExactPreferenceState(
                    productPreferences = state.productPreferences,
                    storePreferences = sortStores(updated)
                )
        )
    }

    fun removeStore(
        state: PracticalShoppingSavedExactPreferenceState,
        storeKey: ShoppingStoreKey
    ): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences = state.productPreferences,
            storePreferences =
                state.storePreferences.filterNot { it.storeKey == storeKey }
        )

    fun clear(
        state: PracticalShoppingSavedExactPreferenceState
    ): PracticalShoppingSavedExactPreferenceState =
        if (state.productPreferences.isEmpty() && state.storePreferences.isEmpty()) {
            state
        } else {
            PracticalShoppingSavedExactPreferenceState.empty()
        }

    private fun sortProducts(
        values: List<PracticalShoppingSavedExactProductPreference>
    ): List<PracticalShoppingSavedExactProductPreference> =
        values.sortedBy { it.itemKey.value }

    private fun sortStores(
        values: List<PracticalShoppingSavedExactStorePreference>
    ): List<PracticalShoppingSavedExactStorePreference> =
        values.sortedBy { it.storeKey.value }
}

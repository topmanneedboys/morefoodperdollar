package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey

private const val MAX_HOME_SAVED_EXACT_PRODUCT_CONTEXT = 128
private const val MAX_HOME_SAVED_EXACT_PRODUCT_LABEL_LENGTH = 160

/**
 * Display-only context for exact product choices that have already been accepted by Saved.
 *
 * This is intentionally a small composition object rather than a second persistence model.
 * Labels come from the existing validated Saved projector, and unresolved keys remain typed so
 * Home can say that a choice exists without exposing a technical identity. The context never
 * supplies package, price, availability, store, freshness or planner authority.
 */
internal data class PracticalShoppingHomeSavedExactProductContext(
    val namedProductLabels: Map<ShoppingItemKey, String> = emptyMap(),
    val unresolvedProductKeys: Set<ShoppingItemKey> = emptySet()
) {
    init {
        require(namedProductLabels.size <= MAX_HOME_SAVED_EXACT_PRODUCT_CONTEXT)
        require(unresolvedProductKeys.size <= MAX_HOME_SAVED_EXACT_PRODUCT_CONTEXT)
        require(namedProductLabels.keys.intersect(unresolvedProductKeys).isEmpty())
        namedProductLabels.forEach { (itemKey, label) ->
            require(itemKey.value.isNotBlank())
            require(label.isNotBlank())
            require(label.length <= MAX_HOME_SAVED_EXACT_PRODUCT_LABEL_LENGTH)
            require(label.none { character -> character.isISOControl() })
        }
        unresolvedProductKeys.forEach { itemKey -> require(itemKey.value.isNotBlank()) }
    }

    companion object {
        /**
         * Projects only safe product rows from an already validated Saved composition snapshot.
         * The existing Saved projector remains the sole owner of label-leakage policy.
         */
        fun fromSnapshot(
            snapshot: PracticalShoppingSavedValidatedSnapshot
        ): PracticalShoppingHomeSavedExactProductContext {
            val projection =
                PracticalShoppingSavedExactPreferenceUiProjector.project(
                    savedState = snapshot.exactState,
                    metadata = snapshot.displayMetadata
                )
            return PracticalShoppingHomeSavedExactProductContext(
                namedProductLabels =
                    projection.state.productRows.associate { row ->
                        row.action.itemKey to row.title
                    },
                unresolvedProductKeys = projection.unresolvedProductKeys.toSet()
            )
        }
    }

    /**
     * Returns a truthful row notice for one current Home key. A missing label never falls back
     * to the opaque key or exact identity; it remains an explicit unresolved state.
     */
    fun noticeFor(itemKey: ShoppingItemKey): String? {
        val label = namedProductLabels[itemKey]
        if (label != null) {
            return "Saved exact product: $label. Private choice only — not applied to this fictional sample plan or any current price, stock or availability claim."
        }
        if (itemKey in unresolvedProductKeys) {
            return "An exact product choice is saved for this item, but its display name is unavailable. Saved keeps the identity without inventing a label; it is not applied to this fictional sample plan."
        }
        return null
    }

    /**
     * Adds one label only after the existing Remember transaction reports that display metadata
     * was accepted. Invalid external text fails closed instead of reaching a renderer.
     */
    fun withNamedProduct(
        itemKey: ShoppingItemKey,
        displayName: String,
        forbiddenIdentifiers: List<String> = emptyList()
    ): PracticalShoppingHomeSavedExactProductContext? {
        val safeName = displayName.trim()
        if (
            itemKey.value.isBlank() ||
                safeName.isBlank() ||
                safeName.length > MAX_HOME_SAVED_EXACT_PRODUCT_LABEL_LENGTH ||
                safeName.any { character -> character.isISOControl() } ||
                forbiddenIdentifiers
                    .map { identifier -> identifier.trim() }
                    .filter { identifier -> identifier.isNotBlank() }
                    .distinct()
                    .any { identifier ->
                        safeName.equals(identifier, ignoreCase = true) ||
                            (identifier.length >= 6 &&
                                safeName.contains(identifier, ignoreCase = true))
                    }
        ) {
            return null
        }
        return copy(
            namedProductLabels = namedProductLabels + (itemKey to safeName),
            unresolvedProductKeys = unresolvedProductKeys - itemKey
        )
    }

    /** Adds an accepted exact choice whose display metadata is unavailable, without leaking IDs. */
    fun withUnresolvedProduct(
        itemKey: ShoppingItemKey
    ): PracticalShoppingHomeSavedExactProductContext? {
        if (itemKey.value.isBlank()) return null
        return copy(
            namedProductLabels = namedProductLabels - itemKey,
            unresolvedProductKeys = unresolvedProductKeys + itemKey
        )
    }
}

package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey

private const val MAX_BASKET_PROGRESS_ITEMS = 128

/**
 * Typed foreground-only progress for a Basket shopping session.
 *
 * This state never owns prices, stores, ranking, evidence or purchase authority.
 * [eligibleItemKeys] is supplied by immutable Basket presentation and acts only as
 * a fail-closed capability set for the local "collected" interaction.
 */
data class PracticalShoppingBasketProgressState internal constructor(
    val eligibleItemKeys: Set<ShoppingItemKey>,
    val collectedItemKeys: Set<ShoppingItemKey>,
    /**
     * Opaque presentation scope for the plan that made the items collectible.
     *
     * This is not a shopping decision or a persisted business identifier. It only
     * prevents a foreground mark from following an item when its projected store
     * assignment or requested presentation changes.
     */
    val collectionScopeId: String? = null
) {
    init {
        require(eligibleItemKeys.size <= MAX_BASKET_PROGRESS_ITEMS)
        require(collectedItemKeys.size <= MAX_BASKET_PROGRESS_ITEMS)
        require(eligibleItemKeys.containsAll(collectedItemKeys))
        require(collectionScopeId == null || collectionScopeId.isNotBlank())
        require(collectionScopeId == null || collectionScopeId.length <= 64)
    }
}

object PracticalShoppingBasketProgressSession {

    fun initial(): PracticalShoppingBasketProgressState =
        PracticalShoppingBasketProgressState(
            eligibleItemKeys = emptySet(),
            collectedItemKeys = emptySet()
        )

    /**
     * Reconciles progress with the latest immutable Basket capability set.
     * Removed/ineligible items lose their collected mark; retained identities keep it.
     */
    fun reconcile(
        state: PracticalShoppingBasketProgressState,
        eligibleItemKeys: List<ShoppingItemKey>,
        collectionScopeId: String? = state.collectionScopeId
    ): PracticalShoppingBasketProgressState {
        require(eligibleItemKeys.size <= MAX_BASKET_PROGRESS_ITEMS)
        require(collectionScopeId == null || collectionScopeId.isNotBlank())
        require(collectionScopeId == null || collectionScopeId.length <= 64)

        val eligible = eligibleItemKeys.toCollection(linkedSetOf())
        val retained =
            if (state.collectionScopeId == collectionScopeId) {
                state.collectedItemKeys.filterTo(linkedSetOf(), eligible::contains)
            } else {
                // A changed projected scope (for example, a different planned
                // store) invalidates every old foreground mark conservatively.
                linkedSetOf()
            }

        return PracticalShoppingBasketProgressState(
            eligibleItemKeys = eligible.toSet(),
            collectedItemKeys = retained.toSet(),
            collectionScopeId = collectionScopeId
        )
    }

    /** Unknown or currently-ineligible identities fail closed without mutation. */
    fun toggle(
        state: PracticalShoppingBasketProgressState,
        itemKey: ShoppingItemKey
    ): PracticalShoppingBasketProgressState {
        if (itemKey !in state.eligibleItemKeys) return state

        val collected = state.collectedItemKeys.toCollection(linkedSetOf())
        if (!collected.add(itemKey)) {
            collected.remove(itemKey)
        }

        return PracticalShoppingBasketProgressState(
            eligibleItemKeys = state.eligibleItemKeys,
            collectedItemKeys = collected.toSet(),
            collectionScopeId = state.collectionScopeId
        )
    }

    /** Clears only the local check-off marks while preserving the current eligible plan items. */
    fun clearCollected(
        state: PracticalShoppingBasketProgressState
    ): PracticalShoppingBasketProgressState =
        PracticalShoppingBasketProgressState(
            eligibleItemKeys = state.eligibleItemKeys,
            collectedItemKeys = emptySet(),
            collectionScopeId = state.collectionScopeId
        )

    /** Stable value-only snapshot for Android view-state restoration. */
    fun snapshot(state: PracticalShoppingBasketProgressState): List<String> =
        state.collectedItemKeys
            .map(ShoppingItemKey::value)
            .sorted()

    /**
     * Restores only well-formed identities that remain eligible now. Corrupt,
     * duplicate or oversized saved state fails closed to no collected marks.
     */
    fun restore(
        collectedItemKeyValues: List<String>?,
        eligibleItemKeys: List<ShoppingItemKey>,
        collectionScopeId: String? = null,
        savedCollectionScopeId: String? = collectionScopeId
    ): PracticalShoppingBasketProgressState {
        val current = reconcile(initial(), eligibleItemKeys, collectionScopeId)
        val saved = collectedItemKeyValues ?: return current

        // Older view state has no scope id. It is intentionally not allowed to
        // restore marks into a newly-scoped plan; the two-argument legacy path
        // remains compatible because both ids are null there.
        if (collectionScopeId != savedCollectionScopeId) return current

        if (
            saved.size > MAX_BASKET_PROGRESS_ITEMS ||
            saved.any(String::isBlank) ||
            saved.distinct().size != saved.size
        ) {
            return current
        }

        val restored = saved.map(::ShoppingItemKey).toSet()
        return PracticalShoppingBasketProgressState(
            eligibleItemKeys = current.eligibleItemKeys,
            collectedItemKeys =
                restored
                    .filterTo(linkedSetOf(), current.eligibleItemKeys::contains)
                    .toSet(),
            collectionScopeId = current.collectionScopeId
        )
    }
}

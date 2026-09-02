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
    val collectedItemKeys: Set<ShoppingItemKey>
) {
    init {
        require(eligibleItemKeys.size <= MAX_BASKET_PROGRESS_ITEMS)
        require(collectedItemKeys.size <= MAX_BASKET_PROGRESS_ITEMS)
        require(eligibleItemKeys.containsAll(collectedItemKeys))
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
        eligibleItemKeys: List<ShoppingItemKey>
    ): PracticalShoppingBasketProgressState {
        require(eligibleItemKeys.size <= MAX_BASKET_PROGRESS_ITEMS)

        val eligible = eligibleItemKeys.toCollection(linkedSetOf())
        val retained =
            state.collectedItemKeys
                .filterTo(linkedSetOf(), eligible::contains)

        return PracticalShoppingBasketProgressState(
            eligibleItemKeys = eligible.toSet(),
            collectedItemKeys = retained.toSet()
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
            collectedItemKeys = collected.toSet()
        )
    }

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
        eligibleItemKeys: List<ShoppingItemKey>
    ): PracticalShoppingBasketProgressState {
        val current = reconcile(initial(), eligibleItemKeys)
        val saved = collectedItemKeyValues ?: return current

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
                    .toSet()
        )
    }
}

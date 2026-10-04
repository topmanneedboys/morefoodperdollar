package com.valuepilot.app

/**
 * Presentation-only Home modes.  The policy deliberately consumes the already projected
 * Home state and never decides whether an item is priced, available, ranked or safe.
 */
internal enum class PracticalShoppingHomeMode {
    FIRST_TIME,
    ACTIVE_BASKET,
    RETURNING
}

internal data class PracticalShoppingHomePresentation(
    val mode: PracticalShoppingHomeMode,
    val showWelcomePurpose: Boolean,
    val showActiveBasket: Boolean,
    val showBuyAgain: Boolean,
    val showBasketEditor: Boolean,
    val itemCount: Int,
    val collectibleIdentityCount: Int,
    val collectedIdentityCount: Int
) {
    init {
        require(itemCount >= 0)
        require(collectibleIdentityCount >= 0)
        require(collectedIdentityCount in 0..collectibleIdentityCount)
        require(showWelcomePurpose == (mode == PracticalShoppingHomeMode.FIRST_TIME))
        require(showActiveBasket == (mode == PracticalShoppingHomeMode.ACTIVE_BASKET))
        require(showBasketEditor == showActiveBasket)
    }

    val collectionProgress: Float?
        get() = collectibleIdentityCount
            .takeIf { it > 0 }
            ?.let { collectedIdentityCount.toFloat() / it.toFloat() }
}

internal object PracticalShoppingHomePresentationPolicy {

    fun from(state: PracticalShoppingHomeRenderState): PracticalShoppingHomePresentation {
        val activeBasket =
            state.query.isNotBlank() ||
                state.items.isNotEmpty() ||
                state.unknownItems.isNotEmpty() ||
                state.result != null ||
                state.privateIdentityItems.isNotEmpty() ||
                state.privateIdentityCollectedKeys.isNotEmpty()
        val returning =
            !activeBasket &&
                (
                    state.confirmedPurchaseCount > 0 ||
                        state.privateIdentityItems.isNotEmpty() ||
                        state.privateMemorySummary != null ||
                        state.privateMemoryHighlights.isNotEmpty()
                    )
        val mode =
            when {
                activeBasket -> PracticalShoppingHomeMode.ACTIVE_BASKET
                returning -> PracticalShoppingHomeMode.RETURNING
                else -> PracticalShoppingHomeMode.FIRST_TIME
            }
        val collectibleIdentityCount = state.privateIdentityItems.size
        val collectedIdentityCount =
            state.privateIdentityCollectedKeys.toSet().count { key ->
                state.privateIdentityItems.any { it.identityKey == key }
            }
        return PracticalShoppingHomePresentation(
            mode = mode,
            showWelcomePurpose = mode == PracticalShoppingHomeMode.FIRST_TIME,
            showActiveBasket = mode == PracticalShoppingHomeMode.ACTIVE_BASKET,
            showBuyAgain = mode == PracticalShoppingHomeMode.RETURNING && state.confirmedPurchaseCount > 0,
            showBasketEditor = mode == PracticalShoppingHomeMode.ACTIVE_BASKET,
            itemCount = state.items.size + state.unknownItems.size + state.privateIdentityItems.size,
            collectibleIdentityCount = collectibleIdentityCount,
            collectedIdentityCount = collectedIdentityCount
        )
    }
}

package com.valuepilot.app

/**
 * Presentation-only Basket hierarchy derived from the immutable Basket projection.
 *
 * This policy deliberately does not decide price coverage, ranking, store choice,
 * purchase facts, or trip economics. It only decides which already-projected
 * pieces deserve the first viewport and which belong behind contextual details.
 */
internal data class PracticalShoppingBasketPresentation(
    val isEmpty: Boolean,
    val productCount: Int,
    val progressTotal: Int,
    val progressCollected: Int,
    val progressPercent: Int,
    val showProgress: Boolean,
    val showGuidance: Boolean,
    val showPrimaryAction: Boolean,
    val showRecordPurchase: Boolean,
    val showStoreContext: Boolean,
    val showTripAction: Boolean,
    val showDetailsToggle: Boolean,
    val showPrivateChecklist: Boolean
) {
    init {
        require(productCount >= 0)
        require(progressTotal >= 0)
        require(progressCollected in 0..progressTotal)
        require(progressPercent in 0..100)
        require(progressPercent == if (progressTotal == 0) 0 else progressCollected * 100 / progressTotal)
        require(showProgress == (progressTotal > 0))
        require(showRecordPurchase == (progressCollected > 0))
        require(showTripAction implies showStoreContext)
        require(showPrivateChecklist == false || productCount > 0)
    }

    private infix fun Boolean.implies(other: Boolean): Boolean = !this || other
}

internal object PracticalShoppingBasketPresentationPolicy {

    fun from(
        state: PracticalShoppingBasketRenderState,
        collectedRegularCount: Int
    ): PracticalShoppingBasketPresentation {
        require(collectedRegularCount >= 0)
        val privateCollectedCount = state.privateIdentityItems.count { it.collected }
        val productCount =
            state.items.size + state.unknownItems.size + state.privateIdentityItems.size
        val isEmpty = state.status == PracticalShoppingBasketStatus.EMPTY && productCount == 0
        val progressTotal = productCount
        val progressCollected =
            (collectedRegularCount + privateCollectedCount).coerceIn(0, progressTotal)
        val progressPercent =
            if (progressTotal == 0) 0 else progressCollected * 100 / progressTotal
        val hasStore = state.items.any { it.storeAssignment != null }
        val hasAdvancedDetails =
            state.result != null ||
                state.unknownItems.isNotEmpty() ||
                state.noCoverageSummary != null ||
                state.extraStopRuleText != null ||
                state.extraStopRuleNotice != null ||
                state.collectionNotice != null

        return PracticalShoppingBasketPresentation(
            isEmpty = isEmpty,
            productCount = productCount,
            progressTotal = progressTotal,
            progressCollected = progressCollected,
            progressPercent = progressPercent,
            showProgress = progressTotal > 0,
            showGuidance = isEmpty || state.status != PracticalShoppingBasketStatus.PLANNED,
            showPrimaryAction = isEmpty || state.status != PracticalShoppingBasketStatus.PLANNED,
            showRecordPurchase = progressCollected > 0,
            showStoreContext = hasStore,
            showTripAction = hasStore && state.result?.primary != null,
            showDetailsToggle = !isEmpty && hasAdvancedDetails,
            showPrivateChecklist = state.privateIdentityItems.isNotEmpty()
        )
    }
}

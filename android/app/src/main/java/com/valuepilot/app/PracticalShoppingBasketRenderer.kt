package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey

/**
 * Immutable Basket-tab presentation derived from the already-rendered Home state.
 *
 * This boundary never plans, ranks, totals, or fills missing facts. It only gives
 * the existing Home result a dedicated consumer destination.
 */
enum class PracticalShoppingBasketStatus {
    EMPTY,
    NEEDS_ATTENTION,
    PLANNED
}

data class PracticalShoppingBasketRenderState(
    val status: PracticalShoppingBasketStatus,
    val headline: String,
    val guidance: String,
    val items: List<PracticalShoppingHomeItemRenderState>,
    val unknownItems: List<String>,
    val result: PracticalShoppingUiState?,
    val extraStopRuleText: String?,
    /** Explains when the already-selected rule is not evaluated for an incomplete result. */
    val extraStopRuleNotice: String? = null,
    val collectionEnabled: Boolean,
    val actionLabel: String,
    val sampleNotice: String,
    /** Exact covered item identities that the foreground check-off may mark. */
    val collectibleItemKeys: List<ShoppingItemKey> =
        if (collectionEnabled) {
            // Preserve older direct constructor call sites; the renderer below
            // always supplies the typed assignment set explicitly.
            items.map { it.key }
        } else {
            emptyList()
        },
    /** Explains the scope of foreground marks without implying cart/order authority. */
    val collectionNotice: String? =
        if (collectionEnabled) {
            "Check-off is only a local shopping-session aid; it does not place an order or change the plan."
        } else {
            null
        }
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(unknownItems.none(String::isBlank))
        require(extraStopRuleText == null || extraStopRuleText.isNotBlank())
        require(extraStopRuleNotice == null || extraStopRuleNotice.isNotBlank())
        require(extraStopRuleNotice == null || extraStopRuleText != null)
        require(actionLabel.isNotBlank())
        require(sampleNotice.isNotBlank())
        require(collectionNotice == null || collectionNotice.isNotBlank())
        require((status == PracticalShoppingBasketStatus.PLANNED) == (result != null))
        require((result?.primary != null) == (extraStopRuleText != null))
        require(!collectionEnabled || status == PracticalShoppingBasketStatus.PLANNED)
        require(!collectionEnabled || result?.primary != null)
        require(collectibleItemKeys.distinct().size == collectibleItemKeys.size)
        require(collectibleItemKeys.all { key -> items.any { it.key == key } })
        require(collectionEnabled == collectibleItemKeys.isNotEmpty())
        require((collectionNotice != null) == collectionEnabled)
        if (status == PracticalShoppingBasketStatus.EMPTY) {
            require(items.isEmpty())
            require(unknownItems.isEmpty())
        }
    }
}

object PracticalShoppingBasketRenderer {

    fun render(source: PracticalShoppingHomeRenderState): PracticalShoppingBasketRenderState {
        val draftReadyForPlanning =
            source.result == null &&
                source.query.isNotBlank() &&
                source.items.isEmpty() &&
                source.unknownItems.isEmpty() &&
                source.submitEnabled
        val status =
            when {
                source.result != null -> PracticalShoppingBasketStatus.PLANNED
                source.query.isBlank() && source.items.isEmpty() && source.unknownItems.isEmpty() ->
                    PracticalShoppingBasketStatus.EMPTY
                else -> PracticalShoppingBasketStatus.NEEDS_ATTENTION
            }

        val headline =
            when (status) {
                PracticalShoppingBasketStatus.EMPTY -> "No basket planned yet"
                PracticalShoppingBasketStatus.NEEDS_ATTENTION ->
                    if (draftReadyForPlanning) {
                        "Plan this list on Home"
                    } else {
                        "Finish your shopping list"
                    }
                PracticalShoppingBasketStatus.PLANNED ->
                    if (source.result?.primary == null) {
                        "Price coverage needed"
                    } else {
                        "Your current basket plan"
                    }
            }
        val guidance =
            when (status) {
                PracticalShoppingBasketStatus.EMPTY ->
                    "Build your shopping list on Home, then its plan will appear here."
                PracticalShoppingBasketStatus.NEEDS_ATTENTION ->
                    if (draftReadyForPlanning) {
                        "Your list is ready to plan. Return to Home and tap Plan my shop to see the sample result."
                    } else {
                        source.message ?: "Finish the items that need attention on Home."
                    }
                PracticalShoppingBasketStatus.PLANNED ->
                    when {
                        source.result?.primary == null ->
                            "No usable price coverage yet. Return to Home to adjust your sample list."

                        source.result.primary.missingItemsText == null ->
                            "Review the full recommendation before you shop. Return to Home to change any item."

                        else ->
                            "Review the priced items before you shop. Items without a usable price stay unchecked until verified."
                    }
            }

        // Check-off uses only the exact item-to-store assignments projected by
        // the planner boundary. Incomplete plans expose covered items only;
        // missing-price rows remain visible but have no collection action.
        val collectibleItemKeys =
            if (status == PracticalShoppingBasketStatus.PLANNED && source.result?.primary != null) {
                val assignedKeys = source.result.itemStoreAssignments.map { it.itemKey }.toSet()
                source.items.map { it.key }.filter(assignedKeys::contains)
            } else {
                emptyList()
            }
        val collectionEnabled = collectibleItemKeys.isNotEmpty()

        return PracticalShoppingBasketRenderState(
            status = status,
            headline = headline,
            guidance = guidance,
            items = source.items,
            unknownItems = source.unknownItems,
            // Preserve the already-projected result object exactly.
            result = source.result,
            extraStopRuleText =
                source.extraStopSettings.summary.takeIf { source.result?.primary != null },
            extraStopRuleNotice =
                source.extraStopSettings.notice.takeIf { source.result?.primary != null },
            collectionEnabled = collectionEnabled,
            actionLabel =
                if (status == PracticalShoppingBasketStatus.EMPTY) {
                    "Build my basket on Home"
                } else if (draftReadyForPlanning) {
                    "Plan this list on Home"
                } else {
                    "Edit on Home"
                },
            sampleNotice = source.sampleNotice,
            collectibleItemKeys = collectibleItemKeys
        )
    }
}

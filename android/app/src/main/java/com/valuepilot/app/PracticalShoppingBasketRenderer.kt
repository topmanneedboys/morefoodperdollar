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
        }
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(unknownItems.none(String::isBlank))
        require(extraStopRuleText == null || extraStopRuleText.isNotBlank())
        require(actionLabel.isNotBlank())
        require(sampleNotice.isNotBlank())
        require((status == PracticalShoppingBasketStatus.PLANNED) == (result != null))
        require((result == null) == (extraStopRuleText == null))
        require(!collectionEnabled || status == PracticalShoppingBasketStatus.PLANNED)
        require(!collectionEnabled || result?.primary != null)
        require(collectibleItemKeys.distinct().size == collectibleItemKeys.size)
        require(collectibleItemKeys.all { key -> items.any { it.key == key } })
        require(collectionEnabled == collectibleItemKeys.isNotEmpty())
        if (status == PracticalShoppingBasketStatus.EMPTY) {
            require(items.isEmpty())
            require(unknownItems.isEmpty())
        }
    }
}

object PracticalShoppingBasketRenderer {

    fun render(source: PracticalShoppingHomeRenderState): PracticalShoppingBasketRenderState {
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
                PracticalShoppingBasketStatus.NEEDS_ATTENTION -> "Finish your shopping list"
                PracticalShoppingBasketStatus.PLANNED -> "Your current basket plan"
            }
        val guidance =
            when (status) {
                PracticalShoppingBasketStatus.EMPTY ->
                    "Build your shopping list on Home, then its plan will appear here."
                PracticalShoppingBasketStatus.NEEDS_ATTENTION ->
                    source.message ?: "Finish the items that need attention on Home."
                PracticalShoppingBasketStatus.PLANNED ->
                    if (source.result?.primary?.missingItemsText == null) {
                        "Review the full recommendation before you shop. Return to Home to change any item."
                    } else {
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
            extraStopRuleText = source.extraStopSettings.summary.takeIf { source.result != null },
            collectionEnabled = collectionEnabled,
            actionLabel =
                if (status == PracticalShoppingBasketStatus.EMPTY) {
                    "Build my basket on Home"
                } else {
                    "Edit on Home"
                },
            sampleNotice = source.sampleNotice,
            collectibleItemKeys = collectibleItemKeys
        )
    }
}

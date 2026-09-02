package com.valuepilot.app

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
    val actionLabel: String,
    val sampleNotice: String
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
                    "Review the full recommendation before you shop. Return to Home to change any item."
            }

        return PracticalShoppingBasketRenderState(
            status = status,
            headline = headline,
            guidance = guidance,
            items = source.items,
            unknownItems = source.unknownItems,
            // Preserve the already-projected result object exactly.
            result = source.result,
            extraStopRuleText = source.extraStopSettings.summary.takeIf { source.result != null },
            actionLabel =
                if (status == PracticalShoppingBasketStatus.EMPTY) {
                    "Build my basket on Home"
                } else {
                    "Edit on Home"
                },
            sampleNotice = source.sampleNotice
        )
    }
}

package com.valuepilot.app

/**
 * A deliberately generic, privacy-safe text card for an explicitly shared Good Price result.
 *
 * The card contains the exact entered price, package quantity and calculated unit rate, but no
 * product name, personal history, receipt, location, account identifier or source identifier.
 * It is a presentation artifact only; the Good Price route remains the authority for the result.
 */
internal data class GoodPriceShareCard(
    val title: String,
    val text: String,
    val preview: String
) {
    init {
        require(title.isNotBlank())
        require(text.isNotBlank())
        require(preview.isNotBlank())
        require(title.length <= MAX_SHARE_CARD_TEXT_CHARS)
        require(text.length <= MAX_SHARE_CARD_TEXT_CHARS)
        require(preview.length <= MAX_SHARE_CARD_TEXT_CHARS)
        require(title.none { Character.isISOControl(it.code) })
        require(text.none { Character.isISOControl(it.code) })
        require(preview.none { Character.isISOControl(it.code) })
    }

    companion object {
        private const val MAX_SHARE_CARD_TEXT_CHARS = 600
    }
}

/**
 * Projects one exact Good Price result into a generic share card.
 *
 * Personal-history wording is intentionally excluded. A shopper can share the useful unit-price
 * math without disclosing that private history exists or revealing what product they checked.
 */
internal object GoodPriceShareCardProjector {
    fun project(state: GoodPriceCheckUiState): GoodPriceShareCard? {
        val price = safeFact(state.priceText) ?: return null
        val quantity = safeFact(state.quantityText) ?: return null
        val unitRate = safeFact(state.unitRateText) ?: return null
        val priceMode = safeFact(state.priceModeText) ?: return null

        val title = "ValuePilot checked an exact grocery price"
        val detail = "Price entered: $price for $quantity ($priceMode)."
        val resultLine = "Exact unit rate: $unitRate"
        val disclosure = "Based on a price I entered, not live store pricing."
        val text =
            "I checked an exact grocery price with ValuePilot. $detail $resultLine $disclosure"
        val preview =
            "$title. $detail $resultLine $disclosure " +
                "This share contains no product name, private history, receipt, location or account data."

        return GoodPriceShareCard(
            title = title,
            text = text,
            preview = preview
        )
    }

    private fun safeFact(value: String): String? {
        val trimmed = value.trim()
        return trimmed.takeIf {
            it.isNotBlank() &&
                it.length <= 180 &&
                it.none { character -> Character.isISOControl(character.code) }
        }
    }
}

package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection

/** Stable, fail-closed encoding for the user's manual Compare Here price-basis choice. */
internal object CompareHerePriceSelectionPersistence {
    fun encode(selection: CompareHerePriceSelection): String = selection.name

    fun decode(raw: String?): CompareHerePriceSelection =
        raw
            ?.let { value ->
                runCatching { CompareHerePriceSelection.valueOf(value) }.getOrNull()
            }
            ?: CompareHerePriceSelection.CURRENT
}

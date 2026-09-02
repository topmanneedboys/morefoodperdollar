package com.valuepilot.app

/**
 * Deterministic editing helper for the manual Compare Here product-entry draft.
 *
 * Removing an entry never changes the product text in any other entry. The activity keeps two
 * empty slots available so the primary comparison flow always has its minimum input shape.
 */
internal object CompareHereManualProductDraft {

    private const val MINIMUM_INPUT_SLOTS = 2

    fun removeAt(blocks: List<String>, index: Int): List<String> {
        if (index !in blocks.indices) return blocks.toList()
        if (blocks.size <= MINIMUM_INPUT_SLOTS) {
            return blocks.mapIndexed { currentIndex, value ->
                if (currentIndex == index) "" else value
            }
        }
        return blocks.filterIndexed { currentIndex, _ -> currentIndex != index }
    }
}

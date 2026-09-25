package com.valuepilot.app

internal enum class CompareHereManualEditorBlockIssue {
    TOO_LONG
}

internal data class CompareHereManualEditorBlock(
    val text: String,
    val issue: CompareHereManualEditorBlockIssue? = null
) {
    init {
        require(text.length <= ManualProductObservationAdapter.MAX_BLOCK_CHARS)
        require(issue == null || text.isEmpty())
    }
}

/**
 * Deterministic editing helper for the manual Compare Here product-entry draft.
 *
 * Removing an entry never changes the product text in any other entry. The activity keeps two
 * empty slots available so the primary comparison flow always has its minimum input shape.
 */
internal object CompareHereManualProductDraft {

    const val MAX_BLOCK_CHARS = ManualProductObservationAdapter.MAX_BLOCK_CHARS

    private const val MINIMUM_INPUT_SLOTS = 2

    /**
     * Prepares bounded editor state without turning a rejected oversized block into partial facts.
     * Oversized restored content becomes an explicit empty/error entry rather than being truncated.
     */
    fun prepareForEditor(blocks: List<String>): List<CompareHereManualEditorBlock> =
        blocks
            .take(CompareHereManualInputAdapter.MAX_OBSERVATIONS)
            .map { value ->
                if (value.length > MAX_BLOCK_CHARS) {
                    CompareHereManualEditorBlock(
                        text = "",
                        issue = CompareHereManualEditorBlockIssue.TOO_LONG
                    )
                } else {
                    CompareHereManualEditorBlock(text = value)
                }
            }

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

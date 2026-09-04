package com.valuepilot.app

/**
 * Deterministic insertion for text intentionally shared into Compare Here.
 *
 * Shared content remains raw, user-provided text. This helper only places one bounded value in
 * the earliest empty editor slot; it does not parse, infer, overwrite, or establish any product,
 * quantity, price, store, availability, promotion, or ranking fact.
 */
internal enum class CompareHereSharedTextDraftIssue {
    BLANK_TEXT,
    TEXT_TOO_LONG,
    NO_EMPTY_SLOT
}

internal data class CompareHereSharedTextDraftResult(
    val blocks: List<String>,
    val addedIndex: Int?,
    val issue: CompareHereSharedTextDraftIssue?
) {
    init {
        require(blocks.size <= CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        require((addedIndex == null) == (issue != null))
        addedIndex?.let { require(it in blocks.indices) }
    }

    val added: Boolean
        get() = addedIndex != null
}

internal object CompareHereSharedTextDraft {

    fun apply(
        existingBlocks: List<String>,
        sharedText: String
    ): CompareHereSharedTextDraftResult {
        require(existingBlocks.size <= CompareHereManualInputAdapter.MAX_OBSERVATIONS)

        val normalizedText = sharedText.trim()
        if (normalizedText.isBlank()) {
            return rejected(existingBlocks, CompareHereSharedTextDraftIssue.BLANK_TEXT)
        }
        if (normalizedText.length > CompareHereManualProductDraft.MAX_BLOCK_CHARS) {
            return rejected(existingBlocks, CompareHereSharedTextDraftIssue.TEXT_TOO_LONG)
        }

        val targetIndex = existingBlocks.indexOfFirst { it.isBlank() }
        if (targetIndex < 0) {
            return rejected(existingBlocks, CompareHereSharedTextDraftIssue.NO_EMPTY_SLOT)
        }

        return CompareHereSharedTextDraftResult(
            blocks = existingBlocks.mapIndexed { index, value ->
                if (index == targetIndex) normalizedText else value
            },
            addedIndex = targetIndex,
            issue = null
        )
    }

    private fun rejected(
        existingBlocks: List<String>,
        issue: CompareHereSharedTextDraftIssue
    ): CompareHereSharedTextDraftResult =
        CompareHereSharedTextDraftResult(
            blocks = existingBlocks.toList(),
            addedIndex = null,
            issue = issue
        )
}

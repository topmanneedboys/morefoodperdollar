package com.valuepilot.app

/**
 * Deterministic identity-only insertion for the Compare Here editor.
 *
 * A barcode identity suggestion may fill one empty editor slot, but it must never replace a
 * shopper's existing entry or smuggle package, price, store, availability or ranking facts into
 * the comparison request. The shopper still completes and reviews the whole product entry.
 */
internal enum class CompareHereBarcodeDraftIssue {
    BLANK_IDENTITY,
    IDENTITY_TOO_LONG,
    NO_EMPTY_SLOT
}

internal data class CompareHereBarcodeDraftResult(
    val blocks: List<String>,
    val addedIndex: Int?,
    val issue: CompareHereBarcodeDraftIssue?
) {
    init {
        require(blocks.size <= CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        require((addedIndex == null) == (issue != null))
        addedIndex?.let { require(it in blocks.indices) }
    }

    val added: Boolean
        get() = addedIndex != null
}

internal object CompareHereBarcodeDraft {

    fun apply(
        existingBlocks: List<String>,
        displayName: String
    ): CompareHereBarcodeDraftResult {
        require(existingBlocks.size <= CompareHereManualInputAdapter.MAX_OBSERVATIONS)

        val normalizedName = displayName.trim()
        if (normalizedName.isBlank()) {
            return rejected(existingBlocks, CompareHereBarcodeDraftIssue.BLANK_IDENTITY)
        }
        if (normalizedName.length > CompareHereManualProductDraft.MAX_BLOCK_CHARS) {
            return rejected(existingBlocks, CompareHereBarcodeDraftIssue.IDENTITY_TOO_LONG)
        }

        val targetIndex = existingBlocks.indexOfFirst { it.isBlank() }
        if (targetIndex < 0) {
            return rejected(existingBlocks, CompareHereBarcodeDraftIssue.NO_EMPTY_SLOT)
        }

        return CompareHereBarcodeDraftResult(
            blocks = existingBlocks.mapIndexed { index, value ->
                if (index == targetIndex) normalizedName else value
            },
            addedIndex = targetIndex,
            issue = null
        )
    }

    private fun rejected(
        existingBlocks: List<String>,
        issue: CompareHereBarcodeDraftIssue
    ): CompareHereBarcodeDraftResult =
        CompareHereBarcodeDraftResult(
            blocks = existingBlocks.toList(),
            addedIndex = null,
            issue = issue
        )
}

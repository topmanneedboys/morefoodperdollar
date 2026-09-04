package com.valuepilot.app

import java.util.Locale

/**
 * Bounded, review-first bridge from user-provided OCR text into Compare Here's editor.
 *
 * OCR is only a proposal. This helper never parses, confirms, ranks, or persists an observation;
 * the existing editor and like-for-like confirmation remain the authority. Invalid or ambiguous
 * snippets are discarded instead of being truncated into facts that could look trustworthy.
 */
internal data class CompareHerePhotoDraftImportResult(
    val blocks: List<String>,
    val addedCount: Int,
    val skippedCount: Int
) {
    init {
        require(blocks.size <= CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        require(addedCount >= 0)
        require(skippedCount >= 0)
    }
}

internal object CompareHerePhotoDraft {
    private const val ALLOWED_CONTROL_CHARACTERS = "\r\n\t"

    fun append(
        existingBlocks: List<String>,
        recognizedBlocks: List<String>
    ): CompareHerePhotoDraftImportResult {
        val blocks =
            CompareHereManualProductDraft.prepareForEditor(existingBlocks)
                .map { it.text }
                .toMutableList()

        while (blocks.size < 2) {
            blocks += ""
        }

        val seen =
            blocks
                .mapNotNull(::normalizationKey)
                .toMutableSet()
        val boundedRecognized =
            recognizedBlocks.take(CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        var addedCount = 0
        var skippedCount =
            (recognizedBlocks.size - boundedRecognized.size).coerceAtLeast(0)

        boundedRecognized.forEach { raw ->
            val candidate = raw.trim()
            val key = normalizationKey(candidate)
            if (
                key == null ||
                key in seen
            ) {
                skippedCount += 1
                return@forEach
            }

            val blankIndex = blocks.indexOfFirst { it.isBlank() }
            if (blankIndex >= 0) {
                blocks[blankIndex] = candidate
            } else if (
                blocks.size < CompareHereManualInputAdapter.MAX_OBSERVATIONS
            ) {
                blocks += candidate
            } else {
                skippedCount += 1
                return@forEach
            }

            seen += key
            addedCount += 1
        }

        return CompareHerePhotoDraftImportResult(
            blocks = blocks.toList(),
            addedCount = addedCount,
            skippedCount = skippedCount
        )
    }

    private fun normalizationKey(value: String): String? {
        if (
            value.isBlank() ||
            value.length > CompareHereManualProductDraft.MAX_BLOCK_CHARS ||
            value.any { it.isISOControl() && it !in ALLOWED_CONTROL_CHARACTERS }
        ) {
            return null
        }

        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
            .takeIf(String::isNotEmpty)
    }
}

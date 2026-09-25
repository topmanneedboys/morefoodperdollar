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
    val skippedCount: Int,
    val firstAddedIndex: Int?
) {
    init {
        require(blocks.size <= CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        require(addedCount >= 0)
        require(skippedCount >= 0)
        require((firstAddedIndex == null) == (addedCount == 0))
        firstAddedIndex?.let { require(it in blocks.indices) }
    }
}

/**
 * Safe OCR proposals ready for an explicit shopper review step.
 *
 * The candidates are still raw, user-provided text. This result deliberately carries no parsed
 * product, quantity, price, currency, promotion or confidence authority; the exact comparison
 * route remains the only place that can admit those facts.
 */
internal data class CompareHerePhotoDraftReview(
    val candidates: List<String>,
    val skippedCount: Int
) {
    init {
        require(candidates.size <= CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        require(candidates.all { it.isNotBlank() })
        require(skippedCount >= 0)
    }
}

internal object CompareHerePhotoDraft {
    private const val ALLOWED_CONTROL_CHARACTERS = "\r\n\t"

    /**
     * Filters and de-duplicates OCR text before it reaches the visible editor. A caller may show
     * [candidates] in a review surface and pass only the explicitly selected values to [append].
     */
    fun review(
        existingBlocks: List<String>,
        recognizedBlocks: List<String>
    ): CompareHerePhotoDraftReview {
        val preparedBlocks =
            CompareHereManualProductDraft.prepareForEditor(existingBlocks)
                .map { it.text }
        val seen =
            preparedBlocks
                .mapNotNull(::normalizationKey)
                .toMutableSet()
        var availableSlots =
            CompareHereManualInputAdapter.MAX_OBSERVATIONS -
                preparedBlocks.count { it.isNotBlank() }
        val boundedRecognized =
            recognizedBlocks.take(CompareHereManualInputAdapter.MAX_OBSERVATIONS)
        var skippedCount =
            (recognizedBlocks.size - boundedRecognized.size).coerceAtLeast(0)
        val candidates = mutableListOf<String>()

        boundedRecognized.forEach { raw ->
            val candidate = raw.trim()
            val key = normalizationKey(candidate)
            if (key == null || key in seen || availableSlots == 0) {
                skippedCount += 1
                return@forEach
            }

            candidates += candidate
            seen += key
            availableSlots -= 1
        }

        return CompareHerePhotoDraftReview(
            candidates = candidates.toList(),
            skippedCount = skippedCount
        )
    }

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

        val review = review(existingBlocks, recognizedBlocks)
        var addedCount = 0
        var skippedCount = review.skippedCount
        var firstAddedIndex: Int? = null

        review.candidates.forEach { candidate ->
            val blankIndex = blocks.indexOfFirst { it.isBlank() }
            if (blankIndex >= 0) {
                blocks[blankIndex] = candidate
                if (firstAddedIndex == null) {
                    firstAddedIndex = blankIndex
                }
            } else if (
                blocks.size < CompareHereManualInputAdapter.MAX_OBSERVATIONS
            ) {
                val appendedIndex = blocks.size
                blocks += candidate
                if (firstAddedIndex == null) {
                    firstAddedIndex = appendedIndex
                }
            } else {
                skippedCount += 1
                return@forEach
            }

            addedCount += 1
        }

        return CompareHerePhotoDraftImportResult(
            blocks = blocks.toList(),
            addedCount = addedCount,
            skippedCount = skippedCount,
            firstAddedIndex = firstAddedIndex
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

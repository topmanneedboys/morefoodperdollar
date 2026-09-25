package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CompareHerePhotoDraftTest {
    @Test
    fun reviewReturnsOnlySafeNewCandidatesBeforeAnyDraftMutation() {
        val existing = listOf("Milk\nCA$6.49", "")

        val review =
            CompareHerePhotoDraft.review(
                existingBlocks = existing,
                recognizedBlocks =
                    listOf(
                        " milk  CA$6.49 ",
                        "Eggs\nCA$4.99",
                        "Bread\nCA$3.49"
                    )
            )

        assertEquals(
            listOf("Eggs\nCA$4.99", "Bread\nCA$3.49"),
            review.candidates
        )
        assertEquals(1, review.skippedCount)
        assertEquals(existing, listOf("Milk\nCA$6.49", ""))
    }

    @Test
    fun reviewIsBoundedAndDeterministicBeforeTheShopperSelectsSuggestions() {
        val recognized =
            List(CompareHereManualInputAdapter.MAX_OBSERVATIONS + 3) { index ->
                "Product $index CA$${index + 1}.00"
            }

        val first = CompareHerePhotoDraft.review(listOf("", ""), recognized)
        val second = CompareHerePhotoDraft.review(listOf("", ""), recognized)

        assertEquals(first, second)
        assertEquals(CompareHereManualInputAdapter.MAX_OBSERVATIONS, first.candidates.size)
        assertEquals(3, first.skippedCount)
    }

    @Test
    fun reviewBoundsAnOversizedExistingDraftLikeTheEditorBoundary() {
        val existing =
            List(CompareHereManualInputAdapter.MAX_OBSERVATIONS + 2) { index ->
                "Existing $index CA$${index + 1}.00"
            }

        val review =
            CompareHerePhotoDraft.review(
                existingBlocks = existing,
                recognizedBlocks = listOf("New product CA$9.99")
            )

        assertEquals(emptyList<String>(), review.candidates)
        assertEquals(1, review.skippedCount)
        assertEquals(CompareHereManualInputAdapter.MAX_OBSERVATIONS + 2, existing.size)
    }

    @Test
    fun fillsExistingEmptySlotsBeforeAppendingAndPreservesTypedBlocks() {
        val result =
            CompareHerePhotoDraft.append(
                existingBlocks = listOf("Milk\nCA$6.49", ""),
                recognizedBlocks = listOf("Eggs\nCA$4.99", "Bread\nCA$3.49")
            )

        assertEquals(
            listOf("Milk\nCA$6.49", "Eggs\nCA$4.99", "Bread\nCA$3.49"),
            result.blocks
        )
        assertEquals(2, result.addedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(1, result.firstAddedIndex)
    }

    @Test
    fun rejectsBlankControlOverlongAndDuplicateSnippetsWithoutTruncatingThem() {
        val overlong =
            "x".repeat(CompareHereManualProductDraft.MAX_BLOCK_CHARS + 1)
        val result =
            CompareHerePhotoDraft.append(
                existingBlocks = listOf("Milk\nCA$6.49", ""),
                recognizedBlocks =
                    listOf(
                        " milk  CA$6.49 ",
                        "   ",
                        "Bread\u0000 CA$3.49",
                        overlong,
                        "Eggs\nCA$4.99"
                    )
            )

        assertEquals(
            listOf("Milk\nCA$6.49", "Eggs\nCA$4.99"),
            result.blocks
        )
        assertEquals(1, result.addedCount)
        assertEquals(4, result.skippedCount)
        assertEquals(1, result.firstAddedIndex)
    }

    @Test
    fun respectsTheComparisonCapacityAndCountsOverflowAsSkipped() {
        val existing =
            List(CompareHereManualInputAdapter.MAX_OBSERVATIONS) { index ->
                "Product $index CA$${index + 1}.00"
            }

        val result =
            CompareHerePhotoDraft.append(
                existingBlocks = existing,
                recognizedBlocks = listOf("New product CA$9.99", "Another CA$8.99")
            )

        assertEquals(existing, result.blocks)
        assertEquals(0, result.addedCount)
        assertEquals(2, result.skippedCount)
        assertEquals(null, result.firstAddedIndex)
    }

    @Test
    fun inputBeyondRecognitionBoundIsDeterministicallySkipped() {
        val recognized =
            List(CompareHereManualInputAdapter.MAX_OBSERVATIONS + 3) { index ->
                "Product $index CA$${index + 1}.00"
            }

        val first = CompareHerePhotoDraft.append(listOf("", ""), recognized)
        val second = CompareHerePhotoDraft.append(listOf("", ""), recognized)

        assertEquals(first, second)
        assertEquals(CompareHereManualInputAdapter.MAX_OBSERVATIONS, first.addedCount)
        assertEquals(3, first.skippedCount)
        assertEquals(CompareHereManualInputAdapter.MAX_OBSERVATIONS, first.blocks.size)
        assertEquals(0, first.firstAddedIndex)
    }
}

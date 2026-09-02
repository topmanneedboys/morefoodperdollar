package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CompareHereManualProductDraftTest {

    @Test
    fun editorPreparationPreservesOrdinaryAndBoundarySizedBlocksExactly() {
        val boundary = "x".repeat(CompareHereManualProductDraft.MAX_BLOCK_CHARS)

        assertEquals(
            listOf(
                CompareHereManualEditorBlock("  Milk\r\nCA$4.00  "),
                CompareHereManualEditorBlock(boundary)
            ),
            CompareHereManualProductDraft.prepareForEditor(
                listOf("  Milk\r\nCA$4.00  ", boundary)
            )
        )
        assertEquals(
            ManualProductObservationAdapter.MAX_BLOCK_CHARS,
            CompareHereManualProductDraft.MAX_BLOCK_CHARS
        )
    }

    @Test
    fun oversizedRestoredBlockBecomesExplicitErrorWithoutPartialEvidence() {
        val prepared =
            CompareHereManualProductDraft.prepareForEditor(
                listOf("x".repeat(CompareHereManualProductDraft.MAX_BLOCK_CHARS + 1))
            ).single()

        assertEquals("", prepared.text)
        assertEquals(CompareHereManualEditorBlockIssue.TOO_LONG, prepared.issue)
    }

    @Test
    fun editorPreparationRetainsOnlyTheExistingBoundedComparisonSlots() {
        val prepared =
            CompareHereManualProductDraft.prepareForEditor(
                List(CompareHereManualInputAdapter.MAX_OBSERVATIONS + 1) { index ->
                    "product-$index"
                }
            )

        assertEquals(CompareHereManualInputAdapter.MAX_OBSERVATIONS, prepared.size)
        assertEquals("product-0", prepared.first().text)
        assertEquals("product-31", prepared.last().text)
    }

    @Test
    fun removingAnExtraProductShrinksOnlyThatSlot() {
        val blocks = listOf("first", "second", "third", "fourth")

        assertEquals(
            listOf("first", "second", "fourth"),
            CompareHereManualProductDraft.removeAt(blocks, 2)
        )
    }

    @Test
    fun removingOneOfTheMinimumSlotsClearsItWithoutDroppingComparisonShape() {
        val blocks = listOf("first", "second")

        assertEquals(
            listOf("", "second"),
            CompareHereManualProductDraft.removeAt(blocks, 0)
        )
    }

    @Test
    fun removingAnInvalidSlotIsAClosedNoOp() {
        val blocks = listOf("first", "second", "third")

        assertEquals(blocks, CompareHereManualProductDraft.removeAt(blocks, -1))
        assertEquals(blocks, CompareHereManualProductDraft.removeAt(blocks, 3))
    }
}

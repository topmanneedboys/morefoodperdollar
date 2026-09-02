package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CompareHereManualProductDraftTest {

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

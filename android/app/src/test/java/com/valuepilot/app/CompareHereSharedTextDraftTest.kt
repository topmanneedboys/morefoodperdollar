package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereSharedTextDraftTest {

    @Test
    fun `shared text fills earliest empty slot and preserves existing entries`() {
        val existing = listOf("Milk\n4 L\nCurrent price CA$6.49", "", "Eggs")

        val result =
            CompareHereSharedTextDraft.apply(
                existingBlocks = existing,
                sharedText = "  Rice\n2 kg\nCA$4.99  "
            )

        assertTrue(result.added)
        assertEquals(1, result.addedIndex)
        assertNull(result.issue)
        assertEquals(
            listOf("Milk\n4 L\nCurrent price CA$6.49", "Rice\n2 kg\nCA$4.99", "Eggs"),
            result.blocks
        )
    }

    @Test
    fun `shared text insertion is deterministic for multiple empty slots`() {
        val result =
            CompareHereSharedTextDraft.apply(
                existingBlocks = listOf("", "Bread", ""),
                sharedText = "Coffee"
            )

        assertEquals(0, result.addedIndex)
        assertEquals(listOf("Coffee", "Bread", ""), result.blocks)
    }

    @Test
    fun `full draft does not replace existing shared comparison entries`() {
        val existing = listOf("Milk", "Eggs")

        val result =
            CompareHereSharedTextDraft.apply(
                existingBlocks = existing,
                sharedText = "Rice"
            )

        assertFalse(result.added)
        assertEquals(CompareHereSharedTextDraftIssue.NO_EMPTY_SLOT, result.issue)
        assertEquals(existing, result.blocks)
    }

    @Test
    fun `blank shared text is rejected without changing the draft`() {
        val existing = listOf("Milk", "")

        val result =
            CompareHereSharedTextDraft.apply(
                existingBlocks = existing,
                sharedText = " \n "
            )

        assertFalse(result.added)
        assertEquals(CompareHereSharedTextDraftIssue.BLANK_TEXT, result.issue)
        assertEquals(existing, result.blocks)
    }

    @Test
    fun `oversized shared text is rejected instead of truncated`() {
        val existing = listOf("", "")
        val tooLong = "x".repeat(CompareHereManualProductDraft.MAX_BLOCK_CHARS + 1)

        val result =
            CompareHereSharedTextDraft.apply(
                existingBlocks = existing,
                sharedText = tooLong
            )

        assertFalse(result.added)
        assertEquals(CompareHereSharedTextDraftIssue.TEXT_TOO_LONG, result.issue)
        assertEquals(existing, result.blocks)
    }
}

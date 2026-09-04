package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereBarcodeDraftTest {

    @Test
    fun `identity fills first empty slot and preserves every existing entry`() {
        val existing = listOf("Milk\n4 L\nCurrent price CA$6.49", "")

        val result =
            CompareHereBarcodeDraft.apply(
                existingBlocks = existing,
                displayName = "  Large brown eggs  "
            )

        assertTrue(result.added)
        assertEquals(1, result.addedIndex)
        assertNull(result.issue)
        assertEquals(
            listOf("Milk\n4 L\nCurrent price CA$6.49", "Large brown eggs"),
            result.blocks
        )
    }

    @Test
    fun `identity uses the earliest empty slot deterministically`() {
        val result =
            CompareHereBarcodeDraft.apply(
                existingBlocks = listOf("", "Rice\n2 kg\nCA$4.99", ""),
                displayName = "Basmati rice"
            )

        assertEquals(0, result.addedIndex)
        assertEquals(
            listOf("Basmati rice", "Rice\n2 kg\nCA$4.99", ""),
            result.blocks
        )
    }

    @Test
    fun `full draft does not replace existing entries`() {
        val existing = listOf("Milk", "Eggs")

        val result =
            CompareHereBarcodeDraft.apply(
                existingBlocks = existing,
                displayName = "Rice"
            )

        assertFalse(result.added)
        assertEquals(CompareHereBarcodeDraftIssue.NO_EMPTY_SLOT, result.issue)
        assertEquals(existing, result.blocks)
    }

    @Test
    fun `blank identity is rejected without changing the draft`() {
        val existing = listOf("Milk", "")

        val result =
            CompareHereBarcodeDraft.apply(
                existingBlocks = existing,
                displayName = " \n "
            )

        assertFalse(result.added)
        assertEquals(CompareHereBarcodeDraftIssue.BLANK_IDENTITY, result.issue)
        assertEquals(existing, result.blocks)
    }

    @Test
    fun `identity that cannot fit is rejected instead of truncated`() {
        val existing = listOf("", "")
        val tooLong = "x".repeat(CompareHereManualProductDraft.MAX_BLOCK_CHARS + 1)

        val result =
            CompareHereBarcodeDraft.apply(
                existingBlocks = existing,
                displayName = tooLong
            )

        assertFalse(result.added)
        assertEquals(CompareHereBarcodeDraftIssue.IDENTITY_TOO_LONG, result.issue)
        assertEquals(existing, result.blocks)
    }
}

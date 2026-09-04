package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareToValuePilotPresentationTest {

    @Test
    fun `ready share preserves bounded text and enables explicit compare handoff`() {
        val state = ShareToValuePilotUiProjector.project("  Milk\n4 L\nCA$6.49  ")

        assertEquals(ShareToValuePilotStatus.READY, state.status)
        assertEquals("Milk\n4 L\nCA$6.49", state.sharedText)
        assertTrue(state.openComparisonEnabled)
    }

    @Test
    fun `empty or missing share stays unavailable`() {
        listOf(null, "", " \n ").forEach { raw ->
            val state = ShareToValuePilotUiProjector.project(raw)

            assertEquals(ShareToValuePilotStatus.EMPTY, state.status)
            assertEquals(null, state.sharedText)
            assertFalse(state.openComparisonEnabled)
        }
    }

    @Test
    fun `oversized share is rejected without truncation`() {
        val state =
            ShareToValuePilotUiProjector.project(
                "x".repeat(ShareToValuePilotInput.MAX_CHARS + 1)
            )

        assertEquals(ShareToValuePilotStatus.TOO_LARGE, state.status)
        assertEquals(null, state.sharedText)
        assertFalse(state.openComparisonEnabled)
    }

    @Test
    fun `maximum supported share is accepted`() {
        val text = "x".repeat(ShareToValuePilotInput.MAX_CHARS)

        val result = ShareToValuePilotInput.validate(text)

        assertTrue(result.accepted)
        assertEquals(text, result.text)
    }
}

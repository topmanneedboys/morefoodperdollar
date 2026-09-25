package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareToValuePilotImageInputTest {

    @Test
    fun `content uri is accepted without rewriting`() {
        val result = ShareToValuePilotImageInput.validate("  content://photos/42  ")

        assertTrue(result.accepted)
        assertEquals("content://photos/42", result.uri)
        assertEquals("content", ShareToValuePilotImageInput.schemeOf(requireNotNull(result.uri)))
    }

    @Test
    fun `file uri and malformed shape stay unavailable`() {
        listOf("file:///tmp/photo.jpg", "https://example.test/photo.jpg", "content:", "content://").forEach { raw ->
            val result = ShareToValuePilotImageInput.validate(raw)

            assertFalse(result.accepted)
            assertEquals(null, result.uri)
        }
    }

    @Test
    fun `oversized and control character uri are rejected without truncation`() {
        val oversized = ShareToValuePilotImageInput.validate(
            "content://photos/" + "x".repeat(ShareToValuePilotImageInput.MAX_URI_CHARS)
        )
        val control = ShareToValuePilotImageInput.validate("content://photos/4\n2")

        assertEquals(ShareToValuePilotImageInputIssue.TOO_LONG, oversized.issue)
        assertEquals(ShareToValuePilotImageInputIssue.CONTROL_CHARACTER, control.issue)
    }
}

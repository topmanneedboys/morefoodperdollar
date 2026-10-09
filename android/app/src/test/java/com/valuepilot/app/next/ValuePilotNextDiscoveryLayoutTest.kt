package com.valuepilot.app.next

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextDiscoveryLayoutTest {

    @Test
    fun `discovery summaries stack for narrow widths and larger system text`() {
        assertTrue(usesCompactDiscoveryContent(360.dp, fontScale = 1f))
        assertTrue(usesCompactDiscoveryContent(480.dp, fontScale = 1.2f))
        assertFalse(usesCompactDiscoveryContent(480.dp, fontScale = 1.15f))
    }
}

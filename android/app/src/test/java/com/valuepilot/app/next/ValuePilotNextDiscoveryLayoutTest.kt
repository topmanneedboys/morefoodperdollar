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

    @Test
    fun `home quick actions share a row at phone width and stack for accessibility`() {
        assertTrue(stacksHomeQuickActions(320.dp, fontScale = 1f))
        assertFalse(stacksHomeQuickActions(360.dp, fontScale = 1f))
        assertTrue(stacksHomeQuickActions(360.dp, fontScale = 1.3f))
    }
}

package com.valuepilot.app.next

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextTopBarLayoutTest {

    @Test
    fun `top bar compacts for narrow content or larger system text`() {
        assertTrue(usesCompactJourneyTopBar(360.dp, fontScale = 1f))
        assertTrue(usesCompactJourneyTopBar(420.dp, fontScale = 1.2f))
        assertFalse(usesCompactJourneyTopBar(420.dp, fontScale = 1.15f))
    }
}

package com.valuepilot.app.next

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextObservedPriceConfirmationLayoutTest {

    @Test
    fun `confirmation fields stack on narrow screens or larger system text`() {
        assertTrue(usesCompactObservedPriceConfirmationFields(360.dp, fontScale = 1f))
        assertTrue(usesCompactObservedPriceConfirmationFields(480.dp, fontScale = 1.2f))
        assertFalse(usesCompactObservedPriceConfirmationFields(480.dp, fontScale = 1.15f))
    }
}

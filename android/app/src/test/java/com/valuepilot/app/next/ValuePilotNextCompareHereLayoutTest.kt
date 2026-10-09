package com.valuepilot.app.next

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextCompareHereLayoutTest {

    @Test
    fun `price basis choices stack on compact screens or larger system text`() {
        assertTrue(usesCompactComparePriceBasisChoices(360.dp, fontScale = 1f))
        assertTrue(usesCompactComparePriceBasisChoices(480.dp, fontScale = 1.2f))
        assertFalse(usesCompactComparePriceBasisChoices(480.dp, fontScale = 1.15f))
    }
}

package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceWatchTargetParserTest {

    @Test
    fun `target parser keeps exact non negative money and supports clearing`() {
        val parsed = PriceWatchTargetParser.parse("5.49", "CAD")
        assertTrue(parsed.accepted)
        assertEquals(549L, parsed.target?.minorUnits)
        assertEquals("CAD", parsed.target?.currencyCode)

        val cleared = PriceWatchTargetParser.parse(" ", " ")
        assertTrue(cleared.accepted)
        assertNull(cleared.target)
    }

    @Test
    fun `target parser rejects partial malformed and negative input`() {
        assertFalse(PriceWatchTargetParser.parse("5.49", "").accepted)
        assertFalse(PriceWatchTargetParser.parse("not-money", "CAD").accepted)
        assertFalse(PriceWatchTargetParser.parse("-1.00", "CAD").accepted)
    }
}

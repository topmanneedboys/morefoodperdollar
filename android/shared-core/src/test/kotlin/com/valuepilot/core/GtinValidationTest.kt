package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinValidationTest {

    @Test
    fun acceptsValidGtinShapesWithCorrectCheckDigit() {
        assertTrue(GtinValidation.isValid("96385074"))
        assertTrue(GtinValidation.isValid("036000291452"))
        assertTrue(GtinValidation.isValid("4006381333931"))
        assertTrue(GtinValidation.isValid("10012345678902"))
    }

    @Test
    fun rejectsBadShapeCharactersAndCheckDigit() {
        assertFalse(GtinValidation.isValid("12345"))
        assertFalse(GtinValidation.isValid("03600029145X"))
        assertFalse(GtinValidation.isValid("036000291453"))
        assertFalse(GtinValidation.isValid("4006381333932"))
    }

    @Test
    fun canonicalizesEquivalentLeadingZeroRepresentations() {
        val canonical = "0036000291452"

        assertEquals(canonical, GtinValidation.canonicalOrNull("036000291452"))
        assertEquals(canonical, GtinValidation.canonicalOrNull(canonical))
        assertEquals(canonical, GtinValidation.canonicalOrNull("00036000291452"))
    }

    @Test
    fun keepsEan8AndNonZeroIndicatorGtin14Distinct() {
        assertEquals("96385074", GtinValidation.canonicalOrNull("96385074"))
        assertEquals(
            "10012345678902",
            GtinValidation.canonicalOrNull("10012345678902")
        )
    }

    @Test
    fun canonicalizationNeverRepairsInvalidGtin() {
        assertNull(GtinValidation.canonicalOrNull("036000291453"))
        assertNull(GtinValidation.canonicalOrNull("not-a-gtin"))
    }
}

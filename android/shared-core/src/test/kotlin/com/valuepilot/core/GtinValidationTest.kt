package com.valuepilot.core

import org.junit.Assert.assertFalse
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
}

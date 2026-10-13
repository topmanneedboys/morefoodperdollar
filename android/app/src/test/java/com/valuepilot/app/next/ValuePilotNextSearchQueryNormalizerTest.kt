package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Test

class ValuePilotNextSearchQueryNormalizerTest {
    @Test
    fun `normalizes reviewed English grocery terms without changing display input`() {
        assertEquals("frutilla", ValuePilotNextSearchQueryNormalizer.canonical(" strawberry "))
        assertEquals("frutilla", ValuePilotNextSearchQueryNormalizer.canonical("strawberries"))
        assertEquals("arroz", ValuePilotNextSearchQueryNormalizer.canonical("RICE"))
        assertEquals("leche entera", ValuePilotNextSearchQueryNormalizer.canonical(" leche entera "))
    }

    @Test
    fun `passes unknown words through`() {
        assertEquals("rare imported product", ValuePilotNextSearchQueryNormalizer.canonical(" rare imported product "))
    }
}

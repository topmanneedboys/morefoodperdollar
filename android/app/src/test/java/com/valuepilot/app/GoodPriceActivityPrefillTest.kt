package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoodPriceActivityPrefillTest {

    @Test
    fun `prefill trims a bounded product name without resolving it`() {
        assertEquals(
            "Whole milk",
            GoodPriceActivityPrefill.sanitize("  Whole milk  ")
        )
    }

    @Test
    fun `blank and oversized external values are ignored`() {
        assertNull(GoodPriceActivityPrefill.sanitize("  \n"))
        assertNull(
            GoodPriceActivityPrefill.sanitize(
                "x".repeat(GoodPriceActivityPrefill.MAX_PRODUCT_NAME_LENGTH + 1)
            )
        )
    }
}

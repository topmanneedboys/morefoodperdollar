package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeScanResolutionTest {

    @Test
    fun `one checksum valid gtin is accepted while unrelated codes are ignored`() {
        val result =
            BarcodeScanResolutionResolver.resolve(
                listOf("https://example.invalid", " 036000291452 ")
            )

        assertTrue(result.accepted)
        assertEquals("036000291452", result.rawGtin)
        assertEquals("0036000291452", result.canonicalGtin)
        assertEquals(null, result.issue)
    }

    @Test
    fun `equivalent upc and ean representations remain one deterministic identity`() {
        val result =
            BarcodeScanResolutionResolver.resolve(
                listOf("0036000291452", "036000291452")
            )

        assertTrue(result.accepted)
        assertEquals("0036000291452", result.rawGtin)
        assertEquals("0036000291452", result.canonicalGtin)
    }

    @Test
    fun `multiple distinct valid gtins fail closed`() {
        val result =
            BarcodeScanResolutionResolver.resolve(
                listOf("036000291452", "4006381333931")
            )

        assertFalse(result.accepted)
        assertEquals(BarcodeScanIssue.MULTIPLE_GTINS, result.issue)
    }

    @Test
    fun `invalid and blank decoder values do not become identity`() {
        val result =
            BarcodeScanResolutionResolver.resolve(
                listOf("", "036000291453", "not-a-gtin")
            )

        assertFalse(result.accepted)
        assertEquals(BarcodeScanIssue.NO_VALID_GTIN, result.issue)
    }
}

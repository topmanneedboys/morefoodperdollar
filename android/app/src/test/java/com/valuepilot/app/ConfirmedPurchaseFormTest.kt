package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedPurchaseFormTest {

    @Test
    fun `blank optional facts remain unknown`() {
        val result =
            ConfirmedPurchaseFormParser.parse(
                ConfirmedPurchaseFormInput("", "", "", "", "")
            )

        assertTrue(result.accepted)
        val values = requireNotNull(result.values)
        assertNull(values.quantity)
        assertNull(values.amountPaid)
        assertNull(values.purchaseDateIso)
        assertNull(values.store)
    }

    @Test
    fun `exact count price date and store are parsed without floating point`() {
        val result =
            ConfirmedPurchaseFormParser.parse(
                ConfirmedPurchaseFormInput("3", "12.34", "cad", "2026-09-28", "Local market")
            )

        assertTrue(result.accepted)
        val values = requireNotNull(result.values)
        assertEquals(3_000_000L, requireNotNull(values.quantity).amountMicros)
        assertEquals(1234L, requireNotNull(values.amountPaid).minorUnits)
        assertEquals("CAD", requireNotNull(values.amountPaid).currencyCode)
        assertEquals("2026-09-28", values.purchaseDateIso)
        assertEquals("Local market", requireNotNull(values.store).displayName)
    }

    @Test
    fun `partial price pair and malformed fields fail without creating facts`() {
        val partialPrice =
            ConfirmedPurchaseFormParser.parse(
                ConfirmedPurchaseFormInput("", "4.00", "", "", "")
            )
        assertFalse(partialPrice.accepted)
        assertEquals(ConfirmedPurchaseFormField.AMOUNT, requireNotNull(partialPrice.failure).field)

        val malformedDate =
            ConfirmedPurchaseFormParser.parse(
                ConfirmedPurchaseFormInput("0", "", "", "2026-02-30", "")
            )
        assertFalse(malformedDate.accepted)
        assertEquals(ConfirmedPurchaseFormField.QUANTITY, requireNotNull(malformedDate.failure).field)

        val invalidDate =
            ConfirmedPurchaseFormParser.parse(
                ConfirmedPurchaseFormInput("1", "", "", "2026-02-30", "")
            )
        assertFalse(invalidDate.accepted)
        assertEquals(ConfirmedPurchaseFormField.DATE, requireNotNull(invalidDate.failure).field)
        assertNotNull(invalidDate.failure)
    }
}

package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EvidenceFingerprintsTest {

    @Test
    fun moneyFingerprintPreservesCurrencyScaleAndExactMinorUnits() {
        assertEquals(
            "money:CAD:2:499",
            EvidenceFingerprints.money(Money.parse("4.99", "CAD"))
        )
        assertNotEquals(
            EvidenceFingerprints.money(Money.parse("4.99", "CAD")),
            EvidenceFingerprints.money(Money.parse("4.99", "USD"))
        )
    }

    @Test
    fun quantityFingerprintPreservesBaseUnitAndExactMicros() {
        assertEquals(
            "quantity:GRAM:1000000000",
            EvidenceFingerprints.quantity(
                NormalizedQuantity(
                    amountMicros = 1_000_000_000L,
                    unit = BaseUnit.GRAM
                )
            )
        )
    }
}

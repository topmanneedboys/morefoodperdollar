package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalModelsTest {
    @Test fun eggsHaveDeterministicPricePerItem() {
        val rate = DeterministicValueMath.pricePerBaseUnit(
            Offer(Money.parse("11.65", "CAD")), QuantityNormalization.count(30)
        )
        assertEquals(388_333L, rate.currencyMicrosPerUnit)
        assertEquals(RateUnit.ITEM, rate.unit)
    }

    @Test fun milkHasDeterministicPricePerLitre() {
        val perLitre = DeterministicValueMath.pricePerBaseUnit(
            Offer(Money.parse("5.49", "CAD")), QuantityNormalization.millilitres(1_900)
        )
        assertEquals(2_889_474L, perLitre.currencyMicrosPerUnit)
        assertEquals(RateUnit.LITRE, perLitre.unit)
    }

    @Test fun poundsAndMultipacksNormalizeExactly() {
        assertEquals(1_360_777_110L, QuantityNormalization.pounds(3).amountMicros)
        assertEquals(3_000_000_000L, QuantityNormalization.multipack(6, QuantityNormalization.grams(500)).amountMicros)
    }

    @Test fun memberPriceAndBogoAreExact() {
        val offer = Offer(
            current = Money.parse("11.65", "CAD"),
            member = Money.parse("9.81", "CAD"),
            promotion = PromotionTerms("Buy one get one", receivedUnits = 2, paidUnits = 1)
        )
        val rate = DeterministicValueMath.pricePerBaseUnit(offer, QuantityNormalization.count(30), useMemberPrice = true)
        assertEquals(163_500L, rate.currencyMicrosPerUnit)
    }

    @Test fun currencyMismatchAndExcessPrecisionFailClosed() {
        assertFailsWith<IllegalArgumentException> { Offer(Money.parse("1.00", "CAD"), member = Money.parse("1.00", "USD")) }
        assertFailsWith<IllegalArgumentException> { Money.parse("1.001", "CAD") }
    }

    @Test fun legacyMoneyBoundaryBecomesExactAndAddsWithoutArtifacts() {
        val first = Money.fromMajorUnits(10.10, "CAD")
        val second = Money.fromMajorUnits(2.20, "CAD")
        assertEquals(1_010L, first.minorUnits)
        assertEquals(1_230L, (first + second).minorUnits)
    }

    @Test fun productIdentityDistinguishesQuantityAndOfferWithoutDisplayFormatting() {
        val base = ProductIdentityKey("large eggs", 1_165, 981, "COUNT", 30_000_000, "none")
        assertEquals(base, base.copy())
        assertEquals(base.stableText(), base.copy().stableText())
        kotlin.test.assertNotEquals(base, base.copy(quantityMicros = 12_000_000))
        kotlin.test.assertNotEquals(base, base.copy(currentPriceMinor = 1_200))
    }

    @Test fun productMatchingIsSeparateAndRejectsUnrelatedProducts() {
        val eggs = ProductMatchEvidence("large eggs", 1_165, 981, "COUNT", 30_000_000)
        val unrelated = ProductMatchEvidence("organic bananas", 1_165, 981, "COUNT", 30_000_000)
        assertEquals(false, ProductMatching.compare(eggs, unrelated).matches)
        assertEquals(true, ProductMatching.compare(eggs, eggs).matches)
    }

    @Test fun noOpSemanticEnrichmentNeverBlocksExactWork() {
        assertEquals(SemanticSignals(), NoSemanticEnricher.enrich("Large Eggs 30 ct $11.65"))
    }
}

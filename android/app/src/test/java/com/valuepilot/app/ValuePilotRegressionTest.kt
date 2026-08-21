package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ValuePilotRegressionTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLocalModel() = TestModelLoader.load()
    }

    @Test
    fun memberAndPreviousPricePhrasesStayOutOfCanonicalName() {
        val item = ValueEngine.analyze(
            "Large Eggs\n30 ct\n\$11.65\nMember price \$9.81\nPrevious price was \$12.49\nfor members"
        )!!

        assertEquals("Large Eggs", item.name)
        assertEquals("large eggs", ValueEngine.canonicalName(item.name))
        assertEquals(11.65, item.offer.currentPrice, .001)
        assertEquals(9.81, item.offer.memberPrice!!, .001)
        assertEquals(12.49, item.offer.previousPrice!!, .001)
        assertTrue(item.offer.membershipRequired)
        assertFalse(item.name.contains("member", ignoreCase = true))
        assertFalse(item.name.contains("previous price", ignoreCase = true))
    }

    @Test
    fun memberSuffixIsClassifiedAsOfferMetadata() {
        val item = ValueEngine.analyze("Large Eggs\n30 ct\n\$11.65\n\$9.81 for members")!!

        assertEquals("Large Eggs", item.name)
        assertEquals(11.65, item.offer.currentPrice, .001)
        assertEquals(9.81, item.offer.memberPrice!!, .001)
    }

    @Test
    fun milkQueryExcludesBanana() {
        val milk = ValueEngine.analyze("Whole Milk\n2 L\n\$5.49")!!
        val banana = ValueEngine.analyze("Organic Bananas\n1 kg\n\$3.99")!!
        val filtered = ValueEngine.filterItems(
            items = listOf(milk, banana),
            foodOnly = false,
            query = "milk"
        )

        assertEquals(listOf(milk), filtered)
        assertFalse(filtered.contains(banana))
    }

    @Test
    fun sameNameDifferentSizeProductsRemainDistinct() {
        val twelve = ValueEngine.analyze("Large Eggs\n12 ct\n\$5.49")!!
        val thirty = ValueEngine.analyze("Large Eggs\n30 ct\n\$11.65")!!
        val deduped = ValueEngine.dedupe(listOf(twelve, thirty))

        assertEquals(2, deduped.size)
        assertNotEquals(twelve.stableId, thirty.stableId)
        assertEquals(setOf(12.0, 30.0), deduped.mapNotNull { it.quantity?.amountBase }.toSet())
    }

    @Test
    fun unrelatedPhrasesCannotBecomeFallbackNames() {
        assertEquals("Unnamed item", ValueEngine.name("for members\nprevious price was for members\n\$9.81"))
        assertNull(ValueEngine.analyze("for members\nprevious price was for members"))
    }

    @Test
    fun midpointQuantityMathIsClearlyMarkedAsEstimate() {
        val item = ValueEngine.analyze("Chicken Breasts\n1.0-1.5 kg\n\$12.00")!!
        val ranked = ValueEngine.rank(listOf(item), RankMode.MASS).single()

        assertEquals("Estimate", ranked.exactnessLabel)
    }

    @Test
    fun displayedUnitRateDoesNotReplaceTheProductPrice() {
        val item = ValueEngine.analyze("Large Eggs\n30 ct\n\$0.39/item\n\$11.65\nIn stock")!!

        assertEquals(11.65, item.offer.currentPrice, .001)
        assertEquals(.388, item.pricePerUnit!!, .002)
    }
}

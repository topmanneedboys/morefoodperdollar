package com.valuepilot.app

import org.junit.Assert.*
import org.junit.Test

class ValueEngineTest {
    @Test fun groceryRank() {
        val a = ValueEngine.analyze("Honeycrisp apples\n3 lb\n$5.99")!!
        val b = ValueEngine.analyze("Gala apples\n1.5 kg\n$4.49")!!
        val r = ValueEngine.rank(listOf(a,b), RankMode.MASS)
        assertTrue(r[0].item.name.contains("Gala"))
    }

    @Test fun bogoCalories() {
        val x = ValueEngine.analyze("Whopper\n660 cal\n$8.99\nBuy one get one free")!!
        assertEquals("bogo", x.promotion.type)
        assertEquals(1320.0/8.99, x.caloriesPerDollar!!, 0.01)
    }

    @Test fun pizzaArea() {
        val p12 = ValueEngine.analyze("12 inch pizza $12.00")!!
        val p14 = ValueEngine.analyze("14 inch pizza $14.00")!!
        assertTrue(p14.quantity!!.amountBase / p12.quantity!!.amountBase > 1.35)
    }

    @Test fun savingsAmountIsNotPrice() {
        val x = ValueEngine.analyze("Cereal 500 g Save $2 Now $8.99 Regular $10.99")!!
        assertEquals(8.99, x.price, 0.001)
    }

    @Test fun bogoHalfOffIsNotFree() {
        val x = ValueEngine.analyze("Burger 700 cal $10 BOGO 50% off")!!
        assertEquals("bogo-percent", x.promotion.type)
        assertEquals(2.0/1.5, x.promotion.receivedMultiplier, 0.001)
    }

    @Test fun portionFallback() {
        val a = ValueEngine.analyze("Small fries $3.00")!!
        val b = ValueEngine.analyze("Large fries $4.00")!!
        val ranked = ValueEngine.rank(listOf(a,b), RankMode.PORTION)
        assertTrue(ranked.first().item.name.contains("Large"))
    }
}

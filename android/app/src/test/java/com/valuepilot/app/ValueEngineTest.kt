package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ValueEngineTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLocalModel() {
            TestModelLoader.load()
        }
    }

    @Test
    fun groceryRank() {
        val first = ValueEngine.analyze("Honeycrisp apples\n3 lb\n$5.99")!!
        val second = ValueEngine.analyze("Gala apples\n1.5 kg\n$4.49")!!
        val ranked = ValueEngine.rank(listOf(first, second), RankMode.MASS)
        assertTrue(ranked[0].item.name.contains("Gala"))
    }

    @Test
    fun bogoCalories() {
        val item = ValueEngine.analyze("Whopper\n660 cal\n$8.99\nBuy one get one free")!!
        assertEquals("bogo", item.promotion.type)
        assertEquals(1320.0 / 8.99, item.caloriesPerDollar!!, 0.01)
    }

    @Test
    fun pizzaAreaUsesSquaredDiameter() {
        val pizza12 = ValueEngine.analyze("12 inch pizza $12.00")!!
        val pizza14 = ValueEngine.analyze("14 inch pizza $14.00")!!
        assertTrue(pizza14.quantity!!.amountBase / pizza12.quantity!!.amountBase > 1.35)
    }

    @Test
    fun savingsAmountIsNotPrice() {
        val item = ValueEngine.analyze("Cereal 500 g Save $2 Now $8.99 Regular $10.99")!!
        assertEquals(8.99, item.price, 0.001)
    }

    @Test
    fun bogoHalfOffIsNotFreeAndRequiresTwoItems() {
        val item = ValueEngine.analyze("Burger 700 cal $10 BOGO 50% off")!!
        assertEquals("bogo-percent", item.promotion.type)
        assertEquals(2.0 / 1.5, item.promotion.receivedMultiplier, 0.001)
        assertEquals(15.0, ValueEngine.minimumSpend(item), 0.001)
    }

    @Test
    fun secondItemDealUsesRealMinimumSpend() {
        val item = ValueEngine.analyze("Chicken dinner $12.00 second item 50% off")!!
        assertEquals("bogo-percent", item.promotion.type)
        assertEquals(18.0, ValueEngine.minimumSpend(item), 0.001)
    }

    @Test
    fun portionFallback() {
        val small = ValueEngine.analyze("Small fries $3.00")!!
        val large = ValueEngine.analyze("Large fries $4.00")!!
        val ranked = ValueEngine.rank(listOf(small, large), RankMode.PORTION)
        assertTrue(ranked.first().item.name.contains("Large"))
    }

    @Test
    fun expandedUnitsNormalize() {
        assertEquals(473.176473, ValueEngine.quantity("2 cups soup")!!.amountBase, 0.001)
        assertEquals(12.0, ValueEngine.quantity("one dozen eggs")!!.amountBase, 0.001)
        assertEquals(2130.0, ValueEngine.quantity("6 x 355 mL")!!.amountBase, 0.001)
    }

    @Test
    fun internationalPricesParseWithoutCurrencyCollapse() {
        val canadian = ValueEngine.analyze("Rice C$12.49 5 kg")!!
        val australian = ValueEngine.analyze("Rice A$13.49 5 kg")!!
        val european = ValueEngine.analyze("Rice 1 kg €1.234,56")!!
        assertEquals("CAD", canadian.currency)
        assertEquals("AUD", australian.currency)
        assertEquals(1234.56, european.price, 0.001)
    }

    @Test
    fun localAiClassifiesFoodAndRejectsObviousNonFood() {
        val chicken = LocalFoodModel.predict("spicy chicken shawarma platter")
        val cable = LocalFoodModel.predict("USB charging cable electronics")
        assertTrue(chicken.available)
        assertEquals("chicken", chicken.category)
        assertTrue(chicken.foodConfidence > .5)
        assertEquals("nonfood", cable.category)
        assertTrue(cable.foodConfidence < .22)
    }

    @Test
    fun localAiAddsBoundedMeatFallback() {
        val chicken = ValueEngine.analyze("Large grilled chicken platter $14.99", semanticEnricher = LocalModelSemanticEnricher)!!
        assertTrue(chicken.portion?.source == "local-ai")
        assertTrue(chicken.meatPointsPerDollar != null)
        assertTrue(chicken.ai.meatRatio in 0.0..1.0)
    }

    @Test
    fun budgetAndDietFiltersUseMinimumSpend() {
        val chicken = ValueEngine.analyze("Chicken bowl $10.00", semanticEnricher = LocalModelSemanticEnricher)!!
        val pork = ValueEngine.analyze("BBQ pork plate $9.00", semanticEnricher = LocalModelSemanticEnricher)!!
        val deal = ValueEngine.analyze("Beef burger $8.00 second item 50% off", semanticEnricher = LocalModelSemanticEnricher)!!
        val filtered = ValueEngine.filterItems(listOf(chicken, pork, deal), maxPrice = 11.0, foodOnly = true, excludePork = true)
        assertEquals(listOf(chicken), filtered)
        assertFalse(filtered.contains(pork))
        assertFalse(filtered.contains(deal))
    }

    @Test
    fun nameSanitizerPreservesLegitimateRegPrefixes() {
        val pizza = ValueEngine.analyze(
            "reginos pizza\n18 in\n${'$'}24"
        )!!

        assertEquals(
            "reginos pizza",
            pizza.name
        )

        assertEquals(
            Quantity.Kind.PIZZA_AREA_SQIN,
            pizza.quantity!!.kind
        )

        assertEquals(
            18.0,
            pizza.quantity!!.diameterIn!!,
            0.001
        )

        assertEquals(
            24.0,
            pizza.offer.currentPrice,
            0.001
        )

        val regal = ValueEngine.analyze(
            "Regal apples\n3 lb\n${'$'}5.99"
        )!!

        assertEquals(
            "Regal apples",
            regal.name
        )

        val regional = ValueEngine.analyze(
            "Regional chicken\n1 kg\n${'$'}12.00"
        )!!

        assertEquals(
            "Regional chicken",
            regional.name
        )
    }

    @Test
    fun regularPriceAbbreviationStillWorksAfterRegPrefixFix() {
        val item = ValueEngine.analyze(
            "Reginos pizza\n18 in\nNow ${'$'}24.00\nReg. ${'$'}29.99"
        )!!

        assertEquals(
            "Reginos pizza",
            item.name
        )

        assertEquals(
            24.0,
            item.offer.currentPrice,
            0.001
        )

        assertEquals(
            29.99,
            item.offer.regularPrice!!,
            0.001
        )
    }
    @Test
    fun unicodeNamesKeepStableKeys() {
        assertTrue(ValueEngine.canonicalName("Crème brûlée $8.00").contains("crème"))
    }

    @Test
    fun deterministicParserWorksWithSemanticEnrichmentDisabled() {
        val item = ValueEngine.analyze("Large Eggs\n30 ct\n$11.65")!!
        assertEquals("Large Eggs", item.name)
        assertEquals(30.0, item.quantity!!.amountBase, 0.0)
        assertEquals(11.65, item.offer.currentPrice, 0.0)
        assertFalse(item.ai.available)
    }
}

package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingBasketSurfaceBoundaryTest {

    @Test
    fun basketMechanicallyRendersImmutableStateAndEmitsOnlyTypedNavigation() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readText()

        assertTrue(source.contains("fun render(state: PracticalShoppingBasketRenderState)"))
        assertTrue(source.contains("planResult.render(state.result)"))
        assertTrue(source.contains("sampleNotice.text = state.sampleNotice"))
        assertTrue(source.contains("PracticalShoppingBasketUiAction.OpenHome"))

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy(",
            "Money.parse",
            "knownBasketCost",
            "SecondStopDecision",
            "ProductionCurrentPrice",
            "affiliate",
            "providerEconomics"
        ).forEach { forbidden ->
            assertFalse(
                "Basket View must not gain shopping authority through $forbidden",
                source.contains(forbidden, ignoreCase = true)
            )
        }
    }

    @Test
    fun homeAndBasketShareOneUiReadyPlanResultRenderer() {
        val home = source("PracticalShoppingHomeSurfaceView.kt").readText()
        val basket = source("PracticalShoppingBasketSurfaceView.kt").readText()
        val result = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        assertTrue(home.contains("PracticalShoppingPlanResultSurfaceView(context)"))
        assertTrue(home.contains("resultContainer.render(state.result)"))
        assertTrue(basket.contains("PracticalShoppingPlanResultSurfaceView(context)"))
        assertTrue(result.contains("fun render(state: PracticalShoppingUiState?)"))

        assertFalse(home.contains("private fun primaryCard"))
        assertFalse(home.contains("private fun secondStopCard"))
        assertFalse(basket.contains("private fun primaryCard"))
        assertFalse(basket.contains("private fun secondStopCard"))
    }

    @Test
    fun sharedPlanViewFormatsNoMoneyAndMakesNoDecision() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy",
            "Money",
            "BigDecimal",
            "PrimaryShoppingPlanKind",
            "SecondStopDecision",
            "formatMoney",
            "compareTo("
        ).forEach { forbidden ->
            assertFalse(
                "Shared plan View must render projected strings only; found $forbidden",
                source.contains(forbidden)
            )
        }
    }

    @Test
    fun sharedPlanViewUsesProjectedCompletenessForCautionStyling() {
        val source = source("PracticalShoppingPlanResultSurfaceView.kt").readText()

        assertTrue(source.contains("practicalShoppingPrimaryCardStyle(state)"))
        assertTrue(source.contains("style.backgroundColor"))
        assertTrue(source.contains("style.strokeColor"))
        assertTrue(source.contains("style.accentColor"))
        assertTrue(source.contains("state.missingItemsText == null"))
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/$name"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}

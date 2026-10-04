package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.ShoppingDecisionBasketLine
import com.valuepilot.core.ShoppingDecisionEngine
import com.valuepilot.core.ShoppingDecisionInput
import com.valuepilot.core.ShoppingDecisionStoreScenario
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.TripDistanceBasis
import com.valuepilot.core.TripOriginKind
import com.valuepilot.core.TripOriginReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerPresentationTest {

    @Test
    fun `empty basket is disclosed before trip inputs`() {
        val state = TripPlannerProjector.project(
            state = TripPlannerState(),
            basket = basket(planned = 0, privateItems = 0, unknown = 0, revision = "empty"),
            directoryAvailable = true
        )
        assertEquals(TripPlannerSurfaceMode.EMPTY_BASKET, state.mode)
        assertFalse(state.canCalculate)
        assertTrue(state.guidance.contains("Basket"))
    }

    @Test
    fun `unresolved basket items still count as the current basket`() {
        val state = TripPlannerProjector.project(
            state = TripPlannerState(),
            basket = basket(planned = 0, privateItems = 0, unknown = 2, revision = "unknown-2"),
            directoryAvailable = false
        )
        assertEquals(TripPlannerSurfaceMode.NEEDS_INPUT, state.mode)
        assertTrue(state.basketText.contains("2 unresolved"))
    }

    @Test
    fun `complete assumptions calculate current basket scenario and keep savings unknown`() {
        val store = TripStoreSelection(
            ShoppingStoreKey("directory:store:1"),
            "Market",
            sourceLabel = "signed location",
            latitudeE7 = 435000000,
            longitudeE7 = -793800000
        )
        val state = TripPlannerProjector.project(
            state = TripPlannerState(
                destination = store,
                origin = TripOriginReference(TripOriginKind.MANUAL, "Home"),
                drivingDistanceMetres = 10_000L,
                distanceBasis = TripDistanceBasis.ROUND_TRIP,
                fuelConsumptionMilliLitresPer100Km = 7_500L,
                fuelPrice = Money.parse("1.80", "CAD")
            ),
            basket = basket(planned = 2, privateItems = 1, unknown = 0, revision = "r1"),
            directoryAvailable = true
        )
        assertEquals(TripPlannerSurfaceMode.CALCULATED, state.mode)
        assertTrue(state.canCalculate)
        assertTrue(state.tripCostText.contains("2.70 CAD"))
        assertTrue(state.tripCostHeadline.contains("2.70 CAD"))
        assertTrue(state.tripCostDetail.contains("manual"))
        assertTrue(state.breakEvenText.contains("2.70 CAD"))
        assertTrue(state.evidenceChips.any { it.label == "Current unknown" })
        assertTrue(state.destinationReady)
        assertTrue(state.originReady)
        assertTrue(state.assumptionsReady)
        assertTrue(state.savingsText.contains("UNKNOWN"))
        assertFalse(state.savingsText.contains("$"))
        assertTrue(state.directionsAvailable)
    }

    @Test
    fun `basket revision and counts change the projection without copying basket data`() {
        val base = basket(planned = 1, privateItems = 0, unknown = 0, revision = "r1")
        val changed = basket(planned = 2, privateItems = 0, unknown = 0, revision = "r2")
        val state = TripPlannerState()
        val first = TripPlannerProjector.project(state, base, directoryAvailable = false)
        val second = TripPlannerProjector.project(state, changed, directoryAvailable = false)
        assertTrue(first.basketText.contains("1 planned"))
        assertTrue(second.basketText.contains("2 planned"))
        assertEquals("r1", first.basketRevision)
        assertEquals("r2", second.basketRevision)
        assertTrue(second.guidance.contains("manual route"))
    }

    @Test
    fun `decision evidence is inspectable without claiming a live price`() {
        val store = ShoppingStoreKey("directory:store:decision")
        val decision =
            ShoppingDecisionEngine.evaluate(
                ShoppingDecisionInput(
                    basket =
                        listOf(
                            ShoppingDecisionBasketLine(
                                lineId = "bread",
                                productIdentityKey = ShoppingItemKey("bread"),
                                displayName = "Bread",
                                requestedQuantity = NormalizedQuantity(1_000_000L, com.valuepilot.core.BaseUnit.COUNT)
                            )
                        ),
                    stores = listOf(ShoppingDecisionStoreScenario(store))
                )
            ).scenarios.single()
        val projected =
            TripPlannerProjector.project(
                state = TripPlannerState(destination = TripStoreSelection(store, "Market", sourceLabel = "signed")),
                basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "decision"),
                directoryAvailable = true,
                decision = decision
            )

        assertTrue(projected.decisionText.contains("unknown"))
        assertTrue(projected.decisionText.contains("authorized live offer"))
        assertTrue(projected.decisionHeadline.contains("evidence"))
        assertTrue(projected.decisionDetail.contains("authorized current"))
        assertTrue(projected.evidenceChips.isNotEmpty())
    }

    @Test
    fun `unreadable private draft is unavailable and never presented as zero`() {
        val state = TripPlannerProjector.project(
            state = null,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "r1"),
            directoryAvailable = false,
            unavailable = true
        )
        assertEquals(TripPlannerSurfaceMode.UNAVAILABLE, state.mode)
        assertTrue(state.tripCostText.contains("unknown"))
        assertTrue(state.breakEvenText.contains("unavailable"))
        assertTrue(state.evidenceChips.any { it.label == "Current unknown" && it.count == 1 })
        assertFalse(state.canCalculate)
    }

    private fun basket(planned: Int, privateItems: Int, unknown: Int, revision: String) =
        TripPlannerBasketSnapshot(planned, privateItems, unknown, revision)
}

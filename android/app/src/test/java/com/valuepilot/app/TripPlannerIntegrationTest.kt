package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingDecisionEngine
import com.valuepilot.core.ShoppingDecisionInput
import com.valuepilot.core.ShoppingDecisionBasketLine
import com.valuepilot.core.ShoppingDecisionStoreScenario
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.TripDistanceBasis
import com.valuepilot.core.TripOriginKind
import com.valuepilot.core.TripOriginReference
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.BaseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerIntegrationTest {

    @Test
    fun `Basket and purchase context reach Trip without mutating either`() {
        val storeKey = ShoppingStoreKey("directory:store:1")
        val purchase = ConfirmedPurchase(
            purchaseId = "purchase-1",
            productIdentityKey = "catalog:coffee",
            identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET,
            displayName = "Coffee",
            amountPaid = Money.parse("6.49", "CAD"),
            store = ConfirmedPurchaseStore(displayName = "Market", storeKey = storeKey)
        )
        val unchangedPurchase = purchase.copy()
        val destination = TripStoreSelection(storeKey, "Market", sourceLabel = "private store")
        val trip = TripPlannerState(
            destination = destination,
            origin = TripOriginReference(TripOriginKind.MANUAL, "Home"),
            drivingDistanceMetres = 10_000L,
            distanceBasis = TripDistanceBasis.ROUND_TRIP,
            fuelConsumptionMilliLitresPer100Km = 7_500L,
            fuelPrice = Money.parse("1.80", "CAD")
        )
        val decision = ShoppingDecisionEngine.evaluate(
            ShoppingDecisionInput(
                basket = listOf(
                    ShoppingDecisionBasketLine(
                        lineId = "coffee",
                        productIdentityKey = ShoppingItemKey("catalog:coffee"),
                        displayName = "Coffee",
                        requestedQuantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT)
                    )
                ),
                stores = listOf(ShoppingDecisionStoreScenario(storeKey, trip.costInputOrNull())),
                confirmedPurchases = listOf(purchase)
            )
        ).scenarios.single()
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = TripPlannerBasketSnapshot(1, 0, 0, "basket-r1"),
            directoryAvailable = false,
            decision = decision
        )

        assertEquals(TripPlannerSurfaceMode.CALCULATED, projected.mode)
        assertEquals("CAD 2.70", projected.tripCostAmount)
        assertEquals("Unknown", projected.currentSavingsAmount)
        assertEquals(unchangedPurchase, purchase)
        assertEquals(storeKey, purchase.store?.storeKey)
        assertTrue(projected.detailLines.any { it.contains("Market") })
    }
}

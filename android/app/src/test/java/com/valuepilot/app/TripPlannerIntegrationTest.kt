package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.ConfirmedPurchaseStore
import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.TripDistanceBasis
import com.valuepilot.core.TripOriginKind
import com.valuepilot.core.TripOriginReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerIntegrationTest {

    @Test
    fun `basket and confirmed purchase inform the trip scenario without mutating either`() {
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
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = TripPlannerBasketSnapshot(plannedItemCount = 1, privateIdentityCount = 0, unknownItemCount = 0, revision = "basket-r1"),
            directoryAvailable = false
        )

        assertEquals(TripPlannerSurfaceMode.CALCULATED, projected.mode)
        assertEquals(unchangedPurchase, purchase)
        assertEquals(storeKey, purchase.store?.storeKey)
        assertTrue(projected.savingsText.contains("UNKNOWN"))
    }
}

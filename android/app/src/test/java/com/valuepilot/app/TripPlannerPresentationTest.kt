package com.valuepilot.app

import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.ShoppingDecisionAuthorizationState
import com.valuepilot.core.ShoppingDecisionBasketLine
import com.valuepilot.core.ShoppingDecisionCurrentOffer
import com.valuepilot.core.ShoppingDecisionEngine
import com.valuepilot.core.ShoppingDecisionInput
import com.valuepilot.core.ShoppingDecisionOfferProvenance
import com.valuepilot.core.ShoppingDecisionStoreScenario
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.TripCostInput
import com.valuepilot.core.TripDistanceBasis
import com.valuepilot.core.TripOriginKind
import com.valuepilot.core.TripOriginReference
import com.valuepilot.core.BaseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerPresentationTest {

    @Test
    fun `complete details and current prices make the decision readable`() {
        val trip = completeTrip()
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = basket(planned = 2, privateItems = 1, unknown = 0, revision = "complete"),
            directoryAvailable = true,
            decision = decisionFor(trip, currentOffer = true)
        )

        assertEquals(TripPlannerSurfaceMode.CALCULATED, projected.mode)
        assertEquals("Trip to Market", projected.headline)
        assertEquals("CAD 2.70", projected.tripCostAmount)
        assertEquals("CAD 2.70", projected.saveAtLeastAmount)
        assertEquals("Unknown", projected.currentSavingsAmount)
        assertEquals("Current prices available", projected.decisionHeadline)
        assertTrue(projected.basketSummary.contains("3 items"))
        assertTrue(projected.detailLines.size <= 8)
    }

    @Test
    fun `complete details without current prices keep savings unknown`() {
        val trip = completeTrip()
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "no-current"),
            directoryAvailable = true,
            decision = decisionFor(trip, currentOffer = false)
        )

        assertEquals(TripPlannerSurfaceMode.CALCULATED, projected.mode)
        assertEquals("CAD 2.70", projected.tripCostAmount)
        assertEquals("Unknown", projected.currentSavingsAmount)
        assertEquals("Not enough current prices.", projected.currentSavingsSupport)
        assertEquals("Need more current prices", projected.decisionHeadline)
    }

    @Test
    fun `missing distance never becomes a zero trip cost`() {
        val state = completeTrip().copy(drivingDistanceMetres = null, distanceBasis = null)
        val projected = TripPlannerProjector.project(
            state = state,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "missing-distance"),
            directoryAvailable = true
        )

        assertEquals(TripPlannerSurfaceMode.NEEDS_INPUT, projected.mode)
        assertEquals("Unknown", projected.tripCostAmount)
        assertFalse(projected.canCalculate)
        assertTrue(projected.guidance.contains("trip details"))
    }

    @Test
    fun `missing fuel price never becomes a zero trip cost`() {
        val state = completeTrip().copy(fuelPrice = null, fuelConsumptionMilliLitresPer100Km = null)
        val projected = TripPlannerProjector.project(
            state = state,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "missing-fuel"),
            directoryAvailable = true
        )

        assertEquals(TripPlannerSurfaceMode.NEEDS_INPUT, projected.mode)
        assertEquals("Unknown", projected.tripCostAmount)
        assertEquals("Unknown", projected.saveAtLeastAmount)
    }

    @Test
    fun `zero tolls and parking stay out of the compact cost support`() {
        val trip = completeTrip().copy(tolls = Money(0L, "CAD", 2), parking = Money(0L, "CAD", 2))
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "fuel-only"),
            directoryAvailable = true,
            decision = decisionFor(trip, currentOffer = false)
        )

        assertEquals("Fuel", projected.tripCostSupport)
        assertFalse(projected.tripCostSupport.contains("tolls"))
        assertFalse(projected.tripCostSupport.contains("parking"))
    }

    @Test
    fun `nonzero tolls and parking remain visible in the compact support`() {
        val trip = completeTrip().copy(
            tolls = Money.parse("1.25", "CAD"),
            parking = Money.parse("2.00", "CAD")
        )
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "extras"),
            directoryAvailable = true,
            decision = decisionFor(trip, currentOffer = false)
        )

        assertEquals("Fuel + tolls + parking", projected.tripCostSupport)
        assertTrue(projected.detailLines.any { it.contains("Trip details") })
    }

    @Test
    fun `conflicting prices are concise and do not force a winner`() {
        val trip = completeTrip()
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "conflict"),
            directoryAvailable = true,
            decision = decisionFor(trip, conflict = true)
        )

        assertEquals("Prices conflict", projected.decisionHeadline)
        assertTrue(projected.decisionDetail.contains("will not force a winner"))
        assertEquals("Prices conflict.", projected.currentSavingsSupport)
    }

    @Test
    fun `stale prices are explained without becoming current savings`() {
        val trip = completeTrip()
        val projected = TripPlannerProjector.project(
            state = trip,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "stale"),
            directoryAvailable = true,
            decision = decisionFor(trip, stale = true)
        )

        assertEquals("Need more current prices", projected.decisionHeadline)
        assertTrue(projected.decisionDetail.contains("out of date"))
        assertEquals("Unknown", projected.currentSavingsAmount)
    }

    @Test
    fun `large Basket stays a compact summary`() {
        val projected = TripPlannerProjector.project(
            state = completeTrip(),
            basket = basket(planned = 120, privateItems = 15, unknown = 4, revision = "large"),
            directoryAvailable = true,
            decision = decisionFor(completeTrip(), currentOffer = false)
        )

        assertEquals("Basket · 139 items", projected.basketSummary)
        assertTrue(projected.detailLines.size <= 8)
        assertFalse(projected.detailLines.any { it.contains("item 119") })
    }

    @Test
    fun `long store names and foreground location remain understandable`() {
        val longStore = TripStoreSelection(
            storeKey = ShoppingStoreKey("store:long"),
            displayName = "Mercado de barrio con nombre muy largo para texto grande",
            sourceLabel = "private",
            latitudeE7 = 435000000,
            longitudeE7 = -793800000
        )
        val state = completeTrip().copy(
            destination = longStore,
            origin = TripOriginReference(TripOriginKind.FOREGROUND_LOCATION, "Current location", "foreground-1")
        )
        val projected = TripPlannerProjector.project(
            state = state,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "large-text"),
            directoryAvailable = true,
            decision = decisionFor(state, currentOffer = false)
        )

        assertTrue(projected.headline.contains("Mercado de barrio"))
        assertTrue(projected.detailLines.any { it.contains("one foreground location reading") })
        assertTrue(projected.detailLines.all { it.isNotBlank() })
    }

    @Test
    fun `unavailable saved state stays unknown and preserves the Basket summary`() {
        val projected = TripPlannerProjector.project(
            state = null,
            basket = basket(planned = 1, privateItems = 0, unknown = 0, revision = "unavailable"),
            directoryAvailable = false,
            unavailable = true
        )

        assertEquals(TripPlannerSurfaceMode.UNAVAILABLE, projected.mode)
        assertEquals("Unknown", projected.tripCostAmount)
        assertEquals("Unknown", projected.saveAtLeastAmount)
        assertEquals("Unknown", projected.currentSavingsAmount)
        assertTrue(projected.basketSummary.contains("1 item"))
    }

    private fun completeTrip(): TripPlannerState =
        TripPlannerState(
            destination = TripStoreSelection(
                storeKey = ShoppingStoreKey("store:1"),
                displayName = "Market",
                addressLabel = "12 Main Street",
                sourceLabel = "private",
                latitudeE7 = 435000000,
                longitudeE7 = -793800000
            ),
            origin = TripOriginReference(TripOriginKind.MANUAL, "Home"),
            drivingDistanceMetres = 10_000L,
            distanceBasis = TripDistanceBasis.ROUND_TRIP,
            fuelConsumptionMilliLitresPer100Km = 7_500L,
            fuelPrice = Money.parse("1.80", "CAD")
        )

    private fun basket(planned: Int, privateItems: Int, unknown: Int, revision: String) =
        TripPlannerBasketSnapshot(planned, privateItems, unknown, revision)

    private fun decisionFor(
        trip: TripPlannerState,
        currentOffer: Boolean = false,
        conflict: Boolean = false,
        stale: Boolean = false
    ) =
        ShoppingDecisionEngine.evaluate(
            ShoppingDecisionInput(
                basket = listOf(
                    ShoppingDecisionBasketLine(
                        lineId = "line:bread",
                        productIdentityKey = ShoppingItemKey("bread"),
                        displayName = "Bread",
                        requestedQuantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT)
                    )
                ),
                stores = listOf(
                    ShoppingDecisionStoreScenario(
                        storeKey = requireNotNull(trip.destination).storeKey,
                        tripCostInput = trip.costInputOrNull()
                    )
                ),
                currentOffers = if (currentOffer || conflict || stale) {
                    listOfNotNull(
                        offer("offer:a", "5.00").let { if (stale) it.copy(freshness = EvidenceFreshness.STALE) else it },
                        if (conflict) offer("offer:b", "6.00") else null
                    )
                } else {
                    emptyList()
                }
            )
        ).scenarios.single()

    private fun offer(id: String, price: String) =
        ShoppingDecisionCurrentOffer(
            offerId = id,
            productIdentityKey = ShoppingItemKey("bread"),
            storeKey = ShoppingStoreKey("store:1"),
            price = Money.parse(price, "CAD"),
            packageQuantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT),
            observedAtEpochMillis = 1L,
            freshness = EvidenceFreshness.FRESH,
            authorization = ShoppingDecisionAuthorizationState.AUTHORIZED,
            provenance = ShoppingDecisionOfferProvenance("fixture", id, "rights")
        )
}

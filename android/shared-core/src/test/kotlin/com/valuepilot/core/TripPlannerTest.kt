package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerTest {

    @Test
    fun `round trip fuel cost uses exact integer arithmetic and adds optional fees`() {
        val result =
            TripCostCalculator.calculate(
                TripCostInput(
                    drivingDistanceMetres = 10_000L,
                    distanceBasis = TripDistanceBasis.ROUND_TRIP,
                    fuelConsumptionMilliLitresPer100Km = 7_500L,
                    fuelPricePerLitre = Money.parse("1.80", "CAD"),
                    tolls = Money.parse("2.25", "CAD"),
                    parking = Money.parse("1.00", "CAD")
                )
            )

        // 20 km × 7.5 L/100 km × $1.80/L = $2.70.
        assertEquals(20_000L, result.effectiveDrivingDistanceMetres)
        assertEquals(270L, result.fuelCost.minorUnits)
        assertEquals(225L, result.tolls.minorUnits)
        assertEquals(100L, result.parking.minorUnits)
        assertEquals(595L, result.total.minorUnits)
    }

    @Test
    fun `additional driving does not double distance and half-up rounding is explicit`() {
        val result =
            TripCostCalculator.calculate(
                TripCostInput(
                    drivingDistanceMetres = 1L,
                    distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
                    fuelConsumptionMilliLitresPer100Km = 1L,
                    fuelPricePerLitre = Money(50L, "CAD", 2)
                )
            )

        assertEquals(1L, result.effectiveDrivingDistanceMetres)
        assertEquals(0L, result.fuelCost.minorUnits)
        assertEquals(0L, result.total.minorUnits)
    }

    @Test
    fun `zero distance and omitted fees remain valid while negatives and mixed currency fail`() {
        val zero =
            TripCostCalculator.calculate(
                TripCostInput(
                    drivingDistanceMetres = 0L,
                    distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
                    fuelConsumptionMilliLitresPer100Km = 8_000L,
                    fuelPricePerLitre = Money.parse("1.50", "CAD")
                )
            )
        assertEquals(0L, zero.total.minorUnits)
        assertEquals(0L, zero.tolls.minorUnits)
        assertEquals(0L, zero.parking.minorUnits)

        assertThrows(IllegalArgumentException::class.java) {
            TripCostInput(
                drivingDistanceMetres = -1L,
                distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
                fuelConsumptionMilliLitresPer100Km = 8_000L,
                fuelPricePerLitre = Money.parse("1.50", "CAD")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TripCostInput(
                drivingDistanceMetres = 1L,
                distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
                fuelConsumptionMilliLitresPer100Km = 0L,
                fuelPricePerLitre = Money.parse("1.50", "CAD")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TripCostInput(
                drivingDistanceMetres = 1L,
                distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
                fuelConsumptionMilliLitresPer100Km = 8_000L,
                fuelPricePerLitre = Money.parse("1.50", "CAD"),
                parking = Money.parse("1.00", "USD")
            )
        }
    }

    @Test
    fun `origin keeps foreground context explicit without storing coordinates`() {
        val manual = TripOriginReference(TripOriginKind.MANUAL, "Home")
        val foreground = TripOriginReference(TripOriginKind.FOREGROUND_LOCATION, "Current location", "foreground-1")
        assertEquals(TripOriginKind.MANUAL, manual.kind)
        assertEquals("foreground-1", foreground.contextId)
        assertThrows(IllegalArgumentException::class.java) {
            TripOriginReference(TripOriginKind.FOREGROUND_LOCATION, "Current location")
        }
    }

    @Test
    fun `basket economics remain unknown until comparable offers are authorized`() {
        val unknown = TripBasketEconomics()
        assertEquals(TripBasketSavingsStatus.UNKNOWN_NO_AUTHORIZED_CURRENT_OFFERS, unknown.status)
        assertNull(unknown.basketSavings)
        assertNull(unknown.netSavingsAfterTripCost)

        assertThrows(IllegalArgumentException::class.java) {
            TripBasketEconomics(
                status = TripBasketSavingsStatus.KNOWN_COMPARABLE_OFFERS,
                basketSavings = null,
                netSavingsAfterTripCost = null
            )
        }
        assertFalse(unknown.status == TripBasketSavingsStatus.KNOWN_COMPARABLE_OFFERS)
        assertTrue(TripBasketSavingsStatus.UNKNOWN_NO_AUTHORIZED_CURRENT_OFFERS != TripBasketSavingsStatus.KNOWN_COMPARABLE_OFFERS)
    }
}

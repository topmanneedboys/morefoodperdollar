package com.valuepilot.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArgentinaPrivateTripCostTest {
    @Test
    fun calculatorKeepsMerchandiseAndTripCostIndependentWithExactDecimalMath() {
        val result = ArgentinaPrivateTripCostCalculator.calculate(
            ArgentinaPrivateTripCostInput(
                oneWayDrivingKm = BigDecimal("10"),
                fuelConsumptionLitresPer100Km = BigDecimal("8"),
                fuelPricePerLitre = BigDecimal("1000"),
                tolls = BigDecimal("50"),
                parking = BigDecimal("25")
            )
        )
        assertEquals(0, result.roundTripKm.compareTo(BigDecimal("20")))
        assertEquals(BigDecimal("1600.00"), result.fuelCost)
        assertEquals(BigDecimal("1675.00"), result.total)
        assertEquals("Trip cost · ARS 1675.00 (fuel ARS 1600.00 · tolls ARS 50.00 · parking ARS 25.00)", result.displayText())
    }

    @Test
    fun parserRejectsMalformedAndOverPreciseInputs() {
        assertThrows(IllegalStateException::class.java) {
            ArgentinaPrivateTripCostCalculator.parseDecimal("not-a-number", "distance")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArgentinaPrivateTripCostCalculator.parseDecimal("1.1234567", "distance")
        }
    }
}

package com.valuepilot.app

import com.valuepilot.core.TripDistanceBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerInputParserTest {

    @Test
    fun `parser keeps decimal distance exact and accepts blank optional fees`() {
        val parsed =
            TripPlannerInputParser.parse(
                TripPlannerAssumptionInput(
                    distanceKmText = "12.345",
                    distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
                    consumptionText = "7500",
                    fuelPriceText = "1.80",
                    currencyText = "cad",
                    tollText = "",
                    parkingText = "   "
                )
            )
        assertTrue(parsed.accepted)
        val input = requireNotNull(parsed.value)
        assertEquals(12_345L, input.drivingDistanceMetres)
        assertEquals(TripDistanceBasis.ADDITIONAL_DRIVING, input.distanceBasis)
        assertEquals("CAD", input.fuelPricePerLitre.currencyCode)
        assertEquals(null, input.tolls)
        assertEquals(null, input.parking)
    }

    @Test
    fun `parser rejects negative malformed and over precise assumptions`() {
        fun parse(distance: String, consumption: String = "7500", fuel: String = "1.80") =
            TripPlannerInputParser.parse(
                TripPlannerAssumptionInput(
                    distanceKmText = distance,
                    distanceBasis = TripDistanceBasis.ROUND_TRIP,
                    consumptionText = consumption,
                    fuelPriceText = fuel,
                    currencyText = "CAD",
                    tollText = "",
                    parkingText = ""
                )
            )

        assertFalse(parse("-1").accepted)
        assertFalse(parse("1.2345").accepted)
        assertFalse(parse("1", consumption = "0").accepted)
        assertFalse(parse("1", fuel = "-0.01").accepted)
        assertFalse(TripPlannerInputParser.parse(itInputWithCurrency("US")).accepted)
    }

    @Test
    fun `origin parser trims private labels without geocoding`() {
        assertEquals("Home base", TripPlannerOriginParser.manual("  Home   base "))
        assertEquals(null, TripPlannerOriginParser.manual("\n"))
    }

    @Test
    fun `denied foreground permission keeps manual-origin path available`() {
        assertFalse(TripPlannerLocationPolicy.hasForegroundPermission(fineGranted = false, coarseGranted = false))
        assertTrue(TripPlannerLocationPolicy.hasForegroundPermission(fineGranted = true, coarseGranted = false))
        assertTrue(TripPlannerLocationPolicy.hasForegroundPermission(fineGranted = false, coarseGranted = true))
    }

    private fun itInputWithCurrency(currency: String): TripPlannerAssumptionInput =
        TripPlannerAssumptionInput(
            distanceKmText = "1",
            distanceBasis = TripDistanceBasis.ROUND_TRIP,
            consumptionText = "7500",
            fuelPriceText = "1.80",
            currencyText = currency,
            tollText = "",
            parkingText = ""
        )
}

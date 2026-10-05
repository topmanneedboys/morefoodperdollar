package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Presentation boundary checks for the calm, decision-first Trip surface. */
class TripPlannerSurfaceBoundaryTest {

    @Test
    fun `trip surface leads with the decision numbers and progressive detail`() {
        val source = source("TripPlannerSurfaceView.kt")
        listOf(
            "Trip cost",
            "Save at least",
            "Current savings",
            "Trip details",
            "text = \"Edit\"",
            "For this decision",
            "Why?",
            "More trip actions",
            "tripCostAmount.text = state.tripCostAmount",
            "saveAtLeastAmount.text = state.saveAtLeastAmount",
            "currentSavingsAmount.text = state.currentSavingsAmount",
            "ValuePilotMotion.reveal(tripCostCard"
        ).forEach { required ->
            assertTrue("Missing Trip presentation boundary $required", source.contains(required))
        }
    }

    @Test
    fun `trip renderer has no trip or decision authority and hides engine vocabulary`() {
        val source = source("TripPlannerSurfaceView.kt")
        listOf(
            "TripCostCalculator",
            "ShoppingDecisionEngine",
            "TripPlannerStore",
            "Money.parse",
            "System.currentTimeMillis",
            "SharedPreferences",
            "Http",
            "URL(",
            "evidenceChips",
            "Decision Engine",
            "break-even threshold",
            "current-authorized"
        ).forEach { forbidden ->
            assertFalse("Trip surface must not own or expose $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `trip renderer exposes complete accessible meanings and actions`() {
        val source = source("TripPlannerSurfaceView.kt")
        listOf(
            "Trip cost: ${'$'}{state.tripCostAmount}",
            "Save at least: ${'$'}{state.saveAtLeastAmount}",
            "Current savings: ${'$'}{state.currentSavingsAmount}",
            "Use my location once",
            "Directions",
            "setDetailsExpanded",
            "setActionsExpanded"
        ).forEach { required -> assertTrue("Missing accessibility/action text $required", source.contains(required)) }
    }

    @Test
    fun `trip route preserves scroll and Map Basket handoff`() {
        val activity = source("MainActivity.kt")
        listOf(
            "private var tripPlannerScrollY = 0",
            "private var tripPlannerRouteActive = false",
            "STATE_TRIP_PLANNER_SCROLL_Y",
            "restoreTripPlannerScrollPosition()",
            "state.route == AppRoute.TRIP_PLANNER",
            "dispatch(AppShellIntent.OpenTripPlanner)",
            "persistTripPlannerState(tripPlannerState.copy(destination = selection",
            "reopenProductMapAfterTrip"
        ).forEach { required -> assertTrue("Missing Trip state boundary $required", activity.contains(required)) }
    }

    @Test
    fun `secondary Trip copy uses shopper language`() {
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("Is this trip worth it?"))
        assertTrue(activity.contains("See the trip cost"))
        assertTrue(activity.contains("Trip details"))
        assertFalse(activity.contains("Read-only Decision Engine summary"))
    }

    private fun source(name: String): String {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "src/main/java/com/valuepilot/app/$name")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $name")
    }
}

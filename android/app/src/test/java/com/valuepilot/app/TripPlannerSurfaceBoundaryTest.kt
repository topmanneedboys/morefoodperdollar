package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Presentation boundary checks for the unified Trip Planner surface. */
class TripPlannerSurfaceBoundaryTest {

    @Test
    fun trip_surface_uses_shared_cards_and_progressive_detail() {
        val source = source("TripPlannerSurfaceView.kt")
        listOf(
            "ValuePilotProductCard(context)",
            "Trip overview",
            "Basket coverage",
            "Why this answer",
            "More trip actions",
            "ValuePilotMotion.reveal(tripCard",
            "ValuePilotMotion.reveal(evidenceCard",
            "TripPlannerEvidenceChip",
            "onAction?.invoke(action)"
        ).forEach { required -> assertTrue("Missing Trip Planner presentation boundary $required", source.contains(required)) }
    }

    @Test
    fun trip_surface_does_not_gain_trip_or_decision_authority() {
        val source = source("TripPlannerSurfaceView.kt")
        listOf(
            "TripCostCalculator",
            "ShoppingDecisionEngine",
            "TripPlannerStore",
            "Money.parse",
            "System.currentTimeMillis",
            "SharedPreferences",
            "Http",
            "URL("
        ).forEach { forbidden -> assertFalse("Trip surface must not own $forbidden", source.contains(forbidden)) }
    }

    @Test
    fun trip_route_preserves_scroll_and_map_basket_handoff() {
        val activity = source("MainActivity.kt")
        listOf(
            "private var tripPlannerScrollY = 0",
            "private var tripPlannerRouteActive = false",
            "STATE_TRIP_PLANNER_SCROLL_Y",
            "restoreTripPlannerScrollPosition()",
            "state.route == AppRoute.TRIP_PLANNER",
            "dispatch(AppShellIntent.OpenTripPlanner)",
            "persistTripPlannerState(tripPlannerState.copy(destination = selection"
        ).forEach { required -> assertTrue("Missing Trip Planner state boundary $required", activity.contains(required)) }
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

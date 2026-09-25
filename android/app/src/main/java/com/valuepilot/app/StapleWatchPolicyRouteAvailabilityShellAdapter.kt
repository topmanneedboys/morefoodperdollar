package com.valuepilot.app

/**
 * Pure shell adapter for the typed availability of the explicit Staple Watch policy route.
 *
 * Availability is already derived by the policy setup coordinator from authoritative baseline-money
 * resolution. This adapter owns only route-sensitive shell intent mapping:
 *
 * - AVAILABLE opens the policy subroute only while Watch setup is the current route.
 * - UNAVAILABLE steps back only while the policy subroute itself is current.
 * - every other combination is ignored.
 *
 * The shell reducer remains the navigation-state authority. This adapter owns no evidence parsing,
 * money resolution, policy construction, draft state, rendering, Android lifecycle, persistence,
 * background scheduling, evaluation, or notification behavior.
 */
internal class StapleWatchPolicyRouteAvailabilityShellAdapter(
    private val currentRoute: () -> AppRoute,
    private val emitIntent: (AppShellIntent) -> Unit
) : StapleWatchPolicyRouteAvailabilityObserver {

    override fun onAvailabilityChanged(availability: StapleWatchPolicyRouteAvailability) {
        when (availability) {
            StapleWatchPolicyRouteAvailability.AVAILABLE -> {
                if (currentRoute() == AppRoute.STAPLE_WATCH_SETUP) {
                    emitIntent(AppShellIntent.OpenStapleWatchPolicy)
                }
            }

            StapleWatchPolicyRouteAvailability.UNAVAILABLE -> {
                if (currentRoute() == AppRoute.STAPLE_WATCH_POLICY) {
                    emitIntent(AppShellIntent.NavigateBack)
                }
            }
        }
    }
}

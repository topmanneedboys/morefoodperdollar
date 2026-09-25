package com.valuepilot.app

/**
 * Route-local gate for foreground Watch result presentation.
 *
 * Foreground evaluation output is forwarded only while the explicit policy route is visible.
 * Leaving that route emits one typed [StapleWatchForegroundEvaluationOutput.Cleared] downstream so
 * previously rendered consumer state cannot survive into another shell route. Hidden-route output
 * is suppressed and is never replayed when the route becomes visible again.
 *
 * This boundary owns no economics, projection, Android view, persistence, background-work, or
 * notification authority.
 */
internal class StapleWatchPolicyRouteForegroundEvaluationOutputGate(
    private val downstream: StapleWatchForegroundEvaluationOutputObserver
) : StapleWatchForegroundEvaluationOutputObserver {

    private var routeVisible = false

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (visible == routeVisible) return

        routeVisible = visible
        if (!visible) {
            downstream.onOutputChanged(StapleWatchForegroundEvaluationOutput.Cleared)
        }
    }

    override fun onOutputChanged(output: StapleWatchForegroundEvaluationOutput) {
        if (!routeVisible) return
        downstream.onOutputChanged(output)
    }
}

package com.valuepilot.app

/**
 * Pure composition boundary from foreground Watch output to one replaceable result surface.
 *
 * Output interpretation remains in [StapleWatchForegroundEvaluationPresentationObserver], while
 * route-local stale suppression remains in [StapleWatchPolicyRouteForegroundEvaluationOutputGate].
 * This binding only assembles those verified boundaries for a shell owner.
 */
internal class StapleWatchForegroundResultSurfaceBinding(
    renderer: StapleWatchSurfaceRenderer,
    clearSurface: () -> Unit
) {
    private val routeGate =
        StapleWatchPolicyRouteForegroundEvaluationOutputGate(
            StapleWatchForegroundEvaluationPresentationObserver(
                presenter = StapleWatchSurfacePresenter(renderer),
                clearSurface = clearSurface
            )
        )

    val outputObserver: StapleWatchForegroundEvaluationOutputObserver
        get() = routeGate

    fun onPolicyRouteVisibilityChanged(visible: Boolean) {
        routeGate.onRouteVisibilityChanged(visible)
    }
}

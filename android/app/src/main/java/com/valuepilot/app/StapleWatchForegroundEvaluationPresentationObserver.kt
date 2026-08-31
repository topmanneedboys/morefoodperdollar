package com.valuepilot.app

/**
 * Foreground-only bridge from evaluation-output lifecycle into the existing Watch presenter.
 *
 * New evidence and completed evaluations without a consumer projection clear any previously shown
 * result. A completed projected evaluation is handed to [StapleWatchSurfacePresenter], which keeps
 * exact decisions and opaque identities outside the physical renderer.
 *
 * This bridge does not inspect economic blockers or decisions, project consumer state, persist
 * anything, schedule work, or authorize notifications.
 */
internal class StapleWatchForegroundEvaluationPresentationObserver(
    private val presenter: StapleWatchSurfacePresenter,
    private val clearSurface: () -> Unit
) : StapleWatchForegroundEvaluationOutputObserver {

    override fun onOutputChanged(output: StapleWatchForegroundEvaluationOutput) {
        when (output) {
            StapleWatchForegroundEvaluationOutput.Cleared -> clearSurface()
            is StapleWatchForegroundEvaluationOutput.Evaluated -> {
                val projection = output.evaluation.projection
                if (projection == null) {
                    clearSurface()
                } else {
                    presenter.render(projection)
                }
            }
        }
    }
}

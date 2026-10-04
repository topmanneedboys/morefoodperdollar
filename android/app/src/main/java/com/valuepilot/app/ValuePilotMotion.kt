package com.valuepilot.app

import android.animation.ValueAnimator
import android.os.Build
import android.view.View

/**
 * Small presentation-only motion boundary for the unified consumer surfaces.
 *
 * The caller decides when a state transition is meaningful. This helper only
 * reveals an already-rendered view, uses the shared duration token, and falls
 * back to an immediate state when the system has disabled animator motion.
 */
internal object ValuePilotMotion {

    fun reveal(view: View, animate: Boolean = true) {
        view.animate().cancel()
        if (!animate || !animatorsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }

        view.alpha = 0f
        view.translationY = dp(view, 4)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ValuePilotDesignTokens.Motion.DISCLOSURE_DURATION_MS)
            .start()
    }

    private fun animatorsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private fun dp(view: View, value: Int): Float =
        value * view.resources.displayMetrics.density
}

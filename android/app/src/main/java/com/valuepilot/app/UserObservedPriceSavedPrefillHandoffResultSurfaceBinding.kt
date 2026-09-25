package com.valuepilot.app

import android.view.View

/**
 * Physical result-surface binding for one already-verified Saved-prefill handoff attempt.
 *
 * The binding receives only the typed handoff attempt, delegates consumer projection to the
 * verified presenter, and controls only physical visibility of the result surface. A hidden route
 * clears the visible result. Showing the route never invents or restores a result on its own.
 *
 * This binding never executes a prefill gate, inspects technical identity, mutates a confirmation
 * draft, captures proof/price/time, persists evidence, ranks offers, navigates, or grants
 * current-price authority.
 */
internal class UserObservedPriceSavedPrefillHandoffResultSurfaceBinding(
    private val surface: UserObservedPriceSavedPrefillHandoffSurfaceView,
    private val presenter: UserObservedPriceSavedPrefillHandoffSurfacePresenter =
        UserObservedPriceSavedPrefillHandoffSurfacePresenter(surface)
) : UserObservedPriceSavedPrefillHandoffAttemptObserver, AutoCloseable {

    private var closed = false

    override fun onAttempt(attempt: UserObservedPriceSavedPrefillHandoffAttempt) {
        if (closed) return

        presenter.render(attempt)
        surface.visibility = View.VISIBLE
    }

    fun clear() {
        if (closed) return
        surface.visibility = View.GONE
    }

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible) return
        surface.visibility = View.GONE
    }

    override fun close() {
        if (closed) return

        closed = true
        surface.visibility = View.GONE
    }
}

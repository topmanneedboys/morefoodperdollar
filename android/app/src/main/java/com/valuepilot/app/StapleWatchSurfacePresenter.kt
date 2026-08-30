package com.valuepilot.app

/** Narrow target for any replaceable physical Watch My Staples renderer. */
fun interface StapleWatchSurfaceRenderer {
    fun render(state: StapleWatchUiState)
}

/**
 * Hands already-projected consumer state to a physical renderer.
 *
 * The presenter receives the full projection only at the application boundary and exposes only
 * [StapleWatchUiState] to the renderer. Opaque store keys, exact economic decisions, arithmetic,
 * freshness authority, notification authorization and provider metadata remain outside the View.
 */
class StapleWatchSurfacePresenter(
    private val renderer: StapleWatchSurfaceRenderer
) {
    fun render(projection: StapleWatchUiProjection) {
        renderer.render(projection.state)
    }
}

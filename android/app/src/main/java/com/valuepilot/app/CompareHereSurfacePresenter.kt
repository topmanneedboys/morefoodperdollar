package com.valuepilot.app

/** Narrow target for any replaceable physical Compare Here renderer. */
fun interface CompareHereSurfaceRenderer {
    fun render(state: CompareHereUiState)
}

/**
 * Hands the already-projected consumer state to a physical renderer.
 *
 * This presenter deliberately receives the full projection only at the application boundary and
 * exposes only [CompareHereUiState] to the renderer. Opaque candidate ids, exact lookup maps,
 * ranking decisions, arithmetic, capture facts and provider metadata remain outside the View.
 */
class CompareHereSurfacePresenter(
    private val renderer: CompareHereSurfaceRenderer
) {
    fun render(projection: CompareHereUiProjection) {
        renderer.render(projection.state)
    }
}

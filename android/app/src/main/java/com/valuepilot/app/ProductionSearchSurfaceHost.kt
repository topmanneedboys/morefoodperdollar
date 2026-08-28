package com.valuepilot.app

import com.valuepilot.core.ProductionBestValuePresentationSnapshot

/**
 * Narrow display boundary for future production Search surfaces.
 *
 * The host owns the exact projection and generation ordering internally. Its
 * renderer can receive only [ProductionSearchUiState], never the exact lookup
 * maps, provider URLs, internal scope identifiers, blocker diagnostics, or raw
 * production evidence retained by [ProductionSearchUiProjection].
 *
 * This class performs no I/O and does not activate any provider. A future
 * coordinator may submit already-evaluated presentation snapshots only after the
 * corresponding production authorization/evidence gates have been satisfied.
 */
fun interface ProductionSearchSurfaceRenderer {
    fun render(state: ProductionSearchUiState?)
}

class ProductionSearchSurfaceHost(
    private val renderer: ProductionSearchSurfaceRenderer
) {
    private var refreshState = ProductionSearchRefreshState()

    fun applySnapshot(
        generation: Long,
        snapshot: ProductionBestValuePresentationSnapshot
    ): ProductionSearchRefreshDisposition {
        val decision =
            ProductionSearchRefreshGate.applySnapshot(
                current = refreshState,
                incomingGeneration = generation,
                snapshot = snapshot
            )

        applyDecision(decision)
        return decision.disposition
    }

    fun clear(generation: Long): ProductionSearchRefreshDisposition {
        val decision =
            ProductionSearchRefreshGate.clear(
                current = refreshState,
                incomingGeneration = generation
            )

        applyDecision(decision)
        return decision.disposition
    }

    private fun applyDecision(decision: ProductionSearchRefreshDecision) {
        if (decision.disposition != ProductionSearchRefreshDisposition.APPLIED) return

        val nextState = decision.state
        renderer.render(nextState.projection?.state)
        refreshState = nextState
    }
}

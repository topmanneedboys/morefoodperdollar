package com.valuepilot.app

import com.valuepilot.core.ProductionBestValuePresentationSnapshot

/**
 * Immutable coordination state for future asynchronous production-search refreshes.
 *
 * Generation is supplied by the caller and represents request/order sequencing,
 * not wall-clock time. The gate owns no clock, performs no I/O, and never parses,
 * ranks, or changes production evidence. It only prevents an older or ambiguous
 * refresh from replacing a newer verified projection.
 */
data class ProductionSearchRefreshState(
    val latestGeneration: Long? = null,
    val projection: ProductionSearchUiProjection? = null
) {
    init {
        latestGeneration?.let { require(it >= 0L) }
        require(latestGeneration != null || projection == null) {
            "A projection requires an applied refresh generation"
        }
    }
}

enum class ProductionSearchRefreshDisposition {
    APPLIED,
    DUPLICATE,
    STALE,
    GENERATION_CONFLICT
}

data class ProductionSearchRefreshDecision(
    val disposition: ProductionSearchRefreshDisposition,
    val state: ProductionSearchRefreshState
)

/**
 * Pure fail-closed refresh gate for the production UI boundary.
 *
 * - newer generations replace older state;
 * - older generations are ignored as stale;
 * - an identical replay at the same generation is idempotent;
 * - a different payload at an already-used generation is rejected as a conflict;
 * - clear participates in the same ordering rules so a stale result cannot
 *   repopulate a surface after a newer clear/search generation.
 */
object ProductionSearchRefreshGate {

    fun applySnapshot(
        current: ProductionSearchRefreshState,
        incomingGeneration: Long,
        snapshot: ProductionBestValuePresentationSnapshot
    ): ProductionSearchRefreshDecision {
        require(incomingGeneration >= 0L)

        val currentGeneration = current.latestGeneration
        if (currentGeneration != null && incomingGeneration < currentGeneration) {
            return ProductionSearchRefreshDecision(
                disposition = ProductionSearchRefreshDisposition.STALE,
                state = current
            )
        }

        val incomingProjection = ProductionSearchUiProjector.project(snapshot)

        if (currentGeneration == incomingGeneration) {
            return ProductionSearchRefreshDecision(
                disposition =
                    if (current.projection == incomingProjection) {
                        ProductionSearchRefreshDisposition.DUPLICATE
                    } else {
                        ProductionSearchRefreshDisposition.GENERATION_CONFLICT
                    },
                state = current
            )
        }

        return ProductionSearchRefreshDecision(
            disposition = ProductionSearchRefreshDisposition.APPLIED,
            state =
                ProductionSearchRefreshState(
                    latestGeneration = incomingGeneration,
                    projection = incomingProjection
                )
        )
    }

    fun clear(
        current: ProductionSearchRefreshState,
        incomingGeneration: Long
    ): ProductionSearchRefreshDecision {
        require(incomingGeneration >= 0L)

        val currentGeneration = current.latestGeneration
        if (currentGeneration != null && incomingGeneration < currentGeneration) {
            return ProductionSearchRefreshDecision(
                disposition = ProductionSearchRefreshDisposition.STALE,
                state = current
            )
        }

        if (currentGeneration == incomingGeneration) {
            return ProductionSearchRefreshDecision(
                disposition =
                    if (current.projection == null) {
                        ProductionSearchRefreshDisposition.DUPLICATE
                    } else {
                        ProductionSearchRefreshDisposition.GENERATION_CONFLICT
                    },
                state = current
            )
        }

        return ProductionSearchRefreshDecision(
            disposition = ProductionSearchRefreshDisposition.APPLIED,
            state =
                ProductionSearchRefreshState(
                    latestGeneration = incomingGeneration,
                    projection = null
                )
        )
    }
}

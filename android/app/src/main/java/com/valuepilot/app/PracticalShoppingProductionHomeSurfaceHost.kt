package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionOrchestrationRequest
import com.valuepilot.core.PracticalShoppingProductionOrchestrator
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

/**
 * Immutable ordering state for a future production Home refresh.
 *
 * Generation is supplied by the application coordinator and represents request
 * ordering, not wall-clock time. The production projection and its UI state are
 * retained only after the renderer accepts the UI state, so a failed render can
 * be retried at the same generation.
 */
internal data class PracticalShoppingProductionHomeRefreshState(
    val latestGeneration: Long? = null,
    val projection: PracticalShoppingProductionHomeProjection? = null,
    val uiState: PracticalShoppingProductionHomeUiState? = null
) {
    init {
        latestGeneration?.let { require(it >= 0L) }
        require(latestGeneration != null || (projection == null && uiState == null)) {
            "A production Home result requires an applied refresh generation"
        }
        require((projection == null) == (uiState == null)) {
            "A production Home projection and UI state must be applied together"
        }
    }
}

enum class PracticalShoppingProductionHomeRefreshDisposition {
    APPLIED,
    DUPLICATE,
    STALE,
    GENERATION_CONFLICT
}

/** Renderer receives only the demo-free, consumer-ready production Home state. */
fun interface PracticalShoppingProductionHomeRenderer {
    fun render(state: PracticalShoppingProductionHomeUiState?)
}

/**
 * Bounded production Home refresh boundary.
 *
 * Each accepted generation starts from the immutable orchestration request and
 * re-runs the shared-core production orchestrator against the registries supplied
 * for that invocation. The host owns no clock, I/O, provider activation, product
 * matching, route calculation, ranking or UI business logic. Callers must invoke
 * [evaluateAndApply] away from the Android main thread and publish the renderer
 * callback on the UI thread when appropriate.
 *
 * A renderer never receives a detached orchestration result, exact decision,
 * opaque store key or raw evidence. The adapter turns structural/reference
 * failure into an unavailable state, while the UI projector keeps a valid
 * no-coverage decision as a normal, truthful Home result.
 */
class PracticalShoppingProductionHomeSurfaceHost(
    private val renderer: PracticalShoppingProductionHomeRenderer
) {

    private var refreshState = PracticalShoppingProductionHomeRefreshState()

    fun evaluateAndApply(
        generation: Long,
        request: PracticalShoppingProductionOrchestrationRequest,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry,
        storeDisplayNames: Map<ShoppingStoreKey, String>,
        itemDisplayNames: Map<ShoppingItemKey, String>
    ): PracticalShoppingProductionHomeRefreshDisposition {
        require(generation >= 0L)

        val currentGeneration = refreshState.latestGeneration
        if (currentGeneration != null && generation < currentGeneration) {
            return PracticalShoppingProductionHomeRefreshDisposition.STALE
        }

        val orchestrationResult =
            PracticalShoppingProductionOrchestrator.evaluate(
                request = request,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry
            )
        val incomingProjection =
            PracticalShoppingProductionHomeAdapter.project(
                request = request,
                orchestrationResult = orchestrationResult,
                storeDisplayNames = storeDisplayNames,
                itemDisplayNames = itemDisplayNames
            )
        val incomingState =
            PracticalShoppingProductionHomeUiProjector.project(
                request = request,
                projection = incomingProjection,
                itemDisplayNames = itemDisplayNames
            )

        if (currentGeneration == generation) {
            return if (refreshState.projection == incomingProjection) {
                PracticalShoppingProductionHomeRefreshDisposition.DUPLICATE
            } else {
                PracticalShoppingProductionHomeRefreshDisposition.GENERATION_CONFLICT
            }
        }

        renderer.render(incomingState)
        refreshState =
            PracticalShoppingProductionHomeRefreshState(
                latestGeneration = generation,
                projection = incomingProjection,
                uiState = incomingState
            )
        return PracticalShoppingProductionHomeRefreshDisposition.APPLIED
    }

    fun clear(generation: Long): PracticalShoppingProductionHomeRefreshDisposition {
        require(generation >= 0L)

        val currentGeneration = refreshState.latestGeneration
        if (currentGeneration != null && generation < currentGeneration) {
            return PracticalShoppingProductionHomeRefreshDisposition.STALE
        }
        if (currentGeneration == generation) {
            return if (refreshState.projection == null) {
                PracticalShoppingProductionHomeRefreshDisposition.DUPLICATE
            } else {
                PracticalShoppingProductionHomeRefreshDisposition.GENERATION_CONFLICT
            }
        }

        renderer.render(null)
        refreshState =
            PracticalShoppingProductionHomeRefreshState(
                latestGeneration = generation,
                projection = null,
                uiState = null
            )
        return PracticalShoppingProductionHomeRefreshDisposition.APPLIED
    }
}

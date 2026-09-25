package com.valuepilot.app

import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.PracticalShoppingPolicy
import com.valuepilot.core.PracticalShoppingProductionOrchestrationRequest
import com.valuepilot.core.PracticalShoppingProductionOrchestrator
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ShoppingStoreKey

/**
 * Stable, display-free identity for one production Home request.
 *
 * The production projection can legitimately be equal for different requests
 * when no price is usable (for example, two different declared stores both
 * produce a no-coverage decision). Keeping only the projection would make a
 * same-generation replay look idempotent when it is actually ambiguous. This
 * identity contains request structure and policy inputs, but no raw provider
 * evidence or display text.
 */
internal data class PracticalShoppingProductionHomeRequestIdentity(
    val itemKeys: List<String>,
    val stores: List<StoreIdentity>,
    val storePairs: List<StorePairIdentity>,
    val priceBindings: List<PriceBindingIdentity>,
    val priceRequestIds: List<String>,
    val evaluatedAtEpochMillis: Long,
    val acceptancePolicy: EvidenceAcceptancePolicy,
    val planningPolicy: PracticalShoppingPolicy
)

internal data class StoreIdentity(
    val storeKey: String,
    val merchantKey: String,
    val locationKey: String?,
    val commerceChannelKey: String,
    val distanceMetres: Long,
    val travelTimeSeconds: Long
)

internal data class StorePairIdentity(
    val baseStoreKey: String,
    val addedStoreKey: String,
    val distanceMetres: Long,
    val travelTimeSeconds: Long
)

internal data class PriceBindingIdentity(
    val itemKey: String,
    val productKey: String,
    val productKeyScope: ProductionProductKeyScope,
    val storeKey: String,
    val currentPriceRequestId: String
)

private fun PracticalShoppingProductionOrchestrationRequest.homeRequestIdentity():
    PracticalShoppingProductionHomeRequestIdentity =
    PracticalShoppingProductionHomeRequestIdentity(
        itemKeys = shoppingRequest.itemKeys.map { itemKey -> itemKey.value },
        stores =
            stores.map { store ->
                StoreIdentity(
                    storeKey = store.storeKey.value,
                    merchantKey = store.merchantKey,
                    locationKey = store.locationKey,
                    commerceChannelKey = store.commerceChannelKey,
                    distanceMetres = store.travelFromUser.distanceMetres,
                    travelTimeSeconds = store.travelFromUser.travelTimeSeconds
                )
            },
        storePairs =
            storePairs.map { pair ->
                StorePairIdentity(
                    baseStoreKey = pair.baseStoreKey.value,
                    addedStoreKey = pair.addedStoreKey.value,
                    distanceMetres = pair.additionalTravel.distanceMetres,
                    travelTimeSeconds = pair.additionalTravel.travelTimeSeconds
                )
            },
        priceBindings =
            priceBindings.map { binding ->
                PriceBindingIdentity(
                    itemKey = binding.itemKey.value,
                    productKey = binding.productKey.value,
                    productKeyScope = binding.productKey.scope,
                    storeKey = binding.storeKey.value,
                    currentPriceRequestId = binding.currentPriceRequestId
                )
            },
        priceRequestIds = priceRequests.map { priceRequest -> priceRequest.requestId },
        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
        acceptancePolicy = acceptancePolicy,
        planningPolicy = planningPolicy
    )

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
    val uiState: PracticalShoppingProductionHomeUiState? = null,
    val requestIdentity: PracticalShoppingProductionHomeRequestIdentity? = null
) {
    init {
        latestGeneration?.let { require(it >= 0L) }
        require(latestGeneration != null || (projection == null && uiState == null)) {
            "A production Home result requires an applied refresh generation"
        }
        require((projection == null) == (uiState == null)) {
            "A production Home projection and UI state must be applied together"
        }
        require((projection == null) == (requestIdentity == null)) {
            "A production Home projection requires its request identity"
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

        val incomingRequestIdentity = request.homeRequestIdentity()

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
            return if (
                refreshState.requestIdentity == incomingRequestIdentity &&
                    refreshState.projection == incomingProjection &&
                    refreshState.uiState == incomingState
            ) {
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
                uiState = incomingState,
                requestIdentity = incomingRequestIdentity
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
                uiState = null,
                requestIdentity = null
            )
        return PracticalShoppingProductionHomeRefreshDisposition.APPLIED
    }
}

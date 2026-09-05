package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductionOrchestrationRequest
import com.valuepilot.core.PracticalShoppingProductionOrchestrationResult
import com.valuepilot.core.PracticalShoppingProductionOrchestrator
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey

/**
 * Whether an immutable production plan is safe to expose to a Home consumer.
 *
 * [UNAVAILABLE] is intentionally distinct from a valid production decision with
 * no price coverage. The former is an orchestration/reference defect or a result
 * whose request no longer passes the structural validation; the latter is a
 * truthful planner outcome that the existing UI projector can explain without
 * inventing a basket.
 */
enum class PracticalShoppingProductionHomeStatus {
    READY,
    UNAVAILABLE
}

/**
 * Android-facing handoff from production orchestration to the existing Home plan
 * projection. No raw evidence, provider identifiers or validation issues are
 * exposed as consumer copy.
 */
data class PracticalShoppingProductionHomeProjection(
    val status: PracticalShoppingProductionHomeStatus,
    val result: PracticalShoppingUiProjection?,
    val notice: String?
) {
    init {
        require((status == PracticalShoppingProductionHomeStatus.READY) == (result != null))
        require((status == PracticalShoppingProductionHomeStatus.UNAVAILABLE) == (notice != null))
        require(notice == null || notice.isNotBlank())
    }

    val state: PracticalShoppingUiState?
        get() = result?.state
}

/**
 * Narrow production-to-Home adapter.
 *
 * The shared-core production orchestrator remains the only authority for
 * evidence eligibility, candidate construction and shopping decisions. This
 * adapter only checks that the supplied request still passes the same structural
 * validation used by the result, then delegates all consumer formatting to the
 * existing [PracticalShoppingUiProjector]. It never ranks candidates,
 * recalculates money, fills missing prices, or turns an invalid result into a
 * no-coverage result.
 */
object PracticalShoppingProductionHomeAdapter {

    const val UNAVAILABLE_NOTICE =
        "This shopping plan is unavailable until its product, store, route and price evidence is verified."

    fun project(
        request: PracticalShoppingProductionOrchestrationRequest,
        orchestrationResult: PracticalShoppingProductionOrchestrationResult,
        storeDisplayNames: Map<ShoppingStoreKey, String>,
        itemDisplayNames: Map<ShoppingItemKey, String>
    ): PracticalShoppingProductionHomeProjection {
        // The validation is structural and side-effect free; it does not re-run
        // any evidence or planner work. The host below this adapter evaluates
        // directly from the request, so callers do not need a detached-result
        // apply path.
        val currentValidation = PracticalShoppingProductionOrchestrator.validate(request)
        val decisionResult = orchestrationResult.decisionResult
        if (
            orchestrationResult.validation != currentValidation ||
                !currentValidation.valid ||
                decisionResult == null
        ) {
            return unavailable()
        }

        val projection =
            try {
                PracticalShoppingUiProjector.project(
                    request = request.shoppingRequest,
                    decision = decisionResult.decision,
                    storeDisplayNames = storeDisplayNames,
                    itemDisplayNames = itemDisplayNames,
                    policy = request.planningPolicy
                )
            } catch (_: IllegalArgumentException) {
                // Missing/unsafe display metadata is a presentation defect, not
                // permission to expose a partial or guessed production result.
                return unavailable()
            }

        return PracticalShoppingProductionHomeProjection(
            status = PracticalShoppingProductionHomeStatus.READY,
            result = projection,
            notice = null
        )
    }

    private fun unavailable(): PracticalShoppingProductionHomeProjection =
        PracticalShoppingProductionHomeProjection(
            status = PracticalShoppingProductionHomeStatus.UNAVAILABLE,
            result = null,
            notice = UNAVAILABLE_NOTICE
        )
}

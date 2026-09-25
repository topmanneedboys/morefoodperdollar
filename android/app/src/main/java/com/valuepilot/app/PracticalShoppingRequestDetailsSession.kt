package com.valuepilot.app

import com.valuepilot.core.ShoppingItemRequestDetail
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingRequestDetails

/**
 * Immutable app owner for typed details attached to one bounded shopping request.
 *
 * Opening a request only accepts lifecycle data whose embedded request is exactly equal.
 * A caller may explicitly reconcile a known revision of the same logical list; no query text,
 * overlap heuristic, product match, price, evidence, ranking, clock, network or UI rule lives
 * here. The existing shared-core value operations remain authoritative for detail edits.
 */
data class PracticalShoppingRequestDetailsSessionState(
    val details: ShoppingRequestDetails?
) {
    val request: ShoppingRequest?
        get() = details?.request
}

object PracticalShoppingRequestDetailsSession {

    fun initial(): PracticalShoppingRequestDetailsSessionState =
        PracticalShoppingRequestDetailsSessionState(details = null)

    /**
     * Opens [request] and recovers saved intent only when its embedded request is exactly equal.
     * Malformed, oversized, stale, reordered or partially overlapping payloads become an empty
     * detail set for this request; they never leak partial intent.
     */
    fun open(
        request: ShoppingRequest,
        encodedLifecycleState: ByteArray? = null
    ): PracticalShoppingRequestDetailsSessionState {
        val restored =
            PracticalShoppingRequestDetailsLifecycleCapsule
                .restore(encodedLifecycleState)
                .detailsForExactRequest(request)

        return PracticalShoppingRequestDetailsSessionState(
            details = restored ?: ShoppingRequestDetails(request = request)
        )
    }

    /** Starts a genuinely new request with no inherited item intent. */
    fun replaceWithNewRequest(
        request: ShoppingRequest
    ): PracticalShoppingRequestDetailsSessionState =
        PracticalShoppingRequestDetailsSessionState(details = ShoppingRequestDetails(request))

    /**
     * Carries details into a caller-confirmed revision of the same logical list.
     * Shared-core drops removed keys and leaves newly introduced keys unspecified.
     */
    fun reconcileTo(
        state: PracticalShoppingRequestDetailsSessionState,
        request: ShoppingRequest
    ): PracticalShoppingRequestDetailsSessionState =
        PracticalShoppingRequestDetailsSessionState(
            details = state.details?.reconciledTo(request) ?: ShoppingRequestDetails(request)
        )

    fun withItemDetail(
        state: PracticalShoppingRequestDetailsSessionState,
        detail: ShoppingItemRequestDetail
    ): PracticalShoppingRequestDetailsSessionState =
        PracticalShoppingRequestDetailsSessionState(
            details = requireNotNull(state.details).withItemDetail(detail)
        )

    fun withoutItemDetail(
        state: PracticalShoppingRequestDetailsSessionState,
        itemKey: ShoppingItemKey
    ): PracticalShoppingRequestDetailsSessionState =
        PracticalShoppingRequestDetailsSessionState(
            details = requireNotNull(state.details).withoutItemDetail(itemKey)
        )

    /** Emits a fresh bounded payload for lifecycle storage, or null for an unopened session. */
    fun encodedOrNull(state: PracticalShoppingRequestDetailsSessionState): ByteArray? =
        state.details?.let {
            PracticalShoppingRequestDetailsLifecycleCapsule
                .fromDetails(it)
                .encodedOrNull()
        }
}

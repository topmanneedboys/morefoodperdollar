package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingProductIdentityCandidate
import com.valuepilot.core.PracticalShoppingStoreIdentityCandidate

/**
 * Ephemeral command accepted only after a separate exact-confirmation boundary has run.
 *
 * These commands are not identity authority. The local gateway delegates to
 * [PracticalShoppingRememberConfirmedChoiceCoordinator], which re-checks USER_CONFIRMED exact
 * relationships before any persistence is attempted.
 */
sealed interface PracticalShoppingRememberConfirmedChoiceRequest {
    data class ProductWithUserLabel(
        val confirmedCandidate: PracticalShoppingProductIdentityCandidate,
        val displayName: String
    ) : PracticalShoppingRememberConfirmedChoiceRequest

    data class OpenFoodFactsProduct(
        val confirmedCandidate: PracticalShoppingProductIdentityCandidate,
        val row: OpenFoodFactsImportedProduct
    ) : PracticalShoppingRememberConfirmedChoiceRequest

    data class StoreWithUserLabel(
        val confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
        val displayName: String
    ) : PracticalShoppingRememberConfirmedChoiceRequest

    data class OpenStreetMapStore(
        val confirmedCandidate: PracticalShoppingStoreIdentityCandidate,
        val row: OpenStreetMapPracticalShoppingStoreDisplayRecord
    ) : PracticalShoppingRememberConfirmedChoiceRequest
}

fun interface PracticalShoppingRememberConfirmedChoiceGateway {
    fun remember(
        request: PracticalShoppingRememberConfirmedChoiceRequest
    ): PracticalShoppingRememberConfirmedChoiceResult
}

/** Thin gateway over the already-verified remember/persistence coordinator. */
class PracticalShoppingRememberConfirmedChoiceLocalGateway(
    private val exactStore: PracticalShoppingSavedExactPreferenceLocalStore,
    private val displayStore: PracticalShoppingSavedDisplayMetadataLocalStore
) : PracticalShoppingRememberConfirmedChoiceGateway {
    override fun remember(
        request: PracticalShoppingRememberConfirmedChoiceRequest
    ): PracticalShoppingRememberConfirmedChoiceResult =
        when (request) {
            is PracticalShoppingRememberConfirmedChoiceRequest.ProductWithUserLabel ->
                PracticalShoppingRememberConfirmedChoiceCoordinator.rememberProductWithUserLabel(
                    exactStore = exactStore,
                    displayStore = displayStore,
                    confirmedCandidate = request.confirmedCandidate,
                    displayName = request.displayName
                )

            is PracticalShoppingRememberConfirmedChoiceRequest.OpenFoodFactsProduct ->
                PracticalShoppingRememberConfirmedChoiceCoordinator.rememberOpenFoodFactsProduct(
                    exactStore = exactStore,
                    displayStore = displayStore,
                    confirmedCandidate = request.confirmedCandidate,
                    row = request.row
                )

            is PracticalShoppingRememberConfirmedChoiceRequest.StoreWithUserLabel ->
                PracticalShoppingRememberConfirmedChoiceCoordinator.rememberStoreWithUserLabel(
                    exactStore = exactStore,
                    displayStore = displayStore,
                    confirmedCandidate = request.confirmedCandidate,
                    displayName = request.displayName
                )

            is PracticalShoppingRememberConfirmedChoiceRequest.OpenStreetMapStore ->
                PracticalShoppingRememberConfirmedChoiceCoordinator.rememberOpenStreetMapStore(
                    exactStore = exactStore,
                    displayStore = displayStore,
                    confirmedCandidate = request.confirmedCandidate,
                    row = request.row
                )
        }
}

sealed interface PracticalShoppingRememberConfirmedChoiceExecutionOutcome {
    data class Completed(
        val result: PracticalShoppingRememberConfirmedChoiceResult
    ) : PracticalShoppingRememberConfirmedChoiceExecutionOutcome

    /** Unexpected execution failure; exception text is intentionally not propagated to UI owners. */
    data object Failed : PracticalShoppingRememberConfirmedChoiceExecutionOutcome
}

data class PracticalShoppingRememberConfirmedChoiceCompletion(
    val requestId: Long,
    val outcome: PracticalShoppingRememberConfirmedChoiceExecutionOutcome
) {
    init {
        require(requestId > 0L)
    }
}

fun interface PracticalShoppingRememberConfirmedChoiceCompletionListener {
    fun onCompleted(completion: PracticalShoppingRememberConfirmedChoiceCompletion)
}

/**
 * Execution owner for future user-facing Remember actions.
 *
 * The host owns sequencing only. It performs no Android View work and owns no filesystem, clock,
 * provider, identity, price, travel, or ranking policy. The supplied worker must serialize the
 * coordinator call away from the UI owner thread; the completion dispatcher returns a typed
 * completion to that owner thread.
 *
 * Only one Remember request may be in flight per host. This prevents double taps from generating
 * duplicate persistence work while preserving the process runtime's stronger FIFO ordering with
 * Saved loads/deletes. Closing a host does not cancel an already-running atomic operation; it
 * suppresses that operation's late completion and all future requests.
 */
class PracticalShoppingRememberConfirmedChoiceHost(
    private val gateway: PracticalShoppingRememberConfirmedChoiceGateway,
    private val worker: PracticalShoppingSavedWorkScheduler,
    private val completionDispatcher: PracticalShoppingSavedCompletionDispatcher,
    private val completionListener: PracticalShoppingRememberConfirmedChoiceCompletionListener
) : AutoCloseable {
    private var nextRequestId = 1L
    private var activeRequestId: Long? = null
    private var closed = false

    @Synchronized
    fun remember(request: PracticalShoppingRememberConfirmedChoiceRequest): Boolean {
        if (closed || activeRequestId != null) return false

        val requestId = nextRequestId
        nextRequestId += 1L
        activeRequestId = requestId
        schedule(requestId, request)
        return true
    }

    @Synchronized
    fun isBusy(): Boolean = !closed && activeRequestId != null

    @Synchronized
    fun isClosed(): Boolean = closed

    @Synchronized
    override fun close() {
        closed = true
        activeRequestId = null
    }

    private fun schedule(
        requestId: Long,
        request: PracticalShoppingRememberConfirmedChoiceRequest
    ) {
        worker.schedule {
            val outcome =
                try {
                    PracticalShoppingRememberConfirmedChoiceExecutionOutcome.Completed(
                        gateway.remember(request)
                    )
                } catch (_: Exception) {
                    PracticalShoppingRememberConfirmedChoiceExecutionOutcome.Failed
                }
            val completion =
                PracticalShoppingRememberConfirmedChoiceCompletion(
                    requestId = requestId,
                    outcome = outcome
                )

            completionDispatcher.dispatch {
                val shouldDeliver =
                    synchronized(this@PracticalShoppingRememberConfirmedChoiceHost) {
                        if (closed || activeRequestId != requestId) {
                            false
                        } else {
                            activeRequestId = null
                            true
                        }
                    }

                if (shouldDeliver) {
                    completionListener.onCompleted(completion)
                }
            }
        }
    }
}

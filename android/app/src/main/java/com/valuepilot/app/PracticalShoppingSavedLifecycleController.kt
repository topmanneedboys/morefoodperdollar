package com.valuepilot.app

/**
 * Immutable lifecycle state for the Saved experience.
 *
 * This controller owns no Android classes, executors, filesystem access, clocks, or network.
 * It only sequences already-typed Saved coordinator work and rejects stale completions.
 */
enum class PracticalShoppingSavedLifecycleStatus {
    IDLE,
    LOADING,
    READY,
    DEGRADED,
    MUTATING,
    ERROR
}

enum class PracticalShoppingSavedLifecycleFailure {
    LOAD_FAILED,
    ACTION_FAILED
}

data class PracticalShoppingSavedLifecycleState(
    val status: PracticalShoppingSavedLifecycleStatus,
    val projection: PracticalShoppingSavedExactPreferenceUiProjection?,
    val activeRequestId: Long?,
    val nextRequestId: Long,
    val pendingAction: PracticalShoppingSavedExactPreferenceUiAction?,
    val failure: PracticalShoppingSavedLifecycleFailure?,
    val displayMetadataDegraded: Boolean,
    val displayCleanupDegraded: Boolean
) {
    init {
        require(nextRequestId > 0L)
        require(
            (activeRequestId != null) ==
                (status == PracticalShoppingSavedLifecycleStatus.LOADING ||
                    status == PracticalShoppingSavedLifecycleStatus.MUTATING)
        )
        require(
            (pendingAction != null) ==
                (status == PracticalShoppingSavedLifecycleStatus.MUTATING)
        )
        require(failure == null || status == PracticalShoppingSavedLifecycleStatus.ERROR)
        require(
            status !in setOf(
                PracticalShoppingSavedLifecycleStatus.READY,
                PracticalShoppingSavedLifecycleStatus.DEGRADED
            ) || projection != null
        )
        require(
            status != PracticalShoppingSavedLifecycleStatus.READY ||
                (!displayMetadataDegraded && !displayCleanupDegraded)
        )
        require(
            status != PracticalShoppingSavedLifecycleStatus.DEGRADED ||
                (displayMetadataDegraded || displayCleanupDegraded)
        )
    }
}

sealed interface PracticalShoppingSavedLifecycleIntent {
    data object Refresh : PracticalShoppingSavedLifecycleIntent

    data class ActionSelected(
        val action: PracticalShoppingSavedExactPreferenceUiAction
    ) : PracticalShoppingSavedLifecycleIntent

    data class LoadCompleted(
        val requestId: Long,
        val result: PracticalShoppingSavedExperienceLoadResult
    ) : PracticalShoppingSavedLifecycleIntent {
        init {
            require(requestId > 0L)
        }
    }

    data class ActionCompleted(
        val requestId: Long,
        val result: PracticalShoppingSavedExperienceActionResult
    ) : PracticalShoppingSavedLifecycleIntent {
        init {
            require(requestId > 0L)
        }
    }
}

sealed interface PracticalShoppingSavedLifecycleWork {
    val requestId: Long

    data class Load(
        override val requestId: Long
    ) : PracticalShoppingSavedLifecycleWork {
        init {
            require(requestId > 0L)
        }
    }

    data class Mutate(
        override val requestId: Long,
        val action: PracticalShoppingSavedExactPreferenceUiAction
    ) : PracticalShoppingSavedLifecycleWork {
        init {
            require(requestId > 0L)
        }
    }
}

data class PracticalShoppingSavedLifecycleTransition(
    val state: PracticalShoppingSavedLifecycleState,
    val work: PracticalShoppingSavedLifecycleWork? = null
)

/**
 * Pure Saved lifecycle reducer.
 *
 * File I/O stays outside this controller. A platform adapter executes emitted [work] away
 * from the main thread and returns the corresponding typed completion intent. Request ids
 * prevent an older completion from replacing a newer Saved state.
 *
 * Successful mutations are never patched into the projection locally. Instead they always
 * trigger a fresh authoritative load through [PracticalShoppingSavedExperienceCoordinator],
 * preserving the exact-preference-first and bind-before-project guarantees.
 */
class PracticalShoppingSavedLifecycleController {

    fun initialState(
        nextRequestId: Long = 1L
    ): PracticalShoppingSavedLifecycleState {
        require(nextRequestId > 0L)
        return PracticalShoppingSavedLifecycleState(
            status = PracticalShoppingSavedLifecycleStatus.IDLE,
            projection = null,
            activeRequestId = null,
            nextRequestId = nextRequestId,
            pendingAction = null,
            failure = null,
            displayMetadataDegraded = false,
            displayCleanupDegraded = false
        )
    }

    fun reduce(
        previous: PracticalShoppingSavedLifecycleState,
        intent: PracticalShoppingSavedLifecycleIntent
    ): PracticalShoppingSavedLifecycleTransition =
        when (intent) {
            PracticalShoppingSavedLifecycleIntent.Refresh -> refresh(previous)
            is PracticalShoppingSavedLifecycleIntent.ActionSelected ->
                actionSelected(previous, intent.action)
            is PracticalShoppingSavedLifecycleIntent.LoadCompleted ->
                loadCompleted(previous, intent.requestId, intent.result)
            is PracticalShoppingSavedLifecycleIntent.ActionCompleted ->
                actionCompleted(previous, intent.requestId, intent.result)
        }

    private fun refresh(
        previous: PracticalShoppingSavedLifecycleState
    ): PracticalShoppingSavedLifecycleTransition {
        if (previous.activeRequestId != null) {
            return PracticalShoppingSavedLifecycleTransition(previous)
        }
        return issueLoad(
            previous = previous,
            preserveProjection = previous.projection,
            preserveCleanupDegradation = false
        )
    }

    private fun actionSelected(
        previous: PracticalShoppingSavedLifecycleState,
        action: PracticalShoppingSavedExactPreferenceUiAction
    ): PracticalShoppingSavedLifecycleTransition {
        if (previous.activeRequestId != null || !isCurrentAction(previous.projection, action)) {
            return PracticalShoppingSavedLifecycleTransition(previous)
        }

        val requestId = previous.nextRequestId
        val next =
            previous.copy(
                status = PracticalShoppingSavedLifecycleStatus.MUTATING,
                activeRequestId = requestId,
                nextRequestId = incrementRequestId(requestId),
                pendingAction = action,
                failure = null
            )
        return PracticalShoppingSavedLifecycleTransition(
            state = next,
            work = PracticalShoppingSavedLifecycleWork.Mutate(requestId, action)
        )
    }

    private fun loadCompleted(
        previous: PracticalShoppingSavedLifecycleState,
        requestId: Long,
        result: PracticalShoppingSavedExperienceLoadResult
    ): PracticalShoppingSavedLifecycleTransition {
        if (
            previous.status != PracticalShoppingSavedLifecycleStatus.LOADING ||
            previous.activeRequestId != requestId
        ) {
            return PracticalShoppingSavedLifecycleTransition(previous)
        }

        if (!result.accepted) {
            return PracticalShoppingSavedLifecycleTransition(
                previous.copy(
                    status = PracticalShoppingSavedLifecycleStatus.ERROR,
                    projection = null,
                    activeRequestId = null,
                    pendingAction = null,
                    failure = PracticalShoppingSavedLifecycleFailure.LOAD_FAILED,
                    displayMetadataDegraded = false,
                    displayCleanupDegraded = false
                )
            )
        }

        val metadataDegraded = result.displayMetadataDegraded
        val cleanupDegraded = previous.displayCleanupDegraded
        return PracticalShoppingSavedLifecycleTransition(
            previous.copy(
                status =
                    if (metadataDegraded || cleanupDegraded) {
                        PracticalShoppingSavedLifecycleStatus.DEGRADED
                    } else {
                        PracticalShoppingSavedLifecycleStatus.READY
                    },
                projection = requireNotNull(result.projection),
                activeRequestId = null,
                pendingAction = null,
                failure = null,
                displayMetadataDegraded = metadataDegraded,
                displayCleanupDegraded = cleanupDegraded
            )
        )
    }

    private fun actionCompleted(
        previous: PracticalShoppingSavedLifecycleState,
        requestId: Long,
        result: PracticalShoppingSavedExperienceActionResult
    ): PracticalShoppingSavedLifecycleTransition {
        if (
            previous.status != PracticalShoppingSavedLifecycleStatus.MUTATING ||
            previous.activeRequestId != requestId
        ) {
            return PracticalShoppingSavedLifecycleTransition(previous)
        }

        if (!result.accepted) {
            return PracticalShoppingSavedLifecycleTransition(
                previous.copy(
                    status = PracticalShoppingSavedLifecycleStatus.ERROR,
                    projection = null,
                    activeRequestId = null,
                    pendingAction = null,
                    failure = PracticalShoppingSavedLifecycleFailure.ACTION_FAILED,
                    displayMetadataDegraded = false,
                    displayCleanupDegraded = false
                )
            )
        }

        return issueLoad(
            previous = previous.copy(
                activeRequestId = null,
                pendingAction = null,
                failure = null,
                displayCleanupDegraded = result.displayCleanupDegraded
            ),
            preserveProjection = null,
            preserveCleanupDegradation = true
        )
    }

    private fun issueLoad(
        previous: PracticalShoppingSavedLifecycleState,
        preserveProjection: PracticalShoppingSavedExactPreferenceUiProjection?,
        preserveCleanupDegradation: Boolean
    ): PracticalShoppingSavedLifecycleTransition {
        val requestId = previous.nextRequestId
        val next =
            previous.copy(
                status = PracticalShoppingSavedLifecycleStatus.LOADING,
                projection = preserveProjection,
                activeRequestId = requestId,
                nextRequestId = incrementRequestId(requestId),
                pendingAction = null,
                failure = null,
                displayMetadataDegraded = false,
                displayCleanupDegraded =
                    if (preserveCleanupDegradation) previous.displayCleanupDegraded else false
            )
        return PracticalShoppingSavedLifecycleTransition(
            state = next,
            work = PracticalShoppingSavedLifecycleWork.Load(requestId)
        )
    }

    private fun isCurrentAction(
        projection: PracticalShoppingSavedExactPreferenceUiProjection?,
        action: PracticalShoppingSavedExactPreferenceUiAction
    ): Boolean {
        val state = projection?.state ?: return false
        return when (action) {
            is PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct ->
                state.productRows.any { row -> row.action == action }

            is PracticalShoppingSavedExactPreferenceUiAction.DeleteStore ->
                state.storeRows.any { row -> row.action == action }

            PracticalShoppingSavedExactPreferenceUiAction.ClearAll ->
                state.clearAllAction == PracticalShoppingSavedExactPreferenceUiAction.ClearAll
        }
    }

    private fun incrementRequestId(current: Long): Long {
        require(current < Long.MAX_VALUE)
        return current + 1L
    }
}

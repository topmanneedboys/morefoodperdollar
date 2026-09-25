package com.valuepilot.app

/** Immutable foreground presentation for the explicit observed-price confirmation action. */
internal data class UserObservedPriceConfirmationActionUiState(
    val status: Status,
    val message: String,
    val actionLabel: String = "Confirm observed price",
    val actionEnabled: Boolean
) {
    enum class Status {
        INACTIVE,
        READY,
        SUBMITTING,
        NOT_ACCEPTED,
        CONFIRMED,
        PROOF_REJECTED,
        CONFIRMATION_REJECTED,
        STORAGE_REJECTED,
        EXECUTION_FAILED,
        CURRENT_DRAFT_CHANGED_AFTER_COMPLETION
    }
}

internal fun interface UserObservedPriceConfirmationActionSurfaceRenderer {
    fun render(state: UserObservedPriceConfirmationActionUiState)
}

/**
 * Foreground state owner for the explicit confirmation action.
 *
 * A successful call to [submitAction] means only that the existing execution host accepted the
 * immutable submission for background work. Durable success is shown only after the existing typed
 * completion reports an accepted transaction result.
 *
 * This controller deliberately keeps no draft values, identifiers, timestamps, proof references,
 * proof bytes, Android URI, storage paths, or exception details. If the editable draft/proof changes
 * while one immutable submission is in flight, that completion is never presented as confirmation
 * of the changed draft. Leaving the route also detaches the foreground state from any older in-flight
 * completion while allowing the existing atomic execution host to finish its work.
 */
internal class UserObservedPriceConfirmationActionPresentationController(
    private val renderer: UserObservedPriceConfirmationActionSurfaceRenderer,
    private val submitAction: () -> Boolean
) : UserObservedPriceConfirmationCompletionListener, AutoCloseable {

    private var routeVisible = false
    private var submitting = false
    private var changedWhileSubmitting = false
    private var closed = false
    private var currentStatus = UserObservedPriceConfirmationActionUiState.Status.INACTIVE

    init {
        render(inactiveState())
    }

    fun onRouteVisibilityChanged(visible: Boolean) {
        if (closed || visible == routeVisible) return

        routeVisible = visible
        if (!visible) {
            // The execution host may still finish its immutable request, but this foreground route
            // must never let that completion confirm a future route instance.
            submitting = false
            changedWhileSubmitting = false
            render(inactiveState())
            return
        }

        render(readyState())
    }

    fun onDraftOrProofChanged() {
        if (closed || !routeVisible) return
        if (submitting) {
            changedWhileSubmitting = true
            return
        }
        if (currentStatus != UserObservedPriceConfirmationActionUiState.Status.READY) {
            render(readyState())
        }
    }

    fun onSubmitRequested() {
        if (closed || !routeVisible || submitting) return
        if (currentStatus == UserObservedPriceConfirmationActionUiState.Status.CONFIRMED) return

        val acceptedForExecution = submitAction()
        if (!acceptedForExecution) {
            render(
                state(
                    status = UserObservedPriceConfirmationActionUiState.Status.NOT_ACCEPTED,
                    message =
                        "Not submitted. Complete the required fields and proof, then try again.",
                    actionEnabled = true
                )
            )
            return
        }

        submitting = true
        changedWhileSubmitting = false
        render(
            state(
                status = UserObservedPriceConfirmationActionUiState.Status.SUBMITTING,
                message =
                    "Checking the confirmation and retaining the proof safely on this device.",
                actionEnabled = false
            )
        )
    }

    override fun onCompleted(completion: UserObservedPriceConfirmationCompletion) {
        if (closed || !routeVisible || !submitting) return

        submitting = false
        if (changedWhileSubmitting) {
            changedWhileSubmitting = false
            val previousAccepted = completion.acceptedTransaction()
            render(
                state(
                    status =
                        UserObservedPriceConfirmationActionUiState.Status
                            .CURRENT_DRAFT_CHANGED_AFTER_COMPLETION,
                    message =
                        if (previousAccepted) {
                            "A previous confirmation completed, but the current draft changed. " +
                                "Confirm again to submit the current values."
                        } else {
                            "The previous attempt finished, but the current draft changed. " +
                                "Review it and confirm the current values."
                        },
                    actionEnabled = true
                )
            )
            return
        }

        render(completion.toUiState())
    }

    fun isSubmitting(): Boolean = !closed && submitting

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return
        closed = true
        routeVisible = false
        submitting = false
        changedWhileSubmitting = false
        render(inactiveState())
    }

    private fun render(state: UserObservedPriceConfirmationActionUiState) {
        currentStatus = state.status
        renderer.render(state)
    }

    private fun inactiveState(): UserObservedPriceConfirmationActionUiState =
        state(
            status = UserObservedPriceConfirmationActionUiState.Status.INACTIVE,
            message = "Confirmation is available only while this draft is open.",
            actionEnabled = false
        )

    private fun readyState(): UserObservedPriceConfirmationActionUiState =
        state(
            status = UserObservedPriceConfirmationActionUiState.Status.READY,
            message =
                "When the required fields and proof are ready, confirm the observed price.",
            actionEnabled = true
        )

    private fun UserObservedPriceConfirmationCompletion.acceptedTransaction(): Boolean =
        when (val execution = outcome) {
            is UserObservedPriceConfirmationExecutionOutcome.Completed -> execution.result.accepted
            UserObservedPriceConfirmationExecutionOutcome.Failed -> false
        }

    private fun UserObservedPriceConfirmationCompletion.toUiState():
        UserObservedPriceConfirmationActionUiState =
        when (val execution = outcome) {
            UserObservedPriceConfirmationExecutionOutcome.Failed ->
                state(
                    status = UserObservedPriceConfirmationActionUiState.Status.EXECUTION_FAILED,
                    message = "Confirmation could not be completed. Try again.",
                    actionEnabled = true
                )

            is UserObservedPriceConfirmationExecutionOutcome.Completed -> {
                val result = execution.result
                when {
                    result.accepted ->
                        state(
                            status = UserObservedPriceConfirmationActionUiState.Status.CONFIRMED,
                            message =
                                if (result.proofAlreadyRetained) {
                                    "Observed price confirmed. The proof was already retained and " +
                                        "verified on this device."
                                } else {
                                    "Observed price confirmed. The proof was retained and verified " +
                                        "on this device."
                                },
                            actionEnabled = false
                        )

                    result.artifactFailures.isNotEmpty() ->
                        state(
                            status = UserObservedPriceConfirmationActionUiState.Status.PROOF_REJECTED,
                            message = "The selected proof could not be accepted. Choose valid proof and try again.",
                            actionEnabled = true
                        )

                    result.confirmationFailures.isNotEmpty() ->
                        state(
                            status =
                                UserObservedPriceConfirmationActionUiState.Status.CONFIRMATION_REJECTED,
                            message = "The confirmation details were not accepted. Review them and try again.",
                            actionEnabled = true
                        )

                    result.storageIssue != null ->
                        state(
                            status =
                                UserObservedPriceConfirmationActionUiState.Status.STORAGE_REJECTED,
                            message = "The proof could not be safely retained on this device. Try again.",
                            actionEnabled = true
                        )

                    else ->
                        state(
                            status = UserObservedPriceConfirmationActionUiState.Status.EXECUTION_FAILED,
                            message = "Confirmation could not be completed. Try again.",
                            actionEnabled = true
                        )
                }
            }
        }

    private fun state(
        status: UserObservedPriceConfirmationActionUiState.Status,
        message: String,
        actionEnabled: Boolean
    ): UserObservedPriceConfirmationActionUiState =
        UserObservedPriceConfirmationActionUiState(
            status = status,
            message = message,
            actionEnabled = actionEnabled
        )
}

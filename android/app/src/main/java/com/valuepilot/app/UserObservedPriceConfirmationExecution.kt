package com.valuepilot.app

/** Executes observed-price confirmation work away from its foreground owner thread. */
internal fun interface UserObservedPriceConfirmationWorkScheduler {
    fun schedule(block: () -> Unit)
}

/** Returns completed observed-price confirmation work to its foreground owner thread. */
internal fun interface UserObservedPriceConfirmationCompletionDispatcher {
    fun dispatch(block: () -> Unit)
}

/** Testable gateway over the already-verified confirmation transaction. */
internal fun interface UserObservedPriceConfirmationGateway {
    fun confirmAndRetain(
        artifactId: String,
        proofType: UserProvidedPriceProofType,
        artifactBytes: ByteArray,
        fields: UserObservedPriceConfirmationFields
    ): UserObservedPriceConfirmationTransactionResult
}

internal class UserObservedPriceConfirmationLocalGateway(
    private val transaction: UserObservedPriceConfirmationTransaction
) : UserObservedPriceConfirmationGateway {
    override fun confirmAndRetain(
        artifactId: String,
        proofType: UserProvidedPriceProofType,
        artifactBytes: ByteArray,
        fields: UserObservedPriceConfirmationFields
    ): UserObservedPriceConfirmationTransactionResult =
        transaction.confirmAndRetain(
            artifactId = artifactId,
            proofType = proofType,
            artifactBytes = artifactBytes,
            fields = fields
        )
}

internal sealed interface UserObservedPriceConfirmationExecutionOutcome {
    data class Completed(
        val result: UserObservedPriceConfirmationTransactionResult
    ) : UserObservedPriceConfirmationExecutionOutcome

    /** Unexpected execution failure; exception details are intentionally not forwarded. */
    data object Failed : UserObservedPriceConfirmationExecutionOutcome
}

internal data class UserObservedPriceConfirmationCompletion(
    val requestId: Long,
    val outcome: UserObservedPriceConfirmationExecutionOutcome
) {
    init {
        require(requestId > 0L)
    }
}

internal fun interface UserObservedPriceConfirmationCompletionListener {
    fun onCompleted(completion: UserObservedPriceConfirmationCompletion)
}

/**
 * Execution owner for one future foreground observed-price confirmation flow.
 *
 * The host owns sequencing only. All identifiers, timestamps, exact confirmation fields, proof
 * type, and proof bytes are supplied by the caller and forwarded without interpretation to the
 * verified transaction gateway. It does not capture proof, read a clock, generate IDs, create
 * evidence, resolve quantity, evaluate freshness/ranking, render UI, or activate a route.
 *
 * Proof bytes are never stored in host state. One private working copy is captured only by the
 * queued work item so caller mutation cannot change an accepted submission; that copy is wiped in
 * a finally block immediately after gateway execution. Only one request may be in flight per host.
 *
 * Closing the host does not attempt to cancel an already-running atomic proof retention. Existing
 * work may finish, but its late completion is suppressed and future submissions are rejected.
 */
internal class UserObservedPriceConfirmationExecutionHost(
    private val gateway: UserObservedPriceConfirmationGateway,
    private val worker: UserObservedPriceConfirmationWorkScheduler,
    private val completionDispatcher: UserObservedPriceConfirmationCompletionDispatcher,
    private val completionListener: UserObservedPriceConfirmationCompletionListener
) : AutoCloseable {
    private var nextRequestId = 1L
    private var activeRequestId: Long? = null
    private var closed = false

    @Synchronized
    fun submit(
        artifactId: String,
        proofType: UserProvidedPriceProofType,
        artifactBytes: ByteArray,
        fields: UserObservedPriceConfirmationFields
    ): Boolean {
        if (closed || activeRequestId != null) return false

        val requestId = nextRequestId
        nextRequestId += 1L
        activeRequestId = requestId
        val proofBytes = artifactBytes.copyOf()

        return try {
            worker.schedule {
                val outcome =
                    try {
                        UserObservedPriceConfirmationExecutionOutcome.Completed(
                            gateway.confirmAndRetain(
                                artifactId = artifactId,
                                proofType = proofType,
                                artifactBytes = proofBytes,
                                fields = fields
                            )
                        )
                    } catch (_: Exception) {
                        UserObservedPriceConfirmationExecutionOutcome.Failed
                    } finally {
                        proofBytes.fill(0)
                    }

                val completion =
                    UserObservedPriceConfirmationCompletion(
                        requestId = requestId,
                        outcome = outcome
                    )

                try {
                    completionDispatcher.dispatch {
                        val shouldDeliver =
                            synchronized(this@UserObservedPriceConfirmationExecutionHost) {
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
                } catch (_: Exception) {
                    synchronized(this@UserObservedPriceConfirmationExecutionHost) {
                        if (activeRequestId == requestId) {
                            activeRequestId = null
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            proofBytes.fill(0)
            if (activeRequestId == requestId) {
                activeRequestId = null
            }
            false
        }
    }

    @Synchronized
    fun isBusy(): Boolean = !closed && activeRequestId != null

    @Synchronized
    fun isClosed(): Boolean = closed

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        activeRequestId = null
    }
}

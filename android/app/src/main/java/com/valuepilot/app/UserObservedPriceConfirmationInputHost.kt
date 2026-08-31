package com.valuepilot.app

/**
 * Observer for the exact result of one explicit observed-price confirmation submission.
 *
 * The result is forwarded unchanged. This contract does not grant presentation, evidence,
 * freshness, ranking, persistence-lifecycle, or current-price authority.
 */
internal fun interface UserObservedPriceConfirmationResultObserver {
    fun onResult(result: UserObservedPriceConfirmationTransactionResult)
}

/**
 * Foreground-only one-shot handoff into [UserObservedPriceConfirmationTransaction].
 *
 * Raw proof bytes exist only as a synchronous [submit] argument. This host never stores them,
 * exposes a session snapshot, or carries them across submissions. All identifiers, timestamps,
 * product/store fields, proof type, and proof bytes are caller supplied and are forwarded without
 * interpretation to the existing transaction boundary.
 *
 * The caller owns capture, execution context, and lifecycle composition. This host owns only its
 * open/closed foreground gate and result forwarding. It does not capture or interpret proof,
 * generate identifiers, read a clock, create evidence, resolve quantity, evaluate policy, render
 * UI, or activate a route.
 */
internal class UserObservedPriceConfirmationInputHost(
    private val transaction: UserObservedPriceConfirmationTransaction,
    private val resultObserver: UserObservedPriceConfirmationResultObserver =
        UserObservedPriceConfirmationResultObserver { }
) : AutoCloseable {

    private var closed = false

    fun submit(
        artifactId: String,
        proofType: UserProvidedPriceProofType,
        artifactBytes: ByteArray,
        fields: UserObservedPriceConfirmationFields
    ) {
        if (closed) return

        val result =
            transaction.confirmAndRetain(
                artifactId = artifactId,
                proofType = proofType,
                artifactBytes = artifactBytes,
                fields = fields
            )
        resultObserver.onResult(result)
    }

    fun isClosed(): Boolean = closed

    override fun close() {
        if (closed) return
        closed = true
    }
}

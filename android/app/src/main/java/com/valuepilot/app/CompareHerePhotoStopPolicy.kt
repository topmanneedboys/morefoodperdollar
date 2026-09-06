package com.valuepilot.app

/**
 * The user-visible terminal reason for an OCR worker that is already finishing.
 *
 * This is lifecycle/presentation state only. It carries no OCR text, product,
 * package, price, currency, evidence or comparison authority.
 */
internal enum class CompareHerePhotoStopReason {
    USER_CANCELLED,
    DRAFT_CHANGED
}

internal data class CompareHerePhotoStopState(
    val requestId: Long,
    val reason: CompareHerePhotoStopReason
) {
    init {
        require(requestId > 0L)
    }
}

internal data class CompareHerePhotoStopCompletion(
    val pending: CompareHerePhotoStopState?,
    val completedReason: CompareHerePhotoStopReason?
) {
    init {
        require(pending == null || completedReason == null) {
            "A stop cannot remain pending after it completes"
        }
    }
}

internal object CompareHerePhotoStopPolicy {

    /** Tracks a terminal reason only when the asynchronous OCR worker is still active. */
    fun begin(
        recognition: CompareHerePhotoRecognitionState,
        reason: CompareHerePhotoStopReason
    ): CompareHerePhotoStopState? =
        recognition.activeRequestId?.let { requestId ->
            CompareHerePhotoStopState(requestId = requestId, reason = reason)
        }

    /**
     * Consumes only the matching worker completion. A stale callback cannot clear a newer stop
     * state or manufacture a terminal message for the wrong request.
     */
    fun complete(
        pending: CompareHerePhotoStopState?,
        callbackRequestId: Long
    ): CompareHerePhotoStopCompletion {
        require(callbackRequestId > 0L)
        if (pending == null) {
            return CompareHerePhotoStopCompletion(
                pending = null,
                completedReason = null
            )
        }
        if (pending.requestId != callbackRequestId) {
            return CompareHerePhotoStopCompletion(
                pending = pending,
                completedReason = null
            )
        }
        return CompareHerePhotoStopCompletion(
            pending = null,
            completedReason = pending.reason
        )
    }
}

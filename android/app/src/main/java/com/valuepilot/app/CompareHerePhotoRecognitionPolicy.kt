package com.valuepilot.app

/**
 * Lifecycle-only gate for the asynchronous OCR worker.
 *
 * The ML Kit task is asynchronous even after the bounded photo executor accepts it. Keeping its
 * request id here prevents a cancelled/slow read from being joined by another read before the
 * first worker has released its resources. It carries no OCR text, product, price or comparison
 * authority.
 */
internal data class CompareHerePhotoRecognitionState(
    val activeRequestId: Long? = null
) {
    init {
        require(activeRequestId == null || activeRequestId > 0L)
    }
}

internal object CompareHerePhotoRecognitionPolicy {

    /** Returns an active worker state, or null when another worker is still finishing. */
    fun begin(
        previous: CompareHerePhotoRecognitionState,
        requestId: Long
    ): CompareHerePhotoRecognitionState? {
        require(requestId > 0L)
        if (previous.activeRequestId != null) return null
        return CompareHerePhotoRecognitionState(activeRequestId = requestId)
    }

    /** Releases only the worker that posted this completion; stale completions cannot release a newer one. */
    fun complete(
        previous: CompareHerePhotoRecognitionState,
        callbackRequestId: Long
    ): CompareHerePhotoRecognitionState {
        require(callbackRequestId > 0L)
        return if (previous.activeRequestId == callbackRequestId) {
            CompareHerePhotoRecognitionState()
        } else {
            previous
        }
    }
}

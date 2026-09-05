package com.valuepilot.app

/**
 * Small lifecycle gate for the asynchronous Compare Here photo path.
 *
 * A photo result is only eligible while the request is still active and its generation matches
 * the current draft. Draft edits, clear actions and teardown invalidate the generation before
 * returning control to the shopper. This class carries no OCR, product, price or comparison data.
 */
internal data class CompareHerePhotoRequestState(
    val requestId: Long,
    val inFlight: Boolean
) {
    init {
        require(requestId >= 0L)
    }

    companion object {
        fun initial(): CompareHerePhotoRequestState =
            CompareHerePhotoRequestState(
                requestId = 0L,
                inFlight = false
            )
    }
}

internal object CompareHerePhotoRequestPolicy {

    fun begin(previous: CompareHerePhotoRequestState): CompareHerePhotoRequestState =
        previous.copy(
            requestId = nextRequestId(previous.requestId),
            inFlight = true
        )

    /**
     * Invalidates any result already posted by the old request and leaves the route idle.
     */
    fun invalidate(previous: CompareHerePhotoRequestState): CompareHerePhotoRequestState =
        previous.copy(
            requestId = nextRequestId(previous.requestId),
            inFlight = false
        )

    fun accepts(
        callbackRequestId: Long,
        current: CompareHerePhotoRequestState,
        closed: Boolean
    ): Boolean =
        !closed &&
            current.inFlight &&
            callbackRequestId == current.requestId

    private fun nextRequestId(current: Long): Long =
        if (current == Long.MAX_VALUE) 1L else current + 1L
}

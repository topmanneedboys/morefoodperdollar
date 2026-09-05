package com.valuepilot.app

/** The user-triggered photo path that can be offered again after a terminal OCR failure. */
internal enum class CompareHerePhotoCaptureKind {
    CAMERA,
    IMPORT
}

/**
 * Presentation-only retry policy for the bounded on-device OCR handoff.
 *
 * A retry is offered only for a known user-triggered photo action and a terminal failure/no-match
 * outcome. It never interprets OCR text, changes a draft, or makes a comparison decision.
 */
internal object CompareHerePhotoRetryPolicy {
    fun shouldOfferRetry(
        captureKind: CompareHerePhotoCaptureKind?,
        recognizedCount: Int,
        error: Throwable?
    ): Boolean =
        captureKind != null &&
            (error != null || recognizedCount <= 0)
}

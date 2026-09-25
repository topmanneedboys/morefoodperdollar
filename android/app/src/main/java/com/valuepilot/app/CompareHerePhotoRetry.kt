package com.valuepilot.app

/** The user-triggered photo path that can be offered again after a recoverable terminal outcome. */
internal enum class CompareHerePhotoCaptureKind {
    CAMERA,
    IMPORT
}

/**
 * Terminal states for a user-triggered photo attempt. The reason is deliberately coarse: it is
 * only a presentation/recovery signal and never carries OCR, price or product meaning.
 */
internal enum class CompareHerePhotoRetryOutcome {
    NO_USABLE_SUGGESTION,
    OCR_FAILURE,
    CAPTURE_FAILURE,
    CANCELLED,
    PERMISSION_DENIED,
    UNAVAILABLE
}

/**
 * Presentation-only retry policy for the bounded on-device OCR handoff.
 *
 * A retry is offered only for a known user-triggered photo action and a recoverable failure or
 * no-match outcome. Permission denial, cancellation and unavailable hardware remain explicit
 * terminal states instead of prompting the same impossible action again. This policy never
 * interprets OCR text, changes a draft, or makes a comparison decision.
 */
internal object CompareHerePhotoRetryPolicy {
    fun shouldOfferRetry(
        captureKind: CompareHerePhotoCaptureKind?,
        outcome: CompareHerePhotoRetryOutcome?
    ): Boolean =
        captureKind != null &&
            when (outcome) {
                CompareHerePhotoRetryOutcome.NO_USABLE_SUGGESTION,
                CompareHerePhotoRetryOutcome.OCR_FAILURE,
                CompareHerePhotoRetryOutcome.CAPTURE_FAILURE -> true
                null,
                CompareHerePhotoRetryOutcome.CANCELLED,
                CompareHerePhotoRetryOutcome.PERMISSION_DENIED,
                CompareHerePhotoRetryOutcome.UNAVAILABLE -> false
            }
}

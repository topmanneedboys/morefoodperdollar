package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHerePhotoRetryTest {
    @Test
    fun `recoverable OCR and capture outcomes offer retry for the same user chosen path`() {
        assertTrue(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.CAMERA,
                outcome = CompareHerePhotoRetryOutcome.NO_USABLE_SUGGESTION
            )
        )
        assertTrue(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.IMPORT,
                outcome = CompareHerePhotoRetryOutcome.OCR_FAILURE
            )
        )
        assertTrue(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.CAMERA,
                outcome = CompareHerePhotoRetryOutcome.CAPTURE_FAILURE
            )
        )
    }

    @Test
    fun `permission denial cancellation and unavailable camera never offer retry`() {
        listOf(
            CompareHerePhotoRetryOutcome.PERMISSION_DENIED,
            CompareHerePhotoRetryOutcome.CANCELLED,
            CompareHerePhotoRetryOutcome.UNAVAILABLE
        ).forEach { outcome ->
            assertFalse(
                CompareHerePhotoRetryPolicy.shouldOfferRetry(
                    captureKind = CompareHerePhotoCaptureKind.CAMERA,
                    outcome = outcome
                )
            )
        }
    }

    @Test
    fun `successful suggestions and no known action never offer a retry`() {
        assertFalse(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.CAMERA,
                outcome = null
            )
        )
        assertFalse(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = null,
                outcome = CompareHerePhotoRetryOutcome.OCR_FAILURE
            )
        )
    }

    @Test
    fun `retry decision is stable for identical terminal inputs`() {
        val first =
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.IMPORT,
                outcome = CompareHerePhotoRetryOutcome.NO_USABLE_SUGGESTION
            )
        val second =
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.IMPORT,
                outcome = CompareHerePhotoRetryOutcome.NO_USABLE_SUGGESTION
            )

        assertTrue(first)
        assertTrue(first == second)
    }
}

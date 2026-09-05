package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHerePhotoRetryTest {
    @Test
    fun `failed or empty OCR result offers retry for the same user chosen path`() {
        assertTrue(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.CAMERA,
                recognizedCount = 0,
                error = null
            )
        )
        assertTrue(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.IMPORT,
                recognizedCount = 2,
                error = IllegalStateException("ocr")
            )
        )
    }

    @Test
    fun `successful suggestions and no known action never offer a retry`() {
        assertFalse(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.CAMERA,
                recognizedCount = 1,
                error = null
            )
        )
        assertFalse(
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = null,
                recognizedCount = 0,
                error = IllegalStateException("stale")
            )
        )
    }

    @Test
    fun `retry decision is stable for identical terminal inputs`() {
        val first =
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.IMPORT,
                recognizedCount = 0,
                error = null
            )
        val second =
            CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = CompareHerePhotoCaptureKind.IMPORT,
                recognizedCount = 0,
                error = null
            )

        assertTrue(first)
        assertTrue(first == second)
    }
}

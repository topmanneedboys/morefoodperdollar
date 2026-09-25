package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaBackendTransportMappingTest {

    private val release =
        ArgentinaBackendContract.ExpectedRelease(
            releaseId = "sepa-2026-09-06",
            releaseDate = "2026-09-06"
        )

    @Test
    fun transportExceptionRemainsAStableTypedBridgeFailure() {
        val bridge =
            ArgentinaBackendConsumerBridge(
                ArgentinaBackendTransport { _, _ ->
                    throw ArgentinaBackendTransportException(
                        ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                        "App Check token is unavailable"
                    )
                },
                release
            )

        val result = bridge.status()

        assertTrue(result is ArgentinaBackendBridgeResult.Failure)
        assertEquals(
            ArgentinaBackendContract.FailureCode.APP_CHECK_UNAVAILABLE,
            (result as ArgentinaBackendBridgeResult.Failure).code
        )
    }

    @Test
    fun protectedHttpStatusesRemainTypedInsteadOfBecomingGenericHttpError() {
        val expected =
            mapOf(
                400 to ArgentinaBackendContract.FailureCode.HTTP_BAD_REQUEST,
                401 to ArgentinaBackendContract.FailureCode.UNAUTHORIZED,
                403 to ArgentinaBackendContract.FailureCode.FORBIDDEN,
                404 to ArgentinaBackendContract.FailureCode.NOT_FOUND,
                408 to ArgentinaBackendContract.FailureCode.TIMEOUT,
                413 to ArgentinaBackendContract.FailureCode.RESPONSE_TOO_LARGE,
                429 to ArgentinaBackendContract.FailureCode.RATE_LIMITED,
                500 to ArgentinaBackendContract.FailureCode.SERVER_ERROR,
                502 to ArgentinaBackendContract.FailureCode.SERVER_ERROR,
                503 to ArgentinaBackendContract.FailureCode.SERVICE_UNAVAILABLE
            )

        expected.forEach { (statusCode, failureCode) ->
            val bridge =
                ArgentinaBackendConsumerBridge(
                    InMemoryArgentinaBackendTransport(
                        mapOf(
                            ArgentinaBackendConsumerBridge.STATUS_ENDPOINT to
                                ArgentinaBackendTransportResponse(statusCode, "")
                        )
                    ),
                    release
                )
            val result = bridge.status()
            assertTrue("status $statusCode", result is ArgentinaBackendBridgeResult.Failure)
            assertEquals(failureCode, (result as ArgentinaBackendBridgeResult.Failure).code)
        }
    }
}

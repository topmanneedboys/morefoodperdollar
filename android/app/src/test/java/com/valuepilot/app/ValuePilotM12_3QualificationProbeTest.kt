package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ValuePilotM12_3QualificationProbeTest {
    @Test
    fun `probe uses only status route without a request body`() {
        var observedEndpoint: String? = null
        var observedBody: String? = "not-called"
        val transport =
            ArgentinaBackendTransport { endpoint, requestBody ->
                observedEndpoint = endpoint
                observedBody = requestBody
                ArgentinaBackendTransportResponse(
                    statusCode = 200,
                    body = "response-body-must-not-be-logged"
                )
            }

        val line =
            ValuePilotM12_3QualificationProbe.qualificationLogLine {
                transport
            }

        assertEquals(ArgentinaBackendConsumerBridge.STATUS_ENDPOINT, observedEndpoint)
        assertNull(observedBody)
        assertEquals("M12_3_STATUS=200", line)
        assertFalse(line.contains("response-body-must-not-be-logged"))
    }

    @Test
    fun `typed transport failure logs only its bounded code`() {
        val transport =
            ArgentinaBackendTransport { _, _ ->
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                    "sensitive-detail-must-not-be-logged"
                )
            }

        val line =
            ValuePilotM12_3QualificationProbe.qualificationLogLine {
                transport
            }

        assertEquals("M12_3_FAILURE=APP_CHECK_UNAVAILABLE", line)
        assertFalse(line.contains("sensitive-detail-must-not-be-logged"))
    }

    @Test
    fun `unexpected failure is reduced to a fixed safe code`() {
        val line =
            ValuePilotM12_3QualificationProbe.qualificationLogLine {
                throw IllegalStateException("token-or-server-detail-must-not-be-logged")
            }

        assertEquals("M12_3_FAILURE=UNEXPECTED", line)
        assertFalse(line.contains("token-or-server-detail-must-not-be-logged"))
    }

    @Test
    fun `out of range status is reduced to a fixed safe code`() {
        val line =
            ValuePilotM12_3QualificationProbe.qualificationLogLine {
                ArgentinaBackendTransport { _, _ ->
                    ArgentinaBackendTransportResponse(
                        statusCode = Int.MAX_VALUE,
                        body = "response-body-must-not-be-logged"
                    )
                }
            }

        assertEquals("M12_3_FAILURE=UNEXPECTED", line)
        assertFalse(line.contains("2147483647"))
        assertFalse(line.contains("response-body-must-not-be-logged"))
    }
}

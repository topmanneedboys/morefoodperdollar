package com.valuepilot.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaConsumerEdgeTransportTest {

    @Test
    fun postSendsAppCheckOnlyInHeaderAndUsesBoundedHttpsConfiguration() {
        val connection = FakeConnection(responseCodeValue = 200, body = "{}".toByteArray())
        val token = "debug-token-for-test"
        val transport = transport(connection) { token }

        val response = transport.execute("/v1/search", "{\"query\":\"yerba\"}")

        assertEquals(200, response.statusCode)
        assertEquals("{}", response.body)
        assertEquals("POST", connection.requestMethod)
        assertEquals(token, connection.requestProperties["X-Firebase-AppCheck"])
        assertEquals("application/json; charset=utf-8", connection.requestProperties["Content-Type"])
        assertEquals("{\"query\":\"yerba\"}", connection.requestBody.toString(StandardCharsets.UTF_8.name()))
        assertTrue(connection.connectTimeoutMillis in 1..1_000)
        assertTrue(connection.readTimeoutMillis in 1..1_000)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertNotNull(connection.openedUrl)
        assertFalse(connection.openedUrl!!.toString().contains(token))
    }

    @Test
    fun getStatusAlsoCarriesAppCheckAndDoesNotCreateARequestBody() {
        val connection = FakeConnection(responseCodeValue = 200, body = "status".toByteArray())
        val transport = transport(connection) { "token" }

        val response = transport.execute("/v1/status", null)

        assertEquals(200, response.statusCode)
        assertEquals("GET", connection.requestMethod)
        assertEquals("token", connection.requestProperties["X-Firebase-AppCheck"])
        assertFalse(connection.doOutput)
        assertEquals(0, connection.requestBody.size())
    }

    @Test
    fun tokenFailureIsTypedAndPreventsOpeningTheConnection() {
        val connection = FakeConnection(responseCodeValue = 200, body = "ok".toByteArray())
        val transport = transport(connection) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                "App Check token is unavailable"
            )
        }

        val failure = runCatching { transport.execute("/v1/status", null) }.exceptionOrNull()

        assertTrue(failure is ArgentinaBackendTransportException)
        assertEquals(
            ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
            (failure as ArgentinaBackendTransportException).error
        )
        assertNull(connection.openedUrl)
    }

    @Test
    fun factoryWithoutApplicationInitializationFailsClosedBeforeOpeningNetwork() {
        assertEquals(ValuePilotFirebaseState.NOT_INITIALIZED, ValuePilotFirebaseRuntime.state)
        val transport = ValuePilotArgentinaEdgeTransportFactory.create()
        val failure = runCatching { transport.execute("/v1/status", null) }.exceptionOrNull()

        assertEquals(ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE, (failure as ArgentinaBackendTransportException).error)
    }

    @Test
    fun requestAndResponseLimitsFailClosedBeforeUnboundedAllocation() {
        val connection = FakeConnection(responseCodeValue = 200, body = "ok".toByteArray())
        val limited = limitedTransport(connection, maxRequestBytes = 4, maxResponseBytes = 4)

        val requestFailure = runCatching { limited.execute("/v1/status", "12345") }.exceptionOrNull()
        assertEquals(ArgentinaBackendTransportError.REQUEST_TOO_LARGE, (requestFailure as ArgentinaBackendTransportException).error)
        assertNull(connection.openedUrl)

        val largeResponseConnection = FakeConnection(responseCodeValue = 200, body = "12345".toByteArray())
        val responseFailure =
            runCatching {
                limitedTransport(largeResponseConnection, maxRequestBytes = 16 * 1024, maxResponseBytes = 4)
                    .execute("/v1/status", null)
            }.exceptionOrNull()
        assertEquals(ArgentinaBackendTransportError.RESPONSE_TOO_LARGE, (responseFailure as ArgentinaBackendTransportException).error)
    }

    @Test
    fun nonSuccessResponsesAreReturnedWithoutForwardingUntrustedErrorBodies() {
        val connection =
            FakeConnection(
                responseCodeValue = 401,
                body = "ignored success body".toByteArray(),
                errorBody = "secret server details".toByteArray()
            )
        val response = transport(connection) { "token" }.execute("/v1/status", null)

        assertEquals(401, response.statusCode)
        assertEquals("", response.body)
    }

    @Test
    fun cancellationIsCheckedBeforeTokenAndBeforeConnection() {
        val connection = FakeConnection(responseCodeValue = 200, body = "ok".toByteArray())
        val cancellation = ArgentinaRequestCancellation()
        cancellation.cancel()
        var tokenCalls = 0
        val transport = transport(connection) {
            tokenCalls += 1
            "token"
        }

        val failure = runCatching { transport.execute("/v1/status", null, cancellation) }.exceptionOrNull()

        assertEquals(ArgentinaBackendTransportError.CANCELLED, (failure as ArgentinaBackendTransportException).error)
        assertEquals(0, tokenCalls)
        assertNull(connection.openedUrl)
    }

    @Test
    fun cancellationDuringReadStopsTheBoundedStream() {
        val cancellation = ArgentinaRequestCancellation()
        val input = object : InputStream() {
            private var readCount = 0

            override fun read(): Int = -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (readCount++ == 0) {
                    cancellation.cancel()
                    buffer[offset] = 'x'.code.toByte()
                    return 1
                }
                return -1
            }
        }
        val connection = FakeConnection(responseCodeValue = 200, body = "ok".toByteArray(), inputOverride = input)
        val transport = transport(connection) { "token" }

        val failure = runCatching { transport.execute("/v1/status", null, cancellation) }.exceptionOrNull()

        assertEquals(ArgentinaBackendTransportError.CANCELLED, (failure as ArgentinaBackendTransportException).error)
        assertTrue(connection.disconnected)
    }

    @Test
    fun timeoutAndTlsFailuresRemainTyped() {
        val timeoutConnection = FakeConnection(responseCodeValue = 200, body = "ok".toByteArray(), connectFailure = SocketTimeoutException())
        val timeoutFailure = runCatching { transport(timeoutConnection) { "token" }.execute("/v1/status", null) }.exceptionOrNull()
        assertEquals(ArgentinaBackendTransportError.TIMEOUT, (timeoutFailure as ArgentinaBackendTransportException).error)

        val tlsConnection = FakeConnection(responseCodeValue = 200, body = "ok".toByteArray(), connectFailure = javax.net.ssl.SSLHandshakeException("bad cert"))
        val tlsFailure = runCatching { transport(tlsConnection) { "token" }.execute("/v1/status", null) }.exceptionOrNull()
        assertEquals(ArgentinaBackendTransportError.TLS_FAILURE, (tlsFailure as ArgentinaBackendTransportException).error)
    }

    @Test
    fun onlyAllowlistedHttpsHostAndRoutesCanBeResolved() {
        val valid = ArgentinaConsumerEdgeClientConfig()
        assertEquals("https://valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app/v1/status", valid.resolve("/v1/status").toString())

        val routeFailure = runCatching { valid.resolve("/v1/admin") }.exceptionOrNull()
        assertEquals(ArgentinaBackendTransportError.ROUTE_REJECTED, (routeFailure as ArgentinaBackendTransportException).error)

        val hostFailure =
            runCatching {
                ArgentinaConsumerEdgeClientConfig(baseUrl = "https://private.test/")
            }.exceptionOrNull()
        assertTrue(hostFailure is IllegalArgumentException)

        val embeddedCredentialFailure =
            runCatching {
                ArgentinaConsumerEdgeClientConfig(
                    baseUrl = "https://user:password@valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app/"
                )
            }.exceptionOrNull()
        assertTrue(embeddedCredentialFailure is ArgentinaBackendTransportException)
        assertEquals(
            ArgentinaBackendTransportError.CONFIGURATION_INVALID,
            (embeddedCredentialFailure as ArgentinaBackendTransportException).error
        )
    }

    @Test
    fun malformedUtf8IsNotAcceptedAsAResponse() {
        val connection = FakeConnection(responseCodeValue = 200, body = byteArrayOf(0xc3.toByte(), 0x28))
        val failure = runCatching { transport(connection) { "token" }.execute("/v1/status", null) }.exceptionOrNull()
        assertEquals(ArgentinaBackendTransportError.NETWORK_ERROR, (failure as ArgentinaBackendTransportException).error)
    }

    private fun transport(
        connection: FakeConnection,
        tokenProvider: ArgentinaAppCheckTokenProvider
    ): ArgentinaConsumerEdgeTransport =
        ArgentinaConsumerEdgeTransport(
            tokenProvider = tokenProvider,
            config = ArgentinaConsumerEdgeClientConfig(
                baseUrl = "https://valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app/",
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000,
                totalTimeoutMillis = 1_000,
                maxRequestBytes = 16 * 1024,
                maxResponseBytes = 512 * 1024
            ),
            connectionFactory = ArgentinaUrlConnectionFactory { url ->
                connection.openedUrl = url
                connection
            },
            nanoTime = { 0L }
        )

    private fun limitedTransport(
        connection: FakeConnection,
        maxRequestBytes: Int,
        maxResponseBytes: Int
    ): ArgentinaConsumerEdgeTransport =
        ArgentinaConsumerEdgeTransport(
            tokenProvider = ArgentinaAppCheckTokenProvider { "token" },
            config = ArgentinaConsumerEdgeClientConfig(
                baseUrl = "https://valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app/",
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000,
                totalTimeoutMillis = 1_000,
                maxRequestBytes = maxRequestBytes,
                maxResponseBytes = maxResponseBytes
            ),
            connectionFactory = ArgentinaUrlConnectionFactory { url ->
                connection.openedUrl = url
                connection
            },
            nanoTime = { 0L }
        )

    private class FakeConnection(
        private val responseCodeValue: Int,
        private val body: ByteArray,
        private val errorBody: ByteArray = ByteArray(0),
        private val inputOverride: InputStream? = null,
        private val connectFailure: IOException? = null
    ) : ArgentinaUrlConnection {
        override var connectTimeoutMillis: Int = 0
        override var readTimeoutMillis: Int = 0
        override var requestMethod: String = ""
        override var doOutput: Boolean = false
        override var instanceFollowRedirects: Boolean = true
        override var useCaches: Boolean = true
        override val contentLengthLong: Long
            get() = body.size.toLong()
        override val responseCode: Int
            get() = responseCodeValue
        val requestProperties = linkedMapOf<String, String>()
        val requestBody = ByteArrayOutputStream()
        var openedUrl: URL? = null
        var disconnected = false

        override fun setRequestProperty(name: String, value: String) {
            requestProperties[name] = value
        }

        override fun connect() {
            connectFailure?.let { throw it }
        }

        override fun outputStream(): OutputStream = requestBody

        override fun inputStream(): InputStream = inputOverride ?: ByteArrayInputStream(body)

        override fun errorStream(): InputStream = ByteArrayInputStream(errorBody)

        override fun disconnect() {
            disconnected = true
        }
    }
}

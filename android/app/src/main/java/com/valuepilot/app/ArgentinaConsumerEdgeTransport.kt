package com.valuepilot.app

import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

private const val MAX_TRANSPORT_TOKEN_CHARS = 16 * 1024

/** A token source that uses Firebase's normal cached-token/refresh behavior. */
internal fun interface ArgentinaAppCheckTokenProvider {
    fun getToken(): String
}

internal class FirebaseAppCheckTokenProvider(
    private val firebaseAppCheck: FirebaseAppCheck,
    private val tokenTimeoutMillis: Long = DEFAULT_TOKEN_TIMEOUT_MILLIS
) : ArgentinaAppCheckTokenProvider {
    init {
        require(tokenTimeoutMillis in 1..MAX_TOKEN_TIMEOUT_MILLIS)
    }

    override fun getToken(): String {
        val token =
            try {
                // false deliberately permits the SDK to use its cache and refresh only when needed.
                Tasks.await(
                    firebaseAppCheck.getAppCheckToken(false),
                    tokenTimeoutMillis,
                    TimeUnit.MILLISECONDS
                ).token
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.CANCELLED,
                    "App Check token acquisition was interrupted"
                )
            } catch (_: TimeoutException) {
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.TIMEOUT,
                    "App Check token acquisition timed out"
                )
            } catch (_: CancellationException) {
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.CANCELLED,
                    "App Check token acquisition was cancelled"
                )
            } catch (_: ExecutionException) {
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                    "App Check token is unavailable"
                )
            } catch (_: Exception) {
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                    "App Check token is unavailable"
                )
            }
        if (token.isBlank() || token.length > MAX_TOKEN_CHARS) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                "App Check token is unavailable"
            )
        }
        return token
    }

    private companion object {
        const val DEFAULT_TOKEN_TIMEOUT_MILLIS = 5_000L
        const val MAX_TOKEN_TIMEOUT_MILLIS = 15_000L
        const val MAX_TOKEN_CHARS = 16 * 1024
    }
}

/** Cancellable request handle shared by the transport and its caller. */
internal class ArgentinaRequestCancellation {
    private val cancelled = AtomicBoolean(false)
    private val cancellationAction = AtomicReference<(() -> Unit)?>(null)

    fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancellationAction.getAndSet(null)?.invoke()
        }
    }

    internal fun isCancelled(): Boolean = cancelled.get()

    internal fun registerCancellationAction(action: () -> Unit) {
        if (cancelled.get()) {
            action()
            return
        }
        cancellationAction.set(action)
        if (cancelled.get() && cancellationAction.compareAndSet(action, null)) {
            action()
        }
    }

    internal fun clearCancellationAction() {
        cancellationAction.set(null)
    }
}

internal enum class ArgentinaBackendTransportError {
    APP_CHECK_UNAVAILABLE,
    CONFIGURATION_INVALID,
    ROUTE_REJECTED,
    REQUEST_TOO_LARGE,
    RESPONSE_TOO_LARGE,
    NO_CONNECTIVITY,
    TLS_FAILURE,
    TIMEOUT,
    CANCELLED,
    NETWORK_ERROR
}

internal class ArgentinaBackendTransportException(
    val error: ArgentinaBackendTransportError,
    override val message: String
) : IOException(message) {
    val failureCode: ArgentinaBackendContract.FailureCode
        get() =
            when (error) {
                ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE ->
                    ArgentinaBackendContract.FailureCode.APP_CHECK_UNAVAILABLE
                ArgentinaBackendTransportError.CONFIGURATION_INVALID ->
                    ArgentinaBackendContract.FailureCode.CONFIGURATION_INVALID
                ArgentinaBackendTransportError.ROUTE_REJECTED ->
                    ArgentinaBackendContract.FailureCode.ROUTE_REJECTED
                ArgentinaBackendTransportError.REQUEST_TOO_LARGE ->
                    ArgentinaBackendContract.FailureCode.REQUEST_TOO_LARGE
                ArgentinaBackendTransportError.RESPONSE_TOO_LARGE ->
                    ArgentinaBackendContract.FailureCode.RESPONSE_TOO_LARGE
                ArgentinaBackendTransportError.NO_CONNECTIVITY ->
                    ArgentinaBackendContract.FailureCode.NO_CONNECTIVITY
                ArgentinaBackendTransportError.TLS_FAILURE ->
                    ArgentinaBackendContract.FailureCode.TLS_FAILURE
                ArgentinaBackendTransportError.TIMEOUT ->
                    ArgentinaBackendContract.FailureCode.TIMEOUT
                ArgentinaBackendTransportError.CANCELLED ->
                    ArgentinaBackendContract.FailureCode.CANCELLED
                ArgentinaBackendTransportError.NETWORK_ERROR ->
                    ArgentinaBackendContract.FailureCode.NETWORK_ERROR
            }

    val safeMessage: String
        get() =
            when (error) {
                ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE -> "App Check token is unavailable"
                ArgentinaBackendTransportError.CONFIGURATION_INVALID -> "edge configuration is invalid"
                ArgentinaBackendTransportError.ROUTE_REJECTED -> "edge endpoint is not allowlisted"
                ArgentinaBackendTransportError.REQUEST_TOO_LARGE -> "backend request exceeds the bounded byte limit"
                ArgentinaBackendTransportError.RESPONSE_TOO_LARGE -> "backend response exceeds the bounded byte limit"
                ArgentinaBackendTransportError.NO_CONNECTIVITY -> "edge connection is unavailable"
                ArgentinaBackendTransportError.TLS_FAILURE -> "edge TLS validation failed"
                ArgentinaBackendTransportError.TIMEOUT -> "edge request timed out"
                ArgentinaBackendTransportError.CANCELLED -> "edge request was cancelled"
                ArgentinaBackendTransportError.NETWORK_ERROR -> "edge request failed"
            }
}

/**
 * Bounds and validates the one public edge route used by the future consumer
 * surface. The private M11 origin is intentionally not representable here.
 */
internal data class ArgentinaConsumerEdgeClientConfig(
    val baseUrl: String = DEFAULT_EDGE_BASE_URL,
    val allowedHost: String = DEFAULT_EDGE_HOST,
    val connectTimeoutMillis: Int = 5_000,
    // The M12.3 cold-start probe took 8.58 s at the public edge. Keep a
    // finite hierarchy that allows that one startup while preserving a hard
    // mobile deadline and cancellation.
    val readTimeoutMillis: Int = 15_000,
    val totalTimeoutMillis: Int = 20_000,
    val maxRequestBytes: Int = MAX_REQUEST_BYTES,
    val maxResponseBytes: Int = ArgentinaBackendContract.MAX_RESPONSE_BYTES
) {
    init {
        require(allowedHost.isNotBlank())
        require(connectTimeoutMillis in 1..MAX_TIMEOUT_MILLIS)
        require(readTimeoutMillis in 1..MAX_TIMEOUT_MILLIS)
        require(totalTimeoutMillis in 1..MAX_TOTAL_TIMEOUT_MILLIS)
        require(maxRequestBytes in 1..MAX_REQUEST_BYTES)
        require(maxResponseBytes in 1..ArgentinaBackendContract.MAX_RESPONSE_BYTES)
        require(allowedHost.equals(DEFAULT_EDGE_HOST, ignoreCase = true))
        val uri = parseBaseUri(baseUrl)
        require(uri.host.equals(allowedHost, ignoreCase = true))
    }

    internal fun resolve(endpoint: String): URL {
        if (endpoint !in ALLOWED_ENDPOINTS) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.ROUTE_REJECTED,
                "edge endpoint is not allowlisted"
            )
        }
        val base = parseBaseUri(baseUrl)
        val resolved = base.resolve(endpoint.removePrefix("/"))
        if (!resolved.scheme.equals("https", ignoreCase = true) ||
            !resolved.host.equals(allowedHost, ignoreCase = true) ||
            resolved.userInfo != null ||
            (resolved.port != -1 && resolved.port != 443) ||
            resolved.query != null ||
            resolved.fragment != null
        ) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.CONFIGURATION_INVALID,
                "edge URL must remain HTTPS on the configured public host"
            )
        }
        return try {
            resolved.toURL()
        } catch (_: Exception) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.CONFIGURATION_INVALID,
                "edge URL is invalid"
            )
        }
    }

    private companion object {
        const val DEFAULT_EDGE_HOST = "valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app"
        const val DEFAULT_EDGE_BASE_URL = "https://$DEFAULT_EDGE_HOST/"
        const val MAX_REQUEST_BYTES = 16 * 1024
        const val MAX_TIMEOUT_MILLIS = 30_000
        const val MAX_TOTAL_TIMEOUT_MILLIS = 45_000
        const val MAX_TOKEN_CHARS = 16 * 1024

        val ALLOWED_ENDPOINTS = setOf(
            ArgentinaBackendConsumerBridge.STATUS_ENDPOINT,
            ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT,
            ArgentinaBackendConsumerBridge.INTERPRET_ENDPOINT,
            ArgentinaBackendConsumerBridge.SHOP_TEXT_ENDPOINT,
            ArgentinaBackendConsumerBridge.SHOP_ENDPOINT
        )

        fun parseBaseUri(value: String): URI {
            val uri =
                try {
                    URI(value)
                } catch (_: Exception) {
                    throw ArgentinaBackendTransportException(
                        ArgentinaBackendTransportError.CONFIGURATION_INVALID,
                        "edge URL is invalid"
                    )
                }
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                uri.host.isNullOrBlank() ||
                uri.userInfo != null ||
                (uri.port != -1 && uri.port != 443) ||
                (uri.path != "" && uri.path != "/") ||
                uri.query != null ||
                uri.fragment != null
            ) {
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.CONFIGURATION_INVALID,
                    "edge URL must be an HTTPS origin without query or fragment"
                )
            }
            return uri
        }
    }
}

/** Minimal connection seam keeps JVM tests deterministic and entirely offline. */
internal interface ArgentinaUrlConnection {
    var connectTimeoutMillis: Int
    var readTimeoutMillis: Int
    var requestMethod: String
    var doOutput: Boolean
    var instanceFollowRedirects: Boolean
    var useCaches: Boolean
    val contentLengthLong: Long
    val responseCode: Int

    fun setRequestProperty(name: String, value: String)
    fun connect()
    fun outputStream(): OutputStream
    fun inputStream(): InputStream
    fun errorStream(): InputStream?
    fun disconnect()
}

internal fun interface ArgentinaUrlConnectionFactory {
    fun open(url: URL): ArgentinaUrlConnection
}

private class PlatformArgentinaUrlConnection(
    private val delegate: HttpURLConnection
) : ArgentinaUrlConnection {
    override var connectTimeoutMillis: Int
        get() = delegate.connectTimeout
        set(value) { delegate.connectTimeout = value }
    override var readTimeoutMillis: Int
        get() = delegate.readTimeout
        set(value) { delegate.readTimeout = value }
    override var requestMethod: String
        get() = delegate.requestMethod
        set(value) { delegate.requestMethod = value }
    override var doOutput: Boolean
        get() = delegate.doOutput
        set(value) { delegate.doOutput = value }
    override var instanceFollowRedirects: Boolean
        get() = delegate.instanceFollowRedirects
        set(value) { delegate.instanceFollowRedirects = value }
    override var useCaches: Boolean
        get() = delegate.useCaches
        set(value) { delegate.useCaches = value }
    override val contentLengthLong: Long
        // getContentLengthLong is newer than the app's minSdk; the int API is
        // sufficient because responses are bounded below 512 KiB.
        get() = delegate.contentLength.toLong()
    override val responseCode: Int
        get() = delegate.responseCode

    override fun setRequestProperty(name: String, value: String) = delegate.setRequestProperty(name, value)
    override fun connect() = delegate.connect()
    override fun outputStream(): OutputStream = delegate.outputStream
    override fun inputStream(): InputStream = delegate.inputStream
    override fun errorStream(): InputStream? = delegate.errorStream
    override fun disconnect() = delegate.disconnect()
}

private val PLATFORM_CONNECTION_FACTORY = ArgentinaUrlConnectionFactory { url ->
    val connection =
        try {
            url.openConnection()
        } catch (_: Exception) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.NO_CONNECTIVITY,
                "edge connection could not be opened"
            )
        }
    if (connection !is HttpsURLConnection) {
        throw ArgentinaBackendTransportException(
            ArgentinaBackendTransportError.CONFIGURATION_INVALID,
            "edge connection is not HTTPS"
        )
    }
    PlatformArgentinaUrlConnection(connection)
}

/**
 * Synchronous, bounded HTTPS transport. Call it from a worker thread; the
 * bridge and fake transport remain unchanged for offline previews.
 */
internal class ArgentinaConsumerEdgeTransport(
    private val tokenProvider: ArgentinaAppCheckTokenProvider,
    private val config: ArgentinaConsumerEdgeClientConfig = ArgentinaConsumerEdgeClientConfig(),
    private val connectionFactory: ArgentinaUrlConnectionFactory = PLATFORM_CONNECTION_FACTORY,
    private val nanoTime: () -> Long = System::nanoTime
) : ArgentinaBackendTransport {
    override fun execute(
        endpoint: String,
        requestBody: String?
    ): ArgentinaBackendTransportResponse =
        execute(endpoint, requestBody, ArgentinaRequestCancellation())

    override fun execute(
        endpoint: String,
        requestBody: String?,
        cancellation: ArgentinaRequestCancellation
    ): ArgentinaBackendTransportResponse {
        val body = requestBody?.toByteArray(StandardCharsets.UTF_8)
        if (body != null && body.size > config.maxRequestBytes) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.REQUEST_TOO_LARGE,
                "backend request exceeds the bounded byte limit"
            )
        }
        ensureNotCancelled(cancellation)
        val url = config.resolve(endpoint)
        ensureNotCancelled(cancellation)
        val token = tokenProvider.getToken()
        if (token.isBlank() || token.length > MAX_TRANSPORT_TOKEN_CHARS || token.any { it == '\r' || it == '\n' }) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                "App Check token is unavailable"
            )
        }
        ensureNotCancelled(cancellation)

        val started = nanoTime()
        var connection: ArgentinaUrlConnection? = null
        try {
            val openedConnection = connectionFactory.open(url)
            connection = openedConnection
            cancellation.registerCancellationAction { openedConnection.disconnect() }
            val remainingAtConnect = remainingMillis(started)
            connection.connectTimeoutMillis = minTimeout(config.connectTimeoutMillis, remainingAtConnect)
            connection.readTimeoutMillis = minTimeout(config.readTimeoutMillis, remainingAtConnect)
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.doOutput = body != null
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Firebase-AppCheck", token)
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Content-Length", body.size.toString())
            }
            ensureNotCancelled(cancellation)
            connection.connect()
            ensureNotCancelled(cancellation)
            if (body != null) {
                connection.outputStream().use { output ->
                    writeBounded(output, body, cancellation)
                }
            }
            ensureNotTimedOut(started)
            val statusCode = connection.responseCode
            val contentLength = connection.contentLengthLong
            if (contentLength > config.maxResponseBytes) {
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.RESPONSE_TOO_LARGE,
                    "backend response exceeds the bounded byte limit"
                )
            }
            val bodyText =
                if (statusCode in 200..299) {
                    readBounded(connection.inputStream(), cancellation, started)
                } else {
                    // Error payloads are not part of the provider contract; keep them bounded and discard them.
                    connection.errorStream()?.use { readAndDiscardBounded(it, cancellation, started) }
                    ""
                }
            ensureNotCancelled(cancellation)
            return ArgentinaBackendTransportResponse(statusCode, bodyText)
        } catch (error: ArgentinaBackendTransportException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.TIMEOUT,
                "edge request timed out"
            )
        } catch (error: SSLException) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.TLS_FAILURE,
                "edge TLS validation failed"
            )
        } catch (error: UnknownHostException) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.NO_CONNECTIVITY,
                "edge host could not be resolved"
            )
        } catch (error: ConnectException) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.NO_CONNECTIVITY,
                "edge connection could not be established"
            )
        } catch (error: IOException) {
            if (cancellation.isCancelled()) {
                throw ArgentinaBackendTransportException(
                    ArgentinaBackendTransportError.CANCELLED,
                    "edge request was cancelled"
                )
            }
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.NETWORK_ERROR,
                "edge request failed"
            )
        } finally {
            cancellation.clearCancellationAction()
            connection?.disconnect()
        }
    }

    private fun ensureNotCancelled(cancellation: ArgentinaRequestCancellation) {
        if (cancellation.isCancelled()) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.CANCELLED,
                "edge request was cancelled"
            )
        }
    }

    private fun ensureNotTimedOut(started: Long) {
        if (remainingMillis(started) <= 0) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.TIMEOUT,
                "edge request timed out"
            )
        }
    }

    private fun remainingMillis(started: Long): Long {
        val elapsedNanos = (nanoTime() - started).coerceAtLeast(0L)
        val elapsedMillis = elapsedNanos / 1_000_000L
        return config.totalTimeoutMillis.toLong() - elapsedMillis
    }

    private fun minTimeout(configured: Int, remaining: Long): Int {
        if (remaining <= 0) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.TIMEOUT,
                "edge request timed out"
            )
        }
        return minOf(configured.toLong(), remaining).coerceAtLeast(1L).toInt()
    }

    private fun writeBounded(
        output: OutputStream,
        body: ByteArray,
        cancellation: ArgentinaRequestCancellation
    ) {
        ensureNotCancelled(cancellation)
        output.write(body)
        ensureNotCancelled(cancellation)
    }

    private fun readBounded(
        input: InputStream,
        cancellation: ArgentinaRequestCancellation,
        started: Long
    ): String {
        val output = ByteArrayOutputStream(minOf(config.maxResponseBytes, READ_BUFFER_BYTES))
        val buffer = ByteArray(READ_BUFFER_BYTES)
        input.use { stream ->
            while (true) {
                ensureNotCancelled(cancellation)
                ensureNotTimedOut(started)
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > config.maxResponseBytes) {
                    throw ArgentinaBackendTransportException(
                        ArgentinaBackendTransportError.RESPONSE_TOO_LARGE,
                        "backend response exceeds the bounded byte limit"
                    )
                }
                output.write(buffer, 0, count)
            }
        }
        ensureNotTimedOut(started)
        return decodeUtf8(output.toByteArray())
    }

    private fun readAndDiscardBounded(
        input: InputStream,
        cancellation: ArgentinaRequestCancellation,
        started: Long
    ) {
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var bytes = 0
        input.use { stream ->
            while (true) {
                ensureNotCancelled(cancellation)
                ensureNotTimedOut(started)
                val count = stream.read(buffer)
                if (count < 0) break
                bytes += count
                if (bytes > config.maxResponseBytes) {
                    throw ArgentinaBackendTransportException(
                        ArgentinaBackendTransportError.RESPONSE_TOO_LARGE,
                        "backend response exceeds the bounded byte limit"
                    )
                }
            }
        }
        ensureNotTimedOut(started)
    }

    private fun decodeUtf8(bytes: ByteArray): String =
        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw ArgentinaBackendTransportException(
                ArgentinaBackendTransportError.NETWORK_ERROR,
                "backend response encoding is invalid"
            )
        }

    private companion object {
        const val READ_BUFFER_BYTES = 8 * 1024
    }
}

/**
 * Creates the production transport only from the Application-installed
 * Firebase App Check instance. A missing initialization remains a typed
 * fail-closed result instead of silently creating an unauthenticated client.
 */
internal object ValuePilotArgentinaEdgeTransportFactory {
    fun create(
        config: ArgentinaConsumerEdgeClientConfig = ArgentinaConsumerEdgeClientConfig()
    ): ArgentinaConsumerEdgeTransport {
        val appCheck = ValuePilotFirebaseRuntime.appCheckOrNull()
        val provider =
            appCheck?.let(::FirebaseAppCheckTokenProvider)
                ?: ArgentinaAppCheckTokenProvider {
                    throw ArgentinaBackendTransportException(
                        ArgentinaBackendTransportError.APP_CHECK_UNAVAILABLE,
                        "App Check token is unavailable"
                    )
                }
        return ArgentinaConsumerEdgeTransport(provider, config)
    }
}

package com.valuepilot.app

import java.math.BigDecimal
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Explicit geography supplied by a future Argentina Search owner.
 *
 * There is intentionally no device-location provider or default coordinate.
 * M12.4A can exercise the coordinator with an explicit fixture, but the
 * visible product remains gated until M12.4B supplies an authoritative user
 * or launch-area context.
 */
internal enum class ArgentinaSearchContextSource {
    EXPLICIT_USER_CONTEXT,
    TEST_FIXTURE
}

internal data class ArgentinaSearchContext(
    val latitude: String,
    val longitude: String,
    val radiusKm: String,
    val source: ArgentinaSearchContextSource
) {
    init {
        require(source != ArgentinaSearchContextSource.EXPLICIT_USER_CONTEXT || latitude.isNotBlank())
        ArgentinaBackendContract.SearchRequest(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            query = "context"
        )
        val radius = radiusKm.toBigDecimalOrNull()
        require(radius != null && radius >= BigDecimal.ZERO && radius <= MAX_RADIUS_KM) {
            "radiusKm must be between 0 and 50 km"
        }
    }

    private companion object {
        val MAX_RADIUS_KM = BigDecimal("50")
    }
}

/** Renderer-ready identity-only state for the future live Search surface. */
internal sealed interface ArgentinaLiveSearchState {
    data object Idle : ArgentinaLiveSearchState

    data class Loading(
        val generation: Long,
        val query: String
    ) : ArgentinaLiveSearchState

    data class Results(
        val generation: Long,
        val query: String,
        val presentation: PracticalShoppingSearchIdentityPresentation
    ) : ArgentinaLiveSearchState

    data class Empty(
        val generation: Long,
        val query: String,
        val presentation: PracticalShoppingSearchIdentityPresentation
    ) : ArgentinaLiveSearchState

    data class Unavailable(
        val generation: Long,
        val query: String,
        val failure: ArgentinaBackendContract.FailureCode
    ) : ArgentinaLiveSearchState
}

/**
 * User-triggered application-edge coordinator for identity-only Argentina
 * Search. It owns lifecycle and release pinning, not parsing, ranking, or UI.
 */
internal class ArgentinaLiveSearchCoordinator(
    private val transport: ArgentinaBackendTransport,
    private val executor: Executor,
    private val stateSink: (ArgentinaLiveSearchState) -> Unit,
    private val closeExecutor: (() -> Unit)? = null
) : AutoCloseable {

    private val lock = Any()
    private var generation = 0L
    private var closed = false
    private var currentCancellation: ArgentinaRequestCancellation? = null
    private var pinnedRelease: ArgentinaBackendContract.ExpectedRelease? = null
    private var currentState: ArgentinaLiveSearchState = ArgentinaLiveSearchState.Idle

    fun state(): ArgentinaLiveSearchState = synchronized(lock) { currentState }

    /** Submit exactly one user-triggered query generation. */
    fun submit(
        rawQuery: String,
        context: ArgentinaSearchContext?
    ): Long {
        val query = rawQuery.trim()
        var task: (() -> Unit)? = null
        var scheduledGeneration = 0L
        var immediate: ArgentinaLiveSearchState
        synchronized(lock) {
            if (closed) return generation
            cancelCurrentLocked()
            generation = nextGenerationLocked()
            val currentGeneration = generation
            scheduledGeneration = currentGeneration
            immediate =
                when {
                    query.isBlank() -> ArgentinaLiveSearchState.Idle
                    query.length > ArgentinaBackendContract.MAX_QUERY_CHARS ->
                        ArgentinaLiveSearchState.Unavailable(
                            currentGeneration,
                            query.take(ArgentinaBackendContract.MAX_QUERY_CHARS),
                            ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
                        )
                    context == null ->
                        ArgentinaLiveSearchState.Unavailable(
                            currentGeneration,
                            query,
                            ArgentinaBackendContract.FailureCode.SEARCH_GEOGRAPHY_UNAVAILABLE
                        )
                    else -> {
                        val cancellation = ArgentinaRequestCancellation()
                        currentCancellation = cancellation
                        task = { performSearch(currentGeneration, query, context, cancellation) }
                        ArgentinaLiveSearchState.Loading(currentGeneration, query)
                    }
                }
            currentState = immediate
        }
        stateSink(immediate)
        try {
            task?.let { work -> executor.execute(Runnable { work() }) }
        } catch (_: RuntimeException) {
            publishFailure(
                generation = scheduledGeneration,
                query = query,
                failure = ArgentinaBackendContract.FailureCode.NETWORK_ERROR
            )
        }
        return generation
    }

    /** Query edits invalidate in-flight work but never start a request. */
    fun queryChanged(rawQuery: String): Long {
        val query = rawQuery.trim()
        val next: ArgentinaLiveSearchState
        synchronized(lock) {
            if (closed) return generation
            cancelCurrentLocked()
            generation = nextGenerationLocked()
            next =
                if (query.length > ArgentinaBackendContract.MAX_QUERY_CHARS) {
                    ArgentinaLiveSearchState.Unavailable(
                        generation,
                        query.take(ArgentinaBackendContract.MAX_QUERY_CHARS),
                        ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
                    )
                } else {
                    ArgentinaLiveSearchState.Idle
                }
            currentState = next
        }
        stateSink(next)
        return generation
    }

    /** Route/session changes cancel work and discard the pinned release. */
    fun invalidateForRouteChange(): Long {
        val next: ArgentinaLiveSearchState
        synchronized(lock) {
            if (closed) return generation
            cancelCurrentLocked()
            pinnedRelease = null
            generation = nextGenerationLocked()
            next = ArgentinaLiveSearchState.Idle
            currentState = next
        }
        stateSink(next)
        return generation
    }

    override fun close() {
        val shouldClose: Boolean
        synchronized(lock) {
            shouldClose = !closed
            if (shouldClose) {
                closed = true
                cancelCurrentLocked()
                pinnedRelease = null
                generation = nextGenerationLocked()
                currentState = ArgentinaLiveSearchState.Idle
            }
        }
        if (shouldClose) closeExecutor?.invoke()
    }

    private fun performSearch(
        requestGeneration: Long,
        query: String,
        context: ArgentinaSearchContext,
        cancellation: ArgentinaRequestCancellation
    ) {
        try {
            val release = pinRelease(requestGeneration, cancellation)
            if (release is ArgentinaBackendBridgeResult.Failure) {
                publishFailure(requestGeneration, query, release.code)
                return
            }
            val expectedRelease = (release as ArgentinaBackendBridgeResult.Success).value
            val request =
                ArgentinaBackendContract.SearchRequest(
                    latitude = context.latitude,
                    longitude = context.longitude,
                    radiusKm = context.radiusKm,
                    query = query
                )
            val result =
                ArgentinaBackendConsumerBridge(transport, expectedRelease)
                    .searchPresentation(request, cancellation)
            when (result) {
                is ArgentinaBackendBridgeResult.Failure -> {
                    if (result.code == ArgentinaBackendContract.FailureCode.RELEASE_MISMATCH) {
                        clearPinnedRelease(requestGeneration)
                    }
                    publishFailure(requestGeneration, query, result.code)
                }
                is ArgentinaBackendBridgeResult.Success -> {
                    if (result.value.response.common.freshness != ArgentinaBackendContract.Freshness.FRESH) {
                        publishFailure(
                            requestGeneration,
                            query,
                            ArgentinaBackendContract.FailureCode.STALE_RELEASE
                        )
                    } else {
                        val presentation = result.value.identity
                        publish(
                            if (presentation.matches.isEmpty()) {
                                ArgentinaLiveSearchState.Empty(requestGeneration, query, presentation)
                            } else {
                                ArgentinaLiveSearchState.Results(requestGeneration, query, presentation)
                            }
                        )
                    }
                }
            }
        } catch (_: IllegalArgumentException) {
            publishFailure(
                requestGeneration,
                query,
                ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
            )
        } finally {
            synchronized(lock) {
                if (generation == requestGeneration && currentCancellation === cancellation) {
                    currentCancellation = null
                }
            }
        }
    }

    private fun pinRelease(
        requestGeneration: Long,
        cancellation: ArgentinaRequestCancellation
    ): ArgentinaBackendBridgeResult<ArgentinaBackendContract.ExpectedRelease> {
        synchronized(lock) {
            pinnedRelease?.let { return ArgentinaBackendBridgeResult.Success(it) }
            if (closed || generation != requestGeneration) {
                return ArgentinaBackendBridgeResult.Failure(
                    ArgentinaBackendContract.FailureCode.CANCELLED,
                    "search was superseded"
                )
            }
        }

        val status = ArgentinaBackendConsumerBridge(transport, null).status(cancellation)
        if (status is ArgentinaBackendBridgeResult.Failure) return status
        val value = (status as ArgentinaBackendBridgeResult.Success).value
        val releaseId = value.releaseId
        val releaseDate = value.releaseDate
        if (releaseId == null || releaseDate == null) {
            return ArgentinaBackendBridgeResult.Failure(
                ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
                "status release metadata is incomplete"
            )
        }
        if (value.freshness != ArgentinaBackendContract.Freshness.FRESH ||
            !value.backendCodeQualified
        ) {
            return ArgentinaBackendBridgeResult.Failure(
                if (value.freshness == ArgentinaBackendContract.Freshness.FRESH) {
                    ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
                } else {
                    ArgentinaBackendContract.FailureCode.STALE_RELEASE
                },
                "status release is not eligible for live Search"
            )
        }
        if (!value.androidNetworkingAuthorized) {
            return ArgentinaBackendBridgeResult.Failure(
                ArgentinaBackendContract.FailureCode.SEARCH_NOT_AUTHORIZED,
                "status has not authorized Android live Search"
            )
        }
        val expected =
            try {
                ArgentinaBackendContract.ExpectedRelease(releaseId, releaseDate)
            } catch (_: IllegalArgumentException) {
                return ArgentinaBackendBridgeResult.Failure(
                    ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
                    "status release metadata is invalid"
                )
            }
        synchronized(lock) {
            if (!closed && generation == requestGeneration) pinnedRelease = expected
        }
        return ArgentinaBackendBridgeResult.Success(expected)
    }

    private fun clearPinnedRelease(requestGeneration: Long) {
        synchronized(lock) {
            if (generation == requestGeneration) pinnedRelease = null
        }
    }

    private fun publishFailure(
        generation: Long,
        query: String,
        failure: ArgentinaBackendContract.FailureCode
    ) {
        publish(ArgentinaLiveSearchState.Unavailable(generation, query, failure))
    }

    private fun publish(next: ArgentinaLiveSearchState) {
        val accepted: Boolean
        synchronized(lock) {
            val nextGeneration = generationOf(next)
            accepted = !closed && nextGeneration == generation
            if (accepted) currentState = next
        }
        if (accepted) stateSink(next)
    }

    private fun generationOf(state: ArgentinaLiveSearchState): Long =
        when (state) {
            ArgentinaLiveSearchState.Idle -> generation
            is ArgentinaLiveSearchState.Loading -> state.generation
            is ArgentinaLiveSearchState.Results -> state.generation
            is ArgentinaLiveSearchState.Empty -> state.generation
            is ArgentinaLiveSearchState.Unavailable -> state.generation
        }

    private fun cancelCurrentLocked() {
        currentCancellation?.cancel()
        currentCancellation = null
    }

    private fun nextGenerationLocked(): Long {
        check(generation < Long.MAX_VALUE) { "search generation exhausted" }
        return generation + 1L
    }

    companion object {
        fun create(
            transport: ArgentinaBackendTransport,
            stateSink: (ArgentinaLiveSearchState) -> Unit
        ): ArgentinaLiveSearchCoordinator {
            val executor = ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                // A cancelled request can still be unwinding its socket. Keep
                // only one latest pending generation; never grow a request storm.
                ArrayBlockingQueue(1),
                { runnable -> Thread(runnable, "ValuePilot-Argentina-search").apply { isDaemon = true } },
                ThreadPoolExecutor.DiscardOldestPolicy()
            )
            return ArgentinaLiveSearchCoordinator(
                transport = transport,
                executor = executor,
                stateSink = stateSink,
                closeExecutor = executor::shutdownNow
            )
        }
    }
}

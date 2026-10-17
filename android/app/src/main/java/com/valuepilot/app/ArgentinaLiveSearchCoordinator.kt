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
    QUALIFIED_LAUNCH_AREA,
    TEST_FIXTURE
}

internal data class ArgentinaSearchContext(
    val latitude: String,
    val longitude: String,
    val radiusKm: String,
    val source: ArgentinaSearchContextSource
) {
    init {
        require(latitude.isNotBlank())
        require(longitude.isNotBlank())
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

/**
 * Bounded, non-authoritative success evidence for the physical live-search
 * trace.  This intentionally excludes the query, response body, product
 * names, tokens, URLs, and headers; it exists only to make page/region/result
 * verification auditable without changing the Search authority boundary.
 */
internal data class ArgentinaLiveSearchSuccessMetadata(
    val page: Int,
    val pageResultCount: Int,
    val hasMore: Boolean,
    val limitReached: Boolean,
    val regionsQueried: List<String>,
    val requestId: String?,
    val releaseId: String?,
    val releaseDate: String?
) {
    init {
        require(page in 0..ArgentinaBackendContract.MAX_SEARCH_PAGE)
        require(pageResultCount in 0..MAX_RESULT_COUNT)
        require(regionsQueried.size <= MAX_REGIONS)
        require(regionsQueried.all { REGION_ID_PATTERN.matches(it) })
    }

    private companion object {
        const val MAX_REGIONS = 32
        const val MAX_RESULT_COUNT =
            ArgentinaBackendContract.MAX_MATCHES * ArgentinaBackendContract.MAX_PRODUCT_CANDIDATES
        val REGION_ID_PATTERN = Regex("ar-[a-z0-9-]{1,31}")
    }
}

/** Renderer-ready bounded evidence state for the live Search surface. */
internal sealed interface ArgentinaLiveSearchState {
    data class Idle(val generation: Long) : ArgentinaLiveSearchState

    data class Loading(
        val generation: Long,
        val query: String,
        val previousPresentation: PracticalShoppingSearchIdentityPresentation? = null
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
        val failure: ArgentinaBackendContract.FailureCode,
        val metadata: ArgentinaBackendFailureMetadata? = null,
        val previousPresentation: PracticalShoppingSearchIdentityPresentation? = null
    ) : ArgentinaLiveSearchState
}

/**
 * User-triggered application-edge coordinator for Argentina Search. It owns
 * lifecycle and release pinning, not parsing, ranking, or UI.
 */
internal class ArgentinaLiveSearchCoordinator(
    private val transport: ArgentinaBackendTransport,
    private val executor: Executor,
    private val stateSink: (ArgentinaLiveSearchState) -> Unit,
    private val closeExecutor: (() -> Unit)? = null,
    private val diagnosticSink: (ArgentinaBackendContract.FailureCode, ArgentinaBackendFailureMetadata?) -> Unit = { _, _ -> },
    private val successDiagnosticSink: (ArgentinaLiveSearchSuccessMetadata) -> Unit = {},
    /** Debug HTML uses the additive identity-only v2 rail; production keeps v1. */
    private val discoveryMode: Boolean = false
) : AutoCloseable {

    private val lock = Any()
    private var generation = 0L
    private var closed = false
    private var currentCancellation: ArgentinaRequestCancellation? = null
    private var currentContext: ArgentinaSearchContext? = null
    private var currentSelectionId: String? = null
    private var currentLanguage: String = "es"
    private var discoveryCursor: String? = null
    private var pinnedRelease: ArgentinaBackendContract.ExpectedRelease? = null
    private var currentState: ArgentinaLiveSearchState = ArgentinaLiveSearchState.Idle(0L)

    fun state(): ArgentinaLiveSearchState = synchronized(lock) { currentState }

    /** Stable refinement currently attached to the active generation. */
    fun selectionId(): String? = synchronized(lock) { currentSelectionId }

    /** Submit exactly one user-triggered query generation. */
    fun submit(
        rawQuery: String,
        context: ArgentinaSearchContext?,
        selectionId: String? = null,
        language: String = "es"
    ): Long {
        val query = rawQuery.trim()
        var task: (() -> Unit)? = null
        var scheduledGeneration = 0L
        var immediate: ArgentinaLiveSearchState
        synchronized(lock) {
            if (closed) return generation
            cancelCurrentLocked()
            currentContext = context
            currentSelectionId = selectionId
            currentLanguage = language
            discoveryCursor = null
            generation = nextGenerationLocked()
            val currentGeneration = generation
            scheduledGeneration = currentGeneration
            immediate =
                when {
                    query.isBlank() -> ArgentinaLiveSearchState.Idle(currentGeneration)
                    language !in setOf("es", "en") ->
                        ArgentinaLiveSearchState.Unavailable(
                            currentGeneration,
                            query,
                            ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
                        )
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
                        task = {
                            performSearch(
                                currentGeneration,
                                query,
                                context,
                                cancellation,
                                selectionId = selectionId,
                                language = language
                            )
                        }
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

    /** Request the next bounded identity page for the current result set. */
    fun loadMore(): Long {
        var task: (() -> Unit)? = null
        var immediate: ArgentinaLiveSearchState? = null
        var scheduledGeneration = 0L
        synchronized(lock) {
            if (closed) return generation
            val result =
                when (val state = currentState) {
                    is ArgentinaLiveSearchState.Results -> state.query to state.presentation
                    is ArgentinaLiveSearchState.Empty -> state.query to state.presentation
                    else -> null
                } ?: return generation
            val query = result.first
            val presentation = result.second
            val nextPage = presentation.nextPage ?: return generation
            if (discoveryMode && discoveryCursor == null) return generation
            val context = currentContext ?: return generation
            val selectionId = currentSelectionId
            val language = currentLanguage
            cancelCurrentLocked()
            generation = nextGenerationLocked()
            scheduledGeneration = generation
            val cancellation = ArgentinaRequestCancellation()
            currentCancellation = cancellation
            task = {
                performSearch(
                    requestGeneration = scheduledGeneration,
                    query = query,
                    context = context,
                    cancellation = cancellation,
                    page = nextPage,
                    previousPresentation = presentation,
                    selectionId = selectionId,
                    language = language
                )
            }
            immediate = ArgentinaLiveSearchState.Loading(
                scheduledGeneration,
                query,
                presentation
            )
            currentState = immediate
        }
        immediate?.let(stateSink)
        try {
            task?.let { work -> executor.execute(Runnable { work() }) }
        } catch (_: RuntimeException) {
            publishFailure(
                generation = scheduledGeneration,
                query = (immediate as? ArgentinaLiveSearchState.Loading)?.query.orEmpty(),
                failure = ArgentinaBackendContract.FailureCode.NETWORK_ERROR,
                previousPresentation = (immediate as? ArgentinaLiveSearchState.Loading)?.previousPresentation
            )
        }
        return scheduledGeneration
    }

    /** Request the next native-owned clarification facet page. */
    fun loadMoreClarification(): Long {
        var task: (() -> Unit)? = null
        var immediate: ArgentinaLiveSearchState? = null
        var scheduledGeneration = 0L
        synchronized(lock) {
            if (closed || !discoveryMode) return generation
            val result =
                when (val state = currentState) {
                    is ArgentinaLiveSearchState.Results -> state.query to state.presentation
                    is ArgentinaLiveSearchState.Empty -> state.query to state.presentation
                    else -> null
                } ?: return generation
            val query = result.first
            val presentation = result.second
            val facetCursor = presentation.clarification?.nextCursor ?: return generation
            val context = currentContext ?: return generation
            val selectionId = currentSelectionId
            val language = currentLanguage
            cancelCurrentLocked()
            generation = nextGenerationLocked()
            scheduledGeneration = generation
            val cancellation = ArgentinaRequestCancellation()
            currentCancellation = cancellation
            task = {
                performSearch(
                    requestGeneration = scheduledGeneration,
                    query = query,
                    context = context,
                    cancellation = cancellation,
                    page = presentation.page,
                    previousPresentation = presentation,
                    selectionId = selectionId,
                    language = language,
                    facetCursor = facetCursor
                )
            }
            immediate = ArgentinaLiveSearchState.Loading(
                scheduledGeneration,
                query,
                presentation
            )
            currentState = immediate
        }
        immediate?.let(stateSink)
        try {
            task?.let { work -> executor.execute(Runnable { work() }) }
        } catch (_: RuntimeException) {
            publishFailure(
                generation = scheduledGeneration,
                query = (immediate as? ArgentinaLiveSearchState.Loading)?.query.orEmpty(),
                failure = ArgentinaBackendContract.FailureCode.NETWORK_ERROR,
                previousPresentation = (immediate as? ArgentinaLiveSearchState.Loading)?.previousPresentation
            )
        }
        return scheduledGeneration
    }

    /** Query edits invalidate in-flight work but never start a request. */
    fun queryChanged(rawQuery: String): Long {
        val query = rawQuery.trim()
        val next: ArgentinaLiveSearchState
        synchronized(lock) {
            if (closed) return generation
            cancelCurrentLocked()
            currentContext = null
            currentSelectionId = null
            discoveryCursor = null
            generation = nextGenerationLocked()
            next =
                if (query.length > ArgentinaBackendContract.MAX_QUERY_CHARS) {
                    ArgentinaLiveSearchState.Unavailable(
                        generation,
                        query.take(ArgentinaBackendContract.MAX_QUERY_CHARS),
                        ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
                    )
                } else {
                    ArgentinaLiveSearchState.Idle(generation)
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
            currentContext = null
            currentSelectionId = null
            discoveryCursor = null
            pinnedRelease = null
            generation = nextGenerationLocked()
            next = ArgentinaLiveSearchState.Idle(generation)
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
                currentContext = null
                currentSelectionId = null
                discoveryCursor = null
                pinnedRelease = null
                generation = nextGenerationLocked()
                currentState = ArgentinaLiveSearchState.Idle(generation)
            }
        }
        if (shouldClose) closeExecutor?.invoke()
    }

    private fun performSearch(
        requestGeneration: Long,
        query: String,
        context: ArgentinaSearchContext,
        cancellation: ArgentinaRequestCancellation,
        page: Int = 0,
        previousPresentation: PracticalShoppingSearchIdentityPresentation? = null,
        selectionId: String? = null,
        language: String = "es",
        facetCursor: String? = null
    ) {
        try {
            val release = pinRelease(requestGeneration, cancellation)
            if (release is ArgentinaBackendBridgeResult.Failure) {
                publishFailure(requestGeneration, query, release.code, release.metadata, previousPresentation)
                return
            }
            if (!isCurrent(requestGeneration, cancellation)) return
            val expectedRelease = (release as ArgentinaBackendBridgeResult.Success).value
            val result = ArgentinaBackendConsumerBridge(transport, expectedRelease).let { bridge ->
                if (discoveryMode) {
                    val request = ArgentinaBackendContract.DiscoverySearchRequest(
                        query = query,
                        language = language,
                        selections = selectionId?.let { listOf(it) }.orEmpty(),
                        pageSize = ArgentinaBackendContract.MAX_DISCOVERY_PAGE_SIZE,
                        cursor = if (facetCursor == null) discoveryCursor else null,
                        facetCursor = facetCursor
                    )
                    when (val response = bridge.discoverySearch(request, cancellation)) {
                        is ArgentinaBackendBridgeResult.Failure -> response
                        is ArgentinaBackendBridgeResult.Success -> {
                            synchronized(lock) {
                                if (facetCursor == null && generation == requestGeneration && currentCancellation === cancellation) {
                                    discoveryCursor = response.value.result.pagination.nextCursor
                                }
                            }
                            val incoming = discoveryPresentation(response.value, page)
                            // Facet pages and product pages are independent cursors.
                            // Reading more choices must not consume product rows,
                            // restart exhausted results or replace the visible table.
                            val presentation = when {
                                facetCursor != null && previousPresentation != null ->
                                    previousPresentation.copy(clarification = incoming.clarification)
                                previousPresentation != null ->
                                    incoming.copy(clarification = previousPresentation.clarification)
                                else -> incoming
                            }
                            ArgentinaBackendBridgeResult.Success(presentation)
                        }
                    }
                } else {
                    val request = ArgentinaBackendContract.SearchRequest(
                        latitude = context.latitude,
                        longitude = context.longitude,
                        radiusKm = context.radiusKm,
                        query = query,
                        page = page,
                        selectionId = selectionId
                    )
                    when (val response = bridge.searchPresentation(request, cancellation)) {
                        is ArgentinaBackendBridgeResult.Failure -> response
                        is ArgentinaBackendBridgeResult.Success -> {
                            if (response.value.response.common.freshness != ArgentinaBackendContract.Freshness.FRESH) {
                                ArgentinaBackendBridgeResult.Failure(
                                    ArgentinaBackendContract.FailureCode.STALE_RELEASE,
                                    "search response is not fresh enough for live Search"
                                )
                            } else {
                                runCatching {
                                    successDiagnosticSink(successMetadata(response.value.response))
                                }
                                ArgentinaBackendBridgeResult.Success(response.value.identity)
                            }
                        }
                    }
                }
            }
            when (result) {
                is ArgentinaBackendBridgeResult.Failure -> {
                    if (result.code == ArgentinaBackendContract.FailureCode.RELEASE_MISMATCH) {
                        clearPinnedRelease(requestGeneration)
                    }
                    publishFailure(requestGeneration, query, result.code, result.metadata, previousPresentation)
                }
                is ArgentinaBackendBridgeResult.Success -> {
                    val presentation = mergePresentations(previousPresentation, result.value)
                    publish(
                        if (presentation.matches.isEmpty()) {
                            ArgentinaLiveSearchState.Empty(requestGeneration, query, presentation)
                        } else {
                            ArgentinaLiveSearchState.Results(requestGeneration, query, presentation)
                        }
                    )
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

    private fun discoveryPresentation(
        response: ArgentinaBackendContract.DiscoveryResponse,
        page: Int
    ): PracticalShoppingSearchIdentityPresentation {
        val result = response.result
        val clarification = result.clarification?.let { value ->
            ArgentinaBackendContract.SearchClarification(
                code = "PRODUCT_FAMILY_CLARIFICATION",
                family = value.dimensionId,
                message = "Elegí una forma observada para precisar el producto.",
                messageEn = "Choose an observed product form to refine the search.",
                choices = value.choices.map { choice ->
                    ArgentinaBackendContract.SearchClarificationChoice(
                        id = choice.id,
                        label = choice.labelEs,
                        labelEn = choice.labelEn,
                        // The stable predicate travels separately as selectionId.
                        query = result.query
                    )
                },
                evidence = "OBSERVED_CATALOG_NAMES",
                hasMore = value.hasMore,
                nextCursor = value.nextCursor,
                allowSingleChoice = value.choices.size == 1
            )
        }
        return PracticalShoppingSearchIdentityPresentation(
            query = result.query,
            matches = result.matches.map { match ->
                PracticalShoppingSearchIdentityPresentation.Match(
                    displayName = match.displayName,
                    brand = match.brand,
                    matchLabel = "provider identity match",
                    sourceLabel = "Argentina identity catalog",
                    identityKey = match.identityKey
                )
            },
            evaluatedCandidateCount = result.total.sourceIdentityCount,
            notice = "Argentina identity catalog only — no current prices, stock, availability or ranking is included.",
            page = page,
            hasMore = result.pagination.hasMore,
            nextPage = if (result.pagination.hasMore) page + 1 else null,
            limitReached = false,
            clarification = clarification
        )
    }

    private fun pinRelease(
        requestGeneration: Long,
        cancellation: ArgentinaRequestCancellation
    ): ArgentinaBackendBridgeResult<ArgentinaBackendContract.ExpectedRelease> {
        synchronized(lock) {
            if (
                closed || generation != requestGeneration ||
                currentCancellation !== cancellation || cancellation.isCancelled()
            ) {
                return ArgentinaBackendBridgeResult.Failure(
                    ArgentinaBackendContract.FailureCode.CANCELLED,
                    "search was superseded"
                )
            }
            pinnedRelease?.let { return ArgentinaBackendBridgeResult.Success(it) }
        }

        val status = ArgentinaBackendConsumerBridge(transport, null).status(cancellation)
        if (status is ArgentinaBackendBridgeResult.Failure) return status
        if (!isCurrent(requestGeneration, cancellation)) {
            return ArgentinaBackendBridgeResult.Failure(
                ArgentinaBackendContract.FailureCode.CANCELLED,
                "search was superseded"
            )
        }
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
        val accepted = synchronized(lock) {
            if (
                closed || generation != requestGeneration ||
                currentCancellation !== cancellation || cancellation.isCancelled()
            ) {
                false
            } else {
                pinnedRelease = expected
                true
            }
        }
        if (!accepted) {
            return ArgentinaBackendBridgeResult.Failure(
                ArgentinaBackendContract.FailureCode.CANCELLED,
                "search was superseded"
            )
        }
        return ArgentinaBackendBridgeResult.Success(expected)
    }

    private fun isCurrent(
        requestGeneration: Long,
        cancellation: ArgentinaRequestCancellation
    ): Boolean =
        synchronized(lock) {
            !closed && generation == requestGeneration &&
                currentCancellation === cancellation && !cancellation.isCancelled()
        }

    private fun clearPinnedRelease(requestGeneration: Long) {
        synchronized(lock) {
            if (generation == requestGeneration) pinnedRelease = null
        }
    }

    private fun publishFailure(
        generation: Long,
        query: String,
        failure: ArgentinaBackendContract.FailureCode,
        metadata: ArgentinaBackendFailureMetadata? = null,
        previousPresentation: PracticalShoppingSearchIdentityPresentation? = null
    ) {
        diagnosticSink(failure, metadata)
        publish(ArgentinaLiveSearchState.Unavailable(generation, query, failure, metadata, previousPresentation))
    }

    private fun mergePresentations(
        previous: PracticalShoppingSearchIdentityPresentation?,
        incoming: PracticalShoppingSearchIdentityPresentation
    ): PracticalShoppingSearchIdentityPresentation {
        if (previous == null) return incoming
        val merged = LinkedHashMap<String, PracticalShoppingSearchIdentityPresentation.Match>()
        (previous.matches + incoming.matches).forEach { match ->
            val existing = merged[match.identityKey]
            when {
                existing == null -> merged[match.identityKey] = match
                existing.qualifiedOffers.isEmpty() && match.qualifiedOffers.isNotEmpty() ->
                    merged[match.identityKey] = match
            }
        }
        return incoming.copy(
            matches = merged.values.toList(),
            evaluatedCandidateCount = maxOf(previous.evaluatedCandidateCount, incoming.evaluatedCandidateCount, merged.size)
        )
    }

    private fun successMetadata(
        response: ArgentinaBackendContract.SearchResponse
    ): ArgentinaLiveSearchSuccessMetadata {
        val pagination = response.pagination
        return ArgentinaLiveSearchSuccessMetadata(
            page = pagination?.page ?: 0,
            pageResultCount = response.identityCandidates.size,
            hasMore = pagination?.hasMore == true,
            limitReached = pagination?.limitReached == true,
            regionsQueried = response.common.diagnostics["regionsQueried"]
                .orEmpty()
                .split(',')
                .asSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && REGION_ID_PATTERN.matches(it) }
                .distinct()
                .take(MAX_REGIONS)
                .toList(),
            requestId = response.common.requestId,
            releaseId = response.common.releaseId,
            releaseDate = response.common.releaseDate
        )
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
            is ArgentinaLiveSearchState.Idle -> state.generation
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
        private const val MAX_REGIONS = 32
        private val REGION_ID_PATTERN = Regex("ar-[a-z0-9-]{1,31}")

        fun create(
            transport: ArgentinaBackendTransport,
            stateSink: (ArgentinaLiveSearchState) -> Unit,
            diagnosticSink: (ArgentinaBackendContract.FailureCode, ArgentinaBackendFailureMetadata?) -> Unit = { _, _ -> },
            successDiagnosticSink: (ArgentinaLiveSearchSuccessMetadata) -> Unit = {},
            discoveryMode: Boolean = false
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
                diagnosticSink = diagnosticSink,
                successDiagnosticSink = successDiagnosticSink,
                discoveryMode = discoveryMode,
                closeExecutor = executor::shutdownNow
            )
        }
    }

}

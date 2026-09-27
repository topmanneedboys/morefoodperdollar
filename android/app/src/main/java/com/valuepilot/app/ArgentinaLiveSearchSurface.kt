package com.valuepilot.app

import android.util.Log

/** Renderer-owned modes for the visible, identity-only Argentina Search. */
internal enum class ArgentinaLiveSearchSurfaceMode {
    IDLE,
    LOADING,
    RESULTS,
    NO_MATCHES,
    TEMPORARILY_UNAVAILABLE,
    AUTHORIZATION_UNAVAILABLE,
    STALE_RELEASE,
    TIMEOUT
}

internal data class ArgentinaLiveSearchSurfaceState(
    val areaLabel: String,
    val mode: ArgentinaLiveSearchSurfaceMode,
    val generation: Long = 0L,
    val query: String = "",
    val statusMessage: String,
    val presentation: PracticalShoppingSearchIdentityPresentation? = null,
    val caveat: String
) {
    init {
        require(areaLabel.isNotBlank())
        require(generation >= 0L)
        require(statusMessage.isNotBlank())
        require(caveat.isNotBlank())
        require(query.length <= ArgentinaBackendContract.MAX_QUERY_CHARS)
        require(presentation == null || presentation.query.isNotBlank())
        if (mode == ArgentinaLiveSearchSurfaceMode.RESULTS) {
            require(presentation != null && presentation.matches.isNotEmpty())
        }
        require(mode != ArgentinaLiveSearchSurfaceMode.NO_MATCHES || presentation != null)
    }
}

internal object ArgentinaLiveSearchSurfacePresenter {
    private const val IDENTITY_CAVEAT =
        "Identity suggestions only. No current price, stock, availability or ranking is confirmed; package size appears only when the source supplies it explicitly."

    fun initial(
        area: ArgentinaQualifiedLaunchArea,
        generation: Long = 0L
    ): ArgentinaLiveSearchSurfaceState =
        ArgentinaLiveSearchSurfaceState(
            areaLabel = area.label,
            mode = ArgentinaLiveSearchSurfaceMode.IDLE,
            generation = generation,
            statusMessage = "Search a product in the qualified Argentina area.",
            caveat = IDENTITY_CAVEAT
        )

    fun from(
        state: ArgentinaLiveSearchState,
        area: ArgentinaQualifiedLaunchArea
    ): ArgentinaLiveSearchSurfaceState =
        when (state) {
            is ArgentinaLiveSearchState.Idle -> initial(area, state.generation)
            is ArgentinaLiveSearchState.Loading ->
                ArgentinaLiveSearchSurfaceState(
                    areaLabel = area.label,
                    mode = ArgentinaLiveSearchSurfaceMode.LOADING,
                    generation = state.generation,
                    query = state.query,
                    statusMessage =
                        if (state.previousPresentation == null) {
                            "Searching Argentina product identities…"
                        } else {
                            "Loading more matching identities…"
                        },
                    presentation = state.previousPresentation,
                    caveat = IDENTITY_CAVEAT
                )
            is ArgentinaLiveSearchState.Results ->
                ArgentinaLiveSearchSurfaceState(
                    areaLabel = area.label,
                    mode = ArgentinaLiveSearchSurfaceMode.RESULTS,
                    generation = state.generation,
                    query = state.query,
                    statusMessage = resultStatus(state.presentation),
                    presentation = state.presentation,
                    caveat = IDENTITY_CAVEAT
                )
            is ArgentinaLiveSearchState.Empty ->
                ArgentinaLiveSearchSurfaceState(
                    areaLabel = area.label,
                    mode = ArgentinaLiveSearchSurfaceMode.NO_MATCHES,
                    generation = state.generation,
                    query = state.query,
                    statusMessage = "No matching product identity was found in this release.",
                    presentation = state.presentation,
                    caveat = IDENTITY_CAVEAT
                )
            is ArgentinaLiveSearchState.Unavailable ->
                unavailable(state, area)
        }

    private fun resultStatus(presentation: PracticalShoppingSearchIdentityPresentation): String =
        when {
            presentation.limitReached ->
                "Product identities found. This release reached the bounded result limit."
            presentation.hasMore ->
                "Product identities found. More matching identities are available."
            else ->
                "Product identities found. No more matching identities were returned in this release."
        }

    private fun unavailable(
        state: ArgentinaLiveSearchState.Unavailable,
        area: ArgentinaQualifiedLaunchArea
    ): ArgentinaLiveSearchSurfaceState {
        val (mode, message) =
            when (state.failure) {
                ArgentinaBackendContract.FailureCode.SEARCH_NOT_AUTHORIZED,
                ArgentinaBackendContract.FailureCode.APP_CHECK_UNAVAILABLE,
                ArgentinaBackendContract.FailureCode.UNAUTHORIZED,
                ArgentinaBackendContract.FailureCode.FORBIDDEN ->
                    ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE to
                        "Live Search is not authorized on this build yet."
                ArgentinaBackendContract.FailureCode.STALE_RELEASE,
                ArgentinaBackendContract.FailureCode.RELEASE_MISMATCH ->
                    ArgentinaLiveSearchSurfaceMode.STALE_RELEASE to
                        "This release is no longer current. Try again later."
                ArgentinaBackendContract.FailureCode.TIMEOUT ->
                    ArgentinaLiveSearchSurfaceMode.TIMEOUT to
                        "Search took too long. Try again."
                ArgentinaBackendContract.FailureCode.SERVICE_UNAVAILABLE ->
                    ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE to
                        "Live Search is temporarily unavailable. Try again."
                else ->
                    ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE to
                        "Live Search is temporarily unavailable. Try again."
            }
        return ArgentinaLiveSearchSurfaceState(
            areaLabel = area.label,
            mode = mode,
            generation = state.generation,
            query = state.query,
            statusMessage = message,
            presentation = state.previousPresentation,
            caveat = IDENTITY_CAVEAT
        )
    }
}

/** Thin lifecycle/action host between MainActivity and the coordinator. */
internal class ArgentinaLiveSearchSurfaceHost(
    private val coordinator: ArgentinaLiveSearchCoordinator,
    val area: ArgentinaQualifiedLaunchArea,
    private val render: (ArgentinaLiveSearchSurfaceState) -> Unit
) : AutoCloseable {
    @Volatile
    private var latest: ArgentinaLiveSearchSurfaceState =
        ArgentinaLiveSearchSurfacePresenter.initial(area)

    init {
        render(latest)
    }

    fun state(): ArgentinaLiveSearchSurfaceState = latest

    fun queryChanged(rawQuery: String) {
        coordinator.queryChanged(rawQuery)
    }

    fun submit(rawQuery: String) {
        coordinator.submit(rawQuery, area.context())
    }

    fun loadMore() {
        coordinator.loadMore()
    }

    fun invalidateForRouteChange() {
        coordinator.invalidateForRouteChange()
    }

    override fun close() {
        coordinator.close()
    }

    private fun accept(next: ArgentinaLiveSearchState) {
        val projected = ArgentinaLiveSearchSurfacePresenter.from(next, area)
        latest = projected
        render(projected)
    }

    companion object {
        fun createProduction(
            area: ArgentinaQualifiedLaunchArea,
            render: (ArgentinaLiveSearchSurfaceState) -> Unit
        ): ArgentinaLiveSearchSurfaceHost {
            lateinit var host: ArgentinaLiveSearchSurfaceHost
            val coordinator = ArgentinaLiveSearchCoordinator.create(
                transport = ValuePilotArgentinaEdgeTransportFactory.create(),
                stateSink = { state -> host.accept(state) },
                diagnosticSink = ArgentinaLiveSearchDiagnostics::record,
                successDiagnosticSink = ArgentinaLiveSearchDiagnostics::recordSuccess
            )
            host = ArgentinaLiveSearchSurfaceHost(coordinator, area, render)
            return host
        }

        fun forTesting(
            coordinator: ArgentinaLiveSearchCoordinator,
            area: ArgentinaQualifiedLaunchArea,
            render: (ArgentinaLiveSearchSurfaceState) -> Unit
        ): ArgentinaLiveSearchSurfaceHost =
            ArgentinaLiveSearchSurfaceHost(coordinator, area, render)
    }
}

/**
 * Production-only safe failure trace. It contains no query, response body,
 * App Check token, URL, headers or personal data.
 */
internal object ArgentinaLiveSearchDiagnostics {
    private const val TAG = "ValuePilotLiveSearch"

    fun record(
        failure: ArgentinaBackendContract.FailureCode,
        metadata: ArgentinaBackendFailureMetadata?
    ) {
        val fields = buildList {
            add("failure=${failure.name}")
            metadata?.endpoint?.let { add("endpoint=$it") }
            metadata?.httpStatus?.let { add("httpStatus=$it") }
            metadata?.requestId?.let { add("requestId=$it") }
            metadata?.releaseId?.let { add("releaseId=$it") }
            metadata?.releaseDate?.let { add("releaseDate=$it") }
        }
        Log.w(TAG, fields.joinToString(" "))
    }

    fun recordSuccess(metadata: ArgentinaLiveSearchSuccessMetadata) {
        val fields = buildList {
            add("success=true")
            add("page=${metadata.page}")
            add("pageResultCount=${metadata.pageResultCount}")
            add("hasMore=${metadata.hasMore}")
            add("limitReached=${metadata.limitReached}")
            add("regionsQueried=${metadata.regionsQueried.joinToString(",").ifBlank { "<unknown>" }}")
            metadata.requestId?.let { add("requestId=$it") }
            metadata.releaseId?.let { add("releaseId=$it") }
            metadata.releaseDate?.let { add("releaseDate=$it") }
        }
        // This is bounded verification telemetry only. It contains no query,
        // product identity, response body, token, URL, header or PII.
        Log.i(TAG, fields.joinToString(" "))
    }
}

/** Main-thread gate that drops callbacks queued before a newer query began. */
internal class ArgentinaLiveSearchMainThreadRenderGate {
    private var lastRenderedGeneration = -1L

    fun renderIfCurrent(
        state: ArgentinaLiveSearchSurfaceState,
        currentGeneration: Long,
        render: (ArgentinaLiveSearchSurfaceState) -> Unit
    ) {
        require(currentGeneration >= 0L)
        if (state.generation != currentGeneration || state.generation < lastRenderedGeneration) return
        lastRenderedGeneration = state.generation
        render(state)
    }
}

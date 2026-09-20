package com.valuepilot.app

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
    val query: String = "",
    val statusMessage: String,
    val presentation: PracticalShoppingSearchIdentityPresentation? = null,
    val caveat: String
) {
    init {
        require(areaLabel.isNotBlank())
        require(statusMessage.isNotBlank())
        require(caveat.isNotBlank())
        require(query.length <= ArgentinaBackendContract.MAX_QUERY_CHARS)
        require(presentation == null || presentation.query.isNotBlank())
        require(
            (mode == ArgentinaLiveSearchSurfaceMode.RESULTS) ==
                (presentation != null && presentation.matches.isNotEmpty())
        )
        require(mode != ArgentinaLiveSearchSurfaceMode.NO_MATCHES || presentation != null)
    }
}

internal object ArgentinaLiveSearchSurfacePresenter {
    private const val IDENTITY_CAVEAT =
        "Identity suggestions only. No current price, package quantity, stock, availability or ranking is confirmed."

    fun initial(area: ArgentinaQualifiedLaunchArea): ArgentinaLiveSearchSurfaceState =
        ArgentinaLiveSearchSurfaceState(
            areaLabel = area.label,
            mode = ArgentinaLiveSearchSurfaceMode.IDLE,
            statusMessage = "Search a product in the qualified Argentina area.",
            caveat = IDENTITY_CAVEAT
        )

    fun from(
        state: ArgentinaLiveSearchState,
        area: ArgentinaQualifiedLaunchArea
    ): ArgentinaLiveSearchSurfaceState =
        when (state) {
            ArgentinaLiveSearchState.Idle -> initial(area)
            is ArgentinaLiveSearchState.Loading ->
                ArgentinaLiveSearchSurfaceState(
                    areaLabel = area.label,
                    mode = ArgentinaLiveSearchSurfaceMode.LOADING,
                    query = state.query,
                    statusMessage = "Searching Argentina product identities…",
                    caveat = IDENTITY_CAVEAT
                )
            is ArgentinaLiveSearchState.Results ->
                ArgentinaLiveSearchSurfaceState(
                    areaLabel = area.label,
                    mode = ArgentinaLiveSearchSurfaceMode.RESULTS,
                    query = state.query,
                    statusMessage = "Product identities found.",
                    presentation = state.presentation,
                    caveat = IDENTITY_CAVEAT
                )
            is ArgentinaLiveSearchState.Empty ->
                ArgentinaLiveSearchSurfaceState(
                    areaLabel = area.label,
                    mode = ArgentinaLiveSearchSurfaceMode.NO_MATCHES,
                    query = state.query,
                    statusMessage = "No matching product identity was found in this release.",
                    presentation = state.presentation,
                    caveat = IDENTITY_CAVEAT
                )
            is ArgentinaLiveSearchState.Unavailable ->
                unavailable(state, area)
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
                else ->
                    ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE to
                        "Live Search is temporarily unavailable. Try again."
            }
        return ArgentinaLiveSearchSurfaceState(
            areaLabel = area.label,
            mode = mode,
            query = state.query,
            statusMessage = message,
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
                stateSink = { state -> host.accept(state) }
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

package com.valuepilot.app

/**
 * UI-only modes for the physical Saved surface.
 *
 * These values carry no persistence, product/store identity, price, or provider authority.
 */
enum class PracticalShoppingSavedSurfaceMode {
    IDLE,
    LOADING,
    REFRESHING,
    CONTENT,
    EMPTY,
    DEGRADED,
    UPDATING,
    ERROR
}

/** Typed actions that a replaceable Saved surface may emit. */
sealed interface PracticalShoppingSavedSurfaceAction {
    data object Refresh : PracticalShoppingSavedSurfaceAction

    data class Preference(
        val action: PracticalShoppingSavedExactPreferenceUiAction
    ) : PracticalShoppingSavedSurfaceAction
}

data class PracticalShoppingSavedSurfaceProductRow(
    val title: String,
    val supportingText: String,
    val action: PracticalShoppingSavedSurfaceAction.Preference?,
    val actionLabel: String?,
    val actionDescription: String?
) {
    init {
        require(title.isNotBlank())
        require(supportingText.isNotBlank())
        require((action != null) == (actionLabel != null))
        require((action != null) == (actionDescription != null))
        require(actionLabel == null || actionLabel.isNotBlank())
        require(actionDescription == null || actionDescription.isNotBlank())
    }
}

data class PracticalShoppingSavedSurfaceStoreRow(
    val title: String,
    val supportingText: String,
    val action: PracticalShoppingSavedSurfaceAction.Preference?,
    val actionLabel: String?,
    val actionDescription: String?
) {
    init {
        require(title.isNotBlank())
        require(supportingText.isNotBlank())
        require((action != null) == (actionLabel != null))
        require((action != null) == (actionDescription != null))
        require(actionLabel == null || actionLabel.isNotBlank())
        require(actionDescription == null || actionDescription.isNotBlank())
    }
}

/**
 * Immutable state consumed by a physical Saved renderer.
 *
 * Raw exact-preference keys remain only inside typed actions. Every normal string—including
 * section and action labels—is already consumer-ready so a physical renderer does not infer
 * lifecycle semantics or manufacture copy.
 */
data class PracticalShoppingSavedSurfaceState(
    val mode: PracticalShoppingSavedSurfaceMode,
    val headline: String,
    val productSectionTitle: String?,
    val productRows: List<PracticalShoppingSavedSurfaceProductRow>,
    val storeSectionTitle: String?,
    val storeRows: List<PracticalShoppingSavedSurfaceStoreRow>,
    val notice: String?,
    val emptyMessage: String?,
    val statusMessage: String?,
    val progressVisible: Boolean,
    val refreshAction: PracticalShoppingSavedSurfaceAction.Refresh?,
    val refreshActionLabel: String?,
    val clearAllAction: PracticalShoppingSavedSurfaceAction.Preference?,
    val clearAllActionLabel: String?
) {
    init {
        require(headline.isNotBlank())
        require((productRows.isNotEmpty()) == (productSectionTitle != null))
        require((storeRows.isNotEmpty()) == (storeSectionTitle != null))
        require(productSectionTitle == null || productSectionTitle.isNotBlank())
        require(storeSectionTitle == null || storeSectionTitle.isNotBlank())
        require(notice == null || notice.isNotBlank())
        require(emptyMessage == null || emptyMessage.isNotBlank())
        require(statusMessage == null || statusMessage.isNotBlank())
        require((refreshAction != null) == (refreshActionLabel != null))
        require((clearAllAction != null) == (clearAllActionLabel != null))
        require(refreshActionLabel == null || refreshActionLabel.isNotBlank())
        require(clearAllActionLabel == null || clearAllActionLabel.isNotBlank())
        require(
            progressVisible ==
                (mode == PracticalShoppingSavedSurfaceMode.LOADING ||
                    mode == PracticalShoppingSavedSurfaceMode.REFRESHING ||
                    mode == PracticalShoppingSavedSurfaceMode.UPDATING)
        )
        require(
            mode != PracticalShoppingSavedSurfaceMode.ERROR ||
                (statusMessage != null && refreshAction != null)
        )
        require(
            mode != PracticalShoppingSavedSurfaceMode.EMPTY ||
                (emptyMessage != null && productRows.isEmpty() && storeRows.isEmpty())
        )
        require(
            mode !in setOf(
                PracticalShoppingSavedSurfaceMode.LOADING,
                PracticalShoppingSavedSurfaceMode.REFRESHING,
                PracticalShoppingSavedSurfaceMode.UPDATING,
                PracticalShoppingSavedSurfaceMode.ERROR
            ) || clearAllAction == null
        )
    }
}

/**
 * Pure mapping from the verified Saved lifecycle into renderer-ready state.
 *
 * The projector never reads storage, resolves identity, parses labels, owns a clock, starts
 * work, or mutates Saved state. Busy states deliberately suppress row/clear actions even when
 * a previous projection is retained, preventing a view from issuing work the lifecycle host
 * would have to reject. Successful content/degraded states expose only typed actions already
 * emitted by the verified Saved UI projector.
 */
object PracticalShoppingSavedSurfaceProjector {

    fun project(
        lifecycle: PracticalShoppingSavedLifecycleState
    ): PracticalShoppingSavedSurfaceState =
        when (lifecycle.status) {
            PracticalShoppingSavedLifecycleStatus.IDLE ->
                base(
                    mode = PracticalShoppingSavedSurfaceMode.IDLE,
                    statusMessage = "Saved choices are ready to load.",
                    refreshAction = PracticalShoppingSavedSurfaceAction.Refresh,
                    refreshActionLabel = "Load saved choices"
                )

            PracticalShoppingSavedLifecycleStatus.LOADING ->
                if (lifecycle.projection == null) {
                    base(
                        mode = PracticalShoppingSavedSurfaceMode.LOADING,
                        statusMessage = "Loading saved choices…",
                        progressVisible = true
                    )
                } else {
                    fromProjection(
                        projection = lifecycle.projection,
                        mode = PracticalShoppingSavedSurfaceMode.REFRESHING,
                        statusMessage = "Refreshing saved choices…",
                        progressVisible = true,
                        interactionsEnabled = false
                    )
                }

            PracticalShoppingSavedLifecycleStatus.READY ->
                readyOrEmpty(requireNotNull(lifecycle.projection))

            PracticalShoppingSavedLifecycleStatus.DEGRADED ->
                fromProjection(
                    projection = requireNotNull(lifecycle.projection),
                    mode = PracticalShoppingSavedSurfaceMode.DEGRADED,
                    statusMessage = degradedMessage(lifecycle),
                    refreshAction = PracticalShoppingSavedSurfaceAction.Refresh,
                    refreshActionLabel = "Refresh",
                    interactionsEnabled = true
                )

            PracticalShoppingSavedLifecycleStatus.MUTATING ->
                lifecycle.projection?.let { projection ->
                    fromProjection(
                        projection = projection,
                        mode = PracticalShoppingSavedSurfaceMode.UPDATING,
                        statusMessage = "Updating saved choices…",
                        progressVisible = true,
                        interactionsEnabled = false
                    )
                } ?: base(
                    mode = PracticalShoppingSavedSurfaceMode.UPDATING,
                    statusMessage = "Updating saved choices…",
                    progressVisible = true
                )

            PracticalShoppingSavedLifecycleStatus.ERROR ->
                base(
                    mode = PracticalShoppingSavedSurfaceMode.ERROR,
                    statusMessage = errorMessage(lifecycle.failure),
                    refreshAction = PracticalShoppingSavedSurfaceAction.Refresh,
                    refreshActionLabel = "Try again"
                )
        }

    private fun readyOrEmpty(
        projection: PracticalShoppingSavedExactPreferenceUiProjection
    ): PracticalShoppingSavedSurfaceState {
        val mode =
            if (projection.state.emptyMessage != null) {
                PracticalShoppingSavedSurfaceMode.EMPTY
            } else {
                PracticalShoppingSavedSurfaceMode.CONTENT
            }

        return fromProjection(
            projection = projection,
            mode = mode,
            refreshAction = PracticalShoppingSavedSurfaceAction.Refresh,
            refreshActionLabel = "Refresh",
            interactionsEnabled = true
        )
    }

    private fun fromProjection(
        projection: PracticalShoppingSavedExactPreferenceUiProjection,
        mode: PracticalShoppingSavedSurfaceMode,
        statusMessage: String? = null,
        progressVisible: Boolean = false,
        refreshAction: PracticalShoppingSavedSurfaceAction.Refresh? = null,
        refreshActionLabel: String? = null,
        interactionsEnabled: Boolean
    ): PracticalShoppingSavedSurfaceState {
        require((refreshAction != null) == (refreshActionLabel != null))

        val state = projection.state
        val products =
            state.productRows.map { row ->
                PracticalShoppingSavedSurfaceProductRow(
                    title = row.title,
                    supportingText = row.supportingText,
                    action =
                        if (interactionsEnabled) {
                            PracticalShoppingSavedSurfaceAction.Preference(row.action)
                        } else {
                            null
                        },
                    actionLabel = if (interactionsEnabled) "Remove" else null,
                    actionDescription =
                        if (interactionsEnabled) {
                            "Remove saved product ${row.title}"
                        } else {
                            null
                        }
                )
            }
        val stores =
            state.storeRows.map { row ->
                PracticalShoppingSavedSurfaceStoreRow(
                    title = row.title,
                    supportingText = row.supportingText,
                    action =
                        if (interactionsEnabled) {
                            PracticalShoppingSavedSurfaceAction.Preference(row.action)
                        } else {
                            null
                        },
                    actionLabel = if (interactionsEnabled) "Remove" else null,
                    actionDescription =
                        if (interactionsEnabled) {
                            "Remove saved store ${row.title}"
                        } else {
                            null
                        }
                )
            }
        val clearAll =
            if (interactionsEnabled) {
                state.clearAllAction?.let { action ->
                    PracticalShoppingSavedSurfaceAction.Preference(action)
                }
            } else {
                null
            }

        return PracticalShoppingSavedSurfaceState(
            mode = mode,
            headline = state.headline,
            productSectionTitle = if (products.isEmpty()) null else "Products",
            productRows = products,
            storeSectionTitle = if (stores.isEmpty()) null else "Stores",
            storeRows = stores,
            notice = state.notice,
            emptyMessage = state.emptyMessage,
            statusMessage = statusMessage,
            progressVisible = progressVisible,
            refreshAction = refreshAction,
            refreshActionLabel = refreshActionLabel,
            clearAllAction = clearAll,
            clearAllActionLabel = if (clearAll == null) null else "Clear all"
        )
    }

    private fun base(
        mode: PracticalShoppingSavedSurfaceMode,
        statusMessage: String? = null,
        progressVisible: Boolean = false,
        refreshAction: PracticalShoppingSavedSurfaceAction.Refresh? = null,
        refreshActionLabel: String? = null
    ): PracticalShoppingSavedSurfaceState {
        require((refreshAction != null) == (refreshActionLabel != null))
        return PracticalShoppingSavedSurfaceState(
            mode = mode,
            headline = "Saved choices",
            productSectionTitle = null,
            productRows = emptyList(),
            storeSectionTitle = null,
            storeRows = emptyList(),
            notice = null,
            emptyMessage = null,
            statusMessage = statusMessage,
            progressVisible = progressVisible,
            refreshAction = refreshAction,
            refreshActionLabel = refreshActionLabel,
            clearAllAction = null,
            clearAllActionLabel = null
        )
    }

    private fun degradedMessage(
        lifecycle: PracticalShoppingSavedLifecycleState
    ): String =
        when {
            lifecycle.displayMetadataDegraded && lifecycle.displayCleanupDegraded ->
                "Some saved names and display cleanup are temporarily unavailable."

            lifecycle.displayMetadataDegraded ->
                "Some saved names couldn't be loaded. Exact saved choices are unchanged."

            lifecycle.displayCleanupDegraded ->
                "Saved choices were updated, but some display cleanup is still pending."

            else ->
                error("DEGRADED lifecycle state must carry a degradation reason.")
        }

    private fun errorMessage(
        failure: PracticalShoppingSavedLifecycleFailure?
    ): String =
        when (failure) {
            PracticalShoppingSavedLifecycleFailure.LOAD_FAILED ->
                "Saved choices couldn't be loaded."

            PracticalShoppingSavedLifecycleFailure.ACTION_FAILED ->
                "That saved-choice change couldn't be completed."

            null ->
                "Saved choices are temporarily unavailable."
        }
}

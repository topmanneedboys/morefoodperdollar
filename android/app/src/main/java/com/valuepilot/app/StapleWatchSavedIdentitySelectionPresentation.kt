package com.valuepilot.app

private const val MAX_STAPLE_WATCH_SAVED_PRODUCT_ROWS = 128
private const val MAX_STAPLE_WATCH_SAVED_STORE_ROWS = 64

/** UI-only identity readiness for configuring Watch My Staples from explicit Saved choices. */
enum class StapleWatchSavedSelectionUiStatus {
    NEEDS_SELECTION,
    READY_FOR_FACT_CHECK,
    DISPLAY_METADATA_INCOMPLETE
}

/**
 * Consumer continuation marker for an explicit identity-only handoff request.
 *
 * This action carries no item/store identity and grants no fact, economic, persistence, background,
 * or notification authority. A later composition owner may map the explicit request through the
 * separately verified handoff gate.
 */
sealed interface StapleWatchSavedIdentityHandoffUiAction {
    data object Request : StapleWatchSavedIdentityHandoffUiAction
}

data class StapleWatchSavedProductSelectionUiRow(
    val title: String,
    val watched: Boolean,
    val action: StapleWatchSavedIdentitySelectionAction.SetProductWatched,
    val actionLabel: String,
    val actionDescription: String
) {
    init {
        require(title.isNotBlank())
        require(actionLabel.isNotBlank())
        require(actionDescription.isNotBlank())
        require(action.watched != watched)
    }
}

data class StapleWatchSavedStoreSelectionUiRow(
    val title: String,
    val usualStore: Boolean,
    val action: StapleWatchSavedIdentitySelectionAction,
    val actionLabel: String,
    val actionDescription: String
) {
    init {
        require(title.isNotBlank())
        require(actionLabel.isNotBlank())
        require(actionDescription.isNotBlank())
        require(
            if (usualStore) {
                action == StapleWatchSavedIdentitySelectionAction.ClearUsualStore
            } else {
                action is StapleWatchSavedIdentitySelectionAction.SelectUsualStore
            }
        )
    }
}

/**
 * Immutable consumer-ready setup state.
 *
 * [status] describes only explicit Saved identity readiness. [factCheckCapability] is a separate
 * composition fact describing whether this build has a real foreground fact source configured.
 * Stable item/store identities exist only inside typed selection actions. The continuation marker
 * carries no identity. All normal strings come from already-sanitized Saved display metadata or
 * fixed product copy. A renderer must never parse a label to recover identity or infer whether an
 * economic switch or notification is authorized.
 */
data class StapleWatchSavedSelectionUiState(
    val status: StapleWatchSavedSelectionUiStatus,
    val headline: String,
    val guidance: String,
    val selectionSummary: String,
    val productSectionTitle: String?,
    val productRows: List<StapleWatchSavedProductSelectionUiRow>,
    val storeSectionTitle: String?,
    val storeRows: List<StapleWatchSavedStoreSelectionUiRow>,
    val watchedItemCount: Int,
    val usualStoreSelected: Boolean,
    val unresolvedDisplayNameCount: Int,
    val selectedDisplayNameBlockerCount: Int,
    val notice: String?,
    val clearSelectionAction: StapleWatchSavedIdentitySelectionAction.ClearSelection?,
    val clearSelectionActionLabel: String?,
    val continueAction: StapleWatchSavedIdentityHandoffUiAction?,
    val continueActionLabel: String?,
    val factCheckCapability: StapleWatchForegroundFactCheckCapability =
        StapleWatchForegroundFactCheckCapability.CONFIGURED,
    val factResolutionProgress: StapleWatchFactResolutionUiState? = null
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(selectionSummary.isNotBlank())
        require(productRows.size <= MAX_STAPLE_WATCH_SAVED_PRODUCT_ROWS)
        require(storeRows.size <= MAX_STAPLE_WATCH_SAVED_STORE_ROWS)
        require((productRows.isNotEmpty()) == (productSectionTitle != null))
        require((storeRows.isNotEmpty()) == (storeSectionTitle != null))
        require(productSectionTitle == null || productSectionTitle.isNotBlank())
        require(storeSectionTitle == null || storeSectionTitle.isNotBlank())
        require(watchedItemCount in 0..MAX_STAPLE_WATCH_SAVED_PRODUCT_ROWS)
        require(unresolvedDisplayNameCount >= 0)
        require(selectedDisplayNameBlockerCount in 0..unresolvedDisplayNameCount)
        require(notice == null || notice.isNotBlank())
        require((clearSelectionAction != null) == (clearSelectionActionLabel != null))
        require(clearSelectionActionLabel == null || clearSelectionActionLabel.isNotBlank())
        require((continueAction != null) == (continueActionLabel != null))
        require(continueActionLabel == null || continueActionLabel.isNotBlank())
        require(
            (
                status == StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK &&
                    factCheckCapability == StapleWatchForegroundFactCheckCapability.CONFIGURED
            ) == (continueAction != null)
        )
        require(
            continueAction == null ||
                continueAction == StapleWatchSavedIdentityHandoffUiAction.Request
        )
        require(
            (status == StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE) ==
                (selectedDisplayNameBlockerCount > 0)
        )
    }
}

/**
 * Pure consumer projection for explicit Saved-backed staple selection.
 *
 * The verified Saved projector remains the only authority for turning detached display metadata
 * into consumer labels. This projector never falls back to stable keys, source ids, provider
 * provenance, merchant/location scope, or other technical identity text.
 *
 * A missing display name for an unselected Saved choice merely hides that optional row. A missing
 * display name for an already selected watched product or usual store fails closed: setup is shown
 * as DISPLAY_METADATA_INCOMPLETE even if the identity reducer could otherwise form a handoff.
 *
 * The identity-only continuation marker is exposed only when the identity setup status is
 * READY_FOR_FACT_CHECK. The projection marks capability CONFIGURED only as a neutral default for
 * this pure identity artifact; the physical presentation boundary must overwrite it with the actual
 * composition capability before rendering. The marker does not itself create a handoff or start work.
 *
 * This boundary owns no fact retrieval, price calculation, route calculation, evidence-freshness
 * policy, scheduling, storage, Android lifecycle, or delivery authority.
 */
object StapleWatchSavedIdentitySelectionUiProjector {

    fun project(
        savedState: PracticalShoppingSavedExactPreferenceState,
        selection: StapleWatchSavedIdentitySelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ): StapleWatchSavedSelectionUiState {
        val current = StapleWatchSavedIdentitySelectionReducer.reconcile(selection, savedState)
        val savedProjection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = savedState,
                metadata = metadata
            )
        val watched = current.watchedItemKeys.toSet()

        val productRows =
            savedProjection.state.productRows.map { savedRow ->
                val itemKey = savedRow.action.itemKey
                val isWatched = itemKey in watched
                StapleWatchSavedProductSelectionUiRow(
                    title = savedRow.title,
                    watched = isWatched,
                    action =
                        StapleWatchSavedIdentitySelectionAction.SetProductWatched(
                            itemKey = itemKey,
                            watched = !isWatched
                        ),
                    actionLabel = if (isWatched) "Stop watching" else "Watch",
                    actionDescription =
                        if (isWatched) {
                            "Stop watching saved product ${savedRow.title}"
                        } else {
                            "Watch saved product ${savedRow.title}"
                        }
                )
            }

        val storeRows =
            savedProjection.state.storeRows.map { savedRow ->
                val storeKey = savedRow.action.storeKey
                val isUsualStore = current.usualStoreKey == storeKey
                StapleWatchSavedStoreSelectionUiRow(
                    title = savedRow.title,
                    usualStore = isUsualStore,
                    action =
                        if (isUsualStore) {
                            StapleWatchSavedIdentitySelectionAction.ClearUsualStore
                        } else {
                            StapleWatchSavedIdentitySelectionAction.SelectUsualStore(storeKey)
                        },
                    actionLabel = if (isUsualStore) "Clear usual store" else "Use as usual store",
                    actionDescription =
                        if (isUsualStore) {
                            "Clear ${savedRow.title} as usual store"
                        } else {
                            "Use ${savedRow.title} as usual store"
                        }
                )
            }

        val unresolvedProducts = savedProjection.unresolvedProductKeys.toSet()
        val unresolvedStores = savedProjection.unresolvedStoreKeys.toSet()
        val selectedProductBlockers =
            current.watchedItemKeys.count { itemKey -> itemKey in unresolvedProducts }
        val selectedStoreBlockers =
            if (current.usualStoreKey?.let(unresolvedStores::contains) == true) 1 else 0
        val selectedBlockers = selectedProductBlockers + selectedStoreBlockers
        val identityReady =
            StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(
                selection = current,
                savedState = savedState
            ) != null

        val status =
            when {
                selectedBlockers > 0 ->
                    StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE
                identityReady -> StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK
                else -> StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION
            }

        val hasSelection = current.watchedItemKeys.isNotEmpty() || current.usualStoreKey != null
        val canContinue = status == StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK

        return StapleWatchSavedSelectionUiState(
            status = status,
            headline = "Watch My Staples",
            guidance = guidance(status),
            selectionSummary =
                "${current.watchedItemKeys.size} staples selected " +
                    "($MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF minimum) · " +
                    if (current.usualStoreKey == null) {
                        "Usual store not selected"
                    } else {
                        "Usual store selected"
                    },
            productSectionTitle = if (productRows.isEmpty()) null else "Saved products",
            productRows = productRows,
            storeSectionTitle = if (storeRows.isEmpty()) null else "Usual store",
            storeRows = storeRows,
            watchedItemCount = current.watchedItemKeys.size,
            usualStoreSelected = current.usualStoreKey != null,
            unresolvedDisplayNameCount = savedProjection.state.unresolvedDisplayNameCount,
            selectedDisplayNameBlockerCount = selectedBlockers,
            notice = notice(selectedBlockers, savedProjection.state.unresolvedDisplayNameCount),
            clearSelectionAction =
                if (hasSelection) StapleWatchSavedIdentitySelectionAction.ClearSelection else null,
            clearSelectionActionLabel = if (hasSelection) "Clear staple setup" else null,
            continueAction =
                if (canContinue) StapleWatchSavedIdentityHandoffUiAction.Request else null,
            continueActionLabel = if (canContinue) "Continue" else null
        )
    }

    private fun guidance(status: StapleWatchSavedSelectionUiStatus): String =
        when (status) {
            StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION ->
                "Choose at least two saved staples and your usual store."
            StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK ->
                "Staple identities are ready for current price, route, and evidence checks."
            StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE ->
                "Refresh the selected saved choice names before continuing."
        }

    private fun notice(selectedBlockers: Int, unresolvedTotal: Int): String? =
        when {
            selectedBlockers == 1 ->
                "1 selected saved choice needs a current display name before setup can continue."
            selectedBlockers > 1 ->
                "$selectedBlockers selected saved choices need current display names before setup can continue."
            unresolvedTotal == 1 ->
                "1 other saved choice is hidden until its display name is available."
            unresolvedTotal > 1 ->
                "$unresolvedTotal other saved choices are hidden until their display names are available."
            else -> null
        }
}

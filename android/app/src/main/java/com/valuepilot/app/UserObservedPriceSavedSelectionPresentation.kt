package com.valuepilot.app

private const val MAX_OBSERVED_PRICE_SAVED_PRODUCT_ROWS = 128
private const val MAX_OBSERVED_PRICE_SAVED_STORE_ROWS = 64

/** UI-only readiness for an explicit Saved product/store pair before the separate prefill check. */
internal enum class UserObservedPriceSavedSelectionUiStatus {
    NEEDS_SELECTION,
    READY_FOR_PREFILL_CHECK,
    DISPLAY_METADATA_INCOMPLETE
}

/**
 * Consumer request marker for checking an already-explicit Saved pair through the verified prefill
 * handoff. The marker carries no identity and grants no route, draft, proof, price, or confirmation
 * authority.
 */
internal sealed interface UserObservedPriceSavedPrefillCheckUiAction {
    data object Request : UserObservedPriceSavedPrefillCheckUiAction
}

internal data class UserObservedPriceSavedProductSelectionUiRow(
    val title: String,
    val selected: Boolean,
    val action: UserObservedPriceSavedSelectionAction,
    val actionLabel: String
) {
    init {
        require(title.isNotBlank())
        require(actionLabel.isNotBlank())
        require(
            if (selected) {
                action == UserObservedPriceSavedSelectionAction.ClearProduct
            } else {
                action is UserObservedPriceSavedSelectionAction.SelectProduct
            }
        )
    }
}

internal data class UserObservedPriceSavedStoreSelectionUiRow(
    val title: String,
    val selected: Boolean,
    val action: UserObservedPriceSavedSelectionAction,
    val actionLabel: String
) {
    init {
        require(title.isNotBlank())
        require(actionLabel.isNotBlank())
        require(
            if (selected) {
                action == UserObservedPriceSavedSelectionAction.ClearStore
            } else {
                action is UserObservedPriceSavedSelectionAction.SelectStore
            }
        )
    }
}

/**
 * Immutable consumer state for choosing one Saved product and one Saved store.
 *
 * READY_FOR_PREFILL_CHECK means only that an explicit, still-saved pair has safe consumer labels.
 * The separately verified prefill gate must still check GTIN availability/validity and exact Saved
 * resolution. This state carries no prefill payload, proof, price, time, generated id, evidence,
 * current-price status, ranking result, route intent, or draft mutation authority.
 */
internal data class UserObservedPriceSavedSelectionUiState(
    val status: UserObservedPriceSavedSelectionUiStatus,
    val headline: String,
    val guidance: String,
    val productSectionTitle: String?,
    val productRows: List<UserObservedPriceSavedProductSelectionUiRow>,
    val storeSectionTitle: String?,
    val storeRows: List<UserObservedPriceSavedStoreSelectionUiRow>,
    val productSelected: Boolean,
    val storeSelected: Boolean,
    val unresolvedDisplayNameCount: Int,
    val selectedDisplayNameBlockerCount: Int,
    val notice: String?,
    val clearSelectionAction: UserObservedPriceSavedSelectionAction.ClearSelection?,
    val clearSelectionActionLabel: String?,
    val checkPrefillAction: UserObservedPriceSavedPrefillCheckUiAction?,
    val checkPrefillActionLabel: String?
) {
    init {
        require(headline.isNotBlank())
        require(guidance.isNotBlank())
        require(productRows.size <= MAX_OBSERVED_PRICE_SAVED_PRODUCT_ROWS)
        require(storeRows.size <= MAX_OBSERVED_PRICE_SAVED_STORE_ROWS)
        require((productRows.isNotEmpty()) == (productSectionTitle != null))
        require((storeRows.isNotEmpty()) == (storeSectionTitle != null))
        require(productSectionTitle == null || productSectionTitle.isNotBlank())
        require(storeSectionTitle == null || storeSectionTitle.isNotBlank())
        require(unresolvedDisplayNameCount >= 0)
        require(selectedDisplayNameBlockerCount in 0..unresolvedDisplayNameCount)
        require(notice == null || notice.isNotBlank())
        require((clearSelectionAction != null) == (clearSelectionActionLabel != null))
        require(clearSelectionActionLabel == null || clearSelectionActionLabel.isNotBlank())
        require((checkPrefillAction != null) == (checkPrefillActionLabel != null))
        require(checkPrefillActionLabel == null || checkPrefillActionLabel.isNotBlank())
        require(
            (status == UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK) ==
                (checkPrefillAction != null)
        )
        require(
            checkPrefillAction == null ||
                checkPrefillAction == UserObservedPriceSavedPrefillCheckUiAction.Request
        )
        require(
            (status == UserObservedPriceSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE) ==
                (selectedDisplayNameBlockerCount > 0)
        )
    }
}

/**
 * Pure projection from validated Saved identity state plus separately validated display metadata.
 *
 * Consumer labels come only from [PracticalShoppingSavedExactPreferenceUiProjector]. Missing or
 * unsafe labels are never replaced with stable keys, GTINs, provider ids, merchant/location keys,
 * or other technical identity text. Unselected unresolved choices are hidden. A selected unresolved
 * choice blocks the check marker until its consumer label is available again.
 *
 * This projector intentionally does not invoke [UserObservedPriceSavedPrefillGate] or
 * [UserObservedPriceSavedPrefillHandoffGate]. Pair readiness here therefore cannot imply GTIN
 * readiness, proof availability, observed-price validity, route availability, or confirmation
 * eligibility.
 */
internal object UserObservedPriceSavedSelectionUiProjector {

    fun project(
        savedState: PracticalShoppingSavedExactPreferenceState,
        selection: UserObservedPriceSavedSelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ): UserObservedPriceSavedSelectionUiState {
        val current = UserObservedPriceSavedSelectionReducer.reconcile(selection, savedState)
        val savedProjection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = savedState,
                metadata = metadata
            )

        val productRows =
            savedProjection.state.productRows.map { savedRow ->
                val itemKey = savedRow.action.itemKey
                val isSelected = current.itemKey == itemKey
                UserObservedPriceSavedProductSelectionUiRow(
                    title = savedRow.title,
                    selected = isSelected,
                    action =
                        if (isSelected) {
                            UserObservedPriceSavedSelectionAction.ClearProduct
                        } else {
                            UserObservedPriceSavedSelectionAction.SelectProduct(itemKey)
                        },
                    actionLabel = if (isSelected) "Clear product" else "Select product"
                )
            }

        val storeRows =
            savedProjection.state.storeRows.map { savedRow ->
                val storeKey = savedRow.action.storeKey
                val isSelected = current.storeKey == storeKey
                UserObservedPriceSavedStoreSelectionUiRow(
                    title = savedRow.title,
                    selected = isSelected,
                    action =
                        if (isSelected) {
                            UserObservedPriceSavedSelectionAction.ClearStore
                        } else {
                            UserObservedPriceSavedSelectionAction.SelectStore(storeKey)
                        },
                    actionLabel = if (isSelected) "Clear store" else "Select store"
                )
            }

        val unresolvedProducts = savedProjection.unresolvedProductKeys.toSet()
        val unresolvedStores = savedProjection.unresolvedStoreKeys.toSet()
        val selectedProductBlocker =
            if (current.itemKey?.let(unresolvedProducts::contains) == true) 1 else 0
        val selectedStoreBlocker =
            if (current.storeKey?.let(unresolvedStores::contains) == true) 1 else 0
        val selectedBlockers = selectedProductBlocker + selectedStoreBlocker
        val pairReady =
            UserObservedPriceSavedSelectionReducer.selectedPairOrNull(
                selection = current,
                savedState = savedState
            ) != null

        val status =
            when {
                selectedBlockers > 0 ->
                    UserObservedPriceSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE
                pairReady -> UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK
                else -> UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION
            }
        val hasSelection = current.itemKey != null || current.storeKey != null
        val canCheck = status == UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK

        return UserObservedPriceSavedSelectionUiState(
            status = status,
            headline = "Confirm an observed price",
            guidance = guidance(status),
            productSectionTitle = if (productRows.isEmpty()) null else "Saved products",
            productRows = productRows,
            storeSectionTitle = if (storeRows.isEmpty()) null else "Saved stores",
            storeRows = storeRows,
            productSelected = current.itemKey != null,
            storeSelected = current.storeKey != null,
            unresolvedDisplayNameCount = savedProjection.state.unresolvedDisplayNameCount,
            selectedDisplayNameBlockerCount = selectedBlockers,
            notice = notice(selectedBlockers, savedProjection.state.unresolvedDisplayNameCount),
            clearSelectionAction =
                if (hasSelection) UserObservedPriceSavedSelectionAction.ClearSelection else null,
            clearSelectionActionLabel = if (hasSelection) "Clear selection" else null,
            checkPrefillAction =
                if (canCheck) UserObservedPriceSavedPrefillCheckUiAction.Request else null,
            checkPrefillActionLabel = if (canCheck) "Check selected Saved pair" else null
        )
    }

    private fun guidance(status: UserObservedPriceSavedSelectionUiStatus): String =
        when (status) {
            UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION ->
                "Choose one saved product and one saved store."
            UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK ->
                "The selected Saved identities can be checked for safe observed-price prefill."
            UserObservedPriceSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE ->
                "Refresh the selected saved choice name before checking this pair."
        }

    private fun notice(selectedBlockers: Int, unresolvedTotal: Int): String? =
        when {
            selectedBlockers == 1 ->
                "1 selected saved choice needs a current display name before it can be checked."
            selectedBlockers > 1 ->
                "$selectedBlockers selected saved choices need current display names before they can be checked."
            unresolvedTotal == 1 ->
                "1 other saved choice is hidden until its display name is available."
            unresolvedTotal > 1 ->
                "$unresolvedTotal other saved choices are hidden until their display names are available."
            else -> null
        }
}

/** Replaceable renderer receives only immutable consumer state. */
internal fun interface UserObservedPriceSavedSelectionSurfaceRenderer {
    fun render(state: UserObservedPriceSavedSelectionUiState)
}

/** One-way presentation boundary. It owns no route lifecycle or downstream prefill execution. */
internal class UserObservedPriceSavedSelectionSurfacePresenter(
    private val renderer: UserObservedPriceSavedSelectionSurfaceRenderer
) {
    fun render(
        savedState: PracticalShoppingSavedExactPreferenceState,
        selection: UserObservedPriceSavedSelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ) {
        renderer.render(
            UserObservedPriceSavedSelectionUiProjector.project(
                savedState = savedState,
                selection = selection,
                metadata = metadata
            )
        )
    }
}

package com.valuepilot.app

/** Consumer navigation only; never a Saved persistence or observed-price confirmation action. */
internal sealed interface PracticalShoppingSavedObservedPriceLaunchAction {
    data object OpenObservedPriceSavedSelection : PracticalShoppingSavedObservedPriceLaunchAction
}

/**
 * Renderer-ready launch state for the Saved-owned observed-price selection entry point.
 *
 * This state carries no Saved document, exact identity, prefill payload, proof, price, draft,
 * evidence, ranking result, route state, or confirmation authority. A null action means the
 * affordance must remain unavailable.
 */
internal data class PracticalShoppingSavedObservedPriceLaunchUiState(
    val title: String?,
    val supportingText: String?,
    val notice: String?,
    val action: PracticalShoppingSavedObservedPriceLaunchAction.OpenObservedPriceSavedSelection?,
    val actionLabel: String?
) {
    private val hasContent = action != null || notice != null

    init {
        require((title != null) == hasContent)
        require((supportingText != null) == (action != null))
        require(title == null || title.isNotBlank())
        require(supportingText == null || supportingText.isNotBlank())
        require(notice == null || notice.isNotBlank())
        require(notice == null || action == null)
        require((action != null) == (actionLabel != null))
        require(actionLabel == null || actionLabel.isNotBlank())
    }
}

/**
 * Pure navigation-readiness projection for opening explicit Saved-backed observed-price selection.
 *
 * Only accepted READY/DEGRADED Saved lifecycle states may expose the action, and at least one
 * consumer-visible Saved product plus one consumer-visible Saved store must exist. Visible rows are
 * deliberately used rather than unresolved technical keys, so navigation cannot depend on labels
 * that are unavailable to the consumer.
 *
 * This projection intentionally does not inspect GTINs, invoke any observed-price prefill gate, or
 * imply that a selected pair will pass the later prefill check. It grants navigation readiness only.
 */
internal object PracticalShoppingSavedObservedPriceLaunchUiProjector {

    fun project(
        lifecycle: PracticalShoppingSavedLifecycleState
    ): PracticalShoppingSavedObservedPriceLaunchUiState {
        val accepted =
            lifecycle.status == PracticalShoppingSavedLifecycleStatus.READY ||
                lifecycle.status == PracticalShoppingSavedLifecycleStatus.DEGRADED
        val projection = lifecycle.projection
        val usable =
            accepted &&
                projection != null &&
                projection.state.productRows.isNotEmpty() &&
                projection.state.storeRows.isNotEmpty()

        return if (usable) {
            PracticalShoppingSavedObservedPriceLaunchUiState(
                title = "Confirm an observed price",
                supportingText =
                    "Choose a saved product and store, then record a price you personally " +
                        "observed with proof.",
                notice = null,
                action =
                    PracticalShoppingSavedObservedPriceLaunchAction.OpenObservedPriceSavedSelection,
                actionLabel = "Confirm an observed price"
            )
        } else if (
            accepted &&
                projection != null &&
                projection.state.emptyMessage == null
        ) {
            PracticalShoppingSavedObservedPriceLaunchUiState(
                title = "Confirm an observed price",
                supportingText = null,
                notice = unavailableNotice(
                    visibleProductCount = projection.state.productRows.size,
                    visibleStoreCount = projection.state.storeRows.size
                ),
                action = null,
                actionLabel = null
            )
        } else {
            PracticalShoppingSavedObservedPriceLaunchUiState(
                title = null,
                supportingText = null,
                notice = null,
                action = null,
                actionLabel = null
            )
        }
    }

    private fun unavailableNotice(
        visibleProductCount: Int,
        visibleStoreCount: Int
    ): String =
        when {
            visibleProductCount == 0 && visibleStoreCount == 0 ->
                "Save at least one named product and one named store to confirm an observed price."

            visibleProductCount == 0 ->
                "Save a named product to confirm an observed price."

            else ->
                "Save a named store to confirm an observed price."
        }
}

/** Narrow target for any replaceable physical Saved-owned observed-price launcher. */
internal fun interface PracticalShoppingSavedObservedPriceLaunchRenderer {
    fun render(state: PracticalShoppingSavedObservedPriceLaunchUiState)
}

/** Presentation-only adapter from Saved lifecycle state to the consumer launch renderer. */
internal class PracticalShoppingSavedObservedPriceLaunchPresenter(
    private val renderer: PracticalShoppingSavedObservedPriceLaunchRenderer
) : PracticalShoppingSavedLifecycleRenderer {

    override fun render(state: PracticalShoppingSavedLifecycleState) {
        renderer.render(PracticalShoppingSavedObservedPriceLaunchUiProjector.project(state))
    }
}

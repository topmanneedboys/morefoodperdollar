package com.valuepilot.app

/** Consumer navigation only; never a Saved persistence action. */
sealed interface PracticalShoppingSavedStapleLaunchAction {
    data object OpenStapleWatchSetup : PracticalShoppingSavedStapleLaunchAction
}

/**
 * Renderer-ready launch state for the Saved-owned Watch My Staples setup entry point.
 *
 * This state contains no Saved documents, exact identity, price/travel facts, ranking result,
 * economic decision, or notification authority. A null action means the affordance must remain
 * unavailable.
 */
data class PracticalShoppingSavedStapleLaunchUiState(
    val title: String?,
    val supportingText: String?,
    val notice: String?,
    val action: PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup?,
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
 * Pure readiness projection for opening explicit Saved-backed staple setup.
 *
 * Setup currently requires at least two selectable saved products and one selectable saved store.
 * Only accepted READY/DEGRADED lifecycle states may expose the navigation action. Visible rows are
 * deliberately used rather than unresolved technical keys so navigation cannot lead to a setup
 * that has too few consumer-selectable identities because display metadata is missing.
 *
 * This is navigation readiness only. It says nothing about current prices, store coverage,
 * travel, savings, evidence freshness, economic eligibility, or notification authorization.
 */
object PracticalShoppingSavedStapleLaunchUiProjector {

    fun project(
        lifecycle: PracticalShoppingSavedLifecycleState
    ): PracticalShoppingSavedStapleLaunchUiState {
        val accepted =
            lifecycle.status == PracticalShoppingSavedLifecycleStatus.READY ||
                lifecycle.status == PracticalShoppingSavedLifecycleStatus.DEGRADED
        val projection = lifecycle.projection
        val usable =
            accepted &&
                projection != null &&
                projection.state.productRows.size >= MIN_STAPLE_PRODUCTS &&
                projection.state.storeRows.isNotEmpty()

        return if (usable) {
            PracticalShoppingSavedStapleLaunchUiState(
                title = "Watch My Staples",
                supportingText =
                    "Choose recurring saved items and a usual store to check whether a future " +
                        "switch is worth the trip.",
                notice = null,
                action = PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup,
                actionLabel = "Choose staples to watch"
            )
        } else if (
            accepted &&
                projection != null &&
                projection.state.emptyMessage == null
        ) {
            PracticalShoppingSavedStapleLaunchUiState(
                title = "Watch My Staples",
                supportingText = null,
                notice = unavailableNotice(
                    visibleProductCount = projection.state.productRows.size,
                    visibleStoreCount = projection.state.storeRows.size
                ),
                action = null,
                actionLabel = null
            )
        } else {
            PracticalShoppingSavedStapleLaunchUiState(
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
            visibleProductCount < MIN_STAPLE_PRODUCTS && visibleStoreCount == 0 ->
                "Save at least two named products and one named store to set up Watch My Staples."

            visibleProductCount < MIN_STAPLE_PRODUCTS ->
                if (visibleProductCount == MIN_STAPLE_PRODUCTS - 1) {
                    "Save one more named product to set up Watch My Staples."
                } else {
                    "Save at least two named products to set up Watch My Staples."
                }

            else ->
                "Save a named store to set up Watch My Staples."
        }

    private const val MIN_STAPLE_PRODUCTS = 2
}

/** Narrow target for any replaceable physical Saved-owned staple launcher. */
fun interface PracticalShoppingSavedStapleLaunchRenderer {
    fun render(state: PracticalShoppingSavedStapleLaunchUiState)
}

/** Presentation-only adapter from Saved lifecycle state to the consumer launch renderer. */
class PracticalShoppingSavedStapleLaunchPresenter(
    private val renderer: PracticalShoppingSavedStapleLaunchRenderer
) : PracticalShoppingSavedLifecycleRenderer {

    override fun render(state: PracticalShoppingSavedLifecycleState) {
        renderer.render(PracticalShoppingSavedStapleLaunchUiProjector.project(state))
    }
}

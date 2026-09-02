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
    val action: PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup?,
    val actionLabel: String?
) {
    init {
        require((title != null) == (action != null))
        require((supportingText != null) == (action != null))
        require(title == null || title.isNotBlank())
        require(supportingText == null || supportingText.isNotBlank())
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
                action = PracticalShoppingSavedStapleLaunchAction.OpenStapleWatchSetup,
                actionLabel = "Choose staples to watch"
            )
        } else {
            PracticalShoppingSavedStapleLaunchUiState(
                title = null,
                supportingText = null,
                action = null,
                actionLabel = null
            )
        }
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

package com.valuepilot.app

/**
 * Why an explicit Saved-backed staple identity handoff could not be produced.
 *
 * These issues describe setup eligibility only. They do not imply anything about current prices,
 * travel burden, economic value, evidence freshness, delivery, or notification authorization.
 */
enum class StapleWatchSavedIdentityHandoffIssue {
    NOT_READY,
    SELECTED_DISPLAY_METADATA_INCOMPLETE
}

data class StapleWatchSavedIdentityHandoffAttempt(
    val handoff: StapleWatchSavedIdentityHandoff?,
    val issue: StapleWatchSavedIdentityHandoffIssue?
) {
    init {
        require((handoff != null) != (issue != null))
    }

    val accepted: Boolean
        get() = handoff != null
}

/**
 * Pure explicit gate from temporary Saved-backed setup selection to identity-only fact handoff.
 *
 * Calling [request] is the explicit continuation signal. Merely becoming selection-ready never
 * calls this gate and therefore never starts later work.
 *
 * Trust rules:
 * - The existing identity reducer remains authoritative for reconciliation and the minimum
 *   two-watched-items plus usual-store requirement.
 * - Selected identities must also remain consumer-displayable through the verified Saved
 *   projector. An unresolved selected label fails closed even when identity alone is sufficient
 *   to form a raw handoff; unresolved unselected Saved choices do not block continuation.
 * - The accepted result contains only the existing [StapleWatchSavedIdentityHandoff].
 * - This gate never resolves prices, routes, evidence freshness, store alternatives, economics,
 *   persistence, background work, delivery, or notifications.
 */
object StapleWatchSavedIdentityHandoffGate {

    fun request(
        selection: StapleWatchSavedIdentitySelection,
        snapshot: PracticalShoppingSavedValidatedSnapshot
    ): StapleWatchSavedIdentityHandoffAttempt {
        val current =
            StapleWatchSavedIdentitySelectionReducer.reconcile(
                previous = selection,
                savedState = snapshot.exactState
            )
        val handoff =
            StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(
                selection = current,
                savedState = snapshot.exactState
            ) ?: return rejected(StapleWatchSavedIdentityHandoffIssue.NOT_READY)

        val savedProjection =
            PracticalShoppingSavedExactPreferenceUiProjector.project(
                savedState = snapshot.exactState,
                metadata = snapshot.displayMetadata
            )
        val unresolvedProducts = savedProjection.unresolvedProductKeys.toSet()
        val unresolvedStores = savedProjection.unresolvedStoreKeys.toSet()
        val selectedDisplayMetadataIncomplete =
            current.watchedItemKeys.any(unresolvedProducts::contains) ||
                current.usualStoreKey?.let(unresolvedStores::contains) == true

        if (selectedDisplayMetadataIncomplete) {
            return rejected(
                StapleWatchSavedIdentityHandoffIssue.SELECTED_DISPLAY_METADATA_INCOMPLETE
            )
        }

        return StapleWatchSavedIdentityHandoffAttempt(
            handoff = handoff,
            issue = null
        )
    }

    private fun rejected(
        issue: StapleWatchSavedIdentityHandoffIssue
    ): StapleWatchSavedIdentityHandoffAttempt =
        StapleWatchSavedIdentityHandoffAttempt(
            handoff = null,
            issue = issue
        )
}

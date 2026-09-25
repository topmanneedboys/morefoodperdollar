package com.valuepilot.app

/** Why explicit Saved selection could not yet reach the verified observed-price prefill gate. */
internal enum class UserObservedPriceSavedPrefillHandoffIssue {
    SELECTION_NOT_READY
}

/**
 * Result of an explicit Saved-selection to observed-price-prefill request.
 *
 * Exactly one outcome is present:
 * - [prefill] is the unchanged accepted output of [UserObservedPriceSavedPrefillGate];
 * - [issue] is a wrapper-level selection-readiness blocker; or
 * - [prefillIssue] is the unchanged typed blocker from [UserObservedPriceSavedPrefillGate].
 *
 * This result grants no route, draft, proof, price, time, evidence, quantity, ranking, current-price,
 * persistence, Android lifecycle, or submission authority.
 */
internal data class UserObservedPriceSavedPrefillHandoffAttempt(
    val prefill: UserObservedPriceSavedPrefill?,
    val issue: UserObservedPriceSavedPrefillHandoffIssue? = null,
    val prefillIssue: UserObservedPriceSavedPrefillIssue? = null
) {
    init {
        val outcomeCount =
            listOf(
                prefill != null,
                issue != null,
                prefillIssue != null
            ).count { present -> present }
        require(outcomeCount == 1)
    }

    val accepted: Boolean
        get() = prefill != null
}

/**
 * Pure explicit handoff from temporary Saved key selection to the already-verified identity/display
 * prefill gate.
 *
 * Calling [request] is only an explicit request to derive prefill context. It does not open a route
 * or mutate an observed-price draft.
 *
 * Trust rules:
 * - [UserObservedPriceSavedSelectionReducer] remains authoritative for whether one explicitly
 *   selected item/store pair is still present in exact Saved state;
 * - an absent or reconciled-away pair fails closed as [UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY];
 * - a still-valid pair is forwarded exactly once to [UserObservedPriceSavedPrefillGate];
 * - every downstream GTIN/display/store blocker is preserved unchanged as [prefillIssue];
 * - an accepted [UserObservedPriceSavedPrefill] is returned unchanged.
 *
 * This boundary does not inspect GTINs or labels itself and owns no proof bytes, price, ids, clock,
 * storage, evidence, quantity, freshness, ranking, current-price, Android, network, UI or navigation.
 */
internal object UserObservedPriceSavedPrefillHandoffGate {

    fun request(
        selection: UserObservedPriceSavedSelection,
        snapshot: PracticalShoppingSavedValidatedSnapshot
    ): UserObservedPriceSavedPrefillHandoffAttempt {
        val pair =
            UserObservedPriceSavedSelectionReducer.selectedPairOrNull(
                selection = selection,
                savedState = snapshot.exactState
            ) ?: return selectionNotReady()

        val prefillAttempt =
            UserObservedPriceSavedPrefillGate.request(
                itemKey = pair.itemKey,
                storeKey = pair.storeKey,
                snapshot = snapshot
            )

        return prefillAttempt.prefill?.let { prefill ->
            UserObservedPriceSavedPrefillHandoffAttempt(
                prefill = prefill
            )
        } ?: UserObservedPriceSavedPrefillHandoffAttempt(
            prefill = null,
            prefillIssue = requireNotNull(prefillAttempt.issue)
        )
    }

    private fun selectionNotReady(): UserObservedPriceSavedPrefillHandoffAttempt =
        UserObservedPriceSavedPrefillHandoffAttempt(
            prefill = null,
            issue = UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY
        )
}

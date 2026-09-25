package com.valuepilot.app

/**
 * Mechanical fanout for one already-produced Saved prefill handoff attempt.
 *
 * The attempt is forwarded unchanged, in order, to the passive result surface owner and the narrow
 * confirmation-draft route owner. This object performs no eligibility check, adaptation, navigation,
 * draft mutation, proof/price work, persistence, ranking, or current-price interpretation.
 */
internal class UserObservedPriceSavedPrefillHandoffAttemptFanout(
    private val resultObserver: UserObservedPriceSavedPrefillHandoffAttemptObserver,
    private val confirmationDraftObserver: UserObservedPriceSavedPrefillHandoffAttemptObserver
) : UserObservedPriceSavedPrefillHandoffAttemptObserver {

    override fun onAttempt(attempt: UserObservedPriceSavedPrefillHandoffAttempt) {
        resultObserver.onAttempt(attempt)
        confirmationDraftObserver.onAttempt(attempt)
    }
}

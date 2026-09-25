package com.valuepilot.app

/**
 * Typed result from attempting to pass one already-read proof artifact into the verified draft
 * submission handoff.
 *
 * A content rejection preserves the exact reader issue. Submission rejection means the proof bytes
 * were readable but the existing route/target handoff declined them. Neither state upgrades the
 * proof into evidence or says anything about confirmation validity.
 */
internal sealed interface UserObservedPriceProofReadSubmissionResult {
    data object Submitted : UserObservedPriceProofReadSubmissionResult

    data class ContentRejected(
        val issue: UserObservedPriceProofContentReadIssue
    ) : UserObservedPriceProofReadSubmissionResult

    data object SubmissionRejected : UserObservedPriceProofReadSubmissionResult
}

/**
 * Platform-neutral gate between bounded proof reading and the existing draft submission handoff.
 *
 * It accepts only the typed output of [UserObservedPriceProofStreamReader] or its Android content
 * adapter. Failed reads never touch the submission handoff. Successful reads forward the same
 * transient ByteArray object unchanged; route completeness/lifecycle and downstream execution remain
 * owned by [UserObservedPriceConfirmationDraftSubmissionHandoff] and its target.
 *
 * This gate stores no proof bytes and owns no URI access, picker UI, Android lifecycle, validation,
 * hashing, persistence, identifiers, clock, evidence construction, quantity resolution, ranking, or
 * current-price authority.
 */
internal class UserObservedPriceProofReadSubmissionGate(
    private val submissionHandoff: UserObservedPriceConfirmationDraftSubmissionHandoff
) {

    fun submit(
        readResult: UserObservedPriceProofContentReadResult
    ): UserObservedPriceProofReadSubmissionResult {
        val bytes = readResult.bytes
            ?: return UserObservedPriceProofReadSubmissionResult.ContentRejected(
                issue = requireNotNull(readResult.issue)
            )

        return if (submissionHandoff.submit(bytes)) {
            UserObservedPriceProofReadSubmissionResult.Submitted
        } else {
            UserObservedPriceProofReadSubmissionResult.SubmissionRejected
        }
    }
}

package com.valuepilot.app

/**
 * Narrow runtime adapter from the explicit confirmation action boundary into the existing Android
 * confirmation execution session.
 *
 * The adapter performs no validation, parsing, proof retention, clock/ID generation, evidence
 * creation, ranking, networking, or UI work. It only unwraps the already-complete immutable draft
 * submission and forwards it, together with the action-owned defensive proof snapshot, to the
 * existing foreground Android session.
 */
internal class UserObservedPriceConfirmationAndroidSubmissionTarget(
    private val session: UserObservedPriceConfirmationAndroidSession
) : UserObservedPriceConfirmationDraftSubmissionTarget {

    override fun submit(
        submission: UserObservedPriceConfirmationDraftSubmission,
        artifactBytes: ByteArray
    ): Boolean =
        session.submit(
            artifactId = submission.artifactId,
            proofType = submission.proofType,
            artifactBytes = artifactBytes,
            fields = submission.fields
        )
}

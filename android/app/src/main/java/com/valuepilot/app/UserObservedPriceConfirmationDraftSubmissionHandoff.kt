package com.valuepilot.app

/**
 * Narrow sink for one already-complete observed-price draft plus its transient proof bytes.
 *
 * Implementations must preserve the exact submission fields and proof bytes. This contract grants
 * no authority to validate confirmation semantics, interpret proof, persist bytes, read a clock,
 * generate identifiers, create evidence, resolve quantity, rank offers, or activate UI.
 */
internal fun interface UserObservedPriceConfirmationDraftSubmissionTarget {
    fun submit(
        submission: UserObservedPriceConfirmationDraftSubmission,
        artifactBytes: ByteArray
    ): Boolean
}

/**
 * Adapter from the route's typed submission target to the existing Android execution owner.
 *
 * It only unwraps the already-complete draft submission into the Android session's existing submit
 * signature. The Android session remains the owner of main-thread enforcement, execution sequencing,
 * proof-byte snapshotting, process runtime composition, and typed completion delivery.
 */
internal class UserObservedPriceConfirmationAndroidDraftSubmissionTarget(
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

/**
 * Foreground handoff from the visible observed-price draft route to one submission target.
 *
 * Submission is allowed only when the route itself currently exposes a complete typed submission.
 * Hidden, closed, and incomplete routes therefore fail closed without touching the target. The
 * complete submission and caller-provided proof-byte object are forwarded unchanged.
 *
 * This handoff owns no lifecycle state and deliberately does not close, hide, or mutate the route.
 * It does not copy, retain, wipe, hash, inspect, or validate proof bytes; semantic validation and
 * proof retention remain downstream in the existing confirmation transaction/execution boundaries.
 */
internal class UserObservedPriceConfirmationDraftSubmissionHandoff(
    private val routeSession: UserObservedPriceConfirmationDraftRouteSession,
    private val target: UserObservedPriceConfirmationDraftSubmissionTarget
) {

    fun submit(artifactBytes: ByteArray): Boolean {
        val submission = routeSession.currentSubmissionOrNull() ?: return false
        return target.submit(
            submission = submission,
            artifactBytes = artifactBytes
        )
    }
}

package com.valuepilot.app

import java.util.UUID

/**
 * Technical metadata captured for one explicit local observed-price confirmation action.
 *
 * The pair is deliberately captured together so a later action coordinator can bind one opaque
 * confirmation-record identity to the wall-clock instant at which that same explicit action was
 * accepted. It is not an observation fact, proof, confirmation result, evidence object, or
 * current-price authority.
 */
internal data class UserObservedPriceConfirmationLifecycleMetadata(
    val confirmationId: String,
    val confirmedAtEpochMillis: Long
)

/**
 * Replaceable source for technical confirmation lifecycle metadata.
 *
 * Callers own the decision of when an explicit confirmation action has legitimately reached this
 * boundary. Implementations must not inspect or infer shopping facts, proof content, draft state,
 * observed time, or submission readiness.
 */
internal fun interface UserObservedPriceConfirmationLifecycleMetadataSource {
    fun capture(): UserObservedPriceConfirmationLifecycleMetadata
}

/**
 * Local production implementation for one explicit confirmation action.
 *
 * This object owns only collision-resistant local confirmation-record identity generation and the
 * wall-clock epoch millisecond captured for that same action. It does not validate the relationship
 * between confirmed time and user-entered observed time; the existing downstream confirmation
 * validator remains authoritative for those semantics.
 */
internal object LocalUserObservedPriceConfirmationLifecycleMetadataSource :
    UserObservedPriceConfirmationLifecycleMetadataSource {

    override fun capture(): UserObservedPriceConfirmationLifecycleMetadata =
        UserObservedPriceConfirmationLifecycleMetadata(
            confirmationId = "confirmation-${UUID.randomUUID()}",
            confirmedAtEpochMillis = System.currentTimeMillis()
        )
}

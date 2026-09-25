package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope

/**
 * Exact user-confirmed fields that are independent of the proof artifact bytes themselves.
 *
 * Every value is caller supplied. This boundary does not discover a product/store, read a clock,
 * generate identifiers, infer proof contents, or decide whether an observed price is rankable.
 */
data class UserObservedPriceConfirmationFields(
    val observationId: String,
    val rawGtin: String,
    val productName: String,
    val price: Money,
    val storeScope: PracticalShoppingStoreIdentityScope,
    val observedAtEpochMillis: Long,
    val confirmationId: String,
    val confirmedAtEpochMillis: Long
)

/**
 * Result of binding one actual proof byte sequence to one exact user confirmation and retaining it.
 *
 * Exactly one failure stage is exposed when the transaction is rejected. An accepted confirmation
 * is returned only after the proof store has accepted and read-back verified the exact bytes.
 */
data class UserObservedPriceConfirmationTransactionResult(
    val confirmation: UserConfirmedObservedPrice?,
    val artifactFailures: Set<UserProvidedPriceArtifactFailure> = emptySet(),
    val confirmationFailures: Set<UserObservedPriceConfirmationFailure> = emptySet(),
    val storageIssue: UserProvidedPriceProofArtifactStorageIssue? = null,
    val proofAlreadyRetained: Boolean = false
) {
    init {
        val failedStages =
            listOf(
                artifactFailures.isNotEmpty(),
                confirmationFailures.isNotEmpty(),
                storageIssue != null
            ).count { it }

        require((confirmation != null) == (failedStages == 0))
        require(confirmation != null || failedStages == 1)
        require(!proofAlreadyRetained || confirmation != null)
    }

    val accepted: Boolean
        get() = confirmation != null
}

/**
 * Fail-closed composition boundary for a user-provided observed-price confirmation.
 *
 * Ordering is deliberate:
 * 1. Fingerprint the actual bounded proof bytes.
 * 2. Validate the complete exact user confirmation while storage is still untouched.
 * 3. Retain and read-back verify those same bytes.
 *
 * The accepted result is therefore never exposed for an invalid confirmation or unretained proof.
 * A failed retention is left to the proof store's explicit recovery semantics; this transaction
 * does not silently delete a pre-existing corrupt digest or otherwise mutate proof history.
 *
 * This class does not capture camera/OCR input, own a clock, generate IDs, create EvidenceClaim or
 * ShoppingEvidence objects, resolve package quantity, decide freshness/ranking, upgrade to
 * CURRENT_PRICE, or activate any UI surface. Those remain separate explicit boundaries.
 */
class UserObservedPriceConfirmationTransaction(
    private val proofStore: UserProvidedPriceProofArtifactLocalStore
) {

    fun confirmAndRetain(
        artifactId: String,
        proofType: UserProvidedPriceProofType,
        artifactBytes: ByteArray,
        fields: UserObservedPriceConfirmationFields
    ): UserObservedPriceConfirmationTransactionResult {
        val artifactResult =
            UserProvidedPriceProofArtifact.fingerprint(
                artifactId = artifactId,
                proofType = proofType,
                artifactBytes = artifactBytes
            )
        val artifact =
            artifactResult.artifact
                ?: return UserObservedPriceConfirmationTransactionResult(
                    confirmation = null,
                    artifactFailures = artifactResult.failures
                )

        val confirmationResult =
            UserConfirmedObservedPrice.confirm(
                UserObservedPriceConfirmationInput(
                    artifact = artifact,
                    observationId = fields.observationId,
                    rawGtin = fields.rawGtin,
                    productName = fields.productName,
                    price = fields.price,
                    storeScope = fields.storeScope,
                    observedAtEpochMillis = fields.observedAtEpochMillis,
                    confirmationId = fields.confirmationId,
                    confirmedAtEpochMillis = fields.confirmedAtEpochMillis
                )
            )
        val confirmation =
            confirmationResult.confirmation
                ?: return UserObservedPriceConfirmationTransactionResult(
                    confirmation = null,
                    confirmationFailures = confirmationResult.failures
                )

        val retention = proofStore.retain(artifact, artifactBytes)
        if (!retention.accepted) {
            return UserObservedPriceConfirmationTransactionResult(
                confirmation = null,
                storageIssue = requireNotNull(retention.issue)
            )
        }

        return UserObservedPriceConfirmationTransactionResult(
            confirmation = confirmation,
            proofAlreadyRetained = retention.alreadyRetained
        )
    }
}

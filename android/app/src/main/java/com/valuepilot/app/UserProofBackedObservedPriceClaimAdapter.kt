package com.valuepilot.app

import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceFingerprints
import java.security.MessageDigest

/** Why a confirmed user observation could not be read as proof-backed observed-price evidence. */
enum class UserProofBackedObservedPriceClaimFailure {
    PROOF_NOT_RETAINED,
    PROOF_VERIFICATION_FAILED
}

data class UserProofBackedObservedPriceClaimResult(
    val claim: EvidenceClaim?,
    val failure: UserProofBackedObservedPriceClaimFailure?,
    val storageIssue: UserProvidedPriceProofArtifactStorageIssue? = null
) {
    init {
        require((claim != null) == (failure == null))
        require(storageIssue == null || failure == UserProofBackedObservedPriceClaimFailure.PROOF_VERIFICATION_FAILED)
        require(failure != UserProofBackedObservedPriceClaimFailure.PROOF_NOT_RETAINED || storageIssue == null)
    }

    val accepted: Boolean
        get() = claim != null
}

/**
 * Re-verifying bridge from one exact user confirmation to one observed-price factual claim.
 *
 * This boundary deliberately emits no ShoppingEvidence or offer object. The retained proof is
 * re-read and re-fingerprinted on every call before proof-backed authority is exposed. Deleting,
 * corrupting, or otherwise making the retained artifact unverifiable therefore closes this path
 * on the next read.
 *
 * Product identity comes only from the confirmation's checksum-valid cross-source GTIN key.
 * Merchant/location/channel scope comes only from the already-exact confirmed store scope.
 * Price arithmetic is not recomputed: the exact Money value is fingerprinted by shared core.
 */
class UserProofBackedObservedPriceClaimAdapter(
    private val proofStore: UserProvidedPriceProofArtifactLocalStore
) {

    fun read(
        confirmation: UserConfirmedObservedPrice
    ): UserProofBackedObservedPriceClaimResult {
        val verification = proofStore.verify(confirmation.artifact)
        if (!verification.verified) {
            return if (!verification.foundStoredArtifact && verification.issue == null) {
                UserProofBackedObservedPriceClaimResult(
                    claim = null,
                    failure = UserProofBackedObservedPriceClaimFailure.PROOF_NOT_RETAINED
                )
            } else {
                UserProofBackedObservedPriceClaimResult(
                    claim = null,
                    failure = UserProofBackedObservedPriceClaimFailure.PROOF_VERIFICATION_FAILED,
                    storageIssue = verification.issue
                )
            }
        }

        val priceFingerprint = EvidenceFingerprints.money(confirmation.price)
        val scope = confirmation.storeScope

        return UserProofBackedObservedPriceClaimResult(
            claim =
                EvidenceClaim(
                    claimId = claimId(confirmation, priceFingerprint),
                    domain = EvidenceClaimDomain.OBSERVED_PRICE,
                    valueFingerprint = priceFingerprint,
                    authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
                    scope =
                        EvidenceClaimScope(
                            productKey = confirmation.productKey.value,
                            merchantKey = scope.merchantKey,
                            locationKey = scope.locationKey,
                            commerceChannelKey = scope.commerceChannelKey,
                            currencyCode = confirmation.price.currencyCode
                        ),
                    observedAtEpochMillis = confirmation.observedAtEpochMillis
                ),
            failure = null
        )
    }

    private fun claimId(
        confirmation: UserConfirmedObservedPrice,
        priceFingerprint: String
    ): String {
        val artifact = confirmation.artifact
        val scope = confirmation.storeScope
        val material =
            listOf(
                component(artifact.artifactId),
                component(artifact.proofType.name),
                component(artifact.sha256),
                component(artifact.byteLength.toString()),
                component(confirmation.observationId),
                component(confirmation.confirmationId),
                component(confirmation.gtin),
                component(confirmation.productKey.value),
                component(scope.merchantKey),
                nullableComponent(scope.locationKey),
                component(scope.commerceChannelKey),
                component(confirmation.price.currencyCode),
                component(priceFingerprint),
                component(confirmation.observedAtEpochMillis.toString()),
                component(confirmation.confirmedAtEpochMillis.toString())
            ).joinToString(separator = "|")

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(material.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }

        return "user-proof-observed-price:$digest"
    }

    private fun component(value: String): String =
        "${value.length}:$value"

    private fun nullableComponent(value: String?): String =
        value?.let(::component) ?: "-1:"
}

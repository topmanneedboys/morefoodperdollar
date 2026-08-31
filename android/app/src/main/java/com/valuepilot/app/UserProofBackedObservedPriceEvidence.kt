package com.valuepilot.app

import com.valuepilot.core.AvailabilityEvidence
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import com.valuepilot.core.ShoppingEvidence
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import com.valuepilot.core.SourceProductIdentity

/** Why an exact user-confirmed observed price could not be promoted from retained proof. */
enum class UserProofBackedObservedPricePromotionFailure {
    PROOF_NOT_RETAINED,
    PROOF_READ_FAILED,
    PROOF_INVALID
}

data class UserProofBackedObservedPricePromotionResult(
    val promoted: UserProofBackedObservedPriceEvidence?,
    val failure: UserProofBackedObservedPricePromotionFailure?
) {
    init {
        require((promoted != null) == (failure == null))
    }

    val accepted: Boolean
        get() = promoted != null
}

/**
 * One ephemeral, freshly proof-verified historical price observation.
 *
 * The exact [confirmation] remains attached so the generic ShoppingEvidence/EvidenceClaim pair is
 * never the only surviving provenance inside this adapter's output. The pair is intentionally an
 * OBSERVED_PRICE fact only. It is not a merchant current offer, availability assertion, promotion,
 * package quantity, Watch fact, or production authorization token.
 *
 * Promotion is deliberately re-evaluated from the app-private proof store every time. Callers must
 * not persist/reuse this object as durable authorization: a later decision should call [promote]
 * again so deleted, corrupt, or unreadable proof fails closed at that later boundary.
 */
class UserProofBackedObservedPriceEvidence private constructor(
    val confirmation: UserConfirmedObservedPrice,
    val evidence: ShoppingEvidence,
    val priceClaim: EvidenceClaim
) {
    companion object {
        private val provider =
            EvidenceProvider(
                id = EvidenceProviderId("local-user-price-proof"),
                displayName = "User-provided price proof"
            )

        private val source =
            ShoppingSource(
                id = ShoppingSourceId("local-user-price-proof"),
                displayName = "User-provided proof"
            )

        fun promote(
            confirmation: UserConfirmedObservedPrice,
            proofStore: UserProvidedPriceProofArtifactLocalStore
        ): UserProofBackedObservedPricePromotionResult {
            val verification = proofStore.verify(confirmation.artifact)
            if (!verification.verified) {
                return UserProofBackedObservedPricePromotionResult(
                    promoted = null,
                    failure = verification.toPromotionFailure()
                )
            }

            if (verification.artifact !== confirmation.artifact) {
                return UserProofBackedObservedPricePromotionResult(
                    promoted = null,
                    failure = UserProofBackedObservedPricePromotionFailure.PROOF_INVALID
                )
            }

            val evidence =
                ShoppingEvidence(
                    observation =
                        ProductObservation(
                            id =
                                ProductObservationId(
                                    "local-user-price:${confirmation.observationId}"
                                ),
                            sourceId = source.id.value,
                            rawText =
                                buildString {
                                    append(confirmation.productName)
                                    append('\n')
                                    append(EvidenceFingerprints.money(confirmation.price))
                                },
                            observedAtEpochMillis = confirmation.observedAtEpochMillis
                        ),
                    provider = provider,
                    source = source,
                    environment = EvidenceEnvironment.REAL_WORLD,
                    channel = EvidenceChannel.USER_PROVIDED,
                    observationClaimKind = EvidenceClaimKind.DIRECT_OBSERVATION,
                    sourceProductIdentity = SourceProductIdentity(gtin = confirmation.gtin),
                    availability = AvailabilityEvidence()
                )

            val priceClaim =
                EvidenceClaim(
                    claimId =
                        "local-user-price-proof:${confirmation.confirmationId}:" +
                            "${confirmation.artifact.sha256}:observed-price",
                    domain = EvidenceClaimDomain.OBSERVED_PRICE,
                    valueFingerprint = EvidenceFingerprints.money(confirmation.price),
                    authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
                    scope =
                        EvidenceClaimScope(
                            productKey = confirmation.productKey.value,
                            merchantKey = confirmation.storeScope.merchantKey,
                            locationKey = confirmation.storeScope.locationKey,
                            commerceChannelKey = confirmation.storeScope.commerceChannelKey,
                            currencyCode = confirmation.price.currencyCode
                        ),
                    observedAtEpochMillis = confirmation.observedAtEpochMillis
                )

            return UserProofBackedObservedPricePromotionResult(
                promoted =
                    UserProofBackedObservedPriceEvidence(
                        confirmation = confirmation,
                        evidence = evidence,
                        priceClaim = priceClaim
                    ),
                failure = null
            )
        }

        private fun UserProvidedPriceProofArtifactVerificationResult.toPromotionFailure():
            UserProofBackedObservedPricePromotionFailure =
            when {
                !foundStoredArtifact && issue == null ->
                    UserProofBackedObservedPricePromotionFailure.PROOF_NOT_RETAINED

                issue == UserProvidedPriceProofArtifactStorageIssue.READ_FAILED ->
                    UserProofBackedObservedPricePromotionFailure.PROOF_READ_FAILED

                else ->
                    UserProofBackedObservedPricePromotionFailure.PROOF_INVALID
            }
    }
}

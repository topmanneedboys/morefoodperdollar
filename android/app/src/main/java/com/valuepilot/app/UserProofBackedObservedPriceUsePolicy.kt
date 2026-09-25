package com.valuepilot.app

import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceDisposition
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.EvidenceFreshnessEvaluator
import com.valuepilot.core.EvidenceFreshnessPolicy

/** Explicit reason for the point-in-time use decision of one proof-backed observed price. */
enum class UserObservedPriceUseReason {
    VERIFIED_FRESH_PRICE_TAG,
    RECEIPT_HISTORICAL_ONLY,
    PRICE_TAG_AGING,
    PRICE_TAG_STALE,
    PRICE_TAG_UNKNOWN_FRESHNESS,
    FUTURE_DATED,
    PROOF_NOT_RETAINED,
    PROOF_VERIFICATION_FAILED
}

/**
 * Point-in-time result after proof re-verification and explicit caller-supplied freshness policy.
 *
 * This result is not a durable authorization token. Callers must re-evaluate when proof retention,
 * evaluation time, or freshness policy may have changed. The underlying claim remains OBSERVED_PRICE;
 * this policy never upgrades it to merchant-authoritative CURRENT_PRICE evidence.
 */
data class UserProofBackedObservedPriceUseResult(
    val claim: EvidenceClaim?,
    val proofType: UserProvidedPriceProofType,
    val disposition: EvidenceDisposition,
    val freshness: EvidenceFreshness?,
    val reason: UserObservedPriceUseReason,
    val claimFailure: UserProofBackedObservedPriceClaimFailure? = null,
    val storageIssue: UserProvidedPriceProofArtifactStorageIssue? = null
) {
    init {
        require((claim != null) == (claimFailure == null))
        require(storageIssue == null || claimFailure == UserProofBackedObservedPriceClaimFailure.PROOF_VERIFICATION_FAILED)

        if (claim == null) {
            require(disposition == EvidenceDisposition.REJECTED)
            require(freshness == null)
            require(
                reason == UserObservedPriceUseReason.PROOF_NOT_RETAINED ||
                    reason == UserObservedPriceUseReason.PROOF_VERIFICATION_FAILED
            )
        } else {
            require(claim.domain == EvidenceClaimDomain.OBSERVED_PRICE)
            require(claim.authority == EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION)
            require(freshness != null)
            require(claimFailure == null)
            require(storageIssue == null)

            if (disposition == EvidenceDisposition.RANKABLE) {
                require(proofType == UserProvidedPriceProofType.PRICE_TAG)
                require(freshness == EvidenceFreshness.FRESH)
                require(reason == UserObservedPriceUseReason.VERIFIED_FRESH_PRICE_TAG)
            }

            if (proofType == UserProvidedPriceProofType.RECEIPT) {
                require(disposition != EvidenceDisposition.RANKABLE)
            }
        }
    }

    val rankable: Boolean
        get() = disposition == EvidenceDisposition.RANKABLE

    val displayable: Boolean
        get() = disposition != EvidenceDisposition.REJECTED
}

/**
 * Application policy for deciding whether retained user proof may contribute an observed price to
 * deterministic value math at one explicit instant.
 *
 * The proof-backed claim bridge is re-read first, so deleted/corrupt/unverifiable proof fails closed
 * before any rankability decision. Freshness is then classified only by the shared deterministic
 * evaluator using the caller-supplied instant and caller-supplied policy; this class owns no clock
 * and invents no universal freshness threshold.
 *
 * Permanent proof-type rule:
 * - RECEIPT is historical/display-only even while fresh;
 * - PRICE_TAG is potentially rankable only while FRESH;
 * - AGING, STALE, and UNKNOWN price-tag freshness remain display-only;
 * - FUTURE_DATED evidence is rejected.
 *
 * This boundary does not decide availability, promotion, current-price authority, factual conflict,
 * package quantity, or final Best Value eligibility.
 */
class UserProofBackedObservedPriceUsePolicy(
    private val claimAdapter: UserProofBackedObservedPriceClaimAdapter
) {

    fun evaluate(
        confirmation: UserConfirmedObservedPrice,
        evaluatedAtEpochMillis: Long,
        freshnessPolicy: EvidenceFreshnessPolicy
    ): UserProofBackedObservedPriceUseResult {
        val claimResult = claimAdapter.read(confirmation)
        val proofType = confirmation.artifact.proofType

        if (!claimResult.accepted) {
            val failure = requireNotNull(claimResult.failure)
            return UserProofBackedObservedPriceUseResult(
                claim = null,
                proofType = proofType,
                disposition = EvidenceDisposition.REJECTED,
                freshness = null,
                reason =
                    when (failure) {
                        UserProofBackedObservedPriceClaimFailure.PROOF_NOT_RETAINED ->
                            UserObservedPriceUseReason.PROOF_NOT_RETAINED

                        UserProofBackedObservedPriceClaimFailure.PROOF_VERIFICATION_FAILED ->
                            UserObservedPriceUseReason.PROOF_VERIFICATION_FAILED
                    },
                claimFailure = failure,
                storageIssue = claimResult.storageIssue
            )
        }

        val claim = requireNotNull(claimResult.claim)
        val freshness =
            EvidenceFreshnessEvaluator.classify(
                observedAtEpochMillis = claim.observedAtEpochMillis,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                policy = freshnessPolicy
            )

        val decision = decide(proofType, freshness)

        return UserProofBackedObservedPriceUseResult(
            claim = claim,
            proofType = proofType,
            disposition = decision.first,
            freshness = freshness,
            reason = decision.second
        )
    }

    private fun decide(
        proofType: UserProvidedPriceProofType,
        freshness: EvidenceFreshness
    ): Pair<EvidenceDisposition, UserObservedPriceUseReason> =
        when {
            freshness == EvidenceFreshness.FUTURE_DATED ->
                EvidenceDisposition.REJECTED to UserObservedPriceUseReason.FUTURE_DATED

            proofType == UserProvidedPriceProofType.RECEIPT ->
                EvidenceDisposition.DISPLAY_ONLY to UserObservedPriceUseReason.RECEIPT_HISTORICAL_ONLY

            freshness == EvidenceFreshness.FRESH ->
                EvidenceDisposition.RANKABLE to UserObservedPriceUseReason.VERIFIED_FRESH_PRICE_TAG

            freshness == EvidenceFreshness.AGING ->
                EvidenceDisposition.DISPLAY_ONLY to UserObservedPriceUseReason.PRICE_TAG_AGING

            freshness == EvidenceFreshness.STALE ->
                EvidenceDisposition.DISPLAY_ONLY to UserObservedPriceUseReason.PRICE_TAG_STALE

            else ->
                EvidenceDisposition.DISPLAY_ONLY to UserObservedPriceUseReason.PRICE_TAG_UNKNOWN_FRESHNESS
        }
}

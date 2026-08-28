package com.valuepilot.core

/**
 * What factual claim a source is making.
 *
 * Claim domains are deliberately separated so unlike facts cannot overwrite
 * each other. In particular, an observed shelf/receipt price is not the same
 * claim as a merchant's current offer, and a market benchmark is not an offer.
 */
enum class EvidenceClaimDomain {
    CURRENT_PRICE,
    OBSERVED_PRICE,
    PACKAGE_QUANTITY,
    PRODUCT_IDENTITY,
    INGREDIENTS,
    NUTRITION,
    AVAILABILITY,
    PROMOTION,
    MARKET_BENCHMARK,
    REGULATORY_FACT
}

/**
 * Authority class of one claim, independent of provider economics.
 *
 * This is not a ranking boost for a merchant or affiliate network. It only
 * controls whether one conflicting factual assertion may replace another.
 */
enum class EvidenceAuthorityClass {
    MERCHANT_AUTHORITATIVE,
    PROOF_BACKED_DIRECT_OBSERVATION,
    GOVERNMENT_RECORD,
    SOURCE_ASSERTED_METADATA,
    USER_ASSERTED,
    INFERRED,
    UNKNOWN
}

/**
 * Scope of a factual claim.
 *
 * productKey should be a stable cross-source identity when available (for
 * example a validated GTIN). Offer-specific fields are nullable because some
 * sources do not know them. Unknown scope is never silently treated as equal
 * to a more specific scope.
 */
data class EvidenceClaimScope(
    val productKey: String,
    val merchantKey: String? = null,
    val locationKey: String? = null,
    val commerceChannelKey: String? = null,
    val currencyCode: String? = null
) {
    init {
        require(productKey.isNotBlank())
        require(currencyCode == null || currencyCode.matches(Regex("[A-Z]{3}")))
    }
}

/**
 * Provider-neutral factual assertion used only for deterministic conflict
 * handling. valueFingerprint is a canonical representation produced by the
 * adapter/domain layer; this class never parses or invents values.
 */
data class EvidenceClaim(
    val claimId: String,
    val domain: EvidenceClaimDomain,
    val valueFingerprint: String,
    val authority: EvidenceAuthorityClass,
    val scope: EvidenceClaimScope,
    val observedAtEpochMillis: Long = 0L
) {
    init {
        require(claimId.isNotBlank())
        require(valueFingerprint.isNotBlank())
        require(observedAtEpochMillis >= 0L)
    }
}

enum class EvidenceConflictRelationship {
    SAME_CLAIM,
    COEXISTS,
    PREFER_LEFT,
    PREFER_RIGHT,
    UNRESOLVED_CONFLICT,
    INCOMPARABLE
}

data class EvidenceConflictDecision(
    val relationship: EvidenceConflictRelationship,
    val selectedClaimId: String?,
    val blocksRanking: Boolean,
    val reason: String
)

/**
 * Fail-closed cross-source conflict policy.
 *
 * Permanent rules:
 * - different products are incomparable;
 * - different fact domains coexist rather than overwrite each other;
 * - current offers never overwrite historical/direct observations and vice versa;
 * - market benchmarks and regulatory/reference facts never become retailer offers;
 * - prices from different merchants, stores, channels, or currencies coexist;
 * - weaker authority never wins merely because its timestamp is newer;
 * - among equal-authority claims for the exact same scoped time-sensitive fact,
 *   a later observation can supersede an older one while the older record
 *   remains provenance/history;
 * - unresolved equal-scope conflicts block Best Value instead of guessing;
 * - commission, EPC, payout, affiliate status, or provider preference are never inputs.
 */
object EvidenceConflictPolicy {

    fun resolve(
        left: EvidenceClaim,
        right: EvidenceClaim
    ): EvidenceConflictDecision {

        if (left.scope.productKey != right.scope.productKey) {
            return decision(
                EvidenceConflictRelationship.INCOMPARABLE,
                null,
                false,
                "different products"
            )
        }

        if (left.domain != right.domain) {
            return decision(
                EvidenceConflictRelationship.COEXISTS,
                null,
                false,
                "different factual claim domains"
            )
        }

        if (left.valueFingerprint == right.valueFingerprint) {
            return decision(
                EvidenceConflictRelationship.SAME_CLAIM,
                preferredDuplicate(left, right),
                false,
                "same canonical factual value"
            )
        }

        if (isOfferScoped(left.domain) && !sameOfferScope(left.scope, right.scope)) {
            return decision(
                EvidenceConflictRelationship.COEXISTS,
                null,
                false,
                "different merchant, location, channel, or currency scope"
            )
        }

        val leftAuthority = authorityScore(left.domain, left.authority)
        val rightAuthority = authorityScore(right.domain, right.authority)

        if (leftAuthority > rightAuthority) {
            return decision(
                EvidenceConflictRelationship.PREFER_LEFT,
                left.claimId,
                false,
                "stronger authority for the same factual claim"
            )
        }

        if (rightAuthority > leftAuthority) {
            return decision(
                EvidenceConflictRelationship.PREFER_RIGHT,
                right.claimId,
                false,
                "stronger authority for the same factual claim"
            )
        }

        val newer = newerClaim(left, right)
        if (newer != null && isTimeSensitive(left.domain)) {
            return if (newer.claimId == left.claimId) {
                decision(
                    EvidenceConflictRelationship.PREFER_LEFT,
                    left.claimId,
                    false,
                    "newer equal-authority observation for the same scoped fact"
                )
            } else {
                decision(
                    EvidenceConflictRelationship.PREFER_RIGHT,
                    right.claimId,
                    false,
                    "newer equal-authority observation for the same scoped fact"
                )
            }
        }

        return decision(
            EvidenceConflictRelationship.UNRESOLVED_CONFLICT,
            null,
            true,
            "equal-scope sources disagree; ValuePilot must not guess"
        )
    }

    private fun preferredDuplicate(
        left: EvidenceClaim,
        right: EvidenceClaim
    ): String {
        val leftAuthority = authorityScore(left.domain, left.authority)
        val rightAuthority = authorityScore(right.domain, right.authority)

        if (leftAuthority > rightAuthority) return left.claimId
        if (rightAuthority > leftAuthority) return right.claimId

        val newer = newerClaim(left, right)
        if (newer != null) return newer.claimId

        return minOf(left.claimId, right.claimId)
    }

    private fun newerClaim(
        left: EvidenceClaim,
        right: EvidenceClaim
    ): EvidenceClaim? {
        if (
            left.observedAtEpochMillis <= 0L ||
            right.observedAtEpochMillis <= 0L ||
            left.observedAtEpochMillis == right.observedAtEpochMillis
        ) {
            return null
        }

        return if (
            left.observedAtEpochMillis > right.observedAtEpochMillis
        ) {
            left
        } else {
            right
        }
    }

    private fun sameOfferScope(
        left: EvidenceClaimScope,
        right: EvidenceClaimScope
    ): Boolean =
        left.productKey == right.productKey &&
            left.merchantKey == right.merchantKey &&
            left.locationKey == right.locationKey &&
            left.commerceChannelKey == right.commerceChannelKey &&
            left.currencyCode == right.currencyCode

    private fun isOfferScoped(
        domain: EvidenceClaimDomain
    ): Boolean =
        domain == EvidenceClaimDomain.CURRENT_PRICE ||
            domain == EvidenceClaimDomain.OBSERVED_PRICE ||
            domain == EvidenceClaimDomain.AVAILABILITY ||
            domain == EvidenceClaimDomain.PROMOTION

    private fun isTimeSensitive(
        domain: EvidenceClaimDomain
    ): Boolean =
        domain == EvidenceClaimDomain.CURRENT_PRICE ||
            domain == EvidenceClaimDomain.OBSERVED_PRICE ||
            domain == EvidenceClaimDomain.AVAILABILITY ||
            domain == EvidenceClaimDomain.PROMOTION

    private fun authorityScore(
        domain: EvidenceClaimDomain,
        authority: EvidenceAuthorityClass
    ): Int =
        when (domain) {
            EvidenceClaimDomain.CURRENT_PRICE,
            EvidenceClaimDomain.AVAILABILITY,
            EvidenceClaimDomain.PROMOTION ->
                when (authority) {
                    EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE -> 100
                    EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION -> 80
                    EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA -> 65
                    EvidenceAuthorityClass.USER_ASSERTED -> 45
                    EvidenceAuthorityClass.GOVERNMENT_RECORD -> 30
                    EvidenceAuthorityClass.INFERRED -> 10
                    EvidenceAuthorityClass.UNKNOWN -> 0
                }

            EvidenceClaimDomain.OBSERVED_PRICE ->
                when (authority) {
                    EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION -> 100
                    EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE -> 90
                    EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA -> 60
                    EvidenceAuthorityClass.USER_ASSERTED -> 45
                    EvidenceAuthorityClass.GOVERNMENT_RECORD -> 30
                    EvidenceAuthorityClass.INFERRED -> 10
                    EvidenceAuthorityClass.UNKNOWN -> 0
                }

            EvidenceClaimDomain.REGULATORY_FACT,
            EvidenceClaimDomain.MARKET_BENCHMARK ->
                when (authority) {
                    EvidenceAuthorityClass.GOVERNMENT_RECORD -> 100
                    EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE -> 70
                    EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA -> 60
                    EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION -> 50
                    EvidenceAuthorityClass.USER_ASSERTED -> 35
                    EvidenceAuthorityClass.INFERRED -> 10
                    EvidenceAuthorityClass.UNKNOWN -> 0
                }

            EvidenceClaimDomain.PACKAGE_QUANTITY,
            EvidenceClaimDomain.PRODUCT_IDENTITY,
            EvidenceClaimDomain.INGREDIENTS,
            EvidenceClaimDomain.NUTRITION ->
                when (authority) {
                    EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE -> 100
                    EvidenceAuthorityClass.GOVERNMENT_RECORD -> 90
                    EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA -> 70
                    EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION -> 60
                    EvidenceAuthorityClass.USER_ASSERTED -> 40
                    EvidenceAuthorityClass.INFERRED -> 10
                    EvidenceAuthorityClass.UNKNOWN -> 0
                }
        }

    private fun decision(
        relationship: EvidenceConflictRelationship,
        selectedClaimId: String?,
        blocksRanking: Boolean,
        reason: String
    ) =
        EvidenceConflictDecision(
            relationship = relationship,
            selectedClaimId = selectedClaimId,
            blocksRanking = blocksRanking,
            reason = reason
        )
}

package com.valuepilot.core

/**
 * Inputs required to calculate a unit-value rate from independently sourced
 * price and package-quantity evidence without collapsing their provenance.
 */
data class EvidenceBackedUnitValueInput(
    val priceClaim: EvidenceClaim,
    val quantityClaim: EvidenceClaim,
    val offer: Offer,
    val quantity: NormalizedQuantity,
    val priceDisposition: EvidenceDisposition,
    val useMemberPrice: Boolean = false
)

enum class EvidenceBackedUnitValueBlockReason {
    PRICE_NOT_RANKABLE,
    UNSUPPORTED_PRICE_DOMAIN,
    INVALID_QUANTITY_DOMAIN,
    PRODUCT_IDENTITY_MISMATCH,
    PRICE_VALUE_MISMATCH,
    QUANTITY_VALUE_MISMATCH,
    WEAK_QUANTITY_AUTHORITY
}

data class EvidenceBackedUnitValueResult(
    val rate: UnitRate?,
    val blockReasons: Set<EvidenceBackedUnitValueBlockReason>
) {
    init {
        require((rate != null) == blockReasons.isEmpty())
    }

    val rankable: Boolean
        get() = rate != null
}

/**
 * Fail-closed boundary for cross-source unit-value math.
 *
 * A price observation and package quantity may come from different providers,
 * but they can participate in one calculation only when:
 * - the price evidence is independently eligible for ranking;
 * - both claims refer to the exact same stable product key;
 * - claim domains match their supplied values;
 * - exact canonical fingerprints match the supplied Money/quantity objects;
 * - quantity authority is strong enough for deterministic comparison.
 *
 * This class never chooses among conflicting claims. Conflict resolution must
 * happen before the selected quantity claim reaches this boundary.
 */
object EvidenceBackedUnitValuePolicy {

    fun evaluate(
        input: EvidenceBackedUnitValueInput
    ): EvidenceBackedUnitValueResult {
        val failures = linkedSetOf<EvidenceBackedUnitValueBlockReason>()

        if (input.priceDisposition != EvidenceDisposition.RANKABLE) {
            failures += EvidenceBackedUnitValueBlockReason.PRICE_NOT_RANKABLE
        }

        if (
            input.priceClaim.domain != EvidenceClaimDomain.CURRENT_PRICE &&
            input.priceClaim.domain != EvidenceClaimDomain.OBSERVED_PRICE
        ) {
            failures += EvidenceBackedUnitValueBlockReason.UNSUPPORTED_PRICE_DOMAIN
        }

        if (input.quantityClaim.domain != EvidenceClaimDomain.PACKAGE_QUANTITY) {
            failures += EvidenceBackedUnitValueBlockReason.INVALID_QUANTITY_DOMAIN
        }

        if (input.priceClaim.scope.productKey != input.quantityClaim.scope.productKey) {
            failures += EvidenceBackedUnitValueBlockReason.PRODUCT_IDENTITY_MISMATCH
        }

        val selectedPrice =
            if (input.useMemberPrice) {
                input.offer.member ?: input.offer.current
            } else {
                input.offer.current
            }

        if (
            input.priceClaim.valueFingerprint !=
            EvidenceFingerprints.money(selectedPrice)
        ) {
            failures += EvidenceBackedUnitValueBlockReason.PRICE_VALUE_MISMATCH
        }

        if (
            input.quantityClaim.valueFingerprint !=
            EvidenceFingerprints.quantity(input.quantity)
        ) {
            failures += EvidenceBackedUnitValueBlockReason.QUANTITY_VALUE_MISMATCH
        }

        if (!isStrongQuantityAuthority(input.quantityClaim.authority)) {
            failures += EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY
        }

        if (failures.isNotEmpty()) {
            return EvidenceBackedUnitValueResult(
                rate = null,
                blockReasons = failures
            )
        }

        return EvidenceBackedUnitValueResult(
            rate =
                DeterministicValueMath.pricePerBaseUnit(
                    offer = input.offer,
                    quantity = input.quantity,
                    useMemberPrice = input.useMemberPrice
                ),
            blockReasons = emptySet()
        )
    }

    private fun isStrongQuantityAuthority(
        authority: EvidenceAuthorityClass
    ): Boolean =
        authority == EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE ||
            authority == EvidenceAuthorityClass.GOVERNMENT_RECORD ||
            authority == EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA ||
            authority == EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION
}

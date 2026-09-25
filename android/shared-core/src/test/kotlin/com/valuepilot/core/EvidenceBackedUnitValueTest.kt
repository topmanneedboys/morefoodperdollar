package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceBackedUnitValueTest {

    @Test
    fun rankableProofPriceAndSourceMetadataQuantityCanCombineByExactGtin() {
        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = NormalizedQuantity(
            amountMicros = 1_000_000_000L,
            unit = BaseUnit.GRAM
        )

        val result = EvidenceBackedUnitValuePolicy.evaluate(
            EvidenceBackedUnitValueInput(
                priceClaim = observedPriceClaim(offer.current),
                quantityClaim = quantityClaim(quantity),
                offer = offer,
                quantity = quantity,
                priceDisposition = EvidenceDisposition.RANKABLE
            )
        )

        assertTrue(result.rankable)
        assertTrue(result.blockReasons.isEmpty())
        assertEquals("CAD", result.rate?.currencyCode)
        assertEquals(RateUnit.KILOGRAM, result.rate?.unit)
        assertEquals(4_990_000L, result.rate?.currencyMicrosPerUnit)
    }

    @Test
    fun staleOrDisplayOnlyPriceCannotGainRankabilityFromFreshMetadata() {
        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = QuantityNormalization.grams(1000)

        val result = EvidenceBackedUnitValuePolicy.evaluate(
            EvidenceBackedUnitValueInput(
                priceClaim = observedPriceClaim(offer.current),
                quantityClaim = quantityClaim(quantity),
                offer = offer,
                quantity = quantity,
                priceDisposition = EvidenceDisposition.DISPLAY_ONLY
            )
        )

        assertFalse(result.rankable)
        assertNull(result.rate)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.PRICE_NOT_RANKABLE in
                result.blockReasons
        )
    }

    @Test
    fun differentGtinsCannotBeJoinedEvenWhenValuesLookPlausible() {
        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = QuantityNormalization.grams(1000)
        val wrongProductQuantity = quantityClaim(quantity).copy(
            scope = EvidenceClaimScope(productKey = "gtin:4006381333931")
        )

        val result = EvidenceBackedUnitValuePolicy.evaluate(
            EvidenceBackedUnitValueInput(
                priceClaim = observedPriceClaim(offer.current),
                quantityClaim = wrongProductQuantity,
                offer = offer,
                quantity = quantity,
                priceDisposition = EvidenceDisposition.RANKABLE
            )
        )

        assertFalse(result.rankable)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.PRODUCT_IDENTITY_MISMATCH in
                result.blockReasons
        )
    }

    @Test
    fun claimFingerprintsMustMatchExactMoneyAndQuantityObjects() {
        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = QuantityNormalization.grams(1000)

        val result = EvidenceBackedUnitValuePolicy.evaluate(
            EvidenceBackedUnitValueInput(
                priceClaim = observedPriceClaim(offer.current).copy(
                    valueFingerprint = EvidenceFingerprints.money(
                        Money.parse("5.99", "CAD")
                    )
                ),
                quantityClaim = quantityClaim(quantity).copy(
                    valueFingerprint = EvidenceFingerprints.quantity(
                        QuantityNormalization.grams(900)
                    )
                ),
                offer = offer,
                quantity = quantity,
                priceDisposition = EvidenceDisposition.RANKABLE
            )
        )

        assertFalse(result.rankable)
        assertTrue(EvidenceBackedUnitValueBlockReason.PRICE_VALUE_MISMATCH in result.blockReasons)
        assertTrue(EvidenceBackedUnitValueBlockReason.QUANTITY_VALUE_MISMATCH in result.blockReasons)
    }

    @Test
    fun userAssertedPackageQuantityDoesNotDriveBestValueWithoutStrongerEvidence() {
        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = QuantityNormalization.grams(1000)

        val result = EvidenceBackedUnitValuePolicy.evaluate(
            EvidenceBackedUnitValueInput(
                priceClaim = observedPriceClaim(offer.current),
                quantityClaim = quantityClaim(quantity).copy(
                    authority = EvidenceAuthorityClass.USER_ASSERTED
                ),
                offer = offer,
                quantity = quantity,
                priceDisposition = EvidenceDisposition.RANKABLE
            )
        )

        assertFalse(result.rankable)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY in
                result.blockReasons
        )
    }

    @Test
    fun unlikeClaimDomainsCannotBeSmuggledIntoUnitValueCalculation() {
        val offer = Offer(current = Money.parse("4.99", "CAD"))
        val quantity = QuantityNormalization.grams(1000)

        val result = EvidenceBackedUnitValuePolicy.evaluate(
            EvidenceBackedUnitValueInput(
                priceClaim = observedPriceClaim(offer.current).copy(
                    domain = EvidenceClaimDomain.MARKET_BENCHMARK
                ),
                quantityClaim = quantityClaim(quantity).copy(
                    domain = EvidenceClaimDomain.NUTRITION
                ),
                offer = offer,
                quantity = quantity,
                priceDisposition = EvidenceDisposition.RANKABLE
            )
        )

        assertFalse(result.rankable)
        assertTrue(EvidenceBackedUnitValueBlockReason.UNSUPPORTED_PRICE_DOMAIN in result.blockReasons)
        assertTrue(EvidenceBackedUnitValueBlockReason.INVALID_QUANTITY_DOMAIN in result.blockReasons)
    }

    private fun observedPriceClaim(money: Money) = EvidenceClaim(
        claimId = "open-prices:1:observed-price",
        domain = EvidenceClaimDomain.OBSERVED_PRICE,
        valueFingerprint = EvidenceFingerprints.money(money),
        authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
        scope = EvidenceClaimScope(
            productKey = PRODUCT_KEY,
            locationKey = "open-prices-location-1",
            commerceChannelKey = "PHYSICAL_STORE",
            currencyCode = "CAD"
        ),
        observedAtEpochMillis = 1_800_000_000_000L
    )

    private fun quantityClaim(quantity: NormalizedQuantity) = EvidenceClaim(
        claimId = "open-food-facts:quantity",
        domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
        valueFingerprint = EvidenceFingerprints.quantity(quantity),
        authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
        scope = EvidenceClaimScope(productKey = PRODUCT_KEY),
        observedAtEpochMillis = 1_799_000_000_000L
    )

    companion object {
        private const val PRODUCT_KEY = "gtin:036000291452"
    }
}

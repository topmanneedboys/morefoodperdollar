package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceConflictPolicy
import com.valuepilot.core.EvidenceConflictRelationship
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.QuantityNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsImportedMetadataTest {

    @Test
    fun acceptsStructuredWholeProductMassAndCreatesOnlyQuantityClaim() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "1 kg",
                productQuantity = "1000",
                productQuantityUnit = "g"
            )
        )

        assertTrue(result.accepted)
        assertTrue(result.failures.isEmpty())
        assertNotNull(result.metadata)
        assertNotNull(result.quantityClaim)

        val metadata = requireNotNull(result.metadata)
        val claim = requireNotNull(result.quantityClaim)

        assertEquals(OpenFoodFactsImportedMetadataMapper.PROVIDER_ID, metadata.providerId)
        assertEquals("036000291452", metadata.gtin)
        assertEquals("Rolled Oats", metadata.productName)
        assertEquals("Example Brand", metadata.brands)
        assertEquals(BaseUnit.GRAM, metadata.normalizedQuantity.unit)
        assertEquals(1_000_000_000L, metadata.normalizedQuantity.amountMicros)
        assertEquals(
            OpenFoodFactsQuantityBasis.STRUCTURED_MASS_OR_VOLUME,
            metadata.quantityBasis
        )
        assertEquals(MODIFIED_SECONDS * 1_000L, metadata.sourceLastModifiedAtEpochMillis)

        assertEquals(EvidenceClaimDomain.PACKAGE_QUANTITY, claim.domain)
        assertEquals(EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA, claim.authority)
        assertEquals("gtin:0036000291452", claim.scope.productKey)
        assertEquals("quantity:GRAM:1000000000", claim.valueFingerprint)
        assertNull(claim.scope.merchantKey)
        assertNull(claim.scope.currencyCode)
    }

    @Test
    fun equivalentUpcAndGtin13ShareCanonicalProductScopeWithoutErasingSourceIdentity() {
        val upc = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(code = "036000291452")
        )
        val gtin13 = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(code = "0036000291452")
        )

        assertTrue(upc.accepted)
        assertTrue(gtin13.accepted)

        val upcMetadata = requireNotNull(upc.metadata)
        val gtin13Metadata = requireNotNull(gtin13.metadata)
        val upcClaim = requireNotNull(upc.quantityClaim)
        val gtin13Claim = requireNotNull(gtin13.quantityClaim)

        assertEquals("036000291452", upcMetadata.gtin)
        assertEquals("0036000291452", gtin13Metadata.gtin)
        assertEquals("gtin:0036000291452", upcClaim.scope.productKey)
        assertEquals(upcClaim.scope.productKey, gtin13Claim.scope.productKey)
        assertEquals(upcClaim.valueFingerprint, gtin13Claim.valueFingerprint)
        assertNotEquals(upcClaim.claimId, gtin13Claim.claimId)
        assertEquals("open-food-facts:036000291452:package-quantity", upcClaim.claimId)
        assertEquals("open-food-facts:0036000291452:package-quantity", gtin13Claim.claimId)
    }

    @Test
    fun acceptsMultipackWhenRawDisplayAndStructuredQuantityAgree() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "6 x 250 ml",
                productQuantity = "1500",
                productQuantityUnit = "ml"
            )
        )

        assertTrue(result.accepted)
        assertNotNull(result.metadata)
        val quantity = requireNotNull(result.metadata).normalizedQuantity
        assertEquals(BaseUnit.MILLILITRE, quantity.unit)
        assertEquals(1_500_000_000L, quantity.amountMicros)
    }

    @Test
    fun acceptsExactDisplayedSupplementCountWithoutInventingMass() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "100 tablets",
                productQuantity = null,
                productQuantityUnit = null
            )
        )

        assertTrue(result.accepted)
        val metadata = requireNotNull(result.metadata)
        val claim = requireNotNull(result.quantityClaim)

        assertEquals(BaseUnit.COUNT, metadata.normalizedQuantity.unit)
        assertEquals(100_000_000L, metadata.normalizedQuantity.amountMicros)
        assertEquals(
            OpenFoodFactsQuantityBasis.DISPLAYED_SUPPLEMENT_COUNT,
            metadata.quantityBasis
        )
        assertEquals("100 tablets", metadata.rawQuantity)
        assertEquals("quantity:COUNT:100000000", claim.valueFingerprint)
        assertEquals(EvidenceClaimDomain.PACKAGE_QUANTITY, claim.domain)
    }

    @Test
    fun acceptsNarrowFrenchSupplementCountVocabulary() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "60 gélules",
                productQuantity = null,
                productQuantityUnit = null
            )
        )

        assertTrue(result.accepted)
        val metadata = requireNotNull(result.metadata)
        assertEquals(BaseUnit.COUNT, metadata.normalizedQuantity.unit)
        assertEquals(60_000_000L, metadata.normalizedQuantity.amountMicros)
        assertEquals(
            OpenFoodFactsQuantityBasis.DISPLAYED_SUPPLEMENT_COUNT,
            metadata.quantityBasis
        )
    }

    @Test
    fun complexSupplementDisplayIsNotGuessedAsPackageCount() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "60 capsules x 500 mg",
                productQuantity = null,
                productQuantityUnit = null
            )
        )

        assertFalse(result.accepted)
        assertTrue(OpenFoodFactsImportFailure.MISSING_STRUCTURED_QUANTITY in result.failures)
    }

    @Test
    fun strengthOnlyDisplayIsNotMisreadAsCount() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "500 mg",
                productQuantity = null,
                productQuantityUnit = null
            )
        )

        assertFalse(result.accepted)
        assertTrue(OpenFoodFactsImportFailure.MISSING_STRUCTURED_QUANTITY in result.failures)
    }

    @Test
    fun acceptsCountEvenWhenUnusedMassFieldsAreAlsoPresent() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "90 capsules",
                productQuantity = "45",
                productQuantityUnit = "g"
            )
        )

        assertTrue(result.accepted)
        val metadata = requireNotNull(result.metadata)
        assertEquals(BaseUnit.COUNT, metadata.normalizedQuantity.unit)
        assertEquals(90_000_000L, metadata.normalizedQuantity.amountMicros)
        assertEquals(
            OpenFoodFactsQuantityBasis.DISPLAYED_SUPPLEMENT_COUNT,
            metadata.quantityBasis
        )
    }

    @Test
    fun rejectsBadGtinMissingQuantityAndUnsupportedStructuredUnit() {
        assertFailure(
            validProduct(code = "036000291453"),
            OpenFoodFactsImportFailure.INVALID_GTIN
        )
        assertFailure(
            validProduct(productQuantity = null),
            OpenFoodFactsImportFailure.MISSING_STRUCTURED_QUANTITY
        )
        assertFailure(
            validProduct(productQuantityUnit = "kg"),
            OpenFoodFactsImportFailure.UNSUPPORTED_STRUCTURED_UNIT
        )
    }

    @Test
    fun rejectsCorruptOrNonPositiveStructuredQuantity() {
        assertFailure(
            validProduct(productQuantity = "0"),
            OpenFoodFactsImportFailure.INVALID_STRUCTURED_QUANTITY
        )
        assertFailure(
            validProduct(productQuantity = "1.1234567"),
            OpenFoodFactsImportFailure.INVALID_STRUCTURED_QUANTITY
        )
        assertFailure(
            validProduct(productQuantity = "999999999999"),
            OpenFoodFactsImportFailure.INVALID_STRUCTURED_QUANTITY
        )
    }

    @Test
    fun parseableRawQuantityDisagreementFailsClosed() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "900 g",
                productQuantity = "1000",
                productQuantityUnit = "g"
            )
        )

        assertFalse(result.accepted)
        assertNull(result.metadata)
        assertNull(result.quantityClaim)
        assertTrue(
            OpenFoodFactsImportFailure.INCONSISTENT_RAW_QUANTITY in result.failures
        )
    }

    @Test
    fun unsupportedRawDisplaySyntaxDoesNotInventAParsingFailure() {
        val result = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "6 eggs",
                productQuantity = "360",
                productQuantityUnit = "g"
            )
        )

        assertTrue(result.accepted)
        assertNotNull(result.metadata)
        assertEquals(
            360_000_000L,
            requireNotNull(result.metadata).normalizedQuantity.amountMicros
        )
        assertEquals(
            OpenFoodFactsQuantityBasis.STRUCTURED_MASS_OR_VOLUME,
            requireNotNull(result.metadata).quantityBasis
        )
    }

    @Test
    fun invalidSourceModificationTimeIsRejectedRatherThanReplacedWithImportTime() {
        assertFailure(
            validProduct(lastModifiedEpochSeconds = -1L),
            OpenFoodFactsImportFailure.INVALID_MODIFICATION_TIME
        )
    }

    @Test
    fun actualOpenFoodFactsQuantityCannotOverrideMerchantAuthoritativeQuantity() {
        val mapped = OpenFoodFactsImportedMetadataMapper.map(
            validProduct(
                rawQuantity = "900 g",
                productQuantity = "900",
                productQuantityUnit = "g"
            )
        )
        assertNotNull(mapped.quantityClaim)
        val community = requireNotNull(mapped.quantityClaim)

        val merchant = EvidenceClaim(
            claimId = "merchant:quantity",
            domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
            valueFingerprint = EvidenceFingerprints.quantity(
                QuantityNormalization.grams(1000)
            ),
            authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE,
            scope = EvidenceClaimScope(productKey = community.scope.productKey),
            observedAtEpochMillis = community.observedAtEpochMillis - 1_000L
        )

        val decision = EvidenceConflictPolicy.resolve(community, merchant)

        assertEquals(EvidenceConflictRelationship.PREFER_RIGHT, decision.relationship)
        assertEquals("merchant:quantity", decision.selectedClaimId)
        assertFalse(decision.blocksRanking)
    }

    private fun assertFailure(
        row: OpenFoodFactsImportedProduct,
        expected: OpenFoodFactsImportFailure
    ) {
        val result = OpenFoodFactsImportedMetadataMapper.map(row)
        assertFalse(result.accepted)
        assertNull(result.metadata)
        assertNull(result.quantityClaim)
        assertTrue("Expected $expected in ${result.failures}", expected in result.failures)
    }

    private fun validProduct(
        code: String = "036000291452",
        productName: String? = "Rolled Oats",
        brands: String? = "Example Brand",
        rawQuantity: String? = "1 kg",
        productQuantity: String? = "1000",
        productQuantityUnit: String? = "g",
        lastModifiedEpochSeconds: Long? = MODIFIED_SECONDS
    ) = OpenFoodFactsImportedProduct(
        code = code,
        productName = productName,
        brands = brands,
        rawQuantity = rawQuantity,
        productQuantity = productQuantity,
        productQuantityUnit = productQuantityUnit,
        lastModifiedEpochSeconds = lastModifiedEpochSeconds
    )

    companion object {
        private const val MODIFIED_SECONDS = 1_790_000_000L
    }
}

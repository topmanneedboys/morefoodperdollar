package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProviderOfferImportTest {

    private val provider = EvidenceProvider(
        id = EvidenceProviderId("provider-test"),
        displayName = "Provider Test"
    )

    private val source = ShoppingSource(
        id = ShoppingSourceId("merchant-test"),
        displayName = "Merchant Test"
    )

    private val dataset = EvidenceDatasetNamespace(
        id = "provider-test-feed",
        displayName = "Provider Test Feed",
        licenseId = "rights-review-pending",
        storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
    )

    private fun record(
        identity: ImportedSourceIdentity = ImportedSourceIdentity(
            providerItemId = "product-1",
            sku = "sku-1",
            suppliedGtin = "036000291452"
        ),
        prices: List<ImportedPriceField> = listOf(
            ImportedPriceField("retail_price", "9.99", Money(999, "CAD")),
            ImportedPriceField("sale_price", "12.99", Money(1299, "CAD"))
        ),
        datasetGeneratedAtEpochMillis: Long? = 1_000L,
        priceObservedAtEpochMillis: Long? = null
    ): ProviderOfferImportRecord =
        ProviderOfferImportRecord(
            provider = provider,
            source = source,
            dataset = dataset,
            environment = EvidenceEnvironment.REAL_WORLD,
            channel = EvidenceChannel.FIRST_PARTY_FEED,
            claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
            identity = identity,
            productName = "Example Product",
            sourcePriceFields = prices,
            availability = AvailabilityEvidence(
                state = AvailabilityState.IN_STOCK,
                claimKind = EvidenceClaimKind.SOURCE_ASSERTED
            ),
            productUrl = "https://example.test/product",
            imageUrl = "https://example.test/image.jpg",
            datasetGeneratedAtEpochMillis = datasetGeneratedAtEpochMillis,
            priceObservedAtEpochMillis = priceObservedAtEpochMillis
        )

    @Test
    fun preservesDistinctProviderPriceFieldsWithoutChoosingCurrentPrice() {
        val imported = record()

        assertEquals(
            ImportedPriceSemantics.UNRESOLVED_SOURCE_FIELDS,
            imported.priceSemantics
        )
        assertEquals(999L, imported.priceField("retail_price")?.parsedAmount?.minorUnits)
        assertEquals(1299L, imported.priceField("SALE_PRICE")?.parsedAmount?.minorUnits)
        assertEquals("9.99", imported.priceField("retail_price")?.rawValue)
        assertEquals("12.99", imported.priceField("sale_price")?.rawValue)
    }

    @Test
    fun invalidSuppliedGtinIsPreservedButNotPromoted() {
        val identity = ImportedSourceIdentity(
            providerItemId = "product-1",
            sku = "sku-1",
            suppliedGtin = "036000291453"
        )

        assertEquals(ImportedGtinStatus.INVALID, identity.gtinStatus)
        assertEquals("036000291453", identity.suppliedGtin)
        assertNull(identity.validatedGtin)

        val promoted = identity.validatedSourceProductIdentity()
        assertNotNull(promoted)
        assertEquals("product-1", promoted?.providerItemId)
        assertEquals("sku-1", promoted?.sku)
        assertNull(promoted?.gtin)
    }

    @Test
    fun checksumValidGtinCanBePromotedForStrongIdentity() {
        val identity = ImportedSourceIdentity(
            providerItemId = "product-1",
            sku = "sku-1",
            suppliedGtin = "036000291452"
        )

        assertEquals(ImportedGtinStatus.VALID, identity.gtinStatus)
        assertEquals("036000291452", identity.validatedGtin)
        assertEquals(
            "036000291452",
            identity.validatedSourceProductIdentity()?.gtin
        )
    }

    @Test
    fun invalidGtinAloneDoesNotCreateAValidatedIdentity() {
        val identity = ImportedSourceIdentity(
            suppliedGtin = "036000291453"
        )

        assertEquals(ImportedGtinStatus.INVALID, identity.gtinStatus)
        assertNull(identity.validatedSourceProductIdentity())
    }

    @Test
    fun datasetGenerationTimeIsNotPromotedToOfferFreshness() {
        val imported = record(
            datasetGeneratedAtEpochMillis = 123_456L,
            priceObservedAtEpochMillis = null
        )

        assertEquals(123_456L, imported.datasetGeneratedAtEpochMillis)
        assertNull(imported.priceObservedAtEpochMillis)
        assertNull(imported.availability.observedAtEpochMillis)
    }

    @Test
    fun malformedSourcePriceCanRemainAuditableWithoutBecomingMoney() {
        val imported = record(
            prices = listOf(
                ImportedPriceField(
                    sourceFieldName = "retail_price",
                    rawValue = "not-a-price",
                    parsedAmount = null
                )
            )
        )

        val field = imported.priceField("retail_price")
        assertEquals("not-a-price", field?.rawValue)
        assertNull(field?.parsedAmount)
    }

    @Test
    fun duplicateSourcePriceFieldNamesAreRejectedCaseInsensitively() {
        try {
            record(
                prices = listOf(
                    ImportedPriceField("sale_price", "9.99", Money(999, "CAD")),
                    ImportedPriceField("SALE_PRICE", "8.99", Money(899, "CAD"))
                )
            )
            fail("Expected duplicate price-field rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("unique"))
        }
    }
}

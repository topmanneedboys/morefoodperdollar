package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.ImportedGtinStatus
import com.valuepilot.core.ImportedPriceSemantics
import com.valuepilot.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JamiesonRakutenPublishedCatalogStagingAdapterTest {

    @Test
    fun `valid catalog row stages unresolved source fields without factual activation`() {
        val extras = List(10) { index -> "opaque-${index + 1}" }
        val row = row(extras = extras)
        val feedGeneratedAt = 1_788_000_000_000L

        val staged = staged(row, feedGeneratedAt)
        val record = staged.importRecord

        assertSame(row, staged.sourceRow)
        assertTrue(staged.priceAssessment.structurallyUsableForStaging)
        assertEquals("rakuten-advertising", record.provider.id.value)
        assertEquals("Rakuten Advertising", record.provider.displayName)
        assertEquals(
            JamiesonProductCatalogProductionContract.DATASET_NAMESPACE_ID,
            record.source.id.value
        )
        assertEquals("Jamieson Product Catalog via Rakuten", record.source.displayName)
        assertEquals(JamiesonProductCatalogProductionContract.datasetNamespace, record.dataset)
        assertEquals(EvidenceEnvironment.REAL_WORLD, record.environment)
        assertEquals(EvidenceChannel.FIRST_PARTY_FEED, record.channel)
        assertEquals(EvidenceClaimKind.SOURCE_ASSERTED, record.claimKind)
        assertEquals(ImportedPriceSemantics.UNRESOLVED_SOURCE_FIELDS, record.priceSemantics)

        assertEquals("product-123", record.identity.providerItemId)
        assertEquals("JAM-D3-100", record.identity.sku)
        assertEquals("4006381333931", record.identity.suppliedGtin)
        assertEquals(ImportedGtinStatus.VALID, record.identity.gtinStatus)
        assertEquals("4006381333931", record.identity.canonicalGtin)

        assertEquals("14.99", record.priceField("Sale Price")?.rawValue)
        assertEquals(Money.parse("14.99", "CAD"), record.priceField("Sale Price")?.parsedAmount)
        assertEquals("19.99", record.priceField("Retail Price")?.rawValue)
        assertEquals(Money.parse("19.99", "CAD"), record.priceField("Retail Price")?.parsedAmount)
        assertEquals(2, record.sourcePriceFields.size)

        assertEquals(AvailabilityState.UNKNOWN, record.availability.state)
        assertEquals(EvidenceClaimKind.UNKNOWN, record.availability.claimKind)
        assertNull(record.availability.observedAtEpochMillis)
        assertEquals(feedGeneratedAt, record.datasetGeneratedAtEpochMillis)
        assertNull(record.priceObservedAtEpochMillis)
        assertEquals("https://example.invalid/product", record.productUrl)
        assertEquals("https://example.invalid/image.jpg", record.imageUrl)

        assertEquals("in-stock", staged.sourceRow.availabilityFieldValue)
        assertEquals("08/01/2026", staged.sourceRow.beginDate)
        assertEquals("09/30/2026", staged.sourceRow.endDate)
        assertEquals("https://example.invalid/buy", staged.sourceRow.buyUrl)
        assertEquals(extras, staged.sourceRow.opaquePostPrimaryFieldValues)
    }

    @Test
    fun `blank optional sale price remains an unresolved null parsed field`() {
        val row = row(overrides = mapOf(JamiesonRakutenPublishedCatalogField.SALE_PRICE to ""))

        val staged = staged(row)
        val sale = staged.importRecord.priceField("Sale Price")
        val retail = staged.importRecord.priceField("Retail Price")

        assertEquals("", sale?.rawValue)
        assertNull(sale?.parsedAmount)
        assertEquals(Money.parse("19.99", "CAD"), retail?.parsedAmount)
        assertEquals(ImportedPriceSemantics.UNRESOLVED_SOURCE_FIELDS, staged.importRecord.priceSemantics)
    }

    @Test
    fun `invalid supplied gtin stays auditable and is not promoted cross source`() {
        val rawInvalidGtin = "1234567890123"
        val row = row(overrides = mapOf(JamiesonRakutenPublishedCatalogField.UNIVERSAL_PRODUCT_CODE to rawInvalidGtin))

        val staged = staged(row)
        val identity = staged.importRecord.identity
        val promoted = identity.validatedSourceProductIdentity()

        assertEquals(rawInvalidGtin, identity.suppliedGtin)
        assertEquals(ImportedGtinStatus.INVALID, identity.gtinStatus)
        assertNull(identity.validatedGtin)
        assertNull(identity.canonicalGtin)
        assertEquals("product-123", promoted?.providerItemId)
        assertEquals("JAM-D3-100", promoted?.sku)
        assertNull(promoted?.gtin)
    }

    @Test
    fun `missing required catalog structure is quarantined before import construction`() {
        val row =
            row(
                overrides =
                    mapOf(
                        JamiesonRakutenPublishedCatalogField.PRODUCT_NAME to "",
                        JamiesonRakutenPublishedCatalogField.PRODUCT_ID to "",
                        JamiesonRakutenPublishedCatalogField.SKU_NUMBER to "",
                        JamiesonRakutenPublishedCatalogField.PRIMARY_CATEGORY to "",
                        JamiesonRakutenPublishedCatalogField.PRODUCT_URL to "",
                        JamiesonRakutenPublishedCatalogField.PRODUCT_IMAGE_URL to "",
                        JamiesonRakutenPublishedCatalogField.RETAIL_PRICE to "",
                        JamiesonRakutenPublishedCatalogField.UNIVERSAL_PRODUCT_CODE to ""
                    )
            )

        val result = JamiesonRakutenPublishedCatalogStagingAdapter.stage(row)
        val quarantined = result as JamiesonRakutenPublishedCatalogStagingResult.Quarantined

        assertSame(row, quarantined.sourceRow)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_PRODUCT_NAME in quarantined.reasons)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_SKU in quarantined.reasons)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_SOURCE_IDENTITY in quarantined.reasons)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.MISSING_PRIMARY_CATEGORY in quarantined.reasons)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.INVALID_PRODUCT_URL in quarantined.reasons)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.INVALID_IMAGE_URL in quarantined.reasons)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.UNUSABLE_PRICE_FIELDS in quarantined.reasons)
    }

    @Test
    fun `valid non CAD row is quarantined without upgrading currency from contract`() {
        val row = row(overrides = mapOf(JamiesonRakutenPublishedCatalogField.CURRENCY to "USD"))

        val result = JamiesonRakutenPublishedCatalogStagingAdapter.stage(row)
        val quarantined = result as JamiesonRakutenPublishedCatalogStagingResult.Quarantined

        assertTrue(quarantined.priceAssessment.structurallyUsableForStaging)
        assertEquals("USD", quarantined.priceAssessment.parsedCurrencyCode)
        assertEquals(
            setOf(JamiesonRakutenPublishedCatalogStagingRejectionReason.DECLARED_CURRENCY_MISMATCH),
            quarantined.reasons
        )
    }

    @Test
    fun `malformed or missing price fields quarantine deterministically`() {
        val cases =
            listOf(
                mapOf(JamiesonRakutenPublishedCatalogField.RETAIL_PRICE to ""),
                mapOf(JamiesonRakutenPublishedCatalogField.RETAIL_PRICE to "not-money"),
                mapOf(JamiesonRakutenPublishedCatalogField.SALE_PRICE to "not-money")
            )

        cases.forEach { overrides ->
            val result = JamiesonRakutenPublishedCatalogStagingAdapter.stage(row(overrides = overrides))
            val quarantined = result as JamiesonRakutenPublishedCatalogStagingResult.Quarantined

            assertTrue(
                JamiesonRakutenPublishedCatalogStagingRejectionReason.UNUSABLE_PRICE_FIELDS in
                    quarantined.reasons
            )
            assertFalse(quarantined.priceAssessment.structurallyUsableForStaging)
        }
    }

    @Test
    fun `invalid product and image urls quarantine using same http scheme boundary as qualifier`() {
        val cases =
            listOf(
                JamiesonRakutenPublishedCatalogField.PRODUCT_URL to "ftp://example.invalid/product",
                JamiesonRakutenPublishedCatalogField.PRODUCT_URL to "https:///missing-host",
                JamiesonRakutenPublishedCatalogField.PRODUCT_IMAGE_URL to ""
            )

        cases.forEach { (field, value) ->
            val result =
                JamiesonRakutenPublishedCatalogStagingAdapter.stage(
                    row(overrides = mapOf(field to value))
                )
            assertTrue(result is JamiesonRakutenPublishedCatalogStagingResult.Quarantined)
        }
    }

    @Test
    fun `verified feed header time is provenance only and invalid times fail closed`() {
        val withoutHeaderTime = staged(row())
        assertNull(withoutHeaderTime.importRecord.datasetGeneratedAtEpochMillis)
        assertNull(withoutHeaderTime.importRecord.priceObservedAtEpochMillis)
        assertNull(withoutHeaderTime.importRecord.availability.observedAtEpochMillis)

        listOf(0L, -1L).forEach { invalidTime ->
            val result =
                JamiesonRakutenPublishedCatalogStagingAdapter.stage(
                    row = row(),
                    verifiedFeedHeaderGeneratedAtEpochMillis = invalidTime
                )
            val quarantined = result as JamiesonRakutenPublishedCatalogStagingResult.Quarantined
            assertTrue(
                JamiesonRakutenPublishedCatalogStagingRejectionReason
                    .INVALID_VERIFIED_FEED_GENERATION_TIME in quarantined.reasons
            )
        }
    }

    @Test
    fun `oversized product or identity values quarantine instead of throwing shared core constructors`() {
        val longProduct = "p".repeat(501)
        val longIdentity = "i".repeat(161)
        val result =
            JamiesonRakutenPublishedCatalogStagingAdapter.stage(
                row(
                    overrides =
                        mapOf(
                            JamiesonRakutenPublishedCatalogField.PRODUCT_NAME to longProduct,
                            JamiesonRakutenPublishedCatalogField.PRODUCT_ID to longIdentity
                        )
                )
            )
        val quarantined = result as JamiesonRakutenPublishedCatalogStagingResult.Quarantined

        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.PRODUCT_NAME_TOO_LONG in quarantined.reasons)
        assertTrue(JamiesonRakutenPublishedCatalogStagingRejectionReason.SOURCE_IDENTITY_TOO_LONG in quarantined.reasons)
    }

    @Test
    fun `staging source boundary cannot mint current price availability freshness ranking or watch authority`() {
        val source = source("JamiesonRakutenPublishedCatalogStagingAdapter.kt").readText()

        listOf(
            "ProviderOfferImportRecord(",
            "ImportedPriceSemantics.UNRESOLVED_SOURCE_FIELDS",
            "AvailabilityState.UNKNOWN",
            "priceObservedAtEpochMillis = null",
            "verifiedFeedHeaderGeneratedAtEpochMillis",
            "EvidenceChannel.FIRST_PARTY_FEED",
            "EvidenceClaimKind.SOURCE_ASSERTED"
        ).forEach { required ->
            assertTrue("missing staging trust boundary: $required", source.contains(required))
        }

        listOf(
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceClaim(",
            "ShoppingEvidence(",
            "AvailabilityState.IN_STOCK",
            "AvailabilityState.OUT_OF_STOCK",
            "row.availabilityFieldValue",
            "row.beginDate",
            "row.endDate",
            "System.currentTimeMillis",
            "Instant.now",
            "HttpURLConnection",
            "OkHttp",
            "Retrofit",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "StapleWatch",
            "ProductionBestValueRanking",
            "MainActivity"
        ).forEach { forbidden ->
            assertFalse("unexpected activation or side-effect authority: $forbidden", source.contains(forbidden))
        }
    }

    private fun staged(
        row: JamiesonRakutenPublishedCatalogRow,
        feedGeneratedAt: Long? = null
    ): JamiesonRakutenPublishedCatalogStagedRecord {
        val result =
            JamiesonRakutenPublishedCatalogStagingAdapter.stage(
                row = row,
                verifiedFeedHeaderGeneratedAtEpochMillis = feedGeneratedAt
            )
        assertTrue("expected staged result, got $result", result is JamiesonRakutenPublishedCatalogStagingResult.Staged)
        return (result as JamiesonRakutenPublishedCatalogStagingResult.Staged).value
    }

    private fun row(
        overrides: Map<JamiesonRakutenPublishedCatalogField, String> = emptyMap(),
        extras: List<String> = emptyList()
    ): JamiesonRakutenPublishedCatalogRow {
        val values = canonicalPrimaryValues().toMutableList()
        overrides.forEach { (field, value) -> values[field.index] = value }
        return JamiesonRakutenPublishedCatalogRow.decode(values + extras)
    }

    private fun canonicalPrimaryValues(): List<String> =
        listOf(
            "product-123",
            "Jamieson Vitamin D3 1000 IU",
            "JAM-D3-100",
            "Health > Vitamins",
            "Vitamin D",
            "https://example.invalid/product",
            "https://example.invalid/image.jpg",
            "https://example.invalid/buy",
            "Short description",
            "Long description",
            "5.00",
            "amount",
            "14.99",
            "19.99",
            "08/01/2026",
            "09/30/2026",
            "Jamieson",
            "0",
            "vitamin,d3",
            "MPN-123",
            "Jamieson Wellness",
            "Canada",
            "in-stock",
            "4006381333931",
            "101",
            "CAD",
            "m1-value",
            "pixel-value"
        )

    private fun source(fileName: String): File =
        sequenceOf(
            File("src/main/java/com/valuepilot/app/$fileName"),
            File("android/app/src/main/java/com/valuepilot/app/$fileName")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $fileName")
}

package com.valuepilot.app

import com.valuepilot.core.ImportedDiscountRelationship
import com.valuepilot.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JamiesonRakutenPublishedPriceSemanticsTest {

    @Test
    fun `sale below retail preserves documented roles and exact money`() {
        val result = assess(sale = "12.34", retail = "15.99", currency = "CAD")

        assertTrue(result.structurallyUsableForStaging)
        assertEquals("CAD", result.parsedCurrencyCode)
        assertEquals(JamiesonRakutenPublishedPriceFieldStatus.PARSED, result.salePriceStatus)
        assertEquals(JamiesonRakutenPublishedPriceFieldStatus.PARSED, result.retailPriceStatus)
        assertEquals(Money(1_234L, "CAD"), result.salePrice)
        assertEquals(Money(1_599L, "CAD"), result.retailPrice)
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_BELOW_REFERENCE,
            result.relationshipAssessment?.relationship
        )
        assertEquals("Sale Price", result.relationshipAssessment?.discountedFieldName)
        assertEquals("Retail Price", result.relationshipAssessment?.referenceFieldName)
        assertTrue(result.blockers.isEmpty())
    }

    @Test
    fun `equal sale and retail is preserved instead of being rewritten as a discount`() {
        val result = assess(sale = "15.99", retail = "15.99")

        assertTrue(result.structurallyUsableForStaging)
        assertEquals(ImportedDiscountRelationship.EQUAL, result.relationshipAssessment?.relationship)
        assertFalse(result.relationshipAssessment?.structurallySupportsDiscountClaim ?: true)
    }

    @Test
    fun `sale above retail is an explicit semantic conflict and never silently corrected`() {
        val result = assess(sale = "19.99", retail = "15.99")

        assertTrue(result.structurallyUsableForStaging)
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_ABOVE_REFERENCE_CONFLICT,
            result.relationshipAssessment?.relationship
        )
        assertTrue(result.relationshipAssessment?.hasSemanticConflict == true)
        assertEquals(Money(1_999L, "CAD"), result.salePrice)
        assertEquals(Money(1_599L, "CAD"), result.retailPrice)
    }

    @Test
    fun `blank optional sale is distinct from an invalid sale and leaves retail exact`() {
        val result = assess(sale = "", retail = "15.99")

        assertTrue(result.structurallyUsableForStaging)
        assertEquals(JamiesonRakutenPublishedPriceFieldStatus.ABSENT_OPTIONAL, result.salePriceStatus)
        assertNull(result.salePrice)
        assertEquals(JamiesonRakutenPublishedPriceFieldStatus.PARSED, result.retailPriceStatus)
        assertEquals(Money(1_599L, "CAD"), result.retailPrice)
        assertEquals(ImportedDiscountRelationship.UNAVAILABLE, result.relationshipAssessment?.relationship)
        assertTrue(result.blockers.isEmpty())
    }

    @Test
    fun `missing required retail fails closed and is not treated like optional sale`() {
        val result = assess(sale = "12.34", retail = "")

        assertFalse(result.structurallyUsableForStaging)
        assertEquals(JamiesonRakutenPublishedPriceFieldStatus.PARSED, result.salePriceStatus)
        assertEquals(JamiesonRakutenPublishedPriceFieldStatus.MISSING_REQUIRED, result.retailPriceStatus)
        assertEquals(Money(1_234L, "CAD"), result.salePrice)
        assertNull(result.retailPrice)
        assertNull(result.relationshipAssessment)
        assertEquals(
            setOf(JamiesonRakutenPublishedPriceSemanticBlocker.MISSING_RETAIL_PRICE),
            result.blockers
        )
    }

    @Test
    fun `malformed zero negative and overprecision prices fail closed without throwing batch qualification`() {
        listOf("not-money", "0", "-1.00", "1.999").forEach { invalidSale ->
            val result = assess(sale = invalidSale, retail = "15.99")

            assertFalse("expected invalid Sale Price: $invalidSale", result.structurallyUsableForStaging)
            assertEquals(JamiesonRakutenPublishedPriceFieldStatus.INVALID, result.salePriceStatus)
            assertNull(result.salePrice)
            assertNull(result.relationshipAssessment)
            assertTrue(JamiesonRakutenPublishedPriceSemanticBlocker.INVALID_SALE_PRICE in result.blockers)
        }

        listOf("not-money", "0", "-1.00", "1.999").forEach { invalidRetail ->
            val result = assess(sale = "12.34", retail = invalidRetail)

            assertFalse("expected invalid Retail Price: $invalidRetail", result.structurallyUsableForStaging)
            assertEquals(JamiesonRakutenPublishedPriceFieldStatus.INVALID, result.retailPriceStatus)
            assertNull(result.retailPrice)
            assertNull(result.relationshipAssessment)
            assertTrue(JamiesonRakutenPublishedPriceSemanticBlocker.INVALID_RETAIL_PRICE in result.blockers)
        }
    }

    @Test
    fun `invalid currency blocks money interpretation without inventing CAD from Jamieson context`() {
        listOf("", "cad", "CA", "CAD ").forEach { invalidCurrency ->
            val result = assess(sale = "12.34", retail = "15.99", currency = invalidCurrency)

            assertFalse(result.structurallyUsableForStaging)
            assertNull(result.parsedCurrencyCode)
            assertNull(result.salePrice)
            assertNull(result.retailPrice)
            assertNull(result.relationshipAssessment)
            assertEquals(
                JamiesonRakutenPublishedPriceFieldStatus.BLOCKED_BY_INVALID_CURRENCY,
                result.salePriceStatus
            )
            assertEquals(
                JamiesonRakutenPublishedPriceFieldStatus.BLOCKED_BY_INVALID_CURRENCY,
                result.retailPriceStatus
            )
            assertTrue(JamiesonRakutenPublishedPriceSemanticBlocker.INVALID_CURRENCY in result.blockers)
        }
    }

    @Test
    fun `syntactically valid non CAD currency is preserved for separate contract gate rather than used as geography`() {
        val result = assess(sale = "12.34", retail = "15.99", currency = "USD")

        assertTrue(result.structurallyUsableForStaging)
        assertEquals("USD", result.parsedCurrencyCode)
        assertEquals(Money(1_234L, "USD"), result.salePrice)
        assertEquals(Money(1_599L, "USD"), result.retailPrice)
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_BELOW_REFERENCE,
            result.relationshipAssessment?.relationship
        )
    }

    @Test
    fun `raw published values remain available beside parsed exact money`() {
        val result = assess(sale = " 12.34 ", retail = "15,99", currency = "CAD")

        assertEquals(" 12.34 ", result.rawSalePriceFieldValue)
        assertEquals("15,99", result.rawRetailPriceFieldValue)
        assertEquals("CAD", result.rawCurrencyFieldValue)
        assertEquals(Money(1_234L, "CAD"), result.salePrice)
        assertEquals(Money(1_599L, "CAD"), result.retailPrice)
    }

    @Test
    fun `source boundary owns no current price freshness geography rights availability ranking network persistence or UI authority`() {
        val source = source("JamiesonRakutenPublishedPriceSemantics.kt").readText()

        listOf(
            "Money.parse(",
            "ImportedDiscountRelationshipEvaluator.assess(",
            "SALE_PRICE_FIELD_NAME",
            "RETAIL_PRICE_FIELD_NAME",
            "structurallyUsableForStaging"
        ).forEach { required ->
            assertTrue("missing price-semantic boundary: $required", source.contains(required))
        }

        listOf(
            "ProviderCurrentPriceCandidateFactory",
            "ADVERTISER_ASSERTED_CURRENT",
            "priceRankingAllowed = true",
            "Offer(",
            "EvidenceClaim(",
            "priceObservedAtEpochMillis",
            "datasetGeneratedAtEpochMillis",
            "System.currentTimeMillis",
            "Instant.now",
            "geographyAssessment",
            "evaluateTermination",
            "AvailabilityEvidence(",
            "HttpURLConnection",
            "OkHttp",
            "Retrofit",
            "android.permission.INTERNET",
            "SharedPreferences",
            "AtomicFile",
            "WorkManager",
            "Notification",
            "MainActivity",
            "StapleWatch",
            "ProductionBestValueRanking"
        ).forEach { forbidden ->
            assertFalse("unexpected authority or side effect: $forbidden", source.contains(forbidden))
        }
    }

    private fun assess(
        sale: String,
        retail: String,
        currency: String = "CAD"
    ): JamiesonRakutenPublishedPriceSemanticAssessment =
        JamiesonRakutenPublishedPriceSemantics.assess(
            JamiesonRakutenPublishedCatalogRow.decode(
                publishedFields(
                    sale = sale,
                    retail = retail,
                    currency = currency
                )
            )
        )

    private fun publishedFields(
        sale: String,
        retail: String,
        currency: String
    ): List<String> =
        MutableList(JamiesonRakutenPublishedCatalogRow.PRIMARY_FIELD_COUNT) { index -> "field-$index" }
            .also { fields ->
                fields[0] = "Jamieson Test Product"
                fields[1] = "sku-1"
                fields[10] = sale
                fields[11] = retail
                fields[20] = "in stock"
                fields[23] = currency
            }

    private fun source(fileName: String): File =
        sequenceOf(
            File("src/main/java/com/valuepilot/app/$fileName"),
            File("android/app/src/main/java/com/valuepilot/app/$fileName")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $fileName")
}

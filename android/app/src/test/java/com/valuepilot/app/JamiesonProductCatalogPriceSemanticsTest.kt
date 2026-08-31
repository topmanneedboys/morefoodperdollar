package com.valuepilot.app

import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ImportedDiscountRelationship
import com.valuepilot.core.ImportedPriceField
import com.valuepilot.core.ImportedSourceIdentity
import com.valuepilot.core.Money
import com.valuepilot.core.ProductionPriceRelationshipRule
import com.valuepilot.core.ProviderOfferImportRecord
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JamiesonProductCatalogPriceSemanticsTest {

    private val provider =
        EvidenceProvider(
            id = JamiesonProductCatalogProductionContract.providerId,
            displayName = "Rakuten Advertising"
        )

    private val source =
        ShoppingSource(
            id = ShoppingSourceId("jamieson-catalog-source"),
            displayName = "Jamieson Catalog"
        )

    private fun record(
        prices: List<ImportedPriceField> =
            listOf(
                price("sale_price", "8.00", 800L),
                price("retail_price", "10.00", 1_000L)
            ),
        providerOverride: EvidenceProvider = provider,
        datasetId: String =
            JamiesonProductCatalogProductionContract.DATASET_NAMESPACE_ID
    ): ProviderOfferImportRecord =
        ProviderOfferImportRecord(
            provider = providerOverride,
            source = source,
            dataset =
                JamiesonProductCatalogProductionContract.datasetNamespace.copy(
                    id = datasetId
                ),
            environment = EvidenceEnvironment.REAL_WORLD,
            channel = EvidenceChannel.FIRST_PARTY_FEED,
            claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
            identity = ImportedSourceIdentity(providerItemId = "jamieson-item-1"),
            productName = "Jamieson Example",
            sourcePriceFields = prices
        )

    private fun price(
        fieldName: String,
        raw: String,
        minorUnits: Long?,
        currencyCode: String = "CAD",
        fractionDigits: Int = 2
    ): ImportedPriceField =
        ImportedPriceField(
            sourceFieldName = fieldName,
            rawValue = raw,
            parsedAmount =
                minorUnits?.let {
                    Money(
                        minorUnits = it,
                        currencyCode = currencyCode,
                        fractionDigits = fractionDigits
                    )
                }
        )

    @Test
    fun `valid sale price is current role and retail price is reference`() {
        val result = JamiesonProductCatalogPriceSemantics.resolve(record())

        assertTrue(result.resolved)
        assertTrue(result.blockers.isEmpty())
        assertEquals("sale_price", result.priceRoles?.currentPriceFieldName)
        assertEquals("retail_price", result.priceRoles?.referencePriceFieldName)
        assertEquals(
            ProductionPriceRelationshipRule.CURRENT_MUST_NOT_EXCEED_REFERENCE,
            result.priceRoles?.relationshipRule
        )
        assertEquals(
            ImportedDiscountRelationship.DISCOUNTED_BELOW_REFERENCE,
            result.discountAssessment?.relationship
        )
    }

    @Test
    fun `absent or blank optional sale price uses regular retail price role`() {
        val absent =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(prices = listOf(price("retail_price", "10.00", 1_000L)))
            )
        val blank =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(
                    prices =
                        listOf(
                            price("sale_price", "", null),
                            price("retail_price", "10.00", 1_000L)
                        )
                )
            )

        listOf(absent, blank).forEach { result ->
            assertTrue(result.resolved)
            assertEquals("retail_price", result.priceRoles?.currentPriceFieldName)
            assertNull(result.priceRoles?.referencePriceFieldName)
            assertEquals(ProductionPriceRelationshipRule.NONE, result.priceRoles?.relationshipRule)
            assertNull(result.discountAssessment)
        }
    }

    @Test
    fun `supplied malformed sale price is never silently replaced by retail`() {
        val result =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(
                    prices =
                        listOf(
                            price("sale_price", "not-a-price", null),
                            price("retail_price", "10.00", 1_000L)
                        )
                )
            )

        assertFalse(result.resolved)
        assertNull(result.priceRoles)
        assertEquals(
            setOf(JamiesonProductCatalogPriceSemanticsBlocker.SALE_PRICE_INVALID),
            result.blockers
        )
    }

    @Test
    fun `required retail price must be present valid and positive`() {
        val missing =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(prices = listOf(price("sale_price", "8.00", 800L)))
            )
        val malformed =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(prices = listOf(price("retail_price", "bad", null)))
            )
        val nonPositive =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(prices = listOf(price("retail_price", "0.00", 0L)))
            )

        listOf(missing, malformed, nonPositive).forEach { result ->
            assertFalse(result.resolved)
            assertTrue(
                JamiesonProductCatalogPriceSemanticsBlocker.RETAIL_PRICE_UNAVAILABLE in
                    result.blockers
            )
        }
    }

    @Test
    fun `cad scope is enforced on every usable source price`() {
        val retailUsd =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(
                    prices =
                        listOf(
                            price(
                                fieldName = "retail_price",
                                raw = "10.00",
                                minorUnits = 1_000L,
                                currencyCode = "USD"
                            )
                        )
                )
            )
        val saleUsd =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(
                    prices =
                        listOf(
                            price(
                                fieldName = "sale_price",
                                raw = "8.00",
                                minorUnits = 800L,
                                currencyCode = "USD"
                            ),
                            price("retail_price", "10.00", 1_000L)
                        )
                )
            )

        listOf(retailUsd, saleUsd).forEach { result ->
            assertFalse(result.resolved)
            assertTrue(
                JamiesonProductCatalogPriceSemanticsBlocker.NON_CAD_PRICE in
                    result.blockers
            )
        }
    }

    @Test
    fun `sale and retail money must be comparable and sale cannot exceed retail`() {
        val incomparable =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(
                    prices =
                        listOf(
                            price("sale_price", "8.000", 8_000L, fractionDigits = 3),
                            price("retail_price", "10.00", 1_000L)
                        )
                )
            )
        val inverted =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(
                    prices =
                        listOf(
                            price("sale_price", "12.00", 1_200L),
                            price("retail_price", "10.00", 1_000L)
                        )
                )
            )

        assertFalse(incomparable.resolved)
        assertTrue(
            JamiesonProductCatalogPriceSemanticsBlocker.INCOMPARABLE_PRICE_MONEY in
                incomparable.blockers
        )
        assertFalse(inverted.resolved)
        assertEquals(
            setOf(
                JamiesonProductCatalogPriceSemanticsBlocker
                    .SALE_PRICE_ABOVE_RETAIL_PRICE
            ),
            inverted.blockers
        )
    }

    @Test
    fun `provider and dataset scope cannot be reused`() {
        val wrongProvider =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(
                    providerOverride =
                        EvidenceProvider(
                            id = EvidenceProviderId("other-provider"),
                            displayName = "Other"
                        )
                )
            )
        val wrongDataset =
            JamiesonProductCatalogPriceSemantics.resolve(
                record(datasetId = "rakuten.other-product-catalog")
            )

        listOf(wrongProvider, wrongDataset).forEach { result ->
            assertFalse(result.resolved)
            assertEquals(
                setOf(
                    JamiesonProductCatalogPriceSemanticsBlocker
                        .PROVIDER_OR_DATASET_SCOPE_MISMATCH
                ),
                result.blockers
            )
        }
    }

    @Test
    fun `price semantics owns field roles only and no freshness current claim or runtime authority`() {
        val source = source("JamiesonProductCatalogPriceSemantics.kt").readText()

        listOf(
            "priceObservedAtEpochMillis",
            "datasetGeneratedAtEpochMillis",
            "EvidenceFreshness",
            "ProductionCurrentPriceClaim",
            "EvidenceClaim(",
            "Offer(",
            "System.currentTimeMillis",
            "java.net",
            "okhttp",
            "retrofit",
            "WorkManager",
            "Notification",
            "MainActivity",
            "StapleWatch"
        ).forEach { forbidden ->
            assertFalse("Price semantics must not contain $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("ProductionPriceFieldRoles"))
        assertTrue(source.contains("CURRENT_MUST_NOT_EXCEED_REFERENCE"))
        assertTrue(source.contains("matchesDeclaredFeedCurrency"))
    }

    private fun source(name: String): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

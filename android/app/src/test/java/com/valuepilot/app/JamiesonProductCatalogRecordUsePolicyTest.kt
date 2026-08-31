package com.valuepilot.app

import com.valuepilot.core.ProductionDatasetDispositionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JamiesonProductCatalogRecordUsePolicyTest {

    private val policy = JamiesonProductCatalogRecordUsePolicy
    private val contract = JamiesonProductCatalogProductionContract
    private val now = 1_800_000_000_000L

    @Test
    fun `active exact Jamieson Canadian CAD catalog can display search cache and index but cannot price rank`() {
        val result =
            policy.evaluate(
                datasetNamespaceId = contract.DATASET_NAMESPACE_ID,
                sourceCurrencyCode = "CAD",
                targetCountryCode = "CA",
                partnershipTerminationAtEpochMillis = null,
                evaluatedAtEpochMillis = now
            )

        assertTrue(result.displayAllowed)
        assertTrue(result.searchAndCatalogComparisonAllowed)
        assertTrue(result.cacheAndIndexAllowed)
        assertFalse(result.priceRankingAllowed)
        assertEquals(ProductionDatasetDispositionState.RETAINED, result.requiredNamespaceDisposition)
        assertNull(result.deletionDeadlineEpochMillis)
        assertFalse(result.deletionOverdue)
        assertEquals(
            setOf(
                JamiesonProductCatalogRecordUseBlocker.PRICE_SEMANTICS_UNVERIFIED,
                JamiesonProductCatalogRecordUseBlocker.DATASET_RECENCY_UNVERIFIED,
                JamiesonProductCatalogRecordUseBlocker.OFFER_FRESHNESS_UNVERIFIED
            ),
            result.blockers
        )
    }

    @Test
    fun `wrong dataset namespace fails closed even when market and currency match`() {
        val result =
            policy.evaluate(
                datasetNamespaceId = "rakuten.some-other-advertiser",
                sourceCurrencyCode = "CAD",
                targetCountryCode = "CA",
                partnershipTerminationAtEpochMillis = null,
                evaluatedAtEpochMillis = now
            )

        assertFalse(result.displayAllowed)
        assertFalse(result.searchAndCatalogComparisonAllowed)
        assertFalse(result.cacheAndIndexAllowed)
        assertFalse(result.priceRankingAllowed)
        assertTrue(JamiesonProductCatalogRecordUseBlocker.DATASET_NAMESPACE_MISMATCH in result.blockers)
        assertEquals(ProductionDatasetDispositionState.RETAINED, result.requiredNamespaceDisposition)
    }

    @Test
    fun `Jamieson documented Canada market cannot be used for a US consumer target`() {
        val result =
            policy.evaluate(
                datasetNamespaceId = contract.DATASET_NAMESPACE_ID,
                sourceCurrencyCode = "CAD",
                targetCountryCode = "US",
                partnershipTerminationAtEpochMillis = null,
                evaluatedAtEpochMillis = now
            )

        assertFalse(result.displayAllowed)
        assertFalse(result.searchAndCatalogComparisonAllowed)
        assertFalse(result.cacheAndIndexAllowed)
        assertTrue(JamiesonProductCatalogRecordUseBlocker.TARGET_MARKET_MISMATCH in result.blockers)
    }

    @Test
    fun `non CAD and missing source currency fail closed without using currency to infer country`() {
        listOf("USD", null).forEach { currency ->
            val result =
                policy.evaluate(
                    datasetNamespaceId = contract.DATASET_NAMESPACE_ID,
                    sourceCurrencyCode = currency,
                    targetCountryCode = "CA",
                    partnershipTerminationAtEpochMillis = null,
                    evaluatedAtEpochMillis = now
                )

            assertFalse(result.displayAllowed)
            assertFalse(result.searchAndCatalogComparisonAllowed)
            assertFalse(result.cacheAndIndexAllowed)
            assertTrue(JamiesonProductCatalogRecordUseBlocker.SOURCE_CURRENCY_MISMATCH in result.blockers)
            assertFalse(JamiesonProductCatalogRecordUseBlocker.TARGET_MARKET_MISMATCH in result.blockers)
        }
    }

    @Test
    fun `partnership termination withdraws production use immediately while preserving deletion deadline`() {
        val terminatedAt = now - 1_000L
        val result =
            policy.evaluate(
                datasetNamespaceId = contract.DATASET_NAMESPACE_ID,
                sourceCurrencyCode = "CAD",
                targetCountryCode = "CA",
                partnershipTerminationAtEpochMillis = terminatedAt,
                evaluatedAtEpochMillis = now
            )

        assertFalse(result.displayAllowed)
        assertFalse(result.searchAndCatalogComparisonAllowed)
        assertFalse(result.cacheAndIndexAllowed)
        assertFalse(result.priceRankingAllowed)
        assertEquals(ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED, result.requiredNamespaceDisposition)
        assertEquals(
            terminatedAt + contract.TERMINATION_DELETION_DAYS * 24L * 60L * 60L * 1_000L,
            result.deletionDeadlineEpochMillis
        )
        assertFalse(result.deletionOverdue)
        assertTrue(JamiesonProductCatalogRecordUseBlocker.PARTNERSHIP_TERMINATED in result.blockers)
    }

    @Test
    fun `sixty day deletion deadline is not a production use grace period and overdue state is surfaced`() {
        val sixtyDaysMillis = contract.TERMINATION_DELETION_DAYS * 24L * 60L * 60L * 1_000L
        val terminatedAt = now - sixtyDaysMillis - 1L
        val result =
            policy.evaluate(
                datasetNamespaceId = contract.DATASET_NAMESPACE_ID,
                sourceCurrencyCode = "CAD",
                targetCountryCode = "CA",
                partnershipTerminationAtEpochMillis = terminatedAt,
                evaluatedAtEpochMillis = now
            )

        assertFalse(result.displayAllowed)
        assertFalse(result.searchAndCatalogComparisonAllowed)
        assertFalse(result.cacheAndIndexAllowed)
        assertEquals(ProductionDatasetDispositionState.WITHDRAWAL_REQUIRED, result.requiredNamespaceDisposition)
        assertEquals(terminatedAt + sixtyDaysMillis, result.deletionDeadlineEpochMillis)
        assertTrue(result.deletionOverdue)
    }

    @Test
    fun `price ranking remains blocked by all three unresolved factual gates`() {
        val result =
            policy.evaluate(
                datasetNamespaceId = contract.DATASET_NAMESPACE_ID,
                sourceCurrencyCode = "CAD",
                targetCountryCode = "CA",
                partnershipTerminationAtEpochMillis = null,
                evaluatedAtEpochMillis = now
            )

        assertFalse(result.priceRankingAllowed)
        assertTrue(JamiesonProductCatalogRecordUseBlocker.PRICE_SEMANTICS_UNVERIFIED in result.blockers)
        assertTrue(JamiesonProductCatalogRecordUseBlocker.DATASET_RECENCY_UNVERIFIED in result.blockers)
        assertTrue(JamiesonProductCatalogRecordUseBlocker.OFFER_FRESHNESS_UNVERIFIED in result.blockers)
    }

    @Test
    fun `policy validates inputs rather than normalizing ambiguous market or dataset values`() {
        assertThrows<IllegalArgumentException> {
            policy.evaluate("", "CAD", "CA", null, now)
        }
        assertThrows<IllegalArgumentException> {
            policy.evaluate(contract.DATASET_NAMESPACE_ID, "CAD", "ca", null, now)
        }
        assertThrows<IllegalArgumentException> {
            policy.evaluate(contract.DATASET_NAMESPACE_ID, "CAD", "CA", null, 0L)
        }
        assertThrows<IllegalArgumentException> {
            policy.evaluate(contract.DATASET_NAMESPACE_ID, "CAD", "CA", now + 1L, now)
        }
    }

    @Test
    fun `source boundary owns no feed schema price authority networking clock persistence ranking or UI`() {
        val source = source("JamiesonProductCatalogRecordUsePolicy.kt").readText()

        listOf(
            "JamiesonProductCatalogProductionContract",
            "geographyAssessment(targetCountryCode)",
            "matchesDeclaredFeedCurrency(sourceCurrencyCode)",
            "evaluateTermination(",
            "priceRankingAllowed = false",
            "PRICE_SEMANTICS_UNVERIFIED",
            "DATASET_RECENCY_UNVERIFIED",
            "OFFER_FRESHNESS_UNVERIFIED"
        ).forEach { required ->
            assertTrue("missing boundary: $required", source.contains(required))
        }

        listOf(
            "sale_price",
            "retail_price",
            "current_price",
            "StagedProductionOfferCandidate(",
            "Offer(",
            "ProductionCurrentPrice",
            "EvidenceClaim(",
            "System.currentTimeMillis",
            "Instant.now",
            "HttpURLConnection",
            "OkHttp",
            "Retrofit",
            "android.permission.INTERNET",
            "WorkManager",
            "Notification",
            "MainActivity",
            "ProductionBestValueRanking"
        ).forEach { forbidden ->
            assertFalse("unexpected authority or side effect: $forbidden", source.contains(forbidden))
        }
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            assertTrue("expected ${T::class.java.name}, got ${error::class.java.name}", error is T)
            return
        }
        throw AssertionError("expected ${T::class.java.name}")
    }

    private fun source(fileName: String): File =
        sequenceOf(
            File("src/main/java/com/valuepilot/app/$fileName"),
            File("android/app/src/main/java/com/valuepilot/app/$fileName")
        ).firstOrNull(File::isFile)
            ?: error("Source file not found: $fileName")
}

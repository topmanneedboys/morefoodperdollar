package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderDatasetRecencyTest {

    private val policy = ImportedDatasetRecencyPolicy(
        recentForMillis = 60_000L,
        staleAfterMillis = 300_000L,
        futureToleranceMillis = 5_000L
    )

    private fun record(
        datasetGeneratedAtEpochMillis: Long?,
        priceObservedAtEpochMillis: Long? = null
    ): ProviderOfferImportRecord =
        ProviderOfferImportRecord(
            provider = EvidenceProvider(
                id = EvidenceProviderId("provider-test"),
                displayName = "Provider Test"
            ),
            source = ShoppingSource(
                id = ShoppingSourceId("merchant-test"),
                displayName = "Merchant Test"
            ),
            dataset = EvidenceDatasetNamespace(
                id = "provider-test-feed",
                displayName = "Provider Test Feed",
                licenseId = "rights-review-pending",
                storageBoundary = EvidenceStorageBoundary.PROPRIETARY_RESTRICTED
            ),
            environment = EvidenceEnvironment.REAL_WORLD,
            channel = EvidenceChannel.FIRST_PARTY_FEED,
            claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
            identity = ImportedSourceIdentity(providerItemId = "product-1"),
            productName = "Example Product",
            sourcePriceFields = listOf(
                ImportedPriceField(
                    sourceFieldName = "source_price",
                    rawValue = "9.99",
                    parsedAmount = Money(999, "CAD")
                )
            ),
            datasetGeneratedAtEpochMillis = datasetGeneratedAtEpochMillis,
            priceObservedAtEpochMillis = priceObservedAtEpochMillis
        )

    @Test
    fun missingDatasetTimeIsUnknown() {
        assertEquals(
            ImportedDatasetRecency.UNKNOWN,
            record(datasetGeneratedAtEpochMillis = null)
                .datasetRecency(
                    evaluatedAtEpochMillis = 1_000_000L,
                    policy = policy
                )
        )
    }

    @Test
    fun recentAgingAndStaleBoundariesAreDeterministic() {
        val evaluatedAt = 1_000_000L

        assertEquals(
            ImportedDatasetRecency.RECENT,
            record(evaluatedAt - 60_000L)
                .datasetRecency(evaluatedAt, policy)
        )
        assertEquals(
            ImportedDatasetRecency.AGING,
            record(evaluatedAt - 60_001L)
                .datasetRecency(evaluatedAt, policy)
        )
        assertEquals(
            ImportedDatasetRecency.AGING,
            record(evaluatedAt - 300_000L)
                .datasetRecency(evaluatedAt, policy)
        )
        assertEquals(
            ImportedDatasetRecency.STALE,
            record(evaluatedAt - 300_001L)
                .datasetRecency(evaluatedAt, policy)
        )
    }

    @Test
    fun smallClockSkewIsRecentButLargeFutureDateFailsClosed() {
        val evaluatedAt = 1_000_000L

        assertEquals(
            ImportedDatasetRecency.RECENT,
            record(evaluatedAt + 5_000L)
                .datasetRecency(evaluatedAt, policy)
        )
        assertEquals(
            ImportedDatasetRecency.FUTURE_DATED,
            record(evaluatedAt + 5_001L)
                .datasetRecency(evaluatedAt, policy)
        )
    }

    @Test
    fun invalidEvaluationInstantIsUnknown() {
        assertEquals(
            ImportedDatasetRecency.UNKNOWN,
            record(datasetGeneratedAtEpochMillis = 100L)
                .datasetRecency(
                    evaluatedAtEpochMillis = 0L,
                    policy = policy
                )
        )
    }

    @Test
    fun datasetRecencyNeverCreatesPerOfferPriceObservationTime() {
        val imported = record(
            datasetGeneratedAtEpochMillis = 950_000L,
            priceObservedAtEpochMillis = null
        )

        assertEquals(
            ImportedDatasetRecency.RECENT,
            imported.datasetRecency(
                evaluatedAtEpochMillis = 1_000_000L,
                policy = policy
            )
        )
        assertNull(imported.priceObservedAtEpochMillis)
        assertEquals(
            ImportedPriceSemantics.UNRESOLVED_SOURCE_FIELDS,
            imported.priceSemantics
        )
    }

    @Test
    fun truePerOfferObservationTimeRemainsSeparateFromDatasetRecency() {
        val imported = record(
            datasetGeneratedAtEpochMillis = 600_000L,
            priceObservedAtEpochMillis = 999_000L
        )

        assertEquals(
            ImportedDatasetRecency.STALE,
            imported.datasetRecency(
                evaluatedAtEpochMillis = 1_000_000L,
                policy = policy
            )
        )
        assertEquals(999_000L, imported.priceObservedAtEpochMillis)
    }
}

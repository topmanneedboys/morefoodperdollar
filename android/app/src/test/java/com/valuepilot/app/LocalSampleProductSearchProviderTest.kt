package com.valuepilot.app

import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSampleProductSearchProviderTest {

    @Test
    fun providerCopiesRequestIdentityAndHonorsBound() {
        val request =
            ProductSearchRequest(
                requestId = 7L,
                query = "eggs",
                maxObservations = 2
            )

        val batch =
            LocalSampleProductSearchProvider
                .search(request)

        assertEquals(
            7L,
            batch.requestId
        )

        assertEquals(
            2,
            batch.evidence.size
        )

        assertTrue(
            batch.evidence.all {
                it.environment ==
                    EvidenceEnvironment.SAMPLE
            }
        )

        assertTrue(
            batch.evidence.all {
                it.channel ==
                    EvidenceChannel.FIXTURE
            }
        )

        assertTrue(
            batch.evidence.all {
                it.isSample &&
                    !it.isRealWorld
            }
        )
    }

    @Test
    fun sampleEvidenceCarriesTypedProviderSourceAndIdentity() {
        val request =
            ProductSearchRequest(
                requestId = 1L,
                query = "eggs",
                maxObservations = 1
            )

        val evidence =
            LocalSampleProductSearchProvider
                .search(request)
                .evidence
                .single()

        assertEquals(
            "valuepilot-sample-catalog",
            evidence.provider.id.value
        )

        assertEquals(
            "ValuePilot Sample Catalog",
            evidence.provider.displayName
        )

        assertEquals(
            "sample-market-a",
            evidence.source.id.value
        )

        assertEquals(
            "Sample Market A",
            evidence.source.displayName
        )

        assertEquals(
            evidence.source.id.value,
            evidence.observation.sourceId
        )

        assertNotNull(
            evidence.sourceProductIdentity
                ?.providerItemId
        )

        assertEquals(
            AvailabilityState.UNKNOWN,
            evidence.availability.state
        )

        assertFalse(
            evidence.isRealWorld
        )
    }

    @Test
    fun sampleCatalogExercisesRealDeterministicValueRanking() {
        assertBest(
            "eggs",
            "Family Pack Eggs"
        )

        assertBest(
            "milk",
            "Whole Milk Family Jug"
        )

        assertBest(
            "chicken",
            "Chicken Breast Family Pack"
        )

        assertBest(
            "rice",
            "Basmati Rice Family Bag"
        )

        assertBest(
            "pizza",
            "Pepperoni Pizza Large"
        )
    }

    @Test
    fun sampleCatalogIsSmallAndExplicitlyBounded() {
        val request =
            ProductSearchRequest(
                requestId = 1L,
                query = "anything",
                maxObservations =
                    UniversalSearchController
                        .MAX_PROVIDER_OBSERVATIONS
            )

        val batch =
            LocalSampleProductSearchProvider
                .search(request)

        assertEquals(
            LocalSampleProductSearchProvider
                .SAMPLE_PRODUCT_COUNT,
            batch.evidence.size
        )

        assertTrue(
            batch.evidence.size <
                UniversalSearchController
                    .MAX_PROVIDER_OBSERVATIONS
        )
    }

    private fun assertBest(
        query: String,
        expectedName: String
    ) {
        val controller =
            UniversalSearchController()

        val ready =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent
                    .QueryChanged(query)
            ).state

        val started =
            controller.reduce(
                ready,
                UniversalSearchIntent.Submit
            )

        val batch =
            LocalSampleProductSearchProvider
                .search(
                    started.request!!
                )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent
                    .ResultsReceived(
                        batch
                    )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        assertEquals(
            expectedName,
            finished.results.first().name
        )

        assertTrue(
            finished.results.first().best
        )

        assertTrue(
            finished.results.first()
                .sampleEvidence
        )

        assertTrue(
            finished.results.first()
                .sourceSummary
                .startsWith(
                    "Sample source: "
                )
        )
    }
}

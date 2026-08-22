package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSampleProductSearchProviderTest {

    @Test
    fun providerCopiesRequestIdentityAndHonorsBound() {
        val request = ProductSearchRequest(
            requestId = 7L,
            query = "eggs",
            maxObservations = 2
        )

        val batch = LocalSampleProductSearchProvider.search(request)

        assertEquals(7L, batch.requestId)
        assertEquals(2, batch.observations.size)
        assertTrue(batch.observations.all { it.sourceId.startsWith("Sample ") })
    }

    @Test
    fun sampleCatalogExercisesRealDeterministicValueRanking() {
        assertBest("eggs", "Family Pack Eggs")
        assertBest("milk", "Whole Milk Family Jug")
        assertBest("chicken", "Chicken Breast Family Pack")
        assertBest("rice", "Basmati Rice Family Bag")
        assertBest("pizza", "Pepperoni Pizza Large")
    }

    @Test
    fun sampleCatalogIsSmallAndExplicitlyBounded() {
        val request = ProductSearchRequest(
            requestId = 1L,
            query = "anything",
            maxObservations = UniversalSearchController.MAX_PROVIDER_OBSERVATIONS
        )

        val batch = LocalSampleProductSearchProvider.search(request)

        assertEquals(
            LocalSampleProductSearchProvider.SAMPLE_PRODUCT_COUNT,
            batch.observations.size
        )
        assertTrue(
            batch.observations.size <
                UniversalSearchController.MAX_PROVIDER_OBSERVATIONS
        )
    }

    private fun assertBest(
        query: String,
        expectedName: String
    ) {
        val controller = UniversalSearchController()

        val ready = controller.reduce(
            controller.initialState(),
            UniversalSearchIntent.QueryChanged(query)
        ).state

        val started = controller.reduce(
            ready,
            UniversalSearchIntent.Submit
        )

        val batch = LocalSampleProductSearchProvider.search(started.request!!)

        val finished = controller.reduce(
            started.state,
            UniversalSearchIntent.ResultsReceived(batch)
        ).state

        assertEquals(UniversalSearchStatus.RESULTS, finished.status)
        assertEquals(expectedName, finished.results.first().name)
        assertTrue(finished.results.first().best)
    }
}

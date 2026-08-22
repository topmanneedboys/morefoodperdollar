package com.valuepilot.app

import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchTest {

    @Test
    fun initialStateIsBoundedIdleSearch() {
        val state =
            UniversalSearchController()
                .initialState()

        assertEquals(
            UniversalSearchStatus.IDLE,
            state.status
        )

        assertEquals("", state.query)
        assertNull(state.activeRequestId)
        assertEquals(1L, state.nextRequestId)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun queryIsNormalizedAndOversizedQueryIsRejected() {
        val controller =
            UniversalSearchController()

        val normalized =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent.QueryChanged(
                    "   chicken     breast   "
                )
            ).state

        assertEquals(
            "chicken breast",
            normalized.query
        )

        assertEquals(
            UniversalSearchStatus.READY,
            normalized.status
        )

        val oversized =
            controller.reduce(
                normalized,
                UniversalSearchIntent.QueryChanged(
                    "a".repeat(
                        UniversalSearchController
                            .MAX_QUERY_CHARS + 1
                    )
                )
            ).state

        assertEquals(
            UniversalSearchStatus.QUERY_TOO_LONG,
            oversized.status
        )

        assertEquals(
            UniversalSearchController.MAX_QUERY_CHARS,
            oversized.query.length
        )

        assertTrue(oversized.results.isEmpty())
    }

    @Test
    fun submitEmitsBoundedReplaceableProviderRequest() {
        val controller =
            UniversalSearchController()

        val ready =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent.QueryChanged(
                    "chicken breast"
                )
            ).state

        val started =
            controller.reduce(
                ready,
                UniversalSearchIntent.Submit
            )

        assertEquals(
            UniversalSearchStatus.LOADING,
            started.state.status
        )

        val request =
            assertNotNull(
                started.request
            ).let {
                started.request!!
            }

        assertEquals(
            "chicken breast",
            request.query
        )

        assertEquals(
            UniversalSearchController
                .MAX_PROVIDER_OBSERVATIONS,
            request.maxObservations
        )

        assertEquals(
            request.requestId,
            started.state.activeRequestId
        )

        assertEquals(
            request.requestId + 1L,
            started.state.nextRequestId
        )
    }

    @Test
    fun staleProviderCompletionCannotReplaceNewerSearch() {
        val controller =
            UniversalSearchController()

        val eggsReady =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent.QueryChanged(
                    "eggs"
                )
            ).state

        val eggsStarted =
            controller.reduce(
                eggsReady,
                UniversalSearchIntent.Submit
            )

        val milkReady =
            controller.reduce(
                eggsStarted.state,
                UniversalSearchIntent.QueryChanged(
                    "milk"
                )
            ).state

        val milkStarted =
            controller.reduce(
                milkReady,
                UniversalSearchIntent.Submit
            )

        val stale =
            controller.reduce(
                milkStarted.state,
                UniversalSearchIntent.ResultsReceived(
                    ProductSearchBatch(
                        requestId =
                            eggsStarted.request!!.requestId,
                        observations =
                            listOf(
                                observation(
                                    1,
                                    "Large Eggs\n12 ct\n$6.00"
                                )
                            )
                    )
                )
            )

        assertEquals(
            milkStarted.state,
            stale.state
        )

        assertNull(stale.request)
    }

    @Test
    fun providerOutputAndVisibleRowsAreStrictlyBounded() {
        var parseCount = 0

        val countingParser =
            ProductParser { rawText, sourceId ->
                parseCount++

                DeterministicProductParser.parse(
                    rawText,
                    sourceId
                )
            }

        val controller =
            UniversalSearchController(
                parser = countingParser
            )

        val started =
            start(
                controller,
                "eggs"
            )

        val observations =
            (1..250).map { index ->
                observation(
                    index,
                    "Eggs $index\n12 ct\n$6.00"
                )
            }

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    ProductSearchBatch(
                        requestId =
                            started.request!!.requestId,
                        observations =
                            observations
                    )
                )
            ).state

        assertEquals(
            UniversalSearchController
                .MAX_PROVIDER_OBSERVATIONS,
            parseCount
        )

        assertEquals(
            250,
            finished.receivedObservationCount
        )

        assertEquals(
            UniversalSearchController
                .MAX_PROVIDER_OBSERVATIONS,
            finished.parsedProductCount
        )

        assertEquals(
            UniversalSearchController
                .MAX_VISIBLE_RESULTS,
            finished.results.size
        )

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )
    }

    @Test
    fun deterministicValueRankingChoosesBetterUnitValue() {
        val controller =
            UniversalSearchController()

        val started =
            start(
                controller,
                "eggs"
            )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    ProductSearchBatch(
                        requestId =
                            started.request!!.requestId,
                        observations =
                            listOf(
                                observation(
                                    1,
                                    "Eggs A\n12 ct\n$6.00"
                                ),
                                observation(
                                    2,
                                    "Eggs B\n30 ct\n$12.00"
                                )
                            )
                    )
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        assertEquals(
            "Eggs B",
            finished.results.first().name
        )

        assertTrue(
            finished.results.first().best
        )

        assertEquals(
            1,
            finished.results.first().rank
        )
    }

    @Test
    fun unrelatedProductsAreRemovedBeforeRanking() {
        val controller =
            UniversalSearchController()

        val started =
            start(
                controller,
                "eggs"
            )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    ProductSearchBatch(
                        requestId =
                            started.request!!.requestId,
                        observations =
                            listOf(
                                observation(
                                    1,
                                    "Whole Milk\n2 L\n$5.00"
                                )
                            )
                    )
                )
            ).state

        assertEquals(
            UniversalSearchStatus.NO_RESULTS,
            finished.status
        )

        assertEquals(
            1,
            finished.parsedProductCount
        )

        assertEquals(
            0,
            finished.matchedProductCount
        )

        assertTrue(
            finished.results.isEmpty()
        )
    }

    @Test
    fun mixedCurrenciesAreNeverValueRankedTogether() {
        val controller =
            UniversalSearchController()

        val started =
            start(
                controller,
                "rice"
            )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    ProductSearchBatch(
                        requestId =
                            started.request!!.requestId,
                        observations =
                            listOf(
                                observation(
                                    1,
                                    "Rice\n5 kg\nC$12.49"
                                ),
                                observation(
                                    2,
                                    "Rice\n5 kg\nA$13.49"
                                )
                            )
                    )
                )
            ).state

        assertEquals(
            UniversalSearchStatus.MIXED_CURRENCIES,
            finished.status
        )

        assertTrue(
            finished.results.isEmpty()
        )
    }

    @Test
    fun replaceableProviderContractFeedsObservationsNotRanks() {
        val controller =
            UniversalSearchController()

        val started =
            start(
                controller,
                "milk"
            )

        val provider =
            ProductSearchProvider { request ->

                assertTrue(
                    request.maxObservations > 0
                )

                ProductSearchBatch(
                    requestId =
                        request.requestId,
                    observations =
                        listOf(
                            observation(
                                1,
                                "Whole Milk\n2 L\n$5.49",
                                sourceId =
                                    "fixture-market"
                            )
                        )
                )
            }

        val batch =
            provider.search(
                started.request!!
            )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    batch
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        assertEquals(
            "Whole Milk",
            finished.results.single().name
        )

        assertEquals(
            "fixture-market",
            finished.results.single().sourceId
        )
    }

    @Test
    fun staleFailureCannotEraseCurrentResultsOrLoadingState() {
        val controller =
            UniversalSearchController()

        val first =
            start(
                controller,
                "eggs"
            )

        val secondReady =
            controller.reduce(
                first.state,
                UniversalSearchIntent.QueryChanged(
                    "milk"
                )
            ).state

        val second =
            controller.reduce(
                secondReady,
                UniversalSearchIntent.Submit
            )

        val staleFailure =
            controller.reduce(
                second.state,
                UniversalSearchIntent.ProviderFailed(
                    first.request!!.requestId
                )
            ).state

        assertEquals(
            UniversalSearchStatus.LOADING,
            staleFailure.status
        )

        assertEquals(
            second.request!!.requestId,
            staleFailure.activeRequestId
        )

        assertFalse(
            staleFailure.status ==
                UniversalSearchStatus.PROVIDER_ERROR
        )
    }

    private fun start(
        controller: UniversalSearchController,
        query: String
    ): UniversalSearchTransition {

        val ready =
            controller.reduce(
                controller.initialState(),
                UniversalSearchIntent.QueryChanged(
                    query
                )
            ).state

        return controller.reduce(
            ready,
            UniversalSearchIntent.Submit
        )
    }

    private fun observation(
        index: Int,
        rawText: String,
        sourceId: String = "fixture"
    ): ProductObservation =
        ProductObservation(
            id =
                ProductObservationId(
                    "search-$index"
                ),
            sourceId = sourceId,
            rawText = rawText,
            observedAtEpochMillis =
                index.toLong()
        )
}

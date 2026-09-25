package com.valuepilot.app

import com.valuepilot.core.AvailabilityEvidence
import com.valuepilot.core.AvailabilityState
import com.valuepilot.core.EvidenceChannel
import com.valuepilot.core.EvidenceClaimKind
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceProvider
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import com.valuepilot.core.PromotionEvidence
import com.valuepilot.core.ShoppingEvidence
import com.valuepilot.core.ShoppingSource
import com.valuepilot.core.ShoppingSourceId
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
    fun submitWhileLoadingIsIgnoredUntilTheQueryChanges() {
        val controller = UniversalSearchController()
        val started = start(controller, "eggs")

        val duplicate =
            controller.reduce(
                started.state,
                UniversalSearchIntent.Submit
            )

        assertEquals(started.state, duplicate.state)
        assertNull(duplicate.request)
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
                        evidence =
                            listOf(
                                evidence(
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
                evidence(
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
                        evidence =
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
            UniversalSearchController.MAX_PROVIDER_OBSERVATIONS,
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
                        evidence =
                            listOf(
                                evidence(
                                    1,
                                    "Eggs A\n12 ct\n$6.00"
                                ),
                                evidence(
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
                        evidence =
                            listOf(
                                evidence(
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
                        evidence =
                            listOf(
                                evidence(
                                    1,
                                    "Rice\n5 kg\nC$12.49"
                                ),
                                evidence(
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
    fun replaceableProviderContractFeedsEvidenceNotRanks() {
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
                    evidence =
                        listOf(
                            evidence(
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
            "Sample source: fixture-market",
            finished.results.single()
                .sourceSummary
        )

        assertTrue(
            finished.results.single()
                .sampleEvidence
        )
    }


    @Test
    fun typedEvidenceProvenanceIsAuthoritativeForPresentation() {
        val controller =
            UniversalSearchController()

        val started =
            start(
                controller,
                "milk"
            )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    ProductSearchBatch(
                        requestId =
                            started.request!!.requestId,
                        evidence =
                            listOf(
                                evidence(
                                    index = 1,
                                    rawText =
                                        "Whole Milk\n2 L\n$5.49",
                                    sourceId =
                                        "trusted-store",
                                    sourceDisplayName =
                                        "Trusted Store",
                                    observationSourceId =
                                        "legacy-wrong-source",
                                    environment =
                                        EvidenceEnvironment
                                            .REAL_WORLD,
                                    channel =
                                        EvidenceChannel
                                            .AUTHORIZED_API,
                                    providerDisplayName =
                                        "Authorized Provider"
                                )
                            )
                    )
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        val row =
            finished.results.single()

        assertEquals(
            "Source: Trusted Store • via Authorized Provider",
            row.sourceSummary
        )

        assertFalse(
            row.sampleEvidence
        )

        assertFalse(
            row.sourceSummary.contains(
                "legacy-wrong-source"
            )
        )
    }

    @Test
    fun freshRealWorldEvidenceCanWinBestValueWhenTimeIsExplicit() {
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
                    batch =
                        ProductSearchBatch(
                            requestId =
                                started.request!!.requestId,
                            evidence =
                                listOf(
                                    evidence(
                                        index = 1,
                                        rawText =
                                            "Eggs A\n12 ct\n$6.00",
                                        sourceId =
                                            "store-a",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    ),
                                    evidence(
                                        index = 2,
                                        rawText =
                                            "Eggs B\n30 ct\n$12.00",
                                        sourceId =
                                            "store-b",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    )
                                )
                        ),
                    evaluatedAtEpochMillis =
                        TEST_NOW
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

        assertTrue(
            finished.results.first()
                .rankingEligible
        )

        assertEquals(
            1,
            finished.results.first().rank
        )
    }

    @Test
    fun staleCheaperEvidenceCannotBeatFreshRankableEvidence() {
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
                    batch =
                        ProductSearchBatch(
                            requestId =
                                started.request!!.requestId,
                            evidence =
                                listOf(
                                    evidence(
                                        index = 1,
                                        rawText =
                                            "Fresh Eggs\n12 ct\n$6.00",
                                        sourceId =
                                            "fresh-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    ),
                                    evidence(
                                        index = 2,
                                        rawText =
                                            "Old Cheap Eggs\n30 ct\n$1.00",
                                        sourceId =
                                            "stale-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                4L *
                                                HOUR,
                                        availability =
                                            inStock()
                                    )
                                )
                        ),
                    evaluatedAtEpochMillis =
                        TEST_NOW
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        val best =
            finished.results.first()

        assertEquals(
            "Fresh Eggs",
            best.name
        )

        assertTrue(best.best)

        val stale =
            finished.results.first {
                it.name ==
                    "Old Cheap Eggs"
            }

        assertFalse(stale.best)

        assertFalse(
            stale.rankingEligible
        )

        assertNull(stale.rank)

        assertTrue(
            stale.evidenceNotice
                ?.contains(
                    "Stale",
                    ignoreCase = true
                ) == true
        )
    }

    @Test
    fun futureDatedEvidenceIsRejectedBeforeParsingAndRanking() {
        var parseCount = 0

        val parser =
            ProductParser {
                    rawText,
                    sourceId ->

                parseCount++

                DeterministicProductParser
                    .parse(
                        rawText,
                        sourceId
                    )
            }

        val controller =
            UniversalSearchController(
                parser = parser
            )

        val started =
            start(
                controller,
                "eggs"
            )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    batch =
                        ProductSearchBatch(
                            requestId =
                                started.request!!.requestId,
                            evidence =
                                listOf(
                                    evidence(
                                        index = 1,
                                        rawText =
                                            "Future Eggs\n30 ct\n$1.00",
                                        sourceId =
                                            "future-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW +
                                                10L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    )
                                )
                        ),
                    evaluatedAtEpochMillis =
                        TEST_NOW
                )
            ).state

        assertEquals(
            0,
            parseCount
        )

        assertEquals(
            1,
            finished.rejectedObservationCount
        )

        assertEquals(
            UniversalSearchStatus.NO_RESULTS,
            finished.status
        )

        assertTrue(
            finished.results.isEmpty()
        )
    }

    @Test
    fun missingEvaluationTimeFailsClosedForRealWorldEvidence() {
        val controller =
            UniversalSearchController()

        val started =
            start(
                controller,
                "milk"
            )

        val finished =
            controller.reduce(
                started.state,
                UniversalSearchIntent.ResultsReceived(
                    ProductSearchBatch(
                        requestId =
                            started.request!!.requestId,
                        evidence =
                            listOf(
                                evidence(
                                    index = 1,
                                    rawText =
                                        "Whole Milk\n2 L\n$5.49",
                                    sourceId =
                                        "real-store",
                                    environment =
                                        EvidenceEnvironment
                                            .REAL_WORLD,
                                    channel =
                                        EvidenceChannel
                                            .AUTHORIZED_API,
                                    observedAtEpochMillis =
                                        TEST_NOW -
                                            5L *
                                            MINUTE,
                                    availability =
                                        inStock()
                                )
                            )
                    )
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        val row =
            finished.results.single()

        assertFalse(row.best)
        assertFalse(row.rankingEligible)
        assertNull(row.rank)

        assertTrue(
            row.evidenceNotice
                ?.contains(
                    "Freshness unknown"
                ) == true
        )
    }

    @Test
    fun displayOnlyDifferentCurrencyDoesNotBlockSafeRanking() {
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
                    batch =
                        ProductSearchBatch(
                            requestId =
                                started.request!!.requestId,
                            evidence =
                                listOf(
                                    evidence(
                                        index = 1,
                                        rawText =
                                            "Fresh Rice\n5 kg\nC$12.49",
                                        sourceId =
                                            "cad-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    ),
                                    evidence(
                                        index = 2,
                                        rawText =
                                            "Stale Rice\n5 kg\nA$1.00",
                                        sourceId =
                                            "aud-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                4L *
                                                HOUR,
                                        availability =
                                            inStock()
                                    )
                                )
                        ),
                    evaluatedAtEpochMillis =
                        TEST_NOW
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        assertEquals(
            "Fresh Rice",
            finished.results.first().name
        )

        assertTrue(
            finished.results.first().best
        )

        assertEquals(
            2,
            finished.results.size
        )
    }

    @Test
    fun parsedPromotionWithoutProvenanceCannotWinBestValue() {
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
                    batch =
                        ProductSearchBatch(
                            requestId =
                                started.request!!.requestId,
                            evidence =
                                listOf(
                                    evidence(
                                        index = 1,
                                        rawText =
                                            "Fresh Eggs\n12 ct\n$6.00",
                                        sourceId =
                                            "fresh-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    ),
                                    evidence(
                                        index = 2,
                                        rawText =
                                            "Promo Eggs\n12 ct\n$6.00\nBuy one get one free",
                                        sourceId =
                                            "promo-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    )
                                )
                        ),
                    evaluatedAtEpochMillis =
                        TEST_NOW
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        val best =
            finished.results.first()

        assertEquals(
            "Fresh Eggs",
            best.name
        )

        assertTrue(best.best)
        assertTrue(best.rankingEligible)

        val unverifiedPromotion =
            finished.results.first {
                it.name ==
                    "Promo Eggs"
            }

        assertFalse(
            unverifiedPromotion.best
        )

        assertFalse(
            unverifiedPromotion
                .rankingEligible
        )

        assertNull(
            unverifiedPromotion.rank
        )

        assertTrue(
            unverifiedPromotion
                .evidenceNotice
                ?.contains(
                    "Promotion not verified for ranking"
                ) == true
        )
    }

    @Test
    fun explicitTrustedPromotionEvidenceCanInfluenceBestValue() {
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
                    batch =
                        ProductSearchBatch(
                            requestId =
                                started.request!!.requestId,
                            evidence =
                                listOf(
                                    evidence(
                                        index = 1,
                                        rawText =
                                            "Regular Eggs\n12 ct\n$6.00",
                                        sourceId =
                                            "regular-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock()
                                    ),
                                    evidence(
                                        index = 2,
                                        rawText =
                                            "Verified Promo Eggs\n12 ct\n$6.00\nBuy one get one free",
                                        sourceId =
                                            "promo-store",
                                        environment =
                                            EvidenceEnvironment
                                                .REAL_WORLD,
                                        channel =
                                            EvidenceChannel
                                                .AUTHORIZED_API,
                                        observedAtEpochMillis =
                                            TEST_NOW -
                                                5L *
                                                MINUTE,
                                        availability =
                                            inStock(),
                                        promotion =
                                            PromotionEvidence(
                                                label =
                                                    "Buy one get one free",
                                                claimKind =
                                                    EvidenceClaimKind
                                                        .SOURCE_ASSERTED,
                                                validUntilEpochMillis =
                                                    TEST_NOW +
                                                        HOUR
                                            )
                                    )
                                )
                        ),
                    evaluatedAtEpochMillis =
                        TEST_NOW
                )
            ).state

        assertEquals(
            UniversalSearchStatus.RESULTS,
            finished.status
        )

        val best =
            finished.results.first()

        assertEquals(
            "Verified Promo Eggs",
            best.name
        )

        assertTrue(best.best)
        assertTrue(best.rankingEligible)

        assertEquals(
            1,
            best.rank
        )

        assertFalse(
            best.evidenceNotice
                ?.contains(
                    "Promotion not verified for ranking"
                ) == true
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

    private fun evidence(
        index: Int,
        rawText: String,
        sourceId: String = "fixture",
        sourceDisplayName: String =
            sourceId,
        observationSourceId: String =
            sourceId,
        environment: EvidenceEnvironment =
            EvidenceEnvironment.SAMPLE,
        channel: EvidenceChannel =
            EvidenceChannel.FIXTURE,
        providerDisplayName: String =
            "Fixture Provider",
        observedAtEpochMillis: Long =
            index.toLong(),
        availability: AvailabilityEvidence =
            AvailabilityEvidence(),
        promotion: PromotionEvidence? =
            null
    ): ShoppingEvidence =
        ShoppingEvidence(
            observation =
                ProductObservation(
                    id =
                        ProductObservationId(
                            "search-$index"
                        ),
                    sourceId =
                        observationSourceId,
                    rawText =
                        rawText,
                    observedAtEpochMillis =
                        observedAtEpochMillis
                ),
            provider =
                EvidenceProvider(
                    id =
                        EvidenceProviderId(
                            "test-provider"
                        ),
                    displayName =
                        providerDisplayName
                ),
            source =
                ShoppingSource(
                    id =
                        ShoppingSourceId(
                            sourceId
                        ),
                    displayName =
                        sourceDisplayName
                ),
            environment =
                environment,
            channel =
                channel,
            observationClaimKind =
                EvidenceClaimKind
                    .SOURCE_ASSERTED,
            availability =
                availability,
            promotion =
                promotion
        )

    private fun inStock():
        AvailabilityEvidence =
        AvailabilityEvidence(
            state =
                AvailabilityState.IN_STOCK,
            claimKind =
                EvidenceClaimKind
                    .SOURCE_ASSERTED,
            observedAtEpochMillis =
                TEST_NOW -
                    5L *
                    MINUTE
        )

    companion object {
        private const val MINUTE =
            60L * 1000L

        private const val HOUR =
            60L * MINUTE

        private const val TEST_NOW =
            1_800_000_000_000L
    }
}

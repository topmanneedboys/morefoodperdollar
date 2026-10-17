package com.valuepilot.app

import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaLiveSearchCoordinatorTest {

    private val release =
        ArgentinaBackendContract.ExpectedRelease(
            releaseId = "sepa-2026-09-06",
            releaseDate = RELEASE_DATE
        )

    private val context =
        ArgentinaSearchContext(
            latitude = "-34.60",
            longitude = "-58.38",
            radiusKm = "20",
            source = ArgentinaSearchContextSource.TEST_FIXTURE
        )

    @Test
    fun explicitGeographyIsRequiredAndBlankQueryDoesNotCallBackend() {
        val transport = RecordingTransport(statusBody = statusJson(), searchBody = searchJson(1))
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val missingContextGeneration = coordinator.submit("yerba", null)
        assertEquals(
            ArgentinaBackendContract.FailureCode.SEARCH_GEOGRAPHY_UNAVAILABLE,
            (states.last() as ArgentinaLiveSearchState.Unavailable).failure
        )
        assertEquals(0, transport.calls.size)

        val blankGeneration = coordinator.submit("   ", context)
        assertTrue(blankGeneration > missingContextGeneration)
        assertEquals(ArgentinaLiveSearchState.Idle(blankGeneration), states.last())
        assertEquals(0, transport.calls.size)
        coordinator.close()
    }

    @Test
    fun statusIsPinnedOnceAndSearchProjectsIdentityOnlyResults() {
        val transport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = searchJson(2),
                searchBodyByQuery = mapOf("mate" to searchJson(2, query = "mate"))
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val firstGeneration = coordinator.submit("yerba", context)
        assertEquals(ArgentinaLiveSearchState.Loading(firstGeneration, "yerba"), states.last())
        executor.runNext()
        val first = states.last() as ArgentinaLiveSearchState.Results
        assertEquals(firstGeneration, first.generation)
        assertEquals(2, first.presentation.matches.size)
        assertTrue(first.presentation.notice.contains("no current prices"))
        assertTrue(first.presentation.notice.contains(RELEASE_DATE))
        assertFalse(first.presentation.notice.contains("stock: true"))
        assertEquals(1, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.STATUS_ENDPOINT })

        val secondGeneration = coordinator.submit("mate", context)
        executor.runNext()
        assertTrue(states.last() is ArgentinaLiveSearchState.Results)
        assertEquals(secondGeneration, (states.last() as ArgentinaLiveSearchState.Results).generation)
        assertEquals(1, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.STATUS_ENDPOINT })
        assertEquals(2, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT })
        assertTrue(transport.calls.all { it.endpoint.startsWith("/v1/") })
        assertTrue(transport.calls.filter { it.endpoint == ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT }
            .all { it.body!!.contains("query") && !it.body.contains("price") })
        coordinator.close()
    }

    @Test
    fun releaseIsLearnedFromStatusInsteadOfBeingHardCoded() {
        val releaseId = "sepa-2026-09-07"
        val releaseDate = "2026-09-07"
        val transport =
            RecordingTransport(
                statusBody = statusJson(releaseId = releaseId, releaseDate = releaseDate),
                searchBody = searchJson(1, releaseId = releaseId, releaseDate = releaseDate)
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        coordinator.submit("yerba", context)
        executor.runNext()

        val result = states.last() as ArgentinaLiveSearchState.Results
        assertTrue(result.presentation.notice.contains(releaseDate))
        assertEquals(1, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.STATUS_ENDPOINT })
        coordinator.close()
    }

    @Test
    fun emptySearchIsDifferentFromUnavailableAndResultBoundIsTwentyFour() {
        val emptyTransport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = searchJson(0, query = "nothing")
            )
        val emptyStates = mutableListOf<ArgentinaLiveSearchState>()
        val emptyExecutor = QueuedExecutor()
        val emptyCoordinator = ArgentinaLiveSearchCoordinator(emptyTransport, emptyExecutor, emptyStates::add)
        emptyCoordinator.submit("nothing", context)
        emptyExecutor.runNext()
        assertTrue(emptyStates.last() is ArgentinaLiveSearchState.Empty)
        emptyCoordinator.close()

        val manyTransport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = searchJson(30, query = "many")
            )
        val manyStates = mutableListOf<ArgentinaLiveSearchState>()
        val manyExecutor = QueuedExecutor()
        val manyCoordinator = ArgentinaLiveSearchCoordinator(manyTransport, manyExecutor, manyStates::add)
        manyCoordinator.submit("many", context)
        manyExecutor.runNext()
        val result = manyStates.last() as ArgentinaLiveSearchState.Results
        assertEquals(24, result.presentation.matches.size)
        assertTrue(result.presentation.evaluatedCandidateCount >= 30)
        manyCoordinator.close()
    }

    @Test
    fun successfulResultIsReplacedByTheNewQueryEmptyState() {
        val transport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = searchJson(1, query = "found"),
                searchBodyByQuery = mapOf("nothing" to searchJson(0, query = "nothing"))
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val foundGeneration = coordinator.submit("found", context)
        executor.runNext()
        assertTrue(coordinator.state() is ArgentinaLiveSearchState.Results)

        val emptyGeneration = coordinator.submit("nothing", context)
        executor.runNext()

        val final = coordinator.state() as ArgentinaLiveSearchState.Empty
        assertTrue(emptyGeneration > foundGeneration)
        assertEquals(emptyGeneration, final.generation)
        assertEquals("nothing", final.query)
        assertFalse(states.any { it is ArgentinaLiveSearchState.Results && it.generation == emptyGeneration })
        coordinator.close()
    }

    @Test
    fun staleMalformedAndHttpFailuresNeverBecomeResults() {
        val stale = RecordingTransport(statusBody = statusJson(freshness = "STALE"), searchBody = searchJson(1))
        val staleStates = mutableListOf<ArgentinaLiveSearchState>()
        val staleExecutor = QueuedExecutor()
        val staleCoordinator = ArgentinaLiveSearchCoordinator(stale, staleExecutor, staleStates::add)
        staleCoordinator.submit("yerba", context)
        staleExecutor.runNext()
        assertEquals(ArgentinaBackendContract.FailureCode.STALE_RELEASE, (staleStates.last() as ArgentinaLiveSearchState.Unavailable).failure)
        staleCoordinator.close()

        val malformed = RecordingTransport(statusBody = "{not-json", searchBody = searchJson(1))
        val malformedStates = mutableListOf<ArgentinaLiveSearchState>()
        val malformedExecutor = QueuedExecutor()
        val malformedCoordinator = ArgentinaLiveSearchCoordinator(malformed, malformedExecutor, malformedStates::add)
        malformedCoordinator.submit("yerba", context)
        malformedExecutor.runNext()
        assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, (malformedStates.last() as ArgentinaLiveSearchState.Unavailable).failure)
        malformedCoordinator.close()

        val unauthorized = RecordingTransport(
            statusBody = statusJson(),
            searchBody = "",
            searchStatus = 401
        )
        val unauthorizedStates = mutableListOf<ArgentinaLiveSearchState>()
        val unauthorizedExecutor = QueuedExecutor()
        val unauthorizedCoordinator = ArgentinaLiveSearchCoordinator(unauthorized, unauthorizedExecutor, unauthorizedStates::add)
        unauthorizedCoordinator.submit("yerba", context)
        unauthorizedExecutor.runNext()
        assertEquals(ArgentinaBackendContract.FailureCode.UNAUTHORIZED, (unauthorizedStates.last() as ArgentinaLiveSearchState.Unavailable).failure)
        unauthorizedCoordinator.close()
    }

    @Test
    fun backendMustAuthorizeAndroidLiveSearchBeforeReleaseIsPinned() {
        val transport =
            RecordingTransport(
                statusBody = statusJson(androidNetworkingAuthorized = false),
                searchBody = searchJson(1)
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        coordinator.submit("yerba", context)
        executor.runNext()

        assertEquals(
            ArgentinaBackendContract.FailureCode.SEARCH_NOT_AUTHORIZED,
            (states.last() as ArgentinaLiveSearchState.Unavailable).failure
        )
        assertEquals(0, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT })
        coordinator.close()
    }

    @Test
    fun debugDiscoveryModeUsesTypedV2SearchAndStablePredicateChoices() {
        val transport = InMemoryArgentinaBackendTransport(
            mapOf(
                ArgentinaBackendConsumerBridge.STATUS_ENDPOINT to
                    ArgentinaBackendTransportResponse(200, statusJson()),
                ArgentinaBackendConsumerBridge.DISCOVERY_SEARCH_ENDPOINT to
                    ArgentinaBackendTransportResponse(200, discoveryV2ResponseJson())
            )
        )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(
            transport = transport,
            executor = executor,
            stateSink = states::add,
            discoveryMode = true
        )

        coordinator.submit("rice", context, language = "en")
        executor.runNext()

        val result = coordinator.state() as ArgentinaLiveSearchState.Results
        assertEquals(2, result.presentation.matches.size)
        assertEquals("form:rice:grain", result.presentation.clarification?.choices?.first()?.id)
        assertEquals("rice", result.presentation.clarification?.choices?.first()?.query)
        assertTrue(transport.calls.any { it.endpoint == ArgentinaBackendConsumerBridge.DISCOVERY_SEARCH_ENDPOINT })
        assertFalse(transport.calls.any { it.endpoint == ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT })
        assertTrue(transport.calls.last().requestBody?.contains("\"language\":\"en\"") == true)
        coordinator.close()
    }

    @Test
    fun debugDiscoveryModePagesClarificationChoicesThroughNativeFacetCursor() {
        val firstPage = discoveryV2ResponseJson(hasMoreFacetChoices = true, facetNextCursor = "facet-1")
        val secondPage = discoveryV2ResponseJson(hasMoreFacetChoices = false, singleFacetChoice = true)
        var discoveryCalls = 0
        val calls = mutableListOf<InMemoryArgentinaBackendTransport.Call>()
        val transport = object : ArgentinaBackendTransport {
            override fun execute(endpoint: String, requestBody: String?): ArgentinaBackendTransportResponse {
                calls += InMemoryArgentinaBackendTransport.Call(endpoint, requestBody)
                return when (endpoint) {
                    ArgentinaBackendConsumerBridge.STATUS_ENDPOINT ->
                        ArgentinaBackendTransportResponse(200, statusJson())
                    ArgentinaBackendConsumerBridge.DISCOVERY_SEARCH_ENDPOINT ->
                        ArgentinaBackendTransportResponse(200, if (discoveryCalls++ == 0) firstPage else secondPage)
                    else -> ArgentinaBackendTransportResponse(404, "")
                }
            }
        }
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(
            transport = transport,
            executor = executor,
            stateSink = states::add,
            discoveryMode = true
        )

        coordinator.submit("rice", context, language = "en")
        executor.runNext()
        val first = coordinator.state() as ArgentinaLiveSearchState.Results
        assertTrue(first.presentation.clarification?.hasMore == true)
        assertEquals("facet-1", first.presentation.clarification?.nextCursor)

        coordinator.loadMoreClarification()
        assertTrue(coordinator.state() is ArgentinaLiveSearchState.Loading)
        executor.runNext()

        val second = coordinator.state() as ArgentinaLiveSearchState.Results
        assertFalse(second.presentation.clarification?.hasMore == true)
        assertEquals(1, second.presentation.clarification?.choices?.size)
        assertTrue(
            calls.last().requestBody?.contains("\"facetCursor\":\"facet-1\"") == true
        )
        coordinator.close()
    }

    @Test
    fun facetPagingDoesNotConsumeOrRestartProductPagination() {
        for (hasMoreProducts in listOf(true, false)) {
            val first = JSONObject(discoveryV2ResponseJson(hasMoreFacetChoices = true, facetNextCursor = "facet-1"))
            first.getJSONObject("result").getJSONObject("pagination")
                .put("hasMore", hasMoreProducts)
                .put("nextCursor", if (hasMoreProducts) "product-2" else JSONObject.NULL)
            // The facet endpoint also returns a first product page. It must
            // never replace the product cursor or table already shown.
            val facet = JSONObject(discoveryV2ResponseJson(singleFacetChoice = true))
            facet.getJSONObject("result").getJSONObject("pagination")
                .put("hasMore", true).put("nextCursor", "wrong-product-cursor")
            facet.getJSONObject("result").getJSONArray("matches").getJSONObject(0)
                .put("identityKey", "ar-sepa-product:must-not-be-inserted")
            var count = 0
            val calls = mutableListOf<InMemoryArgentinaBackendTransport.Call>()
            val transport = object : ArgentinaBackendTransport {
                override fun execute(endpoint: String, requestBody: String?): ArgentinaBackendTransportResponse {
                    calls += InMemoryArgentinaBackendTransport.Call(endpoint, requestBody)
                    return ArgentinaBackendTransportResponse(200, when (endpoint) {
                        ArgentinaBackendConsumerBridge.STATUS_ENDPOINT -> statusJson()
                        else -> when (count++) {
                            0 -> first.toString()
                            1 -> facet.toString()
                            else -> discoveryV2ResponseJson()
                        }
                    })
                }
            }
            val executor = QueuedExecutor()
            val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, {}, discoveryMode = true)
            coordinator.submit("rice", context, language = "en")
            executor.runNext()
            val before = (coordinator.state() as ArgentinaLiveSearchState.Results).presentation
            coordinator.loadMoreClarification()
            executor.runNext()
            val after = (coordinator.state() as ArgentinaLiveSearchState.Results).presentation
            assertEquals(before.matches, after.matches)
            assertEquals(before.page, after.page)
            assertEquals(before.hasMore, after.hasMore)
            assertEquals(before.nextPage, after.nextPage)
            assertFalse(after.clarification?.hasMore == true)
            assertFalse(calls.last().requestBody.orEmpty().contains("product-2"))
            val previousCalls = calls.size
            coordinator.loadMore()
            if (hasMoreProducts) {
                executor.runNext()
                assertTrue(calls.last().requestBody.orEmpty().contains("product-2"))
                val final = (coordinator.state() as ArgentinaLiveSearchState.Results).presentation
                assertEquals(after.clarification, final.clarification)
            } else {
                assertEquals(previousCalls, calls.size)
            }
            coordinator.close()
        }
    }

    @Test
    fun discoveryPagesReachBeyondTenThousandRowsAndCanReturnWithoutAccumulating() {
        val requestedPages = mutableListOf<Int>()
        val transport = object : ArgentinaBackendTransport {
            override fun execute(endpoint: String, requestBody: String?): ArgentinaBackendTransportResponse {
                if (endpoint == ArgentinaBackendConsumerBridge.STATUS_ENDPOINT) {
                    return ArgentinaBackendTransportResponse(200, statusJson())
                }
                val cursor = JSONObject(requestBody!!).optString("cursor", "")
                val page = if (cursor.isEmpty() || cursor == "null") 0 else cursor.removePrefix("page-").toInt()
                requestedPages += page
                val body = JSONObject(discoveryV2ResponseJson())
                val result = body.getJSONObject("result")
                val template = result.getJSONArray("matches").getJSONObject(0)
                result.put("matches", JSONArray().apply {
                    repeat(50) { offset ->
                        val id = page * 50 + offset
                        put(JSONObject(template.toString()).put("identityKey", "ar-sepa-product:row:$id")
                            .put("providerProductId", "row-$id").put("displayName", "Producto $id"))
                    }
                })
                result.getJSONObject("total").put("productCount", 10_500).put("sourceIdentityCount", 10_500)
                result.getJSONObject("pagination").put("hasMore", page < 209)
                    .put("nextCursor", if (page < 209) "page-${page + 1}" else JSONObject.NULL)
                return ArgentinaBackendTransportResponse(200, body.toString())
            }
        }
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, {}, discoveryMode = true)
        coordinator.submit("rice", context, language = "en")
        executor.runNext()
        val seen = mutableSetOf<String>()
        repeat(210) { page ->
            val presentation = (coordinator.state() as ArgentinaLiveSearchState.Results).presentation
            assertEquals(page, presentation.page)
            assertEquals(50, presentation.matches.size)
            assertEquals(if (page == 0) null else page - 1, presentation.previousPage)
            presentation.matches.forEach { assertTrue(seen.add(it.identityKey)) }
            if (page < 209) {
                coordinator.loadMore()
                executor.runNext()
            }
        }
        assertEquals(10_500, seen.size)
        assertFalse((coordinator.state() as ArgentinaLiveSearchState.Results).presentation.hasMore)
        coordinator.loadPrevious()
        executor.runNext()
        assertEquals(208, (coordinator.state() as ArgentinaLiveSearchState.Results).presentation.page)
        assertEquals("ar-sepa-product:row:10400", (coordinator.state() as ArgentinaLiveSearchState.Results).presentation.matches.first().identityKey)
        coordinator.loadMore()
        executor.runNext()
        assertEquals(209, (coordinator.state() as ArgentinaLiveSearchState.Results).presentation.page)
        coordinator.submit("rice", context, language = "es")
        executor.runNext()
        assertEquals(0, requestedPages.last())
        assertEquals(null, (coordinator.state() as ArgentinaLiveSearchState.Results).presentation.previousPage)
        coordinator.close()
    }

    @Test
    fun discoveryModeStillStopsBeforeV2WhenAndroidSearchIsUnauthorized() {
        val transport = InMemoryArgentinaBackendTransport(
            mapOf(
                ArgentinaBackendConsumerBridge.STATUS_ENDPOINT to
                    ArgentinaBackendTransportResponse(200, statusJson(androidNetworkingAuthorized = false)),
                ArgentinaBackendConsumerBridge.DISCOVERY_SEARCH_ENDPOINT to
                    ArgentinaBackendTransportResponse(200, discoveryV2ResponseJson())
            )
        )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(
            transport = transport,
            executor = executor,
            stateSink = states::add,
            discoveryMode = true
        )

        coordinator.submit("rice", context, language = "en")
        executor.runNext()

        assertEquals(
            ArgentinaBackendContract.FailureCode.SEARCH_NOT_AUTHORIZED,
            (coordinator.state() as ArgentinaLiveSearchState.Unavailable).failure
        )
        assertFalse(transport.calls.any { it.endpoint == ArgentinaBackendConsumerBridge.DISCOVERY_SEARCH_ENDPOINT })
        coordinator.close()
    }

    @Test
    fun supersededGenerationCannotPublishAndCancellationIsRequested() {
        val transport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = searchJson(1, query = "third"),
                ignoreCancellation = true
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val first = coordinator.submit("first", context)
        val second = coordinator.submit("second", context)
        val third = coordinator.submit("third", context)
        assertTrue(second > first)
        assertTrue(third > second)
        assertEquals(3, executor.size)

        executor.runNext()
        executor.runNext()
        assertEquals(0, transport.calls.size)
        assertFalse(states.any { it is ArgentinaLiveSearchState.Results && it.generation == first })
        assertFalse(states.any { it is ArgentinaLiveSearchState.Results && it.generation == second })
        executor.runNext()
        val final = states.last() as ArgentinaLiveSearchState.Results
        assertEquals(third, final.generation)
        assertEquals("third", final.query)
        coordinator.close()
    }

    @Test
    fun inFlightOldFailureCannotReplaceNewerSuccessfulSearch() {
        val firstSearchStarted = CountDownLatch(1)
        val releaseFirstSearch = CountDownLatch(1)
        val searchQueries = mutableListOf<String>()
        val transport = object : ArgentinaBackendTransport {
            override fun execute(
                endpoint: String,
                requestBody: String?
            ): ArgentinaBackendTransportResponse =
                execute(endpoint, requestBody, ArgentinaRequestCancellation())

            override fun execute(
                endpoint: String,
                requestBody: String?,
                cancellation: ArgentinaRequestCancellation
            ): ArgentinaBackendTransportResponse =
                when (endpoint) {
                    ArgentinaBackendConsumerBridge.STATUS_ENDPOINT ->
                        ArgentinaBackendTransportResponse(200, statusJson())
                    ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT -> {
                        val query = JSONObject(requireNotNull(requestBody)).getString("query")
                        searchQueries += query
                        if (query == "first") {
                            firstSearchStarted.countDown()
                            check(releaseFirstSearch.await(3, TimeUnit.SECONDS))
                            ArgentinaBackendTransportResponse(401, "")
                        } else {
                            ArgentinaBackendTransportResponse(200, searchJson(1, query = query))
                        }
                    }
                    else -> ArgentinaBackendTransportResponse(404, "")
                }
        }
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val firstGeneration = coordinator.submit("first", context)
        val firstWorker = Thread { executor.runNext() }.apply { start() }
        assertTrue(firstSearchStarted.await(3, TimeUnit.SECONDS))

        val secondGeneration = coordinator.submit("second", context)
        executor.runNext()
        val secondResult = coordinator.state() as ArgentinaLiveSearchState.Results
        assertEquals(secondGeneration, secondResult.generation)
        assertEquals("second", secondResult.query)

        releaseFirstSearch.countDown()
        firstWorker.join(3_000)
        assertFalse(firstWorker.isAlive)
        assertEquals(listOf("first", "second"), searchQueries)
        assertEquals(secondResult, coordinator.state())
        assertFalse(states.any { it is ArgentinaLiveSearchState.Unavailable && it.generation == firstGeneration })
        assertFalse(states.any { it is ArgentinaLiveSearchState.Results && it.generation == firstGeneration })
        coordinator.close()
    }

    @Test
    fun inFlightOldSuccessCannotReplaceNewerSuccessfulSearch() {
        val firstSearchStarted = CountDownLatch(1)
        val releaseFirstSearch = CountDownLatch(1)
        val searchQueries = mutableListOf<String>()
        val transport = object : ArgentinaBackendTransport {
            override fun execute(
                endpoint: String,
                requestBody: String?
            ): ArgentinaBackendTransportResponse =
                execute(endpoint, requestBody, ArgentinaRequestCancellation())

            override fun execute(
                endpoint: String,
                requestBody: String?,
                cancellation: ArgentinaRequestCancellation
            ): ArgentinaBackendTransportResponse =
                when (endpoint) {
                    ArgentinaBackendConsumerBridge.STATUS_ENDPOINT ->
                        ArgentinaBackendTransportResponse(200, statusJson())
                    ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT -> {
                        val query = JSONObject(requireNotNull(requestBody)).getString("query")
                        searchQueries += query
                        if (query == "first") {
                            firstSearchStarted.countDown()
                            check(releaseFirstSearch.await(3, TimeUnit.SECONDS))
                        }
                        ArgentinaBackendTransportResponse(200, searchJson(1, query = query))
                    }
                    else -> ArgentinaBackendTransportResponse(404, "")
                }
        }
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val firstGeneration = coordinator.submit("first", context)
        val firstWorker = Thread { executor.runNext() }.apply { start() }
        assertTrue(firstSearchStarted.await(3, TimeUnit.SECONDS))

        val secondGeneration = coordinator.submit("second", context)
        executor.runNext()
        val secondResult = coordinator.state() as ArgentinaLiveSearchState.Results
        assertEquals(secondGeneration, secondResult.generation)
        assertEquals("second", secondResult.query)

        releaseFirstSearch.countDown()
        firstWorker.join(3_000)
        assertFalse(firstWorker.isAlive)
        assertEquals(listOf("first", "second"), searchQueries)
        assertEquals(secondResult, coordinator.state())
        assertFalse(states.any { it is ArgentinaLiveSearchState.Results && it.generation == firstGeneration })
        assertFalse(states.any { it is ArgentinaLiveSearchState.Results && it.query == "first" })
        coordinator.close()
    }

    @Test
    fun failedSearchCanBeRetriedWithoutRetainingItsErrorState() {
        var searchAttempts = 0
        val transport = object : ArgentinaBackendTransport {
            override fun execute(
                endpoint: String,
                requestBody: String?
            ): ArgentinaBackendTransportResponse =
                execute(endpoint, requestBody, ArgentinaRequestCancellation())

            override fun execute(
                endpoint: String,
                requestBody: String?,
                cancellation: ArgentinaRequestCancellation
            ): ArgentinaBackendTransportResponse =
                when (endpoint) {
                    ArgentinaBackendConsumerBridge.STATUS_ENDPOINT ->
                        ArgentinaBackendTransportResponse(200, statusJson())
                    ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT -> {
                        searchAttempts += 1
                        if (searchAttempts == 1) {
                            ArgentinaBackendTransportResponse(503, "{\"error\":\"UPSTREAM_UNAVAILABLE\"}")
                        } else {
                            val query = JSONObject(requireNotNull(requestBody)).getString("query")
                            ArgentinaBackendTransportResponse(200, searchJson(1, query = query))
                        }
                    }
                    else -> ArgentinaBackendTransportResponse(404, "")
                }
        }
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val failedGeneration = coordinator.submit("yerba", context)
        executor.runNext()
        val failure = coordinator.state() as ArgentinaLiveSearchState.Unavailable
        assertEquals(failedGeneration, failure.generation)
        assertEquals(ArgentinaBackendContract.FailureCode.SERVICE_UNAVAILABLE, failure.failure)

        val retryGeneration = coordinator.submit("yerba", context)
        assertEquals(ArgentinaLiveSearchState.Loading(retryGeneration, "yerba"), coordinator.state())
        executor.runNext()
        val retry = coordinator.state() as ArgentinaLiveSearchState.Results
        assertEquals(retryGeneration, retry.generation)
        assertEquals("yerba", retry.query)
        assertEquals(2, searchAttempts)
        assertFalse(states.any { it is ArgentinaLiveSearchState.Unavailable && it.generation == retryGeneration })
        coordinator.close()
    }

    @Test
    fun candidateReleaseResponseCannotReplaceTheStatusPinnedActiveRelease() {
        val transport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = searchJson(
                    count = 1,
                    releaseId = "argentina-sepa-2026-09-22-candidate",
                    releaseDate = "2026-09-22"
                )
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        coordinator.submit("yerba", context)
        executor.runNext()

        assertEquals(
            ArgentinaBackendContract.FailureCode.RELEASE_MISMATCH,
            (coordinator.state() as ArgentinaLiveSearchState.Unavailable).failure
        )
        assertFalse(states.any { it is ArgentinaLiveSearchState.Results })
        coordinator.close()
    }

    @Test
    fun cancellationAndTeardownStayTyped() {
        val transport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = searchJson(1),
                searchBodyByQuery = mapOf("mate" to searchJson(1, query = "mate"))
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)
        coordinator.submit("yerba", context)
        val clearedGeneration = coordinator.queryChanged("ye")
        assertEquals(ArgentinaLiveSearchState.Idle(clearedGeneration), states.last())
        executor.runNext()
        assertEquals(0, transport.calls.size)
        coordinator.close()
        val callsBeforeClose = transport.calls.size
        coordinator.submit("after-close", context)
        assertEquals(callsBeforeClose, transport.calls.size)
        assertTrue(coordinator.state() is ArgentinaLiveSearchState.Idle)
    }

    @Test
    fun routeInvalidationDropsPinnedReleaseAndForcesFreshHandshake() {
        val transport = RecordingTransport(statusBody = statusJson(), searchBody = searchJson(1))
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        coordinator.submit("yerba", context)
        executor.runNext()
        assertEquals(1, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.STATUS_ENDPOINT })

        coordinator.invalidateForRouteChange()
        assertTrue(coordinator.state() is ArgentinaLiveSearchState.Idle)
        coordinator.submit("mate", context)
        executor.runNext()
        assertEquals(2, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.STATUS_ENDPOINT })
        coordinator.close()
    }

    @Test
    fun pagedDiscoveryAppendsWithoutDuplicatesAndKeepsOlderCardsWhileLoading() {
        val transport =
            RecordingTransport(
                statusBody = statusJson(),
                searchBody = discoverySearchJson(page = 0, start = 0, count = 5, hasMore = true),
                searchBodyByPage = mapOf(
                    0 to discoverySearchJson(page = 0, start = 0, count = 5, hasMore = true),
                    1 to discoverySearchJson(page = 1, start = 5, count = 5, hasMore = false)
                )
            )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val successDiagnostics = mutableListOf<ArgentinaLiveSearchSuccessMetadata>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(
            transport = transport,
            executor = executor,
            stateSink = states::add,
            successDiagnosticSink = successDiagnostics::add
        )

        coordinator.submit("yerba", context)
        executor.runNext()
        val first = coordinator.state() as ArgentinaLiveSearchState.Results
        assertEquals(5, first.presentation.matches.size)
        assertTrue(first.presentation.hasMore)
        assertEquals(1, first.presentation.nextPage)
        assertEquals(1, successDiagnostics.size)
        assertEquals(0, successDiagnostics.single().page)
        assertEquals(5, successDiagnostics.single().pageResultCount)
        assertTrue(successDiagnostics.single().hasMore)
        assertEquals(listOf("ar-b", "ar-caba"), successDiagnostics.single().regionsQueried)

        val nextGeneration = coordinator.loadMore()
        val loading = coordinator.state() as ArgentinaLiveSearchState.Loading
        assertEquals(nextGeneration, loading.generation)
        assertEquals(5, loading.previousPresentation?.matches?.size)
        executor.runNext()

        val combined = coordinator.state() as ArgentinaLiveSearchState.Results
        assertEquals(10, combined.presentation.matches.size)
        assertEquals(10, combined.presentation.matches.map { it.identityKey }.distinct().size)
        assertFalse(combined.presentation.hasMore)
        assertEquals(2, successDiagnostics.size)
        assertEquals(1, successDiagnostics.last().page)
        assertEquals(5, successDiagnostics.last().pageResultCount)
        assertFalse(successDiagnostics.last().hasMore)
        assertTrue(transport.calls.last().body!!.contains("\"page\":1"))
        coordinator.close()
    }

    @Test
    fun pagedSearchKeepsQualifiedOfferWhenLaterPageAddsEvidenceForExistingIdentity() {
        val identityKey = "product-shared"
        val firstPage = discoverySearchJson(page = 0, start = 0, count = 1, hasMore = true)
            .let { JSONObject(it).apply {
                val matches = getJSONArray("matches")
                matches.getJSONObject(0)
                    .put("productEvidenceKey", identityKey)
            }.toString() }
        val secondPage = discoverySearchJson(page = 1, start = 1, count = 1, hasMore = false)
            .let { JSONObject(it).apply {
                val matches = getJSONArray("matches")
                val candidate = matches.getJSONObject(0)
                candidate.put("productEvidenceKey", identityKey)
                candidate.put("qualifiedOffers", JSONArray().put(
                    JSONObject()
                        .put("offerKey", "offer-shared")
                        .put("productEvidenceKey", identityKey)
                        .put("storeKey", "store-1")
                        .put("storeName", "Fixture Market")
                        .put("storeAddress", JSONObject())
                        .put("storeLatitude", "-34.60")
                        .put("storeLongitude", "-58.38")
                        .put("distanceKm", "1")
                        .put("listPrice", JSONObject().put("amount", "12.50").put("currency", "ARS"))
                        .put("referencePrice", JSONObject.NULL)
                        .put("quantity", JSONObject().put("value", "500").put("unit", "g"))
                        .put("quantityStatus", "KNOWN")
                        .put("unitPrice", JSONObject().put("amount", "2.50").put("currency", "ARS").put("basis", "100 g"))
                        .put("releaseDate", RELEASE_DATE)
                        .put("freshnessStatus", "FRESH")
                        .put("availability", "UNKNOWN")
                        .put("provenance", JSONObject()
                            .put("provider", "ARGENTINA_SEPA_PRECIOS_CLAROS")
                            .put("releaseDate", RELEASE_DATE)
                            .put("freshnessStatus", "FRESH"))
                ))
            }.toString() }
        val transport = RecordingTransport(
            statusBody = statusJson(),
            searchBody = firstPage,
            searchBodyByPage = mapOf(0 to firstPage, 1 to secondPage)
        )
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        coordinator.submit("yerba", context)
        executor.runNext()
        assertTrue((coordinator.state() as ArgentinaLiveSearchState.Results).presentation.matches.single().qualifiedOffers.isEmpty())
        coordinator.loadMore()
        executor.runNext()

        val match = (coordinator.state() as ArgentinaLiveSearchState.Results).presentation.matches.single()
        assertEquals(identityKey, match.identityKey)
        assertEquals("offer-shared", match.qualifiedOffers.single().offerKey)
        coordinator.close()
    }

    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        val size: Int
            get() = tasks.size

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() {
            check(tasks.isNotEmpty())
            tasks.removeFirst().run()
        }
    }

    private class RecordingTransport(
        private val statusBody: String,
        private val searchBody: String,
        private val searchStatus: Int = 200,
        private val ignoreCancellation: Boolean = false,
        private val searchBodyByQuery: Map<String, String> = emptyMap(),
        private val searchBodyByPage: Map<Int, String> = emptyMap()
    ) : ArgentinaBackendTransport {
        data class Call(val endpoint: String, val body: String?)

        val calls = mutableListOf<Call>()
        override fun execute(endpoint: String, requestBody: String?): ArgentinaBackendTransportResponse =
            execute(endpoint, requestBody, ArgentinaRequestCancellation())

        override fun execute(
            endpoint: String,
            requestBody: String?,
            cancellation: ArgentinaRequestCancellation
        ): ArgentinaBackendTransportResponse {
            calls += Call(endpoint, requestBody)
            if (cancellation.isCancelled()) {
                if (!ignoreCancellation) {
                    throw ArgentinaBackendTransportException(
                        ArgentinaBackendTransportError.CANCELLED,
                        "cancelled"
                    )
                }
            }
            return when (endpoint) {
                ArgentinaBackendConsumerBridge.STATUS_ENDPOINT -> ArgentinaBackendTransportResponse(200, statusBody)
                ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT -> {
                    val query = requestBody?.let { JSONObject(it).optString("query") }.orEmpty()
                    val page = requestBody?.let { JSONObject(it).optInt("page", 0) } ?: 0
                    ArgentinaBackendTransportResponse(
                        searchStatus,
                        searchBodyByPage[page] ?: searchBodyByQuery[query] ?: searchBody
                    )
                }
                else -> ArgentinaBackendTransportResponse(404, "")
            }
        }
    }

    private fun statusJson(
        freshness: String = "FRESH",
        releaseId: String = release.releaseId,
        releaseDate: String = RELEASE_DATE,
        androidNetworkingAuthorized: Boolean = true
    ): String =
        JSONObject()
            .put("apiContractVersion", ArgentinaBackendContract.API_VERSION)
            .put("service", "valuepilot-argentina-backend")
            .put("releaseId", releaseId)
            .put("releaseDate", releaseDate)
            .put("freshness", freshness)
            .put("backendCodeQualified", true)
            .put("liveCloudDeploymentVerified", true)
            .put("androidNetworkingAuthorized", androidNetworkingAuthorized)
            .put("productionAndroidUiAuthorized", false)
            .put("automatedOfficialAcquisitionProven", false)
            .toString()

    private fun searchJson(
        count: Int,
        query: String = "yerba",
        releaseId: String = release.releaseId,
        releaseDate: String = RELEASE_DATE
    ): String {
        val matches = JSONArray()
        repeat(count) { index ->
            matches.put(
                JSONObject()
                    .put("lineId", "search-$index")
                    .put("query", query)
                    .put("productCandidates", JSONArray().put(productIdentity(index)))
            )
        }
        return envelope(releaseId, releaseDate)
            .put("query", query)
            .put("originalQuery", query)
            .put("interpretedQuery", query)
            .put("resolution", "RESOLVED_EXACT")
            .put("correction", JSONObject.NULL)
            .put("matches", matches)
            .put("input", inputObject())
            .toString()
    }

    private fun discoverySearchJson(
        page: Int,
        start: Int,
        count: Int,
        hasMore: Boolean
    ): String {
        val matches = JSONArray()
        repeat(count) { offset -> matches.put(productIdentity(start + offset)) }
        return envelope(release.releaseId, RELEASE_DATE)
            .put("diagnostics", JSONObject().put("regionsQueried", JSONArray().put("ar-b").put("ar-caba")))
            .put("query", "yerba")
            .put("originalQuery", "yerba")
            .put("interpretedQuery", "yerba")
            .put("resolution", "DISCOVERY")
            .put("correction", JSONObject.NULL)
            .put("matches", matches)
            .put("input", JSONObject.NULL)
            .put(
                "pagination",
                JSONObject()
                    .put("page", page)
                    .put("pageSize", 5)
                    .put("hasMore", hasMore)
                    .put("nextPage", if (hasMore) page + 1 else JSONObject.NULL)
                    .put("limitReached", false)
            )
            .toString()
    }

    private fun discoveryV2ResponseJson(
        hasMoreFacetChoices: Boolean = false,
        facetNextCursor: String? = null,
        singleFacetChoice: Boolean = false
    ): String {
        val choices = JSONArray()
            .put(
                JSONObject()
                    .put("id", "form:rice:grain")
                    .put("predicateId", "form:rice:grain")
                    .put("labelEs", "Arroz en grano")
                    .put("labelEn", "Rice grains")
                    .put("productCount", 2)
                    .put("sourceIdentityCount", 2)
                    .put("countUnit", "DISTINCT_PROVIDER_PRODUCT")
                    .put("hasChildren", false)
            )
            .apply {
                if (!singleFacetChoice) {
                    put(
                        JSONObject()
                            .put("id", "form:rice:dessert")
                            .put("predicateId", "form:rice:dessert")
                            .put("labelEs", "Dulces de arroz")
                            .put("labelEn", "Rice cakes")
                            .put("productCount", 1)
                            .put("sourceIdentityCount", 1)
                            .put("countUnit", "DISTINCT_PROVIDER_PRODUCT")
                            .put("hasChildren", false)
                    )
                }
            }
        val result = JSONObject()
            .put("contract", ArgentinaBackendContract.DISCOVERY_RESULT_CONTRACT)
            .put("status", "NEEDS_CLARIFICATION")
            .put("query", "rice")
            .put("language", "en")
            .put("releaseId", release.releaseId)
            .put("indexVersion", "sidecar")
            .put("queryFingerprint", "fingerprint")
            .put("resolvedPredicates", JSONArray())
            .put("selections", JSONArray())
            .put(
                "total",
                JSONObject()
                    .put("productCount", 2)
                    .put("sourceIdentityCount", 2)
                    .put("countUnit", "DISTINCT_PROVIDER_PRODUCT")
            )
            .put(
                "matches",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("identityKey", "ar-sepa-product:rice:1")
                            .put("providerProductId", "rice-1")
                            .put("displayName", "ARROZ LARGO FINO")
                            .put("brand", "Marca")
                    )
                    .put(
                        JSONObject()
                            .put("identityKey", "ar-sepa-product:rice:2")
                            .put("providerProductId", "rice-2")
                            .put("displayName", "ARROZ DULCE")
                            .put("brand", "Marca")
                    )
            )
            .put("pagination", JSONObject().put("pageSize", 50).put("hasMore", false).put("nextCursor", JSONObject.NULL))
            .put(
                "clarification",
                JSONObject()
                    .put("dimensionId", "product_type")
                    .put("choices", choices)
                    .put("hasMore", hasMoreFacetChoices)
                    .put("nextCursor", facetNextCursor ?: JSONObject.NULL)
            )
        return JSONObject()
            .put("apiContractVersion", ArgentinaBackendContract.DISCOVERY_API_VERSION)
            .put("requestId", "discovery-request")
            .put("releaseId", release.releaseId)
            .put("indexVersion", "sidecar")
            .put("result", result)
            .put(
                "evidenceSemantics",
                JSONObject()
                    .put("identity", "SOURCE_IDENTITY_AND_ACCEPTED_ASSERTIONS")
                    .put("prices", "NOT_INCLUDED")
                    .put("offers", "NOT_INCLUDED")
                    .put("stock", "NOT_INCLUDED")
            )
            .toString()
    }

    private fun envelope(releaseId: String, releaseDate: String): JSONObject =
        JSONObject()
            .put("apiContractVersion", ArgentinaBackendContract.API_VERSION)
            .put("requestId", "request-search")
            .put("releaseId", releaseId)
            .put("releaseDate", releaseDate)
            .put("freshness", "FRESH")
            .put("evidenceSemantics", evidenceSemantics())
            .put("diagnostics", JSONObject().put("fixture", true))

    private fun evidenceSemantics() =
        JSONObject()
            .put("availability", "UNKNOWN")
            .put("distance", "STRAIGHT_LINE_HAVERSINE_ONLY")
            .put("promotions", "UNKNOWN_ELIGIBILITY_NOT_INCLUDED_IN_BASE_TOTAL")
            .put("pricePublicationIsNotInventory", true)

    private fun inputObject(): JSONObject {
        val line =
            JSONObject()
                .put("lineId", "input-1")
                .put("originalText", "yerba")
                .put("normalizedText", "yerba")
                .put("resolution", "NEEDS_CLARIFICATION")
                .put("recognizedProduct", JSONObject.NULL)
                .put("normalizedIntent", JSONObject().put("query", "yerba"))
                .put("quantity", JSONObject().put("status", "UNSPECIFIED").put("total", JSONObject.NULL))
                .put("clarification", JSONObject().put("code", "QUANTITY_REQUIRED").put("message", "Add a package quantity."))
                .put("suggestions", JSONArray())
                .put("structuredLine", JSONObject.NULL)
                .put("candidatesExamined", 1)
        return JSONObject()
            .put("schemaVersion", ArgentinaBackendContract.INPUT_SCHEMA_VERSION)
            .put("policyVersion", ArgentinaBackendContract.INPUT_POLICY_VERSION)
            .put("originalText", "yerba")
            .put("lineCount", 1)
            .put("lines", JSONArray().put(line))
            .put("clarifications", JSONArray().put(JSONObject().put("lineId", "input-1").put("code", "QUANTITY_REQUIRED").put("message", "Add a package quantity.")))
            .put("safeRequestReady", false)
            .put("structuredItems", JSONArray())
            .put("diagnostics", JSONObject())
            .put("safety", JSONObject().put("unknownRemainsUnknown", true).put("unsafeAutomaticSubstitutionAllowed", false).put("universalLanguageUnderstandingClaimed", false))
    }

    private fun productIdentity(index: Int) =
        JSONObject()
            .put("productEvidenceKey", "product-$index")
            .put("name", "Yerba Mate $index")
            .put("brand", "Marca")
            .put("providerProductId", "provider-$index")
            .put("gtinStatus", "UNKNOWN")
            .put("quantity", JSONObject.NULL)

    private companion object {
        const val RELEASE_DATE = "2026-09-06"
    }
}

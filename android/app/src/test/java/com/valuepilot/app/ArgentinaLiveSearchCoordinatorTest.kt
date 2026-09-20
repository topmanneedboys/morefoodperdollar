package com.valuepilot.app

import java.util.ArrayDeque
import java.util.concurrent.Executor
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
        assertEquals(ArgentinaLiveSearchState.Idle, states.last())
        assertEquals(0, transport.calls.size)
        coordinator.close()
    }

    @Test
    fun statusIsPinnedOnceAndSearchProjectsIdentityOnlyResults() {
        val transport = RecordingTransport(statusBody = statusJson(), searchBody = searchJson(2))
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
        val emptyTransport = RecordingTransport(statusBody = statusJson(), searchBody = searchJson(0))
        val emptyStates = mutableListOf<ArgentinaLiveSearchState>()
        val emptyExecutor = QueuedExecutor()
        val emptyCoordinator = ArgentinaLiveSearchCoordinator(emptyTransport, emptyExecutor, emptyStates::add)
        emptyCoordinator.submit("nothing", context)
        emptyExecutor.runNext()
        assertTrue(emptyStates.last() is ArgentinaLiveSearchState.Empty)
        emptyCoordinator.close()

        val manyTransport = RecordingTransport(statusBody = statusJson(), searchBody = searchJson(30))
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
    fun supersededGenerationCannotPublishAndCancellationIsRequested() {
        val transport = RecordingTransport(statusBody = statusJson(), searchBody = searchJson(1), ignoreCancellation = true)
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)

        val first = coordinator.submit("first", context)
        val second = coordinator.submit("second", context)
        assertTrue(second > first)
        assertEquals(2, executor.size)

        executor.runNext()
        assertEquals(0, transport.calls.size)
        assertTrue(states.none { state ->
            state is ArgentinaLiveSearchState.Results && state.generation == first
        })
        executor.runNext()
        val final = states.last() as ArgentinaLiveSearchState.Results
        assertEquals(second, final.generation)
        assertEquals("second", final.query)
        coordinator.close()
    }

    @Test
    fun cancellationAndTeardownStayTyped() {
        val transport = RecordingTransport(statusBody = statusJson(), searchBody = searchJson(1))
        val states = mutableListOf<ArgentinaLiveSearchState>()
        val executor = QueuedExecutor()
        val coordinator = ArgentinaLiveSearchCoordinator(transport, executor, states::add)
        coordinator.submit("yerba", context)
        coordinator.queryChanged("ye")
        assertEquals(ArgentinaLiveSearchState.Idle, states.last())
        executor.runNext()
        assertEquals(0, transport.calls.size)
        coordinator.close()
        val callsBeforeClose = transport.calls.size
        coordinator.submit("after-close", context)
        assertEquals(callsBeforeClose, transport.calls.size)
        assertEquals(ArgentinaLiveSearchState.Idle, coordinator.state())
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
        coordinator.submit("mate", context)
        executor.runNext()
        assertEquals(2, transport.calls.count { it.endpoint == ArgentinaBackendConsumerBridge.STATUS_ENDPOINT })
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
        private val ignoreCancellation: Boolean = false
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
                ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT -> ArgentinaBackendTransportResponse(searchStatus, searchBody)
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
        releaseId: String = release.releaseId,
        releaseDate: String = RELEASE_DATE
    ): String {
        val matches = JSONArray()
        repeat(count) { index ->
            matches.put(
                JSONObject()
                    .put("lineId", "search-$index")
                    .put("query", "yerba")
                    .put("productCandidates", JSONArray().put(productIdentity(index)))
            )
        }
        return envelope(releaseId, releaseDate)
            .put("query", "yerba")
            .put("originalQuery", "yerba")
            .put("interpretedQuery", "Yerba Mate")
            .put("resolution", "RESOLVED_EXACT")
            .put("correction", JSONObject.NULL)
            .put("matches", matches)
            .put("input", inputObject())
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

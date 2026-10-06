package com.valuepilot.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaBackendConsumerBridgeTest {

    private val release =
        ArgentinaBackendContract.ExpectedRelease(
            releaseId = "sepa-2026-09-06",
            releaseDate = RELEASE_DATE
        )

    @Test
    fun validStatusSearchInterpretAndShopUseOnlyFakeTransport() {
        val transport =
            InMemoryArgentinaBackendTransport(
                mapOf(
                    ArgentinaBackendConsumerBridge.STATUS_ENDPOINT to response(200, statusJson()),
                    ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, searchJson()),
                    ArgentinaBackendConsumerBridge.INTERPRET_ENDPOINT to response(200, inputJson(ready = false)),
                    ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to response(200, shopJson(itemCount = 1))
                )
            )
        val bridge = ArgentinaBackendConsumerBridge(transport, release)

        assertTrue(bridge.status() is ArgentinaBackendBridgeResult.Success)
        val search = bridge.searchPresentation(searchRequest())
        assertTrue(search.toString(), search is ArgentinaBackendBridgeResult.Success)
        val searchSuccess = search as ArgentinaBackendBridgeResult.Success
        assertEquals("exact barcode", searchSuccess.value.identity.matches.single().matchLabel)
        assertEquals("1 kg", searchSuccess.value.identity.matches.single().packageQuantityLabel)

        val interpreted = bridge.interpretPresentation(ArgentinaBackendContract.InterpretRequest("1 kg yerba"))
        assertTrue(interpreted is ArgentinaBackendBridgeResult.Success)
        assertFalse((interpreted as ArgentinaBackendBridgeResult.Success).value.safeRequestReady)

        val shop = bridge.shopHome(shopRequest(1))
        assertTrue(shop is ArgentinaBackendBridgeResult.Success)
        val home = (shop as ArgentinaBackendBridgeResult.Success).value.state
        assertEquals(PracticalShoppingProductionHomeStatus.READY, home.status)
        assertEquals("ARS 12.50", home.items.single().plannedPriceText)
        assertTrue(home.result?.primary?.evidenceText?.contains("availability unknown") == true)
        assertTrue(transport.calls.all { call -> call.endpoint.startsWith("/v1/") })
        assertTrue(transport.calls.all { call -> call.requestBody?.isNotBlank() ?: true })
    }

    @Test
    fun validOneFiveAndTenLineBasketsMapWithoutReplanning() {
        listOf(1, 5, 10).forEach { count ->
            val bridge =
                ArgentinaBackendConsumerBridge(
                    InMemoryArgentinaBackendTransport(
                        mapOf(
                            ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to
                                response(200, shopJson(itemCount = count))
                        )
                    ),
                    release
                )
            val result = bridge.shopHome(shopRequest(count))
            assertTrue("basket size $count", result is ArgentinaBackendBridgeResult.Success)
            val state = (result as ArgentinaBackendBridgeResult.Success).value.state
            assertEquals(count, state.items.size)
            assertEquals(count, state.result?.itemStoreAssignments?.size)
        }
    }

    @Test
    fun clarificationRequiredShopTextDoesNotExposeAPlan() {
        val response =
            shopTextJson(
                input = inputObject(ready = false).toString(),
                decision = null
            )
        val bridge =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SHOP_TEXT_ENDPOINT to response(200, response))
                ),
                release
            )

        val result = bridge.shopTextPresentation(shopTextRequest())
        assertTrue(result.toString(), result is ArgentinaBackendBridgeResult.Success)
        val value = (result as ArgentinaBackendBridgeResult.Success).value
        assertNull(value.home)
        assertFalse(value.input.safeRequestReady)
        assertTrue(value.input.notice!!.contains("Clarification required"))
    }

    @Test
    fun emptyCoverageIsReadyButTruthfullyUnknown() {
        val bridge =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(
                        ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to
                            response(200, shopJson(itemCount = 2, withPlan = false))
                    )
                ),
                release
            )
        val result = bridge.shopHome(shopRequest(2))
        assertTrue(result is ArgentinaBackendBridgeResult.Success)
        val state = (result as ArgentinaBackendBridgeResult.Success).value.state
        assertEquals(PracticalShoppingProductionHomeStatus.READY, state.status)
        assertNull(state.result!!.primary)
        assertTrue(state.items.all { it.coverageNotice!!.contains("Availability remains unknown") })
    }

    @Test
    fun twoStorePlanIsUnavailableRatherThanCollapsedIntoOneStore() {
        val bridge =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(
                        ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to
                            response(200, shopJson(itemCount = 2, planType = "TWO_STORE", storeCount = 2))
                    )
                ),
                release
            )
        val result = bridge.shopHome(shopRequest(2))
        assertTrue(result is ArgentinaBackendBridgeResult.Success)
        val state = (result as ArgentinaBackendBridgeResult.Success).value.state
        assertEquals(PracticalShoppingProductionHomeStatus.UNAVAILABLE, state.status)
        assertTrue(state.notice!!.contains("two-store"))
    }

    @Test
    fun staleReleaseAndEdgeHttpUnavailableRemainDistinct() {
        val staleTransport =
            InMemoryArgentinaBackendTransport(
                mapOf(ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to response(200, shopJson(itemCount = 1, freshness = "STALE")))
            )
        val stale = ArgentinaBackendConsumerBridge(staleTransport, release).shopHome(shopRequest(1))
        assertEquals(
            stale.toString(),
            ArgentinaBackendContract.FailureCode.STALE_RELEASE,
            (stale as ArgentinaBackendBridgeResult.Failure).code
        )

        val unavailable =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(
                        ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to
                            ArgentinaBackendTransportResponse(503, "{\"error\":\"UPSTREAM_UNAVAILABLE\"}")
                    )
                ),
                release
            ).shop(shopRequest(1))
        assertEquals(
            ArgentinaBackendContract.FailureCode.SERVICE_UNAVAILABLE,
            (unavailable as ArgentinaBackendBridgeResult.Failure).code
        )
    }

    @Test
    fun unsupportedVersionMissingFieldCurrencyAndAvailabilityFailClosed() {
        val invalidVersion = statusJson().replace(ArgentinaBackendContract.API_VERSION, "future-v2")
        val statusResult =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.STATUS_ENDPOINT to response(200, invalidVersion))
                ),
                release
            ).status()
        assertEquals(
            ArgentinaBackendContract.FailureCode.UNSUPPORTED_VERSION,
            (statusResult as ArgentinaBackendBridgeResult.Failure).code
        )

        val missing = shopJson(itemCount = 1).replace("\"availability\":\"UNKNOWN\",", "")
        val missingResult =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to response(200, missing))
                ),
                release
            ).shop(shopRequest(1))
        assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, (missingResult as ArgentinaBackendBridgeResult.Failure).code)

        val currency = shopJson(itemCount = 1).replace("\"currency\":\"ARS\"", "\"currency\":\"USD\"")
        val currencyResult =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to response(200, currency))
                ),
                release
            ).shop(shopRequest(1))
        assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, (currencyResult as ArgentinaBackendBridgeResult.Failure).code)

        val availability = shopJson(itemCount = 1).replace("\"availability\":\"UNKNOWN\"", "\"availability\":\"IN_STOCK\"")
        val availabilityResult =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to response(200, availability))
                ),
                release
            ).shop(shopRequest(1))
        assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, (availabilityResult as ArgentinaBackendBridgeResult.Failure).code)
    }

    @Test
    fun promotionConditionIsPreservedAndDoesNotChangeBasePrice() {
        val body = shopJson(itemCount = 1, promotion = true)
        val parsed = ArgentinaBackendContract.parseShop(body, release)
        val line = parsed.result.bestSensibleChoice.selectedPlan!!.lines.single()
        assertEquals("Club card required", line.promotions.single().condition)
        assertEquals("UNKNOWN", line.promotions.single().eligibility)
        assertEquals("12.50", line.lineTotal.amount)
    }

    @Test
    fun invalidGtinAndConflictingDuplicateOfferEvidenceFailClosed() {
        val invalidGtin = searchJson().replace("4006381333931", "4006381333932")
        var failed = false
        try {
            ArgentinaBackendContract.parseSearch(invalidGtin, release)
        } catch (error: ArgentinaBackendContract.ContractException) {
            failed = error.code == ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
        }
        assertTrue(failed)

        val conflict = shopJson(itemCount = 2, duplicateOfferKey = true)
        val result =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SHOP_ENDPOINT to response(200, conflict))
                ),
                release
            ).shop(shopRequest(2))
        assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, (result as ArgentinaBackendBridgeResult.Failure).code)
    }

    @Test
    fun oversizedMalformedAndReleaseMismatchResponsesAreRejected() {
        var malformed = false
        try {
            ArgentinaBackendContract.parseStatus("{not-json", release)
        } catch (error: ArgentinaBackendContract.ContractException) {
            malformed = error.code == ArgentinaBackendContract.FailureCode.CONTRACT_INVALID
        }
        assertTrue(malformed)

        val oversized = "{" + "x".repeat(ArgentinaBackendContract.MAX_RESPONSE_BYTES) + "}"
        var tooLarge = false
        try {
            ArgentinaBackendContract.parseStatus(oversized, release)
        } catch (error: ArgentinaBackendContract.ContractException) {
            tooLarge = error.code == ArgentinaBackendContract.FailureCode.RESPONSE_TOO_LARGE
        }
        assertTrue(tooLarge)

        val mismatch = statusJson(releaseId = "sepa-other")
        var releaseFailed = false
        try {
            ArgentinaBackendContract.parseStatus(mismatch, release)
        } catch (error: ArgentinaBackendContract.ContractException) {
            releaseFailed = error.code == ArgentinaBackendContract.FailureCode.RELEASE_MISMATCH
        }
        assertTrue(releaseFailed)
    }

    @Test
    fun identitySearchRefreshRejectsStaleAndSameGenerationConflict() {
        val first =
            ArgentinaBackendSearchRefreshGate.applySnapshot(
                ArgentinaBackendSearchRefreshState(),
                4L,
                identityPresentation("mate")
            )
        assertEquals(ArgentinaBackendSearchRefreshDisposition.APPLIED, first.disposition)
        val stale =
            ArgentinaBackendSearchRefreshGate.applySnapshot(
                first.state,
                3L,
                identityPresentation("old")
            )
        assertEquals(ArgentinaBackendSearchRefreshDisposition.STALE, stale.disposition)
        assertSame(first.state, stale.state)
        val conflict =
            ArgentinaBackendSearchRefreshGate.applySnapshot(
                first.state,
                4L,
                identityPresentation("different")
            )
        assertEquals(ArgentinaBackendSearchRefreshDisposition.GENERATION_CONFLICT, conflict.disposition)
        val duplicate =
            ArgentinaBackendSearchRefreshGate.applySnapshot(
                first.state,
                4L,
                identityPresentation("mate")
            )
        assertEquals(ArgentinaBackendSearchRefreshDisposition.DUPLICATE, duplicate.disposition)
        val cleared = ArgentinaBackendSearchRefreshGate.clear(first.state, 5L)
        assertEquals(ArgentinaBackendSearchRefreshDisposition.APPLIED, cleared.disposition)
        assertNull(cleared.state.presentation)
    }

    @Test
    fun discoverySearchAcceptsLiveBackendCandidateShapeAndNullInput() {
        val discoveryCandidate =
            productIdentity()
                .put("score", 100)
                .put("matchedTokens", JSONArray().put("yerba"))

        val body =
            JSONObject(searchJson())
                .put("interpretedQuery", "yerba")
                .put("resolution", "DISCOVERY")
                .put("input", JSONObject.NULL)
                .put("matches", JSONArray().put(discoveryCandidate))
                .toString()

        val parsed = ArgentinaBackendContract.parseSearch(body, release)

        assertEquals(ArgentinaBackendContract.SearchResolution.DISCOVERY, parsed.resolution)
        assertNull(parsed.input)
        assertTrue(parsed.matches.isEmpty())
        assertEquals(1, parsed.identityCandidates.size)
        assertEquals("product-yerba", parsed.identityCandidates.single().productEvidenceKey)

        val result =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(
                        ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, body)
                    )
                ),
                release
            ).searchPresentation(searchRequest())

        assertTrue(result is ArgentinaBackendBridgeResult.Success)
        val success = result as ArgentinaBackendBridgeResult.Success
        assertEquals("Yerba Mate", success.value.identity.matches.single().displayName)
    }

    @Test
    fun searchCarriesExactCurrentOfferPreviewWithoutClaimingInventory() {
        val offer = qualifiedOffer()
        val candidate = productIdentity().put("qualifiedOffers", JSONArray().put(offer))
        val body =
            JSONObject(searchJson())
                .put("resolution", "DISCOVERY")
                .put("input", JSONObject.NULL)
                .put("matches", JSONArray().put(candidate))
                .toString()

        val result =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, body))
                ),
                release
            ).searchPresentation(searchRequest())

        assertTrue(result is ArgentinaBackendBridgeResult.Success)
        val match = (result as ArgentinaBackendBridgeResult.Success).value.identity.matches.single()
        assertEquals("12.50", match.qualifiedOffers.single().listPrice.amount)
        assertEquals("1.25", match.qualifiedOffers.single().unitPrice!!.amount)
        assertEquals("100 g", match.qualifiedOffers.single().unitPriceBasis)
        assertEquals(ArgentinaBackendContract.Availability.UNKNOWN, match.qualifiedOffers.single().availability)
        assertTrue(match.qualifiedOffers.single().provenance.provider.contains("ARGENTINA_SEPA"))
        assertTrue((result.value.identity.notice).contains("listed prices"))
    }

    @Test
    fun searchRejectsStaleOrStockClaimsInQualifiedOfferPreview() {
        for ((field, value) in listOf("freshnessStatus" to "STALE", "availability" to "IN_STOCK")) {
            val offer = qualifiedOffer().put(field, value)
            val body =
                JSONObject(searchJson())
                    .put("resolution", "DISCOVERY")
                    .put("input", JSONObject.NULL)
                    .put("matches", JSONArray().put(productIdentity().put("qualifiedOffers", JSONArray().put(offer))))
                    .toString()
            val result =
                ArgentinaBackendConsumerBridge(
                    InMemoryArgentinaBackendTransport(
                        mapOf(ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, body))
                    ),
                    release
                ).searchPresentation(searchRequest())
            assertTrue("$field must be rejected", result is ArgentinaBackendBridgeResult.Failure)
            assertEquals(
                ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
                (result as ArgentinaBackendBridgeResult.Failure).code
            )
        }
    }

    @Test
    fun searchRejectsUnitPriceWhenQualifiedQuantityIsUnknown() {
        val offer = qualifiedOffer()
            .put("quantity", JSONObject.NULL)
            .put("quantityStatus", "UNKNOWN")
        val body =
            JSONObject(searchJson())
                .put("resolution", "DISCOVERY")
                .put("input", JSONObject.NULL)
                .put("matches", JSONArray().put(productIdentity().put("qualifiedOffers", JSONArray().put(offer))))
                .toString()

        val result =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, body))
                ),
                release
            ).searchPresentation(searchRequest())

        assertTrue(result is ArgentinaBackendBridgeResult.Failure)
        assertEquals(
            ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
            (result as ArgentinaBackendBridgeResult.Failure).code
        )
    }

    @Test
    fun discoverySearchAcceptsBoundedDiagnosticArraysFromLiveBackend() {
        val body =
            JSONObject(searchJson())
                .put(
                    "diagnostics",
                    JSONObject()
                        .put("regionsQueried", JSONArray().put("ar-b").put("ar-caba"))
                        .put("regionalSearchMs", 1673.376)
                )
                .put("resolution", "DISCOVERY")
                .put("input", JSONObject.NULL)
                .put("matches", JSONArray().put(productIdentity().put("score", 100)))
                .toString()

        val parsed = ArgentinaBackendContract.parseSearch(body, release)

        assertEquals("ar-b,ar-caba", parsed.common.diagnostics["regionsQueried"])
        assertEquals("1673.376", parsed.common.diagnostics["regionalSearchMs"])
        assertEquals(1, parsed.identityCandidates.size)
    }

    @Test
    fun searchIdentityWithoutQuantityRemainsExplicitlyUnknown() {
        val body =
            JSONObject(searchJson())
                .put("resolution", "DISCOVERY")
                .put("input", JSONObject.NULL)
                .put(
                    "matches",
                    JSONArray().put(
                        JSONObject(productIdentity().toString()).put("quantity", JSONObject.NULL)
                    )
                )
                .toString()

        val result =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, body))
                ),
                release
            ).searchPresentation(searchRequest())

        assertTrue(result is ArgentinaBackendBridgeResult.Success)
        val success = result as ArgentinaBackendBridgeResult.Success
        assertNull(success.value.identity.matches.single().packageQuantityLabel)
        assertTrue(success.value.identity.notice.contains("package size is shown only when explicitly supplied"))
    }

    @Test
    fun failedSearchCarriesOnlySafeCorrelationAndReleaseMetadata() {
        val malformed =
            JSONObject(searchJson())
                .put("matches", JSONObject())
                .toString()
        val result =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, malformed))
                ),
                release
            ).searchPresentation(searchRequest())

        assertTrue(result is ArgentinaBackendBridgeResult.Failure)
        val failure = result as ArgentinaBackendBridgeResult.Failure
        assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, failure.code)
        assertEquals("/v1/search", failure.metadata?.endpoint)
        assertEquals("request-search", failure.metadata?.requestId)
        assertEquals(release.releaseId, failure.metadata?.releaseId)
        assertEquals(RELEASE_DATE, failure.metadata?.releaseDate)
        assertTrue(failure.message.contains("matches"))
    }

    @Test
    fun diagnosticArraysRemainBoundedAndScalarOnly() {
        val tooMany = JSONArray().apply {
            repeat(ArgentinaBackendContract.MAX_DIAGNOSTIC_ARRAY_ITEMS + 1) { put("region-$it") }
        }
        val oversized = JSONObject(searchJson()).put(
            "diagnostics",
            JSONObject().put("regionsQueried", tooMany)
        )
        try {
            ArgentinaBackendContract.parseSearch(oversized.toString(), release)
            throw AssertionError("oversized diagnostic arrays must be rejected")
        } catch (error: ArgentinaBackendContract.ContractException) {
            assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, error.code)
        }

        val nested = JSONObject(searchJson()).put(
            "diagnostics",
            JSONObject().put("regionsQueried", JSONArray().put(JSONObject().put("region", "ar-caba")))
        )
        try {
            ArgentinaBackendContract.parseSearch(nested.toString(), release)
            throw AssertionError("nested diagnostic objects must be rejected")
        } catch (error: ArgentinaBackendContract.ContractException) {
            assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, error.code)
        }
    }

    @Test
    fun discoverySearchRejectsGroupedLegacyCandidateShape() {
        val body =
            JSONObject(searchJson())
                .put("resolution", "DISCOVERY")
                .put("input", JSONObject.NULL)
                .toString()

        try {
            ArgentinaBackendContract.parseSearch(body, release)
            throw AssertionError("grouped discovery rows must not be accepted as the live contract")
        } catch (error: ArgentinaBackendContract.ContractException) {
            assertEquals(ArgentinaBackendContract.FailureCode.CONTRACT_INVALID, error.code)
        }
    }

    @Test
    fun searchRejectsAnEchoForAnotherQueryAndDeduplicatesExactIdentityKeys() {
        val mismatchedBody =
            JSONObject(searchJson())
                .put("query", "mate")
                .put("originalQuery", "mate")
                .toString()
        val mismatch =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, mismatchedBody))
                ),
                release
            ).searchPresentation(searchRequest())
        assertTrue(mismatch is ArgentinaBackendBridgeResult.Failure)
        assertEquals(
            ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
            (mismatch as ArgentinaBackendBridgeResult.Failure).code
        )

        val first = productIdentity().put("score", 100)
        val duplicate = JSONObject(first.toString())
        val discoveryBody =
            JSONObject(searchJson())
                .put("resolution", "DISCOVERY")
                .put("input", JSONObject.NULL)
                .put("matches", JSONArray().put(first).put(duplicate))
                .toString()
        val deduplicated =
            ArgentinaBackendConsumerBridge(
                InMemoryArgentinaBackendTransport(
                    mapOf(ArgentinaBackendConsumerBridge.SEARCH_ENDPOINT to response(200, discoveryBody))
                ),
                release
            ).searchPresentation(searchRequest())

        assertTrue(deduplicated is ArgentinaBackendBridgeResult.Success)
        val presentation = (deduplicated as ArgentinaBackendBridgeResult.Success).value.identity
        assertEquals(1, presentation.evaluatedCandidateCount)
        assertEquals(1, presentation.matches.size)
    }

    private fun identityPresentation(query: String): PracticalShoppingSearchIdentityPresentation =
        PracticalShoppingSearchIdentityPresentation(
            query = query,
            matches =
                listOf(
                    PracticalShoppingSearchIdentityPresentation.Match(
                        displayName = query,
                        brand = null,
                        matchLabel = "identity"
                    )
                ),
            evaluatedCandidateCount = 1,
            notice = "Identity only"
        )

    private fun response(status: Int, body: String) = ArgentinaBackendTransportResponse(status, body)

    private fun searchRequest() =
        ArgentinaBackendContract.SearchRequest("-34.60", "-58.38", "20", "yerba")

    private fun shopRequest(count: Int) =
        ArgentinaBackendContract.ShopRequest(
            latitude = "-34.60",
            longitude = "-58.38",
            radiusKm = "20",
            items = (1..count).map { index ->
                ArgentinaBackendContract.RequestItem("line-$index", "item-$index", "1", "count", null)
            }
        )

    private fun shopTextRequest() =
        ArgentinaBackendContract.ShopTextRequest("-34.60", "-58.38", "20", "yerba")

    private fun statusJson(releaseId: String = release.releaseId): String =
        JSONObject()
            .put("apiContractVersion", ArgentinaBackendContract.API_VERSION)
            .put("service", "valuepilot-argentina-backend")
            .put("releaseId", releaseId)
            .put("releaseDate", RELEASE_DATE)
            .put("freshness", "FRESH")
            .put("backendCodeQualified", true)
            .put("liveCloudDeploymentVerified", false)
            .put("androidNetworkingAuthorized", false)
            .put("productionAndroidUiAuthorized", false)
            .put("automatedOfficialAcquisitionProven", false)
            .toString()

    private fun searchJson(): String =
        envelope("request-search", evidence = true)
            .put("regionsQueried", JSONArray().put("arg-caba"))
            .put("query", "yerba")
            .put("originalQuery", "yerba")
            .put("interpretedQuery", "Yerba Mate")
            .put("resolution", "RESOLVED_EXACT")
            .put("correction", JSONObject.NULL)
            .put(
                "matches",
                JSONArray().put(
                    JSONObject()
                        .put("lineId", "search-1")
                        .put("query", "yerba")
                        .put("productCandidates", JSONArray().put(productIdentity()))
                )
            )
            .put("input", inputObject(ready = false))
            .toString()

    private fun inputJson(ready: Boolean): String = inputObject(ready).let { value ->
        envelope("request-input", evidence = false).put("input", value).toString()
    }

    private fun shopTextJson(input: String, decision: String?): String =
        envelope("request-shop-text", evidence = true)
            .put("regionsQueried", JSONArray().put("arg-caba"))
            .put("input", JSONObject(input))
            .put("result", decision?.let(::JSONObject) ?: JSONObject.NULL)
            .put("decisionSkipped", if (decision == null) "CLARIFICATION_REQUIRED" else JSONObject.NULL)
            .toString()

    private fun shopJson(
        itemCount: Int,
        withPlan: Boolean = true,
        planType: String = "SINGLE_STORE",
        storeCount: Int = 1,
        freshness: String = "FRESH",
        promotion: Boolean = false,
        duplicateOfferKey: Boolean = false
    ): String {
        val items = (1..itemCount).map { index ->
            JSONObject()
                .put("lineId", "line-$index")
                .put("query", "item-$index")
                .put("amount", "1")
                .put("unit", "count")
        }
        val plan =
            if (!withPlan) {
                null
            } else {
                val stores = JSONArray()
                (1..storeCount).forEach { index -> stores.put(store(index)) }
                val lines = JSONArray()
                (1..itemCount).forEach { index ->
                    lines.put(planLine(index, freshness, promotion, if (duplicateOfferKey) 1 else index, if (storeCount == 2) ((index - 1) % 2) + 1 else 1))
                }
                JSONObject()
                    .put("type", planType)
                    .put("planId", "plan-$itemCount")
                    .put("storeCount", storeCount)
                    .put("storeKeys", JSONArray((1..storeCount).map { "store-$it" }))
                    .put("stores", stores)
                    .put("lines", lines)
                    .put("totalArs", money("12.50"))
                    .put("maxStraightLineDistanceKm", "1.2")
                    .put("completePriceEvidence", true)
                    .put("missingLineIds", JSONArray())
            }
        val decision =
            JSONObject()
                .put("schemaVersion", ArgentinaBackendContract.SHOPPING_SCHEMA_VERSION)
                .put("request", JSONObject().put("latitude", "-34.60").put("longitude", "-58.38").put("radiusKm", "20").put("items", JSONArray(items)))
                .put("cheapestSingleStore", plan ?: JSONObject.NULL)
                .put("closestCompletePriceEvidenceStore", plan ?: JSONObject.NULL)
                .put("cheapestTwoStoreCombination", JSONObject.NULL)
                .put("cheapestUpToTwoStores", JSONObject.NULL)
                .put("cheapestPerLineUnboundedStores", JSONObject.NULL)
                .put("decisionFrontier", JSONArray())
                .put(
                    "bestSensibleChoice",
                    JSONObject()
                        .put("status", if (plan == null) "UNRESOLVED" else "BEST_SENSIBLE_CHOICE_UNDER_POLICY")
                        .put("selectedPlan", plan ?: JSONObject.NULL)
                )
                .put("diagnostics", JSONObject().put("evaluated", itemCount))
                .put("safety", safety())
        val outer =
            JSONObject()
                .put("schemaVersion", "valuepilot-argentina-shopping-intelligence-v1")
                .put("provider", "ARGENTINA_SEPA_PRECIOS_CLAROS")
                .put("releaseDate", RELEASE_DATE)
                .put("productionUiAuthorized", false)
                .put("androidNetworkingAuthorized", false)
                .put("request", decision.getJSONObject("request"))
                .put("decision", decision)
                .put("providerSafety", safety())
        return envelope("request-shop", evidence = true)
            .put("regionsQueried", JSONArray().put("arg-caba"))
            .put("result", outer)
            .toString()
    }

    private fun envelope(requestId: String, evidence: Boolean): JSONObject =
        JSONObject()
            .put("apiContractVersion", ArgentinaBackendContract.API_VERSION)
            .put("requestId", requestId)
            .put("releaseId", release.releaseId)
            .put("releaseDate", RELEASE_DATE)
            .put("freshness", "FRESH")
            .put("evidenceSemantics", if (evidence) evidenceSemantics() else JSONObject.NULL)
            .put("diagnostics", JSONObject().put("fixture", true))

    private fun evidenceSemantics() =
        JSONObject()
            .put("availability", "UNKNOWN")
            .put("distance", "STRAIGHT_LINE_HAVERSINE_ONLY")
            .put("promotions", "UNKNOWN_ELIGIBILITY_NOT_INCLUDED_IN_BASE_TOTAL")
            .put("pricePublicationIsNotInventory", true)

    private fun inputObject(ready: Boolean): JSONObject {
        val line =
            JSONObject()
                .put("lineId", "input-1")
                .put("originalText", "1 kg yerba")
                .put("normalizedText", "yerba")
                .put("resolution", if (ready) "RESOLVED_EXACT" else "NEEDS_CLARIFICATION")
                .put("recognizedProduct", if (ready) productIdentity() else JSONObject.NULL)
                .put("normalizedIntent", JSONObject().put("query", "yerba"))
                .put("quantity", JSONObject().put("status", if (ready) "KNOWN" else "UNSPECIFIED").put("total", if (ready) quantity() else JSONObject.NULL))
                .put("clarification", if (ready) JSONObject.NULL else JSONObject().put("code", "QUANTITY_REQUIRED").put("message", "Add a package quantity."))
                .put("suggestions", JSONArray())
                .put("structuredLine", if (ready) JSONObject().put("lineId", "input-1").put("query", "yerba").put("amount", "1").put("unit", "kg") else JSONObject.NULL)
                .put("candidatesExamined", 1)
        return JSONObject()
            .put("schemaVersion", ArgentinaBackendContract.INPUT_SCHEMA_VERSION)
            .put("policyVersion", ArgentinaBackendContract.INPUT_POLICY_VERSION)
            .put("originalText", "1 kg yerba")
            .put("lineCount", 1)
            .put("lines", JSONArray().put(line))
            .put("clarifications", if (ready) JSONArray() else JSONArray().put(JSONObject().put("lineId", "input-1").put("code", "QUANTITY_REQUIRED").put("message", "Add a package quantity.")))
            .put("safeRequestReady", ready)
            .put("structuredItems", if (ready) JSONArray().put(JSONObject().put("lineId", "input-1").put("query", "yerba").put("amount", "1").put("unit", "kg")) else JSONArray())
            .put("diagnostics", JSONObject())
            .put("safety", JSONObject().put("unknownRemainsUnknown", true).put("unsafeAutomaticSubstitutionAllowed", false).put("universalLanguageUnderstandingClaimed", false))
    }

    private fun productIdentity() =
        JSONObject()
            .put("productEvidenceKey", "product-yerba")
            .put("name", "Yerba Mate")
            .put("brand", "Mate")
            .put("providerProductId", "779123")
            .put("gtin", "4006381333931")
            .put("gtinStatus", "VALID")
            .put("quantity", JSONObject().put("value", "1").put("unit", "kg"))

    private fun qualifiedOffer(
        freshness: String = "FRESH",
        availability: String = "UNKNOWN"
    ) =
        JSONObject()
            .put("offerKey", "offer-yerba")
            .put("productEvidenceKey", "product-yerba")
            .put("storeKey", "store-1")
            .put("storeName", "Mercado Central")
            .put("storeAddress", JSONObject().put("city", "Buenos Aires"))
            .put("storeLatitude", "-34.601")
            .put("storeLongitude", "-58.381")
            .put("distanceKm", "1.2")
            .put("listPrice", money("12.50"))
            .put("referencePrice", JSONObject.NULL)
            .put("quantity", JSONObject().put("value", "1").put("unit", "kg"))
            .put("quantityStatus", "KNOWN")
            .put("unitPrice", money("1.25").put("basis", "100 g"))
            .put("releaseDate", RELEASE_DATE)
            .put("freshnessStatus", freshness)
            .put("availability", availability)
            .put("promotions", JSONArray())
            .put(
                "provenance",
                JSONObject()
                    .put("provider", "ARGENTINA_SEPA_PRECIOS_CLAROS")
                    .put("releaseDate", RELEASE_DATE)
                    .put("freshnessStatus", freshness)
            )

    private fun store(index: Int) =
        JSONObject()
            .put("storeKey", "store-$index")
            .put("storeName", if (index == 1) "Mercado Central" else "Mercado Norte")
            .put("distanceKm", "1.2")
            .put("latitude", "-34.601")
            .put("longitude", "-58.381")
            .put("address", JSONObject().put("city", "Buenos Aires").put("street", "Calle 1"))

    private fun planLine(index: Int, freshness: String, promotion: Boolean, offerIndex: Int, storeIndex: Int) =
        JSONObject()
            .put("lineId", "line-$index")
            .put("productEvidenceKey", "product-$index")
            .put("productName", "Producto $index")
            .put("brand", "Marca")
            .put("offerKey", "offer-$offerIndex")
            .put("storeKey", "store-$storeIndex")
            .put("storeName", if (storeIndex == 1) "Mercado Central" else "Mercado Norte")
            .put("distanceKm", "1.2")
            .put("requestedQuantity", quantity())
            .put("packageQuantity", quantity())
            .put("packageCount", 1)
            .put("suppliedQuantity", quantity())
            .put("excessQuantity", quantity(allowZero = true))
            .put("lineTotal", money("12.50"))
            .put("packagePrice", money("12.50"))
            .put("availability", "UNKNOWN")
            .put("freshnessStatus", freshness)
            .put("promotions", if (promotion) JSONArray().put(JSONObject().put("eligibility", "UNKNOWN").put("raw", JSONObject().put("condition", "Club card required").put("price", money("10.00")))) else JSONArray())
            .put("provenance", JSONObject().put("provider", "ARGENTINA_SEPA_PRECIOS_CLAROS").put("releaseDate", RELEASE_DATE).put("freshnessStatus", freshness).put("source", "Precios Claros"))

    private fun quantity(allowZero: Boolean = false) =
        JSONObject()
            .put("dimension", "COUNT")
            .put("amount", if (allowZero) "0" else "1")
            .put("unit", "count")
            .put("baseAmount", if (allowZero) "0" else "1")
            .put("baseUnit", "count")

    private fun money(amount: String) = JSONObject().put("amount", amount).put("currency", "ARS")

    private fun safety() =
        JSONObject()
            .put("availability", "UNKNOWN")
            .put("distance", "STRAIGHT_LINE_HAVERSINE_ONLY")
            .put("routing", "NOT_PROVIDED")
            .put("completePriceEvidenceIsNotInventory", true)

    private companion object {
        const val RELEASE_DATE = "2026-09-06"
    }
}

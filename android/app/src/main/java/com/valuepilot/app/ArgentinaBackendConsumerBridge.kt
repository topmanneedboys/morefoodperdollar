package com.valuepilot.app

import com.valuepilot.core.ShoppingItemKey
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provider-neutral transport boundary used by the M12 qualification seam.
 *
 * Tests and local previews use [InMemoryArgentinaBackendTransport]. The
 * production HTTPS implementation lives in its own application-edge file so
 * this parser remains provider-neutral and easy to exercise without a network.
 */
internal data class ArgentinaBackendTransportResponse(
    val statusCode: Int,
    val body: String
) {
    init {
        require(statusCode in 100..599)
    }
}

internal fun interface ArgentinaBackendTransport {
    fun execute(endpoint: String, requestBody: String?): ArgentinaBackendTransportResponse
}

/** Deterministic fake transport for contract and presentation tests. */
internal class InMemoryArgentinaBackendTransport(
    responses: Map<String, ArgentinaBackendTransportResponse>
) : ArgentinaBackendTransport {
    data class Call(val endpoint: String, val requestBody: String?)

    private val responses = responses.toMap()
    private val recordedCalls = mutableListOf<Call>()

    val calls: List<Call>
        get() = recordedCalls.toList()

    override fun execute(
        endpoint: String,
        requestBody: String?
    ): ArgentinaBackendTransportResponse {
        recordedCalls += Call(endpoint, requestBody)
        return responses[endpoint]
            ?: ArgentinaBackendTransportResponse(statusCode = 404, body = "")
    }
}

internal sealed class ArgentinaBackendBridgeResult<out T> {
    data class Success<T>(val value: T) : ArgentinaBackendBridgeResult<T>()

    data class Failure(
        val code: ArgentinaBackendContract.FailureCode,
        val message: String
    ) : ArgentinaBackendBridgeResult<Nothing>()
}

/** One identity-only search response ready for the existing Search seam. */
internal data class ArgentinaBackendSearchPresentation(
    val response: ArgentinaBackendContract.SearchResponse,
    val identity: PracticalShoppingSearchIdentityPresentation
)

internal data class ArgentinaBackendInputLinePresentation(
    val lineId: String,
    val text: String,
    val resolution: ArgentinaBackendContract.Resolution,
    val quantityStatus: String,
    val clarification: String?
)

/**
 * Small presentation projection for interpret/shop-text. It carries no
 * provider keys or price authority; clarification is explicit when the input
 * is not safe to turn into a shopping request.
 */
internal data class ArgentinaBackendInputPresentation(
    val originalText: String,
    val lines: List<ArgentinaBackendInputLinePresentation>,
    val safeRequestReady: Boolean,
    val notice: String?
) {
    init {
        require(originalText.isNotBlank())
        require(lines.isNotEmpty())
        require(lines.size <= ArgentinaBackendContract.MAX_LINES)
        require(notice == null || notice.isNotBlank())
    }
}

internal data class ArgentinaBackendShopTextPresentation(
    val response: ArgentinaBackendContract.ShopTextResponse,
    val input: ArgentinaBackendInputPresentation,
    val home: ArgentinaBackendHomeProjection?
)

/** The only Home state produced by the offline consumer bridge. */
internal data class ArgentinaBackendHomeProjection(
    val releaseId: String,
    val releaseDate: String,
    val freshness: ArgentinaBackendContract.Freshness,
    val provider: String,
    val diagnostics: Map<String, String>,
    val state: PracticalShoppingProductionHomeUiState
)

/**
 * Offline consumer bridge for the already-qualified M11 contract.
 *
 * This class parses untrusted transport responses and performs only display
 * mapping. It never ranks stores, recomputes totals, resolves products, or
 * creates a live transport. The shared-core planner remains authoritative for
 * its own production path; this bridge does not run it a second time for a
 * backend decision.
 */
internal class ArgentinaBackendConsumerBridge(
    private val transport: ArgentinaBackendTransport,
    private val expectedRelease: ArgentinaBackendContract.ExpectedRelease
) {

    fun status(): ArgentinaBackendBridgeResult<ArgentinaBackendContract.StatusResponse> =
        execute(
            endpoint = STATUS_ENDPOINT,
            requestBody = null,
            parser = { body -> ArgentinaBackendContract.parseStatus(body, expectedRelease) }
        )

    fun search(
        request: ArgentinaBackendContract.SearchRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendContract.SearchResponse> =
        execute(
            endpoint = SEARCH_ENDPOINT,
            requestBody = ArgentinaBackendJson.searchRequest(request),
            parser = { body -> ArgentinaBackendContract.parseSearch(body, expectedRelease) }
        )

    fun searchPresentation(
        request: ArgentinaBackendContract.SearchRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendSearchPresentation> =
        mapResult(search(request)) { response ->
            ArgentinaBackendSearchPresentation(
                response = response,
                identity = ArgentinaBackendIdentityMapper.toPresentation(response)
            )
        }

    fun interpret(
        request: ArgentinaBackendContract.InterpretRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendContract.InterpretResponse> =
        execute(
            endpoint = INTERPRET_ENDPOINT,
            requestBody = ArgentinaBackendJson.interpretRequest(request),
            parser = { body -> ArgentinaBackendContract.parseInterpret(body, expectedRelease) }
        )

    fun interpretPresentation(
        request: ArgentinaBackendContract.InterpretRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendInputPresentation> =
        mapResult(interpret(request)) { response ->
            ArgentinaBackendInputMapper.toPresentation(response.input)
        }

    fun shopText(
        request: ArgentinaBackendContract.ShopTextRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendContract.ShopTextResponse> =
        execute(
            endpoint = SHOP_TEXT_ENDPOINT,
            requestBody = ArgentinaBackendJson.shopTextRequest(request),
            parser = { body ->
                val response = ArgentinaBackendContract.parseShopText(body, expectedRelease)
                if (response.result != null && response.common.freshness != ArgentinaBackendContract.Freshness.FRESH) {
                    throw ArgentinaBackendContract.ContractException(
                        ArgentinaBackendContract.FailureCode.STALE_RELEASE,
                        "shop-text response is not fresh enough for a current price plan"
                    )
                }
                response
            }
        )

    fun shopTextPresentation(
        request: ArgentinaBackendContract.ShopTextRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendShopTextPresentation> =
        mapResult(shopText(request)) { response ->
            ArgentinaBackendShopTextPresentation(
                response = response,
                input = ArgentinaBackendInputMapper.toPresentation(response.input),
                home = response.result?.let { result ->
                    ArgentinaBackendHomeMapper.toProjection(response.common, result)
                }
            )
        }

    fun shop(
        request: ArgentinaBackendContract.ShopRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendContract.ShopResponse> =
        execute(
            endpoint = SHOP_ENDPOINT,
            requestBody = ArgentinaBackendJson.shopRequest(request),
            parser = { body ->
                val response = ArgentinaBackendContract.parseShop(body, expectedRelease)
                if (response.common.freshness != ArgentinaBackendContract.Freshness.FRESH) {
                    throw ArgentinaBackendContract.ContractException(
                        ArgentinaBackendContract.FailureCode.STALE_RELEASE,
                        "shop response is not fresh enough for a current price plan"
                    )
                }
                response
            }
        )

    fun shopHome(
        request: ArgentinaBackendContract.ShopRequest
    ): ArgentinaBackendBridgeResult<ArgentinaBackendHomeProjection> =
        mapResult(shop(request)) { response -> ArgentinaBackendHomeMapper.toProjection(response) }

    private fun <T> execute(
        endpoint: String,
        requestBody: String?,
        parser: (String) -> T
    ): ArgentinaBackendBridgeResult<T> {
        if (requestBody != null && requestBody.toByteArray(StandardCharsets.UTF_8).size > MAX_REQUEST_BYTES) {
            return ArgentinaBackendBridgeResult.Failure(
                ArgentinaBackendContract.FailureCode.REQUEST_TOO_LARGE,
                "backend request exceeds the bounded byte limit"
            )
        }

        val response =
            try {
                transport.execute(endpoint, requestBody)
            } catch (error: ArgentinaBackendTransportException) {
                return ArgentinaBackendBridgeResult.Failure(
                    error.failureCode,
                    error.safeMessage
                )
            } catch (error: Exception) {
                return ArgentinaBackendBridgeResult.Failure(
                    ArgentinaBackendContract.FailureCode.HTTP_ERROR,
                    "backend transport failed"
                )
            }

        if (response.statusCode != HTTP_OK) {
            val code =
                when (response.statusCode) {
                    HTTP_BAD_REQUEST -> ArgentinaBackendContract.FailureCode.HTTP_BAD_REQUEST
                    HTTP_UNAUTHORIZED -> ArgentinaBackendContract.FailureCode.UNAUTHORIZED
                    HTTP_FORBIDDEN -> ArgentinaBackendContract.FailureCode.FORBIDDEN
                    HTTP_NOT_FOUND -> ArgentinaBackendContract.FailureCode.NOT_FOUND
                    HTTP_REQUEST_TIMEOUT -> ArgentinaBackendContract.FailureCode.TIMEOUT
                    HTTP_REQUEST_TOO_LARGE -> ArgentinaBackendContract.FailureCode.RESPONSE_TOO_LARGE
                    HTTP_TOO_MANY_REQUESTS -> ArgentinaBackendContract.FailureCode.RATE_LIMITED
                    HTTP_SERVICE_UNAVAILABLE -> ArgentinaBackendContract.FailureCode.CURRENT_PRICE_UNAVAILABLE
                    in HTTP_SERVER_ERROR..HTTP_SERVER_ERROR_LAST -> ArgentinaBackendContract.FailureCode.SERVER_ERROR
                    else -> ArgentinaBackendContract.FailureCode.HTTP_ERROR
                }
            return ArgentinaBackendBridgeResult.Failure(code, "backend returned HTTP ${response.statusCode}")
        }

        return try {
            ArgentinaBackendBridgeResult.Success(parser(response.body))
        } catch (error: ArgentinaBackendContract.ContractException) {
            ArgentinaBackendBridgeResult.Failure(error.code, error.message ?: "backend contract is invalid")
        } catch (error: IllegalArgumentException) {
            ArgentinaBackendBridgeResult.Failure(
                ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
                error.message ?: "backend contract is invalid"
            )
        } catch (error: Exception) {
            ArgentinaBackendBridgeResult.Failure(
                ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
                error.message ?: "backend contract is invalid"
            )
        }
    }

    private fun <A, B> mapResult(
        result: ArgentinaBackendBridgeResult<A>,
        mapper: (A) -> B
    ): ArgentinaBackendBridgeResult<B> =
        when (result) {
            is ArgentinaBackendBridgeResult.Success ->
                try {
                    ArgentinaBackendBridgeResult.Success(mapper(result.value))
                } catch (error: ArgentinaBackendContract.ContractException) {
                    ArgentinaBackendBridgeResult.Failure(error.code, error.message ?: "backend contract is invalid")
                } catch (error: IllegalArgumentException) {
                    ArgentinaBackendBridgeResult.Failure(
                        ArgentinaBackendContract.FailureCode.CONTRACT_INVALID,
                        error.message ?: "backend presentation is invalid"
                    )
                }

            is ArgentinaBackendBridgeResult.Failure -> result
        }

    companion object {
        const val STATUS_ENDPOINT = "/v1/status"
        const val SEARCH_ENDPOINT = "/v1/search"
        const val INTERPRET_ENDPOINT = "/v1/interpret"
        const val SHOP_TEXT_ENDPOINT = "/v1/shop-text"
        const val SHOP_ENDPOINT = "/v1/shop"

        private const val HTTP_OK = 200
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_REQUEST_TOO_LARGE = 413
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private const val HTTP_SERVER_ERROR = 500
        private const val HTTP_SERVER_ERROR_LAST = 599
        private const val MAX_REQUEST_BYTES = 16 * 1024
    }
}

private object ArgentinaBackendJson {
    fun searchRequest(request: ArgentinaBackendContract.SearchRequest): String =
        JSONObject()
            .put("latitude", request.latitude)
            .put("longitude", request.longitude)
            .put("radiusKm", request.radiusKm)
            .put("query", request.query)
            .toString()

    fun interpretRequest(request: ArgentinaBackendContract.InterpretRequest): String =
        JSONObject().put("text", request.text).toString()

    fun shopTextRequest(request: ArgentinaBackendContract.ShopTextRequest): String =
        JSONObject()
            .put("latitude", request.latitude)
            .put("longitude", request.longitude)
            .put("radiusKm", request.radiusKm)
            .put("text", request.text)
            .toString()

    fun shopRequest(request: ArgentinaBackendContract.ShopRequest): String =
        JSONObject()
            .put("latitude", request.latitude)
            .put("longitude", request.longitude)
            .put("radiusKm", request.radiusKm)
            .put("items", JSONArray(request.items.map(::requestItem)))
            .toString()

    private fun requestItem(item: ArgentinaBackendContract.RequestItem): JSONObject =
        JSONObject()
            .put("lineId", item.lineId)
            .put("query", item.query)
            .put("amount", item.amount)
            .put("unit", item.unit)
}

private object ArgentinaBackendInputMapper {
    fun toPresentation(
        input: ArgentinaBackendContract.InputResult
    ): ArgentinaBackendInputPresentation {
        val lines =
            input.lines.map { line ->
                ArgentinaBackendInputLinePresentation(
                    lineId = line.lineId,
                    text = line.originalText,
                    resolution = line.resolution,
                    quantityStatus = line.quantityStatus,
                    clarification = line.clarification?.message ?: line.clarification?.code?.name
                )
            }
        val notice =
            if (input.safeRequestReady) {
                null
            } else {
                val details = input.clarifications.mapNotNull { it.message ?: it.code.name }
                if (details.isEmpty()) {
                    "Clarification is required before ValuePilot can plan this list."
                } else {
                    "Clarification required: ${details.joinToString("; ")}."
                }
            }
        return ArgentinaBackendInputPresentation(
            originalText = input.originalText,
            lines = lines,
            safeRequestReady = input.safeRequestReady,
            notice = notice
        )
    }
}

private object ArgentinaBackendIdentityMapper {
    private const val NOTICE_PREFIX =
        "Argentina SEPA / Precios Claros identity suggestions from the qualified release"

    fun toPresentation(
        response: ArgentinaBackendContract.SearchResponse
    ): PracticalShoppingSearchIdentityPresentation {
        val byKey = linkedMapOf<String, PracticalShoppingSearchIdentityPresentation.Match>()
        response.matches.forEach { match ->
            match.productCandidates.forEach { candidate ->
                if (!byKey.containsKey(candidate.productEvidenceKey)) {
                    byKey[candidate.productEvidenceKey] =
                        PracticalShoppingSearchIdentityPresentation.Match(
                            displayName = candidate.name,
                            brand = candidate.brand,
                            matchLabel =
                                if (candidate.gtinStatus == "VALID") {
                                    "exact barcode"
                                } else {
                                    "provider identity match"
                                }
                        )
                }
            }
        }
        val matches = byKey.values.take(24)
        val evaluated = response.matches.sumOf { match -> match.productCandidates.size }
        return PracticalShoppingSearchIdentityPresentation(
            query = response.originalQuery.trim(),
            matches = matches,
            evaluatedCandidateCount = maxOf(evaluated, matches.size),
            notice = "$NOTICE_PREFIX ${response.common.releaseDate} only — no current prices, stock, package quantity or freshness are implied."
        )
    }
}

private object ArgentinaBackendHomeMapper {
    private const val PROVIDER = "ARGENTINA_SEPA_PRECIOS_CLAROS"
    private const val MAX_DISPLAY_CHARS = 160

    fun toProjection(
        response: ArgentinaBackendContract.ShopResponse
    ): ArgentinaBackendHomeProjection {
        return toProjection(response.common, response.result)
    }

    fun toProjection(
        common: ArgentinaBackendContract.CommonEnvelope,
        result: ArgentinaBackendContract.ShoppingDecision
    ): ArgentinaBackendHomeProjection {
        val state = toState(common, result)
        return ArgentinaBackendHomeProjection(
            releaseId = common.releaseId,
            releaseDate = common.releaseDate,
            freshness = common.freshness,
            provider = PROVIDER,
            diagnostics = common.diagnostics,
            state = state
        )
    }

    private fun toState(
        common: ArgentinaBackendContract.CommonEnvelope,
        decision: ArgentinaBackendContract.ShoppingDecision
    ): PracticalShoppingProductionHomeUiState {
        val requestItems = decision.request.items
        val itemNames = requestItems.associate { item -> item.lineId to safeDisplay(item.query, "request item") }
        val selected = decision.bestSensibleChoice.selectedPlan

        if (common.freshness != ArgentinaBackendContract.Freshness.FRESH) {
            return unavailable("Current Argentina price evidence is stale and is not shown as a current plan.")
        }
        if (selected == null) {
            return noCoverageState(requestItems, itemNames)
        }
        if (selected.type !in setOf("SINGLE_STORE", "REFERENCE_LOWER_BOUND") || selected.storeCount != 1) {
            return unavailable("This result needs a dedicated two-store presentation before Home can show it safely.")
        }

        val requestIds = requestItems.map { it.lineId }
        val requestIdSet = requestIds.toSet()
        val linesById = selected.lines.associateBy { it.lineId }
        require(linesById.size == selected.lines.size) { "backend plan contains duplicate line ids" }
        require(linesById.keys.all(requestIdSet::contains)) { "backend plan contains an unknown request line" }
        val missingIds = selected.missingLineIds.toSet()
        require(missingIds.size == selected.missingLineIds.size) { "backend plan contains duplicate missing line ids" }
        require(missingIds.all(requestIdSet::contains)) { "backend plan contains an unknown missing line" }
        require(linesById.keys.intersect(missingIds).isEmpty()) { "backend plan line is also marked missing" }
        require(linesById.keys + missingIds == requestIdSet) { "backend plan does not account for every request line" }
        require(selected.completePriceEvidence == missingIds.isEmpty()) { "backend completeness disagrees with missing lines" }

        val storesByKey = selected.stores.associateBy { it.storeKey }
        require(storesByKey.size == selected.stores.size) { "backend plan contains duplicate stores" }
        val store = requireNotNull(storesByKey[selected.storeKeys.single()])
        selected.lines.forEach { line ->
            require(line.storeKey == store.storeKey) { "single-store plan line uses another store" }
            if (line.freshnessStatus != ArgentinaBackendContract.Freshness.FRESH) {
                throw ArgentinaBackendContract.ContractException(
                    ArgentinaBackendContract.FailureCode.STALE_RELEASE,
                    "stale plan line cannot be shown as a current price"
                )
            }
        }

        val complete = selected.completePriceEvidence && missingIds.isEmpty()
        val missingNames = requestIds.filter(missingIds::contains).map(itemNames::getValue)
        val primary =
            PracticalShoppingPrimaryUiState(
                badge =
                    when {
                        selected.type == "REFERENCE_LOWER_BOUND" -> "REFERENCE LOWER BOUND"
                        complete -> "BACKEND-SELECTED ONE-STORE OPTION"
                        else -> "BEST COVERAGE FOUND"
                    },
                storeName = safeDisplay(store.storeName, "store name"),
                basketCostText = moneyText(selected.total),
                coverageText = "${linesById.size} of ${requestItems.size} items priced",
                missingItemsText =
                    if (missingNames.isEmpty()) null else "Missing prices: ${missingNames.joinToString(", ")}",
                travelText =
                    "Up to ${selected.maxStraightLineDistanceKm} km straight-line distance; route not provided.",
                evidenceText = evidenceText(common),
                whyText =
                    when {
                        selected.type == "REFERENCE_LOWER_BOUND" ->
                            "This is a backend reference lower bound, not a confirmed cheapest basket."
                        complete ->
                            "The qualified backend selected this single-store plan from current price evidence."
                        else ->
                            "No complete basket is priced yet; this plan shows only the requested items with usable prices."
                    },
                notice =
                    if (missingNames.isEmpty()) null
                    else incompleteNotice(missingNames.size),
                hasPriceFreshnessCaution = false,
                freshnessNotice = null
            )

        val assignments =
            requestIds.mapNotNull { lineId ->
                linesById[lineId]?.let { line ->
                    PracticalShoppingItemStoreAssignmentUiState(
                        itemKey = ShoppingItemKey(line.lineId),
                        storeName = safeDisplay(store.storeName, "store name"),
                        priceText = moneyText(line.lineTotal)
                    )
                }
            }
        val rows =
            requestIds.map { lineId ->
                val line = linesById[lineId]
                PracticalShoppingProductionHomeItemUiState(
                    itemKey = ShoppingItemKey(lineId),
                    name = itemNames.getValue(lineId),
                    storeAssignment = line?.let { safeDisplay(store.storeName, "store name") },
                    plannedPriceText = line?.lineTotal?.let(::moneyText),
                    coverageNotice =
                        if (line == null) {
                            "No current price evidence for this item. Availability remains unknown."
                        } else {
                            null
                        }
                )
            }
        val result =
            PracticalShoppingUiState(
                headline = if (complete) "Your best practical shop" else "Best option with the prices we know",
                primary = primary,
                secondStop = null,
                secondaryMessage =
                    "Published price evidence does not establish live shelf inventory; availability is unknown.",
                itemStoreAssignments = assignments
            )
        return PracticalShoppingProductionHomeUiState(
            status = PracticalShoppingProductionHomeStatus.READY,
            items = rows,
            result = result,
            notice = null
        )
    }

    private fun noCoverageState(
        requestItems: List<ArgentinaBackendContract.RequestItem>,
        itemNames: Map<String, String>
    ): PracticalShoppingProductionHomeUiState {
        val rows =
            requestItems.map { item ->
                PracticalShoppingProductionHomeItemUiState(
                    itemKey = ShoppingItemKey(item.lineId),
                    name = itemNames.getValue(item.lineId),
                    coverageNotice =
                        "No current price evidence for this item. Availability remains unknown."
                )
            }
        return PracticalShoppingProductionHomeUiState(
            status = PracticalShoppingProductionHomeStatus.READY,
            items = rows,
            result =
                PracticalShoppingUiState(
                    headline = "Not enough price coverage yet",
                    primary = null,
                    secondStop = null,
                    secondaryMessage =
                        "No current price evidence could be used for this request. Availability remains unknown.",
                    itemStoreAssignments = emptyList()
                ),
            notice = null
        )
    }

    private fun unavailable(message: String): PracticalShoppingProductionHomeUiState =
        PracticalShoppingProductionHomeUiState(
            status = PracticalShoppingProductionHomeStatus.UNAVAILABLE,
            items = emptyList(),
            result = null,
            notice = message
        )

    private fun evidenceText(common: ArgentinaBackendContract.CommonEnvelope): String =
        "Source: Argentina SEPA / Precios Claros · release ${common.releaseDate} · ${common.freshness.name} · availability unknown; promotions are not included unless eligibility is known."

    private fun moneyText(money: ArgentinaBackendContract.Money): String =
        "${money.currency} ${money.amount}"

    private fun incompleteNotice(count: Int): String =
        if (count == 1) {
            "1 item still has an unknown price. This is not a complete basket total."
        } else {
            "$count items still have unknown prices. This is not a complete basket total."
        }

    private fun safeDisplay(value: String, label: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "$label is blank" }
        require(normalized.length <= MAX_DISPLAY_CHARS) { "$label is too long for Home" }
        require(normalized.none(Char::isISOControl)) { "$label contains a control character" }
        return normalized
    }
}

/** Search ordering gate for identity-only backend responses. */
internal data class ArgentinaBackendSearchRefreshState(
    val latestGeneration: Long? = null,
    val presentation: PracticalShoppingSearchIdentityPresentation? = null
) {
    init {
        latestGeneration?.let { require(it >= 0L) }
        require(latestGeneration != null || presentation == null)
    }
}

internal enum class ArgentinaBackendSearchRefreshDisposition {
    APPLIED,
    DUPLICATE,
    STALE,
    GENERATION_CONFLICT
}

internal data class ArgentinaBackendSearchRefreshDecision(
    val disposition: ArgentinaBackendSearchRefreshDisposition,
    val state: ArgentinaBackendSearchRefreshState
)

/**
 * Same generation semantics as the existing production Search gate, kept
 * separate because identity-only backend results are not priced core evidence
 * and must not be forced through a ranking projection.
 */
internal object ArgentinaBackendSearchRefreshGate {
    fun applySnapshot(
        current: ArgentinaBackendSearchRefreshState,
        incomingGeneration: Long,
        presentation: PracticalShoppingSearchIdentityPresentation
    ): ArgentinaBackendSearchRefreshDecision {
        require(incomingGeneration >= 0L)
        val currentGeneration = current.latestGeneration
        if (currentGeneration != null && incomingGeneration < currentGeneration) {
            return ArgentinaBackendSearchRefreshDecision(
                ArgentinaBackendSearchRefreshDisposition.STALE,
                current
            )
        }
        if (currentGeneration == incomingGeneration) {
            return ArgentinaBackendSearchRefreshDecision(
                if (current.presentation == presentation) {
                    ArgentinaBackendSearchRefreshDisposition.DUPLICATE
                } else {
                    ArgentinaBackendSearchRefreshDisposition.GENERATION_CONFLICT
                },
                current
            )
        }
        return ArgentinaBackendSearchRefreshDecision(
            ArgentinaBackendSearchRefreshDisposition.APPLIED,
            ArgentinaBackendSearchRefreshState(incomingGeneration, presentation)
        )
    }

    fun clear(
        current: ArgentinaBackendSearchRefreshState,
        incomingGeneration: Long
    ): ArgentinaBackendSearchRefreshDecision {
        require(incomingGeneration >= 0L)
        val currentGeneration = current.latestGeneration
        if (currentGeneration != null && incomingGeneration < currentGeneration) {
            return ArgentinaBackendSearchRefreshDecision(
                ArgentinaBackendSearchRefreshDisposition.STALE,
                current
            )
        }
        if (currentGeneration == incomingGeneration) {
            return ArgentinaBackendSearchRefreshDecision(
                if (current.presentation == null) {
                    ArgentinaBackendSearchRefreshDisposition.DUPLICATE
                } else {
                    ArgentinaBackendSearchRefreshDisposition.GENERATION_CONFLICT
                },
                current
            )
        }
        return ArgentinaBackendSearchRefreshDecision(
            ArgentinaBackendSearchRefreshDisposition.APPLIED,
            ArgentinaBackendSearchRefreshState(incomingGeneration, null)
        )
    }
}

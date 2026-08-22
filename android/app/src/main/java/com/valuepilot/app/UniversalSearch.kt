package com.valuepilot.app

import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.ShoppingEvidence

enum class UniversalSearchStatus {
    IDLE,
    READY,
    QUERY_TOO_LONG,
    LOADING,
    RESULTS,
    NO_RESULTS,
    MIXED_CURRENCIES,
    PROVIDER_ERROR
}

/**
 * Provider-facing request.
 *
 * Providers are replaceable adapters. A provider may be local, fixture-backed,
 * authorized remote data, or another future source. It does not own ranking.
 */
data class ProductSearchRequest(
    val requestId: Long,
    val query: String,
    val maxObservations: Int
) {
    init {
        require(requestId > 0L)
        require(query.isNotBlank())
        require(
            maxObservations in
                1..UniversalSearchController.MAX_PROVIDER_OBSERVATIONS
        )
    }
}

/**
 * A provider returns typed shopping evidence, never pre-ranked ValuePilot
 * results.
 *
 * requestId must be copied from the corresponding ProductSearchRequest.
 * Providers describe provenance; they never decide ValuePilot rank.
 */
data class ProductSearchBatch(
    val requestId: Long,
    val evidence: List<ShoppingEvidence>
)

/**
 * Replaceable search-data adapter.
 *
 * Expensive implementations must be invoked away from the Android main thread.
 * Providers should honor request.maxObservations.
 */
fun interface ProductSearchProvider {
    fun search(
        request: ProductSearchRequest
    ): ProductSearchBatch
}

/**
 * Immutable presentation-ready search result.
 *
 * No Android classes are exposed here.
 */
data class UniversalSearchRow(
    val stableId: Long,
    val rank: Int,
    val name: String,
    val quantity: String?,
    val priceSummary: String,
    val metricLabel: String,
    val exactnessLabel: String,
    val best: Boolean,
    val sourceSummary: String,
    val sampleEvidence: Boolean
)

data class UniversalSearchState(
    val query: String,
    val status: UniversalSearchStatus,
    val statusText: String,
    val activeRequestId: Long?,
    val nextRequestId: Long,
    val receivedObservationCount: Int,
    val parsedProductCount: Int,
    val matchedProductCount: Int,
    val rejectedObservationCount: Int,
    val results: List<UniversalSearchRow>
)

sealed interface UniversalSearchIntent {

    data class QueryChanged(
        val rawQuery: String
    ) : UniversalSearchIntent

    data object Submit :
        UniversalSearchIntent

    data class ResultsReceived(
        val batch: ProductSearchBatch
    ) : UniversalSearchIntent

    data class ProviderFailed(
        val requestId: Long
    ) : UniversalSearchIntent

    data object Clear :
        UniversalSearchIntent
}

/**
 * Search transitions can optionally emit a provider request.
 *
 * The controller itself never performs network or filesystem work.
 */
data class UniversalSearchTransition(
    val state: UniversalSearchState,
    val request: ProductSearchRequest? = null
)

/**
 * Permanent ValuePilot search application controller.
 *
 * Responsibilities:
 * - normalize and bound human queries;
 * - issue monotonically increasing request identities;
 * - reject stale provider completions;
 * - bound provider observations and visible results;
 * - parse each accepted observation at most once;
 * - reject irrelevant products;
 * - refuse unsafe cross-currency value ranking;
 * - delegate deterministic ranking to RankingEngine.
 *
 * It deliberately has no Android, network, filesystem, retailer, Accessibility,
 * OCR, model, or hidden-clock dependency.
 */
class UniversalSearchController(
    private val parser: ProductParser =
        DeterministicProductParser,
    private val rankingEngine: RankingEngine =
        DeterministicRankingEngine
) {

    fun initialState(
        nextRequestId: Long = 1L
    ): UniversalSearchState {
        require(nextRequestId > 0L)

        return UniversalSearchState(
            query = "",
            status = UniversalSearchStatus.IDLE,
            statusText =
                "Search for a product or shopping need",
            activeRequestId = null,
            nextRequestId = nextRequestId,
            receivedObservationCount = 0,
            parsedProductCount = 0,
            matchedProductCount = 0,
            rejectedObservationCount = 0,
            results = emptyList()
        )
    }

    fun reduce(
        previous: UniversalSearchState,
        intent: UniversalSearchIntent
    ): UniversalSearchTransition =
        when (intent) {

            is UniversalSearchIntent.QueryChanged ->
                queryChanged(
                    previous = previous,
                    rawQuery = intent.rawQuery
                )

            UniversalSearchIntent.Submit ->
                submit(previous)

            is UniversalSearchIntent.ResultsReceived ->
                receive(
                    previous = previous,
                    batch = intent.batch
                )

            is UniversalSearchIntent.ProviderFailed ->
                providerFailed(
                    previous = previous,
                    requestId = intent.requestId
                )

            UniversalSearchIntent.Clear ->
                UniversalSearchTransition(
                    initialState(
                        nextRequestId =
                            previous.nextRequestId
                    )
                )
        }

    private fun queryChanged(
        previous: UniversalSearchState,
        rawQuery: String
    ): UniversalSearchTransition {

        val normalized =
            normalizeQuery(rawQuery)

        if (normalized.isBlank()) {
            return UniversalSearchTransition(
                initialState(
                    nextRequestId =
                        previous.nextRequestId
                )
            )
        }

        if (normalized.length > MAX_QUERY_CHARS) {
            return UniversalSearchTransition(
                previous.copy(
                    query =
                        normalized.take(
                            MAX_QUERY_CHARS
                        ),
                    status =
                        UniversalSearchStatus
                            .QUERY_TOO_LONG,
                    statusText =
                        "Search is too long",
                    activeRequestId = null,
                    receivedObservationCount = 0,
                    parsedProductCount = 0,
                    matchedProductCount = 0,
                    rejectedObservationCount = 0,
                    results = emptyList()
                )
            )
        }

        return UniversalSearchTransition(
            previous.copy(
                query = normalized,
                status =
                    UniversalSearchStatus.READY,
                statusText =
                    "Ready to search",
                activeRequestId = null,
                receivedObservationCount = 0,
                parsedProductCount = 0,
                matchedProductCount = 0,
                rejectedObservationCount = 0,
                results = emptyList()
            )
        )
    }

    private fun submit(
        previous: UniversalSearchState
    ): UniversalSearchTransition {

        if (
            previous.query.isBlank() ||
            previous.status ==
                UniversalSearchStatus.QUERY_TOO_LONG
        ) {
            return UniversalSearchTransition(
                previous
            )
        }

        val requestId =
            previous.nextRequestId

        val request =
            ProductSearchRequest(
                requestId = requestId,
                query = previous.query,
                maxObservations =
                    MAX_PROVIDER_OBSERVATIONS
            )

        return UniversalSearchTransition(
            state =
                previous.copy(
                    status =
                        UniversalSearchStatus.LOADING,
                    statusText =
                        "Finding the best values…",
                    activeRequestId = requestId,
                    nextRequestId =
                        Math.addExact(
                            requestId,
                            1L
                        ),
                    receivedObservationCount = 0,
                    parsedProductCount = 0,
                    matchedProductCount = 0,
                    rejectedObservationCount = 0,
                    results = emptyList()
                ),
            request = request
        )
    }

    private fun receive(
        previous: UniversalSearchState,
        batch: ProductSearchBatch
    ): UniversalSearchTransition {

        if (
            previous.activeRequestId == null ||
            batch.requestId !=
                previous.activeRequestId
        ) {
            return UniversalSearchTransition(
                previous
            )
        }

        val acceptedEvidence =
            batch.evidence.take(
                MAX_PROVIDER_OBSERVATIONS
            )

        val parsedProducts =
            ArrayList<ValueItem>(
                acceptedEvidence.size
            )

        val evidenceByStableId =
            LinkedHashMap<Long, ShoppingEvidence>(
                acceptedEvidence.size
            )

        var rejected = 0

        for (
            evidence in
            acceptedEvidence
        ) {
            val observation =
                evidence.observation

            val parsed =
                parser.parse(
                    rawText =
                        observation.rawText,
                    sourceId =
                        evidence.source.id.value
                )

            if (parsed == null) {
                rejected++
            } else {
                parsedProducts += parsed

                evidenceByStableId[
                    parsed.stableId
                ] = evidence
            }
        }

        val matched =
            parsedProducts.filter { item ->
                SearchRelevance.matches(
                    query = previous.query,
                    item = item
                )
            }

        if (matched.isEmpty()) {
            return UniversalSearchTransition(
                previous.copy(
                    status =
                        UniversalSearchStatus
                            .NO_RESULTS,
                    statusText =
                        "No matching products found",
                    activeRequestId = null,
                    receivedObservationCount =
                        batch.evidence.size,
                    parsedProductCount =
                        parsedProducts.size,
                    matchedProductCount = 0,
                    rejectedObservationCount =
                        rejected,
                    results = emptyList()
                )
            )
        }

        val currencies =
            matched
                .map { it.currency }
                .toSet()

        if (currencies.size > 1) {
            return UniversalSearchTransition(
                previous.copy(
                    status =
                        UniversalSearchStatus
                            .MIXED_CURRENCIES,
                    statusText =
                        "Results use different currencies and cannot be value-ranked together",
                    activeRequestId = null,
                    receivedObservationCount =
                        batch.evidence.size,
                    parsedProductCount =
                        parsedProducts.size,
                    matchedProductCount =
                        matched.size,
                    rejectedObservationCount =
                        rejected,
                    results = emptyList()
                )
            )
        }

        val ranked =
            rankingEngine.rank(
                RankingRequest(
                    context = null,
                    products = matched,
                    mode = RankMode.SMART,
                    maxPrice = null,
                    foodOnly = false,
                    excludePork = false,
                    useMemberPrices = false
                )
            )

        val rows =
            ranked
                .take(MAX_VISIBLE_RESULTS)
                .map { rankedItem ->

                    val item =
                        rankedItem.item

                    UniversalSearchRow(
                        stableId =
                            rankedItem.stableId,
                        rank =
                            rankedItem.rank,
                        name =
                            item.name,
                        quantity =
                            item.quantity?.display,
                        priceSummary =
                            ValueEngine.money(
                                item.offer.currentPrice,
                                item.offer.currency
                            ),
                        metricLabel =
                            rankedItem.metricLabel,
                        exactnessLabel =
                            rankedItem.exactnessLabel,
                        best =
                            rankedItem.rank == 1,
                        sourceSummary =
                            sourceSummary(
                                evidenceByStableId[
                                    rankedItem.stableId
                                ]
                            ),
                        sampleEvidence =
                            evidenceByStableId[
                                rankedItem.stableId
                            ]?.isSample == true
                    )
                }

        val statusText =
            if (
                ranked.size >
                MAX_VISIBLE_RESULTS
            ) {
                "Showing the best ${rows.size} of ${ranked.size} matches"
            } else {
                "${rows.size} matching products"
            }

        return UniversalSearchTransition(
            previous.copy(
                status =
                    UniversalSearchStatus.RESULTS,
                statusText = statusText,
                activeRequestId = null,
                receivedObservationCount =
                    batch.evidence.size,
                parsedProductCount =
                    parsedProducts.size,
                matchedProductCount =
                    matched.size,
                rejectedObservationCount =
                    rejected,
                results = rows
            )
        )
    }

    private fun providerFailed(
        previous: UniversalSearchState,
        requestId: Long
    ): UniversalSearchTransition {

        if (
            previous.activeRequestId == null ||
            requestId !=
                previous.activeRequestId
        ) {
            return UniversalSearchTransition(
                previous
            )
        }

        return UniversalSearchTransition(
            previous.copy(
                status =
                    UniversalSearchStatus
                        .PROVIDER_ERROR,
                statusText =
                    "Search provider is unavailable. Try again.",
                activeRequestId = null,
                receivedObservationCount = 0,
                parsedProductCount = 0,
                matchedProductCount = 0,
                rejectedObservationCount = 0,
                results = emptyList()
            )
        )
    }

    private fun sourceSummary(
        evidence: ShoppingEvidence?
    ): String =
        when (evidence?.environment) {
            EvidenceEnvironment.SAMPLE ->
                "Sample source: ${evidence.source.displayName}"

            EvidenceEnvironment.REAL_WORLD ->
                "Source: ${evidence.source.displayName} • via ${evidence.provider.displayName}"

            EvidenceEnvironment.UNKNOWN ->
                "Unverified source: ${evidence.source.displayName}"

            null ->
                "Unverified source"
        }

    private fun normalizeQuery(
        rawQuery: String
    ): String =
        rawQuery
            .replace('\u00A0', ' ')
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

    companion object {
        const val MAX_QUERY_CHARS = 160
        const val MAX_PROVIDER_OBSERVATIONS = 200
        const val MAX_VISIBLE_RESULTS = 24
    }
}

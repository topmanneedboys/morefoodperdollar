package com.valuepilot.app

import com.valuepilot.core.EvidenceAcceptanceDecision
import com.valuepilot.core.EvidenceAcceptanceEvaluator
import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.EvidenceDisposition
import com.valuepilot.core.EvidenceEnvironment
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.EvidenceWarning
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
    val rank: Int?,
    val name: String,
    val quantity: String?,
    val priceSummary: String,
    val metricLabel: String,
    val exactnessLabel: String,
    val best: Boolean,
    val sourceSummary: String,
    val sampleEvidence: Boolean,
    val rankingEligible: Boolean,
    val evidenceNotice: String?
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
        val batch: ProductSearchBatch,
        /**
         * Explicit application-supplied evaluation time.
         *
         * Zero means unknown and therefore fails closed for real-world
         * freshness. Sample fixtures remain rankable by their explicit
         * SAMPLE + FIXTURE contract.
         */
        val evaluatedAtEpochMillis: Long = 0L
    ) : UniversalSearchIntent {
        init {
            require(evaluatedAtEpochMillis >= 0L)
        }
    }

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
        DeterministicRankingEngine,
    private val evidenceAcceptancePolicy:
        EvidenceAcceptancePolicy =
        DEFAULT_EVIDENCE_ACCEPTANCE_POLICY
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
                    batch = intent.batch,
                    evaluatedAtEpochMillis =
                        intent.evaluatedAtEpochMillis
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

    private data class EvaluatedSearchItem(
        val item: ValueItem,
        val evidence: ShoppingEvidence,
        val decision: EvidenceAcceptanceDecision,
        val promotionProvenanceMissing: Boolean
    ) {
        val rankingEligible: Boolean
            get() =
                decision.rankable &&
                    !promotionProvenanceMissing
    }

    private fun receive(
        previous: UniversalSearchState,
        batch: ProductSearchBatch,
        evaluatedAtEpochMillis: Long
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

        val boundedEvidence =
            batch.evidence.take(
                MAX_PROVIDER_OBSERVATIONS
            )

        val parsed =
            ArrayList<EvaluatedSearchItem>(
                boundedEvidence.size
            )

        var rejected = 0

        for (
            evidence in
            boundedEvidence
        ) {
            val decision =
                EvidenceAcceptanceEvaluator
                    .evaluate(
                        evidence = evidence,
                        evaluatedAtEpochMillis =
                            evaluatedAtEpochMillis,
                        policy =
                            evidenceAcceptancePolicy
                    )

            if (!decision.displayable) {
                rejected++
                continue
            }

            val observation =
                evidence.observation

            val item =
                parser.parse(
                    rawText =
                        observation.rawText,
                    sourceId =
                        evidence.source.id.value
                )

            if (item == null) {
                rejected++
                continue
            }

            /*
             * Parsing may detect BOGO/bundle promotion arithmetic from raw
             * text. That arithmetic must never improve Best Value unless the
             * provider also supplied explicit PromotionEvidence.
             *
             * We preserve the parsed product for reference display, but block
             * it from ranking until promotion provenance exists.
             */
            /*
             * A no-promotion parse still carries minimumSpend/effectivePrice
             * equal to the ordinary item price, so object inequality is not
             * a valid promotion detector.
             *
             * Only parsed promotion arithmetic that can improve ValuePilot's
             * value metric requires explicit PromotionEvidence here.
             */
            val promotionChangesValue =
                item.promotion.receivedMultiplier > 1.0

            val promotionProvenanceMissing =
                promotionChangesValue &&
                    evidence.promotion == null

            parsed +=
                EvaluatedSearchItem(
                    item = item,
                    evidence = evidence,
                    decision = decision,
                    promotionProvenanceMissing =
                        promotionProvenanceMissing
                )
        }

        val matched =
            parsed.filter { entry ->
                SearchRelevance.matches(
                    query = previous.query,
                    item = entry.item
                )
            }

        if (matched.isEmpty()) {
            val message =
                if (
                    rejected > 0 &&
                    boundedEvidence.isNotEmpty()
                ) {
                    "No trustworthy matching products found"
                } else {
                    "No matching products found"
                }

            return UniversalSearchTransition(
                previous.copy(
                    status =
                        UniversalSearchStatus
                            .NO_RESULTS,
                    statusText = message,
                    activeRequestId = null,
                    receivedObservationCount =
                        batch.evidence.size,
                    parsedProductCount =
                        parsed.size,
                    matchedProductCount = 0,
                    rejectedObservationCount =
                        rejected,
                    results = emptyList()
                )
            )
        }

        val rankable =
            matched.filter {
                it.rankingEligible
            }

        val referenceOnly =
            matched.filter {
                !it.rankingEligible
            }

        val currencies =
            rankable
                .map { it.item.currency }
                .toSet()

        if (currencies.size > 1) {
            return UniversalSearchTransition(
                previous.copy(
                    status =
                        UniversalSearchStatus
                            .MIXED_CURRENCIES,
                    statusText =
                        "Rankable results use different currencies and cannot be value-ranked together",
                    activeRequestId = null,
                    receivedObservationCount =
                        batch.evidence.size,
                    parsedProductCount =
                        parsed.size,
                    matchedProductCount =
                        matched.size,
                    rejectedObservationCount =
                        rejected,
                    results = emptyList()
                )
            )
        }

        val ranked =
            if (rankable.isEmpty()) {
                emptyList()
            } else {
                rankingEngine.rank(
                    RankingRequest(
                        context = null,
                        products =
                            rankable.map {
                                it.item
                            },
                        mode = RankMode.SMART,
                        maxPrice = null,
                        foodOnly = false,
                        excludePork = false,
                        useMemberPrices = false
                    )
                )
            }

        val rankableByStableId =
            rankable.associateBy {
                it.item.stableId
            }

        val rankedRows =
            ranked
                .take(MAX_VISIBLE_RESULTS)
                .map { rankedItem ->

                    val item =
                        rankedItem.item

                    val entry =
                        rankableByStableId[
                            rankedItem.stableId
                        ]

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
                                entry?.evidence
                            ),
                        sampleEvidence =
                            entry?.evidence
                                ?.isSample == true,
                        rankingEligible =
                            true,
                        evidenceNotice =
                            entry?.let(
                                ::evidenceNotice
                            )
                    )
                }

        val remainingSlots =
            (
                MAX_VISIBLE_RESULTS -
                    rankedRows.size
            ).coerceAtLeast(0)

        val referenceRows =
            referenceOnly
                .take(remainingSlots)
                .map { entry ->

                    val item =
                        entry.item

                    UniversalSearchRow(
                        stableId =
                            item.stableId,
                        rank = null,
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
                            "Reference only",
                        exactnessLabel =
                            "Not used for Best Value",
                        best = false,
                        sourceSummary =
                            sourceSummary(
                                entry.evidence
                            ),
                        sampleEvidence =
                            entry.evidence.isSample,
                        rankingEligible =
                            false,
                        evidenceNotice =
                            evidenceNotice(
                                entry
                            )
                    )
                }

        val rows =
            rankedRows +
                referenceRows

        if (rows.isEmpty()) {
            return UniversalSearchTransition(
                previous.copy(
                    status =
                        UniversalSearchStatus
                            .NO_RESULTS,
                    statusText =
                        "No trustworthy matching products found",
                    activeRequestId = null,
                    receivedObservationCount =
                        batch.evidence.size,
                    parsedProductCount =
                        parsed.size,
                    matchedProductCount =
                        matched.size,
                    rejectedObservationCount =
                        rejected,
                    results = emptyList()
                )
            )
        }

        val statusText =
            when {
                rankedRows.isNotEmpty() &&
                    referenceRows.isNotEmpty() ->
                    "${rankedRows.size} ranked • ${referenceRows.size} reference only"

                rankedRows.isNotEmpty() &&
                    ranked.size >
                    MAX_VISIBLE_RESULTS ->
                    "Showing the best ${rankedRows.size} of ${ranked.size} rankable matches"

                rankedRows.isNotEmpty() ->
                    "${rankedRows.size} ranked products"

                else ->
                    "${referenceRows.size} matching products • reference only"
            }

        return UniversalSearchTransition(
            previous.copy(
                status =
                    UniversalSearchStatus.RESULTS,
                statusText =
                    statusText,
                activeRequestId = null,
                receivedObservationCount =
                    batch.evidence.size,
                parsedProductCount =
                    parsed.size,
                matchedProductCount =
                    matched.size,
                rejectedObservationCount =
                    rejected,
                results =
                    rows
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

    private fun evidenceNotice(
        entry: EvaluatedSearchItem
    ): String? {

        val labels =
            entry.decision.warnings
                .mapNotNull { warning ->
                    when (warning) {
                        EvidenceWarning.SAMPLE_DATA ->
                            null

                        EvidenceWarning.AGING ->
                            "Price may be aging"

                        EvidenceWarning.STALE ->
                            "Stale evidence"

                        EvidenceWarning.UNKNOWN_FRESHNESS ->
                            "Freshness unknown"

                        EvidenceWarning.FUTURE_DATED ->
                            "Invalid future timestamp"

                        EvidenceWarning.UNKNOWN_ENVIRONMENT,
                        EvidenceWarning.UNKNOWN_CHANNEL ->
                            "Source not fully verified"

                        EvidenceWarning.WEAK_OBSERVATION_CLAIM ->
                            "Product evidence is inferred"

                        EvidenceWarning.AVAILABILITY_UNKNOWN ->
                            "Availability unknown"

                        EvidenceWarning.LOW_STOCK ->
                            "Low stock"

                        EvidenceWarning.NOT_CURRENTLY_AVAILABLE ->
                            "Not currently available"

                        EvidenceWarning.WEAK_PROMOTION_CLAIM ->
                            "Promotion unverified"

                        EvidenceWarning.EXPIRED_PROMOTION ->
                            "Promotion expired"
                    }
                }
                .distinct()
                .toMutableList()

        if (
            entry.promotionProvenanceMissing
        ) {
            labels +=
                "Promotion not verified for ranking"
        }

        return labels
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" • ")
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

        private const val MINUTE_MILLIS =
            60L * 1000L

        private const val HOUR_MILLIS =
            60L * MINUTE_MILLIS

        val DEFAULT_EVIDENCE_ACCEPTANCE_POLICY =
            EvidenceAcceptancePolicy(
                freshnessPolicy =
                    EvidenceFreshnessPolicy(
                        freshForMillis =
                            15L *
                                MINUTE_MILLIS,
                        staleAfterMillis =
                            2L *
                                HOUR_MILLIS,
                        futureToleranceMillis =
                            5L *
                                MINUTE_MILLIS
                    )
            )
    }
}

package com.valuepilot.app

import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The versioned, read-only contract emitted by the qualified Argentina backend.
 *
 * The parser is strict because a response is untrusted input: values that can
 * change shopping meaning are rejected rather than guessed.  M12.3 keeps the
 * network implementation outside this contract and only adds typed transport
 * outcomes at the application boundary.
 */
object ArgentinaBackendContract {
    const val API_VERSION = "valuepilot-argentina-backend-http-v1"
    const val DISCOVERY_API_VERSION = "valuepilot-discovery-http-v2"
    const val DISCOVERY_RESULT_CONTRACT = "valuepilot-discovery-v2"
    const val SHOPPING_SCHEMA_VERSION = "valuepilot-shopping-intelligence-v1"
    const val INPUT_SCHEMA_VERSION = "valuepilot-argentina-consumer-input-intelligence-v1"
    const val INPUT_POLICY_VERSION = "valuepilot-argentina-input-policy-v1"
    const val MAX_RESPONSE_BYTES = 512 * 1024
    const val MAX_JSON_DEPTH = 24
    const val MAX_TEXT_CHARS = 4_096
    const val MAX_QUERY_CHARS = 96
    const val SEARCH_PAGE_SIZE = 5
    const val MAX_SEARCH_PAGE = 5
    const val MAX_SEARCH_CLARIFICATION_CHOICES = 30
    const val MAX_LINES = 10
    const val MAX_MATCHES = 32
    const val MAX_PRODUCT_CANDIDATES = 128
    const val MAX_SEARCH_OFFERS_PER_IDENTITY = 8
    const val MAX_DISCOVERY_PAGE_SIZE = 50
    const val MAX_DISCOVERY_FACET_PAGE_SIZE = 30
    const val MAX_DISCOVERY_SELECTIONS = 8
    const val MAX_DISCOVERY_MATCHES = 50
    const val MAX_STORES = 2
    const val MAX_PLAN_LINES = 10
    const val MAX_FRONTIER_PLANS = 64
    const val MAX_PROMOTIONS = 16
    const val MAX_DIAGNOSTIC_ENTRIES = 64
    const val MAX_DIAGNOSTIC_VALUE_CHARS = 256
    const val MAX_DIAGNOSTIC_ARRAY_ITEMS = 32
    const val MAX_ADDRESS_ENTRIES = 16
    const val MAX_ADDRESS_VALUE_CHARS = 256

    private val DECIMAL_PATTERN = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")
    private val ID_PATTERN = Regex("[A-Za-z0-9_.:-]{1,320}")
    private val PLAN_ID_PATTERN = Regex("[A-Za-z0-9_.:+-]{1,700}")
    private val DATE_PATTERN = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val GTIN_PATTERN = Regex("[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14}")

    enum class Freshness {
        FRESH,
        STALE,
        UNKNOWN
    }

    enum class Availability {
        UNKNOWN
    }

    enum class Resolution {
        RESOLVED_EXACT,
        RESOLVED_ALIAS,
        RESOLVED_SAFE_CORRECTION,
        NEEDS_CLARIFICATION,
        NO_SAFE_MATCH
    }

    enum class SearchResolution {
        DISCOVERY,
        RESOLVED_EXACT,
        RESOLVED_ALIAS,
        RESOLVED_SAFE_CORRECTION,
        NEEDS_CLARIFICATION,
        NO_SAFE_MATCH
    }

    enum class ClarificationCode {
        PACKAGE_SIZE_REQUIRED,
        QUANTITY_REQUIRED,
        AMBIGUOUS_PRODUCT,
        DIMENSION_MISMATCH,
        MULTIPLE_PLAUSIBLE_PRODUCTS
    }

    enum class DecisionSkipped {
        CLARIFICATION_REQUIRED
    }

    enum class FailureCode {
        REQUEST_TOO_LARGE,
        RESPONSE_TOO_LARGE,
        APP_CHECK_UNAVAILABLE,
        CONFIGURATION_INVALID,
        ROUTE_REJECTED,
        NO_CONNECTIVITY,
        TLS_FAILURE,
        TIMEOUT,
        CANCELLED,
        NETWORK_ERROR,
        HTTP_BAD_REQUEST,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMITED,
        SERVER_ERROR,
        HTTP_ERROR,
        CONTRACT_INVALID,
        UNSUPPORTED_VERSION,
        RELEASE_MISMATCH,
        STALE_RELEASE,
        SERVICE_UNAVAILABLE,
        SEARCH_NOT_AUTHORIZED,
        SEARCH_GEOGRAPHY_UNAVAILABLE
    }

    class ContractException(
        val code: FailureCode,
        message: String
    ) : IllegalArgumentException(message)

    data class ExpectedRelease(
        val releaseId: String,
        val releaseDate: String
    ) {
        init {
            require(ID_PATTERN.matches(releaseId))
            require(DATE_PATTERN.matches(releaseDate))
            require(validIsoDate(releaseDate)) { "releaseDate is invalid" }
        }
    }

    data class EvidenceSemantics(
        val availability: Availability,
        val distance: String,
        val promotions: String?,
        val pricePublicationIsNotInventory: Boolean?
    )

    data class CommonEnvelope(
        val apiContractVersion: String,
        val requestId: String,
        val releaseId: String,
        val releaseDate: String,
        val freshness: Freshness,
        val evidenceSemantics: EvidenceSemantics?,
        val diagnostics: Map<String, String>
    )

    /** Safe envelope fields retained for failure diagnostics only. */
    internal data class SafeResponseMetadata(
        val requestId: String?,
        val releaseId: String?,
        val releaseDate: String?
    )

    data class StatusResponse(
        val apiContractVersion: String,
        val service: String,
        val releaseId: String?,
        val releaseDate: String?,
        val freshness: Freshness?,
        val backendCodeQualified: Boolean,
        val liveCloudDeploymentVerified: Boolean,
        val androidNetworkingAuthorized: Boolean,
        val productionAndroidUiAuthorized: Boolean,
        val automatedOfficialAcquisitionProven: Boolean
    )

    data class Money(
        val amount: String,
        val currency: String,
        val decimal: BigDecimal
    )

    data class Quantity(
        val dimension: String,
        val amount: String,
        val unit: String,
        val baseAmount: String,
        val baseUnit: String,
        val allowZero: Boolean = false
    ) {
        val decimalAmount: BigDecimal
            get() = BigDecimal(amount)
    }

    data class ProductIdentity(
        val productEvidenceKey: String,
        val name: String,
        val brand: String?,
        val providerProductId: String?,
        val gtin: String?,
        val gtinStatus: String?,
        val quantity: Quantity?,
        /** Optional bounded current-price evidence attached to discovery. */
        val qualifiedOffers: List<SearchOffer> = emptyList()
    )

    data class SearchOffer(
        val offerKey: String,
        val productEvidenceKey: String,
        val storeKey: String,
        val storeName: String,
        val storeAddress: Map<String, String>,
        val storeLatitude: String,
        val storeLongitude: String,
        val distanceKm: String,
        val listPrice: Money,
        val referencePrice: Money?,
        val quantity: Quantity?,
        val quantityStatus: String?,
        val unitPrice: Money?,
        val unitPriceBasis: String?,
        val releaseDate: String,
        val freshnessStatus: Freshness,
        val availability: Availability,
        val provenance: Provenance
    )

    data class SearchMatch(
        val lineId: String,
        val query: String,
        val productCandidates: List<ProductIdentity>
    )

    /**
     * Observed product-family choices for discovery queries such as rice or
     * yerba.  These choices refine the query; they never select a product or
     * assert price, stock, availability, or an offer.
     */
    data class SearchClarification(
        val code: String,
        val family: String,
        val message: String,
        val messageEn: String?,
        val choices: List<SearchClarificationChoice>,
        val evidence: String
    ) {
        init {
            require(code == "PRODUCT_FAMILY_CLARIFICATION")
            require(family.matches(ID_PATTERN))
            require(message.isNotBlank())
            require(messageEn == null || messageEn.isNotBlank())
            require(choices.size in 2..MAX_SEARCH_CLARIFICATION_CHOICES)
            require(evidence == "OBSERVED_CATALOG_NAMES")
        }
    }

    data class SearchClarificationChoice(
        val id: String,
        val label: String,
        val labelEn: String?,
        val query: String
    ) {
        init {
            require(id.matches(ID_PATTERN))
            require(label.isNotBlank())
            require(labelEn == null || labelEn.isNotBlank())
            require(query.isNotBlank() && query.length <= MAX_QUERY_CHARS)
        }
    }

    data class InputClarification(
        val lineId: String?,
        val code: ClarificationCode,
        val message: String?
    )

    data class InputLine(
        val lineId: String,
        val originalText: String,
        val normalizedText: String,
        val resolution: Resolution,
        val recognizedProduct: ProductIdentity?,
        val quantityStatus: String,
        val quantity: Quantity?,
        val clarification: InputClarification?,
        val suggestions: List<ProductIdentity>,
        val structuredItem: RequestItem?,
        val candidatesExamined: Int
    )

    data class InputResult(
        val schemaVersion: String,
        val policyVersion: String,
        val originalText: String,
        val lineCount: Int,
        val lines: List<InputLine>,
        val clarifications: List<InputClarification>,
        val safeRequestReady: Boolean,
        val structuredItems: List<RequestItem>,
        val diagnostics: Map<String, String>
    )

    data class SearchResponse(
        val common: CommonEnvelope,
        val query: String,
        val originalQuery: String,
        val interpretedQuery: String?,
        val resolution: SearchResolution?,
        val correction: Map<String, String>?,
        val matches: List<SearchMatch>,
        val identityCandidates: List<ProductIdentity>,
        val input: InputResult?,
        val pagination: SearchPagination? = null,
        val clarification: SearchClarification? = null
    )

    data class SearchPagination(
        val page: Int,
        val pageSize: Int,
        val hasMore: Boolean,
        val nextPage: Int?,
        val limitReached: Boolean
    ) {
        init {
            require(page in 0..MAX_SEARCH_PAGE)
            require(pageSize == SEARCH_PAGE_SIZE)
            require(nextPage == null || nextPage == page + 1)
            require(!limitReached || hasMore)
            require(!hasMore || nextPage != null || limitReached)
        }
    }

    data class InterpretResponse(
        val common: CommonEnvelope,
        val input: InputResult
    )

    data class RequestItem(
        val lineId: String,
        val query: String,
        val amount: String,
        val unit: String,
        val productEvidenceKey: String?
    )

    data class Provenance(
        val provider: String,
        val releaseDate: String,
        val freshnessStatus: Freshness,
        val source: String?
    )

    data class Promotion(
        val eligibility: String,
        val condition: String?,
        val price: Money?,
        val rawJson: String
    )

    data class Store(
        val storeKey: String,
        val storeName: String,
        val distanceKm: String,
        val latitude: String,
        val longitude: String,
        val address: Map<String, String>
    )

    data class PlanLine(
        val lineId: String,
        val productEvidenceKey: String,
        val productName: String,
        val brand: String?,
        val offerKey: String,
        val storeKey: String,
        val storeName: String,
        val distanceKm: String,
        val requestedQuantity: Quantity,
        val packageQuantity: Quantity,
        val packageCount: Int,
        val suppliedQuantity: Quantity,
        val excessQuantity: Quantity,
        val lineTotal: Money,
        val packagePrice: Money,
        val availability: Availability,
        val freshnessStatus: Freshness,
        val promotions: List<Promotion>,
        val provenance: Provenance
    )

    data class Plan(
        val type: String,
        val planId: String,
        val storeCount: Int,
        val storeKeys: List<String>,
        val stores: List<Store>,
        val lines: List<PlanLine>,
        val total: Money,
        val maxStraightLineDistanceKm: String,
        val completePriceEvidence: Boolean,
        val missingLineIds: List<String>
    )

    data class ShoppingRequest(
        val latitude: String,
        val longitude: String,
        val radiusKm: String,
        val items: List<RequestItem>
    )

    data class BestSensibleChoice(
        val status: String,
        val selectedPlan: Plan?
    )

    data class ShoppingDecision(
        val schemaVersion: String,
        val request: ShoppingRequest,
        val cheapestSingleStore: Plan?,
        val closestCompletePriceEvidenceStore: Plan?,
        val cheapestTwoStoreCombination: Plan?,
        val cheapestUpToTwoStores: Plan?,
        val cheapestPerLineUnboundedStores: Plan?,
        val decisionFrontier: List<Plan>,
        val bestSensibleChoice: BestSensibleChoice,
        val diagnostics: Map<String, String>,
        val safety: Map<String, String>
    )

    data class ShopResponse(
        val common: CommonEnvelope,
        val regionsQueried: List<String>,
        val result: ShoppingDecision
    )

    data class ShopTextResponse(
        val common: CommonEnvelope,
        val regionsQueried: List<String>,
        val input: InputResult,
        val result: ShoppingDecision?,
        val decisionSkipped: DecisionSkipped?
    )

    data class SearchRequest(
        val latitude: String,
        val longitude: String,
        val radiusKm: String,
        val query: String,
        val page: Int = 0,
        val selectionId: String? = null
    ) {
        init {
            validateCoordinate(latitude, "latitude", -90, 90)
            validateCoordinate(longitude, "longitude", -180, 180)
            validateDecimal(radiusKm, "radiusKm", nonNegative = true)
            require(query.trim().isNotEmpty() && query.length <= MAX_QUERY_CHARS)
            require(page in 0..MAX_SEARCH_PAGE)
            require(selectionId == null || (selectionId.length <= 160 && ID_PATTERN.matches(selectionId)))
        }
    }

    /** Full-catalog identity discovery request.  It is additive to v1 Search. */
    data class DiscoverySearchRequest(
        val query: String,
        val language: String = "es",
        val selections: List<String> = emptyList(),
        val pageSize: Int = 20,
        val cursor: String? = null,
        val facetPageSize: Int = MAX_DISCOVERY_FACET_PAGE_SIZE,
        val facetCursor: String? = null
    ) {
        init {
            require(query.trim().isNotEmpty() && query.length <= 256)
            require(language in setOf("es", "en"))
            require(selections.size <= MAX_DISCOVERY_SELECTIONS)
            require(selections.distinct().size == selections.size)
            require(selections.all { ID_PATTERN.matches(it) })
            require(pageSize in 1..MAX_DISCOVERY_PAGE_SIZE)
            require(facetPageSize in 1..MAX_DISCOVERY_FACET_PAGE_SIZE)
            require(cursor == null || cursor.length <= 2_048)
            require(facetCursor == null || facetCursor.length <= 2_048)
        }
    }

    data class DiscoveryProduct(
        val identityKey: String,
        val providerProductId: String,
        val displayName: String,
        val brand: String?
    )

    data class DiscoveryChoice(
        val id: String,
        val predicateId: String,
        val labelEs: String,
        val labelEn: String,
        val productCount: Int,
        val sourceIdentityCount: Int,
        val countUnit: String,
        val hasChildren: Boolean
    )

    data class DiscoveryClarification(
        val dimensionId: String,
        val choices: List<DiscoveryChoice>,
        val hasMore: Boolean,
        val nextCursor: String?
    )

    data class DiscoveryPagination(
        val pageSize: Int,
        val hasMore: Boolean,
        val nextCursor: String?
    )

    data class DiscoveryTotal(
        val productCount: Int,
        val sourceIdentityCount: Int,
        val countUnit: String
    )

    data class DiscoveryResult(
        val contract: String,
        val status: String,
        val query: String,
        val language: String,
        val releaseId: String,
        val indexVersion: String,
        val queryFingerprint: String,
        val resolvedPredicates: List<String>,
        val selections: List<String>,
        val total: DiscoveryTotal,
        val matches: List<DiscoveryProduct>,
        val pagination: DiscoveryPagination,
        val clarification: DiscoveryClarification?
    )

    data class DiscoveryResponse(
        val apiContractVersion: String,
        val requestId: String,
        val releaseId: String,
        val indexVersion: String,
        val result: DiscoveryResult
    )

    data class InterpretRequest(val text: String) {
        init {
            require(text.trim().isNotEmpty() && text.length <= MAX_TEXT_CHARS)
        }
    }

    data class ShopTextRequest(
        val latitude: String,
        val longitude: String,
        val radiusKm: String,
        val text: String
    ) {
        init {
            validateCoordinate(latitude, "latitude", -90, 90)
            validateCoordinate(longitude, "longitude", -180, 180)
            validateDecimal(radiusKm, "radiusKm", nonNegative = true)
            require(text.trim().isNotEmpty() && text.length <= MAX_TEXT_CHARS)
        }
    }

    data class ShopRequest(
        val latitude: String,
        val longitude: String,
        val radiusKm: String,
        val items: List<RequestItem>
    ) {
        init {
            validateCoordinate(latitude, "latitude", -90, 90)
            validateCoordinate(longitude, "longitude", -180, 180)
            validateDecimal(radiusKm, "radiusKm", nonNegative = true)
            require(items.size in 1..MAX_LINES)
            require(items.map { it.lineId }.distinct().size == items.size)
        }
    }

    private fun parseRoot(rawJson: String): JSONObject {
        val bytes = rawJson.toByteArray(StandardCharsets.UTF_8).size
        if (bytes > MAX_RESPONSE_BYTES) {
            throw ContractException(FailureCode.RESPONSE_TOO_LARGE, "backend response exceeds the bounded byte limit")
        }
        if (rawJson.isBlank()) {
            throw ContractException(FailureCode.CONTRACT_INVALID, "backend response is empty")
        }
        ensureNestingBound(rawJson)
        try {
            val tokener = JSONTokener(rawJson)
            val value = tokener.nextValue()
            if (value !is JSONObject || tokener.nextClean() != '\u0000') {
                invalid("backend response must be one JSON object")
            }
            return value
        } catch (error: ContractException) {
            throw error
        } catch (error: Exception) {
            throw ContractException(FailureCode.CONTRACT_INVALID, "backend response is malformed JSON: ${error.message}")
        }
    }

    private fun ensureNestingBound(rawJson: String) {
        var depth = 0
        var inString = false
        var escaped = false
        rawJson.forEach { character ->
            if (inString) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') inString = false
                return@forEach
            }
            when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    depth += 1
                    if (depth > MAX_JSON_DEPTH) invalid("backend response nesting exceeds the bounded depth")
                }
                '}', ']' -> depth -= 1
            }
        }
        if (inString || depth != 0) invalid("backend response has unbalanced JSON")
    }

    fun parseStatus(
        rawJson: String,
        expectedRelease: ExpectedRelease? = null
    ): StatusResponse {
        val root = parseRoot(rawJson)
        val version = requiredString(root, "apiContractVersion", 96)
        requireVersion(version)
        val service = requiredString(root, "service", 96)
        val releaseId = optionalString(root, "releaseId", 320)
        val releaseDate = optionalString(root, "releaseDate", 32)
        val freshness = optionalFreshness(root, "freshness")
        if ((releaseId == null) != (releaseDate == null)) invalid("status release identity is incomplete")
        if (releaseDate != null) parseDate(releaseDate, "releaseDate")
        expectedRelease?.let { expected ->
            if (releaseId != null && (releaseId != expected.releaseId || releaseDate != expected.releaseDate)) {
                mismatch()
            }
        }
        return StatusResponse(
            apiContractVersion = version,
            service = service,
            releaseId = releaseId,
            releaseDate = releaseDate,
            freshness = freshness,
            backendCodeQualified = requiredBoolean(root, "backendCodeQualified"),
            liveCloudDeploymentVerified = requiredBoolean(root, "liveCloudDeploymentVerified"),
            androidNetworkingAuthorized = requiredBoolean(root, "androidNetworkingAuthorized"),
            productionAndroidUiAuthorized = requiredBoolean(root, "productionAndroidUiAuthorized"),
            automatedOfficialAcquisitionProven = requiredBoolean(root, "automatedOfficialAcquisitionProven")
        )
    }

    fun parseSearch(
        rawJson: String,
        expectedRelease: ExpectedRelease? = null
    ): SearchResponse {
        val root = parseRoot(rawJson)
        val common = parseCommon(root, requireEvidence = true, expectedRelease = expectedRelease)
        val query = boundedQuery(requiredString(root, "query", MAX_QUERY_CHARS), "query")
        val originalQuery = boundedQuery(requiredString(root, "originalQuery", MAX_QUERY_CHARS), "originalQuery")
        val interpretedQuery = optionalString(root, "interpretedQuery", MAX_QUERY_CHARS)
        val resolution = optionalSearchResolution(root, "resolution")
        val correction = optionalStringMap(root, "correction", 8, 160)
        val clarification = optionalSearchClarification(root, "clarification")
        val rawMatches = requiredArray(root, "matches")
        val pagination =
            if (!root.has("pagination") || root.isNull("pagination")) null
            else parseSearchPagination(requiredObject(root, "pagination"))

        val matches: List<SearchMatch>
        val identityCandidates: List<ProductIdentity>

        if (resolution == SearchResolution.DISCOVERY) {
            boundedArray(rawMatches, MAX_MATCHES, "matches")
            if ((0 until rawMatches.length()).any { index ->
                    rawMatches.optJSONObject(index)?.has("productCandidates") == true
                }
            ) {
                invalid("discovery matches must contain flat product identities")
            }
            matches = emptyList()
            identityCandidates = parseProductCandidates(rawMatches, common.releaseDate)
            if (pagination != null && pagination.pageSize != SEARCH_PAGE_SIZE) invalid("discovery pagination page size is invalid")
        } else {
            if (pagination != null) invalid("non-discovery search cannot contain pagination")
            matches = parseSearchMatches(rawMatches, common.releaseDate)
            identityCandidates = matches.flatMap { it.productCandidates }
        }

        val input =
            if (!root.has("input") || root.isNull("input")) null
            else parseInput(requiredObject(root, "input"))

        if (resolution == SearchResolution.DISCOVERY) {
            if (input != null) invalid("discovery search input must be null")
        } else if (input == null) {
            invalid("non-discovery search requires input")
        }

        return SearchResponse(
            common,
            query,
            originalQuery,
            interpretedQuery,
            resolution,
            correction,
            matches,
            identityCandidates,
            input,
            pagination,
            clarification
        )
    }

    /** Strict parser for the additive identity-only full-catalog rail. */
    fun parseDiscovery(
        rawJson: String,
        expectedRelease: ExpectedRelease? = null
    ): DiscoveryResponse {
        val root = parseRoot(rawJson)
        val version = requiredString(root, "apiContractVersion", 96)
        if (version != DISCOVERY_API_VERSION) invalid("unsupported discovery contract version")
        val requestId = requiredId(root, "requestId", 96)
        val releaseId = requiredString(root, "releaseId", 320)
        expectedRelease?.let { expected -> if (releaseId != expected.releaseId) mismatch() }
        val indexVersion = requiredString(root, "indexVersion", 128)
        val resultRoot = requiredObject(root, "result")
        if (requiredString(resultRoot, "contract", 96) != DISCOVERY_RESULT_CONTRACT) invalid("unsupported discovery result contract")
        val resultReleaseId = requiredString(resultRoot, "releaseId", 320)
        if (resultReleaseId != releaseId) invalid("discovery release identity disagrees")
        val query = boundedQuery(requiredString(resultRoot, "query", 256), "discovery query")
        val language = requiredString(resultRoot, "language", 2)
        if (language !in setOf("es", "en")) invalid("unsupported discovery language")
        val status = requiredString(resultRoot, "status", 32)
        if (status !in setOf("READY", "NEEDS_CLARIFICATION", "NO_MATCHES")) invalid("unsupported discovery status")
        val fingerprint = requiredString(resultRoot, "queryFingerprint", 128)
        val predicates = parseBoundedIdArray(requiredArray(resultRoot, "resolvedPredicates"), MAX_DISCOVERY_SELECTIONS + 16, "resolvedPredicates")
        val selections = parseBoundedIdArray(requiredArray(resultRoot, "selections"), MAX_DISCOVERY_SELECTIONS, "selections")
        val totalRoot = requiredObject(resultRoot, "total")
        val total = DiscoveryTotal(
            productCount = requiredInt(totalRoot, "productCount", 0, 500_000),
            sourceIdentityCount = requiredInt(totalRoot, "sourceIdentityCount", 0, 500_000),
            countUnit = requiredString(totalRoot, "countUnit", 64)
        )
        if (total.countUnit != "DISTINCT_PROVIDER_PRODUCT") invalid("unsupported discovery count unit")
        val matchesArray = requiredArray(resultRoot, "matches")
        boundedArray(matchesArray, MAX_DISCOVERY_MATCHES, "discovery matches")
        val matches = (0 until matchesArray.length()).map { index ->
            val match = matchesArray.get(index) as? JSONObject ?: invalid("discovery match must be an object")
            DiscoveryProduct(
                identityKey = requiredId(match, "identityKey", 256),
                providerProductId = requiredString(match, "providerProductId", 160),
                displayName = requiredText(match, "displayName", 512),
                brand = optionalString(match, "brand", 160)
            )
        }
        val paginationRoot = requiredObject(resultRoot, "pagination")
        val pagination = DiscoveryPagination(
            pageSize = requiredInt(paginationRoot, "pageSize", 1, MAX_DISCOVERY_PAGE_SIZE),
            hasMore = requiredBoolean(paginationRoot, "hasMore"),
            nextCursor = optionalString(paginationRoot, "nextCursor", 2_048)
        )
        if (pagination.hasMore && pagination.nextCursor == null) invalid("discovery pagination cursor is missing")
        val clarification = if (!resultRoot.has("clarification") || resultRoot.isNull("clarification")) null else parseDiscoveryClarification(requiredObject(resultRoot, "clarification"))
        if (status == "NEEDS_CLARIFICATION" && clarification == null) invalid("clarification is missing")
        if (status != "NEEDS_CLARIFICATION" && clarification != null) invalid("unexpected clarification")
        val result = DiscoveryResult(
            contract = DISCOVERY_RESULT_CONTRACT,
            status = status,
            query = query,
            language = language,
            releaseId = resultReleaseId,
            indexVersion = indexVersion,
            queryFingerprint = fingerprint,
            resolvedPredicates = predicates,
            selections = selections,
            total = total,
            matches = matches,
            pagination = pagination,
            clarification = clarification
        )
        val semantics = requiredObject(root, "evidenceSemantics")
        if (requiredString(semantics, "identity", 96) != "SOURCE_IDENTITY_AND_ACCEPTED_ASSERTIONS") invalid("unsupported discovery identity semantics")
        if (requiredString(semantics, "prices", 32) != "NOT_INCLUDED" || requiredString(semantics, "offers", 32) != "NOT_INCLUDED" || requiredString(semantics, "stock", 32) != "NOT_INCLUDED") invalid("discovery response contains shopping evidence")
        return DiscoveryResponse(version, requestId, releaseId, indexVersion, result)
    }

    private fun parseSearchPagination(root: JSONObject): SearchPagination {
        val page = requiredInt(root, "page", 0, MAX_SEARCH_PAGE)
        val pageSize = requiredInt(root, "pageSize", SEARCH_PAGE_SIZE, SEARCH_PAGE_SIZE)
        val hasMore = requiredBoolean(root, "hasMore")
        val nextPage = optionalInt(root, "nextPage", 0, MAX_SEARCH_PAGE)
        val limitReached = requiredBoolean(root, "limitReached")
        return try {
            SearchPagination(page, pageSize, hasMore, nextPage, limitReached)
        } catch (error: IllegalArgumentException) {
            invalid("discovery pagination is inconsistent")
        }
    }

    private fun optionalSearchClarification(root: JSONObject, name: String): SearchClarification? {
        if (!root.has(name) || root.isNull(name)) return null
        val value = requiredObject(root, name)
        val code = requiredString(value, "code", 64)
        val family = requiredId(value, "family", 96)
        val message = requiredText(value, "message", 512)
        val messageEn = optionalString(value, "messageEn", 512)
        val choicesArray = requiredArray(value, "choices")
        boundedArray(choicesArray, MAX_SEARCH_CLARIFICATION_CHOICES, "clarification choices")
        val choices = (0 until choicesArray.length()).map { index ->
            val choice = choicesArray.get(index) as? JSONObject ?: invalid("clarification choice must be an object")
            SearchClarificationChoice(
                id = requiredId(choice, "id", 160),
                label = requiredText(choice, "label", 160),
                labelEn = optionalString(choice, "labelEn", 160),
                query = boundedQuery(requiredText(choice, "query", MAX_QUERY_CHARS), "clarification query")
            )
        }
        if (choices.size < 2) invalid("clarification choices must contain at least two choices")
        val evidence = requiredString(value, "evidence", 64)
        return try {
            SearchClarification(code, family, message, messageEn, choices, evidence)
        } catch (error: IllegalArgumentException) {
            invalid("invalid search clarification")
        }
    }

    fun parseInterpret(
        rawJson: String,
        expectedRelease: ExpectedRelease? = null
    ): InterpretResponse {
        val root = parseRoot(rawJson)
        val common = parseCommon(root, requireEvidence = false, expectedRelease = expectedRelease)
        return InterpretResponse(common, parseInput(requiredObject(root, "input")))
    }

    fun parseShop(
        rawJson: String,
        expectedRelease: ExpectedRelease? = null
    ): ShopResponse {
        val root = parseRoot(rawJson)
        val common = parseCommon(root, requireEvidence = true, expectedRelease = expectedRelease)
        val regions = parseStringArray(requiredArray(root, "regionsQueried"), 32, 96, "regionsQueried")
        val result = parseDecisionEnvelope(requiredObject(root, "result"), common.releaseDate)
        return ShopResponse(common, regions, result)
    }

    fun parseShopText(
        rawJson: String,
        expectedRelease: ExpectedRelease? = null
    ): ShopTextResponse {
        val root = parseRoot(rawJson)
        val common = parseCommon(root, requireEvidence = true, expectedRelease = expectedRelease)
        val regions = parseStringArray(requiredArray(root, "regionsQueried"), 32, 96, "regionsQueried")
        val input = parseInput(requiredObject(root, "input"))
        val result = if (!root.has("result") || root.isNull("result")) null else parseDecisionEnvelope(requiredObject(root, "result"), common.releaseDate)
        val skipped = optionalDecisionSkipped(root, "decisionSkipped")
        if (result == null && skipped != DecisionSkipped.CLARIFICATION_REQUIRED) {
            invalid("a null shop-text result must be explicitly clarification-required")
        }
        if (result != null && skipped != null) invalid("a shop-text result cannot also be skipped")
        if (result == null && input.safeRequestReady) invalid("clarification-required shop-text input cannot be safeRequestReady")
        if (result != null && !input.safeRequestReady) invalid("shop-text result requires safeRequestReady input")
        return ShopTextResponse(common, regions, input, result, skipped)
    }

    /**
     * Extract only bounded, non-sensitive correlation/release fields without
     * weakening the strict consumer parser. This is used when a response
     * fails later validation so diagnostics can identify the request safely.
     */
    internal fun safeResponseMetadata(rawJson: String): SafeResponseMetadata {
        if (rawJson.toByteArray(StandardCharsets.UTF_8).size > MAX_RESPONSE_BYTES) {
            return SafeResponseMetadata(null, null, null)
        }
        val root = try {
            parseRoot(rawJson)
        } catch (_: Exception) {
            return SafeResponseMetadata(null, null, null)
        }
        return SafeResponseMetadata(
            requestId = safeIdentifier(root, "requestId", 96),
            releaseId = safeIdentifier(root, "releaseId", 320),
            releaseDate = safeDate(root, "releaseDate")
        )
    }

    /**
     * M11's provider edge keeps a small adapter envelope around the shared
     * shopping decision.  The HTTP contract exposes the shared decision
     * schema to consumers, but accepting the adapter envelope here lets the
     * Android bridge consume the actual service response without re-running
     * any shopping calculation.
     */
    private fun parseDecisionEnvelope(root: JSONObject, releaseDate: String): ShoppingDecision {
        val schema = requiredString(root, "schemaVersion", 160)
        return when (schema) {
            SHOPPING_SCHEMA_VERSION -> parseDecision(root, releaseDate)
            "valuepilot-argentina-shopping-intelligence-v1" -> {
                val provider = requiredString(root, "provider", 160)
                if (provider != "ARGENTINA_SEPA_PRECIOS_CLAROS") invalid("unsupported shopping provider")
                val adapterReleaseDate = requiredString(root, "releaseDate", 32)
                parseDate(adapterReleaseDate, "adapter releaseDate")
                if (adapterReleaseDate != releaseDate) invalid("adapter releaseDate differs from response release")
                if (requiredBoolean(root, "productionUiAuthorized")) invalid("production UI is not authorized")
                if (requiredBoolean(root, "androidNetworkingAuthorized")) invalid("Android networking is not authorized")
                val decision = parseDecision(requiredObject(root, "decision"), releaseDate)
                val safety = if (root.has("providerSafety") && !root.isNull("providerSafety")) {
                    parseDiagnostics(requiredObject(root, "providerSafety"), "provider safety")
                } else {
                    emptyMap()
                }
                if (safety.isNotEmpty() && safety["availability"] != Availability.UNKNOWN.name) {
                    invalid("provider safety availability must remain UNKNOWN")
                }
                decision
            }
            else -> invalid("unsupported shopping result schema")
        }
    }

    private fun parseCommon(
        root: JSONObject,
        requireEvidence: Boolean,
        expectedRelease: ExpectedRelease?
    ): CommonEnvelope {
        val version = requiredString(root, "apiContractVersion", 96)
        requireVersion(version)
        val requestId = requiredId(root, "requestId", 96)
        val releaseId = requiredString(root, "releaseId", 320)
        val releaseDate = requiredString(root, "releaseDate", 32)
        parseDate(releaseDate, "releaseDate")
        val freshness = requiredFreshness(root, "freshness")
        expectedRelease?.let { expected ->
            if (releaseId != expected.releaseId || releaseDate != expected.releaseDate) mismatch()
        }
        val semantics = if (requireEvidence) parseEvidenceSemantics(requiredObject(root, "evidenceSemantics")) else null
        val diagnostics = parseDiagnostics(requiredObject(root, "diagnostics"), "diagnostics")
        return CommonEnvelope(version, requestId, releaseId, releaseDate, freshness, semantics, diagnostics)
    }

    private fun parseEvidenceSemantics(root: JSONObject): EvidenceSemantics {
        val availability = requiredString(root, "availability", 32)
        if (availability != Availability.UNKNOWN.name) invalid("availability must remain UNKNOWN")
        val distance = requiredString(root, "distance", 96)
        if (distance != "STRAIGHT_LINE_HAVERSINE_ONLY") invalid("unsupported distance semantics")
        val promotions = optionalString(root, "promotions", 96)
        if (promotions != null && promotions !in setOf("NOT_INCLUDED_UNLESS_ELIGIBILITY_KNOWN", "UNKNOWN_ELIGIBILITY_NOT_INCLUDED_IN_BASE_TOTAL")) {
            invalid("unsupported promotion semantics")
        }
        val notInventory = optionalBoolean(root, "pricePublicationIsNotInventory")
        if (notInventory == false) invalid("price publication cannot be treated as inventory")
        return EvidenceSemantics(Availability.UNKNOWN, distance, promotions, notInventory)
    }

    private fun parseSearchMatches(array: JSONArray, expectedReleaseDate: String? = null): List<SearchMatch> {
        boundedArray(array, MAX_MATCHES, "matches")
        val seenLines = mutableSetOf<String>()
        return (0 until array.length()).map { index ->
            val match = array.get(index) as? JSONObject ?: invalid("search match must be an object")
            val lineId = requiredId(match, "lineId", 240)
            if (!seenLines.add(lineId)) invalid("duplicate search line id")
            val query = boundedQuery(requiredString(match, "query", MAX_QUERY_CHARS), "match query")
            val candidates = parseProductCandidates(requiredArray(match, "productCandidates"), expectedReleaseDate)
            SearchMatch(lineId, query, candidates)
        }
    }

    private fun parseBoundedIdArray(array: JSONArray, maximum: Int, label: String): List<String> {
        boundedArray(array, maximum, label)
        val values = (0 until array.length()).map { index ->
            val value = array.get(index) as? String ?: invalid("$label must contain strings")
            if (!ID_PATTERN.matches(value)) invalid("$label contains an invalid id")
            value
        }
        if (values.distinct().size != values.size) invalid("$label contains duplicate ids")
        return values
    }

    private fun parseDiscoveryClarification(root: JSONObject): DiscoveryClarification {
        val dimension = requiredId(root, "dimensionId", 96)
        val choicesArray = requiredArray(root, "choices")
        boundedArray(choicesArray, MAX_DISCOVERY_FACET_PAGE_SIZE, "discovery choices")
        if (choicesArray.length() < 2) invalid("discovery clarification needs at least two choices")
        val choices = (0 until choicesArray.length()).map { index ->
            val choice = choicesArray.get(index) as? JSONObject ?: invalid("discovery choice must be an object")
            val id = requiredId(choice, "id", 160)
            val predicateId = requiredId(choice, "predicateId", 160)
            if (id != predicateId) invalid("discovery choice id disagrees with predicateId")
            DiscoveryChoice(
                id = id,
                predicateId = predicateId,
                labelEs = requiredText(choice, "labelEs", 160),
                labelEn = requiredText(choice, "labelEn", 160),
                productCount = requiredInt(choice, "productCount", 1, 500_000),
                sourceIdentityCount = requiredInt(choice, "sourceIdentityCount", 1, 500_000),
                countUnit = requiredString(choice, "countUnit", 64),
                hasChildren = requiredBoolean(choice, "hasChildren")
            ).also { parsed ->
                if (parsed.countUnit != "DISTINCT_PROVIDER_PRODUCT") invalid("unsupported discovery choice count unit")
            }
        }
        if (choices.map { it.id }.distinct().size != choices.size) invalid("duplicate discovery choices")
        val hasMore = requiredBoolean(root, "hasMore")
        val nextCursor = optionalString(root, "nextCursor", 2_048)
        if (hasMore && nextCursor == null) invalid("discovery facet cursor is missing")
        return DiscoveryClarification(dimension, choices, hasMore, nextCursor)
    }

    private fun parseProductCandidates(array: JSONArray, expectedReleaseDate: String? = null): List<ProductIdentity> {
        boundedArray(array, MAX_PRODUCT_CANDIDATES, "productCandidates")
        val byKey = linkedMapOf<String, ProductIdentity>()
        repeat(array.length()) { index ->
            val candidate = array.get(index) as? JSONObject ?: invalid("product candidate must be an object")
            val parsed = parseProductIdentity(candidate, expectedReleaseDate)
            val previous = byKey[parsed.productEvidenceKey]
            if (previous != null && previous != parsed) invalid("conflicting product identity evidence")
            if (!byKey.containsKey(parsed.productEvidenceKey)) {
                byKey[parsed.productEvidenceKey] = parsed
            }
        }
        return byKey.values.toList()
    }

    private fun parseProductIdentity(root: JSONObject, expectedReleaseDate: String? = null): ProductIdentity {
        val key = requiredId(root, "productEvidenceKey", 256)
        val name = requiredText(root, "name", 512)
        val brand = optionalString(root, "brand", 160)
        val providerProductId = optionalString(root, "providerProductId", 160)
        val gtin = optionalString(root, "gtin", 14)
        val gtinStatus = optionalString(root, "gtinStatus", 32)
        if (gtinStatus != null && gtinStatus !in setOf("VALID", "INVALID", "INVALID_OR_NOT_GTIN", "UNKNOWN")) invalid("unsupported gtinStatus")
        if (gtinStatus == "VALID") {
            if (gtin == null || !GTIN_PATTERN.matches(gtin) || !validGtin(gtin)) invalid("invalid checksum-valid GTIN")
        } else if (gtin != null && (!GTIN_PATTERN.matches(gtin) || !validGtin(gtin))) {
            invalid("invalid GTIN evidence")
        }
        val quantity = optionalProductQuantity(root, "quantity")
        val qualifiedOffers =
            if (!root.has("qualifiedOffers") || root.isNull("qualifiedOffers")) {
                emptyList()
            } else {
                parseQualifiedOffers(requiredArray(root, "qualifiedOffers"), key, expectedReleaseDate)
            }
        return ProductIdentity(key, name, brand, providerProductId, gtin, gtinStatus, quantity, qualifiedOffers)
    }

    private fun parseQualifiedOffers(
        array: JSONArray,
        productKey: String,
        expectedReleaseDate: String? = null
    ): List<SearchOffer> {
        boundedArray(array, MAX_SEARCH_OFFERS_PER_IDENTITY, "qualifiedOffers")
        val seen = mutableSetOf<String>()
        return (0 until array.length()).map { index ->
            val raw = array.get(index) as? JSONObject ?: invalid("qualified offer must be an object")
            val offerKey = requiredId(raw, "offerKey", 320)
            if (!seen.add(offerKey)) invalid("duplicate qualified offer key")
            val offerProductKey = requiredId(raw, "productEvidenceKey", 256)
            if (offerProductKey != productKey) invalid("qualified offer product key disagrees")
            val storeKey = requiredId(raw, "storeKey", 320)
            val storeName = requiredText(raw, "storeName", 240)
            val address = optionalStringMap(raw, "storeAddress", MAX_ADDRESS_ENTRIES, MAX_ADDRESS_VALUE_CHARS).orEmpty()
            val latitude = requiredDecimalString(raw, "storeLatitude", nonNegative = false)
            validateCoordinate(latitude, "qualified offer store latitude", -90, 90)
            val longitude = requiredDecimalString(raw, "storeLongitude", nonNegative = false)
            validateCoordinate(longitude, "qualified offer store longitude", -180, 180)
            val distance = requiredDecimalString(raw, "distanceKm", nonNegative = true)
            val listPrice = parseMoney(requiredObject(raw, "listPrice"), "qualified offer listPrice", positive = true, expectedCurrency = "ARS")
            val referencePrice = if (!raw.has("referencePrice") || raw.isNull("referencePrice")) null else parseMoney(requiredObject(raw, "referencePrice"), "qualified offer referencePrice", positive = true, expectedCurrency = "ARS")
            val quantity = optionalProductQuantity(raw, "quantity")
            val quantityStatus = optionalString(raw, "quantityStatus", 32)
            if (quantityStatus != null && quantityStatus !in setOf("KNOWN", "UNKNOWN", "UNSPECIFIED")) invalid("unsupported qualified offer quantityStatus")
            val unitPrice = if (!raw.has("unitPrice") || raw.isNull("unitPrice")) null else parseMoney(requiredObject(raw, "unitPrice"), "qualified offer unitPrice", positive = true, expectedCurrency = "ARS")
            if (quantityStatus == "KNOWN" && quantity == null) invalid("known qualified offer quantity is missing")
            if (quantityStatus == "UNKNOWN" && quantity != null) invalid("unknown qualified offer quantity must be absent")
            if (quantity == null && unitPrice != null) invalid("qualified offer unitPrice requires known quantity")
            val unitPriceBasis = optionalString(raw, "unitPriceBasis", 32)
                ?: raw.optJSONObject("unitPrice")?.optString("basis")?.takeIf { it.isNotBlank() }
            val releaseDate = requiredString(raw, "releaseDate", 32)
            parseDate(releaseDate, "qualified offer releaseDate")
            if (expectedReleaseDate != null && releaseDate != expectedReleaseDate) {
                invalid("qualified offer releaseDate differs from response release")
            }
            val freshness = requiredFreshness(raw, "freshnessStatus")
            val availability = requiredAvailability(raw, "availability")
            if (freshness != Freshness.FRESH) invalid("qualified offer freshness must be FRESH")
            if (availability != Availability.UNKNOWN) invalid("qualified offer availability must remain UNKNOWN")
            val provenanceRoot = requiredObject(raw, "provenance")
            val provenance = parseProvenance(provenanceRoot, releaseDate)
            SearchOffer(
                offerKey = offerKey,
                productEvidenceKey = offerProductKey,
                storeKey = storeKey,
                storeName = storeName,
                storeAddress = address,
                storeLatitude = latitude,
                storeLongitude = longitude,
                distanceKm = distance,
                listPrice = listPrice,
                referencePrice = referencePrice,
                quantity = quantity,
                quantityStatus = quantityStatus,
                unitPrice = unitPrice,
                unitPriceBasis = unitPriceBasis,
                releaseDate = releaseDate,
                freshnessStatus = freshness,
                availability = availability,
                provenance = provenance
            )
        }
    }

    /**
     * Catalog identities in the M11 response retain the provider-shaped
     * `{value, unit}` quantity.  Plan quantities use the normalized M6
     * `{dimension, amount, baseAmount, ...}` shape.  Accept either explicit
     * form, but never infer a quantity from a product title.
     */
    private fun optionalProductQuantity(root: JSONObject, name: String): Quantity? {
        if (!root.has(name) || root.isNull(name)) return null
        val quantity = requiredObject(root, name)
        if (quantity.has("dimension") || quantity.has("baseAmount")) {
            return parseQuantity(quantity, name, allowZero = false)
        }
        val amount = requiredDecimalString(quantity, "value", nonNegative = true)
        val decimal = BigDecimal(amount)
        if (decimal <= BigDecimal.ZERO) invalid("$name must be positive")
        val unit = requiredUnit(requiredString(quantity, "unit", 32))
        val (dimension, factor, baseUnit) = when (unit) {
            "g" -> Triple("MASS", BigDecimal.ONE, "g")
            "kg" -> Triple("MASS", BigDecimal("1000"), "g")
            "ml" -> Triple("VOLUME", BigDecimal.ONE, "ml")
            "l" -> Triple("VOLUME", BigDecimal("1000"), "ml")
            "count" -> Triple("COUNT", BigDecimal.ONE, "count")
            else -> invalid("unsupported $name unit")
        }
        return Quantity(
            dimension = dimension,
            amount = decimalText(decimal),
            unit = unit,
            baseAmount = decimalText(decimal.multiply(factor)),
            baseUnit = baseUnit,
        )
    }

    private fun parseInput(root: JSONObject): InputResult {
        val schema = requiredString(root, "schemaVersion", 160)
        if (schema != INPUT_SCHEMA_VERSION) invalid("unsupported input schema version")
        val policy = requiredString(root, "policyVersion", 160)
        if (policy != INPUT_POLICY_VERSION) invalid("unsupported input policy version")
        val original = requiredText(root, "originalText", MAX_TEXT_CHARS)
        val lineCount = requiredInt(root, "lineCount", 1, MAX_LINES)
        val linesArray = requiredArray(root, "lines")
        boundedArray(linesArray, MAX_LINES, "input lines")
        if (linesArray.length() != lineCount) invalid("input lineCount does not match lines")
        val lineIds = mutableSetOf<String>()
        val lines = (0 until linesArray.length()).map { index ->
            val line = linesArray.get(index) as? JSONObject ?: invalid("input line must be an object")
            val lineId = requiredId(line, "lineId", 240)
            if (!lineIds.add(lineId)) invalid("duplicate input line id")
            val resolution = requiredResolution(line, "resolution")
            val originalText = requiredText(line, "originalText", 256)
            val normalizedText = requiredText(line, "normalizedText", 256)
            if (normalizedText.isBlank()) invalid("normalized input line is blank")
            if (!line.has("normalizedIntent") || line.isNull("normalizedIntent")) invalid("input normalizedIntent is missing")
            requiredObject(line, "normalizedIntent")
            val recognized = optionalProductIdentity(line, "recognizedProduct")
            val quantityObject = requiredObject(line, "quantity")
            val quantityStatus = requiredString(quantityObject, "status", 32)
            if (quantityStatus !in setOf("KNOWN", "UNSPECIFIED", "INVALID")) invalid("unsupported quantity status")
            val quantity = optionalQuantity(quantityObject, "total", allowZero = false)
            val clarification = optionalClarification(line, "clarification")
            val suggestions = if (!line.has("suggestions") || line.isNull("suggestions")) emptyList() else parseProductCandidates(requiredArray(line, "suggestions"))
            val structured = optionalRequestItem(line, "structuredLine")
            val examined = optionalInt(line, "candidatesExamined", 0, 512) ?: 0
            InputLine(lineId, originalText, normalizedText, resolution, recognized, quantityStatus, quantity, clarification, suggestions, structured, examined)
        }
        val clarificationsArray = requiredArray(root, "clarifications")
        boundedArray(clarificationsArray, MAX_LINES, "clarifications")
        val clarifications = (0 until clarificationsArray.length()).map { index ->
            val value = clarificationsArray.get(index) as? JSONObject ?: invalid("clarification must be an object")
            parseClarification(value, lineIdOptional = true)
        }
        val ready = requiredBoolean(root, "safeRequestReady")
        val structuredArray = requiredArray(root, "structuredItems")
        boundedArray(structuredArray, MAX_LINES, "structuredItems")
        val structuredItems = (0 until structuredArray.length()).map { index ->
            val value = structuredArray.get(index) as? JSONObject ?: invalid("structured item must be an object")
            parseRequestItem(value)
        }
        val structuredIds = structuredItems.map { it.lineId }
        if (structuredIds.distinct().size != structuredIds.size) invalid("duplicate structured input line id")
        if (ready && (structuredItems.size != lines.size || lines.any { it.structuredItem == null })) invalid("safe input must contain one structured item per line")
        if (!ready && structuredItems.isNotEmpty()) invalid("unsafe input cannot contain structured items")
        val diagnostics = parseDiagnostics(requiredObject(root, "diagnostics"), "input diagnostics")
        val safety = requiredObject(root, "safety")
        if (requiredBoolean(safety, "unknownRemainsUnknown") != true) invalid("input safety must preserve unknown")
        if (requiredBoolean(safety, "unsafeAutomaticSubstitutionAllowed")) invalid("unsafe automatic substitution is not allowed")
        if (requiredBoolean(safety, "universalLanguageUnderstandingClaimed")) invalid("unbounded language understanding is not supported")
        return InputResult(schema, policy, original, lineCount, lines, clarifications, ready, structuredItems, diagnostics)
    }

    private fun parseDecision(root: JSONObject, releaseDate: String): ShoppingDecision {
        val schema = requiredString(root, "schemaVersion", 160)
        if (schema != SHOPPING_SCHEMA_VERSION) invalid("unsupported shopping schema version")
        val request = parseShoppingRequest(requiredObject(root, "request"))
        val plans = listOf(
            "cheapestSingleStore" to optionalPlan(root, "cheapestSingleStore", releaseDate),
            "closestCompletePriceEvidenceStore" to optionalPlan(root, "closestCompletePriceEvidenceStore", releaseDate),
            "cheapestTwoStoreCombination" to optionalPlan(root, "cheapestTwoStoreCombination", releaseDate),
            "cheapestUpToTwoStores" to optionalPlan(root, "cheapestUpToTwoStores", releaseDate),
            "cheapestPerLineUnboundedStores" to optionalPlan(root, "cheapestPerLineUnboundedStores", releaseDate)
        ).mapNotNull { it.second }
        val frontierArray = requiredArray(root, "decisionFrontier")
        boundedArray(frontierArray, MAX_FRONTIER_PLANS, "decisionFrontier")
        val frontier = (0 until frontierArray.length()).map { index ->
            val value = frontierArray.get(index) as? JSONObject ?: invalid("frontier plan must be an object")
            parsePlan(value, releaseDate)
        }
        val allPlans = plans + frontier
        val planIds = allPlans.map { it.planId }
        if (planIds.distinct().size != planIds.size) {
            // The named alternatives often repeat the same plan. Identical repeats
            // are normal; a differing duplicate is caught by the line fingerprint
            // check below. Keep the repeated references out of the DTO list itself.
        }
        validateOfferEvidence(allPlans)
        val choiceRoot = requiredObject(root, "bestSensibleChoice")
        val choiceStatus = requiredString(choiceRoot, "status", 96)
        if (choiceStatus !in setOf("UNRESOLVED", "BEST_SENSIBLE_CHOICE_UNDER_POLICY")) invalid("unsupported sensible-choice status")
        val selected = optionalPlan(choiceRoot, "selectedPlan", releaseDate)
        if ((choiceStatus == "UNRESOLVED") != (selected == null)) invalid("sensible-choice status and selectedPlan disagree")
        val diagnostics = parseDiagnostics(requiredObject(root, "diagnostics"), "decision diagnostics")
        val safety = parseSafety(requiredObject(root, "safety"))
        return ShoppingDecision(
            schemaVersion = schema,
            request = request,
            cheapestSingleStore = optionalPlan(root, "cheapestSingleStore", releaseDate),
            closestCompletePriceEvidenceStore = optionalPlan(root, "closestCompletePriceEvidenceStore", releaseDate),
            cheapestTwoStoreCombination = optionalPlan(root, "cheapestTwoStoreCombination", releaseDate),
            cheapestUpToTwoStores = optionalPlan(root, "cheapestUpToTwoStores", releaseDate),
            cheapestPerLineUnboundedStores = optionalPlan(root, "cheapestPerLineUnboundedStores", releaseDate),
            decisionFrontier = frontier,
            bestSensibleChoice = BestSensibleChoice(choiceStatus, selected),
            diagnostics = diagnostics,
            safety = safety
        )
    }

    private fun parseShoppingRequest(root: JSONObject): ShoppingRequest {
        val latitude = requiredDecimalString(root, "latitude", nonNegative = false)
        validateCoordinate(latitude, "latitude", -90, 90)
        val longitude = requiredDecimalString(root, "longitude", nonNegative = false)
        validateCoordinate(longitude, "longitude", -180, 180)
        val radius = requiredDecimalString(root, "radiusKm", nonNegative = true)
        val itemsArray = requiredArray(root, "items")
        boundedArray(itemsArray, MAX_LINES, "shopping request items")
        if (itemsArray.length() == 0) invalid("shopping request must contain at least one item")
        val items = (0 until itemsArray.length()).map { index ->
            val item = itemsArray.get(index) as? JSONObject ?: invalid("shopping request item must be an object")
            parseDecisionRequestItem(item)
        }
        return ShoppingRequest(latitude, longitude, radius, items)
    }

    /**
     * The shared shopping engine serializes its internal request quantities as
     * an explicit normalized `requestedQuantity` object.  The Android request
     * model historically used the input-shaped `amount`/`unit` pair.  Accept
     * either representation at this response boundary, but never derive one
     * from product text or guess a unit: both paths require explicit quantity
     * evidence and retain the optional exact product identity key.
     */
    private fun parseDecisionRequestItem(root: JSONObject): RequestItem {
        if (root.has("amount") || root.has("unit")) {
            return parseRequestItem(root)
        }
        val lineId = requiredId(root, "lineId", 240)
        val query = boundedQuery(requiredString(root, "query", MAX_QUERY_CHARS), "item query")
        val quantity = parseQuantity(requiredObject(root, "requestedQuantity"), "requestedQuantity", allowZero = false)
        val productKey = optionalString(root, "productEvidenceKey", 256)?.also {
            if (!ID_PATTERN.matches(it)) invalid("property productEvidenceKey is not a bounded identifier")
        }
        return RequestItem(lineId, query, quantity.amount, quantity.unit, productKey)
    }

    private fun parseRequestItem(root: JSONObject): RequestItem {
        val lineId = requiredId(root, "lineId", 240)
        val query = boundedQuery(requiredString(root, "query", MAX_QUERY_CHARS), "item query")
        val amount = requiredDecimalString(root, "amount", nonNegative = false)
        if (BigDecimal(amount) <= BigDecimal.ZERO) invalid("item amount must be positive")
        val unit = requiredUnit(requiredString(root, "unit", 32))
        val productKey = optionalString(root, "productEvidenceKey", 256)?.also {
            if (!ID_PATTERN.matches(it)) invalid("property productEvidenceKey is not a bounded identifier")
        }
        return RequestItem(lineId, query, amount, unit, productKey)
    }

    private fun optionalRequestItem(root: JSONObject, name: String): RequestItem? {
        if (!root.has(name) || root.isNull(name)) return null
        return parseRequestItem(requiredObject(root, name))
    }

    private fun optionalPlan(root: JSONObject, name: String, releaseDate: String): Plan? {
        if (!root.has(name) || root.isNull(name)) return null
        return parsePlan(requiredObject(root, name), releaseDate)
    }

    private fun parsePlan(root: JSONObject, releaseDate: String): Plan {
        val type = requiredString(root, "type", 64)
        if (type !in setOf("SINGLE_STORE", "TWO_STORE", "REFERENCE_LOWER_BOUND")) invalid("unsupported plan type")
        val planId = requiredPlanId(root)
        val storeCount = requiredInt(root, "storeCount", 1, MAX_STORES)
        val storeKeys = parseStringArray(requiredArray(root, "storeKeys"), MAX_STORES, 320, "storeKeys")
        if (storeKeys.size != storeCount || storeKeys.distinct().size != storeKeys.size) invalid("plan store count/keys disagree")
        val storesArray = requiredArray(root, "stores")
        boundedArray(storesArray, MAX_STORES, "plan stores")
        if (storesArray.length() != storeCount) invalid("plan store count/metadata disagree")
        val stores = (0 until storesArray.length()).map { index -> parseStore(storesArray.get(index) as? JSONObject ?: invalid("plan store must be an object")) }
        if (stores.map { it.storeKey }.toSet() != storeKeys.toSet()) invalid("plan store metadata keys disagree")
        val linesArray = requiredArray(root, "lines")
        boundedArray(linesArray, MAX_PLAN_LINES, "plan lines")
        val lines = (0 until linesArray.length()).map { index -> parsePlanLine(linesArray.get(index) as? JSONObject ?: invalid("plan line must be an object"), releaseDate) }
        if (lines.map { it.lineId }.distinct().size != lines.size) invalid("duplicate plan line id")
        if (lines.any { it.storeKey !in storeKeys }) invalid("plan line references an undeclared store")
        val total = parseMoney(requiredObject(root, "totalArs"), "totalArs", positive = true, expectedCurrency = "ARS")
        val maxDistance = requiredDecimalString(root, "maxStraightLineDistanceKm", nonNegative = true)
        val complete = requiredBoolean(root, "completePriceEvidence")
        val missing = parseStringArray(requiredArray(root, "missingLineIds"), MAX_LINES, 240, "missingLineIds")
        if (missing.intersect(lines.map { it.lineId }.toSet()).isNotEmpty()) invalid("a plan line cannot also be missing")
        if (complete && missing.isNotEmpty()) invalid("complete plan cannot have missing line ids")
        if (!complete && missing.isEmpty()) invalid("incomplete plan must declare missing line ids")
        if (type == "SINGLE_STORE" && storeCount != 1) invalid("single-store plan has multiple stores")
        if (type == "TWO_STORE" && storeCount != 2) invalid("two-store plan does not have two stores")
        return Plan(type, planId, storeCount, storeKeys, stores, lines, total, maxDistance, complete, missing)
    }

    private fun parseStore(root: JSONObject): Store {
        val key = requiredId(root, "storeKey", 320)
        val name = requiredText(root, "storeName", 240)
        val distance = requiredDecimalString(root, "distanceKm", nonNegative = true)
        val latitude = requiredDecimalString(root, "latitude", nonNegative = false)
        validateCoordinate(latitude, "store latitude", -90, 90)
        val longitude = requiredDecimalString(root, "longitude", nonNegative = false)
        validateCoordinate(longitude, "store longitude", -180, 180)
        val address = optionalStringMap(root, "address", MAX_ADDRESS_ENTRIES, MAX_ADDRESS_VALUE_CHARS).orEmpty()
        return Store(key, name, distance, latitude, longitude, address)
    }

    private fun parsePlanLine(root: JSONObject, releaseDate: String): PlanLine {
        val lineId = requiredId(root, "lineId", 240)
        val productKey = requiredId(root, "productEvidenceKey", 256)
        val productName = requiredText(root, "productName", 512)
        val brand = optionalString(root, "brand", 160)
        val offerKey = requiredId(root, "offerKey", 320)
        val storeKey = requiredId(root, "storeKey", 320)
        val storeName = requiredText(root, "storeName", 240)
        val distance = requiredDecimalString(root, "distanceKm", nonNegative = true)
        val requested = parseQuantity(requiredObject(root, "requestedQuantity"), "requestedQuantity", allowZero = false)
        val packageQuantity = parseQuantity(requiredObject(root, "packageQuantity"), "packageQuantity", allowZero = false)
        val packageCount = requiredInt(root, "packageCount", 1, 1_000_000)
        val supplied = parseQuantity(requiredObject(root, "suppliedQuantity"), "suppliedQuantity", allowZero = false)
        val excess = parseQuantity(requiredObject(root, "excessQuantity"), "excessQuantity", allowZero = true)
        val lineTotal = parseMoney(requiredObject(root, "lineTotal"), "lineTotal", positive = true, expectedCurrency = "ARS")
        val packagePrice = parseMoney(requiredObject(root, "packagePrice"), "packagePrice", positive = true, expectedCurrency = "ARS")
        val availability = requiredAvailability(root, "availability")
        val freshness = requiredFreshness(root, "freshnessStatus")
        val promotions = parsePromotions(requiredArray(root, "promotions"))
        val provenance = parseProvenance(requiredObject(root, "provenance"), releaseDate)
        if (provenance.freshnessStatus != freshness) invalid("line provenance freshness disagrees")
        return PlanLine(lineId, productKey, productName, brand, offerKey, storeKey, storeName, distance, requested, packageQuantity, packageCount, supplied, excess, lineTotal, packagePrice, availability, freshness, promotions, provenance)
    }

    private fun parseQuantity(root: JSONObject, label: String, allowZero: Boolean): Quantity {
        val dimension = requiredString(root, "dimension", 32)
        if (dimension !in setOf("MASS", "VOLUME", "COUNT")) invalid("unsupported $label dimension")
        val amount = requiredDecimalString(root, "amount", nonNegative = allowZero)
        val baseAmount = requiredDecimalString(root, "baseAmount", nonNegative = allowZero)
        if (!allowZero && (BigDecimal(amount) <= BigDecimal.ZERO || BigDecimal(baseAmount) <= BigDecimal.ZERO)) invalid("$label must be positive")
        val unit = requiredUnit(requiredString(root, "unit", 32))
        val baseUnit = requiredUnit(requiredString(root, "baseUnit", 32))
        val expectedBase = when (dimension) {
            "MASS" -> "g"
            "VOLUME" -> "ml"
            "COUNT" -> "count"
            else -> invalid("unsupported quantity dimension")
        }
        if (baseUnit != expectedBase) invalid("$label base unit disagrees with dimension")
        if ((dimension == "MASS" && unit !in setOf("g", "kg")) || (dimension == "VOLUME" && unit !in setOf("ml", "l")) || (dimension == "COUNT" && unit != "count")) {
            invalid("$label unit disagrees with dimension")
        }
        return Quantity(dimension, amount, unit, baseAmount, baseUnit, allowZero)
    }

    private fun optionalQuantity(root: JSONObject, name: String, allowZero: Boolean): Quantity? {
        if (!root.has(name) || root.isNull(name)) return null
        return parseQuantity(requiredObject(root, name), name, allowZero)
    }

    private fun parseMoney(root: JSONObject, label: String, positive: Boolean, expectedCurrency: String? = null): Money {
        val amount = requiredDecimalString(root, "amount", nonNegative = !positive)
        val decimal = BigDecimal(amount)
        if (positive && decimal <= BigDecimal.ZERO) invalid("$label must be positive")
        val currency = requiredString(root, "currency", 3)
        if (!currency.matches(Regex("[A-Z]{3}"))) invalid("$label currency is invalid")
        if (expectedCurrency != null && currency != expectedCurrency) invalid("$label currency must be $expectedCurrency")
        return Money(amount, currency, decimal)
    }

    private fun parsePromotions(array: JSONArray): List<Promotion> {
        boundedArray(array, MAX_PROMOTIONS, "promotions")
        return (0 until array.length()).map { index ->
            val root = array.get(index) as? JSONObject ?: invalid("promotion must be an object")
            val eligibility = requiredString(root, "eligibility", 32)
            if (eligibility !in setOf("UNKNOWN", "ELIGIBLE", "INELIGIBLE", "NOT_APPLICABLE")) invalid("unsupported promotion eligibility")
            val raw = requiredObject(root, "raw")
            val condition = optionalString(raw, "condition", 240)
            val price = if (!raw.has("price") || raw.isNull("price")) null else parseMoney(requiredObject(raw, "price"), "promotion price", positive = true, expectedCurrency = "ARS")
            Promotion(eligibility, condition, price, raw.toString())
        }
    }

    private fun parseProvenance(root: JSONObject, releaseDate: String): Provenance {
        val provider = requiredText(root, "provider", 160)
        val date = requiredString(root, "releaseDate", 32)
        parseDate(date, "provenance releaseDate")
        if (date != releaseDate) invalid("provenance releaseDate differs from response release")
        val freshness = requiredFreshness(root, "freshnessStatus")
        val source = optionalString(root, "source", 240)
        return Provenance(provider, date, freshness, source)
    }

    private fun validateOfferEvidence(plans: List<Plan>) {
        data class Fingerprint(val product: String, val store: String, val amount: String, val currency: String, val freshness: Freshness, val provider: String)
        val seen = linkedMapOf<String, Fingerprint>()
        plans.flatMap { it.lines }.forEach { line ->
            val fingerprint = Fingerprint(line.productEvidenceKey, line.storeKey, line.packagePrice.amount, line.packagePrice.currency, line.freshnessStatus, line.provenance.provider)
            val previous = seen[line.offerKey]
            if (previous != null && previous != fingerprint) invalid("conflicting duplicate offer evidence")
        if (!seen.containsKey(line.offerKey)) {
            seen[line.offerKey] = fingerprint
        }
        }
    }

    private fun parseSafety(root: JSONObject): Map<String, String> {
        val values = parseDiagnostics(root, "safety")
        if (values["availability"] != Availability.UNKNOWN.name) invalid("shopping safety availability must remain UNKNOWN")
        if (values["distance"] != "STRAIGHT_LINE_HAVERSINE_ONLY") invalid("shopping safety distance is unsupported")
        if (values["routing"] != "NOT_PROVIDED") invalid("shopping safety must not claim routing")
        if (values["completePriceEvidenceIsNotInventory"] != "true") invalid("shopping safety must not equate price and inventory")
        return values
    }

    private fun parseDiagnostics(root: JSONObject, label: String): Map<String, String> {
        val keys = root.keys().asSequence().toList()
        if (keys.size > MAX_DIAGNOSTIC_ENTRIES) invalid("$label exceeds the bounded entry limit")
        val result = linkedMapOf<String, String>()
        keys.sorted().forEach { key ->
            if (key.length > 96) invalid("$label key is too long")
            val value = root.get(key)
            val text = when (value) {
                is String -> value
                is Number -> value.toString()
                is Boolean -> value.toString()
                is JSONArray -> parseDiagnosticArray(value, label, key)
                is JSONObject -> {
                    val nested = parseDiagnostics(value, "$label.$key")
                    nested.entries.joinToString(",") { (nestedKey, nestedValue) -> "$nestedKey=$nestedValue" }
                }
                else -> invalid("$label contains an unsupported value")
            }
            if (text.length > MAX_DIAGNOSTIC_VALUE_CHARS) invalid("$label value is too long")
            result[key] = text
        }
        return result
    }

    /**
     * The live backend uses a bounded string array for diagnostics such as
     * `regionsQueried`.  Keep diagnostics non-authoritative and deterministic:
     * accept only scalar, bounded values and project them to one stable text
     * value. Nested objects/arrays and nulls remain rejected rather than guessed.
     */
    private fun parseDiagnosticArray(array: JSONArray, label: String, key: String): String {
        if (array.length() > MAX_DIAGNOSTIC_ARRAY_ITEMS) {
            invalid("$label.$key array exceeds its bounded item limit")
        }
        return (0 until array.length()).joinToString(",") { index ->
            val value = array.get(index)
            val text =
                when (value) {
                    is String -> value
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    else -> invalid("$label.$key contains an unsupported array value")
                }
            if (text.length > MAX_DIAGNOSTIC_VALUE_CHARS) {
                invalid("$label.$key array value is too long")
            }
            text
        }
    }

    private fun parseClarification(root: JSONObject, lineIdOptional: Boolean): InputClarification {
        val lineId = if (lineIdOptional) optionalString(root, "lineId", 240) else null
        val codeText = requiredString(root, "code", 64)
        val code = try {
            ClarificationCode.valueOf(codeText)
        } catch (error: IllegalArgumentException) {
            invalid("unsupported clarification code")
        }
        val message = optionalString(root, "message", 512)
        if (root.has("choices") && !root.isNull("choices")) boundedArray(requiredArray(root, "choices"), MAX_PRODUCT_CANDIDATES, "clarification choices")
        return InputClarification(lineId, code, message)
    }

    private fun optionalClarification(root: JSONObject, name: String): InputClarification? {
        if (!root.has(name) || root.isNull(name)) return null
        return parseClarification(requiredObject(root, name), lineIdOptional = false)
    }

    private fun optionalStringMap(root: JSONObject, name: String, maxEntries: Int, maxValueChars: Int): Map<String, String>? {
        if (!root.has(name) || root.isNull(name)) return null
        val value = requiredObject(root, name)
        val keys = value.keys().asSequence().toList().sorted()
        if (keys.size > maxEntries) invalid("$name has too many entries")
        return keys.associateWith { key -> requiredString(value, key, maxValueChars) }
    }

    private fun requiredVersion(value: String) {
        if (value != API_VERSION) throw ContractException(FailureCode.UNSUPPORTED_VERSION, "unsupported backend API contract version")
    }

    private fun requireVersion(value: String) = requiredVersion(value)

    private fun optionalFreshness(root: JSONObject, name: String): Freshness? =
        if (!root.has(name) || root.isNull(name)) null else requiredFreshness(root, name)

    private fun requiredFreshness(root: JSONObject, name: String): Freshness =
        try {
            Freshness.valueOf(requiredString(root, name, 32))
        } catch (error: IllegalArgumentException) {
            invalid("unsupported freshness value")
        }

    private fun requiredAvailability(root: JSONObject, name: String): Availability {
        val value = requiredString(root, name, 32)
        if (value != Availability.UNKNOWN.name) invalid("availability must remain UNKNOWN")
        return Availability.UNKNOWN
    }

    private fun optionalSearchResolution(root: JSONObject, name: String): SearchResolution? =
        if (!root.has(name) || root.isNull(name)) null else try {
            SearchResolution.valueOf(requiredString(root, name, 48))
        } catch (error: IllegalArgumentException) {
            invalid("unsupported search resolution value")
        }

    private fun optionalResolution(root: JSONObject, name: String): Resolution? =
        if (!root.has(name) || root.isNull(name)) null else requiredResolution(root, name)

    private fun requiredResolution(root: JSONObject, name: String): Resolution =
        try {
            Resolution.valueOf(requiredString(root, name, 48))
        } catch (error: IllegalArgumentException) {
            invalid("unsupported resolution value")
        }

    private fun optionalDecisionSkipped(root: JSONObject, name: String): DecisionSkipped? =
        if (!root.has(name) || root.isNull(name)) null else try {
            DecisionSkipped.valueOf(requiredString(root, name, 64))
        } catch (error: IllegalArgumentException) {
            invalid("unsupported decisionSkipped value")
        }

    private fun requiredUnit(value: String): String {
        val normalized = value.trim().lowercase()
        return when (normalized) {
            "g", "gram", "grams", "gramo", "gramos", "kg", "kilogram", "kilograms", "kilogramo", "kilogramos" -> if (normalized == "kg" || normalized.startsWith("kilo")) "kg" else "g"
            "ml", "millilitre", "millilitres", "milliliter", "milliliters", "mililitro", "mililitros", "cc", "cm3" -> "ml"
            "l", "lt", "litre", "litres", "liter", "liters", "litro", "litros" -> "l"
            "count", "counts", "item", "items", "unit", "units", "each", "ea", "uni", "un" -> "count"
            else -> invalid("unsupported quantity unit")
        }
    }

    private fun requiredString(root: JSONObject, name: String, maxChars: Int): String {
        if (!root.has(name) || root.isNull(name)) invalid("missing required property $name")
        val value = root.get(name)
        if (value !is String) invalid("property $name must be a string")
        if (value.length > maxChars) invalid("property $name exceeds its bound")
        return value
    }

    private fun requiredText(root: JSONObject, name: String, maxChars: Int): String {
        val value = requiredString(root, name, maxChars)
        if (value.isBlank()) invalid("property $name must not be blank")
        return value
    }

    private fun optionalString(root: JSONObject, name: String, maxChars: Int): String? {
        if (!root.has(name) || root.isNull(name)) return null
        return requiredString(root, name, maxChars)
    }

    private fun safeIdentifier(root: JSONObject, name: String, maxChars: Int): String? {
        if (!root.has(name) || root.isNull(name)) return null
        val value = root.get(name)
        return (value as? String)?.takeIf { it.length <= maxChars && ID_PATTERN.matches(it) }
    }

    private fun safeDate(root: JSONObject, name: String): String? {
        if (!root.has(name) || root.isNull(name)) return null
        val value = root.get(name) as? String ?: return null
        return value.takeIf { DATE_PATTERN.matches(it) && validIsoDate(it) }
    }

    private fun requiredId(root: JSONObject, name: String, maxChars: Int): String {
        val value = requiredText(root, name, maxChars)
        if (!ID_PATTERN.matches(value)) invalid("property $name is not a bounded identifier")
        return value
    }

    private fun requiredPlanId(root: JSONObject): String {
        val value = requiredText(root, "planId", 700)
        if (!PLAN_ID_PATTERN.matches(value)) invalid("property planId is not a bounded identifier")
        return value
    }

    private fun requiredBoolean(root: JSONObject, name: String): Boolean {
        if (!root.has(name) || root.isNull(name) || root.get(name) !is Boolean) invalid("property $name must be a boolean")
        return root.getBoolean(name)
    }

    private fun optionalBoolean(root: JSONObject, name: String): Boolean? {
        if (!root.has(name) || root.isNull(name)) return null
        return requiredBoolean(root, name)
    }

    private fun requiredInt(root: JSONObject, name: String, minimum: Int, maximum: Int): Int {
        if (!root.has(name) || root.isNull(name)) invalid("missing required property $name")
        val value = root.get(name)
        if (value !is Number) invalid("property $name must be an integer")
        val decimal = try {
            BigDecimal(value.toString())
        } catch (error: NumberFormatException) {
            invalid("property $name is not numeric")
        }
        if (decimal.scale() > 0 || decimal < BigDecimal.valueOf(minimum.toLong()) || decimal > BigDecimal.valueOf(maximum.toLong())) invalid("property $name is outside its bound")
        return decimal.toInt()
    }

    private fun optionalInt(root: JSONObject, name: String, minimum: Int, maximum: Int): Int? {
        if (!root.has(name) || root.isNull(name)) return null
        return requiredInt(root, name, minimum, maximum)
    }

    private fun requiredDecimalString(root: JSONObject, name: String, nonNegative: Boolean): String {
        val value = requiredString(root, name, 64)
        validateDecimal(value, name, nonNegative)
        return value
    }

    private fun validateDecimal(value: String, name: String, nonNegative: Boolean) {
        if (!DECIMAL_PATTERN.matches(value)) invalid("property $name has invalid decimal syntax")
        val decimal = try {
            BigDecimal(value)
        } catch (error: NumberFormatException) {
            invalid("property $name is not finite")
        }
        if (nonNegative && decimal < BigDecimal.ZERO) invalid("property $name must be non-negative")
    }

    private fun decimalText(value: BigDecimal): String {
        val normalized = value.stripTrailingZeros().toPlainString()
        return if (normalized == "-0") "0" else normalized
    }

    private fun validateCoordinate(value: String, name: String, minimum: Int, maximum: Int) {
        validateDecimal(value, name, nonNegative = false)
        val decimal = BigDecimal(value)
        if (decimal < BigDecimal.valueOf(minimum.toLong()) || decimal > BigDecimal.valueOf(maximum.toLong())) invalid("$name is outside bounds")
    }

    private fun boundedQuery(value: String, label: String): String {
        if (value.trim().isEmpty() || value.length > MAX_QUERY_CHARS) invalid("$label is outside its bound")
        return value
    }

    private fun parseDate(value: String, label: String) {
        if (!DATE_PATTERN.matches(value)) invalid("$label has invalid date syntax")
        if (!validIsoDate(value)) invalid("$label is not a valid date")
    }

    private fun parseStringArray(array: JSONArray, maxItems: Int, maxChars: Int, label: String): List<String> {
        boundedArray(array, maxItems, label)
        return (0 until array.length()).map { index ->
            val value = array.get(index)
            if (value !is String || value.isBlank() || value.length > maxChars) invalid("$label contains an invalid string")
            value
        }
    }

    private fun boundedArray(array: JSONArray, maximum: Int, label: String) {
        if (array.length() > maximum) invalid("$label exceeds its bounded length")
    }

    private fun requiredArray(root: JSONObject, name: String): JSONArray {
        if (!root.has(name) || root.isNull(name) || root.get(name) !is JSONArray) invalid("property $name must be an array")
        return root.getJSONArray(name)
    }

    private fun requiredObject(root: JSONObject, name: String): JSONObject {
        if (!root.has(name) || root.isNull(name) || root.get(name) !is JSONObject) invalid("property $name must be an object")
        return root.getJSONObject(name)
    }

    private fun optionalProductIdentity(root: JSONObject, name: String): ProductIdentity? {
        if (!root.has(name) || root.isNull(name)) return null
        return parseProductIdentity(requiredObject(root, name))
    }

    private fun invalid(message: String): Nothing = throw ContractException(FailureCode.CONTRACT_INVALID, message)

    private fun mismatch(): Nothing = throw ContractException(FailureCode.RELEASE_MISMATCH, "backend response release does not match the pinned release")

    private fun validGtin(value: String): Boolean {
        var sum = 0
        // The check digit is the final character.  Starting immediately to its
        // left, GTIN data digits alternate weights 3 and 1 all the way through
        // the first digit; excluding that first digit would accept/reject real
        // barcodes incorrectly.
        for (index in value.lastIndex - 1 downTo 0) {
            val digit = value[index] - '0'
            val distanceFromCheck = value.lastIndex - index
            sum += digit * if (distanceFromCheck % 2 == 1) 3 else 1
        }
        val check = value.last() - '0'
        return (10 - (sum % 10)) % 10 == check
    }

    /** API-23-safe strict calendar validation for the contract's ISO date fields. */
    private fun validIsoDate(value: String): Boolean {
        val format =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
        val position = ParsePosition(0)
        val parsed = format.parse(value, position) ?: return false
        return position.index == value.length && format.format(parsed) == value
    }
}

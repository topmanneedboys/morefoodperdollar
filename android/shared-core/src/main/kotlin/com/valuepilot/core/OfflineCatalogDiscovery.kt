package com.valuepilot.core

/**
 * A product record used only for offline catalog discovery.
 *
 * Prices, promotions, availability, package quantities and store claims are
 * intentionally absent. Those facts must arrive through their own evidence
 * rails and the existing production evaluators.
 */
data class OfflineCatalogProduct(
    val recordId: String,
    val providerId: EvidenceProviderId,
    val dataset: EvidenceDatasetNamespace,
    val sourceIdentity: SourceProductIdentity,
    val displayName: String,
    val brand: String? = null,
    val canonicalSearchName: String,
    val canonicalSearchBrand: String? = null,
    val canonicalSearchAliases: List<String> = emptyList()
) {
    init {
        require(recordId.matches(ID_PATTERN))
        require(displayName.isNotBlank() && displayName.length <= 240)
        require(canonicalSearchName.isNotBlank() && canonicalSearchName.length <= 240)
        require(canonicalSearchName == canonicalSearchName.trim())
        require(canonicalSearchName.all { it.isLowerCase() || it.isDigit() || it == ' ' }) {
            "Canonical catalog names must be lowercase search tokens"
        }
        require(canonicalSearchName.split(' ').none { it.isBlank() })
        brand?.let { require(it.isNotBlank() && it.length <= 160) }
        canonicalSearchBrand?.let {
            require(it.isNotBlank() && it.length <= 160)
            require(it == it.trim())
            require(it.all { character -> character.isLowerCase() || character.isDigit() || character == ' ' })
        }
        require(canonicalSearchAliases.size <= MAX_ALIASES)
        require(canonicalSearchAliases.distinct().size == canonicalSearchAliases.size)
        canonicalSearchAliases.forEach { alias ->
            require(alias.isNotBlank() && alias.length <= 160)
            require(alias == alias.trim())
            require(alias.all { character -> character.isLowerCase() || character.isDigit() || character == ' ' })
        }
        require(
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = providerId,
                identity = sourceIdentity
            ) != null
        ) {
            "Catalog records must retain at least one usable source identity"
        }
    }

    val canonicalGtin: String?
        get() = sourceIdentity.gtin?.let(GtinValidation::canonicalOrNull)

    val searchableFields: List<String>
        get() =
            buildList {
                add(canonicalSearchName)
                canonicalSearchBrand?.let(::add)
                addAll(canonicalSearchAliases)
            }

    fun identitySuggestion(
        itemKey: ShoppingItemKey,
        candidateId: String = recordId
    ): PracticalShoppingProductIdentityCandidate =
        PracticalShoppingProductIdentityCandidate(
            candidateId = candidateId,
            itemKey = itemKey,
            providerId = providerId,
            sourceIdentity = sourceIdentity,
            relationship = PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION,
            dataset = dataset
        )

    companion object {
        const val MAX_ALIASES = 16
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9._:-]{0,159}")
    }
}

data class OfflineCatalogDiscoveryRequest(
    val rawQuery: String,
    val candidates: List<OfflineCatalogProduct>,
    val maxResults: Int = MAX_RESULTS
) {
    init {
        require(rawQuery.isNotBlank()) { "Catalog discovery query must not be blank" }
        require(rawQuery.length <= MAX_QUERY_CHARS) {
            "Catalog discovery query exceeds the bounded character limit"
        }
        require(candidates.size <= MAX_CANDIDATES) {
            "Catalog discovery candidates exceed the bounded limit"
        }
        require(maxResults in 1..MAX_RESULTS)
        val recordIds = candidates.map { it.recordId }
        require(recordIds.distinct().size == recordIds.size) {
            "Catalog discovery candidate record ids must be unique"
        }
    }

    companion object {
        const val MAX_QUERY_CHARS = 160
        const val MAX_CANDIDATES = 200
        const val MAX_RESULTS = 24
    }
}

enum class OfflineCatalogMatchKind {
    EXACT_GTIN,
    EXACT_NAME,
    TOKEN_MATCH,
    PREFIX_MATCH,
    TYPO_MATCH
}

data class OfflineCatalogDiscoveryMatch(
    val product: OfflineCatalogProduct,
    val kind: OfflineCatalogMatchKind
)

data class OfflineCatalogDiscoveryResult(
    val normalizedQuery: String,
    val evaluatedCandidateCount: Int,
    val matches: List<OfflineCatalogDiscoveryMatch>
) {
    init {
        require(normalizedQuery.isNotBlank())
        require(evaluatedCandidateCount >= matches.size)
        require(matches.size <= OfflineCatalogDiscoveryRequest.MAX_RESULTS)
        val recordIds = matches.map { it.product.recordId }
        require(recordIds.distinct().size == recordIds.size)
    }
}

/**
 * Bounded, deterministic catalog matching. The host may pre-filter candidates
 * through a compact snapshot index; this engine performs the final match and
 * ordering only. No provider, source, price or availability preference enters
 * the result.
 */
object OfflineCatalogDiscoveryEngine {

    fun discover(
        request: OfflineCatalogDiscoveryRequest,
        canonicalizer: TextCanonicalizer
    ): OfflineCatalogDiscoveryResult {
        val normalizedQuery = canonicalizer.search(request.rawQuery)
        require(normalizedQuery.isNotBlank())

        val queryGtin = normalizedQuery.takeIf { it.all(Char::isDigit) }
            ?.let(GtinValidation::canonicalOrNull)

        if (queryGtin != null) {
            val exact =
                request.candidates
                    .asSequence()
                    .filter { it.canonicalGtin == queryGtin }
                    .sortedBy { it.recordId }
                    .take(request.maxResults)
                    .map { OfflineCatalogDiscoveryMatch(it, OfflineCatalogMatchKind.EXACT_GTIN) }
                    .toList()
            return OfflineCatalogDiscoveryResult(
                normalizedQuery = normalizedQuery,
                evaluatedCandidateCount = request.candidates.size,
                matches = exact
            )
        }

        val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank).distinct()
        val matches =
            matchingCandidates(
                products = request.candidates,
                normalizedQuery = normalizedQuery,
                queryTokens = queryTokens
            ).take(request.maxResults)

        return OfflineCatalogDiscoveryResult(
            normalizedQuery = normalizedQuery,
            evaluatedCandidateCount = request.candidates.size,
            matches = matches
        )
    }

    internal fun matchingCandidates(
        products: List<OfflineCatalogProduct>,
        normalizedQuery: String,
        queryTokens: List<String>
    ): List<OfflineCatalogDiscoveryMatch> {
        val queryGtin = normalizedQuery.takeIf { it.all(Char::isDigit) }
            ?.let(GtinValidation::canonicalOrNull)
        if (queryGtin != null) {
            return products
                .asSequence()
                .filter { it.canonicalGtin == queryGtin }
                .sortedBy { it.recordId }
                .map { OfflineCatalogDiscoveryMatch(it, OfflineCatalogMatchKind.EXACT_GTIN) }
                .toList()
        }

        return products
            .mapNotNull { product -> match(product, normalizedQuery, queryTokens) }
            .sortedWith(
                compareBy<OfflineCatalogDiscoveryMatch>(
                    { it.kind.ordinal },
                    { extraTokenCount(it.product, queryTokens) },
                    { it.product.canonicalSearchName },
                    { it.product.recordId }
                )
            )
    }

    private fun match(
        product: OfflineCatalogProduct,
        normalizedQuery: String,
        queryTokens: List<String>
    ): OfflineCatalogDiscoveryMatch? {
        if (product.canonicalSearchName == normalizedQuery) {
            return OfflineCatalogDiscoveryMatch(product, OfflineCatalogMatchKind.EXACT_NAME)
        }

        val fields = product.searchableFields
        val fieldTokens = fields.flatMap { it.split(' ') }.toSet()
        if (queryTokens.all(fieldTokens::contains)) {
            return OfflineCatalogDiscoveryMatch(product, OfflineCatalogMatchKind.TOKEN_MATCH)
        }

        if (queryTokens.all { queryToken ->
                fieldTokens.any { fieldToken -> prefixCompatible(queryToken, fieldToken) }
            }
        ) {
            return OfflineCatalogDiscoveryMatch(product, OfflineCatalogMatchKind.PREFIX_MATCH)
        }

        if (queryTokens.all { queryToken ->
                fieldTokens.any { fieldToken -> typoCompatible(queryToken, fieldToken) }
            }
        ) {
            return OfflineCatalogDiscoveryMatch(product, OfflineCatalogMatchKind.TYPO_MATCH)
        }

        return null
    }

    private fun prefixCompatible(left: String, right: String): Boolean {
        if (left.length < MIN_PREFIX_TOKEN_LENGTH || right.length < MIN_PREFIX_TOKEN_LENGTH) {
            return false
        }
        return left.startsWith(right) || right.startsWith(left)
    }

    private fun typoCompatible(left: String, right: String): Boolean {
        if (left.length < MIN_TYPO_TOKEN_LENGTH || right.length < MIN_TYPO_TOKEN_LENGTH) {
            return false
        }
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        return levenshteinAtMostOne(left, right)
    }

    private fun levenshteinAtMostOne(left: String, right: String): Boolean {
        if (left == right) return true
        if (kotlin.math.abs(left.length - right.length) > 1) return false

        var leftIndex = 0
        var rightIndex = 0
        var edits = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            if (left[leftIndex] == right[rightIndex]) {
                leftIndex++
                rightIndex++
                continue
            }
            edits++
            if (edits > 1) return false
            when {
                left.length == right.length -> {
                    leftIndex++
                    rightIndex++
                }
                left.length > right.length -> leftIndex++
                else -> rightIndex++
            }
        }
        if (leftIndex < left.length || rightIndex < right.length) edits++
        return edits <= 1
    }

    private fun extraTokenCount(
        product: OfflineCatalogProduct,
        queryTokens: List<String>
    ): Int {
        val productTokens = product.canonicalSearchName.split(' ').filter(String::isNotBlank).toSet()
        return (productTokens - queryTokens.toSet()).size
    }

    private const val MIN_PREFIX_TOKEN_LENGTH = 3
    private const val MIN_TYPO_TOKEN_LENGTH = 5
}

/**
 * Bounded adapter for snapshots larger than the final discovery request bound.
 *
 * The snapshot may contain up to [MAX_INDEX_PRODUCTS] identity records, while
 * the final matching engine intentionally evaluates at most
 * [OfflineCatalogDiscoveryRequest.MAX_CANDIDATES] candidates.  Matching stays
 * deterministic and provider-neutral: the index scans the immutable snapshot
 * once, applies the same core ordering, and forwards only the bounded prefix.
 */
class OfflineCatalogDiscoveryIndex private constructor(
    products: List<OfflineCatalogProduct>
) {
    private val products = products.toList()

    init {
        require(this.products.size <= MAX_INDEX_PRODUCTS) {
            "Offline catalog index exceeds the bounded product limit"
        }
        val recordIds = this.products.map { it.recordId }
        require(recordIds.distinct().size == recordIds.size) {
            "Offline catalog index record ids must be unique"
        }
    }

    fun discover(
        rawQuery: String,
        canonicalizer: TextCanonicalizer,
        maxResults: Int = OfflineCatalogDiscoveryRequest.MAX_RESULTS
    ): OfflineCatalogDiscoveryResult {
        val request =
            OfflineCatalogDiscoveryRequest(
                rawQuery = rawQuery,
                candidates = emptyList(),
                maxResults = maxResults
            )
        val normalizedQuery = canonicalizer.search(request.rawQuery)
        require(normalizedQuery.isNotBlank())
        val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank).distinct()
        val boundedMatches =
            OfflineCatalogDiscoveryEngine
                .matchingCandidates(
                    products = products,
                    normalizedQuery = normalizedQuery,
                    queryTokens = queryTokens
                )
                .take(OfflineCatalogDiscoveryRequest.MAX_CANDIDATES)

        return OfflineCatalogDiscoveryResult(
            normalizedQuery = normalizedQuery,
            evaluatedCandidateCount = products.size,
            matches = boundedMatches.take(request.maxResults)
        )
    }

    companion object {
        const val MAX_INDEX_PRODUCTS = OfflineCatalogSnapshotManifest.MAX_TOTAL_RECORDS

        fun build(products: List<OfflineCatalogProduct>): OfflineCatalogDiscoveryIndex =
            OfflineCatalogDiscoveryIndex(products)
    }
}

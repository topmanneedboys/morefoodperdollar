package com.valuepilot.core

private const val MAX_PRACTICAL_SHOPPING_PRODUCT_IDENTITY_CANDIDATES = 256
private const val MAX_PRACTICAL_SHOPPING_PRODUCT_IDENTITY_CANDIDATES_PER_ITEM = 32

/**
 * Why one exact source product identity is being considered for one shopping intent.
 *
 * Only relationships that already establish an exact intent -> product choice may
 * be automatically bound. Catalog/text/semantic suggestions remain useful for a
 * later user-selection UI, but can never silently become production product truth.
 */
enum class PracticalShoppingProductIntentRelationship {
    /** The shopping request itself was captured with this exact product identity. */
    EXACT_PRODUCT_REQUEST,

    /** The user explicitly selected/confirmed this exact product for the intent. */
    USER_CONFIRMED_EXACT_PRODUCT,

    /** A locally saved preference originates from a prior explicit exact selection. */
    SAVED_EXACT_PREFERENCE,

    /** Catalog metadata proposes a possible product but does not prove intent equivalence. */
    CATALOG_SUGGESTION,

    /** Text/model/semantic logic proposes a possible product but is non-authoritative. */
    SEMANTIC_SUGGESTION,

    UNKNOWN;

    val automaticallyBindable: Boolean
        get() =
            this == EXACT_PRODUCT_REQUEST ||
                this == USER_CONFIRMED_EXACT_PRODUCT ||
                this == SAVED_EXACT_PREFERENCE
}

/**
 * Adapter-neutral product-identity candidate for one Practical Shopping item.
 *
 * The adapter supplies only source identity/provenance plus the already-established
 * relationship to the shopping intent. Product names, images, prices, descriptions
 * and similarity scores are intentionally absent, so this boundary cannot invent an
 * exact binding from fuzzy presentation metadata.
 *
 * [dataset] is retained only as source-isolation/audit provenance. It never makes a
 * candidate more authoritative and is not required for user/device/local-preference
 * origins that do not come from a dataset namespace.
 */
data class PracticalShoppingProductIdentityCandidate(
    val candidateId: String,
    val itemKey: ShoppingItemKey,
    val providerId: EvidenceProviderId,
    val sourceIdentity: SourceProductIdentity,
    val relationship: PracticalShoppingProductIntentRelationship,
    val dataset: EvidenceDatasetNamespace? = null
) {
    init {
        require(candidateId.isNotBlank() && candidateId.length <= 240)
    }
}

enum class PracticalShoppingProductIdentityCandidateBlocker {
    ITEM_NOT_REQUESTED,
    PRODUCT_KEY_UNAVAILABLE
}

data class PracticalShoppingProductIdentityCandidateEvaluation(
    val candidate: PracticalShoppingProductIdentityCandidate,
    val productKey: ProductionProductEvidenceKey?,
    val blockers: Set<PracticalShoppingProductIdentityCandidateBlocker>
) {
    val usable: Boolean
        get() = blockers.isEmpty()

    init {
        require((productKey != null) == usable)
    }
}

enum class PracticalShoppingProductIdentityResolutionStatus {
    /** No safe exact identity and no usable suggestion exists for this item yet. */
    UNRESOLVED,

    /** Exactly one distinct explicit exact identity is safe to bind automatically. */
    AUTO_BINDABLE,

    /** Suggestions or conflicting exact identities require an explicit choice. */
    NEEDS_EXPLICIT_SELECTION
}

data class PracticalShoppingProductIdentityResolution(
    val itemKey: ShoppingItemKey,
    val status: PracticalShoppingProductIdentityResolutionStatus,
    val selectedProductKey: ProductionProductEvidenceKey?,
    val supportingCandidateIds: List<String>,
    val suggestionCandidateIds: List<String>
) {
    init {
        require(supportingCandidateIds.distinct().size == supportingCandidateIds.size)
        require(suggestionCandidateIds.distinct().size == suggestionCandidateIds.size)
        require(
            (selectedProductKey != null) ==
                (status == PracticalShoppingProductIdentityResolutionStatus.AUTO_BINDABLE)
        )
        require(
            status != PracticalShoppingProductIdentityResolutionStatus.UNRESOLVED ||
                (supportingCandidateIds.isEmpty() && suggestionCandidateIds.isEmpty())
        )
    }
}

data class PracticalShoppingProductIdentityResolutionResult(
    val candidateEvaluations: List<PracticalShoppingProductIdentityCandidateEvaluation>,
    val itemResolutions: List<PracticalShoppingProductIdentityResolution>
) {
    val automaticBindings: Map<ShoppingItemKey, ProductionProductEvidenceKey>
        get() =
            itemResolutions
                .mapNotNull { resolution ->
                    resolution.selectedProductKey?.let { resolution.itemKey to it }
                }
                .toMap()
}

/**
 * Deterministic product-identity boundary for production Practical Shopping.
 *
 * This evaluator does not search, score, parse names, inspect prices, compare images,
 * call AI, access a network, or choose among fuzzy catalog matches. It only:
 *
 * 1. validates that a candidate belongs to the current shopping request;
 * 2. resolves the supplied source identity through the existing production product-key
 *    resolver (checksum-valid GTIN first, then provider-scoped item id/SKU);
 * 3. automatically binds an item only when all explicit exact candidates agree on one
 *    distinct production product key;
 * 4. keeps metadata/semantic candidates as suggestions requiring explicit selection;
 * 5. fails ambiguous conflicting exact identities into explicit selection rather than
 *    selecting by source preference, provider economics, name similarity, or price.
 */
object PracticalShoppingProductIdentityResolver {

    fun resolve(
        request: ShoppingRequest,
        candidates: List<PracticalShoppingProductIdentityCandidate>
    ): PracticalShoppingProductIdentityResolutionResult {
        require(candidates.size <= MAX_PRACTICAL_SHOPPING_PRODUCT_IDENTITY_CANDIDATES)

        val duplicateCandidateIds =
            candidates.groupBy { it.candidateId }.filterValues { it.size > 1 }.keys
        require(duplicateCandidateIds.isEmpty()) {
            "Practical Shopping product identity candidate ids must be unique"
        }

        candidates
            .groupingBy { it.itemKey }
            .eachCount()
            .forEach { (_, count) ->
                require(count <= MAX_PRACTICAL_SHOPPING_PRODUCT_IDENTITY_CANDIDATES_PER_ITEM) {
                    "Practical Shopping product identity candidates per item exceed the bound"
                }
            }

        val requestedItems = request.itemKeySet
        val evaluations =
            candidates
                .sortedWith(
                    compareBy<PracticalShoppingProductIdentityCandidate>(
                        { it.itemKey.value },
                        { it.candidateId }
                    )
                )
                .map { candidate ->
                    evaluateCandidate(candidate, requestedItems)
                }

        val evaluationsByItem = evaluations.groupBy { it.candidate.itemKey }
        val resolutions =
            request.itemKeys.map { itemKey ->
                resolveItem(
                    itemKey = itemKey,
                    evaluations = evaluationsByItem[itemKey].orEmpty()
                )
            }

        return PracticalShoppingProductIdentityResolutionResult(
            candidateEvaluations = evaluations,
            itemResolutions = resolutions
        )
    }

    private fun evaluateCandidate(
        candidate: PracticalShoppingProductIdentityCandidate,
        requestedItems: Set<ShoppingItemKey>
    ): PracticalShoppingProductIdentityCandidateEvaluation {
        val blockers = linkedSetOf<PracticalShoppingProductIdentityCandidateBlocker>()

        if (candidate.itemKey !in requestedItems) {
            blockers += PracticalShoppingProductIdentityCandidateBlocker.ITEM_NOT_REQUESTED
        }

        val productKey =
            ProductionProductEvidenceKeyResolver.resolve(
                providerId = candidate.providerId,
                identity = candidate.sourceIdentity
            )

        if (productKey == null) {
            blockers += PracticalShoppingProductIdentityCandidateBlocker.PRODUCT_KEY_UNAVAILABLE
        }

        return PracticalShoppingProductIdentityCandidateEvaluation(
            candidate = candidate,
            productKey = productKey.takeIf { blockers.isEmpty() },
            blockers = blockers
        )
    }

    private fun resolveItem(
        itemKey: ShoppingItemKey,
        evaluations: List<PracticalShoppingProductIdentityCandidateEvaluation>
    ): PracticalShoppingProductIdentityResolution {
        val usable = evaluations.filter { it.usable }
        val exact =
            usable.filter { it.candidate.relationship.automaticallyBindable }
        val suggestions =
            usable.filterNot { it.candidate.relationship.automaticallyBindable }

        val exactByKey =
            exact.groupBy { requireNotNull(it.productKey) }

        if (exactByKey.size == 1) {
            val selected = exactByKey.keys.single()
            return PracticalShoppingProductIdentityResolution(
                itemKey = itemKey,
                status = PracticalShoppingProductIdentityResolutionStatus.AUTO_BINDABLE,
                selectedProductKey = selected,
                supportingCandidateIds =
                    exactByKey.getValue(selected)
                        .map { it.candidate.candidateId }
                        .sorted(),
                suggestionCandidateIds =
                    suggestions.map { it.candidate.candidateId }.sorted()
            )
        }

        if (exactByKey.size > 1 || suggestions.isNotEmpty()) {
            return PracticalShoppingProductIdentityResolution(
                itemKey = itemKey,
                status = PracticalShoppingProductIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
                selectedProductKey = null,
                supportingCandidateIds =
                    exact.map { it.candidate.candidateId }.sorted(),
                suggestionCandidateIds =
                    suggestions.map { it.candidate.candidateId }.sorted()
            )
        }

        return PracticalShoppingProductIdentityResolution(
            itemKey = itemKey,
            status = PracticalShoppingProductIdentityResolutionStatus.UNRESOLVED,
            selectedProductKey = null,
            supportingCandidateIds = emptyList(),
            suggestionCandidateIds = emptyList()
        )
    }
}

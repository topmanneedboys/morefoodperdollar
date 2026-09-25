package com.valuepilot.core

private const val MAX_PRACTICAL_SHOPPING_STORE_IDENTITIES = 64
private const val MAX_PRACTICAL_SHOPPING_STORE_IDENTITY_CANDIDATES = 256
private const val MAX_PRACTICAL_SHOPPING_STORE_IDENTITY_CANDIDATES_PER_STORE = 32

/**
 * Exact merchant/location/channel identity for one logical Practical Shopping store.
 *
 * This is identity only. It intentionally contains no display name, coordinates,
 * route/travel values, price, ranking signal, or provider-economic information.
 */
data class PracticalShoppingStoreIdentityScope(
    val merchantKey: String,
    val locationKey: String?,
    val commerceChannelKey: String
) {
    init {
        require(merchantKey.isNotBlank() && merchantKey.length <= 240)
        require(locationKey == null || (locationKey.isNotBlank() && locationKey.length <= 240))
        require(commerceChannelKey.isNotBlank() && commerceChannelKey.length <= 160)
    }
}

/**
 * Why one complete offer scope is being considered for a logical store.
 *
 * Only explicitly established complete scopes may auto-resolve. A source location,
 * geocoder result, retailer-name match, coordinate proximity, or other discovery hint
 * remains a suggestion even when it happens to carry a proposed complete scope.
 */
enum class PracticalShoppingStoreIdentityRelationship {
    /** Reviewed source semantics explicitly assert this merchant/location/channel scope. */
    SOURCE_ASSERTED_EXACT_OFFER_SCOPE,

    /** The user explicitly confirmed this exact store/offer scope. */
    USER_CONFIRMED_EXACT_STORE,

    /** A local saved store preference comes from a prior explicit exact selection. */
    SAVED_EXACT_STORE,

    /** A source-specific location record suggests a possible store but does not prove merchant scope. */
    SOURCE_LOCATION_SUGGESTION,

    /** Name/address/geocoder/coordinate matching proposes a possible store only. */
    NAME_OR_GEO_SUGGESTION,

    UNKNOWN;

    val automaticallyBindable: Boolean
        get() =
            this == SOURCE_ASSERTED_EXACT_OFFER_SCOPE ||
                this == USER_CONFIRMED_EXACT_STORE ||
                this == SAVED_EXACT_STORE
}

/**
 * Source-isolated candidate for one logical store identity.
 *
 * Source-asserted exact scopes must carry provider + dataset provenance. Dataset
 * provenance never makes a suggestion authoritative by itself; the relationship
 * remains explicit and is evaluated independently.
 */
data class PracticalShoppingStoreIdentityCandidate(
    val candidateId: String,
    val storeKey: ShoppingStoreKey,
    val scope: PracticalShoppingStoreIdentityScope,
    val relationship: PracticalShoppingStoreIdentityRelationship,
    val providerId: EvidenceProviderId? = null,
    val dataset: EvidenceDatasetNamespace? = null
) {
    init {
        require(candidateId.isNotBlank() && candidateId.length <= 240)
        require(dataset == null || providerId != null) {
            "Dataset-backed store identity candidates require provider provenance"
        }
        if (relationship == PracticalShoppingStoreIdentityRelationship.SOURCE_ASSERTED_EXACT_OFFER_SCOPE) {
            require(providerId != null && dataset != null) {
                "Source-asserted exact store scope requires provider and dataset provenance"
            }
        }
    }
}

enum class PracticalShoppingStoreIdentityCandidateBlocker {
    STORE_NOT_REQUESTED
}

data class PracticalShoppingStoreIdentityCandidateEvaluation(
    val candidate: PracticalShoppingStoreIdentityCandidate,
    val blockers: Set<PracticalShoppingStoreIdentityCandidateBlocker>
) {
    val usable: Boolean
        get() = blockers.isEmpty()
}

enum class PracticalShoppingStoreIdentityResolutionStatus {
    UNRESOLVED,
    AUTO_BINDABLE,
    NEEDS_EXPLICIT_SELECTION
}

data class PracticalShoppingStoreIdentityResolution(
    val storeKey: ShoppingStoreKey,
    val status: PracticalShoppingStoreIdentityResolutionStatus,
    val selectedScope: PracticalShoppingStoreIdentityScope?,
    val supportingCandidateIds: List<String>,
    val suggestionCandidateIds: List<String>
) {
    init {
        require(supportingCandidateIds.distinct().size == supportingCandidateIds.size)
        require(suggestionCandidateIds.distinct().size == suggestionCandidateIds.size)
        require(
            (selectedScope != null) ==
                (status == PracticalShoppingStoreIdentityResolutionStatus.AUTO_BINDABLE)
        )
        require(
            status != PracticalShoppingStoreIdentityResolutionStatus.UNRESOLVED ||
                (supportingCandidateIds.isEmpty() && suggestionCandidateIds.isEmpty())
        )
    }
}

data class PracticalShoppingStoreIdentityResolutionResult(
    val candidateEvaluations: List<PracticalShoppingStoreIdentityCandidateEvaluation>,
    val storeResolutions: List<PracticalShoppingStoreIdentityResolution>
) {
    val automaticScopes: Map<ShoppingStoreKey, PracticalShoppingStoreIdentityScope>
        get() =
            storeResolutions
                .mapNotNull { resolution ->
                    resolution.selectedScope?.let { resolution.storeKey to it }
                }
                .toMap()
}

/**
 * Deterministic store/offer identity boundary for production Practical Shopping.
 *
 * This evaluator has no store names, addresses, coordinates, distance, route times,
 * prices, network client, or fuzzy-match score. It cannot discover stores. It only
 * decides whether already-supplied complete scopes are safe to bind automatically.
 *
 * Multiple exact candidates may corroborate one scope. Conflicting exact scopes are
 * never resolved by provider preference or source ordering; they require an explicit
 * selection. Location/name/geocoder suggestions never auto-bind.
 */
object PracticalShoppingStoreIdentityResolver {

    fun resolve(
        storeKeys: List<ShoppingStoreKey>,
        candidates: List<PracticalShoppingStoreIdentityCandidate>
    ): PracticalShoppingStoreIdentityResolutionResult {
        require(storeKeys.size <= MAX_PRACTICAL_SHOPPING_STORE_IDENTITIES)
        require(storeKeys.distinct().size == storeKeys.size) {
            "Practical Shopping store identity keys must be unique"
        }
        require(candidates.size <= MAX_PRACTICAL_SHOPPING_STORE_IDENTITY_CANDIDATES)

        val duplicateCandidateIds =
            candidates.groupBy { it.candidateId }.filterValues { it.size > 1 }.keys
        require(duplicateCandidateIds.isEmpty()) {
            "Practical Shopping store identity candidate ids must be unique"
        }

        candidates
            .groupingBy { it.storeKey }
            .eachCount()
            .forEach { (_, count) ->
                require(count <= MAX_PRACTICAL_SHOPPING_STORE_IDENTITY_CANDIDATES_PER_STORE) {
                    "Practical Shopping store identity candidates per store exceed the bound"
                }
            }

        val requestedStores = storeKeys.toSet()
        val evaluations =
            candidates
                .sortedWith(
                    compareBy<PracticalShoppingStoreIdentityCandidate>(
                        { it.storeKey.value },
                        { it.candidateId }
                    )
                )
                .map { candidate ->
                    PracticalShoppingStoreIdentityCandidateEvaluation(
                        candidate = candidate,
                        blockers =
                            if (candidate.storeKey in requestedStores) {
                                emptySet()
                            } else {
                                setOf(PracticalShoppingStoreIdentityCandidateBlocker.STORE_NOT_REQUESTED)
                            }
                    )
                }

        val evaluationsByStore = evaluations.groupBy { it.candidate.storeKey }
        val resolutions =
            storeKeys.map { storeKey ->
                resolveStore(
                    storeKey = storeKey,
                    evaluations = evaluationsByStore[storeKey].orEmpty()
                )
            }

        return PracticalShoppingStoreIdentityResolutionResult(
            candidateEvaluations = evaluations,
            storeResolutions = resolutions
        )
    }

    private fun resolveStore(
        storeKey: ShoppingStoreKey,
        evaluations: List<PracticalShoppingStoreIdentityCandidateEvaluation>
    ): PracticalShoppingStoreIdentityResolution {
        val usable = evaluations.filter { it.usable }
        val exact = usable.filter { it.candidate.relationship.automaticallyBindable }
        val suggestions = usable.filterNot { it.candidate.relationship.automaticallyBindable }
        val exactByScope = exact.groupBy { it.candidate.scope }

        if (exactByScope.size == 1) {
            val selected = exactByScope.keys.single()
            return PracticalShoppingStoreIdentityResolution(
                storeKey = storeKey,
                status = PracticalShoppingStoreIdentityResolutionStatus.AUTO_BINDABLE,
                selectedScope = selected,
                supportingCandidateIds =
                    exactByScope.getValue(selected)
                        .map { it.candidate.candidateId }
                        .sorted(),
                suggestionCandidateIds =
                    suggestions.map { it.candidate.candidateId }.sorted()
            )
        }

        if (exactByScope.size > 1 || suggestions.isNotEmpty()) {
            return PracticalShoppingStoreIdentityResolution(
                storeKey = storeKey,
                status = PracticalShoppingStoreIdentityResolutionStatus.NEEDS_EXPLICIT_SELECTION,
                selectedScope = null,
                supportingCandidateIds = exact.map { it.candidate.candidateId }.sorted(),
                suggestionCandidateIds = suggestions.map { it.candidate.candidateId }.sorted()
            )
        }

        return PracticalShoppingStoreIdentityResolution(
            storeKey = storeKey,
            status = PracticalShoppingStoreIdentityResolutionStatus.UNRESOLVED,
            selectedScope = null,
            supportingCandidateIds = emptyList(),
            suggestionCandidateIds = emptyList()
        )
    }
}

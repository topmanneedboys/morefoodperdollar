package com.valuepilot.core

private const val MAX_PRACTICAL_SHOPPING_TRAVEL_LEGS = 192
private const val MAX_PRACTICAL_SHOPPING_TRAVEL_CANDIDATES = 384
private const val MAX_PRACTICAL_SHOPPING_TRAVEL_CANDIDATES_PER_LEG = 16

/**
 * Opaque planning context for route/travel facts.
 *
 * [originContextId] binds user-to-store travel to the caller's current origin/session
 * without exposing coordinates to shared-core. [travelModeKey] prevents a driving
 * estimate from being reused as walking/transit/cycling evidence. Shared-core neither
 * derives nor interprets either value.
 */
data class PracticalShoppingTravelContext(
    val originContextId: String,
    val travelModeKey: String
) {
    init {
        require(originContextId.isNotBlank() && originContextId.length <= 240)
        require(travelModeKey.isNotBlank() && travelModeKey.length <= 96)
    }
}

/**
 * One requested route leg.
 *
 * [baseStoreKey] == null means current user origin -> [destinationStoreKey].
 * A non-null base means base store -> added store for an optional second stop.
 * Store identity is established elsewhere; this type never contains merchant names,
 * coordinates, addresses, or offer scope.
 */
data class PracticalShoppingTravelLeg(
    val baseStoreKey: ShoppingStoreKey?,
    val destinationStoreKey: ShoppingStoreKey
) {
    init {
        require(baseStoreKey == null || baseStoreKey != destinationStoreKey)
    }

    val isUserToStore: Boolean
        get() = baseStoreKey == null
}

enum class PracticalShoppingTravelRelationship {
    /** A route engine/provider produced this route estimate for the exact requested leg/context. */
    ROUTING_PROVIDER_ESTIMATE,

    /** The user explicitly confirmed this travel estimate for the exact leg/context. */
    USER_CONFIRMED_ESTIMATE,

    /** A saved estimate originates from a prior explicit route estimate for this same context. */
    SAVED_ROUTE_ESTIMATE,

    /** Straight-line/approximate/discovery distance that is not a route/travel fact. */
    APPROXIMATE_SUGGESTION,

    UNKNOWN;

    val automaticallyUsable: Boolean
        get() =
            this == ROUTING_PROVIDER_ESTIMATE ||
                this == USER_CONFIRMED_ESTIMATE ||
                this == SAVED_ROUTE_ESTIMATE
}

/**
 * Adapter-neutral travel candidate.
 *
 * No network access occurs here. A future router may create these values, but it must
 * supply the route calculation/observation time and an auditable opaque basis id.
 * Provider-generated route estimates require provider provenance; user-confirmed or
 * saved local estimates do not.
 */
data class PracticalShoppingTravelCandidate(
    val candidateId: String,
    val leg: PracticalShoppingTravelLeg,
    val context: PracticalShoppingTravelContext,
    val travel: ShoppingTravel,
    val relationship: PracticalShoppingTravelRelationship,
    val observedAtEpochMillis: Long,
    val basisId: String,
    val providerId: EvidenceProviderId? = null
) {
    init {
        require(candidateId.isNotBlank() && candidateId.length <= 240)
        require(observedAtEpochMillis > 0L)
        require(basisId.isNotBlank() && basisId.length <= 320)
        if (relationship == PracticalShoppingTravelRelationship.ROUTING_PROVIDER_ESTIMATE) {
            require(providerId != null) {
                "Routing-provider travel estimates require provider provenance"
            }
        }
    }
}

enum class PracticalShoppingTravelCandidateBlocker {
    LEG_NOT_REQUESTED,
    CONTEXT_MISMATCH,
    NON_ROUTABLE_RELATIONSHIP,
    FUTURE_DATED,
    STALE
}

data class PracticalShoppingTravelCandidateEvaluation(
    val candidate: PracticalShoppingTravelCandidate,
    val freshness: EvidenceFreshness,
    val blockers: Set<PracticalShoppingTravelCandidateBlocker>
) {
    val usable: Boolean
        get() = blockers.isEmpty()

    init {
        require(freshness != EvidenceFreshness.UNKNOWN)
        require(!usable || freshness == EvidenceFreshness.FRESH || freshness == EvidenceFreshness.AGING)
    }
}

enum class PracticalShoppingTravelResolutionStatus {
    UNRESOLVED,
    AUTO_USABLE,
    NEEDS_EXPLICIT_SELECTION
}

data class PracticalShoppingTravelResolution(
    val leg: PracticalShoppingTravelLeg,
    val status: PracticalShoppingTravelResolutionStatus,
    val selectedTravel: ShoppingTravel?,
    val supportingCandidateIds: List<String>,
    val suggestionCandidateIds: List<String>
) {
    init {
        require(supportingCandidateIds.distinct().size == supportingCandidateIds.size)
        require(suggestionCandidateIds.distinct().size == suggestionCandidateIds.size)
        require(
            (selectedTravel != null) ==
                (status == PracticalShoppingTravelResolutionStatus.AUTO_USABLE)
        )
        require(
            status != PracticalShoppingTravelResolutionStatus.UNRESOLVED ||
                (supportingCandidateIds.isEmpty() && suggestionCandidateIds.isEmpty())
        )
    }
}

data class PracticalShoppingTravelResolutionResult(
    val candidateEvaluations: List<PracticalShoppingTravelCandidateEvaluation>,
    val legResolutions: List<PracticalShoppingTravelResolution>
) {
    val automaticTravel: Map<PracticalShoppingTravelLeg, ShoppingTravel>
        get() =
            legResolutions
                .mapNotNull { resolution ->
                    resolution.selectedTravel?.let { resolution.leg to it }
                }
                .toMap()
}

/**
 * Deterministic route/travel-fact boundary for Practical Shopping.
 *
 * This evaluator owns no clock, coordinates, geocoder, router, network client, travel
 * preference, or hidden value-of-time score. The caller supplies the evaluation instant,
 * freshness policy, expected route context, requested legs, and already-calculated facts.
 *
 * Fresh/aging routable candidates may corroborate one exact [ShoppingTravel] value. If
 * fresh routable candidates disagree, no shorter/faster route wins automatically: an
 * explicit selection is required. Approximate/straight-line suggestions never become
 * planner travel facts. Store identity remains a separate prerequisite and cannot be
 * established by the presence of a route result.
 */
object PracticalShoppingTravelResolver {

    fun resolve(
        context: PracticalShoppingTravelContext,
        legs: List<PracticalShoppingTravelLeg>,
        candidates: List<PracticalShoppingTravelCandidate>,
        evaluatedAtEpochMillis: Long,
        freshnessPolicy: EvidenceFreshnessPolicy
    ): PracticalShoppingTravelResolutionResult {
        require(evaluatedAtEpochMillis > 0L)
        require(legs.size <= MAX_PRACTICAL_SHOPPING_TRAVEL_LEGS)
        require(legs.distinct().size == legs.size) {
            "Practical Shopping travel legs must be unique"
        }
        require(candidates.size <= MAX_PRACTICAL_SHOPPING_TRAVEL_CANDIDATES)

        val duplicateCandidateIds =
            candidates.groupBy { it.candidateId }.filterValues { it.size > 1 }.keys
        require(duplicateCandidateIds.isEmpty()) {
            "Practical Shopping travel candidate ids must be unique"
        }

        candidates
            .groupingBy { it.leg }
            .eachCount()
            .forEach { (_, count) ->
                require(count <= MAX_PRACTICAL_SHOPPING_TRAVEL_CANDIDATES_PER_LEG) {
                    "Practical Shopping travel candidates per leg exceed the bound"
                }
            }

        val requestedLegs = legs.toSet()
        val evaluations =
            candidates
                .sortedWith(
                    compareBy<PracticalShoppingTravelCandidate>(
                        { it.leg.baseStoreKey?.value.orEmpty() },
                        { it.leg.destinationStoreKey.value },
                        { it.candidateId }
                    )
                )
                .map { candidate ->
                    evaluateCandidate(
                        expectedContext = context,
                        requestedLegs = requestedLegs,
                        candidate = candidate,
                        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                        freshnessPolicy = freshnessPolicy
                    )
                }

        val evaluationsByLeg = evaluations.groupBy { it.candidate.leg }
        val resolutions =
            legs.map { leg ->
                resolveLeg(
                    leg = leg,
                    evaluations = evaluationsByLeg[leg].orEmpty()
                )
            }

        return PracticalShoppingTravelResolutionResult(
            candidateEvaluations = evaluations,
            legResolutions = resolutions
        )
    }

    private fun evaluateCandidate(
        expectedContext: PracticalShoppingTravelContext,
        requestedLegs: Set<PracticalShoppingTravelLeg>,
        candidate: PracticalShoppingTravelCandidate,
        evaluatedAtEpochMillis: Long,
        freshnessPolicy: EvidenceFreshnessPolicy
    ): PracticalShoppingTravelCandidateEvaluation {
        val blockers = linkedSetOf<PracticalShoppingTravelCandidateBlocker>()

        if (candidate.leg !in requestedLegs) {
            blockers += PracticalShoppingTravelCandidateBlocker.LEG_NOT_REQUESTED
        }
        if (candidate.context != expectedContext) {
            blockers += PracticalShoppingTravelCandidateBlocker.CONTEXT_MISMATCH
        }
        if (!candidate.relationship.automaticallyUsable) {
            blockers += PracticalShoppingTravelCandidateBlocker.NON_ROUTABLE_RELATIONSHIP
        }

        val freshness =
            EvidenceFreshnessEvaluator.classify(
                observedAtEpochMillis = candidate.observedAtEpochMillis,
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                policy = freshnessPolicy
            )

        when (freshness) {
            EvidenceFreshness.FUTURE_DATED ->
                blockers += PracticalShoppingTravelCandidateBlocker.FUTURE_DATED
            EvidenceFreshness.STALE ->
                blockers += PracticalShoppingTravelCandidateBlocker.STALE
            EvidenceFreshness.FRESH,
            EvidenceFreshness.AGING -> Unit
            EvidenceFreshness.UNKNOWN ->
                error("Positive travel/evaluation timestamps must not classify as UNKNOWN")
        }

        return PracticalShoppingTravelCandidateEvaluation(
            candidate = candidate,
            freshness = freshness,
            blockers = blockers
        )
    }

    private fun resolveLeg(
        leg: PracticalShoppingTravelLeg,
        evaluations: List<PracticalShoppingTravelCandidateEvaluation>
    ): PracticalShoppingTravelResolution {
        val usable = evaluations.filter { it.usable }
        val suggestions =
            evaluations.filter {
                it.blockers == setOf(PracticalShoppingTravelCandidateBlocker.NON_ROUTABLE_RELATIONSHIP)
            }
        val usableByTravel = usable.groupBy { it.candidate.travel }

        if (usableByTravel.size == 1) {
            val selected = usableByTravel.keys.single()
            return PracticalShoppingTravelResolution(
                leg = leg,
                status = PracticalShoppingTravelResolutionStatus.AUTO_USABLE,
                selectedTravel = selected,
                supportingCandidateIds =
                    usableByTravel.getValue(selected)
                        .map { it.candidate.candidateId }
                        .sorted(),
                suggestionCandidateIds =
                    suggestions.map { it.candidate.candidateId }.sorted()
            )
        }

        if (usableByTravel.size > 1 || suggestions.isNotEmpty()) {
            return PracticalShoppingTravelResolution(
                leg = leg,
                status = PracticalShoppingTravelResolutionStatus.NEEDS_EXPLICIT_SELECTION,
                selectedTravel = null,
                supportingCandidateIds = usable.map { it.candidate.candidateId }.sorted(),
                suggestionCandidateIds = suggestions.map { it.candidate.candidateId }.sorted()
            )
        }

        return PracticalShoppingTravelResolution(
            leg = leg,
            status = PracticalShoppingTravelResolutionStatus.UNRESOLVED,
            selectedTravel = null,
            supportingCandidateIds = emptyList(),
            suggestionCandidateIds = emptyList()
        )
    }
}

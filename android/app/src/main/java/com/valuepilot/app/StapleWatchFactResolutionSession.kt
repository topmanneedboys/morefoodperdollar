package com.valuepilot.app

/**
 * Immutable handoff for authoritative Watch fact values belonging to one exact fact-check intent.
 *
 * This session does not acquire, resolve, synthesize, persist, schedule, render, or notify. Fact
 * producers remain separate adapters. Each accepted fact object advances readiness only through its
 * already-verified readiness adapter, and each category is single-assignment: reapplying the exact
 * same object is idempotent while replacing it with an equivalent-looking detached object fails
 * closed. That preserves object provenance for downstream facts minted from upstream facts.
 *
 * Fact categories may arrive in any order. Economic preconditions are exposed only after all five
 * authoritative fact objects are retained; the existing precondition evaluator remains the sole
 * owner of evidence completeness/currentness policy.
 */
class StapleWatchFactResolutionSession private constructor(
    val intent: StapleWatchFactCheckIntent,
    val readiness: StapleWatchFactResolutionReadiness,
    internal val identityFacts: StapleWatchAlternativeStoreIdentityFacts?,
    internal val usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts?,
    internal val alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts?,
    internal val additionalTravelFacts: StapleWatchAlternativeAdditionalTravelFacts?,
    internal val currentnessFacts: StapleWatchEvidenceCurrentnessFacts?
) {
    init {
        require(readiness.intent == intent) {
            "Staple-watch fact-resolution session readiness must match its exact intent"
        }

        val expectedUnresolved =
            intent.requirements.filter { requirement ->
                when (requirement) {
                    StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE ->
                        usualStorePriceFacts == null
                    StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES ->
                        identityFacts == null
                    StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE ->
                        alternativeStorePriceFacts == null
                    StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS ->
                        additionalTravelFacts == null
                    StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA ->
                        currentnessFacts == null
                }
            }
        require(readiness.unresolvedRequirements == expectedUnresolved) {
            "Staple-watch fact-resolution readiness must exactly reflect retained fact categories"
        }

        identityFacts?.let { facts ->
            require(facts.intent == intent) {
                "Staple-watch identity facts must match the session intent"
            }
        }
        usualStorePriceFacts?.let { facts ->
            require(facts.intent == intent) {
                "Staple-watch usual-store price facts must match the session intent"
            }
        }
        alternativeStorePriceFacts?.let { facts ->
            require(facts.intent == intent) {
                "Staple-watch alternative price facts must match the session intent"
            }
            identityFacts?.let { identities ->
                require(facts.identityFacts === identities) {
                    "Staple-watch alternative prices must retain the session's exact identity facts"
                }
            }
        }
        additionalTravelFacts?.let { facts ->
            require(facts.intent == intent) {
                "Staple-watch additional travel facts must match the session intent"
            }
            identityFacts?.let { identities ->
                require(facts.identityFacts === identities) {
                    "Staple-watch additional travel must retain the session's exact identity facts"
                }
            }
        }
        currentnessFacts?.let { facts ->
            require(facts.intent == intent) {
                "Staple-watch currentness facts must match the session intent"
            }
            usualStorePriceFacts?.let { usualPrices ->
                require(facts.usualStorePriceFacts === usualPrices) {
                    "Staple-watch currentness must retain the session's exact usual-store prices"
                }
            }
            alternativeStorePriceFacts?.let { alternativePrices ->
                require(facts.alternativeStorePriceFacts === alternativePrices) {
                    "Staple-watch currentness must retain the session's exact alternative prices"
                }
            }
        }
    }

    val allFactCategoriesRetained: Boolean
        get() =
            identityFacts != null &&
                usualStorePriceFacts != null &&
                alternativeStorePriceFacts != null &&
                additionalTravelFacts != null &&
                currentnessFacts != null

    fun accept(
        facts: StapleWatchAlternativeStoreIdentityFacts
    ): StapleWatchFactResolutionSession {
        identityFacts?.let { existing ->
            require(existing === facts) {
                "Staple-watch identity facts are single-assignment for one resolution session"
            }
            return this
        }

        return copyWith(
            readiness = StapleWatchAlternativeStoreIdentityReadinessAdapter.apply(readiness, facts),
            identityFacts = facts
        )
    }

    fun accept(
        facts: StapleWatchUsualStoreBasketPriceFacts
    ): StapleWatchFactResolutionSession {
        usualStorePriceFacts?.let { existing ->
            require(existing === facts) {
                "Staple-watch usual-store price facts are single-assignment for one resolution session"
            }
            return this
        }

        return copyWith(
            readiness = StapleWatchUsualStoreBasketPriceReadinessAdapter.apply(readiness, facts),
            usualStorePriceFacts = facts
        )
    }

    fun accept(
        facts: StapleWatchAlternativeStoreBasketPriceFacts
    ): StapleWatchFactResolutionSession {
        alternativeStorePriceFacts?.let { existing ->
            require(existing === facts) {
                "Staple-watch alternative price facts are single-assignment for one resolution session"
            }
            return this
        }

        return copyWith(
            readiness =
                StapleWatchAlternativeStoreBasketPriceReadinessAdapter.apply(readiness, facts),
            alternativeStorePriceFacts = facts
        )
    }

    fun accept(
        facts: StapleWatchAlternativeAdditionalTravelFacts
    ): StapleWatchFactResolutionSession {
        additionalTravelFacts?.let { existing ->
            require(existing === facts) {
                "Staple-watch additional travel facts are single-assignment for one resolution session"
            }
            return this
        }

        return copyWith(
            readiness = StapleWatchAlternativeAdditionalTravelReadinessAdapter.apply(readiness, facts),
            additionalTravelFacts = facts
        )
    }

    fun accept(
        facts: StapleWatchEvidenceCurrentnessFacts
    ): StapleWatchFactResolutionSession {
        currentnessFacts?.let { existing ->
            require(existing === facts) {
                "Staple-watch currentness facts are single-assignment for one resolution session"
            }
            return this
        }

        return copyWith(
            readiness = StapleWatchEvidenceCurrentnessReadinessAdapter.apply(readiness, facts),
            currentnessFacts = facts
        )
    }

    fun economicPreconditionsOrNull(): StapleWatchEconomicEvidencePreconditions? {
        val identities = identityFacts ?: return null
        val usualPrices = usualStorePriceFacts ?: return null
        val alternativePrices = alternativeStorePriceFacts ?: return null
        val travel = additionalTravelFacts ?: return null
        val currentness = currentnessFacts ?: return null

        return StapleWatchEconomicEvidencePreconditions.evaluate(
            identityFacts = identities,
            usualStorePriceFacts = usualPrices,
            alternativeStorePriceFacts = alternativePrices,
            additionalTravelFacts = travel,
            currentnessFacts = currentness
        )
    }

    private fun copyWith(
        readiness: StapleWatchFactResolutionReadiness = this.readiness,
        identityFacts: StapleWatchAlternativeStoreIdentityFacts? = this.identityFacts,
        usualStorePriceFacts: StapleWatchUsualStoreBasketPriceFacts? = this.usualStorePriceFacts,
        alternativeStorePriceFacts: StapleWatchAlternativeStoreBasketPriceFacts? =
            this.alternativeStorePriceFacts,
        additionalTravelFacts: StapleWatchAlternativeAdditionalTravelFacts? = this.additionalTravelFacts,
        currentnessFacts: StapleWatchEvidenceCurrentnessFacts? = this.currentnessFacts
    ): StapleWatchFactResolutionSession =
        StapleWatchFactResolutionSession(
            intent = intent,
            readiness = readiness,
            identityFacts = identityFacts,
            usualStorePriceFacts = usualStorePriceFacts,
            alternativeStorePriceFacts = alternativeStorePriceFacts,
            additionalTravelFacts = additionalTravelFacts,
            currentnessFacts = currentnessFacts
        )

    companion object {
        fun start(intent: StapleWatchFactCheckIntent): StapleWatchFactResolutionSession =
            StapleWatchFactResolutionSession(
                intent = intent,
                readiness = StapleWatchFactResolutionReadiness.initial(intent),
                identityFacts = null,
                usualStorePriceFacts = null,
                alternativeStorePriceFacts = null,
                additionalTravelFacts = null,
                currentnessFacts = null
            )
    }
}

package com.valuepilot.core

/** Whether shopping evidence may participate in ValuePilot value ranking. */
enum class EvidenceDisposition {
    RANKABLE,
    DISPLAY_ONLY,
    REJECTED
}

/** Non-exclusive warnings attached to an evidence decision. */
enum class EvidenceWarning {
    SAMPLE_DATA,
    AGING,
    STALE,
    UNKNOWN_FRESHNESS,
    FUTURE_DATED,
    UNKNOWN_ENVIRONMENT,
    UNKNOWN_CHANNEL,
    WEAK_OBSERVATION_CLAIM,
    AVAILABILITY_UNKNOWN,
    LOW_STOCK,
    NOT_CURRENTLY_AVAILABLE,
    WEAK_PROMOTION_CLAIM,
    EXPIRED_PROMOTION
}

/** Explicit deterministic policy supplied by the application. */
data class EvidenceAcceptancePolicy(
    val freshnessPolicy: EvidenceFreshnessPolicy,
    val rankAgingRealWorld: Boolean = true,
    val showStaleRealWorld: Boolean = true,
    val showUnknownFreshnessRealWorld: Boolean = true
)

data class EvidenceAcceptanceDecision(
    val disposition: EvidenceDisposition,
    val freshness: EvidenceFreshness,
    val warnings: Set<EvidenceWarning>
) {
    val rankable: Boolean
        get() = disposition == EvidenceDisposition.RANKABLE

    val displayable: Boolean
        get() = disposition != EvidenceDisposition.REJECTED
}

/**
 * Minimal provider-neutral facts consumed by the acceptance policy.
 *
 * This is deliberately internal to shared-core. It is policy input, not an
 * authorization token, so Android/UI code cannot fabricate it as a shortcut
 * around production lifecycle/rights evaluation.
 *
 * observedAtEpochMillis intentionally preserves legacy ShoppingEvidence
 * semantics: any non-positive timestamp classifies as UNKNOWN freshness rather
 * than becoming a constructor error.
 */
internal data class EvidenceAcceptanceFacts(
    val environment: EvidenceEnvironment,
    val channel: EvidenceChannel,
    val observationClaimKind: EvidenceClaimKind,
    val observedAtEpochMillis: Long,
    val availability: AvailabilityEvidence = AvailabilityEvidence(),
    val promotion: PromotionEvidence? = null
) {
    init {
        if (environment == EvidenceEnvironment.SAMPLE) {
            require(channel == EvidenceChannel.FIXTURE) {
                "Sample acceptance facts must use the fixture channel"
            }
        }
        if (channel == EvidenceChannel.FIXTURE) {
            require(environment == EvidenceEnvironment.SAMPLE) {
                "Fixture acceptance facts must be explicitly sample"
            }
        }
    }

    val isSample: Boolean
        get() = environment == EvidenceEnvironment.SAMPLE

    fun freshness(
        evaluatedAtEpochMillis: Long,
        policy: EvidenceFreshnessPolicy
    ): EvidenceFreshness =
        EvidenceFreshnessEvaluator.classify(
            observedAtEpochMillis = observedAtEpochMillis,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            policy = policy
        )

    companion object {
        fun fromShoppingEvidence(evidence: ShoppingEvidence): EvidenceAcceptanceFacts =
            EvidenceAcceptanceFacts(
                environment = evidence.environment,
                channel = evidence.channel,
                observationClaimKind = evidence.observationClaimKind,
                observedAtEpochMillis = evidence.observation.observedAtEpochMillis,
                availability = evidence.availability,
                promotion = evidence.promotion
            )
    }
}

/**
 * Permanent deterministic trust boundary between evidence and consumer ranking.
 *
 * This evaluator performs no I/O, owns no clock, never parses or changes price
 * or quantity, never ranks products, and never upgrades unknown evidence.
 */
object EvidenceAcceptanceEvaluator {

    /** Existing public entry point. Policy behavior remains unchanged. */
    fun evaluate(
        evidence: ShoppingEvidence,
        evaluatedAtEpochMillis: Long,
        policy: EvidenceAcceptancePolicy
    ): EvidenceAcceptanceDecision =
        evaluate(
            facts = EvidenceAcceptanceFacts.fromShoppingEvidence(evidence),
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            policy = policy
        )

    /**
     * Shared-core-only policy entry point for already-established facts.
     *
     * This does not prove production authorization, lifecycle, namespace
     * disposition, factual conflict resolution, quantity authority, or final
     * Best Value eligibility.
     */
    internal fun evaluate(
        facts: EvidenceAcceptanceFacts,
        evaluatedAtEpochMillis: Long,
        policy: EvidenceAcceptancePolicy
    ): EvidenceAcceptanceDecision {
        if (facts.isSample) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.RANKABLE,
                freshness = EvidenceFreshness.UNKNOWN,
                warnings = setOf(EvidenceWarning.SAMPLE_DATA)
            )
        }

        val freshness =
            facts.freshness(
                evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                policy = policy.freshnessPolicy
            )

        if (freshness == EvidenceFreshness.FUTURE_DATED) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.REJECTED,
                freshness = freshness,
                warnings = setOf(EvidenceWarning.FUTURE_DATED)
            )
        }

        if (facts.environment == EvidenceEnvironment.UNKNOWN) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.DISPLAY_ONLY,
                freshness = freshness,
                warnings =
                    buildWarnings(facts, freshness, evaluatedAtEpochMillis) +
                        EvidenceWarning.UNKNOWN_ENVIRONMENT
            )
        }

        if (facts.channel == EvidenceChannel.UNKNOWN) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.DISPLAY_ONLY,
                freshness = freshness,
                warnings =
                    buildWarnings(facts, freshness, evaluatedAtEpochMillis) +
                        EvidenceWarning.UNKNOWN_CHANNEL
            )
        }

        if (
            facts.observationClaimKind == EvidenceClaimKind.INFERRED ||
            facts.observationClaimKind == EvidenceClaimKind.UNKNOWN
        ) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.DISPLAY_ONLY,
                freshness = freshness,
                warnings =
                    buildWarnings(facts, freshness, evaluatedAtEpochMillis) +
                        EvidenceWarning.WEAK_OBSERVATION_CLAIM
            )
        }

        val promotion = facts.promotion

        if (
            promotion != null &&
            (
                promotion.claimKind == EvidenceClaimKind.INFERRED ||
                promotion.claimKind == EvidenceClaimKind.UNKNOWN
            )
        ) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.DISPLAY_ONLY,
                freshness = freshness,
                warnings =
                    buildWarnings(facts, freshness, evaluatedAtEpochMillis) +
                        EvidenceWarning.WEAK_PROMOTION_CLAIM
            )
        }

        if (
            promotion != null &&
            evaluatedAtEpochMillis > 0L &&
            promotion.validUntilEpochMillis != null &&
            evaluatedAtEpochMillis > promotion.validUntilEpochMillis
        ) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.DISPLAY_ONLY,
                freshness = freshness,
                warnings =
                    buildWarnings(facts, freshness, evaluatedAtEpochMillis) +
                        EvidenceWarning.EXPIRED_PROMOTION
            )
        }

        if (
            facts.availability.state == AvailabilityState.OUT_OF_STOCK ||
            facts.availability.state == AvailabilityState.UNAVAILABLE
        ) {
            return EvidenceAcceptanceDecision(
                disposition = EvidenceDisposition.DISPLAY_ONLY,
                freshness = freshness,
                warnings =
                    buildWarnings(facts, freshness, evaluatedAtEpochMillis) +
                        EvidenceWarning.NOT_CURRENTLY_AVAILABLE
            )
        }

        val warnings = buildWarnings(facts, freshness, evaluatedAtEpochMillis)

        val disposition =
            when (freshness) {
                EvidenceFreshness.FRESH -> EvidenceDisposition.RANKABLE
                EvidenceFreshness.AGING ->
                    if (policy.rankAgingRealWorld) {
                        EvidenceDisposition.RANKABLE
                    } else {
                        EvidenceDisposition.DISPLAY_ONLY
                    }
                EvidenceFreshness.STALE ->
                    if (policy.showStaleRealWorld) {
                        EvidenceDisposition.DISPLAY_ONLY
                    } else {
                        EvidenceDisposition.REJECTED
                    }
                EvidenceFreshness.UNKNOWN ->
                    if (policy.showUnknownFreshnessRealWorld) {
                        EvidenceDisposition.DISPLAY_ONLY
                    } else {
                        EvidenceDisposition.REJECTED
                    }
                EvidenceFreshness.FUTURE_DATED -> EvidenceDisposition.REJECTED
            }

        return EvidenceAcceptanceDecision(
            disposition = disposition,
            freshness = freshness,
            warnings = warnings
        )
    }

    private fun buildWarnings(
        facts: EvidenceAcceptanceFacts,
        freshness: EvidenceFreshness,
        evaluatedAtEpochMillis: Long
    ): Set<EvidenceWarning> {
        val warnings = linkedSetOf<EvidenceWarning>()

        when (freshness) {
            EvidenceFreshness.UNKNOWN -> warnings += EvidenceWarning.UNKNOWN_FRESHNESS
            EvidenceFreshness.FUTURE_DATED -> warnings += EvidenceWarning.FUTURE_DATED
            EvidenceFreshness.FRESH -> Unit
            EvidenceFreshness.AGING -> warnings += EvidenceWarning.AGING
            EvidenceFreshness.STALE -> warnings += EvidenceWarning.STALE
        }

        when (facts.availability.state) {
            AvailabilityState.UNKNOWN -> warnings += EvidenceWarning.AVAILABILITY_UNKNOWN
            AvailabilityState.LOW_STOCK -> warnings += EvidenceWarning.LOW_STOCK
            AvailabilityState.OUT_OF_STOCK,
            AvailabilityState.UNAVAILABLE -> warnings += EvidenceWarning.NOT_CURRENTLY_AVAILABLE
            AvailabilityState.IN_STOCK -> Unit
        }

        val promotion = facts.promotion
        if (
            promotion != null &&
            (
                promotion.claimKind == EvidenceClaimKind.INFERRED ||
                promotion.claimKind == EvidenceClaimKind.UNKNOWN
            )
        ) {
            warnings += EvidenceWarning.WEAK_PROMOTION_CLAIM
        }

        if (
            promotion != null &&
            evaluatedAtEpochMillis > 0L &&
            promotion.validUntilEpochMillis != null &&
            evaluatedAtEpochMillis > promotion.validUntilEpochMillis
        ) {
            warnings += EvidenceWarning.EXPIRED_PROMOTION
        }

        return warnings
    }
}

package com.valuepilot.core

/**
 * Whether shopping evidence may participate in ValuePilot value ranking.
 *
 * RANKABLE:
 * Evidence may participate in deterministic ranking.
 *
 * DISPLAY_ONLY:
 * Evidence may be shown with appropriate caveats but must not influence
 * a "best value" decision.
 *
 * REJECTED:
 * Evidence is unsafe or invalid enough that it should not be consumed.
 */
enum class EvidenceDisposition {
    RANKABLE,
    DISPLAY_ONLY,
    REJECTED
}

/**
 * Non-exclusive warnings attached to an evidence decision.
 *
 * Warnings are data, not presentation strings. Presentation clients choose
 * localized human-readable copy later.
 */
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

/**
 * Explicit deterministic policy supplied by the application.
 *
 * Shared core owns no clock. evaluatedAtEpochMillis must always be supplied
 * by the caller.
 */
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
        get() =
            disposition ==
                EvidenceDisposition.RANKABLE

    val displayable: Boolean
        get() =
            disposition !=
                EvidenceDisposition.REJECTED
}

/**
 * Permanent deterministic trust boundary between provider evidence and
 * consumer ranking.
 *
 * This evaluator:
 * - never performs I/O;
 * - never reads a clock;
 * - never parses or changes price/quantity;
 * - never ranks products;
 * - never upgrades unknown evidence into trusted evidence.
 */
object EvidenceAcceptanceEvaluator {

    fun evaluate(
        evidence: ShoppingEvidence,
        evaluatedAtEpochMillis: Long,
        policy: EvidenceAcceptancePolicy
    ): EvidenceAcceptanceDecision {

        if (evidence.isSample) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.RANKABLE,
                freshness =
                    EvidenceFreshness.UNKNOWN,
                warnings =
                    setOf(
                        EvidenceWarning.SAMPLE_DATA
                    )
            )
        }

        val freshness =
            evidence.freshness(
                evaluatedAtEpochMillis =
                    evaluatedAtEpochMillis,
                policy =
                    policy.freshnessPolicy
            )

        if (
            freshness ==
            EvidenceFreshness.FUTURE_DATED
        ) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.REJECTED,
                freshness =
                    freshness,
                warnings =
                    setOf(
                        EvidenceWarning.FUTURE_DATED
                    )
            )
        }

        if (
            evidence.environment ==
            EvidenceEnvironment.UNKNOWN
        ) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.DISPLAY_ONLY,
                freshness =
                    freshness,
                warnings =
                    buildWarnings(
                        evidence = evidence,
                        freshness = freshness,
                        evaluatedAtEpochMillis =
                            evaluatedAtEpochMillis
                    ) +
                        EvidenceWarning
                            .UNKNOWN_ENVIRONMENT
            )
        }

        if (
            evidence.channel ==
            EvidenceChannel.UNKNOWN
        ) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.DISPLAY_ONLY,
                freshness =
                    freshness,
                warnings =
                    buildWarnings(
                        evidence = evidence,
                        freshness = freshness,
                        evaluatedAtEpochMillis =
                            evaluatedAtEpochMillis
                    ) +
                        EvidenceWarning
                            .UNKNOWN_CHANNEL
            )
        }

        if (
            evidence.observationClaimKind ==
                EvidenceClaimKind.INFERRED ||
            evidence.observationClaimKind ==
                EvidenceClaimKind.UNKNOWN
        ) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.DISPLAY_ONLY,
                freshness =
                    freshness,
                warnings =
                    buildWarnings(
                        evidence = evidence,
                        freshness = freshness,
                        evaluatedAtEpochMillis =
                            evaluatedAtEpochMillis
                    ) +
                        EvidenceWarning
                            .WEAK_OBSERVATION_CLAIM
            )
        }

        val promotion =
            evidence.promotion

        if (
            promotion != null &&
            (
                promotion.claimKind ==
                    EvidenceClaimKind.INFERRED ||
                promotion.claimKind ==
                    EvidenceClaimKind.UNKNOWN
            )
        ) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.DISPLAY_ONLY,
                freshness =
                    freshness,
                warnings =
                    buildWarnings(
                        evidence = evidence,
                        freshness = freshness,
                        evaluatedAtEpochMillis =
                            evaluatedAtEpochMillis
                    ) +
                        EvidenceWarning
                            .WEAK_PROMOTION_CLAIM
            )
        }

        if (
            promotion != null &&
            evaluatedAtEpochMillis > 0L &&
            promotion.validUntilEpochMillis != null &&
            evaluatedAtEpochMillis >
                promotion.validUntilEpochMillis
        ) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.DISPLAY_ONLY,
                freshness =
                    freshness,
                warnings =
                    buildWarnings(
                        evidence = evidence,
                        freshness = freshness,
                        evaluatedAtEpochMillis =
                            evaluatedAtEpochMillis
                    ) +
                        EvidenceWarning
                            .EXPIRED_PROMOTION
            )
        }

        if (
            evidence.availability.state ==
                AvailabilityState.OUT_OF_STOCK ||
            evidence.availability.state ==
                AvailabilityState.UNAVAILABLE
        ) {
            return EvidenceAcceptanceDecision(
                disposition =
                    EvidenceDisposition.DISPLAY_ONLY,
                freshness =
                    freshness,
                warnings =
                    buildWarnings(
                        evidence = evidence,
                        freshness = freshness,
                        evaluatedAtEpochMillis =
                            evaluatedAtEpochMillis
                    ) +
                        EvidenceWarning
                            .NOT_CURRENTLY_AVAILABLE
            )
        }

        val warnings =
            buildWarnings(
                evidence = evidence,
                freshness = freshness,
                evaluatedAtEpochMillis =
                    evaluatedAtEpochMillis
            )

        val disposition =
            when (freshness) {

                EvidenceFreshness.FRESH ->
                    EvidenceDisposition.RANKABLE

                EvidenceFreshness.AGING ->
                    if (
                        policy.rankAgingRealWorld
                    ) {
                        EvidenceDisposition.RANKABLE
                    } else {
                        EvidenceDisposition.DISPLAY_ONLY
                    }

                EvidenceFreshness.STALE ->
                    if (
                        policy.showStaleRealWorld
                    ) {
                        EvidenceDisposition.DISPLAY_ONLY
                    } else {
                        EvidenceDisposition.REJECTED
                    }

                EvidenceFreshness.UNKNOWN ->
                    if (
                        policy.showUnknownFreshnessRealWorld
                    ) {
                        EvidenceDisposition.DISPLAY_ONLY
                    } else {
                        EvidenceDisposition.REJECTED
                    }

                EvidenceFreshness.FUTURE_DATED ->
                    EvidenceDisposition.REJECTED
            }

        return EvidenceAcceptanceDecision(
            disposition =
                disposition,
            freshness =
                freshness,
            warnings =
                warnings
        )
    }

    private fun buildWarnings(
        evidence: ShoppingEvidence,
        freshness: EvidenceFreshness,
        evaluatedAtEpochMillis: Long
    ): Set<EvidenceWarning> {

        val warnings =
            linkedSetOf<EvidenceWarning>()

        when (freshness) {
            EvidenceFreshness.UNKNOWN ->
                warnings +=
                    EvidenceWarning
                        .UNKNOWN_FRESHNESS

            EvidenceFreshness.FUTURE_DATED ->
                warnings +=
                    EvidenceWarning
                        .FUTURE_DATED

            EvidenceFreshness.FRESH ->
                Unit

            EvidenceFreshness.AGING ->
                warnings +=
                    EvidenceWarning.AGING

            EvidenceFreshness.STALE ->
                warnings +=
                    EvidenceWarning.STALE
        }

        when (evidence.availability.state) {

            AvailabilityState.UNKNOWN ->
                warnings +=
                    EvidenceWarning
                        .AVAILABILITY_UNKNOWN

            AvailabilityState.LOW_STOCK ->
                warnings +=
                    EvidenceWarning.LOW_STOCK

            AvailabilityState.OUT_OF_STOCK,
            AvailabilityState.UNAVAILABLE ->
                warnings +=
                    EvidenceWarning
                        .NOT_CURRENTLY_AVAILABLE

            AvailabilityState.IN_STOCK ->
                Unit
        }

        val promotion =
            evidence.promotion

        if (
            promotion != null &&
            (
                promotion.claimKind ==
                    EvidenceClaimKind.INFERRED ||
                promotion.claimKind ==
                    EvidenceClaimKind.UNKNOWN
            )
        ) {
            warnings +=
                EvidenceWarning
                    .WEAK_PROMOTION_CLAIM
        }

        if (
            promotion != null &&
            evaluatedAtEpochMillis > 0L &&
            promotion.validUntilEpochMillis != null &&
            evaluatedAtEpochMillis >
                promotion.validUntilEpochMillis
        ) {
            warnings +=
                EvidenceWarning
                    .EXPIRED_PROMOTION
        }

        return warnings
    }
}

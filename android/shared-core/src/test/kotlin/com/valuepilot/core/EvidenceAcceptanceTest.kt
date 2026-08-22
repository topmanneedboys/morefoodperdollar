package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidenceAcceptanceTest {

    @Test
    fun sampleFixtureIsRankableButAlwaysMarkedSample() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    sampleEvidence(),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.RANKABLE,
            decision.disposition
        )

        assertEquals(
            EvidenceFreshness.UNKNOWN,
            decision.freshness
        )

        assertTrue(
            EvidenceWarning.SAMPLE_DATA
                in decision.warnings
        )

        assertTrue(decision.rankable)
        assertTrue(decision.displayable)
    }

    @Test
    fun freshRealWorldEvidenceIsRankable() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        availability =
                            AvailabilityEvidence(
                                state =
                                    AvailabilityState
                                        .IN_STOCK,
                                claimKind =
                                    EvidenceClaimKind
                                        .SOURCE_ASSERTED,
                                observedAtEpochMillis =
                                    NOW -
                                        5L * MINUTE
                            )
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.RANKABLE,
            decision.disposition
        )

        assertEquals(
            EvidenceFreshness.FRESH,
            decision.freshness
        )

        assertTrue(decision.warnings.isEmpty())
    }

    @Test
    fun agingRealWorldEvidenceIsRankableByDefaultWithWarning() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                45L * MINUTE
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.RANKABLE,
            decision.disposition
        )

        assertEquals(
            EvidenceFreshness.AGING,
            decision.freshness
        )

        assertTrue(
            EvidenceWarning.AGING
                in decision.warnings
        )
    }

    @Test
    fun policyCanPreventAgingEvidenceFromRanking() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                45L * MINUTE
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy().copy(
                        rankAgingRealWorld =
                            false
                    )
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertFalse(decision.rankable)
        assertTrue(decision.displayable)
    }

    @Test
    fun staleRealWorldEvidenceIsDisplayOnlyByDefault() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                4L * HOUR
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertEquals(
            EvidenceFreshness.STALE,
            decision.freshness
        )

        assertTrue(
            EvidenceWarning.STALE
                in decision.warnings
        )
    }

    @Test
    fun policyCanRejectStaleEvidenceCompletely() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                4L * HOUR
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy().copy(
                        showStaleRealWorld =
                            false
                    )
            )

        assertEquals(
            EvidenceDisposition.REJECTED,
            decision.disposition
        )

        assertFalse(decision.rankable)
        assertFalse(decision.displayable)
    }

    @Test
    fun unknownFreshnessNeverSilentlyBecomesRankable() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            0L
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertEquals(
            EvidenceFreshness.UNKNOWN,
            decision.freshness
        )

        assertTrue(
            EvidenceWarning
                .UNKNOWN_FRESHNESS
                in decision.warnings
        )
    }

    @Test
    fun policyCanRejectUnknownFreshness() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            0L
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy().copy(
                        showUnknownFreshnessRealWorld =
                            false
                    )
            )

        assertEquals(
            EvidenceDisposition.REJECTED,
            decision.disposition
        )
    }

    @Test
    fun implausiblyFutureDatedRealWorldEvidenceIsRejected() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW +
                                10L * MINUTE
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.REJECTED,
            decision.disposition
        )

        assertEquals(
            EvidenceFreshness.FUTURE_DATED,
            decision.freshness
        )

        assertTrue(
            EvidenceWarning.FUTURE_DATED
                in decision.warnings
        )
    }

    @Test
    fun unknownEnvironmentIsNeverAllowedToRank() {
        val evidence =
            ShoppingEvidence(
                observation =
                    observation(
                        NOW -
                            5L * MINUTE
                    ),
                provider =
                    provider(),
                source =
                    source(),
                environment =
                    EvidenceEnvironment.UNKNOWN,
                channel =
                    EvidenceChannel.UNKNOWN,
                observationClaimKind =
                    EvidenceClaimKind.UNKNOWN
            )

        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    evidence,
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning
                .UNKNOWN_ENVIRONMENT
                in decision.warnings
        )
    }

    @Test
    fun unknownRealWorldChannelCannotRank() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        channel =
                            EvidenceChannel.UNKNOWN
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning.UNKNOWN_CHANNEL
                in decision.warnings
        )
    }

    @Test
    fun inferredObservationCannotInfluenceBestValueRanking() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        claimKind =
                            EvidenceClaimKind.INFERRED
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning
                .WEAK_OBSERVATION_CLAIM
                in decision.warnings
        )
    }

    @Test
    fun outOfStockEvidenceCanBeShownButCannotWinRanking() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        availability =
                            AvailabilityEvidence(
                                state =
                                    AvailabilityState
                                        .OUT_OF_STOCK,
                                claimKind =
                                    EvidenceClaimKind
                                        .SOURCE_ASSERTED,
                                observedAtEpochMillis =
                                    NOW -
                                        5L * MINUTE
                            )
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning
                .NOT_CURRENTLY_AVAILABLE
                in decision.warnings
        )
    }

    @Test
    fun lowStockEvidenceMayRankButCarriesWarning() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        availability =
                            AvailabilityEvidence(
                                state =
                                    AvailabilityState
                                        .LOW_STOCK,
                                claimKind =
                                    EvidenceClaimKind
                                        .SOURCE_ASSERTED,
                                observedAtEpochMillis =
                                    NOW -
                                        5L * MINUTE
                            )
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.RANKABLE,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning.LOW_STOCK
                in decision.warnings
        )
    }

    @Test
    fun unknownAvailabilityDoesNotPretendToBeInStock() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.RANKABLE,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning
                .AVAILABILITY_UNKNOWN
                in decision.warnings
        )
    }

    @Test
    fun expiredPromotionEvidenceCannotInfluenceBestValueRanking() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        promotion =
                            PromotionEvidence(
                                label =
                                    "Member price",
                                claimKind =
                                    EvidenceClaimKind
                                        .SOURCE_ASSERTED,
                                validUntilEpochMillis =
                                    NOW -
                                        MINUTE
                            )
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning
                .EXPIRED_PROMOTION
                in decision.warnings
        )
    }

    @Test
    fun validExplicitPromotionMayStillRank() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        availability =
                            AvailabilityEvidence(
                                state =
                                    AvailabilityState
                                        .IN_STOCK,
                                claimKind =
                                    EvidenceClaimKind
                                        .SOURCE_ASSERTED,
                                observedAtEpochMillis =
                                    NOW -
                                        5L * MINUTE
                            ),
                        promotion =
                            PromotionEvidence(
                                label =
                                    "Member price",
                                claimKind =
                                    EvidenceClaimKind
                                        .SOURCE_ASSERTED,
                                validUntilEpochMillis =
                                    NOW +
                                        HOUR
                            )
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.RANKABLE,
            decision.disposition
        )

        assertFalse(
            EvidenceWarning
                .EXPIRED_PROMOTION
                in decision.warnings
        )
    }

    @Test
    fun inferredPromotionCannotInfluenceBestValueRanking() {
        val decision =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    realEvidence(
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE,
                        promotion =
                            PromotionEvidence(
                                label =
                                    "Possible promotion",
                                claimKind =
                                    EvidenceClaimKind
                                        .INFERRED
                            )
                    ),
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            decision.disposition
        )

        assertTrue(
            EvidenceWarning
                .WEAK_PROMOTION_CLAIM
                in decision.warnings
        )
    }

    @Test
    fun callerSuppliedEvaluationTimeMakesDecisionDeterministic() {
        val evidence =
            realEvidence(
                observedAtEpochMillis =
                    NOW -
                        5L * MINUTE,
                availability =
                    AvailabilityEvidence(
                        state =
                            AvailabilityState
                                .IN_STOCK,
                        claimKind =
                            EvidenceClaimKind
                                .SOURCE_ASSERTED,
                        observedAtEpochMillis =
                            NOW -
                                5L * MINUTE
                    )
            )

        val early =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    evidence,
                evaluatedAtEpochMillis =
                    NOW,
                policy =
                    policy()
            )

        val late =
            EvidenceAcceptanceEvaluator.evaluate(
                evidence =
                    evidence,
                evaluatedAtEpochMillis =
                    NOW +
                        4L * HOUR,
                policy =
                    policy()
            )

        assertEquals(
            EvidenceDisposition.RANKABLE,
            early.disposition
        )

        assertEquals(
            EvidenceDisposition.DISPLAY_ONLY,
            late.disposition
        )

        assertEquals(
            EvidenceFreshness.STALE,
            late.freshness
        )
    }

    private fun sampleEvidence():
        ShoppingEvidence =
        ShoppingEvidence(
            observation =
                ProductObservation(
                    id =
                        ProductObservationId(
                            "sample-1"
                        ),
                    sourceId =
                        "sample-market",
                    rawText =
                        "Eggs\n12 ct\n$5.49",
                    observedAtEpochMillis =
                        0L
                ),
            provider =
                EvidenceProvider(
                    id =
                        EvidenceProviderId(
                            "sample-provider"
                        ),
                    displayName =
                        "Sample Provider"
                ),
            source =
                ShoppingSource(
                    id =
                        ShoppingSourceId(
                            "sample-market"
                        ),
                    displayName =
                        "Sample Market"
                ),
            environment =
                EvidenceEnvironment.SAMPLE,
            channel =
                EvidenceChannel.FIXTURE,
            observationClaimKind =
                EvidenceClaimKind
                    .SOURCE_ASSERTED
        )

    private fun realEvidence(
        observedAtEpochMillis: Long,
        channel: EvidenceChannel =
            EvidenceChannel.AUTHORIZED_API,
        claimKind: EvidenceClaimKind =
            EvidenceClaimKind.SOURCE_ASSERTED,
        availability: AvailabilityEvidence =
            AvailabilityEvidence(),
        promotion: PromotionEvidence? =
            null
    ): ShoppingEvidence =
        ShoppingEvidence(
            observation =
                observation(
                    observedAtEpochMillis
                ),
            provider =
                provider(),
            source =
                source(),
            environment =
                EvidenceEnvironment
                    .REAL_WORLD,
            channel =
                channel,
            observationClaimKind =
                claimKind,
            sourceProductIdentity =
                SourceProductIdentity(
                    providerItemId =
                        "item-1"
                ),
            availability =
                availability,
            promotion =
                promotion
        )

    private fun observation(
        observedAtEpochMillis: Long
    ): ProductObservation =
        ProductObservation(
            id =
                ProductObservationId(
                    "obs-1"
                ),
            sourceId =
                "store-a",
            rawText =
                "Eggs\n12 ct\n$5.49",
            observedAtEpochMillis =
                observedAtEpochMillis
        )

    private fun provider():
        EvidenceProvider =
        EvidenceProvider(
            id =
                EvidenceProviderId(
                    "provider-a"
                ),
            displayName =
                "Provider A"
        )

    private fun source():
        ShoppingSource =
        ShoppingSource(
            id =
                ShoppingSourceId(
                    "store-a"
                ),
            displayName =
                "Store A"
        )

    private fun policy():
        EvidenceAcceptancePolicy =
        EvidenceAcceptancePolicy(
            freshnessPolicy =
                EvidenceFreshnessPolicy(
                    freshForMillis =
                        15L * MINUTE,
                    staleAfterMillis =
                        2L * HOUR,
                    futureToleranceMillis =
                        5L * MINUTE
                )
        )

    companion object {
        private const val MINUTE =
            60L * 1000L

        private const val HOUR =
            60L * MINUTE

        private const val NOW =
            1_800_000_000_000L
    }
}

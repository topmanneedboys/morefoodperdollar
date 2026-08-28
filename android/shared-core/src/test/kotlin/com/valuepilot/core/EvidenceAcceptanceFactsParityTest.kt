package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceAcceptanceFactsParityTest {

    private val policy =
        EvidenceAcceptancePolicy(
            freshnessPolicy =
                EvidenceFreshnessPolicy(
                    freshForMillis = 1_000L,
                    staleAfterMillis = 5_000L,
                    futureToleranceMillis = 100L
                )
        )

    @Test
    fun `shopping evidence entry point and common facts stay decision identical`() {
        val cases =
            listOf(
                sampleEvidence(),
                realEvidence(observedAt = 9_500L, availability = AvailabilityState.IN_STOCK),
                realEvidence(observedAt = 7_000L, availability = AvailabilityState.LOW_STOCK),
                realEvidence(observedAt = 1_000L, availability = AvailabilityState.IN_STOCK),
                realEvidence(observedAt = 0L, availability = AvailabilityState.UNKNOWN),
                realEvidence(observedAt = 10_500L, availability = AvailabilityState.IN_STOCK),
                realEvidence(
                    observedAt = 9_500L,
                    availability = AvailabilityState.IN_STOCK,
                    channel = EvidenceChannel.UNKNOWN
                ),
                realEvidence(
                    observedAt = 9_500L,
                    availability = AvailabilityState.IN_STOCK,
                    claimKind = EvidenceClaimKind.INFERRED
                ),
                realEvidence(observedAt = 9_500L, availability = AvailabilityState.OUT_OF_STOCK),
                realEvidence(
                    observedAt = 9_500L,
                    availability = AvailabilityState.IN_STOCK,
                    promotion =
                        PromotionEvidence(
                            label = "Weak promotion",
                            claimKind = EvidenceClaimKind.INFERRED
                        )
                ),
                realEvidence(
                    observedAt = 9_500L,
                    availability = AvailabilityState.IN_STOCK,
                    promotion =
                        PromotionEvidence(
                            label = "Expired promotion",
                            claimKind = EvidenceClaimKind.SOURCE_ASSERTED,
                            validUntilEpochMillis = 9_000L
                        )
                )
            )

        cases.forEach { evidence ->
            val legacyDecision =
                EvidenceAcceptanceEvaluator.evaluate(
                    evidence = evidence,
                    evaluatedAtEpochMillis = NOW,
                    policy = policy
                )

            val factDecision =
                EvidenceAcceptanceEvaluator.evaluate(
                    facts = EvidenceAcceptanceFacts.fromShoppingEvidence(evidence),
                    evaluatedAtEpochMillis = NOW,
                    policy = policy
                )

            assertEquals(legacyDecision, factDecision)
        }
    }

    @Test
    fun `policy toggles remain identical through common facts`() {
        val aging =
            realEvidence(
                observedAt = 7_000L,
                availability = AvailabilityState.IN_STOCK
            )
        val stale =
            realEvidence(
                observedAt = 1_000L,
                availability = AvailabilityState.IN_STOCK
            )
        val unknown =
            realEvidence(
                observedAt = 0L,
                availability = AvailabilityState.IN_STOCK
            )

        val policies =
            listOf(
                policy.copy(rankAgingRealWorld = false),
                policy.copy(showStaleRealWorld = false),
                policy.copy(showUnknownFreshnessRealWorld = false)
            )

        listOf(aging, stale, unknown).forEach { evidence ->
            policies.forEach { candidatePolicy ->
                assertEquals(
                    EvidenceAcceptanceEvaluator.evaluate(
                        evidence = evidence,
                        evaluatedAtEpochMillis = NOW,
                        policy = candidatePolicy
                    ),
                    EvidenceAcceptanceEvaluator.evaluate(
                        facts = EvidenceAcceptanceFacts.fromShoppingEvidence(evidence),
                        evaluatedAtEpochMillis = NOW,
                        policy = candidatePolicy
                    )
                )
            }
        }
    }

    @Test
    fun `common facts preserve unknown environment behavior`() {
        val evidence =
            ShoppingEvidence(
                observation = observation(9_500L),
                provider = provider(),
                source = source(),
                environment = EvidenceEnvironment.UNKNOWN,
                channel = EvidenceChannel.UNKNOWN,
                observationClaimKind = EvidenceClaimKind.UNKNOWN
            )

        assertEquals(
            EvidenceAcceptanceEvaluator.evaluate(
                evidence = evidence,
                evaluatedAtEpochMillis = NOW,
                policy = policy
            ),
            EvidenceAcceptanceEvaluator.evaluate(
                facts = EvidenceAcceptanceFacts.fromShoppingEvidence(evidence),
                evaluatedAtEpochMillis = NOW,
                policy = policy
            )
        )
    }

    private fun sampleEvidence(): ShoppingEvidence =
        ShoppingEvidence(
            observation = observation(0L),
            provider = provider(),
            source = source(),
            environment = EvidenceEnvironment.SAMPLE,
            channel = EvidenceChannel.FIXTURE,
            observationClaimKind = EvidenceClaimKind.SOURCE_ASSERTED
        )

    private fun realEvidence(
        observedAt: Long,
        availability: AvailabilityState,
        channel: EvidenceChannel = EvidenceChannel.FIRST_PARTY_FEED,
        claimKind: EvidenceClaimKind = EvidenceClaimKind.SOURCE_ASSERTED,
        promotion: PromotionEvidence? = null
    ): ShoppingEvidence =
        ShoppingEvidence(
            observation = observation(observedAt),
            provider = provider(),
            source = source(),
            environment = EvidenceEnvironment.REAL_WORLD,
            channel = channel,
            observationClaimKind = claimKind,
            availability =
                AvailabilityEvidence(
                    state = availability,
                    claimKind = claimKind,
                    observedAtEpochMillis = observedAt.takeIf { it > 0L }
                ),
            promotion = promotion
        )

    private fun observation(observedAt: Long) =
        ProductObservation(
            id = ProductObservationId("obs-$observedAt"),
            sourceId = "source-a",
            rawText = "example",
            observedAtEpochMillis = observedAt
        )

    private fun provider() =
        EvidenceProvider(
            id = EvidenceProviderId("provider-a"),
            displayName = "Provider A"
        )

    private fun source() =
        ShoppingSource(
            id = ShoppingSourceId("source-a"),
            displayName = "Source A"
        )

    companion object {
        private const val NOW = 10_000L
    }
}

package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShoppingEvidenceTest {

    @Test
    fun sampleEvidenceMustBeExplicitlyFixtureBacked() {
        val evidence =
            ShoppingEvidence(
                observation =
                    observation(
                        observedAtEpochMillis = 0L
                    ),
                provider =
                    EvidenceProvider(
                        id =
                            EvidenceProviderId(
                                "valuepilot-sample"
                            ),
                        displayName =
                            "ValuePilot Sample Catalog"
                    ),
                source =
                    ShoppingSource(
                        id =
                            ShoppingSourceId(
                                "sample-market-a"
                            ),
                        displayName =
                            "Sample Market A"
                    ),
                environment =
                    EvidenceEnvironment.SAMPLE,
                channel =
                    EvidenceChannel.FIXTURE,
                observationClaimKind =
                    EvidenceClaimKind
                        .SOURCE_ASSERTED
            )

        assertTrue(evidence.isSample)
        assertFalse(evidence.isRealWorld)

        assertEquals(
            EvidenceFreshness.UNKNOWN,
            evidence.freshness(
                evaluatedAtEpochMillis =
                    1_800_000_000_000L,
                policy =
                    policy()
            )
        )

        assertFailsWith<
            IllegalArgumentException
        > {
            evidence.copy(
                channel =
                    EvidenceChannel
                        .AUTHORIZED_API
            )
        }
    }

    @Test
    fun fixtureChannelCannotMasqueradeAsRealWorldEvidence() {
        assertFailsWith<
            IllegalArgumentException
        > {
            ShoppingEvidence(
                observation =
                    observation(
                        observedAtEpochMillis =
                            1_800_000_000_000L
                    ),
                provider =
                    provider(),
                source =
                    source(),
                environment =
                    EvidenceEnvironment
                        .REAL_WORLD,
                channel =
                    EvidenceChannel.FIXTURE,
                observationClaimKind =
                    EvidenceClaimKind
                        .SOURCE_ASSERTED
            )
        }
    }

    @Test
    fun freshnessUsesOnlyCallerSuppliedTime() {
        val observed =
            1_800_000_000_000L

        val policy =
            EvidenceFreshnessPolicy(
                freshForMillis =
                    15L * 60L * 1000L,
                staleAfterMillis =
                    2L * 60L * 60L * 1000L,
                futureToleranceMillis =
                    5L * 60L * 1000L
            )

        assertEquals(
            EvidenceFreshness.FRESH,
            EvidenceFreshnessEvaluator
                .classify(
                    observedAtEpochMillis =
                        observed,
                    evaluatedAtEpochMillis =
                        observed +
                            10L * 60L * 1000L,
                    policy = policy
                )
        )

        assertEquals(
            EvidenceFreshness.AGING,
            EvidenceFreshnessEvaluator
                .classify(
                    observedAtEpochMillis =
                        observed,
                    evaluatedAtEpochMillis =
                        observed +
                            60L * 60L * 1000L,
                    policy = policy
                )
        )

        assertEquals(
            EvidenceFreshness.STALE,
            EvidenceFreshnessEvaluator
                .classify(
                    observedAtEpochMillis =
                        observed,
                    evaluatedAtEpochMillis =
                        observed +
                            3L * 60L * 60L * 1000L,
                    policy = policy
                )
        )
    }

    @Test
    fun implausiblyFutureDatedEvidenceIsExplicit() {
        val observed =
            1_800_000_000_000L

        val result =
            EvidenceFreshnessEvaluator
                .classify(
                    observedAtEpochMillis =
                        observed +
                            10L * 60L * 1000L,
                    evaluatedAtEpochMillis =
                        observed,
                    policy =
                        policy()
                )

        assertEquals(
            EvidenceFreshness.FUTURE_DATED,
            result
        )
    }

    @Test
    fun smallClockSkewCanStillBeFresh() {
        val observed =
            1_800_000_000_000L

        val result =
            EvidenceFreshnessEvaluator
                .classify(
                    observedAtEpochMillis =
                        observed +
                            2L * 60L * 1000L,
                    evaluatedAtEpochMillis =
                        observed,
                    policy =
                        policy()
                )

        assertEquals(
            EvidenceFreshness.FRESH,
            result
        )
    }

    @Test
    fun sourceProductIdentityNeverInventsMissingIdentifiers() {
        val identity =
            SourceProductIdentity(
                providerItemId =
                    "provider-item-42"
            )

        assertEquals(
            "provider-item-42",
            identity.providerItemId
        )

        assertNull(identity.sku)
        assertNull(identity.gtin)

        assertFailsWith<
            IllegalArgumentException
        > {
            SourceProductIdentity()
        }

        assertFailsWith<
            IllegalArgumentException
        > {
            SourceProductIdentity(
                gtin = "not-a-gtin"
            )
        }
    }

    @Test
    fun standardGtinLengthsAreAccepted() {
        listOf(
            "12345670",
            "012345678905",
            "4006381333931",
            "10012345678902"
        ).forEach { gtin ->
            assertEquals(
                gtin,
                SourceProductIdentity(
                    gtin = gtin
                ).gtin
            )
        }
    }

    @Test
    fun unknownAvailabilityRemainsExplicitlyUnknown() {
        val evidence =
            ShoppingEvidence(
                observation =
                    observation(
                        observedAtEpochMillis =
                            1_800_000_000_000L
                    ),
                provider =
                    provider(),
                source =
                    source(),
                environment =
                    EvidenceEnvironment
                        .REAL_WORLD,
                channel =
                    EvidenceChannel
                        .AUTHORIZED_API,
                observationClaimKind =
                    EvidenceClaimKind
                        .SOURCE_ASSERTED
            )

        assertEquals(
            AvailabilityState.UNKNOWN,
            evidence.availability.state
        )

        assertEquals(
            EvidenceClaimKind.UNKNOWN,
            evidence.availability
                .claimKind
        )

        assertNull(evidence.promotion)
    }

    @Test
    fun availabilityAndPromotionCarryTheirOwnProvenance() {
        val evidence =
            ShoppingEvidence(
                observation =
                    observation(
                        observedAtEpochMillis =
                            1_800_000_000_000L
                    ),
                provider =
                    provider(),
                source =
                    source(),
                environment =
                    EvidenceEnvironment
                        .REAL_WORLD,
                channel =
                    EvidenceChannel
                        .FIRST_PARTY_FEED,
                observationClaimKind =
                    EvidenceClaimKind
                        .SOURCE_ASSERTED,
                sourceProductIdentity =
                    SourceProductIdentity(
                        sku = "EGGS-30"
                    ),
                availability =
                    AvailabilityEvidence(
                        state =
                            AvailabilityState
                                .IN_STOCK,
                        claimKind =
                            EvidenceClaimKind
                                .SOURCE_ASSERTED,
                        observedAtEpochMillis =
                            1_800_000_000_000L
                    ),
                promotion =
                    PromotionEvidence(
                        label =
                            "Member price available",
                        claimKind =
                            EvidenceClaimKind
                                .SOURCE_ASSERTED,
                        validUntilEpochMillis =
                            1_800_086_400_000L
                    )
            )

        assertTrue(evidence.isRealWorld)
        assertFalse(evidence.isSample)

        assertEquals(
            AvailabilityState.IN_STOCK,
            evidence.availability.state
        )

        assertEquals(
            EvidenceClaimKind
                .SOURCE_ASSERTED,
            evidence.promotion
                ?.claimKind
        )
    }

    @Test
    fun evidenceWrapperNeverChangesRawObservation() {
        val observation =
            ProductObservation(
                id =
                    ProductObservationId(
                        "obs-raw"
                    ),
                sourceId =
                    "source-a",
                rawText =
                    "Large Eggs\n30 ct\nC$11.99",
                observedAtEpochMillis =
                    1_800_000_000_000L
            )

        val evidence =
            ShoppingEvidence(
                observation =
                    observation,
                provider =
                    provider(),
                source =
                    source(),
                environment =
                    EvidenceEnvironment
                        .REAL_WORLD,
                channel =
                    EvidenceChannel
                        .AUTHORIZED_API,
                observationClaimKind =
                    EvidenceClaimKind
                        .SOURCE_ASSERTED
            )

        assertEquals(
            observation,
            evidence.observation
        )

        assertEquals(
            "Large Eggs\n30 ct\nC$11.99",
            evidence.observation.rawText
        )
    }

    private fun observation(
        observedAtEpochMillis: Long
    ): ProductObservation =
        ProductObservation(
            id =
                ProductObservationId(
                    "obs-1"
                ),
            sourceId =
                "source-a",
            rawText =
                "Large Eggs\n30 ct\nC$11.99",
            observedAtEpochMillis =
                observedAtEpochMillis
        )

    private fun provider():
        EvidenceProvider =
        EvidenceProvider(
            id =
                EvidenceProviderId(
                    "authorized-provider"
                ),
            displayName =
                "Authorized Provider"
        )

    private fun source():
        ShoppingSource =
        ShoppingSource(
            id =
                ShoppingSourceId(
                    "source-a"
                ),
            displayName =
                "Source A"
        )

    private fun policy():
        EvidenceFreshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis =
                15L * 60L * 1000L,
            staleAfterMillis =
                2L * 60L * 60L * 1000L,
            futureToleranceMillis =
                5L * 60L * 1000L
        )
}

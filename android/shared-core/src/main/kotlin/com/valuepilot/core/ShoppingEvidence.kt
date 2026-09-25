package com.valuepilot.core

/**
 * Stable provider identity.
 *
 * This identifies the adapter/data provider that supplied evidence.
 * It is not a retailer ranking signal.
 */
@JvmInline
value class EvidenceProviderId(
    val value: String
) {
    init {
        require(value.isNotBlank())
        require(value.length <= 128)
    }
}

/**
 * Stable source identity.
 *
 * A source may represent a store, restaurant, marketplace,
 * catalogue, user collection, or another evidence origin.
 */
@JvmInline
value class ShoppingSourceId(
    val value: String
) {
    init {
        require(value.isNotBlank())
        require(value.length <= 128)
    }
}

data class EvidenceProvider(
    val id: EvidenceProviderId,
    val displayName: String
) {
    init {
        require(displayName.isNotBlank())
        require(displayName.length <= 160)
    }
}

data class ShoppingSource(
    val id: ShoppingSourceId,
    val displayName: String
) {
    init {
        require(displayName.isNotBlank())
        require(displayName.length <= 160)
    }
}

/**
 * Whether evidence represents real-world shopping information,
 * deliberately fictional/sample information, or an unknown origin.
 *
 * UNKNOWN is intentionally first-class. ValuePilot must not silently
 * upgrade unknown evidence into real-world evidence.
 */
enum class EvidenceEnvironment {
    SAMPLE,
    REAL_WORLD,
    UNKNOWN
}

/**
 * Platform-neutral description of how evidence entered ValuePilot.
 *
 * No Android, browser, OCR, Accessibility, retailer, filesystem,
 * or network implementation is referenced here.
 */
enum class EvidenceChannel {
    FIXTURE,
    AUTHORIZED_API,
    FIRST_PARTY_FEED,
    USER_PROVIDED,
    DEVICE_OBSERVED,
    IMPORTED,
    UNKNOWN
}

/**
 * Describes what supports a claim.
 *
 * INFERRED evidence remains weaker than explicit/source asserted evidence.
 */
enum class EvidenceClaimKind {
    DIRECT_OBSERVATION,
    SOURCE_ASSERTED,
    USER_ASSERTED,
    INFERRED,
    UNKNOWN
}

/**
 * Optional source-specific identity.
 *
 * providerItemId and SKU are source-specific.
 * GTIN can provide stronger cross-source identity when actually supplied.
 *
 * No identity field is invented when unavailable.
 */
data class SourceProductIdentity(
    val providerItemId: String? = null,
    val sku: String? = null,
    val gtin: String? = null
) {
    init {
        val supplied =
            listOfNotNull(
                providerItemId,
                sku,
                gtin
            )

        require(supplied.isNotEmpty()) {
            "At least one source product identifier is required"
        }

        supplied.forEach {
            require(it.isNotBlank())
            require(it.length <= 160)
        }

        gtin?.let { value ->
            require(
                value.all { character ->
                    character.isDigit()
                }
            ) {
                "GTIN must contain digits only"
            }

            require(
                value.length == 8 ||
                    value.length == 12 ||
                    value.length == 13 ||
                    value.length == 14
            ) {
                "GTIN must contain 8, 12, 13, or 14 digits"
            }
        }
    }
}

enum class AvailabilityState {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK,
    UNAVAILABLE,
    UNKNOWN
}

/**
 * Availability is evidence, not truth inferred from absence.
 *
 * observedAtEpochMillis is nullable because providers may report an
 * availability state without a trustworthy separate availability timestamp.
 */
data class AvailabilityEvidence(
    val state: AvailabilityState =
        AvailabilityState.UNKNOWN,
    val claimKind: EvidenceClaimKind =
        EvidenceClaimKind.UNKNOWN,
    val observedAtEpochMillis: Long? = null
) {
    init {
        require(
            observedAtEpochMillis == null ||
                observedAtEpochMillis >= 0L
        )
    }
}

/**
 * Provider/source metadata about a promotion.
 *
 * Exact promotion arithmetic still belongs to the deterministic offer engine.
 * This contract records provenance only.
 */
data class PromotionEvidence(
    val label: String,
    val claimKind: EvidenceClaimKind,
    val validUntilEpochMillis: Long? = null
) {
    init {
        require(label.isNotBlank())
        require(label.length <= 240)

        require(
            validUntilEpochMillis == null ||
                validUntilEpochMillis > 0L
        )
    }
}

/**
 * Caller-supplied freshness policy.
 *
 * The shared core owns no clock. The caller supplies both the evaluation
 * instant and this policy.
 */
data class EvidenceFreshnessPolicy(
    val freshForMillis: Long,
    val staleAfterMillis: Long,
    val futureToleranceMillis: Long = 300_000L
) {
    init {
        require(freshForMillis >= 0L)
        require(staleAfterMillis >= freshForMillis)
        require(futureToleranceMillis >= 0L)
    }
}

enum class EvidenceFreshness {
    UNKNOWN,
    FUTURE_DATED,
    FRESH,
    AGING,
    STALE
}

object EvidenceFreshnessEvaluator {

    fun classify(
        observedAtEpochMillis: Long,
        evaluatedAtEpochMillis: Long,
        policy: EvidenceFreshnessPolicy
    ): EvidenceFreshness {

        if (
            observedAtEpochMillis <= 0L ||
            evaluatedAtEpochMillis <= 0L
        ) {
            return EvidenceFreshness.UNKNOWN
        }

        val ageMillis =
            evaluatedAtEpochMillis -
                observedAtEpochMillis

        if (
            ageMillis <
            -policy.futureToleranceMillis
        ) {
            return EvidenceFreshness.FUTURE_DATED
        }

        val nonNegativeAge =
            ageMillis.coerceAtLeast(0L)

        return when {
            nonNegativeAge <=
                policy.freshForMillis ->
                EvidenceFreshness.FRESH

            nonNegativeAge <=
                policy.staleAfterMillis ->
                EvidenceFreshness.AGING

            else ->
                EvidenceFreshness.STALE
        }
    }
}

/**
 * Permanent provenance wrapper around ProductObservation.
 *
 * This object does not parse, rank, or alter shopping evidence.
 * It describes where the observation came from and what claims accompany it.
 */
data class ShoppingEvidence(
    val observation: ProductObservation,
    val provider: EvidenceProvider,
    val source: ShoppingSource,
    val environment: EvidenceEnvironment,
    val channel: EvidenceChannel,
    val observationClaimKind: EvidenceClaimKind,
    val sourceProductIdentity: SourceProductIdentity? = null,
    val availability: AvailabilityEvidence =
        AvailabilityEvidence(),
    val promotion: PromotionEvidence? = null
) {
    init {
        if (
            environment ==
            EvidenceEnvironment.SAMPLE
        ) {
            require(
                channel ==
                    EvidenceChannel.FIXTURE
            ) {
                "Sample evidence must use the fixture channel"
            }
        }

        if (
            channel ==
            EvidenceChannel.FIXTURE
        ) {
            require(
                environment ==
                    EvidenceEnvironment.SAMPLE
            ) {
                "Fixture evidence must be explicitly marked sample"
            }
        }
    }

    val isSample: Boolean
        get() =
            environment ==
                EvidenceEnvironment.SAMPLE

    val isRealWorld: Boolean
        get() =
            environment ==
                EvidenceEnvironment.REAL_WORLD

    fun freshness(
        evaluatedAtEpochMillis: Long,
        policy: EvidenceFreshnessPolicy
    ): EvidenceFreshness =
        EvidenceFreshnessEvaluator.classify(
            observedAtEpochMillis =
                observation
                    .observedAtEpochMillis,
            evaluatedAtEpochMillis =
                evaluatedAtEpochMillis,
            policy = policy
        )
}

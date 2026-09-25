package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceBackedUnitValueResult
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceDisposition
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate
import com.valuepilot.core.RateUnit
import com.valuepilot.core.UnitRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceUnitValuePresentationTest {

    @Test
    fun `rankable evaluator result projects exact observed unit value without winner authority`() {
        val result =
            UserProofBackedObservedPriceUnitValueEligibilityResult(
                priceUse = rankablePriceUse(),
                quantityResolution = null,
                policyAttempts = emptyList(),
                selectedQuantityEvidence = quantityCandidate(),
                unitValueResult =
                    EvidenceBackedUnitValueResult(
                        rate = UnitRate("CAD", 5_990_000L, RateUnit.KILOGRAM),
                        blockReasons = emptySet()
                    ),
                blockers = emptySet()
            )

        val state = UserObservedPriceUnitValueUiProjector.project(result)

        assertEquals(UserObservedPriceUnitValueUiStatus.READY_FOR_VALUE_COMPARISON, state.status)
        assertEquals("Observed price", state.evidenceLabel)
        assertEquals("5.99 CAD/kg", state.unitRateText)
        assertTrue(state.valueComparisonEligible)
        assertTrue(state.notice.contains("not retailer-confirmed current prices"))
        assertFalse(state.statusTitle.contains("Best Value", ignoreCase = true))
        assertFalse(state.guidance.contains("winner", ignoreCase = true))
    }

    @Test
    fun `receipt stays historical display only and never exposes a unit rate`() {
        val state =
            UserObservedPriceUnitValueUiProjector.project(
                blockedResult(
                    priceUse =
                        displayOnlyPriceUse(
                            proofType = UserProvidedPriceProofType.RECEIPT,
                            freshness = EvidenceFreshness.FRESH,
                            reason = UserObservedPriceUseReason.RECEIPT_HISTORICAL_ONLY
                        ),
                    blocker = UserObservedPriceUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED
                )
            )

        assertEquals(UserObservedPriceUnitValueUiStatus.HISTORICAL_PRICE_ONLY, state.status)
        assertFalse(state.valueComparisonEligible)
        assertNull(state.unitRateText)
        assertTrue(state.guidance.contains("history"))
    }

    @Test
    fun `aging stale and unknown price tags preserve typed display only semantics`() {
        val cases =
            listOf(
                Triple(
                    EvidenceFreshness.AGING,
                    UserObservedPriceUseReason.PRICE_TAG_AGING,
                    UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_AGING
                ),
                Triple(
                    EvidenceFreshness.STALE,
                    UserObservedPriceUseReason.PRICE_TAG_STALE,
                    UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_STALE
                ),
                Triple(
                    EvidenceFreshness.UNKNOWN,
                    UserObservedPriceUseReason.PRICE_TAG_UNKNOWN_FRESHNESS,
                    UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_FRESHNESS_UNKNOWN
                )
            )

        cases.forEach { (freshness, reason, expectedStatus) ->
            val state =
                UserObservedPriceUnitValueUiProjector.project(
                    blockedResult(
                        priceUse =
                            displayOnlyPriceUse(
                                proofType = UserProvidedPriceProofType.PRICE_TAG,
                                freshness = freshness,
                                reason = reason
                            ),
                        blocker = UserObservedPriceUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED
                    )
                )

            assertEquals(expectedStatus, state.status)
            assertFalse(state.valueComparisonEligible)
            assertNull(state.unitRateText)
        }
    }

    @Test
    fun `future dated observed price remains rejected in presentation`() {
        val priceUse =
            UserProofBackedObservedPriceUseResult(
                claim = observedPriceClaim(),
                proofType = UserProvidedPriceProofType.PRICE_TAG,
                disposition = EvidenceDisposition.REJECTED,
                freshness = EvidenceFreshness.FUTURE_DATED,
                reason = UserObservedPriceUseReason.FUTURE_DATED
            )

        val state =
            UserObservedPriceUnitValueUiProjector.project(
                blockedResult(
                    priceUse = priceUse,
                    blocker = UserObservedPriceUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED
                )
            )

        assertEquals(UserObservedPriceUnitValueUiStatus.OBSERVATION_TIME_INVALID, state.status)
        assertFalse(state.valueComparisonEligible)
        assertNull(state.unitRateText)
        assertTrue(state.guidance.contains("rejected"))
    }

    @Test
    fun `missing and unverifiable proof stay distinct without exposing storage internals`() {
        val missing =
            UserObservedPriceUnitValueUiProjector.project(
                blockedResult(
                    priceUse = unavailablePriceUse(UserObservedPriceUseReason.PROOF_NOT_RETAINED),
                    blocker = UserObservedPriceUnitValueEligibilityBlocker.PRICE_CLAIM_UNAVAILABLE
                )
            )
        val unverifiable =
            UserObservedPriceUnitValueUiProjector.project(
                blockedResult(
                    priceUse = unavailablePriceUse(UserObservedPriceUseReason.PROOF_VERIFICATION_FAILED),
                    blocker = UserObservedPriceUnitValueEligibilityBlocker.PRICE_CLAIM_UNAVAILABLE
                )
            )

        assertEquals(UserObservedPriceUnitValueUiStatus.PRICE_PROOF_UNAVAILABLE, missing.status)
        assertEquals("Price proof unavailable", missing.statusTitle)
        assertEquals(UserObservedPriceUnitValueUiStatus.PRICE_PROOF_UNAVAILABLE, unverifiable.status)
        assertEquals("Price proof could not be verified", unverifiable.statusTitle)
        assertNull(missing.unitRateText)
        assertNull(unverifiable.unitRateText)
    }

    @Test
    fun `missing quantity and unresolved conflict remain different consumer states`() {
        val missing =
            UserObservedPriceUnitValueUiProjector.project(
                blockedResult(
                    rankablePriceUse(),
                    UserObservedPriceUnitValueEligibilityBlocker.NO_RELEVANT_PACKAGE_QUANTITY
                )
            )
        val conflict =
            UserObservedPriceUnitValueUiProjector.project(
                blockedResult(
                    rankablePriceUse(),
                    UserObservedPriceUnitValueEligibilityBlocker.UNRESOLVED_PACKAGE_QUANTITY_CONFLICT
                )
            )

        assertEquals(UserObservedPriceUnitValueUiStatus.PACKAGE_QUANTITY_NEEDED, missing.status)
        assertEquals(UserObservedPriceUnitValueUiStatus.PACKAGE_QUANTITY_CONFLICT, conflict.status)
        assertFalse(missing.valueComparisonEligible)
        assertFalse(conflict.valueComparisonEligible)
        assertNull(missing.unitRateText)
        assertNull(conflict.unitRateText)
    }

    @Test
    fun `quantity integrity blockers collapse to fail closed evidence state`() {
        listOf(
            UserObservedPriceUnitValueEligibilityBlocker.PACKAGE_QUANTITY_CLAIM_ID_COLLISION,
            UserObservedPriceUnitValueEligibilityBlocker.PACKAGE_QUANTITY_FACT_RESOLUTION_MISSING,
            UserObservedPriceUnitValueEligibilityBlocker.RESOLVED_PACKAGE_QUANTITY_NOT_MATERIALIZED
        ).forEach { blocker ->
            val state =
                UserObservedPriceUnitValueUiProjector.project(
                    blockedResult(rankablePriceUse(), blocker)
                )

            assertEquals(UserObservedPriceUnitValueUiStatus.EVIDENCE_BLOCKED, state.status)
            assertFalse(state.valueComparisonEligible)
            assertNull(state.unitRateText)
        }
    }

    @Test
    fun `fresh observed price with downstream unit policy rejection stays unit value blocked`() {
        val state =
            UserObservedPriceUnitValueUiProjector.project(
                blockedResult(
                    rankablePriceUse(),
                    UserObservedPriceUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED
                )
            )

        assertEquals(UserObservedPriceUnitValueUiStatus.UNIT_VALUE_BLOCKED, state.status)
        assertFalse(state.valueComparisonEligible)
        assertNull(state.unitRateText)
        assertTrue(state.guidance.contains("could not be combined safely"))
    }

    @Test
    fun `exact rate formatter covers every canonical rate unit without floating point`() {
        assertEquals("5.99 CAD/kg", formatObservedPriceUnitRate(UnitRate("CAD", 5_990_000L, RateUnit.KILOGRAM)))
        assertEquals("1.25 CAD/L", formatObservedPriceUnitRate(UnitRate("CAD", 1_250_000L, RateUnit.LITRE)))
        assertEquals("0.5 CAD/item", formatObservedPriceUnitRate(UnitRate("CAD", 500_000L, RateUnit.ITEM)))
        assertEquals("0.123456 CAD/in²", formatObservedPriceUnitRate(UnitRate("CAD", 123_456L, RateUnit.SQUARE_INCH)))
    }

    @Test
    fun `source is projection only and owns no proof freshness quantity arithmetic lifecycle or ranking authority`() {
        val source = source("UserObservedPriceUnitValuePresentation.kt").readText()

        listOf(
            "result.rankable",
            "result.blockers",
            "result.priceUse.reason",
            "result.unitValueResult?.rate",
            "valueComparisonEligible = eligible",
            "Observed prices are not retailer-confirmed current prices."
        ).forEach { required ->
            assertTrue("Expected passive presentation boundary $required", source.contains(required))
        }

        listOf(
            "EvidenceFreshnessEvaluator",
            "UserProofBackedObservedPriceUsePolicy(",
            "ProductPackageQuantityFactResolver",
            "EvidenceFactResolver",
            "EvidenceBackedUnitValuePolicy",
            "DeterministicValueMath",
            "ProductionCurrentPriceEligibilityEvaluator",
            "ProductionDatasetLifecycleRegistry",
            "ProviderProductionAuthorization",
            "OpenFoodFacts",
            "AvailabilityEvidence",
            "PromotionEvidence",
            "System.currentTimeMillis",
            "java.net",
            "android.permission",
            "SharedPreferences",
            "WorkManager"
        ).forEach { forbidden ->
            assertFalse("Presentation must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun blockedResult(
        priceUse: UserProofBackedObservedPriceUseResult,
        blocker: UserObservedPriceUnitValueEligibilityBlocker
    ): UserProofBackedObservedPriceUnitValueEligibilityResult =
        UserProofBackedObservedPriceUnitValueEligibilityResult(
            priceUse = priceUse,
            quantityResolution = null,
            policyAttempts = emptyList(),
            selectedQuantityEvidence = null,
            unitValueResult = null,
            blockers = setOf(blocker)
        )

    private fun rankablePriceUse(): UserProofBackedObservedPriceUseResult =
        UserProofBackedObservedPriceUseResult(
            claim = observedPriceClaim(),
            proofType = UserProvidedPriceProofType.PRICE_TAG,
            disposition = EvidenceDisposition.RANKABLE,
            freshness = EvidenceFreshness.FRESH,
            reason = UserObservedPriceUseReason.VERIFIED_FRESH_PRICE_TAG
        )

    private fun displayOnlyPriceUse(
        proofType: UserProvidedPriceProofType,
        freshness: EvidenceFreshness,
        reason: UserObservedPriceUseReason
    ): UserProofBackedObservedPriceUseResult =
        UserProofBackedObservedPriceUseResult(
            claim = observedPriceClaim(),
            proofType = proofType,
            disposition = EvidenceDisposition.DISPLAY_ONLY,
            freshness = freshness,
            reason = reason
        )

    private fun unavailablePriceUse(
        reason: UserObservedPriceUseReason
    ): UserProofBackedObservedPriceUseResult {
        val failure =
            when (reason) {
                UserObservedPriceUseReason.PROOF_NOT_RETAINED ->
                    UserProofBackedObservedPriceClaimFailure.PROOF_NOT_RETAINED

                UserObservedPriceUseReason.PROOF_VERIFICATION_FAILED ->
                    UserProofBackedObservedPriceClaimFailure.PROOF_VERIFICATION_FAILED

                else -> error("Only unavailable proof reasons are valid here")
            }
        return UserProofBackedObservedPriceUseResult(
            claim = null,
            proofType = UserProvidedPriceProofType.PRICE_TAG,
            disposition = EvidenceDisposition.REJECTED,
            freshness = null,
            reason = reason,
            claimFailure = failure
        )
    }

    private fun observedPriceClaim(): EvidenceClaim =
        EvidenceClaim(
            claimId = "observed-price-presentation",
            domain = EvidenceClaimDomain.OBSERVED_PRICE,
            valueFingerprint = "money:CAD:2:599",
            authority = EvidenceAuthorityClass.PROOF_BACKED_DIRECT_OBSERVATION,
            scope = EvidenceClaimScope(productKey = PRODUCT_KEY),
            observedAtEpochMillis = 10_000L
        )

    private fun quantityCandidate(): ProductPackageQuantityEvidenceCandidate {
        val quantity = NormalizedQuantity(1_000_000_000L, BaseUnit.GRAM)
        return ProductPackageQuantityEvidenceCandidate(
            evidenceId = "presentation-quantity",
            namespace =
                EvidenceDatasetNamespace(
                    id = "presentation-quantity-source",
                    displayName = "Presentation quantity source",
                    licenseId = "test-rights-reviewed",
                    storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
                ),
            claim =
                EvidenceClaim(
                    claimId = "presentation-quantity-claim",
                    domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                    valueFingerprint = EvidenceFingerprints.quantity(quantity),
                    authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                    scope = EvidenceClaimScope(productKey = PRODUCT_KEY),
                    observedAtEpochMillis = 9_000L
                ),
            quantity = quantity
        )
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Unable to locate $fileName from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val PRODUCT_KEY = "gtin:04006381333931"
    }
}

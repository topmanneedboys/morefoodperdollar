package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceBackedUnitValueBlockReason
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceFactResolutionStatus
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate
import com.valuepilot.core.RateUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserProofBackedObservedPriceUnitValueEligibilityEvaluatorTest {

    @Test
    fun `fresh verified price tag resolves strong quantity and produces exact observed price rate`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()
        val candidate = quantityCandidate(fixture.confirmation, "quantity-a", quantity)

        val result =
            fixture.evaluator.evaluate(
                confirmation = fixture.confirmation,
                evaluatedAtEpochMillis = 10_500L,
                freshnessPolicy = freshnessPolicy(),
                quantityCandidates = listOf(candidate)
            )

        assertTrue(result.rankable)
        assertTrue(result.blockers.isEmpty())
        assertEquals(EvidenceFreshness.FRESH, result.priceUse.freshness)
        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, requireNotNull(result.priceUse.claim).domain)
        assertEquals("quantity-a", result.selectedQuantityEvidence?.evidenceId)
        assertEquals(1, result.policyAttempts.size)
        val rate = requireNotNull(requireNotNull(result.unitValueResult).rate)
        assertEquals("CAD", rate.currencyCode)
        assertEquals(RateUnit.KILOGRAM, rate.unit)
        assertEquals(5_990_000L, rate.currencyMicrosPerUnit)
        assertEquals(1, fixture.storage.readCalls)
    }

    @Test
    fun `receipt remains display only after quantity resolution and policy blocks ranking`() {
        val fixture = fixture(UserProvidedPriceProofType.RECEIPT)
        val quantity = kilogramsOne()
        val candidate = quantityCandidate(fixture.confirmation, "quantity-a", quantity)

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(candidate)
            )

        assertFalse(result.rankable)
        assertTrue(result.priceUse.displayable)
        assertEquals(UserObservedPriceUseReason.RECEIPT_HISTORICAL_ONLY, result.priceUse.reason)
        assertEquals(
            setOf(UserObservedPriceUnitValueEligibilityBlocker.UNIT_VALUE_POLICY_BLOCKED),
            result.blockers
        )
        assertTrue(
            EvidenceBackedUnitValueBlockReason.PRICE_NOT_RANKABLE in
                result.policyAttempts.single().result.blockReasons
        )
        assertEquals(1, fixture.storage.readCalls)
    }

    @Test
    fun `deleted proof stops before package quantity fact resolution`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        assertTrue(fixture.store.delete(fixture.artifact).accepted)
        val quantity = kilogramsOne()
        val first =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "first",
                quantity = quantity,
                namespaceId = "same-source",
                claimId = "same-claim"
            )
        val mutated =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "mutated",
                quantity = grams(900),
                namespaceId = "same-source",
                claimId = "same-claim"
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(first, mutated)
            )

        assertFalse(result.rankable)
        assertEquals(UserObservedPriceUseReason.PROOF_NOT_RETAINED, result.priceUse.reason)
        assertNull(result.priceUse.claim)
        assertNull(result.quantityResolution)
        assertTrue(result.policyAttempts.isEmpty())
        assertEquals(
            setOf(UserObservedPriceUnitValueEligibilityBlocker.PRICE_CLAIM_UNAVAILABLE),
            result.blockers
        )
        assertEquals(1, fixture.storage.readCalls)
    }

    @Test
    fun `quantity for another product is excluded before unit value policy`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val wrong =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "wrong",
                quantity = kilogramsOne(),
                productKey = "gtin:0036000291452"
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(wrong)
            )

        assertFalse(result.rankable)
        assertTrue(result.policyAttempts.isEmpty())
        assertEquals(
            setOf(UserObservedPriceUnitValueEligibilityBlocker.NO_RELEVANT_PACKAGE_QUANTITY),
            result.blockers
        )
    }

    @Test
    fun `equal authority quantity disagreement remains unresolved and fails closed`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val left =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "left",
                quantity = kilogramsOne(),
                namespaceId = "left-source",
                claimId = "left-claim"
            )
        val right =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "right",
                quantity = grams(900),
                namespaceId = "right-source",
                claimId = "right-claim"
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(left, right)
            )

        assertFalse(result.rankable)
        assertEquals(EvidenceFactResolutionStatus.UNRESOLVED_CONFLICT, result.quantityResolution?.status)
        assertTrue(result.policyAttempts.isEmpty())
        assertEquals(
            setOf(UserObservedPriceUnitValueEligibilityBlocker.UNRESOLVED_PACKAGE_QUANTITY_CONFLICT),
            result.blockers
        )
    }

    @Test
    fun `stronger quantity fact defeats weaker contradictory metadata`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val weaker =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "weaker",
                quantity = grams(900),
                namespaceId = "weaker-source",
                claimId = "weaker-claim",
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
            )
        val stronger =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "stronger",
                quantity = kilogramsOne(),
                namespaceId = "stronger-source",
                claimId = "stronger-claim",
                authority = EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(weaker, stronger)
            )

        assertTrue(result.rankable)
        assertEquals(stronger.claim.valueFingerprint, result.quantityResolution?.selectedValueFingerprint)
        assertEquals("stronger", result.selectedQuantityEvidence?.evidenceId)
    }

    @Test
    fun `weak supporter cannot hide later strong supporter of same resolved quantity`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val quantity = kilogramsOne()
        val weak =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "a-weak",
                quantity = quantity,
                namespaceId = "weak-source",
                claimId = "weak-claim",
                authority = EvidenceAuthorityClass.USER_ASSERTED
            )
        val strong =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "z-strong",
                quantity = quantity,
                namespaceId = "strong-source",
                claimId = "strong-claim",
                authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(weak, strong)
            )

        assertTrue(result.rankable)
        assertEquals(2, result.policyAttempts.size)
        assertEquals("a-weak", result.policyAttempts[0].quantityEvidenceId)
        assertTrue(
            EvidenceBackedUnitValueBlockReason.WEAK_QUANTITY_AUTHORITY in
                result.policyAttempts[0].result.blockReasons
        )
        assertEquals("z-strong", result.policyAttempts[1].quantityEvidenceId)
        assertTrue(result.policyAttempts[1].result.rankable)
        assertEquals("z-strong", result.selectedQuantityEvidence?.evidenceId)
        assertEquals(1, fixture.storage.readCalls)
    }

    @Test
    fun `mismaterialized first supporter cannot hide later valid materialization of same claim`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val claimed = kilogramsOne()
        val bad =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "a-bad",
                quantity = grams(900),
                fingerprintQuantity = claimed,
                namespaceId = "same-source",
                claimId = "same-claim"
            )
        val good =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "z-good",
                quantity = claimed,
                fingerprintQuantity = claimed,
                namespaceId = "same-source",
                claimId = "same-claim"
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(bad, good)
            )

        assertTrue(result.rankable)
        assertEquals(listOf("a-bad", "z-good"), result.policyAttempts.map { it.quantityEvidenceId })
        assertTrue(
            EvidenceBackedUnitValueBlockReason.QUANTITY_VALUE_MISMATCH in
                result.policyAttempts[0].result.blockReasons
        )
        assertTrue(result.policyAttempts[1].result.rankable)
        assertEquals("z-good", result.selectedQuantityEvidence?.evidenceId)
    }

    @Test
    fun `same namespace quantity claim id mutation blocks before policy attempts`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val first =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "first",
                quantity = kilogramsOne(),
                namespaceId = "same-source",
                claimId = "same-claim"
            )
        val mutated =
            quantityCandidate(
                fixture.confirmation,
                evidenceId = "mutated",
                quantity = grams(900),
                namespaceId = "same-source",
                claimId = "same-claim"
            )

        val result =
            fixture.evaluator.evaluate(
                fixture.confirmation,
                10_500L,
                freshnessPolicy(),
                listOf(first, mutated)
            )

        assertFalse(result.rankable)
        assertNull(result.quantityResolution)
        assertTrue(result.policyAttempts.isEmpty())
        assertEquals(
            setOf(UserObservedPriceUnitValueEligibilityBlocker.PACKAGE_QUANTITY_CLAIM_ID_COLLISION),
            result.blockers
        )
    }

    @Test
    fun `source reuses shared resolver and unit value policy without current price lifecycle network or clock authority`() {
        val source = source("UserProofBackedObservedPriceUnitValueEligibilityEvaluator.kt").readText()

        listOf(
            "ProductPackageQuantityFactResolver.validateCandidates(",
            "priceUsePolicy.evaluate(",
            "ProductPackageQuantityFactResolver.resolve(",
            "EvidenceBackedUnitValuePolicy.evaluate(",
            "Offer(current = confirmation.price)",
            "priceDisposition = priceUse.disposition",
            "useMemberPrice = false",
            "attempts.firstOrNull { it.result.rankable }"
        ).forEach { required ->
            assertTrue("Expected observed-price multi-quantity boundary $required", source.contains(required))
        }

        listOf(
            "UserProofBackedObservedPriceUnitValueEvaluator(",
            "ProductionCurrentPriceEligibilityEvaluator.evaluate(",
            "ProductionDatasetLifecycleRegistry(",
            "ProviderProductionAuthorization(",
            "AvailabilityEvidence(",
            "PromotionEvidence(",
            "System.currentTimeMillis",
            "java.net",
            "android.permission",
            "SharedPreferences",
            "WorkManager"
        ).forEach { forbidden ->
            assertFalse("Observed-price multi-quantity boundary must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun kilogramsOne(): NormalizedQuantity =
        NormalizedQuantity(
            amountMicros = 1_000_000_000L,
            unit = BaseUnit.GRAM
        )

    private fun grams(value: Long): NormalizedQuantity =
        NormalizedQuantity(
            amountMicros = value * 1_000_000L,
            unit = BaseUnit.GRAM
        )

    private fun quantityCandidate(
        confirmation: UserConfirmedObservedPrice,
        evidenceId: String,
        quantity: NormalizedQuantity,
        namespaceId: String = "quantity-source",
        claimId: String = "quantity-claim-$evidenceId",
        productKey: String = confirmation.productKey.value,
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
        fingerprintQuantity: NormalizedQuantity = quantity
    ): ProductPackageQuantityEvidenceCandidate {
        val namespace =
            EvidenceDatasetNamespace(
                id = namespaceId,
                displayName = namespaceId,
                licenseId = "quantity-rights-reviewed",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        val claim =
            EvidenceClaim(
                claimId = claimId,
                domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                valueFingerprint = EvidenceFingerprints.quantity(fingerprintQuantity),
                authority = authority,
                scope = EvidenceClaimScope(productKey = productKey),
                observedAtEpochMillis = 9_000L
            )
        return ProductPackageQuantityEvidenceCandidate(
            evidenceId = evidenceId,
            namespace = namespace,
            claim = claim,
            quantity = quantity
        )
    }

    private fun freshnessPolicy(): EvidenceFreshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 1_000L,
            staleAfterMillis = 2_000L,
            futureToleranceMillis = 100L
        )

    private fun fixture(proofType: UserProvidedPriceProofType): Fixture {
        val bytes = "multi-quantity-proof-${proofType.name}".toByteArray()
        val artifact =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = "multi-quantity-${proofType.name.lowercase()}",
                        proofType = proofType,
                        artifactBytes = bytes
                    )
                    .artifact
            )
        val confirmation =
            requireNotNull(
                UserConfirmedObservedPrice
                    .confirm(
                        UserObservedPriceConfirmationInput(
                            artifact = artifact,
                            observationId = "multi-quantity-observation",
                            rawGtin = "4006381333931",
                            productName = "Test Milk",
                            price = Money(599L, "CAD"),
                            storeScope =
                                PracticalShoppingStoreIdentityScope(
                                    merchantKey = "merchant-a",
                                    locationKey = "location-a",
                                    commerceChannelKey = "IN_STORE"
                                ),
                            observedAtEpochMillis = 10_000L,
                            confirmationId = "multi-quantity-confirmation",
                            confirmedAtEpochMillis = 10_100L
                        )
                    )
                    .confirmation
            )
        val storage = CountingProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(artifact, bytes).accepted)
        val claimAdapter = UserProofBackedObservedPriceClaimAdapter(store)
        val usePolicy = UserProofBackedObservedPriceUsePolicy(claimAdapter)

        return Fixture(
            artifact = artifact,
            confirmation = confirmation,
            store = store,
            storage = storage,
            evaluator = UserProofBackedObservedPriceUnitValueEligibilityEvaluator(usePolicy)
        )
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }

    private data class Fixture(
        val artifact: UserProvidedPriceProofArtifact,
        val confirmation: UserConfirmedObservedPrice,
        val store: UserProvidedPriceProofArtifactLocalStore,
        val storage: CountingProofStorage,
        val evaluator: UserProofBackedObservedPriceUnitValueEligibilityEvaluator
    )

    private class CountingProofStorage : UserProvidedPriceProofArtifactByteStorage {
        private val entries = linkedMapOf<String, ByteArray>()
        var readCalls: Int = 0
            private set

        override fun read(
            storageKey: String,
            maxBytes: Int
        ): UserProvidedPriceProofRawReadResult {
            readCalls += 1
            val bytes = entries[storageKey]
                ?: return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = false
                )
            if (bytes.size > maxBytes) {
                return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = true,
                    issue = UserProvidedPriceProofRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return UserProvidedPriceProofRawReadResult(
                bytes = bytes.copyOf(),
                found = true
            )
        }

        override fun replace(
            storageKey: String,
            bytes: ByteArray
        ): Boolean {
            entries[storageKey] = bytes.copyOf()
            return true
        }

        override fun delete(storageKey: String): Boolean {
            entries.remove(storageKey)
            return true
        }

        override fun clearAll(): Boolean {
            entries.clear()
            return true
        }

        override fun inventory(maxArtifactBytes: Int): UserProvidedPriceProofInventoryResult {
            var total = 0L
            entries.values.forEach { bytes ->
                if (bytes.isEmpty() || bytes.size > maxArtifactBytes) {
                    return UserProvidedPriceProofInventoryResult(
                        artifactCount = null,
                        totalBytes = null,
                        issue = UserProvidedPriceProofInventoryIssue.INVALID_COMMITTED_ARTIFACT
                    )
                }
                total += bytes.size.toLong()
            }
            return UserProvidedPriceProofInventoryResult(
                artifactCount = entries.size,
                totalBytes = total
            )
        }
    }
}

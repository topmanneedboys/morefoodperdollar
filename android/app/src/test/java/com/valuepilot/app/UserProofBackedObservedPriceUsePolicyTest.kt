package com.valuepilot.app

import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceDisposition
import com.valuepilot.core.EvidenceFreshness
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserProofBackedObservedPriceUsePolicyTest {

    @Test
    fun `fresh verified price tag is rankable observed price without current price upgrade`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)

        val result = fixture.policy.evaluate(fixture.confirmation, 10_500L, freshnessPolicy())

        assertTrue(result.rankable)
        assertTrue(result.displayable)
        assertEquals(EvidenceDisposition.RANKABLE, result.disposition)
        assertEquals(EvidenceFreshness.FRESH, result.freshness)
        assertEquals(UserObservedPriceUseReason.VERIFIED_FRESH_PRICE_TAG, result.reason)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, result.proofType)
        assertEquals(EvidenceClaimDomain.OBSERVED_PRICE, requireNotNull(result.claim).domain)
        assertNull(result.claimFailure)
        assertNull(result.storageIssue)
    }

    @Test
    fun `fresh verified receipt remains historical display only`() {
        val fixture = fixture(UserProvidedPriceProofType.RECEIPT)

        val result = fixture.policy.evaluate(fixture.confirmation, 10_500L, freshnessPolicy())

        assertFalse(result.rankable)
        assertTrue(result.displayable)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, result.disposition)
        assertEquals(EvidenceFreshness.FRESH, result.freshness)
        assertEquals(UserObservedPriceUseReason.RECEIPT_HISTORICAL_ONLY, result.reason)
    }

    @Test
    fun `price tag becomes aging immediately after caller fresh boundary and cannot rank`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val policy = freshnessPolicy()

        val atBoundary = fixture.policy.evaluate(fixture.confirmation, 11_000L, policy)
        val afterBoundary = fixture.policy.evaluate(fixture.confirmation, 11_001L, policy)

        assertEquals(EvidenceFreshness.FRESH, atBoundary.freshness)
        assertEquals(EvidenceDisposition.RANKABLE, atBoundary.disposition)
        assertEquals(EvidenceFreshness.AGING, afterBoundary.freshness)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, afterBoundary.disposition)
        assertEquals(UserObservedPriceUseReason.PRICE_TAG_AGING, afterBoundary.reason)
    }

    @Test
    fun `price tag stale boundary stays explicit and display only`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val policy = freshnessPolicy()

        val atStaleBoundary = fixture.policy.evaluate(fixture.confirmation, 12_000L, policy)
        val afterStaleBoundary = fixture.policy.evaluate(fixture.confirmation, 12_001L, policy)

        assertEquals(EvidenceFreshness.AGING, atStaleBoundary.freshness)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, atStaleBoundary.disposition)
        assertEquals(UserObservedPriceUseReason.PRICE_TAG_AGING, atStaleBoundary.reason)
        assertEquals(EvidenceFreshness.STALE, afterStaleBoundary.freshness)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, afterStaleBoundary.disposition)
        assertEquals(UserObservedPriceUseReason.PRICE_TAG_STALE, afterStaleBoundary.reason)
    }

    @Test
    fun `unknown evaluation time is explicit and cannot rank a price tag`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)

        val result = fixture.policy.evaluate(fixture.confirmation, 0L, freshnessPolicy())

        assertEquals(EvidenceFreshness.UNKNOWN, result.freshness)
        assertEquals(EvidenceDisposition.DISPLAY_ONLY, result.disposition)
        assertEquals(UserObservedPriceUseReason.PRICE_TAG_UNKNOWN_FRESHNESS, result.reason)
        assertFalse(result.rankable)
    }

    @Test
    fun `future dated beyond caller tolerance is rejected while tolerance boundary stays fresh`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val policy = freshnessPolicy()

        val atTolerance = fixture.policy.evaluate(fixture.confirmation, 9_900L, policy)
        val beyondTolerance = fixture.policy.evaluate(fixture.confirmation, 9_899L, policy)

        assertEquals(EvidenceFreshness.FRESH, atTolerance.freshness)
        assertEquals(EvidenceDisposition.RANKABLE, atTolerance.disposition)
        assertEquals(EvidenceFreshness.FUTURE_DATED, beyondTolerance.freshness)
        assertEquals(EvidenceDisposition.REJECTED, beyondTolerance.disposition)
        assertEquals(UserObservedPriceUseReason.FUTURE_DATED, beyondTolerance.reason)
        assertFalse(beyondTolerance.displayable)
    }

    @Test
    fun `proof deletion revokes rankability before freshness is evaluated`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val beforeDelete = fixture.policy.evaluate(fixture.confirmation, 10_500L, freshnessPolicy())
        assertTrue(beforeDelete.rankable)
        assertTrue(fixture.store.delete(fixture.artifact).accepted)

        val afterDelete = fixture.policy.evaluate(fixture.confirmation, 10_500L, freshnessPolicy())

        assertFalse(afterDelete.rankable)
        assertFalse(afterDelete.displayable)
        assertNull(afterDelete.claim)
        assertNull(afterDelete.freshness)
        assertEquals(EvidenceDisposition.REJECTED, afterDelete.disposition)
        assertEquals(UserObservedPriceUseReason.PROOF_NOT_RETAINED, afterDelete.reason)
        assertEquals(UserProofBackedObservedPriceClaimFailure.PROOF_NOT_RETAINED, afterDelete.claimFailure)
        assertNull(afterDelete.storageIssue)
    }

    @Test
    fun `corrupt retained proof rejects with exact verification failure`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        fixture.storage.entries[fixture.artifact.sha256] = byteArrayOf(9, 9, 9)

        val result = fixture.policy.evaluate(fixture.confirmation, 10_500L, freshnessPolicy())

        assertFalse(result.rankable)
        assertFalse(result.displayable)
        assertNull(result.claim)
        assertNull(result.freshness)
        assertEquals(UserObservedPriceUseReason.PROOF_VERIFICATION_FAILED, result.reason)
        assertEquals(
            UserProofBackedObservedPriceClaimFailure.PROOF_VERIFICATION_FAILED,
            result.claimFailure
        )
        assertEquals(
            UserProvidedPriceProofArtifactStorageIssue.STORED_ARTIFACT_INVALID,
            result.storageIssue
        )
    }

    @Test
    fun `use policy preserves exact reverified claim instead of minting another factual claim`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val expected = requireNotNull(fixture.adapter.read(fixture.confirmation).claim)

        val result = fixture.policy.evaluate(fixture.confirmation, 10_500L, freshnessPolicy())

        assertEquals(expected, result.claim)
    }

    @Test
    fun `source reuses shared freshness policy and owns no hidden clock current price or offer authority`() {
        val source = source("UserProofBackedObservedPriceUsePolicy.kt").readText()

        listOf(
            "claimAdapter.read(confirmation)",
            "EvidenceFreshnessEvaluator.classify(",
            "policy = freshnessPolicy",
            "UserProvidedPriceProofType.RECEIPT",
            "UserProvidedPriceProofType.PRICE_TAG",
            "EvidenceDisposition.RANKABLE",
            "EvidenceFreshness.FRESH"
        ).forEach { required ->
            assertTrue("Expected explicit observed-price use boundary $required", source.contains(required))
        }

        listOf(
            "EvidenceFreshnessPolicy(",
            "System.currentTimeMillis",
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceAuthorityClass.MERCHANT_AUTHORITATIVE",
            "EvidenceAcceptanceEvaluator",
            "ShoppingEvidence(",
            "Offer(",
            "AvailabilityState",
            "PromotionEvidence",
            "ProviderOfferImport",
            "ProductionCurrentPrice",
            "StapleWatch",
            "SharedPreferences",
            "WorkManager",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Observed-price use policy must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun freshnessPolicy(): EvidenceFreshnessPolicy =
        EvidenceFreshnessPolicy(
            freshForMillis = 1_000L,
            staleAfterMillis = 2_000L,
            futureToleranceMillis = 100L
        )

    private fun fixture(
        proofType: UserProvidedPriceProofType
    ): Fixture {
        val bytes = "proof-bytes-${proofType.name}".toByteArray()
        val artifact =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = "artifact-${proofType.name.lowercase()}",
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
                            observationId = "obs-001",
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
                            confirmationId = "confirm-001",
                            confirmedAtEpochMillis = 10_100L
                        )
                    )
                    .confirmation
            )
        val storage = FakeProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(artifact, bytes).accepted)
        val adapter = UserProofBackedObservedPriceClaimAdapter(store)

        return Fixture(
            artifact = artifact,
            confirmation = confirmation,
            storage = storage,
            store = store,
            adapter = adapter,
            policy = UserProofBackedObservedPriceUsePolicy(adapter)
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
        val storage: FakeProofStorage,
        val store: UserProvidedPriceProofArtifactLocalStore,
        val adapter: UserProofBackedObservedPriceClaimAdapter,
        val policy: UserProofBackedObservedPriceUsePolicy
    )

    private class FakeProofStorage : UserProvidedPriceProofArtifactByteStorage {
        val entries = linkedMapOf<String, ByteArray>()

        override fun read(
            storageKey: String,
            maxBytes: Int
        ): UserProvidedPriceProofRawReadResult {
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

        override fun inventory(
            maxArtifactBytes: Int
        ): UserProvidedPriceProofInventoryResult {
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

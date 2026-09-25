package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.EvidenceAuthorityClass
import com.valuepilot.core.EvidenceClaim
import com.valuepilot.core.EvidenceClaimDomain
import com.valuepilot.core.EvidenceClaimScope
import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceFingerprints
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductPackageQuantityEvidenceCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceUnitValueSurfaceHostTest {

    @Test
    fun `fresh retained price tag is re-evaluated then rendered as immutable exact unit value state`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val request = fixture.request(10_500L, listOf(quantityCandidate(fixture.confirmation, "quantity-a", kilogramsOne())))

        val state = fixture.host.evaluateAndRender(request)

        assertEquals(UserObservedPriceUnitValueUiStatus.READY_FOR_VALUE_COMPARISON, state.status)
        assertEquals("5.99 CAD/kg", state.unitRateText)
        assertTrue(state.valueComparisonEligible)
        assertEquals(listOf(state), fixture.renderer.states)
        assertEquals(1, fixture.storage.readCalls)
    }

    @Test
    fun `proof deletion between displays is observed because host never reuses detached eligibility`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val request = fixture.request(10_500L, listOf(quantityCandidate(fixture.confirmation, "quantity-a", kilogramsOne())))

        val first = fixture.host.evaluateAndRender(request)
        assertTrue(first.valueComparisonEligible)
        assertEquals(1, fixture.storage.readCalls)

        assertTrue(fixture.store.delete(fixture.artifact).accepted)
        val second = fixture.host.evaluateAndRender(request)

        assertEquals(UserObservedPriceUnitValueUiStatus.PRICE_PROOF_UNAVAILABLE, second.status)
        assertFalse(second.valueComparisonEligible)
        assertNull(second.unitRateText)
        assertEquals(2, fixture.storage.readCalls)
        assertEquals(listOf(first, second), fixture.renderer.states)
    }

    @Test
    fun `caller supplied later instant reclassifies same retained price tag without host clock authority`() {
        val fixture = fixture(UserProvidedPriceProofType.PRICE_TAG)
        val candidate = quantityCandidate(fixture.confirmation, "quantity-a", kilogramsOne())

        val fresh = fixture.host.evaluateAndRender(fixture.request(10_500L, listOf(candidate)))
        val aging = fixture.host.evaluateAndRender(fixture.request(11_500L, listOf(candidate)))

        assertEquals(UserObservedPriceUnitValueUiStatus.READY_FOR_VALUE_COMPARISON, fresh.status)
        assertEquals(UserObservedPriceUnitValueUiStatus.OBSERVED_PRICE_AGING, aging.status)
        assertFalse(aging.valueComparisonEligible)
        assertNull(aging.unitRateText)
        assertEquals(2, fixture.storage.readCalls)
    }

    @Test
    fun `quantity conflict is recomputed from raw candidates and rendered fail closed`() {
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

        val state = fixture.host.evaluateAndRender(fixture.request(10_500L, listOf(left, right)))

        assertEquals(UserObservedPriceUnitValueUiStatus.PACKAGE_QUANTITY_CONFLICT, state.status)
        assertFalse(state.valueComparisonEligible)
        assertNull(state.unitRateText)
        assertEquals(listOf(state), fixture.renderer.states)
    }

    @Test
    fun `public rendering boundary accepts raw request and exposes only ui state`() {
        val hostMethods = UserObservedPriceUnitValueSurfaceHost::class.java.declaredMethods.filter { it.name == "evaluateAndRender" }
        assertEquals(1, hostMethods.size)
        val hostMethod = hostMethods.single()
        assertEquals(listOf(UserObservedPriceUnitValueSurfaceEvaluationRequest::class.java), hostMethod.parameterTypes.toList())
        assertEquals(UserObservedPriceUnitValueUiState::class.java, hostMethod.returnType)

        val rendererMethod = UserObservedPriceUnitValueSurfaceRenderer::class.java.methods.single { it.name == "render" }
        assertEquals(listOf(UserObservedPriceUnitValueUiState::class.java), rendererMethod.parameterTypes.toList())
        assertEquals(Void.TYPE, rendererMethod.returnType)

        listOf(
            UserProofBackedObservedPriceUnitValueEligibilityResult::class.java,
            UserProofBackedObservedPriceUseResult::class.java,
            EvidenceClaim::class.java
        ).forEach { detachedType ->
            assertFalse(hostMethod.parameterTypes.contains(detachedType))
            assertFalse(rendererMethod.parameterTypes.contains(detachedType))
        }
    }

    @Test
    fun `source delegates evaluation and projection without clock storage current price lifecycle or ui framework authority`() {
        val source = source("UserObservedPriceUnitValueSurfaceHost.kt").readText()

        listOf(
            "evaluator.evaluate(",
            "confirmation = request.confirmation",
            "evaluatedAtEpochMillis = request.evaluatedAtEpochMillis",
            "freshnessPolicy = request.freshnessPolicy",
            "quantityCandidates = request.quantityCandidates",
            "UserObservedPriceUnitValueUiProjector.project(eligibility)",
            "renderer.render(state)"
        ).forEach { required ->
            assertTrue("Expected host delegation $required", source.contains(required))
        }

        listOf(
            "System.currentTimeMillis",
            "UserProvidedPriceProofArtifactLocalStore(",
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
            "android.app.Activity",
            "android.view.View",
            "java.net",
            "android.permission",
            "SharedPreferences",
            "WorkManager"
        ).forEach { forbidden ->
            assertFalse("Surface host must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun fixture(proofType: UserProvidedPriceProofType): Fixture {
        val bytes = "surface-host-proof-${proofType.name}".toByteArray()
        val artifact =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = "surface-host-${proofType.name.lowercase()}",
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
                            observationId = "surface-host-observation",
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
                            confirmationId = "surface-host-confirmation",
                            confirmedAtEpochMillis = 10_100L
                        )
                    )
                    .confirmation
            )
        val storage = CountingProofStorage()
        val store = UserProvidedPriceProofArtifactLocalStore(storage)
        assertTrue(store.retain(artifact, bytes).accepted)
        storage.resetReadCalls()
        val evaluator =
            UserProofBackedObservedPriceUnitValueEligibilityEvaluator(
                UserProofBackedObservedPriceUsePolicy(
                    UserProofBackedObservedPriceClaimAdapter(store)
                )
            )
        val renderer = RecordingRenderer()
        val host = UserObservedPriceUnitValueSurfaceHost(evaluator, renderer)

        return Fixture(
            artifact = artifact,
            confirmation = confirmation,
            store = store,
            storage = storage,
            renderer = renderer,
            host = host
        )
    }

    private fun Fixture.request(
        evaluatedAtEpochMillis: Long,
        candidates: List<ProductPackageQuantityEvidenceCandidate>
    ) =
        UserObservedPriceUnitValueSurfaceEvaluationRequest(
            confirmation = confirmation,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            freshnessPolicy =
                EvidenceFreshnessPolicy(
                    freshForMillis = 1_000L,
                    staleAfterMillis = 2_000L,
                    futureToleranceMillis = 100L
                ),
            quantityCandidates = candidates
        )

    private fun kilogramsOne(): NormalizedQuantity =
        NormalizedQuantity(1_000_000_000L, BaseUnit.GRAM)

    private fun grams(value: Long): NormalizedQuantity =
        NormalizedQuantity(value * 1_000_000L, BaseUnit.GRAM)

    private fun quantityCandidate(
        confirmation: UserConfirmedObservedPrice,
        evidenceId: String,
        quantity: NormalizedQuantity,
        namespaceId: String = "quantity-source",
        claimId: String = "quantity-claim-$evidenceId"
    ): ProductPackageQuantityEvidenceCandidate {
        val namespace =
            EvidenceDatasetNamespace(
                id = namespaceId,
                displayName = namespaceId,
                licenseId = "quantity-rights-reviewed",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        return ProductPackageQuantityEvidenceCandidate(
            evidenceId = evidenceId,
            namespace = namespace,
            claim =
                EvidenceClaim(
                    claimId = claimId,
                    domain = EvidenceClaimDomain.PACKAGE_QUANTITY,
                    valueFingerprint = EvidenceFingerprints.quantity(quantity),
                    authority = EvidenceAuthorityClass.SOURCE_ASSERTED_METADATA,
                    scope = EvidenceClaimScope(productKey = confirmation.productKey.value),
                    observedAtEpochMillis = 9_000L
                ),
            quantity = quantity
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
        val renderer: RecordingRenderer,
        val host: UserObservedPriceUnitValueSurfaceHost
    )

    private class RecordingRenderer : UserObservedPriceUnitValueSurfaceRenderer {
        val states = mutableListOf<UserObservedPriceUnitValueUiState>()

        override fun render(state: UserObservedPriceUnitValueUiState) {
            states += state
        }
    }

    private class CountingProofStorage : UserProvidedPriceProofArtifactByteStorage {
        private val entries = linkedMapOf<String, ByteArray>()
        var readCalls: Int = 0
            private set

        fun resetReadCalls() {
            readCalls = 0
        }

        override fun read(storageKey: String, maxBytes: Int): UserProvidedPriceProofRawReadResult {
            readCalls += 1
            val bytes = entries[storageKey]
                ?: return UserProvidedPriceProofRawReadResult(bytes = null, found = false)
            if (bytes.size > maxBytes) {
                return UserProvidedPriceProofRawReadResult(
                    bytes = null,
                    found = true,
                    issue = UserProvidedPriceProofRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return UserProvidedPriceProofRawReadResult(bytes = bytes.copyOf(), found = true)
        }

        override fun replace(storageKey: String, bytes: ByteArray): Boolean {
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

        override fun inventory(maxArtifactBytes: Int): UserProvidedPriceProofInventoryResult =
            UserProvidedPriceProofInventoryResult(
                artifactCount = entries.size,
                totalBytes = entries.values.sumOf { it.size.toLong() }
            )
    }
}

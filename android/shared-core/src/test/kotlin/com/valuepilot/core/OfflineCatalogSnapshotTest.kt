package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OfflineCatalogSnapshotTest {

    @Test
    fun `fully verified and discovery-authorized source is admitted`() {
        val request = request()

        val decision = OfflineCatalogAdmissionEvaluator.evaluate(request)

        assertTrue(decision.accepted)
        assertTrue(decision.blockers.isEmpty())
        assertTrue(decision.sourceAuthorization.getValue(DATASET_ID).authorized)
        assertEquals(2, request.manifest.totalRecordCount)
    }

    @Test
    fun `catalog discovery requires rights but never offer or network gates`() {
        val gates =
            ProductionActivationProfiles.CONSUMER_MOBILE_PRODUCT_DISCOVERY.requiredGates

        assertEquals(
            setOf(
                ProductionAuthorizationGate.DATA_ACCESS_AUTHORIZED,
                ProductionAuthorizationGate.CONSUMER_DISPLAY_AUTHORIZED,
                ProductionAuthorizationGate.CACHE_AUTHORIZED,
                ProductionAuthorizationGate.INDEX_AUTHORIZED,
                ProductionAuthorizationGate.MOBILE_APP_AUTHORIZED,
                ProductionAuthorizationGate.RETENTION_DELETION_POLICY_DEFINED
            ),
            gates
        )
        assertFalse(ProductionAuthorizationGate.PRICE_SEMANTICS_VALIDATED in gates)
        assertFalse(ProductionAuthorizationGate.OFFER_GEOGRAPHY_VALIDATED in gates)
        assertFalse(ProductionAuthorizationGate.OFFER_FRESHNESS_POLICY_DEFINED in gates)
        assertFalse(ProductionAuthorizationGate.INSTALLED_SOFTWARE_NETWORK_APPROVED in gates)
    }

    @Test
    fun `missing one discovery right blocks the entire snapshot`() {
        val source = source(missingGate = ProductionAuthorizationGate.CACHE_AUTHORIZED)

        val decision =
            OfflineCatalogAdmissionEvaluator.evaluate(
                request(manifest = manifest(sources = listOf(source)))
            )

        assertFalse(decision.accepted)
        assertEquals(
            setOf(OfflineCatalogAdmissionBlocker.SOURCE_NOT_AUTHORIZED_FOR_PRODUCT_DISCOVERY),
            decision.blockers
        )
        assertEquals(
            setOf(ProductionAuthorizationGate.CACHE_AUTHORIZED),
            decision.sourceAuthorization.getValue(DATASET_ID).missingGates
        )
    }

    @Test
    fun `unknown hash and failed signature both fail closed`() {
        val integrity =
            OfflineCatalogIntegrityAssessment(
                manifestHash = OfflineCatalogIntegrityState.UNKNOWN,
                signature = OfflineCatalogIntegrityState.FAILED
            )

        val decision =
            OfflineCatalogAdmissionEvaluator.evaluate(request(integrity = integrity))

        assertEquals(
            setOf(
                OfflineCatalogAdmissionBlocker.MANIFEST_HASH_NOT_VERIFIED,
                OfflineCatalogAdmissionBlocker.SIGNATURE_NOT_VERIFIED
            ),
            decision.blockers
        )
    }

    @Test
    fun `future expired and rollback generations are distinguished`() {
        val future =
            OfflineCatalogAdmissionEvaluator.evaluate(
                request(manifest = manifest(generatedAt = NOW + 1L))
            )
        val expired =
            OfflineCatalogAdmissionEvaluator.evaluate(
                request(
                    manifest = manifest(generatedAt = NOW - 1_001L),
                    maximumAgeMillis = 1_000L
                )
            )
        val rollback =
            OfflineCatalogAdmissionEvaluator.evaluate(
                request(lastKnownGoodGeneratedAt = NOW + 1L)
            )

        assertEquals(
            setOf(OfflineCatalogAdmissionBlocker.FUTURE_DATED_SNAPSHOT),
            future.blockers
        )
        assertEquals(
            setOf(OfflineCatalogAdmissionBlocker.EXPIRED_SNAPSHOT),
            expired.blockers
        )
        assertEquals(
            setOf(OfflineCatalogAdmissionBlocker.OLDER_THAN_LAST_KNOWN_GOOD),
            rollback.blockers
        )
    }

    @Test
    fun `unsupported schema version fails independently of source rights`() {
        val decision =
            OfflineCatalogAdmissionEvaluator.evaluate(
                request(manifest = manifest(schemaVersion = 2))
            )

        assertEquals(
            setOf(OfflineCatalogAdmissionBlocker.UNSUPPORTED_SCHEMA_VERSION),
            decision.blockers
        )
    }

    @Test
    fun `source scope mismatches are rejected before admission`() {
        try {
            source(authorizationDatasetId = "different-dataset")
            fail("Expected source scope rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Authorization dataset scope"))
        }
    }

    @Test
    fun `duplicate source namespace is rejected`() {
        try {
            manifest(sources = listOf(source(), source()))
            fail("Expected duplicate namespace rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("at most once"))
        }
    }

    @Test
    fun `source acquired after manifest generation is rejected`() {
        try {
            manifest(
                generatedAt = NOW - 1L,
                sources = listOf(source(acquiredAt = NOW))
            )
            fail("Expected acquisition ordering rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("acquired before"))
        }
    }

    @Test
    fun `verified integrity requires auditable basis`() {
        try {
            OfflineCatalogIntegrityAssessment(
                manifestHash = OfflineCatalogIntegrityState.VERIFIED,
                signature = OfflineCatalogIntegrityState.VERIFIED
            )
            fail("Expected verified integrity basis rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("auditable basis"))
        }
    }

    private fun request(
        manifest: OfflineCatalogSnapshotManifest = manifest(),
        integrity: OfflineCatalogIntegrityAssessment = verifiedIntegrity(),
        maximumAgeMillis: Long = 10_000L,
        lastKnownGoodGeneratedAt: Long? = null
    ): OfflineCatalogAdmissionRequest =
        OfflineCatalogAdmissionRequest(
            manifest = manifest,
            integrity = integrity,
            evaluatedAtEpochMillis = NOW,
            maximumSnapshotAgeMillis = maximumAgeMillis,
            lastKnownGoodGeneratedAtEpochMillis = lastKnownGoodGeneratedAt
        )

    private fun manifest(
        schemaVersion: Int = OfflineCatalogSnapshotManifest.CURRENT_SCHEMA_VERSION,
        generatedAt: Long = NOW,
        sources: List<OfflineCatalogSnapshotSource> = listOf(source())
    ): OfflineCatalogSnapshotManifest =
        OfflineCatalogSnapshotManifest(
            schemaVersion = schemaVersion,
            snapshotId = "gta-2026-09-03",
            regionId = "ca-gta",
            generatedAtEpochMillis = generatedAt,
            sources = sources
        )

    private fun source(
        missingGate: ProductionAuthorizationGate? = null,
        authorizationDatasetId: String = DATASET_ID,
        acquiredAt: Long = NOW - 10_000L
    ): OfflineCatalogSnapshotSource {
        val providerId = EvidenceProviderId("open-catalog-test")
        val namespace =
            EvidenceDatasetNamespace(
                id = DATASET_ID,
                displayName = "Open catalog fixture",
                licenseId = "ODbL-1.0",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
        val snapshot =
            ProductionDatasetSnapshotRef(
                providerId = providerId,
                datasetNamespaceId = DATASET_ID,
                snapshotId = "source-2026-09-03"
            )
        val authorization =
            ProviderProductionAuthorizationAssessment(
                providerId = providerId,
                datasetNamespaceId = authorizationDatasetId,
                gates =
                    ProductionActivationProfiles.CONSUMER_MOBILE_PRODUCT_DISCOVERY
                        .requiredGates
                        .filterNot { it == missingGate }
                        .sortedBy { it.ordinal }
                        .map { gate ->
                            ProductionGateAssessment(
                                gate = gate,
                                state = ProductionAuthorizationState.SATISFIED,
                                basisId = "fixture-${gate.name.lowercase()}"
                            )
                        }
            )

        return OfflineCatalogSnapshotSource(
            namespace = namespace,
            snapshot = snapshot,
            authorization = authorization,
            recordCount = 2,
            contentSha256 = "a".repeat(64),
            acquiredAtEpochMillis = acquiredAt,
            sourcePublishedAtEpochMillis = acquiredAt - 1L
        )
    }

    private fun verifiedIntegrity(): OfflineCatalogIntegrityAssessment =
        OfflineCatalogIntegrityAssessment(
            manifestHash = OfflineCatalogIntegrityState.VERIFIED,
            signature = OfflineCatalogIntegrityState.VERIFIED,
            basisId = "fixture-signature-verification"
        )

    companion object {
        private const val DATASET_ID = "open-catalog-test-products"
        private const val NOW = 2_000_000L
    }
}

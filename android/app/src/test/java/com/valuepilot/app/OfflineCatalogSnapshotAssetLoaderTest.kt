package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogAdmissionBlocker
import com.valuepilot.core.OfflineCatalogIntegrityAssessment
import com.valuepilot.core.OfflineCatalogIntegrityState
import com.valuepilot.core.ProductionActivationProfiles
import com.valuepilot.core.ProductionAuthorizationGate
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCatalogSnapshotAssetLoaderTest {

    @Test
    fun `accepted canonical snapshot loads identity-only products in deterministic order`() {
        val source = sourceText(
            listOf(
                productLine("off:tea", "Black Tea Bags", providerItemId = "tea"),
                productLine(
                    "off:milk",
                    "Café Whole Milk 2 L",
                    providerItemId = "milk",
                    gtin = "0036000291452",
                    brand = "Dairy Best",
                    aliases = listOf("whole milk", "2 litre milk")
                )
            )
        )

        val loaded = load(source)
        assertTrue(loaded.admission.accepted)
        assertEquals("ca-gta", loaded.manifest.regionId)
        assertEquals("off:milk", loaded.products[0].recordId)
        assertEquals("off:tea", loaded.products[1].recordId)
        assertEquals("cafe whole milk 2 l", loaded.products[0].canonicalSearchName)
        assertEquals(listOf("2 litre milk", "whole milk"), loaded.products[0].canonicalSearchAliases)
        assertTrue(loaded.products.none { product -> product.toString().contains("price", ignoreCase = true) })

        val discovery = loaded.discover("whole milk", JvmTextCanonicalizer)
        assertEquals(2, discovery.evaluatedCandidateCount)
        assertEquals(listOf("off:milk"), discovery.matches.map { it.product.recordId })
    }

    @Test
    fun `failed integrity admits no products even when source text is valid`() {
        val loaded = load(
            sourceText(listOf(productLine("off:milk", "Whole Milk", providerItemId = "milk"))),
            integrity =
                OfflineCatalogIntegrityAssessment(
                    manifestHash = OfflineCatalogIntegrityState.UNKNOWN,
                    signature = OfflineCatalogIntegrityState.FAILED
                )
        )

        assertFalse(loaded.admission.accepted)
        assertTrue(loaded.products.isEmpty())
        val blockedDiscovery = loaded.discover("milk", JvmTextCanonicalizer)
        assertEquals(0, blockedDiscovery.evaluatedCandidateCount)
        assertTrue(blockedDiscovery.matches.isEmpty())
        assertEquals(
            setOf(
                OfflineCatalogAdmissionBlocker.MANIFEST_HASH_NOT_VERIFIED,
                OfflineCatalogAdmissionBlocker.SIGNATURE_NOT_VERIFIED
            ),
            loaded.admission.blockers
        )
    }

    @Test
    fun `expired or rollback snapshot remains unavailable`() {
        val source = sourceText(listOf(productLine("off:milk", "Whole Milk", providerItemId = "milk")))
        val expired = load(source, generatedAt = NOW - 2_001L, maximumAgeMillis = 2_000L)
        val rollback = load(source, lastKnownGoodGeneratedAtEpochMillis = NOW + 1L)

        assertEquals(setOf(OfflineCatalogAdmissionBlocker.EXPIRED_SNAPSHOT), expired.admission.blockers)
        assertTrue(expired.products.isEmpty())
        assertEquals(
            setOf(OfflineCatalogAdmissionBlocker.OLDER_THAN_LAST_KNOWN_GOOD),
            rollback.admission.blockers
        )
        assertTrue(rollback.products.isEmpty())
    }

    @Test
    fun `source hash and asset namespace mismatches fail closed before products load`() {
        val source = sourceText(listOf(productLine("off:milk", "Whole Milk", providerItemId = "milk")))
        val manifest = manifestJson(source)

        try {
            OfflineCatalogSnapshotAssetLoader.load(
                manifestJson = manifest,
                sourceJsonByNamespace = mapOf(DATASET_ID to source + "tampered\n"),
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = NOW,
                maximumSnapshotAgeMillis = MAX_AGE
            )
            throw AssertionError("Expected source hash rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("content hash"))
        }

        try {
            OfflineCatalogSnapshotAssetLoader.load(
                manifestJson = manifest,
                sourceJsonByNamespace = emptyMap(),
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = NOW,
                maximumSnapshotAgeMillis = MAX_AGE
            )
            throw AssertionError("Expected namespace rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("exactly match"))
        }
    }

    @Test
    fun `manifest region must match the region selected by the asset boundary`() {
        val source = sourceText(listOf(productLine("off:milk", "Whole Milk", providerItemId = "milk")))

        try {
            OfflineCatalogSnapshotAssetLoader.load(
                manifestJson = manifestJson(source),
                sourceJsonByNamespace = mapOf(DATASET_ID to source),
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = NOW,
                maximumSnapshotAgeMillis = MAX_AGE,
                expectedRegionId = "ca-metro-vancouver"
            )
            throw AssertionError("Expected region binding rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("does not match expected region"))
        }

        val loaded =
            OfflineCatalogSnapshotAssetLoader.load(
                manifestJson = manifestJson(source),
                sourceJsonByNamespace = mapOf(DATASET_ID to source),
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = NOW,
                maximumSnapshotAgeMillis = MAX_AGE,
                expectedRegionId = "ca-gta"
            )
        assertTrue(loaded.admission.accepted)
        assertEquals("ca-gta", loaded.manifest.regionId)
    }

    @Test
    fun `offer fields and duplicate record ids never enter the catalog`() {
        val offerLine = productLine("off:milk", "Whole Milk", providerItemId = "milk")
            .dropLast(1) + ",\"price\":\"4.99\"}"
        try {
            load(sourceText(listOf(offerLine)))
            throw AssertionError("Expected offer field rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("catalog identity/search fields"))
        }

        val duplicateSource = sourceText(
            listOf(productLine("off:milk", "Whole Milk", providerItemId = "milk"))
        )
        val first = sourceJson(DATASET_ID, PROVIDER_ID, "off-1", duplicateSource)
        val secondDataset = "off-ca-secondary"
        val secondSource = sourceText(
            listOf(
                productLine(
                    "off:milk",
                    "Whole Milk",
                    providerItemId = "milk",
                    providerId = "open-food-facts-secondary",
                    datasetId = secondDataset
                )
            )
        )
        val second = sourceJson(secondDataset, "open-food-facts-secondary", "off-2", secondSource)
        val duplicateManifest = manifestJson(listOf(first, second))
        try {
            OfflineCatalogSnapshotAssetLoader.load(
                manifestJson = duplicateManifest,
                sourceJsonByNamespace = mapOf(DATASET_ID to duplicateSource, secondDataset to secondSource),
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = NOW,
                maximumSnapshotAgeMillis = MAX_AGE
            )
            throw AssertionError("Expected duplicate record rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("record ids must be unique"))
        }
    }

    private fun load(
        source: String,
        integrity: OfflineCatalogIntegrityAssessment = verifiedIntegrity(),
        generatedAt: Long = NOW,
        maximumAgeMillis: Long = MAX_AGE,
        lastKnownGoodGeneratedAtEpochMillis: Long? = null
    ): OfflineCatalogAssetLoadResult =
        OfflineCatalogSnapshotAssetLoader.load(
            manifestJson = manifestJson(source, generatedAt = generatedAt),
            sourceJsonByNamespace = mapOf(DATASET_ID to source),
            integrity = integrity,
            evaluatedAtEpochMillis = NOW,
            maximumSnapshotAgeMillis = maximumAgeMillis,
            lastKnownGoodGeneratedAtEpochMillis = lastKnownGoodGeneratedAtEpochMillis
        )

    private fun manifestJson(
        source: String,
        generatedAt: Long = NOW
    ): String = manifestJson(listOf(sourceJson(DATASET_ID, PROVIDER_ID, "off-2026-09-03", source)), generatedAt)

    private fun manifestJson(sources: List<JSONObject>, generatedAt: Long = NOW): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("catalogRole", "IDENTITY_ONLY")
            .put("snapshotId", "ca-gta-2026-09-03")
            .put("regionId", "ca-gta")
            .put("generatedAtEpochMillis", generatedAt)
            .put("sources", JSONArray(sources))
            .put(
                "coverage",
                JSONObject()
                    .put("catalogRecordCount", sources.sumOf { it.getInt("recordCount") })
                    .put("currentOfferRecordCount", 0)
                    .put("currentOfferCoverage", "NOT_INCLUDED")
            )
            .toString()

    private fun sourceJson(
        datasetId: String,
        providerId: String,
        snapshotId: String,
        source: String
    ): JSONObject =
        JSONObject()
            .put(
                "namespace",
                JSONObject()
                    .put("id", datasetId)
                    .put("displayName", "Open Food Facts Canada")
                    .put("licenseId", "odbl-1.0")
                    .put("storageBoundary", "OPEN_SHARE_ALIKE")
            )
            .put(
                "snapshot",
                JSONObject()
                    .put("providerId", providerId)
                    .put("datasetNamespaceId", datasetId)
                    .put("snapshotId", snapshotId)
            )
            .put("authorization", authorizationJson(providerId, datasetId))
            .put("recordCount", source.lineSequence().count { it.isNotBlank() })
            .put("contentSha256", sha256(source))
            .put("acquiredAtEpochMillis", NOW - 10_000L)
            .put("sourcePublishedAtEpochMillis", NOW - 11_000L)

    private fun authorizationJson(providerId: String, datasetId: String): JSONObject {
        val gates = JSONArray()
        ProductionActivationProfiles.CONSUMER_MOBILE_PRODUCT_DISCOVERY.requiredGates
            .sortedBy(ProductionAuthorizationGate::ordinal)
            .forEach { gate ->
                gates.put(
                    JSONObject()
                        .put("gate", gate.name)
                        .put("state", "SATISFIED")
                        .put("basisId", "test-${gate.name.lowercase()}")
                )
            }
        return JSONObject()
            .put("providerId", providerId)
            .put("datasetNamespaceId", datasetId)
            .put("gates", gates)
    }

    private fun productLine(
        recordId: String,
        displayName: String,
        providerItemId: String,
        gtin: String? = null,
        brand: String? = null,
        aliases: List<String> = emptyList(),
        providerId: String = PROVIDER_ID,
        datasetId: String = DATASET_ID
    ): String {
        val identity = JSONObject().put("providerItemId", providerItemId)
        gtin?.let { identity.put("gtin", it) }
        return JSONObject()
            .put("recordId", recordId)
            .put("providerId", providerId)
            .put("datasetNamespaceId", datasetId)
            .put("sourceIdentity", identity)
            .put("displayName", displayName)
            .put("brand", brand ?: JSONObject.NULL)
            .put("canonicalSearchName", JvmTextCanonicalizer.search(displayName))
            .put("canonicalSearchBrand", brand?.let(JvmTextCanonicalizer::search) ?: JSONObject.NULL)
            .put("canonicalSearchAliases", JSONArray(aliases.map(JvmTextCanonicalizer::search).sorted()))
            .toString()
    }

    private fun sourceText(lines: List<String>): String = lines.joinToString(separator = "\n", postfix = "\n")

    private fun verifiedIntegrity(): OfflineCatalogIntegrityAssessment =
        OfflineCatalogIntegrityAssessment(
            manifestHash = OfflineCatalogIntegrityState.VERIFIED,
            signature = OfflineCatalogIntegrityState.VERIFIED,
            basisId = "test-manifest-signature"
        )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val DATASET_ID = "off-ca"
        private const val PROVIDER_ID = "open-food-facts"
        private const val NOW = 1_800_000_000_000L
        private const val MAX_AGE = 10_000L
    }
}

package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogIntegrityAssessment
import com.valuepilot.core.OfflineCatalogIntegrityState
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreDirectorySnapshotAssetLoaderTest {

    @Test
    fun `accepted signed summary and rows preserve location only boundaries`() {
        val source = sourceLine()
        val loaded = load(source)

        assertTrue(loaded.summary.accepted)
        assertEquals(StoreDirectoryAdmissionState.ACCEPTED, loaded.summary.admissionState)
        assertEquals(1, loaded.summary.totalRecordCount)
        assertEquals(mapOf("ca-gta" to 1, "ca-metro-vancouver" to 0), loaded.summary.regionRecordCounts)
        assertEquals("osm:node:123", loaded.records.single().recordId)
        assertEquals("LOCATION_ONLY", loaded.records.single().status)
        assertEquals("Toronto Grocery", loaded.records.single().name)
    }

    @Test
    fun `failed integrity blocks records while retaining diagnostic summary`() {
        val loaded = load(
            sourceLine(),
            integrity =
                OfflineCatalogIntegrityAssessment(
                    manifestHash = OfflineCatalogIntegrityState.UNKNOWN,
                    signature = OfflineCatalogIntegrityState.FAILED
                )
        )

        assertFalse(loaded.summary.accepted)
        assertEquals(StoreDirectoryAdmissionState.INVALID_INTEGRITY, loaded.summary.admissionState)
        assertTrue(loaded.records.isEmpty())
    }

    @Test
    fun `future and expired snapshots are not exposed as a usable directory`() {
        val source = sourceLine()
        val future = load(source, generatedAt = NOW + 1_000L)
        assertEquals(StoreDirectoryAdmissionState.FUTURE_DATED, future.summary.admissionState)
        assertTrue(future.records.isEmpty())

        val expired = load(source, generatedAt = NOW - 3_001L, maximumAgeMillis = 2_000L)
        assertEquals(StoreDirectoryAdmissionState.EXPIRED, expired.summary.admissionState)
        assertTrue(expired.records.isEmpty())
    }

    @Test
    fun `source hash mismatch and offer fields fail closed`() {
        val source = sourceLine()
        try {
            StoreDirectorySnapshotAssetLoader.load(
                manifestJson = manifestJson(source),
                sourceJsonl = source + "tampered\n",
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = NOW,
                maximumSnapshotAgeMillis = MAX_AGE
            )
            throw AssertionError("Expected source hash rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("content hash"))
        }

        val offerSource = sourceLine().replace("\"status\":\"LOCATION_ONLY\"", "\"price\":\"4.99\",\"status\":\"LOCATION_ONLY\"")
        try {
            StoreDirectorySnapshotAssetLoader.load(
                manifestJson = manifestJson(offerSource),
                sourceJsonl = offerSource,
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = NOW,
                maximumSnapshotAgeMillis = MAX_AGE
            )
            throw AssertionError("Expected offer field rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("offer fields"))
        }
    }

    @Test
    fun `bundled launch directory parses all signed rows without offer authority`() {
        val root = locateBundledDirectory()
        val manifest = File(root, "manifest.json").readText()
        val source = File(root, "sources/openstreetmap-places.jsonl").readText()
        val generatedAt = JSONObject(manifest).getLong("generatedAtEpochMillis")
        val loaded =
            StoreDirectorySnapshotAssetLoader.load(
                manifestJson = manifest,
                sourceJsonl = source,
                integrity = verifiedIntegrity(),
                evaluatedAtEpochMillis = generatedAt + 1L,
                maximumSnapshotAgeMillis = MAX_AGE
            )

        assertTrue(loaded.summary.accepted)
        assertEquals(6_093, loaded.records.size)
        assertEquals(4_311, loaded.records.count { it.regionId == "ca-gta" })
        assertEquals(1_782, loaded.records.count { it.regionId == "ca-metro-vancouver" })
        assertTrue(loaded.records.all { it.status == "LOCATION_ONLY" })
    }

    private fun load(
        source: String,
        integrity: OfflineCatalogIntegrityAssessment = verifiedIntegrity(),
        generatedAt: Long = NOW - 1_000L,
        maximumAgeMillis: Long = MAX_AGE
    ): StoreDirectoryAssetLoadResult =
        StoreDirectorySnapshotAssetLoader.load(
            manifestJson = manifestJson(source, generatedAt),
            sourceJsonl = source,
            integrity = integrity,
            evaluatedAtEpochMillis = NOW,
            maximumSnapshotAgeMillis = maximumAgeMillis
        )

    private fun sourceLine(): String =
        JSONObject(
            linkedMapOf(
                "recordId" to "osm:node:123",
                "sourceElementId" to "node/123",
                "sourceElementType" to "node",
                "providerId" to "openstreetmap",
                "datasetNamespaceId" to "openstreetmap-places",
                "regionId" to "ca-gta",
                "name" to "Toronto Grocery",
                "brand" to "Example Foods",
                "address" to JSONObject(linkedMapOf("city" to "Toronto", "street" to "Main Street")),
                "storeType" to "supermarket",
                "latitudeE7" to 436500000,
                "longitudeE7" to -793800000,
                "sourceSnapshotId" to "overpass-test-20260904",
                "licenseId" to "ODbL-1.0",
                "observedAtEpochMillis" to NOW - 4_000L,
                "confidence" to "SOURCE_LISTED",
                "status" to "LOCATION_ONLY"
            )
        ).toString() + "\n"

    private fun manifestJson(source: String, generatedAt: Long = NOW - 1_000L): String {
        val hash = sha256(source.toByteArray())
        return JSONObject(
            linkedMapOf(
                "schemaVersion" to 1,
                "snapshotRole" to "STORE_DIRECTORY",
                "snapshotId" to "directory-test-20260904",
                "generatedAtEpochMillis" to generatedAt,
                "source" to JSONObject(
                    linkedMapOf(
                        "providerId" to "openstreetmap",
                        "datasetNamespaceId" to "openstreetmap-places",
                        "displayName" to "OpenStreetMap places",
                        "licenseId" to "ODbL-1.0",
                        "storageBoundary" to "OPEN_SHARE_ALIKE",
                        "attribution" to "© OpenStreetMap contributors",
                        "sourceUrl" to "https://www.openstreetmap.org/copyright",
                        "licenseUrl" to "https://opendatacommons.org/licenses/odbl/1-0/",
                        "allowedUses" to listOf("access", "cache", "comparison", "display", "index", "mobile_app", "retention_deletion"),
                        "authorization" to JSONObject(
                            linkedMapOf(
                                "providerId" to "openstreetmap",
                                "datasetNamespaceId" to "openstreetmap-places",
                                "gates" to listOf(
                                    "DATA_ACCESS_AUTHORIZED",
                                    "CACHE_AUTHORIZED",
                                    "INDEX_AUTHORIZED",
                                    "CONSUMER_DISPLAY_AUTHORIZED",
                                    "MOBILE_APP_AUTHORIZED",
                                    "RETENTION_DELETION_POLICY_DEFINED",
                                    "GEOGRAPHY_SCOPED",
                                    "COMMERCIAL_USE_REVIEWED"
                                ).map { gate -> JSONObject(linkedMapOf("gate" to gate, "state" to "SATISFIED", "basisId" to "test-$gate")) }
                            )
                        ),
                        "sourceSnapshotId" to "overpass-test-20260904",
                        "rightsManifestId" to "rights.json",
                        "acquiredAtEpochMillis" to NOW - 4_000L,
                        "observedAtEpochMillis" to NOW - 4_000L
                    )
                ),
                "regions" to listOf(
                    JSONObject(linkedMapOf("regionId" to "ca-gta", "displayName" to "Greater Toronto Area", "boundingBoxE7" to JSONObject(linkedMapOf("minLatitude" to 433500000, "maxLatitude" to 443500000, "minLongitude" to -802000000, "maxLongitude" to -784500000)), "recordCount" to 1)),
                    JSONObject(linkedMapOf("regionId" to "ca-metro-vancouver", "displayName" to "Metro Vancouver", "boundingBoxE7" to JSONObject(linkedMapOf("minLatitude" to 489500000, "maxLatitude" to 495500000, "minLongitude" to -1235500000, "maxLongitude" to -1222000000)), "recordCount" to 0))
                ),
                "coverage" to JSONObject(linkedMapOf("storeRecordCount" to 1, "currentOfferRecordCount" to 0, "currentOfferCoverage" to "NOT_INCLUDED", "priceCoverage" to "NOT_INCLUDED", "stockCoverage" to "NOT_INCLUDED", "availabilityCoverage" to "NOT_INCLUDED", "regionRecordCounts" to JSONObject(linkedMapOf("ca-gta" to 1, "ca-metro-vancouver" to 0)))),
                "content" to JSONObject(linkedMapOf("path" to "sources/openstreetmap-places.jsonl", "sha256" to hash, "recordCount" to 1))
            )
        ).toString()
    }

    private fun verifiedIntegrity() =
        OfflineCatalogIntegrityAssessment(
            manifestHash = OfflineCatalogIntegrityState.VERIFIED,
            signature = OfflineCatalogIntegrityState.VERIFIED,
            basisId = "test-store-directory"
        )

    private fun locateBundledDirectory(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/assets/store_directory")
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate bundled store directory")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val NOW = 1_800_000_000_000L
        private const val MAX_AGE = 10_000L
    }
}

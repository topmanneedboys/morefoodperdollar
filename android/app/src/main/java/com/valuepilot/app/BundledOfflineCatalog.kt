package com.valuepilot.app

import android.content.Context
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.OfflineCatalogDiscoveryRequest
import com.valuepilot.core.OfflineCatalogIntegrityAssessment
import com.valuepilot.core.TextCanonicalizer
import java.nio.charset.StandardCharsets

/** The two explicitly supported Canada-first identity-discovery regions. */
enum class BundledOfflineCatalogRegion(
    val regionId: String,
    private val directoryName: String
) {
    GTA("ca-gta", "ca-gta"),
    METRO_VANCOUVER("ca-metro-vancouver", "ca-metro-vancouver");

    internal val manifestAssetPath: String
        get() = "offline_catalog/$directoryName/manifest.json"

    internal val checksumAssetPath: String
        get() = "offline_catalog/$directoryName/manifest.sha256"

    internal val signatureAssetPath: String
        get() = "offline_catalog/$directoryName/manifest.sig"

    internal val integrityAssetPath: String
        get() = "offline_catalog/$directoryName/integrity.json"
}

/**
 * Local-only entry point for the signed identity catalog bundled in the APK.
 *
 * The caller supplies evaluation time and the freshness/rollback policy. A
 * blocked or failed asset is returned with no products by the existing loader;
 * this object never falls back to network data or sample prices.
 */
object BundledOfflineCatalog {

    private const val SOURCE_ASSET_PATH = "offline_catalog/sources/off-ca.jsonl"
    private const val PUBLIC_KEY_ASSET_PATH = "offline_catalog/public-key.pem"
    private const val DATASET_NAMESPACE_ID = "off-ca"

    fun load(
        context: Context,
        region: BundledOfflineCatalogRegion,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long,
        lastKnownGoodGeneratedAtEpochMillis: Long? = null
    ): OfflineCatalogAssetLoadResult {
        val assets = context.assets
        val manifestBytes = assets.open(region.manifestAssetPath).use { it.readBytes() }
        val sourceText = assets.open(SOURCE_ASSET_PATH).use { it.readBytes().toStringUtf8() }
        val integrity =
            OfflineCatalogAssetIntegrityVerifier.assess(
                manifestBytes = manifestBytes,
                manifestChecksum =
                    assets.open(region.checksumAssetPath).use { it.readBytes().toStringUtf8() },
                integrityJson =
                    assets.open(region.integrityAssetPath).use { it.readBytes().toStringUtf8() },
                signatureBytes = assets.open(region.signatureAssetPath).use { it.readBytes() },
                publicKeyPem =
                    assets.open(PUBLIC_KEY_ASSET_PATH).use { it.readBytes().toStringUtf8() }
            )

        return OfflineCatalogSnapshotAssetLoader.load(
            manifestJson = manifestBytes.toStringUtf8(),
            sourceJsonByNamespace = mapOf(DATASET_NAMESPACE_ID to sourceText),
            integrity = integrity,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            maximumSnapshotAgeMillis = maximumSnapshotAgeMillis,
            lastKnownGoodGeneratedAtEpochMillis = lastKnownGoodGeneratedAtEpochMillis
        )
    }

    fun discover(
        context: Context,
        region: BundledOfflineCatalogRegion,
        rawQuery: String,
        canonicalizer: TextCanonicalizer,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long,
        lastKnownGoodGeneratedAtEpochMillis: Long? = null,
        maxResults: Int = OfflineCatalogDiscoveryRequest.MAX_RESULTS
    ): OfflineCatalogDiscoveryResult =
        load(
            context = context,
            region = region,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            maximumSnapshotAgeMillis = maximumSnapshotAgeMillis,
            lastKnownGoodGeneratedAtEpochMillis = lastKnownGoodGeneratedAtEpochMillis
        ).discover(rawQuery, canonicalizer, maxResults)

    private fun ByteArray.toStringUtf8(): String =
        String(this, StandardCharsets.UTF_8)
}

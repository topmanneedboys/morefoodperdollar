package com.valuepilot.app

import android.content.Context
import com.valuepilot.core.OfflineCatalogIntegrityAssessment
import java.nio.charset.StandardCharsets

/**
 * Local-only access to the signed OSM location directory.  It is intentionally
 * separate from product identity and offer loading: a directory row means a
 * source represents a physical location, never that the store has an item or
 * a current price.
 */
object BundledStoreDirectory {

    const val MAX_DIRECTORY_AGE_MILLIS = 180L * 24L * 60L * 60L * 1_000L

    private const val ROOT = "store_directory"
    private const val MANIFEST = "$ROOT/manifest.json"
    private const val CHECKSUM = "$ROOT/manifest.sha256"
    private const val INTEGRITY = "$ROOT/integrity.json"
    private const val SIGNATURE = "$ROOT/manifest.sig"
    private const val PUBLIC_KEY = "$ROOT/public-key.pem"
    private const val SOURCE = "$ROOT/sources/openstreetmap-places.jsonl"

    fun loadSummary(
        context: Context,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long = MAX_DIRECTORY_AGE_MILLIS
    ): StoreDirectorySummary {
        val assets = context.assets
        val manifestBytes = assets.open(MANIFEST).use { it.readBytes() }
        val sourceBytes = assets.open(SOURCE).use { it.readBytes() }
        return StoreDirectorySnapshotAssetLoader.loadSummary(
            manifestJson = manifestBytes.toStringUtf8(),
            sourceBytes = sourceBytes,
            integrity = assessIntegrity(context, manifestBytes),
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            maximumSnapshotAgeMillis = maximumSnapshotAgeMillis
        )
    }

    fun load(
        context: Context,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long = MAX_DIRECTORY_AGE_MILLIS
    ): StoreDirectoryAssetLoadResult {
        val assets = context.assets
        val manifestBytes = assets.open(MANIFEST).use { it.readBytes() }
        val sourceBytes = assets.open(SOURCE).use { it.readBytes() }
        return StoreDirectorySnapshotAssetLoader.load(
            manifestJson = manifestBytes.toStringUtf8(),
            sourceJsonl = sourceBytes.toStringUtf8(),
            integrity = assessIntegrity(context, manifestBytes),
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            maximumSnapshotAgeMillis = maximumSnapshotAgeMillis
        )
    }

    private fun assessIntegrity(context: Context, manifestBytes: ByteArray): OfflineCatalogIntegrityAssessment {
        val assets = context.assets
        return OfflineCatalogAssetIntegrityVerifier.assess(
            manifestBytes = manifestBytes,
            manifestChecksum = assets.open(CHECKSUM).use { it.readBytes().toStringUtf8() },
            integrityJson = assets.open(INTEGRITY).use { it.readBytes().toStringUtf8() },
            signatureBytes = assets.open(SIGNATURE).use { it.readBytes() },
            publicKeyPem = assets.open(PUBLIC_KEY).use { it.readBytes().toStringUtf8() }
        )
    }

    private fun ByteArray.toStringUtf8(): String = String(this, StandardCharsets.UTF_8)
}

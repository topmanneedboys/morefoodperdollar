package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogDiscoveryIndex
import com.valuepilot.core.OfflineCatalogDiscoveryRequest
import com.valuepilot.core.OfflineCatalogDiscoveryResult
import com.valuepilot.core.TextCanonicalizer

/**
 * The admitted, merged identity index for the supported metro snapshots.
 *
 * The generation timestamp is the oldest regional manifest timestamp. A
 * combined lookup cannot remain admissible after that oldest snapshot expires,
 * because Home deliberately fails closed when supported-region coverage is
 * incomplete.
 */
internal data class BundledOfflineCatalogLoadedIndex(
    val index: OfflineCatalogDiscoveryIndex,
    val earliestGeneratedAtEpochMillis: Long
) {
    init {
        require(earliestGeneratedAtEpochMillis > 0L)
    }
}

/**
 * Activity-scoped reuse of an already admitted immutable catalog index.
 *
 * Home and Search can ask identity questions repeatedly in one session. The
 * first request still reads, hashes, verifies and parses the signed APK
 * assets. Later requests reuse only that in-memory index while rechecking the
 * caller's freshness and rollback inputs. At most one bounded index is held;
 * no query results, source text, prices or availability are cached.
 */
internal class BundledOfflineCatalogDiscoverySession(
    private val loader: (
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long,
        lastKnownGoodGeneratedAtEpochMillis: Long?
    ) -> BundledOfflineCatalogLoadedIndex
) : AutoCloseable {

    private val lock = Any()
    private var cached: BundledOfflineCatalogLoadedIndex? = null
    private var closed = false

    fun discover(
        rawQuery: String,
        canonicalizer: TextCanonicalizer,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long,
        lastKnownGoodGeneratedAtEpochMillis: Long? = null,
        maxResults: Int = OfflineCatalogDiscoveryRequest.MAX_RESULTS
    ): OfflineCatalogDiscoveryResult {
        require(evaluatedAtEpochMillis > 0L)
        require(maximumSnapshotAgeMillis > 0L)
        lastKnownGoodGeneratedAtEpochMillis?.let { require(it > 0L) }

        val reusable =
            synchronized(lock) {
                check(!closed) { "Offline catalog discovery session is closed" }
                cached?.takeIf {
                    isUsable(
                        loaded = it,
                        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
                        maximumSnapshotAgeMillis = maximumSnapshotAgeMillis,
                        lastKnownGoodGeneratedAtEpochMillis =
                            lastKnownGoodGeneratedAtEpochMillis
                    )
                } ?: run {
                    // Do not retain a generation after its caller-supplied
                    // freshness or rollback boundary has failed.
                    cached = null
                    null
                }
            }

        val loaded =
            reusable
                ?: loader(
                    evaluatedAtEpochMillis,
                    maximumSnapshotAgeMillis,
                    lastKnownGoodGeneratedAtEpochMillis
                ).also { fresh ->
                    synchronized(lock) {
                        if (!closed) cached = fresh
                    }
                }

        return loaded.index.discover(rawQuery, canonicalizer, maxResults)
    }

    /** Release the bounded index when the owning Activity is torn down. */
    override fun close() {
        synchronized(lock) {
            closed = true
            cached = null
        }
    }

    private fun isUsable(
        loaded: BundledOfflineCatalogLoadedIndex,
        evaluatedAtEpochMillis: Long,
        maximumSnapshotAgeMillis: Long,
        lastKnownGoodGeneratedAtEpochMillis: Long?
    ): Boolean {
        val generatedAt = loaded.earliestGeneratedAtEpochMillis
        if (generatedAt > evaluatedAtEpochMillis) return false
        if (evaluatedAtEpochMillis - generatedAt > maximumSnapshotAgeMillis) return false
        if (
            lastKnownGoodGeneratedAtEpochMillis != null &&
                generatedAt < lastKnownGoodGeneratedAtEpochMillis
        ) {
            return false
        }
        return true
    }
}

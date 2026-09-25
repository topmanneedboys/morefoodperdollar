package com.valuepilot.app

import com.valuepilot.core.EvidenceDatasetNamespace
import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.EvidenceStorageBoundary
import com.valuepilot.core.OfflineCatalogDiscoveryIndex
import com.valuepilot.core.OfflineCatalogProduct
import com.valuepilot.core.SourceProductIdentity
import com.valuepilot.core.TextCanonicalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledOfflineCatalogDiscoverySessionTest {

    private val canonicalizer =
        object : TextCanonicalizer {
            override fun identity(value: String?): String = value.orEmpty().trim().lowercase()

            override fun search(value: String?): String =
                value.orEmpty()
                    .trim()
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), " ")
                    .replace(Regex("\\s+"), " ")
        }

    @Test
    fun `repeated Home identity lookups reuse one admitted index`() {
        var loadCount = 0
        val session =
            BundledOfflineCatalogDiscoverySession { _, _, _ ->
                loadCount++
                loaded(generatedAt = 1_000L)
            }

        val first =
            session.discover(
                rawQuery = "whole milk",
                canonicalizer = canonicalizer,
                evaluatedAtEpochMillis = 1_000L,
                maximumSnapshotAgeMillis = 10_000L
            )
        val second =
            session.discover(
                rawQuery = "oat milk",
                canonicalizer = canonicalizer,
                evaluatedAtEpochMillis = 1_001L,
                maximumSnapshotAgeMillis = 10_000L
            )

        assertEquals(1, loadCount)
        assertEquals(listOf("milk"), first.matches.map { it.product.recordId })
        assertEquals(listOf("oat"), second.matches.map { it.product.recordId })
        assertEquals(2, second.evaluatedCandidateCount)
    }

    @Test
    fun `one session serves a bounded thirty thousand identity index`() {
        var loadCount = 0
        val session =
            BundledOfflineCatalogDiscoverySession { _, _, _ ->
                loadCount++
                BundledOfflineCatalogLoadedIndex(
                    index =
                        OfflineCatalogDiscoveryIndex.build(
                            (0 until 30_000).map { index ->
                                product(
                                    id = "identity-$index",
                                    name = "Grocery item $index"
                                )
                            }
                        ),
                    earliestGeneratedAtEpochMillis = 4_000L
                )
            }

        val first =
            session.discover(
                rawQuery = "grocery item 29999",
                canonicalizer = canonicalizer,
                evaluatedAtEpochMillis = 4_000L,
                maximumSnapshotAgeMillis = 10_000L,
                maxResults = 1
            )
        val second =
            session.discover(
                rawQuery = "grocery item 1",
                canonicalizer = canonicalizer,
                evaluatedAtEpochMillis = 4_001L,
                maximumSnapshotAgeMillis = 10_000L,
                maxResults = 1
            )

        assertEquals(1, loadCount)
        assertEquals(30_000, first.evaluatedCandidateCount)
        assertEquals(30_000, second.evaluatedCandidateCount)
        assertEquals(listOf("identity-29999"), first.matches.map { it.product.recordId })
        assertEquals(listOf("identity-1"), second.matches.map { it.product.recordId })
    }

    @Test
    fun `expired cached generation is discarded before the next lookup`() {
        var loadCount = 0
        val session =
            BundledOfflineCatalogDiscoverySession { evaluatedAt, _, _ ->
                loadCount++
                loaded(generatedAt = evaluatedAt)
            }

        session.discover(
            rawQuery = "milk",
            canonicalizer = canonicalizer,
            evaluatedAtEpochMillis = 1_000L,
            maximumSnapshotAgeMillis = 100L
        )
        session.discover(
            rawQuery = "milk",
            canonicalizer = canonicalizer,
            evaluatedAtEpochMillis = 1_100L,
            maximumSnapshotAgeMillis = 100L
        )
        session.discover(
            rawQuery = "milk",
            canonicalizer = canonicalizer,
            evaluatedAtEpochMillis = 1_101L,
            maximumSnapshotAgeMillis = 100L
        )

        assertEquals(2, loadCount)
    }

    @Test
    fun `rollback and future-time inputs never reuse an older cached generation`() {
        var loadCount = 0
        val session =
            BundledOfflineCatalogDiscoverySession { evaluatedAt, _, _ ->
                loadCount++
                loaded(generatedAt = evaluatedAt)
            }

        session.discover(
            rawQuery = "milk",
            canonicalizer = canonicalizer,
            evaluatedAtEpochMillis = 2_000L,
            maximumSnapshotAgeMillis = 10_000L
        )
        session.discover(
            rawQuery = "milk",
            canonicalizer = canonicalizer,
            evaluatedAtEpochMillis = 2_001L,
            maximumSnapshotAgeMillis = 10_000L,
            lastKnownGoodGeneratedAtEpochMillis = 2_001L
        )
        session.discover(
            rawQuery = "milk",
            canonicalizer = canonicalizer,
            evaluatedAtEpochMillis = 1_999L,
            maximumSnapshotAgeMillis = 10_000L
        )

        assertEquals(3, loadCount)
    }

    @Test
    fun `closing releases the cached index and rejects later work`() {
        var loadCount = 0
        val session =
            BundledOfflineCatalogDiscoverySession { _, _, _ ->
                loadCount++
                loaded(generatedAt = 3_000L)
            }
        session.discover(
            rawQuery = "milk",
            canonicalizer = canonicalizer,
            evaluatedAtEpochMillis = 3_000L,
            maximumSnapshotAgeMillis = 10_000L
        )
        session.close()

        try {
            session.discover(
                rawQuery = "milk",
                canonicalizer = canonicalizer,
                evaluatedAtEpochMillis = 3_001L,
                maximumSnapshotAgeMillis = 10_000L
            )
            throw AssertionError("Expected a closed discovery session to reject work")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("closed"))
        }
        assertEquals(1, loadCount)
    }

    private fun loaded(generatedAt: Long): BundledOfflineCatalogLoadedIndex =
        BundledOfflineCatalogLoadedIndex(
            index =
                OfflineCatalogDiscoveryIndex.build(
                    listOf(product("milk", "Whole Milk"), product("oat", "Oat Milk"))
                ),
            earliestGeneratedAtEpochMillis = generatedAt
        )

    private fun product(id: String, name: String): OfflineCatalogProduct =
        OfflineCatalogProduct(
            recordId = id,
            providerId = FIXTURE_PROVIDER,
            dataset = FIXTURE_DATASET,
            sourceIdentity = SourceProductIdentity(providerItemId = id),
            displayName = name,
            canonicalSearchName = name.lowercase()
        )

    companion object {
        private val FIXTURE_PROVIDER = EvidenceProviderId("session-fixture-provider")
        private val FIXTURE_DATASET =
            EvidenceDatasetNamespace(
                id = "session-fixture-dataset",
                displayName = "Session fixture",
                licenseId = "fixture-reviewed-rights",
                storageBoundary = EvidenceStorageBoundary.OPEN_SHARE_ALIKE
            )
    }
}

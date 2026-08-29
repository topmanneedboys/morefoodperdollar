package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedExperienceCoordinatorTest {

    private val eggs = ShoppingItemKey("eggs")

    @Test
    fun `matching exact and display storage loads consumer projection`() {
        val exactStorage = FakeExactStorage()
        val displayStorage = FakeDisplayStorage()
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(exactStorage)
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayStorage)
        assertTrue(exactStore.replace(exactState()).accepted)
        assertTrue(displayStore.replace(displaySnapshot()).accepted)

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)

        assertTrue(loaded.accepted)
        assertFalse(loaded.displayMetadataDegraded)
        assertEquals("Example Eggs", requireNotNull(loaded.projection).state.productRows.single().title)
        assertEquals(0, loaded.projection?.state?.unresolvedDisplayNameCount)
        assertTrue(loaded.staleDisplayProductKeys.isEmpty())
    }

    @Test
    fun `missing display metadata degrades to unresolved labels without losing exact choices`() {
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(FakeExactStorage())
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(FakeDisplayStorage())
        assertTrue(exactStore.replace(exactState()).accepted)

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)

        assertTrue(loaded.accepted)
        assertFalse(loaded.displayMetadataDegraded)
        assertEquals(1, requireNotNull(loaded.exactState).productPreferences.size)
        assertTrue(requireNotNull(loaded.projection).state.productRows.isEmpty())
        assertEquals(1, loaded.projection?.state?.unresolvedDisplayNameCount)
        assertNull(loaded.displayStorageIssue)
    }

    @Test
    fun `corrupt display metadata is degradable and exact choices remain available`() {
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(FakeExactStorage())
        val displayStorage = FakeDisplayStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayStorage)
        assertTrue(exactStore.replace(exactState()).accepted)

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)

        assertTrue(loaded.accepted)
        assertTrue(loaded.displayMetadataDegraded)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataStorageIssue.STORED_DATA_INVALID,
            loaded.displayStorageIssue
        )
        assertEquals(
            PracticalShoppingSavedExactPreferenceDisplayMetadataCodecIssue.INVALID_HEADER,
            loaded.displayCodecIssue
        )
        assertEquals(1, requireNotNull(loaded.exactState).productPreferences.size)
        assertTrue(requireNotNull(loaded.projection).state.productRows.isEmpty())
        assertEquals(1, loaded.projection?.state?.unresolvedDisplayNameCount)
    }

    @Test
    fun `corrupt exact preferences fail fast before display storage is read`() {
        val exactStorage = FakeExactStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val displayStorage = FakeDisplayStorage()
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(exactStorage)
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayStorage)

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)

        assertFalse(loaded.accepted)
        assertEquals(
            PracticalShoppingSavedExperienceLoadIssue.EXACT_PREFERENCE_STORAGE_FAILURE,
            loaded.issue
        )
        assertEquals(
            PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_INVALID,
            loaded.exactStorageIssue
        )
        assertEquals(PracticalShoppingSavedExactPreferenceCodecIssue.INVALID_HEADER, loaded.exactCodecIssue)
        assertNull(loaded.projection)
        assertEquals(0, displayStorage.readCount)
    }

    @Test
    fun `stale stored label is reported and withheld from projection`() {
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(FakeExactStorage())
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(FakeDisplayStorage())
        assertTrue(exactStore.replace(exactState(gtin = "012345678905")).accepted)
        assertTrue(displayStore.replace(displaySnapshot()).accepted)

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)

        assertTrue(loaded.accepted)
        assertEquals(listOf(eggs), loaded.staleDisplayProductKeys)
        assertTrue(requireNotNull(loaded.projection).state.productRows.isEmpty())
        assertEquals(1, loaded.projection?.state?.unresolvedDisplayNameCount)
    }

    @Test
    fun `delete product mutates exact storage first and cleans matching display metadata`() {
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(FakeExactStorage())
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(FakeDisplayStorage())
        assertTrue(exactStore.replace(exactState()).accepted)
        assertTrue(displayStore.replace(displaySnapshot()).accepted)

        val result =
            PracticalShoppingSavedExperienceCoordinator.handleAction(
                exactStore,
                displayStore,
                PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(eggs)
            )

        assertTrue(result.accepted)
        assertFalse(result.displayCleanupDegraded)
        assertTrue(requireNotNull(result.exactState).productPreferences.isEmpty())
        assertTrue(requireNotNull(displayStore.load().snapshot).productEntries.isEmpty())
    }

    @Test
    fun `exact mutation failure does not touch display metadata`() {
        val exactStorage = FakeExactStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
        val displayStorage = FakeDisplayStorage()
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayStorage)
        assertTrue(displayStore.replace(displaySnapshot()).accepted)
        val oldDisplayBytes = requireNotNull(displayStorage.bytes).copyOf()
        displayStorage.readCount = 0

        val result =
            PracticalShoppingSavedExperienceCoordinator.handleAction(
                PracticalShoppingSavedExactPreferenceLocalStore(exactStorage),
                displayStore,
                PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(eggs)
            )

        assertFalse(result.accepted)
        assertEquals(
            PracticalShoppingSavedExperienceActionIssue.EXACT_PREFERENCE_MUTATION_FAILURE,
            result.issue
        )
        assertEquals(
            PracticalShoppingSavedExactPreferenceStorageIssue.STORED_DATA_INVALID,
            result.exactStorageIssue
        )
        assertEquals(0, displayStorage.readCount)
        assertArrayEquals(oldDisplayBytes, displayStorage.bytes)
    }

    @Test
    fun `display cleanup failure cannot roll back exact deletion and orphan label is withheld`() {
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(FakeExactStorage())
        val displayStorage = FakeDisplayStorage()
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayStorage)
        assertTrue(exactStore.replace(exactState()).accepted)
        assertTrue(displayStore.replace(displaySnapshot()).accepted)
        val oldDisplayBytes = requireNotNull(displayStorage.bytes).copyOf()
        displayStorage.failReplace = true

        val result =
            PracticalShoppingSavedExperienceCoordinator.handleAction(
                exactStore,
                displayStore,
                PracticalShoppingSavedExactPreferenceUiAction.DeleteProduct(eggs)
            )

        assertTrue(result.accepted)
        assertTrue(result.displayCleanupDegraded)
        assertEquals(PracticalShoppingSavedDisplayMetadataStorageIssue.WRITE_FAILED, result.displayCleanupIssue)
        assertTrue(requireNotNull(result.exactState).productPreferences.isEmpty())
        assertArrayEquals(oldDisplayBytes, displayStorage.bytes)

        displayStorage.failReplace = false
        val reloaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)
        assertTrue(reloaded.accepted)
        assertTrue(requireNotNull(reloaded.projection).state.productRows.isEmpty())
        assertEquals(listOf(eggs), reloaded.staleDisplayProductKeys)
        assertEquals("No saved choices yet.", reloaded.projection?.state?.emptyMessage)
    }

    @Test
    fun `clear all keeps exact state empty even when display file deletion fails`() {
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(FakeExactStorage())
        val displayStorage = FakeDisplayStorage()
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(displayStorage)
        assertTrue(exactStore.replace(exactState()).accepted)
        assertTrue(displayStore.replace(displaySnapshot()).accepted)
        val oldDisplayBytes = requireNotNull(displayStorage.bytes).copyOf()
        displayStorage.failDelete = true

        val result =
            PracticalShoppingSavedExperienceCoordinator.handleAction(
                exactStore,
                displayStore,
                PracticalShoppingSavedExactPreferenceUiAction.ClearAll
            )

        assertTrue(result.accepted)
        assertTrue(result.displayCleanupDegraded)
        assertEquals(
            PracticalShoppingSavedDisplayMetadataStorageIssue.DELETE_FAILED,
            result.displayCleanupIssue
        )
        assertTrue(requireNotNull(result.exactState).productPreferences.isEmpty())
        assertEquals(PracticalShoppingSavedExactPreferenceState.empty(), exactStore.load().state)
        assertArrayEquals(oldDisplayBytes, displayStorage.bytes)

        displayStorage.failDelete = false
        val reloaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)
        assertTrue(reloaded.accepted)
        assertEquals(listOf(eggs), reloaded.staleDisplayProductKeys)
        assertEquals("No saved choices yet.", reloaded.projection?.state?.emptyMessage)
    }

    private fun exactState(
        gtin: String = "036000291452"
    ): PracticalShoppingSavedExactPreferenceState =
        requireNotNull(
            PracticalShoppingSavedExactPreferenceStateManager.load(
                PracticalShoppingSavedExactPreferenceDocument(
                    schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                    productPreferences =
                        listOf(
                            PracticalShoppingSavedExactProductPreference(
                                itemKey = eggs,
                                providerId = EvidenceProviderId("open-food-facts"),
                                sourceIdentity = SourceProductIdentity(gtin = gtin),
                                dataset = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
                            )
                        ),
                    storePreferences = emptyList()
                )
            ).state
        )

    private fun displaySnapshot(): PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot =
        PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot(
            productEntries =
                listOf(
                    PracticalShoppingSavedProductDisplayMetadataEntry(
                        itemKey = eggs,
                        productKey =
                            ProductionProductEvidenceKey(
                                value = "gtin:0036000291452",
                                scope = ProductionProductKeyScope.CROSS_SOURCE_GTIN
                            ),
                        displayName = "Example Eggs",
                        basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                    )
                )
        )

    private class FakeExactStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedExactPreferenceByteStorage {
        var bytes: ByteArray? = bytes?.copyOf()
        var failReplace: Boolean = false
        var failDelete: Boolean = false
        var readCount: Int = 0

        override fun read(maxBytes: Int): PracticalShoppingSavedExactPreferenceRawReadResult {
            readCount += 1
            val current = bytes
                ?: return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = false
                )
            if (current.size > maxBytes) {
                return PracticalShoppingSavedExactPreferenceRawReadResult(
                    bytes = null,
                    found = true,
                    issue = PracticalShoppingSavedExactPreferenceRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return PracticalShoppingSavedExactPreferenceRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            if (failReplace) return false
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            if (failDelete) return false
            bytes = null
            return true
        }
    }

    private class FakeDisplayStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedDisplayMetadataByteStorage {
        var bytes: ByteArray? = bytes?.copyOf()
        var failReplace: Boolean = false
        var failDelete: Boolean = false
        var readCount: Int = 0

        override fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult {
            readCount += 1
            val current = bytes
                ?: return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = false
                )
            if (current.size > maxBytes) {
                return PracticalShoppingSavedDisplayMetadataRawReadResult(
                    bytes = null,
                    found = true,
                    issue = PracticalShoppingSavedDisplayMetadataRawReadIssue.INPUT_TOO_LARGE
                )
            }
            return PracticalShoppingSavedDisplayMetadataRawReadResult(
                bytes = current.copyOf(),
                found = true
            )
        }

        override fun replace(bytes: ByteArray): Boolean {
            if (failReplace) return false
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            if (failDelete) return false
            bytes = null
            return true
        }
    }
}

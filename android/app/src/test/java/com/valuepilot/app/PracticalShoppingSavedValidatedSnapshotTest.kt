package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKey
import com.valuepilot.core.ProductionProductKeyScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSavedValidatedSnapshotTest {

    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")
    private val originalStoreScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-north",
            locationKey = "location-north",
            commerceChannelKey = "PHYSICAL_STORE"
        )

    @Test
    fun `coordinator snapshot contains exact state and binder approved display metadata`() {
        val loaded = load(exactState(), displaySnapshot())

        val snapshot = requireNotNull(loaded.validatedSnapshot)
        assertEquals(loaded.exactState, snapshot.exactState)
        assertEquals("Example Eggs", snapshot.displayMetadata.productDisplayNames[eggs])
        assertEquals("North Market", snapshot.displayMetadata.storeDisplayNames[north])
        assertTrue(loaded.staleDisplayProductKeys.isEmpty())
        assertTrue(loaded.staleDisplayStoreKeys.isEmpty())
    }

    @Test
    fun `stale product and store labels are withheld from validated snapshot`() {
        val changedScope =
            PracticalShoppingStoreIdentityScope(
                merchantKey = "merchant-north",
                locationKey = "location-changed",
                commerceChannelKey = "PHYSICAL_STORE"
            )
        val loaded =
            load(
                exactState(
                    gtin = "012345678905",
                    storeScope = changedScope
                ),
                displaySnapshot()
            )

        val snapshot = requireNotNull(loaded.validatedSnapshot)
        assertFalse(snapshot.displayMetadata.productDisplayNames.containsKey(eggs))
        assertFalse(snapshot.displayMetadata.storeDisplayNames.containsKey(north))
        assertEquals(listOf(eggs), loaded.staleDisplayProductKeys)
        assertEquals(listOf(north), loaded.staleDisplayStoreKeys)
    }

    @Test
    fun `display storage failure degrades to empty validated metadata without losing exact state`() {
        val exactStorage = FakeExactStorage()
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(exactStorage)
        assertTrue(exactStore.replace(exactState()).accepted)
        val displayStore =
            PracticalShoppingSavedDisplayMetadataLocalStore(
                FakeDisplayStorage(bytes = "broken".toByteArray(Charsets.US_ASCII))
            )

        val loaded = PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)

        assertTrue(loaded.accepted)
        assertTrue(loaded.displayMetadataDegraded)
        val snapshot = requireNotNull(loaded.validatedSnapshot)
        assertEquals(loaded.exactState, snapshot.exactState)
        assertTrue(snapshot.displayMetadata.productDisplayNames.isEmpty())
        assertTrue(snapshot.displayMetadata.storeDisplayNames.isEmpty())
    }

    @Test
    fun `current accepted load emits validated snapshot as transient transition output`() {
        val controller = PracticalShoppingSavedLifecycleController()
        val started = controller.reduce(controller.initialState(), PracticalShoppingSavedLifecycleIntent.Refresh)
        val loaded = load(exactState(), displaySnapshot())

        val completed =
            controller.reduce(
                started.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = requireNotNull(started.state.activeRequestId),
                    result = loaded
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.READY, completed.state.status)
        assertEquals(loaded.validatedSnapshot, completed.validatedSnapshot)
        assertNull(completed.work)
        assertFalse(
            PracticalShoppingSavedLifecycleState::class.java.declaredFields
                .map { field -> field.name }
                .contains("validatedSnapshot")
        )
        assertTrue(
            PracticalShoppingSavedLifecycleTransition::class.java.declaredFields
                .map { field -> field.name }
                .contains("validatedSnapshot")
        )
    }

    @Test
    fun `stale load completion cannot emit validated snapshot`() {
        val controller = PracticalShoppingSavedLifecycleController()
        val first = controller.reduce(controller.initialState(), PracticalShoppingSavedLifecycleIntent.Refresh)
        val loaded = load(exactState(), displaySnapshot())
        val ready =
            controller.reduce(
                first.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = requireNotNull(first.state.activeRequestId),
                    result = loaded
                )
            ).state
        val refreshed = controller.reduce(ready, PracticalShoppingSavedLifecycleIntent.Refresh)

        val stale =
            controller.reduce(
                refreshed.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = 1L,
                    result = loaded
                )
            )

        assertSame(refreshed.state, stale.state)
        assertNull(stale.work)
        assertNull(stale.validatedSnapshot)
    }

    @Test
    fun `failed current load cannot emit validated snapshot`() {
        val controller = PracticalShoppingSavedLifecycleController()
        val started = controller.reduce(controller.initialState(), PracticalShoppingSavedLifecycleIntent.Refresh)

        val failed =
            controller.reduce(
                started.state,
                PracticalShoppingSavedLifecycleIntent.LoadCompleted(
                    requestId = requireNotNull(started.state.activeRequestId),
                    result =
                        PracticalShoppingSavedExperienceLoadResult(
                            projection = null,
                            exactState = null,
                            issue = PracticalShoppingSavedExperienceLoadIssue.EXACT_PREFERENCE_STORAGE_FAILURE,
                            exactStorageIssue = PracticalShoppingSavedExactPreferenceStorageIssue.READ_FAILED
                        )
                )
            )

        assertEquals(PracticalShoppingSavedLifecycleStatus.ERROR, failed.state.status)
        assertNull(failed.validatedSnapshot)
        assertNull(failed.work)
    }

    private fun load(
        state: PracticalShoppingSavedExactPreferenceState,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadataSnapshot
    ): PracticalShoppingSavedExperienceLoadResult {
        val exactStore = PracticalShoppingSavedExactPreferenceLocalStore(FakeExactStorage())
        val displayStore = PracticalShoppingSavedDisplayMetadataLocalStore(FakeDisplayStorage())
        assertTrue(exactStore.replace(state).accepted)
        assertTrue(displayStore.replace(metadata).accepted)
        return PracticalShoppingSavedExperienceCoordinator.load(exactStore, displayStore)
    }

    private fun exactState(
        gtin: String = "036000291452",
        storeScope: PracticalShoppingStoreIdentityScope = originalStoreScope
    ): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences =
                listOf(
                    PracticalShoppingSavedExactProductPreference(
                        itemKey = eggs,
                        providerId = EvidenceProviderId("open-food-facts"),
                        sourceIdentity = SourceProductIdentity(gtin = gtin),
                        dataset = OpenFoodFactsPracticalShoppingIdentityAdapter.DATASET_NAMESPACE
                    )
                ),
            storePreferences =
                listOf(
                    PracticalShoppingSavedExactStorePreference(
                        storeKey = north,
                        scope = storeScope
                    )
                )
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
                ),
            storeEntries =
                listOf(
                    PracticalShoppingSavedStoreDisplayMetadataEntry(
                        storeKey = north,
                        scope = originalStoreScope,
                        displayName = "North Market",
                        basis = PracticalShoppingSavedDisplayMetadataBasis.USER_PROVIDED
                    )
                )
        )

    private class FakeExactStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedExactPreferenceByteStorage {
        private var bytes: ByteArray? = bytes?.copyOf()

        override fun read(maxBytes: Int): PracticalShoppingSavedExactPreferenceRawReadResult {
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
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }

    private class FakeDisplayStorage(
        bytes: ByteArray? = null
    ) : PracticalShoppingSavedDisplayMetadataByteStorage {
        private var bytes: ByteArray? = bytes?.copyOf()

        override fun read(maxBytes: Int): PracticalShoppingSavedDisplayMetadataRawReadResult {
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
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }
}

package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSavedSetupCompositionCoordinatorTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun visibleRouteWaitsForValidatedSnapshotBeforeCreatingSetupSession() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))

        assertTrue(createdSnapshots.isEmpty())
        assertTrue(rendered.isEmpty())

        val accepted = snapshot()
        coordinator.onSnapshot(accepted)

        assertEquals(listOf(accepted), createdSnapshots)
        assertEquals(1, rendered.size)
        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, rendered.single().status)
    }

    @Test
    fun hiddenValidatedSnapshotIsCachedUntilRouteEntry() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        val accepted = snapshot()

        coordinator.onSnapshot(accepted)

        assertTrue(createdSnapshots.isEmpty())
        assertTrue(rendered.isEmpty())

        coordinator.onRouteVisibilityChanged(true)

        assertEquals(listOf(accepted), createdSnapshots)
        assertEquals(1, rendered.size)
    }

    @Test
    fun newerValidatedSnapshotReconcilesExistingVisibleSelection() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(watch(eggs))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))
        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, rendered.last().status)

        coordinator.onSnapshot(
            snapshot(
                savedState = savedState(products = listOf(eggs, bread), stores = listOf(west)),
                metadata = metadata(products = listOf(eggs, bread), stores = listOf(west))
            )
        )

        assertEquals(1, createdSnapshots.size)
        val state = rendered.last()
        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertEquals(1, state.watchedItemCount)
        assertFalse(state.usualStoreSelected)
        assertTrue(state.productRows.first { it.action.itemKey == eggs }.watched)
        assertFalse(state.productRows.any { it.title == "Whole Milk" })
    }

    @Test
    fun hideShowReusesMemoryOnlySessionAndHiddenActionsAreIgnored() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onRouteVisibilityChanged(false)
        val countWhileHidden = rendered.size

        coordinator.onSurfaceAction(watch(eggs))
        assertEquals(countWhileHidden, rendered.size)

        coordinator.onRouteVisibilityChanged(true)

        assertEquals(1, createdSnapshots.size)
        val state = rendered.last()
        assertEquals(1, state.watchedItemCount)
        assertTrue(state.productRows.first { it.action.itemKey == milk }.watched)
        assertFalse(state.productRows.first { it.action.itemKey == eggs }.watched)
    }

    @Test
    fun closeDropsSessionAndSnapshotAndIgnoresFutureInputs() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        val beforeClose = rendered.size

        coordinator.close()
        coordinator.onSnapshot(
            snapshot(
                savedState = savedState(products = listOf(eggs, bread), stores = listOf(west)),
                metadata = metadata(products = listOf(eggs, bread), stores = listOf(west))
            )
        )
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(eggs))

        assertTrue(coordinator.isClosed())
        assertEquals(1, createdSnapshots.size)
        assertEquals(beforeClose, rendered.size)
    }

    @Test
    fun compositionCoordinatorOwnsNoAndroidPersistenceFactEconomicOrDeliveryAuthority() {
        val source = source("StapleWatchSavedSetupCompositionCoordinator.kt").readText()

        assertTrue(source.contains("PracticalShoppingSavedValidatedSnapshotObserver"))
        assertTrue(source.contains("StapleWatchSavedSelectionRouteSession"))
        assertFalse(source.contains("StapleWatchSavedSelectionUiStatus"))
        listOf(
            "android.",
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedDisplayMetadataLocalStore",
            "PracticalShoppingSavedExperienceCoordinator",
            "ShoppingRequest",
            "Money",
            "SingleStorePlanCandidate",
            "ShoppingTravel",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse("Staples Saved composition must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun coordinator(
        rendered: MutableList<StapleWatchSavedSelectionUiState>,
        createdSnapshots: MutableList<PracticalShoppingSavedValidatedSnapshot>
    ): StapleWatchSavedSetupCompositionCoordinator =
        StapleWatchSavedSetupCompositionCoordinator { snapshot ->
            createdSnapshots += snapshot
            StapleWatchSavedSelectionRouteSession(
                initialSnapshot = snapshot,
                presenter =
                    StapleWatchSavedSelectionSurfacePresenter { state ->
                        rendered += state
                    }
            )
        }

    private fun watch(itemKey: ShoppingItemKey) =
        StapleWatchSavedIdentitySelectionAction.SetProductWatched(itemKey, watched = true)

    private fun snapshot(
        savedState: PracticalShoppingSavedExactPreferenceState = savedState(),
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata = metadata()
    ): PracticalShoppingSavedValidatedSnapshot =
        PracticalShoppingSavedValidatedSnapshot(
            exactState = savedState,
            displayMetadata = metadata
        )

    private fun savedState(
        products: List<ShoppingItemKey> = listOf(milk, eggs, bread),
        stores: List<ShoppingStoreKey> = listOf(north, west)
    ): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences =
                products.mapIndexed { index, itemKey ->
                    PracticalShoppingSavedExactProductPreference(
                        itemKey = itemKey,
                        providerId = EvidenceProviderId("test-provider"),
                        sourceIdentity = SourceProductIdentity(providerItemId = "product-$index")
                    )
                },
            storePreferences =
                stores.mapIndexed { index, storeKey ->
                    PracticalShoppingSavedExactStorePreference(
                        storeKey = storeKey,
                        scope =
                            PracticalShoppingStoreIdentityScope(
                                merchantKey = "merchant-$index",
                                locationKey = "location-$index",
                                commerceChannelKey = "PHYSICAL_STORE"
                            )
                    )
                }
        )

    private fun metadata(
        products: List<ShoppingItemKey> = listOf(milk, eggs, bread),
        stores: List<ShoppingStoreKey> = listOf(north, west)
    ): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames =
                products.associateWith { itemKey ->
                    when (itemKey) {
                        milk -> "Whole Milk"
                        eggs -> "Large Eggs"
                        bread -> "Sandwich Bread"
                        else -> "Saved Product"
                    }
                },
            storeDisplayNames =
                stores.associateWith { storeKey ->
                    when (storeKey) {
                        north -> "North Market"
                        west -> "West Market"
                        else -> "Saved Store"
                    }
                }
        )

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}

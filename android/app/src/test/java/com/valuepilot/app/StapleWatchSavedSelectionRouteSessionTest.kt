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

class StapleWatchSavedSelectionRouteSessionTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun routeEntryStartsFromEmptyExplicitSelectionAndRendersOnce() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val session = session(rendered = rendered)

        assertTrue(rendered.isEmpty())

        session.onRouteVisibilityChanged(true)
        session.onRouteVisibilityChanged(true)

        assertEquals(1, rendered.size)
        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, rendered.single().status)
        assertEquals(0, rendered.single().watchedItemCount)
        assertFalse(rendered.single().usualStoreSelected)
    }

    @Test
    fun visibleTypedActionsRemainTemporaryAndOnlyReprojectSetupState() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)

        session.onSurfaceAction(watch(milk))
        session.onSurfaceAction(watch(eggs))
        session.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        val state = rendered.last()
        assertEquals(4, rendered.size)
        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, state.status)
        assertEquals(2, state.watchedItemCount)
        assertTrue(state.usualStoreSelected)
        assertTrue(state.productRows.first { it.action.itemKey == milk }.watched)
        assertTrue(state.productRows.first { it.action.itemKey == eggs }.watched)
    }

    @Test
    fun hiddenActionsAreIgnoredWhileHideShowPreservesSessionSelection() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(watch(milk))
        session.onRouteVisibilityChanged(false)
        val renderCountWhileHidden = rendered.size

        session.onSurfaceAction(watch(eggs))

        assertEquals(renderCountWhileHidden, rendered.size)

        session.onRouteVisibilityChanged(true)

        val state = rendered.last()
        assertEquals(renderCountWhileHidden + 1, rendered.size)
        assertEquals(1, state.watchedItemCount)
        assertTrue(state.productRows.first { it.action.itemKey == milk }.watched)
        assertFalse(state.productRows.first { it.action.itemKey == eggs }.watched)
    }

    @Test
    fun visibleSavedSnapshotChangeReconcilesRemovedSelectionsAndRendersCurrentState() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(watch(milk))
        session.onSurfaceAction(watch(eggs))
        session.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))
        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, rendered.last().status)

        session.onSavedSnapshotChanged(
            savedState = savedState(products = listOf(eggs, bread), stores = listOf(west)),
            metadata = metadata(products = listOf(eggs, bread), stores = listOf(west))
        )

        val state = rendered.last()
        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertEquals(1, state.watchedItemCount)
        assertFalse(state.usualStoreSelected)
        assertTrue(state.productRows.first { it.action.itemKey == eggs }.watched)
        assertFalse(state.productRows.first { it.action.itemKey == bread }.watched)
        assertFalse(state.productRows.any { it.title == "Whole Milk" })
    }

    @Test
    fun hiddenSavedSnapshotChangeReconcilesWithoutRenderingUntilReentry() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(watch(milk))
        session.onRouteVisibilityChanged(false)
        val renderCountBeforeSnapshot = rendered.size

        session.onSavedSnapshotChanged(
            savedState = savedState(products = listOf(eggs, bread), stores = listOf(north)),
            metadata = metadata(products = listOf(eggs, bread), stores = listOf(north))
        )

        assertEquals(renderCountBeforeSnapshot, rendered.size)

        session.onRouteVisibilityChanged(true)

        val state = rendered.last()
        assertEquals(renderCountBeforeSnapshot + 1, rendered.size)
        assertEquals(0, state.watchedItemCount)
        assertFalse(state.productRows.any { it.watched })
    }

    @Test
    fun staleVisibleActionFailsClosedAndRendersReconciledSnapshot() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        val staleMilkAction =
            rendered.last().productRows.first { it.action.itemKey == milk }.action

        session.onSavedSnapshotChanged(
            savedState = savedState(products = listOf(eggs, bread), stores = listOf(north)),
            metadata = metadata(products = listOf(eggs, bread), stores = listOf(north))
        )
        val beforeStaleAction = rendered.size

        session.onSurfaceAction(staleMilkAction)

        val state = rendered.last()
        assertEquals(beforeStaleAction + 1, rendered.size)
        assertEquals(0, state.watchedItemCount)
        assertFalse(state.productRows.any { it.title == "Whole Milk" })
        assertFalse(state.productRows.any { it.watched })
    }

    @Test
    fun displayMetadataChangesCanBlockAndUnblockPresentationWithoutChangingSelection() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val saved = savedState()
        val session = session(initialSavedState = saved, rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(watch(milk))
        session.onSurfaceAction(watch(eggs))
        session.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))
        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, rendered.last().status)

        session.onSavedSnapshotChanged(
            savedState = saved,
            metadata =
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    productDisplayNames = mapOf(eggs to "Large Eggs"),
                    storeDisplayNames = mapOf(north to "North Market")
                )
        )

        assertEquals(
            StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE,
            rendered.last().status
        )
        assertEquals(2, rendered.last().watchedItemCount)
        assertTrue(rendered.last().usualStoreSelected)

        session.onSavedSnapshotChanged(savedState = saved, metadata = metadata())

        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, rendered.last().status)
        assertEquals(2, rendered.last().watchedItemCount)
        assertTrue(rendered.last().usualStoreSelected)
    }

    @Test
    fun closeStopsFurtherRouteSnapshotAndActionRendering() {
        val rendered = mutableListOf<StapleWatchSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(watch(milk))
        val beforeClose = rendered.size

        session.close()
        session.onSurfaceAction(watch(eggs))
        session.onSavedSnapshotChanged(
            savedState = savedState(products = listOf(eggs, bread), stores = listOf(west)),
            metadata = metadata(products = listOf(eggs, bread), stores = listOf(west))
        )
        session.onRouteVisibilityChanged(true)

        assertEquals(beforeClose, rendered.size)
    }

    @Test
    fun routeSessionOwnsNoPersistenceFactEconomicOrDeliveryAuthority() {
        val source = source("StapleWatchSavedSelectionRouteSession.kt").readText()

        assertTrue(source.contains("StapleWatchSavedIdentitySelectionReducer"))
        assertTrue(source.contains("StapleWatchSavedSelectionSurfacePresenter"))
        assertFalse(source.contains("identityHandoffOrNull"))
        assertFalse(source.contains("ShoppingRequest"))
        assertFalse(source.contains("StapleWatchSavedSelectionUiStatus"))
        listOf(
            "android.",
            "PracticalShoppingSavedAndroidSession",
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedDisplayMetadataLocalStore",
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
            assertFalse("Staples setup route session must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun session(
        initialSavedState: PracticalShoppingSavedExactPreferenceState = savedState(),
        initialMetadata: PracticalShoppingSavedExactPreferenceDisplayMetadata = metadata(),
        rendered: MutableList<StapleWatchSavedSelectionUiState>
    ): StapleWatchSavedSelectionRouteSession =
        StapleWatchSavedSelectionRouteSession(
            initialSavedState = initialSavedState,
            initialMetadata = initialMetadata,
            presenter =
                StapleWatchSavedSelectionSurfacePresenter { state ->
                    rendered += state
                }
        )

    private fun watch(itemKey: ShoppingItemKey) =
        StapleWatchSavedIdentitySelectionAction.SetProductWatched(itemKey, watched = true)

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

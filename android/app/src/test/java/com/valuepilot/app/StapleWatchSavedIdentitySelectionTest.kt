package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSavedIdentitySelectionTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun savedChoicesNeverBecomeWatchedWithoutExplicitSelection() {
        val saved = savedState(
            products = listOf(milk, eggs, bread),
            stores = listOf(north)
        )

        val selection = StapleWatchSavedIdentitySelectionReducer.initial()

        assertTrue(selection.watchedItemKeys.isEmpty())
        assertNull(selection.usualStoreKey)
        assertNull(
            StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(
                selection,
                saved
            )
        )
    }

    @Test
    fun twoExplicitProductsAndExplicitUsualStoreCreateIdentityHandoff() {
        val saved = savedState(
            products = listOf(milk, eggs, bread),
            stores = listOf(north, west)
        )
        var selection = StapleWatchSavedIdentitySelectionReducer.initial()

        selection = reduce(selection, saved, watch(milk)).state
        assertNull(StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(selection, saved))

        selection = reduce(selection, saved, watch(eggs)).state
        assertNull(StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(selection, saved))

        selection =
            reduce(
                selection,
                saved,
                StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north)
            ).state

        val handoff =
            requireNotNull(
                StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(selection, saved)
            )

        assertEquals(listOf(eggs, milk), handoff.request.itemKeys)
        assertEquals(north, handoff.usualStoreKey)
    }

    @Test
    fun selectingUnsavedProductFailsClosedWithoutChangingState() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val previous = reduce(
            StapleWatchSavedIdentitySelectionReducer.initial(),
            saved,
            watch(milk)
        ).state
        val unknown = ShoppingItemKey("unknown")

        val transition = reduce(previous, saved, watch(unknown))

        assertFalse(transition.accepted)
        assertEquals(StapleWatchSavedIdentitySelectionIssue.PRODUCT_NOT_SAVED, transition.issue)
        assertEquals(previous, transition.state)
    }

    @Test
    fun selectingUnsavedStoreFailsClosedWithoutChangingState() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val previous = StapleWatchSavedIdentitySelectionReducer.initial()
        val unknown = ShoppingStoreKey("unknown-store")

        val transition =
            reduce(
                previous,
                saved,
                StapleWatchSavedIdentitySelectionAction.SelectUsualStore(unknown)
            )

        assertFalse(transition.accepted)
        assertEquals(StapleWatchSavedIdentitySelectionIssue.STORE_NOT_SAVED, transition.issue)
        assertEquals(previous, transition.state)
    }

    @Test
    fun unwatchingProductOrClearingUsualStoreRemovesHandoffReadiness() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        var selection = StapleWatchSavedIdentitySelectionReducer.initial()
        selection = reduce(selection, saved, watch(milk)).state
        selection = reduce(selection, saved, watch(eggs)).state
        selection =
            reduce(
                selection,
                saved,
                StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north)
            ).state
        assertTrue(
            StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(selection, saved) != null
        )

        selection = reduce(selection, saved, unwatch(eggs)).state
        assertNull(StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(selection, saved))

        selection = reduce(selection, saved, watch(eggs)).state
        selection =
            reduce(
                selection,
                saved,
                StapleWatchSavedIdentitySelectionAction.ClearUsualStore
            ).state
        assertNull(StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(selection, saved))
    }

    @Test
    fun reconcileOnlyRemovesDeletedSavedChoicesAndNeverAutoAddsNewOnes() {
        val firstSaved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        var selection = StapleWatchSavedIdentitySelectionReducer.initial()
        selection = reduce(selection, firstSaved, watch(milk)).state
        selection = reduce(selection, firstSaved, watch(eggs)).state
        selection =
            reduce(
                selection,
                firstSaved,
                StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north)
            ).state

        val changedSaved = savedState(products = listOf(milk, bread), stores = listOf(west))
        val reconciled =
            StapleWatchSavedIdentitySelectionReducer.reconcile(selection, changedSaved)

        assertEquals(listOf(milk), reconciled.watchedItemKeys)
        assertFalse(reconciled.watchedItemKeys.contains(bread))
        assertNull(reconciled.usualStoreKey)
        assertNull(
            StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull(
                reconciled,
                changedSaved
            )
        )
    }

    @Test
    fun selectionOrderIsDeterministicRegardlessOfActionOrder() {
        val saved = savedState(products = listOf(milk, eggs, bread), stores = listOf(north))
        var selection = StapleWatchSavedIdentitySelectionReducer.initial()

        selection = reduce(selection, saved, watch(milk)).state
        selection = reduce(selection, saved, watch(bread)).state
        selection = reduce(selection, saved, watch(eggs)).state

        assertEquals(listOf(bread, eggs, milk), selection.watchedItemKeys)
    }

    @Test
    fun clearSelectionDoesNotMutateSavedChoices() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        var selection = StapleWatchSavedIdentitySelectionReducer.initial()
        selection = reduce(selection, saved, watch(milk)).state
        selection = reduce(selection, saved, watch(eggs)).state
        selection =
            reduce(
                selection,
                saved,
                StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north)
            ).state

        val cleared =
            reduce(
                selection,
                saved,
                StapleWatchSavedIdentitySelectionAction.ClearSelection
            ).state

        assertTrue(cleared.watchedItemKeys.isEmpty())
        assertNull(cleared.usualStoreKey)
        assertEquals(2, saved.productPreferences.size)
        assertEquals(1, saved.storePreferences.size)
    }

    @Test
    fun selectionBoundaryOwnsNoEconomicNotificationOrAndroidAuthority() {
        val source = source("StapleWatchSavedIdentitySelection.kt").readText()

        assertTrue(source.contains("PracticalShoppingSavedExactPreferenceState"))
        assertTrue(source.contains("ShoppingRequest"))
        listOf(
            "Money",
            "SingleStorePlanCandidate",
            "ShoppingTravel",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis",
            "android."
        ).forEach { forbidden ->
            assertFalse("Saved identity selection must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun reduce(
        previous: StapleWatchSavedIdentitySelection,
        saved: PracticalShoppingSavedExactPreferenceState,
        action: StapleWatchSavedIdentitySelectionAction
    ): StapleWatchSavedIdentitySelectionTransition =
        StapleWatchSavedIdentitySelectionReducer.reduce(previous, saved, action)

    private fun watch(itemKey: ShoppingItemKey) =
        StapleWatchSavedIdentitySelectionAction.SetProductWatched(itemKey, watched = true)

    private fun unwatch(itemKey: ShoppingItemKey) =
        StapleWatchSavedIdentitySelectionAction.SetProductWatched(itemKey, watched = false)

    private fun savedState(
        products: List<ShoppingItemKey>,
        stores: List<ShoppingStoreKey>
    ): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences =
                products.mapIndexed { index, key ->
                    PracticalShoppingSavedExactProductPreference(
                        itemKey = key,
                        providerId = EvidenceProviderId("test-provider"),
                        sourceIdentity = SourceProductIdentity(providerItemId = "product-$index")
                    )
                },
            storePreferences =
                stores.mapIndexed { index, key ->
                    PracticalShoppingSavedExactStorePreference(
                        storeKey = key,
                        scope =
                            PracticalShoppingStoreIdentityScope(
                                merchantKey = "merchant-$index",
                                locationKey = "location-$index",
                                commerceChannelKey = "PHYSICAL_STORE"
                            )
                    )
                }
        )

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

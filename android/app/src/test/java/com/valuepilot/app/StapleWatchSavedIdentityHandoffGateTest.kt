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

class StapleWatchSavedIdentityHandoffGateTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun `explicit request accepts two watched saved products and saved usual store`() {
        val snapshot = snapshot()
        val selection = readySelection(snapshot.exactState)

        val attempt = StapleWatchSavedIdentityHandoffGate.request(selection, snapshot)

        assertTrue(attempt.accepted)
        assertNull(attempt.issue)
        assertEquals(listOf(eggs, milk), attempt.handoff?.request?.itemKeys)
        assertEquals(north, attempt.handoff?.usualStoreKey)
    }

    @Test
    fun `request before identity readiness fails closed`() {
        val snapshot = snapshot()
        var selection = StapleWatchSavedIdentitySelectionReducer.initial()
        selection = reduce(selection, snapshot.exactState, watch(milk))
        selection =
            reduce(
                selection,
                snapshot.exactState,
                StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north)
            )

        val attempt = StapleWatchSavedIdentityHandoffGate.request(selection, snapshot)

        assertFalse(attempt.accepted)
        assertNull(attempt.handoff)
        assertEquals(StapleWatchSavedIdentityHandoffIssue.NOT_READY, attempt.issue)
    }

    @Test
    fun `selected product without safe display metadata blocks explicit handoff`() {
        val saved = savedState()
        val selection = readySelection(saved)
        val snapshot =
            snapshot(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(eggs to "Large Eggs", bread to "Bread"),
                        storeDisplayNames = mapOf(north to "North Market", west to "West Market")
                    )
            )

        val attempt = StapleWatchSavedIdentityHandoffGate.request(selection, snapshot)

        assertFalse(attempt.accepted)
        assertNull(attempt.handoff)
        assertEquals(
            StapleWatchSavedIdentityHandoffIssue.SELECTED_DISPLAY_METADATA_INCOMPLETE,
            attempt.issue
        )
    }

    @Test
    fun `selected store without safe display metadata blocks explicit handoff`() {
        val saved = savedState()
        val selection = readySelection(saved)
        val snapshot =
            snapshot(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames =
                            mapOf(milk to "Whole Milk", eggs to "Large Eggs", bread to "Bread"),
                        storeDisplayNames = mapOf(west to "West Market")
                    )
            )

        val attempt = StapleWatchSavedIdentityHandoffGate.request(selection, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(
            StapleWatchSavedIdentityHandoffIssue.SELECTED_DISPLAY_METADATA_INCOMPLETE,
            attempt.issue
        )
    }

    @Test
    fun `unresolved unselected saved choices do not block explicit handoff`() {
        val saved = savedState()
        val selection = readySelection(saved)
        val snapshot =
            snapshot(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(milk to "Whole Milk", eggs to "Large Eggs"),
                        storeDisplayNames = mapOf(north to "North Market")
                    )
            )

        val attempt = StapleWatchSavedIdentityHandoffGate.request(selection, snapshot)

        assertTrue(attempt.accepted)
        assertEquals(listOf(eggs, milk), attempt.handoff?.request?.itemKeys)
        assertEquals(north, attempt.handoff?.usualStoreKey)
    }

    @Test
    fun `newer validated snapshot removing a selected identity invalidates handoff`() {
        val original = snapshot()
        val selection = readySelection(original.exactState)
        val changed =
            snapshot(
                savedState = savedState(products = listOf(eggs, bread), stores = listOf(north, west)),
                metadata = metadata(products = listOf(eggs, bread), stores = listOf(north, west))
            )

        val attempt = StapleWatchSavedIdentityHandoffGate.request(selection, changed)

        assertFalse(attempt.accepted)
        assertEquals(StapleWatchSavedIdentityHandoffIssue.NOT_READY, attempt.issue)
    }

    @Test
    fun `unsafe selected provider label is treated as unresolved by verified Saved projector`() {
        val saved = savedState()
        val selection = readySelection(saved)
        val snapshot =
            snapshot(
                savedState = saved,
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames =
                            mapOf(
                                milk to "test-provider",
                                eggs to "Large Eggs",
                                bread to "Bread"
                            ),
                        storeDisplayNames = mapOf(north to "North Market", west to "West Market")
                    )
            )

        val attempt = StapleWatchSavedIdentityHandoffGate.request(selection, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(
            StapleWatchSavedIdentityHandoffIssue.SELECTED_DISPLAY_METADATA_INCOMPLETE,
            attempt.issue
        )
    }

    @Test
    fun `handoff gate owns no fact economic persistence android or delivery authority`() {
        val source = source("StapleWatchSavedIdentityHandoffGate.kt").readText()

        assertTrue(source.contains("identityHandoffOrNull"))
        assertTrue(source.contains("PracticalShoppingSavedExactPreferenceUiProjector"))
        listOf(
            "Money",
            "SingleStorePlanCandidate",
            "ShoppingTravel",
            "StapleWatchAlternativeCandidate",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "OpenPrices",
            "OpenStreetMap",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis",
            "android."
        ).forEach { forbidden ->
            assertFalse("Identity handoff gate must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun readySelection(
        saved: PracticalShoppingSavedExactPreferenceState
    ): StapleWatchSavedIdentitySelection {
        var selection = StapleWatchSavedIdentitySelectionReducer.initial()
        selection = reduce(selection, saved, watch(milk))
        selection = reduce(selection, saved, watch(eggs))
        selection =
            reduce(
                selection,
                saved,
                StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north)
            )
        return selection
    }

    private fun reduce(
        previous: StapleWatchSavedIdentitySelection,
        saved: PracticalShoppingSavedExactPreferenceState,
        action: StapleWatchSavedIdentitySelectionAction
    ): StapleWatchSavedIdentitySelection =
        StapleWatchSavedIdentitySelectionReducer.reduce(previous, saved, action).state

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

    private fun metadata(
        products: List<ShoppingItemKey> = listOf(milk, eggs, bread),
        stores: List<ShoppingStoreKey> = listOf(north, west)
    ): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames =
                products.associateWith { key ->
                    when (key) {
                        milk -> "Whole Milk"
                        eggs -> "Large Eggs"
                        bread -> "Sandwich Bread"
                        else -> "Saved Product"
                    }
                },
            storeDisplayNames =
                stores.associateWith { key ->
                    when (key) {
                        north -> "North Market"
                        west -> "West Market"
                        else -> "Saved Store"
                    }
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

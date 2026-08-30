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

class StapleWatchSavedIdentitySelectionUiProjectorTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun initialSavedChoicesAreSelectableButNeverAutoWatched() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))

        val state = project(saved, StapleWatchSavedIdentitySelectionReducer.initial(), fullMetadata())

        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertEquals(2, state.productRows.size)
        assertTrue(state.productRows.none { it.watched })
        assertEquals(1, state.storeRows.size)
        assertFalse(state.storeRows.single().usualStore)
        assertEquals(0, state.watchedItemCount)
        assertFalse(state.usualStoreSelected)
        assertNull(state.clearSelectionAction)
        assertNull(state.clearSelectionActionLabel)
    }

    @Test
    fun safeExplicitSelectionBecomesReadyAndActionsReverseTheSelection() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north, west))
        val selection = selection(watched = listOf(milk, eggs), usualStore = north)

        val state = project(saved, selection, fullMetadata())

        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, state.status)
        assertEquals(2, state.watchedItemCount)
        assertTrue(state.usualStoreSelected)
        assertEquals(0, state.selectedDisplayNameBlockerCount)
        assertEquals(0, state.unresolvedDisplayNameCount)

        val milkRow = state.productRows.single { it.title == "Whole Milk" }
        assertTrue(milkRow.watched)
        assertFalse(milkRow.action.watched)
        assertEquals("Stop watching", milkRow.actionLabel)

        val northRow = state.storeRows.single { it.title == "North Market" }
        assertTrue(northRow.usualStore)
        assertEquals(StapleWatchSavedIdentitySelectionAction.ClearUsualStore, northRow.action)
        assertEquals("Clear usual store", northRow.actionLabel)
        assertEquals(StapleWatchSavedIdentitySelectionAction.ClearSelection, state.clearSelectionAction)
        assertEquals("Clear staple setup", state.clearSelectionActionLabel)
    }

    @Test
    fun selectedWatchedProductWithoutSafeDisplayNameBlocksReadyState() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val selection = selection(watched = listOf(milk, eggs), usualStore = north)
        val metadata =
            fullMetadata().copy(
                productDisplayNames = mapOf(eggs to "Large Eggs")
            )

        val state = project(saved, selection, metadata)

        assertEquals(StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertEquals(1, state.selectedDisplayNameBlockerCount)
        assertEquals(1, state.unresolvedDisplayNameCount)
        assertEquals(listOf("Large Eggs"), state.productRows.map { it.title })
        assertTrue(state.notice?.contains("1 selected saved choice") == true)
        assertFalse(state.productRows.any { it.title.contains(milk.value, ignoreCase = true) })
    }

    @Test
    fun selectedUsualStoreWithoutSafeDisplayNameBlocksReadyState() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val selection = selection(watched = listOf(milk, eggs), usualStore = north)
        val metadata =
            fullMetadata().copy(
                storeDisplayNames = emptyMap()
            )

        val state = project(saved, selection, metadata)

        assertEquals(StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertEquals(1, state.selectedDisplayNameBlockerCount)
        assertTrue(state.usualStoreSelected)
        assertTrue(state.storeRows.isEmpty())
        assertNull(state.storeSectionTitle)
        assertTrue(state.notice?.contains("selected saved choice") == true)
    }

    @Test
    fun unresolvedUnselectedSavedChoiceIsHiddenWithoutBlockingReadySelection() {
        val saved = savedState(products = listOf(milk, eggs, bread), stores = listOf(north))
        val selection = selection(watched = listOf(milk, eggs), usualStore = north)
        val metadata =
            fullMetadata().copy(
                productDisplayNames =
                    mapOf(
                        milk to "Whole Milk",
                        eggs to "Large Eggs"
                    )
            )

        val state = project(saved, selection, metadata)

        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, state.status)
        assertEquals(0, state.selectedDisplayNameBlockerCount)
        assertEquals(1, state.unresolvedDisplayNameCount)
        assertEquals(setOf("Large Eggs", "Whole Milk"), state.productRows.map { it.title }.toSet())
        assertTrue(state.notice?.contains("other saved choice") == true)
    }

    @Test
    fun selectedIdentifierLikeProductLabelIsRejectedByVerifiedSavedProjector() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val selection = selection(watched = listOf(milk, eggs), usualStore = north)
        val metadata =
            fullMetadata().copy(
                productDisplayNames =
                    mapOf(
                        milk to "product-milk",
                        eggs to "Large Eggs"
                    )
            )

        val state = project(saved, selection, metadata)

        assertEquals(StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertEquals(1, state.selectedDisplayNameBlockerCount)
        assertFalse(state.productRows.any { it.title.contains("product-milk", ignoreCase = true) })
    }

    @Test
    fun extraDisplayMetadataCannotManufactureSelectableRows() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val unknownProduct = ShoppingItemKey("unknown-product")
        val unknownStore = ShoppingStoreKey("unknown-store")
        val metadata =
            fullMetadata().copy(
                productDisplayNames = fullMetadata().productDisplayNames + (unknownProduct to "Ghost Food"),
                storeDisplayNames = fullMetadata().storeDisplayNames + (unknownStore to "Ghost Store")
            )

        val state = project(saved, StapleWatchSavedIdentitySelectionReducer.initial(), metadata)

        assertEquals(setOf("Large Eggs", "Whole Milk"), state.productRows.map { it.title }.toSet())
        assertEquals(listOf("North Market"), state.storeRows.map { it.title })
        assertFalse(state.productRows.any { it.title == "Ghost Food" })
        assertFalse(state.storeRows.any { it.title == "Ghost Store" })
    }

    @Test
    fun deletedSavedSelectionsAreReconciledAwayBeforePresentation() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val staleSelection = selection(watched = listOf(milk, bread), usualStore = west)

        val state = project(saved, staleSelection, fullMetadata())

        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertEquals(1, state.watchedItemCount)
        assertFalse(state.usualStoreSelected)
        assertEquals(0, state.selectedDisplayNameBlockerCount)
        assertTrue(state.productRows.single { it.title == "Whole Milk" }.watched)
    }

    @Test
    fun selectionPresentationOwnsNoEconomicPersistenceNotificationOrAndroidAuthority() {
        val source = source("StapleWatchSavedIdentitySelectionPresentation.kt").readText()

        assertTrue(source.contains("PracticalShoppingSavedExactPreferenceUiProjector.project"))
        assertTrue(source.contains("StapleWatchSavedIdentitySelectionReducer.reconcile"))
        assertTrue(source.contains("StapleWatchSavedIdentitySelectionReducer.identityHandoffOrNull"))
        listOf(
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "SingleStorePlanCandidate",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis",
            "android."
        ).forEach { forbidden ->
            assertFalse("Selection presentation must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun project(
        saved: PracticalShoppingSavedExactPreferenceState,
        selection: StapleWatchSavedIdentitySelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ): StapleWatchSavedSelectionUiState =
        StapleWatchSavedIdentitySelectionUiProjector.project(
            savedState = saved,
            selection = selection,
            metadata = metadata
        )

    private fun selection(
        watched: List<ShoppingItemKey>,
        usualStore: ShoppingStoreKey?
    ): StapleWatchSavedIdentitySelection =
        StapleWatchSavedIdentitySelection(
            watchedItemKeys = watched,
            usualStoreKey = usualStore
        )

    private fun fullMetadata(): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames =
                mapOf(
                    milk to "Whole Milk",
                    eggs to "Large Eggs",
                    bread to "Whole Wheat Bread"
                ),
            storeDisplayNames =
                mapOf(
                    north to "North Market",
                    west to "West Market"
                )
        )

    private fun savedState(
        products: List<ShoppingItemKey>,
        stores: List<ShoppingStoreKey>
    ): PracticalShoppingSavedExactPreferenceState {
        val document =
            PracticalShoppingSavedExactPreferenceDocument(
                schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                productPreferences = products.map(::productPreference),
                storePreferences = stores.map(::storePreference)
            )
        return requireNotNull(PracticalShoppingSavedExactPreferenceStateManager.load(document).state)
    }

    private fun productPreference(
        itemKey: ShoppingItemKey
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = itemKey,
            providerId = EvidenceProviderId("open-food-facts"),
            sourceIdentity = SourceProductIdentity(providerItemId = "product-${itemKey.value}"),
            dataset = null
        )

    private fun storePreference(
        storeKey: ShoppingStoreKey
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = storeKey,
            scope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "wikidata:Q-${storeKey.value}",
                    locationKey = "osm:node:${storeKey.value}-location",
                    commerceChannelKey = "PHYSICAL_STORE"
                ),
            providerId = EvidenceProviderId("openstreetmap"),
            dataset = null
        )

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName from ${System.getProperty("user.dir")}")
    }
}

package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedSelectionPresentationTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun savedChoicesAreSelectableButNeverAutoSelected() {
        val saved = savedState(products = listOf(milk), stores = listOf(north))

        val state =
            project(
                saved = saved,
                selection = UserObservedPriceSavedSelectionReducer.initial(),
                metadata = fullMetadata()
            )

        assertEquals(UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertEquals(listOf("Whole Milk"), state.productRows.map { it.title })
        assertEquals(listOf("North Market"), state.storeRows.map { it.title })
        assertFalse(state.productRows.single().selected)
        assertFalse(state.storeRows.single().selected)
        assertFalse(state.productSelected)
        assertFalse(state.storeSelected)
        assertNull(state.clearSelectionAction)
        assertNull(state.checkPrefillAction)
        assertEquals(
            UserObservedPriceSavedSelectionAction.SelectProduct(milk),
            state.productRows.single().action
        )
        assertEquals(
            UserObservedPriceSavedSelectionAction.SelectStore(north),
            state.storeRows.single().action
        )
    }

    @Test
    fun explicitPairIsReadyOnlyForLaterPrefillCheckEvenWithoutGtin() {
        val saved = savedState(products = listOf(milk), stores = listOf(north))
        assertNull(saved.productPreferences.single().sourceIdentity.gtin)
        val selection = UserObservedPriceSavedSelection(itemKey = milk, storeKey = north)

        val state = project(saved, selection, fullMetadata())

        assertEquals(UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK, state.status)
        assertTrue(state.productSelected)
        assertTrue(state.storeSelected)
        assertTrue(state.productRows.single().selected)
        assertTrue(state.storeRows.single().selected)
        assertEquals(UserObservedPriceSavedSelectionAction.ClearProduct, state.productRows.single().action)
        assertEquals(UserObservedPriceSavedSelectionAction.ClearStore, state.storeRows.single().action)
        assertEquals(UserObservedPriceSavedSelectionAction.ClearSelection, state.clearSelectionAction)
        assertEquals(UserObservedPriceSavedPrefillCheckUiAction.Request, state.checkPrefillAction)
        assertTrue(state.guidance.contains("checked", ignoreCase = true))
        assertFalse(state.guidance.contains("valid", ignoreCase = true))
        assertFalse(state.guidance.contains("confirmed", ignoreCase = true))
    }

    @Test
    fun selectedProductWithoutSafeDisplayNameBlocksPrefillCheckMarker() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val selection = UserObservedPriceSavedSelection(itemKey = milk, storeKey = north)
        val metadata =
            fullMetadata().copy(
                productDisplayNames = mapOf(eggs to "Large Eggs")
            )

        val state = project(saved, selection, metadata)

        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE,
            state.status
        )
        assertEquals(1, state.selectedDisplayNameBlockerCount)
        assertEquals(1, state.unresolvedDisplayNameCount)
        assertTrue(state.productSelected)
        assertEquals(listOf("Large Eggs"), state.productRows.map { it.title })
        assertNull(state.checkPrefillAction)
        assertTrue(state.notice?.contains("selected saved choice") == true)
        assertFalse(state.productRows.any { it.title.contains(milk.value, ignoreCase = true) })
    }

    @Test
    fun selectedStoreWithoutSafeDisplayNameBlocksPrefillCheckMarker() {
        val saved = savedState(products = listOf(milk), stores = listOf(north, west))
        val selection = UserObservedPriceSavedSelection(itemKey = milk, storeKey = north)
        val metadata =
            fullMetadata().copy(
                storeDisplayNames = mapOf(west to "West Market")
            )

        val state = project(saved, selection, metadata)

        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE,
            state.status
        )
        assertEquals(1, state.selectedDisplayNameBlockerCount)
        assertTrue(state.storeSelected)
        assertEquals(listOf("West Market"), state.storeRows.map { it.title })
        assertNull(state.checkPrefillAction)
        assertTrue(state.notice?.contains("selected saved choice") == true)
    }

    @Test
    fun unresolvedUnselectedChoicesAreHiddenWithoutBlockingExplicitPair() {
        val saved =
            savedState(
                products = listOf(milk, bread),
                stores = listOf(north, west)
            )
        val selection = UserObservedPriceSavedSelection(itemKey = milk, storeKey = north)
        val metadata =
            PracticalShoppingSavedExactPreferenceDisplayMetadata(
                productDisplayNames = mapOf(milk to "Whole Milk"),
                storeDisplayNames = mapOf(north to "North Market")
            )

        val state = project(saved, selection, metadata)

        assertEquals(UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK, state.status)
        assertEquals(0, state.selectedDisplayNameBlockerCount)
        assertEquals(2, state.unresolvedDisplayNameCount)
        assertEquals(listOf("Whole Milk"), state.productRows.map { it.title })
        assertEquals(listOf("North Market"), state.storeRows.map { it.title })
        assertNotNull(state.checkPrefillAction)
        assertTrue(state.notice?.contains("other saved choices") == true)
    }

    @Test
    fun identifierLikeSelectedLabelIsRejectedByVerifiedSavedProjector() {
        val saved = savedState(products = listOf(milk), stores = listOf(north))
        val selection = UserObservedPriceSavedSelection(itemKey = milk, storeKey = north)
        val metadata =
            fullMetadata().copy(
                productDisplayNames = mapOf(milk to "product-milk")
            )

        val state = project(saved, selection, metadata)

        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE,
            state.status
        )
        assertTrue(state.productRows.isEmpty())
        assertNull(state.productSectionTitle)
        assertNull(state.checkPrefillAction)
        assertEquals(1, state.selectedDisplayNameBlockerCount)
    }

    @Test
    fun staleSelectionsAreReconciledBeforeProjectionAndNeverReplacedAutomatically() {
        val saved = savedState(products = listOf(milk), stores = listOf(north))
        val stale = UserObservedPriceSavedSelection(itemKey = eggs, storeKey = west)

        val state = project(saved, stale, fullMetadata())

        assertEquals(UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertFalse(state.productSelected)
        assertFalse(state.storeSelected)
        assertFalse(state.productRows.single().selected)
        assertFalse(state.storeRows.single().selected)
        assertNull(state.checkPrefillAction)
        assertNull(state.clearSelectionAction)
    }

    @Test
    fun presenterForwardsExactlyThePureProjectedImmutableState() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        val selection = UserObservedPriceSavedSelection(itemKey = eggs, storeKey = north)
        val metadata = fullMetadata()
        val expected = project(saved, selection, metadata)
        var rendered: UserObservedPriceSavedSelectionUiState? = null
        val presenter =
            UserObservedPriceSavedSelectionSurfacePresenter { state -> rendered = state }

        presenter.render(saved, selection, metadata)

        assertEquals(expected, rendered)
    }

    @Test
    fun presentationOwnsNoPrefillExecutionDraftProofTimeNavigationOrAndroidAuthority() {
        val source = source("UserObservedPriceSavedSelectionPresentation.kt").readText()

        assertTrue(source.contains("PracticalShoppingSavedExactPreferenceUiProjector.project"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionReducer.reconcile"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionReducer.selectedPairOrNull"))
        assertFalse(source.contains("UserObservedPriceSavedPrefillGate.request("))
        assertFalse(source.contains("UserObservedPriceSavedPrefillHandoffGate.request("))
        listOf(
            "GtinValidation",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserProvidedPriceProofArtifact",
            "ByteArray",
            "System.currentTimeMillis",
            "UUID",
            "AppShellIntent",
            "MainActivity",
            "EvidenceBackedUnitValuePolicy",
            "CURRENT_PRICE",
            "android.",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Selection presentation must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun project(
        saved: PracticalShoppingSavedExactPreferenceState,
        selection: UserObservedPriceSavedSelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ): UserObservedPriceSavedSelectionUiState =
        UserObservedPriceSavedSelectionUiProjector.project(
            savedState = saved,
            selection = selection,
            metadata = metadata
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
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}

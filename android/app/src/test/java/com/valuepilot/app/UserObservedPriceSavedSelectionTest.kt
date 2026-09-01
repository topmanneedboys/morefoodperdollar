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

class UserObservedPriceSavedSelectionTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun `saved choices never auto select even when only one pair exists`() {
        val saved = savedState(products = listOf(milk), stores = listOf(north))

        val selection = UserObservedPriceSavedSelectionReducer.initial()

        assertNull(selection.itemKey)
        assertNull(selection.storeKey)
        assertNull(UserObservedPriceSavedSelectionReducer.selectedPairOrNull(selection, saved))
    }

    @Test
    fun `product and store must each be explicitly selected before pair exists`() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north, west))
        var selection = UserObservedPriceSavedSelectionReducer.initial()

        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            ).state
        assertEquals(milk, selection.itemKey)
        assertNull(selection.storeKey)
        assertNull(UserObservedPriceSavedSelectionReducer.selectedPairOrNull(selection, saved))

        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.SelectStore(north)
            ).state

        val pair =
            requireNotNull(
                UserObservedPriceSavedSelectionReducer.selectedPairOrNull(selection, saved)
            )
        assertEquals(milk, pair.itemKey)
        assertEquals(north, pair.storeKey)
    }

    @Test
    fun `selecting another product replaces only product side of pair`() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north))
        var selection = selected(saved, milk, north)

        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.SelectProduct(eggs)
            ).state

        assertEquals(eggs, selection.itemKey)
        assertEquals(north, selection.storeKey)
        assertEquals(
            UserObservedPriceSavedSelectionPair(eggs, north),
            UserObservedPriceSavedSelectionReducer.selectedPairOrNull(selection, saved)
        )
    }

    @Test
    fun `selecting another store replaces only store side of pair`() {
        val saved = savedState(products = listOf(milk), stores = listOf(north, west))
        var selection = selected(saved, milk, north)

        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.SelectStore(west)
            ).state

        assertEquals(milk, selection.itemKey)
        assertEquals(west, selection.storeKey)
        assertEquals(
            UserObservedPriceSavedSelectionPair(milk, west),
            UserObservedPriceSavedSelectionReducer.selectedPairOrNull(selection, saved)
        )
    }

    @Test
    fun `selecting unsaved product fails closed without changing valid selection`() {
        val saved = savedState(products = listOf(milk), stores = listOf(north))
        val previous = selected(saved, milk, north)
        val unknown = ShoppingItemKey("unknown-product")

        val transition =
            reduce(
                previous,
                saved,
                UserObservedPriceSavedSelectionAction.SelectProduct(unknown)
            )

        assertFalse(transition.accepted)
        assertEquals(UserObservedPriceSavedSelectionIssue.PRODUCT_NOT_SAVED, transition.issue)
        assertEquals(previous, transition.state)
    }

    @Test
    fun `selecting unsaved store fails closed without changing valid selection`() {
        val saved = savedState(products = listOf(milk), stores = listOf(north))
        val previous = selected(saved, milk, north)
        val unknown = ShoppingStoreKey("unknown-store")

        val transition =
            reduce(
                previous,
                saved,
                UserObservedPriceSavedSelectionAction.SelectStore(unknown)
            )

        assertFalse(transition.accepted)
        assertEquals(UserObservedPriceSavedSelectionIssue.STORE_NOT_SAVED, transition.issue)
        assertEquals(previous, transition.state)
    }

    @Test
    fun `reconcile clears deleted product but keeps still saved store and never selects additions`() {
        val first = savedState(products = listOf(milk), stores = listOf(north))
        val previous = selected(first, milk, north)
        val changed = savedState(products = listOf(eggs, bread), stores = listOf(north, west))

        val reconciled = UserObservedPriceSavedSelectionReducer.reconcile(previous, changed)

        assertNull(reconciled.itemKey)
        assertEquals(north, reconciled.storeKey)
        assertFalse(reconciled.itemKey == eggs || reconciled.itemKey == bread)
        assertFalse(reconciled.storeKey == west)
        assertNull(UserObservedPriceSavedSelectionReducer.selectedPairOrNull(reconciled, changed))
    }

    @Test
    fun `reconcile clears deleted store but keeps still saved product`() {
        val first = savedState(products = listOf(milk), stores = listOf(north))
        val previous = selected(first, milk, north)
        val changed = savedState(products = listOf(milk, eggs), stores = listOf(west))

        val reconciled = UserObservedPriceSavedSelectionReducer.reconcile(previous, changed)

        assertEquals(milk, reconciled.itemKey)
        assertNull(reconciled.storeKey)
        assertNull(UserObservedPriceSavedSelectionReducer.selectedPairOrNull(reconciled, changed))
    }

    @Test
    fun `clear actions are selection only and never mutate saved state`() {
        val saved = savedState(products = listOf(milk, eggs), stores = listOf(north, west))
        var selection = selected(saved, milk, north)

        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.ClearProduct
            ).state
        assertNull(selection.itemKey)
        assertEquals(north, selection.storeKey)

        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.SelectProduct(eggs)
            ).state
        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.ClearStore
            ).state
        assertEquals(eggs, selection.itemKey)
        assertNull(selection.storeKey)

        selection = selected(saved, milk, west)
        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.ClearSelection
            ).state
        assertEquals(UserObservedPriceSavedSelection.initial(), selection)
        assertEquals(listOf(milk, eggs), saved.productPreferences.map { it.itemKey })
        assertEquals(listOf(north, west), saved.storePreferences.map { it.storeKey })
    }

    @Test
    fun `provider id only product may be selected but gains no gtin authority here`() {
        val saved =
            savedState(
                products = listOf(milk),
                stores = listOf(north),
                productIdentity = SourceProductIdentity(providerItemId = "provider-item-1")
            )

        val pair =
            UserObservedPriceSavedSelectionReducer.selectedPairOrNull(
                selected(saved, milk, north),
                saved
            )

        assertEquals(UserObservedPriceSavedSelectionPair(milk, north), pair)
        assertNull(saved.productFor(milk)?.sourceIdentity?.gtin)
    }

    @Test
    fun `selection boundary stays independent of prefill draft proof price and navigation layers`() {
        val source = source("UserObservedPriceSavedSelection.kt").readText()
        val imports = source.lineSequence().filter { it.startsWith("import ") }.toList()

        assertEquals(
            listOf(
                "import com.valuepilot.core.ShoppingItemKey",
                "import com.valuepilot.core.ShoppingStoreKey"
            ),
            imports
        )
        listOf(
            "UserObservedPriceSavedPrefillGate",
            "GtinValidation",
            "PracticalShoppingSavedExactPreferenceUiProjector",
            "UserObservedPriceConfirmationDraft",
            "UserConfirmedObservedPrice",
            "UserProvidedPriceProofArtifact",
            "UserObservedPriceUnitValue",
            "ProductionCurrentPrice",
            "AppShell",
            "MainActivity",
            "System.currentTimeMillis",
            "UUID"
        ).forEach { forbidden ->
            assertFalse("Saved selection must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun selected(
        saved: PracticalShoppingSavedExactPreferenceState,
        itemKey: ShoppingItemKey,
        storeKey: ShoppingStoreKey
    ): UserObservedPriceSavedSelection {
        var selection = UserObservedPriceSavedSelectionReducer.initial()
        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.SelectProduct(itemKey)
            ).state
        selection =
            reduce(
                selection,
                saved,
                UserObservedPriceSavedSelectionAction.SelectStore(storeKey)
            ).state
        return selection
    }

    private fun reduce(
        previous: UserObservedPriceSavedSelection,
        saved: PracticalShoppingSavedExactPreferenceState,
        action: UserObservedPriceSavedSelectionAction
    ): UserObservedPriceSavedSelectionTransition =
        UserObservedPriceSavedSelectionReducer.reduce(previous, saved, action)

    private fun savedState(
        products: List<ShoppingItemKey>,
        stores: List<ShoppingStoreKey>,
        productIdentity: SourceProductIdentity = SourceProductIdentity(gtin = "036000291452")
    ): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences =
                products.mapIndexed { index, key ->
                    PracticalShoppingSavedExactProductPreference(
                        itemKey = key,
                        providerId = EvidenceProviderId("test-provider-$index"),
                        sourceIdentity =
                            if (index == 0) {
                                productIdentity
                            } else {
                                SourceProductIdentity(providerItemId = "provider-item-$index")
                            }
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

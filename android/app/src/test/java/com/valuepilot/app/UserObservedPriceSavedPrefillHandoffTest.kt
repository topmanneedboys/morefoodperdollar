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

class UserObservedPriceSavedPrefillHandoffTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")
    private val northScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-north",
            locationKey = "location-north",
            commerceChannelKey = "PHYSICAL_STORE"
        )
    private val westScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-west",
            locationKey = "location-west",
            commerceChannelKey = "PHYSICAL_STORE"
        )

    @Test
    fun `saved choices never auto-handoff even when only one product and store exist`() {
        val snapshot =
            snapshot(
                products = listOf(product(milk, SourceProductIdentity(gtin = "036000291452"))),
                stores = listOf(store(north, northScope)),
                metadata = metadata(
                    productNames = mapOf(milk to "Whole Milk"),
                    storeNames = mapOf(north to "North Market")
                )
            )

        val attempt =
            UserObservedPriceSavedPrefillHandoffGate.request(
                selection = UserObservedPriceSavedSelectionReducer.initial(),
                snapshot = snapshot
            )

        assertFalse(attempt.accepted)
        assertNull(attempt.prefill)
        assertEquals(UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY, attempt.issue)
        assertNull(attempt.prefillIssue)
    }

    @Test
    fun `both explicit selections return the exact existing prefill output unchanged`() {
        val snapshot = snapshot()
        val selection = explicitSelection(snapshot, milk, north)
        val expected =
            requireNotNull(
                UserObservedPriceSavedPrefillGate.request(
                    itemKey = milk,
                    storeKey = north,
                    snapshot = snapshot
                ).prefill
            )

        val attempt = UserObservedPriceSavedPrefillHandoffGate.request(selection, snapshot)

        assertTrue(attempt.accepted)
        assertEquals(expected, attempt.prefill)
        assertNull(attempt.issue)
        assertNull(attempt.prefillIssue)
    }

    @Test
    fun `partial explicit selection is not ready`() {
        val snapshot = snapshot()
        val productOnly =
            UserObservedPriceSavedSelectionReducer.reduce(
                previous = UserObservedPriceSavedSelectionReducer.initial(),
                savedState = snapshot.exactState,
                action = UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            ).state

        val attempt = UserObservedPriceSavedPrefillHandoffGate.request(productOnly, snapshot)

        assertFalse(attempt.accepted)
        assertEquals(UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY, attempt.issue)
        assertNull(attempt.prefillIssue)
    }

    @Test
    fun `deleted selected product is reconciled away before prefill gate`() {
        val original = snapshot()
        val selection = explicitSelection(original, milk, north)
        val changed =
            snapshot(
                products = listOf(product(eggs, SourceProductIdentity(gtin = "4006381333931"))),
                metadata = metadata(
                    productNames = mapOf(eggs to "Large Eggs")
                )
            )

        val attempt = UserObservedPriceSavedPrefillHandoffGate.request(selection, changed)

        assertFalse(attempt.accepted)
        assertEquals(UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY, attempt.issue)
        assertNull(attempt.prefillIssue)
    }

    @Test
    fun `deleted selected store is reconciled away before prefill gate`() {
        val original = snapshot()
        val selection = explicitSelection(original, milk, north)
        val changed =
            snapshot(
                stores = listOf(store(west, westScope)),
                metadata = metadata(
                    storeNames = mapOf(west to "West Market")
                )
            )

        val attempt = UserObservedPriceSavedPrefillHandoffGate.request(selection, changed)

        assertFalse(attempt.accepted)
        assertEquals(UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY, attempt.issue)
        assertNull(attempt.prefillIssue)
    }

    @Test
    fun `provider-item-only selected product preserves downstream gtin unavailable blocker`() {
        val snapshot =
            snapshot(
                products =
                    listOf(
                        product(
                            milk,
                            SourceProductIdentity(providerItemId = "provider-item-1")
                        ),
                        product(eggs, SourceProductIdentity(gtin = "4006381333931"))
                    )
            )
        val selection = explicitSelection(snapshot, milk, north)

        val attempt = UserObservedPriceSavedPrefillHandoffGate.request(selection, snapshot)

        assertFalse(attempt.accepted)
        assertNull(attempt.issue)
        assertEquals(UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_UNAVAILABLE, attempt.prefillIssue)
    }

    @Test
    fun `invalid selected gtin preserves downstream invalid blocker`() {
        val snapshot =
            snapshot(
                products =
                    listOf(
                        product(
                            milk,
                            SourceProductIdentity(
                                providerItemId = "provider-item-1",
                                gtin = "036000291453"
                            )
                        ),
                        product(eggs, SourceProductIdentity(gtin = "4006381333931"))
                    )
            )
        val selection = explicitSelection(snapshot, milk, north)

        val attempt = UserObservedPriceSavedPrefillHandoffGate.request(selection, snapshot)

        assertFalse(attempt.accepted)
        assertNull(attempt.issue)
        assertEquals(UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_INVALID, attempt.prefillIssue)
    }

    @Test
    fun `unsafe selected display label preserves downstream display blocker`() {
        val snapshot =
            snapshot(
                metadata = metadata(
                    productNames = mapOf(milk to "036000291452", eggs to "Large Eggs")
                )
            )
        val selection = explicitSelection(snapshot, milk, north)

        val attempt = UserObservedPriceSavedPrefillHandoffGate.request(selection, snapshot)

        assertFalse(attempt.accepted)
        assertNull(attempt.issue)
        assertEquals(
            UserObservedPriceSavedPrefillIssue.PRODUCT_DISPLAY_NAME_UNAVAILABLE,
            attempt.prefillIssue
        )
    }

    @Test
    fun `wrapper owns no gtin display draft proof price time evidence ranking ui or route authority`() {
        val source = source("UserObservedPriceSavedPrefillHandoff.kt").readText()

        assertTrue(source.contains("UserObservedPriceSavedSelectionReducer.selectedPairOrNull"))
        assertTrue(source.contains("UserObservedPriceSavedPrefillGate.request"))
        listOf(
            "GtinValidation",
            "PracticalShoppingSavedExactPreferenceUiProjector",
            ".rawGtin",
            ".productName",
            ".storeScope",
            ".storeDisplayName",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserConfirmedObservedPrice",
            "UserProvidedPriceProof",
            "UserObservedPriceUnitValue",
            "UserProofBackedObservedPrice",
            "ProductPackageQuantity",
            "EvidenceFreshness",
            "ProductionCurrentPrice",
            "Money",
            "ByteArray",
            "System.currentTimeMillis",
            "UUID",
            "AppShell",
            "MainActivity",
            "android.",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Saved prefill handoff must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun explicitSelection(
        snapshot: PracticalShoppingSavedValidatedSnapshot,
        itemKey: ShoppingItemKey,
        storeKey: ShoppingStoreKey
    ): UserObservedPriceSavedSelection {
        val withProduct =
            UserObservedPriceSavedSelectionReducer.reduce(
                previous = UserObservedPriceSavedSelectionReducer.initial(),
                savedState = snapshot.exactState,
                action = UserObservedPriceSavedSelectionAction.SelectProduct(itemKey)
            )
        assertTrue(withProduct.accepted)

        val withStore =
            UserObservedPriceSavedSelectionReducer.reduce(
                previous = withProduct.state,
                savedState = snapshot.exactState,
                action = UserObservedPriceSavedSelectionAction.SelectStore(storeKey)
            )
        assertTrue(withStore.accepted)
        return withStore.state
    }

    private fun snapshot(
        products: List<PracticalShoppingSavedExactProductPreference> =
            listOf(
                product(milk, SourceProductIdentity(gtin = "036000291452")),
                product(eggs, SourceProductIdentity(gtin = "4006381333931"))
            ),
        stores: List<PracticalShoppingSavedExactStorePreference> =
            listOf(store(north, northScope), store(west, westScope)),
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata = metadata()
    ): PracticalShoppingSavedValidatedSnapshot =
        PracticalShoppingSavedValidatedSnapshot(
            exactState =
                PracticalShoppingSavedExactPreferenceState(
                    productPreferences = products,
                    storePreferences = stores
                ),
            displayMetadata = metadata
        )

    private fun product(
        itemKey: ShoppingItemKey,
        identity: SourceProductIdentity
    ): PracticalShoppingSavedExactProductPreference =
        PracticalShoppingSavedExactProductPreference(
            itemKey = itemKey,
            providerId = EvidenceProviderId("test-provider"),
            sourceIdentity = identity
        )

    private fun store(
        storeKey: ShoppingStoreKey,
        scope: PracticalShoppingStoreIdentityScope
    ): PracticalShoppingSavedExactStorePreference =
        PracticalShoppingSavedExactStorePreference(
            storeKey = storeKey,
            scope = scope
        )

    private fun metadata(
        productNames: Map<ShoppingItemKey, String> =
            mapOf(milk to "Whole Milk", eggs to "Large Eggs"),
        storeNames: Map<ShoppingStoreKey, String> =
            mapOf(north to "North Market", west to "West Market")
    ): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames = productNames,
            storeDisplayNames = storeNames
        )

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

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

class UserObservedPriceSavedSelectionCompositionCoordinatorTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun `visible route waits for validated snapshot before creating selection session`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectProduct(milk))

        assertTrue(createdSnapshots.isEmpty())
        assertTrue(rendered.isEmpty())

        val accepted = snapshot()
        coordinator.onSnapshot(accepted)

        assertEquals(listOf(accepted), createdSnapshots)
        assertEquals(1, rendered.size)
        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION,
            rendered.single().status
        )
    }

    @Test
    fun `hidden validated snapshot is cached until route entry`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
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
    fun `typed surface actions update route-local selection and rerender immutable state`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)

        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectProduct(milk))
        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectStore(north))

        assertEquals(1, createdSnapshots.size)
        assertEquals(3, rendered.size)
        val state = rendered.last()
        assertEquals(UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK, state.status)
        assertTrue(state.productSelected)
        assertTrue(state.storeSelected)
        assertTrue(state.productRows.first { it.title == "Whole Milk" }.selected)
        assertTrue(state.storeRows.first { it.title == "North Market" }.selected)
    }

    @Test
    fun `newer validated snapshot reconciles existing visible selection without a new session`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectProduct(milk))
        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectStore(north))

        coordinator.onSnapshot(
            snapshot(
                products = listOf(eggs),
                stores = listOf(north, west)
            )
        )

        assertEquals(1, createdSnapshots.size)
        val state = rendered.last()
        assertEquals(UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertFalse(state.productSelected)
        assertTrue(state.storeSelected)
        assertFalse(state.productRows.any { it.title == "Whole Milk" })
        assertTrue(state.storeRows.first { it.title == "North Market" }.selected)
    }

    @Test
    fun `hide show reuses memory-only session and hidden actions are ignored`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectProduct(milk))
        coordinator.onRouteVisibilityChanged(false)
        val hiddenRenderCount = rendered.size

        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectStore(north))
        assertEquals(hiddenRenderCount, rendered.size)

        coordinator.onRouteVisibilityChanged(true)

        assertEquals(1, createdSnapshots.size)
        val state = rendered.last()
        assertTrue(state.productSelected)
        assertFalse(state.storeSelected)
        assertTrue(state.productRows.first { it.title == "Whole Milk" }.selected)
    }

    @Test
    fun `close drops session and snapshot and ignores later inputs`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val createdSnapshots = mutableListOf<PracticalShoppingSavedValidatedSnapshot>()
        val coordinator = coordinator(rendered, createdSnapshots)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        val beforeClose = rendered.size

        coordinator.close()
        coordinator.close()
        coordinator.onSnapshot(snapshot(products = listOf(eggs), stores = listOf(west)))
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(UserObservedPriceSavedSelectionAction.SelectProduct(eggs))

        assertTrue(coordinator.isClosed())
        assertEquals(1, createdSnapshots.size)
        assertEquals(beforeClose, rendered.size)
    }

    @Test
    fun `selection composition owns no prefill draft evidence ranking storage navigation or android authority`() {
        val source = source("UserObservedPriceSavedSelectionCompositionCoordinator.kt").readText()

        assertTrue(source.contains("PracticalShoppingSavedValidatedSnapshotObserver"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionRouteSession"))
        assertTrue(source.contains("session?.onSelectionAction(action)"))
        assertFalse(source.contains("UserObservedPriceSavedSelectionUiStatus"))
        listOf(
            "requestPrefillOrNull",
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedPrefillGate",
            "UserObservedPriceSavedPrefillCheckUiAction",
            "PracticalShoppingSavedAndroidSession",
            "PracticalShoppingSavedExactPreferenceLocalStore",
            "PracticalShoppingSavedDisplayMetadataLocalStore",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationExecution",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProof",
            "UserConfirmedObservedPrice",
            "UserProofBackedObservedPrice",
            "UserObservedPriceUnitValue",
            "ProductPackageQuantity",
            "EvidenceFreshness",
            "ProductionCurrentPrice",
            "Money",
            "ByteArray",
            "System.currentTimeMillis",
            "UUID",
            "AppShell",
            "MainActivity",
            "Intent",
            "startActivity",
            "android.",
            "java.net",
            "java.io"
        ).forEach { forbidden ->
            assertFalse(
                "Observed-price Saved selection composition must not own $forbidden",
                source.contains(forbidden)
            )
        }
    }

    private fun coordinator(
        rendered: MutableList<UserObservedPriceSavedSelectionUiState>,
        createdSnapshots: MutableList<PracticalShoppingSavedValidatedSnapshot>
    ): UserObservedPriceSavedSelectionCompositionCoordinator =
        UserObservedPriceSavedSelectionCompositionCoordinator { acceptedSnapshot ->
            createdSnapshots += acceptedSnapshot
            UserObservedPriceSavedSelectionRouteSession(
                initialSnapshot = acceptedSnapshot,
                presenter =
                    UserObservedPriceSavedSelectionSurfacePresenter { state ->
                        rendered += state
                    }
            )
        }

    private fun snapshot(
        products: List<ShoppingItemKey> = listOf(milk, eggs),
        stores: List<ShoppingStoreKey> = listOf(north, west)
    ): PracticalShoppingSavedValidatedSnapshot {
        val exactState =
            PracticalShoppingSavedExactPreferenceState(
                productPreferences =
                    products.mapIndexed { index, itemKey ->
                        PracticalShoppingSavedExactProductPreference(
                            itemKey = itemKey,
                            providerId = EvidenceProviderId("test-provider"),
                            sourceIdentity =
                                SourceProductIdentity(
                                    gtin = if (index == 0) "036000291452" else "4006381333931"
                                )
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
        return PracticalShoppingSavedValidatedSnapshot(
            exactState = exactState,
            displayMetadata =
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    productDisplayNames =
                        products.associateWith { itemKey ->
                            when (itemKey) {
                                milk -> "Whole Milk"
                                eggs -> "Large Eggs"
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
        )
    }

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

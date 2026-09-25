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

class UserObservedPriceSavedSelectionRouteSessionTest {

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
    fun `route entry never auto selects and renders once even when only one saved pair exists`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session =
            session(
                initialSnapshot =
                    snapshot(
                        products = listOf(product(milk, SourceProductIdentity(gtin = "036000291452"))),
                        stores = listOf(store(north, northScope)),
                        metadata = metadata(
                            productNames = mapOf(milk to "Whole Milk"),
                            storeNames = mapOf(north to "North Market")
                        )
                    ),
                rendered = rendered
            )

        assertFalse(session.isVisible())
        assertNull(session.currentSelectionOrNull())
        assertNull(session.requestPrefillOrNull())
        assertTrue(rendered.isEmpty())

        session.onRouteVisibilityChanged(true)
        session.onRouteVisibilityChanged(true)

        assertTrue(session.isVisible())
        assertEquals(1, rendered.size)
        assertEquals(UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION, rendered.single().status)
        assertEquals(
            UserObservedPriceSavedSelection.initial(),
            session.currentSelectionOrNull()
        )
        val attempt = requireNotNull(session.requestPrefillOrNull())
        assertFalse(attempt.accepted)
        assertEquals(UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY, attempt.issue)
        assertNull(attempt.prefillIssue)
    }

    @Test
    fun `visible explicit pair renders each action and returns exact verified handoff attempt unchanged`() {
        val savedSnapshot = snapshot()
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(savedSnapshot, rendered)
        session.onRouteVisibilityChanged(true)

        val productTransition =
            requireNotNull(
                session.onSelectionAction(
                    UserObservedPriceSavedSelectionAction.SelectProduct(milk)
                )
            )
        assertTrue(productTransition.accepted)
        assertEquals(UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION, rendered.last().status)

        val storeTransition =
            requireNotNull(
                session.onSelectionAction(
                    UserObservedPriceSavedSelectionAction.SelectStore(north)
                )
            )
        assertTrue(storeTransition.accepted)
        assertEquals(3, rendered.size)
        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK,
            rendered.last().status
        )

        val expected =
            UserObservedPriceSavedPrefillHandoffGate.request(
                selection = storeTransition.state,
                snapshot = savedSnapshot
            )
        val actual = requireNotNull(session.requestPrefillOrNull())

        assertEquals(expected, actual)
        assertTrue(actual.accepted)
    }

    @Test
    fun `hidden route ignores actions and rendering while hide show preserves temporary selection`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            )
        )

        session.onRouteVisibilityChanged(false)
        val renderCountWhileHidden = rendered.size

        assertFalse(session.isVisible())
        assertNull(session.currentSelectionOrNull())
        assertNull(session.requestPrefillOrNull())
        assertNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectStore(north)
            )
        )
        assertEquals(renderCountWhileHidden, rendered.size)

        session.onRouteVisibilityChanged(true)

        val restored = requireNotNull(session.currentSelectionOrNull())
        assertEquals(renderCountWhileHidden + 1, rendered.size)
        assertEquals(milk, restored.itemKey)
        assertNull(restored.storeKey)
        assertTrue(rendered.last().productSelected)
        assertFalse(rendered.last().storeSelected)
        assertEquals(
            UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY,
            requireNotNull(session.requestPrefillOrNull()).issue
        )
    }

    @Test
    fun `visible snapshot change reconciles removed selection and renders without selecting additions`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            )
        )
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectStore(north)
            )
        )
        val beforeSnapshot = rendered.size

        session.onSavedSnapshotChanged(
            snapshot(
                products = listOf(product(eggs, SourceProductIdentity(gtin = "4006381333931"))),
                stores = listOf(store(north, northScope), store(west, westScope)),
                metadata = metadata(
                    productNames = mapOf(eggs to "Large Eggs")
                )
            )
        )

        val reconciled = requireNotNull(session.currentSelectionOrNull())
        assertEquals(beforeSnapshot + 1, rendered.size)
        assertNull(reconciled.itemKey)
        assertEquals(north, reconciled.storeKey)
        assertFalse(reconciled.itemKey == eggs)
        assertFalse(reconciled.storeKey == west)
        assertFalse(rendered.last().productSelected)
        assertTrue(rendered.last().storeSelected)
        assertEquals(
            UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY,
            requireNotNull(session.requestPrefillOrNull()).issue
        )
    }

    @Test
    fun `hidden snapshot change reconciles without rendering until route reentry`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            )
        )
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectStore(north)
            )
        )
        session.onRouteVisibilityChanged(false)
        val renderCountBeforeSnapshot = rendered.size

        session.onSavedSnapshotChanged(
            snapshot(
                products = listOf(product(eggs, SourceProductIdentity(gtin = "4006381333931"))),
                stores = listOf(store(west, westScope)),
                metadata = metadata(
                    productNames = mapOf(eggs to "Large Eggs"),
                    storeNames = mapOf(west to "West Market")
                )
            )
        )

        assertNull(session.currentSelectionOrNull())
        assertEquals(renderCountBeforeSnapshot, rendered.size)

        session.onRouteVisibilityChanged(true)

        assertEquals(renderCountBeforeSnapshot + 1, rendered.size)
        assertEquals(
            UserObservedPriceSavedSelection.initial(),
            session.currentSelectionOrNull()
        )
        assertEquals(UserObservedPriceSavedSelectionUiStatus.NEEDS_SELECTION, rendered.last().status)
        assertEquals(
            UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY,
            requireNotNull(session.requestPrefillOrNull()).issue
        )
    }

    @Test
    fun `stale visible selection action preserves typed reducer rejection and reprojects safe state`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            )
        )
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectStore(north)
            )
        )

        session.onSavedSnapshotChanged(
            snapshot(
                products = listOf(product(eggs, SourceProductIdentity(gtin = "4006381333931"))),
                metadata = metadata(productNames = mapOf(eggs to "Large Eggs"))
            )
        )
        val beforeStaleAction = rendered.size

        val transition =
            requireNotNull(
                session.onSelectionAction(
                    UserObservedPriceSavedSelectionAction.SelectProduct(milk)
                )
            )

        assertFalse(transition.accepted)
        assertEquals(UserObservedPriceSavedSelectionIssue.PRODUCT_NOT_SAVED, transition.issue)
        assertNull(transition.state.itemKey)
        assertEquals(north, transition.state.storeKey)
        assertEquals(beforeStaleAction + 1, rendered.size)
        assertFalse(rendered.last().productSelected)
        assertTrue(rendered.last().storeSelected)
    }

    @Test
    fun `presentation readiness stays weaker than downstream prefill GTIN authority`() {
        val savedSnapshot =
            snapshot(
                products =
                    listOf(
                        product(
                            milk,
                            SourceProductIdentity(providerItemId = "provider-item-1")
                        )
                    ),
                stores = listOf(store(north, northScope)),
                metadata = metadata(
                    productNames = mapOf(milk to "Whole Milk"),
                    storeNames = mapOf(north to "North Market")
                )
            )
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(savedSnapshot, rendered)
        session.onRouteVisibilityChanged(true)
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            )
        )
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectStore(north)
            )
        )

        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK,
            rendered.last().status
        )
        assertEquals(UserObservedPriceSavedPrefillCheckUiAction.Request, rendered.last().checkPrefillAction)

        val expected =
            UserObservedPriceSavedPrefillHandoffGate.request(
                selection = requireNotNull(session.currentSelectionOrNull()),
                snapshot = savedSnapshot
            )
        val actual = requireNotNull(session.requestPrefillOrNull())

        assertEquals(expected, actual)
        assertFalse(actual.accepted)
        assertNull(actual.issue)
        assertEquals(UserObservedPriceSavedPrefillIssue.PRODUCT_GTIN_UNAVAILABLE, actual.prefillIssue)
    }

    @Test
    fun `display metadata changes can block and unblock presentation without changing selection`() {
        val saved = snapshot()
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(saved, rendered)
        session.onRouteVisibilityChanged(true)
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            )
        )
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectStore(north)
            )
        )
        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK,
            rendered.last().status
        )

        session.onSavedSnapshotChanged(
            snapshot(
                products = saved.exactState.productPreferences,
                stores = saved.exactState.storePreferences,
                metadata = metadata(
                    productNames = mapOf(eggs to "Large Eggs"),
                    storeNames = mapOf(north to "North Market", west to "West Market")
                )
            )
        )

        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE,
            rendered.last().status
        )
        assertTrue(rendered.last().productSelected)
        assertTrue(rendered.last().storeSelected)
        assertEquals(
            UserObservedPriceSavedPrefillIssue.PRODUCT_DISPLAY_NAME_UNAVAILABLE,
            requireNotNull(session.requestPrefillOrNull()).prefillIssue
        )

        session.onSavedSnapshotChanged(saved)

        assertEquals(
            UserObservedPriceSavedSelectionUiStatus.READY_FOR_PREFILL_CHECK,
            rendered.last().status
        )
        assertTrue(rendered.last().productSelected)
        assertTrue(rendered.last().storeSelected)
    }

    @Test
    fun `close discards selection and blocks later route snapshot action prefill and rendering`() {
        val rendered = mutableListOf<UserObservedPriceSavedSelectionUiState>()
        val session = session(rendered = rendered)
        session.onRouteVisibilityChanged(true)
        requireNotNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectProduct(milk)
            )
        )
        val beforeClose = rendered.size

        session.close()
        session.close()
        session.onSavedSnapshotChanged(
            snapshot(
                products = listOf(product(eggs, SourceProductIdentity(gtin = "4006381333931"))),
                stores = listOf(store(west, westScope)),
                metadata = metadata(
                    productNames = mapOf(eggs to "Large Eggs"),
                    storeNames = mapOf(west to "West Market")
                )
            )
        )
        session.onRouteVisibilityChanged(true)

        assertTrue(session.isClosed())
        assertFalse(session.isVisible())
        assertNull(session.currentSelectionOrNull())
        assertNull(session.requestPrefillOrNull())
        assertNull(
            session.onSelectionAction(
                UserObservedPriceSavedSelectionAction.SelectStore(west)
            )
        )
        assertEquals(beforeClose, rendered.size)
    }

    @Test
    fun `route session owns only temporary selection presentation reconciliation and verified prefill handoff`() {
        val source = source("UserObservedPriceSavedSelectionRouteSession.kt").readText()

        assertTrue(source.contains("initialSnapshot: PracticalShoppingSavedValidatedSnapshot"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionSurfacePresenter"))
        assertTrue(source.contains("presenter.render"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionReducer.initial()"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionReducer.reconcile"))
        assertTrue(source.contains("UserObservedPriceSavedSelectionReducer.reduce"))
        assertTrue(source.contains("UserObservedPriceSavedPrefillHandoffGate.request"))
        assertFalse(source.contains("UserObservedPriceSavedPrefillGate.request"))
        assertFalse(source.contains("UserObservedPriceSavedSelectionUiStatus"))
        assertFalse(source.contains("UserObservedPriceSavedPrefillCheckUiAction"))
        listOf(
            "android.",
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
            "Renderer",
            "View",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Saved observed-price route session must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun session(
        initialSnapshot: PracticalShoppingSavedValidatedSnapshot = snapshot(),
        rendered: MutableList<UserObservedPriceSavedSelectionUiState> = mutableListOf()
    ): UserObservedPriceSavedSelectionRouteSession =
        UserObservedPriceSavedSelectionRouteSession(
            initialSnapshot = initialSnapshot,
            presenter =
                UserObservedPriceSavedSelectionSurfacePresenter { state ->
                    rendered += state
                }
        )

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
            providerId = EvidenceProviderId("test-provider-${itemKey.value}"),
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

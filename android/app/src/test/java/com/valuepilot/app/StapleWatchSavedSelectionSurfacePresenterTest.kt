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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StapleWatchSavedSelectionSurfacePresenterTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")

    @Test
    fun presenterFailsSafeWhenForegroundFactCheckIsNotConfigured() {
        val saved = savedState()
        val selection =
            StapleWatchSavedIdentitySelection(
                watchedItemKeys = listOf(milk, eggs),
                usualStoreKey = north
            )
        val metadata = metadata()
        val identityState =
            StapleWatchSavedIdentitySelectionUiProjector.project(saved, selection, metadata)
        var rendered: StapleWatchSavedSelectionUiState? = null
        val presenter =
            StapleWatchSavedSelectionSurfacePresenter { state -> rendered = state }

        presenter.render(saved, selection, metadata)

        val state = assertNotNullAndReturn(rendered)
        assertEquals(StapleWatchSavedSelectionUiStatus.FACT_CHECK_UNAVAILABLE, state.status)
        assertEquals(identityState.productRows, state.productRows)
        assertEquals(identityState.storeRows, state.storeRows)
        assertEquals(identityState.watchedItemCount, state.watchedItemCount)
        assertEquals(identityState.usualStoreSelected, state.usualStoreSelected)
        assertNull(state.continueAction)
        assertNull(state.continueActionLabel)
        assertTrue(state.notice?.contains("aren't available in this build yet") == true)
    }

    @Test
    fun explicitlyConfiguredForegroundFactCheckPreservesIdentityReadyContinuation() {
        val saved = savedState()
        val selection =
            StapleWatchSavedIdentitySelection(
                watchedItemKeys = listOf(milk, eggs),
                usualStoreKey = north
            )
        val metadata = metadata()
        val expected =
            StapleWatchSavedIdentitySelectionUiProjector.project(saved, selection, metadata)
        var rendered: StapleWatchSavedSelectionUiState? = null
        val presenter =
            StapleWatchSavedSelectionSurfacePresenter(
                renderer = { state -> rendered = state },
                factCheckCapability = StapleWatchForegroundFactCheckCapability.CONFIGURED
            )

        presenter.render(saved, selection, metadata)

        assertEquals(expected, rendered)
        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, rendered?.status)
        assertNotNull(rendered?.continueAction)
    }

    @Test
    fun capabilityGateLeavesNonReadyIdentityStateUntouched() {
        val saved = savedState()
        val selection = StapleWatchSavedIdentitySelectionReducer.initial()
        val projected =
            StapleWatchSavedIdentitySelectionUiProjector.project(saved, selection, metadata())

        val gated =
            StapleWatchSavedFactCheckCapabilityUiAdapter.apply(
                state = projected,
                capability = StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED
            )

        assertSame(projected, gated)
        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, gated.status)
    }

    @Test
    fun presenterPreservesSelectedDisplayMetadataBlockerInsteadOfInventingFallbackLabel() {
        val saved = savedState()
        val selection =
            StapleWatchSavedIdentitySelection(
                watchedItemKeys = listOf(milk, eggs),
                usualStoreKey = north
            )
        val metadata =
            metadata().copy(
                productDisplayNames = mapOf(eggs to "Large Eggs")
            )
        var rendered: StapleWatchSavedSelectionUiState? = null
        val presenter =
            StapleWatchSavedSelectionSurfacePresenter { state -> rendered = state }

        presenter.render(saved, selection, metadata)

        val state = assertNotNullAndReturn(rendered)
        assertEquals(StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertEquals(1, state.selectedDisplayNameBlockerCount)
        assertFalse(state.productRows.any { row -> row.title.contains(milk.value, ignoreCase = true) })
    }

    @Test
    fun rendererContractAndPresentationSourcesKeepAuthorityOutsidePhysicalSurface() {
        val renderMethod =
            StapleWatchSavedSelectionSurfaceRenderer::class.java.methods
                .single { method -> method.name == "render" }
        assertEquals(listOf(StapleWatchSavedSelectionUiState::class.java), renderMethod.parameterTypes.toList())

        val presenterSource = source("StapleWatchSavedSelectionSurfacePresenter.kt").readText()
        val capabilitySource = source("StapleWatchSavedFactCheckCapabilityPresentation.kt").readText()
        assertTrue(presenterSource.contains("StapleWatchForegroundFactCheckCapability.NOT_CONFIGURED"))
        assertTrue(capabilitySource.contains("state.status != StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK"))
        assertFalse(presenterSource.contains("android."))
        assertFalse(capabilitySource.contains("android."))
        listOf(
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "StapleWatchFactResolutionHost",
            "StapleWatchForegroundFactProducer",
            "PracticalShoppingProduction",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Presenter must not own $forbidden", presenterSource.contains(forbidden))
            assertFalse("Capability gate must not own $forbidden", capabilitySource.contains(forbidden))
        }
    }

    private fun metadata(): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames =
                mapOf(
                    milk to "Whole Milk",
                    eggs to "Large Eggs"
                ),
            storeDisplayNames = mapOf(north to "North Market")
        )

    private fun savedState(): PracticalShoppingSavedExactPreferenceState {
        val document =
            PracticalShoppingSavedExactPreferenceDocument(
                schemaVersion = PracticalShoppingSavedExactPreferenceStateManager.currentSchemaVersion,
                productPreferences = listOf(productPreference(milk), productPreference(eggs)),
                storePreferences = listOf(storePreference(north))
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

    private fun <T> assertNotNullAndReturn(value: T?): T {
        assertNotNull(value)
        return requireNotNull(value)
    }
}

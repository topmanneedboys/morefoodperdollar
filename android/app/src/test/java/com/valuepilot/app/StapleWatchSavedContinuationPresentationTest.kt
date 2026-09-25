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

class StapleWatchSavedContinuationPresentationTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")

    @Test
    fun readySelectionExposesIdentityFreeExplicitContinuationMarker() {
        val state =
            project(
                selection = selection(watched = listOf(milk, eggs), usualStore = north),
                metadata = fullMetadata()
            )

        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, state.status)
        assertEquals(StapleWatchSavedIdentityHandoffUiAction.Request, state.continueAction)
        assertEquals("Continue", state.continueActionLabel)
    }

    @Test
    fun incompleteSelectionDoesNotExposeContinuationMarker() {
        val state =
            project(
                selection = selection(watched = listOf(milk), usualStore = north),
                metadata = fullMetadata()
            )

        assertEquals(StapleWatchSavedSelectionUiStatus.NEEDS_SELECTION, state.status)
        assertNull(state.continueAction)
        assertNull(state.continueActionLabel)
    }

    @Test
    fun selectedDisplayMetadataBlockerSuppressesContinuationMarker() {
        val state =
            project(
                selection = selection(watched = listOf(milk, eggs), usualStore = north),
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(eggs to "Large Eggs", bread to "Sandwich Bread"),
                        storeDisplayNames = mapOf(north to "North Market")
                    )
            )

        assertEquals(StapleWatchSavedSelectionUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertNull(state.continueAction)
        assertNull(state.continueActionLabel)
    }

    @Test
    fun unresolvedUnselectedChoiceDoesNotSuppressReadyContinuationMarker() {
        val state =
            project(
                selection = selection(watched = listOf(milk, eggs), usualStore = north),
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames = mapOf(milk to "Whole Milk", eggs to "Large Eggs"),
                        storeDisplayNames = mapOf(north to "North Market")
                    )
            )

        assertEquals(StapleWatchSavedSelectionUiStatus.READY_FOR_FACT_CHECK, state.status)
        assertEquals(1, state.unresolvedDisplayNameCount)
        assertEquals(StapleWatchSavedIdentityHandoffUiAction.Request, state.continueAction)
        assertEquals("Continue", state.continueActionLabel)
    }

    @Test
    fun continuationPresentationCarriesNoIdentityAndOwnsNoHandoffOrBusinessAuthority() {
        val source = source("StapleWatchSavedIdentitySelectionPresentation.kt").readText()

        assertTrue(source.contains("sealed interface StapleWatchSavedIdentityHandoffUiAction"))
        assertTrue(source.contains("data object Request : StapleWatchSavedIdentityHandoffUiAction"))
        assertTrue(source.contains("val continueAction: StapleWatchSavedIdentityHandoffUiAction?"))
        assertTrue(source.contains("val continueActionLabel: String?"))
        assertTrue(source.contains("if (canContinue) StapleWatchSavedIdentityHandoffUiAction.Request else null"))
        assertFalse(source.contains("StapleWatchSavedIdentityHandoffGate"))
        listOf(
            "ShoppingRequest",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "SingleStorePlanCandidate",
            "ShoppingTravel",
            "Money",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis",
            "android."
        ).forEach { forbidden ->
            assertFalse("Continuation presentation must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun project(
        selection: StapleWatchSavedIdentitySelection,
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata
    ): StapleWatchSavedSelectionUiState =
        StapleWatchSavedIdentitySelectionUiProjector.project(
            savedState = savedState(),
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

    private fun savedState(): PracticalShoppingSavedExactPreferenceState =
        PracticalShoppingSavedExactPreferenceState(
            productPreferences =
                listOf(milk, eggs, bread).mapIndexed { index, itemKey ->
                    PracticalShoppingSavedExactProductPreference(
                        itemKey = itemKey,
                        providerId = EvidenceProviderId("test-provider"),
                        sourceIdentity = SourceProductIdentity(providerItemId = "product-$index")
                    )
                },
            storePreferences =
                listOf(
                    PracticalShoppingSavedExactStorePreference(
                        storeKey = north,
                        scope =
                            PracticalShoppingStoreIdentityScope(
                                merchantKey = "merchant-north",
                                locationKey = "location-north",
                                commerceChannelKey = "PHYSICAL_STORE"
                            )
                    )
                )
        )

    private fun fullMetadata(): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames =
                mapOf(
                    milk to "Whole Milk",
                    eggs to "Large Eggs",
                    bread to "Sandwich Bread"
                ),
            storeDisplayNames = mapOf(north to "North Market")
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

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

class StapleWatchSavedExplicitHandoffCompositionTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val bread = ShoppingItemKey("bread")
    private val north = ShoppingStoreKey("north")
    private val west = ShoppingStoreKey("west")

    @Test
    fun routeSessionExposesDetachedSelectionOnlyWhileVisible() {
        val session = routeSession(snapshot())

        assertNull(session.currentSelectionOrNull())

        session.onRouteVisibilityChanged(true)
        session.onSurfaceAction(watch(milk))
        session.onSurfaceAction(watch(eggs))
        session.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        val current = requireNotNull(session.currentSelectionOrNull())
        assertEquals(listOf(eggs, milk), current.watchedItemKeys)
        assertEquals(north, current.usualStoreKey)

        session.onRouteVisibilityChanged(false)
        assertNull(session.currentSelectionOrNull())

        session.close()
        assertNull(session.currentSelectionOrNull())
    }

    @Test
    fun becomingReadyNeverEmitsHandoffUntilExplicitRequest() {
        val attempts = mutableListOf<StapleWatchSavedIdentityHandoffAttempt>()
        val coordinator = coordinator(attempts)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)

        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(watch(eggs))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        assertTrue(attempts.isEmpty())

        coordinator.requestIdentityHandoff()

        val accepted = attempts.single()
        assertTrue(accepted.accepted)
        assertEquals(listOf(eggs, milk), requireNotNull(accepted.handoff).request.itemKeys)
        assertEquals(north, requireNotNull(accepted.handoff).usualStoreKey)
    }

    @Test
    fun continuationUiActionUsesExistingExplicitRequestPathAndVisibilityGate() {
        val attempts = mutableListOf<StapleWatchSavedIdentityHandoffAttempt>()
        val coordinator = coordinator(attempts)
        coordinator.onSnapshot(snapshot())

        coordinator.onContinueAction(StapleWatchSavedIdentityHandoffUiAction.Request)
        assertTrue(attempts.isEmpty())

        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(watch(eggs))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))
        coordinator.onContinueAction(StapleWatchSavedIdentityHandoffUiAction.Request)

        val accepted = attempts.single()
        assertTrue(accepted.accepted)
        assertEquals(listOf(eggs, milk), requireNotNull(accepted.handoff).request.itemKeys)
        assertEquals(north, requireNotNull(accepted.handoff).usualStoreKey)

        coordinator.onRouteVisibilityChanged(false)
        coordinator.onContinueAction(StapleWatchSavedIdentityHandoffUiAction.Request)
        assertEquals(1, attempts.size)
    }

    @Test
    fun explicitRequestUsesLatestSnapshotAndFailsClosedForSelectedDisplayBlocker() {
        val attempts = mutableListOf<StapleWatchSavedIdentityHandoffAttempt>()
        val coordinator = coordinator(attempts)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(watch(eggs))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        coordinator.onSnapshot(
            snapshot(
                metadata =
                    PracticalShoppingSavedExactPreferenceDisplayMetadata(
                        productDisplayNames =
                            mapOf(
                                eggs to "Large Eggs",
                                bread to "Sandwich Bread"
                            ),
                        storeDisplayNames =
                            mapOf(
                                north to "North Market",
                                west to "West Market"
                            )
                    )
            )
        )

        assertTrue(attempts.isEmpty())

        coordinator.requestIdentityHandoff()

        val rejected = attempts.single()
        assertFalse(rejected.accepted)
        assertNull(rejected.handoff)
        assertEquals(
            StapleWatchSavedIdentityHandoffIssue.SELECTED_DISPLAY_METADATA_INCOMPLETE,
            rejected.issue
        )
    }

    @Test
    fun hiddenOrClosedExplicitRequestsAreIgnored() {
        val attempts = mutableListOf<StapleWatchSavedIdentityHandoffAttempt>()
        val coordinator = coordinator(attempts)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(watch(eggs))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        coordinator.onRouteVisibilityChanged(false)
        coordinator.requestIdentityHandoff()
        assertTrue(attempts.isEmpty())

        coordinator.onRouteVisibilityChanged(true)
        coordinator.requestIdentityHandoff()
        assertEquals(1, attempts.size)

        coordinator.close()
        coordinator.requestIdentityHandoff()
        assertEquals(1, attempts.size)
    }

    @Test
    fun explicitCompositionKeepsEligibilityOutOfRouteSessionAndBusinessWorkOutOfCoordinator() {
        val routeSource = source("StapleWatchSavedSelectionRouteSession.kt").readText()
        val coordinatorSource = source("StapleWatchSavedSetupCompositionCoordinator.kt").readText()

        assertTrue(routeSource.contains("fun currentSelectionOrNull()"))
        assertFalse(routeSource.contains("StapleWatchSavedIdentityHandoffGate"))
        assertFalse(routeSource.contains("identityHandoffOrNull"))
        assertFalse(routeSource.contains("ShoppingRequest"))
        assertFalse(routeSource.contains("StapleWatchSavedSelectionUiStatus"))

        assertTrue(coordinatorSource.contains("fun onContinueAction(action: StapleWatchSavedIdentityHandoffUiAction)"))
        assertTrue(
            coordinatorSource.contains(
                "StapleWatchSavedIdentityHandoffUiAction.Request -> requestIdentityHandoff()"
            )
        )
        assertTrue(coordinatorSource.contains("fun requestIdentityHandoff()"))
        assertTrue(coordinatorSource.contains("StapleWatchSavedIdentityHandoffGate.request"))
        assertTrue(coordinatorSource.contains("currentSelectionOrNull()"))
        assertFalse(coordinatorSource.contains("StapleWatchSavedSelectionUiStatus"))
        listOf(
            "android.",
            "ShoppingRequest",
            "Money",
            "SingleStorePlanCandidate",
            "ShoppingTravel",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "NotificationManager",
            "WorkManager",
            "SharedPreferences",
            "System.currentTimeMillis"
        ).forEach { forbidden ->
            assertFalse(
                "Explicit staple handoff composition must not own $forbidden",
                coordinatorSource.contains(forbidden)
            )
        }
    }

    private fun coordinator(
        attempts: MutableList<StapleWatchSavedIdentityHandoffAttempt>
    ): StapleWatchSavedSetupCompositionCoordinator =
        StapleWatchSavedSetupCompositionCoordinator(
            sessionFactory = { accepted -> routeSession(accepted) },
            handoffAttemptObserver =
                StapleWatchSavedIdentityHandoffAttemptObserver { attempt ->
                    attempts += attempt
                }
        )

    private fun routeSession(
        initialSnapshot: PracticalShoppingSavedValidatedSnapshot
    ): StapleWatchSavedSelectionRouteSession =
        StapleWatchSavedSelectionRouteSession(
            initialSnapshot = initialSnapshot,
            presenter = StapleWatchSavedSelectionSurfacePresenter { }
        )

    private fun watch(itemKey: ShoppingItemKey) =
        StapleWatchSavedIdentitySelectionAction.SetProductWatched(
            itemKey = itemKey,
            watched = true
        )

    private fun snapshot(
        savedState: PracticalShoppingSavedExactPreferenceState = savedState(),
        metadata: PracticalShoppingSavedExactPreferenceDisplayMetadata = metadata()
    ): PracticalShoppingSavedValidatedSnapshot =
        PracticalShoppingSavedValidatedSnapshot(
            exactState = savedState,
            displayMetadata = metadata
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
                listOf(north, west).mapIndexed { index, storeKey ->
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

    private fun metadata(): PracticalShoppingSavedExactPreferenceDisplayMetadata =
        PracticalShoppingSavedExactPreferenceDisplayMetadata(
            productDisplayNames =
                mapOf(
                    milk to "Whole Milk",
                    eggs to "Large Eggs",
                    bread to "Sandwich Bread"
                ),
            storeDisplayNames =
                mapOf(
                    north to "North Market",
                    west to "West Market"
                )
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

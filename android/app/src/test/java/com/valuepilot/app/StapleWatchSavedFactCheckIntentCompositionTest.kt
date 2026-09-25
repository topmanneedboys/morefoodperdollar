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

class StapleWatchSavedFactCheckIntentCompositionTest {

    private val milk = ShoppingItemKey("milk")
    private val eggs = ShoppingItemKey("eggs")
    private val north = ShoppingStoreKey("north")

    @Test
    fun acceptedExplicitHandoffEmitsOneUnresolvedFactCheckIntent() {
        val attempts = mutableListOf<StapleWatchSavedIdentityHandoffAttempt>()
        val intents = mutableListOf<StapleWatchFactCheckIntent>()
        val coordinator = coordinator(attempts, intents)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(watch(eggs))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        assertTrue(attempts.isEmpty())
        assertTrue(intents.isEmpty())

        coordinator.requestIdentityHandoff()

        assertTrue(attempts.single().accepted)
        val intent = intents.single()
        assertEquals(listOf(eggs, milk), intent.request.itemKeys)
        assertEquals(north, intent.usualStoreKey)
        assertEquals(
            listOf(
                StapleWatchFactResolutionRequirement.USUAL_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_CANDIDATE_IDENTITIES,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_STORE_BASKET_PRICE_EVIDENCE,
                StapleWatchFactResolutionRequirement.ALTERNATIVE_ADDITIONAL_TRAVEL_FACTS,
                StapleWatchFactResolutionRequirement.EVIDENCE_CURRENTNESS_METADATA
            ),
            intent.requirements
        )
    }

    @Test
    fun rejectedExplicitHandoffEmitsAttemptButNoFactCheckIntent() {
        val attempts = mutableListOf<StapleWatchSavedIdentityHandoffAttempt>()
        val intents = mutableListOf<StapleWatchFactCheckIntent>()
        val coordinator = coordinator(attempts, intents)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        coordinator.requestIdentityHandoff()

        val rejected = attempts.single()
        assertFalse(rejected.accepted)
        assertEquals(StapleWatchSavedIdentityHandoffIssue.NOT_READY, rejected.issue)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun hiddenAndClosedRequestsEmitNoFactCheckIntent() {
        val attempts = mutableListOf<StapleWatchSavedIdentityHandoffAttempt>()
        val intents = mutableListOf<StapleWatchFactCheckIntent>()
        val coordinator = coordinator(attempts, intents)
        coordinator.onSnapshot(snapshot())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onSurfaceAction(watch(milk))
        coordinator.onSurfaceAction(watch(eggs))
        coordinator.onSurfaceAction(StapleWatchSavedIdentitySelectionAction.SelectUsualStore(north))

        coordinator.onRouteVisibilityChanged(false)
        coordinator.requestIdentityHandoff()
        assertTrue(attempts.isEmpty())
        assertTrue(intents.isEmpty())

        coordinator.onRouteVisibilityChanged(true)
        coordinator.close()
        coordinator.requestIdentityHandoff()
        assertTrue(attempts.isEmpty())
        assertTrue(intents.isEmpty())
    }

    @Test
    fun compositionOnlyAdaptsAcceptedIdentityAndOwnsNoResolvedFactOrEconomicWork() {
        val source = source("StapleWatchSavedSetupCompositionCoordinator.kt").readText()

        assertTrue(source.contains("StapleWatchFactCheckIntentObserver"))
        assertTrue(source.contains("StapleWatchSavedFactCheckIntentAdapter.from(attempt)"))
        assertTrue(source.contains("factCheckIntentObserver::onIntent"))
        assertFalse(source.contains("StapleWatchSavedSelectionUiStatus"))

        listOf(
            "SingleStorePlanCandidate",
            "TwoStorePlanCandidate",
            "ShoppingTravel",
            "ShoppingPlanEvidenceSummary",
            "Money",
            "EvidenceProviderId",
            "SourceProductIdentity",
            "PracticalShoppingPlanner",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "OpenPrices",
            "OpenStreetMap",
            "OpenFoodFacts",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "android."
        ).forEach { forbidden ->
            assertFalse("Fact-intent composition must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun coordinator(
        attempts: MutableList<StapleWatchSavedIdentityHandoffAttempt>,
        intents: MutableList<StapleWatchFactCheckIntent>
    ): StapleWatchSavedSetupCompositionCoordinator =
        StapleWatchSavedSetupCompositionCoordinator(
            handoffAttemptObserver =
                StapleWatchSavedIdentityHandoffAttemptObserver { attempt -> attempts += attempt },
            factCheckIntentObserver =
                StapleWatchFactCheckIntentObserver { intent -> intents += intent },
            sessionFactory = { accepted -> routeSession(accepted) }
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

    private fun snapshot(): PracticalShoppingSavedValidatedSnapshot =
        PracticalShoppingSavedValidatedSnapshot(
            exactState =
                PracticalShoppingSavedExactPreferenceState(
                    productPreferences =
                        listOf(milk, eggs).mapIndexed { index, itemKey ->
                            PracticalShoppingSavedExactProductPreference(
                                itemKey = itemKey,
                                providerId = EvidenceProviderId("test-provider"),
                                sourceIdentity =
                                    SourceProductIdentity(providerItemId = "product-$index")
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
                ),
            displayMetadata =
                PracticalShoppingSavedExactPreferenceDisplayMetadata(
                    productDisplayNames =
                        mapOf(
                            milk to "Whole Milk",
                            eggs to "Large Eggs"
                        ),
                    storeDisplayNames = mapOf(north to "North Market")
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

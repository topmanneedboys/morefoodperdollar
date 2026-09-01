package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UserObservedPriceSavedConfirmationDraftRouteCoordinatorTest {

    @Test
    fun `accepted Saved handoff requests route and applies only identity after route becomes visible`() {
        var openRequests = 0
        var createdSessions = 0
        val finalizations = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver =
                    UserObservedPriceConfirmationDraftRouteOpenObserver { openRequests += 1 },
                sessionFactory = {
                    createdSessions += 1
                    UserObservedPriceConfirmationDraftRouteSession(
                        observer =
                            UserObservedPriceConfirmationDraftObserver { finalization ->
                                finalizations += finalization
                            }
                    ).also { created -> sessions += created }
                }
            )

        coordinator.onAttempt(acceptedAttempt())

        assertEquals(1, openRequests)
        assertEquals(0, createdSessions)
        assertFalse(coordinator.isVisible())

        coordinator.onRouteVisibilityChanged(true)

        assertTrue(coordinator.isVisible())
        assertEquals(1, createdSessions)
        assertTrue(sessions.single().isVisible())

        val latest = finalizations.last()
        assertNull(latest.submission)
        assertEquals(
            setOf(
                UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID,
                UserObservedPriceConfirmationDraftMissingField.PROOF_TYPE,
                UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID,
                UserObservedPriceConfirmationDraftMissingField.PRICE,
                UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            latest.missingFields
        )
    }

    @Test
    fun `explicit typed price is ignored before visibility and forwarded unchanged only to active draft`() {
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = {
                    UserObservedPriceConfirmationDraftRouteSession()
                        .also { created -> sessions += created }
                }
            )
        val price = Money(minorUnits = 1_005L, currencyCode = "BHD", fractionDigits = 3)

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onPriceInput(price)
        coordinator.onRouteVisibilityChanged(true)

        val session = sessions.single()
        assertTrue(
            UserObservedPriceConfirmationDraftMissingField.PRICE in
                requireNotNull(session.currentFinalizationOrNull()).missingFields
        )

        coordinator.onPriceInput(price)

        val afterPrice = requireNotNull(session.currentFinalizationOrNull())
        assertFalse(UserObservedPriceConfirmationDraftMissingField.PRICE in afterPrice.missingFields)
        assertNull(afterPrice.submission)

        session.onArtifactReferenceChanged("artifact-001", UserProvidedPriceProofType.PRICE_TAG)
        session.onObservationReferenceChanged("observation-001")
        session.onObservedAtChanged(10_000L)
        session.onConfirmationChanged("confirmation-001", 20_000L)

        assertEquals(price, requireNotNull(session.currentSubmissionOrNull()).fields.price)
    }

    @Test
    fun `price cannot survive leaving route or enter a later draft without a new explicit input`() {
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = {
                    UserObservedPriceConfirmationDraftRouteSession()
                        .also { created -> sessions += created }
                }
            )

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onPriceInput(Money(599L, "CAD"))
        assertFalse(
            UserObservedPriceConfirmationDraftMissingField.PRICE in
                requireNotNull(sessions[0].currentFinalizationOrNull()).missingFields
        )

        coordinator.onRouteVisibilityChanged(false)
        coordinator.onPriceInput(Money(699L, "USD"))
        assertTrue(sessions[0].isClosed())

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)

        assertEquals(2, sessions.size)
        assertTrue(
            UserObservedPriceConfirmationDraftMissingField.PRICE in
                requireNotNull(sessions[1].currentFinalizationOrNull()).missingFields
        )
    }

    @Test
    fun `rejected Saved handoff cannot request or create confirmation draft route session`() {
        var openRequests = 0
        var createdSessions = 0
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver =
                    UserObservedPriceConfirmationDraftRouteOpenObserver { openRequests += 1 },
                sessionFactory = {
                    createdSessions += 1
                    UserObservedPriceConfirmationDraftRouteSession()
                }
            )

        coordinator.onAttempt(
            UserObservedPriceSavedPrefillHandoffAttempt(
                prefill = null,
                issue = UserObservedPriceSavedPrefillHandoffIssue.SELECTION_NOT_READY
            )
        )
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onPriceInput(Money(599L, "CAD"))

        assertEquals(0, openRequests)
        assertEquals(0, createdSessions)
        assertTrue(coordinator.isVisible())
    }

    @Test
    fun `leaving confirmation route closes temporary draft and does not recreate without a new accepted handoff`() {
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = {
                    UserObservedPriceConfirmationDraftRouteSession()
                        .also { created -> sessions += created }
                }
            )

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        val first = sessions.single()
        assertTrue(first.isVisible())

        coordinator.onRouteVisibilityChanged(false)
        assertTrue(first.isClosed())
        assertFalse(coordinator.isVisible())

        coordinator.onRouteVisibilityChanged(true)
        assertEquals(1, sessions.size)
    }

    @Test
    fun `close clears pending handoff and closes active session`() {
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = {
                    UserObservedPriceConfirmationDraftRouteSession()
                        .also { created -> sessions += created }
                }
            )

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        val active = sessions.single()

        coordinator.close()

        assertTrue(coordinator.isClosed())
        assertTrue(active.isClosed())

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onPriceInput(Money(599L, "CAD"))
        assertEquals(1, sessions.size)
    }

    @Test
    fun `shell adapter emits draft intent only while exact Saved selection route owns foreground`() {
        var route = AppRoute.SAVED
        val intents = mutableListOf<AppShellIntent>()
        val adapter =
            UserObservedPriceConfirmationDraftRouteShellAdapter(
                currentRoute = { route },
                emitIntent = { intent -> intents += intent }
            )

        adapter.onOpenRequested()
        assertTrue(intents.isEmpty())

        route = AppRoute.OBSERVED_PRICE_SAVED_SELECTION
        adapter.onOpenRequested()

        assertEquals(1, intents.size)
        assertSame(AppShellIntent.OpenObservedPriceConfirmationDraft, intents.single())
    }

    private fun acceptedAttempt(): UserObservedPriceSavedPrefillHandoffAttempt =
        UserObservedPriceSavedPrefillHandoffAttempt(
            prefill =
                UserObservedPriceSavedPrefill(
                    itemKey = ShoppingItemKey("milk"),
                    storeKey = ShoppingStoreKey("north"),
                    rawGtin = "036000291452",
                    productName = "Whole Milk 2%",
                    storeScope =
                        PracticalShoppingStoreIdentityScope(
                            merchantKey = "merchant-north",
                            locationKey = "location-north",
                            commerceChannelKey = "PHYSICAL_STORE"
                        ),
                    storeDisplayName = "North Market"
                )
        )
}

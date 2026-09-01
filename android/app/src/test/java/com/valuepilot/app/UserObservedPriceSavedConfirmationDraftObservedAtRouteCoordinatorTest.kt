package com.valuepilot.app

import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedConfirmationDraftObservedAtRouteCoordinatorTest {

    @Test
    fun `explicit observed time is ignored before route visibility and forwarded unchanged while active`() {
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator = coordinator(sessions)
        val observedAt = 1_789_999_123_456L

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onObservedAtInput(observedAt)
        coordinator.onRouteVisibilityChanged(true)

        val session = sessions.single()
        val beforeInput = requireNotNull(session.currentFinalizationOrNull())
        assertTrue(UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT in beforeInput.missingFields)

        coordinator.onObservedAtInput(observedAt)

        val afterInput = requireNotNull(session.currentFinalizationOrNull())
        assertFalse(UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT in afterInput.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID in afterInput.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID in afterInput.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT in afterInput.missingFields)

        session.onArtifactReferenceChanged("artifact-001", UserProvidedPriceProofType.RECEIPT)
        session.onObservationReferenceChanged("observation-001")
        session.onPriceChanged(com.valuepilot.core.Money(599L, "CAD"))
        session.onConfirmationChanged("confirmation-001", observedAt + 1L)

        assertEquals(observedAt, requireNotNull(session.currentSubmissionOrNull()).fields.observedAtEpochMillis)
    }

    @Test
    fun `observed time cannot survive route exit or leak into a later draft`() {
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator = coordinator(sessions)

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onObservedAtInput(10_000L)

        assertFalse(
            UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT in
                requireNotNull(sessions[0].currentFinalizationOrNull()).missingFields
        )

        coordinator.onRouteVisibilityChanged(false)
        coordinator.onObservedAtInput(20_000L)
        assertTrue(sessions[0].isClosed())

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)

        assertEquals(2, sessions.size)
        assertTrue(
            UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT in
                requireNotNull(sessions[1].currentFinalizationOrNull()).missingFields
        )
    }

    @Test
    fun `rejected handoff and closed coordinator cannot acquire observed time state`() {
        var createdSessions = 0
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
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
        coordinator.onObservedAtInput(10_000L)
        assertEquals(0, createdSessions)

        coordinator.close()
        coordinator.onObservedAtInput(20_000L)
        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        assertEquals(0, createdSessions)
    }

    @Test
    fun `coordinator forwards typed epoch only and owns no time parsing generation or downstream authority`() {
        val source = source().readText()

        assertTrue(source.contains("fun onObservedAtInput(observedAtEpochMillis: Long)"))
        assertTrue(source.contains("session?.onObservedAtChanged(observedAtEpochMillis)"))
        assertTrue(source.contains("if (closed || !routeVisible) return"))

        listOf(
            "UserObservedPriceConfirmationDraftObservedAtTextInputAdapter",
            "GregorianCalendar",
            "SimpleTimeZone",
            "TimeZone.getDefault",
            "Calendar.getInstance",
            "System.currentTimeMillis",
            "System.nanoTime",
            "Instant.now",
            "LocalDateTime.now",
            "UUID",
            "MessageDigest",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProofArtifactLocalStore",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionCurrentPrice",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Route coordinator must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun coordinator(
        sessions: MutableList<UserObservedPriceConfirmationDraftRouteSession>
    ): UserObservedPriceSavedConfirmationDraftRouteCoordinator =
        UserObservedPriceSavedConfirmationDraftRouteCoordinator(
            routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
            sessionFactory = {
                UserObservedPriceConfirmationDraftRouteSession()
                    .also { created -> sessions += created }
            }
        )

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

    private fun source(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "app/src/main/java/com/valuepilot/app/UserObservedPriceSavedConfirmationDraftRouteCoordinator.kt"
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate UserObservedPriceSavedConfirmationDraftRouteCoordinator.kt")
    }
}

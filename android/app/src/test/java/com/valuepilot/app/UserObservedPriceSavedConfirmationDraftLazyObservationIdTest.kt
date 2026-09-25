package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedConfirmationDraftLazyObservationIdTest {

    @Test
    fun `route opening and hidden input do not allocate observation identity`() {
        var idRequests = 0
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            coordinator(
                sessions = sessions,
                observationIdSource = UserObservedPriceObservationIdSource {
                    idRequests += 1
                    "observation-$idRequests"
                }
            )

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onPriceInput(Money(599L, "CAD"))

        assertEquals(0, idRequests)
        assertTrue(sessions.isEmpty())

        coordinator.onRouteVisibilityChanged(true)

        assertEquals(0, idRequests)
        val opened = requireNotNull(sessions.single().currentFinalizationOrNull())
        assertTrue(UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID in opened.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.PRICE in opened.missingFields)
    }

    @Test
    fun `first explicit observation input allocates one ID reused by price proof and observed time`() {
        var idRequests = 0
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            coordinator(
                sessions = sessions,
                observationIdSource = UserObservedPriceObservationIdSource {
                    idRequests += 1
                    "observation-$idRequests"
                }
            )

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)

        coordinator.onProofReferenceInput(
            artifactId = "receipt-sept-1",
            proofType = UserProvidedPriceProofType.RECEIPT
        )

        assertEquals(1, idRequests)
        val afterFirstFact = requireNotNull(sessions.single().currentFinalizationOrNull())
        assertFalse(UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID in afterFirstFact.missingFields)

        coordinator.onPriceInput(Money(599L, "CAD"))
        coordinator.onObservedAtInput(10_000L)

        assertEquals(1, idRequests)

        val session = sessions.single()
        session.onConfirmationChanged(
            confirmationId = "confirmation-explicit",
            confirmedAtEpochMillis = 20_000L
        )

        val submission = requireNotNull(session.currentSubmissionOrNull())
        assertEquals("observation-1", submission.fields.observationId)
        assertEquals(599L, submission.fields.price.minorUnits)
        assertEquals("receipt-sept-1", submission.artifactId)
        assertEquals(10_000L, submission.fields.observedAtEpochMillis)
    }

    @Test
    fun `route exit discards allocation and later route receives a fresh observation identity`() {
        var idRequests = 0
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            coordinator(
                sessions = sessions,
                observationIdSource = UserObservedPriceObservationIdSource {
                    idRequests += 1
                    "observation-$idRequests"
                }
            )

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onObservedAtInput(10_000L)

        assertEquals(1, idRequests)
        val first = sessions.single()

        coordinator.onRouteVisibilityChanged(false)
        assertTrue(first.isClosed())

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)

        assertEquals(1, idRequests)
        assertEquals(2, sessions.size)
        val secondBeforeInput = requireNotNull(sessions[1].currentFinalizationOrNull())
        assertTrue(UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID in secondBeforeInput.missingFields)

        coordinator.onPriceInput(Money(699L, "USD"))

        assertEquals(2, idRequests)
        val secondAfterInput = requireNotNull(sessions[1].currentFinalizationOrNull())
        assertFalse(UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID in secondAfterInput.missingFields)
    }

    @Test
    fun `rejected no-session and closed states never allocate observation identity`() {
        var idRequests = 0
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = { UserObservedPriceConfirmationDraftRouteSession() },
                observationIdSource = UserObservedPriceObservationIdSource {
                    idRequests += 1
                    "observation-$idRequests"
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
        coordinator.onProofReferenceInput("receipt-001", UserProvidedPriceProofType.RECEIPT)
        coordinator.onObservedAtInput(10_000L)

        assertEquals(0, idRequests)

        coordinator.close()
        coordinator.onPriceInput(Money(699L, "USD"))
        coordinator.onProofReferenceInput("price-tag-002", UserProvidedPriceProofType.PRICE_TAG)
        coordinator.onObservedAtInput(20_000L)

        assertEquals(0, idRequests)
    }

    @Test
    fun `coordinator delegates opaque ID mechanism and owns no clock confirmation or persistence authority`() {
        val source = coordinatorSource().readText()

        assertTrue(source.contains("observationIdSource.nextObservationId()"))
        assertFalse(source.contains("UUID.randomUUID"))
        assertFalse(source.contains("System.currentTimeMillis"))
        assertFalse(source.contains("Instant.now"))
        assertFalse(source.contains(".onConfirmationChanged("))
        assertFalse(source.contains("UserObservedPriceConfirmationTransaction"))
        assertFalse(source.contains("UserProvidedPriceProofArtifactLocalStore"))
        assertFalse(source.contains("UserConfirmedObservedPrice"))
        assertFalse(source.contains("ProductionCurrentPrice"))
    }

    private fun coordinator(
        sessions: MutableList<UserObservedPriceConfirmationDraftRouteSession>,
        observationIdSource: UserObservedPriceObservationIdSource
    ): UserObservedPriceSavedConfirmationDraftRouteCoordinator =
        UserObservedPriceSavedConfirmationDraftRouteCoordinator(
            routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
            sessionFactory = {
                UserObservedPriceConfirmationDraftRouteSession()
                    .also { created -> sessions += created }
            },
            observationIdSource = observationIdSource
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

    private fun coordinatorSource(): File {
        val root = File(System.getProperty("user.dir"))
        return File(
            root,
            "src/main/java/com/valuepilot/app/UserObservedPriceSavedConfirmationDraftRouteCoordinator.kt"
        ).also {
            assertTrue("Missing coordinator source at ${it.absolutePath}", it.isFile)
        }
    }
}

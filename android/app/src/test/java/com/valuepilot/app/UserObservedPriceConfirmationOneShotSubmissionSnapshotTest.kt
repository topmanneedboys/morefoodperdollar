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
import java.io.File

class UserObservedPriceConfirmationOneShotSubmissionSnapshotTest {

    @Test
    fun `one-shot session snapshot overlays confirmation metadata without mutating editable draft`() {
        val presentations = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val session =
            UserObservedPriceConfirmationDraftRouteSession(
                observer = UserObservedPriceConfirmationDraftObserver(presentations::add)
            )
        val price = Money(599L, "CAD")
        val storeScope = exactStoreScope()

        session.onRouteVisibilityChanged(true)
        session.onArtifactReferenceChanged("receipt-001", UserProvidedPriceProofType.RECEIPT)
        session.onProductChanged(
            observationId = "observation-001",
            rawGtin = "036000291452",
            productName = "Whole Milk 2%"
        )
        session.onPriceChanged(price)
        session.onStoreScopeChanged(storeScope)
        session.onObservedAtChanged(10_000L)

        val before = requireNotNull(session.currentFinalizationOrNull())
        assertEquals(
            setOf(
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            before.missingFields
        )
        assertNull(session.currentSubmissionOrNull())
        val publishedBeforeSnapshot = presentations.size

        val submission =
            requireNotNull(
                session.currentSubmissionWithConfirmationOrNull(
                    UserObservedPriceConfirmationLifecycleMetadata(
                        confirmationId = "confirmation-action-001",
                        confirmedAtEpochMillis = 10_001L
                    )
                )
            )

        assertEquals("receipt-001", submission.artifactId)
        assertSame(UserProvidedPriceProofType.RECEIPT, submission.proofType)
        assertEquals("observation-001", submission.fields.observationId)
        assertEquals("036000291452", submission.fields.rawGtin)
        assertEquals("Whole Milk 2%", submission.fields.productName)
        assertSame(price, submission.fields.price)
        assertSame(storeScope, submission.fields.storeScope)
        assertEquals(10_000L, submission.fields.observedAtEpochMillis)
        assertEquals("confirmation-action-001", submission.fields.confirmationId)
        assertEquals(10_001L, submission.fields.confirmedAtEpochMillis)

        assertEquals(publishedBeforeSnapshot, presentations.size)
        assertNull(session.currentSubmissionOrNull())
        assertEquals(before, session.currentFinalizationOrNull())
    }

    @Test
    fun `one-shot snapshot performs completeness only and preserves raw metadata for downstream validation`() {
        val session = preConfirmationReadySession()
        val submission =
            requireNotNull(
                session.currentSubmissionWithConfirmationOrNull(
                    UserObservedPriceConfirmationLifecycleMetadata(
                        confirmationId = "",
                        confirmedAtEpochMillis = -5L
                    )
                )
            )

        assertEquals("", submission.fields.confirmationId)
        assertEquals(-5L, submission.fields.confirmedAtEpochMillis)
        assertNull(session.currentSubmissionOrNull())
        assertEquals(
            setOf(
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            requireNotNull(session.currentFinalizationOrNull()).missingFields
        )
    }

    @Test
    fun `incomplete hidden and closed sessions cannot produce one-shot confirmation submission`() {
        val metadata =
            UserObservedPriceConfirmationLifecycleMetadata(
                confirmationId = "confirmation-action-001",
                confirmedAtEpochMillis = 10_001L
            )

        val incomplete = UserObservedPriceConfirmationDraftRouteSession()
        incomplete.onRouteVisibilityChanged(true)
        assertNull(incomplete.currentSubmissionWithConfirmationOrNull(metadata))

        val hidden = preConfirmationReadySession()
        hidden.onRouteVisibilityChanged(false)
        assertNull(hidden.currentSubmissionWithConfirmationOrNull(metadata))

        val closed = preConfirmationReadySession()
        closed.close()
        assertNull(closed.currentSubmissionWithConfirmationOrNull(metadata))
    }

    @Test
    fun `route coordinator exposes immutable readiness and one-shot snapshot only for active route session`() {
        val sessions = mutableListOf<UserObservedPriceConfirmationDraftRouteSession>()
        val coordinator =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = {
                    UserObservedPriceConfirmationDraftRouteSession()
                        .also { created -> sessions += created }
                },
                observationIdSource =
                    UserObservedPriceObservationIdSource { "observation-route-001" }
            )

        assertNull(coordinator.currentFinalizationOrNull())
        assertNull(
            coordinator.currentSubmissionWithConfirmationOrNull(
                UserObservedPriceConfirmationLifecycleMetadata("confirmation-hidden", 1L)
            )
        )

        coordinator.onAttempt(acceptedAttempt())
        coordinator.onRouteVisibilityChanged(true)
        coordinator.onProofReferenceInput("receipt-route-001", UserProvidedPriceProofType.RECEIPT)
        coordinator.onPriceInput(Money(799L, "CAD"))
        coordinator.onObservedAtInput(20_000L)

        val ready = requireNotNull(coordinator.currentFinalizationOrNull())
        assertEquals(
            setOf(
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            ready.missingFields
        )

        val submission =
            requireNotNull(
                coordinator.currentSubmissionWithConfirmationOrNull(
                    UserObservedPriceConfirmationLifecycleMetadata(
                        confirmationId = "confirmation-route-001",
                        confirmedAtEpochMillis = 20_001L
                    )
                )
            )

        assertEquals("observation-route-001", submission.fields.observationId)
        assertEquals("receipt-route-001", submission.artifactId)
        assertEquals("confirmation-route-001", submission.fields.confirmationId)
        assertEquals(20_001L, submission.fields.confirmedAtEpochMillis)
        assertNull(sessions.single().currentSubmissionOrNull())
        assertEquals(ready, coordinator.currentFinalizationOrNull())

        coordinator.onRouteVisibilityChanged(false)
        assertNull(coordinator.currentFinalizationOrNull())
        assertNull(
            coordinator.currentSubmissionWithConfirmationOrNull(
                UserObservedPriceConfirmationLifecycleMetadata("confirmation-stale", 30_000L)
            )
        )
    }

    @Test
    fun `snapshot seam owns no metadata generation proof bytes submission storage or evidence authority`() {
        val sessionSource = source("UserObservedPriceConfirmationDraftRouteSession.kt").readText()
        val coordinatorSource =
            source("UserObservedPriceSavedConfirmationDraftRouteCoordinator.kt").readText()

        assertTrue(sessionSource.contains("current.withConfirmation("))
        assertTrue(
            sessionSource.contains(
                "UserObservedPriceConfirmationDraftFinalizer.finalize(actionSnapshot).submission"
            )
        )
        assertTrue(
            coordinatorSource.contains("session?.currentSubmissionWithConfirmationOrNull(metadata)")
        )

        listOf(sessionSource, coordinatorSource).forEach { source ->
            listOf(
                "LocalUserObservedPriceConfirmationLifecycleMetadataSource",
                "System.currentTimeMillis",
                "Instant.now",
                "UUID.randomUUID",
                "artifactBytes",
                "UserObservedPriceConfirmationDraftSubmissionTarget",
                "UserObservedPriceConfirmationAndroidSession",
                "UserObservedPriceConfirmationTransaction(",
                "UserProvidedPriceProofArtifactLocalStore",
                "UserProvidedPriceProofArtifact.fingerprint",
                "UserConfirmedObservedPrice.confirm",
                "ShoppingEvidence(",
                "EvidenceClaim(",
                "CURRENT_PRICE",
                "ProductionCurrentPrice",
                "java.net"
            ).forEach { forbidden ->
                assertFalse("Snapshot seam must not own $forbidden", source.contains(forbidden))
            }
        }
    }

    private fun preConfirmationReadySession(): UserObservedPriceConfirmationDraftRouteSession =
        UserObservedPriceConfirmationDraftRouteSession().also { session ->
            session.onRouteVisibilityChanged(true)
            session.onArtifactReferenceChanged("receipt-001", UserProvidedPriceProofType.RECEIPT)
            session.onProductChanged(
                observationId = "observation-001",
                rawGtin = "036000291452",
                productName = "Whole Milk 2%"
            )
            session.onPriceChanged(Money(599L, "CAD"))
            session.onStoreScopeChanged(exactStoreScope())
            session.onObservedAtChanged(10_000L)
        }

    private fun acceptedAttempt(): UserObservedPriceSavedPrefillHandoffAttempt =
        UserObservedPriceSavedPrefillHandoffAttempt(
            prefill =
                UserObservedPriceSavedPrefill(
                    itemKey = ShoppingItemKey("milk"),
                    storeKey = ShoppingStoreKey("north"),
                    rawGtin = "036000291452",
                    productName = "Whole Milk 2%",
                    storeScope = exactStoreScope(),
                    storeDisplayName = "North Market"
                )
        )

    private fun exactStoreScope(): PracticalShoppingStoreIdentityScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-north",
            locationKey = "location-north",
            commerceChannelKey = "PHYSICAL_STORE"
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

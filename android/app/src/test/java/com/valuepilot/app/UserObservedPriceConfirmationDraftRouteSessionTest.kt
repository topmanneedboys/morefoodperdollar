package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftRouteSessionTest {

    @Test
    fun `visible route publishes initial immutable completeness state`() {
        val observed = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val session = session(observed)

        assertFalse(session.isVisible())
        assertNull(session.currentFinalizationOrNull())

        session.onRouteVisibilityChanged(true)

        assertTrue(session.isVisible())
        assertEquals(1, observed.size)
        assertFalse(observed.single().complete)
        assertEquals(
            UserObservedPriceConfirmationDraftMissingField.entries.toSet(),
            observed.single().missingFields
        )
        assertEquals(observed.single(), session.currentFinalizationOrNull())
    }

    @Test
    fun `hidden route ignores edits and re-show preserves only prior visible draft state`() {
        val observed = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val session = session(observed)

        session.onArtifactReferenceChanged(
            artifactId = "hidden-artifact",
            proofType = UserProvidedPriceProofType.RECEIPT
        )
        session.onRouteVisibilityChanged(true)
        assertTrue(
            UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID in
                observed.last().missingFields
        )

        session.onArtifactReferenceChanged(
            artifactId = "visible-artifact",
            proofType = UserProvidedPriceProofType.PRICE_TAG
        )
        val visibleState = observed.last()
        assertFalse(
            UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID in
                visibleState.missingFields
        )

        session.onRouteVisibilityChanged(false)
        assertNull(session.currentFinalizationOrNull())
        session.onProductChanged(
            observationId = "hidden-observation",
            rawGtin = "123",
            productName = "hidden-product"
        )
        assertEquals(2, observed.size)

        session.onRouteVisibilityChanged(true)
        val restored = observed.last()
        assertFalse(
            UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID in
                restored.missingFields
        )
        assertTrue(
            UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID in
                restored.missingFields
        )
    }

    @Test
    fun `complete visible draft exposes exact submission without semantic validation`() {
        val observed = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val session = session(observed)
        val price = Money(0L, "CAD")
        val storeScope = exactStoreScope()

        session.onRouteVisibilityChanged(true)
        session.onArtifactReferenceChanged(
            artifactId = "  artifact-001  ",
            proofType = UserProvidedPriceProofType.PRICE_TAG
        )
        session.onProductChanged(
            observationId = "",
            rawGtin = "123",
            productName = ""
        )
        session.onPriceChanged(price)
        session.onStoreScopeChanged(storeScope)
        session.onObservedAtChanged(20_000L)
        session.onConfirmationChanged(
            confirmationId = "",
            confirmedAtEpochMillis = 10_000L
        )

        val finalization = requireNotNull(session.currentFinalizationOrNull())
        val submission = requireNotNull(session.currentSubmissionOrNull())

        assertTrue(finalization.complete)
        assertTrue(finalization.missingFields.isEmpty())
        assertEquals("  artifact-001  ", submission.artifactId)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, submission.proofType)
        assertEquals("", submission.fields.observationId)
        assertEquals("123", submission.fields.rawGtin)
        assertEquals("", submission.fields.productName)
        assertSame(price, submission.fields.price)
        assertSame(storeScope, submission.fields.storeScope)
        assertEquals(20_000L, submission.fields.observedAtEpochMillis)
        assertEquals("", submission.fields.confirmationId)
        assertEquals(10_000L, submission.fields.confirmedAtEpochMillis)

        val artifact =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = submission.artifactId,
                        proofType = submission.proofType,
                        artifactBytes = "proof".toByteArray()
                    )
                    .artifact
            )
        val downstream =
            UserConfirmedObservedPrice.confirm(
                UserObservedPriceConfirmationInput(
                    artifact = artifact,
                    observationId = submission.fields.observationId,
                    rawGtin = submission.fields.rawGtin,
                    productName = submission.fields.productName,
                    price = submission.fields.price,
                    storeScope = submission.fields.storeScope,
                    observedAtEpochMillis = submission.fields.observedAtEpochMillis,
                    confirmationId = submission.fields.confirmationId,
                    confirmedAtEpochMillis = submission.fields.confirmedAtEpochMillis
                )
            )

        assertFalse(downstream.accepted)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_OBSERVATION_ID in downstream.failures)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_GTIN in downstream.failures)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_PRODUCT_NAME in downstream.failures)
        assertTrue(UserObservedPriceConfirmationFailure.NON_POSITIVE_PRICE in downstream.failures)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_CONFIRMATION_ID in downstream.failures)
        assertTrue(
            UserObservedPriceConfirmationFailure.CONFIRMATION_PRECEDES_OBSERVATION in
                downstream.failures
        )
    }

    @Test
    fun `observer receives a new immutable finalization after each visible typed edit`() {
        val observed = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val session = session(observed)

        session.onRouteVisibilityChanged(true)
        val initial = observed.single()
        session.onPriceChanged(Money(599L, "CAD"))
        val afterPrice = observed.last()
        session.onObservedAtChanged(50_000L)
        val afterTime = observed.last()

        assertEquals(3, observed.size)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.PRICE in initial.missingFields)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.PRICE in afterPrice.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT in afterPrice.missingFields)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.OBSERVED_AT in afterTime.missingFields)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.PRICE in initial.missingFields)
    }

    @Test
    fun `close clears route draft and permanently suppresses visibility edits and reads`() {
        val observed = mutableListOf<UserObservedPriceConfirmationDraftFinalization>()
        val session = session(observed)

        session.onRouteVisibilityChanged(true)
        session.onArtifactReferenceChanged(
            artifactId = "artifact-001",
            proofType = UserProvidedPriceProofType.PRICE_TAG
        )
        assertEquals(2, observed.size)

        session.close()

        assertTrue(session.isClosed())
        assertFalse(session.isVisible())
        assertNull(session.currentFinalizationOrNull())
        assertNull(session.currentSubmissionOrNull())

        session.onRouteVisibilityChanged(true)
        session.onArtifactReferenceChanged(
            artifactId = "artifact-002",
            proofType = UserProvidedPriceProofType.RECEIPT
        )
        session.onProductChanged("obs", "4006381333931", "Milk")
        assertEquals(2, observed.size)
        assertNull(session.currentFinalizationOrNull())
    }

    @Test
    fun `route session retains no proof bytes and owns no execution semantic or Android authority`() {
        assertTrue(
            UserObservedPriceConfirmationDraftRouteSession::class.java.declaredFields.none {
                field -> field.type == ByteArray::class.java
            }
        )

        val source = source("UserObservedPriceConfirmationDraftRouteSession.kt").readText()
        listOf(
            "ByteArray",
            "UserObservedPriceConfirmationAndroidSession",
            "UserObservedPriceConfirmationExecutionHost",
            "UserObservedPriceConfirmationTransaction(",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserConfirmedObservedPrice.confirm",
            "GtinValidation",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "CURRENT_PRICE",
            "OcrScanner",
            "android.content.Context",
            "android.view.",
            "android.widget.",
            "MainActivity",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Route draft session must not own $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("UserObservedPriceConfirmationDraft.start()"))
        assertTrue(source.contains("UserObservedPriceConfirmationDraftFinalizer.finalize(current)"))
        assertTrue(source.contains("if (closed || !routeVisible) return"))
        assertTrue(source.contains("draft = null"))
    }

    private fun session(
        observed: MutableList<UserObservedPriceConfirmationDraftFinalization>
    ): UserObservedPriceConfirmationDraftRouteSession =
        UserObservedPriceConfirmationDraftRouteSession(
            observer = UserObservedPriceConfirmationDraftObserver(observed::add)
        )

    private fun exactStoreScope(): PracticalShoppingStoreIdentityScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-a",
            locationKey = "location-a",
            commerceChannelKey = "IN_STORE"
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

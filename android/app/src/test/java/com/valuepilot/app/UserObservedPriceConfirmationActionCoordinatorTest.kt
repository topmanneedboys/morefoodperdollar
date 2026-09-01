package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationActionCoordinatorTest {

    @Test
    fun `incomplete draft fails closed before metadata capture or target`() {
        var metadataCaptures = 0
        var targetCalls = 0
        val rig =
            rig(
                metadataSource =
                    UserObservedPriceConfirmationLifecycleMetadataSource {
                        metadataCaptures += 1
                        metadata("confirmation-unexpected", 20_000L)
                    },
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { _, _ ->
                        targetCalls += 1
                        true
                    }
            )

        rig.openRoute()
        rig.selectProof(byteArrayOf(1, 2, 3))

        assertFalse(rig.action.submit())
        assertEquals(0, metadataCaptures)
        assertEquals(0, targetCalls)
    }

    @Test
    fun `missing proof fails closed without consuming confirmation metadata`() {
        var metadataCaptures = 0
        var targetCalls = 0
        val rig =
            rig(
                metadataSource =
                    UserObservedPriceConfirmationLifecycleMetadataSource {
                        metadataCaptures += 1
                        metadata("confirmation-unexpected", 20_000L)
                    },
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { _, _ ->
                        targetCalls += 1
                        true
                    }
            )

        rig.openRoute()
        rig.completeObservationInputs()

        assertEquals(
            setOf(
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            requireNotNull(rig.route.currentFinalizationOrNull()).missingFields
        )
        assertFalse(rig.action.submit())
        assertEquals(0, metadataCaptures)
        assertEquals(0, targetCalls)
    }

    @Test
    fun `explicit action forwards fresh one-shot submission and defensive proof snapshot`() {
        var metadataCaptures = 0
        val selectedBytes = byteArrayOf(1, 3, 5, 7)
        var capturedSubmission: UserObservedPriceConfirmationDraftSubmission? = null
        var capturedBytes: ByteArray? = null
        val rig =
            rig(
                metadataSource =
                    UserObservedPriceConfirmationLifecycleMetadataSource {
                        metadataCaptures += 1
                        metadata("confirmation-001", 10_001L)
                    },
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { submission, bytes ->
                        capturedSubmission = submission
                        capturedBytes = bytes
                        true
                    }
            )

        rig.openRoute()
        rig.completeObservationInputs()
        rig.selectProof(selectedBytes)

        assertTrue(rig.action.submit())

        assertEquals(1, metadataCaptures)
        val submission = requireNotNull(capturedSubmission)
        assertEquals("artifact-001", submission.artifactId)
        assertEquals(UserProvidedPriceProofType.RECEIPT, submission.proofType)
        assertEquals("observation-001", submission.fields.observationId)
        assertEquals(Money(599L, "CAD"), submission.fields.price)
        assertEquals(10_000L, submission.fields.observedAtEpochMillis)
        assertEquals("confirmation-001", submission.fields.confirmationId)
        assertEquals(10_001L, submission.fields.confirmedAtEpochMillis)
        assertEquals(selectedBytes.toList(), requireNotNull(capturedBytes).toList())
        assertNotSame(selectedBytes, capturedBytes)

        val after = requireNotNull(rig.route.currentFinalizationOrNull())
        assertEquals(
            setOf(
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            after.missingFields
        )
        assertFalse(after.complete)
    }

    @Test
    fun `target rejection leaves draft editable and retry binds fresh metadata latest fields and proof`() {
        var metadataCaptures = 0
        val submissions = mutableListOf<UserObservedPriceConfirmationDraftSubmission>()
        val byteSnapshots = mutableListOf<List<Byte>>()
        val rig =
            rig(
                metadataSource =
                    UserObservedPriceConfirmationLifecycleMetadataSource {
                        metadataCaptures += 1
                        metadata(
                            confirmationId = "confirmation-$metadataCaptures",
                            confirmedAtEpochMillis = 20_000L + metadataCaptures
                        )
                    },
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { submission, bytes ->
                        submissions += submission
                        byteSnapshots += bytes.toList()
                        submissions.size > 1
                    }
            )

        rig.openRoute()
        rig.completeObservationInputs()
        rig.selectProof(byteArrayOf(1, 2, 3))

        assertFalse(rig.action.submit())
        assertEquals(1, metadataCaptures)
        assertEquals("confirmation-1", submissions.single().fields.confirmationId)
        assertEquals(Money(599L, "CAD"), submissions.single().fields.price)
        assertEquals("artifact-001", submissions.single().artifactId)
        assertEquals(listOf<Byte>(1, 2, 3), byteSnapshots.single())

        rig.route.onPriceInput(Money(699L, "USD"))
        rig.route.onProofReferenceInput("artifact-002", UserProvidedPriceProofType.PRICE_TAG)
        rig.selectProof(byteArrayOf(9, 8, 7, 6))

        assertTrue(rig.action.submit())
        assertEquals(2, metadataCaptures)
        assertEquals(2, submissions.size)
        val retry = submissions.last()
        assertEquals("confirmation-2", retry.fields.confirmationId)
        assertEquals(Money(699L, "USD"), retry.fields.price)
        assertEquals("artifact-002", retry.artifactId)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, retry.proofType)
        assertEquals(listOf<Byte>(9, 8, 7, 6), byteSnapshots.last())

        assertEquals(
            setOf(
                UserObservedPriceConfirmationDraftMissingField.CONFIRMATION_ID,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            requireNotNull(rig.route.currentFinalizationOrNull()).missingFields
        )
    }

    @Test
    fun `route exit and proof exit fail closed without metadata or target activity`() {
        var metadataCaptures = 0
        var targetCalls = 0
        val rig =
            rig(
                metadataSource =
                    UserObservedPriceConfirmationLifecycleMetadataSource {
                        metadataCaptures += 1
                        metadata("confirmation-unexpected", 20_000L)
                    },
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { _, _ ->
                        targetCalls += 1
                        true
                    }
            )

        rig.openRoute()
        rig.completeObservationInputs()
        rig.selectProof(byteArrayOf(4, 5, 6))
        rig.route.onRouteVisibilityChanged(false)
        rig.proof.onRouteVisibilityChanged(false)

        assertFalse(rig.action.submit())
        assertEquals(0, metadataCaptures)
        assertEquals(0, targetCalls)
    }

    @Test
    fun `action boundary owns no UI parsing storage semantic validation evidence ranking or networking authority`() {
        assertTrue(
            UserObservedPriceConfirmationActionCoordinator::class.java.declaredFields.none {
                field -> field.type == ByteArray::class.java
            }
        )

        val source = source("UserObservedPriceConfirmationActionCoordinator.kt").readText()
        listOf(
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "ContentResolver",
            "Uri",
            "openInputStream",
            "UserObservedPriceProofStreamReader",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserConfirmedObservedPrice.confirm",
            "UserObservedPriceConfirmationTransaction(",
            "onConfirmationChanged(",
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
            assertFalse("Confirmation action must not own $forbidden", source.contains(forbidden))
        }

        listOf(
            "routeCoordinator.currentFinalizationOrNull()",
            "finalization.missingFields != CONFIRMATION_ONLY_MISSING_FIELDS",
            "proofContentCoordinator.selectedContentSnapshotOrNull()",
            "metadataSource.capture()",
            "routeCoordinator.currentSubmissionWithConfirmationOrNull(metadata)",
            "target.submit("
        ).forEach { required ->
            assertTrue("Expected narrow action composition $required", source.contains(required))
        }
    }

    private data class Rig(
        val route: UserObservedPriceSavedConfirmationDraftRouteCoordinator,
        val proof: UserObservedPriceConfirmationDraftProofContentSelectionCoordinator,
        val action: UserObservedPriceConfirmationActionCoordinator,
        val acceptedAttempt: UserObservedPriceSavedPrefillHandoffAttempt
    ) {
        fun openRoute() {
            route.onAttempt(acceptedAttempt)
            route.onRouteVisibilityChanged(true)
            proof.onRouteVisibilityChanged(true)
        }

        fun completeObservationInputs() {
            route.onPriceInput(Money(599L, "CAD"))
            route.onProofReferenceInput("artifact-001", UserProvidedPriceProofType.RECEIPT)
            route.onObservedAtInput(10_000L)
        }

        fun selectProof(bytes: ByteArray) {
            proof.onContentReadResult(UserObservedPriceProofContentReadResult(bytes = bytes))
        }
    }

    private fun rig(
        metadataSource: UserObservedPriceConfirmationLifecycleMetadataSource,
        target: UserObservedPriceConfirmationDraftSubmissionTarget
    ): Rig {
        val route =
            UserObservedPriceSavedConfirmationDraftRouteCoordinator(
                routeOpenObserver = UserObservedPriceConfirmationDraftRouteOpenObserver { },
                sessionFactory = { UserObservedPriceConfirmationDraftRouteSession() },
                observationIdSource =
                    UserObservedPriceObservationIdSource { "observation-001" }
            )
        val proof =
            UserObservedPriceConfirmationDraftProofContentSelectionCoordinator(
                requestForegroundSelection = { },
                observer = UserObservedPriceConfirmationDraftProofContentSelectionObserver { }
            )
        val action =
            UserObservedPriceConfirmationActionCoordinator(
                routeCoordinator = route,
                proofContentCoordinator = proof,
                target = target,
                metadataSource = metadataSource
            )
        return Rig(
            route = route,
            proof = proof,
            action = action,
            acceptedAttempt = acceptedAttempt()
        )
    }

    private fun metadata(
        confirmationId: String,
        confirmedAtEpochMillis: Long
    ): UserObservedPriceConfirmationLifecycleMetadata =
        UserObservedPriceConfirmationLifecycleMetadata(
            confirmationId = confirmationId,
            confirmedAtEpochMillis = confirmedAtEpochMillis
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

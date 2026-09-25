package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceProofReadSubmissionGateTest {

    @Test
    fun `successful read forwards same transient bytes through complete visible draft`() {
        val session = completeVisibleSession()
        val proofBytes = byteArrayOf(3, 1, 4, 1, 5)
        var capturedBytes: ByteArray? = null
        var targetCalls = 0
        val gate =
            UserObservedPriceProofReadSubmissionGate(
                submissionHandoff =
                    UserObservedPriceConfirmationDraftSubmissionHandoff(
                        routeSession = session,
                        target =
                            UserObservedPriceConfirmationDraftSubmissionTarget { _, bytes ->
                                targetCalls += 1
                                capturedBytes = bytes
                                true
                            }
                    )
            )

        val result =
            gate.submit(
                UserObservedPriceProofContentReadResult(bytes = proofBytes)
            )

        assertEquals(UserObservedPriceProofReadSubmissionResult.Submitted, result)
        assertEquals(1, targetCalls)
        assertSame(proofBytes, capturedBytes)
        assertTrue(session.isVisible())
        assertFalse(session.isClosed())
    }

    @Test
    fun `content rejection preserves exact typed issue and never touches submission target`() {
        val session = completeVisibleSession()
        var targetCalls = 0
        val gate =
            UserObservedPriceProofReadSubmissionGate(
                UserObservedPriceConfirmationDraftSubmissionHandoff(
                    routeSession = session,
                    target =
                        UserObservedPriceConfirmationDraftSubmissionTarget { _, _ ->
                            targetCalls += 1
                            true
                        }
                )
            )

        UserObservedPriceProofContentReadIssue.entries.forEach { issue ->
            val result =
                gate.submit(
                    UserObservedPriceProofContentReadResult(
                        bytes = null,
                        issue = issue
                    )
                )

            assertEquals(
                UserObservedPriceProofReadSubmissionResult.ContentRejected(issue),
                result
            )
        }

        assertEquals(0, targetCalls)
        assertTrue(session.isVisible())
    }

    @Test
    fun `readable proof remains rejected when existing route handoff is unavailable`() {
        val proofBytes = byteArrayOf(9, 2, 6)
        var targetCalls = 0
        val session = completeVisibleSession().also {
            it.onRouteVisibilityChanged(false)
        }
        val gate =
            UserObservedPriceProofReadSubmissionGate(
                UserObservedPriceConfirmationDraftSubmissionHandoff(
                    routeSession = session,
                    target =
                        UserObservedPriceConfirmationDraftSubmissionTarget { _, _ ->
                            targetCalls += 1
                            true
                        }
                )
            )

        val result =
            gate.submit(
                UserObservedPriceProofContentReadResult(bytes = proofBytes)
            )

        assertEquals(UserObservedPriceProofReadSubmissionResult.SubmissionRejected, result)
        assertEquals(0, targetCalls)
        assertEquals(byteArrayOf(9, 2, 6).toList(), proofBytes.toList())
        assertFalse(session.isVisible())
        assertFalse(session.isClosed())
    }

    @Test
    fun `downstream target rejection propagates without mutating proof bytes or route`() {
        val session = completeVisibleSession()
        val expectedSubmission = requireNotNull(session.currentSubmissionOrNull())
        val proofBytes = byteArrayOf(8, 5, 3)
        var capturedSubmission: UserObservedPriceConfirmationDraftSubmission? = null
        var capturedBytes: ByteArray? = null
        val gate =
            UserObservedPriceProofReadSubmissionGate(
                UserObservedPriceConfirmationDraftSubmissionHandoff(
                    routeSession = session,
                    target =
                        UserObservedPriceConfirmationDraftSubmissionTarget { submission, bytes ->
                            capturedSubmission = submission
                            capturedBytes = bytes
                            false
                        }
                )
            )

        val result =
            gate.submit(
                UserObservedPriceProofContentReadResult(bytes = proofBytes)
            )

        assertEquals(UserObservedPriceProofReadSubmissionResult.SubmissionRejected, result)
        assertEquals(expectedSubmission, capturedSubmission)
        assertSame(proofBytes, capturedBytes)
        assertEquals(byteArrayOf(8, 5, 3).toList(), proofBytes.toList())
        assertEquals(expectedSubmission, session.currentSubmissionOrNull())
        assertTrue(session.isVisible())
        assertFalse(session.isClosed())
    }

    @Test
    fun `gate retains no raw proof and owns no picker storage semantic ranking or UI authority`() {
        assertTrue(
            UserObservedPriceProofReadSubmissionGate::class.java.declaredFields.none { field ->
                field.type == ByteArray::class.java
            }
        )

        val source = source("UserObservedPriceProofReadSubmissionGate.kt").readText()

        listOf(
            "val bytes = readResult.bytes",
            "requireNotNull(readResult.issue)",
            "submissionHandoff.submit(bytes)",
            "UserObservedPriceProofReadSubmissionResult.ContentRejected",
            "UserObservedPriceProofReadSubmissionResult.SubmissionRejected"
        ).forEach { required ->
            assertTrue("Expected read-submission gate boundary $required", source.contains(required))
        }

        listOf(
            "android.net.Uri",
            "ContentResolver",
            "registerForActivityResult",
            "ActivityResultContracts",
            "takePersistableUriPermission",
            "android.content.Intent",
            "android.content.Context",
            "android.app.Activity",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "copyOf()",
            "fill(0)",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserObservedPriceConfirmationTransaction(",
            "UserConfirmedObservedPrice.confirm",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "CURRENT_PRICE",
            "ProductionBestValue",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "Bitmap",
            "Camera",
            "android.view.",
            "android.widget.",
            "MainActivity",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Read-submission gate must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun completeVisibleSession(): UserObservedPriceConfirmationDraftRouteSession =
        UserObservedPriceConfirmationDraftRouteSession().also { session ->
            session.onRouteVisibilityChanged(true)
            session.onArtifactReferenceChanged(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.RECEIPT
            )
            session.onProductChanged(
                observationId = "observation-001",
                rawGtin = "4006381333931",
                productName = "Milk"
            )
            session.onPriceChanged(Money(599L, "CAD"))
            session.onStoreScopeChanged(
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "merchant-a",
                    locationKey = "location-a",
                    commerceChannelKey = "IN_STORE"
                )
            )
            session.onObservedAtChanged(10_000L)
            session.onConfirmationChanged(
                confirmationId = "confirmation-001",
                confirmedAtEpochMillis = 10_001L
            )
        }

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

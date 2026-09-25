package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftSubmissionHandoffTest {

    @Test
    fun `complete visible route forwards exact submission and same transient proof bytes`() {
        val session = completeVisibleSession()
        val expected = requireNotNull(session.currentSubmissionOrNull())
        val proofBytes = byteArrayOf(1, 2, 3, 4)
        var capturedSubmission: UserObservedPriceConfirmationDraftSubmission? = null
        var capturedBytes: ByteArray? = null
        val handoff =
            UserObservedPriceConfirmationDraftSubmissionHandoff(
                routeSession = session,
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { submission, bytes ->
                        capturedSubmission = submission
                        capturedBytes = bytes
                        true
                    }
            )

        assertTrue(handoff.submit(proofBytes))

        assertEquals(expected, capturedSubmission)
        assertSame(proofBytes, capturedBytes)
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), proofBytes.toList())
        assertTrue(session.isVisible())
        assertFalse(session.isClosed())
        assertEquals(expected, session.currentSubmissionOrNull())
    }

    @Test
    fun `incomplete hidden and closed routes fail closed without touching target`() {
        var targetCalls = 0
        val target =
            UserObservedPriceConfirmationDraftSubmissionTarget { _, _ ->
                targetCalls += 1
                true
            }
        val proofBytes = byteArrayOf(8, 9)

        val incomplete = UserObservedPriceConfirmationDraftRouteSession()
        incomplete.onRouteVisibilityChanged(true)
        val incompleteHandoff =
            UserObservedPriceConfirmationDraftSubmissionHandoff(incomplete, target)
        assertFalse(incompleteHandoff.submit(proofBytes))

        val hidden = completeVisibleSession()
        hidden.onRouteVisibilityChanged(false)
        val hiddenHandoff = UserObservedPriceConfirmationDraftSubmissionHandoff(hidden, target)
        assertFalse(hiddenHandoff.submit(proofBytes))

        val closed = completeVisibleSession()
        closed.close()
        val closedHandoff = UserObservedPriceConfirmationDraftSubmissionHandoff(closed, target)
        assertFalse(closedHandoff.submit(proofBytes))

        assertEquals(0, targetCalls)
        assertEquals(byteArrayOf(8, 9).toList(), proofBytes.toList())
    }

    @Test
    fun `target rejection propagates without hiding closing or mutating complete route`() {
        val session = completeVisibleSession()
        val before = requireNotNull(session.currentSubmissionOrNull())
        val proofBytes = byteArrayOf(4, 3, 2, 1)
        var targetCalls = 0
        val handoff =
            UserObservedPriceConfirmationDraftSubmissionHandoff(
                routeSession = session,
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { submission, bytes ->
                        targetCalls += 1
                        assertEquals(before, submission)
                        assertSame(proofBytes, bytes)
                        false
                    }
            )

        assertFalse(handoff.submit(proofBytes))

        assertEquals(1, targetCalls)
        assertTrue(session.isVisible())
        assertFalse(session.isClosed())
        assertEquals(before, session.currentSubmissionOrNull())
        assertEquals(byteArrayOf(4, 3, 2, 1).toList(), proofBytes.toList())
    }

    @Test
    fun `handoff does not usurp downstream semantic validation`() {
        val session = UserObservedPriceConfirmationDraftRouteSession()
        val price = Money(0L, "CAD")
        val storeScope = exactStoreScope()
        val proofBytes = "proof".toByteArray()
        var captured: UserObservedPriceConfirmationDraftSubmission? = null

        session.onRouteVisibilityChanged(true)
        session.onArtifactReferenceChanged("  artifact-001  ", UserProvidedPriceProofType.PRICE_TAG)
        session.onProductChanged("", "123", "")
        session.onPriceChanged(price)
        session.onStoreScopeChanged(storeScope)
        session.onObservedAtChanged(20_000L)
        session.onConfirmationChanged("", 10_000L)

        val handoff =
            UserObservedPriceConfirmationDraftSubmissionHandoff(
                routeSession = session,
                target =
                    UserObservedPriceConfirmationDraftSubmissionTarget { submission, bytes ->
                        captured = submission
                        assertSame(proofBytes, bytes)
                        true
                    }
            )

        assertTrue(handoff.submit(proofBytes))
        val forwarded = assertNotNull(captured).let { requireNotNull(captured) }
        assertEquals("  artifact-001  ", forwarded.artifactId)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, forwarded.proofType)
        assertEquals("", forwarded.fields.observationId)
        assertEquals("123", forwarded.fields.rawGtin)
        assertEquals("", forwarded.fields.productName)
        assertSame(price, forwarded.fields.price)
        assertSame(storeScope, forwarded.fields.storeScope)
        assertEquals(20_000L, forwarded.fields.observedAtEpochMillis)
        assertEquals("", forwarded.fields.confirmationId)
        assertEquals(10_000L, forwarded.fields.confirmedAtEpochMillis)
    }

    @Test
    fun `Android target adapter only unwraps typed submission into existing session submit`() {
        val source = source("UserObservedPriceConfirmationDraftSubmissionHandoff.kt").readText()
        val adapterStart =
            source.indexOf("internal class UserObservedPriceConfirmationAndroidDraftSubmissionTarget")
        val handoffStart =
            source.indexOf("internal class UserObservedPriceConfirmationDraftSubmissionHandoff")
        val adapter = source.substring(adapterStart, handoffStart)

        assertTrue(adapterStart >= 0)
        assertTrue(handoffStart > adapterStart)
        listOf(
            "session.submit(",
            "artifactId = submission.artifactId",
            "proofType = submission.proofType",
            "artifactBytes = artifactBytes",
            "fields = submission.fields"
        ).forEach { required ->
            assertTrue("Expected exact Android target forwarding $required", adapter.contains(required))
        }
        assertFalse(adapter.contains("copyOf()"))
        assertFalse(adapter.contains("fill(0)"))
    }

    @Test
    fun `handoff owns no proof storage semantic clock ranking or UI authority`() {
        assertTrue(
            UserObservedPriceConfirmationDraftSubmissionHandoff::class.java.declaredFields.none {
                field -> field.type == ByteArray::class.java
            }
        )

        val source = source("UserObservedPriceConfirmationDraftSubmissionHandoff.kt").readText()
        listOf(
            "copyOf()",
            "fill(0)",
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
            assertFalse("Draft submission handoff must not own $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("routeSession.currentSubmissionOrNull() ?: return false"))
        assertTrue(source.contains("return target.submit("))
        assertFalse(source.contains("routeSession.close()"))
        assertFalse(source.contains("onRouteVisibilityChanged("))
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
            session.onStoreScopeChanged(exactStoreScope())
            session.onObservedAtChanged(10_000L)
            session.onConfirmationChanged(
                confirmationId = "confirmation-001",
                confirmedAtEpochMillis = 10_001L
            )
        }

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

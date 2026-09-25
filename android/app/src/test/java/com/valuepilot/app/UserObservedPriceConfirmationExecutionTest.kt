package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationExecutionTest {

    @Test
    fun `submission snapshots proof bytes runs off owner path and wipes working copy after gateway`() {
        val callerBytes = "price-tag-image-bytes".toByteArray()
        val expectedBytes = callerBytes.copyOf()
        val gateway = RecordingGateway(rejectedResult())
        val worker = QueuedWorker()
        val dispatcher = QueuedDispatcher()
        val completions = mutableListOf<UserObservedPriceConfirmationCompletion>()
        val host = host(gateway, worker, dispatcher, completions)

        assertTrue(
            host.submit(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.PRICE_TAG,
                artifactBytes = callerBytes,
                fields = validFields()
            )
        )
        assertTrue(host.isBusy())
        assertEquals(0, gateway.callCount)
        assertEquals(1, worker.pendingCount)

        callerBytes.fill(9)
        worker.runNext()

        assertEquals(1, gateway.callCount)
        assertArrayEquals(expectedBytes, gateway.bytesObservedDuringCall)
        assertTrue(requireNotNull(gateway.rawBytesReference).all { it == 0.toByte() })
        assertTrue(host.isBusy())
        assertEquals(1, dispatcher.pendingCount)
        assertTrue(completions.isEmpty())

        dispatcher.runNext()

        assertFalse(host.isBusy())
        assertEquals(1, completions.size)
        val outcome = completions.single().outcome
        assertTrue(outcome is UserObservedPriceConfirmationExecutionOutcome.Completed)
        assertSame(
            gateway.result,
            (outcome as UserObservedPriceConfirmationExecutionOutcome.Completed).result
        )
    }

    @Test
    fun `only one request is accepted until its completion returns to owner`() {
        val gateway = RecordingGateway(rejectedResult())
        val worker = QueuedWorker()
        val dispatcher = QueuedDispatcher()
        val completions = mutableListOf<UserObservedPriceConfirmationCompletion>()
        val host = host(gateway, worker, dispatcher, completions)

        assertTrue(submit(host, "artifact-001"))
        assertFalse(submit(host, "artifact-002"))
        assertEquals(1, worker.pendingCount)

        worker.runNext()
        assertFalse(submit(host, "artifact-003"))

        dispatcher.runNext()
        assertTrue(submit(host, "artifact-004"))
        assertEquals(1, worker.pendingCount)
    }

    @Test
    fun `close allows queued atomic work to finish but suppresses late completion and future submissions`() {
        val gateway = RecordingGateway(rejectedResult())
        val worker = QueuedWorker()
        val dispatcher = QueuedDispatcher()
        val completions = mutableListOf<UserObservedPriceConfirmationCompletion>()
        val host = host(gateway, worker, dispatcher, completions)

        assertTrue(submit(host, "artifact-001"))
        host.close()

        assertTrue(host.isClosed())
        assertFalse(host.isBusy())
        assertFalse(submit(host, "artifact-002"))

        worker.runNext()
        assertEquals(1, gateway.callCount)
        assertTrue(requireNotNull(gateway.rawBytesReference).all { it == 0.toByte() })
        dispatcher.runNext()

        assertTrue(completions.isEmpty())
    }

    @Test
    fun `unexpected gateway failure becomes typed failed completion without exception text`() {
        val gateway =
            UserObservedPriceConfirmationGateway { _, _, _, _ ->
                throw IllegalStateException("sensitive-storage-detail")
            }
        val worker = QueuedWorker()
        val dispatcher = QueuedDispatcher()
        val completions = mutableListOf<UserObservedPriceConfirmationCompletion>()
        val host = host(gateway, worker, dispatcher, completions)

        assertTrue(submit(host, "artifact-001"))
        worker.runNext()
        dispatcher.runNext()

        assertFalse(host.isBusy())
        assertEquals(1, completions.size)
        assertSame(
            UserObservedPriceConfirmationExecutionOutcome.Failed,
            completions.single().outcome
        )
    }

    @Test
    fun `scheduler rejection does not wedge host and permits a later submission`() {
        var reject = true
        val queued = QueuedWorker()
        val worker =
            UserObservedPriceConfirmationWorkScheduler { block ->
                if (reject) {
                    throw IllegalStateException("scheduler unavailable")
                }
                queued.schedule(block)
            }
        val dispatcher = QueuedDispatcher()
        val completions = mutableListOf<UserObservedPriceConfirmationCompletion>()
        val host =
            UserObservedPriceConfirmationExecutionHost(
                gateway = RecordingGateway(rejectedResult()),
                worker = worker,
                completionDispatcher = dispatcher,
                completionListener = UserObservedPriceConfirmationCompletionListener(completions::add)
            )

        assertFalse(submit(host, "artifact-001"))
        assertFalse(host.isBusy())

        reject = false
        assertTrue(submit(host, "artifact-002"))
        assertTrue(host.isBusy())
        assertEquals(1, queued.pendingCount)
    }

    @Test
    fun `dispatcher failure clears active request without delivering completion`() {
        val gateway = RecordingGateway(rejectedResult())
        val worker = QueuedWorker()
        val dispatcher =
            UserObservedPriceConfirmationCompletionDispatcher {
                throw IllegalStateException("owner unavailable")
            }
        val completions = mutableListOf<UserObservedPriceConfirmationCompletion>()
        val host =
            UserObservedPriceConfirmationExecutionHost(
                gateway = gateway,
                worker = worker,
                completionDispatcher = dispatcher,
                completionListener = UserObservedPriceConfirmationCompletionListener(completions::add)
            )

        assertTrue(submit(host, "artifact-001"))
        worker.runNext()

        assertFalse(host.isBusy())
        assertTrue(completions.isEmpty())
        assertTrue(submit(host, "artifact-002"))
    }

    @Test
    fun `execution host owns sequencing only and never stores proof bytes or semantic authority`() {
        val source = source("UserObservedPriceConfirmationExecution.kt").readText()

        listOf(
            "artifactBytes.copyOf()",
            "proofBytes.fill(0)",
            "activeRequestId",
            "completionDispatcher.dispatch",
            "gateway.confirmAndRetain"
        ).forEach { required ->
            assertTrue("Expected execution boundary $required", source.contains(required))
        }

        assertTrue(
            UserObservedPriceConfirmationExecutionHost::class.java.declaredFields.none {
                it.type == ByteArray::class.java
            }
        )

        listOf(
            "android.content.Context",
            "android.os.",
            "System.currentTimeMillis",
            "UUID",
            "UserProvidedPriceProofArtifactLocalStore(",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "UserObservedPriceUnitValueSurface",
            "MainActivity",
            "OcrScanner.",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Execution host must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun host(
        gateway: UserObservedPriceConfirmationGateway,
        worker: UserObservedPriceConfirmationWorkScheduler,
        dispatcher: UserObservedPriceConfirmationCompletionDispatcher,
        completions: MutableList<UserObservedPriceConfirmationCompletion>
    ): UserObservedPriceConfirmationExecutionHost =
        UserObservedPriceConfirmationExecutionHost(
            gateway = gateway,
            worker = worker,
            completionDispatcher = dispatcher,
            completionListener = UserObservedPriceConfirmationCompletionListener(completions::add)
        )

    private fun submit(
        host: UserObservedPriceConfirmationExecutionHost,
        artifactId: String
    ): Boolean =
        host.submit(
            artifactId = artifactId,
            proofType = UserProvidedPriceProofType.PRICE_TAG,
            artifactBytes = "proof-$artifactId".toByteArray(),
            fields = validFields()
        )

    private fun validFields(): UserObservedPriceConfirmationFields =
        UserObservedPriceConfirmationFields(
            observationId = "obs-001",
            rawGtin = "4006381333931",
            productName = "Test Milk",
            price = Money(599L, "CAD"),
            storeScope =
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "merchant-a",
                    locationKey = "location-a",
                    commerceChannelKey = "IN_STORE"
                ),
            observedAtEpochMillis = 10_000L,
            confirmationId = "confirm-001",
            confirmedAtEpochMillis = 20_000L
        )

    private fun rejectedResult(): UserObservedPriceConfirmationTransactionResult =
        UserObservedPriceConfirmationTransactionResult(
            confirmation = null,
            artifactFailures = setOf(UserProvidedPriceArtifactFailure.EMPTY_ARTIFACT)
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

    private class RecordingGateway(
        val result: UserObservedPriceConfirmationTransactionResult
    ) : UserObservedPriceConfirmationGateway {
        var callCount: Int = 0
        var bytesObservedDuringCall: ByteArray? = null
        var rawBytesReference: ByteArray? = null

        override fun confirmAndRetain(
            artifactId: String,
            proofType: UserProvidedPriceProofType,
            artifactBytes: ByteArray,
            fields: UserObservedPriceConfirmationFields
        ): UserObservedPriceConfirmationTransactionResult {
            callCount += 1
            bytesObservedDuringCall = artifactBytes.copyOf()
            rawBytesReference = artifactBytes
            return result
        }
    }

    private class QueuedWorker : UserObservedPriceConfirmationWorkScheduler {
        private val blocks = ArrayDeque<() -> Unit>()
        val pendingCount: Int
            get() = blocks.size

        override fun schedule(block: () -> Unit) {
            blocks.addLast(block)
        }

        fun runNext() {
            blocks.removeFirst().invoke()
        }
    }

    private class QueuedDispatcher : UserObservedPriceConfirmationCompletionDispatcher {
        private val blocks = ArrayDeque<() -> Unit>()
        val pendingCount: Int
            get() = blocks.size

        override fun dispatch(block: () -> Unit) {
            blocks.addLast(block)
        }

        fun runNext() {
            blocks.removeFirst().invoke()
        }
    }
}

package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private typealias ActionStatus = UserObservedPriceConfirmationActionUiState.Status

class UserObservedPriceConfirmationActionPresentationControllerTest {

    @Test
    fun `accepted action is pending until typed transaction completion succeeds`() {
        val renderer = RecordingRenderer()
        var submits = 0
        val controller =
            controller(renderer) {
                submits += 1
                true
            }

        controller.onRouteVisibilityChanged(true)
        controller.onSubmitRequested()

        assertEquals(1, submits)
        assertEquals(ActionStatus.SUBMITTING, renderer.latest.status)
        assertFalse(renderer.latest.actionEnabled)
        assertTrue(controller.isSubmitting())

        controller.onCompleted(completion(acceptedResult()))

        assertEquals(ActionStatus.CONFIRMED, renderer.latest.status)
        assertFalse(renderer.latest.actionEnabled)
        assertFalse(controller.isSubmitting())
        assertTrue(renderer.latest.message.contains("retained and verified"))
    }

    @Test
    fun `downstream false never appears as confirmation success`() {
        val renderer = RecordingRenderer()
        val controller = controller(renderer) { false }

        controller.onRouteVisibilityChanged(true)
        controller.onSubmitRequested()

        assertEquals(ActionStatus.NOT_ACCEPTED, renderer.latest.status)
        assertTrue(renderer.latest.actionEnabled)
        assertFalse(controller.isSubmitting())
    }

    @Test
    fun `execution failure is generic and retryable without exception detail`() {
        val renderer = RecordingRenderer()
        val controller = controller(renderer) { true }

        controller.onRouteVisibilityChanged(true)
        controller.onSubmitRequested()
        controller.onCompleted(
            UserObservedPriceConfirmationCompletion(
                requestId = 1L,
                outcome = UserObservedPriceConfirmationExecutionOutcome.Failed
            )
        )

        assertEquals(ActionStatus.EXECUTION_FAILED, renderer.latest.status)
        assertEquals("Confirmation could not be completed. Try again.", renderer.latest.message)
        assertTrue(renderer.latest.actionEnabled)
    }

    @Test
    fun `typed transaction rejection stages stay distinct without leaking internals`() {
        val cases =
            listOf(
                UserObservedPriceConfirmationTransactionResult(
                    confirmation = null,
                    artifactFailures = setOf(UserProvidedPriceArtifactFailure.EMPTY_ARTIFACT)
                ) to ActionStatus.PROOF_REJECTED,
                UserObservedPriceConfirmationTransactionResult(
                    confirmation = null,
                    confirmationFailures =
                        setOf(UserObservedPriceConfirmationFailure.NON_POSITIVE_PRICE)
                ) to ActionStatus.CONFIRMATION_REJECTED,
                UserObservedPriceConfirmationTransactionResult(
                    confirmation = null,
                    storageIssue = UserProvidedPriceProofArtifactStorageIssue.WRITE_FAILED
                ) to ActionStatus.STORAGE_REJECTED
            )

        cases.forEach { (result, expectedStatus) ->
            val renderer = RecordingRenderer()
            val controller = controller(renderer) { true }
            controller.onRouteVisibilityChanged(true)
            controller.onSubmitRequested()
            controller.onCompleted(completion(result))

            assertEquals(expectedStatus, renderer.latest.status)
            assertTrue(renderer.latest.actionEnabled)
            assertFalse(renderer.latest.message.contains("WRITE_FAILED"))
            assertFalse(renderer.latest.message.contains("NON_POSITIVE_PRICE"))
            assertFalse(renderer.latest.message.contains("EMPTY_ARTIFACT"))
        }
    }

    @Test
    fun `draft changes during execution prevent stale completion from confirming current draft`() {
        val renderer = RecordingRenderer()
        val controller = controller(renderer) { true }

        controller.onRouteVisibilityChanged(true)
        controller.onSubmitRequested()
        controller.onDraftOrProofChanged()
        assertEquals(ActionStatus.SUBMITTING, renderer.latest.status)

        controller.onCompleted(completion(acceptedResult()))

        assertEquals(ActionStatus.CURRENT_DRAFT_CHANGED_AFTER_COMPLETION, renderer.latest.status)
        assertTrue(renderer.latest.actionEnabled)
        assertTrue(renderer.latest.message.contains("current draft changed"))
    }

    @Test
    fun `completion from route that was left cannot confirm a reopened route`() {
        val renderer = RecordingRenderer()
        val controller = controller(renderer) { true }

        controller.onRouteVisibilityChanged(true)
        controller.onSubmitRequested()
        controller.onRouteVisibilityChanged(false)
        controller.onRouteVisibilityChanged(true)

        assertEquals(ActionStatus.READY, renderer.latest.status)
        controller.onCompleted(completion(acceptedResult()))
        assertEquals(ActionStatus.READY, renderer.latest.status)
    }

    @Test
    fun `editing after success reopens action for the changed draft`() {
        val renderer = RecordingRenderer()
        val controller = controller(renderer) { true }

        controller.onRouteVisibilityChanged(true)
        controller.onSubmitRequested()
        controller.onCompleted(completion(acceptedResult()))
        assertEquals(ActionStatus.CONFIRMED, renderer.latest.status)

        controller.onDraftOrProofChanged()

        assertEquals(ActionStatus.READY, renderer.latest.status)
        assertTrue(renderer.latest.actionEnabled)
    }

    @Test
    fun `close disables action and ignores late completion`() {
        val renderer = RecordingRenderer()
        var submits = 0
        val controller =
            controller(renderer) {
                submits += 1
                true
            }

        controller.onRouteVisibilityChanged(true)
        controller.onSubmitRequested()
        controller.close()
        controller.onCompleted(completion(acceptedResult()))
        controller.onSubmitRequested()

        assertTrue(controller.isClosed())
        assertEquals(ActionStatus.INACTIVE, renderer.latest.status)
        assertFalse(renderer.latest.actionEnabled)
        assertEquals(1, submits)
    }

    @Test
    fun `controller owns presentation sequencing only`() {
        val source = source().readText()

        listOf(
            "ByteArray",
            "Money.parse",
            "Currency.getInstance",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "ContentResolver",
            "openInputStream",
            "UserProvidedPriceProofArtifactLocalStore(",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserConfirmedObservedPrice.confirm",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "OcrScanner",
            "java.net",
            "android.view.",
            "android.widget."
        ).forEach { forbidden ->
            assertFalse(
                "Action presentation controller must not own $forbidden",
                source.contains(forbidden)
            )
        }
    }

    private fun controller(
        renderer: RecordingRenderer,
        submitAction: () -> Boolean
    ): UserObservedPriceConfirmationActionPresentationController =
        UserObservedPriceConfirmationActionPresentationController(
            renderer = renderer,
            submitAction = submitAction
        )

    private fun completion(
        result: UserObservedPriceConfirmationTransactionResult
    ): UserObservedPriceConfirmationCompletion =
        UserObservedPriceConfirmationCompletion(
            requestId = 1L,
            outcome = UserObservedPriceConfirmationExecutionOutcome.Completed(result)
        )

    private fun acceptedResult(): UserObservedPriceConfirmationTransactionResult {
        val bytes = "proof-bytes".toByteArray()
        val artifact =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = "artifact-001",
                        proofType = UserProvidedPriceProofType.PRICE_TAG,
                        artifactBytes = bytes
                    )
                    .artifact
            )
        val confirmation =
            requireNotNull(
                UserConfirmedObservedPrice
                    .confirm(
                        UserObservedPriceConfirmationInput(
                            artifact = artifact,
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
                    )
                    .confirmation
            )

        return UserObservedPriceConfirmationTransactionResult(
            confirmation = confirmation,
            proofAlreadyRetained = false
        )
    }

    private fun source(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "app/src/main/java/com/valuepilot/app/" +
                        "UserObservedPriceConfirmationActionPresentationController.kt"
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate UserObservedPriceConfirmationActionPresentationController.kt")
    }

    private class RecordingRenderer : UserObservedPriceConfirmationActionSurfaceRenderer {
        val states = mutableListOf<UserObservedPriceConfirmationActionUiState>()

        val latest: UserObservedPriceConfirmationActionUiState
            get() = states.last()

        override fun render(state: UserObservedPriceConfirmationActionUiState) {
            states += state
        }
    }
}

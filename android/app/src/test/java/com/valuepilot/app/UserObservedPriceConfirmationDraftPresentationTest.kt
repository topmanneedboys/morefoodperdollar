package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftPresentationTest {

    @Test
    fun `initial finalization projects every missing non byte requirement in canonical order`() {
        val finalization =
            UserObservedPriceConfirmationDraftFinalizer.finalize(
                UserObservedPriceConfirmationDraft.start()
            )

        val state = UserObservedPriceConfirmationDraftUiProjector.project(finalization)

        assertEquals(UserObservedPriceConfirmationDraftUiStatus.NEEDS_NON_BYTE_INPUT, state.status)
        assertEquals("Observed price confirmation", state.headline)
        assertEquals("Confirmation details needed", state.statusTitle)
        assertEquals(
            UserObservedPriceConfirmationDraftMissingField.entries,
            state.missingRequirements.map { it.field }
        )
        assertEquals(
            listOf(
                "Proof reference",
                "Proof type",
                "Observation reference",
                "Product GTIN",
                "Product name",
                "Observed price",
                "Exact store scope",
                "Observation time",
                "Confirmation reference",
                "Confirmation time"
            ),
            state.missingRequirements.map { it.label }
        )
        assertTrue(state.guidance.contains("remaining non-byte details"))
        assertTrue(state.notice.contains("does not mean the confirmation is accepted"))
        assertTrue(state.notice.contains("not retailer-confirmed current prices"))
    }

    @Test
    fun `projection canonicalizes missing field order without reconstructing draft values`() {
        val finalization =
            UserObservedPriceConfirmationDraftFinalization(
                submission = null,
                missingFields =
                    setOf(
                        UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT,
                        UserObservedPriceConfirmationDraftMissingField.GTIN,
                        UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID
                    )
            )

        val state = UserObservedPriceConfirmationDraftUiProjector.project(finalization)

        assertEquals(
            listOf(
                UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID,
                UserObservedPriceConfirmationDraftMissingField.GTIN,
                UserObservedPriceConfirmationDraftMissingField.CONFIRMED_AT
            ),
            state.missingRequirements.map { it.field }
        )
        assertEquals(UserObservedPriceConfirmationDraftUiStatus.NEEDS_NON_BYTE_INPUT, state.status)
    }

    @Test
    fun `complete non byte draft projects completeness without claiming semantic acceptance`() {
        val finalization = UserObservedPriceConfirmationDraftFinalizer.finalize(completeDraft())
        assertTrue(finalization.complete)

        val state = UserObservedPriceConfirmationDraftUiProjector.project(finalization)

        assertEquals(UserObservedPriceConfirmationDraftUiStatus.NON_BYTE_INPUT_COMPLETE, state.status)
        assertEquals("Confirmation details complete", state.statusTitle)
        assertTrue(state.missingRequirements.isEmpty())
        assertTrue(state.guidance.contains("Proof reading, retention, and confirmation validation happen separately"))
        assertTrue(state.notice.contains("does not mean the confirmation is accepted"))
        assertTrue(state.notice.contains("not retailer-confirmed current prices"))

        val uiFieldNames = UserObservedPriceConfirmationDraftUiState::class.java.declaredFields.map { it.name }
        assertFalse(uiFieldNames.contains("submission"))
        assertFalse(uiFieldNames.contains("proofBytes"))
        assertFalse(uiFieldNames.contains("price"))
        assertFalse(uiFieldNames.contains("rawGtin"))
        assertFalse(uiFieldNames.contains("storeScope"))
        assertFalse(uiFieldNames.contains("continueAction"))
        assertFalse(uiFieldNames.contains("submitAction"))
    }

    @Test
    fun `ui state invariants reject readiness mismatch`() {
        val missing =
            listOf(
                UserObservedPriceConfirmationDraftMissingRequirementUi(
                    field = UserObservedPriceConfirmationDraftMissingField.PRICE,
                    label = "Observed price"
                )
            )

        val failure =
            runCatching {
                UserObservedPriceConfirmationDraftUiState(
                    status = UserObservedPriceConfirmationDraftUiStatus.NON_BYTE_INPUT_COMPLETE,
                    headline = "Observed price confirmation",
                    statusTitle = "Confirmation details complete",
                    guidance = "Guidance",
                    missingRequirements = missing,
                    notice = "Notice"
                )
            }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `presenter forwards only projected immutable consumer state`() {
        val finalization =
            UserObservedPriceConfirmationDraftFinalizer.finalize(
                UserObservedPriceConfirmationDraft.start()
            )
        var rendered: UserObservedPriceConfirmationDraftUiState? = null
        val presenter =
            UserObservedPriceConfirmationDraftSurfacePresenter(
                UserObservedPriceConfirmationDraftSurfaceRenderer { state -> rendered = state }
            )

        presenter.render(finalization)

        assertEquals(UserObservedPriceConfirmationDraftUiProjector.project(finalization), rendered)
        assertNull(finalization.submission)
    }

    @Test
    fun `presentation owns no draft shaping validation proof storage picker ranking or Android authority`() {
        val source = source().readText()

        listOf(
            "finalization.missingFields",
            "finalization.complete",
            "UserObservedPriceConfirmationDraftMissingField.entries",
            "Observed prices are not retailer-confirmed current prices.",
            "UserObservedPriceConfirmationDraftSurfaceRenderer",
            "UserObservedPriceConfirmationDraftUiProjector.project(finalization)"
        ).forEach { required ->
            assertTrue("Expected passive presentation boundary $required", source.contains(required))
        }

        listOf(
            "finalization.submission",
            "UserObservedPriceConfirmationDraftFinalizer",
            "UserObservedPriceConfirmationTransaction",
            "UserConfirmedObservedPrice.confirm",
            "UserObservedPriceProofStreamReader",
            "AndroidUserObservedPriceProofContentSource",
            "UserObservedPriceProofReadSubmissionGate",
            "UserProvidedPriceProofArtifactLocalStore",
            "ContentResolver",
            "android.net.Uri",
            "registerForActivityResult",
            "ActivityResultContracts",
            "takePersistableUriPermission",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "Money(",
            "PracticalShoppingStoreIdentityScope(",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "CURRENT_PRICE",
            "ProductionBestValue",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "Bitmap",
            "android.view.",
            "android.widget.",
            "MainActivity",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Draft presentation must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun completeDraft(): UserObservedPriceConfirmationDraft =
        UserObservedPriceConfirmationDraft.start()
            .withArtifactReference(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.PRICE_TAG
            )
            .withProduct(
                observationId = "observation-001",
                rawGtin = "4006381333931",
                productName = "Milk"
            )
            .withPrice(Money(599L, "CAD"))
            .withStoreScope(
                PracticalShoppingStoreIdentityScope(
                    merchantKey = "merchant-a",
                    locationKey = "location-a",
                    commerceChannelKey = "IN_STORE"
                )
            )
            .withObservedAtEpochMillis(10_000L)
            .withConfirmation(
                confirmationId = "confirmation-001",
                confirmedAtEpochMillis = 10_001L
            )

    private fun source(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationDraftPresentation.kt"
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate UserObservedPriceConfirmationDraftPresentation.kt")
    }
}

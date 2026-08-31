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

class UserObservedPriceConfirmationDraftTest {

    @Test
    fun `new draft reports every non-byte submission field unanswered`() {
        val result =
            UserObservedPriceConfirmationDraftFinalizer.finalize(
                UserObservedPriceConfirmationDraft.start()
            )

        assertFalse(result.complete)
        assertNull(result.submission)
        assertEquals(
            UserObservedPriceConfirmationDraftMissingField.entries.toSet(),
            result.missingFields
        )
    }

    @Test
    fun `typed updates are immutable and only remove fields that were explicitly answered`() {
        val initial = UserObservedPriceConfirmationDraft.start()
        val artifact =
            initial.withArtifactReference(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.PRICE_TAG
            )
        val product =
            artifact.withProduct(
                observationId = "obs-001",
                rawGtin = "4006381333931",
                productName = "Test Milk"
            )

        assertNull(initial.artifactId)
        assertNull(initial.proofType)
        assertEquals("artifact-001", artifact.artifactId)
        assertEquals(UserProvidedPriceProofType.PRICE_TAG, artifact.proofType)
        assertNull(artifact.observationId)
        assertEquals("obs-001", product.observationId)

        val missing = UserObservedPriceConfirmationDraftFinalizer.finalize(product).missingFields
        assertFalse(UserObservedPriceConfirmationDraftMissingField.ARTIFACT_ID in missing)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.PROOF_TYPE in missing)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.OBSERVATION_ID in missing)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.GTIN in missing)
        assertFalse(UserObservedPriceConfirmationDraftMissingField.PRODUCT_NAME in missing)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.PRICE in missing)
        assertTrue(UserObservedPriceConfirmationDraftMissingField.STORE_SCOPE in missing)
    }

    @Test
    fun `complete draft forwards exact caller values without trimming or canonicalizing`() {
        val price = Money(599L, "CAD")
        val storeScope = exactStoreScope()
        val draft =
            UserObservedPriceConfirmationDraft
                .start()
                .withArtifactReference(
                    artifactId = "  artifact-001  ",
                    proofType = UserProvidedPriceProofType.RECEIPT
                )
                .withProduct(
                    observationId = "  obs-001  ",
                    rawGtin = " 4006381333931 ",
                    productName = "  Test Milk  "
                )
                .withPrice(price)
                .withStoreScope(storeScope)
                .withObservedAtEpochMillis(10_000L)
                .withConfirmation(
                    confirmationId = "  confirm-001  ",
                    confirmedAtEpochMillis = 20_000L
                )

        val finalization = UserObservedPriceConfirmationDraftFinalizer.finalize(draft)
        val submission = requireNotNull(finalization.submission)

        assertTrue(finalization.complete)
        assertTrue(finalization.missingFields.isEmpty())
        assertEquals("  artifact-001  ", submission.artifactId)
        assertEquals(UserProvidedPriceProofType.RECEIPT, submission.proofType)
        assertEquals("  obs-001  ", submission.fields.observationId)
        assertEquals(" 4006381333931 ", submission.fields.rawGtin)
        assertEquals("  Test Milk  ", submission.fields.productName)
        assertSame(price, submission.fields.price)
        assertSame(storeScope, submission.fields.storeScope)
        assertEquals(10_000L, submission.fields.observedAtEpochMillis)
        assertEquals("  confirm-001  ", submission.fields.confirmationId)
        assertEquals(20_000L, submission.fields.confirmedAtEpochMillis)
    }

    @Test
    fun `complete but invalid fields remain downstream confirmation authority`() {
        val finalization =
            UserObservedPriceConfirmationDraftFinalizer.finalize(
                UserObservedPriceConfirmationDraft
                    .start()
                    .withArtifactReference(
                        artifactId = "artifact-001",
                        proofType = UserProvidedPriceProofType.PRICE_TAG
                    )
                    .withProduct(
                        observationId = "",
                        rawGtin = "123",
                        productName = ""
                    )
                    .withPrice(Money(0L, "CAD"))
                    .withStoreScope(exactStoreScope())
                    .withObservedAtEpochMillis(20_000L)
                    .withConfirmation(
                        confirmationId = "",
                        confirmedAtEpochMillis = 10_000L
                    )
            )

        assertTrue(finalization.complete)
        val submission = requireNotNull(finalization.submission)
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

        val confirmation =
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

        assertFalse(confirmation.accepted)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_OBSERVATION_ID in confirmation.failures)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_GTIN in confirmation.failures)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_PRODUCT_NAME in confirmation.failures)
        assertTrue(UserObservedPriceConfirmationFailure.NON_POSITIVE_PRICE in confirmation.failures)
        assertTrue(UserObservedPriceConfirmationFailure.INVALID_CONFIRMATION_ID in confirmation.failures)
        assertTrue(UserObservedPriceConfirmationFailure.CONFIRMATION_PRECEDES_OBSERVATION in confirmation.failures)
    }

    @Test
    fun `draft and finalization never contain proof byte storage`() {
        val classes =
            listOf(
                UserObservedPriceConfirmationDraft::class.java,
                UserObservedPriceConfirmationDraftSubmission::class.java,
                UserObservedPriceConfirmationDraftFinalization::class.java
            )

        classes.forEach { type ->
            assertTrue(
                "${type.simpleName} must not retain ByteArray fields",
                type.declaredFields.none { field -> field.type == ByteArray::class.java }
            )
        }
    }

    @Test
    fun `draft source owns completeness only and no semantic runtime or UI authority`() {
        val source = source("UserObservedPriceConfirmationDraft.kt").readText()

        listOf(
            "UserConfirmedObservedPrice.confirm",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserObservedPriceConfirmationTransaction(",
            "System.currentTimeMillis",
            "UUID",
            "MessageDigest",
            "GtinValidation",
            "ProductionProductEvidenceKeyResolver",
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
            "java.net",
            "ByteArray"
        ).forEach { forbidden ->
            assertFalse("Draft must not own $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("Null means unanswered"))
        assertTrue(source.contains("if (draft.artifactId == null)"))
        assertTrue(source.contains("if (draft.confirmedAtEpochMillis == null)"))
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

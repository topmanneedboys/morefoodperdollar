package com.valuepilot.app

import com.valuepilot.core.EvidenceProviderId
import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingStoreIdentityScope
import com.valuepilot.core.ProductionProductEvidenceKeyResolver
import com.valuepilot.core.SourceProductIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserConfirmedObservedPriceTest {

    private val storeScope =
        PracticalShoppingStoreIdentityScope(
            merchantKey = "merchant-a",
            locationKey = "location-a",
            commerceChannelKey = "IN_STORE"
        )

    @Test
    fun `artifact fingerprint is computed from actual bounded bytes`() {
        val result =
            UserProvidedPriceProofArtifact.fingerprint(
                artifactId = "artifact-001",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = "receipt-image-bytes".toByteArray()
            )

        assertTrue(result.accepted)
        assertTrue(result.failures.isEmpty())
        val artifact = requireNotNull(result.artifact)
        assertEquals("artifact-001", artifact.artifactId)
        assertEquals(UserProvidedPriceProofType.RECEIPT, artifact.proofType)
        assertEquals(19, artifact.byteLength)
        assertEquals(
            "248ae2255859be5d55d6039fd9248c692a59d41849824debb5f5fac943d8e0d0",
            artifact.sha256
        )

        val other =
            requireNotNull(
                UserProvidedPriceProofArtifact
                    .fingerprint(
                        artifactId = "artifact-002",
                        proofType = UserProvidedPriceProofType.RECEIPT,
                        artifactBytes = "price-tag-image-bytes".toByteArray()
                    )
                    .artifact
            )
        assertNotEquals(artifact.sha256, other.sha256)
    }

    @Test
    fun `artifact creation fails closed for invalid empty and oversized inputs`() {
        val invalidId =
            UserProvidedPriceProofArtifact.fingerprint(
                artifactId = "bad artifact id",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = byteArrayOf(1)
            )
        assertFalse(invalidId.accepted)
        assertEquals(
            setOf(UserProvidedPriceArtifactFailure.INVALID_ARTIFACT_ID),
            invalidId.failures
        )

        val empty =
            UserProvidedPriceProofArtifact.fingerprint(
                artifactId = "artifact-empty",
                proofType = UserProvidedPriceProofType.RECEIPT,
                artifactBytes = byteArrayOf()
            )
        assertFalse(empty.accepted)
        assertEquals(
            setOf(UserProvidedPriceArtifactFailure.EMPTY_ARTIFACT),
            empty.failures
        )

        val oversized =
            UserProvidedPriceProofArtifact.fingerprint(
                artifactId = "artifact-large",
                proofType = UserProvidedPriceProofType.PRICE_TAG,
                artifactBytes =
                    ByteArray(UserProvidedPriceProofArtifact.MAX_ARTIFACT_BYTES + 1)
            )
        assertFalse(oversized.accepted)
        assertEquals(
            setOf(UserProvidedPriceArtifactFailure.ARTIFACT_TOO_LARGE),
            oversized.failures
        )
    }

    @Test
    fun `explicit confirmation binds exact artifact identity store price and timestamps`() {
        val artifact = receiptArtifact()
        val input = validConfirmationInput(artifact)

        val result = UserConfirmedObservedPrice.confirm(input)

        assertTrue(result.accepted)
        assertTrue(result.failures.isEmpty())
        val confirmation = requireNotNull(result.confirmation)
        assertSame(artifact, confirmation.artifact)
        assertSame(storeScope, confirmation.storeScope)
        assertEquals("obs-001", confirmation.observationId)
        assertEquals("4006381333931", confirmation.gtin)
        assertEquals("Test Milk", confirmation.productName)
        assertEquals(Money(599L, "CAD"), confirmation.price)
        assertEquals(10_000L, confirmation.observedAtEpochMillis)
        assertEquals("confirm-001", confirmation.confirmationId)
        assertEquals(20_000L, confirmation.confirmedAtEpochMillis)
        assertEquals("gtin:4006381333931", confirmation.productKey.value)
    }

    @Test
    fun `confirmed gtin keeps cross source identity without borrowing provider sku`() {
        val confirmation =
            requireNotNull(
                UserConfirmedObservedPrice
                    .confirm(validConfirmationInput(receiptArtifact()))
                    .confirmation
            )

        val unrelatedProviderKey =
            requireNotNull(
                ProductionProductEvidenceKeyResolver.resolve(
                    providerId = EvidenceProviderId("unrelated-provider"),
                    identity = SourceProductIdentity(gtin = confirmation.gtin)
                )
            )

        assertTrue(confirmation.productKey.usesCrossSourceRepresentation)
        assertEquals(unrelatedProviderKey, confirmation.productKey)
    }

    @Test
    fun `invalid confirmation facts fail closed together`() {
        val result =
            UserConfirmedObservedPrice.confirm(
                validConfirmationInput(receiptArtifact()).copy(
                    observationId = "bad observation id",
                    rawGtin = "4006381333932",
                    productName = "Milk\nOther store",
                    price = Money(0L, "CAD"),
                    observedAtEpochMillis = 0L,
                    confirmationId = "bad confirmation id",
                    confirmedAtEpochMillis = 0L
                )
            )

        assertFalse(result.accepted)
        assertNull(result.confirmation)
        assertEquals(
            setOf(
                UserObservedPriceConfirmationFailure.INVALID_OBSERVATION_ID,
                UserObservedPriceConfirmationFailure.INVALID_GTIN,
                UserObservedPriceConfirmationFailure.INVALID_PRODUCT_NAME,
                UserObservedPriceConfirmationFailure.NON_POSITIVE_PRICE,
                UserObservedPriceConfirmationFailure.INVALID_OBSERVATION_TIME,
                UserObservedPriceConfirmationFailure.INVALID_CONFIRMATION_ID,
                UserObservedPriceConfirmationFailure.INVALID_CONFIRMATION_TIME
            ),
            result.failures
        )
    }

    @Test
    fun `confirmation cannot precede the preserved observation time`() {
        val result =
            UserConfirmedObservedPrice.confirm(
                validConfirmationInput(receiptArtifact()).copy(
                    observedAtEpochMillis = 20_000L,
                    confirmedAtEpochMillis = 19_999L
                )
            )

        assertFalse(result.accepted)
        assertNull(result.confirmation)
        assertEquals(
            setOf(UserObservedPriceConfirmationFailure.CONFIRMATION_PRECEDES_OBSERVATION),
            result.failures
        )
    }

    @Test
    fun `one receipt artifact can safely bind multiple exact item confirmations`() {
        val artifact = receiptArtifact()
        val milk =
            requireNotNull(
                UserConfirmedObservedPrice
                    .confirm(validConfirmationInput(artifact))
                    .confirmation
            )
        val eggs =
            requireNotNull(
                UserConfirmedObservedPrice
                    .confirm(
                        validConfirmationInput(artifact).copy(
                            observationId = "obs-002",
                            rawGtin = "036000291452",
                            productName = "Test Eggs",
                            price = Money(399L, "CAD"),
                            confirmationId = "confirm-002"
                        )
                    )
                    .confirmation
            )

        assertSame(artifact, milk.artifact)
        assertSame(artifact, eggs.artifact)
        assertNotEquals(milk.productKey, eggs.productKey)
        assertEquals(artifact.sha256, milk.artifact.sha256)
        assertEquals(artifact.sha256, eggs.artifact.sha256)
    }

    @Test
    fun `confirmation boundary creates no evidence authority or hidden runtime ownership`() {
        val source = source("UserConfirmedObservedPrice.kt").readText()

        listOf(
            "class UserProvidedPriceProofArtifact private constructor",
            "MessageDigest",
            "MAX_ARTIFACT_BYTES",
            "class UserConfirmedObservedPrice private constructor",
            "ProductionProductEvidenceKeyResolver",
            "GtinValidation.isValid"
        ).forEach { required ->
            assertTrue("Expected confirmation boundary $required", source.contains(required))
        }

        assertFalse(source.contains("data class UserProvidedPriceProofArtifact"))
        assertFalse(source.contains("data class UserConfirmedObservedPrice"))
        assertFalse(source.contains("artifactSha256:"))

        listOf(
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceClaimDomain.",
            "EvidenceAuthorityClass.",
            "ProviderOfferImportRecord",
            "StapleWatch",
            "System.currentTimeMillis",
            "SharedPreferences",
            "WorkManager",
            "NotificationManager",
            "OcrScanner.scan",
            "java.net",
            "android.permission"
        ).forEach { forbidden ->
            assertFalse("Confirmation boundary must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun receiptArtifact(): UserProvidedPriceProofArtifact =
        requireNotNull(
            UserProvidedPriceProofArtifact
                .fingerprint(
                    artifactId = "artifact-001",
                    proofType = UserProvidedPriceProofType.RECEIPT,
                    artifactBytes = "receipt-image-bytes".toByteArray()
                )
                .artifact
        )

    private fun validConfirmationInput(
        artifact: UserProvidedPriceProofArtifact
    ): UserObservedPriceConfirmationInput =
        UserObservedPriceConfirmationInput(
            artifact = artifact,
            observationId = "obs-001",
            rawGtin = "4006381333931",
            productName = "Test Milk",
            price = Money(599L, "CAD"),
            storeScope = storeScope,
            observedAtEpochMillis = 10_000L,
            confirmationId = "confirm-001",
            confirmedAtEpochMillis = 20_000L
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

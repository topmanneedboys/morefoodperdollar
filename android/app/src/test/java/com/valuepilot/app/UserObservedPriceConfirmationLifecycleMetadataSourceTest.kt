package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationLifecycleMetadataSourceTest {

    @Test
    fun `production source captures downstream-compatible confirmation identity and action time`() {
        val before = System.currentTimeMillis()
        val first = LocalUserObservedPriceConfirmationLifecycleMetadataSource.capture()
        val second = LocalUserObservedPriceConfirmationLifecycleMetadataSource.capture()
        val after = System.currentTimeMillis()
        val opaqueId = Regex("[A-Za-z0-9._:-]{1,160}")

        assertTrue(first.confirmationId.startsWith("confirmation-"))
        assertTrue(second.confirmationId.startsWith("confirmation-"))
        assertTrue(opaqueId.matches(first.confirmationId))
        assertTrue(opaqueId.matches(second.confirmationId))
        assertNotEquals(first.confirmationId, second.confirmationId)
        assertTrue(first.confirmedAtEpochMillis > 0L)
        assertTrue(second.confirmedAtEpochMillis > 0L)
        assertTrue(first.confirmedAtEpochMillis in before..after)
        assertTrue(second.confirmedAtEpochMillis in before..after)
    }

    @Test
    fun `source contract is replaceable without invoking production UUID or clock mechanisms`() {
        val expected =
            UserObservedPriceConfirmationLifecycleMetadata(
                confirmationId = "confirmation-fixed",
                confirmedAtEpochMillis = 123_456L
            )
        val source = UserObservedPriceConfirmationLifecycleMetadataSource { expected }

        assertEquals(expected, source.capture())
    }

    @Test
    fun `production source owns only technical confirmation identity and action time`() {
        val source = source("UserObservedPriceConfirmationLifecycleMetadataSource.kt").readText()

        assertTrue(source.contains("UUID.randomUUID()"))
        assertTrue(source.contains("System.currentTimeMillis()"))

        listOf(
            "Money",
            "PracticalShoppingStoreIdentityScope",
            "UserProvidedPriceProof",
            "artifactBytes",
            "observedAtEpochMillis",
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceSavedConfirmationDraftRouteCoordinator",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceConfirmationInputHost",
            "UserObservedPriceConfirmationAndroidSession",
            "UserObservedPriceConfirmationTransaction",
            "UserConfirmedObservedPrice.confirm",
            "UserProvidedPriceProofArtifactLocalStore",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionCurrentPrice",
            "android.content.Context",
            "android.view.",
            "android.widget.",
            "MainActivity",
            "java.net"
        ).forEach { forbidden ->
            assertFalse(
                "Confirmation lifecycle metadata source must not own $forbidden",
                source.contains(forbidden)
            )
        }
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

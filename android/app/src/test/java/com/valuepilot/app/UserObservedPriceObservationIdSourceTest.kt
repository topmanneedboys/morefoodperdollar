package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceObservationIdSourceTest {

    @Test
    fun `production source emits distinct downstream-compatible opaque record identities`() {
        val first = LocalUserObservedPriceObservationIdSource.nextObservationId()
        val second = LocalUserObservedPriceObservationIdSource.nextObservationId()
        val opaqueId = Regex("[A-Za-z0-9._:-]{1,160}")

        assertTrue(first.startsWith("observation-"))
        assertTrue(second.startsWith("observation-"))
        assertTrue(opaqueId.matches(first))
        assertTrue(opaqueId.matches(second))
        assertNotEquals(first, second)
    }

    @Test
    fun `source owns only opaque identity generation and no observation facts time confirmation or authority`() {
        val source = source("UserObservedPriceObservationIdSource.kt").readText()

        assertTrue(source.contains("UUID.randomUUID()"))
        listOf(
            "System.currentTimeMillis",
            "Instant.now",
            "Clock.",
            "Money",
            "PracticalShoppingStoreIdentityScope",
            "UserProvidedPriceProof",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserConfirmedObservedPrice.confirm",
            "UserProvidedPriceProofArtifactLocalStore",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "android.content.Context",
            "android.view.",
            "android.widget.",
            "MainActivity",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Observation ID source must not own $forbidden", source.contains(forbidden))
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

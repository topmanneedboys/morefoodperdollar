package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaQualifiedStoreMapContextBoundaryTest {
    @Test
    fun `search and basket use in-app source-backed context before optional external map`() {
        val source = source("src/main/java/com/valuepilot/app/MainActivity.kt")
            .readText()
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        assertTrue(source.contains("showQualifiedStoreMapContext("))
        assertTrue(source.contains("ArgentinaQualifiedStoreMapContextPresentation.fromOffer"))
        assertTrue(source.contains("ArgentinaQualifiedStoreMapContextPresentation.fromStore"))
        assertTrue(source.contains("openExternalStoreMap("))
        assertFalse(source.contains("openQualifiedStoreMap("))
        assertTrue(source.contains("private fun showPrivateTripAssumptions()"))
        assertTrue(source.contains("ArgentinaPrivateTripCostCalculator.calculate"))
    }

    private fun source(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

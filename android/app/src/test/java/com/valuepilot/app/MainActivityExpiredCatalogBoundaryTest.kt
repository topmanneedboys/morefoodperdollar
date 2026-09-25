package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityExpiredCatalogBoundaryTest {

    @Test
    fun `expired catalog has a distinct honest Home explanation`() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val strings = source("src/main/res/values/strings.xml").readText()

        assertTrue(activity.contains("BundledOfflineCatalogAdmissionException"))
        assertTrue(activity.contains("R.string.home_offline_catalog_expired"))
        assertTrue(strings.contains("name=\"home_offline_catalog_expired\""))
        assertTrue(strings.contains("fictional sample plan"))
        assertTrue(strings.contains("newer signed catalog"))
        assertTrue(strings.contains("No current price, stock or availability is inferred"))
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

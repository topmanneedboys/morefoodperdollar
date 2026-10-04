package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ValuePilotApplicationBoundaryTest {
    @Test
    fun `10124 startup initializes Firebase but does not run the historical probe`() {
        val source = file("src/main/java/com/valuepilot/app/ValuePilotApplication.kt").readText()
        val gradle = file("build.gradle.kts").readText()
        assertTrue(source.contains("ValuePilotFirebaseRuntime.initialize(this)"))
        assertFalse(source.contains("startIfEligible"))
        assertTrue(gradle.contains("versionCode = 10124"))
        assertTrue(gradle.contains("versionName = \"101.1.23\""))
        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"M12_3_QUALIFICATION_PROBE\", \"false\")"))
    }

    @Test
    fun `trip planner uses only optional foreground location`() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("ACCESS_FINE_LOCATION"))
        assertTrue(manifest.contains("ACCESS_COARSE_LOCATION"))
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
    }

    private fun file(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}

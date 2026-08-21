package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreBoundaryTest {
    @Test
    fun permanentContractsAndUiStateDoNotImportAndroid() {
        listOf("CoreContracts.kt", "ValuePilotUiState.kt", "RankingModePolicy.kt").forEach { name ->
            val source = source(name).readText()
            assertFalse("$name must remain platform neutral", source.contains("import android."))
            assertFalse("$name must not own Android views", source.contains("AccessibilityNodeInfo"))
            assertFalse("$name must not own Android views", source.contains("WindowManager"))
        }
    }

    @Test
    fun androidLiveCaptureIsAnExplicitReplaceableAdapter() {
        val source = source("AndroidLiveConnector.kt").readText()
        assertTrue(source.contains("ProductObservationProvider"))
        assertTrue(source.contains("AccessibilityNodeInfo"))
        assertFalse(source("CoreContracts.kt").readText().contains("AndroidLiveConnector"))
    }

    @Test
    fun applicationBoundaryExposesTypedIntentsAndImmutableState() {
        val source = source("ValuePilotUiState.kt").readText()
        assertTrue(source.contains("data class ValuePilotUiState"))
        assertTrue(source.contains("sealed interface ValuePilotIntent"))
        assertTrue(source.contains("fun interface ValuePilotUiRenderer"))
        assertTrue(source.contains("value class ProductResultId"))
    }

    private fun source(name: String): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

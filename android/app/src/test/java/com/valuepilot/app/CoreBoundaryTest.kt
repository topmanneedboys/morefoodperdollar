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
        assertTrue(source.contains("import com.valuepilot.core.ProductResultId"))
    }

    @Test
    fun deterministicEngineDoesNotReachGlobalModel() {
        val source = source("ValueEngine.kt").readText()
        assertFalse(source.contains("LocalFoodModel"))
        assertTrue(source.contains("SemanticEnricher"))
        assertTrue(source.contains("NoSemanticEnricher"))
    }

    @Test
    fun comparisonActivityUsesExactReplaceableCompareHereBoundary() {
        val source = source("ComparisonActivity.kt").readText()

        assertTrue(source.contains("CompareHereManualRouteCoordinator.compareBlocks"))
        assertTrue(source.contains("CompareHereManualScreenPresenter"))
        assertTrue(source.contains("CompareHereManualActivitySessionReducer"))

        assertFalse(source.contains("StandaloneComparisonController"))
        assertFalse(source.contains("StandaloneComparisonIntent"))
        assertFalse(source.contains("StandaloneComparisonState"))
        assertFalse(source.contains("ValueEngine"))
    }

    @Test
    fun comparisonActivityDoesNotBypassManualRouteCoordinator() {
        val source = source("ComparisonActivity.kt").readText()

        assertFalse(source.contains("CompareHereComparisonIntentKey("))
        assertFalse(source.contains("CompareHereManualInputAdapter.capture"))
        assertFalse(source.contains("CompareHereManualComparisonService"))
        assertFalse(source.contains("ManualProductObservationAdapter"))
    }

    private fun source(name: String): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}

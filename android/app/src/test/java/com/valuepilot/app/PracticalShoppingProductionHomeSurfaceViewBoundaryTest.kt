package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductionHomeSurfaceViewBoundaryTest {

    @Test
    fun surfaceConsumesOnlyDemoFreeUiStateAndStartsInactive() {
        val source = source().readText()

        assertTrue(source.contains("PracticalShoppingProductionHomeUiState?"))
        assertTrue(source.contains("visibility = View.GONE"))
        assertTrue(source.contains("planResult.render(requireNotNull(state.result))"))
        assertFalse(source.contains("PracticalShoppingProductionHomeProjection"))
        assertFalse(source.contains("PracticalShoppingProductionOrchestrationRequest"))
        assertFalse(source.contains("PracticalShoppingProductionOrchestrator"))
    }

    @Test
    fun surfaceRendersUnknownCoverageFromTheImmutableRowNotice() {
        val source = source().readText()

        assertTrue(source.contains("item.coverageNotice"))
        assertTrue(source.contains("item.plannedPriceNotice?.let"))
        assertTrue(source.contains("production_home_store_assignment"))
        assertTrue(source.contains("production_home_included_price"))
        assertFalse(source.contains("knownBasketCost"))
        assertFalse(source.contains("Money.parse"))
        assertFalse(source.contains("PracticalShoppingPlanner"))
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/PracticalShoppingProductionHomeSurfaceView.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}

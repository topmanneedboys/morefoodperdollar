package com.valuepilot.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the Android action boundary honest: a result-producing action must dismiss the IME
 * before projecting its answer, while the exact routes remain the only result authorities.
 */
class ResultVisibilityBoundaryTest {

    @Test
    fun compare_here_dismisses_the_ime_before_projecting_the_exact_result() {
        val source = source("ComparisonActivity.kt").readText()
        val runComparison =
            source
                .substringAfter("private fun runComparison")
                .substringBefore("private fun onProductsChanged")

        assertTrue(runComparison.contains("hideKeyboard()"))
        assertTrue(runComparison.contains("CompareHereManualRouteCoordinator.evaluateBlocks"))
        assertTrue(
            runComparison.indexOf("hideKeyboard()") <
                runComparison.indexOf("CompareHereManualRouteCoordinator.evaluateBlocks")
        )
        assertTrue(source.contains("private fun hideKeyboard()"))
        assertTrue(source.contains("hideSoftInputFromWindow(windowToken, 0)"))
        assertTrue(source.contains("focusedView?.clearFocus()"))
    }

    @Test
    fun good_price_dismisses_the_ime_before_projecting_the_exact_result() {
        val source = source("GoodPriceActivity.kt").readText()
        val runCheck =
            source
                .substringAfter("private fun runCheck")
                .substringBefore("private fun renderIdle")

        assertTrue(runCheck.contains("hideKeyboard()"))
        assertTrue(runCheck.contains("GoodPriceCheckRouteCoordinator.checkBlock"))
        assertTrue(
            runCheck.indexOf("hideKeyboard()") <
                runCheck.indexOf("GoodPriceCheckRouteCoordinator.checkBlock")
        )
        assertTrue(source.contains("private fun hideKeyboard()"))
        assertTrue(source.contains("hideSoftInputFromWindow(focusedView.windowToken, 0)"))
        assertTrue(source.contains("focusedView.clearFocus()"))
    }

    private fun source(name: String): File =
        File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/java/com/valuepilot/app/$name"
        ).also { file ->
            assertTrue("Missing source at ${file.absolutePath}", file.isFile)
        }
}

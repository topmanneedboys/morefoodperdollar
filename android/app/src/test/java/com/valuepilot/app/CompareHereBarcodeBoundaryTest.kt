package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompareHereBarcodeBoundaryTest {

    @Test
    fun `compare barcode route is user initiated identity only and local`() {
        val source = source("ComparisonActivity.kt").readText()
        val layout = file("src/main/res/layout/activity_main.xml").readText()

        listOf(
            "compareBarcodeButton.setOnClickListener",
            "ActivityResultContracts.StartActivityForResult",
            "BarcodeCaptureActivity.EXTRA_GTIN",
            "BundledOfflineCatalog.discoverSupportedRegions",
            "GoodPriceBarcodeIdentityPresentation",
            "CompareHereBarcodeDraft.apply",
            "focusProductInput(result.addedIndex)",
            "private fun focusProductInput(index: Int?)",
            "productInputs::getOrNull",
            "input.requestFocus()",
            "input.setSelection(input.text?.length ?: 0)",
            "barcodeLookupExecutor.shutdownNow",
            "barcodeLookupRequestId",
            "barcodeLookupClosed",
            "ACCESSIBILITY_LIVE_REGION_POLITE"
        ).forEach { required -> assertTrue(source.contains(required)) }

        assertTrue(layout.contains("compareBarcodeButton"))
        assertTrue(layout.contains("compareBarcodeStatus"))

        listOf(
            "PracticalShoppingPlanner",
            "RankingEngine",
            "HttpURLConnection",
            "URL(",
            "INTERNET",
            "current offer",
            "live inventory",
            "getExternalStorage",
            "MediaStore"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
    }

    @Test
    fun `barcode identity handoff focuses only the populated exact-entry block`() {
        val source = source("ComparisonActivity.kt").readText()
        val useNameHandler =
            source
                .substringAfter("CompareHereBarcodeDraft.apply(")
                .substringBefore("dialog.show()")

        assertTrue(useNameHandler.contains("dialog.dismiss()"))
        assertTrue(useNameHandler.contains("focusProductInput(result.addedIndex)"))
        assertTrue(
            useNameHandler.indexOf("dialog.dismiss()") <
                useNameHandler.indexOf("focusProductInput(result.addedIndex)")
        )
        assertTrue(source.contains("if (isFinishing || isDestroyed) return@post"))
    }

    @Test
    fun `barcode status remains explicit about missing offer facts`() {
        val strings = file("src/main/res/values/strings.xml").readText()

        listOf(
            "compare_barcode_match_message",
            "does not prove package quantity, price, store, stock or availability",
            "compare_barcode_used",
            "did not confirm a live offer"
        ).forEach { required -> assertTrue(strings.contains(required)) }
    }

    private fun source(name: String): File =
        file("src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }

    private fun file(path: String): File =
        File(requireNotNull(System.getProperty("user.dir")), path)
}

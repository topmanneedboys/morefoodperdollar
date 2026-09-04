package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BarcodeCaptureBoundaryTest {

    @Test
    fun `barcode capture is user initiated bounded and local`() {
        val source = source("BarcodeCaptureActivity.kt").readText()
        val layout = file("src/main/res/layout/activity_barcode_capture.xml").readText()
        val manifest = file("src/main/AndroidManifest.xml").readText()

        listOf(
            "ActivityResultContracts.GetContent",
            "ActivityResultContracts.RequestPermission",
            "ActivityResultContracts.TakePicture",
            "Manifest.permission.CAMERA",
            "FileProvider.getUriForFile",
            "cacheDir",
            "decodeBoundedPhoto",
            "BarcodeScanning.getClient",
            "BarcodeScanResolutionResolver.resolve",
            "isDestroyed",
            "imageExecutor.shutdownNow",
            "ACCESSIBILITY_LIVE_REGION_POLITE"
        ).forEach { required -> assertTrue(source.contains(required)) }

        assertTrue(layout.contains("barcodeCaptureButton"))
        assertTrue(layout.contains("barcodeImportButton"))
        assertTrue(manifest.contains("android:name=\".BarcodeCaptureActivity\""))
        assertTrue(manifest.contains("android:exported=\"false\""))

        listOf(
            "HttpURLConnection",
            "URL(",
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "getExternalStorage",
            "MediaStore.Images"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
    }

    @Test
    fun `good price owns only barcode handoff and identity lookup, not offer authority`() {
        val source = source("GoodPriceActivity.kt").readText()
        val layout = file("src/main/res/layout/activity_good_price.xml").readText()

        listOf(
            "BarcodeCaptureActivity.EXTRA_GTIN",
            "BundledOfflineCatalog.discoverSupportedRegions",
            "GoodPriceBarcodeIdentityPresentation",
            "barcodeLookupExecutor",
            "barcodeLookupRequestId",
            "good_price_barcode_used"
        ).forEach { required -> assertTrue(source.contains(required)) }
        assertTrue(layout.contains("goodPriceBarcodeButton"))
        assertTrue(layout.contains("goodPriceBarcodeStatus"))

        listOf(
            "PracticalShoppingPlanner",
            "RankingEngine",
            "HttpURLConnection",
            "URL(",
            "INTERNET",
            "current offer",
            "live inventory"
        ).forEach { forbidden -> assertFalse(source.contains(forbidden)) }
    }

    private fun source(name: String): File =
        file("src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }

    private fun file(path: String): File =
        File(requireNotNull(System.getProperty("user.dir")), path)
}

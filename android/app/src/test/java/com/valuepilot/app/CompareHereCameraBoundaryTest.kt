package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompareHereCameraBoundaryTest {
    @Test
    fun cameraCaptureIsUserInitiatedAndReusesBoundedOnDevicePhotoRoute() {
        val source = source("ComparisonActivity.kt").readText()
        val layout = file("src/main/res/layout/activity_main.xml").readText()
        val onCreateBody =
            source.substringAfter("override fun onCreate").substringBefore("override fun onResume")

        assertTrue(source.contains("capturePhotoButton.setOnClickListener"))
        assertTrue(source.contains("ActivityResultContracts.RequestPermission"))
        assertTrue(source.contains("ActivityResultContracts.TakePicture"))
        assertTrue(source.contains("CameraCaptureContract"))
        assertTrue(source.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(source.contains("Intent.FLAG_GRANT_WRITE_URI_PERMISSION"))
        assertTrue(source.contains("Manifest.permission.CAMERA"))
        assertTrue(source.contains("ContextCompat.checkSelfPermission"))
        assertTrue(source.contains("PackageManager.FEATURE_CAMERA_ANY"))
        assertTrue(source.contains("FileProvider.getUriForFile"))
        assertTrue(source.contains("cacheDir"))
        assertTrue(source.contains("syncPhotoActionButtons"))
        assertTrue(source.contains("photoRequestId"))
        assertTrue(source.contains("photoImportClosed"))
        assertTrue(source.contains("isDestroyed"))
        assertTrue(source.contains("decodeBoundedPhoto"))
        assertTrue(source.contains("OcrScanner.scan"))
        assertTrue(source.contains("CompareHerePhotoDraft.review"))
        assertTrue(source.contains("CompareHerePhotoSuggestionPresentationFactory"))
        assertTrue(source.contains("editorPrefill"))
        assertTrue(source.contains("compare_photo_add_with_details"))
        assertTrue(source.contains("BUTTON_NEUTRAL"))
        assertTrue(source.contains("showPhotoReviewDialog"))
        assertTrue(source.contains("setMultiChoiceItems"))
        assertTrue(source.contains("photoReviewDialog"))
        assertTrue(source.contains("CompareHerePhotoDraft.append"))
        assertTrue(source.contains("CompareHerePhotoRetryPolicy"))
        assertTrue(source.contains("CompareHerePhotoRetryOutcome.PERMISSION_DENIED"))
        assertTrue(source.contains("CompareHerePhotoRequestPolicy"))
        assertTrue(source.contains("cancelPhotoRequest"))
        assertTrue(source.contains("cancelPhotoRequestForDraftChange"))
        assertTrue(source.contains("cancelPhotoButton"))
        assertTrue(source.contains("retryPhotoButton"))
        assertTrue(source.contains("cleanupCameraCaptureFile"))
        assertTrue(source.contains("ACCESSIBILITY_LIVE_REGION_POLITE"))
        assertTrue(
            source.contains(
                "scannerStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE"
            )
        )
        assertTrue(layout.contains("capturePhotoButton"))
        assertTrue(layout.contains("importPhotoButton"))
        assertTrue(layout.contains("cancelPhotoButton"))
        assertTrue(layout.contains("retryPhotoButton"))
        assertFalse(onCreateBody.contains("cameraPermissionLauncher.launch"))

        assertFalse(source.contains("getExternalStorage"))
        assertFalse(source.contains("MediaStore"))
        assertFalse(source.contains("android.permission.INTERNET"))
        assertFalse(source.contains("ACCESS_NETWORK_STATE"))
        assertFalse(source.contains("http://"))
        assertFalse(source.contains("https://"))

        val deniedCall =
            source.substringAfter("if (!granted)").substringBefore("launchCameraCapture")
        assertTrue(deniedCall.contains("CompareHerePhotoRetryOutcome.PERMISSION_DENIED"))
        assertFalse(deniedCall.contains("CAPTURE_FAILURE"))
    }

    @Test
    fun cameraPermissionAndFileProviderAreOptionalAndCacheScoped() {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        val paths = file("src/main/res/xml/file_paths.xml").readText()

        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.hardware.camera.any"))
        assertTrue(manifest.contains("android:required=\"false\""))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("\${applicationId}.fileprovider"))
        assertTrue(manifest.contains("android:grantUriPermissions=\"true\""))
        assertTrue(manifest.contains("@xml/file_paths"))
        assertTrue(paths.contains("<cache-path"))
        assertTrue(paths.contains("name=\"camera_cache\""))
        assertTrue(paths.contains("path=\"camera/\""))
        assertFalse(paths.contains("external-path"))
        assertFalse(paths.contains("root-path"))
    }

    @Test
    fun ocr_suggestions_are_reviewed_before_they_can_change_the_draft() {
        val source = source("ComparisonActivity.kt").readText()
        val strings = file("src/main/res/values/strings.xml").readText()

        assertTrue(source.contains("CompareHerePhotoDraft.review"))
        assertTrue(source.contains("compare_photo_review_title"))
        assertTrue(source.contains("compare_photo_add_selected"))
        assertTrue(source.contains("selectedCandidates"))
        assertTrue(strings.contains("untrusted OCR suggestion"))
        assertTrue(strings.contains("parser signals"))
        assertTrue(strings.contains("Add with detected details"))
        assertTrue(strings.contains("never confirms the facts"))
        assertTrue(strings.contains("Nothing is added until you choose an add action"))
        assertTrue(strings.contains("Try another photo"))
        assertTrue(strings.contains("Cancel photo reading"))
        assertTrue(strings.contains("No photo suggestions were added"))
    }

    @Test
    fun `draft transitions invalidate photo callbacks before idle rendering`() {
        val source = source("ComparisonActivity.kt").readSourceText()
        val productsChanged =
            source.substringAfter("private fun onProductsChanged()")
                .substringBefore("private fun syncLikeForLikeConfirmation")
        val clearComparison =
            source.substringAfter("private fun clearComparison()")
                .substringBefore("/**\n     * Applies one intentionally shared text")

        assertTrue(productsChanged.contains("cancelPhotoRequestForDraftChange()"))
        assertTrue(clearComparison.contains("cancelPhotoRequestForDraftChange()"))
        assertTrue(source.contains("CompareHerePhotoRequestPolicy.accepts"))
    }

    private fun source(name: String): File =
        file("src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }

    private fun file(path: String): File =
        File(System.getProperty("user.dir"), path)
}

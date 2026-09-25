package com.valuepilot.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * User-triggered, on-device packaged-product barcode capture.
 *
 * This route returns only one checksum-valid GTIN. It deliberately does not fetch product data,
 * price, store, stock or availability, and it never writes a product observation itself. The
 * caller decides how an explicitly requested identity is used next.
 */
class BarcodeCaptureActivity : AppCompatActivity() {
    private lateinit var captureButton: Button
    private lateinit var importButton: Button
    private lateinit var cancelButton: Button
    private lateinit var status: TextView

    private val imageExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            onPhotoSelected(uri, cleanupFile = null)
        }
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onCameraPermissionResult(granted)
        }
    private val cameraCaptureLauncher =
        registerForActivityResult(CameraCaptureContract()) { captured ->
            onCameraCaptureCompleted(captured)
        }

    private var requestId = 0L
    private var inFlight = false
    private var closed = false
    private var cameraCaptureRequestId = 0L
    private var cameraCaptureUri: Uri? = null
    private var cameraCaptureFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_capture)

        captureButton = findViewById(R.id.barcodeCaptureButton)
        importButton = findViewById(R.id.barcodeImportButton)
        cancelButton = findViewById(R.id.barcodeCancelButton)
        status = findViewById(R.id.barcodeCaptureStatus)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE

        captureButton.setOnClickListener { beginCameraCapture() }
        importButton.setOnClickListener { beginPhotoImport() }
        cancelButton.setOnClickListener { finish() }
        syncActionButtons()
    }

    override fun onDestroy() {
        closed = true
        requestId += 1L
        cleanupCameraCaptureFile()
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun beginPhotoImport() {
        if (closed || inFlight) return
        inFlight = true
        requestId += 1L
        syncActionButtons()
        status.text = getString(R.string.barcode_processing)
        try {
            importLauncher.launch("image/*")
        } catch (_: Exception) {
            finishPhotoRequest(R.string.barcode_error)
        }
    }

    private fun beginCameraCapture() {
        if (closed || inFlight) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            finishPhotoRequest(R.string.barcode_camera_unavailable)
            return
        }

        inFlight = true
        requestId += 1L
        cameraCaptureRequestId = requestId
        syncActionButtons()
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraCapture(cameraCaptureRequestId)
        } else {
            status.text = getString(R.string.barcode_camera_permission_needed)
            try {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (_: Exception) {
                finishPhotoRequest(R.string.barcode_error)
            }
        }
    }

    private fun onCameraPermissionResult(granted: Boolean) {
        if (closed || !inFlight || cameraCaptureRequestId != requestId) return
        if (!granted) {
            finishPhotoRequest(R.string.barcode_camera_permission_denied)
            return
        }
        launchCameraCapture(cameraCaptureRequestId)
    }

    private fun launchCameraCapture(captureRequestId: Long) {
        if (closed || !inFlight || captureRequestId != requestId) return
        try {
            val directory = File(cacheDir, CAMERA_CACHE_DIRECTORY).apply { mkdirs() }
            val file = File.createTempFile(CAMERA_FILE_PREFIX, CAMERA_FILE_SUFFIX, directory)
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    file
                )
            cameraCaptureFile = file
            cameraCaptureUri = uri
            status.text = getString(R.string.barcode_processing)
            cameraCaptureLauncher.launch(uri)
        } catch (_: Exception) {
            cleanupCameraCaptureFile()
            finishPhotoRequest(R.string.barcode_error)
        }
    }

    private fun onCameraCaptureCompleted(captured: Boolean) {
        val activeRequestId = cameraCaptureRequestId
        val uri = cameraCaptureUri
        if (closed || activeRequestId != requestId || !inFlight) {
            cleanupCameraCaptureFile()
            return
        }

        cameraCaptureUri = null
        if (!captured || uri == null) {
            cleanupCameraCaptureFile()
            finishPhotoRequest(R.string.barcode_cancelled)
            return
        }
        onPhotoSelected(uri, cleanupFile = cameraCaptureFile)
    }

    private fun onPhotoSelected(uri: Uri?, cleanupFile: File?) {
        if (closed || !inFlight) {
            cleanupFile?.let(::deleteCameraCaptureFile)
            return
        }
        val activeRequestId = requestId
        if (uri == null) {
            cleanupFile?.let(::deleteCameraCaptureFile)
            finishPhotoRequest(R.string.barcode_cancelled)
            return
        }

        try {
            imageExecutor.execute {
                val bitmap = decodeBoundedPhoto(uri)
                if (bitmap == null) {
                    cleanupFile?.let(::deleteCameraCaptureFile)
                    postBarcodeResult(activeRequestId, emptyList(), IllegalArgumentException("decode"))
                    return@execute
                }
                scanBarcode(bitmap, activeRequestId, cleanupFile)
            }
        } catch (error: Throwable) {
            cleanupFile?.let(::deleteCameraCaptureFile)
            postBarcodeResult(activeRequestId, emptyList(), error)
        }
    }

    private fun scanBarcode(bitmap: Bitmap, activeRequestId: Long, cleanupFile: File?) {
        val scanner = BarcodeScanning.getClient(SCANNER_OPTIONS)
        try {
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { codes ->
                    scanner.close()
                    recycle(bitmap)
                    cleanupFile?.let(::deleteCameraCaptureFile)
                    postBarcodeResult(
                        activeRequestId,
                        codes.mapNotNull { it.rawValue },
                        null
                    )
                }
                .addOnFailureListener { error ->
                    scanner.close()
                    recycle(bitmap)
                    cleanupFile?.let(::deleteCameraCaptureFile)
                    postBarcodeResult(activeRequestId, emptyList(), error)
                }
        } catch (error: Throwable) {
            scanner.close()
            recycle(bitmap)
            cleanupFile?.let(::deleteCameraCaptureFile)
            postBarcodeResult(activeRequestId, emptyList(), error)
        }
    }

    private fun postBarcodeResult(
        activeRequestId: Long,
        rawValues: List<String>,
        error: Throwable?
    ) {
        runOnUiThread {
            applyBarcodeResult(activeRequestId, rawValues, error)
        }
    }

    private fun applyBarcodeResult(
        activeRequestId: Long,
        rawValues: List<String>,
        error: Throwable?
    ) {
        if (
            closed ||
            activeRequestId != requestId ||
            !inFlight ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        inFlight = false
        syncActionButtons()
        if (error != null) {
            status.text = getString(R.string.barcode_error)
            return
        }

        val resolution = BarcodeScanResolutionResolver.resolve(rawValues)
        if (!resolution.accepted) {
            status.text =
                getString(
                    if (resolution.issue == BarcodeScanIssue.MULTIPLE_GTINS) {
                        R.string.barcode_multiple
                    } else {
                        R.string.barcode_no_valid
                    }
                )
            return
        }
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_GTIN, requireNotNull(resolution.rawGtin))
        )
        finish()
    }

    private fun finishPhotoRequest(statusRes: Int) {
        inFlight = false
        syncActionButtons()
        status.text = getString(statusRes)
    }

    private fun syncActionButtons() {
        if (!::captureButton.isInitialized) return
        val enabled = !closed && !inFlight
        captureButton.isEnabled = enabled
        importButton.isEnabled = enabled
    }

    private fun cleanupCameraCaptureFile() {
        cameraCaptureFile?.let(::deleteCameraCaptureFile)
        cameraCaptureFile = null
        cameraCaptureUri = null
    }

    private fun deleteCameraCaptureFile(file: File) {
        runCatching {
            if (file.exists()) file.delete()
        }
        if (cameraCaptureFile == file) {
            cameraCaptureFile = null
            cameraCaptureUri = null
        }
    }

    /** Decode a bounded image before handing it to the barcode model. */
    private fun decodeBoundedPhoto(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (largestDimension / sampleSize > MAX_PHOTO_DIMENSION) sampleSize *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        const val EXTRA_GTIN = "com.valuepilot.app.extra.GTIN"

        private const val MAX_PHOTO_DIMENSION = 2_048
        private const val CAMERA_CACHE_DIRECTORY = "camera"
        private const val CAMERA_FILE_PREFIX = "valuepilot-barcode-"
        private const val CAMERA_FILE_SUFFIX = ".jpg"

        private val SCANNER_OPTIONS =
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_ITF
                )
                .build()
    }

    /** Adds URI grants that the stock TakePicture contract intentionally leaves to callers. */
    private class CameraCaptureContract : ActivityResultContract<Uri, Boolean>() {
        private val delegate = ActivityResultContracts.TakePicture()

        override fun createIntent(context: Context, input: Uri): Intent =
            delegate.createIntent(context, input).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            delegate.parseResult(resultCode, intent)
    }
}

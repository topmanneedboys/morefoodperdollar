package com.valuepilot.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.valuepilot.core.CompareHerePriceSelection
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ComparisonActivity : AppCompatActivity() {
    private lateinit var productInputsContainer: LinearLayout
    private lateinit var addProductButton: Button
    private lateinit var compareButton: Button
    private lateinit var likeForLikeConfirmation: CheckBox
    private lateinit var priceSelectionGroup: RadioGroup
    private lateinit var comparisonScreen: CompareHereManualScreenView
    private lateinit var comparisonPresenter: CompareHereManualScreenPresenter
    private lateinit var shareComparisonButton: Button
    private lateinit var shareComparisonStatus: TextView
    private lateinit var scannerStatus: TextView
    private lateinit var importPhotoButton: Button
    private lateinit var capturePhotoButton: Button
    private lateinit var photoImportStatus: TextView
    private lateinit var cancelPhotoButton: Button
    private lateinit var retryPhotoButton: Button
    private lateinit var compareBarcodeButton: Button
    private lateinit var compareBarcodeStatus: TextView
    private lateinit var privateMemoryStatus: TextView
    private lateinit var clearPrivateMemoryButton: Button
    private lateinit var privateMemoryStore: CompareHerePrivatePriceMemoryStore
    private var privateMemoryStateWhenStatusRendered: CompareHerePrivatePriceMemoryState? = null

    private val productInputs = mutableListOf<EditText>()
    private val productInputRows = mutableListOf<ProductInputRow>()
    private val photoExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val barcodeLookupExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val photoPickerLauncher =
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
    private val barcodeCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onBarcodeCaptureResult(result.resultCode, result.data?.getStringExtra(BarcodeCaptureActivity.EXTRA_GTIN))
        }

    private var activityState = CompareHereManualActivitySessionState.initial()
    private var restoringDraft = false
    private var photoRequestId = 0L
    private var photoImportInFlight = false
    private var photoImportClosed = false
    private var photoReviewDialog: AlertDialog? = null
    private var photoReviewRequestId = 0L
    private var lastPhotoCaptureKind: CompareHerePhotoCaptureKind? = null
    private var cameraCaptureRequestId = 0L
    private var cameraCaptureUri: Uri? = null
    private var cameraCaptureFile: File? = null
    private var barcodeLookupRequestId = 0L
    private var barcodeLookupInFlight = false
    private var barcodeLookupClosed = false
    private var barcodeDialog: AlertDialog? = null
    private var sharedTextDialog: AlertDialog? = null
    private var shareCard: CompareHereShareCard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        installSystemBarInsets()

        productInputsContainer = findViewById(R.id.productInputsContainer)
        addProductButton = findViewById(R.id.addProductButton)
        compareButton = findViewById(R.id.compareButton)
        likeForLikeConfirmation = findViewById(R.id.likeForLikeConfirmation)
        priceSelectionGroup = findViewById(R.id.priceSelectionGroup)
        comparisonScreen = findViewById(R.id.compareHereScreen)
        comparisonPresenter = CompareHereManualScreenPresenter(comparisonScreen)
        shareComparisonButton = findViewById(R.id.shareComparisonButton)
        shareComparisonStatus = findViewById(R.id.shareComparisonStatus)
        shareComparisonStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        scannerStatus = findViewById(R.id.scannerStatus)
        // Optional scanner availability can change while this screen resumes. Announce the
        // projected status politely without making the legacy accessibility adapter a product
        // or shopping authority.
        scannerStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        importPhotoButton = findViewById(R.id.importPhotoButton)
        capturePhotoButton = findViewById(R.id.capturePhotoButton)
        photoImportStatus = findViewById(R.id.photoImportStatus)
        photoImportStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        cancelPhotoButton = findViewById(R.id.cancelPhotoButton)
        cancelPhotoButton.contentDescription =
            getString(R.string.compare_photo_cancel_description)
        cancelPhotoButton.setOnClickListener {
            cancelPhotoRequest()
        }
        retryPhotoButton = findViewById(R.id.retryPhotoButton)
        retryPhotoButton.contentDescription = getString(R.string.compare_photo_retry_description)
        retryPhotoButton.setOnClickListener {
            val kind = lastPhotoCaptureKind ?: return@setOnClickListener
            when (kind) {
                CompareHerePhotoCaptureKind.CAMERA -> beginCameraCapture()
                CompareHerePhotoCaptureKind.IMPORT -> beginPhotoImport()
            }
        }
        compareBarcodeButton = findViewById(R.id.compareBarcodeButton)
        compareBarcodeStatus = findViewById(R.id.compareBarcodeStatus)
        compareBarcodeStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        privateMemoryStatus = findViewById(R.id.privateMemoryStatus)
        // Private-memory outcomes (saved history, unavailable reads, and clear failures) are
        // dynamic feedback. Announce them politely while keeping the evidence and comparison
        // authorities in the existing coordinator/store layers.
        privateMemoryStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        clearPrivateMemoryButton = findViewById(R.id.clearPrivateMemoryButton)
        privateMemoryStore = CompareHerePrivatePriceMemoryAndroidStore(this)

        clearPrivateMemoryButton.setOnClickListener {
            val result = privateMemoryStore.clear()
            if (result.accepted) {
                privateMemoryStateWhenStatusRendered = CompareHerePrivatePriceMemoryState.empty()
                hidePrivateMemoryStatus()
            } else {
                privateMemoryStatus.text = getString(R.string.compare_memory_clear_error)
                privateMemoryStatus.visibility = View.VISIBLE
            }
        }

        importPhotoButton.setOnClickListener {
            beginPhotoImport()
        }

        capturePhotoButton.setOnClickListener {
            beginCameraCapture()
        }

        compareBarcodeButton.setOnClickListener {
            beginBarcodeCapture()
        }

        syncPhotoActionButtons()

        val draft = restoreDraft(savedInstanceState)
        activityState =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = draft.compared,
                observedAtEpochMillis = draft.observedAtEpochMillis,
                likeForLikeConfirmed = draft.likeForLikeConfirmed,
                priceSelection = draft.priceSelection
            )

        renderProductInputs(draft.blocks)
        val sharedTextImportIssue = applySharedTextIfPresent(savedInstanceState)
        syncLikeForLikeConfirmation()
        syncPriceSelection()

        likeForLikeConfirmation.setOnCheckedChangeListener { _, isChecked ->
            if (!restoringDraft) {
                activityState =
                    CompareHereManualActivitySessionReducer.confirmationChanged(
                        state = activityState,
                        confirmed = isChecked
                    )
                renderIdleScreen()
            }
        }

        priceSelectionGroup.setOnCheckedChangeListener { _, checkedId ->
            if (restoringDraft) {
                return@setOnCheckedChangeListener
            }

            val selection =
                when (checkedId) {
                    R.id.priceSelectionCurrent -> CompareHerePriceSelection.CURRENT
                    R.id.priceSelectionMember -> CompareHerePriceSelection.MEMBER
                    else -> return@setOnCheckedChangeListener
                }

            activityState =
                CompareHereManualActivitySessionReducer.priceSelectionChanged(
                    state = activityState,
                    selection = selection
                )
            renderIdleScreen()
        }

        addProductButton.setOnClickListener {
            if (productInputs.size < CompareHereManualInputAdapter.MAX_OBSERVATIONS) {
                addProductInput("")
                onProductsChanged()
                updateAddProductButton()
            }
        }

        compareButton.setOnClickListener {
            val now = System.currentTimeMillis()
            runComparison(
                blocks = currentProductBlocks(),
                observedAtEpochMillis = now,
                priceSelection = activityState.priceSelection,
                persist = true
            )
        }

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            clearComparison()
        }

        findViewById<Button>(R.id.enableButton).setOnClickListener {
            showAccessibilityDisclosure()
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            openAccessibilitySettings()
        }

        if (
            activityState.comparisonWasRun &&
            currentDraftActionState().compareEnabled
        ) {
            val restoreTime =
                activityState.observedAtEpochMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis()

            runComparison(
                blocks = currentProductBlocks(),
                observedAtEpochMillis = restoreTime,
                persist = false
            )
        } else {
            renderIdleScreen()
        }

        sharedTextImportIssue?.let(::showSharedTextImportFailure)
    }

    override fun onResume() {
        super.onResume()
        renderScannerStatus()
        val expected = privateMemoryStateWhenStatusRendered
        if (expected != null && privateMemoryStatus.visibility == View.VISIBLE) {
            val loaded = privateMemoryStore.load()
            if (!loaded.accepted || loaded.state != expected) {
                // Home or another ValuePilot route may have changed device-only history while
                // this screen was paused. Do not leave a stale personal-memory message visible.
                privateMemoryStateWhenStatusRendered =
                    loaded.state ?: CompareHerePrivatePriceMemoryState.empty()
                hidePrivateMemoryStatus()
            }
        }
    }

    override fun onPause() {
        saveDraftToPreferences()
        super.onPause()
    }

    override fun onDestroy() {
        photoImportClosed = true
        invalidatePhotoRequest()
        photoExecutor.shutdownNow()
        barcodeLookupClosed = true
        barcodeLookupRequestId += 1L
        barcodeDialog?.dismiss()
        barcodeDialog = null
        sharedTextDialog?.dismiss()
        sharedTextDialog = null
        barcodeLookupExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            STATE_BLOCKS,
            ArrayList(currentProductBlocks())
        )
        outState.putBoolean(
            STATE_COMPARED,
            activityState.comparisonWasRun
        )
        outState.putLong(
            STATE_OBSERVED_AT,
            activityState.observedAtEpochMillis
        )
        outState.putBoolean(
            STATE_LIKE_FOR_LIKE_CONFIRMED,
            activityState.likeForLikeConfirmed
        )
        outState.putString(
            STATE_PRICE_SELECTION,
            CompareHerePriceSelectionPersistence.encode(activityState.priceSelection)
        )

        super.onSaveInstanceState(outState)
    }

    private fun beginBarcodeCapture() {
        if (
            barcodeLookupClosed ||
                barcodeLookupInFlight ||
                photoImportInFlight ||
                photoReviewDialog != null
        ) {
            return
        }

        barcodeLookupInFlight = true
        barcodeLookupRequestId += 1L
        syncPhotoActionButtons()
        compareBarcodeStatus.text = getString(R.string.compare_barcode_processing)
        compareBarcodeStatus.visibility = View.VISIBLE

        try {
            barcodeCaptureLauncher.launch(
                Intent(this, BarcodeCaptureActivity::class.java)
            )
        } catch (_: Exception) {
            finishBarcodeRequest(R.string.compare_barcode_unavailable)
        }
    }

    private fun onBarcodeCaptureResult(resultCode: Int, gtin: String?) {
        if (barcodeLookupClosed || !barcodeLookupInFlight) {
            return
        }
        if (resultCode != RESULT_OK || gtin.isNullOrBlank()) {
            finishBarcodeRequest(R.string.compare_barcode_cancelled)
            return
        }
        beginBarcodeIdentityLookup(gtin)
    }

    private fun beginBarcodeIdentityLookup(gtin: String) {
        val trimmed = gtin.trim()
        if (barcodeLookupClosed || trimmed.isBlank()) {
            finishBarcodeRequest(R.string.compare_barcode_unavailable)
            return
        }

        val requestId = barcodeLookupRequestId
        compareBarcodeStatus.text = getString(R.string.compare_barcode_processing)
        compareBarcodeStatus.visibility = View.VISIBLE

        try {
            barcodeLookupExecutor.execute {
                val presentation =
                    runCatching {
                        GoodPriceBarcodeIdentityPresentation.from(
                            gtin = trimmed,
                            result =
                                BundledOfflineCatalog.discoverSupportedRegions(
                                    context = applicationContext,
                                    rawQuery = trimmed,
                                    canonicalizer = JvmTextCanonicalizer,
                                    evaluatedAtEpochMillis = System.currentTimeMillis(),
                                    maximumSnapshotAgeMillis = OFFLINE_CATALOG_MAX_AGE_MILLIS
                                )
                        )
                    }.getOrNull()

                runOnUiThread {
                    if (
                        barcodeLookupClosed ||
                        requestId != barcodeLookupRequestId ||
                        !barcodeLookupInFlight ||
                        isFinishing ||
                        isDestroyed
                    ) {
                        return@runOnUiThread
                    }
                    if (presentation == null) {
                        finishBarcodeRequest(R.string.compare_barcode_unavailable)
                        return@runOnUiThread
                    }
                    showBarcodeIdentityChoices(presentation)
                }
            }
        } catch (_: Throwable) {
            finishBarcodeRequest(R.string.compare_barcode_unavailable)
        }
    }

    private fun showBarcodeIdentityChoices(
        presentation: GoodPriceBarcodeIdentityPresentation
    ) {
        if (presentation.options.isEmpty()) {
            finishBarcodeRequest(
                R.string.compare_barcode_no_match,
                presentation.gtin
            )
            return
        }

        var selectedIndex = if (presentation.options.size == 1) 0 else -1
        lateinit var dialog: AlertDialog
        val builder =
            AlertDialog.Builder(this)
                .setTitle(R.string.compare_barcode_match_title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.compare_barcode_use_name, null)

        if (presentation.options.size == 1) {
            builder.setMessage(
                getString(
                    R.string.compare_barcode_match_message,
                    presentation.options.single().label
                )
            )
        } else {
            builder
                .setMessage(
                    getString(
                        R.string.compare_barcode_multiple_message,
                        presentation.gtin
                    )
                )
                .setSingleChoiceItems(
                    presentation.options.map { it.label }.toTypedArray(),
                    -1
                ) { _, which ->
                    selectedIndex = which
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
        }

        dialog = builder.create()
        barcodeDialog = dialog
        dialog.setOnDismissListener {
            if (barcodeDialog === dialog) {
                barcodeDialog = null
            }
            if (barcodeLookupInFlight) {
                barcodeLookupInFlight = false
                syncPhotoActionButtons()
            }
        }
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.contentDescription =
                getString(R.string.compare_barcode_use_name_description)
            button.isEnabled = selectedIndex >= 0
            button.setOnClickListener {
                val option = presentation.options.getOrNull(selectedIndex)
                    ?: return@setOnClickListener
                val result =
                    CompareHereBarcodeDraft.apply(
                        existingBlocks = currentProductBlocks(),
                        displayName = option.displayName
                    )
                if (!result.added) {
                    compareBarcodeStatus.text =
                        getString(
                            when (result.issue) {
                                CompareHereBarcodeDraftIssue.IDENTITY_TOO_LONG ->
                                    R.string.compare_barcode_identity_too_long
                                CompareHereBarcodeDraftIssue.NO_EMPTY_SLOT ->
                                    R.string.compare_barcode_no_empty_slot
                                else -> R.string.compare_barcode_unavailable
                            }
                        )
                    compareBarcodeStatus.visibility = View.VISIBLE
                    dialog.dismiss()
                    return@setOnClickListener
                }

                renderProductInputs(result.blocks)
                activityState =
                    CompareHereManualActivitySessionReducer.productsChanged(activityState)
                syncLikeForLikeConfirmation()
                syncPriceSelection()
                renderIdleScreen()
                saveDraftToPreferences()
                compareBarcodeStatus.text =
                    getString(
                        R.string.compare_barcode_used,
                        option.label,
                        presentation.gtin
                    )
                compareBarcodeStatus.visibility = View.VISIBLE
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun finishBarcodeRequest(@StringRes statusRes: Int, vararg formatArgs: Any) {
        barcodeLookupInFlight = false
        syncPhotoActionButtons()
        compareBarcodeStatus.text = getString(statusRes, *formatArgs)
        compareBarcodeStatus.visibility = View.VISIBLE
    }

    private fun clearBarcodeStatus() {
        barcodeLookupRequestId += 1L
        barcodeDialog?.dismiss()
        barcodeDialog = null
        barcodeLookupInFlight = false
        compareBarcodeStatus.text = getString(R.string.compare_barcode_ready)
        compareBarcodeStatus.visibility = View.VISIBLE
        syncPhotoActionButtons()
    }

    private fun beginPhotoImport() {
        if (
            photoImportClosed ||
                photoImportInFlight ||
                barcodeLookupInFlight ||
                photoReviewDialog != null
        ) {
            return
        }

        lastPhotoCaptureKind = CompareHerePhotoCaptureKind.IMPORT
        hidePhotoRetry()
        beginPhotoRequest()
        syncPhotoActionButtons()
        photoImportStatus.text = getString(R.string.compare_photo_processing)
        try {
            photoPickerLauncher.launch("image/*")
        } catch (_: Exception) {
            finishPhotoRequest(
                R.string.compare_photo_error,
                retryOutcome = CompareHerePhotoRetryOutcome.CAPTURE_FAILURE
            )
        }
    }

    private fun beginCameraCapture() {
        if (
            photoImportClosed ||
                photoImportInFlight ||
                barcodeLookupInFlight ||
                photoReviewDialog != null
        ) {
            return
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            finishPhotoRequest(R.string.compare_camera_unavailable)
            return
        }

        lastPhotoCaptureKind = CompareHerePhotoCaptureKind.CAMERA
        hidePhotoRetry()
        beginPhotoRequest()
        cameraCaptureRequestId = photoRequestId
        syncPhotoActionButtons()

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraCapture(cameraCaptureRequestId)
        } else {
            photoImportStatus.text = getString(R.string.compare_camera_permission_needed)
            try {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (_: Exception) {
                finishPhotoRequest(
                    R.string.compare_camera_error,
                    retryOutcome = CompareHerePhotoRetryOutcome.CAPTURE_FAILURE
                )
            }
        }
    }

    private fun onCameraPermissionResult(granted: Boolean) {
        if (
            photoImportClosed ||
            !photoImportInFlight ||
            cameraCaptureRequestId != photoRequestId
        ) {
            return
        }

        if (!granted) {
            finishPhotoRequest(
                R.string.compare_camera_permission_denied,
                retryOutcome = CompareHerePhotoRetryOutcome.PERMISSION_DENIED
            )
            return
        }

        launchCameraCapture(cameraCaptureRequestId)
    }

    private fun launchCameraCapture(requestId: Long) {
        if (
            photoImportClosed ||
            !photoImportInFlight ||
            requestId != photoRequestId
        ) {
            return
        }

        try {
            val directory = File(cacheDir, CAMERA_CACHE_DIRECTORY).apply {
                mkdirs()
            }
            val file = File.createTempFile(CAMERA_FILE_PREFIX, CAMERA_FILE_SUFFIX, directory)
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    file
                )
            cameraCaptureFile = file
            cameraCaptureUri = uri
            photoImportStatus.text = getString(R.string.compare_photo_processing)
            cameraCaptureLauncher.launch(uri)
        } catch (_: Exception) {
            cleanupCameraCaptureFile()
            finishPhotoRequest(
                R.string.compare_camera_error,
                retryOutcome = CompareHerePhotoRetryOutcome.CAPTURE_FAILURE
            )
        }
    }

    private fun onCameraCaptureCompleted(captured: Boolean) {
        val requestId = cameraCaptureRequestId
        val uri = cameraCaptureUri
        if (
            photoImportClosed ||
            requestId != photoRequestId ||
            !photoImportInFlight
        ) {
            cleanupCameraCaptureFile()
            return
        }

        cameraCaptureUri = null
        if (!captured || uri == null) {
            cleanupCameraCaptureFile()
            finishPhotoRequest(R.string.compare_camera_cancelled)
            return
        }

        onPhotoSelected(uri, cleanupFile = cameraCaptureFile)
    }

    private fun onPhotoSelected(uri: Uri?, cleanupFile: File?) {
        if (photoImportClosed || !photoImportInFlight) {
            cleanupFile?.let { deleteCameraCaptureFile(it) }
            return
        }

        val requestId = photoRequestId
        if (uri == null) {
            cleanupFile?.let { deleteCameraCaptureFile(it) }
            finishPhotoRequest(R.string.compare_photo_cancelled)
            return
        }

        try {
            photoExecutor.execute {
                val bitmap = decodeBoundedPhoto(uri)
                if (bitmap == null) {
                    cleanupFile?.let { deleteCameraCaptureFile(it) }
                    postPhotoImportResult(requestId, emptyList(), IllegalArgumentException("decode"))
                    return@execute
                }

                try {
                    OcrScanner.scan(bitmap) { recognizedBlocks, error ->
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                        cleanupFile?.let { deleteCameraCaptureFile(it) }
                        postPhotoImportResult(requestId, recognizedBlocks, error)
                    }
                } catch (error: Throwable) {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    cleanupFile?.let { deleteCameraCaptureFile(it) }
                    postPhotoImportResult(requestId, emptyList(), error)
                }
            }
        } catch (error: Throwable) {
            cleanupFile?.let { deleteCameraCaptureFile(it) }
            postPhotoImportResult(requestId, emptyList(), error)
        }
    }

    private fun postPhotoImportResult(
        requestId: Long,
        recognizedBlocks: List<String>,
        error: Throwable?
    ) {
        runOnUiThread {
            applyPhotoImportResult(requestId, recognizedBlocks, error)
        }
    }

    private fun applyPhotoImportResult(
        requestId: Long,
        recognizedBlocks: List<String>,
        error: Throwable?
    ) {
        if (
            !CompareHerePhotoRequestPolicy.accepts(
                callbackRequestId = requestId,
                current =
                    CompareHerePhotoRequestState(
                        requestId = photoRequestId,
                        inFlight = photoImportInFlight
                    ),
                closed = photoImportClosed || isFinishing || isDestroyed
            )
        ) {
            return
        }

        photoImportInFlight = false
        syncPhotoActionButtons()

        if (error != null) {
            photoImportStatus.text = getString(R.string.compare_photo_error)
            showPhotoRetryIfEligible(CompareHerePhotoRetryOutcome.OCR_FAILURE)
            return
        }

        val review =
            CompareHerePhotoDraft.review(
                existingBlocks = currentProductBlocks(),
                recognizedBlocks = recognizedBlocks
            )

        if (review.candidates.isEmpty()) {
            photoImportStatus.text = getString(R.string.compare_photo_no_matches)
            showPhotoRetryIfEligible(CompareHerePhotoRetryOutcome.NO_USABLE_SUGGESTION)
            return
        }

        showPhotoReviewDialog(
            requestId = requestId,
            review = review
        )
    }

    /**
     * Keeps OCR in a proposal-only state until the shopper explicitly selects what to add. The
     * selected values still go through the existing bounded draft helper and exact route.
     */
    private fun showPhotoReviewDialog(
        requestId: Long,
        review: CompareHerePhotoDraftReview
    ) {
        if (
            photoImportClosed ||
                requestId != photoRequestId ||
                isFinishing ||
                isDestroyed
        ) {
            return
        }

        val selected = BooleanArray(review.candidates.size) { true }
        var outcomeCommitted = false
        lateinit var dialog: AlertDialog
        dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.compare_photo_review_title)
                .setMessage(
                    getString(
                        R.string.compare_photo_review_body,
                        review.candidates.size
                    )
                )
                .setMultiChoiceItems(
                    review.candidates.map(::photoSuggestionLabel).toTypedArray(),
                    selected
                ) { _, which, checked ->
                    if (which in selected.indices) {
                        selected[which] = checked
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.compare_photo_add_selected, null)
                .create()

        photoReviewDialog = dialog
        photoReviewRequestId = requestId
        dialog.setOnDismissListener {
            if (
                !outcomeCommitted &&
                    !photoImportClosed &&
                    requestId == photoRequestId
            ) {
                photoImportStatus.text = getString(R.string.compare_photo_cancelled)
                photoImportStatus.visibility = View.VISIBLE
            }
            if (photoReviewDialog === dialog) {
                photoReviewDialog = null
                photoReviewRequestId = 0L
                syncPhotoActionButtons()
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (
                    photoImportClosed ||
                        requestId != photoRequestId ||
                        photoReviewRequestId != requestId ||
                        photoReviewDialog !== dialog ||
                        isFinishing ||
                        isDestroyed
                ) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                val selectedCandidates =
                    review.candidates.filterIndexed { index, _ -> selected[index] }
                if (selectedCandidates.isEmpty()) {
                    outcomeCommitted = true
                    photoImportStatus.text = getString(R.string.compare_photo_none_selected)
                    photoImportStatus.visibility = View.VISIBLE
                    dialog.dismiss()
                    return@setOnClickListener
                }

                val result =
                    CompareHerePhotoDraft.append(
                        existingBlocks = currentProductBlocks(),
                        recognizedBlocks = selectedCandidates
                    )
                if (result.addedCount == 0) {
                    outcomeCommitted = true
                    photoImportStatus.text = getString(R.string.compare_photo_no_matches)
                    photoImportStatus.visibility = View.VISIBLE
                    dialog.dismiss()
                    return@setOnClickListener
                }

                renderProductInputs(result.blocks)
                activityState =
                    CompareHereManualActivitySessionReducer.productsChanged(activityState)
                syncLikeForLikeConfirmation()
                syncPriceSelection()
                renderIdleScreen()
                saveDraftToPreferences()
                photoImportStatus.text =
                    getString(
                        R.string.compare_photo_added,
                        result.addedCount,
                        review.skippedCount + result.skippedCount
                    )
                photoImportStatus.visibility = View.VISIBLE
                hidePhotoRetry()
                outcomeCommitted = true
                dialog.dismiss()
            }
        }
        syncPhotoActionButtons()
        dialog.show()
    }

    private fun photoSuggestionLabel(value: String): String {
        return CompareHerePhotoSuggestionPresentationFactory
            .forCandidate(value)
            .displayLabel
    }

    private fun finishPhotoRequest(@StringRes statusRes: Int) {
        finishPhotoRequest(statusRes, retryOutcome = null)
    }

    private fun finishPhotoRequest(
        @StringRes statusRes: Int,
        retryOutcome: CompareHerePhotoRetryOutcome?
    ) {
        photoImportInFlight = false
        syncPhotoActionButtons()
        photoImportStatus.text = getString(statusRes)
        showPhotoRetryIfEligible(retryOutcome)
    }

    private fun beginPhotoRequest() {
        val next =
            CompareHerePhotoRequestPolicy.begin(
                CompareHerePhotoRequestState(
                    requestId = photoRequestId,
                    inFlight = photoImportInFlight
                )
            )
        photoRequestId = next.requestId
        photoImportInFlight = next.inFlight
    }

    /**
     * Invalidates the current photo generation before a draft/lifecycle transition can expose a
     * result from the old draft. This does not cancel OCR work internally; its callback is simply
     * rejected and any temporary camera file is removed.
     */
    private fun invalidatePhotoRequest() {
        val next =
            CompareHerePhotoRequestPolicy.invalidate(
                CompareHerePhotoRequestState(
                    requestId = photoRequestId,
                    inFlight = photoImportInFlight
                )
            )
        photoRequestId = next.requestId
        photoImportInFlight = next.inFlight
        photoReviewRequestId = 0L
        photoReviewDialog?.dismiss()
        photoReviewDialog = null
        cameraCaptureRequestId = 0L
        cleanupCameraCaptureFile()
        lastPhotoCaptureKind = null
        hidePhotoRetry()
        syncPhotoActionButtons()
    }

    private fun cancelPhotoRequest() {
        if (photoImportClosed || !photoImportInFlight) return
        invalidatePhotoRequest()
        photoImportStatus.text = getString(R.string.compare_photo_cancelled_by_user)
        photoImportStatus.visibility = View.VISIBLE
    }

    private fun cancelPhotoRequestForDraftChange() {
        if (
            !photoImportInFlight &&
                photoReviewDialog == null &&
                cameraCaptureFile == null
        ) {
            return
        }

        invalidatePhotoRequest()
        photoImportStatus.text = getString(R.string.compare_photo_draft_changed)
        photoImportStatus.visibility = View.VISIBLE
    }

    private fun showPhotoRetryIfEligible(outcome: CompareHerePhotoRetryOutcome?) {
        if (
            !CompareHerePhotoRetryPolicy.shouldOfferRetry(
                captureKind = lastPhotoCaptureKind,
                outcome = outcome
            )
        ) {
            hidePhotoRetry()
            return
        }

        retryPhotoButton.visibility = View.VISIBLE
        retryPhotoButton.isEnabled = !photoImportClosed
    }

    private fun hidePhotoRetry() {
        if (!::retryPhotoButton.isInitialized) return
        retryPhotoButton.visibility = View.GONE
        retryPhotoButton.isEnabled = false
    }

    private fun syncPhotoActionButtons() {
        val enabled =
            !photoImportInFlight &&
                !barcodeLookupInFlight &&
                photoReviewDialog == null &&
                !photoImportClosed &&
                !barcodeLookupClosed
        if (::importPhotoButton.isInitialized) {
            importPhotoButton.isEnabled = enabled
        }
        if (::capturePhotoButton.isInitialized) {
            capturePhotoButton.isEnabled = enabled
        }
        if (::cancelPhotoButton.isInitialized) {
            cancelPhotoButton.visibility =
                if (photoImportInFlight && !photoImportClosed && photoReviewDialog == null) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            cancelPhotoButton.isEnabled = photoImportInFlight && !photoImportClosed
        }
        if (::compareBarcodeButton.isInitialized) {
            compareBarcodeButton.isEnabled = enabled
        }
        if (::retryPhotoButton.isInitialized) {
            retryPhotoButton.isEnabled =
                retryPhotoButton.visibility == View.VISIBLE && enabled
        }
    }

    private fun cleanupCameraCaptureFile() {
        cameraCaptureFile?.let { deleteCameraCaptureFile(it) }
        cameraCaptureFile = null
        cameraCaptureUri = null
    }

    private fun deleteCameraCaptureFile(file: File) {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
        if (cameraCaptureFile == file) {
            cameraCaptureFile = null
            cameraCaptureUri = null
        }
    }

    /** Decode an image to a bounded bitmap before handing it to on-device OCR. */
    private fun decodeBoundedPhoto(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) return null
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }

            val largestDimension = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (largestDimension / sampleSize > MAX_PHOTO_DIMENSION) {
                sampleSize *= 2
            }

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

    private fun renderProductInputs(blocks: List<String>) {
        restoringDraft = true

        productInputs.clear()
        productInputRows.clear()
        productInputsContainer.removeAllViews()

        val initialBlocks =
            CompareHereManualProductDraft.prepareForEditor(blocks)
                .toMutableList()

        while (initialBlocks.size < 2) {
            initialBlocks += CompareHereManualEditorBlock(text = "")
        }

        initialBlocks.forEach { block ->
            addProductInput(
                initialText = block.text,
                initialIssue = block.issue
            )
        }

        shareComparisonButton.setOnClickListener {
            shareComparisonResult()
        }

        restoringDraft = false
        updateAddProductButton()
    }

    private fun addProductInput(
        initialText: String,
        initialIssue: CompareHereManualEditorBlockIssue? = null
    ) {
        val index = productInputs.size + 1

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
            )

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }

            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F9FAFB"))
                setStroke(
                    dp(1),
                    Color.parseColor("#D1D5DB")
                )
            }
        }

        val label = TextView(this).apply {
            text = getString(
                R.string.product_number,
                index
            )
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                14f
            )
            setTextColor(Color.parseColor("#374151"))
            setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
        }

        val input = EditText(this).apply {
            hint = getString(R.string.product_input_hint)
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            maxLines = 9
            setHorizontallyScrolling(false)

            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters =
                arrayOf(
                    InputFilter.LengthFilter(
                        CompareHereManualProductDraft.MAX_BLOCK_CHARS
                    )
                )

            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                16f
            )
            setTextColor(Color.parseColor("#111827"))
            setHintTextColor(Color.parseColor("#9CA3AF"))
            setPadding(
                0,
                dp(8),
                0,
                0
            )
            background = null
            setText(initialText)
        }

        val removeButton = Button(this).apply {
            text = getString(R.string.remove_product)
            contentDescription = getString(R.string.remove_product_description, index)
            isAllCaps = false
            setOnClickListener { removeProductInput(input) }
        }

        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (restoringDraft) {
                        return
                    }

                    input.error = null
                    onProductsChanged()
                }
            }
        )

        if (initialIssue == CompareHereManualEditorBlockIssue.TOO_LONG) {
            input.error = getString(R.string.product_input_too_long)
        }

        card.addView(label)
        card.addView(input)
        card.addView(removeButton)

        productInputs += input
        productInputRows += ProductInputRow(card, label, removeButton)
        productInputsContainer.addView(card)

        updateAddProductButton()
        updateRemoveProductButtons()
    }

    private fun removeProductInput(input: EditText) {
        val index = productInputs.indexOf(input)
        if (index < 0) return

        val nextBlocks =
            CompareHereManualProductDraft.removeAt(
                blocks = currentProductBlocks(),
                index = index
            )

        if (productInputs.size <= 2) {
            input.setText(nextBlocks[index])
            return
        }

        productInputs.removeAt(index)
        val row = productInputRows.removeAt(index)
        productInputsContainer.removeView(row.card)
        refreshProductInputRows()
        updateAddProductButton()
        onProductsChanged()
    }

    private fun refreshProductInputRows() {
        productInputRows.forEachIndexed { index, row ->
            row.label.text = getString(R.string.product_number, index + 1)
            row.removeButton.contentDescription =
                getString(R.string.remove_product_description, index + 1)
        }
        updateRemoveProductButtons()
    }

    private fun updateRemoveProductButtons() {
        val visible = if (productInputs.size > 2) View.VISIBLE else View.GONE
        productInputRows.forEach { row -> row.removeButton.visibility = visible }
    }

    private fun currentProductBlocks(): List<String> =
        productInputs.map {
            it.text?.toString().orEmpty()
        }

    private fun runComparison(
        blocks: List<String>,
        observedAtEpochMillis: Long,
        priceSelection: CompareHerePriceSelection = activityState.priceSelection,
        persist: Boolean
    ) {
        val evaluation =
            CompareHereManualRouteCoordinator.evaluateBlocks(
                rawBlocks = blocks,
                observedAtEpochMillis = observedAtEpochMillis,
                userConfirmedLikeForLike = activityState.likeForLikeConfirmed,
                priceSelection = priceSelection
            )

        activityState =
            CompareHereManualActivitySessionReducer.comparisonAttempted(
                state = activityState,
                observedAtEpochMillis = observedAtEpochMillis
            )

        comparisonPresenter.render(evaluation.state)
        renderShareCard(evaluation.state.shareCard)

        if (persist) {
            updatePrivateMemoryStatus(evaluation.privateMemoryCapture)
            saveDraftToPreferences()
        }
    }

    private fun onProductsChanged() {
        cancelPhotoRequestForDraftChange()
        clearBarcodeStatus()
        activityState =
            CompareHereManualActivitySessionReducer.productsChanged(activityState)
        syncLikeForLikeConfirmation()
        renderIdleScreen()
    }

    private fun syncLikeForLikeConfirmation() {
        if (likeForLikeConfirmation.isChecked == activityState.likeForLikeConfirmed) {
            return
        }

        restoringDraft = true
        likeForLikeConfirmation.isChecked = activityState.likeForLikeConfirmed
        restoringDraft = false
    }

    private fun syncPriceSelection() {
        val selectedId =
            if (activityState.priceSelection == CompareHerePriceSelection.MEMBER) {
                R.id.priceSelectionMember
            } else {
                R.id.priceSelectionCurrent
            }

        if (priceSelectionGroup.checkedRadioButtonId == selectedId) {
            return
        }

        restoringDraft = true
        priceSelectionGroup.check(selectedId)
        restoringDraft = false
    }

    private fun renderIdleScreen() {
        hidePrivateMemoryStatus()
        renderShareCard(null)
        val actionState = currentDraftActionState()
        compareButton.isEnabled = actionState.compareEnabled
        val content =
            when (actionState.readiness) {
                CompareHereManualDraftReadiness.ADD_PRODUCTS ->
                    CompareHereManualScreenContent.Message(
                        title = getString(R.string.compare_add_products_title),
                        guidance = getString(R.string.compare_add_products_body)
                    )

                CompareHereManualDraftReadiness.CONFIRM_LIKE_FOR_LIKE ->
                    CompareHereManualScreenContent.Message(
                        title = getString(R.string.compare_confirmation_needed_title),
                        guidance = getString(R.string.compare_confirmation_needed_body)
                    )

                CompareHereManualDraftReadiness.READY ->
                    CompareHereManualScreenContent.Message(
                        title = getString(R.string.compare_ready_title),
                        guidance = getString(R.string.compare_ready_body)
                    )
            }

        comparisonScreen.render(content)
    }

    private fun currentDraftActionState(): CompareHereManualDraftActionState =
        CompareHereManualDraftActionEvaluator.evaluate(
            rawBlocks = currentProductBlocks(),
            likeForLikeConfirmed = activityState.likeForLikeConfirmed
        )

    /**
     * Shows sharing only for the immutable, fully labelled READY result returned by the route.
     * The preview and outgoing text are generated by [CompareHereShareCardProjector] and contain
     * no product names, private history, receipt, location or account data.
     */
    private fun renderShareCard(card: CompareHereShareCard?) {
        shareCard = card
        shareComparisonButton.visibility = if (card == null) View.GONE else View.VISIBLE
        shareComparisonButton.isEnabled = card != null
        shareComparisonButton.contentDescription =
            if (card == null) {
                ""
            } else {
                getString(R.string.compare_share_result_description)
            }
        if (card == null) {
            shareComparisonStatus.text = ""
            shareComparisonStatus.visibility = View.GONE
        }
    }

    private fun shareComparisonResult() {
        val card = shareCard ?: return
        val preview =
            card.preview + "\n\n" + getString(R.string.compare_share_preview_body)
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.compare_share_preview_title)
                .setMessage(preview)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.compare_share_send, null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, card.title)
                        putExtra(Intent.EXTRA_TEXT, card.text)
                    }
                try {
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            getString(R.string.compare_share_result)
                        )
                    )
                    dialog.dismiss()
                } catch (_: ActivityNotFoundException) {
                    shareComparisonStatus.text =
                        getString(R.string.compare_share_unavailable)
                    shareComparisonStatus.visibility = View.VISIBLE
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun clearComparison() {
        cancelPhotoRequestForDraftChange()
        clearBarcodeStatus()
        hidePhotoRetry()
        hidePrivateMemoryStatus()
        activityState = CompareHereManualActivitySessionReducer.clear()
        syncLikeForLikeConfirmation()
        syncPriceSelection()

        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .clear()
            .apply()

        renderProductInputs(
            listOf("", "")
        )

        renderIdleScreen()
    }

    /**
     * Applies one intentionally shared text value to the earliest empty Compare Here slot. The
     * source remains raw and reviewable; this never replaces an existing entry or parses it in the
     * lifecycle owner. Instance-state restoration wins so rotation cannot apply the share twice.
     */
    private fun applySharedTextIfPresent(
        savedInstanceState: Bundle?
    ): CompareHereSharedTextDraftIssue? {
        if (savedInstanceState != null) return null

        val rawText =
            runCatching {
                intent?.getStringExtra(EXTRA_SHARED_TEXT)
            }.getOrNull()
                ?: return null
        val input = ShareToValuePilotInput.validate(rawText)
        val sharedText =
            input.text
                ?: return when (input.issue) {
                    ShareToValuePilotInputIssue.EMPTY ->
                        CompareHereSharedTextDraftIssue.BLANK_TEXT
                    ShareToValuePilotInputIssue.TOO_LONG ->
                        CompareHereSharedTextDraftIssue.TEXT_TOO_LONG
                    null -> null
                }

        val result =
            CompareHereSharedTextDraft.apply(
                existingBlocks = currentProductBlocks(),
                sharedText = sharedText
            )
        if (!result.added) return result.issue

        restoringDraft = true
        renderProductInputs(result.blocks)
        restoringDraft = false
        onProductsChanged()
        return null
    }

    private fun showSharedTextImportFailure(
        issue: CompareHereSharedTextDraftIssue
    ) {
        val message =
            when (issue) {
                CompareHereSharedTextDraftIssue.BLANK_TEXT ->
                    getString(R.string.compare_shared_text_empty_body)
                CompareHereSharedTextDraftIssue.TEXT_TOO_LONG ->
                    getString(
                        R.string.share_to_valuepilot_too_large_guidance,
                        ShareToValuePilotInput.MAX_CHARS
                    )
                CompareHereSharedTextDraftIssue.NO_EMPTY_SLOT ->
                    getString(R.string.compare_shared_text_no_empty_slot_body)
            }

        sharedTextDialog?.dismiss()
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    when (issue) {
                        CompareHereSharedTextDraftIssue.BLANK_TEXT ->
                            R.string.share_to_valuepilot_empty_title
                        CompareHereSharedTextDraftIssue.TEXT_TOO_LONG ->
                            R.string.share_to_valuepilot_too_large_title
                        CompareHereSharedTextDraftIssue.NO_EMPTY_SLOT ->
                            R.string.compare_shared_text_no_empty_slot_title
                    }
                )
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create()
        dialog.setOnDismissListener {
            if (sharedTextDialog === dialog) sharedTextDialog = null
        }
        sharedTextDialog = dialog
        dialog.show()
    }

    private fun updatePrivateMemoryStatus(
        capture: CompareHerePrivatePriceMemoryCapture?
    ) {
        if (capture == null) {
            privateMemoryStateWhenStatusRendered = null
            hidePrivateMemoryStatus()
            return
        }

        val loaded = privateMemoryStore.load()
        val insights =
            if (loaded.accepted) {
                capture.entries
                    .map { entry ->
                        CompareHerePriceMemoryEvaluator.assess(
                            current = entry,
                            history = requireNotNull(loaded.state)
                        )
                    }
                    .filterNot {
                        it.assessment == CompareHerePriceMemoryAssessment.NO_MATCHING_HISTORY
                    }
                    .sortedBy { it.displayName.lowercase() }
                    .take(3)
            } else {
                emptyList()
            }

        val result = privateMemoryStore.append(capture)
        if (result.accepted) {
            privateMemoryStateWhenStatusRendered = requireNotNull(result.state)
            val savedText = getString(R.string.compare_memory_saved, capture.entries.size)
            val summaries =
                capture.entries
                    .mapNotNull { entry ->
                        result.state?.let { state ->
                            CompareHerePriceMemoryHistory.summarize(
                                current = entry,
                                state = state
                            )
                        }
                    }
                    .sortedBy { it.displayName.lowercase() }
                    .take(3)
            val insightText =
                summaries
                    .map { summary ->
                        val insight = insights.firstOrNull { it.displayName == summary.displayName }
                        buildString {
                            append(summary.displayName)
                            insight?.let {
                                append(": ")
                                append(CompareHerePriceMemoryInsightPresenter.describe(it))
                            }
                            append(if (insight == null) ": " else " ")
                            append(CompareHerePriceMemoryInsightPresenter.describeHistory(summary))
                        }
                    }
                    .joinToString(separator = "\n")
            privateMemoryStatus.text =
                if (insightText.isBlank()) savedText else "$savedText\n$insightText"
            privateMemoryStatus.setTextColor(
                if (
                    insights.any {
                        it.assessment == CompareHerePriceMemoryAssessment.ABOVE_PERSONAL_RANGE ||
                            it.assessment == CompareHerePriceMemoryAssessment.HIGHER_THAN_LAST
                    }
                ) {
                    Color.parseColor("#92400E")
                } else {
                    Color.parseColor("#047857")
                }
            )
            privateMemoryStatus.visibility = View.VISIBLE
            clearPrivateMemoryButton.visibility = View.VISIBLE
        } else {
            privateMemoryStatus.text = getString(R.string.compare_memory_error)
            privateMemoryStatus.setTextColor(Color.parseColor("#92400E"))
            privateMemoryStatus.visibility = View.VISIBLE
            clearPrivateMemoryButton.visibility = View.VISIBLE
        }
    }

    private fun hidePrivateMemoryStatus() {
        if (!::privateMemoryStatus.isInitialized) return
        privateMemoryStatus.text = ""
        privateMemoryStatus.visibility = View.GONE
        if (::clearPrivateMemoryButton.isInitialized) {
            clearPrivateMemoryButton.visibility = View.GONE
        }
    }

    private fun updateAddProductButton() {
        if (::addProductButton.isInitialized) {
            addProductButton.isEnabled =
                productInputs.size < CompareHereManualInputAdapter.MAX_OBSERVATIONS
        }
    }

    private fun saveDraftToPreferences() {
        val blocks = currentProductBlocks()

        val prefs = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

        if (
            blocks.all { it.isBlank() } &&
            !activityState.comparisonWasRun
        ) {
            prefs.edit()
                .clear()
                .apply()
            return
        }

        val editor =
            prefs.edit()
                .clear()
                .putInt(
                    PREF_COUNT,
                    blocks.size
                )
                .putBoolean(
                    PREF_COMPARED,
                    activityState.comparisonWasRun
                )
                .putLong(
                    PREF_OBSERVED_AT,
                    activityState.observedAtEpochMillis
                )
                .putBoolean(
                    PREF_LIKE_FOR_LIKE_CONFIRMED,
                    activityState.likeForLikeConfirmed
                )
                .putString(
                    PREF_PRICE_SELECTION,
                    CompareHerePriceSelectionPersistence.encode(activityState.priceSelection)
                )

        blocks.forEachIndexed { index, value ->
            editor.putString(
                "$PREF_BLOCK_PREFIX$index",
                value
            )
        }

        editor.apply()
    }

    private fun restoreDraft(
        savedInstanceState: Bundle?
    ): Draft {
        if (
            savedInstanceState != null &&
            savedInstanceState.containsKey(STATE_BLOCKS)
        ) {
            return Draft(
                blocks =
                    savedInstanceState
                        .getStringArrayList(STATE_BLOCKS)
                        ?.toList()
                        .orEmpty(),
                compared =
                    savedInstanceState.getBoolean(
                        STATE_COMPARED,
                        false
                    ),
                observedAtEpochMillis =
                    savedInstanceState.getLong(
                        STATE_OBSERVED_AT,
                        0L
                    ),
                likeForLikeConfirmed =
                    savedInstanceState.getBoolean(
                        STATE_LIKE_FOR_LIKE_CONFIRMED,
                        false
                    ),
                priceSelection =
                    CompareHerePriceSelectionPersistence.decode(
                        savedInstanceState.getString(STATE_PRICE_SELECTION)
                    )
            )
        }

        val prefs = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

        val count =
            prefs
                .getInt(PREF_COUNT, 0)
                .coerceIn(
                    0,
                    CompareHereManualInputAdapter.MAX_OBSERVATIONS
                )

        if (count == 0) {
            return Draft(
                blocks = listOf("", ""),
                compared = false,
                observedAtEpochMillis = 0L,
                likeForLikeConfirmed = false,
                priceSelection = CompareHerePriceSelection.CURRENT
            )
        }

        val blocks =
            (0 until count).map { index ->
                prefs.getString(
                    "$PREF_BLOCK_PREFIX$index",
                    ""
                ).orEmpty()
            }

        return Draft(
            blocks = blocks,
            compared = prefs.getBoolean(
                PREF_COMPARED,
                false
            ),
            observedAtEpochMillis =
                prefs.getLong(
                    PREF_OBSERVED_AT,
                    0L
                ),
            likeForLikeConfirmed =
                prefs.getBoolean(
                    PREF_LIKE_FOR_LIKE_CONFIRMED,
                    false
                ),
            priceSelection =
                CompareHerePriceSelectionPersistence.decode(
                    prefs.getString(PREF_PRICE_SELECTION, null)
                )
        )
    }

    private fun renderScannerStatus() {
        val enabled = isServiceEnabled()

        scannerStatus.text =
            if (enabled) {
                getString(R.string.scanner_on)
            } else {
                getString(R.string.scanner_off)
            }

        scannerStatus.setTextColor(
            if (enabled) {
                Color.parseColor("#047857")
            } else {
                Color.parseColor("#6B7280")
            }
        )
    }

    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle(
                R.string.accessibility_disclosure_title
            )
            .setMessage(
                R.string.accessibility_disclosure_body
            )
            .setNegativeButton(
                R.string.cancel,
                null
            )
            .setPositiveButton(
                R.string.continue_label
            ) { _, _ ->
                openAccessibilitySettings()
            }
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    private fun isServiceEnabled(): Boolean {
        val expected =
            "$packageName/${ValueAccessibilityService::class.java.name}"

        val enabledServices =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

        val splitter =
            TextUtils.SimpleStringSplitter(':')
                .apply {
                    setString(enabledServices)
                }

        return splitter.any {
            it.equals(expected, true)
        }
    }

    private fun installSystemBarInsets() {
        val root =
            findViewById<View>(
                R.id.comparisonRoot
            )

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(
            root
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density)
            .toInt()

    private data class Draft(
        val blocks: List<String>,
        val compared: Boolean,
        val observedAtEpochMillis: Long,
        val likeForLikeConfirmed: Boolean,
        val priceSelection: CompareHerePriceSelection
    )

    private data class ProductInputRow(
        val card: View,
        val label: TextView,
        val removeButton: Button
    )

    companion object {
        const val EXTRA_SHARED_TEXT =
            "com.valuepilot.app.extra.SHARED_TEXT"

        private const val PREFS_NAME =
            "standalone_comparison_draft"

        private const val PREF_COUNT =
            "product_count"

        private const val PREF_COMPARED =
            "comparison_was_run"

        private const val PREF_OBSERVED_AT =
            "observed_at"

        private const val PREF_LIKE_FOR_LIKE_CONFIRMED =
            "like_for_like_confirmed"

        private const val PREF_PRICE_SELECTION =
            "price_selection"

        private const val PREF_BLOCK_PREFIX =
            "product_"

        private const val MAX_PHOTO_DIMENSION =
            2_048

        private const val CAMERA_CACHE_DIRECTORY =
            "camera"

        private const val CAMERA_FILE_PREFIX =
            "valuepilot-price-"

        private const val CAMERA_FILE_SUFFIX =
            ".jpg"

        private const val OFFLINE_CATALOG_MAX_AGE_MILLIS =
            8L * 24L * 60L * 60L * 1_000L

        private const val STATE_BLOCKS =
            "standalone.blocks"

        private const val STATE_COMPARED =
            "standalone.compared"

        private const val STATE_OBSERVED_AT =
            "standalone.observed_at"

        private const val STATE_LIKE_FOR_LIKE_CONFIRMED =
            "standalone.like_for_like_confirmed"

        private const val STATE_PRICE_SELECTION =
            "standalone.price_selection"
    }

    /** Adds the URI grants that the stock TakePicture contract intentionally leaves to callers. */
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

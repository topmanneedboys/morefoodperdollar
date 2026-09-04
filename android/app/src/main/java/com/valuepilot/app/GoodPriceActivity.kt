package com.valuepilot.app

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.valuepilot.core.CompareHerePriceSelection
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * First-class single-product price question. All recognition and exact math stay in the
 * provider-neutral coordinator; this Activity only owns bounded input and local persistence.
 */
class GoodPriceActivity : AppCompatActivity() {
    private lateinit var productInput: TextInputEditText
    private lateinit var priceSelectionGroup: RadioGroup
    private lateinit var checkButton: Button
    private lateinit var screen: GoodPriceCheckSurfaceView
    private lateinit var presenter: GoodPriceCheckScreenPresenter
    private lateinit var memoryStatus: android.widget.TextView
    private lateinit var clearMemoryButton: Button
    private lateinit var memoryStore: CompareHerePrivatePriceMemoryStore
    private lateinit var barcodeButton: Button
    private lateinit var barcodeStatus: TextView

    private var priceSelection = CompareHerePriceSelection.CURRENT
    private var privateMemory = CompareHerePrivatePriceMemoryState.empty()
    private var privateMemoryLoadIssue: CompareHerePrivatePriceMemoryStoreIssue? = null
    private var restoring = false
    private val barcodeLookupExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var barcodeLookupRequestId = 0L
    private var barcodeLookupClosed = false
    private var barcodeDialog: AlertDialog? = null
    private val barcodeCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val gtin = result.data?.getStringExtra(BarcodeCaptureActivity.EXTRA_GTIN)
            if (gtin.isNullOrBlank()) {
                barcodeStatus.text = getString(R.string.good_price_barcode_unavailable)
                barcodeStatus.visibility = View.VISIBLE
                return@registerForActivityResult
            }
            beginBarcodeIdentityLookup(gtin)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_good_price)

        productInput = findViewById(R.id.goodPriceInput)
        priceSelectionGroup = findViewById(R.id.goodPriceSelectionGroup)
        checkButton = findViewById(R.id.checkGoodPriceButton)
        screen = findViewById(R.id.goodPriceScreen)
        presenter = GoodPriceCheckScreenPresenter(screen)
        memoryStatus = findViewById(R.id.goodPriceMemoryStatus)
        clearMemoryButton = findViewById(R.id.clearGoodPriceMemoryButton)
        barcodeButton = findViewById(R.id.goodPriceBarcodeButton)
        barcodeStatus = findViewById(R.id.goodPriceBarcodeStatus)
        barcodeStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        memoryStore = CompareHerePrivatePriceMemoryAndroidStore(this)

        privateMemory = loadPrivateMemory()

        barcodeButton.setOnClickListener { beginBarcodeCapture() }

        productInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!restoring) {
                        // A later manual edit means the previous barcode suggestion no longer
                        // describes the draft. Keep the status honest and invalidate any stale
                        // identity lookup callback.
                        clearBarcodeStatus()
                        renderIdle()
                    }
                }
            }
        )

        priceSelectionGroup.setOnCheckedChangeListener { _, checkedId ->
            if (restoring) return@setOnCheckedChangeListener
            priceSelection =
                when (checkedId) {
                    R.id.goodPriceSelectionMember -> CompareHerePriceSelection.MEMBER
                    else -> CompareHerePriceSelection.CURRENT
                }
            renderIdle()
        }

        checkButton.setOnClickListener { runCheck() }
        findViewById<Button>(R.id.clearGoodPriceButton).setOnClickListener {
            restoring = true
            productInput.setText("")
            priceSelection = CompareHerePriceSelection.CURRENT
            priceSelectionGroup.check(R.id.goodPriceSelectionCurrent)
            restoring = false
            clearBarcodeStatus()
            renderIdle()
        }
        clearMemoryButton.setOnClickListener {
            val result = memoryStore.clear()
            if (result.accepted) {
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
                privateMemoryLoadIssue = null
                hideMemoryStatus()
            } else {
                memoryStatus.text = getString(R.string.compare_memory_clear_error)
                memoryStatus.setTextColor(Color.parseColor("#92400E"))
                memoryStatus.visibility = View.VISIBLE
                clearMemoryButton.visibility = View.VISIBLE
            }
        }

        priceSelection =
            savedInstanceState?.getString(STATE_PRICE_SELECTION)?.let {
                CompareHerePriceSelectionPersistence.decode(it)
            } ?: CompareHerePriceSelection.CURRENT
        restoring = true
        priceSelectionGroup.check(
            if (priceSelection == CompareHerePriceSelection.MEMBER) {
                R.id.goodPriceSelectionMember
            } else {
                R.id.goodPriceSelectionCurrent
            }
        )
        restoring = false
        renderIdle()
    }

    override fun onResume() {
        super.onResume()
        privateMemory = loadPrivateMemory()
        if (privateMemoryLoadIssue != null) showMemoryUnavailable()
    }

    override fun onDestroy() {
        barcodeLookupClosed = true
        barcodeLookupRequestId += 1L
        barcodeDialog?.dismiss()
        barcodeDialog = null
        barcodeLookupExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            STATE_PRICE_SELECTION,
            CompareHerePriceSelectionPersistence.encode(priceSelection)
        )
        super.onSaveInstanceState(outState)
    }

    private fun runCheck() {
        val evaluation =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = productInput.text?.toString().orEmpty(),
                observedAtEpochMillis = System.currentTimeMillis(),
                priceSelection = priceSelection,
                privateMemory = privateMemory
            )
        presenter.render(evaluation.state)

        val capture = evaluation.privateMemoryCapture
        if (capture == null) {
            if (privateMemoryLoadIssue == null) hideMemoryStatus() else showMemoryUnavailable()
            return
        }

        val saved = memoryStore.append(capture)
        if (saved.accepted) {
            privateMemory = requireNotNull(saved.state)
            privateMemoryLoadIssue = null
            memoryStatus.text =
                getString(
                    R.string.good_price_memory_saved,
                    capture.entries.single().displayName
                )
            memoryStatus.setTextColor(Color.parseColor("#047857"))
            memoryStatus.visibility = View.VISIBLE
            clearMemoryButton.visibility = View.VISIBLE
        } else {
            if (privateMemoryLoadIssue != null ||
                saved.issue == CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID
            ) {
                privateMemoryLoadIssue = saved.issue ?: privateMemoryLoadIssue
                showMemoryUnavailable()
            } else {
                memoryStatus.text = getString(R.string.good_price_memory_error)
                memoryStatus.setTextColor(Color.parseColor("#92400E"))
                memoryStatus.visibility = View.VISIBLE
                clearMemoryButton.visibility = View.VISIBLE
            }
        }
    }

    private fun renderIdle() {
        if (privateMemoryLoadIssue == null) hideMemoryStatus() else showMemoryUnavailable()
        checkButton.isEnabled = productInput.text?.toString()?.isNotBlank() == true
        presenter.render(
            GoodPriceCheckRouteState(
                status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
                title = "Ready to check a price",
                guidance = "Enter one product, then tap Check this price."
            )
        )
    }

    private fun beginBarcodeCapture() {
        if (barcodeLookupClosed) return
        try {
            barcodeCaptureLauncher.launch(Intent(this, BarcodeCaptureActivity::class.java))
        } catch (_: Exception) {
            barcodeStatus.text = getString(R.string.good_price_barcode_unavailable)
            barcodeStatus.visibility = View.VISIBLE
        }
    }

    private fun beginBarcodeIdentityLookup(gtin: String) {
        val trimmed = gtin.trim()
        if (barcodeLookupClosed || trimmed.isBlank()) return

        val requestId = barcodeLookupRequestId + 1L
        barcodeLookupRequestId = requestId
        barcodeDialog?.dismiss()
        barcodeDialog = null
        barcodeButton.isEnabled = false
        barcodeStatus.text = getString(R.string.good_price_barcode_processing)
        barcodeStatus.visibility = View.VISIBLE

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
                        isFinishing ||
                        isDestroyed
                    ) {
                        return@runOnUiThread
                    }
                    barcodeButton.isEnabled = true
                    if (presentation == null) {
                        barcodeStatus.text = getString(R.string.good_price_barcode_unavailable)
                        return@runOnUiThread
                    }
                    showBarcodeIdentityChoices(presentation)
                }
            }
        } catch (_: Throwable) {
            barcodeButton.isEnabled = true
            barcodeStatus.text = getString(R.string.good_price_barcode_unavailable)
        }
    }

    private fun showBarcodeIdentityChoices(
        presentation: GoodPriceBarcodeIdentityPresentation
    ) {
        if (presentation.options.isEmpty()) {
            barcodeStatus.text =
                getString(
                    R.string.good_price_barcode_no_match,
                    presentation.gtin
                )
            barcodeStatus.visibility = View.VISIBLE
            return
        }

        var selectedIndex = if (presentation.options.size == 1) 0 else -1
        lateinit var dialog: AlertDialog
        val builder =
            AlertDialog.Builder(this)
                .setTitle(R.string.good_price_barcode_match_title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.good_price_barcode_use_name, null)

        if (presentation.options.size == 1) {
            builder.setMessage(
                getString(
                    R.string.good_price_barcode_match_message,
                    presentation.options.single().label
                )
            )
        } else {
            builder
                .setMessage(
                    getString(
                        R.string.good_price_barcode_multiple_message,
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
            if (barcodeDialog === dialog) barcodeDialog = null
        }
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.contentDescription = getString(R.string.good_price_barcode_use_name_description)
            button.isEnabled = selectedIndex >= 0
            button.setOnClickListener {
                val option = presentation.options.getOrNull(selectedIndex) ?: return@setOnClickListener
                restoring = true
                productInput.setText(option.displayName)
                productInput.setSelection(productInput.text?.length ?: 0)
                restoring = false
                barcodeStatus.text =
                    getString(
                        R.string.good_price_barcode_used,
                        option.label,
                        presentation.gtin
                    )
                barcodeStatus.visibility = View.VISIBLE
                renderIdle()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun clearBarcodeStatus() {
        barcodeLookupRequestId += 1L
        barcodeDialog?.dismiss()
        barcodeDialog = null
        barcodeStatus.text = getString(R.string.good_price_barcode_ready)
        barcodeStatus.visibility = View.VISIBLE
        barcodeButton.isEnabled = !barcodeLookupClosed
    }

    private fun loadPrivateMemory(): CompareHerePrivatePriceMemoryState {
        val loaded = memoryStore.load()
        privateMemoryLoadIssue = loaded.issue
        return loaded.state ?: CompareHerePrivatePriceMemoryState.empty()
    }

    private fun showMemoryUnavailable() {
        memoryStatus.text = getString(R.string.good_price_memory_unavailable)
        memoryStatus.setTextColor(Color.parseColor("#92400E"))
        memoryStatus.visibility = View.VISIBLE
        clearMemoryButton.visibility = View.VISIBLE
    }

    private fun hideMemoryStatus() {
        memoryStatus.text = ""
        memoryStatus.visibility = View.GONE
        clearMemoryButton.visibility = View.GONE
    }

    companion object {
        private const val STATE_PRICE_SELECTION = "good_price.price_selection"
        private const val OFFLINE_CATALOG_MAX_AGE_MILLIS = 8L * 24L * 60L * 60L * 1_000L
    }
}

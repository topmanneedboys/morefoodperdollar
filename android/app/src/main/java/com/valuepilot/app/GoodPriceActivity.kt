package com.valuepilot.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
    private lateinit var shareGoodPriceButton: Button
    private lateinit var shareGoodPriceStatus: TextView

    private var priceSelection = CompareHerePriceSelection.CURRENT
    private var privateMemory = CompareHerePrivatePriceMemoryState.empty()
    private var privateMemoryLoadIssue: CompareHerePrivatePriceMemoryStoreIssue? = null
    private var restoring = false
    private val barcodeLookupExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var barcodeLookupRequestId = 0L
    private var barcodeLookupClosed = false
    private var barcodeCaptureInFlight = false
    private var barcodeDialog: AlertDialog? = null
    private var shareCard: GoodPriceShareCard? = null
    private var lastCheckObservedAtEpochMillis: Long? = null
    private val barcodeCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (barcodeLookupClosed || !barcodeCaptureInFlight) {
                return@registerForActivityResult
            }
            barcodeCaptureInFlight = false
            barcodeButton.isEnabled = true
            if (result.resultCode != RESULT_OK) {
                barcodeStatus.text = getString(R.string.good_price_barcode_cancelled)
                barcodeStatus.visibility = View.VISIBLE
                return@registerForActivityResult
            }
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
        // Memory reads, saves, clears, and recovery failures are user-visible outcomes. Keep
        // them in a polite live region so assistive technology receives the same state changes
        // that sighted shoppers see, without changing any memory authority or result math.
        memoryStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        clearMemoryButton = findViewById(R.id.clearGoodPriceMemoryButton)
        barcodeButton = findViewById(R.id.goodPriceBarcodeButton)
        barcodeStatus = findViewById(R.id.goodPriceBarcodeStatus)
        barcodeStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        shareGoodPriceButton = findViewById(R.id.goodPriceShareButton)
        shareGoodPriceStatus = findViewById(R.id.goodPriceShareStatus)
        shareGoodPriceStatus.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        memoryStore = CompareHerePrivatePriceMemoryAndroidStore(this)

        privateMemory = loadPrivateMemory()

        // Keep the multiline editor, but make the keyboard's Done action follow the same
        // explicit button gate. The existing exact coordinator still decides whether a result
        // can be produced; this only removes a trip to a button below the editor.
        productInput.imeOptions = EditorInfo.IME_ACTION_DONE
        productInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && checkButton.isEnabled) {
                runCheck()
                true
            } else {
                false
            }
        }

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
        shareGoodPriceButton.setOnClickListener { shareGoodPriceResult() }
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
                // A displayed answer may include the history that was just deleted. Clear the
                // projected result and share card so removed private context cannot remain visible
                // or be shared; the shopper can run a fresh explicit check if they still want one.
                renderIdle()
            } else {
                memoryStatus.text = getString(R.string.compare_memory_clear_error)
                memoryStatus.setTextColor(Color.parseColor("#92400E"))
                memoryStatus.visibility = View.VISIBLE
                clearMemoryButton.visibility = View.VISIBLE
            }
        }

        var prefilledFromIntent = false
        if (savedInstanceState?.containsKey(STATE_PRODUCT_INPUT) == true) {
            restoring = true
            val restoredInput = savedInstanceState.getString(STATE_PRODUCT_INPUT).orEmpty()
            productInput.setText(restoredInput)
            productInput.setSelection(productInput.text?.length ?: 0)
            restoring = false
        } else {
            GoodPriceActivityPrefill
                .sanitize(intent.getStringExtra(EXTRA_PRODUCT_NAME))
                ?.let { value ->
                    // This is only an untrusted name prefill. The shopper still supplies the
                    // exact package quantity and observed price before any private memory entry
                    // can be created.
                    restoring = true
                    productInput.setText(value)
                    productInput.setSelection(value.length)
                    restoring = false
                    prefilledFromIntent = true
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

        val restoredCheckObservedAt =
            savedInstanceState
                ?.takeIf { it.containsKey(STATE_CHECK_OBSERVED_AT) }
                ?.getLong(STATE_CHECK_OBSERVED_AT)
                ?.takeIf { it >= 0L }
        if (
            restoredCheckObservedAt != null &&
                privateMemoryLoadIssue == null &&
                productInput.text?.toString()?.isNotBlank() == true
        ) {
            // Re-evaluate the exact immutable check without appending a second memory entry.
            // The coordinator's observation fingerprint excludes the same observation from its
            // own history, so the restored answer remains deterministic and honest.
            runCheck(
                observedAtEpochMillis = restoredCheckObservedAt,
                persist = false
            )
        } else {
            renderIdle()
        }

        // Home/Saved handoffs provide only an untrusted product-name draft. Focus it and reopen
        // the keyboard after the initial idle projection so the shopper can continue with the
        // required exact package quantity and observed price without another tap.
        if (prefilledFromIntent) {
            focusProductInput()
        }
    }

    override fun onResume() {
        super.onResume()
        val previousMemory = privateMemory
        val previousIssue = privateMemoryLoadIssue
        privateMemory = loadPrivateMemory()
        if (previousMemory != privateMemory || previousIssue != privateMemoryLoadIssue) {
            // Home or Compare Here may have changed device-only history while this Activity was
            // paused. Any displayed answer may contain the old personal context, so fail closed
            // and keep the shopper's typed draft ready for an explicit fresh check.
            renderIdle()
        } else if (privateMemoryLoadIssue != null) {
            showMemoryUnavailable()
        }
    }

    override fun onDestroy() {
        barcodeLookupClosed = true
        barcodeCaptureInFlight = false
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
        outState.putString(
            STATE_PRODUCT_INPUT,
            productInput.text?.toString().orEmpty()
        )
        lastCheckObservedAtEpochMillis?.let { observedAt ->
            outState.putLong(STATE_CHECK_OBSERVED_AT, observedAt)
        } ?: outState.remove(STATE_CHECK_OBSERVED_AT)
        super.onSaveInstanceState(outState)
    }

    private fun runCheck(
        observedAtEpochMillis: Long = System.currentTimeMillis(),
        persist: Boolean = true
    ) {
        // The answer and any private-memory outcome are the next task. Dismiss the IME first so
        // the projected result is visible immediately after an aisle-side check.
        hideKeyboard()
        val evaluation =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = productInput.text?.toString().orEmpty(),
                observedAtEpochMillis = observedAtEpochMillis,
                priceSelection = priceSelection,
                privateMemory = privateMemory
            )
        lastCheckObservedAtEpochMillis =
            observedAtEpochMillis.takeIf {
                evaluation.state.status == GoodPriceCheckRouteStatus.EVALUATED
            }
        presenter.render(evaluation.state)
        renderShareCard(evaluation.state.shareCard)

        val capture = evaluation.privateMemoryCapture
        if (!persist) {
            if (privateMemoryLoadIssue == null) hideMemoryStatus() else showMemoryUnavailable()
            return
        }
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

    private fun hideKeyboard() {
        val manager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        val focusedView = currentFocus ?: productInput
        manager?.hideSoftInputFromWindow(focusedView.windowToken, 0)
        focusedView.clearFocus()
    }

    private fun renderIdle() {
        lastCheckObservedAtEpochMillis = null
        if (privateMemoryLoadIssue == null) hideMemoryStatus() else showMemoryUnavailable()
        renderShareCard(null)
        checkButton.isEnabled = productInput.text?.toString()?.isNotBlank() == true
        presenter.render(
            GoodPriceCheckRouteState(
                status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
                title = "Ready to check a price",
                guidance = "Enter one product, then tap Check this price."
            )
        )
    }

    /** Shows sharing only for the immutable exact result returned by the Good Price route. */
    private fun renderShareCard(card: GoodPriceShareCard?) {
        shareCard = card
        shareGoodPriceButton.visibility = if (card == null) View.GONE else View.VISIBLE
        shareGoodPriceButton.isEnabled = card != null
        shareGoodPriceButton.contentDescription =
            if (card == null) {
                ""
            } else {
                getString(R.string.good_price_share_result_description)
            }
        if (card == null) {
            shareGoodPriceStatus.text = ""
            shareGoodPriceStatus.visibility = View.GONE
        } else {
            shareGoodPriceStatus.text = ""
            shareGoodPriceStatus.visibility = View.GONE
        }
    }

    private fun shareGoodPriceResult() {
        val card = shareCard ?: return
        val preview =
            card.preview + "\n\n" + getString(R.string.good_price_share_preview_body)
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.good_price_share_preview_title)
                .setMessage(preview)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.good_price_share_send, null)
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
                            getString(R.string.good_price_share_result)
                        )
                    )
                    dialog.dismiss()
                } catch (_: ActivityNotFoundException) {
                    shareGoodPriceStatus.text =
                        getString(R.string.good_price_share_unavailable)
                    shareGoodPriceStatus.visibility = View.VISIBLE
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun beginBarcodeCapture() {
        if (barcodeLookupClosed || barcodeCaptureInFlight) return
        barcodeCaptureInFlight = true
        barcodeButton.isEnabled = false
        barcodeStatus.text = getString(R.string.good_price_barcode_processing)
        barcodeStatus.visibility = View.VISIBLE
        try {
            barcodeCaptureLauncher.launch(Intent(this, BarcodeCaptureActivity::class.java))
        } catch (_: Exception) {
            barcodeCaptureInFlight = false
            barcodeButton.isEnabled = true
            barcodeStatus.text = getString(R.string.good_price_barcode_unavailable)
            barcodeStatus.visibility = View.VISIBLE
        }
    }

    private fun beginBarcodeIdentityLookup(gtin: String) {
        val trimmed = gtin.trim()
        if (barcodeLookupClosed || trimmed.isBlank()) {
            barcodeCaptureInFlight = false
            barcodeButton.isEnabled = !barcodeLookupClosed
            return
        }

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
        val hasExistingDraft = productInput.text?.toString()?.isNotBlank() == true
        lateinit var dialog: AlertDialog
        val builder =
            AlertDialog.Builder(this)
                .setTitle(R.string.good_price_barcode_match_title)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                    if (hasExistingDraft) {
                        R.string.good_price_barcode_replace_draft
                    } else {
                        R.string.good_price_barcode_use_name
                    },
                    null
                )

        if (presentation.options.size == 1) {
            builder.setMessage(
                if (hasExistingDraft) {
                    getString(
                        R.string.good_price_barcode_replace_message,
                        presentation.options.single().label
                    )
                } else {
                    getString(
                        R.string.good_price_barcode_match_message,
                        presentation.options.single().label
                    )
                }
            )
        } else {
            builder
                .setMessage(
                    buildString {
                        append(
                            getString(
                                R.string.good_price_barcode_multiple_message,
                                presentation.gtin
                            )
                        )
                        if (hasExistingDraft) {
                            append("\n\n")
                            append(getString(R.string.good_price_barcode_replace_warning))
                        }
                    }
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
            button.contentDescription =
                getString(
                    if (hasExistingDraft) {
                        R.string.good_price_barcode_replace_description
                    } else {
                        R.string.good_price_barcode_use_name_description
                    }
                )
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
                focusProductInput()
            }
        }
        dialog.show()
    }

    /**
     * Keep the barcode identity handoff honest while removing an unnecessary navigation step. The
     * field is focused only; exact package quantity and observed price remain manual inputs.
     */
    private fun focusProductInput() {
        productInput.post {
            if (isFinishing || isDestroyed) return@post
            productInput.requestFocus()
            productInput.setSelection(productInput.text?.length ?: 0)
            // Barcode identity is only an editable name suggestion. Re-open the keyboard so the
            // shopper can immediately enter the required package quantity and observed price.
            val manager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            manager?.showSoftInput(productInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearBarcodeStatus() {
        barcodeLookupRequestId += 1L
        barcodeDialog?.dismiss()
        barcodeDialog = null
        barcodeStatus.text = getString(R.string.good_price_barcode_ready)
        barcodeStatus.visibility = View.VISIBLE
        barcodeButton.isEnabled = !barcodeLookupClosed && !barcodeCaptureInFlight
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
        const val EXTRA_PRODUCT_NAME = "com.valuepilot.app.extra.PRODUCT_NAME"
        private const val STATE_PRICE_SELECTION = "good_price.price_selection"
        private const val STATE_PRODUCT_INPUT = "good_price.product_input"
        private const val STATE_CHECK_OBSERVED_AT = "good_price.check_observed_at"
        private const val OFFLINE_CATALOG_MAX_AGE_MILLIS = 8L * 24L * 60L * 60L * 1_000L
    }
}

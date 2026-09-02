package com.valuepilot.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.valuepilot.core.CompareHerePriceSelection

class ComparisonActivity : AppCompatActivity() {
    private lateinit var productInputsContainer: LinearLayout
    private lateinit var addProductButton: Button
    private lateinit var compareButton: Button
    private lateinit var likeForLikeConfirmation: CheckBox
    private lateinit var priceSelectionGroup: RadioGroup
    private lateinit var comparisonScreen: CompareHereManualScreenView
    private lateinit var comparisonPresenter: CompareHereManualScreenPresenter
    private lateinit var scannerStatus: TextView

    private val productInputs = mutableListOf<EditText>()
    private val productInputRows = mutableListOf<ProductInputRow>()

    private var activityState = CompareHereManualActivitySessionState.initial()
    private var restoringDraft = false

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
        scannerStatus = findViewById(R.id.scannerStatus)

        val draft = restoreDraft(savedInstanceState)
        activityState =
            CompareHereManualActivitySessionState.restore(
                comparisonWasRun = draft.compared,
                observedAtEpochMillis = draft.observedAtEpochMillis,
                likeForLikeConfirmed = draft.likeForLikeConfirmed,
                priceSelection = draft.priceSelection
            )

        renderProductInputs(draft.blocks)
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
    }

    override fun onResume() {
        super.onResume()
        renderScannerStatus()
    }

    override fun onPause() {
        saveDraftToPreferences()
        super.onPause()
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

    private fun renderProductInputs(blocks: List<String>) {
        restoringDraft = true

        productInputs.clear()
        productInputRows.clear()
        productInputsContainer.removeAllViews()

        val initialBlocks =
            blocks
                .take(CompareHereManualInputAdapter.MAX_OBSERVATIONS)
                .toMutableList()

        while (initialBlocks.size < 2) {
            initialBlocks += ""
        }

        initialBlocks.forEach(::addProductInput)

        restoringDraft = false
        updateAddProductButton()
    }

    private fun addProductInput(initialText: String) {
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

                    onProductsChanged()
                }
            }
        )

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
        val routeState =
            CompareHereManualRouteCoordinator.compareBlocks(
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

        comparisonPresenter.render(routeState)

        if (persist) {
            saveDraftToPreferences()
        }
    }

    private fun onProductsChanged() {
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

    private fun clearComparison() {
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
}

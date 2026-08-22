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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val comparisonController = StandaloneComparisonController()
    private var comparisonState = comparisonController.initialState()

    private lateinit var productInputsContainer: LinearLayout
    private lateinit var addProductButton: Button
    private lateinit var comparisonStatus: TextView
    private lateinit var resultsHeading: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var scannerStatus: TextView

    private val productInputs = mutableListOf<EditText>()

    private var comparisonWasRun = false
    private var lastComparedAtEpochMillis = 0L
    private var restoringDraft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        productInputsContainer = findViewById(R.id.productInputsContainer)
        addProductButton = findViewById(R.id.addProductButton)
        comparisonStatus = findViewById(R.id.comparisonStatus)
        resultsHeading = findViewById(R.id.resultsHeading)
        resultsContainer = findViewById(R.id.resultsContainer)
        scannerStatus = findViewById(R.id.scannerStatus)

        val draft = restoreDraft(savedInstanceState)

        comparisonWasRun = draft.compared
        lastComparedAtEpochMillis = draft.observedAtEpochMillis

        renderProductInputs(draft.blocks)

        addProductButton.setOnClickListener {
            if (productInputs.size < ManualProductObservationAdapter.MAX_PRODUCT_BLOCKS) {
                addProductInput("")
                comparisonWasRun = false
                comparisonState = comparisonController.initialState()
                renderComparison(comparisonState)
                updateAddProductButton()
            }
        }

        findViewById<Button>(R.id.compareButton).setOnClickListener {
            val now = System.currentTimeMillis()
            runComparison(
                blocks = currentProductBlocks(),
                observedAtEpochMillis = now,
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

        if (comparisonWasRun) {
            val restoreTime =
                lastComparedAtEpochMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis()

            runComparison(
                blocks = currentProductBlocks(),
                observedAtEpochMillis = restoreTime,
                persist = false
            )
        } else {
            renderComparison(comparisonState)
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
            comparisonWasRun
        )
        outState.putLong(
            STATE_OBSERVED_AT,
            lastComparedAtEpochMillis
        )

        super.onSaveInstanceState(outState)
    }

    private fun renderProductInputs(blocks: List<String>) {
        restoringDraft = true

        productInputs.clear()
        productInputsContainer.removeAllViews()

        val initialBlocks = blocks
            .take(ManualProductObservationAdapter.MAX_PRODUCT_BLOCKS)
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

            layoutParams = LinearLayout.LayoutParams(
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

                    comparisonWasRun = false
                    lastComparedAtEpochMillis = 0L
                    comparisonState =
                        comparisonController.initialState()

                    renderComparison(comparisonState)
                }
            }
        )

        card.addView(label)
        card.addView(input)

        productInputs += input
        productInputsContainer.addView(card)

        updateAddProductButton()
    }

    private fun currentProductBlocks(): List<String> =
        productInputs.map {
            it.text?.toString().orEmpty()
        }

    private fun runComparison(
        blocks: List<String>,
        observedAtEpochMillis: Long,
        persist: Boolean
    ) {
        comparisonState = comparisonController.reduce(
            comparisonState,
            StandaloneComparisonIntent.CompareBlocks(
                productBlocks = blocks,
                observedAtEpochMillis = observedAtEpochMillis
            )
        )

        comparisonWasRun = true
        lastComparedAtEpochMillis =
            observedAtEpochMillis

        renderComparison(comparisonState)

        if (persist) {
            saveDraftToPreferences()
        }
    }

    private fun clearComparison() {
        comparisonWasRun = false
        lastComparedAtEpochMillis = 0L
        comparisonState =
            comparisonController.initialState()

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

        renderComparison(comparisonState)
    }

    private fun updateAddProductButton() {
        if (::addProductButton.isInitialized) {
            addProductButton.isEnabled =
                productInputs.size <
                    ManualProductObservationAdapter.MAX_PRODUCT_BLOCKS
        }
    }

    private fun renderComparison(
        state: StandaloneComparisonState
    ) {
        comparisonStatus.text = state.statusText

        comparisonStatus.setTextColor(
            when (state.status) {
                StandaloneComparisonStatus.READY ->
                    Color.parseColor("#047857")

                StandaloneComparisonStatus.EMPTY ->
                    Color.parseColor("#6B7280")

                else ->
                    Color.parseColor("#B42318")
            }
        )

        resultsContainer.removeAllViews()

        if (state.results.isEmpty()) {
            resultsHeading.visibility = View.GONE
            return
        }

        resultsHeading.visibility = View.VISIBLE

        state.results.forEach { row ->
            resultsContainer.addView(
                createResultView(row)
            )
        }
    }

    private fun createResultView(
        row: StandaloneComparisonRow
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }

            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()

                if (row.best) {
                    setColor(Color.parseColor("#ECFDF5"))
                    setStroke(
                        dp(1),
                        Color.parseColor("#A7F3D0")
                    )
                } else {
                    setColor(Color.parseColor("#F9FAFB"))
                    setStroke(
                        dp(1),
                        Color.parseColor("#E5E7EB")
                    )
                }
            }
        }

        val rank = TextView(this).apply {
            text =
                if (row.best) {
                    getString(
                        R.string.best_value_rank,
                        row.rank
                    )
                } else {
                    getString(
                        R.string.rank_number,
                        row.rank
                    )
                }

            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                13f
            )

            setTextColor(
                if (row.best) {
                    Color.parseColor("#047857")
                } else {
                    Color.parseColor("#6B7280")
                }
            )

            setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
        }

        val name = TextView(this).apply {
            text = row.name
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                18f
            )
            setTextColor(Color.parseColor("#111827"))
            setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            setPadding(
                0,
                dp(4),
                0,
                0
            )
        }

        val details = TextView(this).apply {
            text = listOfNotNull(
                row.quantity?.takeIf {
                    it.isNotBlank()
                },
                row.priceSummary
            ).joinToString("  •  ")

            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                15f
            )
            setTextColor(Color.parseColor("#374151"))
            setPadding(
                0,
                dp(6),
                0,
                0
            )
        }

        val metric = TextView(this).apply {
            text = row.metricLabel
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                16f
            )
            setTextColor(Color.parseColor("#111827"))
            setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            setPadding(
                0,
                dp(7),
                0,
                0
            )
        }

        val exactness = TextView(this).apply {
            text = row.exactnessLabel
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                12f
            )
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(
                0,
                dp(3),
                0,
                0
            )
        }

        card.addView(rank)
        card.addView(name)
        card.addView(details)
        card.addView(metric)
        card.addView(exactness)

        return card
    }

    private fun saveDraftToPreferences() {
        val blocks = currentProductBlocks()

        val prefs = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

        if (
            blocks.all { it.isBlank() } &&
            !comparisonWasRun
        ) {
            prefs.edit()
                .clear()
                .apply()
            return
        }

        val editor = prefs.edit()
            .clear()
            .putInt(
                PREF_COUNT,
                blocks.size
            )
            .putBoolean(
                PREF_COMPARED,
                comparisonWasRun
            )
            .putLong(
                PREF_OBSERVED_AT,
                lastComparedAtEpochMillis
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
                    )
            )
        }

        val prefs = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

        val count = prefs
            .getInt(PREF_COUNT, 0)
            .coerceIn(
                0,
                ManualProductObservationAdapter.MAX_PRODUCT_BLOCKS
            )

        if (count == 0) {
            return Draft(
                blocks = listOf("", ""),
                compared = false,
                observedAtEpochMillis = 0L
            )
        }

        val blocks = (0 until count).map { index ->
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density)
            .toInt()

    private data class Draft(
        val blocks: List<String>,
        val compared: Boolean,
        val observedAtEpochMillis: Long
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

        private const val PREF_BLOCK_PREFIX =
            "product_"

        private const val STATE_BLOCKS =
            "standalone.blocks"

        private const val STATE_COMPARED =
            "standalone.compared"

        private const val STATE_OBSERVED_AT =
            "standalone.observed_at"
    }
}

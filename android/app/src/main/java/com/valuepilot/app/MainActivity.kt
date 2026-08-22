package com.valuepilot.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
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

    private lateinit var manualInput: EditText
    private lateinit var comparisonStatus: TextView
    private lateinit var resultsHeading: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var scannerStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        manualInput = findViewById(R.id.manualInput)
        comparisonStatus = findViewById(R.id.comparisonStatus)
        resultsHeading = findViewById(R.id.resultsHeading)
        resultsContainer = findViewById(R.id.resultsContainer)
        scannerStatus = findViewById(R.id.scannerStatus)

        findViewById<Button>(R.id.compareButton).setOnClickListener {
            comparisonState = comparisonController.reduce(
                comparisonState,
                StandaloneComparisonIntent.Compare(
                    rawInput = manualInput.text.toString(),
                    observedAtEpochMillis = System.currentTimeMillis()
                )
            )
            renderComparison(comparisonState)
        }

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            manualInput.text.clear()
            comparisonState = comparisonController.reduce(
                comparisonState,
                StandaloneComparisonIntent.Clear
            )
            renderComparison(comparisonState)
        }

        findViewById<Button>(R.id.enableButton).setOnClickListener {
            showAccessibilityDisclosure()
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            openAccessibilitySettings()
        }

        renderComparison(comparisonState)
    }

    override fun onResume() {
        super.onResume()
        renderScannerStatus()
    }

    private fun renderComparison(state: StandaloneComparisonState) {
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
            resultsContainer.addView(createResultView(row))
        }
    }

    private fun createResultView(
        row: StandaloneComparisonRow
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))

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
                    setStroke(dp(1), Color.parseColor("#A7F3D0"))
                } else {
                    setColor(Color.parseColor("#F9FAFB"))
                    setStroke(dp(1), Color.parseColor("#E5E7EB"))
                }
            }
        }

        val rank = TextView(this).apply {
            text = if (row.best) {
                getString(R.string.best_value_rank, row.rank)
            } else {
                getString(R.string.rank_number, row.rank)
            }

            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(
                if (row.best) {
                    Color.parseColor("#047857")
                } else {
                    Color.parseColor("#6B7280")
                }
            )
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

        val name = TextView(this).apply {
            text = row.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        }

        val details = TextView(this).apply {
            text = listOfNotNull(
                row.quantity?.takeIf { it.isNotBlank() },
                row.priceSummary
            ).joinToString("  •  ")

            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#374151"))
            setPadding(0, dp(6), 0, 0)
        }

        val metric = TextView(this).apply {
            text = row.metricLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(7), 0, 0)
        }

        val exactness = TextView(this).apply {
            text = row.exactnessLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(3), 0, 0)
        }

        card.addView(rank)
        card.addView(name)
        card.addView(details)
        card.addView(metric)
        card.addView(exactness)

        return card
    }

    private fun renderScannerStatus() {
        val enabled = isServiceEnabled()

        scannerStatus.text = if (enabled) {
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
            .setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                openAccessibilitySettings()
            }
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isServiceEnabled(): Boolean {
        val expected =
            "$packageName/${ValueAccessibilityService::class.java.name}"

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter =
            TextUtils.SimpleStringSplitter(':').apply {
                setString(enabledServices)
            }

        return splitter.any { it.equals(expected, true) }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

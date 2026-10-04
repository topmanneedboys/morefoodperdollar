package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Renderer for bounded private Price Watch state. */
internal class PriceWatchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headlineView = textLine("", 22f, "#111827", bold = true)
    private val guidanceView = textLine("", 14f, "#4B5563", topPadding = 8)
    private val rowsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    private val rowViews = LinkedHashMap<String, WatchRow>()

    var onAction: ((PriceWatchUiAction) -> Unit)? = null
        set(value) {
            field = value
            rowViews.values.forEach { it.updateEnabled() }
        }

    init {
        orientation = VERTICAL
        visibility = GONE
        isSaveEnabled = false
        addView(headlineView)
        addView(guidanceView)
        addView(rowsContainer)
    }

    fun render(state: PriceWatchSurfaceState) {
        headlineView.text = state.headline
        guidanceView.text = state.guidance

        val rows = if (state.mode == PriceWatchSurfaceMode.CONTENT) state.rows else emptyList()
        val desiredKeys = rows.map { it.productIdentityKey }.toSet()
        rowViews.keys
            .filterNot(desiredKeys::contains)
            .toList()
            .forEach { identityKey ->
                rowViews.remove(identityKey)?.let { row ->
                    row.dispose()
                    rowsContainer.removeView(row.root)
                }
            }
        rows.forEachIndexed { index, rowState ->
            val row = rowViews[rowState.productIdentityKey] ?: WatchRow(rowState).also {
                rowViews[rowState.productIdentityKey] = it
                ValuePilotMotion.reveal(it.root)
            }
            row.update(rowState)
            val currentIndex = rowsContainer.indexOfChild(row.root)
            if (currentIndex != index) {
                if (currentIndex >= 0) rowsContainer.removeViewAt(currentIndex)
                rowsContainer.addView(row.root, index.coerceAtMost(rowsContainer.childCount))
            }
        }
    }

    private inner class WatchRow(initial: PriceWatchRow) {
        val root = ValuePilotProductCard(context).apply {
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(ValuePilotDesignTokens.Spacing.SM_DP)
            }
        }
        private val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        private val title = textLine("", 17f, "#111827", bold = true)
        private val packageText = textLine("", 12f, "#4B5563", topPadding = 5)
        private val targetText = textLine("", 13f, "#374151", topPadding = 5)
        private val currentOfferText = textLine("", 12f, "#92400E", topPadding = 5)
        private val historySummary = textLine("", 12f, "#6B7280", topPadding = 8)
        private val addObservationButton = actionButton(
            label = "Add personal price",
            description = "Record a private price observation for this exact product"
        )
        private val moreButton = MaterialButton(context).apply {
            text = "More for this product"
            contentDescription = "Show price history, target and watch actions for this product"
            isAllCaps = false
            minHeight = dp(44)
            gravity = Gravity.CENTER
            setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.PRIMARY_DARK))
            setOnClickListener { setMoreExpanded(!moreExpanded) }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        private val moreActions = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
        }
        private val historyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        private val targetButton = actionButton(
            label = "Set target price",
            description = "Set an optional private target price for this exact product"
        )
        private val toggleButton = actionButton(
            label = "Unwatch",
            description = "Stop watching this exact product; private history is retained",
            destructive = true
        )
        private var current = initial
        private var moreExpanded = false

        init {
            body.addView(title)
            body.addView(packageText)
            body.addView(targetText)
            body.addView(currentOfferText)
            body.addView(historySummary)
            body.addView(addObservationButton)
            body.addView(moreButton)
            moreActions.addView(historyContainer)
            moreActions.addView(targetButton)
            moreActions.addView(toggleButton)
            body.addView(moreActions)
            root.addView(body)
            update(initial)
        }

        fun update(next: PriceWatchRow) {
            current = next
            title.text = next.title
            packageText.text = next.packageText
            targetText.text = next.targetText
            currentOfferText.text = next.currentOfferText
            historySummary.text = historySummary(next)
            addObservationButton.setOnClickListener {
                onAction?.invoke(current.addObservationAction)
            }
            targetButton.setOnClickListener { onAction?.invoke(current.editTargetAction) }
            toggleButton.setOnClickListener { onAction?.invoke(current.toggleAction) }
            historyContainer.removeAllViews()
            addHistory(historyContainer, "Confirmed purchases", next.purchaseHistory)
            addHistory(historyContainer, "Personal price observations", next.personalHistory)
            updateEnabled()
        }

        fun updateEnabled() {
            addObservationButton.isEnabled = onAction != null
            targetButton.isEnabled = onAction != null
            toggleButton.isEnabled = onAction != null
            moreButton.isEnabled = onAction != null
        }

        fun dispose() {
            root.animate().cancel()
            moreActions.animate().cancel()
        }

        private fun setMoreExpanded(expanded: Boolean) {
            moreExpanded = expanded
            moreActions.visibility = if (expanded) VISIBLE else GONE
            moreButton.text = if (expanded) "Hide extra details" else "More for this product"
            ValuePilotMotion.reveal(moreActions, animate = expanded)
        }
    }

    private fun historySummary(row: PriceWatchRow): String {
        val purchaseCount = row.purchaseHistory.size
        val observationCount = row.personalHistory.size
        return when {
            purchaseCount == 0 && observationCount == 0 ->
                "No private purchase or price history recorded yet."
            else ->
                "$purchaseCount confirmed purchase${if (purchaseCount == 1) "" else "s"} · " +
                    "$observationCount personal observation${if (observationCount == 1) "" else "s"}"
        }
    }

    private fun addHistory(
        body: LinearLayout,
        heading: String,
        lines: List<PriceWatchHistoryLine>
    ) {
        body.addView(textLine(heading, 13f, "#374151", bold = true, topPadding = 10))
        if (lines.isEmpty()) {
            body.addView(textLine("None recorded", 12f, "#6B7280", topPadding = 3))
        } else {
            lines.take(12).forEach { line ->
                body.addView(textLine(line.asText(), 12f, "#4B5563", topPadding = 3))
                if (line.sourceText == "Personal price observation") {
                    body.addView(
                        actionButton(
                            label = "Correct observation",
                            description = "Correct this private price observation",
                            action = PriceWatchUiAction.EditObservation(line.recordId),
                            compact = true
                        )
                    )
                    body.addView(
                        actionButton(
                            label = "Delete observation",
                            description = "Delete this private price observation",
                            action = PriceWatchUiAction.DeleteObservation(line.recordId),
                            destructive = true,
                            compact = true
                        )
                    )
                }
            }
            if (lines.size > 12) {
                body.addView(textLine("Older records remain stored privately.", 12f, "#6B7280", topPadding = 3))
            }
        }
    }

    private fun actionButton(
        label: String,
        description: String,
        action: PriceWatchUiAction? = null,
        destructive: Boolean = false,
        compact: Boolean = false
    ): MaterialButton = MaterialButton(context).apply {
        text = label
        contentDescription = description
        isAllCaps = false
        minHeight = dp(if (compact) 40 else 44)
        setTextColor(
            ValuePilotPalette.color(
                if (destructive) ValuePilotDesignTokens.Colors.ERROR
                else ValuePilotDesignTokens.Colors.PRIMARY_DARK
            )
        )
        action?.let { typedAction -> setOnClickListener { onAction?.invoke(typedAction) } }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        }
    }

    private fun textLine(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(ValuePilotPalette.color(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

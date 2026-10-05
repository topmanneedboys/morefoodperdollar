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

    private val headlineView = textLine(
        "",
        ValuePilotDesignTokens.Typography.TITLE_SP - 4f,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        bold = true
    )
    private val guidanceView = textLine(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP + 1f,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = 8
    )
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
        private val title = textLine("", 17f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true)
        private val packageText = textLine("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 5)
        private val targetText = textLine("", 13f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 5)
        private val currentOfferText = textLine("", 12f, ValuePilotDesignTokens.Colors.WARNING, topPadding = 5)
        private val historySummary = textLine("", 12f, ValuePilotDesignTokens.Colors.TEXT_MUTED, topPadding = 8).apply {
            visibility = GONE
        }
        private val addObservationButton = actionButton(
            label = "Add personal price",
            description = "Record a private price observation for this exact product"
        )
        private val moreButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            text = "More"
            contentDescription = "Show price history, personal price and watch actions for this product"
            gravity = Gravity.CENTER
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
            body.addView(moreButton)
            moreActions.addView(addObservationButton)
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
            historySummary.visibility =
                if (next.purchaseHistory.isNotEmpty() || next.personalHistory.isNotEmpty()) {
                    VISIBLE
                } else {
                    GONE
                }
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
            moreButton.text = if (expanded) "Hide" else "More"
            ValuePilotMotion.reveal(moreActions, animate = expanded)
        }
    }

    private fun historySummary(row: PriceWatchRow): String {
        val latest =
            (row.purchaseHistory + row.personalHistory)
                .sortedWith(
                    compareBy<PriceWatchHistoryLine> { it.dateText == "Date unknown" }
                        .thenByDescending { it.dateText }
                        .thenBy { it.recordId }
                )
                .firstOrNull()
                ?: return ""
        return when (latest.sourceText) {
            "Confirmed purchase" -> "Last paid: ${latest.priceText}"
            "Personal price observation" -> "Last observed: ${latest.priceText}"
            else -> latest.priceText
        }
    }

    private fun addHistory(
        body: LinearLayout,
        heading: String,
        lines: List<PriceWatchHistoryLine>
    ) {
        body.addView(
            textLine(
                heading,
                ValuePilotDesignTokens.Typography.LABEL_SP,
                ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                bold = true,
                topPadding = 10
            )
        )
        if (lines.isEmpty()) {
            body.addView(
                textLine(
                    "None recorded",
                    12f,
                    ValuePilotDesignTokens.Colors.TEXT_MUTED,
                    topPadding = 3
                )
            )
        } else {
            lines.take(12).forEach { line ->
                body.addView(
                    textLine(
                        line.asText(),
                        12f,
                        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                        topPadding = 3
                    )
                )
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
                body.addView(
                    textLine(
                        "Older records remain stored privately.",
                        12f,
                        ValuePilotDesignTokens.Colors.TEXT_MUTED,
                        topPadding = 3
                    )
                )
            }
        }
    }

    private fun actionButton(
        label: String,
        description: String,
        action: PriceWatchUiAction? = null,
        destructive: Boolean = false,
        compact: Boolean = false
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(
        context,
        compact = compact,
        destructive = destructive
    ).apply {
        text = label
        contentDescription = description
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

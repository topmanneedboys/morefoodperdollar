package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Replaceable renderer for private confirmed-purchase history. */
internal class ConfirmedPurchaseHistorySurfaceView @JvmOverloads constructor(
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
    private val noticeView = textLine(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.WARNING,
        topPadding = 10
    )
    private val rowsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    private val rowViews = LinkedHashMap<String, PurchaseRow>()

    var onAction: ((ConfirmedPurchaseHistoryUiAction) -> Unit)? = null
        set(value) {
            field = value
            rowViews.values.forEach { it.updateEnabled() }
        }
    var onPriceWatchAction: ((PriceWatchUiAction.ToggleWatch) -> Unit)? = null
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
        addView(noticeView)
        addView(rowsContainer)
    }

    fun render(state: ConfirmedPurchaseHistorySurfaceState) {
        headlineView.text = state.headline
        guidanceView.text = state.guidance
        noticeView.text = state.notice.orEmpty()
        noticeView.visibility = if (state.notice == null) GONE else VISIBLE

        val rows =
            if (state.mode == ConfirmedPurchaseHistorySurfaceMode.CONTENT) state.rows else emptyList()
        val desiredIds = rows.map { it.purchaseId }.toSet()
        rowViews.keys
            .filterNot(desiredIds::contains)
            .toList()
            .forEach { purchaseId ->
                rowViews.remove(purchaseId)?.let { row ->
                    row.dispose()
                    rowsContainer.removeView(row.root)
                }
            }
        rows.forEachIndexed { index, rowState ->
            val row = rowViews[rowState.purchaseId] ?: PurchaseRow(rowState).also {
                rowViews[rowState.purchaseId] = it
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

    private inner class PurchaseRow(initial: ConfirmedPurchaseHistoryRow) {
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
        private val details = textLine("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 6)
        private val repeatButton = actionButton(
            label = "Buy again",
            description = "Add this exact confirmed purchase to your private Home list for a new plan"
        )
        private val moreButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            text = "More"
            contentDescription = "Show correction, delete and price-watch actions for this purchase"
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
        private val watchButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(5)
            }
        }
        private val editButton = actionButton(
            label = "Correct",
            description = "Correct this confirmed purchase"
        )
        private val deleteButton = actionButton(
            label = "Delete",
            description = "Delete this private confirmed purchase",
            destructive = true
        )
        private var current = initial
        private var moreExpanded = false

        init {
            body.addView(title)
            body.addView(details)
            body.addView(repeatButton)
            body.addView(moreButton)
            moreActions.addView(watchButton)
            moreActions.addView(editButton)
            moreActions.addView(deleteButton)
            body.addView(moreActions)
            root.addView(body)
            update(initial)
        }

        fun update(next: ConfirmedPurchaseHistoryRow) {
            current = next
            title.text = next.title
            details.text = next.details
            repeatButton.setOnClickListener { onAction?.invoke(current.repeatAction) }
            editButton.setOnClickListener { onAction?.invoke(current.editAction) }
            deleteButton.setOnClickListener { onAction?.invoke(current.deleteAction) }
            val watch = next.priceWatchAction
            watchButton.visibility = if (watch == null) GONE else VISIBLE
            watch?.let { action ->
                watchButton.text = if (action.watched) "Unwatch price" else "Watch price"
                watchButton.contentDescription =
                    if (action.watched) {
                        "Stop watching ${action.displayName}; private history is retained"
                    } else {
                        "Watch exact product ${action.displayName} privately"
                    }
                watchButton.setOnClickListener { onPriceWatchAction?.invoke(action) }
            }
            updateEnabled()
        }

        fun updateEnabled() {
            repeatButton.isEnabled = onAction != null
            editButton.isEnabled = onAction != null
            deleteButton.isEnabled = onAction != null
            watchButton.isEnabled = onPriceWatchAction != null
            moreButton.isEnabled = onAction != null || onPriceWatchAction != null
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

    private fun actionButton(
        label: String,
        description: String,
        destructive: Boolean = false
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(
        context,
        compact = false,
        destructive = destructive
    ).apply {
        text = label
        contentDescription = description
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

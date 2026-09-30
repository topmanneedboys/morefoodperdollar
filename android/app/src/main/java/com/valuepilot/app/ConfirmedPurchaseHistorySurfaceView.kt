package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Replaceable renderer for private confirmed-purchase history. */
internal class ConfirmedPurchaseHistorySurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onAction: ((ConfirmedPurchaseHistoryUiAction) -> Unit)? = null
    var onPriceWatchAction: ((PriceWatchUiAction.ToggleWatch) -> Unit)? = null

    init {
        orientation = VERTICAL
        visibility = GONE
        isSaveEnabled = false
    }

    fun render(state: ConfirmedPurchaseHistorySurfaceState) {
        removeAllViews()
        addView(textLine(state.headline, 22f, "#111827", bold = true))
        addView(textLine(state.guidance, 14f, "#4B5563", topPadding = 8))
        state.notice?.let { addView(textLine(it, 13f, "#92400E", topPadding = 10)) }
        when (state.mode) {
            ConfirmedPurchaseHistorySurfaceMode.EMPTY,
            ConfirmedPurchaseHistorySurfaceMode.UNAVAILABLE -> Unit

            ConfirmedPurchaseHistorySurfaceMode.CONTENT ->
                state.rows.forEach { row -> addView(rowCard(row)) }
        }
    }

    private fun rowCard(row: ConfirmedPurchaseHistoryRow): View {
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(13))
        }
        body.addView(textLine(row.title, 17f, "#111827", bold = true))
        body.addView(textLine(row.details, 12f, "#4B5563", topPadding = 6))
        row.priceWatchAction?.let { body.addView(priceWatchButton(it)) }
        body.addView(
            actionButton(
                label = "Repeat purchase",
                description = "Add ${row.title} to your private Home list for a new plan",
                action = row.repeatAction
            )
        )
        body.addView(
            actionButton(
                label = "Correct",
                description = "Correct confirmed purchase ${row.title}",
                action = row.editAction
            )
        )
        body.addView(
            actionButton(
                label = "Delete",
                description = "Delete confirmed purchase ${row.title}",
                action = row.deleteAction,
                destructive = true
            )
        )
        return MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ValuePilotPalette.color("#FFFFFF"))
            strokeColor = ValuePilotPalette.color("#E5E7EB")
            strokeWidth = dp(1)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            addView(body)
        }
    }

    private fun priceWatchButton(action: PriceWatchUiAction.ToggleWatch): MaterialButton =
        MaterialButton(context).apply {
            text = if (action.watched) "Unwatch" else "Watch price"
            contentDescription =
                if (action.watched) {
                    "Stop watching ${action.displayName}; private history is retained"
                } else {
                    "Watch exact product ${action.displayName} privately"
                }
            isAllCaps = false
            minHeight = dp(44)
            setTextColor(ValuePilotPalette.color("#1D4ED8"))
            setOnClickListener { onPriceWatchAction?.invoke(action) }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        }

    private fun actionButton(
        label: String,
        description: String,
        action: ConfirmedPurchaseHistoryUiAction,
        destructive: Boolean = false
    ): MaterialButton = MaterialButton(context).apply {
        text = label
        contentDescription = description
        isAllCaps = false
        minHeight = dp(44)
        setTextColor(ValuePilotPalette.color(if (destructive) "#A63D32" else "#8F3620"))
        setOnClickListener { onAction?.invoke(action) }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
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

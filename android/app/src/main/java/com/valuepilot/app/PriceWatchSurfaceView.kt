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

/** Minimal renderer for bounded private Price Watch state. */
internal class PriceWatchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onAction: ((PriceWatchUiAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        visibility = GONE
        isSaveEnabled = false
    }

    fun render(state: PriceWatchSurfaceState) {
        removeAllViews()
        addView(textLine(state.headline, 22f, "#111827", bold = true))
        addView(textLine(state.guidance, 14f, "#4B5563", topPadding = 8))
        when (state.mode) {
            PriceWatchSurfaceMode.EMPTY,
            PriceWatchSurfaceMode.UNAVAILABLE -> Unit

            PriceWatchSurfaceMode.CONTENT -> state.rows.forEach { addView(rowCard(it)) }
        }
    }

    private fun rowCard(row: PriceWatchRow): View {
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(13))
        }
        body.addView(textLine(row.title, 17f, "#111827", bold = true))
        body.addView(textLine(row.packageText, 12f, "#4B5563", topPadding = 5))
        body.addView(textLine(row.targetText, 13f, "#374151", topPadding = 5))
        body.addView(textLine(row.currentOfferText, 12f, "#92400E", topPadding = 5))
        addHistory(body, "Confirmed purchases", row.purchaseHistory)
        addHistory(body, "Personal price observations", row.personalHistory)
        body.addView(
            actionButton(
                label = "Add personal price",
                description = "Record a private price observation for ${row.title}",
                action = row.addObservationAction
            )
        )
        body.addView(
            actionButton(
                label = "Set target price",
                description = "Set an optional private target price for ${row.title}",
                action = row.editTargetAction
            )
        )
        body.addView(
            actionButton(
                label = "Unwatch",
                description = "Stop watching ${row.title}; private history is retained",
                action = row.toggleAction,
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
                            description = "Correct private price observation",
                            action = PriceWatchUiAction.EditObservation(line.recordId),
                            destructive = false,
                            compact = true
                        )
                    )
                    body.addView(
                        actionButton(
                            label = "Delete observation",
                            description = "Delete private price observation",
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
        action: PriceWatchUiAction,
        destructive: Boolean = false,
        compact: Boolean = false
    ): MaterialButton = MaterialButton(context).apply {
        text = label
        contentDescription = description
        isAllCaps = false
        minHeight = dp(if (compact) 40 else 44)
        setTextColor(ValuePilotPalette.color(if (destructive) "#A63D32" else "#8F3620"))
        setOnClickListener { onAction?.invoke(action) }
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

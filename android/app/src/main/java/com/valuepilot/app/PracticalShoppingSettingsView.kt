package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Renderer-only Settings/About surface. */
internal class PracticalShoppingSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val content = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private val actionControls = mutableListOf<View>()

    var onAction: ((PracticalShoppingSettingsAction) -> Unit)? = null
        set(value) {
            field = value
            actionControls.forEach { it.isEnabled = value != null }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
        addView(content)
    }

    fun render(state: PracticalShoppingSettingsState) {
        content.removeAllViews()
        actionControls.clear()
        state.detail?.let { detail ->
            content.addView(
                textLine(
                    value = detail,
                    sizeSp = 15f,
                    color = ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                    topPadding = 2,
                    lineSpacing = 1.12f
                )
            )
        }
        state.rows.forEachIndexed { index, row ->
            content.addView(rowView(row))
            if (index < state.rows.lastIndex) {
                content.addView(ValuePilotCompactUi.divider(context))
            }
        }
    }

    private fun rowView(row: PracticalShoppingSettingsRow): View {
        val action = row.action
        val arrow = TextView(context).apply {
            text = if (action == null) "" else "›"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_MUTED))
            gravity = android.view.Gravity.CENTER
            minWidth = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            minHeight = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            contentDescription = if (action == null) "" else "Open ${row.title}"
            importantForAccessibility = if (action == null) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        val compactRow = ValuePilotCompactRow(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
            bind(
                primaryText = row.title,
                secondaryText = row.summary,
                trailingView = if (action == null) null else arrow,
                bodyDescription = "${row.title}: ${row.summary}",
                onBodyClick = action?.let { selected ->
                    { onAction?.invoke(selected) }
                }
            )
            isEnabled = onAction != null || action == null
        }
        actionControls += compactRow
        return compactRow
    }

    private fun textLine(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0,
        lineSpacing: Float = 1f
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(ValuePilotPalette.color(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), lineSpacing)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

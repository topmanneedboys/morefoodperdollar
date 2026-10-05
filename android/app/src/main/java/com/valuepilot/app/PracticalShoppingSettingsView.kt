package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

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

    private val actionButtons = mutableListOf<MaterialButton>()

    var onAction: ((PracticalShoppingSettingsAction) -> Unit)? = null
        set(value) {
            field = value
            actionButtons.forEach { it.isEnabled = value != null }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
        addView(content)
    }

    fun render(state: PracticalShoppingSettingsState) {
        content.removeAllViews()
        actionButtons.clear()
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
        state.rows.forEach { row -> content.addView(rowView(row)) }
    }

    private fun rowView(row: PracticalShoppingSettingsRow): View {
        val card = ValuePilotProductCard(context).apply {
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
            contentDescription = "${row.title}: ${row.summary}"
        }
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(13))
        }
        body.addView(
            textLine(
                value = row.title,
                sizeSp = 17f,
                color = ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
                bold = true
            )
        )
        body.addView(
            textLine(
                value = row.summary,
                sizeSp = 14f,
                color = ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                topPadding = 5,
                lineSpacing = 1.08f
            )
        )
        row.action?.let { action ->
            val button = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
                text = when (action) {
                    PracticalShoppingSettingsAction.OpenPreferences -> "Open Preferences"
                    PracticalShoppingSettingsAction.OpenDataPrivacy -> "Open Data & privacy"
                    PracticalShoppingSettingsAction.OpenDataSources -> "Open Data sources"
                    PracticalShoppingSettingsAction.OpenAbout -> "Open About"
                    PracticalShoppingSettingsAction.OpenLicences -> "Open Licences & attribution"
                    PracticalShoppingSettingsAction.OpenTechnicalDetails -> "Open Technical details"
                }
                contentDescription = text
                isEnabled = onAction != null
                setOnClickListener { onAction?.invoke(action) }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(9)
                }
            }
            actionButtons += button
            body.addView(button)
        }
        card.addView(body)
        return card
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

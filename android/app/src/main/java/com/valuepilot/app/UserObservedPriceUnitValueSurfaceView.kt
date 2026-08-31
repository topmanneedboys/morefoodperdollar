package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Inactive-by-default physical renderer for proof-backed observed-price unit-value presentation.
 *
 * This view consumes only [UserObservedPriceUnitValueUiState]. It does not inspect evidence claims,
 * proof artifacts, timestamps, freshness policy, quantity candidates, provider/dataset lifecycle,
 * or ranking inputs. All semantic decisions and consumer copy are supplied by the verified
 * evaluator/projector boundary before this renderer is called.
 */
class UserObservedPriceUnitValueSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), UserObservedPriceUnitValueSurfaceRenderer {

    private val headlineView = textLine(sizeSp = 20f, textColor = "#111827", bold = true)
    private val evidenceLabelView = textLine(sizeSp = 12f, textColor = "#6B7280", topPadding = 4)
    private val statusTitleView = textLine(sizeSp = 16f, textColor = "#111827", bold = true, topPadding = 14)
    private val guidanceView = textLine(sizeSp = 14f, textColor = "#374151", topPadding = 6)
    private val unitRateView =
        textLine(sizeSp = 18f, textColor = "#111827", bold = true, topPadding = 12).apply {
            visibility = View.GONE
        }
    private val noticeView = textLine(sizeSp = 12f, textColor = "#6B7280", topPadding = 14)

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
        setPadding(dp(16), dp(16), dp(16), dp(16))

        addView(headlineView)
        addView(evidenceLabelView)
        addView(statusTitleView)
        addView(guidanceView)
        addView(unitRateView)
        addView(noticeView)
    }

    override fun render(state: UserObservedPriceUnitValueUiState) {
        headlineView.text = state.headline
        evidenceLabelView.text = state.evidenceLabel
        statusTitleView.text = state.statusTitle
        guidanceView.text = state.guidance

        val unitRateText = state.unitRateText
        unitRateView.text = unitRateText.orEmpty()
        unitRateView.visibility = if (unitRateText == null) View.GONE else View.VISIBLE

        noticeView.text = state.notice
        visibility = View.VISIBLE
    }

    private fun textLine(
        sizeSp: Float,
        textColor: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Color.parseColor(textColor))
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

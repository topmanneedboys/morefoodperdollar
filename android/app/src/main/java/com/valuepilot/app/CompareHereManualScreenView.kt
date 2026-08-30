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
 * Replaceable Android surface for the manual Compare Here route.
 *
 * This View receives only [CompareHereManualScreenContent]. It does not receive raw capture text,
 * observation/candidate ids, parser output, comparison intent keys or ranking engines.
 */
class CompareHereManualScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), CompareHereManualScreenRenderer {

    private val messageContainer = LinearLayout(context).apply {
        orientation = VERTICAL
    }
    private val messageTitle = textView(18f, "#111827", true)
    private val messageGuidance = textView(14f, "#4B5563", false, topPadding = 5)
    private val rejectedCount = textView(13f, "#92400E", true, topPadding = 7)
    private val comparisonSurface = CompareHereSurfaceView(context)

    init {
        orientation = VERTICAL
        isSaveEnabled = false

        messageContainer.addView(messageTitle)
        messageContainer.addView(messageGuidance)
        messageContainer.addView(rejectedCount)
        addView(messageContainer)
        addView(comparisonSurface)

        messageContainer.visibility = GONE
        comparisonSurface.visibility = GONE
        rejectedCount.visibility = GONE
    }

    override fun render(content: CompareHereManualScreenContent) {
        when (content) {
            is CompareHereManualScreenContent.Message -> renderMessage(content)
            is CompareHereManualScreenContent.Comparison -> renderComparison(content.state)
        }
    }

    private fun renderMessage(content: CompareHereManualScreenContent.Message) {
        comparisonSurface.visibility = GONE
        messageContainer.visibility = VISIBLE
        messageTitle.text = content.title
        messageGuidance.text = content.guidance

        if (content.rejectedProductCount > 0) {
            rejectedCount.text =
                if (content.rejectedProductCount == 1) {
                    "1 product could not be compared safely."
                } else {
                    "${content.rejectedProductCount} products could not be compared safely."
                }
            rejectedCount.visibility = VISIBLE
        } else {
            rejectedCount.text = ""
            rejectedCount.visibility = GONE
        }
    }

    private fun renderComparison(state: CompareHereUiState) {
        messageContainer.visibility = GONE
        rejectedCount.text = ""
        rejectedCount.visibility = GONE
        comparisonSurface.visibility = VISIBLE
        comparisonSurface.render(state)
    }

    private fun textView(
        sizeSp: Float,
        color: String,
        bold: Boolean,
        topPadding: Int = 0
    ): TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

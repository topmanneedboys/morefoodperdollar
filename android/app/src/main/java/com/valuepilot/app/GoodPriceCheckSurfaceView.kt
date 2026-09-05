package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

/**
 * Physical renderer for the good-price question. It receives immutable consumer content only;
 * parsing, personal-history matching and exact arithmetic remain upstream.
 */
internal class GoodPriceCheckSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), GoodPriceCheckScreenRenderer {

    private val messageContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val messageTitle = line("", 20f, "#111827", true)
    private val messageGuidance = line("", 14f, "#4B5563", topPadding = 6)
    private val resultContainer = bareColumn().apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    init {
        orientation = VERTICAL
        isSaveEnabled = false
        messageContainer.addView(messageTitle)
        messageContainer.addView(messageGuidance)
        addView(messageContainer)
        addView(resultContainer)
        messageContainer.visibility = GONE
        resultContainer.visibility = GONE
    }

    override fun render(content: GoodPriceCheckScreenContent) {
        when (content) {
            is GoodPriceCheckScreenContent.Message -> renderMessage(content)
            is GoodPriceCheckScreenContent.Result -> renderResult(content.state)
        }
    }

    private fun renderMessage(content: GoodPriceCheckScreenContent.Message) {
        resultContainer.visibility = GONE
        messageContainer.visibility = VISIBLE
        messageTitle.text = content.title
        messageGuidance.text = content.guidance
    }

    private fun renderResult(state: GoodPriceCheckUiState) {
        messageContainer.visibility = GONE
        resultContainer.removeAllViews()
        resultContainer.addView(line(state.headline, 22f, "#111827", true))
        resultContainer.addView(line(state.priceModeText, 13f, "#6B7280", topPadding = 4))
        resultContainer.addView(
            card(
                background = answerBackground(state.answerTone),
                stroke = answerStroke(state.answerTone),
                topMargin = 14
            ).apply {
                // Expose the complete projected answer as one coherent node. The visible child
                // labels are decorative for assistive technology so the same facts are not
                // announced repeatedly or in an ambiguous order.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = goodPriceAnswerCardContentDescription(state)
                addView(
                    column().apply {
                        importantForAccessibility =
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        addView(line(state.answerTitle, 19f, "#111827", true))
                        addView(
                            line(
                                state.productName,
                                17f,
                                "#111827",
                                true,
                                topPadding = 12
                            )
                        )
                        addView(
                            line(
                                "${state.priceText}  •  ${state.quantityText}",
                                14f,
                                "#374151",
                                topPadding = 7
                            )
                        )
                        addView(line(state.unitRateText, 17f, "#111827", true, 7))
                        addView(line(state.answerGuidance, 14f, "#374151", topPadding = 10))
                    }
                )
            }
        )
        state.historyText?.let { history ->
            resultContainer.addView(
                card("#F9FAFB", "#E5E7EB", 10).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    contentDescription = history
                    addView(
                        column().apply {
                            importantForAccessibility =
                                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            addView(line(history, 13f, "#374151"))
                        }
                    )
                }
            )
        }
        resultContainer.addView(line(state.disclosure, 12f, "#6B7280", topPadding = 12))
        resultContainer.visibility = VISIBLE
    }

    private fun answerBackground(tone: GoodPriceCheckAnswerTone): String =
        when (tone) {
            GoodPriceCheckAnswerTone.POSITIVE -> "#ECFDF5"
            GoodPriceCheckAnswerTone.NEUTRAL -> "#F9FAFB"
            GoodPriceCheckAnswerTone.CAUTION -> "#FFFBEB"
        }

    private fun answerStroke(tone: GoodPriceCheckAnswerTone): String =
        when (tone) {
            GoodPriceCheckAnswerTone.POSITIVE -> "#A7F3D0"
            GoodPriceCheckAnswerTone.NEUTRAL -> "#E5E7EB"
            GoodPriceCheckAnswerTone.CAUTION -> "#FDE68A"
        }

    private fun card(
        background: String,
        stroke: String,
        topMargin: Int
    ): MaterialCardView = MaterialCardView(context).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.parseColor(background))
        strokeColor = Color.parseColor(stroke)
        strokeWidth = dp(1)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, topMargin)
    }

    private fun bareColumn(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
    }

    private fun column(): LinearLayout = bareColumn().apply {
        setPadding(dp(16), dp(15), dp(16), dp(15))
    }

    private fun line(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun fullWidth(height: Int, topMargin: Int): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, height).apply { this.topMargin = dp(topMargin) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/**
 * Builds the one-node accessibility summary for the exact projected answer card.
 *
 * This is formatting only. The supplied fields have already been validated and formatted by
 * the Good Price coordinator; no price, quantity, history, or recommendation is inferred here.
 */
internal fun goodPriceAnswerCardContentDescription(state: GoodPriceCheckUiState): String =
    listOf(
            state.priceModeText,
            state.answerTitle,
            state.productName,
            state.priceText,
            state.quantityText,
            state.unitRateText,
            state.answerGuidance
        )
        .joinToString(". ") { value -> value.trim().trimEnd('.', '!', '?') } + "."

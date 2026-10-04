package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

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
    private val messageTitle = textLine("", 20f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true)
    private val messageGuidance = textLine(
        "",
        ValuePilotDesignTokens.Typography.BODY_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = ValuePilotDesignTokens.Spacing.XS_DP
    )
    private val messageCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.SM_DP)
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(messageTitle)
            addView(messageGuidance)
        })
    }
    private val resultContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val resultHeadline = textLine("", 22f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true)
    private val resultPriceMode = textLine(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_MUTED,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    )
    private val answerCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.SM_DP)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val answerBody = LinearLayout(context).apply {
        orientation = VERTICAL
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setPadding(dp(16), dp(15), dp(16), dp(15))
    }
    private val answerBasis = evidenceChip("This exact price only")
    private val answerTitle = textLine("", 19f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true)
    private val answerProduct = textLine("", 17f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true, topPadding = 12)
    private val answerFacts = textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 7)
    private val answerUnitRate = textLine("", 17f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true, topPadding = 7)
    private val answerGuidance = textLine("", ValuePilotDesignTokens.Typography.BODY_SP, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 10)
    private val historyCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.XS_DP)
        visibility = GONE
    }
    private val historyText = textLine("", 13f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY)
    private val disclosureButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "What this means"
        contentDescription = "Show what evidence this price answer uses"
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.SM_DP)
    }
    private val disclosureText = textLine(
        "",
        12f,
        ValuePilotDesignTokens.Colors.TEXT_MUTED,
        topPadding = ValuePilotDesignTokens.Spacing.XS_DP
    ).apply { visibility = GONE }
    private var disclosureExpanded = false

    init {
        orientation = VERTICAL
        isSaveEnabled = false
        messageContainer.addView(messageCard)
        addView(messageContainer)
        addView(resultContainer)
        answerBody.addView(answerBasis)
        answerBody.addView(answerTitle)
        answerBody.addView(answerProduct)
        answerBody.addView(answerFacts)
        answerBody.addView(answerUnitRate)
        answerBody.addView(answerGuidance)
        answerCard.addView(answerBody)
        resultContainer.addView(resultHeadline)
        resultContainer.addView(resultPriceMode)
        resultContainer.addView(answerCard)
        historyCard.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            addView(historyText)
        })
        resultContainer.addView(historyCard)
        resultContainer.addView(disclosureButton)
        resultContainer.addView(disclosureText)
        messageContainer.visibility = GONE
        resultContainer.visibility = GONE
        disclosureButton.setOnClickListener { setDisclosureExpanded(!disclosureExpanded) }
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
        resultContainer.visibility = VISIBLE
        resultHeadline.text = state.headline
        resultPriceMode.text = state.priceModeText
        answerCard.setCardBackgroundColor(ValuePilotPalette.color(answerBackground(state.answerTone)))
        answerCard.strokeColor = ValuePilotPalette.color(answerStroke(state.answerTone))
        answerCard.contentDescription = goodPriceAnswerCardContentDescription(state)
        answerBasis.text = if (state.historyText == null) "Evidence: this exact price only" else "Evidence: your private history"
        answerBasis.contentDescription = answerBasis.text
        answerTitle.text = state.answerTitle
        answerProduct.text = state.productName
        answerFacts.text = "${state.priceText}  •  ${state.quantityText}"
        answerUnitRate.text = state.unitRateText
        answerGuidance.text = state.answerGuidance
        val history = state.historyText
        if (history == null) {
            historyCard.visibility = GONE
        } else {
            historyText.text = history
            historyCard.contentDescription = history
            historyCard.visibility = VISIBLE
        }
        disclosureText.text = state.disclosure
        setDisclosureExpanded(false)
        ValuePilotMotion.reveal(answerCard, animate = true)
    }

    private fun setDisclosureExpanded(expanded: Boolean) {
        disclosureExpanded = expanded
        disclosureText.visibility = if (expanded) VISIBLE else GONE
        disclosureButton.text = if (expanded) "Hide what this means" else "What this means"
        disclosureButton.contentDescription =
            if (expanded) "Hide what evidence this price answer uses" else "Show what evidence this price answer uses"
        if (expanded) ValuePilotMotion.reveal(disclosureText)
    }

    private fun evidenceChip(value: String): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.PRIMARY_DARK))
        setPadding(dp(10), dp(7), dp(10), dp(7))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE_SUBTLE))
        }
        layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun answerBackground(tone: GoodPriceCheckAnswerTone): String =
        when (tone) {
            GoodPriceCheckAnswerTone.POSITIVE -> ValuePilotDesignTokens.Colors.SUCCESS_SURFACE
            GoodPriceCheckAnswerTone.NEUTRAL -> ValuePilotDesignTokens.Colors.SURFACE
            GoodPriceCheckAnswerTone.CAUTION -> ValuePilotDesignTokens.Colors.WARNING_SURFACE
        }

    private fun answerStroke(tone: GoodPriceCheckAnswerTone): String =
        when (tone) {
            GoodPriceCheckAnswerTone.POSITIVE -> ValuePilotDesignTokens.Colors.SUCCESS_OUTLINE
            GoodPriceCheckAnswerTone.NEUTRAL -> ValuePilotDesignTokens.Colors.OUTLINE
            GoodPriceCheckAnswerTone.CAUTION -> ValuePilotDesignTokens.Colors.WARNING_OUTLINE
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

    private fun fullWidth(topMargin: Int): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = dp(topMargin)
        }

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

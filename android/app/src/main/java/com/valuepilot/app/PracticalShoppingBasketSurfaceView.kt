package com.valuepilot.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

sealed interface PracticalShoppingBasketUiAction {
    data object OpenHome : PracticalShoppingBasketUiAction
}

/** Renders immutable Basket state and emits typed navigation actions only. */
class PracticalShoppingBasketSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onAction: ((PracticalShoppingBasketUiAction) -> Unit)? = null

    private val headline = line("", 22f, "#111827", true)
    private val guidance = line("", 14f, "#4B5563", topPadding = 8)
    private val itemsHeading =
        line(context.getString(R.string.basket_items_title), 13f, "#374151", true, 18)
    private val itemsContainer = column(padded = false)
    private val unresolvedBody = column()
    private val unresolvedCard = card("#FFF7ED", "#FED7AA", 12, unresolvedBody)
    private val planResult = PracticalShoppingPlanResultSurfaceView(context)
    private val extraStopRule = line("", 13f, "#374151", topPadding = 12)
    private val sampleNotice = line("", 13f, "#374151", topPadding = 4)
    private val actionButton = MaterialButton(context).apply {
        isAllCaps = false
        textSize = 15f
        cornerRadius = dp(16)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        setTextColor(Color.parseColor("#374151"))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = fullWidth(dp(52), 16)
        setOnClickListener { onAction?.invoke(PracticalShoppingBasketUiAction.OpenHome) }
    }

    init {
        orientation = VERTICAL
        isSaveEnabled = false
        addView(sampleCard())
        addView(headline.apply { setPadding(0, dp(20), 0, 0) })
        addView(guidance)
        addView(itemsHeading)
        addView(itemsContainer)
        addView(unresolvedCard)
        addView(planResult)
        addView(extraStopRule)
        addView(actionButton)
        itemsHeading.visibility = GONE
        unresolvedCard.visibility = GONE
        extraStopRule.visibility = GONE
    }

    fun render(state: PracticalShoppingBasketRenderState) {
        headline.text = state.headline
        guidance.text = state.guidance
        sampleNotice.text = state.sampleNotice
        actionButton.text = state.actionLabel
        renderItems(state.items)
        renderUnknownItems(state.unknownItems)
        planResult.render(state.result)
        extraStopRule.text = state.extraStopRuleText.orEmpty()
        extraStopRule.visibility = if (state.extraStopRuleText == null) GONE else VISIBLE
    }

    private fun renderItems(items: List<PracticalShoppingHomeItemRenderState>) {
        itemsContainer.removeAllViews()
        itemsHeading.visibility = if (items.isEmpty()) GONE else VISIBLE
        items.forEach { item ->
            itemsContainer.addView(
                line("• ${item.name}  •  ${item.detail}", 14f, "#374151", topPadding = 7)
            )
        }
    }

    private fun renderUnknownItems(items: List<String>) {
        unresolvedBody.removeAllViews()
        if (items.isEmpty()) {
            unresolvedCard.visibility = GONE
            return
        }
        unresolvedBody.addView(
            line(
                context.getString(R.string.basket_unresolved_title),
                13f,
                "#92400E",
                true
            )
        )
        items.forEach { item ->
            unresolvedBody.addView(line("• $item", 14f, "#92400E", topPadding = 7))
        }
        unresolvedCard.visibility = VISIBLE
    }

    private fun sampleCard(): View =
        card("#F0FDF4", "#BBF7D0", 0).apply {
            addView(
                column().apply {
                    addView(
                        line(
                            context.getString(R.string.home_sample_badge),
                            11f,
                            "#047857",
                            true
                        )
                    )
                    addView(sampleNotice)
                }
            )
        }

    private fun card(
        background: String,
        stroke: String,
        topMargin: Int,
        body: View? = null
    ): MaterialCardView = MaterialCardView(context).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.parseColor(background))
        strokeColor = Color.parseColor(stroke)
        strokeWidth = dp(1)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, topMargin)
        body?.let(::addView)
    }

    private fun column(padded: Boolean = true): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        if (padded) setPadding(dp(16), dp(15), dp(16), dp(15))
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

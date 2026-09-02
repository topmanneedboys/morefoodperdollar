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

/** Mechanically renders an already-projected Practical Shopping decision. */
class PracticalShoppingPlanResultSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = VERTICAL
        isSaveEnabled = false
    }

    fun render(state: PracticalShoppingUiState?) {
        removeAllViews()
        if (state == null) return

        addView(line(state.headline, 22f, "#111827", true, 24))
        state.primary?.let { addView(primaryCard(it)) }
        state.secondStop?.let { addView(secondStopCard(it)) }
        state.secondaryMessage?.let {
            addView(line(it, 13f, "#6B7280", topPadding = 12))
        }
    }

    private fun primaryCard(state: PracticalShoppingPrimaryUiState): View =
        card("#ECFDF5", "#A7F3D0", 12).apply {
            addView(
                column().apply {
                    addView(line(state.badge, 11f, "#047857", true))
                    addView(line(state.storeName, 22f, "#111827", true, 6))
                    addView(line(state.basketCostText, 18f, "#111827", true, 8))
                    addView(
                        line(
                            "${state.coverageText}  •  ${state.travelText}",
                            14f,
                            "#374151",
                            topPadding = 6
                        )
                    )
                    state.missingItemsText?.let {
                        addView(line(it, 13f, "#92400E", true, 8))
                    }
                    addView(line(state.evidenceText, 12f, "#6B7280", topPadding = 5))
                    addView(line(state.whyText, 13f, "#374151", topPadding = 9))
                    state.notice?.let {
                        addView(line(it, 12f, "#92400E", topPadding = 7))
                    }
                }
            )
        }

    private fun secondStopCard(state: PracticalShoppingSecondStopUiState): View =
        card("#FFFFFF", "#D1FAE5", 12).apply {
            addView(
                column().apply {
                    addView(line(state.badge, 11f, "#047857", true))
                    addView(line(state.storeName, 18f, "#111827", true, 6))
                    addView(line(state.baseItemsText, 13f, "#374151", topPadding = 8))
                    addView(line(state.addedItemsText, 13f, "#374151", topPadding = 4))
                    addView(line(state.savingsText, 17f, "#047857", true, 7))
                    addView(line(state.combinedBasketCostText, 14f, "#374151", topPadding = 5))
                    addView(line(state.additionalTravelText, 13f, "#374151", topPadding = 4))
                    addView(line(state.evidenceText, 12f, "#6B7280", topPadding = 5))
                }
            )
        }

    private fun card(background: String, stroke: String, topMargin: Int): MaterialCardView =
        MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor(background))
            strokeColor = Color.parseColor(stroke)
            strokeWidth = dp(1)
            layoutParams =
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    this.topMargin = dp(topMargin)
                }
        }

    private fun column(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

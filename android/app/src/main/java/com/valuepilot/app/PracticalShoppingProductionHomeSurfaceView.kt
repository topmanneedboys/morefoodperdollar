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
 * Inactive-by-default renderer for the production Home state.
 *
 * The view receives only [PracticalShoppingProductionHomeUiState]. It does not
 * know how a plan was chosen, does not calculate totals, and has no provider or
 * network access. The existing fictional Home surface remains the visible
 * default until a separate activation milestone supplies lawful production
 * evidence and a coordinator.
 */
class PracticalShoppingProductionHomeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), PracticalShoppingProductionHomeRenderer {

    private val planResult = PracticalShoppingPlanResultSurfaceView(context)

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    override fun render(state: PracticalShoppingProductionHomeUiState?) {
        removeAllViews()

        if (state == null) {
            visibility = View.GONE
            return
        }

        if (state.status == PracticalShoppingProductionHomeStatus.UNAVAILABLE) {
            addView(sectionHeading(context.getString(R.string.production_home_unavailable_title)))
            addView(
                noticeLine(
                    requireNotNull(state.notice),
                    color = "#92400E"
                )
            )
            visibility = View.VISIBLE
            return
        }

        addView(sectionHeading(context.getString(R.string.production_home_items_title)))
        state.items.forEach { item -> addView(itemCard(item)) }

        // The existing result-card renderer owns all plan-card formatting. This
        // surface only adds the production list rows around that projection.
        planResult.render(requireNotNull(state.result))
        addView(planResult)
        visibility = View.VISIBLE
    }

    private fun sectionHeading(textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun itemCard(item: PracticalShoppingProductionHomeItemUiState): View {
        val cautious = item.coverageNotice != null
        val card =
            MaterialCardView(context).apply {
                radius = dp(16).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(
                    Color.parseColor(if (cautious) "#FFFBEB" else "#FFFFFF")
                )
                strokeColor =
                    Color.parseColor(if (cautious) "#FDE68A" else "#E5E7EB")
                strokeWidth = dp(1)
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = itemDescription(item)
            }

        val body =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }

        body.addView(textLine(item.name, 17f, "#111827", bold = true))
        item.storeAssignment?.let { store ->
            body.addView(
                textLine(
                    context.getString(R.string.production_home_store_assignment, store),
                    13f,
                    "#374151",
                    topPadding = 6
                )
            )
        }
        item.plannedPriceText?.let { price ->
            body.addView(
                textLine(
                    context.getString(R.string.production_home_included_price, price),
                    14f,
                    "#047857",
                    bold = true,
                    topPadding = 6
                )
            )
        }
        item.plannedPriceNotice?.let { notice ->
            body.addView(textLine(notice, 13f, "#6B7280", topPadding = 6))
        }
        item.coverageNotice?.let { notice ->
            body.addView(textLine(notice, 13f, "#92400E", bold = true, topPadding = 6))
        }

        card.addView(body)
        return card
    }

    private fun itemDescription(item: PracticalShoppingProductionHomeItemUiState): String =
        listOfNotNull(
                item.name,
                item.storeAssignment?.let { store ->
                    context.getString(R.string.production_home_store_assignment, store)
                },
                item.plannedPriceText?.let { price ->
                    context.getString(R.string.production_home_included_price, price)
                },
                item.plannedPriceNotice,
                item.coverageNotice
            )
            .joinToString(". ") { value -> value.trim().trimEnd('.', '!', '?') } + "."

    private fun noticeLine(textValue: String, color: String): TextView =
        textLine(textValue, 13f, color, topPadding = 10)

    private fun textLine(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

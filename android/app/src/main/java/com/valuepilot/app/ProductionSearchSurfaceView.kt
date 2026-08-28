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
 * Physically separate, inactive-by-default renderer for production Search state.
 *
 * The only render input is [ProductionSearchUiState] through the narrow
 * [ProductionSearchSurfaceRenderer] contract. The view has no access to exact
 * production lookup maps, raw provider URLs, internal merchant/location/channel
 * identifiers, blocker enums, provider adapters, or the legacy sample Search
 * controller/ranking pipeline.
 *
 * Merely placing this view in the Android shell does not activate production
 * search. It stays GONE until an authorized future coordinator drives its host.
 */
class ProductionSearchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), ProductionSearchSurfaceRenderer {

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: ProductionSearchUiState?) {
        removeAllViews()

        if (state == null || (state.groups.isEmpty() && state.blocked.isEmpty())) {
            visibility = View.GONE
            return
        }

        addView(sectionHeading(context.getString(R.string.production_search_results_title)))

        state.groups.forEach { group ->
            addView(groupHeading(group.title))
            group.rows.forEach { row -> addView(resultCard(row)) }
        }

        if (state.blocked.isNotEmpty()) {
            addView(
                referenceNotice(
                    resources.getQuantityString(
                        R.plurals.production_search_reference_only_count,
                        state.blocked.size,
                        state.blocked.size
                    )
                )
            )
        }

        visibility = View.VISIBLE
    }

    private fun sectionHeading(textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun groupHeading(textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#374151"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(18), 0, 0)
        }

    private fun resultCard(row: ProductionSearchRowUiState): View {
        val card =
            MaterialCardView(context).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(
                    Color.parseColor(if (row.bestValue) "#ECFDF5" else "#FFFFFF")
                )
                strokeColor = Color.parseColor(if (row.bestValue) "#A7F3D0" else "#E5E7EB")
                strokeWidth = dp(1)
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
            }

        val body =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
            }

        body.addView(
            textLine(
                value =
                    if (row.bestValue) {
                        context.getString(R.string.best_value_rank, row.valueRank)
                    } else {
                        context.getString(R.string.rank_number, row.valueRank)
                    },
                sizeSp = 12f,
                textColor = if (row.bestValue) "#047857" else "#6B7280",
                bold = true
            )
        )
        body.addView(
            textLine(
                value = row.name,
                sizeSp = 18f,
                textColor = "#111827",
                bold = true,
                topPadding = 5
            )
        )
        body.addView(
            textLine(
                value = listOfNotNull(row.priceText, row.referencePriceText).joinToString("  •  "),
                sizeSp = 15f,
                textColor = "#374151",
                topPadding = 7
            )
        )
        body.addView(
            textLine(
                value = "${row.quantityText}  •  ${row.unitRateText}",
                sizeSp = 16f,
                textColor = "#111827",
                bold = true,
                topPadding = 7
            )
        )
        body.addView(
            textLine(
                value = row.offerScopeText,
                sizeSp = 12f,
                textColor = "#6B7280",
                topPadding = 6
            )
        )
        body.addView(
            textLine(
                value = "${row.availabilityText}  •  ${row.freshnessText}",
                sizeSp = 12f,
                textColor = "#6B7280",
                topPadding = 3
            )
        )
        body.addView(
            textLine(
                value = row.sourceSummary,
                sizeSp = 12f,
                textColor = "#6B7280",
                topPadding = 8
            )
        )

        card.addView(body)
        return card
    }

    private fun referenceNotice(textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#92400E"))
            setPadding(dp(2), dp(16), dp(2), 0)
        }

    private fun textLine(
        value: String,
        sizeSp: Float,
        textColor: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Color.parseColor(textColor))
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

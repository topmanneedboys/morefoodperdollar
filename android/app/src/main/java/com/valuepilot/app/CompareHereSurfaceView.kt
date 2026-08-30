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
 * Replaceable Android renderer for Compare Here.
 *
 * It receives immutable consumer state only. It never receives candidate ids, core comparison
 * objects, raw capture/provider facts or ranking engines, and it performs no value arithmetic.
 */
class CompareHereSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), CompareHereSurfaceRenderer {

    private val headline = line("", 24f, "#111827", true)
    private val priceMode = line("", 13f, "#6B7280", topPadding = 4)
    private val statusTitle = line("", 18f, "#111827", true, 18)
    private val guidance = line("", 14f, "#374151", topPadding = 5)
    private val rowsContainer = bareColumn()
    private val blockedContainer = bareColumn()
    private val notice = line("", 12f, "#92400E", topPadding = 12)

    init {
        orientation = VERTICAL
        isSaveEnabled = false

        addView(headline)
        addView(priceMode)
        addView(statusTitle)
        addView(guidance)
        addView(rowsContainer)
        addView(blockedContainer)
        addView(notice)

        notice.visibility = GONE
    }

    override fun render(state: CompareHereUiState) {
        headline.text = state.headline
        priceMode.text = state.priceModeText
        statusTitle.text = state.statusTitle
        statusTitle.setTextColor(statusColor(state.status))
        guidance.text = state.guidance

        renderExactRows(state.rows)
        renderBlockedRows(state.blockedRows)

        val noticeText = state.notice
        if (noticeText == null) {
            notice.text = ""
            notice.visibility = GONE
        } else {
            notice.text = noticeText
            notice.visibility = VISIBLE
        }
    }

    private fun renderExactRows(rows: List<CompareHereUiRow>) {
        rowsContainer.removeAllViews()
        rows.forEach { row -> rowsContainer.addView(exactCard(row)) }
    }

    private fun renderBlockedRows(rows: List<CompareHereBlockedUiRow>) {
        blockedContainer.removeAllViews()
        rows.forEach { row -> blockedContainer.addView(blockedCard(row)) }
    }

    private fun exactCard(row: CompareHereUiRow): View =
        card(
            background = if (row.bestValue) "#ECFDF5" else "#F9FAFB",
            stroke = if (row.bestValue) "#A7F3D0" else "#E5E7EB",
            topMargin = 12
        ).apply {
            addView(
                column().apply {
                    row.valueRank?.let { rank ->
                        addView(
                            line(
                                value = if (row.bestValue) "Best value" else "Value rank #$rank",
                                sizeSp = 12f,
                                color = if (row.bestValue) "#047857" else "#6B7280",
                                bold = true
                            )
                        )
                    }
                    addView(line(row.title, 18f, "#111827", true, 5))
                    addView(
                        line(
                            "${row.priceText}  •  ${row.quantityText}",
                            14f,
                            "#374151",
                            topPadding = 6
                        )
                    )
                    addView(line(row.unitRateText, 16f, "#111827", true, 7))
                }
            )
        }

    private fun blockedCard(row: CompareHereBlockedUiRow): View =
        card(
            background = "#FFFBEB",
            stroke = "#FDE68A",
            topMargin = 10
        ).apply {
            addView(
                column().apply {
                    addView(line("Needs information", 11f, "#92400E", true))
                    addView(line(row.title, 17f, "#111827", true, 5))
                    addView(line(row.reasonText, 13f, "#92400E", topPadding = 5))
                }
            )
        }

    private fun statusColor(status: CompareHereUiStatus): Int =
        Color.parseColor(
            when (status) {
                CompareHereUiStatus.READY -> "#047857"
                CompareHereUiStatus.NOT_ENOUGH_DATA -> "#92400E"
                CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS -> "#B42318"
                CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE -> "#B42318"
            }
        )

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

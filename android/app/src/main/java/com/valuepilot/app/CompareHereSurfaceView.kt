package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

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

    private val headline = textLine("", 24f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true)
    private val priceMode = textLine(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_MUTED,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    )
    private val statusTitle = textLine(
        "",
        18f,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        bold = true
    ).apply {
        // Comparison readiness and evaluated-result status change after the
        // user's typed action. Announce the projected status without moving
        // any comparison or ranking authority into this View.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val guidance = textLine(
        "",
        ValuePilotDesignTokens.Typography.BODY_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = ValuePilotDesignTokens.Spacing.XS_DP
    )
    private val statusCardBody = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(
            dp(ValuePilotDesignTokens.Spacing.MD_DP),
            dp(15),
            dp(ValuePilotDesignTokens.Spacing.MD_DP),
            dp(15)
        )
        addView(statusTitle)
        addView(guidance)
    }
    private val statusCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.SM_DP)
        addView(statusCardBody)
    }
    private val rowsContainer = bareColumn()
    private val blockedContainer = bareColumn().apply { visibility = GONE }
    private val notice = textLine(
        "",
        12f,
        ValuePilotDesignTokens.Colors.WARNING,
        topPadding = ValuePilotDesignTokens.Spacing.SM_DP
    ).apply { visibility = GONE }
    private val detailsButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "Why?"
        contentDescription = "Show why some choices cannot be compared"
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.SM_DP)
        visibility = GONE
    }
    private var detailsExpanded = false
    private var renderedOnce = false

    init {
        orientation = VERTICAL
        isSaveEnabled = false
        addView(headline)
        addView(priceMode)
        addView(statusCard)
        addView(rowsContainer)
        addView(detailsButton)
        addView(blockedContainer)
        addView(notice)
        detailsButton.setOnClickListener { setDetailsExpanded(!detailsExpanded) }
    }

    override fun render(state: CompareHereUiState) {
        headline.text = state.headline
        priceMode.text = state.priceModeText
        statusTitle.text = state.statusTitle
        statusTitle.setTextColor(statusColor(state.status))
        guidance.text = state.guidance
        statusCard.setCardBackgroundColor(ValuePilotPalette.color(statusBackground(state.status)))
        statusCard.strokeColor = ValuePilotPalette.color(statusStroke(state.status))

        val sameValue = state.status == CompareHereUiStatus.READY && state.rows.count { it.bestValue } > 1
        renderExactRows(state.rows, sameValue)
        renderBlockedRows(state.blockedRows)

        val noticeText = state.notice
        if (noticeText == null) {
            notice.text = ""
        } else {
            notice.text = noticeText
        }

        val hasAdditionalDetails = state.blockedRows.isNotEmpty() || noticeText != null
        detailsButton.visibility = if (hasAdditionalDetails) VISIBLE else GONE
        if (!hasAdditionalDetails) {
            setDetailsExpanded(false)
        } else {
            detailsButton.text = if (detailsExpanded) "Hide details" else "Why?"
            detailsButton.contentDescription =
                if (detailsExpanded) {
                    "Hide comparison details"
                } else {
                    "Show why some choices cannot be compared"
                }
            blockedContainer.visibility = if (detailsExpanded) VISIBLE else GONE
            notice.visibility = if (detailsExpanded && noticeText != null) VISIBLE else GONE
        }
        renderedOnce = true
    }

    private fun renderExactRows(rows: List<CompareHereUiRow>, sameValue: Boolean) {
        rowsContainer.removeAllViews()
        rows.forEach { row ->
            val view = exactCard(row, sameValue)
            rowsContainer.addView(view)
            ValuePilotMotion.reveal(view, animate = renderedOnce)
        }
    }

    private fun renderBlockedRows(rows: List<CompareHereBlockedUiRow>) {
        blockedContainer.removeAllViews()
        rows.forEach { row -> blockedContainer.addView(blockedCard(row)) }
    }

    private fun exactCard(row: CompareHereUiRow, sameValue: Boolean): View =
        card(
            background = if (row.bestValue) {
                ValuePilotDesignTokens.Colors.SUCCESS_SURFACE
            } else {
                ValuePilotDesignTokens.Colors.SURFACE
            },
            stroke = if (row.bestValue) {
                ValuePilotDesignTokens.Colors.SUCCESS_OUTLINE
            } else {
                ValuePilotDesignTokens.Colors.OUTLINE
            },
            topMargin = ValuePilotDesignTokens.Spacing.SM_DP
        ).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = compareHereExactCardContentDescription(row, sameValue)
            addView(
                column().apply {
                    // The card exposes one complete projected summary. Hide
                    // decorative child labels to avoid repeated announcements.
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    rowBadge(row, sameValue)?.let { badge ->
                        addView(
                            textLine(
                                value = badge,
                                sizeSp = 12f,
                                color = if (row.bestValue) {
                                    ValuePilotDesignTokens.Colors.SUCCESS
                                } else {
                                    ValuePilotDesignTokens.Colors.TEXT_MUTED
                                },
                                bold = true
                            )
                        )
                    }
                    addView(
                        textLine(
                            row.title,
                            18f,
                            ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
                            bold = true,
                            topPadding = 5
                        )
                    )
                    addView(
                        textLine(
                            "${row.priceText}  •  ${row.quantityText}",
                            14f,
                            ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                            topPadding = 6
                        )
                    )
                    addView(
                        textLine(
                            row.unitRateText,
                            16f,
                            ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
                            bold = true,
                            topPadding = 7
                        )
                    )
                }
            )
        }

    private fun blockedCard(row: CompareHereBlockedUiRow): View =
        card(
            background = ValuePilotDesignTokens.Colors.SURFACE_ACCENT,
            stroke = ValuePilotDesignTokens.Colors.WARNING_OUTLINE,
            topMargin = ValuePilotDesignTokens.Spacing.XS_DP
        ).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = compareHereBlockedCardContentDescription(row)
            addView(
                column().apply {
                    // Keep this blocked result as one concise accessibility
                    // node while preserving the visible explanation.
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    addView(
                        textLine(
                            "Can't compare yet",
                            11f,
                            ValuePilotDesignTokens.Colors.WARNING,
                            bold = true
                        )
                    )
                    addView(
                        textLine(
                            row.title,
                            17f,
                            ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
                            bold = true,
                            topPadding = 5
                        )
                    )
                    addView(
                        textLine(
                            row.reasonText,
                            13f,
                            ValuePilotDesignTokens.Colors.WARNING,
                            topPadding = 5
                        )
                    )
                }
            )
        }

    private fun setDetailsExpanded(expanded: Boolean) {
        detailsExpanded = expanded
        blockedContainer.visibility = if (expanded) VISIBLE else GONE
        notice.visibility = if (expanded && notice.text.isNotBlank()) VISIBLE else GONE
        if (expanded) ValuePilotMotion.reveal(blockedContainer)
        detailsButton.text = if (expanded) "Hide details" else "Why?"
        detailsButton.contentDescription =
            if (expanded) "Hide comparison details" else "Show why some choices cannot be compared"
    }

    private fun statusColor(status: CompareHereUiStatus): Int =
        ValuePilotPalette.color(
            when (status) {
                CompareHereUiStatus.READY -> ValuePilotDesignTokens.Colors.SUCCESS
                CompareHereUiStatus.NOT_ENOUGH_DATA -> ValuePilotDesignTokens.Colors.WARNING
                CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS,
                CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE -> ValuePilotDesignTokens.Colors.ERROR
            }
        )

    private fun statusBackground(status: CompareHereUiStatus): String =
        when (status) {
            CompareHereUiStatus.READY -> ValuePilotDesignTokens.Colors.SUCCESS_SURFACE
            CompareHereUiStatus.NOT_ENOUGH_DATA,
            CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS,
            CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE -> ValuePilotDesignTokens.Colors.WARNING_SURFACE
        }

    private fun statusStroke(status: CompareHereUiStatus): String =
        when (status) {
            CompareHereUiStatus.READY -> ValuePilotDesignTokens.Colors.SUCCESS_OUTLINE
            CompareHereUiStatus.NOT_ENOUGH_DATA,
            CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS,
            CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE -> ValuePilotDesignTokens.Colors.WARNING_OUTLINE
        }

    private fun card(
        background: String,
        stroke: String,
        topMargin: Int
    ): ValuePilotProductCard = ValuePilotProductCard(context).apply {
        setCardBackgroundColor(ValuePilotPalette.color(background))
        strokeColor = ValuePilotPalette.color(stroke)
        strokeWidth = dp(1)
        layoutParams = fullWidth(topMargin)
    }

    private fun bareColumn(): LinearLayout = LinearLayout(context).apply { orientation = VERTICAL }

    private fun column(): LinearLayout = bareColumn().apply {
        setPadding(
            dp(ValuePilotDesignTokens.Spacing.MD_DP),
            dp(15),
            dp(ValuePilotDesignTokens.Spacing.MD_DP),
            dp(15)
        )
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

private fun accessibilitySummary(parts: List<String>): String =
    parts.joinToString(". ") { it.trim().trimEnd('.', '!', '?') } + "."

internal fun compareHereExactCardContentDescription(row: CompareHereUiRow): String =
    accessibilitySummary(
        listOfNotNull(
            row.valueRank?.let { if (row.bestValue) "Better value" else null },
            row.title,
            row.priceText,
            row.quantityText,
            row.unitRateText
        )
    )

internal fun compareHereExactCardContentDescription(
    row: CompareHereUiRow,
    sameValue: Boolean
): String =
    accessibilitySummary(
        listOfNotNull(
            when {
                !row.bestValue -> null
                sameValue -> "Same value"
                else -> "Better value"
            },
            row.title,
            row.priceText,
            row.quantityText,
            row.unitRateText
        )
    )

internal fun compareHereBlockedCardContentDescription(row: CompareHereBlockedUiRow): String =
    accessibilitySummary(listOf("Can't compare yet", row.title, row.reasonText))

private fun rowBadge(row: CompareHereUiRow, sameValue: Boolean): String? =
    when {
        !row.bestValue -> null
        sameValue -> "Same value"
        else -> "Better value"
    }

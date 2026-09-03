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

/** Replaceable Android renderer for already-projected Watch My Staples consumer state. */
class StapleWatchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), StapleWatchSurfaceRenderer {

    private val headline = line("", 24f, "#111827", true)
    private val statusTitle = line("", 18f, "#111827", true, 16).apply {
        // Watch readiness and economic status are projected after a saved
        // selection changes. Announce the status without granting the View
        // policy, freshness, notification, or ranking authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val guidance = line("", 14f, "#374151", topPadding = 5)
    private val baselineEvidence = line("", 13f, "#6B7280", topPadding = 10)
    private val switchContainer = bareColumn()
    private val notice = line("", 12f, "#92400E", topPadding = 12).apply {
        // Safety and display-metadata warnings are projected state changes.
        // Announce the specific warning politely without granting the View
        // policy, economic, notification, or data authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    init {
        orientation = VERTICAL
        isSaveEnabled = false
        visibility = GONE

        addView(headline)
        addView(statusTitle)
        addView(guidance)
        addView(baselineEvidence)
        addView(switchContainer)
        addView(notice)

        notice.visibility = GONE
    }

    override fun render(state: StapleWatchUiState) {
        headline.text = state.headline
        statusTitle.text = state.statusTitle
        statusTitle.setTextColor(statusColor(state.status))
        guidance.text = state.guidance
        baselineEvidence.text = state.baselineEvidenceText

        renderSwitchCandidate(state.switchCandidate)

        val noticeText = state.notice
        if (noticeText == null) {
            notice.text = ""
            notice.visibility = GONE
        } else {
            notice.text = noticeText
            notice.visibility = VISIBLE
        }
        visibility = VISIBLE
    }

    /** Removes all previously rendered consumer state and hides this physical result surface. */
    fun clear() {
        headline.text = ""
        statusTitle.text = ""
        guidance.text = ""
        baselineEvidence.text = ""
        renderSwitchCandidate(null)
        notice.text = ""
        notice.visibility = GONE
        visibility = GONE
    }

    private fun renderSwitchCandidate(candidate: StapleWatchSwitchUiState?) {
        switchContainer.removeAllViews()
        if (candidate != null) switchContainer.addView(switchCard(candidate))
    }

    private fun switchCard(candidate: StapleWatchSwitchUiState): View =
        card(background = "#ECFDF5", stroke = "#A7F3D0", topMargin = 12).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = stapleWatchSwitchCardContentDescription(candidate)
            addView(
                column().apply {
                    // Keep the candidate as one complete projected summary
                    // instead of repeating every visible child label.
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    addView(line(candidate.badge, 11f, "#047857", true))
                    addView(line(candidate.storeName, 18f, "#111827", true, 5))
                    addView(line(candidate.savingsText, 17f, "#047857", true, 7))
                    addView(line(candidate.additionalTravelText, 14f, "#374151", topPadding = 5))
                    addView(line(candidate.alternativeEvidenceText, 13f, "#6B7280", topPadding = 5))
                    addView(line(candidate.actionText, 13f, "#374151", true, 8))
                }
            )
        }

    private fun statusColor(status: StapleWatchUiStatus): Int =
        Color.parseColor(
            when (status) {
                StapleWatchUiStatus.WORTH_CHECKING -> "#047857"
                StapleWatchUiStatus.NOT_WORTH_SWITCHING -> "#374151"
                StapleWatchUiStatus.NOT_ENOUGH_STAPLES -> "#92400E"
                StapleWatchUiStatus.BASELINE_INCOMPLETE -> "#92400E"
                StapleWatchUiStatus.DISPLAY_METADATA_INCOMPLETE -> "#B42318"
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

internal fun stapleWatchSwitchCardContentDescription(
    candidate: StapleWatchSwitchUiState
): String =
    listOf(
        candidate.badge,
        "Store: ${candidate.storeName}",
        candidate.savingsText,
        candidate.additionalTravelText,
        candidate.alternativeEvidenceText,
        candidate.actionText
    ).joinToString(". ") { it.trim().trimEnd('.', '!', '?') } + "."

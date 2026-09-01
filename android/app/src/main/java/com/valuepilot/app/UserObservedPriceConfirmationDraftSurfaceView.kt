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
 * Replaceable physical renderer for passive observed-price confirmation draft completeness.
 *
 * The view receives only already-projected immutable UI state. It mechanically displays consumer
 * copy and missing-requirement labels; it never reconstructs draft values, interprets completeness,
 * reads proof, persists data, submits confirmation work, or emits actions.
 *
 * Route visibility is external to [render]. This view starts GONE and rendering never makes it
 * visible, so a future foreground route owner must explicitly decide when the surface is on screen.
 */
internal class UserObservedPriceConfirmationDraftSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), UserObservedPriceConfirmationDraftSurfaceRenderer {

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: UserObservedPriceConfirmationDraftUiState) {
        removeAllViews()

        addView(heading(state.headline))
        addView(statusTitle(state.statusTitle))
        addView(guidance(state.guidance))

        state.missingRequirements.forEach { requirement ->
            addView(missingRequirement(requirement.label))
        }

        addView(notice(state.notice))
    }

    private fun heading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isSaveEnabled = false
        }

    private fun statusTitle(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, 0)
            isSaveEnabled = false
        }

    private fun guidance(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#4B5563"))
            setPadding(0, dp(6), 0, 0)
            isSaveEnabled = false
        }

    private fun missingRequirement(value: String): TextView =
        TextView(context).apply {
            text = "• $value"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#374151"))
            setPadding(dp(4), dp(8), 0, 0)
            isSaveEnabled = false
        }

    private fun notice(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#92400E"))
            setPadding(0, dp(14), 0, 0)
            isSaveEnabled = false
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

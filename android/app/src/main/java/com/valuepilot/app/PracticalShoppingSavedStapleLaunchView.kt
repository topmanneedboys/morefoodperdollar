package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Replaceable physical launcher for the Saved-owned Watch My Staples setup route.
 *
 * Route visibility remains external. This view starts GONE and [render] never makes the outer
 * view visible. It renders only the already-decided consumer navigation action and emits that
 * typed action through [onAction]. It has no Saved persistence, identity, fact, economic,
 * background, network, or notification authority.
 */
class PracticalShoppingSavedStapleLaunchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), PracticalShoppingSavedStapleLaunchRenderer {

    var onAction: ((PracticalShoppingSavedStapleLaunchAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: PracticalShoppingSavedStapleLaunchUiState) {
        removeAllViews()

        state.action?.let { action ->
            addView(
                TextView(context).apply {
                    text = requireNotNull(state.title)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(Color.parseColor("#111827"))
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                }
            )
            addView(
                TextView(context).apply {
                    text = requireNotNull(state.supportingText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(Color.parseColor("#4B5563"))
                    setPadding(0, dp(6), 0, 0)
                }
            )
            addView(
                Button(context).apply {
                    text = requireNotNull(state.actionLabel)
                    setAllCaps(false)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    isEnabled = onAction != null
                    setOnClickListener { onAction?.invoke(action) }
                    layoutParams =
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(14) }
                }
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

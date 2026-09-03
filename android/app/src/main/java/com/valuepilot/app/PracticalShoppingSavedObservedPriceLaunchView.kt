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
 * Replaceable physical launcher for the Saved-owned observed-price selection route.
 *
 * Route visibility remains external. This view starts GONE and [render] never makes the outer
 * view visible. It renders only the already-decided consumer navigation action and emits that
 * exact typed action through [onAction]. It owns no Saved persistence, identity, prefill,
 * confirmation, proof, price, ranking, route, network, or lifecycle authority.
 */
internal class PracticalShoppingSavedObservedPriceLaunchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), PracticalShoppingSavedObservedPriceLaunchRenderer {

    private val ownerBoundButtons = mutableListOf<Button>()

    var onAction: ((PracticalShoppingSavedObservedPriceLaunchAction) -> Unit)? = null
        set(value) {
            field = value
            // The shell may clear the owner while this route is being
            // replaced. Keep an already-rendered launcher visibly inert too.
            ownerBoundButtons.forEach { button ->
                button.isEnabled = value != null
            }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: PracticalShoppingSavedObservedPriceLaunchUiState) {
        ownerBoundButtons.clear()
        removeAllViews()

        state.title?.let { titleText ->
            addView(
                TextView(context).apply {
                    text = titleText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(Color.parseColor("#111827"))
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                }
            )
        }
        state.supportingText?.let { supportingText ->
            addView(
                TextView(context).apply {
                    text = supportingText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(Color.parseColor("#4B5563"))
                    setPadding(0, dp(6), 0, 0)
                }
            )
        }
        state.notice?.let { noticeText ->
            addView(
                TextView(context).apply {
                    text = noticeText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(Color.parseColor("#92400E"))
                    setPadding(0, dp(8), 0, 0)
                    // Explain why navigation is intentionally unavailable to
                    // assistive technology without changing readiness policy.
                    accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                }
            )
        }
        state.action?.let { action ->
            addView(
                Button(context).apply {
                    ownerBoundButtons += this
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

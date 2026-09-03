package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Replaceable physical renderer for the explicit observed-price confirmation action.
 *
 * It receives only immutable presentation state and emits only a user click. It never reads draft
 * fields, proof references/bytes, clocks, identifiers, storage, confirmation results, or evidence.
 * Route visibility remains external to [render], matching the other foreground surfaces.
 */
internal class UserObservedPriceConfirmationActionSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), UserObservedPriceConfirmationActionSurfaceRenderer {

    var onAction: (() -> Unit)? = null

    private val statusText =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#4B5563"))
            isSaveEnabled = false
            // Confirmation and failure arrive asynchronously. Announce the
            // projected message without moving any execution authority here.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

    private val actionButton =
        MaterialButton(context).apply {
            isAllCaps = false
            textSize = 16f
            setOnClickListener { onAction?.invoke() }
            isSaveEnabled = false
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false

        addView(
            TextView(context).apply {
                text = "Confirm observed price"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.parseColor("#111827"))
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                isSaveEnabled = false
            }
        )
        addView(
            statusText,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            }
        )
        addView(
            actionButton,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(54)).apply {
                topMargin = dp(12)
            }
        )
    }

    override fun render(state: UserObservedPriceConfirmationActionUiState) {
        statusText.text = state.message
        actionButton.text = state.actionLabel
        // A replaceable renderer must stay inert when its typed owner is detached,
        // even if the immutable state was ready at the last render.
        actionButton.isEnabled = state.actionEnabled && onAction != null
    }

    override fun onDetachedFromWindow() {
        actionButton.isPressed = false
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

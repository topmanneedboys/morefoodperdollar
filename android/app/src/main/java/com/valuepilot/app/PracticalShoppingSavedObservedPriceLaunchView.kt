package com.valuepilot.app

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

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

    var onAction: ((PracticalShoppingSavedObservedPriceLaunchAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: PracticalShoppingSavedObservedPriceLaunchUiState) {
        removeAllViews()

        state.action?.let { action ->
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

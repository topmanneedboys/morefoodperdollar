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
 * Replaceable physical renderer for a completed Saved-pair prefill handoff check.
 *
 * This view is intentionally display-only. It receives immutable
 * [UserObservedPriceSavedPrefillHandoffUiState] and renders only consumer copy plus the optional
 * already-verified product/store display names carried by that state. It has no action callbacks and
 * cannot execute another prefill check, reconstruct technical identity, mutate confirmation state,
 * capture proof/price/time, persist evidence, rank offers, navigate, or establish current-price
 * authority.
 *
 * Route visibility remains owner-controlled. The view starts GONE and [render] never changes its
 * visibility.
 */
internal class UserObservedPriceSavedPrefillHandoffSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), UserObservedPriceSavedPrefillHandoffSurfaceRenderer {

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: UserObservedPriceSavedPrefillHandoffUiState) {
        removeAllViews()

        addView(heading(state.headline))
        addView(message(state.message))

        val productName = state.productName
        val storeDisplayName = state.storeDisplayName
        if (productName != null && storeDisplayName != null) {
            addView(savedPairCard(productName, storeDisplayName))
        }
    }

    private fun heading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun message(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#4B5563"))
            setPadding(0, dp(8), 0, 0)
        }

    private fun savedPairCard(productName: String, storeDisplayName: String): View {
        val card =
            MaterialCardView(context).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(Color.parseColor("#F9FAFB"))
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = dp(1)
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(16) }
            }

        val body =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
            }
        body.addView(detailLabel("Saved product"))
        body.addView(detailValue(productName))
        body.addView(detailLabel("Saved store", topPadding = 14))
        body.addView(detailValue(storeDisplayName))
        card.addView(body)
        return card
    }

    private fun detailLabel(value: String, topPadding: Int = 0): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
        }

    private fun detailValue(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, dp(3), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

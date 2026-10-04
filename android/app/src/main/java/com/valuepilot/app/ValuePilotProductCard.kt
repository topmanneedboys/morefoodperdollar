package com.valuepilot.app

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/**
 * Shared presentation shell for product rows/cards.
 *
 * It deliberately contains no product fields or action policy. A caller owns
 * the immutable content and typed callbacks placed inside the card.
 */
internal class ValuePilotProductCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        radius = dp(ValuePilotDesignTokens.Shape.CARD_RADIUS_DP).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE_ACCENT))
        strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
        strokeWidth = dp(1)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

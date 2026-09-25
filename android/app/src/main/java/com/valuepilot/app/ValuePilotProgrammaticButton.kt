package com.valuepilot.app

import android.content.Context
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton

/**
 * Presentation-only factory for buttons created by secondary consumer surfaces.
 *
 * It centralizes the warm action treatment without giving a view any shopping,
 * persistence, evidence, ranking, or route authority.
 */
internal object ValuePilotProgrammaticButton {
    fun filled(
        context: Context,
        compact: Boolean = false
    ): MaterialButton =
        MaterialButton(context).apply {
            setAllCaps(false)
            cornerRadius = dp(context, 14)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(context, if (compact) 44 else 52)
            backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#B84A2B"))
            setTextColor(ValuePilotPalette.color("#FFFDF9"))
        }

    fun outlined(
        context: Context,
        compact: Boolean = false,
        destructive: Boolean = false
    ): MaterialButton =
        MaterialButton(context).apply {
            setAllCaps(false)
            cornerRadius = dp(context, 14)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(context, if (compact) 44 else 52)
            strokeWidth = dp(context, 1)
            strokeColor =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(if (destructive) "#E2B8AE" else "#D8CFC6")
                )
            backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFDF9"))
            setTextColor(
                ValuePilotPalette.color(if (destructive) "#A63D32" else "#8F3620")
            )
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

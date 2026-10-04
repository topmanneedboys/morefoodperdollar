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
            cornerRadius = dp(context, ValuePilotDesignTokens.Shape.CONTROL_RADIUS_DP)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(
                context,
                if (compact) {
                    ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP
                } else {
                    ValuePilotDesignTokens.TouchTarget.MINIMUM_DP
                }
            )
            backgroundTintList =
                ColorStateList.valueOf(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.ACTION_FILLED))
            setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.ON_PRIMARY))
        }

    fun outlined(
        context: Context,
        compact: Boolean = false,
        destructive: Boolean = false
    ): MaterialButton =
        MaterialButton(context).apply {
            setAllCaps(false)
            cornerRadius = dp(context, ValuePilotDesignTokens.Shape.CONTROL_RADIUS_DP)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(
                context,
                if (compact) {
                    ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP
                } else {
                    ValuePilotDesignTokens.TouchTarget.MINIMUM_DP
                }
            )
            strokeWidth = dp(context, 1)
            strokeColor =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(
                        if (destructive) {
                            ValuePilotDesignTokens.Colors.OUTLINE_DESTRUCTIVE
                        } else {
                            ValuePilotDesignTokens.Colors.OUTLINE
                        }
                    )
                )
            backgroundTintList =
                ColorStateList.valueOf(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            setTextColor(
                ValuePilotPalette.color(
                    if (destructive) {
                        ValuePilotDesignTokens.Colors.ERROR
                    } else {
                        ValuePilotDesignTokens.Colors.PRIMARY_DARK
                    }
                )
            )
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

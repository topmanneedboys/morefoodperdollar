package com.valuepilot.app

/**
 * Shared presentation-only tokens for the Android consumer surfaces.
 *
 * These values describe spacing, typography, touch targets, motion and the
 * established warm palette. They do not carry product, price, ranking,
 * evidence or navigation authority.
 */
internal object ValuePilotDesignTokens {
    object Spacing {
        const val XXS_DP = 4
        const val XS_DP = 8
        const val SM_DP = 12
        const val MD_DP = 16
        const val LG_DP = 20
        const val XL_DP = 24
        const val XXL_DP = 32
    }

    object Shape {
        const val CONTROL_RADIUS_DP = 14
        const val CARD_RADIUS_DP = 18
        const val SURFACE_RADIUS_DP = 20
    }

    object TouchTarget {
        const val MINIMUM_DP = 48
        const val COMPACT_MINIMUM_DP = 44
    }

    object Typography {
        const val BODY_SP = 15f
        const val LABEL_SP = 13f
        const val TITLE_SP = 26f
    }

    object Motion {
        const val DISCLOSURE_DURATION_MS = 180L
    }

    object Colors {
        const val BACKGROUND = "#FCF8F3"
        const val SURFACE = "#FFFDF9"
        const val SURFACE_SUBTLE = "#F7EFE6"
        const val SURFACE_ACCENT = "#FFF1E8"
        const val PRIMARY = "#B34B2E"
        const val ACTION_FILLED = "#B84A2B"
        const val PRIMARY_DARK = "#8F3620"
        const val ON_PRIMARY = "#FFFFFF"
        const val TEXT_PRIMARY = "#2A211C"
        const val TEXT_SECONDARY = "#5F5148"
        const val TEXT_MUTED = "#82736A"
        const val OUTLINE = "#D8CFC6"
        const val OUTLINE_DESTRUCTIVE = "#E2B8AE"
        const val SUCCESS = "#2F6F59"
        const val WARNING = "#9A5B16"
        const val ERROR = "#A63D32"
    }
}

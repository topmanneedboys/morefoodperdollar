package com.valuepilot.app.next

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextThemeContrastTest {

    @Test
    fun `light and dark semantic text pairs meet WCAG AA contrast`() {
        assertSchemeTextContrast("light", ValuePilotLightColorScheme)
        assertSchemeTextContrast("dark", ValuePilotDarkColorScheme)
    }

    @Test
    fun `muted text remains readable on the translucent card surfaces`() {
        listOf(
            "light" to ValuePilotLightColorScheme,
            "dark" to ValuePilotDarkColorScheme
        ).forEach { (themeName, scheme) ->
            val noticeBackground = compositeOver(
                tint = scheme.surfaceVariant,
                background = scheme.background,
                alpha = 0.68f
            )
            val summaryBackground = compositeOver(
                tint = scheme.secondaryContainer,
                background = scheme.background,
                alpha = 0.45f
            )

            assertAa(themeName, "notice copy", scheme.onSurfaceVariant, noticeBackground)
            assertAa(themeName, "summary copy", scheme.onSurfaceVariant, summaryBackground)
        }
    }

    private fun assertSchemeTextContrast(themeName: String, scheme: ColorScheme) {
        val pairs = listOf(
            "primary button label" to (scheme.onPrimary to scheme.primary),
            "secondary button label" to (scheme.onSecondary to scheme.secondary),
            "page text" to (scheme.onBackground to scheme.background),
            "card title" to (scheme.onSurface to scheme.surface),
            "supporting copy" to (scheme.onSurfaceVariant to scheme.surface),
            "muted copy on variant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
            "eyebrow" to (scheme.secondary to scheme.background),
            "tertiary status" to (scheme.tertiary to scheme.background),
            "selected card label" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "error button label" to (scheme.onError to scheme.error),
            "error notice" to (scheme.onErrorContainer to scheme.errorContainer),
            "destructive text" to (scheme.error to scheme.surface)
        )

        pairs.forEach { (label, colors) ->
            assertAa(themeName, label, colors.first, colors.second)
        }

        val selectedCardBackground = compositeOver(
            tint = scheme.primaryContainer,
            background = scheme.background,
            alpha = 0.38f
        )
        assertAa(themeName, "selected card body", scheme.onSurface, selectedCardBackground)
    }

    private fun assertAa(themeName: String, label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$themeName $label contrast was ${"%.2f".format(ratio)}:1", ratio >= MINIMUM_AA_RATIO)
    }

    private fun compositeOver(tint: Color, background: Color, alpha: Float): Color =
        Color(
            red = tint.red * alpha + background.red * (1f - alpha),
            green = tint.green * alpha + background.green * (1f - alpha),
            blue = tint.blue * alpha + background.blue * (1f - alpha)
        )

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linearize(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) {
                value / 12.92
            } else {
                Math.pow((value + 0.055) / 1.055, 2.4)
            }
        }

        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }

    private companion object {
        const val MINIMUM_AA_RATIO = 4.5
    }
}

package com.valuepilot.app

import android.graphics.Color
import java.util.Locale

/**
 * Keeps programmatically-created consumer cards on the same warm visual system
 * as the XML shell. The input is still a presentation-only color token; this
 * class does not carry shopping, evidence, or ranking meaning.
 */
object ValuePilotPalette {
    private val legacyToWarm = mapOf(
        "#111827" to "#2A211C",
        "#1D4ED8" to "#8F3620",
        "#374151" to "#5F5148",
        "#4B5563" to "#5F5148",
        "#6B7280" to "#82736A",
        "#9CA3AF" to "#82736A",
        "#047857" to "#2F6F59",
        "#065F46" to "#2F6F59",
        "#92400E" to "#9A5B16",
        "#B91C1C" to "#A63D32",
        "#B42318" to "#A63D32",
        "#F9FAFB" to "#FFFDF9",
        "#FFFFFF" to "#FFFDF9",
        "#E5E7EB" to "#D8CFC6",
        "#D1D5DB" to "#D8CFC6",
        "#ECFDF5" to "#EAF4EE",
        "#F0FDF4" to "#EAF4EE",
        "#D1FAE5" to "#EAF4EE",
        "#A7F3D0" to "#C9E1D1",
        "#BBF7D0" to "#C9E1D1",
        "#86EFAC" to "#C9E1D1",
        "#FFFBEB" to "#FFF1E8",
        "#FFF7ED" to "#FFF1E8",
        "#FDE68A" to "#E8CDBF",
        "#FED7AA" to "#E8CDBF",
        "#EFF6FF" to "#FFF1E8",
        "#BFDBFE" to "#E8CDBF"
    )

    fun color(value: String): Int {
        return Color.parseColor(token(value))
    }

    fun token(value: String): String {
        val normalized = value.trim().uppercase(Locale.ROOT)
        return legacyToWarm[normalized] ?: value
    }
}

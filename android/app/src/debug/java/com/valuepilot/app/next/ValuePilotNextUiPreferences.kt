package com.valuepilot.app.next

import android.content.Context

internal enum class ValuePilotNextAppearance {
    SYSTEM,
    LIGHT,
    DARK
}

internal enum class ValuePilotNextMotionPreference {
    SYSTEM,
    REDUCED,
    FULL
}

internal enum class ValuePilotNextDensity {
    COMFORTABLE,
    COMPACT
}

internal data class ValuePilotNextUiPreferences(
    val appearance: ValuePilotNextAppearance = ValuePilotNextAppearance.SYSTEM,
    val motion: ValuePilotNextMotionPreference = ValuePilotNextMotionPreference.SYSTEM,
    val density: ValuePilotNextDensity = ValuePilotNextDensity.COMFORTABLE
)

internal fun shouldOfferPresentationPreferenceReset(
    preferences: ValuePilotNextUiPreferences
): Boolean = preferences != ValuePilotNextUiPreferences()

internal object ValuePilotNextUiPreferencesCodec {
    fun decode(
        appearance: String?,
        motion: String?,
        density: String? = null
    ): ValuePilotNextUiPreferences =
        ValuePilotNextUiPreferences(
            appearance = appearance.toEnumOrNull() ?: ValuePilotNextAppearance.SYSTEM,
            motion = motion.toEnumOrNull() ?: ValuePilotNextMotionPreference.SYSTEM,
            density = density.toEnumOrNull() ?: ValuePilotNextDensity.COMFORTABLE
        )

    fun resolvesDarkTheme(
        appearance: ValuePilotNextAppearance,
        systemDarkTheme: Boolean
    ): Boolean =
        when (appearance) {
            ValuePilotNextAppearance.SYSTEM -> systemDarkTheme
            ValuePilotNextAppearance.LIGHT -> false
            ValuePilotNextAppearance.DARK -> true
        }

    fun resolvesReducedMotion(
        preference: ValuePilotNextMotionPreference,
        systemReducedMotion: Boolean
    ): Boolean =
        when (preference) {
            ValuePilotNextMotionPreference.SYSTEM -> systemReducedMotion
            ValuePilotNextMotionPreference.REDUCED -> true
            ValuePilotNextMotionPreference.FULL -> false
        }

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        this?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }
}

/** Debug Compose presentation settings; these values never change shopping or release behavior. */
internal class ValuePilotNextUiPreferencesStore(context: Context) {
    private val preferences =
        (context.applicationContext ?: context).getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun load(): ValuePilotNextUiPreferences =
        ValuePilotNextUiPreferencesCodec.decode(
            appearance = preferences.getString(KEY_APPEARANCE, null),
            motion = preferences.getString(KEY_MOTION, null),
            density = preferences.getString(KEY_DENSITY, null)
        )

    fun save(value: ValuePilotNextUiPreferences) {
        preferences.edit()
            .putString(KEY_APPEARANCE, value.appearance.name)
            .putString(KEY_MOTION, value.motion.name)
            .putString(KEY_DENSITY, value.density.name)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "valuepilot_next_presentation_v1"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_MOTION = "motion"
        const val KEY_DENSITY = "density"
    }
}

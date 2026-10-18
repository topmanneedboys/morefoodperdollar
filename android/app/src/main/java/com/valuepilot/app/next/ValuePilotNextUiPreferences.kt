package com.valuepilot.app.next

import android.content.Context
import java.util.Locale

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

internal enum class ValuePilotNextLanguage {
    SYSTEM,
    ENGLISH,
    SPANISH_AR
}

internal data class ValuePilotNextUiPreferences(
    val appearance: ValuePilotNextAppearance = ValuePilotNextAppearance.SYSTEM,
    val motion: ValuePilotNextMotionPreference = ValuePilotNextMotionPreference.SYSTEM,
    val density: ValuePilotNextDensity = ValuePilotNextDensity.COMFORTABLE,
    val language: ValuePilotNextLanguage = ValuePilotNextLanguage.SYSTEM
)

internal fun shouldOfferPresentationPreferenceReset(
    preferences: ValuePilotNextUiPreferences
): Boolean = preferences != ValuePilotNextUiPreferences()

internal object ValuePilotNextUiPreferencesCodec {
    fun decode(
        appearance: String?,
        motion: String?,
        density: String? = null,
        language: String? = null
    ): ValuePilotNextUiPreferences =
        ValuePilotNextUiPreferences(
            appearance = appearance.toEnumOrNull() ?: ValuePilotNextAppearance.SYSTEM,
            motion = motion.toEnumOrNull() ?: ValuePilotNextMotionPreference.SYSTEM,
            density = density.toEnumOrNull() ?: ValuePilotNextDensity.COMFORTABLE,
            language = language.toEnumOrNull() ?: ValuePilotNextLanguage.SYSTEM
        )

    fun resolvesLanguage(
        preference: ValuePilotNextLanguage,
        systemLocale: Locale = Locale.getDefault()
    ): ValuePilotNextLanguage =
        when (preference) {
            ValuePilotNextLanguage.SYSTEM -> {
                if (systemLocale.language.equals("es", ignoreCase = true)) {
                    ValuePilotNextLanguage.SPANISH_AR
                } else {
                    ValuePilotNextLanguage.ENGLISH
                }
            }
            ValuePilotNextLanguage.ENGLISH -> ValuePilotNextLanguage.ENGLISH
            ValuePilotNextLanguage.SPANISH_AR -> ValuePilotNextLanguage.SPANISH_AR
        }

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
            density = preferences.getString(KEY_DENSITY, null),
            language = preferences.getString(KEY_LANGUAGE, null)
        )

    fun save(value: ValuePilotNextUiPreferences) {
        preferences.edit()
            .putString(KEY_APPEARANCE, value.appearance.name)
            .putString(KEY_MOTION, value.motion.name)
            .putString(KEY_DENSITY, value.density.name)
            .putString(KEY_LANGUAGE, value.language.name)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "valuepilot_next_presentation_v1"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_MOTION = "motion"
        const val KEY_DENSITY = "density"
        const val KEY_LANGUAGE = "language"
    }
}

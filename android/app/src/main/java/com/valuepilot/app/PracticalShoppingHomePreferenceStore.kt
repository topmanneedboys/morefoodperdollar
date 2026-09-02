package com.valuepilot.app

import android.content.Context

/**
 * Small typed codec for the one Home preference currently exposed to users.
 *
 * The stored value is only an enum name. It contains no prices, evidence,
 * retailer data, shopping history or decision authority. Unknown/corrupt values
 * fail closed to the product's explicit 15 CAD default.
 */
object PracticalShoppingHomePreferenceCodec {

    fun encode(
        choice: LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice
    ): String = choice.name

    fun decode(storedValue: String?): LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice =
        storedValue
            ?.let { value ->
                runCatching {
                    LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.valueOf(value)
                }.getOrNull()
            }
            ?: LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.DEFAULT
}

interface PracticalShoppingHomePreferenceStore {

    fun loadExtraStopMinimumSavingsChoice():
        LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice

    fun saveExtraStopMinimumSavingsChoice(
        choice: LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice
    )
}

/** App-internal persistence for the explicit Home preference only. */
class AndroidPracticalShoppingHomePreferenceStore(context: Context) :
    PracticalShoppingHomePreferenceStore {

    private val preferences =
        (context.applicationContext ?: context).getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    override fun loadExtraStopMinimumSavingsChoice():
        LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice =
        PracticalShoppingHomePreferenceCodec.decode(
            preferences.getString(KEY_EXTRA_STOP_MINIMUM_SAVINGS, null)
        )

    override fun saveExtraStopMinimumSavingsChoice(
        choice: LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice
    ) {
        preferences
            .edit()
            .putString(
                KEY_EXTRA_STOP_MINIMUM_SAVINGS,
                PracticalShoppingHomePreferenceCodec.encode(choice)
            )
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "valuepilot_practical_shopping_home"
        const val KEY_EXTRA_STOP_MINIMUM_SAVINGS = "extra_stop_minimum_savings"
    }
}

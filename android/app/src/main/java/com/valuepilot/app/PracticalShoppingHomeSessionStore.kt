package com.valuepilot.app

import android.content.Context
import android.util.Base64
import com.valuepilot.core.ShoppingRequestDetailsCodec

/**
 * Validates the small durable envelope used to remember the last local Home session.
 *
 * This is only local UX state. Request-details bytes remain opaque and are validated by the
 * existing shared-core codec/session when the model is restored; no product, price, evidence,
 * ranking or provider fact is created here.
 */
object PracticalShoppingHomeSessionStoreCodec {

    private val MAX_PERSISTED_QUERY_LENGTH =
        LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS + 1

    fun decode(
        query: String?,
        wasSubmitted: Boolean,
        chickenChoice: String?,
        extraStopMinimumSavingsChoice: String?,
        requestDetailsLifecycleState: ByteArray?
    ): PracticalShoppingHomeSession.Snapshot? {
        val safeQuery = query ?: return null
        if (safeQuery.length > MAX_PERSISTED_QUERY_LENGTH) return null

        val safeChickenChoice =
            chickenChoice?.let { value ->
                runCatching {
                    LocalSamplePracticalShoppingDemo.ChickenChoice.valueOf(value)
                }.getOrNull()
            }
        val safeDetails =
            requestDetailsLifecycleState
                ?.takeIf { it.size <= ShoppingRequestDetailsCodec.maximumEncodedBytes }
                ?.clone()

        return PracticalShoppingHomeSession.Snapshot(
            query = safeQuery,
            wasSubmitted = wasSubmitted,
            chickenChoice = safeChickenChoice,
            extraStopMinimumSavingsChoice =
                PracticalShoppingHomePreferenceCodec.decode(extraStopMinimumSavingsChoice),
            requestDetailsLifecycleState = safeDetails
        )
    }
}

interface PracticalShoppingHomeSessionStore {

    fun load(): PracticalShoppingHomeSession.Snapshot?

    fun save(snapshot: PracticalShoppingHomeSession.Snapshot)

    fun clear()
}

/** Local-only persistence for the user's last bounded Home list and explicit preferences. */
class AndroidPracticalShoppingHomeSessionStore(context: Context) :
    PracticalShoppingHomeSessionStore {

    private val preferences =
        (context.applicationContext ?: context).getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    override fun load(): PracticalShoppingHomeSession.Snapshot? {
        if (!preferences.contains(KEY_QUERY)) return null

        val details =
            preferences.getString(KEY_REQUEST_DETAILS, null)?.let { encoded ->
                runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            }
        return PracticalShoppingHomeSessionStoreCodec.decode(
            query = preferences.getString(KEY_QUERY, null),
            wasSubmitted = preferences.getBoolean(KEY_WAS_SUBMITTED, false),
            chickenChoice = preferences.getString(KEY_CHICKEN_CHOICE, null),
            extraStopMinimumSavingsChoice =
                preferences.getString(KEY_EXTRA_STOP_MINIMUM_SAVINGS, null),
            requestDetailsLifecycleState = details
        )
    }

    override fun save(snapshot: PracticalShoppingHomeSession.Snapshot) {
        val editor =
            preferences
                .edit()
                .putString(KEY_QUERY, snapshot.query)
                .putBoolean(KEY_WAS_SUBMITTED, snapshot.wasSubmitted)
                .putString(KEY_CHICKEN_CHOICE, snapshot.chickenChoice?.name)
                .putString(
                    KEY_EXTRA_STOP_MINIMUM_SAVINGS,
                    PracticalShoppingHomePreferenceCodec.encode(
                        snapshot.extraStopMinimumSavingsChoice
                    )
                )

        snapshot.requestDetailsLifecycleState?.let { bytes ->
            editor.putString(
                KEY_REQUEST_DETAILS,
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            )
        } ?: editor.remove(KEY_REQUEST_DETAILS)

        editor.apply()
    }

    override fun clear() {
        preferences
            .edit()
            .remove(KEY_QUERY)
            .remove(KEY_WAS_SUBMITTED)
            .remove(KEY_CHICKEN_CHOICE)
            .remove(KEY_EXTRA_STOP_MINIMUM_SAVINGS)
            .remove(KEY_REQUEST_DETAILS)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "valuepilot_practical_shopping_home_session"
        const val KEY_QUERY = "query"
        const val KEY_WAS_SUBMITTED = "was_submitted"
        const val KEY_CHICKEN_CHOICE = "chicken_choice"
        const val KEY_EXTRA_STOP_MINIMUM_SAVINGS = "extra_stop_minimum_savings"
        const val KEY_REQUEST_DETAILS = "request_details"
    }
}

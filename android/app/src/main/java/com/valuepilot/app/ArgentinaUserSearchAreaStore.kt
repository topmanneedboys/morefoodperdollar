package com.valuepilot.app

import android.content.Context

/**
 * Private, user-selected Search origin.
 *
 * A stored user area is explicit shopper context. It is never presented as a
 * source-qualified launch area, never derived from device location, and never
 * asserts that stores or prices exist near it.
 */
internal class ArgentinaUserSearchAreaStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ArgentinaUserSearchArea? = runCatching {
        val label = preferences.getString(KEY_LABEL, null)?.takeIf { it.isNotBlank() } ?: return null
        val latitude = preferences.getString(KEY_LATITUDE, null) ?: return null
        val longitude = preferences.getString(KEY_LONGITUDE, null) ?: return null
        val radiusKm = preferences.getString(KEY_RADIUS_KM, null) ?: DEFAULT_RADIUS_KM
        ArgentinaUserSearchArea(label, latitude, longitude, radiusKm)
    }.getOrNull()

    fun save(area: ArgentinaUserSearchArea): Boolean = preferences.edit()
        .putString(KEY_LABEL, area.label)
        .putString(KEY_LATITUDE, area.latitude)
        .putString(KEY_LONGITUDE, area.longitude)
        .putString(KEY_RADIUS_KM, area.radiusKm)
        .commit()

    fun clear(): Boolean = preferences.edit().clear().commit()

    private companion object {
        const val PREFERENCES_NAME = "valuepilot_search_area"
        const val KEY_LABEL = "label"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_RADIUS_KM = "radiusKm"
        const val DEFAULT_RADIUS_KM = "25"
    }
}

/** Presentation-free rules for choosing and explaining the active Search area. */
internal object ArgentinaSearchAreaPolicy {

    /** The qualified Buenos Aires launch area remains the default until a shopper chooses. */
    fun selectedOrDefault(stored: ArgentinaUserSearchArea?): ArgentinaSearchArea =
        stored ?: ArgentinaQualifiedLaunchAreas.BuenosAiresCaba

    /**
     * Empty results are not a defect: most of Argentina has no store inside the
     * permitted radius. Say so instead of implying the product does not exist.
     */
    fun emptyResultHint(area: ArgentinaSearchArea): String =
        "No product identity with a nearby store was found for ${area.label} within " +
            "${area.radiusKm} km. Try a larger radius or another city."
}

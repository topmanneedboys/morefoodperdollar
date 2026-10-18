package com.valuepilot.app.next

import android.content.Context
import com.valuepilot.app.ArgentinaUserSearchArea

/** Private preview-only origin; neither saved identities nor production data are changed. */
internal class ValuePilotNextSearchAreaStore(context: Context) {
    private val preferences = context.getSharedPreferences("valuepilot_next_search_area", Context.MODE_PRIVATE)

    fun load(): ArgentinaUserSearchArea? = runCatching {
        val label = preferences.getString("label", null) ?: return null
        ArgentinaUserSearchArea(label, preferences.getString("latitude", null) ?: return null,
            preferences.getString("longitude", null) ?: return null,
            preferences.getString("radiusKm", "25") ?: "25")
    }.getOrNull()

    fun save(area: ArgentinaUserSearchArea): Boolean = preferences.edit()
        .putString("label", area.label).putString("latitude", area.latitude)
        .putString("longitude", area.longitude).putString("radiusKm", area.radiusKm).commit()
}

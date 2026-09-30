package com.valuepilot.app

import com.valuepilot.core.ShoppingStoreKey
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Creates bounded device-private store identities from shopper-entered names. */
internal object TripStoreSelectionAdapter {
    const val PRIVATE_SOURCE_LABEL = "Private shopper store identity"

    fun privateEntry(rawName: String): TripStoreSelection? {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isBlank() || name.length > 240 || name.any { it == '\n' || it == '\r' }) return null
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(name.lowercase(Locale.ROOT).toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        return TripStoreSelection(
            storeKey = ShoppingStoreKey("private-store:$digest"),
            displayName = name,
            sourceLabel = PRIVATE_SOURCE_LABEL
        )
    }
}

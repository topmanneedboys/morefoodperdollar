package com.valuepilot.app

import com.valuepilot.core.ShoppingStoreKey
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Maps the existing signed location-only directory into durable trip-store identities. */
internal object TripStoreDirectoryAdapter {
    const val DIRECTORY_SOURCE_LABEL = "Signed location directory · location only"
    const val PRIVATE_SOURCE_LABEL = "Private shopper store identity"
    const val MAX_PICKER_RESULTS = 24

    fun fromDirectoryRecord(record: StoreDirectoryRecord): TripStoreSelection =
        TripStoreSelection(
            storeKey = ShoppingStoreKey("directory:${record.recordId}"),
            displayName = record.name,
            addressLabel = addressLabel(record.address),
            sourceLabel = "$DIRECTORY_SOURCE_LABEL · ${record.regionId}",
            latitudeE7 = record.latitudeE7,
            longitudeE7 = record.longitudeE7
        )

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

    fun filter(
        stores: List<TripStoreSelection>,
        rawQuery: String,
        limit: Int = MAX_PICKER_RESULTS
    ): List<TripStoreSelection> {
        require(limit in 1..MAX_PICKER_RESULTS)
        val query = rawQuery.trim().lowercase(Locale.ROOT)
        return stores.asSequence()
            .filter { query.isBlank() || it.displayName.lowercase(Locale.ROOT).contains(query) || it.addressLabel?.lowercase(Locale.ROOT)?.contains(query) == true }
            .sortedWith(compareBy<TripStoreSelection> { it.displayName.lowercase(Locale.ROOT) }.thenBy { it.storeKey.value })
            .take(limit)
            .toList()
    }

    private fun addressLabel(address: StoreDirectoryAddress?): String? {
        if (address == null) return null
        return listOfNotNull(
            address.houseNumber,
            address.unit,
            address.street,
            address.city,
            address.postcode
        ).joinToString(", ").takeIf(String::isNotBlank)
    }
}

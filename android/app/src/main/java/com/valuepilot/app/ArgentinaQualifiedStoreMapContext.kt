package com.valuepilot.app

import java.math.BigDecimal

/**
 * Source-backed store context shown inside ValuePilot before any optional
 * hand-off to an external map application.  This is evidence presentation,
 * not routing, geocoding, stock, or availability authority.
 */
internal data class ArgentinaQualifiedStoreMapContext(
    val storeName: String,
    val storeKey: String,
    val latitude: String?,
    val longitude: String?,
    val addressLabel: String?,
    val markers: List<ArgentinaPrivateBasketMapMarkerProjection>
) {
    init {
        require(storeName.isNotBlank())
        require(storeKey.isNotBlank())
        require((latitude == null) == (longitude == null))
        require(addressLabel == null || addressLabel.isNotBlank())
        require(markers.size <= ArgentinaBackendContract.MAX_PLAN_LINES)
    }
}

internal object ArgentinaQualifiedStoreMapContextPresentation {
    fun fromOffer(
        offer: ArgentinaBackendContract.SearchOffer,
        productName: String = offer.productEvidenceKey
    ): ArgentinaQualifiedStoreMapContext {
        val coordinatesValid = validCoordinate(offer.storeLatitude, -90, 90) &&
            validCoordinate(offer.storeLongitude, -180, 180)
        return ArgentinaQualifiedStoreMapContext(
            storeName = offer.storeName,
            storeKey = offer.storeKey,
            latitude = offer.storeLatitude.takeIf { coordinatesValid },
            longitude = offer.storeLongitude.takeIf { coordinatesValid },
            addressLabel = addressLabel(offer.storeAddress),
            markers = markerForOffer(offer, productName)?.let(::listOf).orEmpty()
        )
    }

    fun fromStore(
        store: ArgentinaPrivateBasketStoreProjection,
        markers: List<ArgentinaPrivateBasketMapMarkerProjection>
    ): ArgentinaQualifiedStoreMapContext =
        ArgentinaQualifiedStoreMapContext(
            storeName = store.storeName,
            storeKey = store.storeKey,
            latitude = store.latitude.takeIf { validCoordinate(it, -90, 90) }
                ?.takeIf { validCoordinate(store.longitude, -180, 180) },
            longitude = store.longitude.takeIf { validCoordinate(it, -180, 180) }
                ?.takeIf { validCoordinate(store.latitude, -90, 90) },
            addressLabel = store.addressLabel,
            markers = markers
                .asSequence()
                .filter { it.storeKey == store.storeKey }
                .filter { validCoordinate(it.latitude, -90, 90) }
                .filter { validCoordinate(it.longitude, -180, 180) }
                .distinctBy { listOf(it.productEvidenceKey, it.offerKeyForOrdering()) }
                .sortedWith(compareBy({ it.productEvidenceKey }, { it.productName }, { it.priceText }))
                .take(ArgentinaBackendContract.MAX_PLAN_LINES)
                .toList()
        )

    fun markersFromPlan(
        plan: ArgentinaBackendContract.Plan
    ): List<ArgentinaPrivateBasketMapMarkerProjection> {
        val stores = plan.stores.associateBy { it.storeKey }
        return plan.lines.mapNotNull { line ->
            val store = stores[line.storeKey] ?: return@mapNotNull null
            if (!validCoordinate(store.latitude, -90, 90) ||
                !validCoordinate(store.longitude, -180, 180)
            ) return@mapNotNull null
            ArgentinaPrivateBasketMapMarkerProjection(
                storeKey = store.storeKey,
                storeName = store.storeName,
                latitude = store.latitude,
                longitude = store.longitude,
                productEvidenceKey = line.productEvidenceKey,
                productName = line.productName,
                priceText = "${line.packagePrice.currency} ${line.packagePrice.amount}"
            )
        }.distinctBy { listOf(it.productEvidenceKey, it.offerKeyForOrdering()) }
            .sortedWith(compareBy({ it.productEvidenceKey }, { it.productName }, { it.priceText }))
            .take(ArgentinaBackendContract.MAX_PLAN_LINES)
    }

    fun message(context: ArgentinaQualifiedStoreMapContext): String = buildString {
        append("Source-backed store context\n")
        append("Store: ").append(context.storeName).append('\n')
        append("storeKey: ").append(context.storeKey).append('\n')
        if (context.latitude != null && context.longitude != null) {
            append("Coordinates: ").append(context.latitude).append(", ")
                .append(context.longitude).append('\n')
        } else {
            append("Coordinates: unknown; the source did not provide valid coordinates.\n")
        }
        context.addressLabel?.let { append("Address: ").append(it).append('\n') }
        if (context.markers.isNotEmpty()) {
            append("\nEvidence markers:\n")
            context.markers.forEach { marker ->
                append("• ").append(marker.productName)
                    .append(" · ").append(marker.priceText).append('\n')
                append("  productEvidenceKey ").append(marker.productEvidenceKey).append('\n')
                append("  ").append(marker.latitude).append(", ").append(marker.longitude).append('\n')
            }
        } else {
            append("\nNo product price marker is attached to this store context.\n")
        }
        append("\nCoordinates are source evidence only. ValuePilot does not infer a driving route, travel time, stock, pickup, delivery or availability here.")
    }

    private fun markerForOffer(
        offer: ArgentinaBackendContract.SearchOffer,
        productName: String
    ): ArgentinaPrivateBasketMapMarkerProjection? {
        if (!validCoordinate(offer.storeLatitude, -90, 90) ||
            !validCoordinate(offer.storeLongitude, -180, 180)
        ) return null
        return ArgentinaPrivateBasketMapMarkerProjection(
            storeKey = offer.storeKey,
            storeName = offer.storeName,
            latitude = offer.storeLatitude,
            longitude = offer.storeLongitude,
            productEvidenceKey = offer.productEvidenceKey,
            productName = productName.ifBlank { offer.productEvidenceKey },
            priceText = "${offer.listPrice.currency} ${offer.listPrice.amount}"
        )
    }

    private fun addressLabel(address: Map<String, String>): String? = address.toSortedMap()
        .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
        .entries
        .joinToString(", ") { (key, value) -> "$key: $value" }
        .takeIf(String::isNotBlank)

    private fun validCoordinate(raw: String, minimum: Int, maximum: Int): Boolean =
        runCatching { BigDecimal(raw.trim()) }.getOrNull()?.let {
            it >= BigDecimal.valueOf(minimum.toLong()) &&
                it <= BigDecimal.valueOf(maximum.toLong())
        } == true

    private fun ArgentinaPrivateBasketMapMarkerProjection.offerKeyForOrdering(): String =
        "$storeKey|$productEvidenceKey|$priceText"
}

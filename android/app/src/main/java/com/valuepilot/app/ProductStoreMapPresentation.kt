package com.valuepilot.app

import com.valuepilot.core.ProductStoreMapFreshness
import com.valuepilot.core.ProductStoreMapPin
import com.valuepilot.core.ProductStoreMapResult
import com.valuepilot.core.ProductStoreMapResultStatus
import java.math.BigDecimal
import java.math.RoundingMode

/** The two truthful origins understood by the tile-free map presentation. */
internal enum class ProductStoreMapOrigin {
    CABA_FALLBACK,
    ONE_SHOT_LOCATION
}

internal enum class ProductStoreMapSurfaceMode {
    LOADING,
    READY,
    EMPTY,
    UNAVAILABLE
}

/**
 * Consumer-ready map state. It contains the exact qualified pin records but no renderer
 * policy or provider diagnostics. The map view only lays out this immutable state.
 */
internal data class ProductStoreMapSurfaceState(
    val mode: ProductStoreMapSurfaceMode,
    val productName: String,
    val packageQuantityLabel: String?,
    val origin: ProductStoreMapOrigin,
    val headline: String,
    val support: String,
    val locationLabel: String,
    val pins: List<ProductStoreMapPin>
) {
    init {
        require(productName.isNotBlank())
        require(packageQuantityLabel == null || packageQuantityLabel.isNotBlank())
        require(headline.isNotBlank())
        require(support.isNotBlank())
        require(locationLabel.isNotBlank())
        require(pins.size <= 64)
        require(pins.map { it.storeKey }.distinct().size == pins.size)
        require(mode != ProductStoreMapSurfaceMode.READY || pins.isNotEmpty())
        require(mode != ProductStoreMapSurfaceMode.EMPTY || pins.isEmpty())
        require(mode != ProductStoreMapSurfaceMode.UNAVAILABLE || pins.isEmpty())
    }
}

/** Pure presentation mapping for the existing exact product/store result. */
internal object ProductStoreMapProjector {

    fun loading(
        productName: String,
        packageQuantityLabel: String?
    ): ProductStoreMapSurfaceState =
        ProductStoreMapSurfaceState(
            mode = ProductStoreMapSurfaceMode.LOADING,
            productName = productName,
            packageQuantityLabel = packageQuantityLabel,
            origin = ProductStoreMapOrigin.CABA_FALLBACK,
            headline = "Finding stores…",
            support = "For this exact product.",
            locationLabel = "Location not used · CABA search area",
            pins = emptyList()
        )

    fun unavailable(
        productName: String,
        packageQuantityLabel: String?
    ): ProductStoreMapSurfaceState =
        ProductStoreMapSurfaceState(
            mode = ProductStoreMapSurfaceMode.UNAVAILABLE,
            productName = productName,
            packageQuantityLabel = packageQuantityLabel,
            origin = ProductStoreMapOrigin.CABA_FALLBACK,
            headline = "Store map unavailable",
            support = "Search is unchanged.",
            locationLabel = "Location not used · CABA search area",
            pins = emptyList()
        )

    fun project(
        productName: String,
        packageQuantityLabel: String?,
        result: ProductStoreMapResult,
        origin: ProductStoreMapOrigin
    ): ProductStoreMapSurfaceState {
        val hasPins = result.status == ProductStoreMapResultStatus.READY && result.pins.isNotEmpty()
        return ProductStoreMapSurfaceState(
            mode = if (hasPins) ProductStoreMapSurfaceMode.READY else ProductStoreMapSurfaceMode.EMPTY,
            productName = productName,
            packageQuantityLabel = packageQuantityLabel,
            origin = origin,
            headline = if (hasPins) {
                "${result.pins.size} store${if (result.pins.size == 1) "" else "s"} reported"
            } else {
                "No nearby store data"
            },
            support = if (hasPins) {
                "For this exact product. Availability is unknown."
            } else {
                "Try another product."
            },
            locationLabel = when (origin) {
                ProductStoreMapOrigin.CABA_FALLBACK ->
                    "Location not used · CABA search area"

                ProductStoreMapOrigin.ONE_SHOT_LOCATION ->
                    "Using one-shot location · approximate distance"
            },
            pins = if (hasPins) result.pins else emptyList()
        )
    }

    fun distanceLabel(
        pin: ProductStoreMapPin,
        origin: ProductStoreMapOrigin
    ): String? =
        pin.approximateDistanceMetres?.let { metres ->
            val suffix =
                if (origin == ProductStoreMapOrigin.CABA_FALLBACK) {
                    " from CABA search area"
                } else {
                    " away"
                }
            if (metres < 1_000L) {
                "≈ ${metres} m$suffix"
            } else {
                val kilometres =
                    BigDecimal.valueOf(metres)
                        .movePointLeft(3)
                        .setScale(1, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                "≈ $kilometres km$suffix"
            }
        }

    fun freshnessLabel(value: ProductStoreMapFreshness): String =
        when (value) {
            ProductStoreMapFreshness.FRESH -> "Reported in a qualified release"
            ProductStoreMapFreshness.STALE -> "Older release · not current"
            ProductStoreMapFreshness.UNKNOWN -> "Freshness unknown · not current"
        }

    fun priceLabel(pin: ProductStoreMapPin): String? =
        if (pin.priceConflict) {
            "Conflicting ${priceEvidenceLabel(pin).removeSuffix(" price").lowercase()} prices"
        } else {
            pin.price?.let { price ->
                "${priceEvidenceLabel(pin)} · ${price.currencyCode} ${
                    BigDecimal.valueOf(price.minorUnits)
                        .movePointLeft(price.fractionDigits)
                        .setScale(price.fractionDigits, RoundingMode.UNNECESSARY)
                        .toPlainString()
                }"
            }
        }

    fun unitPriceLabel(pin: ProductStoreMapPin): String? =
        pin.unitPrice?.let { price ->
            val basis = pin.unitPriceBasis ?: return@let null
            "Unit price · ${price.currencyCode} ${
                BigDecimal.valueOf(price.minorUnits)
                    .movePointLeft(price.fractionDigits)
                    .setScale(price.fractionDigits, RoundingMode.UNNECESSARY)
                    .toPlainString()
            }/$basis"
        }

    private fun priceEvidenceLabel(pin: ProductStoreMapPin): String =
        if (pin.sourceLabel.contains("ARGENTINA_SEPA", ignoreCase = true) ||
            pin.sourceLabel.contains("PRECIOS CLAROS", ignoreCase = true)
        ) {
            "Listed price"
        } else {
            "Reported price"
        }
}
